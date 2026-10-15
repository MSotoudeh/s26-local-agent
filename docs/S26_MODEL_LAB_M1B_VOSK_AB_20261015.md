# S26 Model Lab M1b — first verified real model A/B

Status: **DEVICE INSTALL + A/B SWITCH + ONE SHARED WAV VERIFIED**;
release published only after GitHub PR review/merge. Full model catalog remains
future work.

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

## Live S26 proof (executed)

- Official public ZIP: **62,153,530 bytes**,
  SHA256 `7e5697239c6bcbfd4188659bb92c8603cd3228a8a4370d789aaeb9d2eeac42c9`.
  Safe extraction verified all **18 model files**, total 138,541,153 bytes.
- `android/mobile-agent/app/src/main/assets/vosk_fa_small_05_manifest.json`
  pins every file's path, byte length, SHA256; weights are **not** committed.
- USB ADB pushed the new 18 files **only into app-scoped staging**.
  The live Android `ModelLabModelStore` UI returned:
  `MODEL_LAB_INSTALL_INSTALLED_SHA256_VERIFIED | files=18 | bytes=138541153`.
  Original Vosk 0.42 weights remained untouched; staging was atomically renamed.
- New Vosk 0.5 model on the S26: `ASR_SWITCH_OK_asr.vosk.fa-small-05`
  after the pinned hash checks, native Model and Recognizer load.
  Same-device return to old model:
  `ASR_SWITCH_OK_asr.vosk.fa-small-042`, persisted as default.
- **Same on-device Vosk Persian WAV**: SHA256
  `4a656d803a1e07eb3707365433dccdac7cf2f055d0e0aeae2084d4f6dd255bc3`,
  523,052 bytes, 48 kHz. Reference from Vosk `test/test.txt`:
  «من عرضه اين کارو ندارم». Both runs passed:
  - model 0.42 → «من عرضه این کار رو ندارم»;
    literal normalized WER **0.4**, CER **0.0556**, wall 4,188 ms.
  - model 0.5 → «من عرضه این کار را ندارم»;
    literal normalized WER **0.4**, CER **0.1111**, wall 4,199 ms.
  - WER here treats «کارو» as one token, while both model outputs
    tokenize the phrase as two tokens; colloquial equivalence is
    not assumed. **One file cannot rank overall Persian accuracy**.
    Timings include 2-second host polling intervals, Android UI
    interaction and ADB overhead; not a pure inference benchmark.
  - Machine-readable result and score function:
    `benchmarks/model-lab/20261015-vosk-fa-small-042-vs-05.json`,
    `scripts/Evaluate-S26VoskAB.py`.
- Android Release `assembleRelease lintRelease`: PASS with JDK25.
  Host 9 standalone Java suites PASS (24,000 model-switch assertions),
  destination Python tests 11 + 3 subtests PASS; model catalog validator
  32 candidates and six negative safety cases PASS.
- Final installed APK after UI refresh: SHA256
  `9c5e417f4912dda3143abc956a9e224bf26d670a2f9e05a2c04ca827d6b0f92d`.
  Release keeps original signing certificate, non-debuggable APK and
  16 KiB alignment. Upgrade via `adb install -r` preserved Agent process,
  ForegroundService, Android Assistant role, mic permission and Accessibility.
  After final install, catalog 32 entries, Vosk/Qwen baseline files and
  `ASR_SELECTED_MODEL=asr.vosk.fa-small-042` confirmed. Vosk 0.5 is
  visibly `[FILES PRESENT, VERIFY ON SWITCH]`.
- Rollback APKs on Ops:
  `C:\Users\mmsot\AppData\Local\ProjectIR\backups\s26-agent\20261015-before-modelstore-vosk-ab.apk`
  (SHA256 `fd25a3c5...`) and
  `C:\Users\mmsot\AppData\Local\ProjectIR\backups\s26-agent\20261015-before-vosk-ui-refresh.apk`
  (SHA256 `dde69b2...`). Neither backup nor model weights is in Git.
- The manifest Catalog status `planned` is repository-portable; actual
  physical model presence/verification is device-specific and shown
  dynamically by Model Lab. Do not interpret catalog `planned` as
  the absence of a successful on-phone install.
- **Owner's physical Side/Power-key spoken-command check is still pending
  after this latest APK**, although previous releases passed and automated
  role/mic/Accessibility checks all pass. No reboot or Shizuku restart.

## Remaining after M1b

Actual alternative runtimes (Piper TTS, Whisper.cpp, sherpa-onnx Qwen3-ASR,
Omnilingual, llama.cpp/GGUF and VLM) are separate integrations. ModelStore
resumable online downloads, TTS playback, real voice fixture dataset,
metrics/power/thermal dashboard and 9B/14B heavy profiles still require
their dedicated milestones. One verified second Vosk model demonstrates the
switch but does not complete every requested alternative.
