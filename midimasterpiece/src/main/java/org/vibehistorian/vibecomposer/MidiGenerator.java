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
along with this program; if not, write to the Free Software
Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/

package org.vibehistorian.vibecomposer;

import static org.vibehistorian.vibecomposer.MidiUtils.addShortenedChord;
import static org.vibehistorian.vibecomposer.MidiUtils.applyChordFreqMap;
import static org.vibehistorian.vibecomposer.MidiUtils.cIonianScale4;
import static org.vibehistorian.vibecomposer.MidiUtils.chordsMap;
import static org.vibehistorian.vibecomposer.MidiUtils.convertChordToLength;
import static org.vibehistorian.vibecomposer.MidiUtils.cpRulesMap;
import static org.vibehistorian.vibecomposer.MidiUtils.getBasicChordsFromRoots;
import static org.vibehistorian.vibecomposer.MidiUtils.maX;
import static org.vibehistorian.vibecomposer.MidiUtils.mappedChord;
import static org.vibehistorian.vibecomposer.MidiUtils.pickDurationWeightedRandom;
import static org.vibehistorian.vibecomposer.MidiUtils.squishChordProgression;
import static org.vibehistorian.vibecomposer.MidiUtils.transposeChord;
import static org.vibehistorian.vibecomposer.MidiUtils.transposeScale;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.SwingUtilities;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.MidiUtils.POOL;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Panels.ArpGenSettings;
import org.vibehistorian.vibecomposer.Panels.DrumGenSettings;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Popups.VariationPopup;

import jm.JMC;
import jm.gui.show.ShowScore;
import jm.music.data.CPhrase;
import jm.music.data.Note;
import jm.music.data.Part;
import jm.music.data.Phrase;
import jm.music.data.Score;
import jm.music.tools.Mod;
import jm.util.Write;

public class MidiGenerator implements JMC {

	public static double noteMultiplier = 2.0;

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

	}

	public static void recalculateDurations() {
		Durations.SIXTEENTH_NOTE = 0.25 * noteMultiplier;
		Durations.DOTTED_SIXTEENTH_NOTE = 0.375 * noteMultiplier;
		Durations.EIGHTH_NOTE = 0.5 * noteMultiplier;
		Durations.DOTTED_EIGHTH_NOTE = 0.75 * noteMultiplier;
		Durations.QUARTER_NOTE = 1.0 * noteMultiplier;
		Durations.DOTTED_QUARTER_NOTE = 1.5 * noteMultiplier;
		Durations.HALF_NOTE = 2.0 * noteMultiplier;
		Durations.DOTTED_HALF_NOTE = 3.0 * noteMultiplier;
		Durations.WHOLE_NOTE = 4.0 * noteMultiplier;

		START_TIME_DELAY = Durations.EIGHTH_NOTE;
		swingUnitOfTime = Durations.SIXTEENTH_NOTE;
		MELODY_DUR_ARRAY = new double[] { Durations.QUARTER_NOTE, Durations.DOTTED_EIGHTH_NOTE,
				Durations.EIGHTH_NOTE, Durations.SIXTEENTH_NOTE };
		CHORD_DUR_ARRAY = new double[] { Durations.WHOLE_NOTE, Durations.DOTTED_HALF_NOTE,
				Durations.HALF_NOTE, Durations.QUARTER_NOTE };
	}

	public static final double[] SECOND_ARRAY_STRUM = { 0, 0.016666, 0.03125, 0.0625, 0.0625, 0.125,
			0.16666667, 0.250, 0.333333, 0.50000, 0.750, 1.000 };

	private static final boolean debugEnabled = true;
	private static final PrintStream originalStream = System.out;

	// big G
	public static GUIConfig gc;

	// opened windows
	public static List<ShowScore> showScores = new ArrayList<>();
	public static int windowLoc = 5;

	// track map for Solo
	public static List<InstPart> trackList = new ArrayList<>();

	// constants
	public static final int MAXIMUM_PATTERN_LENGTH = 8;
	private static double swingUnitOfTime = Durations.SIXTEENTH_NOTE;
	public static final int OPENHAT_CHANCE = 15;
	private static final int maxAllowedScaleNotes = 7;
	public static double START_TIME_DELAY = Durations.EIGHTH_NOTE;
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

	public static String FIRST_CHORD = null;
	public static String LAST_CHORD = null;

	public static boolean DISPLAY_SCORE = false;
	public static int showScoreMode = 0;

	public static boolean COLLAPSE_DRUM_TRACKS = true;
	public static boolean COLLAPSE_MELODY_TRACKS = true;


	// for internal use only
	private static double[] MELODY_DUR_ARRAY = { Durations.QUARTER_NOTE,
			Durations.DOTTED_EIGHTH_NOTE, Durations.EIGHTH_NOTE, Durations.SIXTEENTH_NOTE };
	private double[] MELODY_DUR_CHANCE = { 0.3, 0.6, 1.0, 1.0 };

	private static double[] CHORD_DUR_ARRAY = { Durations.WHOLE_NOTE, Durations.DOTTED_HALF_NOTE,
			Durations.HALF_NOTE, Durations.QUARTER_NOTE };
	private double[] CHORD_DUR_CHANCE = { 0.0, 0.20, 0.80, 1.0 };

	private List<Integer> MELODY_SCALE = cIonianScale4;
	private List<Double> progressionDurations = new ArrayList<>();
	private List<int[]> chordProgression = new ArrayList<>();
	private List<int[]> rootProgression = new ArrayList<>();

	public static Map<Integer, List<Note>> userMelodyMap = new HashMap<>();
	private Map<Integer, List<Note>> chordMelodyMap1 = new HashMap<>();
	private List<int[]> melodyBasedChordProgression = new ArrayList<>();
	private List<int[]> melodyBasedRootProgression = new ArrayList<>();

	public static List<Integer> melodyNotePattern = null;

	private int samePitchCount = 0;
	private int previousPitch = 0;

	private int modTrans = 0;

	public MidiGenerator(GUIConfig gc) {
		MidiGenerator.gc = gc;
	}

	private List<Integer> patternFromNotes(Collection<Note> notes) {
		// strategy: use 64 hits in pattern, then simplify if needed
		int hits = 64;

		int chordsTotal = chordInts.size();
		double measureTotal = chordsTotal
				* ((gc.isDoubledDurations()) ? Durations.WHOLE_NOTE : Durations.HALF_NOTE);
		double timeForHit = measureTotal / hits;
		List<Integer> pattern = new ArrayList<>();
		List<Double> durationBuckets = new ArrayList<>();
		for (int i = 1; i <= hits; i++) {
			durationBuckets.add(timeForHit * i - 0.01);
			pattern.add(0);
		}
		pattern.set(0, 1);
		double currentDuration = 0;
		int explored = 0;
		for (Note n : notes) {
			/*System.out.println(
					"Current dur: " + currentDuration + ", + rhythm: " + n.getRhythmValue());*/
			currentDuration += n.getRhythmValue();
			for (int i = explored; i < hits; i++) {
				if (currentDuration < durationBuckets.get(i)) {
					pattern.set(i, 1);
					explored = i;
					break;
				}
			}
		}
		//System.out.println(StringUtils.join(pattern, ", "));
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
			Random generator) {
		//clamp
		if (startIndex < 0)
			startIndex = 0;
		if (endIndex > chord.length - 1) {
			endIndex = chord.length - 1;
		}
		int index = generator.nextInt(endIndex - startIndex + 1) + startIndex;
		return chord[index];
	}


	private List<Boolean> generateMelodyDirectionsFromChordProgression(List<int[]> progression,
			boolean roots) {

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

	private Vector<Note> generateMelodySkeletonFromChords(MelodyPart mp, List<int[]> chords,
			List<int[]> roots, int measures, int notesSeedOffset, Section sec,
			List<Integer> variations) {

		boolean genVars = variations == null;

		boolean fillChordMelodyMap = false;
		if (chordMelodyMap1.isEmpty() && notesSeedOffset == 0
				&& (roots.size() == chordInts.size())) {
			fillChordMelodyMap = true;
		}

		int MAX_JUMP_SKELETON_CHORD = gc.getMaxNoteJump();
		int SAME_RHYTHM_CHANCE = gc.getMelodySameRhythmChance();
		int ALTERNATE_RHYTHM_CHANCE = gc.getMelodyAlternateRhythmChance();
		int EXCEPTION_CHANCE = gc.getMelodyExceptionChance();

		int seed = mp.getPatternSeed();

		Vector<Note> noteList = new Vector<>();

		Random algoGenerator = new Random(gc.getRandomSeed());
		if (algoGenerator.nextInt(100) < gc.getMelodyUseOldAlgoChance()) {
			return oldAlgoGenerateMelodySkeletonFromChords(mp, measures, roots);
		}

		Random generator = new Random(seed + notesSeedOffset);
		Random exceptionGenerator = new Random(seed + 2 + notesSeedOffset);
		Random sameRhythmGenerator = new Random(seed + 3);
		Random alternateRhythmGenerator = new Random(seed + 4);
		Random variationGenerator = new Random(seed + notesSeedOffset);
		int numberOfVars = Section.variationDescriptions[0].length - 2;

		double[] melodySkeletonDurations = { Durations.SIXTEENTH_NOTE, Durations.EIGHTH_NOTE,
				Durations.DOTTED_EIGHTH_NOTE, Durations.QUARTER_NOTE };

		int weightIncreaser = gc.getMelodyQuickness() / 4;
		int weightReducer = 25 - weightIncreaser / 2;
		int[] melodySkeletonDurationWeights = { 0 + weightIncreaser, 50 - weightReducer,
				85 - weightReducer, 100 };

		List<int[]> usedChords = null;
		if (gc.isMelodyBasicChordsOnly()) {
			List<int[]> basicChordsUnsquished = getBasicChordsFromRoots(roots);
			for (int i = 0; i < chords.size(); i++) {
				basicChordsUnsquished.set(i, convertChordToLength(basicChordsUnsquished.get(i),
						chords.get(i).length, true));
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

		List<int[]> stretchedChords = usedChords.stream().map(e -> convertChordToLength(e, 4, true))
				.collect(Collectors.toList());
		List<Boolean> directions = generateMelodyDirectionsFromChordProgression(stretchedChords,
				true);
		boolean alternateRhythm = alternateRhythmGenerator.nextInt(100) < ALTERNATE_RHYTHM_CHANCE;
		//System.out.println("Alt: " + alternateRhythm);

		for (int o = 0; o < measures; o++) {
			int previousNotePitch = 0;
			int extraTranspose = 0;

			for (int i = 0; i < stretchedChords.size(); i++) {
				// either after first measure, or after first half of combined chord prog

				if (genVars && (i == 0)) {
					variations = fillVariations(sec, variationGenerator, variations, numberOfVars,
							0);
				}

				if ((variations != null) && (i == 0)) {
					for (Integer var : variations) {
						if (o == measures - 1) {
							System.out.println("Melody variation: " + var);
						}

						switch (var) {
						case 0:
							extraTranspose = 12;
							break;
						case 1:
							MAX_JUMP_SKELETON_CHORD = ((MAX_JUMP_SKELETON_CHORD + 1) % 4) + 1;
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


				boolean sameRhythmTwice = sameRhythmGenerator.nextInt(100) < SAME_RHYTHM_CHANCE;

				double rhythmDuration = sameRhythmTwice ? progressionDurations.get(i) / 2.0
						: progressionDurations.get(i);
				int rhythmSeed = (alternateRhythm && i % 2 == 1) ? seed + 1 : seed;
				Rhythm rhythm = new Rhythm(rhythmSeed, rhythmDuration, melodySkeletonDurations,
						melodySkeletonDurationWeights);
				if (i % 2 == 0) {
					previousNotePitch = 0;
					generator.setSeed(seed + notesSeedOffset);
					exceptionGenerator.setSeed(seed + 2 + notesSeedOffset);
				}
				List<Double> durations = rhythm.regenerateDurations();
				if (sameRhythmTwice) {
					durations.addAll(durations);
				}

				int[] chord = stretchedChords.get(i);
				int exceptionCounter = gc.getMaxExceptions();
				boolean direction = directions.get(i);
				boolean allowException = true;
				for (int j = 0; j < durations.size(); j++) {

					if (allowException && j > 0 && exceptionCounter > 0
							&& exceptionGenerator.nextInt(100) < EXCEPTION_CHANCE) {
						direction = !direction;
						exceptionCounter--;
					}
					int pitch = 0;
					int startIndex = 0;
					int endIndex = chord.length - 1;
					if (previousNotePitch != 0) {
						// up, or down
						if (direction) {
							startIndex = selectClosestIndexFromChord(chord, previousNotePitch,
									true);
							while (endIndex - startIndex >= MAX_JUMP_SKELETON_CHORD) {
								endIndex--;
							}
						} else {
							endIndex = selectClosestIndexFromChord(chord, previousNotePitch, false);
							while (endIndex - startIndex >= MAX_JUMP_SKELETON_CHORD) {
								startIndex++;
							}
						}
					}
					pitch = pickRandomBetweenIndexesInclusive(chord, startIndex, endIndex,
							generator);

					/*// override for first note
					if ((i % 2 == 0) && (j == 0)) {
						pitch = 60;
					}
					
					// override for last note
					if ((i % 2 == 1) && (j == durations.size() - 1)) {
						pitch = 60;
					}*/
					double swingDuration = durations.get(j);
					Note n = new Note(pitch + extraTranspose, swingDuration, 100);
					//TODO: make sound good
					if (previousNotePitch == pitch) {
						direction = !direction;
						allowException = false;
					} else {
						allowException = true;
					}
					previousNotePitch = pitch;
					noteList.add(n);
					if (fillChordMelodyMap && o == 0) {
						chordMelodyMap1.get(Integer.valueOf(i)).add(n);
					}
				}

			}
		}

		if (fillChordMelodyMap) {
			makeMelodyPitchFrequencyMap(1, chordMelodyMap1.keySet().size() - 1, 2);
		}
		if (genVars && variations != null) {
			sec.setVariation(0, getAbsoluteOrder(0, mp), variations);
		}
		return noteList;
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
		System.out.println("LEGACY ALGORITHM!");
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
					generatedMelody = generateMelodyForChord(mp, genRootProg.get(j),
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

	private boolean isMultiple(double first, double second) {
		double result = first / second;
		double rounded = Math.round(result);
		if (roughlyEqual(result, rounded)) {
			return true;
		} else {
			return false;
		}
	}

	private boolean roughlyEqual(double first, double second) {
		return Math.abs(first - second) < 0.001;
	}

	private int getAllowedPitchFromRange(int min, int max) {

		List<Integer> allowedPitches = MELODY_SCALE;
		int adjustment = 0;
		while (max < allowedPitches.get(0)) {
			min += 12;
			max += 12;
			adjustment -= 12;
		}
		while (min > allowedPitches.get(allowedPitches.size() - 1)) {
			min -= 12;
			max -= 12;
			adjustment += 12;
		}
		for (Integer i : allowedPitches) {
			if (i > min && i < max) {
				return i + adjustment;
			}
		}
		for (Integer i : allowedPitches) {
			if (i >= min && i <= max) {
				return i + adjustment;
			}
		}
		return 40;
	}

	private Vector<Note> convertMelodySkeletonToFullMelody(MelodyPart mp, Section sec,
			Vector<Note> skeleton, int notesSeedOffset) {
		int seed = mp.getPatternSeed() + mp.getOrder();
		Random splitGenerator = new Random(seed + 4);
		Random pauseGenerator = new Random(seed + 5);
		Random pauseGenerator2 = new Random(seed + 7);
		Random variationGenerator = new Random(seed + 6);
		Random velocityGenerator = new Random(seed + 1 + notesSeedOffset);
		int splitChance = gc.getMelodySplitChance() * gc.getMelodyQuickness() / 100;
		Vector<Note> fullMelody = new Vector<>();
		int chordCounter = 0;
		double durCounter = 0.0;
		double currentChordDur = progressionDurations.get(0);

		int minVel = mp.getVelocityMin() + (5 * sec.getMelodyChance()) / 10 - 50;
		minVel = (minVel < 0) ? 0 : minVel;
		int maxVel = mp.getVelocityMax() + (5 * sec.getMelodyChance()) / 10 - 50;
		maxVel = (maxVel < 1) ? 1 : maxVel;

		for (int i = 0; i < skeleton.size(); i++) {
			double adjDur = skeleton.get(i).getRhythmValue();
			if (durCounter + adjDur > currentChordDur) {
				chordCounter = (chordCounter + 1) % progressionDurations.size();
				if (chordCounter == 0) {
					// when measure resets
					if (variationGenerator.nextInt(100) < gc.getArrangementPartVariationChance()) {
						splitChance = (int) (splitChance * 1.2);
					}
				}
				durCounter = 0.0;
				currentChordDur = progressionDurations.get(chordCounter);
				splitGenerator.setSeed(seed + 4);
				pauseGenerator.setSeed(seed + 5);
			}
			Note emptyNote = new Note(Integer.MIN_VALUE, adjDur);
			Note emptyNoteHalf = new Note(Integer.MIN_VALUE, adjDur / 2.0);
			Note emptyNoteHalf2 = new Note(Integer.MIN_VALUE, adjDur / 2.0);
			int p = pauseGenerator.nextInt(100);
			int p2 = pauseGenerator2.nextInt(100);
			boolean pause1 = p < mp.getPauseChance();
			boolean pause2 = p2 < (mp.getPauseChance());

			int velocity = velocityGenerator.nextInt(1 + maxVel - minVel) + minVel;

			skeleton.get(i).setDynamic(velocity);

			if (adjDur > Durations.SIXTEENTH_NOTE * 1.4
					&& splitGenerator.nextInt(100) < splitChance) {
				Note n1 = skeleton.get(i);
				Note n2 = skeleton.get((i + 1) % skeleton.size());
				int pitch = 0;
				if (n1.getPitch() >= n2.getPitch()) {
					pitch = getAllowedPitchFromRange(n2.getPitch(), n1.getPitch());
				} else {
					pitch = getAllowedPitchFromRange(n1.getPitch(), n2.getPitch());
				}


				double swingDuration1 = adjDur * 0.5;
				double swingDuration2 = adjDur - swingDuration1;

				Note n1split1 = new Note(n1.getPitch(), swingDuration1, n1.getDynamic());
				Note n1split2 = new Note(pitch, swingDuration2, n1.getDynamic() - 10);

				fullMelody.add(pause1 ? emptyNoteHalf : n1split1);
				fullMelody.add(pause2 ? emptyNoteHalf2 : n1split2);

			} else {
				fullMelody.add(pause1 ? emptyNote : skeleton.get(i));

			}
			durCounter += adjDur;
		}
		return fullMelody;
	}

	private void swingMelody(MelodyPart mp, Vector<Note> fullMelody) {
		double currentChordDur = progressionDurations.get(0);
		int chordCounter = 0;

		int swingPercentAmount = mp.getSwingPercent();
		double swingAdjust = swingUnitOfTime * (swingPercentAmount / ((double) 50.0))
				- swingUnitOfTime;
		double durCounter = 0.0;
		for (Note n : fullMelody) {
			double adjDur = n.getRhythmValue();
			double adjComparison = 0;
			if (!isMultiple(durCounter + adjDur, 2 * swingUnitOfTime)) {
				adjComparison = swingAdjust;
			}
			if (durCounter + adjDur + adjComparison - 0.001 > currentChordDur) {
				chordCounter = (chordCounter + 1) % progressionDurations.size();
				currentChordDur = progressionDurations.get(chordCounter);
				durCounter = 0.0;
				swingAdjust = swingUnitOfTime * (swingPercentAmount / ((double) 50.0))
						- swingUnitOfTime;
			}
			if (isMultiple(durCounter + adjDur, 2 * swingUnitOfTime)) {
				// do nothing, it ends on the main grid
			} else {
				// needs swing
				/*System.out.println("Swinging at: " + durCounter + ", ends at: "
						+ (durCounter + adjDur) + ", added: " + swingAdjust);*/
				adjDur += swingAdjust;
				n.setDuration(adjDur * Note.DEFAULT_DURATION_MULTIPLIER);
				n.setRhythmValue(adjDur);
				swingAdjust *= -1;
			}

			durCounter += adjDur;
		}
	}

	private List<String> makeMelodyPitchFrequencyMap(int start, int end, int orderOfMatch) {
		// only affect chords between start and end <start;end)
		List<int[]> alternateChordProg = new ArrayList<>();
		List<String> chordStrings = new ArrayList<>();
		String prevChordString = null;
		if (start > 0) {
			alternateChordProg
					.add(Arrays.copyOf(chordProgression.get(0), chordProgression.get(0).length));
			melodyBasedRootProgression
					.add(Arrays.copyOf(rootProgression.get(0), rootProgression.get(0).length));
			prevChordString = chordInts.get(start - 1);
		}

		for (int i = start; i < end; i++) {

			List<Integer> chordFreqs = new ArrayList<>();
			double totalDuration = 0;
			for (Note n : chordMelodyMap1.get(i)) {
				double dur = n.getRhythmValue();
				double durCounter = 0.0;
				int index = i;
				if (index >= progressionDurations.size()) {
					index = progressionDurations.size() - 1;
				}
				while (durCounter < dur && totalDuration < progressionDurations.get(index)) {
					chordFreqs.add(n.getPitch() % 12);
					durCounter += Durations.SIXTEENTH_NOTE;
					totalDuration += Durations.SIXTEENTH_NOTE;
				}
			}

			Map<Integer, Long> freqCounts = chordFreqs.stream()
					.collect(Collectors.groupingBy(e -> e, Collectors.counting()));

			Map<Integer, Long> top3 = freqCounts.entrySet().stream()
					.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(10)
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
							(e1, e2) -> e1, LinkedHashMap::new));

			//top3.entrySet().stream().forEach(System.out::println);
			String chordString = applyChordFreqMap(top3, orderOfMatch, prevChordString);
			System.out.println("Alternate chord #" + i + ": " + chordString);
			int[] chordLongMapped = chordsMap.get(chordString);
			melodyBasedRootProgression.add(Arrays.copyOf(chordLongMapped, chordLongMapped.length));
			alternateChordProg.add(chordLongMapped);
			chordStrings.add(chordString);
			prevChordString = chordString;
		}
		if (end < chordMelodyMap1.keySet().size()) {
			alternateChordProg
					.add(Arrays.copyOf(chordProgression.get(chordMelodyMap1.keySet().size() - 1),
							chordProgression.get(chordMelodyMap1.keySet().size() - 1).length));
			melodyBasedRootProgression
					.add(Arrays.copyOf(rootProgression.get(rootProgression.size() - 1),
							rootProgression.get(rootProgression.size() - 1).length));
		}

		melodyBasedChordProgression = squishChordProgression(alternateChordProg,
				gc.isSpiceFlattenBigChords(), gc.getRandomSeed(),
				gc.getChordGenSettings().getFlattenVoicingChance());
		return chordStrings;
	}

	private Note generateNote(MelodyPart mp, int[] chord, boolean isAscDirection,
			List<Integer> chordScale, Note previousNote, Random generator, double durationLeft) {
		// int randPitch = generator.nextInt(8);
		int velMin = mp.getVelocityMin();
		int velSpace = mp.getVelocityMax() - velMin;

		int direction = (isAscDirection) ? 1 : -1;
		double dur = pickDurationWeightedRandom(generator, durationLeft, MELODY_DUR_ARRAY,
				MELODY_DUR_CHANCE, Durations.SIXTEENTH_NOTE);
		boolean isPause = (generator.nextInt(100) < mp.getPauseChance());
		if (previousNote == null) {
			int[] firstChord = chord;
			int chordNote = (gc.isFirstNoteRandomized()) ? generator.nextInt(firstChord.length) : 0;

			int chosenPitch = 60 + (firstChord[chordNote] % 12);

			previousPitch = chordScale.indexOf(Integer.valueOf(chosenPitch));
			if (previousPitch == -1) {
				System.out.println("ERROR PITCH -1 for: " + chosenPitch);
				previousPitch = chordScale.indexOf(Integer.valueOf(chosenPitch + 1));
				if (previousPitch == -1) {
					System.out.println("NOT EVEN +1 pitch exists for " + chosenPitch + "!");
				}
			}

			//System.out.println(firstChord[chordNote] + " > from first chord");
			if (isPause) {
				return new Note(Integer.MIN_VALUE, dur);
			}

			return new Note(chosenPitch, dur, velMin + generator.nextInt(velSpace));
		}

		int change = generator.nextInt(gc.getMaxNoteJump());
		// weighted against same note
		if (change == 0) {
			change = generator.nextInt((gc.getMaxNoteJump() + 1) / 2);
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
			//System.out.println("UNSAMING NOTE!: " + previousPitch + ", BY: " + (-direction * change));
			generatedPitch = maX(previousPitch - direction * change, maxAllowedScaleNotes);
			samePitchCount = 0;
		}
		previousPitch = generatedPitch;
		if (isPause) {
			return new Note(Integer.MIN_VALUE, dur);
		}
		return new Note(chordScale.get(generatedPitch), dur, velMin + generator.nextInt(velSpace));

	}

	public void generatePrettyUserChords(int mainGeneratorSeed, int fixedLength,
			double maxDuration) {
		generateChordProgression(mainGeneratorSeed, gc.getFixedDuration(), 4 * Durations.HALF_NOTE);
	}

	private List<int[]> generateChordProgression(int mainGeneratorSeed, int fixedLength,
			double maxDuration) {

		if (!userChords.isEmpty()) {
			List<int[]> userProgression = new ArrayList<>();
			chordInts.clear();
			chordInts.addAll(userChords);
			for (String chordString : userChords) {
				userProgression.add(mappedChord(chordString));
			}
			System.out.println(
					"Using user's custom progression: " + StringUtils.join(userChords, ","));
			return userProgression;
		}

		Random generator = new Random();
		generator.setSeed(mainGeneratorSeed);

		Random durationGenerator = new Random();
		durationGenerator.setSeed(mainGeneratorSeed);

		Map<String, List<String>> r = cpRulesMap;
		chordInts.clear();

		int maxLength = (fixedLength > 0) ? fixedLength : 8;
		if (fixedLength == 8) {
			maxDuration *= 2;
		}
		double fixedDuration = maxDuration / maxLength;
		int currentLength = 0;
		double currentDuration = 0.0;
		List<String> next = r.get("S");
		if (LAST_CHORD != null) {
			next = new ArrayList<String>();
			next.add(String.valueOf(LAST_CHORD));
		}
		List<String> debugMsg = new ArrayList<>();

		List<String> allowedSpiceChords = new ArrayList<>();
		for (int i = 2; i < MidiUtils.SPICE_NAMES_LIST.size(); i++) {
			String chordString = MidiUtils.SPICE_NAMES_LIST.get(i);
			if (!gc.isDimAugEnabled() && MidiUtils.BANNED_DIM_AUG_LIST.contains(chordString)) {
				continue;
			}
			if (!gc.isEnable9th13th() && MidiUtils.BANNED_9_13_LIST.contains(chordString)) {
				continue;
			}
			allowedSpiceChords.add(chordString);
		}


		List<int[]> cpr = new ArrayList<>();
		int[] prevChord = null;
		boolean canRepeatChord = true;
		String lastUnspicedChord = null;
		Random chordRepeatGenerator = new Random(mainGeneratorSeed);
		while ((currentDuration <= maxDuration - Durations.EIGHTH_NOTE)
				&& currentLength < maxLength) {
			double durationLeft = maxDuration - Durations.EIGHTH_NOTE - currentDuration;

			double dur = (fixedLength > 0) ? fixedDuration
					: pickDurationWeightedRandom(durationGenerator, durationLeft, CHORD_DUR_ARRAY,
							CHORD_DUR_CHANCE, Durations.QUARTER_NOTE);

			if (next.size() == 0 && prevChord != null) {
				cpr.add(prevChord);
				break;
			}
			int nextInt = generator.nextInt(next.size());

			// if last and not empty first chord
			boolean isLastChord = durationLeft - dur < 0.01;
			String chordString = (isLastChord && FIRST_CHORD != null) ? FIRST_CHORD
					: next.get(nextInt);
			if (gc.isAllowChordRepeats() && (fixedLength < 8 || !isLastChord) && canRepeatChord
					&& chordInts.size() > 0 && chordRepeatGenerator.nextInt(100) < 10) {
				chordString = String.valueOf(lastUnspicedChord);
				canRepeatChord = false;
			}

			String firstLetter = chordString.substring(0, 1);
			String spicyChordString = firstLetter
					+ allowedSpiceChords.get(generator.nextInt(allowedSpiceChords.size()));
			if (chordString.endsWith("m") && spicyChordString.contains("maj")) {
				spicyChordString = spicyChordString.replace("maj", "m");
			} else if (chordString.length() == 1 && spicyChordString.contains("m")
					&& !spicyChordString.contains("dim") && !spicyChordString.contains("maj")) {
				spicyChordString = spicyChordString.replace("m", "maj");
			}

			//SPICE CHANCE - multiply by 100/10000 to get aug,dim/maj,min 7th
			// 
			if (generator.nextInt(100) < gc.getSpiceChance()
					&& (chordInts.size() < 7 || FIRST_CHORD == null)) {
			} else {
				spicyChordString = chordString;
			}

			chordInts.add(spicyChordString);

			//System.out.println("Fetching chord: " + chordInt);
			int[] mappedChord = mappedChord(spicyChordString);
			/*mappedChord = transposeChord(mappedChord, Mod.MAJOR_SCALE,
					gc.getScaleMode().noteAdjustScale);*/


			debugMsg.add("Generated int: " + nextInt + ", for chord: " + spicyChordString
					+ ", dur: " + dur + ", C[" + Arrays.toString(mappedChord) + "]");
			cpr.add(mappedChord);
			progressionDurations.add(dur);

			prevChord = mappedChord;
			next = r.get(chordString);

			if (fixedLength == 8 && chordInts.size() == 4) {
				FIRST_CHORD = chordString;
			}

			// if last and empty first chord
			if (durationLeft - dur < 0 && FIRST_CHORD != null) {
				FIRST_CHORD = chordString;
			}
			currentLength += 1;
			currentDuration += dur;
			lastUnspicedChord = chordString;

		}
		System.out.println("CHORD PROG LENGTH: " + cpr.size());
		Collections.reverse(progressionDurations);
		Collections.reverse(cpr);
		Collections.reverse(debugMsg);
		Collections.reverse(chordInts);

		for (String s : debugMsg) {
			System.out.println(s);
		}

		if (progressionDurations.size() > 1
				&& (progressionDurations.get(0) != progressionDurations.get(2))) {
			double middle = (progressionDurations.get(0) + progressionDurations.get(2)) / 2.0;
			progressionDurations.set(0, middle);
			progressionDurations.set(2, middle);

		}

		return cpr;
	}

	private Note[] generateMelodyForChord(MelodyPart mp, int[] chord, double maxDuration,
			Random generator, Note previousChordsNote, boolean isAscDirection) {
		List<Integer> scale = transposeScale(MELODY_SCALE, 0, false);

		double currentDuration = 0.0;

		Note previousNote = (gc.isFirstNoteFromChord()) ? null : previousChordsNote;
		List<Note> notes = new ArrayList<>();

		int exceptionsLeft = gc.getMaxExceptions();

		while (currentDuration <= maxDuration - Durations.SIXTEENTH_NOTE) {
			double durationLeft = maxDuration - Durations.SIXTEENTH_NOTE - currentDuration;
			boolean exceptionChangeUsed = false;
			// generate note,
			boolean actualDirection = isAscDirection;
			if ((generator.nextInt(100) < 33) && (exceptionsLeft > 0)) {
				//System.out.println("Exception used for chordnote: " + chord[0]);
				exceptionChangeUsed = true;
				actualDirection = !actualDirection;
			}
			Note note = generateNote(mp, chord, actualDirection, scale, previousNote, generator,
					durationLeft);
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

	public void generateMasterpiece(int mainGeneratorSeed, String fileName) {
		System.out.println("--- GENERATING MASTERPIECE.. ---");
		trackList.clear();
		//MELODY_SCALE = gc.getScaleMode().absoluteNotesC;

		Score score = new Score("MainScore", 120);
		Part bassRoots = new Part("BassRoots",
				(!gc.getBassPart().isMuted()) ? gc.getBassPart().getInstrument() : 74, 8);

		List<Part> melodyParts = new ArrayList<>();
		for (int i = 0; i < gc.getMelodyParts().size(); i++) {
			Part p = new Part("Melodies" + i, gc.getMelodyParts().get(i).getInstrument(),
					gc.getMelodyParts().get(i).getMidiChannel() - 1);
			melodyParts.add(p);
		}

		List<Part> chordParts = new ArrayList<>();
		for (int i = 0; i < gc.getChordParts().size(); i++) {
			Part p = new Part("Chords" + i, gc.getChordParts().get(i).getInstrument(),
					gc.getChordParts().get(i).getMidiChannel() - 1);
			chordParts.add(p);
		}
		List<Part> arpParts = new ArrayList<>();
		for (int i = 0; i < gc.getArpParts().size(); i++) {
			Part p = new Part("Arps" + i, gc.getArpParts().get(i).getInstrument(),
					gc.getArpParts().get(i).getMidiChannel() - 1);
			arpParts.add(p);
		}
		List<Part> drumParts = new ArrayList<>();
		for (int i = 0; i < gc.getDrumParts().size(); i++) {
			Part p = new Part("MainDrums", 0, 9);
			drumParts.add(p);
		}


		List<int[]> generatedRootProgression = generateChordProgression(mainGeneratorSeed,
				gc.getFixedDuration(), 4 * Durations.HALF_NOTE);
		if (!userChordsDurations.isEmpty()) {
			progressionDurations = userChordsDurations;
		}
		if (gc.isDoubledDurations()) {
			for (int i = 0; i < progressionDurations.size(); i++) {
				progressionDurations.set(i, progressionDurations.get(i) * 2);
			}
		}

		List<Double> actualDurations = progressionDurations;

		List<int[]> actualProgression = squishChordProgression(generatedRootProgression,
				gc.isSpiceFlattenBigChords(), gc.getRandomSeed(),
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
		System.out.println("Starting arrangement..");
		double measureLength = 0;
		for (Double d : progressionDurations) {
			measureLength += d;
		}
		int counter = 0;

		// prepare progressions
		chordProgression = actualProgression;
		rootProgression = generatedRootProgression;
		List<Double> altProgressionDurations = new ArrayList<>();
		List<int[]> altChordProgression = new ArrayList<>();
		List<int[]> altRootProgression = new ArrayList<>();

		fillAlternates(altProgressionDurations, altChordProgression, altRootProgression);

		// run one empty pass through melody generation
		if (userMelody != null) {
			processUserMelody(userMelody);
			actualProgression = chordProgression;
			generatedRootProgression = rootProgression;
			actualDurations = progressionDurations;
		} else {
			fillMelody(gc.getMelodyParts().get(0), actualProgression, generatedRootProgression, 1,
					0, new Section(), null);
		}

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


		boolean never = false;
		if (never) {
			InputStream is = new InputStream() {

				@Override
				public int read() throws IOException {
					// TODO Auto-generated method stub
					return 0;
				}
			};
		}
		boolean isPreview = arr.getSections().size() == 1;
		System.out.println("MidiGenerator - Overridden: " + overridden);
		int arrSeed = (arr.getSeed() != 0) ? arr.getSeed() : mainGeneratorSeed;

		int originalPartVariationChance = gc.getArrangementPartVariationChance();
		int secOrder = -1;

		int transToSet = 0;

		for (Section sec : arr.getSections()) {
			if (overridden) {
				sec.recalculatePartVariationMapBoundsIfNeeded();
			}
			secOrder++;
			System.out.println("Processing section.. " + sec.getType());
			sec.setStartTime(measureLength * counter);

			Random rand = new Random();

			if (transToSet != 0) {
				modTrans = transToSet;
			}

			if (sec.getType().equals("CLIMAX")) {
				// increase variations in follow-up CLIMAX sections, reset when climax ends
				gc.setArrangementPartVariationChance(
						gc.getArrangementPartVariationChance() + originalPartVariationChance / 2);
			} else {
				gc.setArrangementPartVariationChance(originalPartVariationChance);
			}

			if (sec.getType().equals("BUILDUP")) {
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
			System.out.println("Note offset category: " + notesSeedOffset);

			Random variationGen = new Random(arrSeed + sec.getTypeSeedOffset());
			List<Boolean> riskyVariations = sec.getRiskyVariations();
			if (riskyVariations == null) {
				riskyVariations = new ArrayList<>();
				for (int i = 0; i < Section.riskyVariationNames.length; i++) {
					boolean isVariation = variationGen.nextInt(100) < gc
							.getArrangementVariationChance();
					// generate n-1 skip only if next section is same type
					if (i == 0) {
						// if not last, and next section has the same Type
						isVariation &= (secOrder < arr.getSections().size() - 1 && arr.getSections()
								.get(secOrder + 1).getType().equals(sec.getType()));
					}
					// generate "chord swap/melody swap" only for non-critical sections
					if (i == 1 || i == 2) {
						isVariation &= notesSeedOffset > 0;
					}
					riskyVariations.add(isVariation);
				}
			}

			int usedMeasures = sec.getMeasures();

			if (riskyVariations.get(0)) {
				System.out.println("Risky Variation: Skip N-1 Chord!");

				if (sec.getType().equals("CLIMAX") && modTrans == 0) {
					List<String> allCurrentChordsAsBasic = MidiUtils
							.getBasicChordStringsFromRoots(generatedRootProgression);
					String baseChordLast = allCurrentChordsAsBasic
							.get(allCurrentChordsAsBasic.size() - 1);
					String baseChordFirst = allCurrentChordsAsBasic.get(0);
					transToSet = 0;
					Pair<String, String> test = Pair.of(baseChordFirst, baseChordLast);
					for (Integer trans : MidiUtils.modulationMap.keySet()) {
						boolean hasValue = MidiUtils.modulationMap.get(trans).contains(test);
						if (hasValue) {
							//transToSet = (trans < -4) ? (trans + 12) : trans;
							//System.out.println("Trans up by: " + transToSet);
							break;
						}
					}
				}

				progressionDurations = altProgressionDurations;
				rootProgression = altRootProgression;
				chordProgression = altChordProgression;
			} else {
				if (riskyVariations.get(1)) {
					System.out.println("Risky Variation: Chord Swap!");
					rootProgression = melodyBasedRootProgression;
					chordProgression = melodyBasedChordProgression;
				} else {
					rootProgression = generatedRootProgression;
					chordProgression = actualProgression;
				}
				progressionDurations = actualDurations;
			}

			// copied into empty sections
			Note emptyMeasureNote = new Note(Integer.MIN_VALUE, measureLength);
			Phrase emptyPhrase = new Phrase();
			emptyPhrase.setStartTime(START_TIME_DELAY);
			emptyPhrase.add(emptyMeasureNote);
			CPhrase emptyCPhrase = new CPhrase();
			emptyCPhrase.setStartTime(START_TIME_DELAY);
			emptyCPhrase.addChord(new int[] { Integer.MIN_VALUE }, measureLength);


			rand.setSeed(arrSeed);
			variationGen.setSeed(arrSeed);
			if (!gc.getMelodyParts().isEmpty()) {
				List<Phrase> copiedPhrases = new ArrayList<>();
				Set<Integer> presences = sec.getPresence(0);
				for (int i = 0; i < gc.getMelodyParts().size(); i++) {
					MelodyPart mp = (MelodyPart) gc.getMelodyParts().get(i);
					boolean added = !mp.isMuted()
							&& ((overridden && presences.contains(mp.getOrder()))
									|| (!overridden && rand.nextInt(100) < sec.getMelodyChance()));

					if (added) {
						List<int[]> usedMelodyProg = chordProgression;
						List<int[]> usedRoots = rootProgression;

						// if n-1, do not also swap melody
						if (riskyVariations.get(2) && !riskyVariations.get(0)) {
							usedMelodyProg = melodyBasedChordProgression;
							usedRoots = melodyBasedRootProgression;
							System.out.println("Risky Variation: Melody Swap!");
						}
						List<Integer> variations = (overridden) ? sec.getVariation(0, i) : null;
						Phrase m = fillMelody(mp, usedMelodyProg, usedRoots, usedMeasures,
								notesSeedOffset, sec, variations);

						// DOUBLE melody with -12 trans, if there was a variation of +12 and it's a major part and it's the first (full) melody
						// risky variation - wacky melody transpose
						boolean laxCheck = notesSeedOffset == 0
								&& sec.getVariation(0, i).contains(Integer.valueOf(0));
						if (!riskyVariations.get(3)) {
							laxCheck &= (i == 0);
						}

						if (laxCheck) {
							Phrase m2 = m.copy();
							Mod.transpose(m2, -12);
							Part melPart = new Part();
							melPart.add(m2);
							melPart.add(m);
							if (riskyVariations.get(3)) {
								Mod.consolidate(melPart);
							} else {
								JMusicUtilsCustom.consolidate(melPart);
							}

							m = melPart.getPhrase(0);
						}
						copiedPhrases.add(m);
						if (!overridden)
							sec.setPresence(0, i);
					} else {
						copiedPhrases.add(emptyPhrase.copy());
					}
				}
				sec.setMelodies(copiedPhrases);

			}
			rand.setSeed(arrSeed + 10);
			variationGen.setSeed(arrSeed + 10);
			if (!gc.getBassPart().isMuted()) {
				Set<Integer> presences = sec.getPresence(1);
				boolean added = (overridden && presences.contains(gc.getBassPart().getOrder()))
						|| (!overridden && rand.nextInt(100) < sec.getBassChance());
				if (added) {
					List<Integer> variations = (overridden) ? sec.getVariation(1, 0) : null;
					CPhrase b = fillBassRoots(rootProgression, usedMeasures, sec, variations);
					if (variationGen.nextInt(100) < gc.getArrangementPartVariationChance()) {
						// TODO
					}

					if (gc.getBassPart().isDoubleOct()) {
						CPhrase b2 = b.copy();
						Mod.transpose(b2, 12);
						Mod.increaseDynamic((Phrase) b2.getPhraseList().get(0), -15);
						Part bassPart = new Part();
						bassPart.addCPhrase(b2);
						bassPart.addCPhrase(b);
						JMusicUtilsCustom.consolidate(bassPart);

						b = new CPhrase();
						b.setStartTime(START_TIME_DELAY);
						b.addPhrase(bassPart.getPhrase(0));
					}

					sec.setBass(b);
					if (!overridden)
						sec.setPresence(1, 0);
				} else {
					sec.setBass(emptyCPhrase.copy());
				}

			}

			if (!gc.getChordParts().isEmpty()) {
				List<CPhrase> copiedCPhrases = new ArrayList<>();
				Set<Integer> presences = sec.getPresence(2);
				boolean useChordSlash = false;
				for (int i = 0; i < gc.getChordParts().size(); i++) {
					ChordPart cp = (ChordPart) gc.getChordParts().get(i);
					rand.setSeed(arrSeed + 100 + cp.getOrder());
					variationGen.setSeed(arrSeed + 100 + cp.getOrder());
					boolean added = (overridden && presences.contains(cp.getOrder()))
							|| (!overridden && rand.nextInt(100) < sec.getChordChance());
					if (added && !cp.isMuted()) {
						if (i == 0) {
							useChordSlash = true;
						}
						List<Integer> variations = (overridden) ? sec.getVariation(2, i) : null;
						CPhrase c = fillChordsFromPart(cp, chordProgression, usedMeasures, sec,
								variations);
						if (variationGen.nextInt(100) < gc.getArrangementPartVariationChance()) {
							// TODO Mod.transpose(c, 12);
						}
						copiedCPhrases.add(c);
						if (!overridden)
							sec.setPresence(2, i);
					} else {
						copiedCPhrases.add(emptyCPhrase.copy());
					}
				}
				sec.setChords(copiedCPhrases);
				if (useChordSlash) {
					sec.setChordSlash(fillChordSlash(chordProgression, usedMeasures));
				} else {
					sec.setChordSlash(emptyCPhrase.copy());
				}

			}

			if (!gc.getArpParts().isEmpty()) {
				List<CPhrase> copiedCPhrases = new ArrayList<>();
				Set<Integer> presences = sec.getPresence(3);
				for (int i = 0; i < gc.getArpParts().size(); i++) {
					ArpPart ap = (ArpPart) gc.getArpParts().get(i);
					rand.setSeed(arrSeed + 200 + ap.getOrder());
					variationGen.setSeed(arrSeed + 200 + ap.getOrder());
					// if arp1 supports melody with same instrument, always introduce it in second half
					List<Integer> variations = (overridden) ? sec.getVariation(3, i) : null;
					boolean added = (overridden && presences.contains(ap.getOrder()))
							|| (!overridden && rand.nextInt(100) < sec.getArpChance() && i > 0
									&& !ap.isMuted());
					added |= (!overridden && i == 0
							&& ap.getInstrument() == gc.getMelodyParts().get(0).getInstrument()
							&& ((isPreview || counter > ((arr.getSections().size() - 1) / 2))
									&& !ap.isMuted()));

					if (added) {
						CPhrase a = fillArpFromPart(ap, chordProgression, usedMeasures, sec,
								variations);
						if (variationGen.nextInt(100) < gc.getArrangementPartVariationChance()) {
							// TODO Mod.transpose(a, 12);
						}
						copiedCPhrases.add(a);
						if (!overridden)
							sec.setPresence(3, i);
					} else {
						copiedCPhrases.add(emptyCPhrase.copy());
					}
				}
				sec.setArps(copiedCPhrases);
			}

			if (!gc.getDrumParts().isEmpty()) {
				List<Phrase> copiedPhrases = new ArrayList<>();
				Set<Integer> presences = sec.getPresence(4);
				for (int i = 0; i < gc.getDrumParts().size(); i++) {
					DrumPart dp = (DrumPart) gc.getDrumParts().get(i);
					rand.setSeed(arrSeed + 300 + dp.getOrder());
					variationGen.setSeed(arrSeed + 300 + dp.getOrder());
					boolean added = (overridden && presences.contains(dp.getOrder()))
							|| (!overridden && rand.nextInt(100) < sec.getDrumChance());
					if (added && !dp.isMuted()) {
						int sectionChanceModifier = 75 + (sec.getDrumChance() / 4);
						boolean sectionForcedDynamics = (sec.getType().contains("CLIMAX"))
								&& variationGen.nextInt(100) < gc
										.getArrangementPartVariationChance();
						List<Integer> variations = (overridden) ? sec.getVariation(4, i) : null;
						Phrase d = fillDrumsFromPart(dp, chordProgression, usedMeasures,
								sectionChanceModifier, sectionForcedDynamics, sec, variations);
						if (variationGen.nextInt(100) < gc.getArrangementPartVariationChance()) {
							// TODO Mod.accent(d, 0.25);
						}
						copiedPhrases.add(d);
						if (!overridden)
							sec.setPresence(4, i);
					} else {
						copiedPhrases.add(emptyPhrase.copy());
					}
				}
				sec.setDrums(copiedPhrases);
			}
			if (sec.getRiskyVariations() == null) {
				sec.setRiskyVariations(riskyVariations);
			}
			counter += sec.getMeasures();
		}
		System.out.println("Added phrases/cphrases to sections..");

		for (Section sec : arr.getSections()) {
			for (int i = 0; i < gc.getMelodyParts().size(); i++) {
				Phrase p = sec.getMelodies().get(i);
				p.setStartTime(p.getStartTime() + sec.getStartTime());
				p.setAppend(false);
				if (gc.getMelodyParts().get(0).isMuted()) {
					melodyParts.get(i).addPhrase(p);
				} else {
					melodyParts.get(0).addPhrase(p);
				}
			}

			if (!gc.getBassPart().isMuted()) {
				CPhrase bp = sec.getBass();
				bp.setStartTime(bp.getStartTime() + sec.getStartTime());
				bassRoots.addCPhrase(bp);
			}

			for (int i = 0; i < gc.getChordParts().size(); i++) {
				CPhrase cp = sec.getChords().get(i);
				cp.setStartTime(cp.getStartTime() + sec.getStartTime());
				chordParts.get(i).addCPhrase(cp);
			}

			for (int i = 0; i < gc.getArpParts().size(); i++) {
				CPhrase cp = sec.getArps().get(i);
				cp.setStartTime(cp.getStartTime() + sec.getStartTime());
				arpParts.get(i).addCPhrase(cp);
			}

			for (int i = 0; i < gc.getDrumParts().size(); i++) {
				Phrase p = sec.getDrums().get(i);
				p.setStartTime(p.getStartTime() + sec.getStartTime());
				if (COLLAPSE_DRUM_TRACKS) {
					p.setAppend(false);
					drumParts.get(0).addPhrase(p);
				} else {
					drumParts.get(i).addPhrase(p);
				}

			}
			if (gc.getChordParts().size() > 0) {
				CPhrase cscp = sec.getChordSlash();
				cscp.setStartTime(cscp.getStartTime() + sec.getStartTime());
				cscp.setAppend(false);
				chordParts.get(0).addCPhrase(cscp);
			}

		}
		System.out.println("Added sections to parts..");
		int trackCounter = 1;

		for (int i = 0; i < gc.getMelodyParts().size(); i++) {
			if (!gc.getMelodyParts().get(i).isMuted()) {
				score.add(melodyParts.get(i));
				InstPanel ip = VibeComposerGUI.getPanelByOrder(
						gc.getMelodyParts().get(i).getOrder(), VibeComposerGUI.melodyPanels);
				ip.setSequenceTrack(trackCounter++);
				//if (VibeComposerGUI.apSm)
			} else {
				if (i == 0) {
					COLLAPSE_MELODY_TRACKS = false;
				}
			}
			if (COLLAPSE_MELODY_TRACKS) {
				break;
			}
		}

		for (int i = 0; i < gc.getArpParts().size(); i++) {
			if (!gc.getArpParts().get(i).isMuted()) {
				score.add(arpParts.get(i));
				InstPanel ip = VibeComposerGUI.getPanelByOrder(gc.getArpParts().get(i).getOrder(),
						VibeComposerGUI.arpPanels);
				ip.setSequenceTrack(trackCounter++);
				//if (VibeComposerGUI.apSm)
			}
		}

		if (!gc.getBassPart().isMuted()) {
			score.add(bassRoots);
			VibeComposerGUI.bassPanel.setSequenceTrack(trackCounter++);
		}

		for (int i = 0; i < gc.getChordParts().size(); i++) {
			if (!gc.getChordParts().get(i).isMuted()) {
				score.add(chordParts.get(i));
				InstPanel ip = VibeComposerGUI.getPanelByOrder(gc.getChordParts().get(i).getOrder(),
						VibeComposerGUI.chordPanels);
				ip.setSequenceTrack(trackCounter++);
			}

		}
		if (gc.getScaleMode() != ScaleMode.IONIAN) {
			for (Part p : score.getPartArray()) {
				for (Phrase phr : p.getPhraseArray()) {
					MidiUtils.transposePhrase(phr, ScaleMode.IONIAN.noteAdjustScale,
							gc.getScaleMode().noteAdjustScale);
				}
			}
		}
		//int[] backTranspose = { 0, 2, 4, 5, 7, 9, 11, 12 };
		Mod.transpose(score, gc.getTranspose());

		// add drums after transposing transposable parts

		for (int i = 0; i < gc.getDrumParts().size(); i++) {
			score.add(drumParts.get(i));
			InstPanel ip = VibeComposerGUI.getPanelByOrder(gc.getDrumParts().get(i).getOrder(),
					VibeComposerGUI.drumPanels);
			ip.setSequenceTrack(trackCounter++);
			if (COLLAPSE_DRUM_TRACKS) {
				break;
			}
		}


		System.out.println("Added parts to score..");


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
		if (DISPLAY_SCORE) {
			List<Part> partsToRemove = new ArrayList<>();
			for (Object p : score.getPartList()) {
				Part part = (Part) p;
				if (part.getTitle().equalsIgnoreCase("MainDrums") && showScoreMode < 1) {
					partsToRemove.add(part);
					continue;
				} else if (!part.getTitle().equalsIgnoreCase("MainDrums") && showScoreMode == 1) {
					partsToRemove.add(part);
					continue;
				}
				List<Phrase> phrasesToRemove = new ArrayList<>();
				for (Object vec : part.getPhraseList()) {
					Phrase ph = (Phrase) vec;
					if (ph.getHighestPitch() < 0) {
						phrasesToRemove.add(ph);
					}

				}
				phrasesToRemove.forEach(e -> part.removePhrase(e));
			}
			partsToRemove.forEach(e -> score.removePart(e));
			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					pianoRoll(score);

				}

			});

		}

		gc.setActualArrangement(arr);
		System.out.println("********Viewing midi seed: " + mainGeneratorSeed + "************* ");
	}

	public static void pianoRoll(Score s) {
		if (showScores.size() > 2) {
			ShowScore scr = showScores.get(0);
			showScores.remove(0);
			scr.dispose();
		}
		int x = (windowLoc % 10 == 0) ? 50 : 0;
		int y = (windowLoc % 15 == 0) ? 50 : 0;
		ShowScore nextScr = new ShowScore(s, x, y);
		windowLoc += 5;
		if (windowLoc > 15) {
			windowLoc = 5;
		}
		showScores.add(nextScr);

	}

	private void fillAlternates(List<Double> altProgressionDurations,
			List<int[]> altChordProgression, List<int[]> altRootProgression) {
		// TODO: other variations on how to generate alternates?

		// 1: chord trick, max two measures
		// 60 30 4 1 -> 60 30 1 - , 60 30 4 1
		for (int i = 0; i < 1; i++) {
			altProgressionDurations.addAll(progressionDurations);
			altChordProgression.addAll(chordProgression);
			altRootProgression.addAll(rootProgression);
		}

		if (progressionDurations.size() < 3) {
			return;
		}

		double duration = progressionDurations.get(progressionDurations.size() - 1)
				+ progressionDurations.get(progressionDurations.size() - 2);
		altProgressionDurations.set(progressionDurations.size() - 2, duration);
		altProgressionDurations.remove(progressionDurations.size() - 1);

		altChordProgression.remove(progressionDurations.size() - 2);
		altRootProgression.remove(progressionDurations.size() - 2);


	}

	private void processUserMelody(Phrase userMelody) {
		if (!chordMelodyMap1.isEmpty()) {
			return;
		}

		int chordCounter = 0;
		double separatorValue = (gc.isDoubledDurations()) ? Durations.WHOLE_NOTE
				: Durations.HALF_NOTE;
		double chordSeparator = separatorValue;
		Vector<Note> noteList = userMelody.getNoteList();
		if (!chordMelodyMap1.containsKey(Integer.valueOf(0))) {
			chordMelodyMap1.put(Integer.valueOf(0), new ArrayList<>());
		}
		double rhythmCounter = 0;
		List<Double> progDurations = new ArrayList<>();
		progDurations.add(separatorValue);
		for (Note n : noteList) {
			System.out.println("Rhythm counter: " + rhythmCounter);
			if (rhythmCounter >= chordSeparator - 0.001) {
				System.out.println("NEXT CHORD!");
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
		System.out.println("Rhythm counter end: " + rhythmCounter);
		while (rhythmCounter >= chordSeparator + 0.001) {
			System.out.println("NEXT CHORD!");
			chordSeparator += separatorValue;
			chordCounter++;
			progDurations.add(separatorValue);
			if (!chordMelodyMap1.containsKey(Integer.valueOf(chordCounter))) {
				chordMelodyMap1.put(Integer.valueOf(chordCounter), new ArrayList<>());
			}
			chordMelodyMap1.get(Integer.valueOf(chordCounter))
					.add(noteList.get(noteList.size() - 1));
		}
		System.out.println("Processed melody, chords: " + (chordCounter + 1));
		List<String> chordStrings = makeMelodyPitchFrequencyMap(0, chordMelodyMap1.keySet().size(),
				1);
		if (userChords == null || userChords.isEmpty()) {
			System.out.println(StringUtils.join(chordStrings, ","));
			chordInts = chordStrings;

			chordProgression = melodyBasedChordProgression;
			rootProgression = melodyBasedRootProgression;
			progressionDurations = progDurations;
		}
	}

	protected Phrase fillMelody(MelodyPart mp, List<int[]> actualProgression,
			List<int[]> generatedRootProgression, int measures, int notesSeedOffset, Section sec,
			List<Integer> variations) {
		Phrase melodyPhrase = new Phrase();
		Vector<Note> skeletonNotes = null;
		if (userMelody != null) {
			skeletonNotes = userMelody.copy().getNoteList();
		} else {
			skeletonNotes = generateMelodySkeletonFromChords(mp, actualProgression,
					generatedRootProgression, measures, notesSeedOffset, sec, variations);
		}
		Vector<Note> fullMelody = convertMelodySkeletonToFullMelody(mp, sec, skeletonNotes,
				notesSeedOffset);
		if (mp.getOrder() == 1) {
			melodyNotePattern = patternFromNotes(fullMelody);
		}

		swingMelody(mp, fullMelody);
		melodyPhrase.addNoteList(fullMelody, true);


		Mod.transpose(melodyPhrase, mp.getTranspose() + modTrans);
		melodyPhrase.setStartTime(START_TIME_DELAY);
		return melodyPhrase;
	}


	protected CPhrase fillBassRoots(List<int[]> generatedRootProgression, int measures, Section sec,
			List<Integer> variations) {
		boolean genVars = variations == null;

		double[] durationPool = new double[] { Durations.SIXTEENTH_NOTE / 2.0,
				Durations.SIXTEENTH_NOTE, Durations.EIGHTH_NOTE, Durations.DOTTED_EIGHTH_NOTE,
				Durations.QUARTER_NOTE, Durations.SIXTEENTH_NOTE + Durations.QUARTER_NOTE,
				Durations.DOTTED_QUARTER_NOTE, Durations.HALF_NOTE };

		int[] durationWeights = new int[] { 5, 25, 45, 55, 75, 85, 95, 100 };

		int seed = gc.getBassPart().getPatternSeed();

		CPhrase cphraseBassRoot = new CPhrase();
		int minVel = gc.getBassPart().getVelocityMin() + (5 * sec.getBassChance()) / 10 - 50;
		minVel = (minVel < 0) ? 0 : minVel;
		int maxVel = gc.getBassPart().getVelocityMax() + (5 * sec.getBassChance()) / 10 - 50;
		maxVel = (maxVel < 1) ? 1 : maxVel;
		Random variationGenerator = new Random(seed + sec.getTypeMelodyOffset());
		Random rhythmPauseGenerator = new Random(seed + sec.getTypeMelodyOffset());
		Random noteVariationGenerator = new Random(seed + sec.getTypeMelodyOffset() + 2);
		boolean rhythmPauses = false;
		int numberOfVars = Section.variationDescriptions[1].length - 2;
		for (int i = 0; i < measures; i++) {
			int extraSeed = 0;
			for (int j = 0; j < generatedRootProgression.size(); j++) {
				if (genVars && (j == 0) && sec.getTypeMelodyOffset() > 0) {
					variations = fillVariations(sec, variationGenerator, variations, numberOfVars,
							1);
				}

				if ((variations != null) && (j == 0)) {
					for (Integer var : variations) {
						if (i == measures - 1) {
							System.out.println("Bass #1 variation: " + var);
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
				if (gc.getBassPart().isUseRhythm()) {
					int seedCopy = seed;
					seedCopy += extraSeed;
					if (gc.getBassPart().isAlternatingRhythm()) {
						seedCopy += (j % 2);
					}
					Rhythm bassRhythm = new Rhythm(seedCopy, progressionDurations.get(j),
							durationPool, durationWeights);
					int counter = 0;
					for (Double dur : bassRhythm.regenerateDurations()) {

						int randomNote = 0;
						// note variation for short notes, low chance, only after first
						if (counter > 0 && dur < Durations.EIGHTH_NOTE
								&& noteVariationGenerator.nextInt(100) < gc.getBassPart()
										.getNoteVariation()
								&& generatedRootProgression.get(j).length > 1) {
							randomNote = noteVariationGenerator
									.nextInt(generatedRootProgression.get(j).length - 1) + 1;
						}

						int pitch = (rhythmPauses && dur < Durations.EIGHTH_NOTE
								&& rhythmPauseGenerator.nextInt(100) < 33) ? Integer.MIN_VALUE
										: generatedRootProgression.get(j)[randomNote];


						cphraseBassRoot.addChord(new int[] { pitch }, dur,
								bassDynamics.nextInt(velSpace) + minVel);
						counter++;
					}
				} else {
					cphraseBassRoot.addChord(new int[] { generatedRootProgression.get(j)[0] },
							progressionDurations.get(j), bassDynamics.nextInt(velSpace) + minVel);
				}
			}
		}
		Mod.transpose(cphraseBassRoot, -24 + gc.getBassPart().getTranspose() + modTrans);
		cphraseBassRoot.setStartTime(START_TIME_DELAY);
		if (genVars && variations != null) {
			sec.setVariation(1, 0, variations);
		}
		return cphraseBassRoot;

	}

	protected CPhrase fillChordsFromPart(ChordPart cp, List<int[]> actualProgression, int measures,
			Section sec, List<Integer> variations) {
		boolean genVars = variations == null;

		int mainGeneratorSeed = (int) cp.getPatternSeed() + cp.getOrder();
		CPhrase cpr = new CPhrase();
		Random variationGenerator = new Random(
				cp.getPatternSeed() + cp.getOrder() + sec.getTypeSeedOffset());
		int numberOfVars = Section.variationDescriptions[2].length - 2;
		int stretch = cp.getChordNotesStretch();
		boolean maxStrum = false;

		for (int i = 0; i < measures; i++) {
			Random transitionGenerator = new Random(mainGeneratorSeed);
			int extraTranspose = 0;
			boolean ignoreChordSpanFill = false;
			boolean skipSecondNote = false;

			// fill chords
			for (int j = 0; j < actualProgression.size(); j++) {
				if (genVars && (j == 0)) {
					variations = fillVariations(sec, variationGenerator, variations, numberOfVars,
							2);
				}

				if ((variations != null) && (j == 0)) {
					for (Integer var : variations) {
						if (i == measures - 1) {
							System.out.println("Chord #" + cp.getOrder() + " variation: " + var);
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
							maxStrum = true;
							break;
						default:
							throw new IllegalArgumentException("Too much variation!");
						}
					}
				}

				Random velocityGenerator = new Random(mainGeneratorSeed + j);
				int velocity = velocityGenerator.nextInt(cp.getVelocityMax() - cp.getVelocityMin())
						+ cp.getVelocityMin();

				boolean transition = transitionGenerator.nextInt(100) < cp.getTransitionChance();
				int transChord = (transitionGenerator.nextInt(100) < cp.getTransitionChance())
						? (j + 1) % actualProgression.size()
						: j;

				// random = use generated split with potential to transition to 2nd chord early
				// otherwise = use pattern within single chord

				boolean silent = false;

				if (!ignoreChordSpanFill && (cp.getChordSpanFill() != ChordSpanFill.ALL)) {
					if ((cp.getChordSpanFill() == ChordSpanFill.EVEN) && (j % 2 != 0)) {
						silent = true;
					}
					if ((cp.getChordSpanFill() == ChordSpanFill.ODD) && (j % 2 == 0)) {
						silent = true;
					}
				}

				if (silent) {
					cpr.addChord(new int[] { Integer.MIN_VALUE }, progressionDurations.get(j));
					continue;
				}

				double shortenedTo = (gc.getChordGenSettings().isUseShortening()
						&& cp.getInstPool() == POOL.PLUCK) ? 0.2 : 1.0;

				int[] mainChordNotes = actualProgression.get(j);
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

				if (cp.getPattern() == RhythmPattern.FULL) {
					double splitTime = gc.getChordGenSettings().isUseSplit()
							? cp.getTransitionSplit()
							: DEFAULT_CHORD_SPLIT;

					double duration1 = progressionDurations.get(j) * splitTime / 1000.0;
					double duration2 = progressionDurations.get(j) - duration1;
					if (transition) {
						addShortenedChord(cpr,
								convertChordToLength(transposeChord(mainChordNotes, extraTranspose),
										cp.getChordNotesStretch(), cp.isStretchEnabled()),
								duration1, velocity, shortenedTo);
						addShortenedChord(cpr,
								convertChordToLength(
										transposeChord(transChordNotes, extraTranspose),
										cp.getChordNotesStretch(), cp.isStretchEnabled()),
								duration2, velocity, shortenedTo);
					} else {
						addShortenedChord(cpr,
								convertChordToLength(transposeChord(mainChordNotes, extraTranspose),
										cp.getChordNotesStretch(), cp.isStretchEnabled()),
								progressionDurations.get(j), velocity, shortenedTo);
					}

				} else {
					double duration = progressionDurations.get(j) / MAXIMUM_PATTERN_LENGTH;
					List<Integer> pattern = cp.getPattern()
							.getPatternByLength(MAXIMUM_PATTERN_LENGTH);
					Collections.rotate(pattern, cp.getPatternShift());
					for (int p = 0; p < pattern.size(); p++) {
						if (pattern.get(p) > 0) {
							addShortenedChord(cpr,
									convertChordToLength(
											transposeChord(mainChordNotes, extraTranspose),
											cp.getChordNotesStretch(), cp.isStretchEnabled()),
									duration, velocity, shortenedTo);
						} else {
							cpr.addChord(new int[] { Integer.MIN_VALUE }, duration, velocity);
						}
					}

				}
			}
		}

		// transpose
		int extraTranspose = gc.getChordGenSettings().isUseTranspose() ? cp.getTranspose() : 0;
		Mod.transpose(cpr, -12 + extraTranspose + modTrans);

		// delay
		double additionalDelay = 0;
		if (gc.getChordGenSettings().isUseDelay()) {
			additionalDelay = ((noteMultiplier * cp.getDelay()) / 1000.0);
		}
		cpr.setStartTime(START_TIME_DELAY + additionalDelay);

		// chord strum
		if (gc.getChordGenSettings().isUseStrum()) {
			if (maxStrum) {
				cpr.flam(SECOND_ARRAY_STRUM[SECOND_ARRAY_STRUM.length - 1]);
			} else {
				int index = -1;
				for (int i = 0; i < VibeComposerGUI.MILISECOND_ARRAY_STRUM.length; i++) {
					if (cp.getStrum() == VibeComposerGUI.MILISECOND_ARRAY_STRUM[i]) {
						index = i;
						break;
					}
				}
				if (index != -1) {
					cpr.flam(SECOND_ARRAY_STRUM[index] * noteMultiplier);
				} else {
					cpr.flam((noteMultiplier * (double) cp.getStrum()) / 1000.0);
					System.out.println("Chord strum CUSTOM! " + cp.getStrum());
				}
			}

		}
		if (genVars && variations != null) {
			sec.setVariation(2, getAbsoluteOrder(2, cp), variations);
		}
		return cpr;
	}

	protected CPhrase fillArpFromPart(ArpPart ap, List<int[]> actualProgression, int measures,
			Section sec, List<Integer> variations) {
		boolean genVars = variations == null;
		CPhrase arpCPhrase = new CPhrase();

		Map<String, List<Integer>> arpMap = generateArpMap(ap.getPatternSeed(),
				ap.equals(gc.getArpParts().get(0)), ap);

		List<Integer> arpPattern = arpMap.get(ARP_PATTERN_KEY);
		List<Integer> arpOctavePattern = arpMap.get(ARP_OCTAVE_KEY);
		List<Integer> arpPausesPattern = arpMap.get(ARP_PAUSES_KEY);

		List<Boolean> directions = null;

		int repeatedArpsPerChord = ap.getHitsPerPattern() * ap.getPatternRepeat();

		double longestChord = progressionDurations.stream().max((e1, e2) -> Double.compare(e1, e2))
				.get();
		Random variationGenerator = new Random(
				ap.getPatternSeed() + ap.getOrder() + sec.getTypeSeedOffset());
		int numberOfVars = Section.variationDescriptions[3].length - 2;
		for (int i = 0; i < measures; i++) {
			int chordSpanPart = 0;
			int extraTranspose = 0;
			boolean ignoreChordSpanFill = false;
			boolean forceRandomOct = false;
			boolean fillLastBeat = false;

			Random velocityGenerator = new Random(ap.getPatternSeed());
			Random exceptionGenerator = new Random(ap.getPatternSeed() + 1);
			for (int j = 0; j < actualProgression.size(); j++) {
				if (genVars && (j == 0)) {
					variations = fillVariations(sec, variationGenerator, variations, numberOfVars,
							3);
				}

				if ((variations != null) && (j == 0)) {
					for (Integer var : variations) {
						if (i == measures - 1) {
							System.out.println("Arp #" + ap.getOrder() + " variation: " + var);
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

				double chordDurationArp = longestChord / ((double) repeatedArpsPerChord);
				int[] chord = convertChordToLength(actualProgression.get(j),
						ap.getChordNotesStretch(), ap.isStretchEnabled());
				if (directions != null) {
					ArpPattern pat = (directions.get(j)) ? ArpPattern.UP : ArpPattern.DOWN;
					arpPattern = pat.getPatternByLength(ap.getHitsPerPattern(), chord.length,
							ap.getPatternRepeat());
					arpPattern = MidiUtils.intersperse(0, ap.getChordSpan() - 1, arpPattern);
				} else {
					if (ap.getArpPattern() != ArpPattern.RANDOM) {
						arpPattern = ap.getArpPattern().getPatternByLength(ap.getHitsPerPattern(),
								chord.length, ap.getPatternRepeat());
						arpPattern = MidiUtils.intersperse(0, ap.getChordSpan() - 1, arpPattern);
					}
				}

				double durationNow = 0;
				int swingPercentAmount = (repeatedArpsPerChord == 4 || repeatedArpsPerChord == 8)
						? gc.getMaxArpSwing()
						: 50;

				// reset every 2
				if (j % 2 == 0) {
					exceptionGenerator.setSeed(ap.getPatternSeed() + 1);
				}
				for (int p = 0; p < repeatedArpsPerChord; p++) {

					int velocity = velocityGenerator.nextInt(
							ap.getVelocityMax() - ap.getVelocityMin()) + ap.getVelocityMin();

					Integer patternNum = partOfList(chordSpanPart, ap.getChordSpan(), arpPattern)
							.get(p);

					int octaveAdjustGenerated = partOfList(chordSpanPart, ap.getChordSpan(),
							arpOctavePattern).get(p);
					int octaveAdjustmentFromPattern = (patternNum < 2) ? -12
							: ((patternNum < 6) ? 0 : 12);

					int pitch = chord[patternNum % chord.length];
					if (gc.isUseOctaveAdjustments() || forceRandomOct) {
						pitch += octaveAdjustmentFromPattern + octaveAdjustGenerated;
					}

					pitch += extraTranspose;
					if (!fillLastBeat || j < actualProgression.size() - 1) {
						if (partOfList(chordSpanPart, ap.getChordSpan(), arpPausesPattern)
								.get(p) == 0) {
							pitch = Integer.MIN_VALUE;
						}
						if (!ignoreChordSpanFill && (ap.getChordSpanFill() != ChordSpanFill.ALL)) {
							if ((ap.getChordSpanFill() == ChordSpanFill.EVEN) && (j % 2 != 0)) {
								pitch = Integer.MIN_VALUE;
							}
							if ((ap.getChordSpanFill() == ChordSpanFill.ODD) && (j % 2 == 0)) {
								pitch = Integer.MIN_VALUE;
							}
						}
					}


					double swingDuration = chordDurationArp
							* (swingPercentAmount / ((double) 50.0));
					swingPercentAmount = 100 - swingPercentAmount;

					if (durationNow + swingDuration > progressionDurations.get(j)) {
						arpCPhrase.addChord(new int[] { pitch },
								progressionDurations.get(j) - durationNow, velocity);
						break;
					} else {
						if (exceptionGenerator.nextInt(100) < ap.getExceptionChance()) {
							double splitDuration = swingDuration / 2;
							arpCPhrase.addChord(new int[] { pitch }, splitDuration, velocity);
							arpCPhrase.addChord(new int[] { pitch }, splitDuration, velocity - 15);
						} else {
							arpCPhrase.addChord(new int[] { pitch }, swingDuration, velocity);
						}
					}
					durationNow += swingDuration;
				}
				chordSpanPart++;
				if (chordSpanPart >= ap.getChordSpan()) {
					chordSpanPart = 0;
				}
			}
		}
		int extraTranspose = ARP_SETTINGS.isUseTranspose() ? ap.getTranspose() : 0;
		Mod.transpose(arpCPhrase, -24 + extraTranspose + modTrans);

		double additionalDelay = 0;
		/*if (ARP_SETTINGS.isUseDelay()) {
			additionalDelay = (gc.getArpParts().get(i).getDelay() / 1000.0);
		}*/
		arpCPhrase.setStartTime(START_TIME_DELAY + additionalDelay);
		if (genVars && variations != null) {
			sec.setVariation(3, getAbsoluteOrder(3, ap), variations);
		}
		return arpCPhrase;
	}


	protected Phrase fillDrumsFromPart(DrumPart dp, List<int[]> actualProgression, int measures,
			int sectionChanceModifier, boolean sectionForcedDynamics, Section sec,
			List<Integer> variations) {
		boolean genVars = variations == null;
		Phrase drumPhrase = new Phrase();

		sectionForcedDynamics &= (dp.getInstrument() < 38 && dp.getInstrument() > 40);

		int chordsCount = actualProgression.size();

		List<Integer> drumPattern = generateDrumPatternFromPart(dp);

		if (!dp.isVelocityPattern() && drumPattern.indexOf(dp.getInstrument()) == -1) {
			//drumPhrase.addNote(new Note(Integer.MIN_VALUE, patternDurationTotal, 100));
			drumPhrase.setStartTime(START_TIME_DELAY + ((noteMultiplier * dp.getDelay()) / 1000.0));
			return drumPhrase;
		}

		List<Integer> drumVelocityPattern = generateDrumVelocityPatternFromPart(dp);
		Random variationGenerator = new Random(
				dp.getPatternSeed() + dp.getOrder() + sec.getTypeSeedOffset());
		int numberOfVars = Section.variationDescriptions[4].length - 2;
		// bar iter
		int hits = dp.getHitsPerPattern();
		int swingPercentAmount = (hits == 2 || hits == 4 || hits % 8 == 0) ? dp.getSwingPercent()
				: 50;

		String swangPercentAmount = (swingPercentAmount < 100) ? "0." + swingPercentAmount : "1.0";
		double swungPercentAmount = Double.valueOf(swangPercentAmount);

		for (int o = 0; o < measures; o++) {
			// exceptions are generated the same for each bar, but differently for each pattern within bar (if there is more than 1)
			Random exceptionGenerator = new Random(dp.getPatternSeed() + dp.getOrder());
			int chordSpan = dp.getChordSpan();
			int oneChordPatternSize = drumPattern.size() / chordSpan;
			boolean ignoreChordSpanFill = false;
			int extraExceptionChance = 0;

			// chord iter
			for (int j = 0; j < chordsCount; j += chordSpan) {

				if (genVars && ((j == 0) || (j == chordInts.size()))) {
					variations = fillVariations(sec, variationGenerator, variations, numberOfVars,
							4);
				}

				if ((variations != null) && (j == 0)) {
					for (Integer var : variations) {
						if (o == measures - 1) {
							System.out.println("Drum #" + dp.getOrder() + " variation: " + var);
						}

						switch (var) {
						case 0:
							ignoreChordSpanFill = true;
							break;
						case 1:
							extraExceptionChance = (dp.getInstrument() < 38
									&& dp.getInstrument() > 40) ? dp.getExceptionChance() + 10
											: dp.getExceptionChance();
							break;
						default:
							throw new IllegalArgumentException("Too much variation!");
						}
					}
				}

				double patternDurationTotal = 0.0;
				for (int k = 0; k < chordSpan; k++) {
					patternDurationTotal += (progressionDurations.size() > j + k)
							? progressionDurations.get(j + k)
							: 0.0;
				}

				double drumDuration = patternDurationTotal / hits;

				double doubleDrum = drumDuration * 2;
				double swing1 = doubleDrum * swungPercentAmount;
				double swing2 = doubleDrum - swing1;

				boolean swung = false;
				double swingDuration = 0;
				for (int k = 0; k < drumPattern.size(); k++) {
					int drum = drumPattern.get(k);
					int velocity = drumVelocityPattern.get(k);
					int pitch = (drum >= 0) ? drum : Integer.MIN_VALUE;
					if (drum < 0 && (dp.isVelocityPattern() || (o > 0 && sectionForcedDynamics))) {
						velocity = (velocity * 5) / 10;
						pitch = dp.getInstrument();
					}

					velocity = (velocity * sectionChanceModifier / 100);
					boolean isEven = ((j + (k / oneChordPatternSize)) % 2 == 0);
					if (!ignoreChordSpanFill && (dp.getChordSpanFill() != ChordSpanFill.ALL)) {
						if ((dp.getChordSpanFill() == ChordSpanFill.EVEN) && !isEven) {
							pitch = Integer.MIN_VALUE;
						}
						if ((dp.getChordSpanFill() == ChordSpanFill.ODD) && isEven) {
							pitch = Integer.MIN_VALUE;
						}
					}

					if (!swung) {
						swingDuration = swing1;
						swung = true;
					} else {
						swingDuration = swing2;
						swung = false;
					}

					if (pitch != Integer.MIN_VALUE && gc.isDrumCustomMapping()) {
						pitch = mapDrumPitchByCustomMapping(pitch);
					}

					boolean exception = exceptionGenerator
							.nextInt(100) < (dp.getExceptionChance() + extraExceptionChance);
					if (exception) {
						int secondVelocity = (velocity * 8) / 10;
						Note n1 = new Note(pitch, swingDuration / 2, velocity);
						Note n2 = new Note(pitch, swingDuration / 2, secondVelocity);
						n1.setDuration(0.5 * n1.getRhythmValue());
						n2.setDuration(0.5 * n2.getRhythmValue());
						drumPhrase.addNote(n1);
						drumPhrase.addNote(n2);
					} else {
						Note n1 = new Note(pitch, swingDuration, velocity);
						n1.setDuration(0.5 * n1.getRhythmValue());
						drumPhrase.addNote(n1);
					}

				}
			}
		}
		if (genVars && variations != null) {
			sec.setVariation(4, getAbsoluteOrder(4, dp), variations);
		}

		drumPhrase.setStartTime(START_TIME_DELAY + ((noteMultiplier * dp.getDelay()) / 1000.0));
		return drumPhrase;

	}

	private List<Integer> fillVariations(Section sec, Random varGenerator, List<Integer> variations,
			int numVars, int part) {
		int failsafeCounter = 0;

		while (varGenerator.nextInt(100) < gc.getArrangementPartVariationChance()
				&& (variations == null || variations.size() < numVars)) {
			// pick one variation
			int variationInt = varGenerator.nextInt(numVars);
			while (VariationPopup.bannedInstVariations.get(part).contains(variationInt + 2)) {
				failsafeCounter++;
				if (failsafeCounter > numVars) {
					break;
				}
				variationInt = varGenerator.nextInt(numVars);
			}
			if (failsafeCounter > numVars) {
				break;
			}
			if (variations == null) {
				variations = new ArrayList<>();
			}

			if (!variations.contains(variationInt)) {
				variations.add(variationInt);
			}
		}
		return variations;
	}

	private int mapDrumPitchByCustomMapping(int pitch) {
		String customMapping = gc.getDrumCustomMappingNumbers();
		String[] customMappingNumberStrings = customMapping.split(",");
		List<Integer> defaultMappingNumbers = MidiUtils.getInstNumbers(MidiUtils.DRUM_INST_NAMES);
		List<Integer> customMappingNumbers = Arrays.asList(customMappingNumberStrings).stream()
				.map(e -> Integer.valueOf(e.trim())).collect(Collectors.toList());
		int defaultIndex = defaultMappingNumbers.indexOf(pitch);
		if (defaultIndex < 0 || (defaultMappingNumbers.size() != customMappingNumbers.size())) {
			return pitch;
		}

		return customMappingNumbers.get(defaultIndex);
	}

	private int getAbsoluteOrder(int partNum, InstPart part) {
		List<? extends InstPanel> panels = VibeComposerGUI.getInstList(partNum);
		for (int i = 0; i < panels.size(); i++) {
			if (panels.get(i).getPanelOrder() == part.getOrder()) {
				return i;
			}
		}
		throw new IllegalArgumentException("Absolute order not found!");
	}

	protected CPhrase fillChordSlash(List<int[]> actualProgression, int measures) {
		CPhrase chordSlashCPhrase = new CPhrase();
		Random chordSlashGenerator = new Random(gc.getRandomSeed() + 2);
		for (int i = 0; i < measures; i++) {
			// fill slash chord slashes
			for (int j = 0; j < actualProgression.size(); j++) {
				// pick random chord, take first/root pitch
				boolean isChordSlash = chordSlashGenerator.nextInt(100) < gc.getChordSlashChance();
				String slashChord = MidiUtils.MAJOR_CHORDS.get(chordSlashGenerator.nextInt(6));
				int[] mappedChord = mappedChord(slashChord);
				if (isChordSlash) {
					chordSlashCPhrase.addChord(new int[] { mappedChord[0] },
							progressionDurations.get(j));
				} else {
					chordSlashCPhrase.addChord(new int[] { Integer.MIN_VALUE },
							progressionDurations.get(j));
				}
			}
		}
		Mod.transpose(chordSlashCPhrase, -24);
		chordSlashCPhrase.setStartTime(START_TIME_DELAY);
		return chordSlashCPhrase;


	}

	private <T> List<T> partOfList(int part, int partCount, List<T> list) {
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
			Note n = generateNote(mp, chord, true, MELODY_SCALE, null, melodyGenerator,
					Durations.HALF_NOTE);
			copied[0] = new Note(n.getPitch(), originals[0].getRhythmValue(),
					originals[0].getDynamic());
		}
		return copied;
	}

	private Map<String, List<Integer>> generateArpMap(int mainGeneratorSeed, boolean needToReport,
			ArpPart ap) {
		Random uiGenerator2arpPattern = new Random(mainGeneratorSeed + 1);
		Random uiGenerator3arpOctave = new Random(mainGeneratorSeed + 2);
		Random uiGenerator4arpPauses = new Random(mainGeneratorSeed + 3);

		int[] arpPatternArray = IntStream.range(0, ap.getHitsPerPattern()).toArray();
		int[] arpOctaveArray = IntStream.iterate(0, e -> (e + 12) % 24)
				.limit(ap.getHitsPerPattern() * 2).toArray();
		List<Integer> arpPattern = Arrays.stream(arpPatternArray).boxed()
				.collect(Collectors.toList());
		if (ap.isRepeatableNotes()) {
			arpPattern.addAll(arpPattern);
		}
		List<Integer> arpPausesPattern = new ArrayList<>();

		if (ap.getPattern() == RhythmPattern.FULL) {
			for (int i = 0; i < ap.getHitsPerPattern(); i++) {
				if (uiGenerator4arpPauses.nextInt(100) < ap.getPauseChance()) {
					arpPausesPattern.add(0);
				} else {
					arpPausesPattern.add(1);
				}
			}
		} else {
			arpPausesPattern.addAll(ap.getPattern().getPatternByLength(ap.getHitsPerPattern()));

		}
		Collections.rotate(arpPausesPattern, ap.getPatternShift());
		List<Integer> arpOctavePattern = Arrays.stream(arpOctaveArray).boxed()
				.collect(Collectors.toList());

		// TODO: note pattern, different from rhythm pattern
		//if (ap.getPattern() == RhythmPattern.RANDOM) {
		Collections.shuffle(arpPattern, uiGenerator2arpPattern);
		Collections.shuffle(arpOctavePattern, uiGenerator3arpOctave);
		//}
		// always generate ap.getHitsPerPattern(), 
		// cut off however many are needed (support for seed randoms)
		arpPattern = arpPattern.subList(0, ap.getHitsPerPattern());
		arpOctavePattern = arpOctavePattern.subList(0, ap.getHitsPerPattern());
		arpPausesPattern = arpPausesPattern.subList(0, ap.getHitsPerPattern());

		if (needToReport) {
			//System.out.println("Arp count: " + ap.getHitsPerPattern());
			//System.out.println("Arp pattern: " + arpPattern.toString());
			//System.out.println("Arp octaves: " + arpOctavePattern.toString());
		}
		//System.out.println("Arp pauses : " + arpPausesPattern.toString());

		if (ap.getChordSpan() > 1) {
			arpPattern = MidiUtils.intersperse(0, ap.getChordSpan() - 1, arpPattern);
			arpOctavePattern = MidiUtils.intersperse(0, ap.getChordSpan() - 1, arpOctavePattern);
			arpPausesPattern = MidiUtils.intersperse(0, ap.getChordSpan() - 1, arpPausesPattern);
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
		List<Integer> premadePattern = (dp.getPattern() != RhythmPattern.CUSTOM)
				? dp.getPattern().getPatternByLength(dp.getHitsPerPattern())
				: dp.getCustomPattern();
		if (melodyNotePattern != null && dp.isUseMelodyNotePattern()) {
			//System.out.println("Setting note pattern!");
			dp.setHitsPerPattern(melodyNotePattern.size());
			premadePattern = melodyNotePattern;
			dp.setPatternShift(0);
			dp.setVelocityPattern(false);
			dp.setChordSpan(chordInts.size());
		}

		if (dp.getPattern() == RhythmPattern.CUSTOM) {
			//System.out.println(StringUtils.join(premadePattern, ","));
			List<Integer> premadeCopy = new ArrayList<>(premadePattern);
			Collections.rotate(premadeCopy, dp.getPatternShift());
			premadePattern = premadeCopy;
		}

		List<Integer> drumPattern = new ArrayList<>();
		for (int j = 0; j < dp.getHitsPerPattern(); j++) {
			// if random pause or not present in pattern: pause
			if (uiGenerator1drumPattern.nextInt(100) < dp.getPauseChance()
					|| !premadePattern.get(j).equals(1)) {
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
		if (dp.getPattern() != RhythmPattern.CUSTOM) {
			Collections.rotate(drumPattern, dp.getPatternShift());
		}

		/*System.out
				.println("Drum pattern for " + dp.getInstrument() + " : " + drumPattern.toString());*/
		return drumPattern;
	}

	private List<Integer> generateDrumVelocityPatternFromPart(DrumPart dp) {
		Random uiGenerator1drumVelocityPattern = new Random(dp.getPatternSeed() + dp.getOrder());
		List<Integer> drumVelocityPattern = new ArrayList<>();
		for (int j = 0; j < dp.getHitsPerPattern(); j++) {
			int velocityRange = dp.getVelocityMax() - dp.getVelocityMin();

			int velocity = uiGenerator1drumVelocityPattern.nextInt(velocityRange)
					+ dp.getVelocityMin();

			drumVelocityPattern.add(velocity);
		}
		/*System.out.println("Drum velocity pattern for " + dp.getInstrument() + " : "
				+ drumVelocityPattern.toString());*/
		return drumVelocityPattern;
	}
}
