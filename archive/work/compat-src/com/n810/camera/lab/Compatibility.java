package com.n810.camera.lab;

import android.content.Context;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Field;
import java.util.Map;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;

public final class Compatibility {
    private Compatibility() {}
    private static android.media.ImageReader publicPreviewReader;
    private static android.os.HandlerThread previewDrainThread;
    private static boolean receivedPublicPreview;
    private static android.os.Messenger authorizationCallback;

    private static void requestCameraAuthorization(Context context) {
        try {
            authorizationCallback = new android.os.Messenger(new Handler(Looper.getMainLooper(), message -> {
                android.os.Bundle result = message.getData();
                Log.i("CameraLabAuth", "Public Camera authorization result=" + result.getInt("result_code", -1)
                    + ", status=" + result.getString("auth_result"));
                return true;
            }));
            android.os.Bundle request = new android.os.Bundle();
            request.putBinder("callback", authorizationCallback.getBinder());
            context.getContentResolver().call(android.net.Uri.parse("content://com.oplus.ocs.out.OpenCapabilityThirdProvider"),
                "auth", "CAMERA_CLIENT", request);
            Log.i("CameraLabAuth", "Requested public Camera authorization using the copy's own UID");
        } catch (RuntimeException e) { Log.e("CameraLabAuth", "Public Camera authorization unavailable", e); }
    }

    public static synchronized void preparePublicPreview(Object camera) {
        try {
            Class<?> util = Class.forName("com.oplus.ocs.camera.common.util.Util");
            if ((Boolean) util.getMethod("isSystemCamera").invoke(null)) return;
            Field mapField = camera.getClass().getDeclaredField("t");
            mapField.setAccessible(true);
            Map<String, android.view.Surface> surfaces = (Map<String, android.view.Surface>) mapField.get(camera);
            if (!surfaces.isEmpty()) return;
            Field idField = camera.getClass().getDeclaredField("v");
            idField.setAccessible(true);
            String type = (String) Class.forName("kb.p").getMethod("c", int.class).invoke(null, idField.getInt(camera));
            if (publicPreviewReader == null) {
                publicPreviewReader = android.media.ImageReader.newInstance(1920, 1440, android.graphics.PixelFormat.RGBA_8888, 3);
                previewDrainThread = new android.os.HandlerThread("CameraLabPreviewDrain");
                previewDrainThread.start();
                publicPreviewReader.setOnImageAvailableListener(reader -> {
                    try (android.media.Image image = reader.acquireLatestImage()) {
                        if (image != null && !receivedPublicPreview) {
                            receivedPublicPreview = true;
                            Log.i("CameraLabPreview", "Received first SDK output frame");
                        }
                    } catch (RuntimeException e) { Log.w("CameraLabPreview", "Preview output drain failed", e); }
                }, new Handler(previewDrainThread.getLooper()));
            }
            surfaces.put(type, publicPreviewReader.getSurface());
            Log.i("CameraLabCompat", "Provided required external SDK preview surface for " + type);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.e("CameraLabCompat", "Public SDK preview preparation failed", e);
        }
    }

    public static void startGallery(Object helper, android.content.Intent original, boolean secure, android.net.Uri uri) {
        android.app.Activity activity = null;
        try {
            for (Field field : helper.getClass().getDeclaredFields()) {
                if (android.app.Activity.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    activity = (android.app.Activity) field.get(helper);
                    break;
                }
            }
            if (activity == null) throw new IllegalStateException("Gallery activity unavailable");
            if (original.resolveActivity(activity.getPackageManager()) != null) {
                java.lang.reflect.Method launch = helper.getClass().getDeclaredMethod("q$labOriginal", android.content.Intent.class, boolean.class, android.net.Uri.class);
                launch.setAccessible(true);
                try { launch.invoke(helper, original, secure, uri); return; }
                catch (java.lang.reflect.InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (!(cause instanceof SecurityException) && !(cause instanceof android.content.ActivityNotFoundException)) {
                        if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                        throw new IllegalStateException(cause);
                    }
                    Log.w("CameraLabCompat", "Original Gallery launch unavailable; trying public viewer", cause);
                }
            }
            android.app.KeyguardManager keyguard = (android.app.KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
            if (secure || keyguard == null || keyguard.isDeviceLocked() || keyguard.isKeyguardLocked()) {
                Log.w("CameraLabCompat", "Public Gallery fallback withheld for secure camera entry");
                return;
            }
            android.content.Intent view;
            if (uri != null) {
                String type = original.getType();
                if (type == null) type = activity.getContentResolver().getType(uri);
                view = new android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, type == null ? "image/*" : type)
                    .setPackage("com.coloros.gallery3d").addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else view = activity.getPackageManager().getLaunchIntentForPackage("com.coloros.gallery3d");
            if (view == null) throw new android.content.ActivityNotFoundException("Gallery launcher unavailable");
            activity.startActivity(view);
            Log.i("CameraLabCompat", "Opened Gallery through its public activity");
        } catch (ReflectiveOperationException | SecurityException | android.content.ActivityNotFoundException e) {
            Log.e("CameraLabCompat", "Gallery launch failed", e);
            if (activity != null) android.widget.Toast.makeText(activity, "Unable to open Gallery.", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean tryBindService(Context context, android.content.Intent intent, int flags, java.util.concurrent.Executor executor, android.content.ServiceConnection connection) {
        try { return context.bindService(intent, flags, executor, connection); }
        catch (SecurityException e) {
            Log.w("CameraLabCompat", "Service binding denied; reporting failure to the existing client", e);
            return false;
        }
    }

    public static void beforeFeatures(Object adapter) {
        try {
            Field configField = adapter.getClass().getDeclaredField("mFeatureConfigure");
            configField.setAccessible(true);
            Object config = configField.get(adapter);
            Field modeField = config.getClass().getDeclaredField("mModeName");
            modeField.setAccessible(true);
            String mode = (String) modeField.get(config);
            if (!"photo_mode".equals(mode)) return;
            Field tableField = config.getClass().getDeclaredField("mFeatureTable");
            tableField.setAccessible(true);
            Map<Object, Object> original = (Map<Object, Object>) tableField.get(config);
            String name = "com.oplus.camera.feature.ai_composition";
            for (Object info : original.values()) {
                if (name.equals(info.getClass().getMethod("getFeatureName").invoke(info))) {
                    Log.i("CameraLabCompat", "Composition feature already registered in photo mode");
                    return;
                }
            }
            Class<?> keyType = Class.forName("com.oplus.ocs.camera.configure.CameraFeatureKey");
            Class<?> keys = Class.forName("com.oplus.ocs.camera.configure.CameraFeatureKeyContainer");
            java.lang.reflect.Method initKey = keys.getDeclaredMethod("initFeatureKey", String.class, String.class);
            initKey.setAccessible(true);
            Object key = initKey.invoke(null, name, "String");
            Class<?> infoType = Class.forName("com.oplus.ocs.camera.configure.ProtobufFeatureInfoImpl");
            Object info = infoType.getConstructor(String.class, String.class, String.class, String.class, String.class, boolean.class, keyType)
                .newInstance(name, "off,on", "off", "", "main_menu", false, key);
            Map<Object, Object> updated = new java.util.LinkedHashMap<>(original);
            updated.put(key, info);
            tableField.set(config, updated);
            Log.i("CameraLabCompat", "Registered original composition presenter for photo mode main-menu entry");
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.e("CameraLabCompat", "Composition registration failed", e);
        }
    }

    public static String configOverride(String key) {
        if ("com.oplus.feature.aicomposition.realscene.support".equals(key) || "com.oplus.feature.aicomposition.inspiration.support".equals(key)) return "1";
        if ("com.oplus.capture.with.raw10".equals(key) || "com.oplus.capture.raw10.use.mipi".equals(key)) return "0";
        return null;
    }

    public static void beforePreview() {
        try {
            Class<?> helper = Class.forName("com.oplus.ocs.camera.producer.info.CameraConfigHelper");
            java.lang.reflect.Method ready = helper.getDeclaredMethod("checkVendorTagReady");
            ready.setAccessible(true);
            ready.invoke(null);
            Field field = helper.getDeclaredField("sVendorTagMap");
            field.setAccessible(true);
            Map<String, String> original = (Map<String, String>) field.get(null);
            Map<String, String> values = new java.util.HashMap<>();
            if (original != null) values.putAll(original);
            values.put("com.oplus.capture.with.raw10", "0");
            values.put("com.oplus.capture.raw10.use.mipi", "0");
            values.put("com.oplus.capture.surface.decision.support", "0");
            field.set(null, values);
            Field casesField = helper.getDeclaredField("sUsecaseMap");
            casesField.setAccessible(true);
            Map<String, Object> cases = (Map<String, Object>) casesField.get(null);
            if (cases != null && cases.containsKey("simple_photo_case")) {
                Map<String, Object> adapted = new java.util.LinkedHashMap<>(cases);
                java.util.LinkedList<android.util.Pair<String, String>> compatible = new java.util.LinkedList<>();
                for (android.util.Pair<String, String> entry : (java.util.LinkedList<android.util.Pair<String, String>>) cases.get("simple_photo_case")) {
                    // For rear_sat, raw_output and raw16_output now resolve to the same RAW_SENSOR reader.
                    if (!"raw_output".equals(entry.first)) compatible.add(entry);
                }
                for (String missing : new String[]{"sat_photo_none_yuv_full_size_case", "sat_photo_full_size_case"}) {
                    if (!adapted.containsKey(missing)) adapted.put(missing, compatible);
                }
                casesField.set(null, adapted);
            }
            Log.i("CameraLabCompat", "Selected existing RAW_SENSOR path in the test process");
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.e("CameraLabCompat", "Unable to select supported raw format", e);
        }
    }

    public static void initialize(Context context) {
        if (!"com.n810.camera.aicomposition.lab".equals(context.getPackageName())) {
            throw new IllegalStateException("Compatibility initialization outside test copy");
        }
        try {
            Class<?> appLog = Class.forName("jb.a", true, context.getClassLoader());
            Field verbose = appLog.getDeclaredField("b");
            verbose.setAccessible(true);
            verbose.setBoolean(null, false);
            Class<?> sdkLog = Class.forName("com.oplus.ocs.camera.common.util.CameraUnitLog", true, context.getClassLoader());
            sdkLog.getMethod("initLog", boolean.class, boolean.class, boolean.class).invoke(null, false, false, false);
            Class<?> util = Class.forName("com.oplus.ocs.camera.common.util.Util", true, context.getClassLoader());
            Field mode = util.getDeclaredField("mbSystemCamera");
            mode.setAccessible(true);
            boolean privileged = context.checkSelfPermission("com.oplus.permission.safe.CAMERA") == android.content.pm.PackageManager.PERMISSION_GRANTED;
            mode.set(null, privileged);
            Log.i("CameraLabCompat", "Selected SDK pipeline for actual camera privilege: " + privileged);
            new Handler(Looper.getMainLooper()).postDelayed(() -> new Thread(() -> LiveGuideProbe.run(context), "CameraLabLiveGuide").start(), 2000);
            new Handler(Looper.getMainLooper()).postDelayed(Compatibility::inspectThreads, 7000);
            new Handler(Looper.getMainLooper()).postDelayed(() -> inspectFormats(context), 9000);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.e("CameraLabCompat", "Unable to select OEM preview pipeline", e);
        }
    }

    private static void inspectFormats(Context context) {
        try (PrintWriter out = new PrintWriter(new File(context.getFilesDir(), "CameraLabDiagnostics.txt"))) {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                out.println("camera=" + id + " capabilities=" + Arrays.toString(c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)));
                if (map != null) for (int format : map.getOutputFormats()) {
                    out.println("format=" + format + " sizes=" + Arrays.toString(map.getOutputSizes(format)));
                }
            }
        } catch (Exception e) { Log.e("CameraLabCompat", "Camera capability inspection failed", e); }
    }

    private static void inspectThreads() {
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            String name = thread.getName();
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (!(lower.contains("camera") || lower.contains("aps") || lower.contains("preview") || lower.contains("producer") || lower.contains("consumer"))) continue;
            StringBuilder result = new StringBuilder(name).append(" ").append(thread.getState());
            for (StackTraceElement frame : entry.getValue()) result.append(" | ").append(frame);
            Log.w("CameraLabThreads", result.toString());
        }
    }
}
