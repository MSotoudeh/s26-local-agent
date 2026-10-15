# S26 Model Lab — candidate catalog and benchmark contract

This folder is the **roadmap and machine-readable specification**, not an
installer and not evidence of installed candidate models.

- `catalog.json`: 31 alternative model/algorithm entries across
  Persian ASR, VAD, Persian TTS, local LLM/VLM and screen perception.
  Status `installed_verified` means installed from prior device work;
  `planned`, `gated_approval`, `not_auto_install` and
  `license_blocked_for_commercial` are NOT installed by this plan.
- `providers.json`: alternative web-search services, OCR and compute
  backends, all with provider privacy/cost and compatibility gates.
- `scenarios.json`: 30 public/synthetic *definitions*, no actual
  personally identifiable recordings, screenshots or model results.
- `../../docs/S26_MODEL_LAB_MASTER_PLAN_20261015.md`: Persian full
  system architecture, install/switch/rollback/release milestones.
- `../../docs/S26_MODEL_LAB_BENCHMARK_PROTOCOL_20261015.md`:
  ASR/LLM/VLM/TTS/E2E/heavy evaluation details.
- `../../scripts/validate_s26_model_lab.py`: validation of all model
  entries, providers, schema/permissions, conservative memory and
  storage budget, and six intentional negative safety tests.

Validate (on Ops or any PC with Python 3.10+):

```powershell
python scripts/validate_s26_model_lab.py --self-test
git diff --check
```

Verified target: Samsung SM-S948B, SoC SM8850, Android API 37,
MemTotal 11,350,972 KiB, MemAvailable 4,957,864 KiB snapshot,
161 GB free `/data` snapshot at October 15, 2026.

## Next implementation entrypoint

1. Isolated feature branch off this docs checkpoint.
2. Implement native `LocalModelAdapter`, `ModelStore`,
   `ModelSwitcher`, `BenchmarkScenarioRunner`, `MetricsCollector`.
3. Write instrumentation tests with two **tiny fake models** and
   uninstall-free atomic swap/revert; retain current baseline.
4. Implement one real alternative per category and verify on-device
   before larger model downloads. Download on Ops, stage with hashes,
   never embed model weights in Git.
5. Expand UI to choose each installed ASR, LLM/VLM, TTS and VAD, then
   perform side-by-side A/B on one fixed input.
6. Stage quantized heavy 9B and 14B only after RAM/thermal watchdog
   checks, logging not-feasible results without freezing the phone.

Explicit non-goals of the **current plan commit**: installing model
weights, switching live Assistant, stopping Shizuku, consuming API
credits, touching Telegram, deleting any file, rebooting the phone or
taking an unconsented screenshot.
