# Offline PSTN Audio Bridge Lab — 2026-10-13

## Purpose and hard boundaries

Prepare the **local, no-call** audio transport component for a future Persian voice-agent using the cellular SIM of a Samsung SM-S948B without root. The scope here is: review an existing FOSS implementation, verify additional Android hidden API symbols **without calling them**, build a host-side PCM bridge, run deterministic safety and format tests, and document what is unproven.

**Not performed:** dialing, connecting to a telephone system, answering, recording real calls, replacing the dialer, muting the microphone, changing routing, changing app-ops, recording user audio, STT, LLM API calls, or generating genuine Persian speech. No third-party Android APK was installed.

## Live device read-only findings

Project IR -> ops -> USB ADB (device previously confirmed Android 17 / API 37, SM-S948B). The repeatable read-only Java reflection probe produced:

```text
PROBE_SCOPE=read_only_no_call_no_audio
AUDIO_SERVICE=connected
INTERCEPTABLE_API=present parameters=0
PSTN_CALL_AUDIO_INTERCEPTABLE=true
REDIRECT_HELPER_DOWNLINK=present
REDIRECT_HELPER_UPLINK=present
REDIRECT_BUILDER_RECORD=present
REDIRECT_BUILDER_TRACK=present
```

The last four values merely establish that Java reflection finds two `AudioManager` PSTN helper methods and the two `setCallRedirectionMode(int)` builder methods. They do **not** establish Android will permit usable PCM during a real call, that microphone suppression works, or that injected uplink is audible at the remote endpoint. The test executes under ADB shell (uid 2000) and removes its temporary device-side artifact.

## Upstream source audit

- Read-only inspected [sparkcyf/android-voice-sip-agent](https://github.com/sparkcyf/android-voice-sip-agent), upstream HEAD at review: `15a074d`; notably `privileged/.../shell/AudioCapabilities.kt` and `CallAudioTap.kt`.
- Its Android 17 success is on **Pixel 11 Pro**, not SM-S948B. The fallback audio path needs exactly specified redirection and audio attributes; otherwise capture may silently switch to local microphone and injected audio may play locally rather than into the PSTN uplink. Their implementation also handles telecom control and suppression, **not duplicated in this lab**.
- [ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder) targets downlink recording, not sending speech; its maintainers do not yet claim Android 17 support. Do not assume compatibility.
- The review is reference-only; **no GPL third-party source was copied** into this independent offline experiment.

## Offline host implementation

`scripts/pstn-audio-probe/offline_bridge.py`: Python stdlib-only bounded PCM format/transport layer.

- Accepts 20ms frames of **synthetic 48kHz stereo S16LE** input and produces 20ms 16kHz mono S16LE frames to a future STT component.
- Separate model-rate uplink FIFO for a future TTS component; strictly gated by explicit simulated approval and active connection state.
- Maximum queue size; overflow drops oldest frame to bound latency and memory.
- Heartbeat watchdog stops and flushes both queues when expired; explicit stop also disarms injection.
- No recording, network, Bluetooth, PCM system APIs, or telephony control is implemented.
- Format conversion uses a simple 3-sample box-average **only for tests**; for real speech an anti-aliased resampler (SpeexDSP/soxr or verified equivalent), negotiated actual format, clock drift handling and echo/mic suppression are required. S26 policy routes do **not** prove the live device uses exactly 48k stereo.

`scripts/pstn-audio-probe/simulate_pstn_audio.py`: generates two seconds of **synthetic tones**; exercises receive/conversion/queue/send/stop; prints aggregate counters only and fixed Persian text fixtures. No actual speech recognition, natural-language response generation, or Persian text-to-speech is performed.

## Verified results (ops, 2026-10-13)

```text
py -3.14 -m unittest discover -s tests -p test_pstn_offline_bridge.py -v
Ran 11 tests
OK

py -3.14 scripts/pstn-audio-probe/simulate_pstn_audio.py
device_call=NOT_ATTEMPTED
audio_input=SYNTHETIC_TONE
audio_output=SYNTHETIC_TONE
persian_asr=NOT_RUN_FIXTURE_ONLY
persian_llm=NOT_RUN_FIXTURE_ONLY
persian_tts=NOT_RUN_TONE_ONLY
simulated_frames=100
simulated_seconds=2.0
received_frames=100
returned_uplink_frames=100
dropped_frames=0
final_state=stopped
result=PASS
```

One initial simulator run failed on Windows CP1252 console encoding when printing Persian text. This was corrected by JSON `ensure_ascii=True`; rerun passed. The synthetic conversion + queue loop took approximately 62ms on host for 2 seconds of simulated PCM. **This is not end-to-end audio latency and is not a phone benchmark.**

## Gate status

| Gate | Status |
| --- | --- |
| Device AudioPolicy telephony RX/TX and shell grants | PASS (previous gate) |
| `isPstnCallAudioInterceptable()` | PASS (previous and current) |
| Android redirect helpers / builder method reflection | PASS |
| Host PCM contract and watchdog test | PASS, 11 tests |
| 2s host synthetic bidirectional transport | PASS, 100/100 frames, 0 drops |
| Actual phone call audio capture | NOT TESTED |
| Actual uplink injection | NOT TESTED |
| Local on-phone Persian ASR | NOT IMPLEMENTED / NOT TESTED |
| Persian TTS | NOT IMPLEMENTED / NOT TESTED |
| Full Persian AI conversation | NOT IMPLEMENTED |

## Next stages (blocked versus independently feasible)

1. **Independently feasible:** choose a verified Persian ASR and TTS model that fits the S26 CPU/NPU/memory, establish on-device offline inference, and measure latency, accuracy and power. This repo's M3 census does not establish ready-to-use offline Persian speech models. Do not label canned text or a sine tone as successful Persian ASR/TTS.
2. **Independently feasible:** create a separately permission-gated Android shell-side adapter, using exact Android 17 method signatures and explicit audio attribution; first use a no-call dry-run. Requires testing, packaging and careful audio-mode rollback.
3. **Requires real controlled call:** prove capture of the far-end signal and injection heard on a separate known receiver/voicemail/IVR, with participant consent and cost controls. Without such a call, this is fundamentally unverified; don't dial an arbitrary number.
4. **Requires proof from step 3:** on-device orchestrator, echo suppression, interruption/barge-in, constrained call policy, transcript privacy, safe rollback and cellular-data independence.
5. **Do not modify:** original Samsung dialer role, SIM settings, persistent routing/mute, production Project IR connections, or other active M4 work until specifically needed.

## Commands

```powershell
cd C:\GitHub\s26-local-agent
py -3.14 -m unittest discover -s tests -p test_pstn_offline_bridge.py -v
py -3.14 scripts\pstn-audio-probe\simulate_pstn_audio.py
pwsh -NoProfile -File scripts\pstn-audio-probe\Invoke-PstnReadOnlyProbe.ps1
```
