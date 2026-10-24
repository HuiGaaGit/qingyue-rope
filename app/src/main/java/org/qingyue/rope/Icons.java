package org.qingyue.rope;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Original rounded sports pictograms drawn from primitives; no third-party artwork. */
public final class Icons extends View {
    public enum Kind { ROPE,TIMER,REST,VOICE,HISTORY,SETTINGS,PAUSE,PLAY,STOP,SWITCH,SHIELD }
    private final Kind kind;
    private final int color;
    private final boolean badge;
    private final Paint p=new Paint(3);
    public Icons(Context c,Kind kind,int color,boolean badge) {
        super(c);this.kind=kind;this.color=color;this.badge=badge;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private void line(Canvas c,float x,float y,float xx,float yy){c.drawLine(x,y,xx,yy,p);}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/48f,getHeight()/48f);
        p.setStyle(Paint.Style.FILL);p.setColor(color);
        if(badge)canvas.drawRoundRect(0,0,48,48,15,15,p);
        p.setColor(badge?Color.WHITE:color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.6f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        switch(kind) {
            case ROPE:
                canvas.drawCircle(24,12,3,p);line(canvas,24,19,24,29);line(canvas,24,21,17,25);line(canvas,24,21,31,25);
                line(canvas,24,29,20,36);line(canvas,24,29,28,36);
                Path rope=new Path();rope.moveTo(13,23);rope.cubicTo(5,43,43,43,35,23);canvas.drawPath(rope,p);break;
            case TIMER:
                canvas.drawCircle(24,27,12,p);line(canvas,20,9,28,9);line(canvas,24,9,24,14);line(canvas,33,16,36,13);
                line(canvas,24,20,24,27);line(canvas,24,27,29,30);break;
            case REST:
                canvas.drawRoundRect(12,22,31,35,3,3,p);canvas.drawArc(27,23,38,31,-90,180,false,p);
                line(canvas,12,39,34,39);line(canvas,17,15,17,18);line(canvas,23,12,23,17);line(canvas,29,15,29,18);break;
            case HISTORY:
                canvas.drawRoundRect(12,13,36,38,4,4,p);line(canvas,12,21,36,21);line(canvas,18,10,18,16);line(canvas,30,10,30,16);
                line(canvas,18,27,23,27);line(canvas,28,27,30,27);line(canvas,18,32,25,32);break;
            case VOICE:
                Path speaker=new Path();speaker.moveTo(11,21);speaker.lineTo(17,21);speaker.lineTo(24,15);speaker.lineTo(24,34);speaker.lineTo(17,28);speaker.lineTo(11,28);speaker.close();canvas.drawPath(speaker,p);
                canvas.drawArc(22,18,36,31,-60,120,false,p);canvas.drawArc(21,12,43,37,-55,110,false,p);break;
            case PAUSE:line(canvas,19,14,19,35);line(canvas,29,14,29,35);break;
            case PLAY:
                Path play=new Path();play.moveTo(18,13);play.lineTo(35,24);play.lineTo(18,36);play.close();canvas.drawPath(play,p);break;
            case STOP:canvas.drawRoundRect(15,15,33,33,3,3,p);break;
            case SWITCH:
                canvas.drawArc(11,12,37,36,205,150,false,p);canvas.drawArc(11,12,37,36,25,150,false,p);
                line(canvas,35,13,36,21);line(canvas,36,21,28,20);line(canvas,12,27,12,35);line(canvas,12,27,20,28);break;
            case SHIELD:
                Path shield=new Path();shield.moveTo(24,10);shield.lineTo(37,15);shield.lineTo(35,29);shield.quadTo(32,36,24,40);shield.quadTo(16,36,13,29);shield.lineTo(11,15);shield.close();canvas.drawPath(shield,p);
                line(canvas,18,25,22,29);line(canvas,22,29,30,20);break;
            case SETTINGS:
                canvas.drawCircle(24,24,10,p);canvas.drawCircle(24,24,3.8f,p);
                for(int i=0;i<8;i++){canvas.save();canvas.rotate(45*i,24,24);line(canvas,24,10,24,13);canvas.restore();}break;
        }
        canvas.restore();
    }
}
