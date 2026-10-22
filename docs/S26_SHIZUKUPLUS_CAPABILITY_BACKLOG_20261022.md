# ShizukuPlus capability evaluation backlog for S26 Local LLM
Status: **PLANNED ONLY — no new phone setting, permission or runtime changes**.
Updated: 2026-10-22.
Related: [S26_SHIZUKUPLUS_BOOT_AI_TEST_PLAN_20261022.md](S26_SHIZUKUPLUS_BOOT_AI_TEST_PLAN_20261022.md), [S26_AGENT_HEALTH_HUB_401_20261022.md](S26_AGENT_HEALTH_HUB_401_20261022.md), [ROADMAP.md](ROADMAP.md).

## Goal and decision rule

Identify **measurable** gains from ShizukuPlus for the existing `io.projectir.mobile` pipeline:
`microphone / text -> Persian ASR -> on-device LLM -> deterministic policy/router -> Accessibility or Shizuku UserService -> read-back verification`.
The end state is a reliable, safe phone-only agent on an **unrooted** Samsung S26 Ultra / Android 17; Wi-Fi, ops/USB, and the Project IR Hub must not be required for *local* speech/model/Accessibility execution. Remote Hub connectivity is an independent optional channel.

**Non-goal:** claiming root capabilities, Android Device Owner authority, actual NPU delegation, kernel writes or persistent ADB merely because an option, switch, API name, log, notification or simulated success indicates them. Reject novelty without an observable improvement over ordinary Shizuku + Accessibility + documented Android APIs.

## What is already known (do not rerun as if unknown)

- ShizukuPlus `af.shizuku.plus.api` installed; protected user-selected options listed below; no root. Stock Shizuku kept as fallback.
- With the server already running, Shell `uid=2000` and Wi-Fi OFF succeeded. Earlier 10/10 independent tests were successful. This is **runtime persistence after Wi-Fi loss**, not cold-boot self-start.
- Controlled reboot with Wi-Fi OFF failed to restore the Shizuku server automatically; `Settings.Global.adb_tcp_port=5555` survived but `adbd` did not listen after boot. Setting was reverted. mDNS port discovery timed out. **Cold boot remains HOLD**.
- ShizukuPlus Watchdog/boot receiver could be active with no usable Binder; an `AdbStartWorker` notification persisted after the worker completed. Never infer actual health from notifications.
- The main Agent now checks Binder, permission, UID and UserService separately from Hub state. `Hub HTTP401` was independently identified. Testing ShizukuPlus must not change Hub credentials.
- Previous `dpm list-owners` observed a managed-profile owner for user 10, **not a confirmed primary-user Device Owner**. An enabled Dhizuku UI toggle does not change that.
- Hardware acceleration and all extra ShizukuPlus AI integration interfaces remain **unverified**; the existing Qwen3-ASR local inference result is not proof of NPU execution.

## Unchanged user configuration — hard constraint

Keep these EXACT option states as the user selected; **do not toggle any of them as part of planning**:
Shizuku+ Enhanced API, Binder Firewall, Continuity Bridge, Package Governor, Shell Accelerator, VM Manager, Storage Bridge, Dhizuku Mode, Shadow Binder, Stealth Mode, Firewall Success Mocking, BusyBox Simulation, OverlayFS System Ghosting, Kernel Driver Ghosting, Partition Flash Ghosting, Power State Ghosting. Also **keep all existing AI-section options enabled** (including AI Core / Bridge where present) for a future controlled evaluation.

DNS Governor, Process Control, Status Bar Control and Local ADB Proxy Server were previously turned OFF; do not change them. Keep the current boot/Watchdog/auto-reconnect behavior unchanged until a specific approved A/B test and rollback exist.

## Prioritized experiments

Status for each item is `PENDING`; any prior evidence above is baseline, **not** a pass for a new experiment. Use the actual installed version's source/feature help and Android API grants to confirm the feature exists and what it does **before** invoking it.

| ID / priority | Feature(s) | Hypothesis / measurable benefit | Safe planned test / pass gate |
| --- | --- | --- | --- |
| **S+01 P0** | Start on Boot; Intelligent Auto-Reconnect; Watchdog; Keep-Alive / Device Hardening; ADB TCP Mode | Server Binder and real Shell recover after cold boot *without* PC, manual Start or Wi-Fi. | First inventory boot receiver, process and binder transitions using logs. Only in a separately approved reboot window: Wi-Fi-off reboot; collect boot-id, elapsed time, whether user manually interacted, live binder ping and real `id -u`. **PASS only if real UID 2000 returns automatically without USB/manual Start**. Current baseline FAIL. |
| **S+02 P0** | Shizuku+ Enhanced API; Shell Accelerator | Lower `shell` action latency and more robust dispatch than existing UserService; no privilege escalation. | Identify versioned API surface; replay 100 read-only operations through ordinary UserService vs enhanced path, same commands and load; record verified identity, 99th-percentile errors, p50/p95 latency, CPU and lost-bind counts. Require zero false successes and meaningful repeatable improvement (>15% p95 or recovery improvement) to integrate. |
| **S+03 P0** | Binder Firewall; Shadow Binder | Detect compatibility/filtering failure without corrupting healthy Binder state. | **Observe current effective rules/read-only diagnostics first**; exercise allowed and intentionally denied harmless calls, compare local Agent vs DroidBridge. Never weaken filtering, change rules, or equate mock replies with side effects. Fail closed and preserve log of actual UID. |
| **S+04 P0** | Continuity Bridge; Storage Bridge | Preserve local task state and permit safe scoped file operations during Hub, network, or Binder outages. | Confirm real interfaces; use **app-owned temporary files only** and a synthetic resumable task; disconnect network (not USB unless separate recovery proven); compare journal checksum, recovery time, no cross-app data access or data loss. |
| **S+05 P1** | Window Hierarchy Crawler; AI Core / AI Bridge | Improve element discovery for deterministic on-device UI automation; not increase permissions. | Collect identical hierarchy snapshots through Accessibility/DroidBridge and ShizukuPlus on four consented non-sensitive screens. Compare coverage, stale-node rate, latency, memory, permission prompts and false target matches. Require **zero wrong-target clicks** in read-only discovery and then explicit reversible canary. |
| **S+06 P1** | Experimental AI Automation | Safely let local LLM *propose* actions while policy router verifies allowlisted actions. | No autonomous unrestricted command execution. On local non-sensitive fixture: proposed plan -> policy gate -> dry-run -> reversible action -> independent after-state; require 100% rejection of denied/ambiguous requests and no action on uncertain targets. |
| **S+07 P1** | NPU Acceleration; AI Bridge & NPU; AI Core | True hardware acceleration of local LLM or Qwen3-ASR might reduce latency/power. | First identify exact runtime backend/delegate and compatible model & SoC. Same frozen inputs, 10+ warm trials for CPU/GPU/NPU (only where *measurably supported*); compare accuracy, model load, time-to-first-token, tokens/s or ASR RTF, power/thermal/memory; require independent delegate evidence, not a switch label. |
| **S+08 P1** | Package Governor | Maintain availability of the Agent under battery policy without sacrificing OS security. | Read-only package/app-op/standby/idle inventory and lock/unlock/Doze measurements; do not alter package enablement, ownership, battery exemptions or app-op grants. If potential benefit, define an explicit reversible separate change request. |
| **S+09 P1** | Activity Log / Diagnostics / Display Tuner (if present) | Produce trustworthy trace correlated with actual Action Router results. | Cross-correlate `boot-id`, Binder generation, operation ID, real UID, kernel uptime, effect read-back, error cause and AI backend. Display tuning should be tested only on non-sensitive UI and must not be used as proof of system privileges. |
| **S+10 P2** | Dhizuku Mode / Device Owner | Whether independently verifiable *existing* managed/profile-owner capabilities are useful. | Read-only `dpm list-owners` and actual user/profile scope; test harmless supported APIs only if authority is conclusively present and explicitly approved. **Do not provision/transfer Device Owner, remove profiles, or use factory reset**. |
| **S+11 P2** | VM Manager | Whether an actual Android virtualization/container interface improves isolation for model tools. | Read advertised APIs and privileges only; demand a working non-root fixture and measured memory/power/boot overhead before treating as architecture candidate. Otherwise document unsupported and stop. |
| **S+12 P3** | Stealth Mode | May suppress informative diagnostics or cause compatibility regressions, not a core agent capability. | Documentation/source inspection only; compare observable errors and logging passively. Do not tune stealth to evade app security checks or hide unauthorized access. |
| **S+13 P3** | Firewall Success Mocking; BusyBox Simulation | *Negative testing* of whether the Action Router mistakenly trusts simulated command success. | Isolated, harmless read-only fixtures; mark responses `MOCK`/unverified; require a real read-after-write gate and fail-closed detection. **Never use simulated result as success evidence**. |
| **S+14 P3** | OverlayFS System Ghosting; Kernel Driver Ghosting; Partition Flash Ghosting; Power State Ghosting | Typically no practical gain for local ASR/LLM; simulation may create false beliefs about root or reboot. | **Do not invoke destructive-looking operations on the live phone**. Inspect implementation offline / fake fixtures only. Distinguish real `/proc/sys/kernel/random/boot_id` change from simulated reboot. Prefer exclusion from production action router. |

### Recommended implementation order

`S+01 -> S+02 -> S+03 -> S+04 -> S+05 -> S+06 -> S+07`.
The first four establish dependable execution and truthful effects; AI/NPU features have little value while privileged start and action verification are unreliable. Keep low-value/ghosting features out of the production action API by default.

### Shared acceptance protocol for future runs

1. **Preflight** (read-only): installed ShizukuPlus APK version/hash; requested feature name in actual installed UI/source; Android SDK/build; battery/thermal; boot-id; current user/profile; foreground and permission state; live `Shizuku.pingBinder`; real `id -u`; connected client list. Record a timestamp and baseline result. No UI or feature switches altered merely for preflight.
2. **Safety**: snapshots / recovery path on ops before any app/binder-affecting test; distinguish `APP`, `ACCESSIBILITY`, `SHELL_2000`, `PROFILE_OWNER`, `DEVICE_OWNER`, `MOCK`. A return code 0, UI toggle or mocked success is insufficient; every side effect needs independent read-back.
3. **Isolation**: no changes to passwords/secrets, networking/routes, production apps, default Assistant, model weights, managed profile, ShizukuPlus AI settings, or root/kernel/partitions. For tests requiring an A/B toggle, destructive restart, reboot or binding-impacting changes, create a separate reviewed experiment and explicit rollback gate first.
4. **Data**: at least 30 samples for basic latency comparisons (100 for reliability), identical commands/prompts and OS conditions, report p50/p95, error categories, memory/thermal/power where measurable, and whether Wi-Fi/USB/manual start was necessary. Do not fabricate NPU proof from observed speed.
5. **Stop conditions**: binder availability loss with no safe recovery, unexpected user/profile privileges, unrelated UI action, data loss, mismatch between claimed and real effect, or unexplained network/debug port exposure. Preserve a diagnostic receipt and stop; never keep retrying privileged mutations blindly.
6. **Decision**: `ADOPT` only for measured value + safe API + rollback + no false success; `REJECT` for mocks without real benefit/risk too high; `DEFER` for missing capability, source evidence, transport or model backend.
7. **Receipt format**: `experiment_id`, repo commit, installed package/version, boot-id, initial/final Binder & UserService state, effective UID, feature config **before/after unchanged check**, command/fixture checksum, attempts, success count, p50/p95, full error taxonomy, read-back proofs, device owner/profile scope, rollback status, final decision. Store no PII, credentials or sensitive UI screenshots.

## Explicitly deferred

- Cold-boot no-root independence: current FAIL; may ultimately require accepting manual/wireless debugging bootstrap or replacing privileged functionality with Accessibility/ordinary APIs. Do not promise automatic Shizuku startup on Samsung.
- Modifying ShizukuPlus fork, including clearing orphan `AdbStartWorker` notification, belongs to a **separate provider-source patch and independent release** if value warrants it. Agent code cannot reliably cancel another app's foreground notification.
- Hub HTTP401 authentication repair is unrelated to ShizukuPlus feature testing and remains a separate secure credential workflow.
- No ShizukuPlus feature in this backlog is an established substitute for a real device-side local LLM runtime, secure policy engine or model delegate.

## Gate for next session

Before executing any new experiment: re-read this backlog and `S26_SHIZUKUPLUS_BOOT_AI_TEST_PLAN_20261022.md`, check live running service / active chats, and select exactly one bounded experiment with a known recovery strategy. **This file authorizes planning only, not changing preserved settings.**
