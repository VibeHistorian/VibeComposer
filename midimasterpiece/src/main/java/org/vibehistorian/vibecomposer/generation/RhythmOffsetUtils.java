package org.vibehistorian.vibecomposer.generation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Generates seeded, bounded rhythm offsets whose total remains zero. */
public final class RhythmOffsetUtils {
    private RhythmOffsetUtils() {
    }

    public static List<Double> generateRhythmOffsets(int size, double offsetVariation,
            long randomSeed) {
        if (size < 1) {
            return new ArrayList<>();
        } else if (size == 1) {
            return Collections.singletonList(0.0);
        }

        Random random = new Random(randomSeed);
        List<Double> offsets = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < size - 1; i++) {
            double offset = -offsetVariation + (2 * offsetVariation) * random.nextDouble();
            offsets.add(offset);
            sum += offset;
        }

        double lastOffset = -sum;
        if (Math.abs(sum) > offsetVariation) {
            double excess = sum;
            for (int i = 0; i < size - 1 && Math.abs(excess) > 0.00001; i++) {
                double current = offsets.get(i);
                double adjustment = (random.nextDouble() - 0.5) * 2
                        * Math.min(Math.abs(excess), offsetVariation - Math.abs(current));
                if ((excess < 0 && adjustment < 0) || (excess > 0 && adjustment > 0)) {
                    adjustment *= -1;
                }
                offsets.set(i, current + adjustment);
                excess += adjustment;
            }
            lastOffset = -excess;
        }

        if (lastOffset < -offsetVariation) lastOffset = -offsetVariation;
        if (lastOffset > offsetVariation) lastOffset = offsetVariation;
        offsets.add(lastOffset);
        Collections.shuffle(offsets, random);
        return offsets;
    }
}
