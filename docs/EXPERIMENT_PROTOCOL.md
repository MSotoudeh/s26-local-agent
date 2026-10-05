# Experiment Protocol

## Rule 1: test the uncertain primitive directly

Do not build a surrounding product to discover whether a primitive is usable.

Examples:
- benchmark raw NPU inference before wiring it into the agent;
- benchmark Persian ASR before building wake-word flows;
- benchmark tree extraction and target selection before building a generic planner.

## Rule 2: use the actual S26 Ultra

Emulator and desktop benchmarks are useful only for development. Acceptance decisions are based on the target phone.

## Required metrics

For any latency-sensitive experiment record:
- p50, p90, p95, p99;
- cold and warm cases separately;
- success/failure class;
- retry count;
- CPU/GPU/NPU utilization when observable;
- RAM;
- battery delta;
- device temperature/thermal throttling;
- connectivity mode.

## Task result taxonomy

Every task ends in one of:
- PASS_VERIFIED
- PASS_UNVERIFIED
- FAIL_WRONG_TARGET
- FAIL_NO_TARGET
- FAIL_TIMEOUT
- FAIL_MODEL
- FAIL_TRANSPORT
- FAIL_PERMISSION
- FAIL_APP_CHANGED
- ABORTED_BY_POLICY

PASS_UNVERIFIED does not count as success for side-effecting actions.

## Repetition

Microbenchmarks:
- warm latency: >=100 iterations where practical;
- cold-start: >=20 iterations;
- UI tasks: >=20 per task family;
- multi-step tasks: enough repetitions to expose non-deterministic failures.

## Baseline before optimization

Always record a baseline first. Optimizations without a baseline are not accepted.

## Confidence calibration

For models that emit confidence:
- bucket predictions;
- compare confidence to empirical correctness;
- do not use raw model confidence as a safety threshold until calibrated.

## Go/Kill document

Each experiment report ends with:

```text
Hypothesis:
Observed:
Target:
Decision: GO | HOLD | KILL/REPLACE
Reason:
Next experiment:
```

## Latency timing points

Common trace:
- activation_start
- speech_start
- speech_end
- asr_partial
- asr_final
- route_start
- route_end
- perception_start
- perception_end
- plan_start
- plan_end
- action_start
- action_end
- verify_end
- response_start
- response_end

Use monotonic clock for durations.

## Side-effect testing

For R2/R3 actions, prefer a sandbox account/test object where possible.

Do not benchmark dangerous/destructive paths against real data simply to gather latency.

## Network matrix

At minimum:
1. Wi-Fi + internet
2. mobile data
3. Wi-Fi off / mobile data off
4. USB connected, no network
5. Shizuku active, USB disconnected, no Wi-Fi

## Evidence

Raw evidence goes under `benchmarks/<milestone>/`.
Summaries go under `benchmarks/results/`.

Avoid storing credentials, message contents, banking data, contacts, or other sensitive user data in Git.
