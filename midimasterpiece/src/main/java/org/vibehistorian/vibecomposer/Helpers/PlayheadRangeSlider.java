package org.vibehistorian.vibecomposer.Helpers;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class PlayheadRangeSlider extends RangeSlider {

	private static final long serialVersionUID = -8846762395904588112L;

	@Override
	public void setUpperDragging(boolean upperDragging) {
		super.setUpperDragging(upperDragging);
		VibeComposerGUI.isDragging = upperDragging;
		VibeComposerGUI.scorePanel.update();
	}

	@Override
	public void setUpperValue(int value) {
		super.setUpperValue(value);
		VibeComposerGUI.scorePanel.update();
	}
}
