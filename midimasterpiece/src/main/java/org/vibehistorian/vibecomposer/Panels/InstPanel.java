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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;

import org.vibehistorian.vibecomposer.InstComboBox;
import org.vibehistorian.vibecomposer.InstUtils;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Helpers.CheckButton;
import org.vibehistorian.vibecomposer.Helpers.OMNI;
import org.vibehistorian.vibecomposer.Helpers.RandomValueButton;
import org.vibehistorian.vibecomposer.Helpers.RangeSlider;
import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;
import org.vibehistorian.vibecomposer.Helpers.VeloRect;
import org.vibehistorian.vibecomposer.Panels.SoloMuter.State;
import org.vibehistorian.vibecomposer.Parts.InstPart;

public abstract class InstPanel extends JPanel {

	private static final long serialVersionUID = 4381939543337887617L;

	protected InstComboBox instrument = new InstComboBox();
	protected InstUtils.POOL instPool = InstUtils.POOL.PLUCK;

	protected KnobPanel hitsPerPattern = new KnobPanel("Hits", 8, 1, VisualPatternPanel.MAX_HITS);
	protected KnobPanel chordSpan = new KnobPanel("Chords", 1, 1, 4);
	protected ScrollComboBox<ChordSpanFill> chordSpanFill = new ScrollComboBox<>();
	protected CheckButton fillFlip = new CheckButton("~", false);
	protected TransparentablePanel chordSpanFillPanel = new TransparentablePanel();

	protected KnobPanel chordNotesStretch = new KnobPanel("Voices", 3, 2, 6);
	protected CheckButton stretchEnabled = new CheckButton("", false);
	protected TransparentablePanel stretchPanel = new TransparentablePanel();

	protected KnobPanel pauseChance = new KnobPanel("Pause%", 0);
	protected KnobPanel exceptionChance = new KnobPanel("Split%", 0);
	protected CheckButton repeatableNotes = new CheckButton("Note<br>Repeat", true);
	protected KnobPanel patternRepeat = new KnobPanel("Repeat#", 1, 1, 4);

	protected KnobPanel transpose = new KnobPanel("Transpose", 0, -36, 36, 12);
	protected KnobPanel delay = new KnobPanel("Delay", 0, -500, 500);


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

	protected CheckButton lockInst = new CheckButton("Lock", false);
	protected CheckButton muteInst = new CheckButton("Excl.", false);

	protected VeloRect volSlider = new VeloRect(0, 100, 100);

	protected ScrollComboBox<Integer> midiChannel = new ScrollComboBox<>();

	protected JButton removeButton = new JButton("X");
	protected SoloMuter soloMuter;
	protected JButton copyButton = new JButton("Cc");
	protected JButton randomizeButton = new JButton("?");

	protected Set<Component> toggleableComponents = new HashSet<>();

	protected Class<? extends InstPart> partClass = InstPart.class;
	protected Integer sequenceTrack = -1;

	public InstPanel() {

	}

	public void initDefaults(ActionListener l) {
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
		//volSlider.setPaintTicks(true);

		minMaxVelSlider.setName("Velocity range");
		setVelocityMax(90);
		setVelocityMin(63);

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

		lockInst.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				if (SwingUtilities.isMiddleMouseButton(evt)) {
					if (evt.isControlDown()) {
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
			}
		});

		copyButton.addActionListener(l);
		randomizeButton.addActionListener(l);

		copyButton.setActionCommand("CopyPart");
		copyButton.setPreferredSize(new Dimension(25, 30));
		copyButton.setMargin(new Insets(0, 0, 0, 0));

		randomizeButton.setActionCommand("RandomizePart");
		randomizeButton.setPreferredSize(new Dimension(15, 30));
		randomizeButton.setMargin(new Insets(0, 0, 0, 0));

		//transpose.getSlider().setMajorTickSpacing(12);
		//transpose.getSlider().setSnapToTicks(true);

		chordSpan.getKnob().setTickSpacing(50);
		chordSpan.getKnob().setTickThresholds(Arrays.asList(new Integer[] { 1, 2, 4 }));

		//toggleableComponents.add(stretchPanel);
		toggleableComponents.add(exceptionChance);
		toggleableComponents.add(delay);
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
		this.add(soloMuter);
		this.add(muteInst);
		this.add(lockInst);
		this.add(instrument);
	}

	public void addDefaultPanelButtons() {
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
		delay.addBackgroundWithBorder(OMNI.alphen(Color.black, 30));
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
		delay.setShowTextInKnob(b);
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

		setRepeatableNotes(part.isRepeatableNotes());
		setPatternRepeat(part.getPatternRepeat());

		setTranspose(part.getTranspose());
		setDelay(part.getDelay());

		setVelocityMin(part.getVelocityMin());
		setVelocityMax(part.getVelocityMax());
		setNoteLengthMultiplier(part.getNoteLengthMultiplier());

		setSwingPercent(part.getSwingPercent());
		setAccents(part.getAccents());

		setPatternSeed(part.getPatternSeed());
		setPattern(part.getPattern());
		setPatternFlip(part.isPatternFlip());


		if (comboPanel != null) {
			comboPanel.setVelocities(part.getCustomVelocities());
			if (part.getPattern() == RhythmPattern.CUSTOM && part.getCustomPattern() != null
					&& part.getCustomPattern().size() == VisualPatternPanel.MAX_HITS) {
				comboPanel.setTruePattern(new ArrayList<>(part.getCustomPattern()));
			}
		}


		setPatternShift(part.getPatternShift());

		setMidiChannel(part.getMidiChannel());

		volSlider.setValue(part.getSliderVolume());

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
		this.patternSeed.setText(String.valueOf(patternSeed));
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

	public boolean getRepeatableNotes() {
		return repeatableNotes.isSelected();
	}

	public void setRepeatableNotes(boolean val) {
		this.repeatableNotes.setSelected(val);
	}

	public int getPatternRepeat() {
		return patternRepeat.getInt();
	}

	public void setPatternRepeat(int val) {
		this.patternRepeat.setInt(val);
	}

	public int getMidiChannel() {
		return Integer.valueOf(midiChannel.getVal());
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

	public int getDelay() {
		return delay.getInt();
	}

	public void setDelay(int val) {
		this.delay.setInt(val);
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
		String removeActionString = removeButton.getActionCommand().split(",")[0];
		removeButton.setActionCommand(removeActionString + "," + val);
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

	public Class<? extends InstPart> getPartClass() {
		return partClass;
	}

	public void setPartClass(Class<? extends InstPart> partClass) {
		this.partClass = partClass;
	}

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
		getInstrumentBox().setEnabled(b);
		getSoloMuter().setEnabled(b);
		muteInst.setEnabled(b);
		volSlider.setEnabled(b);
	}
}
