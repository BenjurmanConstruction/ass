@echo off
title Build Sable installer
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo Building needs Node.js, which is not installed on this computer.
  echo Opening the download page. Install the LTS version, then double-click this file again.
  start https://nodejs.org/en/download
  pause
  exit /b 1
)
echo Installing build tools...
call npm install
if errorlevel 1 goto failed
echo Building the installer. This takes a few minutes...
call npm run dist
if errorlevel 1 goto failed
echo.
echo Done. The installer is in the "dist" folder.
start "" "%~dp0dist"
pause
exit /b 0
:failed
echo.
echo The build failed. If the error mentions symbolic links, right-click this file
echo and choose "Run as administrator", then try again.
pause
exit /b 1
