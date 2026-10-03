/* --------------------
* @author Vibe Historian
* ---------------------

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or any
later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not,
see <https://www.gnu.org/licenses/>.
*/

package org.vibehistorian.vibecomposer.gui;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.Components.CheckButton;
import org.vibehistorian.vibecomposer.Components.Chordlet;
import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.DynamicGridLayout;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.*;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.PatternJoinMode;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Enums.StrumType;
import org.vibehistorian.vibecomposer.Panels.ChordGenSettings;
import org.vibehistorian.vibecomposer.Panels.ChordPanel;
import org.vibehistorian.vibecomposer.Panels.ChordletPanel;
import org.vibehistorian.vibecomposer.Panels.DetachedKnobPanel;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;
import org.vibehistorian.vibecomposer.Panels.PartManagerPanel;
import org.vibehistorian.vibecomposer.Panels.SoloMuter;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Popups.ChordTransformPopup;
import org.vibehistorian.vibecomposer.Popups.TemporaryInfoPopup;
import org.vibehistorian.vibecomposer.generators.MidiGenerator;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static org.vibehistorian.vibecomposer.InstUtils.POOL;

/** Builds and owns chord controls and their UI state. */
public class ChordGUI extends InstGUI<ChordPanel> {

    public JPanel chordSettingsPanel;
    public JLabel currentChords = new JLabel("Chords:[]");
    public List<String> currentChordsInternal = new ArrayList<>();
    public JLabel tipLabel;

    public ScrollComboBox<String> chordProgressionLength;
    public JCheckBox allowChordRepeats;
    public JCheckBox randomChordsGenerateOnCompose;
    public JCheckBox randomChordDelay;
    public JCheckBox randomChordStrum;
    public KnobPanel randomChordStruminess;
    public JCheckBox randomChordSplit;
    public JCheckBox randomChordTranspose;
    public JCheckBox randomChordPattern;
    public JCheckBox randomChordVaryLength;
    public KnobPanel randomChordExpandChance;
    public KnobPanel randomChordSustainChance;
    public KnobPanel randomChordShiftChance;
    public KnobPanel randomChordVoicingChance;
    public KnobPanel randomChordMaxSplitChance;
    public JCheckBox randomChordUseChordFill;
    public ScrollComboBox<String> randomChordStretchType;
    public ScrollComboBox<Integer> randomChordStretchPicker;
    public KnobPanel randomChordStretchGenerationChance;
    public KnobPanel randomChordMaxStrumPauseChance;
    public KnobPanel randomChordMinVel;
    public KnobPanel randomChordMaxVel;

    public KnobPanel spiceChance;
    public KnobPanel chordSlashChance;
    public JCheckBox spiceAllowDimAug;
    public JCheckBox spiceAllow9th13th;
    public JCheckBox spiceFlattenBigChords;
    public JCheckBox squishChordsProgressively;
    public JCheckBox copyChordsAfterGenerate;
    public KnobPanel spiceParallelChance;
    public JCheckBox spiceForceScale;
    public ScrollComboBox<String> firstChordSelection;
    public ScrollComboBox<String> lastChordSelection;
    public JCheckBox useChordFormula;
    public KnobPanel longProgressionSimilarity;
    public CheckButton userChordsEnabled;
    public CheckButton userDurationsEnabled;
    public JTextField userChordsDurations;
    public ChordletPanel userChords;

    private final Context context;

    public ChordGUI(Context context, InstrumentPanelController panelController) {
        super(INST.CHORD, panelController);
        this.context = context;
    }

    @Override public ChordPanel createPanel(SoloMuter.Context soloMuterContext) {
        return new ChordPanel(soloMuterContext);
    }

    @Override public void createRandomPanels(int panelCount, boolean onlyAdd,
            Integer seed, InstPanel randomizedPanel) {
        createRandomChordPanels(panelCount, onlyAdd, (ChordPanel) randomizedPanel);
    }

    public void saveToConfig(GUIConfig gc, int seed, boolean preserveGeneratedChords) {
        gc.setChordsEnable(enabledCheckBox.isSelected());
        gc.setChordParts(createParts(seed, ChordPart.class));
        gc.setChordGenSettings(getChordSettingsFromUI());
        gc.setAllowChordRepeats(allowChordRepeats.isSelected());
        gc.setFixedDuration(chordProgressionLength.getSelectedIndex() < 2
                ? Integer.parseInt(chordProgressionLength.getVal()) : 0);
        gc.setUseChordFormula(useChordFormula.isSelected());
        gc.setLongProgressionSimilarity(longProgressionSimilarity.getInt());
        gc.setFirstChord(firstChordSelection.getVal());
        gc.setLastChord(lastChordSelection.getVal());
        gc.setCustomChordsEnabled(userChordsEnabled.isSelected());
        gc.setCustomChords(preserveGeneratedChords
                ? StringUtils.join(MidiGenerator.chordInts, ",")
                : userChords.getChordListString());
        gc.setCustomChordDurations(userChordsDurations.getText());
        gc.setCustomDurationsEnabled(userDurationsEnabled.isSelected());
        gc.setSpiceChance(spiceChance.getInt());
        gc.setSpiceParallelChance(spiceParallelChance.getInt());
        gc.setDimAug6thEnabled(spiceAllowDimAug.isSelected());
        gc.setEnable9th13th(spiceAllow9th13th.isSelected());
        gc.setSpiceFlattenBigChords(spiceFlattenBigChords.isSelected());
        gc.setSquishProgressively(squishChordsProgressively.isSelected());
        gc.setChordSlashChance(chordSlashChance.getInt());
        gc.setSpiceForceScale(spiceForceScale.isSelected());
    }

    public void loadFromConfig(GUIConfig gc) {
        enabledCheckBox.setSelected(gc.isChordsEnable());
        allowChordRepeats.setSelected(gc.isAllowChordRepeats());
        setProgressionLength(gc.getFixedDuration());
        spiceChance.setInt(gc.getSpiceChance());
        spiceParallelChance.setInt(gc.getSpiceParallelChance());
        spiceAllowDimAug.setSelected(gc.isDimAug6thEnabled());
        spiceAllow9th13th.setSelected(gc.isEnable9th13th());
        spiceFlattenBigChords.setSelected(gc.isSpiceFlattenBigChords());
        squishChordsProgressively.setSelected(gc.isSquishProgressively());
        chordSlashChance.setInt(gc.getChordSlashChance());
        spiceForceScale.setSelected(gc.isSpiceForceScale());
        useChordFormula.setSelected(gc.isUseChordFormula());
        longProgressionSimilarity.setInt(gc.getLongProgressionSimilarity());
        firstChordSelection.setVal(gc.getFirstChord());
        lastChordSelection.setVal(gc.getLastChord());
        userChordsEnabled.setSelected(gc.isCustomChordsEnabled());
        userChords.setupChords(gc.getCustomChords());
        userChordsDurations.setText(gc.getCustomChordDurations());
        userDurationsEnabled.setSelected(gc.isCustomDurationsEnabled());
        setChordSettingsInUI(gc.getChordGenSettings());
    }

    public void applyGeneratedChords(List<String> chords, boolean userMelodyAttached, GUIConfig config) {
        currentChords.setText(StringUtils.abbreviate("Chords:[" + StringUtils.join(chords, ",") + "]", 60));
        currentChordsInternal.clear();
        currentChordsInternal.addAll(chords);

        if (userMelodyAttached) {
            userChords.setupChords(chords);
            setProgressionLength(chords.size());
            config.setCustomChords(StringUtils.join(chords, ","));
        } else if (!userChordsEnabled.isSelected() && copyChordsAfterGenerate.isSelected()) {
            userChords.setupChords(chords);
        }
    }

    public void copyChords() {
        userChords.setupChords(currentChordsInternal);
        LG.i("Copied chords: " + userChords.getChordListString());
    }

    public void setProgressionLength(int size) {
        switch (size) {
        case 4:
            chordProgressionLength.setVal("4");
            break;
        case 8:
            chordProgressionLength.setVal("8");
            break;
        default:
            chordProgressionLength.setVal("RANDOM");
            break;
        }
    }

    public void loadPartsFromConfig(GUIConfig gc, Consumer<List<ChordPart>> restorePanels) {
        restorePanels.accept(gc.getChordParts());
    }

    private ChordGenSettings getChordSettingsFromUI() {
        ChordGenSettings settings = new ChordGenSettings();
        settings.setIncludePresets(randomChordPattern.isSelected());
        settings.setUseDelay(randomChordDelay.isSelected());
        settings.setUseStrum(randomChordStrum.isSelected());
        settings.setUseSplit(randomChordSplit.isSelected());
        settings.setUseTranspose(randomChordTranspose.isSelected());
        settings.setShiftChance(randomChordShiftChance.getInt());
        settings.setSustainChance(randomChordSustainChance.getInt());
        settings.setFlattenVoicingChance(randomChordVoicingChance.getInt());
        return settings;
    }

    private void setChordSettingsInUI(ChordGenSettings settings) {
        randomChordPattern.setSelected(settings.isIncludePresets());
        randomChordDelay.setSelected(settings.isUseDelay());
        randomChordStrum.setSelected(settings.isUseStrum());
        randomChordSplit.setSelected(settings.isUseSplit());
        randomChordTranspose.setSelected(settings.isUseTranspose());
        randomChordShiftChance.setInt(settings.getShiftChance());
        randomChordSustainChance.setInt(settings.getSustainChance());
        randomChordVoicingChance.setInt(settings.getFlattenVoicingChance());
    }

    /** Supplies shared window operations without coupling this module to the main window. */
    public interface Context {
        PartManagerPanel.Context getPartManagerContext();
        boolean isRandomizeInstOnComposeOrGen();
        boolean isBeatDurationMultiplierBelowOne();
        MidiUtils.ScaleMode getScaleMode();
        GUIConfig getGUIConfig();
        void copyGUItoConfig();
        void adjustScoreTranspose(int amount);
		void alignChordsWithMelody(ChordletPanel chordlets);
    }

	public void initExtraSettingsChords(JPanel chordChoicePanel) {
		// CHORDS
		spiceFlattenBigChords = new CustomCheckBox("Spicy Voicing", false);
		useChordFormula = new CustomCheckBox("Chord Formula", true);
		randomChordVoicingChance = new KnobPanel("Flatten<br>Voicing%", 100);
		squishChordsProgressively = new CustomCheckBox("<html>Flatten<br>Progressively</html>",
				false);
		longProgressionSimilarity = new DetachedKnobPanel("8 Chords <br>Similarity%", 50, 0, 100);

		chordChoicePanel.add(useChordFormula);
		chordChoicePanel.add(longProgressionSimilarity);
		chordChoicePanel.add(randomChordVoicingChance);
		chordChoicePanel.add(spiceFlattenBigChords);
		chordChoicePanel.add(squishChordsProgressively);
	}

	public JCheckBox createExtraComposeControl() {
		copyChordsAfterGenerate = SwingUtils.makeCheckBox(
				"<html>Copy Chords<br>on Compose/Reg.</html>", true, true);
		return copyChordsAfterGenerate;
	}

	public List<JPanel> initMacroControls() {
		chordProgressionLength = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new String[] { "4", "8", "RANDOM" }, chordProgressionLength);
		setProgressionLength(4);
		JPanel chordProgPanel = new JPanel();
		chordProgPanel.add(new JLabel("# of Chords"));
		chordProgPanel.add(chordProgressionLength);
		chordProgPanel.setOpaque(false);

		allowChordRepeats = new CustomCheckBox("Allow Chord Repeats", true);
		JPanel allowRepPanel = new JPanel();
		allowRepPanel.add(allowChordRepeats);
		allowRepPanel.setOpaque(false);

		chordProgPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		allowRepPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		return java.util.Arrays.asList(chordProgPanel, allowRepPanel);
	}

	public int getMaxChordProgressionLength() {
		switch (chordProgressionLength.getSelectedIndex()) {
		case 0:
			return 4;
		case 1:
			return 8;
		default:
			return 16;
		}
	}

	public List<Double> getUserChordDurations() {
		boolean forceDefault = !userDurationsEnabled.isSelected();

		List<Double> durations = new ArrayList<>();
		String[] durationSplit = userChordsDurations.getText().split(",");
		boolean customChords = userChordsEnabled.isSelected()
				&& !userChords.getChordletsRaw().isEmpty();
		boolean coversAllCustomChords = durationSplit.length >= userChords.chordCount();

		try {
			for (int i = 0; i < (customChords && coversAllCustomChords ? userChords.chordCount()
					: durationSplit.length); i++) {
				durations.add(!forceDefault && coversAllCustomChords
						? ExtraSettingsGUI.stretchMidi.getInt() * Double.parseDouble(durationSplit[i]) / 100.0
						: MidiGenerator.Durations.WHOLE_NOTE);
			}
		} catch (Exception e) {
			new TemporaryInfoPopup("Invalid durations!", 3000);
		}

		return durations;
	}

	public Pair<StrumType, Integer> getRandomStrumPair() {
		StrumType type = selectTypeByStrumminess(randomChordStruminess.getInt());
		Integer strum = MidiUtils.getRandom(new Random(), type.CHOICES.toArray(new Integer[] {}));
		return Pair.of(type, strum);
	}

	private static StrumType selectTypeByStrumminess(int strumminess) {
		List<StrumType> types = StrumType.getWeighted(new Random().nextInt(100));
		return types.get(new Random().nextInt(types.size()));
	}

    public void initChordGenSettings() {
		JPanel scrollableChordPanels = new JPanel();
		scrollableChordPanels.setLayout(new BoxLayout(scrollableChordPanels, BoxLayout.Y_AXIS));
		scrollableChordPanels.setAutoscrolls(true);

        panelScrollPane = createPanelScrollPane(scrollableChordPanels);

        chordSettingsPanel = new JPanel();
		chordSettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

		addPanelControls(chordSettingsPanel, "CHORDS", "+Chord", "Generate Chords:", "2");

		randomChordsGenerateOnCompose = SwingUtils.makeCheckBox("On Compose", true, true);
		chordSettingsPanel.add(randomChordsGenerateOnCompose);


		randomChordDelay = new CustomCheckBox("Delay", false);
		randomChordStrum = new CustomCheckBox("", true);
		randomChordStruminess = new DetachedKnobPanel("Struminess", 50);
		randomChordSplit = new CustomCheckBox("Use Split (ms)", false);
		randomChordTranspose = new CustomCheckBox("Transpose", true);
		randomChordSustainChance = new DetachedKnobPanel("Chord%", 50);
		randomChordVaryLength = new CustomCheckBox("Vary Length", true);
		randomChordExpandChance = new DetachedKnobPanel("Expand%", 70);
		randomChordUseChordFill = new CustomCheckBox("Fills", true);
		randomChordMaxSplitChance = new DetachedKnobPanel("Max Tran-<br>sition%", 25);
		chordSlashChance = new KnobPanel("Chord1<br>Slash%", 5);
		randomChordPattern = new CustomCheckBox("Patterns", true);
		randomChordShiftChance = new DetachedKnobPanel("Shift%", 60);
		randomChordMinVel = new DetachedKnobPanel("Min<br>Vel", 65, 0, 126);
		randomChordMaxVel = new DetachedKnobPanel("Max<br>Vel", 90, 1, 127);

		chordSettingsPanel.add(randomChordTranspose);
		chordSettingsPanel.add(randomChordStrum);
		chordSettingsPanel.add(randomChordStruminess);
		chordSettingsPanel.add(randomChordUseChordFill);

		chordSettingsPanel.add(randomChordDelay);
		chordSettingsPanel.add(randomChordSplit);
		//chordSettingsPanel.finishMinimalInit();

		randomChordStretchType = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new String[] { "NONE", "FIXED", "AT_MOST" }, randomChordStretchType);
		randomChordStretchType.setVal("AT_MOST");
		JLabel stretchLabel = new JLabel("VOICES");
		chordSettingsPanel.add(stretchLabel);
		chordSettingsPanel.add(randomChordStretchType);
		randomChordStretchPicker = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new Integer[] { 3, 4, 5, 6 }, randomChordStretchPicker);
		randomChordStretchPicker.setVal(5);
		chordSettingsPanel.add(randomChordStretchPicker);
		randomChordStretchGenerationChance = new DetachedKnobPanel("Chance", 50);
		chordSettingsPanel.add(randomChordStretchGenerationChance);
		randomChordMaxStrumPauseChance = new DetachedKnobPanel("Max. Strum<br>Pause %", 35);
		chordSettingsPanel.add(randomChordMaxStrumPauseChance);

		JButton clearChordPatternSeeds = SwingUtils.makeButton("Clear Seeds",
				e -> panelController.getAffectedPanels(INST.CHORD)
						.forEach(panel -> panel.setPatternSeed(0)));

		JPanel chordSettingsExtraPanel = new JPanel();
		JLabel csExtra = new JLabel("CHORD SETTINGS+");
		csExtra.setPreferredSize(new Dimension(120, 30));
		csExtra.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		chordSettingsExtraPanel.add(csExtra);

		chordSettingsExtraPanel.add(randomChordSustainChance);
		chordSettingsExtraPanel.add(randomChordVaryLength);
		chordSettingsExtraPanel.add(randomChordExpandChance);
		chordSettingsExtraPanel.add(randomChordMaxSplitChance);
		chordSettingsExtraPanel.add(chordSlashChance);
		chordSettingsExtraPanel.add(randomChordMinVel);
		chordSettingsExtraPanel.add(randomChordMaxVel);
		chordSettingsExtraPanel.add(randomChordPattern);
		chordSettingsExtraPanel.add(randomChordShiftChance);
		chordSettingsExtraPanel.add(clearChordPatternSeeds);
        chordSettingsExtraPanel.add(new PartManagerPanel(INST.CHORD, context.getPartManagerContext()));

		UITheme.toggleableComponents.add(randomChordDelay);
		UITheme.toggleableComponents.add(stretchLabel);
		UITheme.toggleableComponents.add(randomChordStretchType);
		UITheme.toggleableComponents.add(randomChordStretchPicker);
		UITheme.toggleableComponents.add(randomChordSplit);

		UITheme.toggleableComponents.add(chordSettingsExtraPanel);


		//constraints.gridy = startY;
		//constraints.anchor = anchorSide;
		chordSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		chordSettingsPanel.setMaximumSize(new Dimension(1800, 50));
		//scrollableChordPanels.add(chordSettingsPanel);
		chordSettingsExtraPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		chordSettingsExtraPanel.setMaximumSize(new Dimension(1800, 50));
		//constraints.gridy = startY + 1;

		//scrollableChordPanels.add(chordSettingsExtraPanel);


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
		borderPanel.add(chordSettingsPanel);
		borderPanel.add(chordSettingsExtraPanel);
		parentPanel.add(borderPanel);
		parentPanel.add(panelScrollPane);

		//addHorizontalSeparatorToPanel(scrollableChordPanels);
	}

    public JPanel initChords() {
		return parentPanel;
	}

    public JPanel initChordProgressionSettings() {
		// CHORD SETTINGS 1 - chord variety
		JPanel chordProgressionSettingsPanel = new JPanel();
		chordProgressionSettingsPanel.setLayout(new GridLayout(2, 0, 0, 0));
		chordProgressionSettingsPanel.setOpaque(false);
		chordProgressionSettingsPanel
				.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		//toggleableComponents.add(chordProgressionSettingsPanel);


		spiceChance = new DetachedKnobPanel("Spice", 35);
		spiceAllowDimAug = new CustomCheckBox("Dim/Aug/6th", false);
		spiceAllow9th13th = new CustomCheckBox("9th/13th", false);
		spiceForceScale = new CustomCheckBox("Force Scale", true);
		spiceParallelChance = new DetachedKnobPanel("Aeolian", 10);

		firstChordSelection = new ScrollComboBox<String>(false);
		firstChordSelection.addItem("?");
		ScrollComboBox.addAll(MidiUtils.MAJOR_CHORDS.toArray(new String[] {}), firstChordSelection);
		firstChordSelection.setVal("?");

		lastChordSelection = new ScrollComboBox<String>(false);
		lastChordSelection.addItem("?");
		ScrollComboBox.addAll(MidiUtils.MAJOR_CHORDS.toArray(new String[] {}), lastChordSelection);

		JPanel spiceChancePanel = new JPanel();
		spiceChancePanel.add(spiceChance);
		spiceChancePanel.setOpaque(false);

		JPanel spiceAllowDimAugPanel = new JPanel();
		spiceAllowDimAugPanel.add(spiceAllowDimAug);
		spiceAllowDimAugPanel.setOpaque(false);

		JPanel spiceAllow9th13thPanel = new JPanel();
		spiceAllow9th13thPanel.add(spiceAllow9th13th);
		spiceAllow9th13thPanel.setOpaque(false);


		JPanel spiceForceScalePanel = new JPanel();
		spiceForceScalePanel.add(spiceForceScale);
		spiceForceScalePanel.setOpaque(false);

		JPanel firstChordsPanel = new JPanel();
		firstChordsPanel.setOpaque(false);
		JPanel lastChordsPanel = new JPanel();
		lastChordsPanel.setOpaque(false);

		JPanel spiceParallelChancePanel = new JPanel();
		spiceParallelChancePanel.add(spiceParallelChance);
		spiceParallelChancePanel.setOpaque(false);

		firstChordsPanel.add(new JLabel("First:"));
		firstChordsPanel.add(firstChordSelection);
		lastChordsPanel.add(new JLabel("Last:"));
		lastChordsPanel.add(lastChordSelection);

		chordProgressionSettingsPanel.add(spiceChancePanel);
		chordProgressionSettingsPanel.add(spiceAllowDimAugPanel);
		chordProgressionSettingsPanel.add(spiceAllow9th13thPanel);

		chordProgressionSettingsPanel.add(spiceForceScalePanel);
		chordProgressionSettingsPanel.add(spiceParallelChancePanel);
		chordProgressionSettingsPanel.add(firstChordsPanel);
		chordProgressionSettingsPanel.add(lastChordsPanel);

		return chordProgressionSettingsPanel;
	}

    public JPanel initCustomChords() {
		JPanel customChordsPanel = new JPanel();
		customChordsPanel.setOpaque(false);
		customChordsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		/*tipLabel = new JLabel(
				"Chord meaning: 1 = I(major), 10 = i(minor), 100 = I(aug), 1000 = I(dim), 10000 = I7(major), "
						+ "100000 = i7(minor), 1000000 = 9th, 10000000 = 13th, 100000000 = Sus4, 1000000000 = Sus2, 10000000000 = Sus7");*/

		tipLabel = new JLabel();
		//chordToolTip.add(tipLabel);

        JButton randomizeCustomChords = SwingUtils.makeButton("    Randomize Chords    ", e -> {
            userChordsEnabled.setSelected(true);
            randomizeUserChords();
            userChordsEnabled.repaint();
        });
		customChordsPanel.add(randomizeCustomChords);

		userChordsEnabled = new CheckButton("Custom Chords", false);
		customChordsPanel.add(userChordsEnabled);

		userChords = new ChordletPanel(600, "Csus4", "Am", "Em", "Gsus4");
		customChordsPanel.add(userChords);

		JButton normalizeChordsButton = new JButton("N") {
			private static final long serialVersionUID = 4142323272860314396L;
			String checkedChords = "";

			@Override
			public String getToolTipText() {
				if (super.getToolTipText() == null) {
					return null;
				}
				String chords = userChords.getChordListString();
				if (!chords.equalsIgnoreCase(checkedChords)) {
					putClientProperty(JComponent.TOOL_TIP_TEXT_KEY,
							(StringUtils.join(MidiUtils.getKeyModesForChordsAndTarget(chords,
									context.getScaleMode()))));
					checkedChords = chords;
				}

				return super.getToolTipText();
			}
		};
		normalizeChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				List<String> normalizedChords = MidiUtils.processRawChords(
						userChords.getChordListString(), context.getScaleMode(),
						context::adjustScoreTranspose);
				if (normalizedChords != null) {
					userChords.setupChords(normalizedChords);
				}
			}
		});
		normalizeChordsButton.setToolTipText("N");
		customChordsPanel.add(normalizeChordsButton);

		JButton respiceChordsButton = new JButton("S");
		respiceChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				context.copyGUItoConfig();
				List<String> normalizedChords = MidiUtils
						.respiceChords(userChords.getChordListString(), context.getGUIConfig());
                userChords.setupChords(normalizedChords);
            }
		});
		customChordsPanel.add(respiceChordsButton);

		JButton twoExChordsButton = new JButton("2x");
		twoExChordsButton.setPreferredSize(new Dimension(25, 25));
		twoExChordsButton.setMargin(new Insets(0, 0, 0, 0));
		twoExChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (userChords.chordCount() < 1) {
					return;
				}
				List<String> chords = userChords.getChordList();
				List<String> chords2x = new ArrayList<>(chords);
                chords2x.addAll(chords);
				userChords.setupChords(chords2x);
			}
		});
		customChordsPanel.add(twoExChordsButton);

		JButton ddChordsButton = new JButton("Dd");
		ddChordsButton.setPreferredSize(new Dimension(25, 25));
		ddChordsButton.setMargin(new Insets(0, 0, 0, 0));
		ddChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (userChords.chordCount() < 1) {
					return;
				}
				List<String> chords = userChords.getChordList();
				List<String> chordsDd = new ArrayList<>();
				chords.forEach(ch -> {
					chordsDd.add(ch);
					chordsDd.add(ch);
				});
				userChords.setupChords(chordsDd);
			}
		});
		customChordsPanel.add(ddChordsButton);

		JButton dotdotChordsButton = new JButton("..");
		dotdotChordsButton.setPreferredSize(new Dimension(25, 25));
		dotdotChordsButton.setMargin(new Insets(0, 0, 0, 0));
		dotdotChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (userChords.chordCount() < 1) {
					return;
				}
				List<Chordlet> chordlets = userChords.getChordlets();
				List<String> chordsDotDot = new ArrayList<>();
				chordlets.forEach(ch -> {
					chordsDotDot.add(MidiUtils
							.makeSpelledChord(MidiUtils.mappedChord(ch.getChordText(), true))
							+ ch.getInversionText());
				});
				userChords.setupChords(chordsDotDot);
			}
		});
		customChordsPanel.add(dotdotChordsButton);

		JButton ivChordsButton = new JButton("Ch");
		ivChordsButton.setPreferredSize(new Dimension(25, 25));
		ivChordsButton.setMargin(new Insets(0, 0, 0, 0));
		ivChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (userChords.chordCount() < 1) {
					return;
				}
				List<Chordlet> chordlets = userChords.getChordlets();
				List<String> chordStrings = new ArrayList<>();
				chordlets.forEach(ch -> {
					chordStrings.add(MidiUtils
							.chordStringFromPitches(MidiUtils.mappedChord(ch.getChordText(), true))
							+ ch.getInversionText());
				});
				userChords.setupChords(chordStrings);
			}
		});
		customChordsPanel.add(ivChordsButton);

		JButton resetChordsButton = new JButton("R");
		resetChordsButton.setPreferredSize(new Dimension(25, 25));
		resetChordsButton.setMargin(new Insets(0, 0, 0, 0));
		resetChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				userChords.resetChordlets();
			}
		});
		customChordsPanel.add(resetChordsButton);

		JButton limitChordsButton = new JButton("L");
		limitChordsButton.setPreferredSize(new Dimension(25, 25));
		limitChordsButton.setMargin(new Insets(0, 0, 0, 0));
		limitChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (userChords.chordCount() > getMaxChordProgressionLength()) {
					userChords.cullChordsAbove(getMaxChordProgressionLength());
				}
			}
		});
		customChordsPanel.add(limitChordsButton);

		JButton melodifyChordsButton = new JButton(".M");
		melodifyChordsButton.setPreferredSize(new Dimension(25, 25));
		melodifyChordsButton.setMargin(new Insets(0, 0, 0, 0));
		melodifyChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				context.alignChordsWithMelody(userChords);
			}
		});
		customChordsPanel.add(melodifyChordsButton);

		JButton chordTransformButton = new JButton("T");
		chordTransformButton.setPreferredSize(new Dimension(25, 25));
		chordTransformButton.setMargin(new Insets(0, 0, 0, 0));
		chordTransformButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				new ChordTransformPopup(userChords.getChordListString());
			}
		});
		customChordsPanel.add(chordTransformButton);

		userDurationsEnabled = new CheckButton("Custom Durations", false);
		customChordsPanel.add(userDurationsEnabled);
		userChordsDurations = new JTextField("4,4,4,4", 9);
		customChordsPanel.add(userChordsDurations);

		UITheme.toggleableComponents.add(twoExChordsButton);
		UITheme.toggleableComponents.add(userDurationsEnabled);
		UITheme.toggleableComponents.add(userChordsDurations);
		UITheme.toggleableComponents.add(dotdotChordsButton);
		UITheme.toggleableComponents.add(ddChordsButton);
		UITheme.toggleableComponents.add(normalizeChordsButton);
		UITheme.toggleableComponents.add(ivChordsButton);
		UITheme.toggleableComponents.add(limitChordsButton);
		UITheme.toggleableComponents.add(melodifyChordsButton);
		UITheme.toggleableComponents.add(chordTransformButton);

		return customChordsPanel;
	}

	private void randomizeUserChords() {
		context.copyGUItoConfig();
		MidiGenerator mg = new MidiGenerator(context.getGUIConfig());
		MidiGenerator.FIRST_CHORD = chordSelect(firstChordSelection.getVal());
		MidiGenerator.LAST_CHORD = chordSelect(lastChordSelection.getVal());
		MidiGenerator.userChords.clear();
		mg.generatePrettyUserChords(new Random().nextInt(),
				userChords.chordCount() > 0 ? userChords.chordCount()
						: MidiGenerator.gc.getFixedDuration(),
				4 * MidiGenerator.Durations.WHOLE_NOTE);
		userChords.setupChords(MidiGenerator.chordInts);
	}

	public static String chordSelect(String chord) {
		return MidiUtils.MAJOR_CHORDS.contains(chord) ? chord : null;
	}

	public void createRandomChordPanels(int panelCount, boolean onlyAdd,
			ChordPanel randomizedPanel) {
		ScrollComboBox.discardInteractions();
		List<ChordPanel> affectedChords = (List<ChordPanel>) (List<?>)
				panelController.getAffectedPanels(INST.CHORD);

		Random panelGenerator = new Random();
		List<ChordPanel> removedPanels = new ArrayList<>();
		List<ChordPanel> remainingPanels = new ArrayList<>();
		for (Iterator<ChordPanel> panelI = affectedChords.iterator(); panelI.hasNext();) {
			ChordPanel panel = panelI.next();
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

		int fixedChordStretch = -1;
		if (randomChordStretchType.getVal().equals("FIXED")) {
			fixedChordStretch = randomChordStretchPicker.getVal();
		}

		List<RhythmPattern> viablePatterns = RhythmPattern.VIABLE_PATTERNS;

		for (int panelIndex = 0; panelIndex < panelCount; panelIndex++) {
			boolean needNewChannel = false;
			ChordPanel ip = null;
			if (randomizedPanel != null) {
				ip = randomizedPanel;
			} else {
				if (panelIndex < removedPanels.size()) {
					ip = removedPanels.get(panelIndex);
				} else {
					ip = (ChordPanel) panelController.addPanel(INST.CHORD);
					needNewChannel = true;
				}
			}
			POOL pool = ip.getInstPool();

			if ((context.isRandomizeInstOnComposeOrGen() || onlyAdd)
					&& ip.getInstrumentBox().isEnabled()) {
				pool = (panelGenerator.nextInt(100) < randomChordSustainChance.getInt())
						? InstUtils.POOL.CHORD
						: InstUtils.POOL.PLUCK;
				ip.setInstPool(pool);
				pool = ip.getInstPool();
				ip.getInstrumentBox().initInstPool(pool);
				ip.setInstrument(ip.getInstrumentBox().getRandomInstrument());

			}

			ip.setTransitionChance(panelGenerator.nextInt(randomChordMaxSplitChance.getInt() + 1));
			ip.setTransitionSplit((OMNI.getRandomFromArray(panelGenerator, Constants.MILISECOND_ARRAY_SPLIT, 0)));
			if (ExtraSettingsGUI.orderedTransposeGeneration.isSelected()) {
				ip.setTranspose((((ip.getPanelOrder()) % 3) - 1) * 12);
			} else {
				ip.setTranspose((panelGenerator.nextInt(3) - 1) * 12);
			}

			boolean pad = ip.getInstPool() == POOL.LONG_PAD;

			Pair<StrumType, Integer> strumPair = getRandomStrumPair();
			ip.setStrum(strumPair.getRight());
			ip.setStrumType(strumPair.getLeft());
			if (randomChordDelay.isSelected()) {
				ip.setOffset((OMNI.getRandomFromArray(panelGenerator, Constants.MILISECOND_ARRAY_DELAY, 0)));
			} else {
				ip.setOffset(0);
			}


			if (randomChordUseChordFill.isSelected() && !pad) {
				ip.setChordSpanFill(ChordSpanFill.getWeighted(panelGenerator.nextInt(100)));
			} else {
				ip.setChordSpanFill(ChordSpanFill.ALL);
			}
			ip.setFillFlip(false);
			ip.setPatternFlip(false);

			// default SINGLE = 4
			RhythmPattern pattern = RhythmPattern.SINGLE;
			// use pattern in 20% of the cases if checkbox selected
			int patternChance = pool == InstUtils.POOL.PLUCK ? 25 : 10;
			if (!pad && panelGenerator.nextInt(100) < patternChance) {
				if (randomChordPattern.isSelected()) {
					pattern = viablePatterns.get(panelGenerator.nextInt(viablePatterns.size()));
					if (pattern == RhythmPattern.MELODY1) {
						pattern = RhythmPattern.FULL;
					}
					if (ip.getStrum() > 501) {
						ip.setStrum(ip.getStrum() / 2);
					}
				}
			}

			if (!randomChordStretchType.getVal().equals("NONE")
					&& panelGenerator.nextInt(100) < randomChordStretchGenerationChance.getInt()) {
				ip.setStretchEnabled(true);
				if (fixedChordStretch < 0) {
					int atMost = randomChordStretchPicker.getVal();
					ip.setChordNotesStretch(panelGenerator.nextInt(atMost - 3 + 1) + 3);
				} else {
					ip.setChordNotesStretch(fixedChordStretch);
				}
				if (ip.getChordNotesStretch() > 3 && ip.getStrum() > 999) {
					ip.setStrum(ip.getStrum() / 2);
				}
			} else {
				ip.setStretchEnabled(false);
			}

			ip.setStrumPauseChance(
					panelGenerator.nextInt(randomChordMaxStrumPauseChance.getInt() + 1));

			ip.setPattern(pattern);
			if ((pattern == RhythmPattern.FULL || pattern == RhythmPattern.MELODY1)
					&& ip.getStrum() > 499) {
				ip.setStrum(ip.getStrum() / 4);
			}

			if (pad || panelGenerator.nextInt(100) < randomChordExpandChance.getInt()) {
				ip.setPatternJoinMode(PatternJoinMode.EXPAND);
			} else {
				ip.setPatternJoinMode(PatternJoinMode.NOJOIN);
			}


			ip.setVelocityMax(randomChordMaxVel.getInt());
			ip.setVelocityMin(randomChordMinVel.getInt());

			if (randomChordVaryLength.isSelected()) {
				if (pool == InstUtils.POOL.PLUCK) {
					ip.setNoteLengthMultiplier(panelGenerator.nextInt(26) + 50);
				} else {
					ip.setNoteLengthMultiplier(panelGenerator.nextInt(26) + 85);
				}

			}

			if (panelGenerator.nextInt(100) < randomChordShiftChance.getInt()) {
				int maxShift = Math.min(ip.getPattern().maxShift, ip.getHitsPerPattern() - 1);
				// test opposite check for shift distance
				if (panelGenerator.nextInt(100) >= randomChordShiftChance.getInt()) {
					maxShift /= 2;
				}
				if (context.isBeatDurationMultiplierBelowOne()) {
					maxShift /= 2;
				}

				ip.setPatternShift(maxShift > 0 ? (panelGenerator.nextInt(maxShift) + 1) : 0);
			} else {
				ip.setPatternShift(0);
			}

			int pauseMax = (int) (50 * ip.getPattern().getNoteFrequency());
			ip.setPauseChance(panelGenerator.nextInt(pauseMax + 1));
			ip.applyPauseChance(panelGenerator);
			ip.growPattern(panelGenerator, 1, 5);

			if (needNewChannel) {
				ip.setNextFreeMidiChannel();
				ip.setPanByOrder(5);
			}
		}

	}

    /** Cleanup method called when this module is no longer needed. */
    public void cleanup() {
        // Clean up chord resources
    }
}
