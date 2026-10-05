package org.vibehistorian.vibecomposer.generation;

import jm.constants.Pitches;
import jm.music.data.Note;
import jm.music.data.Phrase;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Section;

import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.Collectors;

/** Phrase level transition and delay effects. */
final class PhraseEffectUtils {
    private PhraseEffectUtils() {
    }

    static int adjustChanceParamForTransition(int param, Section sec, int chordNum,
            int chordSize, int maxEffect, double affectedMeasure, boolean reverseEffect,
            boolean clampChance) {
        if (chordSize < 2 || !sec.isTransition()) {
            return param;
        }

        int minAffectedChord = OMNI.clamp((int) (affectedMeasure * chordSize) - 1, 1,
                chordSize - 1);
        if (chordNum < minAffectedChord) {
            return param;
        }

        int chordRange = chordSize - 1 - minAffectedChord;
        double effect = (chordRange > 0) ? ((chordNum - minAffectedChord) / ((double) chordRange))
                : 1.0;
        int multiplier = reverseEffect ? -1 : 1;
        if (sec.getTransitionType() == 1) {
            param += (int) (maxEffect * effect * multiplier);
        } else {
            param -= (int) (maxEffect * effect * multiplier);
        }
        if (clampChance) {
            param = OMNI.clampChance(param);
        }
        return param;
    }

    static void applyCrescendoMultiplierMinimum(List<Note> notes, double maxDuration,
            double crescendoStartPercentage, double maxMultiplierAdd, double minimum) {
        double dur = 0.0;
        double start = maxDuration * crescendoStartPercentage;
        for (Note n : notes) {
            if (dur > start) {
                double multiplier = minimum
                        + maxMultiplierAdd * ((dur - start) / (maxDuration - start));
                if (multiplier < 0.1) {
                    n.setPitch(Pitches.REST);
                } else {
                    n.setDynamic(OMNI.clampVel(n.getDynamic() * multiplier));
                }
            }
            dur += n.getRhythmValue();
        }
    }

    static void applyCrescendoMultiplier(List<Note> notes, double maxDuration,
            double crescendoStartPercentage, double maxMultiplierAdd) {
        applyCrescendoMultiplierMinimum(notes, maxDuration, crescendoStartPercentage,
                maxMultiplierAdd, 1);
    }

    static void processSectionTransition(Section sec, List<Note> notes, double maxDuration,
            double crescendoStartPercentage, double maxMultiplierAdd, double muteStartPercentage) {
        if (sec.isTransition()) {
            applyCrescendoMultiplier(notes, maxDuration, crescendoStartPercentage,
                    maxMultiplierAdd);
            if (sec.getTransitionType() == 3) {
                applyCrescendoMultiplierMinimum(notes, maxDuration, muteStartPercentage, 0.05,
                        0.05);
            }
        }
    }

    static void multiDelayPhrase(Phrase phr, int delayCount, double delayAmnt,
            double volMultiplier) {
        if (delayCount <= 0) {
            return;
        }
        List<Double> delays = DoubleStream.iterate(delayAmnt, e -> e + delayAmnt).limit(delayCount)
                .boxed().collect(Collectors.toList());
        List<Double> volMultipliers = DoubleStream.iterate(volMultiplier, e -> e * volMultiplier)
                .limit(delays.size()).boxed().collect(Collectors.toList());
        multiDelayPhrase(phr, delays, volMultipliers);
    }

    static void multiDelayPhrase(Phrase phr, List<Double> delays, List<Double> volMultipliers) {
        if (delays.isEmpty() || (delays.size() != volMultipliers.size())) {
            return;
        }

        List<Note> notes = phr.getNoteList();
        int size = notes.size();
        int currIndex = 0;
        for (int i = 0; i < size; i++) {
            Note n = notes.get(currIndex);
            for (int j = 0; j < delays.size(); j++) {
                Double delay = delays.get(j);
                Double volMult = volMultipliers.get(j);
                Note nd = new Note(n.getPitch(), 0, OMNI.clampVel(n.getDynamic() * volMult));
                nd.setDuration(n.getDuration());
                nd.setOffset(n.getOffset() + delay);
                notes.add(currIndex, nd);
            }
            currIndex += 1 + delays.size();
        }
    }
}
