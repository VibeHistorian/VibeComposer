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
		MidiUtils.addAllToJComboBox(new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9",
				"11", "12", "13", "14", "15" }, midiChannel);
		midiChannel.setSelectedItem("2");

		initDefaults();
		volSlider.setValue(70);
		this.add(volSlider);
		this.add(new JLabel("#"));
		this.add(panelOrder);
		soloMuter = new SoloMuter(3, SoloMuter.Type.SINGLE);
		this.add(soloMuter);
		this.add(muteInst);
		this.add(lockInst);
		this.add(instrument);
		this.add(removeButton);
		copyButton.addActionListener(l);
		this.add(copyButton);

		this.add(hitsPerPattern);
		this.add(chordSpan);

		this.add(new JLabel("Fill"));
		this.add(chordSpanFill);
		this.add(patternRepeat);
		//this.add(repeatableNotes);
		this.add(transpose);
		this.add(pauseChance);
		JLabel notePresetLabel = new JLabel("Note Direction");
		this.add(notePresetLabel);
		this.add(arpPattern);

		this.add(stretchEnabled);
		this.add(chordNotesStretch);
		this.add(minMaxVelSlider);


		this.add(exceptionChance);


		this.add(patternSeedLabel);
		this.add(patternSeed);
		this.add(new JLabel("Pattern"));
		this.add(pattern);
		this.add(patternShift);

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
			if (d != RhythmPattern.CUSTOM) {
				pattern.addItem(d.toString());
			}
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
		if (StringUtils.isEmpty((String) arpPattern.getSelectedItem())) {
			return ArpPattern.RANDOM;
		}
		return ArpPattern.valueOf((String) arpPattern.getSelectedItem());
	}

	public void setArpPattern(ArpPattern pattern) {
		this.arpPattern.setSelectedItem((String.valueOf(pattern.toString())));
	}

	public ArpPart toInstPart(int lastRandomSeed) {
		return toArpPart(lastRandomSeed);
	}
}
