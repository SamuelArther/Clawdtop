package clawdtop;

import java.util.List;

/**
 * Every little switch and dial Clawd has, in one list: the control panel shows them all (there are a lot, on purpose),
 * and the code asks Settings.on(key), Settings.choice(key) or Settings.number(key) for each.
 */
final class Options {
    enum Kind { SWITCH, CHOICE, NUMBER }

    /** One option: its key in settings, what it's called, what kind, its starting value, and its choices or range. */
    record Option(String key, String label, Kind kind, String start, List<String> choices, int min, int max, String group) {
        static Option on(String group, String key, String label, boolean start) {
            return new Option(key, label, Kind.SWITCH, String.valueOf(start), List.of(), 0, 0, group);
        }

        static Option pick(String group, String key, String label, String start, String... choices) {
            return new Option(key, label, Kind.CHOICE, start, List.of(choices), 0, 0, group);
        }

        static Option number(String group, String key, String label, int start, int min, int max) {
            return new Option(key, label, Kind.NUMBER, String.valueOf(start), List.of(), min, max, group);
        }
    }

    static final List<Option> ALL = List.of(
            // One switch for serious people: no tomfoolery
            Option.on("Serious", "serious", "No tomfoolery (just a calm, helpful Clawd)", false),
            // What he does by himself
            Option.on("Antics", "sneezes", "Sneezes", true),
            Option.on("Antics", "flies", "Flies buzzing round him", true),
            Option.on("Antics", "creates", "Codes funny little things on his laptop", true),
            Option.on("Antics", "hiccups", "Hiccups now and then", true),
            Option.on("Antics", "piano", "Plays his mini piano now and then", true),
            Option.on("Antics", "music", "Puts his headphones on and bobs to the beat now and then", true),
            Option.on("Antics", "blush", "Gets shy when the cursor rests on him", true),
            Option.on("Antics", "boop", "Goes cross-eyed when you swipe across his face (boop!)", true),
            Option.on("Antics", "morning", "A big good-morning stretch each day", true),
            Option.on("Antics", "spins", "Spinning when the cursor zooms past", true),
            Option.on("Antics", "naps", "Sitting, lying down and napping", true),
            Option.on("Antics", "eyes", "Eyes follow your cursor", true),
            Option.on("Antics", "blinks", "Blinking", true),
            Option.on("Antics", "breathing", "Breathing (a little bob)", true),
            Option.pick("Antics", "speed", "Animation speed", "Normal", "Slow", "Normal", "Fast", "Zoomies"),
            Option.number("Antics", "sleepAfter", "Fall asleep after (minutes without the mouse)", 3, 1, 60),
            // What makes him react
            Option.on("Reactions", "codingHappy", "Happy when a coding app comes up", true),
            Option.on("Reactions", "codingSad", "Sad when a coding app closes", true),
            Option.on("Reactions", "capsLock", "Caps Lock: WHY ARE WE YELLING", true),
            Option.on("Reactions", "grumpy", "Grumpy when clicked too much", true),
            Option.on("Reactions", "stompOff", "Stomps off when clicked way too much", true),
            Option.on("Reactions", "battery", "Talks about the battery", true),
            Option.on("Reactions", "batteryPanic", "Panics when the battery's about to die", true),
            Option.on("Reactions", "freakout", "Freaks out about a new color", true),
            Option.on("Reactions", "games", "Says nice things about your games (he only looks on this PC)", true),
            Option.on("Reactions", "lateNight", "\"It's late... maybe bed soon?\"", true),
            Option.on("Reactions", "monday", "\"Ugh. Monday.\"", true),
            Option.on("Reactions", "friday", "Friday afternoon confetti", true),
            Option.on("Reactions", "friendship", "Friendship-day parties", true),
            Option.on("Reactions", "missedYou", "\"I missed you\" after a while away", true),
            Option.on("Reactions", "birthday", "Your birthday surprise", true),
            Option.on("Reactions", "seasonalHats", "Seasonal hats (Santa, pumpkin)", true),
            Option.on("Reactions", "holidays", "Holiday surprises (New Year's, Valentine's, Easter, Halloween, Christmas...)", true),
            // Riding and moving
            Option.on("Riding", "rides", "Hops onto your cursor", true),
            Option.on("Riding", "shakeOff", "Can be shaken off", true),
            Option.pick("Riding", "shakeHow", "How hard you have to shake", "Normal", "Gently", "Normal", "Really hard"),
            Option.number("Riding", "hopWait", "Wait before hopping on (tenths of a second)", 5, 1, 30),
            Option.pick("Riding", "walk", "Walking speed", "Normal", "Slow", "Normal", "Fast"),
            // His voice and bubble
            Option.pick("Voice", "voice", "Voice", "Normal", "Squeaky", "Normal", "Deep", "Robot", "Tiny"),
            Option.number("Voice", "volume", "Volume", 5, 1, 10),
            Option.on("Voice", "mouth", "Mouth moves when he talks", true),
            Option.pick("Voice", "font", "Bubble font", "Comic Sans", "Comic Sans", "Normal", "Typewriter"),
            Option.pick("Voice", "bubbleTime", "How long bubbles stay", "Normal", "Short", "Normal", "Long"),
            Option.on("Voice", "quietHours", "Quiet hours (no beeps)", false),
            Option.number("Voice", "quietFrom", "Quiet from (hour, 0 to 23)", 22, 0, 23),
            Option.number("Voice", "quietTo", "Quiet until (hour, 0 to 23)", 7, 0, 23),
            // On screen
            Option.on("Screen", "onTop", "Always on top", true),
            Option.on("Screen", "hideFullScreen", "Hide for full-screen videos (and games, if not in a corner)", true),
            Option.pick("Screen", "gameMode", "In full-screen games", "Sit in a corner", "Sit in a corner", "Hide"),
            Option.on("Screen", "nameTag", "His name when you hover over him", false),
            Option.on("Screen", "pointsTag", "Show his points when you hover", false),
            Option.number("Screen", "nudge", "Nudge up or down (pixels)", 0, -40, 40),
            // His brain (Ask me a question, through Ollama on this PC)
            Option.on("Brain", "askMe", "\"Ask me a question\" in his menu", true),
            Option.on("Brain", "kidFriendly", "Kid-friendly answers, for little kids (normal is more accurate; no bad words either way)", false),
            Option.pick("Brain", "brain", "Brain size (all under 2 GB of memory)", "Normal", "Tiny", "Normal", "Smart"),
            Option.on("Brain", "webSearch", "Look things up online when you ask him something (Wikipedia, DuckDuckGo)", false),
            // Being useful
            Option.on("Useful", "water", "Reminds you to drink water (every hour)", false),
            Option.on("Useful", "stretch", "Reminds you to stretch (every 2 hours)", false),
            Option.on("Useful", "restart", "Says when the computer hasn't restarted in a week", true),
            Option.on("Useful", "diskSpace", "Warns when a drive's nearly full", true),
            // Shop and points
            Option.on("Points", "earnPoints", "Earn Clawd Points", true),
            Option.on("Points", "petHearts", "Hearts when petted", true));

    private Options() {
    }

    static Option find(String key) {
        for (Option o : ALL) if (o.key().equals(key)) return o;
        throw new IllegalArgumentException("no option " + key);
    }
}
