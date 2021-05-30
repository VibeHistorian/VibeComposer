package org.vibehistorian.vibecomposer.Panels;

import java.awt.event.ActionListener;

import javax.swing.JLabel;

import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Parts.ArpPart;

public class ArpPanel extends InstPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6648220153568966988L;


	public void initComponents() {

		instrument.initInstPool(instPool);
		MidiUtils.addAllToJComboBox(new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9",
				"11", "12", "13", "14", "15" }, midiChannel);
		midiChannel.setSelectedItem("2");

		initDefaults();
		this.add(volSlider);
		this.add(new JLabel("#"));
		this.add(panelOrder);
		this.add(muteInst);
		this.add(lockInst);
		this.add(instrument);
		this.add(removeButton);

		this.add(hitsPerPattern);
		this.add(chordSpan);

		this.add(new JLabel("Fill"));
		this.add(chordSpanFill);
		this.add(new JLabel("Repeat#"));
		this.add(patternRepeat);
		this.add(stretchEnabled);
		this.add(chordNotesStretch);
		this.add(repeatableNotes);

		this.add(transpose);

		this.add(velocityMin);
		this.add(velocityMax);

		this.add(pauseChance);
		this.add(exceptionChance);

		this.add(new JLabel("Seed"));
		this.add(patternSeed);
		this.add(new JLabel("Pattern"));
		this.add(pattern);
		this.add(patternShift);

		this.add(new JLabel("Midi ch.:"));
		this.add(midiChannel);


	}

	public ArpPanel(ActionListener l) {
		initComponents();

		for (RhythmPattern d : RhythmPattern.values()) {
			pattern.addItem(d.toString());
		}

		removeButton.addActionListener(l);
		removeButton.setActionCommand("RemoveArp," + panelOrder);
	}


	public ArpPart toArpPart(int lastRandomSeed) {
		ArpPart part = new ArpPart();
		part.setFromPanel(this, lastRandomSeed);
		part.setOrder(getPanelOrder());
		return part;
	}

	public void setFromArpPart(ArpPart part) {
		setFromInstPart(part);
		setPanelOrder(part.getOrder());
	}


	public void setPanelOrder(int panelOrder) {
		this.panelOrder.setText("" + panelOrder);
		removeButton.setActionCommand("RemoveArp," + panelOrder);
	}
}
