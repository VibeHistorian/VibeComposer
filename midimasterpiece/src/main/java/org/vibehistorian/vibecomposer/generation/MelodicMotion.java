package org.vibehistorian.vibecomposer.generation;

/** Measurements distinguish pitch positions from the displacements between them. */
public final class MelodicMotion {
    private MelodicMotion() { }

    public static int directionChanges(int[] pitches) {
        int last = 0, changes = 0;
        for (int i = 1; i < pitches.length; i++) {
            int direction = Integer.compare(pitches[i], pitches[i - 1]);
            if (direction != 0) {
                if (last != 0 && last != direction) { changes++; }
                last = direction;
            }
        }
        return changes;
    }

    public static int displacementDirectionChanges(int[] displacements) {
        int last = 0, changes = 0;
        for (int displacement : displacements) {
            int direction = Integer.signum(displacement);
            if (direction != 0) {
                if (last != 0 && last != direction) { changes++; }
                last = direction;
            }
        }
        return changes;
    }

    public static int largestLeap(int[] pitches) {
        int largest = 0;
        for (int i = 1; i < pitches.length; i++) { largest = Math.max(largest, Math.abs(pitches[i] - pitches[i - 1])); }
        return largest;
    }
}
