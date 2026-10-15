# S26 Model Lab M1b — first verified real model A/B

Status: **IMPLEMENTATION IN PROGRESS** — do not mark final until live phone
receipts and merged commit are included.

Canonical project: https://github.com/MSotoudeh/s26-local-agent
Baseline after PR #2: `d9ca088b228ef45056e7cbaa39ac2cd55ed77832`.

## Objective and independent variable

Compare two genuine offline Persian ASR weights on the *same* Vosk 0.3.75 JNI
runtime to isolate the effect of model choice:

- A: `asr.vosk.fa-small-042` → existing installed
  `vosk-model-small-fa-0.42` (baseline; untouched).
- B: `asr.vosk.fa-small-05` → public
  https://alphacephei.com/vosk/models/vosk-model-small-fa-0.5.zip
  (Apache-2.0); vendor ZIP 62,153,530 bytes
  SHA-256 `7e5697239c6bcbfd4188659bb92c8603cd3228a8a4370d789aaeb9d2eeac42c9`.
  It is **not** necessarily a quality upgrade. Vosk reports the small 0.5
  model has higher WER than small 0.42 in their published Persian benchmarks:
  31.2 vs 23.4 CV17, 26.2 vs 14.0 Fleurs.

## Transfer/security implementation

1. Fetch from official HTTPS origin and verify exact declared Content-Length
   and pinned SHA-256; do not commit model weights to Git.
2. Extract with `scripts/Prepare-S26VoskModel.py` which bounds expanded bytes,
   rejects ZIP traversal, symlinks and collisions, and computes a deterministic
   SHA-256+length list for all 18 model files. Expanded size: 138,541,153 bytes.
3. Pin per-file hashes in `android/mobile-agent/app/src/main/assets/
   vosk_fa_small_05_manifest.json` within signed non-debuggable APK.
4. Transfer into **app-scoped external storage** under
   `models-lab/asr/.staging-vosk-model-small-fa-0.5`; never overwrite
   `models/vosk-model-small-fa-0.42` or existing candidate installs.
5. `ModelLabModelStore.installStagedCandidate`: verify every listed size and
   SHA-256 in background, require storage reserve >=30GB and atomic rename to
   `models-lab/asr/vosk-model-small-fa-0.5`; no deletion on failed install.
6. UI Model Lab allows selecting *only these two verified Vosk runtime IDs*
   for real ASR switch; unsupported TTS/LLM/VLMs cannot be "activated" yet.
   Switch unloads previous JNI model before loading candidate and creates
   a Recognizer to verify load. Only then persists selection in private prefs.
   On failures preserve previous selection and try to reload prior weights.
7. Bundled Vosk sample `test/test.wav` from the baseline is used **identically**
   when either model is selected: a comparison must not use different WAVs.

## Exit gates

- Android `assembleRelease lintRelease` PASS, host model switch safety
  simulations PASS, manifest parser/ZIP rejection tests PASS.
- APK same certificate as installed, non-debuggable, 16K native ELF + ZIP
  compatible, and rollback APK pulled to Ops **before** upgrade.
- Signed `adb install -r` PASS without clearing app data.
- Staging hash verified in app, atomic install PASS, native Model 0.5
  load/Recognizer health PASS, baseline 0.42 reactivation PASS.
- One common 48-kHz WAV test under both ASR models and output transcription
  captured with exact model ID and elapsed times. Do not claim generic WER
  improvement based on one English/other sample.
- Android Assistant holder, microphone permission, ForegroundService,
  Accessibility, Vosk and Qwen baseline and Shizuku remain healthy.
- Commit and Push to `s26-local-agent`; only merge after tests pass.

## Remaining after M1b

Actual alternative runtimes (Piper TTS, Whisper.cpp, sherpa-onnx Qwen3-ASR,
Omnilingual, llama.cpp/GGUF and VLM) are separate integrations. ModelStore
resumable online downloads, TTS playback, real voice fixture dataset,
metrics/power/thermal dashboard and 9B/14B heavy profiles still require
their dedicated milestones. One verified second Vosk model demonstrates the
switch but does not complete every requested alternative.
