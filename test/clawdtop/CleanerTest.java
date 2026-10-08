package clawdtop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Clawd practices cleaning on a pretend mini Windows PC in a temp folder: junk, real files and system folders, and a
 * pretend screen with a pretend File Explorer. Nothing real is ever touched, and nothing goes to the real Recycle Bin.
 */
public class CleanerTest {
    static void run() throws Exception {
        Instant now = Instant.parse("2026-10-08T20:00:00Z");
        Path pc = Files.createTempDirectory("clawd-mini-pc");
        Path windows = Files.createDirectories(pc.resolve("Windows").resolve("System32"));
        Files.writeString(windows.resolve("kernel.tmp"), "pretend Windows file");
        Files.createDirectories(pc.resolve("Program Files").resolve("Game"));
        Files.writeString(pc.resolve("Program Files").resolve("Game").resolve("game.exe"), "pretend program");
        Path me = Files.createDirectories(pc.resolve("Users").resolve("me"));
        Path downloads = Files.createDirectories(me.resolve("Downloads"));
        Files.createDirectories(me.resolve("Desktop"));
        Files.createDirectories(me.resolve("Documents"));

        // Junk he can clean all at once
        Files.writeString(downloads.resolve("report.tmp"), "temp");
        Files.writeString(downloads.resolve("Thumbs.db"), "cache");
        Files.writeString(downloads.resolve(".DS_Store"), "mac");
        Files.writeString(downloads.resolve("._photo.jpg"), "mac");
        Files.writeString(downloads.resolve("~$letter.docx"), "office");
        Files.writeString(downloads.resolve("movie.mp4.crdownload"), "half a movie");
        Files.createDirectories(downloads.resolve("Old Stuff").resolve("Empty"));
        // Things that might be junk: he asks about each one
        Path oldSetup = Files.writeString(downloads.resolve("setup_game.exe"), "old installer");
        Files.setLastModifiedTime(oldSetup, FileTime.from(now.minus(Duration.ofDays(95))));
        Path oldMsi = Files.writeString(downloads.resolve("tool.msi"), "old msi");
        Files.setLastModifiedTime(oldMsi, FileTime.from(now.minus(Duration.ofDays(40))));
        Files.writeString(downloads.resolve("photo.jpg"), "the same picture");
        Files.writeString(downloads.resolve("photo (1).jpg"), "the same picture");
        // Real things he must leave alone
        Path newSetup = Files.writeString(downloads.resolve("new_setup.exe"), "just downloaded");
        Files.setLastModifiedTime(newSetup, FileTime.from(now.minus(Duration.ofDays(2))));
        Files.writeString(downloads.resolve("notes.txt"), "my notes");
        Files.writeString(downloads.resolve("notes (1).txt"), "my newer notes"); // a "(1)" that's different: not a copy
        Files.createDirectories(downloads.resolve("Projects"));
        Files.writeString(downloads.resolve("Projects").resolve("Game.java"), "class Game {}");
        Files.writeString(downloads.resolve("Projects").resolve("build.tmp"), "temp in a project");

        // ---- Where he won't clean ----
        check("not the Windows folder", Cleaner.refuse(Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"))) != null, true);
        check("not Program Files", Cleaner.refuse(Path.of(System.getenv().getOrDefault("ProgramFiles", "C:\\Program Files"))) != null, true);
        check("not your whole user folder", Cleaner.refuse(Path.of(System.getProperty("user.home"))) != null, true);
        check("not a whole drive", Cleaner.refuse(pc.getRoot()) != null, true);
        check("not a folder that's gone", Cleaner.refuse(pc.resolve("Nope")) != null, true);
        check("but the mini PC's Downloads is fine", Cleaner.refuse(downloads), null);

        // ---- What he finds ----
        Cleaner.Plan plan = Cleaner.scan(downloads, now);
        List<String> safe = new ArrayList<>();
        for (Cleaner.Item i : plan.safe()) safe.add(downloads.relativize(i.path()).toString().replace('\\', '/'));
        safe.sort(null);
        check("the plain junk", safe, List.of(".DS_Store", "._photo.jpg", "Old Stuff", "Old Stuff/Empty", "Projects/build.tmp",
                "Thumbs.db", "movie.mp4.crdownload", "report.tmp", "~$letter.docx"));
        List<String> risky = new ArrayList<>();
        for (Cleaner.Item i : plan.risky()) risky.add(i.path().getFileName() + ": " + i.why());
        risky.sort(null);
        check("the maybe-junk he'll ask about one at a time", risky, List.of("photo (1).jpg: an exact copy of photo.jpg",
                "setup_game.exe: an installer from 3 months ago", "tool.msi: an installer from 40 days ago"));
        check("summary of the plain junk", CleanJob.summary(plan.safe()),
                "2 Mac leftovers, 1 unfinished downloads, 2 empty folders, 2 temporary files, 1 picture-preview caches, 1 Office leftovers");
        check("sizes in friendly words", Cleaner.size(512) + ", " + Cleaner.size(2048) + ", " + Cleaner.size(5_500_000) + ", " + Cleaner.size(3L << 30),
                "512 bytes, 2 KB, 5.2 MB, 3.0 GB");
        check("looking changes nothing", Files.exists(downloads.resolve("report.tmp")) && Files.exists(oldSetup), true);

        // ---- The whole job, on a pretend screen ----
        FakeUi ui = new FakeUi(downloads);
        ui.answers.add(0); // "Move the junk to the Recycle Bin?" Yes
        ui.answers.add(0); // first maybe-junk: Yes
        ui.answers.add(1); // second: No
        ui.answers.add(2); // third: Stop
        Body body = new Body();
        Pet pet = new Pet(9);
        CleanJob job = new CleanJob(ui);
        double home = 1800, ground = 1032;
        body.tick(33, 1700, 900, home, ground, 36, 0, 1920);
        job.start(body);
        List<String> steps = new ArrayList<>();
        List<String> moods = new ArrayList<>();
        for (int t = 0; t < 4000 && !job.over(); t++) {
            ui.time = t;
            job.tick(33, body, pet);
            body.tick(33, 1200, 700, home, ground, 36, 0, 1920);
            pet.follow(body.state());
            pet.tick(33, 0, 0, false, false);
            if (steps.isEmpty() || !steps.get(steps.size() - 1).equals(job.step().name())) steps.add(job.step().name());
            if (moods.isEmpty() || !moods.get(moods.size() - 1).equals(pet.mood().name())) moods.add(pet.mood().name());
        }
        // (the pretend screen answers each question straight away, so the asking steps pass within one moment)
        check("he rides, hops onto the window, works, cleans, peeks, climbs, asks, and goes home",
                String.join(">", steps), "BOARD>HOP_IN>WORK>CLEANING>BETWEEN>PEEK>CLIMB>CLEANING>BETWEEN>PEEK>CLIMB>BETWEEN>PEEK>CLIMB>BETWEEN>DONE>LEAVING>OVER");
        check("his laptop comes out, and his hands peek over the top", moods.contains("WORK") && moods.contains("PEEK"), true);
        check("he only cleaned what you said yes to", ui.recycled, List.of(9, 1));
        check("he asked 4 questions: the junk, then each maybe-junk until you said stop", ui.said.stream().filter(x -> x.contains("?")).count(), 4);
        check("he says what he did", ui.said.get(ui.said.size() - 1).startsWith("All done! I moved 10 things"), true);
        check("and walks home", body.state(), Body.State.HOME);

        // Shake him off on the way: the job's off, nothing cleaned
        FakeUi shaken = new FakeUi(downloads);
        Body body2 = new Body();
        CleanJob job2 = new CleanJob(shaken);
        body2.tick(33, 1700, 900, home, ground, 36, 0, 1920);
        job2.start(body2);
        shaken.noExplorer = true;
        for (int t = 0; t < 30; t++) {
            job2.tick(33, body2, pet);
            body2.tick(33, 1200, 700, home, ground, 36, 0, 1920);
        }
        for (int t = 0; t < 16; t++) {
            job2.tick(33, body2, pet);
            body2.tick(33, 1200 + (t % 2 == 0 ? 150 : -150), 700, home, ground, 36, 0, 1920);
        }
        for (int t = 0; t < 3000 && !job2.over(); t++) {
            job2.tick(33, body2, pet);
            body2.tick(33, 1200, 700, home, ground, 36, 0, 1920);
        }
        check("shaken off on the way, he gives up the job and nothing's cleaned", job2.over() + " " + shaken.recycled, "true []");

        // A folder he won't clean: he says why and keeps waiting for another
        FakeUi system = new FakeUi(pc.getRoot());
        Body body3 = new Body();
        CleanJob job3 = new CleanJob(system);
        body3.tick(33, 1700, 900, home, ground, 36, 0, 1920);
        job3.start(body3);
        for (int t = 0; t < 60; t++) {
            job3.tick(33, body3, pet);
            body3.tick(33, 1200, 700, home, ground, 36, 0, 1920);
        }
        check("a whole drive: he says no and stays on your cursor", job3.step() + " " + system.said.get(system.said.size() - 1), "BOARD That's a whole drive. Pick a folder inside it.");
    }

    /** A pretend screen: one File Explorer window showing the folder, and answers ready for his questions. */
    static final class FakeUi implements CleanJob.Ui {
        final Path folder;
        final ArrayDeque<Integer> answers = new ArrayDeque<>();
        final List<String> said = new ArrayList<>();
        final List<Integer> recycled = new ArrayList<>();
        boolean noExplorer;
        long time;

        FakeUi(Path folder) {
            this.folder = folder;
        }

        public Foreground.Front front() {
            if (noExplorer) return new Foreground.Front("chrome.exe", "Chrome_WidgetWin_1", "", 7, new int[] {0, 0, 800, 600});
            return new Foreground.Front("explorer.exe", "CabinetWClass", "Downloads", 42, new int[] {300, 200, 1300, 900});
        }

        public Path folderOf(long handle) {
            return handle == 42 ? folder : null;
        }

        public double[] spot(Foreground.Front w) {
            return new double[] {1020, 201};
        }

        public void say(String text) {
            said.add(text);
        }

        public void ask(String text, String[] buttons, IntConsumer answer) {
            said.add(text);
            Integer a = answers.poll();
            answer.accept(a == null ? buttons.length - 1 : a);
        }

        public void hideBubble() {
        }

        public void background(Runnable work, Runnable then) {
            work.run();
            then.run();
        }

        public int recycle(List<Cleaner.Item> items) {
            recycled.add(items.size()); // pretend: nothing really moves
            return items.size();
        }
    }

    static void check(String what, Object got, Object want) {
        ClawdtopTest.check(what, got, want);
    }
}
