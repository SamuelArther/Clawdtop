package clawdtop;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** The screen test for typing with a real keyboard: the hello's name box, the question box, and your piano. */
public final class SmokeTyping {
    static Robot robot;

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Path home = Files.createTempDirectory("clawdtop-smoke");
        System.setProperty("clawdtop.home", home.toString());
        Files.writeString(home.resolve("settings.properties"), "beeps=false\n"); // not met yet: the hello shows
        Clawdtop[] clawd = new Clawdtop[1];
        SwingUtilities.invokeAndWait(() -> {
            clawd[0] = new Clawdtop();
            clawd[0].start();
        });
        robot = new Robot();
        Rectangle all = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        Thread.sleep(1500);
        clickField(clawd[0]);
        type("SAM");
        Thread.sleep(500);
        ImageIO.write(robot.createScreenCapture(all), "png", new File(out, "1 name typed.png"));
        System.out.println("after typing a name: " + typed(clawd[0]));
        // finish the hello quickly
        for (String step : new String[] {"welcome type Sam", "welcome click Next", "welcome click Above the clock", "welcome click Chill",
                "welcome click Skip", "welcome click Next", "welcome click Normal (recommended)", "welcome click Shh, no beeps",
                "welcome click Not now", "welcome click OK!"}) {
            SwingUtilities.invokeAndWait(() -> clawd[0].smoke(step));
            Thread.sleep(300);
        }
        Thread.sleep(800);
        SwingUtilities.invokeAndWait(() -> clawd[0].smoke("ask"));
        Thread.sleep(800);
        clickField(clawd[0]);
        type("HELLO");
        Thread.sleep(500);
        ImageIO.write(robot.createScreenCapture(all), "png", new File(out, "2 question typed.png"));
        System.out.println("after typing a question: " + typed(clawd[0]));
        SwingUtilities.invokeAndWait(() -> clawd[0].smoke("close ask"));
        SwingUtilities.invokeAndWait(() -> clawd[0].smoke("your piano"));
        Thread.sleep(800);
        java.awt.Point[] keys = {null};
        SwingUtilities.invokeAndWait(() -> keys[0] = clawd[0].smokePianoOnScreen());
        robot.mouseMove(keys[0].x, keys[0].y); // click a key (that also gives it the keyboard)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(300);
        type("ASDF");
        Thread.sleep(300);
        ImageIO.write(robot.createScreenCapture(all), "png", new File(out, "3 piano.png"));
        System.out.println("after playing the piano with the keyboard: " + typed(clawd[0]));
        System.exit(0);
    }

    /** Clicks in the text box that's showing (the hello's, or the question box). */
    static void clickField(Clawdtop c) throws Exception {
        java.awt.Point[] at = {null};
        SwingUtilities.invokeAndWait(() -> at[0] = c.smokeFieldOnScreen());
        if (at[0] == null) return;
        robot.mouseMove(at[0].x, at[0].y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(300);
    }

    static String typed(Clawdtop c) throws Exception {
        String[] t = {null};
        SwingUtilities.invokeAndWait(() -> t[0] = c.smokeTyped());
        return t[0];
    }

    static void type(String letters) throws Exception {
        for (char ch : letters.toCharArray()) {
            int k = KeyEvent.getExtendedKeyCodeForChar(ch);
            robot.keyPress(k);
            robot.keyRelease(k);
            Thread.sleep(120);
        }
    }
}
