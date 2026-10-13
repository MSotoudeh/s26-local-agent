param(
  [string]$Adb='C:\Tools\Android\platform-tools\adb.exe',
  [int]$TimeoutSeconds=40,
  [string]$Output='benchmarks\m1\20261013-shizuku-user-service-rebind.json'
)
$ErrorActionPreference='Stop'
function Shell([string]$cmd) {
  $o=(& $Adb shell $cmd 2>&1 | Out-String).Trim()
  if($LASTEXITCODE -ne 0){throw ('ADB failure for '+($cmd -split ' ')[0])}
  return $o
}
function PidByName([string]$name){
  $list=Shell 'ps -A -o PID,NAME'
  $lines=@($list -split '[\r\n]+'|Where-Object {$_.Trim().EndsWith(' '+$name)})
  if($lines.Count -ne 1){return 0}
  $m=[regex]::Match($lines[0].Trim(),'^\s*([0-9]+)\s+')
  if(!$m.Success){return 0}
  return [int]$m.Groups[1].Value
}
$all=(& $Adb devices | Out-String)
if(@($all -split '[\r\n]+'|Where-Object {$_ -match '^\S+\s+device$'}).Count -ne 1){throw 'Require exactly one authorized USB ADB device'}
$serverBefore=PidByName 'shizuku_server'
$userBefore=PidByName 'io.projectir.mobile:project_ir_shell'
$appBefore=PidByName 'io.projectir.mobile'
if($serverBefore -le 0 -or $userBefore -le 0 -or $appBefore -le 0){throw 'Baseline processes not healthy; no termination performed'}
$started=[Diagnostics.Stopwatch]::StartNew()
$report=[ordered]@{
  experiment='M1-user-service-binder-rebind'
  date=Get-Date -Format 'yyyy-MM-dd'
  preconditions=[ordered]@{server_running=$true;user_service_running=$true;app_running=$true}
  signal='SIGTERM'
  user_service_recreated=$false
  server_pid_unchanged=$false
  app_pid_unchanged=$false
  observed_recovery_ms=$null
  observed_samples=0
  foreground_untouched=$true
}
$null=Shell ('kill -TERM '+$userBefore)
for($i=0;$i -lt [math]::Ceiling($TimeoutSeconds/2);$i++){
  Start-Sleep -Seconds 2
  $report.observed_samples++
  $current=PidByName 'io.projectir.mobile:project_ir_shell'
  if($current -gt 0 -and $current -ne $userBefore){
    $report.user_service_recreated=$true
    $report.observed_recovery_ms=[math]::Round($started.Elapsed.TotalMilliseconds)
    break
  }
}
$report.server_pid_unchanged=(PidByName 'shizuku_server') -eq $serverBefore
$report.app_pid_unchanged=(PidByName 'io.projectir.mobile') -eq $appBefore
$report.recovered_without_ui=$report.user_service_recreated -and $report.server_pid_unchanged -and $report.app_pid_unchanged
$report.hub_binder_ready_after_restart=$null
$report.direct_remote_exec_verified=$null
$report.final_verdict='PENDING_END_TO_END_VERIFICATION'
# A recovered PID alone does NOT prove a live Binder. Confirm Hub heartbeat
# readiness and a successful direct remote_exec in a separate Project IR call.
$full=Join-Path (Get-Location).Path $Output
New-Item -ItemType Directory -Force -Path (Split-Path $full -Parent)|Out-Null
[IO.File]::WriteAllText($full,((ConvertTo-Json -InputObject $report -Depth 5) + [Environment]::NewLine),[Text.UTF8Encoding]::new($false))
Write-Output (ConvertTo-Json -InputObject $report -Depth 5)
if(-not $report.recovered_without_ui){exit 2}
