package clawdtop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * His rocket blowing up: a fireball, smoke, and bits of rocket flying everywhere. Screen pixels; no window here
 * (Clawdtop draws it in the see-through window it uses for big things, like the ducks).
 */
final class Explosion {
    static final long TIME = 1800;
    private static final Color[] FIRE = {new Color(255, 245, 180), new Color(255, 200, 80), new Color(255, 130, 40), new Color(220, 60, 40)};
    private static final Color[] BITS = {new Color(236, 238, 244), new Color(220, 60, 60), new Color(110, 116, 128), new Color(120, 200, 255)};

    private record Bit(double vx, double vy, double spin, Color color, double size) {
    }

    private double x, y;
    private long age = -1;
    private final List<Bit> bits = new ArrayList<>();
    private final Random random = new Random();

    /** BOOM, at (x, y). */
    void start(double x, double y) {
        this.x = x;
        this.y = y;
        age = 0;
        bits.clear();
        for (int i = 0; i < 18; i++) {
            double a = random.nextDouble() * Math.PI * 2, speed = 250 + random.nextDouble() * 650;
            bits.add(new Bit(Math.cos(a) * speed, Math.sin(a) * speed - 300, random.nextDouble() * 12 - 6,
                    BITS[i % BITS.length], 4 + random.nextDouble() * 8));
        }
    }

    void tick(long ms) {
        if (age >= 0) age += ms;
        if (age > TIME) age = -1;
    }

    boolean active() {
        return age >= 0;
    }

    /** Draws it, with (ox, oy) as the screen's top-left; scale is how big (1 for a normal-size Clawd). */
    void paint(Graphics2D g, double ox, double oy, double scale) {
        if (age < 0) return;
        double t = age / 1000.0, f = age / (double) TIME;
        double cx = x - ox, cy = y - oy;
        // smoke, rising and spreading
        for (int i = 0; i < 10; i++) {
            double a = i * 0.63, r = (20 + t * 90) * scale;
            int alpha = (int) (150 * Math.max(0, 1 - f));
            double size = (24 + t * 50) * scale * (0.7 + (i % 3) * 0.2);
            g.setColor(new Color(110, 110, 118, alpha));
            g.fillOval((int) (cx + Math.cos(a) * r - size / 2), (int) (cy + Math.sin(a) * r * 0.7 - t * 60 * scale - size / 2), (int) size, (int) size);
        }
        // the fireball: big and bright, then shrinking away
        if (f < 0.55) {
            double k = f / 0.55;
            for (int ring = 0; ring <= 3; ring++) { // biggest (red) first, hottest (yellow) in the middle
                double r = (30 + 70 * Math.sqrt(k)) * scale * (1 - ring * 0.2) * (1 - k * 0.5);
                Color c = FIRE[3 - ring];
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (255 * (1 - k * 0.7))));
                g.fillOval((int) (cx - r), (int) (cy - r), (int) (r * 2), (int) (r * 2));
            }
        }
        // bits of rocket, flung out and falling
        for (Bit b : bits) {
            double bx = cx + b.vx() * t * scale, by = cy + (b.vy() * t + 0.5 * 1400 * t * t) * scale;
            double s = b.size() * scale;
            Graphics2D r = (Graphics2D) g.create();
            r.rotate(b.spin() * t, bx, by);
            r.setColor(new Color(b.color().getRed(), b.color().getGreen(), b.color().getBlue(), (int) (255 * Math.max(0, 1 - f * f))));
            r.fillRect((int) (bx - s / 2), (int) (by - s / 2), (int) Math.ceil(s), (int) Math.ceil(s * 0.6));
            r.dispose();
        }
    }
}
