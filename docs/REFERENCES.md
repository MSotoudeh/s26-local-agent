# Reference Projects

These are implementation references and benchmark candidates, not permanent dependencies.

## Android control

### Mobilerun Portal — historical/rejected production dependency
https://github.com/droidrun/mobilerun-portal

Retained as a reference only. It was useful for early feasibility work but failed repeated production reliability gates and was removed from the phone.

Use for historical/reference comparison:
- Accessibility tree/state;
- local HTTP/WebSocket;
- ADB ContentProvider;
- screenshot/control primitives;
- reverse-connection design.

### Android Use
https://github.com/bsaisuryacharan/android-use

Use for:
- Android MCP tool surface;
- ADB and companion-app control patterns.

### A11yPilot
https://github.com/azizahmed45/a11ypilot

Use for:
- compact Accessibility serialization;
- settle detection;
- tool executor;
- native Android agent loop.

## Structured decision

### Jev Mobile
https://github.com/Friedjof/jev-mobile

Use for:
- bounded-action loop;
- verification/recovery;
- USB-connected Android decision benchmark.

### Jev Android
https://github.com/dougsong/jev-android

Use for:
- native Kotlin/Accessibility integration with Jev.

Jev remains optional because it is a network service.

## Privileged Android bridge

### Shizuku
https://github.com/RikkaApps/Shizuku
https://shizuku.rikka.app/

Use for:
- local Binder access to privileged APIs after bootstrap.

## On-device models

### Google AI Edge Gallery
https://github.com/google-ai-edge/gallery

Use for:
- on-device model management;
- FunctionGemma/Mobile Actions patterns;
- LiteRT integration references.

### Qualcomm AI Hub Apps
https://github.com/qualcomm/ai-hub-apps

Use for:
- QAIRT/GenieX examples;
- Snapdragon NPU deployment and benchmarking.

### Rin NPU Agent
https://github.com/reimurin/Rin-NPU-Agent

Use for:
- real Android GenieX/NPU-first runtime integration.

## Persian ASR

Candidate families:
- PersianML Shenava Koochik sherpa-onnx
- Android/Samsung SpeechRecognizer
- Whisper-compatible local baseline

Model choice is deferred to M3 benchmark.
