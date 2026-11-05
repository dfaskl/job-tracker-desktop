[CmdletBinding()]
param([string]$TaskName = 'CareerFlow Hourly Local Backup')
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'local-deployment.json')) {
    throw 'This workstation is now the production service. Its database is backed up hourly by the application; do not re-enable the old server polling task.'
}
$script = Join-Path $PSScriptRoot 'backup-server.ps1'
if (-not (Test-Path -LiteralPath $script)) { throw 'Backup script is missing.' }
$userId = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$engine = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$action = New-ScheduledTaskAction -Execute $engine -Argument ('-NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + $script + '" -DatabaseOnly') -WorkingDirectory $PSScriptRoot
$hourly = New-ScheduledTaskTrigger -Once -At (Get-Date).AddHours(1) -RepetitionInterval (New-TimeSpan -Hours 1)
$logon = New-ScheduledTaskTrigger -AtLogOn -User $userId
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Minutes 20) `
    -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 10)
$principal = New-ScheduledTaskPrincipal -UserId $userId -LogonType Interactive -RunLevel Limited
$task = New-ScheduledTask -Action $action -Trigger @($hourly,$logon) -Settings $settings -Principal $principal `
    -Description 'Copy a consistent SQLite snapshot to server-backup/current every hour. Only one local backup is retained. Requires login and access to the server.'
Register-ScheduledTask -TaskName $TaskName -InputObject $task -Force | Out-Null
Write-Host "Hourly local backup task installed: $TaskName"
