package clawdtop;

import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The box Clawd arrives in, the first time: it sits where you said he'd sit, wiggling now and then. Click it and the
 * lid bursts open as he shoots out.
 */
final class Box {
    static final int WIDTH = 13;   // in his pixels
    static final int HEIGHT = 11;

    static final Color CARDBOARD = new Color(196, 148, 92);
    static final Color CARDBOARD_DARK = new Color(160, 115, 66);
    static final Color TAPE = new Color(222, 196, 150);
    static final Color HOLE = new Color(70, 48, 30);

    private final JWindow window = new JWindow();
    private final int unit;
    private long time;
    private boolean open;
    private Timer animation;

    /** A box at (x, groundY) in screen coordinates (bottom centre); opened runs when it's clicked. */
    Box(int unit, int centerX, int groundY, Runnable opened) {
        this.unit = unit;
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(java.awt.AlphaComposite.Clear);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setComposite(java.awt.AlphaComposite.SrcOver);
                draw(g2, unit, time, open);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (open) return;
                open = true;
                window.repaint();
                opened.run();
                Timer gone = new Timer(1400, done -> {
                    animation.stop();
                    window.dispose();
                });
                gone.setRepeats(false);
                gone.start();
            }
        });
        Platform.seeThrough(window);
        window.setContentPane(panel);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        window.setType(java.awt.Window.Type.UTILITY);
        window.setSize((WIDTH + 8) * unit, (HEIGHT + 6) * unit);
        window.setLocation(centerX - window.getWidth() / 2, groundY - window.getHeight()); // (its bottom level with his feet)
        animation = new Timer(33, e -> {
            time += 33;
            window.repaint();
        });
        animation.start();
    }

    void show() {
        window.setVisible(true);
    }

    /** Draws the box (4 units in from the left, standing on the bottom); wiggles every couple of seconds while shut. */
    static void draw(Graphics2D g, int unit, long time, boolean open) {
        double wiggle = 0;
        long phase = time % 2400;
        if (!open && phase < 400) wiggle = Math.sin(phase / 400.0 * Math.PI * 4) * 0.6; // something's moving in there
        double left = 4 + wiggle;
        double bottom = HEIGHT + 6;
        double top = bottom - 8;
        box(g, unit, left, top, WIDTH, 8, CARDBOARD);
        box(g, unit, left, top, WIDTH, 1, CARDBOARD_DARK);
        box(g, unit, left + WIDTH / 2.0 - 1, top + 1, 2, 7, TAPE);
        if (open) {
            // the lid flaps burst open
            box(g, unit, left - 3, top - 2, 4, 1, CARDBOARD_DARK);
            box(g, unit, left - 2, top - 1, 3, 1, CARDBOARD_DARK);
            box(g, unit, left + WIDTH - 1, top - 2, 4, 1, CARDBOARD_DARK);
            box(g, unit, left + WIDTH - 1, top - 1, 3, 1, CARDBOARD_DARK);
            box(g, unit, left + 1, top - 0.5, WIDTH - 2, 1, HOLE);
        } else {
            box(g, unit, left - 0.5, top - 1, WIDTH + 1, 1.5, CARDBOARD_DARK); // the lid
            box(g, unit, left + WIDTH / 2.0 - 1, top - 1, 2, 1.5, TAPE);
        }
    }

    private static void box(Graphics2D g, int unit, double x, double y, double w, double h, Color c) {
        g.setColor(c);
        g.fillRect((int) Math.round(x * unit), (int) Math.round(y * unit), Math.max(1, (int) Math.round(w * unit)), Math.max(1, (int) Math.round(h * unit)));
    }
}
