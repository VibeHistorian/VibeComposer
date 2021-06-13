package org.vibehistorian.vibecomposer.Enums;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "arpPattern")
@XmlEnum
public enum ArpPattern {
	RANDOM(new int[] { 0 }), UP(new int[] { 0, 1, 2, 3, 4, 5 }),
	DOWN(new int[] { 5, 4, 3, 2, 1, 0 }), UPDOWN(new int[] { 0, 1, 2, 1, 0, 1, 2, 1 }),
	DOWNUP(new int[] { 2, 1, 0, 1, 2, 1, 0, 1 });

	public final int[] pattern;

	private ArpPattern(int[] pattern) {
		this.pattern = pattern;
	}

	public List<Integer> getPatternByLength(int hits, int chordLength, int patternRepeat) {
		List<Integer> result = new ArrayList<>();

		int maxL = 6;

		int[] cutPattern = null;
		if (pattern[0] != maxL - 1) {
			cutPattern = Arrays.copyOf(pattern, chordLength);
		} else {
			cutPattern = Arrays.copyOfRange(pattern, maxL - chordLength, maxL);
		}


		while (result.size() < hits) {
			result.addAll(Arrays.stream(cutPattern).boxed().collect(Collectors.toList()));
		}
		result = result.subList(0, hits);
		List<Integer> repResult = new ArrayList<>();
		for (int i = 0; i < patternRepeat; i++) {
			repResult.addAll(result);
		}
		return repResult;
	}
}
