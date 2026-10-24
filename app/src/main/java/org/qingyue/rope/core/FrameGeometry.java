package org.qingyue.rope.core;

import java.nio.ByteBuffer;

/** Shared pixel pipeline used by Android and desktop image regression tests. */
public final class FrameGeometry {
    private static final int[] CHANNEL_SHIFTS={16,8,0};
    private FrameGeometry(){}
    public static final class Plane {
        final ByteBuffer data;final int rowStride,pixelStride,offset;
        public Plane(ByteBuffer data,int rowStride,int pixelStride){
            if(rowStride<1||pixelStride<1)throw new IllegalArgumentException("Invalid plane stride");
            this.data=data.duplicate();this.offset=data.position();this.rowStride=rowStride;this.pixelStride=pixelStride;
        }
        int sample(int x,int y){int at=offset+y*rowStride+x*pixelStride;if(at<offset||at>=data.limit())throw new IllegalArgumentException("YUV plane truncated");return data.get(at)&255;}
    }
    public static final class RgbFrame {
        public final int width,height;public final int[] pixels;
        public RgbFrame(int width,int height,int[] pixels){
            if(width<1||height<1||pixels.length<width*height)throw new IllegalArgumentException("Invalid RGB dimensions");
            this.width=width;this.height=height;this.pixels=pixels;
        }
    }
    /** Clockwise correction for an unmirrored raw MediaImage, per ML Kit's image-input guide. */
    public static int rawRotation(int sensor,int display,boolean front){return (sensor+(front?display:-display)+360)%360;}
    public static RgbFrame yuv420(int imageWidth,int imageHeight,int left,int top,int width,int height,
                                  Plane y,Plane u,Plane v,int rotation,int[] reuse){
        if(left<0||top<0||width<1||height<1||left+width>imageWidth||top+height>imageHeight||rotation%90!=0)throw new IllegalArgumentException("Invalid crop/rotation");
        int ow=rotation%180==0?width:height,oh=rotation%180==0?height:width;
        int[] rgb=reuse!=null&&reuse.length==ow*oh?reuse:new int[ow*oh];
        for(int row=0;row<height;row++)for(int col=0;col<width;col++){
            int sx=left+col,sy=top+row,yy=y.sample(sx,sy)-16,uu=u.sample(sx/2,sy/2)-128,vv=v.sample(sx/2,sy/2)-128;
            int c=Math.max(0,yy)*298,r=clamp((c+409*vv+128)>>8),g=clamp((c-100*uu-208*vv+128)>>8),b=clamp((c+516*uu+128)>>8);
            int dx=col,dy=row;if(rotation==90){dx=height-1-row;dy=col;}else if(rotation==180){dx=width-1-col;dy=height-1-row;}else if(rotation==270){dx=row;dy=width-1-col;}
            rgb[dy*ow+dx]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return new RgbFrame(ow,oh,rgb);
    }
    private static int clamp(int v){return Math.max(0,Math.min(255,v));}
    /** A square in original upright pixel coordinates; outside pixels are zero-padded. */
    public static final class Crop {
        public final float left,top,size;
        public Crop(float left,float top,float size){if(!Float.isFinite(left)||!Float.isFinite(top)||!Float.isFinite(size)||size<=0)throw new IllegalArgumentException("Invalid crop");this.left=left;this.top=top;this.size=size;}
        public static Crop full(int w,int h){float size=Math.max(w,h);return new Crop((w-size)/2f,(h-size)/2f,size);}
        public float globalX(float x,int width){return (left+x*size)/width;}
        public float globalY(float y,int height){return (top+y*size)/height;}
        public float modelX(float x,int width){return (x*width-left)/size;}
        public float modelY(float y,int height){return (y*height-top)/size;}
    }
    /** RGB bytes, UINT8 values 0..255. Pixel-centre bilinear sampling is deterministic. */
    public static byte[] modelRgb(RgbFrame frame,Crop crop,int side,byte[] reuse){
        byte[] out=reuse!=null&&reuse.length==side*side*3?reuse:new byte[side*side*3];int at=0;
        for(int y=0;y<side;y++)for(int x=0;x<side;x++){
            float sx=crop.left+(x+.5f)*crop.size/side-.5f,sy=crop.top+(y+.5f)*crop.size/side-.5f;
            int x0=(int)Math.floor(sx),y0=(int)Math.floor(sy);float ax=sx-x0,ay=sy-y0;
            for(int shift:CHANNEL_SHIFTS){
                float a=channel(frame,x0,y0,shift)*(1-ax)+channel(frame,x0+1,y0,shift)*ax;
                float b=channel(frame,x0,y0+1,shift)*(1-ax)+channel(frame,x0+1,y0+1,shift)*ax;
                out[at++]=(byte)clamp(Math.round(a*(1-ay)+b*ay));
            }
        }
        return out;
    }
    private static int channel(RgbFrame f,int x,int y,int shift){return x<0||y<0||x>=f.width||y>=f.height?0:(f.pixels[y*f.width+x]>>shift)&255;}
    /** Reference-inspired acquisition crop; outputs always map back to the full frame. */
    public static Crop nextCrop(Pose pose,int width,int height){
        if(!pose.personVisible())return Crop.full(width,height);
        float cx=pose.weightedX(11,12,.2f)*width,cy=pose.weightedY(11,12,.2f)*height;
        float shoulder=pose.weightedY(5,6,.2f)*height;
        float half=Math.max(Math.abs(cy-shoulder)*2.2f,Math.max(width,height)*.16f);
        for(int i=0;i<17;i++)if(pose.reliable(i,.2f)){
            float expand=(i==5||i==6||i==11||i==12)?1.9f:1.25f;
            half=Math.max(half,Math.max(Math.abs(pose.x(i)*width-cx),Math.abs(pose.y(i)*height-cy))*expand);
        }
        float size=Math.min(Math.max(width,height),half*2);
        if(size>=Math.max(width,height)*.97f)return Crop.full(width,height);
        return new Crop(cx-size/2,cy-size/2,size);
    }
}
