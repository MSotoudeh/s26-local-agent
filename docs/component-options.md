# Component Options

Keep alternatives limited. Every component has a preferred starting point plus credible fallback options.

## Activation

**Primary:** Android Assistant role / side-key invocation
**Alternative:** Quick Settings tile / floating button
**Later:** local wake word

Reason: do not spend time on wake-word battery/reliability before the core loop is proven.

## Privileged local execution

**Primary:** Shizuku
**Alternative:** public Android API + Accessibility only
**Lab fallback:** USB ADB
**Future research:** root/LSPosed

Known S26 behavior from user test: Shizuku needs Wi-Fi only for bootstrap and remains usable until reboot.

## Android control / Accessibility

**Primary:** native AccessibilityService inside Project IR Mobile Agent
**Alternative reference:** A11yPilot-style Accessibility executor
**Historical reference:** Mobilerun Portal (tested and rejected as a production dependency)
**Reference:** Android Use MCP surface

Selection criteria:
- tree extraction latency;
- reliability across One UI and major apps;
- stable IDs/selectors;
- screenshot fallback;
- text entry reliability;
- local/no-network operation.

## Remote control plane

**Primary:** Project IR Hub -> native S26 Mobile Agent
**Privileged local layer:** Shizuku UserService / shell UID 2000
**Semantic UI layer:** Project IR native Accessibility bridge
**Bootstrap/recovery fallback:** USB ADB from Ops
**Alternative reference:** Android Use companion/server model

The canonical remote path is phone-native and outbound; Ops/ADB are not required for normal remote execution once the agent and Shizuku are running.

## Persian ASR

**Primary candidates to benchmark:**
1. Shenava Koochik sherpa-onnx family
2. Android/Samsung local SpeechRecognizer

**Baseline:** Whisper-compatible local model

Do not choose from published WER alone. Use the user's own Persian + Persian/English mixed command dataset.

## Intent routing

**Primary:** deterministic grammar/rules for common commands
**Secondary:** small classifier / embedding matcher
**Alternative structured model:** FunctionGemma 270M-class model
**Online structured alternative:** Jev

A full LLM should not be used for routine intent routing.

## UI target matching

**Primary:** exact Accessibility attributes + normalized text
**Secondary:** semantic embedding similarity
**Alternative:** Jev / tiny structured model over bounded actions
**Fallback:** local/cloud VLM over screenshot

## Local LLM runtime

**Primary:** Qualcomm QAIRT / GenieX on Snapdragon NPU
**Alternative:** LiteRT/QNN
**Fallback:** GPU backend if NPU graph support or thermals are worse

Models to benchmark initially:
- sub-1B/1B class;
- one 3B–4B class.

Do not start with 7B.

## Cloud reasoning

Keep provider behind a single interface.

Candidates:
- OpenAI
- Anthropic
- Gemini

Only two providers need to be actively supported in the first implementation; the interface remains provider-neutral.

## Structured fast cloud decision

**Candidate:** Jev

Use only if measured latency/accuracy beats local alternatives for a specific decision class. Never make Jev mandatory for offline operation.

## TTS

**Primary first test:** Android/Samsung local TTS
**Alternative:** dedicated local Persian TTS if quality/latency is inadequate
**Cloud:** optional, not required for control functionality.

## Persistence

**Primary:** Room/SQLite
Stores:
- skill registry;
- workflow fingerprints;
- success/latency telemetry;
- model/backend performance;
- task traces.

## Telemetry

**Primary:** local structured JSON/SQLite traces
**Alternative export:** JSONL/CSV for benchmark analysis

No cloud telemetry is required.
