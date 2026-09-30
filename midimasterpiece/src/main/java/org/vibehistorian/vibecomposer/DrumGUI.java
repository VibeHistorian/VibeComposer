/* --------------------
* @author Vibe Historian
* ---------------------

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not,
see <https://www.gnu.org/licenses/>.
*/

package org.vibehistorian.vibecomposer;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.DynamicGridLayout;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Components.VeloRect;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Panels.DetachedKnobPanel;
import org.vibehistorian.vibecomposer.Panels.DrumPanel;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;
import org.vibehistorian.vibecomposer.Panels.PartManagerPanel;
import org.vibehistorian.vibecomposer.Panels.SoloMuter;
import org.vibehistorian.vibecomposer.Panels.VisualPatternPanel;
import org.vibehistorian.vibecomposer.Parts.Defaults.DrumDefaults;
import org.vibehistorian.vibecomposer.Parts.Defaults.DrumSettings;
import org.vibehistorian.vibecomposer.Parts.DrumPart;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

/** Builds and owns drum controls and drum panel generation. */
public class DrumGUI extends InstGUI<DrumPanel> {

	public VeloRect drumVolumeSlider;
	public JCheckBox bottomUpReverseDrumPanels;

	public static final List<Integer> PUNCHY_DRUMS = Collections.unmodifiableList(
			Arrays.asList(35, 36, 38, 39, 40));
	private static final List<Integer> KICK_DRUMS = Collections.unmodifiableList(Arrays.asList(35, 36));
	private static final List<Integer> SNARE_DRUMS = Collections.unmodifiableList(Arrays.asList(38, 40));
	public JCheckBox randomDrumsGenerateOnCompose;
	public KnobPanel randomDrumsOverrandomize;
	public KnobPanel randomDrumMaxSwingAdjust;
	public JCheckBox randomDrumSlide;
	public JCheckBox randomDrumPattern;
	public KnobPanel randomDrumVelocityPatternChance;
	public KnobPanel randomDrumShiftChance;
	public JCheckBox randomDrumUseChordFill;
	public KnobPanel humanizeDrums;
	public ScrollComboBox<String> randomDrumHitsMultiplier;
	public ScrollComboBox<String> randomDrumHitsMultiplierOnGenerate;
	public JCheckBox drumCustomMapping;
	public JTextField drumCustomMappingNumbers;
	public JCheckBox combineDrumTracks;

	private final Context context;

	public DrumGUI(Context context, InstrumentPanelController panelController) {
		super(INST.DRUM, panelController);
		this.context = context;
	}

	@Override public DrumPanel createPanel(SoloMuter.Context soloMuterContext) {
		return new DrumPanel(soloMuterContext);
	}

	@Override public void createRandomPanels(int panelCount, boolean onlyAdd,
			Integer seed, InstPanel randomizedPanel) {
		createRandomDrumPanels(panelCount, onlyAdd, (DrumPanel) randomizedPanel);
	}

	@Override public boolean reversePanelOrder() {
		return bottomUpReverseDrumPanels.isSelected();
	}

	public void saveToConfig(GUIConfig gc, int seed, boolean customMidiDevice) {
		gc.setDrumsEnable(enabledCheckBox.isSelected());
		gc.setDrumParts(createParts(seed, DrumPart.class));
		gc.setHumanizeDrums(humanizeDrums.getInt());
		gc.setDrumCustomMapping(drumCustomMapping.isSelected() && customMidiDevice);
		gc.setDrumCustomMappingNumbers(drumCustomMappingNumbers.getText());
	}

	public void loadFromConfig(GUIConfig gc) {
		enabledCheckBox.setSelected(gc.isDrumsEnable());
		humanizeDrums.setInt(gc.getHumanizeDrums());
		drumCustomMappingNumbers.setText(gc.getDrumCustomMappingNumbers());
		if (StringUtils.countMatches(drumCustomMappingNumbers.getText(), ",") != InstUtils.DRUM_INST_NUMBERS_SEMI.length - 1) {
			drumCustomMappingNumbers.setText(StringUtils.join(InstUtils.DRUM_INST_NUMBERS_SEMI, ","));
		}
	}

	public void loadPartsFromConfig(GUIConfig gc, Consumer<List<DrumPart>> restorePanels) {
		restorePanels.accept(gc.getDrumParts());
	}

	/** Supplies shared window operations without coupling this module to the main window. */
	public interface Context {
		PartManagerPanel.Context getPartManagerContext();
		int getLastRandomSeed();
	}

	public void initDrumGenSettings() {
		JPanel scrollableDrumPanels = new JPanel();
		scrollableDrumPanels.setLayout(new BoxLayout(scrollableDrumPanels, BoxLayout.Y_AXIS));
		scrollableDrumPanels.setAutoscrolls(true);

		panelScrollPane = createPanelScrollPane(scrollableDrumPanels);

		JPanel drumsPanel = new JPanel();
		drumsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		addPanelControls(drumsPanel, "DRUMS", "+Drum", "Generate Drums:", "6", () -> {
			drumVolumeSlider = VeloRect.percent(65);
			//drumVolumeSlider.setOrientation(JSlider.VERTICAL);
			drumVolumeSlider.setPreferredSize(new Dimension(15, 35));
			//drumVolumeSlider.setPaintTicks(true);
			drumsPanel.add(new JLabel("Vol."));
			drumsPanel.add(drumVolumeSlider);
		});
		//drumsPanel.add(drumInst);

		randomDrumsGenerateOnCompose = SwingUtils.makeCheckBox("on Compose", true, true);
		drumsPanel.add(randomDrumsGenerateOnCompose);

		JButton clearPatternSeeds = SwingUtils.makeButton("Clear Seeds",
				e -> panelController.getAffectedPanels(INST.DRUM)
						.forEach(panel -> panel.setPatternSeed(0)));

		randomDrumMaxSwingAdjust = new DetachedKnobPanel("Max Swing+-", 20, 0, 50);
		randomDrumSlide = new CustomCheckBox("Random Offset", false);
		randomDrumUseChordFill = new CustomCheckBox("Fills", true);
		randomDrumPattern = new CustomCheckBox("Patterns", true);
		randomDrumVelocityPatternChance = new DetachedKnobPanel("Dynamic%", 50);
		randomDrumShiftChance = new DetachedKnobPanel("Shift%", 50);

		drumsPanel.add(new JLabel("Max swing%+-"));
		drumsPanel.add(randomDrumMaxSwingAdjust);
		drumsPanel.add(randomDrumUseChordFill);

		randomDrumHitsMultiplier = new ScrollComboBox<>();
		ScrollComboBox.addAll(new String[] { OMNI.EMPTYCOMBO, "0.5x", "0.75x", "1.5x", "2x" },
				randomDrumHitsMultiplier);
		randomDrumHitsMultiplier.setVal(OMNI.EMPTYCOMBO);
		randomDrumHitsMultiplierOnGenerate = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new String[] { OMNI.EMPTYCOMBO, "0.5x", "0.75x", "1.5x", "2x" },
				randomDrumHitsMultiplierOnGenerate);
		randomDrumHitsMultiplierOnGenerate.setVal(OMNI.EMPTYCOMBO);

		randomDrumHitsMultiplier.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent event) {
				List<DrumPanel> affectedDrums = (List<DrumPanel>) (List<?>)
						panelController.getAffectedPanels(INST.DRUM);
				if (event.getStateChange() == ItemEvent.SELECTED) {
					for (int i = 0; i < affectedDrums.size(); i++) {
						int newHits = affectedDrums.get(i).getHitsPerPattern();
						switch (randomDrumHitsMultiplier.getSelectedIndex()) {
						case 0:
							return;
						case 1:
							affectedDrums.get(i).setHitsPerPattern(newHits / 2);
							break;
						case 2:
							affectedDrums.get(i).setHitsPerPattern(newHits * 3 / 4);
							break;
						case 3:
							affectedDrums.get(i).setHitsPerPattern(newHits * 3 / 2);
							break;
						case 4:
							affectedDrums.get(i).setHitsPerPattern(newHits * 2);
							break;
						default:
							throw new IllegalArgumentException("Multiplier too high!");
						}
						if (randomDrumHitsMultiplier.getSelectedIndex() > 1
								&& affectedDrums.get(i).getPattern() == RhythmPattern.CUSTOM) {
							List<Integer> trueSub = affectedDrums.get(i).getComboPanel()
									.getTruePattern().subList(0, newHits);
							Collections.rotate(trueSub, affectedDrums.get(i).getPatternShift());
							affectedDrums.get(i).setPatternShift(0);
							while (trueSub.size() < VisualPatternPanel.MAX_HITS) {
								trueSub.addAll(trueSub);
							}
							affectedDrums.get(i).getComboPanel().setTruePattern(
									trueSub.subList(0, VisualPatternPanel.MAX_HITS));
						}
					}

					randomDrumHitsMultiplier.setVal(OMNI.EMPTYCOMBO);
				}
			}
		});

		drumsPanel.add(new JLabel("Multiply Hits By"));
		drumsPanel.add(randomDrumHitsMultiplier);
		drumsPanel.add(new JLabel("On Generate"));
		drumsPanel.add(randomDrumHitsMultiplierOnGenerate);
		drumsPanel.add(randomDrumSlide);


		JPanel drumExtraSettings = new JPanel();
		JLabel csExtra = new JLabel("DRUM SETTINGS+");
		csExtra.setPreferredSize(new Dimension(120, 30));
		csExtra.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		drumExtraSettings.add(csExtra);


		combineDrumTracks = new CustomCheckBox("Combine MIDI Tracks", true);
		drumExtraSettings.add(combineDrumTracks);

		drumExtraSettings.add(randomDrumPattern);
		drumExtraSettings.add(randomDrumVelocityPatternChance);

		drumExtraSettings.add(randomDrumShiftChance);

		randomDrumsOverrandomize = new DetachedKnobPanel("Overrandomize", 0, 0, 100);
		drumExtraSettings.add(randomDrumsOverrandomize);
		drumExtraSettings.add(clearPatternSeeds);
		drumExtraSettings.add(new PartManagerPanel(INST.DRUM, context.getPartManagerContext()));

		UITheme.toggleableComponents.add(drumExtraSettings);

		drumsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		drumsPanel.setMaximumSize(new Dimension(1800, 50));
		scrollableDrumPanels.add(drumsPanel);
		drumExtraSettings.setAlignmentX(Component.LEFT_ALIGNMENT);
		drumExtraSettings.setMaximumSize(new Dimension(1800, 50));
		//constraints.gridy = startY + 1;
		//scrollableDrumPanels.add(drumExtraSettings);

		parentPanel = new JPanel() {
			@Override
			public Dimension getPreferredSize() {
				return UITheme.scrollPaneDimension;
			}
		};
		parentPanel.setLayout(new BoxLayout(parentPanel, BoxLayout.Y_AXIS));

		JPanel borderPanel = new JPanel() {
			@Override
			public Dimension getMaximumSize() {
				return new Dimension(UITheme.scrollPaneDimension.width, 100);
			}
		};
		borderPanel.setLayout(new DynamicGridLayout(0, 1));
		borderPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
		borderPanel.add(drumsPanel);
		borderPanel.add(drumExtraSettings);
		parentPanel.add(borderPanel);
		parentPanel.add(panelScrollPane);

		//addHorizontalSeparatorToPanel(scrollableDrumPanels);
	}

	public JPanel initDrums() {
		return parentPanel;
	}

	public void createRandomDrumPanels(int panelCount, boolean onlyAdd,
			DrumPanel randomizedPanel) {
		ScrollComboBox.discardInteractions();
		List<DrumPanel> affectedDrums = (List<DrumPanel>) (List<?>)
				panelController.getAffectedPanels(INST.DRUM);

		Random panelGenerator = new Random();
		List<DrumPanel> removedPanels = new ArrayList<>();
		List<DrumPanel> remainingPanels = new ArrayList<>();
		for (Iterator<DrumPanel> panelI = affectedDrums.iterator(); panelI.hasNext();) {
			DrumPanel panel = panelI.next();
			if (!onlyAdd && !panel.getLockInst()) {
				if (removedPanels.size() >= panelCount) {
					((JPanel) panelScrollPane.getViewport().getView()).remove(panel);
					panelI.remove();
				} else {
					removedPanels.add(panel);
				}
			} else {
				remainingPanels.add(panel);
			}

		}
		Collections.sort(removedPanels, Comparator.comparing(e1 -> e1.getPanelOrder()));

		panelCount -= remainingPanels.size();

		int slide = 0;

		if (randomDrumSlide.isSelected()) {
			slide = panelGenerator.nextInt(100) - 50;
		}

		int swingPercent = 50;
		if (onlyAdd && remainingPanels.size() > 0) {
			Optional<Integer> existingSwing = remainingPanels.stream()
					.filter(e -> (e.getSwingPercent() != 50)).map(e -> e.getSwingPercent())
					.findFirst();
			if (existingSwing.isPresent()) {
				swingPercent = existingSwing.get();
			}
		}
		// nothing's changed.. still the same..
		if (swingPercent == 50) {
			swingPercent = 50 + panelGenerator.nextInt(randomDrumMaxSwingAdjust.getInt() * 2 + 1)
					- randomDrumMaxSwingAdjust.getInt();
		}


		List<Integer> pitches = new ArrayList<>();
		for (int i = 0; i < panelCount; i++) {
			pitches.add(InstUtils.getInstByIndex(panelGenerator.nextInt(127), InstUtils.POOL.DRUM));
		}
		Collections.sort(pitches);
		int index = 0;
		long kickCount = remainingPanels.stream()
				.filter(e -> KICK_DRUMS.contains(e.getInstrument())).count();
		long snareCount = remainingPanels.stream()
				.filter(e -> SNARE_DRUMS.contains(e.getInstrument())).count();
		if (!onlyAdd && pitches.size() >= 3) {
			//LG.i(("Kick,snare: " + kickCount + ", " + snareCount));
			if (kickCount == 0) {
				pitches.set(index++, 35);
				pitches.set(index++, 36);
			} else if (kickCount == 1) {
				pitches.set(index++, 36);
			}


			if (snareCount == 0) {
				pitches.set(index++, panelGenerator.nextBoolean() ? 38 : 40);
			}
		} else if (onlyAdd) {
			List<Integer> allowedInsts = new ArrayList<>(
					Arrays.asList(InstUtils.DRUM_INST_NUMBERS));
			if (snareCount >= 1) {
				allowedInsts.remove(3);
				allowedInsts.remove(4);
			} else if (kickCount >= 1) {
				allowedInsts.clear();
				allowedInsts.add(38);
				allowedInsts.add(40);
			}
			if (kickCount >= 2 && snareCount >= 1) {
				allowedInsts.remove(0);
				allowedInsts.remove(0);
			} else if (kickCount == 0) {
				allowedInsts.clear();
				allowedInsts.add(35);
				allowedInsts.add(36);
			}
			pitches.set(0, allowedInsts.get(panelGenerator.nextInt(allowedInsts.size())));
		}
		if (!onlyAdd) {
			if (pitches.stream().filter(e -> KICK_DRUMS.contains(e)).count() > 1) {
				for (int i = index; i < pitches.size(); i++) {
					int e = pitches.get(i);
					if (KICK_DRUMS.contains(e)) {
						pitches.set(i, i % 2 == 0 ? 46 : 60);
					}
				}
			}

			if (pitches.stream().filter(e -> SNARE_DRUMS.contains(e)).count() > 1) {
				for (int i = index; i < pitches.size(); i++) {
					int e = pitches.get(i);
					if (SNARE_DRUMS.contains(e)) {
						pitches.set(i, i % 2 == 0 ? 42 : 44);
					}
				}
			}
			Collections.sort(pitches);
		}


		int chords = 2;
		int maxPatternPerChord = VisualPatternPanel.MAX_HITS;

		/*int[] drumHitGrid = IntStream.iterate(0, e -> e).limit(chords * maxPatternPerChord)
				.toArray();*/
		for (int panelIndex = 0; panelIndex < panelCount; panelIndex++) {
			DrumPanel ip = null;
			if (randomizedPanel != null) {
				ip = randomizedPanel;
			} else {
				if (panelIndex < removedPanels.size()) {
					ip = removedPanels.get(panelIndex);
				} else {
					ip = (DrumPanel) panelController.addPanel(INST.DRUM);
				}
			}

			if (new Random().nextInt(100) < randomDrumsOverrandomize.getInt()) {
				setupOverrandomizedDrum(panelGenerator, slide, swingPercent, pitches, panelIndex,
						ip);
			} else {
				setupBlueprintedDrum(panelGenerator, slide, swingPercent, pitches, panelIndex, ip);
			}


			/*DrumPart panelPart = dp.toDrumPart(context.getLastRandomSeed());
			int[] drumPartArray = displayDrumPart(panelPart, chords, maxPatternPerChord);
			for (int j = 0; j < drumPartArray.length; j++) {
				drumHitGrid[j] += drumPartArray[j];
			}*/
			/*if (needPanApplied) {
				dp.getPanSlider().setValue(drumPanelGenerator.nextInt(100));
			}*/

		}
		/*for (int i = 0; i < chords * maxPatternPerChord; i++) {
			System.out.print(drumHitGrid[i] + ", ");
		}*/

	}
	private void setupBlueprintedDrum(Random panelGenerator, int slide, int swingPercent,
			List<Integer> pitches, int panelIndex, DrumPanel ip) {
		DrumPart dpart = DrumDefaults.getDrumFromInstrument(
				!ip.getInstrumentBox().isEnabled() ? ip.getInstrument() : pitches.get(panelIndex));
		int order = DrumDefaults.getOrder(dpart.getInstrument());
		DrumSettings settings = DrumDefaults.drumSettings[order];
		settings.applyToDrumPart(dpart, context.getLastRandomSeed());


		dpart.setOrder(ip.getPanelOrder());
		dpart.setMuted(ip.getMuteInst());
		switch (randomDrumHitsMultiplierOnGenerate.getSelectedIndex()) {
		case 0:
			break;
		case 1:
			dpart.setHitsPerPattern(dpart.getHitsPerPattern() / 2);
			break;
		case 2:
			dpart.setHitsPerPattern(dpart.getHitsPerPattern() * 3 / 4);
			break;
		case 3:
			dpart.setHitsPerPattern(dpart.getHitsPerPattern() * 3 / 2);
			break;
		case 4:
			dpart.setHitsPerPattern(dpart.getHitsPerPattern() * 2);
			break;
		default:
			throw new IllegalArgumentException("Multiplier index too high.");
		}
		ip.setFromInstPart(dpart);

		//dp.setHitsPerPattern(dp.getHitsPerPattern() * randomDrumHitsMultiplierLastState);

		ip.setFeedbackCount(0);

		if (settings.isSwingable()) {
			ip.setOffset(slide);
			ip.setSwingPercent(swingPercent);
		} else {
			ip.setSwingPercent(50);
		}

		if (settings.isDynamicable() && (ip.getPattern() != RhythmPattern.MELODY1)) {
			double ghostChanceReducer = (panels.size() > 10) ? 0.8 : 1.0;
			ip.setIsVelocityPattern(panelGenerator
					.nextInt(100) < randomDrumVelocityPatternChance.getInt() * ghostChanceReducer);
		} else {
			ip.setIsVelocityPattern(false);
		}

		if (panels.size() > 10 && ip.getPattern() == RhythmPattern.FULL
				&& panelGenerator.nextInt(100) < 30) {
			ip.setPattern(RhythmPattern.ALT);
		}

		if (settings.isVariableShift()
				&& panelGenerator.nextInt(100) < randomDrumShiftChance.getInt()) {
			// settings set the maximum shift, this sets 0 - max randomly
			ip.setPatternShift(panelGenerator.nextInt(ip.getPatternShift() + 1));
		}

		ip.applyPauseChance(panelGenerator);
		ip.growPattern(panelGenerator, 1, 5);

		//if (dp.getPatternShift() > 0) {
		ip.getComboPanel().reapplyShift();
		//}

		ip.getComboPanel().reapplyHits();
	}
	private void setupOverrandomizedDrum(Random drumPanelGenerator, int slide, int swingPercent,
			List<Integer> pitches, int panelIndex, DrumPanel ip) {
		ip.setInstrument(pitches.get(panelIndex));
		//dp.setPitch(32 + drumPanelGenerator.nextInt(33));


		ip.setChordSpan(drumPanelGenerator.nextInt(2) + 1);
		RhythmPattern pattern = RhythmPattern.FULL;
		// use pattern in half the cases if checkbox selected

		if (randomDrumPattern.isSelected()) {
			int[] patternWeights = { 35, 60, 80, 90, 90, 100 };
			int randomWeight = drumPanelGenerator.nextInt(100);
			for (int j = 0; j < patternWeights.length; j++) {
				if (randomWeight < patternWeights[j]) {
					pattern = RhythmPattern.VIABLE_PATTERNS.get(j);
					break;
				}
			}
		}

		int hits = 4;
		while (drumPanelGenerator.nextBoolean() && hits < 16) {
			hits *= 2;
		}
		if ((hits / ip.getChordSpan() >= 8)) {
			hits /= 2;
		}

		switch (randomDrumHitsMultiplierOnGenerate.getSelectedIndex()) {
		case 0:
			break;
		case 1:
			hits /= 2;
			break;
		case 2:
			hits = hits * 3 / 4;
			break;
		case 3:
			hits = hits * 3 / 2;
			break;
		case 4:
			hits *= 2;
			break;
		default:
			throw new IllegalArgumentException("Multiplier index too high.");
		}
		ip.setHitsPerPattern(hits * 2);

		int adjustVelocity = -1 * ip.getHitsPerPattern() / ip.getChordSpan();

		ip.setFeedbackCount(drumPanelGenerator.nextBoolean() ? drumPanelGenerator.nextInt(3) : 0);

		ip.setPattern(pattern);
		int velocityMin = drumPanelGenerator.nextInt(30) + 50 + adjustVelocity;

		ip.setVelocityMax(1 + velocityMin + drumPanelGenerator.nextInt(25));
		ip.setVelocityMin(velocityMin);

		if (pattern != RhythmPattern.FULL) {
			ip.setPauseChance(drumPanelGenerator.nextInt(5) + 0);
		} else {
			ip.setPauseChance(drumPanelGenerator.nextInt(40) + 40);
		}

		// punchy drums - kicks, snares
		if (PUNCHY_DRUMS.contains(ip.getInstrument())) {
			adjustVelocity += 15;
			ip.setExceptionChance(drumPanelGenerator.nextInt(3));
		} else {
			ip.setOffset(slide);
			ip.setSwingPercent(swingPercent);
			ip.setExceptionChance(drumPanelGenerator.nextInt(10));
			if (drumPanelGenerator.nextInt(100) < 30) {
				ip.setPattern(RhythmPattern.MELODY1);
			}
		}

		if (randomDrumUseChordFill.isSelected()) {
			ip.setChordSpanFill(ChordSpanFill.getWeighted(drumPanelGenerator.nextInt(100)));
		}
		ip.setFillFlip(false);
		ip.setPatternFlip(false);

		ip.setIsVelocityPattern(drumPanelGenerator.nextInt(100) < Integer
				.valueOf(randomDrumVelocityPatternChance.getInt()));

		if (drumPanelGenerator.nextInt(100) < randomDrumShiftChance.getInt()
				&& pattern != RhythmPattern.FULL) {
			ip.setPatternShift(drumPanelGenerator.nextInt(ip.getPattern().pattern.length - 1) + 1);
			ip.getComboPanel().reapplyShift();
		}

		ip.getComboPanel().reapplyHits();
	}
	/** Cleanup method called when this module is no longer needed. */
	public void cleanup() {
		// No persistent resources to release.
	}
}
