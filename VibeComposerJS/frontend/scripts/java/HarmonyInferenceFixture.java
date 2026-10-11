package org.vibehistorian.vibecomposer.generation;

import jm.music.data.*;
import org.vibehistorian.vibecomposer.*;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import java.util.*;

/** Production inference, skeleton and squish consumers; no reimplemented musical oracle. */
public class HarmonyInferenceFixture {
    static List<List<Integer>> arrays(List<int[]> values) {
        List<List<Integer>> result = new ArrayList<>();
        for (int[] value : values) { List<Integer> notes = new ArrayList<>(); for (int pitch : value) notes.add(pitch); result.add(notes); }
        return result;
    }
    static List<Object> skeleton(Map<Integer,List<Note>> map) {
        List<Object> result = new ArrayList<>();
        for (int chord = 0; chord < map.size(); chord++) {
            List<Object> notes = new ArrayList<>();
            for (Note note : map.get(chord)) notes.add(Map.of("midi", note.getPitch(), "rhythm", note.getRhythmValue()));
            result.add(notes);
        }
        return result;
    }
    public static void main(String[] args) {
        org.apache.logging.log4j.core.config.Configurator.setRootLevel(org.apache.logging.log4j.Level.OFF);
        org.apache.logging.log4j.core.config.Configurator.setLevel("org.vibehistorian.vibecomposer.LG", org.apache.logging.log4j.Level.OFF);
        String[] seeds = {"42", "-2147483648", "9223372036854775807", "9007199254740993"};
        List<Object> weighting = new ArrayList<>(), voicing = new ArrayList<>(), phrases = new ArrayList<>();
        GUIConfig defaults = new GUIConfig(); MidiGenerator timingOwner = new MidiGenerator(defaults);
        Random input = new Random(819);
        for (int sample = 0; sample < 256; sample++) {
            Map<Integer,List<Note>> map = new HashMap<>(); List<Double> durations = new ArrayList<>();
            for (int chord = 0; chord < 8; chord++) {
                List<Note> notes = new ArrayList<>();
                for (int n = 0; n < sample % 10; n++) notes.add(new Note(sample % 13 == 0 ? Note.REST : 48 + input.nextInt(24), new double[] {0,0.1,0.25,0.5,0.75,1,2,4}[input.nextInt(8)]));
                map.put(chord, notes); durations.add(new double[] {1,2,4,6}[sample % 4]);
            }
            weighting.add(Map.of("skeleton", skeleton(map), "durations", durations,
                "names", MelodyChordInference.getChordsFromMelodyPitches(2, durations, map, MidiUtils.baseFreqMap, timingOwner.getTiming())));
        }
        for (String seed : seeds) for (int sample = 0; sample < 48; sample++) {
            List<int[]> chords = new ArrayList<>(), roots = new ArrayList<>(); List<Integer> skipped = new ArrayList<>();
            for (int chord = 0; chord < 6; chord++) {
                String name = MidiUtils.MAJOR_CHORDS.get((sample + chord * 2) % 7);
                int[] root = MidiUtils.mappedChord(name, true), voiced = MidiUtils.mappedChord(name);
                if (sample % 3 == 1) voiced = MidiUtils.mappedChord(new String[] {"Cmaj7", "Dm9", "G7", "Fmaj13", "Am7", "Bdim"}[chord]);
                if ((sample + chord) % 5 == 0) { voiced = MidiUtils.mappedChord(name + ".1"); skipped.add(chord); }
                chords.add(voiced); roots.add(root);
            }
            int chance = new int[] {0,25,67,100}[sample % 4]; boolean large = sample % 2 == 0, progressive = sample % 3 == 2;
            List<int[]> output = progressive ? MidiUtils.squishChordProgressionProgressively(chords, large, Long.parseLong(seed), chance, skipped, roots)
                : MidiUtils.squishChordProgression(chords, large, Long.parseLong(seed), chance, skipped, roots);
            voicing.add(Map.of("seed", seed, "chords", arrays(chords), "roots", arrays(roots), "skipped", skipped,
                "settings", Map.of("flattenVoicingChance",chance,"flattenLargeChords",large,"progressiveVoicing",progressive), "output", arrays(output)));
        }
        for (String seed : seeds) for (int sample = 0; sample < 24; sample++) {
            GUIConfig config = new GUIConfig(); config.setRandomSeed(Long.parseLong(seed));
            config.setMelodyLegacyMode(false); config.setMelodyNewBlocksChance(sample % 2 == 0 ? 25 : 100);
            config.setMelodyBlockTypePreference(Arrays.asList(100,100,100,100,100,100,100));
            config.setMelodyEmphasizeKey(false); config.setMelodyArpySurprises(false); config.setMelodySingleNoteExceptions(false);
            config.setMelodyMaxDirChanges(2); config.setMelodyFillPausesPerChord(false);
            config.setMelodyPatternEffect(sample % 3); config.setMelodyBlockTargetMode(2);
            config.setMelodyTonicNoteTarget(0); config.setMelodyChordNoteTarget(0); config.setMelodyModeNoteTarget(0); config.setMelodyReplaceAvoidNotes(0);
            config.setMelodyUseCustomDurations(false); config.setScaleMidiVelocityInArrangement(false);
            MelodyPart part = new MelodyPart(); part.setOrder(1); part.setOrderOffset(1); part.setPatternSeed((int)Long.parseLong(seed));
            part.setSpeed(new int[]{-100,0,50,100}[sample % 4]); part.setChordNoteChoices(Arrays.asList(0,2,2,4)); part.setMelodyPatternOffsets(Arrays.asList(1,2,1,3));
            part.setPatternFlexible(true); part.setMaxBlockChange(7); part.setBlockJump(1); part.setDoubledRhythmChance(sample % 2 == 0 ? 0 : 40);
            part.setMaxNoteExceptions(2); part.setNoteExceptionChance(30); part.setTranspose(new int[]{0,5,7,-12}[sample % 4]); part.setSplitChance(0); part.setLeadChordsChance(0);
            part.setStartNoteChance(100); part.setPauseChance(0); part.setFillPauses(false); part.setVelocityMin(80); part.setVelocityMax(105);
            part.setAccents(100); part.setNoteLengthMultiplier(100);
            part.setSwingPercent(50); config.setMelodyParts(Collections.singletonList(part));
            List<Integer> degrees = Arrays.asList(1,5,6,4); List<String> names = new ArrayList<>(); List<int[]> roots = new ArrayList<>();
            for (int degree : degrees) { String name = MidiUtils.MAJOR_CHORDS.get(degree - 1); names.add(name); roots.add(MidiUtils.mappedChord(name,true)); }
            List<int[]> voiced = MidiUtils.squishChordProgression(roots, false, Long.parseLong(seed), 100, Collections.emptyList(), roots);
            MidiGenerator midi = new MidiGenerator(config); List<Double> durations = Collections.nCopies(4,4.0); midi.setProgressionDurations(durations);
            MelodyGenerationState state = new MelodyGenerationState(new HashMap<>(), new ArrayList<>(), new ArrayList<>());
            MelodyChordInference inference = new MelodyChordInference(config, midi.getTiming(), state, new MelodyChordInference.RunStateUpdater() {
                public void progressionChanged(List<int[]> chords, List<int[]> roots) {}
                public void userMelodyProgressionChanged(List<Double> durations, List<String> names) {}
            });
            MelodyBlockSkeletonGenerator generator = new MelodyBlockSkeletonGenerator(config,midi.getTiming(),midi.getGlobalDurationMultiplier(),state,inference,
                new MelodyExpansion(config,midi.getTiming(),midi.getGlobalDurationMultiplier()),new MelodyGenerationSettings());
            generator.generateMelodyBlockSkeletonFromChords(new MelodyBlockSkeletonGenerator.SkeletonGenerationRequest(part,voiced,roots,1,0,new Section(),Collections.emptyList(),Collections.emptyList(),durations,names,roots,voiced,null));
            MelodyGenerator melody = new MelodyGenerator(config,midi);
            MelodyPhraseBuilder builder = new MelodyPhraseBuilder(config,melody,midi.getTiming(),request -> false,request -> {},
                (phrase,swing) -> midi.swingPhrase(phrase,swing,1),request -> {},result -> {});
            List<Object> outputs = new ArrayList<>();
            for (int offset : new int[]{0,100}) outputs.add(ArpCoreFixture.notes(builder.build(part,state.melodyBasedChordProgression,state.melodyBasedRootProgression,durations,offset,
                new Section("VERSE1",1,100,100,100,100,100),Collections.emptyList(),false,Collections.emptyList(),0,0,0,null)));
            phrases.add(Map.of("seed",seed,"sample",sample,"skeleton",skeleton(state.chordMelodyMap1),"alternate",state.alternateChords,
                "roots",arrays(state.melodyBasedRootProgression),"voicings",arrays(state.melodyBasedChordProgression),"notes",outputs));
        }
        System.out.println(ArpCoreFixture.json(Map.of("order",new ArrayList<>(MidiUtils.baseFreqMap.keySet()),"weighting",weighting,"voicing",voicing,"phrases",phrases)));
    }
}
