"""Read only evaluation of upstream Vosk Persian public bundled WAV reference.

Never outputs UI snapshots, app tokens, user data or raw transcripts.
"""
import json
import pathlib
import re
import subprocess
import sys
import unicodedata

ADB = r"C:\Users\mmsot\AppData\Local\Android\Sdk\platform-tools\adb.exe"
REF = pathlib.Path(r"C:\Users\mmsot\AppData\Local\ProjectIR\models\fa-asr\expanded\vosk-model-small-fa-0.42\test\test.txt")


def normalized(s: str) -> str:
    s = unicodedata.normalize("NFKC", s).replace("ي", "ی").replace("ك", "ک")
    s = s.replace("\u200c", " ")
    return re.sub(r"[^\w\s]", " ", s.lower()).strip()


def edit_distance(a, b) -> int:
    prev = list(range(len(b) + 1))
    for i, ai in enumerate(a, 1):
        row = [i]
        for j, bj in enumerate(b, 1):
            row.append(min(row[-1] + 1, prev[j] + 1, prev[j - 1] + (ai != bj)))
        prev = row
    return prev[-1]


def main():
    raw = subprocess.run([ADB, "shell", "content", "call", "--uri",
                          "content://io.projectir.mobile.ui", "--method", "state"],
                         check=True, capture_output=True, text=True, timeout=12).stdout.strip()
    prefix = "Result: Bundle[{result="
    if not raw.startswith(prefix) or not raw.endswith("}]"):
        raise RuntimeError("Accessibility reply invalid")
    data = json.loads(raw[len(prefix):-2])
    if data["result"]["packageName"] != "io.projectir.mobile":
        raise RuntimeError("MainActivity not foreground")
    inputs = [n.get("text", "") for n in data["result"].get("nodes", [])
              if n.get("resourceId") == "io.projectir.mobile:id/local_command_input"]
    if len(inputs) != 1:
        raise RuntimeError("Canonical transcript input not unique")
    reference = REF.read_text(encoding="utf-8").strip()
    transcript = str(inputs[0]).strip()
    if not reference or not transcript:
        raise RuntimeError("Public sample or ASR transcript is empty")
    # Vosk sample reference can include more than one line; report explicitly.
    ref = normalized(reference)
    hyp = normalized(transcript)
    words_r, words_h = ref.split(), hyp.split()
    metrics = {
        "sample": "Vosk upstream bundled Persian 48k mono 5.448-second WAV",
        "ref_lines": len(reference.splitlines()),
        "reference_words": len(words_r),
        "hypothesis_words": len(words_h),
        "wer": round(edit_distance(words_r, words_h) / max(len(words_r), 1), 3),
        "cer": round(edit_distance(ref, hyp) / max(len(ref), 1), 3),
        "reference_chars": len(ref),
        "output_is_public_fixture": True,
        "microphone_recording": False,
        "private_transcript_export": False,
    }
    print(json.dumps(metrics, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
