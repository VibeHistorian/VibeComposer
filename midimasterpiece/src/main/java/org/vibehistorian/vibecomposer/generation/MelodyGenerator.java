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
        this.blockSkeletonGenerator = new MelodyBlockSkeletonGenerator(gc, mg.getTiming(),
                mg.getGlobalDurationMultiplier(), state, inference,
				 expansion, settings);
        this.legacySkeletonGenerator = new LegacyMelodySkeletonGenerator(gc, mg.getTiming(),
                mg.getGlobalDurationMultiplier(), state, inference);
    }

    public Map<Integer, List<Note>> makeFullMelodyMap(MelodyPart ip, List<int[]> actualProgression,
                                     List<int[]> generatedRootProgression, int notesSeedOffset, Section sec,
                                     List<Integer> variations, List<Integer> melodyBlockJumpPreference) {
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
