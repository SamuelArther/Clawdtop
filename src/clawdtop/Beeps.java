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

    private String voice = "Normal";
    private int volume = 5;

    /** His voice ("Normal", "Squeaky", "Deep", "Robot", "Tiny") and volume (1 to 10, 5 normal). */
    public void setVoice(String voice, int volume) {
        this.voice = voice;
        this.volume = volume;
    }

    /** Plays a beep in the background. Does nothing if the computer has no sound. */
    public void play(Pet.Beep beep) {
        String v = voice;
        int vol = volume;
        player.execute(() -> {
            byte[] sound = voiced(make(beep), v, vol);
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

    /**
     * The same beep in another voice: squeaky and tiny are higher (and tiny quieter), deep is lower, robot is chopped
     * up into a buzz. And louder or quieter.
     */
    static byte[] voiced(byte[] pcm, String voice, int volume) {
        int n = pcm.length / 2;
        short[] in = new short[n];
        for (int i = 0; i < n; i++) in[i] = (short) ((pcm[i * 2] & 0xFF) | pcm[i * 2 + 1] << 8);
        double rate = switch (voice) {
            case "Squeaky" -> 1.5;
            case "Tiny" -> 1.9;
            case "Deep" -> 0.65;
            default -> 1;
        };
        int outN = (int) (n / rate);
        byte[] out = new byte[outN * 2];
        double gain = volume / 5.0 * (voice.equals("Tiny") ? 0.6 : 1);
        for (int i = 0; i < outN; i++) {
            double v = in[Math.min(n - 1, (int) (i * rate))] * gain;
            if (voice.equals("Robot") && (i / 90) % 2 == 0) v *= 0.15; // chopped into a buzz
            int s = (int) Math.max(-32768, Math.min(32767, v));
            out[i * 2] = (byte) s;
            out[i * 2 + 1] = (byte) (s >> 8);
        }
        return out;
    }

    /** Plays one piano note (MIDI number, 60 is middle C) for ms, in the background. */
    public void piano(int midi, int ms) {
        int vol = volume;
        player.execute(() -> {
            byte[] sound = voiced(note(midi, ms), "Normal", vol);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(new AudioFormat(RATE, 16, 1, true, false))) {
                line.open();
                line.start();
                line.write(sound, 0, sound.length);
                line.drain();
            } catch (Exception noSound) {
                // quiet
            }
        });
    }

    /** A toy-piano note: a soft tone with a couple of overtones that rings and fades. */
    static byte[] note(int midi, int ms) {
        double freq = 440 * Math.pow(2, (midi - 69) / 12.0);
        int count = (int) (RATE * Math.min(1.2, ms / 1000.0 + 0.15));
        byte[] out = new byte[count * 2];
        for (int i = 0; i < count; i++) {
            double t = i / RATE;
            double tone = Math.sin(2 * Math.PI * freq * t) + 0.35 * Math.sin(4 * Math.PI * freq * t) + 0.12 * Math.sin(6 * Math.PI * freq * t);
            double env = Math.min(1, t / 0.005) * Math.exp(-t * 4.5) * Math.min(1, (count - i) / (RATE * 0.02));
            int v = (int) (tone / 1.47 * env * VOLUME * 1.2 * 32767);
            out[i * 2] = (byte) v;
            out[i * 2 + 1] = (byte) (v >> 8);
        }
        return out;
    }

    /** A party blower's toot: a buzzy sawtooth that slides up as it unrolls, then drops as it rolls back. */
    static byte[] horn() {
        int count = (int) (RATE * 0.55);
        byte[] out = new byte[count * 2];
        double phase = 0;
        for (int i = 0; i < count; i++) {
            double t = i / (double) count;
            double pitch = 420 + 260 * Math.sin(Math.min(1, t * 1.6) * Math.PI / 2) - (t > 0.75 ? (t - 0.75) * 600 : 0);
            phase += pitch / RATE;
            double saw = 2 * (phase - Math.floor(phase + 0.5));          // buzzy
            double fade = Math.min(1, Math.min(i, count - i) / (RATE * 0.02));
            int v = (int) (saw * fade * VOLUME * 0.9 * 32767);
            out[i * 2] = (byte) v;
            out[i * 2 + 1] = (byte) (v >> 8);
        }
        return out;
    }

    /** The sound for a beep, as 16-bit mono samples. Each is a few tiny notes. */
    static byte[] make(Pet.Beep beep) {
        if (beep == Pet.Beep.HORN) return horn();
        double[][] notes = switch (beep) {  // {frequency, milliseconds}, 0 Hz is a pause
            case HELLO -> new double[][] {{1320, 60}, {0, 30}, {1760, 70}};
            case HAPPY -> new double[][] {{1480, 50}, {0, 20}, {1760, 50}, {0, 20}, {2220, 80}};
            case CLICKED -> new double[][] {{1980, 45}, {0, 25}, {1980, 45}};
            case YAWN -> new double[][] {{990, 120}, {880, 120}, {740, 200}};
            case WAKE -> new double[][] {{1170, 60}, {0, 30}, {1480, 90}};
            case TIP -> new double[][] {{1760, 40}, {0, 25}, {1320, 50}, {0, 25}, {1760, 60}};
            case WHEE -> new double[][] {{1320, 40}, {1570, 40}, {1860, 40}, {2220, 90}};
            case OOF -> new double[][] {{660, 70}, {0, 20}, {520, 140}};
            case AWW -> new double[][] {{880, 120}, {740, 180}};
            case ACHOO -> new double[][] {{1600, 30}, {2600, 70}, {900, 60}};
            case CLAP -> new double[][] {{3200, 15}, {0, 10}, {2800, 20}};
            case HORN -> null; // a party blower: made below, a buzzy rising toot
            case PANIC -> new double[][] {{2400, 40}, {1900, 40}, {2400, 40}, {1900, 40}, {2400, 40}, {1900, 40}, {2400, 40}, {1900, 60}};
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
