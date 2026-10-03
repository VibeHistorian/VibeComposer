package org.vibehistorian.vibecomposer.gui;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.RandomIntegerListButton;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Enums.KeyChangeType;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.INST;
import org.vibehistorian.vibecomposer.InstUtils;
import org.vibehistorian.vibecomposer.controllers.InstrumentPanelController;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Panels.DetachedKnobPanel;
import org.vibehistorian.vibecomposer.Panels.DrumPanel;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;
import org.vibehistorian.vibecomposer.PlaybackState;
import org.vibehistorian.vibecomposer.SwingUtils;
import org.vibehistorian.vibecomposer.generation.MidiGenerator;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.SoftBevelBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Owns the extra settings window and its non-generation settings controls. */
public class ExtraSettingsGUI {
    public interface Context {
        void initializeInstrumentPools();
        void initHelperPopups(JPanel settingsPanel);
        void markSoundbankRefreshNeeded();
        void repaintMainWindow();
    }

	private static final String SOUNDBANK_FOLDER = ".";

    public static JPanel extraSettingsPanel;
    public static JPanel currentSettingsMenuPanel;
    public static boolean isShowingTextInKnobs = true;
    public static JTextField bannedInsts;
    public static JCheckBox useAllInsts;
    public static JButton reinitInstPools;
    public static ScrollComboBox<String> soundbankFilename;
    public static JLabel pauseBehaviorLabel;
    public static ScrollComboBox<String> pauseBehaviorCombobox;
    public static JCheckBox startFromBar;
    public static JCheckBox rememberLastPos;
    public static JCheckBox snapStartToBeat;
    public static JCheckBox moveStartToCustomizedSection;
    public static KnobPanel humanizeNotes;
    public static KnobPanel globalNoteLengthMultiplier;
    public static ScrollComboBox<Double> swingUnitMultiplier;
    public static JCheckBox transposeNotePreview;
    public static JCheckBox padGeneratedMidi;
    public static RandomIntegerListButton padGeneratedMidiValues;
    public static JCheckBox randomizeTimingsOnCompose;
    public static JCheckBox sidechainPatternsOnCompose;
    public static KnobPanel bpmLow;
    public static KnobPanel bpmHigh;
    public static KnobPanel stretchMidi;
    public static JCheckBox useMidiCC;
    public static JCheckBox displayVeloRectValues;
    public static JCheckBox knobControlByDragging;
    public static JCheckBox highlightPatterns;
    public static JCheckBox customFilenameAddTimestamp;
    public static JCheckBox customMidiForceScale;
    public static JCheckBox reuseMidiChannelAfterCopy;
    public static JCheckBox transposedNotesForceScale;
    public static JCheckBox orderedTransposeGeneration;
    public static JCheckBox configHistoryStoreRegeneratedTracks;
    public static JCheckBox patternApplyPausesWhenGenerating;
    public static JCheckBox allowValuesOutOfRange;
    public static ScrollComboBox<String> keyChangeTypeSelection;

    public static MidiGenerator.OutputOptions getMidiOutputOptions() {
        return new MidiGenerator.OutputOptions(padGeneratedMidi.isSelected(),
                padGeneratedMidiValues.getValues());
    }

    public static void saveToConfig(GUIConfig gc) {
        gc.setSoundbankName((String) soundbankFilename.getEditor().getItem());
        gc.setSwingUnitMultiplierIndex(swingUnitMultiplier.getSelectedIndex());
        gc.setCustomMidiForceScale(customMidiForceScale.isSelected());
        gc.setTransposedNotesForceScale(transposedNotesForceScale.isSelected());
        gc.setHumanizeNotes(humanizeNotes.getInt());
        gc.setKeyChangeType(KeyChangeType.valueOf(keyChangeTypeSelection.getVal()));
    }

    public static void loadFromConfig(GUIConfig gc) {
        soundbankFilename.getEditor().setItem(gc.getSoundbankName());
        swingUnitMultiplier.setSelectedIndex(gc.getSwingUnitMultiplierIndex());
        customMidiForceScale.setSelected(gc.isCustomMidiForceScale());
        transposedNotesForceScale.setSelected(gc.isTransposedNotesForceScale());
        humanizeNotes.setInt(gc.getHumanizeNotes());
        keyChangeTypeSelection.setVal(gc.getKeyChangeType().toString());
    }

    private final Context context;
	private final DrumGUI drumGUI;
	private final ChordGUI chordGUI;
	private final ArpGUI arpGUI;
    private final MelodyGUI melodyGUI;
    private final InstrumentPanelController panelController;
	private final ScoreGUI scoreGUI;
	private final ArrangementGUI arrangementGUI;

	public ExtraSettingsGUI(Context context, DrumGUI drumGUI, ChordGUI chordGUI,
			ArpGUI arpGUI, MelodyGUI melodyGUI,
			InstrumentPanelController panelController, ScoreGUI scoreGUI,
			ArrangementGUI arrangementGUI) {
		this.context = context;
		this.drumGUI = drumGUI;
		this.chordGUI = chordGUI;
        this.arpGUI = arpGUI;
        this.melodyGUI = melodyGUI;
        this.panelController = panelController;
        this.scoreGUI = scoreGUI;
        this.arrangementGUI = arrangementGUI;
    }

    public void initExtraSettings() {
		extraSettingsPanel = new JPanel();
		extraSettingsPanel.setLayout(new BorderLayout());

		JPanel composeSettingsPanel = new JPanel();
		JPanel scoreMidiPanel = new JPanel();
		JPanel panelGenerationSettingsPanel = new JPanel();
		JPanel chordChoicePanel = new JPanel();
		JPanel melodyTweaksPanel = new JPanel();
		melodyTweaksPanel.setLayout(new BorderLayout());
		JPanel humanizationPanel = new JPanel();
		JPanel instrumentsSettingsPanel = new JPanel();
		JPanel pauseBehaviorPanel = new JPanel();
		JPanel bpmLowHighPanel = new JPanel();
		JPanel displayStylePanel = new JPanel();

		HashMap<String, JPanel> settingsMenuItems = new LinkedHashMap<>();
		settingsMenuItems.put("COMPOSE", composeSettingsPanel);
		settingsMenuItems.put("Score/Midi", scoreMidiPanel);
		settingsMenuItems.put("Generation", panelGenerationSettingsPanel);
		settingsMenuItems.put("Melody", melodyTweaksPanel);
		settingsMenuItems.put("Chords", chordChoicePanel);
		settingsMenuItems.put("Humanization", humanizationPanel);
		settingsMenuItems.put("Instruments", instrumentsSettingsPanel);

		settingsMenuItems.put("Pause Behavior", pauseBehaviorPanel);
		settingsMenuItems.put("BPM", bpmLowHighPanel);
		settingsMenuItems.put("Display", displayStylePanel);

		JPanel sidePanel = new JPanel();
		sidePanel.setLayout(new GridLayout(0, 1, 10, 10));
		JPanel viewPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		viewPanel.setBorder(new SoftBevelBorder(BevelBorder.LOWERED));
		viewPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		JPanel titlePanel = new JPanel();
		titlePanel.setBorder(new SoftBevelBorder(BevelBorder.RAISED));
		JLabel settingsMenuTitle = new JLabel();
		titlePanel.add(settingsMenuTitle);

		extraSettingsPanel.add(titlePanel, BorderLayout.NORTH);
		extraSettingsPanel.add(sidePanel, BorderLayout.WEST);
		extraSettingsPanel.add(viewPanel, BorderLayout.CENTER);
		extraSettingsPanel.setPreferredSize(new Dimension(800, 500));

		// default on first open
		currentSettingsMenuPanel = composeSettingsPanel;
		viewPanel.add(currentSettingsMenuPanel);
		settingsMenuTitle.setText("COMPOSE");

		for (Map.Entry<String, JPanel> entry : settingsMenuItems.entrySet()) {
			String buttonName = entry.getKey();
			JPanel menuPanel = entry.getValue();
			menuPanel.setLayout(new GridLayout(0, 1, 20, 20));
			JButton butt = SwingUtils.makeButton(buttonName, e -> {
				if (currentSettingsMenuPanel != null) {
					//viewPanel.remove(currentSettingsMenuPanel);
					currentSettingsMenuPanel.setVisible(false);
				}
				currentSettingsMenuPanel = menuPanel;
				currentSettingsMenuPanel.setVisible(true);
				viewPanel.add(currentSettingsMenuPanel);
				settingsMenuTitle.setText(buttonName);

				SwingUtilities.updateComponentTreeUI(extraSettingsPanel);
			});

			sidePanel.add(butt);
		}

		initExtraSettingsCompose(composeSettingsPanel);
		initExtraSettingsHumanize(humanizationPanel);
		initExtraSettingsScore(scoreMidiPanel);
		initExtraSettingsInstruments(instrumentsSettingsPanel);
		initExtraSettingsPause(pauseBehaviorPanel);
		chordGUI.initExtraSettingsChords(chordChoicePanel);
		melodyGUI.initExtraSettingsMelody(melodyTweaksPanel);
		initExtraSettingsBpm(bpmLowHighPanel);
		initExtraSettingsDisplay(displayStylePanel);
		initGenerationSettings(panelGenerationSettingsPanel);

		context.initHelperPopups(extraSettingsPanel);
	}

    public void initExtraSettingsScore(JPanel scoreMidiPanel) {
		// SCORE
		JPanel padMidiPanel = new JPanel();
		padMidiPanel.setLayout(new GridLayout(0, 2, 10, 30));
		padGeneratedMidi = new CustomCheckBox("Pad Generated .mid File (# of tracks):", true);
		padGeneratedMidiValues = new RandomIntegerListButton("3,2,5,5,6", null);
		padGeneratedMidiValues.min = 1;
		padGeneratedMidiValues.max = 12;
		padGeneratedMidiValues.editableCount = false;
		padMidiPanel.add(padGeneratedMidi);
		padMidiPanel.add(padGeneratedMidiValues);

		//                stretch
		stretchMidi = new DetachedKnobPanel("Stretch MIDI%:", 100, 25, 400);
		stretchMidi.getKnob().setTickSpacing(25);
		stretchMidi.getKnob().setTickThresholds(
				Arrays.asList(new Integer[] { 25, 50, 100, 150, 200, 300, 400 }));

		//                  arrangement midi settings
		useMidiCC = new CustomCheckBox("Use Volume/Pan/Reverb/Chorus/Filter/.. MIDI CC", true);
		useMidiCC.setToolTipText("Volume - 7, Reverb - 91, Chorus - 93, Filter - 74");

		//                 drum mapping
		JPanel drumMappingPanel = new JPanel();
		drumMappingPanel.setLayout(new GridLayout(0, 2, 10, 30));
		drumGUI.drumCustomMapping = new CustomCheckBox("Custom Drum Mapping", true);
		drumGUI.drumCustomMapping.setToolTipText(
				"<html>" + StringUtils.join(InstUtils.DRUM_INST_NAMES_SEMI, "|") + "</html>");
		drumGUI.drumCustomMappingNumbers = new JTextField(
				StringUtils.join(InstUtils.DRUM_INST_NUMBERS_SEMI, ","));
		drumMappingPanel.add(drumGUI.drumCustomMapping);
		drumMappingPanel.add(drumGUI.drumCustomMappingNumbers);

		scoreMidiPanel.add(padMidiPanel);
		scoreMidiPanel.add(drumMappingPanel);
		scoreMidiPanel.add(useMidiCC);
		scoreMidiPanel.add(stretchMidi);
		scoreMidiPanel.add(arrangementGUI.getArrangementScaleMidiVelocity());
	}

    public void initExtraSettingsInstruments(JPanel instrumentsSettingsPanel) {
		// INSTRUMENTS
		JPanel allInstsPanel = new JPanel();

		allInstsPanel.setLayout(new GridLayout(0, 3, 10, 30));
		useAllInsts = new CustomCheckBox("(Experimental) Use all Inst., except:", false);
		useAllInsts.setHorizontalTextPosition(SwingConstants.RIGHT);
		allInstsPanel.add(useAllInsts);
		bannedInsts = new JTextField("", 8);
		allInstsPanel.add(bannedInsts);
		reinitInstPools = SwingUtils.makeButton("Initialize All Inst.",
				e -> context.initializeInstrumentPools());
		allInstsPanel.add(reinitInstPools);


		transposeNotePreview = new CustomCheckBox("Transpose Note Previews in MIDI Editor", true);

		// 				soundbank
		soundbankFilename = new ScrollComboBox<String>(false);
		soundbankFilename.setEditable(true);
		soundbankFilename.addItem(OMNI.EMPTYCOMBO);
		File folder = new File(SOUNDBANK_FOLDER);
		if (folder.exists()) {
			File[] listOfFiles = folder.listFiles();
			if (listOfFiles != null) {
				for (File f : listOfFiles) {
					if (f.isFile()) {
						String fileName = f.getName();
						if (fileName.endsWith(".sf2")) {
							soundbankFilename.addItem(fileName);
						}
					}
				}
			}
		}
		soundbankFilename.setVal(soundbankFilename.getLastVal());
		soundbankFilename.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				context.markSoundbankRefreshNeeded();
			}
		});

		JPanel soundbankPanel = new JPanel();
		soundbankPanel.setLayout(new GridLayout(0, 2, 10, 30));
		JLabel soundbankLabel = new JLabel("Soundbank name:");
		soundbankPanel.add(soundbankLabel);
		soundbankPanel.add(soundbankFilename);

		instrumentsSettingsPanel.add(allInstsPanel);
		instrumentsSettingsPanel.add(soundbankPanel);
		instrumentsSettingsPanel.add(transposeNotePreview);
	}

    public void initExtraSettingsPause(JPanel pauseBehaviorPanel) {
		// PAUSE
		JPanel startFromPausePanel = new JPanel();
		startFromPausePanel.setLayout(new GridLayout(0, 2, 10, 30));
		pauseBehaviorLabel = new JLabel("Start From Pause:");
		pauseBehaviorCombobox = new ScrollComboBox<>(false);
		startFromBar = new CustomCheckBox("Start From Bar", true);
		rememberLastPos = new CustomCheckBox("Remember Last Pos.", true);
		moveStartToCustomizedSection = new CustomCheckBox("Move Start For Customized Section",
				true);
		snapStartToBeat = new CustomCheckBox("Snap Start To Beat", true);
		snapStartToBeat.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				PlaybackState.slider.setSnapToTicks(snapStartToBeat.isSelected());
			}

		});
		ScrollComboBox.addAll(new String[] { "On regenerate", "On compose/regenerate", "Never" },
				pauseBehaviorCombobox);
		startFromPausePanel.add(pauseBehaviorLabel);
		startFromPausePanel.add(pauseBehaviorCombobox);
		pauseBehaviorPanel.add(startFromPausePanel);
		pauseBehaviorPanel.add(startFromBar);
		pauseBehaviorPanel.add(rememberLastPos);
		pauseBehaviorPanel.add(snapStartToBeat);
		pauseBehaviorPanel.add(moveStartToCustomizedSection);
	}

    public void initExtraSettingsBpm(JPanel bpmLowHighPanel) {
		// BPM
		bpmLow = new DetachedKnobPanel("Min<br>BPM.", 60, 20, 249);
		bpmHigh = new DetachedKnobPanel("Max<br>BPM.", 100, 21, 250);
		bpmLowHighPanel.add(bpmLow);
		bpmLowHighPanel.add(bpmHigh);
		bpmLowHighPanel.add(arpGUI.arpAffectsBpm);
	}

    public void initExtraSettingsDisplay(JPanel displayStylePanel) {
		// DISPLAY
		displayVeloRectValues = new CustomCheckBox("Display Bar Values", true);
		knobControlByDragging = new CustomCheckBox("Knob Up-Down Control", false);
		highlightPatterns = new CustomCheckBox("Highlight Sequencer Pattern (-Perf)", true);
		customFilenameAddTimestamp = new CustomCheckBox("Add Timestamp To Custom Filenames", false);
		displayVeloRectValues.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				context.repaintMainWindow();
			}
		});

		JCheckBox checkbutt = new CustomCheckBox("Show Knob Texts", isShowingTextInKnobs);
		checkbutt.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				isShowingTextInKnobs = !isShowingTextInKnobs;
				for (INST instrument : INST.values()) {
					panelController.getInstList(instrument)
							.forEach(ipanel -> ipanel.toggleComponentTexts(isShowingTextInKnobs));
					if (arrangementGUI.getSelectedSectionIndex() > 0) {
						panelController.getAffectedPanels(instrument).forEach(
								ipanel -> ipanel.toggleComponentTexts(isShowingTextInKnobs));
					}

				}
			}

		});


		drumGUI.bottomUpReverseDrumPanels = new CustomCheckBox("Bottom-Top Drum Display", false);
		drumGUI.bottomUpReverseDrumPanels.addChangeListener(e -> {
            for (DrumPanel dp : drumGUI.getPanels()) {
                dp.setVisible(false);
                ((JPanel) drumGUI.getPanelScrollPane().getViewport().getView()).remove(dp);

            }
            List<DrumPanel> sortedDps = new ArrayList<>(drumGUI.getPanels());
            sortedDps.sort(Comparator.comparing(InstPanel::getPanelOrder));
            for (DrumPanel dp : sortedDps) {
            if (!drumGUI.bottomUpReverseDrumPanels.isSelected()) {
                    ((JPanel) drumGUI.getPanelScrollPane().getViewport().getView()).add(dp);
                } else {
                    ((JPanel) drumGUI.getPanelScrollPane().getViewport().getView()).add(dp, 0);
                }
                dp.setVisible(true);
            }
        });

		displayStylePanel.add(drumGUI.bottomUpReverseDrumPanels);
		displayStylePanel.add(checkbutt);
		displayStylePanel.add(displayVeloRectValues);
		displayStylePanel.add(knobControlByDragging);
		displayStylePanel.add(highlightPatterns);
		displayStylePanel.add(customFilenameAddTimestamp);
		scoreGUI.initDisplaySettings(displayStylePanel);
	}

    public void initExtraSettingsHumanize(JPanel humanizationPanel) {
		// HUMANIZATION
		humanizeNotes = new DetachedKnobPanel("Humanize Notes<br>/10000", 150, 0, 1000);
		drumGUI.humanizeDrums = new DetachedKnobPanel("Humanize Drums<br>/10000", 20, 0, 100);
		globalNoteLengthMultiplier = new DetachedKnobPanel("Note Length Multiplier<br>/1000", 950,
				250, 1000);

		JPanel swingMultiPanel = new JPanel();
		swingMultiPanel.setLayout(new GridLayout(0, 2, 10, 30));
		swingUnitMultiplier = new ScrollComboBox<Double>(false);
		ScrollComboBox.addAll(new Double[] { 0.5, 1.0, 2.0 }, swingUnitMultiplier);
		swingUnitMultiplier.setSelectedIndex(0);

		humanizationPanel.add(humanizeNotes);
		humanizationPanel.add(drumGUI.humanizeDrums);
		humanizationPanel.add(globalNoteLengthMultiplier);
		swingMultiPanel.add(new JLabel("Swing Period Multiplier"));
		swingMultiPanel.add(swingUnitMultiplier);
		humanizationPanel.add(swingMultiPanel);
	}

    public void initExtraSettingsCompose(JPanel composeSettingsPanel) {
		// COMPOSE
		randomizeTimingsOnCompose = SwingUtils.makeCheckBox(
				"<html>Randomize Global Swing/Beat Multiplier<br>on Compose</html>", true, true);
		sidechainPatternsOnCompose = SwingUtils.makeCheckBox("<html>Sidechain Patterns<br>on Compose</html>",
				true, true);
		JCheckBox copyChordsAfterGenerate = chordGUI.createExtraComposeControl();

		composeSettingsPanel.add(arrangementGUI.getArrangementResetCustomPanelsOnCompose());
		composeSettingsPanel.add(randomizeTimingsOnCompose);
		composeSettingsPanel.add(sidechainPatternsOnCompose);
		composeSettingsPanel.add(copyChordsAfterGenerate);
	}

	public void initGenerationSettings(JPanel generationSettingsPanel) {
		// GENERATION
		customMidiForceScale = new CustomCheckBox("Force MIDI Melody Notes To Scale", false);
		reuseMidiChannelAfterCopy = new CustomCheckBox("Reuse MIDI Ch. After Copy (Cc)", true);
		transposedNotesForceScale = new CustomCheckBox("Force Transposed Notes To Scale", false);
		orderedTransposeGeneration = new CustomCheckBox("Ordered Transpose Generation", false);
		configHistoryStoreRegeneratedTracks = new CustomCheckBox(
				"Track History - Include Regenerated Tracks", true);
		patternApplyPausesWhenGenerating = new CustomCheckBox("Apply Pause% on Generate", true);
		allowValuesOutOfRange = new CustomCheckBox("(Experimental!) Allow Knob Values Out of Range", false);

		JPanel keyChangePanel = new JPanel();
		keyChangePanel.setLayout(new GridLayout(0, 2, 10, 30));
		keyChangeTypeSelection = new ScrollComboBox<String>(false);
		ScrollComboBox.addAll(new String[] { "PIVOT", "TWOFIVEONE", "DIRECT" }, keyChangeTypeSelection);
		keyChangeTypeSelection.setVal("TWOFIVEONE");
		keyChangeTypeSelection.setPreferredSize(new Dimension(250, 30));
		keyChangePanel.add(new JLabel("<html>Key Change<br>Type:</html>"));
		keyChangePanel.add(keyChangeTypeSelection);

		generationSettingsPanel.add(customMidiForceScale);
		generationSettingsPanel.add(transposedNotesForceScale);
		generationSettingsPanel.add(reuseMidiChannelAfterCopy);
		generationSettingsPanel.add(orderedTransposeGeneration);
		generationSettingsPanel.add(configHistoryStoreRegeneratedTracks);
		// Melody pattern flips are also available per-instrument.
		generationSettingsPanel.add(patternApplyPausesWhenGenerating);
		generationSettingsPanel.add(allowValuesOutOfRange);
		generationSettingsPanel.add(keyChangePanel);
	}
}
