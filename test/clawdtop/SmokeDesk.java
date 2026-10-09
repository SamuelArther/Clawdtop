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
 * The screen test for the desktop (Windows): a pretend song, zip and picture are each put on the desktop and moved
 * next to him (he should jump up and grab each one: the song goes in his songs, the zip gets torn up and its folder
 * comes out in front of him, the picture gets filed away and a smaller copy comes out), then three pretend files get
 * tidied into Neat and put back. Only its own "clawdtest-" files are ever touched (he's told to leave everything else
 * alone), and they're all removed at the end.
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
        Path pretend = Files.createTempDirectory("clawdtop-pretend");
        // a pretend song, zip and picture
        javax.sound.midi.Sequence seq = new javax.sound.midi.Sequence(javax.sound.midi.Sequence.PPQ, 4);
        javax.sound.midi.Track track = seq.createTrack();
        int[] tune = {60, 64, 67, 72};
        for (int i = 0; i < tune.length; i++) track.add(new javax.sound.midi.MidiEvent(new javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_ON, 0, tune[i], 90), i * 4L));
        javax.sound.midi.MidiSystem.write(seq, 0, pretend.resolve("song.mid").toFile());
        try (var zout = new java.util.zip.ZipOutputStream(Files.newOutputStream(pretend.resolve("stuff.zip")))) {
            for (String n : new String[] {"readme.txt", "notes.txt"}) {
                zout.putNextEntry(new java.util.zip.ZipEntry(n));
                zout.write("a pretend file for Clawdtop's screen test".getBytes(StandardCharsets.UTF_8));
                zout.closeEntry();
            }
        }
        java.awt.image.BufferedImage photo = new java.awt.image.BufferedImage(2400, 1600, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.util.Random grain = new java.util.Random(1);
        for (int y = 0; y < 1600; y += 2) for (int x = 0; x < 2400; x += 2) photo.setRGB(x, y, grain.nextInt(0xFFFFFF));
        ImageIO.write(photo, "png", pretend.resolve("photo.png").toFile());
        try {
            SwingUtilities.invokeAndWait(() -> {
                clawd[0] = new Clawdtop();
                clawd[0].start();
            });
            robot.mouseMove(all.width / 3, all.height / 3);
            Thread.sleep(8000); // (his first look at the desktop: what's there already stays)
            double homeX = field(clawd[0], "homeX"), groundY = field(clawd[0], "groundY");
            double scale = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform().getScaleX();
            String[][] grabs = {{"song.mid", "-170", "120"}, {"stuff.zip", "150", "200"}, {"photo.png", "-100", "260"}};
            for (String[] g : grabs) {
                Path file = desktop.resolve(PREFIX + g[0]);
                Files.copy(pretend.resolve(g[0]), file);
                mine.add(file);
                Thread.sleep(3000);
                moveIcon(PREFIX + g[0].substring(0, g[0].indexOf('.')), (int) ((homeX + Integer.parseInt(g[1])) * scale), (int) ((groundY - Integer.parseInt(g[2])) * scale));
                SwingUtilities.invokeAndWait(() -> clawd[0].smoke("dragged")); // (moved by the test, not a real drag he'd notice)
                String name = g[0].substring(0, g[0].indexOf('.'));
                for (int i = 0; i < 30; i++) {
                    Thread.sleep(400);
                    ImageIO.write(robot.createScreenCapture(all), "png", new File(out, String.format("%s %02d.png", name, i)));
                }
                Thread.sleep(3000);
                ImageIO.write(robot.createScreenCapture(all), "png", new File(out, name + " done.png"));
                SwingUtilities.invokeAndWait(() -> clawd[0].smoke("close bubble"));
            }
            Path folder = desktop.resolve(PREFIX + "stuff"), small = desktop.resolve(PREFIX + "photo (small).jpg");
            mine.add(folder.resolve("readme.txt"));
            mine.add(folder.resolve("notes.txt"));
            mine.add(folder);
            mine.add(small);
            System.out.println("grabbed the song: " + Files.exists(home.resolve("songs").resolve(PREFIX + "song.mid")) + ", left on desktop: " + Files.exists(mine.get(0)));
            System.out.println("tore up the zip: folder out " + Files.isRegularFile(folder.resolve("readme.txt")) + ", zip left on desktop: " + Files.exists(mine.get(1)));
            System.out.println("filed the picture: original kept " + Files.exists(home.resolve("Pictures you gave me").resolve(PREFIX + "photo.png"))
                    + ", smaller copy out " + Files.exists(small) + ", original left on desktop: " + Files.exists(mine.get(2)));
            Desktop.Layout after = Desktop.look();
            if (after != null) {
                for (Desktop.Icon icon : after.icons()) {
                    if (!icon.name().startsWith(PREFIX)) continue;
                    double[] at = Desktop.spot(icon, all, scale);
                    System.out.println("  " + icon.name() + " is at " + Math.round(at[0] - homeX) + " across, " + Math.round(groundY - at[1]) + " up from him");
                }
            }
            // tidying three files, then putting them back
            List<Path> tidied = new ArrayList<>();
            for (String name : new String[] {"report.pdf", "notes.txt", "sheet.csv"}) {
                Path f = desktop.resolve(PREFIX + name);
                Files.writeString(f, "a pretend file for Clawdtop's screen test", StandardCharsets.UTF_8);
                mine.add(f);
                tidied.add(f);
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
            System.out.println("tidied: " + Files.exists(neat.resolve("Documents").resolve(PREFIX + "report.pdf")) + " "
                    + Files.exists(neat.resolve("Documents").resolve(PREFIX + "notes.txt")) + " " + Files.exists(neat.resolve("Spreadsheets").resolve(PREFIX + "sheet.csv")));
            SwingUtilities.invokeAndWait(() -> clawd[0].smoke("put back"));
            Thread.sleep(2000);
            ImageIO.write(robot.createScreenCapture(all), "png", new File(out, "t-back.png"));
            System.out.println("put back: " + tidied.stream().allMatch(Files::exists) + ", Neat gone: " + !Files.exists(neat));
        } finally { // only this test's own files (wherever he put them), and Neat if it's this test's and empty
            for (Path f : mine) {
                try {
                    Files.deleteIfExists(f);
                } catch (java.io.IOException notEmpty) {
                    // (a folder that isn't empty: not this test's)
                }
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
