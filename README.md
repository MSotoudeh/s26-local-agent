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

## Current architecture

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
docs/                 architecture, decisions and experiment contracts
scripts/              host/device census and later benchmark runners
config/               example policies and runtime configuration
benchmarks/m0/        raw M0 evidence
benchmarks/results/   summarized benchmark reports
```

## Reference projects

- Mobilerun Portal / DroidRun — Android Accessibility control surface and local/reverse transports.
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
