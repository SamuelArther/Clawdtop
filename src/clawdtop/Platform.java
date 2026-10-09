package clawdtop;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * The bits that are different on a Mac or Linux (Windows has its own, in Foreground, Power, Startup and Install):
 * which app is in front, the battery, how long the computer's been on, starting at login, the clawd command, and opening
 * handy things.
 */
final class Platform {
    private Platform() {
    }

    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    static final boolean WINDOWS = OS.startsWith("windows");
    static final boolean MAC = OS.startsWith("mac");
    static final boolean LINUX = !WINDOWS && !MAC;
    /** What the bar along the bottom of the screen is called here: the Dock on a Mac, the taskbar elsewhere. */
    static final String BAR = MAC ? "Dock" : "taskbar";

    /** Makes a window see-through, if this computer can (on Linux it needs a compositor; without one, it stays plain). */
    static void seeThrough(Window w) {
        try {
            if (GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .isWindowTranslucencySupported(java.awt.GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                w.setBackground(new Color(0, 0, 0, 0));
            }
        } catch (RuntimeException notSupported) {
            // a plain window, then
        }
    }

    /** Runs a little command and gives back what it printed (or "" if it didn't work), waiting at most a couple of seconds. */
    /** Whether a program is on the PATH (like parec on Linux). */
    static boolean onPath(String program) {
        for (String dir : System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator)) {
            try {
                if (!dir.isBlank() && java.nio.file.Files.isExecutable(java.nio.file.Path.of(dir.strip(), program))) return true;
            } catch (RuntimeException badPath) {
                // skip it
            }
        }
        return false;
    }

    static String run(String... command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "";
            }
            return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException | InterruptedException e) {
            return "";
        }
    }

    // ---- Which app is in front ----

    private static volatile String frontApp = "", frontTitle = "";
    private static volatile int[] frontBounds = new int[4]; // left, top, right, bottom, in real pixels (like Windows gives)
    private static Thread watcher;

    /** The app in front, by name (like "code" or "terminal"), or "" (looked up in the background every second). */
    static synchronized String frontApp() {
        if (watcher == null && !WINDOWS) {
            watcher = new Thread(() -> {
                while (true) {
                    lookUpFront();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }, "Clawdtop front app");
            watcher.setDaemon(true);
            watcher.start();
        }
        return frontApp;
    }

    /** The window in front's title and where it is (Mac and Linux), or "" and zeros. */
    static String frontTitle() {
        frontApp();
        return frontTitle;
    }

    static int[] frontBounds() {
        frontApp();
        return frontBounds;
    }

    /** How many real pixels to one of Java's (2 on a Retina screen), for giving window spots in real pixels. */
    private static double scale() {
        try {
            return java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration()
                    .getDefaultTransform().getScaleX();
        } catch (Exception headless) {
            return 1;
        }
    }

    /**
     * The app in front, its window's title and where it is. Mac: System Events (macOS asks once to allow it; the
     * title and position need Accessibility, also asked once). Linux: xdotool (X11; Wayland doesn't say).
     */
    private static void lookUpFront() {
        if (MAC) {
            String out = run("osascript", "-e", String.join("\n",
                    "tell application \"System Events\"",
                    "  set p to first application process whose frontmost is true",
                    "  set n to name of p",
                    "  set t to \"\"",
                    "  set b to \"\"",
                    "  try",
                    "    set w to front window of p",
                    "    set t to name of w",
                    "    set {x, y} to position of w",
                    "    set {ww, hh} to size of w",
                    "    set b to (x as text) & \",\" & (y as text) & \",\" & (ww as text) & \",\" & (hh as text)",
                    "  end try",
                    "  return n & \"|\" & t & \"|\" & b",
                    "end tell"));
            String[] parts = out.split("\\|", -1);
            frontApp = parts[0].strip().toLowerCase(Locale.ROOT);
            frontTitle = parts.length > 1 ? parts[1].strip() : "";
            frontBounds = parts.length > 2 ? bounds(parts[2], scale()) : new int[4];
            return;
        }
        String pid = run("xdotool", "getactivewindow", "getwindowpid"); // X11 only; on Wayland this just says nothing
        if (!pid.matches("\\d+")) {
            frontApp = "";
            return;
        }
        try {
            frontApp = Files.readString(Path.of("/proc", pid, "comm")).strip().toLowerCase(Locale.ROOT);
        } catch (IOException e) {
            frontApp = "";
        }
        frontTitle = run("xdotool", "getactivewindow", "getwindowname");
        java.util.Map<String, Integer> g = new java.util.HashMap<>();
        for (String line : run("xdotool", "getactivewindow", "getwindowgeometry", "--shell").split("\\R")) {
            String[] kv = line.split("=");
            if (kv.length == 2 && kv[1].strip().matches("-?\\d+")) g.put(kv[0].strip(), Integer.parseInt(kv[1].strip()));
        }
        frontBounds = g.containsKey("X") && g.containsKey("WIDTH")
                ? new int[] {g.get("X"), g.get("Y"), g.get("X") + g.get("WIDTH"), g.get("Y") + g.get("HEIGHT")} : new int[4];
    }

    /** "x,y,width,height" (in points) as left, top, right, bottom in real pixels. */
    static int[] bounds(String xywh, double scale) {
        String[] v = xywh.split(",");
        if (v.length != 4) return new int[4];
        try {
            double x = Double.parseDouble(v[0].strip()), y = Double.parseDouble(v[1].strip()), w = Double.parseDouble(v[2].strip()), h = Double.parseDouble(v[3].strip());
            return new int[] {(int) (x * scale), (int) (y * scale), (int) ((x + w) * scale), (int) ((y + h) * scale)};
        } catch (NumberFormatException e) {
            return new int[4];
        }
    }

    // ---- Folder windows (for cleaning) ----

    /** Whether the window in front is a folder window on a Mac or Linux (Finder; Files, Dolphin and friends). */
    static boolean folderWindow(String app) {
        return MAC ? app.equals("finder") : java.util.Set.of("nautilus", "nemo", "thunar", "dolphin", "caja", "pcmanfm", "pcmanfm-qt").contains(app);
    }

    /** The folder the Finder (or a Linux file manager) window in front shows, or null. */
    static Path folderInFront() {
        if (MAC) {
            String out = run("osascript", "-e", "tell application \"Finder\" to if (count of Finder windows) > 0 then POSIX path of (target of front Finder window as alias)");
            if (out.isBlank() || out.contains("error")) return null;
            Path p = Path.of(out.strip());
            return Files.isDirectory(p) ? p : null;
        }
        // Linux file managers don't say which folder they show: the title is its name, so look for it in the usual places
        String title = frontTitle.replaceAll("\\s+[-\u2014]\\s+.*$", "").strip();
        if (title.isEmpty()) return null;
        Path home = Path.of(System.getProperty("user.home"));
        if (title.equals("Home") || title.equals(home.getFileName().toString())) return home;
        for (Path p : List.of(home.resolve(title), home.resolve("Documents").resolve(title), home.resolve("Downloads").resolve(title), home.resolve("Desktop").resolve(title))) {
            if (Files.isDirectory(p)) return p;
        }
        return null;
    }

    // ---- Battery and uptime ----

    /** The battery (Mac: pmset; Linux: /sys), or null if there isn't one. */
    static Power.State battery() {
        if (MAC) {
            String out = run("pmset", "-g", "batt");
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)%").matcher(out);
            if (!m.find()) return null;
            return new Power.State(Integer.parseInt(m.group(1)), out.contains("AC Power"));
        }
        try (var dirs = Files.list(Path.of("/sys/class/power_supply"))) {
            for (Path d : dirs.toList()) {
                if (!d.getFileName().toString().startsWith("BAT")) continue;
                int percent = Integer.parseInt(Files.readString(d.resolve("capacity")).strip());
                String status = Files.readString(d.resolve("status")).strip();
                return new Power.State(percent, !status.equalsIgnoreCase("Discharging"));
            }
        } catch (IOException | RuntimeException e) {
            // no battery here
        }
        return null;
    }

    /** How long since the computer started (ms), or -1. */
    static long uptime() {
        try {
            if (MAC) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("sec = (\\d+)").matcher(run("sysctl", "-n", "kern.boottime"));
                return m.find() ? System.currentTimeMillis() - Long.parseLong(m.group(1)) * 1000 : -1;
            }
            return (long) (Double.parseDouble(Files.readString(Path.of("/proc/uptime")).split(" ")[0]) * 1000);
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    // ---- Handy things to open ----

    /** Things he can open for you here, by name. */
    static String[][] openable() {
        if (MAC) {
            return new String[][] {{"Downloads", "folder:Downloads"}, {"Desktop", "folder:Desktop"}, {"Documents", "folder:Documents"},
                    {"Trash", "folder:.Trash"}, {"Activity Monitor", "app:Activity Monitor"}, {"Calculator", "app:Calculator"},
                    {"TextEdit", "app:TextEdit"}, {"System Settings", "app:System Settings"}};
        }
        return new String[][] {{"Downloads", "folder:Downloads"}, {"Desktop", "folder:Desktop"}, {"Documents", "folder:Documents"},
                {"Trash", "url:trash:///"}, {"System Monitor", "cmd:gnome-system-monitor"}, {"Calculator", "cmd:gnome-calculator"},
                {"Text Editor", "cmd:gnome-text-editor"}};
    }

    /** Opens a calculator. */
    static void openCalculator() {
        if (MAC) run("open", "-a", "Calculator");
        else if (LINUX) for (String c : List.of("gnome-calculator", "kcalc", "galculator", "xcalc")) {
            try {
                new ProcessBuilder(c).start();
                return;
            } catch (IOException notThisOne) {
                // try the next
            }
        }
    }

    // ---- Starting at login ----

    private static Path loginFile() {
        Path home = Path.of(System.getProperty("user.home"));
        return MAC ? home.resolve("Library/LaunchAgents/com.clawdtop.plist") : home.resolve(".config/autostart/clawdtop.desktop");
    }

    static boolean startsAtLogin() {
        return Files.exists(loginFile());
    }

    static void setStartsAtLogin(boolean on) {
        Path file = loginFile();
        try {
            if (!on) {
                Files.deleteIfExists(file);
                return;
            }
            String text = loginText();
            if (text == null) return;
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // it just won't start by itself
        }
    }

    /** If he starts at login, makes sure it still points at this jar and a Java that's there (they move: updates, folders). */
    static void refreshStartsAtLogin() {
        if (!startsAtLogin()) return;
        String text = loginText();
        if (text == null) return;
        try {
            if (!text.equals(Files.readString(loginFile(), StandardCharsets.UTF_8))) Files.writeString(loginFile(), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // leave it
        }
    }

    /** What the login file says (null if he isn't running from his jar). */
    private static String loginText() {
        Path jar = Install.jar();
        if (jar == null || !jar.toString().endsWith(".jar")) return null;
        // (a Mac's /usr/bin/java always runs the newest Java installed, so a Java update doesn't break it)
        String java = MAC && Files.isExecutable(Path.of("/usr/bin/java")) ? "/usr/bin/java" : Install.java().toString();
        return MAC ? macLogin(java, jar.toString()) : "[Desktop Entry]\nType=Application\nName=Clawdtop\nComment=Clawd, on your taskbar\nExec=\"" + java
                + "\" --enable-native-access=ALL-UNNAMED -jar \"" + jar + "\"\nX-GNOME-Autostart-enabled=true\n";
    }

    /** The Mac's login file (a LaunchAgent), with the paths made safe for its XML ("&" and friends). */
    static String macLogin(String java, String jar) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
                + "<plist version=\"1.0\"><dict>\n<key>Label</key><string>com.clawdtop</string>\n"
                + "<key>ProgramArguments</key><array><string>" + xml(java) + "</string><string>--enable-native-access=ALL-UNNAMED</string>"
                + "<string>-jar</string><string>" + xml(jar) + "</string></array>\n<key>RunAtLoad</key><true/>\n</dict></plist>\n";
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ---- The clawd command ----

    /** Where the clawd command goes: ~/.local/bin (on most Linux computers that's already on your PATH). */
    static Path commandFile() {
        return Path.of(System.getProperty("user.home"), ".local", "bin", "clawd");
    }

    static void ensureCommand() {
        Path jar = Install.jar();
        if (jar == null) return;
        try {
            Path file = commandFile();
            String text = "#!/bin/sh\nexec \"" + Install.java() + "\" --enable-native-access=ALL-UNNAMED -cp \"" + jar + "\" clawdtop.Cli \"$@\"\n";
            Files.createDirectories(file.getParent());
            if (!Files.exists(file) || !Files.readString(file).equals(text)) Files.writeString(file, text, StandardCharsets.UTF_8);
            file.toFile().setExecutable(true);
            if (MAC) onTerminalPath(file.getParent());
        } catch (IOException e) {
            // no clawd command, then
        }
    }

    /**
     * A Mac's Terminal doesn't look in ~/.local/bin, so (once) a line goes in ~/.zprofile that adds it: then every new
     * Terminal window knows the clawd command.
     */
    private static void onTerminalPath(Path folder) {
        String path = System.getenv("PATH");
        if (path != null && java.util.Arrays.asList(path.split(":")).contains(folder.toString())) return; // (already there)
        Path profile = Path.of(System.getProperty("user.home"), ".zprofile");
        try {
            String had = Files.exists(profile) ? Files.readString(profile) : "";
            if (had.contains(".local/bin")) return;
            Files.writeString(profile, had + (had.isEmpty() || had.endsWith("\n") ? "" : "\n")
                    + "# added by Clawdtop, so Terminal knows the clawd command\nexport PATH=\"$HOME/.local/bin:$PATH\"\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            // no clawd command in Terminal, then (it's still at ~/.local/bin/clawd)
        }
    }

    static void removeCommand() {
        try {
            Files.deleteIfExists(commandFile());
        } catch (IOException e) {
            // already gone
        }
    }
}
