import org.qingyue.rope.core.*;
import java.util.*;
import java.io.*;

/** No Android/JUnit dependency. Exercises the shipped Java classes directly on the JVM. */
public final class CoreTests {
    private static int assertions,cases;
    private interface Test {void run()throws Exception;}
    private static void test(String name,Test action)throws Exception{action.run();cases++;System.out.println("PASS "+name);}
    private static void eq(long expected,long actual,String why){assertions++;if(expected!=actual)throw new AssertionError(why+": expected "+expected+", got "+actual);}
    private static void yes(boolean actual,String why){assertions++;if(!actual)throw new AssertionError(why);}
    private static void no(boolean actual,String why){yes(!actual,why);}
    private static void phase(SessionEngine.Phase expected,SessionEngine e){assertions++;if(expected!=e.phase())throw new AssertionError("Expected "+expected+", got "+e.phase());}
    private static Pose pose(long t,float hipRise,float footRise){
        float[][] p=new float[17][3];for(int i=0;i<17;i++){p[i][0]=.3f;p[i][1]=.5f;p[i][2]=.95f;}
        p[0][0]=.10f;p[5][0]=p[6][0]=.25f-hipRise;p[5][1]=.40f;p[6][1]=.60f;
        p[11][0]=p[12][0]=.50f-hipRise;p[11][1]=.43f;p[12][1]=.57f;
        p[13][0]=p[14][0]=.70f-(hipRise+footRise)/2;p[13][1]=.44f;p[14][1]=.56f;
        p[15][0]=p[16][0]=.90f-footRise;p[15][1]=.45f;p[16][1]=.55f;return new Pose(p,t);
    }
    private static JumpDetector calibrated(){JumpDetector d=new JumpDetector();for(int t=0;t<=800;t+=50)d.update(pose(t,0,0));yes(d.ready(),"stable pose calibrates");return d;}
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
        test("loss, low confidence, NaN, cropped feet require calibration",()->{
            JumpDetector d=calibrated();Pose missing=pose(1000,0,0);missing.points[15][2]=.1f;no(d.update(missing),"occlusion");no(d.ready(),"not ready after loss");
            Pose nan=pose(1100,0,0);nan.points[11][0]=Float.NaN;no(nan.fullBody(),"NaN");Pose cropped=pose(1200,0,0);cropped.points[15][0]=1.03f;no(cropped.fullBody(),"cropped feet");
        });
        test("stale/out-of-order detector frames cannot complete a cycle",()->{
            JumpDetector d=calibrated();no(d.update(pose(1000,.05f,.05f)),"takeoff");no(d.update(pose(900,0,0)),"out of order");no(d.update(pose(1400,0,0)),"large frame gap resets");no(d.ready(),"recalibrate after gap");
        });
        test("detector reset at rest/pause prevents cross-boundary landing",()->{
            JumpDetector d=calibrated();d.update(pose(1000,.05f,.05f));d.reset();no(d.update(pose(1100,0,0)),"old cycle forgotten");no(d.ready(),"must stand still again");
        });
        test("work boundary keeps calibration but waits for a fresh grounded cycle",()->{
            JumpDetector d=calibrated();d.update(pose(1000,.05f,.05f));d.discardCycle();
            yes(d.ready(),"stable calibration retained");no(d.update(pose(1100,.04f,.04f)),"flight started before work");
            no(d.update(pose(1200,0,0)),"pre-work landing not counted");
            no(d.update(pose(1400,.05f,.05f)),"next takeoff");yes(d.update(pose(1600,0,0)),"fresh work cycle counted");
        });
        System.out.println("RESULT "+cases+" cases, "+assertions+" assertions passed. Synthetic keypoints only; not human accuracy.");
    }
}
