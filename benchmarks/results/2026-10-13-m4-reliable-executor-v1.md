# M4 — Reliable Local Executor v1: implementation and on-device smoke

Date: 2026-10-13
Phone: Samsung S26 Ultra (SM-S948B)
Project IR feature: `feature/android-agent`

## What changed

Implemented in the existing Android Mobile Agent:
- Opaque Action ID per explicitly submitted user task, generated as UUID by
  default or supplied to the typed `execute(input, actionId)` API.
- Synchronous on-device transaction that commits the accepted command enum,
  unique action ID and `ACCEPTED` status before dispatch.
- Bounded last-64 identity journal for replay protection across app process
  recreation. It does not guarantee lifetime uniqueness after eviction.
- Cancel button, which marks `CANCELLED` and invalidates future poll callbacks.
  Already-dispatched Android effects cannot be reversed by Cancel.
- Interrupted process handling: `ACCEPTED`/`RUNNING` from an old process
  becomes `INTERRUPTED` when the task manager is reconstructed.
- No extra Android permission, cloud dependency, generic shell instruction or
  additional phone control package.

## Validation

| Test | Observed |
| --- | --- |
| Pure JVM Action ID assertions | 17/17 PASS (includes 1000 journal insertions) |
| Existing local command parser | 15/15 PASS |
| Project IR Python regression | 52/52 PASS |
| Android assembleDebug + lintDebug | PASS on final source |
| Signing identity | Matched installed app certificate |
| APK update | `adb install -r` PASS, app data retained |
| New UI controls | Input, Run, Cancel present (3/3) |
| Local status on-device repetition | **30/30 PASS_VERIFIED, 30/30 distinct IDs** |
| Settings -> Connections initial regression batch | 3/5; interrupted by a host ADB timeout |
| Settings -> Connections repeat with increased host timeout | **5/5 PASS_VERIFIED, 5/5 distinct IDs and page assertions** |
| Foreground restoration | Launcher restored |

The 30 status-command samples took 0-2 ms *inside LocalTaskRunner*;
this excludes ADB and host invocation overhead. The five Settings workflow
task durations were 529, 531, 538, 534 and 460 ms (individual samples, not
p50/p95 population estimates).

### Failure preserved

The first Settings batch completed three verified trials (471, 514 and
497 ms) and then exceeded the older 12-second host ADB timeout. This was
recorded as a failed **3/5** batch, not silently converted to success.
The rerun used 30-second host ADB timeout; it returned 5/5. Neither trial
demonstrates infinite device reliability, and a host timeout cannot be
automatically classified as an Android failure.

## Raw evidence and reproducible scripts

- `../m4/20261013-action-id-v1-smoke.json`
- `../m4/20261013-reliable-v1-settings-regression.json` (initial failure)
- `../m4/20261013-reliable-v1-settings-regression-retry.json` (5/5 success)
- `../../scripts/m4-action-id-v1-smoke.py`
- `../../scripts/m4-v1-settings-regression.py`
- `../../scripts/m4-local-text-offline.py` (host timeout made more tolerant)

No private screen tree, messages, raw entered text or signing material is
persisted by the on-device task status or these aggregate benchmark results.

## Acceptance

**GO (limited):** compiled, installed and verified for bounded action-ID
registration plus repeated low-risk commands and the Settings/Connections
workflow. **HOLD:** active-operation cancellation, intentional duplicate-ID
replay on the device, scroll/list selectors, 100-operation multi-step
reliability, physically disconnected USB operation, power-management
survivability, reboot restoration. The cancellation control was confirmed
in the UI; functional cancellation during a running action was not exercised.

Scroll was excluded after the relevant Project IR edit request was blocked.
No attempt was made to route around the safety check.

No reboot was performed. The previously verified v0 offline gate is not a
new strict-offline proof for this v1 APK.
