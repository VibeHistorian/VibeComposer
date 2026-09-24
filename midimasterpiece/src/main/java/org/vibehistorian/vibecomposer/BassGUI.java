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

package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.DynamicGridLayout;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Components.VeloRect;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Panels.BassPanel;
import org.vibehistorian.vibecomposer.Panels.PartManagerPanel;

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

/**
 * Bass GUI module - Handles all bass-related UI components and logic.
 */
public class BassGUI {

    public static List<BassPanel> bassPanels = new ArrayList<>();
    public static JScrollPane bassScrollPane;
    public static JPanel bassParentPanel;

    private final Context context;

    public BassGUI(Context context) {
        this.context = context;
    }

    /** Supplies shared window operations without making this module depend on the main window. */
    public interface Context {
        Dimension getScrollPaneDimension();
        JCheckBox[] getAddInst();
        VeloRect[] getGroupFilterSliders();
        JButton[] getAddPanelButtons();
        JButton[] getGeneratePanelButtons();
        JTextField[] getRandomPanelsToGenerate();
        JButton makeButton(String name, Consumer<? super Object> action);
        void addPanel(int part);
        void generatePanels(int part, boolean triggerRegenerate);
        List<BassPanel> getAffectedBassPanels();
        BassPanel addBassPanel();
        boolean randomizeInstrumentOnComposeOrGen();
        void repaintMainWindow();
    }

    /**
     * Build the bass tab and its shared controls.
     */
    public void initBass(int startY, int anchorSide, GridBagConstraints constraints,
            JTabbedPane instrumentTabPane) {
        JPanel scrollableBassPanels = new JPanel();
        scrollableBassPanels.setLayout(new BoxLayout(scrollableBassPanels, BoxLayout.Y_AXIS));
        scrollableBassPanels.setAutoscrolls(true);

        bassScrollPane = new JScrollPane() {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = context.getScrollPaneDimension();
                return new Dimension(size.width, size.height - 100);
            }
        };
        bassScrollPane.setViewportView(scrollableBassPanels);
        bassScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        bassScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel bassSettingsPanel = new JPanel();
        JCheckBox[] addInst = context.getAddInst();
        VeloRect[] groupFilterSliders = context.getGroupFilterSliders();
        JButton[] addPanelButtons = context.getAddPanelButtons();
        JButton[] generatePanelButtons = context.getGeneratePanelButtons();
        JTextField[] randomPanelsToGenerate = context.getRandomPanelsToGenerate();
        addInst[1] = new CustomCheckBox("BASS", true);
        bassSettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        bassSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bassSettingsPanel.setMaximumSize(new Dimension(1800, 50));
        bassSettingsPanel.add(addInst[1]);

        groupFilterSliders[1] = VeloRect.midi(127);
        bassSettingsPanel.add(new JLabel("LP"));
        bassSettingsPanel.add(groupFilterSliders[1]);

        addPanelButtons[1] = context.makeButton("+Bass", e -> context.addPanel(1));
        generatePanelButtons[1] = context.makeButton("Generate Basses:",
                e -> context.generatePanels(1, true));
        randomPanelsToGenerate[1] = new JTextField("1", 2);
        bassSettingsPanel.add(addPanelButtons[1]);
        bassSettingsPanel.add(generatePanelButtons[1]);
        bassSettingsPanel.add(randomPanelsToGenerate[1]);

        JPanel bassSettingsAdvancedPanel = new JPanel();
        bassSettingsAdvancedPanel.add(new JLabel("BASS SETTINGS+"));
        bassSettingsAdvancedPanel.add(new PartManagerPanel(1));
        bassSettingsAdvancedPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        bassSettingsAdvancedPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bassSettingsAdvancedPanel.setMaximumSize(new Dimension(1800, 50));

        bassParentPanel = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                return context.getScrollPaneDimension();
            }
        };
        bassParentPanel.setLayout(new BoxLayout(bassParentPanel, BoxLayout.Y_AXIS));
        JPanel borderPanel = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(context.getScrollPaneDimension().width, 100);
            }
        };
        borderPanel.setLayout(new DynamicGridLayout(0, 1));
        borderPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
        borderPanel.add(bassSettingsPanel);
        borderPanel.add(bassSettingsAdvancedPanel);
        bassParentPanel.add(borderPanel);
        bassParentPanel.add(bassScrollPane);

        constraints.gridy = startY;
        constraints.anchor = anchorSide;
        instrumentTabPane.addTab("Bass", bassParentPanel);
    }

    public void createRandomBassPanels(int panelCount, boolean onlyAdd,
            BassPanel randomizedPanel) {
        createRandomBassPanels(new Random().nextInt(), panelCount, onlyAdd, null);
    }

    public void createRandomBassPanels(int seed, int panelCount, boolean onlyAdd,
            BassPanel randomizedPanel) {
        ScrollComboBox.discardInteractions();
        List<BassPanel> affectedBasses = context.getAffectedBassPanels();

        Random panelGenerator = new Random(seed);
        List<BassPanel> removedPanels = new ArrayList<>();
        List<BassPanel> remainingPanels = new ArrayList<>();
        for (Iterator<BassPanel> panelI = affectedBasses.iterator(); panelI.hasNext();) {
            BassPanel panel = panelI.next();
            if (!onlyAdd && !panel.getLockInst()) {
                if (removedPanels.size() >= panelCount) {
                    ((JPanel) bassScrollPane.getViewport().getView()).remove(panel);
                    panelI.remove();
                } else {
                    removedPanels.add(panel);
                }
            } else {
                remainingPanels.add(panel);
            }
        }
        Collections.sort(removedPanels, Comparator.comparing(BassPanel::getPanelOrder));
        panelCount -= remainingPanels.size();
        ChordSpanFill[] bassFills = { ChordSpanFill.ALL, ChordSpanFill.ALL, ChordSpanFill.EVEN,
                ChordSpanFill.ODD, ChordSpanFill.HALF1, ChordSpanFill.HALF2 };
        List<RhythmPattern> viablePatterns = RhythmPattern.VIABLE_PATTERNS;

        for (int panelIndex = 0; panelIndex < panelCount; panelIndex++) {
            boolean needNewChannel = false;
            BassPanel ip;
            if (randomizedPanel != null) {
                ip = randomizedPanel;
            } else if (panelIndex < removedPanels.size()) {
                ip = removedPanels.get(panelIndex);
            } else {
                ip = context.addBassPanel();
                needNewChannel = true;
            }
            if (context.randomizeInstrumentOnComposeOrGen()) {
                ip.setInstrument(ip.getInstrumentBox().getRandomInstrument());
            }
            int panelOrder = ip.getPanelOrder();

            ip.setFillFlip(false);

            if (panelOrder > 1) {
                ip.setChordSpanFill(bassFills[panelGenerator.nextInt(bassFills.length)]);
                ip.setPatternSeed(seed);
                ip.setPauseChance(30 + panelGenerator.nextInt(40));
                ip.setVelocityMax(50 + panelGenerator.nextInt(20));
                ip.setVelocityMin(30 + panelGenerator.nextInt(20));
                if (panelOrder % 2 == 0) {
                    ip.setTranspose(0);
                } else {
                    ip.setTranspose(12);
                }
                ip.setNoteLengthMultiplier(60 + panelGenerator.nextInt(40));

                // default SINGLE = 4
                RhythmPattern pattern = RhythmPattern.SINGLE;
                // use pattern in 50% of the cases
                int patternChance = 50;
                if (panelGenerator.nextInt(100) < patternChance) {
                    pattern = viablePatterns.get(panelGenerator.nextInt(viablePatterns.size()));
                    if (pattern == RhythmPattern.MELODY1) {
                        pattern = RhythmPattern.FULL;
                    }
                }
                ip.setPattern(pattern);

                int hits = 4;
                while (panelGenerator.nextBoolean() && hits < 16) {
                    hits *= 2;
                }
                if ((hits / ip.getChordSpan() >= 8)) {
                    hits /= 2;
                }

                ip.setHitsPerPattern(hits * 2);

            } else {
                ip.setPauseChance(panelGenerator.nextInt(10));
                ip.setTranspose(0);
                ip.setVelocityMax(60 + panelGenerator.nextInt(30));
                ip.setVelocityMin(40 + panelGenerator.nextInt(25));
                ip.setNoteLengthMultiplier(80 + panelGenerator.nextInt(25));
            }

            if (needNewChannel) {
                ip.setMidiChannel(9);
            }
        }
        context.repaintMainWindow();
    }
    
    /**
     * Cleanup method called when this module is no longer needed.
     */
    public void cleanup() {
        // Clean up bass resources
    }
}
