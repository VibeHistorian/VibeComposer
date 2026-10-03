package org.vibehistorian.vibecomposer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.HashSet;
import java.util.Set;

/** Owns mutable application-wide theme and display preferences. */
public final class UITheme {
	private UITheme() {
	}

	public static Color panelColorHigh;
	public static Color panelColorLow;
	public static boolean isBigMonitorMode = false;
	public static boolean isDarkMode = true;
	public static boolean isFullMode = true;
	public static Color darkModeUIColor = Color.CYAN;
	public static Color lightModeUIColor = new Color(0, 90, 255);
	public static Color toggledUIColor = Color.cyan;
	public static Color toggledComposeColor = GUIConstants.COMPOSE_COLOR_TEXT;
	public static Color toggledRegenerateColor = GUIConstants.REGENERATE_COLOR_TEXT;
	public static Dimension scrollPaneDimension = new Dimension(GUIConstants.DEFAULT_WIDTH,
			GUIConstants.DEFAULT_HEIGHT);
	public static final Set<Component> toggleableComponents = new HashSet<>();

	public static Color uiColor() {
		return isDarkMode ? darkModeUIColor : lightModeUIColor;
	}

	public static Color uiComposeTextColor() {
		return isDarkMode ? GUIConstants.COMPOSE_COLOR_TEXT : GUIConstants.COMPOSE_COLOR_TEXT_LIGHT;
	}

	public static Color uiRegenerateTextColor() {
		return isDarkMode ? GUIConstants.REGENERATE_COLOR_TEXT
				: GUIConstants.REGENERATE_COLOR_TEXT_LIGHT;
	}
}
