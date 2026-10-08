# Clawdtop

> **A fan project. Not made by, affiliated with, or endorsed by Anthropic.** Clawd, Claude Code's little crab,
> belongs to Anthropic. Clawdtop is free and always will be.

A tiny desktop buddy: Clawd sits on top of your taskbar, right above the clock, and keeps you company.

- He stands around, breathes and blinks, and **his eyes follow your cursor**.
- Leave him alone for a while and he **sits down**, then **lies down**, then **falls asleep** (with little z's).
  Tap him (or move your mouse near him) and he wakes up.
- Open **VS Code, a terminal, IntelliJ** or another coding app and **his eyes light up** and he bounces, happy.
- He talks in **little quiet beeps**, and now and then he has a **tip** for what you're doing, in a little speech
  bubble (all built in, nothing from the internet). Open the **Run box (Win + R)** and he shows handy commands
  (`%temp%`, `appwiz.cpl`, `msinfo32`...). VS Code, terminals, IntelliJ, File Explorer, Task Manager and browsers have
  their own shortcuts. Click the bubble to close it, or turn tips off in his menu.
- **Give him a ride:** hold your cursor on the taskbar's top edge right next to him and he hops on and rides along
  wherever you go. Bring him back down to the taskbar and stop, and he hops off and walks home. **Shake** the cursor
  and he flies off, lands head first, lies there seeing stars, flips back up and walks home to his perch.
- **He cleans folders.** Click him and pick **Clean a folder...**: he hops onto your cursor, you open the folder in
  File Explorer, and he hops onto that window, pulls out his laptop and looks through it. Then he asks before anything
  goes: once for plain junk (temp files, Windows' thumbnail caches, unfinished downloads, Mac leftovers, empty
  folders), and one at a time for things that might matter (installers over a month old, exact "(1)" copies). For
  those, his two little hands peek over the top of the window, then he climbs up to ask. **Everything goes to the
  Recycle Bin**, so you can always put it back, and he won't touch Windows, Program Files, your whole user folder or
  a whole drive.
- Full-screen games and videos? He gets out of the way until you're back.

**Click** him to say hi. **Drag** him along the taskbar to move him. **Right-click** for his menu: beeps and tips on or off,
start with Windows, small / normal / big, back above the clock, and "Bye, Clawd" to close him.

## Running him

You need Java 22 or newer. If you have [Kelp](https://github.com/KelpSquid/kelp) and have played Minecraft 26.3 with
it, you already have Java 25 and don't need anything else.

1. Run `build.bat`. It builds `build\Clawdtop.jar` and checks everything works.
2. Run `Clawdtop.bat` to start him (or `build.bat run` to do both).

Turn on **Start with Windows** in his menu and he'll be there every time you sign in.

## The clawd command

Once he's run, any new terminal knows the `clawd` command:

| Command | What it does |
| --- | --- |
| `clawd start` | brings Clawd to your taskbar |
| `clawd stop` | sends him off for now |
| `clawd restart` | stop, then start |
| `clawd status` | whether he's running, and his settings |
| `clawd controlpanel` | a little menu to change his name, spot, size, beeps, tips and starting with Windows |
| `clawd uninstall` | removes him: stops him, takes out the command and his settings |

It's a `clawd.cmd` in `%LOCALAPPDATA%\Clawdtopin`, put on your own (user) PATH; nothing system-wide changes.
Changes from the control panel reach him within a couple of seconds.

## How he works

It's plain Java with no libraries: a see-through window that stays on top, drawn block by block like Clawd in
Claude Code (`Sprite`). `Pet` decides what he's doing, `Beeps` makes his voice on the spot, and `Foreground` asks
Windows which app is in front (through Java's own way of calling Windows, so nothing extra to install). Settings live in
`%APPDATA%\Clawdtop`.

The coding apps he knows are in `Foreground.DEV_APPS`. Missing your favorite? Add its program name there.
