# Remote access to the phone

Two independent systems control the S26 remotely. They share the phone and Shizuku,
but no code, credential or transport. Neither needs the PC.

| | ChatGPT plugin "M's S26 Ultra" | Project IR Mobile Agent (this repository) |
|---|---|---|
| On the phone | [DroidBridge](https://github.com/zephyr7030/DroidBridge), third-party, Apache-2.0 | `io.projectir.mobile` |
| Transport | OpenAI Secure MCP Tunnel: the phone polls `api.openai.com` | outbound WebSocket to the Project IR Hub |
| Who can use it | ChatGPT on the web, in developer mode | any Hub client with `remote_exec` and `device_id: s26` (Claude, ChatGPT) |
| Authentication | tunnel ID and OpenAI runtime key on the phone; the ChatGPT app itself shows "None" | per-device token, generated on the phone, bound to its Hub |
| Tools | screen, apps, files, shell, network, on-phone automations | shell as UID 2000, plus the semantic UI provider through that shell |

"Authentication: None" in ChatGPT does not mean a public endpoint: the app has no
URL; access is through the owner's OpenAI tunnel and the runtime key on the phone.

## Why both, and nothing else

- DroidBridge's tunnel works for OpenAI clients only. It is the richer tool set and
  needs no maintenance here, so it stays ChatGPT's path.
- Claude cannot use that tunnel. The Hub agent is the only path to Claude that works
  without the PC or an app open on the phone.
- Claude Code on the phone (AndCode) with DroidBridge's local MCP is useful when
  working on the phone itself. Its Remote Control session ends with that app, so it is
  not a remote path to rely on. Local MCP listens on loopback with a bearer token; turn
  it on only for such use.
- ADB from the PC needs the PC and either USB or the same network.

DroidBridge and the assistant in this app both inject input. Do not run both at once
on the same screen; DroidBridge's input lease does not know about this app.

## Enroll the Project IR agent

Since 2026-10-14 the Hub accepts the shared agent token for device `ops` only. Every
other device has its own credential:

```text
owner  --admin token-->  Hub: create device, issue one-time code (default 15 minutes)
phone  --code + new token-->  Hub: POST /agent/enroll
phone  --new token-->  Hub: WebSocket /agent/connect
```

If the reply to `/agent/enroll` is lost, the phone asks `/agent/credential/verify`
whether its token is already registered before treating the code as refused. Changing
the Hub URL deletes the stored token: it is never presented to another host.

Without a cable: copy a code, send it to the phone over a private channel, paste it
into **Hub credential** in the app and press **Save**, then **Start**.

```powershell
.\scripts\New-S26EnrollmentCode.ps1 -CopyToClipboard
```

With the phone on USB (also installs the APK; `-SkipEnrollment` keeps the credential):

```powershell
.\scripts\build-android-agent.ps1 -Release
$code = .\scripts\New-S26EnrollmentCode.ps1
.\scripts\bootstrap-android-agent.ps1 -ApkPath .\android\mobile-agent\app\build\outputs\apk\release\app-release.apk -EnrollmentCode $code
```

Install the **release** variant: it has the same signing key as the installed app and
is not debuggable.

## Agent states

| State | Meaning | Action |
|---|---|---|
| `connected` | Hub connection is up | none |
| `enrolling` | the code is being registered | none |
| `enroll-retry:HTTP…` | Hub busy, blocked or not configured | none, retried slowly |
| `enroll-refused:HTTP…` | code expired, used or wrong | issue a new code |
| `not-enrolled` | no usable credential on the phone | enroll |
| `network-error:HTTP401:…` | token revoked or rotated; retried after 1 minute, then up to 30 | enroll again |
| `network-error:…`, `network-changed` | no network, or moving to another one | none, retried at once when a network appears |

## Resilience

Measured on the S26 (Android 17, mobile data, 2026-11-25) against an isolated copy of
the Hub, with the code of this repository:

| Event | Result |
|---|---|
| enrollment, connection | 0.3 s |
| command round trip | median 199 ms |
| connection dropped | reconnected in 2.4–2.8 s |
| connection dropped while a command runs, or just before it ends | result delivered after reconnecting |
| mobile data off for 25 s during a command | command ran offline; result delivered about 1 s after data returned |
| shell UserService killed | one command failed, next one worked 0.4 s later |
| app crashed (during deep Doze) | restarted and reachable after 3.3 s |
| APK updated through the agent itself | reachable after about 4 s, no UI opened |
| deep Doze, screen off, 8 minutes without traffic | always connected; next command 408 ms |
| command times out | its whole process group is killed |

No battery exemption is needed: a foreground service keeps its network in Doze.

Not covered: a night of real use, Wi-Fi to mobile handover (the same code path as the
mobile data test, but not run), and reboot. After a reboot Shizuku must be started by
hand before any shell command works, on both paths.

## Rollback

`pm install -r -d <older release APK>` through either path. The installed app keeps its
data; a build older than 0.2.0 cannot enroll.
