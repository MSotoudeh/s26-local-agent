param(
    [string]$AndroidRepo = 'C:\GitHub\project-ir-android-agent',
    [string]$JavaHome = 'C:\Users\mmsot\AppData\Local\ProjectIR\toolchains\jdk-17.0.20.1+1',
    [string]$Output = 'C:\GitHub\s26-local-agent\benchmarks\m9\20261014-offline-machine.json'
)
$ErrorActionPreference = 'Stop'
$javac = Join-Path $JavaHome 'bin\javac.exe'
$java = Join-Path $JavaHome 'bin\java.exe'
if (-not (Test-Path $javac) -or -not (Test-Path $java)) {
    throw 'JDK_NOT_AVAILABLE'
}
if (-not (Test-Path (Join-Path $AndroidRepo 'android\mobile-agent\tests\LocalPlanMachineTest.java'))) {
    throw 'PLAN_MACHINE_TEST_NOT_FOUND'
}
$dir = Join-Path $env:TEMP 's26-plan-machine-standalone'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
try {
    $root = Join-Path $AndroidRepo 'android\mobile-agent\app\src\main\java\io\projectir\mobile'
    $src = @('LocalSkillPlan','LocalSkillRegistry','LocalPlanPolicy',
        'LocalSemanticSelector','LocalActionIdentity','LocalPlanMachine') |
        ForEach-Object { Join-Path $root ($_.ToString()+'.java') }
    $test = Join-Path $AndroidRepo 'android\mobile-agent\tests\LocalPlanMachineTest.java'
    & $javac -encoding UTF-8 -d $dir @src $test
    if ($LASTEXITCODE -ne 0) { throw 'JAVA_COMPILATION_FAILED' }
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $stdout = (& $java -cp $dir io.projectir.mobile.LocalPlanMachineTest 2>&1 | Out-String)
    $exit = $LASTEXITCODE
    $watch.Stop()
    if ($exit -ne 0) { throw 'JVM_ASSERTIONS_FAILED' }
    $assertMatch = [regex]::Match($stdout, 'LOCAL_PLAN_MACHINE_TESTS_PASS=(\d+)')
    $trialsMatch = [regex]::Match($stdout, 'SIMULATED_SUCCESSFUL_MULTISTEP_TASKS=(\d+)')
    $effectsMatch = [regex]::Match($stdout, 'SIDE_EFFECTS_PERFORMED=(\d+)')
    if (-not $assertMatch.Success -or -not $trialsMatch.Success -or -not $effectsMatch.Success) {
        throw 'UNRECOGNIZED_TEST_OUTPUT'
    }
    $assertions = [int]$assertMatch.Groups[1].Value
    $trials = [int]$trialsMatch.Groups[1].Value
    $effects = [int]$effectsMatch.Groups[1].Value
    if ($assertions -lt 15022 -or $trials -ne 1000 -or $effects -ne 0) {
        throw 'EXPECTED_TEST_INVARIANTS_NOT_MET'
    }
    $report = [ordered]@{
        date = (Get-Date).ToString('yyyy-MM-dd')
        mode = 'source-only-fake-observations'
        simulation_tasks_pass = $trials
        assertions_pass = $assertions
        actual_android_effects = $effects
        test_duration_ms = [int]$watch.Elapsed.TotalMilliseconds
        device_tested = $false
        reboot_performed = $false
        result = 'PASS_SIMULATION_ONLY'
    }
    $folder = Split-Path -Parent $Output
    New-Item -ItemType Directory -Force -Path $folder | Out-Null
    [IO.File]::WriteAllText($Output,
        ((ConvertTo-Json -InputObject $report -Depth 5) + [Environment]::NewLine),
        [Text.UTF8Encoding]::new($false))
    Write-Output (ConvertTo-Json -InputObject $report -Depth 5)
} finally {
    Remove-Item $dir -Recurse -Force -ErrorAction SilentlyContinue
}
