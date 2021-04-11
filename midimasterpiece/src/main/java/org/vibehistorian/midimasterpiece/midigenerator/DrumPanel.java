package org.vibehistorian.midimasterpiece.midigenerator;

import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class DrumPanel extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6219184197272490684L;
	
	private JLabel panelOrder = new JLabel("0");
	
	public int getPanelOrder() {
		return Integer.valueOf(panelOrder.getText());
	}
	
	public void setPanelOrder(int panelOrder) {
		this.panelOrder.setText("" + panelOrder);
		removeButton.setActionCommand("RemoveDrum," + panelOrder);
	}
	
	private JTextField pitch = new JTextField("36", 2);
	private JTextField hitsPerPattern = new JTextField("8", 2);
	private JTextField chordSpan = new JTextField("1", 1);
	
	private JTextField pauseChance = new JTextField("70", 2);
	private JTextField exceptionChance = new JTextField("5", 2);
	
	private JTextField velocityMin = new JTextField("60", 3);
	private JTextField velocityMax = new JTextField("100", 3);
	
	private JTextField slideMiliseconds = new JTextField("0", 4);
	
	private JTextField patternSeed = new JTextField("0", 8);
	private JComboBox<String> pattern = new JComboBox<String>();
	private JCheckBox isVelocityPattern = new JCheckBox("Dynamic", true);
	private JTextField patternShift = new JTextField("0", 1);
	
	private JCheckBox muteInst = new JCheckBox("Mute", false);
	
	private JButton removeButton = new JButton("X");
	
	public void initComponents() {
		this.add(new JLabel("#"));
		this.add(panelOrder);
		this.add(muteInst);
		this.add(new JLabel("Pitch"));
		this.add(pitch);
		this.add(new JLabel("Hits#"));
		this.add(hitsPerPattern);
		this.add(new JLabel("Chords#"));
		this.add(chordSpan);
		
		this.add(new JLabel("Pause%"));
		this.add(pauseChance);
		this.add(new JLabel("Exception%"));
		this.add(exceptionChance);
		
		this.add(new JLabel("MinVel"));
		this.add(velocityMin);
		this.add(new JLabel("MaxVel"));
		this.add(velocityMax);
		
		this.add(new JLabel("Slide(ms)"));
		this.add(slideMiliseconds);
		
		this.add(new JLabel("Seed"));
		this.add(patternSeed);
		this.add(new JLabel("Pattern"));
		this.add(pattern);
		this.add(isVelocityPattern);
		this.add(new JLabel("Shift"));
		this.add(patternShift);
		
		this.add(removeButton);
	}
	
	public DrumPanel(ActionListener l) {
		for (RhythmPattern d : RhythmPattern.values()) {
			pattern.addItem(d.toString());
		}
		removeButton.addActionListener(l);
		removeButton.setActionCommand("RemoveDrum," + panelOrder);
	}
	
	public int getPitch() {
		return Integer.valueOf(pitch.getText());
	}
	
	public void setPitch(int pitch) {
		this.pitch.setText(String.valueOf(pitch));
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
	
	public int getVelocityMin() {
		return Integer.valueOf(velocityMin.getText());
	}
	
	public void setVelocityMin(int velocityMin) {
		this.velocityMin.setText(String.valueOf(velocityMin));
	}
	
	public int getVelocityMax() {
		return Integer.valueOf(velocityMax.getText());
	}
	
	public void setVelocityMax(int velocityMax) {
		this.velocityMax.setText(String.valueOf(velocityMax));
	}
	
	public int getSlideMiliseconds() {
		return Integer.valueOf(slideMiliseconds.getText());
	}
	
	public void setSlideMiliseconds(int slideMiliseconds) {
		this.slideMiliseconds.setText(String.valueOf(slideMiliseconds));
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
	
	public DrumPart toDrumPart(int lastRandomSeed) {
		DrumPart part = new DrumPart(getPitch(), getHitsPerPattern(), getChordSpan(),
				getPauseChance(), getExceptionChance(), getVelocityMin(), getVelocityMax(),
				getSlideMiliseconds(), (getPatternSeed() != 0) ? getPatternSeed() : lastRandomSeed,
				getPattern(), getIsVelocityPattern(), getPatternShift(), getMuteInst());
		part.setOrder(getPanelOrder());
		return part;
	}
	
	public void setFromDrumPart(DrumPart part) {
		
		setPitch(part.getPitch());
		setHitsPerPattern(part.getHitsPerPattern());
		setChordSpan(part.getChordSpan());
		
		setPauseChance(part.getPauseChance());
		setExceptionChance(part.getExceptionChance());
		
		setVelocityMin(part.getVelocityMin());
		setVelocityMax(part.getVelocityMax());
		
		setSlideMiliseconds(part.getSlideMiliseconds());
		
		setPatternSeed(part.getPatternSeed());
		setPattern(part.getPattern());
		
		setIsVelocityPattern(part.isVelocityPattern());
		setPatternShift(part.getPatternShift());
		
		setPanelOrder(part.getOrder());
		setMuteInst(part.isMuted());
		
	}
	
	public boolean getIsVelocityPattern() {
		return isVelocityPattern.isSelected();
	}
	
	public void setIsVelocityPattern(boolean isVelocityPattern) {
		this.isVelocityPattern.setSelected(isVelocityPattern);
	}
	
	public int getPatternShift() {
		return Integer.valueOf(patternShift.getText());
	}
	
	public void setPatternShift(int shift) {
		patternShift.setText(String.valueOf(shift));
	}
	
	public boolean getMuteInst() {
		return muteInst.isSelected();
	}
	
	public void setMuteInst(boolean selected) {
		this.muteInst.setSelected(selected);
	}
}
