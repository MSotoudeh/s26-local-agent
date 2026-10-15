# S26 real Android plan trial — 2026-10-15

Scope: user-authorized APK update, real Settings navigation, and follow-up bounded-parent selector fix.

## Verified live stage 1 (installed)

- Authorized ADB USB connected to Samsung SM-S948B.
- Existing main Agent original APK: read-only hash and signing match verified against existing backup.
- APK assembled/linted on Ops, user-matching signing certificate verified, installed as
  a data-preserving update (`adb install -r`; no uninstall/data clear).
- MainActivity reopened; same app's foreground service and Accessibility bridge were operational.
- Project IR Hub reported S26 connected with Shizuku running, shell UID 2000, permission
  granted, and UserService ready after the Agent app restarted.
- One actual user-level R0 plan was started through the app's Accessibility UI:
  command `open connections`, task ID `s26_live_settings_connections_20261015_001`.
- Real Settings app opened, but **no click occurred**: the persistent plan journal recorded
  `FAILED` / `SELECTOR_TARGET_NOT_ACTIONABLE` at step 2.
- Native Accessibility snapshot showed one exact `Connections` node:
  text node BFS ID 93, parent 63, clickable enclosing LinearLayout BFS ID 43.
  The leaf is enabled but not clickable; the parent container ID 43 is enabled
  and clickable. The Android action policy correctly refused to guess a target.
- A fresh Hub heartbeat after deployment confirmed Shizuku binder usability.
  Root cause of earlier binder unavailability remains unproven.

## Proposed bounded actionable-parent fix

Source worktree is `C:\GitHub\project-ir-s26-plan-20261014`
on branch `feat/s26-plan-adapter-20261014`.

Updated the pure Java selector and the actual Android Accessibility effect revalidation:
1. Preserve BFS parent ID in observed semantic nodes.
2. Require one exact visible label and an untruncated snapshot in the expected foreground package.
3. Choose the exact clickable text node, or at most 3 same-package, enabled, text-compatible
   ancestors along its observed parent chain.
4. Before effect, re-read the live root tree, resolve the exact same selected actionable
   BFS ID and guard the expected foreground package; never click coordinates or an unbounded ancestor.
5. Reject duplicate labels, missing/cyclic/deep or disabled parents, cross-package parent
   chains, stale UI digest and incorrect action IDs.

Verification complete before second deployment:
- `LocalBoundedAncestorTest`: 1,000 fixtures / 17,000 assertions PASS.
- Existing Java host suites: LocalActionIdentity 17, CommandRouter 17,
  LocalPlanning 48, LocalPlanMachine 15,022, LocalPlanCoordinator 9,015 PASS.
- Project IR Python suite: 52/52 PASS.
- Android compile/lint of bounded-ancestor changes: PASS.

## Live follow-up and timing diagnosis

The first bounded-parent build passed its 17,000 new ancestor-selector
assertions, plus existing JVM and Python regressions, and Android Build/Lint.
The installer preserved the original Agent's settings and the active Hub and
Shizuku UserService both reconnected after replacement.

- Task `..._002`: FAILED at `STALE_STATE_AT_DISPATCH` (UI digest changed
  before effect; no click authorized).
- A 20-sample subsequent resting Settings snapshot: 20/20 complete,
  19/19 stable neighbor pairs, 119 nodes, no UI effects.
- A new 2-snapshot/250 ms quiet-period observation gate was introduced
  before UI selection; tests: 1,000 fixtures / 20,000 checks PASS.
- Task `..._003`: **SUCCEEDED / VERIFIED_CANONICAL_PLAN** on the actual S26,
  finishing in `com.android.settings/.SubSettings`.
- Repeated task `..._004`: **SUCCEEDED**.
- Repeated task `..._005`: **FAILED / STEP_TIMEOUT**, step index 3
  (read-only VERIFY_TEXTS). A subsequent read-only snapshot of SubSettings
  showed exact Connections, Wi-Fi and Bluetooth labels present, suggesting
  the destination was correct but verification was gated too strictly.
  No automatic action replay was performed.

## Final bounded R0 navigation result: GO on verified target

The final source fix retains strict settling for package arrival and
pre-dispatch click selection, then reads full, non-truncated Accessibility
snapshots immediately during the **read-only** `VERIFY_TEXTS` step.
The canonical verification timeout is now bounded at 5 seconds.

**The current built and installed Agent completed 3 of 3 consecutive real
Settings -> Connections workflows** with distinct task IDs `..._006`,
`..._007` and `..._008`. Every one ended in a durable journal:

- `plan.status=SUCCEEDED`
- `plan.reason=VERIFIED_CANONICAL_PLAN`
- Foreground target was the Samsung Settings Connections page.
- No invented coordinate clicks, shell clicks, Hub or Shizuku calls were
  made on the local execution path.

Results for the entire development/debugging series are **not** 8/8:
`..._001` refused an unactionable leaf, `..._002` refused stale
pre-dispatch UI, `..._003` and `..._004` passed, `..._005`
timed out during late read-only postcondition observation, and the current
version passed `..._006` through `..._008`. Failures were recorded
and never blindly retried with the same action ID.

### Final checks

- Android assembleDebug + lintDebug: PASS.
- Existing Project IR Python tests: 52/52 PASS.
- JVM action identity 17, router 17, planning 48 PASS.
- LocalPlanMachine 15,022 assertions / 1,000 simulated plans PASS.
- LocalPlanCoordinator 9,015 assertions / 1,000 simulated plans PASS.
- LocalBoundedAncestorTest 17,000 assertions / 1,000 fixtures PASS.
- LocalObservationGateTest 20,000 assertions / 1,000 fixtures PASS.
- APK build SHA-256: `11660ff0be15388a3f8a99c1e0883e99c78c2a180ecd498a81e8934eb876fe30`.
- Installed using data-preserving Android package update and matching
  signer. Original Project IR ForegroundService and Accessibility operational.
- Project IR Hub reconnected, and reported Shizuku running, UID 2000,
  permission granted and UserService ready after app restart.
- Rollback APK of the immediate previous version was verified and stored
  on Ops as `20261015-before-postcondition-fix.apk` with SHA-256
  `25570cfc71cbe3914134a1669191d88e459ac1ac81337b50806f52852a5df40f`.
  Older backups are retained separately.

### Remaining acceptance gates (HOLD)

This is a **limited, explicitly allowlisted, on-device R0 navigation GO**,
NOT general autonomous control or a demonstrated 99% success rate.
Samsung Settings is the sole multi-step live target validated by this
sequence. Live cancel races, dynamically changing target UIs, screen lock,
independent extended offline operation, Wi-Fi-off persistence, USB-detached
operation, post-reboot start, Persian speech recognition, local LLM/NPU,
and Android Assistant-role integration remain unverified.

No phone reboot, global Wi-Fi toggle, Shizuku server restart or Hub
deployment was performed as part of this validation.
