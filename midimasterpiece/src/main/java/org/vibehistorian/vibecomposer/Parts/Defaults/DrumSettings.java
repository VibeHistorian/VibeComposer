package org.vibehistorian.vibecomposer.Parts.Defaults;

import java.util.Random;

import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Parts.DrumPart;

public class DrumSettings {
	RhythmPattern[] patterns;
	Integer[] hits;
	Integer[] chords;
	Integer[] shift;

	public Integer[] getShift() {
		return shift;
	}

	public void setShift(Integer[] shift) {
		this.shift = shift;
	}

	public RhythmPattern[] getPatterns() {
		return patterns;
	}

	public void setPatterns(RhythmPattern[] patterns) {
		this.patterns = patterns;
	}

	public Integer[] getHits() {
		return hits;
	}

	public void setHits(Integer[] hits) {
		this.hits = hits;
	}

	public Integer[] getChords() {
		return chords;
	}

	public void setChords(Integer[] chords) {
		this.chords = chords;
	}

	public void applyToDrumPart(DrumPart dp) {
		if (patterns == null) {
			throw new IllegalArgumentException("Not initialized DrumSettings!");
		}

		Random rand = new Random();
		int varOrder = rand.nextInt(patterns.length);
		dp.setPattern(patterns[varOrder]);
		dp.setHitsPerPattern(hits[varOrder]);
		dp.setChordSpan(chords[varOrder]);
		dp.setPatternShift(rand.nextInt(shift[varOrder] + 1));

		if (dp.getPattern() == RhythmPattern.FULL) {
			dp.setPauseChance(15);
		}
	}

	private static <T> T getRandom(Random generator, T[] array) {
		return getRandom(generator, array, 0, array.length);
	}

	private static <T> T getRandom(Random generator, T[] array, int from, int to) {
		from = Math.max(from, 0);
		to = Math.min(to, array.length);
		return array[generator.nextInt(to - from) + from];
	}
}