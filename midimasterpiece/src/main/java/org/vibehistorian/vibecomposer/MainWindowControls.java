package org.vibehistorian.vibecomposer;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.Components.CheckButton;
import org.vibehistorian.vibecomposer.Components.MidiListCellRenderer;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Components.VeloRect;
import org.vibehistorian.vibecomposer.Helpers.FileTransferHandler;
import org.vibehistorian.vibecomposer.Panels.SoloMuter;
import org.vibehistorian.vibecomposer.Popups.TemporaryInfoPopup;
import org.vibehistorian.vibecomposer.gui.ScoreGUI;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static org.vibehistorian.vibecomposer.GUIConstants.COMPOSE_COLOR;
import static org.vibehistorian.vibecomposer.UITheme.toggleableComponents;

/** Builds the shared control rows in the main window. */
public final class MainWindowControls {
    public interface HeaderContext {
        void switchDarkMode();
        void switchFullMode();
        void switchBigMonitorMode();
        void toggleExcludeNotSoloed();
        void loadPreset();
        void savePreset();
        void resetAll();
        void loadSelectedHistory(GUIConfig selectedConfig);
        void replaceSection();
        void recomposeSection();
        void openExtraSettings();
    }

    public interface ComposeContext {
        JButton makeButton(String name, String actionCommand);
        void stopPlayback();
        void performAction(ActionEvent event);
        void regenerateInPlace();
        void clearAllSeeds();
    }

    public interface PlaybackContext {
        JButton makeButton(String name, String actionCommand);
        void stopPlaybackButton();
        void playPlaybackButton();
        void pausePlaybackButton();
        void saveConfigFile(int rating);
        void saveWavFile();
        File getCurrentMidi();
        boolean hasMidiDevice();
        void closeMidiDevice();
        void softCloseSynth();
    }

    private final HeaderContext headerContext;
    private final ComposeContext composeContext;
    private final PlaybackContext playbackContext;
    private final ScrollComboBox<GUIConfig> configHistory = new ScrollComboBox<>(false);
    private SoloMuter globalSoloMuter;
    private final java.util.List<SoloMuter> groupSoloMuters = new java.util.ArrayList<>();
    private VeloRect globalVolSlider;
    private VeloRect globalReverbSlider;
    private VeloRect globalChorusSlider;
    private JLabel messageLabel;
    private ScrollComboBox<String> presetLoadBox;
    private JCheckBox randomizeScaleModeOnCompose;
    private JButton compose;
    private JButton regenerate;
    private JButton regenerateStopPlay;
    private JButton regeneratePausePlay;
    private JList<File> generatedMidi;
    private CheckButton midiMode;
    private ScrollComboBox<String> midiModeDevices;
    private JButton playMidi;
    private JButton stopMidi;
    private JButton pauseMidi;
    private JTextField saveCustomFilename;
    private JLabel savedIndicatorLabel;
    private ScrollComboBox<String> loopBeatCompose;
    private final Color[] savedIndicatorForegroundColors = {
            new Color(220, 220, 220), Color.green, Color.magenta, Color.orange };

    public MainWindowControls(HeaderContext headerContext, ComposeContext composeContext,
            PlaybackContext playbackContext) {
        this.headerContext = headerContext;
        this.composeContext = composeContext;
        this.playbackContext = playbackContext;
    }

    public ScrollComboBox<GUIConfig> getConfigHistory() {
        return configHistory;
    }

    public SoloMuter getGlobalSoloMuter() {
        return globalSoloMuter;
    }

    public SoloMuter getGroupSoloMuter(int instrumentIndex) {
        return groupSoloMuters.get(instrumentIndex);
    }

    public java.util.List<SoloMuter> getGroupSoloMuters() {
        return java.util.Collections.unmodifiableList(groupSoloMuters);
    }

    public void addHeaderControls(JPanel parent, GridBagConstraints constraints,
            int startY, int anchorSide, SoloMuter.Context soloMuterContext) {
        constraints.weightx = 100;
        constraints.weighty = 100;
        constraints.gridx = 0;
        constraints.gridy = startY;
        constraints.gridwidth = 3;
        constraints.gridheight = 1;
        constraints.anchor = anchorSide;
        //constraints.add(mainTitle, constraints);
        constraints.gridy = 1;
        //constraints.add(subTitle, constraints);

        JPanel mainButtonsPanel = new JPanel();
        mainButtonsPanel.setOpaque(false);
        constraints.gridy = startY + 3;

        globalVolSlider = new VeloRect(0, 150, 100);
        globalReverbSlider = VeloRect.midi(60);
        globalChorusSlider = VeloRect.midi(15);

        mainButtonsPanel.add(new JLabel("Vol."));
        mainButtonsPanel.add(globalVolSlider);
        mainButtonsPanel.add(new JLabel("Rv."));
        mainButtonsPanel.add(globalReverbSlider);
        mainButtonsPanel.add(new JLabel("Ch."));
        mainButtonsPanel.add(globalChorusSlider);

        globalSoloMuter = new SoloMuter(-1, SoloMuter.Type.GLOBAL, soloMuterContext);
        mainButtonsPanel.add(globalSoloMuter);
        globalSoloMuter.setBackground(null);

        mainButtonsPanel.add(SwingUtils.makeButton("Toggle Dark Mode",
                e -> headerContext.switchDarkMode()));
        mainButtonsPanel.add(SwingUtils.makeButton("Toggle Adv. Features",
                e -> headerContext.switchFullMode()));
        mainButtonsPanel.add(SwingUtils.makeButton("B I G/small",
                e -> headerContext.switchBigMonitorMode()));
        mainButtonsPanel.add(SwingUtils.makeButton("Exclude Not Solo'd",
                e -> headerContext.toggleExcludeNotSoloed()));

        mainButtonsPanel.add(SwingUtils.makeButton("Settings", e -> headerContext.openExtraSettings()));

        messageLabel = new JLabel("Click something!");
        messageLabel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));

        presetLoadBox = new ScrollComboBox<String>(false);
        presetLoadBox.setEditable(true);
        reloadPresetBox(presetLoadBox);

        mainButtonsPanel.add(presetLoadBox);
        mainButtonsPanel.add(SwingUtils.makeButtonMoused("Load Preset", e -> {
            if (SwingUtilities.isLeftMouseButton(e)) {
                headerContext.loadPreset();
            } else {
                openFolder(Constants.PRESET_FOLDER);
            }
        }));
        mainButtonsPanel.add(SwingUtils.makeButton("Save Preset", e -> headerContext.savePreset()));
        mainButtonsPanel.add(SwingUtils.makeButton("Undefault", e -> undefaultPreset()));
        mainButtonsPanel.add(SwingUtils.makeButton("Reset All", e -> headerContext.resetAll()));

        parent.add(mainButtonsPanel, constraints);
    }

    public void addSoloMuterAndTrackControls(JPanel parent, GridBagConstraints constraints,
            int startY, int anchorSide, SoloMuter.Context soloMuterContext) {
        JPanel soloMuterTrackControlPanel = new JPanel();
        soloMuterTrackControlPanel.setOpaque(false);
        JLabel emptySmLabel = new JLabel("");
        emptySmLabel.setPreferredSize(new Dimension(1, 3));
        soloMuterTrackControlPanel.add(emptySmLabel);

        groupSoloMuters.clear();
        for (INST instrument : INST.values()) {
            SoloMuter soloMuter = new SoloMuter(instrument.getIndex(), SoloMuter.Type.GROUP,
                    soloMuterContext);
            groupSoloMuters.add(soloMuter);
            soloMuterTrackControlPanel.add(soloMuter);
        }

        soloMuterTrackControlPanel.add(new JLabel("Track History: "));
        configHistory.box().setPreferredSize(new Dimension(450, 30));
        soloMuterTrackControlPanel.add(configHistory);
        soloMuterTrackControlPanel.add(SwingUtils.makeButton("Load", e -> {
            if (configHistory.getItemCount() > 0) {
                GUIConfig selectedConfig = configHistory.getSelectedItem();
                configHistory.removeItemAt(configHistory.getSelectedIndex());
                configHistory.addItem(selectedConfig);
                configHistory.setSelectedIndex(configHistory.getItemCount() - 1);
                headerContext.loadSelectedHistory(selectedConfig);
            }
        }));

        JButton replaceSectionButton = SwingUtils.makeButton("Replace Section",
                e -> headerContext.replaceSection());
        JButton recomposeSectionButton = SwingUtils.makeButton("Recompose Section",
                e -> headerContext.recomposeSection());

        soloMuterTrackControlPanel.add(replaceSectionButton);
        soloMuterTrackControlPanel.add(recomposeSectionButton);
        JTextField bookmarkField = new JTextField("Intro1", 8);
        soloMuterTrackControlPanel.add(bookmarkField);
        JButton addBookmarkButton = SwingUtils.makeButton("Add Bookmark Text", e -> {
            GUIConfig historyConfig = configHistory.getSelectedItem();
            historyConfig.setBookmarkText(bookmarkField.getText());
            configHistory.removeItemAt(configHistory.getSelectedIndex());
            configHistory.addItem(historyConfig);
            configHistory.setSelectedIndex(configHistory.getItemCount() - 1);
        });
        soloMuterTrackControlPanel.add(addBookmarkButton);

        toggleableComponents.add(bookmarkField);
        toggleableComponents.add(addBookmarkButton);
        toggleableComponents.add(replaceSectionButton);
        toggleableComponents.add(recomposeSectionButton);

        constraints.gridy = startY;
        constraints.anchor = anchorSide;
        parent.add(soloMuterTrackControlPanel, constraints);
    }

    public void addComposeControls(JPanel parent, GridBagConstraints constraints,
            int startY, int anchorSide, Component transposeControl, int minimumBpm,
            int maximumBpm, JComponent currentChords, ChordGUI chordGUI,
            GenerationGUI generationGUI) {
        JPanel controlSettingsPanel = new JPanel();
        controlSettingsPanel.setOpaque(false);
        controlSettingsPanel.add(transposeControl);

        generationGUI.initializeMainControls(minimumBpm, maximumBpm);
        controlSettingsPanel.add(generationGUI.mainBpm);

        controlSettingsPanel.add(new JLabel("Scale"));
        controlSettingsPanel.add(generationGUI.scaleMode);

        randomizeScaleModeOnCompose = SwingUtils.makeCheckBox("Rand. on Compose", true, true);
        controlSettingsPanel.add(randomizeScaleModeOnCompose);

        compose = composeContext.makeButton("COMPOSE", "Compose");
        compose.setBackground(GUIConstants.COMPOSE_COLOR);
        compose.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        compose.setPreferredSize(new Dimension(80, 40));
        compose.setFont(compose.getFont().deriveFont(Font.BOLD));
        regenerate = composeContext.makeButton("Regenerate", "Regenerate");
        regenerateStopPlay = SwingUtils.makeButton("R!", e -> {
            composeContext.stopPlayback();
            composeContext.performAction(new ActionEvent(regenerateStopPlay,
                    ActionEvent.ACTION_PERFORMED, "Regenerate"));
        });
        regeneratePausePlay = SwingUtils.makeButton("R~", e -> composeContext.regenerateInPlace());
        regenerateStopPlay.setMargin(new Insets(0, 0, 0, 0));
        regeneratePausePlay.setMargin(new Insets(0, 0, 0, 0));
        regenerateStopPlay.setPreferredSize(new Dimension(25, 30));
        regeneratePausePlay.setPreferredSize(new Dimension(25, 30));
        regenerate.setFont(regenerate.getFont().deriveFont(Font.BOLD));
        JButton copySeed = SwingUtils.makeButton("Copy Main Seed", e -> {
            generationGUI.randomSeed.setValue(generationGUI.lastRandomSeed);
            LG.i("Copied to random seed: " + generationGUI.lastRandomSeed);
        });
        JButton copyChords = SwingUtils.makeButton("Copy chords", e -> chordGUI.copyChords());
        JButton clearSeed = SwingUtils.makeButton("Clear All Seeds", e -> composeContext.clearAllSeeds());

        controlSettingsPanel.add(regenerate);
        controlSettingsPanel.add(regenerateStopPlay);
        controlSettingsPanel.add(regeneratePausePlay);
        controlSettingsPanel.add(compose);
        controlSettingsPanel.add(generationGUI.randomSeed);
        controlSettingsPanel.add(copySeed);
        controlSettingsPanel.add(currentChords);
        controlSettingsPanel.add(copyChords);
        controlSettingsPanel.add(clearSeed);

        constraints.gridy = startY;
        constraints.anchor = anchorSide;
        parent.add(controlSettingsPanel, constraints);
    }

    public void addPlaybackControls(JPanel parent, GridBagConstraints constraints,
                                    int startY, int anchorSide, ScoreGUI scoreGUI, GenerationGUI generationGUI) {
        JPanel playSavePanel = new JPanel();
        playSavePanel.setOpaque(false);
        stopMidi = SwingUtils.makeButton("STOP", e -> playbackContext.stopPlaybackButton());
        playMidi = SwingUtils.makeButton("PLAY", e -> playbackContext.playPlaybackButton());
        pauseMidi = SwingUtils.makeButton("PAUSE", e -> playbackContext.pausePlaybackButton());
        stopMidi.setFont(stopMidi.getFont().deriveFont(Font.BOLD));
        playMidi.setFont(playMidi.getFont().deriveFont(Font.BOLD));
        pauseMidi.setFont(pauseMidi.getFont().deriveFont(Font.BOLD));

        JButton save3Star = SwingUtils.makeButtonMoused("Save 3*", e -> {
            if (!SwingUtilities.isLeftMouseButton(e)) {
                openFolder(Constants.MIDIS_FOLDER + Constants.SAVED_MIDIS_FOLDER_BASE + "3star/");
            } else {
                playbackContext.saveConfigFile(3);
            }
        });
        save3Star.setForeground(savedIndicatorForegroundColors[0]);
        JButton save4Star = SwingUtils.makeButtonMoused("Save 4*", e -> {
            if (!SwingUtilities.isLeftMouseButton(e)) {
                openFolder(Constants.MIDIS_FOLDER + Constants.SAVED_MIDIS_FOLDER_BASE + "4star/");
            } else {
                playbackContext.saveConfigFile(4);
            }
        });
        save4Star.setForeground(savedIndicatorForegroundColors[1]);
        JButton save5Star = SwingUtils.makeButtonMoused("Save 5*", e -> {
            if (!SwingUtilities.isLeftMouseButton(e)) {
                openFolder(Constants.MIDIS_FOLDER + Constants.SAVED_MIDIS_FOLDER_BASE + "5star/");
            } else {
                playbackContext.saveConfigFile(5);
            }
        });
        save5Star.setForeground(savedIndicatorForegroundColors[2]);
        JButton saveCustom = SwingUtils.makeButtonMoused("Save ->", e -> {
            if (!SwingUtilities.isLeftMouseButton(e)) {
                openFolder(Constants.MIDIS_FOLDER + Constants.SAVED_MIDIS_FOLDER_BASE + "custom/");
            } else {
                playbackContext.saveConfigFile(-1);
            }
        });
        saveCustom.setForeground(savedIndicatorForegroundColors[3]);
        Calendar nowDate = Calendar.getInstance();
        String yearMonth = nowDate.get(Calendar.YEAR) + "-"
                + StringUtils.leftPad(String.valueOf(nowDate.get(Calendar.MONTH) + 1), 2, "0");
        saveCustomFilename = new JTextField(yearMonth + "/savefilename", 12);
        savedIndicatorLabel = new JLabel("[Saved!]");
        savedIndicatorLabel.setVisible(false);

        JButton loadConfig = playbackContext.makeButton("LOAD..", "LoadGUIConfig");
        JButton saveWav = SwingUtils.makeButtonMoused("Export .WAV", e -> {
            if (!SwingUtilities.isLeftMouseButton(e)) {
                openFolder(Constants.EXPORT_FOLDER);
            } else {
                playbackContext.saveWavFile();
            }
        });

        scoreGUI.createShowScoreButton();
        loopBeatCompose = new ScrollComboBox<>(false);
        ScrollComboBox.addAll(new String[] { "REGENERATE", "COMPOSE", "REPLAY" }, loopBeatCompose);

        midiMode = new CheckButton("MIDI Transmitter Mode", true);
        midiMode.setToolTipText("Select a MIDI port on the right and click Regenerate.");
        midiMode.addActionListener(e -> {
            if (playbackContext.hasMidiDevice()) {
                playbackContext.closeMidiDevice();
            } else {
                playbackContext.softCloseSynth();
            }
        });

        midiModeDevices = new ScrollComboBox<String>(false);
        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
        MidiDevice device = null;
        for (MidiDevice.Info info : infos) {
            try {
                device = MidiSystem.getMidiDevice(info);
                if (device.getMaxReceivers() != 0 && device.getMaxTransmitters() == 0) {
                    midiModeDevices.addItem(info.toString());
                    if (info.toString().startsWith("Gervill")) {
                        midiModeDevices.setVal(info.toString());
                    }
                    LG.i(("Added device: " + info.toString()));
                }
            } catch (MidiUnavailableException e) {
                LG.e(e);
            }
        }
        midiModeDevices.addActionListener(e -> {
            if (playbackContext.hasMidiDevice()) {
                playbackContext.closeMidiDevice();
            }
        });

        generatedMidi = new JList<File>();
        generatedMidi.setCellRenderer(new MidiListCellRenderer());
        generatedMidi.setTransferHandler(new FileTransferHandler(e -> playbackContext.getCurrentMidi()));
        generatedMidi.setDragEnabled(true);

        playSavePanel.add(playMidi);
        playSavePanel.add(pauseMidi);
        playSavePanel.add(stopMidi);
        playSavePanel.add(save3Star);
        playSavePanel.add(save4Star);
        playSavePanel.add(save5Star);
        playSavePanel.add(saveCustom);
        playSavePanel.add(saveCustomFilename);
        playSavePanel.add(savedIndicatorLabel);
        playSavePanel.add(loadConfig);
        playSavePanel.add(saveWav);
        playSavePanel.add(new JLabel("Midi Drag'N'Drop:"));
        playSavePanel.add(generatedMidi);

        JPanel playSettingsPanel = new JPanel();
        playSettingsPanel.setOpaque(false);
        playSettingsPanel.add(generationGUI.regenerateWhenValuesChange);
        playSettingsPanel.add(scoreGUI.getShowScoreButton());
        PlaybackState.loopBeat = new CheckButton("Loop Quarter Notes", false);
        playSettingsPanel.add(PlaybackState.loopBeat);
        playSettingsPanel.add(generationGUI.loopBeatCount);
        playSettingsPanel.add(new JLabel("On Loop:"));
        playSettingsPanel.add(loopBeatCompose);
        playSettingsPanel.add(midiMode);
        playSettingsPanel.add(midiModeDevices);

        constraints.gridy = startY;
        constraints.anchor = anchorSide;
        parent.add(playSettingsPanel, constraints);
        constraints.gridy = startY + 5;
        constraints.anchor = anchorSide;
        parent.add(playSavePanel, constraints);
    }

    public void toggleReadyState(boolean state) {
        playMidi.setEnabled(state);
        pauseMidi.setEnabled(state);
        stopMidi.setEnabled(state);
        compose.setEnabled(state);
        midiMode.setEnabled(state);
        midiModeDevices.setEnabled(state);
    }

    public void toggleFgColors(Color uiColor, Color regenerateColor, Color composeColor) {
        messageLabel.setForeground(uiColor);
        compose.setForeground(uiColor);
        compose.setBackground(COMPOSE_COLOR);
        regenerate.setForeground(regenerateColor);
        playMidi.setForeground(uiColor);
        pauseMidi.setForeground(uiColor);
        stopMidi.setForeground(uiColor);
        loopBeatCompose.setForeground(composeColor);
    }

    public VeloRect getGlobalVolSlider() {
        return globalVolSlider;
    }

    public VeloRect getGlobalReverbSlider() {
        return globalReverbSlider;
    }

    public VeloRect getGlobalChorusSlider() {
        return globalChorusSlider;
    }

    public JLabel getMessageLabel() {
        return messageLabel;
    }

    public ScrollComboBox<String> getPresetLoadBox() {
        return presetLoadBox;
    }

    public JCheckBox getRandomizeScaleModeOnCompose() {
        return randomizeScaleModeOnCompose;
    }

    public JButton getRegeneratePausePlayButton() {
        return regeneratePausePlay;
    }

    public JList<File> getGeneratedMidi() {
        return generatedMidi;
    }

    public CheckButton getMidiMode() {
        return midiMode;
    }

    public ScrollComboBox<String> getMidiModeDevices() {
        return midiModeDevices;
    }

    public JTextField getSaveCustomFilename() {
        return saveCustomFilename;
    }

    public JLabel getSavedIndicatorLabel() {
        return savedIndicatorLabel;
    }

    public Color getSavedIndicatorForegroundColor(int index) {
        return savedIndicatorForegroundColors[index];
    }

    public ScrollComboBox<String> getLoopBeatCompose() {
        return loopBeatCompose;
    }

    private void openFolder(String folderPath) {
        File f = new File(folderPath);
        if (!f.exists()) {
            f.mkdirs();
        }
        Desktop desktop = Desktop.getDesktop();
        try {
            desktop.open(f);
        } catch (IOException e) {
            LG.e(e);
        }
    }

    private void reloadPresetBox(ScrollComboBox<String> presetLoadBox) {
        String currentItem = presetLoadBox.getItemCount() > 0 ? presetLoadBox.getSelectedItem()
                : null;
        presetLoadBox.removeAllItems();
        presetLoadBox.addItem(OMNI.EMPTYCOMBO);
        File folder = new File(Constants.PRESET_FOLDER);
        if (folder.exists()) {
            File[] listOfFiles = folder.listFiles();
            for (File f : listOfFiles) {
                if (f.isFile()) {
                    String fileName = f.getName();
                    int pos = fileName.lastIndexOf(".");
                    if (pos > 0 && pos < (fileName.length() - 1)) {
                        fileName = fileName.substring(0, pos);
                    }

                    presetLoadBox.addItem(fileName);
                    if (fileName.equalsIgnoreCase("default")) {
                        presetLoadBox.setVal(fileName);
                    }
                }
            }
        }

        if (currentItem != null) {
            presetLoadBox.setValRaw(currentItem);
        }
    }

    private void undefaultPreset() {
        File loadedFile = new File(Constants.PRESET_FOLDER + "/default.xml");
        boolean exists = loadedFile.exists();
        if (exists) {
            SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();

            f.applyPattern("yyMMdd-HH-mm-ss");
            Date date = new Date();
            String fdate = f.format(date);

            File renamedFile = new File(Constants.PRESET_FOLDER + "/default-" + fdate + ".xml");
            loadedFile.renameTo(renamedFile);

            reloadPresetBox(presetLoadBox);
        }

        new TemporaryInfoPopup(exists ? "Undefaulted 'default' preset!" : "Nothing to undefault!",
                2000);
    }
}
