package org.vibehistorian.midimasterpiece.midigenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import jm.constants.Durations;
import jm.constants.Pitches;
import jm.music.data.CPhrase;
import jm.music.data.Note;

public class MidiUtils {
	
	public enum PARTS {
		MELODY, CHORDS1, CHORDS2, ARP1, ARP2, BASSROOTS, DRUMS;
	}
	
	//full scale
	public static final List<Integer> cMajScale4 = new ArrayList<>(Arrays.asList(Pitches.C4,
			Pitches.D4, Pitches.E4, Pitches.F4, Pitches.G4, Pitches.A4, Pitches.B4, Pitches.C5));
	public static final List<Integer> cMinScale4 = new ArrayList<>(Arrays.asList(Pitches.C4,
			Pitches.D4, Pitches.EF4, Pitches.F4, Pitches.G4, Pitches.AF4, Pitches.BF4, Pitches.C5));
	//chords
	public static final int[] cMaj4 = { Pitches.C4, Pitches.E4, Pitches.G4 };
	public static final int[] cMin4 = { Pitches.C4, Pitches.EF4, Pitches.G4 };
	public static final int[] cAug4 = { Pitches.C4, Pitches.E4, Pitches.GS4 };
	public static final int[] cDim4 = { Pitches.C4, Pitches.EF4, Pitches.GF4 };
	public static final int[] cMaj7th4 = { Pitches.C4, Pitches.E4, Pitches.G4, Pitches.B4 };
	public static final int[] cMin7th4 = { Pitches.C4, Pitches.EF4, Pitches.G4, Pitches.BF4 };
	
	
	public static final Map<Integer, List<Integer>> cpRulesMap = createChordProgressionRulesMap();
	public static final Map<Integer, Integer> diaTransMap = createDiaTransMap();
	public static final Map<Integer, int[]> chordsMap = createChordMap();
	
	
	private static Map<Integer, List<Integer>> createChordProgressionRulesMap() {
		Map<Integer, List<Integer>> cpMap = new HashMap<>();
		//0 is an imaginary last element which can grow into the correct last elements
		cpMap.put(0, new ArrayList<>(Arrays.asList(1, 5, 60)));
		cpMap.put(1, new ArrayList<>(Arrays.asList(4, 5)));
		cpMap.put(20, new ArrayList<>(Arrays.asList(4, 60)));
		cpMap.put(30, new ArrayList<>(Arrays.asList(60)));
		cpMap.put(4, new ArrayList<>(Arrays.asList(1, 20, 30, 5, 60)));
		cpMap.put(5, new ArrayList<>(Arrays.asList(1, 20, 4, 60)));
		cpMap.put(60, new ArrayList<>(Arrays.asList(1, 20, 30, 5)));
		cpMap.put(70, new ArrayList<>(Arrays.asList(1, 30, 4)));
		
		cpMap.put(10, new ArrayList<>());
		cpMap.put(2, new ArrayList<>());
		cpMap.put(3, new ArrayList<>());
		cpMap.put(40, new ArrayList<>());
		cpMap.put(50, new ArrayList<>());
		cpMap.put(6, new ArrayList<>(Arrays.asList(1, 4)));
		cpMap.put(7, new ArrayList<>(Arrays.asList(1, 30, 4)));
		return cpMap;
		
	}
	
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
	
	private static Map<Integer, int[]> createChordMap() {
		Map<Integer, int[]> chordMap = new HashMap<>();
		for (int i = 1; i <= 7; i++) {
			chordMap.put(i, transposeChord(cMaj4, diaTransMap.get(i)));
			chordMap.put(10 * i, transposeChord(cMin4, diaTransMap.get(i)));
			chordMap.put(100 * i, transposeChord(cAug4, diaTransMap.get(i)));
			chordMap.put(1000 * i, transposeChord(cDim4, diaTransMap.get(i)));
			chordMap.put(10000 * i, transposeChord(cMaj7th4, diaTransMap.get(i)));
			chordMap.put(100000 * i, transposeChord(cMin7th4, diaTransMap.get(i)));
		}
		return chordMap;
		
	}
	
	public static int[] transposeChord(int[] chord, int transposeBy) {
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
	
	public static int[] mappedChord(Integer chordInt) {
		int[] mappedChord = chordsMap.get(chordInt);
		return Arrays.copyOf(mappedChord, mappedChord.length);
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
	
	public static List<int[]> squishChordProgression(List<int[]> chords) {
		double avg = MidiUtils.calculateAverageNote(chords);
		System.out.println("AVG: " + avg);
		
		List<int[]> squishedChords = new ArrayList<>();
		for (int i = 0; i < chords.size(); i++) {
			int[] c = Arrays.copyOf(chords.get(i), chords.get(i).length);
			if (avg - c[0] > 6) {
				c[0] += 12;
				System.out.println("SWAP UP: " + i);
			}
			if (c[c.length - 1] - avg > 6) {
				c[c.length - 1] -= 12;
				System.out.println("SWAP DOWN: " + i);
			}
			Arrays.sort(c);
			squishedChords.add(c);
		}
		System.out.println("NEW AVG: " + MidiUtils.calculateAverageNote(squishedChords));
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
			int originalIndex = modeList.indexOf(new Integer(pitch % 12));
			
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
}
