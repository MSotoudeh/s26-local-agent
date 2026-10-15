# S26 ASR evidence gate — continuation checkpoint (2026-10-15)

## Verified starting state
- Canonical repository: MSotoudeh/s26-local-agent.
- GitHub main at the start of this follow-up: 37ee64342d942b364c4a283bebb5c46f17f1e3ca; local main synced by a clean fast-forward.
- PR #7 already merged the actual Android three-engine compare-all APK. It was installed on the physical SM-S948B with data preserved; Assistant role, microphone, Accessibility and service health were verified in the previous live run.
- Live ADB read-back: connected SM-S948B; app process present; Android Assistant role still io.projectir.mobile.
- Original on-device three-way JSON: /sdcard/Android/data/io.projectir.mobile/files/benchmarks/m2-asr-compare-1792082481218-29678.json.
- Phone binary pulled and verified byte-for-byte against benchmarks/model-lab/20261015-asr-three-way-onphone.json. Both SHA-256: 4a874fcdc4d2562ceb65e4fbc5fe6d0eda67590df698254ca9a059748bcd2560.
- Important distinction: Console rendering of Persian text can be lossy depending on PowerShell/remote transport encoding, but the original JSON contains actual UTF-8 Persian bytes. Inspection of the source file with Python confirmed non-ASCII UTF-8; do not rewrite or "repair" this valid receipt based on console display alone.

## Delivered in this branch
- scripts/Evaluate-S26AsrEvidence.py: strictly offline scoring of schema-validated, previously collected Android ASR receipt JSONs (no model execution, phone state changes, recordings or network access).
- tests/test_s26_asr_evidence_quality_gate.py: test coverage of real three-way receipt, Unicode normalization, integrity fields, safety invariants, duplicate WAVs, contradictory references, model coverage, and synthetic 100-distinct-fixture scenarios.
- benchmarks/model-lab/20261015-asr-evidence-summary.json: machine-readable derived report from the real *single public WAV* session.

### Evidence rules
1. Reject unsupported receipt schema, non-CPU backend, changed production Assistant model, nonzero Android intent side effects, fabricated trial states, malformed SHA-256, missing ground truth or impossible timings.
2. Normalize Persian/Arabic yeh and kaf, Unicode presentation and diacritics, but never transliterate Latin-script predictions into Persian or heuristically merge word boundaries.
3. Calculate *corpus-level* WER and CER with edit distance; use only one result per unique SHA-256 WAV hash per model in the error totals, so repeating a short WAV 100 times cannot forge statistical coverage.
4. Compute unstratified native p50/p95 only from at least 20 real trials per engine. When a receipt has no cold/warm tags, never pretend it provides stratified latency or end-to-end microphone metrics.
5. Require at least 100 unique audio fixtures per compared engine and identical per-engine fixture coverage before even marking the evidence as *sufficiently covered for a quality comparison*. This threshold is a minimum coverage heuristic, NOT proof of speaker diversity or statistical confidence.
6. **Production auto-promotion always remains prohibited** and still requires independent quality, safety and physical voice-assistant validation even after coverage passes.

## Real fixed-WAV result (diagnostic only)
| Engine | Native total | Single-fixture WER | Single-fixture CER | Script anomaly |
|---|---:|---:|---:|---|
| Omnilingual CTC 300M Int8 | 1118 ms | 0.8000 | 0.333333 | No |
| Whisper Base multilingual Int8 | 774 ms | 0.8000 | 0.333333 | No |
| Qwen3-ASR 0.6B Int8 | 3166 ms | 1.0000 | 1.277778 | Yes (Latin output) |

WER/CER are calculated with conservative documented normalization and may exceed 1.0 because edit distance counts insertions. No quality winner can be established from this one WAV. Qwen's Latin-script result is an obvious problem for Persian routing. The other two also fail to match many reference words. The production Side-key Assistant remains on Vosk 0.42.

Current report: unique WAVs=1; compared ASRs=3; latency trials per engine=1. Hence sufficient_corpus_coverage_for_quality_comparison=false, native p50/p95=null, production_model_auto_promotion=PROHIBITED_REQUIRES_MANUAL_REVIEW.

## Verification and next steps
- Tests: 49 pytest passed, 3 subtests passed (entire current host test collection).
- No new APK compiled or installed, no Shizuku changes, no altered Android microphone/default Assistant settings, no removal or download of model files. No cost.
- Next evidence acquisition (not claimed complete): 100+ distinct, consented or public, hash-pinned Persian utterances across diverse speakers, conditions, negation and mixed language; full same-WAV engine coverage; explicit cold/warm and order-balanced runs (20+ trials/model); process PSS and thermal sampling; consented real Side-key user validation.
- Do not add user voice recordings to Git or remotely collect them without explicit consent. Public test fixtures are preferable for a reproducible baseline.
- The script is intentionally **evaluation only**; it never changes runtime selection or approves a model swap.

To reproduce offline:

    python scripts/Evaluate-S26AsrEvidence.py --out benchmarks/model-lab/20261015-asr-evidence-summary.json

To score more receipts, pass their exact local JSON paths as positional arguments.
