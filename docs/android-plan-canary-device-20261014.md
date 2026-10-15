# Isolated S26 Android ART canary — 2026-10-14

Repository branch: `feat/s26-plan-adapter-20261014`
Android phone: Samsung SM-S948B / Android 17
App package: `io.projectir.plan.canary` (not the production Project IR agent)
Commit of original offline plan implementation: `0e650d3`

## Outcome: GO for on-device simulated engine and persistent recovery; live UI execution HOLD

The isolated diagnostic APK compiles canonical `LocalPlanMachine`, `LocalPlanCoordinator`,
`LocalPlanJournal` and their dependency-only pure-Java classes into a separate
non-foreground, shell-callable Android `ContentProvider`. It has no network,
microphone, Shizuku, Accessibility or boot receiver permissions. It has no
launcher Activity and does not issue any physical Android UI actions.

The original Agent is `io.projectir.mobile`. This app was neither updated,
force-stopped, restarted nor reconfigured by the canary.

### Verified on the actual phone

- Build `assembleDebug lintDebug`: PASS (independent canary package).
- APK `io.projectir.plan.canary`, size 2,556,508 bytes, SHA-256
  `f3a4ef6cfa0ff873f26c259acaa9c28bfae068708c0772b7d343fe0398c3edbe`.
- First installation `adb install --no-streaming -r`: PASS.
- In the phone's Android Runtime, 100/100 **simulated** multistep plans:
  all finish with the expected journaled postcondition and exactly one
  modelled launch and click. Real UI effects: **zero**.
- Synchronize a journal state `PREPARED` to Android SharedPreferences,
  force-stop **only the canary app**, then re-enter its ContentProvider:
  `INTERRUPTED_UNKNOWN` persisted and duplicate action ID rejected. PASS.
- A subsequent idempotent re-run also passed 100/100 ART simulations;
  it observed the previously recovered ledger and did not count as another
  fresh force-stop durability test.
- The original Project IR Agent PID and Shizuku Server PID were unchanged
  between pre/post snapshots inside each canary test. No reboot, network
  setting, Wi-Fi or main app permission was changed.

On an earlier attempt that day ADB returned `no devices/emulators found`.
It recovered subsequently; no Canary APK was installed during that attempt.

### Important distinctions

These results validate compiled shared engine code on the actual Android
ART/Java/SharedPreferences stack, NOT the new real Accessibility adapter.
They do not establish Wi-Fi independence over hours, live UI success rate,
background-launch reliability or cross-process production resilience.

The independent canary isolates execution from the current Project IR Hub
and the already-installed Project IR app. Do not mistake 100 synthetic
effects for 100 real clicks.

### Read-only UI state stability check

The installed Accessibility provider was sampled 20 times through USB ADB
without issuing any action or persisting screen contents. Results:

- 20/20 usable snapshots and 0 truncated trees.
- 47 nodes in each observed snapshot.
- 1 distinct normalized UI state over 20 samples;
  19/19 consecutive comparable pairs were identical.
- No UI click, input, global action or Wi-Fi change was requested.

This is only **short-window UI observation**. It does not establish
dynamic-screen robustness, stable accessibility node IDs across navigation,
or the safety of a subsequent actual click.

Re-run with `android/plan-canary/scripts/ReadOnly-SnapshotStability.py`
on Ops when one USB device is authorized. Only aggregate counters are
reported; text and state digests remain in RAM.

### Shizuku read-only diagnostic

When the phone was inspected after this test, Shizuku Server was running
as the Android `shell` user, and the Project IR package declared the
Shizuku permission `moe.shizuku.manager.permission.API_V23: granted=true`.
The remote S26 heartbeat **still** reported
`shizuku_running=false`, `shizuku_permission=false` and
`shizuku_user_service_ready=false`.

This points to an unresolved binder/client readiness mismatch, not to
permission absence proven by package metadata. The Shizuku PID later differed
from an earlier observation; the cause was not established. Nothing was
restarted as part of this diagnostic.

Read-only Accessibility `state` via the **installed** Project IR bridge
returned a root-available, node-count-bearing snapshot; no screen contents
were exported into this evidence.

### Explicit next gates

1. Read-only diagnose Shizuku binder delivery/client registration and
   correlate status with Wi-Fi state under a controlled test window.
2. Defer upgrading the installed main Agent until other Project IR chats
   can tolerate the foreground-service restart; don't disrupt a live agent.
3. Run real native Accessibility click, current-state pinning, cancellation
   and UI postcondition canary after controlled APK update.
4. Test prolonged Wi-Fi off and USB physically detached; reboot remains
   deferred. Shizuku must be optional for ordinary user tasks.
5. Compare Persian ASR models using real consented utterances, not only the
   existing synthetic transcript corpus and evaluator tests.

## Reproduction

On authorized Ops PowerShell:

```powershell
& 'C:\GitHub\project-ir-s26-plan-20261014\android\plan-canary\scripts\Build-Canary.ps1'
& 'C:\GitHub\project-ir-s26-plan-20261014\android\plan-canary\scripts\Run-Canary.ps1'
```

The second command requires one authorized USB Android device and installs
only `io.projectir.plan.canary`, then force-stops only that app to verify
durable recovery. It does **not** restart `io.projectir.mobile`, Shizuku
or the Hub. An already consumed test ID will cause `ALREADY_PREPARED`;
for an entirely fresh durability trial use a new test ID in the canary
provider and rebuild.
