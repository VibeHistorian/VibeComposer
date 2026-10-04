package org.vibehistorian.vibecomposer.generation;

import jm.constants.Pitches;
import jm.music.data.Note;
import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.Enums.BlockType;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.Helpers.PartPhraseNotes;
import org.vibehistorian.vibecomposer.Helpers.PhraseNote;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Section;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.vibehistorian.vibecomposer.Constants.DBL_ERR;
import static org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;

final class MelodyExpansion {
    private final GUIConfig gc;
    private final MidiTiming timing;
    private final double globalDurationMultiplier;

    MelodyExpansion(GUIConfig gc, MidiTiming timing, double globalDurationMultiplier) {
        this.gc = gc;
        this.timing = timing;
        this.globalDurationMultiplier = globalDurationMultiplier;
    }
    Map<Integer, List<PhraseNote>> convertCustomUserDurations(MelodyPart mp,
            int melodyBlockGeneratorSeed, int chordIndex, int blockOffsetChordIndex,
            Section section, List<Double> progressionDurations) {
        Map<Integer, List<PhraseNote>> customUserDurationsByBlock = new LinkedHashMap<>();
        if (gc.isMelodyUseCustomDurations() && mp.getCustomDurationNotes() != null && mp.getCustomDurationNotes().size() > 1) {
            PartPhraseNotes customDurationNotesMap = createCustomDurationNotesMap(mp.getCustomDurationNotes());
            Integer indexValue = blockOffsetChordIndex >= 0 ? (blockOffsetChordIndex % customDurationNotesMap.size())
                    : (chordIndex % customDurationNotesMap.size());
            Random customDurationsGenerator = new Random(melodyBlockGeneratorSeed + 12 + indexValue);
            if (gc.isMelodyCustomDurationsRandomWeighting()) {
                List<Integer> firstDurationWeights = customDurationNotesMap.stream().map(e -> mp.getCustomDurationChances().get(e.get(0).getPitch())).collect(Collectors.toList());
                int[] weights = MelodyUtils.normalizedCumulativeWeights(firstDurationWeights.toArray(new Integer[]{}));
                indexValue = OMNI.getWeightedValue(IntStream.range(0, customDurationNotesMap.size()).boxed().toArray(Integer[]::new),
                        customDurationsGenerator.nextInt(100), weights);
            }
            PhraseNotes userCustomDurations = customDurationNotesMap.get(indexValue);
            int pitchValue = userCustomDurations.get(0).getPitch();

            double mult = MidiGenerator.getBeatDurationMult(gc, section);
            if (!MidiUtils.roughlyEqual(mult, 1.0)) {
                userCustomDurations.stretch(mult, false);
            }
            userCustomDurations.remakeNoteStartTimes(true);

            double currentChordDur = progressionDurations.get(chordIndex);


            double startTime = userCustomDurations.getIterationOrder().get(0).getStartTime();
            if (startTime > DBL_ERR) {
                userCustomDurations.add(0, new PhraseNote(pitchValue, 0, 0.0, startTime, 0.0));
            }
            PhraseNote lastNote = userCustomDurations.getIterationOrder().get(userCustomDurations.size()-1);
            if (lastNote.getEndTime() + DBL_ERR < currentChordDur) {
                userCustomDurations.add(userCustomDurations.indexOf(lastNote) + 1, new PhraseNote(pitchValue, 0, 0.0,
                        currentChordDur - lastNote.getEndTime(),
                        lastNote.getOffset() + lastNote.getDuration()));
                userCustomDurations.remakeNoteStartTimes(true);
            }
            // cleanup notes going out of bounds
            for (int i = userCustomDurations.size()-1; i >= 0; i--) {
                PhraseNote currentPn = userCustomDurations.get(i);
                if (currentPn.getStartTime() + DBL_ERR > currentChordDur) {
                    userCustomDurations.remove(currentPn);
                    continue;
                }
                if (currentPn.getEndTime() - DBL_ERR > currentChordDur) {
                    currentPn.setDuration(currentPn.getDuration() - (currentPn.getEndTime() - currentChordDur));
                }
                userCustomDurations.remakeNoteStartTimes(true);
            }

            // cleanup notes overlapping each other
            for (int i = userCustomDurations.size()-1; i >= 1; i--) {
                PhraseNote earlierPn = userCustomDurations.getIterationOrder().get(i-1);
                PhraseNote currentPn = userCustomDurations.getIterationOrder().get(i);
                if (earlierPn.getEndTime() - DBL_ERR > currentPn.getStartTime()) {
                    earlierPn.setDuration(earlierPn.getDuration() - earlierPn.getEndTime() + currentPn.getStartTime());
                    userCustomDurations.remakeNoteStartTimes(true);
                }
            }

            // insert pauses between note gaps
            double target = currentChordDur;
            for (int i = userCustomDurations.size()-1; i >= 0; i--) {
                PhraseNote currentPn = userCustomDurations.getIterationOrder().get(i);
                if (currentPn.getEndTime() + DBL_ERR < target) {
                    int index = userCustomDurations.indexOf(currentPn);
                    userCustomDurations.add(index + 1, new PhraseNote(pitchValue, 0, 0.0,
                            target - currentPn.getEndTime(), currentPn.getOffset() + currentPn.getDuration()));
                    userCustomDurations.remakeNoteStartTimes(true);
                }
                target = currentPn.getStartTime();
            }

            userCustomDurations.remakeNoteStartTimes(true);

            List<PhraseNote> customDurationsBlock = new ArrayList<>();
            int blockCounter = 0;
            int sizeCounter = 0;
            for (PhraseNote pn : userCustomDurations.getIterationOrder()) {
                customDurationsBlock.add(pn);
                if (pn.getDynamic() > 0) {
                    sizeCounter++;
                }
                if (sizeCounter == 3) {
                    if (customDurationsGenerator.nextBoolean()) {
                        customUserDurationsByBlock.put(blockCounter++, customDurationsBlock);
                        LG.i("Custom durations used: " + StringUtils.join(customDurationsBlock, ","));
                        customDurationsBlock = new ArrayList<>();
                        sizeCounter = 0;
                    }
                } else if (sizeCounter >= 4) {
                    customUserDurationsByBlock.put(blockCounter++, customDurationsBlock);
                    LG.i("Custom durations used: " + StringUtils.join(customDurationsBlock, ","));
                    customDurationsBlock = new ArrayList<>();
                    sizeCounter = 0;
                }
            }
            if (!customDurationsBlock.isEmpty()) {
                if (sizeCounter > 0) {
                    // some valid note exists
                    customUserDurationsByBlock.put(blockCounter, customDurationsBlock);
                } else if (blockCounter > 0 && customUserDurationsByBlock.get(blockCounter-1) != null) {
                    // only pauses exist
                    customUserDurationsByBlock.get(blockCounter-1).addAll(customDurationsBlock);
                }

                LG.i("Custom durations extra in last block: " + StringUtils.join(customDurationsBlock, ","));
            }
        }
        return customUserDurationsByBlock;
    }

    private PartPhraseNotes createCustomDurationNotesMap(PhraseNotes customDurationValues) {
        customDurationValues.remakeNoteStartTimes(true);
        List<PhraseNote> iterOrder = customDurationValues.getIterationOrder();
        Map<Integer, List<PhraseNote>> rawMap = iterOrder.stream().collect(Collectors.groupingBy(PhraseNote::getPitch));
        PartPhraseNotes customDurationNotesMap = new PartPhraseNotes();
        for (Integer i : rawMap.keySet().stream().sorted().collect(Collectors.toList())) {
            PhraseNotes pns = PhraseNotes.fromPN(rawMap.get(i));
            pns.sort(Comparator.comparingInt(iterOrder::indexOf));
            customDurationNotesMap.add(pns);
        }
        return customDurationNotesMap;
    }

    protected Map<Integer, List<Note>> convertMelodySkeletonToFullMelody(MelodyPart mp,
                                                                         List<Double> durations, Section sec, Vector<Note> skeleton, int notesSeedOffset,
                                                                         List<int[]> chords, int measures,
                                                                         ScaleMode modScale) {

        int RANDOM_SPLIT_NOTE_PITCH_EXCEPTION_RANGE = 4;

        int seed = mp.getPatternSeedWithPartOffset();
        int orderSeed = seed + mp.getOrderOffset();
        Random splitGenerator = new Random(orderSeed + 4);
        Random pauseGenerator = new Random(orderSeed + 5);
        Random variationGenerator = new Random(mp.getOrderOffset() + gc.getArrangement().getSeed() + 6);
        Random velocityGenerator = new Random(orderSeed + 1 + notesSeedOffset);
        Random splitNoteGenerator = new Random(seed + 8);
        Random splitNoteExceptionGenerator = new Random(seed + 9);
        Random chordLeadingGenerator = new Random(orderSeed + notesSeedOffset + 15);
        Random accentGenerator = new Random(orderSeed + 20);


        int splitChance = mp.getSplitChance();
        Vector<Note> fullMelody = new Vector<>();
        Map<Integer, List<Note>> fullMelodyMap = new HashMap<>();
        for (int i = 0; i < durations.size() * measures; i++) {
            fullMelodyMap.put(i, new Vector<>());
        }
        int chordCounter = 0;
        int measureCounter = 0;
        double durCounter = 0.0;
        double currentChordDur = durations.get(0);

        int volMultiplier = (gc.isScaleMidiVelocityInArrangement()) ? sec.getVol(0) : 100;
        int minVel = MidiGeneratorUtils.multiplyVelocity(mp.getVelocityMin(), volMultiplier, 0, 1);
        int maxVel = MidiGeneratorUtils.multiplyVelocity(mp.getVelocityMax(), volMultiplier, 1, 0);
        LG.d("Sum:" + skeleton.stream().mapToDouble(Note::getRhythmValue).sum());
        for (int i = 0; i < skeleton.size(); i++) {
            Note n1 = skeleton.get(i);
            if (n1.getPitch() >= 0) {
                n1.setPitch(n1.getPitch() + mp.getTranspose());
            }

            //LG.d(" durCounter: " + durCounter);
            if (durCounter > (currentChordDur - DBL_ERR)) {
                chordCounter = (chordCounter + 1) % durations.size();
                if (chordCounter == 0) {
                    measureCounter++;
                    // when measure resets
                    if (variationGenerator.nextInt(100) < gc.getArrangementPartVariationChance()) {
                        splitChance = (int) (splitChance * 1.2);
                    }
                }
                durCounter -= currentChordDur;
                if (durCounter < 0) {
                    durCounter = 0.0;
                }
                currentChordDur = durations.get(chordCounter);
                //splitGenerator.setSeed(seed + 4);
                //pauseGenerator.setSeed(seed + 5);
                //pauseGenerator2.setSeed(seed + 7);
                splitNoteGenerator.setSeed(orderSeed + 8);
                splitNoteExceptionGenerator.setSeed(orderSeed + 9);
                LG.n("Conversion chord#: " + chordCounter + ", duration: " + currentChordDur);
            }

            double adjDur = n1.getRhythmValue();
            //LG.d("Processing dur: " + adjDur + ", durCounter: " + durCounter);
            int velocity = velocityGenerator.nextInt(maxVel - minVel) + minVel;
            double positionInChord = durCounter / durations.get(chordCounter);
            if (positionInChord < DBL_ERR && accentGenerator.nextInt(100) < mp.getAccents()) {
                velocity = MidiGeneratorUtils.addAccent(velocity, accentGenerator, mp.getAccents());
            }

            n1.setDynamic(velocity);


            durCounter += adjDur;

            boolean splitLastNoteInChord = (chordLeadingGenerator.nextInt(100) < mp
                    .getLeadChordsChance()) && (adjDur > timing.dottedSixteenthNote * 1.1)
                    && (i < skeleton.size() - 1)
                    && ((durCounter + skeleton.get(i + 1).getRhythmValue()) > currentChordDur);


            if ((adjDur > timing.eighthNote * 1.4 && splitGenerator.nextInt(100) < splitChance)
                    || splitLastNoteInChord) {

                int pitch1 = n1.getPitch();
                int indexN2 = (i + 1) % skeleton.size();
                Note n2 = skeleton.get(indexN2);
                int pitch2 = n2.getPitch() + (indexN2 > 0 ? mp.getTranspose() : 0);
                if (pitch1 >= pitch2) {
                    int higherNote = pitch1;
                    if (splitNoteExceptionGenerator.nextInt(100) < 33 && !splitLastNoteInChord) {
                        higherNote += RANDOM_SPLIT_NOTE_PITCH_EXCEPTION_RANGE;
                    }
                    pitch2 = MidiGeneratorUtils.getAllowedPitchFromRange(pitch2, higherNote,
                            positionInChord, splitNoteGenerator);
                } else {
                    int lowerNote = pitch1;
                    if (splitNoteExceptionGenerator.nextInt(100) < 33 && !splitLastNoteInChord) {
                        lowerNote -= RANDOM_SPLIT_NOTE_PITCH_EXCEPTION_RANGE;
                    }
                    pitch2 = MidiGeneratorUtils.getAllowedPitchFromRange(lowerNote, pitch2,
                            positionInChord, splitNoteGenerator);
                }

                double multiplier = (MidiGeneratorUtils.isDottedNote(adjDur, timing)
                        && splitGenerator.nextBoolean()) ? (1.0 / 3.0) : 0.5;

                double swingDuration1 = adjDur * multiplier;
                double swingDuration2 = swingDuration1;

                Note n1split1 = new Note(pitch1, swingDuration1, velocity);
                Note n1split2 = new Note(pitch1 >= 0 ? pitch2 : Pitches.REST, swingDuration2, velocity - 10);
                fullMelody.add(n1split1);
                fullMelodyMap.get(chordCounter + chords.size() * measureCounter).add(n1split1);

                fullMelody.add(n1split2);
                fullMelodyMap.get(chordCounter + chords.size() * measureCounter).add(n1split2);

                if (multiplier < 0.4) {
                    int pitch3 = (splitGenerator.nextBoolean()) ? pitch1 : pitch2;
                    double swingDuration3 = swingDuration1;
                    Note n1split3 = new Note(pitch1 >= 0 ? pitch3 : Pitches.REST, swingDuration3, velocity - 20);
                    fullMelody.add(n1split3);
                    fullMelodyMap.get(chordCounter + chords.size() * measureCounter).add(n1split3);
                }

            } else {
                fullMelody.add(n1);
                fullMelodyMap.get(chordCounter + chords.size() * measureCounter).add(n1);
            }
        }
        List<Integer> firstNotePitches = fullMelodyMap.values().stream()
                .map(e -> e.isEmpty() ? Pitches.REST : e.get(0).getPitch())
                .collect(Collectors.toList());

        List<Integer> fillerPattern = mp.getChordSpanFill()
                .getPatternByLength(fullMelodyMap.size(), mp.isFillFlip());

        int[] pitches = new int[12];
        fullMelody.forEach(e -> {
            int pitch = e.getPitch();
            if (pitch >= 0) {
                pitches[pitch % 12]++;
            }
        });
        applyNoteTargets(fullMelody, fullMelodyMap, pitches, notesSeedOffset, chords, sec, mp,
                modScale);

        if (!ScaleMode.LOCRIAN.equals(gc.getScaleMode())) {
            MidiGeneratorUtils.applyBadIntervalRemoval(fullMelody);
        }

        if (gc.getMelodyReplaceAvoidNotes() > 0) {
            MidiGeneratorUtils.replaceNearChordNotes(fullMelodyMap, chords,
                    mp.getPatternSeedWithPartOffset(), gc.getMelodyReplaceAvoidNotes(), timing);
        }

        // pause by %, sort not-paused into pitches
        for (int chordIndex = 0; chordIndex < fullMelodyMap.size(); chordIndex++) {
            List<Note> notes = MelodyUtils
                    .sortNotesByRhythmicImportance(fullMelodyMap.get(chordIndex), timing);
            //Collections.sort(notes, Comparator.comparing(e -> e.getRhythmValue()));
            pauseGenerator.setSeed(orderSeed + 5);
            int actualPauseChance = MidiGeneratorUtils.adjustChanceParamForTransition(
                    mp.getPauseChance(), sec, chordIndex, durations.size(), 40, 0.25, false, true);
            int pausedNotes = (int) Math.round(notes.size() * actualPauseChance / 100.0);
            int startIndex = (mp.isFillPauses())
                    ? (gc.isMelodyFillPausesPerChord() ? 1 : ((chordIndex == 0) ? 1 : 0))
                    : 0;
            //LG.i("Pausing first # sorted notes: " + pausedNotes);
            for (int j = 0; j < pausedNotes; j++) {
                Note n = notes.get(j);
                if (startIndex == 1) {
                    if (n.equals(fullMelodyMap.get(chordIndex).get(0))) {
                        //pitches[n.getPitch() % 12]++;
                        continue;
                    }
                }
                n.setPitch(Pitches.REST);
            }
            for (int j = pausedNotes; j < notes.size(); j++) {
                Note n = notes.get(j);
                if (fillerPattern.get(chordIndex) < 1) {
                    n.setPitch(Pitches.REST);
                }
            }
        }


        // fill pauses toggle
        if (mp.isFillPauses()) {
            Note fillPauseNote = fullMelody.get(0);
            double addedDuration = 0;
            double addedRv = 0;
            List<Note> notesToRemove = new ArrayList<>();

            int currentChordIndex = 0;
            int currentChordCount = 1;

            // 0 1 2 3 | 4 5 6 7 | 8
            // size of 0: 4
            // processing 4:

            for (int i = 1; i < fullMelody.size(); i++) {
                Note n = fullMelody.get(i);
                currentChordCount++;
                if (currentChordCount > fullMelodyMap.get(currentChordIndex).size()) {
                    currentChordIndex++;
                    currentChordCount = 1;
                }

                if (n.getPitch() < 0
                        && !(currentChordCount == 1 && gc.isMelodyFillPausesPerChord())) {
                    addedRv += n.getRhythmValue();
                    if (fillerPattern.get(currentChordIndex) > 0) {
                        addedDuration += n.getRhythmValue();
                    }

                    notesToRemove.add(n);
                } else {
                    fillPauseNote.setDuration(fillPauseNote.getDuration() + addedDuration);
                    fillPauseNote.setRhythmValue(fillPauseNote.getRhythmValue() + addedRv);
                    //LG.d("Filled note duration: " + fillPauseNote.getRhythmValue());
                    addedDuration = 0;
                    addedRv = 0;
                    fillPauseNote = n;
                }
            }
            fullMelody.removeAll(notesToRemove);
            fullMelodyMap.values().forEach(e -> e.removeAll(notesToRemove));

            if (addedDuration > DBL_ERR || addedRv > DBL_ERR) {
                fillPauseNote.setDuration(fillPauseNote.getDuration() + addedDuration);
                fillPauseNote.setRhythmValue(fillPauseNote.getRhythmValue() + addedRv);
            }
        }

        Random startNoteRand = new Random(orderSeed + 25);

        // repair target notes
        for (int chordIndex = 0; chordIndex < firstNotePitches.size(); chordIndex++) {
            if (!fullMelodyMap.get(chordIndex).isEmpty()) {
                Note n = fullMelodyMap.get(chordIndex).get(0);
                if (fillerPattern.get(chordIndex) > 0
                        && (n.getPitch() >= 0 || gc.isMelodyFillPausesPerChord())) {
                    n.setPitch(firstNotePitches.get(chordIndex));
                }
            }
        }
        // accent lengths of first notes in chord, if not paused and next note has different pitch
        fullMelodyMap.values().forEach(e -> {
            if (e.size() < 3) {
                return;
            }
            Note n = e.get(0);
            int pitch = n.getPitch();
            if (pitch >= 0 && n.getDuration() < timing.quarterNote * 1.1
                    && e.get(1).getPitch() != pitch && e.get(2).getPitch() != pitch) {
                n.setDuration(n.getDuration() * (1 + (mp.getAccents() / 200.0)));
            }
        });

        // repair target notes
        for (int i = 0; i < firstNotePitches.size(); i++) {
            if (!fullMelodyMap.get(i).isEmpty()) {
                Note n = fullMelodyMap.get(i).get(0);
                double preferredDelay = timing.getMelodySkeletonDurations()[startNoteRand.nextInt(3)];
                if (n.getPitch() >= 0 && startNoteRand.nextInt(100) >= mp.getStartNoteChance()) {
                    int sixteenths = (int) Math.floor(n.getDuration() / timing.sixteenthNote);
                    double usedDelay = -1;
                    switch (sixteenths) {
                        case 0:
                        case 1:
                            n.setPitch(Pitches.REST);
                            break;
                        case 2:
                            usedDelay = timing.sixteenthNote;
                            break;
                        case 3:
                        case 4:
                            usedDelay = Math.min(preferredDelay, timing.eighthNote);
                            break;
                        default:
                            usedDelay = Math.min(preferredDelay, timing.dottedEighthNote);
                            break;
                    }
                    if (usedDelay > 0) {
                        n.setOffset(n.getOffset() + usedDelay);
                        n.setDuration(n.getDuration() - usedDelay);
                    }
                }
            }
        }

        if (sec.getVariation(0, mp.getAbsoluteOrder(gc.getMelodyParts())).contains(3)) {
            double currRv = 0;
            for (int chordIndex = 0; chordIndex < fullMelodyMap.size(); chordIndex++) {
                List<Note> notes = fullMelodyMap.get(chordIndex);
                for (Note n : notes) {
                    if (n.getPitch() >= 0) {
                        double noteStart = currRv + n.getOffset();
                        double noteEnd = noteStart + n.getDuration();

                        double closestEndOnGrid = Math.floor(noteEnd / timing.eighthNote)
                                * timing.eighthNote;
                        if (closestEndOnGrid > (noteStart + timing.sixteenthNote / 2)) {
                            n.setDuration(closestEndOnGrid - noteStart);
                        }
                    }
                }
            }
        }

        return fullMelodyMap;
    }

    protected void applyNoteTargets(List<Note> fullMelody, Map<Integer, List<Note>> fullMelodyMap,
                                    int[] pitches, int notesSeedOffset, List<int[]> chords, Section sec,
                                    MelodyPart mp, ScaleMode modScale) {
        // --------- NOTE ADJUSTING ---------------
        int[] chordSeparators = new int[fullMelodyMap.size() + 1];
        chordSeparators[0] = 0;
        for (Integer i : fullMelodyMap.keySet()) {
            int index = i + 1;
            chordSeparators[index] = fullMelodyMap.get(i).size() + chordSeparators[index - 1];
        }
        int surplusTonics = applyTonicNoteTargets(fullMelody, fullMelodyMap, pitches,
                notesSeedOffset, chordSeparators);
        List<Note> modeNoteChanges = applyModeNoteTargets(fullMelody, fullMelodyMap, pitches,
                surplusTonics, modScale);
        applyChordNoteTargets(fullMelody, fullMelodyMap, chords, modeNoteChanges, sec, mp);

    }

    private int applyTonicNoteTargets(List<Note> fullMelody, Map<Integer, List<Note>> fullMelodyMap,
                                      int[] pitches, int notesSeedOffset, int[] chordSeparators) {
        double requiredPercentageCs = gc.getMelodyTonicNoteTarget() / 100.0;
        int needed = (int) Math.floor(
                fullMelody.stream().filter(e -> e.getPitch() >= 0).count() * requiredPercentageCs);
        LG.d("Found C's: " + pitches[0] + ", needed: " + needed);
        int surplusTonics = pitches[0] - needed;

        if (gc.getMelodyTonicNoteTarget() > 0 && notesSeedOffset == 0) {
            // for main sections: try to adjust notes towards C if there isn't enough C's
            if (surplusTonics < 0) {
                //LG.d("Correcting melody!");
                int investigatedChordIndex = chordSeparators.length - 1;


                // adjust in pairs starting from last
                while (investigatedChordIndex > 0 && surplusTonics < 0) {
                    int end = chordSeparators[investigatedChordIndex] - 1;
                    // ignore first note in chord - user selectable target note
                    int investigatedChordStart = chordSeparators[investigatedChordIndex - 1] + 1;
                    for (int i = end; i >= investigatedChordStart; i--) {
                        Note n = fullMelody.get(i);
                        int p = n.getPitch();
                        if (p < 0) {
                            continue;
                        }
                        // D
                        if (p % 12 == 2) {
                            n.setPitch(p - 2);
                            surplusTonics++;
                            break;
                        }
                        // B
                        if (p % 12 == 11) {
                            n.setPitch(p + 1);
                            surplusTonics++;
                            break;
                        }
                    }
                    investigatedChordIndex -= 2;
                }

                //LG.d("Remaining difference after last pairs: " + difference);

                // adjust in pairs starting from last-1
                investigatedChordIndex = chordSeparators.length - 2;
                while (investigatedChordIndex > 0 && surplusTonics < 0) {
                    int end = chordSeparators[investigatedChordIndex] - 1;
                    int investigatedChordStart = chordSeparators[investigatedChordIndex - 1] + 1;
                    for (int i = end; i >= investigatedChordStart; i--) {
                        Note n = fullMelody.get(i);
                        int p = n.getPitch();
                        if (p < 0) {
                            continue;
                        }
                        // D
                        if (p % 12 == 2) {
                            n.setPitch(p - 2);
                            surplusTonics++;
                            break;
                        }
                        // B
                        if (p % 12 == 11) {
                            n.setPitch(p + 1);
                            surplusTonics++;
                            break;
                        }
                    }
                    investigatedChordIndex -= 2;
                }

                LG.d("TONIC: Remaining difference after first pairs: " + surplusTonics);

            }
        }
        return surplusTonics;
    }

    private List<Note> applyModeNoteTargets(List<Note> fullMelody,
                                            Map<Integer, List<Note>> fullMelodyMap, int[] pitches,
                                            int surplusTonics, ScaleMode modScale) {

        ScaleMode scale = (modScale != null) ? modScale : gc.getScaleMode();
        List<Note> modeNoteChanges = new ArrayList<>();
        if (gc.getMelodyModeNoteTarget() > 0 && scale.modeTargetNote >= 0) {
            double requiredPercentage = gc.getMelodyModeNoteTarget() / 100.0;
            int needed = (int) Math.ceil(fullMelody.stream().filter(e -> e.getPitch() >= 0).count()
                    * requiredPercentage);

            int modeNote = MidiUtils.MAJ_SCALE.get(scale.modeTargetNote);
            LG.d("Found Mode notes: " + pitches[modeNote] + ", needed: " + needed);
            if (pitches[modeNote] < needed) {
                int chordSize = fullMelodyMap.size();
                int difference = needed - pitches[modeNote];


                List<Note> allNotesExceptFirsts = new ArrayList<>();
                for (int chordIndex = 0; chordIndex < chordSize; chordIndex++) {
                    List<Note> chordNotes = fullMelodyMap.get(chordIndex);
                    if (chordNotes.size() > 1) {
                        allNotesExceptFirsts.addAll(chordNotes.subList(1, chordNotes.size()));
                    }
                }
                allNotesExceptFirsts.sort((e1, e2) -> MidiUtils.compareNotesByDistanceFromModeNote(e1, e2, modeNote));
                for (Note n : allNotesExceptFirsts) {
                    int pitch = n.getPitch();
                    if (pitch < 0) {
                        continue;
                    }
                    int semitone = pitch % 12;
                    int jump = modeNote - semitone;
                    if (jump > 6) {
                        n.setPitch(MidiUtils.octavePitch(pitch) + modeNote - 12);
                    } else if (jump < -6) {
                        n.setPitch(MidiUtils.octavePitch(pitch) + modeNote + 12);
                    } else {
                        n.setPitch(MidiUtils.octavePitch(pitch) + modeNote);
                    }
                    //LG.i(pitch - n.getPitch());
                    difference--;
                    modeNoteChanges.add(n);
                    if (difference <= 0) {
                        break;
                    }
                }
                LG.d("MODE: Remaining difference after first pairs: " + (-1 * difference));
            }

        }
        return modeNoteChanges;
    }

    private void applyChordNoteTargets(List<Note> fullMelody,
                                       Map<Integer, List<Note>> fullMelodyMap, List<int[]> chords, List<Note> modeNoteChanges,
                                       Section sec, MelodyPart mp) {

        int chordNoteTargetChance = sec.getVariation(0, mp.getAbsoluteOrder(gc.getMelodyParts())).contains(3)
                ? (gc.getMelodyChordNoteTarget()
                + (100 - gc.getMelodyChordNoteTarget()) / 3)
                : gc.getMelodyChordNoteTarget();

        if (chordNoteTargetChance > 0) {
            int chordSize = fullMelodyMap.size();
            double requiredPercentage = chordNoteTargetChance / 100.0;
            int needed = (int) Math.ceil(fullMelody.stream().filter(e -> e.getPitch() >= 0).count()
                    * requiredPercentage);
            // step 1: get count of how many

            // step 2: get % of how many of the others need to be turned into chord notes

            // step 3: apply %
            int found = 0;
            for (int chordIndex = 0; chordIndex < chordSize; chordIndex++) {
                List<Note> notes = fullMelodyMap.get(chordIndex);
                List<Integer> chordNotes = MidiUtils
                        .chordToPitches(chords.get(chordIndex % chords.size()));
                for (Note n : notes) {
                    if (n.getPitch() >= 0 && chordNotes.contains(n.getPitch() % 12)) {
                        found++;
                    }
                }
            }

            LG.d("Found Chord notes: " + found + ", needed: " + needed);
            if (found < needed) {
                int difference = needed - found;
				/*int chanceToConvertOthers = 100
						- ((100 * (fullMelody.size() - difference)) / fullMelody.size());*/

                for (int chordIndex = 0; chordIndex < chordSize; chordIndex++) {
                    if (difference <= 0) {
                        break;
                    }
                    int maxDifferenceForThisChord = Math.max(1, (difference + 4) / (chordSize));
                    List<Note> notes = fullMelodyMap.get(chordIndex);
                    List<Integer> chordNotes = MidiUtils
                            .chordToPitches(chords.get(chordIndex % chords.size()));

                    List<Note> sortedNotes = new ArrayList<>(notes);
                    sortedNotes.sort((e1, e2) -> MidiUtils
                            .compareNotesByDistanceFromChordPitches(e1, e2, chordNotes));

                    // put mode notes at the end, but still sorted - so that chord notes start primarily from corrections of regular notes
                    List<Note> sortedModeNotes = new ArrayList<>();
                    for (Note sortedNote : sortedNotes) {
                        if (modeNoteChanges.contains(sortedNote)) {
                            sortedModeNotes.add(sortedNote);
                        }
                    }
                    sortedNotes.removeAll(sortedModeNotes);
                    sortedNotes.addAll(sortedModeNotes);

                    for (Note n : notes) {
                        if (n.getPitch() < 0) {
                            continue;
                        }
                        if (!chordNotes.contains(n.getPitch() % 12)) {
                            n.setPitch(n.getPitch() - (n.getPitch() % 12)
                                    + MidiUtils.getClosestFromList(chordNotes, n.getPitch() % 12));
                            difference--;
                            maxDifferenceForThisChord--;
                        }
                        if (difference <= 0 || maxDifferenceForThisChord <= 0) {
                            break;
                        }
                    }
                }

                LG.d("CHORD: Remaining difference: " + (-1 * difference));
            }
        }
    }
    MelodyBlock convertCustomMelodyBlock(MelodyBlock mb, List<PhraseNote> customBlock) {
        // TODO: dynamic of skeleton is not used at all during skeleton conversion - e.g. embellished notes custom dynamic not used
        // TODO: use custom note dynamic for actual notes? - enabled via extra option

        MelodyBlock newMb = new MelodyBlock(customBlock.stream().map(e -> e.getDynamic() > 0 ? e.getPitch() : Pitches.REST).collect(Collectors.toList()),
                customBlock.stream().map(PhraseNote::getDuration).collect(Collectors.toList()), false);
        int counter = 0;
        for (int i = 0; i < mb.notes.size(); i++) {
            for (int j = counter; j < newMb.notes.size(); j++) {
                if (newMb.notes.get(j) != Pitches.REST) {
                    newMb.notes.set(j, mb.notes.get(i));
                    counter = j;
                    break;
                }
            }
        }
        return newMb;
    }

    List<Note> addEmbellishedNotes(Note n, Random embellishmentGenerator) {
        // pick embellishment rhythm - if note RV is multiple of dotted 8th use length 3, otherwise length 4
        int notesNeeded = MidiUtils.isMultiple(n.getRhythmValue(), timing.dottedEighthNote) ? 3
                : 4;
        Random localEmbGenerator = new Random(embellishmentGenerator.nextInt());
        if (localEmbGenerator.nextInt(100) >= MelodyGenerator.EMBELLISHMENT_CHANCE) {
            return Collections.singletonList(n);
        }
        boolean shortNotes = n.getRhythmValue() > timing.quarterNote - DBL_ERR;
        int[] melodySkeletonDurationWeights = shortNotes
                ? MelodyUtils.normalizedCumulativeWeights(100, 100, 300, 100, 300, 100, 100)
                : MelodyUtils.normalizedCumulativeWeights(100, 300, 100, 300, 100, 100);
        Rhythm blockRhythm = new Rhythm(localEmbGenerator.nextInt(), n.getRhythmValue(),
                shortNotes ? timing.getShortMelodySkeletonDurations()
                        : timing.getMelodySkeletonDurations(),
                melodySkeletonDurationWeights);
        List<Double> blockDurations = blockRhythm.makeDurations(notesNeeded,
                timing.sixteenthNote);

        // split note according to rhythm, apply pitch change to notes
        // pick embellishment pitch pattern according to rhythm length (3 or 4)
        List<Integer[]> pitchPatterns = BlockType.NEIGHBORY.blocks.stream()
                .filter(e -> e.length == notesNeeded).collect(Collectors.toList());
        if (pitchPatterns.isEmpty()) {
            LG.e("No melody pitch patterns found for: " + notesNeeded);
            return Collections.singletonList(n);
        }
        Integer[] chosenPattern = pitchPatterns
                .get(localEmbGenerator.nextInt(pitchPatterns.size()));
        int pitch = n.getPitch();
        if (pitch == Pitches.REST) {
            return Collections.singletonList(n);
        }
        int semis = pitch % 12;
        int octavePitch = pitch - semis;
        List<Note> embNotes = new ArrayList<>();
        for (int i = 0; i < blockDurations.size(); i++) {
            int pitchIndex = MidiUtils.MAJ_SCALE.indexOf(semis);
            if (pitchIndex < 0) {
                LG.e("------------------------------------------------PITCH FROM MAIN MELODY BLOCK WAS NOT IN MAJOR SCALE!");
            }
            int newPitchIndex = pitchIndex + chosenPattern[i];
            int newPitchSemis = MidiUtils.MAJ_SCALE.get((newPitchIndex + 70) % 7);
            int newPitch = octavePitch + newPitchSemis;
            if (newPitchIndex >= 7) {
                newPitch += 12;
            } else if (newPitchIndex < 0) {
                newPitch -= 12;
            }
            Note embNote = new Note(newPitch, blockDurations.get(i));
            embNote.setDuration(blockDurations.get(i) * globalDurationMultiplier);

            // slightly lower volume on following notes
            embNote.setDynamic(n.getDynamic() - (i * 5));

            // add note
            embNotes.add(embNote);
        }
        return embNotes;
    }

}
