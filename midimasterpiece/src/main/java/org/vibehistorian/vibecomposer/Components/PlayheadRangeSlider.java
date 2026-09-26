package org.vibehistorian.vibecomposer.Components;

import org.vibehistorian.vibecomposer.PlaybackState;

import javax.swing.JTabbedPane;
import java.util.function.BooleanSupplier;

public class PlayheadRangeSlider extends RangeSlider {

	private static final long serialVersionUID = -8846762395904588112L;
	private final JTabbedPane instrumentTabPane;
	private final Runnable repaintScore;
	private final BooleanSupplier highlightScoreNotes;

	public PlayheadRangeSlider(JTabbedPane instrumentTabPane, Runnable repaintScore,
			BooleanSupplier highlightScoreNotes) {
		this.instrumentTabPane = instrumentTabPane;
		this.repaintScore = repaintScore;
		this.highlightScoreNotes = highlightScoreNotes;
	}

	@Override
	public void setUpperDragging(boolean upperDragging) {
		super.setUpperDragging(upperDragging);
		PlaybackState.isDragging = upperDragging;
		if (instrumentTabPane.getTabCount() < 8
				|| instrumentTabPane.getSelectedIndex() == 7) {
			repaintScore.run();
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
				&& highlightScoreNotes.getAsBoolean()) {
			repaintScore.run();
		}
	}
}
