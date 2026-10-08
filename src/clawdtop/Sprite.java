package clawdtop;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Draws Clawd, block by block, the way he looks in Claude Code: an orange body with two dark eyes, little arms out to
 * the sides and four legs. Everything is in his own pixels ("units"); the window says how many screen pixels a unit is.
 */
public final class Sprite {
    /** How big the drawing is, in units: room to bounce, raise his arms and let out z's. */
    public static final int WIDTH = 23; // (room at his right for his laptop)
    public static final int HEIGHT = 15;

    static final Color ORANGE = new Color(215, 119, 87); // his own color
    private static Color body = ORANGE;                  // the color he is right now (drawing is on one thread)
    private static Color hand = ORANGE.darker();
    static final Color EYE = new Color(20, 20, 20);
    static final Color LIT = new Color(255, 251, 214);
    static final Color GLOW = new Color(255, 214, 102, 150);
    static final Color ZZZ = new Color(200, 210, 230, 220);
    static final Color LAPTOP = new Color(128, 128, 128); // the plain gray laptop from the Claude Code animation

    private static final int GROUND = 14; // the row his feet stand on (the bottom of the drawing)
    private static final int LEFT = 5;    // where his body starts (in the middle)

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
        if (mood == Pet.Mood.DANCE || made(pet, Creation.Effect.DISCO) || made(pet, Creation.Effect.MUSIC)) {
            // dancing: swaying side to side
            Graphics2D sway = (Graphics2D) g.create();
            sway.translate(Math.sin(pet.time() / 180.0) * unit, 0);
            drawBody(sway, pet, unit, mood);
            sway.dispose();
            return;
        }
        Creation shown = pet.showing();
        if (shown != null && (shown.effect() == Creation.Effect.GROW || shown.effect() == Creation.Effect.SHRINK)) {
            // he coded himself bigger (or tiny): it pops in, wobbles a bit, and stays till he undoes it
            double pop = Math.min(1, pet.moodTime() / 250.0);
            double size = shown.effect() == Creation.Effect.GROW ? 1 + 0.3 * pop : 1 - 0.45 * pop;
            size += Math.sin(pet.moodTime() / 90.0) * 0.02 * pop;
            Graphics2D scaled = (Graphics2D) g.create();
            scaled.translate(feetX() * unit, GROUND * unit);
            scaled.scale(size, size);
            scaled.translate(-feetX() * unit, -GROUND * unit);
            drawBody(scaled, pet, unit, mood);
            scaled.dispose();
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
        if (mood == Pet.Mood.LAUNCHPAD || mood == Pet.Mood.ROCKET) {
            drawRocketTrip(g, pet, unit, mood);
            return;
        }
        drawBody(g, pet, unit, mood);
        long boom = pet.boomAge();
        if (boom < 900) {
            // KABOOM: a burst of fire and smoke round him
            Color[] fire = {new Color(255, 240, 150), new Color(255, 170, 60), new Color(230, 80, 50), new Color(120, 120, 128)};
            double f = boom / 900.0;
            for (int i = 0; i < 14; i++) {
                double a = i * Math.PI * 2 / 14 + i;
                double r = 1 + f * (4 + i % 3 * 1.5);
                Color c = fire[Math.min(3, (int) (f * 3 + i % 2))];
                double size = 2.2 * (1 - f) + 0.6;
                box(g, unit, feetX() + Math.cos(a) * r * 1.3 - size / 2, GROUND - 5 + Math.sin(a) * r - size / 2, size, size,
                        new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (255 * (1 - f * 0.8))));
            }
        }
    }

    /** When the rocket trip happens (ms into LAUNCHPAD): it lands, the ramp slides out, he walks in, it shuts. */
    static final long ROCKET_LANDS = 500, RAMP_OUT = 900, WALKED_IN = 2100, SHUT = 2500;
    private static final double ROCKET_X = LEFT + 13.5; // the rocket's middle, beside him, while it waits

    /**
     * His rocket trip: a rocket drops in beside him, standing up; a ramp slides out of its door (like the Pikmin ship),
     * he walks up it and in, the door shuts and there's his face in the round window for the countdown. Flying, it's
     * just the rocket (the window turns it the way it's going), flame and all.
     */
    private static void drawRocketTrip(Graphics2D g, Pet pet, int unit, Pet.Mood mood) {
        long t = pet.moodTime();
        boolean flying = mood == Pet.Mood.ROCKET;
        if (flying) {
            // off it goes, sliding over to the middle of the window as it lifts
            double x = feetX() + (ROCKET_X - feetX()) * (1 - ease(t / 500.0));
            drawRocket(g, unit, x, GROUND - 1.5, 0, true, true, pet, 0);
            return;
        }
        double drop = (1 - ease(t / (double) ROCKET_LANDS)) * -16;
        double ramp = t < RAMP_OUT ? 0 : t < WALKED_IN ? ease((t - RAMP_OUT) / 300.0) : 1 - ease((t - WALKED_IN) / 300.0);
        boolean doorOpen = t >= RAMP_OUT - 100 && t < SHUT;
        boolean inside = t >= WALKED_IN;
        double shake = t > 6600 ? Math.sin(t / 25.0) * 0.15 : 0;
        drawRocket(g, unit, ROCKET_X + shake, GROUND + drop, ramp, t > 7000, inside, pet, doorOpen ? 1 : 0);
        if (!inside) {
            // him, then walking up the ramp and in through the door (the rocket hides what's gone in)
            double walk = t < RAMP_OUT + 300 ? 0 : ease((t - RAMP_OUT - 300) / (double) (WALKED_IN - RAMP_OUT - 300));
            Graphics2D him = (Graphics2D) g.create();
            double door = ROCKET_X - 3;
            him.clipRect(-WIDTH * unit, -HEIGHT * unit, (int) Math.round((door + WIDTH) * unit), HEIGHT * unit * 3);
            boolean step = walk > 0 && walk < 1 && (pet.time() / 150) % 2 == 0;
            double back = -3 * ease(t / (double) ROCKET_LANDS); // whoa: a step back as it lands
            him.translate((back + walk * 16) * unit, (-walk * 1.5 - (step ? 0.3 : 0)) * unit);
            drawBody(him, pet, unit, Pet.Mood.IDLE);
            him.dispose();
        }
    }

    /**
     * A rocket standing up, its middle at x and its bottom at bottom. ramp (0 to 1) is how far its ramp has slid out of
     * the door (down to the left); lit, there's a flame; inside, his face is in the round window.
     */
    private static void drawRocket(Graphics2D g, int unit, double x, double bottom, double ramp, boolean lit, boolean inside,
            Pet pet, double doorOpen) {
        Color hull = new Color(236, 238, 244), shade = new Color(196, 200, 212), red = new Color(220, 60, 60);
        Color glass = new Color(120, 200, 255), metal = new Color(110, 116, 128);
        double top = bottom - 13;
        if (lit) {
            boolean flick = (pet.time() / 60) % 2 == 0;
            box(g, unit, x - 1.2, bottom - 0.6, 2.4, flick ? 2.2 : 1.7, new Color(255, 170, 60));
            box(g, unit, x - 0.6, bottom - 0.6, 1.2, flick ? 1.3 : 1, new Color(255, 240, 150));
        }
        box(g, unit, x - 1.5, bottom - 1.5, 3, 1, metal);                 // nozzle
        box(g, unit, x - 3, top + 2, 6, 9.5, hull);                       // hull
        box(g, unit, x + 1.8, top + 2, 1.2, 9.5, shade);                  // its shady side
        box(g, unit, x - 2, top + 1, 4, 1, red);                          // nose cone
        box(g, unit, x - 1, top, 2, 1, red);
        box(g, unit, x - 4.5, bottom - 5, 1.5, 3.5, red);                 // fins
        box(g, unit, x + 3, bottom - 5, 1.5, 3.5, red);
        box(g, unit, x - 3, top + 7.5, 6, 0.5, red);                      // a stripe
        // the round window, and him in it
        box(g, unit, x - 2, top + 3, 4, 3.6, metal);
        box(g, unit, x - 1.6, top + 3.4, 3.2, 2.8, glass);
        if (inside) {
            box(g, unit, x - 1.6, top + 4.2, 3.2, 2, body);
            boolean blink = pet.eyesShut();
            box(g, unit, x - 1, top + (blink ? 5 : 4.5), 0.6, blink ? 0.3 : 1, EYE);
            box(g, unit, x + 0.4, top + (blink ? 5 : 4.5), 0.6, blink ? 0.3 : 1, EYE);
        }
        box(g, unit, x - 1.2, top + 3.6, 0.6, 0.6, new Color(255, 255, 255, 170)); // glint
        // the door (low down on the left), and the ramp sliding out of it
        if (doorOpen > 0) box(g, unit, x - 3, bottom - 5, 2.2, 3.5, new Color(40, 42, 50));
        else box(g, unit, x - 3, bottom - 5, 0.3, 3.5, shade);
        if (ramp > 0) {
            double length = 5 * ramp;
            for (double d = 0; d < length; d += 0.5) {
                box(g, unit, x - 3 - d - 0.5, bottom - 1.5 + d * 1.5 / 5, 0.6, 0.5, metal);
            }
        }
    }

    private static void drawBody(Graphics2D g, Pet pet, int unit, Pet.Mood mood) {
        if (mood == Pet.Mood.WORK || mood == Pet.Mood.PACK || mood == Pet.Mood.CODING) {
            drawAtLaptop(g, pet, unit, mood);
            return;
        }
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
            boolean walking = mood == Pet.Mood.WALK || mood == Pet.Mood.CARRY;
            boolean step = (walking && (pet.time() / 150) % 2 == 0) || (running && (pet.time() / 70) % 2 == 0);
            int[] xs = {0, 2, 10, 12};
            for (int i = 0; i < 4; i++) {
                double up = (walking || running) && (i % 2 == 0) == step ? (running ? 1 : 0.5) : 0;
                box(g, unit, LEFT + xs[i], GROUND - legs - lift - up, 1, legs, body);
            }
        }
        // Body
        box(g, unit, LEFT, top, 13, 8, body);
        // Arms: out to the sides, or up in the air when he's happy
        if (mood == Pet.Mood.DANCE || made(pet, Creation.Effect.DISCO) || made(pet, Creation.Effect.MUSIC) || mood == Pet.Mood.JUGGLE) {
            // hands going up and down in turns (dancing, or tossing balls)
            boolean up = (pet.time() / (mood == Pet.Mood.JUGGLE ? 140 : 180)) % 2 == 0;
            box(g, unit, LEFT - 2, top + (up ? 0 : 3), 1, 2, body);
            box(g, unit, LEFT + 14, top + (up ? 3 : 0), 1, 2, body);
        } else if (mood == Pet.Mood.WAVE) {
            // one hand up, waving
            boolean tilt = (pet.time() / 160) % 2 == 0;
            box(g, unit, LEFT - 2, top + 4, 2, 2, body);
            box(g, unit, LEFT + 14 + (tilt ? 0 : 0.6), top - 1, 1, 2, body);
        } else if (mood == Pet.Mood.CARRY) {
            // both hands up, holding a big moving box over his head
            box(g, unit, LEFT + 1, top - 1, 1, 1, hand);
            box(g, unit, LEFT + 11, top - 1, 1, 1, hand);
            box(g, unit, LEFT + 1.5, top - 4, 10, 3, Box.CARDBOARD);
            box(g, unit, LEFT + 1.5, top - 4, 10, 0.7, Box.CARDBOARD_DARK);
            box(g, unit, LEFT + 6, top - 4, 1.2, 3, Box.TAPE);
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
        } else if (mood == Pet.Mood.SORRY) {
            // hands fidgeting down in front of him, looking at his feet, a nervous sweat drop
            boolean fidget = (pet.time() / 400) % 2 == 0;
            box(g, unit, LEFT + 3.5 + (fidget ? 0.3 : 0), top + 6.5, 1.5, 1.5, hand);
            box(g, unit, LEFT + 8 - (fidget ? 0.3 : 0), top + 6.5, 1.5, 1.5, hand);
            box(g, unit, LEFT + 13.2, top - 0.5 + (pet.time() / 300 % 3) * 0.3, 0.6, 1, new Color(140, 200, 255));
        } else if (mood == Pet.Mood.HAPPY || mood == Pet.Mood.RIDE || mood == Pet.Mood.FALL || mood == Pet.Mood.PARTY
                || mood == Pet.Mood.CARPET) {
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
        boolean sleepy = mood == Pet.Mood.LIE || mood == Pet.Mood.GOODBYE || mood == Pet.Mood.SAD // half-shut eyes: dozy, or sad
                || mood == Pet.Mood.SORRY || made(pet, Creation.Effect.RAIN);
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
            box(g, unit, LEFT - 3.5, GROUND - 3, 3.5, 3, cake);
            box(g, unit, LEFT - 3.5, GROUND - 3, 3.5, 0.8, icing);
            box(g, unit, LEFT - 2, GROUND - 4.2, 0.5, 1.2, new Color(140, 200, 255));          // a candle
            if ((pet.time() / 120) % 2 == 0) box(g, unit, LEFT - 2.1, GROUND - 4.9, 0.7, 0.7, new Color(255, 190, 60)); // its flame
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

        // Unpacking: boxes beside him, flaps open, his things popping out
        if (mood == Pet.Mood.UNPACK) {
            long t = pet.moodTime();
            for (int b = 0; b < 2; b++) {
                double bx = b == 0 ? LEFT - 4.5 : LEFT + 13;
                box(g, unit, bx, GROUND - 4, 4.5, 4, Box.CARDBOARD);
                box(g, unit, bx + 1.6, GROUND - 4, 1.2, 4, Box.TAPE);
                if (t > 400 + b * 700) {
                    box(g, unit, bx - 1, GROUND - 5, 2, 0.8, Box.CARDBOARD_DARK); // flaps open
                    box(g, unit, bx + 3.5, GROUND - 5, 2, 0.8, Box.CARDBOARD_DARK);
                    double pop = Math.min(1, (t - 400 - b * 700) / 500.0);
                    Color thing = b == 0 ? new Color(255, 214, 102) : new Color(255, 120, 170);
                    box(g, unit, bx + 1.6, GROUND - 4.5 - pop * 3, 1.2, 1.2, thing);     // a star, a heart...
                }
            }
        }

        // His flying carpet: rainbow stripes under his feet, rippling as he flies, with tassels
        if (mood == Pet.Mood.CARPET) {
            Color[] rainbow = {new Color(235, 80, 80), new Color(245, 160, 60), new Color(245, 220, 80),
                    new Color(110, 200, 110), new Color(90, 150, 240), new Color(160, 110, 220)};
            for (int i = 0; i < 19; i++) {
                double wave = Math.sin(pet.time() / 110.0 + i * 0.7) * 0.25;
                for (int b = 0; b < rainbow.length; b++) box(g, unit, LEFT - 3 + i, GROUND - 0.2 + b * 0.2 + wave, 1, 0.2, rainbow[b]);
            }
            Color tassel = new Color(255, 214, 102);
            box(g, unit, LEFT - 3.6, GROUND + 0.2 + Math.sin(pet.time() / 110.0) * 0.25, 0.6, 0.4, tassel);
            box(g, unit, LEFT + 16, GROUND + 0.2 + Math.sin(pet.time() / 110.0 + 18 * 0.7) * 0.25, 0.6, 0.4, tassel);
        }

        // The disco ball he coded, spinning over his head, throwing spots of light about
        if (made(pet, Creation.Effect.DISCO)) {
            Color silver = new Color(200, 205, 215), shine = new Color(245, 248, 255);
            box(g, unit, LEFT + 6.4, 0, 0.2, top - 3.5, new Color(150, 150, 160));
            box(g, unit, LEFT + 5.5, top - 3.5, 2, 2, silver);
            int turn = (int) (pet.time() / 150 % 4);
            box(g, unit, LEFT + 5.5 + (turn % 2) * 1, top - 3.5 + (turn / 2) * 1, 1, 1, shine);
            Color[] lights = {new Color(255, 120, 170, 170), new Color(120, 220, 255, 170), new Color(255, 230, 120, 170)};
            for (int i = 0; i < 3; i++) {
                double a = pet.time() / 400.0 + i * 2.1;
                box(g, unit, LEFT + 6 + Math.cos(a) * 5, top + 3.5 + Math.sin(a * 1.3) * 2.5, 1, 1, lights[i]);
            }
        }

        // The rain cloud he made by accident: right over him, raining on his head
        if (made(pet, Creation.Effect.RAIN)) {
            Color cloud = new Color(150, 156, 168), dark = new Color(120, 126, 138);
            box(g, unit, LEFT + 2, top - 3, 9, 1.8, cloud);
            box(g, unit, LEFT + 3.5, top - 4, 3, 1, cloud);
            box(g, unit, LEFT + 6.5, top - 4.5, 3, 1.5, cloud);
            box(g, unit, LEFT + 2, top - 1.6, 9, 0.4, dark);
            Color rain = new Color(120, 180, 255);
            for (int i = 0; i < 5; i++) {
                double t = (pet.time() / 420.0 + i * 0.37) % 1;
                box(g, unit, LEFT + 3 + i * 1.7, top - 1.2 + t * 1.6, 0.4, 0.7, rain);
            }
        }
        drawCreation(g, pet, unit, top);

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

    /** What he made, out beside him (the disco ball and rain cloud are drawn with him; this is the rest). */
    private static void drawCreation(Graphics2D g, Pet pet, int unit, double top) {
        Creation c = pet.showing();
        if (c == null) return;
        long t = pet.moodTime();
        double pop = Math.min(1, t / 300.0); // things pop in
        switch (c.effect()) {
            case ITEM -> {
                double bob = Math.sin(t / 300.0) * 0.3;
                Pixels.draw(g, unit, c.item(), 0, GROUND - 4.3 + bob - (1 - pop) * 2, 0.6);
            }
            case SNOW -> {
                for (int i = 0; i < 16; i++) {
                    double f = (t / 2600.0 + i * 0.137) % 1;
                    double x = (i * 5.3) % WIDTH + Math.sin(f * 6 + i) * 0.8;
                    box(g, unit, x, -0.5 + f * (GROUND + 0.5), 0.5, 0.5, new Color(245, 248, 255, 220));
                }
            }
            case FIREWORKS -> {
                Color[] colors = {new Color(255, 120, 170), new Color(120, 220, 255), new Color(255, 214, 102), new Color(150, 240, 140)};
                for (int k = 0; k < 3; k++) {
                    double f = ((t + k * 600) % 1800) / 1800.0;
                    double cx = 3 + k * 7.5, cy = 2.5 + (k % 2);
                    if (f < 0.3) {
                        box(g, unit, cx, GROUND - f / 0.3 * (GROUND - cy), 0.4, 0.8, new Color(255, 230, 180)); // going up
                    } else {
                        double r = (f - 0.3) / 0.7 * 3;
                        int alpha = (int) (255 * (1 - (f - 0.3) / 0.7));
                        Color col = colors[(k + (int) (t / 1800)) % colors.length];
                        col = new Color(col.getRed(), col.getGreen(), col.getBlue(), Math.max(0, alpha));
                        for (int a = 0; a < 8; a++) {
                            double ang = a * Math.PI / 4;
                            box(g, unit, cx + Math.cos(ang) * r, cy + Math.sin(ang) * r, 0.5, 0.5, col);
                        }
                    }
                }
            }
            case PIZZA -> {
                // a pizza beside him, one slice gone at a time
                int left = 4 - (int) Math.min(4, t / (Math.max(1, c.showFor()) / 5.0));
                Color crust = new Color(225, 170, 90), cheese = new Color(250, 210, 90), pepperoni = new Color(205, 60, 50);
                double px = LEFT + 14, py = GROUND - 3.5;
                for (int q = 0; q < left; q++) {
                    double qx = px + (q % 2) * 1.5, qy = py + (q / 2) * 1.5;
                    box(g, unit, qx, qy, 1.5, 1.5, crust);
                    box(g, unit, qx + 0.25, qy + 0.25, 1, 1, cheese);
                    box(g, unit, qx + 0.5, qy + 0.5, 0.4, 0.4, pepperoni);
                }
                if (left < 4 && (t / 200) % 2 == 0) box(g, unit, LEFT + 6, top + 5, 1, 0.5, EYE); // munch
            }
            case BUBBLES -> {
                for (int i = 0; i < 6; i++) {
                    double f = (t / 2200.0 + i / 6.0) % 1;
                    double x = LEFT + 6 + Math.sin(f * 5 + i * 2) * 5;
                    double size = 0.8 + (i % 3) * 0.4;
                    int alpha = (int) (200 * (1 - f));
                    box(g, unit, x, top + 2 - f * 6, size, size, new Color(180, 225, 255, alpha / 2));
                    box(g, unit, x + 0.15, top + 2.15 - f * 6, 0.3, 0.3, new Color(255, 255, 255, alpha));
                }
            }
            case SHADES -> {
                // cool sunglasses over his eyes
                Color black = new Color(20, 20, 24);
                double drop = (1 - pop) * -3; // they slide down onto his face
                box(g, unit, LEFT + 2, top + 2 + drop, 3, 1.6, black);
                box(g, unit, LEFT + 9.5, top + 2 + drop, 3, 1.6, black);
                box(g, unit, LEFT + 5, top + 2.3 + drop, 4.5, 0.5, black);
                box(g, unit, LEFT + 2.4, top + 2.3 + drop, 0.8, 0.4, new Color(255, 255, 255, 160)); // glint
            }
            case MUSIC -> {
                for (int i = 0; i < 3; i++) {
                    double f = (t / 1500.0 + i / 3.0) % 1;
                    double x = LEFT + 12 + Math.sin(f * 4 + i) * 1.5 + i;
                    int alpha = (int) (255 * (1 - f));
                    Color note = new Color(255, 214, 102, alpha);
                    box(g, unit, x, top - f * 4, 0.7, 0.6, note);        // a little note: head
                    box(g, unit, x + 0.5, top - 1.4 - f * 4, 0.25, 1.6, note); // and stem
                }
            }
            case DUCK -> {
                Color yellow = new Color(250, 215, 70), beak = new Color(245, 140, 40);
                double dx = LEFT - 4.5, dy = GROUND - 3 + (pop < 1 ? (1 - pop) * 3 : 0);
                box(g, unit, dx, dy + 1, 3.5, 2, yellow);     // body
                box(g, unit, dx + 2, dy, 1.6, 1.5, yellow);   // head
                box(g, unit, dx + 3.5, dy + 0.6, 0.8, 0.5, beak);
                box(g, unit, dx + 2.8, dy + 0.3, 0.4, 0.4, EYE);
                if ((t / 900) % 3 == 1) box(g, unit, dx + 0.4, dy - 1.2, 2.6, 0.9, new Color(255, 255, 255, 200)); // "quack?"
            }
            case CLONE -> {
                // Mini Clawd, copying him, a beat behind (and glitching a bit)
                double s = 0.3, mx = LEFT + 12.8, my = GROUND - 10 * s;
                boolean glitch = (t / 130) % 9 == 0;
                Color mini = glitch ? new Color(120, 220, 255) : body;
                double hop = Math.abs(Math.sin(t / 220.0)) * 0.6;
                box(g, unit, mx + 2 * s, my - hop, 13 * s, 8 * s, mini);
                for (int x : new int[] {0, 2, 10, 12}) box(g, unit, mx + (2 + x) * s, my + 8 * s - hop, s, 2 * s, mini);
                box(g, unit, mx, my + 4 * s - hop, 2 * s, 2 * s, mini);
                box(g, unit, mx + 15 * s, my + 4 * s - hop, 2 * s, 2 * s, mini);
                box(g, unit, mx + 5 * s, my + 2 * s - hop, s, 2 * s, EYE);
                box(g, unit, mx + 13 * s, my + 2 * s - hop, s, 2 * s, EYE);
            }
            default -> { }
        }
    }

    /** Whether he's showing off something he made with this effect. */
    private static boolean made(Pet pet, Creation.Effect effect) {
        Creation c = pet.showing();
        return c != null && c.effect() == effect;
    }

    /** How long getting his laptop out takes, before he's typing, and how long putting it away takes (ms). */
    static final long SET_UP = 1150, PUT_AWAY = 600;
    private static final double SLIDE = 2;                 // he scoots this far left to make room for it
    private static final double BASE_X = LEFT + 13.5;      // where the laptop sits on the ground (before the scoot)
    private static final double BASE_W = 4.2, SCREEN = 3.6, THICK = 0.5;

    private enum Pose { FRONT, REACH, LIFT, SWING, SET, SIDE, TYPE, FOLD, BACK }

    private static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /**
     * At his laptop, move for move like Clawd's laptop animation in Claude Code. Getting it out: he reaches round for
     * it (one eye squeezed shut), lifts it up over his head and flips it open, swings it down beside him, turns side-on
     * and crouches to type, tapping away on loop. Putting it away (PACK): he stands, folds it shut as he picks it up,
     * and turns back round.
     */
    private static void drawAtLaptop(Graphics2D g, Pet pet, int unit, Pet.Mood mood) {
        long t = pet.moodTime();
        Pose pose;
        double slide;
        if (mood != Pet.Mood.PACK) {
            pose = t < 100 ? Pose.FRONT : t < 560 ? Pose.REACH : t < 760 ? Pose.LIFT : t < 900 ? Pose.SWING
                    : t < 1050 ? Pose.SET : t < SET_UP ? Pose.SIDE : Pose.TYPE;
            slide = SLIDE * ease((t - 100) / 460.0);
        } else {
            pose = t < 100 ? Pose.SIDE : t < 200 ? Pose.FOLD : t < 400 ? Pose.BACK : Pose.FRONT;
            slide = SLIDE * (1 - ease((t - 200) / 250.0));
        }
        Graphics2D s = (Graphics2D) g.create();
        s.translate(-slide * unit, 0);
        boolean side = pose == Pose.SIDE || pose == Pose.TYPE;
        boolean crouch = pose == Pose.TYPE;
        double top = GROUND - 10 + (crouch ? 0.5 : 0);
        Color shade = new Color(body.getRed() * 88 / 100, body.getGreen() * 88 / 100, body.getBlue() * 88 / 100);

        // Legs: four stubs, or crouched with his feet out
        for (int x : new int[] {0, 2, 10, 12}) {
            if (crouch) {
                box(s, unit, LEFT + x, GROUND - 1.5, 1, 1.5, body);
                box(s, unit, LEFT + x - 0.5, GROUND - 0.5, 1.5, 0.5, body);
            } else {
                box(s, unit, LEFT + x, GROUND - 2, 1, 2, body);
            }
        }
        // Body (side-on, the near side of him is in shade)
        box(s, unit, LEFT, top, 13, 8, body);
        if (side) box(s, unit, LEFT, top, 2, 8, shade);

        // Hands, and the laptop
        boolean tap = (pet.time() / 130) % 2 == 0;
        switch (pose) {
            case FRONT -> {
                box(s, unit, LEFT - 2, top + 4, 2, 2, body);
                box(s, unit, LEFT + 13, top + 4, 2, 2, body);
            }
            case REACH, BACK -> { // leaning: one hand up, the other reaching down round his back
                box(s, unit, LEFT - 2, top + 2, 2, 2, body);
                box(s, unit, LEFT + 13, top + 5, 2, 2, body);
            }
            case LIFT -> { // holding it up over his head, flipping it open
                box(s, unit, LEFT - 2, top + 4, 2, 2, body);
                box(s, unit, LEFT + 13, top, 2, 2, body);
                double open = ease((t - 600) / 120.0);
                laptop(s, unit, BASE_X + BASE_W, top, 180 - open * 90, 0);
            }
            case SWING -> { // swinging it down beside him
                double k = ease((t - 760) / 140.0);
                box(s, unit, LEFT - 2, top + 4, 2, 2, body);
                box(s, unit, LEFT + 13, top + k * 4, 2, 2, body);
                laptop(s, unit, BASE_X + BASE_W + Math.sin(k * Math.PI) * 1.2, top + k * (GROUND - top),
                        90 - k * 45, -Math.sin(k * Math.PI) * 40);
            }
            case SET -> { // set down; a last nudge
                box(s, unit, LEFT - 2, top + 2, 2, 2, body);
                box(s, unit, LEFT + 13, top + 3, 2.5, 2, body);
                laptop(s, unit, BASE_X + BASE_W, GROUND, 45, 0);
            }
            case SIDE -> { // side-on, standing, a hand down by the keyboard
                laptop(s, unit, BASE_X + BASE_W, GROUND, 45, 0);
                box(s, unit, LEFT + 13, GROUND - 3.5, 2, 2, hand);
            }
            case TYPE -> { // crouched over it, tapping away
                double slam = pet.slam();
                if (slam < 0) {
                    laptop(s, unit, BASE_X + BASE_W, GROUND, 45, 0);
                    box(s, unit, LEFT + 13 + (tap ? 0.5 : 0), GROUND - (tap ? 2.5 : 3.5), 2, 2, hand);
                } else if (slam < Pet.SLAM_HIT) {
                    // the big finish: hand up, high as it goes...
                    double up = ease(slam / (Pet.SLAM_HIT * 0.6));
                    laptop(s, unit, BASE_X + BASE_W, GROUND, 45, 0);
                    box(s, unit, LEFT + 13 + up * 0.5, GROUND - 3.5 - up * 5, 2, 2, hand);
                } else {
                    // ...SLAM. The laptop jumps, and little lines fly off it
                    double after = (slam - Pet.SLAM_HIT) / (1 - Pet.SLAM_HIT);
                    laptop(s, unit, BASE_X + BASE_W, GROUND, 45 + Math.sin(after * Math.PI * 3) * 10 * (1 - after), 0);
                    box(s, unit, LEFT + 13.5, GROUND - 2.4, 2, 2, hand);
                    Color pow = new Color(255, 230, 150, (int) (255 * (1 - after)));
                    double r = 1.2 + after * 1.5;
                    for (int k = 0; k < 5; k++) {
                        double a = Math.PI * (1.05 + k * 0.225);
                        box(s, unit, LEFT + 14.5 + Math.cos(a) * r * 1.4, GROUND - 2 + Math.sin(a) * r, 0.5, 0.5, pow);
                    }
                }
            }
            case FOLD -> { // folding it shut as he picks it up
                double k = ease((t - 100) / 100.0);
                double hingeY = GROUND - k * 3;
                box(s, unit, LEFT - 2, top + 3, 2, 2, body);
                box(s, unit, LEFT + 13, hingeY - 2.5, 2, 2, body);
                laptop(s, unit, BASE_X + BASE_W, hingeY, 45 + k * 110, 0);
            }
        }

        // Eyes: on the laptop while he works; one squeezed shut as he reaches round
        double eyeY = top + 2 + (crouch ? 0.5 : 0) + (pose == Pose.LIFT ? -0.5 : 0);
        double[] eyes = side ? new double[] {LEFT + 6, LEFT + 11.5}
                : pose == Pose.FRONT ? new double[] {LEFT + 3, LEFT + 11} : new double[] {LEFT + 4, LEFT + 11.5};
        for (int i = 0; i < 2; i++) {
            if (pet.eyesShut()) box(s, unit, eyes[i], top + 3.5, 1, 0.5, EYE);
            else if (pose == Pose.REACH && i == 0) box(s, unit, eyes[i] - 0.25, eyeY + 1, 1.5, 0.5, EYE);
            else box(s, unit, eyes[i], eyeY, 1, 2, EYE);
        }
        if (pet.talking()) {
            double mx = (eyes[0] + eyes[1]) / 2;
            if (pet.mouthOpen()) box(s, unit, mx, top + 4.5, 1, 1.5, EYE);
            else box(s, unit, mx - 0.5, top + 5, 2, 0.5, EYE);
        }
        Graphics2D hat = (Graphics2D) s.create();
        hat.translate(((eyes[0] + eyes[1]) / 2 + 0.5 - (LEFT + 6.5)) * unit, 0); // over the middle of his face
        drawHat(hat, unit, pet.hat(), top);
        hat.dispose();
        s.dispose();
    }

    /**
     * The laptop, side-on: a flat gray keyboard part ending at the hinge (hingeX; groundY is the bottom of it), and the
     * screen standing up from the hinge at screenAngle degrees (90 straight up, 45 leaning back, 180 folded shut), all
     * tipped by tilt degrees. Never drawn below the ground.
     */
    private static void laptop(Graphics2D g, int unit, double hingeX, double groundY, double screenAngle, double tilt) {
        Graphics2D l = (Graphics2D) g.create();
        l.clipRect(-WIDTH * unit, -HEIGHT * unit, WIDTH * unit * 3, (GROUND + HEIGHT) * unit);
        l.rotate(Math.toRadians(tilt), hingeX * unit, groundY * unit);
        box(l, unit, hingeX - BASE_W, groundY - THICK, BASE_W, THICK, LAPTOP);
        l.rotate(-Math.toRadians(screenAngle), (hingeX - THICK / 2) * unit, (groundY - THICK / 2) * unit);
        box(l, unit, hingeX - THICK, groundY - THICK, SCREEN, THICK, LAPTOP);
        l.dispose();
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
