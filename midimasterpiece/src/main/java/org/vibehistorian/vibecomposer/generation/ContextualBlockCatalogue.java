package org.vibehistorian.vibecomposer.generation;

import org.vibehistorian.vibecomposer.Enums.BlockType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Catalogue shapes are proposals; realized harmony and role checks decide applicability. */
public final class ContextualBlockCatalogue {
    public enum Transform { DIATONIC, INVERT, INTERVAL_SEQUENCE, FRAGMENT }
    private ContextualBlockCatalogue() { }
    public static final class Evaluator {
        private final java.util.Map<String,List<int[]>> pitches = new java.util.HashMap<>();
        private final java.util.Map<String,List<Integer>> typeIds = new java.util.HashMap<>();
        public double affinity(int[] gesture,HarmonyTimeline.Event chord,int[] scale,List<Integer> weights,int motif) {
            String key = chord.id + "/" + chord.symbol + "/" + gesture[0] + "/" + (motif < 0);
            if (!pitches.containsKey(key)) {
                List<int[]> candidates = new ArrayList<>(); List<Integer> types = new ArrayList<>();
                for (BlockType type : BlockType.values()) {
                    for (Integer[] shape : type.blocks) {
                        int[] notes = realize(type,shape,gesture[0],chord,scale,motif < 0 ? Transform.INVERT : Transform.DIATONIC);
                        if (notes.length >= gesture.length) { candidates.add(notes); types.add(type.ordinal()); }
                    }
                }
                pitches.put(key,candidates); typeIds.put(key,types);
            }
            double best = 0; List<int[]> candidates = pitches.get(key); List<Integer> types = typeIds.get(key);
            for (int n = 0; n < candidates.size(); n++) {
                int type = types.get(n), weight = weights.isEmpty() ? 100 : type < weights.size() ? weights.get(type) : 0;
                if (weight <= 0 || candidates.get(n).length < gesture.length) { continue; }
                double distance = 0;
                for (int i = 1; i < gesture.length; i++) { distance += Math.abs(gesture[i] - candidates.get(n)[i]); }
                best = Math.max(best,(1.5 - distance * 0.35) * Math.min(2,weight / 100.0));
            }
            return best;
        }
    }

    public static int[] realize(BlockType type, Integer[] shape, int start, HarmonyTimeline.Event chord,
            int[] scale, Transform transform) {
        List<Integer> pitches = new ArrayList<>();
        boolean chordMembers = type == BlockType.CHORDY || type == BlockType.ARPY;
        for (int pitch = 0; pitch <= 127; pitch++) {
            if (chordMembers ? chord.contains(pitch) : contains(scale,pitch % 12)) { pitches.add(pitch); }
        }
        int position = pitches.indexOf(start);
        if (position < 0) { return new int[0]; }
        int[] result = new int[transform == Transform.FRAGMENT ? Math.min(3,shape.length) : shape.length];
        int direction = transform == Transform.INVERT ? -1 : 1;
        for (int i = 0; i < result.length; i++) {
            int offset = chordMembers ? (shape[i] == 7 ? chord.pitchClasses().size() : shape[i] / 2) : shape[i];
            int index = position + direction * offset;
            if (index < 0 || index >= pitches.size()) { return new int[0]; }
            result[i] = pitches.get(index);
            if (transform == Transform.INTERVAL_SEQUENCE && !chordMembers) {
                int[] major = {0,2,4,5,7,9,11};
                int steps = direction * shape[i];
                result[i] = start + 12 * Math.floorDiv(steps,7) + major[Math.floorMod(steps,7)];
            }
        }
        return result;
    }

    public static double affinity(int[] gesture, HarmonyTimeline.Event chord, int[] scale,
            List<Integer> weights, int motif) {
        if (gesture.length < 2) { return 0; }
        double best = 0;
        for (BlockType type : BlockType.values()) {
            int weight = weights.isEmpty() ? 100 : type.ordinal() < weights.size() ? weights.get(type.ordinal()) : 0;
            if (weight <= 0) { continue; }
            for (Integer[] shape : type.blocks) {
                if (shape.length < gesture.length) { continue; }
                int[] candidate = realize(type,shape,gesture[0],chord,scale,
                        motif < 0 ? Transform.INVERT : Transform.DIATONIC);
                if (candidate.length < gesture.length) { continue; }
                double distance = 0;
                for (int i = 1; i < gesture.length; i++) { distance += Math.abs(gesture[i] - candidate[i]); }
                best = Math.max(best,(1.5 - distance * 0.35) * Math.min(2,weight / 100.0));
            }
        }
        return best;
    }

    private static boolean contains(int[] values,int value) {
        for (int item : values) { if (Math.floorMod(item,12) == value) { return true; } }
        return false;
    }
}
