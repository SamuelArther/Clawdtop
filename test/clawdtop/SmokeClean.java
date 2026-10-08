package clawdtop;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The screen test for cleaning a folder, for real: a pretend Downloads folder (made here, full of junk), File Explorer
 * opened on it, Clawd riding the cursor over, hopping on, asking, and moving the junk you said yes to into the
 * Recycle Bin. Only ever touches the folder it made.
 */
public final class SmokeClean {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Path home = Files.createTempDirectory("clawdtop-smoke");
        System.setProperty("clawdtop.home", home.toString());
        Files.writeString(home.resolve("settings.properties"), "met=true\nname=Tester\nbeeps=false\ntips=false\nmetDate=2026-10-08\n");
        Path downloads = Files.createDirectories(Files.createTempDirectory("clawdtop-cleaning").resolve("Downloads"));
        Files.writeString(downloads.resolve("Thumbs.db"), "x");
        Files.writeString(downloads.resolve("report.tmp"), "x");
        Files.writeString(downloads.resolve("movie.mp4.crdownload"), "x");
        Files.createDirectories(downloads.resolve("Old Stuff"));
        Path installer = downloads.resolve("setup_game.exe");
        Files.write(installer, new byte[2048]);
        Files.setLastModifiedTime(installer, FileTime.from(Instant.now().minus(90, ChronoUnit.DAYS)));
        Files.writeString(downloads.resolve("homework.docx"), "keep me");
        System.out.println("folder " + downloads);

        Clawdtop[] clawd = new Clawdtop[1];
        SwingUtilities.invokeAndWait(() -> {
            clawd[0] = new Clawdtop();
            clawd[0].start();
        });
        Robot robot = new Robot();
        Rectangle all = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        robot.mouseMove(all.width / 2, all.height / 2);
        Thread.sleep(3000);
        int[] n = {0};
        Runnable shot = () -> {
            try {
                ImageIO.write(robot.createScreenCapture(all), "png", new File(out, String.format("%03d.png", n[0]++)));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        SwingUtilities.invokeAndWait(() -> clawd[0].smoke("clean"));
        for (int i = 0; i < 6; i++) {
            robot.mouseMove(all.width / 2 - i * 20, all.height / 2 - i * 10);
            Thread.sleep(300);
            shot.run();
        }
        new ProcessBuilder("explorer.exe", downloads.toString()).start();
        robot.mouseMove(all.width / 2, all.height / 3);
        String[] answers = {"0", "1", "1", "1", "1"}; // yes to the plain junk, then no to each maybe
        int answered = 0;
        long until = System.currentTimeMillis() + 90_000;
        while (System.currentTimeMillis() < until) {
            Thread.sleep(500);
            shot.run();
            String[] q = {null};
            boolean[] idle = {false};
            SwingUtilities.invokeAndWait(() -> {
                q[0] = clawd[0].smokeQuestion();
                idle[0] = clawd[0].smokeIdle();
            });
            if (q[0] != null && answered < answers.length) {
                System.out.println("asked: " + q[0].replace("\n", " / "));
                Thread.sleep(1200);
                shot.run();
                String a = answers[answered++];
                SwingUtilities.invokeAndWait(() -> clawd[0].smoke("answer " + a));
            }
            if (idle[0] && answered > 0) break;
        }
        try (var left = Files.list(downloads)) {
            System.out.println("left in the folder: " + left.map(p -> p.getFileName().toString()).sorted().toList());
        }
        // close the Explorer window we opened (only that one)
        new ProcessBuilder("powershell", "-NoProfile", "-Command",
                "(New-Object -ComObject Shell.Application).Windows() | Where-Object { $_.Document.Folder.Self.Path -eq '" + downloads + "' } | ForEach-Object { $_.Quit() }")
                .start().waitFor();
        System.exit(0);
    }
}
