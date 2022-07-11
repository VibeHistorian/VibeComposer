package org.vibehistorian.vibecomposer.Popups;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JScrollPane;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class ShowScorePopup extends CloseablePopup {

	public ShowScorePopup(JScrollPane scoreScrollPane) {
		super("MIDI Score", 12);
		frame.add(scoreScrollPane);
		frame.setResizable(false);
		frame.pack();
		frame.setVisible(true);
	}

	@Override
	protected void addFrameWindowOperation() {
		frame.addWindowListener(new WindowListener() {

			@Override
			public void windowOpened(WindowEvent e) {
			}

			@Override
			public void windowClosing(WindowEvent e) {
				if (frame.isVisible()) {
					frame.remove(VibeComposerGUI.scoreScrollPane);
					currentPopupMap.remove(12);
					VibeComposerGUI.instrumentTabPane.add(VibeComposerGUI.scoreScrollPane, 7);
					VibeComposerGUI.instrumentTabPane.setTitleAt(7, " Score ");
					VibeComposerGUI.scorePopup = null;
					frame.dispose();
				}
			}

			@Override
			public void windowClosed(WindowEvent e) {
			}

			@Override
			public void windowIconified(WindowEvent e) {
			}

			@Override
			public void windowDeiconified(WindowEvent e) {
			}

			@Override
			public void windowActivated(WindowEvent e) {
			}

			@Override
			public void windowDeactivated(WindowEvent e) {
			}
		});
	}
}
