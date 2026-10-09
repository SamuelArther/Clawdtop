package clawdtop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Rubber ducks, falling from the middle of the screen onto the taskbar (duck.py, the flood): where they all are.
 * Screen pixels; no window here, so it can be tested (Clawdtop draws them in a see-through window of their own).
 */
final class DuckRain {
    static final int MAX = 150;
    static final double GRAVITY = 2200;
    private static final Color YELLOW = new Color(250, 215, 70), BEAK = new Color(245, 140, 40), EYE = new Color(20, 20, 20);

    /** One duck: where it is, how fast it's going, and whether it's down. */
    static final class Duck {
        double x, y, vx, vy;
        boolean landed;
        boolean facingLeft;
    }

    private final List<Duck> ducks = new ArrayList<>();
    private final Random random = new Random();
    private long poofFor = -1; // going: ms since they started vanishing, or -1

    /** A new duck at (x, y), off at a little speed sideways. The first one falls straight. */
    void spawn(double x, double y) {
        if (ducks.size() >= MAX || poofFor >= 0) return;
        Duck d = new Duck();
        d.x = x;
        d.y = y;
        d.vx = ducks.isEmpty() ? 0 : (random.nextDouble() - 0.5) * 900;
        d.vy = ducks.isEmpty() ? 0 : -200 - random.nextDouble() * 400;
        d.facingLeft = random.nextBoolean();
        ducks.add(d);
    }

    /** Moves them on ms: falling, bouncing off the screen's sides, landing on the floor. */
    void tick(long ms, double floor, double left, double right) {
        double dt = ms / 1000.0;
        if (poofFor >= 0) {
            poofFor += ms;
            if (poofFor > 500) {
                ducks.clear();
                poofFor = -1;
            }
            return;
        }
        for (Duck d : ducks) {
            if (d.landed) continue;
            d.vy += GRAVITY * dt;
            d.x += d.vx * dt;
            d.y += d.vy * dt;
            if (d.x < left + 10 || d.x > right - 10) {
                d.vx = -d.vx * 0.6;
                d.x = Math.max(left + 10, Math.min(right - 10, d.x));
            }
            if (d.y >= floor) {
                d.y = floor;
                if (d.vy > 500) { // a little bounce
                    d.vy = -d.vy * 0.25;
                    d.vx *= 0.5;
                } else {
                    d.landed = true;
                }
            }
        }
    }

    /** All of them gone, with a poof. */
    void poof() {
        if (!ducks.isEmpty()) poofFor = 0;
    }

    int count() {
        return ducks.size();
    }

    /** Whether there's anything to show (or still vanishing). */
    boolean active() {
        return !ducks.isEmpty();
    }

    /** Whether nothing's moving: all landed, and not vanishing (no need to redraw them). */
    boolean settled() {
        return allLanded() && poofFor < 0;
    }

    /** Whether they're all down on the floor. */
    boolean allLanded() {
        for (Duck d : ducks) if (!d.landed) return false;
        return true;
    }

    /** Draws them, with (ox, oy) as the screen's top-left, each duck pixel px screen pixels. */
    void paint(Graphics2D g, double ox, double oy, int px) {
        double fade = poofFor < 0 ? 1 : 1 - poofFor / 500.0;
        for (Duck d : ducks) {
            int alpha = (int) (255 * Math.max(0, fade));
            double size = px * (poofFor < 0 ? 1 : 1 + poofFor / 250.0);
            double bx = d.x - ox - 3 * size, by = d.y - oy - 4 * size; // feet at (x, y)
            int f = d.facingLeft ? -1 : 1;
            fill(g, bx, by, size, f, 0, 1.5, 6, 2.5, YELLOW, alpha);   // body
            fill(g, bx, by, size, f, 3, 0, 2.5, 2, YELLOW, alpha);     // head
            fill(g, bx, by, size, f, 5.5, 0.8, 1.2, 0.7, BEAK, alpha); // beak
            fill(g, bx, by, size, f, 4.2, 0.4, 0.6, 0.6, EYE, alpha);
        }
    }

    private static void fill(Graphics2D g, double bx, double by, double size, int facing, double x, double y, double w, double h, Color c, int alpha) {
        if (facing < 0) x = 6.5 - x - w; // mirrored
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
        g.fillRect((int) Math.round(bx + x * size), (int) Math.round(by + y * size), (int) Math.ceil(w * size), (int) Math.ceil(h * size));
    }
}
