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
            Map.entry("eleven", 11), Map.entry("twelve", 12), Map.entry("twenty", 20), Map.entry("thirty", 30), Map.entry("forty", 40),
            Map.entry("forty-five", 45), Map.entry("fifty", 50), Map.entry("sixty", 60));

    private static final String NUMBER = "(?:\\d+(?:\\.\\d+)?|an?|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen|twenty|thirty|forty|forty-five|fifty|sixty|half an?)";
    private static final String UNITS = "(?:seconds?|secs?|minutes?|mins?|hours?|hrs?)";
    /** How long: "10 minutes", "1 hour 30 minutes", "an hour and a half", "2 hours and 15 mins" (one group). */
    private static final String HOW_LONG = "(" + NUMBER + "[ -]" + UNITS + "(?:,? (?:and )?(?:a half|" + NUMBER + "[ -]" + UNITS + "))?)";
    private static final Pattern PART = Pattern.compile(NUMBER + "[ -]" + UNITS + "|a half");
    private static final Pattern IN_TO = Pattern.compile("^(?:please )?remind me (?:in|after) " + HOW_LONG + "(?: (?:to|about|that) (.+))?$");
    private static final Pattern TO_IN = Pattern.compile("^(?:please )?remind me (?:to|about) (.+?) (?:in|after) " + HOW_LONG + "$");
    private static final Pattern IN_FIRST = Pattern.compile("^in " + HOW_LONG + ",? remind me (?:to|about) (.+)$");
    private static final Pattern TIMER = Pattern.compile("^(?:(?:please )?(?:set|start|make)(?: me)? )?(?:a |an |the )?timer (?:for )?" + HOW_LONG + "$"
            + "|^(?:(?:please )?(?:set|start|make)(?: me)? )?(?:a |an )?" + HOW_LONG + " timer$");
    /** Sounds like a reminder or timer, but not one he understands (like "at 5pm" or "tomorrow"). */
    private static final Pattern SOUNDS_LIKE = Pattern.compile("^(?:please )?(?:remind me (?:to|about|at|in|tomorrow|tonight|on|every|later)\\b.*"
            + "|remind me\\b.*\\b(?:at|tomorrow|tonight|o'?clock|\\d+ ?(?:am|pm))\\b.*|(?:set|start|make)(?: me)? (?:a |an |the )?(?:\\w+ )*timer\\b.*|timer\\b.*)");

    /** Whether you were trying to set a reminder or timer he can't understand (then he says how to ask). */
    static boolean soundsLikeOne(String said) {
        String s = said.toLowerCase(Locale.ROOT).strip().replaceAll("[.!?]+$", "").replaceAll("\\s+", " ");
        return SOUNDS_LIKE.matcher(s).matches() && parse(said) == null;
    }

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
        return parse(said, java.time.LocalTime.now());
    }

    private static final String CLOCK = "(\\d{1,2})(?::(\\d{2}))? ?(am|pm|a\\.m|p\\.m)?|noon|midnight";
    private static final String WHEN = "(?: today| tonight| this morning| this afternoon| this evening| in the morning| in the afternoon| in the evening| at night)?";
    private static final Pattern AT_TO = Pattern.compile("^(?:please )?remind me (?:at|around) (" + CLOCK + ")" + WHEN + " (?:to|about|that) (.+)$");
    private static final Pattern TO_AT = Pattern.compile("^(?:please )?remind me (?:to|about) (.+?) (?:at|around) (" + CLOCK + ")" + WHEN + "$");

    static Reminder parse(String said, java.time.LocalTime now) {
        String s = said.toLowerCase(Locale.ROOT).strip().replaceAll("[.!?]+$", "").replaceAll("\\s+", " ");
        Matcher m;
        // "tonight", "this evening", "in the afternoon" (no am/pm said): the afternoon or evening one; "this morning": the morning one
        String part = s.matches(".*\\b(tonight|at night)\\b.*") ? "night" // ("12 tonight" is midnight, "1 at night" is 1 am)
                : s.matches(".*\\b(this afternoon|this evening|in the afternoon|in the evening)\\b.*") ? "pm"
                : s.matches(".*\\b(this morning|in the morning)\\b.*") ? "am" : null;
        if ((m = AT_TO.matcher(s)).matches()) return at(m.group(1), now, m.group(5), part);
        if ((m = TO_AT.matcher(s)).matches()) return at(m.group(2), now, m.group(1), part);
        if ((m = IN_TO.matcher(s)).matches()) return make(m.group(1), m.group(2) == null ? "this is your reminder" : m.group(2));
        if ((m = TO_IN.matcher(s)).matches()) return make(m.group(2), m.group(1));
        if ((m = IN_FIRST.matcher(s)).matches()) return make(m.group(1), m.group(2));
        if ((m = TIMER.matcher(s)).matches()) return make(m.group(1) != null ? m.group(1) : m.group(2), "time's up!");
        return null;
    }

    /** A reminder at a time of day ("3pm", "7:30", "noon"): the next time it's that time (a bare "3" is whichever 3 comes next). */
    private static Reminder at(String clock, java.time.LocalTime now, String what, String part) {
        String t = clock.strip();
        Matcher c = Pattern.compile(CLOCK).matcher(t);
        if (!c.matches()) return null;
        int hour, minute = 0;
        String half = null;
        if (t.equals("noon")) hour = 12;
        else if (t.equals("midnight")) hour = 0;
        else {
            hour = Integer.parseInt(c.group(1));
            if (c.group(2) != null) minute = Integer.parseInt(c.group(2));
            half = c.group(3);
            if (half == null && part != null && hour >= 1 && hour <= 12) half = !part.equals("night") ? part : hour == 12 || hour <= 4 ? "am" : "pm"; // ("9 tonight": 9 pm; "12 tonight": midnight)
            if (hour > 23 || minute > 59 || (half != null && (hour == 0 || hour > 12))) return null;
            if (half != null) hour = hour % 12 + (half.startsWith("p") ? 12 : 0);
        }
        long nowS = now.toSecondOfDay(), day = 24 * 3600;
        long wait = Math.floorMod(hour * 3600L + minute * 60L - nowS, day);
        if (half == null && c.group(1) != null && hour >= 1 && hour <= 12) { // "at 3": whichever 3 o'clock comes next, morning or afternoon
            long morning = Math.floorMod((hour % 12) * 3600L + minute * 60L - nowS, day);
            long afternoon = Math.floorMod((hour % 12 + 12) * 3600L + minute * 60L - nowS, day);
            wait = Math.min(morning, afternoon);
        }
        if (wait < 30) wait += day; // (it's that time right now: tomorrow, then)
        // (by the real clock: on the night the clocks change, "at 7" is still at 7, not an hour off)
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.LocalDateTime from = java.time.LocalDateTime.of(java.time.LocalDate.now(zone), now);
        long real = java.time.Duration.between(from.atZone(zone), from.plusSeconds(wait).atZone(zone)).getSeconds();
        if (real > 0) wait = real;
        return new Reminder(wait * 1000, Brain.noBadWords(you(what.strip())));
    }

    /** How long a phrase like "1 hour 30 minutes" is, in ms. */
    static long duration(String howLong) {
        double total = 0, lastUnit = 60_000;
        Matcher part = PART.matcher(howLong);
        while (part.find()) {
            String p = part.group();
            if (p.equals("a half")) { // "an hour and a half"
                total += lastUnit / 2;
                continue;
            }
            int space = Math.max(p.lastIndexOf(' '), p.lastIndexOf('-'));
            String number = p.substring(0, space), unit = p.substring(space + 1);
            if (number.startsWith("half")) { // "half an hour"
                number = "half";
                unit = p.substring(p.lastIndexOf(' ') + 1);
            }
            double n = number.equals("half") ? 0.5 : WORDS.containsKey(number) ? WORDS.get(number) : Double.parseDouble(number);
            lastUnit = unit.startsWith("s") ? 1000 : unit.startsWith("m") ? 60_000 : 3_600_000;
            total += n * lastUnit;
        }
        return (long) total;
    }

    private static Reminder make(String howLong, String what) {
        long ms;
        try {
            ms = duration(howLong);
        } catch (RuntimeException notANumber) {
            return null;
        }
        if (ms < 1000 || ms > 24L * 3_600_000) return null; // between a second and a day
        return new Reminder(ms, Brain.noBadWords(you(what.strip())));
    }

    /** What you said, as he'd say it back to you ("feed my cat" becomes "feed your cat"). */
    static String you(String s) {
        return (" " + s + " ").replace(" my ", " your ").replace(" me ", " you ").replace(" i'm ", " you're ").replace(" i ", " you ")
                .replace(" myself ", " yourself ").replace(" mine ", " yours ").strip();
    }
}
