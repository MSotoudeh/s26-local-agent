# Offline Skill Planner and Persian evaluation infrastructure

Date: 2026-10-13
Device target: Samsung S26 Ultra (SM-S948B)
Canonical Android branch: `feature/android-agent`
Status: **Source validated; phone/UI execution and ASR/NPU model evaluation HOLD**

## Goal and change set

Continue without requiring the user to manipulate or reboot the phone, and
without editing the independent PSTN lab. The pre-existing Local Text Agent
stays as the last device-validated execution path.

Five new pure Java components were built:
1. `LocalSkillPlan` — immutable metadata and structured, bounded plan steps;
2. `LocalSkillRegistry` — deterministic local Persian/English skill retrieval;
3. `LocalPlanPolicy` — risk, package, timeout, step count, final verification,
   and exact canonical plan-shape enforcement;
4. `LocalSemanticSelector` — deterministic, non-actuating selection of a
   unique UI node from a fully observed, current, trusted package state;
5. `LocalPlanningTest` — host JVM adversarial and edge-case validation.

The installed Local Text Agent source gains a "Preview local skill — no
action" button. This does not execute plans or issue any Android actions.

### Current skill coverage

- Settings, Connections, Clock, Calculator, Maps: bounded, canonical plans
  based on previously verified v0/v1 pathways. **The new plan interpreter
  has NOT been live exercised.**
- Battery usage: read-only *candidate*, Preview/HOLD only. Actual One UI
  navigation and data extraction have not been confirmed.
- Arbitrary messages, deletion, payments, external contacts, unknown app
  packages and compound command-injection strings: no registered plan.
- Untrusted/forged R0 labels or plan steps: cannot pass canonical policy.

## Self-verified results

| Suite | Outcome |
| --- | --- |
| Pure Java LocalPlanningTest | 48/48 assertions PASS |
| Existing LocalCommandRouterTest | 17/17 PASS |
| Existing LocalActionIdentityTest | 17/17 PASS |
| Total host JVM assertions | **82/82 PASS** |
| Existing Project IR Python suite | **52/52 PASS** |
| Android assembleDebug + lintDebug | **PASS** |
| Pure Python speech/intent evaluation math | **9/9 PASS** |

Android built from the final source after the canonical-plan hardening change.
No APK from this turn was installed or signed for deployment. No phone reboot,
live task, microphone test or shell mutation was performed.

## M3/M5/M6 readiness

A standalone, local-only evaluator now supports:
- Intent labels: accuracy, macro-F1, per-label metrics and confusion.
- Persian ASR text: normalized CER/WER and named-entity preservation.
- Strict JSONL matching on sample IDs; missing or duplicate IDs fail.
- **36 synthetic example phrases** for future regression and routing tests,
  not user speech, not a representative ASR test set.
- Nine self-tests of metric correctness.

No actual recorded utterances or model predictions were evaluated. In
particular, the tools do **not** prove Persian ASR accuracy, NPU activation,
LLM task capability, latency, battery impact or memory use. NPU runtime/model
selection, profiling and user-owned audio validation remain pending.

## Key safety invariants

- Raw UI nodes are never executed as arbitrary commands by the planning code.
- The selector can only return a candidate node ID from a complete snapshot
  of the expected package and matching state ID; all ambiguity fails closed.
- Current selector intentionally rejects labels with no directly clickable
  node. Safe ancestor resolution would need separate proof.
- The plan validator does not trust user/model supplied risk flags. The plan
  must exactly match a canonical registry definition.
- Experimental skill status is not automatically promoted to live-trusted.
- No new network endpoints, hidden services, plugins or privileged control
  surfaces were installed.

## Remaining gates, without manual intervention

1. Pure-model replay tests and failure injection for plan execution;
2. Safe binding between canonical plan steps and LocalTaskRunner with
   per-step postconditions and idempotency;
3. Representative synthetic/sanitized UI fixtures for calibrated matching;
4. Model runtime selection and automated benchmarks when candidates are
   available and can be verified offline;
5. User-facing confirmation and speech UX *after* the underlying agent
   loop can handle deterministic tasks.

Deferred because they depend on access/approval or genuine device evidence:
live app navigation, battery skill promotion, microphone recording,
physical USB-detached endurance, Assistant role and Wake Word. **Reboot
remains postponed by user request.**

Raw source: `project-ir/android/mobile-agent/app/src/main/java/io/projectir/mobile`.
Android implementation notes: `project-ir/docs/offline-skill-planner.md`.
Speech benchmark protocol: `../m3/README_SPEECH_EVAL.md`.
