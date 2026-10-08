package clawdtop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * The "clawd" command, for terminals: clawd start, stop, restart, status, uninstall, and clawd controlpanel for a
 * little menu of his settings. Clawd notices changed settings within a few seconds, so there's no restart needed.
 */
public final class Cli {
    private static final String ORANGE = "\u001b[38;2;215;119;87m";
    private static final String DIM = "\u001b[2m";
    private static final String RESET = "\u001b[0m";
    private static final String[] FACE = {" ▐▛███▜▌", "▝▜█████▛▘", "  ▘▘ ▝▝"};

    private final PrintStream out;
    private final BufferedReader in;

    Cli(PrintStream out, BufferedReader in) {
        this.out = out;
        this.in = in;
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.exit(new Cli(out, in).run(args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT)));
    }

    int run(String command) throws IOException {
        switch (command) {
            case "start" -> start();
            case "stop" -> stop(true);
            case "restart" -> {
                stop(false);
                start();
            }
            case "status" -> status();
            case "controlpanel", "control", "panel", "settings" -> controlPanel();
            case "uninstall" -> uninstall();
            case "help", "-h", "--help", "/?" -> help();
            default -> {
                out.println("I don't know \"" + command + "\". Here's what I can do:");
                help();
                return 1;
            }
        }
        return 0;
    }

    private void hello(String line) {
        out.println(ORANGE + FACE[0] + RESET + "   " + line);
        out.println(ORANGE + FACE[1] + RESET);
        out.println(ORANGE + FACE[2] + RESET);
    }

    private void help() {
        hello("Clawd's commands");
        out.println();
        out.println("  clawd start          bring Clawd to your taskbar");
        out.println("  clawd stop           send him off for now");
        out.println("  clawd restart        stop, then start");
        out.println("  clawd status         is he running?");
        out.println("  clawd controlpanel   change his settings");
        out.println("  clawd uninstall      remove Clawd from this computer");
    }

    /** The running Clawd, if there is one. */
    static Optional<ProcessHandle> running() {
        try {
            Path pid = Settings.folder().resolve("running.pid");
            if (!Files.exists(pid)) return Optional.empty();
            long id = Long.parseLong(Files.readString(pid).strip());
            return ProcessHandle.of(id).filter(ProcessHandle::isAlive)
                    .filter(p -> p.info().command().map(c -> c.toLowerCase(Locale.ROOT).contains("java")).orElse(true));
        } catch (IOException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void start() throws IOException {
        if (running().isPresent()) {
            out.println("Clawd's already on your taskbar.");
            return;
        }
        Path jar = Install.jar();
        if (jar == null) {
            out.println("I can't find Clawdtop.jar. Build it with build.bat first.");
            return;
        }
        new ProcessBuilder(Install.javaw().toString(), "--enable-native-access=ALL-UNNAMED", "-jar", jar.toString()).start();
        out.println("Clawd's on his way to your taskbar!");
    }

    private void stop(boolean say) {
        Optional<ProcessHandle> clawd = running();
        if (clawd.isEmpty()) {
            if (say) out.println("Clawd isn't running.");
            return;
        }
        clawd.get().destroy();
        try {
            clawd.get().onExit().get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception slow) {
            clawd.get().destroyForcibly();
        }
        if (say) out.println("Bye for now! (clawd start brings him back)");
    }

    private void status() {
        Settings s = Settings.load();
        Optional<ProcessHandle> clawd = running();
        hello(clawd.isPresent() ? "Clawd is on your taskbar." : "Clawd isn't running. (clawd start)");
        out.println();
        out.println("  Name:        " + (s.name().isEmpty() ? DIM + "(not told yet)" + RESET : s.name()));
        out.println("  Sits:        " + (s.x() >= 0 ? "where you dragged him" : s.spot().toLowerCase(Locale.ROOT)));
        out.println("  Size:        " + s.size().toLowerCase(Locale.ROOT));
        out.println("  Beeps:       " + onOff(s.sounds()));
        out.println("  Tips:        " + onOff(s.tips()));
        out.println("  With Windows:" + " " + onOff(Startup.on()));
    }

    private static String onOff(boolean on) {
        return on ? "on" : "off";
    }

    /** Colors he can be, by name. */
    static final String[][] COLORS = {{"Orange (his own)", "#D77757"}, {"Blue", "#5B8DEF"}, {"Green", "#5CB85C"},
            {"Pink", "#F28AB2"}, {"Purple", "#9B6BDF"}, {"Yellow", "#E8C547"}, {"Red", "#D9534F"}, {"Gray", "#9AA0A6"}};

    private void controlPanel() throws IOException {
        while (true) {
            Settings s = Settings.load();
            boolean on = running().isPresent();
            out.println();
            hello(ORANGE + "Clawd's control panel" + RESET + (on ? "" : DIM + "  (he isn't running: clawd start)" + RESET));
            out.println();
            out.println("   1  Name                " + (s.name().isEmpty() ? DIM + "(none)" + RESET : s.name()));
            out.println("   2  Where he sits       " + (s.x() >= 0 ? "where you dragged him" : s.spot()));
            out.println("   3  Size                " + s.size());
            out.println("   4  Personality         " + s.personality().shown());
            out.println("   5  Color               " + colorName(s.color()));
            out.println("   6  Mood right now      (happy, sleepy, asleep, awake)");
            out.println("   7  Beeps               " + onOff(s.sounds()));
            out.println("   8  Tips                " + onOff(s.tips()));
            out.println("  11  Jokes               " + s.jokes());
            out.println("   9  Start with Windows  " + onOff(Startup.on()));
            out.println("  10  " + (on ? "Stop him" : "Start him"));
            out.println("   0  Done");
            out.print("\nPick a number: ");
            out.flush();
            String pick = in.readLine();
            if (pick == null) return;
            switch (pick.strip()) {
                case "1" -> {
                    out.print("What should he call you? ");
                    out.flush();
                    String name = in.readLine();
                    if (name != null) s.setName(name.length() > 24 ? name.substring(0, 24) : name);
                }
                case "2" -> {
                    int i = choose(Welcome.SPOTS);
                    if (i >= 0) s.setSpot(Welcome.SPOTS[i]);
                }
                case "3" -> s.setSize(switch (s.size()) {
                    case "Small" -> "Normal";
                    case "Normal" -> "Big";
                    default -> "Small";
                });
                case "4" -> {
                    Pet.Personality[] all = Pet.Personality.values();
                    String[] names = new String[all.length];
                    for (int k = 0; k < all.length; k++) names[k] = all[k].shown();
                    int i = choose(names);
                    if (i >= 0) s.setPersonality(all[i]);
                }
                case "5" -> {
                    String[] names = new String[COLORS.length + 1];
                    for (int k = 0; k < COLORS.length; k++) names[k] = COLORS[k][0];
                    names[COLORS.length] = "Any color (#RRGGBB)";
                    int i = choose(names);
                    if (i >= 0 && i < COLORS.length) s.setColor(COLORS[i][1]);
                    if (i == COLORS.length) {
                        out.print("  Color, like #33AAFF: ");
                        out.flush();
                        String hex = in.readLine();
                        if (hex != null && hex.strip().matches("#?[0-9A-Fa-f]{6}")) {
                            s.setColor("#" + hex.strip().replace("#", "").toUpperCase(Locale.ROOT));
                        } else {
                            out.println("  That's not a color like #33AAFF.");
                        }
                    }
                    if (i >= 0 && on) out.println("  " + DIM + "(give him a few seconds...)" + RESET);
                }
                case "6" -> {
                    String[] moods = {"happy", "sleepy", "asleep", "awake"};
                    int i = choose(new String[] {"Happy", "Sleepy (lie down)", "Asleep", "Wake up"});
                    if (i >= 0) {
                        if (on) Settings.ask("mood " + moods[i]);
                        else out.println("  He isn't running, so he can't hear you. (clawd start)");
                    }
                }
                case "7" -> s.setSounds(!s.sounds());
                case "8" -> s.setTips(!s.tips());
                case "9" -> Startup.set(!Startup.on());
                case "11" -> {
                    String[] often = {"Off", "Rare", "Sometimes", "Lots"};
                    int i = choose(often);
                    if (i >= 0) s.setJokes(often[i]);
                }
                case "10" -> {
                    if (on) stop(true);
                    else start();
                }
                case "0", "q", "quit", "exit", "" -> {
                    return;
                }
                default -> out.println("That's not one of the numbers.");
            }
        }
    }

    /** Lists choices, numbered from 1; the one picked (from 0), or -1. */
    private int choose(String[] choices) throws IOException {
        for (int i = 0; i < choices.length; i++) out.println("     " + (i + 1) + "  " + choices[i]);
        out.print("   Which? ");
        out.flush();
        int i = number(in.readLine()) - 1;
        return i >= 0 && i < choices.length ? i : -1;
    }

    static String colorName(String hex) {
        for (String[] c : COLORS) if (c[1].equalsIgnoreCase(hex)) return c[0];
        return hex;
    }

    private static int number(String text) {
        try {
            return Integer.parseInt(text == null ? "" : text.strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void uninstall() throws IOException {
        hello("Remove Clawd from this computer?");
        out.print("\nThis stops him, stops him starting with Windows, removes the clawd command, and forgets his settings. Sure? (y/N) ");
        out.flush();
        String answer = in.readLine();
        if (answer == null || !answer.strip().toLowerCase(Locale.ROOT).startsWith("y")) {
            out.println("Phew! He's staying.");
            return;
        }
        Settings s = Settings.load();
        out.println();
        out.println("Here's his save token. Keep it somewhere safe: paste it in when you set him up again, and he'll remember you.");
        out.println();
        out.println("    " + ORANGE + s.saveToken() + RESET);
        out.println();
        Optional<ProcessHandle> clawd = running();
        if (clawd.isPresent()) {
            out.println(DIM + "(look at your taskbar)" + RESET);
            Settings.ask("goodbye"); // he says bye, crumbles away, and closes himself
            try {
                clawd.get().onExit().get(15, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception slow) {
                stop(false);
            }
        }
        Startup.set(false);
        Install.removeCommand();
        Path folder = Settings.folder();
        if (Files.isDirectory(folder)) {
            try (var files = Files.list(folder)) {
                for (Path f : files.toList()) Files.deleteIfExists(f);
            }
            Files.deleteIfExists(folder);
        }
        out.println("...he's gone. (To remove the program too, delete the Clawdtop folder.)");
    }
}
