package org.vibehistorian.vibecomposer.Panels;

import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;

import org.vibehistorian.vibecomposer.InstUtils;
import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;
import org.vibehistorian.vibecomposer.Parts.BassPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;

public class BassPanel extends InstPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1472358707275766819L;

	private JCheckBox useRhythm = new JCheckBox("Use Rhythm", true);
	private JCheckBox alternatingRhythm = new JCheckBox("Alternating", true);
	private JCheckBox doubleOct = new JCheckBox("Double Oct.", false);
	private KnobPanel noteVariation = new KnobPanel("Note Variation", 20);
	private JCheckBox melodyPattern = new JCheckBox("Melody1 Pattern", false);

	public void initComponents(ActionListener l) {
		ScrollComboBox.addAll(new Integer[] { 9 }, midiChannel);
		midiChannel.setVal(9);
		instPool = InstUtils.POOL.BASS;
		instrument.initInstPool(instPool);
		setInstrument(74);
		initDefaults(l);
		volSlider.setDefaultValue(69);
		this.add(volSlider);
		this.add(panSlider);
		this.add(new JLabel("#"));
		this.add(panelOrder);
		soloMuter = new SoloMuter(1, SoloMuter.Type.SINGLE);
		addDefaultInstrumentControls();

		this.add(useRhythm);
		this.add(alternatingRhythm);

		//this.add(new JLabel("Transpose"));
		this.add(transpose);
		//this.add(new JKnob());

		this.add(doubleOct);

		this.add(noteVariation);

		this.add(melodyPattern);

		this.add(minMaxVelSlider);

		this.add(patternSeedLabel);
		this.add(patternSeed);

		this.add(new JLabel("Midi ch.: 9"));
		setPanelOrder(1);

	}

	public BassPanel(ActionListener l) {
		setPartClass(BassPart.class);
		initComponents(l);
	}


	public BassPart toBassPart(int lastRandomSeed) {
		BassPart part = new BassPart();
		part.setFromPanel(this, lastRandomSeed);
		part.setOrder(getPanelOrder());
		part.setUseRhythm(getUseRhythm());
		part.setAlternatingRhythm(getAlternatingRhythm());
		part.setDoubleOct(getDoubleOct());
		part.setNoteVariation(getNoteVariation());
		part.setMelodyPattern(getMelodyPattern());
		return part;
	}

	public void setFromInstPart(InstPart p) {
		BassPart part = (BassPart) p;
		setDefaultsFromInstPart(part);
		setPanelOrder(part.getOrder());
		setUseRhythm(part.isUseRhythm());
		setAlternatingRhythm(part.isAlternatingRhythm());
		setDoubleOct(part.isDoubleOct());
		setNoteVariation(part.getNoteVariation());
		setMelodyPattern(part.isMelodyPattern());
	}

	public boolean getUseRhythm() {
		return useRhythm.isSelected();
	}

	public void setUseRhythm(boolean val) {
		this.useRhythm.setSelected(val);
	}

	public boolean getAlternatingRhythm() {
		return alternatingRhythm.isSelected();
	}

	public void setAlternatingRhythm(boolean val) {
		this.alternatingRhythm.setSelected(val);
	}

	public boolean getDoubleOct() {
		return doubleOct.isSelected();
	}

	public void setDoubleOct(boolean val) {
		this.doubleOct.setSelected(val);
	}

	@Override
	public InstPart toInstPart(int lastRandomSeed) {
		return toBassPart(lastRandomSeed);
	}

	public int getNoteVariation() {
		return noteVariation.getInt();
	}

	public void setNoteVariation(int val) {
		this.noteVariation.setInt(val);
	}

	public boolean getMelodyPattern() {
		return melodyPattern.isSelected();
	}

	public void setMelodyPattern(boolean val) {
		this.melodyPattern.setSelected(val);
	}
}
