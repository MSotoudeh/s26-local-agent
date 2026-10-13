[CmdletBinding()]
param(
    [string]$Adb = 'C:\Tools\Android\platform-tools\adb.exe',
    [string]$Sdk = 'C:\Users\mmsot\AppData\Local\Android\Sdk',
    [string]$Jdk = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
)
# This script CANNOT start a phone call, open AudioRecord/AudioTrack, or send sound.
# It only compiles the Java adapter and runs an in-memory fake backend in app_process.
$ErrorActionPreference = 'Stop'
$javac = Join-Path $Jdk 'bin\javac.exe'
$androidJar = Join-Path $Sdk 'platforms\android-37.0\android.jar'
$d8 = Join-Path $Sdk 'build-tools\36.0.0\d8.bat'
foreach ($required in @($javac, $androidJar, $d8, $Adb)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing dependency: $required" }
}
$online = @(& $Adb devices | Select-String '^\S+\s+device$')
if ($online.Count -ne 1) { throw "Exactly one authorized Android device must be connected" }
$model = (& $Adb shell getprop ro.product.model | Out-String).Trim()
$sdkVersion = (& $Adb shell getprop ro.build.version.sdk | Out-String).Trim()
if ($model -ne 'SM-S948B' -or $sdkVersion -ne '37') {
    throw "Unexpected phone: model=$model sdk=$sdkVersion"
}
$src = $PSScriptRoot
$local = Join-Path $env:TEMP ("pstn-adapter-dryrun-" + [guid]::NewGuid().ToString('N'))
$classesDir = Join-Path $local 'classes'
$dexDir = Join-Path $local 'dex'
$remote = '/data/local/tmp/pstn-adapter-dryrun-' + [guid]::NewGuid().ToString('N') + '.jar'
$uploaded = $false
New-Item -ItemType Directory -Path $classesDir, $dexDir -Force | Out-Null
$env:JAVA_HOME = $Jdk
try {
    & $javac --release 8 -cp $androidJar -d $classesDir (Join-Path $src 'PstnAndroidAdapter.java') (Join-Path $src 'PstnAdapterSelfTest.java')
    if ($LASTEXITCODE -ne 0) { throw "javac failed" }
    $classes = @(Get-ChildItem -Path $classesDir -Recurse -Filter '*.class' | ForEach-Object FullName)
    if ($classes.Count -lt 4) { throw 'Incomplete Java class set' }
    & $d8 --min-api 34 --lib $androidJar --output $dexDir @classes
    if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
    $dex = Join-Path $dexDir 'classes.dex'
    if (-not (Test-Path -LiteralPath $dex)) { throw 'Dex missing' }
    $bundle = Join-Path $local 'adapter-test.jar'
    Compress-Archive -Path $dex -DestinationPath $bundle -Force
    & $Adb push $bundle $remote | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'ADB push failed' }
    $uploaded = $true
    Write-Output 'DEVICE_DRY_RUN_SCOPE=FAKE_BACKEND_ONLY_NO_CALL_NO_AUDIO'
    $output = @(& $Adb shell "CLASSPATH=$remote app_process /system/bin PstnAdapterSelfTest --self-test")
    $exit = $LASTEXITCODE
    $output | ForEach-Object { Write-Output $_ }
    if ($exit -ne 0 -or -not ($output -match '^ANDROID_ADAPTER_FAKE_TESTS_PASS=')) {
        throw "On-device fake-only self test failed (exit=$exit)"
    }
    Write-Output 'DRY_RUN_STATUS=PASS'
} finally {
    if ($uploaded) { & $Adb shell rm $remote | Out-Null }
    Remove-Item -LiteralPath $local -Recurse -Force -ErrorAction SilentlyContinue
}
