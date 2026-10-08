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

    private Clawdtop() {
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
                else Sprite.drawTurned(g2, pet, settings.unit(), body.angle());
                g2.dispose();
            }
        };
        canvas.setOpaque(false);
        canvas.setBackground(new Color(0, 0, 0, 0));
        window.setBackground(new Color(0, 0, 0, 0));
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
                    menu().show(canvas, e.getX(), e.getY());
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
                } else {
                    pet.poke();
                    jobs().show(canvas, e.getX(), e.getY());
                }
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
    }

    /** What you can ask him to do (left-click him). */
    private JPopupMenu jobs() {
        JPopupMenu menu = new JPopupMenu();
        if (job == null) {
            JMenuItem clean = new JMenuItem("Clean a folder...");
            clean.addActionListener(e -> startCleaning());
            menu.add(clean);
        } else {
            JMenuItem stop = new JMenuItem("Stop cleaning");
            stop.addActionListener(e -> job.stop(body, pet));
            menu.add(stop);
        }
        JMenuItem joke = new JMenuItem("Tell me a joke");
        joke.addActionListener(e -> tellJoke());
        menu.add(joke);
        if (settings.owns("dancing")) {
            JMenuItem dance = new JMenuItem("Dance!");
            dance.addActionListener(e -> pet.dance());
            menu.add(dance);
        }
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
    private final Jokes jokes = new Jokes(System.nanoTime());
    private long lastJokeAt = System.currentTimeMillis();
    private long jokeJitter = (long) (Math.random() * 120_000);

    /** Now and then (as often as you set), when he's not busy, he tells a joke. */
    private void maybeJoke() {
        long now = System.currentTimeMillis();
        if (now - lastJokeAt < Jokes.gap(settings.jokes()) + jokeJitter) return;
        Pet.Mood m = pet.mood();
        if (job != null || bubble.showing() || hidden || (m != Pet.Mood.IDLE && m != Pet.Mood.SIT)) return;
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
        pet.setItems(settings.owns("juggling"), settings.owns("waving"), settings.wearing("hat"));
        int unit = settings.unit();
        hut.show(settings.wearing("hut"), unit, (int) Math.round(homeX - Sprite.feetX() * unit + 2 * unit), (int) Math.round(groundY),
                !boxed && !hidden && !farewell);
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
            DisplayMode mode = window.getGraphicsConfiguration().getDevice().getDisplayMode();
            boolean fullScreen = job == null && !devApp && Foreground.fullScreen(mode.getWidth(), mode.getHeight());
            if (fullScreen != hidden) {
                hidden = fullScreen;
                window.setVisible(!hidden);
            }
        }
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
        if (ticks % 90 == 0 && !hidden) {
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
            body.tick(FRAME_MS, mouse.x, mouse.y, homeX, groundY, 12 * unit, screen.x, screen.x + screen.width);
            pet.follow(body.state());
            if (body.state() != Body.State.HOME || window.getX() != (int) Math.round(homeX - Sprite.feetX() * unit)) {
                window.setLocation((int) Math.round(body.x() - Sprite.feetX() * unit),
                        (int) Math.round(body.y() - window.getHeight() + unit));
            }
        }
        earnPoints(mouse, moved);
        if (ticks % 30 == 0) maybeJoke();
        double eyesX = window.getX() + Sprite.eyesX() * unit;
        double eyesY = window.getY() + Sprite.eyesY() * unit;
        pet.tick(FRAME_MS, mouse.x - eyesX, mouse.y - eyesY, moved, devApp);
        Pet.Beep beep = pet.takeBeep();
        if (beep != null && settings.sounds()) beeps.play(beep);
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
        if (kind == null || !settings.tips() || hidden) return;
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

    private void start() {
        pet.setColor(settings.awtColor());
        pet.setPersonality(settings.personality());
        window.setVisible(true);
        useItems();
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
                new Box(settings.unit(), (int) Math.round(homeX), (int) Math.round(groundY), () -> {
                boxed = false;
                window.setVisible(true);
                body.launch((Math.random() < 0.5 ? -1 : 1) * (150 + Math.random() * 250));
                if (settings.sounds()) beeps.play(Pet.Beep.WHEE);
                greetWhenHome = true;
                }).show();
            });
            welcome.start(window.getBounds(), screenBounds());
        } else if (!settings.name().isEmpty()) {
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
        Thread command = new Thread(Install::ensureCommand, "Clawdtop command");
        command.setDaemon(true);
        command.start();
        SwingUtilities.invokeLater(() -> new Clawdtop().start());
    }

    /** For tests: the screen area he'd sit in, without a window. */
    static GraphicsConfiguration screen() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
    }
}
