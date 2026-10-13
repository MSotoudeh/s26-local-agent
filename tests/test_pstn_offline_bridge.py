"""Repeatable phone-free test suite for the PCM bridge."""
from __future__ import annotations

import math
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts" / "pstn-audio-probe"))
from offline_bridge import (
    AudioContractError, AudioGateError, OfflineAudioBridge, Phase,
    CAPTURE_FRAME_BYTES, MODEL_FRAME_BYTES,
    downmix_48k_stereo_to_16k_mono, pcm16_bytes, pcm16_samples,
    rms_level_pcm16,
)


def constant_capture(level: int = 1000) -> bytes:
    from array import array
    return pcm16_bytes(array("h", [level, level] * 960))


def constant_model(level: int = 1000) -> bytes:
    from array import array
    return pcm16_bytes(array("h", [level] * 320))


class PstnOfflineBridgeTests(unittest.TestCase):
    def test_frame_contract_48k_stereo_to_16k_mono(self):
        data = downmix_48k_stereo_to_16k_mono(constant_capture(1234))
        self.assertEqual(len(data), MODEL_FRAME_BYTES)
        self.assertEqual(set(pcm16_samples(data)), {1234})

    def test_silence_and_signed_extremes(self):
        for value in (0, -32768, 32767):
            data = downmix_48k_stereo_to_16k_mono(constant_capture(value))
            self.assertEqual(set(pcm16_samples(data)), {value})
        self.assertEqual(rms_level_pcm16(constant_model(0)), 0.0)

    def test_shape_rejection(self):
        with self.assertRaises(AudioContractError):
            downmix_48k_stereo_to_16k_mono(b"\x00" * (CAPTURE_FRAME_BYTES - 2))
        with self.assertRaises(AudioContractError):
            pcm16_samples(b"\x00")
        with self.assertRaises(AudioContractError):
            OfflineAudioBridge(max_queued_frames=0)

    def test_cannot_activate_without_capability_and_consent(self):
        for consent, capability in ((False, True), (True, False), (False, False)):
            with self.subTest(consent=consent, capability=capability):
                bridge = OfflineAudioBridge()
                with self.assertRaises(AudioGateError):
                    bridge.arm(user_consented=consent, device_interceptable=capability)
                self.assertEqual(bridge.phase, Phase.IDLE)

    def test_no_capture_before_connection(self):
        bridge = OfflineAudioBridge()
        with self.assertRaises(AudioGateError):
            bridge.receive_downlink(constant_capture())
        bridge.arm(user_consented=True, device_interceptable=True)
        with self.assertRaises(AudioGateError):
            bridge.receive_downlink(constant_capture())
        bridge.connected()
        bridge.receive_downlink(constant_capture())
        self.assertEqual(bridge.read_for_stt(), constant_model())

    def test_bounded_backpressure_drops_oldest(self):
        bridge = OfflineAudioBridge(max_queued_frames=2)
        bridge.arm(user_consented=True, device_interceptable=True)
        bridge.connected()
        for value in (100, 200, 300, 400):
            bridge.receive_downlink(constant_capture(value))
        self.assertEqual(bridge.counters().received_frames, 4)
        self.assertEqual(bridge.counters().dropped_frames, 2)
        self.assertEqual(bridge.counters().queued_frames, 2)
        self.assertEqual(bridge.read_for_stt(), constant_model(300))
        self.assertEqual(bridge.read_for_stt(), constant_model(400))
        self.assertIsNone(bridge.read_for_stt())

    def test_uplink_blocked_unless_explicitly_approved(self):
        bridge = OfflineAudioBridge()
        bridge.arm(user_consented=True, device_interceptable=True, uplink_test_approved=False)
        bridge.connected()
        with self.assertRaises(AudioGateError):
            bridge.enqueue_tts(constant_model())
        with self.assertRaises(AudioGateError):
            bridge.next_uplink_for_adapter()

    def test_uplink_shape_and_fifo(self):
        bridge = OfflineAudioBridge()
        bridge.arm(user_consented=True, device_interceptable=True, uplink_test_approved=True)
        bridge.connected()
        with self.assertRaises(AudioContractError):
            bridge.enqueue_tts(b"\x00")
        bridge.enqueue_tts(constant_model(100))
        bridge.enqueue_tts(constant_model(200))
        self.assertEqual(bridge.next_uplink_for_adapter(), constant_model(100))
        self.assertEqual(bridge.next_uplink_for_adapter(), constant_model(200))
        self.assertIsNone(bridge.next_uplink_for_adapter())
        self.assertEqual(bridge.counters().playback_frames, 2)

    def test_watchdog_flushes_buffers_and_disarms_uplink(self):
        now = [100.0]
        bridge = OfflineAudioBridge(clock=lambda: now[0], heartbeat_timeout_s=1.0)
        bridge.arm(user_consented=True, device_interceptable=True, uplink_test_approved=True)
        bridge.connected()
        bridge.receive_downlink(constant_capture(500))
        bridge.enqueue_tts(constant_model(500))
        now[0] = 101.01
        with self.assertRaisesRegex(AudioGateError, "watchdog"):
            bridge.read_for_stt()
        self.assertEqual(bridge.phase, Phase.STOPPED)
        self.assertEqual(bridge.counters().queued_frames, 0)
        with self.assertRaises(AudioGateError):
            bridge.next_uplink_for_adapter()

    def test_explicit_stop_clears_queues(self):
        bridge = OfflineAudioBridge()
        bridge.arm(user_consented=True, device_interceptable=True, uplink_test_approved=True)
        bridge.connected()
        bridge.receive_downlink(constant_capture())
        bridge.enqueue_tts(constant_model())
        bridge.stop()
        self.assertEqual(bridge.counters().queued_frames, 0)
        with self.assertRaises(AudioGateError):
            bridge.enqueue_tts(constant_model())

    def test_synthetic_tone_survives_bridge(self):
        from array import array
        samples = array("h")
        for index in range(960):
            value = int(4000 * math.sin(2 * math.pi * 440 * index / 48000))
            samples.extend((value, value))
        data = downmix_48k_stereo_to_16k_mono(pcm16_bytes(samples))
        self.assertEqual(len(data), MODEL_FRAME_BYTES)
        self.assertGreater(rms_level_pcm16(data), 2000)
        self.assertLess(rms_level_pcm16(data), 3200)


if __name__ == "__main__":
    unittest.main()
