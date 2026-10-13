from __future__ import annotations
import json
import os
import re
import subprocess
import sys
import time
from datetime import date
from pathlib import Path

ADB = r"C:\Tools\Android\platform-tools\adb.exe"
URI = "content://io.projectir.mobile.ui"
APPS = [
    ("settings", "com.android.settings", True),
    ("calculator", "com.sec.android.app.popupcalculator", False),
    ("clock", "com.sec.android.app.clockpackage", False),
    ("maps", "com.google.android.apps.maps", False),
]
READS_PER_APP = 6
SETTINGS_CYCLES = 10


def adb(*args: str, timeout: float = 8.0) -> str:
    cp = subprocess.run([ADB, *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                        timeout=timeout, check=False)
    if cp.returncode:
        raise RuntimeError(f"ADB command failed: {args[0]} exit={cp.returncode}")
    return cp.stdout.decode("utf-8", errors="replace").strip()


def shell(cmd: str, timeout: float = 8.0) -> str:
    return adb("shell", cmd, timeout=timeout)


def content_query() -> tuple[dict, float]:
    start = time.perf_counter()
    raw = shell(f"content query --uri {URI}/state", timeout=8)
    ms = (time.perf_counter() - start) * 1000
    marker = "result="
    if marker not in raw:
        raise ValueError("Provider result missing")
    payload = raw.split(marker, 1)[1].strip()
    data = json.loads(payload)
    if not data.get("ok"):
        raise ValueError("Provider returned unavailable")
    return data["result"], round(ms, 2)


def call(method: str, arg: str) -> bool:
    raw = adb("shell", "content", "call", "--uri", URI, "--method", method, "--arg", arg, timeout=8)
    return '"ok":true' in raw


def texts(state: dict) -> set[str]:
    return {str(n.get("text") or "").strip() for n in state.get("nodes", []) if n.get("text")}


def pctl(values: list[float], q: float) -> float | None:
    if not values:
        return None
    v = sorted(values)
    index = round((len(v) - 1) * q)
    return round(v[index], 2)


def launch(pkg: str, clean: bool) -> tuple[bool, str]:
    if clean:
        shell(f"am force-stop {pkg}")
    try:
        if pkg == "com.sec.android.app.clockpackage":
            # Samsung Clock's resolved launcher is an explicit .ClockPackage activity.
            result = adb("shell", "am", "start", "-W", "-n", pkg + "/.ClockPackage", timeout=12)
        else:
            result = adb("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN",
                         "-c", "android.intent.category.LAUNCHER", "-p", pkg, timeout=12)
        if "Error:" in result:
            return False, "launch-rejected"
        return True, "launched"
    except Exception:
        return False, "launch-failed"


def read_app(label: str, pkg: str, clean: bool) -> dict:
    row: dict = {"app": label, "package": pkg, "samples": [], "launch_ok": False}
    launch_ok, reason = launch(pkg, clean)
    row["launch_ok"] = launch_ok
    row["launch_result"] = reason
    if not launch_ok:
        return row
    time.sleep(0.45)
    for i in range(READS_PER_APP):
        try:
            st, ms = content_query()
            row["samples"].append({
                "ok": st.get("packageName") == pkg and bool(st.get("rootAvailable")) and int(st.get("nodeCount", 0)) > 1,
                "latency_ms": ms,
                "node_count": int(st.get("nodeCount", 0)),
                "truncated": bool(st.get("truncated", False)),
                "package_match": st.get("packageName") == pkg,
            })
        except Exception as exc:
            row["samples"].append({"ok": False, "error_type": type(exc).__name__})
        time.sleep(0.04)
    good = [r for r in row["samples"] if r.get("ok")]
    latencies = [r["latency_ms"] for r in row["samples"] if "latency_ms" in r]
    row["summary"] = {
        "passed": len(good), "total": len(row["samples"]),
        "p50_ms": pctl(latencies, .5), "p95_ms": pctl(latencies, .95),
        "node_min": min((r["node_count"] for r in good), default=None),
        "node_max": max((r["node_count"] for r in good), default=None),
    }
    return row


def settings_cycles() -> dict:
    ok, why = launch("com.android.settings", True)
    out = {"attempted": SETTINGS_CYCLES, "passes": 0, "failures": [], "launch_ok": ok, "launch_reason": why}
    if not ok:
        return out
    time.sleep(.45)
    for i in range(SETTINGS_CYCLES):
        try:
            if not call("click_text", "Connections"):
                raise ValueError("click-rejected")
            time.sleep(.12)
            sub, _ = content_query()
            if not {"Wi-Fi", "Bluetooth"}.issubset(texts(sub)):
                time.sleep(.12)
                sub, _ = content_query()
            if not {"Wi-Fi", "Bluetooth"}.issubset(texts(sub)):
                raise ValueError("subpage-not-verified")
            if not call("global", "1"):
                raise ValueError("back-rejected")
            time.sleep(.1)
            root, _ = content_query()
            if not {"Connections", "Connected devices"}.issubset(texts(root)):
                time.sleep(.1)
                root, _ = content_query()
            if not {"Connections", "Connected devices"}.issubset(texts(root)):
                raise ValueError("root-not-verified")
            out["passes"] += 1
        except Exception as exc:
            out["failures"].append({"iteration": i + 1, "class": str(exc)[:64]})
            # Reset only Settings task, without touching other user applications.
            launch("com.android.settings", True)
            time.sleep(.3)
    return out


report = {"experiment": "M2-multi-app-semantic-smoke",
          "date": str(date.today()), "device_model": "SM-S948B", "reads_per_app": READS_PER_APP,
          "apps": [], "settings_semantic_cycles": {}}
try:
    for item in APPS:
        report["apps"].append(read_app(*item))
    report["settings_semantic_cycles"] = settings_cycles()
finally:
    try:
        adb("shell", "am", "start", "-n", "com.openai.chatgpt/.MainActivity", timeout=10)
        report["chatgpt_restored"] = True
    except Exception:
        report["chatgpt_restored"] = False

report["summary"] = {
    "apps_with_6_of_6": sum(r.get("summary", {}).get("passed") == READS_PER_APP for r in report["apps"]),
    "apps_tested": len(report["apps"]),
    "settings_cycles_pass": report["settings_semantic_cycles"].get("passes"),
    "settings_cycles_total": SETTINGS_CYCLES,
    "foreground_restored": report["chatgpt_restored"],
}
output = Path(__file__).resolve().parents[1] / "benchmarks" / "m2" / "20261013-multi-app-smoke.json"
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"summary": report["summary"], "app_summaries": [
    {"app": r["app"], "launch_ok": r["launch_ok"], "summary": r.get("summary")} for r in report["apps"]],
    "settings_failures": report["settings_semantic_cycles"].get("failures", [])}, ensure_ascii=False, indent=2))
if report["summary"]["apps_with_6_of_6"] < len(APPS) or report["summary"]["settings_cycles_pass"] < SETTINGS_CYCLES or not report["chatgpt_restored"]:
    sys.exit(2)
