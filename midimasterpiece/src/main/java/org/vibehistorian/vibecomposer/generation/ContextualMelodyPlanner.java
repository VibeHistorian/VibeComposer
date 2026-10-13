package org.vibehistorian.vibecomposer.generation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/** Bounded phrase search over realized pitches. No post-search pitch-class quota mutations. */
public final class ContextualMelodyPlanner {
    public enum Ending { OPEN, CLOSED, LOOP }
    public enum Role { CHORD, PASSING, NEIGHBOR, REST }

    public static final class Slot {
        public final long start, duration;
        public final boolean rest;
        public final Integer lockedPitch;
        public final int motif;

        public Slot(long start, long duration, boolean rest, Integer lockedPitch, int motif) {
            if (start < 0 || duration <= 0 || rest && lockedPitch != null) {
                throw new IllegalArgumentException("Invalid rhythm slot");
            }
            this.start = start;
            this.duration = duration;
            this.rest = rest;
            this.lockedPitch = lockedPitch;
            this.motif = motif;
        }
    }

    public static final class Options {
        public final int minimum, maximum, leap, beamWidth, tension;
        public final Ending ending;

        public Options(int minimum, int maximum, int leap, int beamWidth, int tension, Ending ending) {
            if (minimum < 0 || maximum > 127 || minimum > maximum || leap < 0 || leap > 24
                    || beamWidth < 1 || beamWidth > 128 || tension < 0 || tension > 100 || ending == null) {
                throw new IllegalArgumentException("Invalid contextual melody options");
            }
            this.minimum = minimum;
            this.maximum = maximum;
            this.leap = leap;
            this.beamWidth = beamWidth;
            this.tension = tension;
            this.ending = ending;
        }
    }

    public static final class PlannedNote {
        public final Slot slot;
        public final int pitch;
        public final Role role;
        private PlannedNote(Slot slot, int pitch, Role role) {
            this.slot = slot;
            this.pitch = pitch;
            this.role = role;
        }
    }

    public static final class Result {
        public final List<PlannedNote> notes;
        public final double score;
        public final int candidatesExamined;
        private Result(List<PlannedNote> notes, double score, int candidatesExamined) {
            this.notes = Collections.unmodifiableList(notes);
            this.score = score;
            this.candidatesExamined = candidatesExamined;
        }
    }

    private static final class State {
        final State parent;
        final PlannedNote note;
        final int last, previous, first;
        final double score;
        final long serial;
        State(State parent, PlannedNote note, int last, int previous, int first, double score, long serial) {
            this.parent = parent; this.note = note; this.last = last; this.previous = previous;
            this.first = first; this.score = score; this.serial = serial;
        }
    }

    private ContextualMelodyPlanner() { }

    /** Repeated rhythm identities use independent streams; final spans receive a longer arrival. */
    public static List<Slot> rhythm(HarmonyTimeline harmony, int speed, int pauseChance,
            List<Integer> motifs, long seed, Ending ending) {
        if (motifs.isEmpty()) { throw new IllegalArgumentException("A rhythm motif is required"); }
        List<Slot> result = new ArrayList<>();
        for (HarmonyTimeline.Event event : harmony.events()) {
            int motif = motifs.get(event.index % motifs.size());
            Random random = new Random(seed ^ (0x9e3779b97f4a7c15L * motif));
            long unit = speed >= 75 ? 240 : speed >= 35 ? 480 : 960;
            long cursor = event.start;
            int note = 0;
            while (cursor < event.end) {
                long duration = Math.min(event.end - cursor, unit * (random.nextInt(4) == 0 ? 2 : 1));
                boolean closing = ending == Ending.CLOSED && event.index == harmony.events().size() - 1;
                if (closing && event.end - cursor <= 2 * unit) { duration = event.end - cursor; }
                boolean rest = note > 0 && !(closing && cursor + duration == event.end)
                        && random.nextInt(100) < pauseChance;
                result.add(new Slot(cursor, duration, rest, null, Math.abs(motif)));
                cursor += duration;
                note++;
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static Result generate(HarmonyTimeline harmony, int[] scale, List<Slot> slots,
            Options options, long seed) {
        validate(harmony, slots, options);
        Set<Integer> scaleClasses = new TreeSet<>();
        for (int pitch : scale) { scaleClasses.add(Math.floorMod(pitch, 12)); }
        List<State> beam = Collections.singletonList(new State(null, null, -1, -1, -1, 0, 0));
        long serial = 0;
        int examined = 0;
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            HarmonyTimeline.Event chord = harmony.at(slot.start);
            boolean strong = slot.start % HarmonyTimeline.TICKS_PER_BEAT == 0
                    || slot.duration >= HarmonyTimeline.TICKS_PER_BEAT;
            boolean lastSound = !slot.rest && slots.subList(index + 1, slots.size()).stream().allMatch(next -> next.rest);
            List<State> next = new ArrayList<>();
            Random random = new Random(seed ^ (slot.start * 0x9e3779b97f4a7c15L));
            double[] preference = new double[128];
            for (int i = 0; i < preference.length; i++) { preference[i] = random.nextDouble() * 0.25; }
            for (State state : beam) {
                if (slot.rest) {
                    // A rest cannot erase an unfinished ornament obligation.
                    if (state.note == null || state.note.role == Role.CHORD || state.note.role == Role.REST) {
                        next.add(new State(state, new PlannedNote(slot, -1, Role.REST), state.last,
                                state.previous, state.first, state.score, serial++));
                    }
                    continue;
                }
                for (int pitch = options.minimum; pitch <= options.maximum; pitch++) {
                    if (slot.lockedPitch != null && pitch != slot.lockedPitch) { continue; }
                    examined++;
                    boolean stable = true;
                    for (HarmonyTimeline.Event overlap : harmony.overlapping(slot.start, slot.start + slot.duration)) {
                        stable &= overlap.contains(pitch);
                    }
                    Role role = Role.CHORD;
                    if (!stable) {
                        if (strong || options.tension == 0 || lastSound || state.last < 0
                                || !scaleClasses.contains(pitch % 12) || Math.abs(pitch - state.last) > 2
                                || state.note == null || state.note.role != Role.CHORD
                                || slot.start + slot.duration >= chord.end) { continue; }
                        role = Role.PASSING;
                    }
                    if (state.last >= 0 && Math.abs(pitch - state.last) > options.leap) { continue; }
                    if (state.note != null && state.note.role == Role.PASSING) {
                        if (!stable || Math.abs(pitch - state.last) > 2 || pitch == state.last) { continue; }
                    }
                    if (lastSound && options.ending == Ending.CLOSED && chord.contains(0) && pitch % 12 != 0) { continue; }
                    double score = state.score + localScore(state, pitch, slot, chord, harmony, options, stable);
                    score += preference[pitch];
                    if (lastSound && options.ending == Ending.LOOP && state.first >= 0) {
                        int seam = Math.abs(pitch - state.first);
                        if (seam > options.leap) { continue; }
                        score -= seam * 0.75;
                    }
                    next.add(new State(state, new PlannedNote(slot, pitch, role), pitch, state.last,
                            state.first < 0 ? pitch : state.first, score, serial++));
                }
            }
            next.sort(Comparator.<State>comparingDouble(state -> state.score).reversed().thenComparingLong(state -> state.serial));
            if (next.isEmpty()) {
                throw new IllegalArgumentException("No candidate survived bounded melody search at tick " + slot.start
                        + "; check range, leap allowance, rhythm, and pitch locks");
            }
            beam = new ArrayList<>(next.subList(0, Math.min(options.beamWidth, next.size())));
        }
        State best = beam.get(0);
        List<PlannedNote> result = new ArrayList<>();
        for (State state = best; state.note != null; state = state.parent) { result.add(state.note); }
        Collections.reverse(result);
        for (int i = 1; i + 1 < result.size(); i++) {
            PlannedNote note = result.get(i);
            if (note.role == Role.PASSING && result.get(i - 1).pitch == result.get(i + 1).pitch) {
                result.set(i, new PlannedNote(note.slot, note.pitch, Role.NEIGHBOR));
            }
        }
        return new Result(result, best.score, examined);
    }

    private static double localScore(State state, int pitch, Slot slot, HarmonyTimeline.Event chord,
            HarmonyTimeline harmony, Options options, boolean stable) {
        double exposure = slot.duration / (double) HarmonyTimeline.TICKS_PER_BEAT;
        boolean accented = slot.start % HarmonyTimeline.TICKS_PER_BEAT == 0
                || slot.duration >= HarmonyTimeline.TICKS_PER_BEAT;
        double score = stable ? exposure * (accented ? 2 : 0.5)
                : -exposure * (1 - options.tension / 100.0);
        double position = slot.start / (double) harmony.length();
        double center = (options.minimum + options.maximum) / 2.0;
        double arc = Math.sin(position * Math.PI) * Math.min(5, (options.maximum - options.minimum) / 4.0);
        score -= Math.abs(pitch - center - arc) * 0.18;
        if (state.last >= 0) {
            int movement = pitch - state.last;
            score -= Math.abs(movement) * 0.30;
            if (movement == 0) { score -= 0.9; }
            else if (Math.abs(movement) <= 2) { score += 0.5; }
            if (state.previous >= 0 && Math.abs(state.last - state.previous) > 4) {
                score += Integer.signum(movement) == -Integer.signum(state.last - state.previous)
                        && Math.abs(movement) <= 2 ? 1.5 : -1.5;
            }
            if (state.note != null && state.note.slot.start < chord.start) {
                HarmonyTimeline.Event previousChord = harmony.at(state.note.slot.start);
                if (previousChord.majorThird() && chord.root == Math.floorMod(previousChord.root + 5, 12)) {
                    if (state.last % 12 == (previousChord.root + 4) % 12) { score += movement == 1 ? 5 : -3; }
                    if (previousChord.dominantSeventh() && state.last % 12 == (previousChord.root + 10) % 12) {
                        score += movement < 0 && movement >= -2 ? 4 : -3;
                    }
                }
            }
            // Repeated rhythm motifs also retain their interval gesture when harmony permits it.
            State earlier = state;
            while (earlier != null && earlier.note != null) {
                if (earlier.note.slot.motif == slot.motif && earlier.note.slot.start % (4 * HarmonyTimeline.TICKS_PER_BEAT)
                        == slot.start % (4 * HarmonyTimeline.TICKS_PER_BEAT) && earlier.previous >= 0) {
                    score += Math.signum(movement) == Math.signum(earlier.last - earlier.previous) ? 0.8 : -0.4;
                    break;
                }
                earlier = earlier.parent;
            }
        }
        return score;
    }

    private static void validate(HarmonyTimeline harmony, List<Slot> slots, Options options) {
        if (slots.isEmpty()) { throw new IllegalArgumentException("Empty melody rhythm"); }
        long expected = 0;
        for (Slot slot : slots) {
            if (slot.start != expected || slot.duration > harmony.length() - slot.start) {
                throw new IllegalArgumentException("Rhythm must cover harmony exactly without gaps/overlap");
            }
            if (slot.lockedPitch != null && (slot.lockedPitch < options.minimum || slot.lockedPitch > options.maximum)) {
                throw new IllegalArgumentException("Locked pitch outside requested range at tick " + slot.start);
            }
            expected = Math.addExact(slot.start, slot.duration);
        }
        if (expected != harmony.length()) { throw new IllegalArgumentException("Rhythm length differs from harmony"); }
    }
}
