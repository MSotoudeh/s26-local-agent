#!/usr/bin/env python3
"""Validate S26 Model Lab specifications ONLY. No downloads/device actions.

Usage: python scripts/validate_s26_model_lab.py [--self-test]
"""
from __future__ import annotations

import copy
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
CATALOG = ROOT / "android" / "model-lab" / "catalog.json"
SCENARIOS = ROOT / "android" / "model-lab" / "scenarios.json"

STATES = {
    "installed_verified", "planned", "gated_approval", "not_auto_install",
    "license_blocked_for_commercial", "unsupported", "quarantined"
}
TASKS = {"asr", "vad", "tts", "llm", "vlm", "vision", "search"}
TIERS = {"baseline", "speed", "balanced", "quality", "heavy", "stress",
         "extreme", "beyond_phone", "research_only"}
ACTUAL_BASELINES = {"asr.vosk.fa-small-042", "llm.qwen2_5.0_5b.int8",
                    "vision.accessibility_tree"}


def require(predicate: bool, detail: str) -> None:
    if not predicate:
        raise ValueError(detail)


def valid_catalog(d: dict) -> dict:
    require(d.get("schema_version") == "1.0.0", "unsupported catalog schema")
    dev = d.get("verified_device") or {}
    require(dev.get("model") == "SM-S948B" and dev.get("soc") == "SM8850",
            "wrong target device")
    require(dev.get("ram_memtotal_kib", 0) > 0 and
            dev.get("memavailable_kib_snapshot", 0) > 0, "missing RAM evidence")
    policy = d.get("installation_policy") or {}
    require(policy.get("current_stage") == "plan_only", "catalog inaccurately claims execution")
    require(policy.get("max_one_heavy_runtime") is True, "heavy runtime isolation absent")
    require(policy.get("allowlist_only") is True, "unrestricted model execution")
    require(policy.get("never_export_screenshots_or_audio") is True, "privacy violation")
    require(policy.get("reserve_free_storage_gb", 0) >= 30, "storage safety reserve missing")
    require(policy.get("planned_model_storage_cap_gb", 999) <= 110,
            "model storage cap exceeds plan")
    entries = d.get("entries") or []
    require(len(entries) >= 30, "expected full candidate set")
    ids = set()
    known = set()
    tasks = set()
    for e in entries:
        ident = e.get("id")
        require(isinstance(ident, str) and "." in ident and ident not in ids,
                "bad or duplicate model id")
        ids.add(ident)
        cats = set(e.get("task", "").split(","))
        require(cats and cats <= TASKS, "unknown task: " + ident)
        tasks.update(cats)
        require(e.get("tier") in TIERS and e.get("status") in STATES,
                "unknown tier/status: " + ident)
        require(str(e.get("source", "")).startswith("https://"),
                "untrusted or missing source: " + ident)
        require(isinstance(e.get("runtimes"), list) and
                len(e["runtimes"]) >= 1, "missing runtime candidates: " + ident)
        require(isinstance(e.get("notes"), str) and len(e["notes"]) >= 12,
                "missing model notes: " + ident)
        require(e.get("license"), "missing license hint: " + ident)
        if e["status"] == "installed_verified":
            known.add(ident)
        if e.get("license") == "cc-by-nc-4.0":
            require(e["status"] == "license_blocked_for_commercial",
                    "noncommercial model not isolated: " + ident)
        if e.get("license") == "gemma-terms":
            require(e["status"] == "gated_approval", "gated model incorrectly unblocked")
        if e["tier"] == "beyond_phone":
            require(e["status"] == "not_auto_install", "unsafe extreme auto installation")
    require(known == ACTUAL_BASELINES, "installed status diverges from proven baseline")
    require({"asr", "vad", "tts", "llm", "vlm", "vision"} <= tasks,
            "a model comparison category is missing")
    return {"catalog_models": len(entries), "already_verified": len(known),
            "planned_or_gated": len(entries) - len(known), "task_categories": sorted(tasks)}


def valid_scenarios(d: dict) -> dict:
    require(d.get("schema_version") == "1.0.0", "unknown scenarios schema")
    safe = d.get("safety") or {}
    require(safe.get("live_effects_are_disabled_by_default") is True and
            safe.get("no_screen_export_by_default") is True, "unsafe experiment defaults")
    minimums = d.get("targets") or {}
    require(minimums.get("asr_minimum_utterances", 0) >= 100, "insufficient ASR targets")
    require(minimums.get("tts_minimum_texts", 0) >= 40, "insufficient TTS targets")
    require(minimums.get("llm_minimum_prompts", 0) >= 60, "insufficient LLM targets")
    require(minimums.get("vlm_minimum_synthetic_screens", 0) >= 30,
            "insufficient vision targets")
    require(minimums.get("integrated_minimum_turns", 0) >= 30,
            "insufficient end-to-end targets")
    arr = d.get("scenarios") or []
    require(len(arr) >= 25, "not enough seed fixtures")
    names = set()
    for x in arr:
        ident = x.get("id")
        require(isinstance(ident, str) and ident not in names,
                "duplicate/invalid scenario ID")
        names.add(ident)
        require(x.get("task") in TASKS | {"e2e"}, "invalid scenario task: " + ident)
        require(x.get("fixture_status") == "specification_only", "fabricated execution result")
        require(x.get("synthetic") is True and
                x.get("reference_audio_sha256") is None and
                x.get("reference_image_sha256") is None,
                "private asset or unverified hash must not be embedded")
        if x.get("allow_live_device_effect") is True:
            require(ident in {"e2e.voice-navigate", "asr.fa.open-settings",
                              "asr.fa.open-connections"},
                    "unapproved real device-effect fixture")
        if x.get("mode") in {"screen", "screenshot"}:
            require(x.get("requires_explicit_screen_consent") is True,
                    "screen collection without consent")
    return {"seed_scenarios": len(arr), "effects_allowlisted": sum(
        x.get("allow_live_device_effect") is True for x in arr)}


def negative_tests(c: dict, s: dict) -> int:
    tests = 0
    for change in ("clone_id", "mark_gemma_installed", "mark_nc_installed",
                   "enable_32b_auto", "reduce_reserve"):
        copycat = copy.deepcopy(c)
        if change == "clone_id":
            copycat["entries"][1]["id"] = copycat["entries"][0]["id"]
        elif change == "mark_gemma_installed":
            next(x for x in copycat["entries"] if x["license"] == "gemma-terms"
                 )["status"] = "installed_verified"
        elif change == "mark_nc_installed":
            next(x for x in copycat["entries"] if x["license"] == "cc-by-nc-4.0"
                 )["status"] = "planned"
        elif change == "enable_32b_auto":
            next(x for x in copycat["entries"] if x["tier"] == "beyond_phone"
                 )["status"] = "planned"
        elif change == "reduce_reserve":
            copycat["installation_policy"]["reserve_free_storage_gb"] = 0
        try:
            valid_catalog(copycat)
        except ValueError:
            tests += 1
        else:
            raise AssertionError("negative safety test unexpectedly succeeded: " + change)
    tampered = copy.deepcopy(s)
    tampered["scenarios"][0]["allow_live_device_effect"] = False
    tampered["scenarios"][3]["allow_live_device_effect"] = True
    try:
        valid_scenarios(tampered)
    except ValueError:
        tests += 1
    else:
        raise AssertionError("unapproved live device-effect scenario accepted")
    return tests


def valid_providers(d: dict) -> dict:
    require(d.get("schema_version") == "1.0.0", "provider schema changed")
    policy = d.get("selection_policy") or {}
    require(policy.get("offline_default") is True
            and policy.get("network_allowed_only_by_explicit_user_intent") is True
            and policy.get("no_secrets_in_git") is True,
            "network/privacy policy not fail-closed")
    counts = {}
    ids = set()
    for group in ("search", "ocr", "compute_backends"):
        items = d.get(group) or []
        require(len(items) >= 3, "provider category missing: " + group)
        counts[group] = len(items)
        for item in items:
            name = item.get("id")
            require(isinstance(name, str) and name not in ids, "invalid/duplicate provider")
            ids.add(name)
            require(str(item.get("source", "")).startswith("https://"),
                    "provider without trusted HTTPS source")
            require(item.get("status") in {"planned", "installed_verified",
                    "candidate_only"}, "provider claims unverified operational status")
            if group == "search":
                require(item.get("status") == "planned",
                        "network backend must not be listed as installed")
                require(item.get("cost_gate") and item.get("privacy"),
                        "online search missing price/privacy consent gate")
    return {"provider_candidates": sum(counts.values()),
            "provider_groups": counts}


def main() -> int:
    c = json.loads(CATALOG.read_text(encoding="utf-8"))
    s = json.loads(SCENARIOS.read_text(encoding="utf-8"))
    providers = json.loads((ROOT / "android" / "model-lab" / "providers.json").read_text(encoding="utf-8"))
    model_summary = valid_catalog(c)
    scenario_summary = valid_scenarios(s)
    provider_summary = valid_providers(providers)
    if "--self-test" in sys.argv[1:]:
        scenario_summary["negative_safety_tests_passed"] = negative_tests(c, s)
    print(json.dumps({**model_summary, **scenario_summary, **provider_summary,
                      "real_model_installations_performed": 0,
                      "external_actions_performed": 0},
                     ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
