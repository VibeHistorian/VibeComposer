package org.vibehistorian.vibecomposer.generation;

import jm.music.data.*;
import org.vibehistorian.vibecomposer.*;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Enums.*;
import java.util.*;

/** Complete production Ghosts/MELODY1 phrases plus production drum humanization. */
public class DrumAdvancedFixture {
    public static void main(String[] args) {
        org.apache.logging.log4j.core.config.Configurator.setRootLevel(org.apache.logging.log4j.Level.OFF);
        String[] seeds = { "42", "-2147483648", "9223372036854775807", "9007199254740993" };
        String[] rhythms = { "full", "half", "tresillo", "sparse", "single", "one-six", "euclid", "custom" };
        RhythmPattern[] patterns = { RhythmPattern.FULL, RhythmPattern.ALT, RhythmPattern.TRESILLO, RhythmPattern.ONEPER4, RhythmPattern.SINGLE, RhythmPattern.ONESIX, RhythmPattern.EUCLID, RhythmPattern.CUSTOM };
        List<Object> cases = new ArrayList<>();
        for (String seed : seeds) for (int sample = 0; sample < 48; sample++) {
            boolean follow = sample >= 32;
            int hits = new int[] {1, 3, 4, 5, 8, 10, 16, 32}[sample % 8], bars = new int[] {1, 3, 4, 7}[sample % 4];
            DrumPart part = new DrumPart(); part.setOrder(1); part.setOrderOffset(1);
            part.setPatternSeed((int) Long.parseLong(seed)); part.setInstrument(new int[] {36, 38, 42, 46}[sample % 4]);
            part.setPattern(follow ? RhythmPattern.MELODY1 : patterns[sample % 8]); part.setHitsPerPattern(hits);
            part.setPatternShift(sample % 9); part.setPatternFlip(sample % 3 == 1); part.setChordSpan(1 + sample % 4);
            part.setPauseChance(new int[] {0, 35, 100}[sample % 3]); part.setExceptionChance(new int[] {0, 40, 100}[(sample / 3) % 3]);
            part.setVelocityPattern(sample % 3 != 0); part.setVelocityMin(69); part.setVelocityMax(90);
            part.setSwingPercent(new int[] {50, 66, 25}[sample % 3]); part.setChordSpanFill(sample % 4 == 0 ? ChordSpanFill.ODD : ChordSpanFill.ALL);
            part.setFillFlip(sample == 47);
            int pulses = sample % (hits + 1), humanize = new int[] {0, 20, 100}[(sample / 8) % 3];
            List<Integer> grid = new ArrayList<>(), velocities = new ArrayList<>();
            for (int step = 0; step < 32; step++) { grid.add(sample % 8 == 6 ? step < pulses ? 1 : 0 : step % 3 == 0 ? 1 : 0); velocities.add(step % 4 == 0 ? 0 : 50 + step * 2); }
            part.setCustomPattern(grid); boolean custom = sample >= 16;
            if (custom) part.setCustomVelocities(velocities);
            List<int[]> roots = new ArrayList<>(); Map<Integer, List<Integer>> melody = new HashMap<>(); List<Integer> flat = new ArrayList<>(); List<Object> melodyLists = new ArrayList<>();
            for (int bar = 0; bar < bars; bar++) {
                roots.add(new int[] {60}); List<Integer> onsets = new ArrayList<>();
                for (int slot = 0; slot < 16; slot++) onsets.add(slot % 3 == bar % 3 ? 60 + bar : 0);
                melody.put(bar, onsets); flat.addAll(onsets); melodyLists.add(onsets);
            }
            GUIConfig config = new GUIConfig(); config.setScaleMidiVelocityInArrangement(false);
            Section section = new Section("CHORUS1", 1, 100, 100, 100, 100, 100);
            MidiGenerator midi = new MidiGenerator(config); midi.setProgressionDurations(Collections.nCopies(bars, 4.0));
            Map<String, Object> settings = new LinkedHashMap<>();
            settings.put("pitch", part.getInstrument()); settings.put("rhythm", follow ? "melody" : rhythms[sample % 8]);
            settings.put("hitsPerPattern", hits); settings.put("patternShift", part.getPatternShift()); settings.put("patternFlip", part.isPatternFlip());
            settings.put("chordSpan", part.getChordSpan()); settings.put("pauseChance", part.getPauseChance()); settings.put("exceptionChance", part.getExceptionChance());
            settings.put("ghosts", part.isVelocityPattern()); settings.put("humanize", humanize); settings.put("velocityMin", 69); settings.put("velocityMax", 89);
            settings.put("swingPercent", part.getSwingPercent()); settings.put("chordSpanFill", part.getChordSpanFill().toString()); settings.put("fillFlip", part.isFillFlip());
            settings.put("customPattern", grid); settings.put("euclideanPulses", pulses); settings.put("useCustomVelocities", custom); settings.put("customVelocities", velocities);
            DrumPhraseGenerator generator = new DrumPhraseGenerator(config, (sec, ip, vars, group, chances) -> Collections.emptyList());
            Phrase phrase = generator.generate(part, roots, Collections.nCopies(bars, 4.0), Collections.nCopies(bars, "C"),
                flat, melody, false, section, 1, Collections.emptyList(), 0, midi.getTiming(), 0.95).getPhrase();
            if (phrase.size() > 0) midi.swingPhrase(phrase, part.getHitsPerPattern() % 2 == 0 ? part.getSwingPercent() : 50, 1);
            if (humanize > 0) {
                Random rootRandom = new Random(Long.parseLong(seed) + 999);
                JMusicUtilsCustom.humanize(phrase, 0, humanize / 10000.0, 0, new Random(rootRandom.nextLong() + 4001), true);
            }
            Map<String, Object> entry = new LinkedHashMap<>(); entry.put("seed", seed); entry.put("barCount", bars);
            entry.put("settings", settings); entry.put("melody", Collections.singletonMap("pitches", melodyLists)); entry.put("notes", ArpCoreFixture.notes(phrase)); cases.add(entry);
        }
        System.out.println(ArpCoreFixture.json(cases));
    }
}
