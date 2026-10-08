package clawdtop;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** The screen test for the cute stuff with the real cursor: resting on him (shy), and swiping across his face (boop). */
public final class SmokeCute {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Path home = Files.createTempDirectory("clawdtop-smoke");
        System.setProperty("clawdtop.home", home.toString());
        Files.writeString(home.resolve("settings.properties"), "met=true\nname=Tester\nsounds=false\ntips=false\nmetDate=2026-10-08\n");
        Clawdtop[] clawd = new Clawdtop[1];
        SwingUtilities.invokeAndWait(() -> {
            clawd[0] = new Clawdtop();
            clawd[0].start();
        });
        Robot robot = new Robot();
        Rectangle all = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        Thread.sleep(4000);
        double[] spot = new double[3];
        SwingUtilities.invokeAndWait(() -> System.arraycopy(clawd[0].smokeHome(), 0, spot, 0, 3));
        double unit = spot[2];
        int eyeX = (int) spot[0], eyeY = (int) (spot[1] - 7 * unit);
        robot.mouseMove(eyeX, eyeY);
        Thread.sleep(2600);
        ImageIO.write(robot.createScreenCapture(all), "png", new File(out, "1 shy.png"));
        robot.mouseMove(all.width / 3, all.height / 3);
        Thread.sleep(2500);
        robot.mouseMove((int) (eyeX - 6 * 3.5 * unit), eyeY - (int) (12 * unit)); // start off to one side, above him
        Thread.sleep(2500);
        robot.mouseMove((int) (eyeX - 6 * 3.5 * unit), eyeY);
        Thread.sleep(1500);
        for (int i = -6; i <= 6; i++) { // a quick swipe across his face
            robot.mouseMove((int) (eyeX + i * 3.5 * unit), eyeY);
            Thread.sleep(16);
        }
        Thread.sleep(150);
        ImageIO.write(robot.createScreenCapture(all), "png", new File(out, "2 boop.png"));
        robot.mouseMove(all.width / 3, all.height / 3);
        System.exit(0);
    }
}
