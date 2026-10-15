# S26 M2 — sherpa-onnx Omnilingual ASR 300M Int8 rollout

**Repository:** `MSotoudeh/s26-local-agent` (canonical Android).
**Branch:** `feat/m2-offline-asr-sherpa-20261015`.
**Milestone status:** VERIFIED ON S26: pinned sherpa-onnx Android runtime, SHA256-verified Omnilingual 300M Int8 installation and one-shot native offline inference. This is a completed **M2 Omni milestone**, not the entire multi-ASR M2 roadmap.

## Previous physical acceptance

On 2026-10-15, after M1b rollout, the device owner confirmed that a real
long-press Side-key and the spoken Persian command «تنظیمات را باز کن» opened
the Settings app successfully. This is **owner-supplied acceptance evidence**,
separate from automated test results. The current default ASR remains
`asr.vosk.fa-small-042`. Do not replace it as an experiment.

## Experiment goal / why this model

The first new ASR runtime is **sherpa-onnx**, enabling several later
alternative families without compiling private JNI tools for each model.
Compare `Meta Omnilingual ASR CTC 300M Int8` as an additional independent
offline Persian speech recognizer. The CTC model is not a Whisper model and
the available native binder is not the assistant's active ASR adapter yet.

- Upstream sherpa Android AAR: `v1.13.8`,
  `https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar`
  SHA-256 `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`.
  Source tag `11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf`.
- Android `arm64-v8a` AAR native libraries all had ELF PT_LOAD alignment
  **16384**, equal page offset and VAddr modulo 16384. The final APK must
  separately pass `Audit-16k-ELF.py` after build.
- Public ONNX model URL:
  `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12.tar.bz2`
  GitHub response length 292,571,207 bytes; verified SHA-256
  `cdcd0559c7c73efed54209a926e321afc914d046c5fdbf3665f00dc78180e5ed`.
- License: Apache 2.0 (original Meta model, converted int8 archive and
  sherpa-onnx). The alternative corpus has separate license; its data
  is NOT redistributed by this repo.
- Selected archive members: `model.int8.onnx`, `tokens.txt`, `LICENSE`;
  extracted total **365,439,124 bytes**. Individual file digests are
  checked by on-phone `SherpaOmniStore` against the signed APK asset
  `s26_omni300m_manifest.json`.
- Public Vosk test fixture used for apples-to-apples comparison:
  app local `models/vosk-model-small-fa-0.42/test/test.wav`
  SHA-256 `4a656d803a1e07eb3707365433dccdac7cf2f055d0e0aeae2084d4f6dd255bc3`.
  The public fixture is one utterance, not a defensible language-quality
  benchmark. No new microphone capture is required.

## Installation procedure and executable architecture

1. Run `pwsh -File scripts/Prepare-S26SherpaRuntime.ps1`, which downloads the
   exact official `.aar`, checks the SHA and leaves it **ignored by Git**
   in `android/mobile-agent/app/libs/`. No manual app-side downloading.
2. Prepare public model using `scripts/Prepare-S26OmniAsr.py` to verify full
   archive hash and unpack three allowlisted files. Manifest and source in
   Git, large model weights outside Git.
3. `adb push` them into the app-scoped
   `models-lab/asr/.staging-sherpa-omni300m-ctc-int8` on S26. The prior
   Vosk and Qwen files must not be overwritten.
4. `SherpaOmniStore.installVerifiedStage()` checks file sizes, SHA,
   symlink escape and complete file set, storage reserve >=30GB, and uses
   same-filesystem atomic rename into
   `models-lab/asr/sherpa-omni300m-ctc-int8`.
5. `SherpaOmniStore.heavyPreflight()`: file verification, Android
   `MemAvailable >=2GB`, `!lowMemory`, thermal status below SEVERE,
   battery >=30% if readable, and >=30GB free storage. This is an initial
   conservative profile, not proof of safety for bigger 9B/14B language
   models.
6. `SherpaOmniRunner` uses `OfflineOmnilingualAsrCtcModelConfig`,
   native `OfflineRecognizer` on CPU (3 threads), WAV PCM16 mono parser
   (public sample only), stores only transcript/latency in on-screen text,
   and releases recognizer+stream. No Android operations are triggered
   from transcripts. The main Android Assistant remains Vosk during M2
   benchmarking; there is no claimed production "switch" yet.
7. `SherpaOmniPanel` offers distinct install and one-shot benchmark buttons,
   disabled during active mic and while an experimental run is in progress.

## Explicit exclusions

The current deliverable does NOT ship paid cloud APIs, any
`Whisper.cpp` GGML weights, Qwen3-ASR weights (roughly 940MB int8 and
subject to separate hardware tests), 100-utterance Persian corpus or
turn-by-turn full ASR adapter switching. Those remain M2 follow-ups.
No reboot, no Shizuku restart, no phone-data deletion, no root, no
overwriting the existing Vosk 0.42 baseline.

## Pending gates and release evidence

- [x] New Gradle `assembleRelease lintRelease` passes.
- [x] New APK passes the signing-cert comparison, non-debuggability and
      all Native ELF page-size checks.
- [x] Existing APK saved to a separate owner-only rollback file on Ops.
- [x] Android data-preserving install `adb install -r` passes.
- [x] Live model install receipt `M2_OMNI_INSTALL_SHA256_VERIFIED_INSTALLED`.
- [x] Live model CPU inference on same fixed wav yields a text transcript,
      decode time and load time, without duplicate Android effects.
- [x] Agent/Assistant/Accessibility/mic/Vosk/Qwen remain healthy.
- [ ] Commit + push + PR reviewed and merged.

## Executed on-device receipts — 2026-10-15

- **Official runtime:** `sherpa-onnx-1.13.8.aar`, 50,129,134 bytes, SHA256 `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`, prebuilt `arm64-v8a`; all four new native libraries passed 16 KiB ELF segment alignment checks. The complete nondebuggable Release APK passed `zipalign -c -P 16` and all seven native ELFs passed `Audit-16k-ELF.py`.
- **Official model:** 292,571,207-byte model archive SHA256 `cdcd0559c7c73efed54209a926e321afc914d046c5fdbf3665f00dc78180e5ed`; three extracted public files 365,439,124 bytes. Staged via USB, verified on phone against signed manifest and atomically installed. Exact UI receipt: `M2_OMNI_INSTALL_SHA256_VERIFIED_INSTALLED | files=3 bytes=365439124`.
- **Native CPU inference on S26:** `M2_OMNI_RESULT: transcript=من اورزی اینکاررو نداره | load_ms=673 decode_ms=545 total_ms=1218 | input_hz=48000 samples=261504`. Input was the same public Vosk 0.42 `test/test.wav` (SHA256 `4a656d803a1e07eb3707365433dccdac7cf2f055d0e0aeae2084d4f6dd255bc3`). Its reference transcript is «من عرضه اين کارو ندارم». The Omnilingual output is erroneous; one fixture cannot determine language-wide WER or choose a winner. UI/ADB wall time of ~4.18 seconds includes polling and is not comparable to pure decoding.
- **Measured raw JSON:** `benchmarks/model-lab/20261015-omni300m-onphone.json`. This is an independent read-only benchmark: **no execution of Android actions from the ASR transcript and no replacement of the default voice assistant ASR**.
- **Build and regression:** `assembleRelease lintRelease` PASS; nine existing independent host Java test suites PASS, including 24,000 voice-router assertions and 24,000 model-switch assertions; destination pytest 17 passed + 3 subtests; 32-entry catalog + six negative-safety checks PASS.
- **APK:** SHA256 `88de7cafa68fd023b58e39ceb8f486a9983482de3aa53eb74771528f992757f7`, signed with same certificate as installed baseline, non-debuggable, data-preserving `adb install -r` succeeded. Previous APK retained on Ops at `C:\Users\mmsot\AppData\Local\ProjectIR\backups\s26-agent\20261015-before-sherpa-m2.apk` (SHA256 `9c5e417f4912dda3143abc956a9e224bf26d670a2f9e05a2c04ca827d6b0f92d`).
- **Post-install health:** Agent foreground process running, Assistant role held by `io.projectir.mobile`, microphone permission granted, Vosk 0.42 and existing Qwen model present. Immediately after APK update Project IR's Accessibility service was stuck in binding; only its service was narrowly toggled in the enabled-services setting and the original complete string was restored byte-for-byte, preserving Bitwarden and KDE Connect. Accessibility bridge subsequently reconnected. Shizuku was untouched and still running.
- `ACTION_ASSIST` launch test with explicit `SKIP_ASSIST_MIC=true` passed with `ASSIST_READY`, Vosk/Qwen installed and `ASR_SELECTED_MODEL=asr.vosk.fa-small-042`. **Physical Side-key invocation with this exact M2 APK has not yet been retested by the owner**; the owner had verified the physical Side-key command on M1b before this upgrade.

## What's next (not claimed complete)

Full M2 needs on-device Android adapters for Whisper multilingual and Qwen3-ASR, real cross-runtime active-ASR switching with native load/cancel/rollback and a larger fixed Persian utterance corpus. Installing a model does not mean it is selected for Side-key speech. No model weights or signed APK are committed to Git; reproducible download manifests, source and receipts are.
