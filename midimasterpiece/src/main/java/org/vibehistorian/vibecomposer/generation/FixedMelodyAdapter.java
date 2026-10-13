package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Note;
import jm.music.data.Phrase;
import org.vibehistorian.vibecomposer.MidiUtils;
import java.util.*;

/** Authored notes bypass pitch/rhythm repair. Copies are repeated only as complete motifs. */
public final class FixedMelodyAdapter {
    private FixedMelodyAdapter() { }
    public static Map<Integer,List<Note>> group(Phrase source,HarmonyTimeline harmony,double quarter,
            MidiUtils.ScaleMode mode,int partTranspose) {
        Phrase copy = source.copy(); List<Note> original = copy.getNoteList();
        double length = original.stream().mapToDouble(Note::getRhythmValue).sum();
        long motif = HarmonyTimeline.ticks(length / quarter);
        if (motif <= 0 || harmony.length() % motif != 0) {
            throw new IllegalArgumentException("Fixed melody must fit the section as complete motifs; adjust section durations");
        }
        // Authored MIDI is already realized. Mode realization must not rewrite its pitches.
        Map<Integer,List<Note>> result = new LinkedHashMap<>(); for (HarmonyTimeline.Event event : harmony.events()) { result.put(event.index,new ArrayList<>()); }
        for (long repeat = 0; repeat < harmony.length(); repeat += motif) {
            double cursor = repeat / 960.0 * quarter;
            for (Note note : original) {
                long start = HarmonyTimeline.ticks(cursor / quarter);
                Note value = note.copy(); if (value.getPitch() >= 0) { value.setPitch(value.getPitch() + partTranspose); }
                result.get(harmony.at(start).index).add(value); cursor += note.getRhythmValue();
            }
        }
        return result;
    }
}
