package org.vibehistorian.vibecomposer.generation;

import jm.music.data.*;
import org.vibehistorian.vibecomposer.*;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Enums.*;
import java.util.*;

/** Complete supported production ArpPhraseGenerator phrases, not a rewritten oracle. */
public class ArpCoreFixture {
    public static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "\"" + value + "\"";
        if (value instanceof Map) {
            StringJoiner join = new StringJoiner(",", "{", "}");
            ((Map<?, ?>) value).forEach((key, item) -> join.add(json(key.toString()) + ":" + json(item)));
            return join.toString();
        }
        if (value instanceof Iterable) {
            StringJoiner join = new StringJoiner(",", "[", "]");
            for (Object item : (Iterable<?>) value) join.add(json(item));
            return join.toString();
        }
        return value.toString();
    }
    public static List<List<Number>> notes(Phrase phrase) {
        List<List<Number>> notes = new ArrayList<>(); double time = 0;
        for (Object item : phrase.getNoteList()) {
            Note note = (Note) item;
            if (note.getPitch() >= 0 && note.getDynamic() > 0) notes.add(Arrays.asList(note.getPitch(), time + note.getOffset(), note.getDuration(), note.getDynamic()));
            time += note.getRhythmValue();
        }
        return notes;
    }
    public static void main(String[] args) {
        org.apache.logging.log4j.core.config.Configurator.setRootLevel(org.apache.logging.log4j.Level.OFF);
        String[] seeds = { "42", "-2147483648", "9223372036854775807", "9007199254740993" };
        String[] directions = { "up", "down", "up-down", "down-up", "frog-up", "frog-down", "random", "custom" };
        ArpPattern[] types = { ArpPattern.UP, ArpPattern.DOWN, ArpPattern.UPDOWN, ArpPattern.DOWNUP, ArpPattern.FROG_U, ArpPattern.FROG_D, ArpPattern.RANDOM, ArpPattern.CUSTOM };
        String[] rhythms = { "full", "half", "tresillo", "sparse", "single", "one-six", "euclid", "custom" };
        RhythmPattern[] patterns = { RhythmPattern.FULL, RhythmPattern.ALT, RhythmPattern.TRESILLO, RhythmPattern.ONEPER4, RhythmPattern.SINGLE, RhythmPattern.ONESIX, RhythmPattern.EUCLID, RhythmPattern.CUSTOM };
        List<Object> cases = new ArrayList<>();
        for (String seed : seeds) for (int sample = 0; sample < 64; sample++) {
            boolean follow = sample >= 56;
            int dir = sample % 8; if (follow && dir == 6) dir = 0;
            int hits = dir == 6 ? 8 : new int[] { 1, 3, 4, 5, 8, 10, 16, 32 }[(sample / 8 + sample) % 8];
            int count = new int[] { 1, 3, 4, 7 }[sample % 4];
            int key = new int[] { 0, 11, 5 }[(sample / 8) % 3];
            String keyName = new String[] { "C", "B", "F" }[(sample / 8) % 3];
            boolean minor = sample % 2 == 1; int octaves = sample % 3 == 0 ? 2 : 1;
            int[] scale = minor ? new int[] { 0, 2, 3, 5, 7, 8, 10 } : new int[] { 0, 2, 4, 5, 7, 9, 11 };
            List<Integer> degrees = new ArrayList<>(); List<int[]> chords = new ArrayList<>(), roots = new ArrayList<>();
            Map<Integer, List<Integer>> melody = new HashMap<>(); List<Integer> flat = new ArrayList<>(); List<Object> melodyLists = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                int degree = new int[] { 1, 6, 4, 5, 7, 2, 3 }[index]; degrees.add(degree);
                boolean major = minor ? degree == 3 || degree == 6 || degree == 7 : degree == 1 || degree == 4 || degree == 5;
                boolean dim = minor ? degree == 2 : degree == 7;
                int root = 48 + (key + scale[degree - 1]) % 12;
                int third = major ? 4 : 3, fifth = dim ? 6 : 7;
                chords.add(octaves == 2 ? new int[] {root, root + third, root + fifth, root + 12, root + 12 + third, root + 12 + fifth} : new int[] {root, root + third, root + fifth});
                roots.add(new int[] {root});
                List<Integer> grid = new ArrayList<>();
                for (int step = 0; step < 16; step++) grid.add(step % 3 == 0 ? 61 + index : 0);
                melody.put(index, grid); flat.addAll(grid); melodyLists.add(grid);
            }
            ArpPart part = new ArpPart(); part.setOrder(1); part.setOrderOffset(1);
            int explicit = sample >= 48 ? -1234567 : 0;
            part.setPatternSeed(explicit != 0 ? explicit : (int) Long.parseLong(seed));
            part.setArpPattern(types[dir]); part.setHitsPerPattern(hits);
            part.setArpPatternRotate(sample % 9); part.setArpPatternCustom(Arrays.asList(-1, 0, 3, 1, 2));
            part.setArpContour(sample % 4 == 2 ? Arrays.asList(-2, 0, 3, 1) : Collections.emptyList());
            part.setArpContourChordMode(sample % 3 == 1);
            part.setPattern(follow ? RhythmPattern.MELODY1 : patterns[(sample / 8) % 8]);
            part.setChordSpan(1 + (sample / 16) % 4); part.setPatternRepeat(1 + sample % 4);
            part.setPatternShift(sample % 9); part.setPatternFlip(sample % 3 == 1);
            part.setPauseChance(follow && sample >= 60 ? 100 : sample % 3 == 2 ? 35 : 0);
            part.setExceptionChance(new int[] {0, 35, 100}[sample % 3]);
            part.setVelocityMin(69); part.setVelocityMax(90);
            part.setStretchEnabled(sample % 5 == 1); part.setChordNotesStretch(2 + sample % 5);
            part.setSwingPercent(new int[] {50, 66, 25}[sample % 3]);
            part.setChordSpanFill(sample % 4 == 0 ? ChordSpanFill.ODD : ChordSpanFill.ALL);
            part.setFillFlip(sample == 63);
            List<Integer> grid = new ArrayList<>(), velocities = new ArrayList<>(); int pulses = sample % (hits + 1);
            for (int step = 0; step < 32; step++) { grid.add((sample / 8) % 8 == 6 ? step < pulses ? 1 : 0 : step % 3 == 0 ? 1 : 0); velocities.add(step % 4 == 0 ? 0 : 50 + step * 2); }
            part.setCustomPattern(grid); boolean customVelocity = sample >= 32;
            if (customVelocity) part.setCustomVelocities(velocities);
            GUIConfig config = new GUIConfig(); config.setScaleMidiVelocityInArrangement(false);
            config.setUseOctaveAdjustments(sample % 3 == 0);
            Section section = new Section("CHORUS1", 1, 100, 100, 100, 100, 100);
            MidiGenerator midi = new MidiGenerator(config); midi.setProgressionDurations(Collections.nCopies(count, 4.0));
            Map<String, Object> settings = new LinkedHashMap<>();
            settings.put("pattern", directions[dir]); settings.put("rate", "eighth"); settings.put("octaves", octaves);
            settings.put("rhythm", follow ? "melody" : rhythms[(sample / 8) % 8]); settings.put("hitsPerPattern", hits);
            settings.put("arpPatternRotate", part.getArpPatternRotate()); settings.put("arpPatternCustom", part.getArpPatternCustom());
            settings.put("arpContour", part.getArpContour()); settings.put("arpContourChordMode", part.isArpContourChordMode());
            settings.put("useOctaveAdjustments", config.isUseOctaveAdjustments());
            settings.put("chordSpan", part.getChordSpan()); settings.put("patternRepeat", part.getPatternRepeat());
            settings.put("patternShift", part.getPatternShift()); settings.put("patternFlip", part.isPatternFlip());
            settings.put("pauseChance", part.getPauseChance()); settings.put("exceptionChance", part.getExceptionChance());
            settings.put("stretchEnabled", part.isStretchEnabled()); settings.put("chordNotesStretch", part.getChordNotesStretch());
            settings.put("swingPercent", part.getSwingPercent()); settings.put("chordSpanFill", part.getChordSpanFill().toString()); settings.put("fillFlip", part.isFillFlip());
            settings.put("customPattern", grid); settings.put("euclideanPulses", pulses); settings.put("useCustomVelocities", customVelocity); settings.put("customVelocities", velocities);
            settings.put("velocityMin", 69); settings.put("velocityMax", 89); settings.put("patternSeed", explicit);
            ArpPhraseGenerator generator = new ArpPhraseGenerator(config, (sec, ip, vars, group, chances) -> Collections.emptyList());
            Phrase phrase = generator.generate(part, chords, roots, Collections.nCopies(count, 4.0), flat, melody, count,
                section, Collections.emptyList(), 0, midi.getTiming(), 1.0).getPhrase();
            if (phrase.size() > 0) midi.swingPhrase(phrase, part.getHitsPerPattern() % 2 == 0 ? part.getSwingPercent() : 50, 1);
            Map<String, Object> entry = new LinkedHashMap<>(); entry.put("seed", seed); entry.put("key", keyName);
            entry.put("scale", minor ? "natural-minor" : "major"); entry.put("progression", degrees); entry.put("settings", settings);
            entry.put("melody", Collections.singletonMap("pitches", melodyLists)); entry.put("notes", notes(phrase)); cases.add(entry);
        }
        System.out.println(json(cases));
    }
}
