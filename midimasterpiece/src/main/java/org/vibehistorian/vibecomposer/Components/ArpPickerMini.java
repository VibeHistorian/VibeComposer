package org.vibehistorian.vibecomposer.Components;

import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Panels.ArpPanel;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Popups.VisualArrayPopup;
import org.vibehistorian.vibecomposer.SwingUtils;
import org.vibehistorian.vibecomposer.UITheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
						g.setColor(OMNI.alphen(UITheme.uiColor(), 50));
						g.drawLine(drawX, drawY, drawX + (int) colWidth,
								bottomY - (int) (rowHeight * (values.get(i + 1) - min)));
					}


					g.setColor(OMNI.alphen(UITheme.uiColor(),
							UITheme.isDarkMode ? 150 : 220));
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
				int max = OMNI.maxOf(originals);
				int min = OMNI.minOf(originals);
				pop = new VisualArrayPopup(Math.min(-3, min - 2), Math.max(8, max + 2),
						valueHolder.getValues(), true);
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
				if (SwingUtilities.isMiddleMouseButton(evt)
						&& (evt.getModifiersEx() & MouseEvent.ALT_DOWN_MASK) != 0) {
					setVal(ArpPattern.RANDOM);
					prepareInteraction(evt.isControlDown());
					if (evt.isControlDown()) {
						setRandomCustomValuesGlobal();
					} else {
						setRandomCustomValues();
					}
				}
			}
		});
		valueHolder = new RandomIntegerListButton("0", ap);
		pane.add(bpo);
		bpo.setBounds(0, 0, 8, 8);
		pane.setComponentZOrder(bpo, 0);
		pane.setComponentZOrder(lockButt, 1);
		pane.setComponentZOrder(scb, 2);
	}

	protected void setRandomCustomValues() {
		List<Integer> arpPattern = MidiGenerator.makeRandomArpPattern(ap.getHitsPerPattern(), true,
				new Random());
		setCustomValues(arpPattern);
		setVal(ArpPattern.CUSTOM);
		scb.repaint();
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

	public void setRandomCustomValuesGlobal() {
		InstPanel instParent = SwingUtils.getInstParent(this);
		if (instParent == null) {
			setRandomCustomValues();
			return;
		}
		instParent.getAllComponentsLike(this, ArpPickerMini.class).forEach(e -> {
			if (e.isEnabled()) {
				e.setRandomCustomValues();
			}
		});
	}
}

