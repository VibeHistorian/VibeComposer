package org.vibehistorian.vibecomposer.Panels;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.vibehistorian.vibecomposer.InstUtils;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;

public class DrumPanel extends InstPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6219184197272490684L;

	private JCheckBox isVelocityPattern = new JCheckBox("Ghosts", true);

	public void initComponents(ActionListener l) {

		instrument.initInstPool(InstUtils.POOL.DRUM);
		instrument.setInstrument(36);
		ScrollComboBox.addAll(new Integer[] { 10 }, midiChannel);

		initDefaults(l);
		this.add(panSlider);
		this.add(new JLabel("#"));
		this.add(panelOrder);
		soloMuter = new SoloMuter(4, SoloMuter.Type.SINGLE);
		addDefaultInstrumentControls();
		addDefaultPanelButtons();

		this.add(chordSpanFillPanel);
		chordSpanFill.setScrollEnabled(false);

		// pattern business
		this.add(hitsPerPattern);

		hitsPerPattern.getKnob().setTickThresholds(Arrays.asList(
				new Integer[] { 4, 6, 8, 10, 12, 16, 24, 32, VisualPatternPanel.MAX_HITS }));
		hitsPerPattern.getKnob().setTickSpacing(50);

		pattern.setScrollEnabled(false);
		this.add(pattern);
		JButton doublerButt = new JButton("Dd");
		doublerButt.setPreferredSize(new Dimension(25, 30));
		doublerButt.setMargin(new Insets(0, 0, 0, 0));
		JButton veloTogglerButt = new JButton("V");
		veloTogglerButt.setPreferredSize(new Dimension(25, 30));
		veloTogglerButt.setMargin(new Insets(0, 0, 0, 0));
		comboPanel = makeVisualPatternPanel();
		comboPanel.linkDoubler(doublerButt);
		comboPanel.linkVelocityToggle(veloTogglerButt);
		comboPanel.setBigModeAllowed(true);
		comboPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		JPanel comboPanelWrapper = new JPanel();

		comboPanelWrapper.add(doublerButt);
		comboPanelWrapper.add(comboPanel);
		comboPanelWrapper.add(veloTogglerButt);

		this.add(comboPanelWrapper);
		this.add(patternFlip);
		this.add(patternShift);
		this.add(isVelocityPattern);

		this.add(chordSpan);
		this.add(pauseChance);

		this.add(swingPercent);

		this.add(exceptionChance);

		this.add(minMaxVelSlider);


		this.add(delay);


		this.add(patternSeedLabel);
		this.add(patternSeed);

		this.add(new JLabel("Midi ch. 10"));

		getInstrumentBox().setToolTipText("test");
		//toggleableComponents.add(useMelodyNotePattern);
		toggleableComponents.remove(patternShift);
	}

	public DrumPanel(ActionListener l) {
		setPartClass(DrumPart.class);
		initComponents(l);
		for (RhythmPattern d : RhythmPattern.values()) {
			pattern.addItem(d);
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

	public void setFromInstPart(InstPart p) {
		DrumPart part = (DrumPart) p;

		setDefaultsFromInstPart(part);

		setIsVelocityPattern(part.isVelocityPattern());

		setPanelOrder(part.getOrder());

	}

	public boolean getIsVelocityPattern() {
		return isVelocityPattern.isSelected();
	}

	public void setIsVelocityPattern(boolean val) {
		this.isVelocityPattern.setSelected(val);
	}


	public void transitionToPool(String[] pool) {
		instrument.changeInstPoolMapping(pool);
	}

	@Override
	public InstPart toInstPart(int lastRandomSeed) {
		return toDrumPart(lastRandomSeed);
	}
}
