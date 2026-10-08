# Clawdtop

> **A fan project. Not made by, affiliated with, or endorsed by Anthropic.** Clawd, Claude Code's little crab,
> belongs to Anthropic. Clawdtop is free and always will be.

A tiny desktop buddy: Clawd sits on top of your taskbar, right above the clock, and keeps you company.

- He stands around, breathes and blinks, and **his eyes follow your cursor**.
- Leave him alone for a while and he **sits down**, then **lies down**, then **falls asleep** (with little z's).
  Move your mouse near him and he wakes up.
- Open **VS Code, a terminal, IntelliJ** or another coding app and **his eyes light up** and he bounces, happy.
- He talks in **little quiet beeps**.
- Full-screen games and videos? He gets out of the way until you're back.

**Click** him to say hi. **Drag** him along the taskbar to move him. **Right-click** for his menu: beeps on or off,
start with Windows, small / normal / big, back above the clock, and "Bye, Clawd" to close him.

## Running him

You need Java 22 or newer. If you have [Kelp](https://github.com/KelpSquid/kelp) and have played Minecraft 26.3 with
it, you already have Java 25 and don't need anything else.

1. Run `build.bat`. It builds `build\Clawdtop.jar` and checks everything works.
2. Run `Clawdtop.bat` to start him (or `build.bat run` to do both).

Turn on **Start with Windows** in his menu and he'll be there every time you sign in.

## How he works

It's plain Java with no libraries: a see-through window that stays on top, drawn block by block like Clawd in
Claude Code (`Sprite`). `Pet` decides what he's doing, `Beeps` makes his voice on the spot, and `Foreground` asks
Windows which app is in front (through Java's own way of calling Windows, so nothing extra to install). Settings live in
`%APPDATA%\Clawdtop`.

The coding apps he knows are in `Foreground.DEV_APPS`. Missing your favorite? Add its program name there.
