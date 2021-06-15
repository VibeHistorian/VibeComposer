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
import java.util.Random;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.SwingUtilities;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.MidiUtils.POOL;
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

import jm.JMC;
import jm.constants.Durations;
import jm.gui.show.ShowScore;
import jm.music.data.CPhrase;
import jm.music.data.Note;
import jm.music.data.Part;
import jm.music.data.Phrase;
import jm.music.data.Score;
import jm.music.tools.Mod;
import jm.util.Write;

public class MidiGenerator implements JMC {

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
	private static final double swingUnitOfTime = Durations.SIXTEENTH_NOTE;
	private static final int OPENHAT_CHANCE = 15;
	private static final int maxAllowedScaleNotes = 7;
	private static final double START_TIME_DELAY = 0.5;
	private static final double DEFAULT_CHORD_SPLIT = 625;
	private static final String ARP_PATTERN_KEY = "ARP_PATTERN";
	private static final String ARP_OCTAVE_KEY = "ARP_OCTAVE";
	private static final String ARP_PAUSES_KEY = "ARP_PAUSES";

	// visibles/settables
	public static DrumGenSettings DRUM_SETTINGS = new DrumGenSettings();
	public static ArpGenSettings ARP_SETTINGS = new ArpGenSettings();

	public static List<Long> userChords = new ArrayList<>();
	public static List<Double> userChordsDurations = new ArrayList<>();
	public static List<Long> chordInts = new ArrayList<>();

	public static long FIRST_CHORD = 0;
	public static long LAST_CHORD = 0;

	public static boolean DISPLAY_SCORE = false;
	public static int showScoreMode = 0;

	public static boolean COLLAPSE_DRUM_TRACKS = true;

	// for internal use only
	private double[] MELODY_DUR_ARRAY = { Durations.QUARTER_NOTE, Durations.DOTTED_EIGHTH_NOTE,
			Durations.EIGHTH_NOTE, Durations.SIXTEENTH_NOTE };
	private double[] MELODY_DUR_CHANCE = { 0.3, 0.6, 1.0, 1.0 };

	private double[] CHORD_DUR_ARRAY = { Durations.WHOLE_NOTE, Durations.DOTTED_HALF_NOTE,
			Durations.HALF_NOTE, Durations.QUARTER_NOTE };
	private double[] CHORD_DUR_CHANCE = { 0.0, 0.20, 0.80, 1.0 };

	private List<Integer> MELODY_SCALE = MidiUtils.cIonianScale4;
	private List<Double> progressionDurations = new ArrayList<>();
	private List<int[]> chordProgression = new ArrayList<>();
	private List<int[]> rootProgression = new ArrayList<>();

	private Map<Integer, List<Note>> chordMelodyMap1 = new HashMap<>();
	private List<int[]> melodyBasedChordProgression = new ArrayList<>();
	private List<int[]> melodyBasedRootProgression = new ArrayList<>();

	private int samePitchCount = 0;
	private int previousPitch = 0;
	private boolean measureVariationOverride = false;

	public MidiGenerator(GUIConfig gc) {
		MidiGenerator.gc = gc;
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

	private Vector<Note> generateMelodySkeletonFromChords(List<int[]> chords, List<int[]> roots,
			int measures, int notesSeedOffset) {

		boolean fillChordMelodyMap = false;
		if (chordMelodyMap1.isEmpty() && notesSeedOffset == 0
				&& (roots.size() == chordInts.size())) {
			fillChordMelodyMap = true;
		}

		int MAX_JUMP_SKELETON_CHORD = gc.getMaxNoteJump();
		int SAME_RHYTHM_CHANCE = gc.getMelodySameRhythmChance();
		int ALTERNATE_RHYTHM_CHANCE = gc.getMelodyAlternateRhythmChance();
		int EXCEPTION_CHANCE = gc.getMelodyExceptionChance();

		int seed = gc.getMelodyPart().getPatternSeed();

		Vector<Note> noteList = new Vector<>();
		Random generator = new Random(seed + notesSeedOffset);
		Random velocityGenerator = new Random(seed + 1);
		Random exceptionGenerator = new Random(seed + 2);
		Random sameRhythmGenerator = new Random(seed + 3);
		Random alternateRhythmGenerator = new Random(seed + 4);
		Random variationGenerator = new Random(seed + 5);

		double[] melodySkeletonDurations = { Durations.SIXTEENTH_NOTE, Durations.EIGHTH_NOTE,
				Durations.DOTTED_EIGHTH_NOTE, Durations.QUARTER_NOTE };

		int weightIncreaser = gc.getMelodyQuickness() / 4;
		int weightReducer = 25 - weightIncreaser / 2;
		int[] melodySkeletonDurationWeights = { 0 + weightIncreaser, 50 - weightReducer,
				85 - weightReducer, 100 };

		List<int[]> usedChords = null;
		if (gc.isMelodyBasicChordsOnly()) {
			List<int[]> basicChordsUnsquished = MidiUtils.getBasicChordsFromRoots(roots);

			usedChords = MidiUtils.squishChordProgression(basicChordsUnsquished,
					gc.isSpiceFlattenBigChords(), gc.getRandomSeed(),
					gc.getChordGenSettings().getFlattenVoicingChance());
		} else {
			usedChords = chords;
		}

		List<int[]> stretchedChords = usedChords.stream()
				.map(e -> MidiUtils.convertChordToLength(e, 4, true)).collect(Collectors.toList());
		List<Boolean> directions = generateMelodyDirectionsFromChordProgression(stretchedChords,
				true);
		boolean alternateRhythm = alternateRhythmGenerator.nextInt(100) < ALTERNATE_RHYTHM_CHANCE;
		//System.out.println("Alt: " + alternateRhythm);
		for (int o = 0; o < measures; o++) {
			int previousNotePitch = 0;
			int extraTranspose = 0;

			for (int i = 0; i < stretchedChords.size(); i++) {
				// either after first measure, or after first half of combined chord prog
				if ((i == 0 && o > 0) || (i == chordInts.size())) {
					if (variationGenerator.nextInt(100) < gc.getArrangementPartVariationChance()) {
						// pick one variation
						int numberOfVars = 2;
						int variationInt = variationGenerator.nextInt(numberOfVars);
						System.out.println("Melody variation: " + variationInt);
						switch (variationInt) {
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
					exceptionGenerator.setSeed(seed + 2);
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
					Note n = new Note(pitch + extraTranspose, swingDuration,
							velocityGenerator
									.nextInt(1 + gc.getMelodyPart().getVelocityMax()
											- gc.getMelodyPart().getVelocityMin())
									+ gc.getMelodyPart().getVelocityMin());
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
			makeMelodyPitchFrequencyMap();
		}
		return noteList;
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

	private Vector<Note> convertMelodySkeletonToFullMelody(Vector<Note> skeleton) {
		Random splitGenerator = new Random(gc.getMelodyPart().getPatternSeed() + 4);
		Random pauseGenerator = new Random(gc.getMelodyPart().getPatternSeed() + 5);
		Random variationGenerator = new Random(gc.getMelodyPart().getPatternSeed() + 6);
		int splitChance = gc.getMelodySplitChance() * gc.getMelodyQuickness() / 100;
		Vector<Note> fullMelody = new Vector<>();
		int chordCounter = 0;
		double durCounter = 0.0;
		double currentChordDur = progressionDurations.get(0);
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
				splitGenerator.setSeed(gc.getMelodyPart().getPatternSeed() + 4);
				pauseGenerator.setSeed(gc.getMelodyPart().getPatternSeed() + 5);
			}
			Note emptyNote = new Note(Integer.MIN_VALUE, adjDur);
			Note emptyNoteHalf = new Note(Integer.MIN_VALUE, adjDur / 2.0);
			int p = pauseGenerator.nextInt(100);
			boolean pause1 = p < gc.getMelodyPart().getPauseChance();
			boolean pause2 = p < (gc.getMelodyPart().getPauseChance() / 2);


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
				if (pause2) {
					fullMelody.add(n1split1);
					fullMelody.add(emptyNoteHalf);
				} else if (pause1) {
					fullMelody.add(emptyNoteHalf);
					fullMelody.add(n1split2);
				} else {
					fullMelody.add(n1split1);
					fullMelody.add(n1split2);
				}
			} else {
				if (pause1) {
					fullMelody.add(emptyNote);
				} else {
					fullMelody.add(skeleton.get(i));
				}

			}
			durCounter += adjDur;
		}
		return fullMelody;
	}

	private void swingMelody(Vector<Note> fullMelody) {
		double currentChordDur = progressionDurations.get(0);
		int chordCounter = 0;

		int swingPercentAmount = gc.getMelodyPart().getSwingPercent();
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

	private void makeMelodyPitchFrequencyMap() {
		// only affect middle 2 chords 
		List<int[]> alternateChordProg = new ArrayList<>();
		alternateChordProg
				.add(Arrays.copyOf(chordProgression.get(0), chordProgression.get(0).length));
		melodyBasedRootProgression.add(Arrays.copyOf(rootProgression.get(0), 1));
		for (int i = 1; i < chordMelodyMap1.keySet().size() - 1; i++) {

			List<Integer> chordFreqs = new ArrayList<>();
			for (Note n : chordMelodyMap1.get(i)) {
				double dur = n.getRhythmValue();
				double durCounter = 0.0;
				while (durCounter < dur) {
					chordFreqs.add(n.getPitch() % 12);
					durCounter += Durations.SIXTEENTH_NOTE;
				}
			}

			Map<Integer, Long> freqCounts = chordFreqs.stream()
					.collect(Collectors.groupingBy(e -> e, Collectors.counting()));

			Map<Integer, Long> top3 = freqCounts.entrySet().stream()
					.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(3)
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
							(e1, e2) -> e1, LinkedHashMap::new));

			//top3.entrySet().stream().forEach(System.out::println);
			Long chordLong = MidiUtils.applyChordFreqMap(top3.keySet());
			System.out.println("Alternate chord #" + i + ": " + chordLong);
			int[] chordLongMapped = MidiUtils.chordsMap.get(chordLong);
			melodyBasedRootProgression.add(Arrays.copyOf(chordLongMapped, 1));
			alternateChordProg.add(chordLongMapped);
		}
		alternateChordProg
				.add(Arrays.copyOf(chordProgression.get(chordMelodyMap1.keySet().size() - 1),
						chordProgression.get(chordMelodyMap1.keySet().size() - 1).length));
		melodyBasedRootProgression
				.add(Arrays.copyOf(rootProgression.get(rootProgression.size() - 1), 1));
		melodyBasedChordProgression = MidiUtils.squishChordProgression(alternateChordProg,
				gc.isSpiceFlattenBigChords(), gc.getRandomSeed(),
				gc.getChordGenSettings().getFlattenVoicingChance());
	}

	private Note generateNote(int[] chord, boolean isAscDirection, List<Integer> chordScale,
			Note previousNote, Random generator, double durationLeft) {
		// int randPitch = generator.nextInt(8);
		int velMin = gc.getMelodyPart().getVelocityMin();
		int velSpace = gc.getMelodyPart().getVelocityMax() - velMin;

		int direction = (isAscDirection) ? 1 : -1;
		double dur = MidiUtils.pickDurationWeightedRandom(generator, durationLeft, MELODY_DUR_ARRAY,
				MELODY_DUR_CHANCE, Durations.SIXTEENTH_NOTE);
		boolean isPause = (generator.nextInt(100) < gc.getMelodyPart().getPauseChance());
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
		generatedPitch = MidiUtils.maX(generatedPitch, maxAllowedScaleNotes);


		if (generatedPitch == previousPitch && !isPause) {
			samePitchCount++;
		} else {
			samePitchCount = 0;
		}
		//if 3 or more times same note, swap direction for this case
		if (samePitchCount >= 2) {
			//System.out.println("UNSAMING NOTE!: " + previousPitch + ", BY: " + (-direction * change));
			generatedPitch = MidiUtils.maX(previousPitch - direction * change,
					maxAllowedScaleNotes);
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
			for (Long chordInt : userChords) {
				userProgression.add(MidiUtils.mappedChord(chordInt));
			}
			System.out.println(
					"Using user's custom progression: " + StringUtils.join(userChords, ","));
			return userProgression;
		}

		Random generator = new Random();
		generator.setSeed(mainGeneratorSeed);

		Random durationGenerator = new Random();
		durationGenerator.setSeed(mainGeneratorSeed);

		Map<Long, List<Long>> r = MidiUtils.cpRulesMap;
		chordInts.clear();

		int maxLength = (fixedLength > 0) ? fixedLength : 8;
		if (fixedLength == 8) {
			maxDuration *= 2;
		}
		double fixedDuration = maxDuration / maxLength;
		int currentLength = 0;
		double currentDuration = 0.0;
		List<Long> next = r.get(0L);
		if (LAST_CHORD != 0) {
			next = new ArrayList<Long>();
			next.add(Long.valueOf(LAST_CHORD));
		}
		List<String> debugMsg = new ArrayList<>();

		List<int[]> cpr = new ArrayList<>();
		int[] prevChord = null;
		boolean canRepeatChord = true;
		Long lastUnspicedChord = 0L;
		Random chordRepeatGenerator = new Random(gc.getRandomSeed());
		while ((currentDuration <= maxDuration - Durations.EIGHTH_NOTE)
				&& currentLength < maxLength) {
			double durationLeft = maxDuration - Durations.EIGHTH_NOTE - currentDuration;

			double dur = (fixedLength > 0) ? fixedDuration
					: MidiUtils.pickDurationWeightedRandom(durationGenerator, durationLeft,
							CHORD_DUR_ARRAY, CHORD_DUR_CHANCE, Durations.QUARTER_NOTE);

			if (next.size() == 0 && prevChord != null) {
				cpr.add(prevChord);
				break;
			}
			int nextInt = generator.nextInt(next.size());

			// if last and not empty first chord
			boolean isLastChord = durationLeft - dur < 0.01;
			Long chordInt = (isLastChord && FIRST_CHORD != 0) ? FIRST_CHORD : next.get(nextInt);
			if (gc.isAllowChordRepeats() && (fixedLength < 8 || !isLastChord) && canRepeatChord
					&& chordInts.size() > 0 && chordRepeatGenerator.nextInt(100) < 15) {
				chordInt = Long.valueOf(lastUnspicedChord);
				canRepeatChord = false;
			}

			long spiceResult = 1;
			int spiceSelectPow = generator.nextInt(MidiUtils.SPICE_SELECT.length) + 1;
			//SPICE CHANCE - multiply by 100/10000 to get aug,dim/maj,min 7th
			// 
			if (generator.nextInt(100) < gc.getSpiceChance()
					&& (chordInts.size() < 7 || FIRST_CHORD == 0)) {

				// 60 -> 600/6000 block 
				if (!gc.isDimAugEnabled() && spiceSelectPow <= 2) {
					// move to maj/min 7th
					spiceSelectPow += 2;
				}

				// 60 -> 6000000/60000000 block
				if (!gc.isEnable9th13th() && spiceSelectPow >= 5 && spiceSelectPow < 7) {
					// move to maj/min 7th
					spiceSelectPow -= 2;
				}

				// TODO: checkbox for sus chords

				// use 7th with correct maj/min chord
				if (chordInt < 10 && spiceSelectPow == 4) {
					spiceSelectPow--;
				} else if (chordInt >= 10 && spiceSelectPow == 3) {
					spiceSelectPow++;
				}

				spiceResult = (long) Math.pow(10, spiceSelectPow);
				if (chordInt < 10) {
					spiceResult *= 10;
				}
				chordInt *= spiceResult;
			}

			chordInts.add(chordInt);

			//System.out.println("Fetching chord: " + chordInt);
			int[] mappedChord = MidiUtils.mappedChord(chordInt);
			/*mappedChord = MidiUtils.transposeChord(mappedChord, Mod.MAJOR_SCALE,
					gc.getScaleMode().noteAdjustScale);*/


			debugMsg.add("Generated int: " + nextInt + ", for chord: " + chordInt + ", dur: " + dur
					+ ", C[" + Arrays.toString(mappedChord) + "]");
			cpr.add(mappedChord);
			progressionDurations.add(dur);
			chordInt /= spiceResult;

			prevChord = mappedChord;
			next = r.get(chordInt);

			if (fixedLength == 8 && chordInts.size() == 4) {
				FIRST_CHORD = chordInt;
			}

			// if last and empty first chord
			if (durationLeft - dur < 0 && FIRST_CHORD == 0) {
				FIRST_CHORD = chordInt;
			}
			currentLength += 1;
			currentDuration += dur;
			lastUnspicedChord = chordInt;

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

	private Note[] generateMelodyForChord(int[] chord, double maxDuration, Random generator,
			Note previousChordsNote, boolean isAscDirection) {
		List<Integer> scale = MidiUtils.transposeScale(MELODY_SCALE, 0, false);

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
			Note note = generateNote(chord, actualDirection, scale, previousNote, generator,
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
		Part melody = new Part("Melody",
				(!gc.getMelodyPart().isMuted()) ? gc.getMelodyPart().getInstrument() : 0, 0);
		Part bassRoots = new Part("BassRoots",
				(!gc.getBassPart().isMuted()) ? gc.getBassPart().getInstrument() : 74, 8);

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

		List<int[]> actualProgression = MidiUtils.squishChordProgression(generatedRootProgression,
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

		Arrangement arr = null;
		if (gc.getArrangement().isPreviewChorus()) {
			arr = new Arrangement();
			gc.setArrangementPartVariationChance(0);
			gc.setArrangementVariationChance(0);
		} else {
			arr = gc.getArrangement();
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
		boolean overridden = arr.isOverridden();
		int arrSeed = (arr.getSeed() != 0) ? arr.getSeed() : mainGeneratorSeed;
		for (Section sec : arr.getSections()) {
			System.out.println("Processing section.. " + sec.getType());
			sec.setStartTime(measureLength * counter);

			Random rand = new Random();

			if (sec.getType().equals("CLIMAX")) {
				// safe *2
				gc.setArrangementPartVariationChance(gc.getArrangementPartVariationChance() * 2);
			}

			int usedMeasures = sec.getMeasures();
			Random variationGen = new Random(arrSeed + sec.getTypeSeedOffset());
			if (sec.getMeasures() == 2
					&& variationGen.nextInt(100) < gc.getArrangementVariationChance()) {
				System.out.println("USING VARIATION!");
				progressionDurations = altProgressionDurations;
				rootProgression = altRootProgression;
				chordProgression = altChordProgression;
				usedMeasures = 1;
				measureVariationOverride = true;
			} else {
				if (!melodyBasedChordProgression.isEmpty()
						&& variationGen.nextInt(100) < gc.getArrangementVariationChance()) {
					System.out.println("SWAPPED TO MELODY BASED CHORDS/ROOTS!");
					rootProgression = melodyBasedRootProgression;
					chordProgression = melodyBasedChordProgression;
				} else {
					rootProgression = generatedRootProgression;
					chordProgression = actualProgression;
				}
				progressionDurations = actualDurations;
				measureVariationOverride = false;
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
			if (!gc.getMelodyPart().isMuted()) {
				boolean added = (overridden
						&& sec.getMelodyPresence().contains(gc.getMelodyPart().getOrder()))
						|| (!overridden && rand.nextInt(100) < sec.getMelodyChance());
				if (added) {
					int notesSeedOffset = 0;
					List<int[]> usedMelodyProg = chordProgression;
					List<int[]> usedRoots = rootProgression;
					if (!sec.getType().contains("CLIMAX") && !sec.getType().contains("CHORUS")
							&& variationGen.nextInt(100) < gc.getArrangementVariationChance()) {
						if (variationGen.nextBoolean() || melodyBasedChordProgression.isEmpty()) {
							notesSeedOffset = 1;
							System.out.println("Melody offset by 1..");
						} else {
							usedMelodyProg = melodyBasedChordProgression;
							usedRoots = melodyBasedRootProgression;
							System.out.println("Melody uses MELODY BASED CHORDS!");
						}

					}
					Phrase m = fillMelody(usedMelodyProg, usedRoots, usedMeasures, notesSeedOffset);

					sec.setMelody(m);
					if (!overridden)
						sec.getMelodyPresence().add(gc.getMelodyPart().getOrder());
				} else {
					sec.setMelody(emptyPhrase.copy());
				}

			}
			rand.setSeed(arrSeed + 10);
			variationGen.setSeed(arrSeed + 10);
			if (!gc.getBassPart().isMuted()) {

				boolean added = (overridden
						&& sec.getBassPresence().contains(gc.getBassPart().getOrder()))
						|| (!overridden && rand.nextInt(100) < sec.getBassChance());
				if (added) {
					CPhrase b = fillBassRoots(rootProgression, usedMeasures);
					if (variationGen.nextInt(100) < gc.getArrangementPartVariationChance()) {
						// TODO
					}
					sec.setBass(b);
					if (!overridden)
						sec.getBassPresence().add(gc.getBassPart().getOrder());
				} else {
					sec.setBass(emptyCPhrase.copy());
				}

			}

			if (!gc.getChordParts().isEmpty()) {
				List<CPhrase> copiedCPhrases = new ArrayList<>();
				for (int i = 0; i < gc.getChordParts().size(); i++) {
					ChordPart cp = gc.getChordParts().get(i);
					rand.setSeed(arrSeed + 100 + cp.getOrder());
					variationGen.setSeed(arrSeed + 100 + cp.getOrder());
					boolean added = (overridden && sec.getChordPresence().contains(cp.getOrder()))
							|| (!overridden && rand.nextInt(100) < sec.getChordChance());
					if (added && !cp.isMuted()) {
						CPhrase c = fillChordsFromPart(cp, chordProgression, usedMeasures);
						if (variationGen.nextInt(100) < gc.getArrangementPartVariationChance()) {
							// TODO Mod.transpose(c, 12);
						}
						copiedCPhrases.add(c);
						if (!overridden)
							sec.getChordPresence().add(cp.getOrder());
					} else {
						copiedCPhrases.add(emptyCPhrase.copy());
					}
				}
				sec.setChords(copiedCPhrases);
				if (!gc.getChordParts().get(0).isMuted()
						&& sec.getChordPresence().contains(gc.getChordParts().get(0).getOrder())) {
					sec.setChordSlash(fillChordSlash(chordProgression, usedMeasures));
				} else {
					sec.setChordSlash(emptyPhrase.copy());
				}

			}

			if (!gc.getArpParts().isEmpty()) {
				List<CPhrase> copiedCPhrases = new ArrayList<>();
				for (int i = 0; i < gc.getArpParts().size(); i++) {
					ArpPart ap = gc.getArpParts().get(i);
					rand.setSeed(arrSeed + 200 + ap.getOrder());
					variationGen.setSeed(arrSeed + 200 + ap.getOrder());
					// if arp1 supports melody with same instrument, always introduce it in second half
					CPhrase a = fillArpFromPart(ap, chordProgression, usedMeasures);
					if (overridden) {
						if (sec.getArpPresence().contains(ap.getOrder())) {
							copiedCPhrases.add(a);
						} else {
							copiedCPhrases.add(emptyCPhrase.copy());
						}
					} else {
						if (i == 0 && ap.getInstrument() == gc.getMelodyPart().getInstrument()) {
							if (isPreview || counter > ((arr.getSections().size() + 1) / 2)
									&& !ap.isMuted()) {
								if (variationGen.nextInt(100) < gc
										.getArrangementPartVariationChance()) {
									// TODO Mod.transpose(a, 12);
								}
								copiedCPhrases.add(a);
								sec.getArpPresence().add(ap.getOrder());
							} else {
								copiedCPhrases.add(emptyCPhrase.copy());
							}
						} else {
							if (rand.nextInt(100) < sec.getArpChance() && !ap.isMuted()) {
								if (variationGen.nextInt(100) < gc
										.getArrangementPartVariationChance()) {
									// TODO Mod.transpose(a, 12);
								}
								copiedCPhrases.add(a);
								sec.getArpPresence().add(ap.getOrder());
							} else {
								copiedCPhrases.add(emptyCPhrase.copy());
							}
						}
					}
				}
				sec.setArps(copiedCPhrases);
			}

			if (!gc.getDrumParts().isEmpty()) {
				List<Phrase> copiedPhrases = new ArrayList<>();
				for (int i = 0; i < gc.getDrumParts().size(); i++) {
					DrumPart dp = gc.getDrumParts().get(i);
					rand.setSeed(arrSeed + 300 + dp.getOrder());
					variationGen.setSeed(arrSeed + 300 + dp.getOrder());
					boolean added = (overridden && sec.getDrumPresence().contains(dp.getOrder()))
							|| (!overridden && rand.nextInt(100) < sec.getDrumChance());
					if (added && !dp.isMuted()) {
						int sectionChanceModifier = 75 + (sec.getDrumChance() / 4);
						boolean sectionForcedDynamics = (sec.getType().contains("CLIMAX")
								|| sec.getType().contains("CHORUS"))
								&& variationGen.nextInt(100) < gc
										.getArrangementPartVariationChance();
						Phrase d = fillDrumsFromPart(dp, chordProgression, usedMeasures,
								sectionChanceModifier, sectionForcedDynamics);
						if (variationGen.nextInt(100) < gc.getArrangementPartVariationChance()) {
							// TODO Mod.accent(d, 0.25);
						}
						copiedPhrases.add(d);
						if (!overridden)
							sec.getDrumPresence().add(dp.getOrder());
					} else {
						copiedPhrases.add(emptyPhrase.copy());
					}
				}
				sec.setDrums(copiedPhrases);
			}

			counter += sec.getMeasures();
		}
		System.out.println("Added phrases/cphrases to sections..");

		for (Section sec : arr.getSections()) {
			if (!gc.getMelodyPart().isMuted()) {
				Phrase mp = sec.getMelody();
				mp.setStartTime(mp.getStartTime() + sec.getStartTime());
				melody.addPhrase(mp);
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
				Phrase cscp = sec.getChordSlash();
				cscp.setStartTime(cscp.getStartTime() + sec.getStartTime());
				cscp.setAppend(false);
				chordParts.get(0).addPhrase(cscp);
			}

		}
		System.out.println("Added sections to parts..");
		int trackCounter = 1;
		if (!gc.getMelodyPart().isMuted()) {
			score.add(melody);
			VibeComposerGUI.melodyPanel.setSequenceTrack(trackCounter++);
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


		Mod.transpose(score, gc.getScaleMode().ordinal(), Mod.MAJOR_SCALE, 0);
		int[] backTranspose = { 0, 2, 4, 5, 7, 9, 11, 12 };
		Mod.transpose(score, gc.getTranspose() - backTranspose[gc.getScaleMode().ordinal()]);

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
		for (int i = 0; i < 2; i++) {
			altProgressionDurations.addAll(progressionDurations);
			altChordProgression.addAll(chordProgression);
			altRootProgression.addAll(rootProgression);
		}
		double duration = progressionDurations.get(progressionDurations.size() - 1)
				+ progressionDurations.get(progressionDurations.size() - 2);
		altProgressionDurations.set(progressionDurations.size() - 2, duration);
		altProgressionDurations.remove(progressionDurations.size() - 1);

		altChordProgression.remove(progressionDurations.size() - 2);
		altRootProgression.remove(progressionDurations.size() - 2);


	}

	protected Phrase fillMelody(List<int[]> actualProgression, List<int[]> generatedRootProgression,
			int measures, int notesSeedOffset) {
		Phrase melodyPhrase = new Phrase();
		List<Boolean> directionProgression = generateMelodyDirectionsFromChordProgression(
				generatedRootProgression, true);
		Random algoGenerator = new Random(gc.getRandomSeed());
		Note previousChordsNote = null;
		if (algoGenerator.nextInt(100) < gc.getMelodyUseOldAlgoChance()) {
			Note[] pair024 = null;
			Note[] pair15 = null;
			Random melodyGenerator = new Random();
			if (!gc.getMelodyPart().isMuted() && gc.getMelodyPart().getPatternSeed() != 0) {
				melodyGenerator.setSeed(gc.getMelodyPart().getPatternSeed());
			} else {
				melodyGenerator.setSeed(gc.getRandomSeed());
			}
			System.out.println("LEGACY ALGORITHM!");
			Vector<Note> fullMelody = new Vector<>();
			for (int i = 0; i < measures; i++) {
				for (int j = 0; j < generatedRootProgression.size(); j++) {
					Note[] generatedMelody = null;

					if ((i > 0 || j > 0) && (j == 0 || j == 2)) {
						generatedMelody = deepCopyNotes(pair024, generatedRootProgression.get(j),
								melodyGenerator);
					} else if (i > 0 && j == 1) {
						generatedMelody = deepCopyNotes(pair15, null, null);
					} else {
						generatedMelody = generateMelodyForChord(generatedRootProgression.get(j),
								progressionDurations.get(j), melodyGenerator, previousChordsNote,
								directionProgression.get(j));
					}

					previousChordsNote = generatedMelody[generatedMelody.length - 1];

					if (i == 0 && j == 0) {
						pair024 = deepCopyNotes(generatedMelody, null, null);
					}
					if (i == 0 && j == 1) {
						pair15 = deepCopyNotes(generatedMelody, null, null);
					}
					fullMelody.addAll(Arrays.asList(generatedMelody));
				}
			}
			melodyPhrase.addNoteList(fullMelody, true);

		} else {
			Vector<Note> skeletonNotes = generateMelodySkeletonFromChords(actualProgression,
					generatedRootProgression, measures, notesSeedOffset);
			Vector<Note> fullMelody = convertMelodySkeletonToFullMelody(skeletonNotes);
			swingMelody(fullMelody);
			melodyPhrase.addNoteList(fullMelody, true);
		}
		Mod.transpose(melodyPhrase, gc.getMelodyPart().getTranspose());
		melodyPhrase.setStartTime(START_TIME_DELAY);
		return melodyPhrase;
	}


	protected CPhrase fillBassRoots(List<int[]> generatedRootProgression, int measures) {
		CPhrase cphraseBassRoot = new CPhrase();
		Random variationGenerator = new Random(gc.getRandomSeed() + 1);
		for (int i = 0; i < measures; i++) {
			int extraSeed = (i > 0
					&& variationGenerator.nextInt(100) < gc.getArrangementPartVariationChance())
							? 100
							: 0;
			for (int j = 0; j < generatedRootProgression.size(); j++) {
				Random bassDynamics = new Random(gc.getRandomSeed());
				int velSpace = gc.getBassPart().getVelocityMax()
						- gc.getBassPart().getVelocityMin();
				if (gc.getBassPart().isUseRhythm()) {
					int seed = (int) gc.getRandomSeed();
					seed += extraSeed;
					if (gc.getBassPart().isAlternatingRhythm()) {
						seed += (j % 2);
					}
					Rhythm bassRhythm = new Rhythm(seed, progressionDurations.get(j));
					for (Double dur : bassRhythm.regenerateDurations()) {
						cphraseBassRoot.addChord(new int[] { generatedRootProgression.get(j)[0] },
								dur,
								bassDynamics.nextInt(velSpace) + gc.getBassPart().getVelocityMin());
					}
				} else {
					cphraseBassRoot.addChord(new int[] { generatedRootProgression.get(j)[0] },
							progressionDurations.get(j),
							bassDynamics.nextInt(velSpace) + gc.getBassPart().getVelocityMin());
				}
			}
		}
		Mod.transpose(cphraseBassRoot, -24);
		cphraseBassRoot.setStartTime(START_TIME_DELAY);
		return cphraseBassRoot;

	}

	protected CPhrase fillChordsFromPart(ChordPart cp, List<int[]> actualProgression,
			int measures) {
		int mainGeneratorSeed = (int) cp.getPatternSeed() + cp.getOrder();
		CPhrase cpr = new CPhrase();
		Random variationGenerator = new Random(mainGeneratorSeed + 100);
		int stretch = cp.getChordNotesStretch();
		for (int i = 0; i < measures; i++) {
			Random transitionGenerator = new Random(mainGeneratorSeed);
			int extraTranspose = 0;
			boolean ignoreChordSpanFill = false;

			// fill chords
			for (int j = 0; j < actualProgression.size(); j++) {
				if ((j == 0 && i > 0) || (j == chordInts.size())) {
					if (variationGenerator.nextInt(100) < gc.getArrangementPartVariationChance()) {
						// pick one variation
						int numberOfVars = 3;
						int variationInt = variationGenerator.nextInt(numberOfVars);
						System.out
								.println("Chord #" + cp.getOrder() + " variation: " + variationInt);
						switch (variationInt) {
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

				if (cp.getPattern() == RhythmPattern.RANDOM) {
					double splitTime = gc.getChordGenSettings().isUseSplit()
							? cp.getTransitionSplit()
							: DEFAULT_CHORD_SPLIT;

					double duration1 = progressionDurations.get(j) * splitTime / 1000.0;
					double duration2 = progressionDurations.get(j) - duration1;
					if (transition) {
						MidiUtils.addShortenedChord(cpr,
								MidiUtils.convertChordToLength(
										MidiUtils.transposeChord(actualProgression.get(j),
												extraTranspose),
										cp.getChordNotesStretch(), cp.isStretchEnabled()),
								duration1, velocity, shortenedTo);
						MidiUtils.addShortenedChord(cpr,
								MidiUtils.convertChordToLength(
										MidiUtils.transposeChord(actualProgression.get(transChord),
												extraTranspose),
										cp.getChordNotesStretch(), cp.isStretchEnabled()),
								duration2, velocity, shortenedTo);
					} else {
						MidiUtils.addShortenedChord(cpr,
								MidiUtils.convertChordToLength(
										MidiUtils.transposeChord(actualProgression.get(j),
												extraTranspose),
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
							MidiUtils.addShortenedChord(cpr,
									MidiUtils.convertChordToLength(
											MidiUtils.transposeChord(actualProgression.get(j),
													extraTranspose),
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
		Mod.transpose(cpr, -12 + extraTranspose);

		// delay
		double additionalDelay = 0;
		if (gc.getChordGenSettings().isUseDelay()) {
			additionalDelay = (cp.getDelay() / 1000.0);
		}
		cpr.setStartTime(START_TIME_DELAY + additionalDelay);

		// chord strum
		if (gc.getChordGenSettings().isUseStrum()) {
			if (cp.getPattern() == RhythmPattern.RANDOM) {
				cpr.flam(cp.getStrum() / 1000.0);
			} else {
				//cpr.flam(10 / 1000.0);
			}
		}

		return cpr;
	}

	protected CPhrase fillArpFromPart(ArpPart ap, List<int[]> actualProgression, int measures) {

		CPhrase arpCPhrase = new CPhrase();

		Map<String, List<Integer>> arpMap = generateArpMap(ap.getPatternSeed(),
				ap.equals(gc.getArpParts().get(0)), ap);

		List<Integer> arpPattern = arpMap.get(ARP_PATTERN_KEY);
		List<Integer> arpOctavePattern = arpMap.get(ARP_OCTAVE_KEY);
		List<Integer> arpPausesPattern = arpMap.get(ARP_PAUSES_KEY);


		int repeatedArpsPerChord = ap.getHitsPerPattern() * ap.getPatternRepeat();

		double longestChord = progressionDurations.stream().max((e1, e2) -> Double.compare(e1, e2))
				.get();
		Random variationGenerator = new Random(ap.getPatternSeed() + ap.getOrder());
		for (int i = 0; i < measures; i++) {
			int chordSpanPart = 0;
			int extraTranspose = 0;
			boolean ignoreChordSpanFill = false;

			Random velocityGenerator = new Random(ap.getPatternSeed());
			Random exceptionGenerator = new Random(ap.getPatternSeed() + 1);
			for (int j = 0; j < actualProgression.size(); j++) {
				if ((j == 0 && i > 0) || (j == chordInts.size())) {
					if (variationGenerator.nextInt(100) < gc.getArrangementPartVariationChance()) {
						// pick one variation
						int numberOfVars = 2;
						int variationInt = variationGenerator.nextInt(numberOfVars);
						System.out.println("Arp #" + ap.getOrder() + " variation: " + variationInt);
						switch (variationInt) {
						case 0:
							extraTranspose = 12;
							break;
						case 1:
							ignoreChordSpanFill = true;
							break;
						default:
							throw new IllegalArgumentException("Too much variation!");
						}
					}
				}
				double chordDurationArp = longestChord / ((double) repeatedArpsPerChord);
				int[] chord = MidiUtils.convertChordToLength(actualProgression.get(j),
						ap.getChordNotesStretch(), ap.isStretchEnabled());
				if (ap.getArpPattern() != ArpPattern.RANDOM) {
					arpPattern = ap.getArpPattern().getPatternByLength(ap.getHitsPerPattern(),
							chord.length, ap.getPatternRepeat());
					arpPattern = intersperse(0, ap.getChordSpan() - 1, arpPattern);
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
					if (gc.isUseOctaveAdjustments()) {
						pitch += octaveAdjustmentFromPattern + octaveAdjustGenerated;
					}

					pitch += extraTranspose;
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
		Mod.transpose(arpCPhrase, -24 + extraTranspose);

		double additionalDelay = 0;
		/*if (ARP_SETTINGS.isUseDelay()) {
			additionalDelay = (gc.getArpParts().get(i).getDelay() / 1000.0);
		}*/
		arpCPhrase.setStartTime(START_TIME_DELAY + additionalDelay);
		return arpCPhrase;
	}


	protected Phrase fillDrumsFromPart(DrumPart dp, List<int[]> actualProgression, int measures,
			int sectionChanceModifier, boolean sectionForcedDynamics) {
		Phrase drumPhrase = new Phrase();

		int chordsCount = actualProgression.size();

		List<Integer> drumPattern = generateDrumPatternFromPart(dp);
		List<Integer> drumVelocityPattern = generateDrumVelocityPatternFromPart(dp);
		Random variationGenerator = new Random(dp.getPatternSeed() + dp.getOrder());
		// bar iter
		for (int o = 0; o < measures; o++) {
			// exceptions are generated the same for each bar, but differently for each pattern within bar (if there is more than 1)
			Random exceptionGenerator = new Random(dp.getPatternSeed() + dp.getOrder());
			int chordSpan = dp.getChordSpan();
			int oneChordPatternSize = drumPattern.size() / chordSpan;
			boolean ignoreChordSpanFill = false;
			int extraExceptionChance = 0;

			// chord iter
			for (int j = 0; j < chordsCount; j += chordSpan) {
				if ((j == 0 && o > 0) || (j == chordInts.size())) {
					if (variationGenerator.nextInt(100) < gc.getArrangementPartVariationChance()) {
						// pick one variation
						int numberOfVars = 2;
						int variationInt = variationGenerator.nextInt(numberOfVars);
						System.out
								.println("Drum #" + dp.getOrder() + " variation: " + variationInt);
						switch (variationInt) {
						case 0:
							ignoreChordSpanFill = true;
							break;
						case 1:
							extraExceptionChance += 10;
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

				double drumDuration = patternDurationTotal / dp.getHitsPerPattern();
				if (!dp.isVelocityPattern() && drumPattern.indexOf(dp.getInstrument()) == -1) {
					continue;
				}
				int swingPercentAmount = dp.getSwingPercent();
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


					double swingDuration = drumDuration * (swingPercentAmount / ((double) 50.0));
					swingPercentAmount = 100 - swingPercentAmount;

					boolean exception = exceptionGenerator
							.nextInt(100) < (dp.getExceptionChance() + extraExceptionChance);
					if (exception) {
						int secondVelocity = (velocity * 8) / 10;
						drumPhrase.addNote(new Note(pitch, swingDuration / 2, velocity));
						drumPhrase.addNote(new Note(pitch, swingDuration / 2, secondVelocity));
					} else {
						drumPhrase.addNote(new Note(pitch, swingDuration, velocity));
					}

				}
			}
		}


		drumPhrase.setStartTime(START_TIME_DELAY + (dp.getDelay() / 1000.0));


		return drumPhrase;

	}

	protected Phrase fillChordSlash(List<int[]> actualProgression, int measures) {
		Phrase chordSlashCPhrase = new Phrase();
		Random chordSlashGenerator = new Random(gc.getRandomSeed() + 2);
		for (int i = 0; i < measures; i++) {
			// fill slash chord slashes
			for (int j = 0; j < actualProgression.size(); j++) {
				// pick random chord, take first/root pitch
				boolean isChordSlash = chordSlashGenerator.nextInt(100) < gc.getChordSlashChance();
				long slashChord = chordSlashGenerator.nextInt(6) + 1;
				int[] mappedChord = MidiUtils.mappedChord(slashChord);
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

	private List<Integer> intersperse(int number, int times, List<Integer> list) {
		List<Integer> interspersed = new ArrayList<>();
		for (Integer i : list) {
			interspersed.add(i);
			for (int j = 0; j < times; j++) {
				interspersed.add(number);
			}
		}
		return interspersed;
	}

	private void applyRuleToMelody(Note[] melody, Consumer<Note[]> melodyRule) {
		melodyRule.accept(melody);
	}

	private Note[] deepCopyNotes(Note[] originals, int[] chord, Random melodyGenerator) {
		Note[] copied = new Note[originals.length];
		for (int i = 0; i < originals.length; i++) {
			Note n = originals[i];
			copied[i] = new Note(n.getPitch(), n.getRhythmValue());
		}
		if (chord != null && melodyGenerator != null && gc.isFirstNoteFromChord()) {
			Note n = generateNote(chord, true, MELODY_SCALE, null, melodyGenerator,
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

		if (ap.getPattern() == RhythmPattern.RANDOM) {
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
			arpPattern = intersperse(0, ap.getChordSpan() - 1, arpPattern);
			arpOctavePattern = intersperse(0, ap.getChordSpan() - 1, arpOctavePattern);
			arpPausesPattern = intersperse(0, ap.getChordSpan() - 1, arpPausesPattern);
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

	private List<Integer> generateDrumPatternFromPart(DrumPart dp) {
		Random uiGenerator1drumPattern = new Random(dp.getPatternSeed() + dp.getOrder() - 1);
		List<Integer> premadePattern = dp.getPattern().getPatternByLength(dp.getHitsPerPattern());
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
		Collections.rotate(drumPattern, dp.getPatternShift());
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
