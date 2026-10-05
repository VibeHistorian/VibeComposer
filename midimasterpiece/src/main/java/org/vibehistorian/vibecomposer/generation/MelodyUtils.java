package org.vibehistorian.vibecomposer.generation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class MelodyUtils {

	public enum NoteTargetDirection {
		ANY(0), ASC(1), DESC(-1);

		public int direction;

		NoteTargetDirection(int direction) {
			this.direction = direction;
		}
	}

	//public static final List<Integer> chordyNotes = Arrays.asList(new Integer[] { 0, 2, 4, 7 });
	public static final List<Integer> cMajorSubstituteNotes = Arrays
			.asList(0, 2, 3, 5);
	public static List<List<Integer>> MELODY_PATTERNS = new ArrayList<>();
	public static List<List<Integer>> SOLO_MELODY_PATTERNS = new ArrayList<>();
	public static List<Integer> ALT_PATTERN_INDEXES = Arrays.asList(0, 1, 8, 9, 10, 12, 15, 16);
	public static List<Integer> BLOCK_CHANGE_JUMP_PREFERENCE = Arrays.asList(0, 7, 4, 3, 1, 2, 5, 6);

	public static List<List<Integer>> CHORD_DIRECTIONS = new ArrayList<>();

	public static final int NUM_LISTS = 4;

	static {

		CHORD_DIRECTIONS.add(Arrays.asList(-1, -1, 1, 2));
		CHORD_DIRECTIONS.add(Arrays.asList(-1, 0, 0, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, -1, -1, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, -1, 1, 0));
		CHORD_DIRECTIONS.add(Arrays.asList(0, -1, 0, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, -1, 1, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, 1, -1, 0));
		CHORD_DIRECTIONS.add(Arrays.asList(0, 1, -1, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, 1, 1, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, 1, 1, 2));
		//CHORD_DIRECTIONS.add(Arrays.asList(new Integer[] { 0, 0, 1, -1 }));
		CHORD_DIRECTIONS.add(Arrays.asList(0, 0, -1, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(1, -1, 1, 2));
		CHORD_DIRECTIONS.add(Arrays.asList(1, -1, 0, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(2, 0, 1, 2));
		CHORD_DIRECTIONS.add(Arrays.asList(2, 0, -1, 1));

		// ALT PATTERNS: 0, 1, 8, 9, 10, 12, 15, 16
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 1, 3));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 1, 3, 1, 2, 1, 4));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 1, 3, 2, 2, 3, 4));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 3, 4, 1, 2, 1, 3));
		MELODY_PATTERNS.add(Arrays.asList(1, 1, 2, 3));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 3, 1));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 3, 2));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 3, 3));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 2, 1));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 2, 3));
		MELODY_PATTERNS.add(Arrays.asList(1, 1, 2, 1)); // 7
		//MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 1, 1, 2, 2 }));
		MELODY_PATTERNS.add(Arrays.asList(1, 1, 1, 2));
		//MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 1, 1, 1, 1 }));
		// inverse patterns
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, 1, -1, 2));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, 2, -2, -1));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, 2, -1, 2));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, -1, 2, 3));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, -1, 2, 2));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, -1, -1, 1));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, -1, 1, 2));
		MELODY_PATTERNS.addAll(SOLO_MELODY_PATTERNS);


	}

	public static Integer[] getRandomForType(Integer type, Random melodyBlockGenerator) {
		return MelodyBlockUtils.getRandomForType(type, melodyBlockGenerator);
	}

	public static int[] normalizedCumulativeWeights(Integer... weights) {
		return MelodyBlockUtils.normalizedCumulativeWeights(weights);
	}

	public static List<Integer> getRandomMelodyPattern(int altPatternChance, Integer randomSeed) {
		return MelodyPatternUtils.getRandomMelodyPattern(altPatternChance, randomSeed);
	}
}
