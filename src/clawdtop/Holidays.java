package clawdtop;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;

/** Special days: what he says, and what he shows (the first time you're on the computer that day). */
final class Holidays {
    private Holidays() {
    }

    /** A special day: its name (for remembering he's done it), what he says, and what he shows. */
    record Holiday(String id, String line, Creation show) {
    }

    private static Creation show(Creation.Effect effect, String item, long ms) {
        return new Creation("holiday", "", "", "", "", ms, effect, false, "", item);
    }

    /** Today's holiday, or null. */
    static Holiday on(LocalDate d) {
        int m = d.getMonthValue(), day = d.getDayOfMonth();
        if (m == 1 && day == 1) return new Holiday("newyear", "HAPPY NEW YEAR! " + d.getYear() + " is going to be great.", show(Creation.Effect.FIREWORKS, "", 7000));
        if (m == 2 && day == 14) return new Holiday("valentines", "Happy Valentine's Day!\nYou're my favorite human.", show(Creation.Effect.ITEM, "heart", 7000));
        if (d.equals(easter(d.getYear()))) return new Holiday("easter", "Happy Easter!", show(Creation.Effect.ITEM, "flower", 7000));
        if (m == 4 && day == 1) return new Holiday("aprilfools", "I'm a lobster now.\n...April Fools!", show(Creation.Effect.SPIN, "", 0));
        if (m == 7 && day == 4) return new Holiday("july4", "Happy 4th of July!", show(Creation.Effect.FIREWORKS, "", 7000));
        if (m == 10 && day == 31) return new Holiday("halloween", "BOO! Happy Halloween!\n(Did I scare you? I'm very scary.)", show(Creation.Effect.ITEM, "ghost", 7000));
        if (d.equals(LocalDate.of(d.getYear(), Month.NOVEMBER, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY)))) {
            return new Holiday("thanksgiving", "Happy Thanksgiving!\nI'm thankful for you.", show(Creation.Effect.ITEM, "heart", 7000));
        }
        if (m == 12 && day == 25) return new Holiday("christmas", "Merry Christmas!", show(Creation.Effect.SNOW, "", 9000));
        return null;
    }

    /** Easter Sunday that year (the usual way of working it out). */
    static LocalDate easter(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(y, month, day);
    }
}
