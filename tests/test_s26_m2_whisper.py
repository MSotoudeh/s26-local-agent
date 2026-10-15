"""Guardrails for pinned, offline S26 Whisper-base public model benchmark."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "android/mobile-agent/app/src/main/assets/s26_whisper_base_manifest.json"
JAVA = ROOT / "android/mobile-agent/app/src/main/java/io/projectir/mobile"
CATALOG = ROOT / "android/model-lab/catalog.json"
IGNORE = ROOT / ".gitignore"
MODEL_SHA = "911b2083efd7c0dca2ac3b358b75222660dc09fb716d64fbfc417ba6c99ff3de"
FILES = {"base-encoder.int8.onnx", "base-decoder.int8.onnx", "base-tokens.txt"}


def test_pinned_whisper_archive_and_per_file_proofs():
    m = json.loads(MANIFEST.read_text(encoding="utf-8"))
    assert m["schema_version"] == "1.0.0"
    assert m["model_id"] == "asr.whisper.base.int8"
    assert m["license"] == "apache-2.0"
    assert m["archive_sha256"] == MODEL_SHA
    assert m["archive_bytes"] == 207557382
    assert m["total_expanded_bytes"] == 160609290
    assert {f["path"] for f in m["files"]} == FILES
    assert len(m["files"]) == 3
    assert all(len(f["sha256"]) == 64 and f["bytes"] > 0 for f in m["files"])


def test_canonical_catalog_candidate_not_claimed_installed():
    doc = json.loads(CATALOG.read_text(encoding="utf-8"))
    matches = [x for x in doc["entries"] if x["id"] == "asr.whisper.base.int8"]
    assert len(matches) == 1
    assert matches[0]["status"] == "planned"
    assert matches[0]["task"] == "asr"


def test_native_profile_does_not_gain_action_permissions():
    code = (JAVA / "SherpaWhisperRunner.java").read_text(encoding="utf-8")
    for marker in ('setLanguage("fa")', 'setTask("transcribe")',
                   'setWhisper(whisper)', 'setProvider("cpu")',
                   "stream.release()", "recognizer.release()"):
        assert marker in code
    for unsafe in ("startActivity(", "ACTION_CALL", "AccessibilityService.performGlobalAction"):
        assert unsafe not in code
    panel = (JAVA / "SherpaWhisperPanel.java").read_text(encoding="utf-8")
    assert "asr.isActive()" in panel
    assert "store.heavyPreflight()" in panel


def test_real_device_result_is_read_only_and_grounded():
    result = json.loads(
        (ROOT / "benchmarks/model-lab/20261015-whisper-base-onphone.json")
        .read_text(encoding="utf-8")
    )
    assert result["model"]["archive_sha256"] == MODEL_SHA
    assert result["outcome"]["native_load"] is True
    assert result["outcome"]["android_action_effects"] == 0
    assert result["outcome"]["transcript"] == "من ارزیه ان کار رونداره"
    assert result["runtime"]["language"] == "fa"
    assert result["safety"]["assistant_pipeline_still_vosk042"] is True


def test_weights_and_runtime_not_committed():
    ignore = IGNORE.read_text(encoding="utf-8")
    assert "android/mobile-agent/app/libs/*.aar" in ignore
    prep = (ROOT / "scripts/Prepare-S26WhisperBase.py").read_text(encoding="utf-8")
    assert MODEL_SHA in prep
    assert 'member.issym()' in prep
    assert 'member.islnk()' in prep
