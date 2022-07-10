package org.vibehistorian.vibecomposer.Enums;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
	MELODY1(new int[] { 0, 0, 0, 0, 0, 0, 0, 0 }, 0),
	EUCLID(new int[] { 0, 0, 0, 0, 0, 0, 0, 0 }, 7);

	public final int[] pattern;
	public final int maxShift;

	public static final List<RhythmPattern> VIABLE_PATTERNS = new ArrayList<>(
			Arrays.asList(RhythmPattern.values()));
	static {
		VIABLE_PATTERNS.remove(RhythmPattern.CUSTOM);
		VIABLE_PATTERNS.remove(RhythmPattern.EUCLID);
	}

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

	// bjorklund's algorithm - rewrite from JS version: https://github.com/mkontogiannis/euclidean-rhythms
	public static List<Integer> makeEuclideanPattern(int length, int usedHits, int patternShift,
			Integer maxHits) {
		final List<Integer> result = new ArrayList<>();
		if (usedHits < 0 || length < 0 || length < usedHits) {
			return result;
		}

		List<List<Integer>> first = IntStream.iterate(1, e -> e).limit(usedHits)
				.mapToObj(e -> new ArrayList<Integer>(Collections.singletonList(e)))
				.collect(Collectors.toList());
		List<List<Integer>> second = IntStream.iterate(0, e -> e).limit(length - usedHits)
				.mapToObj(e -> new ArrayList<Integer>(Collections.singletonList(e)))
				.collect(Collectors.toList());

		int firstLength = first.size();
		int minLength = Math.min(firstLength, second.size());

		int loopThreshold = 0;

		while (minLength > loopThreshold) {

			if (loopThreshold == 0) {
				loopThreshold = 1;
			}

			for (int i = 0; i < minLength; i++) {
				first.get(i).addAll(second.get(i));
			}

			if (minLength == firstLength) {
				second = second.subList(Math.min(second.size() - 1, minLength), second.size());
			} else {
				second = new ArrayList<>(first.subList(minLength, first.size()));
				first = new ArrayList<>(first.subList(0, Math.min(first.size(), minLength)));
			}
			firstLength = first.size();
			minLength = Math.min(firstLength, second.size());
		}

		first.forEach(e -> result.addAll(e));
		second.forEach(e -> result.addAll(e));
		//LG.i(StringUtils.join(result, ","));
		Collections.rotate(result, patternShift);
		if (maxHits != null) {
			List<Integer> resultCopy = new ArrayList<>(result);
			while (result.size() < maxHits) {
				result.addAll(resultCopy);
			}
		}
		return result.subList(0, (maxHits != null) ? maxHits : length);
	}
}
