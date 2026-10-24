package org.qingyue.rope;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.util.Size;
import org.qingyue.rope.core.Pose;
import java.nio.ByteBuffer;
import java.util.*;

/** One camera worker owns Camera2, YUV conversion and the interpreter. Nothing is recorded. */
public final class CameraController implements AutoCloseable {
    public interface Listener {void frame(Bitmap bitmap,Pose pose,boolean mirrored);void error(String message);}
    private final Context context;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final HandlerThread thread=new HandlerThread("Qingyue-Camera");
    private final Handler worker;
    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader reader;
    private MoveNet model;
    private volatile boolean closed;
    private boolean mirrored,opening;
    private int rotation;
    private long lastFrame;
    private int[] pixels;
    public CameraController(Context context,boolean preferFront,int displayRotation,Listener listener) {
        this.context=context.getApplicationContext();this.listener=listener;thread.start();worker=new Handler(thread.getLooper());
        worker.post(()->open(preferFront,displayRotation));
    }
    private void error(String message){if(!closed)main.post(()->{if(!closed)listener.error(message);});}
    private void open(boolean front,int displayRotation) {
        try {
            if(closed){cleanup();return;}
            if(context.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){error("需要相机权限才能自动计数");return;}
            model=new MoveNet(context);
            if(closed){cleanup();return;}
            CameraManager manager=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
            String chosen=null;CameraCharacteristics chars=null;
            for(String id:manager.getCameraIdList()) {
                CameraCharacteristics c=manager.getCameraCharacteristics(id);Integer facing=c.get(CameraCharacteristics.LENS_FACING);
                if(chosen==null){chosen=id;chars=c;}
                if(facing!=null&&facing==(front?CameraCharacteristics.LENS_FACING_FRONT:CameraCharacteristics.LENS_FACING_BACK)){chosen=id;chars=c;break;}
            }
            if(chosen==null){error("未发现可用相机");return;}
            mirrored=Objects.equals(chars.get(CameraCharacteristics.LENS_FACING),CameraCharacteristics.LENS_FACING_FRONT);
            Integer sensor=chars.get(CameraCharacteristics.SENSOR_ORIENTATION);int angle=sensor==null?90:sensor;
            rotation=mirrored?(angle+displayRotation)%360:(angle-displayRotation+360)%360;
            StreamConfigurationMap map=chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes=map==null?null:map.getOutputSizes(ImageFormat.YUV_420_888);
            if(sizes==null||sizes.length==0){error("相机不支持所需的图像格式");return;}
            Size size=Arrays.stream(sizes).min(Comparator.comparingLong(s->Math.abs(s.getWidth()*s.getHeight()-640L*480L))).get();
            reader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.YUV_420_888,2);
            reader.setOnImageAvailableListener(this::onImage,worker);
            if(closed){cleanup();return;}
            opening=true;
            manager.openCamera(chosen,new CameraDevice.StateCallback(){
                public void onOpened(CameraDevice camera){
                    opening=false;
                    if(closed){camera.close();cleanup();return;}device=camera;
                    try {device.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
                        public void onConfigured(CameraCaptureSession configured){
                            if(closed){configured.close();return;}session=configured;
                            try {CaptureRequest.Builder request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                request.addTarget(reader.getSurface());request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                                session.setRepeatingRequest(request.build(),null,worker);
                            }catch(CameraAccessException|IllegalStateException e){error("相机启动失败，请重试");}
                        }
                        public void onConfigureFailed(CameraCaptureSession failed){error("相机配置失败，可切换前后摄像头后重试");}
                    },worker);}catch(CameraAccessException|IllegalStateException e){error("无法创建相机预览，请重试");}
                }
                public void onDisconnected(CameraDevice camera){opening=false;camera.close();if(closed)cleanup();else error("相机已断开，训练已暂停");}
                public void onError(CameraDevice camera,int code){opening=false;camera.close();if(closed)cleanup();else error("相机被占用或不可用（"+code+"），训练已暂停");}
            },worker);
        } catch(Exception|LinkageError e){opening=false;if(closed)cleanup();else error("相机或本地模型初始化失败："+e.getClass().getSimpleName());}
    }
    private void onImage(ImageReader source) {
        Image image=null;
        try {
            image=source.acquireLatestImage();long captured=SystemClock.elapsedRealtime();
            if(image==null||closed||model==null||captured-lastFrame<60)return;
            lastFrame=captured;Bitmap bitmap=convert(image,rotation);Pose pose=model.detect(bitmap,captured);
            main.post(()->{if(!closed)listener.frame(bitmap,pose,mirrored);});
        }catch(Exception|LinkageError e){error("图像处理失败，训练已暂停，请重试");}
        finally{if(image!=null)image.close();}
    }
    private Bitmap convert(Image image,int degrees) {
        Rect crop=image.getCropRect();int w=crop.width(),h=crop.height();
        int ow=degrees==90||degrees==270?h:w,oh=degrees==90||degrees==270?w:h;
        if(pixels==null||pixels.length!=ow*oh)pixels=new int[ow*oh];
        Image.Plane[] planes=image.getPlanes();ByteBuffer y=planes[0].getBuffer(),u=planes[1].getBuffer(),v=planes[2].getBuffer();
        int ys=planes[0].getRowStride(),yp=planes[0].getPixelStride(),us=planes[1].getRowStride(),up=planes[1].getPixelStride(),vs=planes[2].getRowStride(),vp=planes[2].getPixelStride();
        for(int row=0;row<h;row++)for(int col=0;col<w;col++) {
            int sx=col+crop.left,sy=row+crop.top;
            int yy=(y.get(sy*ys+sx*yp)&255)-16,uu=(u.get((sy/2)*us+(sx/2)*up)&255)-128,vv=(v.get((sy/2)*vs+(sx/2)*vp)&255)-128;
            int c=Math.max(0,yy)*298,r=clamp((c+409*vv+128)>>8),g=clamp((c-100*uu-208*vv+128)>>8),b=clamp((c+516*uu+128)>>8);
            int dx=col,dy=row;
            if(degrees==90){dx=h-1-row;dy=col;}else if(degrees==270){dx=row;dy=w-1-col;}else if(degrees==180){dx=w-1-col;dy=h-1-row;}
            pixels[dy*ow+dx]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return Bitmap.createBitmap(pixels,ow,oh,Bitmap.Config.ARGB_8888);
    }
    private int clamp(int v){return Math.max(0,Math.min(255,v));}
    public void close() {
        if(closed)return;closed=true;
        worker.post(this::cleanup);
    }
    private void cleanup() {
        if(session!=null){session.close();session=null;}if(device!=null){device.close();device=null;}
        if(reader!=null){reader.close();reader=null;}if(model!=null){model.close();model=null;}
        // Keep the callback looper alive while openCamera is pending. The eventual
        // callback closes the newly opened device before this worker can quit.
        if(!opening)thread.quitSafely();
    }
}
