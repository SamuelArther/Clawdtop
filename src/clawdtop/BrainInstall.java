package clawdtop;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Installing his brain: Ollama (free, from ollama.com; its installer is signed) and then a small model. It runs in the
 * background with a notice, and only ever installs for you (no admin). On Linux, Ollama needs an admin to install, so
 * there he tells you the one command to run instead.
 */
final class BrainInstall {
    private BrainInstall() {
    }

    static final String WINDOWS_SETUP = "https://ollama.com/download/OllamaSetup.exe";
    static final String MAC_ZIP = "https://ollama.com/download/Ollama-darwin.zip";

    /** Whether Ollama's already on this computer (then using it needs no download, and no asking). */
    static boolean installed() {
        Path o = ollama();
        return o != null && Files.exists(o);
    }

    /** About how much it all downloads: Ollama itself, plus the model. */
    static String totalSize(String brainChoice) {
        return "about " + (Brain.downloadSize(brainChoice).contains("1 GB") ? "2 GB" : "1.5 GB");
    }

    /** Where Ollama is (wherever it was installed), or where it goes if it isn't yet (just for you); null on Linux. */
    static Path ollama() {
        if (Platform.WINDOWS) {
            java.util.List<Path> places = new java.util.ArrayList<>();
            String local = System.getenv("LOCALAPPDATA"), programs = System.getenv("ProgramFiles");
            if (local != null) places.add(Path.of(local, "Programs", "Ollama", "ollama.exe"));
            if (programs != null) places.add(Path.of(programs, "Ollama", "ollama.exe"));
            for (String dir : System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator)) {
                try {
                    if (!dir.isBlank()) places.add(Path.of(dir.strip(), "ollama.exe"));
                } catch (RuntimeException badPath) {
                    // skip it
                }
            }
            for (Path p : places) if (Files.isRegularFile(p)) return p;
            return places.isEmpty() ? null : places.get(0);
        }
        if (Platform.MAC) {
            Path mine = Path.of(System.getProperty("user.home"), "Applications", "Ollama.app"), everyone = Path.of("/Applications", "Ollama.app");
            return Files.exists(everyone) && !Files.exists(mine) ? everyone : mine;
        }
        return null;
    }

    /**
     * Makes sure his brain is ready: Ollama installed and running, and the model downloaded. Says how it's going through
     * progress. Whether it worked.
     */
    static boolean ensure(Brain brain, String model, Consumer<String> progress) {
        if (!brain.running()) {
            Path ollama = ollama();
            if (ollama == null) {
                progress.accept("To give me a brain on Linux, run this once in a terminal:\ncurl -fsSL https://ollama.com/install.sh | sh");
                return false;
            }
            if (!Files.exists(ollama)) {
                progress.accept("Installing my brain (Ollama, free)... this takes a few minutes.");
                String trouble = install();
                if (trouble != null) {
                    progress.accept("I couldn't install my brain (" + trouble + "). I'll try again when you ask me something.");
                    return false;
                }
            }
            start(ollama);
            for (int i = 0; i < 60 && !brain.running(); i++) sleep(1000);
            if (!brain.running()) {
                progress.accept("My brain is installed, but it won't wake up. Try restarting your computer?");
                return false;
            }
        }
        if (!brain.has(model)) {
            progress.accept("Downloading what I know (" + Brain.downloadSizeOf(model) + ", just once)...");
            int[] said = {0};
            if (!brain.download(model, percent -> { // a word every 25%, so you know it's going
                if (percent >= said[0] + 25 && percent < 100) {
                    said[0] = percent / 25 * 25;
                    progress.accept("Downloading my brain... " + said[0] + "% done.");
                }
            })) {
                progress.accept("The download didn't finish. Is the internet on?");
                return false;
            }
        }
        return true;
    }

    /**
     * Downloads Ollama's own installer and runs it quietly, for you only. Null if it worked, else what went wrong (in a
     * few words). The download goes in one temp folder that's always cleaned up after.
     */
    private static String install() {
        Path temp = Path.of(System.getProperty("java.io.tmpdir"), "clawdtop-brain");
        try {
            Files.createDirectories(temp);
            if (Platform.WINDOWS) {
                Path setup = temp.resolve("OllamaSetup.exe");
                String trouble = download(WINDOWS_SETUP, setup);
                if (trouble != null) return trouble;
                Process p = new ProcessBuilder(setup.toString(), "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/SP-").start();
                if (!p.waitFor(10, java.util.concurrent.TimeUnit.MINUTES)) {
                    p.destroyForcibly();
                    return "its installer got stuck";
                }
                return Files.exists(ollama()) ? null : "its installer didn't finish";
            }
            if (Platform.MAC) {
                Path zip = temp.resolve("Ollama-darwin.zip");
                String trouble = download(MAC_ZIP, zip);
                if (trouble != null) return trouble;
                Path apps = Path.of(System.getProperty("user.home"), "Applications");
                Files.createDirectories(apps);
                new ProcessBuilder("ditto", "-x", "-k", zip.toString(), apps.toString()).start().waitFor(5, java.util.concurrent.TimeUnit.MINUTES);
                return Files.exists(ollama()) ? null : "unpacking it didn't work";
            }
            return "not on this kind of computer";
        } catch (IOException e) {
            return lowOnSpace(temp) ? "the disk is nearly full" : "something went wrong";
        } catch (InterruptedException e) {
            return "it was stopped";
        } finally {
            deleteAll(temp);
        }
    }

    private static boolean lowOnSpace(Path where) {
        try {
            return Files.getFileStore(where.getRoot()).getUsableSpace() < 3L * 1024 * 1024 * 1024;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static void deleteAll(Path folder) {
        try (var files = Files.walk(folder)) {
            for (Path f : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(f);
        } catch (IOException | RuntimeException e) {
            // (a file still in use: the temp folder's tidied by the computer later)
        }
    }

    /** Starts Ollama (with no window). */
    private static void start(Path ollama) {
        try {
            if (Platform.WINDOWS) {
                new ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command",
                        "Start-Process -WindowStyle Hidden -FilePath '" + ollama.toString().replace("'", "''") + "' -ArgumentList 'serve'").start();
            } else if (Platform.MAC) {
                new ProcessBuilder("open", "-g", ollama.toString()).start();
            }
        } catch (IOException e) {
            // it might be starting by itself anyway
        }
    }

    /** How long a download may go with nothing arriving before he gives up on it (a stalled connection). */
    static long stallMs = 60_000;

    /** Downloads a file. Null if it worked, else what went wrong. A download that stalls is given up on (it can't hang forever). */
    static String download(String url, Path to) {
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(10)).build();
        long[] lastBytes = {System.currentTimeMillis()};
        java.io.InputStream[] body = {null};
        Thread watchdog = new Thread(() -> { // nothing arriving for a minute: close it, which ends the read below
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    return;
                }
                if (System.currentTimeMillis() - lastBytes[0] > stallMs && body[0] != null) {
                    try {
                        body[0].close();
                    } catch (IOException ignored) {
                        // closed anyway
                    }
                    return;
                }
            }
        }, "clawd-download-watch");
        watchdog.setDaemon(true);
        watchdog.start();
        try {
            HttpResponse<java.io.InputStream> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (r.statusCode() != 200) return "the download page said no";
            body[0] = r.body();
            try (java.io.InputStream in = body[0]; var out = Files.newOutputStream(to)) {
                byte[] buffer = new byte[64 * 1024];
                for (int n; (n = in.read(buffer)) > 0; ) {
                    out.write(buffer, 0, n);
                    lastBytes[0] = System.currentTimeMillis();
                }
            }
            return Files.size(to) > 1_000_000 ? null : "the download was cut short";
        } catch (IOException e) {
            if (System.currentTimeMillis() - lastBytes[0] > stallMs) return "the download stalled";
            return lowOnSpace(to.toAbsolutePath().getParent()) ? "the disk is nearly full" : "no internet?";
        } catch (InterruptedException e) {
            return "it was stopped";
        } finally {
            watchdog.interrupt();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
