package org.vibehistorian.vibecomposer.Popups;

import java.awt.Dimension;
import java.awt.Component;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.Point;

import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

public class ShowScorePopup extends CloseablePopup {
	private final JScrollPane scoreScrollPane;
	private final JTabbedPane instrumentTabPane;
	private final Runnable onClose;

	public ShowScorePopup(JScrollPane scoreScrollPane, JTabbedPane instrumentTabPane,
			Component parentComponent, boolean miniScore, Runnable onClose) {
		super("MIDI Score", 12, new Point(-400, -500), parentComponent);
		this.scoreScrollPane = scoreScrollPane;
		this.instrumentTabPane = instrumentTabPane;
		this.onClose = onClose;
		frame.add(scoreScrollPane);
		if (miniScore) {
			frame.setPreferredSize(new Dimension(650, 325));
			frame.setMaximumSize(new Dimension(650, 325));
			frame.setResizable(true);
		}
		frame.setAlwaysOnTop(true);
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
					frame.remove(scoreScrollPane);
					instrumentTabPane.add(scoreScrollPane, 7);
					instrumentTabPane.setTitleAt(7, " Score ");
					currentPopupMap.remove(12);
					onClose.run();
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
