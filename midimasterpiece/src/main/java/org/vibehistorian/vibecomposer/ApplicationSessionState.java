package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.generation.MidiTiming;

/** Holds application-level services that still require a shared lifetime. */
public final class ApplicationSessionState {
	private ApplicationSessionState() {
	}

	public static UndoManager actionUndoManager = new UndoManager();
	private static volatile MidiTiming activeMidiTiming = new MidiTiming(100);

	public static MidiTiming getActiveMidiTiming() {
		return activeMidiTiming;
	}

	public static void setActiveMidiTiming(MidiTiming timing) {
		activeMidiTiming = java.util.Objects.requireNonNull(timing, "timing");
	}
}
