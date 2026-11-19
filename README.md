# S26 Local Agent

A feasibility-first research project to build a fast, mostly offline, Persian-capable assistant that controls a Samsung Galaxy S26 Ultra directly on the phone.

## Status

**Experimental / active research.** Components are promoted only after passing a measured gate on the real device; most of the roadmap (local LLM routing, NPU, wake word, smart fallback) is still open. See [docs/roadmap.md](docs/roadmap.md) for gate status and [docs/HISTORY.md](docs/HISTORY.md) for how the project got here.

## What it does

- **Android agent app** (`android/mobile-agent`, package `io.projectir.mobile`): a native executor that runs on the phone. It offers
  - an offline, allowlisted Persian/English command router and multi-step plan runtime with a write-ahead journal, bounded action IDs and fail-closed policy;
  - a native AccessibilityService semantic UI bridge (structured tree, exact-text/resource-ID click, text input) restricted to its own UID and shell UID 2000;
  - privileged shell execution through a Shizuku UserService;
  - an opt-in Android Assistant entry point with offline Persian push-to-talk (Vosk) and a local LLM (MediaPipe Qwen);
  - a **Model Lab** panel that installs SHA-verified offline ASR models (Vosk, sherpa-onnx Omnilingual, Whisper, Qwen3-ASR) and runs same-utterance A/B comparisons with JSON receipts;
  - an optional outbound WebSocket to the Project IR Hub for remote control.
- **PSTN audio lab** (`android/pstn-audio-lab`): an isolated, no-call research app for cellular call-audio feasibility.
- **Host tooling** (`scripts/`): device census, offline/benchmark harnesses, model preparation and evidence scorers.
- **Evidence** (`benchmarks/`): raw JSON receipts and summarized reports for every milestone.

## How it works

```text
Voice (Assistant role / side key) or text
        |
  offline Persian ASR (Model Lab adapter)  ->  normalization
        |
  LocalCommandRouter / LocalSkillRegistry   (deterministic, allowlisted)
        |
  LocalPlanCoordinator -> LocalPlanMachine  (journaled, bounded, fail-closed)
        |
  AccessibilityService actions  |  Shizuku UserService (UID 2000)
        |
  postcondition verification on the live UI tree
```

Remote control is a separate, optional path: `ChatGPT -> Project IR Hub -> S26 agent (outbound WebSocket) -> Shizuku / Accessibility`. Core principles: fast path before LLM, offline by default, ADB only for bootstrap, Accessibility tree before vision, bounded actions, measured fallback. Details: [docs/architecture.md](docs/architecture.md), [docs/control-plane.md](docs/control-plane.md), [docs/decisions.md](docs/decisions.md).

### Canonical ownership (since 2026-10-15)

This repository is the single source of truth for every on-phone component (`android/mobile-agent`, `android/pstn-audio-lab`, `android/model-lab`, ASR/TTS, local LLM, Assistant integration, Android tests and the Model Lab roadmap). `MSotoudeh/project-ir` owns the shared Hub, remote transport and infrastructure. The package ID stays `io.projectir.mobile` to preserve the installed app's signature, data and Assistant role. See [docs/repository-ownership.md](docs/repository-ownership.md).

## Quick start

Host-side checks need only Python 3 (verified with 3.14) and a JDK 17+:

```sh
git clone https://github.com/MSotoudeh/s26-local-agent.git
cd s26-local-agent
python -m pip install pytest
python -m pytest tests/ scripts/ -q -p no:cacheprovider
python scripts/run-jvm-tests.py
python scripts/validate-s26-model-lab.py --self-test
```

Building and installing the APK needs Android SDK API 37, Build Tools 36.0.0 and JDK 21+ (Windows PowerShell):

```powershell
.\scripts\build-android-agent.ps1
.\scripts\bootstrap-android-agent.ps1 -ApkPath .\android\mobile-agent\app\build\outputs\apk\debug\app-debug.apk
```

The ASR runtime AAR is not committed; fetch it with `scripts/Prepare-S26SherpaRuntime.ps1` (SHA-256 pinned). Full device setup: [docs/android-agent/overview.md](docs/android-agent/overview.md).

## Configuration

There is no `.env` file. Configuration is by script parameter or these environment variable names:

- `JAVA_HOME`, `ANDROID_HOME` / `ANDROID_SDK_ROOT`, `GRADLE_USER_HOME` — build toolchain (defaults resolve under `LOCALAPPDATA` / `USERPROFILE`).
- `S26_ADB_SERIAL` — optional ADB serial for `scripts/Recover-S26ShizukuPlus-USB.ps1`.
- Hub URL and agent token are read by `bootstrap-android-agent.ps1` from a local `%LOCALAPPDATA%\ProjectIR\config\agent.json` (never committed) and stored encrypted on the phone.
- Runtime policy example: [config/policy.example.yaml](config/policy.example.yaml).

## Project layout

```text
android/mobile-agent/    Android agent app source and standalone JVM tests
android/model-lab/       Model Lab catalog, providers, scenarios and lock files
android/pstn-audio-lab/  isolated PSTN call-audio research app
benchmarks/              raw evidence per milestone (m0..m9, model-lab) and results/ summaries
config/                  example runtime policy
docs/                    architecture, decisions, roadmap, history and experiment records (index: docs/README.md)
scripts/                 build, bootstrap, census, benchmark and model-preparation tools
tests/                   Python tests for scripts, Model Lab and evidence gates
```

## Testing

```sh
python -m pytest tests/ scripts/ -q -p no:cacheprovider
python scripts/run-jvm-tests.py
```

Device benchmarks under `scripts/` (`m1-*`, `m2-*`, `m4-*`, `Invoke-*`) need a connected, authorized S26 and are never run automatically.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Security issues: [SECURITY.md](SECURITY.md).

## License

[MIT](LICENSE). Third-party models and runtimes (Vosk, sherpa-onnx, Whisper, Qwen, MediaPipe, Shizuku) keep their own licenses and are downloaded, not committed.
