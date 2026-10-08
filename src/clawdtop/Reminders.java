package clawdtop;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Remind me in 10 minutes to check the oven", "set a timer for 5 minutes": reminders he understands by himself, no
 * brain needed.
 */
final class Reminders {
    private Reminders() {
    }

    /** A reminder: in how long (ms), and what to say. */
    record Reminder(long inMs, String what) {
        /** How long, in words ("10 minutes"). */
        String when() {
            long s = inMs / 1000;
            if (s < 60) return s + (s == 1 ? " second" : " seconds");
            long m = s / 60;
            if (m < 60) return m + (m == 1 ? " minute" : " minutes");
            long h = m / 60, left = m % 60;
            return h + (h == 1 ? " hour" : " hours") + (left > 0 ? " " + left + (left == 1 ? " minute" : " minutes") : "");
        }
    }

    private static final Map<String, Integer> WORDS = Map.ofEntries(Map.entry("a", 1), Map.entry("an", 1), Map.entry("one", 1),
            Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4), Map.entry("five", 5), Map.entry("six", 6),
            Map.entry("seven", 7), Map.entry("eight", 8), Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("fifteen", 15),
            Map.entry("twenty", 20), Map.entry("thirty", 30), Map.entry("forty", 40), Map.entry("forty-five", 45), Map.entry("sixty", 60));

    private static final String NUM = "(\\d+(?:\\.\\d+)?|a|an|one|two|three|four|five|six|seven|eight|nine|ten|fifteen|twenty|thirty|forty|forty-five|sixty|half an?)";
    private static final String UNIT = "(seconds?|secs?|minutes?|mins?|hours?|hrs?)";
    private static final Pattern IN_TO = Pattern.compile("^(?:please )?remind me (?:in|after) " + NUM + " " + UNIT + "(?: (?:to|about|that) (.+))?$");
    private static final Pattern TO_IN = Pattern.compile("^(?:please )?remind me (?:to|about) (.+?) (?:in|after) " + NUM + " " + UNIT + "$");
    private static final Pattern IN_FIRST = Pattern.compile("^in " + NUM + " " + UNIT + ",? remind me (?:to|about) (.+)$");
    private static final Pattern TIMER = Pattern.compile("^(?:set |start )?(?:a |an )?timer (?:for )?" + NUM + " " + UNIT + "$|^" + NUM + " " + UNIT + " timer$");

    /** "start a stopwatch": 1. "stop the stopwatch": -1. Anything else: 0. */
    static int stopwatch(String said) {
        String s = said.toLowerCase(Locale.ROOT).strip().replaceAll("[.!?]+$", "");
        if (s.matches("(start|begin|go|run)( a| the)? stopwatch|stopwatch|stopwatch (start|go)")) return 1;
        if (s.matches("(stop|end|pause|finish)( the| my)? stopwatch|stopwatch (stop|end)")) return -1;
        return 0;
    }

    /** A time as an alarm clock shows it: "4:59", or "1:02:03" past an hour. */
    static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        long h = s / 3600, m = s / 60 % 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }

    /** The reminder in what you typed, or null if it isn't one. */
    static Reminder parse(String said) {
        String s = said.toLowerCase(Locale.ROOT).strip().replaceAll("[.!?]+$", "").replaceAll("\\s+", " ");
        Matcher m;
        if ((m = IN_TO.matcher(s)).matches()) return make(m.group(1), m.group(2), m.group(3) == null ? "this is your reminder!" : m.group(3));
        if ((m = TO_IN.matcher(s)).matches()) return make(m.group(2), m.group(3), m.group(1));
        if ((m = IN_FIRST.matcher(s)).matches()) return make(m.group(1), m.group(2), m.group(3));
        if ((m = TIMER.matcher(s)).matches()) {
            return m.group(1) != null ? make(m.group(1), m.group(2), "time's up!") : make(m.group(3), m.group(4), "time's up!");
        }
        return null;
    }

    private static Reminder make(String number, String unit, String what) {
        double n;
        if (number.startsWith("half")) n = 0.5;
        else if (WORDS.containsKey(number)) n = WORDS.get(number);
        else n = Double.parseDouble(number);
        long ms = (long) (n * (unit.startsWith("s") ? 1000 : unit.startsWith("m") ? 60_000 : 3_600_000));
        if (ms < 1000 || ms > 24L * 3_600_000) return null; // between a second and a day
        return new Reminder(ms, you(what.strip()));
    }

    /** What you said, as he'd say it back to you ("feed my cat" becomes "feed your cat"). */
    static String you(String s) {
        return (" " + s + " ").replace(" my ", " your ").replace(" me ", " you ").replace(" i'm ", " you're ").replace(" i ", " you ")
                .replace(" myself ", " yourself ").replace(" mine ", " yours ").strip();
    }
}
