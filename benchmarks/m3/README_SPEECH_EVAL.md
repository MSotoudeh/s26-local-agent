# Persian command ASR / intent benchmark protocol

Date: 2026-10-13
Status: **scaffold and synthetic text corpus only; no real audio recognition result**

The local-only evaluator is
`scripts/s26-agent-eval/evaluate.py`. It accepts separate JSONL reference
and prediction files keyed by an opaque `id`, refuses mismatched sample IDs,
and reports:

- Intent: accuracy, macro-F1, per-label precision/recall/F1 and confusion.
- ASR: Unicode-normalized character error rate (CER), word error rate (WER)
  and entity preservation when entity labels are supplied.

All text normalization is deterministic and shared for fair scoring.
`synthetic_persian_commands_v1.jsonl` contains **36 assistant-authored
synthetic text-only examples**, including Persian/English variations,
mixed-language phrases and unsupported/sensitive instructions. These are
development cases, NOT measured Persian ASR accuracy or a representative
speech corpus. Identifiers, not account data, are used.

## Intended workflow when a local model or recordings exist

- Gather an expressly authorized test corpus; avoid real message contents,
  phone numbers, secrets or identifiable account details.
- Separate train/development/test sets by speaker and utterance; freeze test
  sets before tuning a model.
- Run candidate speech engines entirely on the phone where possible.
- Capture final transcripts and measured first/final token latencies,
  cold/warm startup, RAM, temperature and battery per trial.
- Export only anonymized JSONL reference/predictions; evaluate with the
  same script (on isolated local files) and maintain error categories.
- Report model/version/runtime, locale, offline network state, and the number
  of dropped/failed utterances. No cherry-picked success-only averages.
- Fail closed on unsupported requests; do not execute a model's raw UI text
  as a tool command.
- For NPU, prove the actual runtime/backend used via supported telemetry;
  architecture/SoC marketing names alone do not prove acceleration.

Example after files are available:

```powershell
python scripts/s26-agent-eval/evaluate.py --mode asr `
  --reference path/to/verified-reference.jsonl `
  --predictions path/to/model-transcripts.jsonl `
  --output benchmarks/m3/model-id-results.json
```

No audio files or NPU runtime downloads are created by this scaffold.
Tests:

```powershell
python -m unittest discover -s scripts/s26-agent-eval -p "test_*.py" -v
```
