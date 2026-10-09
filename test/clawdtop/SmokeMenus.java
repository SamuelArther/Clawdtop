package clawdtop;

import javax.imageio.ImageIO;
import javax.swing.JMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** The screen test for his menus: opens his menu, then each submenu in turn (hovering it, as you would), with a screenshot of each. */
public final class SmokeMenus {
    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true");
        File out = new File(args[0]);
        out.mkdirs();
        Path home = Files.createTempDirectory("clawdtop-smoke");
        System.setProperty("clawdtop.home", home.toString());
        Files.writeString(home.resolve("settings.properties"), "met=true\nname=Tester\nbeeps=false\ntips=false\nmetDate=2026-10-08\ntodos=homework\u001fcall Grandma\n");
        Clawdtop[] clawd = new Clawdtop[1];
        SwingUtilities.invokeAndWait(() -> {
            clawd[0] = new Clawdtop();
            clawd[0].start();
        });
        Robot robot = new Robot();
        Rectangle all = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        Thread.sleep(4000);
        for (String sub : new String[] {"Fun", "Useful", "Settings"}) {
            SwingUtilities.invokeAndWait(() -> clawd[0].smoke("menu"));
            Thread.sleep(800);
            Point at = null;
            for (int i = 0; i < 10 && at == null; i++) {
                Point[] found = {null};
                SwingUtilities.invokeAndWait(() -> found[0] = find(sub));
                at = found[0];
                if (at == null) Thread.sleep(200);
            }
            if (at != null) {
                robot.mouseMove(at.x, at.y);
                Thread.sleep(900);
                ImageIO.write(robot.createScreenCapture(all), "png", new File(out, sub + ".png"));
            } else {
                System.out.println("no " + sub + " menu found");
            }
            SwingUtilities.invokeAndWait(() -> clawd[0].smoke("close menus"));
            Thread.sleep(500);
        }
        System.exit(0);
    }

    /** Where a submenu of the open menu is on the screen (its middle), or null. */
    static Point find(String label) {
        for (MenuElement e : MenuSelectionManager.defaultManager().getSelectedPath()) {
            for (MenuElement child : e.getSubElements()) {
                Component c = child.getComponent();
                if (c instanceof JMenu m && m.getText().startsWith(label) && m.isShowing()) {
                    Point p = m.getLocationOnScreen();
                    return new Point(p.x + m.getWidth() / 2, p.y + m.getHeight() / 2);
                }
            }
        }
        return null;
    }
}
