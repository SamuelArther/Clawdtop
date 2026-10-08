package clawdtop;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The screen test: starts the real Clawdtop on a real Windows desktop (GitHub Actions runs it), has him do his big
 * things one after another, and takes a screenshot every half second, into the folder given. Fails if anything
 * throws. Never run this on someone's own computer: it takes over the screen for a couple of minutes.
 */
public final class Smoke {
    private static final List<String> problems = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("the screen test needs a screen");
        File out = new File(args[0]);
        out.mkdirs();
        Path home = Files.createTempDirectory("clawdtop-smoke");
        System.setProperty("clawdtop.home", home.toString());
        System.setProperty("clawdtop.codingMs", "4000");
        Files.writeString(home.resolve("settings.properties"), "met=true\nname=Tester\nsounds=false\ntips=false\nmetDate=2026-10-01\n");
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> problem(t.getName(), e));

        Clawdtop[] clawd = new Clawdtop[1];
        SwingUtilities.invokeAndWait(() -> {
            clawd[0] = new Clawdtop();
            clawd[0].start();
        });
        Robot robot = new Robot();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Rectangle all = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        System.out.println("screen " + all + ", above the taskbar " + screen);
        robot.mouseMove(all.width / 3, all.height / 3);

        // {seconds to wait first, what to do, name}
        Object[][] plan = {
                {2, null, "idle"},
                {0, "menu", "menu"},
                {2, "close menus", "menu closed"},
                {0, "settings menu", "settings menu"},
                {2, "close menus", "closed"},
                {0, "ask", "ask box"},
                {1, "type", "typed a question"},
                {2, "close ask", "ask closed"},
                {1, "tip", "tip bubble"},
                {3, "checkup", "checkup"},
                {4, "pet", "petted"},
                {3, "make disco", "coding disco"},
                {-1, "make carpet", "coding carpet"},
                {14, null, "carpet ride"},
                {0, "click carpet", "carpet clicked away"},
                {-1, "make duck", "coding duck"},
                {14, "stop ducks", "ducks stopped"},
                {-1, "make rocket", "coding rocket"},
                {-1, "mood asleep", "asleep"},
                {3, null, "done"}};
        int shot = 0;
        for (Object[] step : plan) {
            int wait = (Integer) step[0]; // seconds, or -1: until he's back to hanging out at home (at most a minute)
            long until = System.currentTimeMillis() + (wait < 0 ? 60_000 : wait * 1000L);
            while (System.currentTimeMillis() < until) {
                if (wait < 0) {
                    boolean[] idle = {false};
                    SwingUtilities.invokeAndWait(() -> idle[0] = clawd[0].smokeIdle());
                    if (idle[0]) break;
                }
                save(robot.createScreenCapture(all), new File(out, String.format("%04d.png", shot++)));
                Thread.sleep(450);
            }
            String action = (String) step[1];
            if (action != null) {
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        clawd[0].smoke(action);
                    } catch (Throwable e) {
                        problem(action, e);
                    }
                });
            }
            Thread.sleep(300);
            save(robot.createScreenCapture(all), new File(out, String.format("%04d %s.png", shot++, step[2])));
            System.out.println("did: " + step[2]);
        }
        Files.writeString(out.toPath().resolve("problems.txt"), String.join("\n\n", problems));
        System.out.println(problems.isEmpty() ? "SMOKE OK" : "SMOKE PROBLEMS:\n" + String.join("\n\n", problems));
        System.exit(problems.isEmpty() ? 0 : 1);
    }

    private static synchronized void problem(String where, Throwable e) {
        StringWriter s = new StringWriter();
        e.printStackTrace(new PrintWriter(s));
        problems.add(where + ": " + s);
        System.err.println(where + ": " + s);
    }

    private static void save(BufferedImage image, File file) {
        try {
            ImageIO.write(image, "png", file);
        } catch (Exception e) {
            problem("screenshot", e);
        }
    }
}
