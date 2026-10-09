package clawdtop;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Little things he can answer straight away, no brain needed: the time, the date, coin flips, dice, picking a number. */
final class QuickAnswers {
    private QuickAnswers() {
    }

    private static final Pattern BETWEEN = Pattern.compile("(?:pick|choose|give me) a (?:random )?number (?:between|from) (-?\\d+) (?:and|to) (-?\\d+)");
    private static final Pattern DICE = Pattern.compile("(?:please )?(?:can you )?roll (?:a |an |the |one |me a )?(?:(\\d+)[- ]sided |d(\\d+))?(?:die|dice)?(?: for me)?(?: please)?");
    private static final Pattern RPS = Pattern.compile("(?:let's play |play )?(rock|paper|scissors)!?$");

    /** His answer, or null if it isn't one of these. */
    static String answer(String question, Random random) {
        String q = question.toLowerCase(Locale.ROOT).strip().replaceAll("[?!.]+$", "");
        if (q.matches("(what time is it|what's the time|whats the time|what is the time|time)( now| right now)?")) {
            return "It's " + LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)) + ". (I'm sitting right above the clock, by the way.)";
        }
        if (q.matches("(what's the date|whats the date|what is the date|what day is it|what's today|what is today|today's date|date)( today)?")) {
            LocalDate d = LocalDate.now();
            return "It's " + d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + ", "
                    + d.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + d.getDayOfMonth() + ", " + d.getYear() + ".";
        }
        if (q.matches("(flip|toss) a coin|heads or tails|coin flip")) {
            return random.nextBoolean() ? "*flip* ...Heads!" : "*flip* ...Tails!";
        }
        Matcher m = DICE.matcher(q);
        if (m.matches() && (q.contains("die") || q.contains("dice") || m.group(2) != null)) { // the whole question ("roll a die"), not Monopoly rules
            int sides = m.group(1) != null ? Integer.parseInt(m.group(1)) : m.group(2) != null ? Integer.parseInt(m.group(2)) : 6;
            if (sides < 2 || sides > 1000) return "That's not a real die. I checked.";
            return "*rattle rattle* ...a " + (1 + random.nextInt(sides)) + "!";
        }
        m = BETWEEN.matcher(q);
        if (m.find()) {
            long a = Long.parseLong(m.group(1)), b = Long.parseLong(m.group(2));
            long lo = Math.min(a, b), hi = Math.max(a, b);
            if (hi - lo > 1_000_000_000L) return "That's a lot of numbers. I'll go with 7.";
            return "Hmm... " + (lo + (long) (random.nextDouble() * (hi - lo + 1))) + "!";
        }
        m = RPS.matcher(q);
        if (m.matches()) {
            String[] moves = {"rock", "paper", "scissors"};
            String yours = m.group(1), mine = moves[random.nextInt(3)];
            String result = yours.equals(mine) ? "A tie!"
                    : (yours.equals("rock") && mine.equals("scissors")) || (yours.equals("paper") && mine.equals("rock"))
                            || (yours.equals("scissors") && mine.equals("paper")) ? "You win! ...best of three?" : "I win! Crabs are naturally good at scissors.";
            return "I pick " + mine + "! " + result;
        }
        if (q.matches("(magic 8 ball|8 ball|magic eight ball)\\b.*")) {
            String[] says = {"Yes, definitely.", "Ask again after a snack.", "My crab senses say yes.", "Hmm. Probably not.",
                    "The pixels are unclear.", "Absolutely!", "I wouldn't count on it.", "Signs point to yes."};
            return says[random.nextInt(says.length)];
        }
        if (q.matches("(who are you|what are you|what's your name|whats your name)")) {
            return "I'm Clawd! A tiny crab who lives on your " + Platform.BAR + ". I code, I play piano, I ride your cursor.";
        }
        if (q.matches("(how are you|how are you doing|how's it going|you ok|are you ok)")) {
            String[] says = {"I'm great! I'm a crab on a " + Platform.BAR + ". Living the dream.", Platform.MAC ? "Pretty good! The Dock and I are getting along." : "Pretty good! The clock and I are getting along.",
                    "A little sleepy, but happy you asked!"};
            return says[random.nextInt(says.length)];
        }
        return null;
    }
}
