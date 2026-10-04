package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Note;
import jm.music.data.Phrase;
import jm.music.tools.Mod;
import org.vibehistorian.vibecomposer.Constants;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.Helpers.PhraseExt;
import org.vibehistorian.vibecomposer.Helpers.PhraseNote;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Section;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Builds a section melody phrase from generated melody data and applies its phrase-level rules. */
final class MelodyPhraseBuilder {
    static final class PatternResult {
        final List<Integer> pattern;
        final Map<Integer, List<Integer>> patternMap;

        private PatternResult(List<Integer> pattern, Map<Integer, List<Integer>> patternMap) {
            this.pattern = pattern;
            this.patternMap = patternMap;
        }
    }

    private final GUIConfig config;
    private final MelodyGenerator melodyGenerator;
    private final Predicate<CustomMidiRequest> customMidiOverwriter;
    private final Consumer<SectionNotes> sectionNotePublisher;
    private final BiConsumer<Phrase, Integer> phraseSwinger;
    private final Consumer<PhrasePart> phraseOffsetter;
    private final Consumer<PatternResult> patternPublisher;

    MelodyPhraseBuilder(GUIConfig config, MelodyGenerator melodyGenerator,
                        Predicate<CustomMidiRequest> customMidiOverwriter,
                        Consumer<SectionNotes> sectionNotePublisher,
                        BiConsumer<Phrase, Integer> phraseSwinger,
                        Consumer<PhrasePart> phraseOffsetter,
                        Consumer<PatternResult> patternPublisher) {
        this.config = config;
        this.melodyGenerator = melodyGenerator;
        this.customMidiOverwriter = customMidiOverwriter;
        this.sectionNotePublisher = sectionNotePublisher;
        this.phraseSwinger = phraseSwinger;
        this.phraseOffsetter = phraseOffsetter;
        this.patternPublisher = patternPublisher;
    }

    Phrase build(MelodyPart part, List<int[]> actualProgression,
                 List<int[]> generatedRootProgression, List<Double> progressionDurations,
                 int notesSeedOffset, Section section, List<Integer> variations,
                 boolean melodyEmptyPass, List<Integer> melodyBlockJumpPreference,
                 int sectionOrder, double startTimeDelay, int transpose,
                 ScaleMode modifiedScale) {
        LG.d("Processing: " + part.partInfo());
        Phrase phrase = new PhraseExt(0, part.getOrder(), sectionOrder);
        int measures = section.getMeasures();

        Map<Integer, List<Note>> fullMelodyMap = melodyGenerator.makeFullMelodyMap(part,
                actualProgression, generatedRootProgression, notesSeedOffset, section,
                variations, melodyBlockJumpPreference);

        if (melodyEmptyPass || !customMidiOverwriter.test(
                new CustomMidiRequest(section, phrase, part, progressionDurations))) {
            Vector<Note> noteList = new Vector<>();
            fullMelodyMap.values().forEach(noteList::addAll);

            phrase.addNoteList(noteList, true);
            Phrase savedPhrase = phrase.copy();
            Mod.transpose(savedPhrase, part.getTranspose() * -1);
            if (config.isTransposedNotesForceScale()) {
                MidiUtils.transposePhrase(savedPhrase, ScaleMode.IONIAN.noteAdjustScale,
                        ScaleMode.IONIAN.noteAdjustScale);
            }
            if (!melodyEmptyPass) {
                sectionNotePublisher.accept(new SectionNotes(section, part,
                        savedPhrase.getNoteList()));
            }
        } else {
            Mod.transpose(phrase, part.getTranspose());
            if (config.isCustomMidiForceScale()) {
                MidiUtils.transposePhrase(phrase, ScaleMode.IONIAN.noteAdjustScale,
                        ScaleMode.IONIAN.noteAdjustScale);
            }
            if (part.getOrder() == 1) {
                int numChords = progressionDurations.size();
                fullMelodyMap = new HashMap<>();
                for (int i = 0; i < numChords * measures; i++) {
                    fullMelodyMap.put(i, new ArrayList<>());
                }
                int chordCounter = 0;
                int measureCounter = 0;
                double cumulativeChordDur = progressionDurations.get(0);
                PhraseNotes phraseNotes = new PhraseNotes(phrase);
                phraseNotes.remakeNoteStartTimes();
                List<PhraseNote> orderedNotes = new ArrayList<>(phraseNotes);

                if (orderedNotes.size() >= 2) {
                    orderedNotes.sort(Comparator.comparing(PhraseNote::getStartTime));
                    double endTime = phraseNotes.get(phraseNotes.size() - 1).getAbsoluteStartTime()
                            + phraseNotes.get(phraseNotes.size() - 1).getRv();
                    for (int i = 0; i < orderedNotes.size() - 1; i++) {
                        PhraseNote note = orderedNotes.get(i);
                        note.setRv(orderedNotes.get(i + 1).getStartTime() - note.getStartTime());
                        note.setOffset(0);
                    }
                    orderedNotes.get(orderedNotes.size() - 1)
                            .setRv(endTime - orderedNotes.get(orderedNotes.size() - 2).getStartTime());
                }

                for (PhraseNote note : orderedNotes) {
                    if (note.getStartTime() > cumulativeChordDur - Constants.DBL_ERR) {
                        chordCounter = (chordCounter + 1) % numChords;
                        if (chordCounter == 0) {
                            measureCounter++;
                        }
                        cumulativeChordDur += progressionDurations.get(chordCounter % numChords);
                    }
                    fullMelodyMap.get(chordCounter + numChords * measureCounter).add(note.toNote());
                }
            }
        }

        PatternResult patternResult = null;
        if (part.getOrder() == 1) {
            List<Integer> notePattern = new ArrayList<>();
            Map<Integer, List<Integer>> notePatternMap = MelodyUtils.patternsFromNotes(fullMelodyMap,
                    progressionDurations, MidiGenerator.getBeatDurationMult(config, section),
                    config.isMelodyPatternFlip(), melodyGenerator.getTiming());
            notePatternMap.keySet().forEach(key -> notePattern.addAll(notePatternMap.get(key)));
            patternResult = new PatternResult(notePattern, notePatternMap);
            patternPublisher.accept(patternResult);
        }

        phraseSwinger.accept(phrase, part.getSwingPercent());
        MidiGeneratorUtils.applyNoteLengthMultiplier(phrase.getNoteList(),
                part.getNoteLengthMultiplier());
        MidiGeneratorUtils.processSectionTransition(section, phrase.getNoteList(),
                progressionDurations.stream().mapToDouble(value -> value).sum() * measures,
                0.25, 0.25, 0.9);

        List<Integer> melodyVars = section.getVariation(0,
                part.getAbsoluteOrder(config.getInstParts(part.getPartNum())));
        int extraTranspose = melodyVars != null && melodyVars.contains(0) ? 12 : 0;

        ScaleMode scale = modifiedScale != null ? modifiedScale : config.getScaleMode();
        if (scale != ScaleMode.IONIAN) {
            MidiUtils.transposePhrase(phrase, ScaleMode.IONIAN.noteAdjustScale,
                    scale.noteAdjustScale, config.isTransposedNotesForceScale());
        }
        if (transpose + extraTranspose != 0) {
            Mod.transpose(phrase, transpose + extraTranspose);
        }
        phrase.setStartTime(startTimeDelay);
        phraseOffsetter.accept(new PhrasePart(phrase, part));
        return phrase;
    }

    static final class CustomMidiRequest {
        final Section section;
        final Phrase phrase;
        final MelodyPart part;
        final List<Double> progressionDurations;

        private CustomMidiRequest(Section section, Phrase phrase, MelodyPart part,
                                  List<Double> progressionDurations) {
            this.section = section;
            this.phrase = phrase;
            this.part = part;
            this.progressionDurations = progressionDurations;
        }
    }

    static final class SectionNotes {
        final Section section;
        final MelodyPart part;
        final List<Note> notes;

        private SectionNotes(Section section, MelodyPart part, List<Note> notes) {
            this.section = section;
            this.part = part;
            this.notes = notes;
        }
    }

    static final class PhrasePart {
        final Phrase phrase;
        final MelodyPart part;

        private PhrasePart(Phrase phrase, MelodyPart part) {
            this.phrase = phrase;
            this.part = part;
        }
    }
}
