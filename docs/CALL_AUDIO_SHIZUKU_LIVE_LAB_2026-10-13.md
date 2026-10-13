# S26 Ultra — Independent Shizuku PSTN Lab Installation, 2026-10-13

## Objective and scope
Implement an **actual on-device, independently installed Shizuku UserService**, not just a temporary ADB Java probe. The service must demonstrate PCM transport and lifecycle without dialing, touching the radio telephony path, recording, injecting live audio, altering Samsung Phone, or affecting the separate Project IR Mobile Agent.

## Device
- SM-S948B, Android 17 / API 37, Samsung firmware.
- ADB active from `ops`; Shizuku running as shell (UID 2000).
- Existing `io.projectir.mobile` installed and deliberately unchanged.
- New isolated application ID: **`io.s26.pstnlab`**.
- Build: Android SDK 37, AGP 9.4.1, Gradle 9.6, Java 17. Built from source under `android/pstn-audio-lab/`.
- Installed debug APK package/version: `io.s26.pstnlab` / `0.1.0-lab`; SHA256: `51dca7d365675ff84b8d28a8b8e1a3df8afbe6c66e7174a5e87e2e353bb178f6`. APK, Android SDK `local.properties`, and signing configuration are excluded from Git.

## On-device operations verified (first installed build)
- `adb install -t -r` returned `Success`.
- The new app's Shizuku permission was granted in the Shizuku Manager permission UI after an explicit request by this app (Android permission itself was also granted).
- `Shizuku.bindUserService` returned the lab's own `ILabService` Binder, running in independent shell process `io.s26.pstnlab:s26_pstn_lab` (UID 2000).
- `status()` responded from the shell UserService with `phone_audio_backend=disabled`, `adapter=loaded_not_opened`, `call=not_attempted`.
- `fakePcmTest()` completed 100 fake receive frames + 100 fake send frames in the service and released the fake backend.
- Backgrounding the app via Home **did not kill** the UserService.
- Stop via the lab UI removed the UserService; `shizuku_server` continued running, and the Samsung dialer remained unchanged.
- Rebinding created a new UserService process and repeated the 100/100 fake PCM test successfully.
- No real call was made; no real audio was captured, played or injected.
- Version 2 was rebuilt, reinstalled, and verified on the actual SM-S948B: `fakePcmFrame(byte[])` transported **100 x 640-byte PCM16 frames over real Binder IPC** with byte-for-byte checks; the report contains `binder_pcm_20ms_frames=100` and `pass=true`. After updating the app, a local `am force-stop`/launch of only `io.s26.pstnlab` ensured the new service code was loaded.
- Version 2 stop/rebind was repeated: `STOP_PASS=True`, `REBIND_PASS=True`, `BACKGROUND_PERSIST_PASS=True`. Shizuku remained active, Samsung Phone remained the default dialer, and Project IR Mobile Agent stayed installed.

## Verification artifact
The installed debug app writes only a summary into private app storage `files/lab-status.json`, accessible via `adb shell run-as io.s26.pstnlab cat files/lab-status.json`. It does not store PCM or call content.

Expected validated fields after v2 test:
```json
{
  "scope": "NO_CALL_NO_REAL_AUDIO",
  "service": {
    "uid": 2000,
    "shell": true,
    "phone_audio_backend": "disabled",
    "adapter": "loaded_not_opened",
    "call": "not_attempted"
  },
  "fake_pcm": {
    "rx": 100, "tx": 100, "cleanup": true,
    "backend": "FAKE_ONLY", "real_call": "NOT_USED", "pass": true
  },
  "binder_pcm_20ms_frames": 100,
  "pass": true
}
```

## Safety and lifecycle
- The Binder service provides only `status`, `fakePcmTest`, and `fakePcmFrame` plus stop/destroy. It does NOT provide direct execution of shell commands, real audio capture/injection, or call automation.
- Production `PstnAndroidAdapter.AndroidRedirectBackend` source is packaged but never constructed by this app. This is deliberate.
- Authentication to the Shizuku UserService is through Shizuku's grant and Binder connection. This is a highly privileged permission; only grant to trusted apps.
- The service is a Shizuku daemon while Shizuku runs; it does not survive a Shizuku stop or phone reboot automatically. Android ADB/USB is not needed to **run fake PCM after the initial setup** if Shizuku is active.
- The app may be uninstalled independently with `adb uninstall io.s26.pstnlab`, leaving Shizuku, Samsung Phone and Project IR Mobile Agent unchanged.

## Still unverified / out of scope
- Samsung PSTN **real downlink capture and uplink injection**, including context attribution, mute/echo handling, actual PCM formats and integration with a remote party.
- Actual live speech transcription or Persian text-to-speech.
- Real PCM streaming to an LLM (current binder uses synthetic test frames only).
- Autonomous start after reboot, which requires Shizuku itself to be running.

## Source
`android/pstn-audio-lab` is a self-contained isolated Gradle Android app. The previous read-only Android API probe and offline PCM bridge remain under `scripts/pstn-audio-probe`. Upstream conceptual reference was [Shizuku API](https://github.com/RikkaApps/Shizuku-API). No source code from the third-party voice SIP project was imported.
