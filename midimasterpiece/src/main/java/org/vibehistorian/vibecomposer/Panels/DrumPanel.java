package org.vibehistorian.vibecomposer.Panels;

import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;

import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.MidiUtils.POOL;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Parts.DrumPart;

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
	private DrumHitsPatternPanel comboPanel = null;

	public void initComponents(ActionListener l) {

		instrument.initInstPool(POOL.DRUM);
		instrument.setInstrument(36);
		MidiUtils.addAllToJComboBox(new String[] { "10" }, midiChannel);

		initDefaults();
		this.add(new JLabel("#"));
		this.add(panelOrder);
		soloMuter = new SoloMuter(4, SoloMuter.Type.SINGLE);
		this.add(soloMuter);
		this.add(muteInst);
		this.add(instrument);
		this.add(removeButton);
		copyButton.addActionListener(l);
		this.add(copyButton);

		// pattern business
		this.add(hitsPerPattern);
		this.add(new JLabel("Pattern"));
		this.add(pattern);
		comboPanel = new DrumHitsPatternPanel(hitsPerPattern, pattern, patternShift, this);
		this.add(comboPanel);
		this.add(patternShift);
		this.add(isVelocityPattern);

		this.add(chordSpan);
		this.add(pauseChance);

		this.add(swingPercent);
		this.add(new JLabel("Fill"));
		this.add(chordSpanFill);

		this.add(exceptionChance);

		this.add(velocityMin);
		this.add(velocityMax);


		this.add(delay);


		this.add(new JLabel("Seed"));
		this.add(patternSeed);


		this.add(new JLabel("Midi ch. 10"));


	}

	public DrumPanel(ActionListener l) {
		setPartClass(DrumPart.class);
		initComponents(l);
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
		part.setCustomPattern(comboPanel.getTruePattern());

		part.setOrder(getPanelOrder());
		return part;
	}

	public void setFromDrumPart(DrumPart part) {

		setFromInstPart(part);
		comboPanel.setTruePattern(part.getCustomPattern());
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


	public void transitionToPool(String[] pool) {
		instrument.changeInstPoolMapping(pool);
	}

}
