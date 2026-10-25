@echo off
setlocal
powershell.exe -NoProfile -File "%~dp0..\support\manage.ps1" restart
exit /b %errorlevel%
