package org.qingyue.rope.core;

/** Conservative two-foot rise/landing detector. Counts jump cycles, not rope rotations. */
public final class JumpDetector {
    public enum Status { NEED_BODY, CALIBRATING, TRACKING }
    private Status status=Status.NEED_BODY;
    private float groundHip,groundFoot,groundX,bodyHeight;
    private long firstStable=-1,lastFrame=-1,takeoff=-1,lastLanding=-1000;
    private int stableFrames;
    private boolean airborne,waitForGround;
    public void reset() {
        status=Status.NEED_BODY;stableFrames=0;firstStable=-1;lastFrame=-1;airborne=false;takeoff=-1;
        lastLanding=-1000;waitForGround=false;
    }
    public Status status(){return status;}
    public boolean ready(){return status==Status.TRACKING;}
    /** Preserve a stable calibration but never carry a pre-work flight across the boundary. */
    public void discardCycle(){airborne=false;takeoff=-1;waitForGround=true;lastLanding=-1000;}
    public boolean update(Pose pose) {
        long now=pose.capturedAtMs;
        if(lastFrame>=0 && now<=lastFrame)return false;
        if(!pose.fullBody()){reset();return false;}
        if(lastFrame>=0&&now-lastFrame>250)reset();
        lastFrame=now;
        float hip=pose.avgY(11,12),foot=pose.avgY(15,16),x=pose.avgX(11,12);
        float height=foot-pose.avgY(5,6);
        if(status!=Status.TRACKING) {
            status=Status.CALIBRATING;
            if(stableFrames==0||Math.abs(hip-groundHip)>height*0.025f||Math.abs(foot-groundFoot)>height*0.025f) {
                firstStable=now;stableFrames=1;groundHip=hip;groundFoot=foot;groundX=x;bodyHeight=height;
            } else {
                stableFrames++;
                groundHip+=(hip-groundHip)/stableFrames;groundFoot+=(foot-groundFoot)/stableFrames;
                groundX+=(x-groundX)/stableFrames;bodyHeight+=(height-bodyHeight)/stableFrames;
            }
            if(stableFrames>=9&&now-firstStable>=600)status=Status.TRACKING;
            return false;
        }
        if(Math.abs(x-groundX)>bodyHeight*0.24f || height/bodyHeight<0.72f || height/bodyHeight>1.28f) {
            reset();return false;
        }
        float hipRise=(groundHip-hip)/bodyHeight,footRise=(groundFoot-foot)/bodyHeight;
        if(waitForGround){
            if(Math.abs(hipRise)<0.022f&&Math.abs(footRise)<0.014f)waitForGround=false;
            return false;
        }
        if(airborne) {
            long flight=now-takeoff;
            if(flight>850) { reset();return false; }
            if(footRise<0.014f&&hipRise<0.022f) {
                airborne=false;
                if(flight>=80&&now-lastLanding>=180) {lastLanding=now;return true;}
            }
        } else if(hipRise>0.035f&&footRise>0.025f && now-lastLanding>=80) {
            airborne=true;takeoff=now;
        } else if(Math.abs(hipRise)<0.03f&&Math.abs(footRise)<0.02f) {
            // A slow grounded baseline handles drift without learning an airborne pose.
            groundHip+=0.018f*(hip-groundHip);groundFoot+=0.018f*(foot-groundFoot);
            groundX+=0.01f*(x-groundX);bodyHeight+=0.01f*(height-bodyHeight);
        }
        return false;
    }
}
