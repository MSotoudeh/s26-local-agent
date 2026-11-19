# Phone Control Plane

## Canonical architecture

The production control plane is now Project IR-native.

```text
ChatGPT
  -> Project IR
  -> Project IR Hub
  -> S26 Mobile Agent (outbound connection)
       -> Shizuku UserService / shell UID 2000
       -> native AccessibilityService
       -> restricted ContentProvider semantic bridge
```

Normal remote operation does **not** require the phone to be connected to Ops or Wi-Fi after the mobile agent and Shizuku are already running. The agent initiates the Hub connection outbound.

## Roles

### Project IR Mobile Agent

Package: `io.projectir.mobile`.

Responsibilities:
- authenticated outbound Hub connection;
- durable Android device identity (`s26`);
- Shizuku-backed shell execution;
- foreground/autostart lifecycle;
- semantic UI state and actions through the native Accessibility bridge.

### Shizuku

Canonical privileged local execution layer.

Observed execution identity:
- shell UID 2000.

Shizuku remains a runtime dependency. A reboot still requires whatever bootstrap procedure Shizuku itself needs on the device.

### Native semantic UI bridge

The Project IR app contains an AccessibilityService plus a deliberately narrow ContentProvider API.

Read primitives:
- status;
- current structured Accessibility tree.

Action primitives:
- click exact visible text;
- click a resource ID;
- set text on the focused editable node;
- Android global Accessibility actions.

The provider accepts only:
- the app's own UID;
- shell UID 2000.

There is no extra HTTP listener or second WebSocket service for UI automation.

## Lab/bootstrap transport

USB ADB remains useful for:
- APK installation/update;
- Shizuku bootstrap/recovery;
- diagnostics when the native path is unavailable;
- development profiling.

It is not part of the normal agent execution path.

## Retired Mobilerun path

Mobilerun Portal was used as a feasibility reference because it rapidly demonstrated low-latency Accessibility actions. It was later rejected as a production dependency after repeated state/settle reliability tests.

Retired packages:
- `com.mobilerun.portal`;
- `com.mobilerun.portal.lab`.

Both were removed from the S26 after the Project IR bridge achieved 20/20 repeated semantic workflow success and Persian text-input parity.

Raw evidence is retained in `benchmarks/m05/`; runtime Mobilerun adapter code has been removed from the current tree.

## Acceptance evidence

Direct path:
`Project IR -> s26 -> native bridge`.

Samsung Settings workflow:
- 20/20 successful cycles;
- semantic Connections click;
- Wi-Fi/Bluetooth postcondition verification;
- semantic/global Back;
- root-page verification.

Persian text:
- `سلام test 123`;
- exact write/read verification;
- PASS.

## Next control-plane gates

The control plane is sufficiently proven to stop investing in alternative actuator transports.

Remaining hardening:
1. dedicated no-Wi-Fi/cellular/offline runtime matrix;
2. Shizuku restart/reboot recovery;
3. larger app/task corpus;
4. stable bounded node/action identifiers;
5. scroll/list/input reliability across multiple apps;
6. screenshot only for surfaces unavailable to Accessibility.
