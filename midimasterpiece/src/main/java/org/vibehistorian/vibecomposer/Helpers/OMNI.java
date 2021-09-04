package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class OMNI {
	public static final String EMPTYCOMBO = "---";

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
}
