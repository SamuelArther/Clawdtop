package clawdtop;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Which app is in front, asked straight from Windows (through Java's own way of calling Windows, no extra libraries).
 * On other systems, or if Windows won't say, it's just unknown and Clawd carries on.
 */
public final class Foreground {
    /** Apps that count as coding: editors, IDEs, terminals and friends (their program file names, lowercase). */
    static final Set<String> DEV_APPS = Set.of(
            "code.exe", "code - insiders.exe", "cursor.exe", "windsurf.exe", "zed.exe", "devenv.exe",
            "idea64.exe", "idea.exe", "pycharm64.exe", "webstorm64.exe", "clion64.exe", "rider64.exe", "goland64.exe",
            "studio64.exe", "eclipse.exe", "sublime_text.exe", "notepad++.exe", "atom.exe", "fleet.exe",
            "windowsterminal.exe", "wt.exe", "cmd.exe", "powershell.exe", "pwsh.exe", "conhost.exe", "openconsole.exe",
            "bash.exe", "mintty.exe", "wezterm-gui.exe", "alacritty.exe", "githubdesktop.exe", "claude.exe");

    private static final MethodHandle GET_FOREGROUND_WINDOW;
    private static final MethodHandle GET_WINDOW_THREAD_PROCESS_ID;
    private static final MethodHandle GET_WINDOW_RECT;
    private static final MethodHandle GET_CLASS_NAME;

    static {
        MethodHandle window = null;
        MethodHandle process = null;
        MethodHandle rect = null;
        MethodHandle className = null;
        try {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
                Linker linker = Linker.nativeLinker();
                SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
                window = linker.downcallHandle(user32.find("GetForegroundWindow").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.ADDRESS));
                process = linker.downcallHandle(user32.find("GetWindowThreadProcessId").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                rect = linker.downcallHandle(user32.find("GetWindowRect").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                className = linker.downcallHandle(user32.find("GetClassNameW").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            }
        } catch (Throwable notAvailable) {
            window = null;
            process = null;
        }
        GET_FOREGROUND_WINDOW = window;
        GET_WINDOW_THREAD_PROCESS_ID = process;
        GET_WINDOW_RECT = window == null ? null : rect;
        GET_CLASS_NAME = window == null ? null : className;
    }

    private Foreground() {
    }

    /** The program file of the app in front, like "Code.exe", or null if it can't be told. */
    public static String app() {
        if (GET_FOREGROUND_WINDOW == null) return null;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = (MemorySegment) GET_FOREGROUND_WINDOW.invokeExact();
            if (window.address() == 0) return null;
            MemorySegment pid = arena.allocate(ValueLayout.JAVA_INT);
            int thread = (int) GET_WINDOW_THREAD_PROCESS_ID.invokeExact(window, pid);
            if (thread == 0) return null;
            return ProcessHandle.of(Integer.toUnsignedLong(pid.get(ValueLayout.JAVA_INT, 0)))
                    .flatMap(p -> p.info().command())
                    .map(command -> Path.of(command).getFileName().toString())
                    .orElse(null);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Whether the window in front covers a whole screen of this size (in real screen pixels), like a game or a video
     * in full screen, so Clawd should get out of the way. The desktop itself covers the screen too, but doesn't count.
     */
    public static boolean fullScreen(int screenWidth, int screenHeight) {
        if (GET_WINDOW_RECT == null) return false;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = (MemorySegment) GET_FOREGROUND_WINDOW.invokeExact();
            if (window.address() == 0) return false;
            MemorySegment name = arena.allocate(ValueLayout.JAVA_CHAR, 64);
            int length = (int) GET_CLASS_NAME.invokeExact(window, name, 64);
            String className = length > 0 ? new String(name.toArray(ValueLayout.JAVA_CHAR), 0, length) : "";
            if (className.equals("Progman") || className.equals("WorkerW") || className.equals("Shell_TrayWnd")) return false;
            MemorySegment r = arena.allocate(ValueLayout.JAVA_INT, 4);
            if ((int) GET_WINDOW_RECT.invokeExact(window, r) == 0) return false;
            int width = r.getAtIndex(ValueLayout.JAVA_INT, 2) - r.getAtIndex(ValueLayout.JAVA_INT, 0);
            int height = r.getAtIndex(ValueLayout.JAVA_INT, 3) - r.getAtIndex(ValueLayout.JAVA_INT, 1);
            return width >= screenWidth && height >= screenHeight;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Whether this program is a coding app. */
    public static boolean isDevApp(String app) {
        return app != null && DEV_APPS.contains(app.toLowerCase(Locale.ROOT));
    }
}
