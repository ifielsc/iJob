@echo off
setlocal
powershell.exe -NoProfile -File "%~dp0manage.ps1" stop
exit /b %errorlevel%
