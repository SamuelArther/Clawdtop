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

    private static volatile String frontApp = "";
    private static Thread watcher;

    /** The app in front, by name (like "code" or "terminal"), or "" (looked up in the background every second). */
    static synchronized String frontApp() {
        if (watcher == null && !WINDOWS) {
            watcher = new Thread(() -> {
                while (true) {
                    frontApp = lookUpFrontApp();
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

    private static String lookUpFrontApp() {
        if (MAC) {
            return run("osascript", "-e", "tell application \"System Events\" to get name of first application process whose frontmost is true")
                    .toLowerCase(Locale.ROOT);
        }
        String pid = run("xdotool", "getactivewindow", "getwindowpid"); // X11 only; on Wayland this just says nothing
        if (!pid.matches("\\d+")) return "";
        try {
            return Files.readString(Path.of("/proc", pid, "comm")).strip().toLowerCase(Locale.ROOT);
        } catch (IOException e) {
            return "";
        }
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
            Path jar = Install.jar();
            if (jar == null) return;
            String java = Install.java().toString();
            String text = MAC
                    ? "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
                            + "<plist version=\"1.0\"><dict>\n<key>Label</key><string>com.clawdtop</string>\n"
                            + "<key>ProgramArguments</key><array><string>" + java + "</string><string>--enable-native-access=ALL-UNNAMED</string>"
                            + "<string>-jar</string><string>" + jar + "</string></array>\n<key>RunAtLoad</key><true/>\n</dict></plist>\n"
                    : "[Desktop Entry]\nType=Application\nName=Clawdtop\nComment=Clawd, on your taskbar\nExec=\"" + java
                            + "\" --enable-native-access=ALL-UNNAMED -jar \"" + jar + "\"\nX-GNOME-Autostart-enabled=true\n";
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // it just won't start by itself
        }
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
        } catch (IOException e) {
            // no clawd command, then
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
