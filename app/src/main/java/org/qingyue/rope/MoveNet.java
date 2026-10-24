package org.qingyue.rope;

import android.content.Context;
import org.tensorflow.lite.*;
import org.qingyue.rope.core.Pose;
import org.qingyue.rope.core.FrameGeometry;
import java.io.*;
import java.nio.*;

/** CPU inference only. Model is bundled, not downloaded on the phone. */
final class MoveNet implements AutoCloseable {
    private final Interpreter interpreter;
    private final ByteBuffer modelBuffer;
    private FrameGeometry.Crop crop;
    private byte[] rgb;
    private int previousWidth,previousHeight;
    private final ByteBuffer tensor;
    private final boolean integerInput;
    private final float[][][][] output=new float[1][1][17][3];
    MoveNet(Context context)throws IOException {
        byte[] bytes;
        try(InputStream stream=context.getAssets().open("movenet.tflite");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[16384];int n;while((n=stream.read(buffer))>=0)out.write(buffer,0,n);bytes=out.toByteArray();
        }
        modelBuffer=ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());modelBuffer.put(bytes);modelBuffer.rewind();
        Interpreter.Options options=new Interpreter.Options().setNumThreads(2);
        interpreter=new Interpreter(modelBuffer,options);
        int[] shape=interpreter.getInputTensor(0).shape();
        if(shape.length!=4||shape[0]!=1||shape[1]!=192||shape[2]!=192||shape[3]!=3){interpreter.close();throw new IOException("模型输入规格不符");}
        DataType type=interpreter.getInputTensor(0).dataType();integerInput=type==DataType.INT32;
        if(!integerInput&&type!=DataType.UINT8){interpreter.close();throw new IOException("模型输入类型不符");}
        tensor=ByteBuffer.allocateDirect(192*192*3*(integerInput?4:1)).order(ByteOrder.nativeOrder());
        int[] out=interpreter.getOutputTensor(0).shape();
        if(out.length!=4||out[0]!=1||out[1]!=1||out[2]!=17||out[3]!=3||interpreter.getOutputTensor(0).dataType()!=DataType.FLOAT32){interpreter.close();throw new IOException("模型输出规格不符");}
    }
    Pose detect(FrameGeometry.RgbFrame frame,long captured) {
        long started=System.nanoTime();
        if(crop==null||previousWidth!=frame.width||previousHeight!=frame.height)crop=FrameGeometry.Crop.full(frame.width,frame.height);
        FrameGeometry.Crop used=crop;previousWidth=frame.width;previousHeight=frame.height;
        rgb=FrameGeometry.modelRgb(frame,used,192,rgb);tensor.rewind();
        if(integerInput)for(byte value:rgb)tensor.putInt(value&255);else tensor.put(rgb);
        tensor.rewind();interpreter.run(tensor,output);
        float[][] points=new float[17][3];
        for(int i=0;i<17;i++){points[i][0]=used.globalY(output[0][0][i][0],frame.height);points[i][1]=used.globalX(output[0][0][i][1],frame.width);points[i][2]=output[0][0][i][2];}
        Pose pose=new Pose(points,captured);pose.frameWidth=frame.width;pose.frameHeight=frame.height;pose.inferenceMs=(System.nanoTime()-started)/1000000;
        pose.cropLeft=used.left;pose.cropTop=used.top;pose.cropSize=used.size;
        crop=FrameGeometry.nextCrop(pose,frame.width,frame.height);
        return pose;
    }
    public void close(){interpreter.close();}
}
