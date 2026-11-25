param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,

    [string]$DeviceId = "s26",

    [string]$HubUrl = "https://project-ir-hub.mmsotude.workers.dev",

    # One-time code for this device (scripts/New-S26EnrollmentCode.ps1). Prompted
    # for when omitted. Use -SkipEnrollment to update the APK only.
    [securestring]$EnrollmentCode,

    [switch]$SkipEnrollment
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $ApkPath)) {
    throw "APK not found: $ApkPath"
}
if ($DeviceId -cnotmatch '^[a-z][a-z0-9-]{2,63}$') {
    throw "Invalid device ID: $DeviceId"
}
if ($HubUrl -notmatch '^https://') {
    throw "Hub URL must use https"
}
$code = $null
if (-not $SkipEnrollment) {
    if (-not $EnrollmentCode) { $EnrollmentCode = Read-Host "Enrollment code for '$DeviceId'" -AsSecureString }
    $code = [System.Net.NetworkCredential]::new('', $EnrollmentCode).Password.Trim()
    if ($code -cnotmatch "^pir1\.$DeviceId\.[a-f0-9]{64}\.[a-f0-9]{64}$") {
        throw "Not an enrollment code for device '$DeviceId'"
    }
}

$adb = (Get-Command adb -ErrorAction Stop).Source

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

# Without this exemption Android cuts the connection while the phone sleeps.
& $adb shell cmd deviceidle whitelist +io.projectir.mobile | Out-Null

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

if ($SkipEnrollment) {
    & $adb shell am start -W -n io.projectir.mobile/.MainActivity | Out-Null
    Write-Output "Project IR Mobile Agent updated; the stored credential was kept."
    return
}

$remoteDir = "/sdcard/Android/data/io.projectir.mobile/files"
$remoteBootstrap = "$remoteDir/bootstrap.json"
$temp = Join-Path $env:TEMP ("project-ir-android-bootstrap-" + [guid]::NewGuid().ToString("N") + ".json")

try {
    $payload = [ordered]@{
        hub_url = $HubUrl
        device_id = $DeviceId
        enrollment_code = $code
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

# The file holds a one-time code only; the phone generates its own token.
# MainActivity imports the bootstrap into Android Keystore-backed encrypted storage,
# deletes the phone-side plaintext file, requests Shizuku authorization, and starts
# the outbound agent when configuration is ready.
& $adb shell am start -W -n io.projectir.mobile/.MainActivity | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Failed to launch Project IR Mobile Agent"
}

Write-Output "Project IR Mobile Agent installed and bootstrap imported for device '$DeviceId'."
Write-Output "If Shizuku authorization is not already granted, approve the one-time Shizuku prompt on the phone."
