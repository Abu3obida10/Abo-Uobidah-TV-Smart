@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title Abo Uobidah TV - Safe APK Installer
color 0B
echo =====================================================
echo ABO UOBIDAH TV - SAFE APK INSTALLER
echo =====================================================
echo This tool checks APK identity, version and signing certificate first.
echo It never uninstalls an existing application automatically.
echo.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0INSTALL_APK.ps1" %*
if errorlevel 1 (
  echo.
  echo Installation did not complete. Read INSTALL_LOG.txt.
  pause
  exit /b 1
)
echo.
echo Installation completed and package verification passed.
pause
endlocal
