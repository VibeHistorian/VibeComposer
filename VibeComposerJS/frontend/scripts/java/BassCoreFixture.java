package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Note;
import jm.music.data.Phrase;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.Parts.BassPart;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.Enums.*;
import java.util.*;

/** Complete phrases from the production bass consumer; explicit diatonic input chords, no variations. */
public class BassCoreFixture {
    public static void main(String[] args) {
        org.apache.logging.log4j.core.config.Configurator.setRootLevel(org.apache.logging.log4j.Level.OFF);
        org.apache.logging.log4j.core.config.Configurator.setLevel("org.vibehistorian.vibecomposer.LG", org.apache.logging.log4j.Level.OFF);
        String[] seeds = { "42", "-2147483648", "9223372036854775807", "9007199254740993" };
        String[] rhythms = { "full", "half", "tresillo", "sparse", "single", "one-six", "euclid", "custom", "alternating" };
        RhythmPattern[] patterns = { RhythmPattern.FULL, RhythmPattern.ALT, RhythmPattern.TRESILLO,
                RhythmPattern.ONEPER4, RhythmPattern.SINGLE, RhythmPattern.ONESIX, RhythmPattern.EUCLID, RhythmPattern.CUSTOM, RhythmPattern.FULL };
        StringBuilder json = new StringBuilder("[\n"); boolean first = true;
        for (String seed : seeds) for (int sample = 0; sample < 36; sample++) for (PatternJoinMode join : PatternJoinMode.values()) {
            int hits = new int[] { 1, 3, 4, 5, 8, 10, 16, 32 }[sample % 8];
            int count = new int[] { 1, 3, 4, 7 }[sample % 4];
            int key = new int[] { 0, 11, 5 }[sample % 3];
            String keyName = new String[] { "C", "B", "F" }[sample % 3];
            boolean minor = sample % 2 == 1;
            int[] degrees = new int[count];
            int[] scale = minor ? new int[] { 0, 2, 3, 5, 7, 8, 10 } : new int[] { 0, 2, 4, 5, 7, 9, 11 };
            List<int[]> roots = new ArrayList<>();
            for (int chord = 0; chord < count; chord++) {
                int degree = new int[] { 1, 6, 4, 5, 7, 2, 3 }[chord]; degrees[chord] = degree;
                boolean major = minor ? degree == 3 || degree == 6 || degree == 7 : degree == 1 || degree == 4 || degree == 5;
                boolean diminished = minor ? degree == 2 : degree == 7;
                int root = 36 + (key + scale[degree - 1]) % 12;
                roots.add(new int[] { root, root + (major ? 4 : 3), root + (diminished ? 6 : 7) });
            }
            BassPart part = new BassPart(); part.setOrder(1); part.setOrderOffset(1);
            int explicitSeed = sample >= 27 ? -1234567 : 0;
            part.setPatternSeed(explicitSeed != 0 ? explicitSeed : (int) Long.parseLong(seed));
            part.setAlternatingRhythm(sample % 9 == 8); part.setPattern(patterns[sample % 9]);
            part.setPatternJoinMode(join); part.setChordSpan(1 + sample / 9);
            part.setHitsPerPattern(hits); part.setPatternShift(sample % 9); part.setPatternFlip(sample % 3 == 1);
            part.setVelocityMin(69); part.setVelocityMax(90);
            part.setNoteVariation(new int[] { 0, 20, 100 }[(sample / 3) % 3]);
            part.setChordSpanFill(sample % 4 == 0 ? ChordSpanFill.ODD : sample % 4 == 1 ? ChordSpanFill.HALF1 : ChordSpanFill.ALL);
            part.setFillFlip(sample == 35);
            int pulses = sample % (hits + 1);
            List<Integer> grid = new ArrayList<>(), velocities = new ArrayList<>();
            for (int slot = 0; slot < 32; slot++) {
                grid.add(sample % 9 == 6 ? (slot < pulses ? 1 : 0) : (slot % 5 == 0 || slot % 5 == 2 ? 1 : 0));
                velocities.add(slot % 4 == 0 ? 0 : 50 + slot * 2);
            }
            part.setCustomPattern(grid);
            boolean customVelocities = sample >= 18;
            if (customVelocities) part.setCustomVelocities(velocities);
            GUIConfig config = new GUIConfig(); config.setScaleMidiVelocityInArrangement(false);
            Section section = new Section("CHORUS1", 1, 100, 100, 100, 100, 100);
            MidiGenerator midi = new MidiGenerator(config);
            BassPhraseGenerator generator = new BassPhraseGenerator(config, (sec, ip, vars, group, chances) -> Collections.emptyList());
            Phrase phrase = generator.generate(part, roots, Collections.nCopies(count, 4.0), null, section,
                    Collections.emptyList(), 0, midi.getTiming(), 1.0).getPhrase();
            if (!first) json.append(",\n"); first = false;
            json.append("{\"seed\":\"").append(seed).append("\",\"key\":\"").append(keyName)
                    .append("\",\"scale\":\"").append(minor ? "natural-minor" : "major").append("\",\"progression\":").append(Arrays.toString(degrees))
                    .append(",\"settings\":{\"rhythm\":\"").append(rhythms[sample % 9]).append("\",\"patternJoinMode\":\"").append(join)
                    .append("\",\"noteVariation\":").append(part.getNoteVariation()).append(",\"hitsPerPattern\":").append(hits)
                    .append(",\"chordSpan\":").append(part.getChordSpan()).append(",\"patternShift\":").append(part.getPatternShift())
                    .append(",\"patternFlip\":").append(part.isPatternFlip()).append(",\"euclideanPulses\":").append(pulses)
                    .append(",\"customPattern\":").append(grid).append(",\"useCustomVelocities\":").append(customVelocities)
                    .append(",\"customVelocities\":").append(velocities).append(",\"velocityMin\":69,\"velocityMax\":89")
                    .append(",\"patternSeed\":").append(explicitSeed).append(",\"chordSpanFill\":\"").append(part.getChordSpanFill())
                    .append("\",\"fillFlip\":").append(part.isFillFlip()).append("},\"notes\":[");
            double time = 0; boolean firstNote = true;
            for (Object entry : phrase.getNoteList()) {
                Note note = (Note) entry;
                if (note.getPitch() >= 0 && note.getDynamic() > 0) {
                    if (!firstNote) json.append(','); firstNote = false;
                    json.append('[').append(note.getPitch()).append(',').append(time + note.getOffset())
                            .append(',').append(note.getDuration()).append(',').append(note.getDynamic()).append(']');
                }
                time += note.getRhythmValue();
            }
            json.append("]}");
        }
        System.out.println(json.append("\n]"));
    }
}
