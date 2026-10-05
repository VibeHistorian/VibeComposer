package org.vibehistorian.vibecomposer.generation;

import org.vibehistorian.vibecomposer.MidiUtils;

import java.util.List;
import java.util.Random;

public class MidiGeneratorUtils {

	static List<Integer> makeRandomArpPattern(int hits, boolean repeatableNotes,
			Random uiGenerator2arpPattern) {
		return ArpPhraseGenerator.makeRandomArpPattern(hits, repeatableNotes,
				uiGenerator2arpPattern);
	}

	static boolean isDottedNote(double note, MidiTiming timing) {
		return MidiUtils.roughlyEqual(timing.dottedQuarterNote, note)
				|| MidiUtils.roughlyEqual(timing.dottedWholeNote, note)
				|| MidiUtils.roughlyEqual(timing.dottedHalfNote, note)
				|| MidiUtils.roughlyEqual(timing.dottedEighthNote, note)
				|| MidiUtils.roughlyEqual(timing.dottedSixteenthNote, note);
	}

	public static int multiplyVelocity(int velocity, int multiplierPercentage, int maxAdjust,
			int minAdjust) {
		if (multiplierPercentage == 100) {
			return velocity;
		} else if (multiplierPercentage > 100) {
			return Math.min(127 - maxAdjust, velocity * multiplierPercentage / 100);
		} else {
			return Math.max(0 + minAdjust, velocity * multiplierPercentage / 100);
		}
	}

}
