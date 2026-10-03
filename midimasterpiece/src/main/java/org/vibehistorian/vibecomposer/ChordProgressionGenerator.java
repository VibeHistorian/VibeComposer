package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;

/**
 * Builds chord-name progressions from the configured progression rules.
 */
public final class ChordProgressionGenerator {

    static final class RandomStreams {
        final Random progression;
        final Random length;
        final Random spice;
        final Random parallel;
        final Random similarity;

        private RandomStreams(long seed) {
            progression = new Random(seed);
            length = new Random(seed);
            spice = new Random(seed);
            parallel = new Random(seed + 100);
            similarity = new Random(seed + 102);
        }
    }

    private ChordProgressionGenerator() {
    }

    static RandomStreams createRandomStreams(long seed) {
        return new RandomStreams(seed);
    }

    static int chooseProgressionLength(int fixedLength, Random lengthGenerator) {
        if (fixedLength == 0) {
            List<Integer> progressionLengths = Arrays.asList(4, 5, 6, 8);
            return progressionLengths.get(lengthGenerator.nextInt(progressionLengths.size()));
        }
        return fixedLength;
    }

    static List<String> getAllowedSpiceChordsMiddle(GUIConfig gc) {
        List<String> allowedSpiceChords = new ArrayList<>();
        for (int i = 2; i < MidiUtils.SPICE_NAMES_LIST.size(); i++) {
            String chordString = MidiUtils.SPICE_NAMES_LIST.get(i);
            if (!gc.isDimAug6thEnabled() && MidiUtils.BANNED_DIM_AUG_6_LIST.contains(chordString)) {
                continue;
            }
            if (!gc.isEnable9th13th() && MidiUtils.BANNED_9_13_LIST.contains(chordString)) {
                continue;
            }
            allowedSpiceChords.add(chordString);
        }
        return allowedSpiceChords;
    }

    static List<String> getAllowedSpiceChords(List<String> middleChords) {
        List<String> allowedSpiceChords = new ArrayList<>();
        for (String chordString : middleChords) {
            if (MidiUtils.BANNED_DIM_AUG_6_LIST.contains(chordString)
                    || MidiUtils.BANNED_SUS_LIST.contains(chordString)) {
                continue;
            }
            allowedSpiceChords.add(chordString);
        }
        return allowedSpiceChords;
    }

    static String applySpiceAndParallelChanges(GUIConfig gc, String chordString, boolean isLastChord,
                                                boolean hasPreviousChord, int generatedChordCount,
                                                String lastChord, Random generator, Random spiceGenerator,
                                                Random parallelGenerator, List<String> middleSpiceChords,
                                                List<String> otherSpiceChords) {
        List<String> spiceChords = (!isLastChord && hasPreviousChord)
                ? middleSpiceChords : otherSpiceChords;
        String spicyChordString = chordString;
        String tempSpicyChordString = MidiGeneratorUtils
                .generateSpicyChordString(spiceGenerator, chordString, spiceChords);

        if (generator.nextInt(100) < gc.getSpiceChance()
                && (generatedChordCount < 7 || lastChord == null)) {
            spicyChordString = tempSpicyChordString;
        }

        if (!gc.isDimAug6thEnabled()) {
            if (gc.getScaleMode() != ScaleMode.IONIAN && gc.getScaleMode().ordinal() < 7) {
                int scaleOrder = gc.getScaleMode().ordinal();
                if (MidiUtils.MAJOR_CHORDS.indexOf(chordString) == 6 - scaleOrder) {
                    spicyChordString = "Bdim";
                }
            }
        }
        if (parallelGenerator.nextInt(100) < gc.getSpiceParallelChance()) {
            int chordOrder = MidiUtils.MAJOR_CHORDS.indexOf(chordString);
            String parallelChordString = MidiUtils.MINOR_CHORDS.get(chordOrder);
            if (chordOrder != 1 || gc.isDimAug6thEnabled()) {
                spicyChordString = parallelChordString;
                LG.d("PARALLEL: " + spicyChordString);
            }
        }
        return spicyChordString;
    }

    static List<String> generateChordProgressionList(GUIConfig gc, long mainGeneratorSeed,
                                                       int fixedLength, String configuredFirstChord,
                                                       String configuredLastChord) {
        List<String> chordProgList = new ArrayList<>();

        RandomStreams randomStreams = createRandomStreams(mainGeneratorSeed);
        Random generator = randomStreams.progression;
        Random lengthGenerator = randomStreams.length;
        Random spiceGenerator = randomStreams.spice;
        Random parallelGenerator = randomStreams.parallel;
        Random similarityGenerator = randomStreams.similarity;

        boolean isBackwards = !gc.isUseChordFormula();
        Map<String, List<String>> rules = isBackwards ? MidiUtils.cpRulesMap : MidiUtils.cpRulesForwardMap;
        String lastChord = isBackwards ? configuredFirstChord : configuredLastChord;
        String firstChord = isBackwards ? configuredLastChord : configuredFirstChord;

        fixedLength = chooseProgressionLength(fixedLength, lengthGenerator);
        int maxLength = fixedLength > 0 ? fixedLength : 8;
        List<String> next = rules.get("S");
        if (firstChord != null) {
            next = new ArrayList<>();
            next.add(firstChord);
        }
        List<String> debugMsg = new ArrayList<>();
        List<String> allowedSpiceChordsMiddle = getAllowedSpiceChordsMiddle(gc);
        List<String> allowedSpiceChords = getAllowedSpiceChords(allowedSpiceChordsMiddle);

        String prevChord = null;
        boolean canRepeatChord = true;
        String lastUnspicedChord = null;
        Random chordRepeatGenerator = new Random(mainGeneratorSeed);
        for (int chordIndex = 0; chordIndex < maxLength; chordIndex++) {
            if (next.isEmpty() && prevChord != null) {
                LG.w("Next list is EMPTY! Adding default C chord!");
                next.add("C");
            }
            int bSkipper = (!gc.isDimAug6thEnabled() && "Bdim".equals(next.get(next.size() - 1))) ? 1 : 0;
            int nextInt = generator.nextInt(Math.max(next.size() - bSkipper, 1));

            boolean isLastChord = chordIndex == maxLength - 1;
            String chordString;
            if (isLastChord && lastChord != null) {
                chordString = lastChord;
            } else if (gc.isAllowChordRepeats() && (fixedLength < 8 || !isLastChord) && canRepeatChord
                    && chordProgList.size() == 1 && chordRepeatGenerator.nextInt(100) < 10) {
                chordString = lastUnspicedChord;
                canRepeatChord = false;
            } else {
                chordString = next.get(nextInt);
            }

            String spicyChordString = applySpiceAndParallelChanges(gc, chordString, isLastChord,
                    prevChord != null, chordProgList.size(), lastChord, generator, spiceGenerator,
                    parallelGenerator, allowedSpiceChordsMiddle, allowedSpiceChords);

            chordProgList.add(spicyChordString);
            debugMsg.add("Generated int: " + nextInt + ", for chord: " + spicyChordString);
            prevChord = spicyChordString;
            next = rules.get(chordString);

            if (fixedLength == 8 && chordProgList.size() == 4 && lastChord == null) {
                lastChord = chordString;
            }
            if (isLastChord && lastChord == null) {
                lastChord = chordString;
            }
            lastUnspicedChord = chordString;
        }
        if (isBackwards) {
            Collections.reverse(debugMsg);
            Collections.reverse(chordProgList);
        }

        for (String message : debugMsg) {
            LG.d(message);
        }

        if (fixedLength == 8) {
            int[] replacementOrder = new int[] { 4, 7, 5, 6 };
            for (int i : replacementOrder) {
                if (similarityGenerator.nextInt(100) < gc.getLongProgressionSimilarity()) {
                    chordProgList.set(i, chordProgList.get(i - 4));
                    LG.i("Replaced " + i + "-th chord!");
                } else if (i == 5) {
                    break;
                }
            }
        }

        return chordProgList;
    }
}
