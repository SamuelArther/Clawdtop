package clawdtop;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Random;
import java.util.function.IntConsumer;

/**
 * Clawd's mini piano: the songs he knows, and a little piano of your own to play (click the keys, or type A to K for
 * the white keys and W E T Y U for the black ones).
 */
final class Piano {
    /** The service songs, by file name (songs/veterans/navy.mid and so on), so he says the real title. */
    static final java.util.Map<String, String> SERVICE_SONGS = java.util.Map.of(
            "army", "The Army Goes Rolling Along",
            "navy", "Anchors Aweigh",
            "marines", "The Marines' Hymn",
            "airforce", "The U.S. Air Force",
            "coastguard", "Semper Paratus",
            "spaceforce", "Semper Supra");

    /** A song: its name, notes (MIDI numbers, 60 is middle C), and how many beats each one lasts. */
    record Song(String name, int[] notes, double[] beats, int beatMs, java.io.File midi, long fullMs) {
        Song(String name, int[] notes, double[] beats, int beatMs) {
            this(name, notes, beats, beatMs, null, 0);
        }

        /** How long it lasts, ms (a whole MIDI file plays to its end, chords and all). */
        long length() {
            double total = 0;
            for (double b : beats) total += b;
            return Math.max(fullMs, (long) (total * beatMs));
        }
    }

    static final Song[] SONGS = {
            new Song("Twinkle Twinkle Little Star",
                    new int[] {60, 60, 67, 67, 69, 69, 67, 65, 65, 64, 64, 62, 62, 60},
                    new double[] {1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 2}, 330),
            new Song("Ode to Joy",
                    new int[] {64, 64, 65, 67, 67, 65, 64, 62, 60, 60, 62, 64, 64, 62, 62},
                    new double[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1.5, 0.5, 2}, 320),
            new Song("Mary Had a Little Lamb",
                    new int[] {64, 62, 60, 62, 64, 64, 64, 62, 62, 62, 64, 67, 67},
                    new double[] {1, 1, 1, 1, 1, 1, 2, 1, 1, 2, 1, 1, 2}, 320),
            new Song("Fur Elise (the famous bit)",
                    new int[] {76, 75, 76, 75, 76, 71, 74, 72, 69, 60, 64, 69, 71},
                    new double[] {0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1.5, 0.5, 0.5, 0.5, 1.5}, 340),
            new Song("Hot Cross Buns",
                    new int[] {64, 62, 60, 64, 62, 60, 60, 60, 60, 60, 62, 62, 62, 62, 64, 62, 60},
                    new double[] {1, 1, 2, 1, 1, 2, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1, 1, 2}, 300)};

    /** What he can play. */
    enum Instrument {
        PIANO("piano"), GUITAR("guitar"), BASS("bass"), DRUMS("drums"), VOICE("voice");

        final String shown;

        Instrument(String shown) {
            this.shown = shown;
        }
    }

    /** Drum notes (the usual MIDI drum numbers): bass drum, snare, closed hi-hat, crash cymbal, tom. */
    static final int KICK = 36, SNARE = 38, HAT = 42, CRASH = 49, TOM = 45;

    /** Beats for his drum set. */
    static final Song[] BEATS = {
            new Song("a rock beat",
                    new int[] {KICK, HAT, SNARE, HAT, KICK, KICK, SNARE, HAT, KICK, HAT, SNARE, HAT, KICK, KICK, SNARE, CRASH},
                    new double[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2}, 220),
            new Song("a drum solo",
                    new int[] {SNARE, SNARE, TOM, TOM, KICK, SNARE, TOM, KICK, SNARE, SNARE, SNARE, TOM, TOM, KICK, KICK, CRASH},
                    new double[] {0.5, 0.5, 0.5, 0.5, 1, 0.5, 0.5, 1, 0.25, 0.25, 0.5, 0.5, 0.5, 0.5, 0.5, 2}, 240),
            new Song("ba-dum-tss",
                    new int[] {SNARE, TOM, CRASH},
                    new double[] {1, 1, 2}, 220),
            new Song("a march",
                    new int[] {KICK, SNARE, SNARE, SNARE, KICK, SNARE, SNARE, SNARE, KICK, SNARE, KICK, SNARE, SNARE, SNARE, SNARE, CRASH},
                    new double[] {1, 0.5, 0.5, 1, 1, 0.5, 0.5, 1, 1, 1, 1, 0.5, 0.5, 0.5, 0.5, 2}, 260)};

    /** A song he makes up on the spot (from the notes that always sound nice together). */
    static Song madeUp(Random random) {
        int[] scale = {60, 62, 64, 67, 69, 72, 74, 76};
        int count = 10 + random.nextInt(5);
        int[] notes = new int[count];
        double[] beats = new double[count];
        int at = random.nextInt(4);
        for (int i = 0; i < count; i++) {
            at = Math.max(0, Math.min(scale.length - 1, at + random.nextInt(5) - 2));
            notes[i] = scale[at];
            beats[i] = random.nextInt(4) == 0 ? 2 : random.nextBoolean() ? 1 : 0.5;
        }
        notes[count - 1] = 60; // it ends on C, so it sounds finished
        beats[count - 1] = 2;
        return new Song("a song I just made up", notes, beats, 300);
    }

    /** How many notes can sound at once in a song file (more than that gets thinned out: busy songs get simpler). */
    static final int MAX_AT_ONCE = 6;
    /** The electric piano, and how long a song's notes are held (typically) for it to get that instead of the grand. */
    static final int ELECTRIC_PIANO = 4, LONG_NOTES_MS = 600;
    /** In a crowded song: notes shorter than this are left out (ms), and notes starting this close together are one chord. */
    static final int QUICK_MS = 100, TOGETHER_MS = 40;
    /** Notes a second that make a song crowded (thinned down to three at once: the tune, the bass and one more). */
    static final double CROWDED = 12;
    /** The instrument his singing voice uses in a song file: a soft square wave, like his beeps. */
    static final int VOICE_PROGRAM = 80;
    /** How hard (on average) the notes of a song file are played, at least: a softly written song is played up to this. */
    static final int LOUDNESS = 80;

    /**
     * A song file made his: every instrument becomes his piano, singing parts become his little beep voice (following
     * the notes), the drums and the effects (reverb, chorus, wobbles) are left out, and if it's very busy (lots of notes
     * at once, or the same note doubled by several instruments) it's thinned out a bit. Not too much: it's still the song.
     */
    static javax.sound.midi.Sequence pianoOnly(javax.sound.midi.Sequence seq) throws javax.sound.midi.InvalidMidiDataException {
        return forClawd(seq, true);
    }

    /** The same, keeping the song's own instruments and drums (for the end of a jam session: the whole band). */
    static javax.sound.midi.Sequence fullBand(javax.sound.midi.Sequence seq) throws javax.sound.midi.InvalidMidiDataException {
        return forClawd(seq, false);
    }

    private static javax.sound.midi.Sequence forClawd(javax.sound.midi.Sequence seq, boolean allPiano) throws javax.sound.midi.InvalidMidiDataException {
        javax.sound.midi.Track[] tracks = seq.getTracks();
        // which channels are singing: a voice or choir instrument, or a track named like one
        boolean[] vocal = new boolean[16];
        for (javax.sound.midi.Track track : tracks) {
            boolean namedVocal = false;
            java.util.Set<Integer> channels = new java.util.HashSet<>();
            for (int i = 0; i < track.size(); i++) {
                javax.sound.midi.MidiMessage msg = track.get(i).getMessage();
                if (msg instanceof javax.sound.midi.MetaMessage meta && (meta.getType() == 0x03 || meta.getType() == 0x04)) {
                    String name = new String(meta.getData(), java.nio.charset.StandardCharsets.ISO_8859_1).toLowerCase(java.util.Locale.ROOT);
                    if (name.matches(".*(vocal|voice|vox|sing|lyric|choir|soprano|alto|tenor|baritone).*")) namedVocal = true;
                } else if (msg instanceof javax.sound.midi.ShortMessage m) {
                    channels.add(m.getChannel());
                    if (m.getCommand() == javax.sound.midi.ShortMessage.PROGRAM_CHANGE && java.util.List.of(52, 53, 54, 85, 91).contains(m.getData1())) {
                        vocal[m.getChannel()] = true;
                    }
                }
            }
            if (namedVocal) for (int c : channels) if (c != 9) vocal[c] = true;
        }
        // a song of long held notes gets his electric piano, which rings on (the grand fades too fast for them)
        java.util.List<Long> held = new java.util.ArrayList<>();
        for (javax.sound.midi.Track track : tracks) {
            java.util.Map<Integer, Long> down = new java.util.HashMap<>();
            for (int i = 0; i < track.size(); i++) {
                if (!(track.get(i).getMessage() instanceof javax.sound.midi.ShortMessage m) || m.getChannel() == 9 || vocal[m.getChannel()]) continue;
                int key = m.getChannel() * 128 + m.getData1();
                boolean on = m.getCommand() == javax.sound.midi.ShortMessage.NOTE_ON && m.getData2() > 0;
                boolean off = m.getCommand() == javax.sound.midi.ShortMessage.NOTE_OFF || (m.getCommand() == javax.sound.midi.ShortMessage.NOTE_ON && m.getData2() == 0);
                if (on) down.put(key, track.get(i).getTick());
                else if (off && down.containsKey(key)) held.add(track.get(i).getTick() - down.remove(key));
            }
        }
        java.util.Collections.sort(held);
        double msPerTick = seq.getMicrosecondLength() / 1000.0 / Math.max(1, seq.getTickLength());
        int pianoSound = !held.isEmpty() && held.get(held.size() / 2) * msPerTick >= LONG_NOTES_MS ? ELECTRIC_PIANO : 0;
        // a part that only ever hits one or two low notes is really a drum (an 808 boom, say): on his piano it'd be a thud
        java.util.Map<Integer, java.util.Set<Integer>> pitches = new java.util.HashMap<>();
        int[] hits = new int[16];
        for (javax.sound.midi.Track track : tracks) {
            for (int i = 0; i < track.size(); i++) {
                if (track.get(i).getMessage() instanceof javax.sound.midi.ShortMessage m && m.getCommand() == javax.sound.midi.ShortMessage.NOTE_ON && m.getData2() > 0) {
                    pitches.computeIfAbsent(m.getChannel(), c -> new java.util.HashSet<>()).add(m.getData1());
                    hits[m.getChannel()]++;
                }
            }
        }
        boolean[] thud = new boolean[16];
        for (var entry : pitches.entrySet()) {
            int c = entry.getKey();
            thud[c] = allPiano && c != 9 && !vocal[c] && hits[c] >= 8 && entry.getValue().size() <= 2 && java.util.Collections.max(entry.getValue()) < 40;
        }
        // out go the drums, the instrument changes and the effects (volume, expression and the sustain pedal stay)
        for (javax.sound.midi.Track track : tracks) {
            for (int i = track.size() - 1; i >= 0; i--) {
                javax.sound.midi.MidiEvent e = track.get(i);
                if (!(e.getMessage() instanceof javax.sound.midi.ShortMessage m)) continue;
                int cmd = m.getCommand();
                boolean drums = allPiano && (m.getChannel() == 9 || thud[m.getChannel()]);
                boolean effect = (allPiano && cmd == javax.sound.midi.ShortMessage.PROGRAM_CHANGE) || cmd == javax.sound.midi.ShortMessage.PITCH_BEND
                        || cmd == javax.sound.midi.ShortMessage.CHANNEL_PRESSURE || cmd == javax.sound.midi.ShortMessage.POLY_PRESSURE
                        || (cmd == javax.sound.midi.ShortMessage.CONTROL_CHANGE && m.getData1() != 7 && m.getData1() != 11 && m.getData1() != 64);
                if (drums || effect) track.remove(e);
                else if (allPiano && m.getChannel() != 9 && (cmd == javax.sound.midi.ShortMessage.NOTE_ON || cmd == javax.sound.midi.ShortMessage.NOTE_OFF) && m.getData1() < 28) {
                    m.setMessage(cmd, m.getChannel(), m.getData1() + 12, m.getData2()); // (the very bottom of the piano is just rumble: up an octave)
                }
            }
        }
        // notes that end the moment they start can't be heard, and they'd confuse the counting below (left out)
        for (javax.sound.midi.Track track : tracks) {
            java.util.Map<Integer, javax.sound.midi.MidiEvent> down = new java.util.HashMap<>();
            java.util.List<javax.sound.midi.MidiEvent> silent = new java.util.ArrayList<>();
            for (int i = 0; i < track.size(); i++) {
                javax.sound.midi.MidiEvent e = track.get(i);
                if (!(e.getMessage() instanceof javax.sound.midi.ShortMessage m)) continue;
                int key = m.getChannel() * 128 + m.getData1();
                if (m.getCommand() == javax.sound.midi.ShortMessage.NOTE_ON && m.getData2() > 0) down.put(key, e);
                else if (m.getCommand() == javax.sound.midi.ShortMessage.NOTE_OFF || m.getCommand() == javax.sound.midi.ShortMessage.NOTE_ON) {
                    javax.sound.midi.MidiEvent on = down.remove(key);
                    if (on != null && on.getTick() == e.getTick()) {
                        silent.add(on);
                        silent.add(e);
                    }
                }
            }
            for (javax.sound.midi.MidiEvent e : silent) track.remove(e);
        }
        // a crowded song is boiled down: no quick little notes (trills, rolls, flourishes), just the tune on top and a bassline
        if (allPiano) {
            record Held(javax.sound.midi.Track track, javax.sound.midi.MidiEvent on, javax.sound.midi.MidiEvent off, int pitch, double startMs, double ms) {
            }
            java.util.List<Held> heldNotes = new java.util.ArrayList<>();
            for (javax.sound.midi.Track track : tracks) {
                java.util.Map<Integer, java.util.ArrayDeque<javax.sound.midi.MidiEvent>> down = new java.util.HashMap<>();
                for (int i = 0; i < track.size(); i++) {
                    javax.sound.midi.MidiEvent e = track.get(i);
                    if (!(e.getMessage() instanceof javax.sound.midi.ShortMessage m) || m.getChannel() == 9 || vocal[m.getChannel()]) continue;
                    int key = m.getChannel() * 128 + m.getData1();
                    if (m.getCommand() == javax.sound.midi.ShortMessage.NOTE_ON && m.getData2() > 0) {
                        down.computeIfAbsent(key, k -> new java.util.ArrayDeque<>()).add(e);
                    } else if (m.getCommand() == javax.sound.midi.ShortMessage.NOTE_OFF || m.getCommand() == javax.sound.midi.ShortMessage.NOTE_ON) {
                        javax.sound.midi.MidiEvent on = down.getOrDefault(key, new java.util.ArrayDeque<>()).poll();
                        if (on != null) heldNotes.add(new Held(track, on, e, m.getData1(), on.getTick() * msPerTick, (e.getTick() - on.getTick()) * msPerTick));
                    }
                }
            }
            if (heldNotes.size() / Math.max(1.0, seq.getMicrosecondLength() / 1e6) > CROWDED) {
                heldNotes.sort(java.util.Comparator.comparingDouble(Held::startMs));
                for (int i = 0; i < heldNotes.size(); ) {
                    int j = i;
                    while (j < heldNotes.size() && heldNotes.get(j).startMs() - heldNotes.get(i).startMs() < TOGETHER_MS) j++; // (played together)
                    int top = -1, bottom = 128;
                    for (Held h : heldNotes.subList(i, j)) {
                        if (h.ms() < QUICK_MS) continue;
                        top = Math.max(top, h.pitch());
                        bottom = Math.min(bottom, h.pitch());
                    }
                    for (Held h : heldNotes.subList(i, j)) {
                        boolean keep = h.ms() >= QUICK_MS && (h.pitch() == top || (h.pitch() == bottom && bottom <= top - 12));
                        if (!keep) {
                            h.track().remove(h.on());
                            h.track().remove(h.off());
                        }
                    }
                    i = j;
                }
            }
        }
        // thinning: the same note doubled by another instrument plays once, and no more than MAX_AT_ONCE at a time
        record Note(long tick, javax.sound.midi.Track track, javax.sound.midi.MidiEvent event, int channel, int pitch, boolean on) {
        }
        java.util.List<Note> notes = new java.util.ArrayList<>();
        for (javax.sound.midi.Track track : tracks) {
            for (int i = 0; i < track.size(); i++) {
                javax.sound.midi.MidiEvent e = track.get(i);
                if (!(e.getMessage() instanceof javax.sound.midi.ShortMessage m)) continue;
                int cmd = m.getCommand();
                if (cmd != javax.sound.midi.ShortMessage.NOTE_ON && cmd != javax.sound.midi.ShortMessage.NOTE_OFF) continue;
                boolean on = cmd == javax.sound.midi.ShortMessage.NOTE_ON && m.getData2() > 0;
                notes.add(new Note(e.getTick(), track, e, m.getChannel(), m.getData1(), on));
            }
        }
        // (at the same moment: notes ending first, then the highest note and the lowest, so the tune and the bass are kept)
        notes.sort((a, b) -> a.tick() != b.tick() ? Long.compare(a.tick(), b.tick()) : a.on() != b.on() ? Boolean.compare(a.on(), b.on()) : Integer.compare(b.pitch(), a.pitch()));
        for (int i = 0; i < notes.size(); ) {
            int j = i;
            while (j < notes.size() && notes.get(j).tick() == notes.get(i).tick()) j++;
            int firstOn = i;
            while (firstOn < j && !notes.get(firstOn).on()) firstOn++;
            if (j - firstOn > 2) notes.add(firstOn + 1, notes.remove(j - 1)); // (the lowest, straight after the highest)
            i = j;
        }
        // a crowded song (lots of notes a second) is thinned more, down to a few at once
        int starts = 0;
        for (Note n : notes) if (n.on() && n.channel() != 9 && !vocal[n.channel()]) starts++;
        double perSecond = starts / Math.max(1.0, seq.getMicrosecondLength() / 1e6);
        int atOnce = allPiano && perSecond > CROWDED ? MAX_AT_ONCE - 3 : MAX_AT_ONCE;
        java.util.Map<Integer, Integer> sounding = new java.util.HashMap<>(); // pitch -> how many (piano parts)
        java.util.Map<Integer, Integer> own = new java.util.HashMap<>();      // channel*128+pitch -> how many
        java.util.Map<Integer, Integer> dropOff = new java.util.HashMap<>();  // channel*128+pitch -> note-offs to drop
        int playing = 0;
        for (Note n : notes) {
            int key = n.channel() * 128 + n.pitch();
            if (!n.on()) {
                if (dropOff.getOrDefault(key, 0) > 0) {
                    dropOff.merge(key, -1, Integer::sum);
                    n.track().remove(n.event());
                } else if (own.getOrDefault(key, 0) > 0) {
                    own.merge(key, -1, Integer::sum);
                    sounding.merge(n.pitch(), -1, Integer::sum);
                    playing--;
                }
                continue;
            }
            if (vocal[n.channel()] || n.channel() == 9) continue; // (his singing, and drums, are never thinned)
            if (own.getOrDefault(key, 0) > 0) { // the same note again before the last one let go: struck again (not dropped)
                n.track().add(new javax.sound.midi.MidiEvent(new javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.NOTE_OFF, n.channel(), n.pitch(), 0),
                        Math.max(0, n.tick() - 1)));
                dropOff.merge(key, 1, Integer::sum); // (and the old one's ending goes, since it's ended now)
                continue;
            }
            if (sounding.getOrDefault(n.pitch(), 0) > 0 || playing >= atOnce) { // doubled by another part, or too many at once
                n.track().remove(n.event());
                dropOff.merge(key, 1, Integer::sum);
                continue;
            }
            sounding.merge(n.pitch(), 1, Integer::sum);
            own.merge(key, 1, Integer::sum);
            playing++;
        }
        // a song written very softly is played up, so it's about as loud as the others (the soft and loud bits stay soft and loud)
        long velocities = 0;
        int count = 0;
        for (javax.sound.midi.Track track : tracks) {
            for (int i = 0; i < track.size(); i++) {
                if (track.get(i).getMessage() instanceof javax.sound.midi.ShortMessage m && m.getCommand() == javax.sound.midi.ShortMessage.NOTE_ON && m.getData2() > 0) {
                    velocities += m.getData2();
                    count++;
                }
            }
        }
        double louder = count == 0 ? 1 : Math.max(1, Math.min(3.5, LOUDNESS / ((double) velocities / count)));
        if (louder > 1.05) {
            for (javax.sound.midi.Track track : tracks) {
                for (int i = 0; i < track.size(); i++) {
                    if (track.get(i).getMessage() instanceof javax.sound.midi.ShortMessage m && m.getCommand() == javax.sound.midi.ShortMessage.NOTE_ON && m.getData2() > 0) {
                        m.setMessage(m.getCommand(), m.getChannel(), m.getData1(), Math.min(127, (int) Math.round(m.getData2() * louder)));
                    }
                }
            }
        }
        // and the instruments: his piano for everything, his beep voice for singing (a little softer)
        javax.sound.midi.Track first = tracks.length > 0 ? tracks[0] : seq.createTrack();
        for (int channel = 0; channel < 16; channel++) {
            if (channel == 9) continue;
            if (allPiano || vocal[channel]) first.add(new javax.sound.midi.MidiEvent(new javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.PROGRAM_CHANGE,
                    channel, vocal[channel] ? VOICE_PROGRAM : pianoSound, 0), 0));
            first.add(new javax.sound.midi.MidiEvent(new javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.CONTROL_CHANGE, channel, 91, 0), 0)); // no reverb
            first.add(new javax.sound.midi.MidiEvent(new javax.sound.midi.ShortMessage(javax.sound.midi.ShortMessage.CONTROL_CHANGE, channel, 93, 0), 0)); // no chorus
        }
        return seq;
    }

    /** One part of a jam track: which of his instruments plays it, and a bit of it to record. */
    record Part(Instrument instrument, Song bit) {
    }

    /**
     * A jam track split into its parts (its own instrument tracks): drums, bass, guitar, keys and singing, each turned
     * into one of his instruments, with a little bit of that part for him to record. Up to five parts.
     */
    static java.util.List<Part> jamParts(java.io.File file) {
        java.util.List<Part> parts = new java.util.ArrayList<>();
        try {
            javax.sound.midi.Sequence seq = javax.sound.midi.MidiSystem.getSequence(file);
            int[] program = new int[16];
            java.util.Map<Integer, java.util.TreeMap<Long, Integer>> onsets = new java.util.TreeMap<>(); // channel -> tick -> note
            for (javax.sound.midi.Track track : seq.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    javax.sound.midi.MidiEvent e = track.get(i);
                    if (!(e.getMessage() instanceof javax.sound.midi.ShortMessage m)) continue;
                    if (m.getCommand() == javax.sound.midi.ShortMessage.PROGRAM_CHANGE) program[m.getChannel()] = m.getData1();
                    if (m.getCommand() == javax.sound.midi.ShortMessage.NOTE_ON && m.getData2() > 0) {
                        onsets.computeIfAbsent(m.getChannel(), c -> new java.util.TreeMap<>()).merge(e.getTick(), m.getData1(), Math::max);
                    }
                }
            }
            double msPerTick = seq.getMicrosecondLength() / 1000.0 / Math.max(1, seq.getTickLength());
            java.util.Map<Instrument, Integer> best = new java.util.EnumMap<>(Instrument.class); // the busiest channel for each instrument
            for (var entry : onsets.entrySet()) {
                int channel = entry.getKey(), p = program[channel];
                Instrument as = channel == 9 ? Instrument.DRUMS : (p >= 24 && p <= 31) ? Instrument.GUITAR : (p >= 32 && p <= 39) ? Instrument.BASS
                        : (p == 52 || p == 53 || p == 54 || p == 85 || p == 91) ? Instrument.VOICE : Instrument.PIANO;
                Integer had = best.get(as);
                if (had == null || onsets.get(had).size() < entry.getValue().size()) best.put(as, channel);
            }
            for (Instrument as : new Instrument[] {Instrument.DRUMS, Instrument.BASS, Instrument.GUITAR, Instrument.PIANO, Instrument.VOICE}) {
                Integer channel = best.get(as);
                if (channel == null || onsets.get(channel).size() < 4) continue;
                java.util.List<Long> ticks = new java.util.ArrayList<>(onsets.get(channel).keySet());
                int count = Math.min(as == Instrument.DRUMS ? 16 : 12, ticks.size());
                int[] notes = new int[count];
                double[] ms = new double[count];
                for (int i = 0; i < count; i++) {
                    int n = onsets.get(channel).get(ticks.get(i));
                    notes[i] = as == Instrument.DRUMS ? drumFor(n) : as == Instrument.BASS ? n + 24 : n; // (his bass plays two octaves down)
                    long next = i + 1 < ticks.size() ? ticks.get(i + 1) : ticks.get(i) + Math.max(1, seq.getResolution());
                    ms[i] = Math.max(80, Math.min(1500, (next - ticks.get(i)) * msPerTick));
                }
                parts.add(new Part(as, new Song(file.getName(), notes, ms, 1)));
            }
        } catch (Exception notAMidi) {
            // no parts, then
        }
        return parts;
    }

    /** A General MIDI drum (35 to 81) as one of his five drums. */
    static int drumFor(int gm) {
        if (gm == 35 || gm == 36) return KICK;
        if (gm == 37 || gm == 38 || gm == 39 || gm == 40) return SNARE;
        if (gm == 42 || gm == 44 || gm == 46 || gm == 51 || gm == 53 || gm == 59) return HAT;
        if (gm == 49 || gm == 52 || gm == 55 || gm == 57) return CRASH;
        return TOM;
    }

    /**
     * A MIDI file as a song for his piano: the tune (the highest note whenever notes start together), up to 400 notes,
     * with the real timing. Null if it isn't a MIDI file or has no notes.
     */
    static Song fromMidi(java.io.File file) {
        try {
            javax.sound.midi.Sequence seq = javax.sound.midi.MidiSystem.getSequence(file);
            java.util.TreeMap<Long, Integer> top = new java.util.TreeMap<>(); // start tick -> highest note
            long lastNote = 0; // when the last note ends (some files go on in silence for minutes after)
            for (javax.sound.midi.Track track : seq.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    javax.sound.midi.MidiEvent e = track.get(i);
                    if (e.getMessage() instanceof javax.sound.midi.ShortMessage any && (any.getCommand() == javax.sound.midi.ShortMessage.NOTE_ON
                            || any.getCommand() == javax.sound.midi.ShortMessage.NOTE_OFF)) lastNote = Math.max(lastNote, e.getTick());
                    if (e.getMessage() instanceof javax.sound.midi.ShortMessage m && m.getCommand() == javax.sound.midi.ShortMessage.NOTE_ON
                            && m.getData2() > 0 && m.getChannel() != 9) { // (channel 10 is drums)
                        top.merge(e.getTick(), m.getData1(), Math::max);
                    }
                }
            }
            if (top.isEmpty() || seq.getTickLength() == 0) return null;
            double msPerTick = seq.getMicrosecondLength() / 1000.0 / seq.getTickLength();
            java.util.List<Long> ticks = new java.util.ArrayList<>(top.keySet());
            int count = Math.min(400, ticks.size());
            int[] notes = new int[count];
            double[] ms = new double[count];
            for (int i = 0; i < count; i++) {
                int n = top.get(ticks.get(i));
                while (n > 84) n -= 12; // keep it on his little piano
                while (n < 48) n += 12;
                notes[i] = n;
                long next = i + 1 < ticks.size() ? ticks.get(i + 1) : ticks.get(i) + seq.getResolution();
                ms[i] = Math.max(60, Math.min(2000, (next - ticks.get(i)) * msPerTick));
            }
            String name = file.getName().replaceAll("(?i)\\.midi?$", "").replace('_', ' ');
            name = SERVICE_SONGS.getOrDefault(name.toLowerCase(java.util.Locale.ROOT), name); // army.mid -> its real title
            long songMs = Math.min(seq.getMicrosecondLength() / 1000, (long) (lastNote * msPerTick) + 600); // (ends with its last note)
            return new Song("your " + (name.length() > 30 ? name.substring(0, 30) : name), notes, ms, 1, file, songMs);
        } catch (Exception notMidi) {
            return null;
        }
    }

    /** Whether a file looks like MIDI (by its name). */
    static boolean isMidi(java.io.File f) {
        String n = f.getName().toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".mid") || n.endsWith(".midi");
    }

    /** Where a note's key is on his little piano, 0 (left) to 1 (right). */
    static double place(int midi) {
        return Math.max(0, Math.min(1, (midi - 58) / 20.0));
    }

    // ---- Your own little piano ----

    private static final int[] WHITE = {60, 62, 64, 65, 67, 69, 71, 72};      // C D E F G A B C
    private static final int[] BLACK = {61, 63, -1, 66, 68, 70, -1};          // between them (none after E and B)
    private static final String WHITE_KEYS = "ASDFGHJK", BLACK_KEYS = "WE TYU";
    private static final int KEY_W = 30, KEY_H = 96, TOP = 28;

    private final TypingWindow window = java.awt.GraphicsEnvironment.isHeadless() ? null : new TypingWindow();
    private int pressed = -1;
    private long pressedAt;

    /** Shows your piano above Clawd. played gets each note you play. */
    void show(Rectangle clawd, Rectangle screen, IntConsumer played) {
        if (window == null) return;
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(java.awt.AlphaComposite.Clear);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setComposite(java.awt.AlphaComposite.SrcOver);
                Piano.paint(g2, getWidth(), getHeight(), System.currentTimeMillis() - pressedAt < 160 ? pressed : -1);
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setFocusable(true);
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getY() < TOP && e.getX() > panel.getWidth() - 26) {
                    window.setVisible(false); // the little x
                    return;
                }
                int note = keyAt(e.getX(), e.getY());
                if (note > 0) press(note, played, panel);
            }
        });
        panel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                char c = Character.toUpperCase(e.getKeyChar());
                int w = WHITE_KEYS.indexOf(c), b = BLACK_KEYS.indexOf(c);
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) window.setVisible(false);
                else if (w >= 0) press(WHITE[w], played, panel);
                else if (b >= 0 && c != ' ' && BLACK[b] > 0) press(BLACK[b], played, panel);
            }
        });
        Platform.seeThrough(window);
        window.setContentPane(panel);
        window.setAlwaysOnTop(true);
        window.setType(java.awt.Window.Type.UTILITY);
        Dimension size = size();
        window.setSize(size);
        int x = Math.max(screen.x + 4, Math.min(screen.x + screen.width - size.width - 4, clawd.x + clawd.width / 2 - size.width + 40));
        window.setLocation(x, Math.max(screen.y + 4, clawd.y - size.height - 4));
        window.showAndFocus(panel);
        javax.swing.Timer redraw = new javax.swing.Timer(60, null);
        redraw.addActionListener(e -> { // (stops once the piano's closed)
            if (window.isVisible()) panel.repaint();
            else redraw.stop();
        });
        redraw.start();
    }

    /** Where the first white key is on the screen (for the screen test), or null. */
    java.awt.Point firstKeyOnScreen() {
        if (window == null || !window.isShowing()) return null;
        java.awt.Point p = window.getLocationOnScreen();
        return new java.awt.Point(p.x + 10 + KEY_W / 2, p.y + TOP + KEY_H - 15);
    }

    boolean showing() {
        return window != null && window.isVisible();
    }

    private void press(int note, IntConsumer played, JPanel panel) {
        pressed = note;
        pressedAt = System.currentTimeMillis();
        played.accept(note);
        panel.repaint();
    }

    static Dimension size() {
        return new Dimension(WHITE.length * KEY_W + 20, KEY_H + TOP + 12);
    }

    /** Which note is at (x, y) on the piano, or -1. Black keys sit on top, so they're checked first. */
    static int keyAt(int x, int y) {
        int kx = x - 10, ky = y - TOP;
        if (ky < 0 || ky > KEY_H || kx < 0) return -1;
        if (ky < KEY_H * 0.6) {
            for (int i = 0; i < BLACK.length; i++) {
                int bx = (i + 1) * KEY_W - KEY_W / 3;
                if (BLACK[i] > 0 && kx >= bx && kx < bx + KEY_W * 2 / 3) return BLACK[i];
            }
        }
        int w = kx / KEY_W;
        return w < WHITE.length ? WHITE[w] : -1;
    }

    /** Draws your piano: a little wooden case, white and black keys (the pressed one darker), and its keys' letters. */
    static void paint(Graphics2D g, int width, int height, int pressed) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(200, 90, 70));
        g.fillRoundRect(1, 1, width - 2, height - 2, 14, 14);
        g.setColor(new Color(150, 60, 48));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(1, 1, width - 3, height - 3, 14, 14);
        g.setColor(Color.WHITE);
        g.setFont(Bubble.FIRST_LINE);
        g.drawString("Clawd's piano", 12, 19);
        g.drawString("x", width - 18, 19);
        g.setFont(Bubble.FONT.deriveFont(10f));
        for (int i = 0; i < WHITE.length; i++) {
            int x = 10 + i * KEY_W;
            g.setColor(WHITE[i] == pressed ? new Color(225, 225, 235) : Color.WHITE);
            g.fillRect(x, TOP, KEY_W - 2, KEY_H);
            g.setColor(new Color(170, 170, 180));
            g.drawString(String.valueOf(WHITE_KEYS.charAt(i)), x + KEY_W / 2 - 4, TOP + KEY_H - 6);
        }
        for (int i = 0; i < BLACK.length; i++) {
            if (BLACK[i] < 0) continue;
            int x = 10 + (i + 1) * KEY_W - KEY_W / 3;
            g.setColor(BLACK[i] == pressed ? new Color(90, 90, 100) : new Color(30, 30, 34));
            g.fillRect(x, TOP, KEY_W * 2 / 3, (int) (KEY_H * 0.6));
        }
    }
}
