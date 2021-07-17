package org.vibehistorian.vibecomposer.Parts.Defaults;

import java.util.Random;

import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Parts.DrumPart;

public class DrumSettings {

	public static int MAX_SWING = 20;

	RhythmPattern[] patterns;
	Integer[] hits;
	Integer[] chords;
	Integer[] shift;
	boolean variableShift = false;
	int maxPause = 0;
	int maxExc = 10;
	boolean dynamicable = false;
	boolean swingable = false;
	boolean fillable = false;

	public boolean isVariableShift() {
		return variableShift;
	}

	public void setVariableShift(boolean variableShift) {
		this.variableShift = variableShift;
	}

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

	public void applyToDrumPart(DrumPart dp, int seed) {
		if (patterns == null) {
			throw new IllegalArgumentException("Not initialized DrumSettings!");
		}

		Random rand = new Random();
		int varOrder = rand.nextInt(patterns.length);
		dp.setPattern(patterns[varOrder]);
		dp.setHitsPerPattern(hits[varOrder]);
		dp.setChordSpan(chords[varOrder]);
		if (variableShift) {
			dp.setPatternShift(rand.nextInt(shift[varOrder] + 1));
		} else {
			dp.setPatternShift(shift[varOrder]);
		}


		if (dp.getPattern() == RhythmPattern.FULL) {
			dp.setPauseChance(rand.nextInt(maxPause + 1));
		}
		dp.setExceptionChance(rand.nextInt(maxExc + 1));
		dp.setVelocityPattern(dynamicable ? rand.nextBoolean() : false);
		if (fillable) {
			dp.setChordSpanFill(ChordSpanFill.getWeighted(rand.nextInt(100)));
		}
		if (swingable) {
			rand.setSeed(seed);
			int swingPercent = 50 + rand.nextInt(MAX_SWING * 2 + 1) - MAX_SWING;
			dp.setSwingPercent(swingPercent);
		}


	}
}