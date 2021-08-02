package org.vibehistorian.vibecomposer.Popups;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public abstract class CloseablePopup {
	final JFrame frame = new JFrame();

	public CloseablePopup(String windowTitle) {
		Point loc = MouseInfo.getPointerInfo().getLocation();
		loc.translate(12, 12);
		frame.setLocation(loc);
		addFrameWindowOperation();
		frame.setTitle(windowTitle);
		frame.pack();

		if (VibeComposerGUI.currentPopup != null) {
			VibeComposerGUI.currentPopup.close();
			VibeComposerGUI.currentPopup = this;
		} else {
			VibeComposerGUI.currentPopup = this;
		}

		frame.setVisible(true);


	}

	public void close() {
		Toolkit.getDefaultToolkit().getSystemEventQueue()
				.postEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));

	}

	protected abstract void addFrameWindowOperation();

	public JFrame getFrame() {
		return frame;
	}
}

