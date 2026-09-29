package org.vibehistorian.vibecomposer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.Components.CheckButton;
import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.RandomValueButton;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Enums.StrumType;
import org.vibehistorian.vibecomposer.Panels.ArpPanel;
import org.vibehistorian.vibecomposer.Panels.BassPanel;
import org.vibehistorian.vibecomposer.Panels.ChordPanel;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;
import org.vibehistorian.vibecomposer.Panels.MelodyPanel;
import org.vibehistorian.vibecomposer.Popups.TemporaryInfoPopup;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Owns the generation and macro controls in the main window. */
public class GenerationGUI {
    public interface Context {
        JButton makeButton(String name, String actionCommand);
        void switchAllOnComposeCheckboxes(boolean state);
        int getSelectedInstrumentTab();
        void regenerate();
    }

    public static JCheckBox randomizeInstOnComposeOrGen;
    public static JCheckBox randomizeBpmOnCompose;
    public static JCheckBox randomizeTransposeOnCompose;
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

    public static boolean canRegenerateOnChange() {
        return PlaybackState.sequencer != null && regenerateWhenValuesChange.isSelected()
                && !ApplicationSessionState.heavyBackgroundTasksInProgress
                && ArrangementGUI.arrSection.getSelectedIndex() == 0;
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
    private final InstrumentPanelController panelController;
    private boolean onComposeOptionsEnabled = true;

    public GenerationGUI(Context context, InstrumentPanelController panelController) {
        this.context = context;
        this.panelController = panelController;
    }

    public JPanel initRandomButtons() {
        JPanel randomButtonsPanel = new JPanel();
        randomButtonsPanel.setLayout(new GridLayout(0, 2));
        randomButtonsPanel.setOpaque(false);
        randomButtonsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        randomButtonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton randomizeInstruments = context.makeButton("Randomize Inst.", "RandomizeInst");
        JButton randomizeBpm = SwingUtils.makeButton("Randomize BPM", e -> randomizeBpm());
        JButton randomizeTranspose = context.makeButton("Randomize Key", "RandomizeTranspose");

        randomizeInstOnComposeOrGen = SwingUtils.makeCheckBox("on Compose/Gen", true, true);
        randomizeBpmOnCompose = SwingUtils.makeCheckBox("on Compose", true, true);
        randomizeTransposeOnCompose = SwingUtils.makeCheckBox("on Compose", true, true);
        randomizeInstOnComposeOrGen.setAlignmentX(Component.LEFT_ALIGNMENT);
        randomizeBpmOnCompose.setAlignmentX(Component.LEFT_ALIGNMENT);
        randomizeTransposeOnCompose.setAlignmentX(Component.LEFT_ALIGNMENT);

        randomButtonsPanel.add(randomizeInstruments);
        randomButtonsPanel.add(randomizeInstOnComposeOrGen);
        randomButtonsPanel.add(randomizeBpm);
        randomButtonsPanel.add(randomizeBpmOnCompose);
        randomButtonsPanel.add(randomizeTranspose);
        randomButtonsPanel.add(randomizeTransposeOnCompose);

        JButton randomizeStrums = SwingUtils.makeButton("Randomize Strums", e -> {
            for (InstPanel p : panelController.getAffectedPanels(INST.CHORD)) {
                ChordPanel cp = (ChordPanel) p;
                Pair<StrumType, Integer> strumPair = ChordGUI.getRandomStrumPair();
                cp.setStrum(strumPair.getRight());
                cp.setStrumType(strumPair.getLeft());
                if (cp.getStretchEnabled() && cp.getChordNotesStretch() > 4
                        && cp.getStrum() > 999) {
                    cp.setStrum(cp.getStrum() / 2);
                }
            }
            if (canRegenerateOnChange()) {
                context.regenerate();
            }
        });
        randomizeStrums.setAlignmentX(Component.LEFT_ALIGNMENT);
        randomButtonsPanel.add(randomizeStrums);

        switchOnComposeRandom = SwingUtils.makeButton("Untick all 'on Compose'", e -> {
            onComposeOptionsEnabled = !onComposeOptionsEnabled;
            context.switchAllOnComposeCheckboxes(onComposeOptionsEnabled);
            switchOnComposeRandom.setText(onComposeOptionsEnabled
                    ? "Untick all 'on Compose'" : "  Tick all 'on Compose'   ");
        });
        switchOnComposeRandom.setPreferredSize(new Dimension(170, 20));
        switchOnComposeRandom.setAlignmentX(Component.LEFT_ALIGNMENT);
        switchOnComposeRandom.setFont(switchOnComposeRandom.getFont().deriveFont(6));
        enthickenText(switchOnComposeRandom);
        randomButtonsPanel.add(switchOnComposeRandom);

        JPanel transposePanel = new JPanel();
        transposePanel.setPreferredSize(new Dimension(170, 20));
        JButton transposeAllBtn = SwingUtils.makeButton("All", e -> randomizeTranspose(false));
        JButton transposeTabBtn = SwingUtils.makeButton("Tab", e -> randomizeTranspose(true));
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
        sidechainPatterns = SwingUtils.makeButton("All", e -> sidechainPatterns(true, false));
        sidechainPatternsTab = SwingUtils.makeButton("Tab", e -> sidechainPatterns(true, true));
        sidechainPatterns.setMargin(new Insets(0, 0, 0, 0));
        sidechainPatternsTab.setMargin(new Insets(0, 0, 0, 0));
        sidechainPatterns.setPreferredSize(new Dimension(35, 20));
        sidechainPatternsTab.setPreferredSize(new Dimension(35, 20));
        sidechainPanel.add(new JLabel("Sidechain"));
        sidechainPanel.add(sidechainPatterns);
        sidechainPanel.add(sidechainPatternsTab);
        randomButtonsPanel.add(sidechainPanel);

        UITheme.toggleableComponents.add(randomizeStrums);
        UITheme.toggleableComponents.add(sidechainPanel);
        UITheme.toggleableComponents.add(transposePanel);
        return randomButtonsPanel;
    }

    public void randomizeBpm() {
        Random random = new Random();
        int bpm = random.nextInt(1 + ExtraSettingsGUI.bpmHigh.getInt() - ExtraSettingsGUI.bpmLow.getInt())
                + ExtraSettingsGUI.bpmLow.getInt();
        if (ArpGUI.arpAffectsBpm.isSelected()
                && !panelController.getInstList(INST.ARP).isEmpty()) {
            double highestArpPattern = panelController.getInstList(INST.ARP).stream()
                    .map(panel -> ( ((ArpPanel) panel).getPatternRepeat()
                            * ((ArpPanel) panel).getHitsPerPattern())
                            / (((ArpPanel) panel).getChordSpan() * 8.0))
                    .max(Double::compare).get();
            LG.i("Repeater value: " + highestArpPattern);
            if (highestArpPattern > 1) {
                bpm = (int) (bpm * (1 / (0.5 + highestArpPattern * 0.5)));
            }
        }
        mainBpm.setInt(bpm);
        mainBpm.getKnob().setMin(ExtraSettingsGUI.bpmLow.getInt());
        mainBpm.getKnob().setMax(ExtraSettingsGUI.bpmHigh.getInt());
    }

    public void randomizeInstruments() {
        Random random = new Random();
        for (InstPanel panel : panelController.getInstList(INST.CHORD)) {
            ChordPanel chordPanel = (ChordPanel) panel;
            if (!chordPanel.getLockInst()) {
                InstUtils.POOL pool = random.nextInt(100) < ChordGUI.randomChordSustainChance.getInt()
                        ? InstUtils.POOL.CHORD : InstUtils.POOL.PLUCK;
                chordPanel.setInstPool(pool);
                chordPanel.getInstrumentBox().initInstPool(chordPanel.getInstPool());
                chordPanel.setInstrument(chordPanel.getInstrumentBox().getRandomInstrument());
            }
        }
        for (InstPanel panel : panelController.getInstList(INST.ARP)) {
            ArpPanel arpPanel = (ArpPanel) panel;
            if (!arpPanel.getLockInst()) {
                arpPanel.getInstrumentBox().setInstrument(arpPanel.getInstrumentBox().getRandomInstrument());
            }
        }
        List<? extends InstPanel> melodyPanels = panelController.getInstList(INST.MELODY);
        if (!melodyPanels.isEmpty()) {
            if (!MelodyGUI.combineMelodyTracks.isSelected()) {
                for (InstPanel panel : melodyPanels) {
                    MelodyPanel melodyPanel = (MelodyPanel) panel;
                    if (!melodyPanel.getLockInst()) {
                        melodyPanel.getInstrumentBox().setInstrument(
                                melodyPanel.getInstrumentBox().getRandomInstrument());
                    }
                }
            } else {
                int instrument = ((MelodyPanel) melodyPanels.get(0)).getInstrumentBox().getRandomInstrument();
                for (InstPanel panel : melodyPanels) {
                    MelodyPanel melodyPanel = (MelodyPanel) panel;
                    if (!melodyPanel.getLockInst()) {
                        melodyPanel.getInstrumentBox().setInstrument(instrument);
                    }
                }
            }
        }
        for (InstPanel panel : panelController.getInstList(INST.BASS)) {
            BassPanel bassPanel = (BassPanel) panel;
            if (!bassPanel.getLockInst()) {
                bassPanel.getInstrumentBox().setInstrument(bassPanel.getInstrumentBox().getRandomInstrument());
            }
        }
    }

    public void randomizeTranspose(boolean currentTabOnly) {
        int currentTab = context.getSelectedInstrumentTab();
        if (currentTabOnly && currentTab >= 4) {
            new TemporaryInfoPopup("Nothing to transpose in this tab!", null);
            return;
        }
        int start = currentTabOnly ? currentTab : 0;
        int end = currentTabOnly ? currentTab : 3;
        Random rand = new Random();
        for (int instrument = start; instrument <= end; instrument++) {
            INST instrumentType = INST.fromIndex(instrument);
            List<Integer> availableTransposes = new ArrayList<>(
                    instrumentType == INST.BASS
                            ? Arrays.asList(-12, 0) : Arrays.asList(-12, 0, 12));
            List<InstPanel> panels = panelController.getAffectedPanels(
                    instrumentType);
            int maxSame = Math.max(2, (int) Math.ceil(panels.size() / 3.0));
            int[] transposesApplied = { 0, 0, 0 };
            for (InstPanel panel : panels) {
                int transpose = availableTransposes.get(rand.nextInt(availableTransposes.size()));
                panel.setTranspose(transpose);
                int transposeIndex = (transpose / 12) + 1;
                transposesApplied[transposeIndex]++;
                if (transposesApplied[transposeIndex] >= maxSame && availableTransposes.size() > 1) {
                    availableTransposes.remove(Integer.valueOf(transpose));
                }
            }
        }
        if (canRegenerateOnChange()) {
            context.regenerate();
        }
    }

    public void sidechainPatterns(boolean showPopup, boolean currentTabOnly) {
        int currentTab = context.getSelectedInstrumentTab();
        if (currentTabOnly && (currentTab <= 1 || currentTab >= 5)) {
            new TemporaryInfoPopup("Only chords/arps/drums can be sidechained!", null);
            return;
        }
        int multiplier = currentTabOnly && currentTab < 4 ? 3 : 1;
        int[] rhythmGrid = new int[4 * 32];
        Random rand = new Random();
        Random permutationRand = new Random();
        int[] panelChanges = new int[3];
        List<INST> instruments = currentTabOnly
                ? java.util.Collections.singletonList(INST.fromIndex(currentTab))
                : Arrays.asList(INST.DRUM, INST.ARP, INST.CHORD);
        for (INST instrumentType : instruments) {
            List<? extends InstPanel> panels = panelController.getInstList(instrumentType);
            int totalChanged = 0;
            for (InstPanel panel : panels) {
                totalChanged += panel.addToRhythmGrid(rhythmGrid, rand, permutationRand, multiplier);
            }
            panelChanges[instrumentType.getIndex() - INST.CHORD.getIndex()] = totalChanged;
        }
        String popupMsg = "Chord/Arp/Drum changes: " + StringUtils.join(panelChanges, '/');
        LG.i(popupMsg);
        if (showPopup) {
            new TemporaryInfoPopup(popupMsg, null);
        }
    }

    public void applyGlobalSwing(int swing, boolean customPanels) {
        for (INST instrument : INST.values()) {
            List<? extends InstPanel> panels = customPanels
                    ? panelController.getAffectedPanels(instrument)
                    : panelController.getInstList(instrument);
            panels.forEach(panel -> panel.setSwingPercent(swing));
        }
    }

    public JPanel initMacroParams() {
        JPanel macroParams = new JPanel();
        macroParams.setLayout(new GridLayout(2, 0, 0, 0));
        macroParams.setOpaque(false);
        macroParams.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

        ChordGUI.chordProgressionLength = new ScrollComboBox<>(false);
        ScrollComboBox.addAll(new String[] { "4", "8", "RANDOM" }, ChordGUI.chordProgressionLength);
        ChordGUI.setProgressionLength(4);
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
                applyGlobalSwing(globalSwingOverrideValue.getInt(), false));
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
        return macroParams;
    }

    private void enthickenText(Component comp) {
        if (comp != null) {
            comp.setFont(comp.getFont().deriveFont(Font.BOLD));
        }
    }
}
