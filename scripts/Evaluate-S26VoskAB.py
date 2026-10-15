#!/usr/bin/env python3
"""Reproducible single-fixture Persian ASR WER/CER scoring; no remote actions.

No claim of model quality ranking from this one public fixture. Normalize
Arabic/Persian Yeh/Kaf, diacritics and presentation punctuation, not colloquial
word boundaries. Compute literal WER and CER with edit distance.
"""
import json
from pathlib import Path
import re
import sys

ROOT=Path(__file__).resolve().parents[1]
RESULT=ROOT/"benchmarks"/"model-lab"/"20261015-vosk-fa-small-042-vs-05.json"

def normalize(s: str) -> str:
    changes=str.maketrans({"ي":"ی","ك":"ک","ى":"ی","ة":"ه","ۀ":"ه","أ":"ا","إ":"ا"})
    s=s.translate(changes)
    s=re.sub(r"[\u064b-\u065f\u0670\u06d6-\u06ed]", "", s)
    s=re.sub(r"[\u200c\s]+", " ", s)
    s=re.sub(r"[^\u0600-\u06ff\w\s]", "", s)
    return s.strip().lower()

def distance(a, b):
    previous=list(range(len(b)+1))
    for i,x in enumerate(a,1):
        row=[i]
        for j,y in enumerate(b,1):
            row.append(min(row[-1]+1,previous[j]+1,previous[j-1]+(x!=y)))
        previous=row
    return previous[-1]

def score(data):
    fixture=data["common_fixture"]
    ref=normalize(fixture["reference_text_original"])
    out=[]
    assert len(data["models"])==2
    assert len({m["model_id"] for m in data["models"]})==2
    for m in data["models"]:
        if not m["native_load_confirmed"] or not m["same_wav_processed"]:
            raise ValueError("MODEL_NOT_ACTUALLY_MEASURED")
        pred=normalize(m["transcript"])
        wer=distance(ref.split(),pred.split())/max(1,len(ref.split()))
        cer=distance(list(ref.replace(" ","")),list(pred.replace(" ","")))/max(
            1,len(ref.replace(" ","")))
        out.append({"model_id":m["model_id"],"predicted":pred,
            "reference":ref,"wer":round(wer,4),"cer":round(cer,4),
            "e2e_wall_ms_including_rpc":m["wall_clock_ui_ms"]})
    return {"fixture_sha256":fixture["sha256"],"runs":out,
       "warning":"One short fixture cannot rank model quality; wall time includes UI/RPC, not pure decoding."}

if __name__=="__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    print(json.dumps(score(json.loads(RESULT.read_text(encoding="utf-8"))),
          ensure_ascii=False,indent=2))
