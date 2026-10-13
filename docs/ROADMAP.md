# Roadmap and Acceptance Gates

## Philosophy

Every milestone is a falsifiable experiment. The output is not "feature implemented"; it is a decision:

- **GO** — integrate into the main architecture;
- **HOLD** — useful but not yet reliable enough;
- **KILL/REPLACE** — stop investing and test an alternative.

## M0 — Device/runtime census

Status: **COMPLETE / GO** (2026-10-05).

Capture:
- Android build and device identity;
- SoC/ABI/RAM;
- ADB availability and transport;
- Shizuku state if observable;
- Accessibility-related capabilities;
- installed runtime/tooling;
- host Android tooling;
- candidate NPU runtime support.

Output: machine-readable JSON plus a short report.

## M0.5 — Remote phone control plane

Status: **GO / canonical architecture selected** (2026-10-07).

Goal: allow Project IR to inspect and actuate the phone independently of an always-connected PC.

Selected path:
1. Project IR Mobile Agent maintains the outbound Hub connection;
2. Shizuku provides privileged local shell execution;
3. Project IR's native Accessibility bridge provides structured state and semantic UI actions;
4. USB ADB remains bootstrap/recovery only.

Acceptance evidence:
- direct `device_id=s26` execution through Project IR;
- Settings semantic workflow: 20/20 successful repeated cycles;
- Persian mixed text `سلام test 123` written and verified through the native bridge;
- Mobilerun retired and removed after replacement parity.

Remaining connectivity/offline endurance belongs to M1, not M0.5.

## M1 — Shizuku no-Wi-Fi runtime

Status: **bounded offline persistence GO / full acceptance HOLD** (2026-10-13). Initial Airplane Mode check 12/12 PASS; strict Airplane Mode + Wi-Fi off + mobile data off + no IPv4 default route check 8/8 PASS. USB remained attached for recovery; long-run, physically unplugged and post-reboot operation are still unverified. A subsequent controlled UserService Binder failure test exposed an initial process-only false positive, then verified automatic end-to-end recovery in 2/2 repeat trials after stale-connection unbind/rebind hardening. See benchmarks/results/2026-10-13-m1-m2.md and benchmarks/results/2026-10-13-shizuku-rebind.md.

After Shizuku bootstrap:
- disconnect USB;
- Wi-Fi off;
- test privileged operations repeatedly.

Gate:
- >= 99% success over 100 low-risk operations;
- no runtime network dependency;
- median bridge overhead low enough to be negligible relative to UI latency.

## M2 — Accessibility primitives

Status: **four-app structured perception GO / full action reliability HOLD** (2026-10-13). Samsung Settings, Calculator, Clock and Google Maps each passed 6/6 snapshots (24/24 total); 10/10 Settings semantic Connections/Back workflows passed. The initial Clock launcher failure was fixed using a resolved explicit activity, without changing Accessibility. Scroll, dynamic UI and cross-app side-effecting tasks remain untested. See benchmarks/results/2026-10-13-m1-m2.md.

Implement/test:
- dump tree;
- click;
- set text;
- scroll;
- back/home;
- launch app;
- screenshot fallback.

Apps: One UI Settings, Samsung apps, Telegram, Chrome, YouTube.

Gate:
- primitive success >= 99% on supported test set;
- typical tree extraction target < 50 ms;
- no silent wrong target.

## M2.5 — Bounded decision bake-off

Compare:
- deterministic selector;
- semantic matcher;
- Jev;
- optional tiny local structured model.

The model chooses only from controller-generated valid actions.

Gate:
- target-selection accuracy > 97% on representative states;
- calibration good enough to support confidence thresholds.

## M3 — Persian ASR

Status: **read-only service inventory complete; model accuracy/latency NOT TESTED** (2026-10-13). Existing Android RecognitionService registrations do not prove local Persian support. A real evaluation corpus and candidate model installation/verification are still needed. See benchmarks/m3/20261013-speech-npu-census.json.

Build user's command corpus:
- short Persian commands;
- names/entities;
- Persian-English mixed phrases;
- conversational tasks.

Compare:
- Shenava;
- Android/Samsung local recognizer;
- Whisper baseline.

Gate targets:
- simple command accuracy >= 98%;
- entity preservation >= 97%;
- mixed-language >= 95%;
- p95 finalization latency < 500 ms if achievable.

If no local ASR reaches usable quality, explicitly decide whether cloud ASR fallback is acceptable.

## M4 — Deterministic router

Status: **Local Text Agent v0 GO (limited)** — 2026-10-13. An on-device deterministic Persian/English router and task executor now run inside Project IR Mobile Agent without Hub, Shizuku or network dependencies on the local execution path. Strict offline DEVICE_STATUS and Settings → Connections commands passed; Calculator, Clock and Maps single launches were verified. Parser 15/15, Android build/lint PASS. Further task coverage, reliability and voice remain HOLD. See benchmarks/results/2026-10-13-m4-local-text-agent-v0.md.

Reliable Executor v1 update (2026-10-13): bounded device GO for on-phone
Action IDs (30/30 unique successful low-risk commands) and the Settings ->
Connections regression (5/5 verified retry after an initial 3/5 host timeout
batch). Cancel button and process-interruption handling are implemented and
built, but live active-task cancellation is not yet accepted. Scroll, multi-step
stress and USB-detached endurance remain HOLD. See
benchmarks/results/2026-10-13-m4-reliable-executor-v1.md.

Domains:
- app launch;
- volume;
- brightness;
- flashlight;
- navigation;
- simple connectivity/system commands where platform permissions allow.

Gate:
- >= 99% interpretation accuracy on covered grammar;
- routing latency effectively negligible (< 10 ms target).

## M5 — Tiny local model

Compare:
- small classifier;
- embeddings;
- FunctionGemma-class structured model.

Use only for requests not safely covered by deterministic routing.

Gate: must improve coverage materially without unacceptable cold-start/latency.

## M6 — NPU local LLM

Benchmark raw, before integration:
- model load;
- warm/cold TTFT;
- tokens/sec;
- RAM;
- sustained thermals;
- battery;
- crash rate;
- task accuracy.

Runtimes:
- QAIRT/GenieX;
- LiteRT/QNN;
- GPU fallback.

Only models that are genuinely interactive survive.

## M7 — Samsung/app workflow framework

Introduce app-specific knowledge only where generic control is materially worse.

Initial candidates:
- One UI Settings;
- Samsung system surfaces;
- Telegram.

Gate: each workflow must improve latency/reliability enough to justify maintenance cost.

## M8 — Semantic UI navigation

Accessibility tree → target matcher → bounded action.

Gate:
- >97% target selection on test suite;
- fallback path for uncertainty.

## M9 — Generic agent loop

20–50 real multi-step tasks across selected apps.

Compare local 1B/4B and cloud references.

## M10 — Local vs Jev vs cloud routing

Blind evaluation over the same bounded decision and planning tasks.

Measure accuracy, p50/p95, cost, connectivity dependence, confidence calibration.

## M11 — Smart fallback

A/B/C:
- local only;
- cloud only;
- smart.

Initial success target:
- >96% task success;
- simple p50 < 1 s;
- cloud calls <20% if quality is preserved;
- no duplicated side effects.

## M12 — Persian end-to-end

Voice → ASR → route/plan → action → verify → Persian response.

## M13 — Assistant integration

Assistant role / side-key invocation / overlay.

## M14 — Wake word

Only after the core agent is proven.

Measure:
- false accepts/day;
- false rejects;
- wake latency;
- battery cost.

If wake-word UX is worse than side-key invocation, do not ship it as default.

## 2026-10-13 — Work that can proceed without manual phone testing

M2.5/M7/M8: a canonical read-only Skill Registry, bounded Plan Policy and
strict Semantic Selector now exist as **source-only preview components**.
LocalPlanningTest 48/48, existing JVM suites 34/34, Android build/lint PASS.
The newly built APK was not installed in this increment. New battery route
is a HOLD candidate, not a verified navigation skill.

M3/M5: local offline Persian ASR and intent evaluation scaffolds added with
36 *synthetic* text-only cases and 9/9 metric-unit tests. No actual audio,
runtime/model inference, ASR accuracy or NPU speed is measured.

M9/M10/M11: the generic planning/execution loop, trust calibration and
local/cloud arbitration are still not validated or complete. M12–M14 voice,
Assistant role and Wake Word are deferred pending underlying task engine
acceptance and device evidence. Phone reboot remains intentionally deferred.

Source and acceptance boundaries:
`benchmarks/results/2026-10-13-offline-planner-preview.md`.
