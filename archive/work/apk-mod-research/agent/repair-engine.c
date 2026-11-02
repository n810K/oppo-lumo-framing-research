#include "jvmti.h"
extern int __android_log_print(int,const char*,const char*,...);
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

static jclass aps_client_class=0;
static int repairs=0;
static void JNICALL breakpoint_event(jvmtiEnv*j,JNIEnv*env,jthread thread,jmethodID method,jlocation location){
 jobject ref=0;jvmtiError error=(*j)->GetLocalObject(j,thread,0,3,&ref);
 if(error||ref||!aps_client_class)return;
 jmethodID get=(*env)->GetStaticMethodID(env,aps_client_class,"getClient","()Lcom/oplus/ocs/camera/consumer/apsAdapter/APSClient;");
 jobject client=get?(*env)->CallStaticObjectMethod(env,aps_client_class,get):0;
 if(!client){__android_log_print(6,"CameraCallbackRepair","Live SDK client unavailable; no substitution");return;}
 jclass weak=(*env)->FindClass(env,"java/lang/ref/WeakReference");
 jmethodID ctor=(*env)->GetMethodID(env,weak,"<init>","(Ljava/lang/Object;)V");
 jobject replacement=(*env)->NewObject(env,weak,ctor,client);
 error=(*j)->SetLocalObject(j,thread,0,3,replacement);
 if(repairs++<8)__android_log_print(4,"CameraCallbackRepair","Supplied live SDK client reference; SetLocalObject=%d",error);
}
static void JNICALL prepared(jvmtiEnv*j,JNIEnv*env,jthread thread,jclass c){
 char*sig=0;(*j)->GetClassSignature(j,c,&sig,0);
 if(same(sig,"Lcom/oplus/ocs/camera/consumer/apsAdapter/APSClient;"))aps_client_class=(*env)->NewGlobalRef(env,c);
 if(same(sig,"Lcom/oplus/ocs/camera/consumer/apsAdapter/APSClientCallback;")){
  jint count=0;jmethodID*methods=0;(*j)->GetClassMethods(j,c,&count,&methods);
  for(int i=0;i<count;i++){char*name=0;(*j)->GetMethodName(j,methods[i],&name,0,0);
   if(same(name,"onTransact")){int result=(*j)->SetBreakpoint(j,methods[i],0);__android_log_print(4,"CameraCallbackRepair","Callback entry breakpoint=%d",result);}drop(j,name);}
  if(methods)(*j)->Deallocate(j,(unsigned char*)methods);
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
 jvmtiCapabilities cap={0};cap.can_generate_exception_events=1;cap.can_generate_breakpoint_events=1;cap.can_access_local_variables=1;cap.can_generate_native_method_bind_events=1;r=(*j)->AddCapabilities(j,&cap);
 if(r){__android_log_print(6,"CameraExceptionTrace","capability failed=%d",r);return -1;}
 jvmtiEventCallbacks cb={0};cb.Exception=exception_event;cb.ClassPrepare=prepared;cb.Breakpoint=breakpoint_event;cb.NativeMethodBind=native_bind;r=(*j)->SetEventCallbacks(j,&cb,sizeof(cb));
 if(!r)r=(*j)->SetEventNotificationMode(j,JVMTI_ENABLE,JVMTI_EVENT_EXCEPTION,0);
 if(!r)r=(*j)->SetEventNotificationMode(j,JVMTI_ENABLE,JVMTI_EVENT_CLASS_PREPARE,0);if(!r)r=(*j)->SetEventNotificationMode(j,JVMTI_ENABLE,JVMTI_EVENT_BREAKPOINT,0);__android_log_print(4,"CameraCallbackRepair","Diagnostic callback repair attached result=%d",r);if(!r)r=(*j)->SetEventNotificationMode(j,JVMTI_ENABLE,JVMTI_EVENT_NATIVE_METHOD_BIND,0);return r? -1:0;
}
