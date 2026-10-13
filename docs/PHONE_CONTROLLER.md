# Historical PhoneController Adapter

The Python `host/phone_controller.py` adapter was a **Mobilerun lab adapter**, not the final architecture.

It was useful because it established several feasibility facts quickly:
- Accessibility tree reads can be tens of milliseconds;
- semantic/tap actions can be tens of milliseconds;
- Persian UTF-8 text input is viable;
- screenshots are much slower and should be fallback-only;
- ADB transport itself is not the dominant latency source.

However, repeated Mobilerun workflows failed the project's reliability gate. The adapter and its runtime test scripts were therefore removed from the current tree after the native Project IR Mobile Agent bridge passed replacement parity.

Historical raw measurements remain under `benchmarks/m05/`, and the complete reasoning is documented in `HISTORY.md`.

Current implementation lives in the Project IR repository under:

`android/mobile-agent/`

Canonical path:

`Project IR Hub -> s26 -> Shizuku -> native Accessibility bridge`.
