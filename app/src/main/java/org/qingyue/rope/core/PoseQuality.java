package org.qingyue.rope.core;

/** Person acquisition is separate from evidence needed to count a two-foot jump. */
public final class PoseQuality {
    public static final int OK=0,NO_TORSO=1,FEET_MISSING=2,TOO_SMALL=3,NOT_UPRIGHT=4,CAMERA_MOVING=5;
    public final boolean personVisible,countable;
    public final int reason;
    public final float hipX,hipY,footY,height;
    private PoseQuality(boolean visible,boolean countable,int reason,float x,float hip,float foot,float height){this.personVisible=visible;this.countable=countable;this.reason=reason;hipX=x;hipY=hip;footY=foot;this.height=height;}
    public static PoseQuality assess(Pose p){
        boolean torso=(p.reliable(5,.2f)||p.reliable(6,.2f))&&(p.reliable(11,.2f)||p.reliable(12,.2f));
        if(!torso)return new PoseQuality(false,false,NO_TORSO,0,0,0,0);
        float shoulder=p.weightedY(5,6,.2f),hip=p.weightedY(11,12,.2f),x=p.weightedX(11,12,.2f);
        if(hip-shoulder<.015f)return new PoseQuality(true,false,NOT_UPRIGHT,x,hip,0,0);
        if(!p.reliable(15,.15f)||!p.reliable(16,.15f))return new PoseQuality(true,false,FEET_MISSING,x,hip,0,0);
        float foot=p.avgY(15,16),height=foot-shoulder;
        if(height<.08f)return new PoseQuality(true,false,TOO_SMALL,x,hip,foot,height);
        if(foot<hip+height*.12f)return new PoseQuality(true,false,NOT_UPRIGHT,x,hip,foot,height);
        if(p.cameraMoving)return new PoseQuality(true,false,CAMERA_MOVING,x,hip,foot,height);
        return new PoseQuality(true,true,OK,x,hip,foot,height);
    }
}
