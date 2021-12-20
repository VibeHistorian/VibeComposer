package org.vibehistorian.vibecomposer.Components;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.PhraseNote;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Popups.MidiEditPopup;

import jm.music.data.Note;

public class MidiEditArea extends JComponent {

	private static final long serialVersionUID = -2972572935738976623L;
	public int min = -10;
	public int max = 10;
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
	PhraseNote draggedNote = null;
	boolean draggingPosition = false;
	boolean draggingDuration = false;
	double startingOffset = 0.0;
	double startingDuration = 0.0;
	Integer dragX = null;

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
					if (evt.isShiftDown()) {
						Point orderVal = getOrderAndValueFromPosition(evt.getPoint());
						if (orderVal != null) {
							setVal(orderVal.x, orderVal.y);
							isDragging = true;
							repaint();
						}
					} else {
						Point orderVal = getOrderAndValueFromPosition(evt.getPoint());
						if (orderVal == null || values.get(orderVal.x).getPitch() != orderVal.y) {
							// mouse click doesn't overlap with existing note
							// get order in note rhythm 
							orderVal = getOrderAndValueFromPosition(evt.getPoint(), false, true);
							if (orderVal != null) {
								PhraseNote pn = values.get(orderVal.x);
								PhraseNote insertedPn = new PhraseNote(orderVal.y);
								insertedPn.setDuration(pn.getDuration());
								insertedPn.setRv(0);
								values.add(orderVal.x, insertedPn);
								draggedNote = insertedPn;
								startingDuration = draggedNote.getDuration();
								isDragging = true;
								draggingDuration = true;
								dragX = evt.getPoint().x;
								repaint();
							}
						} else {
							draggedNote = getDraggedNote(evt.getPoint());
							startingDuration = draggedNote.getDuration();
							isDragging = true;
							draggingDuration = true;
							dragX = evt.getPoint().x;
						}
					}

				} else if (SwingUtilities.isRightMouseButton(evt)) {
					Point orderVal = getOrderAndValueFromPosition(evt.getPoint());
					setVal(orderVal.x, Note.REST);
					repaint();
				} else if (SwingUtilities.isMiddleMouseButton(evt)) {
					draggedNote = getDraggedNote(evt.getPoint());
					if (draggedNote != null) {
						startingOffset = draggedNote.getOffset();
						isDragging = true;
						draggingPosition = true;

						dragX = evt.getPoint().x;
					}
				}
			}

			@Override
			public void mouseReleased(MouseEvent evt) {
				isDragging = false;
				draggedNote = null;
				draggingPosition = false;
				draggingDuration = false;
				startingOffset = 0;
			}
		});

		addMouseMotionListener(new MouseMotionListener() {

			@Override
			public void mouseDragged(MouseEvent e) {
				processDragEvent(e);
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				processDragEvent(e);
			}

		});
	}

	protected void processDragEvent(MouseEvent e) {
		if (isDragging) {
			if (draggedNote == null) {
				Point orderVal = getOrderAndValueFromPosition(e.getPoint());
				if (orderVal != null && values.get(orderVal.x).getPitch() >= 0) {
					setVal(orderVal.x, orderVal.y);
					repaint();
				}

			} else {
				if (draggingPosition) {
					double offset = getOffsetFromPosition(e.getPoint());
					draggedNote.setOffset(offset);
					repaint();
				} else if (draggingDuration) {
					double duration = getDurationFromPosition(e.getPoint());
					duration = Math.max(MidiGenerator.Durations.SIXTEENTH_NOTE / 2, duration);
					draggedNote.setDuration(duration);
					repaint();
				}
			}
		}
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
			values.remakeNoteStartTimes(false);

			// draw numbers left of Y line
			// draw line marks

			int partNum = (pop.getParent() != null) ? pop.getParent().getPartNum() : 0;

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
				int drawX = bottomLeft.x + (int) (quarterNoteLength * values.get(i).getStartTime());

				g.drawString(drawnValue, drawX - (numWidth * valueLength) / 2, drawValueY);
				g.drawLine(drawX, drawMarkY, drawX, drawMarkY + markWidth);


			}
			Color dotColor = OMNI.alphen(VibeComposerGUI.uiColor(), 80);
			Color highlightedDotColor = new Color(220, 30, 50);
			g.setColor(dotColor);

			// draw line helpers/dots
			for (int i = 0; i < numValues; i++) {
				int drawX = bottomLeft.x + (int) (quarterNoteLength * values.get(i).getStartTime());
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

			// draw chord spacing
			List<Double> chordSpacings = pop.getSec().getGeneratedDurations();
			if (chordSpacings != null) {
				g.setColor(OMNI.alphen(Color.green, VibeComposerGUI.isDarkMode ? 90 : 150));
				double line = 0;
				for (int i = 0; i < chordSpacings.size() - 1; i++) {
					line += chordSpacings.get(i);
					int drawX = bottomLeft.x + (int) (quarterNoteLength * line);
					g.drawLine(drawX, bottomLeft.y, drawX, 0);
				}
			}


			// draw actual values

			int ovalWidth = usableHeight / 40;
			for (int i = 0; i < numValues; i++) {
				int pitch = values.get(i).getPitch();
				if (pitch < 0) {
					continue;
				}
				int drawX = bottomLeft.x + (int) (quarterNoteLength * values.get(i).getStartTime()
						+ quarterNoteLength * values.get(i).getOffset());
				int drawY = bottomLeft.y - (int) (rowHeight * (pitch + 1 - min));

				// draw straight line connecting values -- TODO: requires offset checking
				/*if (i < numValues - 1) {
					int nextPitch = values.get(i + 1).getPitch();
					if (nextPitch >= 0) {
						g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(), 50));
						g.drawLine(drawX, drawY,
								drawX + (int) (quarterNoteLength
										* (values.get(i).getRv() + values.get(i).getOffset())),
								bottomLeft.y - (int) (rowHeight
										* (values.get(i + 1).getPitch() + 1 - min)));
					}
				}*/


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

	public void setValues(PhraseNotes vals) {
		values = vals;
	}


	protected PhraseNote getDraggedNote(Point xy) {
		// TODO Auto-generated method stub
		int w = getWidth();
		int h = getHeight();
		int usableHeight = h - marginY * 2;
		int rowDivisors = max - min;
		double rowHeight = usableHeight / (double) rowDivisors;

		Point bottomLeftAdjusted = new Point(marginX,
				usableHeight + marginY - (int) (rowHeight / 2));
		int yValue = (int) ((bottomLeftAdjusted.y - xy.y) / rowHeight) + min;
		List<PhraseNote> possibleNotes = values.stream().filter(e -> yValue == e.getPitch())
				.collect(Collectors.toList());
		if (possibleNotes.isEmpty()) {
			return null;
		}
		values.remakeNoteStartTimes(true);
		double sectionLength = values.stream().map(e -> e.getRv()).mapToDouble(e -> e).sum();
		double quarterNoteLength = (getWidth() - marginX) / sectionLength;
		double mouseClickTime = (xy.x - marginX) / quarterNoteLength;
		for (int i = 0; i < possibleNotes.size(); i++) {
			PhraseNote pn = possibleNotes.get(i);
			if (mouseClickTime > pn.getStartTime()
					&& mouseClickTime < pn.getStartTime() + pn.getDuration()) {
				return possibleNotes.get(i);
			}
		}

		return null;
	}

	private double getDurationFromPosition(Point xy) {
		if (draggedNote == null || dragX == null) {
			return 0;
		}
		values.remakeNoteStartTimes(true);
		int draggedIndex = values.indexOf(draggedNote);
		double startTime = values.get(draggedIndex).getStartTime();
		double sectionLength = values.stream().map(e -> e.getRv()).mapToDouble(e -> e).sum();
		double quarterNoteLength = (getWidth() - marginX) / sectionLength;

		double durationTime = (xy.x - marginX) / quarterNoteLength;
		double mouseCorrectionTime = (dragX - marginX - startTime) / quarterNoteLength;

		return startingDuration + durationTime - mouseCorrectionTime;
	}

	private double getOffsetFromPosition(Point xy) {
		if (draggedNote == null || dragX == null) {
			return 0;
		}
		values.remakeNoteStartTimes(true);
		int draggedIndex = values.indexOf(draggedNote);
		double startTime = values.get(draggedIndex).getStartTime();
		double sectionLength = values.stream().map(e -> e.getRv()).mapToDouble(e -> e).sum();
		double quarterNoteLength = (getWidth() - marginX) / sectionLength;

		double offsetTime = (xy.x - marginX) / quarterNoteLength;
		double mouseCorrectionTime = (dragX - marginX - startTime) / quarterNoteLength;

		return startingOffset + offsetTime - mouseCorrectionTime;
	}

	public Point getOrderAndValueFromPosition(Point xy) {
		return getOrderAndValueFromPosition(xy, true, false);
	}

	public Point getOrderAndValueFromPosition(Point xy, boolean offsetted,
			boolean getClosestOriginal) {
		int w = getWidth();
		int h = getHeight();
		int rowDivisors = max - min;
		int usableHeight = h - marginY * 2;
		double rowHeight = usableHeight / (double) rowDivisors;

		Point bottomLeftAdjusted = new Point(marginX,
				usableHeight + marginY - (int) (rowHeight / 2));

		values.remakeNoteStartTimes(offsetted);
		double sectionLength = values.stream().map(e -> e.getRv()).mapToDouble(e -> e).sum();
		double quarterNoteLength = (w - bottomLeftAdjusted.x) / sectionLength;

		int yValue = (int) ((bottomLeftAdjusted.y - xy.y) / rowHeight) + min;

		double searchX = (xy.x - bottomLeftAdjusted.x) / quarterNoteLength;
		//LG.d(searchX);
		Integer foundX = searchX < MidiGenerator.DBL_ERR ? 0 : null;
		if (foundX == null) {
			List<Integer> possibleNotes = new ArrayList<>();
			if (getClosestOriginal) {
				for (int i = 0; i < values.size(); i++) {
					double start = values.get(i).getStartTime();
					double end = i < values.size() - 1 ? values.get(i + 1).getStartTime()
							: sectionLength;
					if (start < searchX && searchX < end) {
						possibleNotes.add(i);
						break;
					}
				}
			} else {
				for (int i = 0; i < values.size(); i++) {
					if (searchX + MidiGenerator.DBL_ERR > values.get(i).getStartTime()
							&& searchX - MidiGenerator.DBL_ERR < values.get(i).getStartTime()
									+ values.get(i).getDuration()) {
						possibleNotes.add(i);
					}
				}
			}


			if (possibleNotes.isEmpty()) {
				return null;
			}


			int difference = Integer.MAX_VALUE;
			for (int i = 0; i < possibleNotes.size(); i++) {
				int newDiff = Math.abs(values.get(possibleNotes.get(i)).getPitch() - yValue);
				if (newDiff < difference) {
					difference = newDiff;
					foundX = possibleNotes.get(i);
				}
			}

		}

		int xValue = foundX;

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
