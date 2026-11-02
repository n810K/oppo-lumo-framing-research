#include "jvmti.h"
extern int __android_log_print(int,const char*,const char*,...);
#include <stdarg.h>
extern int vsnprintf(char*,unsigned long,const char*,va_list);
extern int open(const char*,int,...);extern long write(int,const void*,unsigned long);extern int close(int);extern int getpid(void);
static int trace_log(int level,const char*tag,const char*format,...){char buffer[2048];va_list args;va_start(args,format);int n=vsnprintf(buffer,sizeof(buffer),format,args);va_end(args);int r=__android_log_print(level,tag,"%s",buffer);if(n>2046)n=2046;if(n>0){buffer[n++]='\n';int fd=open("/data/user/0/com.n810.camera.aicomposition.lab/files/agent-trace.txt",1|64|1024,0600);if(fd>=0){write(fd,buffer,n);close(fd);}}return r;}
#define __android_log_print trace_log
static int same(const char*a,const char*b){if(!a||!b)return 0;while(*a&&*a==*b){a++;b++;}return *a==*b;}
static void drop(jvmtiEnv*j,char*p){if(p)(*j)->Deallocate(j,(unsigned char*)p);}
static void JNICALL exception_event(jvmtiEnv*j,JNIEnv*env,jthread thread,jmethodID method,jlocation loc,jobject ex,jmethodID caught,jlocation cloc){
 if(!caught)return;
 jclass c;char*sig=0;
 if((*j)->GetMethodDeclaringClass(j,caught,&c))return;
 (*j)->GetClassSignature(j,c,&sig,0);
 int match=same(sig,"Lcom/oplus/ocs/camera/consumer/apsAdapter/APSClientCallback;");drop(j,sig);if(!match)return;
 jclass ec=(*env)->GetObjectClass(env,ex);jmethodID gm=(*env)->GetMethodID(env,ec,"getMessage","()Ljava/lang/String;");
 if(gm){jstring message=(jstring)(*env)->CallObjectMethod(env,ex,gm);if(message){const char*text=(*env)->GetStringUTFChars(env,message,0);__android_log_print(6,"CameraExceptionTrace","exception message=%s",text);(*env)->ReleaseStringUTFChars(env,message,text);}}
 char*es=0;(*j)->GetClassSignature(j,ec,&es,0);
 __android_log_print(6,"CameraExceptionTrace","APS callback exception=%s location=%lld catchLocation=%lld",es,(long long)loc,(long long)cloc);drop(j,es);
 jvmtiFrameInfo frames[12];jint count=0;(*j)->GetStackTrace(j,thread,0,12,frames,&count);
 for(int i=0;i<count;i++){
  char*n=0,*s=0; jclass k;
  (*j)->GetMethodName(j,frames[i].method,&n,0,0);(*j)->GetMethodDeclaringClass(j,frames[i].method,&k);(*j)->GetClassSignature(j,k,&s,0);
  __android_log_print(6,"CameraExceptionTrace","frame %d %s.%s dexPc=%lld",i,s,n,(long long)frames[i].location);drop(j,n);drop(j,s);
 }
}

static jclass aps_client_class=0, frame_helper=0;
static int repairs=0;
static jmethodID aps_callback_method, data_callback_method, ipu_create_method, capture_valid_method, composition_event_method;
static jmethodID public_photo_method, shutter_touch_method, shutter_click_method, zoom_method;
static jobject registered_client;
static int frame_logs=0,last_requested=-1;
static jvmtiIterationControl JNICALL tag_client(jlong class_tag,jlong size,jlong*tag,void*user){*tag=0x810ca;return JVMTI_ITERATION_CONTINUE;}
static jobject find_registered_client(jvmtiEnv*j,JNIEnv*env){
 (*j)->IterateOverInstancesOfClass(j,aps_client_class,JVMTI_HEAP_OBJECT_EITHER,tag_client,0);
 jlong tag=0x810ca;jint count=0;jobject*objects=0;(*j)->GetObjectsWithTags(j,1,&tag,&count,&objects,0);
 jfieldID cb=(*env)->GetFieldID(env,aps_client_class,"mBufferCallback","Lcom/oplus/ocs/camera/consumer/apsAdapter/APSClient$BufferCallback;");
 jobject selected=0;int candidates=0;
 for(int i=0;i<count;i++){jobject callback=(*env)->GetObjectField(env,objects[i],cb);if(callback){selected=objects[i];candidates++;}(*j)->SetTag(j,objects[i],0);}
 __android_log_print(4,"CameraCallbackRepair","Client census objects=%d registered=%d",count,candidates);
 jobject result=candidates==1?(*env)->NewGlobalRef(env,selected):0;
 for(int i=0;i<count;i++)(*env)->DeleteLocalRef(env,objects[i]);if(objects)(*j)->Deallocate(j,(unsigned char*)objects);return result;
}

static void JNICALL breakpoint_event(jvmtiEnv*j,JNIEnv*env,jthread thread,jmethodID method,jlocation location){
 if(method==shutter_touch_method||method==shutter_click_method){jobject receiver=0,event=0;(*j)->GetLocalInstance(j,thread,0,&receiver);if(method==shutter_touch_method)(*j)->GetLocalObject(j,thread,0,13,&event);if(receiver&&frame_helper){jmethodID diagnostic=(*env)->GetStaticMethodID(env,frame_helper,"shutterEvent","(Ljava/lang/Object;Landroid/view/MotionEvent;)V");if(diagnostic)(*env)->CallStaticVoidMethod(env,frame_helper,diagnostic,receiver,event);}return;}
 if(method==zoom_method){jfloat zoom=0;if(!(*j)->GetLocalFloat(j,thread,0,6,&zoom)&&frame_helper){jmethodID diagnostic=(*env)->GetStaticMethodID(env,frame_helper,"zoomEvent","(F)V");if(diagnostic)(*env)->CallStaticVoidMethod(env,frame_helper,diagnostic,zoom);}return;}
 if(method==public_photo_method){
  jobject receiver=0,tag=0,param=0,handler=0;(*j)->GetLocalInstance(j,thread,0,&receiver);(*j)->GetLocalObject(j,thread,0,27,&tag);(*j)->GetLocalObject(j,thread,0,28,&param);(*j)->GetLocalObject(j,thread,0,29,&handler);
  if(receiver&&tag&&handler&&frame_helper){jmethodID take=(*env)->GetStaticMethodID(env,frame_helper,"takePublicPhoto","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Landroid/os/Handler;)Z");if(take&&(*env)->CallStaticBooleanMethod(env,frame_helper,take,receiver,tag,param,handler)){int result=(*j)->ForceEarlyReturnVoid(j,thread);__android_log_print(4,"CameraPublicPhoto","Public photo submitted, early return=%d",result);}}return;
 }
 if(method==composition_event_method){
  jobject payload=0;jint event=0;int result=(*j)->GetLocalObject(j,thread,0,59,&payload);(*j)->GetLocalInt(j,thread,0,58,&event);
  if(!result&&payload&&frame_helper){jmethodID diagnostic=(*env)->GetStaticMethodID(env,frame_helper,"compositionEvent","(ILjava/lang/String;)V");if(diagnostic)(*env)->CallStaticVoidMethod(env,frame_helper,diagnostic,event,payload);}
  return;
 }
 if(method==capture_valid_method){
  jobject receiver=0;(*j)->GetLocalInstance(j,thread,0,&receiver);
  if(receiver&&frame_helper){jmethodID diagnostic=(*env)->GetStaticMethodID(env,frame_helper,"captureDiagnostic","(Ljava/lang/Object;)V");if(diagnostic)(*env)->CallStaticVoidMethod(env,frame_helper,diagnostic,receiver);}
  return;
 }
 if(method==ipu_create_method){
  if(!registered_client){__android_log_print(5,"CameraSharedClient","No registered camera client; preserving original IPU initialization");return;}
  jclass type=0;(*j)->GetMethodDeclaringClass(j,method,&type);
  jfieldID field=(*env)->GetStaticFieldID(env,type,"mAPSClient","Lcom/oplus/ocs/camera/consumer/apsAdapter/APSClient;");
  if(!field){(*env)->ExceptionClear(env);return;}
  jobject previous=(*env)->GetStaticObjectField(env,type,field);
  (*env)->SetStaticObjectField(env,type,field,registered_client);
  jvmtiError result=(*j)->ForceEarlyReturnVoid(j,thread);
  if(result)(*env)->SetStaticObjectField(env,type,field,previous);
  __android_log_print(4,"CameraSharedClient","Reuse registered APS client for IPU, result=%d",result);return;
 }
 if(method==data_callback_method){
  jobject receiver=0,callback=0;(*j)->GetLocalInstance(j,thread,0,&receiver);(*j)->GetLocalObject(j,thread,0,1,&callback);
  jint identity=0;if(receiver)(*j)->GetObjectHashCode(j,receiver,&identity);
  __android_log_print(4,"CameraCallbackRepair","setDataCallback client=%d callbackPresent=%d",identity,callback!=0);
  if(receiver&&callback){if(registered_client)(*env)->DeleteGlobalRef(env,registered_client);registered_client=(*env)->NewGlobalRef(env,receiver);}return;
 }
 if(method!=aps_callback_method){
  char*name=0,*sig=0;jclass type;(*j)->GetMethodName(j,method,&name,0,0);(*j)->GetMethodDeclaringClass(j,method,&type);(*j)->GetClassSignature(j,type,&sig,0);
  if(same(sig,"Lfc/d3;")&&same(name,"K2")){
   jobject receiver=0;(*j)->GetLocalInstance(j,thread,0,&receiver);
   if(receiver){jfieldID active=(*env)->GetFieldID(env,type,"a2","Z");jfieldID mode=(*env)->GetFieldID(env,type,"n2","I");jfieldID ready=(*env)->GetFieldID(env,type,"o2","Z");
    int requested=(*env)->GetBooleanField(env,receiver,active);
    jint identity=0;(*j)->GetObjectHashCode(j,receiver,&identity);if(frame_logs++<5||frame_logs%120==0||requested!=last_requested){__android_log_print(4,"CameraFeatureTrace","K2 identity=%d count=%d requested=%d mode=%d ready=%d",identity,frame_logs,requested,(*env)->GetIntField(env,receiver,mode),(*env)->GetBooleanField(env,receiver,ready));last_requested=requested;}
   }
  }else {
   jobject receiver=0;jint identity=0;(*j)->GetLocalInstance(j,thread,0,&receiver);if(receiver)(*j)->GetObjectHashCode(j,receiver,&identity);
   __android_log_print(4,"CameraFeatureTrace","Entered %s.%s identity=%d",sig,name,identity);
  }
  if(same(sig,"Lgc/t;")&&same(name,"f")&&frame_helper){jobject buffer=0;int result=(*j)->GetLocalObject(j,thread,0,34,&buffer);if(!result&&buffer){jmethodID dump=(*env)->GetStaticMethodID(env,frame_helper,"dumpCompositionFrame","(Landroid/hardware/HardwareBuffer;)V");if(dump)(*env)->CallStaticVoidMethod(env,frame_helper,dump,buffer);}}
  drop(j,name);drop(j,sig);return;
 }
 jobject ref=0;jvmtiError error=(*j)->GetLocalObject(j,thread,0,3,&ref);
 if(error||ref||!aps_client_class)return;
 jmethodID get=(*env)->GetStaticMethodID(env,aps_client_class,"getClient","()Lcom/oplus/ocs/camera/consumer/apsAdapter/APSClient;");
 jobject client=get?(*env)->CallStaticObjectMethod(env,aps_client_class,get):0;
 static int census_done=0;if(!census_done){census_done=1;jobject found=find_registered_client(j,env);if(found){if(registered_client)(*env)->DeleteGlobalRef(env,registered_client);registered_client=found;}}
 if(registered_client)client=registered_client;
 if(!client){__android_log_print(6,"CameraCallbackRepair","Live SDK client unavailable; no substitution");return;}
 jclass weak=(*env)->FindClass(env,"java/lang/ref/WeakReference");
 jmethodID ctor=(*env)->GetMethodID(env,weak,"<init>","(Ljava/lang/Object;)V");
 jobject replacement=(*env)->NewObject(env,weak,ctor,client);
 error=(*j)->SetLocalObject(j,thread,0,3,replacement);
 if(repairs++<8){jint identity=0,code=0;(*j)->GetObjectHashCode(j,client,&identity);(*j)->GetLocalInt(j,thread,0,4,&code);
 jfieldID cb=(*env)->GetFieldID(env,aps_client_class,"mBufferCallback","Lcom/oplus/ocs/camera/consumer/apsAdapter/APSClient$BufferCallback;");jobject callback=(*env)->GetObjectField(env,client,cb);
 __android_log_print(4,"CameraCallbackRepair","Supplied client=%d code=%d callbackPresent=%d registeredSame=%d SetLocalObject=%d",identity,code,callback!=0,registered_client&&(*env)->IsSameObject(env,registered_client,client),error); }
}
static void JNICALL prepared(jvmtiEnv*j,JNIEnv*env,jthread thread,jclass c){
 char*sig=0;(*j)->GetClassSignature(j,c,&sig,0);
 if(same(sig,"Lcom/n810/camera/lab/CameraMetadataTest;")&&!frame_helper)frame_helper=(*env)->NewGlobalRef(env,c);
 if(same(sig,"Lcom/oplus/ocs/camera/consumer/apsAdapter/APSClient;")){
 aps_client_class=(*env)->NewGlobalRef(env,c);
 jint count=0;jmethodID*methods=0;(*j)->GetClassMethods(j,c,&count,&methods);
 for(int i=0;i<count;i++){char*name=0;(*j)->GetMethodName(j,methods[i],&name,0,0);
 if(same(name,"setDataCallback")){data_callback_method=methods[i];int result=(*j)->SetBreakpoint(j,methods[i],0);__android_log_print(4,"CameraCallbackRepair","Data registration breakpoint=%d",result);}drop(j,name);}
 if(methods)(*j)->Deallocate(j,(unsigned char*)methods);
 }
 if(same(sig,"Lcom/oplus/ocs/camera/consumer/apsAdapter/APSClientCallback;")){
  jint count=0;jmethodID*methods=0;(*j)->GetClassMethods(j,c,&count,&methods);
  for(int i=0;i<count;i++){char*name=0;(*j)->GetMethodName(j,methods[i],&name,0,0);
   if(same(name,"onTransact")){aps_callback_method=methods[i];int result=(*j)->SetBreakpoint(j,methods[i],0);__android_log_print(4,"CameraCallbackRepair","Callback entry breakpoint=%d",result);}drop(j,name);}
  if(methods)(*j)->Deallocate(j,(unsigned char*)methods);
 }
 if(same(sig,"Lcom/oplus/ocs/camera/ipusdk/processunit/BaseProcessUnitSDK;")){
  jint count=0;jmethodID*methods=0;(*j)->GetClassMethods(j,c,&count,&methods);
  for(int i=0;i<count;i++){char*name=0;(*j)->GetMethodName(j,methods[i],&name,0,0);
   if(same(name,"createApsClient")){ipu_create_method=methods[i];int result=(*j)->SetBreakpoint(j,methods[i],0);__android_log_print(4,"CameraSharedClient","IPU initialization breakpoint=%d",result);}drop(j,name);}
  if(methods)(*j)->Deallocate(j,(unsigned char*)methods);
 }
 if(same(sig,"Lcom/oplus/ocs/camera/consumer/apsAdapter/adapter/CaptureImageCategory;")){
  jint count=0;jmethodID*methods=0;(*j)->GetClassMethods(j,c,&count,&methods);
  for(int i=0;i<count;i++){char*name=0;(*j)->GetMethodName(j,methods[i],&name,0,0);
   if(same(name,"isValid")){capture_valid_method=methods[i];int result=(*j)->SetBreakpoint(j,methods[i],0);__android_log_print(4,"CameraCaptureDiagnostic","Capture validity breakpoint=%d",result);}drop(j,name);}
  if(methods)(*j)->Deallocate(j,(unsigned char*)methods);
 }
 if(same(sig,"Lcom/oplus/ocs/camera/producer/device/Camera2Impl;")){
  jint count=0;jmethodID*methods=0;(*j)->GetClassMethods(j,c,&count,&methods);
  for(int i=0;i<count;i++){char*name=0;(*j)->GetMethodName(j,methods[i],&name,0,0);if(same(name,"takePicture")){public_photo_method=methods[i];int result=(*j)->SetBreakpoint(j,methods[i],0);__android_log_print(4,"CameraPublicPhoto","Capture entry breakpoint=%d",result);}drop(j,name);}if(methods)(*j)->Deallocate(j,(unsigned char*)methods);
 }
 if(same(sig,"Lcom/oplus/camera/control/ShutterButton;")||same(sig,"Lcom/oplus/ocs/camera/producer/device/Camera2Impl;")){
  jint count=0;jmethodID*methods=0;(*j)->GetClassMethods(j,c,&count,&methods);for(int i=0;i<count;i++){char*name=0,*desc=0;(*j)->GetMethodName(j,methods[i],&name,&desc,0);int target=0;
  if(same(name,"onTouchEvent")){shutter_touch_method=methods[i];target=1;}if(same(name,"performClick")){shutter_click_method=methods[i];target=1;}
  if(same(name,"setZoomRatio")&&same(desc,"(Lcom/oplus/ocs/camera/metadata/parameter/CaptureRequestBuilderProxy;FLandroid/graphics/Rect;Z)V")){zoom_method=methods[i];target=1;}
  if(target)(*j)->SetBreakpoint(j,methods[i],0);drop(j,name);drop(j,desc);}if(methods)(*j)->Deallocate(j,(unsigned char*)methods);
 }
 if(same(sig,"Lgc/r;")){
  jint count=0;jmethodID*methods=0;(*j)->GetClassMethods(j,c,&count,&methods);
  for(int i=0;i<count;i++){char*name=0;(*j)->GetMethodName(j,methods[i],&name,0,0);
   if(same(name,"onEvent")){composition_event_method=methods[i];int result=(*j)->SetBreakpoint(j,methods[i],0);__android_log_print(4,"CameraCaptionDiagnostic","Caption breakpoint=%d",result);}drop(j,name);}
  if(methods)(*j)->Deallocate(j,(unsigned char*)methods);
 }
 if(same(sig,"Lfc/d3;")||same(sig,"Lgc/t;")){
  jint count=0;jmethodID*methods=0;(*j)->GetClassMethods(j,c,&count,&methods);
  for(int i=0;i<count;i++){char*name=0;(*j)->GetMethodName(j,methods[i],&name,0,0);
   int target=same(sig,"Lfc/d3;")?(same(name,"m4")||same(name,"l4")||same(name,"j4")||same(name,"c3")):same(name,"f");
   if(target){int result=(*j)->SetBreakpoint(j,methods[i],0);__android_log_print(4,"CameraFeatureTrace","Trace breakpoint %s.%s result=%d",sig,name,result);}drop(j,name);
  }if(methods)(*j)->Deallocate(j,(unsigned char*)methods);
 }drop(j,sig);
}

extern void* dlopen(const char*,int);
extern void* dlsym(void*,const char*);
extern int dlclose(void*);
extern const char* dlerror(void);
typedef struct {const char*name;void*base;const char*symbol;void*address;} DlInfo;
extern int dladdr(const void*,DlInfo*);
extern int mprotect(void*,unsigned long,int);
extern int getpagesize(void);
extern int snprintf(char*,unsigned long,const char*,...);
extern char* strchr(const char*,int);
extern char* strstr(const char*,const char*);
static char engine_models[1024],engine_libs[1024];
static void* (*original_sphal_load)(const char*,int);
static int (*original_sphal_unload)(void*);
static jlong (*original_engine_create)(JNIEnv*,jclass,jstring);
static jlong (*original_composition_init)(JNIEnv*,jclass);
static void *engine_lib_handle,*tracker_lib_handle;
static int engine_imports_patched;
static jlong redirect_engine_create(JNIEnv*env,jclass type,jstring unused){
 char path[1200];snprintf(path,sizeof(path),"%s/composition_lite2_config.yaml",engine_models);
 jstring config=(*env)->NewStringUTF(env,path);
 jlong result=original_engine_create(env,type,config);(*env)->DeleteLocalRef(env,config);
 __android_log_print(4,"CameraDonorEngine","Donor engine create returned nonzero=%d",result!=0);return result;
}
static void* redirect_symbols(void*handle,const char*name){
 void*result=dlsym(handle,name);
 if(same(name,"Java_com_cv_ai_1camera_1composition_1lite2_AICameraCompositionLite2_nativeCreate")&&result){original_engine_create=result;return (void*)redirect_engine_create;}
 return result;
}
static void* redirect_sphal_load(const char*name,int flags){
 if(same(name,"libai_camera_composition_lite2.so")||same(name,"libAITracker.so")){
  char path[1200];snprintf(path,sizeof(path),"%s/%s",engine_libs,name);void*h=dlopen(path,flags);
  __android_log_print(h?4:6,"CameraDonorEngine","Private donor library %s loaded=%d error=%s",name,h!=0,h?"none":dlerror());
  if(same(name,"libAITracker.so"))tracker_lib_handle=h;else engine_lib_handle=h;return h;
 }
 return original_sphal_load(name,flags);
}
static int redirect_sphal_unload(void*h){if(h&&(h==engine_lib_handle||h==tracker_lib_handle))return dlclose(h);return original_sphal_unload(h);}
static int patch_engine_imports(void){
 if(engine_imports_patched)return 1;
 DlInfo info={0};if(!dladdr((void*)original_composition_init,&info)||!info.name||!strstr(info.name,"libApsAICompositionJni.so"))return 0;
 unsigned char*base=info.base;void**load=(void**)(base+0x57968);void**symbols=(void**)(base+0x57970);void**unload=(void**)(base+0x57988);
 DlInfo check={0};if(!dladdr(*load,&check)||!check.name||!strstr(check.name,"libvndksupport")){__android_log_print(6,"CameraDonorEngine","Unexpected adapter import layout; leaving untouched");return 0;}
 int page=getpagesize();void*start=(void*)((unsigned long)load&~((unsigned long)page-1));
 if(mprotect(start,page,3)){__android_log_print(6,"CameraDonorEngine","Cannot write private import page");return 0;}
 original_sphal_load=*load;original_sphal_unload=*unload;
 __atomic_store_n(load,(void*)redirect_sphal_load,__ATOMIC_RELEASE);__atomic_store_n(symbols,(void*)redirect_symbols,__ATOMIC_RELEASE);__atomic_store_n(unload,(void*)redirect_sphal_unload,__ATOMIC_RELEASE);
 int protection=mprotect(start,page,1);engine_imports_patched=1;
 __android_log_print(4,"CameraDonorEngine","Redirected three adapter imports in this process; restored read protection=%d",protection==0);return 1;
}
static jlong redirect_composition_init(JNIEnv*env,jclass type){
 if(engine_models[0]&&engine_libs[0])patch_engine_imports();
 jlong result=original_composition_init(env,type);__android_log_print(4,"CameraDonorEngine","OEM composition adapter handle nonzero=%d",result!=0);return result;
}
static void JNICALL native_bind(jvmtiEnv*j,JNIEnv*env,jthread thread,jmethodID method,void*address,void**replacement){
 char*name=0;(*j)->GetMethodName(j,method,&name,0,0);
 if(same(name,"nativeInitComposition")){
  jclass type;char*sig=0;(*j)->GetMethodDeclaringClass(j,method,&type);(*j)->GetClassSignature(j,type,&sig,0);
  if(same(sig,"Lcom/oplus/camera/aicomposition/OplusAIComposition;")&&engine_models[0]){original_composition_init=address;*replacement=(void*)redirect_composition_init;__android_log_print(4,"CameraDonorEngine","OEM composition initialization hook registered");}
  drop(j,sig);
 }drop(j,name);
}
static void engine_options(char*options){
 if(!options)return;const char*separator=strchr(options,'|');if(!separator)return;
 unsigned long length=separator-options;if(length>=sizeof(engine_models))return;
 for(unsigned long i=0;i<length;i++)engine_models[i]=options[i];engine_models[length]=0;
 snprintf(engine_libs,sizeof(engine_libs),"%s",separator+1);
}

JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM*vm,char*options,void*reserved){
 engine_options(options);jvmtiEnv*j=0;int r=(*vm)->GetEnv(vm,(void**)&j,JVMTI_VERSION_1_2);
 if(r){__android_log_print(6,"CameraExceptionTrace","GetEnv failed=%d",r);return r;}
 jvmtiCapabilities cap={0};cap.can_force_early_return=1;cap.can_tag_objects=1;cap.can_generate_exception_events=1;cap.can_generate_breakpoint_events=1;cap.can_access_local_variables=1;cap.can_generate_native_method_bind_events=1;r=(*j)->AddCapabilities(j,&cap);
 if(r){__android_log_print(6,"CameraExceptionTrace","capability failed=%d",r);return -1;}
 jvmtiEventCallbacks cb={0};cb.Exception=exception_event;cb.ClassPrepare=prepared;cb.Breakpoint=breakpoint_event;cb.NativeMethodBind=native_bind;r=(*j)->SetEventCallbacks(j,&cb,sizeof(cb));
 /* Exception-wide tracing disabled after callback diagnosis. */
 if(!r)r=(*j)->SetEventNotificationMode(j,JVMTI_ENABLE,JVMTI_EVENT_CLASS_PREPARE,0);if(!r)r=(*j)->SetEventNotificationMode(j,JVMTI_ENABLE,JVMTI_EVENT_BREAKPOINT,0);__android_log_print(4,"CameraCallbackRepair","Diagnostic callback repair attached result=%d",r);if(!r)r=(*j)->SetEventNotificationMode(j,JVMTI_ENABLE,JVMTI_EVENT_NATIVE_METHOD_BIND,0);
 if(!r){JNIEnv*env=0;(*vm)->GetEnv(vm,(void**)&env,JNI_VERSION_1_6);jint count=0;jclass*classes=0;(*j)->GetLoadedClasses(j,&count,&classes);for(int i=0;i<count;i++){jint status=0;(*j)->GetClassStatus(j,classes[i],&status);if(status&JVMTI_CLASS_STATUS_PREPARED)prepared(j,env,0,classes[i]);(*env)->DeleteLocalRef(env,classes[i]);}if(classes)(*j)->Deallocate(j,(unsigned char*)classes);}
 return r? -1:0;
}
