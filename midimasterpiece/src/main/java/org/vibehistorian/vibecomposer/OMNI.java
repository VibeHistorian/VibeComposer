package org.vibehistorian.vibecomposer;

import jm.music.data.Note;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OMNI {
	public static final String EMPTYCOMBO = "---";
	public static final List<Integer> PART_INTS = Arrays.asList(new Integer[] { 0, 1, 2, 3, 4 });

	public static Color alphen(Color c, int alphaValue) {
		Color newC = new Color(c.getRed(), c.getGreen(), c.getBlue(),
				OMNI.clamp(alphaValue, 0, 255));
		return newC;
	}

	public static Color mult(Color c, double multer) {
		return new Color(OMNI.clamp((int) (c.getRed() * multer), 0, 255),
				OMNI.clamp((int) (c.getGreen() * multer), 0, 255),
				OMNI.clamp((int) (c.getBlue() * multer), 0, 255), c.getAlpha());
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

	public static List<Double> parseDoublesString(String dbls) {
		List<Double> dblsList = new ArrayList<>();
		try {
			for (String s : dbls.split(",")) {
				dblsList.add(Double.valueOf(s.trim()));
			}
		} catch (Exception e) {
			return null;
		}
		return dblsList;
	}

	public static int sumList(List<Integer> nums) {
		int sum = 0;
		for (Integer n : nums) {
			sum += n;
		}
		return sum;
	}

	public static double sumListDouble(List<Double> nums) {
		double sum = 0;
		for (Double n : nums) {
			sum += n;
		}
		return sum;
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
		return clampMidi((int) d);
	}

	public static int clampMidi(int d) {
		return clamp(d, 0, 127);
	}

	public static <T> T getWeightedValue(T[] values, int searchedWeight, int[] weights) {
		for (int i = 0; i < weights.length; i++) {
			if (searchedWeight < weights[i]) {
				return values[i];
			}
		}
		return values[values.length - 1];
	}

	public static Color mixColor(Color c1, Color c2, double percentageMix) {
		Color newColor = new Color((int) interp(c1.getRed(), c2.getRed(), percentageMix),
				(int) interp(c1.getGreen(), c2.getGreen(), percentageMix),
				(int) interp(c1.getBlue(), c2.getBlue(), percentageMix),
				(int) interp(c1.getAlpha(), c2.getAlpha(), percentageMix));
		//LG.d("Color: " + c1.toString());
		//LG.d("Mixed: " + newColor.toString());
		return newColor;
	}

	public static double interp(double n1, double n2, double normalizedPercentage) {
		return n1 * (1 - normalizedPercentage) + n2 * normalizedPercentage;
	}

	public static boolean mouseInComp(JComponent c) {
		return mouseInComp(c, SwingUtils.getMouseLocation());
	}

	public static boolean mouseInComp(JComponent c, Point p) {
		try {

			Point cp = c.getLocationOnScreen();
			return pointInRect(p, cp.x, cp.y, c.getWidth(), c.getHeight());
		} catch (Exception e) {
			LG.d("Mouse in comp error!");
			return false;
		}
	}

	public static boolean pointInRect(Point p, int xRect, int yRect, int xWidth, int yHeight) {
		return p.x >= xRect && p.x <= xRect + xWidth && p.y >= yRect && p.y <= yRect + yHeight;
	}

	public static double clamp(double num, double min, double max) {
		return Math.min(max, (Math.max(min, num)));
	}

	public static <T> T d(T val, T defaultVal) {
		return val != null ? val : defaultVal;
	}

	public static <T extends Number> T maxOf(List<T> list) {
		return (T) list.stream().max((e1, e2) -> Double.compare(e1.doubleValue(), e2.doubleValue()))
				.get();
	}

	public static <T extends Number> T minOf(List<T> list) {
		return (T) list.stream().min((e1, e2) -> Double.compare(e1.doubleValue(), e2.doubleValue()))
				.get();
	}

	public static <T> int indexOf(T elem, T[] array) {
		if (array == null) {
			return -1;
		}
		for (int i = 0; i < array.length; i++) {
			if (array[i].equals(elem)) {
				return i;
			}
		}
		return -1;
	}

	public static int clampPitch(int newPitch) {
		if (newPitch < Note.REST + 100) {
			return Note.REST;
		}
		return clamp(newPitch, 0, 127);
	}
}
