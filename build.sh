#!/bin/sh
# Builds build/Clawdtop.jar and runs the tests, on a Mac or Linux. Needs Java 22 or newer.
#   ./build.sh         builds and tests
#   ./build.sh run     builds, tests, and starts Clawd
cd "$(dirname "$0")" || exit 1
rm -rf build/classes build/test-classes
javac --release 22 -Xlint:-options -d build/classes src/clawdtop/*.java || exit 1
jar --create --file build/Clawdtop.jar --manifest manifest.txt -C build/classes . || exit 1
javac -d build/test-classes -cp build/classes src/clawdtop/*.java test/clawdtop/*.java || exit 1
java -Djava.awt.headless=true --enable-native-access=ALL-UNNAMED -cp build/test-classes clawdtop.ClawdtopTest || exit 1
echo "Built build/Clawdtop.jar"
if [ "$1" = "run" ]; then ./Clawdtop.sh; fi
