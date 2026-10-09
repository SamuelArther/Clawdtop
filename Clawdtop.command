#!/bin/sh
# Double-click on a Mac to start Clawd. (The first time, macOS may ask: see "First time on a Mac" in the README.)
cd "$(dirname "$0")" || exit 1
if ! sh ./Clawdtop.sh; then
  echo
  printf "Press Return to close this window."
  read -r _
fi
