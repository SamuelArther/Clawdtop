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

    // ---- Hearing about new windows the moment Windows shows them (so a tackled app never flashes up first) ----

    private static volatile java.util.function.LongConsumer onShown;

    /**
     * Calls shown (on its own thread) with each new top-level window, the instant Windows shows it, until Clawd closes.
     * Whatever shown does must be quick. Does nothing if it can't be set up (then the twice-a-second check still works).
     */
    static void watchShown(java.util.function.LongConsumer shown) {
        if (!Platform.WINDOWS || onShown != null) return;
        onShown = shown;
        Thread t = new Thread(() -> {
            try {
                Linker linker = Linker.nativeLinker();
                SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
                ValueLayout a = ValueLayout.ADDRESS, i = ValueLayout.JAVA_INT;
                MethodHandle setHook = linker.downcallHandle(user32.find("SetWinEventHook").orElseThrow(), FunctionDescriptor.of(a, i, i, a, a, i, i, i));
                MethodHandle getMessage = linker.downcallHandle(user32.find("GetMessageW").orElseThrow(), FunctionDescriptor.of(i, a, a, i, i));
                MethodHandle translate = linker.downcallHandle(user32.find("TranslateMessage").orElseThrow(), FunctionDescriptor.of(i, a));
                MethodHandle dispatch = linker.downcallHandle(user32.find("DispatchMessageW").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_LONG, a));
                MemorySegment callback = linker.upcallStub(java.lang.invoke.MethodHandles.lookup().findStatic(WindowTricks.class, "winEvent",
                                java.lang.invoke.MethodType.methodType(void.class, MemorySegment.class, int.class, MemorySegment.class, int.class, int.class, int.class, int.class)),
                        FunctionDescriptor.ofVoid(a, i, a, i, i, i, i), Arena.global());
                final int eventObjectShow = 0x8002, outOfContext = 0x0000, skipOwnProcess = 0x0002;
                MemorySegment hook = (MemorySegment) setHook.invokeExact(eventObjectShow, eventObjectShow, MemorySegment.NULL, callback, 0, 0, outOfContext | skipOwnProcess);
                if (hook.address() == 0) return;
                try (Arena arena = Arena.ofConfined()) {
                    MemorySegment msg = arena.allocate(64); // (a MSG: the hook's news arrives through this thread's messages)
                    while ((int) getMessage.invokeExact(msg, MemorySegment.NULL, 0, 0) > 0) {
                        int ignored = (int) translate.invokeExact(msg);
                        long done = (long) dispatch.invokeExact(msg);
                    }
                }
            } catch (Throwable cantHook) {
                // then the twice-a-second check does it (with a flash)
            }
        }, "Clawdtop window news");
        t.setDaemon(true);
        t.start();
    }

    /** Windows' news: a window was shown (only top-level windows themselves are passed on). */
    private static void winEvent(MemorySegment hook, int event, MemorySegment hwnd, int idObject, int idChild, int thread, int time) {
        if (idObject != 0 || idChild != 0 || hwnd.address() == 0) return; // (OBJID_WINDOW, the window itself)
        java.util.function.LongConsumer shown = onShown;
        if (shown == null) return;
        try {
            shown.accept(hwnd.address());
        } catch (Throwable ignored) {
            // never let anything escape back into Windows
        }
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

    /** A window's title ("" if it has none). */
    static String title(long window) {
        if (TITLE_LENGTH == null || window == 0) return "";
        try (Arena arena = Arena.ofConfined()) {
            MethodHandle text = Linker.nativeLinker().downcallHandle(SymbolLookup.libraryLookup("user32", Arena.global()).find("GetWindowTextW").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            MemorySegment buffer = arena.allocate(1024);
            int n = (int) text.invokeExact(hwnd(window), buffer, 512);
            char[] chars = new char[Math.max(0, n)];
            for (int k = 0; k < chars.length; k++) chars[k] = buffer.get(ValueLayout.JAVA_CHAR_UNALIGNED, k * 2L);
            return new String(chars);
        } catch (Throwable e) {
            return "";
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

    private static final int SWP_NOSIZE = 0x1, SWP_NOZORDER = 0x4, SWP_NOACTIVATE = 0x10;
    private static final java.util.Map<Long, int[]> PARKED = new java.util.concurrent.ConcurrentHashMap<>(); // window -> where it was

    /**
     * Hides a window for a moment by parking it off the screen (it's still open, and its app draws it as usual; only
     * where it sits changes). Gives back 0 if it worked (put it back with {@link #reveal}), or -1 if it couldn't.
     * (Making it see-through instead breaks how newer apps like Notepad draw themselves.)
     */
    static long vanish(long window) {
        if (!Platform.WINDOWS || window == 0) return -1;
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
            MethodHandle rect = linker.downcallHandle(user32.find("GetWindowRect").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MemorySegment r = arena.allocate(16);
            if ((int) rect.invokeExact(hwnd(window), r) == 0) return -1;
            int x = r.get(ValueLayout.JAVA_INT, 0), y = r.get(ValueLayout.JAVA_INT, 4);
            if (x <= -30000) return -1; // (minimized: nothing to hide)
            if (!move(window, -32000, -32000)) return STUCK;
            PARKED.put(window, new int[] {x, y});
            rememberParked();
            return 0;
        } catch (Throwable e) {
            return -1;
        }
    }

    /** What vanish says when the window won't move (a full-screen popup, or one running as admin): it stays put. */
    static final long STUCK = -2;

    /** Whether a window's still parked off the screen (some apps put themselves back right after they open). */
    static boolean parked(long window) {
        if (!Platform.WINDOWS || window == 0) return false;
        try (Arena arena = Arena.ofConfined()) {
            MethodHandle rect = Linker.nativeLinker().downcallHandle(SymbolLookup.libraryLookup("user32", Arena.global()).find("GetWindowRect").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MemorySegment r = arena.allocate(16);
            if ((int) rect.invokeExact(hwnd(window), r) == 0) return true; // (gone: nothing showing)
            return r.get(ValueLayout.JAVA_INT, 0) <= -30000;
        } catch (Throwable e) {
            return true;
        }
    }

    /** Parks a window off the screen again (it put itself back), keeping where it was to begin with. Whether it moved. */
    static boolean repark(long window) {
        return PARKED.containsKey(window) && move(window, -32000, -32000);
    }

    /** Puts a parked window back exactly where it was. */
    static void reveal(long window, long ignored) {
        int[] was = PARKED.remove(window);
        if (was != null) putBack(window, was[0], was[1]);
        rememberParked();
    }

    /**
     * Puts a window back at (x, y). If it got minimized while it was parked (you clicked its taskbar button), Windows
     * remembers the parked spot as where to restore it: so that's put right too, or it'd come back off the screen.
     */
    private static void putBack(long window, int x, int y) {
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
            MethodHandle get = linker.downcallHandle(user32.find("GetWindowPlacement").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MethodHandle set = linker.downcallHandle(user32.find("SetWindowPlacement").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MethodHandle iconic = linker.downcallHandle(user32.find("IsIconic").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            MemorySegment place = arena.allocate(44); // WINDOWPLACEMENT: length, flags, showCmd, min point, max point, normal rect
            place.set(ValueLayout.JAVA_INT, 0, 44);
            if ((int) get.invokeExact(hwnd(window), place) != 0) {
                int left = place.get(ValueLayout.JAVA_INT, 28), top = place.get(ValueLayout.JAVA_INT, 32);
                if (left <= -30000 || top <= -30000) { // (its "restore to" spot is the parking spot: back where it was)
                    place.set(ValueLayout.JAVA_INT, 28, x);
                    place.set(ValueLayout.JAVA_INT, 32, y);
                    place.set(ValueLayout.JAVA_INT, 36, x + place.get(ValueLayout.JAVA_INT, 36) - left);
                    place.set(ValueLayout.JAVA_INT, 40, y + place.get(ValueLayout.JAVA_INT, 40) - top);
                    int showCmd = place.get(ValueLayout.JAVA_INT, 8);
                    int ignored = (int) set.invokeExact(hwnd(window), place);
                    if (showCmd == 2 || showCmd == 6 || showCmd == 7) return; // (minimized: it comes back to the right spot when you restore it)
                }
            }
            if ((int) iconic.invokeExact(hwnd(window)) != 0) return;
        } catch (Throwable e) {
            // then just move it
        }
        move(window, x, y);
    }

    /** Where windows he's parked are written down, in case he's stopped suddenly (they're put back next time he starts). */
    private static java.nio.file.Path parkedFile() {
        return Settings.folder().resolve("parked.txt");
    }

    private static synchronized void rememberParked() {
        try {
            if (PARKED.isEmpty()) {
                java.nio.file.Files.deleteIfExists(parkedFile());
                return;
            }
            StringBuilder lines = new StringBuilder();
            for (var p : PARKED.entrySet()) lines.append(p.getKey()).append(' ').append(p.getValue()[0]).append(' ').append(p.getValue()[1]).append('\n');
            java.nio.file.Files.writeString(parkedFile(), lines);
        } catch (java.io.IOException | RuntimeException cant) {
            // (the shutdown hook still puts it back, most times)
        }
    }

    /** On start: any window left parked last time (he was stopped mid-tackle) goes back where it was. */
    static void restoreLeftovers() {
        if (!Platform.WINDOWS || CLASS_NAME == null) return;
        try {
            if (!java.nio.file.Files.exists(parkedFile())) return;
            for (String line : java.nio.file.Files.readAllLines(parkedFile())) {
                String[] p = line.strip().split(" ");
                if (p.length != 3) continue;
                long window = Long.parseLong(p[0]);
                if (parked(window) || parkedWhenRestored(window)) putBack(window, Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            }
            java.nio.file.Files.deleteIfExists(parkedFile());
        } catch (java.io.IOException | RuntimeException cant) {
            // never mind
        }
    }

    /** Whether a (minimized) window would come back at the parking spot when restored. */
    private static boolean parkedWhenRestored(long window) {
        try (Arena arena = Arena.ofConfined()) {
            MethodHandle get = Linker.nativeLinker().downcallHandle(SymbolLookup.libraryLookup("user32", Arena.global()).find("GetWindowPlacement").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MemorySegment place = arena.allocate(44);
            place.set(ValueLayout.JAVA_INT, 0, 44);
            return (int) get.invokeExact(hwnd(window), place) != 0 && place.get(ValueLayout.JAVA_INT, 28) <= -30000;
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean move(long window, int x, int y) {
        try {
            MethodHandle setPos = Linker.nativeLinker().downcallHandle(SymbolLookup.libraryLookup("user32", Arena.global()).find("SetWindowPos").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            return (int) setPos.invokeExact(hwnd(window), MemorySegment.NULL, x, y, 0, 0, SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE) != 0;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Brings a window to the front (it just popped open: it's what you wanted, so it's on top). */
    static void toFront(long window) {
        if (window == 0 || !Platform.WINDOWS) return;
        try {
            MethodHandle switchTo = Linker.nativeLinker().downcallHandle(SymbolLookup.libraryLookup("user32", Arena.global()).find("SwitchToThisWindow").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            switchTo.invokeExact(hwnd(window), 1);
        } catch (Throwable e) {
            // it's open, just maybe not on top
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
                "[Console]::OutputEncoding = [Text.Encoding]::UTF8", // (app names in any language come through right)
                "Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes",
                "$A = [Windows.Automation.AutomationElement]",
                "$main = New-Object Windows.Automation.PropertyCondition($A::ClassNameProperty, 'Shell_TrayWnd')",
                "$other = New-Object Windows.Automation.PropertyCondition($A::ClassNameProperty, 'Shell_SecondaryTrayWnd')", // (another monitor's taskbar)
                "foreach ($tray in $A::RootElement.FindAll([Windows.Automation.TreeScope]::Children, (New-Object Windows.Automation.OrCondition($main, $other)))) {",
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
