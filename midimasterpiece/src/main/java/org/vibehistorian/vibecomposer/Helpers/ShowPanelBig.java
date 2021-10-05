/*

<This Java Class is part of the jMusic API version 1.5, March 2004.>:37  2001

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

/*-------------------------------------------
* A jMusic tool which displays a score as a
* piano roll display on Common Practice Notation staves.
* @author Andrew Brown 
 * @version 1.0,Sun Feb 25 18:43
* ---------------------
*/
package org.vibehistorian.vibecomposer.Helpers;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Panel;

import javax.swing.JPanel;

import jm.music.data.Score;

public class ShowPanelBig extends JPanel {
	private static final long serialVersionUID = 1464206032589622048L;
	public Score score;
	protected double beatWidth; //10.0;
	private ShowAreaBig sa;
	private ShowRulerBig ruler;
	private Panel pan;
	private Frame frame;
	private int panelHeight;

	public ShowPanelBig(Frame frame, Score score) {
		this(frame, score, new Dimension(650, 400));
	}

	public ShowPanelBig(Frame frame, Score score, Dimension size) {
		super();
		// set initial wideth to show whole score if possible
		beatWidth = 1500 / (ShowAreaBig.noteOffsetXMargin + score.getEndTime());
		if (beatWidth < 1.0)
			beatWidth = 1.0;
		if (beatWidth > 256.0)
			beatWidth = 256.0;
		this.frame = frame;
		this.score = score;
		// Because the ScrollPanel can only take one componenet 
		// a panel called apn is created to hold all comoponenets
		// then only pan is added to this classes ScrollPane
		pan = new Panel();
		pan.setLayout(new BorderLayout());
		// add the score
		sa = new ShowAreaBig(this); //score, maxWidth, maxParts);
		pan.add("Center", sa);
		//add a ruler
		ruler = new ShowRulerBig(this);
		pan.add("South", ruler);
		this.setSize(size);
		updatePanelHeight();
		this.add(pan);

		//getHAdjustable().setUnitIncrement(50); //set scroll speed
		//getHAdjustable().setBlockIncrement(50);

		//setScrollPosition(0, 0);
		repaint();
	}

	// this method can be used to update the score continets of an existing ShowScore panel
	public void setScore(Score score) {
		this.score = score;
		beatWidth = this.getSize().width / score.getEndTime();
		if (beatWidth < 1.0)
			beatWidth = 1.0;
		if (beatWidth > 256.0)
			beatWidth = 256.0;
		update();
	}

	/**
	 * Used to adjust the height when the size of display is changed.
	 */
	public void updatePanelHeight() {
		panelHeight = sa.getHeight() + ruler.getHeight() + 25;
		this.setSize(new Dimension(this.getSize().width, panelHeight));
	}

	/**
	 * Report the current height of th e panel in this object.
	 */
	public int getHeight() {
		return panelHeight;
	}

	/*
	* Return the currently active ShowArea object
	*/
	public ShowAreaBig getShowArea() {
		return sa;
	}

	public void update() {
		pan.repaint();
		sa.setSize((int) Math.round(score.getEndTime() * beatWidth), sa.getHeight());
		sa.repaint();
		ruler.repaint();
		this.repaint();
		if (frame != null) {
			frame.pack();
		}
	}

}
