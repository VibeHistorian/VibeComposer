package org.vibehistorian.vibecomposer;

import org.apache.commons.io.FileUtils;
import org.vibehistorian.vibecomposer.Popups.TemporaryInfoPopup;
import org.vibehistorian.vibecomposer.gui.ScoreGUI;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Owns preset presentation, view snapshots, and GUI config/preset XML I/O. */
public final class PresetViewController {

	private final MainWindowControls mainWindowControls;
	private final ScoreGUI scoreGUI;
	private final ChordGUI chordGUI;
	private final DrumGUI drumGUI;
	private final ArpGUI arpGUI;
	private final MelodyGUI melodyGUI;
	private final GenerationGUI generationGUI;
	private final ArrangementGUI arrangementGUI;

	public PresetViewController(MainWindowControls mainWindowControls, ScoreGUI scoreGUI,
			ChordGUI chordGUI, DrumGUI drumGUI, ArpGUI arpGUI, MelodyGUI melodyGUI,
			GenerationGUI generationGUI, ArrangementGUI arrangementGUI) {
		this.mainWindowControls = mainWindowControls;
		this.scoreGUI = scoreGUI;
		this.chordGUI = chordGUI;
		this.drumGUI = drumGUI;
		this.arpGUI = arpGUI;
		this.melodyGUI = melodyGUI;
		this.generationGUI = generationGUI;
		this.arrangementGUI = arrangementGUI;
	}

	public GUIPreset loadPreset() {
		String presetName = getPresetName();
		LG.i("Trying to load preset: " + presetName);

		if (OMNI.EMPTYCOMBO.equalsIgnoreCase(presetName)) {
			return null;
		}

		File loadedFile = new File(Constants.PRESET_FOLDER + "/" + presetName + ".xml");
		if (loadedFile.exists()) {
			try {
				GUIPreset preset = unmarshallPreset(loadedFile);
				LG.i("Loaded preset: " + presetName);
				return preset;
			} catch (JAXBException | IOException e) {
				LG.e("Could not load preset!", e);
				new TemporaryInfoPopup("Preset loading failed! " + Constants.BUG_HUNT_MESSAGE, 2000);
				return null;
			}
		}

		LG.i("Loaded preset: " + presetName);
		return null;
	}

	public void savePreset(GUIConfig config) {
		String presetName = getPresetName();
		LG.i("Trying to save preset: " + presetName);
		if (!presetName.matches(Constants.FILENAME_VALID_NAME)) {
			new TemporaryInfoPopup("Name contains invalid characters: "
					+ presetName.replaceAll(Constants.FILENAME_VALID_CHARACTERS, ""), 2500);
			return;
		}

		presetName = presetName.replace(" ", "_");
		new File(Constants.PRESET_FOLDER).mkdir();
		String filePath = Constants.PRESET_FOLDER + "/" + presetName + ".xml";
		savePresetFile(filePath, config);
		mainWindowControls.getPresetLoadBox().addItem(presetName);
		new TemporaryInfoPopup("Saved preset: " + presetName, 2000);
	}

	public GUIConfig loadConfigFile(Frame parentWindow) {
		FileDialog dialog = new FileDialog(parentWindow, "Choose a file", FileDialog.LOAD);
		dialog.setDirectory(null);
		dialog.setFile("*.xml");
		dialog.setVisible(true);
		String filename = dialog.getFile();
		File[] files = dialog.getFiles();
		if (filename == null) {
			LG.i("You cancelled the choice");
			return null;
		}

		LG.i("You chose " + filename);
		try {
			return unmarshallConfig(files[0]);
		} catch (JAXBException | IOException e) {
			LG.e("Can't load config: " + filename, e);
			return null;
		}
	}

	public void saveGuiConfigFile(int rating, GUIConfig config, File currentMidiFile, String newFileName) {
		if (currentMidiFile != null) {
			LG.i("Saving file: " + (rating >= 0
					? newFileName : mainWindowControls.getSaveCustomFilename().getText()));

			String finalFilePath = createGuiConfigFilePath(rating, newFileName);
			LG.i("Saving to final path: " + finalFilePath);
			try {
				FileUtils.copyFile(currentMidiFile, new File(finalFilePath));
				marshalConfig(config, finalFilePath, Constants.MID_EXTENSION.length());
				if (rating >= 3) {
					mainWindowControls.getSavedIndicatorLabel().setForeground(
							mainWindowControls.getSavedIndicatorForegroundColor(rating - 3));
				} else {
					mainWindowControls.getSavedIndicatorLabel().setForeground(
							mainWindowControls.getSavedIndicatorForegroundColor(3));
				}
				mainWindowControls.getSavedIndicatorLabel().setVisible(true);
			} catch (IOException | JAXBException e) {
				LG.e("Error saving file: ", e);
			}
		} else {
			LG.i("currentMidiFile is NULL!");
			LG.w("Cannot save config file without a successful compose/regenerate first!");
			new TemporaryInfoPopup(
					"Cannot save config file without a successful compose/regenerate first!", 1500);
		}
	}

	private String createGuiConfigFilePath(int rating, String newFileName) {
		Date date = new Date();
		String saveDirectory = Constants.SAVED_MIDIS_FOLDER_BASE;
		String name;
		SimpleDateFormat dateFormat = (SimpleDateFormat) SimpleDateFormat.getInstance();
		dateFormat.applyPattern("yyMMdd-HH-mm-ss");
		String additionalInfo = "";

		if (rating >= 0) {
			saveDirectory += rating + "star/";
			new File(Constants.MIDIS_FOLDER + saveDirectory).mkdir();
			name = newFileName.substring(0, newFileName.length() - 4);
			additionalInfo = dateFormat.format(date);
		} else {
			saveDirectory += "custom/";
			name = mainWindowControls.getSaveCustomFilename().getText();
			if (ExtraSettingsGUI.customFilenameAddTimestamp.isSelected()) {
				additionalInfo = dateFormat.format(date);
			}
		}

        return Constants.MIDIS_FOLDER + saveDirectory + additionalInfo
                + (additionalInfo.isEmpty() ? "" : "_") + name + Constants.MID_EXTENSION;
	}

	public GUIPreset copyCurrentViewToPreset(GUIPreset preset, GUIConfig config) {
		preset.setPatternMaps(config.getPatternMaps());

		List<Component> presetComponents = makeSettableComponentList();
		List<Integer> presetComponentValues = new ArrayList<>();
		for (Component component : presetComponents) {
			presetComponentValues.add(UIComponentState.getValue(component));
		}
		preset.setOrderedValuesUI(presetComponentValues);
		preset.setDarkMode(UITheme.isDarkMode);
		preset.setFullMode(UITheme.isFullMode);
		preset.setBigMode(UITheme.isBigMonitorMode);
		return preset;
	}

	public void restoreViewValues(GUIPreset preset) {
		List<Component> presetComponents = makeSettableComponentList();
		for (int i = 0; i < preset.getOrderedValuesUI().size(); i++) {
			UIComponentState.setValue(presetComponents.get(i), preset.getOrderedValuesUI().get(i), false);
		}
	}

	public void marshalConfig(GUIConfig config, String path, int cutOff)
			throws JAXBException, IOException {
		JAXBContext context = JAXBContext.newInstance(GUIConfig.class);
		Marshaller marshaller = context.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "");
		String actualPath = path.substring(0, path.length() - cutOff);
		marshaller.marshal(config, new File(actualPath + "_VCConfig.xml"));
		LG.i("File saved: " + path);
	}

	public GUIConfig unmarshallConfig(File file) throws JAXBException, IOException {
		JAXBContext context = JAXBContext.newInstance(GUIConfig.class);
		try (FileReader reader = new FileReader(file)) {
			return (GUIConfig) context.createUnmarshaller().unmarshal(reader);
		}
	}

	private void savePresetFile(String filePath, GUIConfig config) {
		try {
			GUIPreset preset = new GUIPreset();
			marshalPreset(copyCurrentViewToPreset(preset, config), filePath);
		} catch (IOException | JAXBException e) {
			LG.e(e);
		}
	}

	private void marshalPreset(GUIPreset preset, String path) throws JAXBException, IOException {
		JAXBContext context = JAXBContext.newInstance(GUIPreset.class);
		Marshaller marshaller = context.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		marshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "");
		marshaller.marshal(preset, new File(path));
		LG.i("File saved: " + path);
	}

	private GUIPreset unmarshallPreset(File file) throws JAXBException, IOException {
		JAXBContext context = JAXBContext.newInstance(GUIPreset.class);
		try (FileReader reader = new FileReader(file)) {
			return (GUIPreset) context.createUnmarshaller().unmarshal(reader);
		}
	}

	private String getPresetName() {
		return (String) mainWindowControls.getPresetLoadBox().getEditor().getItem();
	}

	private List<Component> makeSettableComponentList() {
		List<Component> components = new ArrayList<>();
		// melody panel
		components.add(melodyGUI.generateMelodiesOnCompose);
		components.add(null);
		components.add(melodyGUI.combineMelodyTracks);
		components.add(melodyGUI.randomMelodySameSeed);
		components.add(melodyGUI.randomMelodyOnRegenerate);
		components.add(melodyGUI.useUserMelody);
		components.add(melodyGUI.melodyPatternRandomizeOnCompose);
		components.add(melodyGUI.melodyTargetNotesRandomizeOnCompose);

		// chord panel
		components.add(chordGUI.randomChordsGenerateOnCompose);
		components.add(chordGUI.randomChordStruminess);
		components.add(chordGUI.randomChordUseChordFill);
		components.add(chordGUI.randomChordStretchType);
		components.add(chordGUI.randomChordStretchPicker);
		components.add(chordGUI.randomChordStretchGenerationChance);
		components.add(chordGUI.randomChordMaxStrumPauseChance);
		components.add(chordGUI.randomChordVaryLength);
		components.add(chordGUI.randomChordExpandChance);
		components.add(chordGUI.randomChordSustainChance);
		components.add(chordGUI.randomChordMaxSplitChance);
		components.add(chordGUI.chordSlashChance);
		components.add(chordGUI.randomChordMinVel);
		components.add(chordGUI.randomChordMaxVel);
		components.add(chordGUI.randomChordPattern);
		components.add(chordGUI.randomChordShiftChance);

		// arp panel
		components.add(arpGUI.randomArpsGenerateOnCompose);
		components.add(arpGUI.randomArpHitsPicker);
		components.add(arpGUI.randomArpHitsPerPattern);
		components.add(arpGUI.randomArpAllSameHits);
		components.add(arpGUI.randomArpUseChordFill);
		components.add(arpGUI.randomArpTranspose);
		components.add(arpGUI.randomArpStretchType);
		components.add(arpGUI.randomArpStretchPicker);
		components.add(arpGUI.randomArpStretchGenerationChance);
		components.add(arpGUI.randomArpMaxExceptionChance);
		components.add(arpGUI.arpCopyMelodyInst);
		components.add(arpGUI.randomArpAllSameInst);
		components.add(arpGUI.randomArpLimitPowerOfTwo);
		components.add(null);
		components.add(arpGUI.randomArpMaxRepeat);
		components.add(arpGUI.randomArpMinVel);
		components.add(arpGUI.randomArpMaxVel);
		components.add(arpGUI.randomArpPattern);
		components.add(arpGUI.randomArpShiftChance);
		components.add(arpGUI.randomArpMinLength);
		components.add(arpGUI.randomArpMaxLength);

		// drum panel
		components.add(drumGUI.randomDrumsGenerateOnCompose);
		components.add(drumGUI.randomDrumUseChordFill);
		components.add(drumGUI.randomDrumSlide);
		components.add(drumGUI.combineDrumTracks);
		components.add(drumGUI.randomDrumPattern);
		components.add(drumGUI.randomDrumVelocityPatternChance);
		components.add(drumGUI.randomDrumShiftChance);

		// arrangement and randomization panels
		components.add(arrangementGUI.getRandomizeArrangementOnCompose());
		components.add(generationGUI.randomizeInstOnComposeOrGen);
		components.add(generationGUI.randomizeBpmOnCompose);
		components.add(generationGUI.randomizeTransposeOnCompose);

		// globals
		components.add(mainWindowControls.getRandomizeScaleModeOnCompose());
		components.add(generationGUI.regenerateWhenValuesChange);
		components.add(PlaybackState.loopBeat);
		components.add(generationGUI.loopBeatCount);
		components.add(mainWindowControls.getMidiMode());

		// extra settings
		components.add(ExtraSettingsGUI.useMidiCC);
		components.add(arrangementGUI.getArrangementResetCustomPanelsOnCompose());
		components.add(null);
		components.add(null);
		components.add(mainWindowControls.getLoopBeatCompose());
		components.add(ExtraSettingsGUI.useAllInsts);
		components.add(ExtraSettingsGUI.pauseBehaviorCombobox);
		components.add(ExtraSettingsGUI.startFromBar);
		components.add(ExtraSettingsGUI.rememberLastPos);
		components.add(ExtraSettingsGUI.snapStartToBeat);
		components.add(ExtraSettingsGUI.bpmLow);
		components.add(ExtraSettingsGUI.bpmHigh);
		components.add(ExtraSettingsGUI.stretchMidi);
		components.add(ExtraSettingsGUI.displayVeloRectValues);
		components.add(ExtraSettingsGUI.knobControlByDragging);
		components.add(drumGUI.bottomUpReverseDrumPanels);
		components.add(ExtraSettingsGUI.orderedTransposeGeneration);
		components.add(ExtraSettingsGUI.patternApplyPausesWhenGenerating);
		components.add(ExtraSettingsGUI.highlightPatterns);
		components.add(scoreGUI.getHighlightScoreNotes());
		components.add(ExtraSettingsGUI.randomizeTimingsOnCompose);
		components.add(ExtraSettingsGUI.customFilenameAddTimestamp);
		components.add(ExtraSettingsGUI.configHistoryStoreRegeneratedTracks);
		components.add(ExtraSettingsGUI.sidechainPatternsOnCompose);

		// VibeComposer 2
		components.add(drumGUI.randomDrumHitsMultiplierOnGenerate);
		components.add(null);
		components.add(drumGUI.randomDrumsOverrandomize);
		components.add(ExtraSettingsGUI.globalNoteLengthMultiplier);
		components.add(chordGUI.copyChordsAfterGenerate);
		components.add(scoreGUI.getMiniScorePopup());
		components.add(arpGUI.randomArpCorrectMelodyNotes);
		components.add(ExtraSettingsGUI.reuseMidiChannelAfterCopy);
		components.add(ExtraSettingsGUI.transposeNotePreview);
		components.add(ExtraSettingsGUI.moveStartToCustomizedSection);
		components.add(ExtraSettingsGUI.allowValuesOutOfRange);

		return components;
	}
}
