package org.qingyue.rope.core;

import java.util.*;

/** Timestamp-based two-foot rise/landing detector with independent person acquisition. */
public final class JumpDetector {
    public enum Status { NEED_BODY, CALIBRATING, TRACKING, OCCLUDED, MOVING }
    private Status status=Status.NEED_BODY;
    private final List<Sample> samples=new ArrayList<>();
    private final float[] ground=new float[4];
    private boolean calibrated,airborne,waitForGround;
    private long lastFrame=-1,lastGood=-1,takeoff,lastLanding=-1000;
    private float bodyHeight,groundX,smoothHip,smoothFoot,enterHip=.012f,enterFoot=.008f;
    private int reason=PoseQuality.NO_TORSO;
    private static final int[] JOINTS={11,12,15,16};
    private static final long MAX_UNRELIABLE_MS=800;
    // Pose x is normalized by frame width, y by height. Compare both in height units.
    private static float horizontal(Pose p,PoseQuality q){return q.hipX*(p.frameWidth>0&&p.frameHeight>0?p.frameWidth/(float)p.frameHeight:1f);}
    private static final class Sample {
        final long time;final float[] y=new float[4];final float x,height;
        Sample(Pose p,PoseQuality q){time=p.capturedAtMs;x=horizontal(p,q);height=q.height;
            for(int i=0;i<4;i++)y[i]=p.reliable(JOINTS[i],i<2?.2f:.15f)?p.y(JOINTS[i]):Float.NaN;}
    }
    public JumpDetector(){Arrays.fill(ground,Float.NaN);}
    public void reset(){clearCalibration();status=Status.NEED_BODY;lastFrame=-1;lastGood=-1;lastLanding=-1000;reason=PoseQuality.NO_TORSO;}
    private void clearCalibration(){calibrated=false;airborne=false;waitForGround=false;samples.clear();Arrays.fill(ground,Float.NaN);smoothHip=0;smoothFoot=0;}
    public Status status(){return status;}
    public boolean ready(){return calibrated&&status==Status.TRACKING&&!waitForGround;}
    public int qualityReason(){return reason;}
    public float hipSignal(){return smoothHip;}
    public float footSignal(){return smoothFoot;}
    public float hipThreshold(){return enterHip;}
    public float footThreshold(){return enterFoot;}
    public boolean airborne(){return airborne;}
    public void discardCycle(){airborne=false;waitForGround=true;takeoff=0;smoothHip=0;smoothFoot=0;}
    public boolean update(Pose pose){
        long now=pose.capturedAtMs;if(lastFrame>=0&&now<=lastFrame)return false;
        long dt=lastFrame<0?33:now-lastFrame;lastFrame=now;
        PoseQuality q=PoseQuality.assess(pose);reason=q.reason;
        if(pose.cameraMoving){clearCalibration();status=Status.MOVING;return false;}
        if(!q.countable){
            discardCycle();
            if(calibrated&&lastGood>=0&&now-lastGood<=MAX_UNRELIABLE_MS)status=Status.OCCLUDED;
            else {clearCalibration();status=q.personVisible?Status.CALIBRATING:Status.NEED_BODY;}
            return false;
        }
        if(dt>MAX_UNRELIABLE_MS||(calibrated&&lastGood>=0&&now-lastGood>MAX_UNRELIABLE_MS))clearCalibration();else if(dt>260)discardCycle();
        lastGood=now;
        if(!calibrated){calibrate(pose,q);return false;}
        float x=horizontal(pose,q);
        if(Math.abs(x-groundX)>bodyHeight*.16f||q.height/bodyHeight<.60f||q.height/bodyHeight>1.5f){
            clearCalibration();status=Status.MOVING;return false;
        }
        status=Status.TRACKING;
        float left=(ground[2]-pose.y(15))/bodyHeight,right=(ground[3]-pose.y(16))/bodyHeight;
        float hip=hipRise(pose);
        if(!Float.isFinite(hip)){
            if(left<enterFoot*.6f&&right<enterFoot*.6f)for(int i=0;i<2;i++)if(pose.reliable(JOINTS[i],.2f))ground[i]=pose.y(JOINTS[i]);
            discardCycle();status=Status.OCCLUDED;return false;
        }
        float feet=Math.min(left,right),alpha=(float)(1-Math.exp(-Math.min(dt,200)/40.0));
        smoothHip+=alpha*(hip-smoothHip);smoothFoot+=alpha*(feet-smoothFoot);
        boolean onGround=hip<=enterHip*.6f&&left<=enterFoot*.6f&&right<=enterFoot*.6f;
        if(waitForGround){if(onGround)waitForGround=false;return false;}
        if(airborne){
            long flight=now-takeoff;
            if(flight>900){discardCycle();return false;}
            if(onGround){airborne=false;
                if(flight>=60&&now-lastLanding>=170){lastLanding=now;return true;}
            }
        }else if(smoothHip>=enterHip&&smoothFoot>=enterFoot&&hip>0&&left>0&&right>0&&now-lastLanding>=60){
            airborne=true;takeoff=now;
        }else if(onGround){
            float baselineAlpha=(float)(1-Math.exp(-Math.min(dt,200)/2500.0));
            for(int i=0;i<4;i++)if(pose.reliable(JOINTS[i],i<2?.2f:.15f)){
                if(!Float.isFinite(ground[i]))ground[i]=pose.y(JOINTS[i]);else ground[i]+=baselineAlpha*(pose.y(JOINTS[i])-ground[i]);
            }
            groundX+=baselineAlpha*(x-groundX);bodyHeight+=baselineAlpha*.3f*(q.height-bodyHeight);
        }
        return false;
    }
    private float hipRise(Pose pose){float sum=0,weight=0;
        for(int i=0;i<2;i++)if(pose.reliable(JOINTS[i],.2f)&&Float.isFinite(ground[i])){float w=pose.score(JOINTS[i]);sum+=(ground[i]-pose.y(JOINTS[i]))/bodyHeight*w;weight+=w;}
        return weight==0?Float.NaN:sum/weight;
    }
    private void calibrate(Pose pose,PoseQuality q){
        status=Status.CALIBRATING;samples.add(new Sample(pose,q));
        while(samples.size()>50||(!samples.isEmpty()&&pose.capturedAtMs-samples.get(0).time>1000))samples.remove(0);
        if(samples.size()<6||pose.capturedAtMs-samples.get(0).time<500)return;
        float[] heights=new float[samples.size()],xs=new float[samples.size()];
        for(int i=0;i<samples.size();i++){heights[i]=samples.get(i).height;xs[i]=samples.get(i).x;}
        float height=quantile(heights,.5f);
        if(quantile(xs,.9f)-quantile(xs,.1f)>height*.12f){status=Status.MOVING;return;}
        float hipRange=Float.MAX_VALUE,footRange=0,hipNoise=0,footNoise=0;int hipN=0;
        for(int joint=0;joint<4;joint++){
            float[] ys=new float[samples.size()];for(int i=0;i<ys.length;i++)ys[i]=samples.get(i).y[joint];
            float median=quantile(ys,.5f);if(!Float.isFinite(median))continue;
            float range=quantile(ys,.9f)-quantile(ys,.1f),noise=mad(ys,median)/height;
            if(joint<2){hipRange=Math.min(hipRange,range);hipNoise+=noise;hipN++;}
            else {footRange=Math.max(footRange,range);footNoise=Math.max(footNoise,noise);}
            ground[joint]=quantile(ys,.70f);
        }
        if(hipN==0||hipRange>height*.10f||footRange>height*.08f)return;
        bodyHeight=height;groundX=quantile(xs,.5f);
        enterHip=Math.max(.012f,Math.min(.04f,hipNoise/hipN*3.2f));enterFoot=Math.max(.008f,Math.min(.03f,footNoise*3.2f));
        calibrated=true;status=Status.TRACKING;waitForGround=true;smoothHip=0;smoothFoot=0;samples.clear();
    }
    private static float mad(float[] values,float median){float[] deviations=new float[values.length];for(int i=0;i<values.length;i++)deviations[i]=Float.isFinite(values[i])?Math.abs(values[i]-median):Float.NaN;return quantile(deviations,.5f);}
    private static float quantile(float[] values,float fraction){float[] valid=new float[values.length];int n=0;for(float v:values)if(Float.isFinite(v))valid[n++]=v;if(n==0)return Float.NaN;Arrays.sort(valid,0,n);return valid[Math.min(n-1,Math.round((n-1)*fraction))];}
}
