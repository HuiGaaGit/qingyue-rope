import org.qingyue.rope.core.*;
import java.util.*;
import java.io.*;
import java.nio.ByteBuffer;

/** No Android/JUnit dependency. Exercises the shipped Java classes directly on the JVM. */
public final class CoreTests {
    private static int assertions,cases;
    private static final List<String> failures=new ArrayList<>();
    private interface Test {void run()throws Exception;}
    private static void test(String name,Test action)throws Exception{cases++;try{action.run();System.out.println("PASS "+name);}catch(AssertionError error){failures.add(name+": "+error.getMessage());System.out.println("FAIL "+name+": "+error.getMessage());}}
    private static void eq(long expected,long actual,String why){assertions++;if(expected!=actual)throw new AssertionError(why+": expected "+expected+", got "+actual);}
    private static void yes(boolean actual,String why){assertions++;if(!actual)throw new AssertionError(why);}
    private static void no(boolean actual,String why){yes(!actual,why);}
    private static void near(float expected,float actual,float tolerance,String why){assertions++;if(Math.abs(expected-actual)>tolerance)throw new AssertionError(why+": "+actual+" != "+expected);}
    private static void phase(SessionEngine.Phase expected,SessionEngine e){assertions++;if(expected!=e.phase())throw new AssertionError("Expected "+expected+", got "+e.phase());}
    private static Pose pose(long t,float hipRise,float footRise){
        float[][] p=new float[17][3];for(int i=0;i<17;i++){p[i][0]=.3f;p[i][1]=.5f;p[i][2]=.95f;}
        p[0][0]=.10f;p[5][0]=p[6][0]=.25f-hipRise;p[5][1]=.40f;p[6][1]=.60f;
        p[11][0]=p[12][0]=.50f-hipRise;p[11][1]=.43f;p[12][1]=.57f;
        p[13][0]=p[14][0]=.70f-(hipRise+footRise)/2;p[13][1]=.44f;p[14][1]=.56f;
        p[15][0]=p[16][0]=.90f-footRise;p[15][1]=.45f;p[16][1]=.55f;return new Pose(p,t);
    }
    private static JumpDetector calibrated(){JumpDetector d=new JumpDetector();for(int t=0;t<=800;t+=50)d.update(pose(t,0,0));yes(d.ready(),"stable pose calibrates");return d;}
    private static Pose framedPose(long time,int width,int height,float horizontalInHeight){Pose p=pose(time,0,0);p.frameWidth=width;p.frameHeight=height;
        for(float[] joint:p.points)joint[1]=.5f+(joint[1]-.5f+horizontalInHeight)*height/width;return p;}
    private static int periodic(int jumps,int rpm,int fps,float hipAmp,float footAmp){
        JumpDetector detector=calibrated();int count=0;double period=60000.0/rpm;
        long duration=Math.round(jumps*period);
        for(long t=0;t<=duration+200;t+=1000/fps){
            float wave=t>=duration?0:(float)Math.sin(Math.PI*Math.min(1,(t%period)/(period*.72)));
            if(detector.update(pose(900+t,hipAmp*wave,footAmp*wave)))count++;
        }
        return count;
    }
    public static void main(String[] args)throws Exception{
        test("invalid training settings rejected",()->{boolean failed=false;try{new Plan(Plan.Mode.INTERVAL,60,0,3);}catch(IllegalArgumentException e){failed=true;}yes(failed,"zero rest");});
        test("free training counts only after preparation",()->{
            SessionEngine e=new SessionEngine(new Plan(Plan.Mode.FREE,60,15,3),1000);e.start(0);phase(SessionEngine.Phase.COUNTDOWN,e);
            no(e.jump(2500,2500),"preparation never counts");e.tick(3000);phase(SessionEngine.Phase.WORK,e);eq(0,e.workMs(),"countdown excluded");
            yes(e.jump(3200,3200),"work counts");e.tick(63000);phase(SessionEngine.Phase.WORK,e);eq(60000,e.workMs(),"free has no timer limit");
        });
        test("timed training exact deadline and late image",()->{
            SessionEngine e=new SessionEngine(new Plan(Plan.Mode.TIMED,30,15,1),0);e.start(100);e.tick(3100);yes(e.jump(3200,3200),"active jump");
            no(e.jump(33100,33000),"processed at finish is rejected");phase(SessionEngine.Phase.FINISHED,e);eq(30000,e.workMs(),"exact duration");yes(e.completed(),"automatic completion");
        });
        test("interval rest cannot count and has no final rest",()->{
            SessionEngine e=new SessionEngine(new Plan(Plan.Mode.INTERVAL,10,5,2),0);e.start(0);e.tick(3000);yes(e.jump(4000,4000),"round one");
            e.tick(13000);phase(SessionEngine.Phase.REST,e);no(e.jump(14000,14000),"rest jump");e.tick(18000);phase(SessionEngine.Phase.WORK,e);eq(2,e.round(),"second round");
            no(e.jump(18100,17999),"image captured during rest");yes(e.jump(18200,18200),"round two");e.tick(28000);
            phase(SessionEngine.Phase.FINISHED,e);eq(20000,e.workMs(),"two work phases");eq(5000,e.restMs(),"one rest phase");eq(2,e.total(),"no rest counts");eq(1,e.roundCounts()[1],"round split");
        });
        test("pause and manual continue exclude wall time",()->{
            SessionEngine e=new SessionEngine(new Plan(Plan.Mode.TIMED,30,15,1),0);e.start(0);e.tick(5000);e.pause(5500);phase(SessionEngine.Phase.PAUSED,e);e.tick(600000);
            eq(2500,e.workMs(),"background excluded");no(e.jump(600010,600010),"paused jump");e.resume(700000);
            no(e.jump(700100,699999),"pre-resume stale image");yes(e.jump(700150,700150),"post-resume jump");e.tick(701000);eq(3500,e.workMs(),"only resumed elapsed");
        });
        test("pause during rest preserves remaining rest",()->{
            SessionEngine e=new SessionEngine(new Plan(Plan.Mode.INTERVAL,10,5,2),0);e.start(0);e.pause(15000);phase(SessionEngine.Phase.PAUSED,e);eq(3000,e.remainingMs(),"rest remaining");
            e.resume(100000);e.tick(102999);phase(SessionEngine.Phase.REST,e);no(e.jump(102999,102999),"rest still excluded");e.tick(103000);phase(SessionEngine.Phase.WORK,e);
        });
        test("paused countdown is not training time",()->{
            SessionEngine e=new SessionEngine(new Plan(Plan.Mode.TIMED,30,0,1),0);e.start(0);e.pause(1000);e.resume(50000);e.tick(51999);phase(SessionEngine.Phase.COUNTDOWN,e);e.tick(52000);phase(SessionEngine.Phase.WORK,e);eq(0,e.workMs(),"countdown pause");
        });
        test("large scheduler delay consumes exact bounded plan",()->{
            SessionEngine e=new SessionEngine(new Plan(Plan.Mode.INTERVAL,10,5,3),0);e.start(0);e.tick(900000);phase(SessionEngine.Phase.FINISHED,e);eq(30000,e.workMs(),"bounded work");eq(10000,e.restMs(),"bounded rests");
        });
        test("old and future frame timestamps rejected",()->{
            SessionEngine e=new SessionEngine(new Plan(Plan.Mode.TIMED,30,0,1),0);e.start(0);e.tick(4000);no(e.jump(5000,4400),"stale by 600ms");no(e.jump(5000,5001),"future");yes(e.jump(5000,4500),"500ms boundary");
        });
        test("monotonic rollback cannot add elapsed time",()->{
            SessionEngine e=new SessionEngine(new Plan(Plan.Mode.FREE,60,0,1),0);e.start(0);e.tick(5000);e.tick(4000);eq(2000,e.workMs(),"clock rollback ignored");
        });
        test("process recovery always paused, same counts and remaining",()->{
            SessionEngine e=new SessionEngine(new Plan(Plan.Mode.INTERVAL,10,5,2),123456);e.start(0);e.jump(4000,4000);e.tick(16000);
            SessionEngine recovered=SessionEngine.restore(e.snapshot(),900000);phase(SessionEngine.Phase.PAUSED,recovered);eq(1,recovered.total(),"saved counts");eq(2000,recovered.remainingMs(),"saved rest remaining");
            recovered.tick(999000);eq(3000,recovered.restMs(),"downtime excluded");recovered.resume(1000000);recovered.tick(1002000);phase(SessionEngine.Phase.WORK,recovered);yes(e.id.equals(recovered.id),"stable session id");
        });
        test("manual finish idempotent without fake completed flag",()->{
            SessionEngine e=new SessionEngine(new Plan(Plan.Mode.FREE,60,0,1),0);e.start(0);e.jump(5000,5000);e.finish(6000);e.finish(9000);eq(3000,e.workMs(),"finish time frozen");no(e.completed(),"manual finish");
        });
        test("archive checkpoint and exactly-once final commit",()->{
            SessionEngine e=new SessionEngine(new Plan(Plan.Mode.FREE,60,0,1),0);e.start(0);e.jump(5000,5000);
            ArchiveCodec.Archive a=new ArchiveCodec.Archive(Collections.emptyList(),null).checkpoint(e);
            ArchiveCodec.Archive loaded=ArchiveCodec.decode(ArchiveCodec.encode(a));yes(loaded.active!=null,"active stored");eq(0,loaded.history.size(),"no premature history");
            e.finish(6000);WorkoutRecord r=new WorkoutRecord(e);a=loaded.commit(r).commit(r);loaded=ArchiveCodec.decode(ArchiveCodec.encode(a));eq(1,loaded.history.size(),"retry upsert");yes(loaded.active==null,"active cleared in same transaction");eq(1,loaded.history.get(0).total,"history count");
        });
        test("archive checksum and truncated input rejected",()->{
            byte[] data=ArchiveCodec.encode(new ArchiveCodec.Archive(Collections.emptyList(),null));data[8]^=1;
            boolean failed=false;try{ArchiveCodec.decode(data);}catch(IOException e){failed=true;}yes(failed,"CRC corruption");failed=false;try{ArchiveCodec.decode(new byte[3]);}catch(IOException e){failed=true;}yes(failed,"truncation");
        });
        test("finished recovery remains ready for idempotent commit",()->{
            SessionEngine e=new SessionEngine(new Plan(Plan.Mode.TIMED,10,0,1),0);e.start(0);e.tick(13000);SessionEngine r=SessionEngine.restore(e.snapshot(),0);phase(SessionEngine.Phase.FINISHED,r);yes(r.completed(),"completion persisted");
        });
        for(int fps:new int[]{15,30})for(int rpm:new int[]{60,120,180,220}){
            final int f=fps,r=rpm;test("synthetic 20 jumps "+r+"rpm "+f+"fps",()->eq(20,periodic(20,r,f,.055f,.055f),"synthetic jump count"));
        }
        test("synthetic hips-only squat is not a jump",()->eq(0,periodic(20,120,30,.055f,0),"ankles remain grounded"));
        test("synthetic feet-only motion is not a jump",()->eq(0,periodic(20,120,30,0,.055f),"hips remain grounded"));
        test("synthetic small jitter never counts",()->eq(0,periodic(20,180,30,.006f,.006f),"jitter threshold"));
        test("short ankle loss suspends counting, one valid hip remains usable",()->{
            JumpDetector d=calibrated();Pose missing=pose(1000,0,0);missing.points[15][2]=.1f;no(d.update(missing),"occlusion");no(d.ready(),"not ready after loss");
            Pose nan=pose(1100,0,0);nan.points[11][0]=Float.NaN;yes(nan.fullBody(),"one valid hip is sufficient");nan.points[12][0]=Float.NaN;no(nan.fullBody(),"both hips invalid");Pose cropped=pose(1200,0,0);cropped.points[15][0]=1.03f;no(cropped.fullBody(),"cropped feet");
        });
        test("stale/out-of-order detector frames cannot complete a cycle",()->{
            JumpDetector d=calibrated();no(d.update(pose(1000,.05f,.05f)),"takeoff");no(d.update(pose(900,0,0)),"out of order");no(d.update(pose(1400,0,0)),"gap cancels airborne cycle");yes(d.ready(),"short gap keeps valid baseline after landing");no(d.update(pose(2500,0,0)),"long gap");no(d.ready(),"long gap recalibrates");
        });
        test("detector reset at rest/pause prevents cross-boundary landing",()->{
            JumpDetector d=calibrated();d.update(pose(1000,.05f,.05f));d.reset();no(d.update(pose(1100,0,0)),"old cycle forgotten");no(d.ready(),"must stand still again");
        });
        test("work boundary keeps calibration but waits for a fresh grounded cycle",()->{
            JumpDetector d=calibrated();d.update(pose(1000,.05f,.05f));d.discardCycle();
            no(d.ready(),"retained calibration still requires ground");no(d.update(pose(1100,.04f,.04f)),"flight started before work");
            no(d.update(pose(1200,0,0)),"pre-work landing not counted");
            no(d.update(pose(1400,.05f,.05f)),"next takeoff");yes(d.update(pose(1600,0,0)),"fresh work cycle counted");
        });
        for(int fps:new int[]{15,24,30}){final int f=fps;test("low amplitude 20 jumps at "+f+"fps",()->eq(20,periodic(20,150,f,.014f,.012f),"low amplitude"));}
        test("nose, knees and one shoulder/hip may be absent",()->{
            JumpDetector d=new JumpDetector();int count=0;
            for(int t=0;t<6000;t+=33){float wave=t<1000?0:(float)Math.sin(Math.PI*Math.min(1,((t-1000)%500)/360f));Pose p=pose(t,.04f*wave,.04f*wave);
                for(int i:new int[]{0,5,11,13,14})p.points[i][2]=.01f;
                yes(p.personVisible(),"torso found");if(d.update(p))count++;
            }eq(10,count,"partial noncritical joints");
        });
        test("short occlusion cancels only interrupted cycle and recovers on ground",()->{
            JumpDetector d=calibrated();d.update(pose(900,.05f,.05f));Pose p=pose(966,.06f,.06f);p.points[15][2]=.05f;no(d.update(p),"lost foot");
            eq(JumpDetector.Status.OCCLUDED.ordinal(),d.status().ordinal(),"short loss state");no(d.update(pose(1032,0,0)),"occluded landing never counted");yes(d.ready(),"no fresh 600ms wait");
            no(d.update(pose(1100,.05f,.05f)),"new takeoff");yes(d.update(pose(1232,0,0)),"new visible cycle");
        });
        test("alternating walking feet and lateral movement are rejected",()->{
            JumpDetector d=calibrated();int count=0;for(int t=900;t<7000;t+=33){float v=(float)Math.sin((t-900)*Math.PI/250);Pose p=pose(t,.018f*Math.abs(v),0);p.points[15][0]-=.06f*Math.max(0,v);p.points[16][0]-=.06f*Math.max(0,-v);if(d.update(p))count++;}eq(0,count,"alternating feet");
            Pose p=pose(7100,0,0);for(float[] joint:p.points)joint[1]+=.15f;no(d.update(p),"lateral walk");eq(JumpDetector.Status.MOVING.ordinal(),d.status().ordinal(),"move feedback");
        });
        test("variable frame interval uses elapsed time",()->{
            JumpDetector d=calibrated();int count=0,n=0;for(long t=0;t<=10200;t+=new int[]{22,45,31,68,29}[n++%5]){float v=t>=10000?0:(float)Math.sin(Math.PI*Math.min(1,(t%500)/360f));if(d.update(pose(900+t,.04f*v,.04f*v)))count++;}eq(20,count,"variable fps");
        });
        test("long occlusion recalibrates without changing session totals",()->{
            SessionEngine e=new SessionEngine(new Plan(Plan.Mode.FREE,60,0,1),0);e.start(0);e.jump(3500,3500);JumpDetector d=calibrated();Pose p=pose(2000,0,0);p.points[15][2]=0;no(d.update(p),"long loss");no(d.ready(),"calibration expired");eq(1,e.total(),"session count retained");
        });
        test("camera move flag cancels flight",()->{JumpDetector d=calibrated();d.update(pose(900,.05f,.05f));Pose p=pose(970,0,0);p.cameraMoving=true;no(d.update(p),"camera motion");no(d.ready(),"recalibration");no(d.update(pose(1040,0,0)),"no phantom landing");});
        test("portrait person scale is not an acquisition rejection",()->{
            Pose p=pose(0,0,0);for(float[] j:p.points)j[0]=.3f+j[0]*.25f;yes(p.personVisible(),"small torso");yes(p.fullBody(),"small complete body");
            p.points[15][2]=0;yes(p.personVisible(),"found person without foot");no(p.fullBody(),"feet needed only for jump readiness");
        });
        test("crop unprojection preserves world motion",()->{
            for(FrameGeometry.Crop crop:new FrameGeometry.Crop[]{FrameGeometry.Crop.full(480,640),new FrameGeometry.Crop(130,210,240),new FrameGeometry.Crop(-20,100,420)}){
                near(.37f,crop.globalX(crop.modelX(.37f,480),480),.00001f,"global x");near(.72f,crop.globalY(crop.modelY(.72f,640),640),.00001f,"global y");}
        });
        test("UINT8 RGB channel order and square zero padding",()->{
            FrameGeometry.RgbFrame f=new FrameGeometry.RgbFrame(2,1,new int[]{0xffff0000,0xff0000ff});byte[] rgb=FrameGeometry.modelRgb(f,new FrameGeometry.Crop(0,0,2),2,null);
            eq(255,rgb[0]&255,"red");eq(0,rgb[1]&255,"green");eq(255,rgb[5]&255,"blue");eq(0,rgb[6]&255,"outside zero");
        });
        test("YUV row/pixel strides, buffer position, crop and four rotations",()->{
            byte[] y=new byte[2+6*4];Arrays.fill(y,(byte)16);int[] levels={40,60,80,100};for(int row=0;row<2;row++)for(int col=0;col<2;col++)y[2+(row+1)*6+col+1]=(byte)levels[row*2+col];
            ByteBuffer yy=ByteBuffer.wrap(y);yy.position(2);byte[] uv=new byte[1+4*2];Arrays.fill(uv,(byte)128);ByteBuffer uu=ByteBuffer.wrap(uv);uu.position(1);
            int[][] expected={{0,1,2,3},{2,0,3,1},{3,2,1,0},{1,3,0,2}};
            for(int r=0;r<4;r++){FrameGeometry.RgbFrame f=FrameGeometry.yuv420(4,4,1,1,2,2,new FrameGeometry.Plane(yy,6,1),new FrameGeometry.Plane(uu,4,2),new FrameGeometry.Plane(uu,4,2),r*90,null);
                for(int i=0;i<4;i++)eq(((levels[expected[r][i]]-16)*298+128)>>8,f.pixels[i]&255,"rotated luminance");}
            eq(270,FrameGeometry.rawRotation(90,180,true),"front raw rotation");eq(270,FrameGeometry.rawRotation(90,180,false),"back raw rotation");
            eq(180,FrameGeometry.rawRotation(90,90,true),"front plus display");eq(0,FrameGeometry.rawRotation(90,90,false),"back minus display");
        });
        test("textured background motion is detected; centre motion is excluded",()->{
            int w=96,h=128;int[] a=new int[w*h],b=new int[w*h];Random random=new Random(42);for(int i=0;i<a.length;i++){int v=random.nextInt(256);a[i]=0xff000000|v*0x010101;}
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)b[y*w+x]=a[y*w+Math.max(0,x-1)];CameraMotionGuard guard=new CameraMotionGuard();no(guard.update(new FrameGeometry.RgbFrame(w,h,a)),"first frame");yes(guard.update(new FrameGeometry.RgbFrame(w,h,b)),"global translation");
            guard=new CameraMotionGuard();guard.update(new FrameGeometry.RgbFrame(w,h,a));b=a.clone();for(int y=0;y<h;y++)for(int x=w/3;x<w*2/3;x++)b[y*w+x]=0xffeeeeee;no(guard.update(new FrameGeometry.RgbFrame(w,h,b)),"central person movement");
        });
        test("numeric diagnostics are bounded, relative and explicitly clearable",()->{
            NumericDiagnostics trace=new NumericDiagnostics();JumpDetector d=calibrated();for(int i=0;i<300;i++)trace.sample(pose(100000+i*250,0,0),d,2,7);
            eq(240,trace.size(),"bounded RAM");String csv=trace.csv();yes(csv.startsWith("elapsed_ms,phase,total,status,reason"),"numeric schema");eq(242,csv.split("\n",-1).length,"header plus bounded rows");trace.clear();eq(0,trace.size(),"clear removes trace");trace.sample(pose(900000,0,0),d,2,0);yes(trace.csv().contains("\n0,2,0,"),"relative clock restarts");
        });
        test("reliable return after long loss cannot reuse expired calibration",()->{
            JumpDetector d=calibrated();Pose missing=pose(900,0,0);missing.points[15][2]=0;no(d.update(missing),"first missing frame");
            no(d.update(pose(1701,0,0)),"reappearing frame");no(d.ready(),"901ms without reliable evidence must recalibrate, despite only one bad sample");
        });
        test("exact short-loss boundary preserves calibration but never completes old flight",()->{
            JumpDetector d=calibrated();d.update(pose(900,.05f,.05f));Pose missing=pose(1000,0,0);missing.points[15][2]=0;d.update(missing);
            no(d.update(pose(1700,0,0)),"800ms unreliable interval has no count");yes(d.ready(),"within retention limit and grounded");
        });
        test("body-relative horizontal gate has equal physical scale across aspect ratios",()->{
            for(int[] dimensions:new int[][]{{360,640},{640,360},{640,640}}){int w=dimensions[0],h=dimensions[1];JumpDetector d=new JumpDetector();
                for(int t=0;t<=800;t+=50)d.update(framedPose(t,w,h,0));yes(d.ready(),"framed calibration");
                no(d.update(framedPose(900,w,h,.65f*.13f)),"small lateral shift");yes(d.ready(),"same allowed body-relative shift for portrait/landscape");
                d=new JumpDetector();for(int t=0;t<=800;t+=50)d.update(framedPose(t,w,h,0));no(d.update(framedPose(900,w,h,.65f*.18f)),"large lateral shift");eq(JumpDetector.Status.MOVING.ordinal(),d.status().ordinal(),"same movement threshold across aspect ratios");
            }
        });
        test("diagnostics expose torso confidence and distinguish acquisition from readiness",()->{
            NumericDiagnostics trace=new NumericDiagnostics();JumpDetector d=calibrated();Pose p=pose(1000,0,0);p.points[5][2]=p.points[6][2]=0;d.update(p);trace.sample(p,d,2,7);
            String[] lines=trace.csv().split("\n"),columns=lines[0].split(","),values=lines[1].split(",");eq(columns.length,values.length,"CSV field alignment");Map<String,String> row=new HashMap<>();for(int i=0;i<columns.length;i++)row.put(columns[i],values[i]);
            yes(row.containsKey("shoulder_l_score")&&row.containsKey("shoulder_r_score"),"missing shoulder scores observable");near(0,Float.parseFloat(row.get("shoulder_l_score")),.001f,"low shoulder");near(.95f,Float.parseFloat(row.get("hip_l_score")),.001f,"hip still visible");
            eq(0,Integer.parseInt(row.get("person_visible")),"no torso acquisition");eq(0,Integer.parseInt(row.get("countable")),"no counting evidence");eq(0,Integer.parseInt(row.get("ready")),"not ready");
        });
        if(!failures.isEmpty())throw new AssertionError("RESULT "+failures.size()+" failed cases: "+failures);
        System.out.println("RESULT "+cases+" cases, "+assertions+" assertions passed. JVM state, synthetic keypoints and pixel fixtures; not human accuracy.");
    }
}
