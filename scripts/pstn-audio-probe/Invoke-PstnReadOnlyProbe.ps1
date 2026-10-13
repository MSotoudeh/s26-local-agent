# Read-only Android PSTN audio capability gate. No call, recording, or telecom configuration.
[CmdletBinding()]
param(
    [string]$Adb = 'C:\Tools\Android\platform-tools\adb.exe',
    [string]$Sdk = 'C:\Users\mmsot\AppData\Local\Android\Sdk',
    [string]$Jdk = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Adb)) { throw "adb.exe not found" }
if (-not (Test-Path -LiteralPath "$Jdk\bin\javac.exe")) { throw "javac.exe not found" }
$d8 = Join-Path $Sdk 'build-tools\36.0.0\d8.bat'
if (-not (Test-Path -LiteralPath $d8)) { throw "Android d8 not found" }

$devices = @(& $Adb devices | Select-String '^\S+\s+device$')
if ($devices.Count -ne 1) { throw "Expected exactly one authorized Android device" }
$model = (& $Adb shell getprop ro.product.model | Out-String).Trim()
if ($model -ne 'SM-S948B') { throw "Wrong phone model: $model" }

$local = Join-Path $env:TEMP ('s26-pstn-probe-' + [guid]::NewGuid().ToString('N'))
$remote = '/data/local/tmp/s26-pstn-readonly-probe.jar'
$source = Join-Path $PSScriptRoot 'PstnAudioProbe.java'
$env:JAVA_HOME = $Jdk
New-Item -ItemType Directory -Path (Join-Path $local 'dex') -Force | Out-Null
$remoteUploaded = $false
try {
    & "$Jdk\bin\javac.exe" --release 8 -d $local $source
    if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
    & $d8 --min-api 30 --output (Join-Path $local 'dex') (Join-Path $local 'PstnAudioProbe.class')
    if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
    $jar = Join-Path $local 'probe.jar'
    Compress-Archive -Path (Join-Path $local 'dex\classes.dex') -DestinationPath $jar -Force
    & $Adb push $jar $remote
    if ($LASTEXITCODE -ne 0) { throw 'adb push failed' }
    $remoteUploaded = $true
    & $Adb shell "CLASSPATH=$remote app_process /system/bin PstnAudioProbe"
    if ($LASTEXITCODE -ne 0) { throw 'device probe failed' }
} finally {
    if ($remoteUploaded) { & $Adb shell rm $remote | Out-Null }
    Remove-Item -LiteralPath $local -Recurse -Force -ErrorAction SilentlyContinue
}
