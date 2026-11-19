# Smart Fallback

## Objective

Reach cloud-like reliability where needed while retaining local latency, privacy and offline capability for common tasks.

Fallback is not simply "if error, call a bigger model".

## Inputs to the router

- task class;
- risk class;
- deterministic coverage;
- local model confidence;
- confidence calibration;
- historical success for this app/task;
- expected local latency;
- current model warm/cold state;
- device thermal state;
- RAM pressure;
- connectivity;
- cloud latency history;
- UI fingerprint confidence;
- previous action outcome.

## Example route

```text
Request
  ↓
deterministic parser
  ├─ confident → execute
  ↓
small local structured model
  ├─ confident → execute
  ↓
bounded UI decision
  ├─ deterministic/semantic
  ├─ Jev when online and advantageous
  ↓
local LLM
  ↓
cloud LLM/VLM
```

Not every request traverses every stage.

## Side-effect-aware fallback

Timeout does not mean action failed.

Before retry/escalation:
1. inspect expected postcondition;
2. if postcondition already holds, mark success;
3. otherwise decide whether the action is safe to repeat.

Each side-effecting action needs an idempotency policy.

## Backend score

A future simple scoring model can use:

```text
score =
  predicted_success
  - latency_penalty
  - risk_penalty
  - network_penalty
  - thermal_penalty
  - monetary_cost_penalty
```

Weights must come from measured priorities, not guesswork.

## Local/cloud switch granularity

Switch per subsystem, not globally.

Example:
```yaml
asr: local
intent: local
ui_selection: smart
planner: smart
vision: smart
conversation: cloud
```

## Smart-mode experiment

Evaluate identical tasks under:
- local-only;
- cloud-only;
- smart.

Measure:
- verified success;
- p50/p95 total latency;
- first-action latency;
- cloud-call rate;
- duplicate side effects;
- cost;
- thermal/battery effect.

Smart mode only ships if it materially dominates a simpler policy.
