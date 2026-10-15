# S26 Ultra: offline Persian ASR, local LLM, Android Assistant candidate

Verified on-device: 2026-10-15. Scope: `feat/s26-voice-assistant-20261015`
based on previously tested `f058a7b`, separate Project IR Git worktree
`C:\GitHub\project-ir-s26-voice-assistant-20261015`.

## Deliverables implemented

### 1. Fully offline Persian push-to-talk (Vosk)

- Dependency: `com.alphacephei:vosk-android:0.3.47` (Apache-2.0).
- Persian model: `vosk-model-small-fa-0.42` from
  https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip .
- Downloaded ZIP: 53,431,220 bytes, SHA-256
  `977cb5faa538f3a835ccfd35f5f6d8284b5c450b89c700b9bd4736b66536ad46`.
- Unpacked: about 96.6 MiB; files staged to
  `/sdcard/Android/data/io.projectir.mobile/files/models/vosk-model-small-fa-0.42/`.
- `LocalPersianAsr` loads the model on a background worker; the mic is
  activated only by explicit button press and runtime RECORD_AUDIO grant.
  Mic stops on pause/background and on the Stop button. No cloud recognizer,
  background wake-word or hidden microphone path is implemented.
- `PersianTranscript` accepts only a nonempty, bounded Vosk final JSON
  transcript; this text is placed in the local command box **as a draft**.
  No voice recognition callback ever directly dispatches an Android action.
- A separate button plays the **Vosk model's bundled 48-kHz public WAV**
  directly into the offline recognizer (no microphone) for reproducible tests.
- On-device bundled WAV test: `ASR_BUNDLED_SAMPLE_OK_OFFLINE`.
  Android `RECORD_AUDIO` permission remained `granted=false`.
- A benchmark comparing the single 5.448-second WAV with its bundled
  `test.txt` yielded **WER 3.400 / CER 1.864**, 5 reference words versus
  17 recognized words. This very poor **single-fixture** score does not
  measure general Farsi accuracy. Persian live-speech quality is unverified.
  Sample test is a runtime smoke test, NOT a successful quality acceptance.

### 2. On-device text-only LLM

- Dependency: `com.google.mediapipe:tasks-genai:0.10.27`.
- Model: public, Apache-2.0 `litert-community/Qwen2.5-0.5B-Instruct`,
  MediaPipe-compatible `Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task`.
- Download: 546,660,344 bytes, SHA-256
  `e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2`.
- Verified same SHA-256 directly from phone via `adb shell sha256sum`.
- Installed model path:
  `/sdcard/Android/data/io.projectir.mobile/files/models/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task`.
- `LocalLlmEngine` runs only after a button press, on a background worker.
  Results are rendered as plain text; no LLM output can call Hub, Shizuku,
  shell, or Accessibility actions automatically.
- Test prompt `Say hello in Persian` returned an on-device response:
  `سلام، هستم!` as visible in the app's LLM result TextView.
- Phone's existing agent process and Hub service remained operational.
- **Do not claim NPU acceleration, low latency, high quality or autonomous
  execution**: none were measured or implemented.

### 3. Android Assistant role integration

- Manifest declares the standard `android.intent.action.ASSIST` with
  DEFAULT category on exported `MainActivity`. This registers the app
  as an assist-intent target without giving it privileged background access.
- `RoleManager.ROLE_ASSISTANT` availability/held status shown inside app.
- Button calls `createRequestRoleIntent(ROLE_ASSISTANT)`, allowing Android
  system to seek explicit **user consent** if the device permits selecting
  this app. No direct role mutation, bypass, accessibility setting change,
  or silent default-app switch is performed.
- `adb am start -a android.intent.action.ASSIST -n io.projectir.mobile/.MainActivity`
  successfully launches the app (entrypoint).
- `cmd package query-activities` confirms package resolves ASSIST intent.
- The default holder before/after verification remained
  `com.google.android.googlequicksearchbox`.
- The system role picker completion was **NOT** validated; clicking the
  request button did not yield a confirmed system picker in the automated
  capture. User-side role selection and assist hardware gesture behavior
  remain unverified. System compatibility warning overlay can also appear
  when launching this debuggable build.

## Live APK rollout and rollback

Build with JDK 25 (the MediaPipe 0.10.27 Java API is compiled for Java 21):
`assembleDebug lintDebug` PASS. Previous JDK 17 compiler error for that
library was resolved without changing global Java installation.

Agent APK SHA-256:
`f44982951ae6f7947bbc0f245327bc7c8e3494c66e45a4ed3062dc8527544431`,
size 98,272,928 bytes; signature matched installed agent.
Installed via `adb install -r` with preserved app data. MainActivity,
ForegroundService, Accessibility bridge and Hub resumed. No phone reboot,
Wi-Fi change, Shizuku server restart, microphone authorization or role-holder
change was performed.

Read-only backup of the immediately prior installed working APK:
`C:\Users\mmsot\AppData\Local\ProjectIR\backups\s26-agent\20261015-before-fa-asr-qwen-assistant.apk`.
SHA-256 `11660ff0be15388a3f8a99c1e0883e99c78c2a180ecd498a81e8934eb876fe30`.
Models and backed-up APK remain local on user-owned Ops/phone and are NOT
committed or uploaded to Git.

## Important limitations / remaining manual acceptance

- For **live Persian speech**, the user must tap Listen and grant Android's
  runtime microphone permission, speak a sentence, and tap Stop. The text
  remains a draft until an explicit Run confirmation. This manual test is
  still needed to measure WER/CER on representative real speech and accents.
- For **default Android Assistant**, the user must choose Project IR Mobile
  Agent in the Android system chooser, if offered. The Google assistant
  remains unchanged until that explicit user action.
- The local model currently supplies **read-only text generation**. It is
  not a reliable generalized planner and does not autonomously control apps.
- Role shortcut invocation from Home/locked screen, wake-word, long-lived
  background recording, no-Wi-Fi/no-USB endurance, and reboot recovery
  are **not** validated by this milestone.
- Do not interpret a successful 5-second offline file decode as high ASR
  accuracy; the initial sample comparison failed that quality criterion.

## User operation

Open **Project IR Mobile Agent** on the phone.
- For Persian: scroll to `Persian speech`; tap
  `Listen Persian offline` (first grant mic permission if prompted), speak,
  and tap `Stop Persian microphone`. Inspect/edit draft before manually Run.
- For local LLM: type a text query into `Type Persian or English command`,
  tap `Ask Qwen locally - show response, no actions`, then read its response.
- To opt in to Assistant integration: tap
  `Request Android Assistant role (system confirmation)`, and select the app
  only in the OS-controlled confirmation interface.
