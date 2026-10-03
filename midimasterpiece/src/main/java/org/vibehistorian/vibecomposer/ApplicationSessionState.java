package org.vibehistorian.vibecomposer;

/** Owns active configuration and application-level services. */
public final class ApplicationSessionState {
	private ApplicationSessionState() {
	}

	public static GUIConfig guiConfig = new GUIConfig();
	public static UndoManager actionUndoManager = new UndoManager();
}
