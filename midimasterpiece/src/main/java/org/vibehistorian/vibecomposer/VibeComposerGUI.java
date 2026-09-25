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
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.sun.media.sound.AudioSynthesizer;
import jm.music.data.Note;
import jm.music.data.Part;
import jm.music.data.Phrase;
import jm.music.tools.Mod;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.vibehistorian.vibecomposer.Components.*;
import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.Enums.BlockType;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.KeyChangeType;
import org.vibehistorian.vibecomposer.Enums.PatternJoinMode;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Enums.StrumType;
import org.vibehistorian.vibecomposer.Helpers.CheckBoxIcon;
import org.vibehistorian.vibecomposer.Helpers.FileTransferHandler;
import org.vibehistorian.vibecomposer.Helpers.MidiHandler;
import org.vibehistorian.vibecomposer.Helpers.PatternMap;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Helpers.UsedPattern;
import org.vibehistorian.vibecomposer.InstUtils.POOL;
import org.vibehistorian.vibecomposer.MidiGenerator.Durations;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.Panels.*;
import org.vibehistorian.vibecomposer.Panels.SoloMuter.State;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.BassPart;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Parts.Defaults.DrumDefaults;
import org.vibehistorian.vibecomposer.Parts.Defaults.DrumSettings;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Parts.Wrappers.ArpPartsWrapper;
import org.vibehistorian.vibecomposer.Parts.Wrappers.InstPartsWrapper;
import org.vibehistorian.vibecomposer.Popups.*;
import org.vibehistorian.vibecomposer.Section.SectionType;

import javax.sound.midi.*;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.swing.Timer;
import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.SoftBevelBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.vibehistorian.vibecomposer.Constants.instNames;

// main class
public class VibeComposerGUI extends JFrame
		implements ActionListener, ItemListener, WindowListener {

	private static final long serialVersionUID = -677536546851756969L;

	public static List<Image> SECTION_VARIATIONS_ICONS = new ArrayList<>();
	private static final String[] SECTION_VAR_ICON_NAMES = new String[] { "v0_skipChord.png",
			"v1_swapChords.png", "v2_swapMelody.png", "v3_melodySpeed.png", "v4_keyChange.png", };
	public static List<Image> SECTION_TRANSITION_ICONS = new ArrayList<>();
	private static final String[] SECTION_TRANSITION_ICON_NAMES = new String[] { "v5_transUp.png",
			"v6_transDown.png", "v7_transCut.png", "v8_halvedTempo.png" };
	public static List<Image> LOCK_COMPONENT_ICONS = new ArrayList<>();
	private static final String[] LOCK_COMPONENT_ICON_NAMES = new String[] { "lock.png",
			"toggle_lock.png", "lock_white.png", "toggle_lock_white.png" };

	public static GUIPreset defaultGuiPreset = null;

	@Deprecated public static VariationPopup __varPopup = null;
	public static MidiEditPopup currentMidiEditorPopup = null;
	public static int currentMidiEditorSectionIndex = 0;

	// COLORS
	public static Color panelColorHigh, panelColorLow;
	public static boolean isBigMonitorMode = false;
	public static boolean isDarkMode = true;
	private static boolean isFullMode = true;
	public static Color darkModeUIColor = Color.CYAN;
	public static Color lightModeUIColor = new Color(0, 90, 255);
	public static final Color COMPOSE_COLOR = new Color(180, 150, 90);
	public static final Color COMPOSE_COLOR_TEXT = new Color(220, 170, 60);
	public static final Color COMPOSE_COLOR_TEXT_LIGHT = new Color(255, 193, 85);
	public static final Color REGENERATE_COLOR_TEXT = new Color(220, 70, 60);
	public static final Color REGENERATE_COLOR_TEXT_LIGHT = new Color(150, 0, 0);
	public static Color toggledUIColor = Color.cyan;
	public static Color toggledComposeColor = COMPOSE_COLOR_TEXT;
	public static Color toggledRegenerateColor = REGENERATE_COLOR_TEXT;

	Color messageColorDarkMode = new Color(200, 200, 200);
	Color messageColorLightMode = new Color(120, 120, 200);
	@Deprecated Color __arrangementLightModeText = ArrangementGUI.arrangementLightModeText;
	@Deprecated public static int __arrangementDarkModeLowestColor = ArrangementGUI.arrangementDarkModeLowestColor;
	@Deprecated Color __arrangementDarkModeText = ArrangementGUI.arrangementDarkModeText;
	@Deprecated public static int __arrangementLightModeHighestColor = ArrangementGUI.arrangementLightModeHighestColor;

	private static Set<Component> toggleableComponents = new HashSet<>();

	private static Soundbank soundfont = null;
	private Synthesizer synth = null;
	private boolean isSoundbankSynth = false;
	private boolean needSoundbankRefresh = false;

	public static GUIConfig guiConfig = new GUIConfig();
	public static MidiGenerator melodyGen = null;
	public static ScrollComboBox<GUIConfig> configHistory = new ScrollComboBox<>(false);

	// instrument panels added into scrollpanes
	@Deprecated
	public static List<MelodyPanel> __melodyPanels = MelodyGUI.melodyPanels;
	@Deprecated public static List<BassPanel> __bassPanels = BassGUI.bassPanels;
	@Deprecated public static List<ChordPanel> __chordPanels = ChordGUI.chordPanels;
	@Deprecated public static List<ArpPanel> __arpPanels = ArpGUI.arpPanels;
	@Deprecated public static List<DrumPanel> __drumPanels = DrumGUI.drumPanels;

	public static List<InstPanel> getAffectedPanels(int inst) {
		List<InstPanel> affectedPanels = isCustomSection()
				? getSectionPanelList(inst)
				: (List<InstPanel>) getInstList(inst);
		return affectedPanels;
	}

	public static List<? extends InstPanel> getInstList(int order) {
		switch (order) {
		case 0:
			return MelodyGUI.melodyPanels;
		case 1:
			return BassGUI.bassPanels;
		case 2:
			return ChordGUI.chordPanels;
		case 3:
			return ArpGUI.arpPanels;
		case 4:
			return DrumGUI.drumPanels;
		}
		if (order < 0 || order > 4) {
			throw new IllegalArgumentException("Inst list order wrong.");
		}
		return null;
	}

	public static JScrollPane getInstPane(int order) {
		switch (order) {
		case 0:
			return MelodyGUI.melodyScrollPane;
		case 1:
			return BassGUI.bassScrollPane;
		case 2:
			return ChordGUI.chordScrollPane;
		case 3:
			return ArpGUI.arpScrollPane;
		case 4:
			return DrumGUI.drumScrollPane;
		}
		if (order < 0 || order > 4) {
			throw new IllegalArgumentException("Inst list order wrong.");
		}
		return null;
	}

	public static List<InstPanel> getSectionPanelList(int order) {
		JScrollPane viewPane = getInstPane(order);
		JPanel viewPanel = (JPanel) viewPane.getViewport().getView();
		List<InstPanel> sectionPanels = new ArrayList<>();
		for (Component c : viewPanel.getComponents()) {
			if (c instanceof InstPanel) {
				sectionPanels.add((InstPanel) c);
			}
		}
		return sectionPanels;
	}

	// Arrangement fields retained during the module migration.
	@Deprecated public static Arrangement __arrangement;
	@Deprecated public static Arrangement __actualArrangement;
	@Deprecated JPanel __arrangementSettings;
	@Deprecated KnobPanel __arrangementVariationChance;
	@Deprecated public static KnobPanel __arrangementPartVariationChance;
	@Deprecated public static CheckButton __manualArrangement;
	@Deprecated JTextField __pieceLength;
	@Deprecated RandomValueButton __arrangementSeed;
	@Deprecated public static CheckButton __useArrangement;
	@Deprecated JCheckBox __randomizeArrangementOnCompose;
	@Deprecated public static final String __GLOBAL = ArrangementGUI.GLOBAL;
	@Deprecated public static ArrangementSectionSelectorPanel __arrSection;
	@Deprecated public static JScrollPane __arrSectionPane;
	@Deprecated public static boolean __switchTabPaneAfterApply;
	@Deprecated public static boolean __switchTabPaneToScoreAfterApply;
	@Deprecated static JPanel __arrangementMiddleColoredPanel;
	@Deprecated ScrollComboBox<String> __newSectionBox;

	// instrument scrollers
	public static JTabbedPane instrumentTabPane = new JTabbedPane(JTabbedPane.TOP) {
		@Override
		public void setSelectedIndex(int index) {
			if (index >= getComponents().length) {
				index = getComponents().length - 1;
			}
			super.setSelectedIndex(index);
			instrumentTabUndoManager.saveToHistory(instrumentTabPane);
		}
	};
	@Deprecated public static JScrollPane __scoreScrollPane;
	@Deprecated public static ShowPanelBig __scorePanel;
	public static final int DEFAULT_WIDTH = 1600;
	public static final int DEFAULT_HEIGHT = 400;
	public static Dimension scrollPaneDimension = new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT);
	@Deprecated int __arrangementRowHeaderWidth = ArrangementGUI.arrangementRowHeaderWidth;
	public static final int TABLE_COLUMN_MIN_WIDTH = 80;

	@Deprecated
	public static JScrollPane __melodyScrollPane;
	@Deprecated public static JScrollPane __bassScrollPane;
	@Deprecated public static JScrollPane __chordScrollPane;
	@Deprecated public static JScrollPane __arpScrollPane;
	@Deprecated public static JScrollPane __drumScrollPane;

	@Deprecated
	public static JPanel __melodyParentPanel;
	@Deprecated public static JPanel __bassParentPanel;
	@Deprecated public static JPanel __chordParentPanel;
	@Deprecated public static JPanel __arpParentPanel;
	@Deprecated public static JPanel __drumParentPanel;

	@Deprecated JScrollPane __arrangementScrollPane;
	@Deprecated JScrollPane __arrangementActualScrollPane;
	@Deprecated public static JTable __scrollableArrangementTable;
	@Deprecated public static JTable __scrollableArrangementActualTable;
	@Deprecated public static boolean __arrangementTableColumnDragging = false;
	@Deprecated public static boolean __actualArrangementTableColumnDragging = false;

	@Deprecated JPanel __actualArrangementCombinedPanel;
	@Deprecated JPanel __arrangementCombinedPanel;
	@Deprecated public static JPanel __variationButtonsPanel;

	// arrangement subcells - copy dragging
	@Deprecated public static boolean __copyDragging = false;
	@Deprecated public static Triple<Integer, Integer, Integer> __highlightedTableCell = null;
	@Deprecated public static Triple<Integer, Integer, Integer> __copyDraggingOrigin = null;
	@Deprecated public static Point __arrangementActualTableMousePoint = null;
	@Deprecated UsedPattern __copyDraggedPattern = null;

	// instrument global settings
	JTextField __bannedInsts;
	JCheckBox __useAllInsts;
	JButton __reinitInstPools;

	// main title settings
	JLabel mainTitle;
	JLabel subTitle;

	// macro params
	ScrollComboBox<String> __soundbankFilename;

	public static ScrollComboBox<String> scaleMode;
	JCheckBox randomizeScaleModeOnCompose;
	ScrollComboBox<String> __chordProgressionLength;
	ScrollComboBox<Double> beatDurationMultiplier;
	JCheckBox __allowChordRepeats;
	JCheckBox globalSwingOverride;
	KnobPanel globalSwingOverrideValue;
	JButton globalSwingOverrideApplyButton;
	public static KnobPanel loopBeatCount;
	public static JLabel __pauseBehaviorLabel;
	public static ScrollComboBox<String> __pauseBehaviorCombobox;
	public static JCheckBox __startFromBar;
	public static JCheckBox __rememberLastPos;
	public static JCheckBox __snapStartToBeat;
	public static JCheckBox __moveStartToCustomizedSection;
	@Deprecated JCheckBox __bottomUpReverseDrumPanels;
	JCheckBox __orderedTransposeGeneration;
	JCheckBox __configHistoryStoreRegeneratedTracks;
	public static JCheckBox __patternApplyPausesWhenGenerating;
	public static JCheckBox __allowValuesOutOfRange;


	// add/skip instruments
	SettingsPanel __chordSettingsPanel;
	@Deprecated SettingsPanel __arpSettingsPanel;
	@Deprecated SettingsPanel __drumSettingsPanel;
	static JCheckBox[] addInst = new JCheckBox[5];
	@Deprecated VeloRect __drumVolumeSlider;

	@Deprecated JButton __soloAllDrums;

	// all gen settings
	JButton[] addPanelButtons = new JButton[5];
	JButton[] generatePanelButtons = new JButton[5];
	JTextField[] randomPanelsToGenerate = new JTextField[5];

	// melody gen settings
	@Deprecated JCheckBox __generateMelodiesOnCompose;
	@Deprecated KnobPanel __melodyUseOldAlgoChance;
	@Deprecated JCheckBox __randomMelodyOnRegenerate;
	@Deprecated JCheckBox __randomMelodySameSeed;
	@Deprecated JCheckBox __melodyFirstNoteFromChord;
	@Deprecated JCheckBox __randomChordNote;

	@Deprecated JCheckBox __melodyBasicChordsOnly;
	@Deprecated KnobPanel __melodyChordNoteTarget;
	@Deprecated KnobPanel __melodyTonicNoteTarget;
	@Deprecated JCheckBox __melodyEmphasizeKey;
	@Deprecated KnobPanel __melodyModeNoteTarget;

	@Deprecated JCheckBox __useUserMelody;
	@Deprecated public MelodyMidiDropPane __dropPane;
	@Deprecated public static ScrollComboBox<String> __userMelodyScaleModeSelect;

	@Deprecated JCheckBox __melody1ForcePatterns;
	@Deprecated JCheckBox __melodyArpySurprises;
	@Deprecated JCheckBox __melodySingleNoteExceptions;
	@Deprecated JCheckBox __melodyFillPausesPerChord;
	@Deprecated KnobPanel __melodyNewBlocksChance;
	@Deprecated JCheckBox __melodyLegacyMode;

	@Deprecated JCheckBox __melodyAvoidChordJumpsLegacy;
	@Deprecated JCheckBox __melodyUseDirectionsFromProgression;
	@Deprecated JCheckBox __melodyPatternFlip;
	@Deprecated public static ScrollComboBox<MelodyUtils.NoteTargetDirection> __noteTargetDirectionChoice;
	@Deprecated public static ScrollComboBox<String> __melodyBlockTargetMode;
	@Deprecated JCheckBox __melodyTargetNotesRandomizeOnCompose;
	@Deprecated ScrollComboBox<String> __melodyPatternEffect;
	@Deprecated ScrollComboBox<String> __melodyRhythmAccents;
	@Deprecated ScrollComboBox<String> __melodyRhythmAccentsMode;
	@Deprecated JCheckBox __melodyRhythmAccentsPocket;
	@Deprecated JCheckBox __melodyPatternRandomizeOnCompose;
	@Deprecated KnobPanel __melodyReplaceAvoidNotes;
	@Deprecated KnobPanel __melodyMaxDirChanges;
	@Deprecated public static KnobPanel __melodyTargetNoteVariation;
	@Deprecated JPanel __melodyBlockTypePreferences;
	@Deprecated VeloRect[] __melodyBlockTypePreference;

	// melody extra settings
	@Deprecated public static RandomIntegerListButton __melodyBlockChoicePreference;
	@Deprecated public static JCheckBox __melodyUseCustomDurations;
	@Deprecated public static JCheckBox __melodyCustomDurationsRandomWeighting;
	@Deprecated public static JCheckBox __melodyCustomDurationsStrictMode;

	// bass gen settings
	// - there's nothing here -

	// chord gen settings
	JCheckBox __randomChordsGenerateOnCompose;
	JCheckBox __randomChordDelay;
	JCheckBox __randomChordStrum;
	KnobPanel __randomChordStruminess;
	JCheckBox __randomChordSplit;
	JCheckBox __randomChordTranspose;
	JCheckBox __randomChordPattern;
	JCheckBox __randomChordVaryLength;
	KnobPanel __randomChordExpandChance;
	KnobPanel __randomChordSustainChance;
	KnobPanel __randomChordShiftChance;
	KnobPanel __randomChordVoicingChance;
	KnobPanel __randomChordMaxSplitChance;
	JCheckBox __randomChordUseChordFill;
	ScrollComboBox<String> __randomChordStretchType;
	ScrollComboBox<Integer> __randomChordStretchPicker;
	KnobPanel __randomChordStretchGenerationChance;
	KnobPanel __randomChordMaxStrumPauseChance;
	KnobPanel __randomChordMinVel;
	KnobPanel __randomChordMaxVel;

	// arp gen settings
	@Deprecated JCheckBox __randomArpsGenerateOnCompose;
	@Deprecated JCheckBox __randomArpTranspose;
	@Deprecated JCheckBox __randomArpPattern;
	@Deprecated JCheckBox __randomArpHitsPerPattern;
	@Deprecated JCheckBox __randomArpAllSameInst;
	@Deprecated JCheckBox __randomArpAllSameHits;
	@Deprecated JCheckBox __randomArpLimitPowerOfTwo;
	@Deprecated KnobPanel __randomArpShiftChance;
	@Deprecated ScrollComboBox<Integer> __randomArpHitsPicker;
	@Deprecated JCheckBox __randomArpUseChordFill;
	@Deprecated ScrollComboBox<String> __randomArpStretchType;
	@Deprecated ScrollComboBox<Integer> __randomArpStretchPicker;
	@Deprecated KnobPanel __randomArpStretchGenerationChance;
	@Deprecated KnobPanel __randomArpMaxExceptionChance;
	@Deprecated JCheckBox __randomArpUseOctaveAdjustments;
	@Deprecated KnobPanel __randomArpMaxRepeat;
	@Deprecated KnobPanel __randomArpMinVel;
	@Deprecated KnobPanel __randomArpMaxVel;
	@Deprecated KnobPanel __randomArpMinLength;
	@Deprecated KnobPanel __randomArpMaxLength;
	@Deprecated JCheckBox __randomArpCorrectMelodyNotes;
	@Deprecated JCheckBox __arpCopyMelodyInst;

	// drum gen settings
	@Deprecated public static List<Integer> __PUNCHY_DRUMS = DrumGUI.PUNCHY_DRUMS;
	@Deprecated public static List<Integer> __KICK_DRUMS = DrumGUI.KICK_DRUMS;
	@Deprecated public static List<Integer> __SNARE_DRUMS = DrumGUI.SNARE_DRUMS;
	@Deprecated JCheckBox __randomDrumsGenerateOnCompose;
	@Deprecated KnobPanel __randomDrumsOverrandomize;
	@Deprecated KnobPanel __randomDrumMaxSwingAdjust;
	@Deprecated JCheckBox __randomDrumSlide;
	@Deprecated JCheckBox __randomDrumPattern;
	@Deprecated KnobPanel __randomDrumVelocityPatternChance;
	@Deprecated KnobPanel __randomDrumShiftChance;
	@Deprecated JCheckBox __randomDrumUseChordFill;
	@Deprecated JCheckBox __arrangementScaleMidiVelocity;
	public static KnobPanel __humanizeNotes;
	@Deprecated public static KnobPanel __humanizeDrums;
	public static KnobPanel __globalNoteLengthMultiplier;
	public static ScrollComboBox<Double> __swingUnitMultiplier;
	public static JCheckBox __customMidiForceScale;
	public static JCheckBox __reuseMidiChannelAfterCopy;
	public static JCheckBox __transposedNotesForceScale;
	public static JCheckBox __transposeNotePreview;
	public static JCheckBox __padGeneratedMidi;
	public static RandomIntegerListButton __padGeneratedMidiValues;
	public static JCheckBox __randomizeTimingsOnCompose;
	public static JCheckBox __sidechainPatternsOnCompose;
	@Deprecated JCheckBox __arrangementResetCustomPanelsOnCompose;
	@Deprecated ScrollComboBox<String> __randomDrumHitsMultiplier;
	@Deprecated ScrollComboBox<String> __randomDrumHitsMultiplierOnGenerate;
	@Deprecated public static JCheckBox __drumCustomMapping;
	@Deprecated public static JTextField __drumCustomMappingNumbers;


	// chord variety settings
	KnobPanel __spiceChance;
	KnobPanel __chordSlashChance;
	JCheckBox __spiceAllowDimAug;
	JCheckBox __spiceAllow9th13th;
	JCheckBox __spiceFlattenBigChords;
	JCheckBox __squishChordsProgressively;
	JCheckBox __copyChordsAfterGenerate;
	KnobPanel __spiceParallelChance;

	JCheckBox __spiceForceScale;
	ScrollComboBox<String> __firstChordSelection;
	ScrollComboBox<String> __lastChordSelection;

	// chord settings - progression
	JCheckBox __useChordFormula;
	public static KnobPanel __longProgressionSimilarity;
	ScrollComboBox<String> __keyChangeTypeSelection;
	public static CheckButton __userChordsEnabled;
	public static CheckButton __userDurationsEnabled;
	public static JTextField __userChordsDurations;
	public static ChordletPanel __userChords;

	// randomization button settings
	JCheckBox randomizeInstOnComposeOrGen;
	JCheckBox randomizeBpmOnCompose;
	JCheckBox randomizeTransposeOnCompose;
	JCheckBox randomizeChordStrumsOnCompose;
	@Deprecated JCheckBox __arpAffectsBpm;
	public static KnobPanel mainBpm;
	public static KnobPanel __bpmLow;
	public static KnobPanel __bpmHigh;
	public static KnobPanel __stretchMidi;
	@Deprecated public static KnobPanel __transposeScore;
	JButton switchOnComposeRandom;
	JButton sidechainPatterns;
	JButton sidechainPatternsTab;

	// seed / midi
	public static RandomValueButton randomSeed;
	public static int lastRandomSeed = 0;

	public static int getCurrentSeed() {
		return (randomSeed != null && randomSeed.getValue() != 0) ? randomSeed.getValue()
				: lastRandomSeed;
	}

	JList<File> generatedMidi;
	public static Sequencer sequencer = null;
	public static Map<Integer, List<MidiEvent>> midiEventsToRemove = new HashMap<>();
	public static File currentMidi = null;
	public static File currentSequenceMidi = null;
	MidiDevice device = null;
	public static Map<String, Integer> partAndOrderLastNoteIndexes = new HashMap<>();

	public static int getNextNoteIndex(int part, int partOrder) {
		Integer noteIndex = partAndOrderLastNoteIndexes.get(part + "#" + partOrder);
		if (noteIndex == null) {
			noteIndex = -1;
		}
		partAndOrderLastNoteIndexes.put(part + "#" + partOrder, ++noteIndex);
		return noteIndex;
	}

	@Deprecated public static JButton __showScore;
	@Deprecated public static ShowScorePopup __scorePopup;
	CheckButton midiMode;
	ScrollComboBox<String> midiModeDevices;
	MidiHandler mh = new MidiHandler();
	@Deprecated JCheckBox __combineDrumTracks;
	@Deprecated JCheckBox __combineMelodyTracks;
	public static CheckButton regenerateWhenValuesChange;


	JButton compose;
	JButton regenerate;
	JButton regenerateStopPlay;
	JButton regeneratePausePlay;
	JButton playMidi;
	JButton stopMidi;
	JButton pauseMidi;
	JTextField saveCustomFilename;
	JLabel savedIndicatorLabel;
	Color[] savedIndicatorForegroundColors = { new Color(220, 220, 220), Color.green, Color.magenta,
			Color.orange };
	public static boolean heavyBackgroundTasksInProgress = false;

	Thread cycle;
	JCheckBox __useMidiCC;
	static CheckButton loopBeat;
	ScrollComboBox<String> loopBeatCompose;
	public static JPanel sliderPanel;
	public static PlayheadRangeSlider slider;
	public static int sliderExtended = 0;
	public static List<Integer> sliderMeasureStartTimes = null;
	public static List<Integer> sliderBeatStartTimes = null;

	public static JLabel currentTime;
	JLabel totalTime;
	public static int currentSectionIndex = -1;
	public static JLabel sectionText;
	boolean isKeySeeking = false;
	public static boolean isDragging = false;
	private static boolean pauseInfoResettable = true;
	private static int pausedBpm = 50;
	private static int pausedSliderPosition = 0;
	private static int pausedMeasureCounter = 0;
	private static int startBpm = -1;
	private static int startSliderPosition = 0;
	private static int startBeatCounter = 0;

	public static double currentBeatMultiplier = 1.0;

	JLabel __tipLabel;
	public static JLabel __currentChords = new JLabel("Chords:[]");
	public static List<String> __currentChordsInternal = new ArrayList<>();
	JLabel messageLabel;
	ScrollComboBox<String> presetLoadBox;
	VeloRect globalVolSlider;
	VeloRect globalReverbSlider;
	VeloRect globalChorusSlider;
	VeloRect[] groupFilterSliders = new VeloRect[5];
	public static SoloMuter globalSoloMuter;
	public static List<SoloMuter> groupSoloMuters;
	public static boolean needToRecalculateSoloMuters = false;
	public static boolean needToRecalculateSoloMutersAfterSequenceGenerated = false;

	JPanel everythingPanel;
	JPanel controlPanel;
	JScrollPane everythingPane;


	static final PrintStream originalOut = System.out;
	static final PrintStream originalErr = System.err;
	static final PrintStream dummyOut = new PrintStream(new OutputStream() {
		public void write(int b) {
			// NO-OP
		}
	});


	public static Map<Integer, SoloMuter> cpSm = null;
	public static Map<Integer, SoloMuter> apSm = null;
	public static Map<Integer, SoloMuter> dpSm = null;

	public static UndoManager actionUndoManager = new UndoManager();
	public static UndoManager instrumentTabUndoManager = new UndoManager();
	public static DebugConsole dconsole = null;
	public static VibeComposerGUI vibeComposerGUI = null;

	private static GridBagConstraints constraints = new GridBagConstraints();
	private MelodyGUI melodyGUI;
	private BassGUI bassGUI;
	private ChordGUI chordGUI;
	private ArpGUI arpGUI;
	private ScoreGUI scoreGUI;
	private ExtraSettingsGUI extraSettingsGUI;

	public static JPanel __extraSettingsPanel;
	public static JPanel __currentSettingsMenuPanel = null;

	@Deprecated public static boolean __isShowingTextInKnobs = true;
	public static JCheckBox __displayVeloRectValues;
	public static JCheckBox __knobControlByDragging;
	public static JCheckBox __highlightPatterns;
	@Deprecated public static JCheckBox __highlightScoreNotes;
	public static JCheckBox __customFilenameAddTimestamp;
	@Deprecated public static JCheckBox __miniScorePopup;

	public static final String CURRENT_VERSION = "2.6";

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

	static {
		System.setErr(VibeComposerGUI.dummyOut);
	}

	public VibeComposerGUI(String title) {
		super(title);
	}

	private void initExtraSettingsGUI() {
		extraSettingsGUI = new ExtraSettingsGUI(new ExtraSettingsGUI.Context() {
			@Override public JButton makeButton(String name, Consumer<? super Object> action) { return VibeComposerGUI.makeButton(name, action); }
			@Override public JButton makeButton(String name, String actionCommand) { return VibeComposerGUI.this.makeButton(name, actionCommand); }
			@Override public JCheckBox makeCheckBox(String label, boolean selected, boolean thick) { return VibeComposerGUI.makeCheckBox(label, selected, thick); }
			@Override public void initHelperPopups(JPanel settingsPanel) { VibeComposerGUI.this.initHelperPopups(settingsPanel); }
			@Override public void markSoundbankRefreshNeeded() { needSoundbankRefresh = true; }
			@Override public void setSnapToTicks(boolean enabled) { slider.setSnapToTicks(enabled); }
			@Override public void repaintMainWindow() { VibeComposerGUI.this.repaint(); }
			@Override public List<? extends InstPanel> getInstList(int order) { return VibeComposerGUI.getInstList(order); }
			@Override public List<InstPanel> getAffectedPanels(int inst) { return VibeComposerGUI.getAffectedPanels(inst); }
			@Override public JScrollPane getInstPane(int order) { return VibeComposerGUI.getInstPane(order); }
			@Override public ChordGUI chordGUI() { return chordGUI; }
			@Override public MelodyGUI melodyGUI() { return melodyGUI; }
			@Override public ScoreGUI scoreGUI() { return scoreGUI; }
			@Override public ItemListener keyChangeTypeSelectionListener() { return VibeComposerGUI.this; }
		});
		extraSettingsGUI.initExtraSettings();
	}

	private void initArrangementGUI() {
		new ArrangementGUI(new ArrangementGUI.Context() {
			@Override public Dimension getScrollPaneDimension() { return scrollPaneDimension; }
			@Override public boolean isDarkMode() { return VibeComposerGUI.isDarkMode; }
			@Override public int getTableColumnMinWidth() { return TABLE_COLUMN_MIN_WIDTH; }
			@Override public JPanel getEverythingPanel() { return everythingPanel; }
			@Override public GridBagConstraints getConstraints() { return constraints; }
			@Override public Set<Component> getToggleableComponents() { return toggleableComponents; }
			@Override public JTabbedPane getInstrumentTabPane() { return instrumentTabPane; }
			@Override public JButton makeButton(String name, String actionCommand, int width,
					int height) {
				return VibeComposerGUI.this.makeButton(name, actionCommand, width, height);
			}
			@Override public JButton makeButton(String name, Consumer<? super Object> action,
					int width) {
				return VibeComposerGUI.makeButton(name, action, width);
			}
			@Override public JCheckBox makeCheckBox(String label, boolean selected, boolean thick) {
				return VibeComposerGUI.makeCheckBox(label, selected, thick);
			}
			@Override public void recalculateTabPaneCounts() {
				VibeComposerGUI.this.recalculateTabPaneCounts();
			}
			@Override public boolean canRegenerateOnChange() {
				return VibeComposerGUI.this.canRegenerateOnChange();
			}
			@Override public void regenerate() { VibeComposerGUI.this.regenerate(); }
			@Override public void openApplyCustomSectionPopup() {
				VibeComposerGUI.this.openApplyCustomSectionPopup();
			}
			@Override public void processActualArrangementMouseEvent(MouseEvent event) {
				VibeComposerGUI.this.processActualArrangementMouseEvent(event);
			}
			@Override public void processActualArrangementCopyDragging(MouseEvent event) {
				VibeComposerGUI.this.processActualArrangementCopyDragging(event);
			}
			@Override public void resetCopyDrag() { VibeComposerGUI.this.resetCopyDrag(); }
			@Override public Triple<Integer, Integer, Integer> calculateCurrentTableSubcell(
					MouseEvent event) {
				return VibeComposerGUI.this.calculateCurrentTableSubcell(event);
			}
			@Override public void arrangementTableProcessSectionType(Component component,
					String value) {
				VibeComposerGUI.this.arrangementTableProcessSectionType(component, value);
			}
			@Override public void arrangementTableProcessComponent(Component component, int row,
					int column, String value, int[] maxCounts, boolean actual) {
				VibeComposerGUI.this.arrangementTableProcessComponent(component, row, column,
						value, maxCounts, actual);
			}
			@Override public void refreshVariationPopupButtons(int count) {
				VibeComposerGUI.this.refreshVariationPopupButtons(count);
			}
			@Override public void applyCustomPanelsToSection(String action, int replacedPartNum,
					Integer sectionOrder) {
				VibeComposerGUI.this.applyCustomPanelsToSection(action, replacedPartNum, sectionOrder);
			}
			@Override public JFrame getMainWindow() { return VibeComposerGUI.this; }
		});
	}

	private void initScoreGUI() {
		scoreGUI = new ScoreGUI(new ScoreGUI.Context() {
			@Override public Dimension getScrollPaneDimension() { return scrollPaneDimension; }
			@Override public JTabbedPane getInstrumentTabPane() { return instrumentTabPane; }
		});
	}

	private void initMelodyGUI() {
		melodyGUI = new MelodyGUI(new MelodyGUI.Context() {
			@Override
			public Dimension getScrollPaneDimension() {
				return scrollPaneDimension;
			}

			@Override
			public Set<Component> getToggleableComponents() {
				return toggleableComponents;
			}

			@Override
			public JCheckBox[] getAddInst() {
				return addInst;
			}

			@Override
			public VeloRect[] getGroupFilterSliders() {
				return groupFilterSliders;
			}

			@Override
			public JButton[] getAddPanelButtons() {
				return addPanelButtons;
			}

			@Override
			public JButton[] getGeneratePanelButtons() {
				return generatePanelButtons;
			}

			@Override
			public JTextField[] getRandomPanelsToGenerate() {
				return randomPanelsToGenerate;
			}

			@Override
			public JButton makeButton(String name, Consumer<? super Object> action) {
				return SwingUtils.makeButton(name, action);
			}

			@Override
			public void addPanel(int part) {
				VibeComposerGUI.this.addPanel(part);
			}

			@Override
			public void generatePanels(int part, boolean triggerRegenerate) {
				VibeComposerGUI.this.generatePanels(part, triggerRegenerate);
			}

			@Override
			public boolean canRegenerateOnChange() {
				return VibeComposerGUI.this.canRegenerateOnChange();
			}

			@Override
			public void regenerate() {
				VibeComposerGUI.this.regenerate();
			}

			@Override
			public List<? extends InstPanel> getAffectedMelodyPanels() {
				return getAffectedPanels(0);
			}

			@Override
			public MelodyPanel addMelodyPanel() {
				return (MelodyPanel) VibeComposerGUI.this.addInstPanelToLayout(0);
			}

			@Override
			public boolean forceTransposedNotesToScale() {
				return GenerationGUI.transposedNotesForceScale.isSelected();
			}

			@Override
			public boolean randomizeInstrumentOnComposeOrGen() {
				return randomizeInstOnComposeOrGen.isSelected();
			}

			@Override
			public void repaintMainWindow() {
				VibeComposerGUI.this.repaint();
			}

			@Override
			public void setScoreTranspose(int transpose) {
				ScoreGUI.transposeScore.setInt(transpose);
			}

			@Override
			public void setGlobalScaleMode(String mode) {
				scaleMode.setVal(mode);
			}
		});
	}

	private void initBassGUI() {
		bassGUI = new BassGUI(new BassGUI.Context() {
			@Override
			public Dimension getScrollPaneDimension() {
				return scrollPaneDimension;
			}

			@Override
			public JCheckBox[] getAddInst() {
				return addInst;
			}

			@Override
			public VeloRect[] getGroupFilterSliders() {
				return groupFilterSliders;
			}

			@Override
			public JButton[] getAddPanelButtons() {
				return addPanelButtons;
			}

			@Override
			public JButton[] getGeneratePanelButtons() {
				return generatePanelButtons;
			}

			@Override
			public JTextField[] getRandomPanelsToGenerate() {
				return randomPanelsToGenerate;
			}

			@Override
			public JButton makeButton(String name, Consumer<? super Object> action) {
				return SwingUtils.makeButton(name, action);
			}

			@Override
			public void addPanel(int part) {
				VibeComposerGUI.this.addPanel(part);
			}

			@Override
			public void generatePanels(int part, boolean triggerRegenerate) {
				VibeComposerGUI.this.generatePanels(part, triggerRegenerate);
			}

			@Override
			public List<BassPanel> getAffectedBassPanels() {
				return (List<BassPanel>) (List<?>) getAffectedPanels(1);
			}

			@Override
			public BassPanel addBassPanel() {
				return (BassPanel) VibeComposerGUI.this.addInstPanelToLayout(1);
			}

			@Override
			public boolean randomizeInstrumentOnComposeOrGen() {
				return randomizeInstOnComposeOrGen.isSelected();
			}

			@Override
			public void repaintMainWindow() {
				VibeComposerGUI.this.repaint();
			}
		});
	}

	private DrumGUI drumGUI;

	private void initDrumGUI() {
		drumGUI = new DrumGUI(new DrumGUI.Context() {
			@Override public Dimension getScrollPaneDimension() { return scrollPaneDimension; }
			@Override public Set<Component> getToggleableComponents() { return toggleableComponents; }
			@Override public JCheckBox[] getAddInst() { return addInst; }
			@Override public VeloRect[] getGroupFilterSliders() { return groupFilterSliders; }
			@Override public JButton[] getAddPanelButtons() { return addPanelButtons; }
			@Override public JButton[] getGeneratePanelButtons() { return generatePanelButtons; }
			@Override public JTextField[] getRandomPanelsToGenerate() { return randomPanelsToGenerate; }
			@Override public JButton makeButton(String name, Consumer<? super Object> action) {
				return SwingUtils.makeButton(name, action);
			}
			@Override public JButton makeButton(String name, String actionCommand) {
				return VibeComposerGUI.this.makeButton(name, actionCommand);
			}
			@Override public void addPanel(int part) { VibeComposerGUI.this.addPanel(part); }
			@Override public void generatePanels(int part, boolean triggerRegenerate) {
				VibeComposerGUI.this.generatePanels(part, triggerRegenerate);
			}
			@Override public GridBagConstraints getConstraints() { return constraints; }
			@Override public JTabbedPane getInstrumentTabPane() { return instrumentTabPane; }
			@Override public List<DrumPanel> getAffectedDrumPanels() {
				return (List<DrumPanel>) (List<?>) getAffectedPanels(4);
			}
			@Override public DrumPanel addDrumPanel() {
				return (DrumPanel) VibeComposerGUI.this.addInstPanelToLayout(4);
			}
			@Override public int getLastRandomSeed() { return lastRandomSeed; }
			@Override public void repaintMainWindow() { VibeComposerGUI.this.repaint(); }
		});
	}

	@Deprecated
	private void syncDrumGUICompatibilityFields() {
		__drumPanels = DrumGUI.drumPanels;
		__drumScrollPane = DrumGUI.drumScrollPane;
		__drumParentPanel = DrumGUI.drumParentPanel;
		__bottomUpReverseDrumPanels = DrumGUI.bottomUpReverseDrumPanels;
		__drumSettingsPanel = DrumGUI.drumSettingsPanel;
		__drumVolumeSlider = DrumGUI.drumVolumeSlider;
		__soloAllDrums = DrumGUI.soloAllDrums;
		__PUNCHY_DRUMS = DrumGUI.PUNCHY_DRUMS;
		__KICK_DRUMS = DrumGUI.KICK_DRUMS;
		__SNARE_DRUMS = DrumGUI.SNARE_DRUMS;
		__randomDrumsGenerateOnCompose = DrumGUI.randomDrumsGenerateOnCompose;
		__randomDrumsOverrandomize = DrumGUI.randomDrumsOverrandomize;
		__randomDrumMaxSwingAdjust = DrumGUI.randomDrumMaxSwingAdjust;
		__randomDrumSlide = DrumGUI.randomDrumSlide;
		__randomDrumPattern = DrumGUI.randomDrumPattern;
		__randomDrumVelocityPatternChance = DrumGUI.randomDrumVelocityPatternChance;
		__randomDrumShiftChance = DrumGUI.randomDrumShiftChance;
		__randomDrumUseChordFill = DrumGUI.randomDrumUseChordFill;
		__humanizeDrums = DrumGUI.humanizeDrums;
		__randomDrumHitsMultiplier = DrumGUI.randomDrumHitsMultiplier;
		__randomDrumHitsMultiplierOnGenerate = DrumGUI.randomDrumHitsMultiplierOnGenerate;
		__drumCustomMapping = DrumGUI.drumCustomMapping;
		__drumCustomMappingNumbers = DrumGUI.drumCustomMappingNumbers;
		__combineDrumTracks = DrumGUI.combineDrumTracks;
	}

	private void initArpGUI() {
		arpGUI = new ArpGUI(new ArpGUI.Context() {
			@Override
			public Dimension getScrollPaneDimension() {
				return scrollPaneDimension;
			}

			@Override
			public Set<Component> getToggleableComponents() {
				return toggleableComponents;
			}

			@Override
			public JCheckBox[] getAddInst() {
				return addInst;
			}

			@Override
			public VeloRect[] getGroupFilterSliders() {
				return groupFilterSliders;
			}

			@Override
			public JButton[] getAddPanelButtons() {
				return addPanelButtons;
			}

			@Override
			public JButton[] getGeneratePanelButtons() {
				return generatePanelButtons;
			}

			@Override
			public JTextField[] getRandomPanelsToGenerate() {
				return randomPanelsToGenerate;
			}

			@Override
			public JButton makeButton(String name, Consumer<? super Object> action) {
				return SwingUtils.makeButton(name, action);
			}

			@Override
			public JButton makeButton(String name, String actionCommand) {
				return VibeComposerGUI.this.makeButton(name, actionCommand);
			}

			@Override
			public void addPanel(int part) {
				VibeComposerGUI.this.addPanel(part);
			}

			@Override
			public void generatePanels(int part, boolean triggerRegenerate) {
				VibeComposerGUI.this.generatePanels(part, triggerRegenerate);
			}

			@Override
			public GridBagConstraints getConstraints() {
				return constraints;
			}

			@Override
			public JTabbedPane getInstrumentTabPane() {
				return instrumentTabPane;
			}

			@Override
			public List<ArpPanel> getAffectedArpPanels() {
				return (List<ArpPanel>) (List<?>) getAffectedPanels(3);
			}

			@Override
			public ArpPanel addArpPanel() {
				return (ArpPanel) VibeComposerGUI.this.addInstPanelToLayout(3);
			}

			@Override
			public JCheckBox getRandomizeInstrumentOnComposeOrGen() {
				return randomizeInstOnComposeOrGen;
			}

			@Override
			public boolean orderedTransposeGeneration() {
				return GenerationGUI.orderedTransposeGeneration.isSelected();
			}

			@Override
			public int getRandomFromArray(Random generator, int[] values, int from) {
				return VibeComposerGUI.getRandomFromArray(generator, values, from);
			}

			@Override
			public boolean useShortBeatDuration() {
				return beatDurationMultiplier != null && beatDurationMultiplier.getVal() < 0.75;
			}

			@Override
			public MelodyPanel getFirstMelodyPanel() {
				return MelodyGUI.melodyPanels.isEmpty() ? null : MelodyGUI.melodyPanels.get(0);
			}

			@Override
			public void repaintMainWindow() {
				VibeComposerGUI.this.repaint();
			}
		});
	}

	@Deprecated
	private void syncArpGUICompatibilityFields() {
		__arpPanels = ArpGUI.arpPanels;
		__arpScrollPane = ArpGUI.arpScrollPane;
		__arpParentPanel = ArpGUI.arpParentPanel;
		__randomArpsGenerateOnCompose = ArpGUI.randomArpsGenerateOnCompose;
		__randomArpTranspose = ArpGUI.randomArpTranspose;
		__randomArpPattern = ArpGUI.randomArpPattern;
		__randomArpHitsPerPattern = ArpGUI.randomArpHitsPerPattern;
		__randomArpAllSameInst = ArpGUI.randomArpAllSameInst;
		__randomArpAllSameHits = ArpGUI.randomArpAllSameHits;
		__randomArpLimitPowerOfTwo = ArpGUI.randomArpLimitPowerOfTwo;
		__randomArpShiftChance = ArpGUI.randomArpShiftChance;
		__randomArpHitsPicker = ArpGUI.randomArpHitsPicker;
		__randomArpUseChordFill = ArpGUI.randomArpUseChordFill;
		__randomArpStretchType = ArpGUI.randomArpStretchType;
		__randomArpStretchPicker = ArpGUI.randomArpStretchPicker;
		__randomArpStretchGenerationChance = ArpGUI.randomArpStretchGenerationChance;
		__randomArpMaxExceptionChance = ArpGUI.randomArpMaxExceptionChance;
		__randomArpUseOctaveAdjustments = ArpGUI.randomArpUseOctaveAdjustments;
		__randomArpMaxRepeat = ArpGUI.randomArpMaxRepeat;
		__randomArpMinVel = ArpGUI.randomArpMinVel;
		__randomArpMaxVel = ArpGUI.randomArpMaxVel;
		__randomArpMinLength = ArpGUI.randomArpMinLength;
		__randomArpMaxLength = ArpGUI.randomArpMaxLength;
		__randomArpCorrectMelodyNotes = ArpGUI.randomArpCorrectMelodyNotes;
		__arpCopyMelodyInst = ArpGUI.arpCopyMelodyInst;
		__arpSettingsPanel = ArpGUI.arpSettingsPanel;
	}

	private void initChordGUI() {
		chordGUI = new ChordGUI(new ChordGUI.Context() {
			@Override
			public Dimension getScrollPaneDimension() {
				return scrollPaneDimension;
			}

			@Override
			public Set<Component> getToggleableComponents() {
				return toggleableComponents;
			}

			@Override
			public JCheckBox[] getAddInst() {
				return addInst;
			}

			@Override
			public VeloRect[] getGroupFilterSliders() {
				return groupFilterSliders;
			}

			@Override
			public JButton[] getAddPanelButtons() {
				return addPanelButtons;
			}

			@Override
			public JButton[] getGeneratePanelButtons() {
				return generatePanelButtons;
			}

			@Override
			public JTextField[] getRandomPanelsToGenerate() {
				return randomPanelsToGenerate;
			}

			@Override
			public JButton makeButton(String name, Consumer<? super Object> action) {
				return SwingUtils.makeButton(name, action);
			}

			@Override
			public JButton makeButton(String name, String actionCommand) {
				return VibeComposerGUI.this.makeButton(name, actionCommand);
			}

			@Override
			public void addPanel(int part) {
				VibeComposerGUI.this.addPanel(part);
			}

			@Override
			public void generatePanels(int part, boolean triggerRegenerate) {
				VibeComposerGUI.this.generatePanels(part, triggerRegenerate);
			}

			@Override
			public GridBagConstraints getConstraints() {
				return constraints;
			}

			@Override
			public JTabbedPane getInstrumentTabPane() {
				return instrumentTabPane;
			}

			@Override
			public JPanel getControlPanel() {
				return controlPanel;
			}

			@Override
			public JPanel getEverythingPanel() {
				return everythingPanel;
			}

			@Override
			public ItemListener getItemListener() {
				return VibeComposerGUI.this;
			}

			@Override
			public String getScaleMode() {
				return scaleMode.getVal();
			}

			@Override
			public GUIConfig getGuiConfig() {
				return guiConfig;
			}

			@Override
			public void copyGUItoConfig() {
				VibeComposerGUI.this.copyGUItoConfig(guiConfig);
			}

			@Override
			public void randomizeUserChords() {
				VibeComposerGUI.this.randomizeUserChords();
			}

			@Override
			public int getMaxChordProgressionLength() {
				return VibeComposerGUI.this.getMaxChordProgressionLength();
			}

			@Override
			public void alignChordsWithMelody(ChordletPanel chordlets) {
				if (!MelodyGUI.melodyPanels.isEmpty()) {
					chordlets.alignWithMelodyTargetNotes(
							MelodyGUI.melodyPanels.get(0).getChordNoteChoices());
				}
			}

			@Override
			public List<ChordPanel> getAffectedChordPanels() {
				return (List<ChordPanel>) (List<?>) getAffectedPanels(2);
			}

			@Override
			public JScrollPane getChordScrollPane() {
				return ChordGUI.chordScrollPane;
			}

			@Override
			public ChordPanel addChordPanel() {
				return (ChordPanel) VibeComposerGUI.this.addInstPanelToLayout(2);
			}

			@Override
			public boolean randomizeInstrumentOnComposeOrGen() {
				return randomizeInstOnComposeOrGen.isSelected();
			}

			@Override
			public boolean orderedTransposeGeneration() {
				return GenerationGUI.orderedTransposeGeneration.isSelected();
			}

			@Override
			public int getRandomFromArray(Random generator, int[] values, int from) {
				return VibeComposerGUI.getRandomFromArray(generator, values, from);
			}

			@Override
			public Pair<StrumType, Integer> getRandomStrumPair() {
				return VibeComposerGUI.this.getRandomStrumPair();
			}

			@Override
			public boolean useShortBeatDuration() {
				return beatDurationMultiplier != null && beatDurationMultiplier.getVal() < 0.75;
			}

			@Override
			public void repaintMainWindow() {
				VibeComposerGUI.this.repaint();
			}
		});
	}

	private void setMainIcon() {
		this.setIconImage(new ImageIcon(new ImageIcon(
				this.getClass().getResource("/VibeComposer2_LOGO_INVERT_NARROW.jpg"))
				.getImage().getScaledInstance(48, 48, java.awt.Image.SCALE_SMOOTH))
				.getImage());
	}

	private void init() {
		initMelodyGUI();
		initBassGUI();
		initArpGUI();
		initDrumGUI();
		initChordGUI();
		initArrangementGUI();
		initScoreGUI();
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
		for (int i = 0; i < SECTION_VAR_ICON_NAMES.length; i++) {
			SECTION_VARIATIONS_ICONS.add(new ImageIcon(new ImageIcon(
					this.getClass().getResource("/icons/sectionvars/" + SECTION_VAR_ICON_NAMES[i]))
							.getImage().getScaledInstance(15, 15, java.awt.Image.SCALE_SMOOTH))
									.getImage());
		}
		for (int i = 0; i < SECTION_TRANSITION_ICON_NAMES.length; i++) {
			SECTION_TRANSITION_ICONS.add(new ImageIcon(new ImageIcon(this.getClass()
					.getResource("/icons/transitions/" + SECTION_TRANSITION_ICON_NAMES[i]))
							.getImage().getScaledInstance(15, 15, java.awt.Image.SCALE_SMOOTH))
									.getImage());
		}

		for (int i = 0; i < LOCK_COMPONENT_ICON_NAMES.length; i++) {
			LOCK_COMPONENT_ICONS.add(new ImageIcon(new ImageIcon(
					this.getClass().getResource("/icons/" + LOCK_COMPONENT_ICON_NAMES[i]))
							.getImage().getScaledInstance(8, 8, java.awt.Image.SCALE_SMOOTH))
									.getImage());
		}

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
		initTitles(0, GridBagConstraints.CENTER);

		initExtraSettingsGUI();

		// randomization buttons
		initRandomButtons(350, GridBagConstraints.CENTER);

		//createHorizontalSeparator(15, this);

		initSoloMutersAndTrackControl(20, GridBagConstraints.WEST);
		LG.i("Titles, Extra, S/M " + (System.currentTimeMillis() - sysTime) + " ms!");
		// ---- INSTRUMENT SETTINGS ----
		{
			// melody


			// chords
			chordGUI.initChordGenSettings(40, GridBagConstraints.WEST);

			//createHorizontalSeparator(100, this);

			// arps
			arpGUI.initArpGenSettings(105, GridBagConstraints.WEST);
			syncArpGUICompatibilityFields();

			//createHorizontalSeparator(150, this);


			// drums
			drumGUI.initDrumGenSettings(190, GridBagConstraints.WEST);
			syncDrumGUICompatibilityFields();

			melodyGUI.initMelodyGenSettings(220, GridBagConstraints.WEST);

			//createHorizontalSeparator(240, this);

		}
		LG.i("Gen settings: " + (System.currentTimeMillis() - sysTime) + " ms!");
		boolean randomizeInstsTemp = randomizeInstOnComposeOrGen.isSelected();
		randomizeInstOnComposeOrGen.setSelected(true);
		{
			// ---- INSTRUMENT PANELS ----

			melodyGUI.initMelody(300, GridBagConstraints.WEST, constraints, instrumentTabPane);

			//createHorizontalSeparator(30, this);

			// bass
			bassGUI.initBass(310, GridBagConstraints.WEST, constraints, instrumentTabPane);
			__bassScrollPane = BassGUI.bassScrollPane;
			__bassParentPanel = BassGUI.bassParentPanel;
			//createHorizontalSeparator(35, this);

			chordGUI.initChords(311, GridBagConstraints.WEST);
			__chordScrollPane = ChordGUI.chordScrollPane;
			__chordParentPanel = ChordGUI.chordParentPanel;
			arpGUI.initArps(312, GridBagConstraints.WEST);
			drumGUI.initDrums(313, GridBagConstraints.WEST);
			LG.i("Insts: " + (System.currentTimeMillis() - sysTime) + " ms!");

			constraints.gridy = 320;

			instrumentTabPane.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					int indx = instrumentTabPane.indexAtLocation(e.getX(), e.getY());
					if (indx >= 0 && indx < 5) {
						if (SwingUtilities.isRightMouseButton(e)) {
							LG.i(("RMB pressed in instrument tab pane: " + indx));
							setAddInst(indx, !addInst[indx].isSelected());
						} else if (SwingUtilities.isMiddleMouseButton(e)) {
							LG.i(("MMB pressed in instrument tab pane: " + indx));
							boolean hasAny = false;
							for (int i = 0; i < 5; i++) {
								if (i != indx && addInst[i].isSelected()) {
									hasAny = true;
									break;
								}
							}
							for (int i = 0; i < 5; i++) {
								if (i != indx) {
									setAddInst(i, !hasAny);
								}
							}
							setAddInst(indx, true);
						}
					} else if (indx == 6) {
						ArrangementGUI.actualArrangement.getSections().forEach(s -> s.initPartMapFromOldData());
						ArrangementGUI.scrollableArrangementActualTable.repaint();
					}
				}
			});
			everythingPanel.add(instrumentTabPane, constraints);
			for (int i = 0; i < 5; i++) {
				instrumentTabPane.setBackgroundAt(i, OMNI.alphen(Constants.instColors[i], 40));
				int finalI = i;
				addInst[i].addChangeListener((evt) -> {
					instrumentTabPane.setBackgroundAt(finalI,
							OMNI.alphen(addInst[finalI].isSelected() ? Constants.instColors[finalI] : Color.white, 40));
				});
			}

			// arrangement
			ArrangementGUI.arrangementGUI.initArrangementSettings(325, GridBagConstraints.CENTER);


		}
		randomizeInstOnComposeOrGen.setSelected(randomizeInstsTemp);
		LG.i("Arr: " + (System.currentTimeMillis() - sysTime) + " ms!");
		scoreGUI.initScoreSettings(330, GridBagConstraints.CENTER);
		LG.i("Scr: " + (System.currentTimeMillis() - sysTime) + " ms!");
		//createHorizontalSeparator(327, this);

		// ---- OTHER SETTINGS ----
		{


			initMacroParams(360, GridBagConstraints.CENTER);

			// chord settings - variety/spice
			// chord settings - progressions
			chordGUI.initChordProgressionSettings(370, GridBagConstraints.CENTER);

			// chord tool tip

			everythingPanel.add(controlPanel, constraints);

			chordGUI.initCustomChords(380, GridBagConstraints.CENTER);

		}
		LG.i("Butts, params, cp, chords: " + (System.currentTimeMillis() - sysTime) + " ms!");

		//createHorizontalSeparator(400, this);

		// ---- CONTROL PANEL -----
		initControlPanel(410, GridBagConstraints.CENTER);


		// ---- PLAY PANEL ----
		initPlayPanel(420, GridBagConstraints.CENTER);
		initSliderPanel(440, GridBagConstraints.CENTER);
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
		LG.i("Full: " + (System.currentTimeMillis() - sysTime) + " ms!");
		instrumentTabUndoManager.setRecordingEvents(true);
		instrumentTabPane.setSelectedIndex(7);
		//instrumentTabPane.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		recalculateTabPaneCounts();
		switchDarkMode();

		/*for (Component c : everythingPanel.getComponents()) {
			if (c != instrumentTabPane) {
				c.setVisible(false);
			}
			if (c instanceof Container) {
				Container cnt = (Container) c;
				for (Component cs : cnt.getComponents()) {
					if (cs == compose) {
						c.setVisible(true);
					}
				}
			}

		}*/
		pack();
		setLocationRelativeTo(null);
		LG.i("Dark, pack: " + (System.currentTimeMillis() - sysTime) + " ms!");

		defaultGuiPreset = copyCurrentViewToPreset();

		boolean presetLoaded = false;
		if (presetLoadBox.getVal().equalsIgnoreCase("default")) {
			loadPreset();
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
			for (int i = 1; i < 5; i++) {
				generatePanels(i);
			}
			LG.i("Panels generated at : " + (System.currentTimeMillis() - sysTime) + " ms!");
		}

		heavyBackgroundTasksInProgress = false;
	}


	private void initScrollPaneListeners() {
		for (int i = 0; i < 5; i++) {
			SwingUtils.setupScrollpanePriorityScrolling(getInstPane(i));
		}
		SwingUtils.setupScrollpanePriorityScrolling(ArrangementGUI.arrangementScrollPane);
		SwingUtils.setupScrollpanePriorityScrolling(ArrangementGUI.arrangementActualScrollPane);
	}

	protected void setAddInst(int partNum, boolean b) {
		addInst[partNum].setSelected(b);
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

	private void initTitles(int startY, int anchorSide) {
		/*mainTitle = new JLabel("Vibe Composer");
		mainTitle.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		subTitle = new JLabel("by Vibe Historian");
		subTitle.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

		mainTitle.setFont(new Font("Courier", Font.BOLD, 25));
		subTitle.setFont(subTitle.getFont().deriveFont(Font.BOLD));*/
		constraints.weightx = 100;
		constraints.weighty = 100;
		constraints.gridx = 0;
		constraints.gridy = startY;
		constraints.gridwidth = 3;
		constraints.gridheight = 1;
		constraints.anchor = anchorSide;
		//everythingPanel.add(mainTitle, constraints);
		constraints.gridy = 1;
		//everythingPanel.add(subTitle, constraints);

		JPanel mainButtonsPanel = new JPanel();
		mainButtonsPanel.setOpaque(false);
		constraints.gridy = startY + 3;

		//unsoloAll = makeButton("S", "UnsoloAllTracks");

		globalVolSlider = new VeloRect(0, 150, 100);
		globalReverbSlider = VeloRect.midi( 60);
		globalChorusSlider = VeloRect.midi( 15);

		mainButtonsPanel.add(new JLabel("Vol."));
		mainButtonsPanel.add(globalVolSlider);
		mainButtonsPanel.add(new JLabel("Rv."));
		mainButtonsPanel.add(globalReverbSlider);
		mainButtonsPanel.add(new JLabel("Ch."));
		mainButtonsPanel.add(globalChorusSlider);

		globalSoloMuter = new SoloMuter(-1, SoloMuter.Type.GLOBAL);

		mainButtonsPanel.add(globalSoloMuter);
		globalSoloMuter.setBackground(null);

		mainButtonsPanel.add(makeButton("Toggle Dark Mode", e -> switchDarkMode()));

		mainButtonsPanel.add(makeButton("Toggle Adv. Features", e -> switchFullMode()));

		mainButtonsPanel.add(makeButton("B I G/small", e -> switchBigMonitorMode()));

		mainButtonsPanel.add(makeButton("Exclude Not Solo'd", e -> toggleExclude()));

		//mainButtonsPanel.add(makeButton("DrumView", e -> openDrumViewPopup()));


		mainButtonsPanel.add(makeButton("Settings", e -> openExtraSettingsPopup()));


		// ---- MESSAGE PANEL ----

		messageLabel = new JLabel("Click something!");
		messageLabel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		//mainButtonsPanel.add(messageLabel);

		presetLoadBox = new ScrollComboBox<String>(false);
		presetLoadBox.setEditable(true);
		reloadPresetBox();


		mainButtonsPanel.add(presetLoadBox);
		mainButtonsPanel.add(makeButtonMoused("Load Preset", e -> {
			if (SwingUtilities.isLeftMouseButton(e)) {
				loadPreset();
			} else {
				openFolder(Constants.PRESET_FOLDER);
			}
		}));
		mainButtonsPanel.add(makeButton("Save Preset", e -> savePreset()));
		mainButtonsPanel.add(makeButton("Undefault", e -> undefaultPreset()));
		mainButtonsPanel.add(makeButton("Reset All", e -> {
			if (heavyBackgroundTasksInProgress) {
				return;
			}
			loadPresetObject(defaultGuiPreset);
			heavyBackgroundTasksInProgress = true;
			randomPanelsToGenerate[0].setText("" + 3);
			randomPanelsToGenerate[1].setText("" + 1);
			randomPanelsToGenerate[2].setText("" + 2);
			randomPanelsToGenerate[3].setText("" + 3);
			randomPanelsToGenerate[4].setText("" + 6);
			melodyGUI.generateInitialMelodyPanels();
			for (int i = 1; i < 5; i++) {
				generatePanels(i);
			}
			ArrangementGUI.manualArrangement.setSelected(false);
			heavyBackgroundTasksInProgress = false;
			LG.i("Default Panels generated!");
		}));

		everythingPanel.add(mainButtonsPanel, constraints);
	}

	private void reloadPresetBox() {
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

			reloadPresetBox();
		}

		new TemporaryInfoPopup(exists ? "Undefaulted 'default' preset!" : "Nothing to undefault!",
				2000);
	}

	private void loadDrums() {

	}

	private void loadPreset() {
		String presetName = (String) presetLoadBox.getEditor().getItem();
		LG.i("Trying to load preset: " + presetName);

		if (OMNI.EMPTYCOMBO.equalsIgnoreCase(presetName)) {
			return;
		} else {
			// check if file exists | special case: --- should load new GUIConfig()
			File loadedFile = new File(Constants.PRESET_FOLDER + "/" + presetName + ".xml");
			if (loadedFile.exists()) {
				try {
					GUIPreset preset = unmarshallPreset(loadedFile);
					loadPresetObject(preset);
				} catch (JAXBException | IOException e) {
					LG.e("Could not load preset!", e);
					new TemporaryInfoPopup("Preset loading failed! " + Constants.BUG_HUNT_MESSAGE, 2000);
					return;
				}
			}
		}

		LG.i("Loaded preset: " + presetName);
	}

	public void loadPresetObject(GUIPreset preset) {
		if (heavyBackgroundTasksInProgress) {
			return;
		}
		heavyBackgroundTasksInProgress = true;
		guiConfig = preset;
		copyConfigToGUI(guiConfig);
		List<Component> presetComps = makeSettableComponentList();
		for (int i = 0; i < preset.getOrderedValuesUI().size(); i++) {
			setComponent(presetComps.get(i), preset.getOrderedValuesUI().get(i), false);
		}
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

	private void savePreset() {
		String presetName = (String) presetLoadBox.getEditor().getItem();
		LG.i("Trying to save preset: " + presetName);
		if (!presetName.matches(Constants.FILENAME_VALID_NAME)) {
			new TemporaryInfoPopup("Name contains invalid characters: "
					+ presetName.replaceAll(Constants.FILENAME_VALID_CHARACTERS, ""), 2500);
			return;
		}
		presetName = presetName.replaceAll(" ", "_");
		File makeSavedDir = new File(Constants.PRESET_FOLDER);
		makeSavedDir.mkdir();

		String filePath = Constants.PRESET_FOLDER + "/" + presetName + ".xml";
		saveGuiPresetFileByFilePath(filePath);
		presetLoadBox.addItem(presetName);
		new TemporaryInfoPopup("Saved preset: " + presetName, 2000);
	}

	@Deprecated private void __initExtraSettings() { initExtraSettingsGUI(); }
	@Deprecated private void __initExtraSettingsScore(JPanel panel) { extraSettingsGUI.initExtraSettingsScore(panel); }
	@Deprecated private void __initExtraSettingsInstruments(JPanel panel) { extraSettingsGUI.initExtraSettingsInstruments(panel); }
	@Deprecated private void __initExtraSettingsPause(JPanel panel) { extraSettingsGUI.initExtraSettingsPause(panel); }
	@Deprecated private void __initExtraSettingsBpm(JPanel panel) { extraSettingsGUI.initExtraSettingsBpm(panel); }
	@Deprecated private void __initExtraSettingsDisplay(JPanel panel) { extraSettingsGUI.initExtraSettingsDisplay(panel); }
	@Deprecated private void __initExtraSettingsHumanize(JPanel panel) { extraSettingsGUI.initExtraSettingsHumanize(panel); }
	@Deprecated private void __initExtraSettingsCompose(JPanel panel) { extraSettingsGUI.initExtraSettingsCompose(panel); }
	@Deprecated private void __initExtraSettingsGeneration(JPanel panel) { extraSettingsGUI.initGenerationSettings(panel); }

@Deprecated
private void __initExtraSettingsChords(JPanel chordChoicePanel) {
		// CHORDS
		__spiceFlattenBigChords = new CustomCheckBox("Spicy Voicing", false);
		__useChordFormula = new CustomCheckBox("Chord Formula", true);
		__randomChordVoicingChance = new KnobPanel("Flatten<br>Voicing%", 100);
		__squishChordsProgressively = new CustomCheckBox("<html>Flatten<br>Progressively</html>",
				false);
		__longProgressionSimilarity = new DetachedKnobPanel("8 Chords <br>Similarity%", 50, 0, 100);

		chordChoicePanel.add(__useChordFormula);
		chordChoicePanel.add(__longProgressionSimilarity);
		chordChoicePanel.add(__randomChordVoicingChance);
		chordChoicePanel.add(__spiceFlattenBigChords);
		chordChoicePanel.add(__squishChordsProgressively);
	}

	@Deprecated
	private void __initExtraSettingsMelody(JPanel melodyGenerationSettingsPanel) {
		melodyGUI.initExtraSettingsMelody(melodyGenerationSettingsPanel);
	}











	private void initSoloMutersAndTrackControl(int startY, int anchorSide) {
		JPanel soloMuterTrackControlPanel = new JPanel();
		soloMuterTrackControlPanel.setOpaque(false);
		JLabel emptySmLabel = new JLabel("");
		emptySmLabel.setPreferredSize(new Dimension(1, 3));
		soloMuterTrackControlPanel.add(emptySmLabel);

		groupSoloMuters = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			SoloMuter sm = new SoloMuter(i, SoloMuter.Type.GROUP);
			groupSoloMuters.add(sm);
			soloMuterTrackControlPanel.add(sm);
		}

		soloMuterTrackControlPanel.add(new JLabel("Track History: "));
		configHistory.box().setPreferredSize(new Dimension(450, 30));
		soloMuterTrackControlPanel.add(configHistory);
		soloMuterTrackControlPanel.add(makeButton("Load", e -> {
			if (configHistory.getItemCount() > 0) {
				guiConfig = configHistory.getSelectedItem();
				configHistory.removeItemAt(configHistory.getSelectedIndex());
				configHistory.addItem(guiConfig);
				configHistory.setSelectedIndex(configHistory.getItemCount() - 1);
				copyConfigToGUI(guiConfig);
				//clearAllSeeds();
			}
		}));
		JButton loadCustomBtn = makeButton("Replace Section", e -> replaceSection());

		JButton recomposeSectionBtn = makeButton("Recompose Section", e -> recomposeSection());

		soloMuterTrackControlPanel.add(loadCustomBtn);
		soloMuterTrackControlPanel.add(recomposeSectionBtn);
		JTextField bookmarkField = new JTextField("Intro1", 8);
		soloMuterTrackControlPanel.add(bookmarkField);
		JButton butt = makeButton("Add Bookmark Text", e -> {
			GUIConfig historyCfg = configHistory.getSelectedItem();
			historyCfg.setBookmarkText(bookmarkField.getText());
			configHistory.removeItemAt(configHistory.getSelectedIndex());
			configHistory.addItem(historyCfg);
			configHistory.setSelectedIndex(configHistory.getItemCount() - 1);
		});
		soloMuterTrackControlPanel.add(butt);


		toggleableComponents.add(bookmarkField);
		toggleableComponents.add(butt);
		toggleableComponents.add(loadCustomBtn);
		toggleableComponents.add(recomposeSectionBtn);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(soloMuterTrackControlPanel, constraints);
	}

	private void recomposeSection() {
		if (!isCustomSection()) {
			return;
		}
		ArrangementGUI.manualArrangement.setSelected(true);
		for (int i = 0; i < 5; i++) {
			createPanels(i, getInstList(i).size(), false);
			applyCustomPanelsToSection("", i, ArrangementGUI.arrSection.getSelectedIndex());
		}
		ArrangementGUI.arrSection.getCurrentButton().repaint();
		recalculateTabPaneCounts();
		recalculateSoloMuters();
		if (sequencer != null && regenerateWhenValuesChange.isSelected()) {
			stopMidi();
			regenerate();
		}
	}

	private void replaceSection() {
		if (configHistory.getItemCount() > 0 && ArrangementGUI.arrSection.getSelectedIndex() > 0) {
			GUIConfig sectionGuiConfig = configHistory.getVal();
			Section currentSec = ArrangementGUI.actualArrangement.getSections()
					.get(ArrangementGUI.arrSection.getSelectedIndex() - 1);
			for (int i = 0; i < 5; i++) {
				currentSec.setInstPartList(sectionGuiConfig.getInstPartList(i), i);
			}
			/*SectionConfig secConfig = currentSec.getSecConfig();
			if (sectionGuiConfig.getBeatDurationMultiplierIndex() != beatDurationMultiplier
					.getSelectedIndex()) {
				secConfig.setBeatDurationMultiplierIndex(
						sectionGuiConfig.getBeatDurationMultiplierIndex());
			}

			if ((int) sectionGuiConfig.getBpm() != mainBpm.getInt()) {
				secConfig.setSectionBpm((int) sectionGuiConfig.getBpm());
			}

			secConfig.setSectionSwingOverride(sectionGuiConfig.getGlobalSwingOverride());*/
			currentSec.setCustomChords(sectionGuiConfig.getCustomChords());
			currentSec.setCustomDurations(sectionGuiConfig.getCustomChordDurations());
			currentSec.setCustomChordsEnabled(true);
			if (!"4,4,4,4".equals(currentSec.getCustomDurations())) {
				currentSec.setCustomDurationsEnabled(true);
			}
			ArrangementGUI.arrSection.getCurrentButton().repaint();
			switchPanelsForSectionSelection(ArrangementGUI.arrSection.getVal());

			/*if (sectionGuiConfig.getGlobalSwingOverride() != null) {
				applyGlobalSwing(sectionGuiConfig.getGlobalSwingOverride(), true);
			}*/

			//copyConfigToGUI(guiConfig);
			//clearAllSeeds();
		}
	}

	private void generatePanels(int part, boolean triggerRegenerate) {
		int panelCount = isCustomSection() ? getInstList(part).size()
				: Integer.valueOf(randomPanelsToGenerate[part].getText());
		createPanels(part, panelCount, false);
		recalculateTabPaneCounts();
		recalculateSoloMuters();

		if (triggerRegenerate && canRegenerateOnChange()) {
			regenerate();
		}
	}

	private void generatePanels(int part) {
		generatePanels(part, false);
	}

	@Deprecated
	private void __initMelodyGenSettings(int startY, int anchorSide) {
		melodyGUI.initMelodyGenSettings(startY, anchorSide);
	}

	@Deprecated
	private JPanel __initMelodySettings() {
		return melodyGUI.initMelodySettings();
	}

	@Deprecated
	private JPanel __initMelodySettingsPlus() {
		return melodyGUI.initMelodySettingsPlus();
	}

	@Deprecated
	private JPanel __initMelodySettingsPlusPlus() {
		return melodyGUI.initMelodySettingsPlusPlus();
	}

	public static JCheckBox makeCheckBox(String string, boolean b, boolean thick) {
		return SwingUtils.makeCheckBox(string, b, thick);
	}

	/*public void fixCombinedMelodyTracks() {
		if (MelodyGUI.combineMelodyTracks == null) {
			return;
		}
		boolean foundValid = false;
		int start = currentMidi == null ? 1 : 0;
		for (int i = start; i < MelodyGUI.melodyPanels.size(); i++) {
			if (MelodyGUI.combineMelodyTracks.isSelected()) {
				boolean isValid = MelodyGUI.melodyPanels.get(i).getSequenceTrack() >= 0;
				if (!foundValid && isValid) {
					foundValid = true;
					MelodyGUI.melodyPanels.get(i).toggleCombinedMelodyDisabledUI(true);
				} else {
					MelodyGUI.melodyPanels.get(i)
							.toggleCombinedMelodyDisabledUI(!MelodyGUI.combineMelodyTracks.isSelected());
				}
			} else {
				MelodyGUI.melodyPanels.get(i)
						.toggleCombinedMelodyDisabledUI(!MelodyGUI.combineMelodyTracks.isSelected());
			}
		}
		if (!foundValid) {
			MelodyGUI.melodyPanels.get(0).toggleCombinedMelodyDisabledUI(true);
		}
	}*/

	@Deprecated
	private void __initMelody(int startY, int anchorSide) {
		melodyGUI.initMelody(startY, anchorSide, constraints, instrumentTabPane);
	}

	@Deprecated
	private void __generateInitialMelodyPanels() {
		melodyGUI.generateInitialMelodyPanels();
	}


	@Deprecated
	private void __initBass(int startY, int anchorSide) {
		bassGUI.initBass(startY, anchorSide, constraints, instrumentTabPane);
		__bassScrollPane = BassGUI.bassScrollPane;
		__bassParentPanel = BassGUI.bassParentPanel;
	}

	@Deprecated
private void __initChordGenSettings(int startY, int anchorSide) {
		JPanel scrollableChordPanels = new JPanel();
		scrollableChordPanels.setLayout(new BoxLayout(scrollableChordPanels, BoxLayout.Y_AXIS));
		scrollableChordPanels.setAutoscrolls(true);

		__chordScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(scrollPaneDimension.width, scrollPaneDimension.height - 100);
			}
		};
		__chordScrollPane.setViewportView(scrollableChordPanels);
		__chordScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		__chordScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		JPanel __chordSettingsPanel = new JPanel();
		__chordSettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

		addInst[2] = new CustomCheckBox("CHORDS", true);
		__chordSettingsPanel.add(addInst[2]);
		groupFilterSliders[2] = VeloRect.midi( 127);
		JLabel filterLabel = new JLabel("LP");
		__chordSettingsPanel.add(filterLabel);
		__chordSettingsPanel.add(groupFilterSliders[2]);

		addPanelButtons[2] = makeButton("+Chord", e -> {
			addPanel(2);
		});
		generatePanelButtons[2] = makeButton("Generate Chords:", e -> {
			generatePanels(2, true);
		});
		randomPanelsToGenerate[2] = new JTextField("2", 2);
		__chordSettingsPanel.add(addPanelButtons[2]);
		__chordSettingsPanel.add(generatePanelButtons[2]);
		__chordSettingsPanel.add(randomPanelsToGenerate[2]);

		__randomChordsGenerateOnCompose = makeCheckBox("On Compose", true, true);
		__chordSettingsPanel.add(__randomChordsGenerateOnCompose);


		__randomChordDelay = new CustomCheckBox("Delay", false);
		__randomChordStrum = new CustomCheckBox("", true);
		__randomChordStruminess = new DetachedKnobPanel("Struminess", 50);
		__randomChordSplit = new CustomCheckBox("Use Split (ms)", false);
		__randomChordTranspose = new CustomCheckBox("Transpose", true);
		__randomChordSustainChance = new DetachedKnobPanel("Chord%", 50);
		__randomChordVaryLength = new CustomCheckBox("Vary Length", true);
		__randomChordExpandChance = new DetachedKnobPanel("Expand%", 70);
		__randomChordUseChordFill = new CustomCheckBox("Fills", true);
		__randomChordMaxSplitChance = new DetachedKnobPanel("Max Tran-<br>sition%", 25);
		__chordSlashChance = new KnobPanel("Chord1<br>Slash%", 5);
		__randomChordPattern = new CustomCheckBox("Patterns", true);
		__randomChordShiftChance = new DetachedKnobPanel("Shift%", 60);
		__randomChordMinVel = new DetachedKnobPanel("Min<br>Vel", 65, 0, 126);
		__randomChordMaxVel = new DetachedKnobPanel("Max<br>Vel", 90, 1, 127);

		__chordSettingsPanel.add(__randomChordTranspose);
		__chordSettingsPanel.add(__randomChordStrum);
		__chordSettingsPanel.add(__randomChordStruminess);
		__chordSettingsPanel.add(__randomChordUseChordFill);

		__chordSettingsPanel.add(__randomChordDelay);
		__chordSettingsPanel.add(__randomChordSplit);
		//__chordSettingsPanel.finishMinimalInit();

		__randomChordStretchType = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new String[] { "NONE", "FIXED", "AT_MOST" }, __randomChordStretchType);
		__randomChordStretchType.setVal("AT_MOST");
		JLabel stretchLabel = new JLabel("VOICES");
		__chordSettingsPanel.add(stretchLabel);
		__chordSettingsPanel.add(__randomChordStretchType);
		__randomChordStretchPicker = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new Integer[] { 3, 4, 5, 6 }, __randomChordStretchPicker);
		__randomChordStretchPicker.setVal(5);
		__chordSettingsPanel.add(__randomChordStretchPicker);
		__randomChordStretchGenerationChance = new DetachedKnobPanel("Chance", 50);
		__chordSettingsPanel.add(__randomChordStretchGenerationChance);
		__randomChordMaxStrumPauseChance = new DetachedKnobPanel("Max. Strum<br>Pause %", 35);
		__chordSettingsPanel.add(__randomChordMaxStrumPauseChance);

		JButton clearChordPatternSeeds = makeButton("Clear Seeds", "ClearChordSeeds");

		JPanel chordSettingsExtraPanel = new JPanel();
		JLabel csExtra = new JLabel("CHORD SETTINGS+");
		csExtra.setPreferredSize(new Dimension(120, 30));
		csExtra.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		chordSettingsExtraPanel.add(csExtra);

		chordSettingsExtraPanel.add(__randomChordSustainChance);
		chordSettingsExtraPanel.add(__randomChordVaryLength);
		chordSettingsExtraPanel.add(__randomChordExpandChance);
		chordSettingsExtraPanel.add(__randomChordMaxSplitChance);
		chordSettingsExtraPanel.add(__chordSlashChance);
		chordSettingsExtraPanel.add(__randomChordMinVel);
		chordSettingsExtraPanel.add(__randomChordMaxVel);
		chordSettingsExtraPanel.add(__randomChordPattern);
		chordSettingsExtraPanel.add(__randomChordShiftChance);
		chordSettingsExtraPanel.add(clearChordPatternSeeds);
		chordSettingsExtraPanel.add(new PartManagerPanel(2));

		toggleableComponents.add(__randomChordDelay);
		toggleableComponents.add(stretchLabel);
		toggleableComponents.add(__randomChordStretchType);
		toggleableComponents.add(__randomChordStretchPicker);
		toggleableComponents.add(__randomChordSplit);

		toggleableComponents.add(chordSettingsExtraPanel);


		//constraints.gridy = startY;
		//constraints.anchor = anchorSide;
		__chordSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		__chordSettingsPanel.setMaximumSize(new Dimension(1800, 50));
		//scrollableChordPanels.add(__chordSettingsPanel);
		chordSettingsExtraPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		chordSettingsExtraPanel.setMaximumSize(new Dimension(1800, 50));
		//constraints.gridy = startY + 1;

		//scrollableChordPanels.add(chordSettingsExtraPanel);


		__chordParentPanel = new JPanel() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};
		__chordParentPanel.setLayout(new BoxLayout(__chordParentPanel, BoxLayout.Y_AXIS));

		JPanel borderPanel = new JPanel() {
			@Override
			public Dimension getMaximumSize() {
				return new Dimension(scrollPaneDimension.width, 100);
			}
		};
		borderPanel.setLayout(new DynamicGridLayout(0, 1));
		borderPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
		borderPanel.add(__chordSettingsPanel);
		borderPanel.add(chordSettingsExtraPanel);
		__chordParentPanel.add(borderPanel);
		__chordParentPanel.add(__chordScrollPane);

		//addHorizontalSeparatorToPanel(scrollableChordPanels);
	}

	@Deprecated
private void __initChords(int startY, int anchorSide) {
		// ---- CHORDS ----
		// gridy 50 - 99 range
		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Chords", __chordParentPanel);
	}

	@Deprecated
	private void __initArpGenSettings(int startY, int anchorSide) {
		arpGUI.initArpGenSettings(startY, anchorSide);
		syncArpGUICompatibilityFields();
	}

	@Deprecated
	private void __initArps(int startY, int anchorSide) {
		arpGUI.initArps(startY, anchorSide);
	}
	@Deprecated
	private void __initDrumGenSettings(int startY, int anchorSide) {
		drumGUI.initDrumGenSettings(startY, anchorSide);
		syncDrumGUICompatibilityFields();
	}

	@Deprecated
	private void __initDrums(int startY, int anchorSide) {
		drumGUI.initDrums(startY, anchorSide);
	}

	@Deprecated public static void __setActualModel(TableModel model) {
		__setActualModel(model, true);
	}

	@Deprecated public static void __setActualModel(TableModel model, boolean reset) {
		ArrangementGUI.scrollableArrangementActualTable.setModel(model);
		ArrangementGUI.scrollableArrangementActualTable.setRowSelectionAllowed(false);
		ArrangementGUI.scrollableArrangementActualTable.setColumnSelectionAllowed(true);
		if (reset) {
			ArrangementGUI.arrangementGUI.resetArrSection();
		}

	}

	@Deprecated public static void __resetArrSection() {
		List<Section> actualSections = ArrangementGUI.actualArrangement.getSections();
		if (actualSections != null) {
			List<String> sectionNamesNumbers = new ArrayList<>();
			for (int i = 0; i < actualSections.size(); i++) {
				Section sec = actualSections.get(i);
				String suffix = "";
				if (sec.hasCustomizedParts()) {
					suffix = "*";
				}
				sectionNamesNumbers.add((i + 1) + ": " + actualSections.get(i).getType() + suffix);
			}
			ArrangementGUI.arrSection.setButtons(new ArrayList<>());
			ArrangementGUI.arrSection.addAll(sectionNamesNumbers.toArray(new String[] {}));
		}
	}

	@Deprecated public void __handleArrangementAction(String action, int seed, int maxLength) {
		boolean refreshActual = false;
		boolean resetArrSectionSelection = true;
		boolean resetArrSectionPanel = true;
		boolean checkManual = false;
		if (action.equalsIgnoreCase("ArrangementReset")) {
			ArrangementGUI.arrangement.generateDefaultArrangement();
			ArrangementGUI.pieceLength.setText("12");
		} else if (action.equalsIgnoreCase("ArrangementAddLast")) {
			if (instrumentTabPane.getSelectedIndex() == 5) {
				ArrangementGUI.arrangement.duplicateSection(ArrangementGUI.scrollableArrangementTable);
			} else {
				//actualArrangement.resortByIndexes(scrollableArrangementActualTable);
				ArrangementGUI.actualArrangement.duplicateSection(ArrangementGUI.scrollableArrangementActualTable);
				refreshActual = true;
				checkManual = true;
			}
			if (ArrangementGUI.arrangement.getSections().size() > maxLength) {
				ArrangementGUI.pieceLength.setText("" + ++maxLength);
			}
		} else if (action.equalsIgnoreCase("ArrangementRemoveLast")) {
			if (instrumentTabPane.getSelectedIndex() == 5) {
				ArrangementGUI.arrangement.removeSection(ArrangementGUI.scrollableArrangementTable);
			} else {
				//actualArrangement.resortByIndexes(scrollableArrangementActualTable);
				ArrangementGUI.actualArrangement.removeSection(ArrangementGUI.scrollableArrangementActualTable);
				refreshActual = true;
				checkManual = true;
			}
			//pieceLength.setText("" + --maxLength);
		} else if (action.equalsIgnoreCase("ArrangementRandomize")) {
			// on compose -> this must happen before compose part
			ArrangementGUI.arrangement.randomizeFully(maxLength, seed, 50, 30, 2, 4, 15);
		} else if (action.startsWith("ArrangementOpenVariation,")) {
			//actualArrangement.resortByIndexes(scrollableArrangementActualTable);
			Integer secOrder = Integer.valueOf(action.split(",")[1]);
			ArrangementGUI.arrangementGUI.openVariationPopup(secOrder);
			return;
			//variationJD.getFrame().setTitle(action);
		} else if (action.startsWith("ArrangementApply")) {
			String selItem = ArrangementGUI.arrSection.getVal();
			if (ArrangementGUI.GLOBAL.equals(selItem)) {
				return;
			}

			int replacedPartNum = instrumentTabPane.getSelectedIndex();
			Integer secOrder = Integer.valueOf(selItem.split(":")[0]);

			applyCustomPanelsToSection(action, replacedPartNum, secOrder);
			if (instrumentTabPane.getSelectedIndex() < 5) {
				//resetArrSection();
				//arrSection.setSelectedIndex(secOrder);
				resetArrSectionSelection = false;
				resetArrSectionPanel = false;
				refreshActual = true;
				checkManual = true;
			}

			if (instrumentTabPane.getSelectedIndex() < 5) {
				if (ArrangementGUI.switchTabPaneAfterApply) {
					ArrangementGUI.switchTabPaneAfterApply = false;
					instrumentTabPane.setSelectedIndex(6);
					ArrangementGUI.arrSection.setSelectedIndexWithProperty(0, true);
				}
				if (ArrangementGUI.switchTabPaneToScoreAfterApply) {
					ArrangementGUI.switchTabPaneToScoreAfterApply = false;
					if (instrumentTabPane.getComponents().length > 7) {
						instrumentTabPane.setSelectedIndex(7);
					}
					ArrangementGUI.arrSection.setSelectedIndexWithProperty(0, true);
				}
			}


		} else if (action.startsWith("ArrangementClearPanels")) {
			String selItem = ArrangementGUI.arrSection.getVal();
			if (!ArrangementGUI.GLOBAL.equals(selItem)) {
				Integer secOrder = Integer.valueOf(selItem.split(":")[0]);
				Section sec = ArrangementGUI.actualArrangement.getSections().get(secOrder - 1);
				// parts
				sec.setMelodyParts(null);
				sec.setBassParts(null);
				sec.setChordParts(null);
				sec.setArpParts(null);
				sec.setDrumParts(null);
			}
		} else if (action.startsWith("ArrangementAddNewSection")) {
			String selItem = null;
			Integer col = null;
			if (action.contains(",")) {
				selItem = action.split(",")[1];
				col = SectionDropDownCheckButton.popupIndex - 1;
			} else {
				selItem = ArrangementGUI.newSectionBox.getVal();
			}
			if (OMNI.EMPTYCOMBO.equals(selItem)) {
				return;
			}
			if (instrumentTabPane.getSelectedIndex() != 5) {
				Section addedSec = ArrangementGUI.actualArrangement
						.addDefaultSection(ArrangementGUI.scrollableArrangementActualTable, selItem, col);
				addedSec.recalculatePartVariationMapBoundsIfNeeded();
				ArrangementGUI.arrangement.initPartInclusionMapIfNull();
				addedSec.generatePresences(
						ArrangementGUI.arrangementSeed.getValue() != 0 ? new Random(ArrangementGUI.arrangementSeed.getValue())
								: new Random(),
						false);
				resetArrSectionSelection = ArrangementGUI.actualArrangement.getSections()
						.indexOf(addedSec) == ArrangementGUI.arrSection.getSelectedIndex() - 2;
				resetArrSectionPanel = true;
				refreshActual = true;
				checkManual = true;
			} else {
				ArrangementGUI.arrangement.addDefaultSection(ArrangementGUI.scrollableArrangementTable, selItem);
				if (ArrangementGUI.arrangement.getSections().size() > maxLength) {
					ArrangementGUI.pieceLength.setText("" + ++maxLength);
				}
			}
			ArrangementGUI.newSectionBox.setSelectedIndex(0);
		} else if (action.startsWith("ArrangementRemove,")) {
			Integer secIndex = Integer.valueOf(action.split(",")[1]);
			if (instrumentTabPane.getSelectedIndex() == 5) {
				ArrangementGUI.arrangement.removeSectionExact(ArrangementGUI.scrollableArrangementTable, secIndex);
			} else {
				//actualArrangement.resortByIndexes(scrollableArrangementActualTable);
				ArrangementGUI.actualArrangement.removeSectionExact(ArrangementGUI.scrollableArrangementActualTable, secIndex);
				resetArrSectionSelection = secIndex < ArrangementGUI.arrSection.getSelectedIndex();
				resetArrSectionPanel = true;
				refreshActual = true;
				checkManual = true;
			}
		} else if (action.startsWith("ArrangementAdd,")) {
			LG.i(("add exact"));
			Integer secIndex = Integer.valueOf(action.split(",")[1]);
			if (instrumentTabPane.getSelectedIndex() == 5) {
				ArrangementGUI.arrangement.duplicateSectionExact(ArrangementGUI.scrollableArrangementTable, secIndex);
			} else {
				//actualArrangement.resortByIndexes(scrollableArrangementActualTable);
				ArrangementGUI.actualArrangement.duplicateSectionExact(ArrangementGUI.scrollableArrangementActualTable, secIndex);
				resetArrSectionSelection = secIndex < ArrangementGUI.arrSection.getSelectedIndex() - 1;
				resetArrSectionPanel = true;
				refreshActual = true;
				checkManual = true;
			}
			if (ArrangementGUI.arrangement.getSections().size() > maxLength) {
				ArrangementGUI.pieceLength.setText("" + ++maxLength);
			}
		}

		if (!refreshActual) {
			ArrangementGUI.scrollableArrangementTable.setModel(ArrangementGUI.arrangement.convertToTableModel());
		} else {
			int index = ArrangementGUI.arrSection.getSelectedIndex();
			ArrangementGUI.arrangementGUI.setActualModel(ArrangementGUI.actualArrangement.convertToActualTableModel(), resetArrSectionPanel);
			if (resetArrSectionSelection) {
				ArrangementGUI.arrSection.setSelectedIndexWithProperty(0, true);
			} else {
				ArrangementGUI.arrSection.setSelectedIndexWithProperty(index, true);
			}
			ArrangementGUI.arrSection.repaint();
			refreshVariationPopupButtons(ArrangementGUI.actualArrangement.getSections().size());
		}
		if (checkManual) {
			ArrangementGUI.manualArrangement.setSelected(true);
			ArrangementGUI.manualArrangement.repaint();
		}
		recalculateTabPaneCounts();
	}

	@Deprecated private void __openPartInclusionPopup() {
		ArrangementGUI.arrangement.recalculatePartInclusionMapBoundsIfNeeded();
		new ArrangementPartInclusionPopup(ArrangementGUI.arrangement);
	}

	@Deprecated private void __openGlobalVariationPopup() {
		new ArrangementGlobalVariationPopup(ArrangementGUI.arrangement);
	}

	@Deprecated private void __openPatternManagerPopup() {
		new PatternManagerPopup();
	}

	private void applyCustomPanelsToSection(String action, int replacedPartNum, Integer secOrder) {
		int lastSecOrder = secOrder + 1;
		if (action.endsWith("+")) {
			lastSecOrder = ArrangementGUI.actualArrangement.getSections().size() + 1;
		} else if (action.contains(",")) {
			String lastSecIndexString = action.split(",")[1];
			lastSecOrder = Integer.valueOf(lastSecIndexString) + 1;
		}

		for (int i = secOrder; i < lastSecOrder; i++) {
			Section sec = ArrangementGUI.actualArrangement.getSections().get(i - 1);
			// parts
			switch (replacedPartNum) {
			case 0:
				sec.setMelodyParts(
						(List<MelodyPart>) (List<?>) getInstPartsFromCustomSectionInstPanels(0));
				break;
			case 1:
				sec.setBassParts(
						(List<BassPart>) (List<?>) getInstPartsFromCustomSectionInstPanels(1));
				break;
			case 2:
				sec.setChordParts(
						(List<ChordPart>) (List<?>) getInstPartsFromCustomSectionInstPanels(2));
				break;
			case 3:
				sec.setArpParts(
						(List<ArpPart>) (List<?>) getInstPartsFromCustomSectionInstPanels(3));
				break;
			case 4:
				sec.setDrumParts(
						(List<DrumPart>) (List<?>) getInstPartsFromCustomSectionInstPanels(4));
				break;
			default:
				break;
			}
			if (replacedPartNum < 5) {
				String suffix = "";
				if (sec.hasCustomizedParts()) {
					suffix = "*";
				}
				ArrangementGUI.arrSection.getButtons().get(i).setText(i + ": " + sec.getType() + suffix);
			}
		}
	}

	@Deprecated public static void __openVariationPopup(int secOrder) {
		if (ArrangementGUI.varPopup != null) {
			ArrangementGUI.varPopup.getFrame().dispose();
		}
		ArrangementGUI.recalculateActualArrangementSection(secOrder - 1);
		ArrangementGUI.varPopup = new VariationPopup(secOrder, ArrangementGUI.actualArrangement.getSections().get(secOrder - 1),
				new Point(SwingUtils.getMouseLocation().x,
						vibeComposerGUI.getLocation().y),
				vibeComposerGUI.getSize());
	}

	@Deprecated public static void __recalculateActualArrangementSection(int secOrder) {
		if (ArrangementGUI.actualArrangement == null || ArrangementGUI.actualArrangement.getSections() == null
				|| ArrangementGUI.actualArrangement.getSections().size() <= secOrder) {
			return;
		}

		Section sec = ArrangementGUI.actualArrangement.getSections().get(secOrder);
		if (sec != null) {
			sec.recalculatePartVariationMapBoundsIfNeeded();
		}
	}

	@Deprecated private void __initArrangementSettings(int startY, int anchorSide) {

		ArrangementGUI.arrangementSettings = new JPanel();
		ArrangementGUI.arrangementSettings.setOpaque(false);
		ArrangementGUI.arrangementSettings.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));

		JPanel arrangementSettingsLeft = new JPanel();
		arrangementSettingsLeft.setOpaque(false);
		arrangementSettingsLeft.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		JPanel arrangementSettingsRight = new JPanel();
		arrangementSettingsRight.setOpaque(false);
		arrangementSettingsRight.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		ArrangementGUI.useArrangement = new CheckButton("ARRANGE", false);
		arrangementSettingsLeft.add(ArrangementGUI.useArrangement);
		ArrangementGUI.pieceLength = new JTextField("12", 2);
		//arrangementSettings.add(new JLabel("Max Length:"));
		JButton resetArrangementBtn = makeButton("Reset", "ArrangementReset", 60, 30);
		JButton randomizeArrangementBtn = makeButton("Randomize", e -> {
			Random arrGen = new Random();
			ArrangementGUI.arrangementGUI.handleArrangementAction("ArrangementRandomize", arrGen.nextInt(),
					Integer.valueOf(ArrangementGUI.pieceLength.getText()));
			recalculateTabPaneCounts();
			if (canRegenerateOnChange()) {
				regenerate();
			}
		}, 90);
		JButton arrangementPartInclusionBtn = makeButton("Parts", e -> ArrangementGUI.arrangementGUI.openPartInclusionPopup(),
				60);
		JButton arrangementGlobalVariationBtn = makeButton("Vars", e -> ArrangementGUI.arrangementGUI.openGlobalVariationPopup(),
				50);
		JButton patternManagerBtn = makeButton("Patterns", e -> ArrangementGUI.arrangementGUI.openPatternManagerPopup(), 70);

		ArrangementGUI.randomizeArrangementOnCompose = makeCheckBox("on Compose", true, true);

		List<CheckButton> defaultButtons = new ArrayList<>();
		defaultButtons
				.add(new SectionDropDownCheckButton(ArrangementGUI.GLOBAL, true, OMNI.alphen(Color.pink, 70)));
		ArrangementGUI.arrSection = new ArrangementSectionSelectorPanel(new ArrayList<>(), defaultButtons);

		JButton commitPanelBtn = makeButton("Apply", "ArrangementApply", 50, 30);
		JButton commitAllPanelBtn = makeButton("Apply..", e -> openApplyCustomSectionPopup(), 60);
		JButton undoPanelBtn = makeButton("<-*",
				e -> ArrangementGUI.arrSection.setSelectedIndexWithProperty(ArrangementGUI.arrSection.getSelectedIndex(), true),
				30);

		JButton clearPanelBtn = makeButton("X*", e -> {
			if (!ArrangementGUI.GLOBAL.equals(ArrangementGUI.arrSection.getVal())) {
				Section sec = ArrangementGUI.actualArrangement.getSections()
						.get(ArrangementGUI.arrSection.getSelectedIndex() - 1);
				if (sec.hasCustomizedParts()) {
					sec.resetCustomizedParts(VibeComposerGUI.instrumentTabPane.getSelectedIndex());
					ArrangementGUI.arrangementGUI.setActualModel(ArrangementGUI.actualArrangement.convertToActualTableModel(), false);
					if (!sec.hasCustomizedParts()) {
						CheckButton cb = ArrangementGUI.arrSection.getCurrentButton();
						cb.setText(cb.getText().substring(0, cb.getText().length() - 1));
						cb.repaint();
					}

					ArrangementGUI.arrSection.setSelectedIndexWithProperty(ArrangementGUI.arrSection.getSelectedIndex(), true);
				}
			}
		}, 30);

		JButton clearAllPanelsBtn = makeButton("CLR*", e -> {
			ArrangementGUI.actualArrangement.getSections().forEach(s -> s.resetCustomizedParts());
			ArrangementGUI.arrangementGUI.setActualModel(ArrangementGUI.actualArrangement.convertToActualTableModel(), false);
			ArrangementGUI.arrSection.getButtons().forEach(cb -> {
				if (!ArrangementGUI.GLOBAL.equals(cb.getText()) && cb.getText().contains("*")) {
					cb.setText(cb.getText().substring(0, cb.getText().length() - 1));
					repaint();
				}
			});
			ArrangementGUI.scrollableArrangementActualTable.repaint();
		}, 40);

		JButton copySelectedBtn = makeButton("Cc", "ArrangementAddLast", 30, 30);
		JButton removeSelectedBtn = makeButton("X", "ArrangementRemoveLast", 30, 30);
		ArrangementGUI.newSectionBox = new ScrollComboBox<>(false);
		ArrangementGUI.newSectionBox.addItem(OMNI.EMPTYCOMBO);
		for (SectionType type : Section.SectionType.values()) {
			ArrangementGUI.newSectionBox.addItem(type.toString());
		}

		JButton addNewSectionBtn = makeButton("Add", "ArrangementAddNewSection", 35, 30);

		arrangementSettingsLeft.add(randomizeArrangementBtn);
		arrangementSettingsLeft.add(ArrangementGUI.randomizeArrangementOnCompose);
		arrangementSettingsLeft.add(resetArrangementBtn);
		//arrangementSettings.add(pieceLength);

		ArrangementGUI.arrangementVariationChance = new DetachedKnobPanel("Section<br>Variations", 30);
		arrangementSettingsLeft.add(ArrangementGUI.arrangementVariationChance);
		ArrangementGUI.arrangementPartVariationChance = new DetachedKnobPanel("Part<br>Variations", 25);
		arrangementSettingsLeft.add(ArrangementGUI.arrangementPartVariationChance);
		arrangementSettingsLeft.add(arrangementPartInclusionBtn);
		arrangementSettingsLeft.add(arrangementGlobalVariationBtn);
		arrangementSettingsLeft.add(patternManagerBtn);

		ArrangementGUI.arrangementMiddleColoredPanel = new JPanel();
		ArrangementGUI.arrangementMiddleColoredPanel.add(new JLabel("                                      "));
		ArrangementGUI.arrangementSettings.add(arrangementSettingsLeft);
		ArrangementGUI.arrangementSettings.add(ArrangementGUI.arrangementMiddleColoredPanel);


		ArrangementGUI.manualArrangement = new CheckButton("MANUAL", false);
		arrangementSettingsRight.add(ArrangementGUI.manualArrangement);
		arrangementSettingsRight.add(commitPanelBtn);
		arrangementSettingsRight.add(commitAllPanelBtn);
		arrangementSettingsRight.add(undoPanelBtn);
		arrangementSettingsRight.add(clearPanelBtn);
		arrangementSettingsRight.add(clearAllPanelsBtn);

		arrangementSettingsRight.add(ArrangementGUI.newSectionBox);
		arrangementSettingsRight.add(addNewSectionBtn);
		arrangementSettingsRight.add(copySelectedBtn);
		arrangementSettingsRight.add(removeSelectedBtn);

		arrangementSettingsRight.add(new JLabel("Seed"));
		ArrangementGUI.arrangementSeed = new RandomValueButton(0);
		arrangementSettingsRight.add(ArrangementGUI.arrangementSeed);
		ArrangementGUI.arrangementSettings.add(arrangementSettingsRight);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;

		ArrangementGUI.arrSectionPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(scrollPaneDimension.width, 45);
			}
		};
		ArrangementGUI.arrSectionPane.setViewportView(ArrangementGUI.arrSection);
		ArrangementGUI.arrSectionPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		ArrangementGUI.arrSectionPane.getHorizontalScrollBar().setUnitIncrement(32);
		ArrangementGUI.arrSectionPane.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		ArrangementGUI.arrSectionPane.setOpaque(true);
		ArrangementGUI.arrSection.setOpaque(true);
		everythingPanel.add(ArrangementGUI.arrSectionPane, constraints);
		constraints.gridy = startY + 1;
		everythingPanel.add(ArrangementGUI.arrangementSettings, constraints);

		ArrangementGUI.scrollableArrangementTable = new JTable(5, 5) {

			private static final long serialVersionUID = 3846279087936376003L;

			@Override
			public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
				Component comp = super.prepareRenderer(renderer, row, col);
				comp.setForeground(isDarkMode ? ArrangementGUI.arrangementDarkModeText : ArrangementGUI.arrangementLightModeText);
				if (getModel().getColumnCount() <= col) {
					return comp;
				}
				if (row == 0) {
					arrangementTableProcessSectionType(comp,
							(String) getModel().getValueAt(row, col));
					return comp;
				}

				if (row == 1) {
					comp.setBackground(new Color(100, 150, 150));
					return comp;
				}

				Object objValue = getModel().getValueAt(row,
						ArrangementGUI.scrollableArrangementTable.convertColumnIndexToModel(col));
				Integer value = (objValue instanceof String) ? Integer.valueOf((String) objValue)
						: (Integer) objValue;
				if (value > 100) {
					value = 100;
					getModel().setValueAt(value, row, col);
				} else if (value < 0) {
					value = 0;
					getModel().setValueAt(value, row, col);
				}
				arrangementTableProcessComponent(comp, row, col, String.valueOf(value),
						new int[] { 0, 0, 100, 100, 100, 100, 100 }, false);

				return comp;
			}
		};

		ArrangementGUI.arrangement = new Arrangement();
		ArrangementGUI.actualArrangement = new Arrangement();
		ArrangementGUI.arrangement.generateDefaultArrangement();

		ArrangementGUI.scrollableArrangementTable.setModel(ArrangementGUI.arrangement.convertToTableModel());
		ArrangementGUI.arrangementScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};
		ArrangementGUI.scrollableArrangementTable.setRowHeight(35);
		ArrangementGUI.scrollableArrangementTable.setFont(new Font("Calibri", Font.PLAIN, 15));

		ArrangementGUI.arrangementScrollPane.setViewportView(ArrangementGUI.scrollableArrangementTable);
		JList<String> list = new JList<>();
		list.setListData(
				new String[] { "Section", "Bars", "Melody%", "Bass%", "Chord%", "Arp%", "Drum%" });
		list.setFixedCellHeight(ArrangementGUI.scrollableArrangementTable.getRowHeight()
				+ ArrangementGUI.scrollableArrangementTable.getRowMargin());
		ArrangementGUI.arrangementScrollPane.setRowHeaderView(list);
		ArrangementGUI.arrangementScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		ArrangementGUI.arrangementScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		//actualArrangement.generateDefaultArrangement();
		if (ArrangementGUI.useArrangement.isSelected()) {
			ArrangementGUI.arrangement.setPreviewChorus(false);
			ArrangementGUI.actualArrangement.setPreviewChorus(false);
		} else {
			ArrangementGUI.arrangement.setPreviewChorus(true);
			ArrangementGUI.actualArrangement.setPreviewChorus(true);
			ArrangementGUI.actualArrangement.resetArrangement();
		}
		ArrangementGUI.scrollableArrangementTable.setRowSelectionAllowed(false);
		ArrangementGUI.scrollableArrangementTable.setColumnSelectionAllowed(true);
		ArrangementGUI.scrollableArrangementTable.getTableHeader().setPreferredSize(
				new Dimension(scrollPaneDimension.width - ArrangementGUI.arrangementRowHeaderWidth, 30));
		ArrangementGUI.scrollableArrangementTable.getColumnModel()
				.addColumnModelListener(new TableColumnModelListener() {
					@Override
					public void columnMoved(TableColumnModelEvent e) {
						ArrangementGUI.arrangementTableColumnDragging = true;
					}

					@Override
					public void columnAdded(TableColumnModelEvent e) {
						// Auto-generated method stub

					}

					@Override
					public void columnRemoved(TableColumnModelEvent e) {
						// Auto-generated method stub

					}

					@Override
					public void columnMarginChanged(ChangeEvent e) {
						// Auto-generated method stub

					}

					@Override
					public void columnSelectionChanged(ListSelectionEvent e) {
						// Auto-generated method stub

					}

				});
		ArrangementGUI.scrollableArrangementTable.getTableHeader().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				LG.d(("MOVED HEADER"));
				ArrangementGUI.arrangement.resortByIndexes(ArrangementGUI.scrollableArrangementTable, false);
				ArrangementGUI.arrangementTableColumnDragging = false;
			}
		});
		ArrangementGUI.scrollableArrangementTable.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent evt) {
				int row = ArrangementGUI.scrollableArrangementTable.rowAtPoint(evt.getPoint());
				int secOrder = ArrangementGUI.scrollableArrangementTable.columnAtPoint(evt.getPoint());

				//LG.i(("Clicked! " + row + ", " + secOrder));
				if (row == 0 && secOrder >= 0) {
					boolean rClick = SwingUtilities.isRightMouseButton(evt);
					boolean mClick = !rClick && SwingUtilities.isMiddleMouseButton(evt);
					if (rClick) {
						ArrangementGUI.arrangementGUI.handleArrangementAction("ArrangementRemove," + secOrder, 0, 0);
					} else if (mClick) {
						//LG.i(("mClick"));
						ArrangementGUI.arrangementGUI.handleArrangementAction("ArrangementAdd," + secOrder, 0, 0);
					}

				}
			}
		});


		ArrangementGUI.scrollableArrangementActualTable = new JTable(5, 5) {
			private static final long serialVersionUID = 1L;

			@Override
			public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
				Component comp = super.prepareRenderer(renderer, row, col);
				Object value = getModel().getValueAt(row,
						ArrangementGUI.scrollableArrangementActualTable.convertColumnIndexToModel(col));
				comp.setForeground(isDarkMode ? ArrangementGUI.arrangementDarkModeText : ArrangementGUI.arrangementLightModeText);
				if (value == null)
					return comp;
				if (getModel().getColumnCount() <= col) {
					return comp;
				}
				if (row == 0) {
					arrangementTableProcessSectionType(comp,
							(String) getModel().getValueAt(row, col));
					return comp;
				}

				int height = (int) (350 / getModel().getRowCount());
				int width = Math.max(TABLE_COLUMN_MIN_WIDTH,
						(int) ((VibeComposerGUI.scrollPaneDimension.getWidth() - 60)
								/ getModel().getColumnCount()) - 2);

				if (row == 1) {
					return new SectionInfoCellRenderer(width, height, col);
				}

				Collection<? extends Object> stringables = value instanceof String
						? Collections.singleton((String) value)
						: (Collection<? extends Object>) value;

				/*arrangementTableProcessComponent(comp, row, col, value,
						new int[] { 0, 0, MelodyGUI.melodyPanels.size(), 1, ChordGUI.chordPanels.size(),
								ArpGUI.arpPanels.size(), DrumGUI.drumPanels.size() },
						true);*/
				return new CollectionCellRenderer(stringables, width, height, row - 2, col);
			}
		};
		ArrangementGUI.scrollableArrangementActualTable.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent evt) {
				processActualArrangementMouseEvent(evt);
			}

			@Override
			public void mouseReleased(MouseEvent evt) {
				if (ArrangementGUI.copyDragging) {
					processActualArrangementCopyDragging(evt);
					resetCopyDrag();
				}
			};
		});

		ArrangementGUI.scrollableArrangementActualTable.addMouseMotionListener(new MouseMotionListener() {

			@Override
			public void mouseMoved(MouseEvent e) {
				boolean repaintAnyway = ArrangementGUI.highlightedTableCell != null;
				ArrangementGUI.highlightedTableCell = calculateCurrentTableSubcell(e);
				ArrangementGUI.arrangementActualTableMousePoint = new Point(e.getPoint());
				if (ArrangementGUI.highlightedTableCell != null || repaintAnyway) {
					ArrangementGUI.scrollableArrangementActualTable.repaint();
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				boolean repaintAnyway = ArrangementGUI.highlightedTableCell != null;
				ArrangementGUI.highlightedTableCell = calculateCurrentTableSubcell(e);
				ArrangementGUI.arrangementActualTableMousePoint = new Point(e.getPoint());
				if (ArrangementGUI.highlightedTableCell != null || repaintAnyway) {
					ArrangementGUI.scrollableArrangementActualTable.repaint();
				}
			}
		});

		//scrollableArrangementActualTable.setDefaultRenderer(Iterable.class, new ListCellRenderer());

		ArrangementGUI.scrollableArrangementActualTable.setRowHeight(35);
		ArrangementGUI.scrollableArrangementActualTable.setFont(new Font("Calibri", Font.PLAIN, 15));
		ArrangementGUI.scrollableArrangementActualTable.setModel(ArrangementGUI.actualArrangement.convertToActualTableModel());
		ArrangementGUI.arrangementActualScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};

		JList<String> actualList = new JList<>();
		actualList.setListData(
				new String[] { "", "Section", "Info", "Melody", "Bass", "Chord", "Arp", "Drum" });
		actualList.setFixedCellHeight(ArrangementGUI.scrollableArrangementActualTable.getRowHeight()
				+ ArrangementGUI.scrollableArrangementActualTable.getRowMargin());
		ArrangementGUI.arrangementActualScrollPane.setRowHeaderView(actualList);
		ArrangementGUI.arrangementActualScrollPane
				.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		ArrangementGUI.arrangementActualScrollPane.getVerticalScrollBar().setUnitIncrement(16);


		ArrangementGUI.scrollableArrangementActualTable.setColumnSelectionAllowed(true);
		ArrangementGUI.scrollableArrangementActualTable.setRowSelectionAllowed(false);
		ArrangementGUI.scrollableArrangementActualTable.getColumnModel()
				.addColumnModelListener(new TableColumnModelListener() {
					@Override
					public void columnMoved(TableColumnModelEvent e) {
						ArrangementGUI.actualArrangementTableColumnDragging = true;

					}

					@Override
					public void columnAdded(TableColumnModelEvent e) {
						// Auto-generated method stub

					}

					@Override
					public void columnRemoved(TableColumnModelEvent e) {
						// Auto-generated method stub

					}

					@Override
					public void columnMarginChanged(ChangeEvent e) {
						// Auto-generated method stub

					}

					@Override
					public void columnSelectionChanged(ListSelectionEvent e) {
						// Auto-generated method stub

					}

				});
		ArrangementGUI.scrollableArrangementActualTable.getTableHeader().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				LG.i(("MOVED"));
				ArrangementGUI.actualArrangement.resortByIndexes(ArrangementGUI.scrollableArrangementActualTable, true);
				ArrangementGUI.actualArrangementTableColumnDragging = false;
				ArrangementGUI.manualArrangement.setSelected(true);
				ArrangementGUI.manualArrangement.repaint();
			}
		});
		//scrollableArrangementActualTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);


		ArrangementGUI.actualArrangementCombinedPanel = new JPanel();
		ArrangementGUI.actualArrangementCombinedPanel
				.setLayout(new BoxLayout(ArrangementGUI.actualArrangementCombinedPanel, BoxLayout.Y_AXIS));
		ArrangementGUI.scrollableArrangementActualTable.getTableHeader().setPreferredSize(
				new Dimension(scrollPaneDimension.width - ArrangementGUI.arrangementRowHeaderWidth, 30));
		ArrangementGUI.actualArrangementCombinedPanel.add(ArrangementGUI.scrollableArrangementActualTable.getTableHeader());
		ArrangementGUI.actualArrangementCombinedPanel.add(ArrangementGUI.scrollableArrangementActualTable);


		ArrangementGUI.variationButtonsPanel = new JPanel();
		refreshVariationPopupButtons(1);
		ArrangementGUI.actualArrangementCombinedPanel.add(ArrangementGUI.variationButtonsPanel);

		ArrangementGUI.arrangementActualScrollPane.setViewportView(ArrangementGUI.actualArrangementCombinedPanel);

		instrumentTabPane.addTab("Arrangement", ArrangementGUI.arrangementScrollPane);
		instrumentTabPane.addTab("Generated Arrangement", ArrangementGUI.arrangementActualScrollPane);


		//toggleableComponents.add(arrSection);
		//toggleableComponents.add(commitPanelBtn);
		toggleableComponents.add(commitAllPanelBtn);
		toggleableComponents.add(undoPanelBtn);
		toggleableComponents.add(clearPanelBtn);
		toggleableComponents.add(clearAllPanelsBtn);

		ArrangementGUI.arrangementGUI.resetArrSection();
	}

	protected Triple<Integer, Integer, Integer> calculateCurrentTableSubcell(MouseEvent evt) {
		int row = ArrangementGUI.scrollableArrangementActualTable.rowAtPoint(evt.getPoint());
		int secOrder = ArrangementGUI.scrollableArrangementActualTable.columnAtPoint(evt.getPoint());

		//LG.d(("Current subcell: " + row + ", " + secOrder));
		if (row >= 2 && secOrder >= 0) {
			int part = row - 2;
			double orderPercentage = calculateMousePointPercentageInTable(row, secOrder);

			int actualSize = getInstList(part).size();
			int visualSize = Math.max(CollectionCellRenderer.MIN_CELLS + 1, actualSize + 1);
			int partAbsoluteOrder = (int) Math.floor(orderPercentage * visualSize);

			//LG.d("COPY - Selected subcell: " + (partAbsoluteOrder + 1));
			if ((actualSize > CollectionCellRenderer.MIN_CELLS && partAbsoluteOrder == actualSize)
					|| (actualSize <= CollectionCellRenderer.MIN_CELLS
							&& partAbsoluteOrder == CollectionCellRenderer.MIN_CELLS)) {
				//LG.d("Can't copy: randomizer cell not a valid target - " + (partAbsoluteOrder + 1));
				return null;
			} else if (partAbsoluteOrder >= actualSize) {
				//LG.d("Can't copy: subcell not present in part - " + (partAbsoluteOrder + 1));
				return null;
			}
			return Triple.of(part, partAbsoluteOrder, secOrder);
		}
		return null;
	}

	public void switchPanelsForSectionSelection(String selItem) {
		List<InstPanel> addedPanels = new ArrayList<>();

		if (ArrangementGUI.GLOBAL.equals(selItem)) {
			LG.i(("Resetting to normal panels!"));
			ArrangementGUI.arrangementMiddleColoredPanel.setBackground(panelColorHigh.brighter());
			for (int i = 0; i < 5; i++) {
				JScrollPane pane = getInstPane(i);
				List<? extends InstPanel> panels = getInstList(i);
				for (Component c : ((JPanel) pane.getViewport().getView()).getComponents()) {
					if (c instanceof InstPanel) {
						InstPanel ip = (InstPanel) c;
						//LG.i(("Switching panel!"));
						((JPanel) pane.getViewport().getView()).remove(ip);
					}
				}
				panels.forEach(p -> {
					((JPanel) pane.getViewport().getView()).add(p);
					p.setVisible(false);
					//p.setBackground(panelColorLow.darker());
				});
				addedPanels.addAll(panels);
			}
		} else {
			LG.i(("Switching panels!"));
			ArrangementGUI.arrangementMiddleColoredPanel.setBackground(uiColor().darker().darker());
			int sectionOrder = Integer.valueOf(selItem.split(":")[0]) - 1;
			Section sec = ArrangementGUI.actualArrangement.getSections().get(sectionOrder);
			for (int i = 0; i < 5; i++) {
				JScrollPane pane = getInstPane(i);
				List<InstPanel> sectionPanels = new ArrayList<>();
				List<Integer> missingPanels = new ArrayList<>();
				getInstList(i).forEach(e -> missingPanels.add(e.getPanelOrder()));
				if (sec.getInstPartList(i) != null) {
					//LG.i(("Creating panels from section parts! " + i));
					List<? extends InstPart> ips = sec.getInstPartList(i);
					for (Component c : ((JPanel) pane.getViewport().getView()).getComponents()) {
						if (c instanceof InstPanel) {
							int order = ((InstPanel) c).getAbsoluteOrder();
							if (order < ips.size()) {
								((JPanel) pane.getViewport().getView()).remove(c);
								InstPanel pCopy = InstPanel.makeInstPanel(i, VibeComposerGUI.this);
								pCopy.setFromInstPart(ips.get(order));
								sectionPanels.add(pCopy);
								missingPanels.remove(Integer.valueOf(order));
							}
						}
					}
				}
				if (!missingPanels.isEmpty()) {
					//LG.i(("Making copies of normal panels! " + i));
					List<? extends InstPanel> panels = getInstList(i).stream()
							.filter(e -> missingPanels.contains(e.getPanelOrder()))
							.collect(Collectors.toList());
					//Set<Integer> presence = sec.getPresence(i);
					for (Component c : ((JPanel) pane.getViewport().getView()).getComponents()) {
						if (c instanceof InstPanel) {
							InstPanel ip = (InstPanel) c;

							//LG.i(("Switching panel!"));
							int order = ip.getPanelOrder();
							if (missingPanels.contains(order)) {
								((JPanel) pane.getViewport().getView()).remove(ip);
								/*if (!presence.contains(ip.getPanelOrder())) {
									continue;
								}*/
								InstPanel p = panels.stream()
										.filter(e -> e.getPanelOrder() == order).findFirst().get();
								InstPanel pCopy = InstPanel.makeInstPanel(i, VibeComposerGUI.this);
								pCopy.setRelatedSection(sec);
								pCopy.setFromInstPart(p.toInstPart(0));
								sectionPanels.add(pCopy);
							}
						}

					}
				}
				sectionPanels.sort(Comparator.comparing(e -> e.getPanelOrder()));
				sectionPanels.forEach(p -> {
					p.toggleEnabledCopyRemove(false);
					p.toggleGlobalElements(false);
					if (p.getPartClass() == DrumPart.class) {
						p.getInstrumentBox().setEnabled(true);
					}
					p.getToggleableComponents().forEach(g -> g.setVisible(isFullMode));
					p.setVisible(false);
					((JPanel) pane.getViewport().getView()).add(p);
				});
				addedPanels.addAll(sectionPanels);
			}
		}
		ArrangementGUI.arrangementMiddleColoredPanel.repaint();
		addedPanels.forEach(p -> p.setVisible(true));
		toggleButtonEnabledForPanels();
		for (int i = 0; i < 5; i++) {
			JScrollPane pane = getInstPane(i);
			pane.repaint();
		}
		if (instrumentTabPane.getSelectedIndex() == 6) {
			ArrangementGUI.actualArrangement.getSections().forEach(s -> s.initPartMapFromOldData());
			ArrangementGUI.scrollableArrangementActualTable.repaint();
		}
	}

	private void processActualArrangementMouseEvent(java.awt.event.MouseEvent evt) {
		int row = ArrangementGUI.scrollableArrangementActualTable.rowAtPoint(evt.getPoint());
		int secOrder = ArrangementGUI.scrollableArrangementActualTable.columnAtPoint(evt.getPoint());


		LG.d(("Clicked! " + row + ", " + secOrder));
		boolean rClick = SwingUtilities.isRightMouseButton(evt);
		boolean mClick = !rClick && SwingUtilities.isMiddleMouseButton(evt);
		if (row == 0 && secOrder >= 0) {
			if (rClick) {
				ArrangementGUI.arrangementGUI.handleArrangementAction("ArrangementRemove," + secOrder, 0, 0);
			} else if (mClick) {
				ArrangementGUI.arrangementGUI.handleArrangementAction("ArrangementAdd," + secOrder, 0, 0);
			}
		} else if (row >= 2 && secOrder >= 0) {
			double orderPercentage = calculateMousePointPercentageInTable(row, secOrder);

			int part = row - 2;
			int actualSize = getInstList(part).size();
			int visualSize = Math.max(CollectionCellRenderer.MIN_CELLS + 1, actualSize + 1);
			int partAbsoluteOrder = (int) Math.floor(orderPercentage * visualSize);

			LG.d("Percentage: " + orderPercentage);
			LG.d("Selected subcell: " + (partAbsoluteOrder + 1));
			boolean randomizerButtonPressed = false;
			if ((actualSize > CollectionCellRenderer.MIN_CELLS && partAbsoluteOrder == actualSize)
					|| (actualSize <= CollectionCellRenderer.MIN_CELLS
							&& partAbsoluteOrder == CollectionCellRenderer.MIN_CELLS)) {
				randomizerButtonPressed = true;
			} else if (partAbsoluteOrder >= actualSize) {
				LG.d("Can't interact: subcell not present in part - " + (partAbsoluteOrder + 1));
				return;
			}


			if (rClick || mClick) {
				//LG.d(("Clickable! rClick: " + rClick));
				Section sec = ArrangementGUI.actualArrangement.getSections().get(secOrder);
				boolean hasPresence = !sec.getPresence(part).isEmpty();
				boolean hasVariation = hasPresence && sec.hasVariation(part);

				if (evt.isControlDown()) {
					if (hasPresence) {
						int secOrder2 = secOrder;
						if (mClick) {
							secOrder2++;
							if (secOrder2 >= ArrangementGUI.actualArrangement.getSections().size()) {
								return;
							}
						} else if (rClick) {
							secOrder2--;
							if (secOrder2 < 0) {
								return;
							}
						}
						Section sec2 = ArrangementGUI.actualArrangement.getSections().get(secOrder2);
						sec2.resetAllPresence(part);
						for (int i = 2; i < Section.variationDescriptions[part].length; i++) {
							sec2.removeVariationForAllParts(part, i);
						}
						for (Integer p : sec.getPresence(part)) {
							int absOrder = VibeComposerGUI.getAbsoluteOrder(part, p);
							sec2.setPresence(part, absOrder);
							sec2.setVariation(part, absOrder, sec.getVariation(part, absOrder));
							if (evt.isShiftDown()) {
								// CTRL+SHIFT+RMB/MMB -> also copy all section patterns if available and applied
								UsedPattern pat = sec.getPattern(part, p);
								if (pat != null) {
									PhraseNotes pn = guiConfig.getPatternRaw(pat);
									if (pn != null && pn.isApplied()) {
										sec2.putPattern(part, p, pat);
									}
								}
							}
						}
						sec2.setInstPartList(sec.getInstPartList(part), part);
					}
				} else if (randomizerButtonPressed) {
					if (mClick) {
						if (hasVariation) {
							for (int i = 2; i < Section.variationDescriptions[part].length; i++) {
								sec.removeVariationForAllParts(part, i);
							}
						} else if (hasPresence) {
							sec.generateVariations(new Random(), part);
						}
					} else {
						if (hasPresence) {
							for (int i = 0; i < getInstList(part).size(); i++) {
								sec.resetPresence(part, i);
							}
						} else {
							ArrangementGUI.arrangement.initPartInclusionMapIfNull();
							sec.generatePresences(new Random(), part, ArrangementGUI.arrangement.getInclMap(),
									true);
						}
					}
				} else if (evt.isShiftDown()) {
					boolean hasAnyPresence = ArrangementGUI.actualArrangement.getSections().stream()
							.anyMatch(e -> e.getPresence(part).contains(
									getInstList(part).get(partAbsoluteOrder).getPanelOrder()));
					if (mClick) {
						boolean hasAnyVariation = hasAnyPresence
								&& ArrangementGUI.actualArrangement.getSections().stream().anyMatch(
										e -> !e.getVariation(part, partAbsoluteOrder).isEmpty());
						for (Section asec : ArrangementGUI.actualArrangement.getSections()) {
							if (hasAnyVariation) {
								for (int i = 2; i < Section.variationDescriptions[part].length; i++) {
									asec.removeVariationForPart(part, partAbsoluteOrder, i);
								}
							} else if (hasAnyPresence && asec.getPresence(part).contains(
									getInstList(part).get(partAbsoluteOrder).getPanelOrder())) {
								asec.generateVariationForPartAndOrder(new Random(), part,
										partAbsoluteOrder, false);
							}
						}
					} else {
						if (hasAnyPresence) {
							for (Section asec : ArrangementGUI.actualArrangement.getSections()) {
								asec.initPartMapFromOldData();
								for (int i = 0; i < getInstList(part).size(); i++) {
									asec.resetPresence(part, partAbsoluteOrder);
								}
							}
						} else {
							ArrangementGUI.arrangement.initPartInclusionMapIfNull();
							for (Section asec : ArrangementGUI.actualArrangement.getSections()) {
								asec.initPartMapFromOldData();
								if (new Random().nextInt(100) < asec.getChanceForInst(part)) {
									asec.setPresence(part, partAbsoluteOrder);
								}
							}
						}
					}
				} else {
					boolean hasSinglePresence = sec.getPresence(part)
							.contains(getInstList(part).get(partAbsoluteOrder).getPanelOrder());
					boolean hasSingleVariation = hasSinglePresence
							&& !sec.getVariation(part, partAbsoluteOrder).isEmpty();

					if (mClick) {
						if (hasSingleVariation) {
							for (int i = 2; i < Section.variationDescriptions[part].length; i++) {
								sec.removeVariationForPart(part, partAbsoluteOrder, i);
							}
						} else if (hasSinglePresence) {
							sec.generateVariationForPartAndOrder(new Random(), part,
									partAbsoluteOrder, true);
						}
					} else {
						sec.initPartMapFromOldData();
						if (hasSinglePresence) {
							sec.resetPresence(part, partAbsoluteOrder);
						} else {
							sec.setPresence(part, partAbsoluteOrder);
						}
					}
				}


				ArrangementGUI.arrangementGUI.setActualModel(ArrangementGUI.actualArrangement.convertToActualTableModel(), false);
				refreshVariationPopupButtons(ArrangementGUI.actualArrangement.getSections().size());
				ArrangementGUI.manualArrangement.setSelected(true);
				ArrangementGUI.manualArrangement.repaint();
				ArrangementGUI.scrollableArrangementActualTable.repaint();
			} else {
				int panelOrder = getInstList(part).get(partAbsoluteOrder).getPanelOrder();
				if (evt.isAltDown()) {
					if (secOrder + 1 < ArrangementGUI.arrSection.getItemCount()) {
						ArrangementGUI.arrSection.setSelectedIndexWithProperty(secOrder + 1, true);
						ArrangementGUI.arrSection.repaint();
						instrumentTabPane.setSelectedIndex(part);
						ArrangementGUI.switchTabPaneAfterApply = true;
					}
				} else if (evt.isControlDown()) {
					// begin copy-dragging
					Section sec = ArrangementGUI.actualArrangement.getSections().get(secOrder);
					boolean hasSinglePresence = sec.getPresence(part).contains(panelOrder);
					if (hasSinglePresence && sec.containsPattern(part, panelOrder)) {
						ArrangementGUI.copyDraggingOrigin = Triple.of(part, partAbsoluteOrder, secOrder);
						prepareCustomMidiSubcellCopy(part, panelOrder, sec);

					}
				} else if (currentMidi != null && partAbsoluteOrder < getInstList(part).size()) {
					Section sec = ArrangementGUI.actualArrangement.getSections().get(secOrder);
					boolean hasSinglePresence = sec.getPresence(part).contains(panelOrder);

					if (hasSinglePresence && sec.containsPattern(part, panelOrder)) {
						currentMidiEditorPopup = new MidiEditPopup(sec, part, panelOrder);
						currentMidiEditorPopup.setSec(sec);
						currentMidiEditorSectionIndex = secOrder;
					} else {
						LG.i("Presence: " + hasSinglePresence + ", contains pattern: "
								+ sec.containsPattern(part, panelOrder));
					}
				}
			}

		}
	}

	private double calculateMousePointPercentageInTable(int row, int secOrder) {

		Point mousePoint = SwingUtils.getMouseLocation();
		Point tablePoint = ArrangementGUI.scrollableArrangementActualTable.getLocation();
		SwingUtilities.convertPointToScreen(tablePoint, ArrangementGUI.scrollableArrangementActualTable);
		Rectangle r = ArrangementGUI.scrollableArrangementActualTable.getCellRect(row, secOrder, false);

		mousePoint.x -= tablePoint.x;
		mousePoint.y -= tablePoint.y;

		mousePoint.x -= r.x;
		mousePoint.y -= r.y;


		double orderPercentage = OMNI.clamp((mousePoint.x / (double) r.width), 0.01, 0.99);
		return orderPercentage;
	}

	protected void processActualArrangementCopyDragging(MouseEvent evt) {
		Triple<Integer, Integer, Integer> partOrderSection = calculateCurrentTableSubcell(evt);
		if (partOrderSection != null) {
			PhraseNotes pn = guiConfig.getPatternRaw(ArrangementGUI.copyDraggedPattern);
			if (pn == null) {
				new TemporaryInfoPopup("Invalid pattern for copying!", 1500);
				return;
			}
			Section sec = ArrangementGUI.actualArrangement.getSections().get(partOrderSection.getRight());
			UsedPattern newPattern = ArrangementGUI.copyDraggedPattern;
			int part = partOrderSection.getLeft();
			int panelOrder = getInstList(part).get(partOrderSection.getMiddle()).getPanelOrder();
			sec.putPattern(part, panelOrder, newPattern);
			if (!sec.getPresence(part).contains(panelOrder)) {
				sec.setPresence(part, partOrderSection.getMiddle());
			}
			pn.setApplied(true);
			ArrangementGUI.arrangementGUI.setActualModel(ArrangementGUI.actualArrangement.convertToActualTableModel(), false);
			refreshVariationPopupButtons(ArrangementGUI.actualArrangement.getSections().size());
			ArrangementGUI.manualArrangement.setSelected(true);
			ArrangementGUI.manualArrangement.repaint();
			ArrangementGUI.scrollableArrangementActualTable.repaint();
		} else {
			LG.i("Can't copy custom midi - invalid part!");
		}
	}

	private void prepareCustomMidiSubcellCopy(int part, int panelOrder, Section sec) {
		ArrangementGUI.copyDragging = true;
		ArrangementGUI.copyDraggedPattern = sec.getPattern(part, panelOrder);
		ArrangementGUI.scrollableArrangementActualTable.repaint();
	}

	public void resetCopyDrag() {
		ArrangementGUI.copyDragging = false;
		ArrangementGUI.copyDraggedPattern = null;
		ArrangementGUI.copyDraggingOrigin = null;
		ArrangementGUI.scrollableArrangementActualTable.repaint();
	}

	protected void arrangementTableProcessSectionType(Component comp, String valueAt) {
		int typeOffset = Section.getTypeMelodyOffset(valueAt);
		comp.setBackground(new Color(100 + 15 * typeOffset, 150, 150));
	}

	private void arrangementTableProcessComponent(Component comp, int row, int col, String value,
			int[] maxCounts, boolean actual) {
		if (row >= 2) {

			// 2,3,4,5,6 -> melody, bass, chord, arp, drum counts
			//LG.d("Comp class: " + comp.getClass());
			if (value.isEmpty() || value.equalsIgnoreCase("*")) {
				comp.setBackground(panelColorLow.darker());
			} else {
				int count = (actual) ? (StringUtils.countMatches(value, ",") + 1)
						: Integer.valueOf(value);
				int color = 0;
				if (isDarkMode) {
					color = ArrangementGUI.arrangementDarkModeLowestColor + (70 * count) / maxCounts[row];
					color = Math.min(color, 170);
				} else {
					color = ArrangementGUI.arrangementLightModeHighestColor - (70 * count) / maxCounts[row];
					color = Math.max(color, 130);
				}

				int extraRed = 0;
				if (actual && ArrangementGUI.actualArrangement.getSections().size() > col) {
					double remaining = 255 - color - 1;
					extraRed += ArrangementGUI.actualArrangement.getSections().get(col)
							.countVariationsForPartType(row - 2) * remaining;
					extraRed = Math.min(255 - color - 1, extraRed);
				}

				comp.setBackground(new Color(color + extraRed, color, color));
			}
		} else {
			comp.setBackground(new Color(100, 150, 150));
		}
	}

	@Deprecated
	private void __initScoreSettings(int startY, int anchorSide) {
		JPanel scrollableScorePanel = new JPanel();
		scrollableScorePanel.setLayout(new BoxLayout(scrollableScorePanel, BoxLayout.Y_AXIS));
		scrollableScorePanel.setAutoscrolls(true);
		ScoreGUI.scoreScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};
		ScoreGUI.scoreScrollPane.setViewportView(scrollableScorePanel);

		ScoreGUI.scoreScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		ScoreGUI.scoreScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		ScoreGUI.scoreScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		/*ScoreGUI.scoreScrollPane.getHorizontalScrollBar().addAdjustmentListener(new AdjustmentListener() {
			@Override
			public void adjustmentValueChanged(AdjustmentEvent e) {
				if (ScoreGUI.scorePanel != null) {
					LG.i("Updating pos!");
					SwingUtilities.invokeLater(() -> {
							ScoreGUI.scorePanel.update();

					});
				}
			}
		});*/

		instrumentTabPane.addTab("Score", ScoreGUI.scoreScrollPane);
	}


	private void refreshVariationPopupButtons(int count) {
		/*if (count == variationButtonsPanel.getComponents().length) {
			return;
		}*/
		ArrangementGUI.variationButtonsPanel.removeAll();
		for (int i = 0; i < count; i++) {
			int fI = i;
			JButton butt = new JButton("Edit " + (i + 1)) {
				private static final long serialVersionUID = -374920351085418730L;

				@Override
				public void paintComponent(Graphics guh) {
					super.paintComponent(guh);
					if (ArrangementGUI.actualArrangement != null) {
						if (ArrangementGUI.actualArrangement.getSections() != null
								&& fI < ArrangementGUI.actualArrangement.getSections().size()) {
							if (guh instanceof Graphics2D) {
								Graphics2D g = (Graphics2D) guh;
								Section sec = ArrangementGUI.actualArrangement.getSections().get(fI);
								List<Integer> sectionVars = sec.getSectionVariations();
								if (sectionVars == null) {
									sectionVars = Section.EMPTY_SECTION_VARS;
								}
								int xsizeForIcon = Math.max(16,
										((this.getWidth() / Section.sectionVariationNames.length)
												- 2));
								int currentX = 8;
								for (int j = 0; j < (Section.sectionVariationNames.length + 1)
										/ 2; j++) {
									// in case of swap chords, behave same as custom chords
									if (sectionVars.get(j) > 0
											|| (j == 1 && sec.isCustomChordsEnabled())) {
										g.drawImage(SECTION_VARIATIONS_ICONS.get(j), currentX, 6,
												this);
									}
									currentX += xsizeForIcon + 2;
								}
								if (sec.getTransitionType() > 0) {
									g.drawImage(
											SECTION_TRANSITION_ICONS
													.get(sec.getTransitionType() - 1),
											this.getWidth() - 18, 6, this);
								}

								currentX = 8;
								for (int j = (Section.sectionVariationNames.length + 1)
										/ 2; j < Section.sectionVariationNames.length; j++) {
									if (sectionVars.get(j) > 0) {
										g.drawImage(SECTION_VARIATIONS_ICONS.get(j), currentX,
												this.getHeight() * 3 / 4 - 6, this);
									}
									currentX += xsizeForIcon + 2;
								}
							}
						}

					}

				}
			};
			butt.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					ArrangementGUI.arrangementGUI.openVariationPopup(fI + 1);

				}
			});

			int width = Math.max(TABLE_COLUMN_MIN_WIDTH,
					(scrollPaneDimension.width - ArrangementGUI.arrangementRowHeaderWidth) / count);
			butt.setPreferredSize(new Dimension(width, 50));
			butt.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent evt) {
					if (SwingUtilities.isMiddleMouseButton(evt)) {
						ArrangementGUI.actualArrangement.getSections().get(fI)
								.setSectionVariations(new ArrayList<>());
						recolorVariationPopupButton(butt, ArrangementGUI.actualArrangement.getSections().get(fI));
					}
				}
			});
			recolorVariationPopupButton(butt, ArrangementGUI.actualArrangement.getSections().get(i));
			ArrangementGUI.variationButtonsPanel.add(butt);
		}
	}

	public static void recolorAllVariationButtons() {
		for (Component c : ArrangementGUI.variationButtonsPanel.getComponents()) {
			if (c instanceof JButton) {
				JButton butt = (JButton) c;
				int secOrder = Integer.valueOf(butt.getText().split(" ")[1]);
				Section sec = ArrangementGUI.actualArrangement.getSections().get(secOrder - 1);
				recolorVariationPopupButton(butt, sec);
			}
		}
	}

	public static void recolorVariationPopupButton(JButton butt, Section sec) {
		int count = (sec.getSectionVariations() != null)
				? (int) sec.getSectionVariations().stream().filter(e -> e > 0).count()
				: 0;
		count += (sec.isTransition() ? 1 : 0);
		count += (sec.isCustomChordsEnabled() ? 1 : 0);
		int color = 0;
		int total = Section.sectionVariationNames.length + 1;
		if (isDarkMode) {
			color = ArrangementGUI.arrangementDarkModeLowestColor + (35 * count) / total;
			color = Math.min(color, 135);
		} else {
			color = ArrangementGUI.arrangementLightModeHighestColor - (70 * count) / total;
			color = Math.max(color, 130);
		}

		double extraRed = 0;
		double remaining = 255 - color - 1;
		extraRed = Math.min(remaining, (count * remaining) / (double) total);

		butt.setBackground(new Color(color + (int) (extraRed / 2), color, color));
		butt.repaint();
	}

	private void initRandomButtons(int startY, int anchorSide) {
		JPanel randomButtonsPanel = new JPanel();
		//randomButtonsPanel.setBackground(new Color(60, 20, 60));
		randomButtonsPanel.setLayout(new GridLayout(0, 2));
		randomButtonsPanel.setOpaque(false);
		randomButtonsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		randomButtonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		JButton randomizeInstruments = makeButton("Randomize Inst.", "RandomizeInst");

		JButton randomizeBpm = makeButton("Randomize BPM", e -> randomizeBPM());
		JButton randomizeTranspose = makeButton("Randomize Key", "RandomizeTranspose");

		JPanel randomInstPanel = new JPanel();
		JPanel randomBpmPanel = new JPanel();
		JPanel randomTransposePanel = new JPanel();
		JPanel randomBottomPanel = new JPanel();
		randomInstPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		randomBpmPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		randomTransposePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		randomBottomPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		randomInstPanel.setOpaque(false);
		randomBpmPanel.setOpaque(false);
		randomTransposePanel.setOpaque(false);
		randomBottomPanel.setOpaque(false);


		randomizeInstOnComposeOrGen = makeCheckBox("on Compose/Gen", true, true);
		randomizeBpmOnCompose = makeCheckBox("on Compose", true, true);
		randomizeTransposeOnCompose = makeCheckBox("on Compose", true, true);
		randomizeInstOnComposeOrGen.setAlignmentX(Component.LEFT_ALIGNMENT);
		randomizeBpmOnCompose.setAlignmentX(Component.LEFT_ALIGNMENT);
		randomizeTransposeOnCompose.setAlignmentX(Component.LEFT_ALIGNMENT);


		constraints.anchor = GridBagConstraints.CENTER;


		randomButtonsPanel.add(randomizeInstruments);
		randomButtonsPanel.add(randomizeInstOnComposeOrGen);
		//randomButtonsPanel.add(randomInstPanel);

		randomButtonsPanel.add(randomizeBpm);
		randomButtonsPanel.add(randomizeBpmOnCompose);
		//randomButtonsPanel.add(randomBpmPanel);

		randomButtonsPanel.add(randomizeTranspose);
		randomButtonsPanel.add(randomizeTransposeOnCompose);
		//randomButtonsPanel.add(randomTransposePanel);


		JButton randomizeStrums = makeButton("Randomize Strums", "RandStrums");
		randomizeStrums.setAlignmentX(Component.LEFT_ALIGNMENT);
		randomButtonsPanel.add(randomizeStrums);

		randomizeChordStrumsOnCompose = makeCheckBox("on Compose", false, true);
		//randomButtonsPanel.add(randomizeChordStrumsOnCompose);

		switchOnComposeRandom = makeButton("Untick all 'on Compose'", "UncheckComposeRandom");
		switchOnComposeRandom.setPreferredSize(new Dimension(170, 20));
		switchOnComposeRandom.setAlignmentX(Component.LEFT_ALIGNMENT);
		switchOnComposeRandom.setFont(switchOnComposeRandom.getFont().deriveFont(6));
		enthickenText(switchOnComposeRandom);
		randomButtonsPanel.add(switchOnComposeRandom);


		JPanel transposePanel = new JPanel();
		//transposePanel.setBorder(new BevelBorder(BevelBorder.RAISED));
		//transposePanel.setOpaque(false);
		transposePanel.setPreferredSize(new Dimension(170, 20));
		JButton transposeAllBtn = makeButton("All", e -> randomizeTranspose(false));
		JButton transposeTabBtn = makeButton("Tab", e -> randomizeTranspose(true));
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
		//sidechainPanel.setOpaque(false);
		sidechainPanel.setPreferredSize(new Dimension(170, 20));
		sidechainPatterns = makeButton("All", e -> sidechainPatterns(true, false));
		sidechainPatternsTab = makeButton("Tab", e -> sidechainPatterns(true, true));
		sidechainPatterns.setMargin(new Insets(0, 0, 0, 0));
		sidechainPatternsTab.setMargin(new Insets(0, 0, 0, 0));
		sidechainPatterns.setPreferredSize(new Dimension(35, 20));
		sidechainPatternsTab.setPreferredSize(new Dimension(35, 20));
		sidechainPanel.add(new JLabel("Sidechain"));
		sidechainPanel.add(sidechainPatterns);
		sidechainPanel.add(sidechainPatternsTab);
		randomButtonsPanel.add(sidechainPanel);
		//randomButtonsPanel.add(randomBottomPanel);

		toggleableComponents.add(randomizeStrums);
		//toggleableComponents.add(randomizeChordStrumsOnCompose);
		toggleableComponents.add(sidechainPanel);
		toggleableComponents.add(transposePanel);
		controlPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		controlPanel.add(randomButtonsPanel);

	}


	private void randomizeTranspose(boolean currentTabOnly) {
		int currentTab = instrumentTabPane.getSelectedIndex();
		if (currentTabOnly && currentTab >= 4) {
			new TemporaryInfoPopup("Nothing to transpose in this tab!", null);
			return;
		}
		int start = currentTabOnly ? currentTab : 0;
		int end = currentTabOnly ? currentTab : 3;
		Random rand = new Random();
		for (int i = start; i <= end; i++) {
			List<Integer> availableTransposes = new ArrayList<>(
					(i == 1) ? Arrays.asList(new Integer[] { -12, 0 })
							: Arrays.asList(new Integer[] { -12, 0, 12 }));
			List<InstPanel> panels = getAffectedPanels(i);
			int maxSame = Math.max(2, (int) Math.ceil(panels.size() / 3.0));
			int[] transposesApplied = { 0, 0, 0 };
			for (int j = 0; j < panels.size(); j++) {
				int randed = rand.nextInt(availableTransposes.size());
				int transpose = availableTransposes.get(randed);
				panels.get(j).setTranspose(transpose);


				int transposeIndex = (transpose / 12) + 1;
				transposesApplied[transposeIndex]++;
				if (transposesApplied[transposeIndex] >= maxSame) {
					availableTransposes.remove(Integer.valueOf(transpose));
				}
			}
		}
		if (canRegenerateOnChange()) {
			regenerate();
		}
	}

	public void sidechainPatterns(boolean showPopup, boolean currentTabOnly) {
		int currentTab = instrumentTabPane.getSelectedIndex();
		if (currentTabOnly && (currentTab <= 1 || instrumentTabPane.getSelectedIndex() >= 5)) {
			new TemporaryInfoPopup("Only chords/arps/drums can be sidechained!", null);
			return;
		}
		int multiplier = currentTabOnly && currentTab < 4 ? 3 : 1;
		// count rhythm weights in a 1/32 grid across 4 chords span
		int[] rhythmGrid = new int[4 * 32];
		Random rand = new Random();
		Random permutationRand = new Random();
		int[] panelChanges = new int[3];
		int start = currentTabOnly ? currentTab : 4;
		int end = currentTabOnly ? currentTab : 2;
		for (int i = start; i >= end; i--) {
			List<? extends InstPanel> panels = getInstList(i);
			int totalChanged = 0;
			for (int j = 0; j < panels.size(); j++) {
				totalChanged += panels.get(j).addToRhythmGrid(rhythmGrid, rand, permutationRand,
						multiplier);
			}
			panelChanges[i - 2] = totalChanged;
			//LG.i("GRID: " + StringUtils.join(rhythmGrid, ','));
		}
		String popupMsg = "Chord/Arp/Drum changes: " + StringUtils.join(panelChanges, '/');
		LG.i(popupMsg);
		if (showPopup) {
			new TemporaryInfoPopup(popupMsg, null);
		}
	}

	private void initMacroParams(int startY, int anchorSide) {
		JPanel macroParams = new JPanel();
		macroParams.setLayout(new GridLayout(2, 0, 0, 0));
		macroParams.setOpaque(false);
		macroParams.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		ChordGUI.chordProgressionLength = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new String[] { "4", "8", "RANDOM" }, ChordGUI.chordProgressionLength);
		setChordProgressionLength(4);
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
		globalSwingOverrideApplyButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent evt) {
				int swing = globalSwingOverrideValue.getInt();
				applyGlobalSwing(swing, false);
			}
		});
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

		//toggleableComponents.add(globalSwingPanel);
		//toggleableComponents.add(useDoubledPanel);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		controlPanel.add(macroParams);
	}

	private void applyGlobalSwing(int swing, boolean customPanels) {
		if (customPanels) {
			for (int i = 0; i < 5; i++) {
				getAffectedPanels(i).forEach(e -> e.setSwingPercent(swing));
			}
		} else {
			for (int i = 0; i < 5; i++) {
				getInstList(i).forEach(e -> e.setSwingPercent(swing));
			}
		}

	}

	@Deprecated
private void __initChordProgressionSettings(int startY, int anchorSide) {
		// CHORD SETTINGS 1 - chord variety
		JPanel chordProgressionSettingsPanel = new JPanel();
		chordProgressionSettingsPanel.setLayout(new GridLayout(2, 0, 0, 0));
		chordProgressionSettingsPanel.setOpaque(false);
		chordProgressionSettingsPanel
				.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		//toggleableComponents.add(chordProgressionSettingsPanel);


		__spiceChance = new DetachedKnobPanel("Spice", 35);
		__spiceAllowDimAug = new CustomCheckBox("Dim/Aug/6th", false);
		__spiceAllow9th13th = new CustomCheckBox("9th/13th", false);
		__spiceForceScale = new CustomCheckBox("Force Scale", true);
		__spiceParallelChance = new DetachedKnobPanel("Aeolian", 10);

		__firstChordSelection = new ScrollComboBox<String>(false);
		__firstChordSelection.addItem("?");
		ScrollComboBox.addAll(MidiUtils.MAJOR_CHORDS.toArray(new String[] {}), __firstChordSelection);
		__firstChordSelection.setVal("?");
		__firstChordSelection.addItemListener(this);

		__lastChordSelection = new ScrollComboBox<String>(false);
		__lastChordSelection.addItem("?");
		ScrollComboBox.addAll(MidiUtils.MAJOR_CHORDS.toArray(new String[] {}), __lastChordSelection);
		__lastChordSelection.addItemListener(this);

		JPanel spiceChancePanel = new JPanel();
		spiceChancePanel.add(__spiceChance);
		spiceChancePanel.setOpaque(false);

		JPanel spiceAllowDimAugPanel = new JPanel();
		spiceAllowDimAugPanel.add(__spiceAllowDimAug);
		spiceAllowDimAugPanel.setOpaque(false);

		JPanel spiceAllow9th13thPanel = new JPanel();
		spiceAllow9th13thPanel.add(__spiceAllow9th13th);
		spiceAllow9th13thPanel.setOpaque(false);


		JPanel spiceForceScalePanel = new JPanel();
		spiceForceScalePanel.add(__spiceForceScale);
		spiceForceScalePanel.setOpaque(false);

		JPanel firstChordsPanel = new JPanel();
		firstChordsPanel.setOpaque(false);
		JPanel lastChordsPanel = new JPanel();
		lastChordsPanel.setOpaque(false);

		JPanel spiceParallelChancePanel = new JPanel();
		spiceParallelChancePanel.add(__spiceParallelChance);
		spiceParallelChancePanel.setOpaque(false);

		firstChordsPanel.add(new JLabel("First:"));
		firstChordsPanel.add(__firstChordSelection);
		lastChordsPanel.add(new JLabel("Last:"));
		lastChordsPanel.add(__lastChordSelection);

		chordProgressionSettingsPanel.add(spiceChancePanel);
		chordProgressionSettingsPanel.add(spiceAllowDimAugPanel);
		chordProgressionSettingsPanel.add(spiceAllow9th13thPanel);

		chordProgressionSettingsPanel.add(spiceForceScalePanel);
		chordProgressionSettingsPanel.add(spiceParallelChancePanel);
		chordProgressionSettingsPanel.add(firstChordsPanel);
		chordProgressionSettingsPanel.add(lastChordsPanel);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		controlPanel.add(chordProgressionSettingsPanel);
	}

	@Deprecated
private void __initCustomChords(int startY, int anchorSide) {
		JPanel customChordsPanel = new JPanel();
		customChordsPanel.setOpaque(false);
		customChordsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		/*__tipLabel = new JLabel(
				"Chord meaning: 1 = I(major), 10 = i(minor), 100 = I(aug), 1000 = I(dim), 10000 = I7(major), "
						+ "100000 = i7(minor), 1000000 = 9th, 10000000 = 13th, 100000000 = Sus4, 1000000000 = Sus2, 10000000000 = Sus7");*/

		__tipLabel = new JLabel();
		//chordToolTip.add(__tipLabel);

		JButton randomizeCustomChords = makeButton("    Randomize Chords    ", e -> {
			__userChordsEnabled.setSelected(true);
			randomizeUserChords();
			__userChordsEnabled.repaint();
		});
		customChordsPanel.add(randomizeCustomChords);

		__userChordsEnabled = new CheckButton("Custom Chords", false);
		customChordsPanel.add(__userChordsEnabled);

		__userChords = new ChordletPanel(600, "Csus4", "Am", "Em", "Gsus4");
		customChordsPanel.add(__userChords);

		JButton normalizeChordsButton = new JButton("N") {
			private static final long serialVersionUID = 4142323272860314396L;
			String checkedChords = "";

			@Override
			public String getToolTipText() {
				if (super.getToolTipText() == null) {
					return null;
				}
				String chords = __userChords.getChordListString();
				if (!chords.equalsIgnoreCase(checkedChords)) {
					putClientProperty(TOOL_TIP_TEXT_KEY,
							(StringUtils.join(MidiUtils.getKeyModesForChordsAndTarget(chords,
									ScaleMode.valueOf(scaleMode.getVal())))));
					checkedChords = chords;
				}

				return super.getToolTipText();
			}
		};
		normalizeChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				List<String> normalizedChords = MidiUtils.processRawChords(
						__userChords.getChordListString(), ScaleMode.valueOf(scaleMode.getVal()));
				if (normalizedChords != null) {
					__userChords.setupChords(normalizedChords);
				}
			}
		});
		normalizeChordsButton.setToolTipText("N");
		customChordsPanel.add(normalizeChordsButton);

		JButton respiceChordsButton = new JButton("S");
		respiceChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				copyGUItoConfig(guiConfig);
				List<String> normalizedChords = MidiUtils
						.respiceChords(__userChords.getChordListString(), guiConfig);
				if (normalizedChords != null) {
					__userChords.setupChords(normalizedChords);
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
				if (__userChords.chordCount() < 1) {
					return;
				}
				List<String> chords = __userChords.getChordList();
				List<String> chords2x = new ArrayList<>(chords);
				chords.forEach(ch -> {
					chords2x.add(ch);
				});
				__userChords.setupChords(chords2x);
			}
		});
		customChordsPanel.add(twoExChordsButton);

		JButton ddChordsButton = new JButton("Dd");
		ddChordsButton.setPreferredSize(new Dimension(25, 25));
		ddChordsButton.setMargin(new Insets(0, 0, 0, 0));
		ddChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (__userChords.chordCount() < 1) {
					return;
				}
				List<String> chords = __userChords.getChordList();
				List<String> chordsDd = new ArrayList<>();
				chords.forEach(ch -> {
					chordsDd.add(ch);
					chordsDd.add(ch);
				});
				__userChords.setupChords(chordsDd);
			}
		});
		customChordsPanel.add(ddChordsButton);

		JButton dotdotChordsButton = new JButton("..");
		dotdotChordsButton.setPreferredSize(new Dimension(25, 25));
		dotdotChordsButton.setMargin(new Insets(0, 0, 0, 0));
		dotdotChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (__userChords.chordCount() < 1) {
					return;
				}
				List<Chordlet> chordlets = __userChords.getChordlets();
				List<String> chordsDotDot = new ArrayList<>();
				chordlets.forEach(ch -> {
					chordsDotDot.add(MidiUtils
							.makeSpelledChord(MidiUtils.mappedChord(ch.getChordText(), true))
							+ ch.getInversionText());
				});
				__userChords.setupChords(chordsDotDot);
			}
		});
		customChordsPanel.add(dotdotChordsButton);

		JButton ivChordsButton = new JButton("Ch");
		ivChordsButton.setPreferredSize(new Dimension(25, 25));
		ivChordsButton.setMargin(new Insets(0, 0, 0, 0));
		ivChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (__userChords.chordCount() < 1) {
					return;
				}
				List<Chordlet> chordlets = __userChords.getChordlets();
				List<String> chordStrings = new ArrayList<>();
				chordlets.forEach(ch -> {
					chordStrings.add(MidiUtils
							.chordStringFromPitches(MidiUtils.mappedChord(ch.getChordText(), true))
							+ ch.getInversionText());
				});
				__userChords.setupChords(chordStrings);
			}
		});
		customChordsPanel.add(ivChordsButton);

		JButton resetChordsButton = new JButton("R");
		resetChordsButton.setPreferredSize(new Dimension(25, 25));
		resetChordsButton.setMargin(new Insets(0, 0, 0, 0));
		resetChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				__userChords.resetChordlets();
			}
		});
		customChordsPanel.add(resetChordsButton);

		JButton limitChordsButton = new JButton("L");
		limitChordsButton.setPreferredSize(new Dimension(25, 25));
		limitChordsButton.setMargin(new Insets(0, 0, 0, 0));
		limitChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (__userChords.chordCount() > getMaxChordProgressionLength()) {
					__userChords.cullChordsAbove(getMaxChordProgressionLength());
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
				if (MelodyGUI.melodyPanels.isEmpty()) {
					return;
				}
				__userChords.alignWithMelodyTargetNotes(MelodyGUI.melodyPanels.get(0).getChordNoteChoices());
			}
		});
		customChordsPanel.add(melodifyChordsButton);

		JButton chordTransformButton = new JButton("T");
		chordTransformButton.setPreferredSize(new Dimension(25, 25));
		chordTransformButton.setMargin(new Insets(0, 0, 0, 0));
		chordTransformButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				new ChordTransformPopup(__userChords.getChordListString());
			}
		});
		customChordsPanel.add(chordTransformButton);

		__userDurationsEnabled = new CheckButton("Custom Durations", false);
		customChordsPanel.add(__userDurationsEnabled);
		__userChordsDurations = new JTextField("4,4,4,4", 9);
		customChordsPanel.add(__userChordsDurations);


		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(customChordsPanel, constraints);

		toggleableComponents.add(twoExChordsButton);
		toggleableComponents.add(__userDurationsEnabled);
		toggleableComponents.add(__userChordsDurations);
		toggleableComponents.add(dotdotChordsButton);
		toggleableComponents.add(ddChordsButton);
		toggleableComponents.add(normalizeChordsButton);
		toggleableComponents.add(ivChordsButton);
		toggleableComponents.add(limitChordsButton);
		toggleableComponents.add(melodifyChordsButton);
		toggleableComponents.add(chordTransformButton);

	}

	private void initSliderPanel(int startY, int anchorSide) {
		sliderPanel = new JPanel();
		sliderPanel.setOpaque(true);
		sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.X_AXIS));
		sliderPanel.setPreferredSize(new Dimension(1250, 55));

		sliderPanel.add(new JLabel("                                 "));

		slider = new PlayheadRangeSlider();
		slider.setMaximum(0);
		//slider.setToolTipText("Test");
		slider.setDisplayValues(false);
		slider.setSnapToTicks(ExtraSettingsGUI.snapStartToBeat.isSelected());
		//slider.setMinimumSize(new Dimension(1000, 3));
		slider.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseReleased(MouseEvent e) {

				if (isDragging) {
					savePauseInfo();
					if (sequencer != null)
						midiNavigate(slider.getUpperValue());
					isDragging = false;
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
				if (needToRecalculateSoloMuters && !heavyBackgroundTasksInProgress) {
					needToRecalculateSoloMuters = false;
					unapplySolosMutes(true);

					reapplySolosMutes();
					recolorButtons();
				}
			}

			private void recolorButtons() {
				long totalCount = countAllPanels();
				long totalSoloCount = 0;
				long totalMuteCount = 0;

				for (int i = 0; i < 5; i++) {
					long groupSoloCount = getInstList(i).stream()
							.filter(e -> e.getSoloMuter().soloState == SoloMuter.State.FULL)
							.count();
					if (groupSoloCount < getInstList(i).size() && groupSoloCount > 0) {
						groupSoloMuters.get(i).halfSolo();
					} else if (groupSoloCount == 0) {
						groupSoloMuters.get(i).unsolo();
					}
					long groupMuteCount = getInstList(i).stream()
							.filter(e -> e.getSoloMuter().muteState == SoloMuter.State.FULL)
							.count();
					if (groupMuteCount < getInstList(i).size() && groupMuteCount > 0) {
						groupSoloMuters.get(i).halfMute();
					} else if (groupMuteCount == 0) {
						groupSoloMuters.get(i).unmute();
					}
					totalSoloCount += groupSoloCount;
					totalMuteCount += groupMuteCount;
				}
				if (totalSoloCount < totalCount && totalSoloCount > 0) {
					globalSoloMuter.halfSolo();
				} else if (totalSoloCount == 0) {
					globalSoloMuter.unsolo();
				}


				if (totalMuteCount < totalCount && totalMuteCount > 0) {
					globalSoloMuter.halfMute();
				} else if (totalMuteCount == 0) {
					globalSoloMuter.unmute();
				}

			}
		};
		cycle.start();


	}

	public static void recalcGlobals() {
		boolean shouldSolo = false;
		boolean shouldMute = false;
		for (SoloMuter sm : groupSoloMuters) {
			shouldSolo |= (sm.soloState != State.OFF);
			shouldMute |= (sm.muteState != State.OFF);
		}
		if (!shouldSolo) {
			globalSoloMuter.unsolo();
		}
		if (!shouldMute) {
			globalSoloMuter.unmute();
		}
	}

	public static void recalcGroupSolo(int order) {
		long soloCount = getInstList(order).stream()
				.filter(e -> e.getSoloMuter().soloState == SoloMuter.State.FULL).count();
		if (soloCount == 0) {
			groupSoloMuters.get(order).unsolo();
			//globalSoloMuter.solo();
		} else if (soloCount < getInstList(order).size()) {
			groupSoloMuters.get(order).halfSolo();
			//globalSoloMuter.unsolo();
		} else {
			groupSoloMuters.get(order).solo();
		}
	}

	public static void recalcGroupMute(int order) {
		long muteCount = getInstList(order).stream()
				.filter(e -> e.getSoloMuter().muteState == SoloMuter.State.FULL).count();
		if (muteCount == 0) {
			groupSoloMuters.get(order).unmute();
			//globalSoloMuter.mute();
		} else if (muteCount < getInstList(order).size()) {
			groupSoloMuters.get(order).halfMute();
			//globalSoloMuter.unmute();
		} else {
			groupSoloMuters.get(order).mute();
		}
	}

	public static boolean isEnabled(int partNum) {
		return addInst[partNum].isSelected();
	}

	public static int countAllPanels() {
		int count = 0;
		for (int i = 0; i < 5; i++) {
			count += getInstList(i).size();
		}
		return count;
	}

	public static int countAllIncludedPanels() {
		int count = 0;
		for (int i = 0; i < 5; i++) {
			if (isEnabled(i)) {
				List<? extends InstPanel> panels = getInstList(i);
				count += panels.stream().filter(e -> !e.getMuteInst()).count();
			}
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
							if (!isDragging && !isKeySeeking) {
								if (allowedActionsOnZero == 0) {
									slider.setUpperValue(
											(int) (sequencer.getMicrosecondPosition() / 1000));
									if ((currentMidiEditorPopup != null)
											&& currentMidiEditorPopup.isVisible()) {
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
								String newTime = millisecondsToTimeString(slider.getUpperValue());
								if (!newTime.equals(currentTime.getText())) {
									currentTime.setText(newTime);
								}
								int val = slider.getUpperValue();
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
								currentSectionIndex = sectIndex;
								int finalSectIndex = sectIndex;
								String newText = null;
								if (sec == null) {
									newText = "End";
								} else {
									newText = sec.getType().toString();
								}

								if (!sectionText.getText().equalsIgnoreCase(newText)) {
									sectionText.setText(newText);
								}

								Section actualSec = sec;
								int part = instrumentTabPane.getSelectedIndex();
								if (part >= 2 && part <= 4) {
									if (ExtraSettingsGUI.highlightPatterns.isSelected()) {
										SwingUtilities.invokeLater(() -> notifyVisualPatterns(val,
												finalSectIndex, actualSec));
									}
								}

								if (sequencer != null) {
									if (mainBpm.getInt() != (int) guiConfig.getBpm()) {
										sequencer.setTempoFactor(
												(float) (mainBpm.getInt() / guiConfig.getBpm()));
									}
									if (ExtraSettingsGUI.rememberLastPos.isSelected()) {
										savePauseInfo();
									}
								}

							}
						}

						if (loopBeat.isSelected() && !heavyBackgroundTasksInProgress && !isDragging
								&& (sequencer != null)) {
							/*if (ScoreGUI.showScore.isSelected() && !loopBeatCompose.isSelected()) {
								ScoreGUI.showScore.setSelected(false);

							}*/
							int startPos = delayed();
							if (slider.getValue() > startPos) {
								startPos = slider.getValue();
							}
							int newSliderVal = slider.getUpperValue() - startPos;
							boolean sequencerEnded = slider.getMaximum()
									- slider.getUpperValue() < 100 && !sequencer.isRunning();
							double mult = 1;
							if (beatDurationMultiplier.getSelectedIndex() == 0) {
								mult = 0.5;
							} else if (beatDurationMultiplier.getSelectedIndex() == 2) {
								mult = 2;
							}
							if (newSliderVal >= ((mult * loopBeatCount.getInt() * beatFromBpm(0))
									- 50) || sequencerEnded) {
								stopMidi();
								switch (loopBeatCompose.getVal()) {
								case "REGENERATE":
									regenerate();
									break;
								case "COMPOSE":
									ActionEvent action = new ActionEvent(VibeComposerGUI.this,
											ActionEvent.ACTION_PERFORMED, "Compose");
									SwingUtilities.invokeLater(() -> actionPerformed(action));
									break;
								case "REPLAY":
									playMidi(true);
									break;
								default:
									stopMidi();
									throw new IllegalArgumentException(
											"Unsupported loop beat behavior!");
								}
							}
						}

						try {
							/*int tabIndex = instrumentTabPane.getSelectedIndex();
							if (loopBeat.isSelected() || (tabIndex >= 2 && tabIndex <= 4)
									|| (ScoreGUI.scorePopup != null || tabIndex == 7)) {
								sleep(5);
								allowedActionsOnZero = (allowedActionsOnZero + 1) % 5;
							} else {
								allowedActionsOnZero = 0;
								sleep(25);
							}*/
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

			boolean soloCondition = globalSoloMuter.soloState != State.OFF;
			List<InstPanel> panels = getAffectedPanels(part);
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
					int ignoreFillIndex = part == 4 ? 0 : 1;
					isIgnoreFill = sec
							.getVariation(part,
									VibeComposerGUI.getAbsoluteOrder(part, ip.getPanelOrder()))
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

	public static int delayed() {
		return (int) (MidiGenerator.START_TIME_DELAY * 1000 * 60 / guiConfig.getBpm());
	}

	public static int beatFromBpm(int speedAdjustment) {
		int finalVal = (int) (((1000 - speedAdjustment) * 60 * ExtraSettingsGUI.stretchMidi.getInt() / 100.0)
				/ guiConfig.getBpm());
		/*if (useDoubledDurations.isSelected()) {
			finalVal *= 2;
		}*/
		return finalVal;
	}

	public static int sliderMeasureWidth() {
		return (int) (beatFromBpm(0) * MidiGenerator.GENERATED_MEASURE_LENGTH);
	}

	private void initControlPanel(int startY, int anchorSide) {
		JPanel controlSettingsPanel = new JPanel();
		//controlSettingsPanel.setLayout(new BoxLayout(controlSettingsPanel, BoxLayout.Y_AXIS));
		controlSettingsPanel.setOpaque(false);

		controlSettingsPanel.add(scoreGUI.createTransposeControl());

		mainBpm = new DetachedKnobPanel("BPM", 80, ExtraSettingsGUI.bpmLow.getInt(), ExtraSettingsGUI.bpmHigh.getInt());
		mainBpm.getKnob().setStretchAfterCustomInput(true);

		controlSettingsPanel.add(mainBpm);
		scaleMode = new ScrollComboBox<String>();
		String[] scaleModes = new String[MidiUtils.ScaleMode.values().length];
		for (int i = 0; i < MidiUtils.ScaleMode.values().length; i++) {
			scaleModes[i] = MidiUtils.ScaleMode.values()[i].toString();
		}
		ScrollComboBox.addAll(scaleModes, scaleMode);

		controlSettingsPanel.add(new JLabel("Scale"));
		controlSettingsPanel.add(scaleMode);

		randomizeScaleModeOnCompose = makeCheckBox("Rand. on Compose", true, true);
		controlSettingsPanel.add(randomizeScaleModeOnCompose);


		randomSeed = new RandomValueButton(0);
		compose = makeButton("COMPOSE", "Compose");
		compose.setBackground(COMPOSE_COLOR);
		compose.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		//compose.setBorderPainted(true);
		compose.setPreferredSize(new Dimension(80, 40));
		compose.setFont(compose.getFont().deriveFont(Font.BOLD));
		regenerate = makeButton("Regenerate", "Regenerate");
		regenerateStopPlay = makeButton("R!", e -> {
			stopMidi();
			actionPerformed(new ActionEvent(regenerateStopPlay, ActionEvent.ACTION_PERFORMED,
					"Regenerate"));
		});
		regeneratePausePlay = makeButton("R~", e -> {
			regenerateInPlace();
		});
		regenerateStopPlay.setMargin(new Insets(0, 0, 0, 0));
		regeneratePausePlay.setMargin(new Insets(0, 0, 0, 0));
		regenerateStopPlay.setPreferredSize(new Dimension(25, 30));
		regeneratePausePlay.setPreferredSize(new Dimension(25, 30));
		regenerate.setFont(regenerate.getFont().deriveFont(Font.BOLD));
		JButton copySeed = makeButton("Copy Main Seed", "CopySeed");
		JButton copyChords = makeButton("Copy chords", e -> copyChords());
		JButton clearSeed = makeButton("Clear All Seeds", e -> clearAllSeeds());

		controlSettingsPanel.add(regenerate);
		controlSettingsPanel.add(regenerateStopPlay);
		controlSettingsPanel.add(regeneratePausePlay);
		controlSettingsPanel.add(compose);
		controlSettingsPanel.add(randomSeed);
		controlSettingsPanel.add(copySeed);
		controlSettingsPanel.add(ChordGUI.currentChords);
		controlSettingsPanel.add(copyChords);
		controlSettingsPanel.add(clearSeed);


		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(controlSettingsPanel, constraints);
	}

	private void copyChords() {
		ChordGUI.userChords.setupChords(ChordGUI.currentChordsInternal);
		LG.i(("Copied chords: " + ChordGUI.userChords.getChordListString()));
	}

	public void regenerateInPlace() {
		boolean wasSelected = ExtraSettingsGUI.startFromBar.isSelected();
		pauseInfoResettable = false;
		ExtraSettingsGUI.startFromBar.setSelected(false);
		pauseMidi();
		actionPerformed(
				new ActionEvent(regeneratePausePlay, ActionEvent.ACTION_PERFORMED, "Regenerate"));
		ExtraSettingsGUI.startFromBar.setSelected(wasSelected);
		pauseInfoResettable = true;
	}

	private void initPlayPanel(int startY, int anchorSide) {

		JPanel playSavePanel = new JPanel();
		playSavePanel.setOpaque(false);
		stopMidi = makeButton("STOP", e -> stopMidi());
		playMidi = makeButton("PLAY", e -> playMidi(false));
		pauseMidi = makeButton("PAUSE", e -> pauseMidi());
		stopMidi.setFont(stopMidi.getFont().deriveFont(Font.BOLD));
		playMidi.setFont(playMidi.getFont().deriveFont(Font.BOLD));
		pauseMidi.setFont(pauseMidi.getFont().deriveFont(Font.BOLD));

		JButton save3Star = makeButtonMoused("Save 3*", e -> {
			if (!SwingUtilities.isLeftMouseButton(e)) {
				openFolder(Constants.MIDIS_FOLDER + Constants.SAVED_MIDIS_FOLDER_BASE + "3star/");
			} else {
				saveGuiConfigFile(3);
			}
		});
		save3Star.setForeground(savedIndicatorForegroundColors[0]);
		JButton save4Star = makeButtonMoused("Save 4*", e -> {
			if (!SwingUtilities.isLeftMouseButton(e)) {
				openFolder(Constants.MIDIS_FOLDER + Constants.SAVED_MIDIS_FOLDER_BASE + "4star/");
			} else {
				saveGuiConfigFile(4);
			}
		});
		save4Star.setForeground(savedIndicatorForegroundColors[1]);
		JButton save5Star = makeButtonMoused("Save 5*", e -> {
			if (!SwingUtilities.isLeftMouseButton(e)) {
				openFolder(Constants.MIDIS_FOLDER + Constants.SAVED_MIDIS_FOLDER_BASE + "5star/");
			} else {
				saveGuiConfigFile(5);
			}
		});
		save5Star.setForeground(savedIndicatorForegroundColors[2]);
		JButton saveCustom = makeButtonMoused("Save ->", e -> {
			if (!SwingUtilities.isLeftMouseButton(e)) {
				openFolder(Constants.MIDIS_FOLDER + Constants.SAVED_MIDIS_FOLDER_BASE + "custom/");
			} else {
				saveGuiConfigFile(-1);
			}
		});
		saveCustom.setForeground(savedIndicatorForegroundColors[3]);
		Calendar nowDate = Calendar.getInstance();
		String yearMonth = nowDate.get(Calendar.YEAR) + "-"
				+ StringUtils.leftPad(String.valueOf(nowDate.get(Calendar.MONTH) + 1), 2, "0");
		saveCustomFilename = new JTextField(yearMonth + "/savefilename", 12);
		savedIndicatorLabel = new JLabel("[Saved!]");
		savedIndicatorLabel.setVisible(false);

		JButton loadConfig = makeButton("LOAD..", "LoadGUIConfig");

		JButton saveWavFile = makeButtonMoused("Export .WAV", e -> {
			if (!SwingUtilities.isLeftMouseButton(e)) {
				openFolder(Constants.EXPORT_FOLDER);
			} else {
				saveWavFile();
			}
		});


		scoreGUI.createShowScoreButton();

		regenerateWhenValuesChange = new CheckButton("Regenerate on Change", true);
		/*showScorePicker = new ScrollComboBox<String>();
		ScrollComboBox.addAll(
				new String[] { "NO Drums/Chords", "Drums Only", "Chords Only", "ALL" },
				showScorePicker);*/

		loopBeat = new CheckButton("Loop Quarter Notes", false);
		loopBeatCount = new DetachedKnobPanel("", 16, 1, 16);
		loopBeatCompose = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new String[] { "REGENERATE", "COMPOSE", "REPLAY" }, loopBeatCompose);

		midiMode = new CheckButton("MIDI Transmitter Mode", true);
		midiMode.setToolTipText("Select a MIDI port on the right and click Regenerate.");

		midiMode.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (device != null) {
					closeMidiDevice();
				} else {
					softCloseSynth();
				}
			}

		});

		midiModeDevices = new ScrollComboBox<String>(false);
		MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
		MidiDevice dev = null;
		for (int i = 0; i < infos.length; i++) {
			try {
				dev = MidiSystem.getMidiDevice(infos[i]);
				if (dev.getMaxReceivers() != 0 && dev.getMaxTransmitters() == 0) {
					midiModeDevices.addItem(infos[i].toString());
					/*if (infos[i].toString().startsWith("loopMIDI")) {
						midiModeDevices.setVal(infos[i].toString());
					}*/
					if (infos[i].toString().startsWith("Gervill")) {
						midiModeDevices.setVal(infos[i].toString());
					}
					LG.i(("Added device: " + infos[i].toString()));
				}
			} catch (MidiUnavailableException e) {
				// Auto-generated catch block
				LG.e(e);
			}
		}
		midiModeDevices.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (device != null) {
					closeMidiDevice();
				}
			}

		});

		generatedMidi = new JList<File>();
		generatedMidi.setCellRenderer(new MidiListCellRenderer());
		generatedMidi.setTransferHandler(new FileTransferHandler(e -> {
			return currentMidi;
		}));
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
		playSavePanel.add(saveWavFile);
		playSavePanel.add(new JLabel("Midi Drag'N'Drop:"));
		playSavePanel.add(generatedMidi);

		JPanel playSettingsPanel = new JPanel();
		playSettingsPanel.setOpaque(false);

		playSettingsPanel.add(regenerateWhenValuesChange);
		playSettingsPanel.add(ScoreGUI.showScore);
		//playSettingsPanel.add(showScorePicker);
		playSettingsPanel.add(loopBeat);
		playSettingsPanel.add(loopBeatCount);
		playSettingsPanel.add(new JLabel("On Loop:"));
		playSettingsPanel.add(loopBeatCompose);
		playSettingsPanel.add(midiMode);
		playSettingsPanel.add(midiModeDevices);


		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(playSettingsPanel, constraints);

		constraints.gridy = startY + 5;
		constraints.anchor = anchorSide;
		everythingPanel.add(playSavePanel, constraints);
	}

	private void saveWavFile() {
		if (currentMidi == null) {
			messageLabel.setText("Need to compose first!");
			messageLabel.repaint(0);
			return;
		}
		switchMidiButtons(false);
		stopMidi();
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
				defSynth = (synth != null && !midiMode.isSelected()) ? synth
						: MidiSystem.getSynthesizer();
				synth = defSynth;
				String soundbankOptional = (soundfont != null) ? "SB_" : "";
				String filename = f.format(date) + "_" + soundbankOptional
						+ getFilenameForSaving(currentMidi.getName());
				File exportFolderDir = new File(Constants.EXPORT_FOLDER);
				exportFolderDir.mkdir();

				saveWavFile(Constants.EXPORT_FOLDER + "/" + filename + "-export.wav", defSynth);
				synth = null;
				if (device != null) {
					device.close();
					device = null;
				}
				return null;
			}

			@Override
			protected void done() {
				try {
					Synthesizer synthesizer = null;
					if (!midiMode.isSelected()) {
						synthesizer = loadSynth();
					}
					prepareMidiPlayback(synthesizer);
				} catch (InvalidMidiDataException | MidiUnavailableException e) {
					LG.e(e);
				}
				switchMidiButtons(true);
				messageLabel.setText("PROCESSED WAV!");
				repaint();
			}
		};
		worker.execute(); //here the process thread initiates
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

	private void initHelperPopups(JPanel settingsPanel) {
		JPanel helperPopupsPanel = new JPanel();
		helperPopupsPanel.add(makeButton("User Manual (opens browser)", e -> openHelpPopup()));
		helperPopupsPanel.add(makeButton("Debug Console", e -> openDebugConsole()));
		helperPopupsPanel.add(makeButton("About VibeComposer", e -> openAboutPopup()));
		settingsPanel.add(helperPopupsPanel, BorderLayout.SOUTH);
	}

	private void startMidiCcThread() {
		if (cycle != null && cycle.isAlive()) {
			LG.i(("MidiCcThread already exists!"));
			return;
		}
		LG.i(("Starting new MidiCcThread..!"));
		cycle = new Thread() {

			public void run() {

				while (sequencer != null && sequencer.isRunning()) {
					sendAllMidiCc();

					try {
						sleep(25);
					} catch (InterruptedException e) {
						LG.e(e);
						return;
					}
				}
				LG.i(("ENDED MidiCcThread!"));
				cycle = null;
			}


		};
		cycle.start();
	}

	protected void sendAllMidiCc() {
		if (ExtraSettingsGUI.useMidiCC.isSelected()) {
			for (int j = 0; j < 4; j++) {
				List<? extends InstPanel> panels = getInstList(j);
				for (int i = 0; i < panels.size(); i++) {
					double vol = panels.get(i).getVolSlider().getValue() / 100.0;
					int channel = panels.get(i).getMidiChannel() - 1;
					sendVolumeMessage(vol, channel);
					sendReverbMessage(1.0, channel);
					sendChorusMessage(1.0, channel);
					sendLowPassFilterMessage(1.0, channel, j);
					sendPanMessage(panels.get(i).getPanSlider().getValue(), channel);
				}
			}
			double drumVol = DrumGUI.drumVolumeSlider.getValue() / 100.0;
			sendVolumeMessage(drumVol, 9);
			sendReverbMessage(0.5, 9);
			sendChorusMessage(0.1, 9);
			sendLowPassFilterMessage(1.0, 9, 4);
			//DrumGUI.drumPanels.forEach(e -> sendPanMessage(e.getPanSlider().getValue(), 9));
		}
	}

	protected void sendPanMessage(int pan100, int channel) {
		int value127 = ExtraSettingsGUI.useMidiCC.isSelected() ? OMNI.clampMidi(pan100 * 127 / 100) : 64;
		sendMidiCcMessage(value127, channel, 10);
	}

	protected void sendVolumeMessage(double volMultiplier, int channel) {
		int value127 = ExtraSettingsGUI.useMidiCC.isSelected()
				? OMNI.clampVel(volMultiplier * globalVolSlider.getValue() * 127 / 100.0)
				: 100;
		sendMidiCcMessage(value127, channel, 7);
	}

	protected void sendReverbMessage(double reverbMultiplier, int channel) {
		int value127 = ExtraSettingsGUI.useMidiCC.isSelected()
				? OMNI.clampVel(reverbMultiplier * globalReverbSlider.getValue())
				: 0;
		sendMidiCcMessage(value127, channel, 91);
	}

	protected void sendChorusMessage(double chorusMultiplier, int channel) {
		int value127 = ExtraSettingsGUI.useMidiCC.isSelected()
				? OMNI.clampVel(chorusMultiplier * globalChorusSlider.getValue())
				: 0;
		sendMidiCcMessage(value127, channel, 93);
	}

	protected void sendLowPassFilterMessage(double filterMultiplier, int channel, int part) {
		int value127 = ExtraSettingsGUI.useMidiCC.isSelected()
				? OMNI.clampVel(filterMultiplier * groupFilterSliders[part].getValue())
				: 127;
		sendMidiCcMessage(value127, channel, 74);
	}

	protected void sendMidiCcMessage(int value, int channel, int midiCc) {

		try {
			ShortMessage volumeMessage = new ShortMessage();
			volumeMessage.setMessage(ShortMessage.CONTROL_CHANGE, channel, midiCc, value);

			if (midiMode.isSelected() && device != null) {
				device.getReceivers().forEach(e -> e.send(volumeMessage, -1));
			} else if (synth != null && synth.isOpen()) {
				synth.getReceivers().forEach(e -> e.send(volumeMessage, -1));
			}
		} catch (InvalidMidiDataException e) {
			// Auto-generated catch block
			LG.e(e);
		}
	}

	private void switchAllOnComposeCheckboxes(boolean state) {
		MelodyGUI.generateMelodiesOnCompose.setSelected(state);
		ChordGUI.randomChordsGenerateOnCompose.setSelected(state);
		ArpGUI.randomArpsGenerateOnCompose.setSelected(state);
		DrumGUI.randomDrumsGenerateOnCompose.setSelected(state);
		randomizeBpmOnCompose.setSelected(state);
		randomizeTransposeOnCompose.setSelected(state);
		//randomizeChordStrumsOnCompose.setSelected(state);
		randomizeInstOnComposeOrGen.setSelected(state);
		ArpGUI.randomArpHitsPerPattern.setSelected(state);
		ArrangementGUI.randomizeArrangementOnCompose.setSelected(state);
		ArrangementGUI.arrangementResetCustomPanelsOnCompose.setSelected(state);
		randomizeScaleModeOnCompose.setSelected(state);
		MelodyGUI.melodyTargetNotesRandomizeOnCompose.setSelected(state);
		MelodyGUI.melodyPatternRandomizeOnCompose.setSelected(state);
		ExtraSettingsGUI.randomizeTimingsOnCompose.setSelected(state);
		ExtraSettingsGUI.sidechainPatternsOnCompose.setSelected(state);
		ChordGUI.copyChordsAfterGenerate.setSelected(state);
	}

	private void switchAllOnComposeCheckboxesForegrounds(Color fg) {
		MelodyGUI.generateMelodiesOnCompose.setForeground(fg);
		ChordGUI.randomChordsGenerateOnCompose.setForeground(fg);
		ArpGUI.randomArpsGenerateOnCompose.setForeground(fg);
		DrumGUI.randomDrumsGenerateOnCompose.setForeground(fg);
		randomizeBpmOnCompose.setForeground(fg);
		randomizeTransposeOnCompose.setForeground(fg);
		//randomizeChordStrumsOnCompose.setForeground(fg);
		randomizeInstOnComposeOrGen.setForeground(fg);
		ArrangementGUI.randomizeArrangementOnCompose.setForeground(fg);
		ArrangementGUI.arrangementResetCustomPanelsOnCompose.setForeground(fg);
		randomizeScaleModeOnCompose.setForeground(fg);
		MelodyGUI.melodyTargetNotesRandomizeOnCompose.setForeground(fg);
		MelodyGUI.melodyPatternRandomizeOnCompose.setForeground(fg);
		switchOnComposeRandom.setForeground(fg);
		ExtraSettingsGUI.randomizeTimingsOnCompose.setForeground(fg);
		ExtraSettingsGUI.sidechainPatternsOnCompose.setForeground(fg);
		ChordGUI.copyChordsAfterGenerate.setForeground(fg);
	}

	private void switchMidiButtons(boolean state) {
		playMidi.setEnabled(state);
		pauseMidi.setEnabled(state);
		stopMidi.setEnabled(state);
		compose.setEnabled(state);
		//regenerate.setEnabled(state);
		midiMode.setEnabled(state);
		midiModeDevices.setEnabled(state);

	}

	private void switchBigMonitorMode() {
		Dimension newPrefSize = null;
		isBigMonitorMode = !isBigMonitorMode;
		if (isBigMonitorMode) {
			newPrefSize = new Dimension(1900, 600);
			//ShowPanelBig.panelMaxHeight = 600;
		} else {
			newPrefSize = new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT + 35);
			//ShowPanelBig.panelMaxHeight = 400;
		}
		if (ScoreGUI.scorePanel != null) {
			ScoreGUI.scorePanel.updatePanelHeight(newPrefSize.height);
			ScoreGUI.scorePanel.update();
		}
		scrollPaneDimension = newPrefSize;
		instrumentTabPane.setPreferredSize(newPrefSize);
		instrumentTabPane.setSize(newPrefSize);

		for (DrumPanel dp : DrumGUI.drumPanels) {
			dp.getComboPanel().reapplyHits();
		}
		refreshVariationPopupButtons(ArrangementGUI.scrollableArrangementActualTable.getColumnCount());
		pack();
	}

	private void updateGlobalUI() {
		ColorUIResource r = null;
		if (!isDarkMode) {
			r = new ColorUIResource(new Color(153, 160, 166));
		} else {
			r = new ColorUIResource(new Color(68, 66, 67));
		}
		UIManager.put("Button.background", r);
		UIManager.put("Panel.background", r);
		UIManager.put("ComboBox.background", r);
		UIManager.put("ComboBox.buttonBackground",
				isDarkMode ? new Color(60, 58, 61) : new Color(165, 170, 176));
		UIManager.put("TextField.background", r);
		UIManager.put("Table.background", r);
		UIManager.put("TableHeader.background", r);
		UIManager.put("TabbedPane.background", r);
		UIManager.put("ScrollPane.background", r);
		UIManager.put("ScrollPane.border", r);
		UIManager.put("List.background", r);
		UIManager.put("ScrollBar.background", r);
		//UIManager.put("TiltedBorder.background", r);
		SwingUtilities.updateComponentTreeUI(this);
		SwingUtilities.updateComponentTreeUI(ExtraSettingsGUI.extraSettingsPanel);
		SwingUtils.popupMenus.forEach(e -> SwingUtilities.updateComponentTreeUI(e));
	}

	public static Color uiColor() {
		return (isDarkMode) ? darkModeUIColor : lightModeUIColor;
	}

	public static Color uiComposeTextColor() {
		return isDarkMode ? COMPOSE_COLOR_TEXT : COMPOSE_COLOR_TEXT_LIGHT;
	}

	public static Color uiRegenerateTextColor() {
		return isDarkMode ? REGENERATE_COLOR_TEXT : REGENERATE_COLOR_TEXT_LIGHT;
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
		//setVisible(false);
		ArrangementGUI.arrSection.setSelectedIndex(0);

		LG.i(("Switching dark mode!"));
		if (isDarkMode) {
			FlatIntelliJLaf.install();
		} else {
			FlatDarculaLaf.install();
		}
		//UIManager.put("TabbedPane.contentOpaque", false);

		isDarkMode = !isDarkMode;
		updateGlobalUI();

		toggledUIColor = uiColor();
		toggledComposeColor = uiComposeTextColor();
		toggledRegenerateColor = uiRegenerateTextColor();


		//mainTitle.setForeground((isDarkMode) ? new Color(0, 220, 220) : lightModeUIColor);
		//subTitle.setForeground(toggledUIColor);
		messageLabel.setForeground(toggledUIColor);
		ChordGUI.tipLabel.setForeground(toggledUIColor);
		currentTime.setForeground(toggledUIColor);
		totalTime.setForeground(toggledUIColor);
		compose.setForeground(toggledUIColor);
		compose.setBackground(COMPOSE_COLOR);
		regenerate.setForeground(toggledRegenerateColor);
		playMidi.setForeground(toggledUIColor);
		pauseMidi.setForeground(toggledUIColor);
		stopMidi.setForeground(toggledUIColor);
		loopBeatCompose.setForeground(toggledComposeColor);
		ArpGUI.randomArpHitsPerPattern.setForeground(toggledUIColor);
		MelodyGUI.randomMelodyOnRegenerate.setForeground(toggledRegenerateColor);
		switchAllOnComposeCheckboxesForegrounds(toggledComposeColor);

		panelColorHigh = UIManager.getColor("Panel.background");
		panelColorLow = UIManager.getColor("Panel.background");
		if (isDarkMode) {
			panelColorHigh = panelColorHigh.darker();
			panelColorLow = panelColorLow.brighter();
		} else {
			panelColorHigh = panelColorHigh.darker();
			//panelColorLow = panelColorLow.darker();
		}
		if (ArrangementGUI.GLOBAL.equals(ArrangementGUI.arrSection.getVal())) {
			ArrangementGUI.arrangementMiddleColoredPanel.setBackground(panelColorHigh.brighter());
		} else {
			ArrangementGUI.arrangementMiddleColoredPanel.setBackground(toggledUIColor.darker().darker());
		}

		sliderPanel.setBackground(panelColorLow);

		globalSoloMuter.reapplyTextColor();
		for (SoloMuter sm : groupSoloMuters) {
			sm.reapplyTextColor();
		}

		for (int i = 0; i < 5; i++) {
			getInstList(i).forEach(e -> e.getSoloMuter().reapplyTextColor());
			getAffectedPanels(i).forEach(e -> {
				if (e.getComboPanel() != null) {
					e.getComboPanel().reapplyHits();
				}
			});
			int fI = i;
			getAffectedPanels(i).forEach(e -> e.setBackground(OMNI.alphen(Constants.instColors[fI], 60)));
		}
		refreshVariationPopupButtons(ArrangementGUI.actualArrangement.getSections().size());

		//switchFullMode(isDarkMode);

		if (ScoreGUI.scorePanel != null) {
			ScoreGUI.scorePanel.update();
		}


		removeComboBoxArrows(everythingPanel);
		//sizeRespectingPack();
		//setVisible(true);
		repaint();
		ArrangementGUI.arrSectionPane.repaint();
		if (ScoreGUI.scorePanel != null) {

			ScoreGUI.scorePanel.setupMouseWheelListener();
		}
		initScrollPaneListeners();
	}

	private void switchFullMode() {
		isFullMode = !isFullMode;
		setFullMode(isFullMode);
	}

	private void setFullMode(boolean mode) {
		toggleableComponents.forEach(e -> e.setVisible(mode));
		for (int i = 0; i < 5; i++) {
			getInstList(i)
					.forEach(e -> e.getToggleableComponents().forEach(f -> f.setVisible(mode)));
		}


		/*instrumentTabPane
				.setPreferredSize(isFullMode ? scrollPaneDimension : scrollPaneDimensionToggled);*/
		/*if (isFullMode) {
			pack();
		}*/

	}

	private void toggleButtonEnabledForPanels() {
		toggleButtonEnabledForPanels(ArrangementGUI.GLOBAL.equals(ArrangementGUI.arrSection.getVal()));
	}

	private void toggleButtonEnabledForPanels(boolean isOriginal) {
		for (int i = 0; i < 5; i++) {
			addPanelButtons[i].setEnabled(isOriginal);
			randomPanelsToGenerate[i].setEnabled(isOriginal);
		}
	}

	private void softCloseSynth() {
		closeMidiDevice();
		synth = null;
	}

	private void closeMidiDevice() {
		stopMidi();
		if (sequencer != null) {
			sequencer.close();
			sequencer = null;
		}

		LG.i(("Closed sequencer!"));
		MidiDevice oldDevice = device;
		device = null;

		if (oldDevice != null) {
			oldDevice.close();
		}

		LG.i(("Closed oldDevice!"));
		oldDevice = null;
		needToRecalculateSoloMutersAfterSequenceGenerated = true;
	}

	public void regenerate() {
		regenerate(false);
	}

	public void regenerate(boolean manual) {
		composeMidi(true, manual);
	}

	public void compose() {
		compose(false);
	}

	public void compose(boolean manual) {
		composeMidi(false, manual);
	}

	public void composeMidi(boolean regenerate, boolean manual) {
		LG.i("==========Compose Midi [" + (regenerate ? "Regenerate" : "Compose") + "|"
				+ (manual ? "Manual" : "OnChange") + "]: Starting...================");
		heavyBackgroundTasksInProgress = true;
		boolean logPerformance = true;
		long systemTime = System.currentTimeMillis();

		try {
			if (sequencer != null) {
				sequencer.stop();
				flushMidiEvents();
				partAndOrderLastNoteIndexes.clear();
			}

			if (ArrangementGUI.manualArrangement.isSelected() && (ArrangementGUI.actualArrangement.getSections().isEmpty()
					|| !ArrangementGUI.actualArrangement.getSections().stream().anyMatch(e -> e.hasPresence()))) {
				LG.i(("Nothing to compose! Uncheck MANUAL arrangement!"));
				new TemporaryInfoPopup(("Nothing to compose! Uncheck MANUAL arrangement!"), 3000);
				heavyBackgroundTasksInProgress = false;
				return;
			}

			saveStartInfo();
			savedIndicatorLabel.setVisible(false);
			if (midiMode.isSelected()) {
				if (synth != null) {
					if (isSoundbankSynth && soundfont != null) {
						synth.unloadAllInstruments(soundfont);
					}
					synth.close();
					synth = null;
					System.gc();
				}
			} else {
				if (device != null) {
					if (synth != null) {
						synth.close();
						synth = null;
					}
					if (sequencer != null) {
						sequencer.close();
						sequencer = null;
						LG.i(("CLOSED SEQUENCER!"));
					}
					device.close();
					device = null;
					LG.i(("CLOSED DEVICE!"));
				}
			}

			needToRecalculateSoloMuters = true;

			Integer masterpieceSeed = prepareMainSeed(regenerate);

			int regenerateCount = (regenerate) ? guiConfig.getRegenerateCount() + 1 : 0;

			prepareUI(regenerate, manual);
			if (logPerformance) {
				LG.i("After prepareUI: " + (System.currentTimeMillis() - systemTime));
			}
			GUIConfig midiConfig = new GUIConfig();
			copyGUItoConfig(midiConfig, true);

			melodyGen = new MidiGenerator(midiConfig);
			fillUserParameters(regenerate, manual);

			File makeDir = new File(Constants.MIDIS_FOLDER);
			makeDir.mkdir();
			makeDir = new File(Constants.MIDI_HISTORY_FOLDER);
			makeDir.mkdir();

			String seedData = "" + masterpieceSeed;
			if (!MelodyGUI.melodyPanels.isEmpty() && MelodyGUI.melodyPanels.get(0).getPatternSeed() != 0
					&& !MelodyGUI.melodyPanels.get(0).getMuteInst()) {
				seedData += "_" + MelodyGUI.melodyPanels.get(0).getPatternSeed();
			}
			String keyTrans = MidiUtils.SEMITONE_LETTERS.get((ScoreGUI.transposeScore.getInt() + 120) % 12)
					.replaceAll("#", "s");

			String fileName = "bpm" + mainBpm.getInt() + "_" + keyTrans + "_" + scaleMode.getVal()
					+ "_seed" + seedData;
			String relPath = Constants.MIDI_HISTORY_FOLDER + "/" + fileName + ".mid";

			// unapply S/M, generate, reapply S/M with new track numbering
			unapplySolosMutes(true);

			if (logPerformance) {
				LG.i("After setup: " + (System.currentTimeMillis() - systemTime));
			}
			melodyGen.generateMasterpiece(masterpieceSeed, relPath);

			guiConfig = midiConfig;
			//LG.i("Adding to config history, reason: " + regenerate);
			//fixCombinedTracks();
			reapplySolosMutes();

			cleanUpUIAfterCompose(regenerate);

			if (logPerformance) {
				LG.i("After cleanup: " + (System.currentTimeMillis() - systemTime));
			}

			if (GenerationGUI.configHistoryStoreRegeneratedTracks.isSelected() || !regenerate
					|| configHistory.getItemCount() == 0) {
				midiConfig.setCustomChords(StringUtils.join(MidiGenerator.chordInts, ","));
				midiConfig.setRegenerateCount(regenerateCount);
				configHistory.addItem(midiConfig);
				configHistory.setSelectedIndex(configHistory.getItemCount() - 1);
				if (configHistory.getItemCount() > 10) {
					configHistory.removeItemAt(0);
				}
			} else if (regenerate) {
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
				out.println(new Date().toString() + ", Seed: " + seedData);
			} catch (IOException e) {
				LG.i(("Failed to write into Random Seed History.."));
			}

			handleGeneratedMidi(regenerate, relPath, systemTime);
			currentBeatMultiplier = beatDurationMultiplier.getSelectedItem();
			resetArrSectionInBackground();
			heavyBackgroundTasksInProgress = false;

		} catch (Exception e) {
			LG.e("Exception during midi generation! Cause: " + e.getMessage(), e);
			heavyBackgroundTasksInProgress = false;
			new TemporaryInfoPopup(Constants.BUG_HUNT_MESSAGE, null);
			if (sequencer != null && sequencer.isRunning()) {
				sequencer.stop();
			}
			loopBeat.setSelected(false);
			reapplySolosMutes();
			return;
		}
		LG.i("================== VibeComposerGUI::composeMidi time: "
				+ (System.currentTimeMillis() - systemTime) + " ms ==========================");
	}

	private void fixCombinedTracks() {
		if (DrumGUI.combineDrumTracks.isSelected()) {
			DrumGUI.drumPanels.forEach(e -> {
				if (e.getSequenceTrack() < 0) {
					e.getSoloMuter().unsolo();
					e.getSoloMuter().unmute();
				}
			});
		}
		if (MelodyGUI.combineMelodyTracks.isSelected()) {
			MelodyGUI.melodyPanels.forEach(e -> {
				if (e.getSequenceTrack() < 0) {
					e.getSoloMuter().unsolo();
					e.getSoloMuter().unmute();
				}
			});
		}
	}

	public void fillUserParameters(boolean regenerate, boolean manual) {
		try {
			MidiGenerator.COLLAPSE_DRUM_TRACKS = DrumGUI.combineDrumTracks.isSelected();
			MidiGenerator.recalculateDurations(ExtraSettingsGUI.stretchMidi.getInt());
			MidiGenerator.GLOBAL_DURATION_MULTIPLIER = ExtraSettingsGUI.globalNoteLengthMultiplier.getInt() / 1000.0;
			MelodyGenerator.RANDOMIZE_TARGET_NOTES = !regenerate
					&& MelodyGUI.melodyTargetNotesRandomizeOnCompose.isSelected();
			MelodyGenerator.TARGET_NOTES = (MelodyGUI.melody1ForcePatterns.isSelected()
					&& !MelodyGUI.melodyPanels.isEmpty()
					&& !MelodyGUI.melodyPanels.get(0).getNoteTargetsButton().isEnabled())
							? MelodyGUI.melodyPanels.stream()
									.collect(Collectors.toMap(MelodyPanel::getPanelOrder,
											MelodyPanel::getChordNoteChoices))
							: null;

			/*boolean addStartDelay = useArrangement.isSelected() || arrangementCustom.isSelected()
					|| DrumGUI.drumPanels.stream().anyMatch(e -> e.getOffset() < 0)
					|| (ChordGUI.randomChordDelay.isSelected()
							&& (ChordGUI.chordPanels.stream().anyMatch(e -> e.getOffset() < 0)));*/
			MidiGenerator.START_TIME_DELAY = MidiGenerator.Durations.QUARTER_NOTE;

			/*if (loopBeat.isSelected()) {
				//MidiGenerator.START_TIME_DELAY = MidiGenerator.DBL_ERR;
				MidiGenerator.START_TIME_DELAY = MidiGenerator.Durations.EIGHTH_NOTE;
			} else {
				MidiGenerator.START_TIME_DELAY = MidiGenerator.Durations.EIGHTH_NOTE;
			}*/

			MidiGenerator.FIRST_CHORD = chordSelect(ChordGUI.firstChordSelection.getVal());
			MidiGenerator.LAST_CHORD = chordSelect(ChordGUI.lastChordSelection.getVal());

			// solve user chords
			boolean customChords = ChordGUI.userChordsEnabled.isSelected()
					&& ChordGUI.userChords.getChordletsRaw().size() > 0;
			if (customChords || ChordGUI.userDurationsEnabled.isSelected()) {
				List<String> chords = ChordGUI.userChords.getChordList();
				List<Double> durations = getUserChordDurations();

				MidiGenerator.userChordsDurations = durations;

				if (customChords) {
					MidiGenerator.userChords = chords;
				} else {
					MidiGenerator.userChords.clear();
				}
			} else {
				MidiGenerator.userChords.clear();
				MidiGenerator.userChordsDurations.clear();
			}

			if (MelodyMidiDropPane.userMelody != null && MelodyGUI.useUserMelody.isSelected()) {
				MelodyGenerator.userMelody = MelodyMidiDropPane.userMelody;
			} else {
				MelodyGenerator.userMelody = null;
			}

			// to include it in the XML when saving, but not when generating
			/*if (!addInst[0].isSelected()) {
				MidiGenerator.gc.setMelodyParts(new ArrayList<>());
			}
			if (!addInst[1].isSelected()) {
				MidiGenerator.gc.setBassParts(new ArrayList<>());
			}
			if (!addInst[2].isSelected()) {
				MidiGenerator.gc.setChordParts(new ArrayList<>());
			}
			if (!addInst[3].isSelected()) {
				MidiGenerator.gc.setArpParts(new ArrayList<>());
			}
			if (!addInst[4].isSelected()) {
				MidiGenerator.gc.setDrumParts(new ArrayList<>());
			}*/

		} catch (Exception e) {
			LG.i(("User screwed up his inputs!"));
			LG.e(e);
		}

	}

	public static List<Double> getUserChordDurations() {
		boolean forceDefault = !ChordGUI.userDurationsEnabled.isSelected();

		List<Double> durations = new ArrayList<>();
		String[] durationSplit = ChordGUI.userChordsDurations.getText().split(",");
		boolean customChords = ChordGUI.userChordsEnabled.isSelected()
				&& ChordGUI.userChords.getChordletsRaw().size() > 0;
		boolean coversAllCustomChords = durationSplit.length >= ChordGUI.userChords.chordCount();

		try {
			for (int i = 0; i < (customChords && coversAllCustomChords ? ChordGUI.userChords.chordCount()
					: durationSplit.length); i++) {
				durations.add((durationSplit != null && !forceDefault && coversAllCustomChords)
						? (ExtraSettingsGUI.stretchMidi.getInt() * Double.valueOf(durationSplit[i]) / 100.0)
						: MidiGenerator.Durations.WHOLE_NOTE);
			}
		} catch (Exception e) {
			new TemporaryInfoPopup("Invalid durations!", 3000);
		}

		return durations;
	}

	private Integer prepareMainSeed(boolean regenerate) {
		int masterpieceSeed = 0;

		int parsedSeed = randomSeed.getValue();

		if (regenerate) {
			masterpieceSeed = lastRandomSeed;
			if (parsedSeed != 0) {
				masterpieceSeed = parsedSeed;
			}
		}

		if (masterpieceSeed != 0) {
			LG.i(("Skipping, regenerated seed: " + masterpieceSeed));
		} else if (parsedSeed != 0) {
			masterpieceSeed = parsedSeed;
		} else {
			Random seedGenerator = new Random();
			int randomVal = seedGenerator.nextInt();
			masterpieceSeed = randomVal;
		}

		LG.i(("Master seed: " + masterpieceSeed));
		lastRandomSeed = masterpieceSeed;
		return masterpieceSeed;
	}

	private void prepareUI(boolean regenerate, boolean manual) {

		if (!regenerate && randomizeBpmOnCompose.isSelected()) {
			randomizeBPM();
		}

		// MELODY
		if (!regenerate && MelodyGUI.generateMelodiesOnCompose.isSelected()) {
			int seed = getCurrentSeed();
			melodyGUI.createRandomMelodyPanels(seed != 0 ? seed : new Random().nextInt(), MelodyGUI.melodyPanels.size(),
					false, null);
		}

		if (!regenerate && ExtraSettingsGUI.randomizeTimingsOnCompose.isSelected()) {
			if (globalSwingOverride.isSelected()) {
				globalSwingOverrideValue
						.setInt(50 + new Random().nextInt(DrumGUI.randomDrumMaxSwingAdjust.getInt() * 2 + 1)
								- DrumGUI.randomDrumMaxSwingAdjust.getInt());
			}
			double randomBeatMultiplier = new Random().nextDouble();
			if (randomBeatMultiplier < 0.85) {
				beatDurationMultiplier.setSelectedIndex(1);
			} else if (randomBeatMultiplier < 0.95) {
				beatDurationMultiplier.setSelectedIndex(0);
			} else {
				beatDurationMultiplier.setSelectedIndex(2);
			}
		}

		if (!regenerate && ExtraSettingsGUI.sidechainPatternsOnCompose.isSelected()) {
			sidechainPatterns(false, false);
		}


		if (regenerate && manual && MelodyGUI.randomMelodyOnRegenerate.isSelected()) {
			melodyGUI.randomizeMelodySeeds();
		}

		if (regenerate && MelodyGUI.randomMelodyOnRegenerate.isSelected() && !MelodyGUI.melodyPanels.isEmpty()) {
			if (MelodyGUI.melodyPatternRandomizeOnCompose.isSelected()) {
				MelodyGUI.melodyPanels.forEach(e -> {
					if (e.getLockInst() == true) {
						return;
					}
					e.setMelodyPatternOffsets(
							MelodyUtils.getRandomMelodyPattern(e.getAlternatingRhythmChance(),
									e.getPanelOrder() + (e.getPatternSeed() == 0 ? lastRandomSeed
											: e.getPatternSeed())));
				});
			}
			if (MelodyGUI.melodyTargetNotesRandomizeOnCompose.isSelected()) {
				MelodyGUI.melodyPanels.forEach(e -> {
					if (e.getLockInst() == true) {
						return;
					}
					e.setChordNoteChoices(e.getNoteTargetsButton().getRandGenerator().apply(e
							.getPanelOrder()
							+ (e.getPatternSeed() == 0 ? lastRandomSeed : e.getPatternSeed())));
				});
			}
		}

		if (!regenerate && MelodyGUI.melodyPatternRandomizeOnCompose.isSelected()
				&& !MelodyGUI.melodyPanels.isEmpty()) {
			if (MelodyGUI.melody1ForcePatterns.isSelected()) {
				MelodyPanel firstMp = MelodyGUI.melodyPanels.get(0);
				List<Integer> pat = MelodyUtils.getRandomMelodyPattern(
						firstMp.getAlternatingRhythmChance(),
						firstMp.getPanelOrder() + (firstMp.getPatternSeed() == 0 ? lastRandomSeed
								: firstMp.getPatternSeed()));
				firstMp.setMelodyPatternOffsets(pat);
			} else {
				MelodyGUI.melodyPanels.forEach(e -> {
					if (e.getLockInst() == true) {
						return;
					}
					e.setMelodyPatternOffsets(
							MelodyUtils.getRandomMelodyPattern(e.getAlternatingRhythmChance(),
									e.getPanelOrder() + (e.getPatternSeed() == 0 ? lastRandomSeed
											: e.getPatternSeed())));
				});
			}
		}


		if (MelodyGUI.melody1ForcePatterns.isSelected() && !MelodyGUI.melodyPanels.isEmpty()) {
			MelodyPanel mp1 = MelodyGUI.melodyPanels.get(0);
			for (int i = 1; i < MelodyGUI.melodyPanels.size(); i++) {
				MelodyGUI.melodyPanels.get(i).overridePatterns(mp1);
			}
		}


		// BASS

		// ARPS
		if (instrumentTabPane.getSelectedIndex() != 3 && ArpGUI.arpCopyMelodyInst.isSelected()
				&& !MelodyGUI.melodyPanels.isEmpty() && !MelodyGUI.melodyPanels.get(0).getMuteInst()) {
			if (ArpGUI.arpPanels.size() > 0 && !ArpGUI.arpPanels.get(0).getLockInst()) {
				ArpGUI.arpPanels.get(0).getInstrumentBox().initInstPool(POOL.MELODY);
				ArpGUI.arpPanels.get(0).setInstPool(POOL.MELODY);
				ArpGUI.arpPanels.get(0).setInstrument(MelodyGUI.melodyPanels.get(0).getInstrument());
			}
		}

		if (!regenerate && ArrangementGUI.arrangementResetCustomPanelsOnCompose.isSelected()) {
			ArrangementGUI.actualArrangement.getSections().forEach(e -> e.resetCustomizedParts());
		} else {
			// check each section number of customized panels to see if it matches current counts
			for (int i = 0; i < ArrangementGUI.actualArrangement.getSections().size(); i++) {
				Section sec = ArrangementGUI.actualArrangement.getSections().get(i);
				for (int j = 0; j < 5; j++) {
					List<?> partList = sec.getInstPartList(j);
					if (partList != null && partList.size() > getInstList(j).size()) {
						sec.resetCustomizedParts(j);
					}
				}
			}
		}

		if (!regenerate && randomizeScaleModeOnCompose.isSelected()) {
			Integer[] allowedScales = new Integer[] { 0, 1, 3, 4, 5, 8 };
			scaleMode.setSelectedIndex(allowedScales[new Random().nextInt(allowedScales.length)]);
		}

		if (!regenerate && ArrangementGUI.randomizeArrangementOnCompose.isSelected()) {
			ArrangementGUI.arrangementGUI.handleArrangementAction("ArrangementRandomize", lastRandomSeed,
					Integer.valueOf(ArrangementGUI.pieceLength.getText()));
		}

		if ((regenerate || !ArrangementGUI.randomizeArrangementOnCompose.isSelected()) && (currentMidi != null)
				&& ArrangementGUI.manualArrangement.isSelected()) {
			ArrangementGUI.arrangement.setOverridden(true);
		} else {
			ArrangementGUI.arrangement.setOverridden(false);
		}

		if (currentMidiEditorPopup != null && currentMidiEditorPopup.isVisible()) {
			LG.i("MidiEditPopup is open - saving!");
			currentMidiEditorPopup.saveNotes(false);
		}

	}

	private void cleanUpUIAfterCompose(boolean regenerate) {


		List<String> prettyChords = MidiGenerator.chordInts;
		ChordGUI.currentChords.setText(
				StringUtils.abbreviate("Chords:[" + StringUtils.join(prettyChords, ",") + "]", 60));
		ChordGUI.currentChordsInternal.clear();
		ChordGUI.currentChordsInternal.addAll(prettyChords);

		if (MelodyMidiDropPane.userMelody != null) {
			String chords = StringUtils.join(MidiGenerator.chordInts, ",");
			ChordGUI.userChords.setupChords(MidiGenerator.chordInts);
			setChordProgressionLength(MidiGenerator.chordInts.size());
			guiConfig.setCustomChords(chords);
		} else if (!ChordGUI.userChordsEnabled.isSelected() && ChordGUI.copyChordsAfterGenerate.isSelected()) {
			ChordGUI.userChords.setupChords(MidiGenerator.chordInts);
		}

		if (!regenerate && MelodyGUI.melodyTargetNotesRandomizeOnCompose.isSelected()
				&& MelodyGenerator.TARGET_NOTES != null) {
			for (int i = 0; i < MelodyGUI.melodyPanels.size(); i++) {
				int mpOrder = MelodyGUI.melodyPanels.get(i).getPanelOrder();
				List<Integer> notes = MelodyGenerator.TARGET_NOTES.get(mpOrder);
				if (notes != null) {
					MelodyGUI.melodyPanels.get(i).setChordNoteChoices(notes);
					guiConfig.getMelodyParts().get(i).setChordNoteChoices(notes);
				}
			}
		}

		for (int i = 0; i < ArpGUI.arpPanels.size(); i++) {
			ArpPart ap = MidiGenerator.gc.getArpParts().get(i);
			if (ap.getArpPattern() == ArpPattern.RANDOM) {
				ArpGUI.arpPanels.get(i).setArpPatternCustom(ap.getArpPatternCustom());
			}
		}

		//fixCombinedMelodyTracks();

		ArrangementGUI.actualArrangement = new Arrangement();
		ArrangementGUI.actualArrangement.setPreviewChorus(false);
		ArrangementGUI.actualArrangement.getSections().clear();
		for (Section sec : MidiGenerator.gc.getActualArrangement().getSections()) {
			ArrangementGUI.actualArrangement.getSections().add(sec.deepCopy());
		}
		guiConfig.setActualArrangement(ArrangementGUI.actualArrangement);
		ScoreGUI.pianoRoll();
		/*if (ScoreGUI.showScore.isSelected()) {
			instrumentTabPane.setSelectedIndex(7);
		}*/


		if (currentMidiEditorPopup != null && currentMidiEditorPopup.isVisible()) {
			if (ArrangementGUI.actualArrangement.getSections().size() <= currentMidiEditorSectionIndex) {
				currentMidiEditorPopup.close();
				currentMidiEditorPopup = null;
			} else {
				currentMidiEditorPopup
						.setup(ArrangementGUI.actualArrangement.getSections().get(currentMidiEditorSectionIndex));
			}
		} else {
			LG.d("No midi editor is open!");
		}
	}

	private void resetArrSectionInBackground() {
		SwingUtilities.invokeLater(() -> {
			int arrSectionIndex = ArrangementGUI.arrSection.getSelectedIndex();
			ArrangementGUI.arrangementGUI.setActualModel(ArrangementGUI.actualArrangement.convertToActualTableModel());
			if (arrSectionIndex != 0 && arrSectionIndex < ArrangementGUI.arrSection.getItemCount()) {
				ArrangementGUI.arrSection.setSelectedIndex(arrSectionIndex);
			} else {
				ArrangementGUI.arrSection.setSelectedIndex(0);
			}
			refreshVariationPopupButtons(ArrangementGUI.scrollableArrangementActualTable.getColumnCount());
			ArrangementGUI.arrSection.getButtons().forEach(e -> e.repaint());
			ArrangementGUI.arrSection.repaint();
		});

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
			if (!midiMode.isSelected()) {
				synthesizer = loadSynth();
			}


			if (sequencer == null) {
				sequencer = MidiSystem.getSequencer(synthesizer == null);  // Get the default Sequencer
				if (sequencer == null) {
					LG.e("Sequencer device not supported");
					return;
				}
				sequencer.open(); // Open device
			}

			if (logPerformance) {
				LG.i("After sequencer setup: " + (System.currentTimeMillis() - systemTime));
			}


			// Create sequence, the File must contain MIDI file data.
			currentMidi = new File(relPath);
			if (currentMidi == null) {
				new TemporaryInfoPopup("Error: Could not load currently generated MIDI file!",
						3000);
				return;
			}
			currentSequenceMidi = new File(Constants.TEMPORARY_SEQUENCE_MIDI_NAME);
			generatedMidi.setListData(new File[] { currentMidi });
			//sizeRespectingPack();
			repaint();
			if (!prepareMidiPlayback(synthesizer)) {
				return;
			}

			if (logPerformance) {
				LG.i("After prepare midi playback: " + (System.currentTimeMillis() - systemTime));
			}

			resetSequencerTickPosition();

			totalTime.setText(microsecondsToTimeString(sequencer.getMicrosecondLength()));
			slider.setMaximum((int) (sequencer.getMicrosecondLength() / 1000));
			slider.setPaintTicks(true);
			int measureWidth = sliderMeasureWidth();
			int delayed = delayed();
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
					table.put(Integer.valueOf(current), new JLabel(sectionText));
					realIndex++;
				}
				current += ((sec != null) && sec.getSectionDuration() > 0)
						? measureWidth * (sec.getSectionDuration() / fullMeasureNoteDuration)
						: measureWidth;
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
			//sliderBeatStartTimes.add(slider.getMaximum());

			slider.setCustomMajorTicks(sliderMeasureStartTimes);
			slider.setCustomMinorTicks(sliderBeatStartTimes);
			/*LG.i(("Size measures: " + sliderMeasureStartTimes.size()));
			LG.i(("Size beats: " + sliderBeatStartTimes.size()));*/
			//LG.i(("What beats: " + sliderBeatStartTimes.toString()));

			slider.setSnapToTicks(ExtraSettingsGUI.snapStartToBeat.isSelected());
			// fix misalignment from different BPMs
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
			// Force the slider to use the new labels
			slider.setLabelTable(table);
			slider.setPaintLabels(true);

			if (loopBeat.isSelected()) {
				long startPos = (ExtraSettingsGUI.startFromBar.isSelected()) ? delayed : pausedSliderPosition;
				if (startPos < slider.getValue()) {
					startPos = slider.getValue();
					midiNavigate(startPos, 0);
				} else {
					midiNavigate(startPos);
				}
				resetPauseInfo();

			} else {
				String pauseBehavior = ExtraSettingsGUI.pauseBehaviorCombobox.getVal();
				if (!"NEVER".equalsIgnoreCase(pauseBehavior)) {
					boolean unpause = regenerate || pauseBehavior.contains("compose");
					unpause &= (pausedSliderPosition > 0
							&& pausedSliderPosition < slider.getMaximum() - 100);

					if (unpause) {
						long startPos = (ExtraSettingsGUI.startFromBar.isSelected())
								? sliderMeasureStartTimes.get(pausedMeasureCounter)
								: pausedSliderPosition;
						if (startPos < slider.getValue()) {
							startPos = slider.getValue();
						}
						midiNavigate(startPos);
					} else {
						resetPauseInfo();
						int startPos = delayed / 2;
						if (startPos < slider.getValue()) {
							startPos = slider.getValue();
						}
						midiNavigate(startPos);
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
				sequencer.start();  // start the playback
			}

			double divisor = 1;
			if (beatDurationMultiplier.getSelectedIndex() == 0) {
				divisor = 0.5;
			} else if (beatDurationMultiplier.getSelectedIndex() == 2) {
				divisor = 2;
			}
			loopBeatCount.getKnob()
					.setMax(!MidiGenerator.userChordsDurations.isEmpty()
							? (int) Math.ceil(
									OMNI.sumListDouble(MidiGenerator.userChordsDurations) / divisor)
							: MidiGenerator.chordInts.size() * 4);
			startMidiCcThread();
			recalculateTabPaneCounts();
			sequencer.setTempoFactor(1);
			if (needToRecalculateSoloMutersAfterSequenceGenerated) {
				needToRecalculateSoloMuters = true;
				needToRecalculateSoloMutersAfterSequenceGenerated = false;
			}
			startBpm = mainBpm.getInt();
		} catch (MidiUnavailableException | InvalidMidiDataException ex) {
			LG.e(ex);
		}
	}

	private static void adjustSavedPositions() {
		int currentBpm = mainBpm.getInt();
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

	private boolean prepareMidiPlayback(Synthesizer synthesizer)
			throws InvalidMidiDataException, MidiUnavailableException {
		Sequence sequence = null;
		try {
			sequence = MidiSystem.getSequence(currentSequenceMidi);
		} catch (Exception e) {
			new TemporaryInfoPopup(
					"Cannot create MIDI - VibeComposer is in a folder without write access!\n This can happen in restricted folders, e.g. Program Files.",
					null);
			return false;
		}
		sequencer.setSequence(sequence); // load it into sequencer

		if (midiMode.isSelected()) {
			if (device == null) {
				for (Transmitter tm : sequencer.getTransmitters()) {
					tm.close();
				}
				MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
				for (int i = 0; i < infos.length; i++) {
					if (infos[i].toString().equalsIgnoreCase(midiModeDevices.getVal())) {
						device = MidiSystem.getMidiDevice(infos[i]);
						LG.d(infos[i].toString() + "| max recv: " + device.getMaxReceivers()
								+ ", max trm: " + device.getMaxTransmitters());
						if (device.getMaxReceivers() != 0) {
							LG.d("Found max receivers != 0, opening midi receiver device: "
									+ infos[i].toString());
							device.open();
							break;
						}

					}
				}
				sequencer.getTransmitter().setReceiver(device.getReceiver());
			}


		} else {
			if (synthesizer != null) {
				// open soundbank synth
				for (Transmitter tm : sequencer.getTransmitters()) {
					tm.close();
				}

				sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
				synth = synthesizer;
				isSoundbankSynth = true;

			} else if (synth != null) {
				// do nothing, all set
			} else {
				LG.i("Using Default system Synthesizer!");
				// use default system synth
				for (Transmitter tm : sequencer.getTransmitters()) {
					tm.close();
				}
				synth = MidiSystem.getSynthesizer();
				synth.open();
				sequencer.getTransmitter().setReceiver(synth.getReceiver());
				isSoundbankSynth = false;


			}
		}
		return true;
	}

	private void resetSequencerTickPosition() {

		if (slider.getValue() < slider.getMaximum()) {
			midiNavigate(slider.getValue());
		} else {
			slider.setValue(0);
			midiNavigate(0);
		}


	}

	private void setChordProgressionLength(int size) {
		switch (size) {
		case 4:
			ChordGUI.chordProgressionLength.setVal("4");
			break;
		case 8:
			ChordGUI.chordProgressionLength.setVal("8");
			break;
		default:
			ChordGUI.chordProgressionLength.setVal("RANDOM");
			break;
		}

	}

	private int getMaxChordProgressionLength() {
		switch (ChordGUI.chordProgressionLength.getSelectedIndex()) {
		case 0:
			return 4;
		case 1:
			return 8;
		default:
			return 16;
		}
	}

	@Deprecated
	private void __randomizeMelodySeeds() {
		melodyGUI.randomizeMelodySeeds();
	}

	private void unapplySolosMutes(boolean onlyIncluded) {
		if (!sequenceReady()) {
			return;
		}
		/*
				int countReducer = 0;
				if (DrumGUI.combineDrumTracks.isSelected()) {
					countReducer = (int) ((onlyIncluded)
							? DrumGUI.drumPanels.stream().filter(e -> !e.getMuteInst()).count()
							: DrumGUI.drumPanels.size());
					countReducer = Math.max(countReducer - 1, 0);
				}
				if (MelodyGUI.combineMelodyTracks.isSelected()) {
					countReducer += 2;
				}
				int baseCount = (onlyIncluded) ? countAllIncludedPanels() : countAllPanels();
				if (ExtraSettingsGUI.padGeneratedMidi.isSelected()) {
					baseCount += calculatePaddedPartsCount(onlyIncluded);
				}
		*/
		sequencer.setTrackSolo(0, false);
		sequencer.setTrackMute(0, false);

		Set<Integer> tracksToUnsolo = new HashSet<>();
		Set<Integer> tracksToUnmute = new HashSet<>();

		for (int i = 1; i < sequencer.getSequence().getTracks().length; i++) {
			tracksToUnsolo.add(i);
			tracksToUnmute.add(i);
		}

		Optional<DrumPanel> notExcludedDrum = DrumGUI.drumPanels.stream()
				.filter(e -> e.getSequenceTrack() >= 0).findFirst();
		Integer notExcludedCombinedDrumTrack = null;
		for (int i = 0; i < 5; i++) {
			List<? extends InstPanel> panels = getInstList(i);

			if (!isEnabled(i)) {
				continue;
			}

			if (i == 4 && notExcludedCombinedDrumTrack != null) {
				// combined midi tracks -> unsolo drums
				continue;
			}
			for (int j = 0; j < panels.size(); j++) {
				Integer seqTrack = panels.get(j).getSequenceTrack();
				if (seqTrack < 0 || panels.get(j).getMuteInst()) {
					continue;
				}
				if (panels.get(j).getSoloMuter().soloState == State.FULL) {
					tracksToUnsolo.remove(seqTrack);
				} else if (panels.get(j).getSoloMuter().muteState == State.FULL) {
					tracksToUnmute.remove(seqTrack);
				}
			}
		}
		tracksToUnsolo.forEach(e -> {
			sequencer.setTrackSolo(e, false);
			//LG.i("Unsoloed: " + e);
		});
		tracksToUnmute.forEach(e -> {
			sequencer.setTrackMute(e, false);
			//LG.i("Unmuted: " + e);
		});
	}

	private int calculatePaddedPartsCount(boolean onlyIncluded) {
		int count = 0;
		List<Integer> paddedValues = ExtraSettingsGUI.padGeneratedMidiValues.getValues();
		for (int i = 0; i < 5; i++) {
			if (isEnabled(i)) {
				List<? extends InstPanel> panels = getInstList(i);
				long partCount = panels.stream().filter(e -> !e.getMuteInst()).count();
				if (paddedValues.get(i) > partCount) {
					count += (paddedValues.get(i) - partCount);
				}
			}
		}
		return count;
	}

	private void reapplySolosMutes() {
		if (!sequenceReady()) {
			return;
		}
		// set by soloState/muteState
		for (int i = 0; i < 5; i++) {
			List<? extends InstPanel> panels = getInstList(i);
			for (int j = 0; j < panels.size(); j++) {
				InstPanel ip = panels.get(j);
				if (ip.getSequenceTrack() < 0) {
					ip.getSoloMuter().unsolo();
					ip.getSoloMuter().unmute();
				} else {
					sequencer.setTrackSolo(ip.getSequenceTrack(),
							ip.getSoloMuter().soloState == State.FULL);
					sequencer.setTrackMute(ip.getSequenceTrack(),
							ip.getSoloMuter().muteState == State.FULL);
				}
			}
		}

	}

	private void toggleExclude() {
		if (globalSoloMuter.soloState != State.OFF) {
			for (int i = 0; i < 5; i++) {
				List<? extends InstPanel> panels = getInstList(i);
				panels.forEach(e -> {
					if (e.getSoloMuter().soloState == State.OFF) {
						e.setMuteInst(true);
					} else {
						e.getSoloMuter().unsolo();
						e.setMuteInst(false);
					}
				});
			}
		} else {
			for (int i = 0; i < 5; i++) {
				List<? extends InstPanel> panels = getInstList(i);
				panels.forEach(e -> e.setMuteInst(false));
			}
		}
	}

	private Synthesizer loadSynth() {
		Synthesizer synthesizer = null;
		try {
			File soundbankFile = new File((String) ExtraSettingsGUI.soundbankFilename.getEditor().getItem());
			if (soundbankFile.isFile()) {
				if (synth == null || !isSoundbankSynth || needSoundbankRefresh) {
					if (synth != null && isSoundbankSynth && soundfont != null) {
						synth.unloadAllInstruments(soundfont);
						synth.close();
						synth = null;
						System.gc();
					}
					synth = null;

					soundfont = MidiSystem.getSoundbank(
							new BufferedInputStream(new FileInputStream(soundbankFile)));
					synthesizer = MidiSystem.getSynthesizer();

					synthesizer.isSoundbankSupported(soundfont);
					synthesizer.open();
					synthesizer.loadAllInstruments(soundfont);
					needSoundbankRefresh = false;
				}
				LG.i(("Playing using soundbank: "
						+ (String) ExtraSettingsGUI.soundbankFilename.getEditor().getItem()));
			} else {
				if (synth != null && isSoundbankSynth && soundfont != null) {
					synth.unloadAllInstruments(soundfont);
					synth.close();
					synth = null;
					System.gc();
				}
				synthesizer = null;
				synth = null;
				soundfont = null;
				LG.i(("NO SOUNDBANK WITH THAT NAME FOUND!"));
			}


		} catch (InvalidMidiDataException | IOException | MidiUnavailableException ex) {
			if (synth != null && isSoundbankSynth && soundfont != null) {
				synth.unloadAllInstruments(soundfont);
				synth.close();
				synth = null;
				System.gc();
			}
			synthesizer = null;
			synth = null;
			soundfont = null;
			LG.e(ex);
			LG.i(("NO SOUNDBANK WITH THAT NAME FOUND!"));
		}
		synth = synthesizer;
		return synthesizer;
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

	public static JButton makeButton(String name, Consumer<? super Object> a) {
		return SwingUtils.makeButton(name, a);
	}

	public static JButton makeButton(String name, Consumer<? super Object> a, int width) {
		return makeButton(name, a, width, 30);
	}

	public static JButton makeButton(String name, Consumer<? super Object> a, int width,
			int height) {
		JButton butt = new JButton(name);
		butt.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				a.accept(new Object());
			}

		});
		butt.setPreferredSize(new Dimension(width, height));
		butt.setMargin(new Insets(0, 0, 0, 0));
		return butt;
	}

	public static JButton makeButtonMoused(String name, Consumer<? super MouseEvent> a) {
		JButton butt = new JButton(name);
		butt.addMouseListener(new MouseAdapter() {

			@Override
			public void mousePressed(MouseEvent e) {
				a.accept(e);
			}

		});
		return butt;
	}

	private void randomizeUserChords() {
		copyGUItoConfig(guiConfig);
		MidiGenerator mg = new MidiGenerator(guiConfig);
		MidiGenerator.FIRST_CHORD = chordSelect(ChordGUI.firstChordSelection.getVal());
		MidiGenerator.LAST_CHORD = chordSelect(ChordGUI.lastChordSelection.getVal());
		MidiGenerator.userChords.clear();
		mg.generatePrettyUserChords(new Random().nextInt(),
				ChordGUI.userChords.chordCount() > 0 ? ChordGUI.userChords.chordCount()
						: MidiGenerator.gc.getFixedDuration(),
				4 * MidiGenerator.Durations.WHOLE_NOTE);
		List<String> prettyChords = MidiGenerator.chordInts;
		ChordGUI.userChords.setupChords(prettyChords);
	}

	private void openHelpPopup() {
		new HelpPopup();
	}

	private void openAboutPopup() {
		new AboutPopup();
	}

	private void openDrumViewPopup() {
		new DrumLoopPopup();
	}

	private void openExtraSettingsPopup() {
		new ExtraSettingsPopup();
	}

	private void openApplyCustomSectionPopup() {
		if (ArrangementGUI.arrSection.getSelectedIndex() > 0) {
			new ApplyCustomSectionPopup();
		}
	}

	private void openDebugConsole() {
		try {
			dconsole = new DebugConsole();
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
				stopMidi();
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

	public void actionPerformedTask(ActionEvent ae) {
		boolean tabPanePossibleChange = false;
		boolean soloMuterPossibleChange = false;
		boolean triggerRegenerate = false;

		LG.i(("<<<<<<<<<<<<<<<<Processing '" + ae.getActionCommand() + "'>>>>>>>>>>>>>>>>>>"));
		long actionSystemTime = System.currentTimeMillis();

		boolean isCompose = "Compose".equals(ae.getActionCommand());
		boolean isRegenerate = "Regenerate".equals(ae.getActionCommand());
		if (heavyBackgroundTasksInProgress) {
			LG.i("Cannot process action '" + ae.getActionCommand() + "', composing in progress!");
			new TemporaryInfoPopup("Composing in progress..", 1000);
			return;
		}

		InstComboBox.BANNED_INSTS.clear();
		InstComboBox.BANNED_INSTS.addAll(Arrays.asList(ExtraSettingsGUI.bannedInsts.getText().split(",")));

		/*{
			int inst = MelodyGUI.melodyPanels.get(0).getInstrument();
			MelodyGUI.melodyPanels.get(0).getInstrumentBox().initInstPool(MelodyGUI.melodyPanels.get(0).getInstPool());
			MelodyGUI.melodyPanels.get(0).getInstrumentBox().setInstrument(inst);
			inst = bassPanel.getInstrument();
			bassPanel.getInstrumentBox().initInstPool(bassPanel.getInstPool());
			bassPanel.getInstrumentBox().setInstrument(inst);
		}*/


		if (ae.getActionCommand() == "InitAllInsts") {
			if (ExtraSettingsGUI.useAllInsts.isSelected()) {
				InstUtils.initAllInsts();
			} else {
				InstUtils.initNormalInsts();
			}
			for (int i = 0; i < MelodyGUI.melodyPanels.size(); i++) {
				int inst = MelodyGUI.melodyPanels.get(i).getInstrumentBox().getInstrument();
				MelodyGUI.melodyPanels.get(i).getInstrumentBox().initInstPool(InstUtils.POOL.MELODY);
				MelodyGUI.melodyPanels.get(i).getInstrumentBox().setInstrument(inst);
			}
			for (int i = 0; i < BassGUI.bassPanels.size(); i++) {
				int inst = BassGUI.bassPanels.get(i).getInstrumentBox().getInstrument();
				BassGUI.bassPanels.get(i).getInstrumentBox().initInstPool(InstUtils.POOL.BASS);
				BassGUI.bassPanels.get(i).getInstrumentBox().setInstrument(inst);
			}
		}

		if (ae.getActionCommand() == "RandStrums"
				|| (isCompose & randomizeChordStrumsOnCompose.isSelected())) {
			for (InstPanel p : getAffectedPanels(2)) {
				ChordPanel cp = (ChordPanel) p;
				Pair<StrumType, Integer> strumPair = getRandomStrumPair();
				cp.setStrum(strumPair.getRight());
				cp.setStrumType(strumPair.getLeft());
				if (cp.getStretchEnabled() && cp.getChordNotesStretch() > 4
						&& cp.getStrum() > 999) {
					cp.setStrum(cp.getStrum() / 2);
				}
			}
			if (!isCompose) {
				triggerRegenerate = true;
			}
		}

		if (ae.getActionCommand() == "RandomizeInst") {
			randomizeInsts();
			triggerRegenerate = true;
		}
		if (isCompose && randomizeInstOnComposeOrGen.isSelected()) {
			randomizeInsts();
		}

		if (isCompose || isRegenerate) {
			soloMuterPossibleChange = true;
		}

		if (isCompose && addInst[2].isSelected() && ChordGUI.randomChordsGenerateOnCompose.isSelected()) {
			generatePanels(2);
		}
		if (isCompose && addInst[3].isSelected() && ArpGUI.randomArpsGenerateOnCompose.isSelected()) {
			generatePanels(3);
		}

		if (isCompose && addInst[4].isSelected() && DrumGUI.randomDrumsGenerateOnCompose.isSelected()) {
			generatePanels(4);
		}

		if (ae.getActionCommand() == "RandomizeTranspose") {
			Random instGen = new Random();
			ScoreGUI.transposeScore.setInt(instGen.nextInt(12) - 6);
			triggerRegenerate = true;
		}

		if (isCompose && randomizeTransposeOnCompose.isSelected()) {
			Random instGen = new Random();
			ScoreGUI.transposeScore.setInt(instGen.nextInt(12) - 6);
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

		if (ae.getActionCommand() == "UncheckComposeRandom") {
			switchAllOnComposeCheckboxes(false);
			switchOnComposeRandom.setText("  Tick all 'on Compose'   ");
			switchOnComposeRandom.setActionCommand("CheckComposeRandom");
		}

		if (ae.getActionCommand() == "CheckComposeRandom") {
			switchAllOnComposeCheckboxes(true);
			switchOnComposeRandom.setText("Untick all 'on Compose'");
			switchOnComposeRandom.setActionCommand("UncheckComposeRandom");
		}

		if (ae.getActionCommand() == "CopySeed") {
			/*Toolkit toolkit = Toolkit.getDefaultToolkit();
			Clipboard clipboard = toolkit.getSystemClipboard();
			StringSelection strSel = new StringSelection(str);
			clipboard.setContents(strSel, null);*/
			randomSeed.setValue(lastRandomSeed);
			LG.i(("Copied to random seed: " + lastRandomSeed));
		}

		if (ae.getActionCommand() == "LoadGUIConfig") {
			FileDialog fd = new FileDialog(this, "Choose a file", FileDialog.LOAD);
			fd.setDirectory(null);
			fd.setFile("*.xml");
			fd.setVisible(true);
			String filename = fd.getFile();
			File[] files = fd.getFiles();
			if (filename == null)
				LG.i(("You cancelled the choice"));
			else {
				LG.i(("You chose " + filename));
				try {
					stopMidi();
					guiConfig =

							unmarshallConfig(files[0]);
					copyConfigToGUI(guiConfig);
					vibeComposerGUI.repaint();
				} catch (JAXBException | IOException e) {
					LG.e("Can't load config: " + filename, e);
				}
			}
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
		}

		if (ae.getActionCommand() == "ClearChordSeeds")	{
			for (InstPanel cp : getAffectedPanels(2)) {
				cp.setPatternSeed(0);
			}
		}

		if (ae.getActionCommand() == "ClearArpSeeds") {
			for (InstPanel ap : getAffectedPanels(3)) {
				ap.setPatternSeed(0);
			}
		}

		if (ae.getActionCommand() == "ClearDrumSeeds") {
			for (InstPanel dp : getAffectedPanels(4)) {
				dp.setPatternSeed(0);
			}
		}

		if (ae.getActionCommand().startsWith("Arrangement")) {
			Random arrGen = new Random();
			ArrangementGUI.arrangementGUI.handleArrangementAction(ae.getActionCommand(), arrGen.nextInt(),
					Integer.valueOf(ArrangementGUI.pieceLength.getText()));
			tabPanePossibleChange = true;
		}

		if (ae.getActionCommand() == "RandomizePart") {

			JButton source = (JButton) ae.getSource();
			InstPanel sourcePanel = (InstPanel) source.getParent();
			randomizePanel(sourcePanel);
			triggerRegenerate = true;
		}
		// recalcs
		if (tabPanePossibleChange) {
			recalculateTabPaneCounts();
			recalculateGenerationCounts();
		}
		if (soloMuterPossibleChange) {
			recalculateSoloMuters();
		}

		if (triggerRegenerate && canRegenerateOnChange()) {
			regenerate();
		}

		LG.i("Finished '" + ae.getActionCommand() + "' in: "
				+ (System.currentTimeMillis() - actionSystemTime) + " ms");
		messageLabel.setText("::" + ae.getActionCommand() + "::");
	}

	private void randomizeBPM() {
		Random instGen = new Random();

		int bpm = instGen.nextInt(1 + ExtraSettingsGUI.bpmHigh.getInt() - ExtraSettingsGUI.bpmLow.getInt()) + ExtraSettingsGUI.bpmLow.getInt();
		if (ArpGUI.arpAffectsBpm.isSelected() && !ArpGUI.arpPanels.isEmpty()) {
			double highestArpPattern = ArpGUI.arpPanels.stream().map(
					e -> (e.getPatternRepeat() * e.getHitsPerPattern()) / (e.getChordSpan() * 8.0))
					.max((e1, e2) -> Double.compare(e1, e2)).get();
			LG.i(("Repeater value: " + highestArpPattern));
			if (highestArpPattern > 1) {
				bpm *= 1 / (0.5 + highestArpPattern * 0.5);
			}
		}
		mainBpm.setInt(bpm);
		mainBpm.getKnob().setMin(ExtraSettingsGUI.bpmLow.getInt());
		mainBpm.getKnob().setMax(ExtraSettingsGUI.bpmHigh.getInt());
	}

	private void enthickenText(Component comp) {
		if (comp != null) {
			comp.setFont(comp.getFont().deriveFont(Font.BOLD));
		}
	}

	public void recalculateSoloMuters() {
		for (int i = 0; i < 5; i++) {
			recalcGroupSolo(i);
			recalcGroupMute(i);
		}
		recalcGlobals();
		needToRecalculateSoloMutersAfterSequenceGenerated = true;
	}

	private void randomizeInsts() {
		Random instGen = new Random();


		for (ChordPanel cp : ChordGUI.chordPanels) {
			if (!cp.getLockInst()) {

				InstUtils.POOL pool = (instGen.nextInt(100) < Integer
						.valueOf(ChordGUI.randomChordSustainChance.getInt())) ? InstUtils.POOL.CHORD
								: InstUtils.POOL.PLUCK;

				cp.setInstPool(pool);
				pool = cp.getInstPool();
				cp.getInstrumentBox().initInstPool(pool);

				cp.setInstrument(cp.getInstrumentBox().getRandomInstrument());
			}
		}
		for (ArpPanel ap : ArpGUI.arpPanels) {
			if (!ap.getLockInst()) {
				ap.getInstrumentBox().setInstrument(ap.getInstrumentBox().getRandomInstrument());
			}
		}
		if (!MelodyGUI.melodyPanels.isEmpty()) {

			if (!MelodyGUI.combineMelodyTracks.isSelected()) {
				for (MelodyPanel mp : MelodyGUI.melodyPanels) {
					if (!mp.getLockInst()) {
						mp.getInstrumentBox()
								.setInstrument(mp.getInstrumentBox().getRandomInstrument());
					}
				}
			} else {
				int inst = MelodyGUI.melodyPanels.get(0).getInstrumentBox().getRandomInstrument();
				for (MelodyPanel mp : MelodyGUI.melodyPanels) {
					if (!mp.getLockInst()) {
						mp.getInstrumentBox().setInstrument(inst);
					}
				}
			}

		}

		for (BassPanel bp : BassGUI.bassPanels) {
			if (!bp.getLockInst()) {
				bp.getInstrumentBox().setInstrument(bp.getInstrumentBox().getRandomInstrument());
			}
		}

	}

	private void clearAllSeeds() {
		randomSeed.setValue(0);
		for (int i = 0; i < 5; i++) {
			getInstList(i).forEach(e -> e.setPatternSeed(0));
		}
		ArrangementGUI.arrangementSeed.setValue(0);
	}

	private void saveGuiConfigFile(int rating) {
		if (currentMidi != null) {
			String newFileName = getFilenameForSaving(currentMidi.getName());
			LG.i(("Saving file: " + (rating >= 0 ? newFileName : saveCustomFilename.getText())));

			Date date = new Date();
			String saveDirectory = Constants.SAVED_MIDIS_FOLDER_BASE;
			String name = "";

			SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();
			f.applyPattern("yyMMdd-HH-mm-ss");
			String additionalInfo = "";

			if (rating >= 0) {
				saveDirectory += rating + "star/";

				File makeSavedDir = new File(Constants.MIDIS_FOLDER + saveDirectory);
				makeSavedDir.mkdir();
				name = newFileName;
				name = name.substring(0, name.length() - 4);

				additionalInfo = f.format(date);
			} else {
				saveDirectory += "custom/";
				name = saveCustomFilename.getText();
				if (ExtraSettingsGUI.customFilenameAddTimestamp.isSelected()) {
					additionalInfo = f.format(date);
				}
			}

			String finalFilePath = Constants.MIDIS_FOLDER + saveDirectory + additionalInfo
					+ (additionalInfo.isEmpty() ? "" : "_") + name + Constants.MID_EXTENSION;
			LG.i("Saving to final path: " + finalFilePath);
			File savedMidi = new File(finalFilePath);
			try {
				FileUtils.copyFile(currentMidi, savedMidi);
				copyGUItoConfig(guiConfig);
				marshalConfig(guiConfig, finalFilePath, Constants.MID_EXTENSION.length());
				if (rating >= 3) {
					savedIndicatorLabel.setForeground(savedIndicatorForegroundColors[rating - 3]);
				} else {
					savedIndicatorLabel.setForeground(savedIndicatorForegroundColors[3]);
				}

				savedIndicatorLabel.setVisible(true);
			} catch (IOException | JAXBException e) {
				// Auto-generated catch block
				LG.e("Error saving file: ", e);
			}
		} else {
			LG.i(("currentMidi is NULL!"));
			LG.w("Cannot save config file without a successful compose/regenerate first!");
			new TemporaryInfoPopup("Cannot save config file without a successful compose/regenerate first!", 1500);
		}
	}

	private void saveGuiPresetFileByFilePath(String filePath) {
		try {
			GUIPreset preset = copyCurrentViewToPreset();
			marshalPreset(preset, filePath);
		} catch (IOException | JAXBException e) {
			// Auto-generated catch block
			LG.e(e);
		}
	}

	public GUIPreset copyCurrentViewToPreset() {
		GUIPreset preset = new GUIPreset();
		copyGUItoConfig(preset);
		preset.setPatternMaps(guiConfig.getPatternMaps());
		List<Component> presetComps = makeSettableComponentList();
		List<Integer> presetCompValues = new ArrayList<>();
		for (int i = 0; i < presetComps.size(); i++) {
			presetCompValues.add(getComponentValue(presetComps.get(i)));
		}
		preset.setOrderedValuesUI(presetCompValues);
		preset.setDarkMode(isDarkMode);
		preset.setFullMode(isFullMode);
		preset.setBigMode(isBigMonitorMode);
		return preset;
	}

	private void playMidi(boolean replay) {
		LG.i(("Starting Midi.."));
		if (sequencer != null) {
			if (sequencer.isRunning()) {
				if (!replay) {
					sequencer.stop();
				}

				long startPos = (ExtraSettingsGUI.startFromBar.isSelected())
						? sliderMeasureStartTimes.get(pausedMeasureCounter)
						: pausedSliderPosition;
				if (startPos < slider.getValue()) {
					startPos = slider.getValue();
				}
				midiNavigate(startPos);
			} else {
				if (!replay) {
					sequencer.stop();
				}
				savePauseInfo();
				if (pausedSliderPosition > 0 && pausedSliderPosition < slider.getMaximum() - 100) {
					LG.d(("Unpausing.."));
					midiNavigate(pausedSliderPosition);
				} else {
					LG.d(("Resetting.."));
					resetSequencerTickPosition();
				}
			}

			LG.d(("Position set.."));
			if (!replay) {
				try {
					Thread.sleep(25);
				} catch (InterruptedException e) {
					// Auto-generated catch block
					LG.e(e);
				}
			} else {
				sequencer.setLoopCount(1);
			}

			sequencer.start();
			startMidiCcThread();
			sequencer.setLoopCount(0);
			LG.i("Started Midi: " + pausedSliderPosition + "/" + slider.getMaximum() + ", measure: "
					+ pausedMeasureCounter);
		} else {
			LG.i(("Sequencer is NULL!"));
		}
	}

	/*private void replayMidi() {
		LG.i(("Replaying Midi.."));
		if (sequencer != null) {
			if (sequencer.isRunning()) {
				sequencer.stop();
			}
		} else {
			LG.i(("Sequencer is NULL!"));
		}
	}*/

	private void stopMidi() {
		LG.i(("Stopping Midi.."));
		if (sequencer != null) {
			sequencer.stop();
			flushMidiEvents();
			//resetSequencerTickPosition();
			slider.setUpperValue(slider.getValue());
			resetPauseInfo();
			LG.i(("Stopped Midi!"));
			/*if (ScoreGUI.scorePopup != null) {
				LG.i(ShowPanelBig.rulerScrollPane.getPreferredSize());
				LG.i(ShowPanelBig.rulerScrollPane.getWidth());
				LG.i(ShowPanelBig.areaScrollPane.getWidth());
				LG.i(ShowPanelBig.horizontalPane.getWidth());
				LG.i(ScoreGUI.scoreScrollPane.getWidth());
				LG.i(ScoreGUI.scorePopup.getFrame().getWidth());
			}*/

		} else {
			LG.i(("Sequencer is NULL!"));
		}
	}

	private void pauseMidi() {
		LG.i(("Pausing Midi.."));
		if (sequencer != null) {
			sequencer.stop();
			flushMidiEvents();
			savePauseInfo();
			LG.i("Paused Midi: " + pausedSliderPosition + ", measure: " + pausedMeasureCounter);
		} else {
			LG.i(("Sequencer is NULL!"));
		}
	}

	public static void savePauseInfo() {
		pausedSliderPosition = slider.getUpperValue();
		pausedBpm = mainBpm.getInt();
		if ((currentMidi != null) && (MidiGenerator.chordInts.size() > 0)) {
			for (int i = 1; i < sliderMeasureStartTimes.size(); i++) {
				if (sliderMeasureStartTimes.get(i) >= pausedSliderPosition + 50) {
					pausedMeasureCounter = i - 1;
					return;
				}
			}
			pausedMeasureCounter = 0;
		} else {
			pausedMeasureCounter = 0;
		}
	}

	public static void saveStartInfo() {
		startSliderPosition = slider.getValue();
		if ((currentMidi != null) && (MidiGenerator.chordInts.size() > 0)) {
			for (int i = 1; i < sliderBeatStartTimes.size(); i++) {
				if (sliderBeatStartTimes.get(i) >= startSliderPosition + 50) {
					startBeatCounter = i - 1;
					return;
				}
			}
			startBeatCounter = 0;
		} else {
			startBeatCounter = 0;
		}
	}

	private void resetPauseInfo() {
		if (pauseInfoResettable) {
			pausedSliderPosition = 0;
			pausedMeasureCounter = 0;
		}
	}

	public static void unsoloAllTracks(boolean resetButtons) {

		if (resetButtons) {
			for (SoloMuter sm : groupSoloMuters) {
				unsoloGroup(sm, resetButtons);

			}
		}


	}

	public static void toggleSoloGroup(SoloMuter groupSm) {
		if (groupSm.soloState != State.OFF) {
			unsoloGroup(groupSm, true);
		} else {
			soloGroup(groupSm);
		}
	}

	public static void unsoloGroup(SoloMuter groupSm, boolean resetButtons) {
		groupSm.unsolo();
		List<? extends InstPanel> groupList = getInstList(groupSm.inst);
		for (InstPanel ip : groupList) {
			ip.getSoloMuter().unsolo();
		}
		if (!VibeComposerGUI.sequenceReady()) {
			return;
		}
		for (InstPanel ip : groupList) {
			sequencer.setTrackSolo(ip.getSequenceTrack(), false);
		}
	}

	public static void soloGroup(SoloMuter groupSm) {
		groupSm.solo();
		List<? extends InstPanel> groupList = getInstList(groupSm.inst);
		for (InstPanel ip : groupList) {
			ip.getSoloMuter().solo();
		}
		if (groupSoloMuters.stream().filter(e -> e.soloState == State.FULL).count() == 5) {
			groupSm.smParent.solo();
		} else {
			groupSm.smParent.halfSolo();
		}
		if (!sequenceReady())
			return;
		for (InstPanel ip : groupList) {
			sequencer.setTrackSolo(ip.getSequenceTrack(), true);
		}
	}

	public static void unmuteAllTracks(boolean resetButtons) {

		if (resetButtons) {
			for (SoloMuter sm : groupSoloMuters) {
				unmuteGroup(sm, resetButtons);

			}
		}

	}

	public static void toggleMuteGroup(SoloMuter groupSm) {
		if (groupSm.muteState != State.OFF) {
			unmuteGroup(groupSm, true);
		} else {
			muteGroup(groupSm);
		}
	}

	public static void unmuteGroup(SoloMuter groupSm, boolean resetButtons) {

		groupSm.unmute();
		List<? extends InstPanel> groupList = getInstList(groupSm.inst);
		for (InstPanel ip : groupList) {
			ip.getSoloMuter().unmute();
		}
		if (!sequenceReady())
			return;
		for (InstPanel ip : groupList) {
			sequencer.setTrackMute(ip.getSequenceTrack(), false);
		}
	}

	public static void muteGroup(SoloMuter groupSm) {

		groupSm.mute();
		List<? extends InstPanel> groupList = getInstList(groupSm.inst);
		for (InstPanel ip : groupList) {
			ip.getSoloMuter().mute();
		}
		if (groupSoloMuters.stream().filter(e -> e.muteState == State.FULL).count() == 5) {
			groupSm.smParent.mute();
		} else {
			groupSm.smParent.halfMute();
		}
		if (!sequenceReady())
			return;
		for (InstPanel ip : groupList) {
			sequencer.setTrackMute(ip.getSequenceTrack(), true);
		}
	}

	public static boolean sequenceReady() {
		return (sequencer != null) && (sequencer.isOpen()) && (sequencer.getSequence() != null);
	}

	public void recalculateGeneratorAndTabCounts() {
		recalculateGenerationCounts();
		recalculateTabPaneCounts();
	}

	public void recalculateGenerationCounts() {
		for (int i = 0; i < 5; i++) {
			randomPanelsToGenerate[i].setText("" + Math.max(1, getInstList(i).size()));
		}
	}

	public void recalculateTabPaneCounts() {
		if (instrumentTabPane.getComponentCount() < 7) {
			return;
		}
		instrumentTabPane.setTitleAt(0, "Melody (" + MelodyGUI.melodyPanels.size() + ")");
		instrumentTabPane.setTitleAt(1, " Bass  (" + BassGUI.bassPanels.size() + ")");
		instrumentTabPane.setTitleAt(2, "Chords (" + ChordGUI.chordPanels.size() + ")");
		instrumentTabPane.setTitleAt(3, " Arps  (" + ArpGUI.arpPanels.size() + ")");
		instrumentTabPane.setTitleAt(4, " Drums (" + DrumGUI.drumPanels.size() + ")");
		instrumentTabPane.setTitleAt(5, "Arrangement (" + ArrangementGUI.arrangement.getSections().size() + ")");
		instrumentTabPane.setTitleAt(6,
				"Generated Arrangement (" + ArrangementGUI.actualArrangement.getSections().size() + ")");
		if (instrumentTabPane.getComponentCount() >= 8) {
			instrumentTabPane.setTitleAt(7, " Score ");
		}
	}

	public static Pair<List<String>, List<Double>> solveUserChords(String[] userChordsSplit,
			String[] userChordsDurationsSplit) {
		LG.i(("Solving custom chords.."));
		List<String> solvedChords = new ArrayList<>();
		List<Double> solvedDurations = new ArrayList<>();

		try {

			if (userChordsSplit.length == userChordsDurationsSplit.length) {

				List<String> userChordsParsed = new ArrayList<>();
				List<Double> userChordsDurationsParsed = new ArrayList<>();
				for (int i = 0; i < userChordsDurationsSplit.length; i++) {
					int[] mappedChordAttempt = MidiUtils.mappedChord(userChordsSplit[i]);
					if (mappedChordAttempt != null) {
						userChordsParsed.add(userChordsSplit[i]);
					}

					userChordsDurationsParsed.add(Double.valueOf(userChordsDurationsSplit[i])
							* ExtraSettingsGUI.stretchMidi.getInt() / 100.0);
				}
				if (userChordsParsed.size() == userChordsDurationsParsed.size()) {
					solvedChords = userChordsParsed;
					solvedDurations = userChordsDurationsParsed;
				} else {
					LG.i("Lengths don't match, solved only these: " + userChordsParsed.toString()
							+ " !");
				}
			}
		} catch (Exception e) {
			LG.i(("Bad user input in custom chords/durations!\n"));
			LG.e(e);
		}
		if (!solvedChords.isEmpty() && !solvedDurations.isEmpty()) {
			LG.i((solvedChords.toString()));
			LG.i((solvedDurations.toString()));
			return Pair.of(solvedChords, solvedDurations);
		} else {
			return null;
		}
	}

	/*public static Pair<List<String>, List<Double>> solveUserChords(JTextField customChords,
			JTextField customChordsDurations) {

		String text = customChords.getText().replaceAll(" ", "");
		customChords.setText(text);
		String[] userChordsSplit = text.split(",");
		//LG.i((StringUtils.join(userChordsSplit, ";")));

		String[] userChordsDurationsSplit = customChordsDurations.getText().split(",");
		if (userChordsSplit.length != userChordsDurationsSplit.length) {
			List<Integer> durations = IntStream.iterate(4, n -> n).limit(userChordsSplit.length)
					.boxed().collect(Collectors.toList());
			customChordsDurations.setText(StringUtils.join(durations, ","));
			userChordsDurationsSplit = customChordsDurations.getText().split(",");
		}
		return solveUserChords(userChordsSplit, userChordsDurationsSplit);
	}*/

	public static Pair<List<String>, List<Double>> solveUserChords(String customChords,
			String customChordsDurations) {

		String text = customChords.replaceAll(" ", "");
		String[] userChordsSplit = text.split(",");
		//LG.i((StringUtils.join(userChordsSplit, ";")));

		String[] userChordsDurationsSplit = customChordsDurations.split(",");
		if (userChordsSplit.length != userChordsDurationsSplit.length) {
			List<Integer> durations = IntStream.iterate(4, n -> n).limit(userChordsSplit.length)
					.boxed().collect(Collectors.toList());
			userChordsDurationsSplit = StringUtils.join(durations, ",").split(",");
		}
		return solveUserChords(userChordsSplit, userChordsDurationsSplit);
	}

	private ChordGenSettings getChordSettingsFromUI() {
		ChordGenSettings chordSettings = new ChordGenSettings();

		chordSettings.setIncludePresets(ChordGUI.randomChordPattern.isSelected());
		chordSettings.setUseDelay(ChordGUI.randomChordDelay.isSelected());
		chordSettings.setUseStrum(ChordGUI.randomChordStrum.isSelected());
		chordSettings.setUseSplit(ChordGUI.randomChordSplit.isSelected());
		chordSettings.setUseTranspose(ChordGUI.randomChordTranspose.isSelected());
		chordSettings.setShiftChance(ChordGUI.randomChordShiftChance.getInt());
		chordSettings.setSustainChance(ChordGUI.randomChordSustainChance.getInt());
		chordSettings.setFlattenVoicingChance(ChordGUI.randomChordVoicingChance.getInt());
		return chordSettings;
	}

	private void setChordSettingsInUI(ChordGenSettings settings) {
		ChordGUI.randomChordPattern.setSelected(settings.isIncludePresets());
		ChordGUI.randomChordDelay.setSelected(settings.isUseDelay());
		ChordGUI.randomChordStrum.setSelected(settings.isUseStrum());
		ChordGUI.randomChordSplit.setSelected(settings.isUseSplit());
		ChordGUI.randomChordTranspose.setSelected(settings.isUseTranspose());
		ChordGUI.randomChordShiftChance.setInt(settings.getShiftChance());
		ChordGUI.randomChordSustainChance.setInt(settings.getSustainChance());
		ChordGUI.randomChordVoicingChance.setInt(settings.getFlattenVoicingChance());
	}

	public String chordSelect(String s) {
		if (!MidiUtils.MAJOR_CHORDS.contains(s)) {
			return null;
		} else {
			return s;
		}
	}

	@SuppressWarnings("restriction")
	protected void saveWavFile(final String wavFileName, Synthesizer normalSynth)
			throws MidiUnavailableException, IOException {
		AudioSynthesizer synth = null;
		AudioInputStream stream1 = null;
		AudioInputStream stream2 = null;
		try {
			synth = (AudioSynthesizer) normalSynth;
			synth.close();

			// Open AudioStream from AudioSynthesizer with default values
			stream1 = synth.openStream(null, null);
			synth.open();
			boolean midiModeSel = midiMode.isSelected();
			if (midiModeSel) {
				midiMode.setSelectedRaw(false);
			} else {
				if (soundfont != null) {
					synth.unloadAllInstruments(soundfont);
					synth.loadAllInstruments(soundfont);
				}
			}


			// Play Sequence into AudioSynthesizer Receiver.
			double totalLength = sendOutputSequenceMidiEvents(synth.getReceiver());
			if (midiModeSel) {
				midiMode.setSelectedRaw(midiModeSel);
			}
			// give it an extra 2 seconds, to the reverb to fade out--otherwise it sounds unnatural
			totalLength += 2;
			// Calculate how long the WAVE file needs to be.
			long len = (long) (stream1.getFormat().getFrameRate() * totalLength);
			stream2 = new AudioInputStream(stream1, stream1.getFormat(), len);


			// Write the wave file to disk
			AudioSystem.write(stream2, AudioFileFormat.Type.WAVE, new File(wavFileName));
		} catch (Exception e) {
			LG.e("TERRIBLE WAV ERROR!", e);
		} finally {
			if (stream1 != null)
				stream1.close();
			if (stream2 != null)
				stream2.close();
			if (synth != null)
				synth.close();
		}
	}

	private double sendOutputSequenceMidiEvents(Receiver receiver) {
		Sequence sequence = sequencer.getSequence();
		// this method is only designed to handle the PPQ division type.
		assert sequence.getDivisionType() == Sequence.PPQ : sequence.getDivisionType();

		int microsecondsPerQtrNote = (int) (500000 * 120 / guiConfig.getBpm());
		int seqRes = sequence.getResolution();
		long totalTime = 0;
		sendAllMidiCc();
		for (Track track : sequence.getTracks()) {
			long lastTick = 0;
			long curTime = 0;

			for (int i = 0; i < track.size(); i++) {
				MidiEvent event = track.get(i);
				long tick = event.getTick();
				curTime += ((tick - lastTick) * microsecondsPerQtrNote) / seqRes;
				lastTick = tick;
				MidiMessage msg = event.getMessage();
				if (!(msg instanceof MetaMessage)) {
					receiver.send(msg, curTime);
				}
			}

			// make the total time be the time of the langest track
			totalTime = Math.max(curTime, totalTime);
		}

		return totalTime / 1000000.0;
	}

	/*@SuppressWarnings("restriction")
	private static AudioSynthesizer getAudioSynthesizer() throws MidiUnavailableException {
		// First check if default synthesizer is AudioSynthesizer.
		Synthesizer synth = MidiSystem.getSynthesizer();
		if (synth instanceof AudioSynthesizer)
			return (AudioSynthesizer) synth;

		// now check the others...
		for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
			MidiDevice device = MidiSystem.getMidiDevice(info);
			if (device instanceof AudioSynthesizer)
				return (AudioSynthesizer) device;
		}

		throw new MidiUnavailableException("The AudioSynthesizer is not available.");
	}*/

	public static Class<?> getWrapperClass(int partNum) {
		return ArpPartsWrapper.class;
	}

	public static int marshalParts(String path, int partNum, boolean selectiveSave) throws JAXBException {
		SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();
		f.applyPattern("yyMMdd-hh-mm-ss");
		Class<? extends InstPartsWrapper> wrapperClass = InstPartsWrapper.getWrapperClass(partNum);
		JAXBContext context = JAXBContext.newInstance(wrapperClass, InstPartsWrapper.class);
		Marshaller mar = context.createMarshaller();
		mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		InstPartsWrapper<?> wrapper = InstPartsWrapper.forClass(wrapperClass);
		List<? extends InstPanel> affectedPanels = getAffectedPanels(partNum);
		if (selectiveSave && affectedPanels.stream().anyMatch(e -> e.getLockInst())) {
			affectedPanels = affectedPanels.stream().filter(e -> e.getLockInst()).collect(Collectors.toList());
		}
		List<? extends InstPart> parts = getInstPartsFromInstPanels(affectedPanels, false);
		wrapper.setParts(parts);
		mar.marshal(wrapper, new File(path));
		LG.i("File saved: " + path);
		return parts.size();
	}

	public void unmarshallParts(File f, int partNum, boolean clearPreviousPanels) throws JAXBException, IOException {
		JAXBContext context = JAXBContext.newInstance(InstPartsWrapper.getWrapperClass(partNum), InstPartsWrapper.class);
		InstPartsWrapper<?> wrapper = (InstPartsWrapper<?>) context.createUnmarshaller()
				.unmarshal(new FileReader(f));
		List<InstPart> parts = (List<InstPart>) wrapper.getParts();
		boolean isCustomSection = isCustomSection();

		if (!clearPreviousPanels && isCustomSection) {
			new TemporaryInfoPopup("Cannot change # of instruments in custom sections!", 1500);
			return;
		}

		List<InstPanel> currentPanels = getAffectedPanels(partNum);
		List<InstPart> lockedParts = currentPanels.stream().filter(e -> e.getLockInst())
				.map(e -> e.toInstPart(e.getPatternSeed())).collect(Collectors.toList());
		Map<Integer, List<InstPart>> originalLockedPartsOrder = lockedParts.stream().collect(Collectors.groupingBy(e -> e.getOrder()-1));
		List<InstPart> nonLockedParts = currentPanels.stream().filter(e -> !e.getLockInst())
				.map(e -> e.toInstPart(e.getPatternSeed())).collect(Collectors.toList());

		int numParts = parts.size();
		int numPartsToReplace = nonLockedParts.size();

		if (clearPreviousPanels) {
			if (isCustomSection && numPartsToReplace != numParts) {
				if (numParts > numPartsToReplace) {
					parts = parts.subList(0, numPartsToReplace);
				} else {
					parts.addAll(nonLockedParts.subList(numParts, numPartsToReplace));
				}
			}
		}

		// restore original order/placement of locked parts
		if (clearPreviousPanels) {
			// Iterate through the entries sorted by key (ascending order)
			List<InstPart> finalParts = parts;
			originalLockedPartsOrder.entrySet().stream()
					.sorted(Map.Entry.comparingByKey())
					.forEach(entry -> {
						int newIndex = Math.min(finalParts.size(), entry.getKey());
						finalParts.add(newIndex, entry.getValue().get(0));
					});
		}

		int startingOrder = clearPreviousPanels ? 0 : numPartsToReplace;
		int endingSize = parts.size() + startingOrder;
		for (int i = startingOrder; i < endingSize; i++) {
			parts.get(i - startingOrder).setOrder(i + 1);
		}

		recreateInstPanelsFromInstParts(partNum, parts, clearPreviousPanels);
	}

	public void marshalConfig(GUIConfig config, String path, int cutOff)
			throws JAXBException, IOException {
		SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();
		f.applyPattern("yyMMdd-hh-mm-ss");
		JAXBContext context = JAXBContext.newInstance(GUIConfig.class);
		Marshaller mar = context.createMarshaller();
		mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		mar.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "");
		String actualPath = path.substring(0, path.length() - cutOff);
		mar.marshal(config, new File(actualPath + "_VCConfig.xml"));
		LG.i("File saved: " + path);
	}

	public void marshalPreset(GUIPreset preset, String path) throws JAXBException, IOException {
		SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();
		f.applyPattern("yyMMdd-hh-mm-ss");
		JAXBContext context = JAXBContext.newInstance(GUIPreset.class);
		Marshaller mar = context.createMarshaller();
		mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		mar.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "");
		mar.marshal(preset, new File(path));
		LG.i("File saved: " + path);
	}

	public GUIConfig unmarshallConfig(File f) throws JAXBException, IOException {
		JAXBContext context = JAXBContext.newInstance(GUIConfig.class);
		return (GUIConfig) context.createUnmarshaller().unmarshal(new FileReader(f));
	}

	public GUIPreset unmarshallPreset(File f) throws JAXBException, IOException {
		JAXBContext context = JAXBContext.newInstance(GUIPreset.class);
		return (GUIPreset) context.createUnmarshaller().unmarshal(new FileReader(f));
	}

	public List<Component> makeSettableComponentList() {
		List<Component> cs = new ArrayList<>();
		// melody panel
		cs.add(MelodyGUI.generateMelodiesOnCompose);
		cs.add(null);
		cs.add(MelodyGUI.combineMelodyTracks);
		cs.add(MelodyGUI.randomMelodySameSeed);
		cs.add(MelodyGUI.randomMelodyOnRegenerate);
		cs.add(MelodyGUI.useUserMelody);
		cs.add(MelodyGUI.melodyPatternRandomizeOnCompose);
		cs.add(MelodyGUI.melodyTargetNotesRandomizeOnCompose);

		// bass panel

		// chord panel
		cs.add(ChordGUI.randomChordsGenerateOnCompose);
		//cs.add(randomChordsToGenerate);
		cs.add(ChordGUI.randomChordStruminess);
		cs.add(ChordGUI.randomChordUseChordFill);
		cs.add(ChordGUI.randomChordStretchType);
		cs.add(ChordGUI.randomChordStretchPicker);
		cs.add(ChordGUI.randomChordStretchGenerationChance);
		cs.add(ChordGUI.randomChordMaxStrumPauseChance);
		cs.add(ChordGUI.randomChordVaryLength);
		cs.add(ChordGUI.randomChordExpandChance);
		cs.add(ChordGUI.randomChordSustainChance);
		cs.add(ChordGUI.randomChordMaxSplitChance);
		cs.add(ChordGUI.chordSlashChance);
		cs.add(ChordGUI.randomChordMinVel);
		cs.add(ChordGUI.randomChordMaxVel);
		cs.add(ChordGUI.randomChordPattern);
		cs.add(ChordGUI.randomChordShiftChance);


		// arp panel
		cs.add(ArpGUI.randomArpsGenerateOnCompose);
		//cs.add(randomArpsToGenerate);
		cs.add(ArpGUI.randomArpHitsPicker);
		cs.add(ArpGUI.randomArpHitsPerPattern);
		cs.add(ArpGUI.randomArpAllSameHits);
		cs.add(ArpGUI.randomArpUseChordFill);
		cs.add(ArpGUI.randomArpTranspose);
		cs.add(ArpGUI.randomArpStretchType);
		cs.add(ArpGUI.randomArpStretchPicker);
		cs.add(ArpGUI.randomArpStretchGenerationChance);
		cs.add(ArpGUI.randomArpMaxExceptionChance);
		cs.add(ArpGUI.arpCopyMelodyInst);
		cs.add(ArpGUI.randomArpAllSameInst);
		cs.add(ArpGUI.randomArpLimitPowerOfTwo);
		cs.add(null); // randomArpUseOctaveAdjustments
		cs.add(ArpGUI.randomArpMaxRepeat);
		cs.add(ArpGUI.randomArpMinVel);
		cs.add(ArpGUI.randomArpMaxVel);
		cs.add(ArpGUI.randomArpPattern);
		cs.add(ArpGUI.randomArpShiftChance);
		cs.add(ArpGUI.randomArpMinLength);
		cs.add(ArpGUI.randomArpMaxLength);

		// drum panel
		cs.add(DrumGUI.randomDrumsGenerateOnCompose);
		//cs.add(randomDrumsToGenerate);
		//cs.add(DrumGUI.randomDrumMaxSwingAdjust);
		cs.add(DrumGUI.randomDrumUseChordFill);
		cs.add(DrumGUI.randomDrumSlide);
		cs.add(DrumGUI.combineDrumTracks);
		cs.add(DrumGUI.randomDrumPattern);
		cs.add(DrumGUI.randomDrumVelocityPatternChance);
		cs.add(DrumGUI.randomDrumShiftChance);

		// arrangement panel
		cs.add(ArrangementGUI.randomizeArrangementOnCompose);

		// randomization panel
		cs.add(randomizeInstOnComposeOrGen);
		cs.add(randomizeBpmOnCompose);
		cs.add(randomizeTransposeOnCompose);

		// globals
		cs.add(randomizeScaleModeOnCompose);
		cs.add(regenerateWhenValuesChange);
		cs.add(loopBeat);
		cs.add(loopBeatCount);
		cs.add(midiMode);

		// extras
		cs.add(ExtraSettingsGUI.useMidiCC);
		cs.add(ArrangementGUI.arrangementResetCustomPanelsOnCompose);
		cs.add(null);
		cs.add(null);
		cs.add(loopBeatCompose);
		cs.add(ExtraSettingsGUI.useAllInsts);
		//cs.add(ExtraSettingsGUI.bannedInsts);
		cs.add(ExtraSettingsGUI.pauseBehaviorCombobox);
		cs.add(ExtraSettingsGUI.startFromBar);
		cs.add(ExtraSettingsGUI.rememberLastPos);
		cs.add(ExtraSettingsGUI.snapStartToBeat);
		cs.add(ExtraSettingsGUI.bpmLow);
		cs.add(ExtraSettingsGUI.bpmHigh);
		cs.add(ExtraSettingsGUI.stretchMidi);
		cs.add(ExtraSettingsGUI.displayVeloRectValues);
		cs.add(ExtraSettingsGUI.knobControlByDragging);
		cs.add(DrumGUI.bottomUpReverseDrumPanels);
		cs.add(GenerationGUI.orderedTransposeGeneration);
		cs.add(GenerationGUI.patternApplyPausesWhenGenerating);
		cs.add(ExtraSettingsGUI.highlightPatterns);
		cs.add(ScoreGUI.highlightScoreNotes);
		cs.add(ExtraSettingsGUI.randomizeTimingsOnCompose);
		cs.add(ExtraSettingsGUI.customFilenameAddTimestamp);
		cs.add(GenerationGUI.configHistoryStoreRegeneratedTracks);
		cs.add(ExtraSettingsGUI.sidechainPatternsOnCompose);

		// ---------------- VIBECOMPOSER 2 ------------------------------------

		// drum panel
		cs.add(DrumGUI.randomDrumHitsMultiplierOnGenerate);
		cs.add(null);
		cs.add(DrumGUI.randomDrumsOverrandomize);

		// extra settings
		cs.add(ExtraSettingsGUI.globalNoteLengthMultiplier);
		cs.add(ChordGUI.copyChordsAfterGenerate);
		cs.add(ScoreGUI.miniScorePopup);

		// arps panel
		cs.add(ArpGUI.randomArpCorrectMelodyNotes);

		// extra settings 2.5
		cs.add(GenerationGUI.reuseMidiChannelAfterCopy);
		cs.add(ExtraSettingsGUI.transposeNotePreview);
		cs.add(ExtraSettingsGUI.moveStartToCustomizedSection);
		cs.add(GenerationGUI.allowValuesOutOfRange);

		return cs;
	}

	public static void setComponent(Component c, Integer num, boolean repaint) {
		if (c == null) {
			return;
		} else if (c instanceof ScrollComboPanel) {
			ScrollComboPanel csc = ((ScrollComboPanel) c);
			if (csc.getItemCount() > 0) {
				csc.setSelectedIndex(Math.min(num, csc.getItemCount()));
			}
		} else if (c instanceof KnobPanel) {
			((KnobPanel) c).setInt(num);
		} else if (c instanceof CustomCheckBox) {
			((JCheckBox) c).setSelected(num != null && num > 0);
		} else if (c instanceof CheckButton) {
			((CheckButton) c).setSelected(num != null && num > 0);
		} else if (c instanceof ScrollComboBox2) {
			ScrollComboBox2 csc = ((ScrollComboBox2) c);
			if (csc.getItemCount() > 0) {
				csc.setSelectedIndex(Math.min(num, csc.getItemCount()));
			}
		} else if (c == instrumentTabPane) {
			instrumentTabUndoManager.setRecordingEvents(false);
			instrumentTabPane.setSelectedIndex(num < instrumentTabPane.getComponents().length ? num : num - 1);
			instrumentTabUndoManager.setRecordingEvents(true);
		} else {
			throw new IllegalArgumentException("UNSUPPORTED COMPONENT!" + c.getClass());
		}
		if (repaint) {
			c.repaint();
		}
	}

	public static Integer getComponentValue(Component c) {
		if (c == null) {
			return 0;
		}

		if (c instanceof ScrollComboPanel) {
			return ((ScrollComboPanel) c).getSelectedIndex();
		} else if (c instanceof KnobPanel) {
			return ((KnobPanel) c).getInt();
		} else if (c instanceof CustomCheckBox) {
			return ((JCheckBox) c).isSelected() ? 1 : 0;
		} else if (c instanceof CheckButton) {
			return ((CheckButton) c).isSelected() ? 1 : 0;
		} else if (c instanceof ScrollComboBox2) {
			return ((ScrollComboBox2) c).getSelectedIndex();
		} else if (c == instrumentTabPane) {
			return instrumentTabPane.getSelectedIndex();
		} else {
			throw new IllegalArgumentException("UNSUPPORTED COMPONENT!" + c.getClass());
		}
	}

	public void copyGUItoConfig(GUIConfig gc) {
		copyGUItoConfig(gc, false);
	}

	public void copyGUItoConfig(GUIConfig gc, boolean isNew) {
		// seed
		//GUIConfig gc = new GUIConfig();

		if (MelodyMidiDropPane.userMelody != null) {
			gc.setMelodyNotes(new PhraseNotes(MelodyMidiDropPane.userMelody));
		}

		gc.setVersion(CURRENT_VERSION);
		gc.setRandomSeed(lastRandomSeed);
		gc.setMidiMode(midiMode.isSelected());

		// arrangement
		if (!ArrangementGUI.useArrangement.isSelected()) {
			ArrangementGUI.arrangement.setPreviewChorus(true);
		} else {
			ArrangementGUI.arrangement.setPreviewChorus(false);
		}
		ArrangementGUI.arrangement.setFromTable(ArrangementGUI.scrollableArrangementTable);
		boolean overrideSuccessful = ArrangementGUI.manualArrangement.isSelected()
				&& ArrangementGUI.actualArrangement.setFromActualTable(ArrangementGUI.scrollableArrangementActualTable, false);
		ArrangementGUI.arrangement.setOverridden(overrideSuccessful);

		PatternMap.checkMapBounds(guiConfig.getPatternMaps(), !overrideSuccessful);
		if (isNew) {
			gc.setPatternMaps(PatternMap.multiMapCopy(guiConfig.getPatternMaps()));
		}

		ArrangementGUI.arrangement.setSeed(
				ArrangementGUI.arrangementSeed.getValue() != 0 ? ArrangementGUI.arrangementSeed.getValue() : lastRandomSeed);
		ArrangementGUI.actualArrangement.setSeed(
				ArrangementGUI.arrangementSeed.getValue() != 0 ? ArrangementGUI.arrangementSeed.getValue() : lastRandomSeed);

		gc.setArrangement(ArrangementGUI.arrangement);
		gc.setActualArrangement(ArrangementGUI.actualArrangement);
		gc.setArrangementVariationChance(ArrangementGUI.arrangementVariationChance.getInt());
		gc.setArrangementPartVariationChance(ArrangementGUI.arrangementPartVariationChance.getInt());
		gc.setScaleMidiVelocityInArrangement(ArrangementGUI.arrangementScaleMidiVelocity.isSelected());
		gc.setArrangementEnabled(ArrangementGUI.useArrangement.isSelected());

		// macro
		gc.setScaleMode(ScaleMode.valueOf(scaleMode.getVal()));
		gc.setSoundbankName((String) ExtraSettingsGUI.soundbankFilename.getEditor().getItem());
		gc.setPieceLength(Integer.valueOf(ArrangementGUI.pieceLength.getText()));
		if (ChordGUI.chordProgressionLength.getSelectedIndex() < 2) {
			gc.setFixedDuration(Integer.valueOf(ChordGUI.chordProgressionLength.getVal()));
		} else {
			gc.setFixedDuration(0);
		}

		gc.setTranspose(ScoreGUI.transposeScore.getInt());
		gc.setBpm(Double.valueOf(mainBpm.getInt()));
		gc.setArpAffectsBpm(ArpGUI.arpAffectsBpm.isSelected());
		gc.setBeatDurationMultiplierIndex(beatDurationMultiplier.getSelectedIndex());
		gc.setSwingUnitMultiplierIndex(ExtraSettingsGUI.swingUnitMultiplier.getSelectedIndex());
		gc.setCustomMidiForceScale(GenerationGUI.customMidiForceScale.isSelected());
		gc.setTransposedNotesForceScale(GenerationGUI.transposedNotesForceScale.isSelected());
		gc.setAllowChordRepeats(ChordGUI.allowChordRepeats.isSelected());
		gc.setGlobalSwingOverride(
				globalSwingOverride.isSelected() ? globalSwingOverrideValue.getInt() : null);
		gc.setHumanizeDrums(DrumGUI.humanizeDrums.getInt());
		gc.setHumanizeNotes(ExtraSettingsGUI.humanizeNotes.getInt());

		// parts
		gc.setMelodyEnable(addInst[0].isSelected());
		gc.setBassEnable(addInst[1].isSelected());
		gc.setChordsEnable(addInst[2].isSelected());
		gc.setArpsEnable(addInst[3].isSelected());
		gc.setDrumsEnable(addInst[4].isSelected());

		gc.setMelodyParts((List<MelodyPart>) (List<?>) getInstPartsFromInstPanels(0, false));
		gc.setBassParts((List<BassPart>) (List<?>) getInstPartsFromInstPanels(1, false));
		gc.setChordParts((List<ChordPart>) (List<?>) getInstPartsFromInstPanels(2, false));
		gc.setArpParts((List<ArpPart>) (List<?>) getInstPartsFromInstPanels(3, false));
		gc.setDrumParts((List<DrumPart>) (List<?>) getInstPartsFromInstPanels(4, false));

		gc.setChordGenSettings(getChordSettingsFromUI());

		// melody
		gc.setMelodyUseOldAlgoChance(MelodyGUI.melodyUseOldAlgoChance.getInt());
		gc.setFirstNoteFromChord(MelodyGUI.melodyFirstNoteFromChord.isSelected());
		gc.setFirstNoteRandomized(MelodyGUI.randomChordNote.isSelected());
		gc.setMelodyBasicChordsOnly(MelodyGUI.melodyBasicChordsOnly.isSelected());
		gc.setMelodyTonicNoteTarget(MelodyGUI.melodyTonicNoteTarget.getInt());
		gc.setMelodyChordNoteTarget(MelodyGUI.melodyChordNoteTarget.getInt());
		gc.setMelodyModeNoteTarget(MelodyGUI.melodyModeNoteTarget.getInt());
		gc.setMelodyEmphasizeKey(MelodyGUI.melodyEmphasizeKey.isSelected());

		gc.setMelody1ForcePatterns(MelodyGUI.melody1ForcePatterns.isSelected());
		gc.setMelodyArpySurprises(MelodyGUI.melodyArpySurprises.isSelected());
		gc.setMelodySingleNoteExceptions(MelodyGUI.melodySingleNoteExceptions.isSelected());
		gc.setMelodyFillPausesPerChord(MelodyGUI.melodyFillPausesPerChord.isSelected());
		gc.setMelodyLegacyMode(MelodyGUI.melodyLegacyMode.isSelected());
		gc.setMelodyNewBlocksChance(MelodyGUI.melodyNewBlocksChance.getInt());
		gc.setMelodyUseDirectionsFromProgression(MelodyGUI.melodyUseDirectionsFromProgression.isSelected());
		gc.setMelodyAvoidChordJumps(MelodyGUI.melodyAvoidChordJumpsLegacy.isSelected());
		gc.setMelodyBlockTargetMode(MelodyGUI.melodyBlockTargetMode.getSelectedIndex());
		gc.setNoteTargetDirectionChoice(MelodyGUI.noteTargetDirectionChoice.getSelectedItem());
		gc.setMelodyPatternEffect(MelodyGUI.melodyPatternEffect.getSelectedIndex());
		gc.setMelodyRhythmAccents(MelodyGUI.melodyRhythmAccents.getSelectedIndex());
		gc.setMelodyRhythmAccentsMode(MelodyGUI.melodyRhythmAccentsMode.getSelectedIndex());
		gc.setMelodyRhythmAccentsPocket(MelodyGUI.melodyRhythmAccentsPocket.isSelected());
		gc.setMelodyReplaceAvoidNotes(MelodyGUI.melodyReplaceAvoidNotes.getInt());
		gc.setMelodyMaxDirChanges(MelodyGUI.melodyMaxDirChanges.getInt());
		gc.setMelodyTargetNoteVariation(MelodyGUI.melodyTargetNoteVariation.getInt());

		gc.setMelodyBlockChoicePreference(MelodyGUI.melodyBlockChoicePreference.getValues());
		gc.setMelodyBlockTypePreference(Arrays.stream(MelodyGUI.melodyBlockTypePreference).map(e -> e.getValue()).collect(Collectors.toList()));
		gc.setMelodyUseCustomDurations(MelodyGUI.melodyUseCustomDurations.isSelected());
		gc.setMelodyCustomDurationsRandomWeighting(MelodyGUI.melodyCustomDurationsRandomWeighting.isSelected());
		gc.setMelodyCustomDurationsStrictMode(MelodyGUI.melodyCustomDurationsStrictMode.isSelected());


		// chords
		gc.setUseChordFormula(ChordGUI.useChordFormula.isSelected());
		gc.setLongProgressionSimilarity(ChordGUI.longProgressionSimilarity.getInt());
		gc.setFirstChord(ChordGUI.firstChordSelection.getVal());
		gc.setLastChord(ChordGUI.lastChordSelection.getVal());
		gc.setKeyChangeType(KeyChangeType.valueOf(GenerationGUI.keyChangeTypeSelection.getVal()));
		gc.setCustomChordsEnabled(ChordGUI.userChordsEnabled.isSelected());
		gc.setCustomChords(StringUtils.join(MidiGenerator.chordInts, ","));
		gc.setCustomChordDurations(ChordGUI.userChordsDurations.getText());
		gc.setCustomDurationsEnabled(ChordGUI.userDurationsEnabled.isSelected());
		gc.setSpiceChance(ChordGUI.spiceChance.getInt());
		gc.setSpiceParallelChance(ChordGUI.spiceParallelChance.getInt());
		gc.setDimAug6thEnabled(ChordGUI.spiceAllowDimAug.isSelected());
		gc.setEnable9th13th(ChordGUI.spiceAllow9th13th.isSelected());
		gc.setSpiceFlattenBigChords(ChordGUI.spiceFlattenBigChords.isSelected());
		gc.setSquishProgressively(ChordGUI.squishChordsProgressively.isSelected());
		gc.setChordSlashChance(ChordGUI.chordSlashChance.getInt());
		gc.setSpiceForceScale(ChordGUI.spiceForceScale.isSelected());

		// arps
		gc.setUseOctaveAdjustments(ArpGUI.randomArpUseOctaveAdjustments.isSelected());
		gc.setRandomArpCorrectMelodyNotes(ArpGUI.randomArpCorrectMelodyNotes.isSelected());

		// drums
		boolean isCustomMidiDevice = midiMode.isSelected()
				&& !(midiModeDevices.getVal()).contains("ervill");
		gc.setDrumCustomMapping(DrumGUI.drumCustomMapping.isSelected() && isCustomMidiDevice);
		gc.setDrumCustomMappingNumbers(DrumGUI.drumCustomMappingNumbers.getText());
		gc.setMelodyPatternFlip(MelodyGUI.melodyPatternFlip.isSelected());

		gc.setCombineMelodyTracks(MelodyGUI.combineMelodyTracks.isSelected());
	}

	public void copyConfigToGUI(GUIConfig gc) {
		ArrangementGUI.arrSection.setVisible(false);
		MelodyGUI.randomMelodyOnRegenerate.setSelected(false);
		ArrangementGUI.arrSection.setSelectedIndex(0);

		if (!CURRENT_VERSION.equals(gc.getVersion())) {
			LG.w("Loaded file is for an older version of VibeComposer! Curremt: " + CURRENT_VERSION + ", File version: " + gc.getVersion());
			new TemporaryInfoPopup("Loaded file is for an older version of VibeComposer! Not all features may work the same as they did then!", 2500);
		}

		if (gc.getMelodyNotes() != null) {
			MelodyMidiDropPane.userMelody = gc.getMelodyNotes().makePhrase();
			MelodyGUI.dropPane.getMessage().setText("~MELODY LOADED FROM FILE~");
		} else {
			MelodyMidiDropPane.userMelody = null;
			MelodyGUI.dropPane.getMessage().setText(" * * Drag'n'Drop MIDI Here * * ");
		}

		// seed
		randomSeed.setValue((int) gc.getRandomSeed());
		lastRandomSeed = randomSeed.getValue();
		midiMode.setSelected(gc.isMidiMode());

		// arrangement
		ArrangementGUI.arrangement = gc.getArrangement();
		ArrangementGUI.actualArrangement = gc.getActualArrangement();
		ArrangementGUI.scrollableArrangementTable.setModel(ArrangementGUI.arrangement.convertToTableModel());
		ArrangementGUI.arrangementGUI.setActualModel(ArrangementGUI.actualArrangement.convertToActualTableModel());
		//arrSection.setSelectedIndex(0);
		refreshVariationPopupButtons(ArrangementGUI.actualArrangement.getSections().size());

		ArrangementGUI.arrangementVariationChance.setInt(gc.getArrangementVariationChance());
		ArrangementGUI.arrangementPartVariationChance.setInt(gc.getArrangementPartVariationChance());
		ArrangementGUI.arrangementScaleMidiVelocity.setSelected(gc.isScaleMidiVelocityInArrangement());
		ArrangementGUI.arrangementSeed.setValue(ArrangementGUI.arrangement.getSeed());
		ArrangementGUI.useArrangement.setSelected(gc.isArrangementEnabled());
		ArrangementGUI.manualArrangement.setSelected(true);

		// macro
		scaleMode.setVal(gc.getScaleMode().toString());
		ExtraSettingsGUI.soundbankFilename.getEditor().setItem(gc.getSoundbankName());
		ArrangementGUI.pieceLength.setText(String.valueOf(gc.getPieceLength()));
		setChordProgressionLength(gc.getFixedDuration());

		ScoreGUI.transposeScore.setInt(gc.getTranspose());
		int bpm = (int) Math.round(gc.getBpm());
		mainBpm.getKnob().setMin(Math.min(VibeComposerGUI.mainBpm.getKnob().getMin(), bpm));
		mainBpm.getKnob().setMax(Math.max(VibeComposerGUI.mainBpm.getKnob().getMax(), bpm));

		mainBpm.setInt(bpm);

		ArpGUI.arpAffectsBpm.setSelected(gc.isArpAffectsBpm());
		beatDurationMultiplier.setSelectedIndex(gc.getBeatDurationMultiplierIndex());
		ExtraSettingsGUI.swingUnitMultiplier.setSelectedIndex(gc.getSwingUnitMultiplierIndex());
		GenerationGUI.customMidiForceScale.setSelected(gc.isCustomMidiForceScale());
		GenerationGUI.transposedNotesForceScale.setSelected(gc.isTransposedNotesForceScale());
		ChordGUI.allowChordRepeats.setSelected(gc.isAllowChordRepeats());
		globalSwingOverride.setSelected(gc.getGlobalSwingOverride() != null);
		if (gc.getGlobalSwingOverride() != null) {
			globalSwingOverrideValue.setInt(gc.getGlobalSwingOverride());
		}
		DrumGUI.humanizeDrums.setInt(gc.getHumanizeDrums());
		ExtraSettingsGUI.humanizeNotes.setInt(gc.getHumanizeNotes());

		// parts
		setAddInst(0, gc.isMelodyEnable());
		setAddInst(1, gc.isBassEnable());
		setAddInst(2, gc.isChordsEnable());
		setAddInst(3, gc.isArpsEnable());
		setAddInst(4, gc.isDrumsEnable());

		//DrumGUI.drumCustomMapping.setSelected(guiConfig.isDrumCustomMapping());
		DrumGUI.drumCustomMappingNumbers.setText(gc.getDrumCustomMappingNumbers());
		if (StringUtils.countMatches(DrumGUI.drumCustomMappingNumbers.getText(),
				",") != InstUtils.DRUM_INST_NUMBERS_SEMI.length - 1) {
			DrumGUI.drumCustomMappingNumbers
					.setText(StringUtils.join(InstUtils.DRUM_INST_NUMBERS_SEMI, ","));
		}
		MelodyGUI.melodyPatternFlip.setSelected(gc.isMelodyPatternFlip());

		recreateInstPanelsFromInstParts(0, gc.getMelodyParts());
		recreateInstPanelsFromInstParts(1, gc.getBassParts());

		recreateInstPanelsFromInstParts(2, gc.getChordParts());
		recreateInstPanelsFromInstParts(3, gc.getArpParts());
		recreateInstPanelsFromInstParts(4, gc.getDrumParts());

		setChordSettingsInUI(gc.getChordGenSettings());

		// melody
		MelodyGUI.melodyFirstNoteFromChord.setSelected(gc.isFirstNoteFromChord());
		MelodyGUI.randomChordNote.setSelected(gc.isFirstNoteRandomized());
		MelodyGUI.melodyUseOldAlgoChance.setInt(gc.getMelodyUseOldAlgoChance());
		MelodyGUI.melodyBasicChordsOnly.setSelected(gc.isMelodyBasicChordsOnly());
		MelodyGUI.melodyTonicNoteTarget.setInt(gc.getMelodyTonicNoteTarget());
		MelodyGUI.melodyChordNoteTarget.setInt(gc.getMelodyChordNoteTarget());
		MelodyGUI.melodyModeNoteTarget.setInt(gc.getMelodyModeNoteTarget());
		MelodyGUI.melodyEmphasizeKey.setSelected(gc.isMelodyEmphasizeKey());

		MelodyGUI.melodyArpySurprises.setSelected(gc.isMelodyArpySurprises());
		MelodyGUI.melody1ForcePatterns.setSelected(gc.isMelody1ForcePatterns());
		MelodyGUI.melodySingleNoteExceptions.setSelected(gc.isMelodySingleNoteExceptions());
		MelodyGUI.melodyFillPausesPerChord.setSelected(gc.isMelodyFillPausesPerChord());
		MelodyGUI.melodyLegacyMode.setSelected(gc.isMelodyLegacyMode());
		MelodyGUI.melodyNewBlocksChance.setInt(gc.getMelodyNewBlocksChance());
		MelodyGUI.melodyAvoidChordJumpsLegacy.setSelected(gc.isMelodyAvoidChordJumps());
		MelodyGUI.melodyUseDirectionsFromProgression.setSelected(gc.isMelodyUseDirectionsFromProgression());
		MelodyGUI.melodyBlockTargetMode.setSelectedIndex(gc.getMelodyBlockTargetMode());
		MelodyGUI.noteTargetDirectionChoice.setVal(gc.getNoteTargetDirectionChoice());
		MelodyGUI.melodyPatternEffect.setSelectedIndex(gc.getMelodyPatternEffect());
		MelodyGUI.melodyRhythmAccents.setSelectedIndex(gc.getMelodyRhythmAccents());
		MelodyGUI.melodyRhythmAccentsMode.setSelectedIndex(gc.getMelodyRhythmAccentsMode());
		MelodyGUI.melodyRhythmAccentsPocket.setSelected(gc.isMelodyRhythmAccentsPocket());
		MelodyGUI.melodyReplaceAvoidNotes.setInt(gc.getMelodyReplaceAvoidNotes());
		MelodyGUI.melodyMaxDirChanges.setInt(gc.getMelodyMaxDirChanges());
		MelodyGUI.melodyTargetNoteVariation.setInt(gc.getMelodyTargetNoteVariation());

		MelodyGUI.melodyBlockChoicePreference.setValues(gc.getMelodyBlockChoicePreference());
		for (int i = 0; i < BlockType.values().length; i++) {
			int value = i < gc.getMelodyBlockTypePreference().size() ? gc.getMelodyBlockTypePreference().get(i) : BlockType.values()[i].defaultChance;
			MelodyGUI.melodyBlockTypePreference[i].setValue(value);
		}
		MelodyGUI.melodyUseCustomDurations.setSelected(gc.isMelodyUseCustomDurations());
		MelodyGUI.melodyCustomDurationsRandomWeighting.setSelected(gc.isMelodyCustomDurationsRandomWeighting());
		MelodyGUI.melodyCustomDurationsStrictMode.setSelected(gc.isMelodyCustomDurationsStrictMode());

		// chords
		ChordGUI.spiceChance.setInt(gc.getSpiceChance());
		ChordGUI.spiceParallelChance.setInt(gc.getSpiceParallelChance());
		ChordGUI.spiceAllowDimAug.setSelected(gc.isDimAug6thEnabled());
		ChordGUI.spiceAllow9th13th.setSelected(gc.isEnable9th13th());
		ChordGUI.spiceFlattenBigChords.setSelected(gc.isSpiceFlattenBigChords());
		ChordGUI.squishChordsProgressively.setSelected(gc.isSquishProgressively());
		ChordGUI.chordSlashChance.setInt(gc.getChordSlashChance());
		ChordGUI.spiceForceScale.setSelected(gc.isSpiceForceScale());

		ChordGUI.useChordFormula.setSelected(gc.isUseChordFormula());
		ChordGUI.longProgressionSimilarity.setInt(gc.getLongProgressionSimilarity());
		ChordGUI.firstChordSelection.setVal(gc.getFirstChord());
		ChordGUI.lastChordSelection.setVal(gc.getLastChord());
		GenerationGUI.keyChangeTypeSelection.setVal(gc.getKeyChangeType().toString());
		ChordGUI.userChordsEnabled.setSelected(gc.isCustomChordsEnabled());
		ChordGUI.userChords.setupChords(gc.getCustomChords());
		ChordGUI.userChordsDurations.setText(gc.getCustomChordDurations());
		ChordGUI.userDurationsEnabled.setSelected(gc.isCustomDurationsEnabled());

		// arps
		ArpGUI.randomArpUseOctaveAdjustments.setSelected(gc.isUseOctaveAdjustments());
		ArpGUI.randomArpCorrectMelodyNotes.setSelected(gc.isRandomArpCorrectMelodyNotes());

		ArrangementGUI.arrSection.setVisible(true);

		MelodyGUI.combineMelodyTracks.setSelected(gc.isCombineMelodyTracks());
		//fixCombinedMelodyTracks();


		if (MidiGenerator.chordInts.isEmpty()) {
			MidiGenerator.chordInts = ChordGUI.userChords.getChordList();
		}

	}

	private void sizeRespectingPack() {
		Dimension oldSize = getSize();
		//int ver = everythingPane.getVerticalScrollBar().getValue();
		//int hor = everythingPane.getHorizontalScrollBar().getValue();
		pack();
		setSize(oldSize);
		//everythingPane.getVerticalScrollBar().setValue(ver);
		//everythingPane.getHorizontalScrollBar().setValue(hor);

	}

	// -------------- GENERIC INST PANEL METHODS ----------------------------

	public InstPanel addInstPanelToLayout(int part) {
		return addInstPanelToLayout(part, null, true);
	}

	public InstPanel addInstPanelToLayout(int part, boolean recalc) {
		return addInstPanelToLayout(part, null, recalc);
	}

	public InstPanel addInstPanelToLayout(int part, InstPart initializingPart,
			boolean recalcArrangement) {
		InstPanel ip = InstPanel.makeInstPanel(part, this);
		List<InstPanel> affectedPanels = getAffectedPanels(part);
		int panelOrder = (affectedPanels.size() > 0) ? getValidPanelNumber(affectedPanels) : 1;

		ip.getToggleableComponents().forEach(e -> e.setVisible(isFullMode));
		if (isCustomSection()) {
			ip.toggleGlobalElements(false);
			ip.toggleEnabledCopyRemove(false);
			if (part == 4) {
				ip.getInstrumentBox().setEnabled(true);
			}
		} else {
			ip.setBackground(OMNI.alphen(Constants.instColors[part], 60));
		}

		if (initializingPart != null) {
			ip.setFromInstPart(initializingPart);
		}
		ip.setOrderAndOffset(panelOrder, (initializingPart != null) ? initializingPart.getOrderOffset() : panelOrder);

		affectedPanels.add(panelOrder - 1, ip);
		removeComboBoxArrows(ip);
		if (recalcArrangement) {
			if (ArrangementGUI.actualArrangement != null && ArrangementGUI.actualArrangement.getSections() != null) {
				ArrangementGUI.actualArrangement.getSections().forEach(e -> e.initPartMapFromOldData());
			}
		}


		if (part < 4 || !DrumGUI.bottomUpReverseDrumPanels.isSelected()) {
			((JPanel) getInstPane(part).getViewport().getView()).add(ip, panelOrder - 1);
		} else {
			((JPanel) getInstPane(part).getViewport().getView()).add(ip,
					affectedPanels.size() - panelOrder);
		}
		return ip;
	}

	public static boolean isCustomSection() {
		return ArrangementGUI.arrSection != null && ArrangementGUI.arrSection.getSelectedIndex() != 0 && !ArrangementGUI.GLOBAL.equals(ArrangementGUI.arrSection.getVal());
	}

	public static void removeInstPanel(int inst, int order, boolean singleRemove) {

		List<? extends InstPanel> panels = getInstList(inst);
		InstPanel panel = getPanelByOrder(order, panels);
		((JPanel) getInstPane(inst).getViewport().getView()).remove(panel);

		panels.remove(panel);

		ArrangementGUI.actualArrangement.getSections().forEach(e -> e.initPartMapFromOldData());

		vibeComposerGUI.repaint();
	}

	public static List<InstPart> getInstPartsFromInstPanels(int inst, boolean removeMuted) {
		return getInstPartsFromInstPanels(getInstList(inst), removeMuted);
	}

	public static List<InstPart> getInstPartsFromInstPanels(List<? extends InstPanel> panels, boolean removeMuted) {
		List<InstPart> parts = new ArrayList<>();
		for (InstPanel p : panels) {
			if (!removeMuted || !p.getMuteInst()) {
				parts.add(p.toInstPart(lastRandomSeed));
			}
		}
		InstPart.sortParts(parts);
		return parts;
	}

	private List<InstPart> getInstPartsFromCustomSectionInstPanels(int inst) {
		JPanel panePanel = ((JPanel) getInstPane(inst).getViewport().getView());
		List<InstPart> parts = new ArrayList<>();
		for (Component c : panePanel.getComponents()) {
			if (c instanceof InstPanel) {
				parts.add(((InstPanel) c).toInstPart(
						(lastRandomSeed == 0) ? randomSeed.getValue() : lastRandomSeed));
			}
		}
		return parts;
	}

	private void recreateInstPanelsFromInstParts(int inst, List<? extends InstPart> parts) {
		recreateInstPanelsFromInstParts(inst, parts, true);
	}

	private void recreateInstPanelsFromInstParts(int inst, List<? extends InstPart> parts,
			boolean clearPreviousPanels) {
		if (clearPreviousPanels) {
			List<InstPanel> panels = getAffectedPanels(inst);
			JScrollPane pane = getInstPane(inst);
			for (InstPanel panel : panels) {
				((JPanel) pane.getViewport().getView()).remove(panel);
			}
			panels.clear();
		}

		InstPart.sortParts(parts);
		/*LG.i("Panel " + inst + ", order: " + StringUtils
				.join(parts.stream().map(e -> e.getOrder()).collect(Collectors.toList()), ","));*/
		List<InstPanel> newPanels = new ArrayList<>();
		for (int i = 0; i < parts.size(); i++) {
			newPanels.add(addInstPanelToLayout(inst, false));
		}
		for (int i = 0; i < newPanels.size(); i++) {
			int newPanelOrder = newPanels.get(i).getPanelOrder();
			newPanels.get(i).setFromInstPart(parts.get(i));
			if (!clearPreviousPanels) {
				newPanels.get(i).setOrderAndOffset(newPanelOrder, parts.get(i).getOrderOffset());
			}
			if (inst == 4 && newPanels.get(i).getComboPanel() != null) {
				newPanels.get(i).getComboPanel().reapplyHits();
			}
		}
		recalculateTabPaneCounts();
		instrumentTabPane.repaint();
	}

	private void randomizePanel(InstPanel panel) {
		int partNum = panel.getPartNum();
		if (partNum == 0) {
			melodyGUI.createRandomMelodyPanels(new Random().nextInt(), MelodyGUI.melodyPanels.size() + 1, true,
					(MelodyPanel) panel);
		} else if (partNum == 1) {

		} else if (partNum == 2) {
			chordGUI.createRandomChordPanels(ChordGUI.chordPanels.size() + 1, true,
					(ChordPanel) panel);
		} else if (partNum == 3) {
			arpGUI.createRandomArpPanels(ArpGUI.arpPanels.size() + 1, true, (ArpPanel) panel);
		} else if (partNum == 4) {
			drumGUI.createRandomDrumPanels(DrumGUI.drumPanels.size() + 1, true, (DrumPanel) panel);
		}
	}

	private void addPanel(int part) {
		createPanels(part, getAffectedPanels(part).size() + 1, true);
		recalculateGeneratorAndTabCounts();
		recalculateSoloMuters();
		repaint();
	}

	private void createPanels(int part, int panelCount, boolean onlyAdd) {
		if (part == 0) {
			melodyGUI.createRandomMelodyPanels(panelCount, onlyAdd, null);
		} else if (part == 1) {
			bassGUI.createRandomBassPanels(panelCount, onlyAdd, null);
		} else if (part == 2) {
			chordGUI.createRandomChordPanels(panelCount, onlyAdd, null);
		} else if (part == 3) {
			arpGUI.createRandomArpPanels(panelCount, onlyAdd, null);
		} else if (part == 4) {
			drumGUI.createRandomDrumPanels(panelCount, onlyAdd, null);
		} else {
			throw new IllegalArgumentException("Unsupported panel part!");
		}
	}

	@Deprecated
	protected void __createRandomMelodyPanels(int panelCount, boolean onlyAdd,
			MelodyPanel randomizedPanel) {
		melodyGUI.createRandomMelodyPanels(panelCount, onlyAdd, randomizedPanel);
	}

	@Deprecated
	protected void __createRandomMelodyPanels(int seed, int panelCount, boolean onlyAdd,
			MelodyPanel randomizedPanel) {
		melodyGUI.createRandomMelodyPanels(seed, panelCount, onlyAdd, randomizedPanel);
	}

	@Deprecated
	private void __createRandomBassPanels(int panelCount, boolean onlyAdd,
			BassPanel randomizedPanel) {
		bassGUI.createRandomBassPanels(panelCount, onlyAdd, randomizedPanel);
	}

	@Deprecated
	private void __createRandomBassPanels(int seed, int panelCount, boolean onlyAdd,
			BassPanel randomizedPanel) {
		bassGUI.createRandomBassPanels(seed, panelCount, onlyAdd, randomizedPanel);
	}

	@Deprecated
protected void __createRandomChordPanels(int panelCount, boolean onlyAdd,
			ChordPanel randomizedPanel) {
		ScrollComboBox.discardInteractions();
		List<ChordPanel> affectedChords = (List<ChordPanel>) (List<?>) getAffectedPanels(2);

		Random panelGenerator = new Random();
		List<ChordPanel> removedPanels = new ArrayList<>();
		List<ChordPanel> remainingPanels = new ArrayList<>();
		for (Iterator<ChordPanel> panelI = affectedChords.iterator(); panelI.hasNext();) {
			ChordPanel panel = panelI.next();
			if (!onlyAdd && !panel.getLockInst()) {
				if (removedPanels.size() >= panelCount) {
					((JPanel) ChordGUI.chordScrollPane.getViewport().getView()).remove(panel);
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
					ip = (ChordPanel) addInstPanelToLayout(2);
					needNewChannel = true;
				}
			}
			InstUtils.POOL pool = ip.getInstPool();

			if ((randomizeInstOnComposeOrGen.isSelected() || onlyAdd)
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
			ip.setTransitionSplit((getRandomFromArray(panelGenerator, Constants.MILISECOND_ARRAY_SPLIT, 0)));
			if (GenerationGUI.orderedTransposeGeneration.isSelected()) {
				ip.setTranspose((((ip.getPanelOrder()) % 3) - 1) * 12);
			} else {
				ip.setTranspose((panelGenerator.nextInt(3) - 1) * 12);
			}

			boolean pad = ip.getInstPool() == POOL.LONG_PAD;

			Pair<StrumType, Integer> strumPair = getRandomStrumPair();
			ip.setStrum(strumPair.getRight());
			ip.setStrumType(strumPair.getLeft());
			if (ChordGUI.randomChordDelay.isSelected()) {
				ip.setOffset((getRandomFromArray(panelGenerator, Constants.MILISECOND_ARRAY_DELAY, 0)));
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
				if (beatDurationMultiplier != null && beatDurationMultiplier.getVal() < 0.75) {
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

		repaint();
	}

	@Deprecated
	protected void __createRandomArpPanels(int panelCount, boolean onlyAdd,
			ArpPanel randomizedPanel) {
		arpGUI.createRandomArpPanels(panelCount, onlyAdd, randomizedPanel);
	}

	@Deprecated
	protected void __createRandomDrumPanels(int panelCount, boolean onlyAdd, DrumPanel randomizedPanel) {
		drumGUI.createRandomDrumPanels(panelCount, onlyAdd, randomizedPanel);
	}

	@Deprecated
	private void __setupBlueprintedDrum(Random panelGenerator, int slide, int swingPercent,
			List<Integer> pitches, int panelIndex, DrumPanel ip) {
		DrumPart dpart = DrumDefaults.getDrumFromInstrument(
				!ip.getInstrumentBox().isEnabled() ? ip.getInstrument() : pitches.get(panelIndex));
		int order = DrumDefaults.getOrder(dpart.getInstrument());
		DrumSettings settings = DrumDefaults.drumSettings[order];
		settings.applyToDrumPart(dpart, lastRandomSeed);


		dpart.setOrder(ip.getPanelOrder());
		dpart.setMuted(ip.getMuteInst());
		switch (DrumGUI.randomDrumHitsMultiplierOnGenerate.getSelectedIndex()) {
		case 0:
			break;
		case 1:
			dpart.setHitsPerPattern(dpart.getHitsPerPattern() / 2);
			break;
		case 2:
			dpart.setHitsPerPattern(dpart.getHitsPerPattern() * 3 / 4);
			break;
		case 3:
			dpart.setHitsPerPattern(dpart.getHitsPerPattern() * 3 / 2);
			break;
		case 4:
			dpart.setHitsPerPattern(dpart.getHitsPerPattern() * 2);
			break;
		default:
			throw new IllegalArgumentException("Multiplier index too high.");
		}
		ip.setFromInstPart(dpart);

		//dp.setHitsPerPattern(dp.getHitsPerPattern() * randomDrumHitsMultiplierLastState);

		ip.setFeedbackCount(0);

		if (settings.isSwingable()) {
			ip.setOffset(slide);
			ip.setSwingPercent(swingPercent);
		} else {
			ip.setSwingPercent(50);
		}

		if (settings.isDynamicable() && (ip.getPattern() != RhythmPattern.MELODY1)) {
			double ghostChanceReducer = (DrumGUI.drumPanels.size() > 10) ? 0.8 : 1.0;
			ip.setIsVelocityPattern(panelGenerator
					.nextInt(100) < DrumGUI.randomDrumVelocityPatternChance.getInt() * ghostChanceReducer);
		} else {
			ip.setIsVelocityPattern(false);
		}

		if (DrumGUI.drumPanels.size() > 10 && ip.getPattern() == RhythmPattern.FULL
				&& panelGenerator.nextInt(100) < 30) {
			ip.setPattern(RhythmPattern.ALT);
		}

		if (settings.isVariableShift()
				&& panelGenerator.nextInt(100) < DrumGUI.randomDrumShiftChance.getInt()) {
			// settings set the maximum shift, this sets 0 - max randomly
			ip.setPatternShift(panelGenerator.nextInt(ip.getPatternShift() + 1));
		}

		ip.applyPauseChance(panelGenerator);
		ip.growPattern(panelGenerator, 1, 5);

		//if (dp.getPatternShift() > 0) {
		ip.getComboPanel().reapplyShift();
		//}

		ip.getComboPanel().reapplyHits();
	}

	@Deprecated
	private void __setupOverrandomizedDrum(Random drumPanelGenerator, int slide, int swingPercent,
			List<Integer> pitches, int panelIndex, DrumPanel ip) {
		ip.setInstrument(pitches.get(panelIndex));
		//dp.setPitch(32 + drumPanelGenerator.nextInt(33));


		ip.setChordSpan(drumPanelGenerator.nextInt(2) + 1);
		RhythmPattern pattern = RhythmPattern.FULL;
		// use pattern in half the cases if checkbox selected

		if (DrumGUI.randomDrumPattern.isSelected()) {
			int[] patternWeights = { 35, 60, 80, 90, 90, 100 };
			int randomWeight = drumPanelGenerator.nextInt(100);
			for (int j = 0; j < patternWeights.length; j++) {
				if (randomWeight < patternWeights[j]) {
					pattern = RhythmPattern.VIABLE_PATTERNS.get(j);
					break;
				}
			}
		}

		int hits = 4;
		while (drumPanelGenerator.nextBoolean() && hits < 16) {
			hits *= 2;
		}
		if ((hits / ip.getChordSpan() >= 8)) {
			hits /= 2;
		}

		switch (DrumGUI.randomDrumHitsMultiplierOnGenerate.getSelectedIndex()) {
		case 0:
			break;
		case 1:
			hits /= 2;
			break;
		case 2:
			hits = hits * 3 / 4;
			break;
		case 3:
			hits = hits * 3 / 2;
			break;
		case 4:
			hits *= 2;
			break;
		default:
			throw new IllegalArgumentException("Multiplier index too high.");
		}
		ip.setHitsPerPattern(hits * 2);

		int adjustVelocity = -1 * ip.getHitsPerPattern() / ip.getChordSpan();

		ip.setFeedbackCount(drumPanelGenerator.nextBoolean() ? drumPanelGenerator.nextInt(3) : 0);

		ip.setPattern(pattern);
		int velocityMin = drumPanelGenerator.nextInt(30) + 50 + adjustVelocity;

		ip.setVelocityMax(1 + velocityMin + drumPanelGenerator.nextInt(25));
		ip.setVelocityMin(velocityMin);

		if (pattern != RhythmPattern.FULL) {
			ip.setPauseChance(drumPanelGenerator.nextInt(5) + 0);
		} else {
			ip.setPauseChance(drumPanelGenerator.nextInt(40) + 40);
		}

		// punchy drums - kicks, snares
		if (DrumGUI.PUNCHY_DRUMS.contains(ip.getInstrument())) {
			adjustVelocity += 15;
			ip.setExceptionChance(drumPanelGenerator.nextInt(3));
		} else {
			ip.setOffset(slide);
			ip.setSwingPercent(swingPercent);
			ip.setExceptionChance(drumPanelGenerator.nextInt(10));
			if (drumPanelGenerator.nextInt(100) < 30) {
				ip.setPattern(RhythmPattern.MELODY1);
			}
		}

		if (DrumGUI.randomDrumUseChordFill.isSelected()) {
			ip.setChordSpanFill(ChordSpanFill.getWeighted(drumPanelGenerator.nextInt(100)));
		}
		ip.setFillFlip(false);
		ip.setPatternFlip(false);

		ip.setIsVelocityPattern(drumPanelGenerator.nextInt(100) < Integer
				.valueOf(DrumGUI.randomDrumVelocityPatternChance.getInt()));

		if (drumPanelGenerator.nextInt(100) < DrumGUI.randomDrumShiftChance.getInt()
				&& pattern != RhythmPattern.FULL) {
			ip.setPatternShift(drumPanelGenerator.nextInt(ip.getPattern().pattern.length - 1) + 1);
			ip.getComboPanel().reapplyShift();
		}

		ip.getComboPanel().reapplyHits();
	}

	@Deprecated
	private int[] __displayDrumPart(DrumPart dp, int chords, int maxPatternPerChord) {
		int[] displayArray = new int[chords * maxPatternPerChord];
		List<Integer> patternGenerated = MidiGenerator.generateDrumPatternFromPart(dp);
		patternGenerated = MidiUtils.intersperse(0, dp.getChordSpan() - 1, patternGenerated);
		patternGenerated = MidiUtils.intersperse(0,
				(maxPatternPerChord / dp.getHitsPerPattern()) - 1, patternGenerated);
		//LG.i((StringUtils.join(patternGenerated, ",")));
		int size = patternGenerated.size();
		//LG.i(("Size: " + size));
		int patternValue = (dp.getInstrument() <= 40 || dp.getInstrument() == 53) ? 3 : 1;
		List<Integer> fillPattern = dp.getChordSpanFill().getPatternByLength(chords,
				dp.isFillFlip());
		for (int c = 0; c < chords; c++) {
			if (fillPattern.get(c) < 1) {
				continue;
			}
			for (int j = 0; j < maxPatternPerChord; j++) {
				int index = c * maxPatternPerChord + j;
				if (patternGenerated.get(index % patternGenerated.size()) > 0) {
					displayArray[index] += patternValue;
				}

			}
		}
		return displayArray;
	}

	private static int getValidPanelNumber(List<? extends InstPanel> panels) {
		panels.sort(Comparator.comparing(e1 -> e1.getPanelOrder()));
		if (panels.stream().anyMatch(e -> e.getLockInst())) {
			return getLowestAvailablePanelNumber(panels);
		} else {
			return getLowestAvailablePanelNumber(panels);
		}
	}

	private static int getHighestPanelNumber(List<? extends InstPanel> panels) {
		int highest = 0;
		for (InstPanel p : panels) {
			highest = (p.getPanelOrder() > highest) ? p.getPanelOrder() : highest;
		}
		return highest + 1;
	}


	private static int getLowestAvailablePanelNumber(List<? extends InstPanel> panels) {
		int lowest = 0;
		for (InstPanel p : panels) {
			if (p.getPanelOrder() - lowest > 1) {
				return lowest + 1;
			}
			lowest++;
		}
		return lowest + 1;
	}

	public static InstPanel getPanelByOrder(int order, List<? extends InstPanel> panels) {
		return panels.stream().filter(e -> e.getPanelOrder() == order).findFirst().get();
	}

	public static InstPanel getPanelByOrder(int part, int partOrder) {
		return getInstList(part).stream().filter(e -> e.getPanelOrder() == partOrder).findFirst()
				.get();
	}

	private static int getRandomFromArray(Random generator, int[] array, int from) {
		return getRandomFromToArray(generator, array, from, array.length);
	}

	private static int getRandomFromToArray(Random generator, int[] array, int from, int to) {
		from = Math.max(from, 0);
		to = Math.min(to, array.length);
		return array[generator.nextInt(to - from) + from];
	}


	public static String microsecondsToTimeString(long l) {
		long i = l / 1000000;
		long m = i / 60;
		long s = i % 60;
		String sM = String.valueOf(m);
		String sS = String.valueOf(s);
		if (sS.length() < 2)
			sS = "0" + sS;
		String v = sM + ":" + sS;
		return v;
	}

	public static String millisecondsToTimeString(int l) {
		long i = l / 1000;
		long m = i / 60;
		long s = i % 60;
		String sM = String.valueOf(m);
		String sS = String.valueOf(s);
		if (sS.length() < 2)
			sS = "0" + sS;
		String v = sM + ":" + sS;
		return v;
	}

	public static String millisecondsToDetailedTimeString(int l) {
		long i = l / 1000;
		long m = i / 60;
		long s = i % 60;
		String sM = String.valueOf(m);
		String sS = String.valueOf(s);
		if (sS.length() < 2)
			sS = "0" + sS;
		String v = sM + ":" + sS + "." + (l % 1000);
		return v;
	}

	public static long msToTicks(long ms) {
		if (ms == 0 || sequencer.getSequence() == null)
			return 0;
		float fps = sequencer.getSequence().getDivisionType();
		try {
			if (fps == Sequence.PPQ)
				return (long) (ms * sequencer.getTempoInBPM()
						* sequencer.getSequence().getResolution() / 60000000);
			else if (fps > Sequence.PPQ)
				return (long) (ms * fps * sequencer.getSequence().getResolution() / 1000000);
			else
				throw new Exception();
		} catch (Exception e) {
			return 0;
		}
	}

	public static void midiNavigate(long sliderValue) {
		midiNavigate(sliderValue, 25);
	}

	public static void midiNavigate(long sliderValue, int offset) {
		long time = (sliderValue - offset) * 1000;
		long timeTicks = msToTicks(time);
		if (!(time != 0 && timeTicks == 0) | time >= sequencer.getMicrosecondLength()) {
			if (time >= 0) {
				sequencer.setMicrosecondPosition(time);
				//midiPauseProg = timeTicks;
				//midiPauseProgMs = time;

			} else {
				sequencer.setMicrosecondPosition(0);
				//midiPauseProg = 0;
				//midiPauseProgMs = 0;
			}
		}
		flushMidiEvents();
	}

	public int selectRandomStrumByStruminess() {
		return singleWeightedSelectFromArray(Constants.MILISECOND_ARRAY_STRUM, ChordGUI.randomChordStruminess.getInt(),
				1);
	}

	public Pair<StrumType, Integer> getRandomStrumPair() {
		StrumType sType = selectTypeByStrumminess(ChordGUI.randomChordStruminess.getInt());
		Integer strum = MidiUtils.getRandom(new Random(), sType.CHOICES.toArray(new Integer[] {}));
		return Pair.of(sType, strum);
	}

	private StrumType selectTypeByStrumminess(int int1) {
		List<StrumType> types = StrumType.getWeighted(new Random().nextInt(100));
		StrumType type = types.get(new Random().nextInt(types.size()));
		return type;
	}

	public int singleWeightedSelectFromArray(int[] oldArray, int weight, int from) {
		int[] array = Arrays.copyOfRange(oldArray, from, oldArray.length);
		//LG.i(("New array: " + Arrays.toString(array)));
		Random weightGen = new Random();
		double[] realWeights = new double[array.length];
		int mid = array.length / 2;
		for (int i = 0; i < array.length; i++) {
			realWeights[i] = 1.0 / Double.valueOf(array.length);
		}
		double lowMultiplier = 1.0;
		double highMultiplier = 1.0;
		// 100 max, 0 min
		// weight 80 -> multiply high by
		if (weight > 50) {
			highMultiplier = 1 + Math.abs(weight - 50) / 100.0;
			lowMultiplier = 1.0 / highMultiplier;
		} else {
			lowMultiplier = 1 + Math.abs(50 - weight) / 100.0;
			highMultiplier = 1.0 / lowMultiplier;
		}
		double totalWeight = 0;
		for (int i = 0; i < array.length; i++) {
			double multiplier = ((i < mid) ? lowMultiplier : highMultiplier);
			realWeights[i] *= Math.pow(multiplier, Math.abs(i - mid));
			totalWeight += realWeights[i];
		}
		double targetWeight = totalWeight * weightGen.nextDouble();
		//LG.i(("Total: " + totalWeight + ", Target: " + targetWeight));
		// -> strength of reduction depends on how far from ends
		totalWeight = 0;

		//LG.i(("New array: " + Arrays.toString(realWeights)));
		for (int i = 0; i < array.length; i++) {
			totalWeight += realWeights[i];
			if (totalWeight >= targetWeight) {
				return array[i];
			}
		}
		return array[array.length - 1];

	}

	public static int getAbsoluteOrder(int partNum, int partOrder) {
		List<Integer> allPanelOrders = getInstList(partNum).stream().map(e -> e.getPanelOrder())
				.collect(Collectors.toList());
		allPanelOrders.sort(Comparator.comparing(e -> e));
		if (allPanelOrders.contains(partOrder)) {
			return allPanelOrders.indexOf(partOrder);
		}
		throw new IllegalArgumentException("Absolute order not found!");
	}

	@Deprecated
	public static void __pianoRoll() {
		if (MidiGenerator.LAST_SCORES.isEmpty()) {
			return;
		}
		if (ScoreGUI.scorePanel == null) {
			ScoreGUI.scorePanel = new ShowPanelBig();
			((JPanel) ScoreGUI.scoreScrollPane.getViewport().getView()).add(ScoreGUI.scorePanel);
		}
		ShowPanelBig.scoreBox.setSelectedIndex(0);

		ScoreGUI.scorePanel.setScore();
		ScoreGUI.scoreScrollPane.repaint();
	}


	public static boolean canRegenerateOnChange() {
		return sequencer != null && regenerateWhenValuesChange.isSelected()
				&& !heavyBackgroundTasksInProgress && ArrangementGUI.arrSection.getSelectedIndex() == 0;
	}

	public static int calculateSectionMeasureStart(int sectIndex) {
		if (ArrangementGUI.actualArrangement == null || ArrangementGUI.actualArrangement.getSections() == null
				|| sliderMeasureStartTimes == null || sliderMeasureStartTimes.isEmpty()
				|| sectIndex < 0 || sectIndex > ArrangementGUI.actualArrangement.getSections().size()) {
			return 0;
		}
		List<Section> secs = ArrangementGUI.actualArrangement.getSections();
		int measureCounter = 0;
		for (int i = 1; i < secs.size() && i < sectIndex; i++) {
			measureCounter += secs.get(i).getMeasures();
		}
		return OMNI.clamp(measureCounter, 0, sliderMeasureStartTimes.size() - 1);
	}

	public static void setSliderStart(int val) {
		if (val >= slider.getMaximum()) {
			return;
		}
		if (slider.getUpperValue() < val) {
			slider.setUpperValue(val);
			midiNavigate(val, 0);
		}
		slider.setValue(val);
	}

	public static void setSliderEnd(int val) {
		if (val >= slider.getMaximum()) {
			val = Math.max(0, slider.getMaximum() - 1);
		}
		if (slider.getValue() > val) {
			slider.setValue(val);
		}
		slider.setUpperValue(val);
		midiNavigate(val, 0);
	}

	public static void trySliderStartChange(int sectIndex) {
		if (ExtraSettingsGUI.moveStartToCustomizedSection == null || !ExtraSettingsGUI.moveStartToCustomizedSection.isSelected()
				|| sliderMeasureStartTimes == null)
			return;

		int measure = calculateSectionMeasureStart(sectIndex);
		int startSliderVal = sliderMeasureStartTimes.get(measure);
		setSliderStart(startSliderVal);
	}

	public static void recolorVariationPopupButton(int sectionOrder) {
		if (ArrangementGUI.actualArrangement == null || sectionOrder - 1 >= ArrangementGUI.actualArrangement.getSections().size())
			return;

		for (Component c : ArrangementGUI.variationButtonsPanel.getComponents()) {
			if (c instanceof JButton) {
				JButton cbutt = (JButton) c;
				if (cbutt.getText().equals("Edit " + sectionOrder)) {
					VibeComposerGUI.recolorVariationPopupButton(cbutt,
							ArrangementGUI.actualArrangement.getSections().get(sectionOrder - 1));
					break;
				}
			}
		}
	}

	public static List<? extends InstPanel> sortPanels(List<? extends InstPanel> panels) {
		Collections.sort(panels, Comparator.comparing(e1 -> e1.getPanelOrder()));
		return panels;
	}

	public void sendMidiMessage(ShortMessage midiMessage) {
		if (midiMode.isSelected() && device != null) {
			device.getReceivers().forEach(e -> e.send(midiMessage, -1));
		} else if (synth != null && synth.isOpen()) {
			synth.getReceivers().forEach(e -> e.send(midiMessage, -1));
		}
	}

	@Deprecated
	public void __toggleShowScorePopup() {
		if (ScoreGUI.scorePanel != null) {
			if (instrumentTabPane.getComponentCount() == 8) {
				instrumentTabPane.remove(ScoreGUI.scoreScrollPane);
				if (ScoreGUI.miniScorePopup.isSelected()) {
					ShowPanelBig.beatWidthBases = ShowPanelBig.beatWidthBasesSmall;
					ShowPanelBig.beatWidthBase = ShowPanelBig.beatWidthBases
							.get(ShowPanelBig.beatWidthBaseIndex);
					ScoreGUI.scorePanel.updatePanelHeight(300);
					//ScoreGUI.scoreScrollPane.setMaximumSize(new Dimension(600, 300));
					ScoreGUI.scorePanel.getShowArea().setNoteHeight(4);
					ScoreGUI.scorePanel.setScore();
					ScoreGUI.scorePanel.setAlignmentX(LEFT_ALIGNMENT);
					ScoreGUI.scoreScrollPane.repaint();
					SwingUtilities.invokeLater(() -> {
						ShowPanelBig.zoomIn(ShowPanelBig.areaScrollPane, new Point(0, 0), 0.0, 0.0);
					});
				}
				ScoreGUI.scorePopup = new ShowScorePopup(ScoreGUI.scoreScrollPane);
			} else {
				if (ScoreGUI.scorePopup != null) {
					ScoreGUI.scorePopup.close();
					ScoreGUI.scorePopup = null;
				}
				if (instrumentTabPane.getComponentCount() < 8) {
					instrumentTabPane.add(ScoreGUI.scoreScrollPane, 7);
					instrumentTabPane.setTitleAt(7, " Score ");
				}

			}
		}
	}

	public static long lastPlayedMs = 0;

	public static void playNextNote(int keyboardTranspose, int velocity, int part, int partOrder) {
		part = part < 0 ? 0 : part;
		partOrder = partOrder < 1 ? 1 : partOrder;
		LG.i(keyboardTranspose + ", " + velocity + ", " + part + ", " + partOrder);
		Phrase nextNoteMelody = part == 0 && guiConfig.getMelodyParts().get(partOrder-1).getCustomMidi() != null
				? guiConfig.getMelodyParts().get(partOrder-1).getCustomMidi().makePhrase() : null;
		int transpose = keyboardTranspose;
		if (nextNoteMelody == null) {
			LG.d("No custom melody to play!");
			nextNoteMelody = MelodyMidiDropPane.userMelody;
			if (nextNoteMelody == null) {
				LG.d("No user melody/midi to play!");
				Part scorePart = (ScoreGUI.scorePanel == null || ScoreGUI.scorePanel.score == null) ? null : ScoreGUI.scorePanel.score.getPart(instNames[part] + "" + (partOrder-1));
				nextNoteMelody = scorePart == null ? null : scorePart.getPhrase(0);
				if (nextNoteMelody == null) {
					LG.i("No actual melody to play!");
					return;
				}
				transpose += -1 * (getInstList(part).get(partOrder-1).getTranspose() + ScoreGUI.transposeScore.getInt());
			}
		}
		int nextNoteIndex = getNextNoteIndex(part, partOrder) % nextNoteMelody.size();
		Note n;
		while ((n = nextNoteMelody.getNote(nextNoteIndex++)) != null) {
			if (n.getPitch() >= 1) {
				playNote(n.getPitch() + transpose, (int) (n.getDuration() * 1000 * 60 / guiConfig.getBpm()),
						velocity, part, partOrder, ArrangementGUI.actualArrangement.getSections().get(0), true);
				break;
			}
		}
	}

	public static void playNote(int pitch, int durationMs, int velocity, int part, int partOrder,
			Section sec, boolean overrideLastPlayed) {
		if (sequencer == null || !sequencer.isOpen() || (pitch < 0)
				|| (!overrideLastPlayed && System.currentTimeMillis() - lastPlayedMs < 100)) {
			return;
		}

		InstPanel ip = getPanelByOrder(part, partOrder);
		Integer trackNum = ip.getSequenceTrack();
		if (trackNum == null || trackNum < 0) {
			return;
		}
		try {
			if (part < 4 && ExtraSettingsGUI.transposeNotePreview.isSelected()) {
				Pair<ScaleMode, Integer> scaleKey = keyChangeAt(
						ArrangementGUI.actualArrangement.getSections().indexOf(sec));
				int extraTranspose = (part > 0) ? ip.getTranspose() : 0;
				List<Note> notes = Collections.singletonList(new Note(
						(part > 0) ? pitch : (pitch + ip.getTranspose()), durationMs / 1000.0));
				if (scaleKey != null) {
					boolean snapToScale = (scaleKey.getLeft() != ScaleMode.IONIAN)
							|| GenerationGUI.transposedNotesForceScale.isSelected();
					MidiUtils.transposeNotes(notes, ScaleMode.IONIAN.noteAdjustScale,
							scaleKey.getLeft().noteAdjustScale, snapToScale);
					extraTranspose += scaleKey.getRight();
				}

				pitch = notes.get(0).getPitch() + ScoreGUI.transposeScore.getInt() + extraTranspose
						+ sec.getTransposeVariation(part, partOrder);

				if (pitch < 0 || pitch > 127) {
					LG.d("Pitch too high to play: " + pitch);
					return;
				}
			}

			vibeComposerGUI.playNote(ip.getMidiChannel() - 1, pitch, velocity, durationMs);

			// LEGACY WAY THROUGH SEQUENCER - not needed?
			/*boolean test = true;
			if (test) {
				return;
			}
			Track trk = sequencer.getSequence().getTracks()[trackNum];
			ShortMessage noteOnMsg = new ShortMessage();
			noteOnMsg.setMessage(ShortMessage.NOTE_ON, ip.getMidiChannel() - 1, pitch, velocity);
			ShortMessage noteOffMsg = new ShortMessage();
			noteOffMsg.setMessage(ShortMessage.NOTE_OFF, ip.getMidiChannel() - 1, pitch, 0);

			long startPos = (sequencer.isRunning()) ? sequencer.getTickPosition() : 0;

			int startDelayMicroseconds = -5000;
			MidiEvent noteOn = new MidiEvent(noteOnMsg,
					startPos + (msToTicks(startDelayMicroseconds)));
			trk.add(noteOn);
			MidiEvent noteOff = new MidiEvent(noteOffMsg,
					startPos + (msToTicks(startDelayMicroseconds + durationMs * 1000)));
			trk.add(noteOff);

			lastPlayedMs = System.currentTimeMillis();

			if (!sequencer.isRunning() && !(currentMidiEditorPopup != null
					&& currentMidiEditorPopup.isVisible()
					&& MidiEditPopup.regenerateInPlaceChoice)) {
				long returnPos = sequencer.getTickPosition();
				boolean prevSoloState = sequencer.getTrackSolo(trackNum);
				sequencer.setTrackSolo(trackNum, true);
				sequencer.setTickPosition(0);
				sequencer.start();
				Timer tmr = new Timer(durationMs, new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						sequencer.stop();
						flushMidiEvents();
						sequencer.setTickPosition(returnPos);
						sequencer.setTrackSolo(trackNum, prevSoloState);
					}
				});
				tmr.setRepeats(false);
				tmr.start();
			}
			queueMidiEventForRemoval(trackNum, noteOff);
			queueMidiEventForRemoval(trackNum, noteOn);*/
		} catch (InvalidMidiDataException e) {
			LG.e(e);
		}
	}

	private void playNote(int midiChannel, int note, int velocity, int durationMs) throws InvalidMidiDataException {
		if (!midiMode.isSelected()) {
			if (synth == null) {
				if (sequencer.isRunning()) {
					sequencer.stop();
				}
				synth = loadSynth();
				LG.i("Loaded new synth!");
			}
			MidiChannel[] channels = synth.getChannels();
			MidiChannel channel = channels[midiChannel];
			channel.noteOn(note, velocity);
			Timer tmr = new Timer(durationMs, e -> channel.noteOff(note));
			tmr.setRepeats(false);
			tmr.start();
		} else {
			if (device == null) {
				LG.i("Can't play into a null midi device!");
				return;
			}
			ShortMessage noteOnMsg = new ShortMessage();
			noteOnMsg.setMessage(ShortMessage.NOTE_ON, midiChannel, note, velocity);
			ShortMessage noteOffMsg = new ShortMessage();
			noteOffMsg.setMessage(ShortMessage.NOTE_OFF, midiChannel, note, 0);

			device.getReceivers().forEach(e -> e.send(noteOnMsg, -1));
			Timer tmr = new Timer(durationMs, e -> device.getReceivers().forEach(r -> r.send(noteOffMsg, -1)));
			tmr.setRepeats(false);
			tmr.start();
		}
	}

	/*public static void queueMidiEventForRemoval(int trackNum, MidiEvent mve) {
		if (midiEventsToRemove.containsKey(trackNum)) {
			midiEventsToRemove.get(trackNum).add(mve);
		} else {
			List<MidiEvent> mves = new ArrayList<>();
			mves.add(mve);
			midiEventsToRemove.put(trackNum, mves);
		}
	}*/

	public static void flushMidiEvents() {
		if (sequencer == null || !sequencer.isOpen() || midiEventsToRemove.isEmpty()) {
			return;
		}

		midiEventsToRemove.entrySet().forEach(mves -> {
			Track[] trks = sequencer.getSequence().getTracks();
			if (mves.getKey() < trks.length) {
				Track trk = trks[mves.getKey()];
				mves.getValue().forEach(e -> trk.remove(e));
			}
		});
		midiEventsToRemove.clear();
	}

	public static Pair<ScaleMode, Integer> keyChangeAt(int sectionIndex) {
		if (ArrangementGUI.actualArrangement == null || ArrangementGUI.actualArrangement.getSections() == null || sectionIndex < 0
				|| sectionIndex >= ArrangementGUI.actualArrangement.getSections().size()) {
			return null;
		}

		ScaleMode lastMode = ScaleMode.valueOf(scaleMode.getVal());
		int lastKeyChange = 0;
		for (int i = 0; i < sectionIndex; i++) {
			Section sec = ArrangementGUI.actualArrangement.getSections().get(i);
			if (sec.isSectionVar(4)) {
				SectionConfig secC = sec.getSecConfig();
				lastMode = secC.getCustomScale() != null ? secC.getCustomScale() : lastMode;
				lastKeyChange = secC.getCustomKeyChange() != null ? secC.getCustomKeyChange()
						: lastKeyChange;
			}
		}
		return Pair.of(lastMode, lastKeyChange);
	}

	public static boolean isSingleSolo() {
		int groupIndex = -1;
		for (int i = 0; i < groupSoloMuters.size(); i++) {
			if (groupSoloMuters.get(i).soloState != State.OFF) {
				if (groupIndex >= 0) {
					return false;
				}
				groupIndex = i;
			}
		}
		if (groupIndex < 0) {
			return false;
		}
		boolean foundSolo = false;
		for (InstPanel ip : getInstList(groupIndex)) {
			if (ip.getSoloMuter().soloState != State.OFF) {
				if (foundSolo) {
					return false;
				}
				foundSolo = true;
			}
		}
		return foundSolo;
	}

	public static String getFilenameForSaving(String oldName) {
		return oldName.replaceFirst("bpm[0-9]{1,3}_", "bpm" + mainBpm.getInt() + "_");
	}
}
