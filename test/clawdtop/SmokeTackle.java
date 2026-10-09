package clawdtop;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * The screen test for the taskbar tackle: Notepad is started (the way you would, not by him), quick screenshots
 * check it never flashes up, then he charges its taskbar icon and it pops open. Only the Notepad this test opened is
 * closed afterwards.
 */
public final class SmokeTackle {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Path home = Files.createTempDirectory("clawdtop-smoke");
        System.setProperty("clawdtop.home", home.toString());
        Files.writeString(home.resolve("settings.properties"), "met=true\nname=Tester\nbeeps=false\ntips=false\nmetDate=2026-10-08\nopt.tackle=true\n");
        Clawdtop[] clawd = new Clawdtop[1];
        SwingUtilities.invokeAndWait(() -> {
            clawd[0] = new Clawdtop();
            clawd[0].start();
        });
        Robot robot = new Robot();
        Rectangle all = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        Thread.sleep(8000);
        Instant began = Instant.now();
        new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", "notepad.exe").start(); // (started by cmd, like from the Start menu: not his)
        for (int i = 0; i < 12; i++) { // the first second or so, every tenth of a second: it mustn't show
            Thread.sleep(100);
            ImageIO.write(robot.createScreenCapture(all), "png", new File(out, String.format("a%02d.png", i)));
        }
        for (int i = 0; i < 16; i++) { // then him charging, the dive, the pop and the launch
            Thread.sleep(500);
            ImageIO.write(robot.createScreenCapture(all), "png", new File(out, String.format("b%02d.png", i)));
        }
        ProcessHandle.allProcesses() // close only the Notepad this test opened
                .filter(p -> p.info().command().map(c -> c.toLowerCase().endsWith("notepad.exe")).orElse(false))
                .filter(p -> p.info().startInstant().map(s -> s.isAfter(began)).orElse(false))
                .forEach(ProcessHandle::destroy);
        System.exit(0);
    }
}
