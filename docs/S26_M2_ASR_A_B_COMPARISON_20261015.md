# S26 Model Lab M2 — reproducible offline native ASR A/B comparison

Canonical repository: `MSotoudeh/s26-local-agent`.
Branch: `feat/m2-asr-ab-comparison-20261015`, based on M2 Qwen3 merge commit `c73b1e9`.

## Scope

Three previously verified public offline ASR adapters can be selected from a native Android spinner or invoked in a **single same-utterance A/B compare-all**. Their original separate installation/benchmark buttons remain. A shared `ModelLabBenchmarkGate` serializes all install/benchmark operations across the four panels, avoiding accidental concurrent native model load. The production Samsung Side-key Assistant continues to use verified Vosk 0.42 and is never switched by these benchmark controls.

Tested models (their compressed public archives and weights are not committed to Git):

- `asr.omni_ctc_300m.int8` — sherpa-onnx Omnilingual CTC 300M int8
- `asr.whisper.base.int8` — sherpa-onnx multilingual Whisper Base int8
- `asr.qwen3.0_6b.int8` — sherpa-onnx Qwen3-ASR 0.6B int8

All three runners now use the same declared CPU provider and two threads. `SherpaOmniRunner` moved from three threads to **two for this comparison release**, so prior Omni speed receipts (three threads) are not directly comparable with new A/B receipts.

## A/B protocol

1. Assert microphone/Assistant ASR isn't currently active; acquire the shared single-operation lock atomically.
2. SHA256-verify exact public reference WAV `vosk-model-small-fa-0.42/test/test.wav` (fixed SHA256 `4a656d803a1e07eb3707365433dccdac7cf2f055d0e0aeae2084d4f6dd255bc3`). No recording or network API.
3. For each selected runtime, validate signed manifest hashes, app-scoped installed model files, remaining disk budget, `MemAvailable`, battery and thermal. Abort the all-run at the first failure (fail closed).
4. Load one CPU recognizer, transcribe the exact same WAV, record native load/decode time, transcript, before/after Android `availMem`, thermal and battery; release native recognizer and stream before next model.
5. Verify the active production Vosk identifier was unchanged. Write the session as an immutable JSON file with a temporary name + synchronous flush + atomic rename into `/sdcard/Android/data/io.projectir.mobile/files/benchmarks/`.
6. Display all recognized texts/timings on-screen, but do not execute commands derived from them. 100+ independent reference utterances are still required for real-world Persian WER/CER ranking.

**Limitations:** A single utterance, one iteration and possible warm-cache/order effects do not establish statistical confidence, p50/p95, reliable RAM peaks or relative Persian accuracy. Per-run Android memory before/after measures available memory, **not** peak native memory or process PSS. Exact phone-side battery/thermal and benchmark results must be recorded rather than inferred.

## Acceptance checklist

- [x] Added native A/B panel, read-only per-model selector, compare-all, shared gate and JSON receipt.
- [x] Explicit fixed public WAV SHA, identical CPU worker counts and per-engine runtime preflights in code.
- [x] `assembleRelease lintRelease`, signer, nondebuggable and native 16K ELF checks pass.
- [x] APK data-preserving upgrade and post-install Assistant, mic, Accessibility and model-store health all PASS.
- [x] Actual one-click three-model benchmark on S26 returns 3 per-engine native transcripts/timings and an auditable JSON receipt.
- [x] Host Python checks, Android host Java regression suite and device-side evidence pass.
- [x] Publish reviewed source, on-phone three-model receipt and safe UI comparison through a pull request.

The real physical Side-key voice command on this exact comparison APK should be revalidated by owner when practical, but the device can be safely tested without changing its production assistant model.

## Physical S26 A/B execution receipts — 2026-10-15

- Release APK SHA256 `9d35c5c2864a060fb1f3baf63e23f851e2782b7f6df9103371762bb64c3cfc70`, signing certificate matched prior Android agent APK, 16KiB ELF and zipalign PASS, non-debuggable. Prior stable Qwen3 APK backed up on Ops: `C:\Users\mmsot\AppData\Local\ProjectIR\backups\s26-agent\20261015-before-asr-comparison-m2.apk`, SHA256 `d023f3598a088954f5bfd7656e59e8cb82dc033c46037a9673f285858b105f0f`. Data-preserving `adb install -r` PASS; core agent alive/foreground, role/mic/Accessibility intact.
- Android UI compare-all button: `io.projectir.mobile:id/m2_asr_compare_all`, on-device receipt `M2_COMPARE_PASS_3_MODELS | saved=m2-asr-compare-1792082481218-29678.json`.
- Fixed public audio (SHA256 `4a656d803a1e07eb3707365433dccdac7cf2f055d0e0aeae2084d4f6dd255bc3`), identical CPU provider, two threads each:
  - Omnilingual 300M: transcript «من اورزی اینکاررو نداره», **load=673ms decode=445ms native_total=1118ms**
  - Whisper Base: transcript «من ارزیه ان کار رونداره», **load=382ms decode=392ms native_total=774ms**
  - Qwen3-ASR 0.6B: transcript `Man Ordeia encarrou Nadara.`, **load=2524ms decode=642ms native_total=3166ms**. This Latin-script result differs from the reference Persian text.
- Phone/ADB wall time (UI polling plus receipt write) was around 12.4s, not comparable with native decode time. Thermal status before each model was 0 (Android's reported thermal status, not degrees). Available memory before each model was approximately 5.21GB / 5.21GB / 5.07GB; **not** native process peak RSS/PSS. Battery and further resource metadata are in the JSON.
- Immutable original app-scoped phone report: `/sdcard/Android/data/io.projectir.mobile/files/benchmarks/m2-asr-compare-1792082481218-29678.json`. Verified byte-for-byte copy in repo: `benchmarks/model-lab/20261015-asr-three-way-onphone.json`, SHA256 `4a874fcdc4d2562ceb65e4fbc5fe6d0eda67590df698254ca9a059748bcd2560` (2,303 bytes).
- Production Assistant ASR at start and end: `asr.vosk.fa-small-042`, and `intent_effects=0`. No user audio collected, no Android actions routed from inference, no model file or Shizuku changes.
- All **34 Python tests / 3 subtests** PASS prior to deployment, plus standalone 100x16=1,600 concurrency-gate race checks PASS. Native release compiled and lint PASSED.

Only a single fixed public test utterance was used: this is a genuine latency/functionality smoke check, **not a representative Persian word-error-rate study**. User-supplied 100+ recordings remain optional future quality validation. Physical Side-key voice command on the exact comparison APK has not been separately retested by the owner.
