param([int]$First=4,[int]$Count=4)
$ErrorActionPreference='Stop'
if($First -lt 4 -or $Count -lt 1 -or $Count -gt 8){throw 'BOUNDED_TRIAL_RANGE_REQUIRED'}
$adb='C:\Users\mmsot\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$uri='content://io.projectir.mobile.ui'
function Action([string]$method,[string]$argument) {
    $out=((& $script:adb shell content call --uri $script:uri --method $method --arg $argument) -join '')
    if($LASTEXITCODE -ne 0 -or $out -notmatch '"ok":true'){
        throw ('ACCESSIBILITY_ACTION_' + $method + '_REJECTED')
    }
}
function Set-Command {
    Action 'click_resource' 'io.projectir.mobile:id/local_command_input'
    $out=((& $script:adb shell 'content call --uri content://io.projectir.mobile.ui --method input_focused --arg "open connections"') -join '')
    if($LASTEXITCODE -ne 0 -or $out -notmatch '"ok":true'){throw 'TEXT_INPUT_FAILED'}
}
function Read-Journal {
    $xml=((& $script:adb shell run-as io.projectir.mobile cat shared_prefs/local_plan_journal.xml) -join [Environment]::NewLine)
    $item=@{}
    foreach($name in @('plan.task_id','plan.status','plan.reason','plan.step')){
        $m=[regex]::Match($xml,'<string name="'+[regex]::Escape($name)+'">([^<]*)</string>')
        $item[$name]=$m.Groups[1].Value
    }
    return $item
}
$devices=@(& $adb devices -l | Where-Object {$_ -match '^\S+\s+device(\s|$)'})
if($devices.Count -ne 1){throw 'NEED_EXACTLY_ONE_AUTHORIZED_ADB_DEVICE'}
$passed=0
for($i=$First;$i -lt ($First+$Count);$i++){
    $id='s26_live_settings_connections_20261015_'+$i.ToString('000')
    $out=@(& $adb shell am start -n io.projectir.mobile/.MainActivity)
    if($LASTEXITCODE -ne 0){throw 'MAIN_ACTIVITY_NOT_STARTABLE'}
    Start-Sleep -Milliseconds 900
    Set-Command
    Action 'click_resource' 'io.projectir.mobile:id/local_action_id_input'
    Action 'input_focused' $id
    $click=((& $adb shell 'content call --uri content://io.projectir.mobile.ui --method click_text --arg "RUN VERIFIED MULTI-STEP PLAN (OFFLINE)"') -join '')
    if($LASTEXITCODE -ne 0 -or $click -notmatch '"ok":true'){throw 'PLAN_TRIGGER_REJECTED'}
    $final=$null
    for($retry=0;$retry -lt 25;$retry++){
        Start-Sleep -Milliseconds 500
        $j=Read-Journal
        if($j['plan.task_id'] -ne $id){continue}
        if($j['plan.status'] -in @('SUCCEEDED','FAILED','CANCELLED','INTERRUPTED_UNKNOWN')){
            $final=$j; break
        }
    }
    if($null -eq $final){throw ('TRIAL_'+$i+'_NO_TERMINAL_STATUS')}
    $line='TRIAL_'+$i+'_STATUS='+$final['plan.status']+' REASON='+$final['plan.reason']
    Write-Output $line
    if($final['plan.status'] -ne 'SUCCEEDED' -or
        $final['plan.reason'] -ne 'VERIFIED_CANONICAL_PLAN'){
        throw ('TRIAL_'+$i+'_FAILED_CLOSED')
    }
    $passed++
}
Write-Output ('REAL_S26_MULTISTEP_SUCCESS='+$passed+'/'+$Count)
