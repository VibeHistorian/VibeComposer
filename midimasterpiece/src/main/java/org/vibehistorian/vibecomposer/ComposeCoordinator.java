package org.vibehistorian.vibecomposer;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Panels.MelodyPanel;
import org.vibehistorian.vibecomposer.Popups.TemporaryInfoPopup;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Synthesizer;
import javax.swing.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.vibehistorian.vibecomposer.ApplicationSessionState.*;
import static org.vibehistorian.vibecomposer.PlaybackState.*;
import static org.vibehistorian.vibecomposer.SoloMuteState.needToRecalculateSoloMuters;
import static org.vibehistorian.vibecomposer.SoloMuteState.needToRecalculateSoloMutersAfterSequenceGenerated;

/** Coordinates the ordered preparation, generation, and playback setup workflow. */
public final class ComposeCoordinator {
    public interface Context {
        void copyGuiToConfig(GUIConfig config, boolean isNew);
        void assignSequenceTrack(int instrument, int panelOrder, int trackNumber);
        int sliderMeasureWidth();
        int delayed();
        int selectedInstrumentTab();
        void repaintMainWindow();
        void recalculateTabPaneCounts();
        void setHeavyBackgroundTaskInProgress(boolean inProgress);
    }

    private final Context context;
    private final PlaybackController playbackController;
    private final MidiDeviceController midiDeviceController;
    private final MidiCcController midiCcController;
    private final SoloMuteController soloMuteController;
    private final MainWindowControls mainWindowControls;
    private final MelodyGUI melodyGUI;
    private final ChordGUI chordGUI;
    private final ArpGUI arpGUI;
    private final DrumGUI drumGUI;
    private final GenerationGUI generationGUI;
    private final ArrangementGUI arrangementGUI;
    private final ScoreGUI scoreGUI;
    private final MidiEditorSession midiEditorSession;
    private final ConsoleOutputController consoleOutputController;
    private final JLabel totalTime;

    private final boolean logPerformance = false;

    public ComposeCoordinator(Context context, PlaybackController playbackController,
                              MidiDeviceController midiDeviceController,
                              MidiCcController midiCcController,
                              SoloMuteController soloMuteController,
                              MainWindowControls mainWindowControls,
                              MelodyGUI melodyGUI, ChordGUI chordGUI, ArpGUI arpGUI,
                              DrumGUI drumGUI,
                              GenerationGUI generationGUI,
                              ArrangementGUI arrangementGUI, ScoreGUI scoreGUI,
                              MidiEditorSession midiEditorSession,
                              ConsoleOutputController consoleOutputController,
                              JLabel totalTime) {
        this.context = context;
        this.playbackController = playbackController;
        this.midiDeviceController = midiDeviceController;
        this.midiCcController = midiCcController;
        this.soloMuteController = soloMuteController;
        this.mainWindowControls = mainWindowControls;
        this.melodyGUI = melodyGUI;
        this.chordGUI = chordGUI;
        this.arpGUI = arpGUI;
        this.drumGUI = drumGUI;
        this.generationGUI = generationGUI;
        this.arrangementGUI = arrangementGUI;
        this.scoreGUI = scoreGUI;
        this.midiEditorSession = midiEditorSession;
        this.consoleOutputController = consoleOutputController;
        this.totalTime = totalTime;
    }

    public void composeMidi(boolean regenerate, boolean manual) {
        LG.i("==========Compose Midi [" + (regenerate ? "Regenerate" : "Compose") + "|"
                + (manual ? "Manual" : "OnChange") + "]: Starting...================");
        context.setHeavyBackgroundTaskInProgress(true);
        long systemTime = System.currentTimeMillis();

        try {
            if (sequencer != null) {
                sequencer.stop();
                playbackController.flushMidiEvents();
                partAndOrderLastNoteIndexes.clear();
            }

            if (ArrangementGUI.manualArrangement.isSelected()
                    && (ArrangementGUI.actualArrangement.getSections().isEmpty()
                    || ArrangementGUI.actualArrangement.getSections().stream().noneMatch(Section::hasPresence))) {
                LG.i("Nothing to compose! Uncheck MANUAL arrangement!");
                new TemporaryInfoPopup("Nothing to compose! Uncheck MANUAL arrangement!", 3000);
                context.setHeavyBackgroundTaskInProgress(false);
                return;
            }

            playbackController.saveStartInfo();
            mainWindowControls.getSavedIndicatorLabel().setVisible(false);
            midiDeviceController.prepareForComposition();

            needToRecalculateSoloMuters = true;

            Integer masterpieceSeed = prepareMainSeed(regenerate);
            int regenerateCount = regenerate ? guiConfig.getRegenerateCount() + 1 : 0;

            prepareUI(regenerate, manual);
            if (logPerformance) {
                LG.i("After prepareUI: " + (System.currentTimeMillis() - systemTime));
            }
            GUIConfig midiConfig = new GUIConfig();
            context.copyGuiToConfig(midiConfig, true);

            MidiGenerator melodyGenerator = new MidiGenerator(midiConfig, context::assignSequenceTrack,
                    consoleOutputController);
            midiEditorSession.setMelodyGenerator(melodyGenerator);
            fillUserParameters(regenerate, manual);

            File makeDir = new File(Constants.MIDIS_FOLDER);
            makeDir.mkdir();
            makeDir = new File(Constants.MIDI_HISTORY_FOLDER);
            makeDir.mkdir();

            String seedData = "" + masterpieceSeed;
            if (!melodyGUI.getPanels().isEmpty() && melodyGUI.getPanels().get(0).getPatternSeed() != 0
                    && !melodyGUI.getPanels().get(0).getMuteInst()) {
                seedData += "_" + melodyGUI.getPanels().get(0).getPatternSeed();
            }
            String keyTrans = MidiUtils.SEMITONE_LETTERS.get((scoreGUI.getTranspose() + 120) % 12)
                    .replace("#", "s");

            String fileName = "bpm" + generationGUI.mainBpm.getInt() + "_" + keyTrans + "_" + generationGUI.scaleMode.getVal()
                    + "_seed" + seedData;
            String relPath = Constants.MIDI_HISTORY_FOLDER + "/" + fileName + ".mid";

            // unapply S/M, generate, reapply S/M with new track numbering
            soloMuteController.unapplyTracks();

            if (logPerformance) {
                LG.i("After setup: " + (System.currentTimeMillis() - systemTime));
            }
            melodyGenerator.generateMasterpiece(masterpieceSeed, relPath);

            guiConfig = midiConfig;
            soloMuteController.reapplyTracks();

            cleanUpUIAfterCompose(regenerate);

            if (logPerformance) {
                LG.i("After cleanup: " + (System.currentTimeMillis() - systemTime));
            }

            ScrollComboBox<GUIConfig> configHistory = mainWindowControls.getConfigHistory();
            if (ExtraSettingsGUI.configHistoryStoreRegeneratedTracks.isSelected() || !regenerate
                    || configHistory.getItemCount() == 0) {
                midiConfig.setCustomChords(StringUtils.join(MidiGenerator.chordInts, ","));
                midiConfig.setRegenerateCount(regenerateCount);
                configHistory.addItem(midiConfig);
                configHistory.setSelectedIndex(configHistory.getItemCount() - 1);
                if (configHistory.getItemCount() > 10) {
                    configHistory.removeItemAt(0);
                }
            } else {
                // without 'configHistoryStoreRegeneratedTracks', regenerate will try to replace last history instead of creating new entries each time
                midiConfig.setCustomChords(StringUtils.join(MidiGenerator.chordInts, ","));
                midiConfig.setRegenerateCount(regenerateCount);
                String oldBookmarkText = configHistory.getItemCount() > 0
                        ? configHistory.getLastVal().getBookmarkText()
                        : "";
                if (StringUtils.isEmpty(oldBookmarkText)) {
                    configHistory.removeItemAt(configHistory.getItemCount() - 1);
                }
                configHistory.addItem(midiConfig);
                configHistory.setSelectedIndex(configHistory.getItemCount() - 1);
            }

            try (FileWriter fw = new FileWriter("randomSeedHistory.txt", true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 PrintWriter out = new PrintWriter(bw)) {
                out.println(new Date() + ", Seed: " + seedData);
            } catch (IOException e) {
                LG.i("Failed to write into Random Seed History..");
            }

            handleGeneratedMidi(regenerate, relPath, systemTime);
            currentBeatMultiplier = generationGUI.beatDurationMultiplier.getSelectedItem();
            arrangementGUI.resetSectionSelectionAfterGeneration();
            context.setHeavyBackgroundTaskInProgress(false);

        } catch (Exception e) {
            LG.e("Exception during midi generation! Cause: " + e.getMessage(), e);
            context.setHeavyBackgroundTaskInProgress(false);
            new TemporaryInfoPopup(Constants.BUG_HUNT_MESSAGE, null);
            if (sequencer != null && sequencer.isRunning()) {
                sequencer.stop();
            }
            loopBeat.setSelected(false);
            soloMuteController.reapplyTracks();
            return;
        }
        LG.i("================== ComposeCoordinator::composeMidi time: "
                + (System.currentTimeMillis() - systemTime) + " ms ==========================");
    }

    private void fillUserParameters(boolean regenerate, boolean manual) {
        try {
            MidiGenerator.COLLAPSE_DRUM_TRACKS = drumGUI.combineDrumTracks.isSelected();
            MidiGenerator.recalculateDurations(ExtraSettingsGUI.stretchMidi.getInt());
            MidiGenerator.GLOBAL_DURATION_MULTIPLIER = ExtraSettingsGUI.globalNoteLengthMultiplier.getInt() / 1000.0;
            MelodyGenerator.RANDOMIZE_TARGET_NOTES = !regenerate
                    && melodyGUI.melodyTargetNotesRandomizeOnCompose.isSelected();
            MelodyGenerator.TARGET_NOTES = (melodyGUI.melody1ForcePatterns.isSelected()
                    && !melodyGUI.getPanels().isEmpty()
                    && !melodyGUI.getPanels().get(0).getNoteTargetsButton().isEnabled())
                    ? melodyGUI.getPanels().stream()
                    .collect(Collectors.toMap(MelodyPanel::getPanelOrder,
                            MelodyPanel::getChordNoteChoices))
                    : null;

            MidiGenerator.START_TIME_DELAY = MidiGenerator.Durations.QUARTER_NOTE;
            MidiGenerator.FIRST_CHORD = ChordGUI.chordSelect(chordGUI.firstChordSelection.getVal());
            MidiGenerator.LAST_CHORD = ChordGUI.chordSelect(chordGUI.lastChordSelection.getVal());

            boolean customChords = chordGUI.userChordsEnabled.isSelected()
                    && !chordGUI.userChords.getChordletsRaw().isEmpty();
            if (customChords || chordGUI.userDurationsEnabled.isSelected()) {
                List<String> chords = chordGUI.userChords.getChordList();

                MidiGenerator.userChordsDurations = chordGUI.getUserChordDurations();

                if (customChords) {
                    MidiGenerator.userChords = chords;
                } else {
                    MidiGenerator.userChords.clear();
                }
            } else {
                MidiGenerator.userChords.clear();
                MidiGenerator.userChordsDurations.clear();
            }

            if (melodyGUI.getUserMelody() != null && melodyGUI.useUserMelody.isSelected()) {
                MelodyGenerator.userMelody = melodyGUI.getUserMelody();
            } else {
                MelodyGenerator.userMelody = null;
            }
        } catch (Exception e) {
            LG.i("User screwed up his inputs!");
            LG.e(e);
        }
    }

    private Integer prepareMainSeed(boolean regenerate) {
        int masterpieceSeed = 0;
        int parsedSeed = generationGUI.randomSeed.getValue();

        if (regenerate) {
            masterpieceSeed = generationGUI.lastRandomSeed;
            if (parsedSeed != 0) {
                masterpieceSeed = parsedSeed;
            }
        }

        if (masterpieceSeed != 0) {
            LG.i("Skipping, regenerated seed: " + masterpieceSeed);
        } else if (parsedSeed != 0) {
            masterpieceSeed = parsedSeed;
        } else {
            Random seedGenerator = new Random();
            masterpieceSeed = seedGenerator.nextInt();
        }

        LG.i("Master seed: " + masterpieceSeed);
        generationGUI.lastRandomSeed = masterpieceSeed;
        return masterpieceSeed;
    }

    private void prepareUI(boolean regenerate, boolean manual) {
        if (!regenerate && generationGUI.randomizeBpmOnCompose.isSelected()) {
            generationGUI.randomizeBpm();
        }

        // MELODY
        melodyGUI.generateRandomMelodyPanelsOnCompose(regenerate, generationGUI.lastRandomSeed);

        if (!regenerate && ExtraSettingsGUI.randomizeTimingsOnCompose.isSelected()) {
            if (generationGUI.globalSwingOverride.isSelected()) {
                generationGUI.globalSwingOverrideValue
                        .setInt(50 + new Random().nextInt(drumGUI.randomDrumMaxSwingAdjust.getInt() * 2 + 1)
                                - drumGUI.randomDrumMaxSwingAdjust.getInt());
            }
            double randomBeatMultiplier = new Random().nextDouble();
            if (randomBeatMultiplier < 0.85) {
                generationGUI.beatDurationMultiplier.setSelectedIndex(1);
            } else if (randomBeatMultiplier < 0.95) {
                generationGUI.beatDurationMultiplier.setSelectedIndex(0);
            } else {
                generationGUI.beatDurationMultiplier.setSelectedIndex(2);
            }
        }

        if (!regenerate && ExtraSettingsGUI.sidechainPatternsOnCompose.isSelected()) {
            generationGUI.sidechainPatterns(false, false);
        }

        melodyGUI.prepareMelodyPatterns(regenerate, manual, generationGUI.lastRandomSeed);

        // BASS

        // ARPS
        if (context.selectedInstrumentTab() != 3 && arpGUI.arpCopyMelodyInst.isSelected()
                && !melodyGUI.getPanels().isEmpty() && !melodyGUI.getPanels().get(0).getMuteInst()) {
            if (!arpGUI.getPanels().isEmpty() && !arpGUI.getPanels().get(0).getLockInst()) {
                arpGUI.getPanels().get(0).getInstrumentBox().initInstPool(org.vibehistorian.vibecomposer.InstUtils.POOL.MELODY);
                arpGUI.getPanels().get(0).setInstPool(org.vibehistorian.vibecomposer.InstUtils.POOL.MELODY);
                arpGUI.getPanels().get(0).setInstrument(melodyGUI.getPanels().get(0).getInstrument());
            }
        }

        if (!regenerate && mainWindowControls.getRandomizeScaleModeOnCompose().isSelected()) {
            Integer[] allowedScales = new Integer[] { 0, 1, 3, 4, 5, 8 };
            generationGUI.scaleMode.setSelectedIndex(allowedScales[new Random().nextInt(allowedScales.length)]);
        }

        arrangementGUI.prepareForCompose(regenerate, currentMidi != null, generationGUI.lastRandomSeed);

        if (midiEditorSession.isVisible()) {
            LG.i("MidiEditPopup is open - saving!");
            midiEditorSession.saveNotesBeforeCompose();
        }
    }

    private void cleanUpUIAfterCompose(boolean regenerate) {
        chordGUI.applyGeneratedChords(MidiGenerator.chordInts,
                melodyGUI.getUserMelody() != null, guiConfig);
        melodyGUI.applyGeneratedTargetNotes(regenerate, MelodyGenerator.TARGET_NOTES, guiConfig);
        arpGUI.applyGeneratedPatterns(MidiGenerator.gc.getArpParts());
        arrangementGUI.applyGeneratedArrangement(MidiGenerator.gc.getActualArrangement(), guiConfig);
        scoreGUI.pianoRoll();
        if (midiEditorSession.isVisible()) {
            midiEditorSession.refreshAfterCompose(ArrangementGUI.actualArrangement.getSections());
        } else {
            LG.d("No midi editor is open!");
        }
    }

    private void handleGeneratedMidi(boolean regenerate, String relPath, long systemTime) {
        boolean logPerformance = false;

        currentMidi = null;
        currentSequenceMidi = null;
        try {
            if (sequencer != null) {
                sequencer.stop();
            }
            Synthesizer synthesizer = null;
            if (!mainWindowControls.getMidiMode().isSelected()) {
                synthesizer = midiDeviceController.loadSynth();
            }

            if (sequencer == null) {
                sequencer = MidiSystem.getSequencer(synthesizer == null);
                if (sequencer == null) {
                    LG.e("Sequencer device not supported");
                    return;
                }
                sequencer.open();
            }

            if (logPerformance) {
                LG.i("After sequencer setup: " + (System.currentTimeMillis() - systemTime));
            }

            currentMidi = new File(relPath);
            if (!currentMidi.exists()) {
                new TemporaryInfoPopup("Error: Could not load currently generated MIDI file!", 3000);
                return;
            }
            currentSequenceMidi = new File(Constants.TEMPORARY_SEQUENCE_MIDI_NAME);
            mainWindowControls.getGeneratedMidi().setListData(new File[] { currentMidi });
            context.repaintMainWindow();
            if (!midiDeviceController.prepareMidiPlayback(currentSequenceMidi, synthesizer)) {
                return;
            }

            if (logPerformance) {
                LG.i("After prepare midi playback: " + (System.currentTimeMillis() - systemTime));
            }

            playbackController.resetSequencerTickPosition();

            totalTime.setText(OMNI.microsecondsToTimeString(sequencer.getMicrosecondLength()));
            slider.setMaximum((int) (sequencer.getMicrosecondLength() / 1000));
            slider.setPaintTicks(true);
            int measureWidth = context.sliderMeasureWidth();
            int delayed = context.delayed();
            slider.setTickStart(delayed);
            Dictionary<Integer, JLabel> table = new Hashtable<>();

            double fullMeasureNoteDuration = MidiGenerator.GENERATED_MEASURE_LENGTH;
            sliderMeasureStartTimes = new ArrayList<>();
            sliderBeatStartTimes = new ArrayList<>();

            int current = delayed;
            int sectIndex = 0;
            int realIndex = 1;
            Section prevSec = null;
            int sectionMaxText = Math.max(20 - ArrangementGUI.actualArrangement.getSections().size(), 3);
            int explored = 0;
            int exploredSize = 0;
            boolean endDisplayed = false;
            while (current < slider.getMaximum() && !endDisplayed) {
                Section sec = null;

                int sizeCounter = exploredSize;

                for (int i = explored; i < ArrangementGUI.actualArrangement.getSections().size(); i++) {
                    Section arrSec = ArrangementGUI.actualArrangement.getSections().get(i);
                    if (sizeCounter == sectIndex
                            || (sectIndex < sizeCounter + arrSec.getMeasures())) {
                        sec = arrSec;
                        explored = i;
                        exploredSize = sizeCounter;
                        break;
                    }
                    sizeCounter += arrSec.getMeasures();
                }
                sliderMeasureStartTimes.add(current);
                sliderBeatStartTimes.add(current);
                if (sec != null) {
                    List<Double> customDurations = (sec.getSectionBeatDurations() != null)
                            ? sec.getSectionBeatDurations()
                            : MidiGenerator.userChordsDurations;
                    if (!customDurations.isEmpty()) {
                        double adjustment = (measureWidth * customDurations.get(0)
                                / fullMeasureNoteDuration);
                        for (int i = 1; i < customDurations.size(); i++) {
                            sliderBeatStartTimes.add((int) (current + adjustment));
                            adjustment += (measureWidth * customDurations.get(i)
                                    / fullMeasureNoteDuration);
                        }
                    } else {
                        for (int i = 1; i < MidiGenerator.chordInts.size(); i++) {
                            sliderBeatStartTimes.add(
                                    current + (i * measureWidth) / MidiGenerator.chordInts.size());
                        }
                    }

                }

                String sectionText = "END";
                if (sec != null) {
                    boolean shorterSection = (sec.getSectionDuration() > 0)
                            && (sec.getSectionDuration() < fullMeasureNoteDuration - 0.05);
                    int originalLength = sec.getType().length();
                    int realMax = (shorterSection) ? 3 : sectionMaxText - 1;
                    int showMax = Math.min(realMax, originalLength);
                    String showLast = (showMax < originalLength)
                            ? (sec.getType().charAt(originalLength - 1) + "")
                            : "";
                    sectionText = realIndex + ":" + sec.getType().substring(0, showMax) + showLast
                            + (sec.hasCustomizedParts() ? "*" : "");
                } else {
                    endDisplayed = true;
                }
                if (sec != null && sec == prevSec && sec.getMeasures() > 1) {
                    // do not put into labels for followup measures
                } else {
                    table.put(current, new JLabel(sectionText));
                    realIndex++;
                }
                current = (int) (current + (((sec != null) && sec.getSectionDuration() > 0)
                        ? measureWidth * (sec.getSectionDuration() / fullMeasureNoteDuration)
                        : measureWidth));
                sectIndex++;
                prevSec = sec;
            }

            sliderExtended = Math.max(0, current - slider.getMaximum());
            if (endDisplayed) {
                sliderExtended -= measureWidth;
            } else {
                table.put(slider.getMaximum(), new JLabel("END"));
                sliderMeasureStartTimes.add(slider.getMaximum());
                sliderBeatStartTimes.add(slider.getMaximum());
            }

            slider.setCustomMajorTicks(sliderMeasureStartTimes);
            slider.setCustomMinorTicks(sliderBeatStartTimes);

            slider.setSnapToTicks(ExtraSettingsGUI.snapStartToBeat.isSelected());
            adjustSavedPositions();

            if (ExtraSettingsGUI.startFromBar.isSelected()) {
                int snapAdjustment = 50;
                if (startBeatCounter >= sliderBeatStartTimes.size()) {
                    startBeatCounter = 0;
                    pausedSliderPosition = 0;
                    pausedMeasureCounter = 0;
                    ArrangementGUI.arrSection.setSelectedIndex(0);
                }
                slider.setValue(sliderBeatStartTimes.get(startBeatCounter) + snapAdjustment);
            } else {
                int startVal = startSliderPosition;
                slider.setValue(startVal);
            }
            slider.setLabelTable(table);
            slider.setPaintLabels(true);

            if (loopBeat.isSelected()) {
                long startPos = ExtraSettingsGUI.startFromBar.isSelected() ? delayed : pausedSliderPosition;
                if (startPos < slider.getValue()) {
                    startPos = slider.getValue();
                    playbackController.midiNavigate(startPos, 0);
                } else {
                    playbackController.midiNavigate(startPos);
                }
                playbackController.resetPauseInfo();

            } else {
                String pauseBehavior = ExtraSettingsGUI.pauseBehaviorCombobox.getVal();
                if (!"NEVER".equalsIgnoreCase(pauseBehavior)) {
                    boolean unpause = regenerate || pauseBehavior.contains("compose");
                    unpause &= (pausedSliderPosition > 0
                            && pausedSliderPosition < slider.getMaximum() - 100);

                    if (unpause) {
                        long startPos = ExtraSettingsGUI.startFromBar.isSelected()
                                ? sliderMeasureStartTimes.get(pausedMeasureCounter)
                                : pausedSliderPosition;
                        if (startPos < slider.getValue()) {
                            startPos = slider.getValue();
                        }
                        playbackController.midiNavigate(startPos);
                    } else {
                        playbackController.resetPauseInfo();
                        int startPos = delayed / 2;
                        if (startPos < slider.getValue()) {
                            startPos = slider.getValue();
                        }
                        playbackController.midiNavigate(startPos);
                    }
                }
            }

            if (logPerformance) {
                LG.i("After slider setup: " + (System.currentTimeMillis() - systemTime));
            }

            if (sliderMeasureStartTimes.size() <= 1) {
                new TemporaryInfoPopup("Generation produced no playable notes with the current settings - you're vibing too extremely! Change something!", 3000);
                loopBeat.setSelected(false);
            } else {
                sequencer.start();
            }

            double divisor = 1;
            if (generationGUI.beatDurationMultiplier.getSelectedIndex() == 0) {
                divisor = 0.5;
            } else if (generationGUI.beatDurationMultiplier.getSelectedIndex() == 2) {
                divisor = 2;
            }
            generationGUI.loopBeatCount.getKnob()
                    .setMax(!MidiGenerator.userChordsDurations.isEmpty()
                            ? (int) Math.ceil(
                            OMNI.sumListDouble(MidiGenerator.userChordsDurations) / divisor)
                            : MidiGenerator.chordInts.size() * 4);
            midiCcController.startMidiCcThread();
            context.recalculateTabPaneCounts();
            sequencer.setTempoFactor(1);
            if (needToRecalculateSoloMutersAfterSequenceGenerated) {
                needToRecalculateSoloMuters = true;
                needToRecalculateSoloMutersAfterSequenceGenerated = false;
            }
            startBpm = generationGUI.mainBpm.getInt();
        } catch (MidiUnavailableException | InvalidMidiDataException ex) {
            LG.e(ex);
        }
    }

    private void adjustSavedPositions() {
        int currentBpm = generationGUI.mainBpm.getInt();
        if (currentBpm > 0 && startBpm > 0) {
            startSliderPosition = (currentBpm != startBpm)
                    ? (int) Math.ceil(startSliderPosition * startBpm / (double) currentBpm)
                    : startSliderPosition;
            startBpm = currentBpm;
            pausedSliderPosition = (currentBpm != pausedBpm)
                    ? (int) Math.ceil(pausedSliderPosition * pausedBpm / (double) currentBpm)
                    : pausedSliderPosition;
            pausedBpm = currentBpm;
        }
    }
}
