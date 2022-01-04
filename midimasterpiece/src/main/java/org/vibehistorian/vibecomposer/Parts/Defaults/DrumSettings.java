package org.vibehistorian.vibecomposer.Parts.Defaults;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Parts.DrumPart;

public class DrumSettings {

	public static List<List<Integer>> COOL_16_PATTERNS = new ArrayList<>();

	static {
		COOL_16_PATTERNS.add(
				Arrays.asList(new Integer[] { 1, 0, 1, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0 }));
		COOL_16_PATTERNS.add(
				Arrays.asList(new Integer[] { 1, 1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0 }));
		COOL_16_PATTERNS.add(
				Arrays.asList(new Integer[] { 1, 1, 0, 1, 0, 0, 0, 1, 0, 1, 1, 0, 0, 0, 0, 0 }));
		COOL_16_PATTERNS.add(
				Arrays.asList(new Integer[] { 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 1, 0 }));
	}

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
	boolean melodyable = false;

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

	public boolean isSwingable() {
		return swingable;
	}

	public void setSwingable(boolean swingable) {
		this.swingable = swingable;
	}

	public boolean isFillable() {
		return fillable;
	}

	public void setFillable(boolean fillable) {
		this.fillable = fillable;
	}

	public boolean isDynamicable() {
		return dynamicable;
	}

	public void setDynamicable(boolean dynamicable) {
		this.dynamicable = dynamicable;
	}

	public boolean isMelodyable() {
		return melodyable;
	}

	public void setMelodyable(boolean melodyable) {
		this.melodyable = melodyable;
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
		dp.setPatternShift(shift[varOrder]);


		if (dp.getPattern() == RhythmPattern.FULL) {
			dp.setPauseChance(rand.nextInt(maxPause + 1));
		} else if (dp.getPattern() == RhythmPattern.CUSTOM) {
			List<Integer> cool32Pattern = new ArrayList<>(
					COOL_16_PATTERNS.get(rand.nextInt(COOL_16_PATTERNS.size())));
			cool32Pattern.addAll(cool32Pattern);
			dp.setCustomPattern(cool32Pattern);
		}
		dp.setExceptionChance(rand.nextInt(maxExc + 1));
		if (fillable) {
			dp.setChordSpanFill(ChordSpanFill.getWeighted(rand.nextInt(100)));
		}
		dp.setFillFlip(false);

		if (melodyable) {
			if (rand.nextInt(100) < 30) {
				dp.setPattern(RhythmPattern.MELODY1);
				dp.setPauseChance(20 + rand.nextInt(maxPause + 1));
			}

		}

		if (swingable) {
			rand.setSeed(seed);
			int swingPercent = 50 + rand.nextInt(MAX_SWING * 2 + 1) - MAX_SWING;
			dp.setSwingPercent(swingPercent);
		}


	}
}