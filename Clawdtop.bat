@echo off
rem Starts Clawd (with no console window). Build him first with build.bat.
cd /d "%~dp0"
set JAVA_BIN=%APPDATA%\Kelp\runtimes\java-runtime-epsilon\bin
if not exist "%JAVA_BIN%\javaw.exe" set JAVA_BIN=%JAVA_HOME%\bin
start "" "%JAVA_BIN%\javaw.exe" -jar build\Clawdtop.jar
