package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class InstUtils {

	public enum POOL {
		PLUCK, LONG_PAD, CHORD, BASS, DRUM, MELODY, ALL;
	}

	public static final String[] INSTRUMENTS_NAMES = { "0: PIANO", "1: BRIGHT_ACOUSTIC",
			"2: ELECTRIC_GRAND", "3: HONKYTONK", "4: EPIANO", "5: EPIANO2", "6: HARPSICHORD",
			"7: CLAV", "8: CELESTE", "9: GLOCKENSPIEL", "10: MUSIC_BOX", "11: VIBRAPHONE",
			"12: MARIMBA", "13: XYLOPHONE", "14: TUBULAR_BELL", "15: NOTHING", "16: ORGAN",
			"17: ORGAN2", "18: ORGAN3", "19: CHURCH_ORGAN", "20: REED_ORGAN", "21: ACCORDION",
			"22: HARMONICA", "23: BANDNEON", "24: NYLON_GUITAR", "25: STEEL_GUITAR",
			"26: JAZZ_GUITAR", "27: CLEAN_GUITAR", "28: MUTED_GUITAR", "29: OVERDRIVE_GUITAR",
			"30: DISTORTED_GUITAR", "31: GUITAR_HARMONICS", "32: ACOUSTIC_BASS",
			"33: FINGERED_BASS", "34: PICKED_BASS", "35: FRETLESS_BASS", "36: SLAP_BASS",
			"37: SLAP_BASS_2", "38: SYNTH_BASS", "39: SYNTH_BASS_2", "40: VIOLIN", "41: VIOLA",
			"42: CELLO", "43: CONTRABASS", "44: TREMOLO_STRINGS", "45: PIZZICATO_STRINGS",
			"46: HARP", "47: TIMPANI", "48: STRINGS", "49: STRING_ENSEMBLE_2", "50: SYNTH_STRINGS",
			"51: SLOW_STRINGS", "52: AAH", "53: OOH", "54: SYNVOX", "55: ORCHESTRA_HIT",
			"56: TRUMPET", "57: TROMBONE", "58: TUBA", "59: MUTED_TRUMPET", "60: FRENCH_HORN",
			"61: BRASS", "62: SYNTH_BRASS", "63: SYNTH_BRASS_2", "64: SOPRANO_SAX", "65: ALTO_SAX",
			"66: SAXOPHONE", "67: BARITONE_SAX", "68: OBOE", "69: ENGLISH_HORN", "70: BASSOON",
			"71: CLARINET", "72: PICCOLO", "73: FLUTE", "74: RECORDER", "75: PAN_FLUTE",
			"76: BOTTLE_BLOW", "77: SHAKUHACHI", "78: WHISTLE", "79: OCARINA", "80: GMSQUARE_WAVE",
			"81: GMSAW_WAVE", "82: SYNTH_CALLIOPE", "83: CHIFFER_LEAD", "84: CHARANG",
			"85: SOLO_VOX", "86: WHOKNOWS1", "87: WHOKNOWS2", "88: FANTASIA", "89: WARM_PAD",
			"90: POLYSYNTH", "91: SPACE_VOICE", "92: BOWED_GLASS", "93: METAL_PAD", "94: HALO_PAD",
			"95: SWEEP_PAD", "96: ICE_RAIN", "97: SOUNDTRACK", "98: CRYSTAL", "99: ATMOSPHERE",
			"100: BRIGHTNESS", "101: GOBLIN", "102: ECHO_DROPS", "103: STAR_THEME", "104: SITAR",
			"105: BANJO", "106: SHAMISEN", "107: KOTO", "108: KALIMBA", "109: BAGPIPES",
			"110: FIDDLE", "111: SHANNAI", "112: TINKLE_BELL", "113: AGOGO", "114: STEEL_DRUMS",
			"115: WOODBLOCK", "116: TAIKO", "117: TOM", "118: SYNTH_DRUM", "119: REVERSE_CYMBAL",
			"120: FRETNOISE", "121: BREATHNOISE", "122: NATURE", "123: BIRD", "124: TELEPHONE",
			"125: HELICOPTER", "126: APPLAUSE", "127: GUNSHOT" };

	public static final String[] ALL_INST_NAMES = { "0: PIANO", "1: BRIGHT_ACOUSTIC",
			"2: ELECTRIC_GRAND", "3: HONKYTONK", "4: EPIANO", "5: EPIANO2", "6: HARPSICHORD",
			"7: CLAV", "8: CELESTE", "9: GLOCKENSPIEL", "10: MUSIC_BOX", "11: VIBRAPHONE",
			"12: MARIMBA", "13: XYLOPHONE", "14: TUBULAR_BELL", "15: NOTHING", "16: ORGAN",
			"17: ORGAN2", "18: ORGAN3", "19: CHURCH_ORGAN", "20: REED_ORGAN", "21: ACCORDION",
			"22: HARMONICA", "23: BANDNEON", "24: NYLON_GUITAR", "25: STEEL_GUITAR",
			"26: JAZZ_GUITAR", "27: CLEAN_GUITAR", "28: MUTED_GUITAR", "29: OVERDRIVE_GUITAR",
			"30: DISTORTED_GUITAR", "31: GUITAR_HARMONICS", "32: ACOUSTIC_BASS",
			"33: FINGERED_BASS", "34: PICKED_BASS", "35: FRETLESS_BASS", "36: SLAP_BASS",
			"37: SLAP_BASS_2", "38: SYNTH_BASS", "39: SYNTH_BASS_2", "40: VIOLIN", "41: VIOLA",
			"42: CELLO", "43: CONTRABASS", "44: TREMOLO_STRINGS", "45: PIZZICATO_STRINGS",
			"46: HARP", "47: TIMPANI", "48: STRINGS", "49: STRING_ENSEMBLE_2", "50: SYNTH_STRINGS",
			"51: SLOW_STRINGS", "52: AAH", "53: OOH", "54: SYNVOX", "55: ORCHESTRA_HIT",
			"56: TRUMPET", "57: TROMBONE", "58: TUBA", "59: MUTED_TRUMPET", "60: FRENCH_HORN",
			"61: BRASS", "62: SYNTH_BRASS", "63: SYNTH_BRASS_2", "64: SOPRANO_SAX", "65: ALTO_SAX",
			"66: SAXOPHONE", "67: BARITONE_SAX", "68: OBOE", "69: ENGLISH_HORN", "70: BASSOON",
			"71: CLARINET", "72: PICCOLO", "73: FLUTE", "74: RECORDER", "75: PAN_FLUTE",
			"76: BOTTLE_BLOW", "77: SHAKUHACHI", "78: WHISTLE", "79: OCARINA", "80: GMSQUARE_WAVE",
			"81: GMSAW_WAVE", "82: SYNTH_CALLIOPE", "83: CHIFFER_LEAD", "84: CHARANG",
			"85: SOLO_VOX", "86: WHOKNOWS1", "87: WHOKNOWS2", "88: FANTASIA", "89: WARM_PAD",
			"90: POLYSYNTH", "91: SPACE_VOICE", "92: BOWED_GLASS", "93: METAL_PAD", "94: HALO_PAD",
			"95: SWEEP_PAD", "96: ICE_RAIN", "97: SOUNDTRACK", "98: CRYSTAL", "99: ATMOSPHERE",
			"100: BRIGHTNESS", "101: GOBLIN", "102: ECHO_DROPS", "103: STAR_THEME", "104: SITAR",
			"105: BANJO", "106: SHAMISEN", "107: KOTO", "108: KALIMBA", "109: BAGPIPES",
			"110: FIDDLE", "111: SHANNAI", "112: TINKLE_BELL", "113: AGOGO", "114: STEEL_DRUMS",
			"115: WOODBLOCK", "116: TAIKO", "117: TOM", "118: SYNTH_DRUM", "119: REVERSE_CYMBAL" };

	public static final String[] MELODY_INST_NAMES = { "0: PIANO", "1: BRIGHT_ACOUSTIC",
			"3: HONKYTONK", "4: EPIANO", "5: EPIANO2", "6: HARPSICHORD", "7: CLAV", "8: CELESTE",
			"10: MUSIC_BOX", "11: VIBRAPHONE", "12: MARIMBA", "14: TUBULAR_BELL", "16: ORGAN",
			"17: ORGAN2", "18: ORGAN3", "20: REED_ORGAN", "21: ACCORDION", "23: BANDNEON",
			"24: NYLON_GUITAR", "25: STEEL_GUITAR", "26: JAZZ_GUITAR", "28: MUTED_GUITAR",
			"29: OVERDRIVE_GUITAR", "32: ACOUSTIC_BASS", "33: FINGERED_BASS", "34: PICKED_BASS",
			"36: SLAP_BASS", "37: SLAP_BASS_2", "38: SYNTH_BASS", "41: VIOLA", "42: CELLO",
			"43: CONTRABASS", "44: TREMOLO_STRINGS", "45: PIZZICATO_STRINGS", "46: HARP",
			"48: STRINGS", "49: STRING_ENSEMBLE_2", "50: SYNTH_STRINGS", "52: AAH", "53: OOH",
			"54: SYNVOX", "56: TRUMPET", "57: TROMBONE", "58: TUBA", "59: MUTED_TRUMPET",
			"60: FRENCH_HORN", "63: SYNTH_BRASS_2", "64: SOPRANO_SAX", "69: ENGLISH_HORN",
			"70: BASSOON", "71: CLARINET", "72: PICCOLO", "73: FLUTE", "74: RECORDER",
			"75: PAN_FLUTE", "76: BOTTLE_BLOW", "77: SHAKUHACHI", "78: WHISTLE", "79: OCARINA",
			"80: GMSQUARE_WAVE", "81: GMSAW_WAVE", "82: SYNTH_CALLIOPE", "85: SOLO_VOX",
			"87: SYNTH_BASSLEAD", "88: FANTASIA", "89: WARM_PAD", "90: POLYSYNTH",
			"91: SPACE_VOICE", "92: BOWED_GLASS", "93: METAL_PAD", "94: HALO_PAD", "96: ICE_RAIN",
			"98: CRYSTAL", "99: ATMOSPHERE", "100: BRIGHTNESS", "101: GOBLIN", "103: STAR_THEME",
			"105: BANJO", "106: SHAMISEN", "107: KOTO", "108: KALIMBA" };

	public static final String[] BASS_INST_NAMES = { "0: PIANO", "1: BRIGHT_ACOUSTIC", "4: EPIANO",
			"53: OOH", "54: SYNVOX", "73: FLUTE", "74: RECORDER", "75: PAN_FLUTE",
			"82: SYNTH_CALLIOPE", "85: SOLO_VOX", "91: SPACE_VOICE", "102: ECHO_DROPS" };
	public static final String[] CHORD_INST_NAMES = { "4: EPIANO", "16: ORGAN", "17: ORGAN2",
			"32: ACOUSTIC_BASS", "44: TREMOLO_STRINGS", "48: STRINGS", "49: STRING_ENSEMBLE_2",
			"52: AAH", "70: BASSOON" };
	public static final String[] PLUCK_INST_NAMES = { "1: BRIGHT_ACOUSTIC", "3: HONKYTONK",
			"5: EPIANO2", "8: CELESTE", "10: MUSIC_BOX", "11: VIBRAPHONE", "12: MARIMBA",
			"13: XYLOPHONE", "15: NOTHING", "25: STEEL_GUITAR", "27: CLEAN_GUITAR",
			"32: ACOUSTIC_BASS", "33: FINGERED_BASS", "34: PICKED_BASS", "35: FRETLESS_BASS",
			"36: SLAP_BASS", "45: PIZZICATO_STRINGS", "46: HARP", "88: FANTASIA" };
	public static final String[] LONG_INST_NAMES = { "16: ORGAN", "17: ORGAN2", "20: REED_ORGAN",
			"21: ACCORDION", "22: HARMONICA", "23: BANDNEON", "40: VIOLIN", "41: VIOLA",
			"42: CELLO", "43: CONTRABASS", "44: TREMOLO_STRINGS", "48: STRINGS",
			"49: STRING_ENSEMBLE_2", "51: SLOW_STRINGS", "56: TRUMPET", "57: TROMBONE", "58: TUBA",
			"60: FRENCH_HORN", "61: BRASS", "64: SOPRANO_SAX", "65: ALTO_SAX", "67: BARITONE_SAX",
			"68: OBOE", "69: ENGLISH_HORN", "70: BASSOON", "71: CLARINET", "72: PICCOLO",
			"74: RECORDER", "76: BOTTLE_BLOW", "91: SPACE_VOICE", "92: BOWED_GLASS",
			"93: METAL_PAD", "94: HALO_PAD", "95: SWEEP_PAD" };
	public static final String[] DRUM_INST_NAMES = { "35: BASSKICK", "36: KICK", "37: SIDE_STICK",
			"38: SNARE", "39: CLAP", "40: EL.SNARE", "41: FLOOR_TOM", "42: CLOSED_HH",
			"44: PEDAL_HH", "46: OPEN_HH", "47: LO-MID_TOM", "53: RIDE", "54: TAMBOURINE",
			"60: HI_BONGO", "61: LOW_BONGO", "82: SHAKER" };
	public static final Integer[] DRUM_INST_NUMBERS = makeDrumInstNumbers();
	public static final String[] DRUM_INST_NAMES_SEMI = makeDrumInstNamesSemi();
	public static final Integer[] DRUM_INST_NUMBERS_SEMI = makeDrumInstNumbersSemi();


	public static List<Integer> getInstNumbers(String[] instArray) {
		return Arrays.asList(instArray).stream().map(e -> Integer.valueOf(e.split(": ")[0].trim()))
				.collect(Collectors.toList());
	}

	private static Integer[] makeDrumInstNumbers() {
		Integer[] drumInstNumbers = new Integer[DRUM_INST_NAMES.length];
		for (int i = 0; i < drumInstNumbers.length; i++) {
			String numberPart = DRUM_INST_NAMES[i].split(": ")[0];
			drumInstNumbers[i] = Integer.valueOf(numberPart.trim());
		}
		return drumInstNumbers;
	}

	private static Integer[] makeDrumInstNumbersSemi() {
		Integer[] drumInstNumbers = new Integer[DRUM_INST_NAMES.length];
		for (int i = 0; i < drumInstNumbers.length; i++) {
			drumInstNumbers[i] = i + 36;
		}
		return drumInstNumbers;
	}

	private static String[] makeDrumInstNamesSemi() {
		String[] drumInstStrings = new String[DRUM_INST_NAMES.length];
		for (int i = 0; i < drumInstStrings.length; i++) {
			String namePart = DRUM_INST_NAMES[i].split(": ")[1];
			drumInstStrings[i] = (i + 36) + ": " + namePart;
		}
		return drumInstStrings;
	}

	public static String[] combineInstrumentPools(String[]... instPools) {
		Set<String> allInstruments = new HashSet<>();
		for (String[] pool : instPools) {
			allInstruments.addAll(Arrays.asList(pool));
		}
		List<String> allInstrumentsSorted = new ArrayList<>(allInstruments);
		Collections.sort(allInstrumentsSorted, (o1, o2) -> Integer.valueOf(o1.split(": ")[0].trim())
				.compareTo(Integer.valueOf(o2.split(": ")[0].trim())));
		return allInstrumentsSorted.toArray(new String[] {});
	}

	public static final String[] DRUM_KITS = { "0: DRUMKIT0", "1: DRUMKIT1", "2: DRUMKIT2",
			"3: DRUMKIT3" };

	public static final List<Integer> DRUM_KIT_NUMBERS = Arrays.asList(DRUM_KITS).stream()
			.map(e -> Integer.valueOf(e.split(": ")[0].trim())).collect(Collectors.toList());

	public static Map<POOL, String[]> INST_POOLS = new HashMap<>();

	public static void initNormalInsts() {
		INST_POOLS.put(POOL.PLUCK, PLUCK_INST_NAMES);
		INST_POOLS.put(POOL.LONG_PAD, LONG_INST_NAMES);
		INST_POOLS.put(POOL.CHORD, LONG_INST_NAMES);
		INST_POOLS.put(POOL.BASS, BASS_INST_NAMES);
		INST_POOLS.put(POOL.DRUM, DRUM_INST_NAMES);
		INST_POOLS.put(POOL.MELODY, MELODY_INST_NAMES);
		INST_POOLS.put(POOL.ALL, ALL_INST_NAMES);
	}

	public static void initAllInsts() {
		INST_POOLS.put(POOL.PLUCK, INSTRUMENTS_NAMES);
		INST_POOLS.put(POOL.LONG_PAD, INSTRUMENTS_NAMES);
		INST_POOLS.put(POOL.CHORD, INSTRUMENTS_NAMES);
		INST_POOLS.put(POOL.BASS, INSTRUMENTS_NAMES);
		INST_POOLS.put(POOL.DRUM, DRUM_INST_NAMES);
		INST_POOLS.put(POOL.MELODY, INSTRUMENTS_NAMES);
		INST_POOLS.put(POOL.ALL, INSTRUMENTS_NAMES);
	}

	public static Integer getInstByIndex(int index, POOL instPool) {
		return getInstByIndex(index, INST_POOLS.get(instPool));
	}

	public static Integer getInstByIndex(int index, String[] instArray) {
		List<Integer> instPoolNumbers = getInstNumbers(instArray);
		return instPoolNumbers.get(index % instPoolNumbers.size());
	}

	static {
		initNormalInsts();
	}
}
