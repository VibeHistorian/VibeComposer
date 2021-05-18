package org.vibehistorian.midimasterpiece.midigenerator.Panels;

import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;

import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils;
import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils.POOL;
import org.vibehistorian.midimasterpiece.midigenerator.Enums.RhythmPattern;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.DrumPart;

public class DrumPanel extends InstPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6219184197272490684L;

	public void setPanelOrder(int panelOrder) {
		this.panelOrder.setText("" + panelOrder);
		removeButton.setActionCommand("RemoveDrum," + panelOrder);
	}


	private JCheckBox isVelocityPattern = new JCheckBox("Dynamic", true);

	public void initComponents() {

		instrument.initInstPool(POOL.DRUM);
		instrument.setInstrument(35);
		MidiUtils.addAllToJComboBox(new String[] { "10" }, midiChannel);

		initDefaults();
		this.add(new JLabel("#"));
		this.add(panelOrder);
		this.add(muteInst);
		this.add(instrument);
		this.add(new JLabel("Hits#"));
		this.add(hitsPerPattern);
		this.add(new JLabel("Chords#"));
		this.add(chordSpan);

		this.add(new JLabel("Pause%"));
		this.add(pauseChance);
		this.add(new JLabel("Exception%"));
		this.add(exceptionChance);

		this.add(new JLabel("MinVel"));
		this.add(velocityMin);
		this.add(new JLabel("MaxVel"));
		this.add(velocityMax);

		this.add(new JLabel("Swing(%)"));
		this.add(swingPercent);
		this.add(new JLabel("Delay(ms)"));
		this.add(delay);

		this.add(new JLabel("Seed"));
		this.add(patternSeed);
		this.add(new JLabel("Pattern"));
		this.add(pattern);
		this.add(isVelocityPattern);
		this.add(new JLabel("Shift"));
		this.add(patternShift);

		this.add(new JLabel("Midi ch. 10"));

		this.add(removeButton);
	}

	public DrumPanel(ActionListener l) {
		initComponents();
		for (RhythmPattern d : RhythmPattern.values()) {
			pattern.addItem(d.toString());
		}
		removeButton.addActionListener(l);
		removeButton.setActionCommand("RemoveDrum," + panelOrder);
	}

	public DrumPart toDrumPart(int lastRandomSeed) {
		DrumPart part = new DrumPart();
		part.setFromPanel(this, lastRandomSeed);

		part.setVelocityPattern(getIsVelocityPattern());
		part.setSwingPercent(getSwingPercent());

		part.setOrder(getPanelOrder());
		return part;
	}

	public void setFromDrumPart(DrumPart part) {

		setFromInstPart(part);

		setSwingPercent(part.getSwingPercent());
		setIsVelocityPattern(part.isVelocityPattern());

		setPanelOrder(part.getOrder());

	}

	public boolean getIsVelocityPattern() {
		return isVelocityPattern.isSelected();
	}

	public void setIsVelocityPattern(boolean isVelocityPattern) {
		this.isVelocityPattern.setSelected(isVelocityPattern);
	}

}
