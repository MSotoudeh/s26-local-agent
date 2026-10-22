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
