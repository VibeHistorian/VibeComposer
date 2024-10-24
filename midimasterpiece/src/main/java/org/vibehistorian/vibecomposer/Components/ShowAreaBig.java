/*

<This Java Class is part of the jMusic API version 1.5, March 2004.>

Copyright (C) 2000 Andrew Sorensen & Andrew Brown

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or any
later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not,
see <https://www.gnu.org/licenses/>.

*/

/**
 * A java canvas object which displays a score as a
 * psudo Common Practice Notation in a window.
 * Used as part of jMusic ShowScore, and other apps.
 * @author Andrew Brown
 */
package org.vibehistorian.vibecomposer.Components;

import jm.music.data.Note;
import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.Helpers.PartExt;
import org.vibehistorian.vibecomposer.Helpers.PhraseExt;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.SoloMuter.State;
import org.vibehistorian.vibecomposer.Popups.MidiEditPopup;
import org.vibehistorian.vibecomposer.SwingUtils;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

//--------------
//second class!!
//--------------
public class ShowAreaBig extends JComponent {
	private static final long serialVersionUID = -7925170286317013689L;
	//attributes
	private int oldXMouse;
	public static int noteHeight = 5;
	private int w = 2 * noteHeight; //width between stave lines
	private int ePos = 5 * noteHeight; // position of e in the treble stave
	private int e = ePos + noteHeight * 33;
	public static int areaHeight = VibeComposerGUI.DEFAULT_HEIGHT;
	private int[] noteOffset = { 0, 0, noteHeight, noteHeight, noteHeight * 2, noteHeight * 3,
			noteHeight * 3, noteHeight * 4, noteHeight * 4, noteHeight * 5, noteHeight * 5,
			noteHeight * 6 };
	private Font font = new Font("Helvetica", Font.PLAIN, 10);
	private ShowPanelBig sp;
	private int thinNote = 2; // thin value
	public static final int noteOffsetXMargin = 10;
	public static double[] noteTrimValues = { MidiGenerator.Durations.SIXTEENTH_NOTE / 2.0,
			MidiGenerator.Durations.SIXTEENTH_NOTE, MidiGenerator.Durations.EIGHTH_NOTE };
	public static Point mousePoint = null;
	public static boolean consumed = false;

	public static int getIndexForPartName(String partName) {
		if (partName == null) {
			return -1;
		}
		if (partName.contains("Melod")) {
			return 0;
		} else if (partName.contains("Bass")) {
			return 1;
		} else if (partName.contains("Chord")) {
			return 2;
		} else if (partName.contains("Arp")) {
			return 3;
		} else if (partName.contains("Drum")) {
			return 4;
		} else {
			throw new IllegalArgumentException("Unknown part name: " + partName);
		}
	}

	public static int getPartOrderForPartName(String partName) {
		int partNameIndex = getIndexForPartName(partName);
		if (partNameIndex < 0) {
			return -1;
		}
		return Integer.parseInt(StringUtils.getDigits(partName));
	}

	public ShowAreaBig(ShowPanelBig sp) {
		super();
		this.sp = sp;
		//width and height of score notation area
		this.setSize(ShowPanelBig.beatWidthBase, areaHeight);

		/*for (int i = 0; i < maxColours; i++) {
			theColours[i][0] = (float) (Math.random() / maxColours / 2)
					+ (float) (1.0 / maxColours * i);
			theColours[i][1] = (float) (Math.random() / maxColours)
					+ (float) (1.0 / maxColours * i);
		}*/

		addMouseMotionListener(new MouseMotionListener() {

			@Override
			public void mouseMoved(MouseEvent e) {
				mousePoint = new Point(e.getPoint());
				repaint();
			}

			@Override
			public void mouseDragged(MouseEvent e) {
			}
		});

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent evt) {
				boolean leftMouseOpenPopup = SwingUtilities.isLeftMouseButton(evt);
				boolean rightMouseOpenSectionTab = SwingUtilities.isRightMouseButton(evt);
				LG.d("Checking note overlap for: " + evt.getPoint());

				Enumeration<?> enum1 = sp.score.getPartList().elements();
				double beatWidth = sp.beatWidth;
				Set<Integer> soloMuterHighlightedTracks = getSoloMuterHighlightedTracks();

				while (enum1.hasMoreElements()) {
					PartExt part = (PartExt) enum1.nextElement();
					if (part.isFillerPart()) {
						continue;
					}
					if (!soloMuterHighlightedTracks.isEmpty()
							&& !soloMuterHighlightedTracks.contains(part.getTrackNumber())) {
						continue;
					}

					Enumeration<?> enum2 = part.getPhraseList().elements();
					while (enum2.hasMoreElements()) {
						PhraseExt phrase = (PhraseExt) enum2.nextElement();
						double oldXBeat = phrase.getStartTime();

						if (phrase.getHighestPitch() < 0) {
							continue;
						}

						Enumeration<?> enum3 = phrase.getNoteList().elements();
						oldXMouse = (int) (Math.round(oldXBeat * beatWidth));

						while (enum3.hasMoreElements()) {
							Note aNote = (Note) enum3.nextElement();
							int currNote = -1;
							if (aNote.getPitchType() == Note.MIDI_PITCH)
								currNote = aNote.getPitch();
							else
								currNote = Note.freqToMidiPitch(aNote.getFrequency());

							if (currNote < 0) {
								//LG.d("(NOT) PRINTING EMPTY NOTE!");
								oldXBeat += aNote.getRhythmValue();
								oldXMouse = (int) (Math.round(oldXBeat * beatWidth));
								continue;
							}
							//LG.d("Note: " + currNote);
							if ((currNote <= 127) && (currNote >= 0)) {
								int y = getNotePosY(currNote);

								double durationTrimmer = ShowPanelBig.trimNoteLengthBox
										.getSelectedIndex() > 0
												? noteTrimValues[ShowPanelBig.trimNoteLengthBox
														.getSelectedIndex() - 1]
												: 1000;


								int x = (int) (Math
										.round(Math.min(durationTrimmer, aNote.getDuration())
												* beatWidth));

								if (x < 1)
									x = 1;

								int noteOffsetXOffset = (int) (aNote.getOffset() * beatWidth);
								int actualStartingX = oldXMouse + noteOffsetXOffset;

								int actualHeight = (noteHeight > 7 ? noteHeight * 7 / 10
										: noteHeight) - thinNote;

								boolean pointInRect = OMNI.pointInRect(evt.getPoint(),
										actualStartingX, y - actualHeight, x, actualHeight * 2);
								if (pointInRect) {
									if (leftMouseOpenPopup) {
										consumed = true;
										LG.i("Opening popup for section#: " + phrase.secOrder);
										VibeComposerGUI.currentMidiEditorPopup = new MidiEditPopup(
												VibeComposerGUI.actualArrangement.getSections()
														.get(phrase.secOrder),
												phrase.part, phrase.partOrder);
										VibeComposerGUI.currentMidiEditorPopup
												.setSec(VibeComposerGUI.actualArrangement
														.getSections().get(phrase.secOrder));
										VibeComposerGUI.currentMidiEditorSectionIndex = phrase.secOrder;
										return;
									} else if (rightMouseOpenSectionTab) {
										if (!consumed) {
											consumed = true;
											LG.i("Opening inst. tab for section#: " + (phrase.secOrder + 1));
											SwingUtilities.invokeLater(() -> {
												VibeComposerGUI.arrSection.setSelectedIndex(phrase.secOrder + 1);
												VibeComposerGUI.instrumentTabPane.setSelectedIndex(phrase.part);
												VibeComposerGUI.arrSection.getButtons().forEach(e -> e.repaint());
												VibeComposerGUI.arrSection.repaint();
												VibeComposerGUI.switchTabPaneToScoreAfterApply = true;
												JComponent toFlash = VibeComposerGUI.getAffectedPanels(phrase.part).get(phrase.partOrder - 1).getInstrumentBox();
												Timer tmr = new Timer(250, e -> SwingUtils.flashComponentCustom(toFlash,
														(f, state) -> {
															f.setOpaque(state);
															f.setBackground(state ? Color.RED : null);
															f.repaint();
															LG.i(f + "; " + state);
														}, 100, 400));
												tmr.setRepeats(false);
												tmr.start();
											});
										}
									} else {
										if (evt.isShiftDown()) {
											// mute, instead of solo
											VibeComposerGUI
													.getPanelByOrder(phrase.part, phrase.partOrder)
													.getSoloMuter().toggleMute(true);
										} else {
											boolean unsoloAll = false;
											if (VibeComposerGUI.globalSoloMuter.soloState != State.OFF) {
												unsoloAll = VibeComposerGUI.isSingleSolo()
														&& (VibeComposerGUI
																.getPanelByOrder(phrase.part,
																		phrase.partOrder)
																.getSoloMuter().soloState == State.FULL);
											}
											if (!unsoloAll) {
												VibeComposerGUI.globalSoloMuter.toggleSolo(true);
											}

											VibeComposerGUI
													.getPanelByOrder(phrase.part, phrase.partOrder)
													.getSoloMuter().toggleSolo(true);
										}

										return;
									}

								}
							}
							oldXBeat += aNote.getRhythmValue();
							oldXMouse = (int) (Math.round(oldXBeat * beatWidth));
						}
					}
				}
			}
		});
	}

	private void reInit() {
		w = 2 * noteHeight; //width between stave lines
		ePos = 5 * noteHeight; // position of e in the treble stave
		e = ePos + noteHeight * 33;
		areaHeight = noteHeight * 77;
		noteOffset = new int[] { 0, 0, noteHeight, noteHeight, noteHeight * 2, noteHeight * 3,
				noteHeight * 3, noteHeight * 4, noteHeight * 4, noteHeight * 5, noteHeight * 5,
				noteHeight * 6 };
		this.setSize(new Dimension(ShowPanelBig.beatWidthBase, areaHeight));
		//sp.updatePanelHeight();
	}

	/**
	 * Report the current height of this canvas.
	 */
	public int getHeight() {
		return areaHeight;
	}

	/**
	 * Specify the size of the notation.
	 */
	public void setNoteHeight(int val) {
		if (val < 4) {
			repaint();
			return;
		}
		noteHeight = val;
		reInit();
		repaint();
	}

	/**
	 * Returns the current value of the booean variable thinNote.
	 */
	public int getThinNote() {
		return thinNote;
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(ShowPanelBig.beatWidthBase, areaHeight);
	}

	@Override
	public Dimension getMinimumSize() {
		return new Dimension(ShowPanelBig.beatWidthBase, areaHeight);
	}

	/**
	 * Display notes thinner than stave width or not.
	 */
	public void setThinNote(int newVal) {
		if (newVal >= 0)
			thinNote = newVal;
		repaint();
	}

	//public void update(Graphics g) {
	//	paint(g);
	//}
	@Override
	public void paintComponent(Graphics gDef) {
		//LG.i("Painting area!" + System.currentTimeMillis());
		Graphics2D g = (Graphics2D) gDef;
		super.paintComponent(g);
		//Image offScreenImage = this.createImage(this.getSize().width, this.areaHeight);
		//Graphics offScreenGraphics = g.create();
		//offScreenImage.getGraphics();
		int rectLeft, rectTop, rectRight, rectBot;
		//clear
		g.setColor(VibeComposerGUI.isDarkMode ? new Color(100, 100, 100)
				: VibeComposerGUI.panelColorLow);
		g.fillRect(0, 0, this.getSize().width, areaHeight);
		//get current maxWidth
		//paint staves
		g.setColor(Color.black);

		g.setFont(font);
		// e above middle C is at 255
		//treble
		double beatWidth = sp.beatWidth;
		int maxWidth = (int) Math.round((ShowPanelBig.maxEndTime + noteOffsetXMargin) * beatWidth);
		g.drawLine(0, (e), maxWidth, (e));
		g.drawLine(0, (e - w), maxWidth, (e - w));
		g.drawLine(0, (e - w * 2), maxWidth, (e - w * 2));
		g.drawLine(0, (e - w * 3), maxWidth, (e - w * 3));
		g.drawLine(0, (e - w * 4), maxWidth, (e - w * 4));
		//bass
		g.drawLine(0, (e + w * 2), maxWidth, (e + w * 2));
		g.drawLine(0, (e + w * 3), maxWidth, (e + w * 3));
		g.drawLine(0, (e + w * 4), maxWidth, (e + w * 4));
		g.drawLine(0, (e + w * 5), maxWidth, (e + w * 5));
		g.drawLine(0, (e + w * 6), maxWidth, (e + w * 6));
		// upper treble
		//offScreenGraphics.setColor(Color.lightGray);
		g.drawLine(0, (e - w * 7), maxWidth, (e - w * 7));
		g.drawLine(0, (e - w * 8), maxWidth, (e - w * 8));
		g.drawLine(0, (e - w * 9), maxWidth, (e - w * 9));
		g.drawLine(0, (e - w * 10), maxWidth, (e - w * 10));
		g.drawLine(0, (e - w * 11), maxWidth, (e - w * 11));
		//lower bass
		g.drawLine(0, (e + w * 9), maxWidth, (e + w * 9));
		g.drawLine(0, (e + w * 10), maxWidth, (e + w * 10));
		g.drawLine(0, (e + w * 11), maxWidth, (e + w * 11));
		g.drawLine(0, (e + w * 12), maxWidth, (e + w * 12));
		g.drawLine(0, (e + w * 13), maxWidth, (e + w * 13));
		// leger lines
		g.setColor(
				VibeComposerGUI.isDarkMode ? new Color(140, 140, 140) : new Color(200, 200, 200));
		for (int k = 0; k < maxWidth; k += 10) {
			g.drawLine(k, (e + w), k + 1, (e + w)); // middle C
			// above treble
			g.drawLine(k, (e - w * 5), k + 1, (e - w * 5));
			g.drawLine(k, (e - w * 6), k + 1, (e - w * 6));
			// above upper treble
			g.drawLine(k, (e - w * 12), k + 1, (e - w * 12));
			g.drawLine(k, (e - w * 13), k + 1, (e - w * 13));
			g.drawLine(k, (e - w * 14), k + 1, (e - w * 14));
			g.drawLine(k, (e - w * 15), k + 1, (e - w * 15));
			g.drawLine(k, (e - w * 16), k + 1, (e - w * 16));
			g.drawLine(k, (e - w * 17), k + 1, (e - w * 17));
			g.drawLine(k, (e - w * 18), k + 1, (e - w * 18));
			// below bass
			g.drawLine(k, (e + w * 7), k + 1, (e + w * 7));
			g.drawLine(k, (e + w * 8), k + 1, (e + w * 8));
			// below lower bass
			g.drawLine(k, (e + w * 14), k + 1, (e + w * 14));
			g.drawLine(k, (e + w * 15), k + 1, (e + w * 15));
			g.drawLine(k, (e + w * 16), k + 1, (e + w * 16));
			g.drawLine(k, (e + w * 17), k + 1, (e + w * 17));
			g.drawLine(k, (e + w * 18), k + 1, (e + w * 18));
		}

		double maxX = (ShowPanelBig.maxEndTime) * beatWidth;
		int minX = -1;

		double highlightX = (VibeComposerGUI.slider != null
				&& VibeComposerGUI.sliderMeasureStartTimes != null) ? maxX
						* (VibeComposerGUI.slider.getUpperValue())
						/ (double) ((VibeComposerGUI.sliderExtended > 0
								? VibeComposerGUI.sliderExtended
								: 0)
								+ VibeComposerGUI.sliderMeasureStartTimes
										.get(VibeComposerGUI.sliderMeasureStartTimes.size() - 1))
						: -1;

		Set<Integer> soloMuterHighlightedTracks = getSoloMuterHighlightedTracks();

		//g.drawLine(viewPoint.x + 4, 0, viewPoint.x + 4, areaHeight);

		//Paint each phrase in turn
		Enumeration<?> enum1 = sp.score.getPartList().elements();
		int prevColorIndex = -1;
		int samePrevColorCounter = 0;
		int oldX = 0;
		boolean mouseProcessed = mousePoint == null;
		String noteDescription = null;
		while (enum1.hasMoreElements()) {
			PartExt part = (PartExt) enum1.nextElement();
			if (part.isFillerPart()) {
				continue;
			}

			int noteColorIndex = getIndexForPartName(part.getTitle());
			//LG.d("Index: " + noteColorIndex);
			if (prevColorIndex == noteColorIndex) {
				samePrevColorCounter++;
			} else {
				samePrevColorCounter = 0;
				prevColorIndex = noteColorIndex;
			}
			if (!soloMuterHighlightedTracks.isEmpty()
					&& !soloMuterHighlightedTracks.contains(part.getTrackNumber())) {
				continue;
			}
			Color noteColor = VibeComposerGUI.instColors[noteColorIndex];
			if (samePrevColorCounter > 0) {
				Color nextColor = noteColorIndex < 4
						? VibeComposerGUI.instColors[noteColorIndex + 1]
						: Color.red;
				double percentageMix = samePrevColorCounter
						/ (double) Math.max(samePrevColorCounter,
								VibeComposerGUI.getInstList(noteColorIndex).size());

				noteColor = OMNI.mixColor(noteColor, nextColor, percentageMix / 1.5);
			}
			Enumeration<?> enum2 = part.getPhraseList().elements();
			while (enum2.hasMoreElements()) {
				PhraseExt phrase = (PhraseExt) enum2.nextElement();

				if (phrase.getHighestPitch() < 0) {
					continue;
				}

				Enumeration<?> enum3 = phrase.getNoteList().elements();
				double oldXBeat = phrase.getStartTime();
				oldX = (int) (Math.round(oldXBeat * beatWidth));
				if (minX < 0) {
					minX = oldX;
				}
				// calc the phrase rectangles
				rectLeft = oldX;
				rectTop = 100000;
				rectRight = oldX;
				rectBot = 0;

				while (enum3.hasMoreElements()) {
					Note aNote = (Note) enum3.nextElement();
					int currNote = -1;
					if (aNote.getPitchType() == Note.MIDI_PITCH)
						currNote = aNote.getPitch();
					else
						currNote = Note.freqToMidiPitch(aNote.getFrequency());

					if (currNote < 0) {
						//LG.d("(NOT) PRINTING EMPTY NOTE!");
						oldXBeat += aNote.getRhythmValue();
						oldX = (int) (Math.round(oldXBeat * beatWidth));
						continue;
					}
					//LG.d("Note: " + currNote);
					if ((currNote <= 127) && (currNote >= 0)) {
						int y = getNotePosY(currNote);

						double durationTrimmer = ShowPanelBig.trimNoteLengthBox
								.getSelectedIndex() > 0
										? noteTrimValues[ShowPanelBig.trimNoteLengthBox
												.getSelectedIndex() - 1]
										: 1000;


						int x = (int) (Math
								.round(Math.min(durationTrimmer, aNote.getDuration()) * beatWidth)); //480 ppq note
						//int xRV = (int) (Math.round(aNote.getRhythmValue() * beatWidth)); //480 ppq note
						// check if the width of the note is less than 1 so
						// that it will be displayed
						if (x < 1)
							x = 1;
						if (y < rectTop)
							rectTop = y;
						if (y > rectBot)
							rectBot = y;//update values to phrase rectangle
						//set the colour change brightness for dynamic

						int noteOffsetXOffset = (int) (aNote.getOffset() * beatWidth);
						int actualStartingX = oldX + noteOffsetXOffset;
						int actualHeight = (noteHeight > 7 ? noteHeight * 7 / 10 : noteHeight)
								- thinNote;
						boolean mouseHighlightedNote = false;
						if (!mouseProcessed && mousePoint != null) {
							boolean pointInRect = OMNI.pointInRect(mousePoint, actualStartingX,
									y - actualHeight, x, actualHeight * 2);
							if (pointInRect) {
								noteDescription = VibeComposerGUI.instNames[phrase.part] + "#"
										+ phrase.partOrder + "|" + MidiUtils
												.pitchOrDrumToString(currNote, phrase.part, false);
								mouseProcessed = true;
								mouseHighlightedNote = true;
							}
						}

						int boostColor = 0;
						if (mouseHighlightedNote) {
							boostColor = 120;
						} else if (actualStartingX <= highlightX
								&& highlightX <= actualStartingX + x) {
							boostColor = 100;
							/*LG.d("Boosted color!" + highlightX + ", act: "
									+ actualStartingX + ", x: " + x);*/
						}
						Color aC = OMNI.alphen(noteColor, boostColor + 50 + aNote.getDynamic() / 2);
						if (noteColorIndex == 2) {
							GradientPaint gp = new GradientPaint(actualStartingX, 0, aC,
									actualStartingX + x, 0, OMNI.alphen(aC, 30));
							g.setPaint(gp);
						} else {
							g.setColor(aC);
						}

						// draw note inside
						if (aNote.getPitchType() == Note.MIDI_PITCH) {
							g.fillRect(actualStartingX, y - actualHeight, x, actualHeight * 2);
						} else { // draw frequency derrived note
							int heightOffset = 7;
							for (int j = actualStartingX; j < actualStartingX + x - 4; j += 4) {
								g.drawLine(j, y - noteHeight + heightOffset, j + 2,
										y - noteHeight + heightOffset - 3);
								g.drawLine(j + 2, y - noteHeight + heightOffset - 3, j + 4,
										y - noteHeight + heightOffset);
							}
						}
						g.setColor(OMNI.alphen(Color.black, 50 + aNote.getDynamic() / 2));
						// draw note ouside
						g.drawRect(actualStartingX, y - actualHeight, x, actualHeight * 2);
						//add a sharp if required
						if ((currNote % 12) == 1 || (currNote % 12) == 3 || (currNote % 12) == 6
								|| (currNote % 12) == 8 || (currNote % 12) == 10) {
							g.setColor(aC);
							g.drawString("#", actualStartingX - 7, y + 5);
						}
					}
					oldXBeat += aNote.getRhythmValue();
					oldX = (int) (Math.round(oldXBeat * beatWidth));
					rectRight = oldX - rectLeft; //update value for phrase rectangle
				}
				// draw the phrase rectangle
				//offScreenGraphics.setColor(Color.lightGray);
				/*offScreenGraphics.setColor(Color.getHSBColor(theColours[i % maxColours][0],
						theColours[i % maxColours][1], (float) (0.9)));*/
				/*offScreenGraphics.drawRect(rectLeft - 1, rectTop - noteHeight - 1, rectRight + 1,
						rectBot - rectTop + noteHeight * 2 + 2);*/
			}
		}
		//g.drawImage(offScreenImage, 0, 0, this);
		//g.dispose();

		if (mouseProcessed && noteDescription != null) {
			g.setColor(new Color(210, 210, 210));
			g.drawString(noteDescription, mousePoint.x + 10, mousePoint.y - 10);
		}

		Point viewPoint = ShowPanelBig.areaScrollPane.getViewport().getViewPosition();
		g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(), VibeComposerGUI.isDarkMode ? 120 : 140));
		if (mousePoint != null) {
			if (minX >= 0 && ShowPanelBig.scoreBox.getSelectedIndex() == 0) {
				Point mouseLoc = SwingUtils.getMouseLocation();
				if (OMNI.mouseInComp(ShowPanelBig.areaScrollPane, mouseLoc)) {
					Double placeInScore = sp.getSequencePosFromMousePos(mouseLoc);
					g.drawLine(mouseLoc.x, 0, mouseLoc.x, areaHeight);
					if (placeInScore != null) {
						int timePos = (int) (placeInScore * VibeComposerGUI.slider.getMaximum());
						// TODO: buggy scrollpane dimension - extra 35px set when switching Big mode back
						int scrollPaneDim = VibeComposerGUI.scrollPaneDimension.height < 500 ? 400 : 600;
						int pos = viewPoint.y + scrollPaneDim * 4 / 5;
						//LG.i(pos);
						g.drawString(VibeComposerGUI.millisecondsToDetailedTimeString(timePos), mouseLoc.x + 10, Math.min(areaHeight - 5, pos));
					}
				}
			}
		}

		if (noteHeight > 7) {
			Point viewPointH = ShowPanelBig.horizontalPane.getViewport().getViewPosition();
			float usedFontHeight = Math.min(15, Float.valueOf(noteHeight * 9 / 10));
			g.setFont(font.deriveFont(Font.BOLD, usedFontHeight));

			for (int i = 15; i < 105; i++) {
				if (MidiUtils.MAJ_SCALE.contains(i % 12)) {
					int y = getNotePosY(i) + (int) (usedFontHeight / 4) + 2;
					String noteString = MidiUtils.pitchToString(i);
					g.drawString(noteString, viewPointH.x, y);
				}
			}
		}

	}

	private static Set<Integer> getSoloMuterHighlightedTracks() {
		Set<Integer> soloMuterHighlightedTracks = new HashSet<>();
		if (ShowPanelBig.soloMuterHighlight != null
				&& ShowPanelBig.soloMuterHighlight.isSelected()) {
			boolean checkMutes = VibeComposerGUI.globalSoloMuter.soloState == State.OFF;
			for (int i = 0; i < 5; i++) {
				for (InstPanel ip : VibeComposerGUI.getInstList(i)) {
					if (checkMutes) {
						if (ip.getSoloMuter().muteState == State.OFF) {
							soloMuterHighlightedTracks.add(ip.getSequenceTrack());
						}
					} else {
						if (ip.getSoloMuter().soloState != State.OFF) {
							soloMuterHighlightedTracks.add(ip.getSequenceTrack());
						}
					}

				}
			}
		}
		return soloMuterHighlightedTracks;
	}

	private int getNotePosY(int currNote) {
		// 10 - numb of octaves, 12 notes in an octave, 21
		// (octavePixelheight) is the height of
		// an octave, 156 is offset to put in position
		int octavePixelheight = noteHeight * 7;
		return (int) (((10 - currNote / 12) * octavePixelheight + (ePos))
				- noteOffset[currNote % 12]);
	}
}
