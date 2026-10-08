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

    private final Settings settings = Settings.load();
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
                if (job != null && job.sunk() > 0) Sprite.drawRising(g2, pet, settings.unit(), job.sunk()); // climbing up to ask
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
        int x = settings.x() >= 0 ? settings.x() : screen.x + screen.width - 64 - w / 2; // the clock is in the corner
        x = Math.max(screen.x, Math.min(screen.x + screen.width - w, x));
        window.setLocation(x, bottom - h + settings.unit()); // his feet just touch the taskbar
        homeX = x + Sprite.feetX() * settings.unit();
        groundY = bottom;
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
        return menu;
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
        JMenuItem back = new JMenuItem("Back above the clock");
        back.addActionListener(e -> {
            settings.setX(-1);
            place();
        });
        menu.add(back);
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
        // Every few seconds, back on top (the taskbar likes to come up over everything when it's clicked)
        if (ticks % 90 == 0 && !hidden) {
            window.setAlwaysOnTop(false);
            window.setAlwaysOnTop(true);
        }

        int unit = settings.unit();
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
        double eyesX = window.getX() + Sprite.eyesX() * unit;
        double eyesY = window.getY() + Sprite.eyesY() * unit;
        pet.tick(FRAME_MS, mouse.x - eyesX, mouse.y - eyesY, moved, devApp);
        Pet.Beep beep = pet.takeBeep();
        if (beep != null && settings.sounds()) beeps.play(beep);
        bubble.tick();
        if (bubble.showing()) bubble.follow(window.getBounds(), screenBounds());
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
        if (!Tips.urgent(front) && now - lastTipAt < 180_000) return;
        String tip = tips.tipFor(front);
        if (tip == null) return;
        lastTipAt = now;
        bubble.show(tip, window.getBounds(), window.getGraphicsConfiguration().getBounds());
        pet.speak();
    }

    private void start() {
        window.setVisible(true);
        new Timer(FRAME_MS, e -> tick()).start();
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
        SwingUtilities.invokeLater(() -> new Clawdtop().start());
    }

    /** For tests: the screen area he'd sit in, without a window. */
    static GraphicsConfiguration screen() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
    }
}
