package org.vibehistorian.midimasterpiece.midigenerator;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils.POOL;

public abstract class InstPanel extends JPanel {
	
	private static final long serialVersionUID = 4381939543337887617L;
	
	protected InstComboBox instrument = new InstComboBox();
	protected POOL instPool = POOL.PLUCK;
	protected JComboBox<String> chordSpanFill = new JComboBox<String>();
	
	protected JTextField hitsPerPattern = new JTextField("8", 2);
	protected JTextField chordSpan = new JTextField("1", 1);
	
	protected JTextField pauseChance = new JTextField("25", 1);
	protected JTextField exceptionChance = new JTextField("5", 1);
	protected JCheckBox repeatableNotes = new JCheckBox("Note repeat", true);
	protected JTextField patternRepeat = new JTextField("2", 1);
	
	protected JTextField transpose = new JTextField("0", 2);
	protected JTextField delay = new JTextField("0", 3);
	
	protected JLabel panelOrder = new JLabel("0");
	
	protected JTextField patternSeed = new JTextField("0", 8);
	protected JComboBox<String> pattern = new JComboBox<String>();
	protected JTextField patternShift = new JTextField("0", 1);
	
	protected JCheckBox lockInst = new JCheckBox("Lock", false);
	protected JCheckBox muteInst = new JCheckBox("Mute", false);
	
	protected JComboBox<String> midiChannel = new JComboBox<>();
	
	protected JButton removeButton = new JButton("X");
	
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
	
	public ChordSpanFill getChordSpanFill() {
		return ChordSpanFill.valueOf((String) chordSpanFill.getSelectedItem());
	}
	
	public void setChordSpanFill(ChordSpanFill chordSpanFill) {
		this.chordSpanFill.setSelectedItem(chordSpanFill.toString());
	}
	
	public int getDelay() {
		return Integer.valueOf(delay.getText());
	}
	
	public void setDelay(int delay) {
		this.delay.setText("" + delay);
	}
}
