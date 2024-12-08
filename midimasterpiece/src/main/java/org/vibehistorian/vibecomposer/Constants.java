package org.vibehistorian.vibecomposer;

import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Constants {
    public static final List<Integer> TYPICAL_MIDI_CH_START = Arrays.asList(1,9,11,2,10);
    public static final Map<Integer, List<Integer>> TYPICAL_MIDI_CH = new HashMap<>();
    public static final String TEMPORARY_SEQUENCE_MIDI_NAME = "tempSequenceMidi.mid";
    public static final int[] MILISECOND_ARRAY_STRUM = { 0, 31, 62, 125, 250, 333, 375, 500, 666,
            750, 1000, 1333, 1500, 2000 };
    public static final List<Integer> MILISECOND_LIST_STRUM = Arrays.stream(MILISECOND_ARRAY_STRUM)
            .mapToObj(e -> Integer.valueOf(e)).collect(Collectors.toList());
    public static final int[] MILISECOND_ARRAY_FEEDBACK = { -2000, -1500, -1333, -1000, -750, -666,
            -500, -375, -333, -250, -125, -62, -31, 31, 62, 125, 250, 333, 375, 500, 666, 750, 1000,
            1333, 1500, 2000 };
    public static final List<Integer> MILISECOND_LIST_FEEDBACK = Arrays
            .stream(MILISECOND_ARRAY_FEEDBACK).mapToObj(e -> Integer.valueOf(e))
            .collect(Collectors.toList());
    public static final int[] MILISECOND_ARRAY_DELAY = { 0, 62, 125, 250, 333 };
    public static final int[] MILISECOND_ARRAY_SPLIT = { 625, 750, 875 };
    static final String BUG_HUNT_MESSAGE = "You found a bug! Save your project as a new preset, and send the .xml to my email: vibehistorian@gmail.com!";
    static final String FILENAME_VALID_CHARACTERS = "[a-zA-Z0-9,\\-_ ']";
    static final String FILENAME_VALID_NAME = "^" + FILENAME_VALID_CHARACTERS + "+$";
    static final String MIDIS_FOLDER = "midis";
    static final String MIDI_HISTORY_FOLDER = MIDIS_FOLDER + "/midi_history";
    private static final String DRUMS_FOLDER = "drums";
    static final String PRESET_FOLDER = "presets";
    static final String SOUNDBANK_FOLDER = ".";
    static final String EXPORT_FOLDER = "exports";
    static final String MID_EXTENSION = ".mid";
    static final String SAVED_MIDIS_FOLDER_BASE = "/saved_";
    public static Color[] instColors = { Color.blue, Color.black, Color.green, Color.magenta,
            Color.yellow };
    public static String[] instNames = { "Melody", "Bass", "Chords", "Arps", "Drums" };
    public static String[] instPartNames = { "melody", "bass", "chord", "arp", "drum" };

    static {
        TYPICAL_MIDI_CH.put(0, Arrays.asList(1,7,8));
        TYPICAL_MIDI_CH.put(1, Arrays.asList(9));
        TYPICAL_MIDI_CH.put(2, Arrays.asList(11,12,13,14,15));
        TYPICAL_MIDI_CH.put(3, Arrays.asList(2,3,4,5,6,7,8));
        TYPICAL_MIDI_CH.put(4, Arrays.asList(10));
    }
}
