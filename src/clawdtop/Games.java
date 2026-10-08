package clawdtop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Your game library, as far as Clawd can tell from this computer alone (Steam, Epic, EA app, Ubisoft Connect, GOG, the
 * Xbox app, Riot, and Minecraft through Kelp), so he can say nice things about it. Nothing is sent anywhere; he only
 * reads the launchers' own lists of what's installed.
 */
final class Games {
    private Games() {
    }

    /** Names that aren't really games (tools and bits the launchers install too). */
    private static final Pattern NOT_A_GAME = Pattern.compile(
            "(?i).*(redistributable|digital ownership|dlc|proton|steam linux runtime|steamworks|sdk|dedicated server|soundtrack|benchmark|"
                    + "ea app|ea desktop|origin|riot client|ubisoft connect|launcher|directx|vc\\+\\+|easyanticheat|battleye|gamesave).*");

    /** Everything he can find, sorted, no repeats. Slow-ish (it looks round the disk): not on the drawing thread. */
    static List<String> find() {
        TreeSet<String> all = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try {
            steam(all);
        } catch (RuntimeException | IOException e) {
            // no Steam
        }
        try {
            epic(all, Path.of("C:\\ProgramData\\Epic\\EpicGamesLauncher\\Data\\Manifests"));
        } catch (RuntimeException | IOException e) {
            // no Epic
        }
        for (String key : List.of("HKLM\\SOFTWARE\\WOW6432Node\\Electronic Arts", "HKLM\\SOFTWARE\\Electronic Arts")) {
            all.addAll(regSubkeys(key)); // EA app: a key per game
        }
        for (String dir : regValues("HKLM\\SOFTWARE\\WOW6432Node\\Ubisoft\\Launcher\\Installs", "InstallDir")) {
            String name = Path.of(dir.replace('/', '\\')).getFileName() == null ? "" : Path.of(dir.replace('/', '\\')).getFileName().toString();
            if (!name.isBlank()) all.add(name);
        }
        all.addAll(regValues("HKLM\\SOFTWARE\\WOW6432Node\\GOG.com\\Games", "gameName"));
        for (Path root : List.of(Path.of("C:\\XboxGames"), Path.of("D:\\XboxGames"), Path.of("C:\\Riot Games"), Path.of("C:\\Program Files\\EA Games"))) {
            folders(all, root);
        }
        String appData = System.getenv("APPDATA");
        if (appData != null && Files.isDirectory(Path.of(appData, "Kelp"))) all.add("Minecraft");
        all.removeIf(name -> NOT_A_GAME.matcher(name).matches() || name.isBlank());
        return List.copyOf(all);
    }

    /** Steam: every library folder it knows, and the games in each. */
    static void steam(TreeSet<String> into) throws IOException {
        String steamPath = regValue("HKCU\\Software\\Valve\\Steam", "SteamPath");
        Path steam = Path.of(steamPath != null ? steamPath.replace('/', '\\') : "C:\\Program Files (x86)\\Steam");
        List<Path> libraries = new ArrayList<>(List.of(steam));
        Path folders = steam.resolve("steamapps").resolve("libraryfolders.vdf");
        if (Files.isRegularFile(folders)) {
            Matcher m = Pattern.compile("\"path\"\\s+\"([^\"]+)\"").matcher(Files.readString(folders, StandardCharsets.UTF_8));
            while (m.find()) libraries.add(Path.of(m.group(1).replace("\\\\", "\\")));
        }
        for (Path library : libraries) steamApps(into, library.resolve("steamapps"));
    }

    /** The games in one Steam library's steamapps folder (each has an appmanifest_*.acf with its name). */
    static void steamApps(TreeSet<String> into, Path steamapps) throws IOException {
        if (!Files.isDirectory(steamapps)) return;
        Pattern name = Pattern.compile("\"name\"\\s+\"([^\"]+)\"");
        try (var files = Files.list(steamapps)) {
            for (Path f : files.filter(p -> p.getFileName().toString().startsWith("appmanifest_")).toList()) {
                Matcher m = name.matcher(Files.readString(f, StandardCharsets.UTF_8));
                if (m.find()) into.add(m.group(1));
            }
        }
    }

    /** Epic: a .item file per game, with its DisplayName. */
    static void epic(TreeSet<String> into, Path manifests) throws IOException {
        if (!Files.isDirectory(manifests)) return;
        Pattern name = Pattern.compile("\"DisplayName\"\\s*:\\s*\"([^\"]+)\"");
        try (var files = Files.list(manifests)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".item")).toList()) {
                Matcher m = name.matcher(Files.readString(f, StandardCharsets.UTF_8));
                if (m.find()) into.add(m.group(1));
            }
        }
    }

    private static void folders(TreeSet<String> into, Path root) {
        if (!Files.isDirectory(root)) return;
        try (var dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory).forEach(d -> into.add(d.getFileName().toString()));
        } catch (IOException | RuntimeException e) {
            // can't look in there
        }
    }

    // ---- Reading the registry (with Windows' own reg.exe; only ever reading) ----

    private static List<String> reg(String... args) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) return List.of();
        List<String> command = new ArrayList<>(List.of("reg", "query"));
        command.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            return p.exitValue() == 0 ? out.lines().toList() : List.of();
        } catch (IOException | InterruptedException e) {
            return List.of();
        }
    }

    private static String regValue(String key, String value) {
        for (String line : reg(key, "/v", value)) {
            String t = line.strip();
            if (t.startsWith(value + " ")) {
                int at = t.indexOf("REG_");
                int space = at < 0 ? -1 : t.indexOf(' ', at);
                if (space > 0) return t.substring(space).strip();
            }
        }
        return null;
    }

    private static List<String> regValues(String key, String value) {
        List<String> found = new ArrayList<>();
        for (String line : reg(key, "/s", "/v", value)) {
            String t = line.strip();
            if (t.startsWith(value + " ")) {
                int at = t.indexOf("REG_");
                int space = at < 0 ? -1 : t.indexOf(' ', at);
                if (space > 0) found.add(t.substring(space).strip());
            }
        }
        return found;
    }

    private static List<String> regSubkeys(String key) {
        List<String> found = new ArrayList<>();
        String prefix = key.toUpperCase(Locale.ROOT) + "\\";
        for (String line : reg(key)) {
            String t = line.strip();
            String upper = t.toUpperCase(Locale.ROOT).replace("HKEY_LOCAL_MACHINE", "HKLM").replace("HKEY_CURRENT_USER", "HKCU");
            if (upper.startsWith(prefix) && upper.length() > prefix.length()) found.add(t.substring(t.lastIndexOf('\\') + 1));
        }
        return found;
    }

    // ---- What he says about them ----

    /** Games he has something special to say about. */
    private static final String[][] SPECIAL = {
            {"minecraft", "Minecraft! Have you tried Kelp? I hear it's great."},
            {"terraria", "Terraria? Excellent taste in digging."},
            {"stardew", "Stardew Valley. I'd grow crab apples."},
            {"portal", "Portal! The cake is... no, I won't say it."},
            {"hollow knight", "Hollow Knight. Bugs fighting bugs. I relate."},
            {"rocket league", "Rocket League! Cars plus soccer. Genius."},
            {"celeste", "Celeste? You must be great at not giving up."},
            {"among us", "Among Us. I'm not the impostor. Probably."},
            {"undertale", "Undertale. You'd better have been nice."},
            {"fortnite", "Fortnite! Teach me a dance? I have four legs."},
            {"subnautica", "Subnautica. I'd feel right at home in there."},
            {"hades", "Hades. Fight your way out! I'll wait here."},
            {"slime rancher", "Slime Rancher. Cute. Not as cute as me."},
            {"geometry dash", "Geometry Dash. My claws are NOT fast enough."},
            {"half-life", "Half-Life. Still waiting on the third one, huh?"},
            {"forza", "Forza! Vroom vroom. That's car for hello."},
            {"halo", "Halo! Finish the fight. Then have a snack."},
            {"the sims", "The Sims. Don't take the ladder out of my pool."},
            {"fc 2", "EA FC! I'd play, but I can't kick. Or run."},
            {"rayman", "Rayman! He has no arms either. Legend."},
            {"assassin", "Assassin's Creed. I can't climb walls. Yet."},
            {"valorant", "Valorant. Crab main, coming soon."},
            {"league of legends", "League of Legends. I'll be on your team. In spirit."},
            {"cyberpunk", "Cyberpunk. I'm basically a robot crab already."},
            {"elden ring", "Elden Ring. Good luck. You will need it."},
            {"lethal company", "Lethal Company. I'd make a great scrap item."},
            {"roblox", "Roblox! I'd make a game where you're a crab."}};

    private static final String[] ANY = {
            "%s? Good taste.",
            "I see %s in there. Respect.",
            "%s... a classic. Well, I think so. I'm a crab.",
            "Ooh, %s. Can I watch next time?",
            "If you need a co-op buddy for %s, I have claws.",
            "%s! I've heard great things. From you. Just now."};

    private static final String[] COUNT = {
            "You have %d games! I have one laptop. Same thing.",
            "%d games! When do you even sleep?",
            "%d games installed. That's %d more than the crab next door.",
            "Your game library is great. Mine is just ducks.py."};

    /** Something nice about your games, or null if he couldn't find any. */
    static String compliment(List<String> games, Random random) {
        if (games.isEmpty()) return null;
        int roll = random.nextInt(10);
        if (roll < 3) {
            String c = COUNT[random.nextInt(COUNT.length)];
            return c.contains("%d") ? String.format(c, games.size(), games.size()) : c;
        }
        if (roll < 7) { // something special, if there's a game he knows
            List<String> special = new ArrayList<>();
            for (String g : games) for (String[] s : SPECIAL) if (g.toLowerCase(Locale.ROOT).contains(s[0])) special.add(s[1]);
            if (!special.isEmpty()) return special.get(random.nextInt(special.size()));
        }
        if (games.size() >= 2 && roll == 9) {
            String a = games.get(random.nextInt(games.size())), b = games.get(random.nextInt(games.size()));
            if (!a.equals(b)) return a + " AND " + b + "? You've got range.";
        }
        return String.format(ANY[random.nextInt(ANY.length)], games.get(random.nextInt(games.size())));
    }

    /** Whether the app in front plays videos (full screen there is a video, not a game: he hides instead). */
    static boolean videoApp(String app) {
        return switch (app.toLowerCase(Locale.ROOT)) {
            case "chrome.exe", "msedge.exe", "firefox.exe", "brave.exe", "opera.exe", "vlc.exe", "mpc-hc64.exe", "wmplayer.exe",
                    "microsoft.media.player.exe", "video.ui.exe", "netflix.exe", "applicationframehost.exe", "powerpnt.exe" -> true;
            default -> false;
        };
    }

    /** Whether the app in front is a game launcher (he might say something about your games). */
    static boolean launcher(String app) {
        return switch (app.toLowerCase(Locale.ROOT)) {
            case "steam.exe", "steamwebhelper.exe", "epicgameslauncher.exe", "eadesktop.exe", "upc.exe", "ubisoftconnect.exe",
                    "galaxyclient.exe", "xboxpcapp.exe", "riotclientux.exe", "battle.net.exe" -> true;
            default -> false;
        };
    }
}
