@echo off
title Sable
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo Sable needs Node.js, which is not installed on this computer.
  echo Opening the download page. Install the LTS version, then double-click this file again.
  start https://nodejs.org/en/download
  pause
  exit /b 1
)
if not exist "node_modules\electron" (
  echo First run: downloading Electron. This takes a minute or two...
  call npm install
  if errorlevel 1 (
    echo.
    echo Setup failed. Check your internet connection and try again.
    pause
    exit /b 1
  )
)
call npm start
if errorlevel 1 pause
