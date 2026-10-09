package clawdtop;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** The screen test for clicking him: one click says hi, a double-click opens his menu, a right-click pets him. */
public final class SmokeClicks {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Path home = Files.createTempDirectory("clawdtop-smoke");
        System.setProperty("clawdtop.home", home.toString());
        Files.writeString(home.resolve("settings.properties"), "met=true\nname=Tester\nbeeps=false\ntips=false\nmetDate=2026-10-08\n");
        Clawdtop[] clawd = new Clawdtop[1];
        SwingUtilities.invokeAndWait(() -> {
            clawd[0] = new Clawdtop();
            clawd[0].start();
        });
        Robot robot = new Robot();
        Rectangle all = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        Thread.sleep(8000);
        double[] spot = new double[3];
        SwingUtilities.invokeAndWait(() -> System.arraycopy(clawd[0].smokeHome(), 0, spot, 0, 3));
        int x = (int) spot[0], y = (int) (spot[1] - 5 * spot[2]);
        robot.mouseMove(x, y);
        Thread.sleep(400);
        click(robot, InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(900);
        shot(robot, all, out, "1 one click says hi");
        Thread.sleep(1500);
        click(robot, InputEvent.BUTTON1_DOWN_MASK);
        click(robot, InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(900);
        shot(robot, all, out, "2 double-click opens his menu");
        robot.mouseMove(all.width / 3, all.height / 3); // click away: the menu closes
        click(robot, InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(800);
        shot(robot, all, out, "3 clicked away");
        robot.mouseMove(x, y);
        Thread.sleep(2500);
        click(robot, InputEvent.BUTTON3_DOWN_MASK);
        Thread.sleep(700);
        shot(robot, all, out, "4 right-click pets him");
        robot.mouseMove(all.width / 3, all.height / 3);
        System.exit(0);
    }

    private static void click(Robot robot, int button) { // (as long as a real click: about a tenth of a second)
        robot.mousePress(button);
        robot.delay(90);
        robot.mouseRelease(button);
    }

    private static void shot(Robot robot, Rectangle all, File out, String name) throws Exception {
        ImageIO.write(robot.createScreenCapture(all), "png", new File(out, name + ".png"));
    }
}
