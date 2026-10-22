# ShizukuPlus boot persistence and Local Agent integration — 2026-10-22

Status: **IN PROGRESS / reboot acceptance NOT YET VERIFIED**.

## Scope and constraints

Device: Samsung Galaxy S26 Ultra, Android 17, no root.
Canonical agent: `io.projectir.mobile` (Voice -> ASR -> local LLM -> Action Router).
Provider: `af.shizuku.plus.api`, Shizuku+ 13.7.0.r2766.
Phone-only independence is the acceptance goal; PC ADB is recovery/bootstrap only.

**User-preserved settings — do not modify during this experiment:**
Shizuku+ Enhanced API; Binder Firewall; Continuity Bridge; Package Governor;
Shell Accelerator; VM Manager; Storage Bridge; Dhizuku Mode; Shadow Binder;
Stealth Mode; Firewall Success Mocking; BusyBox Simulation;
OverlayFS System Ghosting; Kernel Driver Ghosting;
Partition Flash Ghosting; Power State Ghosting; **all AI features**.
Do not silently enable additional privileged root bridges or auto-approval.
Do not interpret mocked success as execution success.

## Observations on 2026-10-22

- Local DroidBridge/shizuku execution: `uid=2000(shell)` verified.
- Non-root status: root unavailable; normal agent process independent of privileged shell.
- `pm` confirms provider and `io.projectir.mobile` installed; stock Shizuku also present, disabled.
- `WRITE_SECURE_SETTINGS` for `af.shizuku.plus.api`: granted=true.
- Device Idle battery whitelist includes the Plus provider.
- Wi-Fi off (`settings get global wifi_on=0`) while privileged shell calls succeeded.
- Wireless debugging global flag = 1; `service.adb.tcp.port=5555`.
- TCP listener presently bound on `*:5555` (**risk**, restrict exposure later; preserve during controlled bootstrap test).
- `dpm list-owners`: only Island managed-profile owner for user 10; **no main-user Device Owner confirmed**. Dhizuku's UI switch alone does not establish Device Owner.
- The Feature Hub warning says Dhizuku Mode enabled but Device Owner component not active.
- UI changes made via DroidBridge: DNS Governor OFF, Process Control OFF, Status Bar Control OFF; Local ADB Proxy Server OFF. Protected settings were not intentionally modified.
- ShizukuPlus Start on Boot, Watchdog, Intelligent Auto-Reconnect, Device Hardening/Keep-Alive and TCP mode were already enabled in user screenshots; verify they remain enabled after reboot.
- Activity Log enabled. Root-access-dependent options unproven on non-root hardware.

## Privilege semantics for agent actions

1. Categorize calls as `APP`, `ACCESSIBILITY`, `SHIZUKU_SHELL`, `DEVICE_OWNER`, or `MOCK`.
2. Log requested operation, actual execution identity, explicit success/failure and read-after-write verification.
3. Never treat a zero exit code, mock success, UI toggle, or a bridge capability advertisement as evidence of a real privileged side effect.
4. Keep ASR/local LLM/Accessibility operational if Shizuku binder is absent.
5. Retry and rebind Shizuku IPC; avoid a process-alive-only health check.

## Persistence validation matrix

- **P0**: baseline 10x non-destructive shell commands; binder remains healthy.
- **P1**: lock screen -> repeat shell test while locked, no manual Start.
- **P2**: Wi-Fi OFF, cellular ON -> repeat shell test; confirm transport state.
- **P3**: disconnect host USB -> repeat, if safe physical-state check is possible.
- **P4**: reboot with Wi-Fi ON -> test cold boot, binder recovery and execution without user opening Shizuku+.
- **P5**: reboot with Wi-Fi OFF -> cold boot with cellular only; same gates.
- **P6**: force-stop/crash recovery -> inspect Watchdog and 5s/10s/20s retries; destructive service test only after proving a fallback management channel.
- **P7**: prove postboot 100 low-risk operations with >=99% success; report p50/p95 and failures.
- **P8**: enforce network exposure policy for TCP port 5555 after testing; avoid leaving a broadly reachable unauthenticated ADB service.
Each test records Android boot-id, timestamp, package version, available transport, provider/binder state, shell `id`, `dpm list-owners`, app telemetry and whether any UI interaction was needed.

**Hard gate**: A boot test is PASS only if Binder + real `uid=2000` operation return automatically, while no PC and no manual start are required. Keep HOLD otherwise.

## AI Bridge future experiment backlog (keep current switches ON)

- **AI-1**: document enabled ShizukuPlus AI Core / AI Bridge & NPU / NPU Acceleration / Window Hierarchy Crawler / Experimental AI Automation / Display Tuner; read actual capability/API support.
- **AI-2**: record hardware inference backend used by local LLM and Qwen3-ASR. Distinguish actual NPU delegation from displayed NPU options. Compare latency, power, memory and thermals in repeated workloads.
- **AI-3**: compare DroidBridge Accessibility hierarchy vs ShizukuPlus Window Hierarchy Crawler on 4 apps, with accuracy, latency and permission gates.
- **AI-4**: check whether Experimental AI Automation can safely invoke reversible low-risk intents. Require before/after proof; label mocked or simulated execution.
- **AI-5**: run adversarial/permission-denial tests against Binder Firewall and mocked success; privileged actions must not be inferred successful without read-back.
- **AI-6**: verify Local Agent fallback after binder drop and unchanged ASR/LLM pipeline; add an explicit `providerUnavailable` result state.
No production model/runtime or AI feature change before results and a reviewed integration decision.

## Decision record

Until post-reboot evidence exists, keep **ShizukuPlus as experimental provider** and stock Shizuku installed for fallback. Protect the user's selected enhanced/compatibility/AI settings. `Dhizuku enabled` and `Device Owner active` are not equivalent. Do not promote boot-persistence to GO based on current running state.

## Execution receipt — 2026-10-22 12:23 Europe/Rome

- **P0 PASS:** 10/10 independent DroidBridge Shizuku shell `id -u` invocations returned 2000; reported duration 50 ms each (instrument floor, not a defensible p50 latency estimate).
- **P2 PASS:** Android `settings get global wifi_on` was `0`, while local shell operations succeeded through cellular/runtime transport. ADB shell TCP was listening on `*:5555`.
- Pre-attempt boot ID: `bc28a541-d4ec-4877-b15b-c206a8283de0`, up 7 days at 12:23 local time; `sys.boot_completed=1`.
- **P5 INDETERMINATE:** Issued a `svc power reboot` command via DroidBridge. The remote transport replied `NOT_FOUND: Tunnel-client did not poll after this request was enqueued`; subsequent status reads also returned this error. This is **not proof** that reboot was executed or that Shizuku failed to autostart. Postboot boot ID, binder state, and shell revalidation were unavailable. Do not mark PASS or FAIL.
- Project IR older direct `s26` hub was offline; this is not authoritative for the independent DroidBridge tunnel.
- Next diagnostic once DroidBridge reconnects: compare kernel boot ID to receipt, check `sys.boot_completed`, `wifi_on`, `dpm list-owners`, Shizuku+ activity/Watchdog logs and `id -u`. If boot ID unchanged, the reboot command was not delivered; if changed, determine whether any user intervention occurred before binder restoration.

## Follow-up receipt — 2026-10-22 13:20 Europe/Rome

User screenshots show ShizukuPlus main screen Running / adb (USB), while Starting Shizuku / port discovery timed out notification remains and Project IR Mobile Agent shows network-error:ProtocolException.

USB ADB plus independent DroidBridge query on boot ID e28ec629-768f-4817-bbac-9f468e867e39 confirm shizuku_plus_server as shell and shizuku.shell=available; id -u=2000 succeeds with Wi-Fi OFF and no port 5555 listener. Running screen is authoritative for current Binder health. The generic Wireless Debugging card label does not prove the service was started wirelessly.

Android JobScheduler history shows AdbStartWorker jobs 37 and 38 STOPPED (jobFinished), while ongoing notification ID 1447 (channel AdbStartWorker, title Starting Shizuku) remains. This is a stale/orphan notification rather than proof of a stopped server. Targeted Notification Listener dismissal returned dismissed=true, but independent dumpsys notification still showed ID 1447; **not verified cleared**. Do not disable Watchdog or alter the protected feature settings to hide this notification. Provider-side future fix: reconcile/cancel AdbStartWorker foreground notification when an external USB start yields healthy Binder; ensure watchdog checks live Binder rather than stale notification/mDNS status.

Separate unresolved defect: io.projectir.mobile shows network-error:ProtocolException; Project IR hub device s26 is offline. Network/control-plane issue is not proof of Shizuku Binder failure. Investigate backend HTTP/WebSocket handshake separately.

The USB recovery helper scripts/Recover-S26ShizukuPlus-USB.ps1 was tested against the phone: already-running no-op PASS; explicit -Force restart PASS; Binder reconnect PASS. It is not automatic and does not solve postboot phone-only independence. Experimental Settings.Global.adb_tcp_port=5555 was reverted to null; port 5555 is not listening. Wi-Fi OFF was restored.
