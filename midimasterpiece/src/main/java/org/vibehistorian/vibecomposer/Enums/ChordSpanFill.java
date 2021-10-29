package org.vibehistorian.vibecomposer.Enums;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.vibecomposer.Helpers.OMNI;

@XmlType(name = "chordSpanFill")
@XmlEnum
public enum ChordSpanFill {
	ALL(new int[] { 1, 1, 1, 1, 1, 1, 1, 1 }), ODD(new int[] { 0, 1, 0, 1, 0, 1, 0, 1 }),
	EVEN(new int[] { 1, 0, 1, 0, 1, 0, 1, 0 }), F1(new int[] { 1, 0, 0, 0, 1, 0, 0, 0 }),
	F2(new int[] { 0, 1, 0, 0, 0, 1, 0, 0 }), F3(new int[] { 0, 0, 1, 0, 0, 0, 1, 0 }),
	F4(new int[] { 0, 0, 0, 1, 0, 0, 0, 1 }), F12(new int[] { 1, 1, 0, 0, 1, 1, 0, 0 }),
	F23(new int[] { 0, 1, 1, 0, 0, 1, 1, 0 }), F34(new int[] { 0, 0, 1, 1, 0, 0, 1, 1 });

	private static final int[] weights = new int[] { 60, 72, 84, 86, 88, 90, 92, 95, 95, 100 };
	private int[] chordPattern;

	private ChordSpanFill(int[] pattern) {
		chordPattern = pattern;
	}

	public static ChordSpanFill getWeighted(int value) {
		return OMNI.getWeightedValue(ChordSpanFill.values(), value, weights);
	}

	public List<Integer> getPatternByLength(int length) {
		List<Integer> result = new ArrayList<>();

		while (result.size() < length) {
			result.addAll(Arrays.stream(chordPattern).boxed().collect(Collectors.toList()));
		}
		result = result.subList(0, length);
		return result;
	}

	public List<Integer> getPatternByLength(int length, boolean flipped) {
		List<Integer> result = getPatternByLength(length);
		if (flipped) {
			for (int i = 0; i < result.size(); i++) {
				result.set(i, 1 - result.get(i));
			}
		}
		return result;
	}

	public int[] getChordPattern() {
		return chordPattern;
	}

	public void setChordPattern(int[] chordPattern) {
		this.chordPattern = chordPattern;
	}
}
