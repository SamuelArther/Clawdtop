package clawdtop;

import java.util.Random;

/**
 * What Clawd is doing and feeling, moment to moment. No drawing and no windows here, so it can be tested on its own:
 * the window calls {@link #tick} about 30 times a second with what's going on, and draws what this says.
 */
public final class Pet {
    /** What he's up to. */
    public enum Mood {
        /** Standing, breathing, blinking, watching your cursor. */
        IDLE,
        /** Sitting down for a bit. */
        SIT,
        /** Lying down, eyes half shut. */
        LIE,
        /** Fast asleep (nobody's moved the mouse in a while). */
        SLEEP,
        /** A dev app just came to the front, or you clicked him: he bounces with his arms up. */
        HAPPY,
        /** Riding your cursor, arms up. */
        RIDE,
        /** Shaken off, flying through the air head first. */
        FALL,
        /** Landed on his head: lying there upside down, seeing stars. */
        DIZZY,
        /** Back on his feet after landing on his head: shaking it off. */
        SHAKE,
        /** Walking home to his perch. */
        WALK,
        /** On a job, with his laptop out, typing away. */
        WORK,
        /** Hiding behind the top edge of a window: only his two little hands show. */
        PEEK,
        /** He just found out he's a new color: panicking, eyes wide, running on the spot. */
        FREAKOUT,
        /** Being uninstalled: "Well..... bye.....", and then he crumbles away to dust. */
        GOODBYE,
        /** A coding app was just closed: sad for a moment. */
        SAD,
        /** Petted: little hearts float up. */
        LOVED,
        /** A trick he learned: juggling three little balls. */
        JUGGLE,
        /** A trick he learned: dancing side to side. */
        DANCE,
        /** A trick he learned: waving at you. */
        WAVE
    }

    private boolean canJuggle, canWave;
    private String hat = "";

    static final long GOODBYE_PAUSE = 2800;  // standing there sadly after saying bye
    static final long CRUMBLE_TIME = 3200;   // crumbling away

    /** What he's like, picked when you meet him (or in the control panel). */
    public enum Personality {
        /** Easygoing: the usual amount of sitting, tips now and then. */
        CHILL,
        /** Can't sit still: hops around for joy, sits down less, falls asleep later. */
        BOUNCY,
        /** Loves giving tips: they come much more often. */
        HELPFUL,
        /** Always ready for a nap: sits, lies down and dozes off sooner. */
        SLEEPY;

        /** The name shown to you: "Chill", "Bouncy"... */
        public String shown() {
            return name().charAt(0) + name().substring(1).toLowerCase(java.util.Locale.ROOT);
        }

        public static Personality of(String name) {
            for (Personality p : values()) if (p.name().equalsIgnoreCase(name == null ? "" : name.strip())) return p;
            return CHILL;
        }

        /** How long between tips (ms). */
        public long tipGap() {
            return switch (this) {
                case HELPFUL -> 60_000;
                case BOUNCY -> 120_000;
                case SLEEPY -> 300_000;
                default -> 180_000;
            };
        }
    }

    private Personality personality = Personality.CHILL;
    private java.awt.Color color = new java.awt.Color(215, 119, 87);
    private java.awt.Color newColor;   // a color he's about to turn (a few seconds from now)
    private long newColorIn;
    private String line;               // something he wants to say in his bubble

    private final Random random;
    private Mood mood = Mood.IDLE;
    private long moodFor;          // how long he's been in this mood, in ms
    private long nextChange;       // when he'd like to do something else, in ms of the current mood
    private long sinceMouseMoved;  // ms
    private long blinkIn;          // ms until the next blink
    private long blinking;         // ms left of a blink
    private long time;             // ms since he woke up, for breathing and bouncing
    private boolean devApp;        // a dev app is in front
    private float lookX, lookY;    // where his eyes point, -1 to 1 each way
    private Beep wants;            // a sound he wants to make (the window plays it)
    private long talking;          // ms left of moving his mouth along with a beep
    private long talkLength;       // how long this beep's mouth moving lasts

    /** A little sound he makes. */
    public enum Beep { HELLO, HAPPY, CLICKED, YAWN, WAKE, TIP, WHEE, OOF, PANIC, AWW }

    public Pet(long seed) {
        random = new Random(seed);
        blinkIn = 2000 + random.nextInt(3000);
        nextChange = idleTime();
        wants = Beep.HELLO;
    }

    /**
     * Moves time on by ms. dx and dy are where the cursor is from his eyes, in pixels; mouseMoved says whether it moved
     * since last time; devAppInFront whether VS Code, a terminal or another dev app is the window in front.
     */
    public void tick(long ms, double dx, double dy, boolean mouseMoved, boolean devAppInFront) {
        time += ms;
        moodFor += ms;
        sinceMouseMoved = mouseMoved ? 0 : sinceMouseMoved + ms;

        // A dev app coming to the front makes him happy for a moment, and his eyes stay lit while it's there
        if (devAppInFront && !devApp) cheer();
        devApp = devAppInFront;

        // Moving the mouse near a sleeping or lying Clawd wakes him up
        double distance = Math.hypot(dx, dy);
        if ((mood == Mood.SLEEP || mood == Mood.LIE) && mouseMoved && distance < 120) {
            if (mood == Mood.SLEEP) wants = Beep.WAKE;
            set(Mood.IDLE, idleTime());
        }

        switch (mood) {
            case RIDE, FALL, DIZZY, SHAKE, WALK, WORK, PEEK -> { } // his body or his job decides these (see follow and job)
            case GOODBYE -> { }
            case SAD, LOVED, JUGGLE, DANCE, WAVE -> { if (moodFor > nextChange) set(Mood.IDLE, idleTime()); }
            case FREAKOUT -> {
                if (moodFor > nextChange) {
                    set(Mood.IDLE, idleTime());
                    line = "...huh. Actually, I kinda like it.";
                    wants = Beep.HAPPY;
                }
            }
            case IDLE -> {
                if (canJuggle && moodFor > 8000 && random.nextInt(2700) == 0) {
                    set(Mood.JUGGLE, 4500); // bored: a little juggling
                } else if (canWave && moodFor > 6000 && random.nextInt(3600) == 0) {
                    wants = Beep.HELLO;
                    set(Mood.WAVE, 1400); // hi!
                } else if (personality == Personality.BOUNCY && moodFor > 4000 && random.nextInt(900) == 0) {
                    set(Mood.HAPPY, 700); // a little hop, just because
                } else if (moodFor > nextChange) {
                    set(Mood.SIT, (personality == Personality.SLEEPY ? 15_000 : 30_000) + random.nextInt(60_000));
                }
            }
            case SIT -> {
                if (moodFor > nextChange) set(sinceMouseMoved > 20_000 ? Mood.LIE : Mood.IDLE, sinceMouseMoved > 20_000 ? 60_000 + random.nextInt(120_000) : idleTime());
            }
            case LIE -> {
                if (sinceMouseMoved > sleepAfter()) {
                    wants = Beep.YAWN;
                    set(Mood.SLEEP, Long.MAX_VALUE);
                } else if (moodFor > nextChange) {
                    set(Mood.IDLE, idleTime());
                }
            }
            case HAPPY -> { if (moodFor > nextChange) set(Mood.IDLE, idleTime()); }
            case SLEEP -> { }
        }

        if (talking > 0) talking -= ms;

        // A new color, a few seconds after it was picked: suddenly, with no warning. He freaks out.
        if (newColor != null && (newColorIn -= ms) <= 0) {
            color = newColor;
            newColor = null;
            wants = Beep.PANIC;
            line = "WHAT?! WHAT HAPPENED TO ME?!";
            set(Mood.FREAKOUT, 3200);
        }

        // Blinking (not while asleep, his eyes are shut anyway)
        if (blinking > 0) {
            blinking -= ms;
        } else if ((blinkIn -= ms) <= 0) {
            blinking = 140;
            blinkIn = 2500 + random.nextInt(4500);
        }

        // Eyes follow the cursor, easing over so they don't jump
        float wantX = (float) Math.max(-1, Math.min(1, dx / 200));
        float wantY = (float) Math.max(-1, Math.min(1, dy / 200));
        if (mood == Mood.SLEEP) wantX = wantY = 0;
        float ease = Math.min(1, ms / 120f);
        lookX += (wantX - lookX) * ease;
        lookY += (wantY - lookY) * ease;
    }

    /** He has a tip to tell you (a little chirp, and he perks up if he was lying down). */
    public void speak() {
        wants = Beep.TIP;
        if (mood == Mood.LIE || mood == Mood.SIT) set(Mood.IDLE, idleTime());
    }

    /** Keeps his mood in step with what his body is doing: riding, falling, dizzy, walking home, or back home. */
    public void follow(Body.State body) {
        if (job != null && (body == Body.State.PERCH || body == Body.State.HOP_TO)) {
            if (mood != job) set(job, Long.MAX_VALUE);
            return;
        }
        Mood want = switch (body) {
            case HOP_ON, RIDE -> Mood.RIDE;
            case HOP_TO, PERCH -> Mood.IDLE;
            case FALL -> Mood.FALL;
            case DIZZY -> Mood.DIZZY;
            case SHAKE -> Mood.SHAKE;
            case WALK -> Mood.WALK;
            case HOME -> null;
        };
        if (want == null || want == Mood.IDLE) {
            if (mood == Mood.RIDE || mood == Mood.FALL || mood == Mood.DIZZY || mood == Mood.SHAKE || mood == Mood.WALK
                    || mood == Mood.WORK || mood == Mood.PEEK) {
                set(Mood.IDLE, idleTime());
            }
            return;
        }
        if (want == mood) return;
        if (want == Mood.RIDE) wants = Beep.WHEE;
        if (want == Mood.DIZZY) wants = Beep.OOF;
        set(want, Long.MAX_VALUE);
    }

    private Mood job;

    /** On a job: WORK (laptop out), PEEK (just his hands), or null (just himself) while he sits on a window. */
    public void job(Mood mood) {
        job = mood;
    }

    /** A coding app was closed: he's sad for a moment (unless he's busy, asleep, or off somewhere). */
    public void sad() {
        if (mood != Mood.IDLE && mood != Mood.SIT && mood != Mood.HAPPY) return;
        wants = Beep.AWW;
        set(Mood.SAD, 1600);
    }

    /** Rubbed with the mouse: little hearts float up. */
    public void petted() {
        if (mood == Mood.SLEEP || mood == Mood.LIE) return;
        if (mood != Mood.LOVED) wants = Beep.HAPPY;
        set(Mood.LOVED, 1800);
    }

    /** Dances (a trick from the shop). */
    public void dance() {
        wants = Beep.WHEE;
        set(Mood.DANCE, 4000);
    }

    /** What he's learned and what he's wearing, from his shop items. */
    public void setItems(boolean juggling, boolean waving, String hat) {
        canJuggle = juggling;
        canWave = waving;
        this.hat = hat == null ? "" : hat;
    }

    /** The hat he's wearing ("" for none, or a seasonal one if it's that time of year and he has none on). */
    public String hat() {
        if (!hat.isEmpty()) return hat;
        java.time.LocalDate d = java.time.LocalDate.now();
        if (d.getMonthValue() == 12 && d.getDayOfMonth() >= 20 && d.getDayOfMonth() <= 26) return "santa";
        if (d.getMonthValue() == 10 && d.getDayOfMonth() == 31) return "pumpkin";
        return "";
    }

    /** You clicked him. Asleep (or dozing), a tap wakes him up with a little startled hop. */
    public void poke() {
        if (mood == Mood.SLEEP || mood == Mood.LIE) {
            wants = Beep.WAKE;
            set(Mood.HAPPY, 700);
            sinceMouseMoved = 0;
            return;
        }
        wants = Beep.CLICKED;
        set(Mood.HAPPY, 1600);
    }

    private void cheer() {
        wants = Beep.HAPPY;
        set(Mood.HAPPY, 2400);
    }

    private void set(Mood next, long howLong) {
        mood = next;
        moodFor = 0;
        nextChange = howLong;
    }

    private long idleTime() {
        return switch (personality) {
            case SLEEPY -> 10_000 + random.nextInt(15_000);
            case BOUNCY -> 30_000 + random.nextInt(50_000);
            default -> 20_000 + random.nextInt(40_000);
        };
    }

    /** How long with no mouse moving before he falls asleep (ms). */
    private long sleepAfter() {
        return switch (personality) {
            case SLEEPY -> 60_000;
            case BOUNCY -> 300_000;
            default -> 180_000;
        };
    }

    /** While he says goodbye: how far he's crumbled away, 0 (whole) to 1 (gone). */
    public double crumbled() {
        if (mood != Mood.GOODBYE) return 0;
        return Math.max(0, Math.min(1, (moodFor - GOODBYE_PAUSE) / (double) CRUMBLE_TIME));
    }

    /** Whether he's all gone (a moment after the last of him blows away). */
    public boolean gone() {
        return mood == Mood.GOODBYE && moodFor > GOODBYE_PAUSE + CRUMBLE_TIME + 600;
    }

    public Personality personality() {
        return personality;
    }

    public void setPersonality(Personality p) {
        personality = p;
    }

    /** His color right now. */
    public java.awt.Color color() {
        return color;
    }

    /** Sets his color straight away (when he starts up). */
    public void setColor(java.awt.Color c) {
        color = c;
        newColor = null;
    }

    /** Turns him a new color in a few seconds, suddenly, and he freaks out about it. */
    public void changeColor(java.awt.Color c) {
        if (c.equals(color)) {
            newColor = null;
            return;
        }
        newColor = c;
        newColorIn = 3500;
    }

    /** Something he wants to say in his bubble now, or null. Each line is only given out once. */
    public String takeLine() {
        String l = line;
        line = null;
        return l;
    }

    /** Puts him in a mood, asked from the control panel: "happy", "sleepy" (lies down), "asleep" or "awake". */
    public void ask(String what) {
        switch (what.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "happy" -> {
                wants = Beep.HAPPY;
                set(Mood.HAPPY, 2500);
            }
            case "sleepy" -> set(Mood.LIE, 60_000);
            case "asleep" -> {
                wants = Beep.YAWN;
                set(Mood.SLEEP, Long.MAX_VALUE);
            }
            case "goodbye" -> {
                line = "Well..... bye.....";
                set(Mood.GOODBYE, Long.MAX_VALUE);
            }
            case "awake" -> {
                wants = Beep.WAKE;
                sinceMouseMoved = 0;
                set(Mood.IDLE, idleTime());
            }
            default -> { }
        }
    }

    public Mood mood() {
        return mood;
    }

    /** Whether he's asleep or dozing (a tap only wakes him then; it doesn't open his menu). */
    public boolean sleepy() {
        return mood == Mood.SLEEP || mood == Mood.LIE;
    }

    /** Whether his eyes are shut right now (blinking, asleep, or squeezed shut shaking it off). */
    public boolean eyesShut() {
        return mood == Mood.SLEEP || mood == Mood.SHAKE || (blinking > 0 && mood != Mood.GOODBYE);
    }

    /** Whether his eyes are lit up: a dev app is in front, or he's happy. */
    public boolean eyesLit() {
        return devApp || mood == Mood.HAPPY;
    }

    public float lookX() {
        return lookX;
    }

    public float lookY() {
        return lookY;
    }

    /** How high he is off the ground right now, in his own pixels (bouncing when happy, breathing otherwise). */
    public float lift() {
        if (mood == Mood.HAPPY) return (float) Math.abs(Math.sin(time / 130.0)) * 3;
        if (mood == Mood.FREAKOUT) return (float) Math.abs(Math.sin(time / 60.0)) * 1.5f;
        if (mood == Mood.DANCE) return (float) Math.abs(Math.sin(time / 180.0)) * 2;
        if (mood == Mood.WALK) return (time / 150) % 2 == 0 ? 0 : 0.5f; // a little bob with each step
        if (mood == Mood.RIDE || mood == Mood.FALL || mood == Mood.DIZZY || mood == Mood.WORK || mood == Mood.PEEK) return 0;
        if (mood == Mood.SLEEP || mood == Mood.LIE) return 0;
        return (time / 900) % 2 == 0 ? 0 : 0.5f; // a slow breath
    }

    /** ms he's been in his current mood, for animations like pulling out his laptop. */
    public long moodTime() {
        return moodFor;
    }

    /** ms since he woke up, for little animations like the sleeping z's. */
    public long time() {
        return time;
    }

    /** A sound he'd like to make now, or null. Each one is only given out once. */
    public Beep takeBeep() {
        Beep b = wants;
        wants = null;
        if (b != null) talking = talkLength = b == Beep.YAWN ? 600 : 380; // his mouth moves while he beeps
        return b;
    }

    /** Whether he's beeping right now, so his mouth shows. */
    public boolean talking() {
        return talking > 0;
    }

    /** While talking: whether his mouth is open (it flaps open and shut with the beeps; a yawn is one big open). */
    public boolean mouthOpen() {
        if (talking <= 0) return false;
        if (mood == Mood.SLEEP || mood == Mood.LIE) return true;
        return (talkLength - talking) / 70 % 2 == 0;
    }
}
