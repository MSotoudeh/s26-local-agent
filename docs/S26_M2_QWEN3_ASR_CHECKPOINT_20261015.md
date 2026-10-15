# S26 M2 Qwen3-ASR 0.6B Int8 — isolated checkpoint

Canonical product repository: `MSotoudeh/s26-local-agent`.
Branch: `feat/m2-qwen3-asr-06b-20261015`; based on merged Whisper M2 PR #5 (main `0e759c7`).

## Scope and real implementation

- Official upstream source: `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25.tar.bz2`; reported archive Content-Length **878,702,423 bytes**.
- Original Qwen/Qwen3-ASR-0.6B is Apache-2.0. Target public model is its sherpa-onnx ONNX Int8 conversion. This is not a private or paid model and does not require user-account login.
- Reuses installed `sherpa-onnx` Android v1.13.8 AAR, pinned SHA256 `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`; no AAR binary or model weights checked into Git.
- `SherpaQwen3Runner` configures `OfflineQwen3AsrModelConfig`, native `OfflineRecognizer`, 2 CPU threads, expected `conv_frontend.onnx`, `encoder.int8.onnx`, `decoder.int8.onnx` and `tokenizer/` directory.
- Source-verified compatibility caveat: the sherpa-onnx **v1.13.8** `OfflineModelConfig::Validate` exempts Qwen3-ASR from an otherwise mandatory standalone `tokens.txt`; tokenizer files reside in `tokenizer/`. This was explicitly corrected during implementation.
- `SherpaQwen3Store`: app-scoped **staging → SHA256 of each file against signed APK manifest → same-volume atomic rename**. Rejects unknown traversal/symlinks, duplicates, non-expected files, <30GB free disk, <3GB available RAM, battery <30%, and Android thermal severe or higher. Baseline models are not overwritten.
- `SherpaQwen3Panel` adds isolated one-shot benchmark controls to Model Lab. No new Android action can be issued by model output, and the Side-key still uses Vosk 0.42.

## Qwen3 download/rollout gates

- [x] Source URL, byte length, supported Java Runtime API and Apache license identified.
- [x] Android Java source for model Runner, store, panel, UI wiring written.
- [x] Public upstream compressed model fully downloaded and its SHA256 pinned in committed immutable release lock.
- [x] Secure unpacker produces complete signed asset manifest with all ONNX/tokenizer files and per-file digests; native adapter built again with manifest.
- [x] Signed release APK `assembleRelease lintRelease`, 16KiB ELF checks and APK certificate matching original app.
- [x] Device preflight/rollback APK backup, staged USB file transfer, in-app SHA256/atomic install.
- [x] Actual S26 CPU inference of the identical public Persian WAV and capture of transcript, model load/decode, memory/thermal controls.
- [x] Revalidate default Vosk 0.42, microphone/Assistant, Accessibility, existing Qwen LLM, Whisper and Omnilingual.
- [x] Publish tested source, raw on-phone result, per-file hash proofs and ModelStore with a reviewed pull request.

Pinned archive SHA256: `393f8a14e2f5fb96746aaab342997a40641001fbd5bf9592a080a8329178ee96`. Complete extraction: **6 files, 987,015,347 bytes**, all independently hashed. ADB staged all six to S26 without changing the active Assistant. Source/manifest tests **27 passed + 3 subtests**.

A model-source compile, download progress or cached metadata is NOT proof of on-phone Qwen3-ASR execution.

## Reproducibility / interruption resume

Detached Project IR download on `ops` is tracked as run `333a5932378b4f5dbd1f58d48e525f81`. Download location (outside Git):
`C:\Users\mmsot\AppData\Local\ProjectIR\model-lab\s26\qwen3-asr-06b-int8\sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25.tar.bz2`.

After completion, pin final SHA in `android/model-lab/qwen3-asr-0_6b-int8-lock.json`, execute `scripts/Prepare-S26Qwen3Asr.py --archive ... --out ... --manifest ... --lock ...`. Never auto-extract a partial archive and never commit ONNX weights or device data.

Broader backlog: Whisper/Omni fully switchable production ASR, 100+ Persian reference utterances and WER/CER benchmark harness, TTS voice alternatives, independent LLM/VLM runtime adapters, and safely gated large 9B/14B profiling.

## Actual S26 on-phone acceptance (2026-10-15)

- **Build:** Signed release `assembleRelease lintRelease` PASS. APK SHA256 `d023f3598a088954f5bfd7656e59e8cb82dc033c46037a9673f285858b105f0f` matched previous signer and passed non-debuggable packaging, `zipalign -c -P 16` and 16K ELF validation for all seven Native libraries. Previous APK SHA256 `ab06d78e9b211f1bb3f10aee0ff5778637a2c9a6fd725bfafe5a301dd6e82d1e` saved at `C:\Users\mmsot\AppData\Local\ProjectIR\backups\s26-agent\20261015-before-qwen3-asr-m2.apk`.
- **Integrity and installation:** All six pre-staged ONNX/tokenizer files passed SHA256 on the phone against the signed manifest. Exact in-app receipt: `M2_QWEN3_ASR_INSTALL_SHA256_VERIFIED_INSTALLED | files=6 bytes=987015347`; staging was atomically renamed to the final app-scoped directory. Data-preserving `adb install -r` succeeded; no wipe, reboot or Shizuku modification.
- **Real native CPU inference:** On the same public Vosk 0.42 `test/test.wav`, the S26 returned `M2_QWEN3_ASR_RESULT: transcript=Man Ordeia encarrou Nadara. | load_ms=2680 decode_ms=644 total_ms=3324 | input_hz=48000 samples=261504`. Host/UI polling took 6196 ms. The output was Latin-script and differs markedly from Persian ground truth «من عرضه اين کارو ندارم». Do **not** promote Qwen3-ASR as production default.
- **Post-device regression:** Agent process and foreground service alive, Android Assistant role remained `io.projectir.mobile`, microphone grant and Accessibility bridge PASS, Bitwarden and KDE Connect Accessibility settings preserved, production ASR remained `asr.vosk.fa-small-042`, and Omnilingual/Whisper/Qwen3 weights were present. No new costs. Owner has not yet repeated physical Side-key acceptance on this exact APK.
- **Structured evidence:** `benchmarks/model-lab/20261015-qwen3-asr-06b-onphone.json`. One fixed public WAV is not statistically meaningful accuracy coverage. Native load/decode time and full UI elapsed are different metrics.
- **Interrupted-run coordination:** Durable benchmark request `e67dbdb5-fdce-45d8-b923-92846d9423a3` was stuck at `dispatched` without being executed. A separate direct one-shot read-only transcription succeeded; afterwards the app UI was sent Home (foreground service kept running) so any late-dispatched UI benchmark fails closed. Follow-up status check confirmed the stranded run ultimately timed out without ever starting; no duplicate inference occurred.
