package org.vibehistorian.vibecomposer.Panels;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.Components.BigPickerOpener;
import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.RandomIntegerListButton;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.InstUtils.POOL;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MelodyUtils;
import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.MidiGeneratorUtils;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Panels.SoloMuter.State;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Popups.CustomDurationsEditPopup;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MelodyPanel extends InstPanel {

	private static final long serialVersionUID = -7861296600641561431L;

	public static final int CUSTOM_DURATIONS_LIMIT = 10;

	private final JCheckBox fillPauses = new CustomCheckBox("<html>Fill<br>Pauses</html>", false);
	private final RandomIntegerListButton noteTargets = new RandomIntegerListButton("0,2,2,4", this);
	private final RandomIntegerListButton patternStructure = new RandomIntegerListButton("1,2,1,3", this);
	private final KnobPanel maxBlockChange = new KnobPanel("Max Block<br>Change +-", 7, 0, 7);
	private final KnobPanel blockJump = new KnobPanel("Block<br>Jump", 1, 0, 4);
	private final KnobPanel maxNoteExceptions = new KnobPanel("Max<br>Exc.", 0, 0, 4);
	private final KnobPanel alternatingRhythmChance = new KnobPanel("Alt.<br>Pattern", 33);
	private final KnobPanel doubledRhythmChance = new KnobPanel("Doubled<br>Rhythm%", 0);
	private final KnobPanel splitChance = new KnobPanel("Split<br>Long%", 0);
	private final KnobPanel noteExceptionChance = new KnobPanel("Note<br> Exc.%", 25);
	private final KnobPanel speed = new KnobPanel("Speed", 50, -100, 100);
	private final KnobPanel leadChordsChance = new KnobPanel("Lead To<br>Chords%", 25);
	private final KnobPanel startNoteChance = new KnobPanel("Start%", 80);
	private final JCheckBox patternFlexible = new CustomCheckBox("Flex", true);

	private PhraseNotes customDurationValues = new PhraseNotes();
	private List<Integer> customDurationChances = new ArrayList<>(IntStream.iterate(50, e -> e).limit(CUSTOM_DURATIONS_LIMIT).boxed().collect(Collectors.toList()));

	public void initComponents(ActionListener l) {

		ScrollComboBox.addAll(new Integer[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 },
				midiChannel);
		midiChannel.setVal(1);
		instPool = POOL.MELODY;
		instrument.initInstPool(instPool);
		setInstrument(73);
		initDefaults(l);
		volSlider.setDefaultValue(50);
		setVelocityMin(80);
		setVelocityMax(105);
		this.add(volSlider);
		this.add(panSlider);
		/*this.add(new JLabel("#"));
		this.add(panelOrder);*/
		//this.add(new JLabel("#"));
		this.add(panelOrder);
		addDefaultInstrumentControls();
		addDefaultPanelButtons();

		BigPickerOpener bpo = new BigPickerOpener();
		bpo.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				LG.i(customDurationValues);
				new CustomDurationsEditPopup(customDurationValues, customDurationChances, MelodyPanel.this);
			}
		});
		speed.getKnobLockPane().add(bpo);
		bpo.setBounds(0, 0, 8, 8);
		speed.getKnobLockPane().setComponentZOrder(bpo, Integer.valueOf(0));

		transpose.getKnob().setTickSpacing(10);
		Integer[] allowedMelodyTransposes = new Integer[] { 0, 4, 5, 7 };
		List<Integer> totalMelodyTransposes = new ArrayList<>();
		for (int i = -36; i <= 24; i += 12) {
			for (int t : allowedMelodyTransposes) {
				totalMelodyTransposes.add(i + t);
			}
		}
		totalMelodyTransposes.add(36);
		transpose.getKnob().setTickThresholds(totalMelodyTransposes);
		this.add(transpose);

		this.add(chordSpanFillPanel);

		this.add(speed);

		/*this.add(VibeComposerGUI.makeButton("Manual MIDI",
				e -> new VisualArrayPopup(-10, 10, new ArrayList<>())));*/

		pauseChance.setInt(0);
		this.add(pauseChance);
		this.add(fillPauses);

		this.add(new JLabel("<html>Note<br>Targets</html>"));
		noteTargets.setMargin(new Insets(0, 0, 0, 0));
		noteTargets.setTextGenerator(e -> {
			return StringUtils.join(noteTargets.getRandGenerator().apply(new Object()), ",");
		});
		noteTargets.setRandGenerator(e -> {
			return MidiGeneratorUtils.generateNoteTargetOffsets(MidiGenerator.chordInts,
					(e instanceof Integer) ? (Integer) e : new Random().nextInt(),
					VibeComposerGUI.melodyBlockTargetMode.getSelectedIndex(),
					VibeComposerGUI.melodyTargetNoteVariation.getInt(), null, VibeComposerGUI.noteTargetDirectionChoice.getSelectedItem());
		});
		noteTargets.setHighlighterGenerator(e -> {
			if (MidiGenerator.chordInts.isEmpty()) {
				return null;
			}

			if (relatedSection == null) {
				return MidiUtils.getHighlightTargetsFromChords(MidiGenerator.chordInts, true);
			} else {
				return MidiUtils
						.getHighlightTargetsFromChords(relatedSection.isCustomChordsEnabled()
								? relatedSection.getCustomChordsList()
								: MidiGenerator.chordInts, true);
			}

		});
		this.add(noteTargets);

		this.add(maxBlockChange);

		this.add(new JLabel("Pattern"));
		patternStructure.setMargin(new Insets(0, 0, 0, 0));
		this.add(patternStructure);
		patternStructure.setTextGenerator(e -> StringUtils.join(MelodyUtils.getRandomMelodyPattern(getAlternatingRhythmChance(),
                new Random().nextInt()), ","));
		patternStructure.setRandGenerator(e -> MelodyUtils.getRandomMelodyPattern(getAlternatingRhythmChance(),
                new Random().nextInt()));
		this.add(patternFlexible);

		this.add(blockJump);
		this.add(maxNoteExceptions);
		this.add(noteExceptionChance);

		this.add(minMaxVelSlider);
		this.add(noteLengthMultiplier);
		this.add(swingPercent);
		this.add(accents);
		this.add(startNoteChance);


		this.add(alternatingRhythmChance);
		this.add(doubledRhythmChance);
		this.add(splitChance);
		this.add(leadChordsChance);
		addOffsetAndDelayControls();

		this.add(patternSeedLabel);
		this.add(patternSeed);

		//toggleableComponents.add(maxNoteExceptions);
		toggleableComponents.add(alternatingRhythmChance);
		toggleableComponents.add(doubledRhythmChance);
		toggleableComponents.add(splitChance);
		toggleableComponents.add(noteExceptionChance);
		toggleableComponents.add(leadChordsChance);

		this.add(new JLabel("Midi ch."));
		this.add(midiChannel);
	}

	@Override
	public void addBackgroundsForKnobs() {
		super.addBackgroundsForKnobs();
		speed.addBackgroundWithBorder(OMNI.alphen(Color.red, 50));
		startNoteChance.addBackgroundWithBorder(OMNI.alphen(Color.black, 70));
	}

	@Override
	public void toggleComponentTexts(boolean b) {
		super.toggleComponentTexts(b);
		speed.setShowTextInKnob(b);
		startNoteChance.setShowTextInKnob(b);
	}

	public void toggleCombinedMelodyDisabledUI(boolean b) {
		if (!b && soloMuter.soloState != State.OFF) {
			soloMuter.unsolo();
		}
		getSoloMuter().setEnabled(b);
		getPanSlider().setEnabled(b);
		volSlider.setEnabled(b);

	}

	public MelodyPanel(ActionListener l) {
		initComponents(l);
	}


	public MelodyPart toMelodyPart(int lastRandomSeed) {
		MelodyPart part = new MelodyPart();
		part.setFromPanel(this, lastRandomSeed);

		part.setFillPauses(getFillPauses());
		part.setChordNoteChoices(getChordNoteChoices());
		part.setMelodyPatternOffsets(getMelodyPatternOffsets());
		part.setMaxBlockChange(getMaxBlockChange());
		part.setAlternatingRhythmChance(getAlternatingRhythmChance());
		part.setBlockJump(getBlockJump());
		part.setDoubledRhythmChance(getDoubledRhythmChance());
		part.setLeadChordsChance(getLeadChordsChance());
		part.setStartNoteChance(getStartNoteChance());
		part.setMaxNoteExceptions(getMaxNoteExceptions());
		part.setNoteExceptionChance(getNoteExceptionChance());
		part.setSpeed(getSpeed());
		part.setSplitChance(getSplitChance());
		part.setPatternFlexible(getPatternFlexible());

		part.setCustomDurationNotes(getCustomDurationNotes());
		part.setCustomDurationChances(getCustomDurationChances());

		return part;
	}

	public void setFromInstPart(InstPart p) {
		MelodyPart part = (MelodyPart) p;
		setDefaultsFromInstPart(part);

		setFillPauses(part.isFillPauses());
		setChordNoteChoices(part.getChordNoteChoices());
		setMelodyPatternOffsets(part.getMelodyPatternOffsets());
		setMaxBlockChange(part.getMaxBlockChange());
		setAlternatingRhythmChance(part.getAlternatingRhythmChance());
		setBlockJump(part.getBlockJump());
		setDoubledRhythmChance(part.getDoubledRhythmChance());
		setLeadChordsChance(part.getLeadChordsChance());
		setStartNoteChance(part.getStartNoteChance());
		setMaxNoteExceptions(part.getMaxNoteExceptions());
		setNoteExceptionChance(part.getNoteExceptionChance());
		setSpeed(part.getSpeed());
		setSplitChance(part.getSplitChance());
		setPatternFlexible(part.isPatternFlexible());

		setCustomDurationNotes(part.getCustomDurationNotes());
		setCustomDurationChances(part.getCustomDurationChances());
	}

	@Override
	public InstPart toInstPart(int lastRandomSeed) {
		return toMelodyPart(lastRandomSeed);
	}

	public boolean getFillPauses() {
		return fillPauses.isSelected();
	}

	public void setFillPauses(boolean val) {
		this.fillPauses.setSelected(val);
	}

	public List<Integer> getChordNoteChoices() {
		return OMNI.parseIntsString(noteTargets.getValue());
	}

	public void setChordNoteChoices(List<Integer> val) {
		this.noteTargets.setValues(val);
	}

	public List<Integer> getMelodyPatternOffsets() {
		return OMNI.parseIntsString(patternStructure.getValue());
	}

	public void setMelodyPatternOffsets(List<Integer> val) {
		this.patternStructure.setValues(val);
	}

	public void overridePatterns(MelodyPanel mp1) {
		if (getLockInst() == true) {
			return;
		}
		noteTargets.setValue(mp1.noteTargets.getValue());
		patternStructure.setValue(mp1.patternStructure.getValue());
		maxBlockChange.setInt(mp1.maxBlockChange.getInt());
		blockJump.setInt(mp1.blockJump.getInt());
		maxNoteExceptions.setInt(mp1.maxNoteExceptions.getInt());
		alternatingRhythmChance.setInt(mp1.alternatingRhythmChance.getInt());
		doubledRhythmChance.setInt(mp1.doubledRhythmChance.getInt());
		splitChance.setInt(mp1.splitChance.getInt());
		noteExceptionChance.setInt(mp1.noteExceptionChance.getInt());
		speed.setInt(mp1.speed.getInt());
		//leadChordsChance.setInt(mp1.leadChordsChance.getInt());
		//startNoteChance.setInt(mp1.startNoteChance.getInt());
		//setInstrument(mp1.getInstrument());
	}

	public int getMaxBlockChange() {
		return maxBlockChange.getInt();
	}

	public void setMaxBlockChange(int val) {
		this.maxBlockChange.setInt(val);
	}

	public int getBlockJump() {
		return blockJump.getInt();
	}

	public void setBlockJump(int val) {
		this.blockJump.setInt(val);
	}

	public int getMaxNoteExceptions() {
		return maxNoteExceptions.getInt();
	}

	public void setMaxNoteExceptions(int val) {
		this.maxNoteExceptions.setInt(val);
	}

	public int getAlternatingRhythmChance() {
		return alternatingRhythmChance.getInt();
	}

	public void setAlternatingRhythmChance(int val) {
		this.alternatingRhythmChance.setInt(val);
	}

	public int getDoubledRhythmChance() {
		return doubledRhythmChance.getInt();
	}

	public void setDoubledRhythmChance(int val) {
		this.doubledRhythmChance.setInt(val);
	}

	public int getSplitChance() {
		return splitChance.getInt();
	}

	public void setSplitChance(int val) {
		this.splitChance.setInt(val);
	}

	public int getNoteExceptionChance() {
		return noteExceptionChance.getInt();
	}

	public void setNoteExceptionChance(int val) {
		this.noteExceptionChance.setInt(val);
	}

	public int getSpeed() {
		return speed.getInt();
	}

	public void setSpeed(int val) {
		this.speed.setInt(val);
	}

	public int getLeadChordsChance() {
		return leadChordsChance.getInt();
	}

	public void setLeadChordsChance(int val) {
		this.leadChordsChance.setInt(val);
	}

	public int getStartNoteChance() {
		return startNoteChance.getInt();
	}

	public void setStartNoteChance(int val) {
		this.startNoteChance.setInt(val);
	}

	public RandomIntegerListButton getNoteTargetsButton() {
		return noteTargets;
	}

	public RandomIntegerListButton getPatternStructureButton() {
		return patternStructure;
	}

	@Override
	public int getPartNum() {
		return 0;
	}

	@Override
	public Class<? extends InstPart> getPartClass() {
		return MelodyPart.class;
	}

	public boolean getPatternFlexible() {
		return patternFlexible.isSelected();
	}

	public void setPatternFlexible(boolean val) {
		this.patternFlexible.setSelected(val);
	}

	public PhraseNotes getCustomDurationNotes() {
		return PhraseNotes.fromPN(customDurationValues);
	}

	private void setCustomDurationNotes(PhraseNotes customDurationNotes) {
		this.customDurationValues = PhraseNotes.fromPN(customDurationNotes);
	}

	public List<Integer> getCustomDurationChances() {
		return customDurationChances;
	}

	public void setCustomDurationChances(List<Integer> customDurationChances) {
		this.customDurationChances = customDurationChances;
	}
}

