package org.vibehistorian.vibecomposer.generation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Immutable, unvoiced harmony at exact beat boundaries. Inversion never changes the root. */
public final class HarmonyTimeline {
    public static final long TICKS_PER_BEAT = 960;
    public enum Function { TONIC, PREDOMINANT, DOMINANT, APPLIED_DOMINANT, MODAL_CENTER, UNKNOWN }
    public static final class Context {
        public final String key;
        public final int tonic;
        public final org.vibehistorian.vibecomposer.MidiUtils.ScaleMode mode;
        public final boolean functional;
        public Context(String key,int tonic,org.vibehistorian.vibecomposer.MidiUtils.ScaleMode mode,boolean functional) {
            if (key == null || mode == null || tonic < 0 || tonic > 11) { throw new IllegalArgumentException("Invalid harmonic context"); }
            this.key = key; this.tonic = tonic; this.mode = mode; this.functional = functional;
        }
    }
    private static final Context DEFAULT_CONTEXT = new Context("C",0,
            org.vibehistorian.vibecomposer.MidiUtils.ScaleMode.IONIAN,true);

    public static final class Event {
        public final int index;
        public final long start;
        public final long end;
        public final int root;
        public final String symbol;
        public final String id;
        public final Context context;
        public final Function function;
        public final String spelledRoot;
        private final Set<Integer> pitches;
        private final int[] voicing;
        private final int rootPitch;

        private Event(int index, long start, long end, int root, int[] chord) {
            this(index,start,end,root,chord,"harmony:" + index,DEFAULT_CONTEXT,null,-1);
        }

        private Event(int index,long start,long end,int root,int[] chord,String id,Context context,String symbol,int nextRoot) {
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
            this.symbol = symbol == null ? ChordSymbol.identify(root,chord) : symbol;
            this.id = id; this.context = context;
            String spelling;
            try { spelling = ChordSymbol.parse(this.symbol).rootName; }
            catch (IllegalArgumentException custom) { spelling = org.vibehistorian.vibecomposer.MidiUtils.SEMITONE_LETTERS.get(this.root); }
            this.spelledRoot = spelling;
            int degree = Math.floorMod(this.root - context.tonic,12);
            this.function = !context.functional ? degree == 0 ? Function.MODAL_CENTER : Function.UNKNOWN
                    : degree == 0 ? Function.TONIC : degree == 7 && majorThird() ? Function.DOMINANT
                    : dominantSeventh() && nextRoot >= 0 && Math.floorMod(nextRoot - this.root,12) == 5 ? Function.APPLIED_DOMINANT
                    : degree == 2 || degree == 5 ? Function.PREDOMINANT : Function.UNKNOWN;
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
    public final long revision;

    private HarmonyTimeline(List<Event> events, long length) {
        this.events = Collections.unmodifiableList(events);
        this.length = length;
        long hash = 0xcbf29ce484222325L;
        for (Event event : events) { hash = (hash ^ java.util.Objects.hash(event.id,event.symbol,event.start,event.end,
                event.context.key,event.context.mode,event.context.functional,java.util.Arrays.hashCode(event.voicing))) * 0x100000001b3L; }
        this.revision = hash;
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
    public HarmonyTimeline repeat(int count) {
        if (count < 1) { throw new IllegalArgumentException("Harmony repeat count must be positive"); }
        if (count == 1) { return this; }
        List<Event> repeated = new ArrayList<>();
        for (int repeat = 0; repeat < count; repeat++) {
            long offset = Math.multiplyExact(length,repeat);
            for (Event event : events) {
                repeated.add(new Event(repeated.size(),Math.addExact(event.start,offset),Math.addExact(event.end,offset),
                        event.rootPitch,event.voicing,event.id + "/repeat/" + repeat,event.context,event.symbol,-1));
            }
        }
        return new HarmonyTimeline(repeated,Math.multiplyExact(length,count)).rebuild(null,null,null);
    }

    public HarmonyTimeline withContext(Context context) { return rebuild(context,null,null); }
    public HarmonyTimeline withSymbols(List<String> symbols) {
        if (symbols.size() != events.size() || symbols.stream().anyMatch(text -> text == null || text.isEmpty())) {
            throw new IllegalArgumentException("Chord symbols must match harmonic events");
        }
        for (Event event : events) {
            String text = symbols.get(event.index);
            if (text.equals(event.symbol)) { continue; }
            ChordSymbol chord = ChordSymbol.parse(text); Set<Integer> classes = new TreeSet<>();
            for (int pitch : chord.pitches(false)) { classes.add(pitch % 12); }
            if (chord.root != event.root || !classes.equals(event.pitchClasses())) { throw new IllegalArgumentException("Spelling must preserve the sounding chord"); }
        }
        return rebuild(null,null,symbols);
    }

    public HarmonyTimeline withIdentities(List<String> ids) {
        if (ids.size() != events.size() || new java.util.HashSet<>(ids).size() != ids.size()
                || ids.stream().anyMatch(id -> id == null || id.isEmpty())) { throw new IllegalArgumentException("Harmony IDs must be unique and complete"); }
        return rebuild(null,ids,null);
    }

    public static HarmonyTimeline fromSymbols(List<String> symbols,List<Double> beats,Context context) {
        List<int[]> chords = new ArrayList<>(), roots = new ArrayList<>();
        for (String text : symbols) {
            ChordSymbol symbol = ChordSymbol.parse(text);
            chords.add(symbol.pitches(true)); roots.add(new int[] {60 + symbol.root});
        }
        return of(chords,roots,beats).rebuild(context,null,symbols);
    }

    private HarmonyTimeline rebuild(Context context,List<String> ids,List<String> symbols) {
        List<Event> result = new ArrayList<>();
        for (Event event : events) {
            int nextRoot = event.index + 1 < events.size() ? events.get(event.index + 1).root : -1;
            result.add(new Event(event.index,event.start,event.end,event.rootPitch,event.voicing,
                    ids == null ? event.id : ids.get(event.index),context == null ? event.context : context,
                    symbols == null ? event.symbol : symbols.get(event.index),nextRoot));
        }
        return new HarmonyTimeline(result,length);
    }

    /** Immutable edit: IDs survive; duration changes invalidate all shifted events as well as both joins. */
    public HarmonyTimeline edit(String id,String symbol,double duration) {
        ChordSymbol replacement = ChordSymbol.parse(symbol); long ticks = ticks(duration);
        if (ticks <= 0) { throw new IllegalArgumentException("Empty edited harmony duration"); }
        List<Event> result = new ArrayList<>(); long start = 0; boolean found = false;
        for (Event event : events) {
            boolean changed = event.id.equals(id); found |= changed;
            long end = Math.addExact(start,changed ? ticks : event.end - event.start);
            result.add(new Event(event.index,start,end,changed ? 60 + replacement.root : event.rootPitch,
                    changed ? replacement.pitches(true) : event.voicing,event.id,event.context,
                    changed ? symbol : event.symbol,-1));
            start = end;
        }
        if (!found) { throw new IllegalArgumentException("Unknown harmony event: " + id); }
        return new HarmonyTimeline(result,start).rebuild(null,null,null);
    }

    public java.util.Set<String> invalidatedEvents(HarmonyTimeline previous) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        java.util.Map<String,Event> old = new java.util.HashMap<>();
        for (Event event : previous.events) { old.put(event.id,event); }
        for (Event event : events) {
            Event before = old.remove(event.id);
            if (before == null || !before.symbol.equals(event.symbol) || before.start != event.start || before.end != event.end
                    || before.context.mode != event.context.mode || before.context.tonic != event.context.tonic
                    || before.function != event.function) {
                ids.add(event.id);
                if (event.index > 0) { ids.add(events.get(event.index - 1).id); }
                if (event.index + 1 < events.size()) { ids.add(events.get(event.index + 1).id); }
            }
        }
        ids.addAll(old.keySet()); return Collections.unmodifiableSet(ids);
    }

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
