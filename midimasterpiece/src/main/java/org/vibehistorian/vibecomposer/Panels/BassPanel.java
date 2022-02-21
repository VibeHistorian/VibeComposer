package org.vibehistorian.vibecomposer.Panels;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;

import org.vibehistorian.vibecomposer.InstUtils;
import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Enums.PatternJoinMode;
import org.vibehistorian.vibecomposer.Parts.BassPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;

public class BassPanel extends InstPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1472358707275766819L;

	private JCheckBox useRhythm = new CustomCheckBox("Use Pattern", true);
	private JCheckBox alternatingRhythm = new CustomCheckBox("Random Alt. Rhythm", true);
	private JCheckBox doubleOct = new CustomCheckBox("Oct. Interval", false);
	private KnobPanel noteVariation = new KnobPanel("Note Variance", 50);
	private JCheckBox melodyPattern = new CustomCheckBox("Melody1 Pattern", false);

	private ScrollComboBox<PatternJoinMode> patternJoinMode = new ScrollComboBox<>();

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

		//this.add(useRhythm);
		this.add(alternatingRhythm);
		this.add(transpose);

		this.add(chordSpanFillPanel);

		this.add(hitsPerPattern);
		hitsPerPattern.setInt(32);
		this.add(pattern);
		JButton veloTogglerButt = new JButton("V");
		veloTogglerButt.setPreferredSize(new Dimension(25, 30));
		veloTogglerButt.setMargin(new Insets(0, 0, 0, 0));
		comboPanel = makeVisualPatternPanel();
		comboPanel.setBigModeAllowed(false);
		comboPanel.linkVelocityToggle(veloTogglerButt);
		this.add(comboPanel);
		this.add(veloTogglerButt);
		this.add(patternFlip);
		this.add(patternShift);
		this.add(chordSpan);
		//this.add(noteLengthMultiplier);
		this.add(patternJoinMode);


		this.add(doubleOct);

		this.add(noteVariation);

		//this.add(melodyPattern);

		this.add(minMaxVelSlider);
		this.add(delay);

		this.add(patternSeedLabel);
		this.add(patternSeed);

		this.add(new JLabel("Midi ch.: 9"));
		setPanelOrder(1);
		initDefaultsPost();
	}

	public BassPanel(ActionListener l) {
		initComponents(l);
		for (PatternJoinMode pjm : PatternJoinMode.values()) {
			patternJoinMode.addItem(pjm);
		}
		patternJoinMode.setSelectedItem(PatternJoinMode.EXPAND);
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
		part.setPatternJoinMode(getPatternJoinMode());
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
		setPatternJoinMode(part.getPatternJoinMode());
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

	public PatternJoinMode getPatternJoinMode() {
		return patternJoinMode.getVal();
	}

	public void setPatternJoinMode(PatternJoinMode patternJoinMode) {
		this.patternJoinMode.setVal(patternJoinMode);
	}

	@Override
	public int getPartNum() {
		return 1;
	}

	@Override
	public Class<? extends InstPart> getPartClass() {
		return BassPart.class;
	}
}
