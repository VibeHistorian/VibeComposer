package org.vibehistorian.midimasterpiece.midigenerator.Panels;

import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils;
import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils.POOL;
import org.vibehistorian.midimasterpiece.midigenerator.Enums.RhythmPattern;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.ChordPart;

public class ChordPanel extends InstPanel {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 7721347698114633901L;
	
	private JTextField transitionChance = new JTextField("0", 2);
	private JTextField transitionSplit = new JTextField("625", 3);
	
	private JTextField strum = new JTextField("0", 3);
	
	private JComboBox<String> instPoolPicker = new JComboBox<String>();
	
	public void initComponents() {
		
		instrument.initInstPool(POOL.CHORD);
		MidiUtils.addAllToJComboBox(new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9",
				"11", "12", "13", "14", "15" }, midiChannel);
		midiChannel.setSelectedItem("11");
		MidiUtils.addAllToJComboBox(new String[] { "ALL", "ODD", "EVEN" }, chordSpanFill);
		
		this.add(new JLabel("#"));
		this.add(panelOrder);
		this.add(muteInst);
		this.add(lockInst);
		this.add(instrument);
		this.add(instPoolPicker);
		
		this.add(new JLabel("Fill"));
		this.add(chordSpanFill);
		this.add(stretchEnabled);
		this.add(chordNotesStretch);
		
		this.add(new JLabel("Split%"));
		this.add(transitionChance);
		this.add(new JLabel("Split(ms)"));
		this.add(transitionSplit);
		this.add(new JLabel("Strum(ms)"));
		this.add(strum);
		
		this.add(new JLabel("Start+(ms)"));
		this.add(delay);
		
		this.add(new JLabel("Transpose"));
		this.add(transpose);
		
		this.add(new JLabel("Seed"));
		this.add(patternSeed);
		this.add(new JLabel("Pattern"));
		this.add(pattern);
		this.add(new JLabel("Shift"));
		this.add(patternShift);
		
		this.add(new JLabel("Midi ch.:"));
		this.add(midiChannel);
		
		this.add(removeButton);
	}
	
	public ChordPanel(ActionListener l) {
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
	
	
	public int getPanelOrder() {
		return Integer.valueOf(panelOrder.getText());
	}
	
	public void setPanelOrder(int panelOrder) {
		this.panelOrder.setText("" + panelOrder);
		removeButton.setActionCommand("RemoveChord," + panelOrder);
	}
	
	public int getTransitionChance() {
		return Integer.valueOf(transitionChance.getText());
	}
	
	public void setTransitionChance(int transitionChance) {
		this.transitionChance.setText("" + transitionChance);
	}
	
	public int getTransitionSplit() {
		return Integer.valueOf(transitionSplit.getText());
	}
	
	public void setTransitionSplit(int transitionSplit) {
		this.transitionSplit.setText("" + transitionSplit);
	}
	
	public int getStrum() {
		return Integer.valueOf(strum.getText());
	}
	
	public void setStrum(int strum) {
		this.strum.setText("" + strum);
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