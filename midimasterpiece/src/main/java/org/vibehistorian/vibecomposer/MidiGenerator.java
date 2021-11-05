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

package org.vibehistorian.vibecomposer;

import static org.vibehistorian.vibecomposer.MidiUtils.applyChordFreqMap;
import static org.vibehistorian.vibecomposer.MidiUtils.cIonianScale4;
import static org.vibehistorian.vibecomposer.MidiUtils.convertChordToLength;
import static org.vibehistorian.vibecomposer.MidiUtils.cpRulesMap;
import static org.vibehistorian.vibecomposer.MidiUtils.getBasicChordsFromRoots;
import static org.vibehistorian.vibecomposer.MidiUtils.maX;
import static org.vibehistorian.vibecomposer.MidiUtils.mappedChord;
import static org.vibehistorian.vibecomposer.MidiUtils.pickDurationWeightedRandom;
import static org.vibehistorian.vibecomposer.MidiUtils.squishChordProgression;
import static org.vibehistorian.vibecomposer.MidiUtils.transposeScale;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.Section.SectionType;
import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.Enums.KeyChangeType;
import org.vibehistorian.vibecomposer.Enums.PatternJoinMode;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Helpers.OMNI;
import org.vibehistorian.vibecomposer.Helpers.PartExt;
import org.vibehistorian.vibecomposer.Panels.ArpGenSettings;
import org.vibehistorian.vibecomposer.Panels.DrumGenSettings;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.BassPart;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Popups.VariationPopup;

import jm.JMC;
import jm.music.data.Note;
import jm.music.data.Part;
import jm.music.data.Phrase;
import jm.music.data.Score;
import jm.music.tools.Mod;
import jm.util.Write;

public class MidiGenerator implements JMC {

	public static final double DBL_ERR = 0.01;
	public static final double FILLER_NOTE_MIN_DURATION = 0.05;
	public static final double DEFAULT_DURATION_MULTIPLIER = 0.95;

	public enum ShowScoreMode {
		NODRUMSCHORDS, DRUMSONLY, CHORDSONLY, ALL;
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(MidiGenerator.class);

	public static double noteMultiplier = 1.0;

	public static class Durations {

		public static double SIXTEENTH_NOTE = 0.25 * noteMultiplier;
		public static double DOTTED_SIXTEENTH_NOTE = 0.375 * noteMultiplier;
		public static double EIGHTH_NOTE = 0.5 * noteMultiplier;
		public static double DOTTED_EIGHTH_NOTE = 0.75 * noteMultiplier;
		public static double QUARTER_NOTE = 1.0 * noteMultiplier;
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
		Durations.QUARTER_NOTE = 1.0 * noteMultiplier;
		Durations.DOTTED_QUARTER_NOTE = 1.5 * noteMultiplier;
		Durations.HALF_NOTE = 2.0 * noteMultiplier;
		Durations.DOTTED_HALF_NOTE = 3.0 * noteMultiplier;
		Durations.WHOLE_NOTE = 4.0 * noteMultiplier;
		Durations.DOTTED_WHOLE_NOTE = 6.0 * noteMultiplier;

		START_TIME_DELAY = Durations.QUARTER_NOTE;
		MELODY_DUR_ARRAY = new double[] { Durations.HALF_NOTE, Durations.DOTTED_QUARTER_NOTE,
				Durations.QUARTER_NOTE, Durations.EIGHTH_NOTE };
		CHORD_DUR_ARRAY = new double[] { Durations.WHOLE_NOTE * 2, Durations.DOTTED_HALF_NOTE * 2,
				Durations.WHOLE_NOTE, Durations.HALF_NOTE };
	}

	private static final boolean debugEnabled = true;
	private static final PrintStream originalStream = System.out;

	// big G
	public static GUIConfig gc;

	// last scores saved
	public static List<Score> LAST_SCORES = new ArrayList<>();
	public static final int LAST_SCORES_LIMIT = 5;

	// track map for Solo
	public static List<InstPart> trackList = new ArrayList<>();

	// constants
	public static final boolean MAXIMIZE_CHORUS_MAIN_MELODY = false;
	public static final int MELODY_PATTERN_RESOLUTION = 16;

	public static final int MAXIMUM_PATTERN_LENGTH = 8;
	public static final int OPENHAT_CHANCE = 0;
	private static final int maxAllowedScaleNotes = 7;
	private static final int BASE_ACCENT = 15;
	public static double START_TIME_DELAY = Durations.QUARTER_NOTE;
	private static final double DEFAULT_CHORD_SPLIT = 625;
	private static final String ARP_PATTERN_KEY = "ARP_PATTERN";
	private static final String ARP_OCTAVE_KEY = "ARP_OCTAVE";
	private static final String ARP_PAUSES_KEY = "ARP_PAUSES";

	// visibles/settables
	public static DrumGenSettings DRUM_SETTINGS = new DrumGenSettings();
	public static ArpGenSettings ARP_SETTINGS = new ArpGenSettings();

	public static List<String> userChords = new ArrayList<>();
	public static List<Double> userChordsDurations = new ArrayList<>();
	public static Phrase userMelody = null;
	public static List<String> chordInts = new ArrayList<>();
	public static double GENERATED_MEASURE_LENGTH = 0;

	public static String FIRST_CHORD = null;
	public static String LAST_CHORD = null;

	public static boolean COLLAPSE_DRUM_TRACKS = true;
	public static boolean COLLAPSE_MELODY_TRACKS = true;
	public static boolean RANDOMIZE_TARGET_NOTES = false;

	public static List<Integer> TARGET_NOTES = null;


	// for internal use only
	private static double[] MELODY_DUR_ARRAY = { Durations.HALF_NOTE, Durations.DOTTED_QUARTER_NOTE,
			Durations.QUARTER_NOTE, Durations.EIGHTH_NOTE };
	private double[] MELODY_DUR_CHANCE = { 0.3, 0.6, 1.0, 1.0 };

	private static double[] CHORD_DUR_ARRAY = { Durations.WHOLE_NOTE, Durations.HALF_NOTE };
	private double[] CHORD_DUR_CHANCE = { 0.0, 0.20, 0.80, 1.0 };
	private static Map<Integer, Integer> customDrumMappingNumbers = null;

	private List<Integer> MELODY_SCALE = cIonianScale4;
	private List<Double> progressionDurations = new ArrayList<>();
	private List<int[]> chordProgression = new ArrayList<>();
	private List<int[]> rootProgression = new ArrayList<>();

	private int melodyResForChord(int chordIndex) {
		return (int) (Math.round(MELODY_PATTERN_RESOLUTION * progressionDurations.get(chordIndex)
				/ Durations.WHOLE_NOTE));
	}

	public static Map<Integer, List<Note>> userMelodyMap = new HashMap<>();
	private Map<Integer, List<Note>> chordMelodyMap1 = new HashMap<>();
	private List<int[]> melodyBasedChordProgression = new ArrayList<>();
	private List<int[]> melodyBasedRootProgression = new ArrayList<>();

	// global parts
	private List<BassPart> bassParts = null;
	private List<MelodyPart> melodyParts = null;
	private List<ChordPart> chordParts = null;
	private List<DrumPart> drumParts = null;
	private List<ArpPart> arpParts = null;

	public List<? extends InstPart> getInstPartList(int order) {
		if (order < 0 || order > 4) {
			throw new IllegalArgumentException("Inst part list order wrong.");
		}
		switch (order) {
		case 0:
			return melodyParts;
		case 1:
			return bassParts;
		case 2:
			return chordParts;
		case 3:
			return arpParts;
		case 4:
			return drumParts;
		}
		return null;
	}

	public static List<Integer> melodyNotePattern = null;
	public static Map<Integer, List<Integer>> melodyNotePatternMap = null;

	private int samePitchCount = 0;
	private int previousPitch = 0;

	private int modTrans = 0;

	public MidiGenerator(GUIConfig gc) {
		MidiGenerator.gc = gc;
	}

	private Vector<Note> generateMelodyBlockSkeletonFromChords(MelodyPart mp, List<int[]> chords,
			List<int[]> roots, int measures, int notesSeedOffset, Section sec,
			List<Integer> variations) {

		boolean genVars = variations == null;

		boolean fillChordMelodyMap = false;
		if (chordMelodyMap1.isEmpty() && notesSeedOffset == 0
				&& (roots.size() == chordInts.size())) {
			fillChordMelodyMap = true;
		}

		if (sec.getRiskyVariations() != null && sec.isRiskyVar(2)) {
			notesSeedOffset += 100;
		}

		int MAX_JUMP_SKELETON_CHORD = mp.getBlockJump();
		int SAME_RHYTHM_CHANCE = mp.getDoubledRhythmChance();
		int EXCEPTION_CHANCE = mp.getNoteExceptionChance();
		int CHORD_STRETCH = 4;
		int BLOCK_TARGET_MODE = gc.getMelodyBlockTargetMode();

		int seed = mp.getPatternSeed();
		LOGGER.info("Seed: " + seed);


		// A B A C pattern
		List<Integer> blockSeedOffsets = (mp.getMelodyPatternOffsets() != null)
				? mp.getMelodyPatternOffsets()
				: new ArrayList<>(Arrays.asList(new Integer[] { 0, 1, 0, 2 }));

		while (blockSeedOffsets.size() < chords.size()) {
			blockSeedOffsets.addAll(blockSeedOffsets);
		}

		Map<Integer, Pair<Pair<List<Integer>, Integer>, List<MelodyBlock>>> changesAndBlocksMap = new HashMap<>();
		Map<Integer, List<Double>> blockDurationsMap = new HashMap<>();


		// Chord note choices
		List<Integer> blockChordNoteChoices = (mp.getChordNoteChoices() != null)
				? mp.getChordNoteChoices()
				: new ArrayList<>(Arrays.asList(new Integer[] { 0, 2, 2, 4 }));
		while (chords.size() > blockChordNoteChoices.size()) {
			blockChordNoteChoices.addAll(blockChordNoteChoices);
		}
		if (RANDOMIZE_TARGET_NOTES) {
			if (TARGET_NOTES == null) {
				TARGET_NOTES = new ArrayList<>(
						generateOffsets(roots, seed, gc.getMelodyBlockTargetMode()));
			}
			blockChordNoteChoices = TARGET_NOTES;
		}
		TARGET_NOTES = blockChordNoteChoices;
		LOGGER.debug("Choices: " + blockChordNoteChoices);

		Vector<Note> noteList = new Vector<>();

		// if notes seed offset > 0, add it only to one of: rhythms, pitches
		//Random nonMainMelodyGenerator = new Random(seed + 30);
		int pitchPickerOffset = notesSeedOffset;
		int rhythmOffset = notesSeedOffset;

		int melodyBlockGeneratorSeed = seed + notesSeedOffset;

		int firstBlockOffset = blockSeedOffsets.get(0);
		Random pitchPickerGenerator = new Random(seed + pitchPickerOffset + firstBlockOffset);
		Random exceptionGenerator = new Random(seed + 2 + notesSeedOffset + firstBlockOffset);
		Random sameRhythmGenerator = new Random(seed + 3 + firstBlockOffset);
		Random alternateRhythmGenerator = new Random(seed + 4);
		Random durationGenerator = new Random(seed + notesSeedOffset + 5);
		//Random surpriseGenerator = new Random(seed + notesSeedOffset + 15);

		double[] melodySkeletonDurations = { Durations.QUARTER_NOTE, Durations.HALF_NOTE,
				Durations.DOTTED_HALF_NOTE, Durations.WHOLE_NOTE };

		List<int[]> usedChords = null;
		if (gc.isMelodyBasicChordsOnly()) {
			List<int[]> basicChordsUnsquished = getBasicChordsFromRoots(roots);
			for (int i = 0; i < chords.size(); i++) {
				basicChordsUnsquished.set(i,
						convertChordToLength(basicChordsUnsquished.get(i), chords.get(i).length));
			}
			usedChords = basicChordsUnsquished;
		} else {
			usedChords = chords;
		}

		List<int[]> stretchedChords = usedChords.stream()
				.map(e -> convertChordToLength(e, CHORD_STRETCH)).collect(Collectors.toList());
		//LOGGER.debug("Alt: " + alternateRhythm);
		int maxBlockChangeAdjustment = 0;

		for (int o = 0; o < measures; o++) {

			for (int chordIndex = 0; chordIndex < stretchedChords.size(); chordIndex++) {
				// either after first measure, or after first half of combined chord prog

				if (genVars && (chordIndex == 0)) {
					variations = fillVariations(sec, mp, variations, 0);
					// never generate MaxJump for important melodies
					if ((variations != null) && sec.getTypeMelodyOffset() == 0) {
						variations.removeIf(e -> e == 1);
					}
				}

				if ((variations != null) && (chordIndex == 0)) {
					for (Integer var : variations) {
						if (o == measures - 1) {
							LOGGER.debug("Melody variation: " + var);
						}

						switch (var) {
						case 0:
							// only add, processed later
							break;
						case 1:
							maxBlockChangeAdjustment++;
							break;
						default:
							throw new IllegalArgumentException("Too much variation!");
						}
					}
				}

				int blockOffset = blockSeedOffsets.get(chordIndex % blockSeedOffsets.size());

				if (fillChordMelodyMap && o == 0) {
					if (!chordMelodyMap1.containsKey(Integer.valueOf(chordIndex))) {
						chordMelodyMap1.put(Integer.valueOf(chordIndex), new ArrayList<>());
					}
				}
				pitchPickerGenerator.setSeed(seed + pitchPickerOffset + blockOffset);
				exceptionGenerator.setSeed(seed + 2 + notesSeedOffset + blockOffset);
				sameRhythmGenerator.setSeed(seed + 3 + blockOffset);

				List<Double> durations = (gc.getMelodyPatternEffect() != 1)
						? blockDurationsMap.get(blockOffset)
						: null;
				boolean badDuration = false;
				if (durations != null && !roughlyEqual(durations.stream().mapToDouble(e -> e).sum(),
						progressionDurations.get(chordIndex))) {
					durations = null;
					badDuration = true;
				}
				boolean sameRhythmTwice = sameRhythmGenerator.nextInt(100) < SAME_RHYTHM_CHANCE;
				if (durations == null) {
					double rhythmDuration = sameRhythmTwice
							? progressionDurations.get(chordIndex) / 2.0
							: progressionDurations.get(chordIndex);
					int rhythmSeed = seed + blockOffset + rhythmOffset;

					int speed = adjustChanceParamForTransition(mp.getSpeed(), sec, chordIndex,
							chords.size(), 40, 0.25, false);
					int addQuick = (speed - 50) * 4;
					int addSlow = addQuick * -1;

					int[] melodySkeletonDurationWeights = Rhythm
							.normalizedCumulativeWeights(new int[] { 200 + addQuick,
									200 + addQuick / 2, 200 + addQuick, 200 + addSlow });


					Rhythm rhythm = new Rhythm(rhythmSeed, rhythmDuration, melodySkeletonDurations,
							melodySkeletonDurationWeights);

					durations = rhythm.regenerateDurations(10, melodySkeletonDurations[0]);
					if (sameRhythmTwice) {
						durations.addAll(durations);
					}
					blockDurationsMap.put(blockOffset, durations);
				}
				LOGGER.debug("Overall Block Durations: " + StringUtils.join(durations, ",")
						+ ", Doubled rhythm: " + sameRhythmTwice);
				int chord1 = getStartingNote(stretchedChords, blockChordNoteChoices, chordIndex,
						BLOCK_TARGET_MODE);
				int chord2 = getStartingNote(stretchedChords, blockChordNoteChoices, chordIndex + 1,
						BLOCK_TARGET_MODE);
				int startingOct = chord1 / 7;

				Pair<Pair<List<Integer>, Integer>, List<MelodyBlock>> existingPattern = (badDuration)
						? null
						: changesAndBlocksMap.get(blockOffset);

				int remainingDirChanges = gc.getMelodyMaxDirChanges();
				Pair<List<Integer>, Integer> blockChangesPair = (existingPattern != null
						&& gc.getMelodyPatternEffect() > 0)
								? existingPattern.getLeft()
								: MelodyUtils.blockChangeSequence(chord1, chord2,
										melodyBlockGeneratorSeed, durations.size(),
										OMNI.clamp(
												mp.getMaxBlockChange() + maxBlockChangeAdjustment,
												0, 7),
										remainingDirChanges);
				remainingDirChanges -= blockChangesPair.getRight();
				List<Integer> blockChanges = blockChangesPair.getLeft();
				LOGGER.debug("Block changes: " + blockChanges);
				int startingNote = chord1 % 7;

				List<Integer> forcedLengths = (existingPattern != null
						&& gc.getMelodyPatternEffect() == 0)
								? existingPattern.getRight().stream().map(e -> e.durations.size())
										.collect(Collectors.toList())
								: null;

				List<MelodyBlock> melodyBlocks = (existingPattern != null
						&& gc.getMelodyPatternEffect() > 0)
								? existingPattern.getRight()
								: generateMelodyBlocksForDurations(mp, sec, durations, roots,
										melodyBlockGeneratorSeed + blockOffset, blockChanges,
										MAX_JUMP_SKELETON_CHORD, startingNote, chordIndex,
										forcedLengths, remainingDirChanges);
				//LOGGER.debug("Starting note: " + startingNote);

				if (existingPattern == null) {
					LOGGER.debug("Stored pattern: " + blockOffset + ", for chord index:"
							+ chordIndex + ", Pattern effect: " + gc.getMelodyPatternEffect());
					changesAndBlocksMap.put(blockOffset, Pair.of(blockChangesPair, melodyBlocks));
				} else {
					LOGGER.debug("Loaded pattern: " + blockOffset + ", for chord index:"
							+ chordIndex + ", Pattern effect: " + gc.getMelodyPatternEffect());
				}

				int adjustment = 0;
				int exceptionCounter = mp.getMaxNoteExceptions();
				for (int blockIndex = 0; blockIndex < melodyBlocks.size(); blockIndex++) {
					MelodyBlock mb = melodyBlocks.get(blockIndex);
					List<Integer> pitches = new ArrayList<>();
					if (blockIndex > 0) {
						adjustment += blockChanges.get(blockIndex - 1);
					}
					//LOGGER.debug("Adjustment: " + adjustment);
					for (int k = 0; k < mb.durations.size(); k++) {
						int note = mb.notes.get(k);
						int pitch = startingOct * 12;
						int combinedNote = startingNote + note;
						//LOGGER.debug("1st combined: " + combinedNote);
						Pair<Integer, Integer> notePitch = normalizeNotePitch(combinedNote, pitch);
						combinedNote = notePitch.getLeft();
						pitch = notePitch.getRight();
						if (adjustment != 0) {
							combinedNote = combinedNote + adjustment;
							notePitch = normalizeNotePitch(combinedNote, pitch);
							combinedNote = notePitch.getLeft();
							pitch = notePitch.getRight();
						}

						pitch += MidiUtils.MAJ_SCALE.get(combinedNote);
						//LOGGER.debug("Combined note: " + combinedNote + ", pitch: " + pitch);
						pitches.add(pitch);

					}

					List<Double> sortedDurs = new ArrayList<>(mb.durations);
					if (existingPattern == null) {
						if (gc.isMelodyEmphasizeKey()) {
							// re-order durations to make most relevant notes the longest
							for (int k = 0; k < mb.durations.size(); k++) {
								for (int l = 0; l < mb.durations.size(); l++) {
									if (!pitches.get(k).equals(pitches.get(l))) {
										boolean swap = false;
										if (MidiUtils.relevancyOrder.indexOf(
												pitches.get(k) % 12) < MidiUtils.relevancyOrder
														.indexOf(pitches.get(l) % 12)) {
											swap = sortedDurs.get(k) + DBL_ERR < sortedDurs.get(l);
										} else {
											swap = sortedDurs.get(k) - DBL_ERR > sortedDurs.get(l);
										}
										if (swap) {
											double temp = sortedDurs.get(k);
											sortedDurs.set(k, sortedDurs.get(l));
											sortedDurs.set(l, temp);
										}
									}
								}
							}
							// if rhythm or rhythm+notes
							if (gc.getMelodyPatternEffect() != 1) {
								mb.durations = sortedDurs;
							}
						}
					} else if (gc.getMelodyPatternEffect() == 0) {
						// rhythm only - get from stored melodyblock
						sortedDurs = new ArrayList<>(
								existingPattern.getRight().get(blockIndex).durations);
					}


					//LOGGER.debug(StringUtils.join(mb.durations, ","));
					//LOGGER.debug("After: " + StringUtils.join(sortedDurs, ","));
					for (int k = 0; k < mb.durations.size(); k++) {
						int pitch = pitches.get(k);
						// single note exc. = last note in chord
						// other exc. = any note first note in block
						boolean exceptionIndexValid = (gc.isMelodySingleNoteExceptions())
								? (k == mb.durations.size() - 1
										&& blockIndex == melodyBlocks.size() - 1)
								: (k > 0);
						if (exceptionIndexValid && exceptionCounter > 0
								&& exceptionGenerator.nextInt(100) < EXCEPTION_CHANCE) {
							int upDown = exceptionGenerator.nextBoolean() ? 1 : -1;
							int excPitch = MidiUtils.MAJ_SCALE.get(exceptionGenerator
									.nextInt(gc.isMelodySingleNoteExceptions() ? 7 : 4));
							pitch += upDown * excPitch;
							int closestPitch = MidiUtils.getClosestFromList(MidiUtils.MAJ_SCALE,
									pitch % 12);
							pitch -= pitch % 12;
							pitch += closestPitch;
							exceptionCounter--;
						}

						double swingDuration = sortedDurs.get(k);
						Note n = new Note(pitch, swingDuration, 100);
						n.setDuration(swingDuration * (0.75 + durationGenerator.nextDouble() / 4)
								* DEFAULT_DURATION_MULTIPLIER);


						noteList.add(n);
						if (fillChordMelodyMap && o == 0) {
							chordMelodyMap1.get(Integer.valueOf(chordIndex)).add(n);
						}

					}
				}

			}
		}

		if (fillChordMelodyMap) {
			List<String> chordStrings = getChordsFromMelodyPitches(2, chordMelodyMap1,
					MidiUtils.baseFreqMap);
			populateMelodyBasedProgression(chordStrings, 1, chordMelodyMap1.keySet().size() - 1);
		}
		if (genVars && variations != null) {
			sec.setVariation(0, getAbsoluteOrder(0, mp), variations);
		}
		return noteList;
	}

	public static int adjustChanceParamForTransition(int param, Section sec, int chordNum,
			int chordSize, int maxEffect, double affectedMeasure, boolean reverseEffect) {
		if (chordSize < 2 || !sec.isTransition()) {
			return param;
		}

		int minAffectedChord = OMNI.clamp((int) (affectedMeasure * chordSize) - 1, 1,
				chordSize - 1);
		//LOGGER.debug("Min affected: " + minAffectedChord);
		if (chordNum < minAffectedChord) {
			return param;
		}
		//LOGGER.debug("Old param: " + param);

		int chordRange = chordSize - 1 - minAffectedChord;
		double effect = (chordRange > 0) ? ((chordNum - minAffectedChord) / ((double) chordRange))
				: 1.0;

		int transitionType = sec.getTransitionType();

		int multiplier = reverseEffect ? -1 : 1;
		if (transitionType == 5) {
			param += maxEffect * effect * multiplier;
		} else {
			param -= maxEffect * effect * multiplier;
		}
		param = OMNI.clampChance(param);
		//LOGGER.debug("New param: " + param);
		return param;
	}

	private static List<Integer> generateMelodyOffsetDirectionsFromChordProgression(
			List<int[]> progression, boolean roots, int randomSeed) {
		Random rand = new Random(randomSeed);
		List<Integer> dirs = new ArrayList<>();
		dirs.add(0);
		int last = roots ? progression.get(0)[0]
				: progression.get(0)[rand.nextInt(progression.get(0).length)];
		for (int i = 1; i < progression.size(); i++) {
			int next = roots ? progression.get(i)[0]
					: progression.get(i)[rand.nextInt(progression.get(i).length)];
			dirs.add(Integer.compare(next, last));
			last = next;
		}
		return dirs;
	}

	private static List<Integer> randomizedChordDirections(int chords, int randomSeed) {
		Random rand = new Random(randomSeed);

		List<Integer> chordDirs = new ArrayList<>(MelodyUtils.CHORD_DIRECTIONS
				.get(rand.nextInt(MelodyUtils.CHORD_DIRECTIONS.size())));
		while (chordDirs.size() < chords) {
			chordDirs.addAll(chordDirs);
		}
		chordDirs = chordDirs.subList(0, chords);
		return chordDirs;
		/*
		List<Integer> dirs = new ArrayList<>();
		//dirs.add(0);
		for (int i = 0; i < chords; i++) {
			dirs.add(rand.nextInt(3) - 1);
		}
		return dirs;*/
	}

	private static List<Integer> convertRootsToOffsets(List<Integer> roots, int targetMode) {
		List<Integer> offsets = new ArrayList<>();
		for (int i = 0; i < roots.size(); i++) {
			int value = -1 * roots.get(i);
			if (targetMode == 0) {
				value /= 2;
			}
			offsets.add(value);
		}
		return offsets;
	}

	private static List<Integer> multipliedDirections(List<Integer> directions, int randomSeed) {
		// wiggle random
		int MAX_MULTI = 2;
		Random rand = new Random(randomSeed);
		List<Integer> multiDirs = new ArrayList<>();
		for (Integer o : directions) {
			int multiplied = (rand.nextInt(MAX_MULTI) + 1) * o;
			if (o < 0) {
				// small correction for too low dips
				multiplied++;
			}
			multiDirs.add(multiplied);
		}
		return multiDirs;
	}

	private static List<Integer> getRootIndexes(List<int[]> chords) {
		List<Integer> rootIndexes = new ArrayList<>();
		for (int i = 0; i < chords.size(); i++) {
			int root = chords.get(i)[0];
			int rootIndex = MidiUtils.MAJ_SCALE.indexOf(root % 12);
			if (rootIndex < 0) {
				int closestPitch = MidiUtils.getClosestFromList(MidiUtils.MAJ_SCALE, root % 12);
				rootIndex = MidiUtils.MAJ_SCALE.indexOf(closestPitch % 12);
			}
			rootIndexes.add(rootIndex);
		}
		return rootIndexes;
	}

	private static List<Integer> generateOffsets(List<int[]> chords, int randomSeed,
			int targetMode) {
		List<Integer> chordOffsets = convertRootsToOffsets(getRootIndexes(chords), targetMode);
		List<Integer> multipliedDirections = multipliedDirections(
				gc != null && gc.isMelodyUseDirectionsFromProgression()
						? generateMelodyOffsetDirectionsFromChordProgression(chords, true,
								randomSeed)
						: randomizedChordDirections(chords.size(), randomSeed),
				randomSeed + 1);
		List<Integer> offsets = new ArrayList<>();
		for (int i = 0; i < chordOffsets.size(); i++) {
			/*LOGGER.debug("Chord offset: " + chordOffsets.get(i) + ", multiDir: "
					+ multipliedDirections.get(i));*/
			if (targetMode == 1) {
				offsets.add(chordOffsets.get(i) + multipliedDirections.get(i));
			} else {
				offsets.add(multipliedDirections.get(i));
			}

		}
		if (targetMode >= 1) {
			Map<Integer, List<Integer>> choiceMap = getChordNoteChoicesFromChords(chords);
			for (int i = 0; i < chordOffsets.size(); i++) {
				List<Integer> choices = choiceMap.get(i);
				int offset = (targetMode == 1) ? offsets.get(i) - chordOffsets.get(i)
						: offsets.get(i);
				int chordTargetNote = MidiUtils.getClosestFromList(choices, offset);
				LOGGER.debug("Offset old: " + offset + ", C T NOte: " + chordTargetNote);
				offsets.set(i, (targetMode == 1) ? chordTargetNote + chordOffsets.get(i)
						: chordTargetNote);
			}
			if (targetMode == 2) {
				int last = offsets.get(offsets.size() - 1);
				if (offsets.size() > 3 && (last == offsets.get(offsets.size() - 3))) {
					last += (new Random(randomSeed).nextBoolean() ? 2 : -2);
					offsets.set(offsets.size() - 1,
							MidiUtils.getClosestFromList(choiceMap.get(offsets.size() - 1), last));
					LOGGER.debug("Last offset moved!");
				}
			}
		} else {
			int last = offsets.get(offsets.size() - 1);
			if (offsets.size() > 3 && (last == offsets.get(offsets.size() - 3))) {
				last += (new Random(randomSeed).nextBoolean() ? 1 : -1);
				offsets.set(offsets.size() - 1, last);
			}
		}

		//int min = offsets.stream().min((e1, e2) -> e1.compareTo(e2)).get();
		/*if (min == -1) {
			for (int i = 0; i < offsets.size(); i++) {
				offsets.set(i, offsets.get(i) + 1);
			}
		}*/

		LOGGER.debug("RANDOMIZED OFFSETS");
		return offsets;
	}

	private static Map<Integer, List<Integer>> getChordNoteChoicesFromChords(List<int[]> chords) {
		Map<Integer, List<Integer>> choiceMap = new HashMap<>();
		int counter = 0;
		for (int[] c : chords) {
			List<Integer> choices = new ArrayList<>();
			for (int pitch : c) {
				int index = MidiUtils.MAJ_SCALE.indexOf(pitch % 12);
				if (index >= 0) {
					choices.add(index);
					choices.add(index - 7);
				}
			}
			choiceMap.put(counter++, choices);
		}
		return choiceMap;
	}

	public static List<Integer> generateOffsets(List<String> chordStrings, int randomSeed,
			int targetMode, Boolean isPublic) {
		List<int[]> chords = new ArrayList<>();
		for (int i = 0; i < chordStrings.size(); i++) {
			chords.add(MidiUtils.mappedChord(chordStrings.get(i)));
		}
		return generateOffsets(chords, randomSeed, targetMode);
	}

	private Pair<Integer, Integer> normalizeNotePitch(int startingNote, int startingPitch) {
		if (startingNote >= 7) {
			int divided = startingNote / 7;
			startingPitch += (12 * divided);
			startingNote -= (7 * divided);
		} else if (startingNote < 0) {
			int divided = 1 + ((-1 * (startingNote + 1)) / 7);
			startingPitch -= (12 * divided);
			startingNote += (7 * divided);
		}
		return Pair.of(startingNote, startingPitch);
	}

	private List<MelodyBlock> generateMelodyBlocksForDurations(MelodyPart mp, Section sec,
			List<Double> durations, List<int[]> roots, int melodyBlockGeneratorSeed,
			List<Integer> blockChanges, int maxJump, int startingNote, int chordIndex,
			List<Integer> forcedLengths, int remainingDirChanges) {

		List<MelodyBlock> mbs = new ArrayList<>();

		// TODO: generate some common-sense durations, pick randomly from melody phrases, refinement later
		double[] melodySkeletonDurations = { Durations.SIXTEENTH_NOTE, Durations.EIGHTH_NOTE,
				Durations.DOTTED_EIGHTH_NOTE, Durations.QUARTER_NOTE, Durations.DOTTED_QUARTER_NOTE,
				Durations.HALF_NOTE };


		//LOGGER.debug(StringUtils.join(melodySkeletonDurationWeights, ','));
		Random blockNotesGenerator = new Random(melodyBlockGeneratorSeed);

		int prevBlockType = Integer.MIN_VALUE;
		int adjustment = startingNote;

		int remainingVariance = 4;

		for (int blockIndex = 0; blockIndex < durations.size(); blockIndex++) {
			if (blockIndex > 0) {
				adjustment += blockChanges.get(blockIndex - 1);
			}

			int speed = adjustChanceParamForTransition(mp.getSpeed(), sec, chordIndex, roots.size(),
					40, 0.25, false);
			int addQuick = (speed - 50) * 2;
			int addSlow = addQuick * -1;
			int[] melodySkeletonDurationWeights = Rhythm
					.normalizedCumulativeWeights(new int[] { 100 + addQuick, 300 + addQuick,
							100 + addQuick, 300 + addSlow, 100 + addSlow, 100 + addSlow });

			Rhythm blockRhythm = new Rhythm(melodyBlockGeneratorSeed + blockIndex,
					durations.get(blockIndex), melodySkeletonDurations,
					melodySkeletonDurationWeights);
			//int length = blockNotesGenerator.nextInt(100) < gc.getMelodyQuickness() ? 4 : 3;

			blockNotesGenerator.setSeed(melodyBlockGeneratorSeed + blockIndex);
			Pair<Integer, Integer[]> typeBlock = MelodyUtils.getRandomByApproxBlockChangeAndLength(
					blockChanges.get(blockIndex), maxJump, blockNotesGenerator,
					(forcedLengths != null ? forcedLengths.get(blockIndex) : null),
					remainingVariance, remainingDirChanges);
			Integer[] blockNotesArray = typeBlock.getRight();
			int blockType = typeBlock.getLeft();

			boolean chordyBlockNotMatchingChord = false;
			if (blockType == 3) {
				int blockStart = (adjustment + 70) % 7;
				chordyBlockNotMatchingChord = !MelodyUtils.cMajorSubstituteNotes
						.contains(blockStart);
				if (chordyBlockNotMatchingChord) {
					LOGGER.debug("SWAPPING CHORDY BLOCK, blockStart: " + blockStart);
				}
			}

			// try to find a different type for this block change
			if (blockType == prevBlockType || chordyBlockNotMatchingChord) {
				int length = blockNotesArray.length;
				List<Integer> typesToChoose = new ArrayList<>();
				for (int j = 0; j < MelodyUtils.NUM_LISTS; j++) {
					if (j != blockType && MelodyUtils.AVAILABLE_BLOCK_CHANGES_PER_TYPE.get(j)
							.contains(Math.abs(blockChanges.get(blockIndex)))) {
						typesToChoose.add(j);
					}
				}
				if (typesToChoose.size() > 0) {
					int randomType = typesToChoose
							.get(blockNotesGenerator.nextInt(typesToChoose.size()));
					Integer[] typedBlock = MelodyUtils.getRandomForTypeAndBlockChangeAndLength(
							randomType, blockChanges.get(blockIndex), length, blockNotesGenerator,
							0);
					if (typedBlock != null) {
						blockNotesArray = typedBlock;
						blockType = randomType;
						LOGGER.debug("Found new block!");
					} else {
						LOGGER.debug("Different block not found in other types!");
					}
				} else {
					LOGGER.debug("Other types don't have this block!");
				}


			}
			remainingVariance = Math.max(0,
					remainingVariance - MelodyUtils.variance(blockNotesArray));
			remainingDirChanges = Math.max(0,
					remainingDirChanges - MelodyUtils.interblockDirectionChange(blockNotesArray));
			List<Integer> blockNotes = Arrays.asList(blockNotesArray);
			List<Double> blockDurations = blockRhythm.makeDurations(blockNotes.size(),
					mp.getSpeed() < 20 ? Durations.QUARTER_NOTE : Durations.SIXTEENTH_NOTE);


			if (gc.isMelodyArpySurprises() && (blockNotes.size() == 4)
					&& (mp.getSpeed() < 20 || mp.getSpeed() > 80)) {
				double wrongNoteLow = (mp.getSpeed() < 20) ? Durations.SIXTEENTH_NOTE * 0.99
						: Durations.DOTTED_QUARTER_NOTE * 0.99;
				double wrongNoteHigh = (mp.getSpeed() < 20) ? Durations.SIXTEENTH_NOTE * 1.01
						: Durations.WHOLE_NOTE * 1.01;
				boolean containsWrongNote = blockDurations.stream()
						.anyMatch(e -> (e > wrongNoteLow && e < wrongNoteHigh));
				if (containsWrongNote) {
					double arpyDuration = durations.get(blockIndex) / blockNotes.size();
					for (int j = 0; j < blockDurations.size(); j++) {
						blockDurations.set(j, arpyDuration);
					}
					/*LOGGER.debug("Arpy surprise for block#: " + blockNotes.size()
							+ ", duration: " + durations.get(i));*/
				}

			}

			//LOGGER.debug(StringUtils.join(blockDurations, ","));
			prevBlockType = blockType;
			//LOGGER.debug("Block Durations size: " + blockDurations.size());
			MelodyBlock mb = new MelodyBlock(blockNotes, blockDurations, false);
			mbs.add(mb);
		}
		return mbs;
	}

	private int getStartingNote(List<int[]> stretchedChords, List<Integer> blockChordNoteChoices,
			int chordNum, int BLOCK_TARGET_MODE) {

		int chordNumIndex = chordNum % stretchedChords.size();
		int chordNoteChoiceIndex = (BLOCK_TARGET_MODE == 2
				&& chordNum == blockChordNoteChoices.size()) ? (chordNum - 1)
						: (chordNum % blockChordNoteChoices.size());
		int[] chord = stretchedChords.get(chordNumIndex);

		int startingPitch = (BLOCK_TARGET_MODE == 0)
				? MidiUtils.getXthChordNote(blockChordNoteChoices.get(chordNoteChoiceIndex), chord)
				: ((BLOCK_TARGET_MODE == 1) ? chord[0] : (5 * 12));
		int startingOct = startingPitch / 12;
		int startingNote = MidiUtils.MAJ_SCALE.indexOf(startingPitch % 12);
		if (startingNote < 0) {
			throw new IllegalArgumentException("BAD STARTING NOTE!");
		}
		return startingNote + startingOct * 7
				+ ((BLOCK_TARGET_MODE > 0) ? blockChordNoteChoices.get(chordNoteChoiceIndex) : 0);
	}

	private Map<Integer, List<Integer>> patternsFromNotes(Map<Integer, List<Note>> fullMelodyMap) {
		Map<Integer, List<Integer>> patterns = new HashMap<>();
		for (Integer chKey : fullMelodyMap.keySet()) {
			//LOGGER.debug("chkey: " + chKey);
			patterns.put(chKey, patternFromNotes(fullMelodyMap.get(chKey), 1,
					progressionDurations.get(chKey % progressionDurations.size())));
			//LOGGER.debug(StringUtils.join(patterns.get(chKey), ","));
		}
		//LOGGER.debug(StringUtils.join(pattern, ", "));
		return patterns;
	}

	private List<Integer> patternFromNotes(List<Note> notes, int chordsTotal, Double measureTotal) {
		// strategy: use 64 hits in pattern, then simplify if needed

		int hits = (int) Math.round(
				chordsTotal * MELODY_PATTERN_RESOLUTION * measureTotal / Durations.WHOLE_NOTE);
		measureTotal = (measureTotal == null) ? (chordsTotal
				* ((gc.isDoubledDurations()) ? Durations.WHOLE_NOTE * 2 : Durations.WHOLE_NOTE))
				: measureTotal;
		double timeForHit = measureTotal / hits;
		List<Integer> pattern = new ArrayList<>();
		List<Double> durationBuckets = new ArrayList<>();
		for (int i = 1; i <= hits; i++) {
			durationBuckets.add(timeForHit * i - DBL_ERR);
			pattern.add(0);
		}
		pattern.set(0, (notes.size() > 0 && notes.get(0).getPitch() < 0) ? 0 : 1);
		double currentDuration = 0;
		int explored = 0;

		// 111 0 11111 000 11111

		// 1 0 0 0 1 0 0 0 0 0 0 0 1 0 0 0 0
		int counter = 0;
		for (Note n : notes) {
			/*LOGGER.debug(
					"Current dur: " + currentDuration + ", + rhythm: " + n.getRhythmValue());*/
			currentDuration += n.getRhythmValue();
			for (int i = explored; i < hits; i++) {
				if (currentDuration < durationBuckets.get(i)) {
					int nextPitch = ((notes.size() > counter + 1)
							&& notes.get(counter + 1).getPitch() > 0) ? 1 : 0;
					pattern.set(i, nextPitch);
					explored = i;
					break;
				}
			}
			counter++;
		}
		if (gc.isMelodyPatternFlip()) {
			for (int i = 0; i < pattern.size(); i++) {
				pattern.set(i, 1 - pattern.get(i));
			}
		}
		LOGGER.debug("Melody note pattern: " + StringUtils.join(pattern, ", "));
		return pattern;
	}

	private int selectClosestIndexFromChord(int[] chord, int previousNotePitch,
			boolean directionUp) {
		if (directionUp) {
			for (int i = 0; i < chord.length; i++) {
				if (previousNotePitch < chord[i]) {
					return i;
				}
			}
			return chord.length - 1;
		} else {
			for (int i = chord.length - 1; i > 0; i--) {
				if (previousNotePitch > chord[i]) {
					return i;
				}
			}
			return 0;
		}

	}

	private int pickRandomBetweenIndexesInclusive(int[] chord, int startIndex, int endIndex,
			Random generator, double posInChord) {
		//clamp
		if (startIndex < 0)
			startIndex = 0;
		if (endIndex > chord.length - 1) {
			endIndex = chord.length - 1;
		}
		if (((chord[startIndex] % 12 == 11) && (chord[endIndex] % 12 == 11)) || posInChord > 0.66) {
			// do nothing
			//LOGGER.debug("The forced B case, " + posInChord);
		} else if (chord[startIndex] % 12 == 11) {
			startIndex++;
			//LOGGER.debug("B start avoided");
		} else if (chord[endIndex] % 12 == 11) {
			endIndex--;
			//LOGGER.debug("B end avoided");
		}
		int index = generator.nextInt(endIndex - startIndex + 1) + startIndex;
		return chord[index];
	}


	private static List<Boolean> generateMelodyDirectionsFromChordProgression(
			List<int[]> progression, boolean roots) {

		List<Boolean> ascDirectionList = new ArrayList<>();

		for (int i = 0; i < progression.size(); i++) {
			if (roots) {
				int current = progression.get(i)[0];
				int next = progression.get((i + 1) % progression.size())[0];
				ascDirectionList.add(Boolean.valueOf(current <= next));
			} else {
				int current = progression.get(i)[progression.get(i).length - 1];
				int next = progression.get((i + 1)
						% progression.size())[progression.get((i + 1) % progression.size()).length
								- 1];
				ascDirectionList.add(Boolean.valueOf(current <= next));
			}

		}

		return ascDirectionList;
	}

	private static List<Double> generateMelodyDirectionChordDividers(int chords, Random dirGen) {
		List<Double> map = new ArrayList<>();
		for (int i = 0; i < chords; i++) {
			double divider = dirGen.nextDouble() * 0.80 + 0.20;
			map.add(divider);

		}
		return map;
	}

	private Vector<Note> generateMelodySkeletonFromChords(MelodyPart mp, List<int[]> chords,
			List<int[]> roots, int measures, int notesSeedOffset, Section sec,
			List<Integer> variations) {

		boolean genVars = variations == null;

		boolean fillChordMelodyMap = false;
		if (chordMelodyMap1.isEmpty() && notesSeedOffset == 0
				&& (roots.size() == chordInts.size())) {
			fillChordMelodyMap = true;
		}

		int MAX_JUMP_SKELETON_CHORD = mp.getBlockJump();
		int SAME_RHYTHM_CHANCE = mp.getDoubledRhythmChance();
		int ALTERNATE_RHYTHM_CHANCE = mp.getAlternatingRhythmChance();
		int EXCEPTION_CHANCE = mp.getNoteExceptionChance();
		int CHORD_STRETCH = 4;

		int seed = mp.getPatternSeed();

		Vector<Note> noteList = new Vector<>();

		Random algoGenerator = new Random(gc.getRandomSeed());
		if (algoGenerator.nextInt(100) < gc.getMelodyUseOldAlgoChance()) {
			return oldAlgoGenerateMelodySkeletonFromChords(mp, measures, roots);
		}

		// if notes seed offset > 0, add it only to one of: rhythms, pitches
		//Random nonMainMelodyGenerator = new Random(seed + 30);
		int pitchPickerOffset = notesSeedOffset;
		int rhythmOffset = notesSeedOffset;

		Random pitchPickerGenerator = new Random(seed + pitchPickerOffset);
		Random exceptionGenerator = new Random(seed + 2 + notesSeedOffset);
		Random sameRhythmGenerator = new Random(seed + 3);
		Random alternateRhythmGenerator = new Random(seed + 4);
		Random durationGenerator = new Random(seed + notesSeedOffset + 5);
		Random directionGenerator = new Random(seed + 10);
		//Random surpriseGenerator = new Random(seed + notesSeedOffset + 15);
		Random exceptionTypeGenerator = new Random(seed + 20 + notesSeedOffset);

		double[] melodySkeletonDurations = { Durations.SIXTEENTH_NOTE, Durations.EIGHTH_NOTE,
				Durations.QUARTER_NOTE, Durations.DOTTED_QUARTER_NOTE, Durations.HALF_NOTE };

		int weight3rd = mp.getSpeed() / 3;
		// 0% ->
		// 0, 0, 0, 40, 80, 100
		// 50% ->
		// 5 11 16 51 85 100
		// 100% ->
		// 11 22 33 72 91 100
		int[] melodySkeletonDurationWeights = { 0 + weight3rd / 3, 0 + weight3rd,
				40 + weight3rd * 2 / 3, 80 + weight3rd / 3, 100 };

		List<int[]> usedChords = null;
		if (gc.isMelodyBasicChordsOnly()) {
			List<int[]> basicChordsUnsquished = getBasicChordsFromRoots(roots);
			for (int i = 0; i < chords.size(); i++) {
				basicChordsUnsquished.set(i,
						convertChordToLength(basicChordsUnsquished.get(i), chords.get(i).length));
			}

			/* 
			 * if...
			 * usedChords = squishChordProgression(basicChordsUnsquished, gc.isSpiceFlattenBigChords(),
					gc.getRandomSeed(), gc.getChordGenSettings().getFlattenVoicingChance());
			 * */
			usedChords = basicChordsUnsquished;

		} else {
			usedChords = chords;
		}

		List<int[]> stretchedChords = usedChords.stream()
				.map(e -> convertChordToLength(e, CHORD_STRETCH)).collect(Collectors.toList());
		List<Double> directionChordDividers = (!gc.isMelodyUseDirectionsFromProgression())
				? generateMelodyDirectionChordDividers(stretchedChords.size(), directionGenerator)
				: null;
		directionGenerator.setSeed(seed + 10);
		boolean currentDirection = directionGenerator.nextBoolean();
		if (!gc.isMelodyUseDirectionsFromProgression()) {
			LOGGER.debug("Direction dividers: " + directionChordDividers.toString() + ", start at: "
					+ currentDirection);
		}

		List<Boolean> directionsFromChords = (gc.isMelodyUseDirectionsFromProgression())
				? generateMelodyDirectionsFromChordProgression(usedChords, true)
				: null;

		boolean alternateRhythm = alternateRhythmGenerator.nextInt(100) < ALTERNATE_RHYTHM_CHANCE;
		//LOGGER.debug("Alt: " + alternateRhythm);

		for (int o = 0; o < measures; o++) {
			int previousNotePitch = 0;
			int firstPitchInTwoChords = 0;

			for (int i = 0; i < stretchedChords.size(); i++) {
				// either after first measure, or after first half of combined chord prog

				if (genVars && (i == 0)) {
					variations = fillVariations(sec, mp, variations, 0);
				}

				if ((variations != null) && (i == 0)) {
					for (Integer var : variations) {
						if (o == measures - 1) {
							LOGGER.info("Melody variation: " + var);
						}

						switch (var) {
						case 0:
							// only add, processed later
							break;
						case 1:
							MAX_JUMP_SKELETON_CHORD = Math.min(4, MAX_JUMP_SKELETON_CHORD + 1);
							break;
						default:
							throw new IllegalArgumentException("Too much variation!");
						}
					}
				}

				if (fillChordMelodyMap && o == 0) {
					if (!chordMelodyMap1.containsKey(Integer.valueOf(i))) {
						chordMelodyMap1.put(Integer.valueOf(i), new ArrayList<>());
					}
				}
				if (i % 2 == 0) {
					previousNotePitch = firstPitchInTwoChords;
					pitchPickerGenerator.setSeed(seed + pitchPickerOffset);
					exceptionGenerator.setSeed(seed + 2 + notesSeedOffset);
					if (alternateRhythm) {
						sameRhythmGenerator.setSeed(seed + 3);
					}
				}

				boolean sameRhythmTwice = sameRhythmGenerator.nextInt(100) < SAME_RHYTHM_CHANCE;

				double rhythmDuration = sameRhythmTwice ? progressionDurations.get(i) / 2.0
						: progressionDurations.get(i);
				int rhythmSeed = (alternateRhythm && i % 2 == 1) ? seed + 1 : seed;
				rhythmSeed += rhythmOffset;
				Rhythm rhythm = new Rhythm(rhythmSeed, rhythmDuration, melodySkeletonDurations,
						melodySkeletonDurationWeights);

				List<Double> durations = rhythm.regenerateDurations(sameRhythmTwice ? 1 : 2,
						MidiGenerator.Durations.SIXTEENTH_NOTE / 2.0);
				if (gc.isMelodyArpySurprises()) {
					if (sameRhythmTwice) {
						if ((i % 2 == 0) || (durations.size() < 3)) {
							durations.addAll(durations);
						} else {
							List<Double> arpedDurations = makeSurpriseTrioArpedDurations(durations);
							if (arpedDurations != null) {
								LOGGER.debug("Double pattern - surprise!");
								durations.addAll(arpedDurations);
							} else {
								durations.addAll(durations);
							}
						}
					} else if (i % 2 == 1 && durations.size() >= 4) {

						List<Double> arpedDurations = makeSurpriseTrioArpedDurations(durations);
						if (arpedDurations != null) {
							LOGGER.debug("Single pattern - surprise!");
							durations = arpedDurations;
						}
					}
				} else {
					if (sameRhythmTwice) {
						durations.addAll(durations);
					}
				}


				int[] chord = stretchedChords.get(i);
				int exceptionCounter = mp.getMaxNoteExceptions();
				boolean allowException = true;
				double durCounter = 0.0;
				boolean changedDirectionByDivider = false;
				if (gc.isMelodyUseDirectionsFromProgression()) {
					currentDirection = directionsFromChords.get(i);
				}
				for (int j = 0; j < durations.size(); j++) {
					boolean tempChangedDir = false;
					int tempSaveMaxJump = MAX_JUMP_SKELETON_CHORD;
					boolean hasSingleNoteException = false;
					if (allowException && j > 0 && exceptionCounter > 0
							&& exceptionGenerator.nextInt(100) < EXCEPTION_CHANCE) {
						if (gc.isMelodySingleNoteExceptions()) {
							hasSingleNoteException = true;
							if (exceptionTypeGenerator.nextBoolean()) {
								MAX_JUMP_SKELETON_CHORD = Math.max(0, MAX_JUMP_SKELETON_CHORD - 1);
								tempChangedDir = true;
								currentDirection = !currentDirection;
							} else {
								MAX_JUMP_SKELETON_CHORD = Math.min(6, MAX_JUMP_SKELETON_CHORD + 2);
							}
						} else {
							currentDirection = !currentDirection;
						}

						exceptionCounter--;
					}
					int pitch = 0;
					int startIndex = 0;
					int endIndex = chord.length - 1;

					if (previousNotePitch != 0) {
						// up, or down
						if (currentDirection) {
							startIndex = selectClosestIndexFromChord(chord, previousNotePitch,
									true);
							while (endIndex - startIndex > MAX_JUMP_SKELETON_CHORD) {
								endIndex--;
							}
						} else {
							endIndex = selectClosestIndexFromChord(chord, previousNotePitch, false);
							while (endIndex - startIndex > MAX_JUMP_SKELETON_CHORD) {
								startIndex++;
							}
						}
					}
					double positionInChord = durCounter / progressionDurations.get(i);
					pitch = pickRandomBetweenIndexesInclusive(chord, startIndex, endIndex,
							pitchPickerGenerator, positionInChord);

					double swingDuration = durations.get(j);
					Note n = new Note(pitch, swingDuration, 100);
					n.setDuration(swingDuration * (0.75 + durationGenerator.nextDouble() / 4)
							* DEFAULT_DURATION_MULTIPLIER);

					if (hasSingleNoteException && gc.isMelodySingleNoteExceptions()) {
						if (tempChangedDir) {
							currentDirection = !currentDirection;
						}
						MAX_JUMP_SKELETON_CHORD = tempSaveMaxJump;
					}
					if (!gc.isMelodySingleNoteExceptions()) {
						if (previousNotePitch == pitch) {
							currentDirection = !currentDirection;
							allowException = false;
						} else {
							allowException = true;
						}
					}

					if (i % 2 == 0 && j == 0 && !gc.isMelodyAvoidChordJumps()) {
						firstPitchInTwoChords = pitch;
					}
					previousNotePitch = pitch;
					if (hasSingleNoteException && tempChangedDir) {
						previousNotePitch += (currentDirection) ? 2 : -2;
					}
					noteList.add(n);
					if (fillChordMelodyMap && o == 0) {
						chordMelodyMap1.get(Integer.valueOf(i)).add(n);
					}
					durCounter += swingDuration;
					if (!gc.isMelodyUseDirectionsFromProgression() && !changedDirectionByDivider
							&& durCounter > directionChordDividers.get(i)) {
						changedDirectionByDivider = true;
						currentDirection = !currentDirection;
					}
				}
				if (!gc.isMelodyUseDirectionsFromProgression() && !changedDirectionByDivider) {
					currentDirection = !currentDirection;
				}

			}
		}

		if (fillChordMelodyMap) {
			List<String> chordStrings = getChordsFromMelodyPitches(2, chordMelodyMap1,
					MidiUtils.baseFreqMap);
			populateMelodyBasedProgression(chordStrings, 1, chordMelodyMap1.keySet().size() - 1);

		}
		if (genVars && variations != null) {
			sec.setVariation(0, getAbsoluteOrder(0, mp), variations);
		}
		return noteList;
	}

	private List<Double> makeSurpriseTrioArpedDurations(List<Double> durations) {

		List<Double> arpedDurations = new ArrayList<>(durations);
		for (int trioIndex = 0; trioIndex < arpedDurations.size() - 2; trioIndex++) {
			double sumThirds = arpedDurations.subList(trioIndex, trioIndex + 3).stream()
					.mapToDouble(e -> e).sum();
			boolean valid = false;
			if (isDottedNote(sumThirds)) {
				sumThirds /= 3.0;
				for (int trio = trioIndex; trio < trioIndex + 3; trio++) {
					arpedDurations.set(trio, sumThirds);
				}
				valid = true;
			} else if (isMultiple(sumThirds, Durations.HALF_NOTE)) {
				if (sumThirds > Durations.DOTTED_HALF_NOTE) {
					sumThirds /= 4.0;
					for (int trio = trioIndex; trio < trioIndex + 3; trio++) {
						arpedDurations.set(trio, sumThirds);
					}
					arpedDurations.add(trioIndex, sumThirds);
				} else {
					sumThirds /= 2.0;
					for (int trio = trioIndex + 1; trio < trioIndex + 3; trio++) {
						arpedDurations.set(trio, sumThirds);
					}
					arpedDurations.remove(trioIndex);
				}
				valid = true;
			}

			if (valid) {
				return arpedDurations;
			}

		}
		return null;
	}

	public static boolean isDottedNote(double note) {
		if (roughlyEqual(Durations.DOTTED_QUARTER_NOTE, note))
			return true;
		if (roughlyEqual(Durations.DOTTED_WHOLE_NOTE, note))
			return true;
		if (roughlyEqual(Durations.DOTTED_HALF_NOTE, note))
			return true;
		if (roughlyEqual(Durations.DOTTED_EIGHTH_NOTE, note))
			return true;
		if (roughlyEqual(Durations.DOTTED_SIXTEENTH_NOTE, note))
			return true;
		return false;
	}

	public static boolean isMultiple(double first, double second) {
		double result = first / second;
		double rounded = Math.round(result);
		if (roughlyEqual(result, rounded)) {
			return true;
		} else {
			return false;
		}
	}

	public static boolean roughlyEqual(double first, double second) {
		return Math.abs(first - second) < DBL_ERR;
	}

	private int getAllowedPitchFromRange(int min, int max, double posInChord, Random splitNoteGen) {

		boolean allowBs = posInChord > 0.66;
		//LOGGER.info("Min: " + min + ", max: " + max);
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
		//LOGGER.info("Size: " + allowedPitches.size());
		final int finalNormMax = normMax;
		//LOGGER.info("Contents: " + StringUtils.join(allowedPitches, ", "));
		allowedPitches.removeIf(e -> !(normMin <= e && e <= finalNormMax));
		if (!allowBs) {
			allowedPitches.remove(Integer.valueOf(11));
			allowedPitches.remove(Integer.valueOf(23));
		}
		//LOGGER.info("Contents: " + StringUtils.join(allowedPitches, ", "));
		int normReturnPitch = allowedPitches.get(splitNoteGen.nextInt(allowedPitches.size()));
		//LOGGER.info("Return n: " + normReturnPitch);
		while (normReturnPitch < min) {
			normReturnPitch += 12;
		}
		return normReturnPitch;
	}

	private boolean fits(int pitch, int min, int max, boolean isInclusive) {
		if (isInclusive) {
			if (pitch >= min && pitch <= max) {
				return true;
			} else {
				return false;
			}
		} else {
			if (pitch > min && pitch < max) {
				return true;
			} else {
				return false;
			}
		}
	}

	protected Map<Integer, List<Note>> convertMelodySkeletonToFullMelody(MelodyPart mp,
			List<Double> durations, Section sec, Vector<Note> skeleton, int notesSeedOffset,
			List<int[]> chords, int measures) {

		int RANDOM_SPLIT_NOTE_PITCH_EXCEPTION_RANGE = 4;

		List<Integer> melodyVars = sec.getVariation(0, getAbsoluteOrder(0, mp));

		int orderSeed = mp.getPatternSeed() + mp.getOrder();
		int seed = mp.getPatternSeed();
		Random splitGenerator = new Random(orderSeed + 4);
		Random pauseGenerator = new Random(orderSeed + 5);
		Random pauseGenerator2 = new Random(orderSeed + 7);
		Random variationGenerator = new Random(mp.getOrder() + gc.getArrangement().getSeed() + 6);
		Random velocityGenerator = new Random(orderSeed + 1 + notesSeedOffset);
		Random splitNoteGenerator = new Random(seed + 8);
		Random splitNoteExceptionGenerator = new Random(seed + 9);
		Random chordLeadingGenerator = new Random(orderSeed + notesSeedOffset + 15);
		Random accentGenerator = new Random(orderSeed + 20);
		Random noteTargetGenerator = new Random(orderSeed + 30);


		int splitChance = mp.getSplitChance();
		Vector<Note> fullMelody = new Vector<>();
		Map<Integer, List<Note>> fullMelodyMap = new HashMap<>();
		for (int i = 0; i < durations.size() * measures; i++) {
			fullMelodyMap.put(i, new Vector<>());
		}
		int chordCounter = 0;
		int measureCounter = 0;
		double durCounter = 0.0;
		double currentChordDur = durations.get(0);

		int volMultiplier = (gc.isScaleMidiVelocityInArrangement()) ? sec.getVol(0) : 100;
		int minVel = multiplyVelocity(mp.getVelocityMin(), volMultiplier, 0, 1);
		int maxVel = multiplyVelocity(mp.getVelocityMax(), volMultiplier, 1, 0);

		int[] pitches = new int[12];
		for (int i = 0; i < skeleton.size(); i++) {
			Note n1 = skeleton.get(i);
			n1.setPitch(n1.getPitch() + mp.getTranspose());
			double adjDur = n1.getRhythmValue();
			if ((durCounter + adjDur) > (currentChordDur + DBL_ERR)) {
				chordCounter = (chordCounter + 1) % durations.size();
				if (chordCounter == 0) {
					measureCounter++;
					// when measure resets
					if (variationGenerator.nextInt(100) < gc.getArrangementPartVariationChance()) {
						splitChance = (int) (splitChance * 1.2);
					}
				}
				durCounter = 0.0;
				currentChordDur = durations.get(chordCounter);
				//splitGenerator.setSeed(seed + 4);
				//pauseGenerator.setSeed(seed + 5);
				//pauseGenerator2.setSeed(seed + 7);
				splitNoteGenerator.setSeed(orderSeed + 8);
				splitNoteExceptionGenerator.setSeed(orderSeed + 9);
			}

			int velocity = velocityGenerator.nextInt(maxVel - minVel) + minVel;
			double positionInChord = durCounter / durations.get(chordCounter);
			if (positionInChord < DBL_ERR && accentGenerator.nextInt(100) < mp.getAccents()) {
				velocity = addAccent(velocity, accentGenerator, mp.getAccents());
			}

			n1.setDynamic(velocity);


			durCounter += adjDur;

			boolean splitLastNoteInChord = (chordLeadingGenerator.nextInt(100) < mp
					.getLeadChordsChance()) && (adjDur > Durations.DOTTED_SIXTEENTH_NOTE * 1.1)
					&& (i < skeleton.size() - 1)
					&& ((durCounter + skeleton.get(i + 1).getRhythmValue()) > currentChordDur);


			if ((adjDur > Durations.EIGHTH_NOTE * 1.4 && splitGenerator.nextInt(100) < splitChance)
					|| splitLastNoteInChord) {

				int pitch1 = n1.getPitch();
				int indexN2 = (i + 1) % skeleton.size();
				Note n2 = skeleton.get(indexN2);
				int pitch2 = n2.getPitch() + (indexN2 > 0 ? mp.getTranspose() : 0);
				if (pitch1 >= pitch2) {
					int higherNote = pitch1;
					if (splitNoteExceptionGenerator.nextInt(100) < 33 && !splitLastNoteInChord) {
						higherNote += RANDOM_SPLIT_NOTE_PITCH_EXCEPTION_RANGE;
					}
					pitch2 = getAllowedPitchFromRange(pitch2, higherNote, positionInChord,
							splitNoteGenerator);
				} else {
					int lowerNote = pitch1;
					if (splitNoteExceptionGenerator.nextInt(100) < 33 && !splitLastNoteInChord) {
						lowerNote -= RANDOM_SPLIT_NOTE_PITCH_EXCEPTION_RANGE;
					}
					pitch2 = getAllowedPitchFromRange(lowerNote, pitch2, positionInChord,
							splitNoteGenerator);
				}

				double multiplier = (isDottedNote(adjDur) && splitGenerator.nextBoolean())
						? (1.0 / 3.0)
						: 0.5;

				double swingDuration1 = adjDur * multiplier;
				double swingDuration2 = swingDuration1;

				Note n1split1 = new Note(pitch1, swingDuration1, velocity);
				Note n1split2 = new Note(pitch2, swingDuration2, velocity - 10);
				fullMelody.add(n1split1);
				fullMelodyMap.get(chordCounter + chords.size() * measureCounter).add(n1split1);

				fullMelody.add(n1split2);
				fullMelodyMap.get(chordCounter + chords.size() * measureCounter).add(n1split2);

				if (multiplier < 0.4) {
					int pitch3 = (splitGenerator.nextBoolean()) ? pitch1 : pitch2;
					double swingDuration3 = swingDuration1;
					Note n1split3 = new Note(pitch3, swingDuration3, velocity - 20);
					fullMelody.add(n1split3);
					fullMelodyMap.get(chordCounter + chords.size() * measureCounter).add(n1split3);
				}

			} else {
				fullMelody.add(n1);
				fullMelodyMap.get(chordCounter + chords.size() * measureCounter).add(n1);
			}
		}
		List<Integer> firstNotePitches = fullMelodyMap.values().stream()
				.map(e -> e.get(0).getPitch()).collect(Collectors.toList());

		List<Integer> fillerPattern = mp.getChordSpanFill()
				.getPatternByLength(fullMelodyMap.keySet().size(), mp.isFillFlip());

		// pause by %, sort not-paused into pitches
		for (int chordIndex = 0; chordIndex < fullMelodyMap.keySet().size(); chordIndex++) {
			List<Note> notes = new ArrayList<>(fullMelodyMap.get(chordIndex));
			Collections.sort(notes,
					(e1, e2) -> (Double.compare(e1.getRhythmValue(), e2.getRhythmValue())));
			pauseGenerator.setSeed(orderSeed + 5);
			int actualPauseChance = adjustChanceParamForTransition(mp.getPauseChance(), sec,
					chordIndex, durations.size(), 40, 0.25, false);
			int pausedNotes = (int) Math.round(notes.size() * actualPauseChance / 100.0);
			int startIndex = (mp.isFillPauses())
					? (gc.isMelodyFillPausesPerChord() ? 1 : ((chordIndex == 0) ? 1 : 0))
					: 0;

			for (int j = 0; j < pausedNotes; j++) {
				Note n = notes.get(j);
				if (startIndex == 1) {
					if (n.equals(fullMelodyMap.get(chordIndex).get(0))) {
						pitches[n.getPitch() % 12]++;
						continue;
					}
				}
				n.setPitch(Integer.MIN_VALUE);
				if (fillerPattern.get(chordIndex) < 1) {
					firstNotePitches.set(chordIndex, Integer.MIN_VALUE);
				}
			}
			for (int j = pausedNotes; j < notes.size(); j++) {
				Note n = notes.get(j);
				if (fillerPattern.get(chordIndex) < 1) {
					n.setPitch(Integer.MIN_VALUE);
					if (fillerPattern.get(chordIndex) < 1) {
						firstNotePitches.set(chordIndex, Integer.MIN_VALUE);
					}
				} else {
					pitches[n.getPitch() % 12]++;
				}
			}
		}


		applyNoteTargets(fullMelody, fullMelodyMap, pitches, notesSeedOffset, chords,
				noteTargetGenerator);

		applyBadIntervalRemoval(fullMelody);


		if (gc.getMelodyReplaceAvoidNotes() > 0) {
			replaceAvoidNotes(fullMelodyMap, chords, mp.getPatternSeed(),
					gc.getMelodyReplaceAvoidNotes());
		}


		// fill pauses toggle
		if (mp.isFillPauses()) {
			Note fillPauseNote = fullMelody.get(0);
			double addedDuration = 0;
			List<Note> notesToRemove = new ArrayList<>();
			for (int i = 1; i < fullMelody.size(); i++) {
				Note n = fullMelody.get(i);
				if (n.getPitch() < 0) {
					addedDuration += n.getRhythmValue();
					notesToRemove.add(n);
				} else {
					fillPauseNote.setDuration(fillPauseNote.getDuration() + addedDuration);
					fillPauseNote.setRhythmValue(fillPauseNote.getRhythmValue() + addedDuration);
					addedDuration = 0;
					fillPauseNote = n;
				}
			}
			fullMelody.removeAll(notesToRemove);
			fullMelodyMap.values().forEach(e -> e.removeAll(notesToRemove));

			if (addedDuration > DBL_ERR) {
				fillPauseNote.setDuration(fillPauseNote.getDuration() + addedDuration);
			}
		}

		// repair target notes
		for (int i = 0; i < firstNotePitches.size(); i++) {
			if (fullMelodyMap.get(i).size() > 0) {
				Note n = fullMelodyMap.get(i).get(0);
				if (n.getPitch() >= 0) {
					n.setPitch(firstNotePitches.get(i));
				}
			}
		}
		// accent lengths of first notes in chord, if not paused and next note has different pitch
		fullMelodyMap.values().forEach(e -> {
			if (e.size() < 3) {
				return;
			}
			Note n = e.get(0);
			if (n.getPitch() >= 0 && n.getDuration() < Durations.QUARTER_NOTE * 1.1
					&& e.get(1).getPitch() != n.getPitch() && e.get(2).getPitch() != n.getPitch()) {
				n.setDuration(n.getDuration() * (1 + (mp.getAccents() / 200.0)));
			}
		});

		// extraTranspose variation
		if (melodyVars != null && !melodyVars.isEmpty()
				&& melodyVars.contains(Integer.valueOf(0))) {
			for (int i = 0; i < fullMelody.size(); i++) {
				Note n = fullMelody.get(i);
				if (n.getPitch() >= 0) {
					n.setPitch(n.getPitch() + 12);
				}
			}
		}

		return fullMelodyMap;
	}

	private void applyNoteTargets(List<Note> fullMelody, Map<Integer, List<Note>> fullMelodyMap,
			int[] pitches, int notesSeedOffset, List<int[]> chords, Random noteTargetGenerator) {
		// --------- NOTE ADJUSTING ----------------
		double requiredPercentageCs = gc.getMelodyTonicNoteTarget() / 100.0;
		int needed = (int) Math.floor(
				fullMelody.stream().filter(e -> e.getPitch() >= 0).count() * requiredPercentageCs);
		LOGGER.info("Found C's: " + pitches[0] + ", needed: " + needed);
		int surplusTonics = pitches[0] - needed;

		int[] chordSeparators = new int[fullMelodyMap.keySet().size() + 1];
		chordSeparators[0] = 0;
		for (Integer i : fullMelodyMap.keySet()) {
			int index = i + 1;
			chordSeparators[index] = fullMelodyMap.get(i).size() + chordSeparators[index - 1];
		}

		if (gc.getMelodyTonicNoteTarget() > 0 && notesSeedOffset == 0) {
			// for main sections: try to adjust notes towards C if there isn't enough C's
			if (surplusTonics < 0) {
				//LOGGER.debug("Correcting melody!");
				int investigatedChordIndex = chordSeparators.length - 1;


				// adjust in pairs starting from last
				while (investigatedChordIndex > 0 && surplusTonics < 0) {
					int end = chordSeparators[investigatedChordIndex] - 1;
					int investigatedChordStart = chordSeparators[investigatedChordIndex - 1];
					for (int i = end; i >= investigatedChordStart; i--) {
						Note n = fullMelody.get(i);
						int p = n.getPitch();
						if (p < 0) {
							continue;
						}
						// D
						if (p % 12 == 2) {
							n.setPitch(p - 2);
							surplusTonics++;
							break;
						}
						// B
						if (p % 12 == 11) {
							n.setPitch(p + 1);
							surplusTonics++;
							break;
						}
					}
					investigatedChordIndex -= 2;
				}

				//LOGGER.debug("Remaining difference after last pairs: " + difference);

				// adjust in pairs starting from last-1
				investigatedChordIndex = chordSeparators.length - 2;
				while (investigatedChordIndex > 0 && surplusTonics < 0) {
					int end = chordSeparators[investigatedChordIndex] - 1;
					int investigatedChordStart = chordSeparators[investigatedChordIndex - 1];
					for (int i = end; i >= investigatedChordStart; i--) {
						Note n = fullMelody.get(i);
						int p = n.getPitch();
						if (p < 0) {
							continue;
						}
						// D
						if (p % 12 == 2) {
							n.setPitch(p - 2);
							surplusTonics++;
							break;
						}
						// B
						if (p % 12 == 11) {
							n.setPitch(p + 1);
							surplusTonics++;
							break;
						}
					}
					investigatedChordIndex -= 2;
				}

				LOGGER.debug("TONIC: Remaining difference after first pairs: " + surplusTonics);

			}
		}

		if (gc.getMelodyModeNoteTarget() > 0 && gc.getScaleMode().modeTargetNote > 0) {
			double requiredPercentage = gc.getMelodyModeNoteTarget() / 100.0;
			needed = (int) Math.ceil(fullMelody.stream().filter(e -> e.getPitch() >= 0).count()
					* requiredPercentage);

			int modeNote = MidiUtils.MAJ_SCALE.get(gc.getScaleMode().modeTargetNote);
			LOGGER.info("Found Mode notes: " + pitches[modeNote] + ", needed: " + needed);
			if (pitches[modeNote] < needed) {

				int difference = needed - pitches[modeNote];
				int pitchAbove = MidiUtils.MAJ_SCALE
						.get((gc.getScaleMode().modeTargetNote + 1) % 7);
				int pitchBelow = MidiUtils.MAJ_SCALE
						.get((gc.getScaleMode().modeTargetNote + 6) % 7);

				//LOGGER.debug("Correcting melody!");
				int investigatedChordIndex = chordSeparators.length - 1;


				// adjust in pairs starting from last
				while (investigatedChordIndex > 0 && difference > 0) {
					int end = chordSeparators[investigatedChordIndex] - 1;
					int investigatedChordStart = chordSeparators[investigatedChordIndex - 1];
					for (int i = end; i >= investigatedChordStart; i--) {
						Note n = fullMelody.get(i);
						int p = n.getPitch();
						if (p < 0) {
							continue;
						}
						// above
						if (p % 12 == pitchAbove && (pitchAbove != 0 || surplusTonics > 0)) {
							n.setPitch(p - pitchAbove + modeNote);
							if (pitchAbove == 0) {
								surplusTonics--;
							}
							difference--;
							break;
						}
						// below
						if (p % 12 == pitchBelow && (pitchBelow != 0 || surplusTonics > 0)) {
							n.setPitch(p - pitchBelow + modeNote);
							if (pitchBelow == 0) {
								surplusTonics--;
							}
							difference--;
							break;
						}
					}
					investigatedChordIndex -= 2;
				}

				//LOGGER.debug("Remaining difference after last pairs: " + difference);

				// adjust in pairs starting from last-1
				investigatedChordIndex = chordSeparators.length - 2;
				while (investigatedChordIndex > 0 && difference > 0) {
					int end = chordSeparators[investigatedChordIndex] - 1;
					int investigatedChordStart = chordSeparators[investigatedChordIndex - 1];
					for (int i = end; i >= investigatedChordStart; i--) {
						Note n = fullMelody.get(i);
						int p = n.getPitch();
						if (p < 0) {
							continue;
						}
						// above
						if (p % 12 == pitchAbove && (pitchAbove != 0 || surplusTonics > 0)) {
							n.setPitch(p - pitchAbove + modeNote);
							if (pitchAbove == 0) {
								surplusTonics--;
							}
							difference--;
							break;
						}
						// below
						if (p % 12 == pitchBelow && (pitchBelow != 0 || surplusTonics > 0)) {
							n.setPitch(p - pitchBelow + modeNote);
							if (pitchBelow == 0) {
								surplusTonics--;
							}
							difference--;
							break;
						}
					}
					investigatedChordIndex -= 2;
				}

				LOGGER.info("MODE: Remaining difference after first pairs: " + (-1 * difference));
			}

		}
		if (gc.getMelodyChordNoteTarget() > 0) {
			int chordSize = fullMelodyMap.keySet().size();
			double requiredPercentage = gc.getMelodyChordNoteTarget() / 100.0;
			needed = (int) Math.ceil(fullMelody.stream().filter(e -> e.getPitch() >= 0).count()
					* requiredPercentage);
			// step 1: get count of how many 

			// step 2: get % of how many of the others need to be turned into chord notes

			// step 3: apply %
			int found = 0;
			for (int chordIndex = 0; chordIndex < chordSize; chordIndex++) {
				List<Note> notes = fullMelodyMap.get(chordIndex);
				List<Integer> chordNotes = MidiUtils
						.chordToPitches(chords.get(chordIndex % chords.size()));
				for (Note n : notes) {
					if (chordNotes.contains(n.getPitch() % 12)) {
						found++;
					}
				}
			}

			LOGGER.info("Found Chord notes: " + found + ", needed: " + needed);
			if (found < needed) {
				int difference = needed - found;
				/*int chanceToConvertOthers = 100
						- ((100 * (fullMelody.size() - difference)) / fullMelody.size());*/

				for (int chordIndex = 0; chordIndex < chordSize; chordIndex++) {
					if (difference <= 0) {
						break;
					}
					int maxDifferenceForThisChord = Math.max(1, (difference + 4) / (chordSize));
					List<Note> notes = fullMelodyMap.get(chordIndex);
					List<Integer> chordNotes = MidiUtils
							.chordToPitches(chords.get(chordIndex % chords.size()));

					List<Note> sortedNotes = new ArrayList<>(notes);
					Collections.sort(sortedNotes, (e1, e2) -> MidiUtils
							.compareNotesByDistanceFromChordPitches(e1, e2, chordNotes));

					for (Note n : notes) {
						if (n.getPitch() < 0) {
							continue;
						}
						if (!chordNotes.contains(n.getPitch() % 12)) {
							n.setPitch(n.getPitch() - (n.getPitch() % 12)
									+ MidiUtils.getClosestFromList(chordNotes, n.getPitch() % 12));
							difference--;
							maxDifferenceForThisChord--;
						}
						if (difference <= 0 || maxDifferenceForThisChord <= 0) {
							break;
						}
					}
				}

				LOGGER.info("CHORD: Remaining difference: " + (-1 * difference));
			}

		}
	}

	private void replaceAvoidNotes(Map<Integer, List<Note>> fullMelodyMap, List<int[]> chords,
			int randomSeed, int notesToAvoid) {
		Random rand = new Random(randomSeed);
		for (int i = 0; i < fullMelodyMap.keySet().size(); i++) {
			Set<Integer> avoidNotes = MidiUtils.avoidNotesFromChord(chords.get(i % chords.size()),
					notesToAvoid);
			//LOGGER.debug(StringUtils.join(avoidNotes, ","));
			List<Note> notes = fullMelodyMap.get(i);
			for (int j = 0; j < notes.size(); j++) {
				Note n = notes.get(j);
				int oldPitch = n.getPitch();
				if (oldPitch < 0) {
					continue;
				}
				//LOGGER.debug("Note: " + n.getPitch() + ", RV: " + n.getRhythmValue());
				boolean avoidAllLengths = true;
				if (avoidAllLengths || (n.getRhythmValue() > Durations.EIGHTH_NOTE - DBL_ERR)) {
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
						/*LOGGER.debug("Avoiding note: " + j + ", in chord: " + i + ", pitch change: "
								+ (oldPitch - newPitch));*/
					}
				}

			}
		}

	}

	private void applyBadIntervalRemoval(List<Note> fullMelody) {

		int previousPitch = -1;
		for (int i = 0; i < fullMelody.size(); i++) {
			Note n = fullMelody.get(i);
			int pitch = n.getPitch();
			if (pitch < 0) {
				continue;
			}
			// remove all instances of B-F and F-B (the only interval of 6 within the key)
			if (previousPitch % 12 == 11 && Math.abs(pitch - previousPitch) == 6) {
				n.setPitch(pitch - 1);
			} else if (pitch % 12 == 11 && Math.abs(pitch - previousPitch) == 6) {
				n.setPitch(pitch + 1);
			}
			previousPitch = n.getPitch();
		}


	}

	private void processSectionTransition(Section sec, List<Note> notes, double maxDuration,
			double crescendoStartPercentage, double maxMultiplierAdd, double muteStartPercentage) {
		if (sec.isTransition()) {
			applyCrescendoMultiplier(notes, maxDuration, crescendoStartPercentage,
					maxMultiplierAdd);
			if (sec.getTransitionType() == 7) {
				applyCrescendoMultiplierMinimum(notes, maxDuration, muteStartPercentage, 0.05,
						0.05);
			}
		}
	}

	private void applyCrescendoMultiplier(List<Note> notes, double maxDuration,
			double crescendoStartPercentage, double maxMultiplierAdd) {
		applyCrescendoMultiplierMinimum(notes, maxDuration, crescendoStartPercentage,
				maxMultiplierAdd, 1);
	}

	private void applyCrescendoMultiplierMinimum(List<Note> notes, double maxDuration,
			double crescendoStartPercentage, double maxMultiplierAdd, double minimum) {
		double dur = 0.0;
		double start = maxDuration * crescendoStartPercentage;
		for (Note n : notes) {
			if (dur > start) {
				double multiplier = minimum
						+ maxMultiplierAdd * ((dur - start) / (maxDuration - start));
				if (multiplier < 0.1) {
					n.setPitch(Note.REST);
				} else {
					n.setDynamic(OMNI.clampVel(n.getDynamic() * multiplier));
				}
				//LOGGER.debug("Applied multiplier: " + multiplier);
			}
			dur += n.getRhythmValue();
		}
	}

	private void applyNoteLengthMultiplier(List<Note> notes, int noteLengthMultiplier) {
		if (noteLengthMultiplier == 100) {
			return;
		}
		boolean avoidSamePitchCollision = true;
		for (int i = 0; i < notes.size(); i++) {
			Note n = notes.get(i);
			double duration = n.getDuration() * noteLengthMultiplier / 100.0;
			if (avoidSamePitchCollision && noteLengthMultiplier > 100) {
				if (i < notes.size() - 1 && n.getPitch() == notes.get(i + 1).getPitch()) {
					duration = Math.min(duration, n.getRhythmValue() * 0.95);
				} else if (i < notes.size() - 2 && n.getPitch() == notes.get(i + 2).getPitch()) {
					duration = Math.min(duration, n.getRhythmValue() * 0.95);
				}
			}
			n.setDuration(duration);
		}
	}

	private int addAccent(int velocity, Random accentGenerator, int accent) {
		int newVelocity = velocity + BASE_ACCENT + accentGenerator.nextInt(11) - 5 + accent / 20;
		return OMNI.clamp(newVelocity, 0, 127);
	}

	@SuppressWarnings("unchecked")
	private void swingPhrase(Phrase phr, int swingPercent, double swingUnitOfTime) {
		if (gc.getGlobalSwingOverride() != null) {
			swingPercent = gc.getGlobalSwingOverride();
		}
		if (swingPercent == 50) {
			return;
		}
		Vector<Note> notes = phr.getNoteList();
		double currentChordDur = progressionDurations.get(0);
		int chordCounter = 0;

		boolean logSwing = false;

		int swingPercentAmount = swingPercent;
		double swingAdjust = swingUnitOfTime * (swingPercentAmount / ((double) 50.0))
				- swingUnitOfTime;
		double durCounter = 0.0;

		if (logSwing)
			LOGGER.debug(
					"-----------------------------STARTING SWING -----------------------------------");

		List<Double> durationBuckets = new ArrayList<>();
		List<Integer> chordSeparators = new ArrayList<>();
		for (int i = 0; i < notes.size(); i++) {
			durCounter += notes.get(i).getRhythmValue();
			durationBuckets.add(durCounter);
			if (durCounter + DBL_ERR > currentChordDur) {
				chordSeparators.add(i);
				chordCounter = (chordCounter + 1) % progressionDurations.size();
				currentChordDur = progressionDurations.get(chordCounter);
				durCounter = 0.0;
			}
			if (logSwing)
				LOGGER.debug("Dur: " + durCounter + ", chord counter: " + chordCounter);
		}
		// fix short notes at the end not going to next chord
		if (durCounter > DBL_ERR) {
			chordCounter = (chordCounter + 1) % progressionDurations.size();
			chordSeparators.add(notes.size() - 1);
			durCounter = 0.0;
			currentChordDur = progressionDurations.get(chordCounter);
		}
		int chordSepIndex = 0;
		Note swungNote = null;
		Note latestSuitableNote = null;
		durCounter = 0.0;
		for (int i = 0; i < notes.size(); i++) {
			Note n = notes.get(i);
			double adjDur = n.getRhythmValue();
			if (i > chordSeparators.get(chordSepIndex)) {
				chordSepIndex++;
				swingAdjust = swingUnitOfTime * (swingPercentAmount / ((double) 50.0))
						- swingUnitOfTime;
				durCounter = 0.0;

				if (swungNote != null) {
					swingAdjust *= -1;
					double swungDur = swungNote.getRhythmValue();
					swungNote.setRhythmValue(swungDur + swingAdjust);
					swungNote.setDuration((swungDur + swingAdjust) * DEFAULT_DURATION_MULTIPLIER);
					swingAdjust *= -1;
					swungNote = null;
					latestSuitableNote = null;
					if (logSwing)
						LOGGER.debug("Unswung swung note!");
				}
			}
			durCounter += adjDur;
			boolean processed = false;

			// try to find latest note which can be added/subtracted with swingAdjust
			if (swungNote == null) {
				if (adjDur - Math.abs(swingAdjust) > DBL_ERR) {
					latestSuitableNote = n;
				}
				processed = true;
			} else {
				if ((adjDur - Math.abs(swingAdjust) > DBL_ERR) && latestSuitableNote == null) {
					latestSuitableNote = n;
					processed = true;
				}
			}

			// apply swing to best note from previous section when landing on "exact" hits
			if (isMultiple(durCounter, swingUnitOfTime)) {

				if (logSwing)
					LOGGER.debug(durCounter + " is Multiple of Unit");
				// nothing was caught in first half, SKIP swinging for this 2-unit bit of time
				if (swungNote == null && isMultiple(durCounter, 2 * swingUnitOfTime)) {
					swungNote = null;
					latestSuitableNote = null;
					if (logSwing)
						LOGGER.debug("Can't swing this!");
				} else {
					if (latestSuitableNote != null) {
						double suitableDur = latestSuitableNote.getRhythmValue();
						if (swungNote == null) {
							latestSuitableNote.setRhythmValue(suitableDur + swingAdjust);
							latestSuitableNote.setDuration(
									(suitableDur + swingAdjust) * DEFAULT_DURATION_MULTIPLIER);
							swingAdjust *= -1;
							swungNote = latestSuitableNote;
							latestSuitableNote = null;
							if (logSwing)
								LOGGER.debug("Processed 1st swing!");
						} else {
							latestSuitableNote.setRhythmValue(suitableDur + swingAdjust);
							latestSuitableNote.setDuration(
									(suitableDur + swingAdjust) * DEFAULT_DURATION_MULTIPLIER);
							swingAdjust *= -1;
							swungNote = null;
							latestSuitableNote = null;
							if (logSwing)
								LOGGER.debug("Processed 2nd swing!");
						}
					} else {
						if (swungNote != null) {
							double swungDur = swungNote.getRhythmValue();
							swungNote.setRhythmValue(swungDur + swingAdjust);
							swungNote.setDuration(
									(swungDur + swingAdjust) * DEFAULT_DURATION_MULTIPLIER);
							swingAdjust *= -1;
							swungNote = null;
							latestSuitableNote = null;
							if (logSwing)
								LOGGER.debug("Unswung swung note!");
						}
					}
				}

			}

			// 
			if (!processed && !isMultiple(durCounter, 2 * swingUnitOfTime)) {
				if (swungNote != null) {
					if ((adjDur - Math.abs(swingAdjust) > DBL_ERR) && latestSuitableNote == null) {
						latestSuitableNote = n;
					}
				}
			}
		}

		if (swungNote != null) {
			double swungDur = swungNote.getRhythmValue();
			swungNote.setRhythmValue(swungDur + swingAdjust);
			swungNote.setDuration((swungDur + swingAdjust) * DEFAULT_DURATION_MULTIPLIER);
			swingAdjust *= -1;
			swungNote = null;
			latestSuitableNote = null;
			if (logSwing)
				LOGGER.debug("Unswung swung note!");
		}

		if (logSwing) {
			LOGGER.debug("AFTER:");
			currentChordDur = progressionDurations.get(0);
			durCounter = 0.0;
			chordCounter = 0;
			durationBuckets = new ArrayList<>();
			for (int i = 0; i < notes.size(); i++) {
				durCounter += notes.get(i).getRhythmValue();
				if (durCounter - DBL_ERR > currentChordDur) {
					chordCounter = (chordCounter + 1) % progressionDurations.size();
					currentChordDur = progressionDurations.get(chordCounter);
					durCounter = 0.0;
				}
				LOGGER.debug("Dur: " + durCounter + ", chord counter: " + chordCounter);
			}
		}
	}

	private List<String> getChordsFromMelodyPitches(int orderOfMatch,
			Map<Integer, List<Note>> melodyMap, Map<String, Set<Integer>> freqMap) {
		List<String> chordStrings = new ArrayList<>();
		String prevChordString = null;

		for (int i = 0; i < melodyMap.keySet().size(); i++) {
			List<Integer> chordFreqs = new ArrayList<>();
			double totalDuration = 0;
			for (Note n : melodyMap.get(i)) {
				double dur = n.getRhythmValue();
				double durCounter = 0.0;
				int index = i;
				if (index >= progressionDurations.size()) {
					index = progressionDurations.size() - 1;
				}
				while (durCounter < dur && totalDuration < progressionDurations.get(index)) {
					chordFreqs.add(n.getPitch() % 12);
					durCounter += Durations.EIGHTH_NOTE;
					totalDuration += Durations.EIGHTH_NOTE;
				}
			}

			Map<Integer, Long> freqCounts = chordFreqs.stream()
					.collect(Collectors.groupingBy(e -> e, Collectors.counting()));

			Map<Integer, Long> top3 = freqCounts.entrySet().stream()
					.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(4)
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
							(e1, e2) -> e1, LinkedHashMap::new));

			//top3.entrySet().stream().forEach(System.out::println);
			// TODO: if prevChordString not a major chord, not indexed in circle -> never continue circle?
			String chordString = applyChordFreqMap(top3, orderOfMatch, prevChordString, freqMap);
			LOGGER.info("Alternate chord #" + i + ": " + chordString);
			chordStrings.add(chordString);
			prevChordString = chordString;
		}
		return chordStrings;
	}

	public void generatePrettyUserChords(int mainGeneratorSeed, int fixedLength,
			double maxDuration) {
		generateChordProgression(mainGeneratorSeed, gc.getFixedDuration());
	}

	public int multiplyVelocity(int velocity, int multiplierPercentage, int maxAdjust,
			int minAdjust) {
		if (multiplierPercentage == 100) {
			return velocity;
		} else if (multiplierPercentage > 100) {
			return Math.min(127 - maxAdjust, velocity * multiplierPercentage / 100);
		} else {
			return Math.max(0 + minAdjust, velocity * multiplierPercentage / 100);
		}
	}

	public static List<Double> getSustainedDurationsFromPattern(List<Integer> pattern,
			double addDur) {
		List<Double> durations = new ArrayList<>();
		double dur = 0;
		int end = pattern.size();
		for (int i = 0; i < end; i++) {
			if (pattern.get(i) < 1) {
				dur += addDur;
			} else {
				dur = addDur;
			}
			if (i < end - 1 && pattern.get(i + 1) == 1) {
				durations.add(dur);
			}
		}
		durations.add(dur);

		return durations;
	}

	/*public static List<Double> getSustainedDurationsFromPattern(List<Integer> pattern, double start,
			double end, double maxDur) {
		List<Double> durations = new ArrayList<>();
		double addDur = maxDur / pattern.size();
		double dur = addDur;
		double total = dur;
		for (int i = 0; i < pattern.size(); i++) {
			if (pattern.get(i) < 1) {
				dur += addDur;
			} else {
				if (total > start - 0.01 && total < end + 0.01) {
					durations.add(dur);
				} else if (total > end)
					dur = addDur;
			}
		}
		durations.add(dur);
	
		return durations;
	}*/

	private List<int[]> generateChordProgression(int mainGeneratorSeed, int fixedLength) {

		if (!userChords.isEmpty()) {
			List<int[]> userProgression = new ArrayList<>();
			chordInts.clear();
			chordInts.addAll(userChords);
			for (String chordString : userChords) {
				userProgression.add(mappedChord(chordString));
			}
			LOGGER.info("Using user's custom progression: " + StringUtils.join(userChords, ","));
			return userProgression;
		}

		Random generator = new Random(mainGeneratorSeed);
		Random lengthGenerator = new Random(mainGeneratorSeed);
		Random spiceGenerator = new Random(mainGeneratorSeed);
		Random parallelGenerator = new Random(mainGeneratorSeed + 100);

		boolean isBackwards = !gc.isUseChordFormula();
		Map<String, List<String>> r = (isBackwards) ? cpRulesMap : MidiUtils.cpRulesForwardMap;
		chordInts.clear();
		String lastChord = (isBackwards) ? FIRST_CHORD : LAST_CHORD;
		String firstChord = (isBackwards) ? LAST_CHORD : FIRST_CHORD;


		if (fixedLength == 0) {
			List<Integer> progLengths = Arrays.asList(new Integer[] { 4, 5, 6, 8 });
			fixedLength = progLengths.get(lengthGenerator.nextInt(progLengths.size()));
		}
		int maxLength = (fixedLength > 0) ? fixedLength : 8;
		double maxDuration = fixedLength * Durations.WHOLE_NOTE;
		double fixedDuration = maxDuration / maxLength;
		int currentLength = 0;
		double currentDuration = 0.0;
		List<String> next = r.get("S");
		if (firstChord != null) {
			next = new ArrayList<String>();
			next.add(String.valueOf(firstChord));
		}
		List<String> debugMsg = new ArrayList<>();


		List<String> allowedSpiceChordsMiddle = new ArrayList<>();
		for (int i = 2; i < MidiUtils.SPICE_NAMES_LIST.size(); i++) {
			String chordString = MidiUtils.SPICE_NAMES_LIST.get(i);
			if (!gc.isDimAugDom7thEnabled()
					&& MidiUtils.BANNED_DIM_AUG_LIST.contains(chordString)) {
				continue;
			}
			if (!gc.isEnable9th13th() && MidiUtils.BANNED_9_13_LIST.contains(chordString)) {
				continue;
			}
			allowedSpiceChordsMiddle.add(chordString);
		}

		List<String> allowedSpiceChords = new ArrayList<>();
		for (String s : allowedSpiceChordsMiddle) {
			if (MidiUtils.BANNED_DIM_AUG_LIST.contains(s)
					|| MidiUtils.BANNED_SUSSY_LIST.contains(s)) {
				continue;
			}
			allowedSpiceChords.add(s);
		}


		List<int[]> cpr = new ArrayList<>();
		int[] prevChord = null;
		boolean canRepeatChord = true;
		String lastUnspicedChord = null;
		Random chordRepeatGenerator = new Random(mainGeneratorSeed);
		while ((currentDuration <= maxDuration - Durations.QUARTER_NOTE)
				&& currentLength < maxLength) {
			double durationLeft = maxDuration - Durations.QUARTER_NOTE - currentDuration;

			double dur = fixedDuration;

			if (next.size() == 0 && prevChord != null) {
				cpr.add(prevChord);
				break;
			}
			int bSkipper = (!gc.isDimAugDom7thEnabled() && "Bdim".equals(next.get(next.size() - 1)))
					? 1
					: 0;
			int nextInt = generator.nextInt(Math.max(next.size() - bSkipper, 1));

			// if last and not empty first chord
			boolean isLastChord = durationLeft - dur < DBL_ERR;
			String chordString = null;
			if (isLastChord && lastChord != null) {
				chordString = lastChord;
			} else {
				if (gc.isAllowChordRepeats() && (fixedLength < 8 || !isLastChord) && canRepeatChord
						&& chordInts.size() == 1 && chordRepeatGenerator.nextInt(100) < 10) {
					chordString = String.valueOf(lastUnspicedChord);
					canRepeatChord = false;
				} else {
					chordString = next.get(nextInt);
				}
			}


			List<String> spicyChordList = (!isLastChord && prevChord != null)
					? allowedSpiceChordsMiddle
					: allowedSpiceChords;

			String spicyChordString = chordString;
			String tempSpicyChordString = generateSpicyChordString(spiceGenerator, chordString,
					spicyChordList);

			// Generate with SPICE CHANCE
			if (generator.nextInt(100) < gc.getSpiceChance()
					&& (chordInts.size() < 7 || lastChord == null)) {
				spicyChordString = tempSpicyChordString;
			}

			if (!gc.isDimAugDom7thEnabled()) {
				if (gc.getScaleMode() != ScaleMode.IONIAN && gc.getScaleMode().ordinal() < 7) {
					int scaleOrder = gc.getScaleMode().ordinal();
					if (MidiUtils.MAJOR_CHORDS.indexOf(chordString) == 6 - scaleOrder) {
						spicyChordString = "Bdim";
					}
				}
			}
			if (parallelGenerator.nextInt(100) < gc.getSpiceParallelChance()) {
				int chordIndex = MidiUtils.MAJOR_CHORDS.indexOf(chordString);
				String parallelChordString = MidiUtils.MINOR_CHORDS.get(chordIndex);
				if (chordIndex != 1 || gc.isDimAugDom7thEnabled()) {
					spicyChordString = parallelChordString;
					LOGGER.info("PARALLEL: " + spicyChordString);
				}
			}

			chordInts.add(spicyChordString);


			//LOGGER.debug("Fetching chord: " + chordInt);
			int[] mappedChord = mappedChord(spicyChordString);
			/*mappedChord = transposeChord(mappedChord, Mod.MAJOR_SCALE,
					gc.getScaleMode().noteAdjustScale);*/


			debugMsg.add("Generated int: " + nextInt + ", for chord: " + spicyChordString
					+ ", dur: " + dur + ", C[" + Arrays.toString(mappedChord) + "]");
			cpr.add(mappedChord);
			progressionDurations.add(dur);

			prevChord = mappedChord;
			//LOGGER.debug("Getting next for chord: " + chordString);
			next = r.get(chordString);

			if (fixedLength == 8 && chordInts.size() == 4 && lastChord == null) {
				lastChord = chordString;
			}

			// if last and empty first chord
			if (durationLeft - dur < 0 && lastChord == null) {
				lastChord = chordString;
			}
			currentLength += 1;
			currentDuration += dur;
			lastUnspicedChord = chordString;

		}
		LOGGER.info("CHORD PROG LENGTH: " + cpr.size());
		if (isBackwards) {
			Collections.reverse(progressionDurations);
			Collections.reverse(cpr);
			Collections.reverse(debugMsg);
			Collections.reverse(chordInts);
			FIRST_CHORD = lastChord;
			LAST_CHORD = firstChord;
		} else {
			FIRST_CHORD = firstChord;
			LAST_CHORD = lastChord;
		}

		for (String s : debugMsg) {
			LOGGER.info(s);
		}

		if (progressionDurations.size() > 1
				&& (progressionDurations.get(0) != progressionDurations.get(2))) {
			double middle = (progressionDurations.get(0) + progressionDurations.get(2)) / 2.0;
			progressionDurations.set(0, middle);
			progressionDurations.set(2, middle);

		}

		return cpr;
	}

	private String generateSpicyChordString(Random spiceGenerator, String chordString,
			List<String> spicyChordList) {
		List<String> spicyChordListCopy = new ArrayList<>(spicyChordList);
		String firstLetter = chordString.substring(0, 1);
		List<Integer> targetScale = Arrays.asList(ScaleMode.IONIAN.noteAdjustScale);
		int transposeByLetter = targetScale
				.get(MidiUtils.CHORD_FIRST_LETTERS.indexOf(firstLetter) - 1);
		if (gc.isSpiceForceScale()) {
			spicyChordListCopy
					.removeIf(e -> !MidiUtils.isSpiceValid(transposeByLetter, e, targetScale));
		}

		//LOGGER.debug(StringUtils.join(spicyChordListCopy, ", "));

		if (spicyChordListCopy.isEmpty()) {
			return chordString;
		}
		String spicyChordString = firstLetter
				+ spicyChordListCopy.get(spiceGenerator.nextInt(spicyChordListCopy.size()));
		if (chordString.endsWith("m") && spicyChordString.contains("maj")) {
			spicyChordString = spicyChordString.replace("maj", "m");
		} else if (chordString.length() == 1 && spicyChordString.contains("m")
				&& !spicyChordString.contains("dim") && !spicyChordString.contains("maj")) {
			spicyChordString = spicyChordString.replace("m", "maj");
		}
		return spicyChordString;
	}

	private Note oldAlgoGenerateNote(MelodyPart mp, int[] chord, boolean isAscDirection,
			List<Integer> chordScale, Note previousNote, Random generator, double durationLeft) {
		// int randPitch = generator.nextInt(8);
		int velMin = mp.getVelocityMin();
		int velSpace = mp.getVelocityMax() - velMin;

		int direction = (isAscDirection) ? 1 : -1;
		double dur = pickDurationWeightedRandom(generator, durationLeft, MELODY_DUR_ARRAY,
				MELODY_DUR_CHANCE, Durations.EIGHTH_NOTE);
		boolean isPause = (generator.nextInt(100) < mp.getPauseChance());
		if (previousNote == null) {
			int[] firstChord = chord;
			int chordNote = (gc.isFirstNoteRandomized()) ? generator.nextInt(firstChord.length) : 0;

			int chosenPitch = 60 + (firstChord[chordNote] % 12);

			previousPitch = chordScale.indexOf(Integer.valueOf(chosenPitch));
			if (previousPitch == -1) {
				LOGGER.debug("ERROR PITCH -1 for: " + chosenPitch);
				previousPitch = chordScale.indexOf(Integer.valueOf(chosenPitch + 1));
				if (previousPitch == -1) {
					LOGGER.info("NOT EVEN +1 pitch exists for " + chosenPitch + "!");
				}
			}

			//LOGGER.debug(firstChord[chordNote] + " > from first chord");
			if (isPause) {
				return new Note(Integer.MIN_VALUE, dur);
			}

			return new Note(chosenPitch, dur, velMin + generator.nextInt(velSpace));
		}

		int change = generator.nextInt(mp.getBlockJump() + 1);
		// weighted against same note
		if (change == 0) {
			change = generator.nextInt((mp.getBlockJump() + 1) / 2);
		}

		int generatedPitch = previousPitch + direction * change;
		//fit into 0-7 scale
		generatedPitch = maX(generatedPitch, maxAllowedScaleNotes);


		if (generatedPitch == previousPitch && !isPause) {
			samePitchCount++;
		} else {
			samePitchCount = 0;
		}
		//if 3 or more times same note, swap direction for this case
		if (samePitchCount >= 2) {
			//LOGGER.debug("UNSAMING NOTE!: " + previousPitch + ", BY: " + (-direction * change));
			generatedPitch = maX(previousPitch - direction * change, maxAllowedScaleNotes);
			samePitchCount = 0;
		}
		previousPitch = generatedPitch;
		if (isPause) {
			return new Note(Integer.MIN_VALUE, dur);
		}
		return new Note(chordScale.get(generatedPitch), dur, velMin + generator.nextInt(velSpace));

	}

	private Note[] oldAlgoGenerateMelodyForChord(MelodyPart mp, int[] chord, double maxDuration,
			Random generator, Note previousChordsNote, boolean isAscDirection) {
		List<Integer> scale = transposeScale(MELODY_SCALE, 0, false);

		double currentDuration = 0.0;

		Note previousNote = (gc.isFirstNoteFromChord()) ? null : previousChordsNote;
		List<Note> notes = new ArrayList<>();

		int exceptionsLeft = mp.getMaxNoteExceptions();

		while (currentDuration <= maxDuration - Durations.EIGHTH_NOTE) {
			double durationLeft = maxDuration - Durations.EIGHTH_NOTE - currentDuration;
			boolean exceptionChangeUsed = false;
			// generate note,
			boolean actualDirection = isAscDirection;
			if ((generator.nextInt(100) < 33) && (exceptionsLeft > 0)) {
				//LOGGER.debug("Exception used for chordnote: " + chord[0]);
				exceptionChangeUsed = true;
				actualDirection = !actualDirection;
			}
			Note note = oldAlgoGenerateNote(mp, chord, actualDirection, scale, previousNote,
					generator, durationLeft);
			if (exceptionChangeUsed) {
				exceptionsLeft--;
			}
			previousNote = note;
			currentDuration += note.getRhythmValue();
			Note transposedNote = new Note(note.getPitch(), note.getRhythmValue(),
					note.getDynamic());
			notes.add(transposedNote);
		}
		return notes.toArray(new Note[0]);
	}

	private Vector<Note> oldAlgoGenerateMelodySkeletonFromChords(MelodyPart mp, int measures,
			List<int[]> genRootProg) {
		List<Boolean> directionProgression = generateMelodyDirectionsFromChordProgression(
				genRootProg, true);

		Note previousChordsNote = null;

		Note[] pair024 = null;
		Note[] pair15 = null;
		Random melodyGenerator = new Random();
		if (!mp.isMuted() && mp.getPatternSeed() != 0) {
			melodyGenerator.setSeed(mp.getPatternSeed());
		} else {
			melodyGenerator.setSeed(gc.getRandomSeed());
		}
		LOGGER.info("LEGACY ALGORITHM!");
		Vector<Note> fullMelody = new Vector<>();
		for (int i = 0; i < measures; i++) {
			for (int j = 0; j < genRootProg.size(); j++) {
				Note[] generatedMelody = null;

				if ((i > 0 || j > 0) && (j == 0 || j == 2)) {
					generatedMelody = deepCopyNotes(mp, pair024, genRootProg.get(j),
							melodyGenerator);
				} else if (i > 0 && j == 1) {
					generatedMelody = deepCopyNotes(mp, pair15, null, null);
				} else {
					generatedMelody = oldAlgoGenerateMelodyForChord(mp, genRootProg.get(j),
							progressionDurations.get(j), melodyGenerator, previousChordsNote,
							directionProgression.get(j));
				}

				previousChordsNote = generatedMelody[generatedMelody.length - 1];

				if (i == 0 && j == 0) {
					pair024 = deepCopyNotes(mp, generatedMelody, null, null);
				}
				if (i == 0 && j == 1) {
					pair15 = deepCopyNotes(mp, generatedMelody, null, null);
				}
				fullMelody.addAll(Arrays.asList(generatedMelody));
			}
		}
		return fullMelody;
	}

	public void generateMasterpiece(int mainGeneratorSeed, String fileName) {
		LOGGER.info(
				"============================ MIDI GENERATION IN PROGRESS ===========================");
		long systemTime = System.currentTimeMillis();
		customDrumMappingNumbers = null;
		trackList.clear();
		//MELODY_SCALE = gc.getScaleMode().absoluteNotesC;

		Score score = new Score("MainScore", 120);

		List<Part> melodyParts = new ArrayList<>();
		for (int i = 0; i < gc.getMelodyParts().size(); i++) {
			PartExt p = new PartExt("Melodies" + i, gc.getMelodyParts().get(i).getInstrument(),
					gc.getMelodyParts().get(i).getMidiChannel() - 1);
			melodyParts.add(p);
		}

		List<Part> chordParts = new ArrayList<>();
		for (int i = 0; i < gc.getChordParts().size(); i++) {
			PartExt p = new PartExt("Chords" + i, gc.getChordParts().get(i).getInstrument(),
					gc.getChordParts().get(i).getMidiChannel() - 1);
			chordParts.add(p);
		}
		List<Part> arpParts = new ArrayList<>();
		for (int i = 0; i < gc.getArpParts().size(); i++) {
			PartExt p = new PartExt("Arps" + i, gc.getArpParts().get(i).getInstrument(),
					gc.getArpParts().get(i).getMidiChannel() - 1);
			arpParts.add(p);
		}

		List<Part> bassParts = new ArrayList<>();
		for (int i = 0; i < gc.getBassParts().size(); i++) {
			PartExt p = new PartExt("Bass" + i, gc.getBassParts().get(i).getInstrument(),
					gc.getBassParts().get(i).getMidiChannel() - 1);
			bassParts.add(p);
		}

		List<Part> drumParts = new ArrayList<>();
		for (int i = 0; i < gc.getDrumParts().size(); i++) {
			PartExt p = new PartExt("MainDrums", 0, 9);
			drumParts.add(p);
		}


		List<int[]> generatedRootProgression = generateChordProgression(mainGeneratorSeed,
				gc.getFixedDuration());
		if (!userChordsDurations.isEmpty()) {
			progressionDurations = userChordsDurations;
		}
		if (gc.isDoubledDurations()) {
			for (int i = 0; i < progressionDurations.size(); i++) {
				progressionDurations.set(i, progressionDurations.get(i) * 2);
			}
		}

		List<Double> actualDurations = progressionDurations;

		List<int[]> actualProgression = MidiUtils.squishChordProgressionProgressively(
				generatedRootProgression, gc.isSpiceFlattenBigChords(), gc.getRandomSeed(),
				gc.getChordGenSettings().getFlattenVoicingChance());

		if (!debugEnabled) {
			PrintStream dummyStream = new PrintStream(new OutputStream() {
				public void write(int b) {
					// NO-OP
				}
			});
			System.setOut(dummyStream);
		}

		// Arrangement process..
		LOGGER.info("Starting arrangement..");


		// prepare progressions
		chordProgression = actualProgression;
		rootProgression = generatedRootProgression;

		// run one empty pass through melody generation
		if (userMelody != null) {
			processUserMelody(userMelody);
			actualProgression = chordProgression;
			generatedRootProgression = rootProgression;
			actualDurations = progressionDurations;
		} else if (!gc.getMelodyParts().isEmpty()) {
			fillMelodyFromPart(gc.getMelodyParts().get(0), actualProgression,
					generatedRootProgression, 1, 0, new Section(), new ArrayList<>());
		}
		double measureLength = 0;
		for (Double d : progressionDurations) {
			measureLength += d;
		}
		GENERATED_MEASURE_LENGTH = measureLength / noteMultiplier;
		int counter = 0;

		Arrangement arr = null;
		boolean overridden = false;
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
		LOGGER.info("Arrangement - MANUAL? " + overridden);
		int arrSeed = (arr.getSeed() != 0) ? arr.getSeed() : mainGeneratorSeed;

		int originalPartVariationChance = gc.getArrangementPartVariationChance();
		int secOrder = -1;

		storeGlobalParts();

		int transToSet = 0;
		boolean twoFiveOneChanged = false;
		double sectionStartTimer = 0;
		for (Section sec : arr.getSections()) {
			if (overridden) {
				sec.recalculatePartVariationMapBoundsIfNeeded();
			}
			gc.getArrangement().recalculatePartInclusionMapBoundsIfNeeded();
			sec.setSectionDuration(-1);
			sec.setSectionBeatDurations(null);
			boolean gcPartsReplaced = replaceGuiConfigInstParts(sec);
			secOrder++;
			LOGGER.info("*********************************** Processing section.. " + sec.getType()
					+ "!");
			sec.setStartTime(sectionStartTimer);

			Random rand = new Random(arrSeed);

			modTrans = transToSet;
			LOGGER.info("Key extra transpose: " + modTrans);

			if (sec.isClimax()) {
				// increase variations in follow-up CLIMAX sections, reset when climax ends
				gc.setArrangementPartVariationChance(
						gc.getArrangementPartVariationChance() + originalPartVariationChance / 4);
			} else {
				gc.setArrangementPartVariationChance(originalPartVariationChance);
			}

			if (!overridden && sec.getType().equals(SectionType.BUILDUP.toString())) {
				if (rand.nextInt(100) < gc.getArrangementVariationChance()) {
					List<Integer> exceptionChanceList = new ArrayList<>();
					exceptionChanceList.add(1);
					if (sec.getPartPresenceVariationMap().get(4) != null) {
						for (int i = 0; i < sec.getPartPresenceVariationMap().get(4).length; i++) {
							if (rand.nextInt(100) < 66) {
								sec.setVariation(4, i, exceptionChanceList);
							}

						}
					}
				}
			}

			int notesSeedOffset = sec.getTypeMelodyOffset();
			LOGGER.info("Section energy type: " + notesSeedOffset);

			Random variationGen = new Random(arrSeed + sec.getTypeSeedOffset());
			List<Integer> riskyVariations = calculateRiskyVariations(arr, secOrder, sec,
					notesSeedOffset, variationGen);
			List<String> includedRiskyVarNames = new ArrayList<>();
			for (int i = 0; i < riskyVariations.size(); i++) {
				if (riskyVariations.get(i) > 0) {
					includedRiskyVarNames.add(Section.riskyVariationNames[i]);
				}
			}
			LOGGER.info("Risky Variations: " + StringUtils.join(includedRiskyVarNames, ","));

			// reset back to normal?
			boolean sectionChordsReplaced = false;
			if (sec.isCustomChordsDurationsEnabled()) {
				sectionChordsReplaced = replaceWithSectionCustomChordDurations(sec);
			}
			if (!sectionChordsReplaced) {
				if (riskyVariations.get(1) > 0) {
					//LOGGER.debug("Risky Variation: Chord Swap!");
					rootProgression = melodyBasedRootProgression;
					chordProgression = melodyBasedChordProgression;
					progressionDurations = actualDurations;
				} else {
					rootProgression = generatedRootProgression;
					chordProgression = actualProgression;
					progressionDurations = actualDurations;

				}
			} else if (rootProgression.size() == generatedRootProgression.size()) {
				if (riskyVariations.get(1) > 0) {
					//LOGGER.debug("Risky Variation: Chord Swap!");
					rootProgression = melodyBasedRootProgression;
					chordProgression = melodyBasedChordProgression;
					progressionDurations = actualDurations;
				}
			}

			if (riskyVariations.get(4) > 0) {
				//LOGGER.debug("Risky Variation: Key Change (on next chord)!");
				transToSet = generateKeyChange(generatedRootProgression, arrSeed);
			}

			boolean twoFiveOneChords = gc.getKeyChangeType() == KeyChangeType.TWOFIVEONE
					&& riskyVariations.get(4) > 0;
			if (riskyVariations.get(0) > 0 && !twoFiveOneChords) {
				//LOGGER.debug("Risky Variation: Skip N-1 Chord!");
				skipN1Chord();
			}


			rand.setSeed(arrSeed);
			variationGen.setSeed(arrSeed);

			calculatePresencesForSection(sec, rand, variationGen, overridden, riskyVariations,
					arrSeed, notesSeedOffset, isPreview, counter, arr);

			// FARAWAY
			/*if (!overridden && secOrder > 1) {
				adjustArrangementPresencesIfNeeded(sec, arr.getSections().get(secOrder - 1));
			}*/

			fillMelodyPartsForSection(measureLength, overridden, sec, notesSeedOffset,
					riskyVariations, sectionChordsReplaced);

			// possible chord changes handled after melody parts are filled
			if (twoFiveOneChanged) {
				twoFiveOneChanged = false;
				replaceFirstChordForTwoFiveOne(transToSet);
			}

			if (twoFiveOneChords && chordInts.size() > 2) {
				twoFiveOneChanged = replaceLastChordsForTwoFiveOne(transToSet);
			}
			if (gc.getKeyChangeType() == KeyChangeType.TWOFIVEONE) {
				if (transToSet == -1 * modTrans && transToSet != 0) {
					transToSet = 0;
				}
			}

			fillOtherPartsForSection(sec, arr, overridden, riskyVariations, variationGen, arrSeed,
					measureLength);

			if (gcPartsReplaced) {
				restoreGlobalPartsToGuiConfig();
			}
			counter += sec.getMeasures();
			sectionStartTimer += ((sec.getSectionDuration() > 0) ? sec.getSectionDuration()
					: measureLength) * sec.getMeasures();
		}
		LOGGER.debug("Added phrases to sections..");

		Optional<MelodyPart> firstPresentPart = gc.getMelodyParts().stream()
				.filter(e -> !e.isMuted()).findFirst();

		for (Section sec : arr.getSections()) {
			for (int i = 0; i < gc.getMelodyParts().size(); i++) {
				Phrase p = sec.getMelodies().get(i);
				p.setStartTime(p.getStartTime() + sec.getStartTime());
				p.setAppend(false);
				if (!COLLAPSE_MELODY_TRACKS) {
					melodyParts.get(i).addPhrase(p);
				} else {
					if (firstPresentPart.isPresent()) {
						melodyParts.get(firstPresentPart.get().getOrder() - 1).addPhrase(p);
					}

				}
			}

			for (int i = 0; i < gc.getBassParts().size(); i++) {
				Phrase bp = sec.getBasses().get(i);
				bp.setStartTime(bp.getStartTime() + sec.getStartTime());
				bassParts.get(i).addPhrase(bp);
			}

			for (int i = 0; i < gc.getChordParts().size(); i++) {
				Phrase cp = sec.getChords().get(i);
				cp.setStartTime(cp.getStartTime() + sec.getStartTime());
				chordParts.get(i).addPhrase(cp);
			}

			for (int i = 0; i < gc.getArpParts().size(); i++) {
				Phrase cp = sec.getArps().get(i);
				cp.setStartTime(cp.getStartTime() + sec.getStartTime());
				arpParts.get(i).addPhrase(cp);
			}

			Optional<DrumPart> firstPresentDrumPart = gc.getDrumParts().stream()
					.filter(e -> !e.isMuted()).findFirst();

			for (int i = 0; i < gc.getDrumParts().size(); i++) {
				Phrase p = sec.getDrums().get(i);
				p.setStartTime(p.getStartTime() + sec.getStartTime());
				if (COLLAPSE_DRUM_TRACKS && firstPresentDrumPart.isPresent()) {
					p.setAppend(false);
					drumParts.get(firstPresentDrumPart.get().getOrder() - 1).addPhrase(p);
				} else {
					drumParts.get(i).addPhrase(p);
				}

			}
			if (gc.getChordParts().size() > 0) {
				Phrase csp = sec.getChordSlash();
				csp.setStartTime(csp.getStartTime() + sec.getStartTime());
				csp.setAppend(false);
				chordParts.get(0).addPhrase(csp);
			}

		}
		LOGGER.debug("Added sections to parts..");
		int trackCounter = 1;

		for (int i = 0; i < gc.getMelodyParts().size(); i++) {
			InstPanel ip = VibeComposerGUI.getPanelByOrder(gc.getMelodyParts().get(i).getOrder(),
					VibeComposerGUI.melodyPanels);
			if (!gc.getMelodyParts().get(i).isMuted()) {
				score.add(melodyParts.get(i));
				((PartExt) melodyParts.get(i)).setTrackNumber(trackCounter);
				ip.setSequenceTrack(trackCounter++);
				if (COLLAPSE_MELODY_TRACKS) {
					break;
				}
				//if (VibeComposerGUI.apSm)
			} else {
				((PartExt) melodyParts.get(i)).setTrackNumber(-1);
				ip.setSequenceTrack(-1);
			}
		}

		for (int i = 0; i < gc.getArpParts().size(); i++) {

			InstPanel ip = VibeComposerGUI.getPanelByOrder(gc.getArpParts().get(i).getOrder(),
					VibeComposerGUI.arpPanels);
			if (!gc.getArpParts().get(i).isMuted()) {
				score.add(arpParts.get(i));
				((PartExt) arpParts.get(i)).setTrackNumber(trackCounter);
				ip.setSequenceTrack(trackCounter++);
				//if (VibeComposerGUI.apSm)
			} else {
				ip.setSequenceTrack(-1);
				((PartExt) arpParts.get(i)).setTrackNumber(-1);
			}
		}

		for (int i = 0; i < gc.getBassParts().size(); i++) {
			InstPanel ip = VibeComposerGUI.getPanelByOrder(gc.getBassParts().get(i).getOrder(),
					VibeComposerGUI.bassPanels);
			if (!gc.getBassParts().get(i).isMuted()) {
				score.add(bassParts.get(i));
				((PartExt) bassParts.get(i)).setTrackNumber(trackCounter);
				ip.setSequenceTrack(trackCounter++);
				//if (VibeComposerGUI.apSm)
			} else {
				ip.setSequenceTrack(-1);
				((PartExt) arpParts.get(i)).setTrackNumber(-1);
			}
		}

		for (int i = 0; i < gc.getChordParts().size(); i++) {

			InstPanel ip = VibeComposerGUI.getPanelByOrder(gc.getChordParts().get(i).getOrder(),
					VibeComposerGUI.chordPanels);
			if (!gc.getChordParts().get(i).isMuted()) {
				score.add(chordParts.get(i));
				((PartExt) chordParts.get(i)).setTrackNumber(trackCounter);
				ip.setSequenceTrack(trackCounter++);
			} else {
				ip.setSequenceTrack(-1);
				((PartExt) chordParts.get(i)).setTrackNumber(-1);
			}

		}
		/*if (gc.getScaleMode() != ScaleMode.IONIAN) {
			for (Part p : score.getPartArray()) {
				for (Phrase phr : p.getPhraseArray()) {
					MidiUtils.transposePhrase(phr, ScaleMode.IONIAN.noteAdjustScale,
							gc.getScaleMode().noteAdjustScale);
				}
			}
		}*/
		//int[] backTranspose = { 0, 2, 4, 5, 7, 9, 11, 12 };
		Mod.transpose(score, gc.getTranspose());

		// add drums after transposing transposable parts

		for (int i = 0; i < gc.getDrumParts().size(); i++) {
			score.add(drumParts.get(i));
			InstPanel ip = VibeComposerGUI.getPanelByOrder(gc.getDrumParts().get(i).getOrder(),
					VibeComposerGUI.drumPanels);
			if (!gc.getDrumParts().get(i).isMuted()) {
				ip.setSequenceTrack(trackCounter);
				((PartExt) drumParts.get(i)).setTrackNumber(trackCounter);
				if (COLLAPSE_DRUM_TRACKS) {
					for (int j = i + 1; j < gc.getDrumParts().size(); j++) {
						InstPanel ip2 = VibeComposerGUI.getPanelByOrder(
								gc.getDrumParts().get(j).getOrder(), VibeComposerGUI.drumPanels);
						ip2.setSequenceTrack(-1);
						((PartExt) drumParts.get(j)).setTrackNumber(-1);
					}
					break;
				}
			} else {
				ip.setSequenceTrack(-1);
				((PartExt) drumParts.get(i)).setTrackNumber(-1);
			}
			trackCounter++;
		}


		LOGGER.debug("Added parts to score..");
		Random rand = new Random(mainGeneratorSeed + 999);
		for (Object o : score.getPartList()) {
			PartExt pe = (PartExt) o;

			boolean isDrum = pe.getTitle().contains("MainDrum");
			boolean shouldRandomize = (isDrum && VibeComposerGUI.humanizeDrums.getInt() > 0)
					|| (!isDrum && VibeComposerGUI.humanizeNotes.getInt() > 0);


			if (shouldRandomize) {

				JMusicUtilsCustom.humanize(pe, rand,
						isDrum ? noteMultiplier * VibeComposerGUI.humanizeDrums.getInt() / 10000.0
								: noteMultiplier * VibeComposerGUI.humanizeNotes.getInt() / 10000.0,
						isDrum);
			}
		}

		score.setTempo(gc.getBpm());

		// write midi without log

		PrintStream dummyStream = new PrintStream(new OutputStream() {
			public void write(int b) {
				// NO-OP
			}
		});
		System.setOut(dummyStream);

		Write.midi(score, fileName);
		if (VibeComposerGUI.dconsole == null || !VibeComposerGUI.dconsole.getFrame().isVisible()) {
			System.setOut(originalStream);
		} else {
			VibeComposerGUI.dconsole.redirectOut();
		}


		// view midi
		LAST_SCORES.add(0, score);
		if (LAST_SCORES.size() > LAST_SCORES_LIMIT) {
			LAST_SCORES = LAST_SCORES.subList(0, LAST_SCORES_LIMIT);
		}

		gc.setActualArrangement(arr);
		LOGGER.info("MidiGenerator time: " + (System.currentTimeMillis() - systemTime) + " ms");
		LOGGER.info("********Viewing midi seed: " + mainGeneratorSeed + "************* ");
	}

	private List<Integer> calculateRiskyVariations(Arrangement arr, int secOrder, Section sec,
			int notesSeedOffset, Random variationGen) {
		List<Integer> riskyVariations = sec.getRiskyVariations();
		if (riskyVariations == null) {
			riskyVariations = new ArrayList<>();
			for (int i = 0; i < Section.riskyVariationNames.length; i++) {
				boolean isVariation = variationGen
						.nextInt(100) < (gc.getArrangementVariationChance()
								* Section.riskyVariationChanceMultipliers[i]);
				// generate only if not last AND next section is same type
				if (i == 0 || i == 4) {
					isVariation &= (secOrder < arr.getSections().size() - 1
							&& arr.getSections().get(secOrder + 1).getType().equals(sec.getType()));
				}
				// generate only for non-critical sections with offset > 0
				if (i == 1 || i == 2 || i == 4) {
					isVariation &= notesSeedOffset > 0;
				}

				if (i == 3) {
					isVariation = false;
				}

				// transitionFast - from offset > 0 to offset == 0
				if (i == 5) {
					isVariation &= (secOrder < arr.getSections().size() - 1
							&& arr.getSections().get(secOrder + 1).getTypeMelodyOffset() == 0
							&& notesSeedOffset > 0);
				}
				// transitionSlow - from offset > 0 to offset == 0
				if (i == 6) {
					isVariation &= (secOrder < arr.getSections().size() - 1
							&& arr.getSections().get(secOrder + 1).getTypeMelodyOffset() > 0
							&& notesSeedOffset == 0);
				}
				// transitionCut - anywhere, but only if 5 and 6 not generated
				if (i == 7) {
					isVariation &= (riskyVariations.get(5) == 0 && riskyVariations.get(6) == 0);
				}


				riskyVariations.add(isVariation ? 1 : 0);
			}
			sec.setRiskyVariations(riskyVariations);
		}
		return riskyVariations;
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
			int notesSeedOffset, List<Integer> riskyVariations, boolean sectionChordsReplaced) {
		if (!gc.getMelodyParts().isEmpty()) {
			List<Phrase> copiedPhrases = new ArrayList<>();
			Set<Integer> presences = sec.getPresence(0);
			for (int i = 0; i < gc.getMelodyParts().size(); i++) {
				MelodyPart mp = (MelodyPart) gc.getMelodyParts().get(i);
				boolean added = presences.contains(mp.getOrder());
				if (added) {
					List<int[]> usedMelodyProg = chordProgression;
					List<int[]> usedRoots = rootProgression;

					// if n-1, do not also swap melody
					if (riskyVariations.get(2) > 0 && riskyVariations.get(0) == 0
							&& !sectionChordsReplaced) {
						usedMelodyProg = melodyBasedChordProgression;
						usedRoots = melodyBasedRootProgression;
						//LOGGER.debug("Risky Variation: Melody Swap!");
					}
					List<Integer> variations = (overridden) ? sec.getVariation(0, i) : null;
					int speedSave = mp.getSpeed();
					// max speed variation
					if (riskyVariations.get(3) > 0) {
						mp.setSpeed(100);
					}
					Phrase m = fillMelodyFromPart(mp, usedMelodyProg, usedRoots, sec.getMeasures(),
							notesSeedOffset, sec, variations);
					if (riskyVariations.get(3) > 0) {
						mp.setSpeed(speedSave);
					}
					/*
					// DOUBLE melody with -12 trans, if there was a variation of +12 and it's a major part and it's the first (full) melody
					// risky variation - wacky melody transpose
					boolean laxCheck = notesSeedOffset == 0
							&& sec.getVariation(0, i).contains(Integer.valueOf(0));
					if (!riskyVariations.get(3)) {
						laxCheck &= (i == 0);
					}
					
					if (laxCheck) {
						JMusicUtilsCustom.doublePhrase(m);
					}*/
					copiedPhrases.add(m);
				} else {
					Note emptyMeasureNote = new Note(Integer.MIN_VALUE, measureLength);
					Phrase emptyPhrase = new Phrase();
					emptyPhrase.setStartTime(START_TIME_DELAY);
					emptyPhrase.add(emptyMeasureNote);
					copiedPhrases.add(emptyPhrase.copy());
				}
			}
			sec.setMelodies(copiedPhrases);
		}
	}

	private void fillOtherPartsForSection(Section sec, Arrangement arr, boolean overridden,
			List<Integer> riskyVariations, Random variationGen, int arrSeed, double measureLength) {
		// copied into empty sections
		Note emptyMeasureNote = new Note(Integer.MIN_VALUE, measureLength);
		Phrase emptyPhrase = new Phrase();
		emptyPhrase.setStartTime(START_TIME_DELAY);
		emptyPhrase.add(emptyMeasureNote);

		if (!gc.getBassParts().isEmpty()) {
			List<Phrase> copiedPhrases = new ArrayList<>();
			Set<Integer> presences = sec.getPresence(1);
			boolean added = presences.contains(gc.getBassParts().get(0).getOrder());
			if (added) {
				List<Integer> variations = (overridden) ? sec.getVariation(1, 0) : null;
				BassPart bp = gc.getBassParts().get(0);
				Phrase b = fillBassFromPart(bp, rootProgression, sec.getMeasures(), sec,
						variations);

				if (bp.isDoubleOct()) {
					b = JMusicUtilsCustom.doublePhrase(b, 12, false, -15);
					b.setStartTime(START_TIME_DELAY);
				}

				copiedPhrases.add(b);
			} else {
				copiedPhrases.add(emptyPhrase.copy());
			}
			sec.setBasses(copiedPhrases);
		}

		if (!gc.getChordParts().isEmpty()) {
			List<Phrase> copiedPhrases = new ArrayList<>();
			Set<Integer> presences = sec.getPresence(2);
			boolean useChordSlash = false;
			for (int i = 0; i < gc.getChordParts().size(); i++) {
				ChordPart cp = (ChordPart) gc.getChordParts().get(i);
				boolean added = presences.contains(cp.getOrder());
				if (added && !cp.isMuted()) {
					if (i == 0) {
						useChordSlash = true;
					}
					List<Integer> variations = (overridden) ? sec.getVariation(2, i) : null;
					Phrase c = fillChordsFromPart(cp, chordProgression, sec.getMeasures(), sec,
							variations);

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

		if (!gc.getArpParts().isEmpty()) {
			List<Phrase> copiedPhrases = new ArrayList<>();
			Set<Integer> presences = sec.getPresence(3);
			for (int i = 0; i < gc.getArpParts().size(); i++) {
				ArpPart ap = (ArpPart) gc.getArpParts().get(i);
				// if arp1 supports melody with same instrument, always introduce it in second half
				List<Integer> variations = (overridden) ? sec.getVariation(3, i) : null;
				boolean added = presences.contains(ap.getOrder());
				if (added) {
					Phrase a = fillArpFromPart(ap, chordProgression, sec.getMeasures(), sec,
							variations);

					copiedPhrases.add(a);
				} else {
					copiedPhrases.add(emptyPhrase.copy());
				}
			}
			sec.setArps(copiedPhrases);
		}

		if (!gc.getDrumParts().isEmpty()) {
			List<Phrase> copiedPhrases = new ArrayList<>();
			Set<Integer> presences = sec.getPresence(4);
			for (int i = 0; i < gc.getDrumParts().size(); i++) {
				DrumPart dp = (DrumPart) gc.getDrumParts().get(i);
				variationGen.setSeed(arrSeed + 300 + dp.getOrder());

				boolean added = presences.contains(dp.getOrder());
				if (added && !dp.isMuted()) {
					boolean sectionForcedDynamics = (sec.isClimax())
							&& variationGen.nextInt(100) < gc.getArrangementPartVariationChance();
					List<Integer> variations = (overridden) ? sec.getVariation(4, i) : null;
					Phrase d = fillDrumsFromPart(dp, chordProgression, sec.getMeasures(),
							sectionForcedDynamics, sec, variations);

					copiedPhrases.add(d);
				} else {
					copiedPhrases.add(emptyPhrase.copy());
				}
			}
			sec.setDrums(copiedPhrases);
		}
	}

	private void calculatePresencesForSection(Section sec, Random rand, Random variationGen,
			boolean overridden, List<Integer> riskyVariations, int arrSeed, int notesSeedOffset,
			boolean isPreview, int counter, Arrangement arr) {
		if (!gc.getMelodyParts().isEmpty()) {
			Set<Integer> presences = sec.getPresence(0);
			for (int i = 0; i < gc.getMelodyParts().size(); i++) {
				MelodyPart mp = (MelodyPart) gc.getMelodyParts().get(i);
				int melodyChanceMultiplier = (sec.getTypeMelodyOffset() == 0 && i == 0) ? 2 : 1;
				// temporary increase for chance of main (#1) melody
				int oldChance = sec.getMelodyChance();
				sec.setMelodyChance(Math.min(100, oldChance * melodyChanceMultiplier));
				boolean added = !mp.isMuted() && ((overridden && presences.contains(mp.getOrder()))
						|| (!overridden && rand.nextInt(100) < sec.getMelodyChance()));
				added &= gc.getArrangement().getPartInclusion(0, i, notesSeedOffset);
				if (added && !overridden) {
					sec.setPresence(0, i);
				}
				sec.setMelodyChance(oldChance);
			}
		}

		rand.setSeed(arrSeed + 10);
		variationGen.setSeed(arrSeed + 10);
		if (!gc.getBassParts().isEmpty() && !gc.getBassParts().get(0).isMuted()) {
			Set<Integer> presences = sec.getPresence(1);
			boolean added = (overridden && presences.contains(gc.getBassParts().get(0).getOrder()))
					|| (!overridden && rand.nextInt(100) < sec.getBassChance());
			added &= gc.getArrangement().getPartInclusion(1, 0, notesSeedOffset);
			if (added) {
				if (!overridden)
					sec.setPresence(1, 0);
			}
		}

		if (!gc.getChordParts().isEmpty()) {
			Set<Integer> presences = sec.getPresence(2);
			for (int i = 0; i < gc.getChordParts().size(); i++) {
				ChordPart cp = (ChordPart) gc.getChordParts().get(i);
				rand.setSeed(arrSeed + 100 + cp.getOrder());
				variationGen.setSeed(arrSeed + 100 + cp.getOrder());
				boolean added = (overridden && presences.contains(cp.getOrder()))
						|| (!overridden && rand.nextInt(100) < sec.getChordChance());
				added &= gc.getArrangement().getPartInclusion(2, i, notesSeedOffset);
				if (added && !cp.isMuted()) {
					if (!overridden)
						sec.setPresence(2, i);
				}
			}
		}

		if (!gc.getArpParts().isEmpty()) {
			Set<Integer> presences = sec.getPresence(3);
			for (int i = 0; i < gc.getArpParts().size(); i++) {
				ArpPart ap = (ArpPart) gc.getArpParts().get(i);
				rand.setSeed(arrSeed + 200 + ap.getOrder());
				variationGen.setSeed(arrSeed + 200 + ap.getOrder());
				// if arp1 supports melody with same instrument, always introduce it in second half
				boolean added = (overridden && presences.contains(ap.getOrder())) || (!overridden
						&& rand.nextInt(100) < sec.getArpChance() && i > 0 && !ap.isMuted());
				added |= (!overridden && i == 0
						&& ((isPreview || counter > ((arr.getSections().size() - 1) / 2))
								&& !ap.isMuted()));

				added &= gc.getArrangement().getPartInclusion(3, i, notesSeedOffset);
				if (added) {
					if (!overridden)
						sec.setPresence(3, i);
				}
			}
		}

		if (!gc.getDrumParts().isEmpty()) {
			Set<Integer> presences = sec.getPresence(4);
			for (int i = 0; i < gc.getDrumParts().size(); i++) {
				DrumPart dp = (DrumPart) gc.getDrumParts().get(i);
				rand.setSeed(arrSeed + 300 + dp.getOrder());

				// multiply drum chance using section note type + what drum it is
				int drumChanceMultiplier = 1;
				if (sec.getTypeMelodyOffset() == 0
						&& VibeComposerGUI.PUNCHY_DRUMS.contains(dp.getInstrument())) {
					drumChanceMultiplier = 2;
				}

				boolean added = (overridden && presences.contains(dp.getOrder())) || (!overridden
						&& rand.nextInt(100) < sec.getDrumChance() * drumChanceMultiplier);
				added &= gc.getArrangement().getPartInclusion(4, i, notesSeedOffset);
				if (added && !dp.isMuted()) {
					if (!overridden)
						sec.setPresence(4, i);
				}
			}
		}
	}

	private boolean replaceWithSectionCustomChordDurations(Section sec) {
		Pair<List<String>, List<Double>> chordsDurations = VibeComposerGUI
				.solveUserChords(sec.getCustomChords(), sec.getCustomDurations());
		if (chordsDurations == null) {
			return false;
		}

		List<int[]> mappedChords = new ArrayList<>();
		List<int[]> mappedRootChords = new ArrayList<>();
		/*chordInts.clear();
		chordInts.addAll(userChords);*/
		for (String chordString : chordsDurations.getLeft()) {
			int[] mapped = mappedChord(chordString);
			mappedChords.add(mapped);
			mappedRootChords.add(new int[] { mapped[0] });
		}
		chordProgression = mappedChords;
		rootProgression = mappedRootChords;
		progressionDurations = chordsDurations.getRight();
		sec.setSectionBeatDurations(progressionDurations);
		sec.setSectionDuration(progressionDurations.stream().mapToDouble(e -> e).sum());
		LOGGER.info("Using SECTION custom progression: "
				+ StringUtils.join(chordsDurations.getLeft(), ","));

		return true;
	}

	private void storeGlobalParts() {
		melodyParts = gc.getMelodyParts();
		bassParts = gc.getBassParts();
		chordParts = gc.getChordParts();
		arpParts = gc.getArpParts();
		drumParts = gc.getDrumParts();

	}

	private void restoreGlobalPartsToGuiConfig() {
		gc.setMelodyParts(melodyParts);
		gc.setBassParts(bassParts);
		gc.setChordParts(chordParts);
		gc.setArpParts(arpParts);
		gc.setDrumParts(drumParts);
	}

	private boolean replaceGuiConfigInstParts(Section sec) {
		boolean needsReplace = false;
		if (sec.getMelodyParts() != null) {
			gc.setMelodyParts(sec.getMelodyParts());
			needsReplace = true;
		}
		if (sec.getBassParts() != null) {
			gc.setBassParts(sec.getBassParts());
			needsReplace = true;
		}
		if (sec.getChordParts() != null) {
			gc.setChordParts(sec.getChordParts());
			needsReplace = true;
		}
		if (sec.getArpParts() != null) {
			gc.setArpParts(sec.getArpParts());
			needsReplace = true;
		}
		if (sec.getDrumParts() != null) {
			gc.setDrumParts(sec.getDrumParts());
			needsReplace = true;
		}
		return needsReplace;
	}

	private void replaceFirstChordForTwoFiveOne(int transToSet) {
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

		LOGGER.debug("Replaced FIRST");
	}

	private boolean replaceLastChordsForTwoFiveOne(int transToSet) {
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
		//if (transToSet != -2) {
		altChordProgression.set(size - 2, dm);
		altRootProgression.set(size - 2, Arrays.copyOf(dm, 1));
		//}

		altChordProgression.set(size - 1, g7);
		altRootProgression.set(size - 1, Arrays.copyOf(g7, 1));
		chordProgression = altChordProgression;
		rootProgression = altRootProgression;

		LOGGER.debug("Replaced LAST");
		return true;

	}

	private int generateKeyChange(List<int[]> chords, int arrSeed) {
		int transToSet = 0;
		if (modTrans == 0) {
			KeyChangeType chg = gc.getKeyChangeType();
			switch (chg) {
			case PIVOT:
				transToSet = pivotKeyChange(chords);
				break;
			case DIRECT:
				transToSet = directKeyChange(arrSeed);
				break;
			case TWOFIVEONE:
				transToSet = twoFiveOneKeyChange(arrSeed);
				break;
			default:
				break;
			}
		} else {
			if (gc.getKeyChangeType() == KeyChangeType.TWOFIVEONE) {
				transToSet = -1 * modTrans;
			}
		}

		return transToSet;
	}

	private int twoFiveOneKeyChange(int arrSeed) {
		// Dm -> Em, Am, or Am octave below
		int[] transChoices = { 5, 2 };
		Random rand = new Random(arrSeed);
		return transChoices[rand.nextInt(transChoices.length)];
	}

	private int pivotKeyChange(List<int[]> chords) {
		int transToSet = 0;
		List<String> allCurrentChordsAsBasic = MidiUtils.getBasicChordStringsFromRoots(chords);
		String baseChordLast = allCurrentChordsAsBasic.get(allCurrentChordsAsBasic.size() - 1);
		String baseChordFirst = allCurrentChordsAsBasic.get(0);
		transToSet = 0;
		Pair<String, String> test = Pair.of(baseChordFirst, baseChordLast);
		for (Integer trans : MidiUtils.modulationMap.keySet()) {
			boolean hasValue = MidiUtils.modulationMap.get(trans).contains(test);
			if (hasValue) {
				transToSet = (trans < -4) ? (trans + 12) : trans;
				LOGGER.debug("Trans up by: " + transToSet);
				break;
			}
		}
		if (transToSet == 0) {
			LOGGER.info("Pivot chord not found between last and first chord!");
		}
		return transToSet;
	}

	private int directKeyChange(int arrSeed) {
		Random rand = new Random(arrSeed);
		int[] pool = new int[] { -4, -3, 3, 4 };
		return pool[rand.nextInt(pool.length)];

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

	private void processUserMelody(Phrase userMelody) {
		if (!chordMelodyMap1.isEmpty() || !(userChords == null || userChords.isEmpty())) {
			return;
		}

		int chordCounter = 0;
		double separatorValue = (gc.isDoubledDurations()) ? Durations.WHOLE_NOTE * 2
				: Durations.WHOLE_NOTE;
		double chordSeparator = separatorValue;
		Vector<Note> noteList = userMelody.getNoteList();
		if (!chordMelodyMap1.containsKey(Integer.valueOf(0))) {
			chordMelodyMap1.put(Integer.valueOf(0), new ArrayList<>());
		}
		double rhythmCounter = 0;
		List<Double> progDurations = new ArrayList<>();
		progDurations.add(separatorValue);
		for (Note n : noteList) {
			LOGGER.debug("Rhythm counter: " + rhythmCounter);
			if (rhythmCounter >= chordSeparator - DBL_ERR) {
				LOGGER.debug("NEXT CHORD!");
				chordSeparator += separatorValue;
				chordCounter++;
				progDurations.add(separatorValue);
				if (!chordMelodyMap1.containsKey(Integer.valueOf(chordCounter))) {
					chordMelodyMap1.put(Integer.valueOf(chordCounter), new ArrayList<>());
				}
			}
			chordMelodyMap1.get(Integer.valueOf(chordCounter)).add(n);
			rhythmCounter += n.getRhythmValue();
		}
		LOGGER.debug("Rhythm counter end: " + rhythmCounter);
		while (rhythmCounter >= chordSeparator + DBL_ERR) {
			LOGGER.debug("NEXT CHORD!");
			chordSeparator += separatorValue;
			chordCounter++;
			progDurations.add(separatorValue);
			if (!chordMelodyMap1.containsKey(Integer.valueOf(chordCounter))) {
				chordMelodyMap1.put(Integer.valueOf(chordCounter), new ArrayList<>());
			}
			chordMelodyMap1.get(Integer.valueOf(chordCounter))
					.add(noteList.get(noteList.size() - 1));
		}
		LOGGER.info("Processed melody, chords: " + (chordCounter + 1));
		List<String> chordStrings = getChordsFromMelodyPitches(1, chordMelodyMap1,
				MidiUtils.freqMap);
		/*List<String> spicyChordStrings = getChordsFromMelodyPitches(1, chordMelodyMap1,
				MidiUtils.freqMap);
		for (int i = 0; i < spicyChordStrings.size(); i++) {
			if (chordStrings.get(i).charAt(0) == spicyChordStrings.get(i).charAt(0)) {
				chordStrings.set(i, spicyChordStrings.get(i));
			}
		}*/

		populateMelodyBasedProgression(chordStrings, 0, chordMelodyMap1.keySet().size());
		progressionDurations = progDurations;
		chordInts = chordStrings;
	}

	private void populateMelodyBasedProgression(List<String> chordStrings, int start, int end) {
		List<int[]> altChordProg = new ArrayList<>();

		for (int i = 0; i < start; i++) {
			melodyBasedRootProgression
					.add(Arrays.copyOf(rootProgression.get(i), rootProgression.get(i).length));
			altChordProg
					.add(Arrays.copyOf(chordProgression.get(i), chordProgression.get(i).length));
		}
		for (int i = start; i < end; i++) {
			int[] mappedChord = MidiUtils.mappedChord(chordStrings.get(i));
			altChordProg.add(mappedChord);
			melodyBasedRootProgression.add(Arrays.copyOf(mappedChord, mappedChord.length));
		}
		for (int i = end; i < chordStrings.size(); i++) {
			melodyBasedRootProgression
					.add(Arrays.copyOf(rootProgression.get(i), rootProgression.get(i).length));
			altChordProg
					.add(Arrays.copyOf(chordProgression.get(i), chordProgression.get(i).length));
		}

		melodyBasedChordProgression = squishChordProgression(altChordProg,
				gc.isSpiceFlattenBigChords(), gc.getRandomSeed(),
				gc.getChordGenSettings().getFlattenVoicingChance());


		chordProgression = melodyBasedChordProgression;
		rootProgression = melodyBasedRootProgression;
		LOGGER.info(StringUtils.join(chordStrings, ","));

	}

	protected Phrase fillMelodyFromPart(MelodyPart mp, List<int[]> actualProgression,
			List<int[]> generatedRootProgression, int measures, int notesSeedOffset, Section sec,
			List<Integer> variations) {
		Phrase melodyPhrase = new Phrase();
		Vector<Note> skeletonNotes = null;
		if (userMelody != null) {
			skeletonNotes = userMelody.copy().getNoteList();
		} else {
			if (false) {
				LOGGER.info("OLD MELODY ALGO");
				skeletonNotes = generateMelodySkeletonFromChords(mp, actualProgression,
						generatedRootProgression, measures, notesSeedOffset, sec, variations);
			} else {
				skeletonNotes = generateMelodyBlockSkeletonFromChords(mp, actualProgression,
						generatedRootProgression, measures, notesSeedOffset, sec, variations);
			}

		}
		Map<Integer, List<Note>> fullMelodyMap = convertMelodySkeletonToFullMelody(mp,
				progressionDurations, sec, skeletonNotes, notesSeedOffset, actualProgression,
				measures);

		for (int i = 0; i < generatedRootProgression.size() * measures; i++) {
			for (int j = 0; j < MidiUtils.MINOR_CHORDS.size(); j++) {
				int[] minorChord = MidiUtils.mappedChord(MidiUtils.MINOR_CHORDS.get(j));
				boolean isMinor = Arrays.equals(MidiUtils.normalizeChord(minorChord),
						MidiUtils.normalizeChord(
								generatedRootProgression.get(i % generatedRootProgression.size())));
				if (isMinor) {
					MidiUtils.transposeNotes(fullMelodyMap.get(i), ScaleMode.IONIAN.noteAdjustScale,
							MidiUtils.adjustScaleByChord(ScaleMode.IONIAN.noteAdjustScale,
									minorChord));
					LOGGER.info("Transposing melody to match minor chord! Chord#: " + i);
					break;
				}
			}
		}

		if (mp.getOrder() == 1) {
			List<Integer> notePattern = new ArrayList<>();
			Map<Integer, List<Integer>> notePatternMap = patternsFromNotes(fullMelodyMap);
			notePatternMap.keySet().forEach(e -> notePattern.addAll(notePatternMap.get(e)));
			melodyNotePatternMap = notePatternMap;
			melodyNotePattern = notePattern;
			//LOGGER.debug(StringUtils.join(melodyNotePattern, ","));
		}
		Vector<Note> noteList = new Vector<>();
		fullMelodyMap.keySet().forEach(e -> noteList.addAll(fullMelodyMap.get(e)));
		melodyPhrase.addNoteList(noteList, true);
		swingPhrase(melodyPhrase, mp.getSwingPercent(), Durations.QUARTER_NOTE);

		applyNoteLengthMultiplier(melodyPhrase.getNoteList(), mp.getNoteLengthMultiplier());
		processSectionTransition(sec, melodyPhrase.getNoteList(),
				progressionDurations.stream().mapToDouble(e -> e).sum(), 0.25, 0.25, 0.9);

		if (gc.getScaleMode() != ScaleMode.IONIAN) {
			MidiUtils.transposePhrase(melodyPhrase, ScaleMode.IONIAN.noteAdjustScale,
					gc.getScaleMode().noteAdjustScale);
		}
		if (modTrans != 0) {
			Mod.transpose(melodyPhrase, modTrans);
		}

		melodyPhrase.setStartTime(START_TIME_DELAY);
		return melodyPhrase;
	}


	protected Phrase fillBassFromPart(BassPart bp, List<int[]> generatedRootProgression,
			int measures, Section sec, List<Integer> variations) {
		boolean genVars = variations == null;

		double[] durationPool = new double[] { Durations.SIXTEENTH_NOTE, Durations.EIGHTH_NOTE,
				Durations.QUARTER_NOTE, Durations.DOTTED_QUARTER_NOTE, Durations.HALF_NOTE,
				Durations.EIGHTH_NOTE + Durations.HALF_NOTE, Durations.DOTTED_HALF_NOTE,
				Durations.WHOLE_NOTE };

		int[] durationWeights = new int[] { 5, 25, 45, 55, 75, 85, 95, 100 };

		int seed = bp.getPatternSeed();

		Phrase bassPhrase = new Phrase();
		int volMultiplier = (gc.isScaleMidiVelocityInArrangement()) ? sec.getVol(1) : 100;
		int minVel = multiplyVelocity(bp.getVelocityMin(), volMultiplier, 0, 1);
		int maxVel = multiplyVelocity(bp.getVelocityMax(), volMultiplier, 1, 0);
		Random rhythmPauseGenerator = new Random(seed + sec.getTypeMelodyOffset());
		Random noteVariationGenerator = new Random(seed + sec.getTypeMelodyOffset() + 2);

		double rootAverage = 0;
		for (int i = 0; i < generatedRootProgression.size(); i++) {
			rootAverage += generatedRootProgression.get(i)[0];
		}
		rootAverage /= generatedRootProgression.size();

		List<int[]> squishedChords = new ArrayList<>();
		for (int i = 0; i < generatedRootProgression.size(); i++) {
			double dist = generatedRootProgression.get(i)[0] - rootAverage;
			if (Math.abs(dist) < 5 - DBL_ERR) {
				squishedChords.add(generatedRootProgression.get(i));
			} else {
				int adjustment = dist > 0 ? -12 : 12;
				squishedChords
						.add(MidiUtils.transposeChord(generatedRootProgression.get(i), adjustment));
				rootAverage += (adjustment / (double) generatedRootProgression.size());

			}
		}

		boolean rhythmPauses = false;
		double maxDur = progressionDurations.stream().mapToDouble(e -> e).sum();
		for (int i = 0; i < measures; i++) {
			int extraSeed = 0;
			double totalDur = 0.0;
			for (int chordIndex = 0; chordIndex < squishedChords.size(); chordIndex++) {
				if (genVars && (chordIndex == 0) && sec.getTypeMelodyOffset() > 0) {
					variations = fillVariations(sec, bp, variations, 1);
				}

				if ((variations != null) && (chordIndex == 0)) {
					for (Integer var : variations) {
						if (i == measures - 1) {
							LOGGER.debug("Bass #1 variation: " + var);
						}

						switch (var) {
						case 0:
							extraSeed = 100;
							break;
						case 1:
							rhythmPauses = true;
							break;
						default:
							throw new IllegalArgumentException("Too much variation!");
						}
					}
				}


				Random bassDynamics = new Random(gc.getRandomSeed());
				int velSpace = maxVel - minVel;
				if (bp.isUseRhythm()) {
					int seedCopy = seed;
					seedCopy += extraSeed;
					if (bp.isAlternatingRhythm()) {
						seedCopy += (chordIndex % 2);
					}
					Rhythm bassRhythm = new Rhythm(seedCopy, progressionDurations.get(chordIndex),
							durationPool, durationWeights);
					int counter = 0;
					List<Integer> pattern = (bp.isMelodyPattern() && (melodyNotePatternMap != null))
							? new ArrayList<>(melodyNotePatternMap.get(chordIndex))
							: null;
					List<Double> durations = (pattern != null)
							? getSustainedDurationsFromPattern(pattern, Durations.SIXTEENTH_NOTE)
							: bassRhythm.regenerateDurations(4,
									MidiGenerator.Durations.SIXTEENTH_NOTE / 2.0);
					for (Double dur : durations) {

						int randomNote = 0;
						// note variation for short notes, low chance, only after first
						int noteVaryChance = sec.isTransition()
								? adjustChanceParamForTransition(bp.getNoteVariation(), sec,
										chordIndex, squishedChords.size(), 40, 0.25, false)
								: bp.getNoteVariation();
						if (counter > 0 && dur < (Durations.QUARTER_NOTE + DBL_ERR)
								&& noteVariationGenerator.nextInt(100) < noteVaryChance
								&& squishedChords.get(chordIndex).length > 1) {
							randomNote = noteVariationGenerator
									.nextInt(squishedChords.get(chordIndex).length - 1) + 1;
						}

						int pitch = (rhythmPauses && dur < Durations.QUARTER_NOTE
								&& rhythmPauseGenerator.nextInt(100) < 33) ? Integer.MIN_VALUE
										: squishedChords.get(chordIndex)[randomNote];
						if (pattern != null && counter == 0 && pattern.get(0) < 1) {
							pitch = Integer.MIN_VALUE;
						}

						bassPhrase.addNote(
								new Note(pitch, dur, bassDynamics.nextInt(velSpace) + minVel));
						counter++;
					}
				} else {
					bassPhrase.addNote(new Note(squishedChords.get(chordIndex)[0],
							progressionDurations.get(chordIndex),
							bassDynamics.nextInt(velSpace) + minVel));
				}
			}
		}
		if (gc.getScaleMode() != ScaleMode.IONIAN) {
			MidiUtils.transposePhrase(bassPhrase, ScaleMode.IONIAN.noteAdjustScale,
					gc.getScaleMode().noteAdjustScale);
		}
		Mod.transpose(bassPhrase, -24 + bp.getTranspose() + modTrans);
		bassPhrase.setStartTime(START_TIME_DELAY);
		if (genVars && variations != null) {
			sec.setVariation(1, 0, variations);
		}
		return bassPhrase;

	}

	protected Phrase fillChordsFromPart(ChordPart cp, List<int[]> actualProgression, int measures,
			Section sec, List<Integer> variations) {
		boolean genVars = variations == null;

		int orderSeed = cp.getPatternSeed() + cp.getOrder();
		Phrase phr = new Phrase();
		List<Chord> chords = new ArrayList<>();
		Random variationGenerator = new Random(
				gc.getArrangement().getSeed() + cp.getOrder() + sec.getTypeSeedOffset());
		Random flamGenerator = new Random(orderSeed + 30);
		// chord strum
		double flamming = 0.0;
		if (gc.getChordGenSettings().isUseStrum()) {
			flamming = (noteMultiplier * (double) cp.getStrum()) / 1000.0;
			//LOGGER.debug("Chord strum CUSTOM! " + cp.getStrum() + ", flamming: " + flamming);
		}


		int stretch = cp.getChordNotesStretch();
		List<Integer> fillPattern = cp.getChordSpanFill()
				.getPatternByLength(actualProgression.size(), cp.isFillFlip());

		int volMultiplier = (gc.isScaleMidiVelocityInArrangement()) ? sec.getVol(2) : 100;
		int minVel = multiplyVelocity(cp.getVelocityMin(), volMultiplier, 0, 1);
		int maxVel = multiplyVelocity(cp.getVelocityMax(), volMultiplier, 1, 0);

		List<Integer> chordVelocityPattern = new ArrayList<>();
		if (cp.getCustomVelocities() != null
				&& cp.getCustomVelocities().size() >= cp.getHitsPerPattern()) {
			int multiplier = gc.isScaleMidiVelocityInArrangement() ? sec.getVol(3) : 100;
			for (int k = 0; k < cp.getHitsPerPattern(); k++) {
				chordVelocityPattern
						.add(multiplyVelocity(cp.getCustomVelocities().get(k), multiplier, 0, 1));
			}
			chordVelocityPattern = MidiUtils.intersperse(null, cp.getChordSpan() - 1,
					chordVelocityPattern);
		}

		for (int i = 0; i < measures; i++) {
			Random transitionGenerator = new Random(orderSeed);
			int extraTranspose = 0;
			boolean ignoreChordSpanFill = false;
			boolean skipSecondNote = false;
			int chordSpanPart = 0;
			int skipNotes = 0;
			// fill chords
			for (int chordIndex = 0; chordIndex < actualProgression.size(); chordIndex++) {
				if (genVars && (chordIndex == 0)) {
					variations = fillVariations(sec, cp, variations, 2);
				}

				if ((variations != null) && (chordIndex == 0)) {
					for (Integer var : variations) {
						if (i == measures - 1) {
							//LOGGER.debug("Chord #" + cp.getOrder() + " variation: " + var);
						}

						switch (var) {
						case 0:
							extraTranspose = 12;
							break;
						case 1:
							ignoreChordSpanFill = true;
							break;
						case 2:
							if (stretch < 6) {
								int randomStretchAdd = variationGenerator.nextInt(6 - stretch) + 1;
								stretch += randomStretchAdd;
							}
							break;
						case 3:
							skipSecondNote = true;
							break;
						case 4:
							switch (cp.getStrumType()) {
							case ARP_D:
								flamming = Durations.EIGHTH_NOTE;
								break;
							case ARP_U:
								flamming = Durations.EIGHTH_NOTE;
								break;
							case HUMAN:
								flamming = Durations.SIXTEENTH_NOTE / 4;
								break;
							case HUMAN_D:
								flamming = Durations.SIXTEENTH_NOTE / 4;
								break;
							case HUMAN_U:
								flamming = Durations.SIXTEENTH_NOTE / 4;
								break;
							case RAND:
								flamming = Durations.SIXTEENTH_NOTE;
								break;
							case RAND_D:
								flamming = Durations.SIXTEENTH_NOTE;
								break;
							case RAND_U:
								flamming = Durations.SIXTEENTH_NOTE;
								break;
							case RAND_WU:
								flamming = Durations.SIXTEENTH_NOTE;
								break;
							default:
								throw new IllegalArgumentException("Unknown StrumType!");
							}
							break;
						default:
							throw new IllegalArgumentException("Too much variation!");
						}
					}
				}

				flamGenerator.setSeed(orderSeed + 30 + (chordIndex % 2));
				Chord c = Chord.EMPTY(progressionDurations.get(chordIndex));

				Random velocityGenerator = new Random(orderSeed + chordIndex);
				c.setStrumType(cp.getStrumType());

				boolean transition = transitionGenerator.nextInt(100) < cp.getTransitionChance();
				int transChord = (transitionGenerator.nextInt(100) < cp.getTransitionChance())
						? (chordIndex + 1) % actualProgression.size()
						: chordIndex;

				if (!ignoreChordSpanFill) {
					if (fillPattern.get(chordIndex) < 1) {
						chords.add(c);
						skipNotes = 0;
						chordSpanPart = (chordSpanPart + 1) % cp.getChordSpan();
						continue;
					}
				}

				c.setDurationRatio(cp.getNoteLengthMultiplier() / 100.0);

				int[] mainChordNotes = actualProgression.get(chordIndex);
				int[] transChordNotes = actualProgression.get(transChord);

				//only skip if not already an interval (2 notes)
				if (skipSecondNote) {
					if (mainChordNotes.length > 2) {
						int[] newMainChordNotes = new int[mainChordNotes.length - 1];
						for (int m = 0; m < mainChordNotes.length; m++) {
							if (m == 1)
								continue;
							int index = (m > 1) ? m - 1 : m;
							newMainChordNotes[index] = mainChordNotes[m];

						}
						mainChordNotes = newMainChordNotes;
					}
					if (transChordNotes.length > 2) {
						int[] newTransChordNotes = new int[transChordNotes.length - 1];
						for (int m = 0; m < transChordNotes.length; m++) {
							if (m == 1)
								continue;
							int index = (m > 1) ? m - 1 : m;
							newTransChordNotes[index] = transChordNotes[m];
						}

						transChordNotes = newTransChordNotes;
					}
				}
				boolean stretchOverride = (sec.isTransition()
						&& chordIndex >= actualProgression.size() - 2);

				if (stretchOverride || cp.isStretchEnabled()) {
					Integer stretchAmount = (stretchOverride)
							? (sec.getTransitionType() == 5 ? 7 : 2)
							: stretch;
					mainChordNotes = convertChordToLength(mainChordNotes,
							(stretchAmount != null) ? stretchAmount : mainChordNotes.length);
					transChordNotes = convertChordToLength(transChordNotes,
							(stretchAmount != null) ? stretchAmount : transChordNotes.length);
				}


				c.setTranspose(extraTranspose);
				c.setNotes(mainChordNotes);

				// for transition:
				double splitTime = progressionDurations.get(chordIndex)
						* (gc.getChordGenSettings().isUseSplit() ? cp.getTransitionSplit()
								: DEFAULT_CHORD_SPLIT)
						/ 1000.0;
				//LOGGER.debug("Split time: " + splitTime);
				PatternJoinMode joinMode = cp.getPatternJoinMode();
				int stretchedByNote = (joinMode == PatternJoinMode.JOIN) ? 1 : 0;

				List<Integer> pattern = null;
				List<Integer> nextPattern = null;
				List<Integer> velocityPattern = null;
				if (cp.getPattern() == RhythmPattern.MELODY1 && melodyNotePatternMap != null) {
					pattern = new ArrayList<>(melodyNotePatternMap.get(chordIndex));
				} else {
					List<Integer> patternCopy = cp.getFinalPatternCopy();
					List<Integer> patternSub = patternCopy.subList(0, cp.getHitsPerPattern());
					pattern = MidiUtils.intersperse(-1, cp.getChordSpan() - 1, patternSub);
					pattern = partOfListClean(chordSpanPart, cp.getChordSpan(), pattern);
					if (cp.getChordSpan() > 1 && joinMode != PatternJoinMode.NOJOIN) {
						if (chordSpanPart < cp.getChordSpan() - 1) {
							nextPattern = MidiUtils.intersperse(-1, cp.getChordSpan() - 1,
									patternSub);
							nextPattern = partOfListClean(chordSpanPart + 1, cp.getChordSpan(),
									nextPattern);
						}
					}
					velocityPattern = !chordVelocityPattern.isEmpty()
							? partOfList(chordSpanPart, cp.getChordSpan(), chordVelocityPattern)
							: null;
				}
				if (cp.isPatternFlip()) {
					for (int p = 0; p < pattern.size(); p++) {
						pattern.set(p, 1 - pattern.get(p));
					}
				}
				double duration = (cp.getPattern() == RhythmPattern.MELODY1
						&& melodyNotePatternMap != null) ? Durations.SIXTEENTH_NOTE
								: Durations.WHOLE_NOTE / pattern.size();
				double durationNow = 0;
				int nextP = -1;

				int p = 0;
				while (durationNow + DBL_ERR < progressionDurations.get(chordIndex)) {

					//LOGGER.debug("Duration counter: " + durationCounter);
					Chord cC = Chord.copy(c);

					cC.setVelocity(velocityPattern != null
							? velocityPattern.get(p % velocityPattern.size())
							: (velocityGenerator.nextInt(maxVel - minVel) + minVel));
					// less plucky
					//cC.setDurationRatio(cC.getDurationRatio() + (1 - cC.getDurationRatio()) / 2);
					if (pattern.get(p) < 1 || (p <= nextP && stretchedByNote == 1)
							|| skipNotes > 0) {
						if (skipNotes > 0) {
							skipNotes--;
						}
						cC.setNotes(new int[] { Integer.MIN_VALUE });
					} else if (transition && durationNow >= splitTime) {
						cC.setNotes(transChordNotes);
					}

					if (durationNow + duration - DBL_ERR > progressionDurations.get(chordIndex)) {
						double fillerDuration = progressionDurations.get(chordIndex) - durationNow;
						cC.setRhythmValue(fillerDuration);
						if (fillerDuration < FILLER_NOTE_MIN_DURATION) {
							cC.setNotes(new int[] { Integer.MIN_VALUE });
						}
					} else {
						cC.setRhythmValue(duration);
					}

					int durMultiplier = 1;
					boolean joinApplicable = (pattern.get(p) == 1) && (p >= nextP);
					if (joinApplicable) {
						nextP = p + 1;
						while (nextP < pattern.size()) {
							if (pattern.get(nextP) == stretchedByNote || pattern.get(nextP) == -1) {
								durMultiplier++;
							} else {
								break;
							}
							nextP++;
						}
					}
					joinApplicable &= (joinMode != PatternJoinMode.NOJOIN);
					//LOGGER.debug("Dur multiplier be4: " + durMultiplier);
					// chord to spill by 15%
					double durationCapMax = (cp.getStrum() > 750) ? 1.15 : 5.00;
					double durationCap = durationCapMax
							* (progressionDurations.get(chordIndex) - durationNow);
					double durationRatioCap = durationCap / cC.getRhythmValue();

					if (nextP >= pattern.size() && cp.getChordSpan() > 1 && nextPattern != null) {
						skipNotes = countStartingValueInList(stretchedByNote, nextPattern);
						durMultiplier += skipNotes;
						//LOGGER.debug("Dur multiplier added: " + durMultiplier);
					}

					cC.setDurationRatio(Math.min(durationRatioCap, Math.min(durMultiplier,
							cC.getDurationRatio() * (joinApplicable ? durMultiplier : 1.0))));
					//LOGGER.debug("Dur multiplier after: " + cC.getDurationRatio());
					cC.setFlam(flamming);
					cC.makeAndStoreNotesBackwards(flamGenerator);
					chords.add(cC);
					durationNow += duration;
					p = (p + 1) % pattern.size();
				}
				chordSpanPart++;
				if (chordSpanPart >= cp.getChordSpan()) {
					chordSpanPart = 0;
				}
			}
		}
		MidiUtils.addChordsToPhrase(phr, chords, flamming);
		// transpose
		int extraTranspose = gc.getChordGenSettings().isUseTranspose() ? cp.getTranspose() : 0;
		if (gc.getScaleMode() != ScaleMode.IONIAN) {
			MidiUtils.transposePhrase(phr, ScaleMode.IONIAN.noteAdjustScale,
					gc.getScaleMode().noteAdjustScale);
		}
		Mod.transpose(phr, -12 + extraTranspose + modTrans);


		processSectionTransition(sec, phr.getNoteList(),
				progressionDurations.stream().mapToDouble(e -> e).sum(), 0.25, 0.15, 0.9);

		// delay
		double additionalDelay = 0;
		if (gc.getChordGenSettings().isUseDelay()) {
			additionalDelay = ((noteMultiplier * cp.getDelay()) / 1000.0);
		}
		phr.setStartTime(START_TIME_DELAY + additionalDelay);


		if (genVars && variations != null) {
			sec.setVariation(2, getAbsoluteOrder(2, cp), variations);
		}
		return phr;
	}

	private int countStartingValueInList(int stretchedByNote, List<Integer> nextPattern) {
		int counter = 0;
		while (nextPattern.size() > counter) {
			if (nextPattern.get(counter) == stretchedByNote || nextPattern.get(counter) == -1) {
				counter++;
			} else {
				break;
			}
		}
		return counter;
	}

	protected Phrase fillArpFromPart(ArpPart ap, List<int[]> actualProgression, int measures,
			Section sec, List<Integer> variations) {
		boolean genVars = variations == null;
		Phrase arpPhrase = new Phrase();

		ArpPart apClone = (ArpPart) ap.clone();

		Map<String, List<Integer>> arpMap = generateArpMap(ap.getPatternSeed(),
				ap.equals(gc.getArpParts().get(0)), ap);

		List<Integer> arpPattern = arpMap.get(ARP_PATTERN_KEY);
		List<Integer> arpOctavePattern = arpMap.get(ARP_OCTAVE_KEY);
		List<Integer> arpPausesPattern = arpMap.get(ARP_PAUSES_KEY);

		List<Integer> arpVelocityPattern = new ArrayList<>();
		if (ap.getCustomVelocities() != null
				&& ap.getCustomVelocities().size() >= ap.getHitsPerPattern()) {
			int multiplier = gc.isScaleMidiVelocityInArrangement() ? sec.getVol(3) : 100;
			for (int k = 0; k < ap.getHitsPerPattern(); k++) {
				arpVelocityPattern
						.add(multiplyVelocity(ap.getCustomVelocities().get(k), multiplier, 0, 1));
			}
			arpVelocityPattern = MidiUtils.intersperse(null, ap.getChordSpan() - 1,
					arpVelocityPattern);
		}

		List<Boolean> directions = null;


		// TODO: divide
		int repeatedArpsPerChord = ap.getHitsPerPattern() * ap.getPatternRepeat();
		int swingPercentAmount = (repeatedArpsPerChord == 4 || repeatedArpsPerChord == 8)
				? gc.getMaxArpSwing()
				: 50;

		/*if (melodic) {
			repeatedArpsPerChord /= ap.getChordSpan();
		}*/

		int volMultiplier = (gc.isScaleMidiVelocityInArrangement()) ? sec.getVol(3) : 100;
		int minVel = multiplyVelocity(ap.getVelocityMin(), volMultiplier, 0, 1);
		int maxVel = multiplyVelocity(ap.getVelocityMax(), volMultiplier, 1, 0);

		boolean fillLastBeat = false;
		List<Integer> fillPattern = ap.getChordSpanFill()
				.getPatternByLength(actualProgression.size(), ap.isFillFlip());
		for (int i = 0; i < measures; i++) {
			int chordSpanPart = 0;
			int extraTranspose = 0;
			boolean ignoreChordSpanFill = false;
			boolean forceRandomOct = false;

			Random velocityGenerator = new Random(ap.getPatternSeed());
			Random exceptionGenerator = new Random(ap.getPatternSeed() + 1);
			for (int j = 0; j < actualProgression.size(); j++) {
				if (genVars && (j == 0)) {
					List<Double> chanceMultipliers = sec.isTransition()
							? Arrays.asList(new Double[] { 1.0, 1.0, 1.0, 2.0, 1.0 })
							: null;
					variations = fillVariations(sec, ap, variations, 3, chanceMultipliers);
				}

				if ((variations != null) && (j == 0)) {
					for (Integer var : variations) {
						if (i == measures - 1) {
							//LOGGER.debug("Arp #" + ap.getOrder() + " variation: " + var);
						}

						switch (var) {
						case 0:
							extraTranspose = 12;
							break;
						case 1:
							ignoreChordSpanFill = true;
							break;
						case 2:
							forceRandomOct = true;
							break;
						case 3:
							fillLastBeat = true;
							break;
						case 4:
							if (directions == null) {
								directions = generateMelodyDirectionsFromChordProgression(
										actualProgression, true);
							}
							break;
						default:
							throw new IllegalArgumentException("Too much variation!");
						}
					}
				}

				double chordDurationArp = (ap.getPattern() == RhythmPattern.MELODY1
						&& melodyNotePatternMap != null) ? Durations.SIXTEENTH_NOTE
								: Durations.WHOLE_NOTE / ((double) repeatedArpsPerChord);
				int[] chord = convertChordToLength(actualProgression.get(j),
						ap.getChordNotesStretch(), ap.isStretchEnabled());
				if (directions != null) {
					ArpPattern pat = (directions.get(j)) ? ArpPattern.UP : ArpPattern.DOWN;
					arpPattern = pat.getPatternByLength(ap.getHitsPerPattern(), chord.length,
							ap.getPatternRepeat(), ap.getArpPatternRotate());
					arpPattern = MidiUtils.intersperse(0, ap.getChordSpan() - 1, arpPattern);
				} else {
					if (ap.getArpPattern() != ArpPattern.RANDOM) {
						arpPattern = ap.getArpPattern().getPatternByLength(ap.getHitsPerPattern(),
								chord.length, ap.getPatternRepeat(), ap.getArpPatternRotate());
						arpPattern = MidiUtils.intersperse(0, ap.getChordSpan() - 1, arpPattern);
					}
				}

				double durationNow = 0;

				// reset every 2
				if (j % 2 == 0) {
					exceptionGenerator.setSeed(ap.getPatternSeed() + 1);
				}
				List<Integer> pitchPatternSpanned = partOfList(chordSpanPart, ap.getChordSpan(),
						arpPattern);
				List<Integer> octavePatternSpanned = partOfList(chordSpanPart, ap.getChordSpan(),
						arpOctavePattern);
				List<Integer> pausePatternSpanned = (ap.getPattern() == RhythmPattern.MELODY1
						&& melodyNotePatternMap != null)
								? new ArrayList<>(melodyNotePatternMap.get(j))
								: partOfList(chordSpanPart, ap.getChordSpan(), arpPausesPattern);
				List<Integer> velocityPatternSpanned = !arpVelocityPattern.isEmpty()
						? partOfList(chordSpanPart, ap.getChordSpan(), arpVelocityPattern)
						: null;


				int p = 0;
				while (durationNow + DBL_ERR < progressionDurations.get(j)) {
					int velocity = velocityPatternSpanned != null
							? velocityPatternSpanned.get(p % velocityPatternSpanned.size())
							: (velocityGenerator.nextInt(maxVel - minVel) + minVel);

					Integer patternNum = pitchPatternSpanned.get(p);

					int pitch = chord[patternNum % chord.length];
					if (gc.isUseOctaveAdjustments() || forceRandomOct) {
						int octaveAdjustGenerated = octavePatternSpanned.get(p);
						int octaveAdjustmentFromPattern = (patternNum < 2) ? -12
								: ((patternNum < 6) ? 0 : 12);
						pitch += octaveAdjustmentFromPattern + octaveAdjustGenerated;
					}

					boolean isPause = pausePatternSpanned.get(p % pausePatternSpanned.size()) == 0;
					if (ap.isPatternFlip()) {
						isPause = !isPause;
					}

					pitch += extraTranspose;
					if (!fillLastBeat || j < actualProgression.size() - 1) {
						if (isPause) {
							pitch = Integer.MIN_VALUE;
						}
						if (!ignoreChordSpanFill) {
							if (fillPattern.get(j) < 1) {
								pitch = Integer.MIN_VALUE;
							}
						}
					}
					double usedDuration = chordDurationArp;
					if (durationNow + usedDuration - DBL_ERR > progressionDurations.get(j)) {
						usedDuration = progressionDurations.get(j) - durationNow;
						if (usedDuration < FILLER_NOTE_MIN_DURATION) {
							pitch = Integer.MIN_VALUE;
						}
					}
					if (exceptionGenerator.nextInt(100) < ap.getExceptionChance()) {
						double splitDuration = usedDuration / 2;
						arpPhrase.addNote(new Note(pitch, splitDuration, velocity));
						arpPhrase.addNote(new Note(pitch, splitDuration, velocity - 15));
					} else {
						arpPhrase.addNote(new Note(pitch, usedDuration, velocity));
					}
					durationNow += usedDuration;
					p = (p + 1) % repeatedArpsPerChord;
				}

				chordSpanPart++;
				if (chordSpanPart >= ap.getChordSpan()) {
					chordSpanPart = 0;
				}
			}
		}
		int extraTranspose = ARP_SETTINGS.isUseTranspose() ? ap.getTranspose() : 0;
		if (gc.getScaleMode() != ScaleMode.IONIAN) {
			MidiUtils.transposePhrase(arpPhrase, ScaleMode.IONIAN.noteAdjustScale,
					gc.getScaleMode().noteAdjustScale);
		}
		Mod.transpose(arpPhrase, -24 + extraTranspose + modTrans);

		applyNoteLengthMultiplier(arpPhrase.getNoteList(), ap.getNoteLengthMultiplier());
		processSectionTransition(sec, arpPhrase.getNoteList(),
				progressionDurations.stream().mapToDouble(e -> e).sum(), 0.25, 0.15, 0.9);
		swingPhrase(arpPhrase, swingPercentAmount, Durations.QUARTER_NOTE);
		double additionalDelay = 0;
		/*if (ARP_SETTINGS.isUseDelay()) {
			additionalDelay = (gc.getArpParts().get(i).getDelay() / 1000.0);
		}*/
		if (genVars && variations != null) {
			sec.setVariation(3, getAbsoluteOrder(3, ap), variations);
		}
		if (fillLastBeat) {
			Mod.crescendo(arpPhrase, arpPhrase.getEndTime() * 3 / 4, arpPhrase.getEndTime(),
					Math.max(minVel, 55), Math.max(maxVel, 110));
		}
		ap.setPatternShift(apClone.getPatternShift());
		//dp.setVelocityPattern(false);
		ap.setChordSpan(apClone.getChordSpan());
		ap.setHitsPerPattern(apClone.getHitsPerPattern());
		ap.setPatternRepeat(apClone.getPatternRepeat());
		arpPhrase.setStartTime(START_TIME_DELAY + additionalDelay);
		return arpPhrase;
	}


	protected Phrase fillDrumsFromPart(DrumPart dp, List<int[]> actualProgression, int measures,
			boolean sectionForcedDynamics, Section sec, List<Integer> variations) {
		boolean genVars = variations == null;
		Phrase drumPhrase = new Phrase();

		DrumPart dpClone = (DrumPart) dp.clone();
		boolean kicky = dp.getInstrument() < 38;
		boolean aboveSnarey = dp.getInstrument() > 40;
		sectionForcedDynamics &= (kicky || aboveSnarey);

		int chordsCount = actualProgression.size();

		List<Integer> drumPattern = generateDrumPatternFromPart(dp);

		if (!dp.isVelocityPattern() && drumPattern.indexOf(dp.getInstrument()) == -1) {
			//drumPhrase.addNote(new Note(Integer.MIN_VALUE, patternDurationTotal, 100));
			drumPhrase.setStartTime(START_TIME_DELAY + ((noteMultiplier * dp.getDelay()) / 1000.0));
			return drumPhrase;
		}

		List<Integer> drumVelocityPattern = generateDrumVelocityPatternFromPart(sec, dp);

		Random drumFillGenerator = new Random(
				dp.getPatternSeed() + dp.getOrder() + sec.getTypeMelodyOffset());
		// bar iter
		int hits = dp.getHitsPerPattern();
		int swingPercentAmount = (hits % 2 == 0) ? dp.getSwingPercent() : 50;

		List<Integer> fillPattern = dp.getChordSpanFill()
				.getPatternByLength(actualProgression.size(), dp.isFillFlip());

		for (int o = 0; o < measures; o++) {
			// exceptions are generated the same for each bar, but differently for each pattern within bar (if there is more than 1)
			Random exceptionGenerator = new Random(dp.getPatternSeed() + dp.getOrder());
			int chordSpan = dp.getChordSpan();
			int oneChordPatternSize = drumPattern.size() / chordSpan;
			boolean ignoreChordSpanFill = false;
			int extraExceptionChance = 0;
			boolean drumFill = false;
			// chord iter
			for (int chordIndex = 0; chordIndex < chordsCount; chordIndex += chordSpan) {

				if (genVars && ((chordIndex == 0) || (chordIndex == chordInts.size()))) {
					List<Double> chanceMultipliers = sec.isTransition()
							? Arrays.asList(new Double[] { 1.0, 1.0, 2.0 })
							: null;
					variations = fillVariations(sec, dp, variations, 4, chanceMultipliers);
				}

				if ((variations != null) && (chordIndex == 0)) {
					for (Integer var : variations) {
						if (o == measures - 1) {
							//LOGGER.debug("Drum #" + dp.getOrder() + " variation: " + var);
						}

						switch (var) {
						case 0:
							ignoreChordSpanFill = true;
							break;
						case 1:
							extraExceptionChance = (kicky || aboveSnarey)
									? dp.getExceptionChance() + 10
									: dp.getExceptionChance();
							break;
						case 2:
							drumFill = (kicky || aboveSnarey);
							break;
						default:
							throw new IllegalArgumentException("Too much variation!");
						}
					}
				}

				double patternDurationTotal = 0.0;
				for (int k = 0; k < chordSpan; k++) {
					patternDurationTotal += (progressionDurations.size() > chordIndex + k)
							? progressionDurations.get(chordIndex + k)
							: 0.0;
				}

				double drumDuration = (dp.getPattern() == RhythmPattern.MELODY1
						&& melodyNotePatternMap != null) ? Durations.SIXTEENTH_NOTE
								: Durations.WHOLE_NOTE * chordSpan / hits;
				double durationNow = 0.0;
				int k = 0;
				while (durationNow + DBL_ERR < patternDurationTotal) {
					int drum = drumPattern.get(k);
					int velocity = drumVelocityPattern.get(k);
					int pitch = (drum >= 0) ? drum : Integer.MIN_VALUE;
					if (drum < 0 && (dp.isVelocityPattern() || (o > 0 && sectionForcedDynamics))) {
						velocity = (velocity * 5) / 10;
						pitch = dp.getInstrument();
					}
					int chordNumAdd = 0;
					double durationNowCheck = durationNow + DBL_ERR
							- progressionDurations.get(chordIndex);
					while (durationNowCheck > 0.0) {
						chordNumAdd++;
						if (progressionDurations.size() <= (chordNumAdd + chordIndex)) {
							break;
						}
						durationNowCheck -= progressionDurations.get(chordIndex + chordNumAdd);
					}
					int chordNum = chordIndex + chordNumAdd;
					boolean forceLastFilled = drumFill
							&& (chordNum == actualProgression.size() - 1);
					if (!ignoreChordSpanFill && !forceLastFilled) {
						if (fillPattern.get(chordNum % actualProgression.size()) < 1) {
							pitch = Integer.MIN_VALUE;
						}
					}

					int drumFillExceptionChance = 0;
					double usedDrumDuration = drumDuration;
					if (forceLastFilled) {
						k++;
						usedDrumDuration *= 2;
						drumFillExceptionChance = 60;

						int drumFillUnpauseChance = dp.getInstrument() < 46 ? 20 : 10;
						if (pitch < 0 && drumFillGenerator.nextInt(100) < drumFillUnpauseChance) {
							pitch = dp.getInstrument();
						}

					}
					if (pitch != Integer.MIN_VALUE && gc.isDrumCustomMapping()) {
						pitch = mapDrumPitchByCustomMapping(pitch, true);
					}
					boolean exception = exceptionGenerator.nextInt(100) < (dp.getExceptionChance()
							+ extraExceptionChance + drumFillExceptionChance);

					if (durationNow + usedDrumDuration - DBL_ERR > patternDurationTotal) {
						usedDrumDuration = patternDurationTotal - durationNow;
						if (usedDrumDuration < FILLER_NOTE_MIN_DURATION) {
							pitch = Integer.MIN_VALUE;
						}
					}

					if (exception) {
						int secondVelocity = (velocity * 8) / 10;
						Note n1 = new Note(pitch, usedDrumDuration / 2, velocity);
						Note n2 = new Note(pitch, usedDrumDuration / 2, secondVelocity);
						n1.setDuration(0.5 * n1.getRhythmValue());
						n2.setDuration(0.5 * n2.getRhythmValue());
						drumPhrase.addNote(n1);
						drumPhrase.addNote(n2);
					} else {
						Note n1 = new Note(pitch, usedDrumDuration, velocity);
						n1.setDuration(0.5 * n1.getRhythmValue());
						drumPhrase.addNote(n1);
					}
					durationNow += usedDrumDuration;
					k = (k + 1) % drumPattern.size();
				}
			}
		}
		if (genVars && variations != null) {
			sec.setVariation(4, getAbsoluteOrder(4, dp), variations);
		}

		processSectionTransition(sec, drumPhrase.getNoteList(),
				progressionDurations.stream().mapToDouble(e -> e).sum(), 0.25, 0.15, 0.9);

		swingPhrase(drumPhrase, swingPercentAmount, Durations.QUARTER_NOTE);
		drumPhrase.setStartTime(START_TIME_DELAY + ((noteMultiplier * dp.getDelay()) / 1000.0));
		dp.setHitsPerPattern(dpClone.getHitsPerPattern());
		dp.setPatternShift(dpClone.getPatternShift());
		dp.setChordSpan(dpClone.getChordSpan());
		return drumPhrase;

	}

	private List<Integer> fillVariations(Section sec, InstPart instPart, List<Integer> variations,
			int part) {
		return fillVariations(sec, instPart, variations, part, new ArrayList<>());
	}

	private List<Integer> fillVariations(Section sec, InstPart instPart, List<Integer> variations,
			int part, List<Double> chanceMultipliers) {
		if (variations != null) {
			return variations;
		}
		if (chanceMultipliers == null) {
			chanceMultipliers = new ArrayList<>();
		}
		Random varGenerator = new Random(gc.getArrangement().getSeed() + instPart.getOrder()
				+ sec.getTypeSeedOffset() + part * 1000);

		int numVars = Section.variationDescriptions[part].length - 2;
		//LOGGER.debug("Chance: " + gc.getArrangementPartVariationChance());
		int modifiedChance = OMNI.clampChance(
				gc.getArrangementPartVariationChance() * sec.getChanceForInst(part) / 50);
		modifiedChance += (gc.getArrangementPartVariationChance() - modifiedChance) / 2;
		/*LOGGER.debug(
				"Modified: " + modifiedChance + ", for inst: " + sec.getChanceForInst(part));*/

		for (int i = 0; i < numVars; i++) {
			int chance = (chanceMultipliers.size() > i)
					? OMNI.clampChance((int) (modifiedChance * chanceMultipliers.get(i)))
					: modifiedChance;
			if (varGenerator.nextInt(100) >= chance) {
				continue;
			}

			if (VariationPopup.bannedInstVariations.get(part).contains(i + 2)) {
				continue;
			}

			if (variations == null) {
				variations = new ArrayList<>();
			}

			if (!variations.contains(i) && variations.size() < numVars) {
				variations.add(i);
			}
		}
		/*LOGGER.debug("Generated variations for part: " + part + ", size: "
				+ (variations != null ? variations.size() : "null"));*/
		return variations;
	}

	public static int mapDrumPitchByCustomMapping(int pitch, boolean cached) {
		if (cached && customDrumMappingNumbers != null) {
			int mapped = customDrumMappingNumbers.get(pitch);
			if (mapped == -1) {
				throw new IllegalArgumentException(
						"Pitch not found in custom drum mapping: " + pitch);
			}
			return customDrumMappingNumbers.get(pitch);
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

	public static int getAbsoluteOrder(int partNum, InstPart part) {
		return getAbsoluteOrder(partNum, part.getOrder());
	}

	public static int getAbsoluteOrder(int partNum, int partOrder) {
		List<? extends InstPanel> panels = VibeComposerGUI.getInstList(partNum);
		for (int i = 0; i < panels.size(); i++) {
			if (panels.get(i).getPanelOrder() == partOrder) {
				return i;
			}
		}
		throw new IllegalArgumentException("Absolute order not found!");
	}

	protected Phrase fillChordSlash(List<int[]> actualProgression, int measures) {
		Phrase chordSlashPhrase = new Phrase();
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
					chordSlashPhrase.addChord(new int[] { Integer.MIN_VALUE },
							progressionDurations.get(j));
				}
			}
		}
		int extraTranspose = 0;
		if (gc.getScaleMode() != ScaleMode.IONIAN) {
			MidiUtils.transposePhrase(chordSlashPhrase, ScaleMode.IONIAN.noteAdjustScale,
					gc.getScaleMode().noteAdjustScale);
		}
		Mod.transpose(chordSlashPhrase, -12 + extraTranspose + modTrans);

		// delay
		double additionalDelay = 0;
		/*if (gc.getChordGenSettings().isUseDelay()) {
			additionalDelay = ((noteMultiplier * cp.getDelay()) / 1000.0);
		}*/
		chordSlashPhrase.setStartTime(START_TIME_DELAY + additionalDelay);
		return chordSlashPhrase;


	}

	private <T> List<T> partOfListClean(int part, int partCount, List<T> list) {
		double preciseDivision = list.size() / (double) partCount;
		int start = (int) Math.round(preciseDivision * part);
		int end = (int) Math.round(preciseDivision * (part + 1));
		return list.subList(start >= 0 ? start : 0, end < list.size() ? end : list.size());
	}

	private <T> List<T> partOfList(int part, int partCount, List<T> list) {
		if (partCount == 1) {
			return list;
		}
		double size = Math.ceil(list.size() / ((double) partCount));
		List<T> returnList = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			if (i >= part * size && i <= (part + 1) * size) {
				returnList.add(list.get(i));
			}
		}
		return returnList;
	}

	private void applyRuleToMelody(Note[] melody, Consumer<Note[]> melodyRule) {
		melodyRule.accept(melody);
	}

	private Note[] deepCopyNotes(MelodyPart mp, Note[] originals, int[] chord,
			Random melodyGenerator) {
		Note[] copied = new Note[originals.length];
		for (int i = 0; i < originals.length; i++) {
			Note n = originals[i];
			copied[i] = new Note(n.getPitch(), n.getRhythmValue());
		}
		if (chord != null && melodyGenerator != null && gc.isFirstNoteFromChord()) {
			Note n = oldAlgoGenerateNote(mp, chord, true, MELODY_SCALE, null, melodyGenerator,
					Durations.WHOLE_NOTE);
			copied[0] = new Note(n.getPitch(), originals[0].getRhythmValue(),
					originals[0].getDynamic());
		}
		return copied;
	}

	private void processPausePattern(ArpPart ap, List<Integer> arpPausesPattern,
			Random pauseGenerator) {
		for (int i = 0; i < ap.getHitsPerPattern(); i++) {
			if (pauseGenerator.nextInt(100) < ap.getPauseChance()) {
				arpPausesPattern.set(i, 0);
			}
		}
	}

	private Map<String, List<Integer>> generateArpMap(int mainGeneratorSeed, boolean needToReport,
			ArpPart ap) {
		Random uiGenerator2arpPattern = new Random(mainGeneratorSeed + 1);
		Random uiGenerator3arpOctave = new Random(mainGeneratorSeed + 2);
		Random uiGenerator4arpPauses = new Random(mainGeneratorSeed + 3);

		List<Integer> arpPausesPattern = new ArrayList<>();
		if (ap.getPattern() == RhythmPattern.FULL) {
			for (int i = 0; i < ap.getHitsPerPattern(); i++) {
				arpPausesPattern.add(1);
			}
			Collections.rotate(arpPausesPattern, ap.getPatternShift());
		} else if (ap.getPattern() == RhythmPattern.MELODY1 && melodyNotePattern != null) {
			//LOGGER.debug("Setting note pattern!");
			arpPausesPattern = melodyNotePattern;
			ap.setPatternShift(0);
			//dp.setVelocityPattern(false);
			ap.setChordSpan(chordInts.size());
			ap.setHitsPerPattern(melodyNotePattern.size());
			ap.setPatternRepeat(1);
		} else {
			arpPausesPattern = ap.getFinalPatternCopy();
			arpPausesPattern = arpPausesPattern.subList(0, ap.getHitsPerPattern());
		}

		processPausePattern(ap, arpPausesPattern, uiGenerator4arpPauses);

		int[] arpPatternArray = IntStream.iterate(0, e -> (e + 1) % MAXIMUM_PATTERN_LENGTH)
				.limit(ap.getHitsPerPattern() * 2).toArray();
		int[] arpOctaveArray = IntStream.iterate(0, e -> (e + 12) % 24)
				.limit(ap.getHitsPerPattern() * 2).toArray();
		List<Integer> arpPattern = Arrays.stream(arpPatternArray).boxed()
				.collect(Collectors.toList());
		if (ap.isRepeatableNotes()) {
			arpPattern.addAll(arpPattern);
		}


		List<Integer> arpOctavePattern = Arrays.stream(arpOctaveArray).boxed()
				.collect(Collectors.toList());

		// TODO: note pattern, different from rhythm pattern
		//if (ap.getPattern() == RhythmPattern.RANDOM) {
		Collections.shuffle(arpPattern, uiGenerator2arpPattern);
		Collections.shuffle(arpOctavePattern, uiGenerator3arpOctave);
		//}
		// always generate ap.getHitsPerPattern(), 
		// cut off however many are needed (support for seed randoms)
		if (!(ap.getPattern() == RhythmPattern.MELODY1 && melodyNotePattern != null)) {
			arpPausesPattern = arpPausesPattern.subList(0, ap.getHitsPerPattern());
		}
		arpPattern = arpPattern.subList(0, ap.getHitsPerPattern());
		arpOctavePattern = arpOctavePattern.subList(0, ap.getHitsPerPattern());

		Collections.rotate(arpPattern, -1 * ap.getArpPatternRotate());

		if (needToReport) {
			//LOGGER.debug("Arp count: " + ap.getHitsPerPattern());
			//LOGGER.debug("Arp pattern: " + arpPattern.toString());
			//LOGGER.debug("Arp octaves: " + arpOctavePattern.toString());
		}
		//LOGGER.debug("Arp pauses : " + arpPausesPattern.toString());

		if (ap.getChordSpan() > 1) {
			if (!(ap.getPattern() == RhythmPattern.MELODY1 && melodyNotePattern != null)) {
				arpPausesPattern = MidiUtils.intersperse(0, ap.getChordSpan() - 1,
						arpPausesPattern);
			}
			arpPattern = MidiUtils.intersperse(0, ap.getChordSpan() - 1, arpPattern);
			arpOctavePattern = MidiUtils.intersperse(0, ap.getChordSpan() - 1, arpOctavePattern);
		}

		// pattern repeat

		List<Integer> repArpPattern = new ArrayList<>();
		List<Integer> repOctPattern = new ArrayList<>();
		List<Integer> repPausePattern = new ArrayList<>();
		for (int i = 0; i < ap.getPatternRepeat(); i++) {
			repArpPattern.addAll(arpPattern);
			repOctPattern.addAll(arpOctavePattern);
			repPausePattern.addAll(arpPausesPattern);
		}


		Map<String, List<Integer>> arpMap = new HashMap<>();
		arpMap.put(ARP_PATTERN_KEY, repArpPattern);
		arpMap.put(ARP_OCTAVE_KEY, repOctPattern);
		arpMap.put(ARP_PAUSES_KEY, repPausePattern);


		return arpMap;
	}

	public static List<Integer> generateDrumPatternFromPart(DrumPart dp) {
		Random uiGenerator1drumPattern = new Random(dp.getPatternSeed() + dp.getOrder() - 1);
		List<Integer> premadePattern = null;
		if (melodyNotePattern != null && dp.getPattern() == RhythmPattern.MELODY1) {
			//LOGGER.debug("Setting note pattern!");
			dp.setHitsPerPattern(melodyNotePattern.size());
			premadePattern = melodyNotePattern;
			dp.setPatternShift(0);
			//dp.setVelocityPattern(false);
			dp.setChordSpan(chordInts.size());
		} else {
			premadePattern = dp.getFinalPatternCopy();
		}

		List<Integer> drumPattern = new ArrayList<>();
		for (int j = 0; j < dp.getHitsPerPattern(); j++) {
			// if random pause or not present in pattern: pause
			boolean blankDrum = uiGenerator1drumPattern.nextInt(100) < dp.getPauseChance()
					|| !premadePattern.get(j).equals(1);
			if (dp.isPatternFlip()) {
				blankDrum = !blankDrum;
			}
			if (blankDrum) {
				drumPattern.add(-1);
			} else {
				if (dp.getInstrument() == 42
						&& uiGenerator1drumPattern.nextInt(100) < OPENHAT_CHANCE) {
					drumPattern.add(mapDrumPitchByCustomMapping(46, true));
				} else {
					drumPattern.add(dp.getInstrument());
				}

			}
		}

		/*System.out
				.println("Drum pattern for " + dp.getInstrument() + " : " + drumPattern.toString());*/
		return drumPattern;
	}

	private List<Integer> generateDrumVelocityPatternFromPart(Section sec, DrumPart dp) {
		Random uiGenerator1drumVelocityPattern = new Random(dp.getPatternSeed() + dp.getOrder());
		List<Integer> drumVelocityPattern = new ArrayList<>();
		int multiplier = (gc.isScaleMidiVelocityInArrangement()) ? sec.getVol(4) : 100;
		if (dp.getCustomVelocities() != null
				&& dp.getCustomVelocities().size() >= dp.getHitsPerPattern()) {
			for (int i = 0; i < dp.getHitsPerPattern(); i++) {
				drumVelocityPattern
						.add(multiplyVelocity(dp.getCustomVelocities().get(i), multiplier, 0, 1));
			}
		} else {
			int minVel = multiplyVelocity(dp.getVelocityMin(), multiplier, 0, 1);
			int maxVel = multiplyVelocity(dp.getVelocityMax(), multiplier, 1, 0);
			int velocityRange = maxVel - minVel;
			for (int j = 0; j < dp.getHitsPerPattern(); j++) {
				int velocity = uiGenerator1drumVelocityPattern.nextInt(velocityRange) + minVel;
				drumVelocityPattern.add(velocity);
			}
		}

		/*LOGGER.debug("Drum velocity pattern for " + dp.getInstrument() + " : "
				+ drumVelocityPattern.toString());*/
		return drumVelocityPattern;
	}
}
