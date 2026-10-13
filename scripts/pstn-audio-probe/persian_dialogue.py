"""Testable no-call conversation state machine.

All inputs are *text fixtures*. This is not speech recognition, LLM inference
or speech synthesis, and there is no phone/network/device access.
"""
from dataclasses import dataclass
from enum import Enum
from typing import Protocol
import re

class Stage(str, Enum):
    READY="ready"
    WAITING="waiting_for_remote"
    COMPLETE="complete"
    CANCELLED="cancelled"

class DialoguePolicyError(RuntimeError): pass

class Reasoner(Protocol):
    def respond(self, goal: str, history: tuple[tuple[str, str], ...], message: str) -> str: ...

@dataclass(frozen=True)
class DialogueLimits:
    max_turns: int = 8
    max_chars_per_turn: int = 600
    def __post_init__(self):
        if not 1 <= self.max_turns <= 30 or not 20 <= self.max_chars_per_turn <= 10000:
            raise ValueError("unsafe limits")

class DialogueSession:
    def __init__(self, goal: str, reasoner: Reasoner, limits: DialogueLimits = DialogueLimits()):
        if not goal.strip() or len(goal) > 500:
            raise DialoguePolicyError("missing/oversize call goal")
        self.goal=goal
        self.reasoner=reasoner
        self.limits=limits
        self.stage=Stage.READY
        self.history: list[tuple[str, str]]=[]
        self.turns=0

    def begin(self, *, authorized: bool, remote_informed: bool):
        if self.stage != Stage.READY:
            raise DialoguePolicyError("session already started")
        if not authorized or not remote_informed:
            raise DialoguePolicyError("consent gate")
        self.stage=Stage.WAITING

    def hear(self, transcript: str) -> str:
        if self.stage != Stage.WAITING:
            raise DialoguePolicyError("not accepting transcript")
        if not transcript.strip() or len(transcript) > self.limits.max_chars_per_turn:
            raise DialoguePolicyError("invalid transcript")
        self.turns+=1
        if self.turns > self.limits.max_turns:
            self.cancel()
            raise DialoguePolicyError("turn limit exceeded")
        # No unreviewed commitment, financial authorization, payment, OTP or
        # arbitrary phone content can be automated.
        if re.search(r"(?i)(رمز یکبار|رمز پویا|کد تأیید|شماره کارت|پرداخت|واریز|تعهد مالی|otp|password|iban)", transcript):
            self.cancel()
            raise DialoguePolicyError("sensitive request, escalation required")
        reply=self.reasoner.respond(self.goal, tuple(self.history), transcript)
        if not reply.strip() or len(reply) > self.limits.max_chars_per_turn:
            self.cancel()
            raise DialoguePolicyError("unsafe/oversize response")
        self.history.extend([("remote", transcript), ("agent", reply)])
        return reply

    def cancel(self):
        self.stage=Stage.CANCELLED
        # Cleanup conversation content from active memory on cancellation.
        self.history.clear()

    def complete(self):
        if self.stage != Stage.WAITING: raise DialoguePolicyError("not active")
        self.stage=Stage.COMPLETE

class FixedPersianReasoner:
    """Fixtures ONLY; model adapters must implement Reasoner."""
    def respond(self, goal: str, history: tuple[tuple[str,str],...], message: str) -> str:
        if "شماره سفارش" in message:
            return "شماره سفارش را برای بررسی اعلام می‌کنم، پس از تأیید صاحب حساب."
        return "متشکرم. برای بررسی درخواست چه اطلاعات عمومی دیگری نیاز دارید؟"
