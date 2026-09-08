@echo off
setlocal

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$taskName='CareerFlow Keep Awake';" ^
  "$task=Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue;" ^
  "if ($null -eq $task) { Write-Host 'CareerFlow keep-awake task does not exist.' -ForegroundColor Yellow; exit 0 };" ^
  "Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue;" ^
  "Disable-ScheduledTask -TaskName $taskName | Out-Null;" ^
  "Write-Host 'CareerFlow keep-awake task is stopped and disabled.' -ForegroundColor Green"

if errorlevel 1 (
  echo.
  echo Failed to disable the task. Try right-clicking this file and choosing Run as administrator.
  pause
  exit /b 1
)

pause
endlocal