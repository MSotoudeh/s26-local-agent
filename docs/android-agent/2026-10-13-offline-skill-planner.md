# Offline Local Skill Planner — source-only preview

Date: 2026-10-13
Branch: `feature/android-agent`
Acceptance: **Java policy tests GO; device execution HOLD**

## Scope

The existing Project IR Mobile Agent gains a **Preview local skill — no action**
button. It parses an allowlisted Persian/English request into a bounded
immutable plan, validates policy, and displays numbered steps. The button
never invokes the Android executor, Shizuku, Project IR Hub or the network.
The previously installed Local Text Agent action path remains unchanged.

New pure Java modules:

- `LocalSkillPlan`: immutable steps (launch, await package, unique click,
  verify text, report), risk, validation state, timeout, and preview.
- `LocalSkillRegistry`: exact offline utterance aliases and canonical
  definitions. Nothing is dynamically imported or generated from a model.
- `LocalPlanPolicy`: risk R0 and bounded plan validation; maximum eight
  steps, maximum five seconds per step, maximum 20 seconds total; approved
  app packages only. Crucially the entire plan must **exactly match** the
  canonical registered version. A forged "R0" label, selector change, new
  skill ID or fabricated live validation cannot authorize an action.
- `LocalSemanticSelector`: deterministic single-action recommendation
  against an injected immutable UI observation. It rejects stale state IDs,
  changed foreground package, incomplete/truncated trees, duplicate node
  IDs, ambiguous labels and disabled/non-clickable nodes. It **never
  dispatches an Accessibility action**.

## Built-in candidates

- Known bounded routes: Samsung Settings / Connections; app opens for
  Settings, Calculator, Clock and Maps. Their exact routes were previously
  verified in the v0/v1 implementation, but this *new planner* has not
  executed them.
- Experimental read-only battery overview: shown only as **HOLD / preview**
  because current One UI battery labels/routes were not independently
  verified.

The registry is intentionally tiny. It is not a general-purpose planner,
a learned model or an Android permission escalator.

## Safety and acceptance boundaries

1. Do not wire generated, model-proposed or preview-only plans directly
   to the executor. Canonical skill shapes and a live-validation flag
   are checked independently.
2. Semantic selection returns a candidate ID only. The controller must
   capture/revalidate a real UI state identity and an applicable, unique
   clickable element before performing any action.
3. Current selector is conservative: a text label with only a clickable
   ancestor may be rejected. This is preferable to clicking an uncertain
   target; ancestor resolution needs additional validation.
4. Never rely on a package match alone to prove success of a multi-step
   workflow. Verify destination labels/postconditions.
5. The plan preview and small reference corpus contain no live phone
   screen content, messages or user credentials.

## Verified host results

- `LocalPlanningTest`: 48/48 pure-JVM assertions (normalization,
  canonicalization, hostile instruction suffixes, package/risk/timeout
  bounds, stale state, incomplete UI, missing/ambiguous/disabled targets).
- Existing Python suite: 52/52 PASS.
- Android build/lint: tracked in the corresponding checkpoint.

No user interaction, user-owned test recordings, phone reboot, APK update,
microphone capture, NPU model download or added cost in this increment.

## Follow-on gates

1. Bind canonical plans to a transaction-backed local executor; each
   step needs preconditions, postconditions, cancel and idempotency.
2. Validate the exact battery route in a sanitized read-only S26 test.
3. Add representative labeled UI snapshots without personal content for
   matcher calibration.
4. Compare deterministic/planner/learned systems on the same frozen test
   set, not synthetic training examples.
5. ASR/NPU model quality requires actual inference and a recorded corpus
   with user consent; the current protocol supplies metrics, not results.
