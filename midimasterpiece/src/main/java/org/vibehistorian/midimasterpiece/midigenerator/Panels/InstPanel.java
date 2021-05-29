package org.vibehistorian.midimasterpiece.midigenerator.Panels;

import java.awt.Component;
import java.awt.Dimension;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.midimasterpiece.midigenerator.InstComboBox;
import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils;
import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils.POOL;
import org.vibehistorian.midimasterpiece.midigenerator.Enums.ChordSpanFill;
import org.vibehistorian.midimasterpiece.midigenerator.Enums.RhythmPattern;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.InstPart;

public abstract class InstPanel extends JPanel {

	private static final long serialVersionUID = 4381939543337887617L;

	protected InstComboBox instrument = new InstComboBox();
	protected POOL instPool = POOL.PLUCK;
	protected JComboBox<String> chordSpanFill = new JComboBox<String>();

	protected NumPanel hitsPerPattern = new NumPanel("Hits#", 8, 1, 32);
	protected NumPanel chordSpan = new NumPanel("Chords#", 1, 1, 4);

	protected NumPanel chordNotesStretch = new NumPanel("", 3, 2, 6);
	protected JCheckBox stretchEnabled = new JCheckBox("StretCh.", false);

	protected NumPanel pauseChance = new NumPanel("Pause%", 20);
	protected NumPanel exceptionChance = new NumPanel("Exc.%", 5);
	protected JCheckBox repeatableNotes = new JCheckBox("Note repeat", true);
	protected NumPanel patternRepeat = new NumPanel("Repeat#", 2, 1, 4);

	protected NumPanel transpose = new NumPanel("Transpose", 0, -36, 36);
	protected NumPanel delay = new NumPanel("Delay", 0, -500, 500);

	protected NumPanel velocityMin = new NumPanel("MinVel", 70, 0, 126);
	protected NumPanel velocityMax = new NumPanel("MaxVel", 90, 1, 127);

	protected NumPanel swingPercent = new NumPanel("Swing%", 50);

	protected JLabel panelOrder = new JLabel("1");

	protected JTextField patternSeed = new JTextField("0", 8);
	protected JComboBox<String> pattern = new JComboBox<String>();
	protected NumPanel patternShift = new NumPanel("Shift", 0, 0, 8);

	protected JCheckBox lockInst = new JCheckBox("Lock", false);
	protected JCheckBox muteInst = new JCheckBox("Mute", false);

	protected JSlider volSlider = new JSlider();

	protected JComboBox<String> midiChannel = new JComboBox<>();

	protected JButton removeButton = new JButton("X");

	protected Set<Component> toggleableComponents = new HashSet<>();

	public InstPanel() {

	}

	public void initDefaults() {
		MidiUtils.addAllToJComboBox(new String[] { "ALL", "ODD", "EVEN" }, chordSpanFill);

		volSlider.setMaximum(100);
		volSlider.setValue(100);
		volSlider.setOrientation(JSlider.VERTICAL);
		volSlider.setPreferredSize(new Dimension(30, 50));
		volSlider.setPaintTicks(true);

		toggleableComponents.add(hitsPerPattern);
		toggleableComponents.add(chordSpan);
		toggleableComponents.add(chordNotesStretch);
		toggleableComponents.add(pauseChance);
		toggleableComponents.add(exceptionChance);
		toggleableComponents.add(patternRepeat);
		toggleableComponents.add(transpose);
		toggleableComponents.add(delay);
		toggleableComponents.add(velocityMin);
		toggleableComponents.add(velocityMax);
		toggleableComponents.add(patternShift);
	}

	public void setFromInstPart(InstPart part) {
		setInstrument(part.getInstrument());

		setHitsPerPattern(part.getHitsPerPattern());
		setChordSpan(part.getChordSpan());
		setChordSpanFill(part.getChordSpanFill());

		setChordNotesStretch(part.getChordNotesStretch());
		setStretchEnabled(part.isStretchEnabled());

		setPauseChance(part.getPauseChance());
		setExceptionChance(part.getExceptionChance());

		setRepeatableNotes(part.isRepeatableNotes());
		setPatternRepeat(part.getPatternRepeat());

		setTranspose(part.getTranspose());
		setDelay(part.getDelay());

		setVelocityMin(part.getVelocityMin());
		setVelocityMax(part.getVelocityMax());

		setSwingPercent(part.getSwingPercent());

		setPatternSeed(part.getPatternSeed());
		setPattern(part.getPattern());
		setPatternShift(part.getPatternShift());

		setMidiChannel(part.getMidiChannel());

		volSlider.setValue(part.getSliderVolume());

		setMuteInst(part.isMuted());
	}

	public int getHitsPerPattern() {
		return Integer.valueOf(hitsPerPattern.getInt());
	}

	public void setHitsPerPattern(int hitsPerPattern) {
		this.hitsPerPattern.setInt(hitsPerPattern);
	}

	public int getChordSpan() {
		return Integer.valueOf(chordSpan.getInt());
	}

	public void setChordSpan(int chordSpan) {
		this.chordSpan.setInt(chordSpan);
	}

	public int getPauseChance() {
		return Integer.valueOf(pauseChance.getInt());
	}

	public void setPauseChance(int pauseChance) {
		this.pauseChance.setInt(pauseChance);
	}

	public int getExceptionChance() {
		return Integer.valueOf(exceptionChance.getInt());
	}

	public void setExceptionChance(int exceptionChance) {
		this.exceptionChance.setInt(exceptionChance);
	}

	public int getTranspose() {
		return Integer.valueOf(transpose.getInt());
	}

	public void setTranspose(int transpose) {
		this.transpose.setInt(transpose);
	}

	public int getPatternSeed() {
		return Integer.valueOf(patternSeed.getText());
	}

	public void setPatternSeed(int patternSeed) {
		this.patternSeed.setText(String.valueOf(patternSeed));
	}

	public RhythmPattern getPattern() {
		if (StringUtils.isEmpty((String) pattern.getSelectedItem())) {
			return RhythmPattern.RANDOM;
		}
		return RhythmPattern.valueOf((String) pattern.getSelectedItem());
	}

	public void setPattern(RhythmPattern pattern) {
		this.pattern.setSelectedItem((String.valueOf(pattern.toString())));
	}

	public int getPatternShift() {
		return Integer.valueOf(patternShift.getInt());
	}

	public void setPatternShift(int shift) {
		patternShift.setInt(shift);
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
		return Integer.valueOf(patternRepeat.getInt());
	}

	public void setPatternRepeat(int patternRepeat) {
		this.patternRepeat.setInt(patternRepeat);
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

	public void setInstrumentBox(InstComboBox instComboBox) {
		this.instrument = instComboBox;

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
		return Integer.valueOf(delay.getInt());
	}

	public void setDelay(int delay) {
		this.delay.setInt(delay);
	}

	public int getChordNotesStretch() {
		return Integer.valueOf(chordNotesStretch.getInt());
	}

	public void setChordNotesStretch(int chordStretch) {
		this.chordNotesStretch.setInt(chordStretch);
	}

	public boolean getStretchEnabled() {
		return stretchEnabled.isSelected();
	}

	public void setStretchEnabled(boolean stretchEnabled) {
		this.stretchEnabled.setSelected(stretchEnabled);
	}

	public int getVelocityMin() {
		return Integer.valueOf(velocityMin.getInt());
	}

	public void setVelocityMin(int velocityMin) {
		this.velocityMin.setInt(velocityMin);
	}

	public int getVelocityMax() {
		return Integer.valueOf(velocityMax.getInt());
	}

	public void setVelocityMax(int velocityMax) {
		this.velocityMax.setInt(velocityMax);
	}

	public JSlider getVolSlider() {
		return volSlider;
	}

	public void setVolSlider(JSlider volSlider) {
		this.volSlider = volSlider;
	}


	public int getSwingPercent() {
		return Integer.valueOf(swingPercent.getInt());
	}

	public void setSwingPercent(int swingPercent) {
		this.swingPercent.setInt(swingPercent);
	}

	public int getPanelOrder() {
		return Integer.valueOf(panelOrder.getText());
	}

	public Set<Component> getToggleableComponents() {
		return toggleableComponents;
	}

	public void setToggleableComponents(Set<Component> toggleableComponents) {
		this.toggleableComponents = toggleableComponents;
	}
}
