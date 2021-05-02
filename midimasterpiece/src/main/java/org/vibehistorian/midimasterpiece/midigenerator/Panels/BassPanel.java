package org.vibehistorian.midimasterpiece.midigenerator.Panels;

import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;

import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils;
import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils.POOL;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.BassPart;

public class BassPanel extends InstPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1472358707275766819L;

	private JCheckBox useRhythm = new JCheckBox("Use rhythm", true);
	private JCheckBox alternatingRhythm = new JCheckBox("Alternating", true);

	public void initComponents() {
		MidiUtils.addAllToJComboBox(new String[] { "9" }, midiChannel);
		instPool = POOL.BASS;
		instrument.initInstPool(instPool);
		setInstrument(74);
		initDefaults();
		this.add(volSlider);
		/*this.add(new JLabel("#"));
		this.add(panelOrder);*/
		this.add(new JLabel("BASS"));
		this.add(muteInst);
		this.add(lockInst);
		this.add(instrument);

		this.add(useRhythm);
		this.add(alternatingRhythm);

		/*this.add(new JLabel("Transpose"));
		this.add(transpose);*/

		this.add(new JLabel("MinVel"));
		this.add(velocityMin);
		this.add(new JLabel("MaxVel"));
		this.add(velocityMax);

		this.add(new JLabel("Seed"));
		this.add(patternSeed);

		this.add(new JLabel("Midi ch.: 9"));
	}

	public BassPanel(ActionListener l) {
	}


	public BassPart toBassPart(int lastRandomSeed) {
		BassPart part = new BassPart();
		part.setFromPanel(this, lastRandomSeed);
		part.setOrder(getPanelOrder());
		part.setUseRhythm(getUseRhythm());
		part.setAlternatingRhythm(getAlternatingRhythm());
		return part;
	}

	public void setFromBassPart(BassPart part) {
		setFromInstPart(part);
		setPanelOrder(part.getOrder());
		setUseRhythm(part.isUseRhythm());
		setAlternatingRhythm(part.isAlternatingRhythm());
	}


	public int getPanelOrder() {
		return Integer.valueOf(panelOrder.getText());
	}

	public void setPanelOrder(int panelOrder) {
		this.panelOrder.setText("" + panelOrder);
	}

	public boolean getUseRhythm() {
		return useRhythm.isSelected();
	}

	public void setUseRhythm(boolean useRhythm) {
		this.useRhythm.setSelected(useRhythm);
	}

	public boolean getAlternatingRhythm() {
		return alternatingRhythm.isSelected();
	}

	public void setAlternatingRhythm(boolean alternatingRhythm) {
		this.alternatingRhythm.setSelected(alternatingRhythm);
	}
}
