@echo off
setlocal
powershell.exe -NoProfile -File "%~dp0manage.ps1" start
exit /b %errorlevel%
