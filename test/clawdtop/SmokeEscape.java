package clawdtop;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** The screen test for getting out of setup: a brand-new Clawd, Escape on the first question, then Escape on "All set". */
public final class SmokeEscape {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Path home = Files.createTempDirectory("clawdtop-smoke");
        System.setProperty("clawdtop.home", home.toString());
        Files.writeString(home.resolve("settings.properties"), "beeps=false\n");
        Clawdtop[] clawd = new Clawdtop[1];
        SwingUtilities.invokeAndWait(() -> {
            clawd[0] = new Clawdtop();
            clawd[0].start();
        });
        Robot robot = new Robot();
        Rectangle all = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        Thread.sleep(4000);
        ImageIO.write(robot.createScreenCapture(all), "png", new File(out, "1 first question.png"));
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        Thread.sleep(2000);
        ImageIO.write(robot.createScreenCapture(all), "png", new File(out, "2 after escape.png"));
        robot.keyPress(KeyEvent.VK_ESCAPE);
        robot.keyRelease(KeyEvent.VK_ESCAPE);
        Thread.sleep(3000);
        ImageIO.write(robot.createScreenCapture(all), "png", new File(out, "3 after second escape.png"));
        System.out.println("met: " + Settings.load().met());
        System.exit(0);
    }
}
