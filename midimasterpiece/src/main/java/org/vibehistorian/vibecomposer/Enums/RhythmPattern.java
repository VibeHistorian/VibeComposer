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
	FULL(new int[] { 1, 1, 1, 1, 1, 1, 1, 1 }), ALT(new int[] { 1, 0, 1, 0, 1, 0, 1, 0 }),
	ONEPER4(new int[] { 1, 0, 0, 0, 1, 0, 0, 0 }), TRESILLO(new int[] { 1, 0, 0, 1, 0, 0, 1, 0 }),
	SINGLE(new int[] { 1, 0, 0, 0, 0, 0, 0, 0 }), ONESIX(new int[] { 1, 0, 0, 0, 0, 1, 0, 0 }),
	CUSTOM(new int[] { 0, 0, 0, 0, 0, 0, 0, 0 }), MELODY1(new int[] { 1, 1, 1, 1, 1, 1, 1, 1 });

	public final int[] pattern;

	private RhythmPattern(int[] pattern) {
		this.pattern = pattern;
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
		return counter / pattern.length;
	}
}
