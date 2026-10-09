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
        String real = System.getProperty("clawdtop.jar"); // (the clawd command runs from its own copy: this is the real one)
        if (real != null && !real.isBlank()) return Path.of(real);
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

    /**
     * The text of clawd.cmd: runs the command part of Clawdtop with this Java, from its own copy of the jar (so a clawd
     * command left open in a terminal never holds on to the real one and stops it being rebuilt or updated).
     */
    static String script(Path java, Path jar, Path copy) {
        Path console = java.getFileName().toString().equalsIgnoreCase("javaw.exe") ? java.resolveSibling("java.exe") : java;
        return "@echo off\r\n\"" + console + "\" --enable-native-access=ALL-UNNAMED -Dclawdtop.jar=\"" + jar + "\" -cp \"" + copy
                + "\" clawdtop.Cli %* & exit /b\r\n"; // (exit on the same line: clawd uninstall can delete this file mid-run)
    }

    /** The clawd command's own copy of the jar, freshened whenever he starts (unless a clawd command has it open). */
    private static void copyJar(Path jar, Path copy) {
        try {
            if (Files.exists(copy) && Files.size(copy) == Files.size(jar)
                    && Files.getLastModifiedTime(copy).equals(Files.getLastModifiedTime(jar))) return;
            Files.copy(jar, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException inUse) {
            // a clawd command is open right now: next time
        }
    }

    /** Makes sure the clawd command is there and points at this jar (quietly; called each time he starts). */
    static void ensureCommand() {
        Path jar = jar();
        Path folder = commandFolder();
        if (!Platform.WINDOWS) {
            Platform.ensureCommand();
            return;
        }
        if (jar == null || folder == null) return;
        try {
            Files.createDirectories(folder);
            Path cmd = folder.resolve("clawd.cmd");
            Path copy = folder.resolve("clawd-command.jar");
            copyJar(jar, copy);
            String text = script(java(), jar, copy);
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
        if (!Platform.WINDOWS) {
            Platform.removeCommand();
            return;
        }
        Path folder = commandFolder();
        if (folder == null) return;
        try {
            Files.deleteIfExists(folder.resolve("clawd.cmd"));
            Files.deleteIfExists(folder.resolve("clawd-command.jar"));
        } catch (IOException e) {
            // (the command's own copy is in use right now: it's tidied up just after, below)
        }
        try {
            // the clawd command is running from its copy of the jar: a moment after it finishes, that goes too
            new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command",
                    "Start-Sleep 3; Remove-Item -LiteralPath '" + folder.toString().replace("'", "''") + "' -Recurse -Force -ErrorAction SilentlyContinue; "
                            + "Remove-Item -LiteralPath '" + folder.getParent().toString().replace("'", "''") + "' -ErrorAction SilentlyContinue").start();
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
        // (read through PowerShell in UTF-8: reg.exe prints in the old code page, which mangles names like "José")
        String script = "[Console]::OutputEncoding = [Text.Encoding]::UTF8; $k = Get-Item 'HKCU:\\Environment'; "
                + "if ($k.GetValueNames() -contains 'Path') { 'VALUE:' + $k.GetValue('Path', '', [Microsoft.Win32.RegistryValueOptions]::DoNotExpandEnvironmentNames) } else { 'NONE' }";
        try {
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand",
                    java.util.Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE))).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            for (String line : out.split("\r?\n")) {
                if (line.equals("NONE")) return "";
                if (line.startsWith("VALUE:")) {
                    String value = line.substring(6);
                    return value.contains("\uFFFD") ? null : value; // (couldn't read it properly: leave it alone)
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Saves your own PATH (keeping %VARIABLES% working) and tells Windows, so new terminals see it. */
    static void setUserPath(String path) {
        if (path.contains("\uFFFD")) return; // never write back something that got mangled
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
