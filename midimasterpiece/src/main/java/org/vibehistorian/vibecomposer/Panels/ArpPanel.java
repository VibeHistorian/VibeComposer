package org.vibehistorian.vibecomposer.Panels;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JLabel;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.MelodyUtils;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Components.ArpPickerMini;
import org.vibehistorian.vibecomposer.Components.CheckButton;
import org.vibehistorian.vibecomposer.Components.RandomIntegerListButton;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;

public class ArpPanel extends InstPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6648220153568966988L;

	private ArpPickerMini arpPattern = new ArpPickerMini(this);
	private RandomIntegerListButton arpContour = new RandomIntegerListButton("?", this);
	private CheckButton arpContourChordMode = new CheckButton("C", true);
	private KnobPanel arpPatternRotate = new KnobPanel("Rotate", 0, 0, 8);

	public void initComponents(ActionListener l) {

		instrument.initInstPool(instPool);
		ScrollComboBox.addAll(new Integer[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 },
				midiChannel);
		midiChannel.setVal(2);

		initDefaults(l);
		volSlider.setDefaultValue(50);
		this.add(volSlider);
		this.add(panSlider);
		this.add(new JLabel("#"));
		this.add(panelOrder);
		addDefaultInstrumentControls();
		addDefaultPanelButtons();

		this.add(transpose);
		this.add(chordSpanFillPanel);

		this.add(hitsPerPattern);
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

		this.add(patternRepeat);
		//this.add(repeatableNotes);
		this.add(pauseChance);
		JLabel notePresetLabel = new JLabel("Dir:");
		this.add(notePresetLabel);
		this.add(arpPattern);
		arpContour.setMargin(new Insets(0, 0, 0, 0));
		arpContour.setTextGenerator(e -> {
			return StringUtils.join(arpContour.getRandGenerator().apply(new Object()), ",");
		});
		arpContour.setRandGenerator(e -> {
			Random rnd = new Random();
			return new ArrayList<>(Arrays
					.asList(MelodyUtils.getRandomForType(rnd.nextInt(MelodyUtils.NUM_LISTS), rnd)));
		});
		arpContour.setHighlighterGenerator(null);
		this.add(arpContour);
		this.add(arpContourChordMode);
		this.add(arpPatternRotate);

		this.add(stretchPanel);
		this.add(noteLengthMultiplier);

		this.add(minMaxVelSlider);


		this.add(exceptionChance);
		this.add(swingPercent);
		addOffsetAndDelayControls();


		this.add(patternSeedLabel);
		this.add(patternSeed);

		this.add(new JLabel("Midi ch.:"));
		this.add(midiChannel);


		//toggleableComponents.add(arpPattern);
		//toggleableComponents.add(notePresetLabel);
		//toggleableComponents.add(repeatableNotes);
		initDefaultsPost();

	}

	@Override
	public void addBackgroundsForKnobs() {
		super.addBackgroundsForKnobs();
		arpPatternRotate.addBackgroundWithBorder(OMNI.alphen(Color.orange, 60));
	}

	@Override
	public void toggleComponentTexts(boolean b) {
		super.toggleComponentTexts(b);
		arpPatternRotate.setShowTextInKnob(b);
	}

	public ArpPanel(ActionListener l) {
		initComponents(l);

		for (ArpPattern d : ArpPattern.values()) {
			arpPattern.addItem(d);
		}
	}


	public ArpPart toArpPart(int lastRandomSeed) {
		ArpPart part = new ArpPart();
		part.setArpPattern(getArpPattern());
		part.setFromPanel(this, lastRandomSeed);
		part.setOrder(getPanelOrder());
		part.setArpPatternRotate(getArpPatternRotate());
		part.setArpPatternCustom(
				arpPattern.getVal() == ArpPattern.CUSTOM ? arpPattern.getCustomValues() : null);
		part.setArpContour(getArpContour());
		part.setArpContourChordMode(getArpContourChordMode());
		return part;
	}

	public void setFromInstPart(InstPart p) {
		ArpPart part = (ArpPart) p;
		setArpPattern(part.getArpPattern());
		setDefaultsFromInstPart(part);
		setPanelOrder(part.getOrder());
		setArpPatternRotate(part.getArpPatternRotate());
		if (part.getArpPattern() == ArpPattern.CUSTOM) {
			arpPattern.setCustomValues(part.getArpPatternCustom());
		}
		setArpContour(part.getArpContour());
		setArpContourChordMode(part.isArpContourChordMode());
	}

	public ArpPattern getArpPattern() {
		return arpPattern.getVal();
	}

	public void setArpPattern(ArpPattern pattern) {
		this.arpPattern.setVal(pattern);
	}

	public ArpPart toInstPart(int lastRandomSeed) {
		return toArpPart(lastRandomSeed);
	}

	public int getArpPatternRotate() {
		return arpPatternRotate.getInt();
	}

	public void setArpPatternRotate(int val) {
		arpPatternRotate.setInt(val);
	}

	@Override
	public int getPartNum() {
		return 3;
	}

	@Override
	public Class<? extends InstPart> getPartClass() {
		return ArpPart.class;
	}

	public void setArpPatternCustom(List<Integer> arpPatternCustom) {
		arpPattern.setCustomValues(arpPatternCustom);
	}

	public List<Integer> getArpContour() {
		return OMNI.parseIntsString(arpContour.getValue());
	}

	public void setArpContour(List<Integer> val) {
		this.arpContour.setValues(val);
	}

	public boolean getArpContourChordMode() {
		return arpContourChordMode.isSelected();
	}

	public void setArpContourChordMode(boolean val) {
		this.arpContourChordMode.setSelected(val);
	}
}
