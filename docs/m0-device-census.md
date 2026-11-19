# M0 — Device and Runtime Census

## Purpose

Establish the actual development baseline before choosing Android/NPU runtimes.

M0 has two parts:

### M0-A — Ops host/tooling
Can run without the phone:
- OS;
- Git;
- Java;
- Python;
- Android platform-tools;
- ADB server;
- candidate SDK/runtime directories;
- repo environment.

### M0-B — S26 device probe
Requires the phone to be visible to ADB:
- serial/model;
- Android version/API/build;
- ABI;
- SoC-relevant properties;
- RAM;
- storage;
- SELinux;
- battery/thermal state;
- USB transport;
- installed packages relevant to Shizuku/control runtimes;
- selected Android properties useful for NPU/runtime qualification.

## Current finding

On Ops, Android platform-tools exists at:

```text
C:\Tools\Android\platform-tools\adb.exe
```

The current Project IR session can resolve an ADB installation, but project scripts still resolve known explicit locations so behavior does not depend on the agent session's PATH.

Census status on 2026-10-05: the S26 Ultra was attached and authorized over USB ADB. Captured baseline: Samsung SM-S948B, Android 17 / API 37, arm64-v8a, QTI SM8850, SELinux Enforcing. At census time Mobilerun Portal and Shizuku were installed and Shizuku was not running. This is historical evidence; Mobilerun was later removed and the canonical runtime became Project IR Mobile Agent + Shizuku + native Accessibility.

## Output

`scripts/m0-device-census.ps1` writes:
- `benchmarks/m0/<timestamp>-host.json`
- `benchmarks/m0/<timestamp>-device.json` when a device is available
- `benchmarks/m0/<timestamp>-summary.md`

## M0 decision

M0 completion criterion: both host and device evidence must be captured at least once.

Current result: **COMPLETE / GO**.

The device census is sufficient for control-plane work. NPU/runtime compatibility remains a later dedicated benchmark rather than an M0 blocker.

## Next

M0.5 control-plane feasibility has also passed. Continue with stable selectors, event-driven settle/recovery, text input, scroll/dynamic UI reliability, and reconnect testing before moving to the minimal production on-device bridge.
