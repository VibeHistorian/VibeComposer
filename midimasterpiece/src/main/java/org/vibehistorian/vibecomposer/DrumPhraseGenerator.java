package org.vibehistorian.vibecomposer;

import jm.constants.Pitches;
import jm.music.data.Note;
import jm.music.data.Phrase;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Helpers.PhraseExt;
import org.vibehistorian.vibecomposer.Parts.DrumPart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Creates the note pattern for one drum part in one section. */
final class DrumPhraseGenerator extends InstPhraseGenerator<DrumPart> {
    static final class DrumResult extends Result {
        private final boolean patternMissingForInstrument;

        private DrumResult(Phrase phrase, List<Integer> variations, boolean patternMissingForInstrument,
                           boolean storeVariations) {
            super(phrase, variations, storeVariations);
            this.patternMissingForInstrument = patternMissingForInstrument;
        }

        boolean isPatternMissingForInstrument() {
            return patternMissingForInstrument;
        }

    }

    DrumPhraseGenerator(GUIConfig gc, VariationGenerator variationGenerator) {
        super(gc, variationGenerator);
    }

    DrumResult generate(DrumPart ip, List<int[]> actualProgression, List<Double> progressionDurations,
                    List<String> chordInts, List<Integer> melodyNotePattern,
                    Map<Integer, List<Integer>> melodyNotePatternMap,
                    boolean sectionForcedDynamics, Section sec, int measures,
                    List<Integer> variations,
                    int sectionOrder, Timing timing) {
        boolean genVars = variations == null;
        Phrase phr = new PhraseExt(4, ip.getOrder(), sectionOrder);

        boolean kicky = ip.getInstrument() < 38;
        boolean aboveSnarey = ip.getInstrument() > 40;
        sectionForcedDynamics &= (kicky || aboveSnarey);

        int chordsCount = actualProgression.size();
        List<Integer> drumPattern = MidiGeneratorUtils.generateDrumPatternFromPart(ip,
                melodyNotePattern, chordInts.size());

        if (!ip.isVelocityPattern() && !drumPattern.contains(ip.getInstrument())) {
            return new DrumResult(phr, variations, true, false);
        }

        List<Integer> drumVelocityPattern = generateDrumVelocityPatternFromPart(sec, ip);
        Random drumFillGenerator = new Random(
                ip.getPatternSeedWithPartOffset() + ip.getOrderOffset() + sec.getTypeMelodyOffset());
        int hits = ip.getHitsPerPattern();
        int swingPercentAmount = (hits % 2 == 0) ? ip.getSwingPercent() : 50;

        List<Integer> fillPattern = ip.getChordSpanFill()
                .getPatternByLength(actualProgression.size(), ip.isFillFlip());

        for (int o = 0; o < measures; o++) {
            Random exceptionGenerator = new Random(
                    ip.getPatternSeedWithPartOffset() + ip.getOrderOffset());
            int chordSpan = ip.getChordSpan();
            boolean ignoreChordSpanFill = false;
            int extraExceptionChance = 0;
            boolean drumFill = false;
            for (int chordIndex = 0; chordIndex < chordsCount; chordIndex += chordSpan) {

                if (genVars && ((chordIndex == 0) || (chordIndex == chordInts.size()))) {
                    List<Double> chanceMultipliers = sec.isTransition()
                            ? Arrays.asList(1.0, 1.0, 2.0)
                            : null;
                    variations = fillVariations(sec, ip, variations, 4, chanceMultipliers);
                }

                double halfDurMulti = (chordIndex >= (chordsCount + 1) / 2
                        && sec.getTransitionType() == 4) ? 2.0 : 1.0;

                if ((variations != null) && (chordIndex == 0)) {
                    for (Integer var : variations) {
                        switch (var) {
                        case 0:
                            ignoreChordSpanFill = true;
                            break;
                        case 1:
                            extraExceptionChance = (kicky || aboveSnarey)
                                    ? ip.getExceptionChance() + 10
                                    : ip.getExceptionChance();
                            break;
                        case 2:
                            drumFill = (kicky || aboveSnarey);
                            break;
                        default:
                            throw new IllegalArgumentException("Too much variation!");
                        }
                    }
                }

                double patternDurationTotal = 0.0;
                for (int k = 0; k < chordSpan; k++) {
                    patternDurationTotal += (progressionDurations.size() > chordIndex + k)
                            ? progressionDurations.get(chordIndex + k)
                            : 0.0;
                }

                double drumDuration = (ip.getPattern() == RhythmPattern.MELODY1
                        && melodyNotePatternMap != null) ? timing.sixteenthNote
                                : timing.wholeNote * chordSpan / hits;
                drumDuration *= halfDurMulti;
                double durationNow = 0.0;
                int k = 0;
                while (durationNow + timing.doubleError < patternDurationTotal) {
                    int drum = drumPattern.get(k);
                    int velocity = drumVelocityPattern.get(k);
                    int pitch = (drum >= 0) ? drum : Pitches.REST;
                    if (drum < 0 && (ip.isVelocityPattern() || (o > 0 && sectionForcedDynamics))) {
                        velocity = (velocity * 5) / 10;
                        pitch = ip.getInstrument();
                    }
                    int chordNumAdd = 0;
                    double durationNowCheck = durationNow + timing.doubleError
                            - progressionDurations.get(chordIndex);
                    while (durationNowCheck > 0.0) {
                        chordNumAdd++;
                        if (progressionDurations.size() <= (chordNumAdd + chordIndex)) {
                            break;
                        }
                        durationNowCheck -= progressionDurations.get(chordIndex + chordNumAdd);
                    }
                    int chordNum = chordIndex + chordNumAdd;
                    boolean forceLastFilled = drumFill
                            && (chordNum == actualProgression.size() - 1);
                    if (!ignoreChordSpanFill && !forceLastFilled) {
                        if (fillPattern.get(chordNum % actualProgression.size()) < 1) {
                            pitch = Pitches.REST;
                        }
                    }

                    int drumFillExceptionChance = 0;
                    double usedDrumDuration = drumDuration;
                    if (forceLastFilled) {
                        k++;
                        usedDrumDuration *= 2;
                        drumFillExceptionChance = 60;

                        int drumFillUnpauseChance = ip.getInstrument() < 46 ? 20 : 10;
                        if (pitch < 0 && drumFillGenerator.nextInt(100) < drumFillUnpauseChance) {
                            pitch = ip.getInstrument();
                        }
                    }
                    boolean exception = exceptionGenerator.nextInt(100) < (ip.getExceptionChance()
                            + extraExceptionChance + drumFillExceptionChance);

                    if (durationNow + usedDrumDuration - timing.doubleError > patternDurationTotal) {
                        usedDrumDuration = patternDurationTotal - durationNow;
                        if (usedDrumDuration < timing.fillerNoteMinDuration) {
                            pitch = Pitches.REST;
                        }
                    }

                    if (exception) {
                        int secondVelocity = (velocity * 8) / 10;
                        Note n1 = new Note(pitch, usedDrumDuration / 2, velocity);
                        Note n2 = new Note(pitch, usedDrumDuration / 2, secondVelocity);
                        n1.setDuration(0.5 * n1.getRhythmValue() * timing.globalDurationMultiplier);
                        n2.setDuration(0.5 * n2.getRhythmValue() * timing.globalDurationMultiplier);
                        phr.addNote(n1);
                        phr.addNote(n2);
                    } else {
                        Note n1 = new Note(pitch, usedDrumDuration, velocity);
                        n1.setDuration(0.5 * n1.getRhythmValue() * timing.globalDurationMultiplier);
                        phr.addNote(n1);
                    }
                    durationNow += usedDrumDuration;
                    k = (k + 1) % drumPattern.size();
                }
            }
        }

        return new DrumResult(phr, variations, false, genVars && variations != null);
    }

    private List<Integer> generateDrumVelocityPatternFromPart(Section sec, DrumPart dp) {
        Random uiGenerator1drumVelocityPattern = new Random(
                dp.getPatternSeedWithPartOffset() + dp.getOrderOffset());
        List<Integer> drumVelocityPattern = new ArrayList<>();
        int multiplier = (gc.isScaleMidiVelocityInArrangement()) ? sec.getVol(4) : 100;
        if (dp.getCustomVelocities() != null
                && dp.getCustomVelocities().size() >= dp.getHitsPerPattern()) {
            for (int i = 0; i < dp.getHitsPerPattern(); i++) {
                drumVelocityPattern.add(MidiGeneratorUtils
                        .multiplyVelocity(dp.getCustomVelocities().get(i), multiplier, 0, 1));
            }
        } else {
            int minVel = MidiGeneratorUtils.multiplyVelocity(dp.getVelocityMin(), multiplier, 0, 1);
            int maxVel = MidiGeneratorUtils.multiplyVelocity(dp.getVelocityMax(), multiplier, 1, 0);
            int velocityRange = maxVel - minVel;
            for (int j = 0; j < dp.getHitsPerPattern(); j++) {
                int velocity = uiGenerator1drumVelocityPattern.nextInt(velocityRange) + minVel;
                drumVelocityPattern.add(velocity);
            }
        }
        return drumVelocityPattern;
    }
}
