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
        HAPPY
    }

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

    /** A little sound he makes. */
    public enum Beep { HELLO, HAPPY, CLICKED, YAWN, WAKE, TIP }

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
            case IDLE -> { if (moodFor > nextChange) set(Mood.SIT, 30_000 + random.nextInt(60_000)); }
            case SIT -> {
                if (moodFor > nextChange) set(sinceMouseMoved > 20_000 ? Mood.LIE : Mood.IDLE, sinceMouseMoved > 20_000 ? 60_000 + random.nextInt(120_000) : idleTime());
            }
            case LIE -> {
                if (sinceMouseMoved > 180_000) {
                    wants = Beep.YAWN;
                    set(Mood.SLEEP, Long.MAX_VALUE);
                } else if (moodFor > nextChange) {
                    set(Mood.IDLE, idleTime());
                }
            }
            case HAPPY -> { if (moodFor > nextChange) set(Mood.IDLE, idleTime()); }
            case SLEEP -> { }
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

    /** You clicked him. */
    public void poke() {
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
        return 20_000 + random.nextInt(40_000);
    }

    public Mood mood() {
        return mood;
    }

    /** Whether his eyes are shut right now (blinking or asleep). */
    public boolean eyesShut() {
        return mood == Mood.SLEEP || blinking > 0;
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
        if (mood == Mood.SLEEP || mood == Mood.LIE) return 0;
        return (time / 900) % 2 == 0 ? 0 : 0.5f; // a slow breath
    }

    /** ms since he woke up, for little animations like the sleeping z's. */
    public long time() {
        return time;
    }

    /** A sound he'd like to make now, or null. Each one is only given out once. */
    public Beep takeBeep() {
        Beep b = wants;
        wants = null;
        return b;
    }
}
