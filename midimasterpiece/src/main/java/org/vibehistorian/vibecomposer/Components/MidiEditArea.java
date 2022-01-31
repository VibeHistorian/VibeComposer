package org.vibehistorian.vibecomposer.Components;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.PhraseNote;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Popups.MidiEditPopup;

import jm.music.data.Note;

public class MidiEditArea extends JComponent {

	public static enum DM {
		POSITION, DURATION, NOTE_START, VELOCITY, PITCH, PITCH_SHAPE, VELOCITY_SHAPE,
		VELOCITY_AND_POS, PITCH_AND_POS;
	}

	private static final long serialVersionUID = -2972572935738976623L;
	public int min = -10;
	public int max = 10;
	PhraseNotes values = null;

	int colStart = 0;
	int rowStart = 0;
	int marginX = 80;
	int marginY = 40;

	int markWidth = 6;
	int numHeight = 6;
	int numWidth = 4;

	PhraseNote draggedNote;
	PhraseNote draggedNoteCopy;
	Point orderValDeletion;
	Set<DM> dragMode = new HashSet<>();
	Integer dragLocation;
	long lastPlayedNoteTime;
	boolean lockTimeGrid;
	Integer dragX;
	Integer dragY;
	public static double sectionLength = 16.0;
	PhraseNote highlightedNote;
	Integer highlightedDragLocation;
	Integer prevHighlightedDragLocation;

	int noteDragMarginX = 5;

	MidiEditPopup pop = null;

	public MidiEditArea(int minimum, int maximum, PhraseNotes vals) {
		super();
		setMin(minimum);
		setMax(maximum);
		values = vals;
		reset();

		addMouseWheelListener(new MouseWheelListener() {
			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (!e.isAltDown()) {
					int rot = (e.getWheelRotation() > 0) ? -1 : 1;
					if ((rot > 0 && max > 110) || (rot < 0 && min < 10)) {
						return;
					}
					int originalTrackScopeUpDown = MidiEditPopup.trackScope;
					MidiEditPopup.trackScopeUpDown = OMNI
							.clamp(MidiEditPopup.trackScopeUpDown + rot, -4, 4);
					if (originalTrackScopeUpDown != MidiEditPopup.trackScopeUpDown) {
						min += MidiEditPopup.baseMargin * rot;
						max += MidiEditPopup.baseMargin * rot;
						repaint();
					}
				} else {
					int rot = (e.getWheelRotation() > 0) ? -1 : 1;
					int originalTrackScope = MidiEditPopup.trackScope;
					if (rot > 0 && (max > 110 || min < 10)) {
						return;
					}
					MidiEditPopup.trackScope = Math.max(originalTrackScope + rot, 1);
					if (originalTrackScope != MidiEditPopup.trackScope) {
						min -= MidiEditPopup.baseMargin * rot;
						max += MidiEditPopup.baseMargin * rot;
						repaint();
					}
				}
			}
		});

		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				if (!isEnabled()) {
					return;
				}

				dragMode.clear();
				dragX = evt.getPoint().x;
				dragY = evt.getPoint().y;
				draggedNote = getDraggedNote(evt.getPoint());
				dragLocation = getMouseNoteLocationPixelated(draggedNote, evt.getPoint());

				lockTimeGrid = !evt.isShiftDown();

				if (SwingUtilities.isLeftMouseButton(evt)) {
					handleLeftPress(evt);
				} else if (SwingUtilities.isRightMouseButton(evt)) {
					orderValDeletion = getOrderAndValueFromPosition(evt.getPoint());
				} else if (SwingUtilities.isMiddleMouseButton(evt)) {
					handleMiddlePress(evt);
				}
				draggedNoteCopy = (draggedNote != null) ? new PhraseNote(draggedNote.toNote())
						: null;
			}

			private void handleMiddlePress(MouseEvent evt) {
				Point orderVal = getOrderAndValueFromPosition(evt.getPoint());
				int isAlt = evt.getModifiersEx() & MouseEvent.ALT_DOWN_MASK;
				if (isAlt == MouseEvent.ALT_DOWN_MASK) {
					dragMode.add(DM.DURATION);
				} else if (evt.isControlDown()) {
					if (orderVal != null && values.get(orderVal.x).getPitch() >= 0) {
						PhraseNote note = values.get(orderVal.x);
						int velocity = OMNI.clamp(
								(int) (127 * (orderVal.y - min) / (double) (max - min)), 0, 127);
						if (velocity != note.getDynamic()) {
							note.setDynamic(velocity);
							playNote(note);
						}
					}
					dragMode.add(DM.VELOCITY_SHAPE);

				} else if (evt.isShiftDown()) {
					dragMode.add(DM.PITCH);
				} else if (draggedNote != null) {
					lastPlayedNoteTime = System.currentTimeMillis();
					playNote(draggedNote);

					dragMode.add(DM.VELOCITY);
				}
				repaint();

			}

			private void handleRightPress(MouseEvent evt) {
				Point orderVal = getOrderAndValueFromPosition(evt.getPoint());
				if (orderVal != null && orderVal.equals(orderValDeletion)) {
					setVal(orderVal.x, Note.REST);
					repaint();
				}
			}

			private void handleLeftPress(MouseEvent evt) {
				if (evt.isAltDown()) {
					dragMode.add(DM.NOTE_START);
				} else if (evt.isControlDown()) {
					dragMode.add(DM.PITCH_SHAPE);
				} else {
					if (draggedNote == null) {
						Point orderVal = getOrderAndValueFromPosition(evt.getPoint(), false, true);
						if (orderVal != null) {
							/*if (pn.getPitch() == Note.REST && pn.getRv() > MidiGenerator.DBL_ERR) {
							
								LG.d("UnRESTing existing original note..");
								pn.setOffset(0);
								setVal(orderVal.x, orderVal.y);
								draggedNote = pn;
							}*/

							LG.d("Inserting new note..");
							int closestNormalized = MidiUtils
									.getClosestFromList(MidiUtils.MAJ_SCALE, orderVal.y % 12);
							PhraseNote insertedPn = new PhraseNote(pop.snapToScaleGrid.isSelected()
									? (12 * (orderVal.y / 12) + closestNormalized)
									: orderVal.y);
							insertedPn.setDuration(MidiGenerator.Durations.EIGHTH_NOTE);
							insertedPn.setRv(0);
							insertedPn.setOffset(values.get(orderVal.x).getOffset());
							insertedPn.setStartTime(values.get(orderVal.x).getStartTime());
							insertedPn.setAbsoluteStartTime(
									values.get(orderVal.x).getAbsoluteStartTime());
							getValues().add(orderVal.x, insertedPn);


							draggedNote = insertedPn;
							double time = getTimeFromPosition(evt.getPoint());
							LG.d("Time pre:" + time);
							double offset = time - draggedNote.getAbsoluteStartTime();
							if (lockTimeGrid) {
								offset = getClosestToTimeGrid(time)
										- draggedNote.getAbsoluteStartTime();
							}
							draggedNote.setOffset(offset);
							LG.d("Time post:" + (draggedNote.getAbsoluteStartTime()
									+ draggedNote.getOffset()));

							dragMode.add(DM.PITCH_AND_POS);
							playNote(draggedNote, 300);
							repaint();
						}
					} else {
						switch (dragLocation) {
						case 0:
							dragMode.add(DM.NOTE_START);
							break;
						case 1:
							dragMode.add(evt.isShiftDown() ? DM.POSITION : DM.PITCH_AND_POS);
							break;
						case 2:
							dragMode.add(DM.DURATION);
							break;
						}
					}
				}
			}

			@Override
			public void mouseReleased(MouseEvent evt) {
				if (SwingUtilities.isRightMouseButton(evt)) {
					handleRightPress(evt);
				} else {
					if (draggingAny(DM.VELOCITY)
							&& draggedNoteCopy.getDynamic() != draggedNote.getDynamic()
							&& (System.currentTimeMillis() - lastPlayedNoteTime) > 500) {
						playNote(draggedNote, 300);
					}
				}

				pop.saveToHistory();

				reset();
			}
		});

		addMouseMotionListener(new MouseMotionListener() {

			@Override
			public void mouseDragged(MouseEvent e) {
				processDragEvent(e);
				//LG.d("Mouse dragged");
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				highlightedNote = getDraggedNote(e.getPoint());
				highlightedDragLocation = getMouseNoteLocationPixelated(highlightedNote,
						e.getPoint());
				if (prevHighlightedDragLocation != null
						&& !prevHighlightedDragLocation.equals(highlightedDragLocation)) {
					prevHighlightedDragLocation = highlightedDragLocation;
					repaint();
				} else if (prevHighlightedDragLocation == null && highlightedDragLocation != null) {
					prevHighlightedDragLocation = highlightedDragLocation;
					repaint();
				}
				//LG.d("Mouse moved");
				processDragEvent(e);
			}

		});
	}

	private Integer getMouseNoteLocationPixelated(PhraseNote pn, Point loc) {
		if (pn != null) {
			int noteStart = getPositionFromTime(pn.getStartTime()) - noteDragMarginX;
			int noteEnd = getPositionFromTime(pn.getStartTime() + pn.getDuration())
					+ noteDragMarginX;

			if (noteStart <= loc.x && loc.x <= noteEnd) {
				if (pn.getDuration() > MidiGenerator.Durations.SIXTEENTH_NOTE / 2.0) {
					if (noteStart + noteDragMarginX * 2 >= loc.x) {
						return 0;
					} else if (noteEnd - noteDragMarginX * 2 <= loc.x) {
						return 2;
					} else {
						return 1;
					}
				} else {
					return 1;
				}
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	private Integer getMouseNoteLocation(PhraseNote pn, Point loc) {
		if (pn != null) {
			double timeDifference = getTimeFromPosition(loc) - pn.getStartTime();
			double positionInNote = timeDifference / pn.getDuration();

			if (positionInNote >= 0 && positionInNote <= 1.0) {
				if (pn.getDuration() > MidiGenerator.Durations.SIXTEENTH_NOTE / 2.0) {
					if (positionInNote < 0.15) {
						return 0;
					} else if (positionInNote > 0.85) {
						return 2;
					} else {
						return 1;
					}
				} else {
					return 1;
				}
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	private void playNote(PhraseNote pn) {
		playNote(pn, 500);
	}

	private void playNote(PhraseNote pn, int durationMs) {
		if (pn != null) {
			VibeComposerGUI.playNote(pn.getPitch(), durationMs, pn.getDynamic(), pop.part,
					pop.partOrder, pop.getSec());
		}

	}

	private void reset() {
		draggedNote = null;
		dragMode.clear();
		lastPlayedNoteTime = 0;
		lockTimeGrid = false;

		draggedNoteCopy = null;

		lockTimeGrid = false;
		dragX = null;
		dragY = null;
		dragLocation = null;

		highlightedNote = null;
		highlightedDragLocation = null;

		orderValDeletion = null;

		repaint();
	}

	protected void processDragEvent(MouseEvent e) {
		if (!dragMode.isEmpty()) {
			if (draggingAny(DM.PITCH_SHAPE, DM.VELOCITY_SHAPE)) {
				// PITCH SHAPE
				if (draggingAny(DM.PITCH_SHAPE)) {
					Point orderVal = getOrderAndValueFromPosition(e.getPoint());
					if (orderVal != null && values.get(orderVal.x).getPitch() >= 0) {
						int prevPitch = values.get(orderVal.x).getPitch();
						setVal(orderVal.x, orderVal.y);
						repaint();
						if (values.get(orderVal.x).getPitch() != prevPitch) {
							playNote(values.get(orderVal.x), 300);
						}
					}
				}
				// VELOCITY SHAPE
				if (draggingAny(DM.VELOCITY_SHAPE)) {
					Point orderVal = getOrderAndValueFromPosition(e.getPoint());
					if (orderVal != null && values.get(orderVal.x).getPitch() >= 0) {
						PhraseNote note = values.get(orderVal.x);
						int velocity = OMNI.clamp(
								(int) (127 * (orderVal.y - min) / (double) (max - min)), 0, 127);
						if (velocity != note.getDynamic()) {
							note.setDynamic(velocity);
							playNote(note);
							repaint();
						}

					}
				}
			} else if (draggedNote != null) {

				// POSITION
				if (draggingAny(DM.POSITION, DM.VELOCITY_AND_POS, DM.PITCH_AND_POS)) {
					double offset = getOffsetFromPosition(e.getPoint(), draggedNote);
					if (lockTimeGrid) {
						offset = getClosestToTimeGrid(offset + draggedNote.getAbsoluteStartTime())
								- draggedNote.getAbsoluteStartTime();
					}

					draggedNote.setOffset(offset);
				}

				// DURATION
				if (draggingAny(DM.DURATION)) {
					double duration = getDurationFromPosition(e.getPoint());
					if (lockTimeGrid) {
						duration = timeGridValue(duration);
					}
					duration = Math.max(MidiGenerator.Durations.SIXTEENTH_NOTE / 2, duration);
					draggedNote.setDuration(duration);
				}

				// NOTE START
				if (draggingAny(DM.NOTE_START)) {
					double offset = getOffsetFromPosition(e.getPoint(), draggedNote);
					if (lockTimeGrid) {
						offset = getClosestToTimeGrid(offset + draggedNote.getAbsoluteStartTime())
								- draggedNote.getAbsoluteStartTime();
					}

					if ((offset - draggedNoteCopy.getOffset()
							- draggedNoteCopy
									.getDuration()) < (MidiGenerator.Durations.SIXTEENTH_NOTE
											/ 2.5)) {
						draggedNote.setOffset(offset);
						double duration = Math.max(MidiGenerator.Durations.SIXTEENTH_NOTE / 2,
								draggedNoteCopy.getDuration() + draggedNoteCopy.getOffset()
										- offset);
						draggedNote.setDuration(duration);
					}
				}

				// VELOCITY
				if (draggingAny(DM.VELOCITY, DM.VELOCITY_AND_POS)) {
					int velocity = getVelocityFromPosition(e.getPoint());
					velocity = OMNI.clamp(velocity, 0, 127);
					draggedNote.setDynamic(velocity);
					if ((System.currentTimeMillis() - lastPlayedNoteTime) > 500) {
						playNote(draggedNote, 300);
						lastPlayedNoteTime = System.currentTimeMillis();
					}
				}

				// PITCH
				if (draggingAny(DM.PITCH, DM.PITCH_AND_POS)) {
					int pitch = getPitchFromPosition(e.getPoint());
					if (pop.snapToScaleGrid.isSelected()) {
						pitch = MidiUtils.getClosestFromList(MidiUtils.MAJ_SCALE, pitch % 12)
								+ (12 * (pitch / 12));
					}
					draggedNote.setPitch(pitch);
				}

				repaint();
			}
		}
	}

	private double timeGridValue(double val) {
		double timeGrid = MidiEditPopup.getTimeGrid();
		double newVal = Math.round(val / timeGrid) * timeGrid;
		return newVal;
	}

	private double getClosestToTimeGrid(double val) {
		LG.i("Time val: " + val);
		List<Double> timeGridLocations = new ArrayList<>();
		double timeGrid = MidiEditPopup.getTimeGrid();
		double currentTime = 0;
		while (currentTime < sectionLength) {
			timeGridLocations.add(currentTime);
			currentTime += timeGrid;
		}
		timeGridLocations
				.addAll(values.stream().map(e -> e.getStartTime()).collect(Collectors.toSet()));
		timeGridLocations.stream().distinct().collect(Collectors.toList());
		Collections.sort(timeGridLocations);

		double closestVal = MidiUtils.getClosestDoubleFromList(timeGridLocations, val, false);
		LG.i("Time - closest: " + closestVal);
		return closestVal;
	}

	void setVal(int pos, int pitch) {
		if (pitch == Note.REST && values.get(pos).getRv() < MidiGenerator.DBL_ERR) {
			values.remove(pos);
		} else {
			if (pop.snapToScaleGrid.isSelected()) {
				int closestNormalized = MidiUtils.getClosestFromList(MidiUtils.MAJ_SCALE,
						pitch % 12);

				values.get(pos).setPitch(12 * (pitch / 12) + closestNormalized);
			} else {
				values.get(pos).setPitch(pitch);
			}
		}
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
					: new Color(180, 184, 188));
			g.fillRect(0, 0, w, h);

			// draw graph lines - first to last value X, min to max value Y
			g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(), 80));

			Point bottomLeft = new Point(marginX, usableHeight + marginY);
			g.drawLine(bottomLeft.x, bottomLeft.y, bottomLeft.x, 0);
			g.drawLine(bottomLeft.x, bottomLeft.y, w, bottomLeft.y);

			double quarterNoteLength = (w - bottomLeft.x) / sectionLength;
			values.remakeNoteStartTimes();

			int partNum = (pop.getParent() != null) ? pop.getParent().getPartNum() : 0;


			// to draw scale/key helpers
			Color highlightedScaleKeyColor = OMNI.alphen(VibeComposerGUI.uiColor(),
					VibeComposerGUI.isDarkMode ? 65 : 90);
			Color highlightedScaleKeyHelperColor = OMNI.alphen(highlightedScaleKeyColor, 40);
			List<Integer> highlightedScaleKey = calculateHighlightedScaleKey(pop.getSec());
			Color nonHighlightedColor = VibeComposerGUI.isDarkMode ? new Color(150, 100, 30, 65)
					: new Color(150, 150, 150, 100);
			Color nonHighlightedHelperColor = OMNI.alphen(nonHighlightedColor, 50);


			List<Double> chordSpacings = new ArrayList<>(pop.getSec().getGeneratedDurations());
			List<Double> chordSpacingsTemp = new ArrayList<>(chordSpacings);
			for (int i = 1; i < pop.getSec().getMeasures(); i++) {
				chordSpacings.addAll(chordSpacingsTemp);
			}
			Color highlightedChordNoteColor = VibeComposerGUI.isDarkMode
					? new Color(220, 180, 150, 100)
					: new Color(0, 0, 0, 150);
			Color highlightedChordNoteHelperColor = OMNI.alphen(highlightedChordNoteColor, 60);
			Map<Integer, Set<Integer>> chordHighlightedNotes = calculateHighlightedChords(
					pop.getSec());

			// draw numbers left of Y line
			// draw line marks
			for (int i = 0; i < 1 + (max - min); i++) {

				String drawnValue = "" + (min + i) + " | " + MidiUtils.pitchToString(min + i);
				int valueLength = drawnValue.startsWith("-") ? drawnValue.length() + 1
						: drawnValue.length();
				int drawValueX = bottomLeft.x / 2 - (numWidth * valueLength) / 2;
				int drawMarkX = bottomLeft.x - markWidth / 2;
				int drawY = bottomLeft.y - (int) (rowHeight * (i + 1));

				boolean highlighted = false;
				if (pop.highlightMode.getSelectedIndex() % 2 == 1) {
					// scalekey highlighting
					if (highlightedScaleKey != null
							&& highlightedScaleKey.contains((min + i + 1200) % 12)) {
						g.setColor(highlightedScaleKeyColor);
						highlighted = true;
					}
				}
				if (!highlighted) {
					g.setColor(nonHighlightedColor);
				}

				g.drawLine(bottomLeft.x, drawY, w, drawY);

				g.setColor(VibeComposerGUI.uiColor());
				g.drawString(drawnValue, drawValueX, drawY + numHeight / 2);
				g.drawLine(drawMarkX, drawY, drawMarkX + markWidth, drawY);


			}


			List<Double> timeGridLocations = new ArrayList<>();
			double timeGrid = MidiEditPopup.getTimeGrid();
			double currentTime = 0;
			while (currentTime < sectionLength) {
				timeGridLocations.add(currentTime);
				currentTime += timeGrid;
			}
			timeGridLocations
					.addAll(values.stream().map(e -> e.getStartTime()).collect(Collectors.toSet()));
			timeGridLocations.stream().distinct().collect(Collectors.toList());
			Collections.sort(timeGridLocations);


			// draw numbers below X line
			// draw line marks
			double lineSpacing = (timeGrid < 0.24) ? 0.25 : timeGrid;
			double prev = -1;
			for (int i = 0; i < timeGridLocations.size(); i++) {
				double curr = timeGridLocations.get(i);
				if (MidiUtils.roughlyEqual(curr, prev)) {
					continue;
				}
				g.setColor(VibeComposerGUI.uiColor());
				//String drawnValue = "" + (i + 1);
				//int valueLength = drawnValue.startsWith("-") ? drawnValue.length() + 1
				//		: drawnValue.length();
				int drawValueY = numHeight + (bottomLeft.y + h) / 2;
				int drawMarkY = (bottomLeft.y - markWidth / 2);
				int drawX = bottomLeft.x + (int) (quarterNoteLength * curr);
				double remainderToOne = curr % 1.0;
				double remainderToFour = curr % 2.0;
				if (remainderToOne < 0.05 || MidiUtils.isMultiple(remainderToFour, lineSpacing)) {
					String drawnValue = "";
					if (remainderToOne < 0.05) {
						drawnValue = String.format("%.0f", curr);
					} else {
						drawnValue = String.format(Locale.GERMAN, "%.2f", remainderToOne);
						drawnValue = "." + drawnValue.split(",")[1];
					}

					g.drawString(drawnValue, drawX - (numWidth * drawnValue.length()) / 2,
							drawValueY);
				}
				g.drawLine(drawX, drawMarkY, drawX, drawMarkY + markWidth);

				// draw line helpers/dots
				g.setColor(highlightedScaleKeyHelperColor);
				for (int j = 0; j < 1 + max - min; j++) {
					int drawDotY = bottomLeft.y - (int) (rowHeight * (j + 1));
					g.drawLine(drawX, drawDotY - 2, drawX, drawDotY + 2);
				}

				prev = curr;

			}

			// draw chord spacing
			if (chordSpacings != null) {

				double line = 0;
				for (int i = 0; i < chordSpacings.size(); i++) {
					g.setColor(
							OMNI.alphen(VibeComposerGUI.isDarkMode ? Color.green : Color.red, 90));
					int drawX = bottomLeft.x + (int) (quarterNoteLength * line);
					// vertical separators
					if (i > 0) {
						g.drawLine(drawX, bottomLeft.y, drawX, 0);
					}

					Set<Integer> chordNotes = chordHighlightedNotes
							.get(i % chordHighlightedNotes.size());
					if (pop.highlightMode.getSelectedIndex() >= 2 && chordNotes != null) {
						// horizontal helper lines
						int drawXEnd = drawX + (int) (quarterNoteLength * chordSpacings.get(i));
						g.setColor(highlightedChordNoteColor);
						for (int j = 0; j < 1 + (max - min); j++) {
							int noteTest = (min + j + 1200) % 12;
							if (chordNotes.contains(noteTest)) {
								g.setColor(highlightedChordNoteColor);
								int drawY = bottomLeft.y - (int) (rowHeight * (j + 1));
								g.drawLine(drawX, drawY, drawXEnd, drawY);
							}
						}
					}

					line += chordSpacings.get(i);
				}
			}


			// draw actual values

			int ovalWidth = usableHeight / 40;
			for (int i = 0; i < numValues; i++) {
				PhraseNote pn = values.get(i);
				int pitch = pn.getPitch();
				if (pitch < 0) {
					continue;
				}
				int drawX = bottomLeft.x + (int) (quarterNoteLength * pn.getStartTime());
				int drawY = bottomLeft.y - (int) (rowHeight * (pitch + 1 - min));
				int width = (int) (quarterNoteLength * pn.getDuration());

				// draw straight line connecting values -- TODO: requires offset checking
				/*if (i < numValues - 1) {
					int nextPitch = values.get(i + 1).getPitch();
					if (nextPitch >= 0) {
						g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(), 50));
						g.drawLine(drawX, drawY,
								drawX + (int) (quarterNoteLength
										* (pn.getRv() + pn.getOffset())),
								bottomLeft.y - (int) (rowHeight
										* (values.get(i + 1).getPitch() + 1 - min)));
					}
				}*/

				g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(), 140));
				g.drawLine(drawX, drawY - 5, drawX, drawY + 5);
				g.drawLine(drawX + width, drawY - 5, drawX + width, drawY + 5);

				g.drawString(pitch + "(" + MidiUtils.pitchToString(pitch) + ") :" + pn.getDynamic(),
						drawX + ovalWidth / 2, drawY - ovalWidth / 2);

				if (draggedNote != null && pn == draggedNote) {
					g.setColor(OMNI.alphen(OMNI.mixColor(VibeComposerGUI.uiColor(), Color.red, 0.7),
							(int) (30 + 140 * (pn.getDynamic() / 127.0))));
				} else {
					g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(),
							(int) (30 + 140 * (pn.getDynamic() / 127.0))));
				}

				g.fillRect(drawX, drawY - 4, width, 8);

				if (highlightedNote != null && pn == highlightedNote
						&& highlightedDragLocation != null) {
					switch (highlightedDragLocation) {
					case 0:
						g.fillRect(drawX - noteDragMarginX, drawY - 5, noteDragMarginX * 2, 10);
						break;
					case 1:
						g.fillRect(drawX + noteDragMarginX, drawY - 5, width - noteDragMarginX * 2,
								10);
						break;
					case 2:
						g.fillRect(drawX + width - noteDragMarginX, drawY - 5, noteDragMarginX * 2,
								10);
						break;
					}
				}

				if (draggingAny(DM.VELOCITY_SHAPE)) {
					g.drawLine(drawX + width / 2, drawY, drawX + width / 2,
							drawY + 63 - pn.getDynamic());
				}
				if (draggingAny(DM.NOTE_START, DM.POSITION, DM.PITCH_AND_POS)) {

				}


			}
		}
	}

	private boolean draggingAny(DM... values) {
		for (DM val : values) {
			if (dragMode.contains(val)) {
				return true;
			}
		}
		return false;
	}

	private int getChordIndexByStartTime(List<Double> chordLengths, double noteStartTime) {
		double chordLengthSum = 0;
		for (int i = 0; i < chordLengths.size(); i++) {
			chordLengthSum += chordLengths.get(i);
			if (noteStartTime < chordLengthSum) {
				return i;
			}
		}
		return chordLengths.size() - 1;
	}

	public static List<Integer> calculateHighlightedScaleKey(Section sec) {
		return MidiUtils.MAJ_SCALE;
	}

	public static Map<Integer, Set<Integer>> calculateHighlightedChords(Section sec) {
		if (MidiGenerator.chordInts.isEmpty()) {
			return null;
		}

		if (sec == null || !sec.isCustomChordsDurationsEnabled()) {
			return MidiUtils.getHighlightTargetsFromChords(MidiGenerator.chordInts, false);
		} else {
			return MidiUtils.getHighlightTargetsFromChords(sec.getCustomChordsList(), false);
		}
	}

	public PhraseNotes getValues() {
		return values;
	}

	public void setValues(PhraseNotes vals) {
		values = vals;
	}

	protected PhraseNote getDraggedNote(Point xy) {
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
		values.remakeNoteStartTimes();
		double quarterNoteLength = (getWidth() - marginX) / sectionLength;
		double noteDragMarginTime = noteDragMarginX / quarterNoteLength;
		double mouseClickTime = (xy.x - marginX) / quarterNoteLength;
		for (int i = 0; i < possibleNotes.size(); i++) {
			PhraseNote pn = possibleNotes.get(i);
			if (mouseClickTime > (pn.getStartTime() - noteDragMarginTime)
					&& mouseClickTime < (pn.getStartTime() + pn.getDuration()
							+ noteDragMarginTime)) {
				return possibleNotes.get(i);
			}
		}

		return null;
	}

	private int getPitchFromPosition(Point xy) {
		int rowDivisors = max - min;
		int usableHeight = getHeight() - marginY * 2;
		double rowHeight = usableHeight / (double) rowDivisors;

		Point bottomLeftAdjusted = new Point(marginX,
				usableHeight + marginY - (int) (rowHeight / 2));

		int yValue = (int) ((bottomLeftAdjusted.y - xy.y) / rowHeight) + min;
		return yValue;

	}

	private int getVelocityFromPosition(Point xy) {
		if (draggedNote == null) {
			return 0;
		} else if (dragY == null) {
			return draggedNoteCopy.getDynamic();
		}
		int yDiff = dragY - xy.y;
		return draggedNoteCopy.getDynamic() + yDiff / 5;

	}

	private double getDurationFromPosition(Point xy) {
		if (draggedNote == null || dragX == null) {
			return 0;
		}
		values.remakeNoteStartTimes();
		double startTime = draggedNote.getStartTime();
		double quarterNoteLength = (getWidth() - marginX) / sectionLength;

		double durationTime = (xy.x - marginX) / quarterNoteLength;
		double mouseCorrectionTime = (dragX - marginX - startTime) / quarterNoteLength;

		return draggedNoteCopy.getDuration() + durationTime - mouseCorrectionTime;
	}

	private double getOffsetFromPosition(Point xy, PhraseNote dragNote) {
		if (dragNote == null || dragX == null) {
			return 0;
		}
		values.remakeNoteStartTimes();
		double startTime = dragNote.getStartTime();
		double quarterNoteLength = (getWidth() - marginX) / sectionLength;

		double offsetTime = (xy.x - marginX) / quarterNoteLength;
		double mouseCorrectionTime = (dragX - marginX - startTime) / quarterNoteLength;

		return draggedNoteCopy.getOffset() + offsetTime - mouseCorrectionTime;
	}

	private double getTimeFromPosition(Point xy) {
		double quarterNoteLength = (getWidth() - marginX) / sectionLength;
		return (xy.x - marginX) / quarterNoteLength;
	}

	private int getPositionFromTime(double time) {
		double quarterNoteLength = (getWidth() - marginX) / sectionLength;
		return marginX + (int) (quarterNoteLength * time);
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

		values.remakeNoteStartTimes();
		double quarterNoteLength = (w - bottomLeftAdjusted.x) / sectionLength;

		int yValue = (int) ((bottomLeftAdjusted.y - xy.y) / rowHeight) + min;

		double searchX = (xy.x - bottomLeftAdjusted.x) / quarterNoteLength;
		//LG.d(searchX);
		Integer foundX = searchX < MidiGenerator.DBL_ERR ? 0 : null;
		if (foundX == null) {
			List<Integer> possibleNotes = new ArrayList<>();
			if (getClosestOriginal) {
				for (int i = 0; i < values.size(); i++) {
					double start = values.get(i).getStart(offsetted);
					double end = i < values.size() - 1 ? values.get(i + 1).getStart(offsetted)
							: sectionLength;
					if (start < searchX && searchX < end) {
						possibleNotes.add(i);
						break;
					}
				}
			} else {
				for (int i = 0; i < values.size(); i++) {
					if (searchX + MidiGenerator.DBL_ERR > values.get(i).getStart(offsetted)
							&& searchX - MidiGenerator.DBL_ERR < values.get(i).getStart(offsetted)
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

	public MidiEditPopup getPop() {
		return pop;
	}

	public void setPop(MidiEditPopup pop) {
		this.pop = pop;
	}

	public void setMin(int min) {
		this.min = min;
	}

	public void setMax(int max) {
		this.max = max;
	}


}
