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
        WAVE,
        /** Caps Lock went on: hands over his ears. */
        YELLED,
        /** Clicked too many times: grumpy eyebrows. */
        ANNOYED,
        /** Ah... ah... ACHOO! */
        SNEEZE,
        /** A fly buzzes round him: his eyes follow it, then he claps at it. */
        FLY,
        /** Spinning round (some of the things he codes do that). */
        SPIN,
        /** The cursor zoomed past so fast his head's spinning: swirly eyes and little birds going round his head. */
        WOOZY,
        /** A celebration: confetti! */
        PARTY,
        /** Your birthday: party hat, a cake, and a party blower he toots. */
        BIRTHDAY,
        /** Moving house: carrying a big box over his head. */
        CARRY,
        /** Just moved in: unpacking his boxes. */
        UNPACK,
        /** Done with his laptop: he stands up, folds it shut and puts it away. */
        PACK,
        /** Coding something of his own on his laptop (or deleting it, when it went wrong). */
        CODING,
        /** Riding the flying rainbow carpet he coded, all over the screen. */
        CARPET,
        /** Caught: something he made went wrong. Eyes down, hands together. */
        SORRY,
        /** Showing off something he coded (a disco ball, a rain cloud, a pizza...: see Creation). */
        MADE,
        /** A rocket he coded: it pulls up beside him, he climbs on, and counts down from 5. */
        LAUNCHPAD,
        /** Riding his rocket all over the screen (until it crashes). */
        ROCKET,
        /** duck.py: a duck fell from the sky! He loves it. He makes MORE. And more. (Click him to stop it.) */
        DUCKS,
        /** Thinking about your question: laptop out, typing away. */
        THINK,
        /** The cursor's been resting on him a while: he's all shy. Pink cheeks, happy squinty eyes. */
        BLUSH,
        /** The cursor swiped across his face: cross-eyed for a moment. Boop! */
        BOOPED,
        /** Hic! Hic! Hic! */
        HICCUP,
        /** Good morning! A great big stretch. */
        STRETCH,
        /** Playing his mini piano. */
        PIANO,
        /** Listening to you play your piano: eyes shut, swaying. */
        LISTEN,
        /** Veterans Day: a little flag, and a salute. */
        SALUTE,
        /** You dropped a MIDI file: he runs over, grabs it, and brings it back to play it. */
        FETCH,
        /** Focus timer: headphones on, sitting quietly, not bothering you. */
        FOCUS,
        /** A reminder you asked for: hopping up and down so you notice. */
        REMIND,
        /** Running a lap round the screen, as fast as he can (sweating more and more). */
        LAP,
        /** Done running: puffed out. */
        PANT,
        /** Music time: his own headphones on, bobbing to the beat. */
        VIBE
    }

    private boolean canJuggle, canWave;
    private boolean sneezed, clapped, caught;

    // Things he codes on his laptop (see Creation)
    private Creation coding;      // what he's coding right now
    private boolean deleting;     // ...or deleting, because it went wrong
    private Creation showing;     // what he made, out now (Sprite draws it)
    private Creation made;        // just made: the window does its part (a carpet ride, a rocket, a pop-up), once
    private Creation deleted;     // just deleted: the window throws its file away, once
    private Creation guilty;      // went wrong: he's sorry, and deletes it once he's home
    private boolean wantsToCreate; // feeling creative: the window picks something for him to make
    private int toots;
    private boolean sang, birthdayToday;
    private String birthdayLine = "Happy birthday!!";
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

    /** Clawd's options, as Pet needs them (the window passes in the real settings; tests can leave the defaults). */
    public interface Prefs {
        boolean on(String key);
        int number(String key);
        String choice(String key);
    }

    private Prefs prefs = new Prefs() {
        public boolean on(String key) {
            if (ALL_ANTICS && Options.find(key).start().equals("false") && Options.find(key).choices().isEmpty()) return true; // (tests: everything on)
            return Boolean.parseBoolean(Options.find(key).start());
        }

        public int number(String key) {
            return Integer.parseInt(Options.find(key).start());
        }

        public String choice(String key) {
            return Options.find(key).start();
        }
    };

    /** For the tests: every antic on (most of the sillier ones start off now), so each can be checked. */
    static final boolean ALL_ANTICS = Boolean.getBoolean("clawdtop.allAntics");

    public void setPrefs(Prefs p) {
        prefs = p;
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
    private long sinceUsed;        // ms since you last did anything with him (not just used the computer)

    /** You're doing something with him (the cursor's on him, you clicked him, he's riding...): no nodding off. */
    public void used() {
        sinceUsed = 0;
    }
    private long blinkIn;          // ms until the next blink
    private long blinking;         // ms left of a blink
    private long time;             // ms since he woke up, for breathing and bouncing
    private boolean devApp;        // a dev app is in front
    private float lookX, lookY;    // where his eyes point, -1 to 1 each way
    private Beep wants;            // a sound he wants to make (the window plays it)
    private long talking;          // ms left of moving his mouth along with a beep
    private long talkLength;       // how long this beep's mouth moving lasts

    /** A little sound he makes. */
    public enum Beep { HELLO, HAPPY, CLICKED, YAWN, WAKE, TIP, WHEE, OOF, PANIC, AWW, ACHOO, CLAP, HORN }

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
        long real = ms; // (songs and "fall asleep after" go by the real clock, whatever his speed)
        ms = switch (prefs.choice("speed")) { // how fast everything about him goes
            case "Slow" -> ms * 2 / 3;
            case "Fast" -> ms * 3 / 2;
            case "Zoomies" -> ms * 3;
            default -> ms;
        };
        if (!prefs.on("codingHappy")) devAppInFront = false;
        time += ms;
        moodFor += mood == Mood.PIANO ? real : ms;
        sinceUsed += real;

        // A dev app coming to the front makes him happy for a moment, and his eyes stay lit while it's there
        if (devAppInFront && !devApp) cheer();
        devApp = devAppInFront;

        // Moving the mouse near a sleeping or lying Clawd wakes him up
        double distance = Math.hypot(dx, dy);
        if ((mood == Mood.SLEEP || mood == Mood.LIE) && mouseMoved && distance < 120) {
            sinceUsed = 0; // (you came over to see him)
            if (mood == Mood.SLEEP) wants = Beep.WAKE;
            set(Mood.IDLE, idleTime());
        }

        switch (mood) {
            case RIDE, FALL, DIZZY, SHAKE, WALK, WORK, PEEK -> { } // his body or his job decides these (see follow and job)
            case PACK -> {
                if (moodFor > nextChange) {
                    if (sorryAfterPack && guilty != null) { // the laptop's shut: now he realizes what he's done
                        sorryAfterPack = false;
                        line = guilty.after();
                        wants = Beep.AWW;
                        set(Mood.SORRY, 2600);
                    } else {
                        doneCoding();
                    }
                }
            }
            case CODING -> {
                if (!deleting && !typo && moodFor > 6000 && prefs.on("mistakes") && random.nextInt(900) == 0) {
                    typo = true;
                    line = random.nextBoolean() ? "...oops, typo." : "Wait. That's not how you spell \"function\".";
                }
                // the big finish: hand way up... and SLAM the button. That's when it happens.
                if (slam() >= SLAM_HIT && slamBefore < SLAM_HIT) wants = Beep.CLAP;
                slamBefore = slam();
                if (moodFor > nextChange) {
                    slamBefore = -1;
                    doneCoding();
                }
            }
            case CARPET, ROCKET, THINK -> { }
            case BLUSH -> { if (!hovered && moodFor > nextChange) set(Mood.IDLE, idleTime()); }
            case BOOPED, STRETCH -> { if (moodFor > nextChange) set(Mood.IDLE, idleTime()); }
            case SALUTE -> { if (moodFor > nextChange) set(Mood.IDLE, idleTime()); }
            case FOCUS, LAP -> { }
            case PANT -> {
                if (moodFor > nextChange) {
                    line = "...I did it! Personal best!";
                    wants = Beep.HAPPY;
                    set(Mood.HAPPY, 900);
                }
            }
            case VIBE -> {
                if ((moodFor / 600) != ((moodFor - ms) / 600) && random.nextInt(3) == 0) wants = Beep.CLICKED;
                if (moodFor > nextChange) {
                    line = "That song slaps.";
                    set(Mood.IDLE, idleTime());
                }
            }
            case REMIND -> {
                if ((moodFor / 700) != ((moodFor - ms) / 700) && moodFor < 2800) wants = Beep.TIP;
                if (moodFor > nextChange) set(Mood.IDLE, idleTime());
            }
            case FETCH -> {
                if (moodFor > nextChange) {
                    Piano.Song next = fetched;
                    fetched = null;
                    set(Mood.IDLE, 0);
                    if (next != null) playPiano(next);
                }
            }
            case LISTEN -> {
                if (moodFor > nextChange) {
                    String[] bravo = {"Bravo!", "Encore! Encore!", "That was beautiful.", "You should go on tour.", "I got chills. Crab chills."};
                    line = bravo[random.nextInt(bravo.length)];
                    wants = Beep.CLAP;
                    set(Mood.HAPPY, 900);
                }
            }
            case PIANO -> {
                long into = moodFor - PIANO_INTRO;
                if (song != null && into >= 0) {
                    double beat = 0;
                    for (int i = 0; i < song.notes().length; i++) {
                        long at = (long) (beat * song.beatMs());
                        if (into >= at && songNote < i + 1) {
                            songNote = i + 1;
                            note = song.notes()[i];
                            if (i == mistakeAt) { // whoops, a wrong note
                                note = instrument == Piano.Instrument.DRUMS ? (note == Piano.CRASH ? Piano.TOM : Piano.CRASH) : note + (random.nextBoolean() ? 1 : -1);
                                String[] oops = {"Oops.", "...nobody heard that.", "That was jazz.", "I meant to do that."};
                                line = oops[random.nextInt(oops.length)];
                            }
                            noteMs = (int) (song.beats()[i] * song.beatMs());
                            noteAt = moodFor;
                        }
                        beat += song.beats()[i];
                    }
                }
                if (moodFor > nextChange) {
                    String[] thanks = {"Thank you, thank you!", "*bows*", "I've been practicing.", "That's all I know. For now."};
                    line = thanks[random.nextInt(thanks.length)];
                    wants = Beep.HAPPY;
                    song = null;
                    set(Mood.HAPPY, 900);
                }
            }
            case HICCUP -> {
                if ((moodFor / 1000) != ((moodFor - ms) / 1000) && moodFor < 3000) {
                    wants = Beep.CLICKED;
                    if (moodFor >= 2000) line = "...hic!";
                }
                if (moodFor > nextChange) {
                    line = "Okay. I think they're gone.";
                    set(Mood.IDLE, idleTime());
                }
            }
            case DUCKS -> {
                if (moodFor >= DUCK_SURPRISE + 400 && !sang) {
                    sang = true;
                    line = "A duck!!";
                    wants = Beep.HAPPY;
                }
                if (moodFor >= DUCK_SPAM && countdown == 0) {
                    countdown = 1;
                    line = "MORE DUCKS!";
                    wants = Beep.WHEE;
                }
                if (moodFor >= DUCK_SPAM && (moodFor / 300) != ((moodFor - ms) / 300)) wants = Beep.CLICKED; // click click click
                if (moodFor > DUCK_SPAM + 16_000) { // nobody stopped him. He stops himself, eventually
                    line = "...okay. That's enough ducks.";
                    stopDucks();
                }
            }
            case LAUNCHPAD -> {
                // climbs aboard, the door shuts... and a second later, off he goes
                if (moodFor >= Sprite.SHUT && countdown == 0) {
                    countdown = 1;
                    line = "Here we go...";
                    wants = Beep.TIP;
                }
                if (moodFor > nextChange) {
                    made = rocket; // the window launches it (and then he's riding it: see follow)
                    line = "LIFTOFF!!";
                    wants = Beep.WHEE;
                    nextChange = Long.MAX_VALUE;
                }
            }
            case SORRY -> {
                if (moodFor > nextChange) deleteIt();
            }
            case MADE -> {
                if (showing != null && showing.effect() == Creation.Effect.MUSIC && (moodFor / 700) != ((moodFor - ms) / 700)) {
                    wants = Beep.HAPPY; // his song, a beep at a time
                }
                if (moodFor > nextChange) {
                    Creation c = showing;
                    showing = null;
                    if (!c.after().isEmpty()) line = c.after();
                    if (c.oops()) {
                        guilty = c;
                        wants = Beep.AWW;
                        set(Mood.SORRY, 2200);
                    } else {
                        set(Mood.IDLE, idleTime());
                    }
                }
            }
            case GOODBYE -> { }
            case SAD, LOVED, JUGGLE, DANCE, WAVE, YELLED, ANNOYED, SPIN, WOOZY, PARTY -> {
                if (mood == Mood.JUGGLE && !dropped && moodFor > 2800 && moodFor - ms <= 2800 && prefs.on("mistakes") && random.nextInt(3) == 0) {
                    dropped = true; // whoops
                    line = "Whoops!";
                    wants = Beep.OOF;
                }
                if (moodFor > nextChange) set(Mood.IDLE, idleTime());
            }
            case CARRY -> { }
            case UNPACK -> {
                if (moodFor >= 2600 && !sang) {
                    sang = true;
                    line = "This place is nice!" + (home.equals("home") ? "" : "\n" + home + "... I like it here.");
                    wants = Beep.HAPPY;
                }
                if (moodFor > nextChange) set(Mood.IDLE, idleTime());
            }
            case BIRTHDAY -> {
                // three toots on the party blower, then the song line, then he carries on (still in his party hat)
                for (int k = 0; k < 3; k++) {
                    if (moodFor >= 600 + k * 900 && toots == k) {
                        toots++;
                        wants = Beep.HORN;
                    }
                }
                if (moodFor >= 3400 && !sang) {
                    sang = true;
                    line = birthdayLine;
                    wants = Beep.HAPPY;
                }
                if (moodFor > nextChange) set(Mood.IDLE, idleTime());
            }
            case SNEEZE -> {
                if (moodFor >= 700 && !sneezed) {
                    sneezed = true;
                    wants = Beep.ACHOO;
                    line = "ACHOO!";
                }
                if (moodFor > nextChange) set(Mood.IDLE, idleTime());
            }
            case FLY -> {
                if (moodFor >= 4200 && !clapped) {
                    clapped = true;
                    wants = Beep.CLAP;
                    caught = random.nextBoolean();
                    line = caught ? "Got it!" : "...it got away.";
                }
                if (moodFor > nextChange) set(Mood.IDLE, idleTime());
            }
            case FREAKOUT -> {
                if (moodFor > nextChange) {
                    set(Mood.IDLE, idleTime());
                    line = "...huh. Actually, I kinda like it.";
                    wants = Beep.HAPPY;
                }
            }
            case IDLE -> {
                if (focusing) { // (a reminder or something interrupted focus mode: back to it)
                    set(Mood.FOCUS, Long.MAX_VALUE);
                    break;
                }
                if (guilty != null && moodFor > 5000 && coding == null) {
                    line = guilty.after(); // something went wrong earlier: own up and delete it
                    wants = Beep.AWW;
                    set(Mood.SORRY, 2200);
                } else if (moodFor > 8000 && prefs.on("sneezes") && random.nextInt(12_000) == 0) {
                    sneezed = false;
                    line = "Ah... ah...";
                    set(Mood.SNEEZE, 1300);
                } else if (moodFor > Math.min(20_000, nextChange * 2 / 3) && prefs.on("piano") && random.nextInt(90_000) == 0) {
                    playPiano(random.nextInt(3) == 0 ? null : Piano.SONGS[random.nextInt(Piano.SONGS.length)]); // a little tune, just because
                } else if (moodFor > Math.min(15_000, nextChange / 2) && prefs.on("music") && random.nextInt(70_000) == 0) {
                    vibe(); // feeling the music
                } else if (moodFor > 10_000 && prefs.on("hiccups") && random.nextInt(60_000) == 0) {
                    line = "hic!";
                    wants = Beep.CLICKED;
                    set(Mood.HICCUP, 3600);
                } else if (moodFor > Math.min(20_000, nextChange * 2 / 3) && prefs.on("creates") && random.nextInt(70_000) == 0) {
                    wantsToCreate = true; // feeling creative: the window picks what
                } else if (moodFor > 8000 && prefs.on("flies") && random.nextInt(18_000) == 0) {
                    clapped = false;
                    set(Mood.FLY, 5200); // a fly!
                } else if (canJuggle && moodFor > 8000 && random.nextInt(6000) == 0) {
                    dropped = false;
                    set(Mood.JUGGLE, 4500); // bored: a little juggling
                } else if (canWave && moodFor > 6000 && random.nextInt(8000) == 0) {
                    wants = Beep.HELLO;
                    set(Mood.WAVE, 1400); // hi!
                } else if (personality == Personality.BOUNCY && moodFor > 4000 && random.nextInt(900) == 0) {
                    set(Mood.HAPPY, 700); // a little hop, just because
                } else if (moodFor > nextChange && prefs.on("naps")) {
                    set(Mood.SIT, (personality == Personality.SLEEPY ? 15_000 : 30_000) + random.nextInt(60_000));
                }
            }
            case SIT -> {
                if (moodFor > nextChange) set(sinceUsed > 60_000 ? Mood.LIE : Mood.IDLE, sinceUsed > 60_000 ? 60_000 + random.nextInt(120_000) : idleTime());
            }
            case LIE -> {
                if (sinceUsed > sleepAfter()) {
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
        if (tripFor > 0) tripFor -= ms;
        else if (mood == Mood.WALK && prefs.on("mistakes") && random.nextInt(1500) == 0) { // tripped over his own feet
            tripFor = 700;
            line = "Oof! I'm okay!";
            wants = Beep.OOF;
        }

        // A new color, a few seconds after it was picked: suddenly, with no warning. He freaks out.
        if (newColor != null && !busy() && (newColorIn -= ms) <= 0) {
            color = newColor;
            newColor = null;
            wants = Beep.PANIC;
            line = "WHAT?! WHAT HAPPENED TO ME?!";
            set(Mood.FREAKOUT, 3200);
        }

        // Blinking (not while asleep, his eyes are shut anyway)
        if (blinking > 0) {
            blinking -= ms;
        } else if ((blinkIn -= ms) <= 0 && prefs.on("blinks")) {
            blinking = 140;
            blinkIn = 2500 + random.nextInt(4500);
        }

        // Eyes follow the cursor (or a fly!), easing over so they don't jump
        float wantX = (float) Math.max(-1, Math.min(1, dx / 200));
        float wantY = (float) Math.max(-1, Math.min(1, dy / 200));
        double[] fly = fly();
        if (fly != null) {
            wantX = (float) Math.max(-1, Math.min(1, (fly[0] - 10.5) / 5));
            wantY = (float) Math.max(-1, Math.min(1, (fly[1] - 6) / 4));
        }
        if (mood == Mood.SLEEP || (!prefs.on("eyes") && fly == null)) wantX = wantY = 0;
        if (mood == Mood.DUCKS && moodFor < DUCK_SPAM) { // looking round at the duck behind him, up in the middle
            wantX = -1;
            wantY = -0.8f;
        }
        if (mood == Mood.LAUNCHPAD) { // looking at his rocket
            wantX = 1;
            wantY = -0.3f;
        }
        float ease = Math.min(1, ms / 120f);
        lookX += (wantX - lookX) * ease;
        lookY += (wantY - lookY) * ease;
    }

    /** He has a tip to tell you (a little chirp, and he perks up if he was lying down). */
    public void speak() {
        if (mood == Mood.SLEEP) return; // shh
        wants = Beep.TIP;
        if (mood == Mood.LIE || mood == Mood.SIT) set(Mood.IDLE, idleTime());
    }

    /** Keeps his mood in step with what his body is doing: riding, falling, dizzy, walking home, or back home. */
    public void follow(Body.State body) {
        if (mood == Mood.PACK && moodFor <= nextChange) return; // putting his laptop away first
        if (job != null && (body == Body.State.PERCH || body == Body.State.HOP_TO)) {
            if (mood == Mood.WORK && job != Mood.WORK) set(Mood.PACK, Sprite.PUT_AWAY);
            else if (mood != job) set(job, Long.MAX_VALUE);
            return;
        }
        if (mood == Mood.WORK) { // off the job: the laptop goes away before anything else
            set(Mood.PACK, Sprite.PUT_AWAY);
            return;
        }
        Mood want = switch (body) {
            case HOP_ON, RIDE -> Mood.RIDE;
            case FLY -> Mood.CARPET;
            case LAP, TACKLE -> Mood.LAP;
            case ROCKET -> Mood.ROCKET;
            case HOP_TO, PERCH -> Mood.IDLE;
            case FALL -> Mood.FALL;
            case DIZZY -> Mood.DIZZY;
            case SHAKE -> Mood.SHAKE;
            case WALK, AWAY, OUT -> mood == Mood.CARRY ? Mood.CARRY : Mood.WALK;
            case HOME -> null;
        };
        if (want == null || want == Mood.IDLE) {
            if (mood == Mood.CARRY) {
                moving(false); // home: time to unpack
                return;
            }
            if (mood == Mood.RIDE || mood == Mood.FALL || mood == Mood.DIZZY || mood == Mood.SHAKE || mood == Mood.WALK
                    || mood == Mood.PEEK || mood == Mood.CARPET || mood == Mood.LAP) {
                if (guilty != null && body == Body.State.HOME) {
                    line = guilty.after();
                    wants = Beep.AWW;
                    set(Mood.SORRY, 2200);
                } else {
                    if (mood == Mood.CARPET) line = "That was AWESOME.";
                    if (mood == Mood.LAP) {
                        line = "*pant* *pant* *pant*";
                        wants = Beep.OOF;
                        set(Mood.PANT, 2600);
                    } else {
                        set(Mood.IDLE, idleTime());
                    }
                }
            }
            return;
        }
        if (want == mood) return;
        if (mood == Mood.CODING || mood == Mood.PACK) { // picked up mid-code: never mind that
            coding = null;
            deleting = false;
        }
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
        if ((mood != Mood.IDLE && mood != Mood.SIT && mood != Mood.HAPPY) || !prefs.on("codingSad")) return;
        wants = Beep.AWW;
        set(Mood.SAD, 1600);
    }

    /** Rubbed with the mouse: little hearts float up. */
    public void petted() {
        if (mood == Mood.SLEEP || mood == Mood.LIE || busy() || !prefs.on("petHearts")) return;
        if (mood != Mood.LOVED) wants = Beep.HAPPY;
        set(Mood.LOVED, 1800);
    }

    /** Caps Lock went on (true) or off (false). */
    public void capsLock(boolean on) {
        if (mood == Mood.SLEEP || busy() || !prefs.on("capsLock")) return;
        if (on) {
            wants = Beep.PANIC;
            line = "WHY ARE WE YELLING?!";
            set(Mood.YELLED, 2500);
            yelledAt = true;
        } else if (yelledAt) {
            yelledAt = false;
            line = "...thank you.";
        }
    }

    private boolean yelledAt; // he said WHY ARE WE YELLING (so turning Caps Lock off gets a thank you)

    /** Clicked over and over: grumpy for a bit. */
    public void annoyed(String says) {
        wants = Beep.OOF;
        line = says;
        set(Mood.ANNOYED, 3000);
    }

    private long woozyAt = -1_000_000; // (when he last got woozy, by his clock: not again for a bit)

    /** The cursor zoomed right past him: his head's spinning (he stays put; his eyes swirl and birds go round). */
    public void spin() {
        if ((mood != Mood.IDLE && mood != Mood.SIT) || !prefs.on("spins")) return;
        if (time - woozyAt < 90_000) return; // (not again for a minute and a half)
        woozyAt = time;
        String[] whoa = {"Whoa...", "Woah, slow down...", "So... many... cursors...", "Tweet tweet..."};
        line = whoa[random.nextInt(whoa.length)];
        wants = Beep.WHEE;
        set(Mood.WOOZY, 2600);
    }

    /** Something to celebrate: confetti, a happy beep, and what he says. */
    public void party(String says) {
        line = says;
        if (busy()) return;
        wants = Beep.HAPPY;
        set(Mood.PARTY, 3500);
    }

    /** The battery's about to die: he panics, jumping about, until it's plugged in (or everything goes black). */
    public void batteryPanic(boolean on) {
        if (on) {
            wants = Beep.PANIC;
            line = "WHAT'S HAPPENING?! THE BATTERY'S CRITICALLY LOW!! PLUG ME IN!!";
            set(Mood.FREAKOUT, Long.MAX_VALUE);
        } else if (mood == Mood.FREAKOUT && nextChange == Long.MAX_VALUE) {
            line = "...phew. That was close.";
            set(Mood.IDLE, idleTime());
        }
    }

    private String home = "home";

    /** What he calls his home (this computer). */
    public void setHome(String name) {
        home = name;
    }

    /** Moving house: carrying boxes (true), or done and unpacking (false). */
    public void moving(boolean carrying) {
        if (carrying) {
            set(Mood.CARRY, Long.MAX_VALUE);
        } else {
            sang = false;
            set(Mood.UNPACK, 3800);
        }
    }

    /** Says something in his bubble (with a little chirp). */
    public void say(String says) {
        line = says;
        wants = Beep.TIP;
    }

    /** Where the fly is right now (in his pixels, from his drawing's top-left), while one buzzes round him. */
    public double[] fly() {
        if (mood != Mood.FLY) return null;
        double t = moodFor / 1000.0;
        if (moodFor >= 4200) return caught ? null : new double[] {10.5 + (moodFor - 4200) / 60.0, 1 - (moodFor - 4200) / 150.0};
        return new double[] {10.5 + Math.sin(t * 2.3) * 8 + Math.sin(t * 7.1) * 1.5, 2 + Math.cos(t * 3.1) * 2 + Math.sin(t * 9) * 0.8};
    }

    /** Your birthday surprise: party hat, cake, three toots on his party blower, and a happy birthday. */
    public void birthday(String name) {
        birthdayToday = true;
        toots = 0;
        sang = false;
        birthdayLine = "HAPPY BIRTHDAY" + (name.isEmpty() ? "" : ", " + name.toUpperCase(java.util.Locale.ROOT)) + "!!";
        set(Mood.BIRTHDAY, 7000);
    }

    /** Whether it's your birthday (he wears a party hat all day, and his cake sits beside him). */
    public void setBirthdayToday(boolean today) {
        birthdayToday = today;
    }

    public boolean birthdayToday() {
        return birthdayToday;
    }

    /** How far out his party blower is right now, 0 (rolled up) to 1 (all the way out). */
    public double blower() {
        if (mood != Mood.BIRTHDAY) return 0;
        for (int k = 0; k < 3; k++) {
            long t = moodFor - (600 + k * 900);
            if (t >= 0 && t < 600) return Math.sin(t / 600.0 * Math.PI);
        }
        return 0;
    }

    /** The last bit of coding: his hand goes way up (ms), then comes down on the button at SLAM_HIT of the way. */
    static final long SLAM_TIME = 800;
    static final double SLAM_HIT = 0.7;
    private double slamBefore = -1;

    /** How far through the button slam at the end of coding he is (0 to 1), or -1 if he isn't slamming it. */
    public double slam() {
        if (mood != Mood.CODING || nextChange - moodFor > SLAM_TIME) return -1;
        return Math.min(1, 1 - (nextChange - moodFor) / (double) SLAM_TIME);
    }

    /** How long he codes before it's done (ms): a while, and he won't say what he's doing. */
    static final long CODING_TIME = Long.getLong("clawdtop.codingMs", 18_000); // (the screen test codes faster)
    private static final int CODING_EXTRA = System.getProperty("clawdtop.codingMs") == null ? 10_000 : 1;

    /** Gets his laptop out and codes something. Only when he's not busy. Returns whether he started. */
    public boolean create(Creation c) {
        if ((mood != Mood.IDLE && mood != Mood.SIT && mood != Mood.HAPPY && mood != Mood.LOVED) || coding != null || guilty != null) return false;
        coding = c;
        deleting = false;
        typo = false;
        line = c.starting();
        set(Mood.CODING, CODING_TIME + random.nextInt(CODING_EXTRA));
        return true;
    }

    /** Whether he's coding something of his own right now (not deleting): ask him, and it's "Nothing....". */
    public boolean secretlyCoding() {
        return mood == Mood.CODING && !deleting;
    }

    /** What he's coding right now (null if nothing, or if he's deleting). */
    public Creation coding() {
        return mood == Mood.CODING && !deleting ? coding : null;
    }

    /** How far through coding it he is, 0 to 1 (the file fills in as he types). */
    public double codingProgress() {
        if (mood != Mood.CODING || deleting) return mood == Mood.PACK && !deleting && coding != null ? 1 : 0;
        return Math.min(1, Math.max(0, (moodFor - Sprite.SET_UP) / (double) Math.max(1, nextChange - Sprite.SET_UP - 500)));
    }

    /** What he made, out right now (for drawing), or null. */
    public Creation showing() {
        return mood == Mood.MADE ? showing : null;
    }

    /** Something he wants to make, now and then (once). */
    public boolean takeWantsToCreate() {
        boolean w = wantsToCreate;
        wantsToCreate = false;
        return w;
    }

    /** He's sorry: laptop out again, and the file goes. */
    private void deleteIt() {
        deleting = true;
        line = "rm " + guilty.file();
        set(Mood.CODING, 2600);
    }

    /** Laptop away: what he made comes out (or, after deleting something, he's glad it's gone). */
    private void doneCoding() {
        Creation c = coding;
        boolean wasDeleting = deleting;
        set(Mood.IDLE, idleTime()); // (this lets go of coding: we've got it here)
        coding = null;
        deleting = false;
        if (wasDeleting) {
            deleted = guilty;
            guilty = null;
            line = "There. It never happened.";
            return;
        }
        if (c == null) return;
        made = c;
        wants = c.oops() ? Beep.OOF : Beep.HAPPY;
        line = c.done();
        switch (c.effect()) {
            case CARPET -> wants = Beep.WHEE; // the window starts the ride
            case DUCKS -> {
                line = null;  // he hasn't seen it yet: it's falling behind him
                guilty = c;
                sang = false;
                countdown = 0;
                set(Mood.DUCKS, Long.MAX_VALUE);
            }
            case ROCKET -> {
                made = null;                  // not yet: first it pulls up and he climbs on
                guilty = c;                   // (he'll be sorry once he's back)
                rocket = c;
                countdown = 0;
                set(Mood.LAUNCHPAD, Sprite.SHUT + 1000);
            }
            case POPUP, NONE -> {
                // a pop-up says the done line itself (the window shows it); he just says the after line
                line = c.effect() == Creation.Effect.POPUP ? c.after() : c.done() + "\n" + c.after();
                if (c.oops()) {
                    guilty = c;
                    set(Mood.SORRY, 3600);
                }
            }
            case SPIN -> {
                line = c.done();
                set(Mood.SPIN, 700);
            }
            default -> {
                showing = c;
                set(Mood.MADE, Math.max(2500, c.showFor()));
            }
        }
    }

    /** Something he made that the window has to do its part for (a carpet ride, a rocket, a pop-up), once, or null. */
    public Creation takeMade() {
        Creation m = made;
        made = null;
        return m;
    }

    /** Something he just deleted (the window throws its file away), once, or null. */
    public Creation takeDeleted() {
        Creation d = deleted;
        deleted = null;
        return d;
    }

    /** The duck flood: when he spots the first one, and when he starts making more. */
    static final long DUCK_SURPRISE = 1300, DUCK_SPAM = 2700;
    private boolean sorryAfterPack;

    /** Whether he's making ducks as fast as he can click (the window drops one in now and then). */
    public boolean duckSpam() {
        return mood == Mood.DUCKS && moodFor >= DUCK_SPAM;
    }

    /** That's enough ducks: his laptop shuts, and then he's sorry. */
    public void stopDucks() {
        if (mood != Mood.DUCKS) return;
        sorryAfterPack = true;
        set(Mood.PACK, Sprite.PUT_AWAY);
    }

    private Creation rocket;
    private int countdown;
    private long boomAt = Long.MIN_VALUE;

    /** His rocket blew up: BOOM (and off he flies). */
    public void boom() {
        boomAt = time;
        line = "BOOM!";
        wants = Beep.OOF;
    }

    /** ms since his rocket blew up (for the explosion), or a big number. */
    public long boomAge() {
        return boomAt == Long.MIN_VALUE ? Long.MAX_VALUE : time - boomAt;
    }

    /** You clicked his carpet away mid-flight: down he goes, and he'll be sorry once he's home. */
    public void carpetGone(Creation carpet) {
        guilty = carpet;
        line = "WAIT-";
        wants = Beep.OOF;
    }

    /** Dances (a trick from the shop). */
    public void dance() {
        if (busy()) return;
        wants = Beep.WHEE;
        set(Mood.DANCE, 4000);
    }

    /** What he's learned and what he's wearing, from his shop items. */
    public void setItems(boolean juggling, boolean waving, String hat) {
        canJuggle = juggling;
        canWave = waving;
        this.hat = hat == null ? "" : hat;
    }

    private String shirt = "";

    public void setShirt(String id) {
        shirt = id == null ? "" : id;
    }

    /** The shirt he's wearing ("" for none). On Veterans Day: a mini army uniform. */
    public String shirt() {
        java.time.LocalDate d = java.time.LocalDate.now();
        if (d.getMonthValue() == 11 && d.getDayOfMonth() == 11) return "army";
        return shirt;
    }

    /** The hat he's wearing ("" for none, or a seasonal one if it's that time of year and he has none on). */
    public String hat() {
        if (birthdayToday) return "birthday";
        java.time.LocalDate today = java.time.LocalDate.now();
        if (today.getMonthValue() == 11 && today.getDayOfMonth() == 11) return "army-cap"; // Veterans Day
        if (!hat.isEmpty()) return hat;
        if (mood == Mood.SLEEP) { // a nightcap, at night
            int hour = java.time.LocalTime.now().getHour();
            if (hour >= 21 || hour < 6) return "nightcap";
        }
        if (!prefs.on("seasonalHats")) return "";
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
            sinceUsed = 0;
            return;
        }
        wants = Beep.CLICKED;
        if (!busy()) set(Mood.HAPPY, 1600);
    }

    /** Busy with something that shouldn't be cut short: a job, coding, a ride, a fall, moving house... */
    private boolean busy() {
        return switch (mood) {
            case WORK, PEEK, PACK, CODING, SORRY, MADE, CARPET, LAUNCHPAD, ROCKET, DUCKS, THINK, PIANO, FETCH, FOCUS, LAP, PANT, RIDE, FALL, DIZZY, SHAKE, WALK, CARRY, UNPACK, BIRTHDAY,
                    GOODBYE, FREAKOUT -> true;
            default -> false;
        };
    }

    /** How long he takes to get his piano out before the first note. */
    static final long PIANO_INTRO = 700;
    private Piano.Song song;
    private int songNote;
    private int note, noteMs;
    private long noteAt;

    /** Plays a song on his mini piano (null: one he makes up). Returns whether he started. */
    public boolean playPiano(Piano.Song which) {
        return play(Piano.Instrument.PIANO, which);
    }

    private Piano.Instrument instrument = Piano.Instrument.PIANO;
    private int mistakeAt = -1;   // the note he'll mess up in this song (-1: none)
    private long tripFor;         // tripping over his own feet (ms left)
    private boolean typo, dropped; // a typo while coding; dropped a ball juggling (once each)

    /** Whether he's tripping right now (walking home), and how far through it (0 to 1). */
    public double tripping() {
        return tripFor > 0 ? 1 - tripFor / 700.0 : -1;
    }

    /** Whether he dropped one of his juggling balls. */
    public boolean droppedBall() {
        return dropped;
    }

    /** What he's playing on. */
    public Piano.Instrument instrument() {
        return instrument;
    }

    /** Plays a song (or, on the drums, a beat; null: one he makes up) on one of his instruments. */
    public boolean play(Piano.Instrument on, Piano.Song which) {
        if (busy() || mood == Mood.SLEEP) return false;
        instrument = on;
        if (which == null && on == Piano.Instrument.DRUMS) which = Piano.BEATS[random.nextInt(Piano.BEATS.length)];
        song = which != null ? which : Piano.madeUp(random);
        // now and then he messes up one note (never the first or last)
        mistakeAt = song.notes().length > 4 && prefs.on("mistakes") && random.nextInt(3) == 0 ? 1 + random.nextInt(song.notes().length - 2) : -1;
        songNote = 0;
        note = 0;
        line = song.name().startsWith("your ") ? "Ahem. This one's called " + song.name().substring(5) + "."
                : on == Piano.Instrument.DRUMS ? (song.name().equals("ba-dum-tss") ? "" : "Here's " + song.name() + "! One, two, three, four!")
                : "Here's " + song.name() + (on == Piano.Instrument.PIANO ? "" : " on the " + on.shown) + "!";
        if (line.isEmpty()) line = null;
        set(Mood.PIANO, PIANO_INTRO + song.length() + 700);
        return true;
    }

    /** A piano note to play now (MIDI number), once, or 0; and how long it lasts (noteLength). */
    public int takeNote() {
        int n = note;
        note = 0;
        return n;
    }

    public int noteLength() {
        return noteMs;
    }

    /** Which key his hand is on (0 left to 1 right), and whether it's pressing it now; NaN when not playing. */
    public double pianoKey() {
        if (mood != Mood.PIANO || song == null || songNote == 0) return Double.NaN;
        return Piano.place(song.notes()[songNote - 1]);
    }

    /** The MIDI file he's playing right now (the whole thing plays, chords and all), or null. */
    public java.io.File playingMidi() {
        return mood == Mood.PIANO && song != null && song.midi() != null && moodFor >= PIANO_INTRO ? song.midi() : null;
    }

    /** The drum (or note) being hit right now, for drawing his drum set. */
    public int drumHit() {
        if (mood != Mood.PIANO || song == null || songNote == 0) return 0;
        return song.notes()[songNote - 1];
    }

    public boolean pianoPressing() {
        return mood == Mood.PIANO && moodFor - noteAt < 140;
    }

    /** Focus timer on (headphones on, quiet) or off ("Time for a break!" and a stretch, if it ran out by itself). */
    public boolean focus(boolean on, boolean finished) {
        if (on) {
            if (busy() && mood != Mood.FOCUS) return false; // in the middle of something
            focusing = true;
            line = "Focus mode! I'll be quiet.";
            set(Mood.FOCUS, Long.MAX_VALUE);
            return true;
        }
        if (!focusing) return false;
        focusing = false;
        if (finished) {
            line = "Time for a break! You did great.\nStand up, stretch, get some water.";
            wants = Beep.HAPPY;
            if (!busy() || mood == Mood.FOCUS) set(Mood.STRETCH, 1800);
        } else if (mood == Mood.FOCUS) {
            set(Mood.IDLE, idleTime());
        }
        return true;
    }

    private boolean focusing; // the focus timer's on (he goes back to it after anything that interrupts)

    /** Whether the focus timer's on. */
    public boolean focusing() {
        return focusing;
    }

    /** A reminder you asked for. Returns false if he's in the middle of something (it waits a moment). */
    public boolean remind(String what) {
        if (busy() && mood != Mood.FOCUS) return false;
        line = "Reminder: " + what + (what.matches(".*[.!?]$") ? "" : "!");
        wants = Beep.HORN;
        set(Mood.REMIND, 3500);
        return true;
    }

    private long clockMs = -1;   // what his little clock shows (ms), or -1 when he isn't holding it
    private boolean clockUp;     // a stopwatch (counting up) or a timer (counting down)

    /** His little clock and digital display: the time to show (ms), counting up or down; -1 puts it away. */
    public void clock(long ms, boolean countingUp) {
        clockMs = ms;
        clockUp = countingUp;
    }

    /** What his clock shows (ms), or -1 if he isn't holding one. */
    public long clockMs() {
        return clockMs;
    }

    public boolean clockUp() {
        return clockUp;
    }

    private Piano.Song fetched;

    /** Ooh, a file being dragged over him (it might be music!). */
    public void sniff() {
        if (busy() || mood == Mood.SLEEP || mood == Mood.HAPPY) return;
        line = "Ooh! Is that music?!";
        wants = Beep.HAPPY;
        set(Mood.HAPPY, 900); // a little excited hop
    }

    /** You dropped a MIDI file on him: he fetches it, then plays it. */
    public boolean fetch(Piano.Song song) {
        if (busy() || song == null) return false;
        fetched = song;
        line = "Music! For me?!";
        wants = Beep.WHEE;
        set(Mood.FETCH, 2300);
        return true;
    }

    /** Stops playing (you clicked him). */
    public void stopPiano() {
        if (mood != Mood.PIANO) return;
        song = null;
        line = "Okay, okay. I'll stop.";
        set(Mood.IDLE, idleTime());
    }

    /** You played a note on your piano: he listens, swaying, and says something nice when you stop. */
    public void listened() {
        if (mood != Mood.LISTEN && (busy() || mood == Mood.SLEEP)) return;
        set(Mood.LISTEN, 2500);
    }

    /** Music time: on go his headphones, and he bobs to the beat for a while. */
    public void vibe() {
        if (busy() || mood == Mood.SLEEP) return;
        String[] lines = {"Music time!", "This one's my jam.", "Headphones on. World off."};
        line = lines[random.nextInt(lines.length)];
        set(Mood.VIBE, 9000 + random.nextInt(6000));
    }

    /** Ready, set... a lap! (The window starts him running.) */
    public boolean lap() {
        if (busy() || mood == Mood.SLEEP) return false;
        line = "Ready... set... GO!";
        wants = Beep.WHEE;
        return true;
    }

    /** A holiday: he says something and shows it (fireworks, snow, a heart...). Returns whether he could right now. */
    public boolean celebrate(String says, Creation show) {
        if (busy() || mood == Mood.SLEEP) return false;
        line = says;
        wants = Beep.HAPPY;
        if (show.effect() == Creation.Effect.SPIN) {
            set(Mood.SPIN, 700);
        } else {
            showing = show;
            set(Mood.MADE, show.showFor());
        }
        return true;
    }

    /** Veterans Day. */
    public boolean salute() {
        if (busy() || mood == Mood.SLEEP) return false;
        line = "Happy Veterans Day.\nThank you to everyone who served.";
        set(Mood.SALUTE, 6000);
        return true;
    }

    private boolean hovered;
    private long hoverFor;

    /** Whether the cursor is resting on him. Long enough and he gets shy. */
    public void hover(boolean over, long ms) {
        hovered = over;
        hoverFor = over ? hoverFor + ms : 0;
        if (over && hoverFor > 1600 && (mood == Mood.IDLE || mood == Mood.SIT) && prefs.on("blush")) {
            if (random.nextInt(3) == 0) line = random.nextBoolean() ? "...hi." : "Oh! Um. Hello.";
            set(Mood.BLUSH, 900);
        }
    }

    /** The cursor swiped across his face. Boop! */
    public void booped() {
        if (mood != Mood.IDLE && mood != Mood.SIT && mood != Mood.BLUSH) return;
        if (!prefs.on("boop")) return;
        if (random.nextInt(3) == 0) line = "boop!";
        wants = Beep.CLICKED;
        set(Mood.BOOPED, 800);
    }

    /** The cursor's waiting beside him: a little hop with his arms up (ready? ready!) before he jumps on. */
    public void readyToHop() {
        if (mood != Mood.IDLE && mood != Mood.SIT && mood != Mood.LIE) return;
        set(Mood.HAPPY, 600);
    }

    /** Good morning: a great big stretch (the first time you're on the computer each morning). */
    public boolean morning(String name) {
        if (busy() || mood == Mood.SLEEP) return false;
        line = "Good morning" + (name.isEmpty() ? "" : ", " + name) + "!";
        wants = Beep.YAWN;
        set(Mood.STRETCH, 1800);
        return true;
    }

    /** Thinking about your question (laptop out), or done thinking (it goes away). */
    public void think(boolean on) {
        if (on && (mood == Mood.IDLE || mood == Mood.SIT || mood == Mood.LIE || mood == Mood.HAPPY || mood == Mood.LOVED)) {
            set(Mood.THINK, Long.MAX_VALUE);
        } else if (!on && mood == Mood.THINK) {
            set(Mood.PACK, Sprite.PUT_AWAY);
        }
    }

    /** Whether he's in the middle of something (a job, coding, a ride...) and shouldn't be interrupted. */
    public boolean busyNow() {
        return busy() || mood == Mood.SLEEP;
    }

    private void cheer() {
        if (busy()) return;
        wants = Beep.HAPPY;
        set(Mood.HAPPY, 2400);
    }

    private void set(Mood next, long howLong) {
        if (mood == Mood.PACK && next != Mood.PACK) sorryAfterPack = false; // (only right after the laptop shuts)
        if ((mood == Mood.CODING && next != Mood.CODING && next != Mood.PACK) || (mood == Mood.PACK && next != Mood.PACK)) {
            coding = null; // interrupted mid-code (or the laptop's away): that one's not happening
            deleting = false;
        }
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
        long chosen = prefs.number("sleepAfter") * 60_000L;
        return switch (personality) {
            case SLEEPY -> Math.max(60_000, chosen / 3);
            case BOUNCY -> chosen * 5 / 3;
            default -> chosen;
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
        if (!prefs.on("freakout")) { // no fuss: he's just a new color
            color = c;
            newColor = null;
            return;
        }
        newColor = c;
        newColorIn = 3500;
    }

    /** Whether he already has something to say (without taking it), for not talking over himself. */
    public String takeLineIfAny() {
        return line;
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
                sinceUsed = 0;
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
        if (mood == Mood.LISTEN || mood == Mood.PIANO) return 0;
        if (mood == Mood.REMIND) return moodFor < 2800 ? (float) Math.abs(Math.sin(moodFor / 110.0)) * 2.5f : 0; // hop hop hop
        if (mood == Mood.FOCUS || mood == Mood.LAP) return 0;
        if (mood == Mood.HICCUP) return moodFor % 1000 < 180 && moodFor < 3000 ? 1.5f : 0; // a little jump with each hic
        if (mood == Mood.STRETCH) return (float) Math.sin(Math.min(1, moodFor / 600.0) * Math.PI / 2) * (moodFor < 1400 ? 1.5f : 0);
        if (mood == Mood.FREAKOUT) return (float) Math.abs(Math.sin(time / 60.0)) * (nextChange == Long.MAX_VALUE ? 3 : 1.5f); // jumping about
        if (mood == Mood.DANCE) return (float) Math.abs(Math.sin(time / 180.0)) * 2;
        if (mood == Mood.WALK) return (time / 150) % 2 == 0 ? 0 : 0.5f; // a little bob with each step
        if (mood == Mood.RIDE || mood == Mood.FALL || mood == Mood.DIZZY || mood == Mood.WORK || mood == Mood.PEEK || mood == Mood.PACK) return 0;
        if (mood == Mood.SLEEP || mood == Mood.LIE) return 0;
        if (!prefs.on("breathing")) return 0;
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
        return talking > 0 && prefs.on("mouth");
    }

    /** While talking: whether his mouth is open (it flaps open and shut with the beeps; a yawn is one big open). */
    public boolean mouthOpen() {
        if (talking <= 0) return false;
        if (mood == Mood.SLEEP || mood == Mood.LIE) return talkLength == 600; // (only a yawn opens his mouth when he's dozy)
        return (talkLength - talking) / 70 % 2 == 0;
    }
}
