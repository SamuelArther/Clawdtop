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

/** The screen test for math: he opens Calculator, you type the sum (Robot types it), he says if it's right. */
public final class SmokeMath {
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
        Thread.sleep(2000);
        for (String attempt : new String[] {"12+7", "12*7"}) { // wrong first, then right
            if (attempt.startsWith("12+")) SwingUtilities.invokeAndWait(() -> clawd[0].smoke("math"));
            Thread.sleep(4000);
            ImageIO.write(robot.createScreenCapture(all), "png", new File(out, attempt.replace('*', 'x') + " before.png"));
            for (char c : (attempt + "=").toCharArray()) {
                switch (c) {
                    case '+' -> { robot.keyPress(KeyEvent.VK_ADD); robot.keyRelease(KeyEvent.VK_ADD); }
                    case '*' -> { robot.keyPress(KeyEvent.VK_MULTIPLY); robot.keyRelease(KeyEvent.VK_MULTIPLY); }
                    case '=' -> { robot.keyPress(KeyEvent.VK_ENTER); robot.keyRelease(KeyEvent.VK_ENTER); }
                    default -> { int k = KeyEvent.VK_0 + (c - '0'); robot.keyPress(k); robot.keyRelease(k); }
                }
                Thread.sleep(150);
            }
            Thread.sleep(2500);
            ImageIO.write(robot.createScreenCapture(all), "png", new File(out, attempt.replace('*', 'x') + " after.png"));
            robot.keyPress(KeyEvent.VK_ESCAPE); // clear Calculator for the next go
            robot.keyRelease(KeyEvent.VK_ESCAPE);
        }
        System.exit(0);
    }
}
