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
- supports bounded `tap_text` by resolving coordinates from the current tree;
- includes a read-latency benchmark.

Examples:

```powershell
python host\phone_controller.py doctor
python host\phone_controller.py state
python host\phone_controller.py benchmark -n 20
python host\phone_controller.py tap-text Connections
```

This is a **lab adapter**. Stock Mobilerun is not accepted as the production phone service because its manifest and feature surface are broader than this project needs.

The production direction is a narrow on-device service that keeps the fast Accessibility core while removing unrelated SMS/contact/storage/install/cloud/streaming features.
