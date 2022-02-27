package org.vibehistorian.vibecomposer.Components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.Panels.ArpPanel;
import org.vibehistorian.vibecomposer.Popups.VisualArrayPopup;

public class ArpPickerMini extends ScrollComboPanel<ArpPattern> {

	private static final long serialVersionUID = -7062864195878781225L;
	VisualArrayPopup pop = null;
	RandomIntegerListButton valueHolder = null;
	ArpPanel parent = null;

	public ArpPickerMini(ArpPanel ipParent) {
		super(true, false);
		parent = ipParent;
		FireableComboBox<ArpPattern> newBox = new FireableComboBox<ArpPattern>(this) {
			private static final long serialVersionUID = 5167762449773050710L;

			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
			}
		};
		setupBox(newBox);
		BigPickerOpener bpo = new BigPickerOpener();
		bpo.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				ArpPattern pat = getVal();
				if (pat != ArpPattern.CUSTOM && pat != ArpPattern.RANDOM) {
					valueHolder.setValues(pat.getPatternByLength(parent.getHitsPerPattern(),
							parent.getChordNotesStretch(), 1, 0));
				}
				List<Integer> originals = new ArrayList<>(valueHolder.getValues());
				LG.i("Opening with values: " + originals);
				pop = new VisualArrayPopup(-10, 10, valueHolder.getValues());
				pop.linkButton(valueHolder);
				pop.setCloseFunc(e -> {
					if (!originals.equals(pop.getValues())) {
						setVal(ArpPattern.CUSTOM);
					}
				});
			}
		});
		valueHolder = new RandomIntegerListButton("0", parent);
		pane.add(bpo);
		bpo.setBounds(0, 0, 8, 8);
		pane.setComponentZOrder(bpo, Integer.valueOf(0));
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
