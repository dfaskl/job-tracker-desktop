@echo off
title CareerFlow temporary connection
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0connect.ps1"
echo.
pause
