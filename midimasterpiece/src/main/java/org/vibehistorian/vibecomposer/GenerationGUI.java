package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.Components.CheckButton;
import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.RandomValueButton;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.util.function.Consumer;

/** Owns the generation and macro controls in the main window. */
public class GenerationGUI {
    public interface Context {
        JButton makeButton(String name, String actionCommand);
        JButton makeButton(String name, Consumer<? super Object> action);
        JCheckBox makeCheckBox(String label, boolean selected, boolean thick);
        void addControlPanel(JPanel panel, int startY, int anchorSide);
        void alignControlPanel();
        void addToggleableComponent(Component component);
        void enthickenText(Component component);
        void randomizeBpm();
        void randomizeTranspose(boolean currentTabOnly);
        void sidechainPatterns(boolean showPopup, boolean currentTabOnly);
        void applyGlobalSwing(int swing, boolean customPanels);
        void setChordProgressionLength(int size);
    }

    public static JCheckBox randomizeInstOnComposeOrGen;
    public static JCheckBox randomizeBpmOnCompose;
    public static JCheckBox randomizeTransposeOnCompose;
    public static JCheckBox randomizeChordStrumsOnCompose;
    public static JButton switchOnComposeRandom;
    public static JButton sidechainPatterns;
    public static JButton sidechainPatternsTab;
    public static JCheckBox globalSwingOverride;
    public static KnobPanel globalSwingOverrideValue;
    public static JButton globalSwingOverrideApplyButton;
    public static ScrollComboBox<Double> beatDurationMultiplier;
    public static ScrollComboBox<String> scaleMode;
    public static KnobPanel loopBeatCount;
    public static KnobPanel mainBpm;
    public static RandomValueButton randomSeed;
    public static int lastRandomSeed;
    public static CheckButton regenerateWhenValuesChange;

    public static int getCurrentSeed() {
        return (randomSeed != null && randomSeed.getValue() != 0) ? randomSeed.getValue()
                : lastRandomSeed;
    }

    public static void saveToConfig(GUIConfig gc) {
        gc.setBeatDurationMultiplierIndex(beatDurationMultiplier.getSelectedIndex());
        gc.setGlobalSwingOverride(globalSwingOverride.isSelected() ? globalSwingOverrideValue.getInt() : null);
    }

    public static void loadFromConfig(GUIConfig gc) {
        beatDurationMultiplier.setSelectedIndex(gc.getBeatDurationMultiplierIndex());
        globalSwingOverride.setSelected(gc.getGlobalSwingOverride() != null);
        if (gc.getGlobalSwingOverride() != null) globalSwingOverrideValue.setInt(gc.getGlobalSwingOverride());
    }

    private final Context context;

    public GenerationGUI(Context context) {
        this.context = context;
    }

    public void initRandomButtons(int startY, int anchorSide) {
        JPanel randomButtonsPanel = new JPanel();
        randomButtonsPanel.setLayout(new GridLayout(0, 2));
        randomButtonsPanel.setOpaque(false);
        randomButtonsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        randomButtonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton randomizeInstruments = context.makeButton("Randomize Inst.", "RandomizeInst");
        JButton randomizeBpm = context.makeButton("Randomize BPM", e -> context.randomizeBpm());
        JButton randomizeTranspose = context.makeButton("Randomize Key", "RandomizeTranspose");

        randomizeInstOnComposeOrGen = context.makeCheckBox("on Compose/Gen", true, true);
        randomizeBpmOnCompose = context.makeCheckBox("on Compose", true, true);
        randomizeTransposeOnCompose = context.makeCheckBox("on Compose", true, true);
        randomizeInstOnComposeOrGen.setAlignmentX(Component.LEFT_ALIGNMENT);
        randomizeBpmOnCompose.setAlignmentX(Component.LEFT_ALIGNMENT);
        randomizeTransposeOnCompose.setAlignmentX(Component.LEFT_ALIGNMENT);

        randomButtonsPanel.add(randomizeInstruments);
        randomButtonsPanel.add(randomizeInstOnComposeOrGen);
        randomButtonsPanel.add(randomizeBpm);
        randomButtonsPanel.add(randomizeBpmOnCompose);
        randomButtonsPanel.add(randomizeTranspose);
        randomButtonsPanel.add(randomizeTransposeOnCompose);

        JButton randomizeStrums = context.makeButton("Randomize Strums", "RandStrums");
        randomizeStrums.setAlignmentX(Component.LEFT_ALIGNMENT);
        randomButtonsPanel.add(randomizeStrums);
        randomizeChordStrumsOnCompose = context.makeCheckBox("on Compose", false, true);

        switchOnComposeRandom = context.makeButton("Untick all 'on Compose'", "UncheckComposeRandom");
        switchOnComposeRandom.setPreferredSize(new Dimension(170, 20));
        switchOnComposeRandom.setAlignmentX(Component.LEFT_ALIGNMENT);
        switchOnComposeRandom.setFont(switchOnComposeRandom.getFont().deriveFont(6));
        context.enthickenText(switchOnComposeRandom);
        randomButtonsPanel.add(switchOnComposeRandom);

        JPanel transposePanel = new JPanel();
        transposePanel.setPreferredSize(new Dimension(170, 20));
        JButton transposeAllBtn = context.makeButton("All", e -> context.randomizeTranspose(false));
        JButton transposeTabBtn = context.makeButton("Tab", e -> context.randomizeTranspose(true));
        transposeAllBtn.setMargin(new Insets(0, 0, 0, 0));
        transposeTabBtn.setMargin(new Insets(0, 0, 0, 0));
        transposeAllBtn.setPreferredSize(new Dimension(35, 20));
        transposeTabBtn.setPreferredSize(new Dimension(35, 20));
        JLabel transposeLabel = new JLabel("R. Transpose");
        transposeLabel.setPreferredSize(new Dimension(80, 20));
        transposePanel.add(transposeLabel);
        transposePanel.add(transposeAllBtn);
        transposePanel.add(transposeTabBtn);
        randomButtonsPanel.add(transposePanel);

        JPanel sidechainPanel = new JPanel();
        sidechainPanel.setPreferredSize(new Dimension(170, 20));
        sidechainPatterns = context.makeButton("All", e -> context.sidechainPatterns(true, false));
        sidechainPatternsTab = context.makeButton("Tab", e -> context.sidechainPatterns(true, true));
        sidechainPatterns.setMargin(new Insets(0, 0, 0, 0));
        sidechainPatternsTab.setMargin(new Insets(0, 0, 0, 0));
        sidechainPatterns.setPreferredSize(new Dimension(35, 20));
        sidechainPatternsTab.setPreferredSize(new Dimension(35, 20));
        sidechainPanel.add(new JLabel("Sidechain"));
        sidechainPanel.add(sidechainPatterns);
        sidechainPanel.add(sidechainPatternsTab);
        randomButtonsPanel.add(sidechainPanel);

        context.addToggleableComponent(randomizeStrums);
        context.addToggleableComponent(sidechainPanel);
        context.addToggleableComponent(transposePanel);
        context.alignControlPanel();
        context.addControlPanel(randomButtonsPanel, startY, anchorSide);
    }

    public void initMacroParams(int startY, int anchorSide) {
        JPanel macroParams = new JPanel();
        macroParams.setLayout(new GridLayout(2, 0, 0, 0));
        macroParams.setOpaque(false);
        macroParams.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

        ChordGUI.chordProgressionLength = new ScrollComboBox<>(false);
        ScrollComboBox.addAll(new String[] { "4", "8", "RANDOM" }, ChordGUI.chordProgressionLength);
        context.setChordProgressionLength(4);
        JLabel chordDurationFixedLabel = new JLabel("# of Chords");
        JPanel chordProgPanel = new JPanel();
        chordProgPanel.add(chordDurationFixedLabel);
        chordProgPanel.add(ChordGUI.chordProgressionLength);
        chordProgPanel.setOpaque(false);
        macroParams.add(chordProgPanel);

        ChordGUI.allowChordRepeats = new CustomCheckBox("Allow Chord Repeats", true);
        JPanel allowRepPanel = new JPanel();
        allowRepPanel.add(ChordGUI.allowChordRepeats);
        allowRepPanel.setOpaque(false);
        macroParams.add(allowRepPanel);

        JPanel globalSwingPanel = new JPanel();
        globalSwingOverride = new CustomCheckBox("<html>Global Swing<br>Override</html>", false);
        globalSwingOverrideValue = new KnobPanel("", 50);
        globalSwingOverrideApplyButton = new JButton("A");
        globalSwingOverrideApplyButton.addActionListener(e ->
                context.applyGlobalSwing(globalSwingOverrideValue.getInt(), false));
        globalSwingPanel.add(globalSwingOverride);
        globalSwingPanel.add(globalSwingOverrideValue);
        globalSwingPanel.add(globalSwingOverrideApplyButton);
        globalSwingPanel.setOpaque(false);
        macroParams.add(globalSwingPanel);

        beatDurationMultiplier = new ScrollComboBox<Double>();
        ScrollComboBox.addAll(new Double[] { 0.5, 1.0, 2.0 }, beatDurationMultiplier);
        JPanel useDoubledPanel = new JPanel();
        useDoubledPanel.add(new JLabel("<html>Beat Duration<br>Multiplier</html>"));
        useDoubledPanel.add(beatDurationMultiplier);
        beatDurationMultiplier.setSelectedIndex(1);
        useDoubledPanel.setOpaque(false);
        macroParams.add(useDoubledPanel);

        chordProgPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        allowRepPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        globalSwingPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        useDoubledPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        context.addControlPanel(macroParams, startY, anchorSide);
    }
}
