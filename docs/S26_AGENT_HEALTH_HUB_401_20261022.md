# S26 Local Agent: Binder health and Hub authentication — 2026-10-22

## Scope

Canonical repository: `s26-local-agent` (main). Target `io.projectir.mobile`, Android 17, Galaxy S26 Ultra (no root). User-preserved ShizukuPlus compatibility, ghosting, Dhizuku, firewall and AI features **not modified**. Wi-Fi was kept OFF. No UI interaction, package uninstall, data clear, root, TCP/5555 enablement or reboot in this change.

## Cause

- ShizukuPlus's persistent `Starting Shizuku…` foreground notification (ID 1447) survived completion of `AdbStartWorker`, but the actual `shizuku_plus_server` and Binder remained healthy; the app already had a valid USB-bootstrap server.
- The Project IR Mobile Agent previously displayed only WebSocket connection state (`network-error:ProtocolException`), misleadingly overlapping with independent local shell health.
- After adding safe handshake diagnostics and running the updated foreground service, the backend replied **HTTP 401** to the WebSocket handshake; `Shell: ready (uid 2000)` remained independently true.
- The current Hub source checks the request Bearer against its `AGENT_TOKEN` on `/agent/connect`. Thus HTTP401 indicates failure of that authentication gate; **no token was printed, copied into Git, replaced or rotated**. Access to the private credential source was denied by tool safety controls, so this is still a blocker. Do not bypass that restriction.
- Cold boot remains a separate HOLD: on Samsung no-root Android 17, ADB TCP listener 5555 did not survive reboot and mDNS retry did not restore the server without bootstrap.

## Code changes

- `AgentHealthState.java`: classify Shell status using live Binder ping, Shizuku UID=2000, permission and **UserService Binder readiness**. Transport is separate and untrusted notification / mDNS state is excluded.
- `AgentService.java`: local 10-second health refresh and bounded rebind via `ensureBound()`, even if the Hub is offline; notification and UI are composite `Hub: … | Shell: …`, changed only on a state transition.
- `HubClient.java`: distinguish HTTP handshake status (when present) from transport exception; do not log Authorization, URL or response body. Ignore stale WebSocket close/failure callbacks.
- `UiBridgeProvider.java`: shell/own-UID-gated `agent_start` method to start the non-exported `AgentService` through the app's own context, with a ready-configuration gate and Android's normal foreground-service policies. Used only after owner-authorized APK replacement; does not expose an unauthenticated service.
- `build-android-agent.ps1` / README: Ops-local Java 25 default and user Gradle/SDK paths. MediaPipe GenAI 0.10.27 depends on Java 21 bytecode; JDK17 build failed with classfile 65-vs-61 mismatch.

## Test evidence

- 10/10 host-JVM regression assertions in `AgentHealthStateTest.java` passed, including healthy shell with HTTP failure; offline Binder; missing permission; missing UserService; wrong UID; log string sanitation.
- Missing `sherpa-onnx-1.13.8.aar` was downloaded **from official release** by `Prepare-S26SherpaRuntime.ps1`; pinned SHA-256 check passed. Binary dependency ignored by Git.
- `assembleRelease lintRelease` **PASS**, with JDK 25 and no global Java setting changes.
- APK signer equality with installed app **PASS**. Android APK verification and 16 KiB ZIP alignment **PASS**. Installed package `io.projectir.mobile` preserved (no uninstall / data clear).
- Prechange installed APK backup kept on Ops outside Git: `C:\Users\mmsot\AppData\Local\ProjectIR\backups\s26-agent\20261022-before-binder-health.apk` (SHA256 `9d35c5c2864a060fb1f3baf63e23f851e2782b7f6df9103371762bb64c3cfc70`).
- Final installed signed APK: `app-s26-health-shell-recovery-v2.apk`, SHA256 `44250b0fa56b774ef2ce8d62266c189b99e98c59e37337145d9e4aaa5758b1e2`, same signer certificate as prechange installation.
- ADB `install -r` PASS; initial external `am start-foreground-service` correctly denied because the target service is non-exported. The new authorized `content call --uri content://io.projectir.mobile.ui --method agent_start` returned `ok=true`; independent `dumpsys activity services` showed `startForegroundCount=1` for AgentService, preserving the non-exported boundary. No screen interaction.
- Live Android notification showed exact split: `Hub: network-error:HTTP401:ProtocolException | Shell: ready (uid 2000)`. DroidBridge direct shell `id -u` returned **2000**, Wi-Fi OFF, ADB TCP port 5555 not enabled; Accessibility provider returned `ok=true`. The ShizukuPlus watchdog and server were not changed.

## Acceptance and remaining work

- **GO**: independent Binder/UserService health reporting, foreground recovery through owner/shell-gated provider, data-preserving upgrade, local shell with Wi-Fi OFF.
- **HOLD**: Hub WebSocket authentication, until credential validation/reprovision through an authorized secure method; current device `s26` is offline in Hub.
- **HOLD**: stale ShizukuPlus `AdbStartWorker` notification; do not cancel/kill healthy server to make notification look correct. Fix notification life-cycle in ShizukuPlus upstream separately if desired.
- **HOLD**: ShizukuPlus autonomous cold boot without Wi-Fi/USB/root; earlier controlled boot test failed. A 10-second rebind cannot bootstrap a missing server.

Rollback: use original exact-signer backup via data-preserving `adb install -r` only after checking certificate and user impact; never clear app data or rotate credentials as a workaround.
