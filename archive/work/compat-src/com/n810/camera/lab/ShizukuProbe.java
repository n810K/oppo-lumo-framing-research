package com.n810.camera.lab;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Log;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;

public final class ShizukuProbe {
    private static Context context;
    private static ServiceConnection connection;
    private static boolean requested;
    private static final Handler handler = new Handler(Looper.getMainLooper());

    public static void start(Context value) {
        if (!"com.n810.camera.aicomposition.lab".equals(android.app.Application.getProcessName())) return;
        context = value;
        Shizuku.addRequestPermissionResultListener((code, result) -> {
            if (code == 810 && result == PackageManager.PERMISSION_GRANTED) bindBroker();
            else Log.i("CameraLabShizuku", "Shizuku permission result=" + result);
        });
        Shizuku.addBinderReceivedListenerSticky(() -> {
            Log.i("CameraLabShizuku", "Connected to Shizuku; remote UID=" + Shizuku.getUid());
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindBroker();
            else Shizuku.requestPermission(810);
        }, handler);
    }

    private static void finish(String result) {
        Log.i("CameraLabShizuku", result);
        handler.postDelayed(() -> {
            try { context.unbindService(connection); } catch (RuntimeException ignored) {}
        }, 100);
    }

    private static void bindBroker() {
        if (requested) return;
        requested = true;
        final Binder callback = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == INTERFACE_TRANSACTION) { reply.writeString("com.coloros.ocs.base.IAuthenticationListener"); return true; }
                if (code == 1 || code == 2) {
                    data.enforceInterface("com.coloros.ocs.base.IAuthenticationListener");
                    if (code == 2) finish("Shell Camera authorization rejected, code=" + data.readInt());
                    else {
                        try {
                            if (data.readInt() == 0) throw new IllegalStateException("Empty capability result");
                            Class<?> type = Class.forName("com.oplus.ocs.base.common.CapabilityInfo");
                            Parcelable.Creator<?> creator = (Parcelable.Creator<?>) type.getField("CREATOR").get(null);
                            Object capability = creator.createFromParcel(data);
                            Object auth = type.getMethod("getAuthResult").invoke(capability);
                            Object result = auth.getClass().getMethod("getErrrorCode").invoke(auth);
                            finish("Shell Camera authorization callback code=" + result);
                        } catch (Exception e) { Log.e("CameraLabShizuku", "Cannot decode authorization callback", e); }
                    }
                    reply.writeNoException();
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            }
        };
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                Parcel request = Parcel.obtain(), reply = Parcel.obtain();
                try {
                    IBinder remote = new ShizukuBinderWrapper(service);
                    request.writeInterfaceToken("com.coloros.ocs.base.IServiceBroker");
                    request.writeString("CAMERA_CLIENT");
                    request.writeString("1.0.11");
                    request.writeStrongBinder(callback);
                    if (!remote.transact(1, request, reply, 0)) throw new IllegalStateException("Unsupported transaction");
                    reply.readException();
                    Log.i("CameraLabShizuku", "Authorization request forwarded through Shizuku as UID=" + Shizuku.getUid());
                } catch (Exception e) { Log.e("CameraLabShizuku", "Shell authorization request failed", e); }
                finally { request.recycle(); reply.recycle(); }
            }
            @Override public void onServiceDisconnected(ComponentName name) { finish("Authorization service disconnected"); }
        };
        Intent intent = new Intent("com.coloros.opencapabilityservice")
            .setComponent(new ComponentName("com.coloros.ocs.opencapabilityservice", "com.coloros.ocs.opencapabilityservice.service.ColorOcsService"))
            .setType("camera_client_probe/shizuku");
        try {
            if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) finish("Authorization service binding returned false");
        } catch (RuntimeException e) { Log.e("CameraLabShizuku", "Authorization service binding failed", e); }
    }
}
