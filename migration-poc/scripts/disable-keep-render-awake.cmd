@echo off
net session >nul 2>&1
if not %errorlevel%==0 (
  powershell.exe -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0manage-keep-render-awake.ps1" -Action Disable
if errorlevel 1 (
  echo Failed to disable the task. Try running this file as administrator.
  pause
  exit /b 1
)