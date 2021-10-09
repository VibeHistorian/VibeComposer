package org.vibehistorian.vibecomposer.Enums;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.vibecomposer.Helpers.OMNI;

@XmlType(name = "arpPattern")
@XmlEnum
public enum ArpPattern {
	RANDOM, UP, DOWN, UPDOWN, DOWNUP, FROG_U, FROG_D;

	public List<Integer> getPatternByLength(int hits, int chordLength, int patternRepeat) {
		List<Integer> result = new ArrayList<>();

		ArpPattern usedPattern = ArpPattern.this;
		if (usedPattern == FROG_U && chordLength < 3) {
			usedPattern = UP;
		} else if (usedPattern == FROG_D && chordLength < 3) {
			usedPattern = DOWN;
		}

		int[] patternArray = new int[hits];
		switch (usedPattern) {
		case RANDOM:
			patternArray = IntStream.iterate(1, e -> e).limit(hits).toArray();
			break;
		case UP:
			patternArray = IntStream.iterate(0, e -> (e >= chordLength - 1) ? 0 : e + 1).limit(hits)
					.toArray();
			break;
		case DOWN:
			patternArray = IntStream
					.iterate(chordLength - 1, e -> (e == 0) ? chordLength - 1 : e - 1).limit(hits)
					.toArray();
			break;
		case UPDOWN:
			int curr = 0;
			int adding = 1;
			for (int i = 0; i < hits; i++) {
				patternArray[i] = curr;
				if (adding == 1 && curr == chordLength - 1) {
					adding = -1;
				} else if (adding == -1 && curr == 0) {
					adding = 1;
				}
				curr = curr + adding;
			}
			break;
		case DOWNUP:
			curr = chordLength - 1;
			adding = -1;
			for (int i = 0; i < hits; i++) {
				patternArray[i] = curr;
				if (adding == 1 && curr == chordLength - 1) {
					adding = -1;
				} else if (adding == -1 && curr == 0) {
					adding = 1;
				}
				curr = curr + adding;
			}
			break;
		case FROG_U:
			// 5: 0 2 1 3 2 4 3 5 0
			curr = 0;
			adding = 2;
			for (int i = 0; i < hits; i++) {
				patternArray[i] = curr;
				if (curr == chordLength - 1 && adding == 2) {
					curr = 0;
				} else {
					curr += adding;
					adding = (adding == 2) ? -1 : 2;
				}
			}
			break;
		case FROG_D:
			curr = chordLength - 1;
			adding = -2;
			for (int i = 0; i < hits; i++) {
				patternArray[i] = curr;
				if (curr < 2 && adding == -2) {
					curr = chordLength - 1;
				} else {
					curr = OMNI.clamp(curr + adding, 0, chordLength - 1);
					adding = (adding == -2) ? 1 : -2;
				}
			}
			break;
		default:
			throw new IllegalArgumentException("Unsupported ArpPattern!");

		}


		while (result.size() < hits) {
			result.addAll(Arrays.stream(patternArray).boxed().collect(Collectors.toList()));
		}
		//System.out.println(StringUtils.join(result, ","));
		result = result.subList(0, hits);
		List<Integer> repResult = new ArrayList<>();
		for (int i = 0; i < patternRepeat; i++) {
			repResult.addAll(result);
		}
		return repResult;
	}
}
