# Remote access to the phone

Two independent systems can control the S26 remotely. They share the phone and
Shizuku, but no code, credential or transport.

| | ChatGPT plugin "M's S26 Ultra" | Project IR Mobile Agent (this repository) |
|---|---|---|
| On the phone | [DroidBridge](https://github.com/zephyr7030/DroidBridge), third-party, Apache-2.0 | `io.projectir.mobile` |
| Transport | OpenAI Secure MCP Tunnel: the phone polls `api.openai.com` | outbound WebSocket to the Project IR Hub |
| Who can use it | ChatGPT on the web, in developer mode | every Hub client: Claude, ChatGPT through the Project IR app, `/rpc` |
| Authentication | tunnel ID and OpenAI runtime key on the phone; the ChatGPT app itself shows "None" | per-device token, generated on the phone |
| Tools | screen, apps, files, shell, network, on-phone automations (typed, with safety annotations) | shell as UID 2000, plus the semantic UI provider through that shell |
| Purpose | general phone control from ChatGPT | offline Persian assistant; remote shell is secondary |

"Authentication: None" in ChatGPT does not mean a public endpoint: the app has no
URL; access is through the owner's OpenAI tunnel and the runtime key on the phone.

## DroidBridge settings to keep

- Use 0.5.9 or later. Earlier versions publish a `visual` schema that ChatGPT can
  refuse for coordinate taps ("Value matches multiple schemas"). After updating,
  press **Refresh tools** on the ChatGPT app's settings page and open a new chat.
- Local MCP (`127.0.0.1:8765`) stays off unless an agent on this PC uses it
  through `adb forward`.
- DroidBridge and the assistant both inject input. Do not run both at once on the
  same screen; DroidBridge's input lease does not know about this app.

## Enroll the Project IR agent

Since 2026-10-14 the Hub accepts the shared agent token for device `ops` only.
Every other device has its own credential. The S26 was provisioned with the
shared token, so each connection was refused with HTTP 401.

The phone now generates its own token and registers it with a one-time code:

```text
owner  --admin token-->  Hub: create device, issue one-time code (default 15 minutes)
phone  --code + new token-->  Hub: POST /agent/enroll
phone  --new token-->  Hub: WebSocket /agent/connect
```

If the reply to `/agent/enroll` is lost, the phone asks `/agent/credential/verify`
whether its token is already registered before treating the code as refused.

With the phone on USB:

```powershell
$code = .\scripts\New-S26EnrollmentCode.ps1
.\scripts\bootstrap-android-agent.ps1 -ApkPath .\android\mobile-agent\app\build\outputs\apk\debug\app-debug.apk -EnrollmentCode $code
```

The code can also be pasted into **Hub credential** in the app. To update the APK
and keep the credential, use `-SkipEnrollment`.

## Agent states

| State | Meaning | Action |
|---|---|---|
| `connected` | Hub connection is up | none |
| `enrolling` | the code is being registered | none |
| `enroll-retry:HTTP…` | Hub busy, blocked or not configured | none, retried slowly |
| `enroll-refused:HTTP…` | code expired, used or wrong | issue a new code |
| `not-enrolled` | no usable credential on the phone | enroll |
| `network-error:HTTP401:…` | token revoked or rotated; retried after 1 minute, then up to 30 | enroll again |
| `network-error:…` | no network or Hub unreachable | none, retried within 30 seconds |
