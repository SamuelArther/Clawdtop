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
                if (!install(progress)) {
                    progress.accept("I couldn't install my brain. Is the internet on?");
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

    /** Downloads Ollama's own installer and runs it quietly, for you only. */
    private static boolean install(Consumer<String> progress) {
        try {
            Path temp = Files.createTempDirectory("clawdtop-brain");
            if (Platform.WINDOWS) {
                Path setup = temp.resolve("OllamaSetup.exe");
                if (!download(WINDOWS_SETUP, setup)) return false;
                Process p = new ProcessBuilder(setup.toString(), "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/SP-").start();
                if (!p.waitFor(10, java.util.concurrent.TimeUnit.MINUTES)) return false; // stuck: give up (he says so)
                return Files.exists(ollama());
            }
            if (Platform.MAC) {
                Path zip = temp.resolve("Ollama-darwin.zip");
                if (!download(MAC_ZIP, zip)) return false;
                Path apps = Path.of(System.getProperty("user.home"), "Applications");
                Files.createDirectories(apps);
                new ProcessBuilder("ditto", "-x", "-k", zip.toString(), apps.toString()).start().waitFor(5, java.util.concurrent.TimeUnit.MINUTES);
                return Files.exists(ollama());
            }
        } catch (IOException | InterruptedException e) {
            return false;
        }
        return false;
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

    private static boolean download(String url, Path to) {
        try {
            HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<Path> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(to));
            return r.statusCode() == 200 && Files.size(to) > 1_000_000;
        } catch (IOException | InterruptedException e) {
            return false;
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
