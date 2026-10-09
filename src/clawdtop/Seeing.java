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
    private volatile double brightness = -1, change;
    private Thread looker;

    synchronized void start(Rectangle screen) {
        if (on) return;
        on = true;
        looker = new Thread(() -> {
            try {
                Robot robot = new Robot();
                double last = -1;
                while (on) {
                    BufferedImage shot = robot.createScreenCapture(screen);
                    long sum = 0;
                    int count = 0;
                    for (int y = 0; y < shot.getHeight(); y += 24) {
                        for (int x = 0; x < shot.getWidth(); x += 24) {
                            int rgb = shot.getRGB(x, y);
                            sum += ((rgb >> 16) & 0xFF) * 3 + ((rgb >> 8) & 0xFF) * 6 + (rgb & 0xFF); // (how bright it looks to us)
                            count++;
                        }
                    }
                    double now = count == 0 ? 0 : sum / (count * 10.0 * 255);
                    change = last < 0 ? 0 : Math.abs(now - last);
                    brightness = now;
                    last = now;
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
        brightness = -1;
        change = 0;
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

    /** What's playing in the window in front: "video", "music", or null (nothing he can tell). */
    static String mediaIn(String app, String title) {
        String a = app == null ? "" : app.toLowerCase(java.util.Locale.ROOT), t = title == null ? "" : title.toLowerCase(java.util.Locale.ROOT);
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
