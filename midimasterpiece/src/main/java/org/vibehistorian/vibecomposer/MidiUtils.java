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
import java.util.Vector;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;

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
				BLUES_SCALE = { 0, 2, 3, 4, 7, 9, 12 }, TURKISH_SCALE = { 0, 1, 3, 5, 7, 10, 11 },
				INDIAN_SCALE = { 0, 1, 1, 4, 5, 8, 10 }, LOCRIAN_SCALE = { 0, 1, 3, 4, 6, 8, 10 };

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
	public static final List<Integer> cBluesScale4 = new ArrayList<>(Arrays.asList(Pitches.C4,
			Pitches.D4, Pitches.EF4, Pitches.E4, Pitches.G4, Pitches.A4, Pitches.C5));

	public enum ScaleMode {
		IONIAN(Scales.MAJOR_SCALE), DORIAN(Scales.DORIAN_SCALE), PHRYGIAN(Scales.PHRYGIAN_SCALE),
		LYDIAN(Scales.LYDIAN_SCALE), MIXOLYDIAN(Scales.MIXOLYDIAN_SCALE),
		AEOLIAN(Scales.AEOLIAN_SCALE), LOCRIAN(Scales.LOCRIAN_SCALE), BLUES(Scales.BLUES_SCALE),
		HARM_MINOR(Scales.HARMONIC_MINOR_SCALE), TURKISH(Scales.TURKISH_SCALE),
		INDIAN(Scales.INDIAN_SCALE);

		public Integer[] noteAdjustScale;

		private ScaleMode(Integer[] adjust) {
			this.noteAdjustScale = adjust;
		}
	}

	//chords
	public static final int[] cMaj4 = { Pitches.C4, Pitches.E4, Pitches.G4 };
	public static final int[] cMin4 = { Pitches.C4, Pitches.EF4, Pitches.G4 };
	public static final int[] cAug4 = { Pitches.C4, Pitches.E4, Pitches.GS4 };
	public static final int[] cDim4 = { Pitches.C4, Pitches.EF4, Pitches.GF4 };
	public static final int[] c7th4 = { Pitches.C4, Pitches.E4, Pitches.G4, Pitches.BF4 };
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

		SPICE_CHORDS_LIST.add(c7th4);
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

	public static final List<String> BANNED_DIM_AUG_7_LIST = Arrays
			.asList(new String[] { "dim", "aug", "7" });
	public static final List<String> BANNED_9_13_LIST = Arrays
			.asList(new String[] { "maj9", "m9", "maj13", "m13" });
	public static final List<String> BANNED_SUSSY_LIST = Arrays
			.asList(new String[] { "sus4", "sus2", "sus7" });

	public static final List<String> SPICE_NAMES_LIST = Arrays.asList(new String[] { "", "m", "aug",
			"dim", "7", "maj7", "m7", "maj9", "m9", "maj13", "m13", "sus4", "sus2", "sus7" });
	// index 0 unused
	public static final List<String> CHORD_FIRST_LETTERS = Arrays
			.asList(new String[] { "X", "C", "D", "E", "F", "G", "A", "B" });
	public static final List<String> MAJOR_CHORDS = Arrays
			.asList(new String[] { "C", "Dm", "Em", "F", "G", "Am", "Bdim" });

	public static final List<Integer> majorChordsModRating = Arrays
			.asList(new Integer[] { 3, 2, 1, 3, 3, 1, -10 });

	public static final List<String> progressionCircle = Arrays
			.asList(new String[] { "C", "F", "Bdim", "Em", "Am", "Dm", "G", "C" });


	public static final Map<String, List<String>> cpRulesMap = createChordProgressionRulesMap();
	public static final Map<Integer, Integer> diaTransMap = createDiaTransMap();
	public static final Map<String, int[]> chordsMap = createChordMap();

	public static final Map<Integer, List<Pair<String, String>>> modulationMap = createKeyModulationMap();

	private static Map<String, List<String>> createChordProgressionRulesMap() {
		Map<String, List<String>> cpMap = new HashMap<>();
		//"S" is an imaginary last element which can grow into the correct last elements
		cpMap.put("S", new ArrayList<>(Arrays.asList("C", "F", "G", "Am")));
		cpMap.put("C", new ArrayList<>(Arrays.asList("F", "G")));
		cpMap.put("Dm", new ArrayList<>(Arrays.asList("Am")));
		cpMap.put("Em", new ArrayList<>(Arrays.asList("Am", "G")));
		cpMap.put("F", new ArrayList<>(Arrays.asList("C", "Dm", "Am")));
		cpMap.put("G", new ArrayList<>(Arrays.asList("C", "Dm", "Em", "F", "Am")));
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

	private static Map<Integer, List<Pair<String, String>>> createKeyModulationMap() {
		Map<Integer, List<Pair<String, String>>> modMap = new HashMap<>();

		Map<String, Set<Integer>> freqMap = createChordFreqMap();
		for (int i = -5; i <= 6; i++) {
			if (i == 0) {
				continue;
			} else {
				List<Pair<String, String>> pair = getKeyModPairs(i, freqMap);
				if (pair != null) {
					modMap.put(i, pair);
					/*System.out.println(
							"Trans: " + i + ", pair: " + (pair == null ? "NULL" : pair.toString()));*/
				}

			}
		}

		return modMap;

	}

	private static List<Pair<String, String>> getKeyModPairs(int toKey,
			Map<String, Set<Integer>> freqMap) {
		List<Pair<String, String>> pairs = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			// for each chord of major scale, check if this chord transposed up by toKey is also a chord of the major scale
			String chordString = MAJOR_CHORDS.get(i);
			Set<Integer> baseFreqs = freqMap.get(chordString);
			Set<Integer> transFreqs = new HashSet<>();
			baseFreqs.forEach(e -> transFreqs.add((e + toKey + 12) % 12));
			for (String s : freqMap.keySet()) {
				Set<Integer> comparedFreqs = freqMap.get(s);
				if (comparedFreqs.containsAll(transFreqs)) {
					Pair<String, String> goodPair = Pair.of(chordString, s);
					/*System.out.println("Good pair: " + goodPair.toString() + ", rating: "
							+ ratePairForModulation(goodPair));*/
					pairs.add(goodPair);
				}
			}
		}
		return pairs.isEmpty() ? null : pairs;
	}


	public static int ratePairForModulation(Pair<String, String> pair) {
		int val1 = majorChordsModRating.get(MAJOR_CHORDS.indexOf(pair.getLeft()));
		int val2 = majorChordsModRating.get(MAJOR_CHORDS.indexOf(pair.getRight()));
		return val1 + val2;
	}

	// order freq map by which chord contains most of the passed in notes
	// -> create map 
	public static String applyChordFreqMap(Map<Integer, Long> frequentNotes, int orderOfMatch,
			String prevChordString) {
		if (orderOfMatch == 0) {
			orderOfMatch++;
		}
		Map<String, Set<Integer>> freqMap = createChordFreqMap();
		Map<String, Long> chordMatchesMap = new LinkedHashMap<>();
		long bestMatch = 0;
		for (String l : freqMap.keySet()) {
			int counter = 0;
			for (Integer i : frequentNotes.keySet()) {
				if (freqMap.get(l).contains(i)) {
					counter += frequentNotes.get(i);
				}
			}
			chordMatchesMap.put(l, Long.valueOf(counter));
			if (counter > bestMatch) {
				bestMatch = counter;
			}
		}

		Map<String, Long> orderedBestMatches = chordMatchesMap.entrySet().stream()
				.sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(5)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1,
						LinkedHashMap::new));

		int circleIndex = progressionCircle.indexOf(prevChordString);
		if (circleIndex != -1) {
			final long finalBestMatch = bestMatch;
			long bestMatchCount = orderedBestMatches.values().stream()
					.filter(e -> finalBestMatch == e).count();
			if (bestMatchCount > 1) {
				System.out.println(bestMatchCount + " best chords for:  " + bestMatch + " notes.");

				if (bestMatchCount >= orderOfMatch) {
					orderedBestMatches.values().removeIf(e -> e != finalBestMatch);
				}

				String expectedNextChordString = progressionCircle
						.get((circleIndex) + 1 % progressionCircle.size());
				if (orderedBestMatches.containsKey(expectedNextChordString)) {
					System.out.println("Circle: " + expectedNextChordString);
					return expectedNextChordString;
				} else {
					System.out.println("Circle chord not a best match: " + expectedNextChordString);
				}
			}
		}

		//top3.entrySet().stream().forEach(System.out::println);
		// return n-th most matching chord 
		if (orderedBestMatches.keySet().size() > orderOfMatch - 1) {
			return (String) orderedBestMatches.keySet().toArray()[orderOfMatch - 1];
		}
		System.out.println("Only one chord matches? Huh..");
		return (String) orderedBestMatches.keySet().toArray()[0];
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

	public static Pair<ScaleMode, Integer> detectKeyAndMode(Phrase phr, ScaleMode targetMode) {
		int bestNotContained = Integer.MAX_VALUE;
		ScaleMode bestMode = null;
		int transposeUpBy = 0;

		// 12 pitches
		int[] pitchCounts = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		Set<Integer> pitches = new HashSet<>();
		Vector<Note> noteList = phr.getNoteList();
		int mostFrequents = 0;
		int mostFrequentPitch = -1;
		for (Note n : noteList) {
			if (n.getPitch() <= 0) {
				continue;
			}

			int normalized = n.getPitch() % 12;
			pitches.add(normalized);
			pitchCounts[normalized] += (int) (n.getDuration()
					/ (MidiGenerator.Durations.NOTE_32ND / 2.0));
			if (pitchCounts[normalized] > mostFrequents) {
				mostFrequents = pitchCounts[normalized];
				mostFrequentPitch = normalized;
			}
		}
		System.out.println("Examining pitches: " + StringUtils.join(pitches, ", "));
		System.out.println("# of pitches: " + pitches.size());
		System.out.println("Pitch array: " + Arrays.toString(pitchCounts));

		for (ScaleMode mode : ScaleMode.values()) {
			Pair<Integer, Integer> detectionResult = detectKey(pitches, mode.noteAdjustScale);
			System.out.println("Result: " + detectionResult.toString());
			boolean bestForSure = false;
			if (detectionResult.getKey() == 0
					&& (((mostFrequentPitch + ((12 + detectionResult.getValue()) % 12))
							% 12) == 0)) {
				System.out.println("Best for sure: " + detectionResult.toString());
				bestForSure = true;
			}

			if (detectionResult.getKey() == 0 && (targetMode != null) && (targetMode == mode)) {
				bestForSure = true;
				System.out.println("Found target mode: " + targetMode.toString());
			}

			if (detectionResult.getKey() < bestNotContained || bestForSure) {
				bestNotContained = detectionResult.getKey();
				bestMode = mode;
				transposeUpBy = detectionResult.getValue();
			}
			if (bestForSure) {
				break;
			}

		}
		if (bestNotContained > 0) {
			return null;
		}

		System.out.println("Returning: " + bestMode.toString() + ", " + transposeUpBy);
		return Pair.of(bestMode, transposeUpBy);
	}

	public static Pair<Integer, Integer> detectKey(Set<Integer> pitches, Integer[] scale) {


		Set<Integer> desiredPitches = new HashSet<>();
		for (int i = 0; i < scale.length; i++) {
			desiredPitches.add(scale[i]);
		}

		int bestNotContained = pitches.size();
		int transposeUpBy = 0;
		for (int i = -6; i <= 6; i++) {
			int notContained = pitches.size();
			for (Integer p : pitches) {
				Integer transposedPitch = (p + i + 12) % 12;
				if (desiredPitches.contains(transposedPitch)) {
					notContained--;
				}
			}
			if (notContained < bestNotContained) {
				bestNotContained = notContained;
				transposeUpBy = i;
			}
			if (notContained == 0) {
				System.out.println("Found best transpose match: " + i);
				break;
			}
		}
		return Pair.of(bestNotContained, transposeUpBy);
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
		int[] mappedChord = null;

		// allow sharps as chords
		if (chordString.length() >= 2 && "#".equals(chordString.substring(1, 2))) {
			String testChordString = chordString;
			testChordString = testChordString.replaceFirst("#", "");
			mappedChord = chordsMap.get(testChordString);
			if (mappedChord != null) {
				mappedChord = Arrays.copyOf(mappedChord, mappedChord.length);
				for (int i = 0; i < mappedChord.length; i++) {
					mappedChord[i] = mappedChord[i] + 1;
				}
				return mappedChord;
			}
		}

		mappedChord = chordsMap.get(chordString);
		if (mappedChord == null) {
			mappedChord = getSpelledChord(chordString);
		}
		if (mappedChord == null) {
			throw new IllegalArgumentException("Unmappable string: " + chordString);
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
			phr.addChord(chord, MidiGenerator.Durations.EIGHTH_NOTE);
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
			if (index == -1) {
				index = majorScaleNormalized.indexOf((r[0] + 11) % 12);
			}
			String chordLong = MAJOR_CHORDS.get(index);
			basicChords.add(mappedChord(chordLong));
		}
		return basicChords;
	}

	public static List<String> getBasicChordStringsFromRoots(List<int[]> roots) {
		List<Integer> majorScaleNormalized = Arrays.asList(Scales.MAJOR_SCALE);
		List<String> basicChords = new ArrayList<>();
		for (int[] r : roots) {
			int index = majorScaleNormalized.indexOf(r[0] % 12);
			String chordLong = MAJOR_CHORDS.get(index);
			basicChords.add(chordLong);
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

	public static int[] transposeChord(int[] chord, final Integer[] mode, final Integer[] modeTo) {
		int[] transposedChord = new int[chord.length];

		List<Integer> modeList = new ArrayList<>();
		for (int num : mode) {
			modeList.add(num);
		}
		List<Integer> modeToList = new ArrayList<>();
		for (int num : modeTo) {
			modeToList.add(num);
		}

		for (int j = 0; j < chord.length; j++) {
			int pitch = chord[j];
			int searchPitch = Integer.valueOf(pitch % 12);
			int originalIndex = modeList.indexOf(searchPitch);

			if (originalIndex == -1) {
				if (modeToList.contains(searchPitch)) {
					System.out.println("Pitch found only in modeTo, not changing: " + pitch);
				} else {
					int closestPitch = getClosestFromList(modeToList, searchPitch);
					int difference = searchPitch - closestPitch;
					transposedChord[j] = pitch - difference;
					System.out.println(
							"Not indexed pitch.. " + pitch + ", lowered by.. " + difference);
				}
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

	public static void transposePhrase(Phrase phr, final Integer[] mode, final Integer[] modeTo) {
		List<Integer> modeList = new ArrayList<>();
		for (int num : mode) {
			modeList.add(num);
		}

		List<Integer> modeToList = new ArrayList<>();
		for (int num : modeTo) {
			modeToList.add(num);
		}


		for (int j = 0; j < phr.getNoteList().size(); j++) {
			Note n = (Note) phr.getNoteList().get(j);
			int pitch = n.getPitch();
			if (pitch == Note.REST) {
				continue;
			}
			int searchPitch = Integer.valueOf(pitch % 12);
			int originalIndex = modeList.indexOf(searchPitch);

			if (originalIndex == -1) {
				if (modeToList.contains(searchPitch)) {
					System.out.println("Pitch found only in modeTo, not changing: " + pitch);
				} else {
					int closestPitch = getClosestFromList(modeToList, searchPitch);
					int difference = searchPitch - closestPitch;
					n.setPitch(pitch - difference);
					System.out.println(
							"Not indexed pitch.. " + pitch + ", lowered by.. " + difference);
				}
				continue;
			}


			int originalMovement = mode[originalIndex];
			int newMovement = modeTo[originalIndex];

			n.setPitch(pitch - originalMovement + newMovement);
		}
	}

	public static int getClosestFromList(List<Integer> list, int valToFind) {
		if (list == null || list.isEmpty()) {
			return Integer.MIN_VALUE;
		}
		int closest = list.get(0);
		int closestDistance = Math.abs(valToFind - closest);
		for (int i = 1; i < list.size(); i++) {
			int distance = Math.abs(valToFind - list.get(i));
			if (distance < closestDistance) {
				closestDistance = distance;
				closest = list.get(i);
			}
		}
		return closest;
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

	public static final Integer[] DRUM_INST_NUMBERS = { 35, 36, 37, 38, 39, 40, 41, 42, 44, 46, 53,
			54, 60, 82 };
	public static final Integer[] DRUM_INST_NUMBERS_SEMI = { 36, 37, 38, 39, 40, 41, 42, 43, 44, 45,
			46, 47, 48, 49 };

	public static final String[] DRUM_INST_NAMES = { "BASSKICK = 35 ", "KICK = 36 ",
			"SIDE STICK = 37", "SNARE = 38 ", "CLAP = 39", "EL. SNARE = 40 ", "FLOOR TOM = 41 ",
			"CLOSED_HH = 42 ", "PEDAL_HH = 44", "OPEN_HH = 46", "RIDE = 53 ", "TAMBOURINE = 54",
			"HI BONGO = 60 ", "SHAKER = 82" };

	public static final String[] DRUM_INST_NAMES_SEMI = { "BASSKICK = 36 ", "KICK = 37 ",
			"SIDE STICK = 38", "SNARE = 39 ", "CLAP = 40", "EL. SNARE = 41 ", "FLOOR TOM = 42 ",
			"CLOSED_HH = 43 ", "PEDAL_HH = 44", "OPEN_HH = 45", "RIDE = 46 ", "TAMBOURINE = 47",
			"HI BONGO = 48 ", "SHAKER = 49" };

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
		return getInstByIndex(index, INST_POOLS.get(instPool));
	}

	public static Integer getInstByIndex(int index, String[] instArray) {
		List<Integer> instPoolNumbers = getInstNumbers(instArray);
		return instPoolNumbers.get(index % instPoolNumbers.size());
	}

	public static void addAllToJComboBox(String[] choices, ScrollComboBox<String> choice) {
		for (String c : choices) {
			choice.addItem(c);
		}
	}

	public static int[] getSpelledChord(String chordString) {
		List<Character> validChars = Arrays
				.asList(new Character[] { '#', 'C', 'D', 'E', 'F', 'G', 'A', 'B' });
		boolean expectOnlyLetter = true;
		List<Integer> intervalInts = new ArrayList<>();
		int currentOctaveAdjust = 0;
		int lastValue = 0;
		int current = -1;
		for (int i = 0; i < chordString.length(); i++) {
			Character chr = chordString.charAt(i);
			int index = validChars.indexOf(chr);
			if (index > 0) {
				current++;
				int newValue = 60 + diaTransMap.get(index) + currentOctaveAdjust;
				// smaller than last, or equal but next char isn't #
				if (newValue < lastValue
						|| ((newValue == lastValue) && (i == chordString.length() - 1
								|| validChars.indexOf(chordString.charAt(i + 1)) != 0))) {
					currentOctaveAdjust += 12;
					newValue += 12;
				}
				intervalInts.add(newValue);
				lastValue = newValue;
				expectOnlyLetter = false;
			} else if (index == 0) {
				if (expectOnlyLetter) {
					return null;
				} else {
					lastValue = intervalInts.get(current) + 1;
					intervalInts.set(current, lastValue);
					expectOnlyLetter = true;
				}
			} else {
				return null;
			}
		}
		return intervalInts.stream().mapToInt(i -> i).toArray();
	}


	public static <T> T getRandom(Random generator, T[] array) {
		return getRandom(generator, array, 0, array.length);
	}

	public static <T> T getRandom(Random generator, T[] array, int from, int to) {
		from = Math.max(from, 0);
		to = Math.min(to, array.length);
		return array[generator.nextInt(to - from) + from];
	}

	public static List<Integer> intersperse(int number, int times, List<Integer> list) {
		List<Integer> interspersed = new ArrayList<>();
		for (Integer i : list) {
			interspersed.add(i);
			for (int j = 0; j < times; j++) {
				interspersed.add(number);
			}
		}
		return interspersed;
	}

	public static void addChordsToPhrase(Phrase phr, List<Chord> chords, double flam) {
		for (Chord c : chords) {
			c.setFlam(flam);
			Note[] notes = c.getNotesBackwards().toArray(new Note[] {});
			Note lastNote = notes[notes.length - 1];
			lastNote.setDuration(lastNote.getDuration() * 3);
			phr.addNoteList(c.getNotesBackwards().toArray(new Note[] {}));
		}
	}

	public static List<Chord> convertChordStringsToChords(List<String> chordStrings) {
		List<Chord> chords = new ArrayList<>();
		for (String s : chordStrings) {
			Chord c = Chord.EMPTY(MidiGenerator.Durations.HALF_NOTE);
			int[] mapped = mappedChord(s);
			if (mapped == null)
				return null;

			c.setNotes(mapped);
			chords.add(c);
		}
		return chords;
	}

	public static List<String> processRawChords(String rawChords, ScaleMode targetMode) {
		List<String> rawChordsList = Arrays.asList(rawChords.replaceAll(" ", "").split(","));
		List<Chord> chords = convertChordStringsToChords(rawChordsList);
		Phrase phr = new Phrase();
		addChordsToPhrase(phr, chords, 0.125);

		Pair<ScaleMode, Integer> detectionResult = MidiUtils.detectKeyAndMode(phr, targetMode);
		if (detectionResult == null) {
			return null;
		}

		int transposeUpBy = detectionResult.getValue();
		if (transposeUpBy != 0) {
			for (Chord c : chords) {
				c.setNotes(transposeChord(c.getNotes(), transposeUpBy));
			}
		}
		if (detectionResult.getKey() != targetMode) {
			return null;
			/*for (Chord c : chords) {
				c.setNotes(MidiUtils.transposeChord(c.getNotes(),
						detectionResult.getKey().noteAdjustScale,
						ScaleMode.IONIAN.noteAdjustScale));
			}*/
		}
		for (Chord c : chords) {
			c.setNotes(MidiUtils.transposeChord(c.getNotes(),
					detectionResult.getKey().noteAdjustScale, ScaleMode.IONIAN.noteAdjustScale));
		}

		List<String> solvedChords = new ArrayList<>();
		List<Integer> majorScaleNormalized = Arrays.asList(Scales.MAJOR_SCALE);
		for (Chord c : chords) {
			int[] notes = c.getNotes();
			int firstPitch = notes[0] % 12;
			int index = majorScaleNormalized.indexOf(firstPitch);
			if (index < 0) {
				return null;
			}
			String firstLetter = CHORD_FIRST_LETTERS.get(index + 1);
			for (String spice : SPICE_NAMES_LIST) {
				String combinedChord = firstLetter + spice;
				int[] mapped = mappedChord(combinedChord);
				if (Arrays.equals(normalizeChord(mapped), normalizeChord(notes))) {
					solvedChords.add(combinedChord);
					break;
				}
			}
		}


		System.out.println(solvedChords.toString());
		if (solvedChords.size() == chords.size()) {

			VibeComposerGUI.transposeScore
					.setInt(VibeComposerGUI.transposeScore.getInt() + (transposeUpBy * -1));
			//VibeComposerGUI.scaleMode.setSelectedItem(detectionResult.getKey().toString());
			return solvedChords;
		} else {
			return null;
		}

	}

	public static int[] normalizeChord(int[] chord) {
		for (int i = 0; i < chord.length; i++) {
			chord[i] = chord[i] % 12;
		}
		return chord;
	}

}
