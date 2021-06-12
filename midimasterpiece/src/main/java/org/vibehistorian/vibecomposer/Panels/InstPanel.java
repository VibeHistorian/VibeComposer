/* --------------------
* @author Vibe Historian
* ---------------------

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or any
later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/

package org.vibehistorian.vibecomposer.Panels;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.HashSet;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.border.BevelBorder;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.InstComboBox;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.MidiUtils.POOL;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Panels.SoloMuter.State;
import org.vibehistorian.vibecomposer.Parts.InstPart;

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
	protected JCheckBox muteInst = new JCheckBox("Exclude", false);

	protected JSlider volSlider = new JSlider();

	protected JComboBox<String> midiChannel = new JComboBox<>();

	protected JButton removeButton = new JButton("X");
	protected SoloMuter soloMuter;
	protected JButton copyButton = new JButton("Cc");

	protected Set<Component> toggleableComponents = new HashSet<>();

	protected Class<? extends InstPart> partClass = InstPart.class;
	protected Integer sequenceTrack = -1;

	public InstPanel() {

	}

	public void initDefaults() {
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setMaximumSize(new Dimension(3000, 50));
		MidiUtils.addAllToJComboBox(new String[] { "ALL", "ODD", "EVEN" }, chordSpanFill);
		setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		volSlider.setMaximum(100);
		volSlider.setValue(100);
		volSlider.setOrientation(JSlider.VERTICAL);
		volSlider.setPreferredSize(new Dimension(30, 40));
		volSlider.setPaintTicks(true);

		copyButton.setActionCommand("CopyPart");
		copyButton.setPreferredSize(new Dimension(25, 30));
		copyButton.setMargin(new Insets(0, 0, 0, 0));

		transpose.getSlider().setMajorTickSpacing(12);
		transpose.getSlider().setSnapToTicks(true);

		toggleableComponents.add(stretchEnabled);
		toggleableComponents.add(chordNotesStretch);
		toggleableComponents.add(exceptionChance);
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

	public SoloMuter getSoloMuter() {
		return soloMuter;
	}

	public void setSoloMuter(SoloMuter sm) {
		if (sm.soloState == State.FULL) {
			soloMuter.solo();
		}
		if (sm.muteState == State.FULL) {
			soloMuter.mute();
		}
	}

	public Class<? extends InstPart> getPartClass() {
		return partClass;
	}

	public void setPartClass(Class<? extends InstPart> partClass) {
		this.partClass = partClass;
	}

	public Integer getSequenceTrack() {
		return sequenceTrack;
	}

	public void setSequenceTrack(Integer sequenceTrack) {
		this.sequenceTrack = sequenceTrack;
	}


}
