/* --------------------
* A jMusic tool which displays a score as a
* Common Practice Notation in a window.
* @author Andrew Brown 
 * @version 1.0,Sun Feb 25 18:43
* ---------------------
*/
package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;

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
along with this program; if not, write to the Free Software
Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.

*/

//--------------
//third class!!
//--------------
public class ShowRulerBig extends JComponent {
	private static final long serialVersionUID = 3220771981990129313L;
	//attributes
	private int startX;
	private int height = 15;
	private int timeSig = 2;
	private ShowPanelBig sp;
	private Font font = new Font("Helvetica", Font.PLAIN, 10);

	public ShowRulerBig(ShowPanelBig sp) {
		super();
		this.sp = sp;
		this.setSize(1500, 15);
		this.setBackground(Color.lightGray);
		//this.addMouseListener(this);
		//this.addMouseMotionListener(this);
		//this.setCursor(new Cursor(13));
	}

	/**
	 * Report the height of this ruler panel.
	 */
	public int getHeight() {
		return height;
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(3000, 20);
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
		return new Dimension(3000, 20);
	}

	@Override
	public void paintComponent(Graphics gDef) {
		System.out.println("Painting ruler!");
		Graphics2D g = (Graphics2D) gDef;
		double beatWidth = sp.beatWidth;
		g.setColor(new Color(180, 180, 180));
		g.fillRect(0, 0, this.getSize().width, this.getSize().height);
		g.setFont(font);
		g.setColor(Color.black);
		int startOffset = 1;
		for (int i = startOffset; i < (sp.score.getEndTime()) + startOffset; i++) {
			int xLoc = (int) Math.round(i * beatWidth);
			if ((i - startOffset) % timeSig == 0 && beatWidth > 10) {
				g.drawLine(xLoc, 0, xLoc, height);
				g.drawString("" + (i - startOffset), xLoc + 2, height - 2);
			} else if ((i - startOffset) % (timeSig * 2) == 0 && beatWidth > 5) {
				g.drawLine(xLoc, 0, xLoc, height);
				g.drawString("" + (i - startOffset), xLoc + 2, height - 2);
			} else {
				if (beatWidth > 10)
					g.drawLine(xLoc, height / 2, xLoc, height);
			}
		}
		//g.dispose();
	}

	// get the position of inital mouse click
	public void mousePressed(MouseEvent e) {
		//System.out.println("Pressed");
		this.setCursor(new Cursor(10));
		startX = e.getX();
	}

	//Mouse Listener stubs
	public void mouseClicked(MouseEvent e) {
	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
	}

	public void mouseReleased(MouseEvent e) {
		this.setCursor(new Cursor(13));
		sp.update();
	}

	//mouseMotionListener stubs
	public void mouseMoved(MouseEvent e) {
	}

	public void mouseDragged(MouseEvent e) {
		//System.out.println("Dragged");
		double beatWidth = sp.beatWidth;
		beatWidth += (double) ((double) e.getX() - (double) startX) / 5.0;
		if (beatWidth < 1.0)
			beatWidth = 1.0;
		if (beatWidth > 256.0)
			beatWidth = 256.0;
		//System.out.println("beatWidth = "+beatWidth);
		sp.beatWidth = beatWidth;
		startX = e.getX();
		//sp.update();
		this.repaint();
	}
}
