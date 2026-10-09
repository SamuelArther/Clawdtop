# Clawdtop

> **A fan project. Not made by, affiliated with, or endorsed by Anthropic.** Clawd, Claude Code's little crab,
> belongs to Anthropic. Clawdtop is free and always will be.

A tiny desktop buddy: Clawd sits on top of your taskbar, right above the clock (on a Mac, just above the Dock), and
keeps you company. **Double-click him for his menu.** One click says hi; a right-click pets him.

**[Download Clawd](https://github.com/SamuelArther/Clawdtop/releases/latest)** (Windows, Mac or Linux; needs
Java 22 or newer). Unzip it first (Windows: right-click the zip > Extract All; Mac: double-click the zip), then
double-click `Clawdtop.bat` (Windows) or `Clawdtop.command` (Mac). The very first time, your computer double-checks
him: see [First time on a Mac or Windows](#first-time-on-a-mac-or-windows). He checks for a newer version of himself
when he starts, and asks before he updates.

- He stands around, breathes and blinks, and **his eyes follow your cursor**.
- Leave him alone for a while and he **sits down**, then **lies down**, then **falls asleep** (with little z's).
  Click him to wake him up. (Your cursor nearby perks him up if he's only lying down.)
- Open **VS Code, a terminal, IntelliJ** or another coding app and **his eyes light up** and he bounces, happy.
- He talks in **little quiet beeps**, and now and then he has a **tip** for what you're doing, in a little speech
  bubble (all built in, nothing from the internet). Open the **Run box (Win + R)** and he shows handy commands
  (`%temp%`, `appwiz.cpl`, `msinfo32`...). VS Code, terminals, IntelliJ, File Explorer, Task Manager and browsers have
  their own shortcuts. Click the bubble to close it, or turn tips off in his menu (Settings > Tips).
- **Give him a ride:** hold your cursor on the taskbar's top edge right next to him and he hops on and rides along
  wherever you go. Bring him back down to the taskbar and stop, and he hops off and walks home. **Shake** the cursor
  and he flies off, lands head first, lies there seeing stars, flips back up and walks home to his perch.
  **Right-click** while he's riding and he just drops down (on his feet) and walks home.
- **He can open your apps** (Windows only, off at first: turn on "Tackles the taskbar icon" in **Settings > All the options**). Start an app and its
  window stays invisible for a second while Clawd charges along the taskbar and tackles its icon: the app pops open,
  there's an explosion, and he goes flying.
- **Calm by default:** lots of his sillier antics (sneezes, flies, hiccups, yelling about Caps Lock, random tunes and more) start off; turn any of them on in **Settings > All the options** (or `clawd controlpanel` in a terminal).
- **Big surprises:** on his own he keeps to little things beside him. The big screen-wide surprises (his rocket, the
  flying carpet, the duck flood, pop-ups) happen when you pick **Make something!**, or on his own if you turn on "Big
  surprises" in **Settings > All the options**.
- **Spots for each app:** drag him somewhere while an app is in front, double-click him, and pick **Settings > Sit here
  when ... is in front** (or just for that one window or tab). He walks over whenever you switch to it, and back after.
- **Zoom past him** with the cursor and his eyes swirl while little birds fly round his head.
- **He cleans folders.** Double-click him and pick **Useful > Clean a folder...**: he hops onto your cursor, you open
  the folder in File Explorer (or Finder), and he hops onto that window, pulls out his laptop and looks through it. Then he asks before anything
  goes: once for plain junk (temp files, Windows' thumbnail caches, unfinished downloads, Mac leftovers, empty
  folders), and one at a time for things that might matter (installers over a month old, exact "(1)" copies). For
  those, his two little hands peek over the top of the window, then he climbs up to ask. **Everything goes to the
  Recycle Bin** (the Trash on a Mac), so you can always put it back, and he won't touch Windows, Program Files, your whole user folder or
  a whole drive.
- **Personality:** Chill, Bouncy, Helpful or Sleepy, picked when you meet him (or in the control panel: `clawd
  controlpanel` in a terminal).
- **Colors:** change his color in the control panel. He stays orange for a few seconds, then suddenly isn't, and
  freaks out about it (then decides he kinda likes it).
- **He codes things.** Now and then he gets his laptop out (the same moves as Clawd's laptop animation in Claude Code)
  and writes a little program of his own: a flying rainbow carpet he rides round the screen, a disco ball, a rain
  cloud he made by accident (right over himself), a rubber duck to help him debug, Mini Clawd, hello world in a real
  Windows pop-up, and well over 250 more. He rarely makes the same thing twice. Ask what he's doing and it's
  "Nothing....". The files really appear in `%APPDATA%\Clawdtop\creations` (`~/.clawdtop/creations` on a Mac or Linux) as he types them, and when one goes
  wrong he looks sorry and deletes it. Click his carpet mid-flight and see what happens. Or double-click him and pick
  **Fun > Make something!**
- **Ask him a question** (top of his menu): a small AI brain that runs on your own computer through
  [Ollama](https://ollama.com) (free), using under 2 GB of memory. It's a download of about 2 GB, so he asks first
  (in setup, or the first time you ask something that needs it), then installs it in the background (Ollama's own
  signed installer, just for you, no admin needed; on Linux he tells you the one command to install it yourself). Ollama is its own app: it stays if you uninstall him. He
  answers in his personality and never says a bad word. Setup asks how he should answer: normal (the most accurate,
  recommended) or kid-friendly (simple and gentle, for little kids), and whether he may **look things up online**
  (Wikipedia and DuckDuckGo; off unless you say yes). Math? "I wouldn't trust myself to answer right....." He opens
  Calculator, tells you the buttons, and watches: "Good job!" or "Not quite entered right...".
- **His home:** tell him what to call your computer when you meet. Move him to a new computer and he asks what the
  new place is called, and remembers everywhere he's lived.
- **Reminders and timers:** type "remind me in 10 minutes to check the oven", "remind me at 3pm to call Grandma",
  "set a timer for 5 minutes" or "start a stopwatch" in his question box. For timers he holds a tiny alarm clock and a red digital display. A **focus timer**
  (25 minutes, headphones on, no interruptions) is in his menu.
- **Run a lap:** he sprints along the taskbar, up the wall, across the top of the screen upside down, down the other
  side and home, sweating more and more. **Music time:** headphones on, bobbing to the beat.
- **His mini piano, guitar, bass and drums:** he plays songs (or makes one up), and you can play yours ("Let me play!":
  click the keys, or type A to K). **Drop a MIDI file on him** and he catches it, keeps it in his songs and plays the
  whole thing. **Fun > My songs** lists every song file he has: click one to play it. Pick how his piano sounds (Grand,
  Electric piano, Harpsichord) and his guitar (Normal, Rock, Electric) in Fun > Piano and Fun > Guitar.
- **He sings and jams:** Fun > Sing a song (or "sing Stronger") sings a song file in his little beep voice. **Jam
  sessions** (Fun > Jam session): he gets his laptop out, plugs cords into each instrument, records every part of a
  jam track one by one (every part, up to seven), slams the button, and the whole band plays every note. Song files with "jam" in the name go in his
  `songs/jams` folder; every other song file plays on his piano. Now and then he starts a jam session by himself, and
  shows it to you when it's done.
- **Movie and music buddy:** when a video or music is playing, he asks if he can watch or listen along. Say yes and
  the computer's own permission box asks you to confirm (he never looks or listens without it, and it's in Settings >
  All the options > Privacy). He settles in with popcorn and jumps (popcorn everywhere) at the sudden scary bits, or
  bops along to music. Nothing is recorded or sent anywhere. (Listening is Windows and Linux only.)
- **Rare moves:** once in a while he busts out the Club Penguin dance. If you told him you served in the military, he
  salutes you the first time you log in each day.
- **Useful bits:** "How's my computer?", an "Open..." menu for handy places, water and stretch reminders (off at
  first), a nudge when the computer's been on for a week, and a warning when a drive is nearly full.
- **Your to-do list and sticky notes:** "add homework to my list", "what's on my list", "I finished my homework" (he
  cheers). "stick a note: dentist at 4" pins a little yellow note next to him; click it when you're done.
- **Finding things:** "find my history essay" looks through your usual folders (just the names) and gives you
  **Show me** and **Open it**. When a download finishes, he tells you, with the same buttons.
- **Quick helpers you can ask for:** "clean my link" (takes the tracking junk off a link you copied), "make me a
  password", "what time is it in Tokyo", "count my words", "pizza or tacos?", "open the calculator", "save what I
  copied" (a screenshot or text, into a file on your desktop), "how long have I been on the computer today?", "keep
  my computer awake" (he sets down a steaming mug until you say "let my computer sleep", or untick it in Useful), "define curious" (if you let him look things up
  online) and "what color is this?" (point at any color on your screen: he names it and copies its code).
- **Give him files:** drop a song (.mid), a .zip or a picture right on him, or drag one onto the desktop within about
  three icons of him (Windows, or a Mac once you've let him use Finder). He dashes over and jumps as high as it takes to
  grab it, and it's off the desktop. A **song** goes in his songs, and he plays it. A **zip** he tears right open
  (RRRIP): the scraps fly out onto the desktop and its unzipped folder appears right in front of him (the zip goes in
  the Recycle Bin, or the Trash on a Mac, in case you want it back). A **picture** he slips into a folder and tucks
  away (the original is kept safe: **Useful > Pictures you gave me**), and a smaller copy, handy for emailing or
  texting, appears in front of him. Anything he can't open, he gives back.
- **Your desktop:** **Tidy my desktop** (in Useful) tackles your files into a Neat folder, sorted by type, and can put
  them all back. Nothing is ever deleted.
- **Feeling good:** "quiz me on the 7 times table", "breathe with me" (a calm minute, counted on his clock), eye breaks
  every 20 minutes (off at first), and a quick rundown of your day the first time you log in each morning.
- **Games:** full-screen game? He sits in the bottom corner, over your ammo, and watches. He also reads your game
  launchers' lists (Steam, Epic, EA, Ubisoft, GOG, Xbox, Riot) on this PC and says nice things about your games.
- **Cute stuff:** rest the cursor on him and he gets shy; swipe across his face for a boop; hiccups; a nightcap when
  he sleeps at night; a good-morning stretch.
- **Holidays:** fireworks on New Year's and the 4th of July, a heart on Valentine's Day, a ghost on Halloween, snow at
  Christmas, Easter, April Fools and Thanksgiving. On Veterans Day he wears a little army uniform, salutes, and plays
  service songs (put MIDI files in his `songs/veterans` folder).
- **Games and quick answers:** tic-tac-toe against him (he's good, but he's a crab). Ask him the time, the date,
  to flip a coin, roll a die, play rock paper scissors, or (with looking things up on) the weather.
- **Dress him up:** hats and t-shirts from the shop (earn Clawd Points by spending time with him).
- **His diary:** `clawd diary` shows what he got up to lately, in his own words.
- **No tomfoolery:** one switch for serious people. The jokes and gags stop; the useful things stay.
- Close a coding app and he looks sad for a moment.
- Full-screen videos? He gets out of the way until you're back.

**Click** him to say hi (and to wake him up: nothing else does). **Double-click** for his menu: everything he can do, the
shop, and **Settings** (beeps, tips, starting with your computer, size, where he sits, spots for the app you're in, and "Bye,
Clawd"). **Right-click** to pet him. **Drag** him along the taskbar to move him.

## Running him

The easy way: **[download the latest release](https://github.com/SamuelArther/Clawdtop/releases/latest)**, unzip it,
and double-click `Clawdtop.bat` (Windows), `Clawdtop.command` (Mac) or run `./Clawdtop.sh` (Linux). Everything works
on a Mac too, except checking your math on Calculator, hearing your computer's sound and the taskbar tackle (those are
Windows only). On Linux, folder cleaning and the desktop features don't work either.

You need Java 22 or newer (free from [Adoptium](https://adoptium.net); `Clawdtop.bat` and `Clawdtop.command` open the
page for you if it's missing or too old).

### First time on a Mac or Windows

Clawd isn't from an app store, so the first time you start him your computer asks if you're sure:

- **Mac:** double-click `Clawdtop.command`. If macOS says it "could not verify" it, click **Done**, then open
  **System Settings > Privacy & Security**, scroll down and click **Open Anyway** next to Clawdtop.command (then Open).
  Or open **Terminal**, type `sh ` (with a space), drag `Clawdtop.sh` into the window and press Return. macOS may also
  ask whether Terminal can use your Downloads folder: click OK. After the first time, a double-click is all it takes.
- **Windows:** if a blue "Windows protected your PC" box appears, click **More info**, then **Run anyway**.

If you have [Kelp](https://github.com/KelpSquid/kelp) and have played Minecraft 26.3 with it, you already have Java 25
and don't need anything else.

From the code instead:

1. Run `build.bat` (or `./build.sh`). It builds `build\Clawdtop.jar` and checks everything works.
2. Run `Clawdtop.bat` to start him (`./Clawdtop.sh` on a Mac or Linux), or `build.bat run` to do both.

Turn on **Start with Windows** in his menu (on a Mac: **Start when I log in**) and he'll be there every time you sign
in.

## The clawd command

Once he's run, any new terminal knows the `clawd` command:

| Command | What it does |
| --- | --- |
| `clawd start` | brings Clawd to your taskbar |
| `clawd stop` | sends him off for now |
| `clawd restart` | stop, then start |
| `clawd status` | whether he's running, and his settings |
| `clawd controlpanel` | a little menu: his name, spot, size, personality, color, mood right now, beeps, tips, starting with Windows |
| `clawd ask ...` | ask him something right in the terminal |
| `clawd joke` | a joke |
| `clawd diary` | what he got up to lately |
| `clawd creations` | the little programs he's coded, and where they are |
| `clawd move` | moves him to another computer on your wifi |
| `clawd version` | which version of him you have |
| `clawd update` | gets the newest version of him now |
| `clawd uninstall` | gives you a **save token**, then he says goodbye and crumbles away; the command and his settings go too |

Set him up again later and paste your save token in when he asks: he'll (sort of) remember you.

On Windows it's a `clawd.cmd` in `%LOCALAPPDATA%\Clawdtop\bin`, put on your own (user) PATH; nothing system-wide
changes. On a Mac or Linux it's `~/.local/bin/clawd` (on a Mac, a line in `~/.zprofile` lets new Terminal windows find
it).
Changes from the control panel reach him within a couple of seconds.

## Testing him

`build.bat` runs the tests with no window (and draws every mood into `build/frames`). The screen tests in
`test/clawdtop/Smoke*.java` run the real thing on a real desktop (they move the mouse, type and take screenshots), so
only run them on a computer you're not using.

## How he works

It's plain Java with no libraries: a see-through window that stays on top, drawn block by block like Clawd in
Claude Code (`Sprite`). `Pet` decides what he's doing, `Beeps` makes his voice on the spot, and `Foreground` asks
Windows which app is in front (through Java's own way of calling Windows, so nothing extra to install). Settings live in
`%APPDATA%\Clawdtop` on Windows and `~/.clawdtop` on a Mac or Linux.

The coding apps he knows are in `Foreground.DEV_APPS`. Missing your favorite? Add its program name there.
