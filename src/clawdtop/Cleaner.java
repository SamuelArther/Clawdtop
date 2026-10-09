package clawdtop;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clawd's folder cleaning. He only ever looks for real junk, never touches anything outside the folder you showed him,
 * and nothing is deleted for good: everything he cleans goes to the Recycle Bin, so you can always put it back.
 *
 * Safe junk (temp files, Windows' thumbnail caches, Mac leftovers, empty folders) he asks about all at once. Things that
 * might matter (old installers, "(1)" copies) he asks about one at a time.
 */
public final class Cleaner {
    /** One thing he'd clean, how big it is, and why it's junk (said to you in plain words). */
    public record Item(Path path, long size, String why, boolean risky) {
    }

    /** Everything he found in a folder. */
    public record Plan(Path folder, List<Item> safe, List<Item> risky) {
        public long safeSize() {
            return safe.stream().mapToLong(Item::size).sum();
        }
    }

    private static final int MAX_DEPTH = 6;
    private static final int MAX_ITEMS = 5000; // a huge folder: he stops looking after this many, rather than taking forever
    private static final Pattern COPY = Pattern.compile("(.*) \\((\\d+)\\)(\\.[^.]*)?");
    private static final Duration OLD_INSTALLER = Duration.ofDays(30);

    private Cleaner() {
    }

    /** Why he won't clean this folder (in plain words), or null if he will. */
    public static String refuse(Path folder) {
        if (folder == null) return "I couldn't tell which folder that is.";
        Path f = folder.toAbsolutePath().normalize();
        if (!Files.isDirectory(f)) return "That folder isn't there anymore.";
        if (f.getParent() == null) return "That's a whole drive. Pick a folder inside it.";
        String path = f.toString().toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        if (Platform.WINDOWS && (path.startsWith("\\\\") || !fixedDrive(f))) {
            return "That's on a network or USB drive. Those have no " + BIN + ", so I won't clean there.";
        }
        if (home.startsWith(f)) return "That folder holds too much that matters. Show me one inside it, like Downloads.";
        if (Platform.WINDOWS && f.getNameCount() >= 1) { // Windows and programs' folders on any drive (D:\Program Files too)
            String first = f.getName(0).toString().toLowerCase(Locale.ROOT);
            if (List.of("windows", "program files", "program files (x86)", "programdata", "$recycle.bin", "system volume information").contains(first)) {
                return "That's where Windows and your programs live. I'll leave it alone.";
            }
        }
        String windows = env("SystemRoot", "C:\\Windows");
        List<String> off = new ArrayList<>(List.of(windows, env("ProgramFiles", "C:\\Program Files"),
                env("ProgramFiles(x86)", "C:\\Program Files (x86)"), env("ProgramData", "C:\\ProgramData")));
        for (String o : off) {
            String low = o.toLowerCase(Locale.ROOT);
            if (path.equals(low) || path.startsWith(low + "\\")) return "That's where Windows and your programs live. I'll leave it alone.";
        }
        boolean yours = f.startsWith(home) || f.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize());
        if (!Platform.WINDOWS && !yours) {
            for (String o : List.of("/bin", "/boot", "/dev", "/etc", "/lib", "/opt", "/proc", "/sbin", "/sys", "/usr", "/var", "/System",
                    "/Library", "/Applications", "/private")) {
                if (f.toString().equals(o) || f.toString().startsWith(o + "/")) return "That's where your computer's system lives. I'll leave it alone.";
            }
            if (f.equals(home.resolve("Library"))) return "That folder holds too much that matters. Show me one inside it, like Downloads.";
        }
        if (f.equals(home) || f.equals(home.resolve("AppData")) || f.equals(home.resolve("AppData").resolve("Roaming"))
                || f.equals(home.resolve("AppData").resolve("Local")) || f.equals(home.resolve("OneDrive"))) {
            return "That folder holds too much that matters. Show me one inside it, like Downloads.";
        }
        return null;
    }

    /** Whether this folder is on an ordinary disk inside the computer (Windows), the only kind with a Recycle Bin. */
    static boolean fixedDrive(Path folder) {
        try {
            java.lang.foreign.Linker linker = java.lang.foreign.Linker.nativeLinker();
            java.lang.foreign.SymbolLookup k32 = java.lang.foreign.SymbolLookup.libraryLookup("kernel32", java.lang.foreign.Arena.global());
            java.lang.invoke.MethodHandle type = linker.downcallHandle(k32.find("GetDriveTypeW").orElseThrow(),
                    java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.ADDRESS));
            try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
                String root = folder.getRoot().toString();
                java.lang.foreign.MemorySegment name = arena.allocateFrom(root.endsWith("\\") ? root : root + "\\", java.nio.charset.StandardCharsets.UTF_16LE);
                return (int) type.invokeExact(name) == 3; // DRIVE_FIXED
            }
        } catch (Throwable cantTell) {
            return true; // (then the Recycle Bin check decides)
        }
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v != null ? v : fallback;
    }

    /** Looks through a folder (and the folders in it) for junk. Never changes anything. */
    public static Plan scan(Path folder, Instant now) throws IOException {
        List<Item> safe = new ArrayList<>();
        List<Item> risky = new ArrayList<>();
        walk(folder, 0, now, safe, risky);
        return new Plan(folder, safe, risky);
    }

    /** Looks through one folder; true if it ends up empty (so the folder holding it can clean it away too). */
    private static boolean walk(Path dir, int depth, Instant now, List<Item> safe, List<Item> risky) throws IOException {
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) entries.add(p);
        } catch (IOException | SecurityException noAccess) {
            return false;
        }
        entries.sort(null);
        int kept = 0;
        for (Path p : entries) {
            if (safe.size() + risky.size() >= MAX_ITEMS) return false;
            BasicFileAttributes attrs;
            try {
                attrs = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            } catch (IOException e) {
                kept++;
                continue;
            }
            if (attrs.isSymbolicLink() || attrs.isOther() || hiddenSystem(p)) { // shortcuts into elsewhere, and Windows' own files
                kept++;
                continue;
            }
            if (attrs.isDirectory()) {
                String name = p.getFileName().toString();
                if (name.startsWith(".") || name.equals("node_modules") || bundle(name)) { // .git and friends, code packages, and Mac apps and libraries: hands off
                    kept++;
                    continue;
                }
                if (depth < MAX_DEPTH && walk(p, depth + 1, now, safe, risky)) {
                    safe.add(new Item(p, 0, "an empty folder", false));
                } else {
                    kept++;
                }
                continue;
            }
            String why = safeJunk(p, attrs);
            if (why != null) {
                safe.add(new Item(p, attrs.size(), why, false));
                continue;
            }
            String maybe = riskyJunk(p, attrs, now, entries);
            if (maybe != null) {
                risky.add(new Item(p, attrs.size(), maybe, true));
            }
            kept++; // a risky one stays until you say so, so its folder isn't empty
        }
        return kept == 0 && depth > 0;
    }

    private static boolean hiddenSystem(Path p) {
        try {
            DosFileAttributes dos = Files.readAttributes(p, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return dos.isSystem();
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }

    /** Junk nobody misses, or null. */
    static String safeJunk(Path p, BasicFileAttributes attrs) {
        String name = p.getFileName().toString();
        String low = name.toLowerCase(Locale.ROOT);
        if (low.equals("thumbs.db") || low.equals("ehthumbs.db")) return "Windows' picture-preview cache (it makes a new one)";
        if (low.equals(".ds_store")) return "a Mac leftover";
        if (name.startsWith("._") && attrs.size() <= 8192) return "a Mac leftover";
        if (low.endsWith(".tmp") || low.endsWith(".temp")) return "a temporary file";
        if (name.startsWith("~$") && attrs.size() <= 1024) return "a leftover from a closed Office file";
        if (low.endsWith(".crdownload") || low.endsWith(".partial") || low.endsWith(".part")) return "a download that never finished";
        return null;
    }

    /** Might be junk, but could matter, so he asks about it on its own: or null. */
    static String riskyJunk(Path p, BasicFileAttributes attrs, Instant now, List<Path> siblings) {
        String name = p.getFileName().toString();
        String low = name.toLowerCase(Locale.ROOT);
        boolean old = attrs.lastModifiedTime().toInstant().isBefore(now.minus(OLD_INSTALLER));
        if (old && (low.endsWith(".msi") || low.endsWith(".msix") || ((low.contains("setup") || low.contains("install")) && !low.contains("unins") && low.endsWith(".exe")))) {
            long days = Duration.between(attrs.lastModifiedTime().toInstant(), now).toDays();
            return "an installer from " + (days >= 60 ? days / 30 + " months" : days + " days") + " ago";
        }
        Matcher m = COPY.matcher(name);
        if (m.matches()) {
            Path original = p.resolveSibling(m.group(1) + (m.group(3) == null ? "" : m.group(3)));
            if (siblings.contains(original) && sameBytes(p, original)) return "an exact copy of " + original.getFileName();
        }
        return null;
    }

    /** Whether two files hold exactly the same bytes. */
    static boolean sameBytes(Path a, Path b) {
        try {
            if (Files.size(a) != Files.size(b)) return false;
            try (InputStream x = Files.newInputStream(a); InputStream y = Files.newInputStream(b)) {
                byte[] bx = new byte[1 << 16];
                byte[] by = new byte[1 << 16];
                while (true) {
                    int nx = x.readNBytes(bx, 0, bx.length);
                    int ny = y.readNBytes(by, 0, by.length);
                    if (nx != ny || !Arrays.equals(bx, 0, nx, by, 0, ny)) return false;
                    if (nx == 0) return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
    }

    /** Whether this computer lets him use the Recycle Bin. He won't clean anything without it. */
    public static boolean canRecycle() {
        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
    }

    /** Moves things to the Recycle Bin (deepest first, so a folder's junk goes before the folder). How many went. */
    public static int recycle(List<Item> items) {
        if (!canRecycle()) return 0;
        List<Item> order = new ArrayList<>(items);
        order.sort((a, b) -> Integer.compare(b.path().getNameCount(), a.path().getNameCount()));
        int moved = 0;
        for (Item item : order) {
            try {
                if (item.size() == 0 && Files.isDirectory(item.path())) {
                    try (DirectoryStream<Path> left = Files.newDirectoryStream(item.path())) {
                        if (left.iterator().hasNext()) continue; // something's in it after all: it stays
                    }
                }
                if (Desktop.getDesktop().moveToTrash(item.path().toFile())) moved++;
            } catch (IOException | RuntimeException e) {
                // in use, or gone already: skip it
            }
        }
        return moved;
    }

    /** A size in friendly words: "12 KB", "3.4 MB", "1.2 GB". */
    public static String size(long bytes) {
        if (bytes < 1024) return bytes + " bytes";
        if (bytes < 1024 * 1024) return bytes / 1024 + " KB";
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
        return String.format(Locale.ROOT, "%.1f GB", bytes / 1073741824.0);
    }

    /**
     * A Mac "package": looks like a folder, but it's really one thing (an app, a photo library, a project). Anything
     * inside belongs to it, so he never cleans inside one (an app missing a file can stop opening).
     */
    static boolean bundle(String folderName) {
        String n = folderName.toLowerCase(java.util.Locale.ROOT);
        return n.matches(".+\\.(app|bundle|framework|plugin|kext|appex|photoslibrary|photolibrary|imovielibrary|fcpbundle|logicx|band|xcodeproj|xcworkspace"
                + "|playground|pkg|mpkg|rtfd|pages|numbers|key|aplibrary|musiclibrary|tvlibrary|theater|component|vst|vst3|aaxplugin|saver|prefpane|qlgenerator|mdimporter)");
    }

    /** Where things he cleans up go: the Recycle Bin on Windows, the Trash on a Mac or Linux. */
    static final String BIN = Platform.WINDOWS ? "Recycle Bin" : "Trash";
}
