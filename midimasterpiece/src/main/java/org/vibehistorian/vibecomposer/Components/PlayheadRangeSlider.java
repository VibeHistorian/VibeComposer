package org.vibehistorian.vibecomposer.Components;

import org.vibehistorian.vibecomposer.PlaybackState;

import org.vibehistorian.vibecomposer.ScoreGUI;

import javax.swing.JTabbedPane;

public class PlayheadRangeSlider extends RangeSlider {

	private static final long serialVersionUID = -8846762395904588112L;
	private final JTabbedPane instrumentTabPane;

	public PlayheadRangeSlider(JTabbedPane instrumentTabPane) {
		this.instrumentTabPane = instrumentTabPane;
	}

	@Override
	public void setUpperDragging(boolean upperDragging) {
		super.setUpperDragging(upperDragging);
		PlaybackState.isDragging = upperDragging;
		if (instrumentTabPane.getTabCount() < 8
				|| instrumentTabPane.getSelectedIndex() == 7) {
			if (ScoreGUI.scorePanel != null) {
				ScoreGUI.scorePanel.repaintMinimum();
			}
		}

	}

	public void setUpperValueRaw(int value) {
		super.setUpperValue(value);
	}

	@Override
	public void setUpperValue(int value) {
		super.setUpperValue(value);
		if ((instrumentTabPane.getTabCount() < 8
				|| instrumentTabPane.getSelectedIndex() == 7)
				&& ScoreGUI.highlightScoreNotes.isSelected()) {
			ScoreGUI.scorePanel.repaintMinimum();
		}
	}
}
