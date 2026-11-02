package com.n810.camera.lab;
import android.util.Log;
import java.lang.reflect.Field;
import java.util.Map;

/** Isolated experiment in the existing modified Camera process. */
public final class CameraMetadataTest {
 private static android.content.Context testContext;
 private static boolean frameDumped;
 private static java.lang.ref.WeakReference<android.app.Activity> cameraActivity=new java.lang.ref.WeakReference<>(null);
 private static void refreshSavedThumbnail(){new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{android.app.Activity activity=cameraActivity.get();if(activity==null||activity.isFinishing())return;String saved=activity.getSharedPreferences("n810-photo",0).getString("last_saved_uri",null);if(saved==null)return;try{
  android.net.Uri uri=android.net.Uri.parse(saved);android.graphics.Bitmap bitmap=activity.getContentResolver().loadThumbnail(uri,new android.util.Size(160,160),null);
  android.view.View view=activity.findViewById(activity.getResources().getIdentifier("thumbnail","id",activity.getPackageName()));if(view instanceof android.widget.ImageView)((android.widget.ImageView)view).setImageBitmap(bitmap);
 }catch(Exception e){audit("Thumbnail refresh="+e.getClass().getSimpleName());}});}

 public static synchronized void dumpCompositionFrame(android.hardware.HardwareBuffer buffer) {
  if(frameDumped||testContext==null||buffer==null)return;frameDumped=true;
  try {
   android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(buffer.getWidth(),buffer.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
   Class.forName("com.oplus.camera.common.utils.HardwareBufferUtils").getMethod("copyHardwareBufferToBitmap",android.hardware.HardwareBuffer.class,android.graphics.Bitmap.class).invoke(null,buffer,bitmap);
   try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(testContext.getFilesDir(),"composition-input.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
   Log.i("CameraFrameDiagnostic","Saved actual composition input, format="+buffer.getFormat());
  }catch(Exception|LinkageError e){Log.e("CameraFrameDiagnostic","Frame diagnostic failed",e);}
 }
 public static String configOverride(String key) {
  if("com.oplus.camera.preview.hdr.support".equals(key))return "0";
  if("com.oplus.feature.aicomposition.realscene.support".equals(key)||"com.oplus.feature.aicomposition.inspiration.support".equals(key))return "1";
  if("com.oplus.capture.with.raw10".equals(key)||"com.oplus.capture.raw10.use.mipi".equals(key))return "0";
  return null;
 }
 public static void setIntegrationProperty(String key,String value) {
  if(!"persist.sys.camera.integration".equals(key))throw new IllegalArgumentException("Unexpected property key");
  try {Class.forName("com.oplus.wrapper.os.SystemProperties").getMethod("set",String.class,String.class).invoke(null,key,value);}
  catch(java.lang.reflect.InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof RuntimeException && String.valueOf(cause.getMessage()).contains("Permission denied"))Log.w("CameraIntegrationProperty","OEM integration property write denied; leaving system value unchanged",cause);else throw new RuntimeException(cause);}
  catch(ReflectiveOperationException e){throw new RuntimeException(e);}
 }
 public static boolean registerAppSwitchObserver(Object manager, android.content.Context context, Object observer, Object config) {
  try {
   Class<?> observerType=Class.forName("com.oplus.app.OplusAppSwitchManager$OnAppSwitchObserver");
   Class<?> configType=Class.forName("com.oplus.app.OplusAppSwitchConfig");
   return (Boolean)manager.getClass().getMethod("registerAppSwitchObserver",android.content.Context.class,observerType,configType).invoke(manager,context,observer,config);
  } catch(java.lang.reflect.InvocationTargetException e) {
   Throwable cause=e.getCause();
   if(cause instanceof SecurityException && String.valueOf(cause.getMessage()).contains("OPLUS_COMPONENT_SAFE")) {
    Log.w("CameraAppSwitch","OEM cross-app observer denied; registration remains false, allowing normal activity pause",cause);
    return false;
   }
   if(cause instanceof RuntimeException)throw (RuntimeException)cause;
   throw new RuntimeException(cause);
  } catch(ReflectiveOperationException e){throw new RuntimeException(e);}
 }
 private static void audit(String message) {
  if(testContext==null)return;
  try(java.io.FileOutputStream f=new java.io.FileOutputStream(new java.io.File(testContext.getFilesDir(),"parity-diagnostic.txt"),true)){f.write((android.os.SystemClock.elapsedRealtime()+" "+message+"\n").getBytes("UTF-8"));}catch(Exception ignored){}
 }
 public static void compositionEvent(int event,String payload){audit("Composition event="+event+" payload="+payload);}
 private static int captureDiagnosticCount;
 public static void captureDiagnostic(Object category) {
  if(captureDiagnosticCount>=36 || testContext==null)return;
  try {
   Object meta=category.getClass().getField("mMetaItem").get(category);
   java.util.List<?> images=(java.util.List<?>)category.getClass().getField("mImageItemList").get(category);
   java.util.Map<?,?> tuning=(java.util.Map<?,?>)category.getClass().getField("mTuningItemLists").get(category);
   if(meta==null||images==null||images.isEmpty())return;captureDiagnosticCount++;
   StringBuilder out=new StringBuilder("images="+images.size()+" tuning="+tuning.size());
   Class<?> params=Class.forName("com.oplus.ocs.camera.consumer.apsAdapter.adapter.ApsParameters");
   for(String name:new String[]{"KEY_CAPTURE_STREAM_NUMBER","KEY_MERGE_NUMBER","KEY_TIME_STAMP","KEY_USE_TUNING_DATA","KEY_RAW_TUNING_STREAM_NUMBER","KEY_YUV_TUNING_STREAM_NUMBER","KEY_REQUEST_MIXED_FORMAT"}) {
    Object key=params.getField(name).get(null);
    for(java.lang.reflect.Method method:meta.getClass().getMethods())if(method.getName().equals("get")&&method.getParameterCount()==1){out.append(" ").append(name).append("=").append(method.invoke(meta,key));break;}
   }
   Object captureKey=params.getField("KEY_CAPTURE_RESULT").get(null);android.hardware.camera2.CaptureResult result=null;
   for(java.lang.reflect.Method method:meta.getClass().getMethods())if(method.getName().equals("get")&&method.getParameterCount()==1){result=(android.hardware.camera2.CaptureResult)method.invoke(meta,captureKey);break;}
   if(result!=null){
    for(String name:new String[]{"com.mediatek.control.capture.ispTuningDataRaw","com.mediatek.control.capture.ispTuningDataYuv"})try{byte[] bytes=result.get(new android.hardware.camera2.CaptureResult.Key<byte[]>(name,byte[].class));out.append(" ").append(name).append(".bytes=").append(bytes==null?0:bytes.length);}catch(Exception e){out.append(" ").append(name).append(".error=").append(e.getClass().getSimpleName());}
    try{out.append(" ispMetaEnable=").append(java.util.Arrays.toString(result.getRequest().get(new android.hardware.camera2.CaptureRequest.Key<byte[]>("com.mediatek.control.capture.ispMetaEnable",byte[].class))));}catch(Exception e){out.append(" ispMetaEnable.error=").append(e.getClass().getSimpleName());}
   }
   try(java.io.FileOutputStream f=new java.io.FileOutputStream(new java.io.File(testContext.getFilesDir(),"capture-diagnostic.txt"),true)){f.write((out+"\n").getBytes("UTF-8"));}
  }catch(Exception e){Log.e("CameraCaptureDiagnostic","Failed",e);}
 }
 public static void thumbnailClick(Object ui) {
  audit("Thumbnail direct entry");
  try {
   Field managerField=ui.getClass().getDeclaredField("y");managerField.setAccessible(true);Object manager=managerField.get(ui);
   Object helper=null;
   for(Field f:manager.getClass().getDeclaredFields())if(f.getType().getName().equals("com.oplus.camera.helper.GalleryHelper")){f.setAccessible(true);helper=f.get(manager);break;}
   if(helper!=null){Field thumbnail=ui.getClass().getDeclaredField("h");thumbnail.setAccessible(true);if(openGallery(helper,null,thumbnail.get(ui),false))return;}
   audit("Thumbnail original fallback");ui.getClass().getMethod("u$labOriginal").invoke(ui);
  }catch(Exception e){audit("Thumbnail error="+android.util.Log.getStackTraceString(e));Log.e("CameraPublicGallery","Thumbnail entry failed",e);}
  finally{try{Field busy=ui.getClass().getDeclaredField("k0");busy.setAccessible(true);busy.setBoolean(ui,false);}catch(ReflectiveOperationException e){audit("Thumbnail state reset failed="+e);}}
 }
 public static boolean openGallery(Object helper,Object callback,Object thumbnail,boolean flag) {
  audit("Gallery entry");
  android.app.Activity activity=null;
  try {
   Field af=helper.getClass().getDeclaredField("a");af.setAccessible(true);activity=(android.app.Activity)af.get(helper);
   Field secureField=helper.getClass().getDeclaredField("c");secureField.setAccessible(true);Object secureOwner=secureField.get(helper);
   java.lang.reflect.Method secureMethod=secureOwner.getClass().getMethod("T");secureMethod.setAccessible(true);
   boolean secure=(Boolean)secureMethod.invoke(secureOwner);audit("Gallery secure="+secure);
   android.app.KeyguardManager kg=(android.app.KeyguardManager)activity.getSystemService(android.content.Context.KEYGUARD_SERVICE);
   if(secure || kg==null || kg.isDeviceLocked() || kg.isKeyguardLocked()) {
    for(java.lang.reflect.Method method:helper.getClass().getDeclaredMethods())if(method.getName().equals("p$labOriginal")){method.setAccessible(true);return (Boolean)method.invoke(helper,callback,thumbnail,flag);}
    return false;
   }
   android.net.Uri uri=null;
   if(thumbnail!=null)for(Field field:thumbnail.getClass().getDeclaredFields())if(field.getType()==android.net.Uri.class){field.setAccessible(true);uri=(android.net.Uri)field.get(thumbnail);if(uri!=null)break;}
   boolean ready=false;
   if(uri!=null)try(android.database.Cursor c=activity.getContentResolver().query(uri,new String[]{"is_pending","_size"},null,null,null)){ready=c!=null&&c.moveToFirst()&&c.getInt(0)==0&&c.getLong(1)>0;}
   audit("Gallery thumbnail uri="+uri+" ready="+ready);
   String saved=activity.getSharedPreferences("n810-photo",0).getString("last_saved_uri",null);
   if(saved!=null){android.net.Uri completed=android.net.Uri.parse(saved);try(android.database.Cursor c=activity.getContentResolver().query(completed,new String[]{"is_pending","_size","date_added"},null,null,null)){if(c!=null&&c.moveToFirst()&&c.getInt(0)==0&&c.getLong(1)>0){long savedDate=c.getLong(2),thumbDate=0;if(ready)try(android.database.Cursor old=activity.getContentResolver().query(uri,new String[]{"date_added"},null,null,null)){if(old!=null&&old.moveToFirst())thumbDate=old.getLong(0);}if(!ready||savedDate>=thumbDate){uri=completed;ready=true;}}}}

   android.content.Intent view=ready?new android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri,"image/*").setPackage("com.coloros.gallery3d").addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION):activity.getPackageManager().getLaunchIntentForPackage("com.coloros.gallery3d");
   if(view==null)throw new android.content.ActivityNotFoundException("Gallery unavailable");
   audit("Gallery intent="+view+" completed="+ready);activity.startActivity(view);
   if(!ready)android.widget.Toast.makeText(activity,"Photo has not finished saving. Opening Gallery.",android.widget.Toast.LENGTH_LONG).show();
   Log.i("CameraPublicGallery","Opened public Gallery, completedPhoto="+ready);return true;
  }catch(Exception e){audit("Gallery error="+android.util.Log.getStackTraceString(e));Log.e("CameraPublicGallery","Gallery launch failed",e);if(activity!=null)android.widget.Toast.makeText(activity,"Unable to open Gallery.",android.widget.Toast.LENGTH_SHORT).show();return false;}
 }
 public static void attach(android.content.Context context) {
  if (!"com.n810.camera.aicomposition.lab".equals(context.getPackageName()))return;
  testContext=context;
  if(context instanceof android.app.Application)((android.app.Application)context).registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks(){
   public void onActivityResumed(android.app.Activity a){if(a.getClass().getName().equals("com.oplus.camera.Camera")){cameraActivity=new java.lang.ref.WeakReference<>(a);new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->refreshSavedThumbnail(),1000);}}
   public void onActivityCreated(android.app.Activity a,android.os.Bundle b){}public void onActivityStarted(android.app.Activity a){}public void onActivityPaused(android.app.Activity a){}public void onActivityStopped(android.app.Activity a){}public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle b){}public void onActivityDestroyed(android.app.Activity a){}
  });
  try {
   java.io.File library=new java.io.File(context.getCodeCacheDir(),"libCameraCallbackRepair.so");
   try(java.util.zip.ZipFile apk=new java.util.zip.ZipFile(context.getApplicationInfo().sourceDir);
       java.io.InputStream input=apk.getInputStream(apk.getEntry("lib/arm64-v8a/libCameraCallbackRepair.so"));
       java.io.FileOutputStream output=new java.io.FileOutputStream(library)){
    byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);
   }
   android.os.Debug.attachJvmtiAgent(library.getAbsolutePath(),context.getFilesDir().getAbsolutePath()+"/n810-engine|"+context.getApplicationInfo().nativeLibraryDir,context.getClassLoader());
   Log.i("CameraCallbackRepair","Bundled callback repair attached without ADB");
   new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
    try {
     Class<?> logger=Class.forName("jb.a");
     for(String name:new String[]{"b","l","m"}){Field flag=logger.getDeclaredField(name);flag.setAccessible(true);flag.setBoolean(null,false);}
     Log.i("CameraCompositionMode","Kept verbose presenter logging disabled to preserve inference diagnostics");
     Class<?> cfg=Class.forName("com.oplus.camera.configure.CameraConfig");
     java.lang.reflect.Method read=cfg.getMethod("d",String.class,boolean.class,boolean.class);
     Log.i("CameraCompositionMode","realscene="+read.invoke(null,"com.oplus.feature.aicomposition.realscene.support",false,false)+" inspiration="+read.invoke(null,"com.oplus.feature.aicomposition.inspiration.support",false,false));
     Class<?> dmType=Class.forName("com.oplus.camera.data.DataManager");Object dm=dmType.getMethod("getInstance").invoke(null);
     Object key=Class.forName("ec.a").getField("c").get(null);
     for(java.lang.reflect.Method m:dmType.getMethods())if(m.getName().equals("b")&&m.getParameterCount()==2){
      try{
       Object previous=m.invoke(dm,key,Integer.valueOf(0));Log.i("CameraCompositionMode","Stored mode="+previous);
       android.content.SharedPreferences test=context.getSharedPreferences("n810_callback_test",0);
       if(!test.getBoolean("live_guide_mode_frame_test_selected",false)){
        for(java.lang.reflect.Method setter:dmType.getMethods())if(setter.getName().equals("g")&&setter.getParameterCount()==2){
         try{setter.invoke(dm,key,Integer.valueOf(0));test.edit().putBoolean("live_guide_mode_frame_test_selected",true).putInt("previous_composition_mode_frame_test",((Number)previous).intValue()).apply();Log.i("CameraCompositionMode","Selected existing Live guide mode 0 for testing; prior mode saved");break;}catch(IllegalArgumentException ignored){}
        }
       }
       break;
      }catch(IllegalArgumentException ignored){}
     }
    }catch(Exception e){Log.e("CameraCompositionMode","Mode diagnostic failed",e);}
   },1500);

  }catch(Exception e){Log.e("CameraCallbackRepair","Bundled callback repair NOT attached",e);}
 }
 private static Object get(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 private static void put(Object object,String name,Object value)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
 private static Object invoke(Object object,String name,Object... args)throws Exception{
  for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())for(java.lang.reflect.Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){
   Class<?>[] types=m.getParameterTypes();boolean match=true;for(int i=0;i<args.length;i++)if(args[i]!=null&&!types[i].isPrimitive()&&!types[i].isInstance(args[i]))match=false;
   if(match){m.setAccessible(true);return m.invoke(object,args);}
  }throw new NoSuchMethodException(name);
 }
 public static void shutterEvent(Object button,android.view.MotionEvent event){if(event!=null&&event.getAction()!=0&&event.getAction()!=1)return;try{
  android.view.View view=(android.view.View)button;StringBuilder out=new StringBuilder("Shutter action="+(event==null?"click":event.getAction())+" enabled="+view.isEnabled()+" clickable="+view.isClickable());
  for(Class<?> c=button.getClass();c!=null;c=c.getSuperclass())if(c.getName().equals("com.oplus.camera.control.ShutterButton"))for(Field f:c.getDeclaredFields()){
   f.setAccessible(true);if(f.getType().getName().endsWith("ShutterButton$i")){Object listener=f.get(button);out.append(" listener=").append(listener==null?"null":listener.getClass().getName());}
   if(f.getName().equals("S0")||f.getName().equals("Q0")||f.getName().equals("u0"))out.append(" ").append(f.getName()).append("=").append(f.get(button));
  }audit(out.toString());
 }catch(Exception e){audit("Shutter diagnostic="+e);}}
 private static float lastZoom=-1;private static long lastZoomTime;
 public static void zoomEvent(float zoom){long now=android.os.SystemClock.elapsedRealtime();if(Math.abs(zoom-lastZoom)>0.01f&&(now-lastZoomTime>150||Math.abs(zoom-lastZoom)>0.5f)){audit("Zoom requested="+zoom+" thread="+Thread.currentThread().getName());lastZoom=zoom;lastZoomTime=now;}}
 private static android.os.Handler photoWorker;
 public static boolean takePublicPhoto(Object camera,Object tag,Object parameter,android.os.Handler handler){
  try{
   if(!"photo_mode".equals(get(tag,"mCaptureMode")) || (Boolean)get(tag,"mbBurstShot"))return false;
   android.hardware.camera2.CameraDevice device=(android.hardware.camera2.CameraDevice)get(camera,"mCameraDevice");
   android.hardware.camera2.CameraCaptureSession session=(android.hardware.camera2.CameraCaptureSession)get(camera,"mCaptureSession");
   if(device==null||session==null)return false;
   if(photoWorker==null){android.os.HandlerThread t=new android.os.HandlerThread("CameraPhotoEncode");t.start();photoWorker=new android.os.Handler(t.getLooper());}
   android.hardware.camera2.CaptureRequest preview=(android.hardware.camera2.CaptureRequest)invoke(get(camera,"mPreviewBuilder"),"build");
   android.hardware.camera2.CameraManager manager=(android.hardware.camera2.CameraManager)testContext.getSystemService(android.content.Context.CAMERA_SERVICE);
   android.hardware.camera2.CameraCharacteristics chars=manager.getCameraCharacteristics(device.getId());
   android.util.Size chosen=null;for(android.util.Size size:chars.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(256))if(size.getWidth()*size.getHeight()<=13000000 && Math.abs((double)size.getWidth()/size.getHeight()-4.0/3)<0.02 && (chosen==null||size.getWidth()>chosen.getWidth()))chosen=size;
   if(chosen==null)throw new IllegalStateException("No supported 4:3 JPEG size");
   final android.media.ImageReader reader=android.media.ImageReader.newInstance(chosen.getWidth(),chosen.getHeight(),256,2);
   final android.media.ImageReader meter=android.media.ImageReader.newInstance(640,480,35,2);
   meter.setOnImageAvailableListener(r->{android.media.Image i=r.acquireLatestImage();if(i!=null)i.close();},photoWorker);
   final PublicPhoto job=new PublicPhoto(camera,tag,handler);job.device=device;job.preview=preview;job.reader=reader;job.meter=meter;
   put(tag,"mRequestNum",1);put(tag,"mRequestFormat",256);put(tag,"mbUseCaptureMeta",false);put(tag,"mRequestFormatList",null);
   put(tag,"mCaptureStartedTimestamp",android.os.SystemClock.uptimeMillis());
   reader.setOnImageAvailableListener(r->{android.media.Image image=null;try{image=r.acquireNextImage();if(image!=null)job.image(image);}catch(Exception e){job.fail(e);}finally{if(image!=null)image.close();}},photoWorker);
   final android.hardware.camera2.CaptureRequest.Builder still=publicRequest(device,2,preview);still.addTarget(reader.getSurface());still.setTag(tag);
   still.set(android.hardware.camera2.CaptureRequest.JPEG_ORIENTATION,((Number)get(tag,"mOrientation")).intValue());still.set(android.hardware.camera2.CaptureRequest.JPEG_QUALITY,(byte)95);
   final android.hardware.camera2.CaptureRequest.Builder metering=publicRequest(device,1,preview);metering.addTarget(meter.getSurface());
   final android.hardware.camera2.CameraCaptureSession.CaptureCallback capture=new android.hardware.camera2.CameraCaptureSession.CaptureCallback(){
    public void onCaptureStarted(android.hardware.camera2.CameraCaptureSession se,android.hardware.camera2.CaptureRequest r,long timestamp,long frame){job.request=r;try{Object cb=get(tag,"mCallback");invoke(cb,"onCaptureStarted",r,timestamp,0,(int)frame);invoke(cb,"onCaptureStarted",r,timestamp);}catch(Exception e){audit("PublicPhoto shutter callback="+e);}audit("PublicPhoto started ts="+timestamp);}
    public void onCaptureCompleted(android.hardware.camera2.CameraCaptureSession se,android.hardware.camera2.CaptureRequest r,android.hardware.camera2.TotalCaptureResult result){job.result=result;audit("PublicPhoto actual zoom="+result.get(android.hardware.camera2.CaptureResult.CONTROL_ZOOM_RATIO)+" focal="+result.get(android.hardware.camera2.CaptureResult.LENS_FOCAL_LENGTH)+" AF="+result.get(android.hardware.camera2.CaptureResult.CONTROL_AF_STATE));job.deliver();}
    public void onCaptureFailed(android.hardware.camera2.CameraCaptureSession se,android.hardware.camera2.CaptureRequest r,android.hardware.camera2.CaptureFailure f){job.fail(new IllegalStateException("HAL capture failure="+f.getReason()));}
   };
   session.stopRepeating();
   device.createCaptureSession(new android.hardware.camera2.params.SessionConfiguration(0,java.util.Arrays.asList(new android.hardware.camera2.params.OutputConfiguration(reader.getSurface()),new android.hardware.camera2.params.OutputConfiguration(meter.getSurface())),r->handler.post(r),new android.hardware.camera2.CameraCaptureSession.StateCallback(){
    public void onConfigureFailed(android.hardware.camera2.CameraCaptureSession se){job.fail(new IllegalStateException("Public JPEG session rejected"));}
    public void onConfigured(android.hardware.camera2.CameraCaptureSession se){try{
     se.setRepeatingRequest(metering.build(),new android.hardware.camera2.CameraCaptureSession.CaptureCallback(){int frames;boolean captured;
      public void onCaptureCompleted(android.hardware.camera2.CameraCaptureSession se,android.hardware.camera2.CaptureRequest r,android.hardware.camera2.TotalCaptureResult result){
       Integer ae=result.get(android.hardware.camera2.CaptureResult.CONTROL_AE_STATE),af=result.get(android.hardware.camera2.CaptureResult.CONTROL_AF_STATE);
       if(!captured&&++frames>=12&&(frames>=45||(ae!=null&&(ae==2||ae==4)&&af!=null&&(af==2||af==4||af==0)))){captured=true;try{se.capture(still.build(),capture,handler);}catch(Exception e){job.fail(e);}}
      }
     },handler);
    }catch(Exception e){job.fail(e);}}
   }));
   handler.postDelayed(()->{if(!job.done)job.fail(new IllegalStateException("Still capture timed out"));},12000);
   audit("PublicPhoto normal-session JPEG submitted "+chosen+" zoom="+preview.get(android.hardware.camera2.CaptureRequest.CONTROL_ZOOM_RATIO));return true;
  }catch(Exception e){audit("PublicPhoto setup failed="+android.util.Log.getStackTraceString(e));return false;}
 }
 private static android.hardware.camera2.CaptureRequest.Builder publicRequest(android.hardware.camera2.CameraDevice device,int template,android.hardware.camera2.CaptureRequest preview)throws Exception{
  android.hardware.camera2.CaptureRequest.Builder b=device.createCaptureRequest(template);
  for(android.hardware.camera2.CaptureRequest.Key key:preview.getKeys())if(key.getName().startsWith("android.")&&!key.getName().equals("android.control.captureIntent")&&!key.getName().equals("android.control.enableZsl")&&!key.getName().endsWith("Trigger"))try{b.set(key,preview.get(key));}catch(IllegalArgumentException ignored){}
  b.set(android.hardware.camera2.CaptureRequest.CONTROL_ENABLE_ZSL,false);b.set(android.hardware.camera2.CaptureRequest.CONTROL_AF_TRIGGER,0);b.set(android.hardware.camera2.CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,0);return b;
 }
 private static final class PublicPhoto{
  android.hardware.camera2.CameraDevice device;android.hardware.camera2.CaptureRequest preview;android.media.ImageReader reader,meter;boolean restored;
  final long wallTime=System.currentTimeMillis();
  final Object camera,tag;final android.os.Handler handler;volatile boolean done;volatile android.hardware.camera2.CaptureRequest request;volatile android.hardware.camera2.TotalCaptureResult result;volatile Object jpeg;
  PublicPhoto(Object c,Object t,android.os.Handler h){camera=c;tag=t;handler=h;}
  void image(android.media.Image image)throws Exception{
   java.nio.ByteBuffer data=image.getPlanes()[0].getBuffer();byte[] bytes=new byte[data.remaining()];data.get(bytes);
   Class<?> type=Class.forName("com.oplus.ocs.camera.common.util.CameraImage");
   jpeg=type.getConstructors()[0].newInstance(bytes,null,256,image.getWidth(),image.getHeight(),image.getWidth(),image.getHeight(),0,image.getTimestamp(),false,get(tag,"mCameraType"),4,-1,0L,0);
   audit("PublicPhoto encoded format="+image.getFormat()+" size="+image.getWidth()+"x"+image.getHeight()+" bytes="+bytes.length);
   deliver();
  }
  synchronized void deliver(){if(done||jpeg==null||result==null)return;done=true;
   photoWorker.postDelayed(()->publishPhoto(),1800);
   handler.post(()->{try{
    Object consumer=get(tag,"mConsumerInterface"),callback=get(tag,"mCallback");
    Class<?> builderClass=Class.forName("com.oplus.ocs.camera.appinterface.CameraPictureCallbackAdapter$PictureResult$Builder");
    Object b=builderClass.getConstructor().newInstance();invoke(b,"setCaptureResult",result);
    Object key=Class.forName("com.oplus.ocs.camera.common.parameter.apsadapter.ApsOtherParameter").getField("KEY_REQUEST_TAG").get(null);invoke(b,"set",key,get(tag,"mAppOpaqueObj"));
    long timestamp=((Number)invoke(jpeg,"getTimestamp")).longValue();int width=((Number)invoke(jpeg,"getWidth")).intValue(),height=((Number)invoke(jpeg,"getHeight")).intValue();
    Object transported=Class.forName("com.oplus.ocs.camera.consumer.apsAdapter.adapter.ApsResult").getConstructor().newInstance();
    put(transported,"mIdentity",timestamp);put(transported,"mWidth",width);put(transported,"mHeight",height);put(transported,"mImageWidth",width);put(transported,"mImageHeight",height);put(transported,"mStride",width);put(transported,"mScanline",height);put(transported,"mBufferType",256);put(transported,"mMetadata",result);put(transported,"mDataSize",(long)((byte[])invoke(jpeg,"getImage")).length);put(transported,"mCaptureMode","photo_mode");put(transported,"mPictureTitle",get(tag,"mPictureTitle"));invoke(b,"setApsResult",transported);
    Class<?> category=Class.forName("com.oplus.ocs.camera.common.parameter.apsadapter.ApsImageCategoryParameter");
    String[] names={"KEY_IMAGE_CATEGORY_WIDTH","KEY_IMAGE_CATEGORY_HEIGHT","KEY_IMAGE_CATEGORY_FORMAT","KEY_IMAGE_CATEGORY_IMAGE_TIME_STAMP","KEY_IMAGE_CATEGORY_META_TIME_STAMP","KEY_IMAGE_CATEGORY_MERGE_IDENTITY","KEY_IMAGE_CATEGORY_END_OF_CAPTURE","KEY_IMAGE_CATEGORY_IS_VALID"};
    Object[] vals={width,height,256,timestamp,timestamp,timestamp,true,true};for(int i=0;i<names.length;i++)invoke(b,"set",category.getField(names[i]).get(null),vals[i]);
    Object pictureResult=invoke(b,"build");
    invoke(callback,"onCaptureCompleted",request,pictureResult);
    invoke(callback,"onImageReceived",jpeg);
    invoke(callback,"onFinishAddFrame",pictureResult);
    Object producer=get(consumer,"mProducerInterface");invoke(producer,"onFinishAddFrame");invoke(producer,"onCaptureCompleted");
    audit("PublicPhoto delivered actual JPEG to OEM save callback");
   }catch(Exception e){audit("PublicPhoto delivery failed="+android.util.Log.getStackTraceString(e));}finally{restore();}});
  }
  synchronized void fail(Exception e){if(done)return;done=true;audit("PublicPhoto failed="+android.util.Log.getStackTraceString(e));handler.post(()->{try{invoke(get(tag,"mCallback"),"onCaptureFailed",request,null);}catch(Exception ignored){}finally{restore();}});}
  void publishPhoto(){android.net.Uri uri=null;boolean inserted=false;try{
   byte[] bytes=(byte[])invoke(jpeg,"getImage");android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
   if(bounds.outWidth<=0||bounds.outHeight<=0)throw new java.io.IOException("Camera returned invalid JPEG");
   String title=(String)get(tag,"mPictureTitle");if(title==null||title.isEmpty())title="IMG"+new java.text.SimpleDateFormat("yyyyMMddHHmmssSSS",java.util.Locale.US).format(new java.util.Date(wallTime));
   String name=title.toLowerCase(java.util.Locale.US).endsWith(".jpg")?title:title+".jpg";
   android.content.ContentResolver resolver=testContext.getContentResolver();
   android.net.Uri collection=android.provider.MediaStore.setIncludePending(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
   try(android.database.Cursor c=resolver.query(collection,new String[]{"_id","is_pending","_size","width","height"},"owner_package_name=? AND _display_name=? AND date_added>=?",new String[]{testContext.getPackageName(),name,Long.toString(wallTime/1000-5)},"_id DESC")){
    if(c!=null&&c.moveToFirst()){
     if(c.getInt(1)==0&&c.getLong(2)>100000&&c.getInt(3)==bounds.outWidth&&c.getInt(4)==bounds.outHeight){audit("PublicPhoto OEM already finalized "+c.getLong(0));return;}
     if(c.getInt(1)==1)uri=android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0));
    }
   }
   if(uri==null){android.content.ContentValues v=new android.content.ContentValues();v.put("_display_name",name);v.put("mime_type","image/jpeg");v.put("relative_path","DCIM/Camera/");v.put("is_pending",1);uri=resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);inserted=true;}
   if(uri==null)throw new java.io.IOException("MediaStore insert failed");
   try(java.io.OutputStream stream=resolver.openOutputStream(uri,"w")){if(stream==null)throw new java.io.IOException("MediaStore output unavailable");stream.write(bytes);stream.flush();}
   android.media.ExifInterface exif=new android.media.ExifInterface(new java.io.ByteArrayInputStream(bytes));int rotation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);int degrees=rotation==6?90:rotation==3?180:rotation==8?270:0;
   android.content.ContentValues v=new android.content.ContentValues();v.put("width",bounds.outWidth);v.put("height",bounds.outHeight);v.put("orientation",degrees);v.put("datetaken",wallTime);v.put("_size",bytes.length);v.put("is_pending",0);v.putNull("date_expires");
   if(resolver.update(uri,v,null,null)!=1)throw new java.io.IOException("MediaStore publication failed");
   testContext.getSharedPreferences("n810-photo",0).edit().putString("last_saved_uri",uri.toString()).apply();
   refreshSavedThumbnail();
   audit("PublicPhoto saved uri="+uri+" size="+bounds.outWidth+"x"+bounds.outHeight+" bytes="+bytes.length+" orientation="+degrees+" reusedPending="+!inserted+" title="+title);
  }catch(Exception e){audit("PublicPhoto save failed="+android.util.Log.getStackTraceString(e));new android.os.Handler(android.os.Looper.getMainLooper()).post(()->android.widget.Toast.makeText(testContext,"Photo could not be saved. Please try again.",android.widget.Toast.LENGTH_LONG).show());}}
  synchronized void restore(){if(restored)return;restored=true;try{
   java.util.Map<?,?> outputs=(java.util.Map<?,?>)get(camera,"mOutputConfigurationMap");
   int mode=((Number)invoke(get(camera,"mSessionEntity"),"getOperationMode")).intValue();
   final android.hardware.camera2.CameraCaptureSession.StateCallback original=(android.hardware.camera2.CameraCaptureSession.StateCallback)get(camera,"mSessionStateCallback");
   android.hardware.camera2.params.SessionConfiguration config=new android.hardware.camera2.params.SessionConfiguration(mode,new java.util.ArrayList(outputs.values()),r->handler.post(r),new android.hardware.camera2.CameraCaptureSession.StateCallback(){
    public void onConfigureFailed(android.hardware.camera2.CameraCaptureSession se){original.onConfigureFailed(se);audit("PublicPhoto preview restore rejected");}
    public void onConfigured(android.hardware.camera2.CameraCaptureSession se){original.onConfigured(se);try{se.setRepeatingRequest(preview,(android.hardware.camera2.CameraCaptureSession.CaptureCallback)get(camera,"mPreviewCallback"),handler);audit("PublicPhoto original preview restored");}catch(Exception e){audit("PublicPhoto restore repeating="+e);}finally{reader.close();meter.close();}}
   });config.setSessionParameters(preview);device.createCaptureSession(config);
  }catch(Exception e){audit("PublicPhoto restore failed="+android.util.Log.getStackTraceString(e));}}

 }

 public static void apply() {
  if (!"com.n810.camera.aicomposition.lab".equals(android.app.Application.getProcessName())) return;
  try {
   Class<?> type=Class.forName("com.oplus.ocs.camera.metadata.parameter.converter.CustomParameterConverter");
   Field converterField=type.getDeclaredField("sParameterConverter");converterField.setAccessible(true);
   Object converter=converterField.get(null);
   if(converter==null){
    Class<?> platform=Class.forName("com.oplus.ocs.camera.platform.PlatformUtil");
    String flag=(String)platform.getMethod("getPlatformFlag").invoke(null);
    type.getMethod("init",String.class).invoke(null,flag);
    converter=converterField.get(null);
    Log.i("CameraMetadataTest","Initialized SDK converter using normal platform="+flag);
   }
   Field mapField=null;
   for(Class<?> c=converter.getClass();c!=null;c=c.getSuperclass()){
    try{mapField=c.getDeclaredField("mCustomParameterMap");break;}catch(NoSuchFieldException ignored){}
   }
   if(mapField==null)throw new NoSuchFieldException("mCustomParameterMap");
   mapField.setAccessible(true);Map<?,?> map=(Map<?,?>)mapField.get(converter);
   Object previous=map.remove("com.oplus.configure.is.oplus.package");
   java.util.Iterator<? extends Map.Entry<?,?>> entries=map.entrySet().iterator();
   while(entries.hasNext()){
    Map.Entry<?,?> entry=entries.next();Object value=entry.getValue();
    if(value instanceof android.hardware.camera2.CaptureRequest.Key && "com.oplus.is.sdk.camera.package".equals(((android.hardware.camera2.CaptureRequest.Key<?>)value).getName())){
     Log.i("CameraMetadataTest","Matched actual SDK key="+entry.getKey());previous=value;entries.remove();
    }
   }
   Log.i("CameraMetadataTest","Removed SDK client marker mapping="+(previous!=null)+" remainingMappings="+map.size()+"; no system state or signing identity changed");
  }catch(Exception e){Log.e("CameraMetadataTest","Test NOT applied",e);}
 }
}
