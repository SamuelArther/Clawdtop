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
import java.util.function.IntConsumer;

/**
 * Clawd's speech bubble: a tip or a question that pops up above him. A tip goes away by itself after a few seconds (or
 * when clicked); a question has buttons and waits for your answer.
 */
final class Bubble {
    static Font FONT = new Font("Comic Sans MS", Font.PLAIN, 13); // falls back to the normal font where it isn't installed
    static Font FIRST_LINE = FONT.deriveFont(Font.BOLD);
    static double stay = 1; // how long tips stay up: 0.6 short, 1 normal, 1.8 long

    /** The bubble's font: "Comic Sans", "Normal" or "Typewriter". */
    static void setFont(String which) {
        FONT = switch (which) {
            case "Normal" -> new Font("Segoe UI", Font.PLAIN, 13);
            case "Typewriter" -> new Font("Consolas", Font.PLAIN, 13);
            default -> new Font("Comic Sans MS", Font.PLAIN, 13);
        };
        FIRST_LINE = FONT.deriveFont(Font.BOLD);
    }
    private static final Color PAPER = new Color(255, 250, 242);
    private static final Color INK = new Color(40, 38, 36);
    private static final Color EDGE = new Color(215, 119, 87);
    private static final Color BUTTON = new Color(215, 119, 87);
    private static final Color BUTTON_TEXT = Color.WHITE;
    private static final int PAD = 10;
    private static final int TAIL = 9;
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_GAP = 8;

    private static final char BOLD = '\u0001'; // (marks a line to draw in bold: a title over the rest)
    private static final int MAX_WIDTH = 360;   // longer lines wrap

    private final JWindow window = new JWindow();
    private int tailX = -1; // where the tail is (pointing at him), from the bubble's left; -1: the usual spot
    private String[] lines = new String[0];
    private String[] buttons = new String[0];
    private IntConsumer answer;
    private long hideAt;

    Bubble() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(java.awt.AlphaComposite.Clear);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setComposite(java.awt.AlphaComposite.SrcOver);
                Bubble.paint(g2, lines, buttons, getWidth(), getHeight(), tailX);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (buttons.length == 0) {
                    hide();
                    return;
                }
                int chosen = buttonAt(lines, buttons, e.getX(), e.getY());
                if (chosen < 0) return;
                IntConsumer then = answer;
                hide();
                if (then != null) then.accept(chosen);
            }
        });
        Platform.seeThrough(window);
        window.setContentPane(panel);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        window.setType(java.awt.Window.Type.UTILITY);
    }

    /** Shows a tip above Clawd (whose window is at clawd), for long enough to read it. */
    void show(String text, Rectangle clawd, Rectangle screen) {
        ask(text, new String[0], null, clawd, screen);
        hideAt = System.currentTimeMillis() + (long) ((5000 + 1800L * lines.length) * stay);
    }

    /** Asks something, with buttons; answer gets the number of the button clicked. Stays up until answered. */
    void ask(String text, String[] choices, IntConsumer answer, Rectangle clawd, Rectangle screen) {
        lines = layout(text);
        buttons = choices;
        this.answer = answer;
        hideAt = Long.MAX_VALUE;
        window.setSize(size(lines, buttons));
        follow(clawd, screen);
        window.setVisible(true);
        window.repaint();
    }

    /** A question that's only news (a finished download, say) goes away by itself if you don't answer it. */
    void expireIn(long ms) {
        if (asking()) hideAt = System.currentTimeMillis() + ms;
    }

    /** Keeps the bubble pointing at him as he moves. */
    void follow(Rectangle clawd, Rectangle screen) {
        Dimension size = window.getSize();
        int x = clawd.x + clawd.width / 2 - size.width + 24; // the tail points down at him, near the bubble's right
        x = Math.max(screen.x + 4, Math.min(screen.x + screen.width - size.width - 4, x));
        int tail = Math.max(18, Math.min(size.width - 18, clawd.x + clawd.width / 2 - x)); // (still pointing at him at the screen's edges)
        if (tail != tailX) {
            tailX = tail;
            window.repaint();
        }
        window.setLocation(x, Math.max(screen.y + 4, clawd.y - size.height + 6));
    }

    void tick() {
        if (window.isVisible() && System.currentTimeMillis() > hideAt) hide();
    }

    /** Presses one of the question's buttons (for the screen test, as if you'd clicked it). */
    void press(int i) {
        if (!asking() || i < 0 || i >= buttons.length) return;
        IntConsumer then = answer;
        hide();
        if (then != null) then.accept(i);
    }

    /** The question being asked right now, or null. */
    String question() {
        return asking() ? String.join("\n", java.util.Arrays.stream(lines).map(Bubble::text).toList()) : null;
    }

    void hide() {
        window.setVisible(false);
        answer = null;
    }

    boolean showing() {
        return window.isVisible();
    }

    /** Where the bubble is on the screen (null when it's not up). */
    java.awt.Rectangle bounds() {
        return window.isVisible() ? window.getBounds() : null;
    }

    /** Whether a question is waiting for an answer. */
    boolean asking() {
        return window.isVisible() && buttons.length > 0;
    }

    static Dimension size(String[] lines) {
        return size(lines, new String[0]);
    }

    /**
     * The text as lines for the bubble: long lines wrapped to fit, and the first line marked bold when it's a title over
     * more lines (and fits on one line).
     */
    static String[] layout(String text) {
        String[] raw = text.split("\n");
        FontMetrics plain = metrics(FONT), bold = metrics(FIRST_LINE);
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < raw.length; i++) {
            boolean title = i == 0 && raw.length > 1 && bold.stringWidth(raw[0]) <= MAX_WIDTH;
            if (title) {
                out.add(BOLD + raw[0]);
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : raw[i].split(" ", -1)) {
                String tryLine = line.length() == 0 ? word : line + " " + word;
                if (line.length() > 0 && plain.stringWidth(tryLine) > MAX_WIDTH) {
                    out.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                } else {
                    line.setLength(0);
                    line.append(tryLine);
                }
            }
            out.add(line.toString());
        }
        return out.toArray(new String[0]);
    }

    private static boolean bold(String line) {
        return !line.isEmpty() && line.charAt(0) == BOLD;
    }

    private static String text(String line) {
        return bold(line) ? line.substring(1) : line;
    }

    static Dimension size(String[] lines, String[] buttons) {
        FontMetrics plain = metrics(FONT);
        FontMetrics bold = metrics(FIRST_LINE);
        int width = 60; // (never so small the tail hangs off the corner)
        for (String line : lines) width = Math.max(width, (bold(line) ? bold : plain).stringWidth(text(line)));
        width = Math.max(width, buttonsWidth(buttons));
        int height = plain.getHeight() * lines.length + PAD * 2 + TAIL + 2;
        if (buttons.length > 0) height += BUTTON_HEIGHT + PAD;
        return new Dimension(width + PAD * 2 + 2, height);
    }

    private static int buttonWidth(String label) {
        return metrics(FIRST_LINE).stringWidth(label) + 20;
    }

    private static int buttonsWidth(String[] buttons) {
        int w = 0;
        for (String b : buttons) w += buttonWidth(b) + BUTTON_GAP;
        return Math.max(0, w - BUTTON_GAP);
    }

    /** Where button i is: {x, y, width, height}. Buttons sit in a row under the text, at the right. */
    static int[] buttonBox(String[] lines, String[] buttons, int i) {
        Dimension size = size(lines, buttons);
        int y = PAD + metrics(FONT).getHeight() * lines.length + PAD / 2;
        int x = size.width - PAD - 1 - buttonsWidth(buttons);
        for (int k = 0; k < i; k++) x += buttonWidth(buttons[k]) + BUTTON_GAP;
        return new int[] {x, y, buttonWidth(buttons[i]), BUTTON_HEIGHT};
    }

    /** Which button is at (x, y), or -1. */
    static int buttonAt(String[] lines, String[] buttons, int x, int y) {
        for (int i = 0; i < buttons.length; i++) {
            int[] b = buttonBox(lines, buttons, i);
            if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]) return i;
        }
        return -1;
    }

    static void paint(Graphics2D g, String[] lines, int width, int height) {
        paint(g, lines, new String[0], width, height, -1);
    }

    static void paint(Graphics2D g, String[] lines, String[] buttons, int width, int height) {
        paint(g, lines, buttons, width, height, -1);
    }

    /** Draws the bubble filling width x height: a rounded box with a little tail at the bottom (at tail, or the right). */
    static void paint(Graphics2D g, String[] lines, String[] buttons, int width, int height, int tailAt) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int boxHeight = height - TAIL - 1;
        int tailX = tailAt > 0 ? Math.max(18, Math.min(width - 18, tailAt)) : width - 30;
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
            g.setFont(bold(lines[i]) ? FIRST_LINE : FONT);
            g.drawString(text(lines[i]), PAD + 1, y);
            y += m.getHeight();
        }
        g.setFont(FIRST_LINE);
        FontMetrics bm = g.getFontMetrics(FIRST_LINE);
        for (int i = 0; i < buttons.length; i++) {
            int[] b = buttonBox(lines, buttons, i);
            g.setColor(BUTTON);
            g.fillRoundRect(b[0], b[1], b[2], b[3], 10, 10);
            g.setColor(BUTTON_TEXT);
            g.drawString(buttons[i], b[0] + 10, b[1] + (b[3] + bm.getAscent() - bm.getDescent()) / 2);
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
