package org.vibehistorian.vibecomposer.generation;

import org.junit.Test;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Section;
import java.util.*;
import static org.junit.Assert.*;

public class ContextualComposeRandomizationTest {
    @Test public void rangesVaryAndIncludeAnchorsWithDirectionalRoom() {
        Set<String> ranges = new HashSet<>();
        for (int seed = 0; seed < 64; seed++) {
            int[] range = ContextualMelodyRandomizer.range(seed,0,Collections.emptyList());
            assertTrue(range[0] >= 55 && range[1] <= 88);
            assertTrue(range[1] - range[0] >= 12 && range[1] - range[0] <= 24);
            assertArrayEquals(range,ContextualMelodyRandomizer.range(seed,0,Collections.emptyList()));
            ranges.add(Arrays.toString(range));
            int[] anchored = ContextualMelodyRandomizer.range(seed,-1,Arrays.asList(48,96));
            assertTrue(anchored[0] <= 44 && anchored[1] >= 98);
            assertArrayEquals(new int[] {0,127},ContextualMelodyRandomizer.range(seed,0,Arrays.asList(0,127)));
        }
        assertTrue(ranges.size() >= 20);
    }

    @Test public void targetsFollowMajorAndMinorHarmonyAndRemainSeeded() {
        for (MidiUtils.ScaleMode mode : new MidiUtils.ScaleMode[] {MidiUtils.ScaleMode.IONIAN,MidiUtils.ScaleMode.AEOLIAN}) {
            List<String> names = mode == MidiUtils.ScaleMode.IONIAN ? Arrays.asList("C","Dm7","G7","C") : Arrays.asList("Cm","Dm7b5","G7","Cm");
            HarmonyTimeline harmony = HarmonyTimeline.fromSymbols(names,Arrays.asList(4.0,4.0,4.0,4.0),new HarmonyTimeline.Context("C",0,mode,true));
            Set<List<Integer>> targets = new HashSet<>();
            for (int seed = 0; seed < 32; seed++) {
                int[] range = ContextualMelodyRandomizer.range(seed,-1,Collections.emptyList());
                List<Integer> degrees = ContextualMelodyRandomizer.targets(harmony,mode,range[0],range[1],-1,ContextualMelodyPlanner.Ending.CLOSED,seed);
                assertEquals(degrees,ContextualMelodyRandomizer.targets(harmony,mode,range[0],range[1],-1,ContextualMelodyPlanner.Ending.CLOSED,seed));
                assertEquals(Integer.valueOf(0),degrees.get(3));
                for (int i = 0; i < degrees.size(); i++) { assertTrue(harmony.events().get(i).contains(mode.noteAdjustScale[degrees.get(i)])); }
                targets.add(degrees);
            }
            assertTrue("Targets lack seeded variety",targets.size() >= 4);
        }
    }

    @Test public void selectedRangesAndTargetsProduceBoundedMelodiesAcrossModesAndContours() {
        for (MidiUtils.ScaleMode mode : new MidiUtils.ScaleMode[] {MidiUtils.ScaleMode.IONIAN,MidiUtils.ScaleMode.AEOLIAN}) {
            List<String> names = mode == MidiUtils.ScaleMode.IONIAN ? Arrays.asList("C","F","G7","C") : Arrays.asList("Cm","Fm","G7","Cm");
            List<int[]> chords = new ArrayList<>(); for (String name : names) { chords.add(MidiUtils.mappedChord(name)); }
            for (MelodyUtils.NoteTargetDirection direction : new MelodyUtils.NoteTargetDirection[] {MelodyUtils.NoteTargetDirection.ASC,MelodyUtils.NoteTargetDirection.DESC}) {
                for (int seed = 0; seed < 16; seed++) {
                    GUIConfig config = new GUIConfig(); config.setContextualMelody(true); config.setMelodyUseCustomDurations(false);
                    config.setNoteTargetDirectionChoice(direction);
                    int[] range = ContextualMelodyRandomizer.range(seed,direction.direction,Collections.emptyList());
                    config.setMelodyRangeMin(range[0]); config.setMelodyRangeMax(range[1]);
                    MelodyPart part = new MelodyPart(); part.setOrder(1); part.setOrderOffset(1); part.setPatternSeed(seed);
                    part.setStartNoteChance(100); part.setPauseChance(0); part.setSpeed(50);
                    MelodyGenerationSettings settings = new MelodyGenerationSettings(); settings.setRandomizeTargetNotes(true);
                    Section section = new Section(); section.setMeasures(1);
                    ContextualMelodyGenerator generator = new ContextualMelodyGenerator(config,new MidiTiming(100),0.95,settings);
                    generator.generate(part,chords,chords,Arrays.asList(4.0,4.0,4.0,4.0),section,0,mode,true);
                    List<ContextualMelodyPlanner.PlannedNote> notes = generator.lastResult.notes;
                    for (int i = 0; i < notes.size(); i++) {
                        int pitch = notes.get(i).pitch, prior = notes.get((i + notes.size() - 1) % notes.size()).pitch;
                        assertTrue(pitch >= range[0] && pitch <= range[1]); assertTrue(Math.abs(pitch - prior) <= 9);
                    }
                }
            }
        }
    }

    @Test public void contextualComposePublishesTargetsAndRegenerateKeepsThem() {
        GUIConfig config = new GUIConfig(); config.setContextualMelody(true); config.setMelodyUseCustomDurations(false);
        MelodyPart part = new MelodyPart(); part.setOrder(1); part.setOrderOffset(1); part.setPatternSeed(17);
        part.setStartNoteChance(100); part.setPauseChance(0); part.setSpeed(50);
        part.setChordNoteChoices(Arrays.asList(6,6,6,6));
        List<int[]> chords = new ArrayList<>(); for (String name : Arrays.asList("C","F","G7","C")) { chords.add(MidiUtils.mappedChord(name)); }
        MelodyGenerationSettings settings = new MelodyGenerationSettings(); settings.setRandomizeTargetNotes(true);
        Section section = new Section(); section.setMeasures(1);
        ContextualMelodyGenerator generator = new ContextualMelodyGenerator(config,new MidiTiming(100),0.95,settings);
        generator.generate(part,chords,chords,Arrays.asList(4.0,4.0,4.0,4.0),section,0,MidiUtils.ScaleMode.IONIAN,true);
        List<Integer> targets = new ArrayList<>(part.getChordNoteChoices());
        List<Integer> pitches = new ArrayList<>(); generator.lastResult.notes.forEach(note -> pitches.add(note.pitch));
        assertEquals(targets,settings.getTargetNotes().get(1)); assertNotEquals(Arrays.asList(6,6,6,6),targets);
        settings.setRandomizeTargetNotes(false); settings.setTargetNotes(null);
        generator.generate(part,chords,chords,Arrays.asList(4.0,4.0,4.0,4.0),section,0,MidiUtils.ScaleMode.IONIAN,true);
        assertEquals(targets,part.getChordNoteChoices());
        List<Integer> repeated = new ArrayList<>(); generator.lastResult.notes.forEach(note -> repeated.add(note.pitch));
        assertEquals(pitches,repeated);
        settings.setRandomizeTargetNotes(true); settings.setProtectedTargetOrders(Collections.singleton(1));
        part.setChordNoteChoices(Arrays.asList(0,2,2,4));
        generator.generate(part,chords,chords,Arrays.asList(4.0,4.0,4.0,4.0),section,0,MidiUtils.ScaleMode.IONIAN,true);
        assertEquals(Arrays.asList(0,2,2,4),part.getChordNoteChoices()); assertNull(settings.getTargetNotes());
    }
}
