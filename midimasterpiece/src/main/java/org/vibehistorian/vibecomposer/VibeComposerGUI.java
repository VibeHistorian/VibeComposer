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

import com.formdev.flatlaf.FlatDarculaLaf;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.Components.InstComboBox;
import org.vibehistorian.vibecomposer.Components.InstrumentControlContext;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Helpers.CheckBoxIcon;
import org.vibehistorian.vibecomposer.Helpers.MidiHandler;
import org.vibehistorian.vibecomposer.MidiGenerator.Durations;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.Panels.ChordletPanel;
import org.vibehistorian.vibecomposer.Panels.DrumPanel;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;
import org.vibehistorian.vibecomposer.Panels.PartManagerPanel;
import org.vibehistorian.vibecomposer.Panels.SoloMuter;
import org.vibehistorian.vibecomposer.Panels.SoloMuter.State;
import org.vibehistorian.vibecomposer.Popups.AboutPopup;
import org.vibehistorian.vibecomposer.Popups.ApplyCustomSectionPopup;
import org.vibehistorian.vibecomposer.Popups.DrumLoopPopup;
import org.vibehistorian.vibecomposer.Popups.ExtraSettingsPopup;
import org.vibehistorian.vibecomposer.Popups.HelpPopup;
import org.vibehistorian.vibecomposer.Popups.MidiEditPopup;
import org.vibehistorian.vibecomposer.Popups.TemporaryInfoPopup;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.xml.bind.JAXBException;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.vibehistorian.vibecomposer.ApplicationSessionState.actionUndoManager;
import static org.vibehistorian.vibecomposer.GUIConstants.DEFAULT_HEIGHT;
import static org.vibehistorian.vibecomposer.GUIConstants.DEFAULT_WIDTH;
import static org.vibehistorian.vibecomposer.PlaybackState.*;
import static org.vibehistorian.vibecomposer.UITheme.*;

// main class
public class VibeComposerGUI extends JFrame
		implements ActionListener, ItemListener, WindowListener, SoloMuter.Context {

	private static final long serialVersionUID = -677536546851756969L;

	private final MidiDeviceController midiDeviceController;
	private MidiAuditionController midiAuditionController;
	private final MidiCcController midiCcController;
	private final MidiExportController midiExportController;
	private final MainWindowControls mainWindowControls;
	private GUIConfig guiConfig = new GUIConfig();
	private PresetViewController presetViewController;
	private ComposeCoordinator composeCoordinator;
	private AppearanceController appearanceController;
	private GUIPreset defaultGuiPreset;
	private boolean heavyBackgroundTasksInProgress;
	private final UndoManager instrumentTabUndoManager = new UndoManager();
	private final AtomicBoolean playheadUpdatePending = new AtomicBoolean();
	private final AtomicBoolean playheadRefreshPending = new AtomicBoolean();
	private final InstrumentControlContext instrumentControlContext = new InstrumentControlContext() {
		@Override public List<InstPanel> getAffectedPanels(INST instrument) {
			return VibeComposerGUI.getAffectedPanels(instrument);
		}
		@Override public boolean canRegenerateOnChange() {
			return generationGUI.canRegenerateOnChange();
		}
		@Override public void regenerate() { VibeComposerGUI.this.regenerate(); }
	};
	private final PlaybackController playbackController;
	private final ConsoleOutputController consoleOutputController;
	private final SoloMuteController soloMuteController =
			new SoloMuteController(new SoloMuteController.Context() {
				@Override public List<? extends InstPanel> getPanels(INST instrument) {
					return VibeComposerGUI.getInstList(instrument);
				}
				@Override public boolean isInstrumentEnabled(INST instrument) {
					return getInstrumentControls(instrument).getEnabledCheckBox().isSelected();
				}
				@Override public SoloMuter getGlobalSoloMuter() {
					return mainWindowControls.getGlobalSoloMuter();
				}
				@Override public List<SoloMuter> getGroupSoloMuters() {
					return mainWindowControls.getGroupSoloMuters();
				}
				@Override public void refreshScoreForSoloChange() {
					if (scoreGUI.isSoloMuterHighlightEnabled()) {
						SwingUtilities.invokeLater(() -> scoreGUI.getScorePanel().setScore());
					}
				}
				@Override public void refreshScoreForMuteChange() {
					if (scoreGUI.isSoloMuterHighlightEnabled()) {
						SwingUtilities.invokeLater(() -> scoreGUI.getScorePanel().update());
					}
				}
			});
	private final InstrumentPanelController instrumentPanelController =
			new InstrumentPanelController(new InstrumentPanelController.Context() {
				@Override public void recalculateArrangementPartMaps() {
					if (arrangementGUI != null) {
						arrangementGUI.refreshPartMapsFromOldData();
					}
				}
				@Override public void recalculateTabPaneCounts() {
					VibeComposerGUI.this.recalculateTabPaneCounts();
				}
				@Override public void recalculateAfterPanelGeneration() {
					VibeComposerGUI.this.recalculateTabPaneCounts();
					VibeComposerGUI.this.recalculateSoloMuters();
				}
				@Override public void recalculateAfterPanelAddition() {
					VibeComposerGUI.this.recalculateGeneratorAndTabCounts();
					VibeComposerGUI.this.recalculateSoloMuters();
				}
				@Override public void recalculateAfterPanelRemoval() {
					VibeComposerGUI.this.recalculateGeneratorAndTabCounts();
				}
				@Override public void repaintInstrumentTabs() { instrumentTabPane.repaint(); }
				@Override public void repaintMainWindow() { VibeComposerGUI.this.repaint(); }
				@Override public void regenerate() { VibeComposerGUI.this.regenerate(); }
				@Override public boolean canRegenerateOnChange() {
					return generationGUI.canRegenerateOnChange();
				}
			}, instrument -> {
				InstPanel panel = getOwnedInstrumentControls(instrument)
						.createPanel(VibeComposerGUI.this);
				configureRandomizeAction(panel);
				configureInstPanelContext(panel);
				configureInstrumentControlContext(panel);
				removeComboBoxArrows(panel);
				return panel;
			}, this::getOwnedInstrumentControls, () -> this.generationGUI.lastRandomSeed);
	private final InstPanel.Context instPanelContext = new InstPanel.Context() {
		@Override public INST getSelectedInstrument() {
			return INST.fromIndex(VibeComposerGUI.instrumentTabPane.getSelectedIndex());
		}
		@Override public int getAbsoluteOrder(INST instrument, int panelOrder) {
			return instrumentPanelController.getAbsoluteOrder(instrument, panelOrder);
		}
		@Override public void recalculateAfterCopy() {
			VibeComposerGUI.this.recalculateTabPaneCounts();
			VibeComposerGUI.this.recalculateGenerationCounts();
			VibeComposerGUI.this.repaint();
		}
		@Override public int getCurrentSeed() { return generationGUI.getCurrentSeed(); }
		@Override public int getLastRandomSeed() { return generationGUI.lastRandomSeed; }
		@Override public List<Section> getArrangementSections() { return getGeneratedArrangementSections(); }
		@Override public GUIConfig getGUIConfig() { return guiConfig; }
	};
	private final MidiEditPopup.Context midiEditPopupContext = new MidiEditPopup.Context() {
		@Override public Component getMainWindowComponent() { return VibeComposerGUI.this; }
		@Override public List<InstPanel> getAffectedPanels(INST instrument) {
			return VibeComposerGUI.getAffectedPanels(instrument);
		}
		@Override public List<? extends InstPanel> getInstList(INST instrument) {
			return VibeComposerGUI.getInstList(instrument);
		}
		@Override public Pair<ScaleMode, Integer> getScaleKey(Section section) {
			return ArrangementGUI.keyChangeAt(
					ArrangementGUI.actualArrangement.getSections().indexOf(section),
					ScaleMode.valueOf(generationGUI.scaleMode.getVal()));
		}
		@Override public int getTranspose() { return scoreGUI.getTranspose(); }
		@Override public List<Integer> getMelodyBlockChoicePreference() {
			return melodyGUI.melodyBlockChoicePreference.getValues();
		}
		@Override public GUIConfig getGUIConfig() { return guiConfig; }
		@Override public MidiGenerator getMelodyGenerator() {
			return midiEditorSession.getMelodyGenerator();
		}
		@Override public void regenerateInPlace() { VibeComposerGUI.this.regenerateInPlace(); }
		@Override public void playNote(int pitch, int durationMs, int velocity, INST part,
				int partOrder, Section section, boolean overrideLastPlayed) {
			midiAuditionController.playNote(pitch, durationMs, velocity, part, partOrder, section,
					overrideLastPlayed);
		}
		@Override public void markArrangementManual() {
			ArrangementGUI.manualArrangement.setSelected(true);
			ArrangementGUI.manualArrangement.repaint();
		}
		@Override public void repaintActualArrangement() {
			ArrangementGUI.scrollableArrangementActualTable.repaint();
		}
		@Override public List<Double> getUserChordDurations() {
			return chordGUI.getUserChordDurations();
		}
	};
	private final MidiEditorSession midiEditorSession = new MidiEditorSession(midiEditPopupContext);

	// instrument panels added into scrollpanes

	public static List<InstPanel> getAffectedPanels(INST instrument) {
		return getInstrumentPanelController().getAffectedPanels(instrument);
	}

	public static List<? extends InstPanel> getInstList(int order) {
		return getInstList(INST.fromIndex(order));
	}

	public static List<? extends InstPanel> getInstList(INST instrument) {
		return getInstrumentPanelController().getInstList(instrument);
	}

	private static InstrumentPanelController getInstrumentPanelController() {
		if (vibeComposerGUI == null) {
			throw new IllegalStateException("The main window has not been initialized.");
		}
		return vibeComposerGUI.instrumentPanelController;
	}

	private static InstGUI<?> getInstrumentControls(INST instrument) {
		if (vibeComposerGUI == null) {
			throw new IllegalStateException("The main window has not been initialized.");
		}
		return vibeComposerGUI.getOwnedInstrumentControls(instrument);
	}

	private InstGUI<?> getOwnedInstrumentControls(INST instrument) {
		switch (instrument) {
		case MELODY: return melodyGUI;
		case BASS: return bassGUI;
		case CHORD: return chordGUI;
		case ARP: return arpGUI;
		case DRUM: return drumGUI;
		default: throw new IllegalArgumentException("Unsupported instrument: " + instrument);
		}
	}

	public static JScrollPane getInstPane(INST instrument) {
		return getInstrumentPanelController().getInstPane(instrument);
	}

	public static List<InstPanel> getSectionPanelList(INST instrument) {
		return getInstrumentPanelController().getSectionPanelList(instrument);
	}

	// Arrangement fields retained during the module migration.

// instrument scrollers
	public static JTabbedPane instrumentTabPane = new JTabbedPane(JTabbedPane.TOP) {
		@Override
		public void setSelectedIndex(int index) {
			if (index >= getComponents().length) {
				index = getComponents().length - 1;
			}
			super.setSelectedIndex(index);
			if (vibeComposerGUI != null) {
				vibeComposerGUI.instrumentTabUndoManager.saveToHistory(instrumentTabPane);
			}
		}
	};

// arrangement subcells - copy dragging

// instrument global settings

	// macro params

// seed / midi

	MidiHandler mh = new MidiHandler(new MidiHandler.Context() {
		@Override public void setBpm(int bpm) { generationGUI.mainBpm.setInt(bpm); }
		@Override public int getInstrumentPartCount(INST instrument) {
			return VibeComposerGUI.getInstList(instrument).size();
		}
		@Override public void playNextNote(int keyboardTranspose, int velocity, INST instrument,
				int partOrder) {
			midiAuditionController.playNextNote(keyboardTranspose, velocity, instrument, partOrder);
		}
		@Override public void playNote(int pitch, int durationMs, int velocity, INST instrument,
				int partOrder) {
			midiAuditionController.playNote(pitch, durationMs, velocity, instrument, partOrder,
					ArrangementGUI.actualArrangement.getSections().get(0), true);
		}
	});

	boolean isKeySeeking = false;

	JPanel everythingPanel;
	JPanel controlPanel;
	JScrollPane everythingPane;

	public static VibeComposerGUI vibeComposerGUI = null;

	static GridBagConstraints constraints = new GridBagConstraints();
	private MelodyGUI melodyGUI;
	private BassGUI bassGUI;
	private ChordGUI chordGUI;
	private ArpGUI arpGUI;
	private DrumGUI drumGUI;
	private ScoreGUI scoreGUI;
	private ExtraSettingsGUI extraSettingsGUI;
	private GenerationGUI generationGUI;
	private ArrangementGUI arrangementGUI;

public static final String CURRENT_VERSION = Constants.APP_VERSION;

	public static void main(String args[]) {
		FlatDarculaLaf.install();
		UIManager.put("CheckBox.icon", new CheckBoxIcon());

		isDarkMode = true;
		vibeComposerGUI = new VibeComposerGUI("VibeComposer" + CURRENT_VERSION + " (BETA)");
		vibeComposerGUI.setMainIcon();
		vibeComposerGUI.init();

		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			public void uncaughtException(Thread t, Throwable e) {
				LG.e("Uncaught EXCEPTION!", e);
				new TemporaryInfoPopup("Unknown error! " + Constants.BUG_HUNT_MESSAGE, 3000);
				if (sequencer != null && sequencer.isRunning()) {
					sequencer.stop();
				}
				loopBeat.setSelected(false);
			}
		});

		//Toolkit.getDefaultToolkit().getSystemEventQueue().push(new TimedEventQueue());
		vibeComposerGUI.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
	}

	public VibeComposerGUI(String title) {
		super(title);
		consoleOutputController = new ConsoleOutputController();
		vibeComposerGUI = this;
		playbackController = new PlaybackController(new PlaybackController.Context() {
			@Override public void startMidiCcThread() { midiCcController.startMidiCcThread(); }
			@Override public boolean startFromBar() { return ExtraSettingsGUI.startFromBar.isSelected(); }
			@Override public int currentBpm() { return generationGUI.mainBpm.getInt(); }
			@Override public boolean hasGeneratedChordData() { return !MidiGenerator.chordInts.isEmpty(); }
		});
		midiDeviceController = new MidiDeviceController(new MidiDeviceController.Context() {
			@Override public boolean isTransmitterMode() {
				return mainWindowControls.getMidiMode().isSelected();
			}
			@Override public String getSelectedDeviceName() {
				return mainWindowControls.getMidiModeDevices().getVal();
			}
			@Override public File getSoundbankFile() {
				return new File((String) ExtraSettingsGUI.soundbankFilename.getEditor().getItem());
			}
			@Override public void stopPlayback() { playbackController.stopMidi(); }
			@Override public void showSequenceReadError() {
				new TemporaryInfoPopup(
						"Cannot create MIDI - VibeComposer is in a folder without write access!\n This can happen in restricted folders, e.g. Program Files.",
						null);
			}
			@Override public void requestSoloMuteRecalculationAfterSequenceGenerated() {
				soloMuteController.requestRecalculationAfterSequenceGenerated();
			}
		});
		midiCcController = new MidiCcController(new MidiCcController.Context() {
			@Override public boolean useMidiCc() { return ExtraSettingsGUI.useMidiCC.isSelected(); }
			@Override public List<? extends InstPanel> getInstrumentPanels(INST instrument) {
				return VibeComposerGUI.getInstList(instrument);
			}
			@Override public int getDrumVolume() { return drumGUI.drumVolumeSlider.getValue(); }
			@Override public int getGlobalVolume() { return mainWindowControls.getGlobalVolSlider().getValue(); }
			@Override public int getGlobalReverb() { return mainWindowControls.getGlobalReverbSlider().getValue(); }
			@Override public int getGlobalChorus() { return mainWindowControls.getGlobalChorusSlider().getValue(); }
			@Override public int getGroupFilter(INST instrument) {
				return getInstrumentControls(instrument).getGroupFilterSlider().getValue();
			}
			@Override public boolean isSequencerRunning() { return sequencer != null && sequencer.isRunning(); }
			@Override public void sendMidiMessage(ShortMessage message) {
				midiDeviceController.sendVolumeMessage(message);
			}
		});
		midiExportController = new MidiExportController(new MidiExportController.Context() {
			@Override public Sequence getSequence() { return sequencer.getSequence(); }
			@Override public double getBpm() { return guiConfig.getBpm(); }
			@Override public boolean isTransmitterMode() {
				return mainWindowControls.getMidiMode().isSelected();
			}
			@Override public void setTransmitterMode(boolean enabled) {
				mainWindowControls.getMidiMode().setSelectedRaw(enabled);
			}
			@Override public Soundbank getSoundbank() { return midiDeviceController.getSoundbank(); }
			@Override public void sendAllMidiCc() { midiCcController.sendAllMidiCc(); }
		});
		mainWindowControls = new MainWindowControls(new MainWindowControls.HeaderContext() {
			@Override public void switchDarkMode() { VibeComposerGUI.this.switchDarkMode(); }
			@Override public void switchFullMode() { VibeComposerGUI.this.switchFullMode(); }
			@Override public void switchBigMonitorMode() { VibeComposerGUI.this.switchBigMonitorMode(); }
			@Override public void toggleExcludeNotSoloed() { soloMuteController.toggleExclude(); }
			@Override public void loadPreset() {
				GUIPreset preset = presetViewController.loadPreset();
				if (preset != null) {
					VibeComposerGUI.this.loadPresetObject(preset);
				}
			}
			@Override public void savePreset() {
				VibeComposerGUI.this.copyGUItoConfig(guiConfig);
				presetViewController.savePreset(guiConfig);
			}
			@Override public void resetAll() { VibeComposerGUI.this.resetAllFromHeader(); }
			@Override public void loadSelectedHistory(GUIConfig selectedConfig) {
				guiConfig = selectedConfig;
				VibeComposerGUI.this.copyConfigToGUI(guiConfig);
			}
			@Override public void replaceSection() { arrangementGUI.replaceSection(); }
			@Override public void recomposeSection() { arrangementGUI.recomposeSection(); }
			@Override public void openExtraSettings() {
				new ExtraSettingsPopup(generationGUI.mainBpm);
			}
		}, new MainWindowControls.ComposeContext() {
			@Override public JButton makeButton(String name, String actionCommand) {
				return VibeComposerGUI.this.makeButton(name, actionCommand);
			}
			@Override public void stopPlayback() { playbackController.stopMidi(); }
			@Override public void performAction(ActionEvent event) {
				VibeComposerGUI.this.actionPerformed(event);
			}
			@Override public void regenerateInPlace() { VibeComposerGUI.this.regenerateInPlace(); }
			@Override public void clearAllSeeds() { VibeComposerGUI.this.clearAllSeeds(); }
		}, new MainWindowControls.PlaybackContext() {
			@Override public JButton makeButton(String name, String actionCommand) {
				return VibeComposerGUI.this.makeButton(name, actionCommand);
			}
			@Override public void stopPlaybackButton() { playbackController.stopMidi(); }
			@Override public void playPlaybackButton() { playbackController.playMidi(false); }
			@Override public void pausePlaybackButton() { playbackController.pauseMidi(); }
			@Override public void saveConfigFile(int rating) {
				VibeComposerGUI.this.copyGUItoConfig(guiConfig);
				String currentMidiFileName = currentMidi != null ? currentMidi.getName() : "";
				presetViewController.saveGuiConfigFile(rating, guiConfig, currentMidi,
						VibeComposerGUI.this.getFilenameForSaving(currentMidiFileName));
			}
			@Override public void saveWavFile() { VibeComposerGUI.this.saveWavFile(); }
			@Override public File getCurrentMidi() { return currentMidi; }
			@Override public boolean hasMidiDevice() { return midiDeviceController.hasMidiDevice(); }
			@Override public void closeMidiDevice() { midiDeviceController.closeMidiDevice(); }
			@Override public void softCloseSynth() { midiDeviceController.softCloseSynth(); }
		});
	}

	private void initExtraSettingsGUI() {
		extraSettingsGUI = new ExtraSettingsGUI(new ExtraSettingsGUI.Context() {
			@Override public void initializeInstrumentPools() { VibeComposerGUI.this.initializeInstrumentPools(); }
			@Override public void initHelperPopups(JPanel settingsPanel) { VibeComposerGUI.this.initHelperPopups(settingsPanel); }
			@Override public void markSoundbankRefreshNeeded() {
				midiDeviceController.markSoundbankRefreshNeeded();
			}
			@Override public void repaintMainWindow() { VibeComposerGUI.this.repaint(); }
		}, drumGUI, chordGUI, arpGUI, melodyGUI, instrumentPanelController, scoreGUI);
		extraSettingsGUI.initExtraSettings();
	}

	private void initGenerationGUI() {
		generationGUI = new GenerationGUI(new GenerationGUI.Context() {
			@Override public JButton makeButton(String name, String actionCommand) {
				return VibeComposerGUI.this.makeButton(name, actionCommand);
			}
			@Override public void switchAllOnComposeCheckboxes(boolean state) {
				VibeComposerGUI.this.switchAllOnComposeCheckboxes(state);
			}
			@Override public int getSelectedInstrumentTab() { return instrumentTabPane.getSelectedIndex(); }
			@Override public void regenerate() { VibeComposerGUI.this.regenerate(); }
			@Override public boolean isHeavyBackgroundTaskInProgress() {
				return heavyBackgroundTasksInProgress;
			}
		}, instrumentPanelController, arpGUI, melodyGUI, chordGUI);
	}

	private void initArrangementGUI() {
		arrangementGUI = new ArrangementGUI(new ArrangementGUI.Context() {
			@Override public JButton makeButton(String name, String actionCommand, int width,
					int height) {
				return VibeComposerGUI.this.makeButton(name, actionCommand, width, height);
			}
			@Override public void recalculateTabPaneCounts() {
				VibeComposerGUI.this.recalculateTabPaneCounts();
			}
			@Override public void regenerate() { VibeComposerGUI.this.regenerate(); }
			@Override public void openApplyCustomSectionPopup() {
				VibeComposerGUI.this.openApplyCustomSectionPopup();
			}
			@Override public void toggleButtonEnabledForPanels() {
				VibeComposerGUI.this.toggleButtonEnabledForPanels();
			}
			@Override public void addArrangementComponents(JComponent sectionPane,
					JComponent settings, int startY, int anchorSide) {
				constraints.gridy = startY;
				constraints.anchor = anchorSide;
				everythingPanel.add(sectionPane, constraints);
				constraints.gridy = startY + 1;
				everythingPanel.add(settings, constraints);
			}
			@Override public Point getVariationPopupLocation() {
				return new Point(SwingUtils.getMouseLocation().x, VibeComposerGUI.this.getLocation().y);
			}
			@Override public Dimension getVariationPopupWindowSize() {
				return VibeComposerGUI.this.getSize();
			}
			@Override public GUIConfig getSelectedConfigHistory() {
				ScrollComboBox<GUIConfig> configHistory = mainWindowControls.getConfigHistory();
				return configHistory.getItemCount() > 0 ? configHistory.getVal() : null;
			}
			@Override public GUIConfig getGUIConfig() { return guiConfig; }
			@Override public void recalculateAfterSectionRecompose() {
				recalculateTabPaneCounts();
				recalculateSoloMuters();
			}
			@Override public void regenerateAfterSectionRecomposeIfEnabled() {
				if (sequencer != null && generationGUI.regenerateWhenValuesChange.isSelected()) {
					playbackController.stopMidi();
					regenerate();
				}
			}
			@Override public int getCurrentSeed() { return generationGUI.getCurrentSeed(); }
			@Override public int getLastRandomSeed() { return generationGUI.lastRandomSeed; }
			@Override public boolean canRegenerateOnChange() {
				return generationGUI.canRegenerateOnChange();
			}
		}, playbackController, instrumentPanelController, chordGUI, instrumentTabPane,
				midiEditorSession);
	}

	private PartManagerPanel.Context createPartManagerContext() {
		return new PartManagerPanel.Context() {
			@Override public List<PartPresetStore.PresetFile> listPresets(INST part) throws IOException {
				return instrumentPanelController.listPartPresets(part);
			}
			@Override public int saveParts(String name, INST part, boolean selectiveSave)
					throws JAXBException {
				return instrumentPanelController.saveParts(name, part,
						selectiveSave);
			}
			@Override public void loadParts(String name, INST part, boolean clearPreviousPanels)
					throws JAXBException, IOException {
				if (!instrumentPanelController.loadParts(name, part, clearPreviousPanels)) {
					new TemporaryInfoPopup("Cannot change # of instruments in custom sections!", 1500);
				}
			}
			@Override public void recalculatePartCounts() {
				VibeComposerGUI.this.recalculateTabPaneCounts();
				VibeComposerGUI.this.recalculateGenerationCounts();
				VibeComposerGUI.this.recalculateSoloMuters();
			}
		};
	}

	private void initScoreGUI() {
		scoreGUI = new ScoreGUI(new ScoreGUI.Context() {
			@Override public JTabbedPane getInstrumentTabPane() {
				return VibeComposerGUI.instrumentTabPane;
			}
			@Override public Component getMainWindowComponent() {
				return VibeComposerGUI.this;
			}
			@Override public void setSliderEnd(int value) {
				playbackController.setSliderEnd(value);
			}
			@Override public void savePauseInfo() {
				playbackController.savePauseInfo();
			}
			@Override public void openMidiEditor(int sectionOrder, INST part, int panelOrder) {
				Section section = ArrangementGUI.actualArrangement.getSections().get(sectionOrder);
				midiEditorSession.open(section, part.getIndex(), panelOrder, sectionOrder);
			}
			@Override public void selectPanelFromScore(INST part, int panelOrder, int sectionOrder) {
				instrumentTabPane.setSelectedIndex(part.getIndex());
				if (ArrangementGUI.useArrangement.isSelected()) {
					ArrangementGUI.arrSection.setSelectedIndex(sectionOrder + 1);
					ArrangementGUI.arrSection.getButtons().forEach(button -> button.repaint());
					ArrangementGUI.arrSection.repaint();
					ArrangementGUI.switchTabPaneToScoreAfterApply = true;
				}
			}
			@Override public void togglePanelMute(INST part, int panelOrder) {
				instrumentPanelController.getPanelByOrder(part, panelOrder).getSoloMuter().toggleMute();
			}
			@Override public void togglePanelSolo(INST part, int panelOrder) {
				InstPanel panel = instrumentPanelController.getPanelByOrder(part, panelOrder);
				boolean unsoloAll = soloMuteController.getGlobalSoloMuter().soloState != State.OFF
						&& soloMuteController.isSingleSolo()
						&& panel.getSoloMuter().soloState == State.FULL;
				if (!unsoloAll) {
					soloMuteController.getGlobalSoloMuter().toggleSolo();
				}
				panel.getSoloMuter().toggleSolo();
			}
			@Override public JComponent getInstrumentBoxForPanel(INST part, int panelOrder) {
				return getAffectedPanels(part).get(panelOrder - 1).getInstrumentBox();
			}
			@Override public int getInstrumentPanelCount(INST instrument) {
				return getInstList(instrument).size();
			}
			@Override public Set<Integer> getSoloMuterHighlightedTracks() {
				Set<Integer> tracks = new HashSet<>();
				if (!scoreGUI.isSoloMuterHighlightEnabled()) {
					return tracks;
				}
				boolean checkMutes = soloMuteController.getGlobalSoloMuter().soloState == State.OFF;
				for (INST instrument : INST.values()) {
					for (InstPanel panel : getInstList(instrument)) {
						if (checkMutes ? panel.getSoloMuter().muteState == State.OFF
								: panel.getSoloMuter().soloState != State.OFF) {
							tracks.add(panel.getSequenceTrack());
						}
					}
				}
				return tracks;
			}
			@Override public void repaintScore() { scoreGUI.repaintScoreDisplay(); }
			@Override public List<Section> getArrangementSections() { return getGeneratedArrangementSections(); }
		});
	}

	private List<Section> getGeneratedArrangementSections() {
		return ArrangementGUI.actualArrangement == null
				? java.util.Collections.emptyList()
				: ArrangementGUI.actualArrangement.getSections();
	}

	private void initMelodyGUI() {
		melodyGUI = new MelodyGUI(new MelodyGUI.Context() {
			@Override public PartManagerPanel.Context getPartManagerContext() {
				return createPartManagerContext();
			}
			@Override public boolean isRandomizeInstOnComposeOrGen() {
				return generationGUI.randomizeInstOnComposeOrGen.isSelected();
			}
			@Override
			public void regenerate() {
				VibeComposerGUI.this.regenerate();
			}
			@Override public void setScoreTranspose(int transpose) {
				scoreGUI.setTranspose(transpose);
			}
			@Override public boolean canRegenerateOnChange() {
				return generationGUI.canRegenerateOnChange();
			}
			@Override public void setScaleMode(ScaleMode scaleMode) {
				generationGUI.scaleMode.setVal(scaleMode.toString());
			}
			@Override public List<Double> getUserChordDurations() {
				return chordGUI.getUserChordDurations();
			}

		}, instrumentPanelController);
	}

	private void initBassGUI() {
		bassGUI = new BassGUI(new BassGUI.Context() {
			@Override public PartManagerPanel.Context getPartManagerContext() {
				return createPartManagerContext();
			}
			@Override public boolean isRandomizeInstOnComposeOrGen() {
				return generationGUI.randomizeInstOnComposeOrGen.isSelected();
			}
		}, instrumentPanelController);
	}

	private void initChordGUI() {
		chordGUI = new ChordGUI(new ChordGUI.Context() {
			@Override public PartManagerPanel.Context getPartManagerContext() {
				return createPartManagerContext();
			}
			@Override public boolean isRandomizeInstOnComposeOrGen() {
				return generationGUI.randomizeInstOnComposeOrGen.isSelected();
			}
			@Override public boolean isBeatDurationMultiplierBelowOne() {
				return generationGUI.beatDurationMultiplier != null
						&& generationGUI.beatDurationMultiplier.getVal() < 0.75;
			}
			@Override public ScaleMode getScaleMode() {
				return ScaleMode.valueOf(generationGUI.scaleMode.getVal());
			}
			@Override public GUIConfig getGUIConfig() { return guiConfig; }
			@Override
			public void copyGUItoConfig() {
				VibeComposerGUI.this.copyGUItoConfig(guiConfig);
			}
			@Override public void adjustScoreTranspose(int amount) {
				scoreGUI.adjustTranspose(amount);
			}

			@Override
			public void alignChordsWithMelody(ChordletPanel chordlets) {
				if (!melodyGUI.getPanels().isEmpty()) {
					chordlets.alignWithMelodyTargetNotes(
							melodyGUI.getPanels().get(0).getChordNoteChoices());
				}
			}

		}, instrumentPanelController);
	}

	private void initArpGUI() {
		arpGUI = new ArpGUI(new ArpGUI.Context() {
			@Override public PartManagerPanel.Context getPartManagerContext() {
				return createPartManagerContext();
			}
			@Override public boolean isRandomizeInstOnComposeOrGen() {
				return generationGUI.randomizeInstOnComposeOrGen.isSelected();
			}
			@Override public boolean isBeatDurationMultiplierBelowOne() {
				return generationGUI.beatDurationMultiplier != null
						&& generationGUI.beatDurationMultiplier.getVal() < 0.75;
			}
		}, instrumentPanelController);
	}

	private void initDrumGUI() {
		drumGUI = new DrumGUI(new DrumGUI.Context() {
			@Override public PartManagerPanel.Context getPartManagerContext() {
				return createPartManagerContext();
			}
			@Override public int getLastRandomSeed() { return generationGUI.lastRandomSeed; }
		}, instrumentPanelController);
	}

	private void setMainIcon() {
		this.setIconImage(new ImageIcon(new ImageIcon(
				this.getClass().getResource("/VibeComposer2_LOGO_INVERT_NARROW.jpg"))
				.getImage().getScaledInstance(48, 48, java.awt.Image.SCALE_SMOOTH))
				.getImage());
	}

	private void init() {
		initChordGUI();
		initMelodyGUI();
		initBassGUI();
		initArpGUI();
		initDrumGUI();
		initArrangementGUI();
		initScoreGUI();
		midiAuditionController = new MidiAuditionController(instrumentPanelController,
				midiDeviceController, scoreGUI, melodyGUI,
				() -> ScaleMode.valueOf(generationGUI.scaleMode.getVal()), () -> guiConfig);
		long sysTime = System.currentTimeMillis();
		everythingPanel = new JPanel() {

			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				//LG.i("Painted main.");
				Graphics2D g2d = (Graphics2D) g;
				g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
						RenderingHints.VALUE_RENDER_QUALITY);
				int w = getWidth();
				int h = getHeight();
				Color color1 = panelColorHigh;
				Color color2 = panelColorLow;
				GradientPaint gp = new GradientPaint(0, 0, color1, 0, h, color2);
				g2d.setPaint(gp);
				g2d.fillRect(0, 0, w, h);
			}
		};
		GUIAssets.load();

		controlPanel = new JPanel();
		controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.X_AXIS));
		controlPanel.setOpaque(false);

		everythingPanel.setLayout(new GridBagLayout());
		everythingPane = new JScrollPane() {
			@Override
			public Dimension getMinimumSize() {
				return new Dimension(2000, 2000);
			}
		};
		everythingPane.setViewportView(everythingPanel);
		everythingPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		everythingPane.getVerticalScrollBar().setUnitIncrement(16);

		// register the closebox event
		this.addWindowListener(this);

		setLayout(new GridBagLayout());
		//setPreferredSize(new Dimension(1400, 1000));

		//constraints.fill = GridBagConstraints.BOTH;
		constraints.gridwidth = GridBagConstraints.REMAINDER;
		mainWindowControls.addHeaderControls(everythingPanel, constraints, 0, GridBagConstraints.CENTER, this);

		initExtraSettingsGUI();
		initGenerationGUI();

		// randomization buttons
		JPanel randomButtonsPanel = generationGUI.initRandomButtons();
		controlPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		constraints.gridy = 350;
		constraints.anchor = GridBagConstraints.CENTER;
		controlPanel.add(randomButtonsPanel, constraints);

		mainWindowControls.addSoloMuterAndTrackControls(everythingPanel, constraints, 20, GridBagConstraints.WEST, this);
		LG.i("Titles, Extra, S/M " + (System.currentTimeMillis() - sysTime) + " ms!");
		// ---- INSTRUMENT SETTINGS ----
		{
			chordGUI.initChordGenSettings();
			arpGUI.initArpGenSettings();
			drumGUI.initDrumGenSettings();
			melodyGUI.initMelodyGenSettings();

		}
		LG.i("Gen settings: " + (System.currentTimeMillis() - sysTime) + " ms!");
		boolean randomizeInstsTemp = generationGUI.randomizeInstOnComposeOrGen.isSelected();
		generationGUI.randomizeInstOnComposeOrGen.setSelected(true);
		{
			// ---- INSTRUMENT PANELS ----

			instrumentTabPane.addTab("Melody", melodyGUI.initMelody());
			instrumentTabPane.addTab("Bass", bassGUI.initBass());
			instrumentTabPane.addTab("Chords", chordGUI.initChords());
			instrumentTabPane.addTab("Arps", arpGUI.initArps());
			instrumentTabPane.addTab("Drums", drumGUI.initDrums());
			LG.i("Insts: " + (System.currentTimeMillis() - sysTime) + " ms!");

			constraints.gridy = 320;
			constraints.anchor = GridBagConstraints.WEST;

			instrumentTabPane.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					int indx = instrumentTabPane.indexAtLocation(e.getX(), e.getY());
					if (indx >= 0 && indx < INST.values().length) {
						INST inst = INST.fromIndex(indx);
						if (SwingUtilities.isRightMouseButton(e)) {
							LG.i(("RMB pressed in instrument tab pane: " + indx));
							setAddInst(inst,
									!getInstrumentControls(inst).getEnabledCheckBox().isSelected());
						} else if (SwingUtilities.isMiddleMouseButton(e)) {
							LG.i(("MMB pressed in instrument tab pane: " + indx));
							boolean hasAny = false;
							for (INST instrument : INST.values()) {
								if (instrument.getIndex() != indx
										&& getInstrumentControls(instrument).getEnabledCheckBox().isSelected()) {
									hasAny = true;
									break;
								}
							}
							for (INST instrument : INST.values()) {
								if (instrument.getIndex() != indx) {
									setAddInst(instrument, !hasAny);
								}
							}
							setAddInst(inst, true);
						}
					} else if (indx == 6) {
						arrangementGUI.refreshPartMapsFromOldData();
						ArrangementGUI.scrollableArrangementActualTable.repaint();
					}
				}
			});
			everythingPanel.add(instrumentTabPane, constraints);
			for (INST instrument : INST.values()) {
				int instrumentIndex = instrument.getIndex();
				instrumentTabPane.setBackgroundAt(instrumentIndex,
						OMNI.alphen(Constants.instColors[instrumentIndex], 40));
				getInstrumentControls(instrument).getEnabledCheckBox().addChangeListener((evt) -> {
					instrumentTabPane.setBackgroundAt(instrumentIndex,
							OMNI.alphen(getInstrumentControls(instrument).getEnabledCheckBox().isSelected()
									? Constants.instColors[instrumentIndex] : Color.white, 40));
				});
			}

			// arrangement
			arrangementGUI.initArrangementSettings(325, GridBagConstraints.CENTER);


		}
		generationGUI.randomizeInstOnComposeOrGen.setSelected(randomizeInstsTemp);
		LG.i("Arr: " + (System.currentTimeMillis() - sysTime) + " ms!");
		scoreGUI.initScoreSettings();
		LG.i("Scr: " + (System.currentTimeMillis() - sysTime) + " ms!");
		//createHorizontalSeparator(327, this);

		// ---- OTHER SETTINGS ----
		{


			JPanel macroParams = generationGUI.initMacroParams();
			constraints.gridy = 360;
			constraints.anchor = GridBagConstraints.CENTER;
			controlPanel.add(macroParams, constraints);

			// chord settings - variety/spice
			// chord settings - progressions
			JPanel chordProgressionSettingsPanel = chordGUI.initChordProgressionSettings();
			constraints.gridy = 370;
			constraints.anchor = GridBagConstraints.CENTER;
			controlPanel.add(chordProgressionSettingsPanel, constraints);

			// chord tool tip

			everythingPanel.add(controlPanel, constraints);

			JPanel customChordsPanel = chordGUI.initCustomChords();
			constraints.gridy = 380;
			constraints.anchor = GridBagConstraints.CENTER;
			everythingPanel.add(customChordsPanel, constraints);

		}
		LG.i("Butts, params, cp, chords: " + (System.currentTimeMillis() - sysTime) + " ms!");

		//createHorizontalSeparator(400, this);

		// ---- CONTROL PANEL -----
		KnobPanel globalTransposeControl = scoreGUI.createTransposeControl();
		mainWindowControls.addComposeControls(everythingPanel, constraints, 410,
				GridBagConstraints.CENTER, globalTransposeControl, ExtraSettingsGUI.bpmLow.getInt(),
				ExtraSettingsGUI.bpmHigh.getInt(), chordGUI.currentChords, chordGUI, generationGUI);


		// ---- PLAY PANEL ----
		mainWindowControls.addPlaybackControls(everythingPanel, constraints, 420,
				GridBagConstraints.CENTER, scoreGUI, generationGUI);
		initSliderPanel(440, GridBagConstraints.CENTER);
		presetViewController = new PresetViewController(mainWindowControls, scoreGUI, chordGUI, drumGUI,
				arpGUI, melodyGUI, generationGUI);
		LG.i("Control, play, slider: " + (System.currentTimeMillis() - sysTime) + " ms!");
		// --- GENERATED MIDI DRAG n DROP ---

		constraints.anchor = GridBagConstraints.CENTER;

		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		int screenHeight = d.height;
		int screenWidth = d.width;
		setSize(screenWidth / 2, screenHeight / 2);

		setFocusable(true);
		requestFocus();
		requestFocusInWindow();

		isDarkMode = !isDarkMode;


		everythingPane.setViewportView(everythingPanel);
		add(everythingPane, constraints);
		LG.i("Add everything: " + (System.currentTimeMillis() - sysTime) + " ms!");
		setFullMode(isFullMode);
		configureKnobContexts(everythingPanel);
		LG.i("Full: " + (System.currentTimeMillis() - sysTime) + " ms!");
		instrumentTabUndoManager.setRecordingEvents(true);
		instrumentTabPane.setSelectedIndex(7);
		//instrumentTabPane.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		recalculateTabPaneCounts();
		switchDarkMode();

		pack();
		setLocationRelativeTo(null);
		LG.i("Dark, pack: " + (System.currentTimeMillis() - sysTime) + " ms!");

		defaultGuiPreset = new GUIPreset();
		copyGUItoConfig(guiConfig);
		defaultGuiPreset = presetViewController.copyCurrentViewToPreset(defaultGuiPreset, guiConfig);

		boolean presetLoaded = false;
		if (mainWindowControls.getPresetLoadBox().getVal().equalsIgnoreCase("default")) {
			GUIPreset preset = presetViewController.loadPreset();
			if (preset != null) {
				loadPresetObject(preset);
			}
			presetLoaded = true;
		}

		initKeyboardListener();
		initMouseBackForwardListener();
		// block compose/regenerate until UI fully loaded
		heavyBackgroundTasksInProgress = true;
		setVisible(true);

		repaint();
		//initScrollPaneListeners();
		actionUndoManager.setRecordingEvents(true);
		LG.i("VibeComposer started in: " + (System.currentTimeMillis() - sysTime)
				+ " ms! Creating Panels in background...");

		if (!presetLoaded) {
			melodyGUI.generateInitialMelodyPanels();
			for (INST instrument : INST.values()) {
				if (instrument != INST.MELODY) {
					instrumentPanelController.generatePanels(instrument);
				}
			}
			LG.i("Panels generated at : " + (System.currentTimeMillis() - sysTime) + " ms!");
		}

		heavyBackgroundTasksInProgress = false;
	}


	private void initScrollPaneListeners() {
		for (INST instrument : INST.values()) {
			SwingUtils.setupScrollpanePriorityScrolling(getInstPane(instrument));
		}
		SwingUtils.setupScrollpanePriorityScrolling(ArrangementGUI.arrangementScrollPane);
		SwingUtils.setupScrollpanePriorityScrolling(ArrangementGUI.arrangementActualScrollPane);
	}

	protected void setAddInst(INST instrument, boolean enabled) {
		getInstrumentControls(instrument).getEnabledCheckBox().setSelected(enabled);
	}

	private void initKeyboardListener() {
		KeyboardFocusManager.getCurrentKeyboardFocusManager()
				.addKeyEventDispatcher(new KeyEventDispatcher() {
					public boolean dispatchKeyEvent(KeyEvent e) {
						if (!vibeComposerGUI.isFocused()
								|| !vibeComposerGUI.isActive()
								|| e.getID() != KeyEvent.KEY_RELEASED
								|| e.getSource() instanceof JTextField) {
							return false;
						}
						if (e.isControlDown()) {
								if (e.getKeyCode() == KeyEvent.VK_Z)
									actionUndoManager.undo();
								else if (e.getKeyCode() == KeyEvent.VK_Y)
									actionUndoManager.redo();
						} else {
							if (e.getKeyCode() >= KeyEvent.VK_1 && e.getKeyCode() <= KeyEvent.VK_8) {
								instrumentTabPane.setSelectedIndex(e.getKeyCode() - KeyEvent.VK_1);
							}
						}
						return false;
					}
				});
	}

	private void initMouseBackForwardListener() {
		Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
			public void eventDispatched(AWTEvent event) {
				if (event instanceof MouseEvent){
					MouseEvent evt = (MouseEvent)event;
					if (evt.getID() == MouseEvent.MOUSE_RELEASED) {
						if (!vibeComposerGUI.isFocused()
								|| !vibeComposerGUI.isActive()) {
							return;
						}
						if (evt.getButton() == 4) {
							instrumentTabUndoManager.undo();
						} else if (evt.getButton() == 5) {
							instrumentTabUndoManager.redo();
						}
					}
				}
			}
		}, AWTEvent.MOUSE_EVENT_MASK);
	}

	private void resetAllFromHeader() {
		if (heavyBackgroundTasksInProgress) {
			return;
		}
		loadPresetObject(defaultGuiPreset);
		heavyBackgroundTasksInProgress = true;
		getInstrumentControls(INST.MELODY).getRandomPanelsToGenerate().setText("3");
		getInstrumentControls(INST.BASS).getRandomPanelsToGenerate().setText("1");
		getInstrumentControls(INST.CHORD).getRandomPanelsToGenerate().setText("2");
		getInstrumentControls(INST.ARP).getRandomPanelsToGenerate().setText("3");
		getInstrumentControls(INST.DRUM).getRandomPanelsToGenerate().setText("6");
		melodyGUI.generateInitialMelodyPanels();
		for (INST instrument : INST.values()) {
			if (instrument != INST.MELODY) {
				instrumentPanelController.generatePanels(instrument);
			}
		}
		ArrangementGUI.manualArrangement.setSelected(false);
		heavyBackgroundTasksInProgress = false;
		LG.i("Default Panels generated!");
	}

	public void loadPresetObject(GUIPreset preset) {
		if (heavyBackgroundTasksInProgress) {
			return;
		}
		heavyBackgroundTasksInProgress = true;
		guiConfig = preset;
		copyConfigToGUI(guiConfig);
		presetViewController.restoreViewValues(preset);
		clearAllSeeds();
		if (isFullMode != preset.isFullMode()) {
			switchFullMode();
		}
		if (isDarkMode != preset.isDarkMode()) {
			switchDarkMode();
		}
		if (isBigMonitorMode != preset.isBigMode()) {
			switchBigMonitorMode();
		}

		recalculateTabPaneCounts();
		recalculateGenerationCounts();
		//manualArrangement.setSelected(false);
		vibeComposerGUI.repaint();
		heavyBackgroundTasksInProgress = false;
	}

	private void initSliderPanel(int startY, int anchorSide) {
		sliderPanel = new JPanel();
		sliderPanel.setOpaque(true);
		sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.X_AXIS));
		sliderPanel.setPreferredSize(new Dimension(1250, 55));

		sliderPanel.add(new JLabel("                                 "));

		slider = scoreGUI.createPlayheadRangeSlider(instrumentTabPane);
		slider.setMaximum(0);
		//slider.setToolTipText("Test");
		slider.setDisplayValues(false);
		slider.setSnapToTicks(ExtraSettingsGUI.snapStartToBeat.isSelected());
		//slider.setMinimumSize(new Dimension(1000, 3));
		slider.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseReleased(MouseEvent e) {

				if (isPlayheadDragging()) {
					playbackController.savePauseInfo();
					if (sequencer != null)
						playbackController.midiNavigate(slider.getUpperValue());
				}
			}
		});
		sliderPanel.add(slider);
		constraints.gridy = startY;
		constraints.anchor = anchorSide;


		JPanel sliderInfoPanel = new JPanel();
		sliderInfoPanel.setOpaque(false);
		sliderInfoPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
		sliderInfoPanel.setMaximumSize(new Dimension(200, 30));
		sliderInfoPanel.setPreferredSize(new Dimension(200, 30));
		sliderInfoPanel.setMinimumSize(new Dimension(200, 30));
		currentTime = new JLabel("0:00");
		currentTime.setMaximumSize(new Dimension(50, 20));

		totalTime = new JLabel("0:00");
		totalTime.setMaximumSize(new Dimension(50, 20));

		sectionText = new JLabel("INTRO");
		sectionText.setMaximumSize(new Dimension(100, 20));


		sliderInfoPanel.add(currentTime);
		sliderInfoPanel.add(new JLabel(" / "));
		sliderInfoPanel.add(totalTime);
		sliderInfoPanel.add(new JLabel(" | "));
		sliderInfoPanel.add(sectionText);

		sliderPanel.add(sliderInfoPanel);

		everythingPanel.add(sliderPanel, constraints);
		/*constraints.gridy = startY + 1;
		constraints.anchor = anchorSide;
		everythingPanel.add(sliderInfoPanel, constraints);*/

		startOmnipresentThread();
		startSoloButtonControlThread();

	}

	private void startSoloButtonControlThread() {
		Thread cycle = new Thread() {

			public void run() {
				while (true) {
					try {
						recalculateSolosMutes();
						try {
							if (SwingUtilities.isEventDispatchThread()) {
								LG.i("SB EDT!");
							}
							sleep(100);
						} catch (InterruptedException e) {
							LG.e(e.getMessage());
						}
					} catch (Exception e) {
						LG.i(("Exception in SOLO buttons thread:" + e));
					}
				}
			}

			private void recalculateSolosMutes() {
				// recalc sequencer tracks from button colorings
				if (!heavyBackgroundTasksInProgress) {
					soloMuteController.processRecalculationRequest(
							this::recolorButtons);
				}
			}

			private void recolorButtons() {
				long totalCount = countAllPanels();
				long totalSoloCount = 0;
				long totalMuteCount = 0;

				for (INST instrument : INST.values()) {
					int instrumentIndex = instrument.getIndex();
					long groupSoloCount = getInstList(instrument).stream()
							.filter(e -> e.getSoloMuter().soloState == SoloMuter.State.FULL)
							.count();
					if (groupSoloCount < getInstList(instrument).size() && groupSoloCount > 0) {
						soloMuteController.getGroupSoloMuters().get(instrumentIndex).halfSolo();
					} else if (groupSoloCount == 0) {
						soloMuteController.getGroupSoloMuters().get(instrumentIndex).unsolo();
					}
					long groupMuteCount = getInstList(instrument).stream()
							.filter(e -> e.getSoloMuter().muteState == SoloMuter.State.FULL)
							.count();
					if (groupMuteCount < getInstList(instrument).size() && groupMuteCount > 0) {
						soloMuteController.getGroupSoloMuters().get(instrumentIndex).halfMute();
					} else if (groupMuteCount == 0) {
						soloMuteController.getGroupSoloMuters().get(instrumentIndex).unmute();
					}
					totalSoloCount += groupSoloCount;
					totalMuteCount += groupMuteCount;
				}
				if (totalSoloCount < totalCount && totalSoloCount > 0) {
					soloMuteController.getGlobalSoloMuter().halfSolo();
				} else if (totalSoloCount == 0) {
					soloMuteController.getGlobalSoloMuter().unsolo();
				}


				if (totalMuteCount < totalCount && totalMuteCount > 0) {
					soloMuteController.getGlobalSoloMuter().halfMute();
				} else if (totalMuteCount == 0) {
					soloMuteController.getGlobalSoloMuter().unmute();
				}

			}
		};
		cycle.start();


	}

	public static int countAllPanels() {
		int count = 0;
		for (INST instrument : INST.values()) {
			count += getInstList(instrument).size();
		}
		return count;
	}

	private void startOmnipresentThread() {
		// init thread

		Thread cycle = new Thread() {

			public void run() {
				int sleepTime = 10;
				int allowedActionsOnZero = 0;
				while (true) {
					try {
						if (sequencer != null && sequencer.isRunning()) {
							if (!isPlayheadDragging() && !isKeySeeking) {
								queuePlayheadUpdate(allowedActionsOnZero == 0);
								if (allowedActionsOnZero == 0) {
									if (midiEditorSession.isVisible()) {
										sleepTime = 20;
									} else {
										sleepTime = 10;
									}
								} else {
									slider.setUpperValueRaw(
											(int) (sequencer.getMicrosecondPosition() / 1000));
								}
							}

						}


						if (allowedActionsOnZero == 0) {
							if (ArrangementGUI.actualArrangement != null && slider.getMaximum() > 0) {
								int val = sequencer != null && sequencer.isRunning()
										&& !isPlayheadDragging() && !isKeySeeking
										? (int) (sequencer.getMicrosecondPosition() / 1000)
										: slider.getUpperValue();
								String newTime = OMNI.millisecondsToTimeString(val);
								SwingUtilities.invokeLater(() -> {
									if (!newTime.equals(currentTime.getText())) {
										currentTime.setText(newTime);
									}
								});
								int sectIndex = -1;
								if (sliderMeasureStartTimes != null
										&& ArrangementGUI.actualArrangement.getSections().size() > 0) {
									int sectIndexCounter = 0;
									List<Integer> secMeasures = ArrangementGUI.actualArrangement.getSections()
											.stream().map(e -> e.getMeasures())
											.collect(Collectors.toList());
									int currentMeasure = secMeasures.get(0);
									for (int i = 1; i < sliderMeasureStartTimes.size(); i++) {
										if (val < sliderMeasureStartTimes.get(i)) {
											sectIndex = sectIndexCounter;
											break;
										} else {
											currentMeasure--;
											if (currentMeasure <= 0) {
												sectIndexCounter++;
												if (sectIndexCounter >= secMeasures.size()) {
													break;
												}
												currentMeasure = secMeasures.get(sectIndexCounter);
											}
										}
									}
								}

								Section sec = null;
								if (sectIndex >= 0
										&& sectIndex < ArrangementGUI.actualArrangement.getSections().size()) {
									sec = ArrangementGUI.actualArrangement.getSections().get(sectIndex);
								}
								int finalSectIndex = sectIndex;
								String newText = null;
								if (sec == null) {
									newText = "End";
								} else {
									newText = sec.getType().toString();
								}

								String sectionName = newText;
								SwingUtilities.invokeLater(() -> {
									if (!sectionText.getText().equalsIgnoreCase(sectionName)) {
										sectionText.setText(sectionName);
									}
								});

								Section actualSec = sec;
								int part = instrumentTabPane.getSelectedIndex();
								if (part >= 2 && part <= 4) {
									if (ExtraSettingsGUI.highlightPatterns.isSelected()) {
										SwingUtilities.invokeLater(() -> notifyVisualPatterns(val,
												finalSectIndex, actualSec));
									}
								}

								if (sequencer != null) {
									if (generationGUI.mainBpm.getInt() != (int) guiConfig.getBpm()) {
										sequencer.setTempoFactor(
												(float) (generationGUI.mainBpm.getInt() / guiConfig.getBpm()));
									}
									if (ExtraSettingsGUI.rememberLastPos.isSelected()) {
										playbackController.savePauseInfo();
									}
								}

							}
						}

						if (loopBeat.isSelected() && !heavyBackgroundTasksInProgress
								&& !isPlayheadDragging()
								&& (sequencer != null)) {
							int startPos = delayed();
							if (slider.getValue() > startPos) {
								startPos = slider.getValue();
							}
							int newSliderVal = slider.getUpperValue() - startPos;
							boolean sequencerEnded = slider.getMaximum()
									- slider.getUpperValue() < 100 && !sequencer.isRunning();
							double mult = 1;
							if (generationGUI.beatDurationMultiplier.getSelectedIndex() == 0) {
								mult = 0.5;
							} else if (generationGUI.beatDurationMultiplier.getSelectedIndex() == 2) {
								mult = 2;
							}
							if (newSliderVal >= ((mult * generationGUI.loopBeatCount.getInt() * beatFromBpm(0))
									- 50) || sequencerEnded) {
								playbackController.stopMidi();
								switch (mainWindowControls.getLoopBeatCompose().getVal()) {
								case "REGENERATE":
									regenerate();
									break;
								case "COMPOSE":
									ActionEvent action = new ActionEvent(VibeComposerGUI.this,
											ActionEvent.ACTION_PERFORMED, "Compose");
									SwingUtilities.invokeLater(() -> actionPerformed(action));
									break;
								case "REPLAY":
									playbackController.playMidi(true);
									break;
								default:
									playbackController.stopMidi();
									throw new IllegalArgumentException(
											"Unsupported loop beat behavior!");
								}
							}
						}

						try {
							allowedActionsOnZero = (allowedActionsOnZero + 1) % 5;
							sleep(sleepTime);

						} catch (InterruptedException e) {
							LG.e("THREAD INTERRUPTED!");
						}
					} catch (Exception e) {
						LG.e("Exception in SEQUENCE SLIDER:");
						heavyBackgroundTasksInProgress = false;
						LG.e(e.getMessage());
						LG.e(e);
						try {
							sleep(500);
						} catch (InterruptedException e2) {

						}
					}
				}

			}
		};
		cycle.start();
	}

	private void queuePlayheadUpdate(boolean refreshScore) {
		if (refreshScore) {
			playheadRefreshPending.set(true);
		}
		if (!playheadUpdatePending.compareAndSet(false, true)) {
			return;
		}
		SwingUtilities.invokeLater(() -> {
			boolean refresh = playheadRefreshPending.getAndSet(false);
			if (sequencer != null && sequencer.isRunning()
					&& !isPlayheadDragging() && !isKeySeeking) {
				int playheadPosition = (int) (sequencer.getMicrosecondPosition() / 1000);
				if (refresh) {
					slider.setUpperValue(playheadPosition);
				} else {
					slider.setUpperValueRaw(playheadPosition);
				}
			}
			playheadUpdatePending.set(false);
			if (playheadRefreshPending.get()) {
				queuePlayheadUpdate(false);
			}
		});
	}

	private boolean isPlayheadDragging() {
		return slider != null && slider.isUpperDragging();
	}


	private void notifyVisualPatterns(int val, int sectIndex, Section sec) {
		int part = instrumentTabPane.getSelectedIndex();
		if (part >= 2 && part <= 4) {
			int measureStart = sliderMeasureStartTimes.get(
					(sectIndex >= 0 && (sectIndex < sliderMeasureStartTimes.size() - 1)) ? sectIndex
							: 0);
			int beatFindingStartIndex = sliderBeatStartTimes.indexOf(measureStart);
			int beatChordNumInMeasure = 0;
			int bfsi = beatFindingStartIndex;
			//int bfsiEnd = 0;
			int lastMeasureStartTimeIndex = 0;
			double quarterNote = beatFromBpm(0);
			List<Double> prevChordDurations = new ArrayList<>();
			for (; bfsi < sliderBeatStartTimes.size(); bfsi++) {
				if (sliderBeatStartTimes.get(bfsi) > val) {
					//bfsiEnd = bfsi;
					break;
				} else {

					int measureIndex = sliderMeasureStartTimes
							.indexOf(sliderBeatStartTimes.get(bfsi));
					if (measureIndex != -1) {
						// reset when exactly on measure
						beatChordNumInMeasure = 0;
						lastMeasureStartTimeIndex = measureIndex;
						prevChordDurations.clear();
					} else {
						beatChordNumInMeasure++;
					}

					if (beatChordNumInMeasure > 0) {
						prevChordDurations.add((sliderBeatStartTimes.get(bfsi)
								- sliderBeatStartTimes.get(bfsi - 1)) / quarterNote);
					}
				}
			}
			/*if (val < sliderMeasureStartTimes.get(lastMeasureStartTimeIndex)
					&& lastMeasureStartTimeIndex > 0) {
				lastMeasureStartTimeIndex--;
			}*/
			double quarterNotesInMeasure = (val
					- sliderMeasureStartTimes.get(lastMeasureStartTimeIndex)) / quarterNote;
			//LG.d(quarterNotesInMeasure + " qtn");
			if (quarterNotesInMeasure < MidiGenerator.DBL_ERR) {
				return;
			}
			// need current chord's duration!
			double currentChordDuration = (sliderBeatStartTimes.size() > bfsi)
					? (sliderBeatStartTimes.get(bfsi) - sliderBeatStartTimes.get(bfsi - 1))
							/ quarterNote
					: Durations.WHOLE_NOTE;

			boolean soloCondition = soloMuteController.getGlobalSoloMuter().soloState != State.OFF;
			INST instrument = INST.fromIndex(part);
			List<InstPanel> panels = getAffectedPanels(instrument);
			Set<Integer> presences = sec != null ? sec.getPresence(part) : null;
			int totalChords = (sec != null && sec.getSectionBeatDurations() != null)
					? sec.getSectionBeatDurations().size()
					: MidiGenerator.chordInts.size();
			for (InstPanel ip : panels) {
				boolean turnOff = ip.getMuteInst() || presences == null
						|| !presences.contains(ip.getPanelOrder());
				if (!turnOff) {
					turnOff |= ((soloCondition ? ip.getSoloMuter().soloState == State.OFF
							: ip.getSoloMuter().muteState != State.OFF));
				}

				boolean isIgnoreFill = false;
				if (!turnOff && sec != null) {
					int ignoreFillIndex = instrument == INST.DRUM ? 0 : 1;
					isIgnoreFill = sec
							.getVariation(part,
									instrumentPanelController.getAbsoluteOrder(INST.fromIndex(part),
											ip.getPanelOrder()))
							.contains(ignoreFillIndex);
				}

				double delayedQuarterNotes = quarterNotesInMeasure
						- MidiGenerator.noteMultiplier * (ip.getOffset() / 1000.0);

				ip.getComboPanel().notifyPatternHighlight(delayedQuarterNotes,
						beatChordNumInMeasure, prevChordDurations, turnOff, isIgnoreFill,
						totalChords, currentChordDuration);
			}
			/*for (InstPanel ip : panels) {
				if (ip.getMuteInst()
						|| (presences != null && !presences.contains(ip.getPanelOrder()))) {
					continue;
				}
				ip.getComboPanel().repaint();
			}*/
			instrumentTabPane.getSelectedComponent().repaint();
		}
	}

	public int delayed() {
		return (int) (MidiGenerator.START_TIME_DELAY * 1000 * 60 / guiConfig.getBpm());
	}

	public int beatFromBpm(int speedAdjustment) {
		int finalVal = (int) (((1000 - speedAdjustment) * 60 * ExtraSettingsGUI.stretchMidi.getInt() / 100.0)
				/ guiConfig.getBpm());
		/*if (useDoubledDurations.isSelected()) {
			finalVal *= 2;
		}*/
		return finalVal;
	}

	public int sliderMeasureWidth() {
		return (int) (beatFromBpm(0) * MidiGenerator.GENERATED_MEASURE_LENGTH);
	}

	public void regenerateInPlace() {
		boolean wasSelected = ExtraSettingsGUI.startFromBar.isSelected();
		playbackController.setPauseInfoResettable(false);
		ExtraSettingsGUI.startFromBar.setSelected(false);
		playbackController.pauseMidi();
		actionPerformed(
				new ActionEvent(mainWindowControls.getRegeneratePausePlayButton(),
						ActionEvent.ACTION_PERFORMED, "Regenerate"));
		ExtraSettingsGUI.startFromBar.setSelected(wasSelected);
		playbackController.setPauseInfoResettable(true);
	}

	private void saveWavFile() {
		if (currentMidi == null) {
			mainWindowControls.getMessageLabel().setText("Need to compose first!");
			mainWindowControls.getMessageLabel().repaint(0);
			return;
		}
		switchMidiButtons(false);
		playbackController.stopMidi();
		//sizeRespectingPack();
		repaint();
		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {

			@Override
			protected Void doInBackground()
					throws InterruptedException, MidiUnavailableException, IOException {
				SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();
				Synthesizer defSynth;
				f.applyPattern("yyMMdd-HH-mm-ss");
				Date date = new Date();
				defSynth = midiDeviceController.getSynthesizerForWaveExport();
				String soundbankOptional = (midiDeviceController.getSoundbank() != null) ? "SB_" : "";
				String filename = f.format(date) + "_" + soundbankOptional
						+ getFilenameForSaving(currentMidi.getName());
				File exportFolderDir = new File(Constants.EXPORT_FOLDER);
				exportFolderDir.mkdir();

				midiExportController.writeWaveFile(
						Constants.EXPORT_FOLDER + "/" + filename + "-export.wav", defSynth);
				midiDeviceController.clearAfterWaveExport();
				return null;
			}

			@Override
			protected void done() {
				try {
					Synthesizer synthesizer = null;
					if (!mainWindowControls.getMidiMode().isSelected()) {
						synthesizer = midiDeviceController.loadSynth();
					}
					midiDeviceController.prepareMidiPlayback(currentSequenceMidi, synthesizer);
				} catch (InvalidMidiDataException | MidiUnavailableException e) {
					LG.e(e);
				}
				switchMidiButtons(true);
				mainWindowControls.getMessageLabel().setText("PROCESSED WAV!");
				repaint();
			}
		};
		worker.execute(); //here the process thread initiates
	}

	private void initHelperPopups(JPanel settingsPanel) {
		JPanel helperPopupsPanel = new JPanel();
		helperPopupsPanel.add(SwingUtils.makeButton("User Manual (opens browser)", e -> new HelpPopup()));
		helperPopupsPanel.add(SwingUtils.makeButton("Debug Console", e -> openDebugConsole()));
		helperPopupsPanel.add(SwingUtils.makeButton("About VibeComposer", e -> new AboutPopup()));
		settingsPanel.add(helperPopupsPanel, BorderLayout.SOUTH);
	}

	private void switchAllOnComposeCheckboxes(boolean state) {
		melodyGUI.generateMelodiesOnCompose.setSelected(state);
		chordGUI.randomChordsGenerateOnCompose.setSelected(state);
		arpGUI.randomArpsGenerateOnCompose.setSelected(state);
		drumGUI.randomDrumsGenerateOnCompose.setSelected(state);
		generationGUI.randomizeBpmOnCompose.setSelected(state);
		generationGUI.randomizeTransposeOnCompose.setSelected(state);
		generationGUI.randomizeInstOnComposeOrGen.setSelected(state);
		arpGUI.randomArpHitsPerPattern.setSelected(state);
		ArrangementGUI.randomizeArrangementOnCompose.setSelected(state);
		ArrangementGUI.arrangementResetCustomPanelsOnCompose.setSelected(state);
		mainWindowControls.getRandomizeScaleModeOnCompose().setSelected(state);
		melodyGUI.melodyTargetNotesRandomizeOnCompose.setSelected(state);
		melodyGUI.melodyPatternRandomizeOnCompose.setSelected(state);
		ExtraSettingsGUI.randomizeTimingsOnCompose.setSelected(state);
		ExtraSettingsGUI.sidechainPatternsOnCompose.setSelected(state);
		chordGUI.copyChordsAfterGenerate.setSelected(state);
	}

	private void switchMidiButtons(boolean state) {
		mainWindowControls.toggleReadyState(state);
	}

	private void switchBigMonitorMode() {
		Dimension newPrefSize = null;
		isBigMonitorMode = !isBigMonitorMode;
		if (isBigMonitorMode) {
			newPrefSize = new Dimension(1900, 600);
		} else {
			newPrefSize = new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT + 35);
		}
		if (scoreGUI.getScorePanel() != null) {
			scoreGUI.getScorePanel().updatePanelHeight(newPrefSize.height);
			scoreGUI.getScorePanel().update();
		}
		scrollPaneDimension = newPrefSize;
		instrumentTabPane.setPreferredSize(newPrefSize);
		instrumentTabPane.setSize(newPrefSize);

		for (DrumPanel dp : drumGUI.getPanels()) {
			dp.getComboPanel().reapplyHits();
		}
		arrangementGUI.refreshVariationPopupButtons(
				ArrangementGUI.scrollableArrangementActualTable.getColumnCount());
		pack();
	}

	public void removeComboBoxArrows(Container parent) {
		for (Component c : parent.getComponents()) {
			if (c instanceof ScrollComboBox) {
				ScrollComboBox scb = (ScrollComboBox) c;
				scb.removeArrowButton();
				//LG.d("Unconfigured");
			}

			if (c instanceof Container) {
				//LG.d("Going deep");
				removeComboBoxArrows((Container) c);
			}

		}
	}

	private void switchDarkMode() {
		getAppearanceController().switchDarkMode();

		arrangementGUI.refreshVariationPopupButtons(
				ArrangementGUI.actualArrangement.getSections().size());

		if (scoreGUI.getScorePanel() != null) {
			scoreGUI.getScorePanel().update();
		}
		removeComboBoxArrows(everythingPanel);
		repaint();
		ArrangementGUI.arrSectionPane.repaint();
		if (scoreGUI.getScorePanel() != null) {
			scoreGUI.getScorePanel().setupMouseWheelListener();
		}
		initScrollPaneListeners();
	}

	private AppearanceController getAppearanceController() {
		if (appearanceController == null) {
			appearanceController = new AppearanceController(new AppearanceController.Context() {
				@Override public JFrame getWindow() { return VibeComposerGUI.this; }
			}, mainWindowControls, soloMuteController, instrumentPanelController,
				chordGUI, drumGUI, arpGUI,
				melodyGUI, generationGUI);
		}
		return appearanceController;
	}

	private void switchFullMode() {
		isFullMode = !isFullMode;
		setFullMode(isFullMode);
	}

	private void setFullMode(boolean mode) {
		toggleableComponents.forEach(e -> e.setVisible(mode));
		for (INST instrument : INST.values()) {
			getInstList(instrument)
					.forEach(e -> e.getToggleableComponents().forEach(f -> f.setVisible(mode)));
		}
	}

	private void toggleButtonEnabledForPanels() {
		toggleButtonEnabledForPanels(ArrangementGUI.GLOBAL.equals(ArrangementGUI.arrSection.getVal()));
	}

	private void toggleButtonEnabledForPanels(boolean isOriginal) {
		for (INST instrument : INST.values()) {
			getInstrumentControls(instrument).getAddPanelButton().setEnabled(isOriginal);
			getInstrumentControls(instrument).getRandomPanelsToGenerate().setEnabled(isOriginal);
		}
	}

	public void regenerate() {
		composeMidi(true, false);
	}

	public void composeMidi(boolean regenerate, boolean manual) {
		getComposeCoordinator().composeMidi(regenerate, manual);
	}

	private ComposeCoordinator getComposeCoordinator() {
		if (composeCoordinator == null) {
			composeCoordinator = new ComposeCoordinator(new ComposeCoordinator.Context() {
				@Override public GUIConfig getGUIConfig() { return guiConfig; }
				@Override public void setGUIConfig(GUIConfig config) { guiConfig = config; }
				@Override public void copyGuiToConfig(GUIConfig config, boolean isNew) {
					VibeComposerGUI.this.copyGUItoConfig(config, isNew);
				}
				@Override public void assignSequenceTrack(int instrument, int panelOrder, int trackNumber) {
					VibeComposerGUI.this.assignSequenceTrack(instrument, panelOrder, trackNumber);
				}
				@Override public int sliderMeasureWidth() { return VibeComposerGUI.this.sliderMeasureWidth(); }
				@Override public int delayed() { return VibeComposerGUI.this.delayed(); }
				@Override public int selectedInstrumentTab() { return instrumentTabPane.getSelectedIndex(); }
				@Override public void repaintMainWindow() { VibeComposerGUI.this.repaint(); }
				@Override public void recalculateTabPaneCounts() {
					VibeComposerGUI.this.recalculateTabPaneCounts();
				}
				@Override public void setHeavyBackgroundTaskInProgress(boolean inProgress) {
					heavyBackgroundTasksInProgress = inProgress;
				}
			}, playbackController, midiDeviceController, midiCcController, soloMuteController,
					mainWindowControls, melodyGUI, chordGUI, arpGUI, drumGUI, generationGUI,
					arrangementGUI, scoreGUI, midiEditorSession, consoleOutputController, totalTime);
		}
		return composeCoordinator;
	}

	private JButton makeButton(String name, String actionCommand) {
		return makeButton(name, actionCommand, -1, -1);
	}

	private JButton makeButton(String name, String actionCommand, int width, int height) {
		JButton butt = new JButton(name);
		butt.addActionListener(this);
		butt.setActionCommand(actionCommand);
		if (width > 0 && height > 0) {
			butt.setPreferredSize(new Dimension(width, height));
			butt.setMargin(new Insets(0, 0, 0, 0));
		}
		return butt;
	}

	private void openDrumViewPopup() {
		new DrumLoopPopup(drumGUI.getPanels());
	}

	private void openApplyCustomSectionPopup() {
		if (arrangementGUI.getSelectedSectionIndex() > 0) {
			new ApplyCustomSectionPopup(VibeComposerGUI::getInstList,
					arrangementGUI::handleArrangementAction,
					arrangementGUI.getSelectedSectionIndex(),
					arrangementGUI.getSectionNamesFromSelectedIndex(),
					arrangementGUI::getSelectedActualSection);
		}
	}

	private void openDebugConsole() {
		try {
			consoleOutputController.openDebugConsole();
		} catch (Exception e) {
			// Auto-generated catch block
			LG.e(e);
		}
	}

	// Deal with the window closebox
	public void windowClosing(WindowEvent we) {
		int confirmed = JOptionPane.showConfirmDialog(this, "Exit VibeComposer?",
				"Exit Program Message Box", JOptionPane.YES_NO_OPTION);

		if (confirmed == JOptionPane.YES_OPTION) {
			if (sequencer != null) {
				playbackController.stopMidi();
				sequencer.close();
			}
			System.exit(0);
			//dispose();
		}
	}

	// other WindowListener interface methods
	// they do nothing but are required to be present
	public void windowActivated(WindowEvent we) {
	};

	public void windowClosed(WindowEvent we) {
	};

	public void windowDeactivated(WindowEvent we) {
	};

	public void windowIconified(WindowEvent we) {
	};

	public void windowDeiconified(WindowEvent we) {
	};

	public void windowOpened(WindowEvent we) {
	};

	public void itemStateChanged(ItemEvent ie) {
	}

	// Deal with Action events (button pushes)

	public void actionPerformed(ActionEvent ae) {
		actionPerformedTask(ae);
		/*Thread actionThread = new Thread(() -> {
			actionPerformedTask(ae);
		});
		actionThread.start();*/
	}

	private void refreshBannedInstruments() {
		InstComboBox.BANNED_INSTS.clear();
		InstComboBox.BANNED_INSTS.addAll(Arrays.asList(ExtraSettingsGUI.bannedInsts.getText().split(",")));
	}

	private void initializeInstrumentPools() {
		refreshBannedInstruments();
		if (ExtraSettingsGUI.useAllInsts.isSelected()) {
			InstUtils.initAllInsts();
		} else {
			InstUtils.initNormalInsts();
		}
		for (int i = 0; i < melodyGUI.getPanels().size(); i++) {
			int inst = melodyGUI.getPanels().get(i).getInstrumentBox().getInstrument();
			melodyGUI.getPanels().get(i).getInstrumentBox().initInstPool(InstUtils.POOL.MELODY);
			melodyGUI.getPanels().get(i).getInstrumentBox().setInstrument(inst);
		}
		for (int i = 0; i < bassGUI.getPanels().size(); i++) {
			int inst = bassGUI.getPanels().get(i).getInstrumentBox().getInstrument();
			bassGUI.getPanels().get(i).getInstrumentBox().initInstPool(InstUtils.POOL.BASS);
			bassGUI.getPanels().get(i).getInstrumentBox().setInstrument(inst);
		}
	}

	public void actionPerformedTask(ActionEvent ae) {
		String actionCommand = ae.getActionCommand();
		boolean tabPanePossibleChange = false;
		boolean soloMuterPossibleChange = false;
		boolean triggerRegenerate = false;

		LG.i(("<<<<<<<<<<<<<<<<Processing '" + actionCommand + "'>>>>>>>>>>>>>>>>>>"));
		long actionSystemTime = System.currentTimeMillis();

		boolean isCompose = "Compose".equals(actionCommand);
		boolean isRegenerate = "Regenerate".equals(actionCommand);
		if (heavyBackgroundTasksInProgress) {
			LG.i("Cannot process action '" + actionCommand + "', composing in progress!");
			new TemporaryInfoPopup("Composing in progress..", 1000);
			return;
		}

		refreshBannedInstruments();

		if ("RandomizeInst".equals(actionCommand)) {
			generationGUI.randomizeInstruments();
			triggerRegenerate = true;
		}
		if (isCompose && generationGUI.randomizeInstOnComposeOrGen.isSelected()) {
			generationGUI.randomizeInstruments();
		}

		if (isCompose || isRegenerate) {
			soloMuterPossibleChange = true;
		}

		if (isCompose && getInstrumentControls(INST.CHORD).getEnabledCheckBox().isSelected()
				&& chordGUI.randomChordsGenerateOnCompose.isSelected()) {
			instrumentPanelController.generatePanels(INST.CHORD);
		}
		if (isCompose && getInstrumentControls(INST.ARP).getEnabledCheckBox().isSelected()
				&& arpGUI.randomArpsGenerateOnCompose.isSelected()) {
			instrumentPanelController.generatePanels(INST.ARP);
		}

		if (isCompose && getInstrumentControls(INST.DRUM).getEnabledCheckBox().isSelected()
				&& drumGUI.randomDrumsGenerateOnCompose.isSelected()) {
			instrumentPanelController.generatePanels(INST.DRUM);
		}

		if ("RandomizeTranspose".equals(actionCommand)) {
			Random instGen = new Random();
			scoreGUI.setTranspose(instGen.nextInt(12) - 6);
			triggerRegenerate = true;
		}

		if (isCompose && generationGUI.randomizeTransposeOnCompose.isSelected()) {
			Random instGen = new Random();
			scoreGUI.setTranspose(instGen.nextInt(12) - 6);
		}


		// midi generation
		if (isCompose || isRegenerate) {

			switchMidiButtons(false);
			composeMidi(isRegenerate, true);
			switchMidiButtons(true);
			repaint();
			/*SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
				@Override
				protected Void doInBackground()
						throws InterruptedException, MidiUnavailableException, IOException {
					try {

					} catch (Throwable ex) {
						LG.e(ex);
						return null;
					}

					return null;
				}

				@Override
				protected void done() {


					//sizeRespectingPack();

				}
			};*/
			soloMuterPossibleChange = true;
			//worker.execute();
		}

		if ("LoadGUIConfig".equals(actionCommand)) {
			GUIConfig loadedConfig = presetViewController.loadConfigFile(this);
			if (loadedConfig != null) {
				playbackController.stopMidi();
				guiConfig = loadedConfig;
				copyConfigToGUI(guiConfig);
				repaint();
			}
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
		}

		if (actionCommand.startsWith("Arrangement")) {
			Random arrGen = new Random();
			arrangementGUI.handleArrangementAction(actionCommand, arrGen.nextInt(),
					Integer.valueOf(ArrangementGUI.pieceLength.getText()));
			tabPanePossibleChange = true;
		}

		// recalcs
		if (tabPanePossibleChange) {
			recalculateTabPaneCounts();
			recalculateGenerationCounts();
		}
		if (soloMuterPossibleChange) {
			recalculateSoloMuters();
		}

		if (triggerRegenerate && generationGUI.canRegenerateOnChange()) {
			regenerate();
		}

		LG.i("Finished '" + actionCommand + "' in: "
				+ (System.currentTimeMillis() - actionSystemTime) + " ms");
		mainWindowControls.getMessageLabel().setText("::" + actionCommand + "::");
	}

	public void recalculateSoloMuters() {
		soloMuteController.recalculatePanels();
	}

	private void assignSequenceTrack(int instrument, int panelOrder, int trackNumber) {
		instrumentPanelController.getPanelByOrder(INST.fromIndex(instrument), panelOrder)
				.setSequenceTrack(trackNumber);
	}

	private void clearAllSeeds() {
		generationGUI.randomSeed.setValue(0);
		for (INST instrument : INST.values()) {
			getInstList(instrument).forEach(e -> e.setPatternSeed(0));
		}
		ArrangementGUI.arrangementSeed.setValue(0);
	}

	@Override
	public void onSoloToggled(SoloMuter soloMuter) {
		soloMuteController.onSoloToggled(soloMuter);
	}

	@Override
	public void onMuteToggled(SoloMuter soloMuter) {
		soloMuteController.onMuteToggled(soloMuter);
	}

	@Override
	public SoloMuter getGlobalSoloMuter() {
		return mainWindowControls.getGlobalSoloMuter();
	}

	@Override
	public SoloMuter getGroupSoloMuter(int instrumentIndex) {
		return mainWindowControls.getGroupSoloMuter(instrumentIndex);
	}

	public void recalculateGeneratorAndTabCounts() {
		recalculateGenerationCounts();
		recalculateTabPaneCounts();
	}

	public void recalculateGenerationCounts() {
		for (INST instrument : INST.values()) {
			getInstrumentControls(instrument).getRandomPanelsToGenerate()
					.setText("" + Math.max(1, getInstList(instrument).size()));
		}
	}

	public void recalculateTabPaneCounts() {
		if (instrumentTabPane.getComponentCount() < 7) {
			return;
		}
		instrumentTabPane.setTitleAt(0, "Melody (" + melodyGUI.getPanels().size() + ")");
		instrumentTabPane.setTitleAt(1, " Bass  (" + bassGUI.getPanels().size() + ")");
		instrumentTabPane.setTitleAt(2, "Chords (" + chordGUI.getPanels().size() + ")");
		instrumentTabPane.setTitleAt(3, " Arps  (" + arpGUI.getPanels().size() + ")");
		instrumentTabPane.setTitleAt(4, " Drums (" + drumGUI.getPanels().size() + ")");
		instrumentTabPane.setTitleAt(5, "Arrangement (" + ArrangementGUI.arrangement.getSections().size() + ")");
		instrumentTabPane.setTitleAt(6,
				"Generated Arrangement (" + ArrangementGUI.actualArrangement.getSections().size() + ")");
		if (instrumentTabPane.getComponentCount() >= 8) {
			instrumentTabPane.setTitleAt(7, " Score ");
		}
	}

	public void copyGUItoConfig(GUIConfig gc) {
		copyGUItoConfig(gc, false);
	}

	public void copyGUItoConfig(GUIConfig gc, boolean isNew) {
		gc.setVersion(CURRENT_VERSION);
		gc.setRandomSeed(generationGUI.lastRandomSeed);
		gc.setMidiMode(mainWindowControls.getMidiMode().isSelected());
		gc.setBpm(Double.valueOf(generationGUI.mainBpm.getInt()));
		gc.setScaleMode(ScaleMode.valueOf(generationGUI.scaleMode.getVal()));

		arrangementGUI.saveToConfig(gc, isNew, generationGUI.lastRandomSeed, guiConfig.getPatternMaps());
		melodyGUI.saveToConfig(gc, generationGUI.lastRandomSeed);
		bassGUI.saveToConfig(gc, generationGUI.lastRandomSeed);
		chordGUI.saveToConfig(gc, generationGUI.lastRandomSeed);
		arpGUI.saveToConfig(gc, generationGUI.lastRandomSeed);
		drumGUI.saveToConfig(gc, generationGUI.lastRandomSeed,
				mainWindowControls.getMidiMode().isSelected()
						&& !mainWindowControls.getMidiModeDevices().getVal().contains("ervill"));
		scoreGUI.saveToConfig(gc);
		generationGUI.saveToConfig(gc);
		ExtraSettingsGUI.saveToConfig(gc);
	}

	public void copyConfigToGUI(GUIConfig gc) {
		if (!CURRENT_VERSION.equals(gc.getVersion())) {
			LG.w("Loaded file is for an older version of VibeComposer! Curremt: " + CURRENT_VERSION + ", File version: " + gc.getVersion());
			new TemporaryInfoPopup("Loaded file is for an older version of VibeComposer! Not all features may work the same as they did then!", 2500);
		}

		// Prepare application state and clear transient controls before module settings load.
		ArrangementGUI.arrSection.setVisible(false);
		ArrangementGUI.arrSection.setSelectedIndex(0);
		melodyGUI.randomMelodyOnRegenerate.setSelected(false);
		generationGUI.randomSeed.setValue((int) gc.getRandomSeed());
		generationGUI.lastRandomSeed = generationGUI.randomSeed.getValue();
		mainWindowControls.getMidiMode().setSelected(gc.isMidiMode());
		generationGUI.scaleMode.setVal(gc.getScaleMode().toString());

		// Restore each module's controls and models before recreating its panels.
		arrangementGUI.loadFromConfig(gc);
		melodyGUI.loadFromConfig(gc);
		bassGUI.loadFromConfig(gc);
		chordGUI.loadFromConfig(gc);
		arpGUI.loadFromConfig(gc);
		drumGUI.loadFromConfig(gc);
		scoreGUI.loadFromConfig(gc);
		generationGUI.loadFromConfig(gc);
		ExtraSettingsGUI.loadFromConfig(gc);

		int bpm = (int) Math.round(gc.getBpm());
		generationGUI.mainBpm.getKnob().setMin(Math.min(generationGUI.mainBpm.getKnob().getMin(), bpm));
		generationGUI.mainBpm.getKnob().setMax(Math.max(generationGUI.mainBpm.getKnob().getMax(), bpm));
		generationGUI.mainBpm.setInt(bpm);

		melodyGUI.loadPartsFromConfig(gc, parts -> instrumentPanelController.recreatePanels(
				INST.MELODY, parts));
		bassGUI.loadPartsFromConfig(gc, parts -> instrumentPanelController.recreatePanels(
				INST.BASS, parts));
		chordGUI.loadPartsFromConfig(gc, parts -> instrumentPanelController.recreatePanels(
				INST.CHORD, parts));
		arpGUI.loadPartsFromConfig(gc, parts -> instrumentPanelController.recreatePanels(
				INST.ARP, parts));
		drumGUI.loadPartsFromConfig(gc, parts -> instrumentPanelController.recreatePanels(
				INST.DRUM, parts));
        arrangementGUI.recalculatePartMapsAfterPartsLoaded();

		ArrangementGUI.arrSection.setVisible(true);
		if (MidiGenerator.chordInts.isEmpty()) {
			MidiGenerator.chordInts = chordGUI.userChords.getChordList();
		}
	}

	// -------------- GENERIC INST PANEL METHODS ----------------------------

	public void configureRandomizeAction(InstPanel panel) {
		panel.setRandomizeAction(this::randomizePart);
	}

	public void configureInstrumentControlContext(InstPanel panel) {
		panel.setInstrumentControlContext(instrumentControlContext);
	}

	private void configureKnobContexts(Container container) {
		for (Component component : container.getComponents()) {
			if (component instanceof KnobPanel) {
				((KnobPanel) component).getKnob()
						.setInstrumentControlContext(instrumentControlContext);
			}
			if (component instanceof Container) {
				configureKnobContexts((Container) component);
			}
		}
	}

	public void configureInstPanelContext(InstPanel panel) {
		panel.setContext(instPanelContext, instrumentPanelController);
	}

	private void randomizePart(InstPanel panel) {
		String actionName = "RandomizePart";
		long actionSystemTime = System.currentTimeMillis();
		LG.i(("<<<<<<<<<<<<<<<<Processing '" + actionName + "'>>>>>>>>>>>>>>>>>>"));
		if (heavyBackgroundTasksInProgress) {
			LG.i("Cannot process action '" + actionName + "', composing in progress!");
			new TemporaryInfoPopup("Composing in progress..", 1000);
			return;
		}

		refreshBannedInstruments();
		instrumentPanelController.randomizePanel(panel);
		if (generationGUI.canRegenerateOnChange()) {
			regenerate();
		}

		LG.i("Finished '" + actionName + "' in: "
				+ (System.currentTimeMillis() - actionSystemTime) + " ms");
		mainWindowControls.getMessageLabel().setText("::" + actionName + "::");
	}

	public String getFilenameForSaving(String oldName) {
		return oldName.replaceFirst("bpm[0-9]{1,3}_", "bpm" + generationGUI.mainBpm.getInt() + "_");
	}
}
