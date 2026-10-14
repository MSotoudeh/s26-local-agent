# S26 — M9 offline multi-step Plan Machine (no manual tests)

Date: 2026-10-14
Canonical Android branch: `feature/android-agent`
Device: Samsung S26 Ultra
Status: **pure-Java simulated task engine GO; device integration HOLD**

## Context recovery

The immediately preceding tool call returned an HTTP 502 while attempting
to synchronize the synthetic speech evaluator test count in four Markdown
files. The current state was independently inspected before editing.

Both repositories were already clean, at Android commit `6555a1b` and
research commit `6757be6`, with the metric self-test count corrected to
9/9. No duplicate edits or commits were made for that interrupted operation.
No concurrent durable Project IR runs were found in the initial audit.

## Implemented M9 reference machine

New `LocalPlanMachine.java` in the canonical Project IR Android feature
worktree is a pure-Java reference implementation of an offline bounded
multi-step state machine. It consumes **only canonical validated R0
skills**, returning data-only Dispatch requests to a future Android
execution adapter. The reference machine itself has no Android, Shizuku,
Hub, shell, network or LLM side effects.

- Explicit states: READY / RUNNING / WAITING_ACK / SUCCEEDED / FAILED /
  CANCELLED.
- Task ID and one step-level request ID per effect.
- Dispatch-once semantics per step: no re-emission while waiting for ACK.
- Timed steps; fail closed on missing ACK, uncertain effect, clock reversal
  and unknown step outcomes.
- Pre-dispatch UI state identity pinned for semantic click requests.
- On ACK, the engine advances but must still verify destination
  package and expected text labels before reporting success.
- Cancel and terminal states cannot later resume from an old ACK.

## Host-side evidence

`LocalPlanMachineTest.java` executed **1,000 synthetic Settings →
Connections plans** with explicit launch, package check, unique-label
selection, dispatch acknowledgment, destination labels and final report.
Additional fault injection covers duplicate/mismatched/late ACK, effect
rejection, cancellation, timeout, stale UI state, wrong app, ambiguous
target and unverified skill.

Results:

- `SIMULATED_SUCCESSFUL_MULTISTEP_TASKS=1000`
- `LOCAL_PLAN_MACHINE_TESTS_PASS=15022`
- `SIDE_EFFECTS_PERFORMED=0`

These results verify state-machine code, not real Android success rates.
There was no installation or live task performed on the S26.

## What remains before a real agent

- Implement Android adapter with fresh package + node + state verification
  right before a permitted action; never trust a model-supplied coordinate.
- Persist a step-level transaction journal across process death, and
  carefully distinguish `acknowledged`, `verified` and `uncertain`.
- Tie cancellation to each asynchronous effect and define no-replay policy.
- Record a representative sanitized UI-state fixture set, including
  changing and scrollable screens, before enabling general UI actions.
- Add controlled planner evaluation and model comparison using an
  independently held-out dataset; no trained model was deployed here.
- Test real phone actions and USB-detached operation when user intervention
  is unnecessary. Reboot remains deferred by request.

See also `project-ir/docs/offline-plan-machine.md`.

## Final validation and reproducibility

The first build of the new engine completed `assembleDebug` and
`lintDebug` successfully on Ops (Gradle: BUILD SUCCESSFUL). The existing
Project IR regression suite passed **52/52** Python tests, and the Persian
ASR/intent *evaluator's own arithmetic* passed **9/9** Python tests.

A standalone, repeatable host test was added:

`scripts/m9-offline-plan-benchmark.ps1`

Its JSON evidence is saved at:

`benchmarks/m9/20261014-offline-machine.json`

The recorded result was `PASS_SIMULATION_ONLY`, with 1,000 simulated
successes, 15,022 assertions, zero actual Android effects and a measured
test-method runtime of 436 ms **on the Ops host**, not on the phone.
Do not treat the host test duration as mobile task latency.
