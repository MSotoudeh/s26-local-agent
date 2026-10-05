# M0.5 â€” Persistent Accessibility transport benchmark

Date: 2026-10-04
Device: Samsung SM-S948B / Android 17 / One UI 9 generation

## Question

Is direct Android Accessibility fast enough for a low-latency agent loop, or will screen-state acquisition dominate interaction time?

## Baselines already known

Raw USB ADB itself is fast:

- shell no-op p50: 63.3 ms
- HOME command p50: 70.8 ms
- warm-biased Settings launch p50: 70.1 ms

But UIAutomator tree extraction is not usable as the primary loop:

- p50: 2074.0 ms
- p95: 2084.9 ms

Mobilerun through the ADB ContentProvider initially appeared to take ~1.13 s per tree. A control experiment then showed that even the ContentProvider `ping` endpoint took ~1.10 s. The delay therefore belonged to repeatedly launching Android's `content` CLI, not to Accessibility traversal.

## Persistent HTTP result

Mobilerun's local HTTP server was enabled temporarily, reached only through `adb forward`, and driven with one persistent HTTP client. Reverse/cloud control was never enabled.

| Endpoint | n | failures | p50 | p95 |
| --- | ---: | ---: | ---: | ---: |
| ping | 40 | 0 | 3.80 ms | 5.95 ms |
| phone_state | 40 | 0 | 3.88 ms | 4.30 ms |
| a11y_tree | 40 | 0 | 4.88 ms | 5.71 ms |
| state | 40 | 0 | 5.92 ms | 7.36 ms |

The median serialized Accessibility tree was ~5.7 KB and combined state ~5.9 KB on the tested Settings surface.

### Decision

**GO for direct Accessibility as the primary perception primitive.**

The core read path is comfortably below our 50 ms target and leaves substantial latency budget for routing and action selection.

**KILL `adb shell content` as a per-turn transport.** It remains acceptable for one-time setup/configuration only.

## Action-loop observation

A verified Settings navigation was also executed through the persistent HTTP transport:

1. read state;
2. resolve the visible `Connections` target;
3. dispatch tap;
4. poll state;
5. verify both `Wi-Fi` and `Bluetooth` appeared.

Warm observations:

- state read: ~10 ms in the valid steady-state sample
- tap request: ~6.8 ms
- tap â†’ verified Connections state: ~86 ms
- Back request: ~6.8 ms
- Back â†’ verified main Settings state: ~141 ms

The first call after server startup was much slower (~594 ms state, ~389 ms tap), so **cold start and steady-state must remain separate metrics**.

The multi-cycle reliability experiment was intentionally not accepted as a reliability result because the UI layout/scroll state changed after Back and invalidated the naive selector. That is a selector/workflow problem, not an actuator latency failure.

## Security/implementation finding

Two existing projects were evaluated:

### Android Use

Pros:
- minimal companion APK;
- direct AccessibilityNodeInfo tree;
- time-boxed control;
- conservative allowlist;
- narrower manifest.

Cons for this experiment:
- bridge intentionally requires a private pairing token and interactive grant;
- ADB backend itself uses slow UIAutomator.

It was installed for inspection, then removed after the experiment.

### Mobilerun Portal

Pros:
- excellent direct Accessibility latency;
- tree/state/action/screenshot surfaces;
- multiple transports;
- easy ADB bootstrap.

Cons:
- stock manifest is much broader than our production needs (SMS/contact/storage/install/streaming related declarations);
- therefore it is **lab actuator only**, not a production dependency.

Runtime permission audit confirmed SMS and Contacts were not granted. No reverse/cloud transport was enabled.

After benchmarking:
- local HTTP and WebSocket servers were disabled;
- all ADB forwards were removed;
- Android Use was uninstalled;
- Mobilerun remains installed with Accessibility enabled for continued lab work;
- Bitwarden and KDE Connect Accessibility services remained enabled.

## Architectural consequence

Production direction should be one of:

1. a stripped Mobilerun fork containing only the fast Accessibility/state/action core; or
2. our own minimal Accessibility bridge borrowing the proven traversal/action patterns.

The production bridge should:
- have a narrow manifest;
- bind local transport only as required;
- keep network control disabled by default;
- expose structured nodes and stable action IDs;
- support verification and event-driven settle detection;
- avoid full tree rebuilds when event-driven cached state is sufficient.

## Next gate

Build a minimal `PhoneController` host adapter over the proven persistent transport, then benchmark:

- stable target/action IDs;
- event-driven settle rather than fixed sleeps;
- text entry including Persian;
- scroll;
- app launch;
- screenshot latency;
- reconnect/service recovery.

The next reliability test must separate selector correctness from actuator transport latency.
