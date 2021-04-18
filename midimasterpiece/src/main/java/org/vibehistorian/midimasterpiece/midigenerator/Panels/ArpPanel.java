package org.vibehistorian.midimasterpiece.midigenerator.Panels;

import java.awt.event.ActionListener;

import javax.swing.JLabel;

import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils;
import org.vibehistorian.midimasterpiece.midigenerator.Enums.RhythmPattern;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.ArpPart;

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
		MidiUtils.addAllToJComboBox(new String[] { "ALL", "ODD", "EVEN" }, chordSpanFill);
		
		this.add(new JLabel("#"));
		this.add(panelOrder);
		this.add(muteInst);
		this.add(lockInst);
		this.add(instrument);
		
		this.add(new JLabel("Hits#"));
		this.add(hitsPerPattern);
		this.add(new JLabel("Chords#"));
		this.add(chordSpan);
		this.add(chordSpanFill);
		this.add(new JLabel("Repeat#"));
		this.add(patternRepeat);
		this.add(repeatableNotes);
		
		this.add(new JLabel("Transpose"));
		this.add(transpose);
		
		this.add(new JLabel("Pause%"));
		this.add(pauseChance);
		this.add(new JLabel("Exception%"));
		this.add(exceptionChance);
		
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
	
	public ArpPanel(ActionListener l) {
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
	
	
	public int getPanelOrder() {
		return Integer.valueOf(panelOrder.getText());
	}
	
	public void setPanelOrder(int panelOrder) {
		this.panelOrder.setText("" + panelOrder);
		removeButton.setActionCommand("RemoveArp," + panelOrder);
	}
}
