@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title Abo Uobidah TV - Build and Install
color 0B
echo =====================================================
echo ABO UOBIDAH TV - AUTOMATIC BUILD
echo =====================================================
echo One-command setup: Java 17 + offline-first Android SDK check + Gradle tests + APK + safe optional install.
echo Full Gradle test/build diagnostics are saved in GRADLE_TEST_LOG.txt and GRADLE_BUILD_LOG.txt.
echo The installer checks the selected device and signing certificate; it never removes old app data automatically.
echo Internet is only needed if required SDK/Gradle packages are missing; installed SDK packages are reused offline.
echo.
if not exist "%~dp0AUTO_BUILD_INSTALL.ps1" (
  echo [ERROR] AUTO_BUILD_INSTALL.ps1 is missing. Re-extract the complete ZIP.
  pause
  exit /b 1
)
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0AUTO_BUILD_INSTALL.ps1"
if errorlevel 1 (
  echo.
  echo BUILD DID NOT COMPLETE. Read SDK_MANAGER_LOG.txt, GRADLE_TEST_LOG.txt, GRADLE_BUILD_LOG.txt, or INSTALL_LOG.txt first.
  echo PowerShell transcript is saved in AUTO_BUILD_LOG.txt.
  pause
  exit /b 1
)
echo.
echo Build completed. APK: app\build\outputs\apk\debug\app-debug.apk
echo Test summary: TEST_RESULTS_LATEST.txt
endlocal
