package clawdtop;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
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
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Rectangle screen = window.getGraphicsConfiguration().getBounds();
        int w = window.getWidth();
        int h = window.getHeight();
        int bottom = usable.y + usable.height; // the taskbar's top when it's at the bottom of the screen
        int x = settings.x() >= 0 ? settings.x() : switch (settings.spot()) {
            case "In the middle" -> screen.x + screen.width / 2 - w / 2;
            case "On the left" -> screen.x + 70;
            default -> screen.x + screen.width - 64 - w / 2; // above the clock, in the corner
        };
        x = Math.max(screen.x, Math.min(screen.x + screen.width - w, x));
        bottom -= settings.number("nudge"); // nudged up (or down) if you like
        window.setLocation(x, bottom - h + settings.unit()); // his feet just touch the taskbar
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
                    settings.setX(window.getX());
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
        long now = System.currentTimeMillis();
        for (java.util.Iterator<Object[]> it = reminders.iterator(); it.hasNext(); ) {
            Object[] r = it.next();
            if (now >= (Long) r[0] && pet.remind((String) r[1])) it.remove(); // (busy? he tells you in a moment)
        }
        if (focusUntil > 0 && now >= focusUntil) {
            focusUntil = 0;
            pet.focus(false, true);
        }
    }

    private void focus(boolean on) {
        focusUntil = on ? System.currentTimeMillis() + 25 * 60_000L : 0;
        pet.focus(on, false);
    }
    private final Piano yourPiano = new Piano();

    /** A MIDI file you dropped on him: read it (in the background), then he fetches it and plays it. */
    void playDropped(java.io.File midi) {
        worker.execute(() -> {
            Piano.Song song = Piano.fromMidi(midi);
            SwingUtilities.invokeLater(() -> {
                if (song == null) pet.say("I tried, but I can't read that music.");
                else if (!pet.fetch(song)) pet.say("Ooh, music! Give me a sec, I'm busy.");
            });
        });
    }
    private final Ask askBox = new Ask();
    private final Brain brain = new Brain();
    private boolean thinking;

    /** Answers your question: math goes to Calculator (he doesn't trust himself); the rest, his brain. */
    private void answer(String question) {
        int watch = Reminders.stopwatch(question);
        if (watch != 0) {
            stopwatch(watch > 0);
            return;
        }
        Reminders.Reminder reminder = Reminders.parse(question);
        if (reminder != null) {
            reminders.add(new Object[] {System.currentTimeMillis() + reminder.inMs(), reminder.what()});
            pet.say(reminder.what().equals("time's up!") ? "Timer set for " + reminder.when() + "! Tick tock." : "Okay! I'll remind you in " + reminder.when() + ".");
            return;
        }
        MathHelp.Problem sum = MathHelp.parse(question);
        if (sum != null) {
            mathHelp(sum, 0);
            return;
        }
        if (thinking) return;
        thinking = true;
        String model = Brain.model(settings.choice("brain"));
        pet.think(true);
        pet.say("Hmm, let me think...");
        Thread t = new Thread(() -> {
            String problem = !brain.running() ? "no ollama" : !brain.has(model) ? "no brain" : null;
            String reply = problem == null
                    ? brain.ask(question, model, settings.personality(), settings.on("kidFriendly"), settings.name()) : null;
            SwingUtilities.invokeLater(() -> {
                thinking = false;
                pet.think(false);
                if ("no ollama".equals(problem)) {
                    bubble.ask("I need my brain first! It's a free app called Ollama.\nWant me to open its download page?",
                            new String[] {"Open it", "Not now"}, c -> {
                                if (c == 0) Useful.browse(Brain.DOWNLOAD_PAGE);
                            }, window.getBounds(), screenBounds());
                } else if ("no brain".equals(problem)) {
                    bubble.ask("My brain isn't downloaded yet (" + Brain.downloadSize(settings.choice("brain")) + ", just once).\nDownload it now?",
                            new String[] {"Download", "Not now"}, c -> {
                                if (c == 0) downloadBrain(model, question);
                            }, window.getBounds(), screenBounds());
                } else if (reply == null) {
                    pet.say("Hmm... my brain froze. Try again?");
                } else {
                    pet.say(Brain.wrap(reply, 46));
                }
            });
        }, "clawd-brain");
        t.setDaemon(true);
        t.start();
    }

    /** Downloads his brain (a few minutes), then answers the question you asked. */
    private void downloadBrain(String model, String question) {
        pet.say("Downloading my brain... this takes a few minutes.\nI'll answer as soon as it's done!");
        Thread t = new Thread(() -> {
            boolean ok = brain.download(model);
            SwingUtilities.invokeLater(() -> {
                if (ok) answer(question);
                else pet.say("The download didn't work. Is the internet on?");
            });
        }, "clawd-brain-download");
        t.setDaemon(true);
        t.start();
    }

    /**
     * A math question: he doesn't trust himself, so he opens Calculator, says which buttons to press, and watches it.
     * Right: "Good job!". Wrong: "Not quite entered right..." (and he watches for another go, up to three).
     */
    private void mathHelp(MathHelp.Problem sum, int tries) {
        if (tries == 0) {
            pet.say("I wouldn't trust myself to answer right.....\nLet's ask Calculator! Press:\n" + sum.buttons());
            Useful.open("calc");
        }
        if (!Platform.WINDOWS) return; // (watching Calculator's display only works on Windows)
        Thread t = new Thread(() -> {
            String result = "TIMEOUT";
            try {
                String encoded = java.util.Base64.getEncoder().encodeToString(MathHelp.watcherScript().getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
                Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                        "-EncodedCommand", encoded).redirectErrorStream(true).start();
                for (String line : new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                    if (line.startsWith("DONE|") || line.equals("CLOSED") || line.equals("TIMEOUT")) result = line;
                }
            } catch (IOException e) {
                result = "TIMEOUT";
            }
            String got = result;
            SwingUtilities.invokeLater(() -> {
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

    /** Says something nice about your games, at most once a day for each reason (a launcher's open, or just because). */
    private void admireGames(String why) {
        if (!settings.on("games") || job != null || lookingAtGames || pet.busyNow()) return;
        String key = "games:" + why + ":" + java.time.LocalDate.now();
        if (settings.seen(key)) return;
        if (games != null) {
            String nice = Games.compliment(games, new java.util.Random());
            settings.once(key);
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
        if (pet.takeWantsToCreate() && job == null && body.state() == Body.State.HOME) makeSomething();
        Creation typing = pet.coding();
        if (typing != null && ticks % 20 == 0) writeCreation(typing, pet.codingProgress());
        Creation made = pet.takeMade();
        if (made != null) {
            writeCreation(made, 1);
            switch (made.effect()) {
                case CARPET -> {
                    riding = made;
                    body.flyCarpet();
                }
                case ROCKET -> body.rocketRide();
                case POPUP -> Useful.popup(made.file(), made.done());
                case DUCKS -> dropDuck(); // the first one, from the middle of the screen
                default -> { }
            }
        }
        if (pet.duckSpam() && ticks % 7 == 0) dropDuck();
        explosion.tick(FRAME_MS);
        if (ducks.active() || explosion.active()) {
            Rectangle screen = screenBounds();
            ducks.tick(FRAME_MS, groundY, screen.x, screen.x + screen.width);
            if (duckWindow != null) duckWindow.repaint();
        } else if (duckWindow != null && duckWindow.isVisible()) {
            duckWindow.setVisible(false);
        }
        Creation gone = pet.takeDeleted();
        if (gone != null && gone.effect() == Creation.Effect.DUCKS) ducks.poof();
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
            ask.addActionListener(e -> askBox.show("Ask me anything!", window.getBounds(), screenBounds(), this::answer));
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
                item.addActionListener(e -> pet.playPiano(song));
                piano.add(item);
            }
            JMenuItem mine = new JMenuItem("Let me play!");
            mine.addActionListener(e -> yourPiano.show(window.getBounds(), screenBounds(), note -> {
                beeps.piano(note, 400); // you are playing it: it always makes a sound
                pet.listened();
            }));
            piano.addSeparator();
            piano.add(mine);
            fun.add(piano);
            JMenuItem lap = new JMenuItem("Run a lap!");
            lap.addActionListener(e -> {
                if (pet.lap()) body.runLap();
            });
            fun.add(lap);
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
        Shop.Kind last = null;
        for (Shop.Item item : Shop.ITEMS) {
            if (last != null && item.kind() != last) shop.addSeparator();
            last = item.kind();
            boolean owned = settings.owns(item.id());
            String slot = item.kind() == Shop.Kind.HAT ? "hat" : item.kind() == Shop.Kind.HUT ? "hut" : null;
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
                        bubble.show("Yay, " + item.name().toLowerCase(java.util.Locale.ROOT) + "! Thank you!", window.getBounds(), screenBounds());
                    }
                });
            }
            shop.add(entry);
        }
        return shop;
    }

    private final Hut hut = new Hut();

    /** Clawd Points for time together, rides, jobs done, and petting (rubbing the mouse back and forth over him). */
    private void earnPoints(Point mouse, boolean moved) {
        if (!settings.on("earnPoints")) return;
        if (moved) activeFor += FRAME_MS;
        if (activeFor >= 5 * 60_000) {
            activeFor = 0;
            settings.earn(Shop.TIME);
        }
        Body.State state = body.state();
        if (state == Body.State.RIDE && lastBody != Body.State.RIDE) settings.earn(Shop.RIDE);
        lastBody = state;
        if (job != null && job.step() == CleanJob.Step.DONE && job.stepTimeIsNew()) settings.earn(Shop.JOB);
        // Petting: the mouse rubbing back and forth over him
        long now = System.currentTimeMillis();
        if (window.getBounds().contains(mouse) && moved && state == Body.State.HOME) {
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
    private void cameBack(long awayFor) {
        int hourNow = java.time.LocalTime.now().getHour();
        if (hourNow >= 5 && hourNow < 12 && settings.on("morning") && settings.once("morning:" + java.time.LocalDate.now())) {
            pet.morning(settings.name());
        }
        java.time.LocalDate todayNow = java.time.LocalDate.now();
        if (todayNow.getMonthValue() == 11 && todayNow.getDayOfMonth() == 11 && settings.once("veterans:" + todayNow.getYear())) {
            pet.salute();
        }
        long gap = System.currentTimeMillis() - settings.lastSeen();
        if (settings.lastSeen() > 0 && gap >= 2 * 86_400_000L && settings.on("missedYou")) {
            pet.say("Hi.... I missed you..... you've been gone for " + Settings.howLong(gap) + "...."
                    + (settings.homeNamed() ? "\n" + settings.home() + " was so quiet without you." : ""));
        }
        settings.setLastSeen(System.currentTimeMillis());
        int year = java.time.LocalDate.now().getYear();
        if (settings.birthdayToday() && settings.on("birthday") && settings.once("birthday:" + year)) {
            // the surprise: he's nowhere to be seen... for five seconds
            birthdayHiding = true;
            birthdayHideUntil = System.currentTimeMillis() + 5000;
            window.setVisible(false);
        }
    }

    /** After hiding for five seconds: he drops in from the top of the screen with his party hat, cake and blower. */
    private void birthdaySurprise() {
        birthdayHiding = false;
        Rectangle screen = screenBounds();
        body.dropIn(homeX, screen.y - window.getHeight());
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
        if (zoomFrom != null) {
            double speed = zoomFrom.distance(mouse) / Math.max(1, now - zoomAt) * 1000; // px a second
            Rectangle near = window.getBounds();
            near.grow(40, 40);
            // Did the cursor's path cross his face? Then it's a boop. Zooming past close by spins him round.
            double eyesX = window.getX() + Sprite.eyesX() * settings.unit(), eyesY = window.getY() + Sprite.eyesY() * settings.unit();
            boolean acrossFace = false;
            for (int k = 0; k <= 12 && body.state() == Body.State.HOME; k++) {
                double px = zoomFrom.x + (mouse.x - zoomFrom.x) * k / 12.0, py = zoomFrom.y + (mouse.y - zoomFrom.y) * k / 12.0;
                if (Math.abs(px - eyesX) < 7 * settings.unit() && Math.abs(py - eyesY) < 4 * settings.unit()) acrossFace = true;
            }
            if (acrossFace && speed > 450) pet.booped();
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
        bubble.show(jokes.next(), window.getBounds(), screenBounds());
        pet.speak();
    }

    /** Puts on his hat and hut, and lets him use the tricks he's learned. */
    private void useItems() {
        pet.setItems(settings.owns("juggling") && !settings.serious(), settings.owns("waving") && !settings.serious(), settings.wearing("hat"));
        int unit = settings.unit();
        hut.show(settings.wearing("hut"), unit, (int) Math.round(homeX - Sprite.feetX() * unit + 2 * unit), (int) Math.round(groundY),
                !boxed && !hidden && !farewell);
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
                            }, window.getBounds(), screenBounds());
                }
            });
        });
    }

    private void startCleaning() {
        if (!Cleaner.canRecycle()) {
            bubble.show("I can't reach the Recycle Bin on this computer,\nso I won't clean anything.", window.getBounds(), screenBounds());
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
                bubble.show(text, window.getBounds(), screenBounds());
                pet.speak();
            }

            public void ask(String text, String[] buttons, java.util.function.IntConsumer answer) {
                bubble.ask(text, buttons, answer, window.getBounds(), screenBounds());
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
        JMenuItem bye = new JMenuItem("Bye, Clawd");
        bye.addActionListener(e -> System.exit(0));
        menu.add(bye);
        return menu;
    }

    private void tick() {
        ticks++;
        PointerInfo pointer = MouseInfo.getPointerInfo();
        Point mouse = pointer != null ? pointer.getLocation() : lastMouse;
        boolean moved = !mouse.equals(lastMouse);
        lastMouse = mouse;

        // Twice a second: what's in front (a coding app makes him happy; a full-screen game or video hides him)
        if (ticks % 15 == 0) {
            Foreground.Front front = Foreground.front();
            app = front.app();
            devApp = Foreground.isDevApp(app);
            if (job == null) maybeTip(front);
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
            }
            if (hide != hidden) {
                hidden = hide;
                window.setVisible(!hidden);
            }
        }
        // Now and then (once a day, a while after he starts): something nice about your games
        if (ticks % 1800 == 900 && ticks > 30 * 60 * 20 && new java.util.Random().nextInt(6) == 0) admireGames("idle");
        // Every couple of seconds: settings changed from the clawd command (clawd controlpanel)?
        if (ticks % 60 == 0 && Settings.changed() != settingsChanged && !farewell) {
            settingsChanged = Settings.changed();
            String oldSize = settings.size();
            settings = Settings.load();
            pet.setPersonality(settings.personality());
            pet.changeColor(settings.awtColor()); // a few seconds later, suddenly: he'll freak out
            if (!oldSize.equals(settings.size())) resize();
            else if (body.state() == Body.State.HOME) place();
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
            if (welcome != null && welcome.showing()) welcome.follow(window.getBounds(), screenBounds());
            return; // nothing to do until he's out
        }
        if (greetWhenHome && body.state() == Body.State.HOME) {
            greetWhenHome = false;
            pet.poke();
            String hi = settings.restored()
                    ? "Hii.......... I think I remember you...." + (settings.name().isEmpty() ? "" : " " + settings.name() + ", right?")
                    : "Hi" + (settings.name().isEmpty() ? "" : " " + settings.name()) + "!! I'm so happy to be here!";
            bubble.show(hi, window.getBounds(), screenBounds());
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
            body.tick(FRAME_MS, mouse.x, mouse.y, homeX, groundY, 12 * unit, screen.x, screen.x + screen.width);
            pet.follow(body.state());
            creations();
            if (body.takeBoom()) {
                pet.boom();
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
        if (ticks % 900 == 450 && focusUntil == 0) remindMe(nowMs);
        if (ticks % 15 == 7) checkReminders();
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
            bubble.show("...okay. I'm better now.", window.getBounds(), screenBounds());
        }
        double eyesX = window.getX() + Sprite.eyesX() * unit;
        double eyesY = window.getY() + Sprite.eyesY() * unit;
        // the cursor resting on him (he gets shy), or swiping across his face (boop!)
        boolean overHim = Math.abs(mouse.x - eyesX) < 7 * unit && Math.abs(mouse.y - eyesY) < 4 * unit && body.state() == Body.State.HOME;
        pet.hover(overHim && !moved, FRAME_MS);
        pet.tick(FRAME_MS, mouse.x - eyesX, mouse.y - eyesY, moved, devApp);
        int note = pet.takeNote();
        if (note > 0 && mayBeep()) beeps.piano(note, pet.noteLength());
        Pet.Beep beep = pet.takeBeep();
        if (beep != null && mayBeep()) beeps.play(beep);
        String line = pet.takeLine();
        if (line != null) bubble.show(line, window.getBounds(), screenBounds());
        bubble.tick();
        if (bubble.showing()) bubble.follow(window.getBounds(), screenBounds());
        if (welcome != null && welcome.showing()) welcome.follow(window.getBounds(), screenBounds());
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
        if (kind == null || !settings.tips() || hidden || focusUntil > 0) return;
        long now = System.currentTimeMillis();
        if (!Tips.urgent(front) && now - lastTipAt < pet.personality().tipGap()) return;
        String tip = tips.tipFor(front);
        if (tip == null) return;
        lastTipAt = now;
        settings.earn(Shop.TIP);
        bubble.show(tip, window.getBounds(), window.getGraphicsConfiguration().getBounds());
        pet.speak();
    }

    private Welcome welcome;
    private boolean welcomeStarted;

    /** clawd move: he's off to the new computer. He picks up a box and walks off the edge of the screen. */
    private void moveOut() {
        if (job != null) job.stop(body, pet);
        bubble.show("Off to the new place! Bye!", window.getBounds(), screenBounds());
        pet.moving(true);
        Rectangle screen = screenBounds();
        boolean right = homeX > screen.x + screen.width / 2.0;
        body.walkOff(right ? screen.x + screen.width + 200 : screen.x - 200, Long.MAX_VALUE);
        movingOut = true;
    }

    private boolean movingOut;

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
        if (!settings.sounds()) return false;
        if (!settings.on("quietHours")) return true;
        int hour = java.time.LocalTime.now().getHour(), from = settings.number("quietFrom"), to = settings.number("quietTo");
        boolean quiet = from <= to ? hour >= from && hour < to : hour >= from || hour < to;
        return !quiet;
    }

    void start() {
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
                settings = Settings.load(); // a save token may have brought back his color and the rest
                settingsChanged = Settings.changed();
                pet.setColor(settings.awtColor());
                pet.setPersonality(settings.personality());
                resize();
                if (welcome.movedIn()) {
                    // moved in from another computer: he walks in from the side with his boxes, and unpacks
                    boxed = false;
                    window.setVisible(true);
                    Rectangle screen = screenBounds();
                    boolean fromRight = homeX > screen.x + screen.width / 2.0;
                    body.walkIn(fromRight ? screen.x + screen.width + 120 : screen.x - 120);
                    pet.moving(true);
                    return;
                }
                new Box(settings.unit(), (int) Math.round(homeX), (int) Math.round(groundY), () -> {
                boxed = false;
                window.setVisible(true);
                body.launchFrom(homeX, groundY, (Math.random() < 0.5 ? -1 : 1) * (150 + Math.random() * 250)); // out of the box, not from the corner
                if (settings.sounds()) beeps.play(Pet.Beep.WHEE);
                greetWhenHome = true;
                }).show();
            });
            welcome.start(window.getBounds(), screenBounds());
        } else if (!settings.name().isEmpty() && pet.takeLineIfAny() == null && !birthdayHiding) {
            bubble.show("Hi again, " + settings.name() + "!", window.getBounds(), screenBounds());
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
        Rectangle at = window.getBounds();
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
            case "your piano" -> yourPiano.show(window.getBounds(), screenBounds(), note -> {
                beeps.piano(note, 400);
                pet.listened();
                smokeNotes++;
            });
            case "salute" -> pet.salute();
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

    /** For tests: the screen area he'd sit in, without a window. */
    static GraphicsConfiguration screen() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
    }
}
