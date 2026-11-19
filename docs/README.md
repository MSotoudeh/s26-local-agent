# Documentation index

Living documents describe the current design. Dated files (`YYYY-MM-DD-*.md`)
are point-in-time experiment records: they keep the evidence and decisions of
that day and are not updated afterwards, so their "next steps" and branch
names may be stale. The current state is always in [roadmap.md](roadmap.md)
and [HISTORY.md](HISTORY.md).

## Start here

| Document | Purpose |
|---|---|
| [HISTORY.md](HISTORY.md) | Project trajectory, phases, direction changes and detailed experiment narrative |
| [architecture.md](architecture.md) | Target runtime architecture |
| [roadmap.md](roadmap.md) | Milestones M0–M14 with acceptance gates and current status |
| [decisions.md](decisions.md) | Accepted architecture decisions (D001…) |
| [adr/](adr/) | Formal ADRs: feasibility-first, ADB not a runtime dependency |
| [repository-ownership.md](repository-ownership.md) | Why on-phone code moved here from `project-ir` (2026-10-15) |

## Design references

| Document | Purpose |
|---|---|
| [control-plane.md](control-plane.md) | Remote control path through the Project IR Hub |
| [component-options.md](component-options.md) | Primary and alternative choice per component |
| [skills.md](skills.md) | Skill and app-specific knowledge strategy |
| [smart-fallback.md](smart-fallback.md) | Local vs cloud routing rules |
| [security-privacy.md](security-privacy.md) | Privacy posture and sensitive surfaces |
| [experiment-protocol.md](experiment-protocol.md) | How experiments are designed, measured and recorded |
| [m0-device-census.md](m0-device-census.md) | Device/host census procedure (`scripts/m0-device-census.ps1`) |
| [references.md](references.md) | External reference projects |

## Android agent (`android/mobile-agent`)

- [android-agent/overview.md](android-agent/overview.md) — app architecture, build, bootstrap, security
- Experiment records:
  - 2026-10-13 [local text agent v0](android-agent/2026-10-13-local-text-agent-v0.md), [v1 action identity](android-agent/2026-10-13-local-text-agent-v1.md), [replay/cancel](android-agent/2026-10-13-replay-cancel.md), [offline skill planner](android-agent/2026-10-13-offline-skill-planner.md)
  - 2026-10-14 [offline plan machine](android-agent/2026-10-14-offline-plan-machine.md), [plan adapter pre-install](android-agent/2026-10-14-plan-adapter-preinstall.md), [M4 multi-step checkpoint](android-agent/2026-10-14-m4-multi-step-checkpoint.md), [isolated ART canary](android-agent/2026-10-14-isolated-art-canary.md), [release preflight](android-agent/2026-10-14-release-preflight.md)
  - 2026-10-15 [real plan canary](android-agent/2026-10-15-real-plan-canary.md), [offline Persian ASR + local LLM + Assistant](android-agent/2026-10-15-offline-fa-asr-local-llm-assistant.md), [16 KB alignment fix](android-agent/2026-10-15-16kb-alignment-fix.md), [Assistant offline voice flow](android-agent/2026-10-15-assistant-offline-voice-flow.md)
  - 2026-10-22 [Binder health and Hub 401](android-agent/2026-10-22-agent-health-hub-401.md), [ShizukuPlus boot test](android-agent/2026-10-22-shizukuplus-boot-test.md)

## Model Lab (`android/model-lab`)

- [model-lab/master-plan.md](model-lab/master-plan.md) — multi-model lab plan (Persian)
- [model-lab/benchmark-protocol.md](model-lab/benchmark-protocol.md) — reproducible benchmark protocol (Persian)
- 2026-10-15 records: [M1 foundation](model-lab/2026-10-15-m1-checkpoint.md), [M1b Vosk A/B](model-lab/2026-10-15-m1b-vosk-ab.md), [M2 sherpa Omnilingual](model-lab/2026-10-15-m2-sherpa-omni.md), [M2 Whisper base](model-lab/2026-10-15-m2-whisper-base.md), [M2 Qwen3-ASR](model-lab/2026-10-15-m2-qwen3-asr.md), [M2 three-way A/B](model-lab/2026-10-15-m2-asr-ab-comparison.md), [ASR evidence quality gate](model-lab/2026-10-15-asr-quality-evidence-gate.md)

## Call audio research (`android/pstn-audio-lab`)

2026-10-13 records: [PSTN feasibility](call-audio/2026-10-13-pstn-feasibility.md), [Shizuku live lab](call-audio/2026-10-13-shizuku-live-lab.md), [offline bridge lab](call-audio/2026-10-13-offline-bridge-lab.md), [Android adapter](call-audio/2026-10-13-android-adapter.md), [no-call preflight](call-audio/2026-10-13-no-call-preflight.md)

## Open plans (not yet executed)

- [plans/shizukuplus-capability-backlog.md](plans/shizukuplus-capability-backlog.md) — ShizukuPlus feature evaluation backlog (2026-10-22)
- [plans/termux-claude-code-persian-voice.md](plans/termux-claude-code-persian-voice.md) — Termux + Claude bridge voice assistant options (2026-11-18)

## Archive

- [archive/](archive/) — superseded documents kept for context
