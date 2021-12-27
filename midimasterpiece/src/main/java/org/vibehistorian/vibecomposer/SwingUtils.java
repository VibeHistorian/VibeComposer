package org.vibehistorian.vibecomposer;

import javax.swing.JScrollPane;

public class SwingUtils {

	public static double getScrolledPosition(JScrollPane pane, boolean horizontal) {
		LG.i("Get scrl pos: " + pane.getHorizontalScrollBar().getVisibleAmount() / 2.0);
		if (horizontal) {
			return (pane.getHorizontalScrollBar().getValue()
					+ pane.getHorizontalScrollBar().getVisibleAmount() / 2.0)
					/ (double) pane.getHorizontalScrollBar().getMaximum();
		} else {
			return (pane.getVerticalScrollBar().getValue()
					+ pane.getVerticalScrollBar().getVisibleAmount() / 2.0)
					/ (double) pane.getVerticalScrollBar().getMaximum();
		}
	}

	public static void setScrolledPosition(JScrollPane pane, boolean horizontal,
			double percentage) {
		LG.i("Set scrl pos: " + pane.getHorizontalScrollBar().getMaximum() / 2.0);
		if (horizontal) {
			pane.getHorizontalScrollBar().setValue(
					Math.max(0, (int) (percentage * pane.getHorizontalScrollBar().getMaximum()
							- pane.getHorizontalScrollBar().getVisibleAmount() / 2.0)));
		} else {
			pane.getVerticalScrollBar().setValue(
					Math.max(0, (int) (percentage * pane.getVerticalScrollBar().getMaximum()
							- pane.getVerticalScrollBar().getVisibleAmount() / 2.0)));
		}
	}
}
