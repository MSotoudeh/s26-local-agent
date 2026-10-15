# S26 offline Android plan adapter - pre-install checkpoint

Date: 2026-10-14
Branch: feat/s26-plan-adapter-20261014 (isolated Project IR worktree)
Base: feature/android-agent @ 44ba159
Status: SOURCE/WITHOUT-PHONE-EFFECTS; live Android integration acceptance HOLD

## Scope

The existing `LocalPlanMachine` was a pure-Java state machine without an Android
execution path. This change adds `LocalPlanCoordinator`, a synchronous write-ahead
`LocalPlanJournal`, an on-device `LocalAndroidPlanRuntime`, a deliberately
restricted Accessibility semantic action, and explicit UI entry/cancel controls.

The route is: explicit local typed input -> canonical registered R0 plan ->
one-main-looper coordinator -> current Accessibility snapshot -> durable prepare
-> guarded dispatch -> durable result -> machine ACK -> separate postcondition
observation -> terminal journal outcome.

No shell commands, Shizuku, Hub, Wi-Fi, microphone, external service or model
call is required by the plan runtime. Unsupported / unvalidated skills fail
closed. Each plan must be started by explicit user action; the app never
silently resumes an interrupted task.

## Crash and safety contract

- An opaque action ID is claimed synchronously before any side effect.
- Every effect is first recorded as PREPARED; return values are recorded as
  APPLIED or UNCERTAIN before the state machine ACK.
- Recovering RUNNING/PREPARED/APPLIED on process initialization marks
  INTERRUPTED_UNKNOWN and never dispatches pending effects automatically.
- The plan engine does not retry ambiguous effects; status must be separately
  confirmed by live observation.
- App activity launches are a fixed package-to-component allowlist; clicks
  require exact expected package, current snapshot digest, stable Accessibility
  events, the same selected BFS node ID, unique text, enabled+clickable state,
  and no truncated tree. No coordinate tapping or parent fallback.
- Only opaque IDs, step numbers and safe status/reason codes are journaled.
  UI trees, spoken/typed text, selectors and personal content are not saved.
- All scheduling occurs on the main looper. Cancel prevents future steps; it
  cannot roll back a completed Android action.

## Verification

Host JVM coordinator/journal suite:
- 1,000 simulated complete plans, 9,015 assertions, zero phone effects.
- Injected crash/recovery, duplicate ID, prepared effect, stale UI, durable
  prepare failure, cancellation and successful multistep postconditions.

Validation on Ops:
- Android assembleDebug + lintDebug: PASS (Gradle 9.6, offline, with
  GRADLE_USER_HOME pointing to the existing desktop user's Gradle cache).
- Existing LocalPlanMachineTest: 15,022 assertions / 1,000 plans PASS.
- New LocalPlanCoordinatorTest: 9,015 assertions / 1,000 plans PASS.
- Existing LocalActionIdentityTest: 17 PASS.
- Existing LocalCommandRouterTest: 17 PASS.
- Existing LocalPlanningTest: 48 PASS.
- Python suite from this worktree (correct PYTHONPATH): 52/52 PASS.
- Java compilation emitted seven non-blocking missing-annotation warnings.
No new APK installed; reboot, manual tests, and Wi-Fi-off endurance
experiments remain deferred.

## Known residual risks and HOLD gates

1. Android Accessibility provides no truly atomic UI snapshot+click guarantee;
   a target app may update its view tree during two calls. A device-based
   canary must show reliability and cancel behavior before a production GO.
2. The app is not yet updated on the phone. A source/build PASS is not a live
   Android PASS.
3. Source now mutually excludes concurrent old LocalTaskRunner and new
   multi-step runtime, and rejects reuse of action IDs across the two ledgers.
   Device canary still has to confirm the race behavior.
4. Shizuku process presence != Binder readiness. On 2026-10-14 the S26 hub
   heartbeat reported Shizuku unavailable while Android's Shizuku server PID
   existed. This mismatch remains a distinct, unmodified diagnostic issue.
5. Non-root Shizuku cannot be assumed to self-start after reboot. Shell-only
   skills are optional; local Accessibility capabilities must degrade
   independently of Wi-Fi, network, or Shizuku.
6. Verify Android foreground activity-launch restrictions, screen-lock,
   interruption, reboot recovery, power usage, latency and at least one
   repeated real S26 Settings/Connections canary before release.

The active Project IR Hub, installed agent, Shizuku server, Wi-Fi state and
other worktrees have deliberately not been restarted or modified.
