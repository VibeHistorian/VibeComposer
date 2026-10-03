package org.vibehistorian.vibecomposer.generators;

import org.vibehistorian.vibecomposer.Arrangement;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.BassPart;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.SectionConfig;
import org.vibehistorian.vibecomposer.gui.DrumGUI;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Makes arrangement decisions that configure an individual section. */
final class SectionGenerationPlanner {

    static final class CustomProgression {
        final List<int[]> chords;
        final List<int[]> roots;
        final List<Double> durations;
        final List<String> chordNames;

        private CustomProgression(List<int[]> chords, List<int[]> roots,
                                  List<Double> durations, List<String> chordNames) {
            this.chords = chords;
            this.roots = roots;
            this.durations = durations;
            this.chordNames = chordNames;
        }
    }

    private SectionGenerationPlanner() {
    }

    static List<Integer> calculateVariations(GUIConfig gc, Arrangement arrangement,
                                             int sectionOrder, Section section,
                                             int notesSeedOffset, Random variationGenerator) {
        List<Integer> sectionVariations = section.getSectionVariations();
        if (sectionVariations == null) {
            sectionVariations = new ArrayList<>();
            for (int i = 0; i < Section.sectionVariationNames.length; i++) {
                boolean isVariation = variationGenerator.nextInt(100)
                        < gc.getArrangementVariationChance()
                        * Section.sectionVariationChanceMultipliers[i];
                isVariation &= gc.getArrangement().isGlobalVariation(5, i);

                if (i == 0 || i == 4) {
                    isVariation &= sectionOrder < arrangement.getSections().size() - 1
                            && arrangement.getSections().get(sectionOrder + 1).getType()
                                    .equals(section.getType());
                }
                if (i == 1 || i == 2 || i == 4) {
                    isVariation &= notesSeedOffset > 0;
                }
                if (i == 3) {
                    isVariation = false;
                }
                sectionVariations.add(isVariation ? 1 : 0);
            }
            section.setSectionVariations(sectionVariations);
        }
        return sectionVariations;
    }

    static void applyBuildupVariation(GUIConfig gc, Section section, boolean overridden,
                                      Random random) {
        if (overridden || !section.getType().toUpperCase().startsWith("BUILDUP")
                || random.nextInt(100) >= gc.getArrangementVariationChance()) {
            return;
        }

        List<Integer> exceptionChanceList = new ArrayList<>();
        exceptionChanceList.add(1);
        if (section.getPartMap().get(4) != null) {
            for (int i = 0; i < section.getPartMap().get(4).length; i++) {
                if (random.nextInt(100) < 66) {
                    section.setVariation(4, i, exceptionChanceList);
                }
            }
        }
    }

    static boolean replaceConfiguredParts(GUIConfig gc, Section section) {
        boolean needsReplace = false;
        if (section.getMelodyParts() != null) {
            gc.setMelodyParts(section.getMelodyParts());
            needsReplace = true;
        }
        if (section.getBassParts() != null) {
            gc.setBassParts(section.getBassParts());
            needsReplace = true;
        }
        if (section.getChordParts() != null) {
            gc.setChordParts(section.getChordParts());
            needsReplace = true;
        }
        if (section.getArpParts() != null) {
            gc.setArpParts(section.getArpParts());
            needsReplace = true;
        }
        if (section.getDrumParts() != null) {
            gc.setDrumParts(section.getDrumParts());
            needsReplace = true;
        }
        return needsReplace;
    }

    static CustomProgression prepareCustomProgression(GUIConfig gc, Section section,
                                                       SectionConfig currentSectionConfig,
                                                       List<Double> currentDurations,
                                                       String firstChord, String lastChord,
                                                       double wholeNoteDuration) {
        SectionConfig sectionConfig = currentSectionConfig;
        int beatDurationIndex = sectionConfig != null
                && sectionConfig.getBeatDurationMultiplierIndex() != null
                ? sectionConfig.getBeatDurationMultiplierIndex()
                : gc.getBeatDurationMultiplierIndex();
        double defaultDurationMultiplier = beatDurationIndex == 2 ? 2.0
                : beatDurationIndex == 0 ? 0.5 : 1.0;

        if (!section.isCustomChordsEnabled() && !section.isCustomDurationsEnabled()) {
            return null;
        }

        List<String> chords = section.getCustomChordsList();
        if ((chords == null || chords.isEmpty()) && section.isCustomChordsEnabled()) {
            return null;
        }
        List<Double> durations = section.getCustomDurationsList();
        if ((durations == null || durations.isEmpty()) && section.isCustomDurationsEnabled()) {
            return null;
        }

        if (section.isCustomChordsEnabled() && section.isCustomDurationsEnabled()) {
            if (chords.size() != durations.size()) {
                return null;
            }
        } else if (section.isCustomChordsEnabled()) {
            durations = new ArrayList<>();
            for (int i = 0; i < chords.size(); i++) {
                durations.add(i < currentDurations.size()
                        ? currentDurations.get(i)
                        : wholeNoteDuration * defaultDurationMultiplier);
            }
        } else {
            chords = ChordProgressionGenerator.generateChordProgressionList(gc,
                    gc.getRandomSeed(), durations.size(), firstChord, lastChord);
        }

        List<int[]> mappedChords = new ArrayList<>();
        List<int[]> mappedRootChords = new ArrayList<>();
        List<Integer> customInversionIndexes = new ArrayList<>();
        int chordNumber = 0;
        for (String chordString : chords) {
            mappedChords.add(MidiUtils.mappedChord(chordString));
            mappedRootChords.add(MidiUtils.mappedChord(chordString, true));
            if (chordString.contains(".")) {
                customInversionIndexes.add(chordNumber);
            }
            chordNumber++;
        }

        mappedChords = gc.isSquishProgressively()
                ? MidiUtils.squishChordProgressionProgressively(mappedChords,
                        gc.isSpiceFlattenBigChords(), gc.getRandomSeed(),
                        gc.getChordGenSettings().getFlattenVoicingChance(),
                        customInversionIndexes, mappedRootChords)
                : MidiUtils.squishChordProgression(mappedChords,
                        gc.isSpiceFlattenBigChords(), gc.getRandomSeed(),
                        gc.getChordGenSettings().getFlattenVoicingChance(),
                        customInversionIndexes, mappedRootChords);

        return new CustomProgression(mappedChords, mappedRootChords, durations, chords);
    }

    static void calculatePresences(GUIConfig gc, Section section, Random random,
                                    Random variationGenerator, boolean overridden,
                                    int arrangementSeed, int notesSeedOffset, boolean isPreview,
                                    int measureCounter, Arrangement arrangement) {
        if (gc.isMelodyEnable() && !gc.getMelodyParts().isEmpty()) {
            Set<Integer> presences = section.getPresence(0);
            for (int i = 0; i < gc.getMelodyParts().size(); i++) {
                MelodyPart melodyPart = gc.getMelodyParts().get(i);
                int melodyChanceMultiplier = section.getTypeMelodyOffset() == 0 && i == 0 ? 2 : 1;
                int oldChance = section.getMelodyChance();
                section.setMelodyChance(Math.min(100, oldChance * melodyChanceMultiplier));
                boolean added = !melodyPart.isMuted()
                        && ((overridden && presences.contains(melodyPart.getOrder()))
                        || (!overridden && random.nextInt(100) < section.getMelodyChance()));
                added &= gc.getArrangement().isPartInclusion(0, i, notesSeedOffset,
                        gc::getInstParts);
                if (added && !overridden) {
                    section.setPresence(0, i);
                }
                section.setMelodyChance(oldChance);
            }
        }

        random.setSeed(arrangementSeed + 10);
        variationGenerator.setSeed(arrangementSeed + 10);
        if (gc.isBassEnable() && !gc.getBassParts().isEmpty()) {
            Set<Integer> presences = section.getPresence(1);
            for (int i = 0; i < gc.getBassParts().size(); i++) {
                BassPart bassPart = gc.getBassParts().get(i);
                random.setSeed(arrangementSeed + 50 + bassPart.getOrderOffset());
                variationGenerator.setSeed(arrangementSeed + 50 + bassPart.getOrderOffset());
                boolean added = (overridden && presences.contains(bassPart.getOrder()))
                        || (!overridden && random.nextInt(100) < section.getBassChance());
                added &= gc.getArrangement().isPartInclusion(1, i, notesSeedOffset,
                        gc::getInstParts);
                if (added && !bassPart.isMuted() && !overridden) {
                    section.setPresence(1, i);
                }
            }
        }

        if (gc.isChordsEnable() && !gc.getChordParts().isEmpty()) {
            Set<Integer> presences = section.getPresence(2);
            for (int i = 0; i < gc.getChordParts().size(); i++) {
                ChordPart chordPart = gc.getChordParts().get(i);
                random.setSeed(arrangementSeed + 100 + chordPart.getOrderOffset());
                variationGenerator.setSeed(arrangementSeed + 100 + chordPart.getOrderOffset());
                boolean added = (overridden && presences.contains(chordPart.getOrder()))
                        || (!overridden && random.nextInt(100) < section.getChordChance());
                added &= gc.getArrangement().isPartInclusion(2, i, notesSeedOffset,
                        gc::getInstParts);
                if (added && !chordPart.isMuted() && !overridden) {
                    section.setPresence(2, i);
                }
            }
        }

        if (gc.isArpsEnable() && !gc.getArpParts().isEmpty()) {
            Set<Integer> presences = section.getPresence(3);
            for (int i = 0; i < gc.getArpParts().size(); i++) {
                ArpPart arpPart = gc.getArpParts().get(i);
                random.setSeed(arrangementSeed + 200 + arpPart.getOrderOffset());
                variationGenerator.setSeed(arrangementSeed + 200 + arpPart.getOrderOffset());
                boolean added = (overridden && presences.contains(arpPart.getOrder()))
                        || (!overridden && random.nextInt(100) < section.getArpChance()
                        && i > 0 && !arpPart.isMuted());
                added |= !overridden && i == 0
                        && ((isPreview || measureCounter > (arrangement.getSections().size() - 1) / 2)
                        && !arpPart.isMuted());
                added &= gc.getArrangement().isPartInclusion(3, i, notesSeedOffset,
                        gc::getInstParts);
                if (added && !overridden) {
                    section.setPresence(3, i);
                }
            }
        }

        if (gc.isDrumsEnable() && !gc.getDrumParts().isEmpty()) {
            Set<Integer> presences = section.getPresence(4);
            for (int i = 0; i < gc.getDrumParts().size(); i++) {
                DrumPart drumPart = gc.getDrumParts().get(i);
                random.setSeed(arrangementSeed + 300 + drumPart.getOrderOffset());
                int drumChanceMultiplier = section.getTypeMelodyOffset() == 0
                        && DrumGUI.PUNCHY_DRUMS.contains(drumPart.getInstrument()) ? 2 : 1;
                boolean added = (overridden && presences.contains(drumPart.getOrder()))
                        || (!overridden && random.nextInt(100)
                        < section.getDrumChance() * drumChanceMultiplier);
                added &= gc.getArrangement().isPartInclusion(4, i, notesSeedOffset,
                        gc::getInstParts);
                if (added && !drumPart.isMuted() && !overridden) {
                    section.setPresence(4, i);
                }
            }
        }
    }
}
