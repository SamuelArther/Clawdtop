package clawdtop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * "Start with Windows": a tiny script in Windows' Startup folder that starts Clawdtop (with no console window) when
 * you sign in. Turning it off deletes the script.
 */
final class Startup {
    private Startup() {
    }

    private static Path script() {
        String appData = System.getenv("APPDATA");
        if (appData == null) return null;
        return Path.of(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup", "Clawdtop.vbs");
    }

    static boolean on() {
        Path script = script();
        return script != null && Files.exists(script);
    }

    static void set(boolean on) {
        Path script = script();
        if (script == null) return;
        try {
            if (!on) {
                Files.deleteIfExists(script);
                return;
            }
            String text = command();
            if (text != null) Files.writeString(script, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Windows said no: it just doesn't start by itself
        }
    }

    /** The script's text, or null when Clawdtop isn't running from its jar (like while it's being worked on). */
    static String command() {
        try {
            Path jar = Path.of(Clawdtop.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!jar.toString().endsWith(".jar")) return null;
            Path java = Path.of(ProcessHandle.current().info().command().orElse("javaw"));
            Path javaw = java.resolveSibling("javaw.exe");
            if (Files.exists(javaw)) java = javaw; // no console window
            return script(java.toString(), jar.toString());
        } catch (Exception e) {
            return null;
        }
    }

    static String script(String java, String jar) {
        return "Set shell = CreateObject(\"WScript.Shell\")\r\n"
                + "shell.Run \"\"\"" + java + "\"\" --enable-native-access=ALL-UNNAMED -jar \"\"" + jar + "\"\"\", 0, False\r\n";
    }
}
