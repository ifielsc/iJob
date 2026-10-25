@echo off
setlocal
powershell.exe -NoProfile -File "%~dp0support\instance.ps1" %*
exit /b %errorlevel%
