# S26 PSTN Audio Lab — independent Shizuku UserService

This is a **separate Android debug application**, `io.s26.pstnlab`, which does **not** modify Project IR Mobile Agent or Samsung Phone.

## What actually runs on the S26 Ultra

- Android 17 / API 37 / SM-S948B
- Standard app binds a real Shizuku UserService, process suffix `s26_pstn_lab`, Linux UID **2000 (shell)**
- The phone app invokes `status()`, `preflight()` (read-only API/permission discovery), `fakePcmTest()`, and `fakePcmFrame(byte[])` over Binder. The last method validates **640-byte PCM16 mono 16kHz / 20ms** frames, then echoes the input in memory. On test, 100 frames cross the actual app ↔ privileged process boundary.
- The separate **Inspect Persian speech engines** button inventories TTS voices and generic on-device recognition service presence without recording, speaking, installing models or contacting an LLM; the result is saved as `persian-speech-status.json` in app-private storage.
- In-call AudioRecord/AudioTrack are NOT opened by this app. The preflight result `prerequisite_gate=true` only proves permissions and method visibility, not usable live PCM.
- `PstnAndroidAdapter` is packaged in the UserService, but its `AndroidRedirectBackend` is **not opened or exposed**. No live telephony, microphone, recording or injection is possible through this app's current Binder allowlist.
- Explicit Stop button calls Shizuku's teardown and releases the lab process; Bind restarts it.
- This is **not** a persistent system service after a reboot. Shizuku itself must first be started, then the lab app launched/bound. The UserService can remain alive if only the UI is backgrounded.

## Build on ops (Windows)

```powershell
cd C:\GitHub\s26-local-agent\android\pstn-audio-lab
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:GRADLE_USER_HOME='C:\Users\mmsot\.gradle'
.\gradlew.bat :app:assembleDebug --offline --no-daemon --console=plain
```

Debug APK: `app\build\outputs\apk\debug\app-debug.apk`

## Install / inspect

```powershell
$adb='C:\Tools\Android\platform-tools\adb.exe'
& $adb install -r -t .\app\build\outputs\apk\debug\app-debug.apk
# Grant Shizuku's access through its own permission prompt on the phone.
& $adb shell am start -n io.s26.pstnlab/.MainActivity
# Debug builds only:
& $adb shell run-as io.s26.pstnlab cat files/lab-status.json
& $adb shell ps -A | Select-String 'io.s26.pstnlab:s26_pstn_lab'
```

The user approved the isolated lab's Shizuku access during installation. Shizuku treats this as a highly privileged permission. To revoke in Shizuku Manager, select the lab app and revoke access; alternatively uninstall just `io.s26.pstnlab`. Do not remove `io.projectir.mobile` or Shizuku.

## Test gates

- APK install and explicit Shizuku permission
- UserService bound as shell
- Internal fake PCM backend: 100 RX + 100 TX, released
- IPC bridge via `fakePcmFrame(byte[])`: 100 × 640-byte frames, equality checked on both sides of binder transaction
- Service survives returning to Home
- Stop destroys only this UserService, Shizuku remains running
- Bind starts new service process and repeats test
- Samsung dialer unchanged

Real call downlink capture, uplink injection, STT/LLM/TTS integration are **NOT** tested or enabled.

See `docs/CALL_AUDIO_SHIZUKU_LIVE_LAB_2026-10-13.md` for the device evidence.
