@echo off
setlocal
set "KEEP_AWAKE_SCRIPT=%~dp0keep-render-awake.ps1"

if not exist "%KEEP_AWAKE_SCRIPT%" (
  echo ERROR: keep-render-awake.ps1 was not found.
  pause
  exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$taskName='CareerFlow Keep Awake';" ^
  "$scriptPath=$env:KEEP_AWAKE_SCRIPT;" ^
  "Get-CimInstance Win32_Process | Where-Object { $_.ProcessId -ne $PID -and $_.CommandLine -like '*keep-render-awake.ps1*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue };" ^
  "$action=New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File ' + [char]34 + $scriptPath + [char]34);" ^
  "$trigger=New-ScheduledTaskTrigger -AtLogOn;" ^
  "$settings=New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero);" ^
  "Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Description 'Keep CareerFlow Render service awake every 10 minutes' -Force | Out-Null;" ^
  "Enable-ScheduledTask -TaskName $taskName | Out-Null;" ^
  "Start-ScheduledTask -TaskName $taskName;" ^
  "Write-Host 'CareerFlow keep-awake task is enabled and running.' -ForegroundColor Green"

if errorlevel 1 (
  echo.
  echo Failed to enable the task. Try right-clicking this file and choosing Run as administrator.
  pause
  exit /b 1
)

endlocal
