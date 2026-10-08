package clawdtop;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** What you chose in Clawd's menu, kept for next time in your user folder (AppData\Roaming\Clawdtop on Windows). */
public final class Settings {
    private final Properties values = new Properties();
    private final Path file;

    private Settings(Path file) {
        this.file = file;
    }

    static Path folder() {
        String custom = System.getProperty("clawdtop.home"); // lets tests use another folder
        if (custom != null) return Path.of(custom);
        String appData = System.getenv("APPDATA");
        return appData != null ? Path.of(appData, "Clawdtop") : Path.of(System.getProperty("user.home"), ".clawdtop");
    }

    /** When the settings file last changed (0 if it isn't there), to notice changes made from the clawd command. */
    static long changed() {
        try {
            return Files.getLastModifiedTime(folder().resolve("settings.properties")).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    static Settings load() {
        Settings s = new Settings(folder().resolve("settings.properties"));
        if (Files.exists(s.file)) {
            try (Reader in = Files.newBufferedReader(s.file, StandardCharsets.UTF_8)) {
                s.values.load(in);
            } catch (IOException | IllegalArgumentException e) {
                // a damaged file: start over with the defaults
            }
        }
        return s;
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                values.store(out, "Clawdtop");
            }
        } catch (IOException e) {
            // couldn't save: it still works this time
        }
    }

    public boolean sounds() {
        return !"false".equals(values.getProperty("beeps"));
    }

    public void setSounds(boolean on) {
        values.setProperty("beeps", String.valueOf(on));
        save();
    }

    public boolean tips() {
        return !"false".equals(values.getProperty("tips"));
    }

    public void setTips(boolean on) {
        values.setProperty("tips", String.valueOf(on));
        save();
    }

    /** Your name, as you told him when you first met (or "" before then). */
    public String name() {
        return values.getProperty("name", "").strip();
    }

    public void setName(String name) {
        values.setProperty("name", name.strip());
        save();
    }

    /** Whether you've met (he said hi and you told him your name), so he doesn't introduce himself again. */
    public boolean met() {
        return "true".equals(values.getProperty("met"));
    }

    public void setMet() {
        values.setProperty("met", "true");
        save();
    }

    public Pet.Personality personality() {
        return Pet.Personality.of(values.getProperty("personality"));
    }

    public void setPersonality(Pet.Personality p) {
        values.setProperty("personality", p.name());
        save();
    }

    /** His color, as #RRGGBB ("#D77757", his own orange, unless you changed it). */
    public String color() {
        String c = values.getProperty("color", "#D77757").strip();
        return c.matches("#[0-9A-Fa-f]{6}") ? c.toUpperCase(java.util.Locale.ROOT) : "#D77757";
    }

    public java.awt.Color awtColor() {
        return java.awt.Color.decode(color());
    }

    public void setColor(String hex) {
        values.setProperty("color", hex);
        save();
    }

    /** His Clawd Points (earned by spending time together). */
    public int points() {
        try {
            return Math.max(0, Integer.parseInt(values.getProperty("points", "0")));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setPoints(int points) {
        values.setProperty("points", String.valueOf(Math.max(0, points)));
        save();
    }

    /** A save token with who he is to you, for clawd uninstall. */
    public String saveToken() {
        java.util.Map<String, String> v = new java.util.HashMap<>();
        for (String key : SaveToken.KEYS) v.put(key, values.getProperty(key, ""));
        return SaveToken.make(v);
    }

    /** Takes a save token's settings (true), or false if it isn't a real token. */
    public boolean useToken(String token) {
        java.util.Map<String, String> v = SaveToken.read(token);
        if (v == null) return false;
        v.forEach(values::setProperty);
        values.setProperty("x", "-1");
        values.setProperty("restored", "true");
        save();
        return true;
    }

    /** Whether he came back from a save token (he half remembers you). */
    public boolean restored() {
        return "true".equals(values.getProperty("restored"));
    }

    /** Asks the running Clawd to do something (from the clawd command): "mood happy", "goodbye"... */
    static void ask(String what) {
        try {
            Files.createDirectories(folder());
            Files.writeString(folder().resolve("ask.txt"), what, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // he won't hear it
        }
    }

    /** What the clawd command asked (and forgets it), or null. */
    static String takeAsk() {
        Path ask = folder().resolve("ask.txt");
        try {
            if (!Files.exists(ask)) return null;
            String what = Files.readString(ask, StandardCharsets.UTF_8).strip();
            Files.deleteIfExists(ask);
            return what;
        } catch (IOException e) {
            return null;
        }
    }

    /** "Small", "Normal" or "Big". */
    public String size() {
        String size = values.getProperty("size", "Normal");
        return size.equals("Small") || size.equals("Big") ? size : "Normal";
    }

    public void setSize(String size) {
        values.setProperty("size", size);
        save();
    }

    /** Screen pixels for each of his own pixels. */
    public int unit() {
        return switch (size()) {
            case "Small" -> 2;
            case "Big" -> 5;
            default -> 3;
        };
    }

    /** Where he sits when he hasn't been dragged: one of Welcome.SPOTS. */
    public String spot() {
        String spot = values.getProperty("spot", Welcome.SPOTS[0]);
        return java.util.Arrays.asList(Welcome.SPOTS).contains(spot) ? spot : Welcome.SPOTS[0];
    }

    /** Picks one of Welcome.SPOTS (and forgets where he was dragged). */
    public void setSpot(String spot) {
        values.setProperty("spot", spot);
        values.setProperty("x", "-1");
        save();
    }

    /** Where he was dragged to along the taskbar, or -1 for his spot. */
    public int x() {
        try {
            return Integer.parseInt(values.getProperty("x", "-1"));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public void setX(int x) {
        values.setProperty("x", String.valueOf(x));
        save();
    }
}
