package clawdtop;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Windows only: the window in front (just its number, cheap enough to ask every frame), whose it is, and making an
 * app's window see-through for a moment and back (for the taskbar tackle). Everything is harmless if it fails.
 */
final class WindowTricks {
    private static final int GWL_EXSTYLE = -20;
    private static final long WS_EX_LAYERED = 0x80000;
    private static final int LWA_ALPHA = 2;
    private static final int GW_OWNER = 4;
    private static final int RDW_INVALIDATE = 0x1, RDW_ERASE = 0x4, RDW_ALLCHILDREN = 0x80, RDW_FRAME = 0x400;

    private static final MethodHandle FOREGROUND, PROCESS, VISIBLE, OWNER, TITLE_LENGTH, GET_STYLE, SET_STYLE, LAYERED, REDRAW, CLASS_NAME;

    static {
        MethodHandle[] h = new MethodHandle[10];
        try {
            if (Platform.WINDOWS) {
                Linker linker = Linker.nativeLinker();
                SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
                ValueLayout a = ValueLayout.ADDRESS, i = ValueLayout.JAVA_INT;
                h[0] = linker.downcallHandle(user32.find("GetForegroundWindow").orElseThrow(), FunctionDescriptor.of(a));
                h[1] = linker.downcallHandle(user32.find("GetWindowThreadProcessId").orElseThrow(), FunctionDescriptor.of(i, a, a));
                h[2] = linker.downcallHandle(user32.find("IsWindowVisible").orElseThrow(), FunctionDescriptor.of(i, a));
                h[3] = linker.downcallHandle(user32.find("GetWindow").orElseThrow(), FunctionDescriptor.of(a, a, i));
                h[4] = linker.downcallHandle(user32.find("GetWindowTextLengthW").orElseThrow(), FunctionDescriptor.of(i, a));
                h[5] = linker.downcallHandle(user32.find("GetWindowLongPtrW").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_LONG, a, i));
                h[6] = linker.downcallHandle(user32.find("SetWindowLongPtrW").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_LONG, a, i, ValueLayout.JAVA_LONG));
                h[7] = linker.downcallHandle(user32.find("SetLayeredWindowAttributes").orElseThrow(), FunctionDescriptor.of(i, a, i, ValueLayout.JAVA_BYTE, i));
                h[8] = linker.downcallHandle(user32.find("RedrawWindow").orElseThrow(), FunctionDescriptor.of(i, a, a, a, i));
                h[9] = linker.downcallHandle(user32.find("GetClassNameW").orElseThrow(), FunctionDescriptor.of(i, a, a, i));
            }
        } catch (Throwable notAvailable) {
            h = new MethodHandle[10];
        }
        FOREGROUND = h[0];
        PROCESS = h[1];
        VISIBLE = h[2];
        OWNER = h[3];
        TITLE_LENGTH = h[4];
        GET_STYLE = h[5];
        SET_STYLE = h[6];
        LAYERED = h[7];
        REDRAW = h[8];
        CLASS_NAME = h[9];
    }

    private WindowTricks() {
    }

    static boolean available() {
        return CLASS_NAME != null;
    }

    private static MemorySegment hwnd(long handle) {
        return MemorySegment.ofAddress(handle);
    }

    /** The window in front, by its number (0: none, or not on Windows). */
    static long front() {
        if (FOREGROUND == null) return 0;
        try {
            return ((MemorySegment) FOREGROUND.invokeExact()).address();
        } catch (Throwable e) {
            return 0;
        }
    }

    /** Which program (process id) a window belongs to (0 if unknown). */
    static long processOf(long window) {
        if (PROCESS == null || window == 0) return 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pid = arena.allocate(ValueLayout.JAVA_INT);
            int thread = (int) PROCESS.invokeExact(hwnd(window), pid);
            return thread == 0 ? 0 : Integer.toUnsignedLong(pid.get(ValueLayout.JAVA_INT, 0));
        } catch (Throwable e) {
            return 0;
        }
    }

    /** A real app window: visible, nobody's popup, with a title. */
    static boolean appWindow(long window) {
        if (CLASS_NAME == null || window == 0) return false;
        try {
            MemorySegment w = hwnd(window);
            if ((int) VISIBLE.invokeExact(w) == 0) return false;
            if (((MemorySegment) OWNER.invokeExact(w, GW_OWNER)).address() != 0) return false;
            return (int) TITLE_LENGTH.invokeExact(w) > 0;
        } catch (Throwable e) {
            return false;
        }
    }

    static String className(long window) {
        if (CLASS_NAME == null || window == 0) return "";
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(512);
            int n = (int) CLASS_NAME.invokeExact(hwnd(window), buffer, 256);
            char[] chars = new char[Math.max(0, n)];
            for (int k = 0; k < chars.length; k++) chars[k] = buffer.get(ValueLayout.JAVA_CHAR_UNALIGNED, k * 2L);
            return new String(chars);
        } catch (Throwable e) {
            return "";
        }
    }

    /**
     * Makes a window see-through (it's still open and in front, you just can't see it). Gives back its old style to put
     * back with {@link #reveal}, or -1 if it can't be done (or it's see-through-able already: then it's left alone).
     */
    static long vanish(long window) {
        if (SET_STYLE == null || window == 0) return -1;
        try {
            MemorySegment w = hwnd(window);
            long style = (long) GET_STYLE.invokeExact(w, GWL_EXSTYLE);
            if ((style & WS_EX_LAYERED) != 0) return -1;
            long ignored = (long) SET_STYLE.invokeExact(w, GWL_EXSTYLE, style | WS_EX_LAYERED);
            int ok = (int) LAYERED.invokeExact(w, 0, (byte) 0, LWA_ALPHA);
            if (ok == 0) {
                ignored = (long) SET_STYLE.invokeExact(w, GWL_EXSTYLE, style);
                return -1;
            }
            return style;
        } catch (Throwable e) {
            return -1;
        }
    }

    /** Pops a window you made see-through back (all the way, just as it was). */
    static void reveal(long window, long oldStyle) {
        if (SET_STYLE == null || window == 0 || oldStyle < 0) return;
        try {
            MemorySegment w = hwnd(window);
            int ok = (int) LAYERED.invokeExact(w, 0, (byte) 255, LWA_ALPHA);
            long ignored = (long) SET_STYLE.invokeExact(w, GWL_EXSTYLE, oldStyle);
            ok = (int) REDRAW.invokeExact(w, MemorySegment.NULL, MemorySegment.NULL, RDW_INVALIDATE | RDW_ERASE | RDW_FRAME | RDW_ALLCHILDREN);
        } catch (Throwable e) {
            // the window's gone, most likely
        }
    }

    /** A button on the taskbar: its name ("Firefox pinned"), its app id, and where it is (real pixels). */
    record TaskbarButton(String name, String id, int x, int y, int width, int height) {
        /** The middle of it. */
        int centerX() {
            return x + width / 2;
        }
    }

    /** The PowerShell that lists the app buttons on the taskbar, one per line: name|id|x,y,width,height. */
    static String taskbarScript() {
        return String.join("\n",
                "Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes",
                "$A = [Windows.Automation.AutomationElement]",
                "$tray = $A::RootElement.FindFirst([Windows.Automation.TreeScope]::Children, (New-Object Windows.Automation.PropertyCondition($A::ClassNameProperty, 'Shell_TrayWnd')))",
                "if ($tray) {",
                "  $all = $tray.FindAll([Windows.Automation.TreeScope]::Descendants, (New-Object Windows.Automation.PropertyCondition($A::ControlTypeProperty, [Windows.Automation.ControlType]::Button)))",
                "  foreach ($b in $all) { $c = $b.Current; $r = $c.BoundingRectangle",
                "    if ($c.ClassName -notlike 'SystemTray*' -and $c.AutomationId -notlike '*Button') {",
                "      ($c.Name -replace '[\\r\\n|]', ' ') + '|' + $c.AutomationId + '|' + [int]$r.X + ',' + [int]$r.Y + ',' + [int]$r.Width + ',' + [int]$r.Height } } }");
    }

    /** Reads what {@link #taskbarScript} printed. */
    static List<TaskbarButton> parseTaskbar(String output) {
        List<TaskbarButton> buttons = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String[] parts = line.split("\\|");
            if (parts.length != 3) continue;
            String[] r = parts[2].split(",");
            try {
                buttons.add(new TaskbarButton(parts[0].strip(), parts[1].strip(), Integer.parseInt(r[0].strip()), Integer.parseInt(r[1].strip()),
                        Integer.parseInt(r[2].strip()), Integer.parseInt(r[3].strip())));
            } catch (RuntimeException notANumber) {
                // skip it
            }
        }
        return buttons;
    }

    /** The taskbar button for an app (like "firefox.exe", its window titled "Mozilla Firefox"), or null. */
    static TaskbarButton buttonFor(List<TaskbarButton> buttons, String exe, String title) {
        String base = exe.toLowerCase(Locale.ROOT).replaceAll("\\.exe$", "");
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        TaskbarButton pinnedOnly = null;
        for (TaskbarButton b : buttons) {
            if (!matches(b, base, t)) continue;
            if (b.name().toLowerCase(Locale.ROOT).contains("running window")) return b; // the one with the app in it
            if (pinnedOnly == null) pinnedOnly = b;
        }
        return pinnedOnly;
    }

    private static boolean matches(TaskbarButton b, String base, String t) {
        String id = b.id().toLowerCase(Locale.ROOT);
        if (!base.isEmpty() && (id.endsWith("\\" + base + ".exe") || id.endsWith("/" + base + ".exe"))) return true; // its path
        if (base.length() >= 4 && id.contains(base)) return true; // like Microsoft.WindowsTerminal_... for windowsterminal.exe
        String name = shortName(b.name());
        if (name.isEmpty()) return false;
        String squashed = name.replace(" ", "");
        if (!base.isEmpty() && (squashed.equals(base) || squashed.startsWith(base) || base.startsWith(squashed)
                || (squashed.length() >= 4 && base.endsWith(squashed)))) return true;
        return !t.isEmpty() && (t.equals(name) || t.endsWith(" - " + name) || t.endsWith(" — " + name));
    }

    /** "Firefox pinned" or "Terminal - 1 running window pinned" to "firefox" or "terminal". */
    static String shortName(String name) {
        String n = name.toLowerCase(Locale.ROOT).strip();
        n = n.replaceAll("\\s+pinned$", "");
        n = n.replaceAll("\\s+-\\s+\\d+\\s+running windows?$", "");
        return n.strip();
    }
}
