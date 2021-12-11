/*

<This Java Class is part of the jMusic API version 1.5, March 2004.>:39  2001

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


/* --------------------
* A jMusic tool which displays a score as a
* piano roll dispslay on Common Practice Notation staves.
* @author Andrew Brown 
 * @version 1.0,Sun Feb 25 18:43
* ---------------------
*/

/**
 * The tool displays a jMusic class as music notation. To use it write:
 * new ShowScore(scoreName);
 * Where scoreName is the jMusic Score object.
 * Alternately:
 * new ShowScore(scoreName, xpos, ypos);
 * Where xpos and ypos are intergers specifying the topleft position of the window.
 * This is useful if you want to use DrawScore in conjunction with some other GUI interface
 * which is already positioned in the top left corner of the screen.
 */

package org.vibehistorian.vibecomposer.Components;

import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JFrame;

import jm.music.data.Score;
import jm.util.View;

public class ShowScoreBig extends JFrame implements WindowListener, ActionListener {

	private static final long serialVersionUID = -8303709494026975745L;
	Score score;

	public ShowScoreBig(Score score) {
		this(score, 0, 0);
	}

	public ShowScoreBig(Score score, int xPos, int yPos) {
		super("jMusic Show: '" + score.getTitle() + "'");
		this.score = score;
		//register the closebox event
		this.addWindowListener(this);

		//add a scroll pane
		ShowPanelBig sp = new ShowPanelBig();
		this.setSize(ShowPanelBig.beatWidthBase, 500);

		View.show(score);

		this.add(sp);
		// menus
		MenuBar menus = new MenuBar();
		Menu fileMenu = new Menu("Show", true);


		MenuItem line = new MenuItem("-");
		fileMenu.add(line);


		MenuItem line2 = new MenuItem("-");
		fileMenu.add(line2);


		MenuItem quit = new MenuItem("Quit", new MenuShortcut(KeyEvent.VK_Q));
		quit.addActionListener(this);
		fileMenu.add(quit);

		menus.add(fileMenu);
		this.setMenuBar(menus);

		//construct and display
		this.pack();
		this.setLocation(xPos, yPos);
		this.show();
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		// Auto-generated method stub

	}

	@Override
	public void windowOpened(WindowEvent e) {
		// Auto-generated method stub

	}

	// Deal with the window closebox
	public void windowClosing(WindowEvent we) {
		this.dispose(); //System.exit(0);
	}

	@Override
	public void windowClosed(WindowEvent e) {
		// Auto-generated method stub

	}

	@Override
	public void windowIconified(WindowEvent e) {
		// Auto-generated method stub

	}

	@Override
	public void windowDeiconified(WindowEvent e) {
		// Auto-generated method stub

	}

	@Override
	public void windowActivated(WindowEvent e) {
		// Auto-generated method stub

	}

	@Override
	public void windowDeactivated(WindowEvent e) {
		// Auto-generated method stub

	}

}
