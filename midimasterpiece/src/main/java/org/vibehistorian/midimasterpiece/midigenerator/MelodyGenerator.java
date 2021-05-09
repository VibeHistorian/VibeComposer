
package org.vibehistorian.midimasterpiece.midigenerator;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.midimasterpiece.midigenerator.Enums.ChordSpanFill;
import org.vibehistorian.midimasterpiece.midigenerator.Enums.RhythmPattern;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.ArpGenSettings;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.DrumGenSettings;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.ArpPart;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.ChordPart;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.DrumPart;

import jm.JMC;
import jm.constants.Durations;
import jm.music.data.CPhrase;
import jm.music.data.Note;
import jm.music.data.Part;
import jm.music.data.Phrase;
import jm.music.data.Score;
import jm.music.tools.Mod;
import jm.util.View;
import jm.util.Write;

public class MelodyGenerator implements JMC {

	// big G
	public static GUIConfig gc;

	// constants
	public static final int MAXIMUM_PATTERN_LENGTH = 8;
	private static final int OPENHAT_CHANCE = 15;
	private static final int PROGRESSION_LENGTH = 4;
	private static final int maxAllowedScaleNotes = 7;
	private static final double START_TIME_DELAY = 0.5;
	private static final double DEFAULT_CHORD_SPLIT = 625;
	private static final String ARP_PATTERN_KEY = "ARP_PATTERN";
	private static final String ARP_OCTAVE_KEY = "ARP_OCTAVE";
	private static final String ARP_PAUSES_KEY = "ARP_PAUSES";

	// visibles/settables
	public static DrumGenSettings DRUM_SETTINGS = new DrumGenSettings();
	public static ArpGenSettings ARP_SETTINGS = new ArpGenSettings();

	public static List<Integer> userChords = new ArrayList<>();
	public static List<Double> userChordsDurations = new ArrayList<>();
	public static List<Integer> chordInts = new ArrayList<>();

	public static int FIRST_CHORD = 0;
	public static int LAST_CHORD = 0;

	public static boolean DISPLAY_SCORE = false;

	// for internal use only
	private double[] MELODY_DUR_ARRAY = { Durations.QUARTER_NOTE, Durations.DOTTED_EIGHTH_NOTE,
			Durations.EIGHTH_NOTE, Durations.SIXTEENTH_NOTE };
	private double[] MELODY_DUR_CHANCE = { 0.3, 0.6, 1.0, 1.0 };

	private double[] CHORD_DUR_ARRAY = { Durations.WHOLE_NOTE, Durations.DOTTED_HALF_NOTE,
			Durations.HALF_NOTE, Durations.QUARTER_NOTE };
	private double[] CHORD_DUR_CHANCE = { 0.0, 0.20, 0.80, 1.0 };

	private List<Integer> MELODY_SCALE = MidiUtils.cIonianScale4;
	private List<Double> progressionDurations = new ArrayList<>();

	private int samePitchCount = 0;
	private int previousPitch = 0;


	public MelodyGenerator(GUIConfig gc) {
		MelodyGenerator.gc = gc;
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

	public Vector<Note> generateMelodySkeletonFromChords(List<int[]> chords, int measures) {
		//155816678 seed

		// TODO: parameter in melodypart like max jump 
		int MAX_JUMP_SKELETON_CHORD = 1;
		int SAME_RHYTHM_CHANCE = gc.getMelodySameRhythmChance();
		int ALTERNATE_RHYTHM_CHANCE = gc.getMelodyAlternateRhythmChance();

		int seed = gc.getMelodyPart().getPatternSeed();

		Vector<Note> noteList = new Vector<>();
		Random generator = new Random(seed);
		Random velocityGenerator = new Random(seed + 1);
		Random exceptionGenerator = new Random(seed + 2);
		Random sameRhythmGenerator = new Random(seed + 3);
		Random alternateRhythmGenerator = new Random(seed + 4);

		double[] melodySkeletonDurations = { Durations.SIXTEENTH_NOTE, Durations.EIGHTH_NOTE,
				Durations.DOTTED_EIGHTH_NOTE, Durations.QUARTER_NOTE };
		int[] melodySkeletonDurationWeights = { 25, 50, 85, 100 };

		List<Boolean> directions = generateMelodyDirectionsFromChordProgression(chords, true);
		System.out.println(directions);
		// TODO: fix here if 6 not enough
		List<int[]> stretchedChords = chords.stream()
				.map(e -> MidiUtils.convertChordToLength(e, 4, true)).collect(Collectors.toList());

		boolean alternateRhythm = alternateRhythmGenerator.nextInt(100) < ALTERNATE_RHYTHM_CHANCE;
		System.out.println("Alt: " + alternateRhythm);
		for (int o = 0; o < measures; o++) {
			int previousNotePitch = 0;

			for (int i = 0; i < stretchedChords.size(); i++) {
				boolean sameRhythmTwice = sameRhythmGenerator.nextInt(100) < SAME_RHYTHM_CHANCE;

				double rhythmDuration = sameRhythmTwice ? progressionDurations.get(i) / 2.0
						: progressionDurations.get(i);
				int rhythmSeed = (alternateRhythm && i % 2 == 1) ? seed + 1 : seed;
				Rhythm rhythm = new Rhythm(rhythmSeed, rhythmDuration, melodySkeletonDurations,
						melodySkeletonDurationWeights);
				if (i % 2 == 0) {
					previousNotePitch = 0;
					generator.setSeed(seed);
					exceptionGenerator.setSeed(seed + 2);
				}
				List<Double> durations = rhythm.regenerateDurations();
				if (sameRhythmTwice) {
					durations.addAll(durations);
				}

				int[] chord = stretchedChords.get(i);
				int exceptionCounter = gc.getMaxExceptions();
				boolean direction = directions.get(i);
				for (int j = 0; j < durations.size(); j++) {

					if (j > 0 && exceptionCounter > 0 && exceptionGenerator.nextInt(100) < 33) {
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
					pitch = pickRandomBetweenIndexesInclusive(chord, startIndex, endIndex,
							generator);
					Note n = new Note(pitch, durations.get(j),
							velocityGenerator
									.nextInt(1 + gc.getMelodyPart().getVelocityMax()
											- gc.getMelodyPart().getVelocityMin())
									+ gc.getMelodyPart().getVelocityMin());
					//TODO: make sound good
					if (previousNotePitch == pitch) {
						direction = !direction;
					}
					previousNotePitch = pitch;
					noteList.add(n);
				}

			}
		}


		return noteList;
	}

	private int getAllowedPitchFromRange(int min, int max) {
		List<Integer> allowedPitches = gc.getScaleMode().absoluteNotesC;
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
			if (i >= min && i <= max) {
				return i + adjustment;
			}
		}
		return 40;
	}

	public Vector<Note> convertMelodySkeletonToFullMelody(Vector<Note> skeleton) {
		Random splitGenerator = new Random(gc.getMelodyPart().getPatternSeed() + 4);
		Random pauseGenerator = new Random(gc.getMelodyPart().getPatternSeed() + 5);
		int splitChance = 20;
		Vector<Note> fullMelody = new Vector<>();
		int chordCounter = 0;
		double durCounter = 0.0;
		double currentChordDur = progressionDurations.get(0);
		for (int i = 0; i < skeleton.size(); i++) {
			double adjDur = 10.0 * skeleton.get(i).getDuration() / 9.0;
			if (durCounter + adjDur > currentChordDur) {
				chordCounter = (chordCounter + 1) % progressionDurations.size();
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
			if (adjDur > Durations.SIXTEENTH_NOTE && splitGenerator.nextInt(100) < splitChance) {
				Note n1 = skeleton.get(i);
				Note n2 = skeleton.get((i + 1) % skeleton.size());
				int pitch = 0;
				if (n1.getPitch() >= n2.getPitch()) {
					pitch = getAllowedPitchFromRange(n2.getPitch(), n1.getPitch());
				} else {
					pitch = getAllowedPitchFromRange(n1.getPitch(), n2.getPitch());
				}
				double duration = adjDur / 2.0;
				Note n1split1 = new Note(n1.getPitch(), duration, n1.getDynamic());
				Note n1split2 = new Note(pitch, duration, n1.getDynamic() - 10);
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


	public Note generateNote(int[] chord, boolean isAscDirection, List<Integer> chordScale,
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

	public List<int[]> generateChordProgression(int mainGeneratorSeed, boolean fixedLength,
			double maxDuration) {

		if (!userChords.isEmpty()) {
			List<int[]> userProgression = new ArrayList<>();
			chordInts.clear();
			chordInts.addAll(userChords);
			for (Integer chordInt : userChords) {
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

		Map<Integer, List<Integer>> r = MidiUtils.cpRulesMap;
		chordInts.clear();

		int maxLength = (fixedLength) ? PROGRESSION_LENGTH : 8;
		int currentLength = 0;
		double currentDuration = 0.0;
		List<Integer> next = r.get(0);
		if (LAST_CHORD != 0) {
			next = new ArrayList<Integer>();
			next.add(Integer.valueOf(LAST_CHORD));
		}
		List<String> debugMsg = new ArrayList<>();

		List<int[]> cpr = new ArrayList<>();
		int[] prevChord = null;
		while ((currentDuration <= maxDuration - Durations.EIGHTH_NOTE)
				&& currentLength < maxLength) {
			double durationLeft = maxDuration - Durations.EIGHTH_NOTE - currentDuration;

			double dur = (fixedLength) ? Durations.HALF_NOTE
					: MidiUtils.pickDurationWeightedRandom(durationGenerator, durationLeft,
							CHORD_DUR_ARRAY, CHORD_DUR_CHANCE, Durations.QUARTER_NOTE);

			if (next.size() == 0 && prevChord != null) {
				cpr.add(prevChord);
				break;
			}
			int nextInt = generator.nextInt(next.size());

			// if last and not empty first chord
			Integer chordInt = (durationLeft - dur < 0.01 && FIRST_CHORD != 0) ? FIRST_CHORD
					: next.get(nextInt);

			int spiceResult = 1;
			int spiceSelectPow = generator.nextInt(MidiUtils.SPICE_SELECT.length) + 1;
			//SPICE CHANCE - multiply by 100/10000 to get aug,dim/maj,min 7th
			// 
			if (generator.nextInt(100) < gc.getSpiceChance()) {
				int spiceInt = 10;

				// 60 -> 600/6000 block 
				if (!gc.isDimAugEnabled() && spiceSelectPow <= 2) {
					// move to maj/min 7th
					spiceSelectPow += 2;
				}

				// 60 -> 6000000/60000000 block
				if (!gc.isEnable9th13th() && spiceSelectPow >= 5) {
					// move to maj/min 7th
					spiceSelectPow -= 2;
				}

				// use 7th with correct maj/min chord
				if (chordInt < 10 && spiceSelectPow == 4) {
					spiceSelectPow--;
				} else if (chordInt >= 10 && spiceSelectPow == 3) {
					spiceSelectPow++;
				}

				spiceResult = (int) Math.pow(spiceInt, spiceSelectPow);
				if (chordInt < 10) {
					spiceResult *= 10;
				}
				chordInt *= spiceResult;
			}

			chordInts.add(chordInt);
			int[] mappedChord = MidiUtils.mappedChord(chordInt);
			mappedChord = MidiUtils.transposeChord(mappedChord, Mod.MAJOR_SCALE,
					gc.getScaleMode().noteAdjustScale);


			debugMsg.add("Generated int: " + nextInt + ", for chord: " + chordInt + ", dur: " + dur
					+ ", C[" + Arrays.toString(mappedChord) + "]");
			cpr.add(mappedChord);
			progressionDurations.add(dur);
			chordInt /= spiceResult;

			prevChord = mappedChord;
			next = r.get(chordInt);

			// if last and empty first chord
			if (durationLeft - dur < 0 && FIRST_CHORD == 0) {
				FIRST_CHORD = chordInt;
			}
			currentLength += 1;
			currentDuration += dur;

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

	public Note[] generateMelodyForChord(int[] chord, double maxDuration, Random generator,
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
			currentDuration += (note.getDuration() * 10.0 / 9.0);
			Note transposedNote = new Note(note.getPitch(), note.getRhythmValue(),
					note.getDynamic());
			notes.add(transposedNote);
		}
		return notes.toArray(new Note[0]);
	}

	public void generateMasterpiece(int mainGeneratorSeed, String fileName,
			int melodyProgramChange) {
		System.out.println("--- GENERATING MASTERPIECE.. ---");
		MELODY_SCALE = gc.getScaleMode().absoluteNotesC;

		Score score = new Score("MainScore", 120);
		Part melody = new Part("Melody",
				(!gc.getMelodyPart().isMuted()) ? gc.getMelodyPart().getInstrument() : 0, 0);
		Part bassRoots = new Part("BassRoots",
				(!gc.getBassPart().isMuted()) ? gc.getBassPart().getInstrument() : 74, 8);
		Part chordSlash = new Part("ChordSlash",
				(gc.getChordParts().size() > 0) ? gc.getChordParts().get(0).getInstrument() : 4,
				10);

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

		// Generate chords..
		List<int[]> generatedRootProgression = generateChordProgression(mainGeneratorSeed,
				gc.isFixedDuration(), 4 * Durations.HALF_NOTE);
		if (!userChordsDurations.isEmpty()) {
			progressionDurations = userChordsDurations;
		}

		List<int[]> actualProgression = MidiUtils.squishChordProgression(generatedRootProgression);

		// Arrangement process..
		System.out.println("Starting arrangement..");
		double measureLength = 0;
		for (Double d : progressionDurations) {
			measureLength += d;
		}
		int counter = 0;

		Arrangement arr = (gc.getArrangement().isPreviewChorus()) ? new Arrangement()
				: gc.getArrangement();
		for (Section sec : arr.getSections()) {
			sec.setStartTime(measureLength * counter);
			counter += sec.getMeasures();
			Random rand = new Random();

			// copied into empty sections
			Note emptyMeasureNote = new Note(Integer.MIN_VALUE, measureLength);
			Phrase emptyPhrase = new Phrase();
			emptyPhrase.add(emptyMeasureNote);
			CPhrase emptyCPhrase = new CPhrase();
			emptyCPhrase.addChord(new int[] { Integer.MIN_VALUE }, measureLength);


			rand.setSeed(mainGeneratorSeed);
			if (!gc.getMelodyPart().isMuted()) {
				if (rand.nextInt(100) < sec.getMelodyChance()) {
					sec.setMelody(fillMelody(actualProgression, generatedRootProgression,
							sec.getMeasures()));
				} else {
					sec.setMelody(emptyPhrase.copy());
				}

			}
			rand.setSeed(mainGeneratorSeed + 10);
			if (!gc.getBassPart().isMuted()) {
				if (rand.nextInt(100) < sec.getBassChance()) {
					sec.setBass(fillBassRoots(generatedRootProgression, sec.getMeasures()));
				} else {
					sec.setBass(emptyCPhrase.copy());
				}

			}

			if (!gc.getChordParts().isEmpty()) {
				List<CPhrase> copiedCPhrases = new ArrayList<>();
				for (int i = 0; i < gc.getChordParts().size(); i++) {
					rand.setSeed(mainGeneratorSeed + 100 + gc.getChordParts().get(i).getOrder());
					if (rand.nextInt(100) < sec.getChordChance()) {
						copiedCPhrases.add(fillChordsFromPart(gc.getChordParts().get(i),
								actualProgression, sec.getMeasures()));
					} else {
						copiedCPhrases.add(emptyCPhrase.copy());
					}
				}
				sec.setChords(copiedCPhrases);
				if (rand.nextInt(100) < sec.getChordChance()) {
					sec.setChordSlash(fillChordSlash(actualProgression, sec.getMeasures()));
				} else {
					sec.setChordSlash(emptyCPhrase.copy());
				}

			}

			if (!gc.getArpParts().isEmpty()) {
				List<CPhrase> copiedCPhrases = new ArrayList<>();
				for (int i = 0; i < gc.getArpParts().size(); i++) {
					rand.setSeed(mainGeneratorSeed + 200 + gc.getArpParts().get(i).getOrder());
					// if arp1 supports melody with same instrument, always introduce it in second half
					if (i == 0 && gc.getArpParts().get(i).getInstrument() == gc.getMelodyPart()
							.getInstrument()) {
						if (counter > arr.getSections().size() / 2) {
							copiedCPhrases.add(fillArpFromPart(gc.getArpParts().get(i),
									actualProgression, sec.getMeasures()));
						} else {
							copiedCPhrases.add(emptyCPhrase.copy());
						}
					} else {
						if (rand.nextInt(100) < sec.getArpChance()) {
							copiedCPhrases.add(fillArpFromPart(gc.getArpParts().get(i),
									actualProgression, sec.getMeasures()));
						} else {
							copiedCPhrases.add(emptyCPhrase.copy());
						}
					}

				}
				sec.setArps(copiedCPhrases);
			}

			if (!gc.getDrumParts().isEmpty()) {
				List<Phrase> copiedPhrases = new ArrayList<>();
				for (int i = 0; i < gc.getDrumParts().size(); i++) {
					rand.setSeed(mainGeneratorSeed + 300 + gc.getDrumParts().get(i).getOrder());
					if (rand.nextInt(100) < sec.getDrumChance()) {
						copiedPhrases.add(fillDrumsFromPart(gc.getDrumParts().get(i),
								actualProgression, sec.getMeasures()));
					} else {
						copiedPhrases.add(emptyPhrase.copy());
					}
				}
				sec.setDrums(copiedPhrases);
			}

		}
		System.out.println("Added phrases/cphrases to sections..");

		for (Section sec : arr.getSections()) {
			if (!gc.getMelodyPart().isMuted()) {
				Phrase mp = sec.getMelody();
				mp.setStartTime(mp.getStartTime() + sec.getStartTime());
				melody.add(mp);
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
				drumParts.get(i).addPhrase(p);
			}
			if (gc.getChordParts().size() > 0) {
				CPhrase cscp = sec.getChordSlash();
				cscp.setStartTime(cscp.getStartTime() + sec.getStartTime());
				chordSlash.addCPhrase(cscp);
			}

		}
		System.out.println("Added sections to parts..");
		if (!gc.getMelodyPart().isMuted()) {
			score.add(melody);
		}
		if (!gc.getBassPart().isMuted()) {
			score.add(bassRoots);
		}

		for (int i = 0; i < gc.getChordParts().size(); i++) {
			if (!gc.getChordParts().get(i).isMuted()) {
				score.add(chordParts.get(i));
			}

		}

		for (int i = 0; i < gc.getArpParts().size(); i++) {
			if (!gc.getArpParts().get(i).isMuted()) {
				score.add(arpParts.get(i));
			}
		}

		for (int i = 0; i < gc.getDrumParts().size(); i++) {
			if (!gc.getDrumParts().get(i).isMuted()) {
				score.add(drumParts.get(i));
			}
		}
		if (gc.getChordParts().size() > 0) {
			score.add(chordSlash);
		}

		System.out.println("Added parts to score..");

		score.setTempo(gc.getBpm());

		for (Part p : score.getPartArray()) {
			if (p.getHighestPitch() <= 0) {
				System.out.println(
						"Removing inst: " + p.getInstrument() + ", in part: " + p.getTitle());
				score.removePart(p);
			}
		}

		// write midi without log
		PrintStream originalStream = System.out;

		PrintStream dummyStream = new PrintStream(new OutputStream() {
			public void write(int b) {
				// NO-OP
			}
		});
		System.setOut(dummyStream);

		Write.midi(score, fileName);
		System.setOut(originalStream);

		// view midi
		if (DISPLAY_SCORE) {
			List<Part> partsToRemove = new ArrayList<>();
			for (Object p : score.getPartList()) {
				Part part = (Part) p;
				if (part.getTitle().equalsIgnoreCase("MainDrums")) {
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
			View.pianoRoll(score);
		}
		System.out.println("********Viewing midi seed: " + mainGeneratorSeed + "************* ");
	}

	private CPhrase fillChordSlash(List<int[]> actualProgression, int measures) {
		CPhrase chordSlashCPhrase = new CPhrase();
		Random chordSlashGenerator = new Random(gc.getRandomSeed() + 2);
		for (int i = 0; i < measures; i++) {
			// fill slash chord slashes
			for (int j = 0; j < actualProgression.size(); j++) {
				// pick random chord, take first/root pitch
				boolean isChordSlash = chordSlashGenerator.nextInt(100) < gc.getChordSlashChance();
				int slashChord = chordSlashGenerator.nextInt(6) + 1;
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
		Mod.transpose(chordSlashCPhrase, -24 + gc.getTranspose());
		chordSlashCPhrase.setStartTime(START_TIME_DELAY);
		return chordSlashCPhrase;


	}

	private CPhrase fillBassRoots(List<int[]> generatedRootProgression, int measures) {
		CPhrase cphraseBassRoot = new CPhrase();
		for (int i = 0; i < measures; i++) {
			for (int j = 0; j < generatedRootProgression.size(); j++) {
				Random bassDynamics = new Random(gc.getRandomSeed());
				int velSpace = gc.getBassPart().getVelocityMax()
						- gc.getBassPart().getVelocityMin();
				if (gc.getBassPart().isUseRhythm()) {
					int seed = (int) gc.getRandomSeed();
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
		Mod.transpose(cphraseBassRoot, -24 + gc.getTranspose());
		cphraseBassRoot.setStartTime(START_TIME_DELAY);
		return cphraseBassRoot;

	}

	private Phrase fillMelody(List<int[]> actualProgression, List<int[]> generatedRootProgression,
			int measures) {
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
					measures);
			Vector<Note> fullMelody = convertMelodySkeletonToFullMelody(skeletonNotes);
			melodyPhrase.addNoteList(fullMelody, true);
		}
		Mod.transpose(melodyPhrase, gc.getTranspose());
		melodyPhrase.setStartTime(START_TIME_DELAY);
		return melodyPhrase;
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

	private CPhrase fillChordsFromPart(ChordPart cp, List<int[]> actualProgression, int measures) {
		int mainGeneratorSeed = (int) gc.getRandomSeed();
		CPhrase cpr = new CPhrase();
		for (int i = 0; i < measures; i++) {
			Random transitionGenerator = new Random(mainGeneratorSeed + 1);

			// fill chords
			for (int j = 0; j < actualProgression.size(); j++) {

				Random velocityGenerator = new Random(cp.getPatternSeed() + j);
				int velocity = velocityGenerator.nextInt(cp.getVelocityMax() - cp.getVelocityMin())
						+ cp.getVelocityMin();

				boolean transition = transitionGenerator.nextInt(100) < cp.getTransitionChance();
				int transChord = (transitionGenerator.nextInt(100) < cp.getTransitionChance())
						? (j + 1) % actualProgression.size()
						: j;

				// random = use generated split with potential to transition to 2nd chord early
				// otherwise = use pattern within single chord

				boolean silent = false;

				if (cp.getChordSpanFill() != ChordSpanFill.ALL) {
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

				if (cp.getPattern() == RhythmPattern.RANDOM) {
					double splitTime = gc.getChordGenSettings().isUseSplit()
							? cp.getTransitionSplit()
							: DEFAULT_CHORD_SPLIT;

					double duration1 = progressionDurations.get(j) * splitTime / 1000.0;
					double duration2 = progressionDurations.get(j) - duration1;
					if (transition) {
						cpr.addChord(
								MidiUtils.convertChordToLength(actualProgression.get(j),
										cp.getChordNotesStretch(), cp.isStretchEnabled()),
								duration1, velocity);
						cpr.addChord(
								MidiUtils.convertChordToLength(actualProgression.get(transChord),
										cp.getChordNotesStretch(), cp.isStretchEnabled()),
								duration2, velocity);
					} else {
						cpr.addChord(
								MidiUtils.convertChordToLength(actualProgression.get(j),
										cp.getChordNotesStretch(), cp.isStretchEnabled()),
								progressionDurations.get(j), velocity);
					}

				} else {
					double duration = progressionDurations.get(j) / MAXIMUM_PATTERN_LENGTH;
					List<Integer> pattern = cp.getPattern()
							.getPatternByLength(MAXIMUM_PATTERN_LENGTH);
					for (int p = 0; p < pattern.size(); p++) {
						if (pattern.get(p) > 0) {
							cpr.addChord(
									MidiUtils.convertChordToLength(actualProgression.get(j),
											cp.getChordNotesStretch(), cp.isStretchEnabled()),
									duration, velocity);
						} else {
							cpr.addChord(new int[] { Integer.MIN_VALUE }, duration, velocity);
						}
					}

				}
			}
		}

		// transpose
		int extraTranspose = gc.getChordGenSettings().isUseTranspose() ? cp.getTranspose() : 0;
		Mod.transpose(cpr, -12 + gc.getTranspose() + extraTranspose);

		// delay
		double additionalDelay = 0;
		if (gc.getChordGenSettings().isUseDelay()) {
			additionalDelay = (cp.getDelay() / 1000.0);
		}
		cpr.setStartTime(START_TIME_DELAY + additionalDelay);

		// chord strum
		if (gc.getChordGenSettings().isUseStrum()) {
			for (int i = 0; i < gc.getChordParts().size(); i++) {
				if (gc.getChordParts().get(i).getPattern() == RhythmPattern.RANDOM) {
					cpr.flam(gc.getChordParts().get(i).getStrum() / 1000.0);
				} else {
					cpr.flam(10 / 1000.0);
				}
			}
		}

		return cpr;
	}

	private CPhrase fillArpFromPart(ArpPart ap, List<int[]> actualProgression, int measures) {

		CPhrase arpCPhrase = new CPhrase();

		Map<String, List<Integer>> arpMap = generateArpMap(ap.getPatternSeed(),
				ap.equals(gc.getArpParts().get(0)), ap);

		List<Integer> arpPattern = arpMap.get(ARP_PATTERN_KEY);
		List<Integer> arpOctavePattern = arpMap.get(ARP_OCTAVE_KEY);
		List<Integer> arpPausesPattern = arpMap.get(ARP_PAUSES_KEY);

		int repeatedArpsPerChord = ap.getHitsPerPattern() * ap.getPatternRepeat();

		double longestChord = progressionDurations.stream().max((e1, e2) -> Double.compare(e1, e2))
				.get();
		for (int i = 0; i < measures; i++) {
			int chordSpanPart = 0;

			Random velocityGenerator = new Random(ap.getPatternSeed());
			for (int j = 0; j < actualProgression.size(); j++) {

				double chordDurationArp = longestChord / ((double) repeatedArpsPerChord);
				int[] chord = MidiUtils.convertChordToLength(actualProgression.get(j),
						ap.getChordNotesStretch(), ap.isStretchEnabled());
				double durationNow = 0;
				for (int p = 0; p < repeatedArpsPerChord; p++) {

					int velocity = velocityGenerator.nextInt(
							ap.getVelocityMax() - ap.getVelocityMin()) + ap.getVelocityMin();

					Integer k = partOfList(chordSpanPart, ap.getChordSpan(), arpPattern).get(p);

					int octaveAdjustment = (k < 2) ? -12 : ((k < 6) ? 0 : 12);

					int pitch = chord[k % chord.length] + octaveAdjustment
							+ partOfList(chordSpanPart, ap.getChordSpan(), arpOctavePattern).get(p);
					if (partOfList(chordSpanPart, ap.getChordSpan(), arpPausesPattern)
							.get(p) == 0) {
						pitch = Integer.MIN_VALUE;
					}
					if (ap.getChordSpanFill() != ChordSpanFill.ALL) {
						if ((ap.getChordSpanFill() == ChordSpanFill.EVEN) && (j % 2 != 0)) {
							pitch = Integer.MIN_VALUE;
						}
						if ((ap.getChordSpanFill() == ChordSpanFill.ODD) && (j % 2 == 0)) {
							pitch = Integer.MIN_VALUE;
						}
					}
					if (durationNow + chordDurationArp > progressionDurations.get(j)) {
						arpCPhrase.addChord(new int[] { pitch },
								progressionDurations.get(j) - durationNow, velocity);
						break;
					} else {
						arpCPhrase.addChord(new int[] { pitch }, chordDurationArp, velocity);
					}
					durationNow += chordDurationArp;
				}
				chordSpanPart++;
				if (chordSpanPart >= ap.getChordSpan()) {
					chordSpanPart = 0;
				}
			}
		}
		int extraTranspose = ARP_SETTINGS.isUseTranspose() ? ap.getTranspose() : 0;
		Mod.transpose(arpCPhrase, -24 + gc.getTranspose() + extraTranspose);

		double additionalDelay = 0;
		/*if (ARP_SETTINGS.isUseDelay()) {
			additionalDelay = (gc.getArpParts().get(i).getDelay() / 1000.0);
		}*/
		arpCPhrase.setStartTime(START_TIME_DELAY + additionalDelay);
		return arpCPhrase;
	}


	private Phrase fillDrumsFromPart(DrumPart dp, List<int[]> actualProgression, int measures) {
		Phrase drumPhrase = new Phrase();

		int chordsCount = actualProgression.size();

		List<Integer> drumPattern = generateDrumPatternFromPart(dp);
		List<Integer> drumVelocityPattern = generateDrumVelocityPatternFromPart(dp);
		// bar iter
		for (int pieceSize = 0; pieceSize < measures; pieceSize++) {
			// exceptions are generated the same for each bar, but differently for each pattern within bar (if there is more than 1)
			Random exceptionGenerator = new Random(dp.getPatternSeed() + dp.getOrder());
			int chordSpan = dp.getChordSpan();
			// chord iter
			for (int j = 0; j < chordsCount; j += chordSpan) {
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
					if (drum < 0 && dp.isVelocityPattern()) {
						velocity = (velocity * 5) / 10;
						pitch = dp.getInstrument();
					}

					double swingDuration = drumDuration * (swingPercentAmount / ((double) 50.0));
					swingPercentAmount = 100 - swingPercentAmount;

					boolean exception = exceptionGenerator.nextInt(100) < dp.getExceptionChance();
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

	public void applyRuleToMelody(Note[] melody, Consumer<Note[]> melodyRule) {
		melodyRule.accept(melody);
	}

	public Note[] deepCopyNotes(Note[] originals, int[] chord, Random melodyGenerator) {
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

	public Map<String, List<Integer>> generateArpMap(int mainGeneratorSeed, boolean needToReport,
			ArpPart ap) {
		Random mainGenerator = new Random(mainGeneratorSeed);

		Random uiGenerator1arpCount = new Random(mainGenerator.nextInt());
		Random uiGenerator2arpPattern = new Random(mainGenerator.nextInt());
		Random uiGenerator3arpOctave = new Random(mainGenerator.nextInt());
		Random uiGenerator4arpPauses = new Random(mainGenerator.nextInt());

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
			Collections.rotate(arpPausesPattern, ap.getPatternShift());
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
		arpPattern = arpPattern.subList(0, ap.getHitsPerPattern());
		arpOctavePattern = arpOctavePattern.subList(0, ap.getHitsPerPattern());
		arpPausesPattern = arpPausesPattern.subList(0, ap.getHitsPerPattern());

		if (needToReport) {
			System.out.println("Arp count: " + ap.getHitsPerPattern());
			System.out.println("Arp pattern: " + arpPattern.toString());
			System.out.println("Arp octaves: " + arpOctavePattern.toString());
		}
		System.out.println("Arp pauses : " + arpPausesPattern.toString());

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

	public List<Integer> generateDrumPatternFromPart(DrumPart dp) {
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
		System.out
				.println("Drum pattern for " + dp.getInstrument() + " : " + drumPattern.toString());
		return drumPattern;
	}

	public List<Integer> generateDrumVelocityPatternFromPart(DrumPart dp) {
		Random uiGenerator1drumVelocityPattern = new Random(dp.getPatternSeed() + dp.getOrder());
		List<Integer> drumVelocityPattern = new ArrayList<>();

		for (int j = 0; j < dp.getHitsPerPattern(); j++) {
			int velocityRange = dp.getVelocityMax() - dp.getVelocityMin();

			int velocity = uiGenerator1drumVelocityPattern.nextInt(velocityRange)
					+ dp.getVelocityMin();

			drumVelocityPattern.add(velocity);
		}
		System.out.println("Drum velocity pattern for " + dp.getInstrument() + " : "
				+ drumVelocityPattern.toString());
		return drumVelocityPattern;
	}
}
