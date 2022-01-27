package org.vibehistorian.vibecomposer;

import java.awt.Adjustable;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.JScrollPane;

public class SwingUtils {

	public static double getScrolledPosition(JScrollPane pane, boolean horizontal) {
		//LG.i("Get scrl pos: " + pane.getHorizontalScrollBar().getVisibleAmount() / 2.0);
		if (horizontal) {
			return (pane.getHorizontalScrollBar().getValue())
					/ (double) pane.getHorizontalScrollBar().getMaximum();
		} else {
			return (pane.getVerticalScrollBar().getValue())
					/ (double) pane.getVerticalScrollBar().getMaximum();
		}
	}

	public static void setScrolledPosition(JScrollPane pane, boolean horizontal,
			double percentage) {
		//LG.i("Set scrl pos: " + pane.getHorizontalScrollBar().getMaximum() / 2.0);
		if (horizontal) {
			pane.getHorizontalScrollBar().setValue(
					Math.max(0, (int) (percentage * pane.getHorizontalScrollBar().getMaximum())));
		} else {
			pane.getVerticalScrollBar().setValue(
					Math.max(0, (int) (percentage * pane.getVerticalScrollBar().getMaximum())));
		}
	}

	public static void setupScrollpanePriorityScrolling(JScrollPane pane) {
		if (pane.getMouseListeners() != null) {
			for (MouseWheelListener mwl : pane.getMouseWheelListeners()) {
				pane.removeMouseWheelListener(mwl);
			}
		}

		pane.addMouseWheelListener(new MouseWheelListener() {

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {

				int scrollableVerticalGap = pane.getVerticalScrollBar().getMaximum()
						- pane.getVerticalScrollBar().getVisibleAmount();
				//LG.i("Scrollable gap: " + scrollableVerticalGap);
				if (scrollableVerticalGap < 15 || e.isShiftDown()) {
					// Horizontal scrolling
					Adjustable adj = pane.getHorizontalScrollBar();
					int scroll = e.getUnitsToScroll() * adj.getBlockIncrement() * 6;
					adj.setValue(adj.getValue() + scroll);
				} else {
					// Vertical scrolling
					Adjustable adj = pane.getVerticalScrollBar();
					int scroll = e.getUnitsToScroll() * adj.getBlockIncrement();
					adj.setValue(adj.getValue() + scroll);
				}
			}
		});
	}
}
