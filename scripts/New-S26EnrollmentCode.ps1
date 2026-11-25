<#
.SYNOPSIS
Creates a one-time Hub enrollment code for the phone and returns it as a SecureString.

.DESCRIPTION
Run by the owner. The Hub's device admin token is asked for at a hidden prompt; it is
not stored, shown or passed on a command line. Nothing is sent to the phone.

.PARAMETER CopyToClipboard
For a phone that is not connected to this computer: copies the code instead of returning
it. Send it to yourself over a private channel and paste it into "Hub credential" in the
app. It works once and expires; if the phone reports "enroll-refused", someone else used
it or it expired: issue a new one, which replaces the old credential.

.EXAMPLE
$code = .\scripts\New-S26EnrollmentCode.ps1
.\scripts\bootstrap-android-agent.ps1 -ApkPath <apk> -EnrollmentCode $code
#>
[CmdletBinding()]
param(
    [ValidatePattern('^[a-z][a-z0-9-]{2,63}$')]
    [string]$DeviceId = 's26',

    [ValidatePattern('^https://')]
    [string]$HubUrl = 'https://project-ir-hub.mmsotude.workers.dev',

    [ValidateRange(60, 86400)]
    [int]$ExpiresSeconds = 900,

    [securestring]$AdminToken,

    [switch]$CopyToClipboard
)
$ErrorActionPreference = 'Stop'
if (-not $AdminToken) { $AdminToken = Read-Host 'Hub device admin token' -AsSecureString }
$plain = [System.Net.NetworkCredential]::new('', $AdminToken).Password
if ([string]::IsNullOrWhiteSpace($plain)) { throw 'A device admin token is required' }
$headers = @{ Authorization = "Bearer $plain" }
$hub = $HubUrl.TrimEnd('/')

# Idempotent: an existing device is left as it is.
Invoke-RestMethod -Method Post -Uri "$hub/admin/devices/create" -Headers $headers `
    -ContentType 'application/json' -Body (@{ device_id = $DeviceId } | ConvertTo-Json -Compress) | Out-Null
$issued = Invoke-RestMethod -Method Post -Uri "$hub/admin/devices/enrollment" -Headers $headers `
    -ContentType 'application/json' `
    -Body (@{ device_id = $DeviceId; expires_seconds = $ExpiresSeconds } | ConvertTo-Json -Compress)
if (-not $issued.enrollment_code) { throw 'The Hub returned no enrollment code' }
Write-Host "Enrollment code issued for '$DeviceId'; it works once and expires in $ExpiresSeconds seconds."
if ($CopyToClipboard) {
    Set-Clipboard -Value ([string]$issued.enrollment_code)
    Write-Host 'Copied to the clipboard.'
    return
}
$secure = [securestring]::new()
foreach ($ch in ([string]$issued.enrollment_code).ToCharArray()) { $secure.AppendChar($ch) }
$secure.MakeReadOnly()
$secure
