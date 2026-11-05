[CmdletBinding()]
param([string]$AppDirectory = '')
$ErrorActionPreference = 'Stop'
if (-not $AppDirectory) { $AppDirectory = $PSScriptRoot }
$task = Get-ScheduledTask -TaskName 'CareerFlow Local Service' -ErrorAction SilentlyContinue
if ($task -and $task.State -eq 'Running') {
    Stop-ScheduledTask -InputObject $task
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        if ((Get-ScheduledTask -TaskName 'CareerFlow Local Service').State -ne 'Running') { break }
        Start-Sleep -Milliseconds 250
    }
    if ((Get-ScheduledTask -TaskName 'CareerFlow Local Service').State -eq 'Running') { throw 'The local supervisor did not stop.' }
}
$pidFile = Join-Path $AppDirectory 'run\app.pid'
if (Test-Path -LiteralPath $pidFile) {
    $serviceProcessId = 0
    if ([int]::TryParse((Get-Content -Raw -LiteralPath $pidFile).Trim(), [ref]$serviceProcessId)) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$serviceProcessId" -ErrorAction SilentlyContinue
        $jar = Join-Path ([IO.Path]::GetFullPath($AppDirectory)) 'job-tracker.jar'
        if ($process -and $process.CommandLine -like "*$jar*") {
            Stop-Process -Id $serviceProcessId -Force
            Wait-Process -Id $serviceProcessId -Timeout 20 -ErrorAction SilentlyContinue
        }
    }
    Remove-Item -LiteralPath $pidFile -Force
}
Write-Host 'Local service stopped.'
