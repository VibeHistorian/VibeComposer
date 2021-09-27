package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OMNI {
	public static final String EMPTYCOMBO = "---";
	public static final List<Integer> PART_INTS = Arrays.asList(new Integer[] { 0, 1, 2, 3, 4 });

	public static Color alphen(Color c, int alphaValue) {
		Color newC = new Color(c.getRed(), c.getGreen(), c.getBlue(), alphaValue);
		return newC;
	}

	public static List<Integer> parseIntsString(String ints) {
		List<Integer> intsList = new ArrayList<>();
		try {
			for (String s : ints.split(",")) {
				intsList.add(Integer.valueOf(s.trim()));
			}
		} catch (Exception e) {
			return null;
		}
		return intsList;
	}

	public static void clampIntList(List<Integer> list, int min, int max) {
		for (int i = 0; i < list.size(); i++) {
			list.set(i, clamp(list.get(i), min, max));
		}
	}

	public static void clampIntArray(Integer[] array, int min, int max) {
		for (int i = 0; i < array.length; i++) {
			array[i] = clamp(array[i], min, max);
		}
	}

	public static int clamp(int num, int min, int max) {
		return Math.min(max, (Math.max(min, num)));
	}

	public static int clampChance(int num) {
		return Math.min(100, (Math.max(0, num)));
	}

	public static int clampVel(double d) {
		return clamp((int) d, 0, 127);
	}

	public static <T> T getWeightedValue(T[] values, int searchedWeight, int[] weights) {
		for (int i = 0; i < weights.length; i++) {
			if (searchedWeight < weights[i]) {
				return values[i];
			}
		}
		throw new IllegalArgumentException("WEIGHTED error: Value higher than 99!");
	}
}
