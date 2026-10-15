# S26 Local Agent

Feasibility-first Android agent project for Samsung Galaxy S26 Ultra.

## Goal

Build a low-latency, primarily local, Persian-capable assistant that can control Android without requiring an always-on PC or Wi-Fi connection, while retaining optional cloud reasoning and a remote control plane for independent testing.

The project is deliberately benchmark-driven. No subsystem becomes a production dependency until it passes a measured acceptance gate on the actual S26 Ultra.

## Core principles

1. **Fast path before LLM** — native APIs, Shizuku, deterministic parsing, cached workflows, then tiny/local models, then cloud.
2. **Offline by default** — core actions must work with Wi-Fi and mobile data disabled.
3. **ADB is not a runtime dependency** — USB ADB is bootstrap/debug/control-plane only. Shizuku is the preferred privileged local transport between reboots.
4. **Tree before vision** — Accessibility hierarchy is primary perception; screenshots/VLM are fallback.
5. **Bounded actions** — models select from controller-generated valid actions whenever possible; models should not invent raw taps or shell commands.
6. **Measured smart fallback** — local/cloud routing is based on latency, confidence, historical success, risk, thermals, and connectivity.
7. **App-specific knowledge only where it pays** — generic control first; Samsung/app workflows are added only for high-frequency or brittle flows.
8. **Replaceable components** — each major layer has a primary choice plus one or two realistic alternatives.

## Canonical ownership (since 2026-10-15)

**This repository is the single source of truth for every on-phone component**, including `android/mobile-agent`, `android/pstn-audio-lab`, `android/model-lab`, Persian ASR/TTS, local LLM/VLM, Assistant integration, Android benchmark code, native Android tests, and the Model Lab roadmap. All new on-phone changes must be proposed here, not under `project-ir/android/mobile-agent`.

The Android package remains `io.projectir.mobile` to preserve the installed app signature, data and default Assistant assignment. This package ID is not an indication of Git repository ownership. For source lineage, cross-repo split SHA, migration policy and remaining work, see [Repository ownership and migration](docs/REPOSITORY_OWNERSHIP_20261015.md). For model switching and heavy benchmarks, see [S26 Model Lab](docs/S26_MODEL_LAB_MASTER_PLAN_20261015.md). For the conservative on-phone ASR evidence scorer and the reason no ASR production promotion is justified yet, see [ASR evidence quality gate](docs/S26_ASR_QUALITY_EVIDENCE_GATE_20261015.md).

`MSotoudeh/project-ir` owns the shared Hub, remote transport, coordination, cross-device authentication and infrastructure. It is **not** the canonical source for the S26 Android package going forward.

## Current architecture

The canonical phone-control path is now:

```text
ChatGPT -> Project IR -> Project IR Hub -> S26 Mobile Agent
                                      -> Shizuku shell
                                      -> native Accessibility bridge
```

Mobilerun was used for feasibility experiments and then retired after repeated reliability failures. See [Experiment and decision history](docs/HISTORY.md).

See:
- [Architecture](docs/ARCHITECTURE.md)
- [Component options](docs/COMPONENT_OPTIONS.md)
- [Roadmap and gates](docs/ROADMAP.md)
- [Experiment protocol](docs/EXPERIMENT_PROTOCOL.md)
- [Control plane](docs/CONTROL_PLANE.md)
- [Skills and app knowledge](docs/SKILLS.md)
- [Smart fallback](docs/SMART_FALLBACK.md)
- [Security and privacy](docs/SECURITY_PRIVACY.md)
- [M0 device census](docs/M0_DEVICE_CENSUS.md)
- [Decisions](docs/DECISIONS.md)
- [Experiment and decision history](docs/HISTORY.md)

## Milestones

| Milestone | Purpose |
|---|---|
| M0 | Device/runtime census |
| M0.5 | Remote phone control plane |
| M1 | Shizuku + no-Wi-Fi privileged runtime |
| M2 | Accessibility primitives |
| M2.5 | Jev / bounded-action bake-off |
| M3 | Persian ASR benchmark |
| M4 | Deterministic router |
| M5 | Tiny local model |
| M6 | NPU local LLM benchmark |
| M7 | Samsung/app workflow framework |
| M8 | Semantic UI navigation |
| M9 | Generic agent loop |
| M10 | Local vs Jev vs cloud routing |
| M11 | Smart fallback |
| M12 | Persian end-to-end |
| M13 | Android Assistant integration |
| M14 | Wake word |

## Repo layout

```text
android/mobile-agent/   canonical Android APK source and JVM tests
android/model-lab/     model catalog, benchmark scenarios and manifests
android/pstn-audio-lab/ isolated PSTN research
docs/                 architecture, decisions and experiment contracts
scripts/              host/device census and later benchmark runners
config/               example policies and runtime configuration
benchmarks/m0/        raw M0 evidence
benchmarks/results/   summarized benchmark reports
```

## Reference projects

- S26 Local Agent (android/mobile-agent) - canonical Android executor, offline Persian Assistant and Accessibility source.
- Mobilerun Portal / DroidRun — historical feasibility reference; rejected as a production dependency.
- A11yPilot — compact Accessibility tree + tool executor + agent loop.
- Android Use — Android MCP/control-plane reference.
- Jev Mobile / Jev Android — bounded structured decision loops for mobile control.
- Shizuku — privileged local Binder bridge.
- Google AI Edge Gallery / FunctionGemma — small on-device function routing.
- Qualcomm AI Hub / QAIRT / GenieX — Snapdragon NPU runtime.
- Rin NPU Agent — Android GenieX/NPU integration reference.

## Success definition

The project is successful only if it is pleasant in real use, not merely technically functional.

Target examples:
- simple local action: p50 < 700 ms after end of speech, p95 < 1.2 s;
- first visible action for common app command: < 1 s;
- simple-command success: > 98%;
- multi-step common-task success: > 95%;
- Persian command interpretation: > 97%;
- silent wrong side effects: approximately zero.
