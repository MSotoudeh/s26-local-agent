"""M2 canonical Android metadata tests. No phone/model weights required."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "android/mobile-agent/app/src/main/assets/s26_omni300m_manifest.json"
RESULT = ROOT / "benchmarks/model-lab/20261015-omni300m-onphone.json"
GRADLE = ROOT / "android/mobile-agent/app/build.gradle"
BOOTSTRAP = ROOT / "scripts/Prepare-S26SherpaRuntime.ps1"
GITIGNORE = ROOT / ".gitignore"

EXPECTED_MODEL_FILES = {"LICENSE", "model.int8.onnx", "tokens.txt"}
ARCHIVE_SHA = "cdcd0559c7c73efed54209a926e321afc914d046c5fdbf3665f00dc78180e5ed"
RUNTIME_SHA = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"


def load_manifest():
    return json.loads(MODEL.read_text(encoding="utf-8"))


def valid_manifest(x):
    assert x["schema_version"] == "1.0.0"
    assert x["model_id"] == "asr.omni_ctc_300m.int8"
    assert x["directory"] == "sherpa-omni300m-ctc-int8"
    assert x["license"] == "apache-2.0"
    assert x["archive_sha256"] == ARCHIVE_SHA
    assert x["archive_bytes"] == 292571207
    files = x["files"]
    paths = [v["path"] for v in files]
    assert len(paths) == len(set(paths)) == len(EXPECTED_MODEL_FILES)
    assert set(paths) == EXPECTED_MODEL_FILES
    assert sum(v["bytes"] for v in files) == x["total_expanded_bytes"] == 365439124
    assert all(len(v["sha256"]) == 64
               and int(v["sha256"], 16) >= 0
               and v["bytes"] > 0 for v in files)


def test_signed_model_manifest():
    valid_manifest(load_manifest())


def test_native_library_pinned_and_excluded_from_git():
    script = BOOTSTRAP.read_text(encoding="utf-8")
    assert RUNTIME_SHA in script
    assert "sherpa-onnx-1.13.8.aar" in GRADLE.read_text(encoding="utf-8")
    assert "android/mobile-agent/app/libs/*.aar" in GITIGNORE.read_text(encoding="utf-8")


def test_real_result_explicitly_not_mixed_with_vosk_latency():
    record = json.loads(RESULT.read_text(encoding="utf-8"))
    assert record["model"]["id"] == "asr.omni_ctc_300m.int8"
    assert record["model"]["archive_sha256"] == ARCHIVE_SHA
    assert record["runtime"]["runtime_aar_sha256"] == RUNTIME_SHA
    assert record["outcome"]["model_loaded"] is True
    assert record["outcome"]["router_actions_executed"] == 0
    assert record["outcome"]["load_ms"] > 0
    assert record["outcome"]["decode_ms"] > 0
    assert "one" in record["interpretation"].lower() or "Single" in record["interpretation"]
    assert record["operational_checks"]["android_assistant_role_preserved"] is True


def test_fail_closed_bad_manifest_paths():
    for bad in ("../model.int8.onnx", "/tmp/model.int8.onnx", "x/model.int8.onnx"):
        broken = load_manifest()
        broken["files"][1]["path"] = bad
        try:
            valid_manifest(broken)
        except AssertionError:
            pass
        else:
            raise AssertionError("Unsafe model file metadata accepted")


def test_fail_closed_digest_mutation():
    broken = load_manifest()
    broken["files"][0]["sha256"] = "123"
    try:
        valid_manifest(broken)
    except AssertionError:
        pass
    else:
        raise AssertionError("Truncated model hash accepted")


def test_fail_closed_duplicate_manifest_entry():
    broken = load_manifest()
    broken["files"][0]["path"] = broken["files"][1]["path"]
    try:
        valid_manifest(broken)
    except AssertionError:
        pass
    else:
        raise AssertionError("Duplicate model member accepted")
