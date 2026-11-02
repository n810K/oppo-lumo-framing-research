# Source map

This is an archive of experimental source, not a supported port or a reproducible clean build. Paths below are relative to the repository. The private investigation kept the complete source and notes separately before removing build artifacts.

## Final modified OEM app

- `archive/work/apk-mod-research/CameraMetadataTest.java`: process-local configuration overrides, diagnostics, standard Camera2 JPEG capture, MediaStore publication, Gallery handoff and thumbnail handling.
- `archive/work/apk-mod-research/agent/repair-engine-trace.c`: registered APS callback repair, composition library redirection, process-local native hooks, shutter/zoom diagnostics and interception of the photo request.
- `archive/work/apk-mod-research/Patch*.java`: targeted DEX modifications for the inspected build. Names and locations are build-specific.
- `archive/work/apk-mod-research/build_donor_adapter_test.py`: final packaging recipe, including assertions about changed and added APK entries.
- `archive/work/apk-mod-research/rebuild_photo_helper.py`: incremental helper packaging; requires the previously patched DEX inputs.

The agent's camera intercept reads DEX register slots. Do not reuse those slot numbers with a different build. The capture helper has known consecutive-capture and session-transition failures. An APK signing or ZIP-alignment check does not validate those behaviors or resolve the vendor libraries' reported 16 KB ELF-alignment warning.

## Earlier experiments

- `archive/work/compat-src/`: earlier compatibility helper.
- `archive/work/Patch*.java`, `prepare_clone.py`, `rename_n810.py`, `build_camera.py`: early package-renaming and compatibility experiments. They are historical stages, not one linear supported build command.
- `archive/work/liveguide-app/`: earlier standalone Camera2 prototype. It is distinct from the final modified OEM app.
- `archive/work/guide-probe/` and `archive/work/compatible-engine/`: engine probes, verification and extraction tooling.
- Other analysis helpers inspect DEX references, classes or configuration. Their hard-coded offsets and inputs belong to the original investigation.

## Missing inputs

The final packaging script starts from `outputs/Camera-config-timing-test.apk`, an earlier modified intermediate, not a stock APK. It also expects a separately patched `classes30.dex`, donor native libraries in `work/apk-mod-research/donor-native/`, a compiled agent and an existing signing key. The entire historical transformation sequence has not been consolidated into a single stock-to-final patch command.

Reconstruction therefore requires:

1. The exact original and donor artifacts, acquired independently. The recorded original APK/configuration and donor archive hashes are in `evidence/results.json`.
2. The earlier intermediate baseline and modified DEX inputs, or reconstruction and validation of those stages from the archived patchers.
3. Matching model/runtime assets and the three JNI bridge declarations used by the donor engine, which are not redistributed here.
4. Android SDK/build tools, a compatible JDK, the JADX/dexlib dependency and JNI/JVMTI headers from their original distributions.
5. A newly generated signing key for a separate test package, never OPPO's original package.
6. Private model provisioning into the test package before inference. The final APK did not bundle all models.

`/PATH/TO/HOME` and `YOUR_ADB_SERIAL` replace local-only values. Build scripts retain the original tool versions and layout to document the environment. Several diagnostic scripts contain ADB install, launch, tap or attach operations; do not run them as generic read-only checks.

## Validation at publication

Python source was syntax-checked without executing build or device scripts. The central Java helper was compiled against the locally installed Android SDK. The historical Android package was not rebuilt or reinstalled as part of publication, and this repository is not advertised as passing a clean-build test.

The research website uses its existing MDX article layout and collection routing. Its checks are recorded separately from the camera experiments.
