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

    // the same, with a Mac's keys (Cmd and Option instead of Ctrl and Alt)
    private static final List<String> VS_CODE_MAC = List.of(
            "Cmd+P opens any file by name.",
            "Cmd+Shift+P finds any command.",
            "Option+Up/Down moves the line you're on.",
            "Cmd+D picks the next match too, so you can edit both.",
            "F2 renames something everywhere it's used.",
            "Ctrl+` opens the terminal right here.",
            "Shift+Option+F tidies up the whole file.",
            "Cmd+/ turns lines into comments (and back).");

    private static final List<String> TERMINAL_MAC = List.of(
            "Up arrow brings back the last command.",
            "Tab finishes file and folder names for you.",
            "Cmd+K clears the screen.",
            "Ctrl+C stops whatever's running.",
            "Cmd+T opens a new tab.",
            "\"cd ..\" goes up one folder.",
            "Ctrl+R searches your old commands.");

    private static final List<String> INTELLIJ_MAC = List.of(
            "Shift twice: search everything.",
            "Option+Enter fixes what's under the cursor.",
            "Cmd+Option+L tidies up the file.",
            "Shift+F6 renames something everywhere.");

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

    private static final List<String> GIT = List.of(
            "git status: what's changed since your last commit.",
            "git add -p: pick which changes go in, a bit at a time.",
            "git commit --amend: fix your last commit's message\n(before you push it!).",
            "git log --oneline: your history, one line each.",
            "git diff: exactly what you changed.\ngit diff --staged: what's about to be committed.",
            "git switch -c new-idea: a new branch to try things on.",
            "git stash: put your changes away for a sec.\ngit stash pop: get them back.",
            "git restore file.txt: throw away changes to one file.\n(Careful: they're gone for good.)",
            "git pull before you start, so you're up to date.",
            "Commit little and often. Small commits are\neasy to undo.",
            "git blame file.txt: who changed each line, and when.",
            "Merge conflict? Look for <<<<<<< and >>>>>>>,\nkeep what you want, delete the markers, commit.");

    private static final List<String> GITHUB = List.of(
            "Press . on a GitHub repo to open it in a\nweb editor.",
            "Press t on a repo to find any file by name.",
            "A README.md is the first thing people see.\nMake it say what it does!",
            "Issues are a great to-do list for your project.",
            "Pull requests let you check changes before\nthey go in, even your own.",
            "Add a .gitignore so build files and secrets\nnever get committed.",
            "Never commit passwords or API keys. If you did,\nchange them: the history keeps them.",
            "Releases: tag a version and attach your\nbuilt files for people to download.",
            "GitHub Actions can build and test your code\nevery time you push.");

    private static final List<String> CODING = List.of(
            "Stuck? Explain your code out loud to a rubber duck.\nWorks way more than it should.",
            "Name things for what they are: playerScore,\nnot x2.",
            "If you copy and paste code three times,\nmake it a function.",
            "Read the error message. All of it.\nThe answer's usually in there.",
            "Print things out to see what's really going on.\nIt's not cheating, it's debugging.",
            "Save, run, check. Little steps beat\nbig surprises.",
            "Off by one? Check if your loop should be\n< or <=.",
            "Comments should say why, not what.\nThe code already says what.",
            "Take a break. Bugs are easier to see\nafter a walk.");

    private static final List<String> ERRORS = List.of(
            "\"is not recognized as a command\"?\nIt isn't installed, or it isn't on your PATH.",
            "\"Permission denied\"? Something has the file open,\nor you need to run as admin.",
            "NullPointerException: you used something\nthat was never given a value.",
            "IndexError / IndexOutOfBounds: you asked for item 10\nof a list that has fewer than 11.",
            "SyntaxError: a typo the computer can't read.\nCheck brackets, quotes and colons near that line.",
            "\"Module not found\"? Install it first\n(pip install ... or npm install ...).",
            "Stack overflow error: a function that calls itself\nforever. Give it a way to stop.",
            "\"Address already in use\"? Something else is on\nthat port. Close it, or use another port.");

    /** Two (or more) kinds of tips taking turns. */
    private static List<String> mix(List<String> first, List<String> second, List<String> third) {
        java.util.ArrayList<String> mixed = new java.util.ArrayList<>();
        int most = Math.max(first.size(), Math.max(second.size(), third.size()));
        for (int i = 0; i < most; i++) {
            if (i < first.size()) mixed.add(first.get(i));
            if (i < second.size()) mixed.add(second.get(i));
            if (i < third.size()) mixed.add(third.get(i));
        }
        return List.copyOf(mixed);
    }

    private static final List<String> FOR_VS_CODE = mix(Platform.MAC ? VS_CODE_MAC : VS_CODE, CODING, ERRORS);
    private static final List<String> FOR_TERMINAL = mix(Platform.MAC ? TERMINAL_MAC : TERMINAL, GIT, ERRORS);
    private static final List<String> FOR_INTELLIJ = mix(Platform.MAC ? INTELLIJ_MAC : INTELLIJ, CODING, ERRORS);
    private static final List<String> FOR_GITHUB = mix(GITHUB, GIT, List.of());

    private final Map<String, Integer> next = new HashMap<>();

    /** A tip for what's in front, or null when there isn't one. */
    public String tipFor(Foreground.Front front) {
        String kind = kind(front);
        if (kind == null) return null;
        List<String> tips = switch (kind) {
            case "run" -> RUN;
            case "vscode" -> FOR_VS_CODE;
            case "terminal" -> FOR_TERMINAL;
            case "intellij" -> FOR_INTELLIJ;
            case "github" -> FOR_GITHUB;
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
        boolean browser = Set.of("chrome.exe", "msedge.exe", "firefox.exe", "brave.exe", "opera.exe").contains(app);
        if ((browser && title.contains("github")) || app.equals("githubdesktop.exe")) return "github"; // GitHub, in a browser or the app
        if (app.equals("explorer.exe") && (front.windowClass().equals("Progman") || front.windowClass().equals("WorkerW"))) return "windows";
        return switch (app) {
            case "code.exe", "code - insiders.exe", "cursor.exe", "windsurf.exe", "code", "cursor" -> "vscode";
            case "terminal", "iterm2", "warp", "ghostty", "alacritty", "kitty", "konsole", "gnome-terminal-server" -> "terminal";
            case "intellij idea", "pycharm", "android studio" -> "intellij";
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
