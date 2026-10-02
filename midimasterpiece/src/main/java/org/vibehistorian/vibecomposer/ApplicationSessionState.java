package org.vibehistorian.vibecomposer;

import java.io.OutputStream;
import java.io.PrintStream;

import javax.sound.midi.Soundbank;
import org.vibehistorian.vibecomposer.Popups.DebugConsole;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;

/** Owns active configuration and application-level services. */
public final class ApplicationSessionState {
	private ApplicationSessionState() {
	}

	public static GUIPreset defaultGuiPreset;
	static Soundbank soundfont;
	public static MidiGenerator melodyGen;
	public static GUIConfig guiConfig = new GUIConfig();
	public static ScrollComboBox<GUIConfig> configHistory = new ScrollComboBox<>(false);
	public static boolean heavyBackgroundTasksInProgress;

	static final PrintStream originalOut = System.out;
	static final PrintStream originalErr = System.err;
	static final PrintStream dummyOut = new PrintStream(new OutputStream() {
		@Override
		public void write(int b) {
			// NO-OP
		}
	});

	public static UndoManager actionUndoManager = new UndoManager();
	public static UndoManager instrumentTabUndoManager = new UndoManager();
	public static DebugConsole dconsole;
}
