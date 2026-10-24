package org.qingyue.rope;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.qingyue.rope.core.Pose;

public final class PosePreview extends View {
    private final Paint p=new Paint(3);
    private Bitmap bitmap;
    private Pose pose;
    private boolean mirror;
    private final RectF area=new RectF();
    private static final int[][] BONES={{5,6},{5,7},{7,9},{6,8},{8,10},{5,11},{6,12},{11,12},{11,13},{13,15},{12,14},{14,16}};
    public PosePreview(Context context){super(context);setBackgroundColor(Color.rgb(19,23,30));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    public void setFrame(Bitmap bitmap,Pose pose,boolean mirror){this.bitmap=bitmap;this.pose=pose;this.mirror=mirror;invalidate();}
    public void clear(){bitmap=null;pose=null;invalidate();}
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if(bitmap==null) {
            p.setColor(0xff222a35);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);
            float mid=getWidth()/2f,top=getHeight()*.34f;
            c.drawCircle(mid,top,26,p);c.drawLine(mid,top+30,mid,top+145,p);
            c.drawLine(mid,top+66,mid-55,top+105,p);c.drawLine(mid,top+66,mid+55,top+105,p);
            c.drawLine(mid,top+145,mid-42,top+225,p);c.drawLine(mid,top+145,mid+42,top+225,p);return;
        }
        float scale=Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());
        float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
        area.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
        c.save();if(mirror)c.scale(-1,1,getWidth()/2f,getHeight()/2f);
        p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);c.drawBitmap(bitmap,null,area,p);c.restore();
        if(pose!=null) {
            p.setStrokeWidth(getResources().getDisplayMetrics().density*2);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(0xccffffff);
            for(int[] b:BONES)if(pose.score(b[0])>.32f&&pose.score(b[1])>.32f)c.drawLine(x(b[0]),y(b[0]),x(b[1]),y(b[1]),p);
            p.setColor(0xffff8c43);for(int i=5;i<17;i++)if(pose.score(i)>.32f)c.drawCircle(x(i),y(i),getResources().getDisplayMetrics().density*3,p);
        }
    }
    private float x(int i){return area.left+(mirror?1-pose.x(i):pose.x(i))*area.width();}
    private float y(int i){return area.top+pose.y(i)*area.height();}
}
