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
	RANDOM(new int[] { 0 }), UPWARD(new int[] { 0, 1, 2, 3, 4, 5 }),
	DOWNWARD(new int[] { 5, 4, 3, 2, 1, 0 }), UPDOWN(new int[] { 0, 1, 2, 1, 0, 1, 2, 1 }),
	DOWNUP(new int[] { 2, 1, 0, 1, 2, 1, 0, 1 });

	public final int[] pattern;

	private ArpPattern(int[] pattern) {
		this.pattern = pattern;
	}

	public List<Integer> getPatternByLength(int length) {
		List<Integer> result = new ArrayList<>();

		while (result.size() < length) {
			result.addAll(Arrays.stream(pattern).boxed().collect(Collectors.toList()));
		}
		result = result.subList(0, length);
		return result;
	}
}
