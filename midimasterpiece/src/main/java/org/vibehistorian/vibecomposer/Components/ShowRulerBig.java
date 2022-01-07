package org.vibehistorian.vibecomposer.Components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JComponent;

import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

/*

<This Java Class is part of the jMusic API>

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

//--------------
//third class!!
//--------------
public class ShowRulerBig extends JComponent {
	private static final long serialVersionUID = 3220771981990129313L;
	//attributes
	public static int maxHeight = 17;
	private int timeSig = 2;
	private ShowPanelBig sp;
	private Font font = new Font("Helvetica", Font.PLAIN, 10);

	public ShowRulerBig(ShowPanelBig sp) {
		super();
		this.sp = sp;
		this.setSize(ShowPanelBig.beatWidthBase, ShowRulerBig.maxHeight);
		this.setBackground(Color.lightGray);
		//this.addMouseListener(this);
		//this.addMouseMotionListener(this);
		//this.setCursor(new Cursor(13));
	}

	/**
	 * Report the height of this ruler panel.
	 */
	public int getHeight() {
		return ShowRulerBig.maxHeight;
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(ShowPanelBig.beatWidthBase, ShowRulerBig.maxHeight);
	}

	@Override
	public Dimension getMinimumSize() {
		return new Dimension(ShowPanelBig.beatWidthBase, ShowRulerBig.maxHeight);
	}

	@Override
	public void paintComponent(Graphics gDef) {
		//LG.d("Painting ruler!");
		Graphics2D g = (Graphics2D) gDef;
		double beatWidth = sp.beatWidth;
		g.setColor(new Color(180, 180, 180));
		g.fillRect(0, 0, this.getSize().width, this.getSize().height);
		g.setFont(font);
		int startOffset = MidiGenerator.START_TIME_DELAY > MidiGenerator.DBL_ERR ? 1 : 0;
		if (VibeComposerGUI.actualArrangement != null) {
			double durCounter = startOffset;
			for (Section sec : VibeComposerGUI.actualArrangement.getSections()) {
				g.setColor(new Color(100 + 15 * sec.getTypeMelodyOffset(), 150, 150, 200));
				int xLocStart = (int) Math.round(durCounter * beatWidth);
				durCounter += (sec.getSectionDuration() > 0 ? sec.getSectionDuration()
						: MidiGenerator.GENERATED_MEASURE_LENGTH) * sec.getMeasures();
				int xLocEnd = (int) Math.round(durCounter * beatWidth);
				g.fillRect(xLocStart, 0, xLocEnd - xLocStart, ShowRulerBig.maxHeight);
				//g.drawLine(xLocStart, 0, xLocEnd, ShowRulerBig.maxHeight / 2);
			}
		}
		g.setColor(Color.black);


		double maxX = (ShowPanelBig.maxEndTime) * beatWidth;

		double highlightX = (VibeComposerGUI.slider != null
				&& VibeComposerGUI.sliderMeasureStartTimes != null)
						? (maxX * VibeComposerGUI.slider.getUpperValue())
								/ (double) ((VibeComposerGUI.sliderExtended > 0
										? VibeComposerGUI.sliderExtended
										: 0)
										+ VibeComposerGUI.sliderMeasureStartTimes.get(
												VibeComposerGUI.sliderMeasureStartTimes.size() - 1))
						: -1;


		for (int i = 0; i < (ShowPanelBig.maxEndTime); i++) {
			int xLoc = (int) Math.round((i + startOffset) * beatWidth);
			if (i % timeSig == 0 && beatWidth > 10) {
				g.drawLine(xLoc, 0, xLoc, ShowRulerBig.maxHeight);
				g.drawString("" + i, xLoc + 2, ShowRulerBig.maxHeight - 2);
			} else if (i % (timeSig * 2) == 0 && beatWidth > 5) {
				g.drawLine(xLoc, 0, xLoc, ShowRulerBig.maxHeight);
				g.drawString("" + i, xLoc + 2, ShowRulerBig.maxHeight - 2);
			} else if (i % (timeSig * 4) == 0 && beatWidth > 3) {
				g.drawLine(xLoc, 0, xLoc, ShowRulerBig.maxHeight);
				g.drawString("" + i, xLoc + 2, ShowRulerBig.maxHeight - 2);
			} else {
				if (beatWidth > 10)
					g.drawLine(xLoc, ShowRulerBig.maxHeight * 15 / 20, xLoc,
							ShowRulerBig.maxHeight);
			}
		}
		if (VibeComposerGUI.actualArrangement != null) {
			double durCounter = startOffset;
			for (Section sec : VibeComposerGUI.actualArrangement.getSections()) {
				int xLocStart = (int) Math.round(durCounter * beatWidth);
				durCounter += (sec.getSectionDuration() > 0 ? sec.getSectionDuration()
						: MidiGenerator.GENERATED_MEASURE_LENGTH) * sec.getMeasures();
				int xLocEnd = (int) Math.round(durCounter * beatWidth);
				String secText = sec.getType();
				g.setColor(new Color(30, 30, 30, 150));
				g.drawString(secText, xLocStart + (xLocEnd - xLocStart) / 2 - secText.length() * 3,
						ShowRulerBig.maxHeight / 2);
				g.drawLine(xLocEnd - 1, 0, xLocEnd - 1, ShowRulerBig.maxHeight);
				g.drawLine(xLocEnd + 1, 0, xLocEnd + 1, ShowRulerBig.maxHeight);
			}
		}

		g.setColor(Color.green);
		g.drawLine((int) highlightX, ShowRulerBig.maxHeight / 2, (int) highlightX, 0);
		//g.dispose();
	}

}
