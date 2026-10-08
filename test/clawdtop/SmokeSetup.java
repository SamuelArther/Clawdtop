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

/**
 * The screen test for meeting him: a brand new Clawd (setup, his box, the launch), then riding the real cursor and
 * being shaken off. Screenshots into the folder given. Takes over the mouse: never on someone's computer uninvited.
 */
public final class SmokeSetup {
    static Robot robot;
    static Rectangle all;
    static File out;
    static int n;

    public static void main(String[] args) throws Exception {
        out = new File(args[0]);
        out.mkdirs();
        Path home = Files.createTempDirectory("clawdtop-smoke");
        System.setProperty("clawdtop.home", home.toString());
        Clawdtop[] clawd = new Clawdtop[1];
        SwingUtilities.invokeAndWait(() -> {
            clawd[0] = new Clawdtop();
            clawd[0].start();
        });
        robot = new Robot();
        all = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        String[][] steps = {
                {"welcome type Sam", "name"}, {"welcome click Next", "where"}, {"welcome click Above the clock", "personality"},
                {"welcome click Bouncy", "birthday"}, {"welcome click Skip", "home"}, {"welcome click Next", "answers"},
                {"welcome click Normal (recommended)", "beeps"}, {"welcome click Shh, no beeps", "startup"},
                {"welcome click Not now", "all set"}, {"welcome click OK!", "the box"}};
        shot("welcome");
        for (String[] step : steps) {
            Thread.sleep(700);
            SwingUtilities.invokeAndWait(() -> clawd[0].smoke(step[0]));
            Thread.sleep(500);
            shot(step[1]);
        }
        double[] spot = new double[3];
        SwingUtilities.invokeAndWait(() -> System.arraycopy(clawd[0].smokeHome(), 0, spot, 0, 3));
        double unit = spot[2];
        // click the box
        robot.mouseMove(all.width / 3, all.height / 3); // away from the clock first (its tooltip covers the box)
        Thread.sleep(1200);
        robot.mouseMove((int) spot[0], (int) (spot[1] - 2 * unit));
        Thread.sleep(400);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseMove(all.width / 3, all.height / 3);
        for (int i = 0; i < 16; i++) {
            Thread.sleep(400);
            shot("launch");
        }
        waitIdle(clawd[0], 20_000);
        shot("home again");
        // ride the cursor: wait on the taskbar's top edge beside him
        robot.mouseMove((int) (spot[0] + 8 * unit), (int) spot[1]);
        Thread.sleep(1500);
        shot("hopped on");
        for (int i = 0; i < 30; i++) {
            robot.mouseMove((int) (spot[0] + 8 * unit - i * 15), (int) (spot[1] - i * 10));
            Thread.sleep(40);
        }
        shot("riding");
        Thread.sleep(800);
        shot("riding still");
        // shake!
        int cx = (int) (spot[0] - 300), cy = (int) (spot[1] - 300);
        for (int i = 0; i < 16; i++) {
            robot.mouseMove(cx + (i % 2 == 0 ? -120 : 120), cy);
            Thread.sleep(30);
        }
        robot.mouseMove(all.width / 3, all.height / 4);
        for (int i = 0; i < 14; i++) {
            Thread.sleep(300);
            shot("shaken off");
        }
        waitIdle(clawd[0], 30_000);
        shot("walked home");
        System.exit(0);
    }

    static void waitIdle(Clawdtop c, long ms) throws Exception {
        long until = System.currentTimeMillis() + ms;
        boolean[] idle = {false};
        while (System.currentTimeMillis() < until) {
            SwingUtilities.invokeAndWait(() -> idle[0] = c.smokeIdle());
            if (idle[0]) return;
            Thread.sleep(300);
        }
    }

    static void shot(String name) throws Exception {
        ImageIO.write(robot.createScreenCapture(all), "png", new File(out, String.format("%03d %s.png", n++, name)));
    }
}
