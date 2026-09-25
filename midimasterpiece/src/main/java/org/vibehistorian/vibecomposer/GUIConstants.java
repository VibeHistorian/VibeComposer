package org.vibehistorian.vibecomposer;

import java.awt.Color;

/** Immutable presentation values shared by the GUI. */
public final class GUIConstants {
	private GUIConstants() {
	}

	public static final Color COMPOSE_COLOR = new Color(180, 150, 90);
	public static final Color COMPOSE_COLOR_TEXT = new Color(220, 170, 60);
	public static final Color COMPOSE_COLOR_TEXT_LIGHT = new Color(255, 193, 85);
	public static final Color REGENERATE_COLOR_TEXT = new Color(220, 70, 60);
	public static final Color REGENERATE_COLOR_TEXT_LIGHT = new Color(150, 0, 0);

	public static final int DEFAULT_WIDTH = 1600;
	public static final int DEFAULT_HEIGHT = 400;
	public static final int TABLE_COLUMN_MIN_WIDTH = 80;
}
