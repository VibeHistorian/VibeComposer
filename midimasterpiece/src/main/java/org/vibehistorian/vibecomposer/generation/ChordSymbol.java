package org.vibehistorian.vibecomposer.generation;

import org.vibehistorian.vibecomposer.MidiUtils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsed chord identity; root spelling and bass inversion survive color operations. */
public final class ChordSymbol {
    private static final java.util.Map<String,int[]> EXTRA = new java.util.LinkedHashMap<>();
    static {
        EXTRA.put("m7b5",new int[] {0,3,6,10}); EXTRA.put("dim7",new int[] {0,3,6,9});
        EXTRA.put("9",new int[] {0,4,7,10,14}); EXTRA.put("13",new int[] {0,4,7,10,14,21});
        EXTRA.put("7b9",new int[] {0,4,7,10,13}); EXTRA.put("7#9",new int[] {0,4,7,10,15});
        EXTRA.put("7#11",new int[] {0,4,7,10,18});
    }
    public static List<String> supportedQualities() {
        List<String> result = new java.util.ArrayList<>(MidiUtils.SPICE_NAMES_LIST); result.addAll(EXTRA.keySet()); return result;
    }
    public static int[] extraPitches(String rootAndQuality) {
        Matcher match = SYNTAX.matcher(rootAndQuality);
        if (!match.matches() || !EXTRA.containsKey(match.group(3))) { return null; }
        ChordSymbol symbol = parse(rootAndQuality); return symbol.pitches(false);
    }
    private static final Pattern SYNTAX = Pattern.compile("^([A-Ga-g])([#b♯♭]?)([^.]*)(?:\\.(-?\\d+))?$");
    public final String rootName;
    public final int root;
    public final String quality;
    public final Integer inversion;

    private ChordSymbol(String rootName, int root, String quality, Integer inversion) {
        this.rootName = rootName; this.root = root; this.quality = quality; this.inversion = inversion;
    }

    public static ChordSymbol parse(String text) {
        Matcher match = SYNTAX.matcher(text.trim());
        if (!match.matches() || !supportedQualities().contains(match.group(3))) {
            throw new IllegalArgumentException("Unsupported chord symbol: " + text);
        }
        String letter = match.group(1).toUpperCase();
        int root = new int[] { 0, 2, 4, 5, 7, 9, 11 }[MidiUtils.CHORD_FIRST_LETTERS.indexOf(letter)];
        String accidental = match.group(2);
        root = Math.floorMod(root + (accidental.equals("#") || accidental.equals("♯") ? 1
                : accidental.equals("b") || accidental.equals("♭") ? -1 : 0), 12);
        Integer inversion = match.group(4) == null ? null : Integer.valueOf(match.group(4));
        if (inversion != null && Math.abs((long) inversion) > 32) {
            throw new IllegalArgumentException("Chord inversion outside supported range: " + text);
        }
        return new ChordSymbol(letter + accidental, root, match.group(3), inversion);
    }

    public ChordSymbol withQuality(String value) {
        if (!supportedQualities().contains(value)) { throw new IllegalArgumentException("Unknown chord quality: " + value); }
        return new ChordSymbol(rootName, root, value, inversion);
    }
    public ChordSymbol withInversion(Integer value) { return new ChordSymbol(rootName,root,quality,value); }

    public int[] pitches(boolean voiced) {
        int[] result = EXTRA.containsKey(quality) ? Arrays.stream(EXTRA.get(quality)).map(interval -> 60 + root + interval).toArray()
                : MidiUtils.getNormalMappedChord(MidiUtils.SEMITONE_LETTERS.get(root) + quality, true);
        return voiced ? MidiUtils.chordInversion(result, inversion) : result;
    }

    public List<String> extensions() {
        if (quality.equals("")) { return Arrays.asList("maj7", "maj6", "maj9", "maj13"); }
        if (quality.equals("m")) { return Arrays.asList("m7", "m6", "m9", "m13"); }
        if (quality.equals("maj7")) { return Arrays.asList("maj9", "maj13"); }
        if (quality.equals("m7")) { return Arrays.asList("m9", "m13"); }
        if (quality.equals("maj9")) { return Arrays.asList("maj13"); }
        if (quality.equals("m9")) { return Arrays.asList("m13"); }
        if (quality.equals("7")) { return Arrays.asList("9","13","7b9","7#9","7#11"); }
        return java.util.Collections.emptyList();
    }

    /** Derive a display identity from the harmonic root, never from the lowest voiced note. */
    public static String identify(int rootPitch, int[] pitches) {
        java.util.Set<Integer> actual = new java.util.TreeSet<>();
        for (int pitch : pitches) { actual.add(Math.floorMod(pitch,12)); }
        String root = MidiUtils.SEMITONE_LETTERS.get(Math.floorMod(rootPitch,12));
        for (String quality : supportedQualities()) {
            ChordSymbol candidate = parse(root + quality);
            java.util.Set<Integer> expected = new java.util.TreeSet<>();
            for (int pitch : candidate.pitches(false)) { expected.add(pitch % 12); }
            if (actual.equals(expected)) { return root + quality; }
        }
        return MidiUtils.makeSpelledChord(pitches);
    }

    @Override public String toString() { return rootName + quality + (inversion == null ? "" : "." + inversion); }
}
