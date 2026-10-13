package org.vibehistorian.vibecomposer.generation;

import jm.music.data.Note;
import jm.music.data.Phrase;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Section;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class MelodyGenerator {

    // shared constants
    public static final int EMBELLISHMENT_CHANCE = 20;
    public static final int maxAllowedScaleNotes = 7;

    // pass in from outside
    private final GUIConfig gc;
    private final MidiGenerator mg;

    // freely use via object
    Map<Integer, List<Note>> chordMelodyMap1 = new HashMap<>();
    List<int[]> melodyBasedChordProgression = new ArrayList<>();
    List<int[]> melodyBasedRootProgression = new ArrayList<>();
    String alternateChords = null;

    private final MelodyGenerationState state;
    private final MelodyChordInference inference;
    private final MelodyBlockSkeletonGenerator blockSkeletonGenerator;
    private final LegacyMelodySkeletonGenerator legacySkeletonGenerator;
    private final MelodyExpansion expansion;
    private final MelodyGenerationSettings settings;
    private final ContextualMelodyGenerator contextualGenerator;

    public MelodyGenerator(GUIConfig gc, MidiGenerator mg) {
		this(gc, mg, MelodyGenerationSettings.fromConfig(gc));
	}

	public MelodyGenerator(GUIConfig gc, MidiGenerator mg, MelodyGenerationSettings settings) {
        this.gc = gc;
        this.mg = mg;
        this.settings = settings;
        this.state = new MelodyGenerationState(chordMelodyMap1, melodyBasedChordProgression, melodyBasedRootProgression);
        this.inference = new MelodyChordInference(gc, mg.getTiming(), state,
                new MelodyChordInference.RunStateUpdater() {
                    @Override
                    public void progressionChanged(List<int[]> chordProgression,
                            List<int[]> rootProgression) {
                        mg.chordProgression = chordProgression;
                        mg.rootProgression = rootProgression;
                    }

                    @Override
                    public void userMelodyProgressionChanged(List<Double> progressionDurations,
                            List<String> chordNames) {
                        mg.progressionDurations = progressionDurations;
                        mg.replaceGeneratedChordNames(chordNames);
                    }
                });
        this.expansion = new MelodyExpansion(gc, mg.getTiming(), mg.getGlobalDurationMultiplier());
        this.contextualGenerator = new ContextualMelodyGenerator(gc,mg.getTiming(),mg.getGlobalDurationMultiplier(),settings.contextualCache);
        this.blockSkeletonGenerator = new MelodyBlockSkeletonGenerator(gc, mg.getTiming(),
                mg.getGlobalDurationMultiplier(), state, inference,
				 expansion, settings);
        this.legacySkeletonGenerator = new LegacyMelodySkeletonGenerator(gc, mg.getTiming(),
                mg.getGlobalDurationMultiplier(), state, inference);
    }

    public Map<Integer, List<Note>> makeFullMelodyMap(MelodyPart ip, List<int[]> actualProgression,
                                     List<int[]> generatedRootProgression, int notesSeedOffset, Section sec,
                                     List<Integer> variations, List<Integer> melodyBlockJumpPreference) {
        return makeFullMelodyMap(ip, actualProgression, generatedRootProgression, mg.progressionDurations,
                notesSeedOffset, sec, variations, melodyBlockJumpPreference);
    }

    boolean usesContextualMelody() {
        return gc.isContextualMelody() && !gc.isMelodyLegacyMode() && settings.getUserMelody() == null;
    }
    boolean usesFixedContextualMelody() { return gc.isContextualMelody() && !gc.isMelodyLegacyMode() && settings.getUserMelody() != null; }

    public ContextualMelodyPlanner.Result getContextualResult() { return contextualGenerator.lastResult; }
    public HarmonyTimeline getContextualHarmony() { return contextualGenerator.lastHarmony; }

    public Map<Integer, List<Note>> makeFullMelodyMap(MelodyPart ip, List<int[]> actualProgression,
                                     List<int[]> generatedRootProgression, List<Double> activeDurations,
                                     int notesSeedOffset, Section sec, List<Integer> variations,
                                     List<Integer> melodyBlockJumpPreference) {
        if (usesFixedContextualMelody()) {
            contextualGenerator.lastResult = null; contextualGenerator.lastHarmony = null;
            List<int[]> chords = new ArrayList<>(), roots = new ArrayList<>(); List<Double> beats = new ArrayList<>();
            for (int repeat = 0; repeat < sec.getMeasures(); repeat++) {
                for (int i = 0; i < actualProgression.size(); i++) {
                    chords.add(actualProgression.get(i)); roots.add(generatedRootProgression.get(i)); beats.add(activeDurations.get(i) / mg.getTiming().quarterNote);
                }
            }
            return FixedMelodyAdapter.group(settings.getUserMelody(),HarmonyTimeline.of(chords,roots,beats),mg.getTiming().quarterNote,
                    mg.modScale == null ? gc.getScaleMode() : mg.modScale,ip.getTranspose());
        }
        if (usesContextualMelody()) {
            Map<Integer, List<Note>> result = contextualGenerator.generate(ip, actualProgression, generatedRootProgression,
                    activeDurations,sec,notesSeedOffset,mg.modScale == null ? gc.getScaleMode() : mg.modScale,mg.hasRealizedHarmony(),mg.getRenderingHarmony());
            // No inferred replacement progression is advertised until contextual harmonization exists.
            // Existing section-swap consumers still receive a valid fixed-harmony alternative.
            if (melodyBasedChordProgression.isEmpty()) {
                melodyBasedChordProgression = new ArrayList<>(actualProgression);
                melodyBasedRootProgression = new ArrayList<>(generatedRootProgression);
                alternateChords = String.join(",", mg.getGeneratedChordNames());
            }
            if (variations == null) { sec.setVariation(0, ip.getAbsoluteOrder(gc.getMelodyParts()), new ArrayList<>()); }
            return result;
        }
        LG.d("Processing: " + ip.partInfo());
        int measures = sec.getMeasures();

        Vector<Note> skeletonNotes;
        Phrase userMelody = settings.getUserMelody();
        if (userMelody != null) {
            skeletonNotes = (Vector<Note>) userMelody.copy().getNoteList();
        } else {
            if (gc.isMelodyLegacyMode()) {
                LG.i("OLD MELODY ALGO");
                skeletonNotes = legacySkeletonGenerator.generate(ip, actualProgression,
                        generatedRootProgression, measures, notesSeedOffset, sec, variations,
                        mg.progressionDurations, mg.getGeneratedChordNames(),
                        mg.rootProgression, mg.chordProgression);
            } else {
                skeletonNotes = blockSkeletonGenerator.generateMelodyBlockSkeletonFromChords(
                        new MelodyBlockSkeletonGenerator.SkeletonGenerationRequest(ip,
                                actualProgression, generatedRootProgression, measures,
                                notesSeedOffset, sec, variations, melodyBlockJumpPreference,
                                mg.progressionDurations, mg.getGeneratedChordNames(),
                                mg.rootProgression, mg.chordProgression, mg.modScale));
            }
        }
        alternateChords = state.alternateChords;
        melodyBasedChordProgression = state.melodyBasedChordProgression;
        melodyBasedRootProgression = state.melodyBasedRootProgression;
        Map<Integer, List<Note>> fullMelodyMap = expansion.convertMelodySkeletonToFullMelody(ip,
                mg.progressionDurations, sec, skeletonNotes, notesSeedOffset, actualProgression,
                measures, mg.modScale);

        for (int i = 0; i < generatedRootProgression.size() * measures; i++) {
            for (int j = 0; j < MidiUtils.MINOR_CHORDS.size(); j++) {
                int[] minorChord = MidiUtils.mappedChord(MidiUtils.MINOR_CHORDS.get(j));
                boolean isMinor = Arrays.equals(MidiUtils.normalizeChord(minorChord),
                        MidiUtils.normalizeChord(
                                generatedRootProgression.get(i % generatedRootProgression.size())));
                if (isMinor) {
                    MidiUtils.transposeNotes(fullMelodyMap.get(i), MidiUtils.ScaleMode.IONIAN.noteAdjustScale,
                            MidiUtils.adjustScaleByChord(MidiUtils.ScaleMode.IONIAN.noteAdjustScale,
                                    minorChord),
                            gc.isTransposedNotesForceScale());
                    LG.d("Transposing melody to match minor chord! Chord#: " + i);
                    break;
                }
            }
        }
        return fullMelodyMap;
    }

    void processUserMelody(Phrase userMelody) {
        inference.processUserMelody(userMelody, mg.currentSection, mg.getUserChords(),
                mg.progressionDurations, mg.rootProgression, mg.chordProgression);
        melodyBasedChordProgression = state.melodyBasedChordProgression;
        melodyBasedRootProgression = state.melodyBasedRootProgression;
    }

	void setUserMelody(Phrase userMelody) {
		settings.setUserMelody(userMelody);
	}

	Phrase getUserMelody() {
		return settings.getUserMelody();
	}
}
