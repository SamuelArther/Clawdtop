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
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A sticky note he keeps up beside him ("stick a note: dentist at 4"): a little yellow note pinned next to him, there
 * until you click it and say you're done. It follows him around.
 */
final class Sticky {
    private static final Color NOTE = new Color(255, 236, 130), FOLD = new Color(240, 214, 96), INK = new Color(60, 50, 30),
            PIN = new Color(215, 70, 60), SHADOW = new Color(0, 0, 0, 40);
    static final int WIDTH = 132, HEIGHT = 112;

    private final JWindow window = new JWindow();
    private List<String> lines = List.of();
    private Runnable clicked = () -> { };

    Sticky() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth() - 6, h = getHeight() - 6;
                g.rotate(Math.toRadians(-2.5), w / 2.0, h / 2.0); // (a bit wonky, like it was stuck up in a hurry)
                g.setColor(SHADOW);
                g.fillRect(5, 7, w - 4, h - 4);
                g.setColor(NOTE);
                g.fillPolygon(new int[] {2, w - 2, w - 2, w - 18, 2}, new int[] {4, 4, h - 18, h - 2, h - 2}, 5);
                g.setColor(FOLD);
                g.fillPolygon(new int[] {w - 2, w - 18, w - 18}, new int[] {h - 18, h - 18, h - 2}, 3);
                g.setColor(PIN);
                g.fillOval(w / 2 - 5, 0, 10, 10);
                g.setColor(INK);
                g.setFont(Bubble.FONT.deriveFont(Font.PLAIN, 13f));
                FontMetrics fm = g.getFontMetrics();
                int y = 18 + fm.getAscent();
                for (String line : lines) {
                    g.drawString(line, 10, y);
                    y += fm.getHeight();
                }
                g.setStroke(new BasicStroke(1));
                g.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                clicked.run();
            }
        });
        window.setContentPane(panel);
        window.setBackground(new Color(0, 0, 0, 0));
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        window.setType(java.awt.Window.Type.UTILITY);
        window.pack();
    }

    /** Puts the note up with this on it (word-wrapped to fit; very long notes are cut short). */
    void show(String text, Runnable onClick) {
        clicked = onClick;
        lines = wrap(text, window.getFontMetrics(Bubble.FONT.deriveFont(Font.PLAIN, 13f)), WIDTH - 26, 5);
        window.repaint();
        window.setVisible(true);
    }

    void hide() {
        window.setVisible(false);
    }

    boolean showing() {
        return window.isVisible();
    }

    /**
     * Keeps it beside him (to his left, or his right if there's no room), and out from under his speech bubble
     * (bubble: where it is, or null): it slides along, a little at a time, so it never jumps.
     */
    void place(int clawdX, int clawdY, int clawdW, int clawdH, java.awt.Rectangle screen, java.awt.Rectangle bubble) {
        if (!window.isVisible()) return;
        int x = spotX(clawdX, clawdW, screen, bubble), y = clawdY + clawdH - HEIGHT - 14;
        y = Math.max(screen.y, Math.min(y, screen.y + screen.height - HEIGHT));
        int nowX = window.getX(), step = Math.abs(x - nowX) > 400 ? Math.abs(x - nowX) : 40; // (glides; a big move, like a new screen: straight there)
        int nextX = nowX + Math.max(-step, Math.min(step, x - nowX));
        if (nowX != nextX || window.getY() != y) window.setLocation(nextX, y);
    }

    /** Where the note goes across: beside him, and if his bubble would cover it, beside the bubble instead. */
    static int spotX(int clawdX, int clawdW, java.awt.Rectangle screen, java.awt.Rectangle bubble) {
        int x = clawdX - WIDTH + 8;
        if (x < screen.x) x = clawdX + clawdW - 8;
        if (bubble != null && bubble.x < x + WIDTH && bubble.x + bubble.width > x) { // under the bubble: off to its side
            int left = bubble.x - WIDTH - 4, right = bubble.x + bubble.width + 4;
            x = left >= screen.x ? left : right + WIDTH <= screen.x + screen.width ? right : x;
        }
        return x;
    }

    /** The note's words in lines that fit, at most this many (the last one ends "..." if it didn't all fit). */
    static List<String> wrap(String text, FontMetrics fm, int width, int most) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.strip().split("\\s+")) {
            while (fm.stringWidth(word) > width && word.length() > 1) { // (one huge word: broken up)
                int cut = word.length() - 1;
                while (cut > 1 && fm.stringWidth(word.substring(0, cut)) > width) cut--;
                if (!line.isEmpty()) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                out.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            String tried = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(tried) <= width) line = new StringBuilder(tried);
            else {
                out.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        if (out.size() > most) {
            List<String> cut = new ArrayList<>(out.subList(0, most));
            String last = cut.get(most - 1);
            while (!last.isEmpty() && fm.stringWidth(last + "...") > width) last = last.substring(0, last.length() - 1);
            cut.set(most - 1, last + "...");
            return cut;
        }
        return out;
    }
}
