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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JComboBox;

import jm.constants.Durations;
import jm.constants.Pitches;
import jm.music.data.CPhrase;
import jm.music.data.Note;
import jm.music.data.Phrase;

public class MidiUtils {

	public enum PARTS {
		MELODY, ARPS, CHORDS, BASSROOTS, DRUMS;
	}

	public interface Scales {

		public static final Integer[] CHROMATIC_SCALE = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 },
				MAJOR_SCALE = { 0, 2, 4, 5, 7, 9, 11 }, MINOR_SCALE = { 0, 2, 3, 5, 7, 8, 10 },
				HARMONIC_MINOR_SCALE = { 0, 2, 3, 5, 7, 8, 11 },
				MELODIC_MINOR_SCALE = { 0, 2, 3, 5, 7, 8, 9, 10, 11 }, // mix of ascend and descend
				NATURAL_MINOR_SCALE = { 0, 2, 3, 5, 7, 8, 10 },
				DIATONIC_MINOR_SCALE = { 0, 2, 3, 5, 7, 8, 10 },
				AEOLIAN_SCALE = { 0, 2, 3, 5, 7, 8, 10 }, DORIAN_SCALE = { 0, 2, 3, 5, 7, 9, 10 },
				PHRYGIAN_SCALE = { 0, 1, 3, 5, 7, 8, 10 }, LYDIAN_SCALE = { 0, 2, 4, 6, 7, 9, 11 },
				MIXOLYDIAN_SCALE = { 0, 2, 4, 5, 7, 9, 10 }, PENTATONIC_SCALE = { 0, 2, 4, 7, 9 },
				BLUES_SCALE = { 0, 2, 3, 4, 5, 7, 9, 10, 11 },
				TURKISH_SCALE = { 0, 1, 3, 5, 7, 10, 11 }, INDIAN_SCALE = { 0, 1, 1, 4, 5, 8, 10 },
				LOCRIAN_SCALE = { 0, 1, 3, 4, 6, 8, 10 };

	}

	//full scale
	public static final List<Integer> cIonianScale4 = new ArrayList<>(Arrays.asList(Pitches.C4,
			Pitches.D4, Pitches.E4, Pitches.F4, Pitches.G4, Pitches.A4, Pitches.B4, Pitches.C5));
	public static final List<Integer> cDorianScale4 = new ArrayList<>(Arrays.asList(Pitches.C4,
			Pitches.D4, Pitches.EF4, Pitches.F4, Pitches.G4, Pitches.A4, Pitches.BF4, Pitches.C5));
	public static final List<Integer> cPhrygianScale4 = new ArrayList<>(
			Arrays.asList(Pitches.C4, Pitches.DF4, Pitches.EF4, Pitches.F4, Pitches.G4, Pitches.AF4,
					Pitches.BF4, Pitches.C5));
	public static final List<Integer> cLydianScale4 = new ArrayList<>(Arrays.asList(Pitches.C4,
			Pitches.D4, Pitches.E4, Pitches.FS4, Pitches.G4, Pitches.A4, Pitches.B4, Pitches.C5));
	public static final List<Integer> cMixolydianScale4 = new ArrayList<>(Arrays.asList(Pitches.C4,
			Pitches.D4, Pitches.E4, Pitches.F4, Pitches.G4, Pitches.A4, Pitches.BF4, Pitches.C5));
	public static final List<Integer> cAeolianScale4 = new ArrayList<>(Arrays.asList(Pitches.C4,
			Pitches.D4, Pitches.EF4, Pitches.F4, Pitches.G4, Pitches.AF4, Pitches.BF4, Pitches.C5));
	public static final List<Integer> cLocrianScale4 = new ArrayList<>(
			Arrays.asList(Pitches.C4, Pitches.DF4, Pitches.EF4, Pitches.F4, Pitches.GF4,
					Pitches.AF4, Pitches.BF4, Pitches.C5));
	public static final List<Integer> cBluesScale4 = new ArrayList<>(
			Arrays.asList(Pitches.C4, Pitches.EF4, Pitches.F4, Pitches.GF4, Pitches.G4, Pitches.BF4,
					Pitches.BF4, Pitches.C5));

	public enum ScaleMode {
		IONIAN(Scales.MAJOR_SCALE, cIonianScale4), DORIAN(Scales.DORIAN_SCALE, cDorianScale4),
		PHRYGIAN(Scales.PHRYGIAN_SCALE, cPhrygianScale4),
		LYDIAN(Scales.LYDIAN_SCALE, cLydianScale4),
		MIXOLYDIAN(Scales.MIXOLYDIAN_SCALE, cMixolydianScale4),
		AEOLIAN(Scales.AEOLIAN_SCALE, cAeolianScale4),
		LOCRIAN(Scales.LOCRIAN_SCALE, cLocrianScale4), BLUES(Scales.BLUES_SCALE, cBluesScale4);

		public Integer[] noteAdjustScale;
		public List<Integer> absoluteNotesC;

		private ScaleMode(Integer[] adjust, List<Integer> absolute) {
			this.noteAdjustScale = adjust;
			this.absoluteNotesC = absolute;
		}
	}

	//chords
	public static final int[] cMaj4 = { Pitches.C4, Pitches.E4, Pitches.G4 };
	public static final int[] cMin4 = { Pitches.C4, Pitches.EF4, Pitches.G4 };
	public static final int[] cAug4 = { Pitches.C4, Pitches.E4, Pitches.GS4 };
	public static final int[] cDim4 = { Pitches.C4, Pitches.EF4, Pitches.GF4 };
	public static final int[] cMaj7th4 = { Pitches.C4, Pitches.E4, Pitches.G4, Pitches.B4 };
	public static final int[] cMin7th4 = { Pitches.C4, Pitches.EF4, Pitches.G4, Pitches.BF4 };
	public static final int[] cMaj9th4 = { Pitches.C4, Pitches.E4, Pitches.G4, Pitches.B4,
			Pitches.D5 };
	public static final int[] cMin9th4 = { Pitches.C4, Pitches.E4, Pitches.G4, Pitches.B4,
			Pitches.D5 };
	public static final int[] cMaj13th4 = { Pitches.C4, Pitches.E4, Pitches.G4, Pitches.B4,
			Pitches.D5, Pitches.A5 };
	public static final int[] cMin13th4 = { Pitches.C4, Pitches.EF4, Pitches.G4, Pitches.BF4,
			Pitches.D5, Pitches.A5 };
	public static final int[] cSus4th4 = { Pitches.C4, Pitches.F4, Pitches.G4 };
	public static final int[] cSus2nd4 = { Pitches.C4, Pitches.D4, Pitches.G4 };
	public static final int[] cSus7th4 = { Pitches.C4, Pitches.F4, Pitches.G4, Pitches.BF4 };

	public static final List<int[]> SPICE_CHORDS_LIST = new ArrayList<>();
	static {
		SPICE_CHORDS_LIST.add(cMaj4);
		SPICE_CHORDS_LIST.add(cMin4);

		SPICE_CHORDS_LIST.add(cAug4);
		SPICE_CHORDS_LIST.add(cDim4);

		SPICE_CHORDS_LIST.add(cMaj7th4);
		SPICE_CHORDS_LIST.add(cMin7th4);
		SPICE_CHORDS_LIST.add(cMaj9th4);
		SPICE_CHORDS_LIST.add(cMin9th4);
		SPICE_CHORDS_LIST.add(cMaj13th4);
		SPICE_CHORDS_LIST.add(cMin13th4);

		SPICE_CHORDS_LIST.add(cSus4th4);
		SPICE_CHORDS_LIST.add(cSus2nd4);
		SPICE_CHORDS_LIST.add(cSus7th4);
	}

	public static final List<String> BANNED_DIM_AUG_LIST = Arrays
			.asList(new String[] { "dim", "aug" });
	public static final List<String> BANNED_9_13_LIST = Arrays
			.asList(new String[] { "maj9", "m9", "maj13", "m13" });


	public static final List<String> SPICE_NAMES_LIST = Arrays.asList(new String[] { "", "m", "aug",
			"dim", "maj7", "m7", "maj9", "m9", "maj13", "m13", "sus4", "sus2", "sus7" });
	// index 0 unused
	public static final List<String> CHORD_FIRST_LETTERS = Arrays
			.asList(new String[] { "X", "C", "D", "E", "F", "G", "A", "B" });
	public static final List<String> MAJOR_CHORDS = Arrays
			.asList(new String[] { "C", "Dm", "Em", "F", "G", "Am", "Bdim" });

	public static final Map<String, List<String>> cpRulesMap = createChordProgressionRulesMap();
	public static final Map<Integer, Integer> diaTransMap = createDiaTransMap();
	public static final Map<String, int[]> chordsMap = createChordMap();


	private static Map<String, List<String>> createChordProgressionRulesMap() {
		Map<String, List<String>> cpMap = new HashMap<>();
		//"S" is an imaginary last element which can grow into the correct last elements
		cpMap.put("S", new ArrayList<>(Arrays.asList("C", "F", "G", "Am")));
		cpMap.put("C", new ArrayList<>(Arrays.asList("F", "G")));
		cpMap.put("Dm", new ArrayList<>(Arrays.asList("F", "Am")));
		cpMap.put("Em", new ArrayList<>(Arrays.asList("Am")));
		cpMap.put("F", new ArrayList<>(Arrays.asList("C", "Dm", "Em", "G", "Am")));
		cpMap.put("G", new ArrayList<>(Arrays.asList("C", "Dm", "F", "Am")));
		cpMap.put("Am", new ArrayList<>(Arrays.asList("C", "Dm", "Em", "G")));
		cpMap.put("Bdim", new ArrayList<>(Arrays.asList("C", "Em", "F")));
		/*
		cpMap.put("Cm", new ArrayList<>());
		cpMap.put("D", new ArrayList<>());
		cpMap.put("E", new ArrayList<>());
		cpMap.put("Fm", new ArrayList<>());
		cpMap.put("Gm", new ArrayList<>());
		cpMap.put("A", new ArrayList<>(Arrays.asList("C", "F")));
		cpMap.put("B", new ArrayList<>(Arrays.asList("C", "Em", "F")));*/
		return cpMap;

	}

	// diaTransMap.get(i) == MAJOR_SCALE.get(i) ? 
	private static Map<Integer, Integer> createDiaTransMap() {
		Map<Integer, Integer> diaMap = new HashMap<>();
		diaMap.put(1, 0);
		diaMap.put(2, 2);
		diaMap.put(3, 4);
		diaMap.put(4, 5);
		diaMap.put(5, 7);
		diaMap.put(6, 9);
		diaMap.put(7, 11);
		return diaMap;

	}

	private static Map<String, int[]> createChordMap() {
		Map<String, int[]> chordMap = new HashMap<>();

		for (int i = 1; i <= 7; i++) {
			for (int j = 0; j < SPICE_CHORDS_LIST.size(); j++) {
				chordMap.put(CHORD_FIRST_LETTERS.get(i) + SPICE_NAMES_LIST.get(j),
						transposeChord(SPICE_CHORDS_LIST.get(j), diaTransMap.get(i)));
			}
		}
		return chordMap;

	}

	// order freq map by which chord contains most of the passed in notes
	// -> create map 
	public static String applyChordFreqMap(Set<Integer> frequentNotes) {
		Map<String, Set<Integer>> freqMap = createChordFreqMap();
		Map<String, Long> chordMatchesMap = new LinkedHashMap<>();

		for (String l : freqMap.keySet()) {
			int counter = 0;
			for (Integer i : frequentNotes) {
				if (freqMap.get(l).contains(i)) {
					counter++;
				}
			}
			chordMatchesMap.put(l, Long.valueOf(counter));
		}

		Map<String, Long> top3 = chordMatchesMap.entrySet().stream()
				.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(2)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1,
						LinkedHashMap::new));

		//top3.entrySet().stream().forEach(System.out::println);
		// return second most matching chord 
		if (top3.keySet().size() > 1) {
			return (String) top3.keySet().toArray()[1];
		}
		System.out.println("Only one chord matches? Huh..");
		return (String) top3.keySet().toArray()[0];
	}

	private static Map<String, Set<Integer>> createChordFreqMap() {
		Map<String, Set<Integer>> freqMap = new HashMap<>();

		for (String ch : MAJOR_CHORDS) {
			freqMap.put(ch, intArrToList(chordsMap.get(ch)).stream().map(e -> e % 12)
					.collect(Collectors.toSet()));
		}
		return freqMap;
	}

	public static List<Integer> intArrToList(int[] intArr) {
		List<Integer> intList = new ArrayList<Integer>(intArr.length);
		for (int i : intArr) {
			intList.add(i);
		}
		return intList;
	}
	/*
		public static String prettyChord(long chordNum) {
			String chordString = String.valueOf(chordNum);
			int firstNum = Character.digit(chordString.charAt(0), 10);
			String chordLetter = NUM_TO_LETTER.get(firstNum);
			String chordQualifier = "";
			//Long normalizedNum = Long.valueOf(chordNum / firstNum);
			//System.out.println("Normalized: " + normalizedNum);
			if (chordNum < 10) {
				return chordLetter;
			} else if (chordNum < 100) {
				return chordLetter + "m";
			} else {
				int numIndex = SPICE_SELECT_LIST.indexOf(Long.valueOf((chordNum / firstNum) / 10L));
				chordQualifier = SPICE_SELECT_PRETTY.get(numIndex);
				return chordLetter + chordQualifier;
			}
		}
	
		public static long unprettyChord(String chord) {
			int firstNum = NUM_TO_LETTER.indexOf(String.valueOf(chord.charAt(0)));
			if (chord.length() == 1) {
				return firstNum;
			}
			if (chord.length() == 2 && chord.charAt(1) == 'm') {
				return firstNum * 10;
			}
			int chordQualifierIndex = SPICE_SELECT_PRETTY.indexOf(chord.substring(1));
			long chordLong = SPICE_SELECT_LIST.get(chordQualifierIndex) * 10;
			return chordLong * firstNum;
	
		}*/

	public static void addShortenedChord(CPhrase cpr, int[] chord, double rhythmValue, int dynamic,
			double shortenedTo) {
		cpr.addChord(chord, rhythmValue * shortenedTo, dynamic);
		if (shortenedTo > 0.999) {
			return;
		}
		cpr.addChord(new int[] { Integer.MIN_VALUE }, rhythmValue * (1 - shortenedTo), dynamic);
	}

	public static void addShortenedNote(Phrase pr, Note n, double shortenedTo) {
		double rv = n.getRhythmValue();
		n.setRhythmValue(shortenedTo * rv);
		pr.addNote(n);
		if (shortenedTo > 0.999) {
			return;
		}
		pr.addNote(Integer.MIN_VALUE, (1 - shortenedTo) * rv);
	}

	public static int[] transposeChord(int[] chord, int transposeBy) {
		if (transposeBy == 0)
			return chord;
		int[] transposed = Arrays.copyOf(chord, chord.length);
		for (int i = 0; i < chord.length; i++) {
			transposed[i] += transposeBy;
		}
		return transposed;
	}

	public static List<Integer> transposeScale(List<Integer> scale, int transposeBy,
			boolean diatonic) {
		List<Integer> newScale = new ArrayList<>();
		if (diatonic) {
			for (int i = 0; i < 8; i++) {

				if (transposeBy > 0 && i + transposeBy > 7) {
					newScale.add(scale.get((i + transposeBy) % 8) + 12);
				} else if (transposeBy < 0 && i + transposeBy < 0) {
					newScale.add(scale.get((i + transposeBy) % 8) - 12);
				} else {
					newScale.add(scale.get((i + transposeBy) % 8));
				}
			}
			return newScale;
		}
		for (int i = 0; i < 8; i++) {
			newScale.add(scale.get(i) + transposeBy);
		}
		return newScale;
	}

	public static int maX(int value, int x) {
		if (value > x)
			return x;
		if (value < 0)
			return 0;
		return value;
	}

	public static int[] mappedChord(String chordString) {
		int[] mappedChord = chordsMap.get(chordString);
		if (mappedChord == null) {
			mappedChord = getInterval(chordString);
		}
		return Arrays.copyOf(mappedChord, mappedChord.length);
	}

	public static int[] convertChordToLength(int[] chord, int length, boolean conversionNeeded) {
		int[] chordCopy = Arrays.copyOf(chord, chord.length);

		if (!conversionNeeded || chord.length == length) {
			return chordCopy;
		}
		int[] converted = new int[length];
		if (chord.length < length) {
			// repeat from start with +12 transpose
			for (int i = 0; i < length; i++) {
				converted[i] = chordCopy[(i % chord.length)] + 12 * (i / chord.length);
			}
		} else {
			// alternate from beginning and end
			int filled = 0;
			int frontIndex = 0;
			int backIndex = 0;
			while (filled < length) {
				if (filled % 2 == 0) {
					converted[frontIndex] = chordCopy[frontIndex];
					frontIndex++;
				} else {
					converted[length - backIndex - 1] = chordCopy[chord.length - backIndex - 1];
					backIndex++;
				}
				filled++;
			}
		}
		return converted;
	}

	public static CPhrase chordProgressionToPhrase(List<int[]> cpr) {
		CPhrase phr = new CPhrase();
		for (int i = 0; i < cpr.size(); i++) {
			int[] chord = cpr.get(i);
			phr.addChord(chord, Durations.Q);
		}
		return phr;
	}

	public static int getStandardizedPitch(int pitch, int scaleTranspose, int tolerance) {
		int result = pitch;
		int lowBound = Pitches.C4 + scaleTranspose - tolerance;
		int highBound = Pitches.C5 + scaleTranspose + tolerance;

		while (result > highBound) {
			result -= 12;
		}
		while (result < lowBound) {
			result += 12;
		}
		return result;
	}

	public static List<Integer> extendScaleByOctaveUpDown(List<Integer> scale) {
		List<Integer> extended = new ArrayList<>();
		extended.addAll(transposeScale(scale, -12, false));
		extended.addAll(transposeScale(scale, 0, false));
		extended.addAll(transposeScale(scale, 12, false));
		return extended;
	}

	public static double pickDurationWeightedRandom(Random generator, double durationLeft,
			double[] durs, double[] chances, double defaultValue) {
		if (durs.length != chances.length) {
			return defaultValue;
		}
		double rnd = generator.nextDouble();
		for (int i = 0; i < durs.length; i++) {
			if (rnd < chances[i] && durationLeft >= durs[i]) {
				return durs[i];
			}
		}
		return defaultValue;
	}

	public static double calculateAverageNote(List<int[]> chords) {
		double noteCount = 0.001;
		double noteSum = 0;
		for (int[] c : chords) {
			noteCount += c.length;
			for (int i = 0; i < c.length; i++) {
				noteSum += c[i];
			}
		}

		return noteSum / noteCount;
	}

	public static List<int[]> getBasicChordsFromRoots(List<int[]> roots) {
		List<Integer> majorScaleNormalized = Arrays.asList(Scales.MAJOR_SCALE);
		List<int[]> basicChords = new ArrayList<>();
		for (int[] r : roots) {
			int index = majorScaleNormalized.indexOf(r[0] % 12);
			String chordLong = MAJOR_CHORDS.get(index);
			basicChords.add(mappedChord(chordLong));
		}
		return basicChords;
	}

	public static List<int[]> squishChordProgression(List<int[]> chords, boolean squishBigChords,
			long seed, int chance) {
		Random r = new Random(seed);
		double avg = MidiUtils.calculateAverageNote(chords);
		//System.out.println("AVG: " + avg);

		List<int[]> squishedChords = new ArrayList<>();
		for (int i = 0; i < chords.size(); i++) {
			int[] c = Arrays.copyOf(chords.get(i), chords.get(i).length);
			if (r.nextInt(100) < chance && (c.length <= 3 || squishBigChords)) {
				if (avg - c[0] > 6) {
					c[0] += 12;
					//System.out.println("SWAP UP: " + i);
				}
				if (c[c.length - 1] - avg > 6) {
					c[c.length - 1] -= 12;
					//System.out.println("SWAP DOWN: " + i);
				}
			}

			Arrays.sort(c);
			squishedChords.add(c);
		}
		//System.out.println("NEW AVG: " + MidiUtils.calculateAverageNote(squishedChords));
		return squishedChords;
	}

	public static int[] transposeChord(int[] chord, final int[] mode, final int[] modeTo) {
		int[] transposedChord = new int[chord.length];

		List<Integer> modeList = new ArrayList<>();
		for (int num : mode) {
			modeList.add(num);
		}


		for (int j = 0; j < chord.length; j++) {
			int pitch = chord[j];
			int originalIndex = modeList.indexOf(Integer.valueOf(pitch % 12));

			if (originalIndex == -1) {
				transposedChord[j] = pitch - 1;
				continue;
			}


			int originalMovement = mode[originalIndex];
			int newMovement = modeTo[originalIndex];

			if (pitch != Note.REST) {
				transposedChord[j] = pitch - originalMovement + newMovement;
			}
		}
		return transposedChord;
	}

	public static final String[] INSTRUMENTS_NAMES = { "PIANO = 0 ", "BRIGHT_ACOUSTIC = 1 ",
			"ELECTRIC_GRAND = 2 ", "HONKYTONK = 3 ", "EPIANO = 4 ", "EPIANO2 = 5 ",
			"HARPSICHORD = 6 ", "CLAV = 7 ", "CELESTE = 8 ", "GLOCKENSPIEL = 9 ", "MUSIC_BOX = 10 ",
			"VIBRAPHONE = 11 ", "MARIMBA = 12 ", "XYLOPHONE = 13 ", "TUBULAR_BELL = 14 ",
			"NOTHING = 15 ", "ORGAN = 16 ", "ORGAN2 = 17 ", "ORGAN3 = 18 ", "CHURCH_ORGAN = 19 ",
			"REED_ORGAN = 20 ", "ACCORDION = 21 ", "HARMONICA = 22 ", "BANDNEON = 23 ",
			"NYLON_GUITAR = 24 ", "STEEL_GUITAR = 25 ", "JAZZ_GUITAR = 26 ", "CLEAN_GUITAR = 27 ",
			"MUTED_GUITAR = 28 ", "OVERDRIVE_GUITAR = 29 ", "DISTORTED_GUITAR = 30 ",
			"GUITAR_HARMONICS = 31 ", "ACOUSTIC_BASS = 32 ", "FINGERED_BASS = 33 ",
			"PICKED_BASS = 34 ", "FRETLESS_BASS = 35 ", "SLAP_BASS = 36 ", "SLAP_BASS_2 = 37 ",
			"SYNTH_BASS = 38 ", "SYNTH_BASS_2 = 39 ", "VIOLIN = 40 ", "VIOLA = 41 ", "CELLO = 42 ",
			"CONTRABASS = 43 ", "TREMOLO_STRINGS = 44 ", "PIZZICATO_STRINGS = 45 ", "HARP = 46 ",
			"TIMPANI = 47 ", "STRINGS = 48 ", "STRING_ENSEMBLE_2 = 49 ", "SYNTH_STRINGS = 50 ",
			"SLOW_STRINGS = 51 ", "AAH = 52 ", "OOH = 53 ", "SYNVOX = 54 ", "ORCHESTRA_HIT = 55 ",
			"TRUMPET = 56 ", "TROMBONE = 57 ", "TUBA = 58 ", "MUTED_TRUMPET = 59 ",
			"FRENCH_HORN = 60 ", "BRASS = 61 ", "SYNTH_BRASS = 62 ", "SYNTH_BRASS_2 = 63 ",
			"SOPRANO_SAX = 64 ", "ALTO_SAX = 65 ", "SAXOPHONE = 66 ", "BARITONE_SAX = 67 ",
			"OBOE = 68 ", "ENGLISH_HORN = 69 ", "BASSOON = 70 ", "CLARINET = 71 ", "PICCOLO = 72 ",
			"FLUTE = 73 ", "RECORDER = 74 ", "PAN_FLUTE = 75 ", "BOTTLE_BLOW = 76 ",
			"SHAKUHACHI = 77 ", "WHISTLE = 78 ", "OCARINA = 79 ", "GMSQUARE_WAVE = 80 ",
			"GMSAW_WAVE = 81 ", "SYNTH_CALLIOPE = 82 ", "CHIFFER_LEAD = 83 ", "CHARANG = 84 ",
			"SOLO_VOX = 85 ", "WHOKNOWS1 = 86 ", "WHOKNOWS2 = 87 ", "FANTASIA = 88 ",
			"WARM_PAD = 89 ", "POLYSYNTH = 90 ", "SPACE_VOICE = 91 ", "BOWED_GLASS = 92 ",
			"METAL_PAD = 93 ", "HALO_PAD = 94 ", "SWEEP_PAD = 95 ", "ICE_RAIN = 96 ",
			"SOUNDTRACK = 97 ", "CRYSTAL = 98 ", "ATMOSPHERE = 99 ", "BRIGHTNESS = 100 ",
			"GOBLIN = 101 ", "ECHO_DROPS = 102 ", "STAR_THEME = 103 ", "SITAR = 104 ",
			"BANJO = 105 ", "SHAMISEN = 106 ", "KOTO = 107 ", "KALIMBA = 108 ", "BAGPIPES = 109 ",
			"FIDDLE = 110 ", "SHANNAI = 111 ", "TINKLE_BELL = 112 ", "AGOGO = 113 ",
			"STEEL_DRUMS = 114 ", "WOODBLOCK = 115 ", "TAIKO = 116 ", "TOM = 117 ",
			"SYNTH_DRUM = 118 ", "REVERSE_CYMBAL = 119 ", "FRETNOISE = 120 ", "BREATHNOISE = 121 ",
			"NATURE = 122 ", "BIRD = 123 ", "TELEPHONE = 124 ", "HELICOPTER = 125 ",
			"APPLAUSE = 126 ", "GUNSHOT = 127 " };

	public static final String[] BASS_INST_NAMES = { "PIANO = 0 ", "BRIGHT_ACOUSTIC = 1 ",
			"EPIANO = 4 ", "OOH = 53 ", "SYNVOX = 54 ", "FLUTE = 73 ", "RECORDER = 74 ",
			"PAN_FLUTE = 75 ", "SYNTH_CALLIOPE = 82 ", "SOLO_VOX = 85 ", "SPACE_VOICE = 91 ",
			"ECHO_DROPS = 102 " };

	public static final String[] CHORD_INST_NAMES = { "EPIANO = 4 ", "ORGAN = 16 ", "ORGAN2 = 17 ",
			"ACOUSTIC_BASS = 32 ", "TREMOLO_STRINGS = 44 ", "STRINGS = 48 ",
			"STRING_ENSEMBLE_2 = 49 ", "AAH = 52 ", "BASSOON = 70 " };

	public static final String[] PLUCK_INST_NAMES = { "BRIGHT_ACOUSTIC = 1 ", "HONKYTONK = 3 ",
			"EPIANO2 = 5 ", "CELESTE = 8 ", "MUSIC_BOX = 10 ", "VIBRAPHONE = 11 ", "MARIMBA = 12 ",
			"XYLOPHONE = 13 ", "NOTHING = 15 ", "STEEL_GUITAR = 25 ", "CLEAN_GUITAR = 27 ",
			"ACOUSTIC_BASS = 32 ", "FINGERED_BASS = 33 ", "PICKED_BASS = 34 ",
			"FRETLESS_BASS = 35 ", "SLAP_BASS = 36 ", "PIZZICATO_STRINGS = 45 ", "HARP = 46 ",
			"FANTASIA = 88 " };

	public static final String[] LONG_INST_NAMES = { "ORGAN = 16 ", "ORGAN2 = 17 ",
			"REED_ORGAN = 20 ", "ACCORDION = 21 ", "HARMONICA = 22 ", "BANDNEON = 23 ",
			"VIOLIN = 40 ", "VIOLA = 41 ", "CELLO = 42 ", "CONTRABASS = 43 ",
			"TREMOLO_STRINGS = 44 ", "STRINGS = 48 ", "STRING_ENSEMBLE_2 = 49 ",
			"SLOW_STRINGS = 51 ", "TRUMPET = 56 ", "TROMBONE = 57 ", "TUBA = 58 ",
			"FRENCH_HORN = 60 ", "BRASS = 61 ", "SOPRANO_SAX = 64 ", "ALTO_SAX = 65 ",
			"BARITONE_SAX = 67 ", "OBOE = 68 ", "ENGLISH_HORN = 69 ", "BASSOON = 70 ",
			"CLARINET = 71 ", "PICCOLO = 72 ", "RECORDER = 74 ", "BOTTLE_BLOW = 76 ",
			"SPACE_VOICE = 91 ", "BOWED_GLASS = 92 ", "METAL_PAD = 93 ", "HALO_PAD = 94 ",
			"SWEEP_PAD = 95 " };

	public static final String[] DRUM_INST_NAMES = { "BASSKICK = 35 ", "KICK = 36 ",
			"SIDE STICK = 37", "SNARE = 38 ", "CLAP = 39", "EL. SNARE = 40 ", "CLOSED_HH = 42 ",
			"PEDAL_HH = 44", "RIDE = 53 ", "TAMBOURINE = 54", "HI BONGO = 60 ", "SHAKER = 82" };

	public static final String[] DRUM_INST_NAMES_SEMI = { "BASSKICK = 36 ", "KICK = 37 ",
			"SIDE STICK = 38", "SNARE = 39 ", "CLAP = 40", "EL. SNARE = 41 ", "CLOSED_HH = 42 ",
			"PEDAL_HH = 43", "RIDE = 44 ", "TAMBOURINE = 45", "HI BONGO = 46 ", "SHAKER = 47" };

	public static final String[] DRUM_INST_NAMES_WHOLE = { "BASSKICK = 36 ", "KICK = 38 ",
			"SIDE STICK = 40", "SNARE = 41 ", "CLAP = 43", "EL. SNARE = 45 ", "CLOSED_HH = 47 ",
			"PEDAL_HH = 48", "RIDE = 50 ", "TAMBOURINE = 52", "HI BONGO = 53 ", "SHAKER = 55" };

	public static List<Integer> getInstNumbers(String[] instArray) {
		return Arrays.asList(instArray).stream().map(e -> Integer.valueOf(e.split(" = ")[1].trim()))
				.collect(Collectors.toList());
	}

	public static String[] combineInstrumentPools(String[]... instPools) {
		Set<String> allInstruments = new HashSet<>();
		for (String[] pool : instPools) {
			allInstruments.addAll(Arrays.asList(pool));
		}
		List<String> allInstrumentsSorted = new ArrayList<>(allInstruments);
		Collections.sort(allInstrumentsSorted,
				(o1, o2) -> Integer.valueOf(o1.split(" = ")[1].trim())
						.compareTo(Integer.valueOf(o2.split(" = ")[1].trim())));
		return allInstrumentsSorted.toArray(new String[] {});
	}

	public static final String[] DRUM_KITS = { "DRUMKIT0 = 0", "DRUMKIT1 = 1", "DRUMKIT2 = 2",
			"DRUMKIT3 = 3" };

	public static final List<Integer> DRUM_KIT_NUMBERS = Arrays.asList(DRUM_KITS).stream()
			.map(e -> Integer.valueOf(e.split(" = ")[1].trim())).collect(Collectors.toList());

	public enum POOL {
		PLUCK, LONG, CHORD, BASS, DRUM, ALL;
	}

	public static Map<POOL, String[]> INST_POOLS = new HashMap<>();
	static {
		initNormalInsts();
	}

	public static void initNormalInsts() {
		INST_POOLS.put(POOL.PLUCK, PLUCK_INST_NAMES);
		INST_POOLS.put(POOL.LONG, LONG_INST_NAMES);
		INST_POOLS.put(POOL.CHORD, LONG_INST_NAMES);
		INST_POOLS.put(POOL.BASS, BASS_INST_NAMES);
		INST_POOLS.put(POOL.DRUM, DRUM_INST_NAMES);
		INST_POOLS.put(POOL.ALL, INSTRUMENTS_NAMES);
	}

	public static void initAllInsts() {
		INST_POOLS.put(POOL.PLUCK, INSTRUMENTS_NAMES);
		INST_POOLS.put(POOL.LONG, INSTRUMENTS_NAMES);
		INST_POOLS.put(POOL.CHORD, INSTRUMENTS_NAMES);
		INST_POOLS.put(POOL.BASS, INSTRUMENTS_NAMES);
		INST_POOLS.put(POOL.DRUM, DRUM_INST_NAMES);
		INST_POOLS.put(POOL.ALL, INSTRUMENTS_NAMES);
	}

	public static Integer getInstByIndex(int index, POOL instPool) {
		List<Integer> instPoolNumbers = getInstNumbers(INST_POOLS.get(instPool));
		return instPoolNumbers.get(index % instPoolNumbers.size());
	}

	public static void addAllToJComboBox(String[] choices, JComboBox<String> choice) {
		for (String c : choices) {
			choice.addItem(c);
		}
	}

	public static int[] getInterval(String chordString) {
		List<Character> validChars = Arrays
				.asList(new Character[] { '#', 'C', 'D', 'E', 'F', 'G', 'A', 'B' });
		boolean expectOnlyLetter = true;
		List<Integer> intervalInts = new ArrayList<>();
		int current = -1;
		for (int i = 0; i < chordString.length(); i++) {
			Character chr = chordString.charAt(i);
			int index = validChars.indexOf(chr);
			if (index > 0) {
				current++;
				intervalInts.add(60 + diaTransMap.get(index));
				expectOnlyLetter = false;
			} else if (index == 0) {
				if (expectOnlyLetter) {
					return null;
				} else {
					intervalInts.set(current, intervalInts.get(current) + 1);
					expectOnlyLetter = true;
				}
			} else {
				return null;
			}
		}
		return intervalInts.stream().mapToInt(i -> i).toArray();
	}

}
