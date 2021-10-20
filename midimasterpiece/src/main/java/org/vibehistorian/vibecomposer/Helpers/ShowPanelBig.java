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
along with this program; if not,
see <https://www.gnu.org/licenses/>.

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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.JMusicUtilsCustom;
import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

import jm.music.data.Part;
import jm.music.data.Phrase;
import jm.music.data.Score;

public class ShowPanelBig extends JPanel {
	private static final long serialVersionUID = 1464206032589622048L;
	public Score score;
	protected double beatWidth; //10.0;
	public static final int beatWidthBaseDefault = 1500;
	public static int beatWidthBase = 1500;
	public static final List<Integer> beatWidthBases = Arrays
			.asList(new Integer[] { 1500, 2000, 2500, 3000, 4000, 6000 });
	public static int beatWidthBaseIndex = 0;
	public static int panelMaxHeight = VibeComposerGUI.scrollPaneDimension.height;
	private ShowAreaBig sa;
	private ShowRulerBig ruler;
	private JPanel pan;
	private JFrame frame;
	private int panelHeight;
	private JScrollPane areaScrollPane;
	public static CheckButton soloMuterHighlight;
	public static double maxEndTime = 10.0;

	private static JPanel scorePartPanel;
	private static CheckButton[] partsShown;
	private static JButton toggler;
	public static ScrollComboBox<Integer> scoreBox;

	public ShowPanelBig() {
		this(new Dimension(beatWidthBase, panelMaxHeight));
	}

	public ShowPanelBig(Dimension size) {
		super();

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

		// Because the ScrollPanel can only take one componenet 
		// a panel called apn is created to hold all comoponenets
		// then only pan is added to this classes ScrollPane
		partsShown = new CheckButton[5];
		scoreBox = new ScrollComboBox<Integer>();
		ScrollComboBox.addAll(new Integer[] { 0 }, scoreBox);
		scoreBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				setScore();
			}
		});
		scoreBox.setMaximumSize(new Dimension(40, ShowRulerBig.maxHeight));

		pan = new JPanel();
		setOpaque(false);
		pan.setOpaque(false);
		this.setSize(size);
		pan.setSize(size);
		pan.setLayout(new BorderLayout());

		JScrollPane horizontalPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(beatWidthBaseDefault, panelMaxHeight - 50);
			}
		};
		horizontalPane.setViewportView(pan);
		horizontalPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		horizontalPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		horizontalPane.getHorizontalScrollBar().setUnitIncrement(16);


		scorePartPanel = new JPanel();
		scorePartPanel.setLayout(new BoxLayout(scorePartPanel, BoxLayout.X_AXIS));
		scorePartPanel
				.setMaximumSize(new Dimension(ShowPanelBig.beatWidthBase, ShowRulerBig.maxHeight));

		scorePartPanel.add(new JLabel("Score History"));
		scorePartPanel.add(scoreBox);
		scorePartPanel.add(new JLabel("Included Parts"));
		for (int i = 0; i < 5; i++) {
			partsShown[i] = new CheckButton(VibeComposerGUI.instNames[i], i < 4,
					OMNI.alphen(VibeComposerGUI.instColors[i], 75));
			partsShown[i].addRunnable(new Runnable() {
				@Override
				public void run() {
					setScore();
				}
			});
			int fI = i;
			partsShown[i].addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent evt) {
					if (SwingUtilities.isMiddleMouseButton(evt)) {
						for (int j = 0; j < 5; j++) {
							partsShown[j].setSelectedRaw(j == fI);
						}
						setScore();
					}
				}
			});
			partsShown[i].setMargin(new Insets(0, 0, 0, 0));
			scorePartPanel.add(partsShown[i]);
		}
		{
			toggler = new JButton("All");
			toggler.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					boolean turnedOn = false;
					for (CheckButton c : partsShown) {
						if (!c.isSelected()) {
							c.setSelectedRaw(true);
							turnedOn = true;
						}
					}
					if (!turnedOn) {
						for (CheckButton c : partsShown) {
							c.setSelectedRaw(false);
						}
					}
					setScore();
				}
			});
			scorePartPanel.add(toggler);

			soloMuterHighlight = new CheckButton("Highlight Audible", false);
			soloMuterHighlight.addRunnable(new Runnable() {

				@Override
				public void run() {
					if (soloMuterHighlight.isSelected()) {
						/*for (int i = 0; i < 5; i++) {
							partsShown[i].setSelectedRaw(true);
							partsShown[i].setEnabled(false);
						}*/
						setScore();
						//toggler.setEnabled(false);
					} else {
						/*for (int i = 0; i < 5; i++) {
							partsShown[i].setEnabled(true);
						}*/
						//toggler.setEnabled(true);
						setScore();
					}

				}
			});
			scorePartPanel.add(soloMuterHighlight);
		}

		// add the score
		sa = new ShowAreaBig(this); //score, maxWidth, maxParts);
		sa.setVisible(true);

		/*sa.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				if (SwingUtilities.isLeftMouseButton(evt)) {
					Point xy = MouseInfo.getPointerInfo().getLocation();
					SwingUtilities.convertPointFromScreen(xy, sa);
					double percentage = xy.getX() / sa.getWidth();
					VibeComposerGUI.isDragging = true;
					VibeComposerGUI.slider.setUpperValue(
							(int) (percentage * VibeComposerGUI.slider.getMaximum()));
					VibeComposerGUI.vibeComposerGUI.savePauseInfo();
					VibeComposerGUI.vibeComposerGUI
							.midiNavigate(VibeComposerGUI.slider.getUpperValue());
					VibeComposerGUI.isDragging = false;
				}
			}
		});*/

		JPanel areaPanel = new JPanel();
		areaPanel.setMaximumSize(new Dimension(beatWidthBases.get(beatWidthBases.size() - 1),
				ShowAreaBig.areaHeight));
		areaPanel.add(sa);

		areaScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(ShowPanelBig.beatWidthBase, 330);
			}
		};
		areaScrollPane.setViewportView(areaPanel);
		areaScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		areaScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		areaScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		areaScrollPane.addMouseWheelListener(new MouseWheelListener() {

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (e.isAltDown()) {
					sa.setNoteHeight(
							ShowAreaBig.noteHeight + ((e.getWheelRotation() > 0) ? -1 : 1));
					setScore();

					//areaScrollPane.getVerticalScrollBar().setVisible(true);
					areaScrollPane.repaint();
				} else if (e.isControlDown()) {
					beatWidthBaseIndex = OMNI.clamp(
							beatWidthBaseIndex + ((e.getWheelRotation() > 0) ? -1 : 1), 0,
							beatWidthBases.size() - 1);
					ShowPanelBig.beatWidthBase = beatWidthBases.get(beatWidthBaseIndex);
					setScore();
					areaScrollPane.repaint();
				} else {
					// nothing
				}
			}
		});

		areaPanel.setVisible(true);
		pan.add("Center", areaScrollPane);
		//add a ruler
		ruler = new ShowRulerBig(this);
		ruler.setVisible(true);
		JPanel rulerPanel = new JPanel();
		rulerPanel
				.setMaximumSize(new Dimension(ShowPanelBig.beatWidthBase, ShowRulerBig.maxHeight));
		rulerPanel.add(ruler);
		rulerPanel.setVisible(true);
		pan.add("South", rulerPanel);
		updatePanelHeight();
		scorePartPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		this.add(scorePartPanel);
		this.add(horizontalPane);

		//getHAdjustable().setUnitIncrement(50); //set scroll speed
		//getHAdjustable().setBlockIncrement(50);


		soloMuterHighlight.setSelected(true);
		repaint();
	}

	public void setScore() {
		if (MidiGenerator.LAST_SCORES.isEmpty()) {
			return;
		}
		int selectableScore = OMNI.clamp(scoreBox.getVal(), 0,
				MidiGenerator.LAST_SCORES.size() - 1);
		if (scoreBox.getLastVal() < MidiGenerator.LAST_SCORES_LIMIT - 1
				&& MidiGenerator.LAST_SCORES.size() > scoreBox.getLastVal() + 1) {
			scoreBox.addItem(scoreBox.getLastVal() + 1);
		}
		setScore(MidiGenerator.LAST_SCORES.get(selectableScore));
	}


	public void setScore(Score score) {
		List<Integer> includedParts = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			if (partsShown[i].isSelected()) {
				includedParts.add(i);
			}
		}
		setScore(score, includedParts);
	}

	// this method can be used to update the score continets of an existing ShowScore panel
	public void setScore(Score score, List<Integer> includedParts) {

		Score scrCopy = JMusicUtilsCustom.scoreCopy(score);

		List<Part> partsToRemove = new ArrayList<>();
		for (Object p : scrCopy.getPartList()) {
			Part part = (Part) p;
			int partIndex = ShowAreaBig.getIndexForPartName(part.getTitle());
			if (!includedParts.contains(partIndex)) {
				partsToRemove.add(part);
				continue;
			}
			List<Phrase> phrasesToRemove = new ArrayList<>();
			for (Object vec : part.getPhraseList()) {
				Phrase ph = (Phrase) vec;
				if (ph.getHighestPitch() < 0) {
					phrasesToRemove.add(ph);
				}

			}
			phrasesToRemove.forEach(e -> part.removePhrase(e));
		}
		partsToRemove.forEach(e -> scrCopy.removePart(e));
		maxEndTime = score.getEndTime();
		this.score = scrCopy;
		beatWidth = beatWidthBase / (ShowAreaBig.noteOffsetXMargin + ShowPanelBig.maxEndTime);
		if (beatWidth < 1.0)
			beatWidth = 1.0;
		if (beatWidth > 256.0)
			beatWidth = 256.0;
		update();
		//System.out.println();
		//areaScrollPane.getVerticalScrollBar().setValue(50);
		repaint();
	}

	/**
	 * Used to adjust the height when the size of display is changed.
	 */
	public void updatePanelHeight() {
		panelHeight = panelMaxHeight;
		this.setSize(new Dimension(beatWidthBaseDefault, panelHeight));
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
		int sizeX = (int) Math
				.round((ShowAreaBig.noteOffsetXMargin + ShowPanelBig.maxEndTime) * beatWidth);
		sa.setSize(sizeX, panelHeight);
		sa.repaint();
		ruler.setSize(sizeX, ShowRulerBig.maxHeight);
		ruler.repaint();
		this.repaint();
		if (frame != null) {
			frame.pack();
		}
	}

}
