Fix: starting him on Windows.

- Clawdtop.bat now uses the first Java that's new enough. Before, an older Java found first (like Java 21, or an old JAVA_HOME) made it say "too old" even with Java 25 installed. It also looks in the usual install folders.
- Opened Clawdtop.bat straight from inside the zip? It now tells you to unzip it first (Extract All), instead of confusing advice.

Get him: download the zip, unzip, double-click Clawdtop.bat (Windows) or Clawdtop.command (Mac). Needs Java 22+.

A fan project, not made by or affiliated with Anthropic.
