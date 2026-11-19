# S26 Ultra — PSTN Call Audio Feasibility Gate (2026-10-13)

## Scope

Read-only examination of cellular call audio feasibility using stock Samsung firmware, ADB shell and Shizuku. **No phone call was dialed or answered. No audio was captured or injected.** No call roles, appops, routing, SIM settings or dialer were modified.

## Device evidence (Project IR / ops, live ADB)

| Check | Observed | Status |
|---|---|---|
| ADB | device authorized, model SM-S948B | PASS |
| Android / API | Android 17 / 37 | PASS |
| One UI system property | ro.build.version.oneui = 90000 | Observed |
| Shizuku | shizuku_server running as uid shell | PASS |
| Receive audio route | Telephony Rx -> voice_rx | PASS |
| Send audio route | incall_music_uplink -> Telephony Tx | PASS |
| Shell permission CALL_AUDIO_INTERCEPTION | granted=true | PASS |
| Shell permission CAPTURE_AUDIO_OUTPUT | granted=true | PASS |
| Shell permissions MODIFY_PHONE_STATE and MODIFY_AUDIO_ROUTING | granted=true | PASS |
| Default dialer | com.samsung.android.dialer | Unchanged |
| Binder isPstnCallAudioInterceptable() | true | PASS |
| Real call downlink capture | NOT TESTED | Pending |
| Real call uplink injection | NOT TESTED | Pending |
| Persian voice-agent round trip | NOT TESTED | Pending |

The read-only probe was compiled using JDK 17 and Android build-tools 36 on ops, run through `app_process` as the existing ADB shell, then removed from `/data/local/tmp`. A repeatable source and script are in `scripts/pstn-audio-probe/`.

Output (sensitive details excluded):

```text
PROBE_SCOPE=read_only_no_call_no_audio
AUDIO_SERVICE=connected
INTERCEPTABLE_API=present parameters=0
PSTN_CALL_AUDIO_INTERCEPTABLE=true
PROBE_EXIT=0
CLEANUP_EXIT=0
```

## Interpretation

The device exposes PSTN RX/TX policy endpoints and its audio binder reports PSTN interception as supported. The Android Shell UID has the necessary granted permissions, and Shizuku is already active as shell. This makes a **non-root live test technically justified**, but it does **not** prove that Samsung's modem HAL forwards live PCM correctly or that uplink injection is audible to the far end.

The open-source [Voice SIP Agent](https://github.com/sparkcyf/android-voice-sip-agent) explains this architecture with Shizuku and Android's hidden audio redirection APIs; the project's reported Android 17 success is on Pixel, not this S26 Ultra. [ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder) is a separate downlink/recording candidate, but explicitly does not guarantee Android 17 compatibility.

## Next gate: live test with explicit participant

1. Use a consenting test participant and a specific test number or an arranged incoming call; avoid international/chargeable calls until costs are acknowledged.
2. Confirm normal Samsung dialer remains default; record current role and permissions for rollback.
3. Use an isolated probe that reports only PCM frame counts/RMS (not content and not audio recordings) during a short call. Test downlink and playback of a brief recognizable tone only with participant consent.
4. Validate audio heard on both ends; test normal speaker and Bluetooth routing if necessary.
5. Immediately stop the probe and restore any temporary audio routing/mute state; verify Samsung dialer and normal inbound/outbound calls.
6. Only after an actual capture+injection PASS, build local Persian STT -> constrained LLM -> Persian TTS on the phone and measure latency.

**Operational safety:** Do not install/enable a third-party SIP gateway with takeover active on a daily-use phone as a substitute for a controlled call-audio test. Android 17 hidden APIs may change, even though today's static capability gate passes.
