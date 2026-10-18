package org.vibehistorian.vibecomposer.generation;

import java.util.*;
import org.vibehistorian.vibecomposer.MidiUtils;

/** Seeded compose-time suggestions; final pitch legality remains the planner's responsibility. */
public final class ContextualMelodyRandomizer {
    private ContextualMelodyRandomizer() { }

    public static int[] range(long seed, int direction, Collection<Integer> anchors) {
        Random random = new Random(seed ^ 0x72616e67654cL);
        int span = new int[] {12,14,16,19,24}[random.nextInt(5)];
        int center = 55 + random.nextInt(10);
        int low = center - span / 2, high = low + span;
        for (int pitch : anchors) {
            // Leave movement space on the requested side of an exact anchor.
            low = Math.min(low,Math.max(0,pitch - (direction <= 0 ? 4 : 2)));
            high = Math.max(high,Math.min(127,pitch + (direction >= 0 ? 4 : 2)));
        }
        return new int[] {low,high};
    }

    public static List<Integer> targets(HarmonyTimeline harmony, MidiUtils.ScaleMode mode,
            int low, int high, int direction, ContextualMelodyPlanner.Ending ending, long seed) {
        Random random = new Random(seed ^ 0x746172676574L);
        int anyKind = random.nextInt(4);
        double center = (low + high) / 2.0, width = (high - low) / 3.0;
        int previous = -1;
        List<Integer> result = new ArrayList<>();
        for (HarmonyTimeline.Event event : harmony.events()) {
            double position = harmony.events().size() == 1 ? 0 : event.index / (double) (harmony.events().size() - 1);
            double contour = direction != 0 ? direction * (2 * position - 1)
                    : anyKind < 2 ? (anyKind == 0 ? 1 : -1) * (2 * position - 1)
                    : (anyKind == 2 ? 1 : -1) * Math.sin(position * Math.PI);
            double desired = center + contour * width;
            boolean close = ending == ContextualMelodyPlanner.Ending.CLOSED
                    && event.index == harmony.events().size() - 1 && event.contains(0);
            int bestPitch = -1, bestDegree = 0; double bestScore = -Double.MAX_VALUE;
            for (int degree = 0; degree < mode.noteAdjustScale.length; degree++) {
                int pc = mode.noteAdjustScale[degree];
                if (close && pc != 0) { continue; }
                for (int pitch = low; pitch <= high; pitch++) {
                    if (pitch % 12 != pc) { continue; }
                    double score = (event.contains(pitch) ? 8 : 0) - Math.abs(pitch - desired) * 0.2 + random.nextDouble() * 1.5;
                    if (previous >= 0) {
                        score -= Math.abs(pitch - previous) * 0.16;
                        score += direction * Integer.signum(pitch - previous) * 0.15;
                        if (pitch % 12 == previous % 12) { score -= 0.6; }
                    }
                    if (score > bestScore) { bestScore = score; bestPitch = pitch; bestDegree = degree; }
                }
            }
            result.add(bestDegree); previous = bestPitch;
        }
        return result;
    }
}
