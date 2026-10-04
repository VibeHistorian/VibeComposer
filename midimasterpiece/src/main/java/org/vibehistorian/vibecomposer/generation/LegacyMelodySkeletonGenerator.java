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
    private final MidiGenerator mg;
    private final MelodyGenerationState state;
    private final MelodyChordInference inference;
    private final List<Integer> MELODY_SCALE = cIonianScale4;
    private int samePitchCount = 0;
    private int previousPitch = 0;

    LegacyMelodySkeletonGenerator(GUIConfig gc, MidiGenerator mg, MelodyGenerationState state, MelodyChordInference inference) {
        this.gc = gc;
        this.mg = mg;
        this.state = state;
        this.inference = inference;
    }

    Vector<Note> generate(MelodyPart mp, List<int[]> chords, List<int[]> roots, int measures,
                          int notesSeedOffset, Section sec, List<Integer> variations) {
        return algoGen2GenerateMelodySkeletonFromChords(mp, chords, roots, measures, notesSeedOffset, sec, variations);
    }
    private Vector<Note> algoGen2GenerateMelodySkeletonFromChords(MelodyPart mp, List<int[]> chords,
                                                                  List<int[]> roots, int measures, int notesSeedOffset, Section sec,
                                                                  List<Integer> variations) {

        boolean genVars = variations == null;

        boolean fillChordMelodyMap = false;
        if (state.chordMelodyMap1.isEmpty() && notesSeedOffset == 0
                && (roots.size() == mg.getGeneratedChordNames().size())) {
            fillChordMelodyMap = true;
        }

        int MAX_JUMP_SKELETON_CHORD = mp.getBlockJump();
        int SAME_RHYTHM_CHANCE = mp.getDoubledRhythmChance();
        int ALTERNATE_RHYTHM_CHANCE = mp.getAlternatingRhythmChance();
        int EXCEPTION_CHANCE = mp.getNoteExceptionChance();
        int CHORD_STRETCH = 4;

        int seed = mp.getPatternSeedWithPartOffset();

        Vector<Note> noteList = new Vector<>();

        Random algoGenerator = new Random(gc.getRandomSeed());
        if (algoGenerator.nextInt(100) < gc.getMelodyUseOldAlgoChance()) {
            return algoGen1GenerateMelodySkeletonFromChords(mp, measures, roots);
        }

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

        double[] melodySkeletonDurations = { MidiGenerator.Durations.SIXTEENTH_NOTE, MidiGenerator.Durations.EIGHTH_NOTE,
                MidiGenerator.Durations.QUARTER_NOTE, MidiGenerator.Durations.DOTTED_QUARTER_NOTE, MidiGenerator.Durations.HALF_NOTE };

        int weight3rd = mp.getSpeed() / 3;
        // 0% ->
        // 0, 0, 0, 40, 80, 100
        // 50% ->
        // 5 11 16 51 85 100
        // 100% ->
        // 11 22 33 72 91 100
        int[] melodySkeletonDurationWeights = { 0 + weight3rd / 3, 0 + weight3rd,
                40 + weight3rd * 2 / 3, 80 + weight3rd / 3, 100 };

        List<int[]> usedChords = null;
        if (gc.isMelodyBasicChordsOnly()) {
            List<int[]> basicChordsUnsquished = getBasicChordsFromRoots(roots);
            for (int i = 0; i < chords.size(); i++) {
                basicChordsUnsquished.set(i,
                        convertChordToLength(basicChordsUnsquished.get(i), chords.get(i).length));
            }

			/*
			 * if...
			 * usedChords = squishChordProgression(basicChordsUnsquished, gc.isSpiceFlattenBigChords(),
					gc.getRandomSeed(), gc.getChordGenSettings().getFlattenVoicingChance());
			 * */
            usedChords = basicChordsUnsquished;

        } else {
            usedChords = chords;
        }

        List<int[]> stretchedChords = usedChords.stream()
                .map(e -> convertChordToLength(e, CHORD_STRETCH)).collect(Collectors.toList());
        List<Double> directionChordDividers = (!gc.isMelodyUseDirectionsFromProgression())
                ? MidiGeneratorUtils.generateMelodyDirectionChordDividers(stretchedChords.size(),
                directionGenerator)
                : null;
        directionGenerator.setSeed(seed + 10);
        boolean currentDirection = directionGenerator.nextBoolean();
        if (!gc.isMelodyUseDirectionsFromProgression()) {
            LG.d("Direction dividers: " + directionChordDividers.toString() + ", start at: "
                    + currentDirection);
        }

        List<Boolean> directionsFromChords = (gc.isMelodyUseDirectionsFromProgression())
                ? MidiGeneratorUtils.generateMelodyDirectionsFromChordProgression(usedChords, true)
                : null;

        boolean alternateRhythm = alternateRhythmGenerator.nextInt(100) < ALTERNATE_RHYTHM_CHANCE;
        //LG.d("Alt: " + alternateRhythm);

        for (int o = 0; o < measures; o++) {
            int previousNotePitch = 0;
            int firstPitchInTwoChords = 0;

            for (int i = 0; i < stretchedChords.size(); i++) {
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
                                MAX_JUMP_SKELETON_CHORD = Math.min(4, MAX_JUMP_SKELETON_CHORD + 1);
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
                    previousNotePitch = firstPitchInTwoChords;
                    pitchPickerGenerator.setSeed(seed + pitchPickerOffset);
                    exceptionGenerator.setSeed(seed + 2 + notesSeedOffset);
                    if (alternateRhythm) {
                        sameRhythmGenerator.setSeed(seed + 3);
                    }
                }

                boolean sameRhythmTwice = sameRhythmGenerator.nextInt(100) < SAME_RHYTHM_CHANCE;

                double rhythmDuration = sameRhythmTwice ? mg.progressionDurations.get(i) / 2.0
                        : mg.progressionDurations.get(i);
                int rhythmSeed = (alternateRhythm && i % 2 == 1) ? seed + 1 : seed;
                rhythmSeed += rhythmOffset;
                Rhythm rhythm = new Rhythm(rhythmSeed, rhythmDuration, melodySkeletonDurations,
                        melodySkeletonDurationWeights);

                List<Double> durations = rhythm.regenerateDurations(sameRhythmTwice ? 1 : 2,
                        MidiGenerator.Durations.SIXTEENTH_NOTE / 2.0);
                if (gc.isMelodyArpySurprises()) {
                    if (sameRhythmTwice) {
                        if ((i % 2 == 0) || (durations.size() < 3)) {
                            durations.addAll(durations);
                        } else {
                            List<Double> arpedDurations = MelodyUtils.makeSurpriseTrioArpedDurations(durations);
                            if (arpedDurations != null) {
                                LG.d("Double pattern - surprise!");
                                durations.addAll(arpedDurations);
                            } else {
                                durations.addAll(durations);
                            }
                        }
                    } else if (i % 2 == 1 && durations.size() >= 4) {

                        List<Double> arpedDurations = MelodyUtils.makeSurpriseTrioArpedDurations(durations);
                        if (arpedDurations != null) {
                            LG.d("Single pattern - surprise!");
                            durations = arpedDurations;
                        }
                    }
                } else {
                    if (sameRhythmTwice) {
                        durations.addAll(durations);
                    }
                }


                int[] chord = stretchedChords.get(i);
                int exceptionCounter = mp.getMaxNoteExceptions();
                boolean allowException = true;
                double durCounter = 0.0;
                boolean changedDirectionByDivider = false;
                if (gc.isMelodyUseDirectionsFromProgression()) {
                    currentDirection = directionsFromChords.get(i);
                }
                for (int j = 0; j < durations.size(); j++) {
                    boolean tempChangedDir = false;
                    int tempSaveMaxJump = MAX_JUMP_SKELETON_CHORD;
                    boolean hasSingleNoteException = false;
                    if (allowException && j > 0 && exceptionCounter > 0
                            && exceptionGenerator.nextInt(100) < EXCEPTION_CHANCE) {
                        if (gc.isMelodySingleNoteExceptions()) {
                            hasSingleNoteException = true;
                            if (exceptionTypeGenerator.nextBoolean()) {
                                MAX_JUMP_SKELETON_CHORD = Math.max(0, MAX_JUMP_SKELETON_CHORD - 1);
                                tempChangedDir = true;
                                currentDirection = !currentDirection;
                            } else {
                                MAX_JUMP_SKELETON_CHORD = Math.min(6, MAX_JUMP_SKELETON_CHORD + 2);
                            }
                        } else {
                            currentDirection = !currentDirection;
                        }

                        exceptionCounter--;
                    }
                    int pitch = 0;
                    int startIndex = 0;
                    int endIndex = chord.length - 1;

                    if (previousNotePitch != 0) {
                        // up, or down
                        if (currentDirection) {
                            startIndex = MidiGeneratorUtils.selectClosestIndexFromChord(chord,
                                    previousNotePitch, true);
                            endIndex = Math.min(endIndex, startIndex + MAX_JUMP_SKELETON_CHORD);
                        } else {
                            endIndex = MidiGeneratorUtils.selectClosestIndexFromChord(chord,
                                    previousNotePitch, false);
                            startIndex = Math.max(endIndex - MAX_JUMP_SKELETON_CHORD, startIndex);
                        }
                    }
                    double positionInChord = durCounter / mg.progressionDurations.get(i);
                    pitch = MidiGeneratorUtils.pickRandomBetweenIndexesInclusive(chord, startIndex,
                            endIndex, pitchPickerGenerator, positionInChord);

                    double swingDuration = durations.get(j);
                    Note n = new Note(pitch, swingDuration, 100);
                    n.setDuration(swingDuration * (0.75 + durationGenerator.nextDouble() / 4)
                            * MidiGenerator.GLOBAL_DURATION_MULTIPLIER);

                    if (hasSingleNoteException && gc.isMelodySingleNoteExceptions()) {
                        if (tempChangedDir) {
                            currentDirection = !currentDirection;
                        }
                        MAX_JUMP_SKELETON_CHORD = tempSaveMaxJump;
                    }
                    if (!gc.isMelodySingleNoteExceptions()) {
                        if (previousNotePitch == pitch) {
                            currentDirection = !currentDirection;
                            allowException = false;
                        } else {
                            allowException = true;
                        }
                    }

                    if (i % 2 == 0 && j == 0 && !gc.isMelodyAvoidChordJumps()) {
                        firstPitchInTwoChords = pitch;
                    }
                    previousNotePitch = pitch;
                    if (hasSingleNoteException && tempChangedDir) {
                        previousNotePitch += (currentDirection) ? 2 : -2;
                    }
                    noteList.add(n);
                    if (fillChordMelodyMap && o == 0) {
                        state.chordMelodyMap1.get(i).add(n);
                    }
                    durCounter += swingDuration;
                    if (!gc.isMelodyUseDirectionsFromProgression() && !changedDirectionByDivider
                            && durCounter > directionChordDividers.get(i)) {
                        changedDirectionByDivider = true;
                        currentDirection = !currentDirection;
                    }
                }
                if (!gc.isMelodyUseDirectionsFromProgression() && !changedDirectionByDivider) {
                    currentDirection = !currentDirection;
                }

            }
        }

        if (fillChordMelodyMap) {
            List<String> chordStrings = MelodyUtils.getChordsFromMelodyPitches(2, mg.progressionDurations, state.chordMelodyMap1,
                    MidiUtils.baseFreqMap);
            inference.populateMelodyBasedProgression(chordStrings, 1, state.chordMelodyMap1.keySet().size() - 1);

        }
        if (genVars && variations != null) {
            sec.setVariation(0, mp.getAbsoluteOrder(gc.getMelodyParts()), variations);
        }
        return noteList;
    }

    private Note algoGen1GenerateNote(MelodyPart mp, int[] chord, boolean isAscDirection,
                                      List<Integer> chordScale, Note previousNote, Random generator, double durationLeft) {
        // int randPitch = generator.nextInt(8);
        int velMin = mp.getVelocityMin();
        int velSpace = mp.getVelocityMax() - velMin;

        int direction = (isAscDirection) ? 1 : -1;
        double dur = pickDurationWeightedRandom(generator, durationLeft, MidiGenerator.MELODY_DUR_ARRAY,
                MidiGenerator.MELODY_DUR_CHANCE, MidiGenerator.Durations.EIGHTH_NOTE);
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

        while (currentDuration <= maxDuration - MidiGenerator.Durations.EIGHTH_NOTE) {
            double durationLeft = maxDuration - MidiGenerator.Durations.EIGHTH_NOTE - currentDuration;
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
                                                                  List<int[]> genRootProg) {
        List<Boolean> directionProgression = MidiGeneratorUtils
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
                            mg.progressionDurations.get(j), melodyGenerator, previousChordsNote,
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
                    MidiGenerator.Durations.WHOLE_NOTE);
            copied[0] = new Note(n.getPitch(), originals[0].getRhythmValue(),
                    originals[0].getDynamic());
        }
        return copied;
    }



}
