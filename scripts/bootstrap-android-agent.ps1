param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,

    [string]$DeviceId = "s26",

    [string]$AgentConfigPath = (Join-Path $env:LOCALAPPDATA "ProjectIR\config\agent.json")
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $ApkPath)) {
    throw "APK not found: $ApkPath"
}
if (-not (Test-Path $AgentConfigPath)) {
    throw "Project IR agent config not found: $AgentConfigPath"
}

$adb = (Get-Command adb -ErrorAction Stop).Source
$config = Get-Content -Raw $AgentConfigPath | ConvertFrom-Json
if ([string]::IsNullOrWhiteSpace($config.hub_url) -or [string]::IsNullOrWhiteSpace($config.token)) {
    throw "Agent config must contain hub_url and token"
}

$state = (& $adb get-state 2>$null | Out-String).Trim()
if ($state -ne "device") {
    throw "ADB device is not ready"
}

& $adb install -r $ApkPath | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "APK installation failed"
}

# Notification permission is independent of Shizuku authorization.
& $adb shell pm grant io.projectir.mobile android.permission.POST_NOTIFICATIONS 2>$null | Out-Null

# Enable the Project IR semantic Accessibility bridge while preserving every
# previously enabled Accessibility service.
$accessibilityService = "io.projectir.mobile/io.projectir.mobile.UiAccessibilityService"
$enabled = (& $adb shell settings get secure enabled_accessibility_services 2>$null | Out-String).Trim()
if ($enabled -eq "null") {
    $enabled = ""
}
$parts = @($enabled -split ":" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
if ($parts -notcontains $accessibilityService) {
    $parts += $accessibilityService
}
& $adb shell settings put secure enabled_accessibility_services ($parts -join ":") | Out-Null
& $adb shell settings put secure accessibility_enabled 1 | Out-Null

# Start once so Android creates app-scoped external storage.
& $adb shell am start -W -n io.projectir.mobile/.MainActivity | Out-Null
Start-Sleep -Milliseconds 800
& $adb shell am force-stop io.projectir.mobile | Out-Null

$remoteDir = "/sdcard/Android/data/io.projectir.mobile/files"
$remoteBootstrap = "$remoteDir/bootstrap.json"
$temp = Join-Path $env:TEMP ("project-ir-android-bootstrap-" + [guid]::NewGuid().ToString("N") + ".json")

try {
    $payload = [ordered]@{
        hub_url = [string]$config.hub_url
        device_id = $DeviceId
        token = [string]$config.token
        autostart = $true
    } | ConvertTo-Json -Compress

    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($temp, $payload, $utf8NoBom)

    & $adb shell mkdir -p $remoteDir | Out-Null
    & $adb push $temp $remoteBootstrap | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to stage Android bootstrap"
    }
}
finally {
    Remove-Item $temp -Force -ErrorAction SilentlyContinue
}

# MainActivity imports the bootstrap into Android Keystore-backed encrypted storage,
# deletes the phone-side plaintext file, requests Shizuku authorization, and starts
# the outbound agent when configuration is ready.
& $adb shell am start -W -n io.projectir.mobile/.MainActivity | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Failed to launch Project IR Mobile Agent"
}

Write-Output "Project IR Mobile Agent installed and bootstrap imported for device '$DeviceId'."
Write-Output "If Shizuku authorization is not already granted, approve the one-time Shizuku prompt on the phone."
