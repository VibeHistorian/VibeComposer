package org.vibehistorian.vibecomposer.generators;

import jm.music.data.Note;
import java.util.List;
import java.util.Map;

final class MelodyGenerationState {
    final Map<Integer, List<Note>> chordMelodyMap1;
    List<int[]> melodyBasedChordProgression;
    final List<int[]> melodyBasedRootProgression;
    String alternateChords;

    MelodyGenerationState(Map<Integer, List<Note>> chordMelodyMap1,
            List<int[]> melodyBasedChordProgression, List<int[]> melodyBasedRootProgression) {
        this.chordMelodyMap1 = chordMelodyMap1;
        this.melodyBasedChordProgression = melodyBasedChordProgression;
        this.melodyBasedRootProgression = melodyBasedRootProgression;
    }
}
