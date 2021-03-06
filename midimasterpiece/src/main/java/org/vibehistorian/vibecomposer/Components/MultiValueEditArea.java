package org.vibehistorian.vibecomposer.Components;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Popups.VisualArrayPopup;

public class MultiValueEditArea extends JComponent {

	private static final long serialVersionUID = -2972572935738976623L;
	int min = -10;
	int max = 10;
	List<Integer> values = null;
	Map<Integer, Set<Integer>> highlightedGrid = null;

	int marginX = 40;
	int marginY = 40;

	int markWidth = 6;
	int numHeight = 6;
	int numWidth = 4;

	boolean isDragging = false;

	VisualArrayPopup pop = null;

	public MultiValueEditArea(int min, int max, List<Integer> values) {
		super();
		this.min = min;
		this.max = max;
		this.values = values;
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				if (!isEnabled()) {
					return;
				}
				if (SwingUtilities.isLeftMouseButton(evt)) {
					Point orderVal = getOrderAndValueFromPosition(evt.getPoint());
					setVal(orderVal.x, orderVal.y);
					isDragging = true;
					repaint();
				} else if (SwingUtilities.isRightMouseButton(evt)) {
					Point orderVal = getOrderAndValueFromPosition(evt.getPoint());
					setVal(orderVal.x, 0);
					repaint();
				}
			}

			@Override
			public void mouseReleased(MouseEvent evt) {
				isDragging = false;
			}
		});

		addMouseMotionListener(new MouseMotionListener() {

			@Override
			public void mouseDragged(MouseEvent e) {
				if (isDragging) {
					Point orderVal = getOrderAndValueFromPosition(e.getPoint());
					setVal(orderVal.x, orderVal.y);
					repaint();
				}
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				if (isDragging) {
					Point orderVal = getOrderAndValueFromPosition(e.getPoint());
					setVal(orderVal.x, orderVal.y);
					repaint();
				}
			}

		});
	}

	void setVal(int pos, int val) {
		values.set(pos, val);
		if (pop != null) {
			pop.getText().setText(StringUtils.join(values, ","));
		}
	}

	@Override
	public void paintComponent(Graphics guh) {
		if (guh instanceof Graphics2D) {
			Graphics2D g = (Graphics2D) guh;
			int w = getWidth();
			int h = getHeight();
			// clear screen
			g.setColor(VibeComposerGUI.isDarkMode ? VibeComposerGUI.panelColorHigh
					: VibeComposerGUI.panelColorLow.darker());
			g.fillRect(0, 0, w, h);
			int numSize = values.size();
			if (numSize == 0) {
				return;
			}
			int rowDivisors = max - min;
			int usableHeight = h - marginY * 2;
			double rowHeight = usableHeight / (double) rowDivisors;

			Point bottomLeft = new Point(marginX, usableHeight + marginY);
			double colWidth = (w - marginX * 2) / numSize;

			// draw graph lines - first to last value X, min to max value Y
			g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(), 80));

			g.drawLine(bottomLeft.x, bottomLeft.y, bottomLeft.x, 0);
			g.drawLine(bottomLeft.x, bottomLeft.y, w, bottomLeft.y);

			// draw numbers left of Y line
			// draw line marks

			for (int i = 0; i < 1 + (max - min); i++) {

				String drawnValue = "" + (min + i);
				int valueLength = drawnValue.startsWith("-") ? drawnValue.length() + 1
						: drawnValue.length();
				int drawValueX = bottomLeft.x / 2 - (numWidth * valueLength) / 2;
				int drawMarkX = bottomLeft.x - markWidth / 2;
				int drawY = bottomLeft.y - (int) (rowHeight * (i + 1));

				g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(), 40));
				g.drawLine(bottomLeft.x, drawY, w, drawY);

				g.setColor(VibeComposerGUI.uiColor());
				g.drawString(drawnValue, drawValueX, drawY + numHeight / 2);
				g.drawLine(drawMarkX, drawY, drawMarkX + markWidth, drawY);


			}

			// draw numbers below X line
			// draw line marks
			for (int i = 0; i < numSize; i++) {
				String drawnValue = "" + (i + 1);
				int valueLength = drawnValue.startsWith("-") ? drawnValue.length() + 1
						: drawnValue.length();
				int drawValueY = numHeight + (bottomLeft.y + h) / 2;
				int drawMarkY = (bottomLeft.y - markWidth / 2);
				int drawX = bottomLeft.x + (int) (colWidth * (i + 1));

				g.drawString(drawnValue, drawX - (numWidth * valueLength) / 2, drawValueY);
				g.drawLine(drawX, drawMarkY, drawX, drawMarkY + markWidth);


			}
			Color dotColor = OMNI.alphen(VibeComposerGUI.uiColor(), 80);
			Color highlightedDotColor = new Color(220, 30, 50);
			g.setColor(dotColor);

			// draw line helpers/dots
			for (int i = 0; i < numSize; i++) {
				int drawX = bottomLeft.x + (int) (colWidth * (i + 1));
				Set<Integer> helpers = highlightedGrid != null ? highlightedGrid.get(i) : null;
				for (int j = 0; j < 1 + max - min; j++) {
					boolean highlighted = helpers != null && helpers.contains((j + min + 700) % 7);
					int drawDotY = bottomLeft.y - (int) (rowHeight * (j + 1));
					if (highlighted) {
						g.setColor(highlightedDotColor);
						g.drawLine(drawX - 1, drawDotY - 2, drawX + 1, drawDotY + 2);
						g.drawLine(drawX + 1, drawDotY - 2, drawX - 1, drawDotY + 2);
					} else {
						g.setColor(dotColor);
						g.drawLine(drawX, drawDotY - 2, drawX, drawDotY + 2);
					}
				}
			}

			// draw actual values

			int ovalWidth = w / 40;
			for (int i = 0; i < numSize; i++) {
				int drawX = bottomLeft.x + (int) (colWidth * (i + 1));
				int drawY = bottomLeft.y - (int) (rowHeight * (values.get(i) + 1 - min));

				if (i < numSize - 1) {
					g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(), 50));
					g.drawLine(drawX, drawY, drawX + (int) colWidth,
							bottomLeft.y - (int) (rowHeight * (values.get(i + 1) + 1 - min)));
				}


				g.setColor(VibeComposerGUI.uiColor());
				g.drawOval(drawX - ovalWidth / 2, drawY - ovalWidth / 2, ovalWidth, ovalWidth);

				g.drawString("" + values.get(i), drawX + ovalWidth / 2, drawY - ovalWidth / 2);
			}
		}
	}


	public List<Integer> getValues() {
		return values;
	}


	public Point getOrderAndValueFromPosition(Point xy) {
		int numSize = values.size();
		if (numSize == 0) {
			return null;
		}
		int w = getWidth();
		int h = getHeight();
		int rowDivisors = max - min;
		int usableHeight = h - marginY * 2;
		double rowHeight = usableHeight / (double) rowDivisors;

		Point bottomLeftAdjusted = new Point(marginX,
				usableHeight + marginY - (int) (rowHeight / 2));

		int yValue = (int) ((bottomLeftAdjusted.y - xy.y) / rowHeight) + min;
		yValue = OMNI.clamp(yValue, min, max);


		if (numSize == 1) {
			return new Point(0, yValue);
		}

		double colWidth = (w - bottomLeftAdjusted.x * 2) / (double) numSize;

		int searchX = (int) ((xy.x - bottomLeftAdjusted.x - colWidth / 2) / colWidth);

		int xValue = OMNI.clamp(searchX, 0, numSize - 1);

		Point orderValue = new Point(xValue, yValue);
		//LG.d("Incoming point: " + xy.toString());
		//LG.d("Order Value: " + orderValue.toString());

		return orderValue;
	}

	public Map<Integer, Set<Integer>> getHighlightedGrid() {
		return highlightedGrid;
	}

	public void setHighlightedGrid(Map<Integer, Set<Integer>> highlightedGrid) {
		this.highlightedGrid = highlightedGrid;
	}

	public VisualArrayPopup getPop() {
		return pop;
	}

	public void setPop(VisualArrayPopup pop) {
		this.pop = pop;
	}


}
