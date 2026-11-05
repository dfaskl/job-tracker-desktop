[CmdletBinding()]
param([string]$AppDirectory = '')
$ErrorActionPreference = 'Stop'
if (-not $AppDirectory) { $AppDirectory = $PSScriptRoot }
$userId = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$engine = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$arguments = '-NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + (Join-Path $AppDirectory 'start.ps1') + '" -Watch'
$action = New-ScheduledTaskAction -Execute $engine -Argument $arguments -WorkingDirectory $AppDirectory
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $userId
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 10 -RestartInterval (New-TimeSpan -Minutes 1)
$principal = New-ScheduledTaskPrincipal -UserId $userId -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName 'CareerFlow Local Service' -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Description 'Run CareerFlow locally after Windows login; restart the application if it exits.' -Force | Out-Null
Write-Host 'Local service logon task installed.'
