package org.qingyue.rope.core;

/** Conservative global-translation check on textured background strips, never on saved video. */
public final class CameraMotionGuard {
    private int[] previous;private int width,height;
    public boolean update(FrameGeometry.RgbFrame frame){
        int h=128,w=Math.max(32,Math.round(128f*frame.width/frame.height));
        if(w>160){w=128;h=Math.max(32,Math.round(128f*frame.height/frame.width));}
        int[] current=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int c=frame.pixels[Math.min(frame.height-1,y*frame.height/h)*frame.width+Math.min(frame.width-1,x*frame.width/w)];
            current[y*w+x]=(77*((c>>16)&255)+150*((c>>8)&255)+29*(c&255))>>8;
        }
        boolean moving=false;
        if(previous!=null&&width==w&&height==h){
            int[] left=motion(previous,current,w,h,3,Math.max(5,w/4-2));
            int[] right=motion(previous,current,w,h,Math.min(w-5,w*3/4+2),w-4);
            moving=left[2]==1&&right[2]==1&&left[0]==right[0]&&left[1]==right[1]&&(left[0]!=0||left[1]!=0);
        }
        previous=current;width=w;height=h;return moving;
    }
    private int[] motion(int[] a,int[] b,int w,int h,int from,int to){
        if(to<=from)return new int[]{0,0,0};
        float zero=cost(a,b,w,h,from,to,0,0),texture=0;int n=0;
        for(int y=4;y<h-4;y+=2)for(int x=from;x<to;x+=2){texture+=Math.abs(a[y*w+x]-a[y*w+x-1]);n++;}
        if(n==0||texture/n<5||zero<3)return new int[]{0,0,0};
        float best=zero;int bx=0,by=0;
        for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){
            float c=cost(a,b,w,h,from,to,dx,dy);if(c<best-.3f){best=c;bx=dx;by=dy;}
        }
        return new int[]{bx,by,(zero-best)/Math.max(1,zero)>.2f?1:0};
    }
    private float cost(int[] a,int[] b,int w,int h,int from,int to,int dx,int dy){float sum=0;int n=0;
        for(int y=4;y<h-4;y+=2)for(int x=from;x<to;x+=2){sum+=Math.abs(a[(y+dy)*w+x+dx]-b[y*w+x]);n++;}
        return n==0?Float.MAX_VALUE:sum/n;
    }
}
