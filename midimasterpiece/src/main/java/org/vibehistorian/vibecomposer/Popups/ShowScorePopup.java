package org.vibehistorian.vibecomposer.Popups;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Components.ShowPanelBig;

public class ShowScorePopup extends CloseablePopup {

	public ShowScorePopup(JScrollPane scoreScrollPane) {
		super("MIDI Score", 12, new Point(-400, -500), VibeComposerGUI.vibeComposerGUI);
		frame.add(scoreScrollPane);
		if (VibeComposerGUI.miniScorePopup.isSelected()) {
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
					frame.remove(VibeComposerGUI.scoreScrollPane);
					VibeComposerGUI.instrumentTabPane.add(VibeComposerGUI.scoreScrollPane, 7);
					VibeComposerGUI.instrumentTabPane.setTitleAt(7, " Score ");
					currentPopupMap.remove(12);
					//if (VibeComposerGUI.miniScorePopup.isSelected()) {
					ShowPanelBig.beatWidthBases = ShowPanelBig.beatWidthBasesBig;
					ShowPanelBig.beatWidthBase = ShowPanelBig.beatWidthBases
							.get(ShowPanelBig.beatWidthBaseIndex);
					VibeComposerGUI.scorePanel
							.updatePanelHeight(VibeComposerGUI.scrollPaneDimension.height);
					VibeComposerGUI.scorePanel.getShowArea().setNoteHeight(7);
					VibeComposerGUI.scorePanel.setScore();
					VibeComposerGUI.scoreScrollPane.repaint();
					VibeComposerGUI.scorePopup = null;
					SwingUtilities.invokeLater(() -> {
						ShowPanelBig.zoomIn(ShowPanelBig.areaScrollPane, new Point(0, 300), 0.0,
								(7 / 5.0) - 1.0);
					});
					//}
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
