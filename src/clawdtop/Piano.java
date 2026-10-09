package clawdtop;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Random;
import java.util.function.IntConsumer;

/**
 * Clawd's mini piano: the songs he knows, and a little piano of your own to play (click the keys, or type A to K for
 * the white keys and W E T Y U for the black ones).
 */
final class Piano {
    /** A song: its name, notes (MIDI numbers, 60 is middle C), and how many beats each one lasts. */
    record Song(String name, int[] notes, double[] beats, int beatMs) {
        /** How long it lasts, ms. */
        long length() {
            double total = 0;
            for (double b : beats) total += b;
            return (long) (total * beatMs);
        }
    }

    static final Song[] SONGS = {
            new Song("Twinkle Twinkle Little Star",
                    new int[] {60, 60, 67, 67, 69, 69, 67, 65, 65, 64, 64, 62, 62, 60},
                    new double[] {1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 2}, 330),
            new Song("Ode to Joy",
                    new int[] {64, 64, 65, 67, 67, 65, 64, 62, 60, 60, 62, 64, 64, 62, 62},
                    new double[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1.5, 0.5, 2}, 320),
            new Song("Mary Had a Little Lamb",
                    new int[] {64, 62, 60, 62, 64, 64, 64, 62, 62, 62, 64, 67, 67},
                    new double[] {1, 1, 1, 1, 1, 1, 2, 1, 1, 2, 1, 1, 2}, 320),
            new Song("Fur Elise (the famous bit)",
                    new int[] {76, 75, 76, 75, 76, 71, 74, 72, 69, 60, 64, 69, 71},
                    new double[] {0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1.5, 0.5, 0.5, 0.5, 1.5}, 340),
            new Song("Hot Cross Buns",
                    new int[] {64, 62, 60, 64, 62, 60, 60, 60, 60, 60, 62, 62, 62, 62, 64, 62, 60},
                    new double[] {1, 1, 2, 1, 1, 2, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1, 1, 2}, 300)};

    /** What he can play. */
    enum Instrument {
        PIANO("piano"), GUITAR("guitar"), BASS("bass"), DRUMS("drums");

        final String shown;

        Instrument(String shown) {
            this.shown = shown;
        }
    }

    /** Drum notes (the usual MIDI drum numbers): bass drum, snare, closed hi-hat, crash cymbal, tom. */
    static final int KICK = 36, SNARE = 38, HAT = 42, CRASH = 49, TOM = 45;

    /** Beats for his drum set. */
    static final Song[] BEATS = {
            new Song("a rock beat",
                    new int[] {KICK, HAT, SNARE, HAT, KICK, KICK, SNARE, HAT, KICK, HAT, SNARE, HAT, KICK, KICK, SNARE, CRASH},
                    new double[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2}, 220),
            new Song("a drum solo",
                    new int[] {SNARE, SNARE, TOM, TOM, KICK, SNARE, TOM, KICK, SNARE, SNARE, SNARE, TOM, TOM, KICK, KICK, CRASH},
                    new double[] {0.5, 0.5, 0.5, 0.5, 1, 0.5, 0.5, 1, 0.25, 0.25, 0.5, 0.5, 0.5, 0.5, 0.5, 2}, 240),
            new Song("ba-dum-tss",
                    new int[] {SNARE, TOM, CRASH},
                    new double[] {1, 1, 2}, 220),
            new Song("a march",
                    new int[] {KICK, SNARE, SNARE, SNARE, KICK, SNARE, SNARE, SNARE, KICK, SNARE, KICK, SNARE, SNARE, SNARE, SNARE, CRASH},
                    new double[] {1, 0.5, 0.5, 1, 1, 0.5, 0.5, 1, 1, 1, 1, 0.5, 0.5, 0.5, 0.5, 2}, 260)};

    /** A song he makes up on the spot (from the notes that always sound nice together). */
    static Song madeUp(Random random) {
        int[] scale = {60, 62, 64, 67, 69, 72, 74, 76};
        int count = 10 + random.nextInt(5);
        int[] notes = new int[count];
        double[] beats = new double[count];
        int at = random.nextInt(4);
        for (int i = 0; i < count; i++) {
            at = Math.max(0, Math.min(scale.length - 1, at + random.nextInt(5) - 2));
            notes[i] = scale[at];
            beats[i] = random.nextInt(4) == 0 ? 2 : random.nextBoolean() ? 1 : 0.5;
        }
        notes[count - 1] = 60; // it ends on C, so it sounds finished
        beats[count - 1] = 2;
        return new Song("a song I just made up", notes, beats, 300);
    }

    /**
     * A MIDI file as a song for his piano: the tune (the highest note whenever notes start together), up to 400 notes,
     * with the real timing. Null if it isn't a MIDI file or has no notes.
     */
    static Song fromMidi(java.io.File file) {
        try {
            javax.sound.midi.Sequence seq = javax.sound.midi.MidiSystem.getSequence(file);
            java.util.TreeMap<Long, Integer> top = new java.util.TreeMap<>(); // start tick -> highest note
            for (javax.sound.midi.Track track : seq.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    javax.sound.midi.MidiEvent e = track.get(i);
                    if (e.getMessage() instanceof javax.sound.midi.ShortMessage m && m.getCommand() == javax.sound.midi.ShortMessage.NOTE_ON
                            && m.getData2() > 0 && m.getChannel() != 9) { // (channel 10 is drums)
                        top.merge(e.getTick(), m.getData1(), Math::max);
                    }
                }
            }
            if (top.isEmpty() || seq.getTickLength() == 0) return null;
            double msPerTick = seq.getMicrosecondLength() / 1000.0 / seq.getTickLength();
            java.util.List<Long> ticks = new java.util.ArrayList<>(top.keySet());
            int count = Math.min(400, ticks.size());
            int[] notes = new int[count];
            double[] ms = new double[count];
            for (int i = 0; i < count; i++) {
                int n = top.get(ticks.get(i));
                while (n > 84) n -= 12; // keep it on his little piano
                while (n < 48) n += 12;
                notes[i] = n;
                long next = i + 1 < ticks.size() ? ticks.get(i + 1) : ticks.get(i) + seq.getResolution();
                ms[i] = Math.max(60, Math.min(2000, (next - ticks.get(i)) * msPerTick));
            }
            String name = file.getName().replaceAll("(?i)\\.midi?$", "").replace('_', ' ');
            return new Song("your " + (name.length() > 30 ? name.substring(0, 30) : name), notes, ms, 1);
        } catch (Exception notMidi) {
            return null;
        }
    }

    /** Whether a file looks like MIDI (by its name). */
    static boolean isMidi(java.io.File f) {
        String n = f.getName().toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".mid") || n.endsWith(".midi");
    }

    /** Where a note's key is on his little piano, 0 (left) to 1 (right). */
    static double place(int midi) {
        return Math.max(0, Math.min(1, (midi - 58) / 20.0));
    }

    // ---- Your own little piano ----

    private static final int[] WHITE = {60, 62, 64, 65, 67, 69, 71, 72};      // C D E F G A B C
    private static final int[] BLACK = {61, 63, -1, 66, 68, 70, -1};          // between them (none after E and B)
    private static final String WHITE_KEYS = "ASDFGHJK", BLACK_KEYS = "WE TYU";
    private static final int KEY_W = 30, KEY_H = 96, TOP = 28;

    private final TypingWindow window = java.awt.GraphicsEnvironment.isHeadless() ? null : new TypingWindow();
    private int pressed = -1;
    private long pressedAt;

    /** Shows your piano above Clawd. played gets each note you play. */
    void show(Rectangle clawd, Rectangle screen, IntConsumer played) {
        if (window == null) return;
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(java.awt.AlphaComposite.Clear);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setComposite(java.awt.AlphaComposite.SrcOver);
                Piano.paint(g2, getWidth(), getHeight(), System.currentTimeMillis() - pressedAt < 160 ? pressed : -1);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setFocusable(true);
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getY() < TOP && e.getX() > panel.getWidth() - 26) {
                    window.setVisible(false); // the little x
                    return;
                }
                int note = keyAt(e.getX(), e.getY());
                if (note > 0) press(note, played, panel);
            }
        });
        panel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                char c = Character.toUpperCase(e.getKeyChar());
                int w = WHITE_KEYS.indexOf(c), b = BLACK_KEYS.indexOf(c);
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) window.setVisible(false);
                else if (w >= 0) press(WHITE[w], played, panel);
                else if (b >= 0 && c != ' ' && BLACK[b] > 0) press(BLACK[b], played, panel);
            }
        });
        Platform.seeThrough(window);
        window.setContentPane(panel);
        window.setAlwaysOnTop(true);
        window.setType(java.awt.Window.Type.UTILITY);
        Dimension size = size();
        window.setSize(size);
        int x = Math.max(screen.x + 4, Math.min(screen.x + screen.width - size.width - 4, clawd.x + clawd.width / 2 - size.width + 40));
        window.setLocation(x, Math.max(screen.y + 4, clawd.y - size.height - 4));
        window.showAndFocus(panel);
        new javax.swing.Timer(60, e -> panel.repaint()).start();
    }

    /** Where the first white key is on the screen (for the screen test), or null. */
    java.awt.Point firstKeyOnScreen() {
        if (window == null || !window.isShowing()) return null;
        java.awt.Point p = window.getLocationOnScreen();
        return new java.awt.Point(p.x + 10 + KEY_W / 2, p.y + TOP + KEY_H - 15);
    }

    boolean showing() {
        return window != null && window.isVisible();
    }

    private void press(int note, IntConsumer played, JPanel panel) {
        pressed = note;
        pressedAt = System.currentTimeMillis();
        played.accept(note);
        panel.repaint();
    }

    static Dimension size() {
        return new Dimension(WHITE.length * KEY_W + 20, KEY_H + TOP + 12);
    }

    /** Which note is at (x, y) on the piano, or -1. Black keys sit on top, so they're checked first. */
    static int keyAt(int x, int y) {
        int kx = x - 10, ky = y - TOP;
        if (ky < 0 || ky > KEY_H || kx < 0) return -1;
        if (ky < KEY_H * 0.6) {
            for (int i = 0; i < BLACK.length; i++) {
                int bx = (i + 1) * KEY_W - KEY_W / 3;
                if (BLACK[i] > 0 && kx >= bx && kx < bx + KEY_W * 2 / 3) return BLACK[i];
            }
        }
        int w = kx / KEY_W;
        return w < WHITE.length ? WHITE[w] : -1;
    }

    /** Draws your piano: a little wooden case, white and black keys (the pressed one darker), and its keys' letters. */
    static void paint(Graphics2D g, int width, int height, int pressed) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(200, 90, 70));
        g.fillRoundRect(1, 1, width - 2, height - 2, 14, 14);
        g.setColor(new Color(150, 60, 48));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(1, 1, width - 3, height - 3, 14, 14);
        g.setColor(Color.WHITE);
        g.setFont(Bubble.FIRST_LINE);
        g.drawString("Clawd's piano", 12, 19);
        g.drawString("x", width - 18, 19);
        g.setFont(Bubble.FONT.deriveFont(10f));
        for (int i = 0; i < WHITE.length; i++) {
            int x = 10 + i * KEY_W;
            g.setColor(WHITE[i] == pressed ? new Color(225, 225, 235) : Color.WHITE);
            g.fillRect(x, TOP, KEY_W - 2, KEY_H);
            g.setColor(new Color(170, 170, 180));
            g.drawString(String.valueOf(WHITE_KEYS.charAt(i)), x + KEY_W / 2 - 4, TOP + KEY_H - 6);
        }
        for (int i = 0; i < BLACK.length; i++) {
            if (BLACK[i] < 0) continue;
            int x = 10 + (i + 1) * KEY_W - KEY_W / 3;
            g.setColor(BLACK[i] == pressed ? new Color(90, 90, 100) : new Color(30, 30, 34));
            g.fillRect(x, TOP, KEY_W * 2 / 3, (int) (KEY_H * 0.6));
        }
    }
}
