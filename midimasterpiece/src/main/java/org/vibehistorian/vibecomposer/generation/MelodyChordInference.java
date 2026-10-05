package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Note;
import jm.music.data.Phrase;
import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Section;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.Comparator;
import java.util.stream.Collectors;

import static org.vibehistorian.vibecomposer.Constants.DBL_ERR;
import static org.vibehistorian.vibecomposer.MidiUtils.squishChordProgression;

final class MelodyChordInference {
    private final GUIConfig gc;
    private final MidiTiming timing;
    private final MelodyGenerationState state;
    private final RunStateUpdater runStateUpdater;

    MelodyChordInference(GUIConfig gc, MidiTiming timing, MelodyGenerationState state,
            RunStateUpdater runStateUpdater) {
        this.gc = gc;
        this.timing = timing;
        this.state = state;
        this.runStateUpdater = runStateUpdater;
    }

    static List<String> getChordsFromMelodyPitches(int orderOfMatch, List<Double> durations,
            Map<Integer, List<Note>> melodyMap, Map<String, Set<Integer>> freqMap,
            MidiTiming timing) {
        List<String> chordStrings = new ArrayList<>();
        String prevChordString = null;

        for (int i = 0; i < melodyMap.size(); i++) {
            List<Integer> chordFreqs = new ArrayList<>();
            double totalDuration = 0;
            for (Note n : melodyMap.get(i)) {
                double dur = n.getRhythmValue();
                double durCounter = 0.0;
                int index = i;
                if (index >= durations.size()) {
                    index = durations.size() - 1;
                }
                while (durCounter < dur && totalDuration < durations.get(index)) {
                    chordFreqs.add(n.getPitch() % 12);
                    durCounter += timing.eighthNote;
                    totalDuration += timing.eighthNote;
                }
            }

            Map<Integer, Long> freqCounts = chordFreqs.stream()
                    .collect(Collectors.groupingBy(e -> e, Collectors.counting()));

            Map<Integer, Long> top3 = freqCounts.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(4)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                            (e1, e2) -> e1, LinkedHashMap::new));

            String chordString = MidiUtils.applyChordFreqMap(top3, orderOfMatch, prevChordString,
                    freqMap);
            LG.d("Alternate chord #" + i + ": " + chordString);
            chordStrings.add(chordString);
            prevChordString = chordString;
        }
        return chordStrings;
    }

    void processUserMelody(Phrase userMelody, Section currentSection, List<String> userChords,
            List<Double> progressionDurations, List<int[]> rootProgression,
            List<int[]> chordProgression) {
        if (!state.chordMelodyMap1.isEmpty() || !userChords.isEmpty()) {
            return;
        }

        int chordCounter = 0;

        double mult = MidiGenerator.getBeatDurationMult(gc, currentSection);
        double separatorValue = timing.wholeNote * mult;
        double chordSeparator = separatorValue;
        Vector<Note> noteList = userMelody.getNoteList();
        if (!state.chordMelodyMap1.containsKey(0)) {
            state.chordMelodyMap1.put(0, new ArrayList<>());
        }
        double rhythmCounter = 0;
        List<Double> progDurations = new ArrayList<>();
        progDurations.add(separatorValue);
        for (Note n : noteList) {
            LG.d("Rhythm counter: " + rhythmCounter);
            if (rhythmCounter >= chordSeparator - DBL_ERR) {
                LG.d("NEXT CHORD!");
                chordSeparator += separatorValue;
                chordCounter++;
                progDurations.add(separatorValue);
                if (!state.chordMelodyMap1.containsKey(chordCounter)) {
                    state.chordMelodyMap1.put(chordCounter, new ArrayList<>());
                }
            }
            state.chordMelodyMap1.get(chordCounter).add(n);
            rhythmCounter += n.getRhythmValue();
        }
        LG.d("Rhythm counter end: " + rhythmCounter);
        while (rhythmCounter >= chordSeparator + DBL_ERR) {
            LG.d("NEXT CHORD!");
            chordSeparator += separatorValue;
            chordCounter++;
            progDurations.add(separatorValue);
            if (!state.chordMelodyMap1.containsKey(chordCounter)) {
                state.chordMelodyMap1.put(chordCounter, new ArrayList<>());
            }
            state.chordMelodyMap1.get(chordCounter)
                    .add(noteList.get(noteList.size() - 1));
        }
        LG.i("Processed melody, chords: " + (chordCounter + 1));
        List<String> chordStrings = getChordsFromMelodyPitches(1, progressionDurations,
                state.chordMelodyMap1, MidiUtils.freqMap, timing);
		/*List<String> spicyChordStrings = getChordsFromMelodyPitches(1, state.chordMelodyMap1,
				MidiUtils.freqMap);
		for (int i = 0; i < spicyChordStrings.size(); i++) {
			if (chordStrings.get(i).charAt(0) == spicyChordStrings.get(i).charAt(0)) {
				chordStrings.set(i, spicyChordStrings.get(i));
			}
		}*/

        populateMelodyBasedProgression(chordStrings, 0, state.chordMelodyMap1.keySet().size(),
                rootProgression, chordProgression);
        runStateUpdater.userMelodyProgressionChanged(progDurations, chordStrings);
    }

    void populateMelodyBasedProgression(List<String> chordStrings, int start, int end,
            List<int[]> rootProgression, List<int[]> chordProgression) {
        List<int[]> altChordProg = new ArrayList<>();

        for (int i = 0; i < start; i++) {
            state.melodyBasedRootProgression
                    .add(Arrays.copyOf(rootProgression.get(i), rootProgression.get(i).length));
            altChordProg
                    .add(Arrays.copyOf(chordProgression.get(i), chordProgression.get(i).length));
        }
        for (int i = start; i < end; i++) {
            int[] mappedChord = MidiUtils.mappedChord(chordStrings.get(i));
            altChordProg.add(mappedChord);
            state.melodyBasedRootProgression.add(Arrays.copyOf(mappedChord, mappedChord.length));
        }
        for (int i = end; i < chordStrings.size(); i++) {
            state.melodyBasedRootProgression
                    .add(Arrays.copyOf(rootProgression.get(i), rootProgression.get(i).length));
            altChordProg
                    .add(Arrays.copyOf(chordProgression.get(i), chordProgression.get(i).length));
        }

        state.melodyBasedChordProgression = squishChordProgression(altChordProg,
                gc.isSpiceFlattenBigChords(), gc.getRandomSeed(),
                gc.getChordGenSettings().getFlattenVoicingChance(), new ArrayList<>(), null);


        runStateUpdater.progressionChanged(state.melodyBasedChordProgression,
                state.melodyBasedRootProgression);
        LG.i(StringUtils.join(chordStrings, ","));
    }

    interface RunStateUpdater {
        void progressionChanged(List<int[]> chordProgression, List<int[]> rootProgression);

        void userMelodyProgressionChanged(List<Double> progressionDurations, List<String> chordNames);
    }
}
