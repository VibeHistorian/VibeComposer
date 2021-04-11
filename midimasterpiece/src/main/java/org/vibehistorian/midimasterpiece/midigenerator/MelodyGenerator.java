
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
	public static List<ChordPart> CHORD_PARTS = new ArrayList<>();
	public static List<ArpPart> ARP_PARTS = new ArrayList<>();
	
	public static ChordGenSettings CHORD_SETTINGS = new ChordGenSettings();
	public static DrumGenSettings DRUM_SETTINGS = new DrumGenSettings();
	public static ArpGenSettings ARP_SETTINGS = new ArpGenSettings();
	
	public static double START_TIME_DELAY = 0.5;
	public static double DEFAULT_CHORD_SPLIT = 625;
	
	private static final String ARP_PATTERN_KEY = "ARP_PATTERN";
	private static final String ARP_OCTAVE_KEY = "ARP_OCTAVE";
	private static final String ARP_PAUSES_KEY = "ARP_PAUSES";
	
	public static EnumMap<PARTS, Integer> PARTS_INSTRUMENT_MAP = new EnumMap<>(PARTS.class);
	
	public static int MAXIMUM_ARP_COUNT = 8;
	
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
	public static int CHORD_SLASH_CHANCE = 0;
	
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
			int spiceSelectPow = generator.nextInt(2) + 1;
			//SPICE CHANCE - multiply by 100 or 10000 to get aug/dim or maj/min 7th
			if (generator.nextInt(100) < SPICE_CHANCE) {
				int spiceInt = 100;
				
				
				if (!SPICE_ALLOW_DIM_AUG && spiceSelectPow == 1) {
					// move to maj/min 7th
					spiceSelectPow = 2;
				}
				
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
		Part melody = new Part("Melody", melodyProgramChange, 0);
		Part chords = new Part("Chords", 53, 1);
		Part arps = new Part("Arps", XYLOPHONE, 2);
		Part bassRoots = new Part("BassRoots", BASS, 8);
		Part drums = new Part("Drums", PIANO, 9);
		Part chordSlash = new Part("ChordSlash", PIANO, 6);
		
		EnumMap<PARTS, Part> enumMap = new EnumMap<>(PARTS.class);
		enumMap.put(PARTS.MELODY, melody);
		enumMap.put(PARTS.CHORDS, chords);
		enumMap.put(PARTS.ARPS, arps);
		enumMap.put(PARTS.BASSROOTS, bassRoots);
		enumMap.put(PARTS.DRUMS, drums);
		
		
		for (PARTS part : PARTS_INSTRUMENT_MAP.keySet()) {
			Integer instrumentChoice = PARTS_INSTRUMENT_MAP.get(part);
			enumMap.get(part).setInstrument(instrumentChoice);
			if (part == PARTS.CHORDS) {
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
		//System.out.println(directionProgression.toString());
		List<int[]> actualProgression = MidiUtils.squishChordProgression(generatedRootProgression);
		
		
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
		
		List<CPhrase> chordsCPhrases = new ArrayList<>();
		for (int i = 0; i < CHORD_PARTS.size(); i++) {
			chordsCPhrases.add(new CPhrase());
		}
		List<CPhrase> arpCPhrases = new ArrayList<>();
		for (int i = 0; i < ARP_PARTS.size(); i++) {
			arpCPhrases.add(new CPhrase());
		}
		Phrase melodyPhrase = new Phrase();
		CPhrase cphraseBassRoot = new CPhrase();
		CPhrase chordSlashCPhrase = new CPhrase();
		
		for (int i = 0; i < PIECE_LENGTH; i++) {
			Note previousChordsNote = null;
			
			Random transitionGenerator = new Random(mainGeneratorSeed + 1);
			Random chordSlashGenerator = new Random(mainGeneratorSeed + 2);
			// fill chords
			for (int j = 0; j < actualProgression.size(); j++) {
				for (int k = 0; k < CHORD_PARTS.size(); k++) {
					CPhrase cp = chordsCPhrases.get(k);
					boolean transition = transitionGenerator.nextInt(100) < CHORD_PARTS.get(k)
							.getTransitionChance();
					int transChord = (transitionGenerator.nextInt(100) < CHORD_PARTS.get(k)
							.getTransitionChance()) ? (j + 1) % actualProgression.size() : j;
					
					// random = use generated split with potential to transition to 2nd chord early
					// otherwise = use pattern within single chord
					if (CHORD_PARTS.get(k).getPattern() == RhythmPattern.RANDOM) {
						double splitTime = CHORD_SETTINGS.isUseSplit()
								? CHORD_PARTS.get(k).getTransitionSplit()
								: DEFAULT_CHORD_SPLIT;
						
						double duration1 = progressionDurations.get(j) * splitTime / 1000.0;
						double duration2 = progressionDurations.get(j) - duration1;
						if (transition) {
							cp.addChord(Arrays.copyOf(actualProgression.get(j),
									actualProgression.get(j).length), duration1);
							cp.addChord(Arrays.copyOf(actualProgression.get(transChord),
									actualProgression.get(transChord).length), duration2);
						} else {
							cp.addChord(
									Arrays.copyOf(actualProgression.get(j),
											actualProgression.get(j).length),
									progressionDurations.get(j));
						}
						
					} else {
						double duration = progressionDurations.get(j) / MAXIMUM_ARP_COUNT;
						List<Integer> pattern = CHORD_PARTS.get(k).getPattern()
								.getPatternByLength(MAXIMUM_ARP_COUNT);
						for (int p = 0; p < pattern.size(); p++) {
							if (pattern.get(p) > 0) {
								cp.addChord(Arrays.copyOf(actualProgression.get(j),
										actualProgression.get(j).length), duration);
							} else {
								cp.addChord(new int[] { Integer.MIN_VALUE }, duration);
							}
						}
						
					}
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
				melodyPhrase.addNoteList(generatedMelody);
			}
			
			
		}
		
		// Generate and fill arps
		for (int i = 0; i < ARP_PARTS.size(); i++) {
			CPhrase arpCPhrase = arpCPhrases.get(i);
			Map<String, List<Integer>> arpMap = generateArpMap(ARP_PARTS.get(i).getPatternSeed(),
					i == 0, ARP_PARTS.get(i));
			fillArpCPhrase(arpCPhrase, arpMap, actualProgression, ARP_PARTS.get(i));
		}
		
		// Transpose 
		Mod.transpose(melodyPhrase, TRANSPOSE_SCORE);
		Mod.transpose(chordSlashCPhrase, -24 + TRANSPOSE_SCORE);
		Mod.transpose(cphraseBassRoot, -24 + TRANSPOSE_SCORE);
		for (int i = 0; i < CHORD_PARTS.size(); i++) {
			int extraTranspose = CHORD_SETTINGS.isUseTranspose() ? CHORD_PARTS.get(i).getTranspose()
					: 0;
			Mod.transpose(chordsCPhrases.get(i), -12 + TRANSPOSE_SCORE + extraTranspose);
		}
		
		for (int i = 0; i < ARP_PARTS.size(); i++) {
			int extraTranspose = ARP_SETTINGS.isUseTranspose() ? ARP_PARTS.get(i).getTranspose()
					: 0;
			Mod.transpose(arpCPhrases.get(i), -24 + TRANSPOSE_SCORE + extraTranspose);
		}
		
		// Midi velocity / dynamic
		melodyPhrase.setDynamic(80);
		cphraseBassRoot.setDynamic(65);
		for (int i = 0; i < CHORD_PARTS.size(); i++) {
			chordsCPhrases.get(i).setDynamic(70 - i * 2);
		}
		for (int i = 0; i < ARP_PARTS.size(); i++) {
			arpCPhrases.get(i).setDynamic(70 - i * 2);
		}
		
		// Delay start time
		melodyPhrase.setStartTime(START_TIME_DELAY);
		cphraseBassRoot.setStartTime(START_TIME_DELAY);
		chordSlashCPhrase.setStartTime(START_TIME_DELAY);
		for (int i = 0; i < CHORD_PARTS.size(); i++) {
			double additionalDelay = 0;
			if (CHORD_SETTINGS.isUseDelay()) {
				additionalDelay = (CHORD_PARTS.get(i).getDelay() / 1000.0);
			}
			chordsCPhrases.get(i).setStartTime(START_TIME_DELAY + additionalDelay);
		}
		for (int i = 0; i < ARP_PARTS.size(); i++) {
			double additionalDelay = 0;
			/*if (ARP_SETTINGS.isUseDelay()) {
				additionalDelay = (ARP_PARTS.get(i).getDelay() / 1000.0);
			}*/
			arpCPhrases.get(i).setStartTime(START_TIME_DELAY + additionalDelay);
		}
		
		// chord strum
		if (CHORD_SETTINGS.isUseStrum()) {
			for (int i = 0; i < CHORD_PARTS.size(); i++) {
				if (CHORD_PARTS.get(i).getPattern() == RhythmPattern.RANDOM) {
					chordsCPhrases.get(i).flam(CHORD_PARTS.get(i).getStrum() / 1000.0);
				} else {
					chordsCPhrases.get(i).flam(10 / 1000.0);
				}
			}
		}
		
		// add to parts
		melody.addPhrase(melodyPhrase);
		bassRoots.addCPhrase(cphraseBassRoot);
		chordSlash.addCPhrase(chordSlashCPhrase);
		
		
		for (PARTS part : PARTS_INSTRUMENT_MAP.keySet()) {
			if (part != PARTS.DRUMS) {
				if (part == PARTS.CHORDS) {
					for (int i = 0; i < CHORD_PARTS.size() && i < 7; i++) {
						Part p = new Part("Chords" + i, CHORD_PARTS.get(i).getInstrument(), 1 + i);
						p.addCPhrase(chordsCPhrases.get(i));
						score.add(p);
					}
					if (!CHORD_PARTS.isEmpty()) {
						chordSlash.setInstrument(CHORD_PARTS.get(0).getInstrument());
						score.add(chordSlash);
					}
					
					continue;
				}
				
				if (part == PARTS.ARPS) {
					for (int i = 0; i < ARP_PARTS.size() && i < 6; i++) {
						Part p = new Part("Arps" + i, ARP_PARTS.get(i).getInstrument(), 10 + i);
						p.addCPhrase(arpCPhrases.get(i));
						score.add(p);
					}
					continue;
				}
				score.add(enumMap.get(part));
			}
		}
		
		
		// fill drums		
		if (PARTS_INSTRUMENT_MAP.containsKey(PARTS.DRUMS) && DRUM_PARTS.size() > 0) {
			Map<Integer, List<Integer>> drumMap = generateDrumMap();
			Map<Integer, List<Integer>> drumVelocityMap = generateDrumVelocityMap();
			fillDrumsCPhrases(score, drumMap, drumVelocityMap, actualProgression.size());
		}
		
		score.setTempo(MAIN_BPM);
		
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
			List<int[]> actualProgression, ArpPart ap) {
		
		List<Integer> arpPattern = arpMap.get(ARP_PATTERN_KEY);
		List<Integer> arpOctavePattern = arpMap.get(ARP_OCTAVE_KEY);
		List<Integer> arpPausesPattern = arpMap.get(ARP_PAUSES_KEY);
		
		int repeatedArpsPerChord = ap.getHitsPerPattern() * ap.getPatternRepeat();
		
		for (int i = 0; i < PIECE_LENGTH; i++) {
			int chordSpanPart = 0;
			for (int j = 0; j < actualProgression.size(); j++) {
				double chordDurationArp = progressionDurations.get(j)
						/ ((double) repeatedArpsPerChord);
				int[] chord = actualProgression.get(j);
				for (int p = 0; p < repeatedArpsPerChord; p++) {
					Integer k = partOfList(chordSpanPart, ap.getChordSpan(), arpPattern).get(p);
					
					int octaveAdjustment = (k < 2) ? -12 : ((k < 6) ? 0 : 12);
					
					int pitch = chord[k % chord.length] + octaveAdjustment
							+ partOfList(chordSpanPart, ap.getChordSpan(), arpOctavePattern).get(p);
					if (partOfList(chordSpanPart, ap.getChordSpan(), arpPausesPattern)
							.get(p) == 0) {
						pitch = Integer.MIN_VALUE;
					}
					
					arpCPhrase.addChord(new int[] { pitch }, chordDurationArp);
				}
				chordSpanPart++;
				if (chordSpanPart >= ap.getChordSpan()) {
					chordSpanPart = 0;
				}
			}
		}
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
				Random exceptionGenerator = new Random(
						DRUM_PARTS.get(i).getPatternSeed() + DRUM_PARTS.get(i).getOrder());
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
					START_TIME_DELAY + (DRUM_PARTS.get(i).getSlideMiliseconds() / 1000.0));
			
			parts[i].addPhrase(drumPhrases[i]);
			scr.addPart(parts[i]);
		}
		
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
		if (chord != null && melodyGenerator != null && FIRST_NOTE_FROM_CHORD) {
			Note n = generateNote(chord, true, MELODY_SCALE, null, melodyGenerator,
					Durations.HALF_NOTE);
			copied[0] = new Note(n.getPitch(), originals[0].getRhythmValue());
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
		
		int[] arpPatternArray = IntStream.range(0, MAXIMUM_ARP_COUNT).toArray();
		int[] arpOctaveArray = IntStream.iterate(0, e -> (e + 12) % 24).limit(MAXIMUM_ARP_COUNT * 2)
				.toArray();
		for (int i = 0; i < arpOctaveArray.length; i++) {
			//arpOctaveArray[i] -= 12;
		}
		List<Integer> arpPattern = Arrays.stream(arpPatternArray).boxed()
				.collect(Collectors.toList());
		if (ap.isRepeatableNotes()) {
			arpPattern.addAll(arpPattern);
		}
		List<Integer> arpPausesPattern = new ArrayList<>();
		
		if (ap.getPattern() == RhythmPattern.RANDOM) {
			for (int i = 0; i < MAXIMUM_ARP_COUNT; i++) {
				if (uiGenerator4arpPauses.nextInt(100) < ap.getPauseChance()) {
					arpPausesPattern.add(0);
				} else {
					arpPausesPattern.add(1);
				}
			}
		} else {
			arpPausesPattern.addAll(ap.getPattern().getPatternByLength(MAXIMUM_ARP_COUNT));
			Collections.rotate(arpPausesPattern, ap.getPatternShift());
		}
		
		List<Integer> arpOctavePattern = Arrays.stream(arpOctaveArray).boxed()
				.collect(Collectors.toList());
		
		// TODO: note pattern, different from rhythm pattern
		//if (ap.getPattern() == RhythmPattern.RANDOM) {
		Collections.shuffle(arpPattern, uiGenerator2arpPattern);
		Collections.shuffle(arpOctavePattern, uiGenerator3arpOctave);
		//}
		// always generate maximum, cut off however many are needed (support for seed randoms)
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
	
	public Map<Integer, List<Integer>> generateDrumMap() {
		Map<Integer, List<Integer>> drumMap = new HashMap<>();
		
		for (int i = 0; i < DRUM_PARTS.size(); i++) {
			Random uiGenerator1drumPattern = new Random(
					DRUM_PARTS.get(i).getPatternSeed() + DRUM_PARTS.get(i).getOrder() - 1);
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
			Collections.rotate(drumPattern, DRUM_PARTS.get(i).getPatternShift());
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
					DRUM_PARTS.get(i).getPatternSeed() + DRUM_PARTS.get(i).getOrder());
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
