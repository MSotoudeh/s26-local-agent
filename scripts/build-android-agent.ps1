param(
    [string]$JavaHome = (Join-Path $env:LOCALAPPDATA "ProjectIR\toolchains\jdk-17.0.20.1+1"),
    [string]$AndroidSdk = (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
    [switch]$SkipLint
)

$ErrorActionPreference = "Stop"

$java = Join-Path $JavaHome "bin\java.exe"
if (-not (Test-Path $java)) {
    throw "Project IR Android JDK 17 not found: $JavaHome"
}
if (-not (Test-Path (Join-Path $AndroidSdk "platforms\android-37.0"))) {
    throw "Android API 37 platform not found under: $AndroidSdk"
}

$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidSdk
$env:ANDROID_SDK_ROOT = $AndroidSdk
$env:Path = "$JavaHome\bin;$env:Path"

$root = Join-Path $PSScriptRoot "..\android\mobile-agent"
Push-Location $root
try {
    if ($SkipLint) {
        & .\gradlew.bat --no-daemon assembleDebug
    } else {
        & .\gradlew.bat --no-daemon assembleDebug lintDebug
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Android mobile-agent build failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}
