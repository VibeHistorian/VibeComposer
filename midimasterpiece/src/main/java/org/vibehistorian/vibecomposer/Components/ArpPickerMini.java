package org.vibehistorian.vibecomposer.Components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.Panels.ArpPanel;
import org.vibehistorian.vibecomposer.Popups.VisualArrayPopup;

public class ArpPickerMini extends ScrollComboPanel<ArpPattern> {

	private static final long serialVersionUID = -7062864195878781225L;
	VisualArrayPopup pop = null;
	RandomIntegerListButton valueHolder = null;
	ArpPanel ap = null;

	public ArpPickerMini(ArpPanel ipParent) {
		super(true, false);
		ap = ipParent;
		FireableComboBox<ArpPattern> newBox = new FireableComboBox<ArpPattern>(this) {
			private static final long serialVersionUID = 5167762449773050710L;

			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				if (getVal() == ArpPattern.RANDOM) {
					return;
				}
				int w = getWidth();
				int h = getHeight();
				List<Integer> values = valueHolder.getValues();
				int numValues = values.size();
				if (numValues < 2) {
					return;
				}
				//Collections.rotate(values, -1 * ap.getArpPatternRotate());
				int max = OMNI.maxOf(values);
				int min = OMNI.minOf(values);
				int colDivisors = numValues + 1;
				double colWidth = w / (double) colDivisors;
				int rowDivisors = max - min + 3;
				double rowHeight = h / (double) rowDivisors;
				int ovalWidth = w / 20;
				int leftX = 5;
				int bottomY = h - 3;
				for (int i = 0; i < numValues; i++) {
					int drawX = leftX + (int) (colWidth * i);
					int drawY = bottomY - (int) (rowHeight * (values.get(i) - min));

					if (i < numValues - 1) {
						g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(), 50));
						g.drawLine(drawX, drawY, drawX + (int) colWidth,
								bottomY - (int) (rowHeight * (values.get(i + 1) - min)));
					}


					g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(),
							VibeComposerGUI.isDarkMode ? 150 : 220));
					g.drawOval(drawX - ovalWidth / 2, drawY - ovalWidth / 2, ovalWidth, ovalWidth);

					//g.drawString("" + values.get(i), drawX + ovalWidth / 2, drawY - ovalWidth / 2);
				}
			}
		};
		setupBox(newBox);
		BigPickerOpener bpo = new BigPickerOpener();
		bpo.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				ArpPattern pat = getVal();
				if (pat != ArpPattern.CUSTOM && pat != ArpPattern.RANDOM) {
					valueHolder.setValues(pat.getPatternByLength(ap.getHitsPerPattern(),
							ap.getChordNotesStretch(), 1, 0));
				}
				List<Integer> originals = new ArrayList<>(valueHolder.getValues());
				LG.i("Opening with values: " + originals);
				pop = new VisualArrayPopup(-10, 10, valueHolder.getValues());
				pop.linkButton(valueHolder);
				pop.setCloseFunc(e -> {
					if (!originals.equals(pop.getValues())) {
						setVal(ArpPattern.CUSTOM);
						scb.repaint();
					}
				});
			}
		});

		scb.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				if (SwingUtilities.isMiddleMouseButton(evt) && evt.isAltDown()) {
					List<Integer> arpPattern = MidiGenerator.makeRandomArpPattern(
							ap.getHitsPerPattern(), ap.getRepeatableNotes(), new Random());
					valueHolder.setValues(arpPattern);
					scb.repaint();
					setVal(ArpPattern.CUSTOM);
				}
			}
		});
		valueHolder = new RandomIntegerListButton("0", ap);
		pane.add(bpo);
		bpo.setBounds(0, 0, 8, 8);
		pane.setComponentZOrder(bpo, Integer.valueOf(0));
		pane.setComponentZOrder(lockButt, Integer.valueOf(1));
		pane.setComponentZOrder(scb, Integer.valueOf(2));
	}

	@Override
	public void setVal(ArpPattern item) {
		super.setVal(item);
		if (item != ArpPattern.CUSTOM && item != ArpPattern.RANDOM) {
			valueHolder.setValues(item.getPatternByLength(ap.getHitsPerPattern(),
					ap.getChordNotesStretch(), 1, 0));
			scb.repaint();
		}
	}

	public List<Integer> getCustomValues() {
		return valueHolder.getValues();
	}

	public void setCustomValues(List<Integer> values) {
		valueHolder.setValues(values);
	}
}

class BigPickerOpener extends JComponent {

	private static final long serialVersionUID = -8294355665689457350L;

	int w = 8;
	int h = 8;

	public BigPickerOpener() {
		setPreferredSize(new Dimension(w, h));
		setOpaque(true);
		setSize(new Dimension(w, h));
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		g.setColor(Color.white);
		g.drawRect(0, 0, w, h);
		g.setColor(Color.black);
		g.drawString("?", 0, 0);
	}
}
