# Phone Control Plane

## Purpose

Allow an external agent to independently inspect, control, benchmark and recover the S26 Ultra, similar to Remote Desktop Commander on a PC.

This is separate from the local assistant runtime.

## Desired tool surface

Read:
- device state;
- current package/activity;
- Accessibility tree;
- screenshot;
- packages;
- selected system state;
- benchmark/log files.

Act:
- launch/stop app;
- click structured node;
- tap coordinates as fallback;
- set text;
- press key;
- swipe/scroll;
- global back/home/recents;
- run approved diagnostic commands;
- start/stop benchmark jobs.

## Transport options

### A. USB ADB — first lab transport

```text
ChatGPT
  → Remote Desktop Commander
  → Ops
  → USB ADB
  → S26 Ultra
```

Advantages:
- available without Wi-Fi;
- transparent debugging;
- low complexity;
- ideal for initial independent testing.

Disadvantage:
- phone must be physically attached to Ops.

### B. Local Android control server

Mobilerun Portal-style HTTP/WebSocket or our own Binder/socket bridge.

Use when the external controller is on the same host/network.

### C. Outbound reverse WSS

```text
S26 → outbound WSS → relay/control service → MCP → agent
```

Useful for remote independent tests over Wi-Fi or cellular without inbound reachability.

Must be authenticated, encrypted, revocable, and disabled by default until security review.

## Candidate implementations

### Mobilerun Portal
Strong candidate actuator:
- Accessibility tree;
- state;
- screenshot;
- local HTTP/WS;
- ADB ContentProvider;
- reverse transport concepts.

### Android Use
Strong reference for:
- MCP-facing Android tools;
- ADB backend;
- companion-app backend.

### A11yPilot
Useful reference for:
- compact screen serialization;
- settle detection;
- native Accessibility executor.

## Unified adapter

The rest of the project talks only to:

```text
PhoneController
  getState()
  getTree()
  screenshot()
  launchApp()
  click(node)
  setText(node,text)
  tap(x,y)
  swipe(...)
  global(action)
  runDiagnostic(approvedCommand)
```

Transport-specific details stay behind adapters.

## M0.5 acceptance

- USB path works with Wi-Fi disabled.
- Controller can detect reconnect without reinstall/reconfigure.
- Read operations are reliable.
- Actions are verifiable.
- No command allows arbitrary destructive shell execution by default.
- Tool latency is measured separately from device/UI latency.
