package org.vibehistorian.vibecomposer.generation;

import jm.constants.Pitches;
import jm.music.data.Note;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.Enums.BlockType;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.Helpers.PhraseNote;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Section;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;
import java.util.stream.Collectors;

import static org.vibehistorian.vibecomposer.Constants.DBL_ERR;
import static org.vibehistorian.vibecomposer.MidiUtils.convertChordToLength;
import static org.vibehistorian.vibecomposer.MidiUtils.getBasicChordsFromRoots;

final class MelodyBlockSkeletonGenerator {
    private final GUIConfig gc;
    private final MidiGenerator mg;
    private final MelodyGenerationState state;
    private final MelodyChordInference inference;
    private final MelodyExpansion expansion;
    private final MelodyGenerationSettings settings;

    MelodyBlockSkeletonGenerator(GUIConfig gc, MidiGenerator mg, MelodyGenerationState state,
            MelodyChordInference inference, MelodyExpansion expansion,
			MelodyGenerationSettings settings) {
        this.gc = gc;
        this.mg = mg;
        this.state = state;
        this.inference = inference;
        this.expansion = expansion;
		this.settings = settings;
    }
    protected Vector<Note> generateMelodyBlockSkeletonFromChords(MelodyPart mp, List<int[]> chords,
                                                                 List<int[]> roots, int measures, int notesSeedOffset, Section sec,
                                                                 List<Integer> variations, List<Integer> melodyBlockJumpPreference) {

        boolean genVars = variations == null;

        boolean fillChordMelodyMap = false;
        if (state.chordMelodyMap1.isEmpty() && notesSeedOffset == 0
                && (roots.size() == mg.getGeneratedChordNames().size())) {
            fillChordMelodyMap = true;
        }

        if (sec.getSectionVariations() != null && sec.isSectionVar(2)) {
            notesSeedOffset += 100;
        }

        int MAX_JUMP_SKELETON_CHORD = mp.getBlockJump();
        int SAME_RHYTHM_CHANCE = mp.getDoubledRhythmChance();
        int EXCEPTION_CHANCE = mp.getNoteExceptionChance();
        int CHORD_STRETCH = 4;
        int BLOCK_TARGET_MODE = gc.getMelodyBlockTargetMode();

        int seed = mp.getPatternSeedWithPartOffset();
        int melodyBlockGeneratorSeed = seed + notesSeedOffset;
        LG.d("Seed: " + seed);


        // A B A C pattern
        List<Integer> blockSeedOffsets = (mp.getMelodyPatternOffsets() != null)
                ? mp.getMelodyPatternOffsets()
                : new ArrayList<>(Arrays.asList(1, 2, 1, 3));

        while (blockSeedOffsets.size() < chords.size()) {
            blockSeedOffsets.addAll(blockSeedOffsets);
        }

        Map<Integer, Pair<Pair<List<Integer>, Integer>, List<MelodyBlock>>> changesAndBlocksMap = new HashMap<>();
        Map<Integer, List<Double>> blockDurationsMap = new HashMap<>();


        // Chord note choices
        List<Integer> blockChordNoteChoices;
        Map<Integer, List<Integer>> targetNotes = settings.getOrCreateTargetNotes();
        if (settings.isRandomizeTargetNotes()) {
            if (gc.getActualArrangement().getSections().indexOf(sec) < 1
                    || targetNotes.get(mp.getOrderOffset()) == null) {
                int targetNoteSeed = gc.isMelody1ForcePatterns()
                        ? (seed + 1)
                        : (seed + mp.getOrderOffset());
                blockChordNoteChoices = MidiGeneratorUtils.generateNoteTargetOffsets(roots, targetNoteSeed,
                        gc.getMelodyBlockTargetMode(), gc.getMelodyTargetNoteVariation(),
                        gc.getNoteTargetDirectionChoice(), gc.isMelodyUseDirectionsFromProgression());
            } else {
                blockChordNoteChoices = targetNotes.get(mp.getOrderOffset());
            }
        } else {
            blockChordNoteChoices = (mp.getChordNoteChoices() != null)
                    ? mp.getChordNoteChoices()
                    : new ArrayList<>(Arrays.asList(0, 2, 2, 4));

            while (chords.size() > blockChordNoteChoices.size()) {
                blockChordNoteChoices.addAll(blockChordNoteChoices);
            }
        }
        targetNotes.put(mp.getOrderOffset(), blockChordNoteChoices);
        LG.d("Choices: " + blockChordNoteChoices);

        Vector<Note> noteList = new Vector<>();

        // if notes seed offset > 0, add it only to one of: rhythms, pitches
        //Random nonMainMelodyGenerator = new Random(seed + 30);
        int pitchPickerOffset = notesSeedOffset;
        int rhythmOffset = notesSeedOffset;

        int firstBlockOffset = Math.abs(blockSeedOffsets.get(0));
        if (firstBlockOffset > 0) {
            firstBlockOffset--;
        }
        List<Integer> usedMelodyBlockJumpPreference = (melodyBlockJumpPreference != null) && (melodyBlockJumpPreference.size() == MelodyUtils.BLOCK_CHANGE_JUMP_PREFERENCE.size())
                && MelodyUtils.BLOCK_CHANGE_JUMP_PREFERENCE.containsAll(melodyBlockJumpPreference)
                ? melodyBlockJumpPreference :
                Collections.emptyList();

        Random pitchPickerGenerator = new Random(seed + pitchPickerOffset + firstBlockOffset);
        Random exceptionGenerator = new Random(melodyBlockGeneratorSeed + 2 + firstBlockOffset);
        Random sameRhythmGenerator = new Random(seed + 3 + firstBlockOffset);
        Random alternateRhythmGenerator = new Random(seed + 4);
        Random durationGenerator = new Random(melodyBlockGeneratorSeed + 5);
        Random embellishmentGenerator = new Random(melodyBlockGeneratorSeed + 10);
        Random soloGenerator = new Random(melodyBlockGeneratorSeed + 25);
        //Random surpriseGenerator = new Random(seed + notesSeedOffset + 15);

        double[] melodySkeletonDurations = { MidiGenerator.Durations.QUARTER_NOTE, MidiGenerator.Durations.HALF_NOTE,
                MidiGenerator.Durations.DOTTED_HALF_NOTE, MidiGenerator.Durations.WHOLE_NOTE };

        List<int[]> usedChords = null;
        if (gc.isMelodyBasicChordsOnly()) {
            List<int[]> basicChordsUnsquished = getBasicChordsFromRoots(roots);
            for (int i = 0; i < chords.size(); i++) {
                basicChordsUnsquished.set(i,
                        convertChordToLength(basicChordsUnsquished.get(i), chords.get(i).length));
            }
            usedChords = basicChordsUnsquished;
        } else {
            usedChords = chords;
        }

        //List<int[]> stretchedChords = usedChords.stream()
        //		.map(e -> convertChordToLength(e, CHORD_STRETCH)).collect(Collectors.toList());
        //LG.d("Alt: " + alternateRhythm);
        MidiUtils.ScaleMode scale = (mg.modScale != null) ? mg.modScale : gc.getScaleMode();
        List<Integer> emphasizeKeyNoteOrder = new ArrayList<>(MidiUtils.keyEmphasisOrder);
        if (scale != null && scale.modeTargetNote >= 0) {
            Integer ionianTargetNote = MidiUtils.MAJ_SCALE.get(scale.modeTargetNote);
            emphasizeKeyNoteOrder.remove(ionianTargetNote);
            emphasizeKeyNoteOrder.add(1, ionianTargetNote);
        }
        int maxBlockChangeAdjustment = 0;
        boolean embellish = false;
        for (int o = 0; o < measures; o++) {

            for (int chordIndex = 0; chordIndex < usedChords.size(); chordIndex++) {
                // either after first measure, or after first half of combined chord prog

                if (genVars && (chordIndex == 0)) {
                    variations = MidiGenerator.fillVariations(sec, mp, variations, 0);
                    // never generate MaxJump for important melodies
                    if ((variations != null) && sec.getTypeMelodyOffset() == 0) {
                        variations.removeIf(e -> e == 1);
                    }
                }

                if ((variations != null) && (chordIndex == 0)) {
                    for (Integer var : variations) {
                        if (o == measures - 1) {
                            LG.d("Melody variation: " + var);
                        }

                        switch (var) {
                            case 0:
                                // transpose - only add, processed later
                                break;
                            case 1:
                                maxBlockChangeAdjustment++;
                                break;
                            case 2:
                                embellish = true;
                                break;
                            case 3:
                                // solo (also processed later)
                                SAME_RHYTHM_CHANCE = 100;
                                List<Integer> soloTargetPattern = MelodyUtils.SOLO_MELODY_PATTERNS.get(
                                        soloGenerator.nextInt(MelodyUtils.SOLO_MELODY_PATTERNS.size()));
                                blockSeedOffsets = new ArrayList<>(soloTargetPattern);
                                LG.i("Chosen solo pattern: "
                                        + StringUtils.join(soloTargetPattern, ","));

                                while (blockSeedOffsets.size() < chords.size()) {
                                    blockSeedOffsets.addAll(blockSeedOffsets);
                                }
                                break;
                            default:
                                throw new IllegalArgumentException("Too much variation!");
                        }
                    }
                }

                int blockOffset = blockSeedOffsets.get(chordIndex % blockSeedOffsets.size());
                int originalBlockOffset = blockOffset;
                blockOffset = Math.abs(blockOffset);

                if (blockOffset > 0) {
                    blockOffset--;
                }

                if (fillChordMelodyMap && o == 0) {
                    if (!state.chordMelodyMap1.containsKey(chordIndex)) {
                        state.chordMelodyMap1.put(chordIndex, new ArrayList<>());
                    }
                }
                pitchPickerGenerator.setSeed(seed + pitchPickerOffset + blockOffset);
                exceptionGenerator.setSeed(seed + 2 + notesSeedOffset + blockOffset);
                sameRhythmGenerator.setSeed(seed + 3 + blockOffset);

                List<Double> durations = (gc.getMelodyPatternEffect() != 1)
                        ? blockDurationsMap.get(blockOffset)
                        : null;
                boolean badDuration = false;
                if (durations != null
                        && !MidiUtils.roughlyEqual(durations.stream().mapToDouble(e -> e).sum(),
                        mg.progressionDurations.get(chordIndex))) {
                    durations = null;
                    badDuration = true;
                }
                boolean sameRhythmTwice = sameRhythmGenerator.nextInt(100) < SAME_RHYTHM_CHANCE;
                int rhythmSeed = seed + blockOffset + rhythmOffset;

                if (durations == null) {
                    double rhythmDuration = sameRhythmTwice
                            ? mg.progressionDurations.get(chordIndex) / 2.0
                            : mg.progressionDurations.get(chordIndex);

                    int speed = MidiGeneratorUtils.adjustChanceParamForTransition(mp.getSpeed(),
                            sec, chordIndex, chords.size(), 40, 0.25, false, false);
                    speed = OMNI.clamp(speed, -100, 100);
                    int addQuick = (speed - 50) * 4;
                    int addSlow = addQuick * -1;

                    int[] melodySkeletonDurationWeights = MelodyUtils
                            .normalizedCumulativeWeights(200 + addQuick, Math.max(1, 200 + addQuick / 2), 200 + addQuick, 200 + addSlow);


                    Rhythm rhythm = new Rhythm(rhythmSeed, rhythmDuration, melodySkeletonDurations,
                            melodySkeletonDurationWeights);

                    durations = rhythm.regenerateDurations(10, melodySkeletonDurations[0]);
                    if (sameRhythmTwice) {
                        durations.addAll(durations);
                    }
                    blockDurationsMap.put(blockOffset, durations);
                }
                LG.d("Overall Block Durations: " + StringUtils.join(durations, ",")
                        + ", Doubled rhythm: " + sameRhythmTwice);
                int chord1 = MidiGeneratorUtils.getStartingNote(roots,
                        blockChordNoteChoices, chordIndex, BLOCK_TARGET_MODE);
                int chord2 = MidiGeneratorUtils.getStartingNote(roots,
                        blockChordNoteChoices, chordIndex + 1, BLOCK_TARGET_MODE);
                int startingOct = chord1 / 7;

                Pair<Pair<List<Integer>, Integer>, List<MelodyBlock>> existingPattern = (badDuration)
                        ? null
                        : changesAndBlocksMap.get(blockOffset);

                int remainingDirChanges = gc.getMelodyMaxDirChanges();
                Pair<List<Integer>, Integer> blockChangesPair;


                int originalBlockOffsetIndex = (originalBlockOffset < 0) ? (blockSeedOffsets.indexOf(originalBlockOffset * -1)) : blockSeedOffsets.indexOf(originalBlockOffset);
                Map<Integer, List<PhraseNote>> customUserDurationsByBlock = expansion.convertCustomUserDurations(mp, melodyBlockGeneratorSeed,
                        chordIndex, (gc.isMelodyCustomDurationsRandomWeighting() && existingPattern != null) ? originalBlockOffsetIndex : blockOffset);
                int numBlocks = !customUserDurationsByBlock.isEmpty() ? customUserDurationsByBlock.size() : durations.size();
                if (existingPattern != null
                        && gc.getMelodyPatternEffect() > 0) {
                    blockChangesPair = existingPattern.getLeft();
                } else {
                    blockChangesPair = MelodyUtils.blockChangeSequence(chord1, chord2,
                            melodyBlockGeneratorSeed, numBlocks,
                            OMNI.clamp(
                                    mp.getMaxBlockChange() + maxBlockChangeAdjustment,
                                    0, 7),
                            remainingDirChanges);
                }
                remainingDirChanges -= blockChangesPair.getRight();
                List<Integer> blockChanges = blockChangesPair.getLeft();
                boolean FLEXIBLE_PATTERN = mp.isPatternFlexible() && blockChanges.size() > 1;
                if (FLEXIBLE_PATTERN && existingPattern != null) {
                    int blockChangesSum = blockChanges.stream().mapToInt(e -> e).sum();
                    int newLastBlockChange = blockChanges.get(blockChanges.size() - 1);
                    newLastBlockChange += (chord2 - chord1) - blockChangesSum;
                    List<Integer> newBlockChanges = new ArrayList<>(blockChanges);
                    newBlockChanges.set(blockChanges.size() - 1, newLastBlockChange);
                    blockChanges = newBlockChanges;

                }
                LG.d("Block changes: " + blockChanges);
                int startingNote = chord1 % 7;

                List<Integer> forcedLengths = (existingPattern != null
                        && gc.getMelodyPatternEffect() != 1)
                        ? existingPattern.getRight().stream().map(e -> e.durations.size())
                        .collect(Collectors.toList())
                        : null;

                List<MelodyBlock> melodyBlocks = (existingPattern != null
                        && gc.getMelodyPatternEffect() > 0 && !FLEXIBLE_PATTERN)
                        ? existingPattern.getRight()
                        : generateMelodyBlocksForDurations(mp, sec, durations, roots,
                        melodyBlockGeneratorSeed, blockOffset, blockChanges,
                        MAX_JUMP_SKELETON_CHORD, startingNote, chordIndex,
                        forcedLengths, remainingDirChanges, usedMelodyBlockJumpPreference,
                        customUserDurationsByBlock, numBlocks);
                if (FLEXIBLE_PATTERN && existingPattern != null
                        && gc.getMelodyPatternEffect() > 0) {
                    List<MelodyBlock> storedMelodyBlocks = new ArrayList<>(
                            existingPattern.getRight());
                    LG.d("HALF PATTERN - set last melody block to newly generated block! Sizes equal?: "
                            + (storedMelodyBlocks.size() == melodyBlocks.size()));
                    storedMelodyBlocks.set(storedMelodyBlocks.size() - 1,
                            melodyBlocks.get(melodyBlocks.size() - 1));
                    melodyBlocks = storedMelodyBlocks;
                }
                //LG.d("Starting note: " + startingNote);

                if (existingPattern == null) {
                    LG.n("Stored pattern: " + blockOffset + ", for chord index:" + chordIndex
                            + ", Pattern effect: " + gc.getMelodyPatternEffect());
                    changesAndBlocksMap.put(blockOffset, Pair.of(blockChangesPair, melodyBlocks));
                } else {
                    LG.n("Loaded pattern: " + blockOffset + ", for chord index:" + chordIndex
                            + ", Pattern effect: " + gc.getMelodyPatternEffect());
                }

                int adjustment = 0;
                int exceptionCounter = mp.getMaxNoteExceptions();
                boolean invertedPattern = originalBlockOffset < 0;
                for (int blockIndex = 0; blockIndex < melodyBlocks.size(); blockIndex++) {
                    MelodyBlock mb = melodyBlocks.get(blockIndex);
                    if (invertedPattern) {
                        mb = new MelodyBlock(MelodyUtils.inverse(mb.notes), mb.durations, true);
                    }
                    List<Integer> pitches = new ArrayList<>();
                    if (blockIndex > 0) {
                        adjustment += blockChanges.get(blockIndex - 1) * (invertedPattern ? -1 : 1);
                    }
                    //LG.d("Adjustment: " + adjustment);
                    for (int k = 0; k < mb.durations.size(); k++) {
                        int note = mb.notes.get(k);
                        int pitch = startingOct * 12;
                        int combinedNote = startingNote + note;
                        //LG.d("1st combined: " + combinedNote);
                        Pair<Integer, Integer> notePitch = MidiGeneratorUtils
                                .normalizeNotePitch(combinedNote, pitch);
                        combinedNote = notePitch.getLeft();
                        pitch = notePitch.getRight();
                        if (adjustment != 0) {
                            combinedNote = combinedNote + adjustment;
                            notePitch = MidiGeneratorUtils.normalizeNotePitch(combinedNote, pitch);
                            combinedNote = notePitch.getLeft();
                            pitch = notePitch.getRight();
                        }

                        pitch += MidiUtils.MAJ_SCALE.get(combinedNote);
                        //LG.d("Combined note: " + combinedNote + ", pitch: " + pitch);
                        pitches.add(pitch);

                    }

                    List<PhraseNote> customBlock = (customUserDurationsByBlock != null && customUserDurationsByBlock.get(blockIndex) != null)
                            ? customUserDurationsByBlock.get(blockIndex)
                            : null;


                    List<Double> sortedDurs = new ArrayList<>(mb.durations);
                    if (existingPattern == null) {
                        if (gc.isMelodyEmphasizeKey()) {
                            // re-order durations to make most relevant notes the longest
                            for (int k = 0; k < mb.durations.size(); k++) {
                                for (int l = 0; l < mb.durations.size(); l++) {
                                    if (!pitches.get(k).equals(pitches.get(l))) {
                                        boolean swap = false;
                                        if (emphasizeKeyNoteOrder.indexOf(
                                                pitches.get(k) % 12) < emphasizeKeyNoteOrder
                                                .indexOf(pitches.get(l) % 12)) {
                                            swap = sortedDurs.get(k) + DBL_ERR < sortedDurs.get(l);
                                        } else {
                                            swap = sortedDurs.get(k) - DBL_ERR > sortedDurs.get(l);
                                        }
                                        if (swap) {
                                            double temp = sortedDurs.get(k);
                                            sortedDurs.set(k, sortedDurs.get(l));
                                            sortedDurs.set(l, temp);
                                        }
                                    }
                                }
                            }
                            // if rhythm or rhythm+notes
                            if (gc.getMelodyPatternEffect() != 1) {
                                mb.durations = sortedDurs;
                            }
                        }
                    } else if (gc.getMelodyPatternEffect() == 0) {
                        // rhythm only - get from stored melodyblock
                        sortedDurs = new ArrayList<>(
                                existingPattern.getRight().get(blockIndex).durations);
                    }

                    if (customBlock != null) {
                        mb = expansion.convertCustomMelodyBlock(mb, customBlock);
                    }
                    boolean converted = customBlock != null;

                    //LG.d(StringUtils.join(mb.durations, ","));
                    //LG.d("After: " + StringUtils.join(sortedDurs, ","));
                    int validNoteCounter = 0;
                    for (int k = 0; k < mb.durations.size(); k++) {
                        int pitch = mb.notes.get(k) != Pitches.REST ? pitches.get(validNoteCounter) : Pitches.REST;
                        // single note exc. = last note in chord
                        // other exc. = any note first note in block
                        boolean exceptionIndexValid = (gc.isMelodySingleNoteExceptions())
                                ? (k == mb.durations.size() - 1
                                && blockIndex == melodyBlocks.size() - 1)
                                : (k > 0);
                        if (exceptionIndexValid && exceptionCounter > 0
                                && exceptionGenerator.nextInt(100) < EXCEPTION_CHANCE
                                && pitch != Pitches.REST) {
                            int upDown = exceptionGenerator.nextBoolean() ? 1 : -1;
                            int excPitch = MidiUtils.MAJ_SCALE.get(exceptionGenerator
                                    .nextInt(gc.isMelodySingleNoteExceptions() ? 7 : 4));
                            pitch += upDown * excPitch;
                            int closestPitch = MidiUtils.getClosestFromList(MidiUtils.MAJ_SCALE,
                                    pitch % 12);
                            pitch -= pitch % 12;
                            pitch += closestPitch;
                            exceptionCounter--;
                        }

                        double swingDuration = (pitch != Pitches.REST && (!converted || !gc.isMelodyCustomDurationsStrictMode())) ? sortedDurs.get(validNoteCounter) : mb.durations.get(k);
                        if (pitch != Pitches.REST) {
                            validNoteCounter++;
                        }
                        Note n = new Note(pitch, swingDuration);
                        n.setDuration(swingDuration * (0.75 + durationGenerator.nextDouble() / 4)
                                * MidiGenerator.GLOBAL_DURATION_MULTIPLIER);
                        if (embellish
                                && (n.getRhythmValue() > MidiGenerator.Durations.DOTTED_EIGHTH_NOTE - DBL_ERR)) {
                            List<Note> embNotes = expansion.addEmbellishedNotes(n, embellishmentGenerator);
                            noteList.addAll(embNotes);
                            if (fillChordMelodyMap && o == 0) {
                                state.chordMelodyMap1.get(chordIndex).addAll(embNotes);
                            }
                        } else {
                            noteList.add(n);
                            if (fillChordMelodyMap && o == 0) {
                                state.chordMelodyMap1.get(chordIndex).add(n);
                            }
                        }
                    }
                }

            }
        }

        if (fillChordMelodyMap) {
            List<String> chordStrings = MelodyUtils.getChordsFromMelodyPitches(2, mg.progressionDurations, state.chordMelodyMap1,
                    MidiUtils.baseFreqMap);
            int start = 1;
            int end = state.chordMelodyMap1.size() - 1;
            inference.populateMelodyBasedProgression(chordStrings, start, end);
            for (int i = 0; i < start; i++) {
                chordStrings.set(i, mg.getGeneratedChordNames().get(i));
            }
            for (int i = end; i < chordStrings.size(); i++) {
                chordStrings.set(i, mg.getGeneratedChordNames().get(i));
            }
            state.alternateChords = StringUtils.join(chordStrings, ",");
        }
        if (genVars && variations != null) {
            sec.setVariation(0, mp.getAbsoluteOrder(gc.getMelodyParts()), variations);
        }
        return noteList;
    }



    protected List<MelodyBlock> generateMelodyBlocksForDurations(MelodyPart mp, Section sec,
                                                                 List<Double> durations, List<int[]> roots, int melodyBlockGeneratorSeed, int blockOffset,
                                                                 List<Integer> blockChanges, int maxJump, int startingNote, int chordIndex,
                                                                 List<Integer> forcedLengths, int remainingDirChanges, List<Integer> usedMelodyBlockJumpPreference,
                                                                 Map<Integer, List<PhraseNote>> customUserDurationsByBlock, int numBlocks) {
        List<MelodyBlock> mbs = new ArrayList<>();

        //LG.d(StringUtils.join(melodySkeletonDurationWeights, ','));
        int offsettedMelodyGeneratorSeed = melodyBlockGeneratorSeed + blockOffset;
        Random blockNotesGenerator = new Random(offsettedMelodyGeneratorSeed);

        int prevBlockType = Integer.MIN_VALUE;
        int adjustment = startingNote;

        int remainingVariance = 4;

        for (int blockIndex = 0; blockIndex < numBlocks; blockIndex++) {
            if (blockIndex > 0) {
                adjustment += blockChanges.get(blockIndex - 1);
            }
            double blockDuration = !customUserDurationsByBlock.isEmpty()
                    ? customUserDurationsByBlock.get(blockIndex).stream().filter(e -> e.getDynamic() > 0).mapToDouble(PhraseNote::getDuration).sum()
                    : durations.get(blockIndex);

            int speed = MidiGeneratorUtils.adjustChanceParamForTransition(mp.getSpeed(), sec,
                    chordIndex, roots.size(), 40, 0.25, false, false);
            speed = OMNI.clamp(speed, -100, 100);
            int addQuick = (speed - 50) * 2;
            int addSlow = addQuick * -1;
            boolean shortNotes = blockDuration < MidiGenerator.Durations.QUARTER_NOTE - DBL_ERR;
            int[] melodySkeletonDurationWeights = shortNotes
                    ? MelodyUtils.normalizedCumulativeWeights(100 + addQuick, 100 + addQuick, 300 + addQuick,
                    100 + addQuick, 300 + addSlow, 100 + addSlow, 100 + addSlow)
                    : MelodyUtils.normalizedCumulativeWeights(100 + addQuick, 300 + addQuick,
                    100 + addQuick, 300 + addSlow, 100 + addSlow, 100 + addSlow);

            Rhythm blockRhythm = new Rhythm(offsettedMelodyGeneratorSeed + blockIndex,
                    blockDuration,
                    shortNotes ? MelodyGenerator.MELODY_SKELETON_DURATIONS_SHORT : MelodyGenerator.MELODY_SKELETON_DURATIONS,
                    melodySkeletonDurationWeights);
            //int length = blockNotesGenerator.nextInt(100) < gc.getMelodyQuickness() ? 4 : 3;

            blockNotesGenerator.setSeed(offsettedMelodyGeneratorSeed + blockIndex);
            Random generateNewBlocksDecider = new Random(offsettedMelodyGeneratorSeed + blockIndex);
            boolean GENERATE_NEW_BLOCKS = generateNewBlocksDecider.nextInt(100) < gc
                    .getMelodyNewBlocksChance();

            Integer forcedBlockLength = (forcedLengths != null) ? forcedLengths.get(blockIndex) : null;
            if (forcedBlockLength == null && !customUserDurationsByBlock.isEmpty()) {
                forcedBlockLength = (int) customUserDurationsByBlock.get(blockIndex).stream().filter(e -> e.getDynamic() > 0).count();
            }
            Pair<Integer, Integer[]> typeBlock = (GENERATE_NEW_BLOCKS)
                    ? MelodyUtils.generateBlockByBlockChangeAndLength(blockChanges.get(blockIndex),
                    maxJump, blockNotesGenerator,
                    forcedBlockLength,
                    remainingVariance, remainingDirChanges)
                    : MelodyUtils.getRandomByApproxBlockChangeAndLength(
                    blockChanges.get(blockIndex), maxJump, blockNotesGenerator,
                    forcedBlockLength,
                    remainingVariance, remainingDirChanges, usedMelodyBlockJumpPreference, gc.getMelodyBlockTypePreference());
            Integer[] blockNotesArray = typeBlock.getRight();
            int blockType = typeBlock.getLeft();

            boolean chordyBlockNotMatchingChord = false;
            if (blockType == 3) {
                int blockStart = (adjustment + 70) % 7;
                chordyBlockNotMatchingChord = !MelodyUtils.cMajorSubstituteNotes
                        .contains(blockStart);
                if (chordyBlockNotMatchingChord) {
                    LG.d("SWAPPING CHORDY BLOCK, blockStart: " + blockStart);
                }
            }

            // try to find a different type for this block change (only for static/non generated blocks)
            if (blockType != Integer.MAX_VALUE && (blockType == prevBlockType || chordyBlockNotMatchingChord)) {
                int length = blockNotesArray.length;
                List<Integer> typesToChoose = new ArrayList<>();
                for (int j = 0; j < BlockType.values().length; j++) {
                    if (j != blockType && BlockType.AVAILABLE_BLOCK_CHANGES_PER_TYPE.get(j)
                            .contains(Math.abs(blockChanges.get(blockIndex)))) {
                        typesToChoose.add(j);
                    }
                }
                if (!typesToChoose.isEmpty()) {
                    int randomType = BlockType.getWeightedType(typesToChoose, gc.getMelodyBlockTypePreference(), blockNotesGenerator.nextInt(100));
                    Integer[] typedBlock = MelodyUtils.getRandomForTypeAndBlockChangeAndLength(
                            randomType, blockChanges.get(blockIndex), length, blockNotesGenerator,
                            0);
                    if (typedBlock != null) {
                        blockNotesArray = typedBlock;
                        blockType = randomType;
                        LG.d("Found new block!");
                    } else {
                        LG.d("Different block not found in other types!");
                    }
                } else {
                    LG.d("Other types don't have this block!");
                }


            }
            remainingVariance = Math.max(0,
                    remainingVariance - MelodyUtils.variance(blockNotesArray));
            remainingDirChanges = Math.max(0,
                    remainingDirChanges - MelodyUtils.interblockDirectionChange(blockNotesArray));
            List<Integer> blockNotes = Arrays.asList(blockNotesArray);
            List<Double> blockDurations = !customUserDurationsByBlock.isEmpty() && customUserDurationsByBlock.get(blockIndex).size() == blockNotes.size()
                    ? customUserDurationsByBlock.get(blockIndex).stream().filter(e -> e.getDynamic() > 0).map(PhraseNote::getDuration).collect(Collectors.toList())
                    : blockRhythm.makeDurations(blockNotes.size(), mp.getSpeed() < 20 ? MidiGenerator.Durations.QUARTER_NOTE : MidiGenerator.Durations.SIXTEENTH_NOTE);


            if (gc.isMelodyArpySurprises() && (blockNotes.size() == 4)
                    && (mp.getSpeed() < 20 || mp.getSpeed() > 80)) {
                double wrongNoteLow = (mp.getSpeed() < 20) ? MidiGenerator.Durations.SIXTEENTH_NOTE * 0.99
                        : MidiGenerator.Durations.DOTTED_QUARTER_NOTE * 0.99;
                double wrongNoteHigh = (mp.getSpeed() < 20) ? MidiGenerator.Durations.SIXTEENTH_NOTE * 1.01
                        : MidiGenerator.Durations.WHOLE_NOTE * 1.01;
                boolean containsWrongNote = blockDurations.stream()
                        .anyMatch(e -> (e > wrongNoteLow && e < wrongNoteHigh));
                if (containsWrongNote) {
                    double arpyDuration = blockDuration / blockNotes.size();
                    for (int j = 0; j < blockDurations.size(); j++) {
                        blockDurations.set(j, arpyDuration);
                    }
                    LG.d("Arpy surprise for block#: " + blockNotes.size() + ", duration: "
                            + durations.get(blockIndex));
                }

            }

            LG.d("Block durations: " + StringUtils.join(blockDurations, ","));
            prevBlockType = blockType;
            //LG.d("Block Durations size: " + blockDurations.size());
            MelodyBlock mb = new MelodyBlock(blockNotes, blockDurations, false);
            mbs.add(mb);
            LG.d("Created block: " + StringUtils.join(blockNotes, ","));
        }
        return mbs;
    }
}
