package org.qingyue.rope;

import android.content.Context;
import android.graphics.*;
import org.tensorflow.lite.*;
import org.qingyue.rope.core.Pose;
import java.io.*;
import java.nio.*;

/** CPU inference only. Model is bundled, not downloaded on the phone. */
final class MoveNet implements AutoCloseable {
    private final Interpreter interpreter;
    private final Bitmap input=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);
    private final Canvas canvas=new Canvas(input);
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final int[] pixels=new int[192*192];
    private final ByteBuffer tensor;
    private final boolean integerInput;
    private final float[][][][] output=new float[1][1][17][3];
    MoveNet(Context context)throws IOException {
        byte[] bytes;
        try(InputStream stream=context.getAssets().open("movenet.tflite");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[16384];int n;while((n=stream.read(buffer))>=0)out.write(buffer,0,n);bytes=out.toByteArray();
        }
        ByteBuffer model=ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());model.put(bytes);model.rewind();
        Interpreter.Options options=new Interpreter.Options().setNumThreads(2);
        interpreter=new Interpreter(model,options);
        int[] shape=interpreter.getInputTensor(0).shape();
        if(shape.length!=4||shape[1]!=192||shape[2]!=192||shape[3]!=3)throw new IOException("模型输入规格不符");
        DataType type=interpreter.getInputTensor(0).dataType();integerInput=type==DataType.INT32;
        if(!integerInput&&type!=DataType.UINT8)throw new IOException("模型输入类型不符");
        tensor=ByteBuffer.allocateDirect(192*192*3*(integerInput?4:1)).order(ByteOrder.nativeOrder());
    }
    Pose detect(Bitmap bitmap,long captured) {
        float scale=192f/Math.max(bitmap.getWidth(),bitmap.getHeight());
        float width=bitmap.getWidth()*scale,height=bitmap.getHeight()*scale,padX=(192-width)/2,padY=(192-height)/2;
        canvas.drawColor(Color.BLACK);canvas.drawBitmap(bitmap,null,new RectF(padX,padY,padX+width,padY+height),paint);
        input.getPixels(pixels,0,192,0,0,192,192);tensor.rewind();
        for(int pixel:pixels){int r=(pixel>>16)&255,g=(pixel>>8)&255,b=pixel&255;
            if(integerInput){tensor.putInt(r);tensor.putInt(g);tensor.putInt(b);}else{tensor.put((byte)r);tensor.put((byte)g);tensor.put((byte)b);}}
        tensor.rewind();interpreter.run(tensor,output);
        float[][] points=new float[17][3];
        for(int i=0;i<17;i++){points[i][0]=(output[0][0][i][0]*192-padY)/height;points[i][1]=(output[0][0][i][1]*192-padX)/width;points[i][2]=output[0][0][i][2];}
        return new Pose(points,captured);
    }
    public void close(){interpreter.close();input.recycle();}
}
