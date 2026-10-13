param(
    [string]$Adb = 'C:\Tools\Android\platform-tools\adb.exe',
    [int]$OfflineSamples = 12,
    [int]$OfflineSeconds = 12,
    [switch]$StrictOffline,
    [string]$Output = ''
)
$ErrorActionPreference = 'Stop'

function AdbText([string[]]$Argv) {
    $txt = (& $Adb @Argv 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed, exit=$LASTEXITCODE" }
    return $txt
}
function Shell([string]$Command) {
    return AdbText -Argv @('shell', $Command)
}
function TestBridge {
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $data = Shell 'content query --uri content://io.projectir.mobile.ui/status'
    $timer.Stop()
    $ok = $data -match '"connected":true' -and $data -match '"ok":true'
    return [ordered]@{ ok=[bool]$ok; ms=[math]::Round($timer.Elapsed.TotalMilliseconds,1) }
}
function ShizukuState {
    $rows = Shell 'ps -A'
    return [ordered]@{
        server=[bool]($rows -match '\bshizuku_server\b')
        user_service=[bool]($rows -match 'io\.projectir\.mobile:project_ir_shell')
    }
}
function GetGlobal([string]$key) { (Shell ("settings get global " + $key)).Trim() }
function SetAirplane([bool]$enabled) {
    $action = if ($enabled) { 'enable' } else { 'disable' }
    $null = Shell ("cmd connectivity airplane-mode " + $action)
}
function RestoreNetworks {
    SetAirplane ($before.airplane_mode_on -eq '1')
    if ($before.wifi_on -eq '1') {
        $null = Shell 'svc wifi enable'
    } elseif ($before.wifi_on -eq '0') {
        $null = Shell 'svc wifi disable'
    }
    if ($before.mobile_data -eq '1') {
        $null = Shell 'svc data enable'
    } elseif ($before.mobile_data -eq '0') {
        $null = Shell 'svc data disable'
    }
}
if (-not (Test-Path $Adb)) { throw 'ADB executable not found' }
$devices = AdbText -Argv @('devices')
$ready = @($devices -split '[\r\n]+' | Where-Object { $_ -match '^\S+\s+device$' })
if ($ready.Count -ne 1) { throw "Expected exactly one authorized USB ADB device. Ready devices: $($ready.Count)" }
$before = [ordered]@{
    airplane_mode_on = GetGlobal 'airplane_mode_on'
    wifi_on = GetGlobal 'wifi_on'
    mobile_data = GetGlobal 'mobile_data'
}
if ($before.airplane_mode_on -ne '0') { throw 'Preflight: airplane mode already active; refusing to change baseline.' }
$baseline = [ordered]@{
    bridge = TestBridge
    shizuku = ShizukuState
}
if (-not $baseline.bridge.ok -or -not $baseline.shizuku.server) { throw 'Baseline bridge or Shizuku not healthy' }
$report = [ordered]@{
    experiment = 'M1-offline-airplane-isolated'
    date = (Get-Date -Format 'yyyy-MM-dd')
    model = (Shell 'getprop ro.product.model')
    before = $before
    baseline = $baseline
    airplane_mode_verified = $false
    strict_offline_verified = $null
    network_during = $null
    offline = @()
    restored = $false
    after = $null
}
try {
    SetAirplane $true
    if ($StrictOffline) {
        $null = Shell 'svc wifi disable'
        $null = Shell 'svc data disable'
    }
    Start-Sleep -Seconds 2
    $report.airplane_mode_verified = (GetGlobal 'airplane_mode_on') -eq '1'
    $report.network_during = [ordered]@{ airplane_mode_on = GetGlobal 'airplane_mode_on'; wifi_on = GetGlobal 'wifi_on'; mobile_data = GetGlobal 'mobile_data'; ipv4_default_route_present = [bool]((Shell 'ip route show default').Trim()) }
    $report.strict_offline_verified = $report.airplane_mode_verified -and $report.network_during.wifi_on -eq '0' -and $report.network_during.mobile_data -eq '0' -and -not $report.network_during.ipv4_default_route_present
    if (-not $report.airplane_mode_verified) { throw 'Airplane mode did not activate' }
    if ($StrictOffline -and -not $report.strict_offline_verified) { throw 'Strict offline state not verified' }
    for ($i=0; $i -lt $OfflineSamples; $i++) {
        $shizuku = ShizukuState
        $bridge = TestBridge
        $report.offline += [ordered]@{
            index = $i+1
            bridge_ok = $bridge.ok
            bridge_ms = $bridge.ms
            shizuku_server = $shizuku.server
            user_service = $shizuku.user_service
        }
        if ($OfflineSeconds -gt 0 -and $i+1 -lt $OfflineSamples) {
            Start-Sleep -Milliseconds ([int](1000*$OfflineSeconds/$OfflineSamples))
        }
    }
}
finally {
    RestoreNetworks
    Start-Sleep -Seconds 2
    $report.after = [ordered]@{
        airplane_mode_on = GetGlobal 'airplane_mode_on'
        wifi_on = GetGlobal 'wifi_on'
        mobile_data = GetGlobal 'mobile_data'
    }
    $report.restored = ($report.after.airplane_mode_on -eq $before.airplane_mode_on) -and
        ($report.after.wifi_on -eq $before.wifi_on) -and
        ($report.after.mobile_data -eq $before.mobile_data)
}
$pass = @($report.offline | Where-Object { $_.bridge_ok -and $_.shizuku_server -and $_.user_service }).Count
$report.summary = [ordered]@{
    samples = $report.offline.Count
    local_bridge_and_shizuku_pass = $pass
    network_restored = $report.restored
}
$json = ConvertTo-Json -InputObject $report -Depth 10
if ($Output) {
    $folder = Split-Path $Output -Parent
    if (-not (Test-Path $folder)) { New-Item -ItemType Directory -Path $folder -Force | Out-Null }
    [IO.File]::WriteAllText($Output, $json + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
}
Write-Output ($json)
if (-not $report.restored -or $pass -ne $OfflineSamples -or ($StrictOffline -and -not $report.strict_offline_verified)) { exit 2 }
