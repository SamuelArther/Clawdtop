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
            Option.on("Antics", "sneezes", "Sneezes", false),
            Option.on("Antics", "flies", "Flies buzzing round him", false),
            Option.on("Antics", "creates", "Codes funny little things on his laptop", true),
            Option.on("Antics", "hiccups", "Hiccups now and then", false),
            Option.on("Antics", "mistakes", "Little mistakes now and then (a wrong note, a trip, a typo...)", false),
            Option.on("Antics", "piano", "Plays his mini piano now and then", false),
            Option.on("Antics", "music", "Puts his headphones on and bobs to the beat now and then", false),
            Option.on("Antics", "blush", "Gets shy when the cursor rests on him", true),
            Option.on("Antics", "boop", "Goes cross-eyed when you swipe across his face (boop!)", false),
            Option.on("Antics", "morning", "A big good-morning stretch each day", true),
            Option.on("Antics", "jamSessions", "Now and then, a jam session all by himself (with your jam tracks)", true),
            Option.on("Antics", "cpDance", "Now and then, out of nowhere, the Club Penguin dance", true),
            Option.on("Antics", "tackle", "Tackles the taskbar icon of apps you open (it pops open when he hits it; Windows only)", false),
            Option.on("Antics", "bigSurprises", "Big surprises on his own (a rocket, a flying carpet, a duck flood, pop-ups)", false),
            Option.on("Antics", "spins", "Seeing birds when the cursor zooms past", false),
            Option.on("Antics", "naps", "Sitting, lying down and napping", true),
            Option.on("Antics", "eyes", "Eyes follow your cursor", true),
            Option.on("Antics", "blinks", "Blinking", true),
            Option.on("Antics", "breathing", "Breathing (a little bob)", true),
            Option.pick("Antics", "speed", "Animation speed", "Normal", "Slow", "Normal", "Fast", "Zoomies"),
            Option.number("Antics", "sleepAfter", "Fall asleep after (minutes of nobody playing with him)", 5, 1, 60),
            // What makes him react
            Option.on("Reactions", "codingHappy", "Happy when a coding app comes up", true),
            Option.on("Reactions", "codingSad", "Sad when a coding app closes", false),
            Option.on("Reactions", "capsLock", "Caps Lock: WHY ARE WE YELLING", false),
            Option.on("Reactions", "grumpy", "Grumpy when clicked too much", false),
            Option.on("Reactions", "stompOff", "Stomps off when clicked way too much", false),
            Option.on("Reactions", "battery", "Talks about the battery", true),
            Option.on("Reactions", "batteryPanic", "Panics when the battery's about to die", false),
            Option.on("Reactions", "freakout", "Freaks out about a new color", false),
            Option.on("Reactions", "games", "Says nice things about your games (he only looks on this computer)", true),
            Option.on("Reactions", "lateNight", "\"It's late... maybe bed soon?\"", false),
            Option.on("Reactions", "monday", "\"Ugh. Monday.\"", false),
            Option.on("Reactions", "friday", "Friday afternoon confetti", false),
            Option.on("Reactions", "friendship", "Friendship-day parties", false),
            Option.on("Reactions", "missedYou", "\"I missed you\" after a while away", false),
            Option.on("Reactions", "birthday", "Your birthday surprise", true),
            Option.on("Reactions", "seasonalHats", "Seasonal hats (Santa, pumpkin)", true),
            Option.on("Reactions", "holidays", "Holiday surprises (New Year's, Valentine's, Easter, Halloween, Christmas...)", true),
            // Riding and moving
            Option.on("Riding", "rides", "Hops onto your cursor", true),
            Option.on("Riding", "shakeOff", "Can be shaken off", false),
            Option.pick("Riding", "shakeHow", "How hard you have to shake", "Normal", "Gently", "Normal", "Really hard"),
            Option.number("Riding", "hopWait", "Wait before hopping on (tenths of a second)", 10, 1, 30),
            Option.pick("Riding", "walk", "Walking speed", "Normal", "Slow", "Normal", "Fast"),
            // His voice and bubble
            Option.pick("Voice", "voice", "Voice", "Normal", "Squeaky", "Normal", "Deep", "Robot", "Tiny"),
            Option.number("Voice", "volume", "Volume", 5, 1, 10),
            Option.pick("Voice", "pianoSound", "His piano sounds like", "Grand", Beeps.PIANO_SOUNDS),
            Option.pick("Voice", "guitarSound", "His guitar sounds like", "Normal", Beeps.GUITAR_SOUNDS),
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
            Option.on("Screen", "pointsTag", "Show his Clawd Points when you hover", false),
            Option.number("Screen", "nudge", "Nudge up or down (pixels)", 0, -40, 40),
            // His brain (Ask me a question, through Ollama on this PC)
            Option.on("Brain", "askMe", "\"Ask me a question\" in his menu", true),
            Option.on("Brain", "kidFriendly", "Kid-friendly answers, for little kids (normal is more accurate; no bad words either way)", false),
            Option.pick("Brain", "brain", "Brain size (all under 2 GB of memory)", "Normal", "Tiny", "Normal", "Chatty"),
            Option.on("Brain", "webSearch", "Look things up online when you ask him something (Wikipedia, DuckDuckGo)", false),
            // Being useful
            Option.on("Useful", "updates", "Checks for a newer version of him when he starts", true),
            Option.on("Privacy", "seeing", "Watches videos with you (looks at how bright the screen is; you're asked first)", false),
            Option.on("Privacy", "hearing", "Hears how loud your computer's sound is, to bop along (you're asked first)", false),
            Option.on("Useful", "downloads", "Tells you when a download finishes (with Open and Show buttons)", true),
            Option.on("Useful", "summary", "A quick rundown of your day the first time you log in each morning", true),
            Option.on("Useful", "eyeBreaks", "Eye breaks: every 20 minutes on the computer, look far away for 20 seconds", false),
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
