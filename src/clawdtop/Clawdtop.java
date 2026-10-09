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
    private static final int FRAME_MS = 16;          // his window moves 60 times a second (smooth)...
    private static final int MOOD_MS = FRAME_MS * 2; // ...and his moods and timing tick 30 times a second, as they always have

    private Settings settings = Settings.load();
    private long settingsChanged = Settings.changed();
    private final Pet pet = new Pet(System.nanoTime());
    static { // his menus look like the rest of Windows, and they're compact (small text, snug rows)
        System.setProperty("apple.awt.UIElement", "true"); // (on a Mac: no Dock icon or menu bar for him, like any desktop buddy)
        if (Platform.WINDOWS && !GraphicsEnvironment.isHeadless()) {
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception keepTheDefault) {
                // fine as it is
            }
        }
        if (!GraphicsEnvironment.isHeadless()) {
            for (String key : new String[] {"Menu.font", "MenuItem.font", "CheckBoxMenuItem.font", "RadioButtonMenuItem.font", "PopupMenu.font"}) {
                java.awt.Font f = javax.swing.UIManager.getFont(key);
                if (f != null) javax.swing.UIManager.put(key, new javax.swing.plaf.FontUIResource(f.deriveFont(Math.max(10f, f.getSize2D() * 0.85f))));
            }
            for (String key : new String[] {"Menu.margin", "MenuItem.margin", "CheckBoxMenuItem.margin"}) {
                javax.swing.UIManager.put(key, new javax.swing.plaf.InsetsUIResource(1, 2, 1, 2));
            }
        }
    }

    private final Beeps beeps = new Beeps();
    private boolean wasPlaying; // on his piano (or guitar, or drums) last frame
    private boolean fxStill;    // the ducks and explosions overlay hasn't changed since it was last drawn
    private boolean rightWasDown; // the right mouse button, last frame
    private boolean lastFull, steadyFull; // something full screen (last check, and for a second and a half or so)
    private int fullChecks;
    private boolean readyShown;   // he's shown he's about to hop on your cursor
    private final java.util.ArrayDeque<String> heldLines = new java.util.ArrayDeque<>(); // what he wanted to say while asking you something
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
    private int ticks;          // mood ticks (30 a second): what all the "every so often" checks count
    private int frames;         // every frame (60 a second)
    private boolean newTick;    // this frame is also a mood tick
    private boolean movedSinceMood; // the mouse moved since his last mood tick
    private boolean dropZone;       // you're dragging something near him: his whole window takes drops, not just him
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
                if (dropZone) { // dragging something near him: his whole window catches it (all but invisible)
                    g2.setColor(new Color(0, 0, 0, 1));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
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
        useItems();
    }

    private void listen() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pet.used();
                if (!bubble.asking()) bubble.hide();
                if (SwingUtilities.isLeftMouseButton(e) && body.state() == Body.State.FLY) {
                    body.knockOff(); // poof, no more carpet
                    pet.carpetGone(riding);
                    Diary.write("Somebody clicked my flying carpet away. Mid-air. Rude.");
                    return;
                }
                if (SwingUtilities.isRightMouseButton(e)) rightPressRiding = body.state() != Body.State.HOME; // (dropping him off your cursor: no pet)
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
                if (SwingUtilities.isRightMouseButton(e)) { // a right-click pets him (unless it was to get him off your cursor)
                    if (!rightPressRiding && body.state() == Body.State.HOME) rightClickPet();
                    rightPressRiding = false;
                    return;
                }
                if (dragFrom == Integer.MIN_VALUE) {
                    if (job != null && SwingUtilities.isLeftMouseButton(e) && e.getClickCount() >= 2) jobs().show(canvas, e.getX(), e.getY());
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
                } else if (e.getClickCount() >= 2) { // double-click: his menu
                    jobs().show(canvas, e.getX(), e.getY());
                } else if (!clickedTooMuch()) {
                    pet.poke(); // one click: hi!
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
        else pet.say("I'm in the middle of something!\nTry focus mode again in a moment.");
    }
    private final Piano yourPiano = new Piano();
    private javax.sound.midi.Sequencer sequencer; // playing a whole MIDI file (his piano just shows it)
    private java.io.File sequencerFile;

    /** Plays a whole MIDI file while he plays it on his piano (null stops it). */
    private int midiRound; // which MIDI start is the current one (an older one still loading gets closed)

    private boolean sequencerBand; // the song file's playing with its own instruments (a jam session), not all piano

    private void playMidi(java.io.File file) {
        playMidi(file, false);
    }

    private void playMidi(java.io.File file, boolean fullBand) {
        if (java.util.Objects.equals(file, sequencerFile) && fullBand != sequencerBand && file != null) sequencerFile = null; // (same song, other way: restart)
        sequencerBand = fullBand;
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
            javax.sound.midi.Sequencer opened = null;
            try {
                javax.sound.midi.Sequencer s = javax.sound.midi.MidiSystem.getSequencer();
                s.open();
                opened = s;
                javax.sound.midi.Sequence seq = javax.sound.midi.MidiSystem.getSequence(file);
                s.setSequence(fullBand ? Piano.fullBand(seq) : Piano.pianoOnly(seq)); // (all piano: it's his piano he's playing; a jam: the whole band)
                s.start();
                SwingUtilities.invokeLater(() -> {
                    if (round == midiRound) sequencer = s;
                    else s.close(); // already stopped (or a newer one started)
                });
            } catch (Exception noMidi) {
                if (opened != null) opened.close(); // no sound for it, then (he still plays along)
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

    /** Every song file he knows: your songs folder, and the service songs. */
    private java.util.List<java.io.File> allSongFiles() {
        java.util.List<java.io.File> all = new java.util.ArrayList<>(songs(songsFolder()));
        all.addAll(songs(songsFolder().resolve("veterans")));
        return all;
    }

    /** A song file's name as he'd say it ("navy.mid" is Anchors Aweigh). */
    static String songTitle(java.io.File f) {
        String name = f.getName().replaceAll("(?i)\\.midi?$", "").replace('_', ' ');
        return Piano.SERVICE_SONGS.getOrDefault(name.toLowerCase(java.util.Locale.ROOT), name);
    }

    // ---- Watching and listening along (only once you've said he may, through the computer's own permission box) ----
    private final Hearing ears = new Hearing();
    private final Seeing eyes = new Seeing();
    private long mediaSeenAt, lastScare, lastBop, loudSince;
    private double calmLevel; // how loud it usually is, lately (so a sudden jump stands out)
    private boolean askingMedia;
    private long lastOffer = -10 * 60_000L;

    /** Something playing in front (a video or music)? He offers to watch or listen; once allowed, he does. */
    private void watchAndListen(Foreground.Front front) {
        String media = farewell || boxed ? null : Seeing.mediaIn(front.app(), front.title());
        long now = System.currentTimeMillis();
        if (media != null) mediaSeenAt = now;
        if (media == null) {
            if (now - mediaSeenAt > 20_000 && pet.watching()) pet.watch(false); // the show's over
            if (now - mediaSeenAt > 20_000 && eyes.looking()) eyes.stop();
            if (now - mediaSeenAt > 60_000 && ears.listening()) ears.stop();
            return;
        }
        boolean video = media.equals("video");
        if (video && settings.on("seeing")) {
            Rectangle whole = window.getGraphicsConfiguration().getBounds();
            double scale = window.getGraphicsConfiguration().getDefaultTransform().getScaleX();
            int[] b = front.bounds(); // (real pixels: the video's window, which is what he watches)
            if (b != null && b[2] > b[0]) eyes.lookAt(new Rectangle(whole.x + (int) (b[0] / scale), whole.y + (int) (b[1] / scale),
                    (int) ((b[2] - b[0]) / scale), (int) ((b[3] - b[1]) / scale)).intersection(whole));
            eyes.start(whole);
            if (!pet.watching() && !hidden) pet.watch(true);
        }
        if (settings.on("hearing") && Hearing.possible()) ears.start();
        if (askingMedia || bubble.asking() || pet.busyNow() || hidden || now - lastOffer < 10 * 60_000) return; // (one question at a time, not too often)
        if (video && !settings.on("seeing") && !settings.flag("askedSeeing")) {
            offerSense("Ooh, a video! Want me to watch this with you?", "seeing", "askedSeeing",
                    "Can Clawd see your screen?", "He takes a quick look at how bright your screen is, a couple of times a second, so he can watch along"
                            + " and react (popcorn included). Nothing is recorded, saved or sent anywhere. You can turn it off in his options.");
        } else if (Hearing.possible() && !settings.on("hearing") && !settings.flag("askedHearing")) {
            offerSense(video ? "Want me to hear it too?" : "Ooh, music! Want me to hear this?", "hearing", "askedHearing",
                    "Can Clawd hear your computer's sound?", "He only hears how loud it is, so he can bop along and jump at the scary bits."
                            + " Nothing is recorded, saved or sent anywhere. You can turn it off in his options.");
        }
    }

    /** He asks in his bubble; yes brings up the computer's own permission box; allowed, the option goes on. */
    private void offerSense(String question, String option, String asked, String permission, String detail) {
        askingMedia = true;
        lastOffer = System.currentTimeMillis();
        pet.speak();
        javax.swing.Timer giveUp = new javax.swing.Timer(25_000, e -> { // no answer: never mind (he'll ask another time)
            if (askingMedia && question.equals(bubble.question())) {
                bubble.hide();
                askingMedia = false;
            }
        });
        giveUp.setRepeats(false);
        giveUp.start();
        bubble.ask(question, new String[] {"Yes!", "No thanks"}, choice -> {
            settings.setFlag(asked, true); // (he only asks once: it's in his options after that)
            if (choice != 0) {
                askingMedia = false;
                pet.say("Okay! (You can turn it on in my options later.)");
                return;
            }
            Consent.ask(permission, detail, allowed -> {
                askingMedia = false;
                settings.set(option, String.valueOf(allowed));
                pet.say(allowed ? (option.equals("seeing") ? "Yay! Movie buddy!" : "Yay! Let's hear it!") : "Okay, I won't. (It's in my options if you change your mind.)");
            });
        }, head(), screenBounds());
    }

    /** Every frame or so: reacting to what he sees and hears (a jump at the scary bits, bopping along to music). */
    private void react(long now) {
        if (!ears.listening() && !eyes.looking()) return;
        double level = ears.level();
        boolean suddenLoud = level > 0.55 && calmLevel < 0.18;
        calmLevel = calmLevel * 0.97 + level * 0.03;
        boolean bigFlash = eyes.looking() && eyes.change() > 0.3;
        if (pet.watching() && (suddenLoud || bigFlash) && now - lastScare > 15_000) {
            lastScare = now;
            pet.scare();
        }
        if (!pet.watching() && level > 0.06) { // music playing: after a few seconds of it, he bops along
            if (loudSince == 0) loudSince = now;
            if (now - loudSince > 4000 && now - lastBop > 90_000 && !pet.busyNow() && !hidden) {
                lastBop = now;
                pet.vibe();
            }
        } else if (level < 0.02) {
            loudSince = 0;
        }
    }

    /** A jam session to a song file: recording the parts (the first time), or just playing it again. */
    private void jamTo(java.io.File f, boolean again) {
        worker.execute(() -> {
            Piano.Song song = Piano.fromMidi(f);
            java.util.List<Piano.Part> parts = again ? java.util.List.of() : Piano.jamParts(f); // (its own tracks: drums, bass, guitar...)
            SwingUtilities.invokeLater(() -> {
                if (song == null) pet.say("I tried, but I can't read that music.");
                else if (again ? pet.jamAgain(song) : pet.jam(song, parts)) {
                    earnFun(Shop.SONG);
                    jamFile = f.getName();
                } else pet.say("Give me a sec, I'm busy. Then we jam!");
            });
        });
    }

    private String jamFile; // the jam track he's on (to remember once its parts are recorded)

    /** Opens one of his song folders (made if it isn't there yet). */
    private void openSongFolder(String which) {
        try {
            Path folder = which.isEmpty() ? songsFolder() : songsFolder().resolve(which);
            java.nio.file.Files.createDirectories(folder);
            Useful.lastOpened = System.currentTimeMillis();
            java.awt.Desktop.getDesktop().open(folder.toFile());
        } catch (Exception ignored) {
            // no file browser here
        }
    }

    /** He sings a song file: just his little voice, following the tune. */
    private void singFile(java.io.File f) {
        worker.execute(() -> {
            Piano.Song song = Piano.fromMidi(f);
            SwingUtilities.invokeLater(() -> {
                if (song == null) pet.say("I tried, but I can't read that music.");
                else if (pet.play(Piano.Instrument.VOICE, song)) earnFun(Shop.SONG);
                else pet.say("Give me a sec, I'm busy. Then I'll sing it!");
            });
        });
    }

    /** "Sing ..." : a song he knows by that name (a song file, or one of his own), or any song if you didn't say. */
    private void sing(String name) {
        String want = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\b(the|a|song|please|for me)\\b", " ").strip();
        java.util.List<java.io.File> files = allSongFiles();
        if (want.isEmpty()) {
            java.util.Random r = new java.util.Random();
            if (!files.isEmpty() && r.nextBoolean()) singFile(files.get(r.nextInt(files.size())));
            else if (pet.play(Piano.Instrument.VOICE, Piano.SONGS[r.nextInt(Piano.SONGS.length)])) earnFun(Shop.SONG);
            return;
        }
        for (java.io.File f : files) {
            String title = (songTitle(f) + " " + f.getName()).toLowerCase(java.util.Locale.ROOT);
            if (java.util.Arrays.stream(want.split("\\s+")).allMatch(title::contains)) {
                singFile(f);
                return;
            }
        }
        for (Piano.Song song : Piano.SONGS) {
            if (java.util.Arrays.stream(want.split("\\s+")).allMatch(song.name().toLowerCase(java.util.Locale.ROOT)::contains)) {
                if (pet.play(Piano.Instrument.VOICE, song)) earnFun(Shop.SONG);
                return;
            }
        }
        pet.say("I don't know that one!\nPut its MIDI file in my songs folder (or drop it on me)\nand I'll sing it.");
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
    /** A jam track: it has "jam" in its name (or lives in the jams folder). Any other song file is a piano song. */
    static boolean isJam(java.io.File f) {
        return f.getName().toLowerCase(java.util.Locale.ROOT).contains("jam")
                || (f.getParentFile() != null && f.getParentFile().getName().equalsIgnoreCase("jams"));
    }

    void playDropped(java.io.File midi) {
        if (isJam(midi)) { // a jam track: a jam session (or, if he's recorded it before, it plays again)
            jamTo(midi, settings.jamRecorded(midi.getName()));
            return;
        }
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
        if (bubble.asking()) bubble.hide(); // (you've moved on: the old question goes, so you see this answer)
        answer(question, true);
    }

    /** Answers a question (points: only the first time it's asked, not again after his brain's installed). */
    private void answer(String question, boolean firstTime) {
        if (question.toLowerCase(java.util.Locale.ROOT).matches("\\W*(help|what can you do|what do you do|commands|how do (i|you) use you)\\W*")) {
            pet.say("Things you can ask me:\nAny question (I'll think about it), or math like \"what's 12 times 7\"\n"
                    + "\"remind me at 3pm to call Grandma\", \"set a timer for 5 minutes\"\n\"add homework to my list\", \"stick a note: dentist at 4\"\n"
                    + "\"find my essay\", \"what time is it in Tokyo\", \"clean my link\"\n\"make me a password\", \"keep my computer awake\"\nMore fun stuff is in my menu!");
            return;
        }
        java.util.regex.Matcher singIt = java.util.regex.Pattern.compile("(?i)^\\W*(?:please |can you |could you |will you )?sing(?: me| us)?(?: a song| something| anything)?(?: called| named)?\\s*(.*?)\\W*$").matcher(question);
        if (singIt.matches()) {
            sing(singIt.group(1));
            return;
        }
        if (helped(question)) return; // (notes, countdowns, conversions, the internet, screenshots, locking up)
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
            boolean atATime = question.toLowerCase(java.util.Locale.ROOT).matches(".*\\b(at|around) (\\d|noon|midnight).*");
            String clock = java.time.LocalTime.now().plusSeconds(reminder.inMs() / 1000).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH));
            pet.say(reminder.what().equals("time's up!") ? "Timer set for " + reminder.when() + "! Tick tock."
                    : atATime ? "Okay! I'll remind you at " + clock + ".\n(That's in " + reminder.when() + ". Keep me running till then!)"
                    : "Okay! I'll remind you in " + reminder.when() + ".");
            return;
        }
        if (Reminders.soundsLikeOne(question)) {
            pet.say("I can do reminders like these:\n\"remind me in 10 minutes to stretch\"\n\"remind me at 5pm to call Grandma\"\n\"set a timer for 5 minutes\"\n(\"Tomorrow\" is too tricky for me.)");
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

    /** The handy things he does himself (no brain needed). True if he did one. */
    private boolean helped(String question) {
        String q = question.toLowerCase(java.util.Locale.ROOT).strip().replaceAll("[?!.]+$", "");
        // notes
        java.util.regex.Matcher note = java.util.regex.Pattern.compile("^(?:please )?(?:remember (?:that )?|make a note (?:that )?|note:?\\s*|write down (?:that )?)(.+)$")
                .matcher(question.strip());
        if (note.matches() && !q.startsWith("remember me") && !q.matches("remember (what|when|who|where|how)\\b.*")) {
            settings.addNote(Brain.noBadWords(note.group(1).strip()));
            pet.say("Got it! I'll remember that.\n(Ask me \"what did I tell you to remember?\" anytime.)");
            return true;
        }
        if (q.matches("(what did i (ask|tell) you to remember|what are my notes|my notes|show (me )?my notes|what do you remember|read (me )?my notes)")) {
            java.util.List<String> notes = settings.notes();
            if (notes.isEmpty()) pet.say("You haven't told me to remember anything yet!\n(Try: \"remember that the game is at 6\")");
            else {
                StringBuilder b = new StringBuilder("You told me to remember:");
                int from = Math.max(0, notes.size() - 6);
                for (int i = from; i < notes.size(); i++) b.append("\n- ").append(notes.get(i));
                if (from > 0) b.append("\n(and ").append(from).append(" older ones)");
                pet.say(b.toString());
            }
            return true;
        }
        if (q.matches("(forget|clear|delete|erase) (all )?(my |the )?notes|forget everything i told you")) {
            settings.clearNotes();
            pet.say("Done. My mind is a beautiful blank slate.");
            return true;
        }
        // countdowns and conversions
        String countdown = Helpers.countdown(question, settings.birthday(), java.time.LocalDate.now());
        if (countdown != null) {
            pet.say(countdown);
            return true;
        }
        String converted = Helpers.convert(question);
        if (converted != null) {
            pet.say(converted);
            return true;
        }
        // is the internet working?
        if (q.matches("(is (my |the )?(internet|wifi|wi-fi|connection) (working|on|down|ok|okay|up)|am i (online|connected)|(check|test) (my |the )?(internet|wifi|connection))")) {
            pet.say("Checking...");
            worker.execute(() -> {
                long start = System.currentTimeMillis();
                boolean ok;
                try {
                    var http = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(4)).build();
                    var reply = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://www.google.com/generate_204"))
                            .timeout(java.time.Duration.ofSeconds(5)).method("HEAD", java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
                            java.net.http.HttpResponse.BodyHandlers.discarding());
                    ok = reply.statusCode() < 500;
                } catch (Exception e) {
                    ok = false;
                }
                long ms = System.currentTimeMillis() - start;
                boolean online = ok;
                SwingUtilities.invokeLater(() -> pet.say(online ? "Yep, the internet's working! (Answered in " + ms + " ms" + (ms < 150 ? ". Zoom!)" : ms < 600 ? ".)" : ". A bit slow.)")
                        : "Hmm, I can't reach the internet right now.\nCheck your wifi? (Or try turning the router off and on.)"));
            });
            return true;
        }
        // screenshots
        if (q.matches("(take a |make a |grab a )?(screenshot|screen shot|screen grab|snip)( of my screen)?|how do i (take a )?screenshot")) {
            pet.say(Platform.WINDOWS ? "Opening the screenshot tool! Drag over what you want.\n(Shortcut: Win + Shift + S)"
                    : Platform.MAC ? "Opening Screenshot! (Shortcut: Cmd + Shift + 5)" : "Opening the screenshot tool!");
            if (Platform.WINDOWS) Useful.open("ms-screenclip:");
            else if (Platform.MAC) Useful.open("app:Screenshot");
            else Useful.open("cmd:gnome-screenshot");
            return true;
        }
        // the time somewhere else, choosing for you, opening an app
        String elsewhere = Extras.timeIn(question, java.time.Instant.now(), java.time.ZoneId.systemDefault());
        if (elsewhere != null) {
            pet.say(elsewhere);
            return true;
        }
        String app = Extras.appFor(question);
        if (app != null) {
            pet.say("Opening it!");
            if (Platform.WINDOWS && !app.contains(":")) {
                try {
                    new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", app).start(); // ("start": the way Windows opens things, asking first if it needs to)
                } catch (IOException cant) {
                    pet.say("Hmm, I couldn't open that one.");
                }
            } else Useful.open(app);
            return true;
        }
        // your to-do list
        if (q.matches("(what'?s on |show |read |open )?(me )?(my |the )?(to-?do |to do |todo )?list|(my |the )?(to-?do|to do|todo)s?( list)?|what do i (have|need) to do( today)?")) {
            java.util.List<String> list = settings.todos();
            if (list.isEmpty()) pet.say("Your list's empty! Nothing to do. (Add things: \"add homework to my list\")");
            else {
                StringBuilder b = new StringBuilder("On your list:");
                for (int i = 0; i < Math.min(8, list.size()); i++) b.append("\n").append(i + 1).append(". ").append(list.get(i));
                if (list.size() > 8) b.append("\n(and ").append(list.size() - 8).append(" more)");
                pet.say(b.toString());
            }
            return true;
        }
        java.util.regex.Matcher todo = java.util.regex.Pattern.compile("^(?:please )?(?:add|put) (.+?) (?:to|on|onto) (?:my |the )?(?:to-?do |to do |todo )?list$|^(?:to-?do|to do|todo): (.+)$",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(question.strip().replaceAll("[?!.]+$", ""));
        if (todo.matches()) {
            addTodo(todo.group(1) != null ? todo.group(1) : todo.group(2)); // (as you typed it, capitals and all)
            return true;
        }
        if (q.matches("(clear|empty|delete|erase) my (to-?do |to do )?list")) {
            settings.setTodos(java.util.List.of());
            pet.say("Your list is wiped clean!");
            return true;
        }
        java.util.regex.Matcher crossed = java.util.regex.Pattern.compile("^(?:i'?m |i'?ve |i have |i just |i |just )*(?:finally )?(?:finished|did|done with|done|completed|cross off|check off|tick off|crossed off|cross|check|tick)"
                + " (.+?)(?: off)?(?: (?:of |from )?(?:my |the )?(?:to-?do |to do )?list)?$").matcher(q);
        if (crossed.matches() && !question.strip().endsWith("?")) { // ("did grandma call?" is a question, not a done)
            java.util.List<String> items = Extras.todosMatching(crossed.group(1), settings.todos());
            if (items.size() == 1) {
                doneTodo(items.get(0));
                return true;
            }
            if (items.size() > 1) { // which one?
                java.util.List<String> some = items.subList(0, Math.min(3, items.size()));
                pet.speak();
                bubble.ask("Which one did you finish?", some.toArray(new String[0]), choice -> doneTodo(some.get(choice)), head(), screenBounds());
                bubble.expireIn(30_000);
                return true;
            }
        }
        // the sticky note
        java.util.regex.Matcher note2 = java.util.regex.Pattern.compile("^(?:please )?(?:stick (?:up )?a note|sticky note|post-?it|put up a (?:note|sign))(?: (?:saying|that says|for me))?:? (.+)$")
                .matcher(question.strip().replaceAll("[.!]+$", ""));
        if (note2.matches()) {
            stickNote(Brain.noBadWords(note2.group(1).strip()));
            pet.say("Stuck it up! Click the note when you're done with it.");
            return true;
        }
        if (q.matches("(take down|remove|get rid of|hide) (the |my |your )?(sticky )?(note|sign|post-?it)|done with (the |my )?(sticky )?note")) {
            boolean had = !settings.text("sticky").isEmpty();
            stickNote("");
            pet.say(had ? "Down it comes!" : "I'm not holding up a note right now.");
            return true;
        }
        // finding a file
        java.util.List<String> lookFor = FindFile.wordsIn(question);
        if (lookFor != null) {
            findFile(lookFor);
            return true;
        }
        // screen time
        if (q.matches("how long have i been on (the |my )?(computer|pc|laptop|mac|screen)( today)?|(how much )?screen ?time( today| have i had( today)?)?|how long have i been (on|using) (it|this) today")) {
            countScreenTime(System.currentTimeMillis());
            long ms = screenMsToday;
            pet.say(ms < 60_000 ? "You've barely been on today!" : "You've been on the computer about " + howLong(ms) + " today."
                    + (ms > 4 * 3_600_000L ? "\nThat's a lot! Maybe go outside for a bit?" : ms > 2 * 3_600_000L ? "\nRemember to take breaks!" : ""));
            return true;
        }
        // keeping the computer awake
        if (q.matches("(please )?(keep|make) (my |the )?(computer|pc|laptop|mac|screen) (awake|on)|don'?t let (my |the )?(computer|pc|laptop|mac|screen) (sleep|go to sleep|turn off)|(stay awake|caffeine|coffee) mode")) {
            keepAwake(true);
            return true;
        }
        if (q.matches("(you can )?(let|allow) (my |the )?(computer|pc|laptop|mac|screen) (sleep|go to sleep|nap)( again| now)?|stop keeping (it|my computer|the computer) awake"
                + "|(stop|end|turn off|no more) (the )?(coffee|caffeine|stay awake|keep awake)( mode)?")) {
            if (Awake.on()) keepAwake(false);
            else pet.say("I'm not keeping it awake right now.");
            return true;
        }
        // things to do with what you copied: clean a link, count the words, save it, or a new password to paste
        if (q.matches("(please )?(clean|fix|shorten|tidy|strip) (up )?(my|this|the|that)( copied)? (link|url)( i copied)?( up)?|remove (the )?tracking( from (my |this |the )?(link|url))?")) {
            String copied = clipboardText();
            String clean = Extras.cleanLink(copied);
            if (clean == null) pet.say("Copy a link first (" + COPY_KEYS + "), then ask me again!");
            else if (clean.equals(copied.strip())) pet.say("That link's already clean! No tracking junk on it.");
            else {
                setClipboard(clean);
                pet.say("Cleaned! I took " + (copied.strip().length() - clean.length()) + " characters of tracking junk off it.\nIt's copied, ready to paste.");
            }
            return true;
        }
        if (q.matches("(please )?(make|generate|give|create|get)( me)? (a |an )?(new |strong |good |random )*password|new password|password (please|generator)")) {
            String password = Extras.password(new java.security.SecureRandom());
            setClipboard(password);
            pet.say("Here's a strong one:\n" + password + "\nIt's copied, ready to paste. (I won't remember it!)");
            return true;
        }
        if (q.matches("(count (my |the |these |those )?words|how many words( (did i copy|is (this|that)|are (in )?(this|that|these)|i copied))?|word count)")) {
            String counted = Extras.wordCount(clipboardText());
            pet.say(counted == null ? "Copy some text first (select it, then " + COPY_KEYS + "),\nand ask me again!" : "What you copied: " + counted);
            return true;
        }
        if (q.matches("(please )?save (what i copied|my clipboard|the clipboard|what's (on )?my clipboard|this picture i copied)( to (a file|my desktop))?")) {
            saveClipboard();
            return true;
        }
        // choosing for you ("pizza or tacos?"): last, so it never grabs a question meant for something else
        String picked = Extras.choose(question, new java.util.Random());
        if (picked != null) {
            pet.say(picked);
            return true;
        }
        // locking up
        if (q.matches("(please )?lock (my |the )?(computer|pc|screen|laptop|mac)( now)?")) {
            pet.speak();
            bubble.ask("Lock your computer now?", new String[] {"Lock it", "Never mind"}, choice -> {
                if (choice != 0) return;
                try {
                    if (Platform.WINDOWS) new ProcessBuilder("rundll32.exe", "user32.dll,LockWorkStation").start();
                    else if (Platform.MAC) new ProcessBuilder("pmset", "displaysleepnow").start();
                    else new ProcessBuilder("loginctl", "lock-session").start();
                } catch (Exception cant) {
                    pet.say("Hmm, I couldn't lock it from here.");
                }
            }, head(), screenBounds());
            return true;
        }
        return false;
    }

    // ---- Your to-do list, the sticky note, finding files, finished downloads, screen time, keeping awake ----
    private final Sticky sticky = new Sticky();
    private final java.util.Map<String, Long> downloadsSeen = new java.util.HashMap<>(); // name -> size, when last looked
    private final java.util.Map<String, long[]> downloadsGrowing = new java.util.HashMap<>(); // new ones, not done yet: name -> {size, time, looks the same}
    private boolean downloadsLooked;
    private final java.util.ArrayDeque<Path> downloadsDone = new java.util.ArrayDeque<>(); // to tell you about
    private long screenMsToday, screenSavedAt, lastScreenTick;
    private java.time.LocalDate screenDay;

    /** Adds something to your to-do list. */
    private void addTodo(String what) {
        String item = Brain.noBadWords(what.strip().replaceAll("[.!]+$", ""));
        if (item.isEmpty()) return;
        java.util.List<String> list = new java.util.ArrayList<>(settings.todos());
        list.add(item);
        settings.setTodos(list);
        pet.say("Added to your list: " + item + "\n(" + list.size() + (list.size() == 1 ? " thing" : " things") + " on it. Double-click me > Useful to see it.)");
    }

    /** Crosses something off (and cheers). */
    private void doneTodo(String item) {
        java.util.List<String> list = new java.util.ArrayList<>(settings.todos());
        if (!list.remove(item)) return;
        settings.setTodos(list);
        if (settings.on("earnPoints")) settings.earn(Shop.ASK);
        pet.party(list.isEmpty() ? "Crossed off: " + item + "\nAND YOUR LIST IS EMPTY! You did it all!" : "Crossed off: " + item + "! Nice!\n" + list.size() + " to go.");
        Diary.write("Got something done: " + item + ".");
    }

    /** Puts up his sticky note (or takes it down, for ""). */
    private void stickNote(String text) {
        settings.setText("sticky", text == null ? "" : text.strip());
        showSticky();
    }

    private void showSticky() {
        String text = settings.text("sticky");
        if (text.isEmpty() || hidden) {
            sticky.hide();
            return;
        }
        sticky.show(text, () -> {
            pet.speak();
            bubble.ask("Done with this note?\n\"" + text + "\"", new String[] {"Take it down", "Keep it"}, choice -> {
                if (choice != 0) return;
                stickNote("");
                pet.say("Down it comes! One less thing.");
            }, head(), screenBounds());
        });
    }

    /** "Find my essay": he looks (on his helper thread, a few seconds at most) and shows you what he found. */
    private void findFile(java.util.List<String> words) {
        pet.say("Looking for \"" + String.join(" ", words) + "\"...");
        pet.sniff();
        Path home = Path.of(System.getProperty("user.home"));
        worker.execute(() -> {
            java.util.List<Path> found = FindFile.search(words, FindFile.places(home), 5000);
            SwingUtilities.invokeLater(() -> showFound(found, 0, words, home));
        });
    }

    private void showFound(java.util.List<Path> found, int i, java.util.List<String> words, Path home) {
        if (found.isEmpty()) {
            pet.say("I couldn't find a file called \"" + String.join(" ", words) + "\".\n(I looked in Desktop, Documents, Downloads, Pictures, Music and Videos.)");
            return;
        }
        Path file = found.get(i);
        boolean more = i + 1 < found.size();
        pet.speak();
        bubble.ask((i == 0 ? "Found it! " : "How about this one? ") + file.getFileName() + "\n(in " + FindFile.whereIs(file, home) + ")"
                        + (i == 0 && found.size() > 1 ? "\n(" + (found.size() - 1) + (found.size() == 2 ? " more file matches too.)" : " more files match too.)") : ""),
                more ? new String[] {"Show me", "Open it", "Next one"} : new String[] {"Show me", "Open it", "Thanks!"}, choice -> {
                    if (choice == 0) FindFile.showInFolder(file);
                    else if (choice == 1) FindFile.open(file);
                    else if (more) showFound(found, i + 1, words, home);
                }, head(), screenBounds());
        bubble.expireIn(60_000);
    }

    /** Every few seconds: a download that's just finished? He tells you (once it's stopped growing). */
    private void watchDownloads() {
        if (!settings.on("downloads")) return;
        Path folder = Path.of(System.getProperty("user.home"), "Downloads");
        worker.execute(() -> {
            java.util.Map<String, Long> now = new java.util.HashMap<>();
            java.util.Map<String, Long> changed = new java.util.HashMap<>();
            try (var files = java.nio.file.Files.list(folder)) {
                for (Path f : files.toList()) {
                    String name = f.getFileName().toString();
                    if (FindFile.partial(name) || !java.nio.file.Files.isRegularFile(f)) continue;
                    long size = java.nio.file.Files.size(f);
                    now.put(name, size);
                    changed.put(name, java.nio.file.Files.getLastModifiedTime(f).toMillis());
                }
            } catch (IOException | RuntimeException noFolder) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                if (!downloadsLooked) { // (the first look only learns what's there already)
                    downloadsLooked = true;
                    downloadsSeen.putAll(now);
                    return;
                }
                long recent = System.currentTimeMillis() - 10 * 60_000;
                for (var e : now.entrySet()) {
                    String name = e.getKey();
                    if (downloadsSeen.containsKey(name)) continue;
                    long size = e.getValue(), time = changed.getOrDefault(name, 0L);
                    long[] before = downloadsGrowing.get(name);
                    boolean same = before != null && before[0] == size && before[1] == time;
                    long looks = same ? before[2] + 1 : 0;
                    downloadsGrowing.put(name, new long[] {size, time, looks});
                    // done: the same size and time on three looks in a row (not just a pause: some downloads stop for a moment)
                    if (looks >= 2 && size > 0) {
                        downloadsGrowing.remove(name);
                        downloadsSeen.put(name, e.getValue());
                        if (changed.getOrDefault(name, 0L) > recent) downloadsDone.add(folder.resolve(name)); // (not an old file copied in)
                    }
                }
                downloadsSeen.keySet().retainAll(now.keySet());
                downloadsGrowing.keySet().retainAll(now.keySet());
            });
        });
    }

    /** Tells you about a finished download, when he's free to. */
    private void tellAboutDownloads() {
        if (downloadsDone.isEmpty() || pet.busyNow() || bubble.showing() || hidden || job != null) return;
        Path file = downloadsDone.poll();
        if (!java.nio.file.Files.exists(file)) return;
        long size;
        try {
            size = java.nio.file.Files.size(file);
        } catch (IOException gone) {
            return;
        }
        pet.speak();
        bubble.ask("Your download's done!\n" + file.getFileName() + " (" + FindFile.size(size) + ")", new String[] {"Open it", "Show me", "OK"}, choice -> {
            if (choice == 0) FindFile.open(file);
            else if (choice == 1) FindFile.showInFolder(file);
        }, head(), screenBounds());
        bubble.expireIn(30_000);
    }

    /** Adds up how long you've been on the computer today (moving the mouse in the last few minutes counts). */
    private void countScreenTime(long nowMs) {
        java.time.LocalDate today = java.time.LocalDate.now();
        if (!today.equals(screenDay)) {
            if (screenDay != null) settings.setText("screen." + screenDay, String.valueOf(screenMsToday)); // (the end of yesterday)
            for (String old : settings.textNames("screen.")) { // only the last week is kept
                try {
                    if (java.time.LocalDate.parse(old.substring(7)).isBefore(today.minusDays(7))) settings.setText(old, "");
                } catch (RuntimeException notADate) {
                    settings.setText(old, "");
                }
            }
            screenDay = today;
            screenMsToday = parseLong(settings.text("screen." + today), 0);
            lastScreenTick = nowMs;
        }
        long step = Math.min(nowMs - lastScreenTick, 5_000);
        lastScreenTick = nowMs;
        if (nowMs - lastMoved < 5 * 60_000 && step > 0) screenMsToday += step;
        if (nowMs - screenSavedAt > 5 * 60_000) {
            screenSavedAt = nowMs;
            settings.setText("screen." + today, String.valueOf(screenMsToday));
        }
    }

    private static long parseLong(String s, long otherwise) {
        try {
            return Long.parseLong(s.strip());
        } catch (NumberFormatException e) {
            return otherwise;
        }
    }

    /** "3 hours 12 minutes". */
    static String howLong(long ms) {
        long m = ms / 60_000, h = m / 60;
        m %= 60;
        if (h == 0) return m + (m == 1 ? " minute" : " minutes");
        return h + (h == 1 ? " hour" : " hours") + (m > 0 ? " " + m + (m == 1 ? " minute" : " minutes") : "");
    }

    private boolean awakeWanted; // (what you last asked for: starting takes a moment, and you might change your mind)

    /** Keeps the computer awake (he holds his coffee), or stops. */
    private void keepAwake(boolean on) {
        awakeWanted = on;
        if (!on) {
            Awake.stop();
            pet.setCoffee(false);
            pet.say("Okay, your computer can nap again. *sips the last of the coffee*");
            return;
        }
        pet.say("Brewing...");
        Thread brew = new Thread(() -> { // (its own thread: not stuck behind a file search)
            boolean ok = Awake.start();
            SwingUtilities.invokeLater(() -> {
                if (!awakeWanted) { // you said stop while it was brewing
                    Awake.stop();
                    return;
                }
                pet.setCoffee(ok);
                pet.say(ok ? "Got my coffee! I'll keep your computer awake (no sleeping, no dark screen)\nuntil you tell me to stop, or I go."
                        : "Hmm, I couldn't keep it awake on this computer.");
            });
        }, "clawd-coffee");
        brew.setDaemon(true);
        brew.start();
    }

    /** How you copy, on this computer. */
    static final String COPY_KEYS = Platform.MAC ? "Cmd+C" : "Ctrl+C";

    /** The text you've copied, or "" if it isn't text. */
    static String clipboardText() {
        try {
            var clip = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clip.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                return (String) clip.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            }
        } catch (Exception busy) {
            // (another app has it open this instant)
        }
        return "";
    }

    static void setClipboard(String text) {
        try {
            var copied = new java.awt.datatransfer.StringSelection(text);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(copied, copied);
        } catch (Exception busy) {
            // (another app has it open this instant)
        }
    }

    /** "Save what I copied": a copied picture (a screenshot, say) or text goes into a file on your desktop. */
    private void saveClipboard() {
        Path desktop = desktopFolder != null ? desktopFolder : Path.of(System.getProperty("user.home"), "Desktop");
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm"));
        try {
            var clip = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clip.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.imageFlavor)) {
                java.awt.Image image = (java.awt.Image) clip.getData(java.awt.datatransfer.DataFlavor.imageFlavor);
                java.awt.image.BufferedImage picture = new java.awt.image.BufferedImage(image.getWidth(null), image.getHeight(null), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = picture.createGraphics();
                g.drawImage(image, 0, 0, null);
                g.dispose();
                Path to = Desktop.free(desktop.resolve("Copied picture " + stamp + ".png"));
                javax.imageio.ImageIO.write(picture, "png", to.toFile());
                pet.say("Saved! It's on your desktop:\n" + to.getFileName());
                return;
            }
            String text = clipboardText();
            if (!text.isEmpty()) {
                Path to = Desktop.free(desktop.resolve("Copied text " + stamp + ".txt"));
                java.nio.file.Files.writeString(to, text);
                pet.say("Saved! It's on your desktop:\n" + to.getFileName());
                return;
            }
            pet.say("There's nothing copied right now! Copy a picture or some text first.");
        } catch (Exception cant) {
            pet.say("Hmm, I couldn't save that one.");
        }
    }

    // ---- The desktop: fetching a song you drag near him, and tidying everything into a Neat folder ----
    private Path desktopFolder;                                  // (learned the first time he looks)
    private final java.util.Map<String, Boolean> songNearHim = new java.util.HashMap<>(); // song files on the desktop: near him last time?
    private boolean lookingAtDesktop, desktopLooked;            // (the first look only learns what's already there)
    private Path fetching;                                       // the song file he's off to get
    private int fetchStage;                                      // 1: hopping over to it, 2: bringing it home
    private long fetchSince;
    private final java.util.ArrayDeque<Object[]> tidyQueue = new java.util.ArrayDeque<>(); // {file, x, y} still to tackle
    private int tidyStage, tidiedCount;                          // 1: hopping to the next one
    private long tidySince;
    private String songsOnDesktop = "";                          // the song files there last time (a new one: a look)
    private long dragSince, lastDesktopLook;                     // a drag (holding the button down) just ended: a look
    private boolean draggedSomething;

    /** Each frame (Windows): a mouse drag just ended? (Maybe a song file was dragged near him: worth a look.) */
    private void noticeDrags() {
        if (!Platform.WINDOWS) return;
        boolean down = Foreground.leftButtonDown();
        long now = System.currentTimeMillis();
        if (down && dragSince == 0) dragSince = now;
        else if (!down && dragSince != 0) {
            if (now - dragSince > 200) draggedSomething = true;
            dragSince = 0;
        }
    }

    /**
     * Whether he can see where things are on the desktop without asking: Windows, or a Mac where you've let him ask Finder
     * (macOS checks the first time, so that first time is when you ask him to tidy, not out of nowhere).
     */
    private boolean desktopReadable() {
        return Platform.WINDOWS || (Platform.MAC && settings.flag("finderOk"));
    }

    /** Every few seconds: a song file dragged near him on the desktop? He goes and gets it. */
    private void watchDesktopForSongs() {
        if (!desktopReadable() || lookingAtDesktop || fetchStage != 0 || tidyStage != 0 || job != null || hidden || boxed
                || body.state() != Body.State.HOME || pet.busyNow()) return;
        long now = System.currentTimeMillis();
        boolean look;
        if (desktopFolder == null) look = !desktopLooked; // (the first time: one look, to learn where the desktop really is)
        else {
            String songs;
            try (var files = java.nio.file.Files.list(desktopFolder)) {
                songs = files.filter(f -> Piano.isMidi(f.toFile())).map(f -> f.getFileName().toString()).sorted().toList().toString();
            } catch (IOException | RuntimeException e) {
                songs = "[]";
            }
            boolean newSongs = !songs.equals(songsOnDesktop);
            songsOnDesktop = songs;
            if (songs.equals("[]")) {
                songNearHim.clear();
                return;
            }
            // looking takes a moment (and a little program), so only when something's changed: a new song file, a drag
            // just ended (Windows), or now and then (Mac, where Finder doesn't mind)
            look = newSongs || draggedSomething || (Platform.MAC && now - lastDesktopLook > 10_000);
        }
        draggedSomething = false;
        if (!look || now - lastDesktopLook < 2500) return;
        lastDesktopLook = now;
        lookingAtDesktop = true;
        worker.execute(() -> {
            Desktop.Layout layout = Desktop.look();
            SwingUtilities.invokeLater(() -> {
                lookingAtDesktop = false;
                if (layout == null) {
                    desktopLooked = true;
                    return;
                }
                desktopFolder = layout.folder();
                Rectangle screen = screenBounds();
                double scale = window.getGraphicsConfiguration().getDefaultTransform().getScaleX();
                double tile = 80; // (about one desktop icon's width, in Java's pixels)
                boolean first = !desktopLooked;
                desktopLooked = true;
                String only = System.getProperty("clawdtop.tidyOnly"); // (the screen test: only its own pretend files)
                for (Desktop.Icon icon : layout.icons()) {
                    Path file = Desktop.fileFor(icon, layout.folder());
                    if (file == null || !Piano.isMidi(file.toFile())) continue;
                    if (only != null && !file.getFileName().toString().startsWith(only)) continue;
                    double[] at = Desktop.spot(icon, screen, scale);
                    double x = at[0], y = at[1];
                    boolean near = Math.abs(x - homeX) < 3 * tile && groundY - y < 3.5 * tile && y < groundY;
                    Boolean before = songNearHim.put(file.toString(), near);
                    // dragged in close (or just put down there): off he goes. (Not for ones already there when he started)
                    if (near && !first && (before == null || !before) && fetchStage == 0 && body.state() == Body.State.HOME && !pet.busyNow()) {
                        fetching = file;
                        fetchStage = 1;
                        fetchSince = System.currentTimeMillis();
                        pet.say("Ooh, a song file! Hang on...");
                        body.perchAt(x, y);
                    }
                }
            });
        });
    }

    /** Each frame: how his trip to fetch a song, or to tidy the desktop, is going. */
    private void desktopTrips() {
        long now = System.currentTimeMillis();
        if (fetchStage == 1 && body.state() == Body.State.PERCH) { // got there: he picks it up (into his songs) and hops down
            Path into = Clawdtop.isJam(fetching.toFile()) ? songsFolder().resolve("jams") : songsFolder();
            try {
                java.nio.file.Files.createDirectories(into);
                fetching = java.nio.file.Files.move(fetching, Desktop.free(into.resolve(fetching.getFileName().toString())));
                pet.say("Got it! (It's in my songs now.)");
            } catch (IOException | RuntimeException cant) {
                pet.say("Hmm, I can't pick that one up.");
                fetching = null;
            }
            body.leave();
            fetchStage = 2;
            fetchSince = now;
        } else if (fetchStage == 2 && body.state() == Body.State.HOME) { // home again: and he plays it
            fetchStage = 0;
            if (fetching != null) playDropped(fetching.toFile());
            fetching = null;
        } else if (fetchStage != 0 && now - fetchSince > 20_000) { // (something went wrong: never mind)
            fetchStage = 0;
            fetching = null;
            if (body.state() == Body.State.PERCH || body.state() == Body.State.HOP_TO) body.leave();
        }
        if (tidyStage == 1 && body.state() == Body.State.PERCH && now - tidySince > 800) { // landed on it: tackled into Neat
            Object[] next = tidyQueue.poll();
            if (next != null) {
                Path from = (Path) next[0];
                Path to = Desktop.tidy(from, desktopFolder);
                if (to != null) {
                    settings.addTidied(to, from);
                    tidiedCount++;
                }
            }
            Object[] after = tidyQueue.peek();
            if (after != null) {
                body.perchAt((Double) after[1], (Double) after[2]);
                tidySince = now;
            } else {
                tidyStage = 0;
                body.leave();
                pet.say(tidiedCount == 0 ? "Hmm, I couldn't move any of them." : "All neat! " + tidiedCount + (tidiedCount == 1 ? " file is" : " files are")
                        + " in the Neat folder on your desktop,\nsorted by type. (\"Put my desktop back\" undoes it.)");
                Diary.write("Tidied the desktop. Tackled " + tidiedCount + " files into a Neat folder.");
            }
        } else if (tidyStage == 1 && now - tidySince > 15_000) { // (stuck: stop there)
            tidyStage = 0;
            tidyQueue.clear();
            if (body.state() == Body.State.PERCH || body.state() == Body.State.HOP_TO) body.leave();
        }
    }

    /** Useful > Tidy my desktop: he asks first, then tackles each file into Neat (sorted by type). Nothing's deleted. */
    private void tidyDesktop() {
        if (tidyStage != 0) return;
        if (lookingAtDesktop) { // (he's mid-look already: right after that)
            javax.swing.Timer again = new javax.swing.Timer(1000, e -> tidyDesktop());
            again.setRepeats(false);
            again.start();
            return;
        }
        lookingAtDesktop = true;
        pet.say("Let me take a look...");
        String only = System.getProperty("clawdtop.tidyOnly"); // (the screen test: only its own pretend files)
        worker.execute(() -> {
            Desktop.Layout layout = Desktop.look();
            SwingUtilities.invokeLater(() -> {
                lookingAtDesktop = false;
                if (layout == null) {
                    pet.say(Platform.MAC ? "Hmm, I can't see your desktop. (Mac: System Settings > Privacy & Security >\nAutomation, and let Clawdtop use Finder.)"
                            : "Hmm, I can't see your desktop right now.");
                    return;
                }
                desktopFolder = layout.folder();
                if (Platform.MAC && !settings.flag("finderOk")) settings.setFlag("finderOk", true); // (now he can watch for songs too)
                Rectangle screen = screenBounds();
                double scale = window.getGraphicsConfiguration().getDefaultTransform().getScaleX();
                java.util.List<Object[]> todo = new java.util.ArrayList<>();
                java.util.Set<Path> seen = new java.util.HashSet<>();
                for (Desktop.Icon icon : layout.icons()) {
                    Path file = Desktop.fileFor(icon, layout.folder());
                    if (file == null || !seen.add(file) || Desktop.category(file) == null) continue;
                    if (only != null && !file.getFileName().toString().startsWith(only)) continue;
                    double[] at = Desktop.spot(icon, screen, scale);
                    todo.add(new Object[] {file, at[0], at[1]});
                }
                if (todo.isEmpty()) {
                    pet.say("Your desktop's already neat! (Shortcuts and folders stay where they are.)");
                    return;
                }
                pet.speak();
                bubble.ask("I can tackle " + todo.size() + (todo.size() == 1 ? " file" : " files") + " into a Neat folder on your desktop,\n"
                        + "sorted by type (Pictures, Music, Documents...).\nNothing gets deleted, and I can put them back. Go?",
                        new String[] {"Go!", "Not now"}, choice -> {
                            if (choice != 0) return;
                            if (fetchStage != 0 || tidyStage != 0) {
                                pet.say("Hang on, let me finish this first!");
                                return;
                            }
                            tidyQueue.clear();
                            tidyQueue.addAll(todo);
                            tidiedCount = 0;
                            tidyStage = 1;
                            tidySince = System.currentTimeMillis();
                            Object[] firstOne = tidyQueue.peek();
                            pet.say("Tidy time! HUP!");
                            body.perchAt((Double) firstOne[1], (Double) firstOne[2]);
                        }, head(), screenBounds());
            });
        });
    }

    /** Useful > Put my desktop back: every tidied file goes back where it was (and the empty Neat folders go). */
    private void putDesktopBack() {
        int back = 0;
        java.util.List<String[]> stuck = new java.util.ArrayList<>(); // (open in something just now: kept, for next time)
        Path desktop = desktopFolder;
        for (String[] pair : settings.tidied()) {
            try {
                Path to = Path.of(pair[0]), from = Path.of(pair[1]);
                if (desktop == null) desktop = from.getParent();
                if (java.nio.file.Files.exists(to)) {
                    java.nio.file.Files.move(to, Desktop.free(from));
                    back++;
                }
            } catch (IOException | RuntimeException inUse) {
                stuck.add(pair);
            }
        }
        settings.clearTidied();
        for (String[] pair : stuck) settings.addTidied(Path.of(pair[0]), Path.of(pair[1]));
        if (desktop != null) { // the Neat folders, if they're empty now
            Path neat = desktop.resolve("Neat");
            try (var dirs = java.nio.file.Files.list(neat)) {
                for (Path d : dirs.toList()) {
                    if (!Desktop.CATEGORIES.contains(d.getFileName().toString())) continue; // (only the folders he made)
                    try {
                        java.nio.file.Files.delete(d); // (only works if it's empty)
                    } catch (IOException notEmpty) {
                        // keep it
                    }
                }
                java.nio.file.Files.delete(neat);
            } catch (IOException | RuntimeException keep) {
                // something of yours is in there: it stays
            }
        }
        String still = stuck.isEmpty() ? "" : "\n" + stuck.size() + (stuck.size() == 1 ? " is" : " are") + " open in something, so I left " + (stuck.size() == 1 ? "it" : "them") + ". Try again later!";
        pet.say((back == 0 ? "Everything's already back where it was!" : "Done! " + back + (back == 1 ? " file is" : " files are") + " back where they were.") + still);
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
        makeSomething(true);
    }

    /** asked: you picked Make something (anything goes); otherwise it's his own idea (big ones only if you allow them). */
    private void makeSomething(boolean asked) {
        Creation c = Creation.pick(new java.util.Random(), settings.made(), settings.lastMade(), asked || settings.on("bigSurprises"));
        if (pet.create(c)) settings.addMade(c.id());
    }

    /** Things he codes: the file fills in as he types; then what he made does its thing (or gets deleted). */
    private void creations() {
        if (pet.takeWantsToCreate() && job == null && body.state() == Body.State.HOME && !hidden && !inCorner) makeSomething(false);
        if (pet.takeWantsToJam() && job == null && body.state() == Body.State.HOME && !hidden && !inCorner && mayBeep()) { // a jam, all by himself
            java.util.List<java.io.File> tracks = new java.util.ArrayList<>(songs(songsFolder().resolve("jams")));
            for (java.io.File f : songs(songsFolder())) if (isJam(f)) tracks.add(f);
            if (!tracks.isEmpty()) jamTo(tracks.get(new java.util.Random().nextInt(tracks.size())), false);
        }
        Creation typing = pet.coding();
        if (typing != null && newTick && ticks % 20 == 0) writeCreation(typing, pet.codingProgress());
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
        if (pet.duckSpam() && newTick && ticks % 7 == 0) dropDuck();
        explosion.tick(frameMs);
        if (ducks.active() || explosion.active()) {
            Rectangle screen = screenBounds();
            ducks.tick(frameMs, groundY, screen.x, screen.x + screen.width);
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
            java.util.List<java.io.File> yours = new java.util.ArrayList<>(songs(songsFolder()));
            yours.removeIf(Clawdtop::isJam); // (jam tracks are in Jam session)
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
            javax.swing.JMenu sing = new javax.swing.JMenu("Sing a song");
            JMenuItem anySong = new JMenuItem("Sing me something!");
            anySong.addActionListener(e -> sing(""));
            sing.add(anySong);
            for (Piano.Song song : Piano.SONGS) {
                JMenuItem item = new JMenuItem(song.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT) + song.name().substring(1));
                item.addActionListener(e -> { if (pet.play(Piano.Instrument.VOICE, song)) earnFun(Shop.SONG); });
                sing.add(item);
            }
            java.util.List<java.io.File> singable = songs(songsFolder().resolve("sing"));
            if (!singable.isEmpty()) sing.addSeparator();
            for (java.io.File f : singable) {
                JMenuItem item = new JMenuItem(songTitle(f));
                item.addActionListener(e -> singFile(f));
                sing.add(item);
            }
            fun.add(sing);
            java.util.List<java.io.File> jams = new java.util.ArrayList<>(songs(songsFolder().resolve("jams")));
            for (java.io.File f : songs(songsFolder())) if (isJam(f)) jams.add(f); // (anything with "jam" in its name is a jam track)
            javax.swing.JMenu jam = new javax.swing.JMenu("Jam session");
            for (java.io.File f : jams) {
                boolean done = settings.jamRecorded(f.getName());
                JMenuItem item = new JMenuItem((done ? "Listen: " : "Record: ") + songTitle(f));
                item.addActionListener(e -> jamTo(f, done));
                jam.add(item);
                if (done) {
                    JMenuItem again = new JMenuItem("    (record it again)");
                    again.addActionListener(e -> jamTo(f, false));
                    jam.add(again);
                }
            }
            if (!jams.isEmpty()) jam.addSeparator();
            JMenuItem jamFolder = new JMenuItem("Your jam tracks folder (put MIDI files in it)...");
            jamFolder.addActionListener(e -> openSongFolder("jams"));
            jam.add(jamFolder);
            fun.add(jam);
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
            if (Platform.WINDOWS || Platform.MAC) {
                JMenuItem tidyUp = new JMenuItem("Tidy my desktop");
                tidyUp.addActionListener(e -> tidyDesktop());
                useful.add(tidyUp);
                if (!settings.tidied().isEmpty()) {
                    JMenuItem putBack = new JMenuItem("Put my desktop back");
                    putBack.addActionListener(e -> putDesktopBack());
                    useful.add(putBack);
                }
            }
            JMenuItem clean = new JMenuItem("Clean a folder...");
            clean.addActionListener(e -> startCleaning());
            useful.add(clean);
        }
        JMenuItem focusItem = new JMenuItem(focusUntil > 0 ? "Stop the focus timer (" + Math.max(1, (focusUntil - System.currentTimeMillis()) / 60_000) + " min left)" : "Focus timer (25 min)");
        focusItem.addActionListener(e -> focus(focusUntil == 0));
        useful.add(focusItem);
        JMenuItem watchItem = new JMenuItem(stopwatchFrom > 0 ? "Stop the stopwatch" : "Start a stopwatch");
        watchItem.addActionListener(e -> stopwatch(stopwatchFrom == 0));
        useful.add(watchItem);
        javax.swing.JMenu todoMenu = new javax.swing.JMenu(settings.todos().isEmpty() ? "To-do list" : "To-do list (" + settings.todos().size() + ")");
        for (String item : settings.todos()) {
            javax.swing.JCheckBoxMenuItem box = new javax.swing.JCheckBoxMenuItem(item.length() > 40 ? item.substring(0, 39) + "..." : item);
            box.addActionListener(e -> doneTodo(item));
            todoMenu.add(box);
        }
        if (!settings.todos().isEmpty()) todoMenu.addSeparator();
        JMenuItem addOne = new JMenuItem("Add something...");
        addOne.addActionListener(e -> askBox.show("What do you need to do?", "Like \"homework\" or \"call Grandma\"", "Add it", head(), screenBounds(), this::addTodo));
        todoMenu.add(addOne);
        useful.add(todoMenu);
        JMenuItem noteItem = new JMenuItem(settings.text("sticky").isEmpty() ? "Stick up a note..." : "Take down the sticky note");
        noteItem.addActionListener(e -> {
            if (settings.text("sticky").isEmpty()) {
                askBox.show("What should the note say?", "Like \"dentist at 4\" (click the note when you're done with it)", "Stick it up", head(), screenBounds(), text -> {
                    if (text.isBlank()) return;
                    stickNote(Brain.noBadWords(text));
                    pet.say("Stuck it up! Click the note when you're done with it.");
                });
            } else {
                stickNote("");
                pet.say("Down it comes!");
            }
        });
        useful.add(noteItem);
        JMenuItem findItem = new JMenuItem("Find a file...");
        findItem.addActionListener(e -> askBox.show("What's the file called?", "Part of the name is fine, like \"essay\" or \"birthday\"", "Find it", head(), screenBounds(), text -> {
            java.util.List<String> words = FindFile.wordsIn("find my " + text);
            if (words != null) findFile(words);
        }));
        useful.add(findItem);
        javax.swing.JCheckBoxMenuItem awakeItem = new javax.swing.JCheckBoxMenuItem("Keep my computer awake", Awake.on());
        awakeItem.addActionListener(e -> keepAwake(!Awake.on()));
        useful.add(awakeItem);
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
        menu.add(settingsMenu());
        return menu;
    }

    /** The shop: what Clawd Points buy (and putting on what he already has). */
    private javax.swing.JMenu shop() {
        javax.swing.JMenu shop = new javax.swing.JMenu("Shop (" + settings.points() + " Clawd Points)");
        for (Shop.Kind kind : Shop.Kind.values()) {
            javax.swing.JMenu section = new javax.swing.JMenu(switch (kind) {
                case HAT -> "Hats";
                case SHIRT -> "Shirts";
                case TRICK -> "Tricks";
            });
            String slot = switch (kind) {
                case HAT -> "hat";
                case SHIRT -> "shirt";
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


    /** Clawd Points for time together, rides, jobs done, and petting (rubbing the mouse back and forth over him). */
    private void earnPoints(Point mouse, boolean moved) {
        if (moved) activeFor += frameMs;
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
    private boolean rightPressRiding; // the right button went down while he was riding (so it was to drop him off)

    /** A right-click pets him: hearts, and points once in a while (not for spamming). */
    private void rightClickPet() {
        pet.used();
        if (pet.sleepy()) return; // (asleep: only a left click wakes him)
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
        // (asleep, he stays asleep till you click him: the hellos wait till then)
        greetings.clear();
        long gap = System.currentTimeMillis() - settings.lastSeen();
        if (settings.lastSeen() > 0 && gap >= 2 * 86_400_000L && settings.on("missedYou")) {
            String missed = "Hi.... I missed you..... you've been gone for " + Settings.howLong(gap) + "...."
                    + (settings.homeNamed() ? "\n" + settings.home() + " was so quiet without you." : "");
            greetings.add(() -> pet.say(missed));
        }
        String[] served = settings.service();
        String saluteKey = "salute:" + java.time.LocalDate.now();
        if (served != null && !settings.seen(saluteKey)) { // a salute for your service, once a day
            greetings.add(() -> { if (pet.saluteYou(settings.name(), served[0], served[1])) settings.once(saluteKey); });
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
            else if (speed > 6500 && near.contains(mouse)) pet.spin(); // (really zooming right past him: not just heading for the clock)
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
    /** The next joke: they go round in an order kept from one start to the next, so he doesn't repeat himself. */
    private String nextJoke() {
        int told = settings.count("jokesTold");
        settings.addOne("jokesTold");
        return Jokes.nth(settings.metDate().toString().hashCode(), told);
    }
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
        bubble.show(nextJoke(), head(), screenBounds());
        pet.speak();
    }

    /** Puts on his hat and shirt, and lets him use the tricks he's learned. */
    private void useItems() {
        if (pet == null || settings == null) return;
        pet.setItems(settings.owns("juggling") && !settings.serious(), settings.owns("waving") && !settings.serious(), settings.wearing("hat"));
        pet.setShirt(settings.wearing("shirt"));
    }

    /** Huts are gone from the shop: if you'd bought one, its points come back. */
    private void refundHuts() {
        int back = 0;
        for (String[] hut : new String[][] {{"cardboard-hut", "20"}, {"wooden-hut", "60"}, {"castle", "150"}}) {
            if (settings.owns(hut[0])) {
                settings.disown(hut[0]);
                back += Integer.parseInt(hut[1]);
            }
        }
        if (!settings.wearing("hut").isEmpty()) settings.setWearing("hut", "");
        if (back > 0) {
            settings.setPoints(settings.points() + back);
            pet.say("Huts are gone from the shop, so here are your " + back + " Clawd Points back!");
        }
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
        if ((!settings.on("restart") || settings.seen("restart:" + today)) && (!settings.on("diskSpace") || settings.seen("disk:" + today))) return; // (both said today)
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

    private javax.swing.JMenu settingsMenu() {
        javax.swing.JMenu menu = new javax.swing.JMenu("Settings");
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
        if (a.equalsIgnoreCase("WindowsTerminal")) return "Windows Terminal";
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
    private final java.util.Set<Long> seenPrograms = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean tackleReady; // he's free to tackle (worked out every frame, read by Windows' window news too)

    /** A new app just opened in front? Hide it, and off he goes to its taskbar icon. */
    private void watchForLaunch() {
        if (!WindowTricks.available()) return;
        WindowTricks.watchShown(this::tryTackle); // (the instant a new window shows: no flash. The check below is the backup)
        long now = System.currentTimeMillis();
        tackleReady = tackleWindow == 0 && settings.on("tackle") && !settings.serious() && !hidden && !boxed && body.state() == Body.State.HOME
                && job == null && !pet.busyNow() && !inCorner && dragFrom == Integer.MIN_VALUE;
        if (tackleWindow != 0) {
            // something went wrong (he got picked up, say): just open it
            if (now > tackleUntil || (!tackleLooking && tackleStarted && body.state() != Body.State.TACKLE)) endTackle();
            return;
        }
        long front = WindowTricks.front();
        if (front == lastFrontWindow) return;
        lastFrontWindow = front;
        tryTackle(front);
    }

    /**
     * A window that's just appeared: if it's a new app's first window (just opened, not one he opened himself), it
     * vanishes at once and he goes for its icon. Runs on Windows' window-news thread or the Swing thread, so it's quick.
     */
    private synchronized void tryTackle(long hwnd) {
        if (!tackleReady || tackleWindow != 0 || hwnd == 0 || !WindowTricks.appWindow(hwnd)) return;
        long pid = WindowTricks.processOf(hwnd);
        long me = ProcessHandle.current().pid();
        if (pid == 0 || pid == me || seenPrograms.contains(pid)) return; // (each program only the once)
        ProcessHandle program = ProcessHandle.of(pid).orElse(null);
        if (program == null) return;
        java.time.Instant started = program.info().startInstant().orElse(null);
        if (started == null || java.time.Duration.between(started, java.time.Instant.now()).toMillis() > 8000) return; // not just opened
        if (program.parent().map(p -> p.pid() == me).orElse(false) || System.currentTimeMillis() - Useful.lastOpened < 10_000) return; // his own
        if (!seenPrograms.add(pid)) return;
        long style = WindowTricks.vanish(hwnd);
        if (style < 0) return;
        tackleWindow = hwnd;
        tackleStyle = style;
        tackleStarted = false;
        tackleUntil = System.currentTimeMillis() + 7000;
        String exe = program.info().command().map(c -> Path.of(c).getFileName().toString()).orElse("");
        String title = WindowTricks.title(hwnd);
        SwingUtilities.invokeLater(() -> beginTackle(hwnd, exe, title));
    }

    private volatile boolean tackleStarted; // (beginTackle has run: he's looking for the icon)

    /** The window's hidden: he says so, and goes looking for its icon on the taskbar. */
    private void beginTackle(long front, String exe, String title) {
        if (tackleWindow != front) return;
        long now = System.currentTimeMillis();
        tackleStarted = true;
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
        if (tries >= 240) return; // no good moment in an hour: there's always tomorrow
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

    private long lastFrameAt;
    private int frameMs = FRAME_MS, moodMs = MOOD_MS, moodSoFar; // how long this frame and this mood tick really took (ms)

    private void tick() {
        long nowNanos = System.nanoTime(); // (by the real clock: on a slow or busy computer he doesn't go in slow motion)
        frameMs = lastFrameAt == 0 ? FRAME_MS : (int) Math.max(1, Math.min(100, (nowNanos - lastFrameAt) / 1_000_000));
        lastFrameAt = nowNanos;
        frames++;
        moodSoFar += frameMs;
        newTick = moodSoFar >= MOOD_MS - 2;
        if (newTick) {
            ticks++;
            moodMs = Math.min(200, moodSoFar);
            moodSoFar = 0;
        }
        PointerInfo pointer = MouseInfo.getPointerInfo();
        Point mouse = pointer != null ? pointer.getLocation() : lastMouse;
        boolean moved = !mouse.equals(lastMouse);
        lastMouse = mouse;
        movedSinceMood |= moved;
        closeMenuOnClickAway(mouse);
        Rectangle near = window.getBounds();
        near.grow(220, 220);
        boolean zone = Foreground.leftButtonDown() && near.contains(mouse) && dragFrom == Integer.MIN_VALUE && window.isShowing()
                && !window.getBounds().contains(mouse) || (dropZone && Foreground.leftButtonDown()); // (stays on till you let go)
        if (zone != dropZone) dropZone = zone;
        boolean right = Foreground.rightButtonDown();
        if (right && !rightWasDown && body.state() == Body.State.RIDE) body.dropOff(); // right-click: he hops down (no flick needed)
        rightWasDown = right;
        watchForLaunch();

        // Twice a second: what's in front (a coding app makes him happy; a full-screen game or video hides him)
        if (newTick && ticks % 15 == 0) {
            Foreground.Front front = Foreground.front();
            app = front.app();
            devApp = Foreground.isDevApp(app);
            if (job == null) maybeTip(front);
            followAppSpot(front);
            watchAndListen(front);
            if (Games.launcher(app) && !hidden) admireGames("launcher");
            DisplayMode mode = window.getGraphicsConfiguration().getDevice().getDisplayMode();
            boolean fullNow = job == null && !devApp && Foreground.fullScreen(mode.getWidth(), mode.getHeight());
            fullChecks = fullNow == lastFull ? fullChecks + 1 : 1; // (it has to stay that way a moment: not just Start or Alt+Tab)
            lastFull = fullNow;
            if (fullChecks >= (fullNow ? 3 : 2)) steadyFull = fullNow;
            boolean fullScreen = steadyFull;
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
                showSticky();
                window.setVisible(!hidden);
                if (hidden) bubble.hide();
            }
        }
        // Now and then (once a day, a while after he starts): something nice about your games
        if (newTick && ticks % 1800 == 900 && ticks > 30 * 60 * 20 && new java.util.Random().nextInt(6) == 0) admireGames("idle");
        // Every couple of seconds: settings changed from the clawd command (clawd controlpanel)?
        long settingsNow = newTick && ticks % 60 == 0 ? Settings.changed() : settingsChanged;
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
        if (newTick && ticks % 90 == 45 && !farewell) {
            worker.execute(() -> { // looking through every program takes a moment: not on the drawing thread
                java.util.Set<String> now = Foreground.openDevPrograms();
                SwingUtilities.invokeLater(() -> {
                    if (devPrograms != null && !now.containsAll(devPrograms)) pet.sad();
                    devPrograms = now;
                });
            });
        }
        // And anything it asked him to do: a mood, or goodbye
        if (newTick && ticks % 30 == 0 && !farewell) {
            String asked = Settings.takeAsk();
            if (asked != null && asked.equals("quit")) System.exit(0); // clawd stop (his shutdown hooks tidy up)
            if (asked != null && asked.equals("goodbye")) sayGoodbye();
            else if (asked != null && asked.equals("moveout")) moveOut();
            else if (asked != null && asked.startsWith("mood ")) pet.ask(asked.substring(5));
        }
        if (farewell) {
            pet.tick(frameMs, 0, 0, false, false);
            String line = pet.takeLine();
            if (line != null) bubble.show(line, new Rectangle(window.getX(), window.getY() + FAREWELL_ROOM * settings.unit(),
                    Sprite.WIDTH * settings.unit(), Sprite.HEIGHT * settings.unit()), screenBounds());
            if (pet.crumbled() > 0) bubble.hide();
            if (pet.gone()) System.exit(0);
            canvas.repaint();
            return;
        }
        // Every few seconds, back on top (the taskbar likes to come up over everything when it's clicked)
        if (newTick && ticks % 90 == 0 && !hidden && settings.on("onTop")) {
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
                job.tick(frameMs, body, pet);
                if (job.over()) job = null;
            }
            body.setCeiling(screen.y);
            body.setUnit(unit);
            body.setMistakes(settings.on("mistakes"));
            if (moveOutPending && body.state() == Body.State.HOME) walkOffToMove();
            // the cursor's waiting beside him: he gets ready (a little hop, arms up) before he jumps on, so it's no surprise
            if (body.hopReady() > 0.35 && !readyShown) {
                readyShown = true;
                pet.readyToHop();
            } else if (body.hopReady() == 0) {
                readyShown = false;
            }
            body.tick(frameMs, mouse.x, mouse.y, homeX, groundY, 12 * unit, screen.x, screen.x + screen.width);
            pet.follow(body.state());
            creations();
            if (body.takeMissed()) pet.say("Missed! ...I meant to do that.");
            if (body.takeTackled()) {
                long popped = tackleWindow;
                endTackle(); // POP: there it is
                WindowTricks.toFront(popped);
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
        if (newTick && ticks % 30 == 0) maybeJoke();
        if (newTick && ticks % 10 == 0) checkCapsLock();
        // coming back after a long while counts as a new login
        long nowMs = System.currentTimeMillis();
        if (moved) {
            if (nowMs - lastMoved > 10 * 60_000 && !boxed) cameBack(nowMs - lastMoved);
            lastMoved = nowMs;
            if (newTick && ticks % 1800 == 0) settings.setLastSeen(nowMs);
        }
        if (birthdayHiding && nowMs > birthdayHideUntil && !hidden) birthdaySurprise(); // (not over your video: after it)
        if (pendingBirthday && body.state() != Body.State.FALL) {
            pendingBirthday = false;
            pet.birthday(settings.name());
        }
        if (newTick && ticks % 300 == 150) checkTimes();
        if (newTick) noticeDrags();
        if (newTick && ticks % 90 == 45) watchDownloads();
        if (newTick && ticks % 30 == 7) {
            tellAboutDownloads();
            countScreenTime(nowMs);
        }
        sticky.place(window.getX(), window.getY(), window.getWidth(), window.getHeight(), screenBounds(), bubble.bounds());
        if (newTick && ticks % 90 == 20) watchDesktopForSongs();
        desktopTrips();
        if (newTick && ticks % 3 == 0) react(nowMs);
        if (newTick && ticks % 90 == 60) updateTag(); // (points change as you earn and spend them)
        if (!greetings.isEmpty() && newTick && ticks % 15 == 3 && !pet.busyNow() && pet.takeLineIfAny() == null && !bubble.showing()
                && body.state() == Body.State.HOME && !hidden && !boxed && !birthdayHiding) greetings.poll().run(); // one at a time
        if (newTick && ticks % 900 == 450 && focusUntil == 0) remindMe(nowMs);
        if (newTick && ticks % 15 == 7) checkReminders();
        if (newTick && ticks % 30 == 11) veteransSongs(nowMs);
        // The screen changed (another monitor, a new resolution, the taskbar moved)? Back to his spot on it
        if (newTick && ticks % 90 == 30 && body.state() == Body.State.HOME && !inCorner) {
            Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            if (lastUsable != null && !usable.equals(lastUsable)) place();
            lastUsable = usable;
        }
        updateClock();
        if (newTick && ticks % 150 == 75) worker.execute(() -> {
            Power.criticalLevel(); // asked once, here in the background
            Power.State b = Power.now();
            SwingUtilities.invokeLater(() -> checkBattery(b));
        });
        checkZoom(mouse);
        if (movingOut && body.state() == Body.State.OUT) System.exit(0); // gone to the new computer
        if (huffed && body.state() == Body.State.HOME) {
            huffed = false;
            bubble.show("...okay. I'm better now.", head(), screenBounds());
        }
        double eyesX = window.getX() + Sprite.eyesX() * unit;
        double eyesY = window.getY() + Sprite.eyesY() * unit;
        // the cursor resting on him (he gets shy), or swiping across his face (boop!)
        boolean overHim = Math.abs(mouse.x - eyesX) < 7 * unit && Math.abs(mouse.y - eyesY) < 4 * unit && body.state() == Body.State.HOME;
        // using him (the cursor on him, riding, a job, a question or game open): he stays awake
        if (head().contains(mouse) || body.state() != Body.State.HOME || job != null || bubble.asking()
                || (game != null && game.showing()) || yourPiano.showing()) pet.used();
        if (newTick) { // (his moods at their own pace; his window moves every frame)
            pet.hover(overHim && !movedSinceMood, moodMs);
            pet.tick(moodMs, mouse.x - eyesX, mouse.y - eyesY, movedSinceMood, devApp);
            movedSinceMood = false;
        }
        boolean playingNow = pet.mood() == Pet.Mood.PIANO;
        if (wasPlaying && !playingNow) beeps.stopAll(); // he stopped: so does the music
        wasPlaying = playingNow;
        int note = pet.takeNote();
        if (note > 0 && mayBeep() && pet.playingMidi() == null) beeps.play(pet.instrument(), note, pet.noteLength());
        if (pet.takeJamRecorded() && jamFile != null) settings.addJam(jamFile); // (next time: he just plays it)
        java.io.File band = pet.jamPlaying();
        playMidi(!mayBeep() ? null : band != null ? band : pet.playingMidi(), band != null); // (muted, quiet hours or hidden: the song stops)
        Pet.Beep beep = pet.takeBeep();
        if (beep != null && mayBeep()) beeps.play(beep);
        // what he says (not while he's hidden for your video, and never over a question he's asking you)
        String line = pet.takeLine();
        if (hidden) line = null;
        else if (bubble.asking()) { // keep it for after you've answered
            if (line != null) {
                heldLines.add(line);
                while (heldLines.size() > 3) heldLines.poll(); // (just the latest few)
            }
            line = null;
        } else if (line == null && !heldLines.isEmpty() && !bubble.showing()) {
            line = heldLines.poll();
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
        updateTag();
        pet.setHome(s.home());
    }

    /** His name tag (what shows when you rest the cursor on him), with his points kept up to date. */
    private void updateTag() {
        Settings s = settings;
        String tag = (s.on("nameTag") ? "Clawd" : "") + (s.on("nameTag") && s.on("pointsTag") ? " - " : "") + (s.on("pointsTag") ? s.points() + " Clawd Points" : "");
        String now = tag.isEmpty() ? null : tag;
        if (!java.util.Objects.equals(now, canvas.getToolTipText())) canvas.setToolTipText(now);
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
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { // never leave an app see-through (or the computer kept awake)
            Awake.stop();
            if (screenDay != null) settings.setText("screen." + screenDay, String.valueOf(screenMsToday)); // (today's screen time so far)
            long w = tackleWindow;
            if (w != 0) WindowTricks.reveal(w, tackleStyle);
        }));
        Updater.tidy();
        refundHuts();
        Settings.takeAsk(); // (anything the clawd command asked before he started: old news)
        javax.swing.Timer updates = new javax.swing.Timer(20_000, e -> checkForUpdate()); // once he's settled in (and once a day, if he's left running)
        updates.setDelay(3_600_000);
        updates.setInitialDelay(20_000);
        updates.start();
        try {
            for (String folder : new String[] {"veterans", "sing", "jams"}) java.nio.file.Files.createDirectories(songsFolder().resolve(folder)); // so you can see where songs go
        } catch (IOException ignored) {
            // no songs folder, then
        }
        useOptions();
        pet.setColor(settings.awtColor());
        pet.setPersonality(settings.personality());
        pet.setBirthdayToday(settings.birthdayToday());
        window.setVisible(true);
        useItems();
        showSticky(); // (the note you stuck up last time)
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

    // "Only one of me": held for as long as he runs. (Kept here, not in local variables: otherwise Java tidies the
    // file away after a while, the lock goes with it, and a second Clawd could start or clawd stop couldn't find him.)
    private static RandomAccessFile runningFile;
    private static FileLock runningLock;

    public static void main(String[] args) throws IOException {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Clawdtop needs a screen to sit on.");
            return;
        }
        // Only one Clawd at a time
        Path folder = Settings.folder();
        Files.createDirectories(folder);
        runningFile = new RandomAccessFile(folder.resolve("running.lock").toFile(), "rw");
        runningLock = runningFile.getChannel().tryLock();
        if (runningLock == null) return; // he's running already
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
        if (action.startsWith("jam ")) { // jam to a file: "jam C:\...\closet_jam.mid" (records the parts), "jam again ..." (just plays it)
            boolean again = action.startsWith("jam again ");
            jamTo(new java.io.File(action.substring(again ? 10 : 4)), again);
            return;
        }
        Rectangle at = head();
        switch (action) {
            case "menu" -> jobs().show(canvas, at.width / 2, at.height / 3);
            case "settings menu" -> jobs().show(canvas, at.width / 2, at.height / 3);
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
            case "salute me" -> pet.saluteYou("Tester", "Army National Guard", "Retired");
            case "sing" -> pet.play(Piano.Instrument.VOICE, Piano.SONGS[0]);
            case "sing army" -> answer("sing the army song");
            case "cpdance" -> pet.smokeMood(Pet.Mood.CPDANCE, 5200);
            case "watch" -> pet.watch(true);
            case "scare" -> pet.scare();
            case "stop watching" -> pet.watch(false);
            case "offer seeing" -> offerSense("Ooh, a video! Want me to watch this with you?", "seeing", "askedSeeing",
                    "Can Clawd see your screen?", "(test) He takes a quick look at how bright your screen is.");
            case "yes" -> bubble.press(0);
            case "tidy" -> tidyDesktop();
            case "download done" -> downloadsDone.add(Path.of(System.getProperty("smoke.download", "missing")));
            case "dragged" -> draggedSomething = true;
            case "put back" -> putDesktopBack();
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
                if (action.startsWith("say ")) { // (asking him something, as if typed in the ask box)
                    answer(action.substring(4));
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
