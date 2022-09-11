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
along with this program; if not,
see <https://www.gnu.org/licenses/>.
*/

package org.vibehistorian.vibecomposer.Panels;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;

import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.InstUtils;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Components.CheckButton;
import org.vibehistorian.vibecomposer.Components.InstComboBox;
import org.vibehistorian.vibecomposer.Components.JKnob;
import org.vibehistorian.vibecomposer.Components.MidiMVI;
import org.vibehistorian.vibecomposer.Components.RandomValueButton;
import org.vibehistorian.vibecomposer.Components.RangeSlider;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Components.ScrollComboPanel;
import org.vibehistorian.vibecomposer.Components.VeloRect;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Panels.SoloMuter.State;
import org.vibehistorian.vibecomposer.Parts.InstPart;

public abstract class InstPanel extends JPanel {

	private static final long serialVersionUID = 4381939543337887617L;
	public static final int TARGET_RHYTHM_DENSITY = 8;
	public static final int MAX_RHYTHM_DENSITY = 11;

	protected InstComboBox instrument = new InstComboBox();
	protected InstUtils.POOL instPool = InstUtils.POOL.PLUCK;

	protected MidiMVI midiMVI = new MidiMVI();

	protected KnobPanel hitsPerPattern = new KnobPanel("Hits", 8, 1, VisualPatternPanel.MAX_HITS);
	protected KnobPanel chordSpan = new KnobPanel("Span", 1, 1, 4);
	protected ScrollComboBox<ChordSpanFill> chordSpanFill = new ScrollComboBox<>();
	protected CheckButton fillFlip = new CheckButton("~", false);
	protected TransparentablePanel chordSpanFillPanel = new TransparentablePanel();

	protected KnobPanel chordNotesStretch = new KnobPanel("Voices", 3, 2, 6);
	protected CheckButton stretchEnabled = new CheckButton("", false);
	protected TransparentablePanel stretchPanel = new TransparentablePanel();

	protected KnobPanel pauseChance = new KnobPanel("Pause%", 0);
	protected KnobPanel exceptionChance = new KnobPanel("Split%", 0);
	protected KnobPanel patternRepeat = new KnobPanel("Repeat#", 1, 1, 4);

	protected KnobPanel transpose = new KnobPanel("Transpose", 0, -36, 36, 12);
	protected KnobPanel offset = new KnobPanel("Offset", 0, -1000, 1000);
	protected KnobPanel feedbackCount = new KnobPanel("Delays", 0, 0, 5);
	protected KnobPanel feedbackDuration = new KnobPanel("FB Dur.", 500, -2000, 2000);
	protected KnobPanel feedbackVol = new KnobPanel("FB Vol.", 80, 10, 150);


	protected RangeSlider minMaxVelSlider = new RangeSlider(0, 127);

	protected KnobPanel noteLengthMultiplier = new KnobPanel("Length", 100, 25, 200);

	protected KnobPanel swingPercent = new KnobPanel("Swing%", 50);
	protected KnobPanel accents = new KnobPanel("Accent", 100);

	protected JLabel panelOrder = new JLabel("1");

	protected JLabel patternSeedLabel = new JLabel("Seed");
	protected RandomValueButton patternSeed = new RandomValueButton(0);
	protected ScrollComboBox<RhythmPattern> pattern = new ScrollComboBox<>();
	protected CheckButton patternFlip = new CheckButton("~", false);

	protected VisualPatternPanel comboPanel = null;
	protected KnobPanel patternShift = new KnobPanel("Shift", 0, 0, 8);

	protected CheckButton lockInst = new CheckButton("<html>&#x1F512;</html>", false);
	protected CheckButton muteInst = new CheckButton("Ex", false);

	protected VeloRect volSlider = new VeloRect(0, 100, 100);
	protected VeloRect panSlider = new VeloRect(0, 100, 50);

	protected ScrollComboBox<Integer> midiChannel = new ScrollComboBox<>();

	protected JButton removeButton = new JButton("X");
	protected SoloMuter soloMuter;
	protected JButton copyButton = new JButton("Cc");
	protected JButton randomizeButton = new JButton("?");

	protected Set<Component> toggleableComponents = new HashSet<>();

	protected Integer sequenceTrack = -1;
	protected Section relatedSection = null;

	protected PhraseNotes customMidi = null;

	public InstPanel() {

	}

	public void initDefaultsPost() {
		if (comboPanel != null && pattern.getSelectedIndex() == 0) {
			comboPanel.reapplyShift();
		}
	}

	public void initDefaults(ActionListener l) {
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setMaximumSize(new Dimension(3000, 50));
		for (ChordSpanFill fill : ChordSpanFill.values()) {
			chordSpanFill.addItem(fill);
		}
		panelOrder.setPreferredSize(new Dimension(20, 30));

		setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		//volSlider.setMaximum(100);
		//volSlider.setValue(100);
		//volSlider.setOrientation(JSlider.VERTICAL);
		volSlider.setPreferredSize(new Dimension(15, 35));
		panSlider.setPreferredSize(new Dimension(15, 30));
		//volSlider.setPaintTicks(true);

		minMaxVelSlider.setName("Velocity range");
		setVelocityMax(90);
		setVelocityMin(64);


		if (getPartNum() > 0) {
			for (RhythmPattern d : RhythmPattern.values()) {
				pattern.addItem(d);
			}
		}

		stretchPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
		stretchPanel.setMaximumSize(new Dimension(3000, 50));
		stretchPanel.add(stretchEnabled);
		stretchPanel.add(chordNotesStretch);

		chordSpanFillPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
		chordSpanFillPanel.setMaximumSize(new Dimension(3000, 50));
		chordSpanFillPanel.add(new JLabel("Fill"));
		chordSpanFillPanel.add(chordSpanFill);
		chordSpanFill.setOpaque(false);
		chordSpanFillPanel.add(fillFlip);

		muteInst.setPreferredSize(new Dimension(20, 25));
		muteInst.setMargin(new Insets(0, 0, 0, 0));

		lockInst.setPreferredSize(new Dimension(20, 25));
		lockInst.setMargin(new Insets(0, 0, 0, 0));
		lockInst.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				if (SwingUtilities.isMiddleMouseButton(evt)) {
					for (Component c : getComponents()) {
						if (c instanceof ScrollComboBox) {
							((ScrollComboBox) c).setEnabled(true);
						} else if (c instanceof KnobPanel) {
							((KnobPanel) c).setBlockInput(false);
						} else if (c instanceof JPanel) {
							for (Component c2 : ((JPanel) c).getComponents()) {
								if (c2 instanceof ScrollComboBox) {
									((ScrollComboBox) c2).setEnabled(true);
								} else if (c instanceof KnobPanel) {
									((KnobPanel) c2).setBlockInput(false);
								}
							}
						}
					}
				}
			}
		});

		copyButton.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (!copyButton.isEnabled()) {
					return;
				}
				InstPart part = toInstPart(VibeComposerGUI.lastRandomSeed);
				InstPanel newPanel = VibeComposerGUI.vibeComposerGUI.addInstPanelToLayout(
						VibeComposerGUI.instrumentTabPane.getSelectedIndex(), part, true);
				newPanel.setPatternSeed(getPatternSeed());

				// todo checkbox set cc'd panel's midichannel?
				if (true) {
					newPanel.setMidiChannel(getMidiChannel());
				} else {
					switch (VibeComposerGUI.instrumentTabPane.getSelectedIndex()) {
					case 2:
						newPanel.setMidiChannel(11 + (newPanel.getPanelOrder() - 1) % 5);
						newPanel.setPanByOrder(5);
						break;
					case 3:
						newPanel.setMidiChannel(2 + (newPanel.getPanelOrder() - 1) % 7);
						newPanel.setPanByOrder(7);
						break;
					case 4:
						newPanel.getComboPanel().reapplyHits();
						break;
					default:
						break;
					}
				}

				if (SwingUtilities.isMiddleMouseButton(e)) {
					setChordSpanFill(ChordSpanFill.HALF1);
					newPanel.setChordSpanFill(ChordSpanFill.HALF2);
				}

				VibeComposerGUI.vibeComposerGUI.recalculateTabPaneCounts();
				VibeComposerGUI.vibeComposerGUI.recalculateGenerationCounts();
				VibeComposerGUI.vibeComposerGUI.repaint();
			}

		});
		randomizeButton.addActionListener(l);

		copyButton.setPreferredSize(new Dimension(25, 30));
		copyButton.setMargin(new Insets(0, 0, 0, 0));

		randomizeButton.setActionCommand("RandomizePart");
		randomizeButton.setPreferredSize(new Dimension(15, 30));
		randomizeButton.setMargin(new Insets(0, 0, 0, 0));

		instrument.setPrototype("XXXXXXXXXXXX");

		//transpose.getSlider().setMajorTickSpacing(12);
		//transpose.getSlider().setSnapToTicks(true);

		chordSpan.getKnob().setTickSpacing(50);
		chordSpan.getKnob().setTickThresholds(Arrays.asList(new Integer[] { 1, 2, 4 }));

		feedbackDuration.getKnob().setTickThresholds(VibeComposerGUI.MILISECOND_LIST_FEEDBACK);
		feedbackDuration.getKnob().setTickSpacing(50);

		//toggleableComponents.add(stretchPanel);
		toggleableComponents.add(exceptionChance);
		toggleableComponents.add(offset);
		toggleableComponents.add(feedbackDuration);
		toggleableComponents.add(feedbackCount);
		toggleableComponents.add(feedbackVol);
		toggleableComponents.add(minMaxVelSlider);
		//toggleableComponents.add(noteLengthMultiplier);
		//toggleableComponents.add(patternShift);
		toggleableComponents.add(patternSeed);
		toggleableComponents.add(patternSeedLabel);
		toggleableComponents.add(fillFlip);
		toggleableComponents.add(patternFlip);


		addBackgroundsForKnobs();
		toggleComponentTexts(VibeComposerGUI.isShowingTextInKnobs);
	}

	public void addDefaultInstrumentControls() {
		soloMuter = new SoloMuter(getPartNum(), SoloMuter.Type.SINGLE);
		this.add(soloMuter);
		this.add(muteInst);
		this.add(lockInst);
		midiMVI.setupParent(this);
		this.add(midiMVI);
		this.add(instrument);
	}

	public void addOffsetAndDelayControls() {
		this.add(offset);
		this.add(feedbackCount);
		this.add(feedbackDuration);
		this.add(feedbackVol);
	}

	public void addDefaultPanelButtons() {
		removeButton.addActionListener(e -> {
			VibeComposerGUI.removeInstPanel(getPartNum(), getPanelOrder(), true);
			VibeComposerGUI.vibeComposerGUI.recalculateGeneratorAndTabCounts();
		});
		this.add(removeButton);
		this.add(copyButton);
		this.add(randomizeButton);
	}

	public void addBackgroundsForKnobs() {

		hitsPerPattern.addBackgroundWithBorder(OMNI.alphen(Color.red, 50));
		pauseChance.addBackgroundWithBorder(OMNI.alphen(Color.blue, 50));
		exceptionChance.addBackgroundWithBorder(OMNI.alphen(Color.magenta, 50));
		chordSpan.addBackgroundWithBorder(OMNI.alphen(Color.green, 50));
		swingPercent.addBackgroundWithBorder(OMNI.alphen(Color.yellow.brighter(), 50));
		patternShift.addBackgroundWithBorder(OMNI.alphen(Color.red.darker().darker(), 50));
		transpose.addBackgroundWithBorder(OMNI.alphen(Color.white, 50));
		offset.addBackgroundWithBorder(OMNI.alphen(Color.black, 30));
		feedbackDuration.addBackgroundWithBorder(OMNI.alphen(Color.gray, 50));
		feedbackCount.addBackgroundWithBorder(OMNI.alphen(Color.gray, 40));
		feedbackVol.addBackgroundWithBorder(OMNI.alphen(Color.gray, 30));
		chordNotesStretch.addBackgroundWithBorder(OMNI.alphen(Color.PINK, 30));
		stretchPanel.addBackground(OMNI.alphen(Color.PINK, 30));
		chordSpanFillPanel.addBackground(OMNI.alphen(Color.green.brighter(), 40));
		accents.addBackground(OMNI.alphen(Color.cyan, 40));
		noteLengthMultiplier.addBackground(OMNI.alphen(new Color(100, 170, 240), 50));
	}

	public void toggleComponentTexts(boolean b) {
		// knob texts shown in knobpanels

		hitsPerPattern.setShowTextInKnob(b);
		chordSpan.setShowTextInKnob(b);
		transpose.setShowTextInKnob(b);
		pauseChance.setShowTextInKnob(b);
		exceptionChance.setShowTextInKnob(b);
		patternRepeat.setShowTextInKnob(b);
		offset.setShowTextInKnob(b);
		feedbackDuration.setShowTextInKnob(b);
		feedbackCount.setShowTextInKnob(b);
		feedbackVol.setShowTextInKnob(b);
		swingPercent.setShowTextInKnob(b);
		patternShift.setShowTextInKnob(b);
		chordNotesStretch.setShowTextInKnob(b);
		accents.setShowTextInKnob(b);
		noteLengthMultiplier.setShowTextInKnob(b);

	}

	public void toggleEnabledCopyRemove(boolean isOriginal) {
		removeButton.setEnabled(isOriginal);
		copyButton.setEnabled(isOriginal);
	}

	public void setDefaultsFromInstPart(InstPart part) {
		setInstrument(part.getInstrument());

		setHitsPerPattern(part.getHitsPerPattern());
		setChordSpan(part.getChordSpan());
		setChordSpanFill(part.getChordSpanFill());
		setFillFlip(part.isFillFlip());

		setChordNotesStretch(part.getChordNotesStretch());
		setStretchEnabled(part.isStretchEnabled());

		setPauseChance(part.getPauseChance());
		setExceptionChance(part.getExceptionChance());

		setPatternRepeat(part.getPatternRepeat());

		setTranspose(part.getTranspose());
		setOffset(part.getOffset());
		setFeedbackDuration(part.getFeedbackDuration());
		setFeedbackCount(part.getFeedbackCount());
		setFeedbackVol(part.getFeedbackVol());

		setVelocityMin(part.getVelocityMin());
		setVelocityMax(part.getVelocityMax());
		setNoteLengthMultiplier(part.getNoteLengthMultiplier());

		setSwingPercent(part.getSwingPercent());
		setAccents(part.getAccents());

		setPatternSeed(part.getPatternSeed());
		setPattern(part.getPattern());
		setPatternFlip(part.isPatternFlip());

		if (comboPanel != null && pattern.isEnabled()) {
			comboPanel.setVelocities(part.getCustomVelocities());
			if ((part.getPattern() == RhythmPattern.CUSTOM
					|| part.getPattern() == RhythmPattern.EUCLID)
					&& (part.getCustomPattern() != null)
					&& (part.getCustomPattern().size() == VisualPatternPanel.MAX_HITS)) {
				comboPanel.setTruePattern(new ArrayList<>(part.getCustomPattern()));
			}
		}


		setPatternShift(part.getPatternShift());

		setMidiChannel(part.getMidiChannel());

		volSlider.setValue(part.getSliderVolume());
		panSlider.setValue(part.getSliderPan());

		setMuteInst(part.isMuted());
	}

	public abstract void setFromInstPart(InstPart part);

	public int getHitsPerPattern() {
		return hitsPerPattern.getInt();
	}

	public void setHitsPerPattern(int val) {
		this.hitsPerPattern.setInt(val);
	}

	public int getChordSpan() {
		return chordSpan.getInt();
	}

	public void setChordSpan(int val) {
		this.chordSpan.setInt(val);
	}

	public int getPauseChance() {
		return pauseChance.getInt();
	}

	public void setPauseChance(int val) {
		this.pauseChance.setInt(val);
	}

	public int getExceptionChance() {
		return exceptionChance.getInt();
	}

	public void setExceptionChance(int val) {
		this.exceptionChance.setInt(val);
	}

	public int getTranspose() {
		return transpose.getInt();
	}

	public void setTranspose(int val) {
		this.transpose.setInt(val);
	}

	public int getPatternSeed() {
		return Integer.valueOf(patternSeed.getText());
	}

	public void setPatternSeed(int patternSeed) {
		this.patternSeed.setValue(patternSeed);
	}

	public RhythmPattern getPattern() {
		return pattern.getVal();
	}

	public void setPattern(RhythmPattern pattern) {
		if (pattern == null)
			return;
		this.pattern.setVal(pattern);
	}

	public int getPatternShift() {
		return patternShift.getInt();
	}

	public void setPatternShift(int val) {
		patternShift.setInt(val);
	}

	public int getInstrument() {
		return this.instrument.getInstrument();
	}

	public void setInstrument(int val) {
		this.instrument.setInstrument(val);
	}

	public boolean getLockInst() {
		return lockInst.isSelected();
	}

	public void setLockInst(boolean val) {
		this.lockInst.setSelected(val);
	}

	public boolean getMuteInst() {
		return muteInst.isSelected();
	}

	public void setMuteInst(boolean val) {
		this.muteInst.setSelected(val);
		muteInst.repaint();
	}

	public int getPatternRepeat() {
		return patternRepeat.getInt();
	}

	public void setPatternRepeat(int val) {
		this.patternRepeat.setInt(val);
	}

	public int getMidiChannel() {
		return midiChannel.getVal();
	}

	public void setMidiChannel(int val) {
		this.midiChannel.setVal(val);
	}

	public InstComboBox getInstrumentBox() {
		return instrument;
	}

	public void setInstrumentBox(InstComboBox instComboBox) {
		this.instrument = instComboBox;

	}

	public InstUtils.POOL getInstPool() {
		return instPool;
	}

	public void setInstPool(InstUtils.POOL val) {
		this.instPool = val;
	}

	public ChordSpanFill getChordSpanFill() {
		return chordSpanFill.getVal();
	}

	public void setChordSpanFill(ChordSpanFill val) {
		this.chordSpanFill.setVal(val);
	}

	public int getOffset() {
		return offset.getInt();
	}

	public void setOffset(int val) {
		this.offset.setInt(val);
	}

	public int getChordNotesStretch() {
		return chordNotesStretch.getInt();
	}

	public void setChordNotesStretch(int val) {
		this.chordNotesStretch.setInt(val);
	}

	public boolean getStretchEnabled() {
		return stretchEnabled.isSelected();
	}

	public void setStretchEnabled(boolean val) {
		this.stretchEnabled.setSelected(val);
	}

	public int getVelocityMin() {
		return minMaxVelSlider.getValue();
	}

	public void setVelocityMin(int val) {
		this.minMaxVelSlider.setValue(val);
	}

	public int getVelocityMax() {
		return minMaxVelSlider.getUpperValue();
	}

	public void setVelocityMax(int val) {
		this.minMaxVelSlider.setUpperValue(val);
	}

	public VeloRect getVolSlider() {
		return volSlider;
	}

	public void setVolSlider(VeloRect volSlider) {
		this.volSlider = volSlider;
	}


	public int getSwingPercent() {
		return swingPercent.getInt();
	}

	public void setSwingPercent(int val) {
		this.swingPercent.setInt(val);
	}

	public int getAccents() {
		return accents.getInt();
	}

	public void setAccents(int val) {
		accents.setInt(val);
	}

	public int getPanelOrder() {
		return Integer.valueOf(panelOrder.getText());
	}

	public void setPanelOrder(int val) {
		this.panelOrder.setText("" + val);
	}

	public boolean getFillFlip() {
		return fillFlip.isSelected();
	}

	public void setFillFlip(boolean val) {
		this.fillFlip.setSelected(val);
	}

	public boolean getPatternFlip() {
		return patternFlip.isSelected();
	}

	public void setPatternFlip(boolean val) {
		this.patternFlip.setSelected(val);
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

	public int getNoteLengthMultiplier() {
		return noteLengthMultiplier.getInt();
	}

	public void setNoteLengthMultiplier(int noteLengthMultiplier) {
		this.noteLengthMultiplier.setInt(noteLengthMultiplier);
	}

	public abstract Class<? extends InstPart> getPartClass();

	public Integer getSequenceTrack() {
		return sequenceTrack;
	}

	public void setSequenceTrack(Integer val) {
		this.sequenceTrack = val;
	}

	public abstract InstPart toInstPart(int lastRandomSeed);

	public VisualPatternPanel makeVisualPatternPanel() {
		return new VisualPatternPanel(hitsPerPattern, pattern, patternShift, chordSpan, this);
	}

	public VisualPatternPanel getComboPanel() {
		return comboPanel;
	}


	public static InstPanel makeInstPanel(int inst, ActionListener l) {

		InstPanel ip = null;
		switch (inst) {
		case 0:
			ip = new MelodyPanel(l);
			break;
		case 1:
			ip = new BassPanel(l);
			break;
		case 2:
			ip = new ChordPanel(l);
			break;
		case 3:
			ip = new ArpPanel(l);
			break;
		case 4:
			ip = new DrumPanel(l);
			break;
		}
		return ip;
	}

	public void toggleGlobalElements(boolean b) {
		//getInstrumentBox().setEnabled(b);
		getSoloMuter().setEnabled(b);
		muteInst.setEnabled(b);
		volSlider.setEnabled(b);
	}

	public VeloRect getPanSlider() {
		return panSlider;
	}

	public void setPanSlider(VeloRect panSlider) {
		this.panSlider = panSlider;
	}

	public void setPanByOrder(int panelLimit) {
		setPanByOrder(getPanelOrder(), panelLimit);
	}

	public void setPanByOrder(int order, int panelLimit) {
		order--;
		getPanSlider().setValue(
				50 + (order % 2 == 0 ? 1 : -1) * ((order - 1) % panelLimit) * (49 / panelLimit));
	}

	public void applyPauseChance(Random randGen) {
		if (getPauseChance() > 0 && getPattern() != RhythmPattern.MELODY1
				&& VibeComposerGUI.patternApplyPausesWhenGenerating.isSelected()) {
			long totalAvailable = getComboPanel().getTruePattern().subList(0, getHitsPerPattern())
					.stream().filter(e -> e > 0).count();
			for (int j = 0; j < getHitsPerPattern() && totalAvailable >= 2; j++) {
				if (randGen.nextInt(100) < getPauseChance()
						&& getComboPanel().getTruePattern().get(j) > 0) {
					getComboPanel().checkPattern(getComboPanel().getShifted(j), 0);
					totalAvailable--;
					//LG.d("Pause chance applied");
				}
			}
			setPauseChance(0);
		}
	}

	public Section getRelatedSection() {
		return relatedSection;
	}

	public void setRelatedSection(Section relatedSection) {
		this.relatedSection = relatedSection;
	}

	public boolean isMainPanel() {
		return relatedSection == null;
	}

	public abstract int getPartNum();

	public PhraseNotes getCustomMidi() {
		return customMidi;
	}

	public int getAbsoluteOrder() {
		return VibeComposerGUI.getAbsoluteOrder(getPartNum(), getPanelOrder());
	}

	public int getFeedbackCount() {
		return feedbackCount.getInt();
	}

	public void setFeedbackCount(int val) {
		this.feedbackCount.setInt(val);
	}

	public int getFeedbackDuration() {
		return feedbackDuration.getInt();
	}

	public void setFeedbackDuration(int val) {
		this.feedbackDuration.setInt(val);
	}

	public int getFeedbackVol() {
		return feedbackVol.getInt();
	}

	public void setFeedbackVol(int val) {
		this.feedbackVol.setInt(val);
	}

	public String panelInfo() {
		return "Part: " + getPartNum() + ", order: " + getPanelOrder();
	}

	public void growPattern(Random randGen, int maxGrowth, int growthChance) {
		if (getPattern() == RhythmPattern.MELODY1 || getPattern() == RhythmPattern.FULL) {
			return;
		}

		List<Integer> ptrn = getComboPanel().getTruePattern().subList(0, getHitsPerPattern());
		List<Integer> emptyIndices = new ArrayList<>();
		for (int i = 0; i < ptrn.size(); i++) {
			if (ptrn.get(i) < 1) {
				emptyIndices.add(i);
			}
		}
		if (emptyIndices.isEmpty()) {
			return;
		}
		//LG.i("Changing pattern: " + getPattern().toString());
		for (int i = 0; i < maxGrowth; i++) {
			if (randGen.nextInt(100) < growthChance && !emptyIndices.isEmpty()) {
				int index = emptyIndices.get(randGen.nextInt(emptyIndices.size()));
				getComboPanel().checkPattern(getComboPanel().getShifted(index), 1);
				//LG.i(panelInfo() + ", checked: " + index);
			}
		}

	}

	public int addToRhythmGrid(int[] rhythmGrid, Random rand, Random permutationRand,
			int multiplier) {
		// forward calculation
		long totalAvailable = getComboPanel().getTruePattern().subList(0, getHitsPerPattern())
				.stream().filter(e -> e > 0).count();
		Pair<List<Integer>, Map<Integer, Integer>> mappedGrid = makeMappedRhythmGrid();
		List<Integer> panelRhythmGrid = mappedGrid.getLeft();
		//StringUtils.join(rhythmGrid, ",");
		List<Integer> fillPattern = getChordSpanFill().getPatternByLength(4, getFillFlip());
		for (int i = 0; i < 4 * 32; i++) {
			if (fillPattern.get(i / 32) < 1) {
				panelRhythmGrid.set(i, 0);
			}
		}
		//LG.i(getPartNum() + "grid: " + StringUtils.join(panelRhythmGrid, ','));

		// sort by most crowded after addition of this layer / most needing of sidechaining
		List<Pair<Integer, Integer>> orderOverlapPairs = new ArrayList<>();
		for (int i = 0; i < panelRhythmGrid.size(); i++) {
			if (panelRhythmGrid.get(i) != null && panelRhythmGrid.get(i) > 0) {
				Integer mapped = mappedGrid.getRight().get(i);
				if (mapped == null) {
					continue;
				}
				int nextVal = rhythmGrid[i] + panelRhythmGrid.get(i) * multiplier;
				orderOverlapPairs.add(Pair.of(i, nextVal));
			} else {
				orderOverlapPairs.add(Pair.of(i, rhythmGrid[i]));
			}
		}
		Collections.sort(orderOverlapPairs, (p1, p2) -> (p2.getRight().compareTo(p1.getRight())));

		/*LG.i(StringUtils.join(orderOverlapPairs.stream().map(e -> e.getLeft() + "|" + e.getRight())
				.collect(Collectors.toList()), ","));*/

		int changed = 0;
		for (int i = 0; i < panelRhythmGrid.size(); i++) {
			Integer index = orderOverlapPairs.get(i).getLeft();
			if (panelRhythmGrid.get(index) != null && panelRhythmGrid.get(index) > 0) {
				Integer mapped = mappedGrid.getRight().get(index);
				if (mapped == null) {
					LG.i("Skipping not mapped " + index);
					continue;
				}
				int nextVal = rhythmGrid[index] + panelRhythmGrid.get(index) * multiplier;
				if (totalAvailable >= 2 && getComboPanel().getPattern(mapped) > 0) {
					if (nextVal > MAX_RHYTHM_DENSITY
							|| (nextVal > TARGET_RHYTHM_DENSITY && rand.nextInt(100) < 50)) {
						// remove - backward calculation
						getComboPanel().checkPattern(mapped, 0);
						totalAvailable--;
						changed++;
						//LG.i(panelInfo() + " Unchecked: " + mapped);
						//rhythmGrid[i] = nextVal;
					} else {
						rhythmGrid[index] = nextVal;
					}
				} else {
					rhythmGrid[index] = nextVal;
				}

			}
		}
		return changed;
	}

	public List<Integer> getFinalPatternCopy() {
		List<Integer> premadePattern = null;
		if (getPattern() != RhythmPattern.CUSTOM) {
			RhythmPattern d = getPattern();
			int shift = getPatternShift();
			int hits = getHitsPerPattern();
			premadePattern = (d == RhythmPattern.EUCLID)
					? RhythmPattern.makeEuclideanPattern(hits,
							(int) comboPanel.getTruePattern().subList(0, hits).stream()
									.filter(e -> e > 0).count(),
							shift, null)
					: d.getPatternByLength(hits, shift);
		} else {
			List<Integer> premadeCopy = new ArrayList<>(getComboPanel().getTruePattern());
			Collections.rotate(premadeCopy, getPatternShift());
			premadePattern = premadeCopy;
		}
		return premadePattern;
	}

	protected Pair<List<Integer>, Map<Integer, Integer>> makeMappedRhythmGrid() {

		// shift
		List<Integer> rhythmGridBase = getFinalPatternCopy().subList(0, getHitsPerPattern());
		int realHits = getHitsPerPattern() * getPatternRepeat();
		for (int i = 0; i < getHitsPerPattern(); i++) {
			if (rhythmGridBase.get(i) > 0) {
				rhythmGridBase.set(i, i + 1);
			}
		}
		for (int i = 0; i < getPatternRepeat() - 1; i++) {
			List<Integer> toAdd = rhythmGridBase.subList(0, getHitsPerPattern());
			rhythmGridBase.addAll(toAdd);
		}

		// span
		double rhythmMultiplier = 32 / (double) realHits;
		int[] rhythmGridStretched = new int[32];
		for (int i = 0; i < rhythmGridBase.size(); i++) {
			if (rhythmGridBase.get(i) > 0) {
				//LG.i("" + i * rhythmMultiplier + " " + (int) Math.round(i * rhythmMultiplier));
				int placement = Math.min(31, (int) Math.round(i * rhythmMultiplier));
				rhythmGridStretched[placement] = rhythmGridBase.get(i);
			}
		}
		List<Integer> rhythmGridSpanned = MidiUtils.intArrToList(rhythmGridStretched);
		//LG.i("Temp grid: " + StringUtils.join(rhythmGridSpanned, ";"));
		rhythmGridSpanned = MidiUtils.intersperse(0, getChordSpan() - 1, rhythmGridSpanned);
		//LG.i("Size: " + rhythmGridSpanned.size());
		int sizeSoFar = rhythmGridSpanned.size();
		while (rhythmGridSpanned.size() < 4 * 32) {
			List<Integer> toAdd = rhythmGridSpanned.subList(0, sizeSoFar);
			rhythmGridSpanned.addAll(toAdd);
		}
		// delay
		int delayShift = getOffset() / 125;
		if (delayShift != 0) {
			Collections.rotate(rhythmGridSpanned, delayShift);
		}


		Map<Integer, Integer> gridMap = new HashMap<>();
		for (int i = 0; i < getHitsPerPattern(); i++) {
			for (int j = 0; j < rhythmGridSpanned.size(); j++) {
				Integer val = rhythmGridSpanned.get(j);
				if (val != null && val == i + 1) {
					gridMap.put(j, i);
				}
			}
		}
		/*LG.i(gridMap.entrySet().stream().collect(Collectors.groupingBy(Map.Entry::getValue,
				Collectors.mapping(Map.Entry::getKey, Collectors.toList()))).toString());*/
		for (int i = 0; i < rhythmGridSpanned.size(); i++) {
			if (rhythmGridSpanned.get(i) > 0) {
				rhythmGridSpanned.set(i, 1);
			}
		}
		return Pair.of(rhythmGridSpanned, gridMap);
	}

	public List<ScrollComboPanel> findScrollComboBoxesByFirstVal(Object firstVal) {
		List<ScrollComboPanel> allBoxes = getChildComponents(ScrollComboPanel.class, this, true);
		if (firstVal == null) {
			return allBoxes;
		}
		List<ScrollComboPanel> namedBoxes = allBoxes.stream()
				.filter(e -> firstVal.equals(e.getItemAt(0))).collect(Collectors.toList());
		//LG.i("Found boxes: " + namedBoxes.size());
		return namedBoxes;
	}

	public List<JKnob> findKnobsByName(String name) {
		List<JKnob> allKnobs = getChildComponents(JKnob.class, this, true);
		if (name == null) {
			return allKnobs;
		}
		List<JKnob> namedKnobs = allKnobs.stream().filter(e -> name.equals(e.getName()))
				.collect(Collectors.toList());
		return namedKnobs;
	}

	public <T extends JComponent> List<T> getAllComponentsLike(JComponent c, Class<T> clazz) {
		int indexInPanel = findIndexOfComponent(c, clazz);
		if (indexInPanel < 0) {
			LG.i("Found no component for global setting!");
			return new ArrayList<>();
		}
		List<T> components = VibeComposerGUI.getAffectedPanels(getPartNum()).stream()
				.map(e -> e.getComponentByClassIndex(clazz, indexInPanel))
				.collect(Collectors.toList());
		return components;
	}

	public <T extends JComponent> int findIndexOfComponent(JComponent c, Class<T> clazz) {
		List<T> children = getChildComponents(clazz, this, true);
		//LG.i("Found children: " + children.size());
		for (int i = 0; i < children.size(); i++) {
			//LG.i("Hash1: " + children.get(i).hashCode() + ", hash2: " + c.hashCode());
			if (clazz.isInstance(children.get(i)) && children.get(i).equals(c)) {
				return i;
			}
		}
		return -1;
	}

	public <T extends JComponent> T getComponentByClassIndex(Class<T> clazz, int index) {
		return getChildComponents(clazz, this, true).get(index);
	}

	public static <T extends JComponent> List<T> getChildComponents(Class<T> clazz,
			Container parent, boolean includeNested) {

		List<T> children = new ArrayList<T>();

		for (Component c : parent.getComponents()) {
			boolean isClazz = clazz.isAssignableFrom(c.getClass());
			if (isClazz) {
				children.add(clazz.cast(c));
			}
			if (includeNested && c instanceof Container) {
				children.addAll(getChildComponents(clazz, (Container) c, includeNested));
			}
		}

		return children;
	}
}
