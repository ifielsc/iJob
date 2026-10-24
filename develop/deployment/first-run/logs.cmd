@echo off
setlocal
powershell.exe -NoProfile -File "%~dp0manage.ps1" logs
exit /b %errorlevel%
