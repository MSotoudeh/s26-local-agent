# S26 Android App Compatibility warning: 16 KB native alignment and non-debug rollout

Date: 2026-10-15. Isolated Git worktree `C:\GitHub\project-ir-s26-page16-20261015`
Branch: `fix/s26-16kb-compat-20261015` based on voice-ASR/Qwen commit `614c934`.

## Trigger

When the owner configured `io.projectir.mobile` as the Android Assistant
and long-pressed the side/power button, the platform displayed a full-screen
**Android App Compatibility** dialog. It said this was a debuggable app and
listed 16-KB page-size native issues:

- `libllm_inference_engine_jni.so`: Unknown error
- `libjnidispatch.so`: Unknown error
- `libvosk.so`: LOAD segment not aligned

The Assistant role assignment itself worked. Direct on-device
`cmd role get-role-holders android.app.role.ASSISTANT` reported
`io.projectir.mobile`. Device `getconf PAGE_SIZE` currently reports
`4096`; the warning concerns wider compatibility and release validation,
not proof that local 16-KB paging is in use.

## Exact original APK findings

Using `scripts/Audit-16k-ELF.py` (stdlib ZIP and ELF program header
inspection), Android's three arm64 native libraries had:

| library | ELF PT_LOAD p_align | zip method | APK ZIP page-aligned |
|---|---:|---|---|
| `libvosk.so` | 4096 | stored | no |
| `libjnidispatch.so` | 65536 | stored | no |
| `libllm_inference_engine_jni.so` | 16384 | stored | no |

The detailed ELF evidence showed only Vosk had non-16-KB segment alignment;
the other two were structurally ELF compliant but packaged at offsets
unsuitable for direct uncompressed mmap on 16-KB devices.

## Remediation

1. Upgrade from `com.alphacephei:vosk-android:0.3.47` to `0.3.75`.
   Independent inspection of upstream version 0.3.75 AAR gave Vosk's
   `PT_LOAD` alignment `[16384,16384,16384,16384]`.
2. Add Gradle `packaging.jniLibs.useLegacyPackaging = true`.
   Android extracts compressed JNI libs rather than mmap-loading
   improperly ZIP-offset-aligned libraries.
3. Define non-debuggable `release` Gradle build type with
   `minifyEnabled=false`.
4. To preserve the existing authorized device owner's data and Android
   Assistant role, sign the new release *with the already-installed
   development key*, `signingConfigs.debug`. This is **not**
   production-grade release-key management; it must be replaced through a
   planned signing-key migration before external distribution. The keystore
   and all secret material remain out of Git.

## Build and validation

- `assembleDebug lintDebug`: PASS.
- `assembleRelease lintRelease`: PASS (JDK 25, Java 21-compiled MediaPipe API).
- Project IR Python regressions: 52/52 PASS.
- Seven existing host JVM suites, including 1,000 simulated plans,
  17,000 bounded-parent assertions, 20,000 settling assertions and
  9,015 coordinator assertions: PASS.
- New `app-release.apk`: 42,402,027 bytes;
  SHA-256 `95ec794b8afd2dab01066157fc651ad9cfeff836b1c1be0da6a2b1902d8a55d6`.
- `apksigner verify`: PASS; signing certificate matched actual
  pre-upgrade installed APK certificate.
- `aapt dump badging`: `application-debuggable=false`.
- `zipalign -c -P 16 -v 4`: `Verification successful`.
- Independent extracted ELF check showed exactly 3 arm64 native libs
  with aligned PT_LOAD segments and compressed ZIP packaging:

| native library | PT_LOAD p_align | ZIP compression | 16 KB ELF verdict |
|---|---|---|---|
| `libvosk.so` | 16384 | compressed | PASS |
| `libjnidispatch.so` | 16384 | compressed | PASS |
| `libllm_inference_engine_jni.so` | 16384 | compressed | PASS |

## Safe rollout and live checks

Before upgrade, pulled and verified the installed working voice-agent APK:
`C:\Users\mmsot\AppData\Local\ProjectIR\backups\s26-agent\20261015-before-16kb-fix.apk`.
SHA-256 `f44982951ae6f7947bbc0f245327bc7c8e3494c66e45a4ed3062dc8527544431`.
Rollback requires `adb install -r` of that exact backup; **never**
uninstall or clear the app's data during troubleshooting.

Installed the release via `adb install -r` (no uninstall/data clear).
Postinstall proved:
- MainActivity starts and Agent process is present.
- Existing ForegroundService and exported Accessibility bridge are active.
- Android Assistant role holder remains `io.projectir.mobile`.
- Accessibility snapshot foreground `io.projectir.mobile`, 41 nodes,
  and **no visible Android App Compatibility warning**.
- UI still shows `FA_IR_ASR_MODEL=INSTALLED` and `QWEN_LOCAL_MODEL=INSTALLED`.
- Upgraded Vosk 0.3.75 actually ran the offline bundled Persian sample
  on the phone and reported `ASR_BUNDLED_SAMPLE_OK_OFFLINE`.

- Qwen local on-phone generation after upgrade: `QWEN_TRIGGER=PASS`,
  `QWEN_16KB_RELEASE_INFERENCE=PASS`, nonempty 20-character
  response; original Agent process remained running. No cloud action or
  device operation was delegated to model output.

## Remaining limits and release governance

- Physical power-button invocation after install and operation on an actual
  16-KB `getconf PAGE_SIZE` device have **not** been directly validated.
  The local S26 returns 4 KB pages. Verified APK native layout and runtime
  execution, but these cannot substitute for a real 16-KB runtime test.
- The development signing key is still used to maintain continuity of the
  previously-installed Agent. This non-debuggable device build is not a
  signed and audited production distribution.
- No persistent media recording, no default Assistant role alteration,
  no reset, no wipe, no phone reboot, no Shizuku server restart, no model
  file replacement or persistent Wi-Fi reconfiguration was performed.
- Live Persian speech accuracy remains unquantified despite the owner's
  functional initial test.
