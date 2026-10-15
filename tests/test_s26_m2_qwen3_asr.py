"""Safety and compatibility contracts for native Qwen3-ASR S26 candidate."""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"android/mobile-agent/app/src/main/java/io/projectir/mobile"

def test_qwen3_offline_catalog_and_license():
    data=json.loads((ROOT/"android/model-lab/catalog.json").read_text(encoding="utf-8"))
    entries=[e for e in data["entries"] if e["id"]=="asr.qwen3.0_6b.int8"]
    assert len(entries)==1
    assert entries[0]["status"]=="planned"
    # Catalog remains conservative until the converted public bundle is inspected.
    assert entries[0]["license"] in ("verify-on-artifact", "apache-2.0")
    assert entries[0]["task"]=="asr"

def test_qwen3_offline_native_config_has_no_android_actions():
    s=(JAVA/"SherpaQwen3Runner.java").read_text(encoding="utf-8")
    assert "setQwen3Asr(qwen)" in s
    assert "qwen.setTokenizer(" in s
    assert "qwen.setConvFrontend(" in s
    assert "setProvider(\"cpu\")" in s
    assert "recognizer.release()" in s
    assert "stream.release()" in s
    for bad in ("startActivity(", "performGlobalAction(", "ACTION_CALL", "http://"):
        assert bad not in s

def test_qwen3_verified_model_store_requires_digest_and_guards():
    s=(JAVA/"SherpaQwen3Store.java").read_text(encoding="utf-8")
    for marker in ("SHA256", "MODEL_ID", "STAGING", "RESERVE_BYTES",
                   "MIN_AVAILABLE_MEM", "getCurrentThermalStatus",
                   "BATTERY_PROPERTY_CAPACITY", "renameTo(dest)"):
        assert marker in s
    assert "FILE_SHA_OR_LENGTH_MISMATCH" in s
    assert "MODEL_FILE_COUNT_CAP" in s

def test_secure_public_model_extractor_requires_lock():
    s=(ROOT/"scripts/Prepare-S26Qwen3Asr.py").read_text(encoding="utf-8")
    for marker in ("ARCHIVE_SHA256_MISMATCH","UNSAFE_ARCHIVE_PATH",
                   "member.issym()", "member.islnk()", "OUTPUT_COLLISION",
                   "878702423", "MODEL_ID"):
        assert marker in s
def test_signed_archive_and_model_manifest_complete():
    lock=json.loads((ROOT/"android/model-lab/qwen3-asr-0_6b-int8-lock.json")
                    .read_text(encoding="utf-8"))
    manifest=json.loads((ROOT/"android/mobile-agent/app/src/main/assets/s26_qwen3_asr_manifest.json")
                        .read_text(encoding="utf-8"))
    assert manifest["model_id"]==lock["model_id"]=="asr.qwen3.0_6b.int8"
    assert lock["sha256"]==manifest["archive_sha256"]
    assert lock["bytes"]==manifest["archive_bytes"]==878702423
    assert manifest["license"]=="apache-2.0"
    required={"encoder.int8.onnx","decoder.int8.onnx","conv_frontend.onnx"}
    files=manifest["files"]
    paths={f["path"] for f in files}
    assert required.issubset(paths)
    assert any(x.startswith("tokenizer/") for x in paths)
    assert len(paths)==len(files)==6
    assert sum(f["bytes"] for f in files)==manifest["total_expanded_bytes"]==987015347
    assert all(len(f["sha256"])==64 and f["bytes"]>0 for f in files)

def test_qwen3_real_ondevice_receipt_is_not_default_or_action_source():
    raw=json.loads(
        (ROOT/"benchmarks/model-lab/20261015-qwen3-asr-06b-onphone.json")
        .read_text(encoding="utf-8")
    )
    assert raw["model"]["id"]=="asr.qwen3.0_6b.int8"
    assert raw["model"]["sha256"]=="393f8a14e2f5fb96746aaab342997a40641001fbd5bf9592a080a8329178ee96"
    assert raw["model"]["installed_bytes"]==987015347
    assert raw["result"]["on_phone_native_inference_succeeded"] is True
    assert raw["result"]["transcript"]=="Man Ordeia encarrou Nadara."
    assert raw["result"]["android_actions_executed_from_transcript"]==0
    assert raw["context"]["qwen3_production_asr_selected"] is False
    assert raw["safety"]["vosk042_active"] is True
    assert raw["safety"]["assistant_role_preserved"] is True
    assert raw["safety"]["whisper_and_omni_weights_preserved"] is True
