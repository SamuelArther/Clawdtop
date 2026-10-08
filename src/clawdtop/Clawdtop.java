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
    private final JWindow window = new JWindow();
    private final JPanel canvas;
    private Point lastMouse = new Point();
    private String app;
    private boolean devApp;
    private boolean hidden;
    private int ticks;
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
                Sprite.draw(g2, pet, settings.unit());
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
    }

    private void listen() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
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
                if (dragFrom == Integer.MIN_VALUE) return;
                boolean moved = Math.abs(e.getXOnScreen() - dragFrom) > 3;
                dragFrom = Integer.MIN_VALUE;
                if (moved) {
                    settings.setX(window.getX());
                } else {
                    pet.poke();
                }
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
    }

    private JPopupMenu menu() {
        JPopupMenu menu = new JPopupMenu();
        JCheckBoxMenuItem sounds = new JCheckBoxMenuItem("Beeps", settings.sounds());
        sounds.addActionListener(e -> settings.setSounds(sounds.isSelected()));
        menu.add(sounds);
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
            app = Foreground.app();
            devApp = Foreground.isDevApp(app);
            DisplayMode mode = window.getGraphicsConfiguration().getDevice().getDisplayMode();
            boolean fullScreen = !devApp && Foreground.fullScreen(mode.getWidth(), mode.getHeight());
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
        double eyesX = window.getX() + Sprite.eyesX() * unit;
        double eyesY = window.getY() + Sprite.eyesY() * unit;
        pet.tick(FRAME_MS, mouse.x - eyesX, mouse.y - eyesY, moved, devApp);
        Pet.Beep beep = pet.takeBeep();
        if (beep != null && settings.sounds()) beeps.play(beep);
        canvas.repaint();
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
