package clawdtop;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Clawd's ears, once you say he may: how loud the computer's sound is right now (0 silent to 1 as loud as it goes),
 * ten times a second. Only the loudness: never what's being said or played, and nothing's ever recorded or sent.
 * On Windows it asks Windows' own sound meter (through a small hidden PowerShell); on Linux, the sound server's
 * monitor (if parec is there). Macs don't let apps listen to what's playing, so there it stays off.
 */
final class Hearing {
    private Process process;
    private volatile double level;
    private volatile long heardAt;

    /** Whether hearing can work on this computer at all. */
    static boolean possible() {
        return Platform.WINDOWS || (!Platform.MAC && Platform.onPath("parec"));
    }

    /** Starts listening (if it isn't already). */
    synchronized void start() {
        if (process != null && process.isAlive()) return;
        try {
            if (Platform.WINDOWS) {
                String script = meterScript(ProcessHandle.current().pid());
                process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                        "-EncodedCommand", java.util.Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE)))
                        .redirectErrorStream(true).start();
                readLevels(process);
            } else if (possible()) {
                process = new ProcessBuilder("parec", "-d", "@DEFAULT_MONITOR@", "--raw", "--format=s16le", "--rate=8000", "--channels=1")
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                readPcm(process);
            }
        } catch (Exception cantHear) {
            process = null;
        }
    }

    synchronized void stop() {
        if (process != null) process.destroy();
        process = null;
        level = 0;
    }

    /** How loud it is right now, 0 to 1 (0 if he isn't listening, or hasn't heard anything lately). */
    double level() {
        return System.currentTimeMillis() - heardAt > 1500 ? 0 : level;
    }

    boolean listening() {
        Process p = process;
        return p != null && p.isAlive();
    }

    private void readLevels(Process p) {
        Thread t = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = in.readLine()) != null; ) {
                    try {
                        double v = Double.parseDouble(line.strip());
                        if (v >= 0) {
                            level = Math.min(1, v);
                            heardAt = System.currentTimeMillis();
                        }
                    } catch (NumberFormatException notANumber) {
                        // (PowerShell saying something else)
                    }
                }
            } catch (Exception gone) {
                // stopped
            }
        }, "Clawdtop ears");
        t.setDaemon(true);
        t.start();
    }

    private void readPcm(Process p) {
        Thread t = new Thread(() -> {
            byte[] buffer = new byte[1600]; // a tenth of a second at 8000 samples a second
            try (var in = p.getInputStream()) {
                for (int n; (n = in.readNBytes(buffer, 0, buffer.length)) > 0; ) {
                    int peak = 0;
                    for (int i = 0; i + 1 < n; i += 2) peak = Math.max(peak, Math.abs((short) ((buffer[i] & 0xFF) | buffer[i + 1] << 8)));
                    level = peak / 32768.0;
                    heardAt = System.currentTimeMillis();
                }
            } catch (Exception gone) {
                // stopped
            }
        }, "Clawdtop ears");
        t.setDaemon(true);
        t.start();
    }

    /** Windows' sound meter (IAudioMeterInformation on the default speakers), read ten times a second; stops with Clawd. */
    static String meterScript(long clawdPid) {
        String cs = String.join("\n",
                "using System; using System.Runtime.InteropServices;",
                "[ComImport, Guid(\"BCDE0395-E52F-467C-8E3D-C4579291692E\")] class ClawdDevices {}",
                "[InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid(\"A95664D2-9614-4F35-A746-DE8DB63617E6\")]",
                "interface IClawdEnumerator { int NotUsed(); [PreserveSig] int GetDefaultAudioEndpoint(int flow, int role, out IClawdDevice device); }",
                "[InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid(\"D666063F-1587-4E43-81F1-B948E807363F\")]",
                "interface IClawdDevice { [PreserveSig] int Activate(ref Guid iid, int context, IntPtr options, [MarshalAs(UnmanagedType.IUnknown)] out object thing); }",
                "[InterfaceType(ComInterfaceType.InterfaceIsIUnknown), Guid(\"C02216F6-8C67-4B5B-9D00-D008E73E0064\")]",
                "interface IClawdMeter { [PreserveSig] int GetPeakValue(out float peak); }",
                "public static class ClawdEars {",
                "  static IClawdMeter meter;",
                "  public static void Reset() { meter = null; }",
                "  public static float Peak() {",
                "    if (meter == null) {",
                "      IClawdEnumerator e = (IClawdEnumerator) new ClawdDevices(); IClawdDevice d;",
                "      if (e.GetDefaultAudioEndpoint(0, 1, out d) != 0) return -1;",
                "      Guid iid = typeof(IClawdMeter).GUID; object o;",
                "      if (d.Activate(ref iid, 23, IntPtr.Zero, out o) != 0) return -1;",
                "      meter = (IClawdMeter) o;",
                "    }",
                "    float p; meter.GetPeakValue(out p); return p;",
                "  }",
                "}");
        return String.join("\n",
                "Add-Type -TypeDefinition @'", cs, "'@",
                "$i = 0",
                "while ($true) {",
                "  try { [Console]::WriteLine([ClawdEars]::Peak().ToString([Globalization.CultureInfo]::InvariantCulture)) } catch { [ClawdEars]::Reset() }",
                "  $i++",
                "  if ($i % 50 -eq 0) {", // every five seconds: still wanted? (and the speakers may have changed)
                "    [ClawdEars]::Reset()",
                "    try { [void][Diagnostics.Process]::GetProcessById(" + clawdPid + ") } catch { exit }",
                "  }",
                "  Start-Sleep -Milliseconds 100",
                "}");
    }
}
