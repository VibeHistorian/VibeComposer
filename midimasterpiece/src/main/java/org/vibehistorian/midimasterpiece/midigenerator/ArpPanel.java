package org.vibehistorian.midimasterpiece.midigenerator;

import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils.POOL;

public class ArpPanel extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6648220153568966988L;
	
	private JComboBox<String> instrument = new JComboBox<String>();
	
	private JTextField hitsPerPattern = new JTextField("8", 2);
	private JTextField chordSpan = new JTextField("1", 1);
	
	private JTextField pauseChance = new JTextField("25", 1);
	private JTextField exceptionChance = new JTextField("5", 1);
	private JCheckBox repeatableNotes = new JCheckBox("Note repeat", true);
	private JTextField patternRepeat = new JTextField("2", 1);
	
	private JTextField transpose = new JTextField("0", 2);
	
	private JLabel panelOrder = new JLabel("0");
	
	private JTextField patternSeed = new JTextField("0", 8);
	private JComboBox<String> pattern = new JComboBox<String>();
	private JTextField patternRotation = new JTextField("0", 1);
	
	private JCheckBox lockInst = new JCheckBox("Lock", false);
	private JCheckBox muteInst = new JCheckBox("Mute", false);
	
	private JButton removeButton = new JButton("X");
	
	public void initComponents() {
		
		MidiUtils.addAllToJComboBox(MidiUtils.INST_POOLS.get(MidiUtils.POOL.PLUCK), instrument);
		
		this.add(new JLabel("#"));
		this.add(panelOrder);
		this.add(muteInst);
		this.add(lockInst);
		this.add(instrument);
		
		this.add(new JLabel("Arp#"));
		this.add(hitsPerPattern);
		this.add(new JLabel("Chords#"));
		this.add(chordSpan);
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
		this.add(new JLabel("Rot."));
		this.add(patternRotation);
		
		this.add(removeButton);
	}
	
	public ArpPanel(ActionListener l) {
		for (RhythmPattern d : RhythmPattern.values()) {
			pattern.addItem(d.toString());
		}
		
		removeButton.addActionListener(l);
		removeButton.setActionCommand("RemoveArp," + panelOrder);
	}
	
	public int getHitsPerPattern() {
		return Integer.valueOf(hitsPerPattern.getText());
	}
	
	public void setHitsPerPattern(int hitsPerPattern) {
		this.hitsPerPattern.setText(String.valueOf(hitsPerPattern));
	}
	
	public int getChordSpan() {
		return Integer.valueOf(chordSpan.getText());
	}
	
	public void setChordSpan(int chordSpan) {
		this.chordSpan.setText(String.valueOf(chordSpan));
	}
	
	public int getPauseChance() {
		return Integer.valueOf(pauseChance.getText());
	}
	
	public void setPauseChance(int pauseChance) {
		this.pauseChance.setText(String.valueOf(pauseChance));
	}
	
	public int getExceptionChance() {
		return Integer.valueOf(exceptionChance.getText());
	}
	
	public void setExceptionChance(int exceptionChance) {
		this.exceptionChance.setText(String.valueOf(exceptionChance));
	}
	
	public int getPanelOrder() {
		return Integer.valueOf(panelOrder.getText());
	}
	
	public void setPanelOrder(int panelOrder) {
		this.panelOrder.setText("" + panelOrder);
		removeButton.setActionCommand("RemoveArp," + panelOrder);
	}
	
	public int getTranspose() {
		return Integer.valueOf(transpose.getText());
	}
	
	public void setTranspose(int transpose) {
		this.transpose.setText("" + transpose);
	}
	
	public int getPatternSeed() {
		return Integer.valueOf(patternSeed.getText());
	}
	
	public void setPatternSeed(int patternSeed) {
		this.patternSeed.setText(String.valueOf(patternSeed));
	}
	
	public RhythmPattern getPattern() {
		return RhythmPattern.valueOf((String) pattern.getSelectedItem());
	}
	
	public void setPattern(RhythmPattern pattern) {
		this.pattern.setSelectedItem((String.valueOf(pattern.toString())));
	}
	
	public int getPatternRotation() {
		return Integer.valueOf(patternRotation.getText());
	}
	
	public void setPatternRotation(int rotation) {
		patternRotation.setText(String.valueOf(rotation));
	}
	
	public int getInstrument() {
		return MidiUtils.getInstByIndex(instrument.getSelectedIndex(),
				MidiUtils.INST_POOLS.get(POOL.PLUCK));
	}
	
	public void setInstrument(int instrument) {
		MidiUtils.selectJComboBoxByInst(this.instrument, MidiUtils.INST_POOLS.get(POOL.PLUCK),
				instrument);
	}
	
	public boolean getLockInst() {
		return lockInst.isSelected();
	}
	
	public void setLockInst(boolean selected) {
		this.lockInst.setSelected(selected);
	}
	
	public boolean getMuteInst() {
		return muteInst.isSelected();
	}
	
	public void setMuteInst(boolean selected) {
		this.muteInst.setSelected(selected);
	}
	
	public boolean getRepeatableNotes() {
		return repeatableNotes.isSelected();
	}
	
	public void setRepeatableNotes(boolean repeatableNotes) {
		this.repeatableNotes.setSelected(repeatableNotes);
	}
	
	public int getPatternRepeat() {
		return Integer.valueOf(patternRotation.getText());
	}
	
	public void setPatternRepeat(int patternRepeat) {
		this.patternRepeat.setText(String.valueOf(patternRepeat));
	}
	
	public ArpPart toArpPart(int lastRandomSeed) {
		ArpPart part = new ArpPart(getInstrument(), getHitsPerPattern(), getChordSpan(),
				getPauseChance(), getExceptionChance(), getRepeatableNotes(), getPatternRepeat(),
				getTranspose(), getPanelOrder(),
				(getPatternSeed() != 0) ? getPatternSeed() : lastRandomSeed, getPattern(),
				getPatternRotation());
		return part;
	}
	
	public void setFromArpPart(ArpPart part) {
		setInstrument(part.getInstrument());
		setHitsPerPattern(part.getHitsPerPattern());
		setChordSpan(part.getChordSpan());
		setPauseChance(part.getPauseChance());
		setExceptionChance(part.getExceptionChance());
		setRepeatableNotes(part.isRepeatableNotes());
		setPatternRepeat(part.getPatternRepeat());
		setTranspose(part.getTranspose());
		setPanelOrder(part.getOrder());
		
		setPatternSeed(part.getPatternSeed());
		setPattern(part.getPattern());
		setPatternRotation(part.getPatternRotation());
	}
}
