package clawdtop;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The screen test for the desktop (Windows): a pretend song file is put on the desktop and moved next to him (he
 * should go and get it), then three pretend files get tidied into Neat and put back. Only its own "clawdtest-" files
 * are ever touched (he's told to leave everything else alone), and they're all removed at the end.
 */
public final class SmokeDesk {
    static final String PREFIX = "clawdtest-";

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        System.setProperty("clawdtop.tidyOnly", PREFIX);
        Path home = Files.createTempDirectory("clawdtop-smoke");
        System.setProperty("clawdtop.home", home.toString());
        Files.writeString(home.resolve("settings.properties"), "met=true\nname=Tester\nbeeps=false\ntips=false\nmetDate=2026-10-08\n");
        Path desktop = Path.of(System.getProperty("user.home"), "Desktop");
        Desktop.Layout first = Desktop.look();
        if (first != null) desktop = first.folder();
        boolean neatWasThere = Files.exists(desktop.resolve("Neat"));
        List<Path> mine = new ArrayList<>();
        Clawdtop[] clawd = new Clawdtop[1];
        Robot robot = new Robot();
        Rectangle all = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        try {
            SwingUtilities.invokeAndWait(() -> {
                clawd[0] = new Clawdtop();
                clawd[0].start();
            });
            robot.mouseMove(all.width / 3, all.height / 3);
            Thread.sleep(8000); // (his first look at the desktop: what's there already stays)
            // 1. a song put on the desktop, then dragged next to him
            Path song = desktop.resolve(PREFIX + "song.mid");
            Files.copy(Path.of(args[1]), song);
            mine.add(song);
            Thread.sleep(2500);
            double homeX = field(clawd[0], "homeX"), groundY = field(clawd[0], "groundY");
            double scale = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform().getScaleX();
            moveIcon(PREFIX + "song", (int) ((homeX - 170) * scale), (int) ((groundY - 120) * scale));
            for (int i = 0; i < 24; i++) {
                Thread.sleep(500);
                ImageIO.write(robot.createScreenCapture(all), "png", new File(out, String.format("f%02d.png", i)));
            }
            System.out.println("fetched: " + Files.exists(home.resolve("songs").resolve(PREFIX + "song.mid")) + ", left on desktop: " + Files.exists(song));
            // 2. tidying three files, then putting them back
            for (String name : new String[] {"photo.png", "notes.txt", "stuff.zip"}) {
                Path f = desktop.resolve(PREFIX + name);
                Files.writeString(f, "a pretend file for Clawdtop's screen test", StandardCharsets.UTF_8);
                mine.add(f);
            }
            Thread.sleep(2500);
            SwingUtilities.invokeAndWait(() -> clawd[0].smoke("tidy"));
            Thread.sleep(3000);
            ImageIO.write(robot.createScreenCapture(all), "png", new File(out, "t-ask.png"));
            SwingUtilities.invokeAndWait(() -> clawd[0].smoke("yes"));
            for (int i = 0; i < 24; i++) {
                Thread.sleep(500);
                ImageIO.write(robot.createScreenCapture(all), "png", new File(out, String.format("t%02d.png", i)));
            }
            Path neat = desktop.resolve("Neat");
            System.out.println("tidied: " + Files.exists(neat.resolve("Pictures").resolve(PREFIX + "photo.png")) + " "
                    + Files.exists(neat.resolve("Documents").resolve(PREFIX + "notes.txt")) + " " + Files.exists(neat.resolve("Zips").resolve(PREFIX + "stuff.zip")));
            SwingUtilities.invokeAndWait(() -> clawd[0].smoke("put back"));
            Thread.sleep(2000);
            ImageIO.write(robot.createScreenCapture(all), "png", new File(out, "t-back.png"));
            System.out.println("put back: " + mine.subList(1, 4).stream().allMatch(Files::exists) + ", Neat gone: " + !Files.exists(neat));
        } finally { // only this test's own files (wherever he put them), and Neat if it's this test's and empty
            for (Path f : mine) {
                Files.deleteIfExists(f);
                for (String c : Desktop.CATEGORIES) Files.deleteIfExists(desktop.resolve("Neat").resolve(c).resolve(f.getFileName()));
            }
            if (!neatWasThere && Files.isDirectory(desktop.resolve("Neat"))) {
                for (String c : Desktop.CATEGORIES) {
                    try {
                        Files.deleteIfExists(desktop.resolve("Neat").resolve(c));
                    } catch (java.io.IOException notEmpty) {
                        // leave it
                    }
                }
                try {
                    Files.deleteIfExists(desktop.resolve("Neat"));
                } catch (java.io.IOException notEmpty) {
                    // leave it
                }
            }
        }
        System.exit(0);
    }

    static double field(Object o, String name) throws Exception {
        var f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.getDouble(o);
    }

    /** Moves one desktop icon (found by its name) to a spot, the way dragging it would. */
    static void moveIcon(String name, int x, int y) throws Exception {
        Desktop.Layout layout = Desktop.look();
        int index = -1;
        for (int i = 0; i < layout.icons().size(); i++) if (layout.icons().get(i).name().startsWith(name)) index = i;
        if (index < 0) throw new IllegalStateException("no icon " + name);
        String script = String.join("\n",
                "Add-Type -TypeDefinition @'",
                "using System; using System.Runtime.InteropServices;",
                "public static class ClawdMove {",
                "  [DllImport(\"user32.dll\", CharSet = CharSet.Unicode)] static extern IntPtr FindWindow(string c, string n);",
                "  [DllImport(\"user32.dll\", CharSet = CharSet.Unicode)] static extern IntPtr FindWindowEx(IntPtr p, IntPtr a, string c, string n);",
                "  [DllImport(\"user32.dll\")] static extern IntPtr SendMessage(IntPtr w, uint m, IntPtr wp, IntPtr lp);",
                "  public static void Move(int i, int x, int y) {",
                "    IntPtr view = FindWindowEx(FindWindow(\"Progman\", null), IntPtr.Zero, \"SHELLDLL_DefView\", null); IntPtr w = IntPtr.Zero;",
                "    while (view == IntPtr.Zero && (w = FindWindowEx(IntPtr.Zero, w, \"WorkerW\", null)) != IntPtr.Zero) view = FindWindowEx(w, IntPtr.Zero, \"SHELLDLL_DefView\", null);",
                "    IntPtr lv = FindWindowEx(view, IntPtr.Zero, \"SysListView32\", null);",
                "    SendMessage(lv, 0x100F, (IntPtr) i, (IntPtr) ((y << 16) | (x & 0xFFFF)));",
                "  }",
                "}",
                "'@",
                "[ClawdMove]::Move(" + index + ", " + x + ", " + y + ")");
        new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand",
                java.util.Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE))).inheritIO().start().waitFor();
    }
}
