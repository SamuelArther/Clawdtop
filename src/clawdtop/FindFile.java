package clawdtop;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Find my essay", "where did I save the birthday pictures?": he looks through your usual folders (desktop, documents,
 * downloads, pictures, music, videos, and OneDrive's copies of them) for files with those words in their names.
 * Only names are looked at, never what's inside, and nothing leaves your computer.
 */
final class FindFile {
    private FindFile() {
    }

    private static final Pattern ASK = Pattern.compile("^(?:can you |please |help me )?(find my |find (?:the |a |some )?(?:file|document|doc|picture|photo|pic|song|video)s? "
            + "(?:called |named |of |about )?|where(?:'s| is| are) my |where did i (?:put|save|leave) (?:my |the )?)(.+?)(?: file| document| doc)?$");
    private static final List<String> FILLER = List.of("the", "a", "an", "my", "file", "files", "called", "named", "that", "one", "i", "made", "saved", "for",
            "of", "about", "from", "with", "and", "on", "in", "picture", "pictures", "photo", "photos", "pic", "pics", "song", "songs", "video", "videos",
            "document", "documents", "doc", "docs");
    /** Things you might ask "where's my..." about that aren't files (those are for his brain, not a search). */
    private static final List<String> NOT_FILES = List.of("phone", "keys", "key", "wallet", "glasses", "mom", "dad", "mum", "cat", "dog", "shoes", "shoe", "remote",
            "charger", "headphones", "airpods", "earbuds", "backpack", "car", "friend", "friends", "way", "mind", "brother", "sister", "homework folder", "socks",
            "jacket", "hat", "bike", "controller", "mouse", "keyboard", "pencil", "book", "lunch", "money", "family", "parents", "grandma", "grandpa");
    /** Words that make "where's my ..." sound like a file. */
    private static final Pattern FILEISH = Pattern.compile(".*\\b(essay|file|document|doc|pdf|picture|photo|pic|screenshot|video|song|resume|project|report|presentation|slides|spreadsheet"
            + "|homework|assignment|paper|notes|download|recording|drawing|zip|installer|save|world|mod)s?\\b.*|.*\\.[a-z0-9]{2,4}$");

    /** The words to look for, if that was a "find my file" question (else null). */
    static List<String> wordsIn(String question) {
        Matcher m = ASK.matcher(question.toLowerCase(Locale.ROOT).strip().replaceAll("[?!]+$", "").replaceAll("\\.+$", ""));
        if (!m.matches()) return null;
        String what = m.group(2).strip();
        if ((m.group(1).startsWith("find my") || m.group(1).startsWith("where")) && NOT_FILES.contains(what.replaceFirst("^(the|my) ", ""))) return null; // ("find my phone": not a file)
        if (m.group(1).startsWith("where") && !m.group(1).startsWith("where did") && !FILEISH.matcher(what).matches()) return null; // ("where are my keys")
        List<String> words = new ArrayList<>();
        for (String w : what.split("[\\s_\\-]+")) if (!w.isBlank() && !FILLER.contains(w)) words.add(w);
        return words.isEmpty() ? null : words;
    }

    /** Where to look: your usual folders (the ones that are there). */
    static List<Path> places(Path home) {
        List<Path> roots = new ArrayList<>();
        for (String name : new String[] {"Desktop", "Documents", "Downloads", "Pictures", "Music", "Videos", "Movies"}) roots.add(home.resolve(name));
        try (var top = Files.list(home)) { // OneDrive (and "OneDrive - School") keeps its own copies
            top.filter(p -> p.getFileName().toString().startsWith("OneDrive") && Files.isDirectory(p)).forEach(roots::add);
        } catch (IOException | RuntimeException none) {
            // just the usual ones
        }
        roots.removeIf(p -> !Files.isDirectory(p));
        return roots;
    }

    /**
     * The files whose names have all the words in them (newest first, at most 20), looking for up to this long.
     * Hidden folders, app folders and very deep folders are skipped.
     */
    static List<Path> search(List<String> words, List<Path> roots, long forMs) {
        long until = System.currentTimeMillis() + forMs;
        List<Path> found = new ArrayList<>();
        java.util.Set<Path> seen = new java.util.HashSet<>();
        for (Path root : roots) {
            try {
                Files.walkFileTree(root, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), 7, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (System.currentTimeMillis() > until) return FileVisitResult.TERMINATE;
                        String n = dir.getFileName() == null ? "" : dir.getFileName().toString();
                        if (!dir.equals(root) && (n.startsWith(".") || n.equals("node_modules") || n.equals("AppData") || n.equals("$RECYCLE.BIN"))) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (attrs.isRegularFile() && !n.equals("desktop.ini") && words.stream().allMatch(n::contains) && seen.add(file.toAbsolutePath())) found.add(file);
                        return found.size() > 400 ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException e) {
                        return FileVisitResult.CONTINUE; // (no permission there: skip it)
                    }
                });
            } catch (IOException | RuntimeException skip) {
                // next place
            }
        }
        found.sort(Comparator.comparingLong((Path p) -> {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }).reversed());
        return found.size() > 20 ? found.subList(0, 20) : found;
    }

    /** Where a file is, the way you'd say it: "Documents\School" (from your home folder). */
    static String whereIs(Path file, Path home) {
        Path folder = file.getParent();
        try {
            return home.relativize(folder).toString();
        } catch (IllegalArgumentException elsewhere) {
            return folder.toString();
        }
    }

    /** Opens the folder a file is in, with the file picked out (Windows and Mac; the folder on Linux). */
    static void showInFolder(Path file) {
        try {
            if (Platform.WINDOWS) new ProcessBuilder("explorer.exe", "/select," + file.toAbsolutePath()).start();
            else if (Platform.MAC) new ProcessBuilder("open", "-R", file.toAbsolutePath().toString()).start();
            else new ProcessBuilder("xdg-open", file.toAbsolutePath().getParent().toString()).start();
        } catch (IOException cant) {
            // (no file browser)
        }
    }

    static void open(Path file) {
        try {
            java.awt.Desktop.getDesktop().open(file.toFile());
        } catch (Exception cant) {
            showInFolder(file);
        }
    }

    /** "12 MB", "340 KB". */
    static String size(long bytes) {
        if (bytes < 1024) return bytes + " bytes";
        if (bytes < 1024 * 1024) return Math.round(bytes / 1024.0) + " KB";
        if (bytes < 1024L * 1024 * 1024) return Helpers.pretty(Math.round(bytes / 1024.0 / 1024 * 10) / 10.0) + " MB";
        return Helpers.pretty(Math.round(bytes / 1024.0 / 1024 / 1024 * 10) / 10.0) + " GB";
    }

    /** Whether a file in Downloads is still downloading (the browser's half-done file). */
    static boolean partial(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return Arrays.asList(".crdownload", ".part", ".partial", ".download", ".tmp", ".opdownload", ".!ut", ".aria2").stream().anyMatch(n::endsWith)
                || n.startsWith(".") || n.startsWith("~$") || n.equals("desktop.ini") || n.equals("thumbs.db");
    }
}
