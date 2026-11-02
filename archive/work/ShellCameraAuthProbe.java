import android.content.*;
import android.os.*;

public final class ShellCameraAuthProbe {
    static Context context;
    static ServiceConnection connection;
    static Handler handler;
    static boolean completed;
    static Object activityManager;
    static Object shellConnection;
    static void finish(String result) {
        if (completed) return;
        completed = true;
        System.out.println(result);
        handler.postDelayed(() -> {
            try {
                Class.forName("android.app.IActivityManager").getMethod("unbindService", Class.forName("android.app.IServiceConnection"))
                    .invoke(activityManager, shellConnection);
            } catch (Exception ignored) {}
            System.exit(0);
        }, 100);
    }
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        handler = new Handler(Looper.getMainLooper());
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Context system = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        context = system.createPackageContext("com.android.shell", 0);
        System.out.println("Actual uid=" + android.os.Process.myUid() + " package=" + context.getPackageName());
        for (java.lang.reflect.Method method : Class.forName("android.app.IActivityManager").getMethods()) {
            if (method.getName().startsWith("bindService")) System.out.println("Shell binding API: " + method);
        }
        final Binder callback = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == INTERFACE_TRANSACTION) { reply.writeString("com.coloros.ocs.base.IAuthenticationListener"); return true; }
                if (code == 1 || code == 2) {
                    data.enforceInterface("com.coloros.ocs.base.IAuthenticationListener");
                    String result = code == 2 ? "Authorization rejected, code=" + data.readInt() : "Authorization success callback received";
                    reply.writeNoException();
                    handler.post(() -> finish(result));
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            }
        };
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                Parcel request = Parcel.obtain(), reply = Parcel.obtain();
                try {
                    System.out.println("Connected to " + service.getInterfaceDescriptor());
                    request.writeInterfaceToken("com.coloros.ocs.base.IServiceBroker");
                    request.writeString("CAMERA_CLIENT");
                    request.writeString("1.0.11");
                    request.writeStrongBinder(callback);
                    if (!service.transact(1, request, reply, 0)) throw new IllegalStateException("Unsupported transaction");
                    reply.readException();
                } catch (Exception e) { finish("Request failed: " + e); }
                finally { request.recycle(); reply.recycle(); }
            }
            @Override public void onServiceDisconnected(ComponentName name) { finish("Service disconnected"); }
        };
        Intent intent = new Intent("com.coloros.opencapabilityservice")
            .setComponent(new ComponentName("com.coloros.ocs.opencapabilityservice", "com.coloros.ocs.opencapabilityservice.service.ColorOcsService"))
            .setType("camera_client_probe/shell");
        try {
            Binder connectionBinder = new Binder() {
                @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                    if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString("android.app.IServiceConnection"); return true; }
                    if (code == 1) {
                        data.enforceInterface("android.app.IServiceConnection");
                        ComponentName name = data.readInt() != 0 ? ComponentName.CREATOR.createFromParcel(data) : null;
                        IBinder service = data.readStrongBinder();
                        boolean dead = data.readInt() != 0;
                        if (dead || service == null) handler.post(() -> finish("Bound service unavailable"));
                        else handler.post(() -> connection.onServiceConnected(name, service));
                        return true;
                    }
                    return super.onTransact(code, data, reply, flags);
                }
            };
            shellConnection = Class.forName("android.app.IServiceConnection$Stub").getMethod("asInterface", IBinder.class).invoke(null, connectionBinder);
            activityManager = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
            java.lang.reflect.Method bind = Class.forName("android.app.IActivityManager").getMethod("bindService",
                Class.forName("android.app.IApplicationThread"), IBinder.class, Intent.class, String.class,
                Class.forName("android.app.IServiceConnection"), long.class, String.class, int.class);
            int result = (Integer) bind.invoke(activityManager, null, null, intent, intent.getType(), shellConnection,
                (long) Context.BIND_AUTO_CREATE, "com.android.shell", 0);
            System.out.println("Shell bind result=" + result);
            if (result <= 0) finish("Shell binding rejected");
        } catch (Exception e) { finish("Shell binding failed: " + e + " cause=" + e.getCause()); }
        handler.postDelayed(() -> finish("Authorization callback timed out"), 10000);
        Looper.loop();
    }
}
