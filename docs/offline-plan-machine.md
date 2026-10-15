# Local Plan Machine — bounded multi-step reference runtime

Date: 2026-10-14
Branch: `feature/android-agent`
Status: **source-validated / simulation GO; live Android execution HOLD**

## Intent

Provide a local, deterministic and independently testable state machine
between the canonical Skill Registry and a future Android execution adapter.
The class is pure Java. It has no Android, Shizuku, Project IR Hub, shell,
network, microphone or model calls.

The machine accepts only canonical, previously validated R0 plans via
`LocalPlanPolicy.mayExecute`, with a syntactically valid opaque task ID.
It refuses candidate-only skills such as unverified Battery navigation.

## State and events

States: READY, RUNNING, WAITING_ACK, SUCCEEDED, FAILED, CANCELLED.

Requests are **data** with a unique `taskId_stepNumber`, canonical step kind,
expected package, chosen node ID and pre-dispatch state ID. They are not
executed by the engine. An independent adapter would have to revalidate
the package/state and dispatch the requested effect before calling
`acknowledge`.

An ACK never means the task has succeeded; later observation and explicit
VERIFY_TEXTS / AWAIT_PACKAGE steps must establish postconditions.

Core guarantees within one in-memory task instance:

- No duplicate Dispatch event while a request awaits ACK.
- Side effects are not blindly retried following an ACK timeout or an
  uncertain/failed dispatch.
- Incorrect or stale UI state IDs and mismatched action ACKs are refused.
- Every step has a strict timeout inherited from the canonical plan.
- Time must be monotonically nondecreasing; clock reversal fails closed.
- Cancellation drops any outstanding request and prevents future progress.
- Terminal states are stable against repeated `advance` or late ACK calls.
- An ambiguous, non-clickable or incomplete UI state is not guessed through.

## Benchmarks

Host-only `LocalPlanMachineTest` drives 1,000 complete synthetic
Settings → Connections workflows, with exactly one simulated launch and
one simulated semantic click per plan, followed by explicit package and
destination-label verification. It also injects failures and checks safety
invariants for wrong/duplicate/late ACK, uncertain effect result, cancellation,
step/ACK timeout, stale or wrong-package observation, ambiguous target,
nonmonotonic time and unvalidated skill. No phone action is performed.

Observed in the host-only JVM test:
- `SIMULATED_SUCCESSFUL_MULTISTEP_TASKS=1000`
- `LOCAL_PLAN_MACHINE_TESTS_PASS=15022`
- `SIDE_EFFECTS_PERFORMED=0`

Do **not** interpret these as measured phone reliability or service
availability. They validate the state-machine logic under synthetic
observations and modeled failures only.

## Critical deployment gates before real Android use

1. Implement an Android execution adapter that binds a request to one
   policy-authorized intent or a verified Accessibility action, and that
   revalidates the *current* package, node identity and state immediately
   before a mutating click.
2. Maintain an on-device durable transaction journal across process deaths
   and reboot, not merely the in-memory machine; the earlier LocalTaskRunner
   ID journal covers bounded input deduplication but does not provide a
   complete per-step acknowledgment ledger.
3. Enforce all calls serialized on one looper/executor; the pure class
   intentionally does not implement synchronization for concurrent callers.
4. Prove that the real UI snapshot is stable/complete and that the selector
   can safely handle clickability delegated to a parent before enabling
   that behavior. Fail closed until proven.
5. Verify cancellation races during a live Android operation, and handle
   restart with an explicit `UNKNOWN/INTERRUPTED` outcome instead of
   automatically replaying an uncertain side effect.
6. Pass phone-based and USB-detached endurance/latency benchmarks. User
   manual tests and reboot are deferred.

No APK installed or changed on the phone as part of this increment.
