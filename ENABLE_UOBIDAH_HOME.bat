@echo off
chcp 65001 >nul
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0SET_UOBIDAH_HOME_SAFE.ps1"
echo.
pause
