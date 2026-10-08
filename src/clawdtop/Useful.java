package clawdtop;

import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The useful side of Clawd: how the computer's doing, and opening things for you. */
final class Useful {
    private static final MethodHandle GET_TICK_COUNT_64;

    static {
        MethodHandle ticks = null;
        try {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
                ticks = Linker.nativeLinker().downcallHandle(
                        SymbolLookup.libraryLookup("kernel32", Arena.global()).find("GetTickCount64").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_LONG));
            }
        } catch (Throwable notHere) {
            ticks = null;
        }
        GET_TICK_COUNT_64 = ticks;
    }

    private Useful() {
    }

    /** How long since the computer last started (ms), or -1 if it can't tell. */
    static long uptime() {
        if (GET_TICK_COUNT_64 == null) return -1;
        try {
            return (long) GET_TICK_COUNT_64.invokeExact();
        } catch (Throwable e) {
            return -1;
        }
    }

    /** A drive that's nearly full (under 8% free), as "C:", or null. */
    static File nearlyFull() {
        for (File root : File.listRoots()) {
            long total = root.getTotalSpace();
            if (total < 8L << 30) continue; // skip little things like USB sticks and card readers
            if (root.getUsableSpace() * 100 / total < 8) return root;
        }
        return null;
    }

    /** "How's my computer?": a few lines about it, in plain words (and a little joke if it's all fine). */
    static String checkup() {
        List<String> lines = new ArrayList<>();
        long up = uptime();
        if (up >= 0) {
            long days = up / 86_400_000L, hours = up / 3_600_000L % 24;
            lines.add("On for " + (days > 0 ? days + (days == 1 ? " day " : " days ") : "") + hours + (hours == 1 ? " hour" : " hours")
                    + (days >= 7 ? " (it could use a restart!)" : ""));
        }
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            long total = os.getTotalMemorySize(), free = os.getFreeMemorySize();
            if (total > 0) lines.add("Memory: " + (total - free) * 100 / total + "% in use (" + Cleaner.size(free) + " free)");
            double cpu = os.getCpuLoad();
            if (cpu >= 0) lines.add("Processor: " + Math.round(cpu * 100) + "% busy");
        }
        for (File root : File.listRoots()) {
            long total = root.getTotalSpace();
            if (total < 8L << 30) continue;
            lines.add("Drive " + root.getPath().replace("\\", "") + " " + Cleaner.size(root.getUsableSpace()) + " free of " + Cleaner.size(total));
        }
        Power.State battery = Power.now();
        if (battery != null) lines.add("Battery: " + battery.percent() + "%" + (battery.pluggedIn() ? ", charging" : ""));
        boolean fine = (up < 0 || up < 7 * 86_400_000L) && nearlyFull() == null;
        return (fine ? "Your computer's doing great!" : "Here's how your computer's doing:") + "\n" + String.join("\n", lines);
    }

    /** A plain Windows message box (on its own thread, so nothing waits for it to be closed). */
    static void popup(String title, String text) {
        if (java.awt.GraphicsEnvironment.isHeadless()) return;
        Thread t = new Thread(() -> {
            try {
                MethodHandle box = Linker.nativeLinker().downcallHandle(
                        SymbolLookup.libraryLookup("user32", Arena.global()).find("MessageBoxW").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
                try (Arena arena = Arena.ofConfined()) {
                    int flags = 0x40 | 0x10000 | 0x40000; // information icon, in front, on top
                    int ignored = (int) box.invokeExact(java.lang.foreign.MemorySegment.NULL, wide(arena, text), wide(arena, title), flags);
                }
            } catch (Throwable notWindows) {
                javax.swing.SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(null, text, title,
                        javax.swing.JOptionPane.INFORMATION_MESSAGE));
            }
        }, "clawd-popup");
        t.setDaemon(true);
        t.start();
    }

    private static java.lang.foreign.MemorySegment wide(Arena arena, String s) {
        byte[] bytes = (s + "\0").getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
        java.lang.foreign.MemorySegment m = arena.allocate(bytes.length);
        m.copyFrom(java.lang.foreign.MemorySegment.ofArray(bytes));
        return m;
    }

    /** Things he can open for you, by name: folders, and handy Windows tools. */
    static final String[][] OPENABLE = {
            {"Downloads", "folder:Downloads"}, {"Desktop", "folder:Desktop"}, {"Documents", "folder:Documents"},
            {"Recycle Bin", "shell:RecycleBinFolder"}, {"Task Manager", "taskmgr"}, {"Calculator", "calc"},
            {"Notepad", "notepad"}, {"Settings", "ms-settings:"}, {"Snipping Tool", "ms-screenclip:"}};

    /** Opens one of those. */
    static void open(String what) {
        try {
            if (what.startsWith("folder:")) {
                Path folder = Path.of(System.getProperty("user.home"), what.substring(7));
                java.awt.Desktop.getDesktop().open(folder.toFile());
            } else if (what.startsWith("shell:") || what.contains(":")) {
                new ProcessBuilder("explorer.exe", what).start();
            } else {
                new ProcessBuilder(what).start();
            }
        } catch (Exception e) {
            // not on this computer
        }
    }
}
