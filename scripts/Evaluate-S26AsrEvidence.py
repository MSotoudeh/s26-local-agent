#!/usr/bin/env python3
"""Offline, conservative evaluation of real S26 Model Lab ASR receipts.

No phone access, inference, action execution, installation, or auto-promotion.
Unique WAV hashes prevent repeated runs from inflating quality evidence.
"""
from __future__ import annotations

import argparse
from collections import defaultdict
import hashlib
import json
import math
from pathlib import Path
import re
import sys
import unicodedata

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_RECEIPT = ROOT / "benchmarks/model-lab/20261015-asr-three-way-onphone.json"
HEX_SHA = re.compile(r"^[a-fA-F0-9]{64}$")
TRANSLATE = str.maketrans({
    "\u064a": "\u06cc",  # Arabic yeh -> Persian yeh
    "\u0649": "\u06cc",
    "\u0643": "\u06a9",  # Arabic kaf -> Persian kaf
    "\u06c0": "\u0647",
})


def normalize(text: str) -> str:
    """Minimal reproducible Persian normalization, without transliteration."""
    if not isinstance(text, str):
        raise ValueError("TRANSCRIPT_NOT_TEXT")
    text = unicodedata.normalize("NFKC", text).translate(TRANSLATE).lower()
    result = []
    for ch in text:
        category = unicodedata.category(ch)
        if category.startswith("M") or ch == "\u0640":
            continue  # tashkeel/tatweel
        if ch.isspace() or ch in ("\u200c", "\u200d"):
            result.append(" ")
        elif category.startswith(("P", "S")):
            result.append(" ")
        else:
            result.append(ch)
    return " ".join("".join(result).split())


def edit_distance(a: list[str] | str, b: list[str] | str) -> int:
    row = list(range(len(b) + 1))
    for index, left in enumerate(a, 1):
        nxt = [index]
        for j, right in enumerate(b, 1):
            nxt.append(min(nxt[-1] + 1, row[j] + 1, row[j - 1] + (left != right)))
        row = nxt
    return row[-1]


def _natural_positive(value: object, name: str) -> int:
    if type(value) is not int or value <= 0:
        raise ValueError("INVALID_" + name)
    return value


def _percentile_nearest(values: list[int], proportion: float) -> int:
    ordered = sorted(values)
    return ordered[math.ceil(proportion * len(ordered)) - 1]


def evaluate(paths: list[Path], *, min_quality_fixtures: int = 100,
             min_latency_trials: int = 20) -> dict:
    if not paths or min_quality_fixtures < 2 or min_latency_trials < 2:
        raise ValueError("INVALID_EVIDENCE_REQUIREMENTS")
    reference_by_hash: dict[str, str] = {}
    per_model: dict[str, list[dict]] = defaultdict(list)
    source_hashes = []
    seen_files = set()

    for path in paths:
        raw = Path(path).read_bytes()
        file_hash = hashlib.sha256(raw).hexdigest()
        if file_hash in seen_files:
            raise ValueError("DUPLICATE_RECEIPT_FILE")
        seen_files.add(file_hash)
        source_hashes.append(file_hash)
        receipt = json.loads(raw.decode("utf-8", errors="strict"))
        if receipt.get("schema_version") != "1.0.0":
            raise ValueError("UNSUPPORTED_RECEIPT_SCHEMA")
        if receipt.get("session_type") not in ("all_three", "one_selected"):
            raise ValueError("UNSUPPORTED_SESSION_TYPE")
        if receipt.get("backend") != "cpu":
            raise ValueError("BACKEND_NOT_VERIFIED_CPU")
        before, after = (receipt.get("active_assistant_asr_at_start"),
                         receipt.get("active_assistant_asr_at_end"))
        if not before or before != after or receipt.get("intent_effects") != 0:
            raise ValueError("PRODUCTION_ASSISTANT_OR_EFFECTS_CHANGED")
        fixture = receipt.get("reference_wav_sha256")
        if not isinstance(fixture, str) or not HEX_SHA.fullmatch(fixture):
            raise ValueError("INVALID_REFERENCE_WAV_SHA")
        fixture = fixture.lower()
        reference = normalize(receipt.get("reference_transcript"))
        if not reference:
            raise ValueError("EMPTY_REFERENCE_TRANSCRIPT")
        if fixture in reference_by_hash and reference_by_hash[fixture] != reference:
            raise ValueError("CONFLICTING_GROUND_TRUTH_FOR_SAME_WAV")
        reference_by_hash[fixture] = reference
        trials = receipt.get("trials")
        if not isinstance(trials, list) or not trials:
            raise ValueError("NO_MODEL_TRIALS")
        seen_in_session = set()
        for trial in trials:
            model = trial.get("model_id")
            if not isinstance(model, str) or not model.startswith("asr."):
                raise ValueError("INVALID_MODEL_ID")
            if model in seen_in_session:
                raise ValueError("DUPLICATE_MODEL_TRIAL_IN_SESSION")
            seen_in_session.add(model)
            if trial.get("status") != "PASSED_NATIVE_INFERENCE":
                raise ValueError("TRIAL_NOT_REAL_NATIVE_SUCCESS")
            _natural_positive(trial.get("threads"), "THREAD_COUNT")
            load = _natural_positive(trial.get("load_ms"), "LOAD_MS")
            decode = _natural_positive(trial.get("decode_ms"), "DECODE_MS")
            total = _natural_positive(trial.get("total_native_ms"), "NATIVE_TOTAL_MS")
            if total < load + decode:
                raise ValueError("IMPOSSIBLE_NATIVE_TIMING")
            transcript = normalize(trial.get("transcript"))
            # Empty transcript is valid *as an ASR error* and scores accordingly.
            per_model[model].append({
                "fixture": fixture, "ref": reference, "hyp": transcript,
                "load_ms": load, "decode_ms": decode, "total_ms": total,
                "script_mismatch": (
                    any("\u0600" <= x <= "\u06ff" for x in reference)
                    and not any("\u0600" <= x <= "\u06ff" for x in transcript)
                ),
            })

    if not per_model:
        raise ValueError("NO_MODEL_EVIDENCE")
    models = {}
    fixture_sets = []
    for model in sorted(per_model):
        rows = per_model[model]
        unique = {}
        for row in rows:
            unique.setdefault(row["fixture"], row)
        # Each distinct utterance contributes exactly once to WER/CER;
        # repeated trials contribute only to the unstratified latency sample.
        wer_errors = sum(edit_distance(v["ref"].split(), v["hyp"].split())
                         for v in unique.values())
        wer_units = sum(len(v["ref"].split()) for v in unique.values())
        cer_errors = sum(edit_distance(v["ref"].replace(" ", ""),
                                       v["hyp"].replace(" ", ""))
                         for v in unique.values())
        cer_units = sum(len(v["ref"].replace(" ", "")) for v in unique.values())
        fixture_sets.append(set(unique))
        total_ms = [x["total_ms"] for x in rows]
        sufficient_latency = len(rows) >= min_latency_trials
        models[model] = {
            "native_trials": len(rows),
            "unique_audio_fixtures": len(unique),
            "wer": round(wer_errors / wer_units, 6),
            "cer": round(cer_errors / cer_units, 6),
            "script_mismatch_unique_fixtures": sum(
                item["script_mismatch"] for item in unique.values()),
            "latency_median_native_ms": (
                _percentile_nearest(total_ms, .5) if sufficient_latency else None),
            "latency_p95_native_ms": (
                _percentile_nearest(total_ms, .95) if sufficient_latency else None),
            "latency_distribution": (
                "UNSTRATIFIED_COLD_WARM_UNKNOWN" if sufficient_latency
                else "INSUFFICIENT_TRIALS"),
        }
    matching_coverage = all(s == fixture_sets[0] for s in fixture_sets)
    enough_coverage = (
        matching_coverage and len(fixture_sets[0]) >= min_quality_fixtures
        and len(models) >= 2)
    return {
        "schema_version": "1.0.0",
        "evidence_kind": "real_android_receipts_offline_host_scoring",
        "receipt_sha256": sorted(source_hashes),
        "model_count": len(models),
        "total_unique_wav_hashes": len(reference_by_hash),
        "balanced_identical_fixture_coverage": matching_coverage,
        "required_unique_fixtures_per_model": min_quality_fixtures,
        "required_trials_for_latency_distribution": min_latency_trials,
        "sufficient_corpus_coverage_for_quality_comparison": enough_coverage,
        "production_model_auto_promotion": "PROHIBITED_REQUIRES_MANUAL_REVIEW",
        "models": models,
        "limitations": [
            "Repeated WAV hashes never count as additional accuracy samples.",
            "Single-utterance WER/CER are diagnostic only, not population estimates.",
            "Even 100 unique WAVs do not prove diverse speakers or conditions.",
            "Native timings exclude Android UI, audio capture and Assistant latency.",
            "Cold/warm stratification and peak PSS are absent from this receipt schema.",
        ],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("receipts", nargs="*", type=Path, default=[DEFAULT_RECEIPT])
    parser.add_argument("--out", type=Path, help="write UTF-8 JSON report")
    args = parser.parse_args()
    result = evaluate(args.receipts)
    output = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(output, encoding="utf-8")
    else:
        if hasattr(sys.stdout, "reconfigure"):
            sys.stdout.reconfigure(encoding="utf-8")
        print(output, end="")


if __name__ == "__main__":
    main()
