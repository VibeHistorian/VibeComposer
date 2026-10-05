package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Note;
import org.vibehistorian.vibecomposer.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Converts generated melody notes into the rhythm patterns consumed by other parts. */
final class MelodyPatternUtils {
    private MelodyPatternUtils() {
    }

    static Map<Integer, List<Integer>> patternsFromNotes(Map<Integer, List<Note>> fullMelodyMap,
            List<Double> progressionDurations, double beatDurationMultiplier, boolean flip,
            MidiTiming timing) {
        Map<Integer, List<Integer>> patterns = new HashMap<>();
        for (Integer chordKey : fullMelodyMap.keySet()) {
            patterns.put(chordKey, patternFromNotes(fullMelodyMap.get(chordKey), 1,
                    progressionDurations.get(chordKey % progressionDurations.size()),
                    beatDurationMultiplier, flip, timing));
        }
        return patterns;
    }

    static List<Integer> getRandomMelodyPattern(int altPatternChance, Integer randomSeed) {
        Random rand = new Random();
        if (randomSeed != null) {
            rand.setSeed(randomSeed);
        }
        if (rand.nextInt(100) < altPatternChance) {
            return new ArrayList<>(MelodyUtils.MELODY_PATTERNS
                    .get(MelodyUtils.ALT_PATTERN_INDEXES
                            .get(rand.nextInt(MelodyUtils.ALT_PATTERN_INDEXES.size()))));
        }
        return new ArrayList<>(MelodyUtils.MELODY_PATTERNS
                .get(rand.nextInt(MelodyUtils.MELODY_PATTERNS.size())));
    }

    private static List<Integer> patternFromNotes(List<Note> notes, int chordsTotal,
            Double measureTotal, double beatDurationMultiplier, boolean flip, MidiTiming timing) {
        int hits = (int) Math.round(
                chordsTotal * MidiTiming.MELODY_PATTERN_RESOLUTION * measureTotal / timing.wholeNote);
        measureTotal = (measureTotal == null)
                ? (chordsTotal * beatDurationMultiplier * timing.wholeNote) : measureTotal;
        double timeForHit = measureTotal / hits;
        List<Integer> pattern = new ArrayList<>();
        List<Double> durationBuckets = new ArrayList<>();
        for (int i = 1; i <= hits; i++) {
            durationBuckets.add(timeForHit * i - Constants.DBL_ERR);
            pattern.add(0);
        }

        if (notes == null || notes.isEmpty()) {
            return pattern;
        }

        int explored = 0;
        int counter = 0;
        List<Double> startTimes = new ArrayList<>();
        double current = 0.0;
        for (Note n : notes) {
            startTimes.add(current + n.getOffset());
            current += n.getRhythmValue();
        }

        boolean skipCounter = startTimes.get(0) < Constants.DBL_ERR;
        if (skipCounter) {
            pattern.set(0, (!notes.isEmpty() && notes.get(0).getPitch() < 0) ? 0
                    : notes.get(0).getPitch());
        }

        for (Note n : notes) {
            if (counter == 0 && skipCounter) {
                counter++;
                continue;
            }
            for (int i = explored; i < hits; i++) {
                if (startTimes.get(counter) < durationBuckets.get(i)) {
                    int nextPitch = Math.max(n.getPitch(), 0);
                    pattern.set(i, nextPitch);
                    explored = i;
                    break;
                }
            }
            counter++;
        }
        if (flip) {
            pattern.replaceAll(integer -> integer > 0 ? 0 : 1);
        }
        return pattern;
    }
}
