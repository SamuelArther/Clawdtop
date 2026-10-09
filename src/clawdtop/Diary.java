package clawdtop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Clawd's diary: the things he got up to, in his own words (clawd diary shows it). Kept small. */
final class Diary {
    private Diary() {
    }

    static Path file() {
        return Settings.folder().resolve("diary.txt");
    }

    /** Writes a line in his diary ("Made a flying carpet. Best day ever."). Quietly does nothing if it can't. */
    static void write(String entry) {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            String line = LocalDate.now() + " " + LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)) + "|" + entry.replace('\n', ' ') + "\n";
            Files.writeString(f, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            List<String> all = Files.readAllLines(f, StandardCharsets.UTF_8);
            if (all.size() > 400) Files.write(f, all.subList(all.size() - 300, all.size()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // no diary today
        }
    }

    /** The last few days of his diary, written out for reading (newest last). */
    static List<String> read(int days) {
        List<String> out = new ArrayList<>();
        try {
            List<String> all = Files.exists(file()) ? Files.readAllLines(file(), StandardCharsets.UTF_8) : List.of();
            LocalDate since = LocalDate.now().minusDays(days - 1L);
            String day = "";
            for (String line : all) {
                int bar = line.indexOf('|');
                if (bar < 11) continue;
                LocalDate d;
                try {
                    d = LocalDate.parse(line.substring(0, 10));
                } catch (RuntimeException e) {
                    continue;
                }
                if (d.isBefore(since)) continue;
                if (!line.substring(0, 10).equals(day)) {
                    day = line.substring(0, 10);
                    out.add("");
                    out.add(d.equals(LocalDate.now()) ? "Dear diary, today:" : d.equals(LocalDate.now().minusDays(1)) ? "Yesterday:"
                            : d.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + ", " + d + ":");
                }
                out.add("  " + line.substring(11, bar).strip() + "  " + line.substring(bar + 1));
            }
        } catch (IOException e) {
            // nothing to read
        }
        return out;
    }
}
