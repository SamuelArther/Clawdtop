package clawdtop;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Clawdtop: Clawd sits on top of your taskbar, above the clock. He watches your cursor, sits, lies down, falls asleep
 * when you're away, and gets excited when you open a coding app. Click him to say hi; drag him along the taskbar to
 * move him; right-click for settings.
 */
public final class Clawdtop {
    private static final int FRAME_MS = 33;

    private Settings settings = Settings.load();
    private long settingsChanged = Settings.changed();
    private final Pet pet = new Pet(System.nanoTime());
    private final Beeps beeps = new Beeps();
    private boolean wasPlaying; // on his piano (or guitar, or drums) last frame
    private boolean fxStill;    // the ducks and explosions overlay hasn't changed since it was last drawn
    private boolean rightWasDown; // the right mouse button, last frame
    private String heldLine;      // something he wanted to say while he was asking you something
    private final Tips tips = new Tips();
    private final Bubble bubble = new Bubble();
    private String lastKind;
    private long lastTipAt = System.currentTimeMillis() - 60_000; // the first tip can come a minute in
    private final JWindow window = new JWindow();
    private final JPanel canvas;
    private Point lastMouse = new Point();
    private String app;
    private boolean devApp;
    private boolean hidden;
    private java.util.Set<String> devPrograms; // coding apps open last time he looked, to notice one closing
    private Body.State lastBody = Body.State.HOME;
    private long activeFor;        // ms the mouse has been moving lately, towards a Clawd Point every 5 minutes
    private int rubTurns;          // back-and-forth turns of the mouse over him (petting)
    private int rubDirection;
    private long rubStarted, lastPet;
    private int ticks;
    private final Body body = new Body();
    private double homeX, groundY; // his perch: the point between his feet, on the taskbar's top edge
    private CleanJob job; // a folder he's cleaning, or null
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Clawdtop work");
        t.setDaemon(true);
        return t;
    });
    private int dragFrom = Integer.MIN_VALUE;
    private int windowXAtDrag;

    Clawdtop() {
        canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(java.awt.AlphaComposite.Clear); // a see-through window: clear last frame first
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setComposite(java.awt.AlphaComposite.SrcOver);
                if (farewell) {
                    g2.translate(0, FAREWELL_ROOM * settings.unit());
                    Sprite.drawCrumbling(g2, pet, settings.unit(), pet.crumbled());
                } else if (job != null && job.sunk() > 0) Sprite.drawRising(g2, pet, settings.unit(), job.sunk()); // climbing up to ask
                else if (body.state() == Body.State.LAP) Sprite.drawSpun(g2, pet, settings.unit(), body.angle()); // up the walls
                else Sprite.drawTurned(g2, pet, settings.unit(), body.angle());
                g2.dispose();
            }
        };
        canvas.setOpaque(false);
        canvas.setBackground(new Color(0, 0, 0, 0));
        Platform.seeThrough(window);
        window.setContentPane(canvas);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false); // clicking him never takes the keyboard from what you're doing
        window.setType(java.awt.Window.Type.UTILITY);
        resize();
        listen();
    }

    private void resize() {
        int unit = settings.unit();
        window.setSize(new Dimension(Sprite.WIDTH * unit, Sprite.HEIGHT * unit));
        place();
    }

    /** On the taskbar's top edge, above the clock (or wherever he was dragged to). */
    private void place() {
        place(true);
    }

    /** Works out his spot (and puts him there, unless he's about to walk over). */
    private void place(boolean move) {
        if (inCorner) return; // watching your game from the corner: back to his spot when it's over
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Rectangle screen = window.getGraphicsConfiguration().getBounds();
        int w = window.getWidth();
        int h = window.getHeight();
        int bottom = usable.y + usable.height; // the taskbar's top when it's at the bottom of the screen
        int x = appSpotX != null ? appSpotX : settings.x() >= 0 ? settings.x() : switch (settings.spot()) {
            case "In the middle" -> screen.x + screen.width / 2 - w / 2;
            case "On the left" -> screen.x + 70;
            default -> screen.x + screen.width - 64 - w / 2; // above the clock, in the corner
        };
        x = Math.max(screen.x, Math.min(screen.x + screen.width - w, x));
        bottom -= settings.number("nudge"); // nudged up (or down) if you like
        if (move) window.setLocation(x, bottom - h + settings.unit()); // his feet just touch the taskbar
        homeX = x + Sprite.feetX() * settings.unit();
        groundY = bottom;
        if (hut != null) useItems();
    }

    private void listen() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!bubble.asking()) bubble.hide();
                if (SwingUtilities.isLeftMouseButton(e) && body.state() == Body.State.FLY) {
                    body.knockOff(); // poof, no more carpet
                    pet.carpetGone(riding);
                    Diary.write("Somebody clicked my flying carpet away. Mid-air. Rude.");
                    return;
                }
                if (SwingUtilities.isRightMouseButton(e)) rightHeldSince = System.currentTimeMillis();
                if (SwingUtilities.isLeftMouseButton(e) && body.state() == Body.State.HOME) {
                    dragFrom = e.getXOnScreen();
                    windowXAtDrag = window.getX();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragFrom == Integer.MIN_VALUE) return;
                window.setLocation(windowXAtDrag + e.getXOnScreen() - dragFrom, window.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    boolean held = rightHeldSince > 0 && System.currentTimeMillis() - rightHeldSince >= HOLD_TO_PET;
                    rightHeldSince = 0;
                    if (!held) menu().show(canvas, e.getX(), e.getY()); // a hold was petting him, not asking for the menu
                    return;
                }
                if (dragFrom == Integer.MIN_VALUE) {
                    if (job != null && SwingUtilities.isLeftMouseButton(e)) jobs().show(canvas, e.getX(), e.getY());
                    return;
                }
                boolean moved = Math.abs(e.getXOnScreen() - dragFrom) > 3;
                dragFrom = Integer.MIN_VALUE;
                if (moved) {
                    if (appSpotKey != null) { // moving his spot for the app in front
                        settings.setAppSpot(appSpotKey, window.getX());
                        appSpotX = window.getX();
                    } else {
                        if (xBeforeDrag == Integer.MIN_VALUE) xBeforeDrag = settings.x(); // (in case this was for an app)
                        settings.setX(window.getX());
                    }
                    place(); // this is home now (or he'd jump straight back to where he was)
                } else if (pet.sleepy()) {
                    pet.poke(); // just wakes him up
                } else if (pet.mood() == Pet.Mood.PIANO) {
                    pet.stopPiano(); // enough music
                } else if (pet.duckSpam()) {
                    pet.stopDucks(); // you clicked his laptop: it shuts. No more ducks
                } else if (pet.secretlyCoding()) {
                    pet.say("Nothing...."); // what are you doing? nothing.
                } else if (!clickedTooMuch()) {
                    pet.poke();
                    jobs().show(canvas, e.getX(), e.getY());
                }
            }
        };
        canvas.addMouseListener(mouse);
        // Drop a MIDI file on him: he fetches it and plays it
        new java.awt.dnd.DropTarget(canvas, new java.awt.dnd.DropTargetAdapter() {
            @Override
            public void dragEnter(java.awt.dnd.DropTargetDragEvent e) {
                if (e.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) pet.sniff();
            }

            @Override
            public void drop(java.awt.dnd.DropTargetDropEvent e) {
                try {
                    e.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    java.util.List<java.io.File> files = (java.util.List<java.io.File>) e.getTransferable().getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    java.io.File midi = files.stream().filter(Piano::isMidi).findFirst().orElse(null);
                    e.dropComplete(true);
                    if (midi == null) {
                        pet.say("Hmm, that's not music. I can only play MIDI files (.mid).");
                        return;
                    }
                    playDropped(midi);
                } catch (Exception ex) {
                    e.dropComplete(false);
                }
            }
        });
        canvas.addMouseMotionListener(mouse);
    }

    private boolean inCorner; // sitting in the corner, watching your full-screen game
    private Rectangle lastUsable; // the screen (above the taskbar) last time he looked
    private final java.util.List<Object[]> reminders = new java.util.ArrayList<>(); // {due ms, what}
    private long focusUntil;    // the focus timer runs out then (0: off)
    private long stopwatchFrom; // the stopwatch started then (0: off)

    /** Starts the stopwatch, or stops it and says how long it was. */
    private void stopwatch(boolean start) {
        if (start) {
            stopwatchFrom = System.currentTimeMillis();
            pet.say("Stopwatch started! Tell me to stop it.");
        } else if (stopwatchFrom > 0) {
            long took = System.currentTimeMillis() - stopwatchFrom;
            stopwatchFrom = 0;
            pet.say("Stop! That was " + Reminders.clock(took) + (took < 60_000 ? " (seconds)." : "."));
        } else {
            pet.say("The stopwatch isn't running. Say \"start a stopwatch\"!");
        }
    }

    /** What his little clock shows: a running timer (the soonest), or the stopwatch. */
    private void updateClock() {
        long now = System.currentTimeMillis();
        long soonest = Long.MAX_VALUE;
        for (Object[] r : reminders) if ("time's up".equals(r[1]) || "time's up!".equals(r[1])) soonest = Math.min(soonest, (Long) r[0]);
        if (soonest != Long.MAX_VALUE) pet.clock(soonest - now, false);
        else if (stopwatchFrom > 0) pet.clock(now - stopwatchFrom, true);
        else pet.clock(-1, false);
    }

    /** Reminders that are due, and the focus timer running out. */
    private void checkReminders() {
        if (hidden) return; // (watching a video: they wait till you're back, nothing gets lost)
        long now = System.currentTimeMillis();
        for (java.util.Iterator<Object[]> it = reminders.iterator(); it.hasNext(); ) {
            Object[] r = it.next();
            if (now >= (Long) r[0] && pet.remind((String) r[1])) {
                it.remove(); // (busy? he tells you in a moment)
                Diary.write("Reminded you: " + r[1]);
            }
        }
        if (focusUntil > 0 && now >= focusUntil) {
            focusUntil = 0;
            pet.focus(false, true);
            if (settings.on("earnPoints")) settings.earn(Shop.FOCUS);
            Diary.write("Kept quiet for a whole focus timer. You did great.");
        }
    }

    private void focus(boolean on) {
        if (pet.focus(on, false) || !on) focusUntil = on ? System.currentTimeMillis() + 25 * 60_000L : 0;
        else pet.say("Let me finish this first, then focus mode!");
    }
    private final Piano yourPiano = new Piano();
    private javax.sound.midi.Sequencer sequencer; // playing a whole MIDI file (his piano just shows it)
    private java.io.File sequencerFile;

    /** Plays a whole MIDI file while he plays it on his piano (null stops it). */
    private int midiRound; // which MIDI start is the current one (an older one still loading gets closed)

    private void playMidi(java.io.File file) {
        if (java.util.Objects.equals(file, sequencerFile)) return;
        if (sequencer != null) {
            sequencer.stop();
            sequencer.close();
            sequencer = null;
        }
        sequencerFile = file;
        int round = ++midiRound;
        if (file == null) return;
        worker.execute(() -> {
            try {
                javax.sound.midi.Sequencer s = javax.sound.midi.MidiSystem.getSequencer();
                s.open();
                s.setSequence(javax.sound.midi.MidiSystem.getSequence(file));
                s.start();
                SwingUtilities.invokeLater(() -> {
                    if (round == midiRound) sequencer = s;
                    else s.close(); // already stopped (or a newer one started)
                });
            } catch (Exception noMidi) {
                // no sound for it, then (he still plays along)
            }
        });
    }

    /** Where your MIDI files go for his piano (and, in "veterans", the songs he plays on Veterans Day). */
    static Path songsFolder() {
        return Settings.folder().resolve("songs");
    }

    /** The MIDI files in a folder, by name. */
    static java.util.List<java.io.File> songs(Path folder) {
        java.io.File[] files = folder.toFile().listFiles((dir, name) -> Piano.isMidi(new java.io.File(name)));
        if (files == null) return java.util.List.of();
        java.util.List<java.io.File> list = new java.util.ArrayList<>(java.util.List.of(files));
        list.sort(java.util.Comparator.comparing(java.io.File::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private long nextVeteransSong; // on Veterans Day: when he plays the next service song (0: not today)

    /** Veterans Day: a service song now and then through the day (from songs/veterans). */
    private void veteransSongs(long now) {
        java.time.LocalDate d = java.time.LocalDate.now();
        if (d.getMonthValue() != 11 || d.getDayOfMonth() != 11 || nextVeteransSong == 0 || now < nextVeteransSong) return;
        if (pet.busyNow()) return; // in a moment
        java.util.List<java.io.File> marches = songs(songsFolder().resolve("veterans"));
        nextVeteransSong = now + (60 + new java.util.Random().nextInt(60)) * 60_000L; // then again in an hour or two
        if (marches.isEmpty()) return;
        java.io.File march = marches.get(new java.util.Random().nextInt(marches.size()));
        worker.execute(() -> {
            Piano.Song song = Piano.fromMidi(march);
            if (song != null) SwingUtilities.invokeLater(() -> pet.playPiano(song));
        });
    }

    /** A MIDI file you dropped on him: read it (in the background), then he fetches it and plays it. */
    void playDropped(java.io.File midi) {
        worker.execute(() -> {
            Piano.Song song = Piano.fromMidi(midi);
            SwingUtilities.invokeLater(() -> {
                if (song == null) pet.say("I tried, but I can't read that music.");
                else if (!pet.fetch(song)) pet.say("Ooh, music! Give me a sec, I'm busy.");
                else {
                    earnFun(Shop.MIDI);
                    Diary.write("Somebody gave me music: " + song.name().replaceFirst("^your ", "") + ". I played it on my piano!");
                }
            });
        });
    }
    private final Ask askBox = new Ask();
    private final Brain brain = new Brain();
    private boolean thinking;

    /** Answers your question: math goes to Calculator (he doesn't trust himself); the rest, his brain. */
    private void answer(String question) {
        answer(question, true);
    }

    /** Answers a question (points: only the first time it's asked, not again after his brain's installed). */
    private void answer(String question, boolean firstTime) {
        if (question.toLowerCase(java.util.Locale.ROOT).matches("\\W*(help|what can you do|what do you do|commands|how do (i|you) use you)\\W*")) {
            pet.say("Things you can ask me:\nAny question (I'll think about it)\nMath like \"what's 12 times 7\" (we'll use Calculator)\n"
                    + "\"remind me in 10 minutes to stretch\"\n\"set a timer for 5 minutes\", \"start a stopwatch\"\nMore fun stuff is in my menu!");
            return;
        }
        String quick = QuickAnswers.answer(question, new java.util.Random());
        if (quick != null) {
            pet.say(quick);
            return;
        }
        if (WebSearch.aboutWeather(question)) {
            if (!settings.on("webSearch")) {
                pet.say("I can't see outside from in here!\n(Turn on \"look things up online\" in my options and I'll check.)");
                return;
            }
            worker.execute(() -> {
                String w = WebSearch.weather();
                SwingUtilities.invokeLater(() -> pet.say(w == null ? "I tried to look outside, but the internet said no."
                        : "Right now it's " + w + ".\n" + weatherQuip(w)));
            });
            return;
        }
        int watch = Reminders.stopwatch(question);
        if (watch != 0) {
            stopwatch(watch > 0);
            return;
        }
        if (firstTime && settings.on("earnPoints")) settings.earn(Shop.ASK);
        Reminders.Reminder reminder = Reminders.parse(question);
        if (reminder != null) {
            reminders.add(new Object[] {System.currentTimeMillis() + reminder.inMs(), reminder.what()});
            pet.say(reminder.what().equals("time's up!") ? "Timer set for " + reminder.when() + "! Tick tock." : "Okay! I'll remind you in " + reminder.when() + ".");
            return;
        }
        if (Reminders.soundsLikeOne(question)) {
            pet.say("I can only do reminders like these:\n\"remind me in 10 minutes to stretch\"\n\"set a timer for 5 minutes\"\n(Times like \"at 5pm\" or \"tomorrow\" are too tricky for me.)");
            return;
        }
        MathHelp.Problem sum = MathHelp.parse(question);
        if (sum != null) {
            mathHelp(sum, 0);
            return;
        }
        if (MathHelp.tooTricky(question)) {
            pet.say("I wouldn't trust myself with that one.....\nCalculator can do it though! It has buttons for\npercent, square roots and powers.");
            Useful.open("calc");
            return;
        }
        if (thinking) {
            pet.say("Still thinking about the last one! One at a time.");
            return;
        }
        thinking = true;
        String model = Brain.model(settings.choice("brain"));
        pet.think(true);
        pet.say("Hmm, let me think...");
        boolean web = settings.on("webSearch"), kid = settings.on("kidFriendly");
        Thread t = new Thread(() -> {
            WebSearch.Found found = null;
            String problem = "broken", reply = null;
            try {
                found = web ? WebSearch.lookUp(question) : null;
                problem = !brain.running() ? "no ollama" : !brain.has(model) ? "no brain" : null;
                reply = problem == null ? brain.ask(question, model, settings.personality(), kid, settings.name(), found) : null;
            } catch (RuntimeException oops) {
                problem = null; // (he just says his brain froze)
                reply = null;
            }
            WebSearch.Found looked = found;
            String trouble = problem, answer = reply;
            SwingUtilities.invokeLater(() -> {
                WebSearch.Found found2 = looked;
                String problem2 = trouble, reply2 = answer;
                thinking = false;
                pet.think(false);
                if (problem2 != null && found2 != null && !kid) {
                    // no brain yet, but he looked it up
                    pet.say(Brain.wrap("I looked it up! " + Brain.clean(found2.text()) + " (from " + found2.source() + ")", 46));
                } else if (problem2 != null) {
                    // no brain yet: he installs it (with a notice), then answers
                    pet.say("I need my brain for that! Getting it ready now...\nI'll answer as soon as it's done.");
                    prepareBrain(() -> answer(question, false));
                } else if (reply2 == null) {
                    pet.say("Hmm... my brain froze. Try again?");
                } else {
                    pet.say(Brain.wrap(reply2, 46));
                }
            });
        }, "clawd-brain");
        t.setDaemon(true);
        t.start();
    }

    /** A game of tic-tac-toe against him. */
    private TicTacToe game; // the game on the go, if there is one

    private void ticTacToe() {
        if (game != null && game.showing()) return;
        pet.say("You're X. I'm O. Good luck. (You'll need it.)");
        game = new TicTacToe(new java.util.Random());
        game.show(head(), screenBounds(), result -> {
            String[] lines = switch (result) {
                case "you" -> new String[] {"Nooo! Best of three?", "You won?! I demand a rematch.", "Okay, you're good at this."};
                case "clawd" -> new String[] {"Crab victory!", "I win! Crabs are great at corners.", "Ha! Good game though."};
                default -> new String[] {"A tie! Great minds think alike.", "Nobody wins. Everybody's happy?"};
            };
            settings.addOne("ttt-" + result);
            int you = settings.count("ttt-you"), him = settings.count("ttt-clawd"), ties = settings.count("ttt-tie");
            String score = you == him ? "We're tied, " + you + " to " + him + "." : you > him ? "You're winning, " + you + " to " + him + "." : "I'm winning, " + him + " to " + you + ".";
            pet.say(lines[new java.util.Random().nextInt(lines.length)] + "\n" + score + (ties > 0 ? " (" + ties + (ties == 1 ? " tie)" : " ties)") : ""));
            if (result.equals("you")) pet.ask("happy");
            earnFun(Shop.GAME);
            Diary.write(switch (result) {
                case "you" -> "Lost at tic-tac-toe. Rematch pending.";
                case "clawd" -> "Won at tic-tac-toe. Undefeated crab.";
                default -> "Tied at tic-tac-toe.";
            });
        });
    }

    /** Something to say about the weather. */
    static String weatherQuip(String w) {
        String s = w.toLowerCase(java.util.Locale.ROOT);
        if (s.contains("thunder")) return "Thunder! I'm hiding under the taskbar.";
        if (s.contains("rain") || s.contains("drizzle") || s.contains("shower")) return "Don't forget an umbrella! (I'd lend you mine, but I'm 4 pixels tall.)";
        if (s.contains("snow")) return "SNOW?! Snow day?!";
        if (s.contains("sun") || s.contains("clear")) return "Nice day for it! Maybe go outside for a bit?";
        if (s.contains("fog") || s.contains("mist")) return "Spooky.";
        if (s.contains("cloud") || s.contains("overcast")) return "A bit gray. Good coding weather.";
        return "Weather: confirmed.";
    }

    private boolean brainBusy; // installing his brain right now
    private final java.util.List<Runnable> afterBrain = new java.util.ArrayList<>(); // questions waiting for it
    private String lastBrainNote; // what he last said about installing (said again if he was still in his box)

    /** Gets his brain ready in the background (installing Ollama and the model if needed), then runs then (if any). */
    void prepareBrain(Runnable then) {
        if (then != null) afterBrain.add(then);
        if (brainBusy) return;
        brainBusy = true;
        String model = Brain.model(settings.choice("brain"));
        Thread t = new Thread(() -> {
            boolean ok = false;
            try {
                ok = BrainInstall.ensure(brain, model, note -> SwingUtilities.invokeLater(() -> {
                    lastBrainNote = note;
                    if (!boxed) pet.say(note);
                }));
            } catch (RuntimeException failed) {
                ok = false;
            }
            boolean ready = ok;
            SwingUtilities.invokeLater(() -> {
                brainBusy = false;
                java.util.List<Runnable> waiting = new java.util.ArrayList<>(afterBrain);
                afterBrain.clear();
                if (ready && !waiting.isEmpty()) waiting.forEach(Runnable::run);
                else if (ready) pet.say("My brain is ready! Ask me anything.");
                else if (!waiting.isEmpty()) pet.say("I couldn't get my brain working this time.\nIs the internet on? Ask me again in a bit.");
            });
        }, "clawd-brain-install");
        t.setDaemon(true);
        t.start();
    }

    /**
     * A math question: he doesn't trust himself, so he opens Calculator, says which buttons to press, and watches it.
     * Right: "Good job!". Wrong: "Not quite entered right..." (and he watches for another go, up to three).
     */
    private Process mathWatcher; // watching Calculator for the current sum (an older one gets stopped)
    private int mathRound;

    private void mathHelp(MathHelp.Problem sum, int tries) {
        if (tries == 0 && !MathHelp.possible(sum)) {
            pet.say("Ooh, dividing by zero! Even Calculator can't do that one.\n(Nobody can. It's a math rule.)");
            return;
        }
        if (tries == 0) {
            pet.say("I wouldn't trust myself to answer right.....\nLet's ask Calculator! Press:\n" + sum.buttons());
            Useful.open("calc");
        }
        if (!Platform.WINDOWS) return; // (watching Calculator's display only works on Windows)
        if (mathWatcher != null) mathWatcher.destroy(); // a new sum: stop watching for the old one
        int round = ++mathRound;
        Thread t = new Thread(() -> {
            String result = "TIMEOUT";
            try {
                String encoded = java.util.Base64.getEncoder().encodeToString(MathHelp.watcherScript().getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
                Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                        "-WindowStyle", "Hidden", "-EncodedCommand", encoded).redirectErrorStream(true).start();
                SwingUtilities.invokeLater(() -> { if (round == mathRound) mathWatcher = p; else p.destroy(); });
                for (String line : new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                    if (line.startsWith("DONE|") || line.equals("CLOSED") || line.equals("TIMEOUT")) result = line;
                }
            } catch (IOException e) {
                result = "TIMEOUT";
            }
            String got = result;
            SwingUtilities.invokeLater(() -> {
                if (round != mathRound) return; // an old sum
                mathWatcher = null;
                if (!got.startsWith("DONE|")) return; // closed it, or gave up: that's fine
                double shown = MathHelp.shown(got.substring(got.lastIndexOf('|') + 1));
                if (sum.right(shown)) {
                    pet.say("Good job!");
                    pet.ask("happy");
                } else {
                    pet.say("Not quite entered right...\nTry: " + sum.buttons());
                    if (tries < 2) mathHelp(sum, tries + 1);
                }
            });
        }, "clawd-calculator");
        t.setDaemon(true);
        t.start();
    }

    private java.util.List<String> games;          // your game library (looked up once, in the background)
    private boolean lookingAtGames;

    private final java.util.Set<String> gamesMentioned = new java.util.HashSet<>(); // so it's a different game each time
    private long lastAdmired;     // when he last said something about your games
    private int admiredToday;
    private java.time.LocalDate admiredOn;

    /**
     * Says something nice about your games: while a launcher's open, a different game every couple of minutes (up to 8
     * a day); otherwise now and then, once a day.
     */
    private void admireGames(String why) {
        if (!settings.on("games") || job != null || lookingAtGames || pet.busyNow()) return;
        long now = System.currentTimeMillis();
        java.time.LocalDate today = java.time.LocalDate.now();
        if (!today.equals(admiredOn)) {
            admiredOn = today;
            admiredToday = 0;
        }
        String key = "games:" + why + ":" + today;
        if (why.equals("launcher") ? admiredToday >= 8 || now - lastAdmired < 150_000 : settings.seen(key)) return;
        if (games != null) {
            String nice = Games.compliment(games, new java.util.Random(), gamesMentioned);
            if (!why.equals("launcher")) settings.once(key);
            lastAdmired = now;
            admiredToday++;
            if (nice != null) pet.say(nice);
            return;
        }
        lookingAtGames = true;
        worker.execute(() -> {
            java.util.List<String> found = Games.find();
            SwingUtilities.invokeLater(() -> {
                games = found;
                lookingAtGames = false;
                admireGames(why);
            });
        });
    }

    private Creation riding; // the flying carpet he's on

    /** Picks something for him to code (one he hasn't made before, if there are any left), and he gets to it. */
    private void makeSomething() {
        Creation c = Creation.pick(new java.util.Random(), settings.made(), settings.lastMade());
        if (pet.create(c)) settings.addMade(c.id());
    }

    /** Things he codes: the file fills in as he types; then what he made does its thing (or gets deleted). */
    private void creations() {
        if (pet.takeWantsToCreate() && job == null && body.state() == Body.State.HOME && !hidden && !inCorner) makeSomething();
        Creation typing = pet.coding();
        if (typing != null && ticks % 20 == 0) writeCreation(typing, pet.codingProgress());
        Creation made = pet.takeMade();
        if (made != null && settings.on("earnPoints")) settings.earn(Shop.MADE);
        if (made != null) Diary.write("Coded " + made.file() + ". " + made.done());
        if (made != null) {
            writeCreation(made, 1);
            switch (made.effect()) {
                case CARPET -> {
                    riding = made;
                    body.flyCarpet();
                }
                case ROCKET -> body.rocketRide();
                case POPUP -> { if (!hidden && !inCorner) Useful.popup(made.file(), made.done()); } // (never over your game)
                case DUCKS -> { if (!hidden && !inCorner) dropDuck(); } // the first one, from the middle of the screen
                default -> { }
            }
        }
        if (pet.duckSpam() && ticks % 7 == 0) dropDuck();
        explosion.tick(FRAME_MS);
        if (ducks.active() || explosion.active()) {
            Rectangle screen = screenBounds();
            ducks.tick(FRAME_MS, groundY, screen.x, screen.x + screen.width);
            boolean still = ducks.settled() && !explosion.active();
            if (duckWindow != null && (!still || !fxStill)) duckWindow.repaint(); // (once they've all landed: drawn once, then left be)
            fxStill = still;
        } else if (duckWindow != null && duckWindow.isVisible()) {
            duckWindow.setVisible(false);
        }
        Creation gone = pet.takeDeleted();
        if (gone != null && gone.effect() == Creation.Effect.DUCKS) ducks.poof();
        if (gone != null) Diary.write("Deleted " + gone.file() + ". It never happened. (" + gone.after() + ")");
        if (gone != null) worker.execute(() -> {
            try {
                java.nio.file.Files.deleteIfExists(Settings.creations().resolve(gone.savedAs()));
            } catch (java.io.IOException e) {
                // still there; no harm
            }
        });
    }

    private final DuckRain ducks = new DuckRain();
    private final Explosion explosion = new Explosion();
    private javax.swing.JWindow duckWindow; // see-through, over the whole screen, for big things: ducks, explosions

    /** One more duck, from the middle of the screen. */
    private void dropDuck() {
        showFx();
        Rectangle screen = screenBounds();
        ducks.spawn(screen.x + screen.width / 2.0, screen.y + screen.height / 2.0);
    }

    /** Shows the see-through window for big things (ducks, explosions), over the screen above the taskbar. */
    private void showFx() {
        Rectangle screen = screenBounds();
        if (duckWindow == null) {
            duckWindow = new javax.swing.JWindow();
            Platform.seeThrough(duckWindow);
            javax.swing.JPanel panel = new javax.swing.JPanel() {
                @Override
                protected void paintComponent(java.awt.Graphics g) {
                    java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                    g2.setComposite(java.awt.AlphaComposite.Clear);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setComposite(java.awt.AlphaComposite.SrcOver);
                    ducks.paint(g2, duckWindow.getX(), duckWindow.getY(), Math.max(3, settings.unit()));
                    explosion.paint(g2, duckWindow.getX(), duckWindow.getY(), settings.unit() / 4.0);
                    g2.dispose();
                }
            };
            panel.setOpaque(false);
            duckWindow.setContentPane(panel);
            duckWindow.setAlwaysOnTop(true);
            duckWindow.setFocusableWindowState(false);
            duckWindow.setType(java.awt.Window.Type.UTILITY);
        }
        duckWindow.setBounds(screen.x, screen.y, screen.width, (int) Math.round(groundY) - screen.y + 2);
        if (!duckWindow.isVisible()) duckWindow.setVisible(true);
    }

    /** Writes as much of a creation's file as he's typed so far (progress 0 to 1). */
    private void writeCreation(Creation c, double progress) {
        String code = c.code();
        String typed = code.substring(0, (int) Math.round(code.length() * Math.max(0, Math.min(1, progress))));
        worker.execute(() -> {
            try {
                java.nio.file.Files.createDirectories(Settings.creations());
                java.nio.file.Files.writeString(Settings.creations().resolve(c.savedAs()), typed);
            } catch (java.io.IOException e) {
                // couldn't save it; he still had fun
            }
        });
    }

    /** What you can ask him to do (left-click him). */
    private JPopupMenu jobs() {
        JPopupMenu menu = new JPopupMenu();
        boolean free = job == null && body.state() == Body.State.HOME;
        if (job != null) {
            JMenuItem stop = new JMenuItem("Stop cleaning");
            stop.addActionListener(e -> job.stop(body, pet));
            menu.add(stop);
        }
        if (settings.on("askMe") && job == null) {
            JMenuItem ask = new JMenuItem("Ask me a question...");
            ask.addActionListener(e -> askBox.show("Ask me anything!", head(), screenBounds(), this::answer));
            menu.add(ask);
        }

        // Fun
        javax.swing.JMenu fun = new javax.swing.JMenu("Fun");
        if (free) {
            JMenuItem make = new JMenuItem("Make something!");
            make.addActionListener(e -> makeSomething());
            fun.add(make);
            javax.swing.JMenu piano = new javax.swing.JMenu("Piano");
            JMenuItem any = new JMenuItem("Play me something!");
            any.addActionListener(e -> pet.playPiano(new java.util.Random().nextInt(3) == 0 ? null : Piano.SONGS[new java.util.Random().nextInt(Piano.SONGS.length)]));
            piano.add(any);
            for (Piano.Song song : Piano.SONGS) {
                JMenuItem item = new JMenuItem(song.name());
                item.addActionListener(e -> {
                    if (pet.playPiano(song)) earnFun(Shop.SONG);
                });
                piano.add(item);
            }
            java.util.List<java.io.File> yours = songs(songsFolder());
            if (!yours.isEmpty()) {
                piano.addSeparator();
                for (java.io.File f : yours) {
                    JMenuItem item = new JMenuItem(f.getName().replaceAll("(?i)\\.midi?$", ""));
                    item.addActionListener(e -> playDropped(f));
                    piano.add(item);
                }
            }
            JMenuItem folder = new JMenuItem("Your songs folder (put MIDI files in it)...");
            folder.addActionListener(e -> {
                try {
                    java.nio.file.Files.createDirectories(songsFolder());
                    java.awt.Desktop.getDesktop().open(songsFolder().toFile());
                } catch (Exception ignored) {
                    // no file browser here
                }
            });
            piano.add(folder);
            JMenuItem mine = new JMenuItem("Let me play!");
            mine.addActionListener(e -> yourPiano.show(head(), screenBounds(), note -> {
                beeps.piano(note, 400); // you are playing it: it always makes a sound
                pet.listened();
            }));
            piano.addSeparator();
            piano.add(mine);
            fun.add(piano);
            for (Piano.Instrument inst : new Piano.Instrument[] {Piano.Instrument.GUITAR, Piano.Instrument.BASS, Piano.Instrument.DRUMS}) {
                javax.swing.JMenu menuFor = new javax.swing.JMenu(switch (inst) {
                    case GUITAR -> "Guitar";
                    case BASS -> "Bass";
                    default -> "Drums";
                });
                JMenuItem rock = new JMenuItem(inst == Piano.Instrument.DRUMS ? "Play me a beat!" : "Play me something!");
                rock.addActionListener(e -> {
                    if (pet.play(inst, null)) earnFun(Shop.SONG);
                });
                menuFor.add(rock);
                for (Piano.Song song : inst == Piano.Instrument.DRUMS ? Piano.BEATS : Piano.SONGS) {
                    JMenuItem item = new JMenuItem(song.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT) + song.name().substring(1));
                    item.addActionListener(e -> {
                        if (pet.play(inst, song)) earnFun(Shop.SONG);
                    });
                    menuFor.add(item);
                }
                fun.add(menuFor);
            }
            JMenuItem lap = new JMenuItem("Run a lap!");
            lap.addActionListener(e -> {
                if (pet.lap()) {
                    body.runLap();
                    Diary.write("Ran a lap around the whole screen. Up the walls and everything. Personal best!");
                    earnFun(Shop.LAP);
                }
            });
            fun.add(lap);
            JMenuItem ttt = new JMenuItem("Tic-tac-toe!");
            ttt.addActionListener(e -> ticTacToe());
            fun.add(ttt);
            JMenuItem music = new JMenuItem("Music time!");
            music.addActionListener(e -> pet.vibe());
            fun.add(music);
        }
        JMenuItem joke = new JMenuItem("Tell me a joke");
        joke.addActionListener(e -> tellJoke());
        fun.add(joke);
        if (settings.owns("dancing")) {
            JMenuItem dance = new JMenuItem("Dance!");
            dance.addActionListener(e -> pet.dance());
            fun.add(dance);
        }
        menu.add(fun);

        // Useful
        javax.swing.JMenu useful = new javax.swing.JMenu("Useful");
        if (job == null) {
            JMenuItem clean = new JMenuItem(Platform.WINDOWS ? "Clean a folder..." : "Clean a folder (Windows only for now)");
            clean.setEnabled(Platform.WINDOWS);
            clean.addActionListener(e -> startCleaning());
            useful.add(clean);
        }
        JMenuItem focusItem = new JMenuItem(focusUntil > 0 ? "Stop the focus timer (" + Math.max(1, (focusUntil - System.currentTimeMillis()) / 60_000) + " min left)" : "Focus timer (25 min)");
        focusItem.addActionListener(e -> focus(focusUntil == 0));
        useful.add(focusItem);
        JMenuItem watchItem = new JMenuItem(stopwatchFrom > 0 ? "Stop the stopwatch" : "Start a stopwatch");
        watchItem.addActionListener(e -> stopwatch(stopwatchFrom == 0));
        useful.add(watchItem);
        JMenuItem checkup = new JMenuItem("How's my computer?");
        checkup.addActionListener(e -> worker.execute(() -> {
            String report = Useful.checkup();
            SwingUtilities.invokeLater(() -> pet.say(report));
        }));
        useful.add(checkup);
        javax.swing.JMenu open = new javax.swing.JMenu("Open...");
        for (String[] thing : Useful.OPENABLE) {
            JMenuItem item = new JMenuItem(thing[0]);
            item.addActionListener(e -> Useful.open(thing[1]));
            open.add(item);
        }
        useful.add(open);
        menu.add(useful);

        menu.addSeparator();
        menu.add(shop());
        return menu;
    }

    /** The shop: what Clawd Points buy (and putting on what he already has). */
    private javax.swing.JMenu shop() {
        javax.swing.JMenu shop = new javax.swing.JMenu("Shop (" + settings.points() + " Clawd Points)");
        for (Shop.Kind kind : Shop.Kind.values()) {
            javax.swing.JMenu section = new javax.swing.JMenu(switch (kind) {
                case HAT -> "Hats";
                case SHIRT -> "Shirts";
                case HUT -> "Huts";
                case TRICK -> "Tricks";
            });
            String slot = switch (kind) {
                case HAT -> "hat";
                case SHIRT -> "shirt";
                case HUT -> "hut";
                case TRICK -> null;
            };
            for (Shop.Item item : Shop.ITEMS) {
                if (item.kind() != kind) continue;
                boolean owned = settings.owns(item.id());
                JMenuItem entry;
                if (owned && slot != null) {
                    boolean on = settings.wearing(slot).equals(item.id());
                    entry = new JCheckBoxMenuItem(item.name(), on);
                    entry.addActionListener(e -> {
                        settings.setWearing(slot, on ? "" : item.id());
                        useItems();
                    });
                } else if (owned) {
                    entry = new JMenuItem(item.name() + " (learned!)");
                    entry.setEnabled(false);
                } else {
                    entry = new JMenuItem(item.name() + " - " + item.price() + " points: " + item.about());
                    entry.setEnabled(settings.points() >= item.price());
                    entry.addActionListener(e -> {
                        if (Shop.buy(settings, item)) {
                            useItems();
                            pet.poke();
                            bubble.show("Yay, " + item.name().toLowerCase(java.util.Locale.ROOT) + "! Thank you!", head(), screenBounds());
                        }
                    });
                }
                section.add(entry);
            }
            shop.add(section);
        }
        return shop;
    }

    private final Hut hut = new Hut();

    /** Clawd Points for time together, rides, jobs done, and petting (rubbing the mouse back and forth over him). */
    private void earnPoints(Point mouse, boolean moved) {
        if (moved) activeFor += FRAME_MS;
        if (activeFor >= 5 * 60_000) {
            activeFor = 0;
            settings.earn(Shop.TIME);
        }
        Body.State state = body.state();
        if (state == Body.State.RIDE && lastBody != Body.State.RIDE) {
            settings.earn(Shop.RIDE);
            int hourNow = java.time.LocalTime.now().getHour();
            if (hourNow != rideDiaryHour) { // (once an hour, at most)
                rideDiaryHour = hourNow;
                Diary.write("Rode the cursor around. Wheee!");
            }
        }
        if (state == Body.State.DIZZY && lastBody == Body.State.FALL && !movingOut) Diary.write("Landed on my head. Saw stars. I'm fine.");
        lastBody = state;
        if (job != null && job.step() == CleanJob.Step.DONE && job.stepTimeIsNew()) settings.earn(Shop.JOB);
        // Petting: the mouse rubbing back and forth over him
        long now = System.currentTimeMillis();
        if (head().contains(mouse) && moved && state == Body.State.HOME) {
            int dir = Integer.signum(mouse.x - lastRubX);
            if (dir != 0 && dir != rubDirection) {
                if (rubTurns == 0) rubStarted = now;
                rubTurns++;
                rubDirection = dir;
            }
            if (rubTurns >= 4 && now - rubStarted < 2000 && now - lastPet > 2500) {
                lastPet = now;
                rubTurns = 0;
                pet.petted();
                if (settings.petsToday() < Shop.PETS_A_DAY) {
                    settings.countPet();
                    settings.earn(Shop.PET);
                }
            }
        }
        if (now - rubStarted > 2000) rubTurns = 0;
        lastRubX = mouse.x;
    }

    private int lastRubX;
    private int rideDiaryHour = -1;
    private int funToday;                  // points from games and songs today (there's a limit, so it's not a points farm)
    private java.time.LocalDate funDay;

    /** Points for playing (songs, games, laps): up to 60 a day. */
    private void earnFun(int points) {
        java.time.LocalDate today = java.time.LocalDate.now();
        if (!today.equals(funDay)) {
            funDay = today;
            funToday = 0;
        }
        int give = Math.min(points, 60 - funToday);
        if (give <= 0) return;
        funToday += give;
        settings.earn(give);
    }
    private static final long HOLD_TO_PET = 500;
    private long rightHeldSince;   // the right button is held down on him: petting

    /** Holding the right button on him pets him: hearts while you hold, points once per pet (not for spamming). */
    private void holdPet() {
        if (rightHeldSince == 0 || System.currentTimeMillis() - rightHeldSince < HOLD_TO_PET) return;
        if (pet.mood() != Pet.Mood.LOVED) {
            pet.petted();
            long now = System.currentTimeMillis();
            if (now - lastPet > 4000 && settings.petsToday() < Shop.PETS_A_DAY) {
                settings.countPet();
                settings.earn(Shop.HOLD_PET);
            }
            lastPet = now;
        }
    }
    private final java.util.ArrayDeque<Long> clicks = new java.util.ArrayDeque<>();
    private boolean huffed;      // stomped off: say something when he's back
    private Boolean capsWasOn;
    private Power.State battery;
    private long lastMoved = System.currentTimeMillis(); // the last time the mouse moved
    private long startedAt = System.currentTimeMillis();
    private boolean birthdayHiding;   // hiding for the birthday surprise
    private long birthdayHideUntil;

    /**
     * You've just come back: the program just started, or the mouse moved after a long while (a new login, or waking
     * the computer). He might have missed you, and it might be your birthday.
     */
    private final java.util.ArrayDeque<Runnable> greetings = new java.util.ArrayDeque<>(); // said one at a time

    private void cameBack(long awayFor) {
        if (pet.sleepy()) pet.ask("awake"); // you're back! (he wakes up for it)
        greetings.clear();
        long gap = System.currentTimeMillis() - settings.lastSeen();
        if (settings.lastSeen() > 0 && gap >= 2 * 86_400_000L && settings.on("missedYou")) {
            String missed = "Hi.... I missed you..... you've been gone for " + Settings.howLong(gap) + "...."
                    + (settings.homeNamed() ? "\n" + settings.home() + " was so quiet without you." : "");
            greetings.add(() -> pet.say(missed));
        }
        int hourNow = java.time.LocalTime.now().getHour();
        String morningKey = "morning:" + java.time.LocalDate.now();
        if (hourNow >= 5 && hourNow < 12 && settings.on("morning") && !settings.seen(morningKey)) {
            greetings.add(() -> { if (pet.morning(settings.name())) settings.once(morningKey); });
        }
        greetHoliday();
        settings.setLastSeen(System.currentTimeMillis());
        int year = java.time.LocalDate.now().getYear();
        if (settings.birthdayToday() && settings.on("birthday") && settings.once("birthday:" + year)) {
            // the surprise: he's nowhere to be seen... for five seconds
            birthdayHiding = true;
            birthdayHideUntil = System.currentTimeMillis() + 5000;
            window.setVisible(false);
        }
    }

    /** Veterans Day and holidays (when he starts, when you come back, and at midnight if you're up). */
    private void greetHoliday() {
        java.time.LocalDate todayNow = java.time.LocalDate.now();
        boolean veterans = todayNow.getMonthValue() == 11 && todayNow.getDayOfMonth() == 11;
        if (veterans && !settings.seen("veterans:" + todayNow.getYear())) {
            greetings.add(() -> {
                if (pet.salute()) {
                    settings.once("veterans:" + todayNow.getYear());
                    nextVeteransSong = System.currentTimeMillis() + 7000; // and straight after the salute, a song
                }
            });
        } else if (veterans && nextVeteransSong == 0) {
            nextVeteransSong = System.currentTimeMillis() + 60_000;
        }
        Holidays.Holiday holiday = Holidays.on(todayNow);
        if (holiday != null && settings.on("holidays") && !settings.seen(holiday.id() + ":" + todayNow.getYear())) {
            greetings.add(() -> {
                if (!settings.seen(holiday.id() + ":" + todayNow.getYear()) && pet.celebrate(holiday.line(), holiday.show())) {
                    Diary.write(holiday.line().split("\n")[0]);
                    settings.once(holiday.id() + ":" + todayNow.getYear());
                }
            });
        }
    }

    /** Puts his window where his body is right now (before showing him, so he doesn't flash at home first). */
    private void snapToBody() {
        snapTo(body.x(), body.y());
    }

    private void snapTo(double feetX, double feetY) {
        int unit = settings.unit();
        window.setLocation((int) Math.round(feetX - Sprite.feetX() * unit), (int) Math.round(feetY - window.getHeight() + unit));
    }

    /** After hiding for five seconds: he drops in from the top of the screen with his party hat, cake and blower. */
    private void birthdaySurprise() {
        birthdayHiding = false;
        Rectangle screen = screenBounds();
        body.dropIn(homeX, screen.y - window.getHeight());
        snapToBody();
        window.setVisible(true);
        pet.setBirthdayToday(true);
        pendingBirthday = true;
    }

    private boolean pendingBirthday; // drop in first, then the party once he's landed
    private Point zoomFrom;
    private long zoomAt;

    /** The cursor zooming right past him, really fast: he spins round. */
    private void checkZoom(Point mouse) {
        long now = System.currentTimeMillis();
        if (dragFrom != Integer.MIN_VALUE) { // you're dragging him: the cursor's on him the whole time, that's no boop
            zoomFrom = null;
            return;
        }
        if (zoomFrom != null) {
            double speed = zoomFrom.distance(mouse) / Math.max(1, now - zoomAt) * 1000; // px a second
            Rectangle near = head();
            near.grow(40, 40);
            // Did the cursor's path cross his face? Then it's a boop. Zooming past close by spins him round.
            double eyesX = window.getX() + Sprite.eyesX() * settings.unit(), eyesY = window.getY() + Sprite.eyesY() * settings.unit();
            boolean acrossFace = false;
            for (int k = 0; k <= 12 && body.state() == Body.State.HOME; k++) {
                double px = zoomFrom.x + (mouse.x - zoomFrom.x) * k / 12.0, py = zoomFrom.y + (mouse.y - zoomFrom.y) * k / 12.0;
                if (Math.abs(px - eyesX) < 7 * settings.unit() && Math.abs(py - eyesY) < 4 * settings.unit()) acrossFace = true;
            }
            boolean fromOutside = Math.abs(zoomFrom.x - eyesX) >= 7 * settings.unit() || Math.abs(zoomFrom.y - eyesY) >= 4 * settings.unit();
            if (acrossFace && fromOutside && speed > 450) pet.booped();
            else if (speed > 4000 && near.contains(mouse)) pet.spin();
        }
        zoomFrom = mouse;
        zoomAt = now;
    }

    /** Things that happen at certain times: late at night, Monday mornings, Friday afternoons, and friendship days. */
    private void checkTimes() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDate today = now.toLocalDate();
        int hour = now.getHour();
        if (!today.equals(lastDay)) { // a new day (up past midnight?): birthday hat on or off, and any holiday
            if (lastDay != null) {
                pet.setBirthdayToday(settings.birthdayToday());
                greetHoliday();
            }
            lastDay = today;
        }
        if (focusUntil > 0) return; // shh: focus mode
        if ((hour >= 23 || hour < 4) && settings.on("lateNight") && settings.once("late:" + (hour < 4 ? today.minusDays(1) : today))) {
            pet.say("It's late... maybe bed soon?");
        } else if (now.getDayOfWeek() == java.time.DayOfWeek.MONDAY && hour >= 6 && hour < 12 && settings.on("monday") && settings.once("monday:" + today)) {
            pet.say("Ugh. Monday.");
        } else if (now.getDayOfWeek() == java.time.DayOfWeek.FRIDAY && hour >= 15 && settings.on("friday") && settings.once("friday:" + today)) {
            pet.party("It's FRIDAY!!");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(settings.metDate(), today);
        for (long milestone : new long[] {1, 7, 30, 100, 365, 500, 1000}) {
            if (days == milestone && settings.on("friendship") && settings.once("friends:" + milestone)) {
                pet.party("We've been friends for " + milestone + (milestone == 1 ? " day" : " days") + "!");
                settings.earn((int) Math.min(50, milestone));
            }
        }
    }

    private java.time.LocalDate lastDay; // (for noticing midnight)

    /** The laptop battery: low makes him tired, plugging in perks him up. */
    private void checkBattery(Power.State now) {
        if (now == null) return;
        if (battery != null) {
            if (settings.on("battery") && !now.pluggedIn() && now.percent() <= 15 && battery.percent() > 15) pet.say("My battery's low... and so is yours.");
            if (settings.on("battery") && now.pluggedIn() && !battery.pluggedIn()) pet.say("Ahh. Power.");
            int last = Power.criticalLevel() + 1; // 1% before Windows shuts the laptop off by itself
            boolean critical = !now.pluggedIn() && now.percent() <= last;
            boolean wasCritical = !battery.pluggedIn() && battery.percent() <= last;
            if (critical && !wasCritical && settings.on("batteryPanic")) pet.batteryPanic(true);   // and then it all goes black, when the laptop shuts off
            if (!critical && wasCritical) pet.batteryPanic(false);
        }
        battery = now;
    }

    /** Clicked lots of times in a row: he gets grumpy, and with even more, stomps off for a bit. */
    private boolean clickedTooMuch() {
        long now = System.currentTimeMillis();
        clicks.addLast(now);
        while (!clicks.isEmpty() && clicks.peekFirst() < now - 5000) clicks.removeFirst();
        if (!settings.on("grumpy")) return false;
        if (clicks.size() >= 15 && settings.on("stompOff")) {
            clicks.clear();
            Rectangle screen = screenBounds();
            pet.annoyed("That's it. I need a minute.");
            boolean right = homeX > screen.x + screen.width / 2.0;
            body.walkOff(right ? screen.x + screen.width + 150 : screen.x - 150, 15_000);
            huffed = true;
            return true;
        }
        if (clicks.size() == 8) {
            pet.annoyed("Okay, okay! I'm awake!");
            return true;
        }
        return clicks.size() > 8; // no menu while he's grumpy
    }

    /** Caps Lock on: he covers his ears. Off again: thanks you. */
    private void checkCapsLock() {
        try {
            boolean on = java.awt.Toolkit.getDefaultToolkit().getLockingKeyState(java.awt.event.KeyEvent.VK_CAPS_LOCK);
            if (capsWasOn != null && on != capsWasOn) pet.capsLock(on);
            capsWasOn = on;
        } catch (UnsupportedOperationException notHere) {
            capsWasOn = false;
        }
    }
    private final Jokes jokes = new Jokes(System.nanoTime());
    private long lastJokeAt = System.currentTimeMillis();
    private long jokeJitter = (long) (Math.random() * 120_000);

    /** Now and then (as often as you set), when he's not busy, he tells a joke. */
    private void maybeJoke() {
        long now = System.currentTimeMillis();
        if (now - lastJokeAt < Jokes.gap(settings.jokes()) + jokeJitter) return;
        Pet.Mood m = pet.mood();
        if (job != null || bubble.showing() || hidden || focusUntil > 0 || (m != Pet.Mood.IDLE && m != Pet.Mood.SIT)) return;
        tellJoke();
    }

    private void tellJoke() {
        lastJokeAt = System.currentTimeMillis();
        jokeJitter = (long) (Math.random() * 120_000);
        bubble.show(jokes.next(), head(), screenBounds());
        pet.speak();
    }

    /** Puts on his hat and hut, and lets him use the tricks he's learned. */
    private void useItems() {
        pet.setItems(settings.owns("juggling") && !settings.serious(), settings.owns("waving") && !settings.serious(), settings.wearing("hat"));
        pet.setShirt(settings.wearing("shirt"));
        int unit = settings.unit();
        hut.show(settings.wearing("hut"), unit, (int) Math.round(homeX - Sprite.feetX() * unit + 2 * unit), (int) Math.round(groundY),
                !boxed && !hidden && !farewell && !inCorner);
    }

    private long remindedWater = System.currentTimeMillis(), remindedStretch = System.currentTimeMillis();

    /**
     * The useful reminders, while you're actually at the computer (the mouse moved in the last couple of minutes):
     * water every hour, a stretch every two, a restart after a week on, and a drive that's nearly full.
     */
    private void remindMe(long now) {
        if (now - lastMoved > 2 * 60_000 || job != null || pet.busyNow() || hidden || bubble.showing()) return;
        java.util.Random r = new java.util.Random();
        if (settings.on("water") && now - remindedWater >= 60 * 60_000L) {
            remindedWater = now;
            pet.say(Useful.WATER[r.nextInt(Useful.WATER.length)]);
            return;
        }
        if (settings.on("stretch") && now - remindedStretch >= 2 * 60 * 60_000L) {
            remindedStretch = now;
            pet.say(Useful.STRETCH[r.nextInt(Useful.STRETCH.length)]);
            return;
        }
        String today = java.time.LocalDate.now().toString();
        worker.execute(() -> {
            long up = Useful.uptime();
            java.io.File full = Useful.nearlyFull();
            SwingUtilities.invokeLater(() -> {
                if (settings.on("restart") && up >= 7 * 86_400_000L && !settings.seen("restart:" + today)) {
                    settings.once("restart:" + today);
                    pet.say("Your computer's been on for " + up / 86_400_000L + " days straight!\nA restart would make it feel fresh.");
                } else if (settings.on("diskSpace") && full != null && !settings.seen("disk:" + today)) {
                    settings.once("disk:" + today);
                    bubble.ask("Your " + full.getPath().replace("\\", "") + " drive is nearly full (" + Cleaner.size(full.getUsableSpace()) + " left).\n"
                            + "Want me to clean out a folder?", new String[] {"Yes, clean one", "Not now"}, c -> {
                                if (c == 0) startCleaning();
                            }, head(), screenBounds());
                }
            });
        });
    }

    private void startCleaning() {
        if (!Cleaner.canRecycle()) {
            bubble.show("I can't reach the Recycle Bin on this computer,\nso I won't clean anything.", head(), screenBounds());
            return;
        }
        job = new CleanJob(new CleanJob.Ui() {
            public Foreground.Front front() {
                return Foreground.front();
            }

            public java.nio.file.Path folderOf(long handle) {
                return Foreground.explorerFolder(handle);
            }

            public double[] spot(Foreground.Front w) {
                return spotOn(w);
            }

            public void say(String text) {
                bubble.show(text, head(), screenBounds());
                pet.speak();
            }

            public void ask(String text, String[] buttons, java.util.function.IntConsumer answer) {
                bubble.ask(text, buttons, answer, head(), screenBounds());
                pet.speak();
            }

            public void hideBubble() {
                bubble.hide();
            }

            public void background(Runnable work, Runnable then) {
                worker.execute(() -> {
                    try {
                        work.run();
                    } finally {
                        SwingUtilities.invokeLater(then);
                    }
                });
            }

            public int recycle(java.util.List<Cleaner.Item> items) {
                return Cleaner.recycle(items);
            }
        });
        job.start(body);
    }

    /** Where he sits on a window: its top edge, most of the way across (on a maximized window, just inside the top). */
    private double[] spotOn(Foreground.Front w) {
        int[] b = w.bounds();
        if (b[2] - b[0] < 100 || b[0] <= -30000) return null; // minimized, or too small to sit on
        double scale = window.getGraphicsConfiguration().getDefaultTransform().getScaleX(); // Windows' pixels to Java's
        Rectangle screen = screenBounds();
        double left = b[0] / scale, right = b[2] / scale, top = b[1] / scale;
        if (top < screen.y + 4) top = screen.y + 34;
        return new double[] {left + (right - left) * 0.72, top + 1};
    }

    private Rectangle screenBounds() {
        return window.getGraphicsConfiguration().getBounds();
    }

    private JPopupMenu menu() {
        JPopupMenu menu = new JPopupMenu();
        JCheckBoxMenuItem sounds = new JCheckBoxMenuItem("Beeps", settings.sounds());
        sounds.addActionListener(e -> settings.setSounds(sounds.isSelected()));
        menu.add(sounds);
        JCheckBoxMenuItem tipsItem = new JCheckBoxMenuItem("Tips", settings.tips());
        tipsItem.addActionListener(e -> {
            settings.setTips(tipsItem.isSelected());
            if (!tipsItem.isSelected()) bubble.hide();
        });
        menu.add(tipsItem);
        JCheckBoxMenuItem startup = new JCheckBoxMenuItem("Start with Windows", Startup.on());
        startup.addActionListener(e -> Startup.set(startup.isSelected()));
        menu.add(startup);
        menu.addSeparator();
        for (String size : new String[] {"Small", "Normal", "Big"}) {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(size, settings.size().equals(size));
            item.addActionListener(e -> {
                settings.setSize(size);
                resize();
            });
            menu.add(item);
        }
        menu.addSeparator();
        for (String spot : Welcome.SPOTS) {
            JMenuItem item = new JMenuItem("Sit " + Character.toLowerCase(spot.charAt(0)) + spot.substring(1).replace("On the", "on the"));
            item.addActionListener(e -> {
                settings.setSpot(spot);
                place();
            });
            menu.add(item);
        }
        // A spot just for the app (or the one window or tab) you're in: drag him there first, then pick one of these
        Foreground.Front f = lastFront;
        if (!f.app().isEmpty()) {
            menu.addSeparator();
            String app = appName(f);
            JMenuItem forApp = new JMenuItem("Sit here when " + app + " is in front");
            forApp.addActionListener(e -> rememberSpot(Settings.appKey(f.app()), app));
            menu.add(forApp);
            if (!f.title().isBlank() && !f.title().equals(app)) {
                String t = f.title().length() > 40 ? f.title().substring(0, 40) + "..." : f.title();
                JMenuItem forWindow = new JMenuItem("Sit here for just this window or tab: \"" + t + "\"");
                forWindow.addActionListener(e -> rememberSpot(Settings.windowKey(f.title()), "this one"));
                menu.add(forWindow);
            }
            if (appSpotKey != null) {
                JMenuItem forget = new JMenuItem("Forget this spot (back to his usual one)");
                forget.addActionListener(e -> {
                    settings.forgetAppSpot(appSpotKey);
                    appSpotKey = null;
                    appSpotX = null;
                    place(false);
                    body.walkHome();
                    pet.say("Okay, back to my usual spot.");
                });
                menu.add(forget);
            }
            menu.addSeparator();
        }
        JMenuItem bye = new JMenuItem("Bye, Clawd");
        bye.addActionListener(e -> System.exit(0));
        menu.add(bye);
        return menu;
    }

    private Integer appSpotX;   // where he sits for the window in front (null: his usual spot)
    private String appSpotKey;  // which of your saved spots that is
    private Foreground.Front lastFront = Foreground.Front.UNKNOWN; // the last real window in front (not his, not the taskbar)
    private int xBeforeDrag = Integer.MIN_VALUE; // his usual spot before you last dragged him (if that drag was for an app)

    /** Spots you gave him for an app, or just one window or tab: he walks over when it's in front, and back after. */
    private void followAppSpot(Foreground.Front front) {
        String cls = front.windowClass();
        if (front.app().isEmpty() || cls.equals("Shell_TrayWnd") || cls.equals("Shell_SecondaryTrayWnd")
                || cls.startsWith("NotifyIconOverflow") || cls.equals("TopLevelWindowForOverflowXamlIsland")
                || java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null) return; // the taskbar, or him
        if (!front.app().equals(lastFront.app()) || !front.title().equals(lastFront.title())) xBeforeDrag = Integer.MIN_VALUE;
        lastFront = front;
        String key = settings.appSpotKey(front.app(), front.title());
        if (java.util.Objects.equals(key, appSpotKey)) return;
        if (body.state() != Body.State.HOME || job != null || inCorner || dragFrom != Integer.MIN_VALUE || pet.busyNow()) return; // in a bit
        appSpotKey = key;
        appSpotX = key == null ? null : settings.appSpot(key);
        place(false);
        body.walkHome();
    }

    /** Remembers where he is now as his spot for an app, or for one window or tab. */
    private void rememberSpot(String key, String name) {
        int x = window.getX();
        settings.setAppSpot(key, x);
        if (appSpotKey == null && xBeforeDrag != Integer.MIN_VALUE) settings.setX(xBeforeDrag); // that drag was for this, not his usual spot
        xBeforeDrag = Integer.MIN_VALUE;
        appSpotKey = key;
        appSpotX = x;
        place();
        pet.say(name.equals("this one") ? "Got it! This is my spot for this one." : "Got it! I'll sit here when " + name + " is in front.");
    }

    /** A friendly name for the app in front ("Google Chrome", not "chrome.exe"). */
    static String appName(Foreground.Front f) {
        String t = f.title();
        int dash = t.lastIndexOf(" - ");
        if (dash >= 0 && t.length() - dash - 3 > 1 && t.length() - dash - 3 < 30) return t.substring(dash + 3).strip();
        String a = f.app().replaceAll("(?i)\\.exe$", "");
        return a.isEmpty() ? "this app" : Character.toUpperCase(a.charAt(0)) + a.substring(1);
    }

    // ---- The taskbar tackle: open an app and he charges its taskbar icon; when he hits it, it pops open and BOOM ----
    private static final String[] TACKLE_YELLS = {"AAAAAAAH!", "WORTH IT!", "Got it open for youuuuu!", "Nailed iiiiit!"};
    private static final String[] TACKLE_SPOTS = {"Ooh! Something's opening! I'll get it!", "Wait wait wait, let me open that!",
            "I got this one!", "Hold on! That's my job!"};
    private volatile long tackleWindow;      // the app window he's hiding till he gets to its icon (0: none)
    private volatile long tackleStyle = -1;  // its style, to put back
    private boolean tackleLooking;           // still finding its icon on the taskbar
    private long tackleUntil;                // never hidden longer than this, whatever happens
    private long lastFrontWindow;
    private final java.util.Set<Long> seenPrograms = new java.util.HashSet<>();

    /** A new app just opened in front? Hide it, and off he goes to its taskbar icon. */
    private void watchForLaunch() {
        if (!WindowTricks.available()) return;
        long now = System.currentTimeMillis();
        if (tackleWindow != 0) {
            // something went wrong (he got picked up, say): just open it
            if (now > tackleUntil || (!tackleLooking && body.state() != Body.State.TACKLE)) endTackle();
            return;
        }
        long front = WindowTricks.front();
        if (front == lastFrontWindow) return;
        lastFrontWindow = front;
        if (front == 0 || !settings.on("tackle") || settings.serious() || hidden || boxed) return;
        if (body.state() != Body.State.HOME || job != null || pet.busyNow() || inCorner || dragFrom != Integer.MIN_VALUE) return;
        long pid = WindowTricks.processOf(front);
        long me = ProcessHandle.current().pid();
        if (pid == 0 || pid == me || !seenPrograms.add(pid)) return; // (each program only the once)
        ProcessHandle program = ProcessHandle.of(pid).orElse(null);
        if (program == null) return;
        java.time.Instant started = program.info().startInstant().orElse(null);
        if (started == null || java.time.Duration.between(started, java.time.Instant.now()).toMillis() > 8000) return; // not just opened
        if (program.parent().map(p -> p.pid() == me).orElse(false) || now - Useful.lastOpened < 10_000) return; // he opened it himself
        if (!WindowTricks.appWindow(front)) return;
        String exe = program.info().command().map(c -> Path.of(c).getFileName().toString()).orElse("");
        String title = Foreground.front().title();
        long style = WindowTricks.vanish(front);
        if (style < 0) return;
        tackleWindow = front;
        tackleStyle = style;
        tackleUntil = now + 7000;
        tackleLooking = true;
        Thread.ofPlatform().daemon().start(() -> { // (and in case he's stuck somehow: it's back in 8 seconds regardless)
            try {
                Thread.sleep(8000);
            } catch (InterruptedException ignored) {
                // then now
            }
            if (tackleWindow == front) SwingUtilities.invokeLater(this::endTackle);
        });
        pet.say(TACKLE_SPOTS[new java.util.Random().nextInt(TACKLE_SPOTS.length)]);
        Thread.ofPlatform().daemon().name("taskbar").start(() -> {
            WindowTricks.TaskbarButton button = null;
            try {
                String encoded = java.util.Base64.getEncoder().encodeToString(WindowTricks.taskbarScript().getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
                Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                        "-WindowStyle", "Hidden", "-EncodedCommand", encoded).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                button = WindowTricks.buttonFor(WindowTricks.parseTaskbar(out), exe, title);
            } catch (Exception noTaskbar) {
                // then no tackle
            }
            WindowTricks.TaskbarButton found = button;
            SwingUtilities.invokeLater(() -> {
                tackleLooking = false;
                if (tackleWindow != front) return;
                Rectangle s = window.getGraphicsConfiguration().getBounds();
                double scale = window.getGraphicsConfiguration().getDefaultTransform().getScaleX();
                double x = found == null ? -1 : s.x + found.centerX() / scale;
                if (found == null || x < s.x || x > s.x + s.width || body.state() != Body.State.HOME) {
                    endTackle(); // no icon to tackle (or he got busy): there it is anyway
                    if (!bubble.asking()) bubble.hide();
                    return;
                }
                body.tackle(x);
            });
        });
    }

    /** On start (once a day): is there a newer Clawd? Then he asks if you'd like it. */
    private void checkForUpdate() {
        if (!settings.on("updates") || System.getProperty("clawdtop.home") != null) return; // (not in test runs)
        if (!settings.once("update:" + java.time.LocalDate.now())) return;
        worker.execute(() -> {
            Updater.Release release = Updater.check();
            if (release != null) SwingUtilities.invokeLater(() -> offerUpdate(release, 0));
        });
    }

    private void offerUpdate(Updater.Release release, int tries) {
        if (tries >= 40) return; // never found a good moment: there's always tomorrow
        if (pet.busyNow() || bubble.asking() || body.state() != Body.State.HOME || hidden || inCorner || boxed || farewell || movingOut
                || focusUntil > 0 || pet.focusing()) { // in a bit
            javax.swing.Timer later = new javax.swing.Timer(15_000, e -> offerUpdate(release, tries + 1));
            later.setRepeats(false);
            later.start();
            return;
        }
        pet.speak();
        bubble.ask("There's a new me! Version " + release.version() + " is out.\nWant me to update? (takes a few seconds)",
                new String[] {"Update!", "Not now"}, choice -> {
                    if (choice != 0) {
                        pet.say("Okay! I'll ask again another day.");
                        return;
                    }
                    pet.say("Updating... be right back!");
                    worker.execute(() -> {
                        boolean ok = Updater.install(release);
                        SwingUtilities.invokeLater(() -> {
                            if (ok) {
                                Diary.write("Updated myself to version " + release.version() + ". New me, who dis?");
                                System.exit(0); // the helper puts the new me in place and starts me again
                            } else {
                                pet.say("Hmm, the update didn't download.\nI'll try again next time.");
                            }
                        });
                    });
                }, head(), screenBounds());
    }

    /** Puts the app's window back, all at once. */
    private void endTackle() {
        long w = tackleWindow;
        if (w == 0) return;
        tackleWindow = 0;
        WindowTricks.reveal(w, tackleStyle);
        tackleStyle = -1;
    }

    /**
     * His menus: a click anywhere else closes them. (He never takes the focus from what you're doing, so Swing can't
     * tell you clicked away on its own: we watch the mouse buttons instead.)
     */
    private void closeMenuOnClickAway(Point mouse) {
        MenuElement[] open = MenuSelectionManager.defaultManager().getSelectedPath();
        if (open.length == 0 || !Foreground.mouseButtonDown()) return;
        if (window.isShowing() && new Rectangle(window.getLocationOnScreen(), window.getSize()).contains(mouse)) return;
        for (MenuElement e : open) {
            if (e instanceof JPopupMenu popup && popup.isShowing()
                    && new Rectangle(popup.getLocationOnScreen(), popup.getSize()).contains(mouse)) return; // a click in the menu
        }
        MenuSelectionManager.defaultManager().clearSelectedPath();
    }

    private void tick() {
        ticks++;
        PointerInfo pointer = MouseInfo.getPointerInfo();
        Point mouse = pointer != null ? pointer.getLocation() : lastMouse;
        boolean moved = !mouse.equals(lastMouse);
        lastMouse = mouse;
        closeMenuOnClickAway(mouse);
        boolean right = Foreground.rightButtonDown();
        if (right && !rightWasDown && body.state() == Body.State.RIDE) body.dropOff(); // right-click: he hops down (no flick needed)
        rightWasDown = right;
        watchForLaunch();

        // Twice a second: what's in front (a coding app makes him happy; a full-screen game or video hides him)
        if (ticks % 15 == 0) {
            Foreground.Front front = Foreground.front();
            app = front.app();
            devApp = Foreground.isDevApp(app);
            if (job == null) maybeTip(front);
            followAppSpot(front);
            if (Games.launcher(app) && !hidden) admireGames("launcher");
            DisplayMode mode = window.getGraphicsConfiguration().getDevice().getDisplayMode();
            boolean fullScreen = job == null && !devApp && Foreground.fullScreen(mode.getWidth(), mode.getHeight());
            // A full-screen game: he sits down in the bottom corner (over your health bar) and watches. A video: he hides.
            boolean corner = fullScreen && !Games.videoApp(app) && settings.choice("gameMode").equals("Sit in a corner")
                    && (body.state() == Body.State.HOME || inCorner);
            boolean hide = fullScreen && !corner && settings.on("hideFullScreen");
            if (corner && !inCorner) { // (set before the move below, so it lands right in the corner)
                Rectangle whole = window.getGraphicsConfiguration().getBounds();
                homeX = whole.x + whole.width - (Sprite.WIDTH - Sprite.feetX()) * settings.unit() - 6;
                groundY = whole.y + whole.height;
            }
            if (corner != inCorner) {
                inCorner = corner;
                Rectangle whole = window.getGraphicsConfiguration().getBounds();
                if (corner) {
                    homeX = whole.x + whole.width - (Sprite.WIDTH - Sprite.feetX()) * settings.unit() - 6; // bottom right, over the ammo
                    groundY = whole.y + whole.height;
                    window.setAlwaysOnTop(false); // (re-asserted just below, to get above the game)
                    window.setAlwaysOnTop(true);
                    if (pet.takeLineIfAny() == null && settings.once("gameCorner:" + java.time.LocalDate.now())) pet.say("Ooh, a game! I'll watch from here.");
                } else {
                    place();
                }
                useItems(); // his hut stays at home while he's in the corner
            }
            if (hide != hidden) {
                hidden = hide;
                window.setVisible(!hidden);
                if (hidden) bubble.hide();
                useItems(); // his hut hides (and comes back) with him
            }
        }
        // Now and then (once a day, a while after he starts): something nice about your games
        if (ticks % 1800 == 900 && ticks > 30 * 60 * 20 && new java.util.Random().nextInt(6) == 0) admireGames("idle");
        // Every couple of seconds: settings changed from the clawd command (clawd controlpanel)?
        long settingsNow = ticks % 60 == 0 ? Settings.changed() : settingsChanged;
        if (settingsNow != settingsChanged && settingsNow == Settings.lastSaved) settingsChanged = settingsNow; // (his own save)
        if (settingsNow != settingsChanged && !farewell) {
            settingsChanged = settingsNow;
            String oldSize = settings.size(), oldSpot = settings.spot() + "/" + settings.x() + "/" + settings.number("nudge");
            settings = Settings.load();
            pet.setPersonality(settings.personality());
            pet.changeColor(settings.awtColor()); // a few seconds later, suddenly: he'll freak out
            if (!oldSize.equals(settings.size())) resize();
            else if (body.state() == Body.State.HOME && !oldSpot.equals(settings.spot() + "/" + settings.x() + "/" + settings.number("nudge"))) place();
            useItems();
            useOptions();
        }
        // Every few seconds: did a coding app just close? He's sad for a moment.
        if (ticks % 90 == 45 && !farewell) {
            worker.execute(() -> { // looking through every program takes a moment: not on the drawing thread
                java.util.Set<String> now = Foreground.openDevPrograms();
                SwingUtilities.invokeLater(() -> {
                    if (devPrograms != null && !now.containsAll(devPrograms)) pet.sad();
                    devPrograms = now;
                });
            });
        }
        // And anything it asked him to do: a mood, or goodbye
        if (ticks % 30 == 0 && !farewell) {
            String asked = Settings.takeAsk();
            if (asked != null && asked.equals("quit")) System.exit(0); // clawd stop (his shutdown hooks tidy up)
            if (asked != null && asked.equals("goodbye")) sayGoodbye();
            else if (asked != null && asked.equals("moveout")) moveOut();
            else if (asked != null && asked.startsWith("mood ")) pet.ask(asked.substring(5));
        }
        if (farewell) {
            pet.tick(FRAME_MS, 0, 0, false, false);
            String line = pet.takeLine();
            if (line != null) bubble.show(line, new Rectangle(window.getX(), window.getY() + FAREWELL_ROOM * settings.unit(),
                    Sprite.WIDTH * settings.unit(), Sprite.HEIGHT * settings.unit()), screenBounds());
            if (pet.crumbled() > 0) bubble.hide();
            if (pet.gone()) System.exit(0);
            canvas.repaint();
            return;
        }
        // Every few seconds, back on top (the taskbar likes to come up over everything when it's clicked)
        if (ticks % 90 == 0 && !hidden && settings.on("onTop")) {
            window.setAlwaysOnTop(false);
            window.setAlwaysOnTop(true);
        }

        int unit = settings.unit();
        if (boxed) {
            if (welcome != null && welcome.showing()) welcome.follow(head(), screenBounds());
            return; // nothing to do until he's out
        }
        if (greetWhenHome && body.state() == Body.State.HOME) {
            greetWhenHome = false;
            pet.poke();
            String hi = settings.restored()
                    ? "Hii.......... I think I remember you...." + (settings.name().isEmpty() ? "" : " " + settings.name() + ", right?")
                    : "Hi" + (settings.name().isEmpty() ? "" : " " + settings.name()) + "!! I'm so happy to be here!";
            bubble.show(hi, head(), screenBounds());
            if (brainBusy && lastBrainNote != null) { // what he's been up to in his box: getting his brain ready
                javax.swing.Timer later = new javax.swing.Timer(7000, e -> { if (brainBusy && lastBrainNote != null) pet.say(lastBrainNote); });
                later.setRepeats(false);
                later.start();
            }
        }
        // His body: on his perch, or riding your cursor, flying off, dizzy, walking home
        if (dragFrom == Integer.MIN_VALUE) {
            Rectangle screen = window.getGraphicsConfiguration().getBounds();
            if (job != null) {
                job.tick(FRAME_MS, body, pet);
                if (job.over()) job = null;
            }
            body.setCeiling(screen.y);
            body.setUnit(unit);
            body.setMistakes(settings.on("mistakes"));
            if (moveOutPending && body.state() == Body.State.HOME) walkOffToMove();
            body.tick(FRAME_MS, mouse.x, mouse.y, homeX, groundY, 12 * unit, screen.x, screen.x + screen.width);
            pet.follow(body.state());
            creations();
            if (body.takeMissed()) pet.say("Missed! ...I meant to do that.");
            if (body.takeTackled()) {
                endTackle(); // POP: there it is
                explosion.start(body.x(), groundY - 2 * unit);
                showFx();
                Rectangle s = window.getGraphicsConfiguration().getBounds();
                body.launchFrom(body.x(), body.y(), body.x() > s.x + s.width / 2.0 ? -550 : 550);
                pet.say(TACKLE_YELLS[new java.util.Random().nextInt(TACKLE_YELLS.length)]);
                Diary.write("Tackled a taskbar icon. It exploded. Worth it.");
            }
            if (body.takeBoom()) {
                pet.boom();
                Diary.write("My rocket exploded. I knew there was a bug in the code.");
                explosion.start(body.x(), body.y() - 7 * unit); // round the middle of his rocket
                showFx();
            }
            if (body.state() != Body.State.HOME || window.getX() != (int) Math.round(homeX - Sprite.feetX() * unit)) {
                window.setLocation((int) Math.round(body.x() - Sprite.feetX() * unit),
                        (int) Math.round(body.y() - window.getHeight() + unit));
            }
        }
        earnPoints(mouse, moved);
        if (ticks % 30 == 0) maybeJoke();
        if (ticks % 10 == 0) checkCapsLock();
        // coming back after a long while counts as a new login
        long nowMs = System.currentTimeMillis();
        if (moved) {
            if (nowMs - lastMoved > 10 * 60_000 && !boxed) cameBack(nowMs - lastMoved);
            lastMoved = nowMs;
            if (ticks % 1800 == 0) settings.setLastSeen(nowMs);
        }
        if (birthdayHiding && nowMs > birthdayHideUntil) birthdaySurprise();
        if (pendingBirthday && body.state() != Body.State.FALL) {
            pendingBirthday = false;
            pet.birthday(settings.name());
        }
        if (ticks % 300 == 150) checkTimes();
        if (!greetings.isEmpty() && ticks % 15 == 3 && !pet.busyNow() && pet.takeLineIfAny() == null && !bubble.showing()
                && body.state() == Body.State.HOME && !hidden && !boxed && !birthdayHiding) greetings.poll().run(); // one at a time
        if (ticks % 900 == 450 && focusUntil == 0) remindMe(nowMs);
        if (ticks % 15 == 7) checkReminders();
        if (ticks % 30 == 11) veteransSongs(nowMs);
        // The screen changed (another monitor, a new resolution, the taskbar moved)? Back to his spot on it
        if (ticks % 90 == 30 && body.state() == Body.State.HOME && !inCorner) {
            Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            if (lastUsable != null && !usable.equals(lastUsable)) place();
            lastUsable = usable;
        }
        updateClock();
        if (ticks % 150 == 75) worker.execute(() -> {
            Power.criticalLevel(); // asked once, here in the background
            Power.State b = Power.now();
            SwingUtilities.invokeLater(() -> checkBattery(b));
        });
        checkZoom(mouse);
        holdPet();
        if (movingOut && body.state() == Body.State.OUT) System.exit(0); // gone to the new computer
        if (huffed && body.state() == Body.State.HOME) {
            huffed = false;
            bubble.show("...okay. I'm better now.", head(), screenBounds());
        }
        double eyesX = window.getX() + Sprite.eyesX() * unit;
        double eyesY = window.getY() + Sprite.eyesY() * unit;
        // the cursor resting on him (he gets shy), or swiping across his face (boop!)
        boolean overHim = Math.abs(mouse.x - eyesX) < 7 * unit && Math.abs(mouse.y - eyesY) < 4 * unit && body.state() == Body.State.HOME;
        pet.hover(overHim && !moved, FRAME_MS);
        pet.tick(FRAME_MS, mouse.x - eyesX, mouse.y - eyesY, moved, devApp);
        boolean playingNow = pet.mood() == Pet.Mood.PIANO;
        if (wasPlaying && !playingNow) beeps.stopAll(); // he stopped: so does the music
        wasPlaying = playingNow;
        int note = pet.takeNote();
        if (note > 0 && mayBeep() && pet.playingMidi() == null) beeps.play(pet.instrument(), note, pet.noteLength());
        playMidi(mayBeep() ? pet.playingMidi() : null); // (muted, quiet hours or hidden: the song stops)
        Pet.Beep beep = pet.takeBeep();
        if (beep != null && mayBeep()) beeps.play(beep);
        // what he says (not while he's hidden for your video, and never over a question he's asking you)
        String line = pet.takeLine();
        if (hidden) line = null;
        else if (bubble.asking()) { // keep it for after you've answered
            if (line != null) heldLine = line;
            line = null;
        } else if (line == null && heldLine != null) {
            line = heldLine;
            heldLine = null;
        }
        if (line != null) bubble.show(line, head(), screenBounds());
        bubble.tick();
        if (bubble.showing()) bubble.follow(head(), screenBounds());
        if (welcome != null && welcome.showing()) welcome.follow(head(), screenBounds());
        canvas.repaint();
    }

    /**
     * A tip when something new comes to the front: straight away for the Run box (you opened it to type something),
     * otherwise at most one every three minutes, so he's helpful without nagging.
     */
    private void maybeTip(Foreground.Front front) {
        String kind = Tips.kind(front);
        if (java.util.Objects.equals(kind, lastKind)) return;
        lastKind = kind;
        if (kind == null || !settings.tips() || hidden || focusUntil > 0 || bubble.asking()) return;
        long now = System.currentTimeMillis();
        if (!Tips.urgent(front) && now - lastTipAt < pet.personality().tipGap()) return;
        String tip = tips.tipFor(front);
        if (tip == null) return;
        lastTipAt = now;
        settings.earn(Shop.TIP);
        bubble.show(tip, head(), window.getGraphicsConfiguration().getBounds());
        pet.speak();
    }

    private Welcome welcome;
    private boolean welcomeStarted;

    /** clawd move: he's off to the new computer. He picks up a box and walks off the edge of the screen. */
    private void moveOut() {
        if (job != null) job.stop(body, pet);
        bubble.show("Off to the new place! Bye!", head(), screenBounds());
        movingOut = true;
        moveOutPending = true; // off he goes once he's back on the taskbar (he might be riding, or falling)
    }

    /** Moving out: he picks up his boxes and walks off the edge of the screen (once he's home to pack). */
    private void walkOffToMove() {
        moveOutPending = false;
        pet.moving(true);
        Rectangle screen = screenBounds();
        boolean right = homeX > screen.x + screen.width / 2.0;
        body.walkOff(right ? screen.x + screen.width + 200 : screen.x - 200, Long.MAX_VALUE);
    }

    private boolean movingOut, moveOutPending;

    /** clawd uninstall: he says bye, then crumbles away into dust, and the program ends. */
    private void sayGoodbye() {
        farewell = true;
        if (job != null) job.stop(body, pet);
        bubble.hide();
        int unit = settings.unit();
        // room above and to the right for his dust to drift into
        window.setBounds(window.getX(), window.getY() - FAREWELL_ROOM * unit, window.getWidth() + FAREWELL_ROOM * unit,
                window.getHeight() + FAREWELL_ROOM * unit);
        pet.ask("goodbye");
    }
    private boolean boxed;         // still in his box, the first time
    private boolean greetWhenHome; // just shot out of his box: say hi once he's back on his spot
    private boolean farewell;      // being uninstalled: saying bye, then crumbling away
    private static final int FAREWELL_ROOM = 14; // units of room above and to the right for his dust

    /** Passes his options on to the parts that use them. */
    private void useOptions() {
        Settings s = settings;
        pet.setPrefs(new Pet.Prefs() {
            public boolean on(String key) {
                return s.on(key);
            }

            public int number(String key) {
                return s.number(key);
            }

            public String choice(String key) {
                return s.choice(key);
            }
        });
        body.setRules(s.on("rides"), s.on("shakeOff"), s.number("hopWait") * 100L,
                switch (s.choice("shakeHow")) {
                    case "Gently" -> 900;
                    case "Really hard" -> 2400;
                    default -> Body.SHAKE_SPEED;
                },
                switch (s.choice("walk")) {
                    case "Slow" -> 80;
                    case "Fast" -> 260;
                    default -> Body.WALK_SPEED;
                });
        beeps.setVoice(s.choice("voice"), s.number("volume"));
        Bubble.setFont(s.choice("font"));
        Bubble.stay = switch (s.choice("bubbleTime")) {
            case "Short" -> 0.6;
            case "Long" -> 1.8;
            default -> 1;
        };
        window.setAlwaysOnTop(s.on("onTop"));
        String tag = (s.on("nameTag") ? "Clawd" : "") + (s.on("nameTag") && s.on("pointsTag") ? " - " : "") + (s.on("pointsTag") ? s.points() + " Clawd Points" : "");
        canvas.setToolTipText(tag.isEmpty() ? null : tag);
        pet.setHome(s.home());
    }

    /** Whether he may beep right now (beeps on, and not in quiet hours). */
    private boolean mayBeep() {
        if (!settings.sounds() || hidden) return false; // (hidden for your video: not a peep)
        if (!settings.on("quietHours")) return true;
        int hour = java.time.LocalTime.now().getHour(), from = settings.number("quietFrom"), to = settings.number("quietTo");
        boolean quiet = from <= to ? hour >= from && hour < to : hour >= from || hour < to;
        return !quiet;
    }

    void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { // never leave an app see-through
            long w = tackleWindow;
            if (w != 0) WindowTricks.reveal(w, tackleStyle);
        }));
        Updater.tidy();
        Settings.takeAsk(); // (anything the clawd command asked before he started: old news)
        javax.swing.Timer updates = new javax.swing.Timer(20_000, e -> checkForUpdate()); // once he's settled in
        updates.setRepeats(false);
        updates.start();
        try {
            java.nio.file.Files.createDirectories(songsFolder().resolve("veterans")); // so you can see where songs go
        } catch (IOException ignored) {
            // no songs folder, then
        }
        useOptions();
        pet.setColor(settings.awtColor());
        pet.setPersonality(settings.personality());
        pet.setBirthdayToday(settings.birthdayToday());
        window.setVisible(true);
        useItems();
        if (settings.met()) cameBack(0);
        welcomeStarted = !settings.met();
        new Timer(FRAME_MS, e -> tick()).start();
        if (!settings.met()) {
            // The first time: he's all excited to meet you
            welcome = new Welcome(settings, this::place, b -> {
                if (settings.sounds()) beeps.play(b);
            });
            boxed = true;
            window.setVisible(false); // he's in his box, which shows up when you've finished meeting him
            welcome.onFinished(() -> {
                if (settings.on("askMe") && System.getProperty("clawdtop.home") == null) prepareBrain(null); // his brain, in the background
                settings = Settings.load(); // a save token may have brought back his color and the rest
                settingsChanged = Settings.changed();
                pet.setColor(settings.awtColor());
                pet.setPersonality(settings.personality());
                resize();
                if (welcome.movedIn()) {
                    // moved in from another computer: he walks in from the side with his boxes, and unpacks
                    boxed = false;
                    Rectangle screen = screenBounds();
                    boolean fromRight = homeX > screen.x + screen.width / 2.0;
                    body.walkIn(fromRight ? screen.x + screen.width + 120 : screen.x - 120);
                    snapTo(body.x(), groundY); // (on the taskbar, off the side of the screen)
                    window.setVisible(true);
                    pet.moving(true);
                    return;
                }
                new Box(settings.unit(), (int) Math.round(homeX), (int) Math.round(groundY), () -> {
                boxed = false;
                body.launchFrom(homeX, groundY, (Math.random() < 0.5 ? -1 : 1) * (150 + Math.random() * 250)); // out of the box, not from the corner
                snapToBody();
                window.setVisible(true);
                if (settings.sounds()) beeps.play(Pet.Beep.WHEE);
                greetWhenHome = true;
                }).show();
            });
            welcome.start(head(), screenBounds());
        } else if (!settings.name().isEmpty() && pet.takeLineIfAny() == null && !birthdayHiding) {
            bubble.show("Hi again, " + settings.name() + "!", head(), screenBounds());
        }
    }

    public static void main(String[] args) throws IOException {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Clawdtop needs a screen to sit on.");
            return;
        }
        // Only one Clawd at a time
        Path folder = Settings.folder();
        Files.createDirectories(folder);
        RandomAccessFile lockFile = new RandomAccessFile(folder.resolve("running.lock").toFile(), "rw");
        FileLock lock = lockFile.getChannel().tryLock();
        if (lock == null) return;
        // If anything ever goes wrong, a note of it goes in clawd.log (to help fix it), and he carries on
        Path log = folder.resolve("clawd.log");
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            try {
                if (Files.exists(log) && Files.size(log) > 200_000) Files.delete(log);
                java.io.StringWriter trace = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(trace));
                Files.writeString(log, java.time.LocalDateTime.now() + " (" + thread.getName() + ")\n" + trace + "\n",
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // can't write it down either
            }
        });
        // So the clawd command can find him (clawd stop, clawd status)
        Path pid = folder.resolve("running.pid");
        Files.writeString(pid, String.valueOf(ProcessHandle.current().pid()));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.deleteIfExists(pid);
            } catch (IOException ignored) {
                // next start writes it again
            }
        }));
        if (System.getProperty("clawdtop.home") == null) { // (a test run in its own folder leaves your PATH alone)
            Thread command = new Thread(Install::ensureCommand, "Clawdtop command");
            command.setDaemon(true);
            command.start();
        }
        SwingUtilities.invokeLater(() -> new Clawdtop().start());
    }

    /**
     * For the screen test (test/clawdtop/Smoke.java, run on a real Windows computer by GitHub Actions): makes him do
     * things on cue. Call on the Swing thread.
     */
    void smoke(String action) {
        Rectangle at = head();
        switch (action) {
            case "menu" -> jobs().show(canvas, at.width / 2, at.height / 3);
            case "settings menu" -> menu().show(canvas, at.width / 2, at.height / 3);
            case "close menus" -> javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
            case "ask" -> askBox.show("Ask me anything!", at, screenBounds(), this::answer);
            case "type" -> askBox.field().setText("why is the sky blue?");
            case "close ask" -> askBox.hide();
            case "tip" -> pet.say("Win+Shift+S takes a screenshot of part of the screen.");
            case "math" -> answer("what's 12 times 7?");
            case "piano" -> pet.playPiano(Piano.SONGS[0]);
            case "guitar" -> pet.play(Piano.Instrument.GUITAR, Piano.SONGS[1]);
            case "bass" -> pet.play(Piano.Instrument.BASS, Piano.SONGS[4]);
            case "drums" -> pet.play(Piano.Instrument.DRUMS, Piano.BEATS[0]);
            case "your piano" -> yourPiano.show(head(), screenBounds(), note -> {
                beeps.piano(note, 400);
                pet.listened();
                smokeNotes++;
            });
            case "salute" -> pet.salute();
            case "veterans" -> {
                pet.salute();
                nextVeteransSong = System.currentTimeMillis() + 7000;
            }
            case "ttt" -> ticTacToe();
            case "christmas" -> pet.celebrate("Merry Christmas!", Holidays.on(java.time.LocalDate.of(2026, 12, 25)).show());
            case "focus" -> focus(true);
            case "lap" -> {
                if (pet.lap()) body.runLap();
            }
            case "vibe" -> pet.vibe();
            case "remind" -> answer("remind me in 3 seconds to drink some water");
            case "timer" -> answer("set a timer for 5 minutes");
            case "stopwatch" -> answer("start a stopwatch");
            case "clean" -> startCleaning();
            case "checkup" -> pet.say(Useful.checkup());
            case "pet" -> pet.petted();
            case "stop ducks" -> pet.stopDucks();
            case "click carpet" -> {
                if (body.state() != Body.State.FLY) return;
                body.knockOff();
                pet.carpetGone(riding);
            }
            default -> {
                if (action.startsWith("welcome click ") && welcome != null) {
                    for (javax.swing.JButton b : smokeFind(welcome.panel(), javax.swing.JButton.class)) {
                        if (b.getText().equals(action.substring(14))) {
                            b.doClick();
                            return;
                        }
                    }
                    throw new IllegalStateException("no button " + action.substring(14));
                }
                if (action.startsWith("welcome type ") && welcome != null) {
                    smokeFind(welcome.panel(), javax.swing.JTextField.class).get(0).setText(action.substring(13));
                    return;
                }
                if (action.startsWith("answer ")) {
                    bubble.press(Integer.parseInt(action.substring(7)));
                    return;
                }
                if (action.startsWith("midi ")) {
                    playDropped(new java.io.File(action.substring(5)));
                    return;
                }
                if (action.startsWith("make ")) pet.create(Creation.find(action.substring(5)));
                else if (action.startsWith("mood ")) pet.ask(action.substring(5));
                else if (action.startsWith("say ")) pet.say(action.substring(4));
            }
        }
    }

    private static <T> java.util.List<T> smokeFind(java.awt.Container in, Class<T> kind) {
        java.util.List<T> found = new java.util.ArrayList<>();
        for (java.awt.Component c : in.getComponents()) {
            if (kind.isInstance(c)) found.add(kind.cast(c));
            if (c instanceof java.awt.Container inner) found.addAll(smokeFind(inner, kind));
        }
        return found;
    }

    /** For the screen test: where his home spot is on the screen {x, ground y}, and how big a unit is. */
    double[] smokeHome() {
        return new double[] {homeX, groundY, settings.unit()};
    }

    int smokeNotes; // notes you've played on your piano (for the screen test)

    /** For the screen test: the middle of the text box that's showing, on the screen. */
    java.awt.Point smokeFieldOnScreen() {
        javax.swing.JTextField f = askBox.field().isShowing() ? askBox.field()
                : welcome == null ? null : smokeFind(welcome.panel(), javax.swing.JTextField.class).stream().filter(java.awt.Component::isShowing).findFirst().orElse(null);
        if (f == null) return null;
        java.awt.Point p = f.getLocationOnScreen();
        return new java.awt.Point(p.x + f.getWidth() / 2, p.y + f.getHeight() / 2);
    }

    /** For the screen test: the middle of your piano's first white key, on the screen. */
    java.awt.Point smokePianoOnScreen() {
        return yourPiano.firstKeyOnScreen();
    }

    /** For the screen test: what's typed in the question box, and in the hello's name box. */
    String smokeTyped() {
        String welcomeText = welcome == null ? "" : smokeFind(welcome.panel(), javax.swing.JTextField.class).stream().map(javax.swing.JTextField::getText).findFirst().orElse("");
        return askBox.field().getText() + "|" + welcomeText + "|" + smokeNotes + "|" + pet.mood();
    }

    /** For the screen test: the question in his bubble, or null. */
    String smokeQuestion() {
        return bubble.question();
    }

    /** For the screen test: whether he's back to just hanging out at home. */
    boolean smokeIdle() {
        return job == null && body.state() == Body.State.HOME && (pet.mood() == Pet.Mood.IDLE || pet.mood() == Pet.Mood.SIT);
    }

    /** Where he is on the screen, for pointing bubbles at (his window, without the room it keeps above him for hats). */
    private Rectangle head() {
        Rectangle r = window.getBounds();
        double hat = Sprite.hatHeight(pet.hat());
        int room = (int) Math.round(Math.max(0, Math.min(3, 6 - hat)) * settings.unit()); // (room for a tall hat under the bubble)
        return new Rectangle(r.x, r.y + room, r.width, r.height - room);
    }

    /** For tests: the screen area he'd sit in, without a window. */
    static GraphicsConfiguration screen() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
    }
}
