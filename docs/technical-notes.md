# LUMO Framing technical notes

Research conducted November 1–2, 2026. One CPH2791 running ColorOS 17. The original Camera inspected was version 7.006.125.

The feature was more than a hidden icon. A separate modified copy of OPPO Camera eventually sent a live preview frame into the real composition engine, received a proposed crop and scene description, displayed the original dot-and-ring guidance, and applied a zoom ratio that could be checked independently in camera-service metadata. This happened without root or an unlocked bootloader.

It still did not make a camera I wanted to use. The stock still-photo pipeline never completed reliably in the copy. An alternate JPEG path finally saved real photos, but changed the processing and introduced session transitions. Image quality and colors were visibly worse, lenses jumped during capture, and a second shutter press could do nothing. I stopped at that point.

The useful finding is that the device can execute the tested composition workload. Making the Camera UI expose that workload and preserving the original camera's behavior are separate engineering problems.

## What was present

The original app contained configuration gates and code associated with real-scene AI composition and inspiration composition:

```text
com.oplus.feature.aicomposition.realscene.support
com.oplus.feature.aicomposition.inspiration.support
```

Neither key appeared in the shipped configuration sources inspected. The Boolean lookup supplies `false` for an absent key. These were missing entries, rather than explicit zero-valued entries. A separate `com.oplus.lumo.setting.guide.support` value of `1` did not enable them.

The configuration investigation decoded 666 vendor-tag records from `oplus_camera_config` and inspected the separate 22-entry `camera_unit_config` vendor map. It also traced the SDK defaults and app fallback. That established the shipped fallback, not a direct read of every value in the running original Camera: its private update and preference files were inaccessible to ordinary shell access.

The feature's code being present did not mean all its required payloads were installed or compatible. The eventual success needed donor engine/model assets as well as changes to the clone. It is not evidence that setting two booleans in the untouched original app would have been sufficient.

## Why an update or Shizuku was not enough

A locally rebuilt APK does not carry OPPO's signing key. Android verifies the identity of updates, and signature-protected services make their own authorization decisions outside the calling app. Changing an app's answer to its own certificate checks does not make Package Manager or another process accept that certificate.

An OEM-signed donor Camera update was tried earlier and launched, but did not expose the desired control on this configuration. That is different from installing a locally modified, re-signed update: successful installation of an authentic donor does not authorize modified bytes.

The traced feature gates read Camera configuration, not similarly named Android settings. No supported shell-accessible configuration setter was established. Internal activities and receivers were private or protected by OPPO signature permissions. Shizuku's ordinary ADB mode did not turn the shell into an OEM-signed caller or make protected system configuration writable.

The practical experiment therefore used a distinct package, `com.n810.camera.aicomposition.lab`, retaining the label Camera. Changes were confined to that copy and its process. The original Camera was kept separate throughout the modified-app iteration.

## Finding a compatible engine

The first native inference failures were not proof that the phone could never run the model. An inspected pair reported a compiled-model/runtime mismatch, `V254803` against `V245303`.

Two donor firmware archives were investigated. Their tracked build identifiers were `CPH2911_17.0.0.106(EX01)` and `CPH2913_17.0.0.106(EX01)`, stored locally under Find X10 and Find X10 Pro Max labels. Those labels describe the investigation's inventory; the compatibility finding depends on the actual platform and files, not the marketing name.

The CPH2911 package supplied a `dx5`/MT6993 combination. Its `libneuron_runtime.9.so` matched the X9 runtime byte for byte in the recorded comparison. The other donor targeted `dx6`. Substituting one donor indiscriminately for the other would miss the reason the successful pair worked.

A standalone probe first returned a nonempty composition result with the compatible model/runtime combination. The later modified OEM app used private model paths, matching Neuron/APU dependencies, and process-local redirection of the composition adapter's engine and tracker imports. The original APK's C++ dependencies were not overwritten by the donor variants. No system library replacement was required.

The final test APK depended on roughly 660 MB of models provisioned separately into its private directory. Installing that APK alone on another phone would not reproduce the result.

## Restoring the preview callbacks

Getting the icon to appear was the easy part. The clone also had a black or stalled viewfinder and native callback failures.

The callback arrived with a null weak reference. Substituting a generic `APSClient.getClient()` avoided one crash but selected the wrong client. A census found two SDK clients, with only one holding a registered preview callback. The repair retained the client registered through `setDataCallback` and supplied it to the broken callback path. Sharing that registered client with the image-processing unit avoided creating another unregistered client.

The repair ran through an app-local JVMTI agent bundled into the debuggable test copy. It was not a root module or a system-wide hook. Its method signatures, DEX locations and register slots were tied to this build.

Severe preview color corruption was separately reduced by disabling `com.oplus.camera.preview.hdr.support` inside the clone. That produced an SDR preview; it did not repair the HDR preview path. A black diagnostic bitmap from a buffer-copy experiment was excluded as evidence of the actual inference input.

## What actually worked

The OEM presenter consumed real preview input, the native engine returned success and a crop, and the guidance UI displayed its positioning target. In one recorded test, the presenter requested zoom `2.49756` from `1.0`. Camera-service metadata independently reported `android.control.zoomRatio=2.49756002`. That matters because a moving slider or changed label alone would not show that the camera applied the request.

Recorded successful inference durations included 4,896 ms, 3,678 ms and 1,835 ms. These were individual diagnostic observations, not a benchmark or a latency guarantee. Controlled rear-camera checks also reached 0.6×, 1×, 2×, 3× and 6× with metadata identifying ultrawide, main and telephoto cameras. Settled focus and sensor throughput did not establish smooth transitions.

A later user-aligned capture reported zoom `3.3355`, focal length `16.53` and autofocus state `2`. The engine also produced Chinese scene descriptions. One visible caption read:

> 画框立在窗边，氛围更凝练

Meaning: “The frames stand beside the window, creating a more focused mood.” It was generated composition text, not an error message. Replacing one embedded prompt literal with English did not change the generated output, so that experiment was reverted. English generation was not solved.

## Why taking a photo was a separate problem

The original still pipeline received six RAW frames and corresponding metadata, but its processing category still required tuning data while reporting none. Captures stalled before producing a finalized JPEG. A storage permission was not the demonstrated cause of that stall.

An experiment with tuning-buffer delivery caused a vendor camera-service assertion and was removed. Another attempt to request a full-resolution image through the existing OEM session also failed. Neither result identifies every missing vendor dependency; both show why bypassing a guard is not a substitute for supplying the processing inputs it expects.

The successful fallback created a temporary standard Camera2 session on the open camera device. It used a JPEG reader and a small metering stream, waited for exposure/focus readiness, captured a real JPEG, and then restored the OEM preview session. The helper published those bytes to the clone's matching pending MediaStore entry, or created an entry if necessary. It did not relabel a preview thumbnail as a completed photograph.

Saved images were independently decoded: a 1× JPEG measured 4096×3072, and a controlled 3× portrait JPEG measured 3072×4096. The aligned capture produced 2,504,592 JPEG bytes. Individual full-resolution photographs really were saved.

But this route bypassed the original multi-frame still-processing path. The user's report of different colors and worse quality is consistent with that change; there was no controlled image-quality study attributing each difference to a particular processing stage. Calling it Hasselblad or stock-camera parity would be unsupported.

## What remained broken

| Area | What was established | Limit |
| --- | --- | --- |
| LUMO Framing | Real inference, crop, guidance UI and applied zoom | Not a broad accuracy or scene-coverage study |
| Preview | Working SDR viewfinder | HDR preview still incompatible |
| Capture | Individual full-resolution JPEGs saved | Original multi-frame processing bypassed |
| Repeated shutter | First aligned shot completed | Second tap reached the enabled button but not the SDK capture hook |
| Zoom and lenses | Requested ratios and physical cameras observed | Capture could visibly jump between views |
| Gallery | Public viewing of a completed saved photo demonstrated | Stale thumbnail and lifecycle behavior not fully resolved |
| Language | English movement UI, real scene descriptions | Generated descriptions remained Chinese |
| Other modes | Some earlier extension experiments | No full video, RAW, portrait or stock-mode validation |

The repeat-capture trace is especially useful. The second tap reached `ShutterButton.performClick` while the control was enabled and clickable, but never reached the capture interception point. That locates the unresolved problem earlier in the app's capture-state path. It does not justify forcing every busy flag to false.

The aligned-shot trace did not show alternating app zoom requests during the visible jumping. Switching between the temporary standard session and the OEM session is a plausible cause of the transitions, but it was not conclusively isolated or fixed.

Gallery had a related integration problem. The photo could already be published while the OEM thumbnail state still considered it unfinished. A public viewing intent and selection of the newest valid saved URI demonstrated viewing without the unavailable privileged animation handoff. That did not establish that every secure-entry or resume lifecycle worked.

## What this does and does not show

This is evidence of working computation and partial integration on the tested device. The feature was not just a decorative icon, a hardcoded answer or a mock overlay. Compatible donor assets allowed the actual engine to execute in an ordinary app process.

It does not establish that OPPO could ship the same feature on every X9 configuration with one flag change, that the complete stock implementation was already ready on this phone, or that product segmentation was the only reason it was absent. It also does not prove that root is necessary or sufficient. The investigation stopped with specific missing integration work rather than a mathematical impossibility result.

The remaining work would have to address capture completion state, session transitions and access to or reconstruction of the vendor processing contract. A usable camera requires those behaviors to work together. The successful inference alone was not enough to justify keeping the experimental app.

## Source and evidence

The accompanying repository preserves the original experimental helper and patch source, a source-hash manifest and a curated results record. Raw device logs and personal images remain private. The published measurements are an account of the recorded tests, not an independent reproduction by another laboratory.

No firmware, proprietary models, OEM APK or signing key is distributed. The historical build scripts require intermediate inputs that are not part of this repository; see the source map before attempting to reconstruct an experiment.

The Android mechanisms discussed above are documented in these primary references:

- [Android app signing](https://developer.android.com/studio/publish/app-signing)
- [Signature permission protection levels](https://developer.android.com/guide/topics/manifest/permission-element)
- [Camera2 vendor extensions](https://developer.android.com/media/camera/camera2/extensions-api)
- [Shizuku setup and ADB operation](https://shizuku.rikka.app/guide/)

These references explain the platform mechanisms. Device-specific findings come from this investigation's configuration decoding, decompilation, native analysis and on-device traces.
