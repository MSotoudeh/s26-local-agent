# Termux + Claude Code CLI + Offline Persian Voice: Scenarios, Engines and Plan

Date: 2026-11-18
Status: PLAN ONLY. Nothing here is implemented or verified on device.
Target device: Samsung Galaxy S26 Ultra (aarch64, Android).

## 1. Goal

A personal Persian (Farsi) voice assistant on the phone:

1. Listen with an OFFLINE speech-to-text (STT) engine.
2. Send the text to Claude (Claude Code CLI in Termux, or the API).
3. Speak the reply with an OFFLINE text-to-speech (TTS) engine.

Only step 2 needs internet. Claude cannot run offline; an optional local LLM fallback is covered in section 7.

## 2. Terms of use (read before choosing a bridge)

Facts (checked 2026-11-18):
- Claude Code docs: Anthropic does not permit third-party developers to offer Claude.ai login in their own applications, or to route requests through Free/Pro/Max/Ultra plan credentials on behalf of their users.
- Since Feb 2026 the terms state that using OAuth tokens from Free/Pro/Max accounts in any other product, tool or service (including the Agent SDK) is not permitted; Anthropic also enforces this server-side.
- Subscription login is described as supporting ordinary use of Claude Code and other native Anthropic applications.

What this means for this project:
- ALLOWED: running the official Claude Code CLI in Termux and using it yourself.
- NOT ALLOWED: extracting the subscription OAuth token and using it in our own app or SDK code.
- GREY AREA: our own app invoking `claude -p` automatically as the assistant backend. It is the official binary, but arguably beyond "ordinary use". Risk: account suspension.
- CLEAN: an Anthropic API key (pay per token) for any automated or app-driven use.

Decision rule: prototype with the CLI for personal testing if accepted; the production path is the API key. Re-check the terms before deploying.

## 3. Claude bridge scenarios

| ID | Scenario | Pros | Cons | Terms |
|----|----------|------|------|-------|
| C0 | Official Claude Android app + Persian voice typing (Gboard/Samsung) + system TTS | Zero development, fully supported | No custom pipeline, Persian TTS depends on system | Clean |
| C1 | Claude Code in Termux, patched native linux-arm64 binary (glibc-runner + patchelf) | Lightest, fast start | Breaks on updates; auto-updater must be disabled | Grey if app-driven |
| C2 | Claude Code in proot-distro Ubuntu in Termux | Official installer and auto-update work | Heavier, slower I/O, more storage | Grey if app-driven |
| C3 | Android Virtualization Framework (AVF) Linux VM | Real Linux, isolated | Device/OS dependent, heavy | Grey if app-driven |
| C4 | Claude Code on the Ops machine, phone reaches it via SSH/Tailscale | Most stable, no Android quirks | Needs host online; home IP is dynamic (use Tailscale) | Grey if app-driven |
| C5 | Anthropic Messages API with API key, from Termux script or directly from the app | Lowest latency, streaming, prompt caching | Separate billing | Clean |
| C6 | Claude Agent SDK (Python/TS) in Termux with API key | Tools, MCP, sessions without the CLI | Separate billing; Node/Python on Termux | Clean |
| C7 | Small relay on Cloudflare Worker (existing Project IR hub) holding the API key | Key never on phone; rate limits, logging | Extra hop, extra component | Clean |

Recommendation: C5 (or C7 for key isolation) as the main path. C1 only for personal experiments. C0 as the zero-effort baseline to compare against.

## 4. Speech-to-text engines (offline, Persian)

Status legend: KNOWN = Persian support documented; VERIFY = Persian support or phone performance must be confirmed first.

| ID | Engine / model | Runtime options | Streaming | Notes | Status |
|----|----------------|-----------------|-----------|-------|--------|
| S1 | Vosk Persian model | Vosk Android / Python in Termux | Yes | Already benchmarked in this repo (M1B). Small and fast, lower accuracy | KNOWN |
| S2 | Whisper tiny / base / small (multilingual) | sherpa-onnx, whisper.cpp | No (chunked) | base already documented here; small is the likely quality floor for Persian | KNOWN |
| S3 | Whisper large-v3-turbo, quantized (q5/q8) | whisper.cpp (NEON, optional Vulkan/OpenCL), sherpa-onnx | No | Best multilingual quality class; check latency and heat on S26 | VERIFY |
| S4 | Persian fine-tuned Whisper checkpoints (Hugging Face community) | Convert to ggml or ONNX | No | Often large WER gains over base Whisper on Persian; licence varies | VERIFY |
| S5 | wav2vec2 XLSR Persian fine-tunes (e.g. m3hrdadfi/wav2vec2-large-xlsr-persian) | ONNX Runtime | Pseudo | CTC, no punctuation; needs normaliser | VERIFY |
| S6 | Meta MMS ASR (1,000+ languages, includes Persian) | ONNX / Transformers | No | Large model with per-language adapters; licence CC-BY-NC, personal use only | VERIFY |
| S7 | Qwen3-ASR | Already deployed APK files in this repo | No | Compare using existing A/B docs | KNOWN (repo) |
| S8 | sherpa-onnx multilingual models (SenseVoice, Omni variants) | sherpa-onnx | Some | Check whether fa is in the language list before testing | VERIFY |
| S9 | Android on-device SpeechRecognizer with offline Persian pack | Android API | Yes | Zero setup; offline fa pack availability varies by device and Google/Samsung | VERIFY |
| S10 | Gemma 3n or other on-device multimodal LLM with audio input | LiteRT / MediaPipe | No | Could do ASR plus intent in one step; Persian quality unknown | VERIFY |
| S11 | Cloud STT fallback (Google, Azure Speech fa-IR) | HTTPS | Yes | NOT offline; only as optional fallback when local confidence is low | KNOWN |

Supporting audio components:
- VAD: Silero VAD (via sherpa-onnx or ONNX Runtime) to cut silence and detect end of speech.
- Noise suppression: RNNoise or the Android NoiseSuppressor / AEC effects.
- Language ID: Whisper language detection or a small LID model to switch between Persian, English and Italian.
- Keyword spotting / wake word: sherpa-onnx keyword spotter (custom keywords), openWakeWord (custom-trained wake word). Porcupine only if a free custom keyword fits.

Selection rule: run every candidate through the existing S26 model-lab benchmark protocol and ASR quality evidence gate with the same Persian recordings (clean, noisy, numbers, mixed English). Report WER, CER, real-time factor, RAM, battery and temperature. Do not choose by reputation.

## 5. Text-to-speech engines (offline, Persian)

| ID | Engine / voice | Runtime options | Notes | Status |
|----|----------------|-----------------|-------|--------|
| T1 | Piper Persian voices (fa_IR community voices, e.g. by SadeghKrmi/gyroing; check rhasspy/piper-voices list) | sherpa-onnx (Android lib/APK), Piper binary in Termux | Best-documented offline route; quality varies per voice | KNOWN |
| T2 | sherpa-onnx TTS engine APK registered as Android system TTS | Android TextToSpeech API | Lets any app (and C0) speak Persian with a Piper voice | VERIFY (fa build) |
| T3 | Meta MMS-TTS Persian (VITS) | sherpa-onnx (if converted), ONNX Runtime | Many languages, modest quality; CC-BY-NC | VERIFY |
| T4 | Android system TTS with an installed Persian voice (Google, Samsung, other engines) | Android API | Easiest; offline Persian voice often missing | VERIFY |
| T5 | eSpeak-NG Persian | Termux package, Android port | Tiny and instant, robotic; emergency fallback only | KNOWN |
| T6 | Custom Piper voice fine-tuned on Persian datasets (e.g. ManaTTS, ParsiGoo) | Train on desktop/cloud GPU, export ONNX | Highest control over voice; costs GPU time; check dataset licences | VERIFY |
| T7 | Newer neural TTS with Persian fine-tunes (F5-TTS, VITS2, Matcha variants, IMS-Toucan) | ONNX / PyTorch | Better prosody possible, likely too heavy for real time on phone | VERIFY |
| T8 | Cloud TTS fallback (Azure fa-IR neural voices, Google Cloud TTS) | HTTPS | NOT offline; optional high-quality fallback | KNOWN |

Kokoro and XTTS-v2 are excluded: no Persian support at time of writing (re-check).

## 6. Persian text processing (applies to both STT output and TTS input)

Persian script omits short vowels, so TTS mispronunciation (wrong ezafe, homographs) is the main quality risk. The pipeline needs:
- Normalisation: Hazm or Parsivar normaliser (Arabic vs Persian ye/kaf, ZWNJ half-space, spacing, diacritics).
- Numbers, dates, currency, units to Persian words before TTS (e.g. num2fawords-style conversion), including Jalali dates.
- Mixed-script handling: detect English/Latin words and either transliterate or switch to an English voice for that span.
- G2P quality: compare Piper's eSpeak phonemiser against a Persian G2P with ezafe prediction; keep a user pronunciation dictionary.
- Output shaping: the Claude system prompt must request short spoken Persian sentences, no markdown, no lists, no emoji, numbers written for speech.
- STT post-processing: punctuation restoration and Finglish-to-Persian conversion where needed.

## 7. Feature options (beyond the basic loop)

Interaction
- F1 Push-to-talk: in-app button, Quick Settings tile, home widget, Termux:Widget shortcut, Bluetooth headset button.
- F2 Hands-free wake word (sherpa-onnx KWS or openWakeWord) with VAD end-of-speech detection.
- F3 Barge-in: stop TTS when the user starts speaking (VAD on mic during playback plus echo cancellation).
- F4 Streaming replies: stream Claude tokens, split at sentence boundaries, synthesise and play sentence by sentence to cut perceived latency.
- F5 Filler/earcon while waiting (short tone or "یک لحظه") so silence is never ambiguous.

Languages and modes
- F6 Automatic language ID: Persian, English, Italian; reply in the spoken language with the matching voice.
- F7 Translation mode: speak Persian, hear Italian or English (and the reverse).
- F8 Italian practice mode: A1-level conversation partner with Piper it_IT voice, gentle corrections, vocabulary recap.
- F9 English grammar-correction mode: corrected sentence first, then the answer.

Context and memory
- F10 Conversation history in local SQLite, with session resume (CLI `--resume` or API message history).
- F11 Prompt caching (API path) for the long system prompt to reduce cost and latency.
- F12 Personal context file (preferences, names) injected into the system prompt; stored only on device.

Phone actions (reuse this repo's Shizuku adapters and plan agent)
- F13 MCP server or tool layer exposing safe phone actions: alarms, timers, calendar read, open app, read notifications aloud, battery/network status.
- F14 Confirmation gate: any action that sends, deletes, calls or pays requires spoken confirmation; read-only by default.
- F15 Notification reader: speak selected incoming notifications in Persian.

Reliability and offline
- F16 Local LLM fallback when offline or Claude is unreachable (llama.cpp / MLC with a small multilingual model; reuse S26 local-LLM lab results). Clearly announced as "offline mode".
- F17 Cloud STT/TTS fallback when local confidence is low (optional, off by default).
- F18 Smart fallback chain per stage, following docs/smart-fallback.md.
- F19 Health and telemetry: per-stage latency, token usage, cost, error counts, stored locally; optional push to the Project IR hub.

Security and privacy
- F20 API key stored in Android Keystore or kept only on the Cloudflare relay (C7); never in the repo or logs.
- F21 Transcripts and audio stay on device unless sent to Claude; retention setting and one-tap wipe.
- F22 Restrict the Claude Code tool set in assistant mode (no shell, no file writes) when using C1/C2.
- F23 Secret scanning (Gitleaks / GitHub secret scanning) in CI before every push.

## 8. Architecture options

- A. Termux-only: Termux:API microphone, STT binary, Claude call (C1 or C5), Piper binary, playback. Fastest prototype, rough UX.
- B. Android app + Termux bridge: the existing Kotlin app does audio, VAD, STT, TTS in-process (sherpa-onnx); it calls Termux via RUN_COMMAND intent or a localhost HTTP wrapper around `claude -p`. Grey area on terms.
- C. Android app + direct API (C5) or relay (C7): no Termux needed for Claude. Clean, best UX. RECOMMENDED target.
- D. Hybrid: C for normal use, A kept as a developer console for experiments.

## 9. Milestones

- M0 Bridge feasibility: install Claude Code via C1, test login and one `-p` JSON call; separately test one API call (C5). Measure cold start and round-trip latency. Gate: choose C5/C7 for the app.
- M1 TTS bake-off: T1, T2, T3, T4, T5 on a fixed set of 30 Persian sentences (numbers, dates, English words, homographs). Blind MOS-style scoring plus real-time factor.
- M2 STT bake-off: S1, S2, S3, S4, S7, S9 on the repo's recordings plus new noisy and mixed-language clips. WER/CER, RTF, RAM, battery, temperature.
- M3 Text processing: normaliser, number/date expansion, Claude spoken-style system prompt.
- M4 Termux-only end-to-end loop (Architecture A) with per-stage latency logs.
- M5 App integration (Architecture C): push-to-talk, streaming sentence-by-sentence TTS, earcon, error voice messages, Keystore key.
- M6 Features wave 1: barge-in, language ID, history, translation and Italian practice modes.
- M7 Features wave 2: wake word, phone-action tools with confirmation gate, notification reader, offline local-LLM fallback.
- M8 Hardening and deploy: battery optimisation exemption, foreground service, network-loss handling, CI with secret scanning, signed release APK, install on S26, canary week.

## 10. Success criteria (targets, adjust after M0)

- Spoken Persian question to first spoken word of the answer under 3 s with streaming on Wi-Fi; full short answer under 6 s.
- Persian STT WER below the current best in this repo's evidence gate.
- TTS intelligible on numbers, dates and common English loanwords in at least 90% of the test set.
- Airplane mode: STT, TTS and the local fallback still work and say so.
- No credentials in the repository, logs or crash reports.

## 11. Risks

- Terms of use on the subscription route (section 2). Mitigation: API key for anything app-driven.
- Patched Claude Code binary breaking after updates. Mitigation: pin version, disable auto-update, keep backup build.
- Persian TTS naturalness and ezafe errors. Mitigation: bake-off, pronunciation dictionary, optional custom voice (T6).
- Android/Samsung killing background work. Mitigation: foreground service, wake lock, battery exemption.
- Heat and battery from large STT models. Mitigation: VAD gating, smaller models for short commands, larger only on demand.
- Non-commercial licences (MMS, some datasets). Mitigation: personal use only, document licences per model.

## 12. References

- Claude Code docs, Legal and compliance (code.claude.com/docs/en/legal-and-compliance)
- anthropics/claude-code issue 50270 (no Android build)
- ferrumclaudepilgrim/claude-code-android; gtbuchanan/claude-code-termux; markoboskoauroville/CLAUDE_CODE_TERMUX
- k2-fsa/sherpa-onnx and its TTS/ASR model lists
- rhasspy/piper-voices
- BehnamFekri/offline-tts-android (Persian Piper on sherpa-onnx)
- karim23657/awesome-Persian-Speech (Persian STT/TTS models and datasets)
- This repo: [model-lab/benchmark-protocol.md](../model-lab/benchmark-protocol.md), [model-lab/2026-10-15-asr-quality-evidence-gate.md](../model-lab/2026-10-15-asr-quality-evidence-gate.md), [smart-fallback.md](../smart-fallback.md), [android-agent/2026-10-15-offline-fa-asr-local-llm-assistant.md](../android-agent/2026-10-15-offline-fa-asr-local-llm-assistant.md)
