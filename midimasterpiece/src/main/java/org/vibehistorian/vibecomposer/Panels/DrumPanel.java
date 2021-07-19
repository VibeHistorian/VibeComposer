package org.vibehistorian.vibecomposer.Panels;

import java.awt.Component;
import java.awt.event.ActionListener;
import java.util.Arrays;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.MidiUtils.POOL;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;

public class DrumPanel extends InstPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6219184197272490684L;

	private JCheckBox isVelocityPattern = new JCheckBox("Dynamic", true);
	private DrumHitsPatternPanel comboPanel = null;
	private JCheckBox useMelodyNotePattern = new JCheckBox("Melody Pattern", false);

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

		hitsPerPattern.getKnob()
				.setTickThresholds(Arrays.asList(new Integer[] { 4, 6, 8, 10, 12, 16, 24, 32 }));
		hitsPerPattern.getKnob().setTickSpacing(50);

		this.add(new JLabel("Pattern"));
		this.add(pattern);
		comboPanel = new DrumHitsPatternPanel(hitsPerPattern, pattern, patternShift, chordSpan,
				this);
		comboPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel comboPanelWrapper = new JPanel();
		comboPanelWrapper.add(comboPanel);

		this.add(comboPanelWrapper);
		this.add(patternShift);
		this.add(isVelocityPattern);

		this.add(chordSpan);
		this.add(pauseChance);

		this.add(swingPercent);
		this.add(new JLabel("Fill"));
		this.add(chordSpanFill);

		this.add(useMelodyNotePattern);


		this.add(exceptionChance);

		this.add(minMaxVelSlider);


		this.add(delay);


		this.add(patternSeedLabel);
		this.add(patternSeed);

		this.add(new JLabel("Midi ch. 10"));

		//toggleableComponents.add(useMelodyNotePattern);
		toggleableComponents.remove(patternShift);
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
		part.setUseMelodyNotePattern(getUseMelodyNotePattern());

		part.setOrder(getPanelOrder());
		return part;
	}

	public void setFromInstPart(InstPart p) {
		DrumPart part = (DrumPart) p;

		setDefaultsFromInstPart(part);
		if (part.getCustomPattern() != null && part.getCustomPattern().size() == 32) {
			comboPanel.setTruePattern(part.getCustomPattern());
		}

		setSwingPercent(part.getSwingPercent());
		setIsVelocityPattern(part.isVelocityPattern());
		setUseMelodyNotePattern(part.isUseMelodyNotePattern());

		setPanelOrder(part.getOrder());

	}

	public boolean getIsVelocityPattern() {
		return isVelocityPattern.isSelected();
	}

	public void setIsVelocityPattern(boolean isVelocityPattern) {
		this.isVelocityPattern.setSelected(isVelocityPattern);
	}

	public boolean getUseMelodyNotePattern() {
		return useMelodyNotePattern.isSelected();
	}

	public void setUseMelodyNotePattern(boolean useMelodyNotePattern) {
		this.useMelodyNotePattern.setSelected(useMelodyNotePattern);
	}


	public void transitionToPool(String[] pool) {
		instrument.changeInstPoolMapping(pool);
	}

	public DrumHitsPatternPanel getComboPanel() {
		return comboPanel;
	}

	@Override
	public InstPart toInstPart(int lastRandomSeed) {
		return toDrumPart(lastRandomSeed);
	}
}
