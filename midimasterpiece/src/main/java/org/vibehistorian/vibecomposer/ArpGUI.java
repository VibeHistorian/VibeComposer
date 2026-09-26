/* --------------------
* @author Vibe Historian
* ---------------------

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
any later version.

This program is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not,
see <https://www.gnu.org/licenses/>.
*/

package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.DynamicGridLayout;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Components.VeloRect;
import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Panels.ArpPanel;
import org.vibehistorian.vibecomposer.Panels.DetachedKnobPanel;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;
import org.vibehistorian.vibecomposer.Panels.MelodyPanel;
import org.vibehistorian.vibecomposer.Panels.PartManagerPanel;
import org.vibehistorian.vibecomposer.Panels.SettingsPanel;
import org.vibehistorian.vibecomposer.Parts.ArpPart;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/** Builds and owns arpeggio controls and their UI state. */
public class ArpGUI implements InstrumentGUIControls {

	private final List<ArpPanel> arpPanels = new ArrayList<>();
	private JScrollPane arpScrollPane;
	public static JPanel arpParentPanel;
	public static SettingsPanel arpSettingsPanel;
	private JCheckBox enabledCheckBox;
	private VeloRect groupFilterSlider;
	private JButton addPanelButton;
	private JButton generatePanelButton;
	private JTextField randomPanelsToGenerate;

	public static JCheckBox randomArpsGenerateOnCompose;
	public static JCheckBox randomArpTranspose;
	public static JCheckBox randomArpPattern;
	public static JCheckBox randomArpHitsPerPattern;
	public static JCheckBox randomArpAllSameInst;
	public static JCheckBox randomArpAllSameHits;
	public static JCheckBox randomArpLimitPowerOfTwo;
	public static KnobPanel randomArpShiftChance;
	public static ScrollComboBox<Integer> randomArpHitsPicker;
	public static JCheckBox randomArpUseChordFill;
	public static ScrollComboBox<String> randomArpStretchType;
	public static ScrollComboBox<Integer> randomArpStretchPicker;
	public static KnobPanel randomArpStretchGenerationChance;
	public static KnobPanel randomArpMaxExceptionChance;
	public static JCheckBox randomArpUseOctaveAdjustments;
	public static KnobPanel randomArpMaxRepeat;
	public static KnobPanel randomArpMinVel;
	public static KnobPanel randomArpMaxVel;
	public static KnobPanel randomArpMinLength;
	public static KnobPanel randomArpMaxLength;
	public static JCheckBox randomArpCorrectMelodyNotes;
	public static JCheckBox arpCopyMelodyInst;
	public static JCheckBox arpAffectsBpm;

	private final Context context;

	public ArpGUI(Context context) {
		this.context = context;
	}

	@Override public JCheckBox getEnabledCheckBox() { return enabledCheckBox; }
	@Override public VeloRect getGroupFilterSlider() { return groupFilterSlider; }
	@Override public JButton getAddPanelButton() { return addPanelButton; }
	@Override public JButton getGeneratePanelButton() { return generatePanelButton; }
	@Override public JTextField getRandomPanelsToGenerate() { return randomPanelsToGenerate; }
	@Override public JScrollPane getPanelScrollPane() { return arpScrollPane; }
	@Override public List<ArpPanel> getPanels() { return arpPanels; }

	public void saveToConfig(GUIConfig gc, int seed) {
		gc.setArpsEnable(enabledCheckBox.isSelected());
		List<ArpPart> parts = new ArrayList<>();
		for (ArpPanel panel : arpPanels) parts.add((ArpPart) panel.toInstPart(seed));
		org.vibehistorian.vibecomposer.Parts.InstPart.sortParts(parts);
		gc.setArpParts(parts);
		gc.setArpAffectsBpm(arpAffectsBpm.isSelected());
		gc.setUseOctaveAdjustments(randomArpUseOctaveAdjustments.isSelected());
		gc.setRandomArpCorrectMelodyNotes(randomArpCorrectMelodyNotes.isSelected());
	}

	public void loadFromConfig(GUIConfig gc) {
		enabledCheckBox.setSelected(gc.isArpsEnable());
		arpAffectsBpm.setSelected(gc.isArpAffectsBpm());
		randomArpUseOctaveAdjustments.setSelected(gc.isUseOctaveAdjustments());
		randomArpCorrectMelodyNotes.setSelected(gc.isRandomArpCorrectMelodyNotes());
	}

	public void loadPartsFromConfig(GUIConfig gc, Consumer<List<ArpPart>> restorePanels) {
		restorePanels.accept(gc.getArpParts());
	}

	/** Supplies shared window operations without coupling this module to the main window. */
	public interface Context {
		PartManagerPanel.Context getPartManagerContext();
		void addPanel();
		void generatePanels(boolean triggerRegenerate);
		ArpPanel addArpPanel();
		List<InstPanel> getAffectedPanels(int instrument);
		List<? extends InstPanel> getInstList(int instrument);
	}

	public void initArpGenSettings() {
		JPanel scrollableArpPanels = new JPanel();
		scrollableArpPanels.setLayout(new BoxLayout(scrollableArpPanels, BoxLayout.Y_AXIS));
		scrollableArpPanels.setAutoscrolls(true);

		arpScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				Dimension size = UITheme.scrollPaneDimension;
				return new Dimension(size.width, size.height - 100);
			}
		};
		arpScrollPane.setViewportView(scrollableArpPanels);
		arpScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		arpScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		JPanel arpsSettingsPanel = new JPanel();
		arpsSettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		enabledCheckBox = new CustomCheckBox("ARPS", true);
		arpsSettingsPanel.add(enabledCheckBox);
		groupFilterSlider = VeloRect.midi(127);
		JLabel filterLabel = new JLabel("LP");
		arpsSettingsPanel.add(filterLabel);
		arpsSettingsPanel.add(groupFilterSlider);

		addPanelButton = SwingUtils.makeButton("+Arp", e -> context.addPanel());
		generatePanelButton = SwingUtils.makeButton("Generate Arps:", e -> context.generatePanels(true));
		randomPanelsToGenerate = new JTextField("3", 2);
		arpsSettingsPanel.add(addPanelButton);
		arpsSettingsPanel.add(generatePanelButton);
		arpsSettingsPanel.add(randomPanelsToGenerate);

		randomArpsGenerateOnCompose = SwingUtils.makeCheckBox("on Compose", true, true);
		arpsSettingsPanel.add(randomArpsGenerateOnCompose);

		randomArpTranspose = new CustomCheckBox("Transpose", true);
		randomArpPattern = new CustomCheckBox("Patterns", true);
		randomArpHitsPicker = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new Integer[] { 1, 2, 3, 4, 5, 6, 7, 8 }, randomArpHitsPicker);
		randomArpHitsPicker.setVal(4);
		randomArpHitsPerPattern = new CustomCheckBox("Random#", true);
		randomArpAllSameInst = new CustomCheckBox("One Inst.", false);
		randomArpAllSameHits = new CustomCheckBox("One #", true);
		randomArpLimitPowerOfTwo = new CustomCheckBox("<html>Limit 2<sup>n</sup>", true);
		randomArpUseChordFill = new CustomCheckBox("Fills", true);
		randomArpShiftChance = new DetachedKnobPanel("Shift%", 50);
		randomArpUseOctaveAdjustments = new CustomCheckBox("Rand. Oct.", false);
		randomArpMaxRepeat = new DetachedKnobPanel("Max<br>Repeat", 2, 1, 4);
		randomArpMinVel = new DetachedKnobPanel("Min<br>Vel", 65, 0, 126);
		randomArpMaxVel = new DetachedKnobPanel("Max<br>Vel", 90, 1, 127);
		randomArpMinLength = new DetachedKnobPanel("Min<br>Length", 75, 25, 200);
		randomArpMaxLength = new DetachedKnobPanel("Max<br>Length", 100, 25, 200);
		randomArpCorrectMelodyNotes = new CustomCheckBox(
				"<html>Correct Notes<br>by Melody</html>", false);

		arpsSettingsPanel.add(new JLabel("Arp#"));
		arpsSettingsPanel.add(randomArpHitsPicker);
		arpsSettingsPanel.add(randomArpHitsPerPattern);
		arpsSettingsPanel.add(randomArpAllSameHits);
		arpsSettingsPanel.add(randomArpUseChordFill);
		arpsSettingsPanel.add(randomArpTranspose);

		randomArpStretchType = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new String[] { "NONE", "FIXED", "AT_MOST" }, randomArpStretchType);
		randomArpStretchType.setVal("AT_MOST");
		JLabel stretchLabel = new JLabel("VOICES");
		arpsSettingsPanel.add(stretchLabel);
		arpsSettingsPanel.add(randomArpStretchType);
		randomArpStretchPicker = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new Integer[] { 3, 4, 5, 6 }, randomArpStretchPicker);
		randomArpStretchPicker.setVal(4);
		arpsSettingsPanel.add(randomArpStretchPicker);
		randomArpStretchGenerationChance = new DetachedKnobPanel("Chance", 50);
		arpsSettingsPanel.add(randomArpStretchGenerationChance);
		randomArpMaxExceptionChance = new DetachedKnobPanel("Max.<br>Split%", 20);
		arpsSettingsPanel.add(randomArpMaxExceptionChance);

		UITheme.toggleableComponents.add(randomArpStretchType);
		UITheme.toggleableComponents.add(randomArpStretchPicker);

		JButton clearArpPatternSeeds = SwingUtils.makeButton("Clear Seeds",
				e -> context.getAffectedPanels(3).forEach(panel -> panel.setPatternSeed(0)));
		JPanel arpSettingsExtraPanel = new JPanel();
		JLabel csExtra = new JLabel("ARP SETTINGS+");
		csExtra.setPreferredSize(new Dimension(120, 30));
		csExtra.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		arpSettingsExtraPanel.add(csExtra);

		arpCopyMelodyInst = new CustomCheckBox("Arp#1 Copy Melody Inst.", true);
		arpSettingsExtraPanel.add(arpCopyMelodyInst);
		arpSettingsExtraPanel.add(randomArpAllSameInst);
		arpSettingsExtraPanel.add(randomArpLimitPowerOfTwo);
		arpSettingsExtraPanel.add(randomArpUseOctaveAdjustments);
		arpSettingsExtraPanel.add(randomArpMaxRepeat);
		arpSettingsExtraPanel.add(randomArpMinVel);
		arpSettingsExtraPanel.add(randomArpMaxVel);
		arpSettingsExtraPanel.add(randomArpPattern);
		arpSettingsExtraPanel.add(randomArpShiftChance);
		arpSettingsExtraPanel.add(randomArpMinLength);
		arpSettingsExtraPanel.add(randomArpMaxLength);
		arpSettingsExtraPanel.add(randomArpCorrectMelodyNotes);
		arpSettingsExtraPanel.add(clearArpPatternSeeds);
		arpSettingsExtraPanel.add(new PartManagerPanel(3, context.getPartManagerContext()));
		UITheme.toggleableComponents.add(arpSettingsExtraPanel);

		arpsSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		arpsSettingsPanel.setMaximumSize(new Dimension(1800, 50));
		arpSettingsExtraPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		arpSettingsExtraPanel.setMaximumSize(new Dimension(1800, 50));

		arpParentPanel = new JPanel() {
			@Override
			public Dimension getPreferredSize() {
				return UITheme.scrollPaneDimension;
			}
		};
		arpParentPanel.setLayout(new BoxLayout(arpParentPanel, BoxLayout.Y_AXIS));

		JPanel borderPanel = new JPanel() {
			@Override
			public Dimension getMaximumSize() {
				return new Dimension(UITheme.scrollPaneDimension.width, 100);
			}
		};
		borderPanel.setLayout(new DynamicGridLayout(0, 1));
		borderPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
		borderPanel.add(arpsSettingsPanel);
		borderPanel.add(arpSettingsExtraPanel);
		arpParentPanel.add(borderPanel);
		arpParentPanel.add(arpScrollPane);
	}

	public JPanel initArps() {
		return arpParentPanel;
	}

	public void createRandomArpPanels(int panelCount, boolean onlyAdd, ArpPanel randomizedPanel) {
		ScrollComboBox.discardInteractions();
		List<ArpPanel> affectedArps = (List<ArpPanel>) (List<?>) context.getAffectedPanels(3);

		Random panelGenerator = new Random();
		List<ArpPanel> removedPanels = new ArrayList<>();
		List<ArpPanel> remainingPanels = new ArrayList<>();
		for (Iterator<ArpPanel> panelI = affectedArps.iterator(); panelI.hasNext();) {
			ArpPanel panel = panelI.next();
			if (!onlyAdd && !panel.getLockInst()) {
				if (removedPanels.size() >= panelCount) {
					((JPanel) arpScrollPane.getViewport().getView()).remove(panel);
					panelI.remove();
				} else {
					removedPanels.add(panel);
				}
			} else {
				remainingPanels.add(panel);
			}
		}
		Collections.sort(removedPanels, Comparator.comparing(InstPanel::getPanelOrder));
		panelCount -= remainingPanels.size();

		int fixedHitsGenerated = -1;
		if (randomArpHitsPerPattern.isSelected() && randomArpAllSameHits.isSelected()) {
			Random instGen = new Random();
			if (randomArpLimitPowerOfTwo.isSelected()) {
				fixedHitsGenerated = OMNI.getRandomFromArray(instGen, new int[] { 2, 4, 4, 8, 8, 8, 8 }, 0);
			} else {
				fixedHitsGenerated = instGen.nextInt(MidiGenerator.MAXIMUM_PATTERN_LENGTH - 1) + 2;
				if (fixedHitsGenerated == 5) {
					fixedHitsGenerated = instGen.nextInt(MidiGenerator.MAXIMUM_PATTERN_LENGTH - 1) + 2;
				}
				if (fixedHitsGenerated == 7) {
					fixedHitsGenerated++;
				}
			}
			randomArpHitsPicker.setVal(fixedHitsGenerated);
		}

		int fixedInstrument = -1;
		int fixedHits = -1;
		List<? extends InstPanel> melodyPanels = context.getInstList(0);
		MelodyPanel firstMelodyPanel = melodyPanels.isEmpty() ? null : (MelodyPanel) melodyPanels.get(0);
		if (arpCopyMelodyInst.isSelected() && firstMelodyPanel != null
				&& !firstMelodyPanel.getMuteInst()) {
			fixedInstrument = firstMelodyPanel.getInstrument();
			if (!affectedArps.isEmpty()) {
				affectedArps.get(0).setInstrument(fixedInstrument);
			}
		}

		int fixedArpStretch = -1;
		if (randomArpStretchType.getVal().equals("FIXED")) {
			fixedArpStretch = randomArpStretchPicker.getVal();
		}

		int start = 0;
		if (randomizedPanel != null) {
			start = randomizedPanel.getPanelOrder() - 1;
			panelCount = start + 1;
		}

		ArpPanel first = (affectedArps.isEmpty() || !affectedArps.get(0).getLockInst()
				|| (randomizedPanel != null && start == 0)) ? null : affectedArps.get(0);
		List<RhythmPattern> viablePatterns = new ArrayList<>(RhythmPattern.VIABLE_PATTERNS);

		for (int panelIndex = start; panelIndex < panelCount; panelIndex++) {
			if (randomArpAllSameInst.isSelected() && first != null && fixedInstrument < 0) {
				fixedInstrument = first.getInstrument();
			}
			if (randomArpAllSameHits.isSelected() && first != null && fixedHits < 0) {
				fixedHits = first.getHitsPerPattern() / first.getChordSpan();
			}
			ArpPanel ip;
			boolean needNewChannel = false;
			if (randomizedPanel != null) {
				ip = randomizedPanel;
			} else if (panelIndex < removedPanels.size()) {
				ip = removedPanels.get(panelIndex);
			} else {
				needNewChannel = true;
				ip = context.addArpPanel();
			}

			if (randomArpHitsPerPattern.isSelected()) {
				if (fixedHits > 0) {
					ip.setHitsPerPattern(fixedHits);
				} else if (fixedHitsGenerated > 0) {
					ip.setHitsPerPattern(fixedHitsGenerated);
				} else {
					Random instGen = new Random();
					int value;
					if (randomArpLimitPowerOfTwo.isSelected()) {
						value = OMNI.getRandomFromArray(instGen, new int[] { 2, 4, 4, 8, 8, 8, 8 }, 0);
					} else {
						value = instGen.nextInt(MidiGenerator.MAXIMUM_PATTERN_LENGTH - 1) + 2;
						if (value == 5) {
							value = instGen.nextInt(MidiGenerator.MAXIMUM_PATTERN_LENGTH - 1) + 2;
						}
						if (value == 7) {
							value++;
						}
					}
					ip.setHitsPerPattern(value);
				}
			} else {
				ip.setHitsPerPattern(randomArpHitsPicker.getSelectedIndex() + 1);
			}

			if (GenerationGUI.randomizeInstOnComposeOrGen.isSelected() || onlyAdd) {
				int instrument = ip.getInstrumentBox().getRandomInstrument();
				if (randomArpAllSameInst.isSelected()) {
					if (fixedInstrument >= 0) {
						instrument = fixedInstrument;
					} else {
						fixedInstrument = instrument;
					}
				}
				if (ip.getInstrumentBox().isEnabled()) {
					ip.setInstrument(instrument);
				}
			}

			ip.setChordSpan(panelGenerator.nextInt(2) + 1);
			if (ExtraSettingsGUI.orderedTransposeGeneration.isSelected()) {
				ip.setTranspose((((ip.getPanelOrder() + 1) % 3) - 1) * 12);
			} else if (first == null && panelIndex == 0 && !onlyAdd) {
				ip.setTranspose(12);
			} else {
				ip.setTranspose((panelGenerator.nextInt(3) - 1) * 12);
			}

			if (first == null && panelIndex == 0 && !onlyAdd && arpCopyMelodyInst.isSelected()
					&& firstMelodyPanel != null && !firstMelodyPanel.getMuteInst()) {
				ip.setInstrument(fixedInstrument);
			}

			if (ip.getChordSpan() == 1) {
				ip.setPatternRepeat(panelGenerator.nextInt(randomArpMaxRepeat.getInt()) + 1);
			} else {
				ip.setPatternRepeat(1);
				if (panelGenerator.nextBoolean() && ((first == null && panelIndex > 1) || first != null)) {
					ip.setHitsPerPattern(ip.getHitsPerPattern() * ip.getChordSpan());
				}
			}

			boolean fastArp = (ip.getPatternRepeat() * ip.getHitsPerPattern()
					/ (double) ip.getChordSpan()) >= 16;
			if (fastArp) {
				ip.setExceptionChance(panelGenerator.nextInt(1 + (randomArpMaxExceptionChance.getInt() / 3)));
			} else {
				ip.setExceptionChance(panelGenerator.nextInt(1 + randomArpMaxExceptionChance.getInt()));
			}

			if (!randomArpStretchType.getVal().equals("NONE")
					&& panelGenerator.nextInt(100) < randomArpStretchGenerationChance.getInt()) {
				ip.setStretchEnabled(true);
				if (fixedArpStretch < 0) {
					int atMost = randomArpStretchPicker.getVal();
					ip.setChordNotesStretch(panelGenerator.nextInt(atMost - 3 + 1) + 3);
				} else {
					ip.setChordNotesStretch(fixedArpStretch);
				}
			} else {
				ip.setStretchEnabled(false);
			}

			RhythmPattern pattern = RhythmPattern.FULL;
			int panelTotal = arpPanels.size();
			int patternChanceIncrease = (ip.getPanelOrder() < 4 || panelTotal < 3) ? 0 : panelTotal * 5;
			int fillChanceIncrease = (ip.getPanelOrder() < 4 || panelTotal < 3) ? 0 : (panelTotal - 3) * 5;
			if (panelGenerator.nextInt(100) < (30 + patternChanceIncrease) && randomArpPattern.isSelected()) {
				pattern = viablePatterns.get(panelGenerator.nextInt(viablePatterns.size()));
			}
			ip.setPattern(pattern);
			if (randomArpUseChordFill.isSelected()) {
				int fillWeight = OMNI.clampChance(
						panelGenerator.nextInt(100 - fillChanceIncrease) + fillChanceIncrease);
				ip.setChordSpanFill(ChordSpanFill.getWeighted(fillWeight));
			} else {
				ip.setChordSpanFill(ChordSpanFill.ALL);
			}
			ip.setFillFlip(false);
			ip.setPatternFlip(false);

			ip.setVelocityMax(randomArpMaxVel.getInt());
			ip.setVelocityMin(randomArpMinVel.getInt());
			int pauseMax = (int) (50 * ip.getPattern().getNoteFrequency());
			ip.setPauseChance(panelGenerator.nextInt(pauseMax + 1));
			ip.applyPauseChance(panelGenerator);

			if (panelGenerator.nextInt(100) < randomArpShiftChance.getInt()) {
				int maxShift = Math.min(ip.getPattern().maxShift, ip.getHitsPerPattern() - 1);
				if (GenerationGUI.beatDurationMultiplier != null
						&& GenerationGUI.beatDurationMultiplier.getVal() < 0.75) {
					maxShift /= 2;
				}
				ip.setPatternShift(maxShift > 0 ? (panelGenerator.nextInt(maxShift) + 1) : 0);
			} else {
				ip.setPatternShift(0);
			}

			ip.growPattern(panelGenerator, 1, 5);
			int lengthRange = Math.max(1, 1 + randomArpMaxLength.getInt() - randomArpMinLength.getInt());
			ip.setNoteLengthMultiplier(panelGenerator.nextInt(lengthRange) + randomArpMinLength.getInt());

			if (panelGenerator.nextBoolean()) {
				int arpPatternOrder = 0;
				int[] patternWeights = { 60, 68, 75, 83, 91, 97, 100 };
				int randomWeight = panelGenerator.nextInt(100);
				for (int j = 0; j < patternWeights.length; j++) {
					if (randomWeight < patternWeights[j]) {
						arpPatternOrder = j;
						break;
					}
				}
				ip.setArpPattern(ArpPattern.values()[arpPatternOrder]);
				if (arpPatternOrder > 0 && panelGenerator.nextBoolean()) {
					ip.setArpPatternRotate(panelGenerator.nextInt(Math.min(4, ip.getChordNotesStretch())));
				}
			} else {
				ip.setArpPattern(ArpPattern.RANDOM);
			}

			if (needNewChannel) {
				ip.setNextFreeMidiChannel();
				ip.setPanByOrder(7);
			}
			ip.getComboPanel().reapplyShift();
			ip.getComboPanel().reapplyHits();
		}
	}

	/** Cleanup method called when this module is no longer needed. */
	public void cleanup() {
		// No persistent resources to release.
	}
}
