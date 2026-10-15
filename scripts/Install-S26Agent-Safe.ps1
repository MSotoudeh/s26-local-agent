$ErrorActionPreference = 'Stop'
$adb = 'C:\Users\mmsot\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$signer = 'C:\Users\mmsot\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat'
$apk = 'C:\GitHub\project-ir-s26-plan-20261014\android\mobile-agent\app\build\outputs\apk\debug\app-s26-matching-signer.apk'
$backup = 'C:\Users\mmsot\AppData\Local\ProjectIR\backups\s26-agent\20261014-before-local-plan-update.apk'
$expectedCandidate = 'efb826ec1ff86b1478d3ee2839917cd78896659f806fd519e20db74699bb9ce2'
$expectedOld = '2f234cf131452ab2813f570dbcc6ecd2a1dac5e7c4f3de7c48feda4b3bc58b34'
$pkg = 'io.projectir.mobile'
function Require([bool]$condition, [string]$reason) {
    if (-not $condition) { throw $reason }
}
Require (Test-Path $adb) 'ADB_MISSING'
Require (Test-Path $apk) 'CANDIDATE_MISSING'
Require (Test-Path $backup) 'BACKUP_MISSING'
Require (((Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant()) -eq $expectedCandidate) 'CANDIDATE_CHANGED'
Require (((Get-FileHash $backup -Algorithm SHA256).Hash.ToLowerInvariant()) -eq $expectedOld) 'BACKUP_CHANGED'
$devices = @(& $adb devices -l | Where-Object {$_ -match '^\S+\s+device(\s|$)'})
Require ($devices.Count -eq 1) 'REQUIRE_EXACTLY_ONE_AUTHORIZED_ADB_DEVICE'
$serial = ([regex]::Match($devices[0], '^(\S+)')).Groups[1].Value
$pathLine = @(& $adb -s $serial shell pm path $pkg | Where-Object {$_ -match '^package:'})
Require ($pathLine.Count -eq 1) 'INSTALLED_PACKAGE_PATH_AMBIGUOUS'
$original = Join-Path $env:TEMP 's26-original-installed-20261015-preflight.apk'
Require (!(Test-Path $original)) 'TEMP_EXISTS'
try {
    $null = & $adb -s $serial pull $pathLine[0].Substring(8) $original 2>&1
    Require ($LASTEXITCODE -eq 0) 'READ_INSTALLED_APK_FAILED'
    Require (((Get-FileHash $original -Algorithm SHA256).Hash.ToLowerInvariant()) -eq $expectedOld) 'INSTALLED_PACKAGE_CHANGED_SINCE_BACKUP'
    $pattern = '(?im)Signer #1 certificate SHA-256 digest:\s*([a-f0-9]+)'
    $oldSig = @(& $signer verify --print-certs $original)
    Require ($LASTEXITCODE -eq 0) 'ORIGINAL_SIGNATURE_BAD'
    $newSig = @(& $signer verify --print-certs $apk)
    Require ($LASTEXITCODE -eq 0) 'CANDIDATE_SIGNATURE_BAD'
    $oldFp = [regex]::Match(($oldSig -join [Environment]::NewLine), $pattern).Groups[1].Value
    $newFp = [regex]::Match(($newSig -join [Environment]::NewLine), $pattern).Groups[1].Value
    Require ($oldFp.Length -eq 64 -and $oldFp -eq $newFp) 'CERTIFICATES_DO_NOT_MATCH'
    Write-Output 'PRE_INSTALL_BACKUP_SHA256_MATCH=PASS'
    Write-Output 'SIGNATURE_MATCH=PASS'
} finally {
    Remove-Item -LiteralPath $original -Force -ErrorAction SilentlyContinue
}
$before = @(& $adb -s $serial shell pidof $pkg 2>$null) -join ''
Write-Output ('OLD_AGENT_PROCESS_PRESENT=' + [bool]($before.Trim()))
Write-Output 'INSTALL_START'
$install = @(& $adb -s $serial install --no-streaming -r $apk 2>&1)
if ($LASTEXITCODE -ne 0 -or ($install -join ' ') -notmatch 'Success') {
    Write-Output 'INSTALL_FAILED_OR_UNCONFIRMED'
    throw ($install -join ' ')
}
Write-Output 'INSTALL_SUCCESS_NO_UNINSTALL_NO_DATA_CLEAR'
$start = @(& $adb -s $serial shell am start -n io.projectir.mobile/.MainActivity 2>&1)
Write-Output ('ACTIVITY_LAUNCH_EXIT=' + $LASTEXITCODE)
Write-Output ('ACTIVITY_LAUNCH_RESULT=' + (($start -join ' ') -replace '[\r\n]', ' '))
Start-Sleep -Seconds 5
$after = @(& $adb -s $serial shell pidof $pkg 2>$null) -join ''
Write-Output ('NEW_AGENT_PROCESS_PRESENT=' + [bool]($after.Trim()))
$svc = @(& $adb -s $serial shell dumpsys activity services io.projectir.mobile)
Write-Output ('FOREGROUND_SERVICE_PRESENT=' + [bool](($svc -join [Environment]::NewLine) -match 'isForeground=true'))
$access = @(& $adb -s $serial shell settings get secure enabled_accessibility_services)
Write-Output ('ACCESSIBILITY_STILL_ENABLED=' + [bool](($access -join '') -match 'io.projectir.mobile/io.projectir.mobile.UiAccessibilityService'))
$provider = @(& $adb -s $serial shell content call --uri content://io.projectir.mobile.ui --method status)
Write-Output ('ACCESSIBILITY_PROVIDER_OK=' + [bool](($provider -join '') -match '"ok":true'))
Write-Output 'POST_INSTALL_INSPECTION_COMPLETE'
