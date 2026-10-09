package clawdtop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The scraps of a zip he's torn up: flung up and out in front of him, fluttering down onto the desktop, lying there a
 * few seconds, then fading away. Screen pixels; no window here (Clawdtop draws them in its see-through window for big
 * things, like the ducks).
 */
final class Scraps {
    static final int COUNT = 18;
    static final long LIE = 4000, FADE = 800; // ms on the floor, then fading
    static final double GRAVITY = 1500, FLUTTER = 240; // px/s², and the fastest they float down (px/s)
    private static final Color[] COLORS = {Sprite.ZIP_YELLOW, Sprite.ZIP_DARK, new Color(250, 248, 240), Sprite.ZIP_YELLOW, new Color(90, 90, 100)};

    private static final class Scrap {
        double x, y, vx, vy, spin, angle, w, h, sway;
        Color color;
        boolean landed;
    }

    private final List<Scrap> scraps = new ArrayList<>();
    private final Random random = new Random();
    private long age = -1, downFor = -1; // ms since they were flung (-1: none), and since the last one landed

    /** Flings them out from (x, y): one of his pixels is px screen pixels (so the scraps are his size). */
    void start(double x, double y, double px) {
        scraps.clear();
        age = 0;
        downFor = -1;
        for (int i = 0; i < COUNT; i++) {
            Scrap s = new Scrap();
            double side = i % 2 == 0 ? -1 : 1;
            s.x = x + (random.nextDouble() - 0.5) * px * 4;
            s.y = y;
            s.vx = side * (60 + random.nextDouble() * 380);
            s.vy = -(350 + random.nextDouble() * 450);
            s.spin = (random.nextDouble() - 0.5) * 16;
            s.sway = random.nextDouble() * Math.PI * 2;
            s.w = px * (1.2 + random.nextDouble() * 1.6);
            s.h = px * (0.7 + random.nextDouble() * 0.9);
            s.color = COLORS[i % COLORS.length];
            scraps.add(s);
        }
    }

    /** Moves them on ms: up, out, and fluttering down to the floor (between left and right). */
    void tick(long ms, double floor, double left, double right) {
        if (age < 0) return;
        age += ms;
        double dt = ms / 1000.0;
        boolean allDown = true;
        for (Scrap s : scraps) {
            if (s.landed) continue;
            allDown = false;
            s.vy = Math.min(FLUTTER, s.vy + GRAVITY * dt);
            s.vx *= Math.pow(0.3, dt);
            double drift = s.vy > 0 ? Math.sin(age / 170.0 + s.sway) * 70 : 0; // (side to side, like paper does)
            s.x = Math.max(left + s.w, Math.min(right - s.w, s.x + (s.vx + drift) * dt));
            s.y += s.vy * dt;
            s.angle += s.spin * dt * (s.vy > 0 ? 0.5 : 1);
            if (s.vy > 0 && s.y >= floor - s.h / 2) {
                s.y = floor - s.h / 2;
                s.landed = true;
                s.angle = (random.nextDouble() - 0.5) * 0.5; // lying flat(ish)
            }
        }
        if (allDown) {
            downFor = downFor < 0 ? 0 : downFor + ms;
            if (downFor > LIE + FADE) {
                age = -1;
                scraps.clear();
            }
        }
    }

    boolean active() {
        return age >= 0;
    }

    /** Whether they've all come down. */
    boolean landed() {
        return active() && downFor >= 0;
    }

    /** Whether nothing's changing (all down, not fading yet): no need to redraw them. */
    boolean settled() {
        return !active() || (downFor >= 0 && downFor < LIE);
    }

    /** Draws them, with (ox, oy) as the screen's top-left. */
    void paint(Graphics2D g, double ox, double oy) {
        if (age < 0) return;
        double fade = downFor > LIE ? Math.max(0, 1 - (downFor - LIE) / (double) FADE) : 1;
        for (Scrap s : scraps) {
            Graphics2D r = (Graphics2D) g.create();
            double cx = s.x - ox, cy = s.y - oy;
            r.rotate(s.angle, cx, cy);
            r.setColor(new Color(s.color.getRed(), s.color.getGreen(), s.color.getBlue(), (int) (255 * fade)));
            r.fillRect((int) Math.round(cx - s.w / 2), (int) Math.round(cy - s.h / 2), (int) Math.ceil(s.w), (int) Math.ceil(s.h));
            r.dispose();
        }
    }
}
