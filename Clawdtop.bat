@echo off
rem Starts Clawd (with no console window). Needs Java 22 or newer.
cd /d "%~dp0"
rem Opened straight from the zip? (Windows runs it from a temporary copy that disappears)
set "HERE=%~dp0"
set INZIP=
if /i not "%HERE:\AppData\Local\Temp\=%"=="%HERE%" set INZIP=1
if /i not "%HERE:.zip\=%"=="%HERE%" set INZIP=1
if defined INZIP (
  echo It looks like you opened Clawd straight from the zip file.
  echo Unzip it first: right-click the zip, pick "Extract All", then double-click Clawdtop.bat in the new folder.
  pause
  exit /b 1
)
set JAR=Clawdtop.jar
if not exist "%JAR%" set JAR=build\Clawdtop.jar
if not exist "%JAR%" (
  echo Clawdtop.jar is missing. If you downloaded the code, run build.bat first.
  pause
  exit /b 1
)
rem The first Java that's new enough: Kelp's, JAVA_HOME's, the one on the PATH, then the usual install folders
set JAVAW=
set OLDJAVA=
call :try "%APPDATA%\Kelp\runtimes\java-runtime-epsilon\bin\javaw.exe"
if not defined JAVAW if defined JAVA_HOME call :try "%JAVA_HOME%\bin\javaw.exe"
if not defined JAVAW for /f "delims=" %%j in ('where javaw 2^>nul') do if not defined JAVAW call :try "%%j"
if not defined JAVAW for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\*" "%ProgramFiles%\Java\*" "%ProgramFiles%\Microsoft\jdk-*" "%ProgramFiles%\Zulu\*" "%ProgramFiles%\Amazon Corretto\*") do if not defined JAVAW call :try "%%~d\bin\javaw.exe"
if not defined JAVAW (
  if defined OLDJAVA (
    echo The Java on this computer is too old for Clawd. He needs Java 22 or newer.
  ) else (
    echo Clawd needs Java 22 or newer, and it isn't on this computer yet.
  )
  echo Opening the free download page: get the latest "JRE" for Windows, install it, then run this again.
  start "" "https://adoptium.net/temurin/releases/?os=windows&package=jre"
  pause
  exit /b 1
)
start "" "%JAVAW%" --enable-native-access=ALL-UNNAMED -jar "%JAR%"
exit /b 0

rem :try "path\to\javaw.exe" - uses it if it's Java 22 or newer (and notes an old one)
:try
if not exist "%~1" exit /b 0
set "JX=%~dp1java.exe"
set JV=
for /f "tokens=3" %%v in ('call "%JX%" -version 2^>^&1 ^| findstr /i "version"') do if not defined JV set "JV=%%~v"
if not defined JV exit /b 0
set JM=0
for /f "delims=." %%m in ("%JV%") do set JM=%%m
if "%JM%"=="1" (
  set OLDJAVA=1
  exit /b 0
)
if %JM% GEQ 22 (
  set "JAVAW=%~1"
) else (
  set OLDJAVA=1
)
exit /b 0
