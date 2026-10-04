package org.vibehistorian.vibecomposer.generation;

import java.util.Arrays;

/** Immutable beat-length values for one MIDI generation run. */
public final class MidiTiming {
	public static final int MELODY_PATTERN_RESOLUTION = 16;

	public final double noteMultiplier;
	public final double sixteenthNote;
	public final double dottedSixteenthNote;
	public final double eighthNote;
	public final double dottedEighthNote;
	public final double quarterNote;
	public final double dottedQuarterNote;
	public final double halfNote;
	public final double dottedHalfNote;
	public final double wholeNote;
	public final double dottedWholeNote;
	public final double startTimeDelay;

	private final double[] melodyDurationOptions;
	private final double[] melodyDurationChances;
	private final double[] melodySkeletonDurations;
	private final double[] shortMelodySkeletonDurations;

	public MidiTiming(int stretchPercent) {
		noteMultiplier = stretchPercent / 100.0;
		sixteenthNote = 0.25 * noteMultiplier;
		dottedSixteenthNote = 0.375 * noteMultiplier;
		eighthNote = 0.5 * noteMultiplier;
		dottedEighthNote = 0.75 * noteMultiplier;
		quarterNote = noteMultiplier;
		dottedQuarterNote = 1.5 * noteMultiplier;
		halfNote = 2.0 * noteMultiplier;
		dottedHalfNote = 3.0 * noteMultiplier;
		wholeNote = 4.0 * noteMultiplier;
		dottedWholeNote = 6.0 * noteMultiplier;
		startTimeDelay = quarterNote;
		melodyDurationOptions = new double[] { halfNote, dottedQuarterNote, quarterNote, eighthNote };
		melodyDurationChances = new double[] { 0.3, 0.6, 1.0, 1.0 };
		melodySkeletonDurations = new double[] { sixteenthNote, eighthNote, dottedEighthNote,
				quarterNote, dottedQuarterNote, halfNote };
		shortMelodySkeletonDurations = new double[] { sixteenthNote / 2.0, sixteenthNote,
				eighthNote, dottedEighthNote, quarterNote, dottedQuarterNote, halfNote };
	}

	public double[] getMelodyDurationOptions() {
		return Arrays.copyOf(melodyDurationOptions, melodyDurationOptions.length);
	}

	public double[] getMelodyDurationChances() {
		return Arrays.copyOf(melodyDurationChances, melodyDurationChances.length);
	}

	public double[] getMelodySkeletonDurations() {
		return Arrays.copyOf(melodySkeletonDurations, melodySkeletonDurations.length);
	}

	public double[] getShortMelodySkeletonDurations() {
		return Arrays.copyOf(shortMelodySkeletonDurations, shortMelodySkeletonDurations.length);
	}
}
