import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import java.util.Arrays;
public final class CameraCapabilityProbe {
 public static void main(String[] args) throws Exception {
  android.os.Looper.prepareMainLooper();
  Class<?> at=Class.forName("android.app.ActivityThread");
  Object thread=at.getMethod("systemMain").invoke(null);
  Context system=(Context)at.getMethod("getSystemContext").invoke(thread);
  Context context=system.createPackageContext("com.android.shell",0);
  System.out.println("uid="+android.os.Process.myUid()+" package="+context.getPackageName());
  CameraManager manager=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
  for(String id:manager.getCameraIdList()) {
   try {
    CameraCharacteristics c=manager.getCameraCharacteristics(id);
    StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
    System.out.println("camera="+id+" physical="+c.getPhysicalCameraIds()+" formats="+Arrays.toString(map.getOutputFormats()));
    for(int format:new int[]{32,37,38,35}) {
     System.out.println("format="+format+" sizes="+Arrays.toString(map.getOutputSizes(format)));
    }
   }catch(Exception e){System.out.println("camera="+id+" error="+e);}
  }
  System.exit(0);
 }
}
