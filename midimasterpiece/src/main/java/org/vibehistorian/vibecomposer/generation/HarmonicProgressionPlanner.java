package org.vibehistorian.vibecomposer.generation;

import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.MidiUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Phrase-aware alternatives to the historical random adjacency walk. Inputs use the app's normalized key. */
public final class HarmonicProgressionPlanner {
    public enum Strategy { CURRENT, FUNCTIONAL, FIFTHS, POP_LOOP, MODAL }
    private static final int BEAM_WIDTH = 32;
    private static final int[] FIFTHS = { 0, 3, 6, 2, 5, 1, 4 };
    private static final int[][] POP = { { 0, 4, 5, 3 }, { 0, 5, 3, 4 }, { 5, 3, 0, 4 }, { 0, 3, 5, 4 } };

    private static final class Candidate {
        final List<String> chords;
        final double score;
        final long serial;
        Candidate(List<String> chords, double score, long serial) { this.chords = chords; this.score = score; this.serial = serial; }
    }

    private HarmonicProgressionPlanner() { }

    public static List<String> generate(GUIConfig config, long seed, int length, String first, String last,
            Map<Integer, String> locked) {
        if (length < 1 || length > 32) { throw new IllegalArgumentException("Progression length must be 1..32"); }
        Strategy strategy = config.getProgressionStrategy();
        Map<Integer,ChordLock> partial = new java.util.HashMap<>();
        String[] declared = config.getCustomChords().replace(" ","").split(",");
        for (ChordLock lock : config.getChordLocks()) {
            if (lock.getIndex() >= length || lock.getIndex() >= declared.length) { throw new IllegalArgumentException("Chord property lock has no source event"); }
            partial.put(lock.getIndex(),lock);
        }
        if (strategy == Strategy.CURRENT) { throw new IllegalArgumentException("Current strategy uses the compatibility generator"); }
        for (Map.Entry<Integer, String> entry : locked.entrySet()) {
            if (entry.getKey() < 0 || entry.getKey() >= length) { throw new IllegalArgumentException("Chord lock outside progression"); }
            ChordSymbol.parse(entry.getValue());
        }
        validateEndpoint(first, locked.get(0));
        validateEndpoint(last, locked.get(length - 1));
        if (length == 1 && first != null && last != null && !first.equals(last)) {
            throw new IllegalArgumentException("One chord cannot satisfy different first and last locks");
        }
        Random structure = new Random(seed);
        List<String> vocabulary = vocabulary(config);
        int[] pop = POP[structure.nextInt(POP.length)];
        List<Candidate> beam = Collections.singletonList(new Candidate(Collections.emptyList(), 0, 0));
        long serial = 1;
        for (int index = 0; index < length; index++) {
            String hard = locked.get(index);
            if (index == 0 && first != null) { hard = first; }
            if (index == length - 1 && last != null) { hard = last; }
            List<String> choices = hard == null ? vocabulary : Collections.singletonList(hard);
            if (partial.containsKey(index)) {
                ChordLock property = partial.get(index); ChordSymbol original = ChordSymbol.parse(declared[index]);
                List<String> filtered = new ArrayList<>();
                List<String> proposals = new ArrayList<>(choices);
                if (hard == null) {
                    proposals.add(original.toString());
                    for (String base : vocabulary) {
                        ChordSymbol candidate = ChordSymbol.parse(base);
                        if ((property.getMask() & ChordLock.ROOT) != 0) { candidate = original.withQuality(candidate.quality); }
                        if ((property.getMask() & ChordLock.QUALITY) != 0) { candidate = candidate.withQuality(original.quality); }
                        for (int inversion = -2; inversion <= 3; inversion++) { proposals.add(candidate.withInversion(inversion).toString()); }
                    }
                }
                for (String proposal : proposals) { if (property.matches(original,ChordSymbol.parse(proposal)) && !filtered.contains(proposal)) { filtered.add(proposal); } }
                if (filtered.isEmpty()) { throw new IllegalArgumentException("Endpoint conflicts with chord property lock at " + index); }
                choices = filtered;
            }
            List<Candidate> next = new ArrayList<>();
            Random slot = new Random(seed ^ (0x9e3779b97f4a7c15L * (index + 1)));
            double[] variation = new double[choices.size()];
            for (int i = 0; i < variation.length; i++) { variation[i] = slot.nextDouble() * 1.5; }
            for (Candidate state : beam) {
                for (int choice = 0; choice < choices.size(); choice++) {
                    String name = choices.get(choice);
                    ChordSymbol chord = ChordSymbol.parse(name);
                    int degree = Arrays.asList(config.getScaleMode().noteAdjustScale).indexOf(chord.root);
                    double score = state.score + variation[choice];
                    ChordSymbol previous = index == 0 ? null : ChordSymbol.parse(state.chords.get(index - 1));
                    if (previous != null) { score += transition(previous, chord, config.isAllowChordRepeats()); }
                    if (strategy == Strategy.FUNCTIONAL) {
                        if (index == 0) { score += degree == 0 ? 8 : degree == 5 ? 3 : 0; }
                        if (index == length - 1) { score += degree == 0 ? 12 : degree == 4 ? 3 : -3; }
                        if (index == length - 2) { score += degree == 4 ? 9 : degree == 6 ? 4 : -1; }
                        if (index == length - 3) { score += degree == 1 || degree == 3 ? 5 : 0; }
                    } else if (strategy == Strategy.FIFTHS) {
                        int start = first == null ? 0 : Math.max(0,indexInCycle(Arrays.asList(config.getScaleMode().noteAdjustScale).indexOf(ChordSymbol.parse(first).root)));
                        score += degree == FIFTHS[(start + index) % FIFTHS.length] ? 16 : -5;
                    } else if (strategy == Strategy.POP_LOOP) {
                        score += degree == pop[index % pop.length] ? 10 : -2;
                    } else {
                        score += degree == 0 ? 5 : degree == 3 || degree == 6 ? 3 : 0;
                        if (index > 0 && previous.root == chord.root) { score += 2; }
                    }
                    if (index == length - 1 && strategy == Strategy.POP_LOOP && !state.chords.isEmpty()) {
                        score += transition(chord, ChordSymbol.parse(state.chords.get(0)), config.isAllowChordRepeats());
                    }
                    if (index >= 4 && config.getLongProgressionSimilarity() > 0
                            && name.equals(state.chords.get(index - 4))) { score += config.getLongProgressionSimilarity() / 25.0; }
                    List<String> path = new ArrayList<>(state.chords);
                    path.add(name);
                    next.add(new Candidate(path, score, serial++));
                }
            }
            next.sort(Comparator.<Candidate>comparingDouble(candidate -> candidate.score).reversed()
                    .thenComparingLong(candidate -> candidate.serial));
            beam = new ArrayList<>(next.subList(0, Math.min(BEAM_WIDTH, next.size())));
        }
        List<String> result = new ArrayList<>(beam.get(0).chords);
        for (int index = 0; index < result.size(); index++) {
            if (locked.containsKey(index) || partial.containsKey(index) && (partial.get(index).getMask() & ChordLock.QUALITY) != 0
                    || index == 0 && first != null || index == length - 1 && last != null) { continue; }
            long colorSeed = seed ^ (0xd1b54a32d192ed03L * (index + 1));
            result.set(index, color(result.get(index), config, colorSeed));
        }
        return Collections.unmodifiableList(result);
    }

    /** Color adds tones without changing root, triad quality, inversion, or existing guide tones. */
    public static String color(String text, GUIConfig config, long seed) {
        ChordSymbol original;
        try { original = ChordSymbol.parse(text); } catch (IllegalArgumentException unsupported) { return text; }
        Random random = new Random(seed);
        if (random.nextInt(100) >= config.getSpiceChance()) { return text; }
        List<ChordSymbol> candidates = new ArrayList<>();
        for (String quality : original.extensions()) {
            if (!config.isEnable9th13th() && (quality.contains("9") || quality.contains("13") || quality.contains("11"))
                    || !config.isDimAug6thEnabled() && MidiUtils.BANNED_DIM_AUG_6_LIST.contains(quality)) { continue; }
            ChordSymbol candidate = original.withQuality(quality);
            boolean valid = candidate.pitches(true)[0] % 12 == original.pitches(true)[0] % 12;
            for (int pitch : original.pitches(false)) {
                valid &= Arrays.stream(candidate.pitches(false)).anyMatch(p -> p % 12 == pitch % 12);
            }
            if (config.isSpiceForceScale()) {
                for (int pitch : candidate.pitches(false)) {
                    boolean existing = Arrays.stream(original.pitches(false)).anyMatch(p -> p % 12 == pitch % 12);
                    valid &= existing || Arrays.asList(config.getScaleMode().noteAdjustScale).contains(pitch % 12);
                }
            }
            if (valid) { candidates.add(candidate); }
        }
        return candidates.isEmpty() ? text : candidates.get(random.nextInt(candidates.size())).toString();
    }

    /** Local edits rank both joins and change only the requested event. */
    public static List<String> reroll(List<String> source, int index, GUIConfig config, long seed) {
        if (index < 0 || index >= source.size()) { throw new IllegalArgumentException("Reroll index outside progression"); }
        ChordSymbol original = ChordSymbol.parse(source.get(index));
        ChordSymbol before = source.size() > 1 ? ChordSymbol.parse(source.get(Math.floorMod(index - 1, source.size()))) : null;
        ChordSymbol after = source.size() > 1 ? ChordSymbol.parse(source.get((index + 1) % source.size())) : null;
        List<Candidate> candidates = new ArrayList<>();
        ChordLock property = config.getChordLocks().stream().filter(lock -> lock.getIndex() == index).findFirst().orElse(new ChordLock(index,0));
        long serial = 0;
        for (String base : vocabulary(config)) {
            ChordSymbol chord = ChordSymbol.parse(base);
            if ((property.getMask() & ChordLock.ROOT) != 0) { chord = original.withQuality(chord.quality); }
            if ((property.getMask() & ChordLock.QUALITY) != 0) { chord = chord.withQuality(original.quality); }
            chord = chord.withInversion(original.inversion);
            if (!property.matches(original,chord) || chord.toString().equals(original.toString())) { continue; }
            double score = (before == null ? 0 : transition(before,chord,config.isAllowChordRepeats()))
                    + (after == null ? 0 : transition(chord,after,config.isAllowChordRepeats()));
            candidates.add(new Candidate(Collections.singletonList(chord.toString()),score,serial++));
        }
        candidates.sort(Comparator.<Candidate>comparingDouble(candidate -> candidate.score).reversed()
                .thenComparingLong(candidate -> candidate.serial));
        Random random = new Random(seed);
        if (candidates.isEmpty()) { return Collections.unmodifiableList(new ArrayList<>(source)); }
        String selected = candidates.get(random.nextInt(Math.min(3,candidates.size()))).chords.get(0);
        List<String> result = new ArrayList<>(source);
        result.set(index,(property.getMask() & ChordLock.QUALITY) != 0 ? selected : color(selected,config,seed ^ 0xd1b54a32d192ed03L));
        return Collections.unmodifiableList(result);
    }

    private static void validateEndpoint(String endpoint, String lock) {
        if (endpoint != null) { ChordSymbol.parse(endpoint); }
        if (endpoint != null && lock != null && !endpoint.equals(lock)) {
            throw new IllegalArgumentException("Endpoint conflicts with chord lock");
        }
    }

    private static int degree(ChordSymbol chord) { return MidiUtils.MAJ_SCALE.indexOf(chord.root); }
    public static List<String> vocabulary(GUIConfig config) {
        Integer[] scale = config.getScaleMode().noteAdjustScale; List<String> result = new ArrayList<>();
        for (int degree = 0; degree < 7; degree++) {
            int[] pitches = new int[3];
            for (int note = 0; note < 3; note++) {
                int index = degree + note * 2; pitches[note] = 60 + scale[index % 7] + 12 * (index / 7);
            }
            String name = ChordSymbol.identify(pitches[0],pitches);
            try { ChordSymbol.parse(name); } catch (IllegalArgumentException unusual) {
                // Exotic duplicated-degree scales use a defined, limited center/vamp vocabulary.
                name = MidiUtils.SEMITONE_LETTERS.get(Math.floorMod(scale[degree],12)) + (scale[2] == 3 ? "m" : "");
            }
            if (config.getScaleMode().ordinal() <= MidiUtils.ScaleMode.LOCRIAN.ordinal() || config.getScaleMode() == MidiUtils.ScaleMode.HARM_MINOR) {
                name = PitchSpelling.letter(degree,scale[degree]) + ChordSymbol.parse(name).quality;
            }
            if (degree == 4 && scale[2] == 3 && config.getProgressionStrategy() == Strategy.FUNCTIONAL && scale[4] == 7) {
                name = "G7";
            }
            if (degree == 1 && scale[2] == 3 && config.getProgressionStrategy() == Strategy.FUNCTIONAL && scale[1] == 2) {
                name = "Dm7b5";
            }
            result.add(name);
        }
        return Collections.unmodifiableList(result);
    }
    private static int indexInCycle(int degree) {
        for (int i = 0; i < FIFTHS.length; i++) { if (FIFTHS[i] == degree) { return i; } }
        return -1;
    }

    private static double transition(ChordSymbol previous, ChordSymbol next, boolean repeats) {
        if (previous.root == next.root) { return repeats ? -0.5 : -6; }
        double score = 0;
        if (Math.floorMod(next.root - previous.root, 12) == 5) { score += 3; }
        if (previous.root == 7 && previous.pitches(false)[1] % 12 == 11 && next.root == 0) { score += 5; }
        if (previous.root == 2 && next.root == 7) { score += 3; }
        for (int pitch : previous.pitches(false)) {
            if (Arrays.stream(next.pitches(false)).anyMatch(p -> p % 12 == pitch % 12)) { score += 0.5; }
        }
        return score;
    }
}
