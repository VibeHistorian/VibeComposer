package org.vibehistorian.vibecomposer.generation;

import jm.constants.Pitches;
import jm.music.data.Note;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Section;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.stream.Collectors;

import static org.vibehistorian.vibecomposer.MidiUtils.*;

final class LegacyMelodySkeletonGenerator {
    private final GUIConfig gc;
    private final MidiTiming timing;
    private final double globalDurationMultiplier;
    private final MelodyGenerationState state;
    private final MelodyChordInference inference;
    private final List<Integer> MELODY_SCALE = cIonianScale4;
    private int samePitchCount = 0;
    private int previousPitch = 0;

    LegacyMelodySkeletonGenerator(GUIConfig gc, MidiTiming timing, double globalDurationMultiplier,
            MelodyGenerationState state, MelodyChordInference inference) {
        this.gc = gc;
        this.timing = timing;
        this.globalDurationMultiplier = globalDurationMultiplier;
        this.state = state;
        this.inference = inference;
    }

    Vector<Note> generate(MelodyPart mp, List<int[]> chords, List<int[]> roots, int measures,
                          int notesSeedOffset, Section sec, List<Integer> variations,
                          List<Double> progressionDurations, List<String> generatedChordNames,
                          List<int[]> currentRootProgression, List<int[]> currentChordProgression) {
        return algoGen2GenerateMelodySkeletonFromChords(mp, chords, roots, measures, notesSeedOffset, sec,
                variations, progressionDurations, generatedChordNames,
                currentRootProgression, currentChordProgression);
    }
    private Vector<Note> algoGen2GenerateMelodySkeletonFromChords(MelodyPart mp, List<int[]> chords,
                                                                  List<int[]> roots, int measures, int notesSeedOffset, Section sec,
                                                                   List<Integer> variations,
                                                                   List<Double> progressionDurations,
                                                                   List<String> generatedChordNames,
                                                                   List<int[]> currentRootProgression,
                                                                   List<int[]> currentChordProgression) {

        boolean genVars = variations == null;

        boolean fillChordMelodyMap = false;
        if (state.chordMelodyMap1.isEmpty() && notesSeedOffset == 0
                && (roots.size() == generatedChordNames.size())) {
            fillChordMelodyMap = true;
        }

        int seed = mp.getPatternSeedWithPartOffset();

        Vector<Note> noteList = new Vector<>();

        Random algoGenerator = new Random(gc.getRandomSeed());
        if (algoGenerator.nextInt(100) < gc.getMelodyUseOldAlgoChance()) {
            return algoGen1GenerateMelodySkeletonFromChords(mp, measures, roots, progressionDurations);
        }

        LegacySkeletonSetup setup = prepareLegacySkeletonSetup(mp, chords, roots, seed,
                notesSeedOffset, fillChordMelodyMap);

        for (int o = 0; o < measures; o++) {
            LegacyMeasureState measureState = new LegacyMeasureState();

            for (int i = 0; i < setup.stretchedChords.size(); i++) {
                // either after first measure, or after first half of combined chord prog

                if (genVars && (i == 0)) {
                    variations = MidiGenerator.fillVariations(gc, sec, mp, variations, 0);
                }

                if ((variations != null) && (i == 0)) {
                    for (Integer var : variations) {
                        if (o == measures - 1) {
                            LG.i("Melody variation: " + var);
                        }

                        switch (var) {
                            case 0:
                                // only add, processed later
                                break;
                            case 1:
                                setup.pitchGeneration.maxJumpSkeletonChord = Math.min(4,
                                        setup.pitchGeneration.maxJumpSkeletonChord + 1);
                                break;
                            default:
                                break;
                        }
                    }
                }

                if (fillChordMelodyMap && o == 0) {
                    if (!state.chordMelodyMap1.containsKey(i)) {
                        state.chordMelodyMap1.put(i, new ArrayList<>());
                    }
                }
                if (i % 2 == 0) {
                    measureState.previousNotePitch = measureState.firstPitchInTwoChords;
                    setup.pitchGeneration.pitchPickerGenerator.setSeed(setup.seed + setup.pitchPickerOffset);
                    setup.pitchGeneration.exceptionGenerator.setSeed(setup.seed + 2 + notesSeedOffset);
                    if (setup.alternateRhythm) {
                        setup.sameRhythmGenerator.setSeed(setup.seed + 3);
                    }
                }

                List<Double> durations = generateLegacyChordDurations(i, progressionDurations.get(i),
                        setup.seed, setup.rhythmOffset, setup.alternateRhythm,
                        setup.sameRhythmGenerator, setup.sameRhythmChance,
                        setup.melodySkeletonDurations, setup.melodySkeletonDurationWeights);

                generateLegacyChordNotes(mp, setup.stretchedChords.get(i), durations,
                        progressionDurations.get(i), i, o, setup.pitchGeneration, measureState, noteList);

            }
        }

        return publishLegacySkeletonResults(noteList, fillChordMelodyMap, progressionDurations,
                currentRootProgression, currentChordProgression, genVars, variations, sec, mp);
    }

    private LegacySkeletonSetup prepareLegacySkeletonSetup(MelodyPart mp, List<int[]> chords,
                                                            List<int[]> roots, int seed,
                                                            int notesSeedOffset,
                                                            boolean fillChordMelodyMap) {
        int maxJumpSkeletonChord = mp.getBlockJump();
        int sameRhythmChance = mp.getDoubledRhythmChance();
        int alternateRhythmChance = mp.getAlternatingRhythmChance();
        int exceptionChance = mp.getNoteExceptionChance();
        int chordStretch = 4;

        // if notes seed offset > 0, add it only to one of: rhythms, pitches
        //Random nonMainMelodyGenerator = new Random(seed + 30);
        int pitchPickerOffset = notesSeedOffset;
        int rhythmOffset = notesSeedOffset;
        Random pitchPickerGenerator = new Random(seed + pitchPickerOffset);
        Random exceptionGenerator = new Random(seed + 2 + notesSeedOffset);
        Random sameRhythmGenerator = new Random(seed + 3);
        Random alternateRhythmGenerator = new Random(seed + 4);
        Random durationGenerator = new Random(seed + notesSeedOffset + 5);
        Random directionGenerator = new Random(seed + 10);
        //Random surpriseGenerator = new Random(seed + notesSeedOffset + 15);
        Random exceptionTypeGenerator = new Random(seed + 20 + notesSeedOffset);

        double[] melodySkeletonDurations = { timing.sixteenthNote, timing.eighthNote,
                timing.quarterNote, timing.dottedQuarterNote, timing.halfNote };
        int weight3rd = mp.getSpeed() / 3;
        int[] melodySkeletonDurationWeights = { 0 + weight3rd / 3, 0 + weight3rd,
                40 + weight3rd * 2 / 3, 80 + weight3rd / 3, 100 };

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

        List<int[]> stretchedChords = usedChords.stream()
                .map(e -> convertChordToLength(e, chordStretch)).collect(Collectors.toList());
        List<Double> directionChordDividers = (!gc.isMelodyUseDirectionsFromProgression())
                ? MelodyTargetUtils.generateMelodyDirectionChordDividers(stretchedChords.size(),
                directionGenerator)
                : null;
        directionGenerator.setSeed(seed + 10);
        boolean currentDirection = directionGenerator.nextBoolean();
        if (!gc.isMelodyUseDirectionsFromProgression()) {
            LG.d("Direction dividers: " + directionChordDividers.toString() + ", start at: "
                    + currentDirection);
        }
        List<Boolean> directionsFromChords = (gc.isMelodyUseDirectionsFromProgression())
                ? MelodyTargetUtils.generateMelodyDirectionsFromChordProgression(usedChords, true)
                : null;
        boolean alternateRhythm = alternateRhythmGenerator.nextInt(100) < alternateRhythmChance;
        //LG.d("Alt: " + alternateRhythm);
        LegacyPitchGeneration pitchGeneration = new LegacyPitchGeneration(maxJumpSkeletonChord,
                exceptionChance, pitchPickerGenerator, exceptionGenerator, durationGenerator,
                exceptionTypeGenerator, directionChordDividers, directionsFromChords, currentDirection,
                fillChordMelodyMap);
        return new LegacySkeletonSetup(seed, pitchPickerOffset, rhythmOffset, sameRhythmChance,
                sameRhythmGenerator, melodySkeletonDurations, melodySkeletonDurationWeights,
                stretchedChords, alternateRhythm, pitchGeneration);
    }

    private List<Double> generateLegacyChordDurations(int chordIndex, double chordDuration, int seed,
                                                       int rhythmOffset, boolean alternateRhythm,
                                                       Random sameRhythmGenerator, int sameRhythmChance,
                                                       double[] melodySkeletonDurations,
                                                       int[] melodySkeletonDurationWeights) {
        boolean sameRhythmTwice = sameRhythmGenerator.nextInt(100) < sameRhythmChance;
        double rhythmDuration = sameRhythmTwice ? chordDuration / 2.0 : chordDuration;
        int rhythmSeed = (alternateRhythm && chordIndex % 2 == 1) ? seed + 1 : seed;
        rhythmSeed += rhythmOffset;
        Rhythm rhythm = new Rhythm(rhythmSeed, rhythmDuration, melodySkeletonDurations,
                melodySkeletonDurationWeights);

        List<Double> durations = rhythm.regenerateDurations(sameRhythmTwice ? 1 : 2,
                timing.sixteenthNote / 2.0);
        if (gc.isMelodyArpySurprises()) {
            if (sameRhythmTwice) {
                if ((chordIndex % 2 == 0) || (durations.size() < 3)) {
                    durations.addAll(durations);
                } else {
                    List<Double> arpedDurations = MelodyUtils.makeSurpriseTrioArpedDurations(durations,
                            timing);
                    if (arpedDurations != null) {
                        LG.d("Double pattern - surprise!");
                        durations.addAll(arpedDurations);
                    } else {
                        durations.addAll(durations);
                    }
                }
            } else if (chordIndex % 2 == 1 && durations.size() >= 4) {
                List<Double> arpedDurations = MelodyUtils.makeSurpriseTrioArpedDurations(durations,
                        timing);
                if (arpedDurations != null) {
                    LG.d("Single pattern - surprise!");
                    durations = arpedDurations;
                }
            }
        } else if (sameRhythmTwice) {
            durations.addAll(durations);
        }
        return durations;
    }

    private void generateLegacyChordNotes(MelodyPart mp, int[] chord, List<Double> durations,
                                          double progressionDuration, int chordIndex, int measure,
                                          LegacyPitchGeneration pitchGeneration,
                                          LegacyMeasureState measureState, Vector<Note> noteList) {
        int exceptionCounter = mp.getMaxNoteExceptions();
        boolean allowException = true;
        double durCounter = 0.0;
        boolean changedDirectionByDivider = false;
        if (gc.isMelodyUseDirectionsFromProgression()) {
            pitchGeneration.currentDirection = pitchGeneration.directionsFromChords.get(chordIndex);
        }
        for (int j = 0; j < durations.size(); j++) {
            boolean tempChangedDir = false;
            int tempSaveMaxJump = pitchGeneration.maxJumpSkeletonChord;
            boolean hasSingleNoteException = false;
            if (allowException && j > 0 && exceptionCounter > 0
                    && pitchGeneration.exceptionGenerator.nextInt(100) < pitchGeneration.exceptionChance) {
                if (gc.isMelodySingleNoteExceptions()) {
                    hasSingleNoteException = true;
                    if (pitchGeneration.exceptionTypeGenerator.nextBoolean()) {
                        pitchGeneration.maxJumpSkeletonChord = Math.max(0,
                                pitchGeneration.maxJumpSkeletonChord - 1);
                        tempChangedDir = true;
                        pitchGeneration.currentDirection = !pitchGeneration.currentDirection;
                    } else {
                        pitchGeneration.maxJumpSkeletonChord = Math.min(6,
                                pitchGeneration.maxJumpSkeletonChord + 2);
                    }
                } else {
                    pitchGeneration.currentDirection = !pitchGeneration.currentDirection;
                }
                exceptionCounter--;
            }

            int startIndex = 0;
            int endIndex = chord.length - 1;
            if (measureState.previousNotePitch != 0) {
                if (pitchGeneration.currentDirection) {
                    startIndex = MidiGeneratorUtils.selectClosestIndexFromChord(chord,
                            measureState.previousNotePitch, true);
                    endIndex = Math.min(endIndex, startIndex + pitchGeneration.maxJumpSkeletonChord);
                } else {
                    endIndex = MidiGeneratorUtils.selectClosestIndexFromChord(chord,
                            measureState.previousNotePitch, false);
                    startIndex = Math.max(endIndex - pitchGeneration.maxJumpSkeletonChord, startIndex);
                }
            }
            double positionInChord = durCounter / progressionDuration;
            int pitch = MidiGeneratorUtils.pickRandomBetweenIndexesInclusive(chord, startIndex,
                    endIndex, pitchGeneration.pitchPickerGenerator, positionInChord);
            double swingDuration = durations.get(j);
            Note note = new Note(pitch, swingDuration, 100);
            note.setDuration(swingDuration * (0.75 + pitchGeneration.durationGenerator.nextDouble() / 4)
                    * globalDurationMultiplier);

            if (hasSingleNoteException && gc.isMelodySingleNoteExceptions()) {
                if (tempChangedDir) {
                    pitchGeneration.currentDirection = !pitchGeneration.currentDirection;
                }
                pitchGeneration.maxJumpSkeletonChord = tempSaveMaxJump;
            }
            if (!gc.isMelodySingleNoteExceptions()) {
                if (measureState.previousNotePitch == pitch) {
                    pitchGeneration.currentDirection = !pitchGeneration.currentDirection;
                    allowException = false;
                } else {
                    allowException = true;
                }
            }

            if (chordIndex % 2 == 0 && j == 0 && !gc.isMelodyAvoidChordJumps()) {
                measureState.firstPitchInTwoChords = pitch;
            }
            measureState.previousNotePitch = pitch;
            if (hasSingleNoteException && tempChangedDir) {
                measureState.previousNotePitch += pitchGeneration.currentDirection ? 2 : -2;
            }
            noteList.add(note);
            if (pitchGeneration.fillChordMelodyMap && measure == 0) {
                state.chordMelodyMap1.get(chordIndex).add(note);
            }
            durCounter += swingDuration;
            if (!gc.isMelodyUseDirectionsFromProgression() && !changedDirectionByDivider
                    && durCounter > pitchGeneration.directionChordDividers.get(chordIndex)) {
                changedDirectionByDivider = true;
                pitchGeneration.currentDirection = !pitchGeneration.currentDirection;
            }
        }
        if (!gc.isMelodyUseDirectionsFromProgression() && !changedDirectionByDivider) {
            pitchGeneration.currentDirection = !pitchGeneration.currentDirection;
        }
    }

    private Vector<Note> publishLegacySkeletonResults(Vector<Note> noteList, boolean fillChordMelodyMap,
                                                       List<Double> progressionDurations,
                                                       List<int[]> currentRootProgression,
                                                       List<int[]> currentChordProgression,
                                                       boolean genVars, List<Integer> variations,
                                                       Section sec, MelodyPart mp) {
        if (fillChordMelodyMap) {
            List<String> chordStrings = MelodyUtils.getChordsFromMelodyPitches(2, progressionDurations,
                    state.chordMelodyMap1, MidiUtils.baseFreqMap, timing);
            inference.populateMelodyBasedProgression(chordStrings, 1,
                    state.chordMelodyMap1.size() - 1,
                    currentRootProgression, currentChordProgression);
        }
        if (genVars && variations != null) {
            sec.setVariation(0, mp.getAbsoluteOrder(gc.getMelodyParts()), variations);
        }
        return noteList;
    }

    private static final class LegacyMeasureState {
        private int previousNotePitch;
        private int firstPitchInTwoChords;
    }

    private static final class LegacySkeletonSetup {
        private final int seed;
        private final int pitchPickerOffset;
        private final int rhythmOffset;
        private final int sameRhythmChance;
        private final Random sameRhythmGenerator;
        private final double[] melodySkeletonDurations;
        private final int[] melodySkeletonDurationWeights;
        private final List<int[]> stretchedChords;
        private final boolean alternateRhythm;
        private final LegacyPitchGeneration pitchGeneration;

        private LegacySkeletonSetup(int seed, int pitchPickerOffset, int rhythmOffset,
                                    int sameRhythmChance, Random sameRhythmGenerator,
                                    double[] melodySkeletonDurations,
                                    int[] melodySkeletonDurationWeights, List<int[]> stretchedChords,
                                    boolean alternateRhythm,
                                    LegacyPitchGeneration pitchGeneration) {
            this.seed = seed;
            this.pitchPickerOffset = pitchPickerOffset;
            this.rhythmOffset = rhythmOffset;
            this.sameRhythmChance = sameRhythmChance;
            this.sameRhythmGenerator = sameRhythmGenerator;
            this.melodySkeletonDurations = melodySkeletonDurations;
            this.melodySkeletonDurationWeights = melodySkeletonDurationWeights;
            this.stretchedChords = stretchedChords;
            this.alternateRhythm = alternateRhythm;
            this.pitchGeneration = pitchGeneration;
        }
    }

    private static final class LegacyPitchGeneration {
        private int maxJumpSkeletonChord;
        private final int exceptionChance;
        private final Random pitchPickerGenerator;
        private final Random exceptionGenerator;
        private final Random durationGenerator;
        private final Random exceptionTypeGenerator;
        private final List<Double> directionChordDividers;
        private final List<Boolean> directionsFromChords;
        private boolean currentDirection;
        private final boolean fillChordMelodyMap;

        private LegacyPitchGeneration(int maxJumpSkeletonChord, int exceptionChance,
                                      Random pitchPickerGenerator, Random exceptionGenerator,
                                      Random durationGenerator, Random exceptionTypeGenerator,
                                      List<Double> directionChordDividers,
                                      List<Boolean> directionsFromChords, boolean currentDirection,
                                      boolean fillChordMelodyMap) {
            this.maxJumpSkeletonChord = maxJumpSkeletonChord;
            this.exceptionChance = exceptionChance;
            this.pitchPickerGenerator = pitchPickerGenerator;
            this.exceptionGenerator = exceptionGenerator;
            this.durationGenerator = durationGenerator;
            this.exceptionTypeGenerator = exceptionTypeGenerator;
            this.directionChordDividers = directionChordDividers;
            this.directionsFromChords = directionsFromChords;
            this.currentDirection = currentDirection;
            this.fillChordMelodyMap = fillChordMelodyMap;
        }
    }

    private Note algoGen1GenerateNote(MelodyPart mp, int[] chord, boolean isAscDirection,
                                      List<Integer> chordScale, Note previousNote, Random generator, double durationLeft) {
        // int randPitch = generator.nextInt(8);
        int velMin = mp.getVelocityMin();
        int velSpace = mp.getVelocityMax() - velMin;

        int direction = (isAscDirection) ? 1 : -1;
        double dur = pickDurationWeightedRandom(generator, durationLeft, timing.getMelodyDurationOptions(),
                timing.getMelodyDurationChances(), timing.eighthNote);
        boolean isPause = (generator.nextInt(100) < mp.getPauseChance());
        if (previousNote == null) {
            int[] firstChord = chord;
            int chordNote = (gc.isFirstNoteRandomized()) ? generator.nextInt(firstChord.length) : 0;

            int chosenPitch = 60 + (firstChord[chordNote] % 12);

            previousPitch = chordScale.indexOf(chosenPitch);
            if (previousPitch == -1) {
                LG.d("ERROR PITCH -1 for: " + chosenPitch);
                previousPitch = chordScale.indexOf(chosenPitch + 1);
                if (previousPitch == -1) {
                    LG.i("NOT EVEN +1 pitch exists for " + chosenPitch + "!");
                }
            }

            //LG.d(firstChord[chordNote] + " > from first chord");
            if (isPause) {
                return new Note(Pitches.REST, dur);
            }

            return new Note(chosenPitch, dur, velMin + generator.nextInt(velSpace));
        }

        int change = generator.nextInt(mp.getBlockJump() + 1);
        // weighted against same note
        if (change == 0) {
            change = generator.nextInt((mp.getBlockJump() + 1) / 2);
        }

        int generatedPitch = previousPitch + direction * change;
        //fit into 0-7 scale
        generatedPitch = maX(generatedPitch, MelodyGenerator.maxAllowedScaleNotes);


        if (generatedPitch == previousPitch && !isPause) {
            samePitchCount++;
        } else {
            samePitchCount = 0;
        }
        //if 3 or more times same note, swap direction for this case
        if (samePitchCount >= 2) {
            //LG.d("UNSAMING NOTE!: " + previousPitch + ", BY: " + (-direction * change));
            generatedPitch = maX(previousPitch - direction * change, MelodyGenerator.maxAllowedScaleNotes);
            samePitchCount = 0;
        }
        previousPitch = generatedPitch;
        if (isPause) {
            return new Note(Pitches.REST, dur);
        }
        return new Note(chordScale.get(generatedPitch), dur, velMin + generator.nextInt(velSpace));

    }

    private Note[] algoGen1GenerateMelodyForChord(MelodyPart mp, int[] chord, double maxDuration,
                                                  Random generator, Note previousChordsNote, boolean isAscDirection) {
        List<Integer> scale = transposeScale(MELODY_SCALE, 0, false);

        double currentDuration = 0.0;

        Note previousNote = (gc.isFirstNoteFromChord()) ? null : previousChordsNote;
        List<Note> notes = new ArrayList<>();

        int exceptionsLeft = mp.getMaxNoteExceptions();

        while (currentDuration <= maxDuration - timing.eighthNote) {
            double durationLeft = maxDuration - timing.eighthNote - currentDuration;
            boolean exceptionChangeUsed = false;
            // generate note,
            boolean actualDirection = isAscDirection;
            if ((generator.nextInt(100) < 33) && (exceptionsLeft > 0)) {
                //LG.d("Exception used for chordnote: " + chord[0]);
                exceptionChangeUsed = true;
                actualDirection = !actualDirection;
            }
            Note note = algoGen1GenerateNote(mp, chord, actualDirection, scale, previousNote,
                    generator, durationLeft);
            if (exceptionChangeUsed) {
                exceptionsLeft--;
            }
            previousNote = note;
            currentDuration += note.getRhythmValue();
            Note transposedNote = new Note(note.getPitch(), note.getRhythmValue(),
                    note.getDynamic());
            notes.add(transposedNote);
        }
        return notes.toArray(new Note[0]);
    }

    private Vector<Note> algoGen1GenerateMelodySkeletonFromChords(MelodyPart mp, int measures,
                                                                  List<int[]> genRootProg,
                                                                  List<Double> progressionDurations) {
        List<Boolean> directionProgression = MelodyTargetUtils
                .generateMelodyDirectionsFromChordProgression(genRootProg, true);

        Note previousChordsNote = null;

        Note[] pair024 = null;
        Note[] pair15 = null;
        Random melodyGenerator = new Random();
        if (!mp.isMuted() && mp.getPatternSeedWithPartOffset() != 0) {
            melodyGenerator.setSeed(mp.getPatternSeedWithPartOffset());
        } else {
            melodyGenerator.setSeed(gc.getRandomSeed());
        }
        LG.i("LEGACY ALGORITHM!");
        Vector<Note> fullMelody = new Vector<>();
        for (int i = 0; i < measures; i++) {
            for (int j = 0; j < genRootProg.size(); j++) {
                Note[] generatedMelody = null;

                if ((i > 0 || j > 0) && (j == 0 || j == 2)) {
                    generatedMelody = deepCopyNotes(mp, pair024, genRootProg.get(j),
                            melodyGenerator);
                } else if (i > 0 && j == 1) {
                    generatedMelody = deepCopyNotes(mp, pair15, null, null);
                } else {
                    generatedMelody = algoGen1GenerateMelodyForChord(mp, genRootProg.get(j),
                            progressionDurations.get(j), melodyGenerator, previousChordsNote,
                            directionProgression.get(j));
                }

                previousChordsNote = generatedMelody[generatedMelody.length - 1];

                if (i == 0 && j == 0) {
                    pair024 = deepCopyNotes(mp, generatedMelody, null, null);
                }
                if (i == 0 && j == 1) {
                    pair15 = deepCopyNotes(mp, generatedMelody, null, null);
                }
                fullMelody.addAll(Arrays.asList(generatedMelody));
            }
        }
        return fullMelody;
    }

    private Note[] deepCopyNotes(MelodyPart mp, Note[] originals, int[] chord,
                                 Random melodyGenerator) {
        Note[] copied = new Note[originals.length];
        for (int i = 0; i < originals.length; i++) {
            Note n = originals[i];
            copied[i] = new Note(n.getPitch(), n.getRhythmValue());
        }
        if (chord != null && melodyGenerator != null && gc.isFirstNoteFromChord()) {
            Note n = algoGen1GenerateNote(mp, chord, true, MELODY_SCALE, null, melodyGenerator,
                    timing.wholeNote);
            copied[0] = new Note(n.getPitch(), originals[0].getRhythmValue(),
                    originals[0].getDynamic());
        }
        return copied;
    }



}
