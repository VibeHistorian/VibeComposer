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
	
	private InstComboBox instrument = new InstComboBox();
	private POOL instPool = POOL.PLUCK;
	
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
	private JTextField patternShift = new JTextField("0", 1);
	
	private JCheckBox lockInst = new JCheckBox("Lock", false);
	private JCheckBox muteInst = new JCheckBox("Mute", false);
	
	private JComboBox<String> midiChannel = new JComboBox<>();
	
	private JButton removeButton = new JButton("X");
	
	public void initComponents() {
		
		instrument.initInstPool(instPool);
		MidiUtils.addAllToJComboBox(new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9",
				"11", "12", "13", "14", "15" }, midiChannel);
		midiChannel.setSelectedItem("2");
		
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
	
	public int getPatternShift() {
		return Integer.valueOf(patternShift.getText());
	}
	
	public void setPatternShift(int shift) {
		patternShift.setText(String.valueOf(shift));
	}
	
	public int getInstrument() {
		return this.instrument.getInstrument();
	}
	
	public void setInstrument(int instrument) {
		this.instrument.setInstrument(instrument);
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
		return Integer.valueOf(patternRepeat.getText());
	}
	
	public void setPatternRepeat(int patternRepeat) {
		this.patternRepeat.setText(String.valueOf(patternRepeat));
	}
	
	public ArpPart toArpPart(int lastRandomSeed) {
		ArpPart part = new ArpPart(getInstrument(), getHitsPerPattern(), getChordSpan(),
				getPauseChance(), getExceptionChance(), getRepeatableNotes(), getPatternRepeat(),
				getTranspose(), getPanelOrder(),
				(getPatternSeed() != 0) ? getPatternSeed() : lastRandomSeed, getPattern(),
				getPatternShift(), getMuteInst(), getMidiChannel());
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
		setPatternShift(part.getPatternShift());
		
		setMidiChannel(part.getMidiChannel());
		
		setMuteInst(part.isMuted());
	}
	
	public int getMidiChannel() {
		return Integer.valueOf((String) midiChannel.getSelectedItem());
	}
	
	public void setMidiChannel(int midiChannel) {
		this.midiChannel.setSelectedItem("" + midiChannel);
	}
	
	public InstComboBox getInstrumentBox() {
		return instrument;
	}
	
	public POOL getInstPool() {
		return instPool;
	}
	
	public void setInstPool(POOL instPool) {
		this.instPool = instPool;
	}
}
