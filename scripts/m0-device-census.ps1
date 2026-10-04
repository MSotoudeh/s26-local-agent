param(
    [string]$AdbPath
)

$ErrorActionPreference = "Stop"

if (-not $AdbPath) {
    $resolved = Get-Command adb.exe -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Source
    $candidates = @(
        $resolved,
        "C:\Tools\Android\platform-tools\adb.exe",
        (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"),
        (Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"),
        "C:\platform-tools\adb.exe"
    )
    $AdbPath = $candidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
}

if (-not $AdbPath) {
    throw "ADB executable not found. Supply -AdbPath explicitly."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $repoRoot "benchmarks\m0"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"

function Command-Text {
    param([scriptblock]$Command)
    try {
        return ((& $Command 2>&1 | Out-String).Trim())
    } catch {
        return "ERROR: $($_.Exception.Message)"
    }
}

function Adb-Shell {
    param([string]$Serial, [string[]]$Args)
    try {
        return ((& $AdbPath -s $Serial shell @Args 2>&1 | Out-String).Trim())
    } catch {
        return "ERROR: $($_.Exception.Message)"
    }
}

$os = Get-CimInstance Win32_OperatingSystem
$cs = Get-CimInstance Win32_ComputerSystem

$hostResult = [ordered]@{
    captured_at = (Get-Date).ToString("o")
    computer_name = $cs.Name
    username = $env:USERNAME
    os_caption = $os.Caption
    os_version = $os.Version
    os_build = $os.BuildNumber
    total_physical_memory_bytes = [int64]$cs.TotalPhysicalMemory
    powershell = $PSVersionTable.PSVersion.ToString()
    git = Command-Text { git --version }
    python = Command-Text { python --version }
    java = ((cmd.exe /c "java -version 2>&1" | Out-String).Trim())
    adb_path = $AdbPath
    adb_exists = (Test-Path $AdbPath)
    adb_version = if (Test-Path $AdbPath) { Command-Text { & $AdbPath version } } else { $null }
}

$hostPath = Join-Path $outDir "$stamp-host.json"
$hostResult | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 $hostPath

$deviceResult = $null
$devicePath = $null
$classification = "M0_HOST_ONLY_NO_DEVICE"
$adbDevicesRaw = ""

if (Test-Path $AdbPath) {
    $adbDevicesRaw = Command-Text { & $AdbPath devices -l }
    $deviceLines = @($adbDevicesRaw -split "\r?\n" | Where-Object {
        $_ -match "\sdevice(?:\s|$)" -and $_ -notmatch "^List of devices"
    })

    if ($deviceLines.Count -gt 0) {
        $serial = ($deviceLines[0] -split "\s+")[0]
        $allPackages = Adb-Shell $serial @("pm","list","packages")
        $interestingPackages = @($allPackages -split "\r?\n" | Where-Object {
            $_ -match "(shizuku|mobilerun|droidrun|a11ypilot|zafiro|termux|geniex|qualcomm)"
        })

        $meminfo = Adb-Shell $serial @("cat","/proc/meminfo")
        $memTotal = (($meminfo -split "\r?\n" | Where-Object { $_ -match "^MemTotal:" } | Select-Object -First 1) -replace "\s+"," ").Trim()

        $deviceResult = [ordered]@{
            captured_at = (Get-Date).ToString("o")
            adb_transport = "authorized"
            manufacturer = Adb-Shell $serial @("getprop","ro.product.manufacturer")
            model = Adb-Shell $serial @("getprop","ro.product.model")
            device = Adb-Shell $serial @("getprop","ro.product.device")
            android_release = Adb-Shell $serial @("getprop","ro.build.version.release")
            sdk = Adb-Shell $serial @("getprop","ro.build.version.sdk")
            security_patch = Adb-Shell $serial @("getprop","ro.build.version.security_patch")
            build_fingerprint = Adb-Shell $serial @("getprop","ro.build.fingerprint")
            oneui_version_raw = Adb-Shell $serial @("getprop","ro.build.version.oneui")
            abi = Adb-Shell $serial @("getprop","ro.product.cpu.abi")
            abilist = Adb-Shell $serial @("getprop","ro.product.cpu.abilist")
            hardware = Adb-Shell $serial @("getprop","ro.hardware")
            board_platform = Adb-Shell $serial @("getprop","ro.board.platform")
            soc_model = Adb-Shell $serial @("getprop","ro.soc.model")
            soc_manufacturer = Adb-Shell $serial @("getprop","ro.soc.manufacturer")
            mem_total = $memTotal
            selinux = Adb-Shell $serial @("getenforce")
            battery = Adb-Shell $serial @("dumpsys","battery")
            enabled_accessibility_services = Adb-Shell $serial @("settings","get","secure","enabled_accessibility_services")
            interesting_packages = $interestingPackages
        }

        $devicePath = Join-Path $outDir "$stamp-device.json"
        $deviceResult | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 $devicePath
        $classification = "M0_HOST_AND_DEVICE_CAPTURED"
    }
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# M0 census - $stamp")
$lines.Add("")
$lines.Add("Classification: **$classification**")
$lines.Add("")
$lines.Add("## Host")
$lines.Add("")
$lines.Add("- Computer: $($hostResult.computer_name)")
$lines.Add("- OS: $($hostResult.os_caption) $($hostResult.os_version) build $($hostResult.os_build)")
$lines.Add("- PowerShell: $($hostResult.powershell)")
$lines.Add("- Git: $($hostResult.git)")
$lines.Add("- Python: $($hostResult.python)")
$lines.Add("- ADB path: $AdbPath")
$lines.Add("- ADB exists: $($hostResult.adb_exists)")
$lines.Add("")
$lines.Add("## ADB discovery")
$lines.Add("")
$deviceCount = @($adbDevicesRaw -split "\r?\n" | Where-Object { $_ -match "\sdevice(?:\s|$)" -and $_ -notmatch "^List of devices" }).Count
$lines.Add("ADB device count: $deviceCount")
$lines.Add("")
$lines.Add("## Device")
$lines.Add("")

if ($deviceResult) {
    $lines.Add("- Model: $($deviceResult.model)")
    $lines.Add("- Android: $($deviceResult.android_release) / API $($deviceResult.sdk)")
    $lines.Add("- One UI raw version: $($deviceResult.oneui_version_raw)")
    $lines.Add("- ABI: $($deviceResult.abi)")
    $lines.Add("- SoC: $($deviceResult.soc_manufacturer) $($deviceResult.soc_model)")
    $lines.Add("- Board: $($deviceResult.board_platform)")
    $lines.Add("- RAM: $($deviceResult.mem_total)")
    $lines.Add("- SELinux: $($deviceResult.selinux)")
    $lines.Add("- Relevant packages: $($deviceResult.interesting_packages -join ', ')")
} else {
    $lines.Add("No Android device was visible to ADB during this run. Host evidence is valid; device census remains pending.")
}

$lines.Add("")
$lines.Add("## Evidence")
$lines.Add("")
$hostLeaf = Split-Path -Leaf $hostPath
$lines.Add("- Host JSON: $hostLeaf")
if ($devicePath) {
    $deviceLeaf = Split-Path -Leaf $devicePath
    $lines.Add("- Device JSON: $deviceLeaf")
} else {
    $lines.Add("- Device JSON: not produced")
}

$summaryPath = Join-Path $outDir "$stamp-summary.md"
$lines | Set-Content -Encoding UTF8 $summaryPath

Write-Output "classification=$classification"
Write-Output "host=$hostPath"
if ($devicePath) { Write-Output "device=$devicePath" }
Write-Output "summary=$summaryPath"
