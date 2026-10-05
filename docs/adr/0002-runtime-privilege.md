# ADR 0002: ADB is not a runtime dependency

Status: Accepted

## Context

The assistant should work locally without keeping Wi-Fi or a PC attached.

The target S26 Ultra has already demonstrated that Shizuku can be bootstrapped once and remains active until reboot.

## Decision

Runtime privilege preference:
1. public Android APIs;
2. Accessibility;
3. Shizuku;
4. ADB for bootstrap/lab only.

USB ADB remains a first-class development/control-plane transport.

## Consequences

- normal assistant operation survives USB disconnect;
- M0.5 can still use USB for deterministic external testing;
- reboot requires a Shizuku bootstrap procedure unless a later mechanism improves this.
