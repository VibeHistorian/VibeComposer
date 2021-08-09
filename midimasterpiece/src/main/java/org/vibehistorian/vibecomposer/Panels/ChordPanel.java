package org.vibehistorian.vibecomposer.Panels;

import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;
import java.util.stream.Collectors;

import javax.swing.JLabel;

import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.MidiUtils.POOL;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;

public class ChordPanel extends InstPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7721347698114633901L;

	private KnobPanel transitionChance = new KnobPanel("Split%", 0);
	private KnobPanel transitionSplit = new KnobPanel("Split<br>(ms)", 625, 0, 1000);

	private KnobPanel strum = new KnobPanel("Strum<br>(ms)", 0, 0, 1000);

	private ScrollComboBox<String> instPoolPicker = new ScrollComboBox<String>();

	public void initComponents(ActionListener l) {


		instrument.initInstPool(POOL.PLUCK);
		instPoolPicker.setSelectedItem("PLUCK");
		MidiUtils.addAllToJComboBox(new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9",
				"11", "12", "13", "14", "15" }, midiChannel);
		midiChannel.setSelectedItem("11");

		initDefaults();
		volSlider.setValue(60);
		this.add(volSlider);
		this.add(new JLabel("#"));
		this.add(panelOrder);
		soloMuter = new SoloMuter(2, SoloMuter.Type.SINGLE);
		this.add(soloMuter);
		this.add(muteInst);
		this.add(lockInst);
		this.add(instrument);
		this.add(instPoolPicker);
		this.add(removeButton);
		copyButton.addActionListener(l);
		this.add(copyButton);

		this.add(new JLabel("    Fill"));
		this.add(chordSpanFill);
		this.add(strum);
		this.add(transpose);

		this.add(hitsPerPattern);
		this.add(pattern);
		comboPanel = makeVisualPatternPanel();
		comboPanel.setBigModeAllowed(false);
		this.add(comboPanel);

		strum.getKnob().setTickThresholds(Arrays.stream(VibeComposerGUI.MILISECOND_ARRAY_STRUM)
				.mapToObj(e -> Integer.valueOf(e)).collect(Collectors.toList()));
		strum.getKnob().setTickSpacing(50);

		this.add(stretchEnabled);
		this.add(chordNotesStretch);
		this.add(transitionChance);
		this.add(transitionSplit);
		this.add(delay);

		this.add(minMaxVelSlider);

		this.add(patternSeedLabel);
		this.add(patternSeed);

		this.add(patternShift);

		this.add(new JLabel("Midi ch.:"));
		this.add(midiChannel);


		toggleableComponents.add(transitionChance);
		toggleableComponents.add(transitionSplit);

	}

	public ChordPanel(ActionListener l) {
		setPartClass(ChordPart.class);
		initComponents(l);
		for (RhythmPattern d : RhythmPattern.values()) {
			if (d != RhythmPattern.MELODY1) {
				pattern.addItem(d.toString());
			}
		}
		for (MidiUtils.POOL p : MidiUtils.POOL.values()) {
			if (p != POOL.DRUM) {
				instPoolPicker.addItem(p.toString());
			}
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
		return Integer.valueOf(transitionChance.getInt());
	}

	public void setTransitionChance(int transitionChance) {
		this.transitionChance.setInt(transitionChance);
	}

	public int getTransitionSplit() {
		return Integer.valueOf(transitionSplit.getInt());
	}

	public void setTransitionSplit(int transitionSplit) {
		this.transitionSplit.setInt(transitionSplit);
	}

	public int getStrum() {
		return Integer.valueOf(strum.getInt());
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

		setPanelOrder(part.getOrder());

	}

	public MidiUtils.POOL getInstPool() {
		return MidiUtils.POOL.valueOf((String) instPoolPicker.getSelectedItem());
	}

	public void setInstPool(MidiUtils.POOL pool) {
		instPoolPicker.setSelectedItem(pool.name());
	}

	@Override
	public InstPart toInstPart(int lastRandomSeed) {
		return toChordPart(lastRandomSeed);
	}
}
