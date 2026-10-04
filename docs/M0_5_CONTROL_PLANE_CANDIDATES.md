# M0.5 Control Plane Candidates

Status: prepared while X-pc RDC transport was temporarily unresponsive.

## First baseline: raw USB ADB

Before installing any agent APK, establish the raw control baseline:

- current focused app/activity;
- UIAutomator XML dump latency;
- screenshot latency;
- key/global action latency;
- app launch latency;
- reconnect behavior;
- Wi-Fi disabled.

This gives us a neutral reference for every later control plane.

## Candidate A — Mobilerun Portal

Repository: https://github.com/droidrun/mobilerun-portal

Latest release observed on 2026-10-04:
- tag: v0.7.25
- APK: com.mobilerun.portal-0.7.25.apk
- size: 53,610,015 bytes
- SHA-256: 6dc9e8327fcaecf3ba0ad5054f19357d832fc923763fb6c9d1ab09f79e3d3297

Why test it:
- Accessibility tree/state;
- local HTTP/WebSocket;
- ADB ContentProvider;
- screenshot and action primitives;
- reverse connection design.

Primary role: likely actuator/control bridge, not assistant brain.

## Candidate B — Android Use

Repository: https://github.com/bsaisuryacharan/android-use

Latest release observed on 2026-10-04:
- tag: v0.1.0
- APK: app-debug.apk
- size: 2,480,412 bytes
- SHA-256: 89a22c689387d748a619920f7a3b196375c2203bd30693853fbef9dc5cedd2ec

Why test it:
- already designed as Android computer-use/MCP;
- very small companion APK;
- useful reference for direct agent ergonomics.

Caution:
- first public release;
- debug-signed;
- must be treated as an experiment, not a trusted production dependency.

## Selection rule

Do not install both immediately.

1. Measure raw ADB first.
2. Install Mobilerun Portal first because its control surface is broader and better aligned with our planned PhoneController abstraction.
3. Add Android Use only if it offers a meaningful latency, reliability, MCP ergonomics, or remote-transport advantage.

## M0.5 GO criteria

A candidate advances only if:
- state/tree/screenshot retrieval is stable;
- input actions are verifiable;
- p95 overhead is low enough not to dominate UI latency;
- USB path needs no Wi-Fi;
- reconnect is predictable;
- permission model is acceptable;
- no unexplained background/cloud dependency exists.
