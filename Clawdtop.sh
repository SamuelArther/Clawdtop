#!/bin/sh
# Starts Clawd on a Mac or Linux (build him first with ./build.sh).
cd "$(dirname "$0")" || exit 1
nohup java --enable-native-access=ALL-UNNAMED -jar build/Clawdtop.jar >/dev/null 2>&1 &
