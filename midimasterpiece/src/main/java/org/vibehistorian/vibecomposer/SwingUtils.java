package org.vibehistorian.vibecomposer;

import java.awt.Adjustable;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.Timer;

public class SwingUtils {

	public static List<JPopupMenu> popupMenus = new ArrayList<>();

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

	public static int getDrawStringWidth(String text) {
		AffineTransform affinetransform = new AffineTransform();
		FontRenderContext frc = new FontRenderContext(affinetransform, true, true);
		Font font = new Font("Tahoma", Font.PLAIN, 12);
		int textwidth = (int) (font.getStringBounds(text, frc).getWidth());
		//int textheight = (int)(font.getStringBounds(text, frc).getHeight());
		return textwidth;
	}

	public static void flashComponent(final JComponent field, Color flashColor,
			final int timerDelay, int totalTime) {
		final int totalCount = totalTime / timerDelay;
		javax.swing.Timer timer = new javax.swing.Timer(timerDelay, new ActionListener() {
			int count = 0;

			public void actionPerformed(ActionEvent evt) {
				if (count % 2 == 0) {
					field.setBackground(flashColor);
				} else {
					field.setBackground(null);
					if (count >= totalCount) {
						((Timer) evt.getSource()).stop();
					}
				}
				count++;
			}
		});
		timer.start();
	}

	public static void addPopupMenu(JComponent comp, BiConsumer<ActionEvent, String> actionOnSelect,
			Function<MouseEvent, Boolean> actionOnMousePress, List<String> displayedPopupItems,
			List<Color> popupItemColors) {
		final JPopupMenu popup = new JPopupMenu();
		popupMenus.add(popup);
		for (int i = 0; i < displayedPopupItems.size(); i++) {
			String e = displayedPopupItems.get(i);
			JMenuItem newE = new JMenuItem(e);
			newE.setOpaque(false);
			if (popupItemColors != null) {
				newE.setForeground(popupItemColors.get(i));
			}
			newE.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent evt) {
					actionOnSelect.accept(evt, e);
				}
			});
			popup.add(newE);
		}

		comp.addMouseListener(new MouseAdapter() {

			@Override
			public void mousePressed(MouseEvent evt) {
				Boolean goodPress = actionOnMousePress.apply(evt);
				if (goodPress != null && goodPress) {
					popup.show(evt.getComponent(), evt.getX(), evt.getY());
				}
			}

		});
	}
}
