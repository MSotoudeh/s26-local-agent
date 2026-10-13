"""Self-tests of evaluation math, not trained-model or ASR performance."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

spec=importlib.util.spec_from_file_location("evaluate",Path(__file__).with_name("evaluate.py"))
mod=importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class EvaluationTests(unittest.TestCase):
    def test_normalize_persian(self):
        self.assertEqual(mod.normalize("تنظيمات\u200c رو  باز كن!"),"تنظیمات رو باز کن")

    def test_character_distance(self):
        self.assertEqual(mod.levenshtein(list("abc"),list("axc")),1)
        self.assertEqual(mod.levenshtein([],list("abcd")),4)

    def test_intent_metrics(self):
        reference={"a":{"intent":"OPEN"},"b":{"intent":"OPEN"},"c":{"intent":"STOP"}}
        predicted={"a":{"intent":"OPEN"},"b":{"intent":"STOP"},"c":{"intent":"STOP"}}
        result=mod.evaluate(reference,predicted,"intent")
        self.assertEqual(result["samples"],3)
        self.assertAlmostEqual(result["accuracy"],2/3)
        self.assertGreater(result["macro_f1"],0)
        self.assertLess(result["macro_f1"],1)

    def test_asr_metrics_and_entity(self):
        reference={"1":{"transcript":"سلام دنیا","entities":["دنیا"]}}
        pred={"1":{"transcript":"سلام دنیا"}}
        score=mod.evaluate(reference,pred,"asr")
        self.assertEqual(score["cer"],0)
        self.assertEqual(score["wer"],0)
        self.assertEqual(score["entity_preservation"],1)

    def test_entity_requires_word_match(self):
        reference={"1":{"transcript":"سلام علی","entities":["علی"]}}
        prediction={"1":{"transcript":"سلام علیها"}}
        score=mod.evaluate(reference,prediction,"asr")
        self.assertEqual(score["entity_preservation"],0)

    def test_asr_mismatch(self):
        ref={"a":{"transcript":"سلام خوبی"}}
        predicted={"a":{"transcript":"سلام بدی"}}
        score=mod.evaluate(ref,predicted,"asr")
        self.assertGreater(score["cer"],0)
        self.assertEqual(score["wer"],0.5)

    def test_no_missing_predictions(self):
        with self.assertRaises(ValueError):
            mod.evaluate({"x":{"intent":"a"}},{}, "intent")

    def test_empty_transcript_rejected(self):
        with self.assertRaises(ValueError):
            mod.evaluate({"x":{"transcript":""}},
                         {"x":{"transcript":""}},"asr")

    def test_duplicate_ids_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/"x.jsonl"
            p.write_text('{"id":"x"}\n{"id":"x"}\n',encoding="utf-8")
            with self.assertRaises(ValueError):
                mod.load_jsonl(p)


if __name__=="__main__":
    unittest.main(verbosity=2)
