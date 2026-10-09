#!/bin/sh
# Starts Clawd on a Mac or Linux. Needs Java 22 or newer.
cd "$(dirname "$0")" || exit 1
JAR=Clawdtop.jar
[ -f "$JAR" ] || JAR=build/Clawdtop.jar
if [ ! -f "$JAR" ]; then echo "Clawdtop.jar is missing. If you downloaded the code, run ./build.sh first."; exit 1; fi
GET="https://adoptium.net/temurin/releases/?package=jre"

# the first Java that's new enough: JAVA_HOME's, then the usual one, then (on a Mac) any installed Java 22+
JAVA=""
FOUND=""
MACJAVA=""
[ -x /usr/libexec/java_home ] && MACJAVA="$(/usr/libexec/java_home -v 22+ 2>/dev/null)"
for J in "${JAVA_HOME:+$JAVA_HOME/bin/java}" "$(command -v java 2>/dev/null)" "${MACJAVA:+$MACJAVA/bin/java}"; do
  [ -n "$J" ] && [ -x "$J" ] || continue
  V="$("$J" -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')"
  [ -n "$V" ] || continue # (a Mac's /usr/bin/java with no Java installed says nothing useful)
  M="${V%%.*}"
  [ "$M" = 1 ] && M="$(echo "$V" | cut -d. -f2)" # (old Javas: "1.8" is Java 8)
  if [ "$M" -ge 22 ] 2>/dev/null; then JAVA="$J"; break; fi
  FOUND="$V"
done

if [ -z "$JAVA" ]; then
  if [ -n "$FOUND" ]; then echo "The Java on this computer ($FOUND) is too old for Clawd. He needs Java 22 or newer."
  else echo "Clawd needs Java 22 or newer, and it isn't on this computer yet."; fi
  echo "Opening the free download page: get the latest JRE, install it, then start Clawd again."
  if [ "$(uname)" = Darwin ]; then open "$GET&os=mac"; else (xdg-open "$GET&os=linux" >/dev/null 2>&1 &); fi
  exit 1
fi
nohup "$JAVA" --enable-native-access=ALL-UNNAMED -jar "$JAR" >/dev/null 2>&1 &
echo "Clawd's on his way! (You can close this window.)"
