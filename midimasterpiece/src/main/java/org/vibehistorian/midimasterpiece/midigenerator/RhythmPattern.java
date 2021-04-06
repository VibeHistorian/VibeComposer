package org.vibehistorian.midimasterpiece.midigenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "rhythmPattern")
@XmlEnum
public enum RhythmPattern {
	RANDOM(new int[] { 1 }), ALTERNATE2(new int[] { 1, 0 }), ONEPER4(new int[] { 1, 0, 0, 0 }),
	TRESILLO(new int[] { 1, 0, 0, 1, 0, 0, 1, 0 }), SINGLE(new int[] { 1, 0, 0, 0, 0, 0, 0, 0 });
	
	public final int[] pattern;
	
	private RhythmPattern(int[] pattern) {
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
