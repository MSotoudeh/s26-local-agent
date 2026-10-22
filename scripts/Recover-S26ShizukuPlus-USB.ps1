<#
.SYNOPSIS
  Restore the installed ShizukuPlus server through an authorized USB ADB connection.
.DESCRIPTION
  An explicitly invoked USB recovery helper for the S26 Local Agent. It does NOT
  enable network ADB, change settings, touch the UI, use root, or configure a
  scheduled task. It is NOT a phone-only post-boot solution.
#>
[CmdletBinding()]
param(
  [string]$Serial = 'R5GL82NN2YE',
  [switch]$Force
)
$ErrorActionPreference = 'Stop'

$paths = @(
  "$env:USERPROFILE\AppData\Local\Android\Sdk\platform-tools\adb.exe",
  "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
  'C:\platform-tools\adb.exe'
)
$found = Get-Command adb -ErrorAction SilentlyContinue
if ($found) { $paths = @($found.Source) + $paths }
$adb = $paths | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $adb) { throw 'adb.exe not found' }

$devices = @(& $adb devices)
$match = '^' + [regex]::Escape($Serial) + '\s+device\b'
if (-not ($devices | Where-Object { $_ -match $match })) {
  throw "Device $Serial is not connected/authorized over USB ADB"
}
function Call-Adb([string[]]$ArgsList) {
  $output = @(& $script:adb -s $script:Serial @ArgsList)
  if ($LASTEXITCODE -ne 0) {
    throw "adb failed: $($ArgsList -join ' ')"
  }
  return $output
}

$bootId = (Call-Adb @('shell','cat','/proc/sys/kernel/random/boot_id') | Select-Object -First 1).Trim()
$wifi = (Call-Adb @('shell','settings','get','global','wifi_on') | Select-Object -First 1).Trim()
$packageName = 'af.shizuku.plus.api'
$packagePathLine = Call-Adb @('shell','pm','path',$packageName) | Where-Object { $_ -like 'package:*base.apk' } | Select-Object -First 1
if (-not $packagePathLine) { throw 'ShizukuPlus standard package is not installed' }
$apk = $packagePathLine.Substring(8).Trim()
$native = $apk -replace '/base\.apk$', '/lib/arm64'
$processRows = Call-Adb @('shell','ps','-A','-o','USER,PID,ARGS')
$isRunning = @($processRows | Where-Object { $_ -match '\bshizuku_plus_server\b' }).Count -gt 0

Write-Output "boot_id=$bootId wifi_on=$wifi shizuku_server_running=$isRunning"
if ($isRunning -and -not $Force) {
  Write-Output 'No change: server is already running; use -Force only for explicit recovery.'
  exit 0
}

$binary = "$native/libshizuku.so"
$command = "LD_LIBRARY_PATH=$native $binary --apk=$apk"
$launch = Call-Adb @('shell', $command)
$launch | ForEach-Object { Write-Output $_ }
if (-not ($launch -match 'shizuku_starter exit with 0')) {
  throw 'Starter did not report a successful exit'
}
Start-Sleep -Milliseconds 750
$after = Call-Adb @('shell','ps','-A','-o','USER,PID,ARGS')
if (-not ($after | Where-Object { $_ -match '^shell\s+\d+\s+.*\bshizuku_plus_server\b' })) {
  throw 'Server process not found under shell UID after launch'
}
Write-Output 'PASS: ShizukuPlus server process running as shell; check DroidBridge Binder separately.'
