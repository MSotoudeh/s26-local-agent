param(
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$target = Join-Path $repo 'android\mobile-agent\app\libs\sherpa-onnx-1.13.8.aar'
$expected = '633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96'
$url = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar'
New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
if (!(Test-Path $target)) {
    if ($Offline) { throw 'M2_SHERPA_RUNTIME_MISSING_IN_OFFLINE_MODE' }
    $partial = $target + '.partial'
    if (Test-Path $partial) { throw 'M2_PARTIAL_FILE_REQUIRES_EXPLICIT_REVIEW' }
    & curl.exe -fL --retry 2 --connect-timeout 15 --max-time 210 -o $partial $url
    if ($LASTEXITCODE -ne 0) { throw 'SHERPA_DOWNLOAD_FAILED' }
    $hash = (Get-FileHash $partial -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $expected) { throw 'SHERPA_RELEASE_HASH_MISMATCH' }
    Move-Item -LiteralPath $partial -Destination $target
}
$actual = (Get-FileHash $target -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $expected) { throw 'SHERPA_RELEASE_HASH_MISMATCH' }
Write-Output 'SHERPA_RUNTIME_SOURCE=official_release_v1.13.8'
Write-Output 'SHERPA_RUNTIME_PINNED_SHA256=PASS'
Write-Output 'SHERPA_RUNTIME_WEIGHT_FILES_EMBEDDED=NONE'
Write-Output 'SHERPA_RUNTIME_PATH=android/mobile-agent/app/libs/sherpa-onnx-1.13.8.aar'
