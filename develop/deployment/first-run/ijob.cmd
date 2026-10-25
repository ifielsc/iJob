@echo off
setlocal
powershell.exe -NoProfile -File "%~dp0instance.ps1" %*
exit /b %errorlevel%
