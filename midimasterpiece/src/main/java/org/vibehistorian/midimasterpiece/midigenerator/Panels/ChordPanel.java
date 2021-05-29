package org.vibehistorian.midimasterpiece.midigenerator.Panels;

import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JComboBox;
import javax.swing.JLabel;

import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils;
import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils.POOL;
import org.vibehistorian.midimasterpiece.midigenerator.Enums.RhythmPattern;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.ChordPart;

public class ChordPanel extends InstPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7721347698114633901L;

	private NumPanel transitionChance = new NumPanel("Split%", 0);
	private NumPanel transitionSplit = new NumPanel("Split(ms)", 625, 0, 1000);

	private NumPanel strum = new NumPanel("Strum(ms)", 0, 0, 1000);

	private JComboBox<String> instPoolPicker = new JComboBox<String>();

	public void initComponents() {


		instrument.initInstPool(POOL.PLUCK);
		instPoolPicker.setSelectedItem("PLUCK");
		MidiUtils.addAllToJComboBox(new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9",
				"11", "12", "13", "14", "15" }, midiChannel);
		midiChannel.setSelectedItem("11");

		initDefaults();
		this.add(volSlider);
		this.add(new JLabel("#"));
		this.add(panelOrder);
		this.add(muteInst);
		this.add(lockInst);
		this.add(instrument);
		this.add(instPoolPicker);
		this.add(removeButton);

		this.add(new JLabel("    Fill"));
		this.add(chordSpanFill);
		this.add(stretchEnabled);
		this.add(chordNotesStretch);

		this.add(transitionChance);
		this.add(transitionSplit);
		this.add(strum);

		this.add(delay);
		this.add(transpose);

		this.add(velocityMin);
		this.add(velocityMax);

		this.add(new JLabel("Seed"));
		this.add(patternSeed);
		this.add(new JLabel("Pattern"));
		this.add(pattern);
		this.add(patternShift);

		this.add(new JLabel("Midi ch.:"));
		this.add(midiChannel);

		toggleableComponents.add(transitionChance);
		toggleableComponents.add(transitionSplit);
		toggleableComponents.add(strum);

	}

	public ChordPanel(ActionListener l) {
		initComponents();
		for (RhythmPattern d : RhythmPattern.values()) {
			pattern.addItem(d.toString());
		}
		for (MidiUtils.POOL p : MidiUtils.POOL.values()) {
			instPoolPicker.addItem(p.toString());
		}

		instPoolPicker.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent event) {
				if (instPoolPicker.hasFocus()) {
					if (event.getStateChange() == ItemEvent.SELECTED) {
						instrument.initInstPool(getInstPool());
						setInstPool(getInstPool());
					}
				}
			}
		});

		removeButton.addActionListener(l);
		removeButton.setActionCommand("RemoveChord," + panelOrder);
	}

	public void setPanelOrder(int panelOrder) {
		this.panelOrder.setText("" + panelOrder);
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

	public void setFromChordPart(ChordPart part) {
		instrument.initInstPool(part.getInstPool());
		setInstPool(part.getInstPool());
		setFromInstPart(part);

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


}
