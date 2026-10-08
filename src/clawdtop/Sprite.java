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

    static final Color ORANGE = new Color(215, 119, 87); // his own color
    private static Color body = ORANGE;                  // the color he is right now (drawing is on one thread)
    private static Color hand = ORANGE.darker();
    static final Color EYE = new Color(20, 20, 20);
    static final Color LIT = new Color(255, 251, 214);
    static final Color GLOW = new Color(255, 214, 102, 150);
    static final Color ZZZ = new Color(200, 210, 230, 220);
    static final Color LAPTOP = new Color(150, 158, 170);
    static final Color LAPTOP_DARK = new Color(96, 104, 116);
    static final Color SCREEN_GLOW = new Color(150, 210, 255, 120);

    private static final int GROUND = 14; // the row his feet stand on (the bottom of the drawing)
    private static final int LEFT = 4;    // where his body starts

    private Sprite() {
    }

    /** Draws him as he is right now, with the top-left of the drawing at (0, 0). */
    public static void draw(Graphics2D g, Pet pet, int unit) {
        Pet.Mood mood = pet.mood();
        body = pet.color();
        hand = new Color(body.getRed() * 83 / 100, body.getGreen() * 77 / 100, body.getBlue() * 74 / 100); // a shade darker, so his hands show in front of him
        if (mood == Pet.Mood.SPIN) {
            // spun round by a zooming cursor: once all the way round
            Graphics2D spun = (Graphics2D) g.create();
            spun.rotate(Math.min(1, pet.moodTime() / 600.0) * Math.PI * 2, WIDTH * unit / 2.0, (GROUND - 5) * unit);
            drawBody(spun, pet, unit, mood);
            spun.dispose();
            return;
        }
        if (mood == Pet.Mood.SNEEZE && pet.moodTime() >= 700 && pet.moodTime() < 900) {
            // the ACHOO jolt
            Graphics2D jolt = (Graphics2D) g.create();
            jolt.translate(0, unit);
            drawBody(jolt, pet, unit, mood);
            jolt.dispose();
            return;
        }
        if (mood == Pet.Mood.DANCE) {
            // dancing: swaying side to side
            Graphics2D sway = (Graphics2D) g.create();
            sway.translate(Math.sin(pet.time() / 180.0) * unit, 0);
            drawBody(sway, pet, unit, mood);
            sway.dispose();
            return;
        }
        if (mood == Pet.Mood.FREAKOUT) {
            // panicking: shaking all over
            Graphics2D panic = (Graphics2D) g.create();
            long t = pet.time() / 40;
            panic.translate((t % 3 - 1) * 0.5 * unit, 0);
            drawBody(panic, pet, unit, mood);
            panic.dispose();
            return;
        }
        if (mood == Pet.Mood.SHAKE) {
            // shaking it off: his whole body wobbles side to side, fast
            Graphics2D wobble = (Graphics2D) g.create();
            wobble.translate((pet.time() / 50) % 2 == 0 ? -0.7 * unit : 0.7 * unit, 0);
            drawBody(wobble, pet, unit, mood);
            wobble.dispose();
            return;
        }
        drawBody(g, pet, unit, mood);
    }

    private static void drawBody(Graphics2D g, Pet pet, int unit, Pet.Mood mood) {
        if (mood == Pet.Mood.PEEK) {
            // Hiding behind the window's top edge: just his two little hands gripping it
            box(g, unit, LEFT + 2, GROUND - 1, 1, 1, body);
            box(g, unit, LEFT + 10, GROUND - 1, 1, 1, body);
            return;
        }
        int drop = switch (mood) {    // how far his body sits down from standing
            case SIT, WORK, SAD -> 1;
            case LIE, SLEEP -> 2;
            default -> 0;
        };
        double lift = pet.lift();
        double top = GROUND - 2 - 8 + drop - lift; // his body is 8 tall, on legs 2 tall

        // Legs: four little stubs (shorter when he sits, tucked away when he lies down, stepping when he walks)
        int legs = 2 - drop;
        if (legs > 0) {
            boolean running = mood == Pet.Mood.FREAKOUT; // running on the spot, legs going like mad
            boolean step = (mood == Pet.Mood.WALK && (pet.time() / 150) % 2 == 0) || (running && (pet.time() / 70) % 2 == 0);
            int[] xs = {0, 2, 10, 12};
            for (int i = 0; i < 4; i++) {
                double up = (mood == Pet.Mood.WALK || running) && (i % 2 == 0) == step ? (running ? 1 : 0.5) : 0;
                box(g, unit, LEFT + xs[i], GROUND - legs - lift - up, 1, legs, body);
            }
        }
        // Body
        box(g, unit, LEFT, top, 13, 8, body);
        // Arms: out to the sides, or up in the air when he's happy
        if (mood == Pet.Mood.WORK) {
            // His side nubs are his hands, and they float free (no arms, like a Mii): while he types they're over at
            // the laptop, drawn with it below
        } else if (mood == Pet.Mood.DANCE || mood == Pet.Mood.JUGGLE) {
            // hands going up and down in turns (dancing, or tossing balls)
            boolean up = (pet.time() / (mood == Pet.Mood.DANCE ? 180 : 140)) % 2 == 0;
            box(g, unit, LEFT - 2, top + (up ? 0 : 3), 1, 2, body);
            box(g, unit, LEFT + 14, top + (up ? 3 : 0), 1, 2, body);
        } else if (mood == Pet.Mood.WAVE) {
            // one hand up, waving
            boolean tilt = (pet.time() / 160) % 2 == 0;
            box(g, unit, LEFT - 2, top + 4, 2, 2, body);
            box(g, unit, LEFT + 14 + (tilt ? 0 : 0.6), top - 1, 1, 2, body);
        } else if (mood == Pet.Mood.YELLED) {
            // hands clamped over where his ears would be
            box(g, unit, LEFT - 1, top + 1, 1, 2, body);
            box(g, unit, LEFT + 13, top + 1, 1, 2, body);
        } else if (mood == Pet.Mood.FREAKOUT) {
            // hands flailing up and down
            boolean flap = (pet.time() / 80) % 2 == 0;
            box(g, unit, LEFT - 2, top + (flap ? 0 : 3), 1, 2, body);
            box(g, unit, LEFT + 14, top + (flap ? 3 : 0), 1, 2, body);
            // a couple of sweat drops
            box(g, unit, LEFT + 13.5, top - 1.5 + (pet.time() / 90 % 3) * 0.5, 0.6, 1, new Color(140, 200, 255));
            box(g, unit, LEFT - 1, top - 0.5 + (pet.time() / 110 % 3) * 0.5, 0.6, 1, new Color(140, 200, 255));
        } else if (mood == Pet.Mood.HAPPY || mood == Pet.Mood.RIDE || mood == Pet.Mood.FALL || mood == Pet.Mood.PARTY) {
            box(g, unit, LEFT - 2, top - 1, 1, 3, body);
            box(g, unit, LEFT - 1, top + 1, 1, 1, body);
            box(g, unit, LEFT + 14, top - 1, 1, 3, body);
            box(g, unit, LEFT + 13, top + 1, 1, 1, body);
        } else {
            box(g, unit, LEFT - 2, top + 4, 2, 2, body);
            box(g, unit, LEFT + 13, top + 4, 2, 2, body);
        }

        // Eyes: they follow the cursor (half a unit each way), shut when he blinks or sleeps, and light up for dev apps
        double ex = pet.lookX() * 0.5;
        double ey = pet.lookY() * 0.5;
        boolean sleepy = mood == Pet.Mood.LIE || mood == Pet.Mood.GOODBYE || mood == Pet.Mood.SAD; // half-shut eyes: dozy, or sad
        for (int x : new int[] {3, 11}) {
            double eyeX = LEFT + x + ex;
            double eyeY = top + 2 + ey;
            if (pet.eyesShut()) {
                box(g, unit, LEFT + x, top + 3.5, 1, 0.5, EYE);
            } else if (pet.eyesLit()) {
                box(g, unit, eyeX - 0.5, eyeY - 0.5, 2, 3, GLOW);
                box(g, unit, eyeX, eyeY, 1, 2, LIT);
            } else if (mood == Pet.Mood.ANNOYED) {
                box(g, unit, eyeX, eyeY + 1, 1, 1, EYE);                              // narrowed eyes
                box(g, unit, eyeX + (x == 3 ? -0.5 : 0), eyeY - 0.5 + (x == 3 ? 0 : 0), 1.5, 0.5, EYE); // grumpy eyebrows
                box(g, unit, eyeX + (x == 3 ? 0.5 : -0.5), eyeY, 1, 0.5, EYE);
            } else if (mood == Pet.Mood.YELLED || mood == Pet.Mood.FREAKOUT) {
                box(g, unit, eyeX - 0.5, eyeY - 0.5, 2, 3, EYE); // eyes wide open in shock
                box(g, unit, eyeX, eyeY + 0.5, 1, 1, LIT);
            } else if (sleepy) {
                box(g, unit, eyeX, eyeY + 1, 1, 1, EYE);
            } else {
                box(g, unit, eyeX, eyeY, 1, 2, EYE);
            }
        }
        // His laptop, out in front of him (it rises up as he pulls it out), seen from the back of its screen
        if (mood == Pet.Mood.WORK) {
            double out = Math.min(1, pet.moodTime() / 450.0);
            double ly = GROUND - out * 4.5; // low enough that his eyes peek over it
            Graphics2D clip = (Graphics2D) g.create();
            clip.clipRect(0, 0, WIDTH * unit, GROUND * unit);
            box(clip, unit, LEFT + 3.5, ly - 0.5, 6, 0.5, SCREEN_GLOW);  // light from the screen over its top
            box(clip, unit, LEFT + 3.5, ly, 6, 3.5, LAPTOP_DARK);         // the screen's back
            box(clip, unit, LEFT + 6, ly + 1.25, 1, 1, body);            // a tiny orange logo
            box(clip, unit, LEFT + 2.5, ly + 3.5, 8, 1, LAPTOP);          // the keyboard part
            clip.dispose();
            // His two floating hands typing on the keyboard where it sticks out past the screen, taking turns,
            // never in front of the screen. Until the laptop's all the way out they're still at his sides.
            boolean tap = (pet.time() / 110) % 2 == 0;
            if (out >= 1) {
                box(g, unit, LEFT + 2.5, ly + 2.5 + (tap ? 0.75 : 0), 1, 1, hand);
                box(g, unit, LEFT + 9.5, ly + 2.5 + (tap ? 0 : 0.75), 1, 1, hand);
            } else {
                box(g, unit, LEFT - 2, top + 4, 2, 2, body);
                box(g, unit, LEFT + 13, top + 4, 2, 2, body);
            }
        }

        // Mouth: only while he beeps, flapping open and shut between his eyes
        if (pet.talking()) {
            if (pet.mouthOpen()) {
                box(g, unit, LEFT + 6, top + 4.5, 1, 1.5, EYE);
            } else {
                box(g, unit, LEFT + 5.5, top + 5, 2, 0.5, EYE);
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

        // Dizzy (he's upside down, so these are drawn by his feet and end up over his head): stars going round
        if (mood == Pet.Mood.DIZZY) {
            for (int i = 0; i < 3; i++) {
                double a = pet.time() / 250.0 + i * Math.PI * 2 / 3;
                double sx = LEFT + 6 + Math.cos(a) * 5;
                double sy = GROUND + 0.2 + Math.sin(a) * 0.6;
                box(g, unit, sx, sy - 0.5, 1, 1, LIT);
                box(g, unit, sx - 0.5, sy, 2, 0.4, GLOW);
            }
        }

        drawHat(g, unit, pet.hat(), top);

        // A fly buzzing round him, and his hands clapping at it at the end
        double[] fly = pet.fly();
        if (fly != null) {
            box(g, unit, fly[0], fly[1], 0.7, 0.7, new Color(30, 30, 30));
            boolean wing = (pet.time() / 40) % 2 == 0;
            box(g, unit, fly[0] - 0.3, fly[1] - (wing ? 0.4 : 0.2), 0.5, 0.3, new Color(200, 220, 255, 170));
            box(g, unit, fly[0] + 0.5, fly[1] - (wing ? 0.2 : 0.4), 0.5, 0.3, new Color(200, 220, 255, 170));
        }
        if (mood == Pet.Mood.FLY && pet.moodTime() >= 4200 && pet.moodTime() < 4700) {
            box(g, unit, LEFT + 5, top - 2, 1, 1, hand);   // clap!
            box(g, unit, LEFT + 6.2, top - 2, 1, 1, hand);
        }

        // Sneeze spray
        if (mood == Pet.Mood.SNEEZE && pet.moodTime() >= 700) {
            double t = (pet.moodTime() - 700) / 600.0;
            for (int i = 0; i < 6; i++) {
                box(g, unit, LEFT + 6 + (i - 2.5) * (1 + t * 3), top + 5 - t * 2 + (i % 2) * 0.5, 0.5, 0.5,
                        new Color(200, 230, 255, (int) Math.max(0, 200 * (1 - t))));
            }
        }

        // Birthday: a cake by his side, and his party blower going out and back from his mouth
        if (pet.birthdayToday() && mood != Pet.Mood.WORK && mood != Pet.Mood.PEEK) {
            Color cake = new Color(250, 225, 200), icing = new Color(255, 140, 180);
            box(g, unit, 0.5, GROUND - 3, 3.5, 3, cake);
            box(g, unit, 0.5, GROUND - 3, 3.5, 0.8, icing);
            box(g, unit, 2, GROUND - 4.2, 0.5, 1.2, new Color(140, 200, 255));          // a candle
            if ((pet.time() / 120) % 2 == 0) box(g, unit, 1.9, GROUND - 4.9, 0.7, 0.7, new Color(255, 190, 60)); // its flame
        }
        double blow = pet.blower();
        if (mood == Pet.Mood.BIRTHDAY) {
            double mouthX = LEFT + 7, mouthY = top + 5;
            Color paper = new Color(255, 214, 102), stripe = new Color(255, 100, 150);
            if (blow < 0.15) {
                box(g, unit, mouthX, mouthY - 0.5, 1.2, 1.2, paper);  // rolled up in a little curl
                box(g, unit, mouthX + 0.3, mouthY - 0.2, 0.6, 0.6, stripe);
            } else {
                double length = 1 + blow * 7;
                box(g, unit, mouthX, mouthY, length, 0.8, paper);    // unrolled, straight out
                for (double s = 1; s < length; s += 1.5) box(g, unit, mouthX + s, mouthY, 0.5, 0.8, stripe);
            }
        }

        // Confetti!
        if (mood == Pet.Mood.PARTY || (mood == Pet.Mood.BIRTHDAY && pet.moodTime() > 3200)) {
            Color[] colors = {new Color(255, 214, 102), new Color(120, 220, 255), new Color(255, 120, 170), new Color(140, 230, 140)};
            for (int i = 0; i < 14; i++) {
                double t = (pet.moodTime() / 1400.0 + i * 0.13) % 1;
                double x = (i * 7.3) % WIDTH + Math.sin(t * 8 + i) * 0.8;
                box(g, unit, x, -1 + t * (GROUND + 1), 0.6, 0.6, colors[i % colors.length]);
            }
        }

        // Petted: little hearts float up
        if (mood == Pet.Mood.LOVED) {
            for (int i = 0; i < 3; i++) {
                double t = (pet.moodTime() / 900.0 + i / 3.0) % 1;
                heart(g, unit, LEFT + 3 + i * 4 + Math.sin(t * 6 + i) * 0.5, top - 1 - t * 4, (int) (255 * (1 - t)));
            }
        }

        // Juggling: three balls going round above his head
        if (mood == Pet.Mood.JUGGLE) {
            Color[] balls = {new Color(255, 214, 102), new Color(120, 220, 255), new Color(255, 140, 170)};
            for (int i = 0; i < 3; i++) {
                double a = pet.moodTime() / 260.0 + i * Math.PI * 2 / 3;
                box(g, unit, LEFT + 6 + Math.cos(a) * 5, top - 2.5 + Math.sin(a) * 2.2, 1, 1, balls[i]);
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

    /**
     * Draws him turned by angle (0 standing, PI upside down, as when he falls head first), shifted down as he turns so
     * his head, not the air above it, meets the ground.
     */
    public static void drawTurned(Graphics2D g, Pet pet, int unit, double angle) {
        Graphics2D turned = (Graphics2D) g.create();
        if (angle != 0) {
            turned.translate(0, 4 * unit * (1 - Math.cos(angle)) / 2);
            turned.rotate(angle, WIDTH * unit / 2.0, HEIGHT * unit / 2.0);
        }
        draw(turned, pet, unit);
        turned.dispose();
    }

    /**
     * Draws him climbing up from behind an edge (the ground line): sunk units of him are still below it and hidden,
     * so he seems to come up from behind the window's top.
     */
    public static void drawRising(Graphics2D g, Pet pet, int unit, double sunk) {
        Graphics2D rising = (Graphics2D) g.create();
        rising.clipRect(0, 0, WIDTH * unit, GROUND * unit);
        rising.translate(0, sunk * unit);
        draw(rising, pet, unit);
        rising.dispose();
    }

    /**
     * Saying goodbye: he crumbles away into dust that drifts off up and to the right, a bit at a time, the far side of
     * him first. crumbled goes from 0 (all there) to 1 (gone). Draw into a drawing with room above and to the right.
     */
    public static void drawCrumbling(Graphics2D g, Pet pet, int unit, double crumbled) {
        if (crumbled <= 0) {
            draw(g, pet, unit);
            return;
        }
        int fine = 4; // look at him in quarter-unit specks
        java.awt.image.BufferedImage whole = new java.awt.image.BufferedImage(WIDTH * fine, HEIGHT * fine, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D w = whole.createGraphics();
        draw(w, pet, fine);
        w.dispose();
        double speck = unit / (double) fine;
        java.util.Random random = new java.util.Random(7);
        for (int y = 0; y < whole.getHeight(); y++) {
            for (int x = 0; x < whole.getWidth(); x++) {
                int argb = whole.getRGB(x, y);
                double luck = random.nextDouble();
                if ((argb >>> 24) == 0) continue;
                // when this speck goes: mostly at random, the right side and top a little sooner
                double when = 0.65 * luck + 0.35 * (1 - x / (double) whole.getWidth()) * 0.6 + 0.35 * (y / (double) whole.getHeight()) * 0.4;
                double age = (crumbled - when) / 0.35;
                if (age <= 0) {
                    g.setColor(new Color(argb, true));
                    g.fillRect((int) Math.round(x * speck), (int) Math.round(y * speck), (int) Math.ceil(speck), (int) Math.ceil(speck));
                    continue;
                }
                if (age >= 1) continue;
                double drift = age * (8 + luck * 10) * unit;
                double rise = age * (4 + luck * 6) * unit + Math.sin(age * 6 + luck * 10) * unit * 0.6;
                int alpha = (int) ((argb >>> 24) * (1 - age));
                g.setColor(new Color((argb & 0xFFFFFF) | alpha << 24, true));
                int size = (int) Math.ceil(speck * (1 - age * 0.5));
                g.fillRect((int) Math.round(x * speck + drift), (int) Math.round(y * speck - rise), Math.max(1, size), Math.max(1, size));
            }
        }
    }

    /** A hat from the shop (or the season) on his head, whose top is at top. */
    static void drawHat(Graphics2D g, int unit, String hat, double top) {
        double mid = LEFT + 6.5;
        switch (hat) {
            case "party-hat" -> {
                Color pink = new Color(255, 120, 170), gold = new Color(255, 214, 102);
                box(g, unit, mid - 2.5, top - 1, 5, 1, pink);
                box(g, unit, mid - 1.5, top - 2, 3, 1, gold);
                box(g, unit, mid - 0.5, top - 3, 1, 1, pink);
                box(g, unit, mid - 0.5, top - 4, 1, 1, Color.WHITE);
            }
            case "top-hat" -> {
                Color black = new Color(48, 48, 58), shine = new Color(90, 90, 108);
                box(g, unit, mid - 3.5, top - 0.75, 7, 0.75, black);
                box(g, unit, mid - 2, top - 4, 4, 3.25, black);
                box(g, unit, mid - 2, top - 4, 0.5, 3.25, shine); // a glint down one side
                box(g, unit, mid - 2, top - 1.5, 4, 0.6, new Color(200, 50, 60));
            }
            case "crown" -> {
                Color gold = new Color(255, 200, 60);
                box(g, unit, mid - 2.5, top - 1.5, 5, 1.5, gold);
                for (int i = 0; i < 3; i++) box(g, unit, mid - 2.5 + i * 2, top - 2.5, 1, 1, gold);
                box(g, unit, mid - 0.5, top - 1.1, 1, 0.7, new Color(220, 40, 60));
            }
            case "santa" -> {
                Color red = new Color(210, 40, 50);
                box(g, unit, mid - 3, top - 1, 6, 1, Color.WHITE);
                box(g, unit, mid - 2, top - 2, 4, 1, red);
                box(g, unit, mid - 0.5, top - 3, 2.5, 1, red);
                box(g, unit, mid + 2, top - 3.5, 1, 1, Color.WHITE);
            }
            case "birthday" -> {
                // a striped party hat with a pom-pom
                Color blue = new Color(100, 170, 255), yellow = new Color(255, 214, 102);
                box(g, unit, mid - 2.5, top - 1, 5, 1, blue);
                box(g, unit, mid - 1.5, top - 2, 3, 1, yellow);
                box(g, unit, mid - 1, top - 3, 2, 1, blue);
                box(g, unit, mid - 0.5, top - 4, 1, 1, yellow);
                box(g, unit, mid - 0.75, top - 5, 1.5, 1, new Color(255, 120, 170));
            }
            case "pumpkin" -> {
                box(g, unit, mid - 2.5, top - 2.5, 5, 2.5, new Color(240, 140, 30));
                box(g, unit, mid - 0.5, top - 3.5, 1, 1, new Color(70, 140, 60));
            }
            default -> { }
        }
    }

    /** A little pink heart, 3 units wide, fading out with alpha. */
    private static void heart(Graphics2D g, int unit, double x, double y, int alpha) {
        if (alpha <= 0) return;
        Color pink = new Color(255, 110, 150, Math.max(0, Math.min(255, alpha)));
        box(g, unit, x, y, 1, 1, pink);
        box(g, unit, x + 2, y, 1, 1, pink);
        box(g, unit, x, y + 1, 3, 1, pink);
        box(g, unit, x + 1, y + 2, 1, 1, pink);
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

    /** The point between his feet, across the drawing, in units. */
    public static double feetX() {
        return LEFT + 6.5;
    }

    /** Where his eyes are in the drawing, in units (for working out where the cursor is from them). */
    public static double eyesX() {
        return LEFT + 6.5;
    }

    public static double eyesY() {
        return GROUND - 2 - 8 + 3;
    }
}
