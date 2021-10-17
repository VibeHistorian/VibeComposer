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
		PLUCK, LONG, CHORD, BASS, DRUM, MELODY, ALL;
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

	public static final String[] ALL_INST_NAMES = { "PIANO = 0 ", "BRIGHT_ACOUSTIC = 1 ",
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
			"SYNTH_DRUM = 118 ", "REVERSE_CYMBAL = 119 " };

	public static final String[] MELODY_INST_NAMES = { "PIANO = 0 ", "BRIGHT_ACOUSTIC = 1 ",
			"HONKYTONK = 3 ", "EPIANO = 4 ", "EPIANO2 = 5 ", "HARPSICHORD = 6 ", "CLAV = 7 ",
			"CELESTE = 8 ", "MUSIC_BOX = 10 ", "VIBRAPHONE = 11 ", "MARIMBA = 12 ",
			"TUBULAR_BELL = 14 ", "ORGAN = 16 ", "ORGAN2 = 17 ", "ORGAN3 = 18 ", "REED_ORGAN = 20 ",
			"ACCORDION = 21 ", "BANDNEON = 23 ", "NYLON_GUITAR = 24 ", "STEEL_GUITAR = 25 ",
			"JAZZ_GUITAR = 26 ", "MUTED_GUITAR = 28 ", "OVERDRIVE_GUITAR = 29 ",
			"ACOUSTIC_BASS = 32 ", "FINGERED_BASS = 33 ", "PICKED_BASS = 34 ", "SLAP_BASS = 36 ",
			"SLAP_BASS_2 = 37 ", "SYNTH_BASS = 38 ", "VIOLA = 41 ", "CELLO = 42 ",
			"CONTRABASS = 43 ", "TREMOLO_STRINGS = 44 ", "PIZZICATO_STRINGS = 45 ", "HARP = 46 ",
			"STRINGS = 48 ", "STRING_ENSEMBLE_2 = 49 ", "SYNTH_STRINGS = 50 ", "AAH = 52 ",
			"OOH = 53 ", "SYNVOX = 54 ", "TRUMPET = 56 ", "TROMBONE = 57 ", "TUBA = 58 ",
			"MUTED_TRUMPET = 59 ", "FRENCH_HORN = 60 ", "SYNTH_BRASS_2 = 63 ", "SOPRANO_SAX = 64 ",
			"ENGLISH_HORN = 69 ", "BASSOON = 70 ", "CLARINET = 71 ", "PICCOLO = 72 ", "FLUTE = 73 ",
			"RECORDER = 74 ", "PAN_FLUTE = 75 ", "BOTTLE_BLOW = 76 ", "SHAKUHACHI = 77 ",
			"WHISTLE = 78 ", "OCARINA = 79 ", "GMSQUARE_WAVE = 80 ", "GMSAW_WAVE = 81 ",
			"SYNTH_CALLIOPE = 82 ", "SOLO_VOX = 85 ", "SYNTH_BASSLEAD = 87 ", "FANTASIA = 88 ",
			"WARM_PAD = 89 ", "POLYSYNTH = 90 ", "SPACE_VOICE = 91 ", "BOWED_GLASS = 92 ",
			"METAL_PAD = 93 ", "HALO_PAD = 94 ", "ICE_RAIN = 96 ", "CRYSTAL = 98 ",
			"ATMOSPHERE = 99 ", "BRIGHTNESS = 100 ", "GOBLIN = 101 ", "STAR_THEME = 103 ",
			"BANJO = 105 ", "SHAMISEN = 106 ", "KOTO = 107 ", "KALIMBA = 108 " };

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

	public static Map<POOL, String[]> INST_POOLS = new HashMap<>();

	public static void initNormalInsts() {
		INST_POOLS.put(POOL.PLUCK, PLUCK_INST_NAMES);
		INST_POOLS.put(POOL.LONG, LONG_INST_NAMES);
		INST_POOLS.put(POOL.CHORD, LONG_INST_NAMES);
		INST_POOLS.put(POOL.BASS, BASS_INST_NAMES);
		INST_POOLS.put(POOL.DRUM, DRUM_INST_NAMES);
		INST_POOLS.put(POOL.MELODY, MELODY_INST_NAMES);
		INST_POOLS.put(POOL.ALL, ALL_INST_NAMES);
	}

	public static void initAllInsts() {
		INST_POOLS.put(POOL.PLUCK, INSTRUMENTS_NAMES);
		INST_POOLS.put(POOL.LONG, INSTRUMENTS_NAMES);
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
