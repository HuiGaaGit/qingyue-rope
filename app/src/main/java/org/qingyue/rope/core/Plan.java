package org.qingyue.rope.core;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Immutable training settings, seconds are converted to milliseconds at the boundary. */
public final class Plan {
    public enum Mode { FREE, TIMED, INTERVAL }
    public final Mode mode;
    public final int workSeconds, restSeconds, rounds;
    public Plan(Mode mode, int workSeconds, int restSeconds, int rounds) {
        if (mode == null || workSeconds < 10 || workSeconds > 3600 || restSeconds < 0 ||
                restSeconds > 600 || rounds < 1 || rounds > 20) throw new IllegalArgumentException("训练参数越界");
        if (mode == Mode.INTERVAL && restSeconds < 1) throw new IllegalArgumentException("分组休息至少1秒");
        if (mode == Mode.INTERVAL && rounds < 2) throw new IllegalArgumentException("分组训练至少2组");
        this.mode=mode; this.workSeconds=workSeconds; this.restSeconds=restSeconds;
        this.rounds=mode == Mode.INTERVAL ? rounds : 1;
    }
    public String title() { return mode==Mode.FREE ? "自由跳绳" : mode==Mode.TIMED ? "定时跳绳" : "分组训练"; }
    public void write(DataOutput out) throws IOException {
        out.writeInt(mode.ordinal());out.writeInt(workSeconds);out.writeInt(restSeconds);out.writeInt(rounds);
    }
    public static Plan read(DataInput in) throws IOException {
        int mode=in.readInt(); if(mode<0||mode>=Mode.values().length)throw new IOException("未知训练类型");
        try { return new Plan(Mode.values()[mode],in.readInt(),in.readInt(),in.readInt()); }
        catch(IllegalArgumentException error) { throw new IOException(error); }
    }
}
