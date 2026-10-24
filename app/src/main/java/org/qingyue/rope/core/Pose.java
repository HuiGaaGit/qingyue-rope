package org.qingyue.rope.core;

/** MoveNet's 17 unmirrored keypoints, normalized to the full upright camera frame. */
public final class Pose {
    public final float[][] points;
    public final long capturedAtMs;
    public Pose(float[][] points, long capturedAtMs) {
        if(points==null||points.length!=17)throw new IllegalArgumentException("17个关键点");
        this.points=points;this.capturedAtMs=capturedAtMs;
    }
    public float y(int index) { return points[index][0]; }
    public float x(int index) { return points[index][1]; }
    public float score(int index) { return points[index][2]; }
    public float avgY(int a,int b) { return (y(a)+y(b))/2f; }
    public float avgX(int a,int b) { return (x(a)+x(b))/2f; }
    public boolean fullBody() {
        int[] required={0,5,6,11,12,13,14,15,16};
        for(int i:required) {
            for(float v:points[i])if(!Float.isFinite(v))return false;
            if(score(i)<0.32f || x(i)<0.025f || x(i)>0.975f || y(i)<0.025f || y(i)>0.975f)return false;
        }
        float shoulder=avgY(5,6),hip=avgY(11,12),knee=avgY(13,14),foot=avgY(15,16);
        return foot-shoulder>0.30f && hip-shoulder>0.07f && knee>hip && foot>knee &&
                Math.abs(y(15)-y(16)) < (foot-shoulder)*0.15f;
    }
}
