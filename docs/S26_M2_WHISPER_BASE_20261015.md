# S26 M2 Whisper-base multilingual Int8 checkpoint (2026-10-15)

Canonical repository: `MSotoudeh/s26-local-agent`.
Branch: `feat/m2-whisper-multilingual-20261015`.
Status: on-device Whisper install, native decoding and baseline health confirmed; the change is published through a separate PR. The broader multi-runtime M2 roadmap remains incomplete.

## Purpose and scope

A third independent, **offline Persian ASR runtime/model profile** in the on-device Model Lab, alongside the existing Vosk 0.42 and Vosk 0.5 benchmarks and sherpa-onnx Omnilingual 300M Int8. Whisper Base multilingual ONNX is a research benchmark, **not** the default Side-key assistant or an Android action executor. Vosk 0.42 remains the selected voice assistant recognizer.

Whisper shares the verified Android sherpa-onnx 1.13.8 runtime AAR, SHA256:
`633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`.
Signed APK fetch instructions: `scripts/Prepare-S26SherpaRuntime.ps1`.
No network, payment, account or user-side download required for the public package.

## Source provenance, artifact and integrity

Official public sherpa-onnx release:
`https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.tar.bz2`.
Pinned archive is 207,557,382 bytes and SHA256:
`911b2083efd7c0dca2ac3b358b75222660dc09fb716d64fbfc417ba6c99ff3de`.

The `scripts/Prepare-S26WhisperBase.py` script rejects unexpected archive roots/types, symlinks, duplicate required files and size/hash mismatches, then selects only these three model files:

- `base-encoder.int8.onnx` — 29,120,534 bytes.
- `base-decoder.int8.onnx` — 130,672,026 bytes.
- `base-tokens.txt` — 816,730 bytes.

Combined extracted artifact size is **160,609,290 bytes**.
`android/mobile-agent/app/src/main/assets/s26_whisper_base_manifest.json` pins independent SHA-256 for each file, and the Android `SherpaWhisperStore` verifies these files before atomic installation. All model weights, downloaded AAR libraries and the APK itself are excluded from Git.

Device staging directory:
`/sdcard/Android/data/io.projectir.mobile/files/models-lab/asr/.staging-sherpa-whisper-base-int8`.

After device verification, final directory:
`/sdcard/Android/data/io.projectir.mobile/files/models-lab/asr/sherpa-whisper-base-int8`.

## Application and safety behavior

`SherpaWhisperRunner` creates `OfflineWhisperModelConfig` specifying the encoder, decoder, tokens, Persian language `fa`, transcription task, CPU provider, and 2 CPU threads. It reads the same fixed public WAV test file as previous Vosk/Omni evaluations:
`vosk-model-small-fa-0.42/test/test.wav`, SHA256 `4a656d803a1e07eb3707365433dccdac7cf2f055d0e0aeae2084d4f6dd255bc3`.
It measures native model load and decode in monotonic elapsed milliseconds, never routes recognized text to Android actions, and releases the native stream/recognizer.

`SherpaWhisperPanel` presents **separate** install/verify and benchmark buttons in Model Lab. A benchmark requires signed-manifest verification, ≥2GB available memory, ≥30GB free disk, battery ≥30% where visible and thermal below severe, with no active microphone; unverified models are never activated. The switcher for the actual Side-key remains Vosk 0.42/0.5 only.

## Evidence/acceptance checklist

- [x] Download public model from official release and record source SHA.
- [x] Manifest generated with three per-file SHA and expected expanded size.
- [x] Verify the already-staged three files **on the phone** by full SHA256 against the same manifest (all PASS).
- [x] Build signed, nondebuggable `assembleRelease lintRelease`, verify all native 16KB ELF segments, backup currently installed app.
- [x] Install new APK data-preserving and prove Assistant/mic/Accessibility stable.
- [x] Install model from verified staging using Android SHA256 and atomic rename (record exact receipt).
- [x] Execute actual native Whisper Base Persian transcription of common WAV on S26, record transcript, native load/decode times, or exact failure.
- [x] Preserve Vosk 0.42 active, existing Vosk 0.5/Omnilingual/Qwen files, Shizuku and non-Project IR Accessibility services.
- [ ] Commit and push to GitHub, then merge only with tested receipts.

## Confirmed S26 device receipts (2026-10-15)

- From a previous interrupted attempt, the 3-model-file `*.tar.bz2` extraction and creation of the signed-APK manifest were complete. Its exact pinned archive SHA-256 and all three file hashes matched on `ops`. Rather than downloading/transferring twice, the existing app-scoped Android **staging** directory was verified against the manifest via on-device `sha256sum`: `WHISPER_STAGE_ALL_BYTES_AND_HASHES_PASS=YES`.
- First Gradle build was blocked by missing `decoder` local variable and omitted Whisper encoder/decoder configuration; corrected both. Then **`assembleRelease lintRelease` PASS**. The APK passed release-signing-certificate equality against the existing app, nondebuggable packaging, `zipalign -c -P 16`, and all seven native ELF libraries passed 16 KiB PT_LOAD alignment checks.
- Current app APK saved before upgrade at `C:\Users\mmsot\AppData\Local\ProjectIR\backups\s26-agent\20261015-before-whisper-base-m2.apk`, SHA256 `88de7cafa68fd023b58e39ceb8f486a9983482de3aa53eb74771528f992757f7`. Upgraded APK SHA256 `ab06d78e9b211f1bb3f10aee0ff5778637a2c9a6fd725bfafe5a301dd6e82d1e`. Data-preserving `adb install -r` returned Success; no app data removed and no reboot.
- **Android atomic model installation:** `M2_WHISPER_INSTALL_SHA256_VERIFIED_INSTALLED | files=3 bytes=160609290`. The app verified all three expected file sizes/hashes from its signed manifest and moved the staging directory into the permanent app-scoped path.
- **Real native on-phone CPU inference on the exact same public WAV:** `M2_WHISPER_RESULT: transcript=من ارزیه ان کار رونداره | load_ms=393 decode_ms=402 total_ms=795 | input_hz=48000 samples=261504`. The transcript has substantial errors versus the reference «من عرضه اين کارو ندارم». 4,165 ms UI/ADB wall-clock includes polling delays. This one fixture is insufficient to rank Persian models. Raw record: `benchmarks/model-lab/20261015-whisper-base-onphone.json`.
- **Regression:** 21 Python tests plus three subtests passed, 33 catalog candidates validated and six negative safety tests passed. Post-upgrade checks found `io.projectir.mobile` still holding Assistant role, service running in foreground, mic permission and Project IR Accessibility connection alive; Bitwarden and KDE Connect Accessibility settings preserved. On-screen `ASR_SELECTED_MODEL=asr.vosk.fa-small-042` and 33 catalog entries confirmed. No Shizuku restart.
- Whisper remains an **independent benchmark option**. There is **no** release claim that it handles Side-key commands in place of Vosk; the owner has not repeated the physical Side-key voice test for this exact Whisper APK.

## Important remaining full roadmap

One public WAV does not support a statistically credible WER ranking. Need a larger consented Persian evaluation corpus, then verify additional Qwen3-ASR and Whisper models and add a real multi-runtime in-app safe switch/rollback system; TTS, VLM and 9B+ thermal-managed model experiments are later milestones. Do not silently equate public candidate catalog entries with installed/functional phone runtimes.
