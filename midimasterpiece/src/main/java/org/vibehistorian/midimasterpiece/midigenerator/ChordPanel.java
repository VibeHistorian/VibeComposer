package org.vibehistorian.midimasterpiece.midigenerator;

import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class ChordPanel extends JPanel {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 7721347698114633901L;
	
	private JLabel chordPanelOrder = new JLabel("0");
	
	private JComboBox<String> instrument = new JComboBox<String>();
	
	private JTextField transitionChance = new JTextField("0", 2);
	private JTextField transitionSplit = new JTextField("625", 3);
	
	private JTextField strum = new JTextField("0", 3);
	private JTextField delay = new JTextField("0", 3);
	
	private JTextField transpose = new JTextField("0", 2);
	
	private JTextField patternSeed = new JTextField("0", 8);
	private JComboBox<String> pattern = new JComboBox<String>();
	private JTextField patternRotation = new JTextField("0", 1);
	
	private JCheckBox lockInst = new JCheckBox("Lock", false);
	private JCheckBox muteInst = new JCheckBox("Mute", false);
	private JComboBox<String> instPool = new JComboBox<String>();
	
	private JButton removeButton = new JButton("X");
	
	public void initComponents() {
		
		MidiUtils.addAllToJComboBox(MidiUtils.INST_POOLS.get(MidiUtils.POOL.CHORD), instrument);
		
		this.add(new JLabel("#"));
		this.add(chordPanelOrder);
		this.add(muteInst);
		this.add(lockInst);
		this.add(instrument);
		this.add(instPool);
		this.add(new JLabel("Transition%"));
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
		this.add(new JLabel("Rot."));
		this.add(patternRotation);
		
		this.add(removeButton);
	}
	
	public ChordPanel(ActionListener l) {
		for (RhythmPattern d : RhythmPattern.values()) {
			pattern.addItem(d.toString());
		}
		for (MidiUtils.POOL p : MidiUtils.POOL.values()) {
			instPool.addItem(p.toString());
		}
		
		instPool.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent event) {
				if (event.getStateChange() == ItemEvent.SELECTED) {
					setInstPool(getInstPool());
				}
			}
		});
		
		removeButton.addActionListener(l);
		removeButton.setActionCommand("RemoveChord," + chordPanelOrder);
	}
	
	
	public int getChordPanelOrder() {
		return Integer.valueOf(chordPanelOrder.getText());
	}
	
	public void setChordPanelOrder(int chordPanelOrder) {
		this.chordPanelOrder.setText("" + chordPanelOrder);
		removeButton.setActionCommand("RemoveChord," + chordPanelOrder);
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
	
	public int getDelay() {
		return Integer.valueOf(delay.getText());
	}
	
	public void setDelay(int delay) {
		this.delay.setText("" + delay);
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
				MidiUtils.INST_POOLS.get(getInstPool()));
	}
	
	public void setInstrument(int instrument) {
		MidiUtils.selectJComboBoxByInst(this.instrument, MidiUtils.INST_POOLS.get(getInstPool()),
				instrument);
	}
	
	public ChordPart toChordPart(int lastRandomSeed) {
		ChordPart part = new ChordPart(getInstrument(), getTransitionChance(), getTransitionSplit(),
				getStrum(), getDelay(), getTranspose(),
				(getPatternSeed() != 0) ? getPatternSeed() : lastRandomSeed, getPattern(),
				getPatternRotation(), getChordPanelOrder());
		part.setInstPool(getInstPool());
		return part;
	}
	
	public void setFromChordPart(ChordPart part) {
		setInstPool(part.getInstPool());
		setInstrument(part.getInstrument());
		setTransitionChance(part.getTransitionChance());
		setTransitionSplit(part.getTransitionSplit());
		setTranspose(part.getTranspose());
		
		setStrum(part.getStrum());
		setDelay(part.getDelay());
		
		setPatternSeed(part.getPatternSeed());
		setPattern(part.getPattern());
		setPatternRotation(part.getPatternRotation());
		
		setChordPanelOrder(part.getOrder());
		
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
	
	public MidiUtils.POOL getInstPool() {
		return MidiUtils.POOL.valueOf((String) instPool.getSelectedItem());
	}
	
	public void setInstPool(MidiUtils.POOL pool) {
		instPool.setSelectedItem(pool.name());
		instrument.removeAllItems();
		MidiUtils.addAllToJComboBox(MidiUtils.INST_POOLS.get(getInstPool()), instrument);
		instrument.setSelectedIndex(0);
	}
	
}
