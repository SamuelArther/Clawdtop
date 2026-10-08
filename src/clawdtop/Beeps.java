package clawdtop;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Clawd's voice: little quiet beeps, made on the spot (no sound files). */
public final class Beeps {
    private static final float RATE = 44100;
    private static final double VOLUME = 0.12; // quiet: he's a desk buddy, not an alarm
    private final ExecutorService player = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Clawdtop beeps");
        t.setDaemon(true);
        return t;
    });

    /** Plays a beep in the background. Does nothing if the computer has no sound. */
    public void play(Pet.Beep beep) {
        player.execute(() -> {
            byte[] sound = make(beep);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(new AudioFormat(RATE, 16, 1, true, false))) {
                line.open();
                line.start();
                line.write(sound, 0, sound.length);
                line.drain();
            } catch (Exception noSound) {
                // no speakers, or they're busy: he just stays quiet
            }
        });
    }

    /** The sound for a beep, as 16-bit mono samples. Each is a few tiny notes. */
    static byte[] make(Pet.Beep beep) {
        double[][] notes = switch (beep) {  // {frequency, milliseconds}, 0 Hz is a pause
            case HELLO -> new double[][] {{1320, 60}, {0, 30}, {1760, 70}};
            case HAPPY -> new double[][] {{1480, 50}, {0, 20}, {1760, 50}, {0, 20}, {2220, 80}};
            case CLICKED -> new double[][] {{1980, 45}, {0, 25}, {1980, 45}};
            case YAWN -> new double[][] {{990, 120}, {880, 120}, {740, 200}};
            case WAKE -> new double[][] {{1170, 60}, {0, 30}, {1480, 90}};
            case TIP -> new double[][] {{1760, 40}, {0, 25}, {1320, 50}, {0, 25}, {1760, 60}};
        };
        int total = 0;
        for (double[] n : notes) total += (int) (RATE * n[1] / 1000);
        byte[] out = new byte[total * 2 + (int) RATE / 50 * 2]; // a little silence after, so the end isn't cut
        int at = 0;
        double phase = 0;
        for (double[] n : notes) {
            int count = (int) (RATE * n[1] / 1000);
            for (int i = 0; i < count; i++, at++) {
                double sample = 0;
                if (n[0] > 0) {
                    phase += n[0] / RATE;
                    // a soft square-ish wave (a sine with a bit of its third), with quick fades so it doesn't click
                    double wave = Math.sin(2 * Math.PI * phase) + Math.sin(6 * Math.PI * phase) / 4;
                    double fade = Math.min(1, Math.min(i, count - i) / (RATE * 0.006));
                    sample = wave * fade * VOLUME;
                }
                int v = (int) Math.max(-32768, Math.min(32767, sample * 32767));
                out[at * 2] = (byte) v;
                out[at * 2 + 1] = (byte) (v >> 8);
            }
        }
        return out;
    }
}
