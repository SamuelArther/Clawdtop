package clawdtop;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Clawd's tips: all built in (nothing from the internet), picked by what's in front. Each kind of app's tips come
 * round in turn, so the same one doesn't show twice in a row.
 */
public final class Tips {
    /** The Run box (Win + R) is titled like this in a few languages. */
    private static final Set<String> RUN_TITLES = Set.of("run", "ejecutar", "exécuter", "ausführen", "executar", "esegui",
            "uitvoeren", "uruchamianie", "выполнить", "実行", "运行", "실행");

    private static final List<String> RUN = List.of(
            "Run box tricks:\n%temp%  junk files you can delete\n%appdata%  where apps keep settings\nshell:startup  apps that start with Windows",
            "More Run box tricks:\ncontrol  the old Control Panel\nappwiz.cpl  uninstall programs\nncpa.cpl  network adapters",
            "Run box tricks:\nmsinfo32  everything about your PC\ndxdiag  graphics and sound info\nwinver  which Windows you have",
            "Run box tricks:\ncleanmgr  Disk Cleanup\nmsconfig  how Windows starts\nservices.msc  background services",
            "Run box tricks:\ncmd  a command prompt\npowershell  a better one\nCtrl+Shift+Enter runs it as admin",
            "Run box tricks:\nshell:sendto  the Send to menu\nmstsc  Remote Desktop\nosk  the on-screen keyboard");

    private static final List<String> VS_CODE = List.of(
            "Ctrl+P opens any file by name.",
            "Ctrl+Shift+P finds any command.",
            "Alt+Up/Down moves the line you're on.",
            "Ctrl+D picks the next match too, so you can edit both.",
            "F2 renames something everywhere it's used.",
            "Ctrl+` opens the terminal right here.",
            "Shift+Alt+F tidies up the whole file.",
            "Ctrl+/ turns lines into comments (and back).");

    private static final List<String> TERMINAL = List.of(
            "Up arrow brings back the last command.",
            "Tab finishes file and folder names for you.",
            "cls clears the screen (clear on Linux and Mac).",
            "Ctrl+C stops whatever's running.",
            "Ctrl+Shift+T opens a new tab in Windows Terminal.",
            "\"cd ..\" goes up one folder.",
            "Ctrl+R in PowerShell searches old commands.");

    private static final List<String> INTELLIJ = List.of(
            "Shift twice: search everything.",
            "Alt+Enter fixes what's under the cursor.",
            "Ctrl+Alt+L tidies up the file.",
            "Shift+F6 renames something everywhere.");

    private static final List<String> EXPLORER = List.of(
            "Ctrl+Shift+N makes a new folder.",
            "Alt+Up goes to the folder above.",
            "Type a path in the address bar, or \"cmd\" to open a terminal right there.",
            "Shift+right-click a file for \"Copy as path\".",
            "Ctrl+Shift+2 shows big thumbnails.");

    private static final List<String> TASK_MANAGER = List.of(
            "Click a column (like Memory) to see what's using the most.",
            "The Startup apps page turns off things that slow your sign-in.",
            "Ctrl+Shift+Esc opens me any time.");

    private static final List<String> BROWSER = List.of(
            "Ctrl+Shift+T brings back the tab you just closed.",
            "Ctrl+L jumps to the address bar.",
            "Ctrl+Tab goes to the next tab.",
            "Ctrl+Shift+N opens a private window.",
            "Middle-click a link to open it in a new tab.");

    private static final List<String> WINDOWS = List.of(
            "Win+Shift+S takes a screenshot of part of the screen.",
            "Win+V shows everything you copied lately.",
            "Win+. opens emoji and symbols.",
            "Win+D shows the desktop (and back).",
            "Win+Left/Right snaps a window to half the screen.",
            "Win+E opens File Explorer.");

    private final Map<String, Integer> next = new HashMap<>();

    /** A tip for what's in front, or null when there isn't one. */
    public String tipFor(Foreground.Front front) {
        String kind = kind(front);
        if (kind == null) return null;
        List<String> tips = switch (kind) {
            case "run" -> RUN;
            case "vscode" -> VS_CODE;
            case "terminal" -> TERMINAL;
            case "intellij" -> INTELLIJ;
            case "explorer" -> EXPLORER;
            case "taskmgr" -> TASK_MANAGER;
            case "browser" -> BROWSER;
            default -> WINDOWS;
        };
        int i = next.getOrDefault(kind, 0);
        next.put(kind, (i + 1) % tips.size());
        return tips.get(i);
    }

    /** Which set of tips fits what's in front, or null for none. */
    static String kind(Foreground.Front front) {
        String app = front.app().toLowerCase(Locale.ROOT);
        String title = front.title().toLowerCase(Locale.ROOT);
        if (app.equals("explorer.exe") && front.windowClass().equals("#32770") && RUN_TITLES.contains(title)) return "run";
        if (app.equals("explorer.exe") && front.windowClass().equals("CabinetWClass")) return "explorer";
        if (app.equals("explorer.exe") && (front.windowClass().equals("Progman") || front.windowClass().equals("WorkerW"))) return "windows";
        return switch (app) {
            case "code.exe", "code - insiders.exe", "cursor.exe", "windsurf.exe" -> "vscode";
            case "windowsterminal.exe", "wt.exe", "cmd.exe", "powershell.exe", "pwsh.exe", "conhost.exe", "openconsole.exe" -> "terminal";
            case "idea64.exe", "idea.exe", "pycharm64.exe", "webstorm64.exe", "clion64.exe", "rider64.exe", "goland64.exe", "studio64.exe" -> "intellij";
            case "taskmgr.exe" -> "taskmgr";
            case "chrome.exe", "msedge.exe", "firefox.exe", "brave.exe", "opera.exe" -> "browser";
            default -> null;
        };
    }

    /** Whether tips for this should show straight away (the Run box: you opened it to type something). */
    static boolean urgent(Foreground.Front front) {
        return "run".equals(kind(front));
    }
}
