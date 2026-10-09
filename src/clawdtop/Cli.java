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

    /** Windows terminals: UTF-8 while he talks (so his little picture doesn't come out as gibberish), then back after. */
    private static void utf8Console() {
        if (!Platform.WINDOWS) return;
        try {
            java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
            java.lang.foreign.SymbolLookup k32 = java.lang.foreign.SymbolLookup.libraryLookup("kernel32", java.lang.foreign.Arena.global());
            java.lang.foreign.FunctionDescriptor get = java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_INT);
            java.lang.foreign.FunctionDescriptor set = java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT);
            java.lang.invoke.MethodHandle getOut = linker.downcallHandle(k32.find("GetConsoleOutputCP").orElseThrow(), get);
            java.lang.invoke.MethodHandle getIn = linker.downcallHandle(k32.find("GetConsoleCP").orElseThrow(), get);
            java.lang.invoke.MethodHandle setOut = linker.downcallHandle(k32.find("SetConsoleOutputCP").orElseThrow(), set);
            java.lang.invoke.MethodHandle setIn = linker.downcallHandle(k32.find("SetConsoleCP").orElseThrow(), set);
            int oldOut = (int) getOut.invokeExact(), oldIn = (int) getIn.invokeExact();
            if (oldOut == 0 || oldOut == 65001) return; // no console, or it's UTF-8 already
            int ok = (int) setOut.invokeExact(65001);
            ok = (int) setIn.invokeExact(65001);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    int back = (int) setOut.invokeExact(oldOut);
                    back = (int) setIn.invokeExact(oldIn);
                } catch (Throwable ignored) {
                    // (the terminal's closing anyway)
                }
            }));
        } catch (Throwable noConsole) {
            // then it's as it was
        }
    }

    public static void main(String[] args) throws Exception {
        utf8Console();
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        Cli cli = new Cli(out, in);
        if (args.length > 1) cli.words = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        System.exit(cli.run(args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT)));
    }

    String words = ""; // whatever came after the command (clawd ask why is the sky blue)

    /** clawd ask: quick answers and math straight away; anything else, his brain (installing it first if it has to). */
    private void ask() throws IOException {
        String q = words.strip();
        if (q.isEmpty()) {
            out.print("Ask me anything: ");
            out.flush();
            String line = in.readLine();
            q = line == null ? "" : line.strip();
        }
        if (q.isEmpty()) return;
        String quick = QuickAnswers.answer(q, new java.util.Random());
        if (quick != null) {
            out.println(ORANGE + "Clawd: " + RESET + quick);
            return;
        }
        MathHelp.Problem sum = MathHelp.parse(q);
        if (sum != null) {
            double v = sum.properly();
            String shown = v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v) : String.valueOf(v);
            out.println(ORANGE + "Clawd: " + RESET + "I wouldn't trust myself... but the computer says " + shown + ".");
            return;
        }
        Settings s = Settings.load();
        Brain brain = new Brain();
        String model = Brain.model(s.choice("brain"));
        if (!brain.running() || !brain.has(model)) {
            out.println(DIM + "(getting my brain ready first...)" + RESET);
            if (!BrainInstall.ensure(brain, model, note -> out.println(DIM + note.replace("\n", " ") + RESET))) return;
        }
        WebSearch.Found found = s.on("webSearch") ? WebSearch.lookUp(q) : null;
        out.println(DIM + "(thinking...)" + RESET);
        String reply = brain.ask(q, model, s.personality(), s.on("kidFriendly"), s.name(), found);
        out.println(ORANGE + "Clawd: " + RESET + (reply == null ? "Hmm... my brain froze. Try again?" : reply));
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
            case "move" -> move();
            case "creations", "made" -> creations();
            case "ask" -> ask();
            case "diary" -> {
                java.util.List<String> lines = Diary.read(3);
                hello("Clawd's diary");
                if (lines.isEmpty()) out.println("\n  Nothing yet! Spend some time with me and check back.");
                else for (String l : lines) out.println(l.endsWith(":") ? ORANGE + l + RESET : l);
            }
            case "joke" -> out.println(new Jokes(System.nanoTime()).next());
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
        out.println("  clawd move           move Clawd to another computer on your wifi");
        out.println("  clawd creations      the little programs he's coded");
        out.println("  clawd ask \"...\"      ask him something, right here in the terminal");
        out.println("  clawd joke           a joke");
        out.println("  clawd diary          what he got up to lately, in his own words");
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
        out.println("  Lives in:    " + (s.homeNamed() ? s.home() : DIM + "(this computer has no name yet)" + RESET));
        if (!s.oldHomes().isEmpty()) out.println("  Lived in:    " + String.join(", ", s.oldHomes()));
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
            out.println("  12  Your birthday       " + (s.birthday().isEmpty() ? DIM + "(not set)" + RESET : s.birthday().replace('-', '/')));
            out.println("  13  All the options     " + DIM + "(" + Options.ALL.size() + " of them!)" + RESET);
            out.println("  14  His home's name     " + (s.homeNamed() ? s.home() : DIM + "(none)" + RESET));
            out.println("  15  No tomfoolery       " + onOff(s.serious()) + DIM + "  (serious mode: no silly stuff)" + RESET);
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
                case "13" -> allOptions();
                case "15" -> s.set("serious", String.valueOf(!s.serious()));
                case "14" -> {
                    out.print("What should he call this computer? (like " + s.suggestedHome() + ") ");
                    out.flush();
                    String home = in.readLine();
                    if (home != null && !home.isBlank()) s.setHome(home);
                }
                case "12" -> {
                    out.print("  Your birthday, month/day like 10/08 (blank for none): ");
                    out.flush();
                    String typed = in.readLine();
                    if (typed == null || typed.isBlank()) s.setBirthday("");
                    else {
                        String[] parts = typed.strip().replace('-', '/').split("/");
                        try {
                            int m = Integer.parseInt(parts[0]), d = Integer.parseInt(parts[1]);
                            if (m >= 1 && m <= 12 && d >= 1 && d <= 31) s.setBirthday(String.format("%02d-%02d", m, d));
                            else out.println("  That's not a date.");
                        } catch (RuntimeException e) {
                            out.println("  That's not a date like 10/08.");
                        }
                    }
                }
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

    /** Every option, in groups: pick one by number to flip it, pick the next choice, or type a number. */
    private void allOptions() throws IOException {
        while (true) {
            Settings s = Settings.load();
            out.println();
            String group = "";
            for (int i = 0; i < Options.ALL.size(); i++) {
                Options.Option o = Options.ALL.get(i);
                if (!o.group().equals(group)) {
                    group = o.group();
                    out.println(ORANGE + "  " + group + RESET);
                }
                String value = switch (o.kind()) {
                    case SWITCH -> onOff(s.on(o.key()));
                    case CHOICE -> s.choice(o.key());
                    case NUMBER -> String.valueOf(s.number(o.key()));
                };
                out.printf("  %3d  %-52s %s%n", i + 1, o.label(), value);
            }
            out.print("\nPick a number to change (0 to go back): ");
            out.flush();
            int i = number(in.readLine()) - 1;
            if (i < 0 || i >= Options.ALL.size()) return;
            Options.Option o = Options.ALL.get(i);
            switch (o.kind()) {
                case SWITCH -> s.set(o.key(), String.valueOf(!s.on(o.key())));
                case CHOICE -> {
                    int k = choose(o.choices().toArray(String[]::new));
                    if (k >= 0) s.set(o.key(), o.choices().get(k));
                }
                case NUMBER -> {
                    out.print("  " + o.label() + " (" + o.min() + " to " + o.max() + "): ");
                    out.flush();
                    int n = number(in.readLine());
                    if (n >= o.min() && n <= o.max()) s.set(o.key(), String.valueOf(n));
                    else out.println("  That's not between " + o.min() + " and " + o.max() + ".");
                }
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

    /** Moves him to another computer: it's showing a code in its setup; this sends his save token across. */
    /** The little programs he's coded, and where they are. */
    private void creations() throws IOException {
        Settings s = Settings.load();
        hello("Things Clawd has coded");
        out.println();
        Path folder = Settings.creations();
        java.util.List<Path> files = new java.util.ArrayList<>();
        if (Files.isDirectory(folder)) {
            try (var all = Files.list(folder)) {
                all.sorted().forEach(files::add);
            }
        }
        if (files.isEmpty()) {
            out.println("  Nothing yet. (Left-click him and pick \"Make something!\", or wait. He gets ideas.)");
        } else {
            for (Path f : files) out.println("  " + f.getFileName() + DIM + "  (" + Files.size(f) + " bytes)" + RESET);
            out.println();
            out.println("  They're in " + folder);
        }
        out.println("  He's made " + s.made().size() + " of the " + Creation.ALL.size() + " things he knows how to make."
                + (s.made().size() > files.size() ? DIM + " (The ones that went wrong, he deleted.)" + RESET : ""));
    }

    private void move() throws IOException {
        Settings here = Settings.load();
        hello("Moving Clawd " + (here.homeNamed() ? "out of " + here.home() : "to another computer"));
        out.println();
        out.println("On the new computer, start Clawdtop and pick \"Moving from another computer\". It shows a code.");
        out.print("Type the code: ");
        out.flush();
        String code = in.readLine();
        if (code == null || !code.strip().matches("\\d{4}")) {
            out.println("That's not a 4-digit code.");
            return;
        }
        out.println("Looking for it on your wifi...");
        java.net.InetAddress there = Transfer.find(code.strip(), Transfer.FIND_PORT, 10_000);
        if (there == null) {
            out.println("I couldn't find it. Both computers need to be on the same wifi, with the new one showing that code.");
            return;
        }
        if (!Transfer.send(there, Transfer.MOVE_PORT, code.strip(), Settings.load().saveToken())) {
            out.println("It didn't take him. Check the code and try again.");
            return;
        }
        out.println("Sent! He's moving to the new computer.");
        Optional<ProcessHandle> clawd = running();
        if (clawd.isPresent()) {
            Settings.ask("moveout"); // he picks up a box and walks off the screen
            try {
                clawd.get().onExit().get(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception slow) {
                stop(false);
            }
        }
        out.print("Remove him from this computer now? (y/N) ");
        out.flush();
        String answer = in.readLine();
        if (answer != null && answer.strip().toLowerCase(Locale.ROOT).startsWith("y")) {
            Startup.set(false);
            Install.removeCommand();
            Path folder = Settings.folder();
            if (Files.isDirectory(folder)) {
                try (var files = Files.list(folder)) {
                    for (Path f : files.toList()) Files.deleteIfExists(f);
                }
                Files.deleteIfExists(folder);
            }
            out.println("Done. He lives on the new computer now.");
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
