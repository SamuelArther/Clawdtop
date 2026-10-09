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

    /** Starts keeping it awake. Whether it worked. (The wait to see if it worked isn't locked: asking on() never stalls.) */
    static boolean start() {
        Process started;
        synchronized (Awake.class) {
            if (on()) return true;
            started = launch();
            helper = started;
            if (started == null) return false;
        }
        try {
            Thread.sleep(Platform.WINDOWS ? 2500 : 400); // (long enough for it to have failed, if it was going to)
        } catch (InterruptedException woken) {
            // see how it's doing now, then
        }
        synchronized (Awake.class) {
            if (started.isAlive()) return true;
            if (helper == started) helper = null;
            return false;
        }
    }

    /** The little helper program that holds the computer awake while Clawdtop's running (null if it couldn't start). */
    private static Process launch() {
        long me = ProcessHandle.current().pid();
        try {
            if (Platform.WINDOWS) {
                String script = String.join("\n",
                        "$ErrorActionPreference = 'Stop'", // (if anything fails, it stops: he never says it's awake when it isn't)
                        "Add-Type -Name Awake -Namespace Clawd -MemberDefinition '[DllImport(\"kernel32.dll\")] public static extern uint SetThreadExecutionState(uint f);'",
                        // (keep running, keep the screen on: ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_DISPLAY_REQUIRED, as a uint: PowerShell reads 0x80000003 as a negative int)
                        "if ([Clawd.Awake]::SetThreadExecutionState([uint32]2147483651) -eq 0) { exit 1 }",
                        "while (Get-Process -Id " + me + " -ErrorAction SilentlyContinue) { Start-Sleep -Seconds 5 }");
                return new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                        "-EncodedCommand", java.util.Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE))).start();
            } else if (Platform.MAC) {
                return new ProcessBuilder("caffeinate", "-di", "-w", String.valueOf(me)).start();
            } else {
                return new ProcessBuilder("systemd-inhibit", "--what=idle:sleep", "--who=Clawdtop", "--why=Keeping your computer awake",
                        "sh", "-c", "while kill -0 " + me + " 2>/dev/null; do sleep 5; done").start();
            }
        } catch (Exception cant) {
            return null;
        }
    }

    /** Lets it sleep again. */
    static synchronized void stop() {
        if (helper != null) helper.destroy();
        helper = null;
    }
}
