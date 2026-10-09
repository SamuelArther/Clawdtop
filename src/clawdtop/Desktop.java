package clawdtop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Your desktop: where its icons are (Windows: read from the desktop's icon list, the way desktop-layout tools do),
 * and tidying it into a "Neat" folder sorted by type. Nothing is ever deleted: files are only moved, and every move is
 * remembered so "put them back" can undo it.
 */
final class Desktop {
    private Desktop() {
    }

    /** An icon on the desktop: its name (as shown, often without the extension) and where its top-left is (real pixels). */
    record Icon(String name, int x, int y) {
    }

    /** The script that lists the desktop's icons: the desktop folder's path first ("DESKTOP|path"), then name|x,y. */
    static String iconScript() {
        return String.join("\n", deskClass(),
                "'DESKTOP|' + [Environment]::GetFolderPath('Desktop')",
                "foreach ($line in [ClawdDesk]::Icons()) { $line }");
    }

    /**
     * The script that moves one icon (by its name, once Explorer's shown it: it waits up to 6 seconds) so its top-left
     * is at (x, y), in real pixels. Says "placed" if it did.
     */
    static String placeScript(String name, int x, int y) {
        return String.join("\n", deskClass(),
                "if ([ClawdDesk]::Place('" + name.replace("'", "''") + "', " + x + ", " + y + ")) { 'placed' }");
    }

    /** The desktop icon reader (and mover), for PowerShell. */
    private static String deskClass() {
        return String.join("\n",
                "[Console]::OutputEncoding = [Text.Encoding]::UTF8",
                "Add-Type -TypeDefinition @'",
                "using System; using System.Runtime.InteropServices; using System.Text;",
                "public static class ClawdDesk {",
                "  [DllImport(\"user32.dll\", CharSet = CharSet.Unicode)] static extern IntPtr FindWindow(string cls, string name);",
                "  [DllImport(\"user32.dll\", CharSet = CharSet.Unicode)] static extern IntPtr FindWindowEx(IntPtr parent, IntPtr after, string cls, string name);",
                "  [DllImport(\"user32.dll\")] static extern IntPtr SendMessage(IntPtr w, uint msg, IntPtr wp, IntPtr lp);",
                "  [DllImport(\"user32.dll\")] static extern uint GetWindowThreadProcessId(IntPtr w, out uint pid);",
                "  [DllImport(\"user32.dll\")] static extern bool ClientToScreen(IntPtr w, ref POINT p);",
                "  [DllImport(\"user32.dll\")] static extern bool ScreenToClient(IntPtr w, ref POINT p);",
                "  [DllImport(\"kernel32.dll\")] static extern IntPtr OpenProcess(uint access, bool inherit, uint pid);",
                "  [DllImport(\"kernel32.dll\")] static extern IntPtr VirtualAllocEx(IntPtr p, IntPtr at, UIntPtr size, uint type, uint protect);",
                "  [DllImport(\"kernel32.dll\")] static extern bool VirtualFreeEx(IntPtr p, IntPtr at, UIntPtr size, uint type);",
                "  [DllImport(\"kernel32.dll\")] static extern bool ReadProcessMemory(IntPtr p, IntPtr at, byte[] buf, UIntPtr size, out UIntPtr read);",
                "  [DllImport(\"kernel32.dll\")] static extern bool WriteProcessMemory(IntPtr p, IntPtr at, byte[] buf, UIntPtr size, out UIntPtr wrote);",
                "  [DllImport(\"kernel32.dll\")] static extern bool CloseHandle(IntPtr h);",
                "  public struct POINT { public int X, Y; }",
                "  static IntPtr ListView() {",
                "    IntPtr view = FindWindowEx(FindWindow(\"Progman\", null), IntPtr.Zero, \"SHELLDLL_DefView\", null);",
                "    IntPtr w = IntPtr.Zero;",
                "    while (view == IntPtr.Zero && (w = FindWindowEx(IntPtr.Zero, w, \"WorkerW\", null)) != IntPtr.Zero) view = FindWindowEx(w, IntPtr.Zero, \"SHELLDLL_DefView\", null);",
                "    return view == IntPtr.Zero ? IntPtr.Zero : FindWindowEx(view, IntPtr.Zero, \"SysListView32\", null);",
                "  }",
                "  public static string[] Icons() {",
                "    IntPtr lv = ListView();",
                "    if (lv == IntPtr.Zero) return new string[0];",
                "    uint pid; GetWindowThreadProcessId(lv, out pid);",
                "    IntPtr proc = OpenProcess(0x0008 | 0x0010 | 0x0020, false, pid);",
                "    if (proc == IntPtr.Zero) return new string[0];",
                "    IntPtr mem = VirtualAllocEx(proc, IntPtr.Zero, (UIntPtr) 4096, 0x1000, 0x04);",
                "    int count = (int) SendMessage(lv, 0x1004, IntPtr.Zero, IntPtr.Zero);",
                "    string[] result = new string[count];",
                "    UIntPtr n;",
                "    for (int i = 0; i < count; i++) {",
                "      SendMessage(lv, 0x1010, (IntPtr) i, mem); // LVM_GETITEMPOSITION",
                "      byte[] pt = new byte[8]; ReadProcessMemory(proc, mem, pt, (UIntPtr) 8, out n);",
                "      POINT p = new POINT { X = BitConverter.ToInt32(pt, 0), Y = BitConverter.ToInt32(pt, 4) };",
                "      ClientToScreen(lv, ref p);",
                "      byte[] item = new byte[88]; // LVITEMW (64-bit)",
                "      BitConverter.GetBytes(0x1u).CopyTo(item, 0);              // mask = LVIF_TEXT",
                "      BitConverter.GetBytes(i).CopyTo(item, 4);                  // iItem",
                "      BitConverter.GetBytes(mem.ToInt64() + 1024).CopyTo(item, 24); // pszText",
                "      BitConverter.GetBytes(260).CopyTo(item, 32);               // cchTextMax",
                "      WriteProcessMemory(proc, mem, item, (UIntPtr) item.Length, out n);",
                "      SendMessage(lv, 0x1073, (IntPtr) i, mem); // LVM_GETITEMTEXTW",
                "      byte[] text = new byte[520]; ReadProcessMemory(proc, mem + 1024, text, (UIntPtr) 520, out n);",
                "      string name = Encoding.Unicode.GetString(text); int end = name.IndexOf('\\0'); if (end >= 0) name = name.Substring(0, end);",
                "      result[i] = name + \"|\" + p.X + \",\" + p.Y;",
                "    }",
                "    VirtualFreeEx(proc, mem, UIntPtr.Zero, 0x8000);",
                "    CloseHandle(proc);",
                "    return result;",
                "  }",
                "  public static bool Place(string name, int x, int y) {",
                "    int dot = name.LastIndexOf('.'); string stem = dot > 0 ? name.Substring(0, dot) : name;",
                "    for (int tries = 0; tries < 40; tries++) {",
                "      string[] icons = Icons();",
                "      for (int i = 0; i < icons.Length; i++) {",
                "        string shown = icons[i].Substring(0, icons[i].LastIndexOf('|'));",
                "        if (shown != name && shown != stem) continue;",
                "        IntPtr lv = ListView(); POINT p = new POINT { X = x, Y = y }; ScreenToClient(lv, ref p);",
                "        SendMessage(lv, 0x100F, (IntPtr) i, (IntPtr) ((p.Y << 16) | (p.X & 0xFFFF))); // LVM_SETITEMPOSITION",
                "        return true;",
                "      }",
                "      System.Threading.Thread.Sleep(150); // (Explorer shows a new file a moment after it's made)",
                "    }",
                "    return false;",
                "  }",
                "}",
                "'@");
    }

    /** What the icon script said: the desktop folder, and the icons. */
    record Layout(Path folder, List<Icon> icons) {
    }

    static Layout read(String output) {
        Path folder = null;
        List<Icon> icons = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (line.startsWith("DESKTOP|")) {
                try {
                    folder = Path.of(line.substring(8).strip());
                } catch (RuntimeException badPath) {
                    folder = null;
                }
                continue;
            }
            int bar = line.lastIndexOf('|');
            if (bar <= 0) continue;
            String[] xy = line.substring(bar + 1).split(",");
            try {
                icons.add(new Icon(line.substring(0, bar), Integer.parseInt(xy[0].strip()), Integer.parseInt(xy[1].strip())));
            } catch (RuntimeException notAnIcon) {
                // skip it
            }
        }
        return new Layout(folder, icons);
    }

    /** The script that asks Finder (Mac) to move one icon (name, x, y: its middle, in points), once it's there. */
    static final String FINDER_PLACE = String.join("\n",
            "on run argv",
            "  repeat 24 times",
            "    try",
            "      tell application \"Finder\" to set desktop position of item (item 1 of argv) of desktop to {(item 2 of argv) as integer, (item 3 of argv) as integer}",
            "      return \"placed\"",
            "    end try",
            "    delay 0.25",
            "  end repeat",
            "end run");

    /**
     * Moves a file's icon on the desktop to a spot (Java's pixels, the top middle of its picture, as spot says), like you'd
     * drag it there. Windows, or a Mac where Finder's allowed (askFinder); if the desktop arranges itself, it stays
     * where the desktop puts it. Takes a moment: not on the Swing thread. Whether it moved it.
     */
    static boolean place(Path file, double javaX, double javaY, boolean askFinder) {
        String name = file.getFileName().toString();
        try {
            if (Platform.MAC) {
                if (!askFinder) return false;
                Process p = new ProcessBuilder("osascript", "-e", FINDER_PLACE, name, String.valueOf((int) Math.round(javaX)), String.valueOf((int) Math.round(javaY + 28)))
                        .redirectErrorStream(true).start();
                String out = output(p, 20);
                return out != null && out.contains("placed");
            }
            if (!Platform.WINDOWS) return false;
            java.awt.Point real = Clawdtop.toReal(javaX - 37, javaY - 8); // (the icon's top-left, in Windows' pixels: see spot)
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                    "-EncodedCommand", java.util.Base64.getEncoder().encodeToString(placeScript(name, real.x, real.y).getBytes(StandardCharsets.UTF_16LE)))
                    .redirectErrorStream(true).start();
            String out = output(p, 20);
            return out != null && out.contains("placed");
        } catch (Exception cant) {
            return false;
        }
    }

    /** The script that asks Finder (Mac) for the desktop's icons: name|x,y, the middle of each icon, in points. */
    static final String FINDER_SCRIPT = String.join("\n",
            "set out to \"\"",
            "tell application \"Finder\"",
            "  repeat with i in (get items of desktop)",
            "    try",
            "      set p to desktop position of i",
            "      set out to out & (name of i) & \"|\" & ((item 1 of p) as integer) & \",\" & ((item 2 of p) as integer) & linefeed",
            "    end try",
            "  end repeat",
            "end tell",
            "return out");

    /**
     * Reads the desktop (Windows: its icon list; Mac: asks Finder, which macOS checks with you the first time; null on
     * Linux, or if it couldn't). Takes about half a second: not on the Swing thread.
     */
    static Layout look() {
        if (Platform.MAC) {
            try {
                Process p = new ProcessBuilder("osascript", "-e", FINDER_SCRIPT).redirectErrorStream(true).start();
                String out = output(p, 60);
                if (out == null || p.exitValue() != 0) return null; // (not allowed, or Finder's stuck)
                Layout icons = read(out);
                return new Layout(Path.of(System.getProperty("user.home"), "Desktop"), icons.icons());
            } catch (Exception cant) {
                return null;
            }
        }
        if (!Platform.WINDOWS) return null;
        try {
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                    "-EncodedCommand", java.util.Base64.getEncoder().encodeToString(iconScript().getBytes(StandardCharsets.UTF_16LE))).redirectErrorStream(true).start();
            String out = output(p, 15);
            if (out == null) return null; // (Explorer's stuck)
            Layout layout = read(out);
            return layout.folder() == null ? null : layout;
        } catch (Exception cant) {
            return null;
        }
    }

    /**
     * Where an icon is on the screen, in Java's pixels: {x, y} of the top middle of its picture (where he lands on it).
     * Windows says the icon's top-left in real pixels; Finder says its middle in points.
     */
    static double[] spot(Icon icon, java.awt.Rectangle screen, double scale) {
        if (Platform.MAC) return new double[] {icon.x(), icon.y() - 28};
        java.awt.geom.Point2D.Double at = Clawdtop.toJava(icon.x(), icon.y()); // (Windows' pixels to Java's, on the icon's own monitor)
        return new double[] {at.x + 37, at.y + 8};
    }

    /** What a program says, if it finishes in time (else it's stopped, and null). Reads and waits at once: a hung program can't hang him. */
    static String output(Process p, int seconds) throws InterruptedException {
        var said = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
        if (!p.waitFor(seconds, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return null;
        }
        try {
            return said.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            return null;
        }
    }

    /** The file an icon is showing (its name, with or without the extension), or null if it isn't a plain file there. */
    static Path fileFor(Icon icon, Path folder) {
        try (var files = Files.list(folder)) {
            for (Path f : files.toList()) {
                String name = f.getFileName().toString();
                String stem = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
                if ((name.equals(icon.name()) || stem.equals(icon.name())) && Files.isRegularFile(f)) return f;
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    // ---- Tidying ----

    /** The folders in Neat. */
    static final List<String> CATEGORIES = List.of("Pictures", "Videos", "Music", "Documents", "Spreadsheets", "Slides", "Zips", "Installers", "Code", "Other");

    /** Which folder in Neat a file goes in (by its type), or null if it stays put (shortcuts, and his own). */
    static String category(Path file) {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.equals("desktop.ini") || n.endsWith(".lnk") || n.endsWith(".url") || n.startsWith("~$") || n.startsWith(".")) return null;
        String ext = n.contains(".") ? n.substring(n.lastIndexOf('.') + 1) : "";
        return switch (ext) {
            case "png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "svg", "ico", "tif", "tiff" -> "Pictures";
            case "mp4", "mov", "avi", "mkv", "wmv", "webm", "m4v" -> "Videos";
            case "mp3", "wav", "flac", "m4a", "ogg", "aac", "wma", "mid", "midi" -> "Music";
            case "pdf", "doc", "docx", "txt", "rtf", "odt", "md", "pages" -> "Documents";
            case "xls", "xlsx", "csv", "ods", "numbers" -> "Spreadsheets";
            case "ppt", "pptx", "key", "odp" -> "Slides";
            case "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> "Zips";
            case "exe", "msi", "dmg", "pkg", "appx", "msix", "msixbundle" -> "Installers";
            case "java", "py", "js", "ts", "html", "css", "json", "xml", "c", "cpp", "h", "cs", "go", "rs", "sh", "bat", "ps1", "jar" -> "Code";
            default -> "Other";
        };
    }

    /** Moves a file into Neat/category on the desktop (a new name if that's taken). Where it went, or null if it couldn't. */
    static Path tidy(Path file, Path desktop) {
        String category = category(file);
        if (category == null) return null;
        try {
            Path into = desktop.resolve("Neat").resolve(category);
            Files.createDirectories(into);
            Path to = free(into.resolve(file.getFileName().toString()));
            return Files.move(file, to);
        } catch (IOException | RuntimeException inUse) {
            return null;
        }
    }

    /** The same name, or "name (2).ext" and so on if that's taken. */
    static Path free(Path wanted) {
        if (!Files.exists(wanted)) return wanted;
        String name = wanted.getFileName().toString();
        String stem = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name, ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
        for (int i = 2; ; i++) {
            Path p = wanted.resolveSibling(stem + " (" + i + ")" + ext);
            if (!Files.exists(p)) return p;
        }
    }
}
