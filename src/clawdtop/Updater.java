package clawdtop;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeping Clawd up to date: asks GitHub for his newest release, and if there's a newer him, downloads it and swaps it in
 * (a tiny helper waits for this Clawd to close, puts the new jar in place, and starts him again).
 */
final class Updater {
    static final String VERSION = "2.7";
    static final String REPO = "SamuelArther/Clawdtop";

    private Updater() {
    }

    /** A newer release: its version ("1.0.1"), where its jar is, and what's new. */
    record Release(String version, String jarUrl, String notes) {
    }

    /** Whether version a is newer than b ("1.0.10" is newer than "1.0.9"). */
    static boolean newer(String a, String b) {
        String[] x = a.replaceFirst("^[vV]", "").split("[.-]"), y = b.replaceFirst("^[vV]", "").split("[.-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? number(x[i]) : 0, q = i < y.length ? number(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Reads GitHub's answer about the latest release (null if it has no jar). */
    static Release parse(String json) {
        Matcher tag = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!tag.find()) return null;
        Matcher jar = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+/Clawdtop\\.jar)\"").matcher(json);
        if (!jar.find()) return null;
        String notes = jsonText(json, "body"); // (read by hand: a regex here overflows the stack on long notes)
        return new Release(tag.group(1).replaceFirst("^[vV]", ""), jar.group(1), notes);
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /** The newest release if it's newer than this one; null if he's up to date (or there's no internet). */
    static Release check() {
        try {
            HttpRequest ask = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + REPO + "/releases/latest"))
                    .header("Accept", "application/vnd.github+json").header("User-Agent", "Clawdtop/" + VERSION)
                    .timeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> answer = client().send(ask, HttpResponse.BodyHandlers.ofString());
            if (answer.statusCode() != 200) return null;
            Release latest = parse(answer.body());
            return latest != null && newer(latest.version(), VERSION) ? latest : null;
        } catch (IOException | InterruptedException | RuntimeException | StackOverflowError offline) {
            return null;
        }
    }

    /** A text value in JSON ("key": "value"), unescaped, or "" if it isn't there. */
    static String jsonText(String json, String key) {
        int at = json.indexOf("\"" + key + "\"");
        if (at < 0) return "";
        int colon = json.indexOf(':', at + key.length() + 2);
        if (colon < 0) return "";
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != '"') return "";
        StringBuilder out = new StringBuilder();
        for (i++; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') return out.toString();
            if (c == '\\' && i + 1 < json.length()) {
                char e = json.charAt(++i);
                switch (e) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> { }
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            try {
                                out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                            } catch (NumberFormatException broken) {
                                // skip it
                            }
                            i += 4;
                        }
                    }
                    default -> out.append(e);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Downloads the new jar next to the running one and starts the helper that swaps it in once this Clawd closes.
     * Returns false if it couldn't (then nothing has changed). The caller then closes Clawd.
     */
    static boolean install(Release release) {
        Path jar = Install.jar();
        if (jar == null) return false;
        Path fresh = jar.resolveSibling("Clawdtop-" + release.version() + ".jar.new");
        try {
            HttpRequest get = HttpRequest.newBuilder(URI.create(release.jarUrl())).header("User-Agent", "Clawdtop/" + VERSION)
                    .timeout(Duration.ofMinutes(3)).build();
            HttpResponse<InputStream> answer = client().send(get, HttpResponse.BodyHandlers.ofInputStream());
            if (answer.statusCode() != 200) return false;
            try (InputStream in = answer.body()) {
                Files.copy(in, fresh, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!looksLikeJar(fresh)) {
                Files.deleteIfExists(fresh);
                return false;
            }
            new ProcessBuilder(Install.javaw().toString(), "--enable-native-access=ALL-UNNAMED", "-cp", fresh.toString(),
                    "clawdtop.Updater", String.valueOf(ProcessHandle.current().pid()), jar.toString()).start();
            return true;
        } catch (IOException | InterruptedException | RuntimeException failed) {
            try {
                Files.deleteIfExists(fresh);
            } catch (IOException ignored) {
                // next time
            }
            return false;
        }
    }

    /** A real jar (a zip with his main class in it), not an error page. */
    static boolean looksLikeJar(Path file) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file.toFile())) {
            return zip.getEntry("clawdtop/Clawdtop.class") != null;
        } catch (IOException notAZip) {
            return false;
        }
    }

    /** Leftovers from an update (the downloaded copy the helper ran from). */
    static void tidy() {
        Path jar = Install.jar();
        if (jar == null || jar.getParent() == null) return;
        try (var files = Files.list(jar.getParent())) {
            files.filter(f -> f.getFileName().toString().endsWith(".jar.new")).forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException inUse) {
                    // still running from it: next time
                }
            });
        } catch (IOException ignored) {
            // fine
        }
    }

    /**
     * The helper, run from the new jar: java -cp Clawdtop-x.jar.new clawdtop.Updater (old Clawd's pid) (jar to replace).
     * Waits for the old Clawd to close, puts the new jar in its place, and starts him.
     */
    public static void main(String[] args) throws Exception {
        long pid = Long.parseLong(args[0]);
        Path jar = Path.of(args[1]);
        Path fresh = Path.of(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        ProcessHandle.of(pid).ifPresent(old -> {
            try {
                old.onExit().get(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception tooSlow) {
                // go ahead anyway
            }
        });
        Path temp = jar.resolveSibling(jar.getFileName() + ".tmp");
        for (int tries = 0; tries < 20; tries++) { // Windows can hold on to the old file for a moment
            try {
                Files.copy(fresh, temp, StandardCopyOption.REPLACE_EXISTING); // copied next to it first, then swapped in whole
                try {
                    Files.move(temp, jar, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException notHere) {
                    Files.move(temp, jar, StandardCopyOption.REPLACE_EXISTING);
                }
                break;
            } catch (IOException busy) {
                Thread.sleep(500);
            }
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException stillBusy) {
            // tidied up next time
        }
        new ProcessBuilder(Install.javaw().toString(), "--enable-native-access=ALL-UNNAMED", "-jar", jar.toString()).start();
    }
}
