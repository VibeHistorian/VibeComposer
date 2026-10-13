package org.vibehistorian.vibecomposer.generation;

import org.vibehistorian.vibecomposer.GUIConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Time/accent weighted harmonization of a fixed melody; it never edits its input notes. */
public final class HarmonyHarmonizer {
    public static final class MelodyNote {
        public final long start, duration;
        public final int pitch;
        public final double accent;
        public MelodyNote(long start,long duration,int pitch,double accent) {
            if (start < 0 || duration <= 0 || pitch < -1 || pitch > 127 || !Double.isFinite(accent) || accent < 0) {
                throw new IllegalArgumentException("Invalid fixed melody event");
            }
            this.start = start; this.duration = duration; this.pitch = pitch; this.accent = accent;
        }
    }
    private static final class State {
        final List<String> names; final double score;
        State(List<String> names,double score) { this.names = names; this.score = score; }
    }
    private HarmonyHarmonizer() { }
    public static HarmonyTimeline harmonize(GUIConfig config,List<MelodyNote> melody,List<Double> durations,
            Map<Integer,String> locks) {
        if (durations.isEmpty() || melody.isEmpty()) { throw new IllegalArgumentException("Harmonization needs melody and harmonic rhythm"); }
        List<String> vocabulary = new ArrayList<>(HarmonicProgressionPlanner.vocabulary(config));
        if (!config.isSpiceForceScale()) { vocabulary.addAll(Arrays.asList("E7","A7","D7","G7")); }
        List<State> beam = Collections.singletonList(new State(Collections.emptyList(),0)); long start = 0;
        for (int index = 0; index < durations.size(); index++) {
            long duration = HarmonyTimeline.ticks(durations.get(index)), end = start + duration;
            if (duration <= 0) { throw new IllegalArgumentException("Empty harmonization duration"); }
            List<State> next = new ArrayList<>();
            for (State state : beam) {
                for (String symbol : locks.containsKey(index) ? Collections.singletonList(locks.get(index)) : vocabulary) {
                    ChordSymbol chord = ChordSymbol.parse(symbol); double score = state.score;
                    for (MelodyNote note : melody) {
                        long overlap = Math.min(end,note.start + note.duration) - Math.max(start,note.start);
                        if (overlap <= 0 || note.pitch < 0) { continue; }
                        boolean fit = Arrays.stream(chord.pitches(false)).anyMatch(pitch -> pitch % 12 == note.pitch % 12);
                        double exposure = overlap / 960.0 * (1 + note.accent);
                        score += fit ? exposure * 2 : -exposure * 2;
                    }
                    if (!state.names.isEmpty()) {
                        ChordSymbol previous = ChordSymbol.parse(state.names.get(state.names.size() - 1));
                        if (Math.floorMod(chord.root - previous.root,12) == 5) { score += 1.5; }
                        if (previous.quality.contains("7") && Math.floorMod(chord.root - previous.root,12) != 5) { score -= 1; }
                        if (previous.root == chord.root) { score -= 0.4; }
                    }
                    List<String> names = new ArrayList<>(state.names); names.add(symbol); next.add(new State(names,score));
                }
            }
            next.sort(Comparator.<State>comparingDouble(state -> state.score).reversed().thenComparing(state -> String.join(",",state.names)));
            beam = new ArrayList<>(next.subList(0,Math.min(32,next.size()))); start = end;
        }
        final long length = start;
        if (melody.stream().anyMatch(note -> note.start + note.duration > length)) { throw new IllegalArgumentException("Fixed melody exceeds harmonic rhythm"); }
        return HarmonyTimeline.fromSymbols(beam.get(0).names,durations,new HarmonyTimeline.Context("C",0,config.getScaleMode(),
                config.getProgressionStrategy() != HarmonicProgressionPlanner.Strategy.MODAL));
    }
}
