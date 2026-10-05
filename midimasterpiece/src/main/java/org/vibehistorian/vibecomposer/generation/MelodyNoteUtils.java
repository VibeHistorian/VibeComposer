package org.vibehistorian.vibecomposer.generation;

import jm.constants.Pitches;
import jm.music.data.Note;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.Constants;
import org.vibehistorian.vibecomposer.JMusicUtilsCustom;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.OMNI;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Pitch, articulation, and collision adjustments applied to melody notes. */
public final class MelodyNoteUtils {
    private MelodyNoteUtils() {
    }

    public static int getAllowedPitchFromRange(int min, int max, double posInChord,
            Random splitNoteGen) {
        boolean allowBs = posInChord > 0.66;
        int normMin = min % 12;
        int normMax = max % 12;
        if (normMax <= normMin) {
            normMax += 12;
        }

        List<Integer> allowedPitches = new ArrayList<>(MidiUtils.MAJ_SCALE);
        int allowedPitchSize = allowedPitches.size();
        for (int i = 0; i < allowedPitchSize; i++) {
            allowedPitches.add(allowedPitches.get(i) + 12);
        }
        final int finalNormMax = normMax;
        allowedPitches.removeIf(e -> !(normMin <= e && e <= finalNormMax));
        if (!allowBs) {
            allowedPitches.remove(Integer.valueOf(11));
            allowedPitches.remove(Integer.valueOf(23));
        }
        int normReturnPitch = allowedPitches.get(splitNoteGen.nextInt(allowedPitches.size()));
        while (normReturnPitch < min) {
            normReturnPitch += 12;
        }
        return normReturnPitch;
    }

    static void applyNoteLengthMultiplier(List<Note> notes, int noteLengthMultiplier) {
        if (noteLengthMultiplier == 100) {
            return;
        }
        boolean avoidSamePitchCollision = true;
        List<Pair<Double, Note>> sn = JMusicUtilsCustom.makeNoteStartTimes(notes);
        for (int i = 0; i < sn.size(); i++) {
            Note n = sn.get(i).getRight();
            double duration = n.getDuration() * noteLengthMultiplier / 100.0;
            if (avoidSamePitchCollision && noteLengthMultiplier > 100) {
                if (i < sn.size() - 1 && n.getPitch() == sn.get(i + 1).getRight().getPitch()) {
                    double difference = sn.get(i + 1).getLeft() - sn.get(i).getLeft();
                    duration = Math.min(duration, difference);
                } else if (i < sn.size() - 2
                        && n.getPitch() == sn.get(i + 2).getRight().getPitch()) {
                    double difference = sn.get(i + 2).getLeft() - sn.get(i).getLeft();
                    duration = Math.min(duration, difference);
                }
            }
            n.setDuration(duration);
        }
    }

    public static void applySamePitchCollisionAvoidance(List<Note> notes) {
        List<Pair<Double, Note>> sn = JMusicUtilsCustom.makeNoteStartTimes(notes);
        for (int i = 0; i < sn.size(); i++) {
            Note n = sn.get(i).getRight();
            double duration = n.getDuration();
            if (i < sn.size() - 1 && n.getPitch() == sn.get(i + 1).getRight().getPitch()) {
                double difference = sn.get(i + 1).getLeft() - sn.get(i).getLeft();
                duration = Math.min(duration, difference);
            } else if (i < sn.size() - 2
                    && n.getPitch() == sn.get(i + 2).getRight().getPitch()) {
                double difference = sn.get(i + 2).getLeft() - sn.get(i).getLeft();
                duration = Math.min(duration, difference);
            }
            n.setDuration(duration);
        }
    }

    static int addAccent(int velocity, Random accentGenerator, int accent) {
        int newVelocity = velocity + MidiGenerator.BASE_ACCENT + accentGenerator.nextInt(11) - 5
                + accent / 20;
        return OMNI.clampMidi(newVelocity);
    }

    static void applyBadIntervalRemoval(List<Note> fullMelody) {
        if (fullMelody.isEmpty()) {
            return;
        }
        int previousPitch = fullMelody.get(0).getPitch();
        if (previousPitch < 0) {
            previousPitch = Pitches.REST;
        }
        for (int i = 1; i < fullMelody.size(); i++) {
            Note n = fullMelody.get(i);
            int pitch = n.getPitch();
            if (pitch < 0 || previousPitch == Pitches.REST) {
                previousPitch = pitch;
                continue;
            }
            if (previousPitch % 12 == 11 && Math.abs(pitch - previousPitch) == 6) {
                n.setPitch(pitch - 1);
            } else if (pitch % 12 == 11 && Math.abs(pitch - previousPitch) == 6) {
                n.setPitch(pitch + 1);
            } else if ((i > 0) && (pitch - previousPitch >= 12)) {
                int avgPitch = (pitch + previousPitch) / 2;
                int avgSemi = avgPitch % 12;
                if (avgSemi > 9) {
                    n.setPitch(avgPitch - avgSemi + 12);
                } else if (avgSemi < 3) {
                    n.setPitch(avgPitch - avgSemi);
                } else {
                    n.setPitch(avgPitch - avgSemi + 7);
                }
                LG.i("Reducing interval - changing note to: " + n.getPitch());
            }
            previousPitch = n.getPitch();
        }
    }

    static void replaceNearChordNotes(Map<Integer, List<Note>> fullMelodyMap, List<int[]> chords,
            int randomSeed, int notesToAvoid, MidiTiming timing) {
        Random rand = new Random(randomSeed);
        for (int i = 0; i < fullMelodyMap.keySet().size(); i++) {
            Set<Integer> avoidNotes = MidiUtils.getNearNotesFromChord(chords.get(i % chords.size()),
                    notesToAvoid);
            List<Note> notes = fullMelodyMap.get(i);
            for (int j = 0; j < notes.size(); j++) {
                Note n = notes.get(j);
                int oldPitch = n.getPitch();
                if (oldPitch < 0) {
                    continue;
                }
                boolean avoidAllLengths = true;
                if (avoidAllLengths || (n.getRhythmValue() > timing.eighthNote
                        - Constants.DBL_ERR)) {
                    if (avoidNotes.contains(oldPitch % 12)) {
                        int normalizedPitch = oldPitch % 12;
                        int pitchIndex = MidiUtils.MAJ_SCALE.indexOf(normalizedPitch);
                        int upOrDown = rand.nextBoolean() ? 1 : -1;
                        int newPitch = oldPitch - normalizedPitch;
                        newPitch += MidiUtils.MAJ_SCALE.get((pitchIndex + upOrDown + 7) % 7);
                        if (pitchIndex == 6 && upOrDown == 1) {
                            newPitch += 12;
                        } else if (pitchIndex == 0 && upOrDown == -1) {
                            newPitch -= 12;
                        }
                        n.setPitch(newPitch);
                    }
                }
            }
        }
    }
}
