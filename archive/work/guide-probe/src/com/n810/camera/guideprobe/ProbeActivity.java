package com.n810.camera.guideprobe;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.ScrollView;
import com.cv.ai_camera_composition_lite2.AICameraCompositionLite2;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import org.json.JSONObject;

public final class ProbeActivity extends Activity {
    private static final String TAG = "N810GuideProbe";
    private TextView text;
    private final StringBuilder transcript = new StringBuilder();
    private final JSONObject result = new JSONObject();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        text = new TextView(this);
        text.setTextSize(17);
        text.setPadding(32, 100, 32, 64);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        setContentView(scroll);
        String mode = getIntent().getStringExtra("mode");
        boolean infer = "infer".equals(mode);
        boolean initialize = "initialize".equals(mode) || infer;
        note("Live guide engine test\n\nNo camera, microphone or network permissions.\nThe original Camera is not used by this test.\n");
        new Thread(() -> runProbe(initialize, infer), "GuideEngineProbe").start();
    }

    private synchronized void note(String message) {
        Log.i(TAG, message);
        transcript.append(message).append('\n');
        String snapshot = transcript.toString();
        runOnUiThread(() -> text.setText(snapshot));
        try (FileOutputStream output = openFileOutput("probe.txt", MODE_PRIVATE)) {
            output.write(snapshot.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { Log.e(TAG, "Cannot save transcript", e); }
    }

    private void record(String key, Object value) {
        try {
            result.put(key, value);
            try (FileOutputStream output = openFileOutput("result.json", MODE_PRIVATE)) {
                output.write(result.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) { Log.e(TAG, "Cannot save result", e); }
    }

    private ByteBuffer syntheticImage(int width, int height) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = 110, g = 170, b = 225;
                if (y > height * 2 / 3) { r = 70; g = 130; b = 65; }
                if (x > 200 && x < 450 && y > 480 && y < 780) { r = 190; g = 110; b = 65; }
                if (x > 275 && x < 345 && y > 630 && y < 780) { r = 65; g = 45; b = 30; }
                if (x > 590 && x < 620 && y > 380 && y < 800) { r = 90; g = 65; b = 35; }
                if ((x-605)*(x-605) + (y-360)*(y-360) < 120*120) { r = 35; g = 105; b = 45; }
                pixels.put((byte)r).put((byte)g).put((byte)b).put((byte)255);
            }
        }
        pixels.rewind();
        return pixels;
    }

    private void runProbe(boolean initialize, boolean infer) {
        long handle = 0;
        record("mode", infer ? "infer" : initialize ? "initialize" : "load");
        record("pid", android.os.Process.myPid());
        record("completed", false);
        try {
            System.loadLibrary("AITracker");
            record("tracker_library_loaded", true);
            note("Tracker library loaded. Tracker initialization is not tested here.");
            System.loadLibrary("ai_camera_composition_lite2");
            record("composition_library_loaded", true);
            note("Composition engine library loaded.");
            if (initialize) {
                File config = new File(getFilesDir(), "models/composition_lite2_config.yaml");
                if (!config.isFile()) throw new IllegalStateException("Model configuration has not been staged.");
                note("Creating engine with private model files...");
                record("stage", "nativeCreate");
                handle = AICameraCompositionLite2.nativeCreate(config.getAbsolutePath());
                record("engine_created", handle != 0);
                note("Engine created: " + (handle != 0));
                if (handle != 0) {
                    record("stage", "nativeInit");
                    boolean initialized = AICameraCompositionLite2.nativeInit(handle);
                    record("engine_initialized", initialized);
                    note("Engine initialized: " + initialized);
                    if (initialized && infer) {
                        record("input", "generated 768x1024 RGBA test image; no camera or personal photo");
                        note("Running one generated test image. This is not a camera frame.");
                        ByteBuffer pixels = syntheticImage(768, 1024);
                        record("stage", "nativeRun");
                        long start = android.os.SystemClock.elapsedRealtime();
                        String output = AICameraCompositionLite2.nativeRun(handle, pixels, 768, 1024, 768 * 4, true);
                        record("inference_elapsed_ms", android.os.SystemClock.elapsedRealtime() - start);
                        record("inference_output", output == null ? JSONObject.NULL : output);
                        note("Inference returned: " + output);
                    }
                }
            }
        } catch (Throwable failure) {
            record("error", failure.toString());
            note("Test failed: " + failure);
            Log.e(TAG, "Probe failure", failure);
        } finally {
            if (handle != 0) {
                record("stage", "nativeDestroy");
                AICameraCompositionLite2.nativeDestroy(handle);
                note("Engine released.");
            }
            record("completed", true);
            note(infer ? "Test finished. No photo was captured. Model success requires checking the native logs and output." : "Test finished. No inference or photo capture was performed.");
        }
    }
}
