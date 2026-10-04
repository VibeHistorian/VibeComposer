package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Note;
import jm.music.data.Phrase;
import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import static org.vibehistorian.vibecomposer.Constants.DBL_ERR;
import static org.vibehistorian.vibecomposer.MidiUtils.squishChordProgression;

final class MelodyChordInference {
    private final GUIConfig gc;
    private final MidiGenerator mg;
    private final MelodyGenerationState state;

    MelodyChordInference(GUIConfig gc, MidiGenerator mg, MelodyGenerationState state) {
        this.gc = gc;
        this.mg = mg;
        this.state = state;
    }
    void processUserMelody(Phrase userMelody) {
        if (!state.chordMelodyMap1.isEmpty() || !mg.getUserChords().isEmpty()) {
            return;
        }

        int chordCounter = 0;

        double mult = MidiGenerator.getBeatDurationMult(gc, mg.currentSection);
        double separatorValue = mg.getTiming().wholeNote * mult;
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
        List<String> chordStrings = MelodyUtils.getChordsFromMelodyPitches(1, mg.progressionDurations,
                state.chordMelodyMap1, MidiUtils.freqMap, mg.getTiming());
		/*List<String> spicyChordStrings = getChordsFromMelodyPitches(1, state.chordMelodyMap1,
				MidiUtils.freqMap);
		for (int i = 0; i < spicyChordStrings.size(); i++) {
			if (chordStrings.get(i).charAt(0) == spicyChordStrings.get(i).charAt(0)) {
				chordStrings.set(i, spicyChordStrings.get(i));
			}
		}*/

        populateMelodyBasedProgression(chordStrings, 0, state.chordMelodyMap1.keySet().size());
        mg.progressionDurations = progDurations;
        mg.replaceGeneratedChordNames(chordStrings);
    }

    void populateMelodyBasedProgression(List<String> chordStrings, int start, int end) {
        List<int[]> altChordProg = new ArrayList<>();

        for (int i = 0; i < start; i++) {
            state.melodyBasedRootProgression
                    .add(Arrays.copyOf(mg.rootProgression.get(i), mg.rootProgression.get(i).length));
            altChordProg
                    .add(Arrays.copyOf(mg.chordProgression.get(i), mg.chordProgression.get(i).length));
        }
        for (int i = start; i < end; i++) {
            int[] mappedChord = MidiUtils.mappedChord(chordStrings.get(i));
            altChordProg.add(mappedChord);
            state.melodyBasedRootProgression.add(Arrays.copyOf(mappedChord, mappedChord.length));
        }
        for (int i = end; i < chordStrings.size(); i++) {
            state.melodyBasedRootProgression
                    .add(Arrays.copyOf(mg.rootProgression.get(i), mg.rootProgression.get(i).length));
            altChordProg
                    .add(Arrays.copyOf(mg.chordProgression.get(i), mg.chordProgression.get(i).length));
        }

        state.melodyBasedChordProgression = squishChordProgression(altChordProg,
                gc.isSpiceFlattenBigChords(), gc.getRandomSeed(),
                gc.getChordGenSettings().getFlattenVoicingChance(), new ArrayList<>(), null);


        mg.chordProgression = state.melodyBasedChordProgression;
        mg.rootProgression = state.melodyBasedRootProgression;
        LG.i(StringUtils.join(chordStrings, ","));
    }

}
