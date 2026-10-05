# M0.5 Project IR follow-up — 2026-10-05

## Scope

All host/device operations in this follow-up were executed through **Project IR** on device `ops`. No RDC or SentinelX path was used.

Target device: Samsung SM-S948B, Android 17 / API 37.

## What this follow-up verified

The previously committed M0.5 direction remains valid:

- USB ADB is a viable lab/bootstrap transport.
- UIAutomator is too slow for the primary perception loop.
- Mobilerun's direct Accessibility HTTP path is viable.
- The reusable `host/phone_controller.py` adapter works against the real phone.

## Fresh measurements

### Current PhoneController read path

`python host\phone_controller.py benchmark -n 30`

| Primitive | p50 | p95 |
| --- | ---: | ---: |
| ping | 16.81 ms | 31.42 ms |
| phone_state | 21.00 ms | 32.63 ms |
| a11y_tree | 29.45 ms | 33.94 ms |
| state | 30.07 ms | 39.99 ms |

One `state` sample reached 440.17 ms, so tail behavior still needs explicit tracking.

These results are slower than the best 2026-10-04 steady-state sample, but remain below the current interactive perception gate.

### Action primitive

A no-op-style Accessibility tap at coordinate (2,2), 30 iterations:

- failures: 0
- p50: 26.12 ms
- p95: 29.65 ms
- max: 34.71 ms

This separates the gesture transport from heavier app-launch behavior.

### Real verified workflow

One session:

1. open Android Settings;
2. read state;
3. resolve the visible `Connections` node from the Accessibility tree;
4. tap its center;
5. poll state;
6. verify `Wi-Fi` and `Bluetooth` are visible;
7. issue Accessibility Back;
8. verify return to the Settings root.

Measured:

- initial state: 33.92 ms
- tap request: 27.53 ms
- tap -> verified Connections state: 32.39 ms
- verification polls: 1
- Back -> verified root state: 86.66 ms
- workflow verification: PASS

### Screenshot path

Repeated screenshot capture is not suitable for the primary loop.

Mobilerun HTTP screenshot, 15 iterations:

- failures/timeouts: 5
- p50: 734.06 ms
- p95: ~3015 ms

Raw ADB binary screenshot transfer, 20 iterations:

- failures: 0
- p50: 1012.10 ms
- p95: 1020.88 ms

Conclusion: screenshot remains a fallback for inaccessible/visual-only surfaces, not a per-turn perception primitive.

### App-launch tail

Repeatedly foregrounding an already-running app through the Portal action path showed a large tail:

- action p50: 32.14 ms
- action p95: 1533.97 ms
- verified end-to-end p50: 66.72 ms
- verified end-to-end p95: 4543.53 ms
- failures: 2/20

A separate tap benchmark stayed below 30 ms p95, so this tail must not be attributed to the base action transport. App launch/foreground actions require their own settle/retry policy.

## Security / hygiene

- Mobilerun auth token was used only inside benchmark processes and was not printed or persisted.
- Local HTTP was disabled after each test.
- ADB forwards were removed after each test.
- M0 battery capture was hardened to store only a small non-identifying power/health summary rather than the full `dumpsys battery` output.

## Primitive closure follow-up

Additional live tests on the final adapter path produced:

- verified app launch to Settings: 118.08 ms;
- verified swipe request: 17.60 ms, with a changed structured state and six newly visible text elements;
- raw ADB PNG screenshot fallback: valid 318,932-byte PNG in 1451.82 ms;
- verified Persian/ASCII text input through Accessibility: `سلام test 123` in 40.23 ms, with the exact text observed back in the Accessibility tree;
- no IME switch was required for that successful text-input run;
- one Settings search transition required two Back actions to normalize from Samsung Settings Suggestions before the text test could proceed.

Repeated Settings-search entry was not fully reliable across reruns. This is classified as a selector/settle workflow problem rather than a UTF-8 or input-actuator failure.

A later stress/repeatability run after many rapid sessions also showed degraded read behavior: one structured state read took ~1533 ms and a Connections navigation failed to verify within a 2 s window, even though the tap request itself remained ~27.8 ms. This reinforces the separation between fast actuator transport and the still-unproven long-run reliability of the stock Portal read/settle path.

Controller hardening added during the follow-up:

- UTF-8 stdout on Windows;
- bounded startup retry for the local HTTP server;
- stale ADB-forward cleanup;
- bounded retry for transient GET/read timeouts;
- package-based launch verification;
- `swipe`, `input_text`, `launch_app_verified`, and ADB PNG screenshot primitives.

### Reconnect result

Six complete open/read/close sessions succeeded, including an ADB server restart between session 3 and session 4.

- before restart: 3/3 successful, ~3.26–4.24 s full bootstrap/session time;
- first session after `adb kill-server`: successful, 8.36 s cold recovery;
- next two sessions: successful, ~3.24 s each.

This validates software transport recovery. A literal physical USB unplug/replug was not executed in this run.

## Decision

- **M0: COMPLETE / GO.**
- **M0.5 feasibility: GO.**
- **M0.5 full acceptance: HOLD** until physical USB reconnect is exercised and selector/settle reliability is demonstrated repeatedly rather than by isolated successful workflows.

Primary control-plane primitive:

```text
Project IR
  -> ops
  -> PhoneController
  -> USB ADB bootstrap/forward
  -> local Mobilerun Accessibility HTTP
  -> structured state/actions
```

The next engineering focus should be:

1. stable resource/action selectors with ambiguity rejection;
2. event-driven settle detection;
3. app-launch-specific recovery;
4. repeated text-input reliability, including Persian;
5. scroll/dynamic-list reliability;
6. physical disconnect/reconnect validation;
7. minimal production Accessibility bridge with a narrower permission surface.
