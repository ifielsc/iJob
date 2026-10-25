@echo off
setlocal
powershell.exe -NoProfile -File "%~dp0..\support\manage.ps1" status
exit /b %errorlevel%
