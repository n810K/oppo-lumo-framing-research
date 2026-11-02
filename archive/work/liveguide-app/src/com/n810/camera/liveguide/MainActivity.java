package com.n810.camera.liveguide;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.*;
import android.view.*;
import android.widget.*;
import com.cv.ai_camera_composition_lite2.AICameraCompositionLite2;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
 private final Handler ui=new Handler(Looper.getMainLooper());
 private HandlerThread cameraThread; private Handler cameraHandler;
 private final ExecutorService engine=Executors.newSingleThreadExecutor();
 private final ExecutorService storage=Executors.newSingleThreadExecutor();
 private final Object evidenceLock=new Object(); private final JSONObject evidence=new JSONObject();
 private CameraManager manager; private CameraCharacteristics chars;
 private CameraDevice camera; private CameraCaptureSession session; private CameraExtensionSession extensionSession;
 private CaptureRequest.Builder repeating; private ImageReader jpeg; private Surface previewSurface;
 private TextureView texture; private GuideOverlay overlay; private TextView status,zoomText; private Button guideButton,modeButton;
 private FrameLayout viewfinder; private volatile boolean resumed,opening,front,engineReady,analyzing,guiding,capturing;
 private volatile int generation,mode=-1,frameCount; private int sensorOrientation=90; private long engineHandle;
 private volatile float zoom=1f; private Range<Float> zoomRange=new Range<>(1f,10f); private String cameraId="0";
 private Uri lastPhoto; private RectF suggestion; private float suggestedZoom=1f; private boolean zoomVerified;
 private Size previewSize; private boolean autoZoom=true; private Set<CaptureRequest.Key> extensionKeys=Collections.emptySet();
 private static final int GOLD=0xffffb04a;

 @Override public void onCreate(Bundle state) {
  super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  cameraThread=new HandlerThread("CameraSession");cameraThread.start();cameraHandler=new Handler(cameraThread.getLooper());
  manager=(CameraManager)getSystemService(CAMERA_SERVICE);buildUi();prepareEngine();
  if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},10);
 }
 private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
 private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setGravity(Gravity.CENTER);return v;}
 private Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setOnClickListener(v->action.run());return b;}
 private void buildUi(){
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff08090b);
  root.setOnApplyWindowInsetsListener((v,insets)->{Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});
  TextView title=label("Camera",20);root.addView(title,new LinearLayout.LayoutParams(-1,dp(40)));
  viewfinder=new FrameLayout(this);texture=new TextureView(this);overlay=new GuideOverlay();viewfinder.addView(texture,new FrameLayout.LayoutParams(-1,-1));viewfinder.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
  int height=Math.round(getResources().getDisplayMetrics().widthPixels*4f/3f);root.addView(viewfinder,new LinearLayout.LayoutParams(-1,height));
  status=label("Preparing Live guide",13);status.setMaxLines(2);root.addView(status,new LinearLayout.LayoutParams(-1,dp(46)));
  LinearLayout presets=new LinearLayout(this);presets.setGravity(Gravity.CENTER);
  for(float z:new float[]{0.6f,1f,2f,3f,6f}){final float value=z;presets.addView(button(String.format(Locale.US,"%s×",z==Math.floor(z)?Integer.toString((int)z):Float.toString(z)),()->setZoom(value)),new LinearLayout.LayoutParams(0,dp(44),1));}root.addView(presets);
  zoomText=label("1.00×",13);root.addView(zoomText,new LinearLayout.LayoutParams(-1,dp(24)));
  LinearLayout guideRow=new LinearLayout(this);guideButton=button("Live guide",()->{guiding=!guiding;guideButton.setText(guiding?"Stop guide":"Live guide");if(guiding)analyze();else {suggestion=null;overlay.invalidate();say("Guide stopped");}});guideRow.addView(guideButton,new LinearLayout.LayoutParams(0,dp(48),1));
  Switch auto=new Switch(this);auto.setText("Auto zoom");auto.setTextColor(Color.WHITE);auto.setChecked(true);auto.setOnCheckedChangeListener((v,on)->autoZoom=on);guideRow.addView(auto,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(guideRow);
  LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
  controls.addView(button("Gallery",()->openGallery()),new LinearLayout.LayoutParams(0,dp(56),1));
  Button shutter=button("Take photo",()->capture());shutter.setTextColor(GOLD);controls.addView(shutter,new LinearLayout.LayoutParams(0,dp(56),1));
  controls.addView(button("Flip",()->{front=!front;mode=-1;restartCamera();}),new LinearLayout.LayoutParams(0,dp(56),1));root.addView(controls);
  modeButton=button("Photo",()->chooseMode());root.addView(modeButton,new LinearLayout.LayoutParams(-1,dp(42)));
  setContentView(root);
  root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->{int available=root.getHeight()-root.getPaddingTop()-root.getPaddingBottom();int ph=Math.min(Math.round(root.getWidth()*4f/3f),available-dp(305));if(ph>100&&viewfinder.getLayoutParams().height!=ph){viewfinder.getLayoutParams().height=ph;viewfinder.requestLayout();int pw=Math.round(ph*.75f);FrameLayout.LayoutParams fit=new FrameLayout.LayoutParams(pw,ph,Gravity.CENTER);texture.setLayoutParams(fit);overlay.setLayoutParams(new FrameLayout.LayoutParams(pw,ph,Gravity.CENTER));}});
  texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h){openCamera();}public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h){transform();}public boolean onSurfaceTextureDestroyed(SurfaceTexture s){closeCamera();return true;}public void onSurfaceTextureUpdated(SurfaceTexture s){frameCount++;}});
  ScaleGestureDetector scale=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){setZoom(zoom*d.getScaleFactor());return true;}});
  texture.setOnTouchListener((v,e)->{scale.onTouchEvent(e);return true;});
 }
 private void say(String s){ui.post(()->status.setText(s));Log.i("N810LiveGuide",s);}
 private void record(String key,Object value){synchronized(evidenceLock){try{evidence.put(key,value);try(FileOutputStream f=openFileOutput("evidence.json",MODE_PRIVATE)){f.write(evidence.toString(2).getBytes("UTF-8"));}}catch(Exception e){Log.e("N810LiveGuide","Evidence",e);}}}
 private void prepareEngine(){engine.execute(()->{try{
  File dir=new File(getFilesDir(),"models");dir.mkdirs();String[] names=getAssets().list("models");
  for(String n:names){File target=new File(dir,n);if(!target.isFile()||target.length()==0){File temp=new File(dir,n+".part");try(InputStream in=getAssets().open("models/"+n);FileOutputStream out=new FileOutputStream(temp)){byte[] b=new byte[1024*1024];int len;while((len=in.read(b))!=-1)out.write(b,0,len);}if(!temp.renameTo(target))throw new IOException("Cannot finish model copy");}}
  File config=new File(dir,"composition_lite2_config.yaml");String text=new String(java.nio.file.Files.readAllBytes(config.toPath()),"UTF-8");text=text.replace("/odm/etc/camera/ai_composition/",dir.getAbsolutePath()+"/");java.nio.file.Files.write(config.toPath(),text.getBytes("UTF-8"));
  System.loadLibrary("ai_camera_composition_lite2");engineHandle=AICameraCompositionLite2.nativeCreate(config.getAbsolutePath());if(engineHandle==0||!AICameraCompositionLite2.nativeInit(engineHandle))throw new IOException("Engine initialization failed");engineReady=true;record("engine_ready",true);say("Live guide ready");
 }catch(Throwable e){record("engine_error",e.toString());say("Live guide unavailable: "+e.getClass().getSimpleName());}});}
 @Override protected void onResume(){super.onResume();resumed=true;if(texture.isAvailable())openCamera();}
 @Override protected void onPause(){resumed=false;guiding=false;guideButton.setText("Live guide");closeCamera();super.onPause();}
 @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==10&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)openCamera();else say("Camera permission is needed for the viewfinder");}
 private Size selectSize(List<Size> sizes,int targetWidth){return sizes.stream().filter(s->Math.abs((double)s.getWidth()/s.getHeight()-4d/3)<0.035).min(Comparator.comparingInt(s->Math.abs(s.getWidth()-targetWidth))).orElseGet(()->sizes.stream().min(Comparator.comparingInt(s->Math.abs(s.getWidth()-targetWidth))).get());}
 private void openCamera(){
  if(!resumed||opening||camera!=null||!texture.isAvailable()||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
  opening=true;frameCount=0;record("session_ready",false);record("process_id",android.os.Process.myPid());final int token=++generation;record("session_generation",token);
  try{
   for(String id:manager.getCameraIdList()){CameraCharacteristics c=manager.getCameraCharacteristics(id);Integer facing=c.get(CameraCharacteristics.LENS_FACING);if(facing!=null&&facing==(front?CameraCharacteristics.LENS_FACING_FRONT:CameraCharacteristics.LENS_FACING_BACK)){cameraId=id;chars=c;break;}}
   sensorOrientation=chars.get(CameraCharacteristics.SENSOR_ORIENTATION);Range<Float> range=chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);zoomRange=range==null?new Range<>(1f,chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)):range;zoom=zoomRange.clamp(1f);
   StreamConfigurationMap map=chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);List<Size> previews=Arrays.asList(map.getOutputSizes(SurfaceTexture.class));List<Size> jpegs=Arrays.asList(map.getOutputSizes(ImageFormat.JPEG));
   if(mode>=0){CameraExtensionCharacteristics ec=manager.getCameraExtensionCharacteristics(cameraId);previews=ec.getExtensionSupportedSizes(mode,SurfaceTexture.class);jpegs=ec.getExtensionSupportedSizes(mode,ImageFormat.JPEG);extensionKeys=ec.getAvailableCaptureRequestKeys(mode);}
   previewSize=selectSize(previews,1280);Size still=selectSize(jpegs,4096);texture.getSurfaceTexture().setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());previewSurface=new Surface(texture.getSurfaceTexture());transform();
   jpeg=ImageReader.newInstance(still.getWidth(),still.getHeight(),ImageFormat.JPEG,2);jpeg.setOnImageAvailableListener(reader->{try(Image image=reader.acquireNextImage()){if(image==null)return;ByteBuffer b=image.getPlanes()[0].getBuffer();byte[] bytes=new byte[b.remaining()];b.get(bytes);storage.execute(()->savePhoto(bytes));}catch(Exception e){capturing=false;say("Photo failed: "+e.getMessage());}},cameraHandler);
   record("camera_id",cameraId);record("preview_size",previewSize.toString());record("jpeg_size",still.toString());record("zoom_range",zoomRange.toString());record("mode",mode);record("extension_zoom_supported",mode<0||extensionKeys.contains(CaptureRequest.CONTROL_ZOOM_RATIO));record("crop_type",chars.get(CameraCharacteristics.SCALER_CROPPING_TYPE));
   manager.openCamera(cameraId,new CameraDevice.StateCallback(){public void onOpened(CameraDevice c){if(token!=generation||!resumed){c.close();return;}camera=c;opening=false;createSession(token);}public void onDisconnected(CameraDevice c){c.close();camera=null;opening=false;say("Camera disconnected");}public void onError(CameraDevice c,int error){c.close();camera=null;opening=false;record("camera_error",error);say("Camera error "+error);}},cameraHandler);
  }catch(Exception e){opening=false;record("open_error",e.toString());say("Cannot open camera: "+e.getMessage());}
 }
 private void transform(){if(previewSize==null||texture.getWidth()==0)return;/* Landscape sensor buffer becomes portrait without stretching. */
  float w=texture.getWidth(),h=texture.getHeight();Matrix m=new Matrix();RectF view=new RectF(0,0,w,h);RectF buffer=new RectF(0,0,previewSize.getHeight(),previewSize.getWidth());buffer.offset(w/2-buffer.centerX(),h/2-buffer.centerY());m.setRectToRect(view,buffer,Matrix.ScaleToFit.FILL);float scale=Math.max(w/previewSize.getHeight(),h/previewSize.getWidth());m.postScale(scale,scale,w/2,h/2);if(front)m.postScale(-1,1,w/2,h/2);texture.setTransform(m);}
 private void createSession(int token){try{
  repeating=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);repeating.addTarget(previewSurface);applyControls(repeating);
  if(mode<0){camera.createCaptureSession(Arrays.asList(previewSurface,jpeg.getSurface()),new CameraCaptureSession.StateCallback(){public void onConfigured(CameraCaptureSession s){if(token!=generation){s.close();return;}session=s;startRepeating();sessionReady();}public void onConfigureFailed(CameraCaptureSession s){say("Photo preview configuration failed");record("session_error","photo configuration failed");}},cameraHandler);}
  else {ExtensionSessionConfiguration config=new ExtensionSessionConfiguration(mode,Arrays.asList(new OutputConfiguration(previewSurface),new OutputConfiguration(jpeg.getSurface())),cameraHandler::post,new CameraExtensionSession.StateCallback(){public void onConfigured(CameraExtensionSession s){if(token!=generation){try{s.close();}catch(Exception ignored){}return;}extensionSession=s;startRepeating();sessionReady();}public void onConfigureFailed(CameraExtensionSession s){record("session_error","extension "+mode+" configuration failed");say("OEM mode could not start; choose Photo");}public void onClosed(CameraExtensionSession s){}});camera.createExtensionSession(config);}
 }catch(Exception e){record("session_error",e.toString());say("Preview failed: "+e.getMessage());}}
 private void sessionReady(){record("session_ready",true);ui.post(()->{modeButton.setText(modeName());zoomText.setText("1.00×");say(modeName()+" ready");if(getIntent().getBooleanExtra("verify_zoom",false)&&mode<0&&!zoomVerified){zoomVerified=true;ui.postDelayed(()->setZoom(2.86f),1200);ui.postDelayed(()->{record("zoom_test_completed",true);setZoom(1f);},3000);}});}
 private String modeName(){return mode==CameraExtensionCharacteristics.EXTENSION_NIGHT?"Night":mode==CameraExtensionCharacteristics.EXTENSION_BOKEH?"Portrait":"Photo";}
 private <T> void key(CaptureRequest.Builder b,CaptureRequest.Key<T> k,T v){if(mode<0||extensionKeys.contains(k))b.set(k,v);}
 private void applyControls(CaptureRequest.Builder b){key(b,CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);key(b,CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);key(b,CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);key(b,CaptureRequest.CONTROL_ZOOM_RATIO,zoom);}
 private final CameraCaptureSession.CaptureCallback previewCallback=new CameraCaptureSession.CaptureCallback(){@Override public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest r,TotalCaptureResult result){Float z=result.get(CaptureResult.CONTROL_ZOOM_RATIO);if(z!=null){if(Math.abs(z-2.86f)<0.02f)record("verified_zoom_2_86",z);if(frameCount%30==0)record("reported_zoom",z);} }};
 private final CameraExtensionSession.ExtensionCaptureCallback extensionCallback=new CameraExtensionSession.ExtensionCaptureCallback(){@Override public void onCaptureResultAvailable(CameraExtensionSession s,CaptureRequest r,TotalCaptureResult result){Float z=result.get(CaptureResult.CONTROL_ZOOM_RATIO);if(z!=null&&frameCount%30==0)record("reported_zoom",z);}@Override public void onCaptureFailed(CameraExtensionSession s,CaptureRequest r){capturing=false;say("OEM capture failed");}};
 private void startRepeating(){try{if(repeating==null)return;applyControls(repeating);if(session!=null)session.setRepeatingRequest(repeating.build(),previewCallback,cameraHandler);else if(extensionSession!=null)extensionSession.setRepeatingRequest(repeating.build(),cameraHandler::post,extensionCallback);}catch(Exception e){record("repeat_error",e.toString());say("Camera control failed");}}
 private void setZoom(float z){if(camera==null)return;if(mode>=0&&!extensionKeys.contains(CaptureRequest.CONTROL_ZOOM_RATIO)){say("This OEM mode does not expose zoom control");return;}zoom=zoomRange.clamp(z);zoomText.setText(String.format(Locale.US,"%.2f×",zoom));record("requested_zoom",zoom);cameraHandler.post(this::startRepeating);}
 private void restartCamera(){guiding=false;guideButton.setText("Live guide");suggestion=null;overlay.invalidate();closeCamera();ui.postDelayed(this::openCamera,400);}
 private void closeCamera(){generation++;opening=false;try{if(session!=null)session.close();if(extensionSession!=null)extensionSession.close();}catch(Exception ignored){}session=null;extensionSession=null;repeating=null;if(camera!=null)camera.close();camera=null;if(jpeg!=null)jpeg.close();jpeg=null;if(previewSurface!=null)previewSurface.release();previewSurface=null;}
 private void chooseMode(){try{List<Integer> available=manager.getCameraExtensionCharacteristics(cameraId).getSupportedExtensions();ArrayList<String> labels=new ArrayList<>();ArrayList<Integer> modes=new ArrayList<>();labels.add("Photo");modes.add(-1);if(available.contains(CameraExtensionCharacteristics.EXTENSION_NIGHT)){labels.add("Night");modes.add(CameraExtensionCharacteristics.EXTENSION_NIGHT);}if(available.contains(CameraExtensionCharacteristics.EXTENSION_BOKEH)){labels.add("Portrait");modes.add(CameraExtensionCharacteristics.EXTENSION_BOKEH);}new android.app.AlertDialog.Builder(this).setTitle("Camera mode").setItems(labels.toArray(new String[0]),(d,i)->{mode=modes.get(i);restartCamera();}).show();}catch(Exception e){say("Cannot query OEM modes");}}
 private void analyze(){
  if(!guiding||analyzing||!resumed)return;if(!engineReady||camera==null||frameCount<3){say("Waiting for camera and guide");ui.postDelayed(this::analyze,1000);return;}
  Bitmap bitmap=texture.getBitmap(768,1024);if(bitmap==null){ui.postDelayed(this::analyze,1000);return;}analyzing=true;final int token=generation;final float initialZoom=zoom;say("Finding a composition");
  engine.execute(()->{try{int[] pixels=new int[768*1024];bitmap.getPixels(pixels,0,768,0,0,768,1024);bitmap.recycle();ByteBuffer rgba=ByteBuffer.allocateDirect(pixels.length*4);double sum=0,sum2=0;for(int c:pixels){int r=(c>>16)&255,g=(c>>8)&255,b=c&255;rgba.put((byte)r).put((byte)g).put((byte)b).put((byte)255);double y=(r+g+b)/3d;sum+=y;sum2+=y*y;}rgba.rewind();record("frame_variance",sum2/pixels.length-Math.pow(sum/pixels.length,2));long start=SystemClock.elapsedRealtime();String raw=AICameraCompositionLite2.nativeRun(engineHandle,rgba,768,1024,768*4,true);JSONObject result=new JSONObject(raw);record("inference_ms",SystemClock.elapsedRealtime()-start);record("inference",result);record("inference_source","live TextureView preview");
   ui.post(()->{analyzing=false;if(token!=generation||!guiding||Math.abs(zoom-initialZoom)>.02f){if(guiding)ui.postDelayed(this::analyze,700);return;}try{JSONArray crops=result.optJSONArray("crops");if(crops==null||crops.length()==0){say("No framing suggestion yet");ui.postDelayed(this::analyze,1800);return;}JSONObject c=crops.getJSONObject(0);JSONArray rect=c.getJSONArray("srcCrop");RectF box=new RectF((float)rect.getDouble(0)/768,(float)rect.getDouble(1)/1024,(float)rect.getDouble(2)/768,(float)rect.getDouble(3)/1024);if(box.left<0||box.top<0||box.right>1||box.bottom>1||box.width()<=0||box.height()<=0)throw new Exception("Invalid crop");suggestion=box;overlay.invalidate();suggestedZoom=zoomRange.clamp(initialZoom*(float)c.optDouble("zoom",1));float dx=box.centerX()-.5f,dy=box.centerY()-.5f;
    if(Math.abs(dx)<Math.min(.035f,box.width()*.12f)&&Math.abs(dy)<Math.min(.035f,box.height()*.12f)){if(autoZoom&&suggestedZoom>initialZoom*1.08f){setZoom(suggestedZoom);record("guide_auto_zoom",suggestedZoom);record("accepted_crop_center_error",new JSONArray(Arrays.asList(dx,dy)));suggestion=null;overlay.invalidate();say("Framing applied — ready to shoot");guiding=false;guideButton.setText("Live guide");}else say("Composition aligned");}
    else {String horizontal=Math.abs(dx)>=Math.min(.035f,box.width()*.12f)?(dx<0?"left":"right"):"";String vertical=Math.abs(dy)>=Math.min(.035f,box.height()*.12f)?(dy<0?"up":"down"):"";say("Aim "+horizontal+(horizontal.isEmpty()||vertical.isEmpty()?"":" and ")+vertical+" to align the frame");}
    if(guiding)ui.postDelayed(this::analyze,1800);
   }catch(Exception e){say("Guide result could not be applied");record("guide_result_error",e.toString());guiding=false;guideButton.setText("Live guide");}});
  }catch(Throwable e){record("inference_error",e.toString());ui.post(()->{analyzing=false;guiding=false;guideButton.setText("Live guide");say("Live guide failed; camera remains available");});}});
 }
 private void capture(){if(camera==null||capturing)return;capturing=true;say("Taking photo");cameraHandler.post(()->{try{CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);request.addTarget(jpeg.getSurface());applyControls(request);key(request,CaptureRequest.JPEG_ORIENTATION,sensorOrientation);key(request,CaptureRequest.JPEG_QUALITY,(byte)95);if(session!=null)session.capture(request.build(),new CameraCaptureSession.CaptureCallback(){@Override public void onCaptureFailed(CameraCaptureSession s,CaptureRequest r,CaptureFailure f){capturing=false;say("Photo capture failed");}},cameraHandler);else if(extensionSession!=null)extensionSession.capture(request.build(),cameraHandler::post,extensionCallback);else capturing=false;}catch(Exception e){capturing=false;say("Photo failed: "+e.getMessage());}});}
 private void savePhoto(byte[] data){Uri uri=null;try{ContentValues v=new ContentValues();v.put(MediaStore.Images.Media.DISPLAY_NAME,"Camera_"+System.currentTimeMillis()+".jpg");v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");v.put(MediaStore.Images.Media.RELATIVE_PATH,"DCIM/Camera Live Guide");v.put(MediaStore.Images.Media.IS_PENDING,1);uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException("MediaStore insert failed");try(OutputStream out=getContentResolver().openOutputStream(uri)){out.write(data);}v.clear();v.put(MediaStore.Images.Media.IS_PENDING,0);getContentResolver().update(uri,v,null,null);lastPhoto=uri;record("last_photo",uri.toString());say("Photo saved");}catch(Exception e){if(uri!=null)getContentResolver().delete(uri,null,null);say("Could not save photo");record("save_error",e.toString());}finally{capturing=false;}}
 private void openGallery(){try{if(lastPhoto!=null)startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(lastPhoto,"image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));else startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,Intent.CATEGORY_APP_GALLERY));}catch(ActivityNotFoundException e){say("No gallery app could open this photo");}}
 private final class GuideOverlay extends View {private final Paint paint=new Paint(3);GuideOverlay(){super(MainActivity.this);setClickable(false);}@Override protected void onDraw(Canvas c){if(suggestion==null)return;paint.setColor(GOLD);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));RectF r=new RectF(suggestion.left*getWidth(),suggestion.top*getHeight(),suggestion.right*getWidth(),suggestion.bottom*getHeight());c.drawRoundRect(r,dp(5),dp(5),paint);float x=getWidth()/2f,y=getHeight()/2f;c.drawLine(x-dp(8),y,x+dp(8),y,paint);c.drawLine(x,y-dp(8),x,y+dp(8),paint);}}
 @Override protected void onDestroy(){closeCamera();engine.execute(()->{if(engineHandle!=0){AICameraCompositionLite2.nativeDestroy(engineHandle);engineHandle=0;}});engine.shutdown();storage.shutdown();cameraThread.quitSafely();super.onDestroy();}
}
