package org.vibehistorian.vibecomposer.generation;

import org.vibehistorian.vibecomposer.MidiUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Contextual edits declare their destination and publish one complete immutable revision. */
public final class HarmonyOperations {
    public enum Operation { CONTEXTUAL, APPLIED_DOMINANT, MIXTURE, TRITONE_SUBSTITUTION, SUSPENSION }
    public static final class Alternative {
        public final String symbol, explanation;
        public final HarmonyTimeline harmony;
        public final double score;
        private Alternative(String symbol,String explanation,HarmonyTimeline harmony,double score) {
            this.symbol = symbol; this.explanation = explanation; this.harmony = harmony; this.score = score;
        }
        @Override public String toString() { return symbol + ": " + explanation; }
    }
    private HarmonyOperations() { }

    public static List<Alternative> alternatives(HarmonyTimeline source,int index,Operation operation,
            boolean jazz,Set<String> locked,Map<String,List<Integer>> importantMelody) {
        if (index < 0 || index >= source.events().size()) { throw new IllegalArgumentException("Chord edit outside progression"); }
        if (operation == Operation.CONTEXTUAL) {
            List<Alternative> choices = new ArrayList<>();
            for (Operation candidate : Operation.values()) {
                if (candidate != Operation.CONTEXTUAL) { choices.addAll(alternatives(source,index,candidate,jazz,locked,importantMelody)); }
            }
            choices.sort(Comparator.<Alternative>comparingDouble(candidate -> candidate.score).reversed().thenComparing(candidate -> candidate.symbol));
            return Collections.unmodifiableList(choices);
        }
        HarmonyTimeline.Event event = source.events().get(index);
        if (locked.contains(event.id)) { return Collections.emptyList(); }
        HarmonyTimeline.Event next = index + 1 < source.events().size() ? source.events().get(index + 1) : null;
        List<String> candidates = new ArrayList<>(); String explanation;
        if (operation == Operation.APPLIED_DOMINANT) {
            if (next == null) { return Collections.emptyList(); }
            int destinationLetter = MidiUtils.CHORD_FIRST_LETTERS.indexOf(next.spelledRoot.substring(0,1));
            candidates.add(PitchSpelling.letter(destinationLetter + 4,(next.root + 7) % 12) + "7");
            explanation = "Prepares " + next.symbol;
        } else if (operation == Operation.MIXTURE) {
            if (Math.floorMod(event.root - event.context.tonic,12) != 5 || next == null || next.root != event.context.tonic) {
                return Collections.emptyList();
            }
            candidates.add(event.spelledRoot + "m");
            explanation = "Borrowed minor subdominant resolves to " + next.symbol;
        } else if (operation == Operation.TRITONE_SUBSTITUTION) {
            if (!jazz || next == null || !event.dominantSeventh() || Math.floorMod(next.root - event.root,12) != 5) {
                return Collections.emptyList();
            }
            int destinationLetter = MidiUtils.CHORD_FIRST_LETTERS.indexOf(next.spelledRoot.substring(0,1));
            candidates.add(PitchSpelling.letter(destinationLetter + 1,(event.root + 6) % 12) + "7");
            explanation = "Keeps the dominant destination " + next.symbol;
        } else {
            if (next == null || next.root != event.root || !next.majorThird()) { return Collections.emptyList(); }
            candidates.add(event.spelledRoot + "sus7");
            explanation = "Suspended third resolves in " + next.symbol;
        }
        List<Alternative> result = new ArrayList<>();
        for (String symbol : candidates) {
            ChordSymbol chord = ChordSymbol.parse(symbol);
            List<Integer> melody = importantMelody.getOrDefault(event.id,Collections.emptyList());
            boolean compatible = true;
            for (int pitch : melody) {
                boolean found = false; for (int note : chord.pitches(false)) { found |= note % 12 == Math.floorMod(pitch,12); }
                compatible &= found;
            }
            if (!compatible || symbol.equals(event.symbol)) { continue; }
            HarmonyTimeline changed = source.edit(event.id,symbol,(event.end - event.start) / 960.0);
            double score = next == null ? 0 : -Math.min(Math.floorMod(next.root - chord.root,12),Math.floorMod(chord.root - next.root,12));
            result.add(new Alternative(symbol,explanation,changed,score));
        }
        result.sort(Comparator.<Alternative>comparingDouble(candidate -> candidate.score).reversed().thenComparing(candidate -> candidate.symbol));
        return Collections.unmodifiableList(result);
    }
}
