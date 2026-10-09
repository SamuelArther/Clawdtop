package clawdtop;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A quick screen test: SmokeQuick folder "action|seconds|name" ... runs each of his smoke actions, waits, and takes a
 * screenshot (cropped round him) named after it.
 */
public final class SmokeQuick {
    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true"); // (as when he starts for real: no Dock icon on a Mac)
        File out = new File(args[0]);
        out.mkdirs();
        Path home = Files.createTempDirectory("clawdtop-smoke");
        System.setProperty("clawdtop.home", home.toString());
        Files.writeString(home.resolve("settings.properties"), "met=true\nname=Tester\nbeeps=false\ntips=false\nmetDate=2026-10-08\n"
                + System.getProperty("smoke.settings", "").replace("|", "\n") + "\n"); // (-Dsmoke.settings=size=Big|... : more settings to start with)
        String songs = System.getProperty("smoke.songs"); // (a songs folder to copy in, for singing and jamming)
        if (songs != null) {
            Path from = Path.of(songs);
            try (var walk = Files.walk(from)) {
                for (Path f : walk.toList()) {
                    Path to = home.resolve("songs").resolve(from.relativize(f).toString());
                    if (Files.isDirectory(f)) Files.createDirectories(to);
                    else Files.copy(f, to);
                }
            }
        }
        Clawdtop[] clawd = new Clawdtop[1];
        SwingUtilities.invokeAndWait(() -> {
            clawd[0] = new Clawdtop();
            clawd[0].start();
        });
        Robot robot = new Robot();
        Rectangle all = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        robot.mouseMove(all.width / 3, all.height / 3);
        Thread.sleep(3000);
        for (int i = 1; i < args.length; i++) {
            String[] step = args[i].split("\\|");
            if (step[0].equals("key enter")) { // (answering a system dialog, like the permission box)
                robot.keyPress(java.awt.event.KeyEvent.VK_ENTER);
                robot.keyRelease(java.awt.event.KeyEvent.VK_ENTER);
            } else if (!step[0].isEmpty()) SwingUtilities.invokeAndWait(() -> clawd[0].smoke(step[0]));
            Thread.sleep((long) (Double.parseDouble(step[1]) * 1000));
            ImageIO.write(robot.createScreenCapture(all), "png", new File(out, String.format("%02d %s.png", i, step[2])));
        }
        System.exit(0);
    }
}
