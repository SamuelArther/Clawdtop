package clawdtop;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Little useful things he works out himself, no brain needed: countdowns and unit conversions. */
final class Helpers {
    private Helpers() {
    }

    // ---- Countdowns: "how many days until Christmas?" ----

    private static final Pattern UNTIL = Pattern.compile("^(?:how many (?:days|sleeps) (?:until|till|til|to|before)|how long (?:until|till|til|before)|days (?:until|till|til|to))\\s+(?:it'?s\\s+)?(.+)$");
    private static final Map<String, String> MONTHS = Map.ofEntries(Map.entry("jan", "1"), Map.entry("feb", "2"), Map.entry("mar", "3"),
            Map.entry("apr", "4"), Map.entry("may", "5"), Map.entry("jun", "6"), Map.entry("jul", "7"), Map.entry("aug", "8"), Map.entry("sep", "9"),
            Map.entry("oct", "10"), Map.entry("nov", "11"), Map.entry("dec", "12"));

    /**
     * A birthday as you'd type it ("10/08", "10-8", "Oct 8", "8 October", "25/12": a first number over 12 must be the
     * day) as "MM-dd", or null if it isn't a real date.
     */
    static String birthday(String typed) {
        String t = typed.toLowerCase(Locale.ROOT).strip().replaceAll("(\\d)(st|nd|rd|th)\\b", "$1").replaceAll("[,]", " ").replaceAll("\\s+", " ");
        int month, day;
        try {
            Matcher words = Pattern.compile("^([a-z]{3,9})\\.? (\\d{1,2})$|^(\\d{1,2}) ([a-z]{3,9})\\.?$").matcher(t);
            if (words.matches()) {
                String m = (words.group(1) != null ? words.group(1) : words.group(4));
                String num = MONTHS.get(m.substring(0, 3));
                if (num == null) return null;
                month = Integer.parseInt(num);
                day = Integer.parseInt(words.group(2) != null ? words.group(2) : words.group(3));
            } else {
                Matcher nums = Pattern.compile("^(\\d{1,2})\\s*[/.\\- ]\\s*(\\d{1,2})(?:\\s*[/.\\- ]\\s*\\d{2,4})?$").matcher(t);
                if (!nums.matches()) return null;
                int a = Integer.parseInt(nums.group(1)), b = Integer.parseInt(nums.group(2));
                if (a > 12 && b <= 12) { // (25/12: day first)
                    month = b;
                    day = a;
                } else {
                    month = a;
                    day = b;
                }
            }
            MonthDay.of(month, day); // (a real date? 2/31 isn't)
            return String.format("%02d-%02d", month, day);
        } catch (RuntimeException notADate) {
            return null;
        }
    }

    /** "how many days until christmas" and the like: the answer, or null if it isn't one. birthday is "MM-dd" or "". */
    /** Days until your birthday ("MM-DD"), or -1 if he doesn't know it. */
    static long daysToBirthday(String birthday, LocalDate today) {
        if (birthday == null || !birthday.matches("\\d\\d-\\d\\d")) return -1;
        try {
            MonthDay md = MonthDay.parse("--" + birthday);
            return java.time.temporal.ChronoUnit.DAYS.between(today, next(today, md));
        } catch (RuntimeException notADate) {
            return -1;
        }
    }

    /**
     * The morning rundown: the date, then what's on your plate (to-dos, your sticky note, a birthday coming up).
     * Null if there's nothing but the date to say.
     */
    static String rundown(LocalDate today, java.util.List<String> todos, String sticky, long birthdayIn, String name) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (!todos.isEmpty()) lines.add(todos.size() == 1 ? "1 thing on your to-do list: " + todos.get(0) : todos.size() + " things on your to-do list (first up: " + todos.get(0) + ")");
        if (sticky != null && !sticky.isBlank()) lines.add("Your note says: " + sticky);
        if (birthdayIn > 0 && birthdayIn <= 14) lines.add(birthdayIn == 1 ? "Your birthday is TOMORROW!" : birthdayIn + " days till your birthday!");
        if (lines.isEmpty()) return null;
        String day = today.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + ", "
                + today.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + today.getDayOfMonth();
        return "Here's your day" + (name == null || name.isBlank() ? "" : ", " + name) + "! It's " + day + ".\n- " + String.join("\n- ", lines);
    }

    static String countdown(String question, String birthday, LocalDate today) {
        String q = question.toLowerCase(Locale.ROOT).strip().replaceAll("[?!.]+$", "");
        Matcher m = UNTIL.matcher(q);
        if (!m.matches()) return null;
        String what = m.group(1).replaceAll("^(the |my |our )", "").strip();
        String name;
        LocalDate day;
        switch (what.replace("'", "")) {
            case "christmas", "xmas", "christmas day" -> { name = "Christmas"; day = next(today, MonthDay.of(12, 25)); }
            case "christmas eve" -> { name = "Christmas Eve"; day = next(today, MonthDay.of(12, 24)); }
            case "halloween" -> { name = "Halloween"; day = next(today, MonthDay.of(10, 31)); }
            case "new year", "new years", "new years day" -> { name = "New Year's Day"; day = next(today, MonthDay.of(1, 1)); }
            case "valentines", "valentines day" -> { name = "Valentine's Day"; day = next(today, MonthDay.of(2, 14)); }
            case "veterans day" -> { name = "Veterans Day"; day = next(today, MonthDay.of(11, 11)); }
            case "fourth of july", "4th of july", "independence day" -> { name = "the Fourth of July"; day = next(today, MonthDay.of(7, 4)); }
            case "summer" -> { name = "summer"; day = next(today, MonthDay.of(6, 21)); }
            case "thanksgiving" -> {
                name = "Thanksgiving";
                LocalDate t = thanksgiving(today.getYear());
                day = t.isBefore(today) ? thanksgiving(today.getYear() + 1) : t;
            }
            case "easter" -> {
                name = "Easter";
                LocalDate e = Holidays.easter(today.getYear());
                day = e.isBefore(today) ? Holidays.easter(today.getYear() + 1) : e;
            }
            case "birthday" -> {
                if (birthday == null || birthday.isBlank()) return "I don't know your birthday yet!\nTell me like this: \"my birthday is October 8\"";
                name = "your birthday";
                day = next(today, MonthDay.parse("--" + birthday));
            }
            default -> {
                day = date(what, today);
                if (day == null) return null;
                name = day.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + day.getDayOfMonth();
            }
        }
        long days = ChronoUnit.DAYS.between(today, day);
        if (days == 0) return "It's " + name + " TODAY!";
        if (days == 1) return "Just 1 more day until " + name + "! (Tomorrow!)";
        return days + " days until " + name + "!" + (days <= 7 ? " Almost!" : days > 300 ? " ...a while." : "");
    }

    private static LocalDate next(LocalDate today, MonthDay md) {
        LocalDate d = md.atYear(today.getYear());
        return d.isBefore(today) ? md.atYear(today.getYear() + 1) : d;
    }

    private static LocalDate thanksgiving(int year) {
        return LocalDate.of(year, 11, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY));
    }

    /** "march 3", "3/14", "december 25th": the next such day (today counts), or null. */
    static LocalDate date(String text, LocalDate today) {
        Matcher m = Pattern.compile("^([a-z]+)\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?$").matcher(text);
        try {
            if (m.matches() && MONTHS.containsKey(m.group(1).substring(0, Math.min(3, m.group(1).length())))) {
                return next(today, MonthDay.of(Integer.parseInt(MONTHS.get(m.group(1).substring(0, 3))), Integer.parseInt(m.group(2))));
            }
            m = Pattern.compile("^(\\d{1,2})/(\\d{1,2})$").matcher(text);
            if (m.matches()) return next(today, MonthDay.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
        } catch (RuntimeException notADate) {
            return null;
        }
        return null;
    }

    // ---- Conversions: "how many cm in 5 inches", "70 f in c" ----

    private record Unit(String kind, double factor) {
    }

    private static final Map<String, Unit> UNITS = Map.ofEntries(
            u("mm", "length", 0.001), u("millimeter", "length", 0.001), u("cm", "length", 0.01), u("centimeter", "length", 0.01),
            u("m", "length", 1), u("meter", "length", 1), u("metre", "length", 1), u("km", "length", 1000), u("kilometer", "length", 1000),
            u("in", "length", 0.0254), u("inch", "length", 0.0254), u("ft", "length", 0.3048), u("foot", "length", 0.3048), u("feet", "length", 0.3048),
            u("yd", "length", 0.9144), u("yard", "length", 0.9144), u("mi", "length", 1609.344), u("mile", "length", 1609.344),
            u("g", "mass", 0.001), u("gram", "mass", 0.001), u("kg", "mass", 1), u("kilogram", "mass", 1), u("kilo", "mass", 1),
            u("oz", "mass", 0.028349523125), u("ounce", "mass", 0.028349523125), u("lb", "mass", 0.45359237), u("pound", "mass", 0.45359237),
            u("ml", "volume", 0.001), u("milliliter", "volume", 0.001), u("l", "volume", 1), u("liter", "volume", 1), u("litre", "volume", 1),
            u("cup", "volume", 0.2365882365), u("pint", "volume", 0.473176473), u("quart", "volume", 0.946352946), u("gallon", "volume", 3.785411784),
            u("tablespoon", "volume", 0.0147867648), u("tbsp", "volume", 0.0147867648), u("teaspoon", "volume", 0.00492892159), u("tsp", "volume", 0.00492892159),
            u("second", "time", 1), u("sec", "time", 1), u("minute", "time", 60), u("min", "time", 60), u("hour", "time", 3600), u("hr", "time", 3600),
            u("day", "time", 86400), u("week", "time", 604800), u("year", "time", 31_557_600),
            u("c", "temp", 0), u("celsius", "temp", 0), u("f", "temp", 0), u("fahrenheit", "temp", 0), u("k", "temp", 0), u("kelvin", "temp", 0));

    private static Map.Entry<String, Unit> u(String name, String kind, double factor) {
        return Map.entry(name, new Unit(kind, factor));
    }

    private static final String NUM = "(-?\\d+(?:\\.\\d+)?)";
    private static final Pattern HOW_MANY = Pattern.compile("^how many ([a-z]+(?: [a-z]+)?) (?:are )?(?:in|is) (?:a |an |one )?" + NUM + "?\\s*([a-z]+)$");
    private static final Pattern TO = Pattern.compile("^(?:convert |what'?s |what is )?" + NUM + "\\s*([a-z]+)(?: degrees)? (?:to|in|into) ([a-z]+)$");

    /** A unit conversion, or null if it isn't one. */
    static String convert(String question) {
        String q = question.toLowerCase(Locale.ROOT).strip().replaceAll("[?!.]+$", "").replace("degrees ", "").replace("°", "");
        String from, to;
        double n;
        Matcher m = HOW_MANY.matcher(q);
        if (m.matches()) {
            to = m.group(1);
            n = m.group(2) == null ? 1 : Double.parseDouble(m.group(2));
            from = m.group(3);
        } else if ((m = TO.matcher(q)).matches()) {
            n = Double.parseDouble(m.group(1));
            from = m.group(2);
            to = m.group(3);
        } else {
            return null;
        }
        Unit a = unit(from), b = unit(to);
        if (a == null || b == null) return null;
        if (!a.kind().equals(b.kind())) return "Hmm, you can't turn " + from + " into " + to + ". (Different kinds of things!)";
        double result;
        if (a.kind().equals("temp")) {
            double celsius = switch (base(from)) {
                case "f", "fahrenheit" -> (n - 32) * 5 / 9;
                case "k", "kelvin" -> n - 273.15;
                default -> n;
            };
            result = switch (base(to)) {
                case "f", "fahrenheit" -> celsius * 9 / 5 + 32;
                case "k", "kelvin" -> celsius + 273.15;
                default -> celsius;
            };
        } else {
            result = n * a.factor() / b.factor();
        }
        return pretty(n) + " " + from + " is " + pretty(result) + " " + to + ".";
    }

    private static String base(String unit) {
        String u = unit.endsWith("es") && UNITS.containsKey(unit.substring(0, unit.length() - 2)) ? unit.substring(0, unit.length() - 2)
                : unit.endsWith("s") && UNITS.containsKey(unit.substring(0, unit.length() - 1)) ? unit.substring(0, unit.length() - 1) : unit;
        return u.equals("feet") ? "foot" : u;
    }

    private static Unit unit(String name) {
        return UNITS.get(base(name.strip()));
    }

    static String pretty(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9 && Math.abs(v) < 1e12) return String.valueOf((long) Math.rint(v));
        double abs = Math.abs(v);
        String s = abs >= 100 ? String.format(Locale.ROOT, "%.1f", v) : abs >= 1 ? String.format(Locale.ROOT, "%.2f", v) : String.format(Locale.ROOT, "%.4f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }
}
