package org.vibehistorian.vibecomposer.Enums;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "rhythmPattern")
@XmlEnum
public enum RhythmPattern {
	FULL(new int[] { 1, 1, 1, 1, 1, 1, 1, 1 }, 0), ALT(new int[] { 1, 0, 1, 0, 1, 0, 1, 0 }, 1),
	ONEPER4(new int[] { 1, 0, 0, 0, 1, 0, 0, 0 }, 3),
	TRESILLO(new int[] { 1, 0, 0, 1, 0, 0, 1, 0 }, 7),
	SINGLE(new int[] { 1, 0, 0, 0, 0, 0, 0, 0 }, 7),
	ONESIX(new int[] { 1, 0, 0, 0, 0, 1, 0, 0 }, 7),
	CUSTOM(new int[] { 0, 0, 0, 0, 0, 0, 0, 0 }, 7),
	MELODY1(new int[] { 0, 0, 0, 0, 0, 0, 0, 0 }, 0);

	public final int[] pattern;
	public final int maxShift;

	private RhythmPattern(int[] pattern, int mShift) {
		this.pattern = pattern;
		maxShift = mShift;
	}

	public List<Integer> getPatternByLength(int length, int patternShift) {
		List<Integer> result = new ArrayList<>();

		while (result.size() < length) {
			result.addAll(Arrays.stream(pattern).boxed().collect(Collectors.toList()));
		}
		Collections.rotate(result, patternShift);
		result = result.subList(0, length);
		return result;
	}

	public double getNoteFrequency() {
		double counter = 0;
		for (int i = 0; i < pattern.length; i++) {
			counter += pattern[i];
		}
		return counter / (double) pattern.length;
	}
}
