#!/bin/sh
# Starts Clawd on a Mac or Linux. Needs Java 22 or newer.
cd "$(dirname "$0")" || exit 1
JAR=Clawdtop.jar
[ -f "$JAR" ] || JAR=build/Clawdtop.jar
if [ ! -f "$JAR" ]; then echo "Clawdtop.jar is missing. If you downloaded the code, run ./build.sh first."; exit 1; fi
if ! command -v java >/dev/null 2>&1; then echo "Clawd needs Java 22 or newer: get it free from https://adoptium.net"; exit 1; fi
nohup java --enable-native-access=ALL-UNNAMED -jar "$JAR" >/dev/null 2>&1 &
