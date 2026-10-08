package clawdtop;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Draws Clawd, block by block, the way he looks in Claude Code: an orange body with two dark eyes, little arms out to
 * the sides and four legs. Everything is in his own pixels ("units"); the window says how many screen pixels a unit is.
 */
public final class Sprite {
    /** How big the drawing is, in units: room to bounce, raise his arms and let out z's. */
    public static final int WIDTH = 21;
    public static final int HEIGHT = 15;

    static final Color BODY = new Color(215, 119, 87);
    static final Color EYE = new Color(20, 20, 20);
    static final Color LIT = new Color(255, 251, 214);
    static final Color GLOW = new Color(255, 214, 102, 150);
    static final Color ZZZ = new Color(200, 210, 230, 220);

    private static final int GROUND = 14; // the row his feet stand on (the bottom of the drawing)
    private static final int LEFT = 4;    // where his body starts

    private Sprite() {
    }

    /** Draws him as he is right now, with the top-left of the drawing at (0, 0). */
    public static void draw(Graphics2D g, Pet pet, int unit) {
        Pet.Mood mood = pet.mood();
        int drop = switch (mood) {    // how far his body sits down from standing
            case SIT -> 1;
            case LIE, SLEEP -> 2;
            default -> 0;
        };
        double lift = pet.lift();
        double top = GROUND - 2 - 8 + drop - lift; // his body is 8 tall, on legs 2 tall

        // Legs: four little stubs (shorter when he sits, tucked away when he lies down)
        int legs = 2 - drop;
        if (legs > 0) {
            for (int x : new int[] {0, 2, 10, 12}) box(g, unit, LEFT + x, GROUND - legs - lift, 1, legs, BODY);
        }
        // Body
        box(g, unit, LEFT, top, 13, 8, BODY);
        // Arms: out to the sides, or up in the air when he's happy
        if (mood == Pet.Mood.HAPPY) {
            box(g, unit, LEFT - 2, top - 1, 1, 3, BODY);
            box(g, unit, LEFT - 1, top + 1, 1, 1, BODY);
            box(g, unit, LEFT + 14, top - 1, 1, 3, BODY);
            box(g, unit, LEFT + 13, top + 1, 1, 1, BODY);
        } else {
            box(g, unit, LEFT - 2, top + 4, 2, 2, BODY);
            box(g, unit, LEFT + 13, top + 4, 2, 2, BODY);
        }

        // Eyes: they follow the cursor (half a unit each way), shut when he blinks or sleeps, and light up for dev apps
        double ex = pet.lookX() * 0.5;
        double ey = pet.lookY() * 0.5;
        boolean sleepy = mood == Pet.Mood.LIE;
        for (int x : new int[] {3, 11}) {
            double eyeX = LEFT + x + ex;
            double eyeY = top + 2 + ey;
            if (pet.eyesShut()) {
                box(g, unit, LEFT + x, top + 3.5, 1, 0.5, EYE);
            } else if (pet.eyesLit()) {
                box(g, unit, eyeX - 0.5, eyeY - 0.5, 2, 3, GLOW);
                box(g, unit, eyeX, eyeY, 1, 2, LIT);
            } else if (sleepy) {
                box(g, unit, eyeX, eyeY + 1, 1, 1, EYE);
            } else {
                box(g, unit, eyeX, eyeY, 1, 2, EYE);
            }
        }
        if (pet.eyesLit() && !pet.eyesShut()) {
            // a little twinkle above his head
            int phase = (int) (pet.time() / 150 % 4);
            if (phase < 2) {
                box(g, unit, LEFT + 15, top - 2 - phase, 1, 1, LIT);
                box(g, unit, LEFT + 14.5, top - 1.5 - phase, 2, 0.5, GLOW);
            }
        }

        // Asleep: z's float up from beside his head
        if (mood == Pet.Mood.SLEEP) {
            for (int i = 0; i < 3; i++) {
                double t = (pet.time() / 1000.0 + i) % 3 / 3; // 0 to 1, each z a third of the way after the last
                drawZ(g, unit, LEFT + 11 + t * 3, top - 1 - t * 5, t < 0.15 ? 0 : 1 - t);
            }
        }
    }

    /** A tiny z, 3 x 3 units, fading as it rises. */
    private static void drawZ(Graphics2D g, int unit, double x, double y, double fade) {
        if (fade <= 0) return;
        Color c = new Color(ZZZ.getRed(), ZZZ.getGreen(), ZZZ.getBlue(), (int) (ZZZ.getAlpha() * fade));
        double s = 0.6;
        box(g, unit, x, y, 3 * s, s, c);
        box(g, unit, x + 2 * s, y + s, s, s, c);
        box(g, unit, x + s, y + 2 * s, s, s, c);
        box(g, unit, x, y + 3 * s, 3 * s, s, c);
    }

    private static void box(Graphics2D g, int unit, double x, double y, double w, double h, Color c) {
        g.setColor(c);
        int x0 = (int) Math.round(x * unit);
        int y0 = (int) Math.round(y * unit);
        int x1 = (int) Math.round((x + w) * unit);
        int y1 = (int) Math.round((y + h) * unit);
        g.fillRect(x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0));
    }

    /** Where his eyes are in the drawing, in units (for working out where the cursor is from them). */
    public static double eyesX() {
        return LEFT + 6.5;
    }

    public static double eyesY() {
        return GROUND - 2 - 8 + 3;
    }
}
