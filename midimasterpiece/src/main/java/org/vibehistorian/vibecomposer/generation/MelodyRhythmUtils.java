package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Note;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Rhythm transforms shared by melody generation and phrase preparation. */
final class MelodyRhythmUtils {
    private MelodyRhythmUtils() {
    }

    static List<Note> sortNotesByRhythmicImportance(List<Note> notes, MidiTiming timing) {
        List<Note> sorted = new ArrayList<>();
        List<Note> main8th = new ArrayList<>();
        List<Note> main16th = new ArrayList<>();
        List<Note> others = new ArrayList<>();

        double currTime = 0;
        for (Note n : notes) {
            if (MidiUtils.isMultiple(currTime + n.getOffset(), timing.eighthNote)) {
                main8th.add(n);
            } else if (MidiUtils.isMultiple(currTime + n.getOffset(), timing.sixteenthNote)) {
                main16th.add(n);
            } else {
                others.add(n);
            }
            currTime += n.getRhythmValue();
        }
        main8th.sort(Comparator.comparing(Note::getRhythmValue));
        main16th.sort(Comparator.comparing(Note::getRhythmValue));
        others.sort(Comparator.comparing(Note::getRhythmValue));
        sorted.addAll(others);
        sorted.addAll(main16th);
        sorted.addAll(main8th);
        LG.n("Others: " + others.size() + ", 16th: " + main16th.size() + ", 8th: "
                + main8th.size());
        return sorted;
    }

    static List<Double> makeSurpriseTrioArpedDurations(List<Double> durations, MidiTiming timing) {
        List<Double> arpedDurations = new ArrayList<>(durations);
        for (int trioIndex = 0; trioIndex < arpedDurations.size() - 2; trioIndex++) {
            double sumThirds = arpedDurations.subList(trioIndex, trioIndex + 3).stream()
                    .mapToDouble(e -> e).sum();
            boolean valid = false;
            if (MidiGeneratorUtils.isDottedNote(sumThirds, timing)) {
                sumThirds /= 3.0;
                for (int trio = trioIndex; trio < trioIndex + 3; trio++) {
                    arpedDurations.set(trio, sumThirds);
                }
                valid = true;
            } else if (MidiUtils.isMultiple(sumThirds, timing.halfNote)) {
                if (sumThirds > timing.dottedHalfNote) {
                    sumThirds /= 4.0;
                    for (int trio = trioIndex; trio < trioIndex + 3; trio++) {
                        arpedDurations.set(trio, sumThirds);
                    }
                    arpedDurations.add(trioIndex, sumThirds);
                } else {
                    sumThirds /= 2.0;
                    for (int trio = trioIndex + 1; trio < trioIndex + 3; trio++) {
                        arpedDurations.set(trio, sumThirds);
                    }
                    arpedDurations.remove(trioIndex++);
                }
                valid = true;
            }

            if (valid) {
                return arpedDurations;
            }
        }
        return null;
    }
}
