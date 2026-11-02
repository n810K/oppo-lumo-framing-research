# OPPO Live guide research

The Find X9 Pro ran OPPO's actual Live guide inference and guided zoom without root, inside a separate modified Camera APK. It did not become a reliable replacement for the original Camera. The final experiment saved real photographs through a standard Camera2 JPEG session, but bypassed the original multi-frame processing, had visibly different quality and colors, and still suffered lens jumps and a repeat-shutter failure.

This repository preserves the investigation and original experimental patch/helper source. It is a research archive, not an installable camera release.

- [Research report](docs/research.md)
- [Evidence and limitations](evidence/results.json)
- [Source map and reconstruction requirements](docs/source-map.md)
- Website article prepared for n810studios.com; publication is pending the site integration merge.

## Scope

Tests took place on November 1–2, 2026, on one OPPO Find X9 Pro, CPH2791, running ColorOS 17. The original inspected Camera was 7.006.125. Firmware and app versions matter: obfuscated method names, DEX register numbers, native imports and model/runtime combinations are specific to the inspected artifacts.

The final experiment used `com.n810.camera.aicomposition.lab`. The earlier standalone prototype used `com.n810.camera.liveguide`. Neither represents a supported update over `com.oplus.camera`.

## What is included

Original helper Java, DEX patchers, the process-local JVMTI agent, diagnostic scripts, and the earlier standalone prototype's application source. `archive/work/` preserves the investigation's directory layout. Personal home paths and the device serial were replaced with placeholders; the source manifest records original and published hashes.

## What is not included

No OEM APKs, full decompilations, ROMs, vendor native libraries, proprietary model weights, private signing keys, raw device logs, personal photographs, or installable releases. JNI wrapper declarations and JNI/JVMTI headers from external dependencies are also omitted. The local private archive retains the broader investigation for its owner.

The historical build scripts are **not a complete clean build**. They depend on earlier patched APK/DEX inputs and vendor assets that are not distributed here. Some scripts install or interact with the test app. Read the source map before executing anything; reproducing a diagnostic is not the same as having a working camera.

## Status

Investigation paused after real inference, automatic framing, and individual saved JPEGs were demonstrated. Stock processing parity, reliable consecutive captures, smooth lens transitions, English model-generated descriptions, and full Camera-mode coverage were not achieved. No claim is made that root would solve these problems or that every possible non-root route has been exhausted.

## Attribution

OPPO, ColorOS, OnePlus and Hasselblad names identify the software and hardware investigated. This work is independent and unaffiliated. Vendor software and model assets retain their respective ownership; this repository does not grant rights to redistribute them. The original research code is preserved for inspection; no additional license grant is asserted for third-party material.
