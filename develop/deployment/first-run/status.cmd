@echo off
setlocal
powershell.exe -NoProfile -File "%~dp0manage.ps1" status
exit /b %errorlevel%
