package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Note;
import java.util.*;

/** Production MelodyPatternUtils onset buckets, including rests and boundary offsets. */
public class MelodyContextFixture {
    public static void main(String[] args) {
        boolean flip = args.length > 0 && args[0].equals("flip");
        List<Object> cases = new ArrayList<>();
        for (int sample = 0; sample < 64; sample++) {
            Map<Integer, List<Note>> byChord = new LinkedHashMap<>(); List<Object> input = new ArrayList<>();
            int count = 1 + sample % 4;
            for (int chord = 0; chord < count; chord++) {
                List<Note> notes = new ArrayList<>(); double time = 0;
                for (int index = 0; index < 16; index++) {
                    int pitch = (sample + index) % 4 == 0 ? jm.constants.Pitches.REST : 60 + (sample + index) % 12;
                    Note note = new Note(pitch, 0.25, 80);
                    double offset = new double[] {0, 0.245, 0.239, 0.01}[(sample / 4) % 4];
                    note.setOffset(offset); notes.add(note);
                    Map<String, Object> event = new LinkedHashMap<>(); event.put("midi", pitch < 0 ? -1 : pitch); event.put("startBeat", chord * 4 + time + offset); input.add(event);
                    time += 0.25;
                }
                byChord.put(chord, notes);
            }
            Map<Integer, List<Integer>> patterns = MelodyPatternUtils.patternsFromNotes(byChord, Collections.nCopies(count, 4.0), 1, flip, new MidiTiming(100));
            List<Object> grids = new ArrayList<>(); for (int chord = 0; chord < count; chord++) grids.add(patterns.get(chord));
            Map<String, Object> entry = new LinkedHashMap<>(); entry.put("chordCount", count); entry.put("notes", input); entry.put("pitches", grids); cases.add(entry);
        }
        System.out.println(ArpCoreFixture.json(cases));
    }
}
