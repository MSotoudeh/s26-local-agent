# Native Project IR control-plane decision — 2026-10-07

## Decision

Replace Mobilerun Portal as the production control plane with the native semantic UI bridge inside Project IR Mobile Agent.

## Evidence

### Previous Mobilerun path

Mobilerun proved that Accessibility primitives can be low-latency, but repeated workflows did not satisfy reliability requirements.

Key evidence retained under `benchmarks/m05/`:
- fast HTTP/tree/action microbenchmarks;
- 30-cycle selector/settle baseline: 18/30 = 60%;
- event-sequence variants that degraded rather than improved reliability;
- screenshot latency/reliability failures.

### Native Project IR path

Execution path:

```text
Project IR Hub -> s26 -> Shizuku shell -> Project IR Accessibility bridge
```

Samsung Settings repeated semantic test:
- 4 independent batches;
- 5 iterations per batch;
- 20 total;
- 20 PASS;
- 0 phone-workflow failures.

Each iteration:
1. click exact semantic label `Connections`;
2. verify `Wi-Fi` and `Bluetooth`;
3. global Back;
4. verify `Connections` and `Connected devices`.

Persian text-input parity:
- focused Samsung Settings search;
- wrote `سلام test 123`;
- exact string re-read from Accessibility tree;
- PASS.

Structured Settings root after serializer correction:
- nodeCount = 119;
- no heuristic deduplication;
- hard traversal cap = 500.

Android build quality:
- `assembleDebug`: PASS;
- `lintDebug`: PASS after fixing four pre-existing lint errors.

## Cleanup

Removed from phone:
- `com.mobilerun.portal`;
- `com.mobilerun.portal.lab`.

Removed from Ops:
- Mobilerun source/build temp tree;
- Android Platform 34 installed solely for Mobilerun;
- temporary JDK download/staging artifacts.

Retained:
- Shizuku;
- JDK17, moved to the Project IR toolchain directory because the replacement Android agent requires it;
- raw benchmark evidence demonstrating why Mobilerun was rejected.

## Classification

Mobilerun: **KILL/REPLACE — completed**.

Project IR Mobile Agent semantic bridge: **GO — continue to M1/M2 hardening**.
