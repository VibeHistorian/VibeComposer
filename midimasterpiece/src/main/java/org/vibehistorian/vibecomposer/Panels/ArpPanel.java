package org.vibehistorian.vibecomposer.Panels;

import java.awt.Color;
import java.awt.event.ActionListener;

import javax.swing.JLabel;

import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Helpers.OMNI;
import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;

public class ArpPanel extends InstPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6648220153568966988L;

	private ScrollComboBox<ArpPattern> arpPattern = new ScrollComboBox<>();
	private KnobPanel arpPatternRotate = new KnobPanel("Rotate", 0, 0, 8);

	public void initComponents(ActionListener l) {

		instrument.initInstPool(instPool);
		ScrollComboBox.addAll(new Integer[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 },
				midiChannel);
		midiChannel.setVal(2);

		initDefaults(l);
		volSlider.setDefaultValue(70);
		this.add(volSlider);
		this.add(panSlider);
		this.add(new JLabel("#"));
		this.add(panelOrder);
		soloMuter = new SoloMuter(3, SoloMuter.Type.SINGLE);
		addDefaultInstrumentControls();
		addDefaultPanelButtons();

		this.add(chordSpanFillPanel);

		this.add(hitsPerPattern);
		this.add(pattern);
		comboPanel = makeVisualPatternPanel();
		comboPanel.setBigModeAllowed(false);
		this.add(comboPanel);
		this.add(patternFlip);

		this.add(patternShift);
		this.add(chordSpan);

		this.add(patternRepeat);
		//this.add(repeatableNotes);
		this.add(transpose);
		this.add(pauseChance);
		JLabel notePresetLabel = new JLabel("Dir:");
		this.add(notePresetLabel);
		this.add(arpPattern);
		this.add(arpPatternRotate);

		this.add(stretchPanel);
		this.add(noteLengthMultiplier);

		this.add(minMaxVelSlider);


		this.add(exceptionChance);


		this.add(patternSeedLabel);
		this.add(patternSeed);

		this.add(new JLabel("Midi ch.:"));
		this.add(midiChannel);


		//toggleableComponents.add(arpPattern);
		//toggleableComponents.add(notePresetLabel);
		//toggleableComponents.add(repeatableNotes);


	}

	@Override
	public void addBackgroundsForKnobs() {
		super.addBackgroundsForKnobs();
		arpPatternRotate.addBackgroundWithBorder(OMNI.alphen(Color.orange, 60));
	}

	@Override
	public void toggleComponentTexts(boolean b) {
		super.toggleComponentTexts(b);
		arpPatternRotate.setShowTextInKnob(b);
	}

	public ArpPanel(ActionListener l) {
		setPartClass(ArpPart.class);
		initComponents(l);

		for (RhythmPattern d : RhythmPattern.values()) {
			pattern.addItem(d);
		}
		for (ArpPattern d : ArpPattern.values()) {
			arpPattern.addItem(d);
		}

		removeButton.addActionListener(l);
		removeButton.setActionCommand("RemoveArp," + panelOrder);
	}


	public ArpPart toArpPart(int lastRandomSeed) {
		ArpPart part = new ArpPart();
		part.setArpPattern(getArpPattern());
		part.setFromPanel(this, lastRandomSeed);
		part.setOrder(getPanelOrder());
		part.setArpPatternRotate(getArpPatternRotate());
		return part;
	}

	public void setFromInstPart(InstPart p) {
		ArpPart part = (ArpPart) p;
		setArpPattern(part.getArpPattern());
		setDefaultsFromInstPart(part);
		setPanelOrder(part.getOrder());
		setArpPatternRotate(part.getArpPatternRotate());
	}

	public ArpPattern getArpPattern() {
		return arpPattern.getVal();
	}

	public void setArpPattern(ArpPattern pattern) {
		this.arpPattern.setVal(pattern);
	}

	public ArpPart toInstPart(int lastRandomSeed) {
		return toArpPart(lastRandomSeed);
	}

	public int getArpPatternRotate() {
		return arpPatternRotate.getInt();
	}

	public void setArpPatternRotate(int val) {
		arpPatternRotate.setInt(val);
	}
}
