package org.vibehistorian.vibecomposer.generation;

import java.util.Random;

/** Seeded duration preferences; speed changes likelihoods, never the available vocabulary. */
final class ContextualRhythmDuration {
    private static final long BEAT = HarmonyTimeline.TICKS_PER_BEAT;
    private static final long[] DURATIONS = { BEAT / 4, BEAT / 2, BEAT, 2 * BEAT };

    private ContextualRhythmDuration() { }

    static long choose(HarmonyTimeline harmony, int speed, long cursor, long limit,
            boolean arrival, boolean continuation, Random random) {
        HarmonyTimeline.Event event = harmony.at(cursor);
        HarmonyTimeline.Event next = event.end < harmony.length() ? harmony.at(event.end) : null;
        boolean change = next != null && !event.pitchClasses().equals(next.pitchClasses());
        boolean approach = change && event.end - cursor <= BEAT;
        long available = limit - cursor;
        // Weak subdivisions return to the beat; short approaches leave a fresh chord attack available.
        if (cursor % BEAT != 0) { available = Math.min(available,BEAT - cursor % BEAT); }
        if (approach) { available = Math.min(available,event.end - cursor); }

        double preferred = (35.0 - Math.max(-100,Math.min(100,speed))) / 50.0;
        if (cursor % BEAT != 0) { preferred -= 0.25; }
        if (continuation) { preferred -= 0.2; }
        if (approach) {
            preferred -= 0.45;
            if ((event.function == HarmonyTimeline.Function.DOMINANT
                    || event.function == HarmonyTimeline.Function.APPLIED_DOMINANT)
                    && Math.floorMod(next.root - event.root,12) == 5) { preferred -= 0.25; }
        }
        if (arrival) { preferred += 0.45; }

        double[] weights = new double[DURATIONS.length];
        double peak = 0;
        for (int i = 0; i < DURATIONS.length; i++) {
            if (DURATIONS[i] > available) { continue; }
            double distance = (i - 2 - preferred) / 0.9;
            weights[i] = Math.exp(-0.5 * distance * distance);
            peak = Math.max(peak,weights[i]);
        }
        double total = 0;
        for (int i = 0; i < DURATIONS.length; i++) {
            if (DURATIONS[i] > available) { continue; }
            // Scale the ornament floor to the eligible peak: clipping must not amplify it at low speeds.
            weights[i] += peak * 0.002;
            total += weights[i];
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < DURATIONS.length; i++) {
            if (weights[i] == 0) { continue; }
            roll -= weights[i];
            if (roll < 0) { return DURATIONS[i]; }
        }
        // Exact authored harmony/phrase boundaries can leave less than a sixteenth note.
        return Math.min(available,DURATIONS[0]);
    }
}
