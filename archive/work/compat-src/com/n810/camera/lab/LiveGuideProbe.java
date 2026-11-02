package com.n810.camera.lab;

import android.content.Context;
import android.util.Log;

public final class LiveGuideProbe {
    public static void run(Context context) {
        Object engine = null;
        Class<?> type = null;
        try {
            type = Class.forName("com.oplus.camera.aicomposition.OplusAIComposition");
            engine = type.getConstructor().newInstance();
            type.getMethod("initComposition").invoke(engine);
            java.lang.reflect.Field composition = type.getDeclaredField("mCompositionHandle");
            composition.setAccessible(true);
            Log.i("CameraLabLiveGuide", "Composition engine initialized=" + (composition.getLong(engine) != 0));
            type.getMethod("initTracker", String.class).invoke(engine, context.getFilesDir().getAbsolutePath());
            java.lang.reflect.Field tracker = type.getDeclaredField("mTrackerHandle");
            tracker.setAccessible(true);
            Log.i("CameraLabLiveGuide", "Motion tracker initialized=" + (tracker.getLong(engine) != 0));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            Log.e("CameraLabLiveGuide", "Live guide engine probe failed", e);
        } finally {
            if (engine != null && type != null) {
                try { type.getMethod("release").invoke(engine); }
                catch (ReflectiveOperationException | RuntimeException e) { Log.w("CameraLabLiveGuide", "Engine release failed", e); }
            }
        }
    }
}
