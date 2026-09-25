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

package org.vibehistorian.vibecomposer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.Components.*;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.PatternJoinMode;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Enums.StrumType;
import org.vibehistorian.vibecomposer.Panels.*;
import org.vibehistorian.vibecomposer.Popups.ChordTransformPopup;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import static org.vibehistorian.vibecomposer.InstUtils.POOL;

/** Builds and owns chord controls and their UI state. */
public class ChordGUI implements InstrumentGUIControls {

    private final List<ChordPanel> chordPanels = new ArrayList<>();
    private JScrollPane chordScrollPane;
    public static JPanel chordParentPanel;
    private JCheckBox enabledCheckBox;
    private VeloRect groupFilterSlider;
    private JButton addPanelButton;
    private JButton generatePanelButton;
    private JTextField randomPanelsToGenerate;
    public static JPanel chordSettingsPanel;
    public static JLabel currentChords = new JLabel("Chords:[]");
    public static List<String> currentChordsInternal = new ArrayList<>();
    public static JLabel tipLabel;

    public static ScrollComboBox<String> chordProgressionLength;
    public static JCheckBox allowChordRepeats;
    public static JCheckBox randomChordsGenerateOnCompose;
    public static JCheckBox randomChordDelay;
    public static JCheckBox randomChordStrum;
    public static KnobPanel randomChordStruminess;
    public static JCheckBox randomChordSplit;
    public static JCheckBox randomChordTranspose;
    public static JCheckBox randomChordPattern;
    public static JCheckBox randomChordVaryLength;
    public static KnobPanel randomChordExpandChance;
    public static KnobPanel randomChordSustainChance;
    public static KnobPanel randomChordShiftChance;
    public static KnobPanel randomChordVoicingChance;
    public static KnobPanel randomChordMaxSplitChance;
    public static JCheckBox randomChordUseChordFill;
    public static ScrollComboBox<String> randomChordStretchType;
    public static ScrollComboBox<Integer> randomChordStretchPicker;
    public static KnobPanel randomChordStretchGenerationChance;
    public static KnobPanel randomChordMaxStrumPauseChance;
    public static KnobPanel randomChordMinVel;
    public static KnobPanel randomChordMaxVel;

    public static KnobPanel spiceChance;
    public static KnobPanel chordSlashChance;
    public static JCheckBox spiceAllowDimAug;
    public static JCheckBox spiceAllow9th13th;
    public static JCheckBox spiceFlattenBigChords;
    public static JCheckBox squishChordsProgressively;
    public static JCheckBox copyChordsAfterGenerate;
    public static KnobPanel spiceParallelChance;
    public static JCheckBox spiceForceScale;
    public static ScrollComboBox<String> firstChordSelection;
    public static ScrollComboBox<String> lastChordSelection;
    public static JCheckBox useChordFormula;
    public static KnobPanel longProgressionSimilarity;
    public static CheckButton userChordsEnabled;
    public static CheckButton userDurationsEnabled;
    public static JTextField userChordsDurations;
    public static ChordletPanel userChords;

    private final Context context;

    public ChordGUI(Context context) {
        this.context = context;
    }

    @Override public JCheckBox getEnabledCheckBox() { return enabledCheckBox; }
    @Override public VeloRect getGroupFilterSlider() { return groupFilterSlider; }
    @Override public JButton getAddPanelButton() { return addPanelButton; }
    @Override public JButton getGeneratePanelButton() { return generatePanelButton; }
    @Override public JTextField getRandomPanelsToGenerate() { return randomPanelsToGenerate; }
    @Override public JScrollPane getPanelScrollPane() { return chordScrollPane; }
    @Override public List<ChordPanel> getPanels() { return chordPanels; }

    /** Supplies shared window operations without coupling this module to the main window. */
    public interface Context {
        Dimension getScrollPaneDimension();
        Set<Component> getToggleableComponents();
        JButton makeButton(String name, Consumer<? super Object> action);
        JButton makeButton(String name, String actionCommand);
        void addPanel();
        void generatePanels(boolean triggerRegenerate);
        GridBagConstraints getConstraints();
        JPanel getControlPanel();
        JPanel getEverythingPanel();
        ItemListener getItemListener();
        String getScaleMode();
        GUIConfig getGuiConfig();
        void copyGUItoConfig();
        void randomizeUserChords();
        int getMaxChordProgressionLength();
        void alignChordsWithMelody(ChordletPanel chordlets);
        List<ChordPanel> getAffectedChordPanels();
        ChordPanel addChordPanel();
        boolean randomizeInstrumentOnComposeOrGen();
        boolean orderedTransposeGeneration();
        int getRandomFromArray(Random generator, int[] values, int from);
        Pair<StrumType, Integer> getRandomStrumPair();
        boolean useShortBeatDuration();
        void repaintMainWindow();
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

    public void initChordGenSettings(int startY, int anchorSide) {
		JPanel scrollableChordPanels = new JPanel();
		scrollableChordPanels.setLayout(new BoxLayout(scrollableChordPanels, BoxLayout.Y_AXIS));
		scrollableChordPanels.setAutoscrolls(true);

		chordScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(context.getScrollPaneDimension().width, context.getScrollPaneDimension().height - 100);
			}
		};
		chordScrollPane.setViewportView(scrollableChordPanels);
		chordScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		chordScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        chordSettingsPanel = new JPanel();
		chordSettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

		enabledCheckBox = new CustomCheckBox("CHORDS", true);
		chordSettingsPanel.add(enabledCheckBox);
		groupFilterSlider = VeloRect.midi( 127);
		JLabel filterLabel = new JLabel("LP");
		chordSettingsPanel.add(filterLabel);
		chordSettingsPanel.add(groupFilterSlider);

		addPanelButton = context.makeButton("+Chord", e -> {
			context.addPanel();
		});
		generatePanelButton = context.makeButton("Generate Chords:", e -> {
			context.generatePanels(true);
		});
		randomPanelsToGenerate = new JTextField("2", 2);
		chordSettingsPanel.add(addPanelButton);
		chordSettingsPanel.add(generatePanelButton);
		chordSettingsPanel.add(randomPanelsToGenerate);

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

		JButton clearChordPatternSeeds = context.makeButton("Clear Seeds", "ClearChordSeeds");

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
		chordSettingsExtraPanel.add(new PartManagerPanel(2));

		context.getToggleableComponents().add(randomChordDelay);
		context.getToggleableComponents().add(stretchLabel);
		context.getToggleableComponents().add(randomChordStretchType);
		context.getToggleableComponents().add(randomChordStretchPicker);
		context.getToggleableComponents().add(randomChordSplit);

		context.getToggleableComponents().add(chordSettingsExtraPanel);


		//constraints.gridy = startY;
		//constraints.anchor = anchorSide;
		chordSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		chordSettingsPanel.setMaximumSize(new Dimension(1800, 50));
		//scrollableChordPanels.add(chordSettingsPanel);
		chordSettingsExtraPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		chordSettingsExtraPanel.setMaximumSize(new Dimension(1800, 50));
		//constraints.gridy = startY + 1;

		//scrollableChordPanels.add(chordSettingsExtraPanel);


		chordParentPanel = new JPanel() {
			@Override
			public Dimension getPreferredSize() {
				return context.getScrollPaneDimension();
			}
		};
		chordParentPanel.setLayout(new BoxLayout(chordParentPanel, BoxLayout.Y_AXIS));

		JPanel borderPanel = new JPanel() {
			@Override
			public Dimension getMaximumSize() {
				return new Dimension(context.getScrollPaneDimension().width, 100);
			}
		};
		borderPanel.setLayout(new DynamicGridLayout(0, 1));
		borderPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
		borderPanel.add(chordSettingsPanel);
		borderPanel.add(chordSettingsExtraPanel);
		chordParentPanel.add(borderPanel);
		chordParentPanel.add(chordScrollPane);

		//addHorizontalSeparatorToPanel(scrollableChordPanels);
	}

    public JPanel initChords() {
		return chordParentPanel;
	}

    public void initChordProgressionSettings(int startY, int anchorSide) {
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
		firstChordSelection.addItemListener(context.getItemListener());

		lastChordSelection = new ScrollComboBox<String>(false);
		lastChordSelection.addItem("?");
		ScrollComboBox.addAll(MidiUtils.MAJOR_CHORDS.toArray(new String[] {}), lastChordSelection);
		lastChordSelection.addItemListener(context.getItemListener());

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

		context.getConstraints().gridy = startY;
		context.getConstraints().anchor = anchorSide;
		context.getControlPanel().add(chordProgressionSettingsPanel);
	}

    public void initCustomChords(int startY, int anchorSide) {
		JPanel customChordsPanel = new JPanel();
		customChordsPanel.setOpaque(false);
		customChordsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		/*tipLabel = new JLabel(
				"Chord meaning: 1 = I(major), 10 = i(minor), 100 = I(aug), 1000 = I(dim), 10000 = I7(major), "
						+ "100000 = i7(minor), 1000000 = 9th, 10000000 = 13th, 100000000 = Sus4, 1000000000 = Sus2, 10000000000 = Sus7");*/

		tipLabel = new JLabel();
		//chordToolTip.add(tipLabel);

		JButton randomizeCustomChords = context.makeButton("    Randomize Chords    ", e -> {
			userChordsEnabled.setSelected(true);
			context.randomizeUserChords();
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
									MidiUtils.ScaleMode.valueOf(context.getScaleMode())))));
					checkedChords = chords;
				}

				return super.getToolTipText();
			}
		};
		normalizeChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				List<String> normalizedChords = MidiUtils.processRawChords(
						userChords.getChordListString(), MidiUtils.ScaleMode.valueOf(context.getScaleMode()));
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
						.respiceChords(userChords.getChordListString(), context.getGuiConfig());
				if (normalizedChords != null) {
					userChords.setupChords(normalizedChords);
				}
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
				chords.forEach(ch -> {
					chords2x.add(ch);
				});
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
				if (userChords.chordCount() > context.getMaxChordProgressionLength()) {
					userChords.cullChordsAbove(context.getMaxChordProgressionLength());
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


		context.getConstraints().gridy = startY;
		context.getConstraints().anchor = anchorSide;
		context.getEverythingPanel().add(customChordsPanel, context.getConstraints());

		context.getToggleableComponents().add(twoExChordsButton);
		context.getToggleableComponents().add(userDurationsEnabled);
		context.getToggleableComponents().add(userChordsDurations);
		context.getToggleableComponents().add(dotdotChordsButton);
		context.getToggleableComponents().add(ddChordsButton);
		context.getToggleableComponents().add(normalizeChordsButton);
		context.getToggleableComponents().add(ivChordsButton);
		context.getToggleableComponents().add(limitChordsButton);
		context.getToggleableComponents().add(melodifyChordsButton);
		context.getToggleableComponents().add(chordTransformButton);

	}

public void createRandomChordPanels(int panelCount, boolean onlyAdd,
			ChordPanel randomizedPanel) {
		ScrollComboBox.discardInteractions();
		List<ChordPanel> affectedChords = context.getAffectedChordPanels();

		Random panelGenerator = new Random();
		List<ChordPanel> removedPanels = new ArrayList<>();
		List<ChordPanel> remainingPanels = new ArrayList<>();
		for (Iterator<ChordPanel> panelI = affectedChords.iterator(); panelI.hasNext();) {
			ChordPanel panel = panelI.next();
			if (!onlyAdd && !panel.getLockInst()) {
				if (removedPanels.size() >= panelCount) {
					((JPanel) chordScrollPane.getViewport().getView()).remove(panel);
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
		if (ChordGUI.randomChordStretchType.getVal().equals("FIXED")) {
			fixedChordStretch = ChordGUI.randomChordStretchPicker.getVal();
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
					ip = (ChordPanel) context.addChordPanel();
					needNewChannel = true;
				}
			}
			InstUtils.POOL pool = ip.getInstPool();

			if ((context.randomizeInstrumentOnComposeOrGen() || onlyAdd)
					&& ip.getInstrumentBox().isEnabled()) {
				pool = (panelGenerator.nextInt(100) < ChordGUI.randomChordSustainChance.getInt())
						? InstUtils.POOL.CHORD
						: InstUtils.POOL.PLUCK;
				ip.setInstPool(pool);
				pool = ip.getInstPool();
				ip.getInstrumentBox().initInstPool(pool);
				ip.setInstrument(ip.getInstrumentBox().getRandomInstrument());

			}

			ip.setTransitionChance(panelGenerator.nextInt(ChordGUI.randomChordMaxSplitChance.getInt() + 1));
			ip.setTransitionSplit((context.getRandomFromArray(panelGenerator, Constants.MILISECOND_ARRAY_SPLIT, 0)));
			if (context.orderedTransposeGeneration()) {
				ip.setTranspose((((ip.getPanelOrder()) % 3) - 1) * 12);
			} else {
				ip.setTranspose((panelGenerator.nextInt(3) - 1) * 12);
			}

			boolean pad = ip.getInstPool() == POOL.LONG_PAD;

			Pair<StrumType, Integer> strumPair = context.getRandomStrumPair();
			ip.setStrum(strumPair.getRight());
			ip.setStrumType(strumPair.getLeft());
			if (ChordGUI.randomChordDelay.isSelected()) {
				ip.setOffset((context.getRandomFromArray(panelGenerator, Constants.MILISECOND_ARRAY_DELAY, 0)));
			} else {
				ip.setOffset(0);
			}


			if (ChordGUI.randomChordUseChordFill.isSelected() && !pad) {
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
				if (ChordGUI.randomChordPattern.isSelected()) {
					pattern = viablePatterns.get(panelGenerator.nextInt(viablePatterns.size()));
					if (pattern == RhythmPattern.MELODY1) {
						pattern = RhythmPattern.FULL;
					}
					if (ip.getStrum() > 501) {
						ip.setStrum(ip.getStrum() / 2);
					}
				}
			}

			if (!ChordGUI.randomChordStretchType.getVal().equals("NONE")
					&& panelGenerator.nextInt(100) < ChordGUI.randomChordStretchGenerationChance.getInt()) {
				ip.setStretchEnabled(true);
				if (fixedChordStretch < 0) {
					int atMost = ChordGUI.randomChordStretchPicker.getVal();
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
					panelGenerator.nextInt(ChordGUI.randomChordMaxStrumPauseChance.getInt() + 1));

			ip.setPattern(pattern);
			if ((pattern == RhythmPattern.FULL || pattern == RhythmPattern.MELODY1)
					&& ip.getStrum() > 499) {
				ip.setStrum(ip.getStrum() / 4);
			}

			if (pad || panelGenerator.nextInt(100) < ChordGUI.randomChordExpandChance.getInt()) {
				ip.setPatternJoinMode(PatternJoinMode.EXPAND);
			} else {
				ip.setPatternJoinMode(PatternJoinMode.NOJOIN);
			}


			ip.setVelocityMax(ChordGUI.randomChordMaxVel.getInt());
			ip.setVelocityMin(ChordGUI.randomChordMinVel.getInt());

			if (ChordGUI.randomChordVaryLength.isSelected()) {
				if (pool == InstUtils.POOL.PLUCK) {
					ip.setNoteLengthMultiplier(panelGenerator.nextInt(26) + 50);
				} else {
					ip.setNoteLengthMultiplier(panelGenerator.nextInt(26) + 85);
				}

			}

			if (panelGenerator.nextInt(100) < ChordGUI.randomChordShiftChance.getInt()) {
				int maxShift = Math.min(ip.getPattern().maxShift, ip.getHitsPerPattern() - 1);
				// test opposite check for shift distance
				if (panelGenerator.nextInt(100) >= ChordGUI.randomChordShiftChance.getInt()) {
					maxShift /= 2;
				}
				if (context.useShortBeatDuration()) {
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

		context.repaintMainWindow();
	}

    /** Cleanup method called when this module is no longer needed. */
    public void cleanup() {
        // Clean up chord resources
    }
}
