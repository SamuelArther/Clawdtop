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
        if (!Platform.WINDOWS) return Platform.startsAtLogin();
        Path script = script();
        return script != null && Files.exists(script);
    }

    static void set(boolean on) {
        if (!Platform.WINDOWS) {
            Platform.setStartsAtLogin(on);
            return;
        }
        Path script = script();
        if (script == null) return;
        try {
            if (!on) {
                Files.deleteIfExists(script);
                return;
            }
            String text = command();
            if (text != null) write(script, text);
        } catch (IOException e) {
            // Windows said no: it just doesn't start by itself
        }
    }

    /** Saved in UTF-16 with a byte-order mark: the only way Windows Script Host reads names like "José" right. */
    private static void write(Path script, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_16LE);
        byte[] all = new byte[body.length + 2];
        all[0] = (byte) 0xFF;
        all[1] = (byte) 0xFE;
        System.arraycopy(body, 0, all, 2, body.length);
        Files.write(script, all);
    }

    /** If it's on, makes sure the script still points at this Java and this jar (they can move, like after an update). */
    static void refresh() {
        if (!Platform.WINDOWS || !on()) return;
        Path script = script();
        String text = command();
        if (script == null || text == null) return;
        try {
            byte[] now = Files.readAllBytes(script);
            String was = now.length >= 2 && (now[0] & 0xFF) == 0xFF && (now[1] & 0xFF) == 0xFE
                    ? new String(now, 2, now.length - 2, StandardCharsets.UTF_16LE) : new String(now, StandardCharsets.UTF_8);
            if (!was.equals(text)) write(script, text);
        } catch (IOException e) {
            // leave it
        }
    }

    /** The script's text, or null when Clawdtop isn't running from its jar (like while it's being worked on). */
    static String command() {
        try {
            Path jar = Install.jar(); // (the real jar, even when asked from the clawd command's own copy)
            if (jar == null || !jar.toString().endsWith(".jar")) return null;
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
