"""Offline-only PCM and lifecycle primitives for a future PSTN voice agent.

NO calls, no audio APIs, no Shizuku privileges, no network access.
Does NOT prove capture or injection on a real Samsung call.
"""
from __future__ import annotations

from array import array
from collections import deque
from dataclasses import dataclass
from enum import Enum
from math import sqrt
from time import monotonic
import sys


FRAME_MS = 20
CAPTURE_RATE = 48000
CAPTURE_CHANNELS = 2
MODEL_RATE = 16000
MODEL_CHANNELS = 1
SAMPLE_BYTES = 2
CAPTURE_FRAME_BYTES = CAPTURE_RATE * FRAME_MS // 1000 * CAPTURE_CHANNELS * SAMPLE_BYTES
MODEL_FRAME_BYTES = MODEL_RATE * FRAME_MS // 1000 * MODEL_CHANNELS * SAMPLE_BYTES


class AudioContractError(ValueError):
    pass


class AudioGateError(RuntimeError):
    pass


def pcm16_samples(data: bytes) -> array:
    if len(data) % SAMPLE_BYTES:
        raise AudioContractError("unaligned PCM16 data")
    samples = array("h")
    samples.frombytes(data)
    if sys.byteorder != "little":
        samples.byteswap()
    return samples


def pcm16_bytes(samples: array) -> bytes:
    samples = array("h", samples)
    if sys.byteorder != "little":
        samples.byteswap()
    return samples.tobytes()


def downmix_48k_stereo_to_16k_mono(data: bytes) -> bytes:
    """20ms S16LE, 48kHz stereo -> 20ms S16LE, 16kHz mono.

    A three-sample box-average is acceptable for contract tests only;
    replace with a proper anti-aliasing resampler (e.g. soxr/SpeexDSP)
    before using actual speech.
    """
    if len(data) != CAPTURE_FRAME_BYTES:
        raise AudioContractError(
            f"expected {CAPTURE_FRAME_BYTES} capture bytes; got {len(data)}"
        )
    values = pcm16_samples(data)
    output = array("h")
    for i in range(0, len(values), 6):
        avg = sum(values[i:i + 6]) / 6.0
        output.append(max(-32768, min(32767, round(avg))))
    if len(output) * SAMPLE_BYTES != MODEL_FRAME_BYTES:
        raise AudioContractError("resampler frame length mismatch")
    return pcm16_bytes(output)


def rms_level_pcm16(frame: bytes) -> float:
    samples = pcm16_samples(frame)
    return sqrt(sum(int(x) * int(x) for x in samples) / len(samples)) if samples else 0.0


class Phase(Enum):
    IDLE = "idle"
    ARMED = "armed"
    ACTIVE = "active"
    STOPPED = "stopped"


@dataclass(frozen=True)
class BridgeCounters:
    received_frames: int
    queued_frames: int
    dropped_frames: int
    playback_frames: int
    phase: str


class OfflineAudioBridge:
    """Small bounded frame transport contract; deliberately no device I/O.

    Caller/real-call state must come from a separately audited telephony
    adapter. No call initiation, audio capture, recording, or playback here.
    """

    def __init__(self, *, max_queued_frames: int = 10, heartbeat_timeout_s: float = 3.0,
                 clock=monotonic):
        if not 1 <= max_queued_frames <= 100:
            raise AudioContractError("max_queued_frames must be 1..100")
        if not 0.1 <= heartbeat_timeout_s <= 60:
            raise AudioContractError("heartbeat_timeout_s must be 0.1..60")
        self.max_queued_frames = max_queued_frames
        self.heartbeat_timeout_s = heartbeat_timeout_s
        self.clock = clock
        self.phase = Phase.IDLE
        self._downlink: deque[bytes] = deque()
        self._uplink: deque[bytes] = deque()
        self._last_heartbeat = None
        self._can_inject = False
        self._received = 0
        self._dropped = 0
        self._playback = 0

    def arm(self, *, user_consented: bool, device_interceptable: bool,
            uplink_test_approved: bool = False) -> None:
        if self.phase is not Phase.IDLE:
            raise AudioGateError("arm permitted only from idle")
        if not user_consented or not device_interceptable:
            raise AudioGateError("consent and device capabilities required")
        self._can_inject = uplink_test_approved
        self.phase = Phase.ARMED

    def connected(self) -> None:
        if self.phase is not Phase.ARMED:
            raise AudioGateError("only an armed bridge can become active")
        self._last_heartbeat = self.clock()
        self.phase = Phase.ACTIVE

    def heartbeat(self) -> None:
        if self.phase is not Phase.ACTIVE:
            raise AudioGateError("heartbeat outside active state")
        self._last_heartbeat = self.clock()

    def _check_live(self) -> None:
        if self.phase is not Phase.ACTIVE:
            raise AudioGateError("frame rejected: no active call")
        if self.clock() - self._last_heartbeat > self.heartbeat_timeout_s:
            self.stop()
            raise AudioGateError("watchdog expired; queues flushed")

    def receive_downlink(self, captured_frame: bytes) -> None:
        self._check_live()
        converted = downmix_48k_stereo_to_16k_mono(captured_frame)
        if len(self._downlink) >= self.max_queued_frames:
            self._downlink.popleft()
            self._dropped += 1
        self._downlink.append(converted)
        self._received += 1

    def read_for_stt(self) -> bytes | None:
        self._check_live()
        return self._downlink.popleft() if self._downlink else None

    def enqueue_tts(self, model_frame: bytes) -> None:
        self._check_live()
        if not self._can_inject:
            raise AudioGateError("uplink injection is not authorized")
        if len(model_frame) != MODEL_FRAME_BYTES:
            raise AudioContractError("TTS must provide 20ms 16kHz mono PCM16")
        if len(self._uplink) >= self.max_queued_frames:
            self._uplink.popleft()
            self._dropped += 1
        self._uplink.append(bytes(model_frame))

    def next_uplink_for_adapter(self) -> bytes | None:
        self._check_live()
        if not self._can_inject:
            raise AudioGateError("uplink injection is not authorized")
        frame = self._uplink.popleft() if self._uplink else None
        if frame is not None:
            self._playback += 1
        return frame

    def stop(self) -> None:
        self._downlink.clear()
        self._uplink.clear()
        self._can_inject = False
        self._last_heartbeat = None
        self.phase = Phase.STOPPED

    def counters(self) -> BridgeCounters:
        return BridgeCounters(self._received, len(self._downlink),
                              self._dropped, self._playback, self.phase.value)
