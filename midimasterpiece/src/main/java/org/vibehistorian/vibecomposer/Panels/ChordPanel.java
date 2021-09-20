package org.vibehistorian.vibecomposer.Panels;

import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;
import java.util.stream.Collectors;

import javax.swing.JLabel;

import org.vibehistorian.vibecomposer.InstUtils;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Enums.PatternJoinMode;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;

public class ChordPanel extends InstPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7721347698114633901L;

	private KnobPanel transitionChance = new KnobPanel("Tran-<br>sition%", 0);
	private KnobPanel transitionSplit = new KnobPanel("Split<br>(ms)", 625, 0, 1000);

	private KnobPanel strum = new KnobPanel("Strum<br>(ms)", 0, 0, 1000);

	private ScrollComboBox<PatternJoinMode> patternJoinMode = new ScrollComboBox<>();

	private ScrollComboBox<InstUtils.POOL> instPoolPicker = new ScrollComboBox<>();

	public void initComponents(ActionListener l) {


		instrument.initInstPool(InstUtils.POOL.PLUCK);
		instPoolPicker.setSelectedItem(InstUtils.POOL.PLUCK);
		MidiUtils.addAllToJComboBox(new Integer[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 },
				midiChannel);
		midiChannel.setSelectedItem(11);

		initDefaults(l);
		volSlider.setValue(60);
		this.add(volSlider);
		this.add(new JLabel("#"));
		this.add(panelOrder);
		soloMuter = new SoloMuter(2, SoloMuter.Type.SINGLE);
		addDefaultInstrumentControls();
		this.add(instPoolPicker);
		addDefaultPanelButtons();

		this.add(new JLabel("    Fill"));
		this.add(chordSpanFill);
		this.add(fillFlip);
		this.add(strum);
		this.add(transpose);

		this.add(hitsPerPattern);
		this.add(pattern);
		comboPanel = makeVisualPatternPanel();
		comboPanel.setBigModeAllowed(false);
		this.add(comboPanel);
		this.add(patternFlip);
		this.add(patternShift);

		strum.getKnob().setTickThresholds(Arrays.stream(VibeComposerGUI.MILISECOND_ARRAY_STRUM)
				.mapToObj(e -> Integer.valueOf(e)).collect(Collectors.toList()));
		strum.getKnob().setTickSpacing(50);

		this.add(stretchPanel);

		this.add(transitionChance);
		this.add(transitionSplit);
		this.add(delay);

		this.add(minMaxVelSlider);
		this.add(noteLengthMultiplier);


		this.add(patternJoinMode);

		this.add(patternSeedLabel);
		this.add(patternSeed);


		this.add(new JLabel("Midi ch.:"));
		this.add(midiChannel);


		toggleableComponents.add(transitionChance);
		toggleableComponents.add(transitionSplit);
		toggleableComponents.add(patternJoinMode);

	}

	public ChordPanel(ActionListener l) {
		setPartClass(ChordPart.class);
		initComponents(l);
		for (RhythmPattern d : RhythmPattern.values()) {
			pattern.addItem(d);
		}
		for (InstUtils.POOL p : InstUtils.POOL.values()) {
			if (p != InstUtils.POOL.DRUM) {
				instPoolPicker.addItem(p);
			}
		}
		for (PatternJoinMode pjm : PatternJoinMode.values()) {
			patternJoinMode.addItem(pjm);
		}

		instPoolPicker.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent event) {
				//if (instPoolPicker.hasFocus()) {
				if (event.getStateChange() == ItemEvent.SELECTED) {
					instrument.initInstPool(getInstPool());
					setInstPool(getInstPool());
				}
				//}
			}
		});

		removeButton.addActionListener(l);
		removeButton.setActionCommand("RemoveChord," + panelOrder);
	}

	public int getTransitionChance() {
		return transitionChance.getInt();
	}

	public void setTransitionChance(int transitionChance) {
		this.transitionChance.setInt(transitionChance);
	}

	public int getTransitionSplit() {
		return transitionSplit.getInt();
	}

	public void setTransitionSplit(int transitionSplit) {
		this.transitionSplit.setInt(transitionSplit);
	}

	public int getStrum() {
		return strum.getInt();
	}

	public void setStrum(int strum) {
		this.strum.setInt(strum);
	}

	public ChordPart toChordPart(int lastRandomSeed) {
		ChordPart part = new ChordPart();
		part.setFromPanel(this, lastRandomSeed);
		part.setTransitionChance(getTransitionChance());
		part.setTransitionSplit(getTransitionSplit());
		part.setStrum(getStrum());
		part.setPatternJoinMode(getPatternJoinMode());

		part.setInstPool(getInstPool());
		part.setOrder(getPanelOrder());
		return part;
	}

	public void setFromInstPart(InstPart p) {
		ChordPart part = (ChordPart) p;
		instrument.initInstPool(part.getInstPool());
		setInstPool(part.getInstPool());
		setDefaultsFromInstPart(part);

		setTransitionChance(part.getTransitionChance());
		setTransitionSplit(part.getTransitionSplit());
		setStrum(part.getStrum());
		setPatternJoinMode(part.getPatternJoinMode());

		setPanelOrder(part.getOrder());

	}

	public InstUtils.POOL getInstPool() {
		return instPoolPicker.getVal();
	}

	public void setInstPool(InstUtils.POOL pool) {
		instPoolPicker.setSelectedItem(pool);
	}

	@Override
	public InstPart toInstPart(int lastRandomSeed) {
		return toChordPart(lastRandomSeed);
	}

	public PatternJoinMode getPatternJoinMode() {
		return patternJoinMode.getVal();
	}

	public void setPatternJoinMode(PatternJoinMode patternJoinMode) {
		this.patternJoinMode.setSelectedItem(patternJoinMode);
	}
}
