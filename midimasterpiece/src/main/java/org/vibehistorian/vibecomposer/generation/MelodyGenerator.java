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
    public Map<Integer, List<Note>> chordMelodyMap1 = new HashMap<>();
    public List<int[]> melodyBasedChordProgression = new ArrayList<>();
    public List<int[]> melodyBasedRootProgression = new ArrayList<>();
    public String alternateChords = null;

    public static double[] MELODY_SKELETON_DURATIONS_SHORT = { MidiGenerator.Durations.SIXTEENTH_NOTE / 2.0,
            MidiGenerator.Durations.SIXTEENTH_NOTE, MidiGenerator.Durations.EIGHTH_NOTE, MidiGenerator.Durations.DOTTED_EIGHTH_NOTE,
            MidiGenerator.Durations.QUARTER_NOTE, MidiGenerator.Durations.DOTTED_QUARTER_NOTE, MidiGenerator.Durations.HALF_NOTE };

    public static double[] MELODY_SKELETON_DURATIONS = { MidiGenerator.Durations.SIXTEENTH_NOTE,
            MidiGenerator.Durations.EIGHTH_NOTE, MidiGenerator.Durations.DOTTED_EIGHTH_NOTE, MidiGenerator.Durations.QUARTER_NOTE,
            MidiGenerator.Durations.DOTTED_QUARTER_NOTE, MidiGenerator.Durations.HALF_NOTE };


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
        this.inference = new MelodyChordInference(gc, mg, state);
        this.expansion = new MelodyExpansion(gc, mg);
        this.blockSkeletonGenerator = new MelodyBlockSkeletonGenerator(gc, mg, state, inference,
				 expansion, settings);
        this.legacySkeletonGenerator = new LegacyMelodySkeletonGenerator(gc, mg, state, inference);
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
                        generatedRootProgression, measures, notesSeedOffset, sec, variations);
            } else {
                skeletonNotes = generateMelodyBlockSkeletonFromChords(ip, actualProgression,
                        generatedRootProgression, measures, notesSeedOffset, sec, variations, melodyBlockJumpPreference);
            }
        }
        alternateChords = state.alternateChords;
        melodyBasedChordProgression = state.melodyBasedChordProgression;
        melodyBasedRootProgression = state.melodyBasedRootProgression;
        Map<Integer, List<Note>> fullMelodyMap = convertMelodySkeletonToFullMelody(ip,
                mg.progressionDurations, sec, skeletonNotes, notesSeedOffset, actualProgression,
                measures);

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
    protected Vector<Note> generateMelodyBlockSkeletonFromChords(MelodyPart mp, List<int[]> chords,
            List<int[]> roots, int measures, int notesSeedOffset, Section sec,
            List<Integer> variations, List<Integer> melodyBlockJumpPreference) {
        Vector<Note> result = blockSkeletonGenerator.generateMelodyBlockSkeletonFromChords(mp, chords,
                roots, measures, notesSeedOffset, sec, variations, melodyBlockJumpPreference);
        alternateChords = state.alternateChords;
        return result;
    }

    protected Map<Integer, List<Note>> convertMelodySkeletonToFullMelody(MelodyPart mp,
            List<Double> durations, Section sec, Vector<Note> skeleton, int notesSeedOffset,
            List<int[]> chords, int measures) {
        return expansion.convertMelodySkeletonToFullMelody(mp, durations, sec, skeleton,
                notesSeedOffset, chords, measures);
    }

    void processUserMelody(Phrase userMelody) {
        inference.processUserMelody(userMelody);
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
