# PSTN Android Adapter — Shell-side laboratory gate (2026-10-13)

## Scope

This stage **only** prepares the Android PCM adapter. No Persian model selection or benchmarking, no dial, no call answering, no call recording, no audio injection, no default dialer change, no Android routing/mute modification, and no production service changes.

Device: Samsung SM-S948B, Android 17 / API 37, shell Shizuku available, tested via Project IR -> ops -> ADB.

## Implementation

- `scripts/pstn-audio-probe/android-adapter/PstnAndroidAdapter.java`: experimental Android-side adapter with a strict explicit authorization gate, active cellular call check, shell UID + audio permission check, 16kHz mono PCM16 20ms frame contract, separate RX/TX counters, fail-closed exceptions, resource release, and a 3-second automated heartbeat watchdog.
- `scripts/pstn-audio-probe/android-adapter/PstnAdapterSelfTest.java`: **fake audio only**; tests owner consent, participant notice, an active-call gate, buffer size, RX/TX contract, call drop, open failure, uplink approval, teardown, and watchdog. It **never** instantiates the live audio backend.
- `scripts/pstn-audio-probe/android-adapter/Invoke-AndroidAdapterDryRun.ps1`: compile with Android SDK 37 + Java 17 and D8, push a short-lived classes.dex JAR to the real S26 Ultra, execute the fake test as the adb shell using `app_process`, then delete both temporary host and device artifacts.

The runtime backend is `AndroidRedirectBackend`, using the Android redirection helper methods discovered in the previous read-only probe:

```text
AudioManager.getCallDownlinkExtractionAudioRecord(AudioFormat)
AudioManager.getCallUplinkInjectionAudioTrack(AudioFormat)
```

It is not exposed through the dry-run entrypoint, and must not be used until a consenting live test can verify route identity and far-end audio. The helper may fail due to default process attribution; the backend intentionally has **no legacy mic/speaker fallback** because silently routing PCM to the wrong endpoint would be unsafe. The current authorization flags are an adapter-side contract and are NOT a secure UI consent flow.

## Verified results

Command:

```powershell
pwsh -NoProfile -File scripts\pstn-audio-probe\android-adapter\Invoke-AndroidAdapterDryRun.ps1
```

Device output:

```text
DEVICE_DRY_RUN_SCOPE=FAKE_BACKEND_ONLY_NO_CALL_NO_AUDIO
SCOPE=FAKE_BACKEND_ONLY_NO_CALL_NO_AUDIO
PHONE_AUDIO_BACKEND=NOT_OPENED
PASS owner_required
PASS participant_notice_required
PASS active_call_required
PASS fake_pcm_20ms_round_trip
PASS bad_uplink_frame_rejected
PASS call_drop_closes_session
PASS cleanup_after_call_end
PASS open_failure_closes
PASS uplink_approval_required
PASS automatic_watchdog_close
ANDROID_ADAPTER_FAKE_TESTS_PASS=10
DRY_RUN_STATUS=PASS
```

**Classification:** Java/D8 compile on ops: PASS; Android 17 app_process device fake backend tests: PASS 10/10; PSTN downlink capture: NOT TESTED; real uplink injection: NOT TESTED; Persian ASR/LLM/TTS: OUT OF SCOPE.

The previous `PstnAudioProbe.java` confirmed that hidden helper names exist on this phone. Existence alone does not prove they will construct functional `AudioRecord`/`AudioTrack` instances during a real call.

## Known limitations

1. Actual Samsung call audio path remains unverified until a permitted end-to-end test. Call audio interception can depend on vendor Audio HAL, attribution context, and carrier.
2. Current `AndroidRedirectBackend` uses 16kHz S16LE mono PCM. Actual supported frame format, live HAL routing, incomplete reads, short writes, and jitter must be measured. Partial `read()` values are surfaced to the caller; a real streaming client needs a bounded PCM accumulator.
3. This is a research module, **not** an installed Android application, packaged Shizuku UserService, or complete PSTN streaming agent. To integrate, a separately authorized UserService must supply the correctly attributed shell context and a safe audio consumer/producer.
4. `TelecomManager.isInCall()` plus `AudioManager.MODE_IN_CALL` are conservative fail-closed checks. Some real modem variants might expose a different state and will need read-only inspection, not relaxation without evidence.
5. A caller must explicitly send a heartbeat for an active session. Without it, the watchdog closes the session after about 3 seconds. The currently tested path uses only a fake backend.
6. No models for Persian speech recognition, LLM, or text-to-speech are built or benchmarked here.

## Open live test gate

Only when an appropriate consenting counterparty, voicemail system, or test IVR is available should a separate live-test harness be written. Use explicit cost limits and rollback checks. Do not auto-dial unknown numbers or use this module to record/inject into private third-party calls.

Upstream reference reviewed (for architecture, not copied): [sparkcyf/android-voice-sip-agent](https://github.com/sparkcyf/android-voice-sip-agent), Git revision `15a074d`, especially `ShellEnv.kt`, `ModernAudioContext.kt`, `CallAudioTap.kt`. This independently authored module is deliberately narrower and does not copy upstream implementation.
