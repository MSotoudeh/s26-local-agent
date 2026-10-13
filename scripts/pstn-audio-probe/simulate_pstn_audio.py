"""Run a deterministic PSTN-like *offline* PCM smoke test on the host.

Only synthetic audio and fixed Persian text fixtures are used. This does not
dial a number, access the microphone, run ASR/LLM/TTS, or inject call audio.
"""
from __future__ import annotations
from array import array
import json
import math
from pathlib import Path
import sys
from time import perf_counter

sys.path.insert(0, str(Path(__file__).resolve().parent))
from offline_bridge import OfflineAudioBridge, MODEL_FRAME_BYTES, Phase, pcm16_bytes, rms_level_pcm16

FRAMES = 100  # two seconds at 20 ms/frame
PERSIAN_ASR_FIXTURE = "سلام، برای پیگیری سفارش تماس گرفتم."
PERSIAN_LLM_FIXTURE = "سلام. لطفاً شماره سفارش را اعلام کنید."


def tone(rate: int, channels: int, index: int, hz: int = 440) -> bytes:
    samples = array("h")
    frames = rate // 50
    for j in range(frames):
        sample = round(3200 * math.sin(2 * math.pi * hz * (index * frames + j) / rate))
        for _ in range(channels):
            samples.append(sample)
    return pcm16_bytes(samples)


def main() -> int:
    bridge = OfflineAudioBridge(max_queued_frames=8, heartbeat_timeout_s=3)
    bridge.arm(user_consented=True, device_interceptable=True, uplink_test_approved=True)
    bridge.connected()
    start = perf_counter()
    min_rms = float("inf")
    for index in range(FRAMES):
        # Audio fixture represents voice downlink; it is never sourced from PSTN.
        bridge.receive_downlink(tone(48000, 2, index))
        downlink = bridge.read_for_stt()
        if downlink is None or len(downlink) != MODEL_FRAME_BYTES:
            raise RuntimeError("missing or malformed downlink frame")
        min_rms = min(min_rms, rms_level_pcm16(downlink))
        # TTS fixture is only a tone, NOT synthesized Persian speech.
        synthetic_uplink = tone(16000, 1, index)
        bridge.enqueue_tts(synthetic_uplink)
        if bridge.next_uplink_for_adapter() != synthetic_uplink:
            raise RuntimeError("uplink PCM did not match source")
        bridge.heartbeat()
    total_ms = round((perf_counter() - start) * 1000, 2)
    counters = bridge.counters()
    bridge.stop()
    if bridge.phase is not Phase.STOPPED:
        raise RuntimeError("bridge did not stop")
    print(json.dumps({
        "device_call": "NOT_ATTEMPTED",
        "audio_input": "SYNTHETIC_TONE",
        "audio_output": "SYNTHETIC_TONE",
        "persian_asr": "NOT_RUN_FIXTURE_ONLY",
        "persian_llm": "NOT_RUN_FIXTURE_ONLY",
        "persian_tts": "NOT_RUN_TONE_ONLY",
        "fixture_transcript": PERSIAN_ASR_FIXTURE,
        "fixture_reply": PERSIAN_LLM_FIXTURE,
        "simulated_frames": FRAMES,
        "simulated_seconds": FRAMES / 50,
        "capture_to_model_output_bytes_per_frame": MODEL_FRAME_BYTES,
        "min_rms": round(min_rms, 1),
        "host_loop_elapsed_ms": total_ms,
        "received_frames": counters.received_frames,
        "returned_uplink_frames": counters.playback_frames,
        "dropped_frames": counters.dropped_frames,
        "final_state": bridge.phase.value,
        "result": "PASS" if counters.received_frames == FRAMES and counters.playback_frames == FRAMES and counters.dropped_frames == 0 else "FAIL"
    }, ensure_ascii=True, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
