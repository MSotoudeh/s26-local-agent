# S26 Ultra call-audio integration: no-call readiness checks (2026-10-13)

## Purpose

Continue the isolated PSTN audio lab without a second phone number. Keep stock Samsung Phone, existing Project IR Mobile Agent, SIM settings, call routing, and microphone state unchanged. No real calls, recording, sound injection, foreign telephone numbers, or external LLM API requests.

## Baseline

- S26 Ultra SM-S948B, Android 17 API 37
- `C:\\GitHub\\s26-local-agent`, main baseline `4641396`
- Shizuku initially reported "not running". Bootstrapped using the EXACT Shizuku-generated `View command` displayed on the phone: `adb shell <installed-Shizuku-library-path>/lib/arm64/libshizuku.so`. Verified shell server active and the isolated `io.s26.pstnlab:s26_pstn_lab` UserService bound as UID 2000. No reboot.
- The app was rebuilt and installed only under `io.s26.pstnlab` and no Project IR application code was modified.

## Actual on-device read-only preflight

`PstnReadOnlyPreflight.inspect(Context)` runs inside the Shizuku UserService. It checks all critical permission grants, actual Android reflective method availability, binder `isPstnCallAudioInterceptable()`, and optionally reads current Telecom/AudioManager state. **It never creates an AudioRecord or AudioTrack.**

Observed from `adb shell run-as io.s26.pstnlab cat files/lab-status.json`:

```json
{
  "scope": "READ_ONLY_NO_CALL_NO_AUDIO",
  "uid": 2000,
  "shell": true,
  "context_available": true,
  "call_audio_interception_granted": true,
  "capture_audio_output_granted": true,
  "modify_phone_state_granted": true,
  "record_audio_granted": true,
  "redirect_helper_downlink": true,
  "redirect_helper_uplink": true,
  "record_redirection_builder": true,
  "track_redirection_builder": true,
  "pstn_interceptable": true,
  "in_call": null,
  "audio_mode": null,
  "audio_record_instances_opened": 0,
  "audio_track_instances_opened": 0,
  "real_audio_enabled": false,
  "prerequisite_gate": true,
  "live_capture_or_injection_verified": false
}
```

`in_call=null` and `audio_mode=null` mean these read-only runtime queries could not be evaluated in this context. **Do not interpret them as “not in a call.”** The prerequisite gate means the platform exposes functions and permissions, not that the vendor HAL routes actual PCM.

## Adapter PCM fix and regression

`PstnAndroidAdapter.readDownlink()` now buffers partial `READ_NON_BLOCKING` reads of the configured 640-byte (20 ms / 16 kHz / mono / S16LE) contract, returning 0 while incomplete and exactly 640 bytes after completion. The bounded residue is overwritten and cleared during close. It does not fabricate audio data or open live PSTN audio by itself.

The fake backend delivered 320 + 320 byte segments. On device:

```json
{
  "binder_pcm_20ms_frames": 100,
  "fake_pcm": {
    "rx": 100,
    "tx": 100,
    "cleanup": true,
    "partial_pcm_assembled": true,
    "backend": "FAKE_ONLY",
    "real_call": "NOT_USED",
    "pass": true
  },
  "pass": true
}
```

The Binder allowlist remains unchanged except a **read-only** `preflight()` query. There is NO method for third-party code to enable `AndroidRedirectBackend`, dial, record or inject, and no modification to Project IR Mobile Agent.

## Persian conversation orchestration, WITHOUT a speech model

`scripts/pstn-audio-probe/persian_dialogue.py` defines a deterministic state machine for owner approval, far-end notification, max turns, valid transcript length, cancellation, sensitive-request rejection and in-memory history cleanup. `FixedPersianReasoner` is a **test fixture only**. It is NOT an LLM, ASR or TTS engine.

`py -3.14 -m unittest discover -s scripts/pstn-audio-probe -p test_persian_dialogue.py -v` → **8/8 PASS**. No network calls or telephone use.

## Persian speech capability inventory

A separate, user-invoked, **read-only** Android speech inventory checks installed TTS voices, offline Persian voice candidates, and presence of an on-device recognition service. It does not speak, record, download models, or change preferred voice/engine.

**Observed on the S26 Ultra:** `on_device_recognition_service=true` and `any_recognition_service=true` (generic, NOT Persian accuracy confirmation); `tts_init_code=0` (TTS engine initialized); `fa_ir_language_available_code=-2` (`TextToSpeech.LANG_NOT_SUPPORTED`), `persian_voice_count=0`, `offline_persian_voice_candidates=0`, `actual_persian_tts_output_tested=false`. **Conclusion:** There is no Persian TTS engine reported by the configured Android TTS provider. A Persian voice must be added separately and genuinely benchmarked. The presence of an on-device ASR service is not proof it supports Persian. Source: actual device `files/persian-speech-status.json`.

## Remaining gates

1. Read-only preflight PASS; usable PSTN downlink and uplink still **UNVERIFIED** until a controlled call with a known and consenting destination or verified test IVR.
2. Confirm the exact sample rates, track/record attribution and safe teardown in a controlled call. A permission/API query is not a modem loopback test.
3. Select and run a **real** offline Persian ASR model and TTS engine on S26 Ultra; measure word error rate, intelligibility, first-token/speech latency, memory, thermals and battery. Any local TTS inventory is a capability check only.
4. Wire a real model provider into the already-tested dialogue policy, include audio turn-taking, barge-in, and a consent workflow.
5. Autonomous calls, notifications and transcripts require real-call verification and documented cost/privacy constraints.

## Operator verification

```powershell
$adb='C:\\Tools\\Android\\platform-tools\\adb.exe'
& $adb shell run-as io.s26.pstnlab cat files/lab-status.json
& $adb shell run-as io.s26.pstnlab cat files/persian-speech-status.json
& $adb shell ps -A | Select-String 'shizuku_server|s26_pstn_lab'
& $adb shell cmd role get-role-holders android.app.role.DIALER

cd C:\\GitHub\\s26-local-agent
py -3.14 -m unittest discover -s scripts/pstn-audio-probe -p test_persian_dialogue.py -v
py -3.14 -m unittest discover -s tests -p test_pstn_offline_bridge.py -v
```

To stop ONLY this lab, use the lab UI stop button; to remove use `adb uninstall io.s26.pstnlab`. Do not remove Shizuku or `io.projectir.mobile`.
