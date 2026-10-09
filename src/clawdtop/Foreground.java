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
    private static final MethodHandle GET_WINDOW_TEXT;

    static {
        MethodHandle window = null;
        MethodHandle process = null;
        MethodHandle rect = null;
        MethodHandle className = null;
        MethodHandle text = null;
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
                text = linker.downcallHandle(user32.find("GetWindowTextW").orElseThrow(),
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
        GET_WINDOW_TEXT = window == null ? null : text;
    }

    private Foreground() {
    }

    /**
     * The window in front: its program file (like "Code.exe"), its kind of window, its title, Windows' number for it,
     * and where it is on the screen (real pixels: left, top, right, bottom). Parts can be "" or 0.
     */
    public record Front(String app, String windowClass, String title, long handle, int[] bounds) {
        static final Front UNKNOWN = new Front("", "", "", 0, new int[4]);

        public Front(String app, String windowClass, String title) {
            this(app, windowClass, title, 0, new int[4]);
        }
    }

    /** What's in front right now (all "" if it can't be told). */
    public static Front front() {
        if (!Platform.WINDOWS) return new Front(Platform.frontApp(), "", ""); // Mac and Linux: just the app's name
        if (GET_WINDOW_TEXT == null) return Front.UNKNOWN;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment window = (MemorySegment) GET_FOREGROUND_WINDOW.invokeExact();
            if (window.address() == 0) return Front.UNKNOWN;
            MemorySegment buffer = arena.allocate(ValueLayout.JAVA_CHAR, 256);
            int length = (int) GET_CLASS_NAME.invokeExact(window, buffer, 256);
            String windowClass = length > 0 ? new String(buffer.toArray(ValueLayout.JAVA_CHAR), 0, length) : "";
            length = (int) GET_WINDOW_TEXT.invokeExact(window, buffer, 256);
            String title = length > 0 ? new String(buffer.toArray(ValueLayout.JAVA_CHAR), 0, length) : "";
            String app = app();
            MemorySegment r = arena.allocate(ValueLayout.JAVA_INT, 4);
            int[] bounds = new int[4];
            if ((int) GET_WINDOW_RECT.invokeExact(window, r) != 0) bounds = r.toArray(ValueLayout.JAVA_INT);
            return new Front(app == null ? "" : app, windowClass, title, window.address(), bounds);
        } catch (Throwable e) {
            return Front.UNKNOWN;
        }
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

    /**
     * The folder a File Explorer window is showing, or null. Windows only tells this through its Shell, so a quick
     * PowerShell asks it (once, when he needs it, not all the time).
     */
    public static Path explorerFolder(long handle) {
        if (handle == 0) return null;
        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "[Console]::OutputEncoding = [Text.Encoding]::UTF8; " // (so folders like "Música" come through right)
                            + "(New-Object -ComObject Shell.Application).Windows() | Where-Object { $_.HWND -eq " + handle
                            + " } | ForEach-Object { $_.Document.Folder.Self.Path } | Select-Object -First 1")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
            if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly();
            if (out.isEmpty() || out.startsWith("::") || out.contains("\n")) return null; // "This PC" and other non-folders
            Path folder = Path.of(out);
            return java.nio.file.Files.isDirectory(folder) ? folder : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Coding apps that are whole programs you open and close (not the little helpers terminals start and stop). */
    static final Set<String> DEV_PROGRAMS = Set.of("code.exe", "code - insiders.exe", "cursor.exe", "windsurf.exe", "zed.exe",
            "devenv.exe", "idea64.exe", "idea.exe", "pycharm64.exe", "webstorm64.exe", "clion64.exe", "rider64.exe",
            "goland64.exe", "studio64.exe", "eclipse.exe", "sublime_text.exe", "notepad++.exe", "fleet.exe",
            "windowsterminal.exe", "githubdesktop.exe",
            // on a Mac or Linux, apps go by their names
            "code", "cursor", "zed", "terminal", "iterm2", "warp", "ghostty", "alacritty", "kitty", "konsole", "gnome-terminal-server",
            "xcode", "android studio", "intellij idea", "pycharm", "sublime text", "github desktop");

    /** Which of those are open right now (by program name). */
    public static Set<String> openDevPrograms() {
        Set<String> open = new java.util.HashSet<>();
        ProcessHandle.allProcesses().forEach(p -> p.info().command().ifPresent(c -> {
            String name = Path.of(c).getFileName().toString().toLowerCase(Locale.ROOT);
            if (DEV_PROGRAMS.contains(name)) open.add(name);
        }));
        return open;
    }

    /** Whether this program is a coding app. */
    public static boolean isDevApp(String app) {
        return app != null && DEV_APPS.contains(app.toLowerCase(Locale.ROOT));
    }
    /** Whether a mouse button is held down right now, anywhere on the screen (Windows only; false elsewhere). */
    public static boolean mouseButtonDown() {
        if (Buttons.STATE == null) return false;
        try {
            for (int button : new int[] {0x01, 0x02, 0x04}) { // left, right, middle
                if (((short) Buttons.STATE.invokeExact(button) & 0x8000) != 0) return true;
            }
        } catch (Throwable notAvailable) {
            // then we can't tell
        }
        return false;
    }

    /** Whether the right mouse button is held down right now (Windows only). */
    public static boolean rightButtonDown() {
        if (Buttons.STATE == null) return false;
        try {
            return ((short) Buttons.STATE.invokeExact(0x02) & 0x8000) != 0;
        } catch (Throwable notAvailable) {
            return false;
        }
    }

    /** GetAsyncKeyState, looked up the first time it's needed. */
    private static final class Buttons {
        static final MethodHandle STATE = load();

        private static MethodHandle load() {
            try {
                if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) return null;
                SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
                return Linker.nativeLinker().downcallHandle(user32.find("GetAsyncKeyState").orElseThrow(),
                        FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ValueLayout.JAVA_INT));
            } catch (Throwable notAvailable) {
                return null;
            }
        }
    }
}
