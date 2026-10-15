"""Real-receipt ASR quality scoring and conservative acceptance safety."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location(
    "asr_evidence", ROOT / "scripts/Evaluate-S26AsrEvidence.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
REAL = ROOT / "benchmarks/model-lab/20261015-asr-three-way-onphone.json"


def _write(tmp_path, obj, name="receipt.json"):
    file = tmp_path / name
    file.write_text(json.dumps(obj, ensure_ascii=False), encoding="utf-8")
    return file


def _real():
    return json.loads(REAL.read_text(encoding="utf-8"))


def test_real_phone_receipt_remains_not_eligible():
    report = MODULE.evaluate([REAL])
    assert report["total_unique_wav_hashes"] == 1
    assert report["model_count"] == 3
    assert not report["sufficient_corpus_coverage_for_quality_comparison"]
    assert report["production_model_auto_promotion"].startswith("PROHIBITED")
    assert all(row["latency_p95_native_ms"] is None
               for row in report["models"].values())
    assert report["models"]["asr.qwen3.0_6b.int8"]["script_mismatch_unique_fixtures"] == 1
    assert report["models"]["asr.whisper.base.int8"]["wer"] >= 0
    assert report["models"]["asr.qwen3.0_6b.int8"]["wer"] > 0


def test_persian_yeh_kaf_diacritics_and_zwnj_normalized():
    assert MODULE.normalize("\u064a\u0643 \u0645\u064e\u0646 \u200c\u062f\u0631\u0648\u062f!") == (
        "\u06cc\u06a9 \u0645\u0646 \u062f\u0631\u0648\u062f")
    assert MODULE.normalize("من‌نمیدانم") == "من نمیدانم"
    assert MODULE.normalize("Man Ordeia encarrou") == "man ordeia encarrou"


@pytest.mark.parametrize("key,value,err", [
    ("intent_effects", 1, "PRODUCTION_ASSISTANT_OR_EFFECTS_CHANGED"),
    ("active_assistant_asr_at_end", "asr.another", "PRODUCTION_ASSISTANT_OR_EFFECTS_CHANGED"),
    ("backend", "gpu", "BACKEND_NOT_VERIFIED_CPU"),
    ("reference_wav_sha256", "invalid", "INVALID_REFERENCE_WAV_SHA"),
    ("reference_transcript", "", "EMPTY_REFERENCE_TRANSCRIPT"),
    ("session_type", "simulation", "UNSUPPORTED_SESSION_TYPE"),
])
def test_invalid_receipt_rejected(tmp_path, key, value, err):
    receipt = _real()
    receipt[key] = value
    with pytest.raises(ValueError, match=err):
        MODULE.evaluate([_write(tmp_path, receipt)])


def test_missing_or_duplicate_native_measurements_rejected(tmp_path):
    r = _real()
    r["trials"][0]["status"] = "SIMULATED"
    with pytest.raises(ValueError, match="TRIAL_NOT_REAL_NATIVE_SUCCESS"):
        MODULE.evaluate([_write(tmp_path, r)])
    r = _real()
    r["trials"].append(copy.deepcopy(r["trials"][0]))
    with pytest.raises(ValueError, match="DUPLICATE_MODEL"):
        MODULE.evaluate([_write(tmp_path, r)])
    r = _real()
    r["trials"][0]["total_native_ms"] = 4
    with pytest.raises(ValueError, match="IMPOSSIBLE_NATIVE_TIMING"):
        MODULE.evaluate([_write(tmp_path, r)])


def test_conflicting_ground_truth_and_duplicate_receipt_rejected(tmp_path):
    r = _real()
    a = _write(tmp_path, r, "a.json")
    with pytest.raises(ValueError, match="DUPLICATE_RECEIPT_FILE"):
        MODULE.evaluate([a, a])
    r["reference_transcript"] = "متن دیگری"
    b = _write(tmp_path, r, "b.json")
    with pytest.raises(ValueError, match="CONFLICTING_GROUND_TRUTH_FOR_SAME_WAV"):
        MODULE.evaluate([a, b])


def test_repeated_audio_does_not_inflate_quality_or_qualify_ranking(tmp_path):
    files = []
    for i in range(20):
        r = _real()
        r["timestamp_unix_ms"] += i + 1
        # Same WAV fixture across different benchmark sessions.
        files.append(_write(tmp_path, r, f"repeat-{i}.json"))
    report = MODULE.evaluate(files)
    assert report["total_unique_wav_hashes"] == 1
    assert not report["sufficient_corpus_coverage_for_quality_comparison"]
    for metrics in report["models"].values():
        assert metrics["unique_audio_fixtures"] == 1
        assert metrics["native_trials"] == 20
        assert metrics["latency_p95_native_ms"] is not None
        assert metrics["latency_distribution"] == "UNSTRATIFIED_COLD_WARM_UNKNOWN"


def test_genuine_distinct_fixture_coverage_needed_for_quality_gate(tmp_path):
    files = []
    for i in range(100):
        r = _real()
        r["reference_wav_sha256"] = hashlib.sha256(f"public-fixture-{i}".encode()).hexdigest()
        r["timestamp_unix_ms"] += i + 1
        files.append(_write(tmp_path, r, f"distinct-{i}.json"))
    result = MODULE.evaluate(files)
    assert result["total_unique_wav_hashes"] == 100
    assert result["balanced_identical_fixture_coverage"]
    assert result["sufficient_corpus_coverage_for_quality_comparison"]
    assert result["production_model_auto_promotion"] == "PROHIBITED_REQUIRES_MANUAL_REVIEW"
    assert result["models"]["asr.qwen3.0_6b.int8"]["script_mismatch_unique_fixtures"] == 100


def test_unbalanced_engine_coverage_fails_gate(tmp_path):
    files = []
    for i in range(100):
        r = _real()
        r["reference_wav_sha256"] = hashlib.sha256(f"fixture-{i}".encode()).hexdigest()
        r["timestamp_unix_ms"] += i + 1
        if i == 99:
            r["trials"].pop()
        files.append(_write(tmp_path, r, f"distinct-{i}.json"))
    result = MODULE.evaluate(files)
    assert not result["balanced_identical_fixture_coverage"]
    assert not result["sufficient_corpus_coverage_for_quality_comparison"]


def test_edit_distance_correct_and_empty_hypothesis_allowed(tmp_path):
    assert MODULE.edit_distance("cat", "bat") == 1
    assert MODULE.edit_distance(["one", "two"], ["one"]) == 1
    r = _real()
    r["trials"][0]["transcript"] = ""
    result = MODULE.evaluate([_write(tmp_path, r)])
    assert result["models"]["asr.omni_ctc_300m.int8"]["wer"] == 1.0
    assert result["models"]["asr.omni_ctc_300m.int8"]["cer"] == 1.0
