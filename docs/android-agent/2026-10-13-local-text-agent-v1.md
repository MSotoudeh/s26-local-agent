# Reliable Local Executor v1 — action identity and cancellation

Date: 2026-10-13
Branch: `feature/android-agent`
Status: limited on-device Action ID smoke GO; full Cancel/Scroll acceptance HOLD

## Scope and dependencies

This increment extends the existing offline `LocalTaskRunner` within
`io.projectir.mobile`. The command allowlist and the Android system
Accessibility boundary remain unchanged. The task runtime does not invoke
Project IR Hub, Shizuku, a generic shell, cloud services or an LLM.

Implemented:
- A caller-provided action ID overload and automatic UUIDs for explicit UI submissions.
- A bounded 64-ID on-device journal persisted synchronously before dispatch.
- Rejection of invalid or previously-seen IDs within the journal window, with no action replay.
- A **Cancel local task** control which invalidates outstanding asynchronous poll callbacks.
- Recovery of stale persisted `RUNNING` state as `INTERRUPTED` after app process recreation.
- Display of the last action ID alongside local status.

## Cancellation semantics

Cancel prevents future scheduled steps from the current task. It does **not**
roll back an Intent launch or Accessibility click which was already
dispatched. The UI status explicitly reports this limitation.

This is cancellation of the local task state machine, not cancellation of an
Android system API call or another application's activity.

## Safety and idempotency boundaries

- Journal stores opaque IDs only, not user text, screen contents or credentials.
- User-initiated button presses generate fresh UUIDs; they are distinct actions.
- Replayed caller-supplied IDs within the last 64 accepted IDs are rejected.
- The synchronous commit precedes dispatch and refuses to act if it fails.
- The journal is bounded, not a lifetime global replay registry. An ID evicted
  after 64 newer IDs may be accepted again; cross-device and distributed
  idempotency are outside v1.
- A kill between dispatch and verification can leave an Android side effect
  without a verified final result. The existing journal prevents re-use of
  its ID until eviction; `INTERRUPTED` does not imply rollback or failure.
- The existing fixed Settings/Connections selector is retained; scroll,
  multi-app side effects and screen fingerprints are **not** enabled here.

## Tests and limitations

- Pure Java `LocalActionIdentityTest`: 17 assertions PASS, including ID
  validation, deduplication, journal eviction and 1000 insertions.
- Existing Persian/English parser: 15/15 assertions PASS.
- Project IR Python regression: 52/52 PASS.
- Android `assembleDebug` and `lintDebug`: PASS after the final Java change.
- APK signed with the existing installed-app signing identity; signature
  compared and `adb install -r` succeeded without data removal.
- On-device UI had 3/3 required controls: command input, Run and Cancel.
- 30 consecutive local `DEVICE_STATUS` UI trials: 30/30 PASS_VERIFIED,
  with 30 distinct Action IDs and bounded on-device journal checks.
  Measured internal task duration: 0-2 ms for these low-risk status commands.
- Settings -> Connections: first test run 3/5 before an ADB test-harness
  command timed out; that failure is preserved in the research evidence.
  With the host ADB timeout increased, **5/5** sequential repeat trials
  passed exact destination labels and distinct action IDs. Individual
  local-task times were 460-538 ms.
- Device foreground restored to Launcher after each batch; Hub later reported
  connected and Shizuku UserService ready.

Device-level **Cancel during active execution** and **replay of an identical
caller-provided action ID on Android** remain untested; the former is only
implemented/compiled, the latter verified by pure-Java journal tests.
Scroll was deliberately not incorporated after a blocked Project IR edit.
100-operation multi-step reliability, physical USB disconnect and reboot
recovery remain HOLD. No reboot was performed.
