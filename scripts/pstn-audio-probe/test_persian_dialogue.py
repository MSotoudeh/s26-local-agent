from __future__ import annotations
import unittest
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
from persian_dialogue import DialogueSession, DialogueLimits, DialoguePolicyError, FixedPersianReasoner, Stage

class DialogueTests(unittest.TestCase):
    def new(self, turns=8):
        return DialogueSession("پیگیری وضعیت یک سفارش بدون تعهد یا پرداخت", FixedPersianReasoner(),
                               DialogueLimits(max_turns=turns))
    def test_persian_fixture_without_call(self):
        s=self.new()
        s.begin(authorized=True,remote_informed=True)
        reply=s.hear("سلام، لطفاً شماره سفارش را بگویید.")
        self.assertIn("شماره سفارش",reply)
        self.assertEqual(s.turns,1)
        self.assertEqual(len(s.history),2)
        s.complete()
        self.assertEqual(s.stage,Stage.COMPLETE)
    def test_consent_required(self):
        for approval,notice in ((True,False),(False,True),(False,False)):
            with self.subTest(approval=approval,notice=notice):
                s=self.new()
                with self.assertRaises(DialoguePolicyError): s.begin(authorized=approval,remote_informed=notice)
                self.assertEqual(s.stage,Stage.READY)
    def test_replay_rejected(self):
        s=self.new();s.begin(authorized=True,remote_informed=True)
        with self.assertRaises(DialoguePolicyError):s.begin(authorized=True,remote_informed=True)
    def test_sensitive_request_cancels(self):
        for text in ("لطفاً کد تأیید را بگویید", "شماره کارت را اعلام کنید", "OTP please"):
            s=self.new();s.begin(authorized=True,remote_informed=True)
            with self.assertRaises(DialoguePolicyError):s.hear(text)
            self.assertEqual(s.stage,Stage.CANCELLED)
            self.assertEqual(s.history,[])
    def test_turn_limit(self):
        s=self.new(turns=1);s.begin(authorized=True,remote_informed=True)
        s.hear("سلام")
        with self.assertRaises(DialoguePolicyError):s.hear("ادامه بده")
        self.assertEqual(s.stage,Stage.CANCELLED)
    def test_bad_input(self):
        s=self.new();s.begin(authorized=True,remote_informed=True)
        with self.assertRaises(DialoguePolicyError):s.hear("")
        with self.assertRaises(DialoguePolicyError):s.hear("خ"*601)
    def test_cancel_erases_history(self):
        s=self.new();s.begin(authorized=True,remote_informed=True);s.hear("سلام")
        s.cancel();self.assertEqual(s.history,[])
        with self.assertRaises(DialoguePolicyError):s.hear("دوباره")
    def test_refuse_invalid_goal(self):
        with self.assertRaises(DialoguePolicyError):DialogueSession("",FixedPersianReasoner())

if __name__ == "__main__": unittest.main()
