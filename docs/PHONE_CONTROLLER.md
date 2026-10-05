# PhoneController host adapter

`host/phone_controller.py` is the first reusable M0.5 control-plane component.

It is intentionally not an agent. It only normalizes the proven lab path:

```text
Python PhoneController
  -> USB ADB bootstrap
  -> temporary adb forward
  -> Mobilerun local HTTP
  -> direct Accessibility state/actions
```

Properties:

- standard-library Python only;
- auto-discovers ADB on Ops/X-pc-style Windows installs;
- requires exactly one authorized ADB device;
- obtains Mobilerun's documented local token at runtime;
- never prints or persists the token;
- turns the Mobilerun HTTP server off on exit;
- removes the ADB forward on exit;
- exposes state/tree/tap/global actions;
- exposes swipe, UTF-8 text input, verified app launch, and ADB PNG screenshot fallback;
- supports bounded `tap_text` by resolving coordinates from the current tree;
- retries bounded local-server startup races and transient GET/read timeouts;
- cleans stale ADB forwards before opening a new lab session;
- emits UTF-8 CLI output on Windows;
- includes a read-latency benchmark.

Examples:

```powershell
python host\phone_controller.py doctor
python host\phone_controller.py state
python host\phone_controller.py benchmark -n 20
python host\phone_controller.py tap-text Connections
```

This is a **lab adapter**. Stock Mobilerun is not accepted as the production phone service because its manifest and feature surface are broader than this project needs.

Text is transported as UTF-8/Base64. A live integration test successfully wrote and re-read `سلام test 123` through Accessibility without switching the active IME. Search-entry workflow reliability is still under test and is tracked separately from the input actuator.

Session bootstrap is intentionally separate from runtime-loop latency: ContentProvider setup/cleanup costs seconds, while the active HTTP Accessibility loop is typically tens of milliseconds. A long-running controller should therefore keep the session open rather than reopen it per agent turn.

The production direction is a narrow on-device service that keeps the fast Accessibility core while removing unrelated SMS/contact/storage/install/cloud/streaming features.
