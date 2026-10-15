# S26 Model Lab M1 — initial executable foundation (2026-10-15)

Canonical repository: `MSotoudeh/s26-local-agent`. This checkpoint is **M1 scaffolding**, NOT full multi-model runtime execution and NOT proof that alternative model weights have been installed.

## Scope completed in the branch

1. Pure-Java `ModelLabSwitchMachine`: an adapter/registry/journal transaction. A switch only commits when the candidate has passed `load()` and `health()`; precondition checks reject unknown/unverified models. The existing loaded adapter is unloaded before the next one to avoid overlapping heavyweight resident weights. On failed load, failed health or failed persistence, the previous adapter is reloaded when feasible and an explicit rollback receipt is returned. If rollback cannot be made healthy, the operation fails closed and does not advance the persisted active model pointer.
2. Deterministic `ModelLabSwitchMachineTest` uses two fake models and no Android effects to test same-model no-op, unverified/invalid IDs, proper unload-before-load, successful activation, failing loads, health failure, journal refusal, old-model unload errors and rollback.
3. Read-only `ModelLabInventory` parses the bundled 31-entry catalog. It uses **real installed files or Accessibility connection** to mark only known baseline runtimes available; other entries remain `NOT INSTALLED` even if catalog metadata mentions a model.
4. New `ModelLabPanel` added to the bottom of the Android app UI. It has category and model selectors and a read-only Inspect action; it deliberately does **not** offer an active Switch button until new runtime adapters and actual compatibility tests exist. Thus the user sees what is installed vs planned without falsely changing a runtime.
5. Catalog JSON is included as Android asset `app/src/main/assets/s26_model_lab_catalog.json`.

## Tests and acceptance

- `ModelLabSwitchMachineTest`: 24,000 assertions over 1,000 independent fixture cycles with no device effects.
- Android release Build and lint: must pass before any install or merge.
- 31-entry model-lab catalog remains schema-validated and passwords/weights are excluded from source control.
- No model weights downloaded; no Hub/Shizuku restarts, phone reboot, app data deletion, or paid requests.

## Next required M1 tasks (not represented as done)

- Implement production `ModelStore`: resumable downloads, verified digest, filesystem atomic rename and capacity/availability gates, plus real on-device `ModelAdapter` bridges with explicit init/warmup/unload and reallocation monitoring.
- Wire production `ModelLabSwitchMachine` to those real adapters and dedicated persisted selected-model keys; only then enable Switch UI.
- Implement a fixed-input Android benchmark runner and repeatable result receipts. Add a first **second** verified model (prefer Piper Persian TTS or Whisper small for measured warm/cold activation).
- Publish an APK only after preserving signer, existing app data, a backup and running Android and voice tests; avoid disrupting other parallel Project IR conversations.

## Existing active device configuration

The on-phone baseline was updated via signed data-preserving install after passing the M1 release preflight. The rollback APK is retained on Ops; this implementation contains read-only catalog selection, not runtime switching between alternative models.
## Live phone rollout receipt (additional verification)

- Device: authorized Samsung S26 Ultra, Android API 37. No reboot or phone-data reset.
- M1 APK SHA-256: fd25a3c5f8bf9095e885bb7ea90effe5f3cd315fc438fe30220829f57a718ae5.
- Pre-upgrade APK backed up on Ops at C:\Users\mmsot\AppData\Local\ProjectIR\backups\s26-agent\20261015-before-canonical-m1.apk; SHA-256 b277a5314707709988746fb6272e278745ef5f96492e17f7432f22b52b5e27d7.
- Certificate matches the pre-upgrade installed APK; non-debuggable and 16-KiB-aligned zipalign checks PASS.
- Data-preserving adb install -r returned Success; Agent process, ForegroundService, Android Assistant role and microphone grant preserved.
- Accessibility provider connected; foreground MainActivity displays Model Lab 31-entry catalog and selection, voice-ready banner, Vosk installed and Qwen installed.
- Native ACTION_ASSIST launch with SKIP_ASSIST_MIC=true: PASS, compatibility dialog absent; no microphone automatically activated by the test.
- Live on-device bundled public Persian WAV recognition returned ASR_BUNDLED_SAMPLE_OK_OFFLINE.
- Live on-device Qwen2.5-0.5B returned a nonempty 20-character inference response, model process remained running.
- Physical Side/Power-key invocation after this precise upgrade has NOT been retested by owner; previous pre-M1 live voice test passed.
- Alternative ASR/LLM/TTS/VLM models remain NOT installed and production activation UI is intentionally disabled until adapter integrity tests.
