# Reliable Executor v1b — Replay/Cancel checkpoint

Date: 2026-10-13
Branch: `feature/android-agent`
Delivery: **source-validated; device-install HOLD**

## Implementation

- Optional Action ID field (stable resource ID `local_action_id_input`) in
  the existing Local Text Agent UI. Blank ID retains automatic UUID behavior.
  Entering the same accepted ID twice attempts an explicit replay.
- Reject re-used IDs with `REJECTED_DUPLICATE` without dispatching a new task,
  changing the last accepted ID, or expanding the journal.
- A safe five-second local timer command (`wait five seconds` /
  `پنج ثانیه صبر کن`) to provide a real cancellable in-process task.
  It changes no system settings and sends no network requests.
- The previously implemented `Cancel local task` invalidates pending task
  callbacks; it does not undo a previously launched Android activity.

These additions do not grant a model arbitrary shell access and do not
depend on Hub/Shizuku to interpret or execute the local task.

## Verified evidence

- Pure-Java command router: **17/17 PASS** (including both wait phrases).
- Pure-Java bounded action-ID journal: **17/17 PASS**.
- Project IR Python suite: **52/52 PASS**.
- Android debug build and lint: **PASS**, 2026-10-13, using known JDK/SDK.

## Safety gate / reason for HOLD

During deployment, the remote execution tool refused the APK signing/install
request with an OpenAI safety block. No alternative route was attempted.
The APK on the phone was not replaced during this increment.

**Do not claim live replay or live cancellation PASSED.** The new phone test
script in the research repo is prepared, syntax-checked, and pending deployment:
`scripts/m4-v1b-replay-cancel.py`.

The prior deployed version v1 (`47575d7`) remains the last proven APK.
The new source must be installed through a permitted, authorized mechanism
before running the device tests. No reboot is needed or planned.

## Deferred

- Live repeat of identical Action ID.
- Live Cancel during the five-second local timer.
- Verification that no completion callback fires after Cancel.
- Repeated 100+ low-risk ops; Scroll primitive remained excluded because
  its earlier edit request was blocked.
- USB-disconnected and reboot restoration acceptance.
