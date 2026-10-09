package clawdtop;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The screen test for watching along: a pretend video window ("... - YouTube") comes to the front, he offers to
 * watch, you say yes, the real permission box comes up and gets an Enter (Yes), he settles in with popcorn, and a
 * big flash on the "video" makes him jump. Screenshots of each step go in the folder given.
 */
public final class SmokeWatch {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Path home = Files.createTempDirectory("clawdtop-smoke");
        System.setProperty("clawdtop.home", home.toString());
        System.setProperty("clawdtop.smokeBrowser", "true"); // (our pretend YouTube window is a Java one: count it as a browser)
        Files.writeString(home.resolve("settings.properties"), "met=true\nname=Tester\nbeeps=false\ntips=false\nmetDate=2026-10-08\n");
        Clawdtop[] clawd = new Clawdtop[1];
        SwingUtilities.invokeAndWait(() -> {
            clawd[0] = new Clawdtop();
            clawd[0].start();
        });
        Robot robot = new Robot();
        Rectangle all = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        Thread.sleep(6000);
        JFrame[] video = new JFrame[1];
        JPanel[] screen = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            video[0] = new JFrame("Funny cats compilation - YouTube - Google Chrome");
            screen[0] = new JPanel();
            screen[0].setBackground(new Color(20, 20, 24));
            video[0].setContentPane(screen[0]);
            video[0].setBounds(all.x + 60, all.y + 60, all.width / 2, all.height / 2);
            video[0].setVisible(true);
            video[0].toFront();
        });
        Thread.sleep(3000);
        shot(robot, all, out, "1 he offers to watch");
        SwingUtilities.invokeAndWait(() -> clawd[0].smoke("yes"));
        Thread.sleep(2500);
        shot(robot, all, out, "2 the permission box");
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
        Thread.sleep(500);
        SwingUtilities.invokeAndWait(() -> video[0].toFront());
        Thread.sleep(4000);
        shot(robot, all, out, "3 watching with popcorn");
        SwingUtilities.invokeAndWait(() -> {
            screen[0].setBackground(Color.WHITE); // a big flash
            screen[0].repaint();
        });
        Thread.sleep(1300);
        shot(robot, all, out, "4 the flash makes him jump");
        Thread.sleep(2500);
        shot(robot, all, out, "5 back to watching");
        SwingUtilities.invokeAndWait(() -> video[0].dispose());
        Thread.sleep(23_000);
        shot(robot, all, out, "6 show's over");
        System.out.println("seeing allowed: " + Settings.load().on("seeing"));
        System.exit(0);
    }

    private static void shot(Robot robot, Rectangle all, File out, String name) throws Exception {
        ImageIO.write(robot.createScreenCapture(all), "png", new File(out, name + ".png"));
    }
}
