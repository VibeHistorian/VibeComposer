package org.vibehistorian.midimasterpiece.midigenerator;

import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

public class DrumPanel extends InstPanel {
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
	
	private JTextField velocityMin = new JTextField("60", 3);
	private JTextField velocityMax = new JTextField("100", 3);
	
	private JTextField swingPercent = new JTextField("50", 2);
	private JCheckBox isVelocityPattern = new JCheckBox("Dynamic", true);
	
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
		
		this.add(new JLabel("Swing(%)"));
		this.add(swingPercent);
		this.add(new JLabel("Delay(ms)"));
		this.add(delay);
		
		this.add(new JLabel("Seed"));
		this.add(patternSeed);
		this.add(new JLabel("Pattern"));
		this.add(pattern);
		this.add(isVelocityPattern);
		this.add(new JLabel("Shift"));
		this.add(patternShift);
		
		this.add(new JLabel("Midi ch. 10"));
		
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
	
	public DrumPart toDrumPart(int lastRandomSeed) {
		DrumPart part = new DrumPart(getPitch(), getHitsPerPattern(), getChordSpan(),
				getPauseChance(), getExceptionChance(), getVelocityMin(), getVelocityMax(),
				getDelay(), (getPatternSeed() != 0) ? getPatternSeed() : lastRandomSeed,
				getPattern(), getIsVelocityPattern(), getPatternShift(), getMuteInst(),
				getSwingPercent());
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
		
		setDelay(part.getSlideMiliseconds());
		setSwingPercent(part.getSwingPercent());
		
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
	
	public int getSwingPercent() {
		return Integer.valueOf(swingPercent.getText());
	}
	
	public void setSwingPercent(int swingPercent) {
		this.swingPercent.setText("" + swingPercent);
	}
}
