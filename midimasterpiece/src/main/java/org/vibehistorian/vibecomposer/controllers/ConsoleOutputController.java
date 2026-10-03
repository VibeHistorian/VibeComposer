package org.vibehistorian.vibecomposer.controllers;

import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.Popups.DebugConsole;

import java.io.OutputStream;
import java.io.PrintStream;

/** Owns process output redirection and the optional debug console for the active window. */
public final class ConsoleOutputController {
	private static final ConsoleOutputController NO_OP = new ConsoleOutputController(false);

	private final boolean enabled;
	private final PrintStream originalOut;
	private final PrintStream suppressedOutput;
	private DebugConsole debugConsole;

	public ConsoleOutputController() {
		this(true);
	}

	private ConsoleOutputController(boolean enabled) {
		this.enabled = enabled;
		if (enabled) {
			originalOut = System.out;
			suppressedOutput = new PrintStream(new OutputStream() {
				@Override
				public void write(int value) {
					// Discard generated MIDI library output.
				}
			});
			System.setErr(suppressedOutput);
		} else {
			originalOut = null;
			suppressedOutput = null;
		}
	}

	public static ConsoleOutputController noOp() {
		return NO_OP;
	}

	public void suppressStandardOutput() {
		if (enabled) {
			System.setOut(suppressedOutput);
		}
	}

	public void restoreAfterMidiWrite() {
		if (!enabled) {
			return;
		}
		if (debugConsole == null || !debugConsole.getFrame().isVisible()) {
			System.setOut(originalOut);
			System.setErr(suppressedOutput);
		} else {
			redirectErrorsToDebugConsole();
		}
	}

	public void openDebugConsole() throws Exception {
		if (enabled) {
			debugConsole = new DebugConsole();
			redirectErrorsToDebugConsole();
			LG.d("Started debug console..");
		}
	}

	private void redirectErrorsToDebugConsole() {
		System.setErr(debugConsole.createOutputStream());
	}
}
