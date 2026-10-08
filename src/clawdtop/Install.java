package clawdtop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Puts the "clawd" command in your terminal: a little clawd.cmd in your own app folder, and that folder on your own
 * (user) PATH. Only your account's settings change, nothing system-wide, and clawd uninstall takes it all back out.
 */
final class Install {
    private Install() {
    }

    /** Clawdtop.jar, if that's what's running (not while he's being worked on from loose classes). */
    static Path jar() {
        try {
            Path p = Path.of(Install.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return p.toString().endsWith(".jar") ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    static Path java() {
        return Path.of(ProcessHandle.current().info().command().orElse("java"));
    }

    /** javaw.exe next to the running Java (no console window), or the running Java itself. */
    static Path javaw() {
        Path w = java().resolveSibling("javaw.exe");
        return Files.exists(w) ? w : java();
    }

    static Path commandFolder() {
        String local = System.getenv("LOCALAPPDATA");
        return local == null ? null : Path.of(local, "Clawdtop", "bin");
    }

    /** The text of clawd.cmd: runs the command part of Clawdtop with this Java and this jar. */
    static String script(Path java, Path jar) {
        Path console = java.getFileName().toString().equalsIgnoreCase("javaw.exe") ? java.resolveSibling("java.exe") : java;
        return "@echo off\r\n\"" + console + "\" --enable-native-access=ALL-UNNAMED -cp \"" + jar + "\" clawdtop.Cli %*\r\n";
    }

    /** Makes sure the clawd command is there and points at this jar (quietly; called each time he starts). */
    static void ensureCommand() {
        Path jar = jar();
        Path folder = commandFolder();
        if (jar == null || folder == null || !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) return;
        try {
            Files.createDirectories(folder);
            Path cmd = folder.resolve("clawd.cmd");
            String text = script(java(), jar);
            if (!Files.exists(cmd) || !Files.readString(cmd).equals(text)) Files.writeString(cmd, text, StandardCharsets.UTF_8);
            String path = userPath();
            if (path != null && !hasEntry(path, folder.toString())) {
                setUserPath(path.isEmpty() ? folder.toString() : path + (path.endsWith(";") ? "" : ";") + folder);
            }
        } catch (IOException e) {
            // no command this time: he still works
        }
    }

    /** Takes the clawd command back out (clawd uninstall). */
    static void removeCommand() {
        Path folder = commandFolder();
        if (folder == null) return;
        try {
            Files.deleteIfExists(folder.resolve("clawd.cmd"));
            Files.deleteIfExists(folder);
            Files.deleteIfExists(folder.getParent());
        } catch (IOException e) {
            // leave whatever Windows won't let go of
        }
        String path = userPath();
        if (path != null && hasEntry(path, folder.toString())) setUserPath(withoutEntry(path, folder.toString()));
        try {
            new ProcessBuilder("reg", "delete", "HKCU\\Environment", "/v", "CLAWDTOP", "/f").redirectErrorStream(true).start().waitFor();
        } catch (Exception e) {
            // it was only a signal anyway
        }
    }

    static boolean hasEntry(String path, String folder) {
        for (String part : path.split(";")) if (part.strip().equalsIgnoreCase(folder)) return true;
        return false;
    }

    static String withoutEntry(String path, String folder) {
        List<String> kept = new ArrayList<>();
        for (String part : path.split(";")) if (!part.isBlank() && !part.strip().equalsIgnoreCase(folder)) kept.add(part);
        return String.join(";", kept);
    }

    /** Your own PATH exactly as stored (with any %VARIABLES% left as they are), "" if you have none, null if unknown. */
    static String userPath() {
        try {
            Process p = new ProcessBuilder("reg", "query", "HKCU\\Environment", "/v", "Path").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = p.waitFor();
            if (code != 0) return out.contains("unable to find") || out.contains("ERROR") ? "" : null;
            for (String line : out.split("\r?\n")) {
                String t = line.strip();
                if (t.startsWith("Path ") || t.startsWith("PATH ")) {
                    int at = t.indexOf("REG_");
                    int value = t.indexOf("    ", at);
                    return value < 0 ? "" : t.substring(value).strip();
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Saves your own PATH (keeping %VARIABLES% working) and tells Windows, so new terminals see it. */
    static void setUserPath(String path) {
        if (path.endsWith("\\")) path += ";"; // a backslash right before the closing quote would confuse Windows' quoting
        try {
            new ProcessBuilder("reg", "add", "HKCU\\Environment", "/v", "Path", "/t", "REG_EXPAND_SZ", "/d", path, "/f")
                    .redirectErrorStream(true).start().waitFor();
            // setx always tells running programs that the environment changed; this one variable is just for that
            new ProcessBuilder("setx", "CLAWDTOP", "1").redirectErrorStream(true).start().waitFor();
        } catch (Exception e) {
            // no clawd command in new terminals this time
        }
    }
}
