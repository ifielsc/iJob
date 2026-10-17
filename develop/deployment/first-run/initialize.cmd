@echo off
setlocal
cd /d "%~dp0"
if defined JAVA_HOME (set "IJOB_JAVA=%JAVA_HOME%\bin\java.exe") else (set "IJOB_JAVA=java.exe")
"%IJOB_JAVA%" -jar "%~dp0ijob-setup-tool-1.0.0.jar" init --bundle "%~dp0."
pause
