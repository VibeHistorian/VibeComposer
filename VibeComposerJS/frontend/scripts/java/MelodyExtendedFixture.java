package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Note;
import jm.music.data.Phrase;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import java.util.*;

/** Complete supported phrases through production block skeleton, expansion and phrase builder. */
public class MelodyExtendedFixture {
    public static void main(String[] args) {
        boolean globals = args.length > 0 && args[0].equals("globals");
        boolean effects = args.length > 0 && args[0].equals("effects");
        boolean catalogue = args.length > 0 && args[0].equals("catalogue");
        boolean emphasis = args.length > 0 && args[0].equals("emphasis");
        boolean tonal = args.length > 0 && args[0].equals("tonal");
        boolean modes = args.length > 0 && args[0].equals("modes");
        boolean harmony = modes || args.length > 0 && args[0].equals("harmony");
        boolean preparedTargets = args.length > 0 && args[0].equals("targets");
        org.apache.logging.log4j.core.config.Configurator.setRootLevel(org.apache.logging.log4j.Level.OFF);
        org.apache.logging.log4j.core.config.Configurator.setLevel("org.vibehistorian.vibecomposer.LG", org.apache.logging.log4j.Level.OFF);
        StringBuilder json = new StringBuilder("[\n");
        String[] seeds = { "42", "-2147483648", "9223372036854775807", "9007199254740993" };
        String[] keys = { "C", "D", "A\\u266d", "F\\u266f" };
        int[] transpositions = { 0, 2, 8, 6 };
        int[] speeds = { -100, 0, 50, 100 };
        boolean first = true;
        for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
          for (int effectCase = 0; effectCase < (modes ? 6 : harmony ? 12 : tonal ? 10 : catalogue ? 8 : effects || emphasis ? 6 : 1); effectCase++) {
            for (int sample = 0; sample < 48; sample++) {
                int chordCount = new int[] { 1, 3, 4, 7, 8 }[sample % 5];
                int[] structure = sample % 3 == 0 ? new int[] { 1, 2, 1, 3 }
                        : sample % 3 == 1 ? new int[] { 1, -1, 2, -2 } : new int[] { 0, 0, 3 };
                if ((emphasis || tonal || harmony) && sample % 4 == 3) structure = new int[] {-1, 1, -2, 2};
                int[] targets = sample % 2 == 0 ? new int[] { 0, 2, 2, 4 } : new int[] { -3, 1, 5 };
                int notesSeedOffset = sample % 4 == 0 ? 100 : 0;
                int explicitSeed = sample == 19 ? 123456789 : 0;
                MelodyPart part = new MelodyPart();
                part.setOrder(1); part.setOrderOffset(1);
                part.setPatternSeed(explicitSeed == 0 ? (int) Long.parseLong(seeds[seedIndex]) : explicitSeed);
                part.setMelodyPatternOffsets(list(structure)); part.setChordNoteChoices(list(targets));
                part.setSpeed(speeds[sample % 4]); part.setMaxBlockChange(sample % 8);
                part.setBlockJump(sample % 5); part.setPatternFlexible(sample % 2 == 0);
                part.setDoubledRhythmChance(new int[] { 0, 35, 100 }[sample % 3]); part.setMaxNoteExceptions(sample % 5); part.setNoteExceptionChance(new int[] { 0, 40, 100 }[(sample / 3) % 3]);
                part.setSplitChance(new int[] { 0, 45, 100 }[(sample / 4) % 3]); part.setLeadChordsChance(new int[] { 0, 25, 100 }[(sample / 6) % 3]); part.setStartNoteChance(new int[] { 100, 80, 0 }[(sample / 8) % 3]);
                part.setFillPauses(sample % 3 == 1); part.setPauseChance((sample % 5) * 20);
                part.setAccents(sample % 3 == 0 ? 0 : 100);
                part.setVelocityMin(80); part.setVelocityMax(105);
                part.setTranspose(new int[] { 0, 5, 7, -12 }[sample % 4]);
                part.setNoteLengthMultiplier(sample % 2 == 0 ? 150 : 75);
                part.setSwingPercent(sample % 4 == 0 ? 66 : 50);
                part.setChordSpanFill(sample % 5 == 0 ? ChordSpanFill.ODD : ChordSpanFill.ALL);
                part.setFillFlip(sample == 18);

                GUIConfig config = new GUIConfig();
                config.setMelodyParts(Collections.singletonList(part));
                config.setMelodyLegacyMode(false); config.setMelodyNewBlocksChance(100);
                List<Integer> preference = catalogue && effectCase % 3 != 0
                        ? list(effectCase % 3 == 1 ? new int[] {0,7,4,3,1,2,5,6} : new int[] {7,6,5,4,3,2,1,0})
                        : Collections.emptyList();
                if (catalogue) {
                    // Isolate catalogue parity from Java's empty split/lead pitch range bug.
                    part.setSplitChance(0); part.setLeadChordsChance(0);
                    config.setMelodyNewBlocksChance(new int[] {0,0,0,0,25,50,75,100}[effectCase]);
                    config.setMelodyBlockTypePreference(list(new int[][] {
                        {100,100,100,100,100,100,100}, {0,0,0,0,0,0,0}, {100,0,0,0,0,0,0},
                        {0,0,100,60,20,100,100}, {0,0,0,0,0,100,100}, {10,30,50,70,90,100,100},
                        {100,100,100,100,100,100,100}, {100,100,100,100,100,100,100}
                    }[effectCase]));
                }
                if (emphasis || tonal || harmony) {
                    part.setSplitChance(0); part.setLeadChordsChance(0);
                    config.setMelodyNewBlocksChance(effectCase >= 3 ? 25 : 100);
                    config.setMelodyBlockTypePreference(list(new int[] {100,100,100,100,100,100,100}));
                }
                config.setMelodyBlockTargetMode(2); config.setMelodyPatternEffect((effects || catalogue || emphasis || tonal || harmony) ? effectCase % 3 : 2);
                config.setMelodyMaxDirChanges(globals ? 1 + sample % 6 : 2); config.setMelodyEmphasizeKey(emphasis || (tonal || harmony) && sample % 2 == 0);
                config.setMelodySingleNoteExceptions(globals && sample % 2 == 0); config.setMelodyArpySurprises(effects && effectCase >= 3); config.setMelodyUseCustomDurations(false);
                config.setMelodyTonicNoteTarget(0); config.setMelodyChordNoteTarget(0); config.setMelodyModeNoteTarget(0);
                if (tonal) {
                    config.setMelodyTonicNoteTarget(new int[] {0,10,30,70,100,0,0,0,40,100}[effectCase]);
                    config.setMelodyModeNoteTarget(new int[] {0,0,0,0,0,10,50,100,60,100}[effectCase]);
                }
                config.setMelodyReplaceAvoidNotes(0); config.setMelodyFillPausesPerChord(globals && sample % 4 < 2);
                if (harmony) {
                    config.setMelodyChordNoteTarget(new int[] {0,25,70,100}[effectCase / 3]);
                    config.setMelodyReplaceAvoidNotes(effectCase % 3);
                    config.setMelodyTonicNoteTarget(effectCase >= 9 ? 40 : 0);
                    config.setMelodyModeNoteTarget(effectCase >= 9 ? 60 : 0);
                    config.setMelodyFillPausesPerChord(sample % 4 < 2);
                }
                config.setMelodyBasicChordsOnly(false); config.setScaleMidiVelocityInArrangement(false);
                config.setMelodyTargetNoteVariation(preparedTargets ? 1 + sample % 6 : 3);
                config.setNoteTargetDirectionChoice(preparedTargets ? MelodyUtils.NoteTargetDirection.values()[(sample / 6) % 3] : MelodyUtils.NoteTargetDirection.ASC);
                config.setMelodyUseDirectionsFromProgression(preparedTargets && (sample / 18) % 2 == 1);
                if (modes) {
                    config.setMelodyBlockTargetMode(effectCase % 3);
                    config.setMelodyTargetNoteVariation(effectCase % 3 == 0 ? 1 : 1 + sample % 6);
                    config.setNoteTargetDirectionChoice(effectCase % 3 == 0 ? MelodyUtils.NoteTargetDirection.ASC : MelodyUtils.NoteTargetDirection.values()[(sample / 6) % 3]);
                    config.setMelodyUseDirectionsFromProgression((sample / 18) % 2 == 1);
                    config.setMelodyBasicChordsOnly(sample % 2 == 0);
                    if (effectCase % 3 == 0) { targets = new int[] {0,1,2,3}; part.setChordNoteChoices(list(targets)); }
                }
                int[] targetProgression = new int[chordCount];
                int targetSeed = part.getPatternSeed() + 1;
                String[] symbols = {"C", "Dm", "Em", "F", "G", "Am", "Bdim"};
                List<String> targetChords = new ArrayList<>();
                for (int chord = 0; chord < chordCount; chord++) {
                    targetProgression[chord] = new int[] {1, 5, 6, 4, 7, 3, 2, 1}[chord];
                    targetChords.add(symbols[targetProgression[chord] - 1]);
                }
                if (preparedTargets) {
                    // Production split/lead pitch selection can throw for some descending
                    // intervals. Target phrase evidence isolates preparation from that bug;
                    // the original extended fixture retains its articulation comparisons.
                    part.setSplitChance(0); part.setLeadChordsChance(0);
                    targets = MelodyTargetUtils.generateNoteTargetOffsets(targetChords, targetSeed, 2,
                            config.getMelodyTargetNoteVariation(), null, config.getNoteTargetDirectionChoice(), config.isMelodyUseDirectionsFromProgression())
                            .stream().mapToInt(Integer::intValue).toArray();
                    part.setChordNoteChoices(list(targets));
                }
                config.setScaleMode(sample % 2 == 0 ? MidiUtils.ScaleMode.IONIAN : MidiUtils.ScaleMode.AEOLIAN);
                Section section = new Section("VERSE1", 1, 100, 100, 100, 100, 100);
                MidiGenerator midi = new MidiGenerator(config);
                midi.setProgressionDurations(Collections.nCopies(chordCount, 4.0));
                MelodyGenerator melody = new MelodyGenerator(config, midi);
                // Root-only progression is the supported P1b context; chord-driven targeting is deferred.
                List<int[]> roots = new ArrayList<>();
                for (int chord = 0; chord < chordCount; chord++) roots.add(new int[] { 60 });
                List<int[]> repairChords = roots;
                StringJoiner customJson = new StringJoiner(",", "[", "]");
                if (harmony) {
                    repairChords = new ArrayList<>();
                    if (modes) roots.clear();
                    for (int chord = 0; chord < chordCount; chord++) {
                        if (sample % 3 == 0) {
                            repairChords.add(MidiUtils.getNormalMappedChord(symbols[targetProgression[chord] - 1], false));
                            if (modes) roots.add(MidiUtils.getNormalMappedChord(symbols[targetProgression[chord] - 1], true));
                            customJson.add("null");
                        } else {
                            int root = (sample + chord * 5) % 12, inversion = (sample + chord) % 11 - 5;
                            String spice = MidiUtils.SPICE_NAMES_LIST.get((sample + chord * 3) % MidiUtils.SPICE_NAMES_LIST.size());
                            if (modes && sample % 8 == 4 && chord == 0) { root = 0; spice = "m"; }
                            int[] voicing = MidiUtils.getNormalMappedChord(MidiUtils.SEMITONE_LETTERS.get(root) + spice + "." + inversion, false);
                            if (sample % 2 != 0) voicing = MidiUtils.transposeChord(voicing,
                                    MidiUtils.ScaleMode.AEOLIAN.noteAdjustScale, MidiUtils.ScaleMode.IONIAN.noteAdjustScale, true);
                            repairChords.add(voicing);
                            if (modes) {
                                int[] rootVoicing = MidiUtils.getNormalMappedChord(MidiUtils.SEMITONE_LETTERS.get(root) + spice, true);
                                if (sample % 2 != 0) rootVoicing = MidiUtils.transposeChord(rootVoicing,
                                        MidiUtils.ScaleMode.AEOLIAN.noteAdjustScale, MidiUtils.ScaleMode.IONIAN.noteAdjustScale, true);
                                roots.add(rootVoicing);
                            }
                            customJson.add("{\"root\":" + ((root + transpositions[seedIndex]) % 12)
                                    + ",\"spice\":\"" + spice + "\",\"inversion\":" + inversion + "}");
                        }
                    }
                }
                if (modes && effectCase >= 3) {
                    targets = MelodyTargetUtils.generateNoteTargetOffsets(roots, targetSeed, config.getMelodyBlockTargetMode(),
                            config.getMelodyTargetNoteVariation(), config.getNoteTargetDirectionChoice(), config.isMelodyUseDirectionsFromProgression())
                            .stream().mapToInt(Integer::intValue).toArray();
                    part.setChordNoteChoices(list(targets));
                }
                MelodyPhraseBuilder builder = new MelodyPhraseBuilder(config, melody, midi.getTiming(),
                        request -> false, request -> {},
                        (phrase, swing) -> midi.swingPhrase(phrase, swing, 1), request -> {}, result -> {});
                Phrase phrase = builder.build(part, repairChords, roots, Collections.nCopies(chordCount, 4.0), notesSeedOffset,
                        section, Collections.emptyList(), false, preference, 0, 0,
                        transpositions[seedIndex], null);
                if (!first) json.append(",\n"); first = false;
                json.append("{\"globalSettings\":{\"maxDirectionChanges\":").append(config.getMelodyMaxDirChanges())
                        .append(",\"singleNoteExceptions\":").append(config.isMelodySingleNoteExceptions())
                        .append(",\"fillPausesPerChord\":").append(config.isMelodyFillPausesPerChord())
                        .append(",\"patternEffect\":").append(config.getMelodyPatternEffect())
                        .append(",\"arpySurprises\":").append(config.isMelodyArpySurprises())
                        .append(",\"targetNoteVariation\":").append(config.getMelodyTargetNoteVariation())
                        .append(",\"targetDirection\":\"").append(config.getNoteTargetDirectionChoice().name()).append("\"")
                        .append(",\"useDirectionsFromProgression\":").append(config.isMelodyUseDirectionsFromProgression())
                        .append(",\"newBlocksChance\":").append(config.getMelodyNewBlocksChance())
                        .append(",\"emphasizeKey\":").append(config.isMelodyEmphasizeKey())
                        .append(",\"tonicNoteTarget\":").append(config.getMelodyTonicNoteTarget())
                        .append(",\"modeNoteTarget\":").append(config.getMelodyModeNoteTarget())
                        .append(",\"chordNoteTarget\":").append(config.getMelodyChordNoteTarget())
                        .append(",\"replaceAvoidNotes\":").append(config.getMelodyReplaceAvoidNotes())
                        .append(",\"blockTargetMode\":").append(config.getMelodyBlockTargetMode())
                        .append(",\"blockTypeWeights\":").append(config.getMelodyBlockTypePreference())
                        .append(",\"blockChangePreference\":").append(preference)
                        .append("},\"seed\":\"").append(seeds[seedIndex]).append("\",\"key\":\"").append(keys[seedIndex])
                        .append("\",\"scale\":\"").append(sample % 2 == 0 ? "major" : "natural-minor")
                        .append("\",\"chordCount\":").append(chordCount).append(",\"notesSeedOffset\":").append(notesSeedOffset)
                        .append(",\"targetSeed\":").append(targetSeed).append(",\"targetProgression\":").append(Arrays.toString(targetProgression))
                        .append(harmony ? ",\"customChords\":" + customJson.toString() : "")
                        .append(modes ? ",\"prepared\":" + (effectCase >= 3) + ",\"basicChordsOnly\":" + config.isMelodyBasicChordsOnly() : "")
                        .append(",\"settings\":{\"algorithm\":\"block\",\"speed\":").append(part.getSpeed())
                        .append(",\"doubledRhythmChance\":").append(part.getDoubledRhythmChance()).append(",\"maxNoteExceptions\":").append(part.getMaxNoteExceptions()).append(",\"noteExceptionChance\":").append(part.getNoteExceptionChance()).append(",\"splitChance\":").append(part.getSplitChance()).append(",\"leadChordsChance\":").append(part.getLeadChordsChance()).append(",\"startNoteChance\":").append(part.getStartNoteChance()).append(",\"chordNoteChoices\":").append(Arrays.toString(targets))
                        .append(",\"melodyPatternOffsets\":").append(Arrays.toString(structure))
                        .append(",\"maxBlockChange\":").append(part.getMaxBlockChange()).append(",\"blockJump\":").append(part.getBlockJump())
                        .append(",\"patternFlexible\":").append(part.isPatternFlexible()).append(",\"fillPauses\":").append(part.isFillPauses())
                        .append(",\"pauseChance\":").append(part.getPauseChance()).append(",\"accents\":").append(part.getAccents())
                        .append(",\"velocityMin\":80,\"velocityMax\":105,\"transpose\":").append(part.getTranspose())
                        .append(",\"noteLengthMultiplier\":").append(part.getNoteLengthMultiplier())
                        .append(",\"swingPercent\":").append(part.getSwingPercent())
                        .append(",\"chordSpanFill\":\"").append(part.getChordSpanFill().name())
                        .append("\",\"fillFlip\":").append(part.isFillFlip()).append(",\"patternSeed\":").append(explicitSeed)
                        .append("},\"notes\":[");
                double time = 0;
                boolean firstNote = true;
                for (Object entry : phrase.getNoteList()) {
                    Note note = (Note) entry;
                    if (note.getPitch() >= 0) {
                        if (!firstNote) json.append(','); firstNote = false;
                        json.append('[').append(note.getPitch()).append(',').append(time + note.getOffset())
                                .append(',').append(note.getDuration()).append(',').append(note.getDynamic()).append(']');
                    }
                    time += note.getRhythmValue();
                }
                json.append("]}");
            }
          }
        }
        System.out.println(json.append("\n]"));
    }

    private static List<Integer> list(int[] values) {
        List<Integer> result = new ArrayList<>();
        for (int value : values) result.add(value);
        return result;
    }
}

