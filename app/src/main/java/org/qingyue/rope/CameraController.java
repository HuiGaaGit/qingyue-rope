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
import org.qingyue.rope.core.FrameGeometry;
import org.qingyue.rope.core.CameraMotionGuard;
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
    private final CameraMotionGuard motionGuard=new CameraMotionGuard();
    private long sensorClockOffset=Long.MIN_VALUE;
    private boolean timestampRealtime;
    private boolean disableAutoRotateCrop;
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
            rotation=FrameGeometry.rawRotation(angle,displayRotation,mirrored);
            timestampRealtime=Objects.equals(chars.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE),CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME);
            if(Build.VERSION.SDK_INT>=31){int[] modes=chars.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES);
                if(modes!=null)for(int mode:modes)if(mode==CaptureRequest.SCALER_ROTATE_AND_CROP_NONE)disableAutoRotateCrop=true;}
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
                                if(Build.VERSION.SDK_INT>=31&&disableAutoRotateCrop)request.set(CaptureRequest.SCALER_ROTATE_AND_CROP,CaptureRequest.SCALER_ROTATE_AND_CROP_NONE);
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
            image=source.acquireLatestImage();long acquired=SystemClock.elapsedRealtime();
            if(image==null||closed||model==null||acquired-lastFrame<30)return;
            lastFrame=acquired;long sensor=image.getTimestamp()/1000000,captured=acquired;
            if(sensor>0){
                if(timestampRealtime&&Math.abs(sensor-acquired)<5000)captured=sensor;
                else {if(sensorClockOffset==Long.MIN_VALUE)sensorClockOffset=acquired-sensor;captured=sensor+sensorClockOffset;}
            }
            captured=Math.min(acquired,captured);
            FrameGeometry.RgbFrame frame=convert(image,rotation);image.close();image=null;
            boolean moving=motionGuard.update(frame);Pose pose=model.detect(frame,captured);
            pose.cameraMoving=moving;pose.rotation=rotation;pose.mirror=mirrored?1:0;
            Bitmap bitmap=Bitmap.createBitmap(frame.pixels,frame.width,frame.height,Bitmap.Config.ARGB_8888);
            main.post(()->{if(!closed)listener.frame(bitmap,pose,mirrored);});
        }catch(Exception|LinkageError e){error("图像处理失败，训练已暂停，请重试");}
        finally{if(image!=null)image.close();}
    }
    private FrameGeometry.RgbFrame convert(Image image,int degrees) {
        Rect crop=image.getCropRect();int w=crop.width(),h=crop.height();
        Image.Plane[] planes=image.getPlanes();
        FrameGeometry.RgbFrame frame=FrameGeometry.yuv420(image.getWidth(),image.getHeight(),crop.left,crop.top,w,h,
                new FrameGeometry.Plane(planes[0].getBuffer(),planes[0].getRowStride(),planes[0].getPixelStride()),
                new FrameGeometry.Plane(planes[1].getBuffer(),planes[1].getRowStride(),planes[1].getPixelStride()),
                new FrameGeometry.Plane(planes[2].getBuffer(),planes[2].getRowStride(),planes[2].getPixelStride()),degrees,pixels);
        pixels=frame.pixels;return frame;
    }
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
