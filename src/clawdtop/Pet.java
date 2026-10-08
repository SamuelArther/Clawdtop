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
        /** Walking home to his perch. */
        WALK,
        /** On a job, with his laptop out, typing away. */
        WORK,
        /** Hiding behind the top edge of a window: only his two little hands show. */
        PEEK
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
    private long talking;          // ms left of moving his mouth along with a beep
    private long talkLength;       // how long this beep's mouth moving lasts

    /** A little sound he makes. */
    public enum Beep { HELLO, HAPPY, CLICKED, YAWN, WAKE, TIP, WHEE, OOF }

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
            case RIDE, FALL, DIZZY, WALK, WORK, PEEK -> { } // his body or his job decides these (see follow and job)
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

        if (talking > 0) talking -= ms;

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
            case WALK -> Mood.WALK;
            case HOME -> null;
        };
        if (want == null || want == Mood.IDLE) {
            if (mood == Mood.RIDE || mood == Mood.FALL || mood == Mood.DIZZY || mood == Mood.WALK || mood == Mood.WORK || mood == Mood.PEEK) {
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
        return 20_000 + random.nextInt(40_000);
    }

    public Mood mood() {
        return mood;
    }

    /** Whether he's asleep or dozing (a tap only wakes him then; it doesn't open his menu). */
    public boolean sleepy() {
        return mood == Mood.SLEEP || mood == Mood.LIE;
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
