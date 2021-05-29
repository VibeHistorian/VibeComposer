package org.vibehistorian.midimasterpiece.midigenerator.Panels;

import java.awt.event.ActionListener;

import javax.swing.JLabel;

import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.MelodyPart;

public class MelodyPanel extends InstPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7861296600641561431L;

	public void initComponents() {
		MidiUtils.addAllToJComboBox(new String[] { "1" }, midiChannel);
		instrument.initInstPool(instPool);
		setInstrument(8);
		setVelocityMin(80);
		setVelocityMax(105);
		initDefaults();
		this.add(volSlider);
		/*this.add(new JLabel("#"));
		this.add(panelOrder);*/
		this.add(new JLabel("MELODY"));
		this.add(muteInst);
		this.add(lockInst);
		this.add(instrument);

		this.add(transpose);

		this.add(velocityMin);
		this.add(velocityMax);

		pauseChance.setInt(0);
		this.add(pauseChance);

		this.add(swingPercent);

		this.add(new JLabel("Seed"));
		this.add(patternSeed);

		this.add(new JLabel("Midi ch.: 1"));
		setPanelOrder(1);
	}

	public MelodyPanel(ActionListener l) {
		initComponents();
	}


	public MelodyPart toMelodyPart(int lastRandomSeed) {
		MelodyPart part = new MelodyPart();
		part.setFromPanel(this, lastRandomSeed);
		part.setOrder(getPanelOrder());
		return part;
	}

	public void setFromMelodyPart(MelodyPart part) {
		setFromInstPart(part);
		setPanelOrder(part.getOrder());
	}


	public int getPanelOrder() {
		return Integer.valueOf(panelOrder.getText());
	}

	public void setPanelOrder(int panelOrder) {
		this.panelOrder.setText("" + panelOrder);
	}
}
