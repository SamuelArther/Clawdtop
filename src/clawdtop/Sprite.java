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
    public static final int HEIGHT = 18; // (room above him for tall hats)

    static final Color ORANGE = new Color(215, 119, 87); // his own color
    private static Color body = ORANGE;                  // the color he is right now (drawing is on one thread)
    private static Color hand = ORANGE.darker();
    static final Color EYE = new Color(20, 20, 20);
    static final Color LIT = new Color(255, 251, 214);
    static final Color GLOW = new Color(255, 214, 102, 150);
    static final Color ZZZ = new Color(200, 210, 230, 220);
    static final Color LAPTOP = new Color(128, 128, 128); // the plain gray laptop from the Claude Code animation

    static final int GROUND = 17; // the row his feet stand on (the bottom of the drawing)
    private static final int LEFT = 5;    // where his body starts (in the middle)

    private Sprite() {
    }

    /** Woozy: little yellow birds flying round and round his head (front: the ones on the near side of the circle). */
    private static void drawBirds(Graphics2D g, int unit, Pet pet, double top, boolean front) {
        for (int i = 0; i < 3; i++) {
            double a = pet.time() / 300.0 + i * Math.PI * 2 / 3;
            if ((Math.sin(a) > 0) != front) continue;
            double bx = LEFT + 6 + Math.cos(a) * 6.5, by = top - 1.8 + Math.sin(a) * 1.1;
            boolean right = Math.sin(a) > 0; // which way he's flying (front of the circle: to the right)
            Color bird = new Color(255, 214, 60), beak = new Color(255, 140, 40);
            box(g, unit, bx, by, 1.2, 0.9, bird);                                      // body
            box(g, unit, right ? bx + 1.2 : bx - 0.4, by + 0.2, 0.4, 0.3, beak);       // beak
            box(g, unit, right ? bx + 0.8 : bx + 0.1, by + 0.1, 0.25, 0.25, EYE);      // eye
            boolean flap = ((pet.time() / 120) + i) % 2 == 0;
            box(g, unit, bx + 0.3, by + (flap ? -0.5 : 0.5), 0.7, 0.4, new Color(240, 190, 40)); // wing
        }
    }

    /** How far a hat sticks up above his head (units). */
    static double hatHeight(String hat) {
        return switch (hat == null ? "" : hat) {
            case "wizard" -> 6;
            case "birthday" -> 5;
            case "chef" -> 4.6;
            case "beanie" -> 4.2;
            case "party-hat", "top-hat" -> 4;
            case "" -> 0;
            default -> 3.6;
        };
    }

    private static boolean noHat; // (upside down: his hat would be in the taskbar)

    /** Draws him as he is right now, with the top-left of the drawing at (0, 0). */
    public static void draw(Graphics2D g, Pet pet, int unit) {
        Pet.Mood mood = pet.mood();
        body = pet.color();
        time = pet.time();
        hand = new Color(body.getRed() * 83 / 100, body.getGreen() * 77 / 100, body.getBlue() * 74 / 100); // a shade darker, so his hands show in front of him
        if (mood == Pet.Mood.SPIN) {
            // spun round by a zooming cursor: once all the way round
            Graphics2D spun = (Graphics2D) g.create();
            double p = Math.min(1, pet.moodTime() / 600.0);
            spun.translate(0, -Math.sin(Math.PI * p) * 3 * unit); // a little hop, so he spins clear of the ground
            spun.rotate(p * Math.PI * 2, WIDTH * unit / 2.0, (GROUND - 5) * unit);
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
        if (mood == Pet.Mood.LISTEN) {
            // listening to you play: swaying gently
            Graphics2D sway = (Graphics2D) g.create();
            sway.translate(Math.sin(pet.time() / 300.0) * 0.6 * unit, 0);
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
        if (pet.tripping() >= 0) {
            // tripped: tipping over forwards, then back up
            Graphics2D trip = (Graphics2D) g.create();
            double t = pet.tripping();
            trip.rotate(Math.sin(t * Math.PI) * 0.9, (LEFT + 13) * unit, GROUND * unit); // tipping over his front foot
            drawBody(trip, pet, unit, mood);
            trip.dispose();
            return;
        }
        if (mood == Pet.Mood.CPDANCE) {
            // the Club Penguin dance: a waddle, tipping side to side on the beat
            Graphics2D waddle = (Graphics2D) g.create();
            boolean left = (pet.time() / 260) % 2 == 0;
            waddle.translate((left ? -0.4 : 0.4) * unit, 0);
            waddle.rotate(left ? -0.06 : 0.06, feetX() * unit, GROUND * unit);
            drawBody(waddle, pet, unit, mood);
            waddle.dispose();
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
        if (boom < 300) {
            // KABOOM: a burst of fire and smoke round him
            Color[] fire = {new Color(255, 240, 150), new Color(255, 170, 60), new Color(230, 80, 50), new Color(120, 120, 128)};
            double f = boom / 300.0;
            for (int i = 0; i < 14; i++) {
                double a = i * Math.PI * 2 / 14 + i;
                double r = 1 + f * (4 + i % 3 * 1.5);
                Color c = fire[Math.min(3, (int) (f * 3 + i % 2))];
                double size = 2.2 * (1 - f) + 0.6;
                double down = Math.sin(a) * r * (Math.sin(a) > 0 ? 0.35 : 1); // (the bits going down stop at the floor: inside his square)
                box(g, unit, feetX() + Math.cos(a) * r * 1.3 - size / 2, GROUND - 5 + down - size / 2, size, size,
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
        double shake = t > SHUT + 300 ? Math.sin(t / 25.0) * 0.15 : 0;
        drawRocket(g, unit, ROCKET_X + shake, GROUND + drop, ramp, t > SHUT + 600, inside, pet, doorOpen ? 1 : 0);
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
            double room = Math.max(0.6, HEIGHT - (bottom - 0.6)); // (the flame, as long as there's room below in his square)
            box(g, unit, x - 1.2, bottom - 0.6, 2.4, Math.min(room, flick ? 2.2 : 1.7), new Color(255, 170, 60));
            box(g, unit, x - 0.6, bottom - 0.6, 1.2, Math.min(room, flick ? 1.3 : 1), new Color(255, 240, 150));
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
        if (mood == Pet.Mood.WORK || mood == Pet.Mood.PACK || mood == Pet.Mood.CODING || mood == Pet.Mood.THINK
                || (mood == Pet.Mood.DUCKS && pet.duckSpam())) {
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
            case SIT, WORK, SAD, FOCUS -> 1;
            case LIE, SLEEP, WATCH -> 2;
            default -> 0;
        };
        double lift = Math.min(pet.lift(), Math.max(0, 7 - hatHeight(pet.hat()))); // (a tall hat: smaller hops, so it fits)
        double top = GROUND - 2 - 8 + drop - lift; // his body is 8 tall, on legs 2 tall
        // His one dance: bobbing his body down and up to the beat, feet planted (dancing, music time, the disco ball)
        boolean dancing = mood == Pet.Mood.DANCE || mood == Pet.Mood.VIBE || (mood == Pet.Mood.JAM && pet.jamStep() == Pet.JAM_PLAYING) || made(pet, Creation.Effect.DISCO) || made(pet, Creation.Effect.MUSIC);
        double bob = dancing ? (Math.sin(pet.time() / 300.0 * Math.PI) > 0 ? 0.8 : 0) : 0;
        top += bob;

        // Legs: four little stubs (shorter when he sits, tucked away when he lies down, stepping when he walks)
        int legs = 2 - drop;
        if (legs > 0) {
            boolean running = mood == Pet.Mood.FREAKOUT; // running on the spot, legs going like mad
            boolean walking = mood == Pet.Mood.WALK || mood == Pet.Mood.CARRY || mood == Pet.Mood.LAP || mood == Pet.Mood.CPDANCE
                    || (mood == Pet.Mood.GRAB && !pet.inAir());
            boolean step = (walking && (pet.time() / 150) % 2 == 0) || (running && (pet.time() / 70) % 2 == 0);
            int[] xs = {0, 2, 10, 12};
            for (int i = 0; i < 4; i++) {
                double up = (walking || running) && (i % 2 == 0) == step ? (running ? 1 : 0.5) : 0;
                double planted = pet.breathing() ? lift : 0; // (breathing: his body rises, his feet stay on the ground)
                box(g, unit, LEFT + xs[i], GROUND - legs - lift - up + bob, 1, legs - bob + planted, body);
            }
        }
        // Body
        box(g, unit, LEFT, top, 13, 8, body);
        drawShirt(g, unit, pet.shirt(), top, mood);
        // Arms: out to the sides, or up in the air when he's happy
        boolean holdingClock = pet.clockMs() >= 0 && (mood == Pet.Mood.IDLE || mood == Pet.Mood.SIT || mood == Pet.Mood.HAPPY || mood == Pet.Mood.LOVED
                || mood == Pet.Mood.BLUSH || mood == Pet.Mood.BOOPED || mood == Pet.Mood.HICCUP || mood == Pet.Mood.REMIND);
        if (holdingClock) {
            // (both hands are on his little clock: see drawClock)
        } else if (mood == Pet.Mood.TEAR) {
            drawTear(g, pet, unit, top);
        } else if (mood == Pet.Mood.STASH) {
            drawStash(g, pet, unit, top);
        } else if (pet.held() != null) {
            // both hands up, holding the file he grabbed over his head
            drawFile(g, unit, pet.held(), LEFT + 3.5, top - 5, 1.5);
            box(g, unit, LEFT + 2.5, top - 1.6, 1.2, 1.6, hand);
            box(g, unit, LEFT + 9.3, top - 1.6, 1.2, 1.6, hand);
        } else if (mood == Pet.Mood.GRAB && pet.inAir()) {
            // reaching up as high as he can for it
            box(g, unit, LEFT - 1, top - 3, 1, 3, body);
            box(g, unit, LEFT + 13, top - 3, 1, 3, body);
        } else if (mood == Pet.Mood.GRAB) {
            // dashing over: arms pumping
            boolean pump = (pet.time() / 70) % 2 == 0;
            box(g, unit, LEFT - 1.6, top + (pump ? 2.5 : 4.5), 1.6, 1.6, body);
            box(g, unit, LEFT + 13, top + (pump ? 4.5 : 2.5), 1.6, 1.6, body);
        } else if (mood == Pet.Mood.CPDANCE) {
            // flippers flapping, one up as the other goes down
            boolean up = (pet.time() / 260) % 2 == 0;
            box(g, unit, LEFT - 2, top + (up ? -0.5 : 3), 1.4, 2.5, body);
            box(g, unit, LEFT + 13.6, top + (up ? 3 : -0.5), 1.4, 2.5, body);
        } else if (mood == Pet.Mood.DANCE || made(pet, Creation.Effect.DISCO) || made(pet, Creation.Effect.MUSIC) || mood == Pet.Mood.JUGGLE) {
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
        } else if (mood == Pet.Mood.FALL) {
            // falling: arms flailing like mad
            boolean flail = (pet.time() / 70) % 2 == 0;
            box(g, unit, LEFT - 2, top + (flail ? -0.5 : 3), 1.2, 2.5, body);
            box(g, unit, LEFT + 13.8, top + (flail ? 3 : -0.5), 1.2, 2.5, body);
        } else if (mood == Pet.Mood.LAP) {
            // running flat out: arms pumping
            boolean pump = (pet.time() / 60) % 2 == 0;
            box(g, unit, LEFT - 1.6, top + (pump ? 2.5 : 4.5), 1.6, 1.6, body);
            box(g, unit, LEFT + 13, top + (pump ? 4.5 : 2.5), 1.6, 1.6, body);
        } else if (mood == Pet.Mood.PANT) {
            box(g, unit, LEFT - 2, top + 5, 2, 2, body); // arms hanging, worn out
            box(g, unit, LEFT + 13, top + 5, 2, 2, body);
        } else if (mood == Pet.Mood.REMIND) {
            // waving both hands: hey! hey!
            boolean up = (pet.time() / 140) % 2 == 0;
            box(g, unit, LEFT - 2, top + (up ? -1 : 1), 1.2, 2.5, body);
            box(g, unit, LEFT + 13.8, top + (up ? 1 : -1), 1.2, 2.5, body);
        } else if (mood == Pet.Mood.WATCH) {
            // one hand in the popcorn, the other bringing some up to his mouth now and then
            box(g, unit, LEFT - 1.6, top + 3.5, 1.6, 1.6, body);
            if (pet.munching()) box(g, unit, LEFT + 5.4, top + 4.2, 1.4, 1.4, hand);
            else box(g, unit, LEFT + 13, top + 4, 2, 2, body);
        } else if (mood == Pet.Mood.SCARED) {
            // hands flung up
            box(g, unit, LEFT - 2, top - 1.5, 1.2, 3, body);
            box(g, unit, LEFT + 13.8, top - 1.5, 1.2, 3, body);
        } else if (mood == Pet.Mood.JAM) {
            int step = pet.jamStep();
            if (step == Pet.JAM_SLAM) { // hand way up... then SLAM, down on the laptop
                boolean up = pet.moodTime() < Pet.JAM_SLAM_AT;
                box(g, unit, LEFT - 2, up ? top - 3 : GROUND - 3.6, 1.6, up ? 3 : 1.6, body);
                if (!up && pet.moodTime() < Pet.JAM_SLAM_AT + 250) { // a little flash where it hit
                    box(g, unit, LEFT - 3.6, GROUND - 4.8, 0.6, 0.6, new Color(255, 230, 120));
                    box(g, unit, LEFT - 0.6, GROUND - 4.8, 0.6, 0.6, new Color(255, 230, 120));
                }
                box(g, unit, LEFT + 13, top + 4, 2, 2, body);
            } else if (step == Pet.JAM_PLAYING) { // jamming: hands up and down to the beat
                boolean up = (pet.time() / 300) % 2 == 0;
                box(g, unit, LEFT - 2, top + (up ? 0 : 3), 1, 2, body);
                box(g, unit, LEFT + 14, top + (up ? 3 : 0), 1, 2, body);
            } else { // typing on his laptop, down beside him
                boolean tap = (pet.time() / 110) % 2 == 0;
                box(g, unit, LEFT - 2, GROUND - 3.6 - (tap ? 0.4 : 0), 1.6, 1.6, body);
                box(g, unit, LEFT + 13, top + 4, 2, 2, body);
            }
        } else if (mood == Pet.Mood.PIANO && pet.instrument() == Piano.Instrument.VOICE) {
            // singing: one hand on his chest, the other out, swaying a little with the notes
            box(g, unit, LEFT + 2, top + 5, 2, 2, hand);
            box(g, unit, LEFT + 13, top + (pet.pianoPressing() ? 2 : 3), 2, 2, body);
        } else if (mood == Pet.Mood.PIANO) {
            // (his hands are behind his piano: see drawPiano)
        } else if (mood == Pet.Mood.LISTEN) {
            box(g, unit, LEFT - 2, top + 4, 2, 2, body);
            box(g, unit, LEFT + 13, top + 4, 2, 2, body);
        } else if (mood == Pet.Mood.SALUTE) {
            // a salute (one hand up at his brow), and a little flag in the other
            box(g, unit, LEFT + 12.5, top - 0.5, 2, 1.5, body);
            box(g, unit, LEFT - 2, top + 4, 2, 2, body);
            box(g, unit, LEFT - 1.4, top - 4, 0.4, 8, new Color(150, 150, 160)); // pole
            Color red = new Color(200, 40, 50), blue = new Color(40, 60, 140);
            for (int s = 0; s < 5; s++) box(g, unit, LEFT - 1, top - 4 + s * 0.6, 4, 0.6, s % 2 == 0 ? red : Color.WHITE);
            box(g, unit, LEFT - 1, top - 4, 1.6, 1.8, blue);
        } else if (mood == Pet.Mood.STRETCH && pet.moodTime() < 1400) {
            // reaching up as high as he can
            box(g, unit, LEFT - 1, top - 3, 1, 3, body);
            box(g, unit, LEFT + 13, top - 3, 1, 3, body);
        } else if (mood == Pet.Mood.HAPPY || mood == Pet.Mood.RIDE || mood == Pet.Mood.PARTY
                || mood == Pet.Mood.CARPET || (mood == Pet.Mood.DUCKS && pet.moodTime() >= Pet.DUCK_SURPRISE + 400)) {
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
                || mood == Pet.Mood.PANT || mood == Pet.Mood.VIBE
                || mood == Pet.Mood.LISTEN || mood == Pet.Mood.FOCUS
                || (mood == Pet.Mood.STRETCH && pet.moodTime() < 1400)
                || mood == Pet.Mood.SORRY || made(pet, Creation.Effect.RAIN);
        for (int x : new int[] {3, 11}) {
            double eyeX = LEFT + x + ex;
            double eyeY = top + 2 + ey;
            if (pet.eyesShut()) {
                box(g, unit, eyeX, top + 3.5, 1, 0.5, EYE); // (shut where they were looking)
            } else if (mood == Pet.Mood.BLUSH) {
                // happy squinty eyes: little upside-down Vs
                box(g, unit, LEFT + x - 0.5, top + 3, 0.6, 0.6, EYE);
                box(g, unit, LEFT + x, top + 2.5, 0.6, 0.6, EYE);
                box(g, unit, LEFT + x + 0.5, top + 3, 0.6, 0.6, EYE);
            } else if (mood == Pet.Mood.BOOPED) {
                box(g, unit, LEFT + x + (x == 3 ? 1.2 : -1.2), eyeY, 1, 2, EYE); // cross-eyed
            } else if (mood == Pet.Mood.WOOZY) {
                // swirly eyes: each one a little two-bladed spinner going round and round (the two go opposite ways)
                double cx = LEFT + x + 0.5, cy = top + 3;
                double a = pet.time() / 110.0 * (x == 3 ? 1 : -1);
                for (int k = 0; k < 2; k++) {
                    double b = a + k * Math.PI;
                    box(g, unit, cx - 0.45 + Math.cos(b) * 0.75, cy - 0.45 + Math.sin(b) * 0.75, 0.9, 0.9, EYE);
                }
            } else if (pet.eyesLit()) {
                // excited: his eyes just go big and wide
                box(g, unit, eyeX - 0.3, eyeY - 0.5, 1.6, 2.8, EYE);
            } else if (mood == Pet.Mood.ANNOYED) {
                box(g, unit, eyeX, eyeY + 1, 1, 1, EYE);                              // narrowed eyes
                box(g, unit, eyeX + (x == 3 ? -0.5 : 0), eyeY - 0.5 + (x == 3 ? 0 : 0), 1.5, 0.5, EYE); // grumpy eyebrows
                box(g, unit, eyeX + (x == 3 ? 0.5 : -0.5), eyeY, 1, 0.5, EYE);
            } else if (mood == Pet.Mood.YELLED || mood == Pet.Mood.FREAKOUT || mood == Pet.Mood.SCARED
                    || (mood == Pet.Mood.DUCKS && pet.moodTime() >= Pet.DUCK_SURPRISE && pet.moodTime() < Pet.DUCK_SURPRISE + 400)) {
                box(g, unit, eyeX - 0.5, eyeY - 0.5, 2, 3, EYE); // eyes wide open in shock
                box(g, unit, eyeX, eyeY + 0.5, 1, 1, LIT);
            } else if (sleepy) {
                box(g, unit, eyeX, eyeY + 1, 1, 1, EYE);
            } else {
                box(g, unit, eyeX, eyeY, 1, 2, EYE);
            }
        }
        if (pet.jamming()) drawJamGear(g, pet, unit, top, mood);
        if (mood == Pet.Mood.WATCH || mood == Pet.Mood.SCARED) drawPopcorn(g, pet, unit, mood);
        else if (pet.coffee() && mood != Pet.Mood.JAM) drawCoffee(g, pet, unit);
        if (mood == Pet.Mood.PIANO) { // his instrument
            switch (pet.instrument()) {
                case GUITAR -> drawGuitar(g, pet, unit, top, false);
                case BASS -> drawGuitar(g, pet, unit, top, true);
                case DRUMS -> drawDrums(g, pet, unit, top);
                case VOICE -> drawSinging(g, pet, unit, top);
                default -> drawPiano(g, pet, unit, top);
            }
        }
        if (mood == Pet.Mood.LAP || mood == Pet.Mood.PANT) {
            // sweat: more and more drops the longer he runs (and still dripping as he pants)
            int drops = mood == Pet.Mood.PANT ? 4 : (int) Math.min(6, pet.moodTime() / 700);
            for (int i = 0; i < drops; i++) {
                double f = (pet.time() / 500.0 + i * 0.37) % 1;
                double side = i % 2 == 0 ? -1 : 1;
                box(g, unit, LEFT + 6.5 + side * (5 + f * 3), top - 0.5 + f * 2 + (i / 2) * 0.8, 0.6, 0.9, new Color(140, 200, 255, (int) (230 * (1 - f))));
            }
            if (mood == Pet.Mood.PANT && (pet.time() / 250) % 2 == 0) box(g, unit, LEFT + 6, top + 5, 1, 1.2, EYE); // mouth open, panting
        }
        if (pet.clockMs() >= 0 && (mood == Pet.Mood.IDLE || mood == Pet.Mood.SIT || mood == Pet.Mood.HAPPY || mood == Pet.Mood.LOVED
                || mood == Pet.Mood.BLUSH || mood == Pet.Mood.BOOPED || mood == Pet.Mood.HICCUP || mood == Pet.Mood.REMIND)) {
            drawClock(g, pet, unit, top);
        }
        if (mood == Pet.Mood.VIBE) {
            // music notes floating out of his headphones
            for (int i = 0; i < 3; i++) {
                double f = (pet.time() / 1300.0 + i / 3.0) % 1;
                double nx = (i % 2 == 0 ? LEFT - 1.5 - f * 2 : LEFT + 13.5 + f * 2), ny = top + 1 - f * 4;
                Color note = new Color(255, 214, 102, (int) (255 * (1 - f)));
                box(g, unit, nx, ny + 1.2, 0.7, 0.6, note);
                box(g, unit, nx + 0.5, ny, 0.25, 1.6, note);
            }
        }
        if (mood == Pet.Mood.FOCUS || mood == Pet.Mood.VIBE) {
            // tiny headphones: a band over his head and a cup on each side
            Color band = new Color(60, 60, 70), cup = new Color(90, 90, 104);
            box(g, unit, LEFT + 1, top - 1, 11, 0.6, band);
            box(g, unit, LEFT + 0.5, top - 0.6, 0.6, 1.8, band);
            box(g, unit, LEFT + 11.9, top - 0.6, 0.6, 1.8, band);
            box(g, unit, LEFT - 0.6, top + 1, 1.6, 2.4, cup);
            box(g, unit, LEFT + 12, top + 1, 1.6, 2.4, cup);
        }

        // Blushing: little pink cheeks
        if (mood == Pet.Mood.BLUSH) {
            Color pink = new Color(255, 130, 160, 200);
            box(g, unit, LEFT + 1.2, top + 5, 2, 1, pink);
            box(g, unit, LEFT + 10.8, top + 5, 2, 1, pink);
        }

        // Mouth: only while he beeps, flapping open and shut between his eyes
        if (pet.talking()) {
            if (pet.mouthOpen()) {
                box(g, unit, LEFT + 6, top + 4.5, 1, 1.5, EYE);
            } else {
                box(g, unit, LEFT + 5.5, top + 5, 2, 0.5, EYE);
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

        if (mood == Pet.Mood.WOOZY) drawBirds(g, unit, pet, top, false); // (the far side: behind his hat)
        if (!noHat && mood != Pet.Mood.CARRY) drawHat(g, unit, pet.hat(), top); // (no hat through the moving box)

        if (mood == Pet.Mood.WOOZY) drawBirds(g, unit, pet, top, true); // (the near side of their circle: in front of his hat)

        // A fly buzzing round him, and his hands clapping at it at the end
        double[] fly = pet.fly();
        if (fly != null) {
            fly = new double[] {fly[0], fly[1] + GROUND - 14}; // (its path is round his head)
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
        boolean standingStill = switch (mood) { // (the cake stays on the ground: not carried off on adventures)
            case IDLE, SIT, LIE, SLEEP, HAPPY, BIRTHDAY, LOVED, BLUSH, BOOPED, HICCUP, PARTY, DANCE, WAVE, SNEEZE, YELLED, ANNOYED, SAD -> true;
            default -> false;
        };
        if (pet.birthdayToday() && standingStill && !noHat && pet.tripping() < 0) {
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
                double bx = b == 0 ? LEFT - 3.8 : LEFT + 12.3; // (flaps and all, inside his square)
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
                for (int b = 0; b < rainbow.length; b++) box(g, unit, LEFT - 3 + i, GROUND - 0.6 + b * 0.2 + wave, 1, 0.2, rainbow[b]); // (all of it inside his square)
            }
            Color tassel = new Color(255, 214, 102);
            box(g, unit, LEFT - 3.6, GROUND - 0.2 + Math.sin(pet.time() / 110.0) * 0.25, 0.6, 0.4, tassel);
            box(g, unit, LEFT + 16, GROUND - 0.2 + Math.sin(pet.time() / 110.0 + 18 * 0.7) * 0.25, 0.6, 0.4, tassel);
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
                if (pet.droppedBall() && i == 2) { // the one he dropped: bouncing away on the ground
                    double f = Math.min(1, (pet.moodTime() - 2800) / 900.0);
                    box(g, unit, LEFT + 12 + f * 5, GROUND - 1 - Math.abs(Math.sin(f * Math.PI * 2)) * 2 * (1 - f), 1, 1, balls[i]);
                    continue;
                }
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
    /** Draws him turned round his middle by angle, with no shifting (running up walls and across the ceiling). */
    public static void drawSpun(Graphics2D g, Pet pet, int unit, double angle) {
        Graphics2D spun = (Graphics2D) g.create();
        spun.rotate(angle, WIDTH * unit / 2.0, HEIGHT * unit / 2.0);
        draw(spun, pet, unit);
        spun.dispose();
    }

    public static void drawTurned(Graphics2D g, Pet pet, int unit, double angle) {
        Graphics2D turned = (Graphics2D) g.create();
        if (angle != 0) {
            double drop = Math.max(0, 6 * (1 - Math.cos(angle)) / 2 - 3 * Math.abs(Math.sin(angle))); // so his head lands on the ground
            turned.translate(0, drop * unit);
            turned.rotate(angle, WIDTH * unit / 2.0, HEIGHT * unit / 2.0);
        }
        noHat = Math.abs(Math.IEEEremainder(angle, Math.PI * 2)) > Math.PI / 2;
        draw(turned, pet, unit);
        noHat = false;
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

    /**
     * His mini piano, turned to face him: we see its wooden back, low in front of him, with a little music stand on top
     * holding his sheet music (his eyes peek out either side of it). His hands pop up over the top as he plays.
     */
    private static void drawPiano(Graphics2D g, Pet pet, int unit, double top) {
        double px = LEFT - 0.5, w = 14;
        double out = Math.min(1, pet.moodTime() / (double) Pet.PIANO_INTRO); // it slides up into place
        double py = GROUND - 4.5 + (1 - out) * 4.5;
        Color wood = new Color(160, 82, 60), dark = new Color(118, 58, 42), light = new Color(190, 104, 78);
        Graphics2D c = (Graphics2D) g.create();
        c.clipRect(-WIDTH * unit, -HEIGHT * unit, WIDTH * unit * 3, (GROUND + HEIGHT) * unit);
        box(c, unit, px, py, w, 4.5, wood);                       // its back
        box(c, unit, px, py, w, 0.6, light);                      // the lid's edge
        box(c, unit, px + 1, py + 1.4, w - 2, 0.3, dark);         // panels
        box(c, unit, px + 1, py + 3, w - 2, 0.3, dark);
        box(c, unit, px + 0.6, py + 4.5, 0.8, 0.5, dark);         // little feet
        box(c, unit, px + w - 1.4, py + 4.5, 0.8, 0.5, dark);
        // the music stand, and his sheet music on it
        double sx = LEFT + 4.5, sy = py - 3.2;
        box(c, unit, sx + 1.8, sy + 2.6, 0.4, 0.7, dark);
        box(c, unit, sx - 0.3, sy + 2.4, 4.6, 0.4, dark);
        box(c, unit, sx, sy, 4, 2.6, new Color(250, 248, 240)); // the back of the page (the notes face him)
        box(c, unit, sx + 3.4, sy, 0.6, 2.6, new Color(228, 226, 218));
        // his hands, popping up over the top on each note
        double key = pet.pianoKey();
        if (!Double.isNaN(key) && pet.pianoPressing()) {
            double kx = px + 0.5 + key * (w - 3);
            box(c, unit, kx, py - 1, 2, 1.2, hand);
            box(c, unit, kx + 1.2, sy - 1.6, 0.7, 0.6, new Color(255, 214, 102)); // a note floats up
            box(c, unit, kx + 1.7, sy - 2.9, 0.25, 1.4, new Color(255, 214, 102));
        }
        c.dispose();
    }

    /** His guitar (or bass: bigger, darker, a longer neck), held across him, and his hand strumming on each note. */
    private static void drawGuitar(Graphics2D g, Pet pet, int unit, double top, boolean bass) {
        Color wood = bass ? new Color(40, 40, 48) : new Color(205, 135, 60), edge = bass ? new Color(170, 30, 40) : new Color(140, 85, 35);
        Color neck = new Color(110, 70, 40), fret = new Color(210, 200, 170);
        double bx = LEFT + (bass ? 4.5 : 6), by = top + 4.6; // (so the head of the neck stays in the window)
        // the neck, going up to his right
        double len = bass ? 9 : 7.5;
        for (double d = 0; d < len; d += 0.5) box(g, unit, bx + 3 + d, by + 0.6 - d * 0.55, 0.8, 0.8, neck);
        for (int f = 1; f < 5; f++) box(g, unit, bx + 3 + f * len / 5, by + 0.6 - f * len / 5 * 0.55, 0.25, 0.8, fret);
        box(g, unit, bx + 3 + len, by + 0.2 - len * 0.55, 1.4, 1.1, edge); // the head
        // the body
        box(g, unit, bx - 1, by - 0.6, 4.6, 3.6, edge);
        box(g, unit, bx - 0.6, by - 0.2, 3.8, 2.8, wood);
        box(g, unit, bx + 0.6, by + 0.6, 1.2, 1.2, new Color(30, 30, 34)); // the sound hole (or pickup)
        // his hand strumming (up on each note), the other on the neck
        boolean down = pet.pianoPressing();
        box(g, unit, bx + 1.2, by + (down ? 1.4 : 0) - 0.4, 1.5, 1.5, hand);
        box(g, unit, bx + 3 + len * 0.7, by - len * 0.7 * 0.55, 1.4, 1.4, hand);
        if (down) {
            box(g, unit, bx + 4, by - 3, 0.7, 0.6, new Color(255, 214, 102));
            box(g, unit, bx + 4.5, by - 4.3, 0.25, 1.4, new Color(255, 214, 102));
        }
    }

    /** His mug of coffee on the floor beside him, steaming (he's keeping your computer awake). */
    private static void drawCoffee(Graphics2D g, Pet pet, int unit) {
        Color edge = new Color(70, 60, 55), mug = new Color(215, 119, 87), band = new Color(250, 246, 238), coffee = new Color(90, 52, 30),
                steam = new Color(235, 235, 235, 200);
        double mx = LEFT - 4.6, my = GROUND - 3.6;
        box(g, unit, mx - 0.3, my - 0.3, 3.6, 3.9, edge); // (an outline, so it shows up on any background)
        box(g, unit, mx + 3.3, my + 0.5, 1.2, 2.0, edge);
        box(g, unit, mx, my, 3.0, 3.3, mug);             // his orange mug
        box(g, unit, mx, my + 1.4, 3.0, 0.6, band);      // with a white stripe
        box(g, unit, mx + 3.0, my + 0.8, 0.9, 0.4, mug); // the handle
        box(g, unit, mx + 3.5, my + 0.8, 0.4, 1.4, mug);
        box(g, unit, mx + 3.0, my + 1.8, 0.9, 0.4, mug);
        box(g, unit, mx + 0.3, my, 2.4, 0.5, coffee);    // the coffee in it
        long t = pet.time() / 280;
        for (int i = 0; i < 2; i++) { // two wisps of steam, drifting up
            double up = (t + i * 2) % 4;
            box(g, unit, mx + 0.6 + i * 1.2 + (up % 2 == 0 ? 0 : 0.4), my - 1.0 - up * 0.7, 0.5, 0.6, steam);
        }
    }

    /** His bucket of popcorn on the floor beside him (and, when he's scared, popcorn flying everywhere). */
    private static void drawPopcorn(Graphics2D g, Pet pet, int unit, Pet.Mood mood) {
        Color red = new Color(220, 50, 55), white = new Color(250, 246, 238), corn = new Color(255, 236, 170);
        double bx = LEFT - 4.4, by = GROUND - 3.2;
        for (int i = 0; i < 4; i++) box(g, unit, bx + i * 0.8, by, 0.8, 3.2, i % 2 == 0 ? red : white); // the striped bucket
        boolean spilled = mood == Pet.Mood.SCARED;
        if (!spilled) {
            box(g, unit, bx - 0.2, by - 0.8, 3.6, 0.9, corn); // heaped up on top
            box(g, unit, bx + 0.6, by - 1.3, 1.2, 0.6, corn);
        } else { // popcorn flying out all over
            double t = Math.min(1, pet.moodTime() / 1200.0);
            for (int i = 0; i < 12; i++) {
                double a = -Math.PI / 2 + (i - 2.5) * 0.22;
                double speed = 8 + (i * 7 % 5);
                double sideways = Math.cos(a) * speed * t * (Math.cos(a) < 0 ? 0.25 : 0.9); // (mostly up and over him: it stays in his square)
                double x = bx + 1.6 + sideways, y = by - 0.6 + Math.sin(a) * speed * t + 14 * t * t;
                if (y < GROUND - 0.6) box(g, unit, x, y, 0.6, 0.6, corn);
            }
        }
        if (pet.munching()) box(g, unit, LEFT + 6, top(pet) + 4.4, 0.6, 0.6, corn); // a bit at his mouth
    }

    private static double top(Pet pet) {
        return GROUND - 2 - 8 + 2; // (lying down, watching)
    }

    /** A jam session: his laptop on the floor beside him, a cord to whatever he's playing, REC while he records. */
    private static void drawJamGear(Graphics2D g, Pet pet, int unit, double top, Pet.Mood mood) {
        Color shell = new Color(70, 74, 84), screen = new Color(30, 34, 44), cord = new Color(40, 40, 46);
        double lx = LEFT - 4.6, ly = GROUND - 1;
        box(g, unit, lx, ly, 4.2, 0.6, shell);            // the bottom half
        box(g, unit, lx + 0.2, ly - 2.6, 3.8, 2.6, shell); // the lid, open
        box(g, unit, lx + 0.5, ly - 2.3, 3.2, 2, screen);
        boolean recording = mood == Pet.Mood.PIANO;
        int step = pet.jamStep();
        if (recording && (pet.time() / 400) % 2 == 0) box(g, unit, lx + 0.8, ly - 2, 0.7, 0.7, new Color(230, 50, 50)); // REC
        if (mood == Pet.Mood.JAM && step == Pet.JAM_PLAYING) { // the song playing: little bars bouncing
            for (int i = 0; i < 4; i++) {
                double h = 0.4 + Math.abs(Math.sin(pet.time() / (140.0 + i * 37) + i)) * 1.4;
                box(g, unit, lx + 0.8 + i * 0.7, ly - 0.4 - h, 0.5, h, new Color(120, 220, 160));
            }
        } else if (mood == Pet.Mood.JAM) { // code scrolling by as he types
            for (int i = 0; i < 3; i++) {
                double w = 1 + ((pet.time() / 250 + i * 3) % 4) * 0.5;
                box(g, unit, lx + 0.8, ly - 2 + i * 0.6, w, 0.3, new Color(120, 200, 255));
            }
        }
        if (recording) { // the cord, from the laptop to his instrument, sagging a little
            double x0 = lx + 4.2, y0 = ly + 0.2, x1 = LEFT + 4, y1 = top + 7.5;
            for (double t = 0; t <= 1; t += 0.08) {
                double x = x0 + (x1 - x0) * t, y = y0 + (y1 - y0) * t + Math.sin(t * Math.PI) * 0.8;
                box(g, unit, x, y, 0.35, 0.35, cord);
            }
        }
    }

    /** Singing: his mouth opens on each note, and little music notes float up. */
    private static void drawSinging(Graphics2D g, Pet pet, int unit, double top) {
        if (pet.pianoPressing()) box(g, unit, LEFT + 6, top + 4.6, 1, 1.4, EYE); // "la!"
        else box(g, unit, LEFT + 5.5, top + 5, 2, 0.5, EYE);
        for (int i = 0; i < 2; i++) {
            double f = (pet.time() / 1100.0 + i / 2.0) % 1;
            double nx = LEFT + 9 + f * 3 + i, ny = top + 1 - f * 4;
            Color note = new Color(255, 214, 102, (int) (255 * (1 - f)));
            box(g, unit, nx, ny + 1.2, 0.7, 0.6, note);
            box(g, unit, nx + 0.5, ny, 0.25, 1.6, note);
        }
    }

    /** His drum set in front of him: bass drum, snare, a tom, a hi-hat and a crash cymbal (they wobble when hit). */
    private static void drawDrums(Graphics2D g, Pet pet, int unit, double top) {
        Color shell = new Color(200, 40, 50), head = new Color(245, 240, 230), metal = new Color(230, 190, 70), stand = new Color(150, 150, 160);
        int note = pet.drumHit();
        boolean hit = pet.pianoPressing();
        // cymbals on stands, at the sides
        double crashTilt = hit && note == Piano.CRASH ? 0.6 : 0, hatTilt = hit && note == Piano.HAT ? 0.4 : 0;
        box(g, unit, LEFT - 2.6, top + 2.4, 0.3, GROUND - top - 2.4, stand);
        box(g, unit, LEFT - 4, top + 2 - hatTilt, 3, 0.5, metal);
        box(g, unit, LEFT - 4, top + 2.6, 3, 0.4, metal);
        box(g, unit, LEFT + 15.4, top + 0.6, 0.3, GROUND - top - 0.6, stand);
        box(g, unit, LEFT + 13.8, top + 0.2 - crashTilt, 3.6, 0.5, metal);
        // the bass drum, big, in the middle
        g.setColor(shell);
        g.fillOval((int) Math.round((LEFT + 3) * unit), (int) Math.round((GROUND - 6.2) * unit), 7 * unit, (int) Math.round(6.2 * unit));
        g.setColor(head);
        g.fillOval((int) Math.round((LEFT + 3.7) * unit), (int) Math.round((GROUND - 5.5) * unit), (int) Math.round(5.6 * unit), (int) Math.round(4.8 * unit));
        box(g, unit, LEFT + 5.3, GROUND - 3.6, 2.4, 1.2, body); // a little crab logo (just orange)
        box(g, unit, LEFT + 5.8, GROUND - 3.3, 0.4, 0.4, EYE);
        box(g, unit, LEFT + 6.8, GROUND - 3.3, 0.4, 0.4, EYE);
        if (hit && note == Piano.KICK) box(g, unit, LEFT + 3.4, GROUND - 6.8, 6.2, 0.3, new Color(255, 255, 255, 140)); // thump!
        // the snare (left) and a tom (right), on top
        box(g, unit, LEFT + 0.2, GROUND - 5.6 + (hit && note == Piano.SNARE ? 0.2 : 0), 3.2, 1.4, shell);
        box(g, unit, LEFT + 0.2, GROUND - 5.8 + (hit && note == Piano.SNARE ? 0.2 : 0), 3.2, 0.4, head);
        box(g, unit, LEFT + 9.6, GROUND - 6.4 + (hit && note == Piano.TOM ? 0.2 : 0), 2.8, 1.5, shell);
        box(g, unit, LEFT + 9.6, GROUND - 6.6 + (hit && note == Piano.TOM ? 0.2 : 0), 2.8, 0.4, head);
        // his hands with drumsticks, hitting whatever's playing
        double target = switch (note) {
            case Piano.HAT -> LEFT - 2.5;
            case Piano.SNARE -> LEFT + 1.8;
            case Piano.TOM -> LEFT + 11;
            case Piano.CRASH -> LEFT + 15.6;
            default -> LEFT + 6.5;
        };
        Color stick = new Color(235, 210, 160);
        double ly = top + (hit ? 3.6 : 2.4);
        box(g, unit, target - 1.6, ly, 1.3, 1.3, hand);
        box(g, unit, target - 0.4, ly + 1.2, 1.6, 0.35, stick);
        box(g, unit, LEFT + 8, top + 2.2, 1.3, 1.3, hand); // the other hand, waiting
        box(g, unit, LEFT + 9.2, top + 2.6, 1.6, 0.35, stick);
    }

    /** A MIDI file you dropped on him: he runs over to it, grabs it, and brings it back (a sheet of music). */
    private static final Color PAPER = new Color(250, 248, 240), INK = new Color(150, 150, 160);
    static final Color ZIP_YELLOW = new Color(246, 204, 92), ZIP_DARK = new Color(196, 150, 50);
    static final Color MANILA = new Color(236, 200, 120), MANILA_DARK = new Color(200, 160, 82);

    /** A file, 4 by 3 times k (top-left at x, y): a sheet of music ("song"), a zip (a yellow folder with a zipper), or a photo. */
    static void drawFile(Graphics2D g, int unit, String kind, double x, double y, double k) {
        switch (kind) {
            case "zip" -> {
                box(g, unit, x, y + 0.4 * k, 4 * k, 2.6 * k, ZIP_YELLOW);
                box(g, unit, x, y, 1.6 * k, 0.6 * k, ZIP_YELLOW);                    // its tab
                box(g, unit, x, y + 2.6 * k, 4 * k, 0.4 * k, ZIP_DARK);
                for (int i = 0; i < 5; i++) box(g, unit, x + (1.7 + (i % 2) * 0.3) * k, y + (0.5 + i * 0.4) * k, 0.3 * k, 0.4 * k, ZIPPER); // the zipper
            }
            case "picture" -> {
                box(g, unit, x, y, 4 * k, 3 * k, Color.WHITE);
                box(g, unit, x + 0.3 * k, y + 0.3 * k, 3.4 * k, 2.4 * k, new Color(140, 200, 245)); // sky
                box(g, unit, x + 0.3 * k, y + 1.8 * k, 3.4 * k, 0.9 * k, new Color(110, 180, 90));  // a hill
                box(g, unit, x + 2.6 * k, y + 0.6 * k, 0.7 * k, 0.7 * k, new Color(255, 220, 80));  // the sun
            }
            default -> {
                box(g, unit, x, y, 4 * k, 3 * k, PAPER);
                for (int l = 0; l < 3; l++) box(g, unit, x + 0.3 * k, y + (0.6 + l * 0.7) * k, 3.4 * k, 0.12 * k, INK);
                box(g, unit, x + 1 * k, y + 1.0 * k, 0.5 * k, 0.4 * k, ZIPPER);     // a couple of notes
                box(g, unit, x + 2.4 * k, y + 1.6 * k, 0.5 * k, 0.4 * k, ZIPPER);
            }
        }
    }

    private static final Color ZIPPER = new Color(70, 70, 80);

    /** Tearing a zip in two: holding it, pulling it apart (RRRIP), then flinging the halves out (the scraps fly). */
    static final long TEAR_FLING = 1300;

    private static void drawTear(Graphics2D g, Pet pet, int unit, double top) {
        long t = pet.moodTime();
        double cx = LEFT + 6.5, y = top + 3.6, half = 3.6, tall = 5;
        if (t < TEAR_FLING) {
            double apart = t < 450 ? 0 : ease((t - 450) / 800.0) * 4;   // how far the halves have come apart
            double shake = t < 450 ? Math.sin(t / 30.0) * 0.2 : 0;
            double lift = apart * 0.25;
            double lx = cx - half - apart / 2 + shake, rx = cx + apart / 2 + shake;
            // the left half and the right half (each with its half of the zipper), with a jagged torn edge
            box(g, unit, lx, y + lift, half, tall, ZIP_YELLOW);
            box(g, unit, rx, y - lift, half, tall, ZIP_YELLOW);
            box(g, unit, lx, y + lift - 0.8, 2.2, 0.9, ZIP_YELLOW);              // the tab
            box(g, unit, lx, y + lift + tall - 0.6, half, 0.6, ZIP_DARK);
            box(g, unit, rx, y - lift + tall - 0.6, half, 0.6, ZIP_DARK);
            for (int i = 0; i < 8; i++) {
                box(g, unit, cx - 0.6 - apart / 2 + shake, y + lift + 0.3 + i * 0.55, 0.6, 0.55, i % 2 == 0 ? ZIPPER : ZIP_YELLOW);
                box(g, unit, cx + apart / 2 + shake, y - lift + 0.3 + i * 0.55, 0.6, 0.55, i % 2 == 0 ? ZIP_YELLOW : ZIPPER);
            }
            if (apart > 0) {
                for (int i = 0; i < 5; i++) { // little bits flying out of the rip
                    double f = ((t + i * 170) % 500) / 500.0;
                    box(g, unit, cx - 0.3 + (i - 2) * f * 1.6, y + 1.5 - f * 4, 0.6, 0.6, i % 2 == 0 ? ZIP_YELLOW : PAPER);
                }
            }
            // his hands, gripping each half
            box(g, unit, lx - 1.2, y + 1.8 + lift, 1.4, 1.6, hand);
            box(g, unit, rx + half - 0.2, y + 1.8 - lift, 1.4, 1.6, hand);
        } else {
            // flung! arms up (the scraps are out on the desktop now)
            box(g, unit, LEFT - 2, top - 1, 1, 3, body);
            box(g, unit, LEFT - 1, top + 1, 1, 1, body);
            box(g, unit, LEFT + 14, top - 1, 1, 3, body);
            box(g, unit, LEFT + 13, top + 1, 1, 1, body);
        }
    }

    /** When filing a picture happens (ms into STASH): the folder comes out, opens, the picture goes in, it shuts, and away. */
    static final long STASH_OPEN = 500, STASH_IN = 900, STASH_SHUT = 1400, STASH_AWAY = 1500, STASH_GONE = 2200;

    private static void drawStash(Graphics2D g, Pet pet, int unit, double top) {
        long t = pet.moodTime();
        double base = top + 3.8, y = base, fw = 7.5, fh = 5;
        double fx = LEFT - 1.5;
        // pulled out (it grows into his hand), and tucked away again at the end (it shrinks away): all inside his square
        double grow = t < STASH_OPEN ? ease(t / (double) STASH_OPEN) : t < STASH_AWAY ? 1 : 1 - ease((t - STASH_AWAY) / (double) (STASH_GONE - STASH_AWAY));
        fw *= grow;
        fh *= grow;
        y += 5 * (1 - grow); // (growing up from his hand at the bottom)
        boolean open = t >= STASH_OPEN && t < STASH_SHUT;
        boolean folderOut = t < STASH_GONE && grow > 0.05;
        double in = t < STASH_IN ? 0 : t < STASH_SHUT ? ease((t - STASH_IN) / (double) (STASH_SHUT - STASH_IN - 100)) : 1;
        if (folderOut) {
            box(g, unit, fx, y, fw, fh, MANILA_DARK);                       // the back of the folder
            box(g, unit, fx, y - 0.8 * grow, 3 * grow, 0.9 * grow, MANILA_DARK); // its tab
        }
        if (in < 1) { // the picture, in his other hand: over to the folder, and down into it
            double px = LEFT + 8.5 + (fx + 0.9 - LEFT - 8.5) * Math.min(1, in * 2), py = base - 0.8 + Math.max(0, in * 2 - 1) * 3;
            drawFile(g, unit, "picture", px, py, 1.5);
            box(g, unit, px + 5.6, py + 1.6, 1.4, 1.6, hand);
        } else {
            box(g, unit, LEFT + 13, top + 4, 2, 2, body); // (hand free again)
        }
        if (folderOut) {
            if (open) box(g, unit, fx, y + 2.6, fw, fh - 2.6, MANILA);         // the front, folded down: open
            else box(g, unit, fx, y + 0.3, fw, fh - 0.3, MANILA);              // shut
            box(g, unit, fx, y + fh - 0.4, fw, 0.4, MANILA_DARK);
            box(g, unit, fx - 1.2, Math.min(y + 1.8, base + 3), 1.4, 1.6, hand); // holding the folder
        } else {
            box(g, unit, LEFT - 2, top + 4, 2, 2, body);
            if (t < STASH_GONE + 300) box(g, unit, LEFT - 2.5, top + 2.5, 0.8, 0.8, new Color(255, 255, 255, 200)); // (a little sparkle: done)
        }
    }

    /**
     * A timer or stopwatch: a tiny round clock in one hand, its hands going round, and a little digital display held
     * in front of him with the time in red alarm-clock digits.
     */
    private static void drawClock(Graphics2D g, Pet pet, int unit, double top) {
        long ms = pet.clockMs();
        // the round clock, in his left hand
        double cx = LEFT - 2.2, cy = top + 1.6, r = 2.1;
        Color rim = new Color(210, 60, 60), face = new Color(252, 250, 244), ink = new Color(40, 40, 46);
        g.setColor(rim);
        g.fillOval((int) Math.round((cx - r - 0.35) * unit), (int) Math.round((cy - r - 0.35) * unit), (int) Math.round((r + 0.35) * 2 * unit), (int) Math.round((r + 0.35) * 2 * unit));
        g.setColor(face);
        g.fillOval((int) Math.round((cx - r) * unit), (int) Math.round((cy - r) * unit), (int) Math.round(r * 2 * unit), (int) Math.round(r * 2 * unit));
        box(g, unit, cx - 1.3, cy - r - 1, 0.8, 0.6, rim); // its little bells
        box(g, unit, cx + 0.5, cy - r - 1, 0.8, 0.6, rim);
        java.awt.Stroke was = g.getStroke();
        g.setStroke(new java.awt.BasicStroke(Math.max(1, unit * 0.3f), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.setColor(ink);
        double seconds = (ms / 1000.0) % 60, minutes = (ms / 60000.0) % 60;
        double sa = Math.PI * 2 * seconds / 60 - Math.PI / 2, ma = Math.PI * 2 * minutes / 60 - Math.PI / 2;
        g.drawLine((int) (cx * unit), (int) (cy * unit), (int) ((cx + Math.cos(ma) * r * 0.55) * unit), (int) ((cy + Math.sin(ma) * r * 0.55) * unit));
        g.setColor(rim);
        g.drawLine((int) (cx * unit), (int) (cy * unit), (int) ((cx + Math.cos(sa) * r * 0.85) * unit), (int) ((cy + Math.sin(sa) * r * 0.85) * unit));
        g.setStroke(was);
        box(g, unit, LEFT - 2, top + 3.4, 1.4, 1.4, hand); // holding it
        // the digital display, held in front of him
        String text = Reminders.clock(ms);
        double dw = 1.3, dh = 2.1, gap = 0.35, colon = 0.6;
        double width = 0;
        for (char c : text.toCharArray()) width += c == ':' ? colon : dw + gap;
        double bx = LEFT + 6.5 - width / 2 - 0.5, by = top + 4.4;
        box(g, unit, bx - 0.4, by - 0.4, width + 1.3, dh + 0.8, new Color(30, 30, 34));
        box(g, unit, bx - 0.4, by - 0.4, width + 1.3, 0.25, new Color(70, 70, 78));
        Color lit = new Color(255, 50, 40), dim = new Color(70, 22, 22);
        double x = bx;
        for (char c : text.toCharArray()) {
            if (c == ':') {
                boolean blink = (pet.time() / 500) % 2 == 0;
                box(g, unit, x + 0.1, by + 0.5, 0.35, 0.35, blink ? lit : dim);
                box(g, unit, x + 0.1, by + 1.3, 0.35, 0.35, blink ? lit : dim);
                x += colon;
            } else {
                digit(g, unit, x, by, dw, dh, c - '0', lit, dim);
                x += dw + gap;
            }
        }
        box(g, unit, bx - 1, by + 0.6, 0.9, 1.2, hand);         // his hands on its ends
        box(g, unit, bx + width + 0.3, by + 0.6, 0.9, 1.2, hand);
    }

    /** One seven-segment digit ("8" style, like an alarm clock), the unlit segments faintly showing. */
    private static void digit(Graphics2D g, int unit, double x, double y, double w, double h, int d, Color on, Color off) {
        // segments: a top, b top right, c bottom right, d bottom, e bottom left, f top left, g middle
        int[] masks = {0b1111110, 0b0110000, 0b1101101, 0b1111001, 0b0110011, 0b1011011, 0b1011111, 0b1110000, 0b1111111, 0b1111011};
        int m = d >= 0 && d <= 9 ? masks[d] : 0;
        double t = Math.max(0.22, w * 0.22), half = h / 2;
        double[][] seg = {
                {x + t, y, w - 2 * t, t}, {x + w - t, y + t, t, half - 1.5 * t}, {x + w - t, y + half + t / 2, t, half - 1.5 * t},
                {x + t, y + h - t, w - 2 * t, t}, {x, y + half + t / 2, t, half - 1.5 * t}, {x, y + t, t, half - 1.5 * t},
                {x + t, y + half - t / 2, w - 2 * t, t}};
        for (int i = 0; i < 7; i++) {
            boolean lit = (m & (1 << (6 - i))) != 0;
            box(g, unit, seg[i][0], seg[i][1], seg[i][2], seg[i][3], lit ? on : off);
        }
    }

    /** His shirt: the bottom part of his body, in its colors (his arms are its sleeves). */
    private static void drawShirt(Graphics2D g, int unit, String shirt, double top, Pet.Mood mood) {
        if (shirt.isEmpty()) return;
        double y = top + 4.5, h = 3.5;
        Color base = switch (shirt) {
            case "tee-red" -> new Color(210, 50, 55);
            case "tee-star" -> new Color(60, 110, 210);
            case "stripes", "tuxedo" -> Color.WHITE;
            case "heart-tee" -> new Color(250, 245, 245);
            case "hoodie" -> new Color(120, 125, 135);
            case "jersey" -> new Color(40, 160, 90);
            case "hawaiian" -> new Color(40, 170, 200);
            case "army" -> new Color(95, 110, 60);
            default -> new Color(200, 200, 200);
        };
        box(g, unit, LEFT, y, 13, h, base);
        switch (shirt) {
            case "tee-star" -> {
                Color gold = new Color(255, 214, 102);
                box(g, unit, LEFT + 6, y + 0.6, 1, 2.4, gold);
                box(g, unit, LEFT + 5, y + 1.4, 3, 0.9, gold);
            }
            case "stripes" -> {
                for (int i = 0; i < 3; i++) box(g, unit, LEFT, y + 0.4 + i * 1.1, 13, 0.5, new Color(40, 70, 160));
            }
            case "heart-tee" -> {
                Color red = new Color(225, 50, 70);
                box(g, unit, LEFT + 5.2, y + 0.7, 1.1, 1, red);
                box(g, unit, LEFT + 6.7, y + 0.7, 1.1, 1, red);
                box(g, unit, LEFT + 5.2, y + 1.5, 2.6, 0.9, red);
                box(g, unit, LEFT + 5.9, y + 2.3, 1.2, 0.7, red);
            }
            case "hoodie" -> {
                Color dark = new Color(95, 100, 110);
                box(g, unit, LEFT + 3.5, y + 2, 6, 1.2, dark); // the front pocket
                box(g, unit, LEFT + 5.4, y + 0.1, 0.3, 1.4, Color.WHITE); // drawstrings
                box(g, unit, LEFT + 7.3, y + 0.1, 0.3, 1.4, Color.WHITE);
            }
            case "jersey" -> {
                box(g, unit, LEFT + 6, y + 0.6, 1, 2.4, Color.WHITE); // a big 1
                box(g, unit, LEFT + 5.3, y + 0.6, 0.8, 0.7, Color.WHITE);
                box(g, unit, LEFT, y, 13, 0.4, Color.WHITE);
            }
            case "hawaiian" -> {
                Color[] flowers = {new Color(255, 120, 170), new Color(255, 230, 100), Color.WHITE};
                for (int i = 0; i < 6; i++) box(g, unit, LEFT + 0.8 + i * 2.1, y + 0.6 + (i % 2) * 1.4, 1, 1, flowers[i % 3]);
            }
            case "tuxedo" -> {
                Color black = new Color(30, 30, 36);
                box(g, unit, LEFT, y, 4.5, h, black);
                box(g, unit, LEFT + 8.5, y, 4.5, h, black);
                box(g, unit, LEFT + 5.4, y + 0.1, 0.9, 0.7, black); // bow tie
                box(g, unit, LEFT + 6.7, y + 0.1, 0.9, 0.7, black);
                box(g, unit, LEFT + 6.2, y + 0.25, 0.6, 0.45, black);
                box(g, unit, LEFT + 6.3, y + 1.4, 0.4, 0.4, black); // buttons
                box(g, unit, LEFT + 6.3, y + 2.4, 0.4, 0.4, black);
            }
            case "army" -> {
                Color a = new Color(70, 85, 45), b = new Color(120, 110, 70);
                box(g, unit, LEFT + 1, y + 0.5, 2.5, 1, a);
                box(g, unit, LEFT + 7, y + 1.8, 3, 1, a);
                box(g, unit, LEFT + 4, y + 2.4, 2, 0.8, b);
                box(g, unit, LEFT + 10, y + 0.4, 2, 1, b);
                box(g, unit, LEFT + 2, y + 0.2, 1.4, 0.5, new Color(220, 190, 80)); // a little patch
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
        boolean tap = (pet.time() / (mood == Pet.Mood.DUCKS ? 55 : 130)) % 2 == 0; // ducks: clicking like mad
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
    private static long time; // for hats that move (the propeller), set when he's drawn

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
            case "bow" -> {
                Color red = new Color(225, 50, 70), dark = new Color(170, 30, 50);
                box(g, unit, mid + 1.5, top - 1.4, 1.6, 1.4, red);
                box(g, unit, mid + 3.6, top - 1.4, 1.6, 1.4, red);
                box(g, unit, mid + 3, top - 1, 0.7, 0.8, dark);
            }
            case "beanie" -> {
                Color knit = new Color(80, 150, 230), rib = new Color(55, 115, 190);
                box(g, unit, mid - 4, top - 2.2, 8, 2.2, knit);
                box(g, unit, mid - 3, top - 3, 6, 0.8, knit);
                box(g, unit, mid - 4, top - 0.8, 8, 0.8, rib);
                box(g, unit, mid - 0.7, top - 4.2, 1.4, 1.2, Color.WHITE); // pom-pom
            }
            case "cap" -> { // a baseball cap, backwards
                Color cap = new Color(220, 60, 60);
                box(g, unit, mid - 3.5, top - 2, 7, 2, cap);
                box(g, unit, mid - 6, top - 0.8, 2.6, 0.8, cap); // the brim, at the back
                box(g, unit, mid - 0.3, top - 2.4, 0.6, 0.4, Color.WHITE);
            }
            case "propeller" -> {
                Color a = new Color(255, 214, 102), b = new Color(80, 150, 230), c = new Color(220, 60, 60);
                box(g, unit, mid - 3.5, top - 1.8, 3.5, 1.8, a);
                box(g, unit, mid, top - 1.8, 3.5, 1.8, b);
                box(g, unit, mid - 0.2, top - 2.8, 0.4, 1, new Color(90, 90, 100));
                double spin = Math.abs(Math.sin(time / 60.0));
                box(g, unit, mid - 3 * spin, top - 3.1, 6 * spin + 0.3, 0.4, c); // spinning blades
            }
            case "flowers" -> {
                Color leaf = new Color(90, 170, 80);
                box(g, unit, mid - 4.5, top - 0.6, 9, 0.6, leaf);
                Color[] petals = {new Color(255, 140, 180), new Color(255, 230, 100), new Color(180, 140, 240), new Color(255, 255, 255)};
                for (int i = 0; i < 5; i++) {
                    box(g, unit, mid - 4.5 + i * 2, top - 1.4, 1.2, 1.2, petals[i % petals.length]);
                    box(g, unit, mid - 4.1 + i * 2, top - 1, 0.4, 0.4, new Color(255, 200, 60));
                }
            }
            case "chef" -> {
                box(g, unit, mid - 2.5, top - 1.2, 5, 1.2, Color.WHITE);
                box(g, unit, mid - 3, top - 4, 6, 2.8, Color.WHITE);
                box(g, unit, mid - 3.5, top - 4.6, 2.5, 1.6, Color.WHITE);
                box(g, unit, mid + 1, top - 4.6, 2.5, 1.6, Color.WHITE);
                box(g, unit, mid - 2.5, top - 1.4, 5, 0.25, new Color(220, 220, 228));
            }
            case "cowboy" -> {
                Color brown = new Color(150, 95, 50), band = new Color(90, 55, 30);
                box(g, unit, mid - 6, top - 0.9, 12, 0.9, brown);
                box(g, unit, mid - 6.5, top - 1.6, 1, 0.8, brown);
                box(g, unit, mid + 5.5, top - 1.6, 1, 0.8, brown);
                box(g, unit, mid - 3, top - 3.5, 6, 2.6, brown);
                box(g, unit, mid - 3, top - 1.6, 6, 0.6, band);
                box(g, unit, mid - 0.5, top - 3.6, 1, 0.5, band); // the dent on top
            }
            case "grad" -> {
                Color black = new Color(30, 30, 36), gold = new Color(255, 200, 60);
                box(g, unit, mid - 2.5, top - 1.5, 5, 1.5, black);
                box(g, unit, mid - 4.5, top - 2.3, 9, 0.8, black);
                box(g, unit, mid - 0.4, top - 2.6, 0.8, 0.4, gold);
                box(g, unit, mid + 3.2, top - 2.1, 0.3, 2.4, gold); // tassel
                box(g, unit, mid + 3, top + 0.2, 0.7, 0.7, gold);
            }
            case "wizard" -> {
                Color purple = new Color(110, 70, 190), star = new Color(255, 220, 90);
                box(g, unit, mid - 4.5, top - 0.8, 9, 0.8, purple);
                box(g, unit, mid - 3, top - 2.3, 6, 1.5, purple);
                box(g, unit, mid - 2, top - 3.8, 4, 1.5, purple);
                box(g, unit, mid - 1, top - 5.2, 2.5, 1.4, purple);
                box(g, unit, mid + 0.8, top - 6, 1.6, 0.9, purple); // the floppy tip
                box(g, unit, mid - 2, top - 2, 0.6, 0.6, star);
                box(g, unit, mid + 1, top - 3.4, 0.6, 0.6, star);
                box(g, unit, mid - 0.4, top - 4.8, 0.5, 0.5, star);
            }
            case "viking" -> {
                Color metal = new Color(170, 175, 185), horn = new Color(240, 230, 205), band = new Color(140, 100, 60);
                box(g, unit, mid - 3.5, top - 2.4, 7, 2.4, metal);
                box(g, unit, mid - 3.5, top - 0.7, 7, 0.7, band);
                box(g, unit, mid - 0.3, top - 2.4, 0.6, 2.4, band);
                box(g, unit, mid - 5, top - 2.2, 1.5, 1, horn); // horns
                box(g, unit, mid - 5.6, top - 3.6, 1, 1.6, horn);
                box(g, unit, mid + 3.5, top - 2.2, 1.5, 1, horn);
                box(g, unit, mid + 4.6, top - 3.6, 1, 1.6, horn);
            }
            case "army-cap" -> {
                Color olive = new Color(85, 100, 55), dark = new Color(60, 72, 38);
                box(g, unit, mid - 3.5, top - 2, 7, 2, olive);
                box(g, unit, mid - 3.5, top - 0.6, 7, 0.6, dark);
                box(g, unit, mid + 3, top - 0.7, 2.5, 0.7, dark); // the brim
                box(g, unit, mid - 0.5, top - 1.6, 1, 0.8, new Color(220, 190, 80)); // a little badge
            }
            case "nightcap" -> {
                // a floppy sleeping cap, drooping to one side, with a pom-pom
                Color blue = new Color(90, 120, 210), stripe = new Color(230, 235, 255);
                box(g, unit, mid - 3, top - 1, 6, 1, stripe);
                box(g, unit, mid - 2.5, top - 2, 5, 1, blue);
                box(g, unit, mid - 1, top - 3, 4, 1, blue);
                box(g, unit, mid + 1.5, top - 3.5, 2.5, 0.8, stripe);
                box(g, unit, mid + 3.5, top - 3.5, 1.2, 2, blue);
                box(g, unit, mid + 3.6, top - 1.8, 1, 1, Color.WHITE);
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

    /** How far below the middle of the drawing his feet are, in units (he turns round the middle). */
    public static double spinRadius() {
        return GROUND - HEIGHT / 2.0;
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
