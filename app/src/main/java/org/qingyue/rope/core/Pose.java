package org.qingyue.rope.core;

/** MoveNet's 17 unmirrored keypoints, normalized to the full upright camera frame. */
public final class Pose {
    public final float[][] points;
    public final long capturedAtMs;
    public boolean cameraMoving;
    public int frameWidth,frameHeight,rotation,mirror;
    public long inferenceMs;
    public float cropLeft,cropTop,cropSize;
    public Pose(float[][] points, long capturedAtMs) {
        if(points==null||points.length!=17)throw new IllegalArgumentException("17个关键点");
        for(float[] point:points)if(point==null||point.length!=3)throw new IllegalArgumentException("关键点需yx和置信度");
        this.points=points;this.capturedAtMs=capturedAtMs;
    }
    public float y(int index) { return points[index][0]; }
    public float x(int index) { return points[index][1]; }
    public float score(int index) { return points[index][2]; }
    public float avgY(int a,int b) { return (y(a)+y(b))/2f; }
    public float avgX(int a,int b) { return (x(a)+x(b))/2f; }
    public boolean reliable(int index,float threshold){return Float.isFinite(x(index))&&Float.isFinite(y(index))&&Float.isFinite(score(index))&&score(index)>=threshold&&x(index)>=0&&x(index)<=1&&y(index)>=0&&y(index)<=1;}
    public float weightedY(int a,int b,float threshold){return weighted(a,b,threshold,0);}
    public float weightedX(int a,int b,float threshold){return weighted(a,b,threshold,1);}
    private float weighted(int a,int b,float threshold,int coordinate){
        float wa=reliable(a,threshold)?score(a):0,wb=reliable(b,threshold)?score(b):0;
        return wa+wb==0?Float.NaN:((wa>0?points[a][coordinate]*wa:0)+(wb>0?points[b][coordinate]*wb:0))/(wa+wb);
    }
    public boolean personVisible(){return (reliable(5,.2f)||reliable(6,.2f))&&(reliable(11,.2f)||reliable(12,.2f));}
    /** Compatibility name for counting evidence; it no longer means every joint must pass. */
    public boolean fullBody(){return PoseQuality.assess(this).countable;}
}
