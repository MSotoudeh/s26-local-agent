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

It is not currently on the SYSTEM PATH used by Remote Desktop Commander. Scripts therefore resolve this explicit path rather than assuming `adb` is globally available.

At the first M0 run, no Android device was attached according to `adb devices -l`. This is recorded as an environment state, not a project failure.

## Output

`scripts/m0-device-census.ps1` writes:
- `benchmarks/m0/<timestamp>-host.json`
- `benchmarks/m0/<timestamp>-device.json` when a device is available
- `benchmarks/m0/<timestamp>-summary.md`

## M0 decision

M0 is complete only when both host and device evidence have been captured at least once.

Host-only result: PARTIAL / waiting for device transport.

## Next

Once device evidence exists:
1. verify Shizuku package/state;
2. record One UI/Android versions;
3. inspect NPU/runtime compatibility;
4. proceed to M0.5 control-plane baseline.
