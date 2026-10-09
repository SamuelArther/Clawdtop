@echo off
rem Starts Clawd (with no console window). Needs Java 22 or newer.
cd /d "%~dp0"
set JAR=Clawdtop.jar
if not exist "%JAR%" set JAR=build\Clawdtop.jar
if not exist "%JAR%" (
  echo Clawdtop.jar is missing. If you downloaded the code, run build.bat first.
  pause
  exit /b 1
)
set JAVAW=%APPDATA%\Kelp\runtimes\java-runtime-epsilon\bin\javaw.exe
if not exist "%JAVAW%" if defined JAVA_HOME set JAVAW=%JAVA_HOME%\bin\javaw.exe
if not exist "%JAVAW%" for /f "delims=" %%j in ('where javaw 2^>nul') do if not defined FOUND_JAVAW set FOUND_JAVAW=%%j
if not exist "%JAVAW%" if defined FOUND_JAVAW set JAVAW=%FOUND_JAVAW%
if not exist "%JAVAW%" (
  echo Clawd needs Java 22 or newer, and it isn't on this computer yet.
  echo Opening the free download page: get the latest "JDK" or "JRE" for Windows, install it, then run this again.
  start "" "https://adoptium.net/temurin/releases/?os=windows&package=jre"
  pause
  exit /b 1
)
start "" "%JAVAW%" --enable-native-access=ALL-UNNAMED -jar "%JAR%"
