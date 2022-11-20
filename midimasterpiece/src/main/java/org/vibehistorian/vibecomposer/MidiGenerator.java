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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
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
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.Enums.KeyChangeType;
import org.vibehistorian.vibecomposer.Enums.PatternJoinMode;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Helpers.PartExt;
import org.vibehistorian.vibecomposer.Helpers.PhraseExt;
import org.vibehistorian.vibecomposer.Helpers.PhraseNote;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Helpers.UsedPattern;
import org.vibehistorian.vibecomposer.Panels.DrumGenSettings;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.BassPart;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;

import jm.JMC;
import jm.music.data.Note;
import jm.music.data.Phrase;
import jm.music.data.Score;
import jm.music.tools.Mod;

public class MidiGenerator implements JMC {

	public static final double DBL_ERR = 0.01;
	public static final double FILLER_NOTE_MIN_DURATION = 0.05;
	public static double GLOBAL_DURATION_MULTIPLIER = 0.95;
	public static double SPLIT_DURATION_MULTIPLIER = 0.97;
	public static final int[] DEFAULT_INSTRUMENT_TRANSPOSE = { 0, -24, -12, -24, 0 };

	public enum ShowScoreMode {
		NODRUMSCHORDS, DRUMSONLY, CHORDSONLY, ALL;
	}

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
		MELODY_SKELETON_DURATIONS = new double[] { Durations.SIXTEENTH_NOTE, Durations.EIGHTH_NOTE,
				Durations.DOTTED_EIGHTH_NOTE, Durations.QUARTER_NOTE, Durations.DOTTED_QUARTER_NOTE,
				Durations.HALF_NOTE };
		MELODY_SKELETON_DURATIONS_SHORT = new double[] { Durations.SIXTEENTH_NOTE / 2.0,
				Durations.SIXTEENTH_NOTE, Durations.EIGHTH_NOTE, Durations.DOTTED_EIGHTH_NOTE,
				Durations.QUARTER_NOTE, Durations.DOTTED_QUARTER_NOTE, Durations.HALF_NOTE };
	}

	private static final boolean debugEnabled = true;
	private static final PrintStream originalStream = System.out;

	// big G
	public static GUIConfig gc;

	// last scores saved
	public static List<Score> LAST_SCORES = new ArrayList<>();
	public static final int LAST_SCORES_LIMIT = 10;

	// track map for Solo
	public static List<InstPart> trackList = new ArrayList<>();

	// constants
	public static final boolean MAXIMIZE_CHORUS_MAIN_MELODY = false;
	public static final int MELODY_PATTERN_RESOLUTION = 16;

	public static final int MAXIMUM_PATTERN_LENGTH = 8;
	public static final int OPENHAT_CHANCE = 0;
	public static final int EMBELLISHMENT_CHANCE = 20;
	private static final int maxAllowedScaleNotes = 7;
	static final int BASE_ACCENT = 15;
	public static double START_TIME_DELAY = Durations.QUARTER_NOTE;
	private static final double DEFAULT_CHORD_SPLIT = 625;
	private static final String ARP_PATTERN_KEY = "ARP_PATTERN";
	private static final String ARP_OCTAVE_KEY = "ARP_OCTAVE";
	private static final String ARP_PAUSES_KEY = "ARP_PAUSES";

	// visibles/settables
	public static DrumGenSettings DRUM_SETTINGS = new DrumGenSettings();

	public static List<String> userChords = new ArrayList<>();
	public static List<Double> userChordsDurations = new ArrayList<>();
	public static Phrase userMelody = null;
	public static List<String> chordInts = new ArrayList<>();
	public static double GENERATED_MEASURE_LENGTH = 0;

	public static String FIRST_CHORD = null;
	public static String LAST_CHORD = null;

	public static boolean COLLAPSE_DRUM_TRACKS = true;
	public static boolean RANDOMIZE_TARGET_NOTES = false;

	public static Map<Integer, List<Integer>> TARGET_NOTES = null;


	// for internal use only
	private static double[] MELODY_SKELETON_DURATIONS_SHORT = { Durations.SIXTEENTH_NOTE / 2.0,
			Durations.SIXTEENTH_NOTE, Durations.EIGHTH_NOTE, Durations.DOTTED_EIGHTH_NOTE,
			Durations.QUARTER_NOTE, Durations.DOTTED_QUARTER_NOTE, Durations.HALF_NOTE };

	private static double[] MELODY_SKELETON_DURATIONS = { Durations.SIXTEENTH_NOTE,
			Durations.EIGHTH_NOTE, Durations.DOTTED_EIGHTH_NOTE, Durations.QUARTER_NOTE,
			Durations.DOTTED_QUARTER_NOTE, Durations.HALF_NOTE };

	private static double[] MELODY_DUR_ARRAY = { Durations.HALF_NOTE, Durations.DOTTED_QUARTER_NOTE,
			Durations.QUARTER_NOTE, Durations.EIGHTH_NOTE };
	private double[] MELODY_DUR_CHANCE = { 0.3, 0.6, 1.0, 1.0 };

	private static double[] CHORD_DUR_ARRAY = { Durations.WHOLE_NOTE, Durations.HALF_NOTE };
	private double[] CHORD_DUR_CHANCE = { 0.0, 0.20, 0.80, 1.0 };
	private static Map<Integer, Integer> customDrumMappingNumbers = null;

	private List<Integer> MELODY_SCALE = cIonianScale4;
	public List<Double> progressionDurations = new ArrayList<>();
	public List<int[]> chordProgression = new ArrayList<>();
	public List<int[]> rootProgression = new ArrayList<>();

	public List<Double> progressionDurationsBackup = new ArrayList<>();
	public List<int[]> chordProgressionBackup = new ArrayList<>();
	public List<int[]> rootProgressionBackup = new ArrayList<>();

	private int melodyResForChord(int chordIndex) {
		return (int) (Math.round(MELODY_PATTERN_RESOLUTION * progressionDurations.get(chordIndex)
				/ Durations.WHOLE_NOTE));
	}

	public static Map<Integer, List<Note>> userMelodyMap = new HashMap<>();
	private Map<Integer, List<Note>> chordMelodyMap1 = new HashMap<>();
	private List<int[]> melodyBasedChordProgression = new ArrayList<>();
	private List<int[]> melodyBasedRootProgression = new ArrayList<>();
	private String alternateChords = null;
	private Section currentSection = null;

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
	private int secOrder = -1;

	private int modTrans = 0;
	private ScaleMode modScale = null;

	public MidiGenerator(GUIConfig gc) {
		MidiGenerator.gc = gc;
	}

	protected Vector<Note> generateMelodyBlockSkeletonFromChords(MelodyPart mp, List<int[]> chords,
			List<int[]> roots, int measures, int notesSeedOffset, Section sec,
			List<Integer> variations) {

		boolean genVars = variations == null;

		boolean fillChordMelodyMap = false;
		if (chordMelodyMap1.isEmpty() && notesSeedOffset == 0
				&& (roots.size() == chordInts.size())) {
			fillChordMelodyMap = true;
		}

		if (sec.getSectionVariations() != null && sec.isSectionVar(2)) {
			notesSeedOffset += 100;
		}

		int MAX_JUMP_SKELETON_CHORD = mp.getBlockJump();
		int SAME_RHYTHM_CHANCE = mp.getDoubledRhythmChance();
		int EXCEPTION_CHANCE = mp.getNoteExceptionChance();
		int CHORD_STRETCH = 4;
		int BLOCK_TARGET_MODE = gc.getMelodyBlockTargetMode();

		int seed = mp.getPatternSeedWithPartOffset();
		int melodyBlockGeneratorSeed = seed + notesSeedOffset;
		LG.d("Seed: " + seed);


		// A B A C pattern
		List<Integer> blockSeedOffsets = (mp.getMelodyPatternOffsets() != null)
				? mp.getMelodyPatternOffsets()
				: new ArrayList<>(Arrays.asList(new Integer[] { 1, 2, 1, 3 }));

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
		if (TARGET_NOTES == null) {
			TARGET_NOTES = new HashMap<>();
		}
		if (RANDOMIZE_TARGET_NOTES) {
			int targetNoteSeed = VibeComposerGUI.vibeComposerGUI.melody1ForcePatterns.isSelected()
					? (seed + 1)
					: (seed + mp.getOrder());
			blockChordNoteChoices = MidiGeneratorUtils.generateOffsets(roots, targetNoteSeed,
					gc.getMelodyBlockTargetMode(), gc.getMelodyTargetNoteVariation());
		}
		TARGET_NOTES.put(mp.getOrder(), blockChordNoteChoices);
		LG.d("Choices: " + blockChordNoteChoices);

		Vector<Note> noteList = new Vector<>();

		// if notes seed offset > 0, add it only to one of: rhythms, pitches
		//Random nonMainMelodyGenerator = new Random(seed + 30);
		int pitchPickerOffset = notesSeedOffset;
		int rhythmOffset = notesSeedOffset;

		int firstBlockOffset = Math.abs(blockSeedOffsets.get(0));
		if (firstBlockOffset > 0) {
			firstBlockOffset--;
		}

		Random pitchPickerGenerator = new Random(seed + pitchPickerOffset + firstBlockOffset);
		Random exceptionGenerator = new Random(melodyBlockGeneratorSeed + 2 + firstBlockOffset);
		Random sameRhythmGenerator = new Random(seed + 3 + firstBlockOffset);
		Random alternateRhythmGenerator = new Random(seed + 4);
		Random durationGenerator = new Random(melodyBlockGeneratorSeed + 5);
		Random embellishmentGenerator = new Random(melodyBlockGeneratorSeed + 10);
		Random soloGenerator = new Random(melodyBlockGeneratorSeed + 25);
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
		//LG.d("Alt: " + alternateRhythm);
		int maxBlockChangeAdjustment = 0;
		boolean embellish = false;
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
							LG.d("Melody variation: " + var);
						}

						switch (var) {
						case 0:
							// transpose - only add, processed later
							break;
						case 1:
							maxBlockChangeAdjustment++;
							break;
						case 2:
							embellish = true;
							break;
						case 3:
							// solo (also processed later)
							SAME_RHYTHM_CHANCE = 100;
							List<Integer> soloTargetPattern = MelodyUtils.SOLO_MELODY_PATTERNS.get(
									soloGenerator.nextInt(MelodyUtils.SOLO_MELODY_PATTERNS.size()));
							blockSeedOffsets = new ArrayList<>(soloTargetPattern);
							LG.i("Chosen solo pattern: "
									+ StringUtils.join(soloTargetPattern, ","));

							while (blockSeedOffsets.size() < chords.size()) {
								blockSeedOffsets.addAll(blockSeedOffsets);
							}
							break;
						default:
							throw new IllegalArgumentException("Too much variation!");
						}
					}
				}

				if (false) {
					new Object() {

					};
				}

				int blockOffset = blockSeedOffsets.get(chordIndex % blockSeedOffsets.size());
				int originalBlockOffset = blockOffset;
				blockOffset = Math.abs(blockOffset);

				if (blockOffset > 0) {
					blockOffset--;
				}

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
				if (durations != null
						&& !MidiUtils.roughlyEqual(durations.stream().mapToDouble(e -> e).sum(),
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

					int speed = MidiGeneratorUtils.adjustChanceParamForTransition(mp.getSpeed(),
							sec, chordIndex, chords.size(), 40, 0.25, false);
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
				LG.d("Overall Block Durations: " + StringUtils.join(durations, ",")
						+ ", Doubled rhythm: " + sameRhythmTwice);
				int chord1 = MidiGeneratorUtils.getStartingNote(stretchedChords,
						blockChordNoteChoices, chordIndex, BLOCK_TARGET_MODE);
				int chord2 = MidiGeneratorUtils.getStartingNote(stretchedChords,
						blockChordNoteChoices, chordIndex + 1, BLOCK_TARGET_MODE);
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
				boolean FLEXIBLE_PATTERN = mp.isPatternFlexible() && blockChanges.size() > 1;
				if (FLEXIBLE_PATTERN && existingPattern != null) {
					int blockChangesSum = blockChanges.stream().mapToInt(e -> e).sum();
					int newLastBlockChange = blockChanges.get(blockChanges.size() - 1);
					newLastBlockChange += (chord2 - chord1) - blockChangesSum;
					List<Integer> newBlockChanges = new ArrayList<>(blockChanges);
					newBlockChanges.set(blockChanges.size() - 1, newLastBlockChange);
					blockChanges = newBlockChanges;

				}
				LG.d("Block changes: " + blockChanges);
				int startingNote = chord1 % 7;

				List<Integer> forcedLengths = (existingPattern != null
						&& gc.getMelodyPatternEffect() != 1)
								? existingPattern.getRight().stream().map(e -> e.durations.size())
										.collect(Collectors.toList())
								: null;

				List<MelodyBlock> melodyBlocks = (existingPattern != null
						&& gc.getMelodyPatternEffect() > 0 && !FLEXIBLE_PATTERN)
								? existingPattern.getRight()
								: generateMelodyBlocksForDurations(mp, sec, durations, roots,
										melodyBlockGeneratorSeed + blockOffset, blockChanges,
										MAX_JUMP_SKELETON_CHORD, startingNote, chordIndex,
										forcedLengths, remainingDirChanges);
				if (FLEXIBLE_PATTERN && existingPattern != null
						&& gc.getMelodyPatternEffect() > 0) {
					List<MelodyBlock> storedMelodyBlocks = new ArrayList<>(
							existingPattern.getRight());
					LG.d("HALF PATTERN - set last melody block to newly generated block! Sizes equal?: "
							+ (storedMelodyBlocks.size() == melodyBlocks.size()));
					storedMelodyBlocks.set(storedMelodyBlocks.size() - 1,
							melodyBlocks.get(melodyBlocks.size() - 1));
					melodyBlocks = storedMelodyBlocks;
				}
				//LG.d("Starting note: " + startingNote);

				if (existingPattern == null) {
					LG.n("Stored pattern: " + blockOffset + ", for chord index:" + chordIndex
							+ ", Pattern effect: " + gc.getMelodyPatternEffect());
					changesAndBlocksMap.put(blockOffset, Pair.of(blockChangesPair, melodyBlocks));
				} else {
					LG.n("Loaded pattern: " + blockOffset + ", for chord index:" + chordIndex
							+ ", Pattern effect: " + gc.getMelodyPatternEffect());
				}

				int adjustment = 0;
				int exceptionCounter = mp.getMaxNoteExceptions();
				boolean invertedPattern = originalBlockOffset < 0;
				for (int blockIndex = 0; blockIndex < melodyBlocks.size(); blockIndex++) {
					MelodyBlock mb = melodyBlocks.get(blockIndex);
					if (invertedPattern) {
						mb = new MelodyBlock(MelodyUtils.inverse(mb.notes), mb.durations, true);
					}
					List<Integer> pitches = new ArrayList<>();
					if (blockIndex > 0) {
						adjustment += blockChanges.get(blockIndex - 1) * (invertedPattern ? -1 : 1);
					}
					//LG.d("Adjustment: " + adjustment);
					for (int k = 0; k < mb.durations.size(); k++) {
						int note = mb.notes.get(k);
						int pitch = startingOct * 12;
						int combinedNote = startingNote + note;
						//LG.d("1st combined: " + combinedNote);
						Pair<Integer, Integer> notePitch = MidiGeneratorUtils
								.normalizeNotePitch(combinedNote, pitch);
						combinedNote = notePitch.getLeft();
						pitch = notePitch.getRight();
						if (adjustment != 0) {
							combinedNote = combinedNote + adjustment;
							notePitch = MidiGeneratorUtils.normalizeNotePitch(combinedNote, pitch);
							combinedNote = notePitch.getLeft();
							pitch = notePitch.getRight();
						}

						pitch += MidiUtils.MAJ_SCALE.get(combinedNote);
						//LG.d("Combined note: " + combinedNote + ", pitch: " + pitch);
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


					//LG.d(StringUtils.join(mb.durations, ","));
					//LG.d("After: " + StringUtils.join(sortedDurs, ","));
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
						Note n = new Note(pitch, swingDuration);
						n.setDuration(swingDuration * (0.75 + durationGenerator.nextDouble() / 4)
								* GLOBAL_DURATION_MULTIPLIER);
						if (embellish
								&& (n.getRhythmValue() > Durations.DOTTED_EIGHTH_NOTE - DBL_ERR)) {
							List<Note> embNotes = addEmbellishedNotes(n, embellishmentGenerator);
							noteList.addAll(embNotes);
							if (fillChordMelodyMap && o == 0) {
								chordMelodyMap1.get(Integer.valueOf(chordIndex)).addAll(embNotes);
							}
						} else {
							noteList.add(n);
							if (fillChordMelodyMap && o == 0) {
								chordMelodyMap1.get(Integer.valueOf(chordIndex)).add(n);
							}
						}


					}
				}

			}
		}

		if (fillChordMelodyMap) {
			List<String> chordStrings = getChordsFromMelodyPitches(2, chordMelodyMap1,
					MidiUtils.baseFreqMap);
			int start = 1;
			int end = chordMelodyMap1.keySet().size() - 1;
			populateMelodyBasedProgression(chordStrings, start, end);
			for (int i = 0; i < start; i++) {
				chordStrings.set(i, chordInts.get(i));
			}
			for (int i = end; i < chordStrings.size(); i++) {
				chordStrings.set(i, chordInts.get(i));
			}
			alternateChords = StringUtils.join(chordStrings, ",");
		}
		if (genVars && variations != null) {
			sec.setVariation(0, mp.getAbsoluteOrder(), variations);
		}
		return noteList;
	}

	private List<Note> addEmbellishedNotes(Note n, Random embellishmentGenerator) {
		// pick embellishment rhythm - if note RV is multiple of dotted 8th use length 3, otherwise length 4
		int notesNeeded = MidiUtils.isMultiple(n.getRhythmValue(), Durations.DOTTED_EIGHTH_NOTE) ? 3
				: 4;
		Random localEmbGenerator = new Random(embellishmentGenerator.nextInt());
		if (localEmbGenerator.nextInt(100) >= EMBELLISHMENT_CHANCE) {
			return Collections.singletonList(n);
		}
		boolean shortNotes = n.getRhythmValue() > Durations.QUARTER_NOTE - DBL_ERR;
		int[] melodySkeletonDurationWeights = shortNotes
				? Rhythm.normalizedCumulativeWeights(
						new int[] { 100, 100, 300, 100, 300, 100, 100 })
				: Rhythm.normalizedCumulativeWeights(new int[] { 100, 300, 100, 300, 100, 100 });
		Rhythm blockRhythm = new Rhythm(localEmbGenerator.nextInt(), n.getRhythmValue(),
				shortNotes ? MELODY_SKELETON_DURATIONS_SHORT : MELODY_SKELETON_DURATIONS,
				melodySkeletonDurationWeights);
		List<Double> blockDurations = blockRhythm.makeDurations(notesNeeded,
				Durations.SIXTEENTH_NOTE);

		// split note according to rhythm, apply pitch change to notes
		// pick embellishment pitch pattern according to rhythm length (3 or 4)
		List<Integer[]> pitchPatterns = MelodyUtils.NEIGHBORY.stream()
				.filter(e -> e.length == notesNeeded).collect(Collectors.toList());
		if (pitchPatterns.isEmpty()) {
			LG.e("No melody pitch patterns found for: " + notesNeeded);
			return Collections.singletonList(n);
		}
		Integer[] chosenPattern = pitchPatterns
				.get(localEmbGenerator.nextInt(pitchPatterns.size()));
		int pitch = n.getPitch();
		int semis = pitch % 12;
		int octavePitch = pitch - semis;
		List<Note> embNotes = new ArrayList<>();
		for (int i = 0; i < blockDurations.size(); i++) {
			int pitchIndex = MidiUtils.MAJ_SCALE.indexOf(semis);
			if (pitchIndex < 0) {
				LG.e("------------------------------------------------PITCH FROM MAIN MELODY BLOCK WAS NOT IN MAJOR SCALE!");
			}
			int newPitchIndex = pitchIndex + chosenPattern[i];
			int newPitchSemis = MidiUtils.MAJ_SCALE.get((newPitchIndex + 70) % 7);
			int newPitch = octavePitch + newPitchSemis;
			if (newPitchIndex >= 7) {
				newPitch += 12;
			} else if (newPitchIndex < 0) {
				newPitch -= 12;
			}
			Note embNote = new Note(newPitch, blockDurations.get(i));
			embNote.setDuration(blockDurations.get(i) * GLOBAL_DURATION_MULTIPLIER);

			// slightly lower volume on following notes
			embNote.setDynamic(n.getDynamic() - (i * 5));

			// add note
			embNotes.add(embNote);
		}
		return embNotes;
	}

	protected List<MelodyBlock> generateMelodyBlocksForDurations(MelodyPart mp, Section sec,
			List<Double> durations, List<int[]> roots, int melodyBlockGeneratorSeed,
			List<Integer> blockChanges, int maxJump, int startingNote, int chordIndex,
			List<Integer> forcedLengths, int remainingDirChanges) {

		List<MelodyBlock> mbs = new ArrayList<>();

		// TODO: generate some common-sense durations, pick randomly from melody phrases, refinement later


		//LG.d(StringUtils.join(melodySkeletonDurationWeights, ','));
		Random blockNotesGenerator = new Random(melodyBlockGeneratorSeed);

		int prevBlockType = Integer.MIN_VALUE;
		int adjustment = startingNote;

		int remainingVariance = 4;

		for (int blockIndex = 0; blockIndex < durations.size(); blockIndex++) {
			if (blockIndex > 0) {
				adjustment += blockChanges.get(blockIndex - 1);
			}

			int speed = MidiGeneratorUtils.adjustChanceParamForTransition(mp.getSpeed(), sec,
					chordIndex, roots.size(), 40, 0.25, false);
			int addQuick = (speed - 50) * 2;
			int addSlow = addQuick * -1;
			boolean shortNotes = durations.get(blockIndex) < Durations.QUARTER_NOTE - DBL_ERR;
			int[] melodySkeletonDurationWeights = shortNotes
					? Rhythm.normalizedCumulativeWeights(
							new int[] { 100 + addQuick, 100 + addQuick, 300 + addQuick,
									100 + addQuick, 300 + addSlow, 100 + addSlow, 100 + addSlow })
					: Rhythm.normalizedCumulativeWeights(new int[] { 100 + addQuick, 300 + addQuick,
							100 + addQuick, 300 + addSlow, 100 + addSlow, 100 + addSlow });

			Rhythm blockRhythm = new Rhythm(melodyBlockGeneratorSeed + blockIndex,
					durations.get(blockIndex),
					shortNotes ? MELODY_SKELETON_DURATIONS_SHORT : MELODY_SKELETON_DURATIONS,
					melodySkeletonDurationWeights);
			//int length = blockNotesGenerator.nextInt(100) < gc.getMelodyQuickness() ? 4 : 3;

			blockNotesGenerator.setSeed(melodyBlockGeneratorSeed + blockIndex);
			boolean GENERATE_NEW_BLOCKS = false;
			Pair<Integer, Integer[]> typeBlock = (GENERATE_NEW_BLOCKS)
					? MelodyUtils.generateBlockByBlockChangeAndLength(blockChanges.get(blockIndex),
							maxJump, blockNotesGenerator,
							(forcedLengths != null ? forcedLengths.get(blockIndex) : null),
							remainingVariance, remainingDirChanges)
					: MelodyUtils.getRandomByApproxBlockChangeAndLength(
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
					LG.d("SWAPPING CHORDY BLOCK, blockStart: " + blockStart);
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
						LG.d("Found new block!");
					} else {
						LG.d("Different block not found in other types!");
					}
				} else {
					LG.d("Other types don't have this block!");
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
					LG.d("Arpy surprise for block#: " + blockNotes.size() + ", duration: "
							+ durations.get(blockIndex));
				}

			}

			LG.d("Block durations: " + StringUtils.join(blockDurations, ","));
			prevBlockType = blockType;
			//LG.d("Block Durations size: " + blockDurations.size());
			MelodyBlock mb = new MelodyBlock(blockNotes, blockDurations, false);
			mbs.add(mb);
			LG.d("Created block: " + StringUtils.join(blockNotes, ","));
		}
		return mbs;
	}

	protected Map<Integer, List<Note>> convertMelodySkeletonToFullMelody(MelodyPart mp,
			List<Double> durations, Section sec, Vector<Note> skeleton, int notesSeedOffset,
			List<int[]> chords, int measures) {

		int RANDOM_SPLIT_NOTE_PITCH_EXCEPTION_RANGE = 4;

		int seed = mp.getPatternSeedWithPartOffset();
		int orderSeed = seed + mp.getOrder();
		Random splitGenerator = new Random(orderSeed + 4);
		Random pauseGenerator = new Random(orderSeed + 5);
		Random pauseGenerator2 = new Random(orderSeed + 7);
		Random variationGenerator = new Random(mp.getOrder() + gc.getArrangement().getSeed() + 6);
		Random velocityGenerator = new Random(orderSeed + 1 + notesSeedOffset);
		Random splitNoteGenerator = new Random(seed + 8);
		Random splitNoteExceptionGenerator = new Random(seed + 9);
		Random chordLeadingGenerator = new Random(orderSeed + notesSeedOffset + 15);
		Random accentGenerator = new Random(orderSeed + 20);


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
		int minVel = MidiGeneratorUtils.multiplyVelocity(mp.getVelocityMin(), volMultiplier, 0, 1);
		int maxVel = MidiGeneratorUtils.multiplyVelocity(mp.getVelocityMax(), volMultiplier, 1, 0);
		LG.d("Sum:" + skeleton.stream().mapToDouble(e -> e.getRhythmValue()).sum());
		for (int i = 0; i < skeleton.size(); i++) {
			Note n1 = skeleton.get(i);
			n1.setPitch(n1.getPitch() + mp.getTranspose());
			//LG.d(" durCounter: " + durCounter);
			if (durCounter > (currentChordDur - DBL_ERR)) {
				chordCounter = (chordCounter + 1) % durations.size();
				if (chordCounter == 0) {
					measureCounter++;
					// when measure resets
					if (variationGenerator.nextInt(100) < gc.getArrangementPartVariationChance()) {
						splitChance = (int) (splitChance * 1.2);
					}
				}
				durCounter -= currentChordDur;
				if (durCounter < 0) {
					durCounter = 0.0;
				}
				currentChordDur = durations.get(chordCounter);
				//splitGenerator.setSeed(seed + 4);
				//pauseGenerator.setSeed(seed + 5);
				//pauseGenerator2.setSeed(seed + 7);
				splitNoteGenerator.setSeed(orderSeed + 8);
				splitNoteExceptionGenerator.setSeed(orderSeed + 9);
				LG.n("Conversion chord#: " + chordCounter + ", duration: " + currentChordDur);
			}

			double adjDur = n1.getRhythmValue();
			//LG.d("Processing dur: " + adjDur + ", durCounter: " + durCounter);
			int velocity = velocityGenerator.nextInt(maxVel - minVel) + minVel;
			double positionInChord = durCounter / durations.get(chordCounter);
			if (positionInChord < DBL_ERR && accentGenerator.nextInt(100) < mp.getAccents()) {
				velocity = MidiGeneratorUtils.addAccent(velocity, accentGenerator, mp.getAccents());
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
					pitch2 = MidiGeneratorUtils.getAllowedPitchFromRange(pitch2, higherNote,
							positionInChord, splitNoteGenerator);
				} else {
					int lowerNote = pitch1;
					if (splitNoteExceptionGenerator.nextInt(100) < 33 && !splitLastNoteInChord) {
						lowerNote -= RANDOM_SPLIT_NOTE_PITCH_EXCEPTION_RANGE;
					}
					pitch2 = MidiGeneratorUtils.getAllowedPitchFromRange(lowerNote, pitch2,
							positionInChord, splitNoteGenerator);
				}

				double multiplier = (MidiGeneratorUtils.isDottedNote(adjDur)
						&& splitGenerator.nextBoolean()) ? (1.0 / 3.0) : 0.5;

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
				.map(e -> e.isEmpty() ? Integer.MIN_VALUE : e.get(0).getPitch())
				.collect(Collectors.toList());

		List<Integer> fillerPattern = mp.getChordSpanFill()
				.getPatternByLength(fullMelodyMap.keySet().size(), mp.isFillFlip());

		int[] pitches = new int[12];
		fullMelody.forEach(e -> {
			int pitch = e.getPitch();
			if (pitch >= 0) {
				pitches[pitch % 12]++;
			}
		});
		applyNoteTargets(fullMelody, fullMelodyMap, pitches, notesSeedOffset, chords, sec, mp);


		MidiGeneratorUtils.applyBadIntervalRemoval(fullMelody);


		if (gc.getMelodyReplaceAvoidNotes() > 0) {
			MidiGeneratorUtils.replaceAvoidNotes(fullMelodyMap, chords,
					mp.getPatternSeedWithPartOffset(), gc.getMelodyReplaceAvoidNotes());
		}

		// pause by %, sort not-paused into pitches
		for (int chordIndex = 0; chordIndex < fullMelodyMap.keySet().size(); chordIndex++) {
			List<Note> notes = MelodyUtils
					.sortNotesByRhythmicImportance(fullMelodyMap.get(chordIndex));
			//Collections.sort(notes, Comparator.comparing(e -> e.getRhythmValue()));
			pauseGenerator.setSeed(orderSeed + 5);
			int actualPauseChance = MidiGeneratorUtils.adjustChanceParamForTransition(
					mp.getPauseChance(), sec, chordIndex, durations.size(), 40, 0.25, false);
			int pausedNotes = (int) Math.round(notes.size() * actualPauseChance / 100.0);
			int startIndex = (mp.isFillPauses())
					? (gc.isMelodyFillPausesPerChord() ? 1 : ((chordIndex == 0) ? 1 : 0))
					: 0;
			//LG.i("Pausing first # sorted notes: " + pausedNotes);
			for (int j = 0; j < pausedNotes; j++) {
				Note n = notes.get(j);
				if (startIndex == 1) {
					if (n.equals(fullMelodyMap.get(chordIndex).get(0))) {
						//pitches[n.getPitch() % 12]++;
						continue;
					}
				}
				n.setPitch(Integer.MIN_VALUE);
			}
			for (int j = pausedNotes; j < notes.size(); j++) {
				Note n = notes.get(j);
				if (fillerPattern.get(chordIndex) < 1) {
					n.setPitch(Integer.MIN_VALUE);
				} else {
					//pitches[n.getPitch() % 12]++;
				}
			}
		}


		// fill pauses toggle
		if (mp.isFillPauses()) {
			Note fillPauseNote = fullMelody.get(0);
			double addedDuration = 0;
			double addedRv = 0;
			List<Note> notesToRemove = new ArrayList<>();

			int currentChordIndex = 0;
			int currentChordCount = 1;

			// 0 1 2 3 | 4 5 6 7 | 8
			// size of 0: 4 
			// processing 4:

			for (int i = 1; i < fullMelody.size(); i++) {
				Note n = fullMelody.get(i);
				currentChordCount++;
				if (currentChordCount > fullMelodyMap.get(currentChordIndex).size()) {
					currentChordIndex++;
					currentChordCount = 1;
				}

				if (n.getPitch() < 0
						&& !(currentChordCount == 1 && gc.isMelodyFillPausesPerChord())) {
					addedRv += n.getRhythmValue();
					if (fillerPattern.get(currentChordIndex) > 0) {
						addedDuration += n.getRhythmValue();
					}

					notesToRemove.add(n);
				} else {
					fillPauseNote.setDuration(fillPauseNote.getDuration() + addedDuration);
					fillPauseNote.setRhythmValue(fillPauseNote.getRhythmValue() + addedRv);
					//LG.d("Filled note duration: " + fillPauseNote.getRhythmValue());
					addedDuration = 0;
					addedRv = 0;
					fillPauseNote = n;
				}
			}
			fullMelody.removeAll(notesToRemove);
			fullMelodyMap.values().forEach(e -> e.removeAll(notesToRemove));

			if (addedDuration > DBL_ERR || addedRv > DBL_ERR) {
				fillPauseNote.setDuration(fillPauseNote.getDuration() + addedDuration);
				fillPauseNote.setRhythmValue(fillPauseNote.getRhythmValue() + addedRv);
			}
		}

		Random startNoteRand = new Random(orderSeed + 25);

		// repair target notes
		for (int chordIndex = 0; chordIndex < firstNotePitches.size(); chordIndex++) {
			if (fullMelodyMap.get(chordIndex).size() > 0) {
				Note n = fullMelodyMap.get(chordIndex).get(0);
				if (fillerPattern.get(chordIndex) > 0
						&& (n.getPitch() >= 0 || gc.isMelodyFillPausesPerChord())) {
					n.setPitch(firstNotePitches.get(chordIndex));
				}
			}
		}
		// accent lengths of first notes in chord, if not paused and next note has different pitch
		fullMelodyMap.values().forEach(e -> {
			if (e.size() < 3) {
				return;
			}
			Note n = e.get(0);
			int pitch = n.getPitch();
			if (pitch >= 0 && n.getDuration() < Durations.QUARTER_NOTE * 1.1
					&& e.get(1).getPitch() != pitch && e.get(2).getPitch() != pitch) {
				n.setDuration(n.getDuration() * (1 + (mp.getAccents() / 200.0)));
			}
		});

		// repair target notes
		for (int i = 0; i < firstNotePitches.size(); i++) {
			if (fullMelodyMap.get(i).size() > 0) {
				Note n = fullMelodyMap.get(i).get(0);
				double preferredDelay = MELODY_SKELETON_DURATIONS[startNoteRand.nextInt(3)];
				if (n.getPitch() >= 0 && startNoteRand.nextInt(100) >= mp.getStartNoteChance()) {
					int sixteenths = (int) Math.floor(n.getDuration() / Durations.SIXTEENTH_NOTE);
					double usedDelay = -1;
					switch (sixteenths) {
					case 0:
						n.setPitch(Note.REST);
						break;
					case 1:
						n.setPitch(Note.REST);
						break;
					case 2:
						usedDelay = Durations.SIXTEENTH_NOTE;
						break;
					case 3:
						usedDelay = Math.min(preferredDelay, Durations.EIGHTH_NOTE);
						break;
					case 4:
						usedDelay = Math.min(preferredDelay, Durations.EIGHTH_NOTE);
						break;
					default:
						usedDelay = Math.min(preferredDelay, Durations.DOTTED_EIGHTH_NOTE);
						break;
					}
					if (usedDelay > 0) {
						n.setOffset(n.getOffset() + usedDelay);
						n.setDuration(n.getDuration() - usedDelay);
					}
				}
			}
		}

		if (sec.getVariation(0, mp.getAbsoluteOrder()).contains(Integer.valueOf(3))) {
			double currRv = 0;
			for (int chordIndex = 0; chordIndex < fullMelodyMap.keySet().size(); chordIndex++) {
				List<Note> notes = fullMelodyMap.get(chordIndex);
				for (Note n : notes) {
					if (n.getPitch() >= 0) {
						double noteStart = currRv + n.getOffset();
						double noteEnd = noteStart + n.getDuration();

						double closestEndOnGrid = Math.floor(noteEnd / Durations.EIGHTH_NOTE)
								* Durations.EIGHTH_NOTE;
						if (closestEndOnGrid > (noteStart + Durations.SIXTEENTH_NOTE / 2)) {
							n.setDuration(closestEndOnGrid - noteStart);
						}
					}
				}
			}
		}

		return fullMelodyMap;
	}

	protected void applyNoteTargets(List<Note> fullMelody, Map<Integer, List<Note>> fullMelodyMap,
			int[] pitches, int notesSeedOffset, List<int[]> chords, Section sec, MelodyPart mp) {
		// --------- NOTE ADJUSTING ---------------
		int[] chordSeparators = new int[fullMelodyMap.keySet().size() + 1];
		chordSeparators[0] = 0;
		for (Integer i : fullMelodyMap.keySet()) {
			int index = i + 1;
			chordSeparators[index] = fullMelodyMap.get(i).size() + chordSeparators[index - 1];
		}
		int surplusTonics = applyTonicNoteTargets(fullMelody, fullMelodyMap, pitches,
				notesSeedOffset, chordSeparators);
		List<Note> modeNoteChanges = applyModeNoteTargets(fullMelody, fullMelodyMap, pitches,
				surplusTonics);
		applyChordNoteTargets(fullMelody, fullMelodyMap, chords, modeNoteChanges, sec, mp);

	}

	private int applyTonicNoteTargets(List<Note> fullMelody, Map<Integer, List<Note>> fullMelodyMap,
			int[] pitches, int notesSeedOffset, int[] chordSeparators) {
		double requiredPercentageCs = gc.getMelodyTonicNoteTarget() / 100.0;
		int needed = (int) Math.floor(
				fullMelody.stream().filter(e -> e.getPitch() >= 0).count() * requiredPercentageCs);
		LG.d("Found C's: " + pitches[0] + ", needed: " + needed);
		int surplusTonics = pitches[0] - needed;

		if (gc.getMelodyTonicNoteTarget() > 0 && notesSeedOffset == 0) {
			// for main sections: try to adjust notes towards C if there isn't enough C's
			if (surplusTonics < 0) {
				//LG.d("Correcting melody!");
				int investigatedChordIndex = chordSeparators.length - 1;


				// adjust in pairs starting from last
				while (investigatedChordIndex > 0 && surplusTonics < 0) {
					int end = chordSeparators[investigatedChordIndex] - 1;
					// ignore first note in chord - user selectable target note
					int investigatedChordStart = chordSeparators[investigatedChordIndex - 1] + 1;
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

				//LG.d("Remaining difference after last pairs: " + difference);

				// adjust in pairs starting from last-1
				investigatedChordIndex = chordSeparators.length - 2;
				while (investigatedChordIndex > 0 && surplusTonics < 0) {
					int end = chordSeparators[investigatedChordIndex] - 1;
					int investigatedChordStart = chordSeparators[investigatedChordIndex - 1] + 1;
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

				LG.d("TONIC: Remaining difference after first pairs: " + surplusTonics);

			}
		}
		return surplusTonics;
	}

	private List<Note> applyModeNoteTargets(List<Note> fullMelody,
			Map<Integer, List<Note>> fullMelodyMap, int[] pitches, int surplusTonics) {

		ScaleMode scale = (modScale != null) ? modScale : gc.getScaleMode();
		List<Note> modeNoteChanges = new ArrayList<>();
		if (gc.getMelodyModeNoteTarget() > 0 && scale.modeTargetNote > 0) {
			double requiredPercentage = gc.getMelodyModeNoteTarget() / 100.0;
			int needed = (int) Math.ceil(fullMelody.stream().filter(e -> e.getPitch() >= 0).count()
					* requiredPercentage);

			int modeNote = MidiUtils.MAJ_SCALE.get(scale.modeTargetNote);
			LG.d("Found Mode notes: " + pitches[modeNote] + ", needed: " + needed);
			if (pitches[modeNote] < needed) {
				int chordSize = fullMelodyMap.keySet().size();
				int difference = needed - pitches[modeNote];


				List<Note> allNotesExceptFirsts = new ArrayList<>();
				for (int chordIndex = 0; chordIndex < chordSize; chordIndex++) {
					List<Note> chordNotes = fullMelodyMap.get(chordIndex);
					if (chordNotes.size() > 1) {
						allNotesExceptFirsts.addAll(chordNotes.subList(1, chordNotes.size()));
					}
				}
				Collections.sort(allNotesExceptFirsts,
						(e1, e2) -> MidiUtils.compareNotesByDistanceFromModeNote(e1, e2, modeNote));
				for (Note n : allNotesExceptFirsts) {
					int pitch = n.getPitch();
					if (pitch < 0) {
						continue;
					}
					int semitone = pitch % 12;
					int jump = modeNote - semitone;
					if (jump > 6) {
						n.setPitch(MidiUtils.octavePitch(pitch) + modeNote - 12);
					} else if (jump < -6) {
						n.setPitch(MidiUtils.octavePitch(pitch) + modeNote + 12);
					} else {
						n.setPitch(MidiUtils.octavePitch(pitch) + modeNote);
					}
					//LG.i(pitch - n.getPitch());
					difference--;
					modeNoteChanges.add(n);
					if (difference <= 0) {
						break;
					}
				}
				LG.d("MODE: Remaining difference after first pairs: " + (-1 * difference));
			}

		}
		return modeNoteChanges;
	}

	private void applyChordNoteTargets(List<Note> fullMelody,
			Map<Integer, List<Note>> fullMelodyMap, List<int[]> chords, List<Note> modeNoteChanges,
			Section sec, MelodyPart mp) {

		int chordNoteTargetChance = sec.getVariation(0, mp.getAbsoluteOrder())
				.contains(Integer.valueOf(3))
						? (gc.getMelodyChordNoteTarget()
								+ (100 - gc.getMelodyChordNoteTarget()) / 3)
						: gc.getMelodyChordNoteTarget();

		if (chordNoteTargetChance > 0) {
			int chordSize = fullMelodyMap.keySet().size();
			double requiredPercentage = chordNoteTargetChance / 100.0;
			int needed = (int) Math.ceil(fullMelody.stream().filter(e -> e.getPitch() >= 0).count()
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
					if (n.getPitch() >= 0 && chordNotes.contains(n.getPitch() % 12)) {
						found++;
					}
				}
			}

			LG.d("Found Chord notes: " + found + ", needed: " + needed);
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

					// put mode notes at the end, but still sorted - so that chord notes start primarily from corrections of regular notes
					List<Note> sortedModeNotes = new ArrayList<>();
					for (int i = 0; i < sortedNotes.size(); i++) {
						if (modeNoteChanges.contains(sortedNotes.get(i))) {
							sortedModeNotes.add(sortedNotes.get(i));
						}
					}
					sortedNotes.removeAll(sortedModeNotes);
					sortedNotes.addAll(sortedModeNotes);

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

				LG.d("CHORD: Remaining difference: " + (-1 * difference));
			}

		}

	}

	private Map<Integer, List<Integer>> patternsFromNotes(Map<Integer, List<Note>> fullMelodyMap) {
		Map<Integer, List<Integer>> patterns = new HashMap<>();
		for (Integer chKey : fullMelodyMap.keySet()) {
			//LG.d("chkey: " + chKey);
			patterns.put(chKey, patternFromNotes(fullMelodyMap.get(chKey), 1,
					progressionDurations.get(chKey % progressionDurations.size())));
			//LG.d(StringUtils.join(patterns.get(chKey), ","));
		}
		//LG.d(StringUtils.join(pattern, ", "));
		return patterns;
	}

	private List<Integer> patternFromNotes(List<Note> notes, int chordsTotal, Double measureTotal) {
		// strategy: use 64 hits in pattern, then simplify if needed

		int hits = (int) Math.round(
				chordsTotal * MELODY_PATTERN_RESOLUTION * measureTotal / Durations.WHOLE_NOTE);
		double mult = 1;
		SectionConfig sc = (currentSection != null) ? currentSection.getSecConfig() : null;
		int beatDurMultiIndex = (sc != null && sc.getBeatDurationMultiplierIndex() != null)
				? sc.getBeatDurationMultiplierIndex()
				: gc.getBeatDurationMultiplierIndex();
		if (beatDurMultiIndex == 0) {
			mult = 0.5;
		} else if (beatDurMultiIndex == 2) {
			mult = 2;
		}
		measureTotal = (measureTotal == null) ? (chordsTotal * mult * Durations.WHOLE_NOTE)
				: measureTotal;
		double timeForHit = measureTotal / hits;
		List<Integer> pattern = new ArrayList<>();
		List<Double> durationBuckets = new ArrayList<>();
		for (int i = 1; i <= hits; i++) {
			durationBuckets.add(timeForHit * i - DBL_ERR);
			pattern.add(0);
		}

		if (notes == null || notes.isEmpty()) {
			return pattern;
		}

		double currentDuration = 0;
		int explored = 0;

		// 111 0 11111 000 11111

		// 1 0 0 0 1 0 0 0 0 0 0 0 1 0 0 0 0
		int counter = 0;

		List<Double> startTimes = new ArrayList<>();
		double current = 0.0;
		for (Note n : notes) {
			startTimes.add(current + n.getOffset());
			current += n.getRhythmValue();
		}

		boolean skipCounter = startTimes.get(0) < DBL_ERR;
		if (skipCounter) {
			pattern.set(0, (notes.size() > 0 && notes.get(0).getPitch() < 0) ? 0
					: notes.get(0).getPitch());
		}

		for (Note n : notes) {
			if (counter == 0 && skipCounter) {
				counter++;
				continue;
			}
			/*LG.d("START TIME: " + startTimes.get(counter) + ", PITCH: " + n.getPitch()
					+ ", OFFSET: " + n.getOffset());*/
			for (int i = explored; i < hits; i++) {
				if (startTimes.get(counter) < durationBuckets.get(i)) {
					int nextPitch = (n.getPitch() > 0) ? n.getPitch() : 0;
					pattern.set(i, nextPitch);
					explored = i;
					break;
				}
			}
			counter++;
		}
		if (gc.isMelodyPatternFlip()) {
			for (int i = 0; i < pattern.size(); i++) {
				pattern.set(i, pattern.get(i) > 0 ? 0 : 1);
			}
		}
		//LG.i("Melody note pattern: " + StringUtils.join(pattern, ", "));
		return pattern;
	}

	private List<Double> makeSurpriseTrioArpedDurations(List<Double> durations) {

		List<Double> arpedDurations = new ArrayList<>(durations);
		for (int trioIndex = 0; trioIndex < arpedDurations.size() - 2; trioIndex++) {
			double sumThirds = arpedDurations.subList(trioIndex, trioIndex + 3).stream()
					.mapToDouble(e -> e).sum();
			boolean valid = false;
			if (MidiGeneratorUtils.isDottedNote(sumThirds)) {
				sumThirds /= 3.0;
				for (int trio = trioIndex; trio < trioIndex + 3; trio++) {
					arpedDurations.set(trio, sumThirds);
				}
				valid = true;
			} else if (MidiUtils.isMultiple(sumThirds, Durations.HALF_NOTE)) {
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
				LG.d("Dur: " + durCounter + ", chord counter: " + chordCounter);
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
			if (adjDur < DBL_ERR) {
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
			if (MidiUtils.isMultiple(durCounter, swingUnitOfTime)) {

				if (logSwing)
					LG.d(durCounter + " is Multiple of Unit");
				// nothing was caught in first half, SKIP swinging for this 2-unit bit of time
				if (swungNote == null && MidiUtils.isMultiple(durCounter, 2 * swingUnitOfTime)) {
					swungNote = null;
					latestSuitableNote = null;
					if (logSwing)
						LG.d("Can't swing this!");
				} else {
					if (latestSuitableNote != null) {
						double suitableDur = latestSuitableNote.getRhythmValue();
						if (swungNote == null) {
							latestSuitableNote.setRhythmValue(suitableDur + swingAdjust);
							latestSuitableNote.setDuration(
									(suitableDur + swingAdjust) * GLOBAL_DURATION_MULTIPLIER);
							swingAdjust *= -1;
							swungNote = latestSuitableNote;
							latestSuitableNote = null;
							if (logSwing)
								LG.d("Processed 1st swing!");
						} else {
							latestSuitableNote.setRhythmValue(suitableDur + swingAdjust);
							latestSuitableNote.setDuration(
									(suitableDur + swingAdjust) * GLOBAL_DURATION_MULTIPLIER);
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
							swungNote.setDuration(
									(swungDur + swingAdjust) * GLOBAL_DURATION_MULTIPLIER);
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
					if ((adjDur - Math.abs(swingAdjust) > DBL_ERR) && latestSuitableNote == null) {
						latestSuitableNote = n;
					}
				}
			}
		}

		if (swungNote != null) {
			double swungDur = swungNote.getRhythmValue();
			swungNote.setRhythmValue(swungDur + swingAdjust);
			swungNote.setDuration((swungDur + swingAdjust) * GLOBAL_DURATION_MULTIPLIER);
			swingAdjust *= -1;
			swungNote = null;
			latestSuitableNote = null;
			if (logSwing)
				LG.d("Unswung swung note!");
		}

		if (logSwing) {
			LG.d("AFTER:");
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
				LG.d("Dur: " + durCounter + ", chord counter: " + chordCounter);
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
			LG.d("Alternate chord #" + i + ": " + chordString);
			chordStrings.add(chordString);
			prevChordString = chordString;
		}
		return chordStrings;
	}

	public void generatePrettyUserChords(int mainGeneratorSeed, int fixedLength,
			double maxDuration) {
		generateChordProgression(mainGeneratorSeed, fixedLength);
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


	private List<String> generateChordProgressionList(long mainGeneratorSeed, int fixedLength) {
		List<String> chordProgList = new ArrayList<>();

		Random generator = new Random(mainGeneratorSeed);
		Random lengthGenerator = new Random(mainGeneratorSeed);
		Random spiceGenerator = new Random(mainGeneratorSeed);
		Random parallelGenerator = new Random(mainGeneratorSeed + 100);
		Random similarityGenerator = new Random(mainGeneratorSeed + 102);

		boolean isBackwards = !gc.isUseChordFormula();
		Map<String, List<String>> r = (isBackwards) ? cpRulesMap : MidiUtils.cpRulesForwardMap;
		String lastChord = (isBackwards) ? FIRST_CHORD : LAST_CHORD;
		String firstChord = (isBackwards) ? LAST_CHORD : FIRST_CHORD;


		if (fixedLength == 0) {
			List<Integer> progLengths = Arrays.asList(new Integer[] { 4, 5, 6, 8 });
			fixedLength = progLengths.get(lengthGenerator.nextInt(progLengths.size()));
		}
		int maxLength = (fixedLength > 0) ? fixedLength : 8;
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
					&& MidiUtils.BANNED_DIM_AUG_6_LIST.contains(chordString)) {
				continue;
			}
			if (!gc.isEnable9th13th() && MidiUtils.BANNED_9_13_LIST.contains(chordString)) {
				continue;
			}
			allowedSpiceChordsMiddle.add(chordString);
		}

		List<String> allowedSpiceChords = new ArrayList<>();
		for (String s : allowedSpiceChordsMiddle) {
			if (MidiUtils.BANNED_DIM_AUG_6_LIST.contains(s)
					|| MidiUtils.BANNED_SUSSY_LIST.contains(s)) {
				continue;
			}
			allowedSpiceChords.add(s);
		}

		String prevChord = null;
		boolean canRepeatChord = true;
		String lastUnspicedChord = null;
		Random chordRepeatGenerator = new Random(mainGeneratorSeed);
		for (int chordIndex = 0; chordIndex < maxLength; chordIndex++) {
			if (next.size() == 0 && prevChord != null) {
				LG.w("Next list is EMPTY! Adding default C chord!");
				next.add("C");
			}
			int bSkipper = (!gc.isDimAugDom7thEnabled() && "Bdim".equals(next.get(next.size() - 1)))
					? 1
					: 0;
			int nextInt = generator.nextInt(Math.max(next.size() - bSkipper, 1));

			// if last and not empty first chord
			boolean isLastChord = (chordIndex == maxLength - 1);
			String chordString = null;
			if (isLastChord && lastChord != null) {
				chordString = lastChord;
			} else {
				if (gc.isAllowChordRepeats() && (fixedLength < 8 || !isLastChord) && canRepeatChord
						&& chordProgList.size() == 1 && chordRepeatGenerator.nextInt(100) < 10) {
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
			String tempSpicyChordString = MidiGeneratorUtils
					.generateSpicyChordString(spiceGenerator, chordString, spicyChordList);

			// Generate with SPICE CHANCE
			if (generator.nextInt(100) < gc.getSpiceChance()
					&& (chordProgList.size() < 7 || lastChord == null)) {
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
				int chordOrder = MidiUtils.MAJOR_CHORDS.indexOf(chordString);
				String parallelChordString = MidiUtils.MINOR_CHORDS.get(chordOrder);
				// #1 - is Ddim allowed?
				if (chordOrder != 1 || gc.isDimAugDom7thEnabled()) {
					spicyChordString = parallelChordString;
					LG.d("PARALLEL: " + spicyChordString);
				}
			}

			chordProgList.add(spicyChordString);
			/*mappedChord = transposeChord(mappedChord, Mod.MAJOR_SCALE,
					gc.getScaleMode().noteAdjustScale);*/

			debugMsg.add("Generated int: " + nextInt + ", for chord: " + spicyChordString);
			prevChord = spicyChordString;
			next = r.get(chordString);

			if (fixedLength == 8 && chordProgList.size() == 4 && lastChord == null) {
				lastChord = chordString;
			}

			// if last and empty first chord
			if (isLastChord && lastChord == null) {
				lastChord = chordString;
			}
			lastUnspicedChord = chordString;
		}
		if (isBackwards) {
			Collections.reverse(debugMsg);
			Collections.reverse(chordProgList);
			//FIRST_CHORD = lastChord;
			//LAST_CHORD = firstChord;
		} else {
			//FIRST_CHORD = firstChord;
			//LAST_CHORD = lastChord;
		}

		for (String s : debugMsg) {
			LG.d(s);
		}

		// similarity generation - replace chords 4-7 with chords from 0-3
		if (fixedLength == 8) {
			int[] replacementOrder = new int[] { 4, 7, 5, 6 };
			for (int i : replacementOrder) {
				if (similarityGenerator.nextInt() < gc.getLongProgressionSimilarity()) {
					chordProgList.set(i, chordProgList.get(i - 4));
					LG.i("Replaced " + i + "-th chord!");
				} else if (i == 5) {
					break;
				}
			}
		}

		return chordProgList;
	}

	private List<int[]> generateChordProgression(int mainGeneratorSeed, int fixedLength) {
		Random generator = new Random(mainGeneratorSeed);
		Random lengthGenerator = new Random(mainGeneratorSeed);
		Random spiceGenerator = new Random(mainGeneratorSeed);
		Random parallelGenerator = new Random(mainGeneratorSeed + 100);
		Random similarityGenerator = new Random(mainGeneratorSeed + 102);

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
					&& MidiUtils.BANNED_DIM_AUG_6_LIST.contains(chordString)) {
				continue;
			}
			if (!gc.isEnable9th13th() && MidiUtils.BANNED_9_13_LIST.contains(chordString)) {
				continue;
			}
			allowedSpiceChordsMiddle.add(chordString);
		}

		List<String> allowedSpiceChords = new ArrayList<>();
		for (String s : allowedSpiceChordsMiddle) {
			if (MidiUtils.BANNED_DIM_AUG_6_LIST.contains(s)
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
			String tempSpicyChordString = MidiGeneratorUtils
					.generateSpicyChordString(spiceGenerator, chordString, spicyChordList);

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
					LG.d("PARALLEL: " + spicyChordString);
				}
			}

			chordInts.add(spicyChordString);


			//LG.d("Fetching chord: " + chordInt);
			int[] mappedChord = mappedChord(spicyChordString);
			/*mappedChord = transposeChord(mappedChord, Mod.MAJOR_SCALE,
					gc.getScaleMode().noteAdjustScale);*/


			debugMsg.add("Generated int: " + nextInt + ", for chord: " + spicyChordString
					+ ", dur: " + dur + ", C[" + Arrays.toString(mappedChord) + "]");
			cpr.add(mappedChord);
			progressionDurations.add(dur);

			prevChord = mappedChord;
			//LG.d("Getting next for chord: " + chordString);
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
		LG.d("CHORD PROG LENGTH: " + cpr.size());
		if (isBackwards) {
			Collections.reverse(progressionDurations);
			Collections.reverse(cpr);
			Collections.reverse(debugMsg);
			Collections.reverse(chordInts);
			//FIRST_CHORD = lastChord;
			//LAST_CHORD = firstChord;
		} else {
			//FIRST_CHORD = firstChord;
			//LAST_CHORD = lastChord;
		}

		for (String s : debugMsg) {
			LG.d(s);
		}

		// similarity generation - replace chords 4-7 with chords from 0-3
		if (fixedLength == 8) {
			int[] replacementOrder = new int[] { 4, 7, 5, 6 };
			for (int i : replacementOrder) {
				if (similarityGenerator.nextInt() < gc.getLongProgressionSimilarity()) {
					chordInts.set(i, chordInts.get(i - 4));
					cpr.set(i, cpr.get(i - 4).clone());
					LG.i("Replaced " + i + "-th chord!");
				} else if (i == 5) {
					break;
				}
			}
		}

		if (progressionDurations.size() > 2
				&& (progressionDurations.get(0) != progressionDurations.get(2))) {
			double middle = (progressionDurations.get(0) + progressionDurations.get(2)) / 2.0;
			progressionDurations.set(0, middle);
			progressionDurations.set(2, middle);

		}

		return cpr;
	}

	private Vector<Note> algoGen2GenerateMelodySkeletonFromChords(MelodyPart mp, List<int[]> chords,
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

		int seed = mp.getPatternSeedWithPartOffset();

		Vector<Note> noteList = new Vector<>();

		Random algoGenerator = new Random(gc.getRandomSeed());
		if (algoGenerator.nextInt(100) < gc.getMelodyUseOldAlgoChance()) {
			return algoGen1GenerateMelodySkeletonFromChords(mp, measures, roots);
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
				? MidiGeneratorUtils.generateMelodyDirectionChordDividers(stretchedChords.size(),
						directionGenerator)
				: null;
		directionGenerator.setSeed(seed + 10);
		boolean currentDirection = directionGenerator.nextBoolean();
		if (!gc.isMelodyUseDirectionsFromProgression()) {
			LG.d("Direction dividers: " + directionChordDividers.toString() + ", start at: "
					+ currentDirection);
		}

		List<Boolean> directionsFromChords = (gc.isMelodyUseDirectionsFromProgression())
				? MidiGeneratorUtils.generateMelodyDirectionsFromChordProgression(usedChords, true)
				: null;

		boolean alternateRhythm = alternateRhythmGenerator.nextInt(100) < ALTERNATE_RHYTHM_CHANCE;
		//LG.d("Alt: " + alternateRhythm);

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
							LG.i("Melody variation: " + var);
						}

						switch (var) {
						case 0:
							// only add, processed later
							break;
						case 1:
							MAX_JUMP_SKELETON_CHORD = Math.min(4, MAX_JUMP_SKELETON_CHORD + 1);
							break;
						default:
							break;
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
								LG.d("Double pattern - surprise!");
								durations.addAll(arpedDurations);
							} else {
								durations.addAll(durations);
							}
						}
					} else if (i % 2 == 1 && durations.size() >= 4) {

						List<Double> arpedDurations = makeSurpriseTrioArpedDurations(durations);
						if (arpedDurations != null) {
							LG.d("Single pattern - surprise!");
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
							startIndex = MidiGeneratorUtils.selectClosestIndexFromChord(chord,
									previousNotePitch, true);
							while (endIndex - startIndex > MAX_JUMP_SKELETON_CHORD) {
								endIndex--;
							}
						} else {
							endIndex = MidiGeneratorUtils.selectClosestIndexFromChord(chord,
									previousNotePitch, false);
							while (endIndex - startIndex > MAX_JUMP_SKELETON_CHORD) {
								startIndex++;
							}
						}
					}
					double positionInChord = durCounter / progressionDurations.get(i);
					pitch = MidiGeneratorUtils.pickRandomBetweenIndexesInclusive(chord, startIndex,
							endIndex, pitchPickerGenerator, positionInChord);

					double swingDuration = durations.get(j);
					Note n = new Note(pitch, swingDuration, 100);
					n.setDuration(swingDuration * (0.75 + durationGenerator.nextDouble() / 4)
							* GLOBAL_DURATION_MULTIPLIER);

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
			sec.setVariation(0, mp.getAbsoluteOrder(), variations);
		}
		return noteList;
	}

	private Note algoGen1GenerateNote(MelodyPart mp, int[] chord, boolean isAscDirection,
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
				LG.d("ERROR PITCH -1 for: " + chosenPitch);
				previousPitch = chordScale.indexOf(Integer.valueOf(chosenPitch + 1));
				if (previousPitch == -1) {
					LG.i("NOT EVEN +1 pitch exists for " + chosenPitch + "!");
				}
			}

			//LG.d(firstChord[chordNote] + " > from first chord");
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
			//LG.d("UNSAMING NOTE!: " + previousPitch + ", BY: " + (-direction * change));
			generatedPitch = maX(previousPitch - direction * change, maxAllowedScaleNotes);
			samePitchCount = 0;
		}
		previousPitch = generatedPitch;
		if (isPause) {
			return new Note(Integer.MIN_VALUE, dur);
		}
		return new Note(chordScale.get(generatedPitch), dur, velMin + generator.nextInt(velSpace));

	}

	private Note[] algoGen1GenerateMelodyForChord(MelodyPart mp, int[] chord, double maxDuration,
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
				//LG.d("Exception used for chordnote: " + chord[0]);
				exceptionChangeUsed = true;
				actualDirection = !actualDirection;
			}
			Note note = algoGen1GenerateNote(mp, chord, actualDirection, scale, previousNote,
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

	private Vector<Note> algoGen1GenerateMelodySkeletonFromChords(MelodyPart mp, int measures,
			List<int[]> genRootProg) {
		List<Boolean> directionProgression = MidiGeneratorUtils
				.generateMelodyDirectionsFromChordProgression(genRootProg, true);

		Note previousChordsNote = null;

		Note[] pair024 = null;
		Note[] pair15 = null;
		Random melodyGenerator = new Random();
		if (!mp.isMuted() && mp.getPatternSeedWithPartOffset() != 0) {
			melodyGenerator.setSeed(mp.getPatternSeedWithPartOffset());
		} else {
			melodyGenerator.setSeed(gc.getRandomSeed());
		}
		LG.i("LEGACY ALGORITHM!");
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
					generatedMelody = algoGen1GenerateMelodyForChord(mp, genRootProg.get(j),
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
		LG.i("========================== MIDI GENERATION IN PROGRESS, " + new Date().toString()
				+ " ===========================");
		long systemTime = System.currentTimeMillis();
		boolean logPerformance = false;
		customDrumMappingNumbers = null;
		trackList.clear();
		//MELODY_SCALE = gc.getScaleMode().absoluteNotesC;

		Score score = new Score("MainScore", 120);
		Score scoreFull = new Score("MainScore", 120);

		List<PartExt> melodyParts = new ArrayList<>();
		for (int i = 0; i < gc.getMelodyParts().size(); i++) {
			PartExt p = new PartExt("Melodies" + i, gc.getMelodyParts().get(i).getInstrument(),
					gc.getMelodyParts().get(i).getMidiChannel() - 1);
			melodyParts.add(p);
		}

		List<PartExt> melodyPartsFull = new ArrayList<>();
		for (int i = 0; i < gc.getMelodyParts().size(); i++) {
			PartExt p = new PartExt("Melodies" + i, gc.getMelodyParts().get(i).getInstrument(),
					gc.getMelodyParts().get(i).getMidiChannel() - 1);
			melodyPartsFull.add(p);
		}

		List<PartExt> chordParts = new ArrayList<>();
		for (int i = 0; i < gc.getChordParts().size(); i++) {
			PartExt p = new PartExt("Chords" + i, gc.getChordParts().get(i).getInstrument(),
					gc.getChordParts().get(i).getMidiChannel() - 1);
			chordParts.add(p);
		}

		List<PartExt> arpParts = new ArrayList<>();
		for (int i = 0; i < gc.getArpParts().size(); i++) {
			PartExt p = new PartExt("Arps" + i, gc.getArpParts().get(i).getInstrument(),
					gc.getArpParts().get(i).getMidiChannel() - 1);
			arpParts.add(p);
		}


		List<PartExt> bassParts = new ArrayList<>();
		for (int i = 0; i < gc.getBassParts().size(); i++) {
			PartExt p = new PartExt("Bass" + i, gc.getBassParts().get(i).getInstrument(),
					gc.getBassParts().get(i).getMidiChannel() - 1);
			bassParts.add(p);
		}


		List<PartExt> drumParts = new ArrayList<>();
		for (int i = 0; i < gc.getDrumParts().size(); i++) {
			PartExt p = new PartExt("MainDrums", 0, 9);
			drumParts.add(p);
		}

		List<PartExt> drumPartsFull = new ArrayList<>();
		for (int i = 0; i < gc.getDrumParts().size(); i++) {
			PartExt p = new PartExt("MainDrums", 0, 9);
			drumPartsFull.add(p);
		}


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

		List<int[]> generatedRootProgression = (userRootProgression != null) ? userRootProgression
				: generateChordProgression(mainGeneratorSeed,
						!userChordsDurations.isEmpty() ? userChordsDurations.size()
								: gc.getFixedDuration());
		if (!userChordsDurations.isEmpty()) {
			progressionDurations = userChordsDurations;
		}

		SectionConfig sc = (currentSection != null) ? currentSection.getSecConfig() : null;
		int beatDurMultiIndex = (sc != null && sc.getBeatDurationMultiplierIndex() != null)
				? sc.getBeatDurationMultiplierIndex()
				: gc.getBeatDurationMultiplierIndex();
		if (beatDurMultiIndex == 0) {
			for (int i = 0; i < progressionDurations.size(); i++) {
				progressionDurations.set(i, progressionDurations.get(i) * 0.5);
			}
		} else if (beatDurMultiIndex == 2) {
			for (int i = 0; i < progressionDurations.size(); i++) {
				progressionDurations.set(i, progressionDurations.get(i) * 2);
			}
		}

		List<Double> actualDurations = progressionDurations;

		List<int[]> actualProgression = (gc.isSquishProgressively())
				? MidiUtils.squishChordProgressionProgressively(
						(userProgression != null ? userProgression : generatedRootProgression),
						gc.isSpiceFlattenBigChords(), gc.getRandomSeed(),
						gc.getChordGenSettings().getFlattenVoicingChance(),
						customInversionIndexList, userRootProgression)
				: MidiUtils.squishChordProgression(
						(userProgression != null ? userProgression : generatedRootProgression),
						gc.isSpiceFlattenBigChords(), gc.getRandomSeed(),
						gc.getChordGenSettings().getFlattenVoicingChance(),
						customInversionIndexList, userRootProgression);

		if (!debugEnabled) {
			PrintStream dummyStream = new PrintStream(new OutputStream() {
				public void write(int b) {
					// NO-OP
				}
			});
			System.setOut(dummyStream);
		}
		if (logPerformance) {
			LG.i("Generated chords, starting arrangement after: "
					+ (System.currentTimeMillis() - systemTime));
		}
		// Arrangement process..
		LG.i("Starting arrangement..");


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
					generatedRootProgression, 0, new Section(), new ArrayList<>(), true);
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
		gc.getArrangement().recalculatePartInclusionMapBoundsIfNeeded();
		for (Section sec : arr.getSections()) {
			LG.i("*********************************** Processing section.. " + sec.getType()
					+ "!***** Time: " + (System.currentTimeMillis() - systemTime));
			currentSection = sec;
			if (overridden) {
				sec.initPartMapFromOldData();
			}
			sec.setSectionDuration(-1);
			sec.setSectionBeatDurations(null);
			boolean gcPartsReplaced = replaceGuiConfigInstParts(sec);
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

			if (!overridden && sec.getType().toUpperCase().startsWith("BUILDUP")) {
				if (rand.nextInt(100) < gc.getArrangementVariationChance()) {
					List<Integer> exceptionChanceList = new ArrayList<>();
					exceptionChanceList.add(1);
					if (sec.getPartMap().get(4) != null) {
						for (int i = 0; i < sec.getPartMap().get(4).length; i++) {
							if (rand.nextInt(100) < 66) {
								sec.setVariation(4, i, exceptionChanceList);
							}

						}
					}
				}
			}

			int notesSeedOffset = sec.getTypeMelodyOffset();

			Random variationGen = new Random(arrSeed + sec.getTypeSeedOffset());
			List<Integer> sectionVariations = calculateSectionVariations(arr, secOrder, sec,
					notesSeedOffset, variationGen);
			List<String> includedSectionVarNames = new ArrayList<>();
			for (int i = 0; i < sectionVariations.size(); i++) {
				if (sectionVariations.get(i) > 0) {
					includedSectionVarNames.add(Section.sectionVariationNames[i]);
				}
			}
			LG.i("Section Variations: " + StringUtils.join(includedSectionVarNames, ","));

			// generate transition
			if (!overridden) {
				int transChance = variationGen.nextInt(100);
				int[] rawChances = new int[Section.transitionChanceMultipliers.length];
				for (int i = 0; i < rawChances.length; i++) {
					rawChances[i] = (int) (gc.getArrangementVariationChance()
							* Section.transitionChanceMultipliers[i]);
				}
				int transType = 0;
				for (int i = 1; i < Section.transitionChanceMultipliers.length; i++) {
					if (transChance >= rawChances[i]) {
						continue;
					}
					if (i == 1) {
						if ((secOrder < arr.getSections().size() - 1
								&& arr.getSections().get(secOrder + 1).getTypeMelodyOffset() == 0
								&& notesSeedOffset > 0)) {
							transType = 1;
							break;
						}
					} else if (i == 2) {
						if ((secOrder < arr.getSections().size() - 1

								&& notesSeedOffset == 0)) {
							transType = 2;
							break;
						}
					}
					if (i > 2) {
						transType = i;
						break;
					}

				}
				sec.setTransitionType(transType);
			}
			LG.i("Transition type: " + sec.getTransitionType() + ", MVI: " + notesSeedOffset);


			// reset back to normal?
			boolean sectionChordsReplaced = false;
			if (sec.isCustomChordsEnabled() || sec.isCustomDurationsEnabled()) {
				sectionChordsReplaced = replaceWithSectionCustomChordDurations(sec);
			}
			if (!sectionChordsReplaced) {
				sec.setGeneratedSectionBeatDurations(new ArrayList<>(progressionDurations));

				if (sectionVariations.get(1) > 0 && alternateChords != null
						&& !alternateChords.isEmpty()) {
					//LG.d("Section Variation: Chord Swap!");
					rootProgression = melodyBasedRootProgression;
					chordProgression = melodyBasedChordProgression;
					progressionDurations = actualDurations;
					sec.setDisplayAlternateChords(true);
					sec.setCustomChords(alternateChords);
				} else {
					rootProgression = generatedRootProgression;
					chordProgression = actualProgression;
					progressionDurations = actualDurations;
					sec.setDisplayAlternateChords(false);

				}
			} else if (rootProgression.size() == generatedRootProgression.size()) {
				if (sectionVariations.get(1) > 0) {
					//LG.d("Section Variation: Chord Swap!");
					rootProgression = melodyBasedRootProgression;
					chordProgression = melodyBasedChordProgression;
					progressionDurations = actualDurations;
				}
			}

			SectionConfig secC = sec.getSecConfig();

			if (sectionVariations.get(4) > 0) {
				//LG.d("Section Variation: Key Change (on next chord)!");
				if (secC.getCustomKeyChange() == null && secC.getCustomScale() == null) {
					transToSet = generateKeyChange(generatedRootProgression, arrSeed);
					LG.i("Generated key change: " + transToSet);
					secC.setCustomKeyChange(transToSet);
				} else {
					transToSet = secC.getCustomKeyChange() != null ? secC.getCustomKeyChange() : 0;
					if (secC.getCustomScale() != null) {
						scaleToSet = secC.getCustomScale();
					}
					LG.i("Using custom key change: " + transToSet + ", with ScaleMode: "
							+ scaleToSet);
				}
			}

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

			calculatePresencesForSection(sec, rand, variationGen, overridden, sectionVariations,
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


		Optional<MelodyPart> firstPresentPart = gc.getMelodyParts().stream()
				.filter(e -> !e.isMuted()).findFirst();

		for (Section sec : arr.getSections()) {
			for (int i = 0; i < sec.getMelodies().size(); i++) {
				Phrase p = sec.getMelodies().get(i);
				p.setStartTime(p.getStartTime() + sec.getStartTime());
				p.setAppend(false);
				if (!gc.isCombineMelodyTracks()) {
					melodyParts.get(i).addPhrase(p);
				} else {
					if (firstPresentPart.isPresent()) {
						melodyParts.get(VibeComposerGUI.getAbsoluteOrder(0,
								firstPresentPart.get().getOrder())).addPhrase(p);
					}
				}
				melodyPartsFull.get(i).addPhrase(p.copy());
			}
			for (int i = 0; i < sec.getBasses().size(); i++) {
				Phrase bp = sec.getBasses().get(i);
				bp.setStartTime(bp.getStartTime() + sec.getStartTime());
				bassParts.get(i).addPhrase(bp);
			}
			for (int i = 0; i < sec.getChords().size(); i++) {
				Phrase cp = sec.getChords().get(i);
				cp.setStartTime(cp.getStartTime() + sec.getStartTime());
				chordParts.get(i).addPhrase(cp);
			}
			for (int i = 0; i < sec.getArps().size(); i++) {
				Phrase cp = sec.getArps().get(i);
				cp.setStartTime(cp.getStartTime() + sec.getStartTime());
				arpParts.get(i).addPhrase(cp);
			}

			Optional<DrumPart> firstPresentDrumPart = gc.getDrumParts().stream()
					.filter(e -> !e.isMuted()).findFirst();
			for (int i = 0; i < sec.getDrums().size(); i++) {
				Phrase p = sec.getDrums().get(i);
				p.setStartTime(p.getStartTime() + sec.getStartTime());
				if (COLLAPSE_DRUM_TRACKS && firstPresentDrumPart.isPresent()) {
					p.setAppend(false);
					drumParts.get(VibeComposerGUI.getAbsoluteOrder(4,
							firstPresentDrumPart.get().getOrder())).addPhrase(p);
				} else {
					drumParts.get(i).addPhrase(p);
				}
				drumPartsFull.get(i).addPhrase(p.copy());

			}
			if (gc.getChordParts().size() > 0 && gc.isChordsEnable()) {
				Phrase csp = sec.getChordSlash();
				csp.setStartTime(csp.getStartTime() + sec.getStartTime());
				csp.setAppend(false);
				chordParts.get(0).addPhrase(csp);
			}

		}
		if (logPerformance) {
			LG.i("Added to parts, at: " + (System.currentTimeMillis() - systemTime));
		}
		LG.d("Added sections to parts..");
		setupScore(mainGeneratorSeed, systemTime, logPerformance, score, melodyParts, chordParts,
				arpParts, bassParts, drumParts, true, true);
		setupScore(mainGeneratorSeed, systemTime, logPerformance, scoreFull, melodyPartsFull,
				chordParts, arpParts, bassParts, drumPartsFull, false, false);

		score.setTempo(gc.getBpm());
		scoreFull.setTempo(gc.getBpm());

		// write midi without log

		PrintStream dummyStream = new PrintStream(new OutputStream() {
			public void write(int b) {
				// NO-OP
			}
		});
		System.setOut(dummyStream);

		JMusicUtilsCustom.midi(score, fileName);
		JMusicUtilsCustom.midi(scoreFull, VibeComposerGUI.TEMPORARY_SEQUENCE_MIDI_NAME);
		if (VibeComposerGUI.dconsole == null || !VibeComposerGUI.dconsole.getFrame().isVisible()) {
			System.setOut(originalStream);
		} else {
			VibeComposerGUI.dconsole.redirectOut();
		}


		// view midi
		LAST_SCORES.add(0, scoreFull);
		if (LAST_SCORES.size() > LAST_SCORES_LIMIT) {
			LAST_SCORES = LAST_SCORES.subList(0, LAST_SCORES_LIMIT);
		}

		gc.setActualArrangement(arr);
		LG.i("MidiGenerator time: " + (System.currentTimeMillis() - systemTime) + " ms");
		LG.i("********Viewing midi seed: " + mainGeneratorSeed + "************* ");
	}

	private void setupScore(int mainGeneratorSeed, long systemTime, boolean logPerformance,
			Score score, List<PartExt> melodyParts, List<PartExt> chordParts,
			List<PartExt> arpParts, List<PartExt> bassParts, List<PartExt> drumParts,
			boolean allowCombination, boolean transposeBCA) {
		int trackCounter = 1;

		List<Integer> partPadding = VibeComposerGUI.padGeneratedMidi.isSelected()
				? VibeComposerGUI.padGeneratedMidiValues.getValues()
				: new ArrayList<>();
		int lastPartTrackCount = 1;
		for (int i = 0; i < melodyParts.size(); i++) {
			InstPanel ip = VibeComposerGUI.getPanelByOrder(gc.getMelodyParts().get(i).getOrder(),
					VibeComposerGUI.melodyPanels);
			if (!gc.getMelodyParts().get(i).isMuted() && gc.isMelodyEnable()) {
				score.add(melodyParts.get(i));
				((PartExt) melodyParts.get(i)).setTrackNumber(trackCounter);
				ip.setSequenceTrack(trackCounter++);
				if (allowCombination && gc.isCombineMelodyTracks()) {
					for (int j = i + 1; j < gc.getMelodyParts().size(); j++) {
						ip = VibeComposerGUI.getPanelByOrder(gc.getMelodyParts().get(j).getOrder(),
								VibeComposerGUI.melodyPanels);
						ip.setSequenceTrack(-1);
					}
					break;
				}
			} else {
				trackCounter += padSingle(score, partPadding, 0, trackCounter - lastPartTrackCount);
				ip.setSequenceTrack(-1);
			}
		}

		if (!transposeBCA) {
			Mod.transpose(score, gc.getTranspose());
		}

		trackCounter += padScoreParts(score, partPadding, 0, trackCounter - lastPartTrackCount);
		lastPartTrackCount = trackCounter;


		for (int i = 0; i < bassParts.size(); i++) {
			InstPanel ip = VibeComposerGUI.getPanelByOrder(gc.getBassParts().get(i).getOrder(),
					VibeComposerGUI.bassPanels);
			if (!gc.getBassParts().get(i).isMuted() && gc.isBassEnable()) {
				score.add(bassParts.get(i));
				((PartExt) bassParts.get(i)).setTrackNumber(trackCounter);
				ip.setSequenceTrack(trackCounter++);
			} else {
				trackCounter += padSingle(score, partPadding, 0, trackCounter - lastPartTrackCount);
				ip.setSequenceTrack(-1);
			}
		}
		trackCounter += padScoreParts(score, partPadding, 1, trackCounter - lastPartTrackCount);
		lastPartTrackCount = trackCounter;

		for (int i = 0; i < chordParts.size(); i++) {

			InstPanel ip = VibeComposerGUI.getPanelByOrder(gc.getChordParts().get(i).getOrder(),
					VibeComposerGUI.chordPanels);
			if (!gc.getChordParts().get(i).isMuted() && gc.isChordsEnable()) {
				score.add(chordParts.get(i));
				((PartExt) chordParts.get(i)).setTrackNumber(trackCounter);
				ip.setSequenceTrack(trackCounter++);
			} else {
				trackCounter += padSingle(score, partPadding, 0, trackCounter - lastPartTrackCount);
				ip.setSequenceTrack(-1);
			}

		}
		trackCounter += padScoreParts(score, partPadding, 2, trackCounter - lastPartTrackCount);
		lastPartTrackCount = trackCounter;

		for (int i = 0; i < arpParts.size(); i++) {

			InstPanel ip = VibeComposerGUI.getPanelByOrder(gc.getArpParts().get(i).getOrder(),
					VibeComposerGUI.arpPanels);
			if (!gc.getArpParts().get(i).isMuted() && gc.isArpsEnable()) {
				score.add(arpParts.get(i));
				((PartExt) arpParts.get(i)).setTrackNumber(trackCounter);
				ip.setSequenceTrack(trackCounter++);
			} else {
				trackCounter += padSingle(score, partPadding, 0, trackCounter - lastPartTrackCount);
				ip.setSequenceTrack(-1);
			}
		}
		trackCounter += padScoreParts(score, partPadding, 3, trackCounter - lastPartTrackCount);
		lastPartTrackCount = trackCounter;
		/*if (gc.getScaleMode() != ScaleMode.IONIAN) {
			for (Part p : score.getPartArray()) {
				for (Phrase phr : p.getPhraseArray()) {
					MidiUtils.transposePhrase(phr, ScaleMode.IONIAN.noteAdjustScale,
							gc.getScaleMode().noteAdjustScale);
				}
			}
		}*/
		//int[] backTranspose = { 0, 2, 4, 5, 7, 9, 11, 12 };
		if (transposeBCA) {
			Mod.transpose(score, gc.getTranspose());
		}


		// add drums after transposing transposable parts
		for (int i = 0; i < drumParts.size(); i++) {
			if (!allowCombination || !COLLAPSE_DRUM_TRACKS) {
				score.add(drumParts.get(i));
			}
			InstPanel ip = VibeComposerGUI.getPanelByOrder(gc.getDrumParts().get(i).getOrder(),
					VibeComposerGUI.drumPanels);
			if (!gc.getDrumParts().get(i).isMuted() && gc.isDrumsEnable()) {
				ip.setSequenceTrack(trackCounter);
				((PartExt) drumParts.get(i)).setTrackNumber(trackCounter);
				if (allowCombination && COLLAPSE_DRUM_TRACKS) {
					score.add(drumParts.get(i));
					for (int j = i + 1; j < gc.getDrumParts().size(); j++) {
						InstPanel ip2 = VibeComposerGUI.getPanelByOrder(
								gc.getDrumParts().get(j).getOrder(), VibeComposerGUI.drumPanels);
						ip2.setSequenceTrack(-1);
					}
					break;
				}
			} else {
				ip.setSequenceTrack(-1);
			}
			if (!allowCombination || !COLLAPSE_DRUM_TRACKS) {
				trackCounter++;
			}
		}
		if (!allowCombination || !COLLAPSE_DRUM_TRACKS) {
			trackCounter += padScoreParts(score, partPadding, 4, trackCounter - lastPartTrackCount);
			//lastPartTrackCount = trackCounter;
		}


		if (logPerformance) {
			LG.i("Added to score, at: " + (System.currentTimeMillis() - systemTime));
		}
		LG.d("Added parts to score.., allow combo: " + allowCombination);
		Random rand = new Random(mainGeneratorSeed + 999);
		for (Object o : score.getPartList()) {
			PartExt pe = (PartExt) o;
			if (pe == null || pe.isFillerPart()) {
				continue;
			}
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
						.anyMatch(drumTime -> Math.abs(drumTime - time) < DBL_ERR))) {
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

					if (accentGenerator.nextInt(100) >= mp.getAccents()) {
						continue;
					}

					if (n.getDuration() - DBL_ERR < Durations.SIXTEENTH_NOTE) {
						continue;
					}

					// small 32nd buffer to prevent cutting notes that would result in too small leftovers
					double startTime = n.getOffset() + currTime + Durations.SIXTEENTH_NOTE / 2
							+ DBL_ERR;
					double endTime = startTime + n.getDuration() - Durations.SIXTEENTH_NOTE / 2
							- DBL_ERR;
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
					int originalPitch = n.getPitch();
					Note splitNote = new Note(originalPitch, 0, n.getDynamic());
					splitNote.setDuration(n.getDuration() - intersectionLength);
					splitNote.setOffset(n.getOffset() + intersectionLength);
					switch (gc.getMelodyRhythmAccentsMode()) {
					case 0:
						splitNote.setPitch(Note.REST);
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

	private int padScoreParts(Score score, List<Integer> partPadding, int part, int trackCount) {
		if (paddable(partPadding, part, trackCount)) {
			int tracksToPad = partPadding.get(part) - trackCount;
			LG.d("Padding: " + part + ", #: " + tracksToPad);
			for (int i = 0; i < tracksToPad; i++) {
				score.add(PartExt.makeFillerPart());
			}
			return tracksToPad;
		}
		return 0;
	}

	private int padSingle(Score score, List<Integer> partPadding, int part, int trackCount) {
		if (paddable(partPadding, part, trackCount)) {
			LG.d("Padding Single: " + part + ", #: " + 1);
			score.add(PartExt.makeFillerPart());
			return 1;
		}
		return 0;
	}

	private boolean paddable(List<Integer> partPadding, int part, int trackCount) {
		return gc.isPartEnabled(part) && partPadding.size() > part
				&& trackCount < partPadding.get(part);
	}

	private List<Integer> calculateSectionVariations(Arrangement arr, int secOrder, Section sec,
			int notesSeedOffset, Random variationGen) {
		List<Integer> sectionVariations = sec.getSectionVariations();
		if (sectionVariations == null) {
			sectionVariations = new ArrayList<>();
			for (int i = 0; i < Section.sectionVariationNames.length; i++) {
				boolean isVariation = variationGen
						.nextInt(100) < (gc.getArrangementVariationChance()
								* Section.sectionVariationChanceMultipliers[i]);
				isVariation &= gc.getArrangement().isGlobalVariation(5, i);

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
				sectionVariations.add(isVariation ? 1 : 0);
			}
			sec.setSectionVariations(sectionVariations);
		}

		return sectionVariations;
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
				MelodyPart mp = (MelodyPart) gc.getMelodyParts().get(i);
				boolean added = presences.contains(mp.getOrder());
				if (added) {
					List<int[]> usedMelodyProg = chordProgression;
					List<int[]> usedRoots = rootProgression;

					// if n-1, do not also swap melody
					if (sectionVariations.get(2) > 0 && sectionVariations.get(0) == 0
							&& !sectionChordsReplaced) {
						usedMelodyProg = melodyBasedChordProgression;
						usedRoots = melodyBasedRootProgression;
						//LG.d("Section Variation: Melody Swap!");
					}
					List<Integer> variations = (overridden) ? sec.getVariation(0, i) : null;
					int speedSave = mp.getSpeed();
					// max speed variation
					if (sectionVariations.get(3) > 0) {
						mp.setSpeed(100);
					}
					Phrase m = fillMelodyFromPart(mp, usedMelodyProg, usedRoots, notesSeedOffset,
							sec, variations, false);
					if (sectionVariations.get(3) > 0) {
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
					Note emptyMeasureNote = new Note(Integer.MIN_VALUE, measureLength);
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
		Note emptyMeasureNote = new Note(Integer.MIN_VALUE, measureLength);
		Phrase emptyPhrase = new PhraseExt();
		emptyPhrase.setStartTime(START_TIME_DELAY);
		emptyPhrase.add(emptyMeasureNote);

		if (gc.isBassEnable() && !gc.getBassParts().isEmpty()) {
			List<Phrase> copiedPhrases = new ArrayList<>();
			Set<Integer> presences = sec.getPresence(1);
			for (int i = 0; i < gc.getBassParts().size(); i++) {
				BassPart bp = (BassPart) gc.getBassParts().get(i);
				boolean added = presences.contains(bp.getOrder());
				if (added) {
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
				ChordPart cp = (ChordPart) gc.getChordParts().get(i);
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
				ArpPart ap = (ArpPart) gc.getArpParts().get(i);
				// if arp1 supports melody with same instrument, always introduce it in second half
				List<Integer> variations = (overridden) ? sec.getVariation(3, i) : null;
				boolean added = presences.contains(ap.getOrder());
				if (added) {
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
				DrumPart dp = (DrumPart) gc.getDrumParts().get(i);
				variationGen.setSeed(arrSeed + 300 + dp.getOrder());

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

	private void calculatePresencesForSection(Section sec, Random rand, Random variationGen,
			boolean overridden, List<Integer> sectionVariations, int arrSeed, int notesSeedOffset,
			boolean isPreview, int counter, Arrangement arr) {
		if (gc.isMelodyEnable() && !gc.getMelodyParts().isEmpty()) {
			Set<Integer> presences = sec.getPresence(0);
			for (int i = 0; i < gc.getMelodyParts().size(); i++) {
				MelodyPart mp = (MelodyPart) gc.getMelodyParts().get(i);
				int melodyChanceMultiplier = (sec.getTypeMelodyOffset() == 0 && i == 0) ? 2 : 1;
				// temporary increase for chance of main (#1) melody
				int oldChance = sec.getMelodyChance();
				sec.setMelodyChance(Math.min(100, oldChance * melodyChanceMultiplier));
				boolean added = !mp.isMuted() && ((overridden && presences.contains(mp.getOrder()))
						|| (!overridden && rand.nextInt(100) < sec.getMelodyChance()));
				added &= gc.getArrangement().isPartInclusion(0, i, notesSeedOffset);
				if (added && !overridden) {
					sec.setPresence(0, i);
				}
				sec.setMelodyChance(oldChance);
			}
		}

		rand.setSeed(arrSeed + 10);
		variationGen.setSeed(arrSeed + 10);
		if (gc.isBassEnable() && !gc.getBassParts().isEmpty()) {
			Set<Integer> presences = sec.getPresence(1);
			for (int i = 0; i < gc.getBassParts().size(); i++) {
				BassPart bp = (BassPart) gc.getBassParts().get(i);
				rand.setSeed(arrSeed + 50 + bp.getOrder());
				variationGen.setSeed(arrSeed + 50 + bp.getOrder());
				boolean added = (overridden && presences.contains(bp.getOrder()))
						|| (!overridden && rand.nextInt(100) < sec.getBassChance());
				added &= gc.getArrangement().isPartInclusion(1, i, notesSeedOffset);
				if (added && !bp.isMuted()) {
					if (!overridden)
						sec.setPresence(1, i);
				}
			}
		}

		if (gc.isChordsEnable() && !gc.getChordParts().isEmpty()) {
			Set<Integer> presences = sec.getPresence(2);
			for (int i = 0; i < gc.getChordParts().size(); i++) {
				ChordPart cp = (ChordPart) gc.getChordParts().get(i);
				rand.setSeed(arrSeed + 100 + cp.getOrder());
				variationGen.setSeed(arrSeed + 100 + cp.getOrder());
				boolean added = (overridden && presences.contains(cp.getOrder()))
						|| (!overridden && rand.nextInt(100) < sec.getChordChance());
				added &= gc.getArrangement().isPartInclusion(2, i, notesSeedOffset);
				if (added && !cp.isMuted()) {
					if (!overridden)
						sec.setPresence(2, i);
				}
			}
		}

		if (gc.isArpsEnable() && !gc.getArpParts().isEmpty()) {
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

				added &= gc.getArrangement().isPartInclusion(3, i, notesSeedOffset);
				if (added) {
					if (!overridden)
						sec.setPresence(3, i);
				}
			}
		}

		if (gc.isDrumsEnable() && !gc.getDrumParts().isEmpty()) {
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
				added &= gc.getArrangement().isPartInclusion(4, i, notesSeedOffset);
				if (added && !dp.isMuted()) {
					if (!overridden)
						sec.setPresence(4, i);
				}
			}
		}
	}

	public boolean replaceWithSectionCustomChordDurations(Section sec) {
		if (!sec.isCustomChordsEnabled() && !sec.isCustomDurationsEnabled()) {
			return false;
		}

		List<String> chords = sec.getCustomChordsList();
		if ((chords == null || chords.isEmpty()) && sec.isCustomChordsEnabled()) {
			return false;
		}
		List<Double> durations = sec.getCustomDurationsList();
		if ((durations == null || durations.isEmpty()) && sec.isCustomDurationsEnabled()) {
			return false;
		}

		if (sec.isCustomChordsEnabled() && sec.isCustomDurationsEnabled()) {
			if (chords.size() != durations.size()) {
				return false;
			}
		} else if (sec.isCustomChordsEnabled()) {
			durations = new ArrayList<>();
			for (int i = 0; i < chords.size(); i++) {
				durations.add((i < progressionDurations.size() && !progressionDurations.isEmpty())
						? progressionDurations.get(i)
						: Durations.WHOLE_NOTE);
			}
		} else {
			chords = generateChordProgressionList(gc.getRandomSeed(), durations.size());
		}

		List<int[]> mappedChords = new ArrayList<>();
		List<int[]> mappedRootChords = new ArrayList<>();
		List<Integer> customInversionIndexList = new ArrayList<>();
		/*chordInts.clear();
		chordInts.addAll(userChords);*/
		int chordNum = 0;
		for (String chordString : chords) {
			mappedChords.add(mappedChord(chordString));
			mappedRootChords.add(mappedChord(chordString, true));
			if (chordString.contains(".")) {
				customInversionIndexList.add(chordNum);
			}
			chordNum++;
		}

		mappedChords = (gc.isSquishProgressively())
				? MidiUtils.squishChordProgressionProgressively(mappedChords,
						gc.isSpiceFlattenBigChords(), gc.getRandomSeed(),
						gc.getChordGenSettings().getFlattenVoicingChance(),
						customInversionIndexList, mappedRootChords)
				: MidiUtils.squishChordProgression(mappedChords, gc.isSpiceFlattenBigChords(),
						gc.getRandomSeed(), gc.getChordGenSettings().getFlattenVoicingChance(),
						customInversionIndexList, mappedRootChords);


		chordProgression = mappedChords;
		rootProgression = mappedRootChords;
		progressionDurations = durations;

		SectionConfig sc = (currentSection != null) ? currentSection.getSecConfig() : null;
		int beatDurMultiIndex = (sc != null && sc.getBeatDurationMultiplierIndex() != null)
				? sc.getBeatDurationMultiplierIndex()
				: gc.getBeatDurationMultiplierIndex();
		if (beatDurMultiIndex == 0) {
			for (int i = 0; i < progressionDurations.size(); i++) {
				progressionDurations.set(i, progressionDurations.get(i) * 0.5);
			}
		} else if (beatDurMultiIndex == 2) {
			for (int i = 0; i < progressionDurations.size(); i++) {
				progressionDurations.set(i, progressionDurations.get(i) * 2);
			}
		}

		sec.setSectionBeatDurations(progressionDurations);
		sec.setSectionDuration(progressionDurations.stream().mapToDouble(e -> e).sum());
		if (!sec.isCustomChordsEnabled()) {
			sec.setCustomChords(StringUtils.join(chords, ","));
			sec.setDisplayAlternateChords(true);
		}
		LG.i("Using SECTION custom progression: " + StringUtils.join(chords, ","));

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

	private int generateKeyChange(List<int[]> chords, int arrSeed) {
		Integer transToSet = null;
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
				throw new IllegalArgumentException("Unknown keychange!");
			}
		} else {
			transToSet = 0;
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
				LG.d("Trans up by: " + transToSet);
				break;
			}
		}
		if (transToSet == 0) {
			LG.i("Pivot chord not found between last and first chord!");
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

		double mult = 1;
		SectionConfig sc = (currentSection != null) ? currentSection.getSecConfig() : null;
		int beatDurMultiIndex = (sc != null && sc.getBeatDurationMultiplierIndex() != null)
				? sc.getBeatDurationMultiplierIndex()
				: gc.getBeatDurationMultiplierIndex();
		if (beatDurMultiIndex == 0) {
			mult = 0.5;
		} else if (beatDurMultiIndex == 2) {
			mult = 2;
		}
		double separatorValue = Durations.WHOLE_NOTE * mult;
		double chordSeparator = separatorValue;
		Vector<Note> noteList = userMelody.getNoteList();
		if (!chordMelodyMap1.containsKey(Integer.valueOf(0))) {
			chordMelodyMap1.put(Integer.valueOf(0), new ArrayList<>());
		}
		double rhythmCounter = 0;
		List<Double> progDurations = new ArrayList<>();
		progDurations.add(separatorValue);
		for (Note n : noteList) {
			LG.d("Rhythm counter: " + rhythmCounter);
			if (rhythmCounter >= chordSeparator - DBL_ERR) {
				LG.d("NEXT CHORD!");
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
		LG.d("Rhythm counter end: " + rhythmCounter);
		while (rhythmCounter >= chordSeparator + DBL_ERR) {
			LG.d("NEXT CHORD!");
			chordSeparator += separatorValue;
			chordCounter++;
			progDurations.add(separatorValue);
			if (!chordMelodyMap1.containsKey(Integer.valueOf(chordCounter))) {
				chordMelodyMap1.put(Integer.valueOf(chordCounter), new ArrayList<>());
			}
			chordMelodyMap1.get(Integer.valueOf(chordCounter))
					.add(noteList.get(noteList.size() - 1));
		}
		LG.i("Processed melody, chords: " + (chordCounter + 1));
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
				gc.getChordGenSettings().getFlattenVoicingChance(), new ArrayList<>(), null);


		chordProgression = melodyBasedChordProgression;
		rootProgression = melodyBasedRootProgression;
		LG.i(StringUtils.join(chordStrings, ","));

	}

	public Phrase fillMelodyFromPart(MelodyPart ip, List<int[]> actualProgression,
			List<int[]> generatedRootProgression, int notesSeedOffset, Section sec,
			List<Integer> variations, boolean melodyEmptyPass) {
		LG.d("Processing: " + ip.partInfo());
		Phrase phr = new PhraseExt(0, ip.getOrder(), secOrder);

		int measures = sec.getMeasures();

		Vector<Note> skeletonNotes = null;
		if (userMelody != null) {
			skeletonNotes = userMelody.copy().getNoteList();
		} else {
			if (gc.isMelodyLegacyMode()) {
				LG.i("OLD MELODY ALGO");
				skeletonNotes = algoGen2GenerateMelodySkeletonFromChords(ip, actualProgression,
						generatedRootProgression, measures, notesSeedOffset, sec, variations);
			} else {
				skeletonNotes = generateMelodyBlockSkeletonFromChords(ip, actualProgression,
						generatedRootProgression, measures, notesSeedOffset, sec, variations);
			}

		}
		Map<Integer, List<Note>> fullMelodyMap = convertMelodySkeletonToFullMelody(ip,
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
									minorChord),
							gc.isTransposedNotesForceScale());
					LG.d("Transposing melody to match minor chord! Chord#: " + i);
					break;
				}
			}
		}

		if (melodyEmptyPass || !overwriteWithCustomSectionMidi(sec, phr, ip)) {
			Vector<Note> noteList = new Vector<>();
			fullMelodyMap.values().forEach(e -> noteList.addAll(e));

			phr.addNoteList(noteList, true);
			Phrase phrSaved = phr.copy();
			Mod.transpose(phrSaved, ip.getTranspose() * -1);
			if (gc.isTransposedNotesForceScale()) {
				MidiUtils.transposePhrase(phrSaved, ScaleMode.IONIAN.noteAdjustScale,
						ScaleMode.IONIAN.noteAdjustScale);
			}
			if (!melodyEmptyPass) {
				addPhraseNotesToSection(sec, ip, phrSaved.getNoteList());
			}
		} else {
			Mod.transpose(phr, ip.getTranspose());
			if (gc.isCustomMidiForceScale()) {
				MidiUtils.transposePhrase(phr, ScaleMode.IONIAN.noteAdjustScale,
						ScaleMode.IONIAN.noteAdjustScale);
			}
			if (ip.getOrder() == 1) {
				int numChords = progressionDurations.size();
				fullMelodyMap = new HashMap<>();
				for (int i = 0; i < numChords * measures; i++) {
					fullMelodyMap.put(i, new ArrayList<>());
				}
				int chordCounter = 0;
				int measureCounter = 0;
				double cumulativeChordDur = progressionDurations.get(0);
				PhraseNotes pn = new PhraseNotes(phr);
				pn.remakeNoteStartTimes();
				List<PhraseNote> pns = new ArrayList<>(pn);

				if (pns.size() >= 2) {
					Collections.sort(pns, Comparator.comparing(e -> e.getStartTime()));
					double endTime = pn.get(pn.size() - 1).getAbsoluteStartTime()
							+ pn.get(pn.size() - 1).getRv();
					for (int i = 0; i < pns.size() - 1; i++) {
						PhraseNote n = pns.get(i);
						n.setRv(pns.get(i + 1).getStartTime() - n.getStartTime());
						n.setOffset(0);
					}
					pns.get(pns.size() - 1).setRv(endTime - pns.get(pns.size() - 2).getStartTime());
				}

				for (int i = 0; i < pns.size(); i++) {
					PhraseNote n = pns.get(i);
					if (n.getStartTime() > (cumulativeChordDur - DBL_ERR)) {
						chordCounter = (chordCounter + 1) % numChords;
						if (chordCounter == 0) {
							measureCounter++;
						}
						cumulativeChordDur += progressionDurations.get(chordCounter % numChords);
					}
					fullMelodyMap.get(chordCounter + numChords * measureCounter).add(n.toNote());
				}
			}


		}

		if (ip.getOrder() == 1 && !melodyEmptyPass) {
			List<Integer> notePattern = new ArrayList<>();
			Map<Integer, List<Integer>> notePatternMap = patternsFromNotes(fullMelodyMap);
			notePatternMap.keySet().forEach(e -> notePattern.addAll(notePatternMap.get(e)));
			melodyNotePatternMap = notePatternMap;
			melodyNotePattern = notePattern;
			//LG.d(StringUtils.join(melodyNotePattern, ","));
		}

		swingPhrase(phr, ip.getSwingPercent(), Durations.QUARTER_NOTE);

		MidiGeneratorUtils.applyNoteLengthMultiplier(phr.getNoteList(),
				ip.getNoteLengthMultiplier());
		MidiGeneratorUtils.processSectionTransition(sec, phr.getNoteList(),
				progressionDurations.stream().mapToDouble(e -> e).sum() * measures, 0.25, 0.25,
				0.9);

		List<Integer> melodyVars = sec.getVariation(0, ip.getAbsoluteOrder());
		// extraTranspose variation
		int extraTranspose = 0;
		if (melodyVars != null && melodyVars.contains(Integer.valueOf(0))) {
			extraTranspose = 12;
		}

		ScaleMode scale = (modScale != null) ? modScale : gc.getScaleMode();
		if (scale != ScaleMode.IONIAN) {
			MidiUtils.transposePhrase(phr, ScaleMode.IONIAN.noteAdjustScale, scale.noteAdjustScale,
					gc.isTransposedNotesForceScale());
		}
		if ((modTrans + extraTranspose) != 0) {
			Mod.transpose(phr, modTrans + extraTranspose);
		}
		phr.setStartTime(START_TIME_DELAY);
		addOffsetsToPhrase(phr, ip);
		return phr;
	}

	public Phrase fillBassFromPart(BassPart ip, List<int[]> generatedRootProgression, Section sec,
			List<Integer> variations) {
		LG.d("Processing: " + ip.partInfo());
		boolean genVars = variations == null;

		int measures = sec.getMeasures();

		double[] durationPool = new double[] { Durations.SIXTEENTH_NOTE, Durations.EIGHTH_NOTE,
				Durations.QUARTER_NOTE, Durations.DOTTED_QUARTER_NOTE, Durations.HALF_NOTE,
				Durations.EIGHTH_NOTE + Durations.HALF_NOTE, Durations.DOTTED_HALF_NOTE,
				Durations.WHOLE_NOTE };

		int[] durationWeights = new int[] { 5, 25, 45, 55, 75, 85, 95, 100 };

		int seed = ip.getPatternSeedWithPartOffset();

		Phrase phr = new PhraseExt(1, ip.getOrder(), secOrder);
		int volMultiplier = (gc.isScaleMidiVelocityInArrangement()) ? sec.getVol(1) : 100;
		int minVel = MidiGeneratorUtils.multiplyVelocity(ip.getVelocityMin(), volMultiplier, 0, 1);
		int maxVel = MidiGeneratorUtils.multiplyVelocity(ip.getVelocityMax(), volMultiplier, 1, 0);
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


		List<Integer> bassVelocityPattern = new ArrayList<>();
		if (ip.getCustomVelocities() != null
				&& ip.getCustomVelocities().size() >= ip.getHitsPerPattern()) {
			int multiplier = gc.isScaleMidiVelocityInArrangement() ? sec.getVol(3) : 100;
			for (int k = 0; k < ip.getHitsPerPattern(); k++) {
				bassVelocityPattern.add(MidiGeneratorUtils
						.multiplyVelocity(ip.getCustomVelocities().get(k), multiplier, 0, 1));
			}
			bassVelocityPattern = MidiUtils.intersperse(null, ip.getChordSpan() - 1,
					bassVelocityPattern);
		}


		Random bassDynamics = new Random(ip.getPatternSeedWithPartOffset());
		boolean rhythmPauses = false;
		List<Integer> fillPattern = ip.getChordSpanFill()
				.getPatternByLength(progressionDurations.size(), ip.isFillFlip());
		//LG.d("Bass fill pattern:" + StringUtils.join(fillPattern, ", "));
		for (int i = 0; i < measures; i++) {
			int extraSeed = 0;
			int chordSpanPart = 0;
			int skipNotes = 0;

			bassDynamics.setSeed(ip.getPatternSeedWithPartOffset());
			for (int chordIndex = 0; chordIndex < squishedChords.size(); chordIndex++) {
				if (genVars && (chordIndex == 0) && sec.getTypeMelodyOffset() > 0) {
					variations = fillVariations(sec, ip, variations, 1);
				}
				double halfDurMulti = (chordIndex >= (squishedChords.size() + 1) / 2
						&& sec.getTransitionType() == 4) ? 2.0 : 1.0;
				if ((variations != null) && (chordIndex == 0)) {
					for (Integer var : variations) {
						if (i == measures - 1) {
							LG.d("Bass #1 variation: " + var);
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


				if (fillPattern.get(chordIndex) < 1) {
					skipNotes = 0;
					chordSpanPart = (chordSpanPart + 1) % ip.getChordSpan();
					phr.addNote(new Note(Integer.MIN_VALUE, progressionDurations.get(chordIndex)));
					continue;
				}
				int velSpace = maxVel - minVel;

				if (ip.isAlternatingRhythm()) {
					int counter = 0;
					int seedCopy = seed + extraSeed + (chordIndex % 2);
					Rhythm bassRhythm = new Rhythm(seedCopy, progressionDurations.get(chordIndex),
							durationPool, durationWeights);
					List<Double> durations = bassRhythm.regenerateDurations(4,
							MidiGenerator.Durations.SIXTEENTH_NOTE / 2.0);

					for (Double dur : durations) {

						int randomNote = 0;
						// note variation for short notes, low chance, only after first
						int noteVaryChance = sec.isTransition()
								? MidiGeneratorUtils.adjustChanceParamForTransition(
										ip.getNoteVariation(), sec, chordIndex,
										squishedChords.size(), 40, 0.25, false)
								: ip.getNoteVariation();
						if (counter > 0 && dur < (Durations.QUARTER_NOTE + DBL_ERR)
								&& noteVariationGenerator.nextInt(100) < noteVaryChance
								&& squishedChords.get(chordIndex).length > 1) {
							randomNote = noteVariationGenerator
									.nextInt(squishedChords.get(chordIndex).length - 1) + 1;
						}

						int pitch = (rhythmPauses && dur < Durations.QUARTER_NOTE
								&& rhythmPauseGenerator.nextInt(100) < 33) ? Integer.MIN_VALUE
										: squishedChords.get(chordIndex)[randomNote];

						int velocity = bassDynamics.nextInt(velSpace) + minVel;
						Note n = new Note(pitch, dur, velocity);
						n.setDuration(dur * GLOBAL_DURATION_MULTIPLIER);
						phr.addNote(n);
						counter++;
					}
				} else {
					List<Integer> pattern = null;
					List<Integer> nextPattern = null;
					List<Integer> velocityPattern = null;
					PatternJoinMode joinMode = ip.getPatternJoinMode();
					int stretchedByNote = (joinMode == PatternJoinMode.JOIN) ? 1 : 0;
					if (ip.getPattern() == RhythmPattern.MELODY1 && melodyNotePatternMap != null) {
						pattern = new ArrayList<>(melodyNotePatternMap.get(chordIndex));
					} else {
						List<Integer> patternCopy = ip.getFinalPatternCopy();
						List<Integer> patternSub = patternCopy.subList(0, ip.getHitsPerPattern());

						pattern = MidiUtils.intersperse(-1, ip.getChordSpan() - 1, patternSub);
						pattern = partOfListClean(chordSpanPart, ip.getChordSpan(), pattern);
						if (ip.getChordSpan() > 1 && joinMode != PatternJoinMode.NOJOIN) {
							if (chordSpanPart < ip.getChordSpan() - 1) {
								nextPattern = MidiUtils.intersperse(-1, ip.getChordSpan() - 1,
										patternSub);
								nextPattern = partOfListClean(chordSpanPart + 1, ip.getChordSpan(),
										nextPattern);
							}
						}
						velocityPattern = !bassVelocityPattern.isEmpty()
								? partOfList(chordSpanPart, ip.getChordSpan(), bassVelocityPattern)
								: null;
					}

					if (ip.isPatternFlip()) {
						for (int p = 0; p < pattern.size(); p++) {
							if (pattern.get(p) >= 0) {
								pattern.set(p, pattern.get(p) > 0 ? 0 : 1);
							}
						}
					}

					double duration = (ip.getPattern() == RhythmPattern.MELODY1
							&& melodyNotePatternMap != null) ? Durations.SIXTEENTH_NOTE
									: Durations.WHOLE_NOTE / pattern.size();
					duration *= halfDurMulti;

					double durationNow = 0;
					int nextP = -1;

					int p = 0;
					while (durationNow + DBL_ERR < progressionDurations.get(chordIndex)) {
						int velocity = velocityPattern != null
								? velocityPattern.get(p % velocityPattern.size())
								: (bassDynamics.nextInt(velSpace) + minVel);
						int pitch = 0;
						double finalDuration = 0.0;
						if (pattern.get(p) < 1 || (p <= nextP && stretchedByNote == 1)
								|| skipNotes > 0) {
							if (skipNotes > 0) {
								skipNotes--;
							}
							pitch = Integer.MIN_VALUE;
						}

						if (durationNow + duration > progressionDurations.get(chordIndex)
								- DBL_ERR) {
							double fillerDuration = progressionDurations.get(chordIndex)
									- durationNow;
							finalDuration = fillerDuration;
							duration = fillerDuration;
							if (fillerDuration < FILLER_NOTE_MIN_DURATION) {
								pitch = Integer.MIN_VALUE;
							}
						} else {
							finalDuration = duration;
						}

						int durMultiplier = 1;
						boolean joinApplicable = joinMode != PatternJoinMode.NOJOIN
								&& (pattern.get(p) > 0) && (p >= nextP);
						if (joinApplicable) {
							nextP = p + 1;
							while (nextP < pattern.size()) {
								if (Integer.signum(pattern.get(nextP)) == stretchedByNote
										|| pattern.get(nextP) == -1) {
									durMultiplier++;
									nextP++;
								} else {
									break;
								}
							}
						}

						if (nextP >= pattern.size() && ip.getChordSpan() > 1
								&& nextPattern != null) {
							skipNotes = countStartingValueInList(stretchedByNote, nextPattern);
							durMultiplier += skipNotes;

						}
						//LG.d("Dur multiplier added: " + durMultiplier);
						finalDuration = duration * durMultiplier;

						if (pitch == 0) {
							int randomNote = 0;
							// note variation for short notes, low chance, only after first
							int noteVaryChance = sec.isTransition()
									? MidiGeneratorUtils.adjustChanceParamForTransition(
											ip.getNoteVariation(), sec, chordIndex,
											squishedChords.size(), 40, 0.25, false)
									: ip.getNoteVariation();
							if (p > 0 && finalDuration < (Durations.QUARTER_NOTE + DBL_ERR)
									&& noteVariationGenerator.nextInt(100) < noteVaryChance
									&& squishedChords.get(chordIndex).length > 1) {
								randomNote = noteVariationGenerator
										.nextInt(squishedChords.get(chordIndex).length - 1) + 1;
							}
							pitch = (rhythmPauses && finalDuration < Durations.QUARTER_NOTE
									&& rhythmPauseGenerator.nextInt(100) < 33) ? Integer.MIN_VALUE
											: squishedChords.get(chordIndex)[randomNote];
						}
						Note n = new Note(pitch, duration, velocity);
						n.setDuration(finalDuration * GLOBAL_DURATION_MULTIPLIER);
						phr.addNote(n);

						durationNow += duration;
						p = (p + 1) % pattern.size();
					}
					chordSpanPart = (chordSpanPart + 1) % ip.getChordSpan();
				}
			}
		}

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
		if (genVars && variations != null) {
			sec.setVariation(1, 0, variations);
		}
		return phr;

	}

	public Phrase fillChordsFromPart(ChordPart ip, List<int[]> actualProgression, Section sec,
			List<Integer> variations) {
		LG.d("Processing: " + ip.partInfo());
		boolean genVars = variations == null;

		int measures = sec.getMeasures();

		int orderSeed = ip.getPatternSeedWithPartOffset() + ip.getOrder();
		Phrase phr = new PhraseExt(2, ip.getOrder(), secOrder);
		List<Chord> chords = new ArrayList<>();
		Random variationGenerator = new Random(
				gc.getArrangement().getSeed() + ip.getOrder() + sec.getTypeSeedOffset());
		Random flamGenerator = new Random(orderSeed + 30);
		Random pauseGenerator = new Random(orderSeed + 50);
		// chord strum
		double flamming = 0.0;
		if (gc.getChordGenSettings().isUseStrum()) {

			if (ip.getStrum() == 666) {
				flamming = noteMultiplier * 0.6666666666666;
			} else if (ip.getStrum() == 333) {
				flamming = noteMultiplier * 0.3333333333333;
			} else if (ip.getStrum() == 31) {
				flamming = noteMultiplier * 0.03125;
			} else if (ip.getStrum() == 62) {
				flamming = noteMultiplier * 0.0625;
			} else {
				flamming = (noteMultiplier * (double) ip.getStrum()) / 1000.0;
			}
			//LG.d("Chord strum CUSTOM! " + cp.getStrum() + ", flamming: " + flamming);
		}


		int stretch = ip.getChordNotesStretch();
		List<Integer> fillPattern = ip.getChordSpanFill()
				.getPatternByLength(actualProgression.size(), ip.isFillFlip());

		int volMultiplier = (gc.isScaleMidiVelocityInArrangement()) ? sec.getVol(2) : 100;
		int minVel = MidiGeneratorUtils.multiplyVelocity(ip.getVelocityMin(), volMultiplier, 0, 1);
		int maxVel = MidiGeneratorUtils.multiplyVelocity(ip.getVelocityMax(), volMultiplier, 1, 0);

		List<Integer> chordVelocityPattern = new ArrayList<>();
		if (ip.getCustomVelocities() != null
				&& ip.getCustomVelocities().size() >= ip.getHitsPerPattern()) {
			int multiplier = gc.isScaleMidiVelocityInArrangement() ? sec.getVol(3) : 100;
			for (int k = 0; k < ip.getHitsPerPattern(); k++) {
				chordVelocityPattern.add(MidiGeneratorUtils
						.multiplyVelocity(ip.getCustomVelocities().get(k), multiplier, 0, 1));
			}
			chordVelocityPattern = MidiUtils.intersperse(null, ip.getChordSpan() - 1,
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
					variations = fillVariations(sec, ip, variations, 2);
				}

				double halfDurMulti = (chordIndex >= (actualProgression.size() + 1) / 2
						&& sec.getTransitionType() == 4) ? 2.0 : 1.0;

				if ((variations != null) && (chordIndex == 0)) {
					for (Integer var : variations) {
						if (i == measures - 1) {
							//LG.d("Chord #" + cp.getOrder() + " variation: " + var);
						}

						switch (var) {
						case 0:
							//extraTranspose = 12;
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
							switch (ip.getStrumType()) {
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

				flamGenerator.setSeed(orderSeed + 30 + (chordIndex % 4));
				Chord c = Chord.EMPTY(progressionDurations.get(chordIndex));
				if (!ignoreChordSpanFill) {
					if (fillPattern.get(chordIndex) < 1) {
						chords.add(c);
						skipNotes = 0;
						chordSpanPart = (chordSpanPart + 1) % ip.getChordSpan();
						continue;
					}
				}
				Random velocityGenerator = new Random(orderSeed + chordIndex);

				boolean transition = transitionGenerator.nextInt(100) < ip.getTransitionChance();
				int transChord = (transitionGenerator.nextInt(100) < ip.getTransitionChance())
						? (chordIndex + 1) % actualProgression.size()
						: chordIndex;

				c.setStrumPauseChance(ip.getStrumPauseChance());
				c.setStrumType(ip.getStrumType());
				c.setDurationRatio((ip.getNoteLengthMultiplier() / 100.0) / halfDurMulti);

				int[] mainChordNotes = actualProgression.get(chordIndex);
				int[] transChordNotes = actualProgression.get(transChord);

				//only skip if not already an interval (2 notes)
				boolean copiedMain = false;
				boolean copiedTrans = false;
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
						copiedMain = true;
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
						copiedTrans = true;
					}
				}
				boolean stretchOverride = (sec.isTransition()
						&& chordIndex >= actualProgression.size() - 2);

				if (stretchOverride || ip.isStretchEnabled()) {
					int stretchAmount = (stretchOverride)
							? (sec.getTransitionType() == 1 || sec.getTransitionType() == 4 ? 7 : 2)
							: stretch;
					mainChordNotes = convertChordToLength(mainChordNotes, stretchAmount);
					transChordNotes = convertChordToLength(transChordNotes, stretchAmount);
					copiedMain = true;
					copiedTrans = true;
				}
				if (!copiedMain) {
					mainChordNotes = Arrays.copyOf(mainChordNotes, mainChordNotes.length);
				}
				if (!copiedTrans) {
					transChordNotes = Arrays.copyOf(transChordNotes, transChordNotes.length);
				}

				c.setTranspose(extraTranspose);
				c.setNotes(mainChordNotes);

				// for transition:
				double splitTime = progressionDurations.get(chordIndex)
						* (gc.getChordGenSettings().isUseSplit() ? ip.getTransitionSplit()
								: DEFAULT_CHORD_SPLIT)
						/ 1000.0;
				//LG.d("Split time: " + splitTime);
				PatternJoinMode joinMode = ip.getPatternJoinMode();
				int stretchedByNote = (joinMode == PatternJoinMode.JOIN) ? 1 : 0;

				List<Integer> pattern = null;
				List<Integer> nextPattern = null;
				List<Integer> velocityPattern = null;
				if (ip.getPattern() == RhythmPattern.MELODY1 && melodyNotePatternMap != null) {
					pattern = new ArrayList<>(melodyNotePatternMap.get(chordIndex));
				} else {
					List<Integer> patternCopy = ip.getFinalPatternCopy();
					List<Integer> patternSub = patternCopy.subList(0, ip.getHitsPerPattern());
					pattern = MidiUtils.intersperse(-1, ip.getChordSpan() - 1, patternSub);
					pattern = partOfListClean(chordSpanPart, ip.getChordSpan(), pattern);
					if (ip.getChordSpan() > 1 && joinMode != PatternJoinMode.NOJOIN) {
						if (chordSpanPart < ip.getChordSpan() - 1) {
							nextPattern = MidiUtils.intersperse(-1, ip.getChordSpan() - 1,
									patternSub);
							nextPattern = partOfListClean(chordSpanPart + 1, ip.getChordSpan(),
									nextPattern);
						}
					}
					velocityPattern = !chordVelocityPattern.isEmpty()
							? partOfList(chordSpanPart, ip.getChordSpan(), chordVelocityPattern)
							: null;
				}
				if (ip.isPatternFlip()) {
					for (int p = 0; p < pattern.size(); p++) {
						if (pattern.get(p) >= 0) {
							pattern.set(p, pattern.get(p) > 0 ? 0 : 1);
						}
					}
				}
				double duration = (ip.getPattern() == RhythmPattern.MELODY1
						&& melodyNotePatternMap != null) ? Durations.SIXTEENTH_NOTE
								: Durations.WHOLE_NOTE / pattern.size();
				duration *= halfDurMulti;
				double durationNow = 0;
				int nextP = -1;

				int p = 0;
				int patternExtension = 0;
				while (durationNow + DBL_ERR < progressionDurations.get(chordIndex)) {

					//LG.d("Duration counter: " + durationCounter);
					Chord cC = Chord.copy(c);

					cC.setVelocity(velocityPattern != null
							? velocityPattern.get(p % velocityPattern.size())
							: (velocityGenerator.nextInt(maxVel - minVel) + minVel));
					// less plucky
					//cC.setDurationRatio(cC.getDurationRatio() + (1 - cC.getDurationRatio()) / 2);
					if (pattern.get(p) < 1
							|| ((p + patternExtension <= nextP) && stretchedByNote == 1)
							|| skipNotes > 0) {
						if (skipNotes > 0) {
							skipNotes--;
						}
						cC.setNotes(new int[] { Integer.MIN_VALUE });
					} else if (transition && durationNow >= splitTime) {
						cC.setNotes(transChordNotes);
					}

					if (pauseGenerator.nextInt(100) < ip.getPauseChance()) {
						cC.setNotes(new int[] { Integer.MIN_VALUE });
					}

					if (durationNow + duration > progressionDurations.get(chordIndex) - DBL_ERR) {
						double fillerDuration = progressionDurations.get(chordIndex) - durationNow;
						cC.setRhythmValue(fillerDuration);
						if (fillerDuration < FILLER_NOTE_MIN_DURATION) {
							cC.setNotes(new int[] { Integer.MIN_VALUE });
						}
					} else {
						cC.setRhythmValue(duration);
					}

					int durMultiplier = 1;
					boolean joinApplicable = (pattern.get(p) > 0)
							&& (p + patternExtension >= nextP);
					if (joinApplicable) {
						nextP = p + 1;
						while (nextP < pattern.size()) {
							if (Integer.signum(pattern.get(nextP)) == stretchedByNote
									|| pattern.get(nextP) == -1) {
								durMultiplier++;
								nextP++;
							} else {
								break;
							}
						}
						nextP += patternExtension;
					}
					joinApplicable &= (joinMode != PatternJoinMode.NOJOIN);
					//LG.d("Dur multiplier be4: " + durMultiplier);
					// chord to spill by 15%
					double durationCapMax = (ip.getStrum() > 750) ? 1.15 : 5.00;
					double durationCap = durationCapMax
							* (progressionDurations.get(chordIndex) - durationNow);
					double durationRatioCap = durationCap / cC.getRhythmValue();

					if (nextP - patternExtension >= pattern.size() && ip.getChordSpan() > 1
							&& nextPattern != null) {
						skipNotes = countStartingValueInList(stretchedByNote, nextPattern);
						durMultiplier += skipNotes;
						//LG.d("CHORD Dur multiplier added: " + durMultiplier);
					}

					cC.setDurationRatio(Math.min(durationRatioCap, Math.min(durMultiplier,
							cC.getDurationRatio() * (joinApplicable ? durMultiplier : 1.0))));
					//LG.d("Dur multiplier after: " + cC.getDurationRatio());
					cC.setFlam(flamming);
					cC.makeAndStoreNotesBackwards(flamGenerator);
					chords.add(cC);
					durationNow += duration;
					p = (p + 1) % pattern.size();
					if (p == 0) {
						patternExtension += pattern.size();
					}
				}
				chordSpanPart = (chordSpanPart + 1) % ip.getChordSpan();
			}
		}
		Mod.transpose(phr, DEFAULT_INSTRUMENT_TRANSPOSE[2]);

		if (!overwriteWithCustomSectionMidi(sec, phr, ip)) {
			MidiUtils.addChordsToPhrase(phr, chords, flamming);
			addPhraseNotesToSection(sec, ip, phr.getNoteList());
		}

		if (genVars && variations != null) {
			sec.setVariation(2, ip.getAbsoluteOrder(), variations);
		}

		// transpose
		int extraTranspose = gc.getChordGenSettings().isUseTranspose() ? ip.getTranspose() : 0;

		// extraTranspose variation
		List<Integer> vars = sec.getVariation(2, ip.getAbsoluteOrder());
		if (vars != null && vars.contains(Integer.valueOf(0))) {
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

	private static int countStartingValueInList(int stretchedByNote, List<Integer> nextPattern) {
		int counter = 0;
		while (counter < nextPattern.size()) {
			if (nextPattern.get(counter) == stretchedByNote || nextPattern.get(counter) == -1) {
				counter++;
			} else {
				break;
			}
		}
		return counter;
	}

	public Phrase fillArpFromPart(ArpPart ip, List<int[]> actualProgression, Section sec,
			List<Integer> variations) {
		LG.d("Processing: " + ip.partInfo());
		boolean genVars = variations == null;

		int measures = sec.getMeasures();

		Phrase phr = new PhraseExt(3, ip.getOrder(), secOrder);

		ArpPart apClone = (ArpPart) ip.clone();
		int seed = ip.getPatternSeedWithPartOffset() + ip.getOrder();
		Map<String, List<Integer>> arpMap = generateArpMap(seed, ip.equals(gc.getArpParts().get(0)),
				ip);

		List<Integer> arpPattern = arpMap.get(ARP_PATTERN_KEY);
		List<Integer> arpOctavePattern = arpMap.get(ARP_OCTAVE_KEY);
		List<Integer> arpPausesPattern = arpMap.get(ARP_PAUSES_KEY);

		List<Integer> arpVelocityPattern = new ArrayList<>();
		if (ip.getCustomVelocities() != null
				&& ip.getCustomVelocities().size() >= ip.getHitsPerPattern()) {
			int multiplier = gc.isScaleMidiVelocityInArrangement() ? sec.getVol(3) : 100;
			for (int k = 0; k < ip.getHitsPerPattern(); k++) {
				arpVelocityPattern.add(MidiGeneratorUtils
						.multiplyVelocity(ip.getCustomVelocities().get(k), multiplier, 0, 1));
			}
			arpVelocityPattern = MidiUtils.intersperse(null, ip.getChordSpan() - 1,
					arpVelocityPattern);
		}

		List<Boolean> directions = null;


		// TODO: divide
		int repeatedArpsPerChord = ip.getHitsPerPattern() * ip.getPatternRepeat();

		/*if (melodic) {
			repeatedArpsPerChord /= ap.getChordSpan();
		}*/

		int volMultiplier = (gc.isScaleMidiVelocityInArrangement()) ? sec.getVol(3) : 100;
		int minVel = MidiGeneratorUtils.multiplyVelocity(ip.getVelocityMin(), volMultiplier, 0, 1);
		int maxVel = MidiGeneratorUtils.multiplyVelocity(ip.getVelocityMax(), volMultiplier, 1, 0);

		boolean fillLastBeat = false;
		List<Integer> fillPattern = ip.getChordSpanFill()
				.getPatternByLength(actualProgression.size(), ip.isFillFlip());
		for (int i = 0; i < measures; i++) {
			int chordSpanPart = 0;
			int spannedPulseCounter = 0;
			int extraTranspose = 0;
			boolean ignoreChordSpanFill = false;
			boolean forceRandomOct = false;

			Random velocityGenerator = new Random(seed);
			Random exceptionGenerator = new Random(seed + 1);
			for (int chordIndex = 0; chordIndex < actualProgression.size(); chordIndex++) {
				if (genVars && (chordIndex == 0)) {
					List<Double> chanceMultipliers = sec.isTransition()
							? Arrays.asList(new Double[] { 1.0, 1.0, 1.0, 2.0, 1.0 })
							: null;
					variations = fillVariations(sec, ip, variations, 3, chanceMultipliers);
				}

				double halfDurMulti = (chordIndex >= (actualProgression.size() + 1) / 2
						&& sec.getTransitionType() == 4) ? 2.0 : 1.0;

				if ((variations != null) && (chordIndex == 0)) {
					for (Integer var : variations) {
						if (i == measures - 1) {
							//LG.d("Arp #" + ap.getOrder() + " variation: " + var);
						}

						switch (var) {
						case 0:
							//extraTranspose = 12;
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
								directions = MidiGeneratorUtils
										.generateMelodyDirectionsFromChordProgression(
												actualProgression, true);
							}
							break;
						default:
							throw new IllegalArgumentException("Too much variation!");
						}
					}
				}

				double chordDurationArp = (ip.getPattern() == RhythmPattern.MELODY1
						&& melodyNotePatternMap != null) ? Durations.SIXTEENTH_NOTE
								: Durations.WHOLE_NOTE / ((double) repeatedArpsPerChord);
				int[] chord = convertChordToLength(actualProgression.get(chordIndex),
						ip.getChordNotesStretch(), ip.isStretchEnabled());
				/*List<Integer> chordNotes = Arrays.stream(chord).boxed().map(e -> e % 12)
						.collect(Collectors.toList());*/
				if (directions != null) {
					ArpPattern pat = (directions.get(chordIndex)) ? ArpPattern.UP : ArpPattern.DOWN;
					arpPattern = pat.getPatternByLength(ip.getHitsPerPattern(), chord.length,
							ip.getPatternRepeat(), ip.getArpPatternRotate());
					arpPattern = MidiUtils.intersperse(0, ip.getChordSpan() - 1, arpPattern);
				} else {
					if (ip.getArpPattern() != ArpPattern.RANDOM) {
						if (ip.getArpPattern() == ArpPattern.CUSTOM) {
							arpPattern = ip.getArpPattern().getPatternByLength(
									ip.getHitsPerPattern(), chord.length, ip.getPatternRepeat(),
									ip.getArpPatternRotate(), ip.getArpPatternCustom());
						} else {
							arpPattern = ip.getArpPattern().getPatternByLength(
									ip.getHitsPerPattern(), chord.length, ip.getPatternRepeat(),
									ip.getArpPatternRotate());
						}

						arpPattern = MidiUtils.intersperse(0, ip.getChordSpan() - 1, arpPattern);
					} else {
						ip.setArpPatternCustom(arpPattern);
					}
				}
				chordDurationArp *= halfDurMulti;

				// reset every 2
				if (chordIndex % 2 == 0) {
					//exceptionGenerator.setSeed(seed + 1);
				}
				List<Integer> pitchPatternSpanned = partOfList(chordSpanPart, ip.getChordSpan(),
						arpPattern);
				List<Integer> octavePatternSpanned = partOfList(chordSpanPart, ip.getChordSpan(),
						arpOctavePattern);
				List<Integer> melodyPattern = (melodyNotePatternMap != null)
						? melodyNotePatternMap.get(chordIndex)
						: null;
				List<Integer> pausePatternSpanned = (ip.getPattern() == RhythmPattern.MELODY1
						&& melodyPattern != null) ? new ArrayList<>(melodyPattern)
								: partOfList(chordSpanPart, ip.getChordSpan(), arpPausesPattern);
				List<Integer> velocityPatternSpanned = !arpVelocityPattern.isEmpty()
						? partOfList(chordSpanPart, ip.getChordSpan(), arpVelocityPattern)
						: null;
				List<Integer> contour = ip.getArpContour();
				List<Integer> arpContourSpanned = (contour != null && !contour.isEmpty()) ? contour
						: null;
				Integer contourInterval = (arpContourSpanned != null)
						? Math.max(repeatedArpsPerChord / arpContourSpanned.size(), 1)
						: null;

				double melodySubdivisions = -1;
				if (melodyPattern != null && melodyPattern.size() > 0) {
					melodySubdivisions = progressionDurations.get(chordIndex)
							/ melodyPattern.size();
				}
				int lastMelodyIndex = 0;

				int pulse = 0;
				double durationNow = 0;
				while (durationNow + DBL_ERR < progressionDurations.get(chordIndex)) {
					int velocity = velocityPatternSpanned != null
							? velocityPatternSpanned.get(pulse % velocityPatternSpanned.size())
							: (velocityGenerator.nextInt(maxVel - minVel) + minVel);

					Integer patternNum = pitchPatternSpanned.get(pulse);

					int pitch = MidiUtils.getXthChordNote(patternNum, chord);
					if ((contourInterval != null) && (spannedPulseCounter % contourInterval == 0)) {
						int newPitch = 24 + MidiUtils.getXthChordNote(arpContourSpanned.get(
								(spannedPulseCounter / contourInterval) % arpContourSpanned.size()),
								MidiUtils.cChromatic);
						if (ip.isArpContourChordMode()) {
							newPitch += rootProgression.get(chordIndex)[0] % 12;
							newPitch = MidiUtils.octavePitch(newPitch) + MidiUtils
									.getClosestPitchFromList(MidiUtils.MAJ_SCALE, newPitch);
						}
						pitch = newPitch;
						/*LG.i("Replaced with ARP contour pitch: " + pitch + ", at pulse: " + pulse
								+ ", spanned: " + spannedPulseCounter + ", #: "
								+ spannedPulseCounter / contourInterval);*/
					}

					if (gc.isUseOctaveAdjustments() || forceRandomOct) {
						int octaveAdjustGenerated = octavePatternSpanned.get(pulse);
						int octaveAdjustmentFromPattern = (patternNum < 2) ? -12
								: ((patternNum < 6) ? 0 : 12);
						pitch += octaveAdjustmentFromPattern + octaveAdjustGenerated;
					}

					if (gc.isRandomArpCorrectMelodyNotes()) {
						Integer melodyPitch = null;
						if (melodySubdivisions > 0) {
							for (int mInd = lastMelodyIndex; mInd < melodyPattern.size(); mInd++) {
								melodyPitch = melodyPattern.get(mInd);
								if (melodyPitch != null) {
									break;
								}
								if (mInd * melodySubdivisions > durationNow + chordDurationArp) {
									break;
								}
							}
							lastMelodyIndex = (int) Math
									.round((durationNow + chordDurationArp) / melodySubdivisions);
						}

						if (melodyPitch != null) {

							LG.i("Last MelodyIndex: " + lastMelodyIndex + ", pitch: "
									+ melodyPitch);
							if (MidiUtils.getSemitonalDistance(melodyPitch, pitch) == 1) {
								LG.i("Old pitch: " + pitch);
								pitch = MidiUtils.octavePitch(pitch) + (melodyPitch % 12)
										+ ((pitch % 12 >= 6) && (melodyPitch % 12) < 6 ? 12 : 0);
								LG.i("new pitch: " + pitch);
							}
						}
					}

					boolean isPause = pausePatternSpanned
							.get(pulse % pausePatternSpanned.size()) == 0;
					if (ip.isPatternFlip()) {
						isPause = !isPause;
					}

					pitch += extraTranspose;
					if (!fillLastBeat || chordIndex < actualProgression.size() - 1) {
						if (isPause) {
							pitch = Integer.MIN_VALUE;
						} else if (!ignoreChordSpanFill) {
							if (fillPattern.get(chordIndex) < 1) {
								pitch = Integer.MIN_VALUE;
							}
						}
					}
					double usedDuration = chordDurationArp;
					if (durationNow + usedDuration - DBL_ERR > progressionDurations
							.get(chordIndex)) {
						usedDuration = progressionDurations.get(chordIndex) - durationNow;
						if (usedDuration < FILLER_NOTE_MIN_DURATION) {
							pitch = Integer.MIN_VALUE;
						}
					}
					double durMultiplier = GLOBAL_DURATION_MULTIPLIER * ip.getChordSpan();
					if (exceptionGenerator.nextInt(100) < ip.getExceptionChance() && pitch >= 0) {
						double splitDuration = usedDuration / 2;
						int patternNum2 = pitchPatternSpanned
								.get((pulse + 1) % repeatedArpsPerChord);
						int pitch2 = MidiUtils.getXthChordNote(patternNum2, chord) + extraTranspose;
						if (pitch2 >= 0) {
							pitch2 = MidiUtils.transposeNote((pitch + pitch2) / 2,
									ScaleMode.IONIAN.noteAdjustScale,
									ScaleMode.IONIAN.noteAdjustScale);
						} else {
							pitch2 = pitch;
						}
						//LG.d("Splitting arp!"); 
						Note n1 = new Note(pitch, splitDuration, velocity);
						n1.setDuration(splitDuration * durMultiplier);
						phr.addNote(n1);
						Note n2 = new Note(pitch2, splitDuration, Math.max(0, velocity - 15));
						n2.setDuration(splitDuration * durMultiplier);
						phr.addNote(n2);
					} else {
						Note n1 = new Note(pitch, usedDuration, velocity);
						n1.setDuration(usedDuration * durMultiplier);
						phr.addNote(n1);
					}
					durationNow += usedDuration;
					pulse = (pulse + 1) % repeatedArpsPerChord;
					spannedPulseCounter++;
				}

				chordSpanPart++;
				if (chordSpanPart >= ip.getChordSpan()) {
					chordSpanPart = 0;
					spannedPulseCounter = 0;
				}
			}
		}

		Mod.transpose(phr, DEFAULT_INSTRUMENT_TRANSPOSE[3]);

		if (!overwriteWithCustomSectionMidi(sec, phr, ip)) {
			addPhraseNotesToSection(sec, ip, phr.getNoteList());
		}

		if (genVars && variations != null) {
			sec.setVariation(3, ip.getAbsoluteOrder(), variations);
		}

		int extraTranspose = ip.getTranspose();

		// extraTranspose variation
		List<Integer> vars = sec.getVariation(3, ip.getAbsoluteOrder());
		if (vars != null && vars.contains(Integer.valueOf(0))) {
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
		if (fillLastBeat) {
			Mod.crescendo(phr, phr.getEndTime() * 3 / 4, phr.getEndTime(), Math.max(minVel, 55),
					Math.max(maxVel, 110));
		}
		ip.setPatternShift(apClone.getPatternShift());
		//dp.setVelocityPattern(false);
		ip.setChordSpan(apClone.getChordSpan());
		ip.setHitsPerPattern(apClone.getHitsPerPattern());
		ip.setPatternRepeat(apClone.getPatternRepeat());
		phr.setStartTime(START_TIME_DELAY);
		addOffsetsToPhrase(phr, ip);
		//MidiGeneratorUtils.multiDelayPhrase(phr, 2, Durations.DOTTED_EIGHTH_NOTE);
		return phr;
	}


	public Phrase fillDrumsFromPart(DrumPart ip, List<int[]> actualProgression,
			boolean sectionForcedDynamics, Section sec, List<Integer> variations) {
		LG.d("Processing: " + ip.partInfo());
		boolean genVars = variations == null;

		int measures = sec.getMeasures();

		Phrase phr = new PhraseExt(4, ip.getOrder(), secOrder);

		DrumPart dpClone = (DrumPart) ip.clone();
		boolean kicky = ip.getInstrument() < 38;
		boolean aboveSnarey = ip.getInstrument() > 40;
		sectionForcedDynamics &= (kicky || aboveSnarey);

		int chordsCount = actualProgression.size();

		List<Integer> drumPattern = generateDrumPatternFromPart(ip);

		if (!ip.isVelocityPattern() && drumPattern.indexOf(ip.getInstrument()) == -1) {
			//drumPhrase.addNote(new Note(Integer.MIN_VALUE, patternDurationTotal, 100));
			phr.setStartTime(START_TIME_DELAY);
			addOffsetsToPhrase(phr, ip);
			return phr;
		}

		List<Integer> drumVelocityPattern = generateDrumVelocityPatternFromPart(sec, ip);

		Random drumFillGenerator = new Random(
				ip.getPatternSeedWithPartOffset() + ip.getOrder() + sec.getTypeMelodyOffset());
		// bar iter
		int hits = ip.getHitsPerPattern();
		int swingPercentAmount = (hits % 2 == 0) ? ip.getSwingPercent() : 50;

		List<Integer> fillPattern = ip.getChordSpanFill()
				.getPatternByLength(actualProgression.size(), ip.isFillFlip());

		for (int o = 0; o < measures; o++) {
			// exceptions are generated the same for each bar, but differently for each pattern within bar (if there is more than 1)
			Random exceptionGenerator = new Random(
					ip.getPatternSeedWithPartOffset() + ip.getOrder());
			int chordSpan = ip.getChordSpan();
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
					variations = fillVariations(sec, ip, variations, 4, chanceMultipliers);
				}

				double halfDurMulti = (chordIndex >= (chordsCount + 1) / 2
						&& sec.getTransitionType() == 4) ? 2.0 : 1.0;

				if ((variations != null) && (chordIndex == 0)) {
					for (Integer var : variations) {
						if (o == measures - 1) {
							//LG.d("Drum #" + dp.getOrder() + " variation: " + var);
						}

						switch (var) {
						case 0:
							ignoreChordSpanFill = true;
							break;
						case 1:
							extraExceptionChance = (kicky || aboveSnarey)
									? ip.getExceptionChance() + 10
									: ip.getExceptionChance();
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

				double drumDuration = (ip.getPattern() == RhythmPattern.MELODY1
						&& melodyNotePatternMap != null) ? Durations.SIXTEENTH_NOTE
								: Durations.WHOLE_NOTE * chordSpan / hits;
				drumDuration *= halfDurMulti;
				double durationNow = 0.0;
				int k = 0;
				while (durationNow + DBL_ERR < patternDurationTotal) {
					int drum = drumPattern.get(k);
					int velocity = drumVelocityPattern.get(k);
					int pitch = (drum >= 0) ? drum : Integer.MIN_VALUE;
					if (drum < 0 && (ip.isVelocityPattern() || (o > 0 && sectionForcedDynamics))) {
						velocity = (velocity * 5) / 10;
						pitch = ip.getInstrument();
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

						int drumFillUnpauseChance = ip.getInstrument() < 46 ? 20 : 10;
						if (pitch < 0 && drumFillGenerator.nextInt(100) < drumFillUnpauseChance) {
							pitch = ip.getInstrument();
						}

					}
					boolean exception = exceptionGenerator.nextInt(100) < (ip.getExceptionChance()
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
						n1.setDuration(0.5 * n1.getRhythmValue() * GLOBAL_DURATION_MULTIPLIER);
						n2.setDuration(0.5 * n2.getRhythmValue() * GLOBAL_DURATION_MULTIPLIER);
						phr.addNote(n1);
						phr.addNote(n2);
					} else {
						Note n1 = new Note(pitch, usedDrumDuration, velocity);
						n1.setDuration(0.5 * n1.getRhythmValue() * GLOBAL_DURATION_MULTIPLIER);
						phr.addNote(n1);
					}
					durationNow += usedDrumDuration;
					k = (k + 1) % drumPattern.size();
				}
			}
		}
		if (genVars && variations != null) {
			sec.setVariation(4, ip.getAbsoluteOrder(), variations);
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
					chordSlashPhrase.addChord(new int[] { Integer.MIN_VALUE },
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
			Note n = algoGen1GenerateNote(mp, chord, true, MELODY_SCALE, null, melodyGenerator,
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
			// TODO: already set from melodyNotePatternMap in later processing, remove?
			//LG.d("Setting note pattern!");
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

		int[] arpOctaveArray = IntStream.iterate(0, e -> (e + 12) % 24)
				.limit(ap.getHitsPerPattern() * 2).toArray();


		List<Integer> arpOctavePattern = Arrays.stream(arpOctaveArray).boxed()
				.collect(Collectors.toList());

		// TODO: note pattern, different from rhythm pattern
		//if (ap.getPattern() == RhythmPattern.RANDOM) {
		Collections.shuffle(arpOctavePattern, uiGenerator3arpOctave);
		//}
		// always generate ap.getHitsPerPattern(), 
		// cut off however many are needed (support for seed randoms)
		if (!(ap.getPattern() == RhythmPattern.MELODY1 && melodyNotePattern != null)) {
			arpPausesPattern = arpPausesPattern.subList(0, ap.getHitsPerPattern());
		}

		List<Integer> arpPattern = makeRandomArpPattern(ap.getHitsPerPattern(), true,
				uiGenerator2arpPattern);
		arpOctavePattern = arpOctavePattern.subList(0, ap.getHitsPerPattern());

		Collections.rotate(arpPattern, -1 * ap.getArpPatternRotate());

		if (needToReport) {
			//LG.d("Arp count: " + ap.getHitsPerPattern());
			//LG.d("Arp pattern: " + arpPattern.toString());
			//LG.d("Arp octaves: " + arpOctavePattern.toString());
		}
		//LG.d("Arp pauses : " + arpPausesPattern.toString());

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

	public static List<Integer> makeRandomArpPattern(int hits, boolean repeatableNotes,
			Random uiGenerator2arpPattern) {
		int[] arpPatternArray = IntStream.iterate(0, e -> (e + 1) % MAXIMUM_PATTERN_LENGTH)
				.limit(hits * 2).toArray();
		List<Integer> arpPattern = Arrays.stream(arpPatternArray).boxed()
				.collect(Collectors.toList());
		if (repeatableNotes) {
			arpPattern.addAll(arpPattern);
		}
		arpPattern = arpPattern.subList(0, hits);
		Collections.shuffle(arpPattern, uiGenerator2arpPattern);
		return arpPattern;
	}

	public static List<Integer> generateDrumPatternFromPart(DrumPart dp) {
		Random uiGenerator1drumPattern = new Random(
				dp.getPatternSeedWithPartOffset() + dp.getOrder() - 1);
		List<Integer> premadePattern = null;
		if (melodyNotePattern != null && dp.getPattern() == RhythmPattern.MELODY1) {
			//LG.d("Setting note pattern!");
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
					|| premadePattern.get(j) < 1;
			if (dp.isPatternFlip()) {
				blankDrum = !blankDrum;
			}
			if (blankDrum) {
				drumPattern.add(-1);
			} else {
				if (dp.getInstrument() == 42
						&& uiGenerator1drumPattern.nextInt(100) < OPENHAT_CHANCE) {
					drumPattern.add(46);
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
		Random uiGenerator1drumVelocityPattern = new Random(
				dp.getPatternSeedWithPartOffset() + dp.getOrder());
		List<Integer> drumVelocityPattern = new ArrayList<>();
		int multiplier = (gc.isScaleMidiVelocityInArrangement()) ? sec.getVol(4) : 100;
		if (dp.getCustomVelocities() != null
				&& dp.getCustomVelocities().size() >= dp.getHitsPerPattern()) {
			for (int i = 0; i < dp.getHitsPerPattern(); i++) {
				drumVelocityPattern.add(MidiGeneratorUtils
						.multiplyVelocity(dp.getCustomVelocities().get(i), multiplier, 0, 1));
			}
		} else {
			int minVel = MidiGeneratorUtils.multiplyVelocity(dp.getVelocityMin(), multiplier, 0, 1);
			int maxVel = MidiGeneratorUtils.multiplyVelocity(dp.getVelocityMax(), multiplier, 1, 0);
			int velocityRange = maxVel - minVel;
			for (int j = 0; j < dp.getHitsPerPattern(); j++) {
				int velocity = uiGenerator1drumVelocityPattern.nextInt(velocityRange) + minVel;
				drumVelocityPattern.add(velocity);
			}
		}

		/*LG.d("Drum velocity pattern for " + dp.getInstrument() + " : "
				+ drumVelocityPattern.toString());*/
		return drumVelocityPattern;
	}
}
