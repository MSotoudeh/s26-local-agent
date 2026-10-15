"""M2 side-by-side sherpa ASR comparison safety contracts."""
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/"android/mobile-agent/app/src/main/java/io/projectir/mobile"
PANEL=(JAVA/"SherpaAsrComparisonPanel.java").read_text(encoding="utf-8")
IDS=(ROOT/"android/mobile-agent/app/src/main/res/values/model_lab_ids.xml").read_text(encoding="utf-8")

def test_three_true_native_runtimes_not_mocked():
    for name in ("SherpaOmniRunner.decodeFixedWav",
                 "SherpaWhisperRunner.decodeFixedWav",
                 "SherpaQwen3Runner.decodeFixedWav"):
        assert name in PANEL
    assert 'session.put("trials", trials)' in PANEL

def test_fixed_fixture_and_no_hidden_audio_capture():
    assert "4a656d803a1e07eb3707365433dccdac7cf2f055d0e0aeae2084d4f6dd255bc3" in PANEL
    assert "REFERENCE_WAV_MISSING_OR_HASH_CHANGED" in PANEL
    assert "asr.isActive()" in PANEL
    for prohibited in ("MediaRecorder(", "AudioRecord(", "startActivity(",
                       "sendBroadcast(", "ACTION_CALL", "http://", "https://"):
        assert prohibited not in PANEL

def test_compare_receipt_is_atomic_app_scoped_and_readable():
    for marker in ('getExternalFilesDir("benchmarks")',
                   'new FileOutputStream(staging, false)',
                   "stream.getFD().sync()", "staging.renameTo(target)",
                   'session.put("runtime"',
                   'session.put("backend"',
                   'session.put("active_assistant_asr_at_end"',
                   'trial.put("resource_before"',
                   'trial.put("resource_after"'):
        assert marker in PANEL

def test_gate_shared_across_all_alternative_model_panels():
    assert 'ModelLabBenchmarkGate.shared()' in PANEL
    for file in ("SherpaOmniPanel.java", "SherpaWhisperPanel.java",
                 "SherpaQwen3Panel.java"):
        assert 'ModelLabBenchmarkGate.shared()' in (JAVA/file).read_text(encoding="utf-8")
    assert 'compareAndSet(false, true)' in PANEL
    assert "gate.set(false)" in PANEL

def test_all_engines_two_cpu_threads():
    for file in ("SherpaOmniRunner.java","SherpaWhisperRunner.java",
                 "SherpaQwen3Runner.java"):
        source=(JAVA/file).read_text(encoding="utf-8")
        assert 'm.setNumThreads(2)' in source
        assert 'm.setProvider("cpu")' in source

def test_ui_has_real_comparison_action_ids():
    for suffix in ("engine","selected","all","result"):
        assert f"m2_asr_compare_{suffix}" in IDS
    lab=(JAVA/"ModelLabPanel.java").read_text(encoding="utf-8")
    assert "SherpaAsrComparisonPanel.attach(activity, parent, asr)" in lab

def test_real_phone_3way_receipt_has_consistent_comparison_and_no_effects():
    import json
    data=json.loads((ROOT/"benchmarks/model-lab/20261015-asr-three-way-onphone.json")
                    .read_text(encoding="utf-8"))
    assert data["schema_version"]=="1.0.0"
    assert data["session_type"]=="all_three"
    assert data["backend"]=="cpu"
    assert data["active_assistant_asr_at_start"]=="asr.vosk.fa-small-042"
    assert data["active_assistant_asr_at_end"]=="asr.vosk.fa-small-042"
    assert data["intent_effects"]==0
    assert data["reference_wav_sha256"]=="4a656d803a1e07eb3707365433dccdac7cf2f055d0e0aeae2084d4f6dd255bc3"
    trials=data["trials"]
    assert len(trials)==3
    assert [item["model_id"] for item in trials]==[
        "asr.omni_ctc_300m.int8",
        "asr.whisper.base.int8",
        "asr.qwen3.0_6b.int8"]
    assert all(item["status"]=="PASSED_NATIVE_INFERENCE" and
               item["threads"]==2 and
               item["total_native_ms"]>=item["decode_ms"]>0 and
               item["load_ms"]>0 for item in trials)
    assert [item["total_native_ms"] for item in trials]==[1118,774,3166]
    assert trials[2]["transcript"]=="Man Ordeia encarrou Nadara."
    assert all(item["resource_before"]["available_ram_bytes"]>2_000_000_000
               for item in trials)
