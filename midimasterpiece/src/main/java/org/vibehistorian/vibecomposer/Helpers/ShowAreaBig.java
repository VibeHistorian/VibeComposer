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
along with this program; if not, write to the Free Software
Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.

*/

/**
 * A java canvas object which displays a score as a
 * psudo Common Practice Notation in a window.
 * Used as part of jMusic ShowScore, and other apps.
 * @author Andrew Brown
 */
package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JComponent;

import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.SoloMuter.State;

import jm.music.data.Note;
import jm.music.data.Phrase;

//--------------
//second class!!
//--------------
public class ShowAreaBig extends JComponent {
	private static final long serialVersionUID = -7925170286317013689L;
	//attributes
	private int oldX;
	public static int noteHeight = 5;
	private int w = 2 * noteHeight; //width between stave lines
	private int ePos = 5 * noteHeight; // position of e in the treble stave
	private int e = ePos + noteHeight * 33;
	public static int areaHeight = 400;
	private int[] noteOffset = { 0, 0, noteHeight, noteHeight, noteHeight * 2, noteHeight * 3,
			noteHeight * 3, noteHeight * 4, noteHeight * 4, noteHeight * 5, noteHeight * 5,
			noteHeight * 6 };
	private Font font = new Font("Helvetica", Font.PLAIN, 10);
	private ShowPanelBig sp;
	private double beatWidth;
	private int thinNote = 2; // thin value
	public static final int noteOffsetXMargin = 10;

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
	 *
	 * @param int The new note height
	 */
	public void setNoteHeight(int val) {
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

	/**
	 * Return the minimum size that the knob would like to be.
	 * This is the same size as the preferred size so the
	 * knob will be of a fixed size.
	 *
	 * @return the minimum size of the JKnob.
	 */
	@Override
	public Dimension getMinimumSize() {
		return new Dimension(ShowPanelBig.beatWidthBase, areaHeight);
	}

	/**
	 * Display notes thinner than stave width or not.
	 *
	 * @param int The thinNote value ( 0, 1, 2, 3 etc.)
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
		//System.out.println("Painting area!");
		Graphics2D g = (Graphics2D) gDef;
		super.paintComponent(g);
		//Image offScreenImage = this.createImage(this.getSize().width, this.areaHeight);
		//Graphics offScreenGraphics = g.create();
		//offScreenImage.getGraphics();
		int rectLeft, rectTop, rectRight, rectBot;
		//clear
		g.setColor(
				VibeComposerGUI.isDarkMode ? new Color(100, 100, 100) : new Color(180, 180, 180));
		g.fillRect(0, 0, this.getSize().width, areaHeight);
		//get current maxWidth
		//paint staves
		g.setColor(Color.black);
		g.setFont(font);
		// e above middle C is at 255
		//treble
		beatWidth = sp.beatWidth;
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
		g.setColor(new Color(140, 140, 140));
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

		double highlightX = (VibeComposerGUI.slider != null
				&& VibeComposerGUI.sliderMeasureStartTimes != null)
						? maxX * VibeComposerGUI.slider.getUpperValue()
								/ (double) VibeComposerGUI.sliderMeasureStartTimes
										.get(VibeComposerGUI.sliderMeasureStartTimes.size() - 1)
						: -1;

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

		//Paint each phrase in turn
		Enumeration<?> enum1 = sp.score.getPartList().elements();
		int prevColorIndex = -1;
		int samePrevColorCounter = 0;
		while (enum1.hasMoreElements()) {
			PartExt part = (PartExt) enum1.nextElement();

			int noteColorIndex = getIndexForPartName(part.getTitle());
			//System.out.println("Index: " + noteColorIndex);
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
				double percentageMix = (samePrevColorCounter
						/ (double) VibeComposerGUI.getInstList(noteColorIndex).size()) / 1.5;

				noteColor = OMNI.mixColor(noteColor, nextColor, percentageMix);
			}
			Enumeration<?> enum2 = part.getPhraseList().elements();
			while (enum2.hasMoreElements()) {
				Phrase phrase = (Phrase) enum2.nextElement();

				if (phrase.getHighestPitch() < 0) {
					continue;
				}

				Enumeration<?> enum3 = phrase.getNoteList().elements();
				double oldXBeat = phrase.getStartTime();
				oldX = (int) (Math.round(oldXBeat * beatWidth));
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
						//System.out.println("(NOT) PRINTING EMPTY NOTE!");
						oldXBeat += aNote.getRhythmValue();
						oldX = (int) (Math.round(oldXBeat * beatWidth));
						continue;
					}
					//System.out.println("Note: " + currNote);
					if ((currNote <= 127) && (currNote >= 0)) {
						// 10 - numb of octaves, 12 notes in an octave, 21
						// (octavePixelheight) is the height of
						// an octave, 156 is offset to put in position
						int octavePixelheight = noteHeight * 7;
						int y = (int) (((10 - currNote / 12) * octavePixelheight + (ePos))
								- noteOffset[currNote % 12]);
						int x = (int) (Math.round(aNote.getDuration() * beatWidth)); //480 ppq note
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


						int boostColor = 0;
						if (actualStartingX <= highlightX && highlightX <= actualStartingX + x) {
							boostColor = 100;
							/*System.out.println("Boosted color!" + highlightX + ", act: "
									+ actualStartingX + ", x: " + x);*/
						}
						g.setColor(
								OMNI.alphen(noteColor, boostColor + 50 + aNote.getDynamic() / 2));
						// draw note inside
						if (aNote.getPitchType() == Note.MIDI_PITCH) {
							g.fillRect(actualStartingX, y - noteHeight + thinNote, x,
									noteHeight * 2 - 2 * thinNote);
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
						g.drawRect(actualStartingX, y - noteHeight + thinNote, x,
								noteHeight * 2 - 2 * thinNote);
						//add a sharp if required
						if ((currNote % 12) == 1 || (currNote % 12) == 3 || (currNote % 12) == 6
								|| (currNote % 12) == 8 || (currNote % 12) == 10) {
							g.setColor(OMNI.alphen(noteColor, 70));
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
	}
}
