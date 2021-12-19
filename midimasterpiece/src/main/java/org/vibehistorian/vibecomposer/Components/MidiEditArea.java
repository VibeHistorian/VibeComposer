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

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Popups.MidiEditPopup;

public class MidiEditArea extends JComponent {

	private static final long serialVersionUID = -2972572935738976623L;
	int min = -10;
	int max = 10;
	PhraseNotes values = null;
	Map<Integer, List<Integer>> highlightedGrid = null;

	int colStart = 0;
	int rowStart = 0;
	int marginX = 80;
	int marginY = 40;

	int markWidth = 6;
	int numHeight = 6;
	int numWidth = 4;

	boolean isDragging = false;

	MidiEditPopup pop = null;

	public MidiEditArea(int min, int max, PhraseNotes values) {
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
		values.get(pos).setPitch(val);
	}

	@Override
	public void paintComponent(Graphics guh) {
		if (guh instanceof Graphics2D) {
			Graphics2D g = (Graphics2D) guh;
			int w = getWidth();
			int h = getHeight();
			int numValues = values.size();
			int rowDivisors = max - min;
			int usableHeight = h - marginY * 2;
			double rowHeight = usableHeight / (double) rowDivisors;
			// clear screen
			g.setColor(VibeComposerGUI.isDarkMode ? VibeComposerGUI.panelColorHigh
					: VibeComposerGUI.panelColorLow.darker());
			g.fillRect(0, 0, w, h);

			// draw graph lines - first to last value X, min to max value Y
			g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(), 80));

			Point bottomLeft = new Point(marginX, usableHeight + marginY);
			g.drawLine(bottomLeft.x, bottomLeft.y, bottomLeft.x, 0);
			g.drawLine(bottomLeft.x, bottomLeft.y, w, bottomLeft.y);


			double sectionLength = values.stream().map(e -> e.getRv()).mapToDouble(e -> e).sum();
			double quarterNoteLength = (w - bottomLeft.x) / sectionLength;
			List<Double> starts = values.getNoteStartTimes();

			// draw numbers left of Y line
			// draw line marks

			int partNum = (pop != null && pop.getParent() != null) ? pop.getParent().getPartNum()
					: 0;

			for (int i = 0; i < 1 + (max - min); i++) {

				String drawnValue = "" + (min + i) + " | "
						+ MidiUtils.SEMITONE_LETTERS.get((min + i) % 12) + ((min + i) / 12 - 1);
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
			for (int i = 0; i < numValues; i++) {
				String drawnValue = "" + (i + 1);
				int valueLength = drawnValue.startsWith("-") ? drawnValue.length() + 1
						: drawnValue.length();
				int drawValueY = numHeight + (bottomLeft.y + h) / 2;
				int drawMarkY = (bottomLeft.y - markWidth / 2);
				int drawX = bottomLeft.x + (int) (quarterNoteLength * starts.get(i));

				g.drawString(drawnValue, drawX - (numWidth * valueLength) / 2, drawValueY);
				g.drawLine(drawX, drawMarkY, drawX, drawMarkY + markWidth);


			}
			Color dotColor = OMNI.alphen(VibeComposerGUI.uiColor(), 80);
			Color highlightedDotColor = new Color(220, 30, 50);
			g.setColor(dotColor);

			// draw line helpers/dots
			for (int i = 0; i < numValues; i++) {
				int drawX = bottomLeft.x + (int) (quarterNoteLength * starts.get(i));
				List<Integer> helpers = highlightedGrid != null ? highlightedGrid.get(i) : null;
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

			int ovalWidth = usableHeight / 40;
			for (int i = 0; i < numValues; i++) {
				int pitch = values.get(i).getPitch();
				if (pitch < 0) {
					continue;
				}
				int drawX = bottomLeft.x + (int) (quarterNoteLength * starts.get(i)
						+ quarterNoteLength * values.get(i).getOffset());
				int drawY = bottomLeft.y - (int) (rowHeight * (pitch + 1 - min));

				// draw straight line connecting values
				if (i < numValues - 1) {
					int nextPitch = values.get(i + 1).getPitch();
					if (nextPitch >= 0) {
						g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(), 50));
						g.drawLine(drawX, drawY,
								drawX + (int) (quarterNoteLength
										* (values.get(i).getRv() + values.get(i).getOffset())),
								bottomLeft.y - (int) (rowHeight
										* (values.get(i + 1).getPitch() + 1 - min)));
					}
				}


				g.setColor(VibeComposerGUI.uiColor());
				g.drawOval(drawX - ovalWidth / 2, drawY - ovalWidth / 2, ovalWidth, ovalWidth);

				g.drawString("" + pitch, drawX + ovalWidth / 2, drawY - ovalWidth / 2);
				g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(), 140));
				g.fillRect(drawX, drawY - 4,
						(int) (quarterNoteLength * values.get(i).getDuration()), 8);


			}
		}
	}


	public PhraseNotes getValues() {
		return values;
	}


	public Point getOrderAndValueFromPosition(Point xy) {
		int w = getWidth();
		int h = getHeight();
		int rowDivisors = max - min;
		int usableHeight = h - marginY * 2;
		double rowHeight = usableHeight / (double) rowDivisors;

		Point bottomLeftAdjusted = new Point(marginX,
				usableHeight + marginY - (int) (rowHeight / 2));

		List<Double> starts = values.getNoteStartTimes();
		double sectionLength = values.stream().map(e -> e.getRv()).mapToDouble(e -> e).sum();
		double quarterNoteLength = (w - bottomLeftAdjusted.x) / sectionLength;


		int searchX = xy.x - bottomLeftAdjusted.x;
		//LG.d(searchX);
		int foundX = 0;
		for (int i = 0; i < starts.size(); i++) {
			if (starts.get(i) * quarterNoteLength > searchX) {
				foundX = i - 1;
				break;
			} else if (i == starts.size() - 1) {
				foundX = i;
			}
		}

		int xValue = foundX;
		int yValue = (int) ((bottomLeftAdjusted.y - xy.y) / rowHeight) + min;

		xValue = OMNI.clamp(xValue, 0, values.size() - 1);
		yValue = OMNI.clamp(yValue, min, max);

		Point orderValue = new Point(xValue, yValue);
		//LG.d("Incoming point: " + xy.toString());
		//LG.d("Order Value: " + orderValue.toString());

		return orderValue;
	}

	public Map<Integer, List<Integer>> getHighlightedGrid() {
		return highlightedGrid;
	}

	public void setHighlightedGrid(Map<Integer, List<Integer>> highlightedGrid) {
		this.highlightedGrid = highlightedGrid;
	}

	public MidiEditPopup getPop() {
		return pop;
	}

	public void setPop(MidiEditPopup pop) {
		this.pop = pop;
	}

}
