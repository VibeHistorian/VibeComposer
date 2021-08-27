package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Color;

public class OMNI {
	public static final String EMPTYCOMBO = "---";

	public static Color alphen(Color c, int alphaValue) {
		Color newC = new Color(c.getRed(), c.getGreen(), c.getBlue(), alphaValue);
		return newC;
	}
}
