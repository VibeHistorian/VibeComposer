package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.Components.ScrollComboBox;

/** Owns active configuration and application-level services. */
public final class ApplicationSessionState {
	private ApplicationSessionState() {
	}

	public static GUIConfig guiConfig = new GUIConfig();
	public static ScrollComboBox<GUIConfig> configHistory = new ScrollComboBox<>(false);
	public static boolean heavyBackgroundTasksInProgress;

	public static UndoManager actionUndoManager = new UndoManager();
	public static UndoManager instrumentTabUndoManager = new UndoManager();
}
