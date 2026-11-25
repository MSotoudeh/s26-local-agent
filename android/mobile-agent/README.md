# Project IR Mobile Agent

Native Android executor and semantic UI bridge for Project IR.

## Runtime architecture

```text
ChatGPT / Project IR tools
        |
        | HTTPS / WSS
        v
existing Project IR Hub
        |
        | outbound WebSocket initiated by phone
        v
S26: Project IR Mobile Agent
        |-----------------------------|
        |                             |
        v                             v
Shizuku UserService            AccessibilityService
uid=2000 (shell)               structured UI state/actions
        |                             |
        v                             v
/system/bin/sh                 restricted ContentProvider
```

The Android agent registers a configurable device ID (default `s26`) and reuses the existing Project IR execution contract. Current clients can therefore target the phone directly with `remote_exec(device_id="s26", ...)`.

No inbound port, VPN, Wi-Fi specifically, ADB-over-network, desktop relay, or PC is required after bootstrap. Cellular data is sufficient for the Hub connection because the phone initiates it.

## Capabilities

### Shizuku shell

Privileged shell execution is delegated to a Shizuku UserService. With ADB-started Shizuku on the target S26, commands execute as UID 2000.

### Semantic UI bridge

The native Accessibility bridge exposes:
- current structured Accessibility state;
- exact-text semantic click;
- resource-ID semantic click;
- text input into the focused editable field;
- global Accessibility actions.

The provider is callable only by the app itself or shell UID 2000. It adds no HTTP listener or secondary WebSocket server.

Target validation on the S26:
- Samsung Settings semantic workflow: **20/20 PASS**;
- mixed Persian/English input `سلام test 123`: exact write/read verification **PASS**;
- final direct Project IR smoke test after APK update: **PASS**.

## Security

- Hub traffic is TLS-only.
- The phone generates its own Hub token, registers it with a one-time code and stores it encrypted with an Android Keystore AES-GCM key (see `../../docs/remote-access.md`).
- The bootstrap file holds only that one-time code and is deleted after import.
- stdout and stderr together are limited to 400 KB (UTF-8, JSON-escaped) to stay below Binder transaction limits; longer output is cut and marked `*_truncated`.
- A command that times out is killed with its whole process group.
- The Hub token is deleted when the Hub URL changes.
- Text of password fields is replaced by `[password]` in UI snapshots.
- the semantic ContentProvider enforces own-UID or shell-UID access.
- the APK does not listen on a network port.

## Reboot boundary

The app can restart and reconnect after `BOOT_COMPLETED` and after an APK update, but an unrooted app cannot independently restore Shizuku after reboot. Hub connectivity and native non-Shizuku capabilities can return while shell-privileged execution remains degraded until Shizuku is started again.

## Build

Requires:
- Android SDK API 37;
- Build Tools 36.0.0;
- JDK 21+ (JDK 25 verified on Ops; MediaPipe 0.10.27 requires classfile version 65);
- AGP 9.4.1;
- Gradle 9.6.0.

On Ops use:

```powershell
.\scripts\build-android-agent.ps1 -Release
```

The canonical JDK lives under `%LOCALAPPDATA%\ProjectIR\toolchains`; generated build state and `local.properties` stay outside Git.

For bootstrap and full architecture/validation details, see `../../docs/android-agent/overview.md`.
