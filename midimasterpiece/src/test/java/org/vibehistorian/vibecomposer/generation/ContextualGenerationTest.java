package org.vibehistorian.vibecomposer.generation;

import org.junit.Test;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.MidiUtils;

import javax.xml.bind.JAXBContext;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class ContextualGenerationTest {
    private static final int[] MAJOR = { 0, 2, 4, 5, 7, 9, 11 };

    private HarmonyTimeline harmony(String... names) {
        List<int[]> chords = Arrays.stream(names).map(name -> ChordSymbol.parse(name).pitches(false)).collect(Collectors.toList());
        return HarmonyTimeline.of(chords, chords, Arrays.stream(names).map(name -> 2.0).collect(Collectors.toList()));
    }

    private ContextualMelodyPlanner.Options options(ContextualMelodyPlanner.Ending ending) {
        return new ContextualMelodyPlanner.Options(60, 84, 9, 32, 50, ending);
    }

    private ContextualMelodyPlanner.Slot slot(long start, long duration, int pitch) {
        return new ContextualMelodyPlanner.Slot(start, duration, false, pitch, 1);
    }

    @Test public void immutableHarmonyUsesFollowingChordAtExactBoundary() {
        List<int[]> chords = new ArrayList<>(Arrays.asList(new int[] { 60,64,67 }, new int[] { 62,65,69 }));
        HarmonyTimeline timeline = HarmonyTimeline.of(chords, chords, Arrays.asList(1.0, 1.0));
        chords.get(0)[0] = 70;
        assertTrue(timeline.at(0).contains(60));
        assertEquals(2, timeline.at(960).root);
        assertEquals(1, timeline.overlapping(0, 960).size());
        assertEquals(2, timeline.overlapping(480, 1440).size());
        timeline.at(0).pitches()[0] = 70;
        assertTrue(timeline.at(0).contains(60));
    }

    @Test public void inversionDoesNotChangeHarmonicRoot() {
        HarmonyTimeline timeline = HarmonyTimeline.of(Collections.singletonList(new int[] {64,67,72}),
                Collections.singletonList(new int[] {60}), Collections.singletonList(1.0));
        assertEquals(0, timeline.at(0).root);
        assertEquals("C", timeline.at(0).symbol);
    }

    @Test public void motionMeasuresOctavesAndZeroNetReversals() {
        assertEquals(12, MelodicMotion.largestLeap(new int[] {60,72}));
        assertEquals(1, MelodicMotion.directionChanges(new int[] {0,2,1,0}));
        assertEquals(1, MelodicMotion.displacementDirectionChanges(new int[] {-2,-1,0,3,4}));
    }

    @Test public void weakPassingToneAndNeighborPreserveTheirRole() {
        HarmonyTimeline timeline = harmony("C");
        List<ContextualMelodyPlanner.Slot> rhythm = Arrays.asList(slot(0,480,60), slot(480,480,62), slot(960,960,64));
        ContextualMelodyPlanner.Result passing = ContextualMelodyPlanner.generate(timeline, MAJOR, rhythm,
                options(ContextualMelodyPlanner.Ending.OPEN), 1);
        assertEquals(ContextualMelodyPlanner.Role.PASSING, passing.notes.get(1).role);
        rhythm = Arrays.asList(slot(0,480,60), slot(480,480,62), slot(960,960,60));
        ContextualMelodyPlanner.Result neighbor = ContextualMelodyPlanner.generate(timeline, MAJOR, rhythm,
                options(ContextualMelodyPlanner.Ending.OPEN), 1);
        assertEquals(ContextualMelodyPlanner.Role.NEIGHBOR, neighbor.notes.get(1).role);
        assertEquals(ContextualMelodyPlanner.Role.CHORD, neighbor.notes.get(2).role);
    }

    @Test(expected = IllegalArgumentException.class) public void longAccentedNonChordLockReportsConflict() {
        ContextualMelodyPlanner.generate(harmony("C"), MAJOR, Collections.singletonList(slot(0,1920,65)),
                options(ContextualMelodyPlanner.Ending.OPEN), 1);
    }

    @Test(expected = IllegalArgumentException.class) public void noteSpanningIncompatibleHarmonyIsNotOnlyCheckedAtOnset() {
        ContextualMelodyPlanner.generate(harmony("C", "Dm"), MAJOR, Collections.singletonList(slot(0,3840,64)),
                options(ContextualMelodyPlanner.Ending.OPEN), 1);
    }

    @Test public void finalCadenceChoosesTonicWithoutQuotaMutation() {
        HarmonyTimeline timeline = harmony("G7", "C");
        List<ContextualMelodyPlanner.Slot> rhythm = Arrays.asList(slot(0,1920,71),
                new ContextualMelodyPlanner.Slot(1920,1920,false,null,2));
        ContextualMelodyPlanner.Result result = ContextualMelodyPlanner.generate(timeline, MAJOR, rhythm,
                options(ContextualMelodyPlanner.Ending.CLOSED), 10);
        assertEquals(72, result.notes.get(1).pitch);
    }

    @Test public void chromaticDominantToneSurvivesInMinor() {
        HarmonyTimeline timeline = harmony("E7", "Am");
        List<ContextualMelodyPlanner.Slot> rhythm = Arrays.asList(slot(0,1920,68), slot(1920,1920,69));
        ContextualMelodyPlanner.Result result = ContextualMelodyPlanner.generate(timeline,
                new int[] {0,2,4,5,7,9,10}, rhythm, options(ContextualMelodyPlanner.Ending.OPEN), 10);
        assertEquals(68, result.notes.get(0).pitch);
    }

    @Test public void generatedRhythmAndMelodyAreDeterministicAndWithinBounds() {
        HarmonyTimeline timeline = harmony("C", "Am", "F", "G7");
        List<ContextualMelodyPlanner.Slot> rhythm = ContextualMelodyPlanner.rhythm(timeline, 75, 15,
                Arrays.asList(1,2,1,3), 42, ContextualMelodyPlanner.Ending.LOOP);
        ContextualMelodyPlanner.Result first = ContextualMelodyPlanner.generate(timeline, MAJOR, rhythm,
                options(ContextualMelodyPlanner.Ending.LOOP), 42);
        ContextualMelodyPlanner.Result second = ContextualMelodyPlanner.generate(timeline, MAJOR, rhythm,
                options(ContextualMelodyPlanner.Ending.LOOP), 42);
        assertEquals(first.score, second.score, 0);
        assertEquals(first.candidatesExamined, second.candidatesExamined);
        assertEquals(first.notes.size(), second.notes.size());
        for (int i = 0; i < first.notes.size(); i++) {
            assertEquals(first.notes.get(i).pitch, second.notes.get(i).pitch);
            int pitch = first.notes.get(i).pitch;
            assertTrue(pitch == -1 || pitch >= 60 && pitch <= 84);
        }
        assertTrue(first.candidatesExamined < rhythm.size() * 32 * 128);
        assertEquals(timeline.length(), rhythm.stream().mapToLong(note -> note.duration).sum());
    }

    @Test(expected = IllegalArgumentException.class) public void impossibleRangeDoesNotSilentlyRelaxLocks() {
        ContextualMelodyPlanner.generate(harmony("C"), MAJOR, Collections.singletonList(slot(0,1920,60)),
                new ContextualMelodyPlanner.Options(70,72,4,8,0,ContextualMelodyPlanner.Ending.OPEN), 1);
    }

    @Test public void firstAndLastAndInteriorLocksSurviveMaximumColorAndSimilarity() {
        GUIConfig config = new GUIConfig();
        config.setProgressionStrategy(HarmonicProgressionPlanner.Strategy.FUNCTIONAL);
        config.setSpiceChance(100); config.setLongProgressionSimilarity(100);
        Map<Integer,String> locks = new HashMap<>(); locks.put(3,"F#m.1");
        List<String> names = HarmonicProgressionPlanner.generate(config, 123, 8, "C", "G7", locks);
        assertEquals("C", names.get(0));
        assertEquals("F#m.1", names.get(3));
        assertEquals("G7", names.get(7));
        assertEquals(names, HarmonicProgressionPlanner.generate(config, 123, 8, "C", "G7", locks));
    }

    @Test public void fifthStrategyPreservesDiatonicDiminishedLink() {
        GUIConfig config = new GUIConfig(); config.setSpiceChance(0);
        config.setProgressionStrategy(HarmonicProgressionPlanner.Strategy.FIFTHS);
        assertEquals(Arrays.asList("C","F","Bdim","Em","Am","Dm","G","C"),
                HarmonicProgressionPlanner.generate(config, 12, 8, null, null, Collections.emptyMap()));
    }

    @Test public void functionalStrategyPlansPredominantDominantTonic() {
        GUIConfig config = new GUIConfig(); config.setSpiceChance(0);
        config.setProgressionStrategy(HarmonicProgressionPlanner.Strategy.FUNCTIONAL);
        for (long seed = 0; seed < 20; seed++) {
            List<String> names = HarmonicProgressionPlanner.generate(config, seed, 4, null, null, Collections.emptyMap());
            assertEquals("C", names.get(0));
            assertTrue(names.get(1).equals("Dm") || names.get(1).equals("F"));
            assertEquals("G", names.get(2)); assertEquals("C", names.get(3));
        }
    }

    @Test public void colorPreservesAccidentalQualityAndInversionAndDominantIdentity() {
        GUIConfig config = new GUIConfig(); config.setSpiceChance(100); config.setSpiceForceScale(false);
        for (long seed = 0; seed < 20; seed++) {
            ChordSymbol colored = ChordSymbol.parse(HarmonicProgressionPlanner.color("F#m.1",config,seed));
            assertEquals(6,colored.root); assertEquals("F#",colored.rootName); assertEquals(Integer.valueOf(1),colored.inversion);
            assertTrue(Arrays.stream(colored.pitches(false)).anyMatch(pitch -> pitch % 12 == 9));
            assertEquals("G7",HarmonicProgressionPlanner.color("G7",config,seed));
        }
        config.setSpiceChance(0);
        assertEquals(Arrays.asList("F#m.1","Bb7","CGCE"), MidiUtils.respiceChords("F#m.1,Bb7,CGCE",config,12));
        assertEquals(10, ChordSymbol.parse("Bb7").root);
    }

    @Test public void minorNinthHasMinorThirdAndSeventh() {
        int[] pitches = ChordSymbol.parse("Cm9").pitches(false);
        assertArrayEquals(new int[] {60,63,67,70,74},pitches);
        assertArrayEquals(pitches,MidiUtils.mappedChord("Cm9"));
        assertArrayEquals(MidiUtils.mappedChord("A#7"),MidiUtils.mappedChord("Bb7"));
    }

    @Test public void localRerollChangesOnlyRequestedChordAndIsDeterministic() {
        GUIConfig config = new GUIConfig(); config.setSpiceChance(100);
        List<String> source = Arrays.asList("C.1","Am","F","G7");
        List<String> next = HarmonicProgressionPlanner.reroll(source,1,config,123);
        assertEquals(source.get(0),next.get(0)); assertEquals(source.get(2),next.get(2)); assertEquals(source.get(3),next.get(3));
        assertNotEquals(9,ChordSymbol.parse(next.get(1)).root);
        assertEquals(next,HarmonicProgressionPlanner.reroll(source,1,config,123));
        assertEquals("Am",source.get(1));
    }

    @Test public void facadeForwardsPersistedChordLocksToBothProgressionPaths() {
        GUIConfig config = new GUIConfig(); config.setProgressionStrategy(HarmonicProgressionPlanner.Strategy.FUNCTIONAL);
        config.setCustomChords("C,F#m.1,F,G7"); config.setLockedChordIndices(Collections.singletonList(1));
        List<String> names = ChordProgressionGenerator.generateChordProgressionList(config,123,4,null,null);
        ChordProgressionGenerator.MappedProgression mapped = ChordProgressionGenerator.generateMappedProgression(
                config,123,4,null,null,Collections.emptyList(),4,1,0.00001);
        assertEquals("F#m.1",names.get(1)); assertEquals(names,mapped.chordNames);
    }

    @Test public void oldConfigDefaultsToCompatibilityAndNewSettingsRoundTrip() throws Exception {
        JAXBContext context = JAXBContext.newInstance(GUIConfig.class);
        GUIConfig old = (GUIConfig) context.createUnmarshaller().unmarshal(new StringReader("<GUIConfig/>"));
        assertFalse(old.isContextualMelody());
        assertEquals(HarmonicProgressionPlanner.Strategy.CURRENT, old.getProgressionStrategy());
        old.setContextualMelody(true); old.setProgressionStrategy(HarmonicProgressionPlanner.Strategy.FIFTHS);
        old.setMelodyEnding(ContextualMelodyPlanner.Ending.CLOSED); old.setMelodyRangeMin(55); old.setMelodyRangeMax(79);
        old.setLockedChordIndices(Arrays.asList(0,2));
        StringWriter writer = new StringWriter(); context.createMarshaller().marshal(old,writer);
        GUIConfig restored = (GUIConfig) context.createUnmarshaller().unmarshal(new StringReader(writer.toString()));
        assertTrue(restored.isContextualMelody());
        assertEquals(HarmonicProgressionPlanner.Strategy.FIFTHS, restored.getProgressionStrategy());
        assertEquals(ContextualMelodyPlanner.Ending.CLOSED, restored.getMelodyEnding());
        assertEquals(55,restored.getMelodyRangeMin()); assertEquals(79,restored.getMelodyRangeMax());
        assertEquals(Arrays.asList(0,2),restored.getLockedChordIndices());
    }

    @Test public void contextualPipelineRendersReferenceArrangementWithFinalizedHarmony() throws Exception {
        GUIConfig config = (GUIConfig) JAXBContext.newInstance(GUIConfig.class).createUnmarshaller()
                .unmarshal(new File("GENERATOR_REFACTOR_TEST_GUICONFIG.xml"));
        config.setContextualMelody(true);
        config.setProgressionStrategy(HarmonicProgressionPlanner.Strategy.FUNCTIONAL);
        config.setMelodyUseCustomDurations(false);
        Path output = Files.createTempFile("vibe-contextual-", ".mid");
        Path temporarySequence = new File(org.vibehistorian.vibecomposer.Constants.TEMPORARY_SEQUENCE_MIDI_NAME).toPath();
        byte[] previous = Files.exists(temporarySequence) ? Files.readAllBytes(temporarySequence) : null;
        PrintStream stdout = System.out, stderr = System.err;
        try {
            MidiGenerator generator = new MidiGenerator(config);
            generator.generateMasterpiece((int) config.getRandomSeed(), output.toString());
            assertTrue(Files.size(output) > 100);
            assertNotNull(generator.getFinalizedSectionHarmony());
            assertTrue(generator.getFinalizedSectionHarmony().length() > 0);
        } finally {
            System.setOut(stdout); System.setErr(stderr);
            Files.deleteIfExists(output);
            if (previous == null) { Files.deleteIfExists(temporarySequence); }
            else { Files.write(temporarySequence,previous); }
        }
    }

    @Test public void minorAndCoDesignRenderThroughTheCompletePipeline() throws Exception {
        for (boolean coDesign : new boolean[] {false,true}) {
            GUIConfig config = (GUIConfig) JAXBContext.newInstance(GUIConfig.class).createUnmarshaller()
                    .unmarshal(new File("GENERATOR_REFACTOR_TEST_GUICONFIG.xml"));
            config.setContextualMelody(true); config.setMelodyLegacyMode(false);
            config.setProgressionStrategy(HarmonicProgressionPlanner.Strategy.FUNCTIONAL); config.setScaleMode(MidiUtils.ScaleMode.AEOLIAN);
            config.setCoDesignHarmony(coDesign); config.setCustomChordsEnabled(false); config.setMelodyUseCustomDurations(false);
            config.setFirstChord("Cm"); config.setLastChord("Cm"); config.setSpiceChance(0);
            config.getArrangement().setOverridden(false); config.getArrangement().setPreviewChorus(true);
            Path output = Files.createTempFile("vibe-minor-", ".mid");
            Path temporary = new File(org.vibehistorian.vibecomposer.Constants.TEMPORARY_SEQUENCE_MIDI_NAME).toPath();
            byte[] previous = Files.exists(temporary) ? Files.readAllBytes(temporary) : null;
            PrintStream stdout = System.out, stderr = System.err;
            try {
                MidiGenerator generator = new MidiGenerator(config); generator.generateMasterpiece((int) config.getRandomSeed(),output.toString());
                assertTrue(Files.size(output) > 100); assertFalse(generator.getSectionHarmonySnapshots().isEmpty());
                boolean dominant = false;
                for (HarmonyTimeline.Event event : generator.getFinalizedSectionHarmony().events()) {
                    if (event.root == 7 && event.dominantSeventh()) { assertTrue(event.contains(71)); dominant = true; }
                }
                if (!coDesign) { assertTrue("Functional minor must retain its leading tone",dominant); }
            } finally {
                System.setOut(stdout); System.setErr(stderr); Files.deleteIfExists(output);
                if (previous == null) { Files.deleteIfExists(temporary); } else { Files.write(temporary,previous); }
            }
        }
    }

    @Test public void attachedMelodyKeepsItsLeadingToneThroughMinorRendering() throws Exception {
        GUIConfig config = (GUIConfig) JAXBContext.newInstance(GUIConfig.class).createUnmarshaller()
                .unmarshal(new File("GENERATOR_REFACTOR_TEST_GUICONFIG.xml"));
        config.setContextualMelody(true); config.setMelodyLegacyMode(false); config.setScaleMode(MidiUtils.ScaleMode.AEOLIAN);
        config.setCustomChordsEnabled(false); config.setTranspose(0);
        config.getArrangement().setOverridden(false); config.getArrangement().setPreviewChorus(true);
        jm.music.data.Phrase authored = new jm.music.data.Phrase(); jm.music.data.Note note = new jm.music.data.Note(71,4,87);
        note.setDuration(2); authored.addNote(note);
        config.setMelodyNotes(new org.vibehistorian.vibecomposer.Helpers.PhraseNotes(authored));
        config.getMelodyParts().forEach(part -> { part.setTranspose(0); part.setOffset(0); part.setFeedbackCount(0); part.setNoteLengthMultiplier(150); });
        Path output = Files.createTempFile("vibe-fixed-", ".mid");
        Path temporary = new File(org.vibehistorian.vibecomposer.Constants.TEMPORARY_SEQUENCE_MIDI_NAME).toPath();
        byte[] previous = Files.exists(temporary) ? Files.readAllBytes(temporary) : null;
        PrintStream stdout = System.out, stderr = System.err;
        try {
            MidiGenerator generator = new MidiGenerator(config); generator.generateMasterpiece((int) config.getRandomSeed(),output.toString());
            assertTrue(Files.size(output) > 100);
            jm.music.data.Phrase rendered = config.getActualArrangement().getSections().get(0).getMelodies().get(0);
            assertTrue(rendered.size() > 0);
            for (Object value : rendered.getNoteList()) {
                jm.music.data.Note actual = (jm.music.data.Note) value;
                if (actual.getPitch() >= 0) { assertEquals(71,actual.getPitch()); assertEquals(2,actual.getDuration(),0); assertEquals(4,actual.getRhythmValue(),0); }
            }
            assertEquals(71,config.getMelodyNotes().get(0).getPitch()); assertEquals(2,config.getMelodyNotes().get(0).getDuration(),0);
        } finally {
            System.setOut(stdout); System.setErr(stderr); Files.deleteIfExists(output);
            if (previous == null) { Files.deleteIfExists(temporary); } else { Files.write(temporary,previous); }
        }
    }

    @Test public void absentCustomRhythmFallsBackToTheSameGeneratedMelodyAsUnchecked() {
        GUIConfig config = new GUIConfig(); config.setContextualMelody(true); config.setMelodyUseCustomDurations(false);
        org.vibehistorian.vibecomposer.Parts.MelodyPart part = new org.vibehistorian.vibecomposer.Parts.MelodyPart();
        part.setOrderOffset(1); part.setPatternSeed(101); part.setSplitChance(100); part.setStartNoteChance(100);
        org.vibehistorian.vibecomposer.Section section = new org.vibehistorian.vibecomposer.Section(); section.setMeasures(1);
        List<int[]> chords = Arrays.asList(MidiUtils.mappedChord("C"),MidiUtils.mappedChord("G7"),MidiUtils.mappedChord("C"));
        List<Double> durations = Arrays.asList(2.0,2.0,2.0);
        java.util.Map<Integer,List<jm.music.data.Note>> expected = new ContextualMelodyGenerator(config,new MidiTiming(100),0.95)
                .generate(part,chords,chords,durations,section,0,MidiUtils.ScaleMode.IONIAN);
        config.setMelodyUseCustomDurations(true);
        for (org.vibehistorian.vibecomposer.Helpers.PhraseNotes absent : Arrays.asList(null,new org.vibehistorian.vibecomposer.Helpers.PhraseNotes())) {
            part.setCustomDurationNotes(absent);
            java.util.Map<Integer,List<jm.music.data.Note>> actual = new ContextualMelodyGenerator(config,new MidiTiming(100),0.95)
                    .generate(part,chords,chords,durations,section,0,MidiUtils.ScaleMode.IONIAN);
            assertEquals(expected.keySet(),actual.keySet());
            for (int event : expected.keySet()) {
                assertEquals(expected.get(event).size(),actual.get(event).size());
                for (int i = 0; i < expected.get(event).size(); i++) {
                    jm.music.data.Note left = expected.get(event).get(i), right = actual.get(event).get(i);
                    assertEquals(left.getPitch(),right.getPitch()); assertEquals(left.getRhythmValue(),right.getRhythmValue(),0);
                    assertEquals(left.getDuration(),right.getDuration(),0); assertEquals(left.getDynamic(),right.getDynamic());
                }
            }
        }
        assertTrue(config.isMelodyUseCustomDurations());
    }

    @Test public void customRhythmKeepsItsDurationsAndRestUsingActiveSectionLength() {
        GUIConfig config = new GUIConfig(); config.setContextualMelody(true); config.setMelodyUseCustomDurations(true);
        org.vibehistorian.vibecomposer.Parts.MelodyPart part = new org.vibehistorian.vibecomposer.Parts.MelodyPart();
        part.setCustomDurationNotes(org.vibehistorian.vibecomposer.Helpers.PhraseNotes.fromPN(Arrays.asList(
                new org.vibehistorian.vibecomposer.Helpers.PhraseNote(0,80,0.5,0.5,0),
                new org.vibehistorian.vibecomposer.Helpers.PhraseNote(0,0,0.5,0.5,0),
                new org.vibehistorian.vibecomposer.Helpers.PhraseNote(0,80,1,1,0))));
        org.vibehistorian.vibecomposer.Section section = new org.vibehistorian.vibecomposer.Section(); section.setMeasures(1);
        MidiTiming timing = new MidiTiming(100);
        ContextualMelodyGenerator generator = new ContextualMelodyGenerator(config,timing,0.95);
        List<int[]> chords = Collections.singletonList(MidiUtils.mappedChord("C"));
        List<jm.music.data.Note> notes = generator.generate(part,chords,chords,Collections.singletonList(2.0),
                section,0,MidiUtils.ScaleMode.IONIAN).get(0);
        assertEquals(3,notes.size());
        assertEquals(0.5,notes.get(0).getRhythmValue(),0);
        assertEquals(0.5,notes.get(1).getRhythmValue(),0);
        assertTrue(notes.get(1).getPitch() < 0);
        assertEquals(1,notes.get(2).getRhythmValue(),0);
        assertEquals(1920,generator.lastHarmony.length());
        assertEquals(0.5,part.getCustomDurationNotes().get(1).getDuration(),0);
        assertEquals(1,part.getCustomDurationNotes().get(2).getDuration(),0);
    }
}
