package org.qingyue.rope.core;

import java.io.*;
import java.util.UUID;

/** Single-threaded deterministic state machine. All time arguments use elapsed realtime. */
public final class SessionEngine {
    public enum Phase { READY, COUNTDOWN, WORK, REST, PAUSED, FINISHED }
    public final Plan plan;
    public final String id;
    public final long startedAtEpochMs;
    private Phase phase=Phase.READY,pausedFrom=Phase.READY;
    private long phaseElapsed,workElapsed,restElapsed,lastNow,acceptAfter;
    private int round=1,total;
    private final int[] roundCounts;
    private final long[] roundWork;
    private boolean completed;

    public SessionEngine(Plan plan,long epochMs) {this(plan,epochMs,UUID.randomUUID().toString());}
    private SessionEngine(Plan plan,long epochMs,String id) {
        this.plan=plan;startedAtEpochMs=epochMs;this.id=id;
        roundCounts=new int[plan.rounds];roundWork=new long[plan.rounds];
    }
    public void start(long now) {
        if(phase!=Phase.READY)return;
        phase=Phase.COUNTDOWN;phaseElapsed=0;lastNow=now;acceptAfter=now;
    }
    public void tick(long now) {
        if(now<lastNow)return;
        long left=now-lastNow;lastNow=now;
        if(phase==Phase.READY||phase==Phase.PAUSED||phase==Phase.FINISHED)return;
        while(left>0&&phase!=Phase.FINISHED) {
            long duration=phase==Phase.COUNTDOWN?3000:phase==Phase.REST?plan.restSeconds*1000L:
                    plan.mode==Plan.Mode.FREE?Long.MAX_VALUE:plan.workSeconds*1000L;
            long used=Math.min(left,duration-phaseElapsed);
            phaseElapsed+=used;
            if(phase==Phase.WORK){workElapsed+=used;roundWork[round-1]+=used;}
            if(phase==Phase.REST)restElapsed+=used;
            left-=used;
            if(phaseElapsed>=duration) {
                long boundary=now-left;
                if(phase==Phase.COUNTDOWN){phase=Phase.WORK;acceptAfter=boundary;}
                else if(phase==Phase.WORK) {
                    if(round>=plan.rounds){phase=Phase.FINISHED;completed=true;}
                    else phase=Phase.REST;
                } else {round++;phase=Phase.WORK;acceptAfter=boundary;}
                phaseElapsed=0;
            }
        }
    }
    public boolean jump(long now,long capturedAt) {
        tick(now);
        if(phase!=Phase.WORK||capturedAt<acceptAfter||capturedAt>now||now-capturedAt>500)return false;
        total++;roundCounts[round-1]++;return true;
    }
    public void pause(long now) {
        tick(now);
        if(phase==Phase.COUNTDOWN||phase==Phase.WORK||phase==Phase.REST){pausedFrom=phase;phase=Phase.PAUSED;}
    }
    public void resume(long now) {
        if(phase!=Phase.PAUSED)return;
        phase=pausedFrom;lastNow=now;acceptAfter=now;
    }
    public void finish(long now) {
        tick(now);if(phase!=Phase.FINISHED){phase=Phase.FINISHED;completed=false;}
    }
    public Phase phase(){return phase;}
    public Phase pausedFrom(){return pausedFrom;}
    public long phaseElapsed(){return phaseElapsed;}
    public long workMs(){return workElapsed;}
    public long restMs(){return restElapsed;}
    public int total(){return total;}
    public int round(){return round;}
    public int[] roundCounts(){return roundCounts.clone();}
    public long[] roundWork(){return roundWork.clone();}
    public boolean completed(){return completed;}
    public long remainingMs() {
        Phase p=phase==Phase.PAUSED?pausedFrom:phase;
        if(p==Phase.COUNTDOWN)return Math.max(0,3000-phaseElapsed);
        if(p==Phase.REST)return Math.max(0,plan.restSeconds*1000L-phaseElapsed);
        if(p==Phase.WORK&&plan.mode!=Plan.Mode.FREE)return Math.max(0,plan.workSeconds*1000L-phaseElapsed);
        return 0;
    }
    public byte[] snapshot() throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(1);plan.write(out);out.writeUTF(id);out.writeLong(startedAtEpochMs);
        out.writeInt(phase.ordinal());out.writeInt(pausedFrom.ordinal());out.writeLong(phaseElapsed);
        out.writeLong(workElapsed);out.writeLong(restElapsed);out.writeInt(round);out.writeInt(total);out.writeBoolean(completed);
        for(int v:roundCounts)out.writeInt(v);for(long v:roundWork)out.writeLong(v);out.close();return bytes.toByteArray();
    }
    /** Recovery is always paused. Old elapsed-realtime values never cross process restarts. */
    public static SessionEngine restore(byte[] bytes,long now) throws IOException {
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));
        if(in.readInt()!=1)throw new IOException("不支持的训练版本");
        return restoreBody(bytes,now);
    }
    private static SessionEngine restoreBody(byte[] bytes,long now) throws IOException {
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));in.readInt();Plan p=Plan.read(in);
        String id=in.readUTF();long epoch=in.readLong();SessionEngine e=new SessionEngine(p,epoch,id);
        int phase=in.readInt(),paused=in.readInt();
        if(phase<0||phase>=Phase.values().length||paused<0||paused>=Phase.values().length)throw new IOException("状态损坏");
        e.phase=Phase.values()[phase];e.pausedFrom=Phase.values()[paused];e.phaseElapsed=in.readLong();
        e.workElapsed=in.readLong();e.restElapsed=in.readLong();e.round=in.readInt();e.total=in.readInt();e.completed=in.readBoolean();
        if(e.round<1||e.round>p.rounds||e.total<0||e.phaseElapsed<0||e.workElapsed<0||e.restElapsed<0)throw new IOException("训练损坏");
        long sum=0;for(int i=0;i<p.rounds;i++){e.roundCounts[i]=in.readInt();if(e.roundCounts[i]<0)throw new IOException("次数损坏");sum+=e.roundCounts[i];}
        for(int i=0;i<p.rounds;i++){e.roundWork[i]=in.readLong();if(e.roundWork[i]<0)throw new IOException("时长损坏");}
        if(sum!=e.total||in.available()!=0)throw new IOException("训练校验失败");
        if(e.phase!=Phase.READY&&e.phase!=Phase.FINISHED&&e.phase!=Phase.PAUSED){e.pausedFrom=e.phase;e.phase=Phase.PAUSED;}
        e.lastNow=now;e.acceptAfter=now;return e;
    }
}
