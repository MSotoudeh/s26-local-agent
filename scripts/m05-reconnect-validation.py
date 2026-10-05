from __future__ import annotations

import json
import subprocess
import time

from host.phone_controller import MobilerunUsbSession, find_adb

ADB = find_adb()
results = []


def one(label: str) -> None:
    t0 = time.perf_counter_ns()
    try:
        with MobilerunUsbSession(adb=ADB) as s:
            state = s.phone_state()
        ok = bool(state.get("packageName"))
        error = None
    except Exception as exc:
        ok = False
        error = str(exc)
    t1 = time.perf_counter_ns()
    results.append(
        {
            "label": label,
            "ok": ok,
            "ms": round((t1 - t0) / 1e6, 2),
            "error": error,
        }
    )


for i in range(3):
    one(f"before_restart_{i+1}")

subprocess.run([ADB, "kill-server"], check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
time.sleep(0.15)

for i in range(3):
    one(f"after_restart_{i+1}")

print(
    json.dumps(
        {
            "all_ok": all(item["ok"] for item in results),
            "results": results,
        },
        indent=2,
    )
)
