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
    static final class SkeletonGenerationRequest {
        final MelodyPart melodyPart;
        final List<int[]> chords;
        final List<int[]> roots;
        final int measures;
        final int notesSeedOffset;
        final Section section;
        final List<Integer> variations;
        final List<Integer> melodyBlockJumpPreference;
        final List<Double> progressionDurations;
        final List<String> generatedChordNames;
        final List<int[]> currentRootProgression;
        final List<int[]> currentChordProgression;
        final MidiUtils.ScaleMode modifiedScale;

        SkeletonGenerationRequest(MelodyPart melodyPart, List<int[]> chords, List<int[]> roots,
                int measures, int notesSeedOffset, Section section, List<Integer> variations,
                List<Integer> melodyBlockJumpPreference, List<Double> progressionDurations,
                List<String> generatedChordNames, List<int[]> currentRootProgression,
                List<int[]> currentChordProgression, MidiUtils.ScaleMode modifiedScale) {
            this.melodyPart = melodyPart;
            this.chords = chords;
            this.roots = roots;
            this.measures = measures;
            this.notesSeedOffset = notesSeedOffset;
            this.section = section;
            this.variations = variations;
            this.melodyBlockJumpPreference = melodyBlockJumpPreference;
            this.progressionDurations = progressionDurations;
            this.generatedChordNames = generatedChordNames;
            this.currentRootProgression = currentRootProgression;
            this.currentChordProgression = currentChordProgression;
            this.modifiedScale = modifiedScale;
        }
    }

    /** Prepared, per-call inputs; random streams keep the seeds and first uses of the old flow. */
    private static final class SkeletonGenerationSetup {
        final boolean generateVariations;
        final boolean fillChordMelodyMap;
        final int notesSeedOffset;
        final int seed;
        final int melodyBlockGeneratorSeed;
        final List<Integer> blockSeedOffsets;
        final List<Integer> blockChordNoteChoices;
        final List<Integer> usedMelodyBlockJumpPreference;
        final Map<Integer, Pair<Pair<List<Integer>, Integer>, List<MelodyBlock>>> changesAndBlocksMap = new HashMap<>();
        final Map<Integer, List<Double>> blockDurationsMap = new HashMap<>();
        final Random pitchPickerGenerator;
        final Random exceptionGenerator;
        final Random sameRhythmGenerator;
        final Random alternateRhythmGenerator;
        final Random durationGenerator;
        final Random embellishmentGenerator;
        final Random soloGenerator;
        final double[] melodySkeletonDurations;
        final List<int[]> usedChords;
        final List<Integer> emphasizeKeyNoteOrder;

        SkeletonGenerationSetup(boolean generateVariations, boolean fillChordMelodyMap,
                int notesSeedOffset, int seed, int melodyBlockGeneratorSeed,
                List<Integer> blockSeedOffsets, List<Integer> blockChordNoteChoices,
                List<Integer> usedMelodyBlockJumpPreference, Random pitchPickerGenerator,
                Random exceptionGenerator, Random sameRhythmGenerator,
                Random alternateRhythmGenerator, Random durationGenerator,
                Random embellishmentGenerator, Random soloGenerator,
                double[] melodySkeletonDurations, List<int[]> usedChords,
                List<Integer> emphasizeKeyNoteOrder) {
            this.generateVariations = generateVariations;
            this.fillChordMelodyMap = fillChordMelodyMap;
            this.notesSeedOffset = notesSeedOffset;
            this.seed = seed;
            this.melodyBlockGeneratorSeed = melodyBlockGeneratorSeed;
            this.blockSeedOffsets = blockSeedOffsets;
            this.blockChordNoteChoices = blockChordNoteChoices;
            this.usedMelodyBlockJumpPreference = usedMelodyBlockJumpPreference;
            this.pitchPickerGenerator = pitchPickerGenerator;
            this.exceptionGenerator = exceptionGenerator;
            this.sameRhythmGenerator = sameRhythmGenerator;
            this.alternateRhythmGenerator = alternateRhythmGenerator;
            this.durationGenerator = durationGenerator;
            this.embellishmentGenerator = embellishmentGenerator;
            this.soloGenerator = soloGenerator;
            this.melodySkeletonDurations = melodySkeletonDurations;
            this.usedChords = usedChords;
            this.emphasizeKeyNoteOrder = emphasizeKeyNoteOrder;
        }
    }

    private static final class ChordRhythmSelection {
        final List<Double> durations;
        final boolean badDuration;
        final boolean doubledRhythm;

        ChordRhythmSelection(List<Double> durations, boolean badDuration,
                boolean doubledRhythm) {
            this.durations = durations;
            this.badDuration = badDuration;
            this.doubledRhythm = doubledRhythm;
        }
    }

    /** Selected pattern and chord coordinates consumed by note assembly. */
    private static final class ChordAssemblyRequest {
        final List<MelodyBlock> melodyBlocks;
        final List<Integer> blockChanges;
        final Pair<Pair<List<Integer>, Integer>, List<MelodyBlock>> existingPattern;
        final Map<Integer, List<PhraseNote>> customUserDurationsByBlock;
        final int chordIndex;
        final int measureIndex;
        final int originalBlockOffset;
        final int startingNote;
        final int startingOctave;
        final boolean embellish;
        final int exceptionChance;

        ChordAssemblyRequest(List<MelodyBlock> melodyBlocks, List<Integer> blockChanges,
                Pair<Pair<List<Integer>, Integer>, List<MelodyBlock>> existingPattern,
                Map<Integer, List<PhraseNote>> customUserDurationsByBlock, int chordIndex,
                int measureIndex, int originalBlockOffset, int startingNote,
                int startingOctave, boolean embellish, int exceptionChance) {
            this.melodyBlocks = melodyBlocks;
            this.blockChanges = blockChanges;
            this.existingPattern = existingPattern;
            this.customUserDurationsByBlock = customUserDurationsByBlock;
            this.chordIndex = chordIndex;
            this.measureIndex = measureIndex;
            this.originalBlockOffset = originalBlockOffset;
            this.startingNote = startingNote;
            this.startingOctave = startingOctave;
            this.embellish = embellish;
            this.exceptionChance = exceptionChance;
        }
    }

    /** Blocks and conversion data selected for one chord before note assembly. */
    private static final class SelectedChordBlocks {
        final Pair<Pair<List<Integer>, Integer>, List<MelodyBlock>> existingPattern;
        final Map<Integer, List<PhraseNote>> customUserDurationsByBlock;
        final List<Integer> blockChanges;
        final List<MelodyBlock> melodyBlocks;
        final int startingNote;
        final int startingOctave;

        SelectedChordBlocks(
                Pair<Pair<List<Integer>, Integer>, List<MelodyBlock>> existingPattern,
                Map<Integer, List<PhraseNote>> customUserDurationsByBlock,
                List<Integer> blockChanges, List<MelodyBlock> melodyBlocks,
                int startingNote, int startingOctave) {
            this.existingPattern = existingPattern;
            this.customUserDurationsByBlock = customUserDurationsByBlock;
            this.blockChanges = blockChanges;
            this.melodyBlocks = melodyBlocks;
            this.startingNote = startingNote;
            this.startingOctave = startingOctave;
        }
    }

    private static final class BlockShape {
        final int type;
        final Integer[] notes;

        private BlockShape(int type, Integer[] notes) {
            this.type = type;
            this.notes = notes;
        }
    }

    private final GUIConfig gc;
    private final MidiTiming timing;
    private final double globalDurationMultiplier;
    private final MelodyGenerationState state;
    private final MelodyChordInference inference;
    private final MelodyExpansion expansion;
    private final MelodyGenerationSettings settings;

    MelodyBlockSkeletonGenerator(GUIConfig gc, MidiTiming timing, double globalDurationMultiplier,
            MelodyGenerationState state,
            MelodyChordInference inference, MelodyExpansion expansion,
			MelodyGenerationSettings settings) {
        this.gc = gc;
        this.timing = timing;
        this.globalDurationMultiplier = globalDurationMultiplier;
        this.state = state;
        this.inference = inference;
        this.expansion = expansion;
		this.settings = settings;
    }

    /**
     * Input: one melody, progression, section, and its generation settings.
     * Output: effective chords, target choices, and seeded streams for the chord loop.
     */
    private SkeletonGenerationSetup prepareSkeletonGeneration(
            SkeletonGenerationRequest request) {
        MelodyPart mp = request.melodyPart;
        List<int[]> chords = request.chords;
        List<int[]> roots = request.roots;
        Section sec = request.section;
        int notesSeedOffset = request.notesSeedOffset;
        boolean generateVariations = request.variations == null;
        boolean fillChordMelodyMap = state.chordMelodyMap1.isEmpty() && notesSeedOffset == 0
                && roots.size() == request.generatedChordNames.size();

        if (sec.getSectionVariations() != null && sec.isSectionVar(2)) {
            notesSeedOffset += 100;
        }
        int seed = mp.getPatternSeedWithPartOffset();
        int melodyBlockGeneratorSeed = seed + notesSeedOffset;
        LG.d("Seed: " + seed);

        List<Integer> blockSeedOffsets = (mp.getMelodyPatternOffsets() != null)
                ? mp.getMelodyPatternOffsets()
                : new ArrayList<>(Arrays.asList(1, 2, 1, 3));
        while (blockSeedOffsets.size() < chords.size()) {
            blockSeedOffsets.addAll(blockSeedOffsets);
        }

        List<Integer> blockChordNoteChoices;
        Map<Integer, List<Integer>> targetNotes = settings.getOrCreateTargetNotes();
        if (settings.isRandomizeTargetNotes()) {
            if (gc.getActualArrangement().getSections().indexOf(sec) < 1
                    || targetNotes.get(mp.getOrderOffset()) == null) {
                int targetNoteSeed = gc.isMelody1ForcePatterns()
                        ? seed + 1 : seed + mp.getOrderOffset();
                blockChordNoteChoices = MidiGeneratorUtils.generateNoteTargetOffsets(roots,
                        targetNoteSeed, gc.getMelodyBlockTargetMode(),
                        gc.getMelodyTargetNoteVariation(), gc.getNoteTargetDirectionChoice(),
                        gc.isMelodyUseDirectionsFromProgression());
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

        int pitchPickerOffset = notesSeedOffset;
        int rhythmOffset = notesSeedOffset;
        int firstBlockOffset = Math.abs(blockSeedOffsets.get(0));
        if (firstBlockOffset > 0) {
            firstBlockOffset--;
        }
        List<Integer> usedMelodyBlockJumpPreference =
                request.melodyBlockJumpPreference != null
                        && request.melodyBlockJumpPreference.size()
                                == MelodyUtils.BLOCK_CHANGE_JUMP_PREFERENCE.size()
                        && MelodyUtils.BLOCK_CHANGE_JUMP_PREFERENCE
                                .containsAll(request.melodyBlockJumpPreference)
                        ? request.melodyBlockJumpPreference : Collections.emptyList();

        Random pitchPickerGenerator = new Random(seed + pitchPickerOffset + firstBlockOffset);
        Random exceptionGenerator = new Random(melodyBlockGeneratorSeed + 2 + firstBlockOffset);
        Random sameRhythmGenerator = new Random(seed + 3 + firstBlockOffset);
        Random alternateRhythmGenerator = new Random(seed + 4);
        Random durationGenerator = new Random(melodyBlockGeneratorSeed + 5);
        Random embellishmentGenerator = new Random(melodyBlockGeneratorSeed + 10);
        Random soloGenerator = new Random(melodyBlockGeneratorSeed + 25);

        double[] melodySkeletonDurations = { timing.quarterNote, timing.halfNote,
                timing.dottedHalfNote, timing.wholeNote };
        List<int[]> usedChords;
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

        MidiUtils.ScaleMode scale = (request.modifiedScale != null)
                ? request.modifiedScale : gc.getScaleMode();
        List<Integer> emphasizeKeyNoteOrder = new ArrayList<>(MidiUtils.keyEmphasisOrder);
        if (scale != null && scale.modeTargetNote >= 0) {
            Integer ionianTargetNote = MidiUtils.MAJ_SCALE.get(scale.modeTargetNote);
            emphasizeKeyNoteOrder.remove(ionianTargetNote);
            emphasizeKeyNoteOrder.add(1, ionianTargetNote);
        }
        return new SkeletonGenerationSetup(generateVariations, fillChordMelodyMap,
                notesSeedOffset, seed, melodyBlockGeneratorSeed, blockSeedOffsets,
                blockChordNoteChoices, usedMelodyBlockJumpPreference, pitchPickerGenerator,
                exceptionGenerator, sameRhythmGenerator, alternateRhythmGenerator,
                durationGenerator, embellishmentGenerator, soloGenerator,
                melodySkeletonDurations, usedChords, emphasizeKeyNoteOrder);
    }

    /** Input: one chord's progression length and repeat slot; output: its selected rhythm. */
    private ChordRhythmSelection selectChordRhythm(MelodyPart mp, Section sec, int chordIndex,
            int chordCount, List<Double> progressionDurations, int patternEffect,
            Map<Integer, List<Double>> blockDurationsMap, int blockOffset, int rhythmSeedBase,
            double[] melodySkeletonDurations, Random sameRhythmGenerator,
            int sameRhythmChance) {
        List<Double> durations = (patternEffect != 1)
                ? blockDurationsMap.get(blockOffset) : null;
        boolean badDuration = false;
        if (durations != null
                && !MidiUtils.roughlyEqual(durations.stream().mapToDouble(e -> e).sum(),
                        progressionDurations.get(chordIndex))) {
            durations = null;
            badDuration = true;
        }
        boolean sameRhythmTwice = sameRhythmGenerator.nextInt(100) < sameRhythmChance;
        int rhythmSeed = rhythmSeedBase + blockOffset;
        if (durations == null) {
            double rhythmDuration = sameRhythmTwice
                    ? progressionDurations.get(chordIndex) / 2.0
                    : progressionDurations.get(chordIndex);
            int speed = MidiGeneratorUtils.adjustChanceParamForTransition(mp.getSpeed(), sec,
                    chordIndex, chordCount, 40, 0.25, false, false);
            speed = OMNI.clamp(speed, -100, 100);
            int addQuick = (speed - 50) * 4;
            int addSlow = addQuick * -1;
            int[] melodySkeletonDurationWeights = MelodyUtils.normalizedCumulativeWeights(
                    200 + addQuick, Math.max(1, 200 + addQuick / 2),
                    200 + addQuick, 200 + addSlow);
            Rhythm rhythm = new Rhythm(rhythmSeed, rhythmDuration, melodySkeletonDurations,
                    melodySkeletonDurationWeights);
            durations = rhythm.regenerateDurations(10, melodySkeletonDurations[0]);
            if (sameRhythmTwice) {
                durations.addAll(durations);
            }
            blockDurationsMap.put(blockOffset, durations);
        }
        return new ChordRhythmSelection(durations, badDuration, sameRhythmTwice);
    }

    Vector<Note> generateMelodyBlockSkeletonFromChords(SkeletonGenerationRequest request) {
        MelodyPart mp = request.melodyPart;
        List<int[]> chords = request.chords;
        List<int[]> roots = request.roots;
        int measures = request.measures;
        Section sec = request.section;
        List<Integer> variations = request.variations;
        List<Double> progressionDurations = request.progressionDurations;
        SkeletonGenerationSetup setup = prepareSkeletonGeneration(request);
        boolean genVars = setup.generateVariations;
        boolean fillChordMelodyMap = setup.fillChordMelodyMap;
        int notesSeedOffset = setup.notesSeedOffset;
        int seed = setup.seed;
        int pitchPickerOffset = notesSeedOffset;
        int rhythmOffset = notesSeedOffset;
        List<Integer> blockSeedOffsets = setup.blockSeedOffsets;
        Map<Integer, List<Double>> blockDurationsMap = setup.blockDurationsMap;
        Random pitchPickerGenerator = setup.pitchPickerGenerator;
        Random exceptionGenerator = setup.exceptionGenerator;
        Random sameRhythmGenerator = setup.sameRhythmGenerator;
        Random soloGenerator = setup.soloGenerator;
        double[] melodySkeletonDurations = setup.melodySkeletonDurations;
        List<int[]> usedChords = setup.usedChords;

        int MAX_JUMP_SKELETON_CHORD = mp.getBlockJump();
        int SAME_RHYTHM_CHANCE = mp.getDoubledRhythmChance();
        int EXCEPTION_CHANCE = mp.getNoteExceptionChance();
        Vector<Note> noteList = new Vector<>();

        int maxBlockChangeAdjustment = 0;
        boolean embellish = false;
        for (int o = 0; o < measures; o++) {

            for (int chordIndex = 0; chordIndex < usedChords.size(); chordIndex++) {
                // either after first measure, or after first half of combined chord prog

                if (genVars && (chordIndex == 0)) {
                    variations = MidiGenerator.fillVariations(gc, sec, mp, variations, 0);
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

                ChordRhythmSelection rhythmSelection = selectChordRhythm(mp, sec, chordIndex,
                        chords.size(), progressionDurations, gc.getMelodyPatternEffect(),
                        blockDurationsMap, blockOffset, seed + rhythmOffset,
                        melodySkeletonDurations, sameRhythmGenerator, SAME_RHYTHM_CHANCE);
                List<Double> durations = rhythmSelection.durations;
                boolean badDuration = rhythmSelection.badDuration;
                boolean sameRhythmTwice = rhythmSelection.doubledRhythm;
                LG.d("Overall Block Durations: " + StringUtils.join(durations, ",")
                        + ", Doubled rhythm: " + sameRhythmTwice);
                SelectedChordBlocks selected = selectBlocksForChord(request, setup, chordIndex,
                        blockSeedOffsets, blockOffset, originalBlockOffset, durations, badDuration,
                        maxBlockChangeAdjustment, MAX_JUMP_SKELETON_CHORD);
                Pair<Pair<List<Integer>, Integer>, List<MelodyBlock>> existingPattern =
                        selected.existingPattern;
                Map<Integer, List<PhraseNote>> customUserDurationsByBlock =
                        selected.customUserDurationsByBlock;
                List<Integer> blockChanges = selected.blockChanges;
                List<MelodyBlock> melodyBlocks = selected.melodyBlocks;
                int startingNote = selected.startingNote;
                int startingOct = selected.startingOctave;
                ChordAssemblyRequest assembly = new ChordAssemblyRequest(melodyBlocks,
                        blockChanges, existingPattern, customUserDurationsByBlock, chordIndex, o,
                        originalBlockOffset, startingNote, startingOct, embellish, EXCEPTION_CHANCE);
                assembleChordNotes(request, setup, assembly, noteList);

            }
        }

        publishSkeletonResults(request, setup, variations);
        return noteList;
    }

    /**
     * Input: one chord's rhythm, pattern offset, and progression data.
     * Output: reusable or newly generated blocks plus the durations needed to assemble them.
     */
    private SelectedChordBlocks selectBlocksForChord(SkeletonGenerationRequest request,
            SkeletonGenerationSetup setup, int chordIndex, List<Integer> blockSeedOffsets,
            int blockOffset,
            int originalBlockOffset, List<Double> durations, boolean badDuration,
            int maxBlockChangeAdjustment, int maxJump) {
        MelodyPart mp = request.melodyPart;
        Section sec = request.section;
        List<int[]> roots = request.roots;
        int chord1 = MidiGeneratorUtils.getStartingNote(roots, setup.blockChordNoteChoices,
                chordIndex, gc.getMelodyBlockTargetMode());
        int chord2 = MidiGeneratorUtils.getStartingNote(roots, setup.blockChordNoteChoices,
                chordIndex + 1, gc.getMelodyBlockTargetMode());
        int startingOct = chord1 / 7;
        Pair<Pair<List<Integer>, Integer>, List<MelodyBlock>> existingPattern = badDuration
                ? null : setup.changesAndBlocksMap.get(blockOffset);
        int remainingDirChanges = gc.getMelodyMaxDirChanges();

        int originalBlockOffsetIndex = originalBlockOffset < 0
                ? blockSeedOffsets.indexOf(originalBlockOffset * -1)
                : blockSeedOffsets.indexOf(originalBlockOffset);
        Map<Integer, List<PhraseNote>> customUserDurationsByBlock =
                expansion.convertCustomUserDurations(mp, setup.melodyBlockGeneratorSeed,
                        chordIndex,
                        gc.isMelodyCustomDurationsRandomWeighting() && existingPattern != null
                                ? originalBlockOffsetIndex : blockOffset,
                        sec, request.progressionDurations);
        int numBlocks = !customUserDurationsByBlock.isEmpty()
                ? customUserDurationsByBlock.size() : durations.size();

        Pair<List<Integer>, Integer> blockChangesPair;
        if (existingPattern != null && gc.getMelodyPatternEffect() > 0) {
            blockChangesPair = existingPattern.getLeft();
        } else {
            blockChangesPair = MelodyUtils.blockChangeSequence(chord1, chord2,
                    setup.melodyBlockGeneratorSeed, numBlocks,
                    OMNI.clamp(mp.getMaxBlockChange() + maxBlockChangeAdjustment, 0, 7),
                    remainingDirChanges);
        }
        remainingDirChanges -= blockChangesPair.getRight();
        List<Integer> blockChanges = blockChangesPair.getLeft();
        boolean flexiblePattern = mp.isPatternFlexible() && blockChanges.size() > 1;
        if (flexiblePattern && existingPattern != null) {
            int blockChangesSum = blockChanges.stream().mapToInt(e -> e).sum();
            int newLastBlockChange = blockChanges.get(blockChanges.size() - 1);
            newLastBlockChange += (chord2 - chord1) - blockChangesSum;
            List<Integer> newBlockChanges = new ArrayList<>(blockChanges);
            newBlockChanges.set(newBlockChanges.size() - 1, newLastBlockChange);
            blockChanges = newBlockChanges;
        }
        LG.d("Block changes: " + blockChanges);
        int startingNote = chord1 % 7;

        List<Integer> forcedLengths = existingPattern != null
                && gc.getMelodyPatternEffect() != 1
                        ? existingPattern.getRight().stream().map(e -> e.durations.size())
                                .collect(Collectors.toList())
                        : null;
        List<MelodyBlock> melodyBlocks = existingPattern != null
                && gc.getMelodyPatternEffect() > 0 && !flexiblePattern
                        ? existingPattern.getRight()
                        : generateMelodyBlocksForDurations(mp, sec, durations, roots,
                                setup.melodyBlockGeneratorSeed, blockOffset, blockChanges,
                                maxJump, startingNote, chordIndex, forcedLengths,
                                remainingDirChanges, setup.usedMelodyBlockJumpPreference,
                                customUserDurationsByBlock, numBlocks);
        if (flexiblePattern && existingPattern != null && gc.getMelodyPatternEffect() > 0) {
            List<MelodyBlock> storedMelodyBlocks = new ArrayList<>(existingPattern.getRight());
            LG.d("HALF PATTERN - set last melody block to newly generated block! Sizes equal?: "
                    + (storedMelodyBlocks.size() == melodyBlocks.size()));
            storedMelodyBlocks.set(storedMelodyBlocks.size() - 1,
                    melodyBlocks.get(melodyBlocks.size() - 1));
            melodyBlocks = storedMelodyBlocks;
        }

        if (existingPattern == null) {
            LG.n("Stored pattern: " + blockOffset + ", for chord index:" + chordIndex
                    + ", Pattern effect: " + gc.getMelodyPatternEffect());
            setup.changesAndBlocksMap.put(blockOffset, Pair.of(blockChangesPair, melodyBlocks));
        } else {
            LG.n("Loaded pattern: " + blockOffset + ", for chord index:" + chordIndex
                    + ", Pattern effect: " + gc.getMelodyPatternEffect());
        }
        return new SelectedChordBlocks(existingPattern, customUserDurationsByBlock,
                blockChanges, melodyBlocks, startingNote, startingOct);
    }

    /** Converts selected blocks into ordered notes and updates the first-pass chord map. */
    private void assembleChordNotes(SkeletonGenerationRequest request,
            SkeletonGenerationSetup setup, ChordAssemblyRequest assembly,
            Vector<Note> noteList) {
        MelodyPart mp = request.melodyPart;
        List<MelodyBlock> melodyBlocks = assembly.melodyBlocks;
        List<Integer> blockChanges = assembly.blockChanges;
        Pair<Pair<List<Integer>, Integer>, List<MelodyBlock>> existingPattern = assembly.existingPattern;
        Map<Integer, List<PhraseNote>> customUserDurationsByBlock = assembly.customUserDurationsByBlock;
        int chordIndex = assembly.chordIndex;
        int o = assembly.measureIndex;
        int originalBlockOffset = assembly.originalBlockOffset;
        int startingNote = assembly.startingNote;
        int startingOct = assembly.startingOctave;
        boolean embellish = assembly.embellish;
        int EXCEPTION_CHANCE = assembly.exceptionChance;
        boolean fillChordMelodyMap = setup.fillChordMelodyMap;
        List<Integer> emphasizeKeyNoteOrder = setup.emphasizeKeyNoteOrder;
        Random exceptionGenerator = setup.exceptionGenerator;
        Random durationGenerator = setup.durationGenerator;
        Random embellishmentGenerator = setup.embellishmentGenerator;
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
                        * globalDurationMultiplier);
                if (embellish
                        && (n.getRhythmValue() > timing.dottedEighthNote - DBL_ERR)) {
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

    /** Publishes progression inference and generated variations after skeleton assembly. */
    private void publishSkeletonResults(SkeletonGenerationRequest request,
            SkeletonGenerationSetup setup, List<Integer> variations) {
        if (setup.fillChordMelodyMap) {
            List<String> chordStrings = MelodyUtils.getChordsFromMelodyPitches(2,
                    request.progressionDurations, state.chordMelodyMap1,
                    MidiUtils.baseFreqMap, timing);
            int start = 1;
            int end = state.chordMelodyMap1.size() - 1;
            inference.populateMelodyBasedProgression(chordStrings, start, end,
                    request.currentRootProgression, request.currentChordProgression);
            for (int i = 0; i < start; i++) {
                chordStrings.set(i, request.generatedChordNames.get(i));
            }
            for (int i = end; i < chordStrings.size(); i++) {
                chordStrings.set(i, request.generatedChordNames.get(i));
            }
            state.alternateChords = StringUtils.join(chordStrings, ",");
        }
        if (setup.generateVariations && variations != null) {
            request.section.setVariation(0,
                    request.melodyPart.getAbsoluteOrder(gc.getMelodyParts()), variations);
        }
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
            double blockDuration = getBlockDuration(blockIndex, durations,
                    customUserDurationsByBlock);
            Rhythm blockRhythm = createBlockRhythm(mp, sec, roots.size(), chordIndex,
                    offsettedMelodyGeneratorSeed + blockIndex, blockDuration);
            //int length = blockNotesGenerator.nextInt(100) < gc.getMelodyQuickness() ? 4 : 3;

            int blockSeed = offsettedMelodyGeneratorSeed + blockIndex;
            boolean generateNewBlocks = shouldGenerateNewBlock(blockNotesGenerator, blockSeed);
            Integer forcedBlockLength = getForcedBlockLength(blockIndex, forcedLengths,
                    customUserDurationsByBlock);
            BlockShape blockShape = selectBlockShape(blockIndex, blockChanges, maxJump,
                    blockNotesGenerator, generateNewBlocks, forcedBlockLength,
                    remainingVariance, remainingDirChanges,
                    usedMelodyBlockJumpPreference, adjustment, prevBlockType);
            Integer[] blockNotesArray = blockShape.notes;
            int blockType = blockShape.type;
            remainingVariance = Math.max(0,
                    remainingVariance - MelodyUtils.variance(blockNotesArray));
            remainingDirChanges = Math.max(0,
                    remainingDirChanges - MelodyUtils.interblockDirectionChange(blockNotesArray));
            List<Integer> blockNotes = Arrays.asList(blockNotesArray);
            List<Double> blockDurations = reconcileBlockDurations(blockIndex, blockNotes,
                    customUserDurationsByBlock, blockRhythm, mp);


            if (gc.isMelodyArpySurprises() && (blockNotes.size() == 4)
                    && (mp.getSpeed() < 20 || mp.getSpeed() > 80)) {
                double wrongNoteLow = (mp.getSpeed() < 20) ? timing.sixteenthNote * 0.99
                        : timing.dottedQuarterNote * 0.99;
                double wrongNoteHigh = (mp.getSpeed() < 20) ? timing.sixteenthNote * 1.01
                        : timing.wholeNote * 1.01;
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

    private double getBlockDuration(int blockIndex, List<Double> durations,
            Map<Integer, List<PhraseNote>> customUserDurationsByBlock) {
        return !customUserDurationsByBlock.isEmpty()
                ? customUserDurationsByBlock.get(blockIndex).stream()
                        .filter(note -> note.getDynamic() > 0)
                        .mapToDouble(PhraseNote::getDuration).sum()
                : durations.get(blockIndex);
    }

    private Rhythm createBlockRhythm(MelodyPart mp, Section sec, int rootCount, int chordIndex,
            int blockSeed, double blockDuration) {
        int speed = MidiGeneratorUtils.adjustChanceParamForTransition(mp.getSpeed(), sec,
                chordIndex, rootCount, 40, 0.25, false, false);
        speed = OMNI.clamp(speed, -100, 100);
        int addQuick = (speed - 50) * 2;
        int addSlow = addQuick * -1;
        boolean shortNotes = blockDuration < timing.quarterNote - DBL_ERR;
        int[] durationWeights = shortNotes
                ? MelodyUtils.normalizedCumulativeWeights(100 + addQuick, 100 + addQuick,
                        300 + addQuick, 100 + addQuick, 300 + addSlow,
                        100 + addSlow, 100 + addSlow)
                : MelodyUtils.normalizedCumulativeWeights(100 + addQuick, 300 + addQuick,
                        100 + addQuick, 300 + addSlow, 100 + addSlow, 100 + addSlow);
        return new Rhythm(blockSeed, blockDuration,
                shortNotes ? timing.getShortMelodySkeletonDurations()
                        : timing.getMelodySkeletonDurations(),
                durationWeights);
    }

    private Integer getForcedBlockLength(int blockIndex, List<Integer> forcedLengths,
            Map<Integer, List<PhraseNote>> customUserDurationsByBlock) {
        Integer forcedBlockLength = (forcedLengths != null) ? forcedLengths.get(blockIndex) : null;
        if (forcedBlockLength == null && !customUserDurationsByBlock.isEmpty()) {
            forcedBlockLength = (int) customUserDurationsByBlock.get(blockIndex).stream()
                    .filter(note -> note.getDynamic() > 0).count();
        }
        return forcedBlockLength;
    }

    private boolean shouldGenerateNewBlock(Random blockNotesGenerator, int blockSeed) {
        blockNotesGenerator.setSeed(blockSeed);
        Random generateNewBlocksDecider = new Random(blockSeed);
        return generateNewBlocksDecider.nextInt(100) < gc.getMelodyNewBlocksChance();
    }

    private BlockShape selectBlockShape(int blockIndex, List<Integer> blockChanges, int maxJump,
            Random blockNotesGenerator, boolean generateNewBlocks, Integer forcedBlockLength,
            int remainingVariance, int remainingDirChanges,
            List<Integer> usedMelodyBlockJumpPreference, int adjustment, int previousBlockType) {
        int blockChange = blockChanges.get(blockIndex);
        Pair<Integer, Integer[]> selected = generateNewBlocks
                ? MelodyUtils.generateBlockByBlockChangeAndLength(blockChange, maxJump,
                        blockNotesGenerator, forcedBlockLength, remainingVariance,
                        remainingDirChanges)
                : MelodyUtils.getRandomByApproxBlockChangeAndLength(blockChange, maxJump,
                        blockNotesGenerator, forcedBlockLength, remainingVariance,
                        remainingDirChanges, usedMelodyBlockJumpPreference,
                        gc.getMelodyBlockTypePreference());
        Integer[] blockNotes = selected.getRight();
        int blockType = selected.getLeft();

        boolean chordyBlockNotMatchingChord = false;
        if (blockType == 3) {
            int blockStart = (adjustment + 70) % 7;
            chordyBlockNotMatchingChord = !MelodyUtils.cMajorSubstituteNotes.contains(blockStart);
            if (chordyBlockNotMatchingChord) {
                LG.d("SWAPPING CHORDY BLOCK, blockStart: " + blockStart);
            }
        }

        // try to find a different type for this block change (only for static/non generated blocks)
        if (blockType != Integer.MAX_VALUE
                && (blockType == previousBlockType || chordyBlockNotMatchingChord)) {
            int length = blockNotes.length;
            List<Integer> typesToChoose = new ArrayList<>();
            for (int j = 0; j < BlockType.values().length; j++) {
                if (j != blockType && BlockType.AVAILABLE_BLOCK_CHANGES_PER_TYPE.get(j)
                        .contains(Math.abs(blockChange))) {
                    typesToChoose.add(j);
                }
            }
            if (!typesToChoose.isEmpty()) {
                int randomType = BlockType.getWeightedType(typesToChoose,
                        gc.getMelodyBlockTypePreference(), blockNotesGenerator.nextInt(100));
                Integer[] typedBlock = MelodyUtils.getRandomForTypeAndBlockChangeAndLength(
                        randomType, blockChange, length, blockNotesGenerator, 0);
                if (typedBlock != null) {
                    blockNotes = typedBlock;
                    blockType = randomType;
                    LG.d("Found new block!");
                } else {
                    LG.d("Different block not found in other types!");
                }
            } else {
                LG.d("Other types don't have this block!");
            }
        }
        return new BlockShape(blockType, blockNotes);
    }

    private List<Double> reconcileBlockDurations(int blockIndex, List<Integer> blockNotes,
            Map<Integer, List<PhraseNote>> customUserDurationsByBlock, Rhythm blockRhythm,
            MelodyPart mp) {
        return !customUserDurationsByBlock.isEmpty()
                && customUserDurationsByBlock.get(blockIndex).size() == blockNotes.size()
                ? customUserDurationsByBlock.get(blockIndex).stream()
                        .filter(note -> note.getDynamic() > 0)
                        .map(PhraseNote::getDuration).collect(Collectors.toList())
                : blockRhythm.makeDurations(blockNotes.size(), mp.getSpeed() < 20
                        ? timing.quarterNote : timing.sixteenthNote);
    }
}
