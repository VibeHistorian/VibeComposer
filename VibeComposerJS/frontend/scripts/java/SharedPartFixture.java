package org.vibehistorian.vibecomposer.generation;

import java.util.*;
import jm.music.data.Note;
import jm.music.data.Phrase;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;

/** Production slicing, voice conversion and note-producing delay helpers. */
public class SharedPartFixture {
    public static void main(String[] args) {
        StringJoiner cases = new StringJoiner(",\n", "[\n", "\n]");
        List<Integer> grid = new ArrayList<>(), dynamics = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            grid.add(index % 3 == 0 ? 1 : 0); dynamics.add(index * 4);
        }
        for (boolean arp : new boolean[] { false, true })
        for (int hits : new int[] { 1, 3, 8, 10, 32 })
        for (int span = 1; span <= 4; span++)
        for (int repeats = 1; repeats <= (arp ? 4 : 1); repeats++)
        for (boolean flip : new boolean[] { false, true })
        for (int chord = 0; chord < span; chord++) {
            ChordPart part = new ChordPart(); part.setPattern(RhythmPattern.CUSTOM);
            part.setCustomPattern(grid); part.setHitsPerPattern(hits); part.setPatternShift(2);
            List<Integer> base = part.getFinalPatternCopy().subList(0, hits);
            List<Integer> pattern = MidiUtils.intersperse(arp ? 0 : -1, span - 1, base);
            List<Integer> expanded = new ArrayList<>();
            for (int repeat = 0; repeat < repeats; repeat++) expanded.addAll(pattern);
            pattern = new ArrayList<>(PhrasePatternUtils.partOfListClean(chord, span, expanded));
            for (int index = 0; index < pattern.size(); index++) {
                int value = pattern.get(index);
                if (flip && value >= 0) pattern.set(index, 1 - value);
            }
            List<Integer> velocities = MidiUtils.intersperse(null, span - 1, dynamics.subList(0, hits));
            velocities = arp ? PhrasePatternUtils.partOfListClean(chord, span, velocities)
                    : PhrasePatternUtils.partOfList(chord, span, velocities);
            cases.add("{\"kind\":\"span\",\"arp\":" + arp + ",\"chordIndex\":" + chord
                    + ",\"settings\":{\"rhythm\":\"custom\",\"hitsPerPattern\":" + hits
                    + ",\"chordSpan\":" + span + ",\"patternRepeat\":" + repeats + ",\"patternShift\":2"
                    + ",\"patternFlip\":" + flip + ",\"customPattern\":" + grid
                    + ",\"useCustomVelocities\":true,\"customVelocities\":" + dynamics
                    + "},\"pattern\":" + pattern + ",\"velocities\":" + velocities + "}");
        }
        for (int[] pitches : new int[][] { { 60, 64, 67 }, { 60, 67, 76 }, { 48, 52, 55, 60, 64, 67 } })
        for (int voices = 2; voices <= 6; voices++)
        for (boolean enabled : new boolean[] { false, true }) {
            cases.add("{\"kind\":\"voices\",\"pitches\":" + Arrays.toString(pitches)
                    + ",\"settings\":{\"chordNotesStretch\":" + voices + ",\"stretchEnabled\":" + enabled
                    + "},\"result\":" + Arrays.toString(MidiUtils.convertChordToLength(pitches, voices, enabled)) + "}");
        }
        for (int count : new int[] { 0, 1, 3, 5 })
        for (int duration : new int[] { -2000, 0, 750, 2000 })
        for (int volume : new int[] { 10, 65, 150 }) {
            Phrase phrase = new Phrase(); Note note = new Note(60, 1, 90);
            note.setDuration(0.75); note.setOffset(0.25); phrase.addNote(note);
            PhraseEffectUtils.multiDelayPhrase(phrase, count, duration / 1000.0, volume / 100.0);
            StringJoiner notes = new StringJoiner(",", "[", "]");
            for (Object entry : phrase.getNoteList()) {
                Note event = (Note) entry;
                if (event.getDynamic() > 0) notes.add("[60," + (12 + event.getOffset()) + "," + event.getDuration() + "," + event.getDynamic() + "]");
            }
            cases.add("{\"kind\":\"timing\",\"settings\":{\"offset\":250,\"feedbackCount\":" + count
                    + ",\"feedbackDuration\":" + duration + ",\"feedbackVol\":" + volume + "},\"notes\":" + notes + "}");
        }
        System.out.println(cases);
    }
}
