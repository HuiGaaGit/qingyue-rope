package org.qingyue.rope.core;

import java.io.*;

public final class WorkoutRecord {
    public final String id;
    public final Plan plan;
    public final long startedAt,workMs,restMs;
    public final int total;
    public final boolean completed;
    public final int[] counts;
    public final long[] roundWork;
    public WorkoutRecord(SessionEngine e) {
        if(e.phase()!=SessionEngine.Phase.FINISHED)throw new IllegalArgumentException("训练尚未结束");
        id=e.id;plan=e.plan;startedAt=e.startedAtEpochMs;workMs=e.workMs();restMs=e.restMs();total=e.total();completed=e.completed();
        counts=e.roundCounts();roundWork=e.roundWork();
    }
    private WorkoutRecord(DataInput in) throws IOException {
        id=in.readUTF();plan=Plan.read(in);startedAt=in.readLong();workMs=in.readLong();restMs=in.readLong();total=in.readInt();completed=in.readBoolean();
        counts=new int[plan.rounds];roundWork=new long[plan.rounds];
        long sum=0;for(int i=0;i<counts.length;i++){counts[i]=in.readInt();if(counts[i]<0)throw new IOException("次数损坏");sum+=counts[i];}
        for(int i=0;i<counts.length;i++){roundWork[i]=in.readLong();if(roundWork[i]<0)throw new IOException("时长损坏");}
        if(sum!=total||total<0||workMs<0||restMs<0)throw new IOException("记录损坏");
    }
    public void write(DataOutput out) throws IOException {
        out.writeUTF(id);plan.write(out);out.writeLong(startedAt);out.writeLong(workMs);out.writeLong(restMs);out.writeInt(total);out.writeBoolean(completed);
        for(int c:counts)out.writeInt(c);for(long t:roundWork)out.writeLong(t);
    }
    public static WorkoutRecord read(DataInput in) throws IOException{return new WorkoutRecord(in);}
}
