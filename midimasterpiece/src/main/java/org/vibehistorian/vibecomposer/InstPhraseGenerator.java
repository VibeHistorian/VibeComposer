package org.vibehistorian.vibecomposer;

import jm.music.data.Phrase;
import org.vibehistorian.vibecomposer.Parts.InstPart;

import java.util.ArrayList;
import java.util.List;

/** Shared configuration and result shapes for instrument phrase renderers. */
abstract class InstPhraseGenerator<P extends InstPart> {
    @FunctionalInterface
    interface VariationGenerator {
        List<Integer> fill(Section section, InstPart part, List<Integer> variations,
                           int instrumentGroup, List<Double> chanceMultipliers);
    }

    static final class Timing {
        final double sixteenthNote;
        final double eighthNote;
        final double quarterNote;
        final double dottedQuarterNote;
        final double halfNote;
        final double dottedHalfNote;
        final double wholeNote;
        final double noteMultiplier;
        final double globalDurationMultiplier;
        final double fillerNoteMinDuration;
        final double doubleError;

        Timing(double sixteenthNote, double eighthNote, double quarterNote,
               double dottedQuarterNote, double halfNote, double dottedHalfNote,
               double wholeNote, double noteMultiplier, double globalDurationMultiplier,
               double fillerNoteMinDuration, double doubleError) {
            this.sixteenthNote = sixteenthNote;
            this.eighthNote = eighthNote;
            this.quarterNote = quarterNote;
            this.dottedQuarterNote = dottedQuarterNote;
            this.halfNote = halfNote;
            this.dottedHalfNote = dottedHalfNote;
            this.wholeNote = wholeNote;
            this.noteMultiplier = noteMultiplier;
            this.globalDurationMultiplier = globalDurationMultiplier;
            this.fillerNoteMinDuration = fillerNoteMinDuration;
            this.doubleError = doubleError;
        }
    }

    static class Result {
        private final Phrase phrase;
        private final List<Integer> variations;
        private final boolean storeVariations;

        Result(Phrase phrase, List<Integer> variations, boolean storeVariations) {
            this.phrase = phrase;
            this.variations = variations;
            this.storeVariations = storeVariations;
        }

        Phrase getPhrase() {
            return phrase;
        }

        List<Integer> getVariations() {
            return variations;
        }

        boolean shouldStoreVariations() {
            return storeVariations;
        }
    }

    protected final GUIConfig gc;
    private final VariationGenerator variationGenerator;

    InstPhraseGenerator(GUIConfig gc, VariationGenerator variationGenerator) {
        this.gc = gc;
        this.variationGenerator = variationGenerator;
    }

    protected List<Integer> fillVariations(Section section, P part,
                                           List<Integer> variations, int instrumentGroup) {
        return variationGenerator.fill(section, part, variations, instrumentGroup,
                new ArrayList<>());
    }

    protected List<Integer> fillVariations(Section section, P part,
                                           List<Integer> variations, int instrumentGroup,
                                           List<Double> chanceMultipliers) {
        return variationGenerator.fill(section, part, variations, instrumentGroup,
                chanceMultipliers);
    }
}
