import org.qingyue.rope.core.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Test-only desktop bridge. Uses the exact RGB/crop/quality/detector classes shipped in the APK. */
public final class ImagePipelineTool {
    private static FrameGeometry.RgbFrame frame;
    private static final JumpDetector detector=new JumpDetector();
    public static void main(String[] args)throws Exception{
        BufferedReader input=new BufferedReader(new InputStreamReader(System.in,"UTF-8"));String line;
        while((line=input.readLine())!=null){String[] p=line.split("\t");
            if(p[0].equals("image")){BufferedImage b=ImageIO.read(new File(p[1]));frame=new FrameGeometry.RgbFrame(b.getWidth(),b.getHeight(),b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth()));System.out.println("{\"width\":"+frame.width+",\"height\":"+frame.height+"}");}
            else if(p[0].equals("reset")){detector.reset();System.out.println("{}");}
            else if(p[0].equals("prepare")){FrameGeometry.Crop c=crop(p);Files.write(Paths.get(p[4]),FrameGeometry.modelRgb(frame,c,192,null));System.out.println("{}");}
            else if(p[0].equals("step")){FrameGeometry.Crop c=crop(p);float[][] joints=new float[17][3];try(DataInputStream in=new DataInputStream(new FileInputStream(p[5]))){for(int i=0;i<17;i++){joints[i][0]=c.globalY(in.readFloat(),frame.height);joints[i][1]=c.globalX(in.readFloat(),frame.width);joints[i][2]=in.readFloat();}}
                Pose pose=new Pose(joints,Long.parseLong(p[4]));pose.frameWidth=frame.width;pose.frameHeight=frame.height;PoseQuality q=PoseQuality.assess(pose);boolean jump=detector.update(pose);FrameGeometry.Crop next=FrameGeometry.nextCrop(pose,frame.width,frame.height);
                StringBuilder out=new StringBuilder(String.format(Locale.ROOT,"{\"person\":%s,\"countable\":%s,\"reason\":%d,\"old_gate\":%s,\"jump\":%s,\"ready\":%s,\"height\":%.6f,\"status\":%d,\"crop\":[%.6f,%.6f,%.6f],\"points\":[",q.personVisible,q.countable,q.reason,oldGate(pose),jump,detector.ready(),q.height,detector.status().ordinal(),next.left,next.top,next.size));
                for(int i=0;i<17;i++){if(i>0)out.append(',');out.append(String.format(Locale.ROOT,"[%.6f,%.6f,%.6f]",joints[i][0],joints[i][1],joints[i][2]));}System.out.println(out.append("]}"));
            }else throw new IllegalArgumentException("Unknown command");
        }
    }
    private static FrameGeometry.Crop crop(String[] p){return new FrameGeometry.Crop(Float.parseFloat(p[1]),Float.parseFloat(p[2]),Float.parseFloat(p[3]));}
    /** Original 0.1.0 admission rule, retained only to reproduce the reported rejection. */
    private static boolean oldGate(Pose p){for(int i:new int[]{0,5,6,11,12,13,14,15,16})if(!p.reliable(i,.32f)||p.x(i)<.025f||p.x(i)>.975f||p.y(i)<.025f||p.y(i)>.975f)return false;
        float shoulder=(p.y(5)+p.y(6))/2,hip=(p.y(11)+p.y(12))/2,knee=(p.y(13)+p.y(14))/2,foot=(p.y(15)+p.y(16))/2,height=foot-shoulder;
        return height>.30f&&hip-shoulder>.07f&&knee>hip&&foot>knee&&Math.abs(p.y(15)-p.y(16))<height*.15f;
    }
}
