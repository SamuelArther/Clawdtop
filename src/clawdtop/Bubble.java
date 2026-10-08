package clawdtop;

import javax.swing.JPanel;
import javax.swing.JWindow;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/** Clawd's speech bubble: a tip that pops up above him for a few seconds. Click it to close it sooner. */
final class Bubble {
    static final Font FONT = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font FIRST_LINE = FONT.deriveFont(Font.BOLD);
    private static final Color PAPER = new Color(255, 250, 242);
    private static final Color INK = new Color(40, 38, 36);
    private static final Color EDGE = new Color(215, 119, 87);
    private static final int PAD = 10;
    private static final int TAIL = 9;

    private final JWindow window = new JWindow();
    private String[] lines = new String[0];
    private long hideAt;

    Bubble() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(java.awt.AlphaComposite.Clear);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setComposite(java.awt.AlphaComposite.SrcOver);
                Bubble.paint(g2, lines, getWidth(), getHeight());
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                hide();
            }
        });
        window.setBackground(new Color(0, 0, 0, 0));
        window.setContentPane(panel);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        window.setType(java.awt.Window.Type.UTILITY);
    }

    /** Shows a tip above Clawd (whose window is at clawd), for long enough to read it. */
    void show(String text, Rectangle clawd, Rectangle screen) {
        lines = text.split("\n");
        Dimension size = size(lines);
        window.setSize(size);
        int x = clawd.x + clawd.width / 2 - size.width + 24; // the tail points down at him, near the bubble's right
        x = Math.max(screen.x + 4, Math.min(screen.x + screen.width - size.width - 4, x));
        window.setLocation(x, clawd.y - size.height + 6);
        window.setVisible(true);
        window.repaint();
        hideAt = System.currentTimeMillis() + 5000 + 1800L * lines.length;
    }

    void tick() {
        if (window.isVisible() && System.currentTimeMillis() > hideAt) hide();
    }

    void hide() {
        window.setVisible(false);
    }

    boolean showing() {
        return window.isVisible();
    }

    static Dimension size(String[] lines) {
        FontMetrics plain = metrics(FONT);
        FontMetrics bold = metrics(FIRST_LINE);
        int width = 0;
        for (int i = 0; i < lines.length; i++) width = Math.max(width, (i == 0 ? bold : plain).stringWidth(lines[i]));
        return new Dimension(width + PAD * 2 + 2, plain.getHeight() * lines.length + PAD * 2 + TAIL + 2);
    }

    /** Draws the bubble filling width x height: a rounded box with a little tail at the bottom right. */
    static void paint(Graphics2D g, String[] lines, int width, int height) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int boxHeight = height - TAIL - 1;
        int tailX = width - 30;
        Polygon tail = new Polygon(new int[] {tailX - 8, tailX + 4, tailX + 8}, new int[] {boxHeight - 2, boxHeight - 2, height - 2}, 3);
        g.setColor(PAPER);
        g.fillRoundRect(1, 1, width - 2, boxHeight - 2, 14, 14);
        g.fillPolygon(tail);
        g.setColor(EDGE);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(1, 1, width - 3, boxHeight - 3, 14, 14);
        g.drawLine(tailX - 8, boxHeight - 2, tailX + 8, height - 2);
        g.drawLine(tailX + 4, boxHeight - 2, tailX + 8, height - 2);
        g.setColor(PAPER); // the tail's opening, over the box's edge
        g.fillRect(tailX - 6, boxHeight - 3, 10, 3);
        g.setColor(INK);
        FontMetrics m = g.getFontMetrics(FONT);
        int y = PAD + m.getAscent();
        for (int i = 0; i < lines.length; i++) {
            g.setFont(i == 0 && lines.length > 1 ? FIRST_LINE : FONT);
            g.drawString(lines[i], PAD + 1, y);
            y += m.getHeight();
        }
    }

    private static FontMetrics metrics(Font font) {
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            return g.getFontMetrics(font);
        } finally {
            g.dispose();
        }
    }
}
