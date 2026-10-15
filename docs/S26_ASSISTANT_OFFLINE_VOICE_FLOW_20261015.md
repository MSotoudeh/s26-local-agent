# S26 Assistant → offline Persian voice → canonical plans

Date: 2026-10-15. Branch `feat/s26-assist-voice-flow-20261015`.
Base commit `f63ed1a` (16 KiB-aligned, non-debuggable, signer-compatible
Vosk 0.3.75 + MediaPipe Qwen APK).

## Objective

The user explicitly requested: long-press the phone's Side/Power button
to launch Project IR as the default Android Assistant, listen to Persian
locally, understand permitted requests, and run an audited on-device
Accessibility navigation skill without Wi-Fi/PC/Hub in the execution path.

## Design: strict functional and trust boundaries

- Android already lists `io.projectir.mobile` as current
  `android.app.role.ASSISTANT` holder, and `RECORD_AUDIO` as granted.
- MainActivity implements `ACTION_ASSIST` and `singleTop`. The
  Activity handles both cold start and `onNewIntent()`, using a one-shot
  intent latch. It auto-starts offline Vosk only while the Activity is
  *resumed*, the OS Assistant role is held, permission is granted,
  language model files exist, the screen is unlocked, and no other local
  plan is busy. A test-only `SKIP_ASSIST_MIC` extra prevents microphone
  use during automated launch tests.
- No wake word, notification-triggered recording, background microphone
  service, cloud ASR, or Shizuku microphone injection. The existing
  `onPause()` stops the microphone. A top-level Stop button also
  invalidates its session generation.
- ASR `onResult` and `onFinalResult` may both happen; a one-shot
  session guard ensures the first accepted transcript is dispatched
  **at most once** and the microphone is stopped. A newly generated
  action ID prevents accidental replay of a previous action.
- `LocalVoiceRouting` is an independent, pure Java policy that performs
  **exact** Persian/English normalized phrase matching to just two
  live-verified R0 skills: `android.settings.open` and
  `android.settings.connections`. It maps them to canonical, built-in
  commands (not user-controlled package names or actions).
- Even after exact ASR routing, `LocalPlanPolicy` and
  `LocalAndroidPlanRuntime` verify the canonical skill and use their
  existing write-ahead journal and fail-closed UI snapshots before
  launching/clicking via Accessibility.
- Questions beginning with known question forms may be sent to Qwen for
  **read-only text response**. Qwen has no Android execution interface.
- Unknown, ambiguous, destructive or non-allowlisted utterances are
  rejected without a device action. Original recognized text is left
  as an editable draft. Since Persian Vosk quality was insufficient on
  an isolated reference sample, **no fuzzy intent inference or broad
  command execution** is permitted.

## Build/test acceptance criteria

1. Pure host classifier tests: `LocalVoiceRoutingTest`, 24,000
   assertions over 1,000 repetitions; unknown/partial/destructive phrases
   fail closed. Existing planner/selector/journal/regression suites PASS.
2. Android `assembleRelease lintRelease` PASS, no debuggable build or
   16-KiB native ELF compatibility regression.
3. Before data-preserving `adb install -r`, verify exact authorized S26,
   SHA-256, existing app signer match, and read-only local APK backup.
4. Verify persisted Agent configuration/ForegroundService, Hub/Shizuku
   heartbeat, Android Assistant holder, microphone permission, and model
   availability are unchanged.
5. On-phone scripted **no-microphone** `ACTION_ASSIST` intent smoke test,
   including repeated launch via `singleTop`, and that screen shows
   `ASSIST_READY` without compatibility warning.
6. Owner physical key test is necessary to confirm **true trigger →
   microphone → actual Persian utterance → resulting navigation**; a
   shell-intent test with microphone skip does not establish this.
7. No reboot, Wi-Fi off, uninstall, credential export, model deletion,
   system Assistant role mutation, or forced Shizuku restart.

## Known limitations

- Vosk's single bundled evaluation WAV had poor WER; the exact phrase
  filter is intentionally restrictive. Human pronunciation and
  background noise can still produce false transcription.
- No 99% reliability claim and no broad Android automation:
  only two canonical R0 navigation skills are auto-executed.
- LLM response quality, NPU acceleration and idle screen Assistant
  invocation require separate measurements.
- A non-debuggable owner APK is signed with the device's existing
  developer key to preserve app data. Do not distribute it externally as
  a formally signed production application.

## Verified execution evidence (2026-10-15)

- Android `assembleRelease lintRelease`: PASS with JDK 25.
- On-host `LocalVoiceRoutingTest`: **24,000/24,000 assertions** PASS.
  Existing local-plan host suites: 15,022 machine, 9,015 coordinator,
  17,000 bounded-parent, 20,000 observation-settling assertions PASS.
- Python repo regressions: **52/52 PASS**.
- Release APK `app-release.apk`: 42,411,419 bytes,
  SHA-256 `b277a5314707709988746fb6272e278745ef5f96492e17f7432f22b52b5e27d7`.
- Native PT_LOAD checks `libvosk.so`, `libjnidispatch.so`,
  `libllm_inference_engine_jni.so`: all **16 KiB compliant**;
  `zipalign -c -P 16 -v 4`: PASS.
- Release remains non-debuggable with matching certificate of installed
  Agent. No signing-key switch, package uninstall or data clear.
- Pre-update read-only APK backup:
  `C:\Users\mmsot\AppData\Local\ProjectIR\backups\s26-agent\20261015-before-assist-voice-flow.apk`;
  SHA-256 `95ec794b8afd2dab01066157fc651ad9cfeff836b1c1be0da6a2b1902d8a55d6`.
- Data-preserving `adb install -r` returned Android **Success**.
  Agent process, ForegroundService, Assistant role, and runtime
  RECORD_AUDIO grant all remained present.
- **Transient Accessibility binder recovery:** after install, Android
  reported Project IR accessibility enabled but stuck in `Binding services`.
  The service provider returned `accessibility-not-connected` even though
  Agent was connected. To recover without reboot, the exact original
  colon-separated service list was saved, then only Project IR's service
  was briefly excluded while **both other Accessibility services remained
  enabled**, and the original list was restored verbatim. Post-rebind
  provider returned connected. This is a transient OS rebinding issue
  observed during this upgrade, not a proven regression in routing logic.
- Two `ACTION_ASSIST` launches to the installed app using
  `io.projectir.mobile.extra.SKIP_ASSIST_MIC=true`: both PASSED.
  No microphone started; `ASSIST_READY` banner, Vosk and Qwen model
  indicators appeared; no Android compatibility warning.
  A `singleTop` intent was delivered to an already foreground activity
  without duplicate activity creation.
- Bundled publicly available Persian WAV on the actual S26 processed
  offline after upgrade: `ASR_BUNDLED_SAMPLE_OK_OFFLINE` PASS.
- Qwen inference after upgrade: nonempty **20-character** response shown
  in the Agent UI, without invoking Android actions. Agent stayed running.

**Physical Side/Power-key + actual microphone speech to auto-dispatched
canonical navigation is still UNVERIFIED.** The user needs to hold the
Side key and speak exactly `تنظیمات را باز کن` while the phone is unlocked.
Successful acceptance requires seeing `ASSIST_LISTENING_FA_IR_OFFLINE`
(or loading followed by listening) and verified Android Settings navigation.
Questions instead return plain local text; unsupported ASR strings fail
closed. This acceptance must not be represented as completed beforehand.
