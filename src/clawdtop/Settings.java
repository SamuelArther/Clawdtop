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

    /** A switch from Options (on or off). */
    public boolean on(String key) {
        Options.Option o = Options.find(key);
        if (TOMFOOLERY.contains(key) && serious()) return false;
        return Boolean.parseBoolean(values.getProperty("opt." + key, o.start()));
    }

    /** What "No tomfoolery" turns off: the silly stuff. The useful things (tips, cleaning, reminders) stay. */
    static final java.util.Set<String> TOMFOOLERY = java.util.Set.of("sneezes", "flies", "spins", "creates", "capsLock", "grumpy",
            "stompOff", "batteryPanic", "freakout", "friday", "friendship", "rides", "shakeOff", "seasonalHats", "monday");

    /** No tomfoolery: serious mode. */
    public boolean serious() {
        return Boolean.parseBoolean(values.getProperty("opt.serious", "false"));
    }

    /** A choice from Options (one of its choices). */
    public String choice(String key) {
        Options.Option o = Options.find(key);
        String v = values.getProperty("opt." + key, o.start());
        return o.choices().contains(v) ? v : o.start();
    }

    /** A number from Options (kept inside its range). */
    public int number(String key) {
        Options.Option o = Options.find(key);
        try {
            return Math.max(o.min(), Math.min(o.max(), Integer.parseInt(values.getProperty("opt." + key, o.start()).strip())));
        } catch (NumberFormatException e) {
            return Integer.parseInt(o.start());
        }
    }

    /** Changes an option. */
    public void set(String key, String value) {
        Options.find(key);
        values.setProperty("opt." + key, value);
        save();
    }

    /** How often he tells jokes: "Off", "Rare", "Sometimes" or "Lots". */
    public String jokes() {
        if (serious()) return "Off";
        String j = values.getProperty("jokes", "Sometimes");
        return java.util.List.of("Off", "Rare", "Sometimes", "Lots").contains(j) ? j : "Sometimes";
    }

    public void setJokes(String often) {
        values.setProperty("jokes", often);
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

    /** What he calls this computer, his home (like "Sam's Laptop"), or "home" if you never said. */
    public String home() {
        String h = values.getProperty("home", "").strip();
        return h.isEmpty() ? "home" : h;
    }

    /** Whether you've named his home. */
    public boolean homeNamed() {
        return !values.getProperty("home", "").isBlank();
    }

    public void setHome(String home) {
        home = home.strip().replace("|", "");
        values.setProperty("home", home.length() > 30 ? home.substring(0, 30) : home);
        save();
    }

    /** Every computer he's lived on before this one, oldest first. */
    public java.util.List<String> oldHomes() {
        String all = values.getProperty("oldHomes", "").strip();
        return all.isEmpty() ? java.util.List.of() : java.util.List.of(all.split("\\|"));
    }

    /** He just moved here from oldHome (its name): it goes on the list of places he's lived. */
    public void movedFrom(String oldHome) {
        java.util.List<String> homes = new java.util.ArrayList<>(oldHomes());
        if (!oldHome.isBlank()) homes.add(oldHome.strip().replace("|", ""));
        while (homes.size() > 10) homes.remove(0);
        values.setProperty("oldHomes", String.join("|", homes));
        values.remove("home");
        save();
    }

    /** A name for this computer if you don't pick one: yours on it, laptop or computer. */
    public String suggestedHome() {
        String kind = Power.now() != null ? "Laptop" : "Computer";
        return name().isEmpty() ? "My " + kind : name() + "'s " + kind;
    }

    /** Everything he's coded so far (ids), so he doesn't keep making the same thing. */
    public java.util.Set<String> made() {
        String all = values.getProperty("made", "").strip();
        return all.isEmpty() ? java.util.Set.of() : new java.util.LinkedHashSet<>(java.util.List.of(all.split(",")));
    }

    /** The last thing he coded. */
    public String lastMade() {
        return values.getProperty("lastMade", "");
    }

    public void addMade(String id) {
        java.util.Set<String> all = new java.util.LinkedHashSet<>(made());
        all.add(id);
        values.setProperty("made", String.join(",", all));
        values.setProperty("lastMade", id);
        save();
    }

    /** Where the files he codes go. */
    static Path creations() {
        return folder().resolve("creations");
    }

    /** Whether you've met (he said hi and you told him your name), so he doesn't introduce himself again. */
    public boolean met() {
        return "true".equals(values.getProperty("met"));
    }

    public void setMet() {
        values.setProperty("met", "true");
        if (!values.containsKey("metDate")) values.setProperty("metDate", java.time.LocalDate.now().toString());
        values.setProperty("lastSeen", String.valueOf(System.currentTimeMillis()));
        save();
    }

    /** Your birthday as "MM-DD" (no year), or "" if you didn't say. */
    public String birthday() {
        String b = values.getProperty("birthday", "");
        return b.matches("\\d\\d-\\d\\d") ? b : "";
    }

    public void setBirthday(String mmdd) {
        values.setProperty("birthday", mmdd);
        save();
    }

    /** Whether today is your birthday. */
    public boolean birthdayToday() {
        String b = birthday();
        if (b.isEmpty()) return false;
        java.time.LocalDate d = java.time.LocalDate.now();
        return b.equals(String.format("%02d-%02d", d.getMonthValue(), d.getDayOfMonth()));
    }

    /** The last moment you were around (moving the mouse while he ran), in ms, or 0. */
    public long lastSeen() {
        try {
            return Long.parseLong(values.getProperty("lastSeen", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void setLastSeen(long when) {
        values.setProperty("lastSeen", String.valueOf(when));
        save();
    }

    /** "3 days", "2 weeks", "5 months", "1 year": how long a gap was, in friendly words. */
    static String howLong(long ms) {
        long days = ms / 86_400_000L;
        if (days >= 365) return plural(days / 365, "year");
        if (days >= 60) return plural(days / 30, "month");
        if (days >= 14) return plural(days / 7, "week");
        return plural(days, "day");
    }

    private static String plural(long n, String what) {
        return n + " " + what + (n == 1 ? "" : "s");
    }

    /** The day you met (today if it's not known). */
    public java.time.LocalDate metDate() {
        try {
            return java.time.LocalDate.parse(values.getProperty("metDate"));
        } catch (Exception e) {
            return java.time.LocalDate.now();
        }
    }

    /** Whether something once-only (like "monday:2026-10-12") has happened; marks it as happened. */
    public boolean once(String what) {
        String done = values.getProperty("once", "");
        if (java.util.Arrays.asList(done.split(",")).contains(what)) return false;
        // keep the list short: only the last 20
        java.util.List<String> list = new java.util.ArrayList<>(java.util.Arrays.asList(done.split(",")));
        list.removeIf(String::isBlank);
        list.add(what);
        while (list.size() > 20) list.remove(0);
        values.setProperty("once", String.join(",", list));
        save();
        return true;
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

    /** Adds Clawd Points (for something you did together). */
    public void earn(int points) {
        setPoints(points() + points);
    }

    /** Whether he has this shop item. */
    public boolean owns(String id) {
        return java.util.Arrays.asList(values.getProperty("owned", "").split(",")).contains(id);
    }

    public void own(String id) {
        if (owns(id)) return;
        String owned = values.getProperty("owned", "");
        values.setProperty("owned", owned.isEmpty() ? id : owned + "," + id);
        save();
    }

    /** What he's wearing or sitting by: "hat" or "hut" (an item id, or "" for none). */
    public String wearing(String slot) {
        String id = values.getProperty(slot, "");
        return owns(id) ? id : "";
    }

    public void setWearing(String slot, String id) {
        values.setProperty(slot, id);
        save();
    }

    /** Pets today (points for petting stop after Shop.PETS_A_DAY a day). */
    public int petsToday() {
        String today = java.time.LocalDate.now().toString();
        if (!today.equals(values.getProperty("petDay"))) return 0;
        try {
            return Integer.parseInt(values.getProperty("pets", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void countPet() {
        int pets = petsToday() + 1;
        values.setProperty("petDay", java.time.LocalDate.now().toString());
        values.setProperty("pets", String.valueOf(pets));
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
