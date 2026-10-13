package org.vibehistorian.vibecomposer.generation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Immutable, unvoiced harmony at exact beat boundaries. Inversion never changes the root. */
public final class HarmonyTimeline {
    public static final long TICKS_PER_BEAT = 960;

    public static final class Event {
        public final int index;
        public final long start;
        public final long end;
        public final int root;
        public final String symbol;
        private final Set<Integer> pitches;
        private final int[] voicing;
        private final int rootPitch;

        private Event(int index, long start, long end, int root, int[] chord) {
            if (root < 0 || root > 127) { throw new IllegalArgumentException("Invalid harmony root: " + root); }
            this.index = index;
            this.start = start;
            this.end = end;
            this.root = Math.floorMod(root, 12);
            this.rootPitch = root;
            this.voicing = java.util.Arrays.copyOf(chord, chord.length);
            Set<Integer> classes = new TreeSet<>();
            for (int pitch : chord) {
                if (pitch < 0 || pitch > 127) {
                    throw new IllegalArgumentException("Invalid harmony pitch: " + pitch);
                }
                classes.add(pitch % 12);
            }
            this.pitches = Collections.unmodifiableSet(classes);
            this.symbol = ChordSymbol.identify(root,chord);
        }

        public Set<Integer> pitchClasses() { return pitches; }
        public int[] pitches() { return java.util.Arrays.copyOf(voicing, voicing.length); }
        public int[] rootPitch() { return new int[] { rootPitch }; }
        public boolean contains(int pitch) { return pitches.contains(Math.floorMod(pitch, 12)); }
        public boolean majorThird() { return contains(root + 4); }
        public boolean dominantSeventh() { return majorThird() && contains(root + 10); }
    }

    private final List<Event> events;
    private final long length;

    private HarmonyTimeline(List<Event> events, long length) {
        this.events = Collections.unmodifiableList(events);
        this.length = length;
    }

    public static long ticks(double beats) {
        if (!Double.isFinite(beats) || beats < 0 || beats > Long.MAX_VALUE / (double) TICKS_PER_BEAT) {
            throw new IllegalArgumentException("Invalid beat value: " + beats);
        }
        long ticks = Math.round(beats * TICKS_PER_BEAT);
        if (Math.abs(beats - ticks / (double) TICKS_PER_BEAT) > 0.00001) {
            throw new IllegalArgumentException("Beat value cannot be represented at 960 PPQ: " + beats);
        }
        return ticks;
    }

    public static HarmonyTimeline of(List<int[]> chords, List<int[]> roots, List<Double> durations) {
        if (chords.isEmpty() || chords.size() != roots.size() || chords.size() != durations.size()) {
            throw new IllegalArgumentException("Harmony chords, roots, and durations must have matching nonzero lengths");
        }
        List<Event> events = new ArrayList<>();
        long start = 0;
        for (int i = 0; i < chords.size(); i++) {
            if (chords.get(i).length == 0 || roots.get(i).length == 0) {
                throw new IllegalArgumentException("Empty chord/root at event " + i);
            }
            long duration = ticks(durations.get(i));
            if (duration <= 0) { throw new IllegalArgumentException("Empty harmony duration at event " + i); }
            long end = Math.addExact(start, duration);
            events.add(new Event(i, start, end, roots.get(i)[0], chords.get(i)));
            start = end;
        }
        return new HarmonyTimeline(events, start);
    }

    public List<Event> events() { return events; }
    public long length() { return length; }

    /** Half-open intervals: an onset on a boundary belongs to the following event. */
    public Event at(long tick) {
        if (tick < 0 || tick >= length) { throw new IllegalArgumentException("Harmony time out of range: " + tick); }
        int low = 0, high = events.size() - 1;
        while (low < high) {
            int middle = (low + high) / 2;
            if (events.get(middle).end <= tick) { low = middle + 1; } else { high = middle; }
        }
        return events.get(low);
    }

    public List<Event> overlapping(long start, long end) {
        if (start < 0 || end <= start || end > length) { throw new IllegalArgumentException("Invalid harmony overlap"); }
        List<Event> result = new ArrayList<>();
        int first = at(start).index;
        for (int i = first; i < events.size() && events.get(i).start < end; i++) { result.add(events.get(i)); }
        return Collections.unmodifiableList(result);
    }
}
