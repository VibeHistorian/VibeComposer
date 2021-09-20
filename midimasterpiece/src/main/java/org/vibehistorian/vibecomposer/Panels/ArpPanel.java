package org.vibehistorian.vibecomposer.Panels;

import java.awt.event.ActionListener;

import javax.swing.JLabel;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;

public class ArpPanel extends InstPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6648220153568966988L;

	private ScrollComboBox<String> arpPattern = new ScrollComboBox<>();

	public void initComponents(ActionListener l) {

		instrument.initInstPool(instPool);
		MidiUtils.addAllToJComboBox(new Integer[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 },
				midiChannel);
		midiChannel.setSelectedItem("2");

		initDefaults(l);
		volSlider.setValue(70);
		this.add(volSlider);
		this.add(new JLabel("#"));
		this.add(panelOrder);
		soloMuter = new SoloMuter(3, SoloMuter.Type.SINGLE);
		addDefaultInstrumentControls();
		addDefaultPanelButtons();

		this.add(hitsPerPattern);
		this.add(pattern);
		comboPanel = makeVisualPatternPanel();
		comboPanel.setBigModeAllowed(false);
		this.add(comboPanel);
		this.add(patternFlip);

		this.add(patternShift);
		this.add(chordSpan);

		this.add(new JLabel("Fill"));
		this.add(chordSpanFill);
		this.add(fillFlip);

		this.add(patternRepeat);
		//this.add(repeatableNotes);
		this.add(transpose);
		this.add(pauseChance);
		JLabel notePresetLabel = new JLabel("Dir:");
		this.add(notePresetLabel);
		this.add(arpPattern);

		this.add(stretchPanel);

		this.add(minMaxVelSlider);
		this.add(noteLengthMultiplier);


		this.add(exceptionChance);


		this.add(patternSeedLabel);
		this.add(patternSeed);

		this.add(new JLabel("Midi ch.:"));
		this.add(midiChannel);


		//toggleableComponents.add(arpPattern);
		//toggleableComponents.add(notePresetLabel);
		//toggleableComponents.add(repeatableNotes);


	}

	public ArpPanel(ActionListener l) {
		setPartClass(ArpPart.class);
		initComponents(l);

		for (RhythmPattern d : RhythmPattern.values()) {
			pattern.addItem(d.toString());
		}
		for (ArpPattern d : ArpPattern.values()) {
			arpPattern.addItem(d.toString());
		}

		removeButton.addActionListener(l);
		removeButton.setActionCommand("RemoveArp," + panelOrder);
	}


	public ArpPart toArpPart(int lastRandomSeed) {
		ArpPart part = new ArpPart();
		part.setArpPattern(getArpPattern());
		part.setFromPanel(this, lastRandomSeed);
		part.setOrder(getPanelOrder());
		return part;
	}

	public void setFromInstPart(InstPart p) {
		ArpPart part = (ArpPart) p;
		setArpPattern(part.getArpPattern());
		setDefaultsFromInstPart(part);
		setPanelOrder(part.getOrder());
	}

	public ArpPattern getArpPattern() {
		if (StringUtils.isEmpty(arpPattern.getVal())) {
			return ArpPattern.RANDOM;
		}
		return ArpPattern.valueOf(arpPattern.getVal());
	}

	public void setArpPattern(ArpPattern pattern) {
		this.arpPattern.setSelectedItem((String.valueOf(pattern.toString())));
	}

	public ArpPart toInstPart(int lastRandomSeed) {
		return toArpPart(lastRandomSeed);
	}
}
