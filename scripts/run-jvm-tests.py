"""Compile and run the standalone JVM tests in android/mobile-agent/tests.

The tests exercise the pure-Java core of the Android agent (router, planner,
plan machine, Model Lab gates) without the Android SDK, Gradle or a device.
Each test is compiled together with every main source file that has no
Android or third-party imports, then run with `java`. Requires a JDK (javac
and java on PATH, or JAVA_HOME set).

Usage: python scripts/run-jvm-tests.py
"""
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
MAIN = ROOT / "android/mobile-agent/app/src/main/java/io/projectir/mobile"
TESTS = ROOT / "android/mobile-agent/tests"
NON_PURE = re.compile(r"^import (android|androidx|com|org|rikka)\.", re.M)


def tool(name: str) -> str:
    home = os.environ.get("JAVA_HOME")
    if home:
        candidate = pathlib.Path(home) / "bin" / (name + (".exe" if os.name == "nt" else ""))
        if candidate.exists():
            return str(candidate)
    found = shutil.which(name)
    if not found:
        sys.exit(f"{name} not found; install a JDK (17+) or set JAVA_HOME")
    return found


def main() -> int:
    javac, java = tool("javac"), tool("java")
    pure = [str(p) for p in sorted(MAIN.glob("*.java"))
            if not NON_PURE.search(p.read_text(encoding="utf-8"))]
    failures = 0
    for test in sorted(TESTS.glob("*Test.java")):
        with tempfile.TemporaryDirectory() as out:
            compiled = subprocess.run([javac, "-encoding", "UTF-8", "-d", out, *pure, str(test)],
                                      capture_output=True, text=True)
            if compiled.returncode != 0:
                print(f"FAIL {test.stem}: compilation\n{compiled.stderr}")
                failures += 1
                continue
            run = subprocess.run([java, "-cp", out, "io.projectir.mobile." + test.stem],
                                 capture_output=True, text=True, encoding="utf-8", timeout=300)
            last = (run.stdout.strip().splitlines() or [""])[-1]
            status = "PASS" if run.returncode == 0 else "FAIL"
            failures += run.returncode != 0
            print(f"{status} {test.stem} {last}")
            if run.returncode != 0:
                print(run.stdout[-2000:], run.stderr[-2000:])
    print(f"{failures} failed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
