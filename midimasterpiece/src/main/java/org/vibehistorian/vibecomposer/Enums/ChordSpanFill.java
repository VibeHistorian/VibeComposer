package org.vibehistorian.vibecomposer.Enums;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "chordSpanFill")
@XmlEnum
public enum ChordSpanFill {
	ALL(new int[] { 1, 1, 1, 1, 1, 1, 1, 1 }), ODD(new int[] { 0, 1, 0, 1, 0, 1, 0, 1 }),
	EVEN(new int[] { 1, 0, 1, 0, 1, 0, 1, 0 }), FST(new int[] { 1, 0, 0, 0, 1, 0, 0, 0 }),
	SCND(new int[] { 0, 1, 0, 0, 0, 1, 0, 0 }), THRD(new int[] { 0, 0, 1, 0, 0, 0, 1, 0 }),
	FRTH(new int[] { 0, 0, 0, 1, 0, 0, 0, 1 });

	private static final int[] weights = new int[] { 60, 72, 84, 86, 88, 90, 100 };
	private int[] chordPattern;

	private ChordSpanFill(int[] pattern) {
		chordPattern = pattern;
	}

	public static ChordSpanFill getWeighted(int value) {
		for (int i = 0; i < weights.length; i++) {
			if (value < weights[i]) {
				return ChordSpanFill.values()[i];
			}
		}
		throw new IllegalArgumentException("ChordSpanFill error: Value higher than 99!");
	}

	public List<Integer> getPatternByLength(int length) {
		List<Integer> result = new ArrayList<>();

		while (result.size() < length) {
			result.addAll(Arrays.stream(chordPattern).boxed().collect(Collectors.toList()));
		}
		result = result.subList(0, length);
		return result;
	}

	public int[] getChordPattern() {
		return chordPattern;
	}

	public void setChordPattern(int[] chordPattern) {
		this.chordPattern = chordPattern;
	}
}
