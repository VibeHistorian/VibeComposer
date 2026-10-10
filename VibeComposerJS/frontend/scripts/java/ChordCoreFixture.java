package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Note;
import jm.music.data.Phrase;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.Enums.*;
import java.util.*;

/** Complete supported Chords consumer, tone expansion and swing; no arrangement variations. */
public class ChordCoreFixture {
    public static void main(String[] args) {
        org.apache.logging.log4j.core.config.Configurator.setRootLevel(org.apache.logging.log4j.Level.OFF);
        org.apache.logging.log4j.core.config.Configurator.setLevel("org.vibehistorian.vibecomposer.LG", org.apache.logging.log4j.Level.OFF);
        String[] seeds = { "42", "-2147483648", "9223372036854775807", "9007199254740993" };
        String[] rhythms = { "full", "half", "tresillo", "sparse", "single", "one-six", "euclid", "custom" };
        RhythmPattern[] patterns = { RhythmPattern.FULL, RhythmPattern.ALT, RhythmPattern.TRESILLO,
                RhythmPattern.ONEPER4, RhythmPattern.SINGLE, RhythmPattern.ONESIX, RhythmPattern.EUCLID, RhythmPattern.CUSTOM };
        StringJoiner json = new StringJoiner(",\n", "[\n", "\n]");
        for (String seed : seeds) for (int sample = 0; sample < 72; sample++) {
            int hits = new int[] { 1, 3, 4, 5, 8, 10, 16, 32 }[sample % 8];
            int count = new int[] { 1, 3, 4, 7 }[sample % 4];
            int key = new int[] { 0, 11, 5 }[sample % 3];
            String keyName = new String[] { "C", "B", "F" }[sample % 3];
            boolean minor = sample % 2 == 1, open = sample % 3 == 1;
            int[] degrees = new int[count];
            int[] scale = minor ? new int[] { 0, 2, 3, 5, 7, 8, 10 } : new int[] { 0, 2, 4, 5, 7, 9, 11 };
            List<int[]> roots = new ArrayList<>();
            for (int chord = 0; chord < count; chord++) {
                int degree = new int[] { 1, 6, 4, 5, 7, 2, 3 }[chord]; degrees[chord] = degree;
                boolean major = minor ? degree == 3 || degree == 6 || degree == 7 : degree == 1 || degree == 4 || degree == 5;
                boolean diminished = minor ? degree == 2 : degree == 7;
                int root = 60 + (key + scale[degree - 1]) % 12;
                int third = major ? 4 : 3, fifth = diminished ? 6 : 7;
                roots.add(open ? new int[] { root, root + fifth, root + 12 + third } : new int[] { root, root + third, root + fifth });
            }
            ChordPart part = new ChordPart(); part.setOrder(1); part.setOrderOffset(1);
            int explicitSeed = sample >= 54 ? -1234567 : 0;
            part.setPatternSeed(explicitSeed != 0 ? explicitSeed : (int) Long.parseLong(seed));
            part.setPattern(patterns[sample % 8]); part.setPatternJoinMode(PatternJoinMode.values()[(sample / 8) % 3]);
            part.setChordSpan(1 + (sample / 18) % 4); part.setHitsPerPattern(hits);
            part.setPatternShift(sample % 9); part.setPatternFlip(sample % 3 == 1);
            part.setVelocityMin(69); part.setVelocityMax(90);
            part.setNoteLengthMultiplier(new int[] { 75, 100, 150 }[(sample / 3) % 3]);
            part.setChordSpanFill(sample % 4 == 0 ? ChordSpanFill.ODD : sample % 4 == 1 ? ChordSpanFill.HALF1 : ChordSpanFill.ALL);
            part.setFillFlip(sample == 71);
            part.setTransitionChance(new int[] { 0, 35, 100 }[sample % 3]);
            part.setTransitionSplit(new int[] { 0, 625, 1000 }[(sample / 3) % 3]);
            part.setStrum(new int[] { 0, 31, 62, 333, 666, 1000, 1500, 2000 }[(sample / 9) % 8]);
            part.setStrumType(StrumType.values()[sample % 9]);
            part.setStrumPauseChance(new int[] { 0, 35, 100 }[(sample / 18) % 3]);
            part.setPauseChance(sample % 4 == 2 ? 30 : 0);
            part.setStretchEnabled(sample % 5 == 1); part.setChordNotesStretch(2 + sample % 5);
            part.setSwingPercent(hits % 2 == 0 ? new int[] { 50, 60, 35 }[(sample / 12) % 3] : 50);
            int pulses = sample % (hits + 1);
            List<Integer> grid = new ArrayList<>(), velocities = new ArrayList<>();
            for (int slot = 0; slot < 32; slot++) {
                grid.add(sample % 8 == 6 ? (slot < pulses ? 1 : 0) : (slot % 5 == 0 || slot % 5 == 2 ? 1 : 0));
                velocities.add(slot % 4 == 0 ? 0 : 50 + slot * 2);
            }
            part.setCustomPattern(grid); boolean customVelocities = sample >= 36;
            if (customVelocities) part.setCustomVelocities(velocities);
            GUIConfig config = new GUIConfig(); config.setScaleMidiVelocityInArrangement(false);
            config.getChordGenSettings().setUseStrum(true); config.getChordGenSettings().setUseSplit(true);
            Section section = new Section("CHORUS1", 1, 100, 100, 100, 100, 100);
            MidiGenerator midi = new MidiGenerator(config);
            midi.setProgressionDurations(Collections.nCopies(count, 4.0));
            ChordPhraseGenerator generator = new ChordPhraseGenerator(config, (sec, ip, vars, group, chances) -> Collections.emptyList());
            ChordPhraseGenerator.ChordResult result = generator.generate(part, roots, Collections.nCopies(count, 4.0), null,
                    section, 0, Collections.emptyList(), 1, midi.getTiming(), 1.0);
            Phrase phrase = result.getPhrase(); MidiUtils.addChordsToPhrase(phrase, result.getChords(), result.getFlamming());
            midi.swingPhrase(phrase, part.getSwingPercent(), midi.getTiming().quarterNote);
            StringBuilder entry = new StringBuilder("{\"seed\":\"").append(seed).append("\",\"key\":\"").append(keyName)
                    .append("\",\"scale\":\"").append(minor ? "natural-minor" : "major").append("\",\"progression\":").append(Arrays.toString(degrees))
                    .append(",\"settings\":{\"rhythm\":\"").append(rhythms[sample % 8]).append("\",\"patternJoinMode\":\"").append(part.getPatternJoinMode())
                    .append("\",\"voicing\":\"").append(open ? "open" : "close").append("\",\"noteLengthPercent\":").append(part.getNoteLengthMultiplier())
                    .append(",\"hitsPerPattern\":").append(hits).append(",\"chordSpan\":").append(part.getChordSpan()).append(",\"patternShift\":").append(part.getPatternShift())
                    .append(",\"patternFlip\":").append(part.isPatternFlip()).append(",\"euclideanPulses\":").append(pulses)
                    .append(",\"customPattern\":").append(grid).append(",\"useCustomVelocities\":").append(customVelocities)
                    .append(",\"customVelocities\":").append(velocities).append(",\"velocityMin\":69,\"velocityMax\":89")
                    .append(",\"patternSeed\":").append(explicitSeed).append(",\"chordSpanFill\":\"").append(part.getChordSpanFill())
                    .append("\",\"fillFlip\":").append(part.isFillFlip()).append(",\"transitionChance\":").append(part.getTransitionChance())
                    .append(",\"transitionSplit\":").append(part.getTransitionSplit()).append(",\"strum\":").append(part.getStrum())
                    .append(",\"strumType\":\"").append(part.getStrumType()).append("\",\"strumPauseChance\":").append(part.getStrumPauseChance())
                    .append(",\"pauseChance\":").append(part.getPauseChance()).append(",\"stretchEnabled\":").append(part.isStretchEnabled())
                    .append(",\"chordNotesStretch\":").append(part.getChordNotesStretch()).append(",\"swingPercent\":").append(part.getSwingPercent()).append("},\"notes\":[");
            double time = 0; boolean firstNote = true;
            for (Object value : phrase.getNoteList()) {
                Note note = (Note) value;
                if (note.getPitch() >= 0 && note.getDynamic() > 0) {
                    if (!firstNote) entry.append(','); firstNote = false;
                    entry.append('[').append(note.getPitch()).append(',').append(time + note.getOffset())
                            .append(',').append(note.getDuration()).append(',').append(note.getDynamic()).append(']');
                }
                time += note.getRhythmValue();
            }
            json.add(entry.append("]}").toString());
        }
        System.out.println(json);
    }
}
