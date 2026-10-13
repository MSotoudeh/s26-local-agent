#!/usr/bin/env python3
"""Local-only evaluation protocol; uses reference/prediction JSONL files.

Reference rows: {"id": "...", "intent": "...", "transcript": "...",
                 "entities": ["..."]}.
Prediction rows: {"id": "...", "intent": "...", "transcript": "..."}.
No network, telemetry upload or microphone recording.
"""
from __future__ import annotations

import argparse
import collections
import json
import re
import unicodedata
from pathlib import Path


def normalize(raw: str) -> str:
    value = unicodedata.normalize("NFKC", raw).lower()
    value = value.replace("ي", "ی").replace("ك", "ک").replace("\u200c", " ")
    value = re.sub(r"[\u064b-\u065f\u0670]", "", value)
    value = re.sub(r"[^\w\s]", " ", value, flags=re.UNICODE)
    return re.sub(r"\s+", " ", value).strip()


def load_jsonl(path: Path) -> dict[str, dict]:
    result = {}
    for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        obj = json.loads(line)
        key = obj.get("id")
        if not isinstance(key, str) or not key or key in result:
            raise ValueError(f"invalid or duplicate id at line {lineno}")
        result[key] = obj
    if not result:
        raise ValueError("empty evaluation set")
    return result


def levenshtein(a: list, b: list) -> int:
    if len(b) > len(a):
        a, b = b, a
    previous = list(range(len(b) + 1))
    for i, left in enumerate(a, 1):
        current = [i] + [0] * len(b)
        for j, right in enumerate(b, 1):
            current[j] = min(
                current[j - 1] + 1,
                previous[j] + 1,
                previous[j - 1] + (left != right),
            )
        previous = current
    return previous[-1]


def evaluate(reference: dict, predictions: dict, mode: str) -> dict:
    missing = sorted(set(reference) - set(predictions))
    extra = sorted(set(predictions) - set(reference))
    if missing or extra:
        raise ValueError(f"id-set mismatch: missing={len(missing)} extra={len(extra)}")
    if mode == "intent":
        rows = [(ref["intent"], predictions[key]["intent"])
                for key, ref in reference.items()]
        correct = sum(a == b for a, b in rows)
        labels = sorted(set(a for a, _ in rows) | set(b for _, b in rows))
        per_label = {}
        for label in labels:
            tp = sum(a == label and b == label for a, b in rows)
            fp = sum(a != label and b == label for a, b in rows)
            fn = sum(a == label and b != label for a, b in rows)
            precision = tp / (tp + fp) if tp + fp else 0.0
            recall = tp / (tp + fn) if tp + fn else 0.0
            f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
            per_label[label] = {"support": sum(a == label for a, _ in rows),
                                "precision": precision, "recall": recall, "f1": f1}
        return {
            "mode": mode, "samples": len(rows),
            "accuracy": correct / len(rows),
            "macro_f1": sum(v["f1"] for v in per_label.values()) / len(per_label),
            "confusion": [{"expected": a, "predicted": b, "count": count}
                          for (a, b), count in sorted(collections.Counter(rows).items())],
            "per_label": per_label,
        }
    total_chars = total_words = char_errors = word_errors = 0
    preserved_entities = total_entities = 0
    for key, ref in reference.items():
        truth = normalize(ref["transcript"])
        hypothesis = normalize(predictions[key]["transcript"])
        ta, tb = list(truth), list(hypothesis)
        wa, wb = truth.split(), hypothesis.split()
        total_chars += len(ta)
        total_words += len(wa)
        char_errors += levenshtein(ta, tb)
        word_errors += levenshtein(wa, wb)
        for entity in ref.get("entities", []):
            entity_words = normalize(entity).split()
            if not entity_words:
                raise ValueError("entity reference cannot be empty")
            total_entities += 1
            for idx in range(len(wb) - len(entity_words) + 1):
                if wb[idx:idx + len(entity_words)] == entity_words:
                    preserved_entities += 1
                    break
    if not total_chars or not total_words:
        raise ValueError("reference transcripts cannot be empty")
    return {
        "mode": "asr", "samples": len(reference),
        "cer": char_errors / total_chars,
        "wer": word_errors / total_words,
        "entity_preservation": (preserved_entities / total_entities
                                if total_entities else None),
        "total_entities": total_entities,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=["intent", "asr"], required=True)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--predictions", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = evaluate(load_jsonl(args.reference),
                      load_jsonl(args.predictions), args.mode)
    payload = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(payload, encoding="utf-8")
    print(payload, end="")


if __name__ == "__main__":
    main()
