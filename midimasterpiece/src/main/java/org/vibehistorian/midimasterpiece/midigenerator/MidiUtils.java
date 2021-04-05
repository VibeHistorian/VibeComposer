package org.vibehistorian.midimasterpiece.midigenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

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
	
	public static final String[] BASS_INST_NAMES = { "PIANO = 0", "BRIGHT_ACOUSTIC = 1",
			"EPIANO = 4", "AAH = 52", "OOH = 53", "SYNVOX = 54", "FLUTE = 73", "RECORDER = 74",
			"PAN_FLUTE = 75", "SYNTH_CALLIOPE = 82", "SOLO_VOX = 85", "SPACE_VOICE = 91",
			"ECHO_DROPS = 102" };
	
	public static final String[] CHORD_INST_NAMES = { "PIANO = 0", "BRIGHT_ACOUSTIC = 1",
			"EPIANO = 4", "CELESTE = 8", "MUSIC_BOX = 10", "VIBRAPHONE = 11", "MARIMBA = 12",
			"XYLOPHONE = 13", "ORGAN = 16", "ORGAN2 = 17", "REED_ORGAN = 20", "STEEL_GUITAR = 25",
			"CLEAN_GUITAR = 27", "ACOUSTIC_BASS = 32", "FINGERED_BASS = 33", "PICKED_BASS = 34",
			"FRETLESS_BASS = 35", "SLAP_BASS = 36", "TREMOLO_STRINGS = 44",
			"PIZZICATO_STRINGS = 45", "HARP = 46", "STRINGS = 48", "STRING_ENSEMBLE_2 = 49",
			"BASSOON = 70", "FANTASIA = 88", };
	
	public static final String[] PLUCKY_INST_NAMES = { "PIANO = 0", "BRIGHT_ACOUSTIC = 1",
			"HONKYTONK = 3", "EPIANO = 4", "EPIANO2 = 5", "HARPSICHORD = 6", "CLAV = 7",
			"CELESTE = 8", "MUSIC_BOX = 10", "VIBRAPHONE = 11", "MARIMBA = 12", "XYLOPHONE = 13",
			"TUBULAR_BELL = 14", "NOTHING = 15", "STEEL_GUITAR = 25", "CLEAN_GUITAR = 27",
			"ACOUSTIC_BASS = 32", "FINGERED_BASS = 33", "PICKED_BASS = 34", "FRETLESS_BASS = 35",
			"SLAP_BASS = 36", "PIZZICATO_STRINGS = 45", "HARP = 46", "AAH = 52", "OOH = 53",
			"FANTASIA = 88", "SITAR = 104", "BANJO = 105", "SHAMISEN = 106", "KOTO = 107",
			"AGOGO = 113", "TAIKO = 116" };
	
	public static final String[] SUSTAINY_INST_NAMES = { "ORGAN = 16", "ORGAN2 = 17",
			"REED_ORGAN = 20", "ACCORDION = 21", "HARMONICA = 22", "BANDNEON = 23", "VIOLIN = 40",
			"VIOLA = 41", "CELLO = 42", "CONTRABASS = 43", "TREMOLO_STRINGS = 44", "STRINGS = 48",
			"STRING_ENSEMBLE_2 = 49", "SLOW_STRINGS = 51", "TRUMPET = 56", "TROMBONE = 57",
			"TUBA = 58", "FRENCH_HORN = 60", "BRASS = 61", "SOPRANO_SAX = 64", "ALTO_SAX = 65",
			"BARITONE_SAX = 67", "OBOE = 68", "ENGLISH_HORN = 69", "BASSOON = 70", "CLARINET = 71",
			"PICCOLO = 72", "RECORDER = 74", "BOTTLE_BLOW = 76", "SPACE_VOICE = 91",
			"BOWED_GLASS = 92", "METAL_PAD = 93", "HALO_PAD = 94", "SWEEP_PAD = 95" };
	
	public static final String[] DRUM_INST_NAMES = { "KICK = 36", "SNARE = 38", "CLOSED_HH = 42",
			"CYMBAL = 53", "TOM = 60" };
	
	
	public static List<Integer> getInstNumbers(String[] instArray) {
		return Arrays.asList(instArray).stream().map(e -> Integer.valueOf(e.split(" = ")[1]))
				.collect(Collectors.toList());
	}
	
	public static String[] combineInstrumentPools(String[]... instPools) {
		Set<String> allInstruments = new HashSet<>();
		for (String[] pool : instPools) {
			allInstruments.addAll(Arrays.asList(pool));
		}
		List<String> allInstrumentsSorted = new ArrayList<>(allInstruments);
		Collections.sort(allInstrumentsSorted, (o1, o2) -> Integer.valueOf(o1.split(" = ")[1])
				.compareTo(Integer.valueOf(o2.split(" = ")[1])));
		return allInstrumentsSorted.toArray(new String[] {});
	}
	
	public static final String[] DRUM_KITS = { "DRUMKIT0 = 0", "DRUMKIT1 = 1", "DRUMKIT2 = 2",
			"DRUMKIT3 = 3" };
	
	public static final List<Integer> DRUM_KIT_NUMBERS = Arrays.asList(DRUM_KITS).stream()
			.map(e -> Integer.valueOf(e.split(" = ")[1])).collect(Collectors.toList());
	
	public static final Map<PARTS, String[]> PART_INST_NAMES = new HashMap<>();
	static {
		PART_INST_NAMES.put(PARTS.MELODY, PLUCKY_INST_NAMES);
		PART_INST_NAMES.put(PARTS.CHORDS1, CHORD_INST_NAMES);
		PART_INST_NAMES.put(PARTS.CHORDS2, PLUCKY_INST_NAMES);
		PART_INST_NAMES.put(PARTS.ARP1, PLUCKY_INST_NAMES);
		PART_INST_NAMES.put(PARTS.ARP2, PLUCKY_INST_NAMES);
		PART_INST_NAMES.put(PARTS.BASSROOTS, BASS_INST_NAMES);
		PART_INST_NAMES.put(PARTS.DRUMS, DRUM_INST_NAMES);
	}
	
	
}
