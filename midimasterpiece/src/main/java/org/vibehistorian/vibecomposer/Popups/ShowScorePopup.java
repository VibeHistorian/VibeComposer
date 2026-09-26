package org.vibehistorian.vibecomposer.Popups;

import org.vibehistorian.vibecomposer.UITheme;

import org.vibehistorian.vibecomposer.ScoreGUI;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Component;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.JTabbedPane;

import org.vibehistorian.vibecomposer.Components.ShowPanelBig;

public class ShowScorePopup extends CloseablePopup {
	private final JTabbedPane instrumentTabPane;

	public ShowScorePopup(JScrollPane scoreScrollPane, JTabbedPane instrumentTabPane,
			Component parentComponent) {
		super("MIDI Score", 12, new Point(-400, -500), parentComponent);
		this.instrumentTabPane = instrumentTabPane;
		frame.add(scoreScrollPane);
		if (ScoreGUI.miniScorePopup.isSelected()) {
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
					frame.remove(ScoreGUI.scoreScrollPane);
					instrumentTabPane.add(ScoreGUI.scoreScrollPane, 7);
					instrumentTabPane.setTitleAt(7, " Score ");
					currentPopupMap.remove(12);
					//if (ScoreGUI.miniScorePopup.isSelected()) {
					ShowPanelBig.beatWidthBases = ShowPanelBig.beatWidthBasesBig;
					ShowPanelBig.beatWidthBase = ShowPanelBig.beatWidthBases
							.get(ShowPanelBig.beatWidthBaseIndex);
					ScoreGUI.scorePanel
							.updatePanelHeight(UITheme.scrollPaneDimension.height);
					ScoreGUI.scorePanel.getShowArea().setNoteHeight(7);
					ScoreGUI.scorePanel.setScore();
					ScoreGUI.scoreScrollPane.repaint();
					ScoreGUI.scorePopup = null;
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
