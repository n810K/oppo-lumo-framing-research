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
JNIEXPORT jint JNICALL Agent_OnAttach(JavaVM*vm,char*options,void*reserved){
 jvmtiEnv*j=0;int r=(*vm)->GetEnv(vm,(void**)&j,JVMTI_VERSION_1_2);
 if(r){__android_log_print(6,"CameraExceptionTrace","GetEnv failed=%d",r);return r;}
 jvmtiCapabilities cap={0};cap.can_generate_exception_events=1;r=(*j)->AddCapabilities(j,&cap);
 if(r){__android_log_print(6,"CameraExceptionTrace","capability failed=%d",r);return -1;}
 jvmtiEventCallbacks cb={0};cb.Exception=exception_event;r=(*j)->SetEventCallbacks(j,&cb,sizeof(cb));
 if(!r)r=(*j)->SetEventNotificationMode(j,JVMTI_ENABLE,JVMTI_EVENT_EXCEPTION,0);
 __android_log_print(4,"CameraExceptionTrace","Exception-only diagnostic attached result=%d",r);return r? -1:0;
}
