package org.qingyue.rope.core;

import java.util.*;

/** Opt-in bounded RAM trace. No pixels, audio, personal identifiers or wall-clock times. */
public final class NumericDiagnostics {
    private final ArrayDeque<String> rows=new ArrayDeque<>();
    private long started=-1,last=-1;
    public void clear(){rows.clear();started=-1;last=-1;}
    public void sample(Pose p,JumpDetector detector,int phase,int total){
        if(last>=0&&p.capturedAtMs-last<250)return;
        if(started<0)started=p.capturedAtMs;last=p.capturedAtMs;
        PoseQuality quality=PoseQuality.assess(p);
        rows.add(String.format(Locale.ROOT,"%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%d,%d,%.5f,%.5f,%.5f,%.5f,%d,%d,%d,%d,%d,%.1f,%.1f,%.1f",
            p.capturedAtMs-started,phase,total,detector.status().ordinal(),detector.qualityReason(),p.score(5),p.score(6),p.score(11),p.score(12),p.score(15),p.score(16),quality.personVisible?1:0,quality.countable?1:0,detector.ready()?1:0,detector.hipSignal(),detector.footSignal(),detector.hipThreshold(),detector.footThreshold(),p.inferenceMs,p.frameWidth,p.frameHeight,p.rotation,p.mirror,p.cropLeft,p.cropTop,p.cropSize));
        while(rows.size()>240)rows.removeFirst();
    }
    public int size(){return rows.size();}
    public String csv(){StringBuilder out=new StringBuilder("elapsed_ms,phase,total,status,reason,shoulder_l_score,shoulder_r_score,hip_l_score,hip_r_score,ankle_l_score,ankle_r_score,person_visible,countable,ready,hip_rise,feet_rise,hip_enter,feet_enter,inference_ms,width,height,rotation,preview_mirror,crop_left,crop_top,crop_size\n");for(String row:rows)out.append(row).append('\n');return out.toString();}
}
