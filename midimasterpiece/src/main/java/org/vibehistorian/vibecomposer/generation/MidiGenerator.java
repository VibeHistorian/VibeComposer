/* --------------------
* @author Vibe Historian
* ---------------------

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or any
later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not,
see <https://www.gnu.org/licenses/>.
*/

package org.vibehistorian.vibecomposer.generation;

import jm.JMC;
import jm.constants.Pitches;
import jm.music.data.Note;
import jm.music.data.Phrase;
import jm.music.data.Score;
import jm.music.tools.Mod;
import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.*;
import org.vibehistorian.vibecomposer.Enums.KeyChangeType;
import org.vibehistorian.vibecomposer.Helpers.PartExt;
import org.vibehistorian.vibecomposer.Helpers.PhraseExt;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Helpers.UsedPattern;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.BassPart;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.controllers.ConsoleOutputController;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static org.vibehistorian.vibecomposer.Constants.instNames;
import static org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import static org.vibehistorian.vibecomposer.MidiUtils.mappedChord;

public class MidiGenerator implements JMC {
	public static final class OutputOptions {
		private final boolean padGeneratedTracks;
		private final List<Integer> trackPadding;

		public OutputOptions(boolean padGeneratedTracks, List<Integer> trackPadding) {
			this.padGeneratedTracks = padGeneratedTracks;
			this.trackPadding = trackPadding == null ? Collections.emptyList()
					: new ArrayList<>(trackPadding);
		}

		public static OutputOptions defaults() {
			return new OutputOptions(true, Arrays.asList(3, 2, 5, 5, 6));
		}

		private List<Integer> getTrackPadding() {
			return padGeneratedTracks ? new ArrayList<>(trackPadding) : new ArrayList<>();
		}
	}

	@FunctionalInterface
	public interface SequenceTrackAssigner {
		void assign(int instrument, int panelOrder, int trackNumber);
	}

	private static final SequenceTrackAssigner NO_SEQUENCE_TRACK_ASSIGNER = (instrument, panelOrder,
			trackNumber) -> { };

	public static final double FILLER_NOTE_MIN_DURATION = 0.05;
	public static double GLOBAL_DURATION_MULTIPLIER = 0.95;
	public static double SPLIT_DURATION_MULTIPLIER = 0.97;
	public static final int[] DEFAULT_INSTRUMENT_TRANSPOSE = { 0, -24, -12, -24, 0 };

	public static double noteMultiplier = 1.0;

	public static class Durations {

		public static double SIXTEENTH_NOTE = 0.25 * noteMultiplier;
		public static double DOTTED_SIXTEENTH_NOTE = 0.375 * noteMultiplier;
		public static double EIGHTH_NOTE = 0.5 * noteMultiplier;
		public static double DOTTED_EIGHTH_NOTE = 0.75 * noteMultiplier;
		public static double QUARTER_NOTE = noteMultiplier;
		public static double DOTTED_QUARTER_NOTE = 1.5 * noteMultiplier;
		public static double HALF_NOTE = 2.0 * noteMultiplier;
		public static double DOTTED_HALF_NOTE = 3.0 * noteMultiplier;
		public static double WHOLE_NOTE = 4.0 * noteMultiplier;
		public static double DOTTED_WHOLE_NOTE = 6.0 * noteMultiplier;
	}

	public static void recalculateDurations(int multiplier) {
		noteMultiplier = multiplier / 100.0;
		Durations.SIXTEENTH_NOTE = 0.25 * noteMultiplier;
		Durations.DOTTED_SIXTEENTH_NOTE = 0.375 * noteMultiplier;
		Durations.EIGHTH_NOTE = 0.5 * noteMultiplier;
		Durations.DOTTED_EIGHTH_NOTE = 0.75 * noteMultiplier;
		Durations.QUARTER_NOTE = noteMultiplier;
		Durations.DOTTED_QUARTER_NOTE = 1.5 * noteMultiplier;
		Durations.HALF_NOTE = 2.0 * noteMultiplier;
		Durations.DOTTED_HALF_NOTE = 3.0 * noteMultiplier;
		Durations.WHOLE_NOTE = 4.0 * noteMultiplier;
		Durations.DOTTED_WHOLE_NOTE = 6.0 * noteMultiplier;

		START_TIME_DELAY = Durations.QUARTER_NOTE;
		MELODY_DUR_ARRAY = new double[] { Durations.HALF_NOTE, Durations.DOTTED_QUARTER_NOTE,
				Durations.QUARTER_NOTE, Durations.EIGHTH_NOTE };
		MelodyGenerator.MELODY_SKELETON_DURATIONS = new double[] { Durations.SIXTEENTH_NOTE, Durations.EIGHTH_NOTE,
				Durations.DOTTED_EIGHTH_NOTE, Durations.QUARTER_NOTE, Durations.DOTTED_QUARTER_NOTE,
				Durations.HALF_NOTE };
		MelodyGenerator.MELODY_SKELETON_DURATIONS_SHORT = new double[] { Durations.SIXTEENTH_NOTE / 2.0,
				Durations.SIXTEENTH_NOTE, Durations.EIGHTH_NOTE, Durations.DOTTED_EIGHTH_NOTE,
				Durations.QUARTER_NOTE, Durations.DOTTED_QUARTER_NOTE, Durations.HALF_NOTE };
	}

	private static final boolean debugEnabled = true;
	// big G
	public static GUIConfig gc;

	// constants
	public static final int MELODY_PATTERN_RESOLUTION = 16;

	public static final int MAXIMUM_PATTERN_LENGTH = 8;
	public static final int OPENHAT_CHANCE = 0;
	static final int BASE_ACCENT = 15;
	public static double START_TIME_DELAY = Durations.QUARTER_NOTE;
	private static final String ARP_PATTERN_KEY = "ARP_PATTERN";
	private static final String ARP_OCTAVE_KEY = "ARP_OCTAVE";
	private static final String ARP_PAUSES_KEY = "ARP_PAUSES";

	// visibles/settables

	public static List<String> userChords = new ArrayList<>();
	public static List<Double> userChordsDurations = new ArrayList<>();
	public static List<String> chordInts = new ArrayList<>();
	public static double GENERATED_MEASURE_LENGTH = 0;

	public static String FIRST_CHORD = null;
	public static String LAST_CHORD = null;

	public static boolean COLLAPSE_DRUM_TRACKS = true;


	// for internal use only
	public static double[] MELODY_DUR_ARRAY = { Durations.HALF_NOTE, Durations.DOTTED_QUARTER_NOTE,
			Durations.QUARTER_NOTE, Durations.EIGHTH_NOTE };
	public static final double[] MELODY_DUR_CHANCE = { 0.3, 0.6, 1.0, 1.0 };

	private static Map<Integer, Integer> customDrumMappingNumbers = null;

	public List<Double> progressionDurations = new ArrayList<>();
	public List<int[]> chordProgression = new ArrayList<>();
	public List<int[]> rootProgression = new ArrayList<>();

	public List<Double> progressionDurationsBackup = new ArrayList<>();
	public List<int[]> chordProgressionBackup = new ArrayList<>();
	public List<int[]> rootProgressionBackup = new ArrayList<>();

	Section currentSection = null;

	// global parts
	private List<BassPart> bassParts = null;
	private List<MelodyPart> melodyParts = null;
	private List<ChordPart> chordParts = null;
	private List<DrumPart> drumParts = null;
	private List<ArpPart> arpParts = null;

	private List<Integer> melodyNotePattern = null;
	private Map<Integer, List<Integer>> melodyNotePatternMap = null;
	int secOrder = -1;

	private int modTrans = 0;
	ScaleMode modScale = null;

	private final MelodyGenerator mgen;
	private final MelodyPhraseBuilder melodyPhraseBuilder;
	private final ArpPhraseGenerator arpPhraseGenerator;
	private final BassPhraseGenerator bassPhraseGenerator;
	private final ChordPhraseGenerator chordPhraseGenerator;
	private final DrumPhraseGenerator drumPhraseGenerator;
	private final SequenceTrackAssigner sequenceTrackAssigner;
	private final ConsoleOutputController consoleOutputController;
	private final OutputOptions outputOptions;

	public MidiGenerator(GUIConfig gc) {
		this(gc, NO_SEQUENCE_TRACK_ASSIGNER, ConsoleOutputController.noOp());
	}

	/** Applies config-backed generator inputs using fresh-window defaults. */
	public static boolean configureFromConfig(GUIConfig config) {
		return configureFromConfig(config, 100, 0.95, true);
	}

	public static boolean configureFromConfig(GUIConfig config, int stretchPercent,
			double globalDurationMultiplier, boolean collapseDrumTracks) {
		Objects.requireNonNull(config, "config");
		recalculateDurations(stretchPercent);
		GLOBAL_DURATION_MULTIPLIER = globalDurationMultiplier;
		COLLAPSE_DRUM_TRACKS = collapseDrumTracks;
		START_TIME_DELAY = Durations.QUARTER_NOTE;
		FIRST_CHORD = MidiUtils.MAJOR_CHORDS.contains(config.getFirstChord())
				? config.getFirstChord() : null;
		LAST_CHORD = MidiUtils.MAJOR_CHORDS.contains(config.getLastChord())
				? config.getLastChord() : null;

		List<String> configuredChords = MidiUtils.parseChordList(config.getCustomChords());
		boolean customChords = config.isCustomChordsEnabled() && !configuredChords.isEmpty();
		userChords.clear();
		if (customChords) {
			userChords.addAll(configuredChords);
		}
		userChordsDurations.clear();
		boolean validDurations = true;
		if (customChords || config.isCustomDurationsEnabled()) {
			String[] durationValues = config.getCustomChordDurations().split(",");
			boolean coversAllCustomChords = durationValues.length >= configuredChords.size();
			int durationCount = customChords && coversAllCustomChords
					? configuredChords.size() : durationValues.length;
			try {
				for (int i = 0; i < durationCount; i++) {
					userChordsDurations.add(config.isCustomDurationsEnabled() && coversAllCustomChords
							? stretchPercent * Double.parseDouble(durationValues[i]) / 100.0
							: Durations.WHOLE_NOTE);
				}
			} catch (NumberFormatException e) {
				validDurations = false;
			}
		}

		return validDurations;
	}

	public MidiGenerator(GUIConfig gc, SequenceTrackAssigner sequenceTrackAssigner) {
		this(gc, sequenceTrackAssigner, ConsoleOutputController.noOp());
	}

	public MidiGenerator(GUIConfig gc, SequenceTrackAssigner sequenceTrackAssigner,
			ConsoleOutputController consoleOutputController) {
		this(gc, sequenceTrackAssigner, consoleOutputController, OutputOptions.defaults());
	}

	public MidiGenerator(GUIConfig gc, SequenceTrackAssigner sequenceTrackAssigner,
			ConsoleOutputController consoleOutputController, OutputOptions outputOptions) {
		this(gc, sequenceTrackAssigner, consoleOutputController, outputOptions,
				MelodyGenerationSettings.fromConfig(gc));
	}

	public MidiGenerator(GUIConfig gc, SequenceTrackAssigner sequenceTrackAssigner,
			ConsoleOutputController consoleOutputController, OutputOptions outputOptions,
			MelodyGenerationSettings melodyGenerationSettings) {
		MidiGenerator.gc = gc;
		this.sequenceTrackAssigner = Objects.requireNonNull(sequenceTrackAssigner);
		this.consoleOutputController = Objects.requireNonNull(consoleOutputController);
		this.outputOptions = Objects.requireNonNull(outputOptions);
		mgen = new MelodyGenerator(gc, this, melodyGenerationSettings);
		melodyPhraseBuilder = new MelodyPhraseBuilder(gc, mgen,
				request -> overwriteWithCustomSectionMidi(request.section, request.phrase,
						request.part),
				request -> addPhraseNotesToSection(request.section, request.part, request.notes),
				(phrase, swingPercent) -> swingPhrase(phrase, swingPercent, Durations.QUARTER_NOTE),
				request -> addOffsetsToPhrase(request.phrase, request.part),
				result -> {
					melodyNotePatternMap = result.patternMap;
					melodyNotePattern = result.pattern;
				});
		arpPhraseGenerator = new ArpPhraseGenerator(gc, MidiGenerator::fillVariations);
		bassPhraseGenerator = new BassPhraseGenerator(gc,
                MidiGenerator::fillVariations);
		chordPhraseGenerator = new ChordPhraseGenerator(gc,
                MidiGenerator::fillVariations);
		drumPhraseGenerator = new DrumPhraseGenerator(gc,
				MidiGenerator::fillVariations);
	}

	private InstPhraseGenerator.Timing getInstrumentPhraseTiming() {
		return new InstPhraseGenerator.Timing(Durations.SIXTEENTH_NOTE, Durations.EIGHTH_NOTE,
				Durations.QUARTER_NOTE, Durations.DOTTED_QUARTER_NOTE, Durations.HALF_NOTE,
				Durations.DOTTED_HALF_NOTE, Durations.WHOLE_NOTE, noteMultiplier,
				GLOBAL_DURATION_MULTIPLIER, FILLER_NOTE_MIN_DURATION, Constants.DBL_ERR);
	}

	private int getAbsoluteOrder(InstPart part) {
		return part.getAbsoluteOrder(gc.getInstParts(part.getPartNum()));
	}

	public static double getBeatDurationMult(Section currSection) {
		double mult = 1;
		SectionConfig sc = (currSection != null) ? currSection.getSecConfig() : null;
		int beatDurMultiIndex = (sc != null && sc.getBeatDurationMultiplierIndex() != null)
				? sc.getBeatDurationMultiplierIndex()
				: gc.getBeatDurationMultiplierIndex();
		if (beatDurMultiIndex == 0) {
			mult = 0.5;
		} else if (beatDurMultiIndex == 2) {
			mult = 2;
		}
		return mult;
	}

	@SuppressWarnings("unchecked")
	protected void swingPhrase(Phrase phr, int swingPercent, double swingUnitOfTime) {
		if (gc.getGlobalSwingOverride() != null) {
			swingPercent = gc.getGlobalSwingOverride();
		}
		if (swingPercent == 50) {
			return;
		}

		swingUnitOfTime *= (gc.getSwingUnitMultiplierIndex() == 0) ? 0.5
				: (double) gc.getSwingUnitMultiplierIndex();

		Vector<Note> notes = phr.getNoteList();
		double currentChordDur = progressionDurations.get(0);
		int chordCounter = 0;

		boolean logSwing = false;

		int swingPercentAmount = swingPercent;
		double swingAdjust = swingUnitOfTime * (swingPercentAmount / ((double) 50.0))
				- swingUnitOfTime;
		double durCounter = 0.0;

		if (logSwing)
			LG.d("-----------------------------STARTING SWING -----------------------------------");

		List<Integer> chordSeparators = new ArrayList<>();
		for (int i = 0; i < notes.size(); i++) {
			durCounter += notes.get(i).getRhythmValue();
			if (durCounter + Constants.DBL_ERR > currentChordDur) {
				chordSeparators.add(i);
				chordCounter = (chordCounter + 1) % progressionDurations.size();
				currentChordDur = progressionDurations.get(chordCounter);
				durCounter = 0.0;
			}
			if (logSwing)
				LG.d("Dur: " + durCounter + ", chord counter: " + chordCounter);
		}
		// fix short notes at the end not going to next chord
		if (durCounter > Constants.DBL_ERR) {
			chordSeparators.add(notes.size() - 1);
		}
		int chordSepIndex = 0;
		Note swungNote = null;
		Note latestSuitableNote = null;
		durCounter = 0.0;
		for (int i = 0; i < notes.size(); i++) {
			Note n = notes.get(i);
			double adjDur = n.getRhythmValue();
			if (adjDur < Constants.DBL_ERR) {
				continue;
			}
			if (i > chordSeparators.get(chordSepIndex)) {
				chordSepIndex++;
				swingAdjust = swingUnitOfTime * (swingPercentAmount / ((double) 50.0))
						- swingUnitOfTime;
				durCounter = 0.0;

				if (swungNote != null) {
					swingAdjust *= -1;
					double swungDur = swungNote.getRhythmValue();
					swungNote.setRhythmValue(swungDur + swingAdjust);
					swungNote.setDuration((swungDur + swingAdjust) * GLOBAL_DURATION_MULTIPLIER);
					swingAdjust *= -1;
					swungNote = null;
					latestSuitableNote = null;
					if (logSwing)
						LG.d("Unswung swung note!");
				}
			}
			durCounter += adjDur;
			boolean processed = false;

			// try to find latest note which can be added/subtracted with swingAdjust
			if (swungNote == null) {
				if (adjDur - Math.abs(swingAdjust) > Constants.DBL_ERR) {
					latestSuitableNote = n;
				}
				processed = true;
			} else {
				if ((adjDur - Math.abs(swingAdjust) > Constants.DBL_ERR) && latestSuitableNote == null) {
					latestSuitableNote = n;
					processed = true;
				}
			}

			// apply swing to best note from previous section when landing on "exact" hits
			if (MidiUtils.isMultiple(durCounter, swingUnitOfTime)) {

				if (logSwing)
					LG.d(durCounter + " is Multiple of Unit");
				// nothing was caught in first half, SKIP swinging for this 2-unit bit of time
				if (swungNote == null && MidiUtils.isMultiple(durCounter, 2 * swingUnitOfTime)) {
					latestSuitableNote = null;
					if (logSwing)
						LG.d("Can't swing this!");
				} else {
					if (latestSuitableNote != null) {
						double suitableDur = latestSuitableNote.getRhythmValue();
						if (swungNote == null) {
							latestSuitableNote.setRhythmValue(suitableDur + swingAdjust);
							double newDuration = Math.max(Durations.SIXTEENTH_NOTE / 2, (suitableDur + swingAdjust));
							latestSuitableNote.setDuration(newDuration * GLOBAL_DURATION_MULTIPLIER);
							swingAdjust *= -1;
							swungNote = latestSuitableNote;
							latestSuitableNote = null;
							if (logSwing)
								LG.d("Processed 1st swing!");
						} else {
							latestSuitableNote.setRhythmValue(suitableDur + swingAdjust);
							double newDuration = Math.max(Durations.SIXTEENTH_NOTE / 2, (suitableDur + swingAdjust));
							latestSuitableNote.setDuration(newDuration * GLOBAL_DURATION_MULTIPLIER);
							swingAdjust *= -1;
							swungNote = null;
							latestSuitableNote = null;
							if (logSwing)
								LG.d("Processed 2nd swing!");
						}
					} else {
						if (swungNote != null) {
							double swungDur = swungNote.getRhythmValue();
							swungNote.setRhythmValue(swungDur + swingAdjust);
							double newDuration = Math.max(Durations.SIXTEENTH_NOTE / 2, (swungDur + swingAdjust));
							swungNote.setDuration(newDuration * GLOBAL_DURATION_MULTIPLIER);
							swingAdjust *= -1;
							swungNote = null;
							latestSuitableNote = null;
							if (logSwing)
								LG.d("Unswung swung note!");
						}
					}
				}

			}

			// 
			if (!processed && !MidiUtils.isMultiple(durCounter, 2 * swingUnitOfTime)) {
				if (swungNote != null) {
					if ((adjDur - Math.abs(swingAdjust) > Constants.DBL_ERR) && latestSuitableNote == null) {
						latestSuitableNote = n;
					}
				}
			}
		}

		if (swungNote != null) {
			double swungDur = swungNote.getRhythmValue();
			swungNote.setRhythmValue(swungDur + swingAdjust);
			double newDuration = Math.max(Durations.SIXTEENTH_NOTE / 2, (swungDur + swingAdjust));
			swungNote.setDuration(newDuration * GLOBAL_DURATION_MULTIPLIER);
			if (logSwing)
				LG.d("Unswung swung note!");
		}

		if (logSwing) {
			LG.d("AFTER:");
			currentChordDur = progressionDurations.get(0);
			durCounter = 0.0;
			chordCounter = 0;
            for (Note note : notes) {
                durCounter += note.getRhythmValue();
                if (durCounter - Constants.DBL_ERR > currentChordDur) {
                    chordCounter = (chordCounter + 1) % progressionDurations.size();
                    currentChordDur = progressionDurations.get(chordCounter);
                    durCounter = 0.0;
                }
                LG.d("Dur: " + durCounter + ", chord counter: " + chordCounter);
            }
		}
	}

	public void generatePrettyUserChords(int mainGeneratorSeed, int fixedLength,
			double maxDuration) {
		generateChordProgression(mainGeneratorSeed, fixedLength);
	}

	private List<int[]> generateChordProgression(int mainGeneratorSeed, int fixedLength) {
		chordInts.clear();
		ChordProgressionGenerator.MappedProgression generated =
				ChordProgressionGenerator.generateMappedProgression(gc, mainGeneratorSeed,
						fixedLength, FIRST_CHORD, LAST_CHORD, progressionDurations,
						Durations.WHOLE_NOTE, Durations.QUARTER_NOTE, Constants.DBL_ERR);
		progressionDurations.clear();
		progressionDurations.addAll(generated.durations);
		chordInts.clear();
		chordInts.addAll(generated.chordNames);
		return generated.chords;
	}

	private static final class ProgressionPreparation {
		final List<int[]> actualProgression;
		final List<int[]> generatedRootProgression;
		final List<Double> durations;

		private ProgressionPreparation(List<int[]> actualProgression,
				List<int[]> generatedRootProgression, List<Double> durations) {
			this.actualProgression = actualProgression;
			this.generatedRootProgression = generatedRootProgression;
			this.durations = durations;
		}
	}

	private ProgressionPreparation prepareChordProgressions(int mainGeneratorSeed) {
		List<int[]> userProgression = null;
		List<int[]> userRootProgression = null;
		List<Integer> customInversionIndexList = new ArrayList<>();
		if (!userChords.isEmpty()) {
			userProgression = new ArrayList<>();
			userRootProgression = new ArrayList<>();
			chordInts.clear();
			chordInts.addAll(userChords);
			int chordNum = 0;
			for (String chordString : userChords) {
				userProgression.add(mappedChord(chordString));
				userRootProgression.add(mappedChord(chordString, true));
				if (chordString.contains(".")) {
					customInversionIndexList.add(chordNum);
				}
				chordNum++;
			}
			LG.i("Using user's custom progression: " + StringUtils.join(userChords, ","));
		}

		List<int[]> generatedRootProgression = userRootProgression != null
				? userRootProgression
				: generateChordProgression(mainGeneratorSeed,
						!userChordsDurations.isEmpty() ? userChordsDurations.size()
								: gc.getFixedDuration());
		if (!userChordsDurations.isEmpty()) {
			progressionDurations = userChordsDurations;
		}

		SectionConfig sectionConfig = currentSection != null ? currentSection.getSecConfig() : null;
		progressionDurations = adjustByBeatDurationMultiplier(sectionConfig, progressionDurations);

		List<int[]> progressionToMap = userProgression != null
				? userProgression : generatedRootProgression;
		List<int[]> actualProgression = gc.isSquishProgressively()
				? MidiUtils.squishChordProgressionProgressively(progressionToMap,
						gc.isSpiceFlattenBigChords(), gc.getRandomSeed(),
						gc.getChordGenSettings().getFlattenVoicingChance(),
						customInversionIndexList, userRootProgression)
				: MidiUtils.squishChordProgression(progressionToMap,
						gc.isSpiceFlattenBigChords(), gc.getRandomSeed(),
						gc.getChordGenSettings().getFlattenVoicingChance(),
						customInversionIndexList, userRootProgression);

		return new ProgressionPreparation(actualProgression, generatedRootProgression,
				progressionDurations);
	}

	private void publishGeneratedScores(int mainGeneratorSeed, String fileName, Arrangement arr,
			Score score, Score scoreFull, long systemTime) {
		score.setTempo(gc.getBpm());
		scoreFull.setTempo(gc.getBpm());
		consoleOutputController.suppressStandardOutput();
		LG.i("Printing score...");
		JMusicUtilsCustom.midi(score, fileName);
		LG.i("Printing scoreFull...");
		JMusicUtilsCustom.midi(scoreFull, Constants.TEMPORARY_SEQUENCE_MIDI_NAME);
		consoleOutputController.restoreAfterMidiWrite();

		ScoreHistory.add(scoreFull);

		gc.setActualArrangement(arr);
		LG.i("MidiGenerator time: " + (System.currentTimeMillis() - systemTime) + " ms");
		LG.i("********Viewing midi seed: " + mainGeneratorSeed + "************* ");
	}

	public void setUserMelody(Phrase userMelody) {
		mgen.setUserMelody(userMelody);
	}

	private ScoreParts createScoreParts() {
		List<PartExt> melodyParts = new ArrayList<>();
		List<PartExt> melodyPartsFull = new ArrayList<>();
		for (int i = 0; i < gc.getMelodyParts().size(); i++) {
			PartExt part = new PartExt(instNames[0] + i, gc.getMelodyParts().get(i).getInstrument(),
					gc.getMelodyParts().get(i).getMidiChannel() - 1);
			melodyParts.add(part);
		}
		for (int i = 0; i < gc.getMelodyParts().size(); i++) {
			melodyPartsFull.add(new PartExt(instNames[0] + i,
					gc.getMelodyParts().get(i).getInstrument(),
					gc.getMelodyParts().get(i).getMidiChannel() - 1));
		}

		List<PartExt> chordParts = new ArrayList<>();
		for (int i = 0; i < gc.getChordParts().size(); i++) {
			chordParts.add(new PartExt(instNames[2] + i, gc.getChordParts().get(i).getInstrument(),
					gc.getChordParts().get(i).getMidiChannel() - 1));
		}
		List<PartExt> arpParts = new ArrayList<>();
		for (int i = 0; i < gc.getArpParts().size(); i++) {
			arpParts.add(new PartExt(instNames[3] + i, gc.getArpParts().get(i).getInstrument(),
					gc.getArpParts().get(i).getMidiChannel() - 1));
		}
		List<PartExt> bassParts = new ArrayList<>();
		for (int i = 0; i < gc.getBassParts().size(); i++) {
			bassParts.add(new PartExt(instNames[1] + i, gc.getBassParts().get(i).getInstrument(),
					gc.getBassParts().get(i).getMidiChannel() - 1));
		}

		List<PartExt> drumParts = new ArrayList<>();
		List<PartExt> drumPartsFull = new ArrayList<>();
		for (int i = 0; i < gc.getDrumParts().size(); i++) {
			drumParts.add(new PartExt(instNames[4] + i, 0, 9));
			drumPartsFull.add(new PartExt(instNames[4] + i, 0, 9));
		}
		return new ScoreParts(melodyParts, melodyPartsFull, chordParts, arpParts, bassParts,
				drumParts, drumPartsFull);
	}

	public void generateMasterpiece(int mainGeneratorSeed, String fileName) {
		LG.i("========================== MIDI GENERATION IN PROGRESS, " + new Date().toString()
				+ " ===========================");
		long systemTime = System.currentTimeMillis();
		boolean logPerformance = false;
		customDrumMappingNumbers = null;
		//MELODY_SCALE = gc.getScaleMode().absoluteNotesC;

		Score score = new Score("MainScore", 120);
		Score scoreFull = new Score("MainScore", 120);

		ScoreParts scoreParts = createScoreParts();

		ProgressionPreparation progressionPreparation =
				prepareChordProgressions(mainGeneratorSeed);

		if (!debugEnabled) {
			consoleOutputController.suppressStandardOutput();
		}
		if (logPerformance) {
			LG.i("Generated chords, starting arrangement after: "
					+ (System.currentTimeMillis() - systemTime));
		}
		// Arrangement process..
		LG.i("Starting arrangement..");


		Arrangement arr = processArrangementSections(mainGeneratorSeed, systemTime,
				logPerformance, progressionPreparation);

		addSectionsToScoreParts(arr, scoreParts, systemTime, logPerformance);
		setupScore(mainGeneratorSeed, systemTime, logPerformance, score, scoreParts.melody, scoreParts.chords,
				scoreParts.arps, scoreParts.bass, scoreParts.drums, true, true);
		setupScore(mainGeneratorSeed, systemTime, logPerformance, scoreFull, scoreParts.melodyFull,
				scoreParts.chords, scoreParts.arps, scoreParts.bass, scoreParts.drumsFull, false, false);
		publishGeneratedScores(mainGeneratorSeed, fileName, arr, score, scoreFull, systemTime);
	}

	private void addSectionsToScoreParts(Arrangement arr, ScoreParts scoreParts,
			long systemTime, boolean logPerformance) {
		Optional<MelodyPart> firstPresentPart = gc.getMelodyParts().stream()
				.filter(e -> !e.isMuted()).findFirst();

		for (Section sec : arr.getSections()) {
			for (int i = 0; i < sec.getMelodies().size(); i++) {
				Phrase p = sec.getMelodies().get(i);
				p.setStartTime(p.getStartTime() + sec.getStartTime());
				p.setAppend(false);
				if (!gc.isCombineMelodyTracks()) {
					scoreParts.melody.get(i).addPhrase(p);
				} else {
					if (firstPresentPart.isPresent()) {
						scoreParts.melody.get(firstPresentPart.get().getAbsoluteOrder(gc.getMelodyParts()))
								.addPhrase(p);
					}
				}
				scoreParts.melodyFull.get(i).addPhrase(p.copy());
			}
			for (int i = 0; i < sec.getBasses().size(); i++) {
				Phrase bp = sec.getBasses().get(i);
				bp.setStartTime(bp.getStartTime() + sec.getStartTime());
				scoreParts.bass.get(i).addPhrase(bp);
			}
			for (int i = 0; i < sec.getChords().size(); i++) {
				Phrase cp = sec.getChords().get(i);
				cp.setStartTime(cp.getStartTime() + sec.getStartTime());
				scoreParts.chords.get(i).addPhrase(cp);
			}
			for (int i = 0; i < sec.getArps().size(); i++) {
				Phrase cp = sec.getArps().get(i);
				cp.setStartTime(cp.getStartTime() + sec.getStartTime());
				scoreParts.arps.get(i).addPhrase(cp);
			}

			Optional<DrumPart> firstPresentDrumPart = gc.getDrumParts().stream()
					.filter(e -> !e.isMuted()).findFirst();
			for (int i = 0; i < sec.getDrums().size(); i++) {
				Phrase p = sec.getDrums().get(i);
				p.setStartTime(p.getStartTime() + sec.getStartTime());
				if (COLLAPSE_DRUM_TRACKS && firstPresentDrumPart.isPresent()) {
					p.setAppend(false);
					scoreParts.drums.get(firstPresentDrumPart.get().getAbsoluteOrder(gc.getDrumParts()))
							.addPhrase(p);
				} else {
					scoreParts.drums.get(i).addPhrase(p);
				}
				scoreParts.drumsFull.get(i).addPhrase(p.copy());

			}
			if (gc.getChordParts().size() > 0 && gc.isChordsEnable()) {
				Phrase csp = sec.getChordSlash();
				csp.setStartTime(csp.getStartTime() + sec.getStartTime());
				csp.setAppend(false);
				scoreParts.chords.get(0).addPhrase(csp);
			}

		}
		if (logPerformance) {
			LG.i("Added to parts, at: " + (System.currentTimeMillis() - systemTime));
		}
		LG.d("Added sections to parts..");
	}

	private Arrangement processArrangementSections(int mainGeneratorSeed, long systemTime,
			boolean logPerformance, ProgressionPreparation progressionPreparation) {
		List<int[]> generatedRootProgression = progressionPreparation.generatedRootProgression;
		List<Double> actualDurations = progressionPreparation.durations;
		List<int[]> actualProgression = progressionPreparation.actualProgression;
		// prepare progressions
		chordProgression = actualProgression;
		rootProgression = generatedRootProgression;

		// run one empty pass through melody generation
		Phrase userMelody = mgen.getUserMelody();
		if (userMelody != null) {
			mgen.processUserMelody(userMelody);
			actualProgression = chordProgression;
			generatedRootProgression = rootProgression;
			actualDurations = progressionDurations;
		} else if (!gc.getMelodyParts().isEmpty()) {
			fillMelodyFromPart(gc.getMelodyParts().get(0), actualProgression,
					generatedRootProgression, 0, new Section(), new ArrayList<>(),
					true, gc.getMelodyBlockChoicePreference());
		}
		if (logPerformance) {
			LG.i("First pre-melody filled at: " + (System.currentTimeMillis() - systemTime));
		}
		progressionDurationsBackup = actualDurations;
		chordProgressionBackup = actualProgression;
		rootProgressionBackup = generatedRootProgression;


		double measureLength = 0;
		for (Double d : progressionDurations) {
			measureLength += d;
		}
		GENERATED_MEASURE_LENGTH = measureLength / noteMultiplier;
		int counter = 0;

		Arrangement arr = null;
		boolean overridden = false;

		int originalPVC = gc.getArrangementPartVariationChance();
		int originalVC = gc.getArrangementVariationChance();

		if (gc.getArrangement().isOverridden()) {
			arr = gc.getActualArrangement();
			overridden = true;
		} else {
			if (gc.getArrangement().isPreviewChorus()) {
				arr = new Arrangement();
				gc.setArrangementPartVariationChance(0);
				gc.setArrangementVariationChance(0);
			} else {
				arr = gc.getArrangement();
			}
		}


		if (false) {
			InputStream is = new InputStream() {
				public int read() throws IOException {
					return 0;
				}
			};
		}
		boolean isPreview = arr.getSections().size() == 1;
		LG.i("Arrangement - MANUAL? " + overridden);
		int arrSeed = (arr.getSeed() != 0) ? arr.getSeed() : mainGeneratorSeed;
		secOrder = -1;
		int normalPartVariationChance = gc.getArrangementPartVariationChance();

		storeGlobalParts();

		currentSection = null;
		Integer transToSet = null;
		ScaleMode scaleToSet = null;
		boolean twoFiveOneChanged = false;
		double sectionStartTimer = 0;
		modScale = gc.getScaleMode();
		gc.getArrangement().recalculatePartInclusionMapBoundsIfNeeded(gc::getInstParts);
		for (Section sec : arr.getSections()) {
			LG.i("*********************************** Processing section.. " + sec.getType()
					+ "!***** Time: " + (System.currentTimeMillis() - systemTime));
			currentSection = sec;
			sec.initPartMapIfNull(gc::getInstParts);
			sec.recalculatePartVariationMapBoundsIfNeeded(gc::getInstParts);
			if (overridden) {
				sec.initPartMapFromOldData(gc::getInstParts);
			}
			sec.setSectionDuration(-1);
			sec.setSectionBeatDurations(null);
			boolean gcPartsReplaced = SectionGenerationPlanner.replaceConfiguredParts(gc, sec);
			secOrder++;
			sec.setStartTime(sectionStartTimer);

			Random rand = new Random(arrSeed);

			if (transToSet != null) {
				modTrans = transToSet;
			}
			if (scaleToSet != null) {
				modScale = scaleToSet;
			}

			LG.i("Key extra transpose: " + modTrans + ", key scale: " + modScale.toString());

			if (sec.isClimax()) {
				// increase variations in follow-up CLIMAX sections, reset when climax ends
				gc.setArrangementPartVariationChance(
						gc.getArrangementPartVariationChance() + normalPartVariationChance / 4);
			} else {
				gc.setArrangementPartVariationChance(normalPartVariationChance);
			}

			SectionGenerationPlanner.applyBuildupVariation(gc, sec, overridden, rand);

			int notesSeedOffset = sec.getTypeMelodyOffset();

			Random variationGen = new Random(arrSeed + sec.getTypeSeedOffset());
			List<Integer> sectionVariations = SectionGenerationPlanner.calculateVariations(gc,
					arr, secOrder, sec, notesSeedOffset, variationGen);
			List<String> includedSectionVarNames = new ArrayList<>();
			for (int i = 0; i < sectionVariations.size(); i++) {
				if (sectionVariations.get(i) > 0) {
					includedSectionVarNames.add(Section.sectionVariationNames[i]);
				}
			}
			LG.i("Section Variations: " + StringUtils.join(includedSectionVarNames, ","));

			SectionGenerationPlanner.assignTransition(gc, arr, secOrder, sec, notesSeedOffset,
					overridden, variationGen);
			LG.i("Transition type: " + sec.getTransitionType() + ", MVI: " + notesSeedOffset);


			// reset back to normal?
			boolean sectionChordsReplaced = false;
			if (sec.isCustomChordsEnabled() || sec.isCustomDurationsEnabled()) {
				sectionChordsReplaced = replaceWithSectionCustomChordDurations(sec);
			}
			boolean useMelodyProgression = SectionGenerationPlanner.shouldUseMelodyProgression(
					sectionVariations, sectionChordsReplaced, rootProgression,
						generatedRootProgression, mgen.alternateChords);
			if (!sectionChordsReplaced) {
				sec.setGeneratedSectionBeatDurations(new ArrayList<>(progressionDurations));

				if (useMelodyProgression) {
					//LG.d("Section Variation: Chord Swap!");
					rootProgression = mgen.melodyBasedRootProgression;
					chordProgression = mgen.melodyBasedChordProgression;
					progressionDurations = actualDurations;
					sec.setDisplayAlternateChords(true);
					sec.setCustomChords(mgen.alternateChords);
				} else {
					rootProgression = generatedRootProgression;
					chordProgression = actualProgression;
					progressionDurations = actualDurations;
					sec.setDisplayAlternateChords(false);

				}
			} else if (useMelodyProgression) {
				//LG.d("Section Variation: Chord Swap!");
				rootProgression = mgen.melodyBasedRootProgression;
				chordProgression = mgen.melodyBasedChordProgression;
				progressionDurations = actualDurations;
			}

			SectionConfig secC = sec.getSecConfig();

			SectionGenerationPlanner.KeyChangeDecision keyChangeDecision =
					SectionGenerationPlanner.chooseSectionKeyChange(gc, sec, sectionVariations,
							generatedRootProgression, arrSeed, transToSet, modTrans, scaleToSet);
			transToSet = keyChangeDecision.transpose;
			scaleToSet = keyChangeDecision.scale;

			boolean twoFiveOneChords = ((gc.getKeyChangeType() == KeyChangeType.TWOFIVEONE
					|| secC.getCustomKeyChangeType() == 1) && (secC.getCustomKeyChangeType() != 2))
					&& (sectionVariations.get(4) > 0);
			if (sectionVariations.get(0) > 0 && !twoFiveOneChords) {
				//LG.d("Section Variation: Skip N-1 Chord!");
				skipN1Chord();
			}

			if (logPerformance) {
				LG.i("After variations and transitions, at: "
						+ (System.currentTimeMillis() - systemTime));
			}

			rand.setSeed(arrSeed);
			variationGen.setSeed(arrSeed);

			SectionGenerationPlanner.calculatePresences(gc, sec, rand, variationGen, overridden,
					arrSeed, notesSeedOffset, isPreview, counter, arr);

			// FARAWAY
			/*if (!overridden && secOrder > 1) {
				adjustArrangementPresencesIfNeeded(sec, arr.getSections().get(secOrder - 1));
			}*/

			double currentMeasureLength = (sec.getSectionDuration() > 0) ? sec.getSectionDuration()
					: measureLength;

			fillMelodyPartsForSection(currentMeasureLength, overridden, sec, notesSeedOffset,
					sectionVariations, sectionChordsReplaced);

			if (logPerformance) {
				LG.i("After fill melody, at: " + (System.currentTimeMillis() - systemTime));
			}
			// possible chord changes handled after melody parts are filled
			if (twoFiveOneChanged) {
				twoFiveOneChanged = false;
				replaceFirstChordForTwoFiveOne();
			}

			if (twoFiveOneChords && chordInts.size() > 2 && transToSet != null) {
				twoFiveOneChanged = replaceLastChordsForTwoFiveOne(transToSet, scaleToSet);
			}

			fillOtherPartsForSection(sec, arr, overridden, sectionVariations, variationGen, arrSeed,
					currentMeasureLength);

			if (logPerformance) {
				LG.i("After fill other, at: " + (System.currentTimeMillis() - systemTime));
			}
			postprocessMelodyRhythmAccents(sec, arr, measureLength, overridden);

			if (gcPartsReplaced) {
				restoreGlobalPartsToGuiConfig();
			}
			counter += sec.getMeasures();
			sectionStartTimer += currentMeasureLength * sec.getMeasures();

			if (logPerformance) {
				LG.i("End of section, at: " + (System.currentTimeMillis() - systemTime));
			}
		}
		LG.d("Added phrases to sections..");
		if (false) {
			new Object() {
			};
		}


		gc.setArrangementPartVariationChance(originalPVC);
		gc.setArrangementVariationChance(originalVC);


		return arr;
	}
	private List<Double> adjustByBeatDurationMultiplier(SectionConfig sc, List<Double> durations) {
		int beatDurMultiIndex = (sc != null && sc.getBeatDurationMultiplierIndex() != null)
				? sc.getBeatDurationMultiplierIndex()
				: gc.getBeatDurationMultiplierIndex();
		if (beatDurMultiIndex == 0) {
            progressionDurations.replaceAll(aDouble -> aDouble * 0.5);
		} else if (beatDurMultiIndex == 2) {
            progressionDurations.replaceAll(aDouble -> aDouble * 2);
		}
		return durations;
	}

	private void setupScore(int mainGeneratorSeed, long systemTime, boolean logPerformance,
			Score score, List<PartExt> melodyParts, List<PartExt> chordParts,
			List<PartExt> arpParts, List<PartExt> bassParts, List<PartExt> drumParts,
			boolean allowCombination, boolean transposeBCA) {
		MidiScoreBuilder scoreBuilder = new MidiScoreBuilder(gc, outputOptions.getTrackPadding(),
				COLLAPSE_DRUM_TRACKS, noteMultiplier, sequenceTrackAssigner::assign);
		scoreBuilder.build(mainGeneratorSeed, systemTime, logPerformance, score, melodyParts,
				chordParts, arpParts, bassParts, drumParts, allowCombination, transposeBCA);
	}


	private void postprocessMelodyRhythmAccents(Section sec, Arrangement arr, double measureLength,
			boolean overridden) {
		// find times when drums are present -> depends on combobox selection
		if (gc.getMelodyRhythmAccents() == 0 || sec.getDrums().isEmpty()
				|| sec.getMelodies().isEmpty()) {
			//  (NONE -> return)
			return;
		}

		List<Double> drumHitTimes = findDrumHitTimes(sec.getDrums(), gc.getMelodyRhythmAccents(),
				gc.isDrumCustomMapping());

		if (gc.isMelodyRhythmAccentsPocket()) {
			List<Double> fullMeasureHits = new ArrayList<>();
			for (double i = 0; i < sec.getMeasures()
					* measureLength; i += Durations.SIXTEENTH_NOTE) {
				double time = i;
				if (!(drumHitTimes.stream()
						.anyMatch(drumTime -> Math.abs(drumTime - time) < Constants.DBL_ERR))) {
					fullMeasureHits.add(i);
				}
			}
			drumHitTimes = fullMeasureHits;
		}


		// for each melody, make a note list sorted by start time
		int iterations = gc.getMelodyRhythmAccents() > 3 ? 2 : 1;
		for (int iter = 0; iter < iterations; iter++) {
			for (int melodyIndex = 0; melodyIndex < sec.getMelodies().size(); melodyIndex++) {
				Phrase phr = sec.getMelodies().get(melodyIndex);
				List<Note> notes = phr.getNoteList();
				if (notes.isEmpty()) {
					continue;
				}
				List<Integer> sortedPitches = notes.stream().filter(e -> e.getPitch() >= 0)
						.map(e -> e.getPitch() % 12).collect(Collectors.toList());
				if (sortedPitches.isEmpty()) {
					// no non-rest notes
					continue;
				}
				sortedPitches = new ArrayList<>(new HashSet<>(sortedPitches));
				Collections.sort(sortedPitches);

				MelodyPart mp = gc.getMelodyParts().get(melodyIndex);
				Random accentGenerator = new Random(mp.getPatternSeed());

				// find where a drum start time intersects with a note's start-end
				double currentRv = 0;
				Vector<Note> newNotes = new Vector<>(notes);
				int addedNotes = 0;
				for (int i = 0; i < notes.size(); i++) {
					Note n = notes.get(i);
					double currTime = currentRv;
					currentRv += n.getRhythmValue();
					int originalPitch = n.getPitch();

					if (accentGenerator.nextInt(100) >= mp.getAccents()) {
						continue;
					}

					if (n.getDuration() - Constants.DBL_ERR < Durations.SIXTEENTH_NOTE) {
						continue;
					}

					if (originalPitch < 0) {
						continue;
					}

					// small 32nd buffer to prevent cutting notes that would result in too small leftovers
					double startTime = n.getOffset() + currTime + Durations.SIXTEENTH_NOTE / 2
							+ Constants.DBL_ERR;
					double endTime = startTime + n.getDuration() - Durations.SIXTEENTH_NOTE / 2
							- Constants.DBL_ERR;
					if (startTime >= endTime) {
						continue;
					}
					List<Double> intersectingDrumHits = drumHitTimes.stream()
							.filter(e -> (startTime < e && e < endTime))
							.collect(Collectors.toList());
					if (intersectingDrumHits.isEmpty()) {
						continue;
					}
					List<Double> sixteenthAlignedDrumHits = intersectingDrumHits.stream()
							.filter(e -> MidiUtils.isMultiple(e, Durations.SIXTEENTH_NOTE))
							.collect(Collectors.toList());
					double intersection;
					if (!sixteenthAlignedDrumHits.isEmpty()) {
						intersection = sixteenthAlignedDrumHits.get(0);
						LG.d("Found 16th intersections: " + intersection + ", note start: "
								+ (currTime + n.getOffset()));
					} else {
						intersection = intersectingDrumHits.get(0);
						LG.d("No 16th intersections: " + intersection + ", note start: "
								+ (currTime + n.getOffset()));
					}
					int noteInsertionIndex = i + addedNotes;

					// |---x----------| -> |---|---------| -> old note's duration is intersection length, new note's offset is moved up by the same amount
					double intersectionLength = intersection - currTime - n.getOffset();
					// skip if either of the resulting 2 notes would be too short
					if (intersectionLength - Constants.DBL_ERR < Durations.SIXTEENTH_NOTE/2 || (n.getDuration() - intersectionLength - Constants.DBL_ERR) < Durations.SIXTEENTH_NOTE/2) {
						continue;
					}

					Note splitNote = new Note(originalPitch, 0, n.getDynamic());
					splitNote.setDuration(n.getDuration() - intersectionLength);
					splitNote.setOffset(n.getOffset() + intersectionLength);
					switch (gc.getMelodyRhythmAccentsMode()) {
					case 0:
						splitNote.setPitch(Pitches.REST);
						break;
					case 1:
						int newPitchOrderUp = sortedPitches.indexOf(originalPitch % 12) + 1;
						int pitchAdd = (newPitchOrderUp < sortedPitches.size())
								? sortedPitches.get(newPitchOrderUp)
								: (sortedPitches.get(0) + 12);
						splitNote.setPitch(MidiUtils.octavePitch(originalPitch) + pitchAdd);
						break;
					case 2:
						int newPitchOrderDown = sortedPitches.indexOf(originalPitch % 12) - 1;
						int pitchSubtract = (newPitchOrderDown >= 0)
								? sortedPitches.get(newPitchOrderDown)
								: (sortedPitches.get(sortedPitches.size() - 1) - 12);
						splitNote.setPitch(MidiUtils.octavePitch(originalPitch) + pitchSubtract);
						break;
					case 3:
						int newPitchOrder = sortedPitches.indexOf(originalPitch % 12)
								+ (accentGenerator.nextBoolean() ? 1 : -1);
						int pitchAdjustment = (newPitchOrder >= 0
								&& newPitchOrder < sortedPitches.size())
										? sortedPitches.get(newPitchOrder)
										: (newPitchOrder < 0
												? (sortedPitches.get(sortedPitches.size() - 1) - 12)
												: (sortedPitches.get(0) + 12));
						splitNote.setPitch(MidiUtils.octavePitch(originalPitch) + pitchAdjustment);
						break;
					case 4:
						splitNote.setDynamic((int) Math.min(126, n.getDynamic() * 1.25));
						break;
					case 5:
						splitNote.setDynamic((int) Math.min(126, n.getDynamic() * 0.75));
						break;
					default:
						break;
					}
					n.setDuration(intersectionLength * SPLIT_DURATION_MULTIPLIER);

					newNotes.add(noteInsertionIndex, splitNote);
					addedNotes++;

				}
				if (addedNotes > 0) {
					phr.setNoteList(newNotes);
				}
			}
		}

		// REMINDER: melody and drums can have separate delays -> in that case, accenting is not guaranteed to sound good
	}

	private static List<Double> findDrumHitTimes(List<Phrase> drums, int melodyRhythmAccents,
			boolean drumCustomMapping) {
		List<Double> drumHitTimes = new ArrayList<>();
		Set<Integer> validPitches = new HashSet<>();

		switch (melodyRhythmAccents) {
		case 1:
			// snare
			validPitches.add(38);
			validPitches.add(40);
			break;
		case 2:
			// kick
			validPitches.add(35);
			validPitches.add(36);
			break;
		case 3:
			// open HH/ride
			validPitches.add(46);
			validPitches.add(53);
			break;
		case 4:
			// 1 + 2
			validPitches.add(38);
			validPitches.add(40);
			validPitches.add(35);
			validPitches.add(36);
			break;
		case 5:
			// 1 + 3
			validPitches.add(38);
			validPitches.add(40);
			validPitches.add(46);
			validPitches.add(53);
			break;
		default:
			throw new IllegalArgumentException("Invalid melody rhythm accent type.");
		}

		if (drumCustomMapping) {
			// convert set to semitonal mapping
			validPitches = validPitches.stream().map(e -> mapDrumPitchByCustomMapping(e, true))
					.collect(Collectors.toSet());
		}
		for (Phrase phr : drums) {
			List<Note> notes = phr.getNoteList();
			double currTime = 0;
			for (Note n : notes) {
				if (validPitches.contains(n.getPitch())) {
					drumHitTimes.add(currTime + n.getOffset());
				}
				currTime += n.getRhythmValue();
			}
		}
		return drumHitTimes;
	}

	/*private void adjustArrangementPresencesIfNeeded(Section sec, Section prevSec) {
		int currentSecEnergy = sec.getTypeMelodyOffset();
		int prevSecEnergy = prevSec.getTypeMelodyOffset();
		if (currentSecEnergy == prevSecEnergy) {
			return;
		}
	
		// compare presences and adjust using part inclusions
		int currentPresCount = OMNI.PART_INTS.stream().map(e -> sec.countPresence(e))
				.mapToInt(e -> e).sum();
		int prevPresCount = OMNI.PART_INTS.stream().map(e -> prevSec.countPresence(e))
				.mapToInt(e -> e).sum();
	}*/

	private void fillMelodyPartsForSection(double measureLength, boolean overridden, Section sec,
			int notesSeedOffset, List<Integer> sectionVariations, boolean sectionChordsReplaced) {
		if (gc.isMelodyEnable() && !gc.getMelodyParts().isEmpty()) {
			List<Phrase> copiedPhrases = new ArrayList<>();
			Set<Integer> presences = sec.getPresence(0);
			for (int i = 0; i < gc.getMelodyParts().size(); i++) {
				MelodyPart mp = gc.getMelodyParts().get(i);
				boolean added = presences.contains(mp.getOrder());
				if (added && !mp.isMuted()) {
					List<int[]> usedMelodyProg = chordProgression;
					List<int[]> usedRoots = rootProgression;

					// if n-1, do not also swap melody
					if (sectionVariations.get(2) > 0 && sectionVariations.get(0) == 0
							&& !sectionChordsReplaced) {
						usedMelodyProg = mgen.melodyBasedChordProgression;
						usedRoots = mgen.melodyBasedRootProgression;
						//LG.d("Section Variation: Melody Swap!");
					}
					List<Integer> variations = (overridden) ? sec.getVariation(0, i) : null;
					int speedSave = mp.getSpeed();
					// max speed variation
					boolean speedVariation = sectionVariations.get(3) > 0;
					if (speedVariation) {
						mp.setSpeed(100);
					}
					Phrase m = fillMelodyFromPart(mp, usedMelodyProg, usedRoots, notesSeedOffset,
							sec, variations, false, gc.getMelodyBlockChoicePreference());
					if (speedVariation) {
						mp.setSpeed(speedSave);
					}
					if (melodyParts.get(i).getInstrument() != mp.getInstrument()) {
						m.setInstrument(mp.getInstrument());
					}
					/*
					// DOUBLE melody with -12 trans, if there was a variation of +12 and it's a major part and it's the first (full) melody
					// Section Variation - wacky melody transpose
					boolean laxCheck = notesSeedOffset == 0
							&& sec.getVariation(0, i).contains(Integer.valueOf(0));
					if (!sectionVariations.get(3)) {
						laxCheck &= (i == 0);
					}
					
					if (laxCheck) {
						JMusicUtilsCustom.doublePhrase(m);
					}*/
					copiedPhrases.add(m);
				} else {
					Note emptyMeasureNote = new Note(Pitches.REST, measureLength);
					Phrase emptyPhrase = new PhraseExt(0, mp.getOrder(), secOrder);
					emptyPhrase.setStartTime(START_TIME_DELAY);
					emptyPhrase.add(emptyMeasureNote);
					copiedPhrases.add(emptyPhrase.copy());
				}
			}
			sec.setMelodies(copiedPhrases);
		}
	}

	private void fillOtherPartsForSection(Section sec, Arrangement arr, boolean overridden,
			List<Integer> sectionVariations, Random variationGen, int arrSeed,
			double measureLength) {
		// copied into empty sections
		Note emptyMeasureNote = new Note(Pitches.REST, measureLength);
		Phrase emptyPhrase = new PhraseExt();
		emptyPhrase.setStartTime(START_TIME_DELAY);
		emptyPhrase.add(emptyMeasureNote);

		if (gc.isBassEnable() && !gc.getBassParts().isEmpty()) {
			List<Phrase> copiedPhrases = new ArrayList<>();
			Set<Integer> presences = sec.getPresence(1);
			for (int i = 0; i < gc.getBassParts().size(); i++) {
				BassPart bp = gc.getBassParts().get(i);
				boolean added = presences.contains(bp.getOrder());
				if (added && !bp.isMuted()) {
					List<Integer> variations = (overridden) ? sec.getVariation(1, i) : null;
					Phrase b = fillBassFromPart(bp, rootProgression, sec, variations);

					if (bp.isDoubleOct()) {
						b = JMusicUtilsCustom.doublePhrase(b, 12, false, -15);
						b.setStartTime(START_TIME_DELAY);
					}
					if (bassParts.get(i).getInstrument() != bp.getInstrument()) {
						b.setInstrument(bp.getInstrument());
					}
					copiedPhrases.add(b);
				} else {
					copiedPhrases.add(emptyPhrase.copy());
				}
			}

			sec.setBasses(copiedPhrases);
		}

		if (gc.isChordsEnable() && !gc.getChordParts().isEmpty()) {
			List<Phrase> copiedPhrases = new ArrayList<>();
			Set<Integer> presences = sec.getPresence(2);
			boolean useChordSlash = false;
			for (int i = 0; i < gc.getChordParts().size(); i++) {
				ChordPart cp = gc.getChordParts().get(i);
				boolean added = presences.contains(cp.getOrder());
				if (added && !cp.isMuted()) {
					if (i == 0) {
						useChordSlash = true;
					}
					List<Integer> variations = (overridden) ? sec.getVariation(2, i) : null;
					Phrase c = fillChordsFromPart(cp, chordProgression, sec, variations);
					if (chordParts.get(i).getInstrument() != cp.getInstrument()) {
						c.setInstrument(cp.getInstrument());
					}
					copiedPhrases.add(c);
				} else {
					copiedPhrases.add(emptyPhrase.copy());
				}
			}
			sec.setChords(copiedPhrases);
			if (useChordSlash) {
				sec.setChordSlash(fillChordSlash(chordProgression, sec.getMeasures()));
			} else {
				sec.setChordSlash(emptyPhrase.copy());
			}

		}

		if (gc.isArpsEnable() && !gc.getArpParts().isEmpty()) {
			List<Phrase> copiedPhrases = new ArrayList<>();
			Set<Integer> presences = sec.getPresence(3);
			for (int i = 0; i < gc.getArpParts().size(); i++) {
				ArpPart ap = gc.getArpParts().get(i);
				// if arp1 supports melody with same instrument, always introduce it in second half
				List<Integer> variations = (overridden) ? sec.getVariation(3, i) : null;
				boolean added = presences.contains(ap.getOrder());
				if (added && !ap.isMuted()) {
					Phrase a = fillArpFromPart(ap, chordProgression, sec, variations);
					if (arpParts.get(i).getInstrument() != ap.getInstrument()) {
						a.setInstrument(ap.getInstrument());
					}
					copiedPhrases.add(a);
				} else {
					copiedPhrases.add(emptyPhrase.copy());
				}
			}
			sec.setArps(copiedPhrases);
		}

		if (gc.isDrumsEnable() && !gc.getDrumParts().isEmpty()) {
			List<Phrase> copiedPhrases = new ArrayList<>();
			Set<Integer> presences = sec.getPresence(4);
			for (int i = 0; i < gc.getDrumParts().size(); i++) {
				DrumPart dp = gc.getDrumParts().get(i);
				variationGen.setSeed(arrSeed + 300 + dp.getOrderOffset());

				boolean added = presences.contains(dp.getOrder());
				if (added && !dp.isMuted()) {
					boolean sectionForcedDynamics = (sec.isClimax())
							&& variationGen.nextInt(100) < gc.getArrangementPartVariationChance();
					List<Integer> variations = (overridden) ? sec.getVariation(4, i) : null;
					Phrase d = fillDrumsFromPart(dp, chordProgression, sectionForcedDynamics, sec,
							variations);

					copiedPhrases.add(d);
				} else {
					copiedPhrases.add(emptyPhrase.copy());
				}
			}
			sec.setDrums(copiedPhrases);
		}
	}


	public boolean replaceWithSectionCustomChordDurations(Section sec) {
		SectionConfig sectionConfig = currentSection != null ? currentSection.getSecConfig() : null;
		SectionGenerationPlanner.CustomProgression customProgression =
				SectionGenerationPlanner.prepareCustomProgression(gc, sec, sectionConfig,
						progressionDurations, FIRST_CHORD, LAST_CHORD, Durations.WHOLE_NOTE);
		if (customProgression == null) {
			return false;
		}

		chordProgression = customProgression.chords;
		rootProgression = customProgression.roots;
		progressionDurations = customProgression.durations;
		sec.setSectionBeatDurations(progressionDurations);
		sec.setSectionDuration(progressionDurations.stream().mapToDouble(e -> e).sum());
		if (!sec.isCustomChordsEnabled()) {
			sec.setCustomChords(StringUtils.join(customProgression.chordNames, ","));
			sec.setDisplayAlternateChords(true);
		}
		LG.i("Using SECTION custom progression: "
				+ StringUtils.join(customProgression.chordNames, ","));
		return true;
	}

	public void replaceChordsDurationsFromBackup() {
		chordProgression = chordProgressionBackup;
		rootProgression = rootProgressionBackup;
		progressionDurations = progressionDurationsBackup;
	}

	public void storeGlobalParts() {
		melodyParts = gc.getMelodyParts();
		bassParts = gc.getBassParts();
		chordParts = gc.getChordParts();
		arpParts = gc.getArpParts();
		drumParts = gc.getDrumParts();

	}

	public void restoreGlobalPartsToGuiConfig() {
		gc.setMelodyParts(melodyParts);
		gc.setBassParts(bassParts);
		gc.setChordParts(chordParts);
		gc.setArpParts(arpParts);
		gc.setDrumParts(drumParts);
	}

	private void replaceFirstChordForTwoFiveOne() {
		if (chordInts.get(0).startsWith("C")) {
			return;
		}

		List<int[]> altChordProgression = new ArrayList<>();
		List<int[]> altRootProgression = new ArrayList<>();
		altChordProgression.addAll(chordProgression);
		altRootProgression.addAll(rootProgression);

		int[] c = MidiUtils.mappedChord("CGCE");
		altChordProgression.set(0, c);
		altRootProgression.set(0, Arrays.copyOfRange(c, 0, 1));

		chordProgression = altChordProgression;
		rootProgression = altRootProgression;

		LG.d("Replaced FIRST");
	}

	private boolean replaceLastChordsForTwoFiveOne(int transToSet, ScaleMode scaleToSet) {
		int size = chordProgression.size();
		if (size < 3) {
			return false;
		}
		List<int[]> altChordProgression = new ArrayList<>();
		List<int[]> altRootProgression = new ArrayList<>();
		altChordProgression.addAll(chordProgression);
		altRootProgression.addAll(rootProgression);
		int[] dm = MidiUtils.transposeChord(MidiUtils.mappedChord("Dm"), transToSet);
		int[] g7 = MidiUtils.transposeChord(MidiUtils.mappedChord("G7"), transToSet);
		if (scaleToSet != null) {
			dm = MidiUtils.transposeChord(dm, modScale.noteAdjustScale, scaleToSet.noteAdjustScale);
			g7 = MidiUtils.transposeChord(g7, modScale.noteAdjustScale, scaleToSet.noteAdjustScale);
		}

		//if (transToSet != -2) {
		altChordProgression.set(size - 2, dm);
		altRootProgression.set(size - 2, Arrays.copyOf(dm, 1));
		//}

		altChordProgression.set(size - 1, g7);
		altRootProgression.set(size - 1, Arrays.copyOf(g7, 1));
		chordProgression = altChordProgression;
		rootProgression = altRootProgression;

		LG.d("Replaced LAST");
		return true;

	}

	private void skipN1Chord() {
		List<Double> altProgressionDurations = new ArrayList<>();
		List<int[]> altChordProgression = new ArrayList<>();
		List<int[]> altRootProgression = new ArrayList<>();

		// TODO: other variations on how to generate alternates?
		// 1: chord trick, max two measures
		// 60 30 4 1 -> 60 30 1 - , 60 30 4 1
		altProgressionDurations.addAll(progressionDurations);
		altChordProgression.addAll(chordProgression);
		altRootProgression.addAll(rootProgression);

		int size = progressionDurations.size();
		if (size < 3) {
			return;
		}

		double duration = progressionDurations.get(size - 1) + progressionDurations.get(size - 2);
		altProgressionDurations.set(size - 2, duration);
		altProgressionDurations.remove(size - 1);

		altChordProgression.remove(size - 2);
		altRootProgression.remove(size - 2);

		progressionDurations = altProgressionDurations;
		chordProgression = altChordProgression;
		rootProgression = altRootProgression;
	}

	public Phrase fillMelodyFromPart(MelodyPart ip, List<int[]> actualProgression,
			List<int[]> generatedRootProgression, int notesSeedOffset, Section sec,
			List<Integer> variations, boolean melodyEmptyPass, List<Integer> melodyBlockJumpPreference) {
		return melodyPhraseBuilder.build(ip, actualProgression, generatedRootProgression,
				progressionDurations, notesSeedOffset, sec, variations, melodyEmptyPass,
				melodyBlockJumpPreference, secOrder, START_TIME_DELAY, modTrans, modScale);
	}

	public Phrase fillBassFromPart(BassPart ip, List<int[]> generatedRootProgression, Section sec,
			List<Integer> variations) {
		LG.d("Processing: " + ip.partInfo());
		InstPhraseGenerator.Timing timing = getInstrumentPhraseTiming();
		InstPhraseGenerator.Result result = bassPhraseGenerator.generate(ip, generatedRootProgression,
				progressionDurations, melodyNotePatternMap, sec, variations, secOrder, timing);
		Phrase phr = result.getPhrase();
		Mod.transpose(phr, DEFAULT_INSTRUMENT_TRANSPOSE[1]);

		if (!overwriteWithCustomSectionMidi(sec, phr, ip)) {
			addPhraseNotesToSection(sec, ip, phr.getNoteList());
		}
		ScaleMode scale = (modScale != null) ? modScale : gc.getScaleMode();
		if (scale != ScaleMode.IONIAN) {
			MidiUtils.transposePhrase(phr, ScaleMode.IONIAN.noteAdjustScale, scale.noteAdjustScale,
					gc.isTransposedNotesForceScale());
		}
		Mod.transpose(phr, ip.getTranspose() + modTrans);
		phr.setStartTime(START_TIME_DELAY);
		addOffsetsToPhrase(phr, ip);
		if (result.shouldStoreVariations()) {
			sec.setVariation(1, 0, result.getVariations());
		}
		return phr;

	}

	public Phrase fillChordsFromPart(ChordPart ip, List<int[]> actualProgression, Section sec,
			List<Integer> variations) {
		LG.d("Processing: " + ip.partInfo());
		int measures = sec.getMeasures();
		InstPhraseGenerator.Timing timing = getInstrumentPhraseTiming();
		ChordPhraseGenerator.ChordResult result = chordPhraseGenerator.generate(ip, actualProgression,
				progressionDurations, melodyNotePatternMap, sec, secOrder, variations, measures, timing);
		Phrase phr = result.getPhrase();
		Mod.transpose(phr, DEFAULT_INSTRUMENT_TRANSPOSE[2]);

		if (!overwriteWithCustomSectionMidi(sec, phr, ip)) {
			MidiUtils.addChordsToPhrase(phr, result.getChords(), result.getFlamming());
			addPhraseNotesToSection(sec, ip, phr.getNoteList());
		}

		if (result.shouldStoreVariations()) {
			sec.setVariation(2, getAbsoluteOrder(ip), result.getVariations());
		}

		// transpose
		int extraTranspose = gc.getChordGenSettings().isUseTranspose() ? ip.getTranspose() : 0;

		// extraTranspose variation
		List<Integer> vars = sec.getVariation(2, getAbsoluteOrder(ip));
		if (vars != null && vars.contains(0)) {
			extraTranspose += 12;
		}
		ScaleMode scale = (modScale != null) ? modScale : gc.getScaleMode();
		if (scale != ScaleMode.IONIAN) {
			MidiUtils.transposePhrase(phr, ScaleMode.IONIAN.noteAdjustScale, scale.noteAdjustScale,
					gc.isTransposedNotesForceScale());
		}
		Mod.transpose(phr, extraTranspose + modTrans);
		int hits = ip.getHitsPerPattern();
		int swingPercentAmount = (hits % 2 == 0) ? ip.getSwingPercent() : 50;
		swingPhrase(phr, swingPercentAmount, Durations.QUARTER_NOTE);

		MidiGeneratorUtils.processSectionTransition(sec, phr.getNoteList(),
				progressionDurations.stream().mapToDouble(e -> e).sum() * measures, 0.25, 0.15,
				0.9);

		// delay
		phr.setStartTime(START_TIME_DELAY);
		addOffsetsToPhrase(phr, ip);
		return phr;
	}

	private static void addOffsetsToPhrase(Phrase phr, InstPart ip) {
		if (ip.getOffset() != 0) {
			double offsetDelay = (noteMultiplier * ip.getOffset()) / 1000.0;
			for (Object no : phr.getNoteList()) {
				Note n = (Note) no;
				n.setOffset(n.getOffset() + offsetDelay);
			}
		}
		if (ip.getFeedbackCount() > 0) {
			MidiGeneratorUtils.multiDelayPhrase(phr, ip.getFeedbackCount(),
					ip.getFeedbackDuration() / 1000.0, ip.getFeedbackVol() / 100.0);
		}
	}

	public Phrase fillArpFromPart(ArpPart ip, List<int[]> actualProgression, Section sec,
			List<Integer> variations) {
		LG.d("Processing: " + ip.partInfo());
		int measures = sec.getMeasures();
		InstPhraseGenerator.Timing timing = getInstrumentPhraseTiming();
		ArpPhraseGenerator.ArpResult result = arpPhraseGenerator.generate(ip, actualProgression,
				rootProgression, progressionDurations, melodyNotePattern, melodyNotePatternMap,
				chordInts.size(), sec, variations, secOrder, timing);
		Phrase phr = result.getPhrase();
		Mod.transpose(phr, DEFAULT_INSTRUMENT_TRANSPOSE[3]);

		if (!overwriteWithCustomSectionMidi(sec, phr, ip)) {
			addPhraseNotesToSection(sec, ip, phr.getNoteList());
		}
		if (result.shouldStoreVariations()) {
			sec.setVariation(3, getAbsoluteOrder(ip), result.getVariations());
		}

		int extraTranspose = ip.getTranspose();
		List<Integer> vars = sec.getVariation(3, getAbsoluteOrder(ip));
		if (vars != null && vars.contains(0)) {
			extraTranspose += 12;
		}
		ScaleMode scale = (modScale != null) ? modScale : gc.getScaleMode();
		if (scale != ScaleMode.IONIAN) {
			MidiUtils.transposePhrase(phr, ScaleMode.IONIAN.noteAdjustScale, scale.noteAdjustScale,
					gc.isTransposedNotesForceScale());
		}
		Mod.transpose(phr, extraTranspose + modTrans);
		MidiGeneratorUtils.applyNoteLengthMultiplier(phr.getNoteList(),
				ip.getNoteLengthMultiplier());
		MidiGeneratorUtils.processSectionTransition(sec, phr.getNoteList(),
				progressionDurations.stream().mapToDouble(e -> e).sum() * measures, 0.25, 0.15,
				0.9);

		int hits = ip.getHitsPerPattern();
		int swingPercentAmount = (hits % 2 == 0) ? ip.getSwingPercent() : 50;
		swingPhrase(phr, swingPercentAmount, Durations.QUARTER_NOTE);
		if (result.fillLastBeat) {
			Mod.crescendo(phr, phr.getEndTime() * 3 / 4, phr.getEndTime(),
					Math.max(result.minVelocity, 55), Math.max(result.maxVelocity, 110));
		}
		ip.setPatternShift(result.originalPartState.getPatternShift());
		ip.setChordSpan(result.originalPartState.getChordSpan());
		ip.setHitsPerPattern(result.originalPartState.getHitsPerPattern());
		ip.setPatternRepeat(result.originalPartState.getPatternRepeat());
		phr.setStartTime(START_TIME_DELAY);
		addOffsetsToPhrase(phr, ip);
		return phr;
	}

	private static final class ScoreParts {
		final List<PartExt> melody;
		final List<PartExt> melodyFull;
		final List<PartExt> chords;
		final List<PartExt> arps;
		final List<PartExt> bass;
		final List<PartExt> drums;
		final List<PartExt> drumsFull;

		private ScoreParts(List<PartExt> melody, List<PartExt> melodyFull,
				List<PartExt> chords, List<PartExt> arps, List<PartExt> bass,
				List<PartExt> drums, List<PartExt> drumsFull) {
			this.melody = melody;
			this.melodyFull = melodyFull;
			this.chords = chords;
			this.arps = arps;
			this.bass = bass;
			this.drums = drums;
			this.drumsFull = drumsFull;
		}
	}
	public Phrase fillDrumsFromPart(DrumPart ip, List<int[]> actualProgression,
			boolean sectionForcedDynamics, Section sec, List<Integer> variations) {
		LG.d("Processing: " + ip.partInfo());
		int measures = sec.getMeasures();
		DrumPart dpClone = (DrumPart) ip.clone();
		int swingPercentAmount = (ip.getHitsPerPattern() % 2 == 0) ? ip.getSwingPercent() : 50;
		InstPhraseGenerator.Timing timing = getInstrumentPhraseTiming();
		DrumPhraseGenerator.DrumResult result = drumPhraseGenerator.generate(ip, actualProgression,
				progressionDurations, chordInts, melodyNotePattern, melodyNotePatternMap,
				sectionForcedDynamics, sec, measures, variations, secOrder, timing);
		Phrase phr = result.getPhrase();

		if (result.isPatternMissingForInstrument()) {
			phr.setStartTime(START_TIME_DELAY);
			addOffsetsToPhrase(phr, ip);
			return phr;
		}
		if (result.shouldStoreVariations()) {
			sec.setVariation(4, getAbsoluteOrder(ip), result.getVariations());
		}

		if (!overwriteWithCustomSectionMidi(sec, phr, ip)) {
			addPhraseNotesToSection(sec, ip, phr.getNoteList());
		}
		if (gc.isDrumCustomMapping()) {
			for (Object o : phr.getNoteList()) {
				Note n = (Note) o;
				int pitch = n.getPitch();
				if (pitch >= 0) {
					pitch = mapDrumPitchByCustomMapping(n.getPitch(), true);
					n.setPitch(pitch);
				}
			}
		}

		MidiGeneratorUtils.processSectionTransition(sec, phr.getNoteList(),
				progressionDurations.stream().mapToDouble(e -> e).sum() * measures, 0.25, 0.15,
				0.9);

		swingPhrase(phr, swingPercentAmount, Durations.QUARTER_NOTE);
		phr.setStartTime(START_TIME_DELAY);
		addOffsetsToPhrase(phr, ip);
		ip.setHitsPerPattern(dpClone.getHitsPerPattern());
		ip.setPatternShift(dpClone.getPatternShift());
		ip.setChordSpan(dpClone.getChordSpan());
		return phr;
	}


	private boolean overwriteWithCustomSectionMidi(Section sec, Phrase phr, InstPart ip) {
		UsedPattern pat = sec.getPattern(ip.getPartNum(), ip.getOrder());
		if (pat != null && UsedPattern.NONE.equalsIgnoreCase(pat.getName())) {
			LG.i("Forced generation, pattern none!");
			return false;
		}

		PhraseNotes pn = gc.getPattern(pat);
		UsedPattern sectionTypePat = new UsedPattern(ip.getPartNum(), ip.getOrder(),
				sec.getPatternType());
		if (pn == null || !pn.isApplied()) {
			//LG.i("Pattern 1 is null: " + (pn == null));
			pat = sectionTypePat;
			pn = gc.getPattern(pat);
			//LG.i("Pattern 2 is null: " + (pn == null));
			if (pn != null && !pn.isApplied()) {
				pn = null;
			} else {
				sec.putPattern(ip.getPartNum(), ip.getOrder(), pat);
			}
		}

		if (pn != null) {
			LG.d("Loaded pattern for: " + ip.partInfo() + ", pattern: " + pat.toString());
			Phrase customPhr = pn.makePhrase();
			MidiUtils.scalePhrase(customPhr,
					progressionDurations.stream().mapToDouble(e -> e).sum() * sec.getMeasures());
			phr.setNoteList(customPhr.getNoteList());

			// also check if section pattern exists in GC..
			PhraseNotes oldPn = gc.getPattern(sectionTypePat);
			if (oldPn != null && oldPn.isApplied()) {
				LG.d("Skipping section pattern for: " + ip.partInfo());
			} else {
				oldPn = pn.copy();
				oldPn.setApplied(false);
				gc.putPattern(sectionTypePat, oldPn);
			}
			return true;
		} else {
			//LG.d("Not overwritten with MIDI: " + ip.getPartNum() + ", " + ip.getAbsoluteOrder());
			return false;
		}

	}

	private void addPhraseNotesToSection(Section sec, InstPart ip, List<Note> noteList) {
		PhraseNotes pn = new PhraseNotes(noteList);
		pn.setPartOrder(ip.getOrder());
		pn.setApplied(false);

		//LG.i("Adding generated/section pattern to GC!");
		UsedPattern pat = UsedPattern.generated(ip, pn);
		gc.putPattern(pat, pn);
		UsedPattern sectionTypePat = new UsedPattern(ip.getPartNum(), ip.getOrder(),
				sec.getPatternType());
		PhraseNotes oldPn = gc.getPattern(sectionTypePat);
		if (oldPn != null && oldPn.isApplied()) {
			LG.i("Skipping section pattern for: " + ip.partInfo());
		} else {
			gc.putPattern(sectionTypePat, pn);
		}
		sec.putPattern(ip.getPartNum(), ip.getOrder(), pat);
	}

	public static List<Integer> fillVariations(Section sec, InstPart instPart, List<Integer> variations,
			int part) {
		return fillVariations(sec, instPart, variations, part, new ArrayList<>());
	}

	public static List<Integer> fillVariations(Section sec, InstPart instPart, List<Integer> variations,
			int part, List<Double> chanceMultipliers) {
		if (variations != null) {
			return variations;
		}
		if (chanceMultipliers == null) {
			chanceMultipliers = new ArrayList<>();
		}
		Random varGenerator = new Random(gc.getArrangement().getSeed() + instPart.getOrderOffset()
				+ sec.getTypeSeedOffset() + part * 1000);

		int numVars = Section.variationDescriptions[part].length - 2;
		//LG.d("Chance: " + gc.getArrangementPartVariationChance());
		int modifiedChance = OMNI.clampChance(
				gc.getArrangementPartVariationChance() * sec.getChanceForInst(part) / 50);
		modifiedChance += (gc.getArrangementPartVariationChance() - modifiedChance) / 2;
		/*LG.d(
				"Modified: " + modifiedChance + ", for inst: " + sec.getChanceForInst(part));*/

		for (int i = 0; i < numVars; i++) {
			int chance = (chanceMultipliers.size() > i)
					? OMNI.clampChance((int) (modifiedChance * chanceMultipliers.get(i)))
					: modifiedChance;
			if (varGenerator.nextInt(100) >= chance) {
				continue;
			}

			if (!gc.getArrangement().isGlobalVariation(part, i)) {
				continue;
			}

			if (variations == null) {
				variations = new ArrayList<>();
			}

			if (!variations.contains(i) && variations.size() < numVars) {
				variations.add(i);
			}
		}
		/*LG.d("Generated variations for part: " + part + ", size: "
				+ (variations != null ? variations.size() : "null"));*/
		return variations;
	}

	public static int mapDrumPitchByCustomMapping(int pitch, boolean cached) {
		if (cached && customDrumMappingNumbers != null) {
			Integer mapped = customDrumMappingNumbers.get(pitch);
			if (mapped == null) {
				throw new IllegalArgumentException(
						"Pitch not found in custom drum mapping: " + pitch);
			}
			return mapped;
		}
		List<Integer> customMappingNumbers = null;
		if (gc != null) {
			String customMapping = gc.getDrumCustomMappingNumbers();
			customMappingNumbers = OMNI.parseIntsString(customMapping);
		} else {
			customMappingNumbers = Arrays.asList(InstUtils.DRUM_INST_NUMBERS_SEMI);
		}

		List<Integer> defaultMappingNumbers = InstUtils.getInstNumbers(InstUtils.DRUM_INST_NAMES);
		int defaultIndex = defaultMappingNumbers.indexOf(pitch);
		if (defaultIndex < 0) {
			throw new IllegalArgumentException("Pitch not found in default drum mapping: " + pitch);
		} else if (defaultMappingNumbers.size() != customMappingNumbers.size()) {
			throw new IllegalArgumentException("Custom mapping has incorrect number of elements!");
		}
		if (cached) {
			customDrumMappingNumbers = new HashMap<>();
			for (int i = 0; i < defaultMappingNumbers.size(); i++) {
				customDrumMappingNumbers.put(defaultMappingNumbers.get(i),
						customMappingNumbers.get(i));
			}
		}

		return customMappingNumbers.get(defaultIndex);
	}

	public Phrase fillChordSlash(List<int[]> actualProgression, int measures) {
		Phrase chordSlashPhrase = new PhraseExt(2, 1, secOrder);
		Random chordSlashGenerator = new Random(gc.getRandomSeed() + 2);
		for (int i = 0; i < measures; i++) {
			// fill slash chord slashes
			for (int j = 0; j < actualProgression.size(); j++) {
				boolean isChordSlash = chordSlashGenerator.nextInt(100) < gc.getChordSlashChance();

				if (isChordSlash) {
					int[] actualChord = actualProgression.get(j);
					int semitone = actualChord[chordSlashGenerator.nextInt(actualChord.length)];
					int lowestSemitone = actualChord[0];
					int targetOctave = (lowestSemitone / 12) - 1;
					int targetSemitone = targetOctave * 12 + semitone % 12;
					chordSlashPhrase.addChord(new int[] { targetSemitone },
							progressionDurations.get(j));
				} else {
					chordSlashPhrase.addChord(new int[] { Pitches.REST },
							progressionDurations.get(j));
				}
			}
		}
		int extraTranspose = 0;
		ScaleMode scale = (modScale != null) ? modScale : gc.getScaleMode();
		if (scale != ScaleMode.IONIAN) {
			MidiUtils.transposePhrase(chordSlashPhrase, ScaleMode.IONIAN.noteAdjustScale,
					scale.noteAdjustScale, gc.isTransposedNotesForceScale());
		}
		Mod.transpose(chordSlashPhrase, -12 + extraTranspose + modTrans);

		// delay
		chordSlashPhrase.setStartTime(START_TIME_DELAY);
		return chordSlashPhrase;


	}

	public static List<Integer> makeRandomArpPattern(int hits, boolean repeatableNotes,
			Random uiGenerator2arpPattern) {
		return MidiGeneratorUtils.makeRandomArpPattern(hits, repeatableNotes, uiGenerator2arpPattern);
	}
}
