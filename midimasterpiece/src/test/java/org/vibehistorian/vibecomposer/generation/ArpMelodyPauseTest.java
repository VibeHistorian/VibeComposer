package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Note;
import jm.music.data.Phrase;
import org.junit.Test;
import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Section;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ArpMelodyPauseTest {
    @Test public void melodyPausesRemoveHitsAfterInversionAndPreserveSharedSource() {
        Map<Integer, List<Integer>> melody = new HashMap<>();
        List<Integer> base = new ArrayList<>();
        for (int chord = 0; chord < 3; chord++) {
            List<Integer> grid = new ArrayList<>();
            for (int step = 0; step < 16; step++) grid.add(step % 3 == 0 ? 72 : 0);
            melody.put(chord, grid);
            base.addAll(grid);
        }
        List<Integer> original = new ArrayList<>(base);
        for (boolean flip : new boolean[] { false, true }) {
            List<Double> baseline = generate(0, flip, melody, base);
            List<Double> paused = generate(50, flip, melody, base);
            assertTrue(paused.size() > 0 && paused.size() < baseline.size());
            assertTrue("Pause must only remove existing hits", baseline.containsAll(paused));
            assertEquals(paused, generate(50, flip, melody, base));
            assertTrue("100% pauses must also silence inverted melody rhythm", generate(100, flip, melody, base).isEmpty());
            assertEquals("Shared base melody must not be mutated", original, base);
            for (int chord = 0; chord < 3; chord++) assertEquals(original.subList(chord * 16, (chord + 1) * 16), melody.get(chord));
        }
    }

    private List<Double> generate(int pause, boolean flip, Map<Integer, List<Integer>> melody, List<Integer> base) {
        GUIConfig config = new GUIConfig();
        config.setScaleMidiVelocityInArrangement(false);
        ArpPart part = new ArpPart();
        part.setOrder(1);
        part.setOrderOffset(1);
        part.setPatternSeed(42);
        part.setPattern(RhythmPattern.MELODY1);
        part.setArpPattern(ArpPattern.UP);
        part.setPatternFlip(flip);
        part.setPauseChance(pause);
        part.setExceptionChance(0);
        List<int[]> chords = Arrays.asList(new int[] { 48, 52, 55 }, new int[] { 53, 57, 60 }, new int[] { 55, 59, 62 });
        List<int[]> roots = Arrays.asList(new int[] { 48 }, new int[] { 53 }, new int[] { 55 });
        List<Double> durations = Collections.nCopies(3, 4.0);
        MidiGenerator midi = new MidiGenerator(config);
        midi.setProgressionDurations(durations);
        Section section = new Section("CHORUS1", 1, 100, 100, 100, 100, 100);
        ArpPhraseGenerator generator = new ArpPhraseGenerator(config, (sec, ip, vars, group, chances) -> Collections.emptyList());
        Phrase phrase = generator.generate(part, chords, roots, durations, base, melody, 3,
                section, Collections.emptyList(), 0, midi.getTiming(), 1.0).getPhrase();
        List<Double> starts = new ArrayList<>();
        double time = 0;
        for (Object item : phrase.getNoteList()) {
            Note note = (Note) item;
            if (note.getPitch() >= 0 && note.getDynamic() > 0) starts.add(time + note.getOffset());
            time += note.getRhythmValue();
        }
        return starts;
    }
}
