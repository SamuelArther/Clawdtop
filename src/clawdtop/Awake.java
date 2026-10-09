package clawdtop;

import java.nio.charset.StandardCharsets;

/**
 * Keeping the computer awake (no sleeping, no screen going dark) while he holds his coffee: for a big download, a
 * movie, or a slideshow. A little helper program does it, and it stops by itself if he does.
 */
final class Awake {
    private Awake() {
    }

    private static Process helper;

    static synchronized boolean on() {
        return helper != null && helper.isAlive();
    }

    /** Starts keeping it awake. Whether it worked. */
    static synchronized boolean start() {
        if (on()) return true;
        long me = ProcessHandle.current().pid();
        try {
            if (Platform.WINDOWS) {
                String script = String.join("\n",
                        "Add-Type -Name Awake -Namespace Clawd -MemberDefinition '[DllImport(\"kernel32.dll\")] public static extern uint SetThreadExecutionState(uint f);'",
                        "[void][Clawd.Awake]::SetThreadExecutionState(0x80000003)", // (keep running, keep the screen on)
                        "while (Get-Process -Id " + me + " -ErrorAction SilentlyContinue) { Start-Sleep -Seconds 5 }");
                helper = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                        "-EncodedCommand", java.util.Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE))).start();
            } else if (Platform.MAC) {
                helper = new ProcessBuilder("caffeinate", "-di", "-w", String.valueOf(me)).start();
            } else {
                helper = new ProcessBuilder("systemd-inhibit", "--what=idle:sleep", "--who=Clawdtop", "--why=Keeping your computer awake",
                        "sh", "-c", "while kill -0 " + me + " 2>/dev/null; do sleep 5; done").start();
            }
            Thread.sleep(400);
            return helper.isAlive();
        } catch (Exception cant) {
            helper = null;
            return false;
        }
    }

    /** Lets it sleep again. */
    static synchronized void stop() {
        if (helper != null) helper.destroy();
        helper = null;
    }
}
