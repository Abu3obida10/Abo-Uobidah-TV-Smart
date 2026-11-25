@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo Abo Uobidah TV - Release only. No source project will be uploaded.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0PUBLISH_RELEASE_ONLY.ps1" %*
if errorlevel 1 (
  echo.
  echo Release publishing failed. Read the error above.
  pause
  exit /b 1
)
echo.
pause
