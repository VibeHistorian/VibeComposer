package org.vibehistorian.vibecomposer;

/** Holds application-level services that still require a shared lifetime. */
public final class ApplicationSessionState {
	private ApplicationSessionState() {
	}

	public static UndoManager actionUndoManager = new UndoManager();
}
