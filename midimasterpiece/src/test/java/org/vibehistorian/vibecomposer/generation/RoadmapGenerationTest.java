package org.vibehistorian.vibecomposer.generation;

import org.junit.Test;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Enums.BlockType;
import java.util.*;
import static org.junit.Assert.*;

public class RoadmapGenerationTest {
    private static final int[] MAJOR = {0,2,4,5,7,9,11};
    private HarmonyTimeline harmony(String... names) {
        return HarmonyTimeline.fromSymbols(Arrays.asList(names),Collections.nCopies(names.length,1.0),
                new HarmonyTimeline.Context("C",0,MidiUtils.ScaleMode.IONIAN,true));
    }
    private ContextualMelodyPlanner.Slot slot(long start,long duration,int pitch) {
        return new ContextualMelodyPlanner.Slot(start,duration,false,pitch,1);
    }
    private ContextualMelodyPlanner.Options options() {
        return new ContextualMelodyPlanner.Options(48,84,12,32,50,ContextualMelodyPlanner.Ending.OPEN);
    }

    @Test public void revisionsRetainEventIdentitySpellingAndContext() {
        HarmonyTimeline original = harmony("C","Bb7","Am","G7","C");
        HarmonyTimeline changed = original.edit(original.events().get(1).id,"E7",1);
        assertEquals("Bb",original.events().get(1).spelledRoot);
        assertNotEquals(original.revision,changed.revision);
        assertEquals(original.events().get(1).id,changed.events().get(1).id);
        assertEquals(HarmonyTimeline.Function.APPLIED_DOMINANT,changed.events().get(1).function);
        assertEquals(HarmonyTimeline.Function.DOMINANT,changed.events().get(3).function);
        assertTrue(changed.invalidatedEvents(original).contains(original.events().get(2).id));
        assertFalse(changed.invalidatedEvents(original).contains(original.events().get(4).id));
        assertEquals("Bb7",original.events().get(1).symbol);
    }

    @Test public void durationEditInvalidatesShiftedEvents() {
        HarmonyTimeline original = harmony("C","F","G7","C");
        HarmonyTimeline changed = original.edit(original.events().get(1).id,"F",2);
        assertEquals(4800,changed.length());
        assertTrue(changed.invalidatedEvents(original).contains(original.events().get(3).id));
    }

    @Test public void phraseRolesAndRhythmDoNotResetAtEveryChord() {
        HarmonyTimeline timeline = HarmonyTimeline.fromSymbols(Arrays.asList("C","F","G7","C"),Arrays.asList(3.0,3.0,3.0,3.0),
                new HarmonyTimeline.Context("C",0,MidiUtils.ScaleMode.IONIAN,true));
        MelodyPhrasePlan plan = MelodyPhrasePlan.create(timeline,MelodyPhrasePlan.Form.QUESTION_ANSWER,3,2,Arrays.asList(1,1),ContextualMelodyPlanner.Ending.OPEN);
        assertEquals(2,plan.phrases.size());
        assertEquals(MelodyPhrasePlan.Purpose.QUESTION,plan.phrases.get(0).purpose);
        assertEquals(MelodyPhrasePlan.Purpose.ANSWER,plan.phrases.get(1).purpose);
        assertEquals(12 * 960,plan.rhythm(50,0,3,0).stream().mapToLong(note -> note.duration).sum());
        assertEquals(0,plan.phrases.get(0).start);
        assertEquals(5760,plan.phrases.get(1).start);
    }

    @Test public void chordyCatalogueUsesActualChordMembersStartingOnThird() {
        int[] notes = ContextualBlockCatalogue.realize(BlockType.CHORDY,new Integer[] {0,2,4},64,harmony("C").at(0),MAJOR,
                ContextualBlockCatalogue.Transform.DIATONIC);
        assertArrayEquals(new int[] {64,67,72},notes);
        assertFalse(Arrays.stream(notes).anyMatch(pitch -> pitch % 12 == 11));
        int[] inverted = ContextualBlockCatalogue.realize(BlockType.CHORDY,new Integer[] {0,2,4},64,harmony("C").at(0),MAJOR,
                ContextualBlockCatalogue.Transform.INVERT);
        assertArrayEquals(new int[] {64,60,55},inverted);
    }

    @Test public void anticipationHasAnExplicitNextChordRepetition() {
        ContextualMelodyPlanner.Result result = ContextualMelodyPlanner.generate(harmony("C","Dm"),MAJOR,
                Arrays.asList(slot(0,480,60),slot(480,480,65),slot(960,960,65)),options(),1);
        assertEquals(ContextualMelodyPlanner.Role.ANTICIPATION,result.notes.get(1).role);
    }

    @Test public void suspensionIsPreparedAcrossChangeAndResolvesDown() {
        ContextualMelodyPlanner.Result result = ContextualMelodyPlanner.generate(harmony("C","Dm"),MAJOR,
                Arrays.asList(slot(0,1440,64),slot(1440,480,62)),options(),1);
        assertEquals(ContextualMelodyPlanner.Role.SUSPENSION,result.notes.get(0).role);
        assertEquals(62,result.notes.get(1).pitch);
    }

    @Test(expected = IllegalArgumentException.class) public void suspensionCannotResolveUp() {
        ContextualMelodyPlanner.generate(harmony("C","Dm"),MAJOR,Arrays.asList(slot(0,1440,64),slot(1440,480,65)),options(),1);
    }

    @Test public void appoggiaturaAndChromaticApproachResolveByStep() {
        List<ContextualMelodyPlanner.Slot> app = Arrays.asList(slot(0,480,60),
                new ContextualMelodyPlanner.Slot(480,480,false,65,1,ContextualMelodyPlanner.Intent.APPOGGIATURA),slot(960,960,64));
        HarmonyTimeline timeline = HarmonyTimeline.fromSymbols(Collections.singletonList("C"),Collections.singletonList(2.0),
                new HarmonyTimeline.Context("C",0,MidiUtils.ScaleMode.IONIAN,true));
        assertEquals(ContextualMelodyPlanner.Role.APPOGGIATURA,ContextualMelodyPlanner.generate(timeline,MAJOR,app,options(),1).notes.get(1).role);
        ContextualMelodyPlanner.Options options = options(); options.chromaticApproaches = true;
        assertEquals(ContextualMelodyPlanner.Role.CHROMATIC_APPROACH,ContextualMelodyPlanner.generate(timeline,MAJOR,
                Arrays.asList(slot(0,480,64),slot(480,480,63),slot(960,960,64)),options,1).notes.get(1).role);
    }

    @Test public void localRerollKeepsUnrelatedNotesAndTiming() {
        HarmonyTimeline timeline = HarmonyTimeline.fromSymbols(Collections.singletonList("C"),Collections.singletonList(4.0),
                new HarmonyTimeline.Context("C",0,MidiUtils.ScaleMode.IONIAN,true));
        ContextualMelodyPlanner.Result previous = ContextualMelodyPlanner.generate(timeline,MAJOR,
                Arrays.asList(slot(0,1920,60),slot(1920,1920,64)),options(),1);
        List<ContextualMelodyPlanner.Slot> proposal = Arrays.asList(new ContextualMelodyPlanner.Slot(0,960,false,null,1),
                new ContextualMelodyPlanner.Slot(960,960,false,null,1),new ContextualMelodyPlanner.Slot(1920,1920,false,null,1));
        List<ContextualMelodyPlanner.Slot> constrained = MelodyConstraints.rerollLocks(proposal,previous,timeline,"2,4");
        assertEquals(Integer.valueOf(60),constrained.get(0).lockedPitch);
        assertEquals(1920,constrained.get(0).duration);
        assertEquals(2,constrained.size());
        List<ContextualMelodyPlanner.Slot> locked = MelodyConstraints.apply(proposal,"0=60,2=67",false);
        assertEquals(Integer.valueOf(67),locked.get(2).lockedPitch);
    }

    @Test public void voicingsKeepAllTonesRegisterAndBassLocks() {
        HarmonyTimeline timeline = harmony("C","G7","C");
        Map<String,Integer> bass = new HashMap<>(); bass.put(timeline.events().get(1).id,7);
        List<int[]> voices = HarmonyVoicingPlanner.voice(timeline,48,84,bass,Collections.emptyMap());
        assertEquals(7,voices.get(1)[0] % 12);
        for (int i = 0; i < voices.size(); i++) {
            Set<Integer> classes = new TreeSet<>();
            for (int note : voices.get(i)) { assertTrue(note >= 48 && note <= 84); classes.add(note % 12); }
            assertEquals(timeline.events().get(i).pitchClasses(),classes);
        }
        assertArrayEquals(new int[] {71,77},HarmonyVoicingPlanner.retainGuideTones(new int[] {67,71},new int[] {67,71,74,77},7));
    }

    @Test public void minorFunctionalCadenceRetainsRaisedLeadingTone() {
        GUIConfig config = new GUIConfig(); config.setScaleMode(MidiUtils.ScaleMode.AEOLIAN);
        config.setProgressionStrategy(HarmonicProgressionPlanner.Strategy.FUNCTIONAL); config.setSpiceChance(0);
        boolean dominant = false;
        for (long seed = 0; seed < 20; seed++) {
            List<String> names = HarmonicProgressionPlanner.generate(config,seed,4,null,null,Collections.emptyMap());
            assertTrue(ChordSymbol.parse(names.get(3)).quality.startsWith("m"));
            if (names.get(2).equals("G7")) {
                dominant = true; assertTrue(Arrays.stream(ChordSymbol.parse(names.get(2)).pitches(false)).anyMatch(pitch -> pitch % 12 == 11));
            }
        }
        assertTrue("Minor functional phrases must offer a raised-leading-tone dominant",dominant);
        assertArrayEquals(new int[] {62,65,68,72},MidiUtils.mappedChord("Dm7b5"));
    }

    @Test public void typedEditsRespectDestinationLocksAndImportantMelody() {
        HarmonyTimeline source = harmony("C","G7","C");
        List<HarmonyOperations.Alternative> substitute = HarmonyOperations.alternatives(source,1,HarmonyOperations.Operation.TRITONE_SUBSTITUTION,
                true,Collections.emptySet(),Collections.emptyMap());
        assertEquals(1,substitute.size()); assertEquals(1,ChordSymbol.parse(substitute.get(0).symbol).root);
        assertEquals("C",substitute.get(0).harmony.events().get(2).symbol);
        assertTrue(HarmonyOperations.alternatives(source,1,HarmonyOperations.Operation.TRITONE_SUBSTITUTION,
                false,Collections.emptySet(),Collections.emptyMap()).isEmpty());
        assertTrue(HarmonyOperations.alternatives(source,1,HarmonyOperations.Operation.APPLIED_DOMINANT,
                false,Collections.singleton(source.events().get(1).id),Collections.emptyMap()).isEmpty());
        HarmonyTimeline applied = harmony("C","Em","Am");
        assertEquals("E7",HarmonyOperations.alternatives(applied,1,HarmonyOperations.Operation.APPLIED_DOMINANT,false,
                Collections.emptySet(),Collections.emptyMap()).get(0).symbol);
    }

    @Test public void fixedMelodyHarmonizationWeightsDurationAndDoesNotChangeMelody() {
        GUIConfig config = new GUIConfig(); config.setProgressionStrategy(HarmonicProgressionPlanner.Strategy.FUNCTIONAL); config.setSpiceForceScale(false);
        List<HarmonyHarmonizer.MelodyNote> melody = Arrays.asList(new HarmonyHarmonizer.MelodyNote(0,960,68,2),
                new HarmonyHarmonizer.MelodyNote(960,960,69,2));
        HarmonyTimeline result = HarmonyHarmonizer.harmonize(config,melody,Arrays.asList(1.0,1.0),Collections.singletonMap(1,"Am"));
        assertEquals("E7",result.events().get(0).symbol); assertEquals("Am",result.events().get(1).symbol);
        assertEquals(68,melody.get(0).pitch); assertEquals(960,melody.get(0).duration);
    }

    @Test public void independentPropertyLocksAndDurationsSurviveGeneration() {
        GUIConfig config = new GUIConfig(); config.setProgressionStrategy(HarmonicProgressionPlanner.Strategy.FUNCTIONAL);
        config.setCustomChords("C,F#m.1,F,G7"); config.setCustomChordDurations("4,2,4,4"); config.setSpiceChance(100);
        config.setChordLocks(Collections.singletonList(new ChordLock(1,ChordLock.ROOT | ChordLock.QUALITY | ChordLock.BASS | ChordLock.DURATION)));
        ChordProgressionGenerator.MappedProgression result = ChordProgressionGenerator.generateMappedProgression(config,1,4,null,null,
                Collections.emptyList(),4,1,0.00001);
        ChordSymbol locked = ChordSymbol.parse(result.chordNames.get(1));
        assertEquals(6,locked.root); assertEquals("m",locked.quality); assertEquals(9,locked.pitches(true)[0] % 12);
        assertEquals(2,result.durations.get(1),0);
    }

    @Test public void canonicalHarmonySurvivesRepetitionAndAdapterPlanning() {
        HarmonyTimeline source = harmony("Bb7","Eb"); HarmonyTimeline repeated = source.repeat(2);
        assertEquals("Bb7",harmony("A#7","D#").withSymbols(Arrays.asList("Bb7","Eb")).events().get(0).symbol);
        assertEquals(4,repeated.events().size()); assertEquals("Bb7",repeated.events().get(2).symbol);
        assertEquals(source.length() * 2,repeated.length()); assertNotEquals(repeated.events().get(0).id,repeated.events().get(2).id);
        GUIConfig config = new GUIConfig(); config.setContextualMelody(true);
        org.vibehistorian.vibecomposer.Parts.MelodyPart part = new org.vibehistorian.vibecomposer.Parts.MelodyPart();
        part.setOrderOffset(1); part.setStartNoteChance(100); part.setPauseChance(0);
        org.vibehistorian.vibecomposer.Section section = new org.vibehistorian.vibecomposer.Section(); section.setMeasures(1);
        ContextualMelodyGenerator generator = new ContextualMelodyGenerator(config,new MidiTiming(100),0.95);
        List<int[]> chords = Arrays.asList(source.events().get(0).pitches(),source.events().get(1).pitches());
        generator.generate(part,chords,Arrays.asList(new int[] {70},new int[] {63}),Arrays.asList(1.0,1.0),section,0,MidiUtils.ScaleMode.IONIAN,true,source);
        assertSame(source,generator.lastHarmony); assertEquals(source.revision,generator.lastResult.harmonyRevision);
    }

    @Test public void jazzExtensionsAreStableAndSpellingSeparatesEquivalentMidi() {
        HarmonyTimeline source = harmony("C"); ContextualMelodyPlanner.Options jazz = options(); jazz.style = ContextualMelodyPlanner.Style.JAZZ;
        assertEquals(ContextualMelodyPlanner.Role.EXTENSION,ContextualMelodyPlanner.generate(source,MAJOR,
                Collections.singletonList(slot(0,960,62)),jazz,1).notes.get(0).role);
        assertEquals("Eb",PitchSpelling.chordTone(ChordSymbol.parse("Cm"),63));
        assertEquals("D#",PitchSpelling.chordTone(ChordSymbol.parse("B"),63));
        assertEquals("D#",PitchSpelling.chordTone(ChordSymbol.parse("C7#9"),63));
    }

    @Test public void fixedMelodyKeepsAuthoredPitchesRhythmArticulationAndInput() {
        jm.music.data.Phrase source = new jm.music.data.Phrase();
        jm.music.data.Note first = new jm.music.data.Note(71,1,87); first.setDuration(0.8); first.setOffset(0.1);
        source.addNote(first); source.addNote(new jm.music.data.Note(69,1,91));
        Map<Integer,List<jm.music.data.Note>> result = FixedMelodyAdapter.group(source,harmony("C","F","G7","C"),1,MidiUtils.ScaleMode.AEOLIAN,0);
        assertEquals(71,result.get(0).get(0).getPitch()); assertEquals(71,result.get(2).get(0).getPitch());
        assertEquals(0.8,result.get(0).get(0).getDuration(),0); assertEquals(0.1,result.get(0).get(0).getOffset(),0);
        result.get(0).get(0).setPitch(60); assertEquals(71,source.getNote(0).getPitch());
        GUIConfig config = new GUIConfig(); config.setContextualMelody(true);
        org.vibehistorian.vibecomposer.Parts.MelodyPart part = new org.vibehistorian.vibecomposer.Parts.MelodyPart(); part.setOrder(2);
        config.setMelodyParts(Collections.singletonList(part)); part.setNoteLengthMultiplier(150); part.setSwingPercent(75);
        MelodyGenerationSettings settings = new MelodyGenerationSettings(); settings.setUserMelody(source);
        MidiGenerator midi = new MidiGenerator(config);
        MelodyGenerator generator = new MelodyGenerator(config,midi,settings);
        MelodyPhraseBuilder builder = new MelodyPhraseBuilder(config,generator,new MidiTiming(100),request -> false,
                notes -> { },(phrase,swing) -> { throw new AssertionError("Authored swing changed"); },request -> { },pattern -> { });
        org.vibehistorian.vibecomposer.Section section = new org.vibehistorian.vibecomposer.Section(); section.setMeasures(1);
        List<int[]> chords = Arrays.asList(MidiUtils.mappedChord("C"),MidiUtils.mappedChord("G7"));
        jm.music.data.Phrase rendered = builder.build(part,chords,chords,Arrays.asList(1.0,1.0),0,section,Collections.emptyList(),
                false,Collections.emptyList(),0,0,0,MidiUtils.ScaleMode.AEOLIAN);
        assertEquals(71,rendered.getNote(0).getPitch()); assertEquals(0.8,rendered.getNote(0).getDuration(),0);
        assertEquals(1,rendered.getNote(0).getRhythmValue(),0);
    }

    @Test public void performanceEffectsCannotShiftStableAttacksIntoDifferentHarmony() {
        GUIConfig config = new GUIConfig(); config.setContextualMelody(true);
        org.vibehistorian.vibecomposer.Parts.MelodyPart part = new org.vibehistorian.vibecomposer.Parts.MelodyPart();
        part.setOrder(2); part.setOrderOffset(2); part.setStartNoteChance(100); part.setPauseChance(0); part.setContextualPitchLocks("0=64,1=67");
        config.setMelodyParts(Collections.singletonList(part));
        MidiGenerator midi = new MidiGenerator(config); MelodyGenerator generator = new MelodyGenerator(config,midi);
        MelodyPhraseBuilder builder = new MelodyPhraseBuilder(config,generator,new MidiTiming(100),request -> false,
                notes -> { },(phrase,swing) -> { },request -> {
                    for (Object value : request.phrase.getNoteList()) { ((jm.music.data.Note) value).setOffset(1.1); }
                },pattern -> { });
        org.vibehistorian.vibecomposer.Section section = new org.vibehistorian.vibecomposer.Section(); section.setMeasures(1);
        List<int[]> chords = Arrays.asList(MidiUtils.mappedChord("C"),MidiUtils.mappedChord("G7"));
        try {
            builder.build(part,chords,chords,Arrays.asList(1.0,1.0),0,section,Collections.emptyList(),false,Collections.emptyList(),0,0,0,MidiUtils.ScaleMode.IONIAN);
            fail("Timing changes must not invalidate stable harmony silently");
        } catch (IllegalArgumentException invalid) { assertTrue(invalid.getMessage(),invalid.getMessage().contains("timing effects")); }
    }

    @Test public void coDesignReturnsConsistentBoundedPairWithLocks() {
        GUIConfig config = new GUIConfig(); config.setProgressionStrategy(HarmonicProgressionPlanner.Strategy.FUNCTIONAL);
        HarmonyTimeline initial = harmony("C","Dm","G7","C");
        List<ContextualMelodyPlanner.Slot> rhythm = new ArrayList<>();
        for (int i = 0; i < 4; i++) { rhythm.add(new ContextualMelodyPlanner.Slot(i * 960,960,false,null,1)); }
        MelodyHarmonyCoDesigner.Result result = MelodyHarmonyCoDesigner.generate(config,initial,rhythm,options(),Collections.singletonMap(3,"C"),17);
        assertTrue(result.passes >= 1 && result.passes <= 2); assertEquals("C",result.harmony.events().get(3).symbol);
        assertEquals(result.harmony.revision,result.melody.harmonyRevision);
        for (ContextualMelodyPlanner.PlannedNote note : result.melody.notes) { assertTrue(result.harmony.at(note.slot.start).contains(note.pitch)); }
    }

    @Test public void propertyLocksAndPhraseSettingsRoundTrip() throws Exception {
        GUIConfig config = new GUIConfig(); config.setMelodyForm(MelodyPhrasePlan.Form.QUESTION_ANSWER);
        config.setChordEventIds(Arrays.asList("event-a","event-b"));
        config.setChordLocks(Collections.singletonList(new ChordLock(1,ChordLock.ROOT | ChordLock.BASS)));
        javax.xml.bind.JAXBContext context = javax.xml.bind.JAXBContext.newInstance(GUIConfig.class);
        java.io.StringWriter writer = new java.io.StringWriter(); context.createMarshaller().marshal(config,writer);
        GUIConfig restored = (GUIConfig) context.createUnmarshaller().unmarshal(new java.io.StringReader(writer.toString()));
        assertEquals(MelodyPhrasePlan.Form.QUESTION_ANSWER,restored.getMelodyForm());
        assertEquals(config.getChordEventIds(),restored.getChordEventIds());
        assertEquals(5,restored.getChordLocks().get(0).getMask());
        restored.getChordLocks().clear(); assertEquals(1,restored.getChordLocks().size());
    }
}
