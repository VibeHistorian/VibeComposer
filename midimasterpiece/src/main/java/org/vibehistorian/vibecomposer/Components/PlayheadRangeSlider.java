package org.vibehistorian.vibecomposer.Components;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class PlayheadRangeSlider extends RangeSlider {

	private static final long serialVersionUID = -8846762395904588112L;

	@Override
	public void setUpperDragging(boolean upperDragging) {
		super.setUpperDragging(upperDragging);
		VibeComposerGUI.isDragging = upperDragging;
		if (VibeComposerGUI.instrumentTabPane.getTabCount() < 8
				|| VibeComposerGUI.instrumentTabPane.getSelectedIndex() == 7) {
			VibeComposerGUI.scorePanel.update();
		}

	}

	public void setUpperValueRaw(int value) {
		super.setUpperValue(value);
	}

	@Override
	public void setUpperValue(int value) {
		super.setUpperValue(value);
		if ((VibeComposerGUI.instrumentTabPane.getTabCount() < 8
				|| VibeComposerGUI.instrumentTabPane.getSelectedIndex() == 7)
				&& VibeComposerGUI.highlightScoreNotes.isSelected()) {
			VibeComposerGUI.scorePanel.update();
		}
	}
}
