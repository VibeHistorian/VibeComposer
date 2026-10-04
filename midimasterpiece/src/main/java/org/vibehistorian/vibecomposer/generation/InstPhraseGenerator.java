package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Phrase;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Section;

import java.util.ArrayList;
import java.util.List;

/** Shared configuration and result shapes for instrument phrase renderers. */
abstract class InstPhraseGenerator<P extends InstPart> {
    static final double FILLER_NOTE_MIN_DURATION = 0.05;

    @FunctionalInterface
    interface VariationGenerator {
        List<Integer> fill(Section section, InstPart part, List<Integer> variations,
                           int instrumentGroup, List<Double> chanceMultipliers);
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
