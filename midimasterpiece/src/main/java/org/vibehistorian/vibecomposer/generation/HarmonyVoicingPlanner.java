package org.vibehistorian.vibecomposer.generation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Register and voice continuity change voicings, never harmonic roots or pitch-class definitions. */
public final class HarmonyVoicingPlanner {
    private HarmonyVoicingPlanner() { }
    private static final class State {
        final List<int[]> voices; final double cost; final long order;
        State(List<int[]> voices,double cost,long order) { this.voices = voices; this.cost = cost; this.order = order; }
    }
    public static List<int[]> voice(HarmonyTimeline harmony,int minimum,int maximum,Map<String,Integer> bassLocks,
            Map<String,Integer> topLocks) {
        if (minimum < 0 || maximum > 127 || minimum > maximum) { throw new IllegalArgumentException("Invalid voicing register"); }
        List<State> beam = Collections.singletonList(new State(Collections.emptyList(),0,0)); long order = 1;
        for (HarmonyTimeline.Event event : harmony.events()) {
            List<int[]> candidates = new ArrayList<>();
            for (int bass = minimum; bass <= maximum; bass++) {
                if (!event.contains(bass) || bassLocks.containsKey(event.id) && bass % 12 != bassLocks.get(event.id)) { continue; }
                int[] notes = new int[event.pitchClasses().size()]; notes[0] = bass;
                Set<Integer> used = new TreeSet<>(); used.add(bass % 12); int count = 1;
                for (int pitch = bass + 1; pitch <= maximum && count < notes.length; pitch++) {
                    if (event.contains(pitch) && used.add(pitch % 12)) { notes[count++] = pitch; }
                }
                if (count != notes.length || topLocks.containsKey(event.id) && notes[count - 1] % 12 != topLocks.get(event.id)) { continue; }
                candidates.add(notes);
            }
            if (candidates.isEmpty()) { throw new IllegalArgumentException("No voicing fits register/bass/top locks for " + event.id); }
            List<State> next = new ArrayList<>();
            for (State state : beam) {
                for (int[] candidate : candidates) {
                    double cost = state.cost + Math.abs(Arrays.stream(candidate).average().orElse(60) - (minimum + maximum) / 2.0) * 0.2;
                    if (!state.voices.isEmpty()) {
                        int[] previous = state.voices.get(state.voices.size() - 1);
                        cost += movement(previous,candidate);
                        HarmonyTimeline.Event previousChord = harmony.events().get(event.index - 1);
                        if (previousChord.majorThird() && Math.floorMod(event.root - previousChord.root,12) == 5) {
                            for (int i = 0; i < Math.min(previous.length,candidate.length); i++) {
                                int interval = Math.floorMod(previous[i] - previousChord.root,12), motion = candidate[i] - previous[i];
                                if (interval == 4) { cost += motion == 1 ? -2 : 2; }
                                if (interval == 10) { cost += motion == -1 || motion == -2 ? -2 : 2; }
                            }
                        }
                    }
                    List<int[]> path = new ArrayList<>(state.voices); path.add(candidate);
                    next.add(new State(path,cost,order++));
                }
            }
            next.sort(Comparator.<State>comparingDouble(state -> state.cost).thenComparingLong(state -> state.order));
            beam = new ArrayList<>(next.subList(0,Math.min(32,next.size())));
        }
        List<int[]> result = new ArrayList<>();
        for (int[] chord : beam.get(0).voices) { result.add(Arrays.copyOf(chord,chord.length)); }
        return Collections.unmodifiableList(result);
    }
    public static double movement(int[] previous,int[] next) {
        double cost = 0;
        for (int i = 0; i < next.length; i++) {
            int position = next.length == 1 ? 0 : (int) Math.round(i * (previous.length - 1.0) / (next.length - 1));
            int distance = Math.abs(next[i] - previous[position]); cost += distance + distance * distance / 24.0;
        }
        return cost / Math.max(previous.length,next.length);
    }

    /** Omission/voice reduction retains thirds, sevenths, and quality-defining altered fifths. */
    public static int[] retainGuideTones(int[] proposed,int[] original,int root) {
        Set<Integer> required = new TreeSet<>();
        for (int pitch : original) {
            int interval = Math.floorMod(pitch - root,12);
            if (interval == 3 || interval == 4 || interval == 6 || interval == 8 || interval == 10 || interval == 11) { required.add(pitch % 12); }
        }
        int[] result = Arrays.copyOf(proposed,Math.max(proposed.length,required.size()));
        for (int i = proposed.length; i < result.length; i++) { result[i] = original[Math.min(i,original.length - 1)]; }
        Set<Integer> present = new TreeSet<>(); for (int pitch : result) { present.add(pitch % 12); }
        for (int pitchClass : required) {
            if (present.contains(pitchClass)) { continue; }
            for (int i = result.length - 1; i >= 0; i--) {
                int currentClass = result[i] % 12;
                final int index = i;
                boolean duplicate = java.util.stream.IntStream.range(0,result.length).anyMatch(j -> j != index && result[j] % 12 == currentClass);
                if (!required.contains(currentClass) || duplicate) {
                    int replacement = original[0];
                    for (int pitch : original) { if (pitch % 12 == pitchClass) { replacement = pitch; break; } }
                    result[i] = replacement; present = new TreeSet<>(); for (int pitch : result) { present.add(pitch % 12); }
                    break;
                }
            }
        }
        Arrays.sort(result); return result;
    }
}
