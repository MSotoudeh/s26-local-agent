param(
    # MediaPipe tasks-genai 0.10.27 contains Java 21 bytecode (class version 65).
    # Ops has JDK 25; do not build this app with the older project JDK 17.
    [string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot',
    [string]$AndroidSdk = (Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk'),
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
if (-not $env:GRADLE_USER_HOME) {
    # Project IR may run as SYSTEM with a redirected user profile.
    $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
}
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
