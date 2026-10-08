@echo off
rem Builds build\Clawdtop.jar and runs the tests. Needs Java 22 or newer (Kelp's Java 25 works, or set JAVA_HOME).
rem   build.bat         builds and tests
rem   build.bat run     builds, tests, and starts Clawd
cd /d "%~dp0"
set JAVA_BIN=%APPDATA%\Kelp\runtimes\java-runtime-epsilon\bin
if not exist "%JAVA_BIN%\javac.exe" set JAVA_BIN=%JAVA_HOME%\bin
if not exist "%JAVA_BIN%\javac.exe" (
    echo Couldn't find Java 22 or newer. Install it, or set JAVA_HOME to it.
    pause
    exit /b 1
)
if exist build\classes rmdir /s /q build\classes
if exist build\test-classes rmdir /s /q build\test-classes
"%JAVA_BIN%\javac.exe" -d build\classes src\clawdtop\*.java || (pause & exit /b 1)
"%JAVA_BIN%\jar.exe" --create --file build\Clawdtop.jar --manifest manifest.txt -C build\classes . || (pause & exit /b 1)
"%JAVA_BIN%\javac.exe" -d build\test-classes -cp build\classes src\clawdtop\*.java test\clawdtop\*.java || (pause & exit /b 1)
"%JAVA_BIN%\java.exe" -Djava.awt.headless=true --enable-native-access=ALL-UNNAMED -cp build\test-classes clawdtop.ClawdtopTest || (pause & exit /b 1)
echo Built build\Clawdtop.jar
if "%1"=="run" start "" "%JAVA_BIN%\javaw.exe" -jar build\Clawdtop.jar
