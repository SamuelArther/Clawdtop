package clawdtop;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;

/**
 * Clawd's eyes, once you say he may, while you watch something: how bright the screen is, twice a second, so he can
 * react to a big flash or a sudden change. Just the brightness of a quick, tiny look: nothing's kept, saved or sent.
 * (On a Mac, macOS asks you itself the first time.)
 */
final class Seeing {
    private volatile boolean on;
    private volatile Rectangle region; // what he looks at: the video's window (or the whole screen)

    /** Looks at just this part of the screen from now on (the video's window). */
    void lookAt(Rectangle r) {
        if (r == null || r.width <= 40 || r.height <= 40 || r.equals(region)) return;
        region = r;
        fresh = true; // (somewhere new: not compared with the old spot, or a moved window would look like a flash)
    }

    private volatile boolean fresh;
    private volatile double brightness = -1, change, spread;
    /** The picture in a grid of this many squares a side: a real flash changes nearly all of them, a menu opening only a few. */
    static final int GRID = 4;
    /** How much one square has to change to count, and how many of them (of all of them) for a flash. */
    static final double SQUARE_CHANGE = 0.2, FLASH_SPREAD = 0.7;
    private Thread looker;

    synchronized void start(Rectangle screen) {
        if (region == null) region = screen;
        if (on) return;
        on = true;
        looker = new Thread(() -> {
            try {
                Robot robot = new Robot();
                double last = -1;
                double[] lastSquares = null;
                while (on) {
                    if (fresh) {
                        fresh = false;
                        last = -1;
                        lastSquares = null;
                    }
                    BufferedImage shot = robot.createScreenCapture(region != null ? region : screen);
                    double[] squares = squares(shot);
                    double now = 0;
                    for (double s : squares) now += s / squares.length;
                    change = last < 0 ? 0 : Math.abs(now - last);
                    spread = lastSquares == null ? 0 : spread(lastSquares, squares);
                    brightness = now;
                    last = now;
                    lastSquares = squares;
                    Thread.sleep(500);
                }
            } catch (Exception cantSee) {
                on = false;
            }
        }, "Clawdtop eyes");
        looker.setDaemon(true);
        looker.start();
    }

    synchronized void stop() {
        on = false;
        region = null;
        brightness = -1;
        change = 0;
        spread = 0;
    }

    /** How bright each square of the picture is (0 dark to 1 bright), GRID by GRID. */
    static double[] squares(BufferedImage shot) {
        double[] sum = new double[GRID * GRID];
        int[] count = new int[GRID * GRID];
        int step = Math.max(4, Math.min(shot.getWidth(), shot.getHeight()) / 40);
        for (int y = 0; y < shot.getHeight(); y += step) {
            for (int x = 0; x < shot.getWidth(); x += step) {
                int rgb = shot.getRGB(x, y);
                int square = Math.min(GRID - 1, y * GRID / shot.getHeight()) * GRID + Math.min(GRID - 1, x * GRID / shot.getWidth());
                sum[square] += (((rgb >> 16) & 0xFF) * 3 + ((rgb >> 8) & 0xFF) * 6 + (rgb & 0xFF)) / (10.0 * 255); // (how bright it looks to us)
                count[square]++;
            }
        }
        for (int i = 0; i < sum.length; i++) sum[i] = count[i] == 0 ? 0 : sum[i] / count[i];
        return sum;
    }

    /** How much of the picture changed a lot (0 none of it, 1 all of it). */
    static double spread(double[] before, double[] after) {
        int changed = 0;
        for (int i = 0; i < before.length; i++) if (Math.abs(after[i] - before[i]) > SQUARE_CHANGE) changed++;
        return changed / (double) before.length;
    }

    /** A big flash just now: the whole picture suddenly much brighter or darker (not just a menu or a pop-up opening on it). */
    boolean flash() {
        return on && change > 0.3 && spread >= FLASH_SPREAD;
    }

    boolean looking() {
        return on;
    }

    /** How bright the screen is (0 dark to 1 bright), or -1 if he isn't looking. */
    double brightness() {
        return brightness;
    }

    /** How much it just changed (0 none, 1 from black to white), in the last half second. */
    double change() {
        return change;
    }

    /** Web browsers (Windows' program names, and a Mac's or Linux's app names): only their window titles say what's playing. */
    private static final java.util.Set<String> BROWSERS = java.util.Set.of("chrome.exe", "msedge.exe", "firefox.exe", "brave.exe", "opera.exe", "vivaldi.exe",
            "arc.exe", "chromium.exe", "google chrome", "safari", "firefox", "microsoft edge", "brave browser", "arc", "opera", "vivaldi", "chromium", "chrome");

    /** What's playing in the window in front: "video", "music", or null (nothing he can tell). */
    static String mediaIn(String app, String title) {
        String a = app == null ? "" : app.toLowerCase(java.util.Locale.ROOT);
        // (a title only counts in a browser: a Word file called "Netflix essay" or a folder called "YouTube" isn't a show)
        boolean browser = BROWSERS.contains(a) || (Boolean.getBoolean("clawdtop.smokeBrowser") && a.startsWith("java")); // (the screen test's pretend video window)
        String t = title == null || !browser ? "" : title.toLowerCase(java.util.Locale.ROOT);
        if (a.equals("spotify.exe") || a.equals("itunes.exe") || a.equals("applemusic.exe") || a.equals("music.ui.exe") || a.equals("foobar2000.exe")
                || a.equals("winamp.exe") || a.equals("musicbee.exe") || a.equals("spotify") || a.equals("music")
                || t.contains("youtube music") || t.contains("soundcloud") || t.contains("deezer") || t.contains("pandora") || t.contains("apple music")) {
            return "music";
        }
        if (a.equals("vlc.exe") || a.equals("video.ui.exe") || a.equals("microsoft.media.player.exe") || a.equals("mpc-hc64.exe") || a.equals("potplayermini64.exe")
                || a.equals("vlc") || a.equals("quicktime player") || a.equals("tv")
                || t.matches(".*\\b(youtube|netflix|twitch|disney\\+|prime video|hulu|crunchyroll|vimeo|plex|peacock|paramount\\+)\\b.*")) {
            return "video";
        }
        return null;
    }
}
