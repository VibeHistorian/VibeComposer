package org.vibehistorian.vibecomposer.Panels;

import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Helpers.OMNI;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;

public class MelodyPanel extends InstPanel {

	private static final long serialVersionUID = -7861296600641561431L;

	private JCheckBox fillPauses = new JCheckBox("Fill Pauses", false);
	private JTextField chordNoteChoices = new JTextField("0,1,1,2");
	private JTextField melodyPatternOffsets = new JTextField("0,1,0,2");

	public void initComponents(ActionListener l) {
		MidiUtils.addAllToJComboBox(new String[] { "1" }, midiChannel);
		midiChannel.setSelectedItem("1");
		instrument.initInstPool(instPool);
		setInstrument(8);
		initDefaults(l);
		volSlider.setValue(80);
		setVelocityMin(80);
		setVelocityMax(105);
		this.add(volSlider);
		/*this.add(new JLabel("#"));
		this.add(panelOrder);*/
		this.add(new JLabel("#"));
		this.add(panelOrder);
		soloMuter = new SoloMuter(0, SoloMuter.Type.SINGLE);
		addDefaultInstrumentControls();

		this.add(minMaxVelSlider);

		this.add(transpose);

		pauseChance.setInt(0);
		this.add(pauseChance);
		this.add(fillPauses);

		this.add(new JLabel("Chord Note"));
		this.add(chordNoteChoices);
		this.add(new JLabel("Pattern"));
		this.add(melodyPatternOffsets);


		this.add(minMaxVelSlider);
		this.add(swingPercent);

		this.add(patternSeedLabel);
		this.add(patternSeed);

		this.add(new JLabel("Midi ch.: 1"));
		setPanelOrder(1);

	}

	public MelodyPanel(ActionListener l) {
		setPartClass(MelodyPart.class);
		initComponents(l);
	}


	public MelodyPart toMelodyPart(int lastRandomSeed) {
		MelodyPart part = new MelodyPart();
		part.setFromPanel(this, lastRandomSeed);
		part.setOrder(getPanelOrder());

		part.setFillPauses(getFillPauses());
		part.setChordNoteChoices(getChordNoteChoices());
		part.setMelodyPatternOffsets(getMelodyPatternOffsets());
		return part;
	}

	public void setFromInstPart(InstPart p) {
		MelodyPart part = (MelodyPart) p;
		setDefaultsFromInstPart(part);
		setPanelOrder(part.getOrder());

		setFillPauses(part.isFillPauses());
		setChordNoteChoices(part.getChordNoteChoices());
		setMelodyPatternOffsets(part.getMelodyPatternOffsets());
	}

	@Override
	public InstPart toInstPart(int lastRandomSeed) {
		return toMelodyPart(lastRandomSeed);
	}

	public boolean getFillPauses() {
		return fillPauses.isSelected();
	}

	public void setFillPauses(boolean fillPauses) {
		this.fillPauses.setSelected(fillPauses);
	}

	public List<Integer> getChordNoteChoices() {
		return OMNI.parseIntsString(chordNoteChoices.getText());
	}

	public void setChordNoteChoices(List<Integer> chordNoteChoices) {
		this.chordNoteChoices.setText(StringUtils.join(chordNoteChoices, ","));
	}

	public List<Integer> getMelodyPatternOffsets() {
		return OMNI.parseIntsString(melodyPatternOffsets.getText());
	}

	public void setMelodyPatternOffsets(List<Integer> melodyPatternOffsets) {
		this.melodyPatternOffsets.setText(StringUtils.join(melodyPatternOffsets, ","));
	}
}
