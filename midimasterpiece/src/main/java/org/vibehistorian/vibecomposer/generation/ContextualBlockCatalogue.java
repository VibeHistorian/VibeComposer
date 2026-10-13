package org.vibehistorian.vibecomposer.generation;

import org.vibehistorian.vibecomposer.Enums.BlockType;
import java.util.*;

/** Balanced, seeded gestures guide the search; harmony and role checks remain authoritative. */
public final class ContextualBlockCatalogue {
    public enum Transform { DIATONIC, INVERT, INTERVAL_SEQUENCE, FRAGMENT }
    private static final Map<BlockType,List<int[]>> SHAPES = new EnumMap<>(BlockType.class);
    static {
        for (BlockType type : BlockType.values()) {
            List<int[]> shapes = new ArrayList<>();
            for (Integer[] original : type.blocks) {
                int[] steps = new int[Math.max(3,original.length)];
                for (int i = 0; i < steps.length; i++) {
                    int step = original[Math.min(i,original.length - 1)];
                    steps[i] = chordMembers(type) ? step == 7 ? 3 : (int) Math.round(step / 2.0) : step;
                }
                addBalanced(shapes,steps);
            }
            switch (type) {
                case SCALEY: addBalanced(shapes,new int[] {0,1,2,3,2}); addBalanced(shapes,new int[] {0,1,0,-1,-2}); break;
                case NEIGHBORY: addBalanced(shapes,new int[] {0,1,0,-1,0}); addBalanced(shapes,new int[] {0,0,-1,0}); break;
                case ARPY: addBalanced(shapes,new int[] {0,1,0,-1}); addBalanced(shapes,new int[] {0,-1,1,0}); break;
                case CHORDY: addBalanced(shapes,new int[] {0,1,2,1}); addBalanced(shapes,new int[] {0,2,1,-1}); break;
                case WAVY: addBalanced(shapes,new int[] {0,-1,1,-2,0}); addBalanced(shapes,new int[] {0,1,-1,0,2}); break;
                case INTERVAL: addBalanced(shapes,new int[] {0,2,1}); addBalanced(shapes,new int[] {0,3,1}); break;
                case NOTE: addBalanced(shapes,new int[] {0,0,0,0}); break;
            }
            SHAPES.put(type,Collections.unmodifiableList(shapes));
        }
    }
    private ContextualBlockCatalogue() { }
    private static boolean chordMembers(BlockType type) { return type == BlockType.CHORDY || type == BlockType.ARPY; }
    private static void addBalanced(List<int[]> list,int[] shape) {
        for (int sign : new int[] {1,-1}) {
            int[] value = Arrays.stream(shape).map(step -> step * sign).toArray();
            if (list.stream().noneMatch(existing -> Arrays.equals(existing,value))) { list.add(value); }
        }
    }
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
    private static int orientation(int[] shape) {
        for (int step : shape) { if (step != 0) { return Integer.signum(step); } }
        return 0;
    }
    private static final class Guide {
        final BlockType type; final int[] steps;
        Guide(BlockType type,int[] steps) { this.type = type; this.steps = steps; }
    }
    public static final class Evaluator {
        private final long seed;
        private final int newChance;
        private final Map<String,Guide> guides = new HashMap<>();
        private final Map<String,List<Integer>> spaces = new HashMap<>();
        public Evaluator() { this(0,0); }
        public Evaluator(long seed,int newChance) {
            if (newChance < 0 || newChance > 100) { throw new IllegalArgumentException("New blocks chance must be 0..100"); }
            this.seed = seed; this.newChance = newChance;
        }
        public double affinity(int[] gesture,HarmonyTimeline.Event chord,int[] scale,List<Integer> weights,int motif) {
            return affinity(gesture,chord,scale,weights,motif,0,motif < 0 ? -1 : 1);
        }
        public double affinity(int[] gesture,HarmonyTimeline.Event chord,int[] scale,List<Integer> weights,int motif,int phase,int direction) {
            if (gesture.length < 2) { return 0; }
            int segment = phase / 4;
            String key = motif + "/" + segment + "/" + direction + "/" + weights;
            Guide guide;
            if (guides.containsKey(key)) { guide = guides.get(key); }
            else {
                guide = choose(weights,motif,segment,direction);
                guides.put(key,guide);
            }
            if (guide == null) { return 0; }
            String spaceKey = chordMembers(guide.type) ? chord.id + "/" + chord.symbol : Arrays.toString(scale);
            List<Integer> pitches = spaces.get(spaceKey);
            if (pitches == null) {
                pitches = new ArrayList<>();
                for (int pitch = 0; pitch <= 127; pitch++) {
                    if (chordMembers(guide.type) ? chord.contains(pitch) : contains(scale,pitch % 12)) { pitches.add(pitch); }
                }
                spaces.put(spaceKey,pitches);
            }
            int position = Collections.binarySearch(pitches,gesture[0]);
            if (position < 0) { return 0; }
            int offset = phase % 4, origin = guide.steps[offset % guide.steps.length];
            double distance = 0;
            for (int i = 1; i < gesture.length; i++) {
                int index = position + guide.steps[(offset + i) % guide.steps.length] - origin;
                if (index < 0 || index >= pitches.size()) { return 0; }
                distance += Math.abs(gesture[i] - pitches.get(index));
            }
            return Math.max(0,3.0 - distance * 0.45);
        }
        private Guide choose(List<Integer> weights,int motif,int segment,int direction) {
            Random random = new Random(mix(seed ^ Math.abs((long) motif) * 0x9e3779b97f4a7c15L ^ segment * 0xd1b54a32d192ed03L));
            boolean fresh = random.nextInt(100) < newChance;
            int total = 0;
            for (BlockType type : BlockType.values()) { total += weight(weights,type); }
            if (total == 0) { return null; }
            int roll = random.nextInt(total); BlockType family = BlockType.NOTE;
            for (BlockType type : BlockType.values()) {
                roll -= weight(weights,type); if (roll < 0) { family = type; break; }
            }
            List<int[]> eligible = new ArrayList<>();
            for (int[] shape : SHAPES.get(family)) {
                if (direction == 0 || orientation(shape) == direction || orientation(shape) == 0) { eligible.add(shape); }
            }
            int[] steps = eligible.get(random.nextInt(eligible.size())).clone();
            if (fresh) { steps = derive(steps,random); }
            if (orientation(steps) != 0 && direction != 0 && orientation(steps) != direction) {
                for (int i = 0; i < steps.length; i++) { steps[i] = -steps[i]; }
            }
            return new Guide(family,steps);
        }
        private static int weight(List<Integer> weights,BlockType type) {
            return weights.isEmpty() ? 100 : type.ordinal() < weights.size() ? Math.max(0,Math.min(100,weights.get(type.ordinal()))) : 0;
        }
        private static int[] derive(int[] source,Random random) {
            if (orientation(source) == 0) { return new int[] {0,0,1,0}; }
            int[] result;
            switch (random.nextInt(3)) {
                case 0:
                    result = new int[source.length];
                    for (int i = 0; i < source.length; i++) { result[i] = source[source.length - 1 - i] - source[source.length - 1]; }
                    break;
                case 1:
                    result = new int[source.length]; int rotate = 1 + random.nextInt(source.length - 1);
                    for (int i = 0; i < source.length; i++) { result[i] = source[(rotate + i) % source.length] - source[rotate]; }
                    break;
                default:
                    result = Arrays.copyOf(source,source.length + 2);
                    result[source.length] = source[source.length - 1] + Integer.signum(source[source.length - 1]);
                    result[source.length + 1] = source[source.length - 1];
            }
            return result;
        }
    }

    /** Explicit realization API retains the historical shape-coordinate convention. */
    public static int[] realize(BlockType type,Integer[] shape,int start,HarmonyTimeline.Event chord,int[] scale,Transform transform) {
        List<Integer> pitches = new ArrayList<>();
        boolean members = chordMembers(type);
        for (int pitch = 0; pitch <= 127; pitch++) {
            if (members ? chord.contains(pitch) : contains(scale,pitch % 12)) { pitches.add(pitch); }
        }
        int position = pitches.indexOf(start);
        if (position < 0) { return new int[0]; }
        int[] result = new int[transform == Transform.FRAGMENT ? Math.min(3,shape.length) : shape.length];
        int direction = transform == Transform.INVERT ? -1 : 1;
        for (int i = 0; i < result.length; i++) {
            int offset = members ? (shape[i] == 7 ? chord.pitchClasses().size() : shape[i] / 2) : shape[i];
            int index = position + direction * offset;
            if (index < 0 || index >= pitches.size()) { return new int[0]; }
            result[i] = pitches.get(index);
            if (transform == Transform.INTERVAL_SEQUENCE && !members) {
                int[] major = {0,2,4,5,7,9,11}; int steps = direction * shape[i];
                result[i] = start + 12 * Math.floorDiv(steps,7) + major[Math.floorMod(steps,7)];
            }
        }
        return result;
    }
    public static double affinity(int[] gesture,HarmonyTimeline.Event chord,int[] scale,List<Integer> weights,int motif) {
        return new Evaluator().affinity(gesture,chord,scale,weights,motif);
    }
    private static boolean contains(int[] values,int value) {
        for (int item : values) { if (Math.floorMod(item,12) == value) { return true; } }
        return false;
    }
}
