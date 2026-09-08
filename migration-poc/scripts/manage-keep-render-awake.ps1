param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Enable', 'Disable')]
    [string]$Action
)

$ErrorActionPreference = 'Stop'
$taskName = 'CareerFlow Keep Awake'
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$workerPath = Join-Path $scriptDirectory 'keep-render-awake.ps1'
$launcherPath = Join-Path $scriptDirectory 'keep-render-awake-hidden.vbs'

if ($Action -eq 'Enable') {
    if (-not (Test-Path -LiteralPath $workerPath)) { throw "Missing worker script: $workerPath" }
    if (-not (Test-Path -LiteralPath $launcherPath)) { throw "Missing hidden launcher: $launcherPath" }

    Get-CimInstance Win32_Process |
        Where-Object { $_.ProcessId -ne $PID -and $_.CommandLine -match '(?i)-File\s+["'']?[^"'']*[\\/]keep-render-awake\.ps1(?:["'']|\s|$)' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

    $taskAction = New-ScheduledTaskAction -Execute 'wscript.exe' -Argument ('"' + $launcherPath + '"')
    $trigger = New-ScheduledTaskTrigger -AtLogOn
    $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew `
        -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero)
    Register-ScheduledTask -TaskName $taskName -Action $taskAction -Trigger $trigger -Settings $settings `
        -Description 'Keep CareerFlow Render service awake every 10 minutes' -Force | Out-Null
    Enable-ScheduledTask -TaskName $taskName | Out-Null
    Start-ScheduledTask -TaskName $taskName
    exit 0
}

$task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($null -ne $task) {
    Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    Disable-ScheduledTask -TaskName $taskName | Out-Null
}