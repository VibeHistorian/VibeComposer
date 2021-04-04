
/*if (generatedPitch != MidiUtils.maX(generatedPitch, maxAllowedScaleNotes)) {
	// set positive if >maxAllowedScaleNotes, because in next step it
	// will be modded to fall in 0-maxAllowedScaleNotes range and lose
	// 12 pitch value
	addSubtractOctave = (generatedPitch > maxAllowedScaleNotes) ? 12 : -12;
	// for guaranteed non negative mods in java
	generatedPitch = (generatedPitch + 10 * (maxAllowedScaleNotes + 1))
			% (maxAllowedScaleNotes + 1);
	if (direction == 1
			&& chordScale.get(generatedPitch) + addSubtractOctave < previousPitch) {
		addSubtractOctave += 12;
	} else if (direction == -1
			&& chordScale.get(generatedPitch) + addSubtractOctave > previousPitch) {
		addSubtractOctave -= 12;
	}
}*/
package org.vibehistorian.midimasterpiece.midigenerator;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils.PARTS;

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
	
	
	/*private static final String[] DRUM_MAP_KEYS = { "KICK", "SNARE", "OPENHAT", "CLOSEDHAT",
	"CYMBAL" };*/
	
	public static final int OPENHAT_CHANCE = 15;
	
	public static List<DrumPart> DRUM_PARTS = new ArrayList<>();
	public static double startTimeDelay = 0.5;
	
	private static final String ARP_PATTERN_KEY = "ARP_PATTERN";
	private static final String ARP_OCTAVE_KEY = "ARP_OCTAVE";
	private static final String ARP_PAUSES_KEY = "ARP_PAUSES";
	
	public static EnumMap<PARTS, Integer> PARTS_INSTRUMENT_MAP = new EnumMap<>(PARTS.class);
	
	private static int MAXIMUM_ARP_COUNT = 8;
	
	public static boolean MINOR_SONG = false;
	public static int MAX_JUMP = 4;
	public static int MAX_EXCEPTIONS = 1;
	public static int PIECE_LENGTH = 4;
	public static int FIRST_CHORD = 0;
	public static int LAST_CHORD = 0;
	
	public static int MELODY_PAUSE_CHANCE = 20;
	
	public static boolean RANDOM_CHORD_NOTE = true;
	public int PROGRESSION_LENGTH = 4;
	public int maxAllowedScaleNotes = 7;
	public static boolean FIXED_LENGTH = false;
	public static double MAIN_BPM = 80.0;
	
	public static boolean FIRST_NOTE_FROM_CHORD = true;
	public static Integer SPICE_CHANCE = 0;
	public static boolean SPICE_ALLOW_DIM_AUG = false;
	public static int CHORD_FLAM = 0;
	public static int CHORD_TRANSITION_CHANCE = 0;
	public static int CHORD_SLASH_CHANCE = 0;
	public static int SECOND_CHORD_FLAM = 0;
	
	public static int ARPS_PER_CHORD = 3;
	public static boolean ARP_RANDOM_SHUFFLE = true;
	public static boolean RANDOM_ARPS_PER_CHORD = false;
	public static boolean ARP_PATTERN_REPEAT = true;
	public static boolean ARP_ALLOW_PAUSES = true;
	public static int ARP_PAUSE_CHANCE = 25;
	public static int SECOND_ARP_PAUSE_CHANCE = 50;
	
	public static int SECOND_ARP_COUNT_MULTIPLIER = 2;
	public static int SECOND_ARP_OCTAVE_ADJUST = 0;
	
	public double[] MELODY_DUR_ARRAY = { Durations.QUARTER_NOTE, Durations.DOTTED_EIGHTH_NOTE,
			Durations.EIGHTH_NOTE, Durations.SIXTEENTH_NOTE };
	public double[] MELODY_DUR_CHANCE = { 0.3, 0.6, 1.0, 1.0 };
	
	public double[] CHORD_DUR_ARRAY = { Durations.WHOLE_NOTE, Durations.DOTTED_HALF_NOTE,
			Durations.HALF_NOTE, Durations.QUARTER_NOTE };
	public double[] CHORD_DUR_CHANCE = { 0.0, 0.20, 0.80, 1.0 };
	
	public static int TRANSPOSE_SCORE = 0;
	
	public static List<Integer> MELODY_SCALE = MidiUtils.cMajScale4;
	
	public List<Double> progressionDurations = new ArrayList<>();
	
	public static List<Integer> userChords = new ArrayList<>();
	public static List<Double> userChordsDurations = new ArrayList<>();
	public static Integer USER_MELODY_SEED = 0;
	
	public int samePitchCount = 0;
	public int previousPitch = 0;
	
	public static List<Integer> chordInts = new ArrayList<>();
	
	public MelodyGenerator() {
		
	}
	
	public Note generateNote(int[] chord, boolean isAscDirection, List<Integer> chordScale,
			Note previousNote, Random generator, double durationLeft) {
		// int randPitch = generator.nextInt(8);
		int direction = (isAscDirection) ? 1 : -1;
		double dur = MidiUtils.pickDurationWeightedRandom(generator, durationLeft, MELODY_DUR_ARRAY,
				MELODY_DUR_CHANCE, Durations.SIXTEENTH_NOTE);
		boolean isPause = (generator.nextInt(100) < MELODY_PAUSE_CHANCE);
		if (previousNote == null) {
			int[] firstChord = chord;
			int chordNote = (RANDOM_CHORD_NOTE) ? generator.nextInt(firstChord.length) : 0;
			
			int chosenPitch = 60 + (firstChord[chordNote] % 12);
			
			previousPitch = chordScale.indexOf(new Integer(chosenPitch));
			if (previousPitch == -1) {
				System.out.println("ERROR PITCH -1 for: " + chosenPitch);
				previousPitch = chordScale.indexOf(new Integer(chosenPitch + 1));
				if (previousPitch == -1) {
					System.out.println("NOT EVEN +1 pitch exists for " + chosenPitch + "!");
				}
			}
			
			//System.out.println(firstChord[chordNote] + " > from first chord");
			if (isPause) {
				return new Note(Integer.MIN_VALUE, dur);
			}
			
			return new Note(chosenPitch, dur, 70 + generator.nextInt(20));
		}
		
		int change = generator.nextInt(MAX_JUMP);
		// weighted against same note
		if (change == 0) {
			change = generator.nextInt((MAX_JUMP + 1) / 2);
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
		
		if (false) {
			System.out.println(chordScale.get(generatedPitch) + ",\t Dir: " + direction
					+ ",\t Prev.: " + previousPitch + ",\t Cur.: " + generatedPitch + ",\t Chg: "
					+ change + ",\t Dur: " + dur);
		}
		previousPitch = generatedPitch;
		if (isPause) {
			return new Note(Integer.MIN_VALUE, dur);
		}
		return new Note(chordScale.get(generatedPitch), dur, 70 + generator.nextInt(20));
		
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
			next.add(new Integer(LAST_CHORD));
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
			
			//SPICE CHANCE
			if (generator.nextInt(100) < SPICE_CHANCE) {
				int spiceInt = 10;
				int spiceSelectPow = generator.nextInt(4) + 1;
				
				if (!SPICE_ALLOW_DIM_AUG && spiceSelectPow < 3) {
					// move to maj/min 7th
					spiceSelectPow += 2;
				}
				
				if (chordInt < 10) {
					spiceSelectPow++;
				}
				
				
				/*if ((chordInt > 10) && (spiceSelectPow == 3)) {
					spiceSelectPow++;
				}*/
				spiceResult = (int) Math.pow(spiceInt, spiceSelectPow);
				chordInt *= spiceResult;
			}
			
			chordInts.add(chordInt);
			int[] mappedChord = MidiUtils.mappedChord(chordInt);
			if (MINOR_SONG) {
				mappedChord = MidiUtils.transposeChord(mappedChord, Mod.MAJOR_SCALE,
						Mod.MINOR_SCALE);
			}
			
			
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
		
		Note previousNote = (FIRST_NOTE_FROM_CHORD) ? null : previousChordsNote;
		List<Note> notes = new ArrayList<>();
		
		int exceptionsLeft = MAX_EXCEPTIONS;
		
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
			Note transposedNote = new Note(note.getPitch(), note.getRhythmValue());
			notes.add(transposedNote);
		}
		return notes.toArray(new Note[0]);
	}
	
	public void generateMasterpiece(int mainGeneratorSeed, String fileName,
			int melodyProgramChange) {
		System.out.println("--- GENERATING MASTERPIECE.. ---");
		
		
		Score score = new Score("MainScore", 120);
		Part mainMelody = new Part("MainMelody", melodyProgramChange, 0);
		Part mainChords = new Part("MainChords", 53, 1);
		Part chordsSecond = new Part("ChordsSecond", GOBLIN, 2);
		Part chordsThirdArp = new Part("ChordsThird", XYLOPHONE, 3);
		Part chordsFourthArp = new Part("ChordsFourth", EPIANO, 4);
		Part bassChordRoots = new Part("BassChordRoots", BASS, 5);
		Part drums = new Part("MainDrums", PIANO, 9);
		Part chordSlash = new Part("ChordSlash", PIANO, 6);
		
		EnumMap<PARTS, Part> enumMap = new EnumMap<>(PARTS.class);
		enumMap.put(PARTS.MELODY, mainMelody);
		enumMap.put(PARTS.CHORDS1, mainChords);
		enumMap.put(PARTS.CHORDS2, chordsSecond);
		enumMap.put(PARTS.CHORDS3, chordsThirdArp);
		enumMap.put(PARTS.CHORDS4, chordsFourthArp);
		enumMap.put(PARTS.BASSROOTS, bassChordRoots);
		enumMap.put(PARTS.DRUMS, drums);
		
		
		for (PARTS part : PARTS_INSTRUMENT_MAP.keySet()) {
			Integer instrumentChoice = PARTS_INSTRUMENT_MAP.get(part);
			enumMap.get(part).setInstrument(instrumentChoice);
			if (part == PARTS.CHORDS1) {
				chordSlash.setInstrument(instrumentChoice);
			}
		}
		
		// Generate chords..
		List<int[]> generatedRootProgression = generateChordProgression(mainGeneratorSeed,
				FIXED_LENGTH, 4 * Durations.HALF_NOTE);
		if (!userChordsDurations.isEmpty()) {
			progressionDurations = userChordsDurations;
		}
		
		List<Boolean> directionProgression = generateMelodyDirectionsFromChordProgression(
				generatedRootProgression);
		System.out.println(directionProgression.toString());
		List<int[]> actualProgression = MidiUtils.squishChordProgression(generatedRootProgression);
		
		// Generate arps..
		Map<String, List<Integer>> arpMap = generateArpMap(mainGeneratorSeed, true,
				ARP_PAUSE_CHANCE);
		Map<String, List<Integer>> arp2Map = generateArpMap(mainGeneratorSeed, false,
				SECOND_ARP_PAUSE_CHANCE);
		
		MELODY_SCALE = (MINOR_SONG) ? MidiUtils.cMinScale4 : MidiUtils.cMajScale4;
		
		// Generate melody and fill other parts..
		Note[] pair024 = null;
		Note[] pair15 = null;
		Random melodyGenerator = new Random();
		if (USER_MELODY_SEED != null && USER_MELODY_SEED != 0) {
			melodyGenerator.setSeed(USER_MELODY_SEED);
		} else {
			melodyGenerator.setSeed(mainGeneratorSeed);
		}
		
		CPhrase cphrase = new CPhrase();
		Phrase melody = new Phrase();
		CPhrase arpCPhrase = new CPhrase();
		CPhrase arp2CPhrase = new CPhrase();
		CPhrase cphraseBassRoot = new CPhrase();
		CPhrase chordSlashCPhrase = new CPhrase();
		
		for (int i = 0; i < PIECE_LENGTH; i++) {
			Note previousChordsNote = null;
			
			Random exceptionGenerator = new Random(mainGeneratorSeed + 1);
			Random chordSlashGenerator = new Random(mainGeneratorSeed + 2);
			// fill chords
			for (int j = 0; j < actualProgression.size(); j++) {
				boolean exception = exceptionGenerator.nextInt(100) < CHORD_TRANSITION_CHANCE;
				if (exception) {
					double duration1 = progressionDurations.get(j) * 5 / 8;
					double duration2 = progressionDurations.get(j) * 3 / 8;
					int excChord = (exceptionGenerator.nextInt(100) < CHORD_TRANSITION_CHANCE) ? j
							: (j + 1) % actualProgression.size();
					cphrase.addChord(actualProgression.get(j), duration1);
					cphrase.addChord(actualProgression.get(excChord), duration2);
				} else {
					cphrase.addChord(actualProgression.get(j), progressionDurations.get(j));
				}
				
				// pick random chord, take first/root pitch
				boolean isChordSlash = chordSlashGenerator.nextInt(100) < CHORD_SLASH_CHANCE;
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
			
			// fill bass roots
			for (int j = 0; j < generatedRootProgression.size(); j++) {
				cphraseBassRoot.addChord(new int[] { generatedRootProgression.get(j)[0] },
						progressionDurations.get(j));
			}
			
			// generate+fill melody
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
				melody.addNoteList(generatedMelody);
			}
			
			
		}
		
		// fill arp
		fillArpCPhrase(arpCPhrase, arpMap, actualProgression, 1);
		fillArpCPhrase(arp2CPhrase, arp2Map, actualProgression, SECOND_ARP_COUNT_MULTIPLIER);
		
		Mod.transpose(cphraseBassRoot, -24 + TRANSPOSE_SCORE);
		Mod.transpose(cphrase, -12 + TRANSPOSE_SCORE);
		Mod.transpose(arpCPhrase, -12 + TRANSPOSE_SCORE);
		Mod.transpose(arp2CPhrase, -24 + TRANSPOSE_SCORE + SECOND_ARP_OCTAVE_ADJUST);
		Mod.transpose(melody, TRANSPOSE_SCORE);
		Mod.transpose(chordSlashCPhrase, -24 + TRANSPOSE_SCORE);
		
		
		melody.setDynamic(80);
		cphrase.setDynamic(80);
		arpCPhrase.setDynamic(70);
		arp2CPhrase.setDynamic(65);
		
		melody.setStartTime(startTimeDelay);
		cphrase.setStartTime(startTimeDelay);
		arpCPhrase.setStartTime(startTimeDelay);
		arp2CPhrase.setStartTime(startTimeDelay);
		cphraseBassRoot.setStartTime(startTimeDelay);
		chordSlashCPhrase.setStartTime(startTimeDelay);
		
		CPhrase cphrase2 = cphrase.copy();
		
		if (CHORD_FLAM > 0) {
			cphrase.flam(CHORD_FLAM / 1000.0d);
		}
		
		
		if (SECOND_CHORD_FLAM > 0) {
			cphrase2.flam(SECOND_CHORD_FLAM / 1000.0d);
		}
		
		mainMelody.addPhrase(melody);
		mainChords.addCPhrase(cphrase);
		chordsSecond.addCPhrase(cphrase2);
		chordsThirdArp.addCPhrase(arpCPhrase);
		chordsFourthArp.addCPhrase(arp2CPhrase);
		bassChordRoots.addCPhrase(cphraseBassRoot);
		chordSlash.addCPhrase(chordSlashCPhrase);
		
		for (PARTS part : PARTS_INSTRUMENT_MAP.keySet()) {
			if (part != PARTS.DRUMS) {
				score.add(enumMap.get(part));
			}
		}
		score.add(chordSlash);
		
		// fill drums		
		if (PARTS_INSTRUMENT_MAP.containsKey(PARTS.DRUMS) && DRUM_PARTS.size() > 0) {
			Map<Integer, List<Integer>> drumMap = generateDrumMap();
			Map<Integer, List<Integer>> drumVelocityMap = generateDrumVelocityMap();
			fillDrumsCPhrases(score, drumMap, drumVelocityMap, actualProgression.size());
		}
		
		score.setTempo(MAIN_BPM);
		
		for (Part p : score.getPartArray()) {
			if (p.getHighestPitch() <= 0) {
				System.out.println("Removing inst: " + p.getInstrument());
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
		View.pianoRoll(score);
		System.out.println("********Viewing midi seed: " + mainGeneratorSeed + "************* ");
	}
	
	private List<Boolean> generateMelodyDirectionsFromChordProgression(
			List<int[]> generatedRootProgression) {
		
		List<Boolean> ascDirectionList = new ArrayList<>();
		
		for (int i = 0; i < generatedRootProgression.size(); i++) {
			int current = generatedRootProgression.get(i)[0];
			int next = generatedRootProgression.get((i + 1) % generatedRootProgression.size())[0];
			ascDirectionList.add(new Boolean(current <= next));
		}
		
		return ascDirectionList;
	}
	
	private void fillArpCPhrase(CPhrase arpCPhrase, Map<String, List<Integer>> arpMap,
			List<int[]> actualProgression, int repeating) {
		
		List<Integer> arpPattern = arpMap.get(ARP_PATTERN_KEY);
		List<Integer> arpOctavePattern = arpMap.get(ARP_OCTAVE_KEY);
		List<Integer> arpPausesPattern = arpMap.get(ARP_PAUSES_KEY);
		
		int repeatedArpsPerChord = ARPS_PER_CHORD * repeating;
		
		for (int i = 0; i < PIECE_LENGTH; i++) {
			for (int j = 0; j < actualProgression.size(); j++) {
				double chordDurationArp = progressionDurations.get(j)
						/ ((double) repeatedArpsPerChord);
				int[] chord = actualProgression.get(j);
				
				for (int r = 0; r < repeating; r++) {
					for (int p = 0; p < ARPS_PER_CHORD; p++) {
						Integer k = arpPattern.get(p);
						
						int octaveAdjustment = (k < 2) ? -12 : ((k < 6) ? 0 : 12);
						
						int pitch = chord[k % chord.length] + octaveAdjustment
								+ arpOctavePattern.get(p);
						if (arpPausesPattern.get(p) == 0) {
							pitch = Integer.MIN_VALUE;
						}
						
						arpCPhrase.addChord(new int[] { pitch }, chordDurationArp);
					}
				}
			}
		}
	}
	
	private void fillDrumsCPhrases(Score scr, Map<Integer, List<Integer>> drumPatternMap,
			Map<Integer, List<Integer>> drumVelocityPatternMap, int chordsCount) {
		
		Part[] parts = new Part[DRUM_PARTS.size()];
		Phrase[] drumPhrases = new Phrase[DRUM_PARTS.size()];
		for (int i = 0; i < drumPhrases.length; i++) {
			drumPhrases[i] = new Phrase();
			parts[i] = new Part("MainDrums", 0, 9);
		}
		// bar iter
		for (int pieceSize = 0; pieceSize < PIECE_LENGTH; pieceSize++) {
			// drum parts iter
			for (int i = 0; i < DRUM_PARTS.size(); i++) {
				// exceptions are generated the same for each bar, but differently for each pattern within bar (if there is more than 1)
				Random exceptionGenerator = new Random(DRUM_PARTS.get(i).getPatternSeed() + i);
				int chordSpan = DRUM_PARTS.get(i).getChordSpan();
				// chord iter
				for (int j = 0; j < chordsCount; j += chordSpan) {
					double patternDurationTotal = 0.0;
					for (int k = 0; k < chordSpan; k++) {
						patternDurationTotal += (progressionDurations.size() > j + k)
								? progressionDurations.get(j + k)
								: 0.0;
					}
					
					double drumDuration = patternDurationTotal
							/ DRUM_PARTS.get(i).getHitsPerPattern();
					List<Integer> drumPattern = drumPatternMap.get(i);
					if (!DRUM_PARTS.get(i).isVelocityPattern()
							&& drumPattern.indexOf(DRUM_PARTS.get(i).getPitch()) == -1) {
						continue;
					}
					List<Integer> drumVelocityPattern = drumVelocityPatternMap.get(i);
					for (int k = 0; k < drumPattern.size(); k++) {
						int drum = drumPattern.get(k);
						int velocity = drumVelocityPattern.get(k);
						int pitch = (drum >= 0) ? drum : Integer.MIN_VALUE;
						if (drum < 0 && DRUM_PARTS.get(i).isVelocityPattern()) {
							velocity = (velocity * 5) / 10;
							pitch = DRUM_PARTS.get(i).getPitch();
						}
						
						
						boolean exception = exceptionGenerator.nextInt(100) < DRUM_PARTS.get(i)
								.getExceptionChance();
						if (exception) {
							int secondVelocity = (velocity * 8) / 10;
							drumPhrases[i].addNote(new Note(pitch, drumDuration / 2, velocity));
							drumPhrases[i]
									.addNote(new Note(pitch, drumDuration / 2, secondVelocity));
						} else {
							drumPhrases[i].addNote(new Note(pitch, drumDuration, velocity));
						}
						
					}
				}
			}
		}
		
		for (int i = 0; i < DRUM_PARTS.size(); i++) {
			if (drumPhrases[i].length() == 0) {
				continue;
			}
			drumPhrases[i].setStartTime(
					startTimeDelay + (DRUM_PARTS.get(i).getSlideMiliseconds() / 1000.0));
			
			parts[i].addPhrase(drumPhrases[i]);
			scr.addPart(parts[i]);
		}
		
	}
	
	public void applyRuleToMelody(Note[] melody, Consumer<Note[]> melodyRule) {
		melodyRule.accept(melody);
	}
	
	public static final String[] INSTRUMENTS_NAMES = { "PIANO = 0", "BRIGHT_ACOUSTIC = 1",
			"ELECTRIC_GRAND = 2", "HONKYTONK = 3", "EPIANO = 4", "EPIANO2 = 5", "HARPSICHORD = 6",
			"CLAV = 7", "CELESTE = 8", "GLOCKENSPIEL = 9", "MUSIC_BOX = 10", "VIBRAPHONE = 11",
			"MARIMBA = 12", "XYLOPHONE = 13", "TUBULAR_BELL = 14", "NOTHING = 15", "ORGAN = 16",
			"ORGAN2 = 17", "ORGAN3 = 18", "CHURCH_ORGAN = 19", "REED_ORGAN = 20", "ACCORDION = 21",
			"HARMONICA = 22", "BANDNEON = 23", "NYLON_GUITAR = 24", "STEEL_GUITAR = 25",
			"JAZZ_GUITAR = 26", "CLEAN_GUITAR = 27", "MUTED_GUITAR = 28", "OVERDRIVE_GUITAR = 29",
			"DISTORTED_GUITAR = 30", "GUITAR_HARMONICS = 31", "ACOUSTIC_BASS = 32",
			"FINGERED_BASS = 33", "PICKED_BASS = 34", "FRETLESS_BASS = 35", "SLAP_BASS = 36",
			"SLAP_BASS_2 = 37", "SYNTH_BASS = 38", "SYNTH_BASS_2 = 39", "VIOLIN = 40", "VIOLA = 41",
			"CELLO = 42", "CONTRABASS = 43", "TREMOLO_STRINGS = 44", "PIZZICATO_STRINGS = 45",
			"HARP = 46", "TIMPANI = 47", "STRINGS = 48", "STRING_ENSEMBLE_2 = 49",
			"SYNTH_STRINGS = 50", "SLOW_STRINGS = 51", "AAH = 52", "OOH = 53", "SYNVOX = 54",
			"ORCHESTRA_HIT = 55", "TRUMPET = 56", "TROMBONE = 57", "TUBA = 58",
			"MUTED_TRUMPET = 59", "FRENCH_HORN = 60", "BRASS = 61", "SYNTH_BRASS = 62",
			"SYNTH_BRASS_2 = 63", "SOPRANO_SAX = 64", "ALTO_SAX = 65", "SAXOPHONE = 66",
			"BARITONE_SAX = 67", "OBOE = 68", "ENGLISH_HORN = 69", "BASSOON = 70", "CLARINET = 71",
			"PICCOLO = 72", "FLUTE = 73", "RECORDER = 74", "PAN_FLUTE = 75", "BOTTLE_BLOW = 76",
			"SHAKUHACHI = 77", "WHISTLE = 78", "OCARINA = 79", "GMSQUARE_WAVE = 80",
			"GMSAW_WAVE = 81", "SYNTH_CALLIOPE = 82", "CHIFFER_LEAD = 83", "CHARANG = 84",
			"SOLO_VOX = 85", "WHOKNOWS1 = 86", "WHOKNOWS2 = 87", "FANTASIA = 88", "WARM_PAD = 89",
			"POLYSYNTH = 90", "SPACE_VOICE = 91", "BOWED_GLASS = 92", "METAL_PAD = 93",
			"HALO_PAD = 94", "SWEEP_PAD = 95", "ICE_RAIN = 96", "SOUNDTRACK = 97", "CRYSTAL = 98",
			"ATMOSPHERE = 99", "BRIGHTNESS = 100", "GOBLIN = 101", "ECHO_DROPS = 102",
			"STAR_THEME = 103", "SITAR = 104", "BANJO = 105", "SHAMISEN = 106", "KOTO = 107",
			"KALIMBA = 108", "BAGPIPES = 109", "FIDDLE = 110", "SHANNAI = 111", "TINKLE_BELL = 112",
			"AGOGO = 113", "STEEL_DRUMS = 114", "WOODBLOCK = 115", "TAIKO = 116", "TOM = 117",
			"SYNTH_DRUM = 118", "REVERSE_CYMBAL = 119", "FRETNOISE = 120", "BREATHNOISE = 121",
			"NATURE = 122", "BIRD = 123", "TELEPHONE = 124", "HELICOPTER = 125", "APPLAUSE = 126",
			"GUNSHOT = 127" };
	
	public static final String[] NICE_INSTRUMENTS_NAMES = { "PIANO = 0", "BRIGHT_ACOUSTIC = 1",
			"HONKYTONK = 3", "EPIANO = 4", "EPIANO2 = 5", "HARPSICHORD = 6", "CLAV = 7",
			"CELESTE = 8", "MUSIC_BOX = 10", "VIBRAPHONE = 11", "MARIMBA = 12", "XYLOPHONE = 13",
			"TUBULAR_BELL = 14", "NOTHING = 15", "ORGAN = 16", "ORGAN2 = 17", "REED_ORGAN = 20",
			"ACCORDION = 21", "HARMONICA = 22", "BANDNEON = 23", "STEEL_GUITAR = 25",
			"CLEAN_GUITAR = 27", "ACOUSTIC_BASS = 32", "FINGERED_BASS = 33", "PICKED_BASS = 34",
			"FRETLESS_BASS = 35", "SLAP_BASS = 36", "VIOLIN = 40", "VIOLA = 41", "CELLO = 42",
			"CONTRABASS = 43", "TREMOLO_STRINGS = 44", "PIZZICATO_STRINGS = 45", "HARP = 46",
			"STRINGS = 48", "STRING_ENSEMBLE_2 = 49", "SLOW_STRINGS = 51", "AAH = 52", "OOH = 53",
			"SYNVOX = 54", "TRUMPET = 56", "TROMBONE = 57", "TUBA = 58", "FRENCH_HORN = 60",
			"BRASS = 61", "SOPRANO_SAX = 64", "ALTO_SAX = 65", "BARITONE_SAX = 67", "OBOE = 68",
			"ENGLISH_HORN = 69", "BASSOON = 70", "CLARINET = 71", "PICCOLO = 72", "FLUTE = 73",
			"RECORDER = 74", "PAN_FLUTE = 75", "BOTTLE_BLOW = 76", "SYNTH_CALLIOPE = 82",
			"SOLO_VOX = 85", "FANTASIA = 88", "SPACE_VOICE = 91", "BOWED_GLASS = 92",
			"METAL_PAD = 93", "HALO_PAD = 94", "SWEEP_PAD = 95", "ICE_RAIN = 96", "SOUNDTRACK = 97",
			"ATMOSPHERE = 99", "ECHO_DROPS = 102", "SITAR = 104", "BANJO = 105", "SHAMISEN = 106",
			"KOTO = 107", "AGOGO = 113", "TAIKO = 116" };
	
	public static final List<Integer> NICE_INSTRUMENTS_NUMBERS = Arrays
			.asList(NICE_INSTRUMENTS_NAMES).stream().map(e -> Integer.valueOf(e.split(" = ")[1]))
			.collect(Collectors.toList());
	
	public static final String[] DRUM_NAMES = { "KICK = 36", "SNARE = 38", "CLOSED_HH = 42",
			"CYMBAL = 53" };
	
	public static final List<Integer> DRUM_NAMES_NUMBERS = Arrays.asList(DRUM_NAMES).stream()
			.map(e -> Integer.valueOf(e.split(" = ")[1])).collect(Collectors.toList());
	
	public static final String[] DRUM_KITS = { "DRUMKIT0 = 0", "DRUMKIT1 = 1", "DRUMKIT2 = 2",
			"DRUMKIT3 = 3" };
	
	public static final List<Integer> DRUM_KIT_NUMBERS = Arrays.asList(DRUM_KITS).stream()
			.map(e -> Integer.valueOf(e.split(" = ")[1])).collect(Collectors.toList());
	
	public Note[] deepCopyNotes(Note[] originals, int[] chord, Random melodyGenerator) {
		Note[] copied = new Note[originals.length];
		for (int i = 0; i < originals.length; i++) {
			Note n = originals[i];
			copied[i] = new Note(n.getPitch(), n.getRhythmValue());
		}
		if (chord != null && melodyGenerator != null && FIRST_NOTE_FROM_CHORD) {
			Note n = generateNote(chord, true, MELODY_SCALE, null, melodyGenerator,
					Durations.HALF_NOTE);
			copied[0] = new Note(n.getPitch(), originals[0].getRhythmValue());
		}
		return copied;
	}
	
	public Map<String, List<Integer>> generateArpMap(int mainGeneratorSeed, boolean needToGenerate,
			int pauseChance) {
		
		Random mainGenerator = new Random(mainGeneratorSeed);
		
		Random uiGenerator1arpCount = new Random(mainGenerator.nextInt());
		Random uiGenerator2arpPattern = new Random(mainGenerator.nextInt());
		Random uiGenerator3arpOctave = new Random(mainGenerator.nextInt());
		Random uiGenerator4arpPauses = new Random(mainGenerator.nextInt());
		
		if (RANDOM_ARPS_PER_CHORD && needToGenerate) {
			ARPS_PER_CHORD = uiGenerator1arpCount.nextInt(MAXIMUM_ARP_COUNT - 1) + 2;
			//reduced chance of 5 or 7 but not eliminated
			if (ARPS_PER_CHORD == 5 || ARPS_PER_CHORD == 7) {
				ARPS_PER_CHORD = uiGenerator1arpCount.nextInt(MAXIMUM_ARP_COUNT - 1) + 2;
			}
		}
		
		int[] arpPatternArray = IntStream.range(0, MAXIMUM_ARP_COUNT).toArray();
		int[] arpOctaveArray = IntStream.iterate(0, e -> (e + 12) % 24).limit(MAXIMUM_ARP_COUNT * 2)
				.toArray();
		for (int i = 0; i < arpOctaveArray.length; i++) {
			//arpOctaveArray[i] -= 12;
		}
		List<Integer> arpPattern = Arrays.stream(arpPatternArray).boxed()
				.collect(Collectors.toList());
		if (ARP_PATTERN_REPEAT) {
			arpPattern.addAll(arpPattern);
		}
		List<Integer> arpPausesPattern = new ArrayList<>();
		if (ARP_ALLOW_PAUSES) {
			for (int i = 0; i < MAXIMUM_ARP_COUNT; i++) {
				if (uiGenerator4arpPauses.nextInt(100) < (pauseChance)) {
					arpPausesPattern.add(0);
				} else {
					arpPausesPattern.add(1);
				}
			}
		} else {
			for (int i = 0; i < MAXIMUM_ARP_COUNT; i++) {
				arpPausesPattern.add(1);
			}
		}
		List<Integer> arpOctavePattern = Arrays.stream(arpOctaveArray).boxed()
				.collect(Collectors.toList());
		if (ARP_RANDOM_SHUFFLE) {
			Collections.shuffle(arpPattern, uiGenerator2arpPattern);
			Collections.shuffle(arpOctavePattern, uiGenerator3arpOctave);
		}
		// always generate maximum, cut off however many are needed (support for seed randoms)
		arpPattern = arpPattern.subList(0, ARPS_PER_CHORD);
		arpOctavePattern = arpOctavePattern.subList(0, ARPS_PER_CHORD);
		arpPausesPattern = arpPausesPattern.subList(0, ARPS_PER_CHORD);
		
		Map<String, List<Integer>> arpMap = new HashMap<>();
		arpMap.put(ARP_PATTERN_KEY, arpPattern);
		arpMap.put(ARP_OCTAVE_KEY, arpOctavePattern);
		arpMap.put(ARP_PAUSES_KEY, arpPausesPattern);
		
		if (needToGenerate) {
			System.out.println("Arp count: " + ARPS_PER_CHORD);
			System.out.println("Arp pattern: " + arpPattern.toString());
			System.out.println("Arp octaves: " + arpOctavePattern.toString());
		}
		System.out.println("Arp pauses : " + arpPausesPattern.toString());
		
		
		return arpMap;
	}
	
	public Map<Integer, List<Integer>> generateDrumMap() {
		Map<Integer, List<Integer>> drumMap = new HashMap<>();
		
		for (int i = 0; i < DRUM_PARTS.size(); i++) {
			Random uiGenerator1drumPattern = new Random(DRUM_PARTS.get(i).getPatternSeed() + i);
			List<Integer> premadePattern = DRUM_PARTS.get(i).getPattern()
					.getPatternByLength(DRUM_PARTS.get(i).getHitsPerPattern());
			List<Integer> drumPattern = new ArrayList<>();
			for (int j = 0; j < DRUM_PARTS.get(i).getHitsPerPattern(); j++) {
				// if random pause or not present in pattern: pause
				if (uiGenerator1drumPattern.nextInt(100) < DRUM_PARTS.get(i).getPauseChance()
						|| !premadePattern.get(j).equals(1)) {
					drumPattern.add(-1);
				} else {
					if (DRUM_PARTS.get(i).getPitch() == 42
							&& uiGenerator1drumPattern.nextInt(100) < OPENHAT_CHANCE) {
						drumPattern.add(46);
					} else {
						drumPattern.add(DRUM_PARTS.get(i).getPitch());
					}
					
				}
			}
			Collections.rotate(drumPattern, DRUM_PARTS.get(i).getPatternRotation());
			System.out.println("Drum pattern for " + DRUM_PARTS.get(i).getPitch() + " : "
					+ drumPattern.toString());
			drumMap.put(i, drumPattern);
		}
		return drumMap;
	}
	
	public Map<Integer, List<Integer>> generateDrumVelocityMap() {
		
		Map<Integer, List<Integer>> drumVelocityMap = new HashMap<>();
		
		for (int i = 0; i < DRUM_PARTS.size(); i++) {
			Random uiGenerator1drumVelocityPattern = new Random(
					DRUM_PARTS.get(i).getPatternSeed() + i);
			List<Integer> drumVelocityPattern = new ArrayList<>();
			
			for (int j = 0; j < DRUM_PARTS.get(i).getHitsPerPattern(); j++) {
				int velocityRange = DRUM_PARTS.get(i).getVelocityMax()
						- DRUM_PARTS.get(i).getVelocityMin();
				
				int velocity = uiGenerator1drumVelocityPattern.nextInt(velocityRange)
						+ DRUM_PARTS.get(i).getVelocityMin();
				
				drumVelocityPattern.add(velocity);
			}
			System.out.println("Drum velocity pattern for " + DRUM_PARTS.get(i).getPitch() + " : "
					+ drumVelocityPattern.toString());
			drumVelocityMap.put(i, drumVelocityPattern);
		}
		return drumVelocityMap;
	}
}
