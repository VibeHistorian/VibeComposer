package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Note;
import jm.music.data.Phrase;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import java.util.*;

/** Complete single-pitch phrases through the production drum consumer and swing processor. */
public class DrumCoreFixture {
    public static void main(String[] args) {
        org.apache.logging.log4j.core.config.Configurator.setRootLevel(org.apache.logging.log4j.Level.OFF);
        org.apache.logging.log4j.core.config.Configurator.setLevel("org.vibehistorian.vibecomposer.LG", org.apache.logging.log4j.Level.OFF);
        String[] seeds = { "42", "-2147483648", "9223372036854775807", "9007199254740993" };
        String[] rhythms = { "full", "half", "tresillo", "sparse", "single", "one-six", "euclid", "custom" };
        RhythmPattern[] patterns = { RhythmPattern.FULL, RhythmPattern.ALT, RhythmPattern.TRESILLO,
                RhythmPattern.ONEPER4, RhythmPattern.SINGLE, RhythmPattern.ONESIX, RhythmPattern.EUCLID, RhythmPattern.CUSTOM };
        int[] pitches = { 35, 36, 37, 38, 39, 40, 41, 42, 44, 46, 47, 53, 54, 60, 61, 82 };
        StringBuilder json = new StringBuilder("[\n");
        boolean first = true;
        for (String seed : seeds) for (int sample = 0; sample < 24; sample++) {
            int hits = new int[] { 1, 3, 4, 5, 8, 10, 16, 32 }[(sample / 8 + sample) % 8];
            int bars = new int[] { 1, 3, 4, 7 }[sample % 4];
            int pulses = sample % (hits + 1), shift = sample % 9;
            DrumPart part = new DrumPart();
            part.setOrder(1); part.setOrderOffset(1); part.setPatternSeed((int) Long.parseLong(seed));
            part.setInstrument(pitches[sample % pitches.length]); part.setHitsPerPattern(hits);
            part.setPattern(patterns[sample % patterns.length]); part.setPatternShift(shift);
            part.setPatternFlip(sample % 3 == 1); part.setChordSpan(1);
            part.setPauseChance(0); part.setExceptionChance(0); part.setVelocityPattern(false);
            part.setVelocityMin(69); part.setVelocityMax(90); // UI maximum 89 is inclusive.
            part.setSwingPercent(sample % 3 == 0 ? 66 : 50);
            part.setChordSpanFill(sample % 4 == 0 ? ChordSpanFill.ODD : ChordSpanFill.ALL);
            part.setFillFlip(sample == 23);
            List<Integer> grid = new ArrayList<>(), velocities = new ArrayList<>();
            for (int slot = 0; slot < 32; slot++) {
                grid.add(sample % 8 == 6 ? (slot < pulses ? 1 : 0) : (slot % 5 == 0 || slot % 5 == 2 ? 1 : 0));
                velocities.add(slot % 4 == 0 ? 0 : 50 + slot * 2);
            }
            part.setCustomPattern(grid);
            boolean customVelocities = sample >= 16;
            if (customVelocities) part.setCustomVelocities(velocities);
            GUIConfig config = new GUIConfig(); config.setScaleMidiVelocityInArrangement(false);
            Section section = new Section("VERSE1", 1, 100, 100, 100, 100, 100);
            List<int[]> roots = new ArrayList<>();
            for (int bar = 0; bar < bars; bar++) roots.add(new int[] { 60 });
            MidiGenerator midi = new MidiGenerator(config);
            midi.setProgressionDurations(Collections.nCopies(bars, 4.0));
            DrumPhraseGenerator generator = new DrumPhraseGenerator(config, (sec, ip, vars, group, chances) -> Collections.emptyList());
            Phrase phrase = generator.generate(part, roots, Collections.nCopies(bars, 4.0), Collections.nCopies(bars, "C"),
                    null, null, false, section, 1, Collections.emptyList(), 0, midi.getTiming(), 0.95).getPhrase();
            if (phrase.getNoteList().size() > 0) midi.swingPhrase(phrase, hits % 2 == 0 ? part.getSwingPercent() : 50, 1);
            if (!first) json.append(",\n"); first = false;
            json.append("{\"seed\":\"").append(seed).append("\",\"barCount\":").append(bars)
                    .append(",\"settings\":{\"pitch\":").append(part.getInstrument())
                    .append(",\"rhythm\":\"").append(rhythms[sample % 8]).append("\",\"hitsPerPattern\":").append(hits)
                    .append(",\"patternShift\":").append(shift).append(",\"patternFlip\":").append(part.isPatternFlip())
                    .append(",\"euclideanPulses\":").append(pulses).append(",\"customPattern\":").append(grid)
                    .append(",\"useCustomVelocities\":").append(customVelocities).append(",\"customVelocities\":").append(velocities)
                    .append(",\"velocityMin\":69,\"velocityMax\":89,\"swingPercent\":").append(part.getSwingPercent())
                    .append(",\"chordSpanFill\":\"").append(part.getChordSpanFill()).append("\",\"fillFlip\":").append(part.isFillFlip())
                    .append("},\"notes\":[");
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
