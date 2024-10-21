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
import jm.music.data.Phrase;
import jm.music.tools.Mod;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.vibehistorian.vibecomposer.Components.*;
import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.KeyChangeType;
import org.vibehistorian.vibecomposer.Enums.PatternJoinMode;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Enums.StrumType;
import org.vibehistorian.vibecomposer.Helpers.CheckBoxIcon;
import org.vibehistorian.vibecomposer.Helpers.FileTransferHandler;
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
import org.vibehistorian.vibecomposer.Parts.DrumPartsWrapper;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// main class

public class VibeComposerGUI extends JFrame
		implements ActionListener, ItemListener, WindowListener {

	private static final long serialVersionUID = -677536546851756969L;

	private static final String BUG_HUNT_MESSAGE = "You found a bug! Save your project as a new preset, and send the .xml to my email: vibehistorian@gmail.com!";
	private static final String FILENAME_VALID_CHARACTERS = "[a-zA-Z0-9,\\-_']";
	private static final String FILENAME_VALID_NAME = "^" + FILENAME_VALID_CHARACTERS + "+$";
	private static final String MIDIS_FOLDER = "midis";
	private static final String DRUMS_FOLDER = "drums";
	private static final String MIDI_HISTORY_FOLDER = MIDIS_FOLDER + "/midi_history";
	private static final String PRESET_FOLDER = "presets";
	private static final String SOUNDBANK_FOLDER = ".";
	private static final String EXPORT_FOLDER = "exports";
	private static final String MID_EXTENSION = ".mid";
	private static final String SAVED_MIDIS_FOLDER_BASE = "/saved_";
	public static final String TEMPORARY_SEQUENCE_MIDI_NAME = "tempSequenceMidi.mid";

	public static List<Image> SECTION_VARIATIONS_ICONS = new ArrayList<>();
	private static final String[] SECTION_VAR_ICON_NAMES = new String[] { "v0_skipChord.png",
			"v1_swapChords.png", "v2_swapMelody.png", "v3_melodySpeed.png", "v4_keyChange.png", };
	public static List<Image> SECTION_TRANSITION_ICONS = new ArrayList<>();
	private static final String[] SECTION_TRANSITION_ICON_NAMES = new String[] { "v5_transUp.png",
			"v6_transDown.png", "v7_transCut.png", "v8_halvedTempo.png" };
	public static List<Image> LOCK_COMPONENT_ICONS = new ArrayList<>();
	private static final String[] LOCK_COMPONENT_ICON_NAMES = new String[] { "lock.png",
			"toggle_lock.png", "lock_white.png", "toggle_lock_white.png" };

	public static final int[] MILISECOND_ARRAY_STRUM = { 0, 31, 62, 125, 250, 333, 375, 500, 666,
			750, 1000, 1333, 1500, 2000 };
	public static final List<Integer> MILISECOND_LIST_STRUM = Arrays.stream(MILISECOND_ARRAY_STRUM)
			.mapToObj(e -> Integer.valueOf(e)).collect(Collectors.toList());

	public static final int[] MILISECOND_ARRAY_FEEDBACK = { -2000, -1500, -1333, -1000, -750, -666,
			-500, -375, -333, -250, -125, -62, -31, 31, 62, 125, 250, 333, 375, 500, 666, 750, 1000,
			1333, 1500, 2000 };
	public static final List<Integer> MILISECOND_LIST_FEEDBACK = Arrays
			.stream(MILISECOND_ARRAY_FEEDBACK).mapToObj(e -> Integer.valueOf(e))
			.collect(Collectors.toList());

	public static final int[] MILISECOND_ARRAY_DELAY = { 0, 62, 125, 250, 333 };
	public static final int[] MILISECOND_ARRAY_SPLIT = { 625, 750, 875 };

	public static GUIPreset defaultGuiPreset = null;

	public static VariationPopup varPopup = null;
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
	Color arrangementLightModeText = new Color(220, 220, 220);
	public static int arrangementDarkModeLowestColor = 100;
	Color arrangementDarkModeText = new Color(50, 50, 50);
	public static int arrangementLightModeHighestColor = 180;

	private static Set<Component> toggleableComponents = new HashSet<>();

	private static List<JSeparator> separators = new ArrayList<>();

	private static Soundbank soundfont = null;
	private Synthesizer synth = null;
	private boolean isSoundbankSynth = false;
	private boolean needSoundbankRefresh = false;

	public static GUIConfig guiConfig = new GUIConfig();
	public static MidiGenerator melodyGen = null;
	public static ScrollComboBox<GUIConfig> configHistory = new ScrollComboBox<>(false);

	public static Color[] instColors = { Color.blue, Color.black, Color.green, Color.magenta,
			Color.yellow };
	public static String[] instNames = { "Melody", "Bass", "Chords", "Arps", "Drums" };

	// instrument panels added into scrollpanes
	public static List<MelodyPanel> melodyPanels = new ArrayList<>();
	public static List<BassPanel> bassPanels = new ArrayList<>();
	public static List<ChordPanel> chordPanels = new ArrayList<>();
	public static List<ArpPanel> arpPanels = new ArrayList<>();
	public static List<DrumPanel> drumPanels = new ArrayList<>();

	public static List<InstPanel> getAffectedPanels(int inst) {
		List<InstPanel> affectedPanels = (arrSection == null || arrSection.getSelectedIndex() == 0)
				? (List<InstPanel>) getInstList(inst)
				: getSectionPanelList(inst);
		return affectedPanels;
	}

	public static final List<Integer> TYPICAL_MIDI_CH_START = Arrays.asList(1,9,11,2,10);
	public static final Map<Integer, List<Integer>> TYPICAL_MIDI_CH = new HashMap<>();
	static {
		TYPICAL_MIDI_CH.put(0, Arrays.asList(1,7,8));
		TYPICAL_MIDI_CH.put(1, Arrays.asList(9));
		TYPICAL_MIDI_CH.put(2, Arrays.asList(11,12,13,14,15));
		TYPICAL_MIDI_CH.put(3, Arrays.asList(2,3,4,5,6,7,8));
		TYPICAL_MIDI_CH.put(4, Arrays.asList(10));
	}

	public static List<? extends InstPanel> getInstList(int order) {
		switch (order) {
		case 0:
			return melodyPanels;
		case 1:
			return bassPanels;
		case 2:
			return chordPanels;
		case 3:
			return arpPanels;
		case 4:
			return drumPanels;
		}
		if (order < 0 || order > 4) {
			throw new IllegalArgumentException("Inst list order wrong.");
		}
		return null;
	}

	public static JScrollPane getInstPane(int order) {
		switch (order) {
		case 0:
			return melodyScrollPane;
		case 1:
			return bassScrollPane;
		case 2:
			return chordScrollPane;
		case 3:
			return arpScrollPane;
		case 4:
			return drumScrollPane;
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

	public static int getNextFreeMidiChannel(int order, int panelOrder) {
		List<InstPanel> instPanels = (List<InstPanel>) getInstList(order);
		List<Integer> typicalChannels = TYPICAL_MIDI_CH.get(order);
		Set<Integer> usedChannels = instPanels.stream().map(e -> e.getMidiChannel()).collect(Collectors.toSet());
		return typicalChannels.stream().filter(e -> !usedChannels.contains(e)).findFirst()
				.orElse(TYPICAL_MIDI_CH_START.get(order) + (panelOrder - 1) % typicalChannels.size());
	}

	// arrangement
	public static Arrangement arrangement;
	public static Arrangement actualArrangement;

	JPanel arrangementSettings;
	KnobPanel arrangementVariationChance;
	public static KnobPanel arrangementPartVariationChance;
	public static CheckButton manualArrangement;
	JTextField pieceLength;
	RandomValueButton arrangementSeed;
	CheckButton useArrangement;
	JCheckBox randomizeArrangementOnCompose;
	public static final String GLOBAL = "Global";
	public static ArrangementSectionSelectorPanel arrSection;
	public static JScrollPane arrSectionPane;
	public static boolean switchTabPaneAfterApply;
	JPanel arrangementMiddleColoredPanel;
	ScrollComboBox<String> newSectionBox;

	// instrument scrollers
	public static JTabbedPane instrumentTabPane = new JTabbedPane(JTabbedPane.TOP);
	public static JScrollPane scoreScrollPane;
	public static ShowPanelBig scorePanel;
	public static final int DEFAULT_WIDTH = 1600;
	public static final int DEFAULT_HEIGHT = 400;
	public static Dimension scrollPaneDimension = new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT);
	int arrangementRowHeaderWidth = 120;
	public static final int TABLE_COLUMN_MIN_WIDTH = 80;

	public static JScrollPane melodyScrollPane;
	public static JScrollPane bassScrollPane;
	public static JScrollPane chordScrollPane;
	public static JScrollPane arpScrollPane;
	public static JScrollPane drumScrollPane;

	public static JPanel melodyParentPanel;
	public static JPanel bassParentPanel;
	public static JPanel chordParentPanel;
	public static JPanel arpParentPanel;
	public static JPanel drumParentPanel;

	JScrollPane arrangementScrollPane;
	JScrollPane arrangementActualScrollPane;
	public static JTable scrollableArrangementTable;
	public static JTable scrollableArrangementActualTable;
	public static boolean arrangementTableColumnDragging = false;
	public static boolean actualArrangementTableColumnDragging = false;

	JPanel actualArrangementCombinedPanel;
	JPanel arrangementCombinedPanel;
	public static JPanel variationButtonsPanel;

	// arrangement subcells - copy dragging
	public static boolean copyDragging = false;
	public static Triple<Integer, Integer, Integer> highlightedTableCell = null;
	public static Triple<Integer, Integer, Integer> copyDraggingOrigin = null;
	public static Point arrangementActualTableMousePoint = null;
	UsedPattern copyDraggedPattern = null;

	// instrument global settings
	JTextField bannedInsts;
	JCheckBox useAllInsts;
	JButton reinitInstPools;

	// main title settings
	JLabel mainTitle;
	JLabel subTitle;

	// macro params
	ScrollComboBox<String> soundbankFilename;

	public static ScrollComboBox<String> scaleMode;
	JCheckBox randomizeScaleModeOnCompose;
	ScrollComboBox<String> chordProgressionLength;
	ScrollComboBox<Double> beatDurationMultiplier;
	JCheckBox allowChordRepeats;
	JCheckBox globalSwingOverride;
	KnobPanel globalSwingOverrideValue;
	JButton globalSwingOverrideApplyButton;
	public static KnobPanel loopBeatCount;
	public static JLabel pauseBehaviorLabel;
	public static ScrollComboBox<String> pauseBehaviorCombobox;
	public static JCheckBox startFromBar;
	public static JCheckBox rememberLastPos;
	public static JCheckBox snapStartToBeat;
	public static JCheckBox moveStartToCustomizedSection;
	JCheckBox bottomUpReverseDrumPanels;
	JCheckBox orderedTransposeGeneration;
	JCheckBox configHistoryStoreRegeneratedTracks;


	// add/skip instruments
	SettingsPanel chordSettingsPanel;
	SettingsPanel arpSettingsPanel;
	SettingsPanel drumSettingsPanel;
	static JCheckBox[] addInst = new JCheckBox[5];
	VeloRect drumVolumeSlider;

	JButton soloAllDrums;

	// all gen settings
	JButton[] addPanelButtons = new JButton[5];
	JButton[] generatePanelButtons = new JButton[5];
	JTextField[] randomPanelsToGenerate = new JTextField[5];

	// melody gen settings
	JCheckBox generateMelodiesOnCompose;
	KnobPanel melodyUseOldAlgoChance;
	JCheckBox randomMelodyOnRegenerate;
	JCheckBox randomMelodySameSeed;
	JCheckBox melodyFirstNoteFromChord;
	JCheckBox randomChordNote;

	JCheckBox melodyBasicChordsOnly;
	KnobPanel melodyChordNoteTarget;
	KnobPanel melodyTonicNoteTarget;
	JCheckBox melodyEmphasizeKey;
	KnobPanel melodyModeNoteTarget;

	JCheckBox useUserMelody;
	public MelodyMidiDropPane dropPane;
	public static ScrollComboBox<String> userMelodyScaleModeSelect;

	JCheckBox melody1ForcePatterns;
	JCheckBox melodyArpySurprises;
	JCheckBox melodySingleNoteExceptions;
	JCheckBox melodyFillPausesPerChord;
	KnobPanel melodyNewBlocksChance;
	JCheckBox melodyLegacyMode;

	JCheckBox melodyAvoidChordJumpsLegacy;
	JCheckBox melodyUseDirectionsFromProgression;
	JCheckBox melodyPatternFlip;
	public static JCheckBox patternApplyPausesWhenGenerating;
	public static ScrollComboBox<String> melodyBlockTargetMode;
	JCheckBox melodyTargetNotesRandomizeOnCompose;
	ScrollComboBox<String> melodyPatternEffect;
	ScrollComboBox<String> melodyRhythmAccents;
	ScrollComboBox<String> melodyRhythmAccentsMode;
	JCheckBox melodyRhythmAccentsPocket;
	JCheckBox melodyPatternRandomizeOnCompose;
	KnobPanel melodyReplaceAvoidNotes;
	KnobPanel melodyMaxDirChanges;
	public static KnobPanel melodyTargetNoteVariation;
	public static RandomIntegerListButton melodyBlockChoicePreference;

	// bass gen settings
	// - there's nothing here - 

	// chord gen settings
	JCheckBox randomChordsGenerateOnCompose;
	JCheckBox randomChordDelay;
	JCheckBox randomChordStrum;
	KnobPanel randomChordStruminess;
	JCheckBox randomChordSplit;
	JCheckBox randomChordTranspose;
	JCheckBox randomChordPattern;
	JCheckBox randomChordVaryLength;
	KnobPanel randomChordExpandChance;
	KnobPanel randomChordSustainChance;
	KnobPanel randomChordShiftChance;
	KnobPanel randomChordVoicingChance;
	KnobPanel randomChordMaxSplitChance;
	JCheckBox randomChordUseChordFill;
	ScrollComboBox<String> randomChordStretchType;
	ScrollComboBox<Integer> randomChordStretchPicker;
	KnobPanel randomChordStretchGenerationChance;
	KnobPanel randomChordMaxStrumPauseChance;
	KnobPanel randomChordMinVel;
	KnobPanel randomChordMaxVel;

	// arp gen settings
	JCheckBox randomArpsGenerateOnCompose;
	JCheckBox randomArpTranspose;
	JCheckBox randomArpPattern;
	JCheckBox randomArpHitsPerPattern;
	JCheckBox randomArpAllSameInst;
	JCheckBox randomArpAllSameHits;
	JCheckBox randomArpLimitPowerOfTwo;
	KnobPanel randomArpShiftChance;
	ScrollComboBox<Integer> randomArpHitsPicker;
	JCheckBox randomArpUseChordFill;
	ScrollComboBox<String> randomArpStretchType;
	ScrollComboBox<Integer> randomArpStretchPicker;
	KnobPanel randomArpStretchGenerationChance;
	KnobPanel randomArpMaxExceptionChance;
	JCheckBox randomArpUseOctaveAdjustments;
	KnobPanel randomArpMaxRepeat;
	KnobPanel randomArpMinVel;
	KnobPanel randomArpMaxVel;
	KnobPanel randomArpMinLength;
	KnobPanel randomArpMaxLength;
	JCheckBox randomArpCorrectMelodyNotes;
	JCheckBox arpCopyMelodyInst;

	// drum gen settings
	public static List<Integer> PUNCHY_DRUMS = Arrays.asList(new Integer[] { 35, 36, 38, 39, 40 });
	public static List<Integer> KICK_DRUMS = Arrays.asList(new Integer[] { 35, 36 });
	public static List<Integer> SNARE_DRUMS = Arrays.asList(new Integer[] { 38, 40 });
	JCheckBox randomDrumsGenerateOnCompose;
	KnobPanel randomDrumsOverrandomize;
	KnobPanel randomDrumMaxSwingAdjust;
	JCheckBox randomDrumSlide;
	JCheckBox randomDrumPattern;
	KnobPanel randomDrumVelocityPatternChance;
	KnobPanel randomDrumShiftChance;
	JCheckBox randomDrumUseChordFill;
	JCheckBox arrangementScaleMidiVelocity;
	public static KnobPanel humanizeNotes;
	public static KnobPanel humanizeDrums;
	public static KnobPanel globalNoteLengthMultiplier;
	public static ScrollComboBox<Double> swingUnitMultiplier;
	public static JCheckBox customMidiForceScale;
	public static JCheckBox reuseMidiChannelAfterCopy;
	public static JCheckBox transposedNotesForceScale;
	public static JCheckBox transposeNotePreview;
	public static JCheckBox padGeneratedMidi;
	public static RandomIntegerListButton padGeneratedMidiValues;
	public static JCheckBox randomizeTimingsOnCompose;
	public static JCheckBox sidechainPatternsOnCompose;
	JCheckBox arrangementResetCustomPanelsOnCompose;
	ScrollComboBox<String> randomDrumHitsMultiplier;
	ScrollComboBox<String> randomDrumHitsMultiplierOnGenerate;
	public static JCheckBox drumCustomMapping;
	public static JTextField drumCustomMappingNumbers;


	// chord variety settings
	KnobPanel spiceChance;
	KnobPanel chordSlashChance;
	JCheckBox spiceAllowDimAug;
	JCheckBox spiceAllow9th13th;
	JCheckBox spiceFlattenBigChords;
	JCheckBox squishChordsProgressively;
	JCheckBox copyChordsAfterGenerate;
	KnobPanel spiceParallelChance;

	JCheckBox spiceForceScale;
	ScrollComboBox<String> firstChordSelection;
	ScrollComboBox<String> lastChordSelection;

	// chord settings - progression
	JCheckBox useChordFormula;
	public static KnobPanel longProgressionSimilarity;
	ScrollComboBox<String> keyChangeTypeSelection;
	public static CheckButton userChordsEnabled;
	public static CheckButton userDurationsEnabled;
	public static JTextField userChordsDurations;
	public static ChordletPanel userChords;

	// randomization button settings
	JCheckBox randomizeInstOnComposeOrGen;
	JCheckBox randomizeBpmOnCompose;
	JCheckBox randomizeTransposeOnCompose;
	JCheckBox randomizeChordStrumsOnCompose;
	JCheckBox arpAffectsBpm;
	public static KnobPanel mainBpm;
	public static KnobPanel bpmLow;
	public static KnobPanel bpmHigh;
	public static KnobPanel stretchMidi;
	public static KnobPanel transposeScore;
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

	public static JButton showScore;
	public static ShowScorePopup scorePopup;
	CheckButton midiMode;
	ScrollComboBox<String> midiModeDevices;
	//MidiHandler mh = new MidiHandler();
	JCheckBox combineDrumTracks;
	JCheckBox combineMelodyTracks;
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
	JCheckBox useMidiCC;
	CheckButton loopBeat;
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

	JLabel tipLabel;
	public static JLabel currentChords = new JLabel("Chords:[]");
	public static List<String> currentChordsInternal = new ArrayList<>();
	JLabel messageLabel;
	ScrollComboBox<String> presetLoadBox;
	ScrollComboBox<String> drumPartPresetBox;
	JCheckBox drumPartPresetAddCheckbox;
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

	public static DebugConsole dconsole = null;
	public static VibeComposerGUI vibeComposerGUI = null;

	private static GridBagConstraints constraints = new GridBagConstraints();

	public static JPanel extraSettingsPanel;
	public static JPanel currentSettingsMenuPanel = null;

	public static boolean isShowingTextInKnobs = true;
	public static JCheckBox displayVeloRectValues;
	public static JCheckBox knobControlByDragging;
	public static JCheckBox highlightPatterns;
	public static JCheckBox highlightScoreNotes;
	public static JCheckBox customFilenameAddTimestamp;
	public static JCheckBox miniScorePopup;

	public static final String CURRENT_VERSION = "2.5";

	public static void main(String args[]) {
		FlatDarculaLaf.install();
		UIManager.put("CheckBox.icon", new CheckBoxIcon());

		isDarkMode = true;
		vibeComposerGUI = new VibeComposerGUI("VibeComposer" + CURRENT_VERSION + " (BETA)");
		vibeComposerGUI.setMainIcon();
		vibeComposerGUI.init();
		//Toolkit.getDefaultToolkit().getSystemEventQueue().push(new TimedEventQueue());
		vibeComposerGUI.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
	}

	static {
		System.setErr(VibeComposerGUI.dummyOut);
	}

	public VibeComposerGUI(String title) {
		super(title);
	}

	private void setMainIcon() {
		this.setIconImage(new ImageIcon(new ImageIcon(
				this.getClass().getResource("/VibeComposer2_LOGO_INVERT_NARROW.jpg"))
				.getImage().getScaledInstance(48, 48, java.awt.Image.SCALE_SMOOTH))
				.getImage());
	}

	private void init() {
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

		initExtraSettings();

		// randomization buttons
		initRandomButtons(350, GridBagConstraints.CENTER);

		//createHorizontalSeparator(15, this);

		initSoloMutersAndTrackControl(20, GridBagConstraints.WEST);
		LG.i("Titles, Extra, S/M " + (System.currentTimeMillis() - sysTime) + " ms!");
		// ---- INSTRUMENT SETTINGS ----
		{
			// melody


			// chords
			initChordGenSettings(40, GridBagConstraints.WEST);

			//createHorizontalSeparator(100, this);

			// arps
			initArpGenSettings(105, GridBagConstraints.WEST);

			//createHorizontalSeparator(150, this);


			// drums
			initDrumGenSettings(190, GridBagConstraints.WEST);

			initMelodyGenSettings(220, GridBagConstraints.WEST);

			//createHorizontalSeparator(240, this);

		}
		LG.i("Gen settings: " + (System.currentTimeMillis() - sysTime) + " ms!");
		boolean randomizeInstsTemp = randomizeInstOnComposeOrGen.isSelected();
		randomizeInstOnComposeOrGen.setSelected(true);
		{
			// ---- INSTRUMENT PANELS ----

			initMelody(300, GridBagConstraints.WEST);

			//createHorizontalSeparator(30, this);

			// bass
			initBass(310, GridBagConstraints.WEST);
			//createHorizontalSeparator(35, this);

			initChords(311, GridBagConstraints.WEST);
			initArps(312, GridBagConstraints.WEST);
			initDrums(313, GridBagConstraints.WEST);
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
						actualArrangement.getSections().forEach(s -> s.initPartMapFromOldData());
						scrollableArrangementActualTable.repaint();
					}
				}
			});
			everythingPanel.add(instrumentTabPane, constraints);
			for (int i = 0; i < 5; i++) {
				instrumentTabPane.setBackgroundAt(i, OMNI.alphen(instColors[i], 40));
			}

			// arrangement
			initArrangementSettings(325, GridBagConstraints.CENTER);


		}
		randomizeInstOnComposeOrGen.setSelected(randomizeInstsTemp);
		LG.i("Arr: " + (System.currentTimeMillis() - sysTime) + " ms!");
		initScoreSettings(330, GridBagConstraints.CENTER);
		LG.i("Scr: " + (System.currentTimeMillis() - sysTime) + " ms!");
		//createHorizontalSeparator(327, this);

		// ---- OTHER SETTINGS ----
		{


			initMacroParams(360, GridBagConstraints.CENTER);

			// chord settings - variety/spice
			// chord settings - progressions
			initChordProgressionSettings(370, GridBagConstraints.CENTER);

			// chord tool tip

			everythingPanel.add(controlPanel, constraints);

			initCustomChords(380, GridBagConstraints.CENTER);

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
		// block compose/regenerate until UI fully loaded
		heavyBackgroundTasksInProgress = true;
		setVisible(true);

		repaint();
		//initScrollPaneListeners();
		UndoManager.recordingEvents = true;
		LG.i("VibeComposer started in: " + (System.currentTimeMillis() - sysTime)
				+ " ms! Creating Panels in background...");

		if (!presetLoaded) {
			generateInitialMelodyPanels();
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
		SwingUtils.setupScrollpanePriorityScrolling(arrangementScrollPane);
		SwingUtils.setupScrollpanePriorityScrolling(arrangementActualScrollPane);
	}

	protected void setAddInst(int partNum, boolean b) {
		addInst[partNum].setSelected(b);
		instrumentTabPane.setBackgroundAt(partNum,
				OMNI.alphen(b ? instColors[partNum] : Color.white, 40));
	}

	private void initKeyboardListener() {


		// switch pane using C/A/D (chords/arps/drums)

		KeyboardFocusManager.getCurrentKeyboardFocusManager()
				.addKeyEventDispatcher(new KeyEventDispatcher() {
					public boolean dispatchKeyEvent(KeyEvent e) {
						if (e.isControlDown() && vibeComposerGUI.isFocused()
								&& vibeComposerGUI.isActive()) {
							if (e.getID() == KeyEvent.KEY_RELEASED) {
								if (e.getKeyCode() == KeyEvent.VK_Z)
									UndoManager.undo();
								else if (e.getKeyCode() == KeyEvent.VK_Y)
									UndoManager.redo();
							}
						}
						return false;
					}
				});
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
		globalReverbSlider = new VeloRect(0, 127, 60);
		globalChorusSlider = new VeloRect(0, 127, 15);

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


		extraSettingsPanel = new JPanel();
		extraSettingsPanel.setLayout(new BorderLayout());

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
				openFolder(PRESET_FOLDER);
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
			generateInitialMelodyPanels();
			for (int i = 1; i < 5; i++) {
				generatePanels(i);
			}
			manualArrangement.setSelected(false);
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
		File folder = new File(PRESET_FOLDER);
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
		File loadedFile = new File(PRESET_FOLDER + "/default.xml");
		boolean exists = loadedFile.exists();
		if (exists) {
			SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();

			f.applyPattern("yyMMdd-HH-mm-ss");
			Date date = new Date();
			String fdate = f.format(date);

			File renamedFile = new File(PRESET_FOLDER + "/default-" + fdate + ".xml");
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
			File loadedFile = new File(PRESET_FOLDER + "/" + presetName + ".xml");
			if (loadedFile.exists()) {
				try {
					GUIPreset preset = unmarshallPreset(loadedFile);
					loadPresetObject(preset);
				} catch (JAXBException | IOException e) {
					e.printStackTrace();
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
		if (!presetName.matches(FILENAME_VALID_NAME)) {
			new TemporaryInfoPopup("Name contains invalid characters: "
					+ presetName.replaceAll(FILENAME_VALID_CHARACTERS, ""), 2500);
			return;
		}
		File makeSavedDir = new File(PRESET_FOLDER);
		makeSavedDir.mkdir();

		String filePath = PRESET_FOLDER + "/" + presetName + ".xml";
		saveGuiPresetFileByFilePath(filePath);
		presetLoadBox.addItem(presetName);
		new TemporaryInfoPopup("Saved preset: " + presetName, 2000);
	}

	private void initExtraSettings() {

		JPanel composeSettingsPanel = new JPanel();
		JPanel scoreMidiPanel = new JPanel();
		JPanel panelGenerationSettingsPanel = new JPanel();
		JPanel chordChoicePanel = new JPanel();
		JPanel humanizationPanel = new JPanel();
		JPanel instrumentsSettingsPanel = new JPanel();
		JPanel pauseBehaviorPanel = new JPanel();
		JPanel bpmLowHighPanel = new JPanel();
		JPanel displayStylePanel = new JPanel();

		HashMap<String, JPanel> settingsMenuItems = new LinkedHashMap<>();
		settingsMenuItems.put("COMPOSE", composeSettingsPanel);
		settingsMenuItems.put("Score/Midi", scoreMidiPanel);
		settingsMenuItems.put("Generation", panelGenerationSettingsPanel);
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
			JButton butt = makeButton(buttonName, e -> {
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

		// COMPOSE

		arrangementResetCustomPanelsOnCompose = makeCheckBox("Reset Customized Panels on Compose",
				true, true);
		randomizeTimingsOnCompose = makeCheckBox(
				"<html>Randomize Global Swing/Beat Multiplier<br>on Compose</html>", true, true);
		sidechainPatternsOnCompose = makeCheckBox("<html>Sidechain Patterns<br>on Compose</html>",
				true, true);
		copyChordsAfterGenerate = makeCheckBox("<html>Copy Chords<br>on Compose/Reg.</html>", true,
				true);

		composeSettingsPanel.add(arrangementResetCustomPanelsOnCompose);
		composeSettingsPanel.add(randomizeTimingsOnCompose);
		composeSettingsPanel.add(sidechainPatternsOnCompose);
		composeSettingsPanel.add(copyChordsAfterGenerate);


		// HUMANIZATION
		humanizeNotes = new DetachedKnobPanel("Humanize Notes<br>/10000", 150, 0, 1000);
		humanizeDrums = new DetachedKnobPanel("Humanize Drums<br>/10000", 20, 0, 100);
		globalNoteLengthMultiplier = new DetachedKnobPanel("Note Length Multiplier<br>/1000", 950,
				250, 1000);

		JPanel swingMultiPanel = new JPanel();
		swingMultiPanel.setLayout(new GridLayout(0, 2, 10, 30));
		swingUnitMultiplier = new ScrollComboBox<Double>(false);
		ScrollComboBox.addAll(new Double[] { 0.5, 1.0, 2.0 }, swingUnitMultiplier);
		swingUnitMultiplier.setSelectedIndex(0);

		humanizationPanel.add(humanizeNotes);
		humanizationPanel.add(humanizeDrums);
		humanizationPanel.add(globalNoteLengthMultiplier);
		swingMultiPanel.add(new JLabel("Swing Period Multiplier"));
		swingMultiPanel.add(swingUnitMultiplier);
		humanizationPanel.add(swingMultiPanel);


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
		arrangementScaleMidiVelocity = new CustomCheckBox("Scale Midi Velocity in Arrangement",
				true);
		useMidiCC = new CustomCheckBox("Use Volume/Pan/Reverb/Chorus/Filter/.. MIDI CC", true);
		useMidiCC.setToolTipText("Volume - 7, Reverb - 91, Chorus - 93, Filter - 74");

		//                 drum mapping
		JPanel drumMappingPanel = new JPanel();
		drumMappingPanel.setLayout(new GridLayout(0, 2, 10, 30));
		drumCustomMapping = new CustomCheckBox("Custom Drum Mapping", true);
		drumCustomMapping.setToolTipText(
				"<html>" + StringUtils.join(InstUtils.DRUM_INST_NAMES_SEMI, "|") + "</html>");
		drumCustomMappingNumbers = new JTextField(
				StringUtils.join(InstUtils.DRUM_INST_NUMBERS_SEMI, ","));
		drumMappingPanel.add(drumCustomMapping);
		drumMappingPanel.add(drumCustomMappingNumbers);

		scoreMidiPanel.add(padMidiPanel);
		scoreMidiPanel.add(drumMappingPanel);
		scoreMidiPanel.add(useMidiCC);
		scoreMidiPanel.add(stretchMidi);
		scoreMidiPanel.add(arrangementScaleMidiVelocity);

		// INSTRUMENTS
		JPanel allInstsPanel = new JPanel();

		allInstsPanel.setLayout(new GridLayout(0, 3, 10, 30));
		useAllInsts = new CustomCheckBox("(Experimental) Use all Inst., except:", false);
		useAllInsts.setHorizontalTextPosition(SwingConstants.RIGHT);
		allInstsPanel.add(useAllInsts);
		bannedInsts = new JTextField("", 8);
		allInstsPanel.add(bannedInsts);
		reinitInstPools = makeButton("Initialize All Inst.", "InitAllInsts");
		allInstsPanel.add(reinitInstPools);


		transposeNotePreview = new CustomCheckBox("Transpose Note Previews in MIDI Editor", true);

		// 				soundbank
		soundbankFilename = new ScrollComboBox<String>(false);
		soundbankFilename.setEditable(true);
		soundbankFilename.addItem(OMNI.EMPTYCOMBO);
		File folder = new File(SOUNDBANK_FOLDER);
		if (folder.exists()) {
			File[] listOfFiles = folder.listFiles();
			for (File f : listOfFiles) {
				if (f.isFile()) {
					String fileName = f.getName();
					if (fileName.endsWith(".sf2")) {
						soundbankFilename.addItem(fileName);
					}
				}
			}
		}
		soundbankFilename.setVal(soundbankFilename.getLastVal());
		soundbankFilename.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				needSoundbankRefresh = true;
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


		// PAUSE
		JPanel startFromPausePanel = new JPanel();
		drumMappingPanel.setLayout(new GridLayout(0, 2, 10, 30));
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
				if (slider.getSnapToTicks() != snapStartToBeat.isSelected()) {
					slider.setSnapToTicks(snapStartToBeat.isSelected());
				}
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

		// BPM
		arpAffectsBpm = new CustomCheckBox("BPM slowed by ARP", false);
		bpmLow = new DetachedKnobPanel("Min<br>BPM.", 60, 20, 249);
		bpmHigh = new DetachedKnobPanel("Max<br>BPM.", 100, 21, 250);
		bpmLowHighPanel.add(bpmLow);
		bpmLowHighPanel.add(bpmHigh);
		bpmLowHighPanel.add(arpAffectsBpm);

		// DISPLAY
		displayVeloRectValues = new CustomCheckBox("Display Bar Values", true);
		knobControlByDragging = new CustomCheckBox("Knob Up-Down Control", false);
		highlightPatterns = new CustomCheckBox("Highlight Sequencer Pattern (-Perf)", true);
		highlightScoreNotes = new CustomCheckBox("Highlight Score Notes (-Perf)", true);
		customFilenameAddTimestamp = new CustomCheckBox("Add Timestamp To Custom Filenames", false);
		miniScorePopup = new CustomCheckBox("Mini Score Popup", true);
		displayVeloRectValues.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				VibeComposerGUI.this.repaint();
			}
		});

		JCheckBox checkbutt = new CustomCheckBox("Show Knob Texts", isShowingTextInKnobs);
		checkbutt.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				isShowingTextInKnobs = !isShowingTextInKnobs;
				for (int i = 0; i < 5; i++) {
					getInstList(i)
							.forEach(ipanel -> ipanel.toggleComponentTexts(isShowingTextInKnobs));
					if (arrSection.getSelectedIndex() > 0) {
						getAffectedPanels(i).forEach(
								ipanel -> ipanel.toggleComponentTexts(isShowingTextInKnobs));
					}

				}
			}

		});


		bottomUpReverseDrumPanels = new CustomCheckBox("Bottom-Top Drum Display", false);
		bottomUpReverseDrumPanels.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent e) {
				for (DrumPanel dp : drumPanels) {
					dp.setVisible(false);
					((JPanel) getInstPane(4).getViewport().getView()).remove(dp);

				}
				List<DrumPanel> sortedDps = new ArrayList<>(drumPanels);
				Collections.sort(sortedDps, Comparator.comparing(e1 -> e1.getPanelOrder()));
				for (DrumPanel dp : sortedDps) {
					if (!bottomUpReverseDrumPanels.isSelected()) {
						((JPanel) getInstPane(4).getViewport().getView()).add(dp);
					} else {
						((JPanel) getInstPane(4).getViewport().getView()).add(dp, 0);
					}
					dp.setVisible(true);
				}
			}
		});

		displayStylePanel.add(bottomUpReverseDrumPanels);
		displayStylePanel.add(checkbutt);
		displayStylePanel.add(displayVeloRectValues);
		displayStylePanel.add(knobControlByDragging);
		displayStylePanel.add(highlightPatterns);
		displayStylePanel.add(highlightScoreNotes);
		displayStylePanel.add(customFilenameAddTimestamp);
		displayStylePanel.add(miniScorePopup);

		// GENERATION

		//          scale
		customMidiForceScale = new CustomCheckBox("Force MIDI Melody Notes To Scale", false);
		reuseMidiChannelAfterCopy = new CustomCheckBox("Reuse MIDI Ch. After Copy (Cc)", true);
		transposedNotesForceScale = new CustomCheckBox("Force Transposed Notes To Scale", false);

		orderedTransposeGeneration = new CustomCheckBox("Ordered Transpose Generation", false);
		configHistoryStoreRegeneratedTracks = new CustomCheckBox(
				"Track History - Include Regenerated Tracks", true);
		JPanel melodyBlockChoicePreferencePanel = new JPanel();
		melodyBlockChoicePreferencePanel.setLayout(new GridLayout(0, 2, 10, 30));
		melodyBlockChoicePreferencePanel.add(new JLabel("<html>Melody Block Choice<br>Preferred Order</html>"));
		melodyBlockChoicePreference = new RandomIntegerListButton("0", null);
		melodyBlockChoicePreference.setValues(MelodyUtils.BLOCK_CHANGE_JUMP_PREFERENCE);
		melodyBlockChoicePreference.min = 0;
		melodyBlockChoicePreference.max = 7;
		melodyBlockChoicePreference.editableCount = false;
		melodyBlockChoicePreference.setRandGenerator(e -> {
			List<Integer> scrambledDefaultPreference = new ArrayList<>(MelodyUtils.BLOCK_CHANGE_JUMP_PREFERENCE);
			Collections.shuffle(scrambledDefaultPreference, new Random());
			return scrambledDefaultPreference;
		});
		melodyBlockChoicePreference.setTextGenerator(e -> StringUtils.join(melodyBlockChoicePreference.getRandGenerator()
				.apply(new Object()),","));
		melodyBlockChoicePreference.setPostFunc(e -> {
			// verify it's set correctly
			List<Integer> vals = melodyBlockChoicePreference.getValues();
			if (vals.size() > 1 && (vals.size() != MelodyUtils.BLOCK_CHANGE_JUMP_PREFERENCE.size() ||
				!vals.containsAll(MelodyUtils.BLOCK_CHANGE_JUMP_PREFERENCE))) {
				melodyBlockChoicePreference.setValues(MelodyUtils.BLOCK_CHANGE_JUMP_PREFERENCE, false);
				return;
			}
		});
		melodyBlockChoicePreferencePanel.add(melodyBlockChoicePreference);
		melodyPatternFlip = new CustomCheckBox("Inverse Melody1 Pattern", false);
		patternApplyPausesWhenGenerating = new CustomCheckBox("Apply Pause% on Generate", true);


		JPanel keyChangePanel = new JPanel();
		keyChangePanel.setLayout(new GridLayout(0, 2, 10, 30));
		keyChangeTypeSelection = new ScrollComboBox<String>(false);
		ScrollComboBox.addAll(new String[] { "PIVOT", "TWOFIVEONE", "DIRECT" },
				keyChangeTypeSelection);
		keyChangeTypeSelection.setVal("TWOFIVEONE");
		keyChangeTypeSelection.setPreferredSize(new Dimension(250, 30));
		keyChangeTypeSelection.addItemListener(this);
		keyChangePanel.add(new JLabel("<html>Key Change<br>Type:</html>"));
		keyChangePanel.add(keyChangeTypeSelection);

		panelGenerationSettingsPanel.add(customMidiForceScale);
		panelGenerationSettingsPanel.add(transposedNotesForceScale);
		panelGenerationSettingsPanel.add(reuseMidiChannelAfterCopy);
		panelGenerationSettingsPanel.add(orderedTransposeGeneration);
		panelGenerationSettingsPanel.add(configHistoryStoreRegeneratedTracks);
		panelGenerationSettingsPanel.add(melodyBlockChoicePreferencePanel);
		//panelGenerationSettingsPanel.add(melodyPatternFlip); -- pattern flip is now also available per-instrument..
		panelGenerationSettingsPanel.add(patternApplyPausesWhenGenerating);
		panelGenerationSettingsPanel.add(keyChangePanel);

		initHelperPopups();
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
		manualArrangement.setSelected(true);
		for (int i = 0; i < 5; i++) {
			createPanels(i, getInstList(i).size(), false);
			applyCustomPanelsToSection("", i, arrSection.getSelectedIndex());
		}
		arrSection.getCurrentButton().repaint();
		recalculateTabPaneCounts();
		recalculateSoloMuters();
		if (sequencer != null && regenerateWhenValuesChange.isSelected()) {
			stopMidi();
			regenerate();
		}
	}

	private void replaceSection() {
		if (configHistory.getItemCount() > 0 && arrSection.getSelectedIndex() > 0) {
			GUIConfig sectionGuiConfig = configHistory.getVal();
			Section currentSec = actualArrangement.getSections()
					.get(arrSection.getSelectedIndex() - 1);
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
			arrSection.getCurrentButton().repaint();
			switchPanelsForSectionSelection(arrSection.getVal());

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

	private void initMelodyGenSettings(int startY, int anchorSide) {

		JPanel scrollableMelodyPanels = new JPanel();
		scrollableMelodyPanels.setLayout(new BoxLayout(scrollableMelodyPanels, BoxLayout.Y_AXIS));
		scrollableMelodyPanels.setAutoscrolls(true);

		melodyScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(scrollPaneDimension.width, scrollPaneDimension.height - 100);
			}
		};
		melodyScrollPane.setViewportView(scrollableMelodyPanels);
		melodyScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		melodyScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		//melodySettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		melodyUseOldAlgoChance = new KnobPanel("Legacy<br>Algo", 0);

		randomChordNote = new CustomCheckBox();
		randomChordNote.setSelected(true);
		melodyFirstNoteFromChord = new CustomCheckBox();
		melodyFirstNoteFromChord.setSelected(true);


		//melodySettingsPanel.add(new JLabel("Note#1 From Chord:"));
		//melodySettingsPanel.add(melodyFirstNoteFromChord);
		//melodySettingsPanel.add(new JLabel("But Randomized:"));
		//melodySettingsPanel.add(randomChordNote);
		//melodySettingsExtraPanelShape.add(melodyAvoidChordJumps);

		JPanel melodySettingsExtraPanelOrg = initMelodySettings();
		JPanel melodySettingsExtraPanelShape = initMelodySettingsPlus();
		JPanel melodySettingsExtraPanelBlocksPatternsCompose = initMelodySettingsPlusPlus();


		//scrollableMelodyPanels.add(melodySettingsPanel);
		melodyParentPanel = new JPanel() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};
		melodyParentPanel.setLayout(new BoxLayout(melodyParentPanel, BoxLayout.Y_AXIS));
		JPanel borderPanel = new JPanel() {
			@Override
			public Dimension getMaximumSize() {
				return new Dimension(scrollPaneDimension.width, 150);
			}
		};
		borderPanel.setLayout(new DynamicGridLayout(0, 1));
		borderPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
		borderPanel.add(melodySettingsExtraPanelOrg);
		borderPanel.add(melodySettingsExtraPanelShape);
		borderPanel.add(melodySettingsExtraPanelBlocksPatternsCompose);
		melodyParentPanel.add(borderPanel);
		melodyParentPanel.add(melodyScrollPane);
		//addHorizontalSeparatorToPanel(scrollableMelodyPanels);

		toggleableComponents.add(melodySettingsExtraPanelShape);
		toggleableComponents.add(melodySettingsExtraPanelBlocksPatternsCompose);
	}

	private JPanel initMelodySettings() {
		JPanel melodySettingsExtraPanelOrg = new JPanel();
		melodySettingsExtraPanelOrg.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		melodySettingsExtraPanelOrg.setAlignmentX(Component.LEFT_ALIGNMENT);
		melodySettingsExtraPanelOrg.setMaximumSize(new Dimension(1800, 50));

		addInst[0] = new CustomCheckBox("MELODY", true);
		melodySettingsExtraPanelOrg.add(addInst[0]);
		groupFilterSliders[0] = new VeloRect(0, 127, 127);
		JLabel filterLabel = new JLabel("LP");
		melodySettingsExtraPanelOrg.add(filterLabel);
		melodySettingsExtraPanelOrg.add(groupFilterSliders[0]);

		addPanelButtons[0] = makeButton("+Melody", e -> {
			addPanel(0);
		});
		generatePanelButtons[0] = makeButton("Generate Melodies:", e -> {
			generatePanels(0, true);
		});
		randomPanelsToGenerate[0] = new JTextField("3", 2);
		melodySettingsExtraPanelOrg.add(addPanelButtons[0]);
		melodySettingsExtraPanelOrg.add(generatePanelButtons[0]);
		melodySettingsExtraPanelOrg.add(randomPanelsToGenerate[0]);
		generateMelodiesOnCompose = makeCheckBox("On Compose", false, true);
		melodySettingsExtraPanelOrg.add(generateMelodiesOnCompose);

		JButton generateUserMelodySeed = makeButton("Randomize Seed", e -> {
			randomizeMelodySeeds();
			if (canRegenerateOnChange()) {
				regenerate();
			}
		});
		JButton clearUserMelodySeed = makeButton("Clear Seed",
				e -> getAffectedPanels(0).forEach(m -> m.setPatternSeed(0)));
		randomMelodySameSeed = new CustomCheckBox("Same#", false);
		randomMelodyOnRegenerate = makeCheckBox("on Manual Regen.", false, true);
		melody1ForcePatterns = new CustomCheckBox("<html>Force Melody#1<br> Outline</html>", true);

		dropPane = new MelodyMidiDropPane();
		useUserMelody = new CustomCheckBox("<html>Use MIDI<br>Melody File</html>", true);
		userMelodyScaleModeSelect = new ScrollComboBox<>(false);
		userMelodyScaleModeSelect.addItem(OMNI.EMPTYCOMBO);
		userMelodyScaleModeSelect.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					if (userMelodyScaleModeSelect.getSelectedIndex() > 0) {
						if (MelodyMidiDropPane.userMelodyCandidate != null) {
							Phrase melody = MelodyMidiDropPane.userMelodyCandidate.copy();
							String item = userMelodyScaleModeSelect
									.getItemAt(userMelodyScaleModeSelect.getSelectedIndex());
							String[] itemSplit = item.split(",");
							int transposeUpBy = Integer.valueOf(itemSplit[1]);
							ScaleMode toMode = ScaleMode.valueOf(itemSplit[0]);
							Mod.transpose(melody, transposeUpBy);
							MidiUtils.transposePhrase(melody, toMode.noteAdjustScale,
									ScaleMode.IONIAN.noteAdjustScale,
									transposedNotesForceScale.isSelected());
							VibeComposerGUI.transposeScore.setInt(transposeUpBy * -1);
							VibeComposerGUI.scaleMode.setVal(toMode.toString());
							MelodyMidiDropPane.userMelody = melody;
						}
						userMelodyScaleModeSelect.setSelectedIndex(0);
					}
				}
			}

		});

		combineMelodyTracks = new CustomCheckBox("<html>Combine<br>MIDI Tracks</html>", false);

		melodySettingsExtraPanelOrg.add(melody1ForcePatterns);
		melodySettingsExtraPanelOrg.add(combineMelodyTracks);
		melodySettingsExtraPanelOrg.add(generateUserMelodySeed);
		melodySettingsExtraPanelOrg.add(randomMelodySameSeed);
		melodySettingsExtraPanelOrg.add(randomMelodyOnRegenerate);
		melodySettingsExtraPanelOrg.add(clearUserMelodySeed);
		melodySettingsExtraPanelOrg.add(useUserMelody);
		melodySettingsExtraPanelOrg.add(dropPane);
		melodySettingsExtraPanelOrg.add(new JLabel("in Mode:"));
		melodySettingsExtraPanelOrg.add(userMelodyScaleModeSelect);
		return melodySettingsExtraPanelOrg;
	}

	private JPanel initMelodySettingsPlus() {
		JPanel melodySettingsExtraPanelShape = new JPanel();
		melodySettingsExtraPanelShape.setAlignmentX(Component.LEFT_ALIGNMENT);
		melodySettingsExtraPanelShape.setMaximumSize(new Dimension(1800, 50));
		JLabel melodyExtraLabel2 = new JLabel("MELODY SETTINGS+");
		melodyExtraLabel2.setPreferredSize(new Dimension(120, 30));
		melodyExtraLabel2.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		melodySettingsExtraPanelShape.add(melodyExtraLabel2);

		melodyBasicChordsOnly = new CustomCheckBox("<html>Base<br> Chords</html>", false);
		melodyChordNoteTarget = new KnobPanel("Chord Note<br> Target%", 40);
		melodyTonicNoteTarget = new KnobPanel("Tonic Note<br> Target%", 20);
		melodyEmphasizeKey = new CustomCheckBox("<html>Emphasize<br> Key</html>", true);
		melodyModeNoteTarget = new KnobPanel("Mode Note<br> Target%", 15);
		melodyArpySurprises = new CustomCheckBox("<html>Insert<br> Arps</html>", false);
		melodySingleNoteExceptions = new CustomCheckBox("<html>Single Note<br>Exceptions</html>",
				true);
		melodyFillPausesPerChord = new CustomCheckBox("<html>Fill Pauses<br>Per Chord</html>",
				true);
		melodyNewBlocksChance = new KnobPanel("New<br>Blocks%", 25);
		melodyLegacyMode = new CustomCheckBox("<html>LEGACY<br>MODE</html>", false);
		melodyAvoidChordJumpsLegacy = new CustomCheckBox("<html>Avoid<br>Chord Jumps</html>", true);

		melodyReplaceAvoidNotes = new KnobPanel("Replace Near<br>Chord Notes", 1, 0, 2);
		melodyMaxDirChanges = new KnobPanel("Max. Dir.<br>Changes", 2, 1, 6);
		melodyTargetNoteVariation = new KnobPanel("Target Note<br>Variation", 3, 1, 6);

		melodySettingsExtraPanelShape.add(melodyBasicChordsOnly);
		melodySettingsExtraPanelShape.add(melodyChordNoteTarget);
		melodySettingsExtraPanelShape.add(melodyTonicNoteTarget);
		melodySettingsExtraPanelShape.add(melodyEmphasizeKey);
		melodySettingsExtraPanelShape.add(melodyModeNoteTarget);
		melodySettingsExtraPanelShape.add(melodyReplaceAvoidNotes);
		melodySettingsExtraPanelShape.add(melodyMaxDirChanges);
		melodySettingsExtraPanelShape.add(melodyTargetNoteVariation);
		melodySettingsExtraPanelShape.add(melodyArpySurprises);
		melodySettingsExtraPanelShape.add(melodySingleNoteExceptions);
		melodySettingsExtraPanelShape.add(melodyFillPausesPerChord);
		melodySettingsExtraPanelShape.add(melodyNewBlocksChance);
		melodySettingsExtraPanelShape.add(melodyLegacyMode);
		return melodySettingsExtraPanelShape;
	}

	private JPanel initMelodySettingsPlusPlus() {
		melodyUseDirectionsFromProgression = new CustomCheckBox(
				"<html>Use Chord<br>Directions</html>", false);
		melodyBlockTargetMode = new ScrollComboBox<>();
		ScrollComboBox.addAll(
				new String[] { "#. Chord Note", "Chord Root + #", "MIDI 60 (C4) + #" },
				melodyBlockTargetMode);
		melodyBlockTargetMode.setSelectedIndex(2);
		melodyTargetNotesRandomizeOnCompose = makeCheckBox(
				"<html>Randomize Targets<br> on Compose</html>", true, true);
		melodyPatternEffect = new ScrollComboBox<>();
		ScrollComboBox.addAll(new String[] { "Rhythm", "Notes", "Rhythm+Notes" },
				melodyPatternEffect);
		melodyPatternEffect.setSelectedIndex(2);
		melodyPatternRandomizeOnCompose = makeCheckBox(
				"<html>Randomize Pattern<br> on Compose</html>", true, true);
		melodyRhythmAccents = new ScrollComboBox<>();
		ScrollComboBox.addAll(new String[] { "None", "Snares", "Kicks", "Rides,OpenHH",
				"Snares,Kicks", "Snares,Rides,OpenHH" }, melodyRhythmAccents);
		melodyRhythmAccentsMode = new ScrollComboBox<>();
		ScrollComboBox.addAll(
				new String[] { "Mute", "Pitch+", "Pitch-", "Pitch~", "Vol+", "Vol-", "---" },
				melodyRhythmAccentsMode);
		melodyRhythmAccentsPocket = new CustomCheckBox("Pocket", false);

		JPanel melodySettingsExtraPanelBlocksPatternsCompose = new JPanel();
		melodySettingsExtraPanelBlocksPatternsCompose.setAlignmentX(Component.LEFT_ALIGNMENT);
		melodySettingsExtraPanelBlocksPatternsCompose.setMaximumSize(new Dimension(1800, 50));
		JLabel melodyExtraLabel3 = new JLabel("MELODY SETTINGS++");
		melodyExtraLabel3.setPreferredSize(new Dimension(120, 30));
		melodyExtraLabel3.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		melodySettingsExtraPanelBlocksPatternsCompose.add(melodyExtraLabel3);


		melodySettingsExtraPanelBlocksPatternsCompose.add(melodyUseDirectionsFromProgression);
		melodySettingsExtraPanelBlocksPatternsCompose
				.add(new JLabel("<html>Note Target<br>Mode</html>"));
		melodySettingsExtraPanelBlocksPatternsCompose.add(melodyBlockTargetMode);
		melodySettingsExtraPanelBlocksPatternsCompose.add(melodyTargetNotesRandomizeOnCompose);
		melodySettingsExtraPanelBlocksPatternsCompose.add(new JLabel("Pattern Effect"));
		melodySettingsExtraPanelBlocksPatternsCompose.add(melodyPatternEffect);
		melodySettingsExtraPanelBlocksPatternsCompose.add(melodyPatternRandomizeOnCompose);
		JPanel postProcessPanel = new JPanel();
		postProcessPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		postProcessPanel.add(new JLabel("<html>Drum Rhythm Accents<br>(Post-process)</html>"));
		postProcessPanel.add(melodyRhythmAccents);
		postProcessPanel.add(new JLabel("Mode"));
		postProcessPanel.add(melodyRhythmAccentsMode);
		postProcessPanel.add(melodyRhythmAccentsPocket);
		melodySettingsExtraPanelBlocksPatternsCompose.add(postProcessPanel);
		return melodySettingsExtraPanelBlocksPatternsCompose;
	}

	public static JCheckBox makeCheckBox(String string, boolean b, boolean thick) {
		JCheckBox cb = new CustomCheckBox(string, b);
		if (thick) {
			Font fnt = cb.getFont();
			fnt = fnt.deriveFont(Font.BOLD);
			cb.setFont(fnt);
		}
		return cb;
	}

	/*public void fixCombinedMelodyTracks() {
		if (combineMelodyTracks == null) {
			return;
		}
		boolean foundValid = false;
		int start = currentMidi == null ? 1 : 0;
		for (int i = start; i < melodyPanels.size(); i++) {
			if (combineMelodyTracks.isSelected()) {
				boolean isValid = melodyPanels.get(i).getSequenceTrack() >= 0;
				if (!foundValid && isValid) {
					foundValid = true;
					melodyPanels.get(i).toggleCombinedMelodyDisabledUI(true);
				} else {
					melodyPanels.get(i)
							.toggleCombinedMelodyDisabledUI(!combineMelodyTracks.isSelected());
				}
			} else {
				melodyPanels.get(i)
						.toggleCombinedMelodyDisabledUI(!combineMelodyTracks.isSelected());
			}
		}
		if (!foundValid) {
			melodyPanels.get(0).toggleCombinedMelodyDisabledUI(true);
		}
	}*/

	private void initMelody(int startY, int anchorSide) {
		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Melody", melodyParentPanel);
	}

	private void generateInitialMelodyPanels() {
		for (int i = 0; i < 3; i++) {
			MelodyPanel melodyPanel = (MelodyPanel) addInstPanelToLayout(0);
			melodyPanel.setInstrument(73);
			melodyPanel.setPanelOrder(i + 1);
			if (i > 0) {
				melodyPanel.setAccents(50);
				melodyPanel.setFillPauses(true);
				melodyPanel.setSpeed(0);
				melodyPanel.setPauseChance(70);

				if (i > 1) {
					melodyPanel.setMuteInst(true);
				}
				melodyPanel.setVelocityMax(70);
				melodyPanel.setVelocityMin(40);
				melodyPanel.setMidiChannel(i + 6);
				if (i % 2 == 1) {
					melodyPanel.setTranspose(0);
				} else {
					melodyPanel.setTranspose(-12);
				}
				melodyPanel.setPanByOrder(3);
				melodyPanel.getVolSlider().setValue(45);
			} else {
				melodyPanel.setAccents(75);
				melodyPanel.setFillPauses(false);
				melodyPanel.setSpeed(15);
				melodyPanel.setPauseChance(15);
				melodyPanel.setTranspose(12);
				melodyPanel.setVelocityMax(100);
				melodyPanel.setVelocityMin(50);
				melodyPanel.getVolSlider().setValue(60);
				melodyPanel.setNoteLengthMultiplier(108);
			}
		}
	}


	private void initBass(int startY, int anchorSide) {

		JPanel scrollableBassPanels = new JPanel();
		scrollableBassPanels.setLayout(new BoxLayout(scrollableBassPanels, BoxLayout.Y_AXIS));
		scrollableBassPanels.setAutoscrolls(true);

		bassScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(scrollPaneDimension.width, scrollPaneDimension.height - 100);
			}
		};
		bassScrollPane.setViewportView(scrollableBassPanels);
		bassScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		bassScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		JPanel bassSettingsPanel = new JPanel();
		addInst[1] = new CustomCheckBox("BASS", true);
		bassSettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		bassSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		bassSettingsPanel.setMaximumSize(new Dimension(1800, 50));
		bassSettingsPanel.add(addInst[1]);

		groupFilterSliders[1] = new VeloRect(0, 127, 127);
		JLabel filterLabel = new JLabel("LP");
		bassSettingsPanel.add(filterLabel);
		bassSettingsPanel.add(groupFilterSliders[1]);

		addPanelButtons[1] = makeButton("+Bass", e -> {
			addPanel(1);
		});
		generatePanelButtons[1] = makeButton("Generate Basses:", e -> {
			generatePanels(1, true);
		});
		randomPanelsToGenerate[1] = new JTextField("1", 2);
		bassSettingsPanel.add(addPanelButtons[1]);
		bassSettingsPanel.add(generatePanelButtons[1]);
		bassSettingsPanel.add(randomPanelsToGenerate[1]);


		JPanel bassSettingsAdvancedPanel = new JPanel();
		bassSettingsAdvancedPanel.add(new JLabel("BASS SETTINGS+"));
		//bassSettingsAdvancedPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		bassSettingsAdvancedPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		bassSettingsAdvancedPanel.setMaximumSize(new Dimension(1800, 50));

		bassParentPanel = new JPanel() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};
		bassParentPanel.setLayout(new BoxLayout(bassParentPanel, BoxLayout.Y_AXIS));
		JPanel borderPanel = new JPanel() {
			@Override
			public Dimension getMaximumSize() {
				return new Dimension(scrollPaneDimension.width, 100);
			}
		};
		borderPanel.setLayout(new DynamicGridLayout(0, 1));
		borderPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
		borderPanel.add(bassSettingsPanel);
		borderPanel.add(bassSettingsPanel);
		bassParentPanel.add(borderPanel);
		bassParentPanel.add(bassScrollPane);

		bassSettingsAdvancedPanel.setVisible(false);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Bass", bassParentPanel);
	}

	private void initChordGenSettings(int startY, int anchorSide) {
		JPanel scrollableChordPanels = new JPanel();
		scrollableChordPanels.setLayout(new BoxLayout(scrollableChordPanels, BoxLayout.Y_AXIS));
		scrollableChordPanels.setAutoscrolls(true);

		chordScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(scrollPaneDimension.width, scrollPaneDimension.height - 100);
			}
		};
		chordScrollPane.setViewportView(scrollableChordPanels);
		chordScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		chordScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		JPanel chordSettingsPanel = new JPanel();
		chordSettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

		addInst[2] = new CustomCheckBox("CHORDS", true);
		chordSettingsPanel.add(addInst[2]);
		groupFilterSliders[2] = new VeloRect(0, 127, 127);
		JLabel filterLabel = new JLabel("LP");
		chordSettingsPanel.add(filterLabel);
		chordSettingsPanel.add(groupFilterSliders[2]);

		addPanelButtons[2] = makeButton("+Chord", e -> {
			addPanel(2);
		});
		generatePanelButtons[2] = makeButton("Generate Chords:", e -> {
			generatePanels(2, true);
		});
		randomPanelsToGenerate[2] = new JTextField("2", 2);
		chordSettingsPanel.add(addPanelButtons[2]);
		chordSettingsPanel.add(generatePanelButtons[2]);
		chordSettingsPanel.add(randomPanelsToGenerate[2]);

		randomChordsGenerateOnCompose = makeCheckBox("On Compose", true, true);
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

		JButton clearChordPatternSeeds = makeButton("Clear presets", "ClearChordPatterns");

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

		toggleableComponents.add(randomChordDelay);
		toggleableComponents.add(stretchLabel);
		toggleableComponents.add(randomChordStretchType);
		toggleableComponents.add(randomChordStretchPicker);
		toggleableComponents.add(randomChordSplit);

		toggleableComponents.add(chordSettingsExtraPanel);


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
				return scrollPaneDimension;
			}
		};
		chordParentPanel.setLayout(new BoxLayout(chordParentPanel, BoxLayout.Y_AXIS));

		JPanel borderPanel = new JPanel() {
			@Override
			public Dimension getMaximumSize() {
				return new Dimension(scrollPaneDimension.width, 100);
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

	private void initChords(int startY, int anchorSide) {
		// ---- CHORDS ----
		// gridy 50 - 99 range
		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Chords", chordParentPanel);
	}

	private void initArpGenSettings(int startY, int anchorSide) {
		JPanel scrollableArpPanels = new JPanel();
		scrollableArpPanels.setLayout(new BoxLayout(scrollableArpPanels, BoxLayout.Y_AXIS));
		scrollableArpPanels.setAutoscrolls(true);

		arpScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(scrollPaneDimension.width, scrollPaneDimension.height - 100);
			}
		};
		arpScrollPane.setViewportView(scrollableArpPanels);
		arpScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		arpScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		JPanel arpsSettingsPanel = new JPanel();
		arpsSettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		addInst[3] = new CustomCheckBox("ARPS", true);
		arpsSettingsPanel.add(addInst[3]);
		groupFilterSliders[3] = new VeloRect(0, 127, 127);
		JLabel filterLabel = new JLabel("LP");
		arpsSettingsPanel.add(filterLabel);
		arpsSettingsPanel.add(groupFilterSliders[3]);

		addPanelButtons[3] = makeButton("+Arp", e -> {
			addPanel(3);
		});
		generatePanelButtons[3] = makeButton("Generate Arps:", e -> {
			generatePanels(3, true);
		});
		randomPanelsToGenerate[3] = new JTextField("3", 2);
		arpsSettingsPanel.add(addPanelButtons[3]);
		arpsSettingsPanel.add(generatePanelButtons[3]);
		arpsSettingsPanel.add(randomPanelsToGenerate[3]);

		randomArpsGenerateOnCompose = makeCheckBox("on Compose", true, true);
		arpsSettingsPanel.add(randomArpsGenerateOnCompose);


		randomArpTranspose = new CustomCheckBox("Transpose", true);
		randomArpPattern = new CustomCheckBox("Patterns", true);
		randomArpHitsPicker = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new Integer[] { 1, 2, 3, 4, 5, 6, 7, 8 }, randomArpHitsPicker);
		randomArpHitsPicker.setVal(4);
		randomArpHitsPerPattern = new CustomCheckBox("Random#", true);
		randomArpAllSameInst = new CustomCheckBox("One Inst.", false);
		randomArpAllSameHits = new CustomCheckBox("One #", true);
		randomArpLimitPowerOfTwo = new CustomCheckBox("<html>Limit 2<sup>n</sup>", true);
		randomArpUseChordFill = new CustomCheckBox("Fills", true);
		randomArpShiftChance = new DetachedKnobPanel("Shift%", 50);
		randomArpUseOctaveAdjustments = new CustomCheckBox("Rand. Oct.", false);
		randomArpMaxRepeat = new DetachedKnobPanel("Max<br>Repeat", 2, 1, 4);
		randomArpMinVel = new DetachedKnobPanel("Min<br>Vel", 65, 0, 126);
		randomArpMaxVel = new DetachedKnobPanel("Max<br>Vel", 90, 1, 127);
		randomArpMinLength = new DetachedKnobPanel("Min<br>Length", 75, 25, 200);
		randomArpMaxLength = new DetachedKnobPanel("Max<br>Length", 100, 25, 200);
		randomArpCorrectMelodyNotes = new CustomCheckBox("<html>Correct Notes<br>by Melody</html>",
				false);

		arpsSettingsPanel.add(new JLabel("Arp#"));
		arpsSettingsPanel.add(randomArpHitsPicker);
		arpsSettingsPanel.add(randomArpHitsPerPattern);
		arpsSettingsPanel.add(randomArpAllSameHits);
		arpsSettingsPanel.add(randomArpUseChordFill);

		arpsSettingsPanel.add(randomArpTranspose);

		randomArpStretchType = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new String[] { "NONE", "FIXED", "AT_MOST" }, randomArpStretchType);
		randomArpStretchType.setVal("AT_MOST");
		JLabel stretchLabel = new JLabel("VOICES");
		arpsSettingsPanel.add(stretchLabel);
		arpsSettingsPanel.add(randomArpStretchType);
		randomArpStretchPicker = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new Integer[] { 3, 4, 5, 6 }, randomArpStretchPicker);
		randomArpStretchPicker.setVal(4);
		arpsSettingsPanel.add(randomArpStretchPicker);
		randomArpStretchGenerationChance = new DetachedKnobPanel("Chance", 50);
		arpsSettingsPanel.add(randomArpStretchGenerationChance);
		randomArpMaxExceptionChance = new DetachedKnobPanel("Max.<br>Split%", 20);
		arpsSettingsPanel.add(randomArpMaxExceptionChance);

		toggleableComponents.add(stretchLabel);
		toggleableComponents.add(randomArpStretchType);
		toggleableComponents.add(randomArpStretchPicker);

		JButton clearArpPatternSeeds = makeButton("Clear Patterns", "ClearArpPatterns");
		JPanel arpSettingsExtraPanel = new JPanel();
		JLabel csExtra = new JLabel("ARP SETTINGS+");
		csExtra.setPreferredSize(new Dimension(120, 30));
		csExtra.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		arpSettingsExtraPanel.add(csExtra);

		arpCopyMelodyInst = new CustomCheckBox("Arp#1 Copy Melody Inst.", true);

		arpSettingsExtraPanel.add(arpCopyMelodyInst);
		arpSettingsExtraPanel.add(randomArpAllSameInst);
		arpSettingsExtraPanel.add(randomArpLimitPowerOfTwo);
		arpSettingsExtraPanel.add(randomArpUseOctaveAdjustments);
		arpSettingsExtraPanel.add(randomArpMaxRepeat);
		arpSettingsExtraPanel.add(randomArpMinVel);
		arpSettingsExtraPanel.add(randomArpMaxVel);
		arpSettingsExtraPanel.add(randomArpPattern);
		arpSettingsExtraPanel.add(randomArpShiftChance);
		arpSettingsExtraPanel.add(randomArpMinLength);
		arpSettingsExtraPanel.add(randomArpMaxLength);
		arpSettingsExtraPanel.add(randomArpCorrectMelodyNotes);
		arpSettingsExtraPanel.add(clearArpPatternSeeds);
		toggleableComponents.add(arpSettingsExtraPanel);


		arpsSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		arpsSettingsPanel.setMaximumSize(new Dimension(1800, 50));
		//scrollableArpPanels.add(arpsSettingsPanel);
		arpSettingsExtraPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		arpSettingsExtraPanel.setMaximumSize(new Dimension(1800, 50));
		//constraints.gridy = startY + 1;
		//scrollableArpPanels.add(arpSettingsExtraPanel);

		arpParentPanel = new JPanel() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};
		arpParentPanel.setLayout(new BoxLayout(arpParentPanel, BoxLayout.Y_AXIS));


		JPanel borderPanel = new JPanel() {
			@Override
			public Dimension getMaximumSize() {
				return new Dimension(scrollPaneDimension.width, 100);
			}
		};
		borderPanel.setLayout(new DynamicGridLayout(0, 1));
		borderPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
		borderPanel.add(arpsSettingsPanel);
		borderPanel.add(arpSettingsExtraPanel);
		arpParentPanel.add(borderPanel);
		arpParentPanel.add(arpScrollPane);

		//addHorizontalSeparatorToPanel(scrollableArpPanels);
	}

	private void initArps(int startY, int anchorSide) {
		// --- ARPS -----------
		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Arps", arpParentPanel);
	}

	private void initDrumGenSettings(int startY, int anchorSide) {
		JPanel scrollableDrumPanels = new JPanel();
		scrollableDrumPanels.setLayout(new BoxLayout(scrollableDrumPanels, BoxLayout.Y_AXIS));
		scrollableDrumPanels.setAutoscrolls(true);

		drumScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(scrollPaneDimension.width, scrollPaneDimension.height - 100);
			}
		};
		drumScrollPane.setViewportView(scrollableDrumPanels);
		drumScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		drumScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		JPanel drumsPanel = new JPanel();
		drumsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		addInst[4] = new CustomCheckBox("DRUMS", true);
		drumsPanel.add(addInst[4]);

		drumVolumeSlider = new VeloRect(0, 100, 65);
		//drumVolumeSlider.setOrientation(JSlider.VERTICAL);
		drumVolumeSlider.setPreferredSize(new Dimension(15, 35));
		//drumVolumeSlider.setPaintTicks(true);
		JLabel volSliderLabel = new JLabel("Vol.");
		drumsPanel.add(volSliderLabel);
		drumsPanel.add(drumVolumeSlider);
		groupFilterSliders[4] = new VeloRect(0, 127, 127);
		JLabel filterLabel = new JLabel("LP");
		drumsPanel.add(filterLabel);
		drumsPanel.add(groupFilterSliders[4]);
		//drumsPanel.add(drumInst);

		addPanelButtons[4] = makeButton("+Drum", e -> {
			addPanel(4);
		});
		generatePanelButtons[4] = makeButton("Generate Drums:", e -> {
			generatePanels(4, true);
		});
		randomPanelsToGenerate[4] = new JTextField("6", 2);
		drumsPanel.add(addPanelButtons[4]);
		drumsPanel.add(generatePanelButtons[4]);
		drumsPanel.add(randomPanelsToGenerate[4]);

		randomDrumsGenerateOnCompose = makeCheckBox("on Compose", true, true);
		drumsPanel.add(randomDrumsGenerateOnCompose);

		JButton clearPatternSeeds = makeButton("Clear Presets", "ClearPatterns");

		randomDrumMaxSwingAdjust = new DetachedKnobPanel("Max Swing+-", 20, 0, 50);
		randomDrumSlide = new CustomCheckBox("Random Delay", false);
		randomDrumUseChordFill = new CustomCheckBox("Fills", true);
		randomDrumPattern = new CustomCheckBox("Patterns", true);
		randomDrumVelocityPatternChance = new DetachedKnobPanel("Dynamic%", 50);
		randomDrumShiftChance = new DetachedKnobPanel("Shift%", 50);

		drumsPanel.add(new JLabel("Max swing%+-"));
		drumsPanel.add(randomDrumMaxSwingAdjust);
		drumsPanel.add(randomDrumUseChordFill);

		randomDrumHitsMultiplier = new ScrollComboBox<>();
		ScrollComboBox.addAll(new String[] { OMNI.EMPTYCOMBO, "0.5x", "0.75x", "1.5x", "2x" },
				randomDrumHitsMultiplier);
		randomDrumHitsMultiplier.setVal(OMNI.EMPTYCOMBO);
		randomDrumHitsMultiplierOnGenerate = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new String[] { OMNI.EMPTYCOMBO, "0.5x", "0.75x", "1.5x", "2x" },
				randomDrumHitsMultiplierOnGenerate);
		randomDrumHitsMultiplierOnGenerate.setVal(OMNI.EMPTYCOMBO);

		randomDrumHitsMultiplier.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent event) {
				List<DrumPanel> affectedDrums = (List<DrumPanel>) (List<?>) getAffectedPanels(4);
				if (event.getStateChange() == ItemEvent.SELECTED) {
					for (int i = 0; i < affectedDrums.size(); i++) {
						int newHits = affectedDrums.get(i).getHitsPerPattern();
						switch (randomDrumHitsMultiplier.getSelectedIndex()) {
						case 0:
							return;
						case 1:
							affectedDrums.get(i).setHitsPerPattern(newHits / 2);
							break;
						case 2:
							affectedDrums.get(i).setHitsPerPattern(newHits * 3 / 4);
							break;
						case 3:
							affectedDrums.get(i).setHitsPerPattern(newHits * 3 / 2);
							break;
						case 4:
							affectedDrums.get(i).setHitsPerPattern(newHits * 2);
							break;
						default:
							throw new IllegalArgumentException("Multiplier too high!");
						}
						if (randomDrumHitsMultiplier.getSelectedIndex() > 1
								&& affectedDrums.get(i).getPattern() == RhythmPattern.CUSTOM) {
							List<Integer> trueSub = affectedDrums.get(i).getComboPanel()
									.getTruePattern().subList(0, newHits);
							Collections.rotate(trueSub, affectedDrums.get(i).getPatternShift());
							affectedDrums.get(i).setPatternShift(0);
							while (trueSub.size() < VisualPatternPanel.MAX_HITS) {
								trueSub.addAll(trueSub);
							}
							affectedDrums.get(i).getComboPanel().setTruePattern(
									trueSub.subList(0, VisualPatternPanel.MAX_HITS));
						}
					}

					randomDrumHitsMultiplier.setVal(OMNI.EMPTYCOMBO);
				}
			}
		});

		drumsPanel.add(new JLabel("Multiply Hits By"));
		drumsPanel.add(randomDrumHitsMultiplier);
		drumsPanel.add(new JLabel("On Generate"));
		drumsPanel.add(randomDrumHitsMultiplierOnGenerate);
		drumsPanel.add(randomDrumSlide);
		drumPartPresetBox = new ScrollComboBox<>();
		ScrollComboBox.addAll(new String[] { OMNI.EMPTYCOMBO }, drumPartPresetBox);
		File folder = new File(DRUMS_FOLDER);
		if (folder.exists()) {
			File[] listOfFiles = folder.listFiles();
			for (File f : listOfFiles) {
				if (f.isFile()) {
					String fileName = f.getName();
					int pos = fileName.lastIndexOf(".");
					if (pos > 0 && pos < (fileName.length() - 1)) {
						fileName = fileName.substring(0, pos);
					}

					drumPartPresetBox.addItem(fileName);
				}
			}
		}


		drumPartPresetBox.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent event) {
				if (event.getStateChange() == ItemEvent.SELECTED) {
					String item = (String) event.getItem();
					if (OMNI.EMPTYCOMBO.equals(item)) {
						return;
					}

					LG.i("Trying to load drum preset: " + item);

					// check if file exists | special case: --- should load new GUIConfig()
					File loadedFile = new File(DRUMS_FOLDER + "/" + item + ".xml");
					if (loadedFile.exists()) {
						try {
							unmarshallDrums(loadedFile);
							drumPartPresetBox.setVal(OMNI.EMPTYCOMBO);
						} catch (JAXBException | IOException e) {
							e.printStackTrace();
							return;
						}
					}

					LG.i("Loaded preset: " + item);
				}
			}
		});

		drumPartPresetAddCheckbox = new CustomCheckBox("Add", false);
		drumsPanel.add(new JLabel("Presets(/drums):"));
		drumsPanel.add(drumPartPresetBox);
		drumsPanel.add(drumPartPresetAddCheckbox);


		JPanel drumExtraSettings = new JPanel();
		JLabel csExtra = new JLabel("DRUM SETTINGS+");
		csExtra.setPreferredSize(new Dimension(120, 30));
		csExtra.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		drumExtraSettings.add(csExtra);


		combineDrumTracks = new CustomCheckBox("Combine MIDI Tracks", true);
		drumExtraSettings.add(combineDrumTracks);

		drumExtraSettings.add(makeButton("Save Drums As", "DrumSave"));
		drumExtraSettings.add(makeButton("Load Drums", "DrumLoad"));

		drumExtraSettings.add(randomDrumPattern);
		drumExtraSettings.add(randomDrumVelocityPatternChance);

		drumExtraSettings.add(randomDrumShiftChance);
		drumExtraSettings.add(clearPatternSeeds);

		randomDrumsOverrandomize = new DetachedKnobPanel("Overrandomize", 0, 0, 100);
		drumExtraSettings.add(randomDrumsOverrandomize);

		toggleableComponents.add(drumExtraSettings);

		drumsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		drumsPanel.setMaximumSize(new Dimension(1800, 50));
		scrollableDrumPanels.add(drumsPanel);
		drumExtraSettings.setAlignmentX(Component.LEFT_ALIGNMENT);
		drumExtraSettings.setMaximumSize(new Dimension(1800, 50));
		//constraints.gridy = startY + 1;
		//scrollableDrumPanels.add(drumExtraSettings);

		drumParentPanel = new JPanel() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};
		drumParentPanel.setLayout(new BoxLayout(drumParentPanel, BoxLayout.Y_AXIS));

		JPanel borderPanel = new JPanel() {
			@Override
			public Dimension getMaximumSize() {
				return new Dimension(scrollPaneDimension.width, 100);
			}
		};
		borderPanel.setLayout(new DynamicGridLayout(0, 1));
		borderPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
		borderPanel.add(drumsPanel);
		borderPanel.add(drumExtraSettings);
		drumParentPanel.add(borderPanel);
		drumParentPanel.add(drumScrollPane);

		//addHorizontalSeparatorToPanel(scrollableDrumPanels);
	}

	private void initDrums(int startY, int anchorSide) {
		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Drums", drumParentPanel);

	}

	public static void setActualModel(TableModel model) {
		setActualModel(model, true);
	}

	public static void setActualModel(TableModel model, boolean reset) {
		scrollableArrangementActualTable.setModel(model);
		scrollableArrangementActualTable.setRowSelectionAllowed(false);
		scrollableArrangementActualTable.setColumnSelectionAllowed(true);
		if (reset) {
			resetArrSection();
		}

	}

	public static void resetArrSection() {
		List<Section> actualSections = actualArrangement.getSections();
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
			arrSection.setButtons(new ArrayList<>());
			arrSection.addAll(sectionNamesNumbers.toArray(new String[] {}));
		}
	}

	public void handleArrangementAction(String action, int seed, int maxLength) {
		boolean refreshActual = false;
		boolean resetArrSectionSelection = true;
		boolean resetArrSectionPanel = true;
		boolean checkManual = false;
		if (action.equalsIgnoreCase("ArrangementReset")) {
			arrangement.generateDefaultArrangement();
			pieceLength.setText("12");
		} else if (action.equalsIgnoreCase("ArrangementAddLast")) {
			if (instrumentTabPane.getSelectedIndex() == 5) {
				arrangement.duplicateSection(scrollableArrangementTable);
			} else {
				//actualArrangement.resortByIndexes(scrollableArrangementActualTable);
				actualArrangement.duplicateSection(scrollableArrangementActualTable);
				refreshActual = true;
				checkManual = true;
			}
			if (arrangement.getSections().size() > maxLength) {
				pieceLength.setText("" + ++maxLength);
			}
		} else if (action.equalsIgnoreCase("ArrangementRemoveLast")) {
			if (instrumentTabPane.getSelectedIndex() == 5) {
				arrangement.removeSection(scrollableArrangementTable);
			} else {
				//actualArrangement.resortByIndexes(scrollableArrangementActualTable);
				actualArrangement.removeSection(scrollableArrangementActualTable);
				refreshActual = true;
				checkManual = true;
			}
			//pieceLength.setText("" + --maxLength);
		} else if (action.equalsIgnoreCase("ArrangementRandomize")) {
			// on compose -> this must happen before compose part
			arrangement.randomizeFully(maxLength, seed, 50, 30, 2, 4, 15);
		} else if (action.startsWith("ArrangementOpenVariation,")) {
			//actualArrangement.resortByIndexes(scrollableArrangementActualTable);
			Integer secOrder = Integer.valueOf(action.split(",")[1]);
			openVariationPopup(secOrder);
			return;
			//variationJD.getFrame().setTitle(action);
		} else if (action.startsWith("ArrangementApply")) {
			String selItem = arrSection.getVal();
			if (GLOBAL.equals(selItem)) {
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

			if (switchTabPaneAfterApply && instrumentTabPane.getSelectedIndex() < 5) {
				switchTabPaneAfterApply = false;
				instrumentTabPane.setSelectedIndex(6);
				arrSection.setSelectedIndexWithProperty(0, true);
			}


		} else if (action.startsWith("ArrangementClearPanels")) {
			String selItem = arrSection.getVal();
			if (!GLOBAL.equals(selItem)) {
				Integer secOrder = Integer.valueOf(selItem.split(":")[0]);
				Section sec = actualArrangement.getSections().get(secOrder - 1);
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
				selItem = newSectionBox.getVal();
			}
			if (OMNI.EMPTYCOMBO.equals(selItem)) {
				return;
			}
			if (instrumentTabPane.getSelectedIndex() != 5) {
				Section addedSec = actualArrangement
						.addDefaultSection(scrollableArrangementActualTable, selItem, col);
				addedSec.recalculatePartVariationMapBoundsIfNeeded();
				arrangement.initPartInclusionMapIfNull();
				addedSec.generatePresences(
						arrangementSeed.getValue() != 0 ? new Random(arrangementSeed.getValue())
								: new Random(),
						false);
				resetArrSectionSelection = actualArrangement.getSections()
						.indexOf(addedSec) == arrSection.getSelectedIndex() - 2;
				resetArrSectionPanel = true;
				refreshActual = true;
				checkManual = true;
			} else {
				arrangement.addDefaultSection(scrollableArrangementTable, selItem);
				if (arrangement.getSections().size() > maxLength) {
					pieceLength.setText("" + ++maxLength);
				}
			}
			newSectionBox.setSelectedIndex(0);
		} else if (action.startsWith("ArrangementRemove,")) {
			Integer secIndex = Integer.valueOf(action.split(",")[1]);
			if (instrumentTabPane.getSelectedIndex() == 5) {
				arrangement.removeSectionExact(scrollableArrangementTable, secIndex);
			} else {
				//actualArrangement.resortByIndexes(scrollableArrangementActualTable);
				actualArrangement.removeSectionExact(scrollableArrangementActualTable, secIndex);
				resetArrSectionSelection = secIndex < arrSection.getSelectedIndex();
				resetArrSectionPanel = true;
				refreshActual = true;
				checkManual = true;
			}
		} else if (action.startsWith("ArrangementAdd,")) {
			LG.i(("add exact"));
			Integer secIndex = Integer.valueOf(action.split(",")[1]);
			if (instrumentTabPane.getSelectedIndex() == 5) {
				arrangement.duplicateSectionExact(scrollableArrangementTable, secIndex);
			} else {
				//actualArrangement.resortByIndexes(scrollableArrangementActualTable);
				actualArrangement.duplicateSectionExact(scrollableArrangementActualTable, secIndex);
				resetArrSectionSelection = secIndex < arrSection.getSelectedIndex() - 1;
				resetArrSectionPanel = true;
				refreshActual = true;
				checkManual = true;
			}
			if (arrangement.getSections().size() > maxLength) {
				pieceLength.setText("" + ++maxLength);
			}
		}

		if (!refreshActual) {
			scrollableArrangementTable.setModel(arrangement.convertToTableModel());
		} else {
			int index = arrSection.getSelectedIndex();
			setActualModel(actualArrangement.convertToActualTableModel(), resetArrSectionPanel);
			if (resetArrSectionSelection) {
				arrSection.setSelectedIndexWithProperty(0, true);
			} else {
				arrSection.setSelectedIndexWithProperty(index, true);
			}
			arrSection.repaint();
			refreshVariationPopupButtons(actualArrangement.getSections().size());
		}
		if (checkManual) {
			manualArrangement.setSelected(true);
			manualArrangement.repaint();
		}
		recalculateTabPaneCounts();
	}

	private void openPartInclusionPopup() {
		arrangement.recalculatePartInclusionMapBoundsIfNeeded();
		new ArrangementPartInclusionPopup(arrangement);
	}

	private void openGlobalVariationPopup() {
		new ArrangementGlobalVariationPopup(arrangement);
	}

	private void openPatternManagerPopup() {
		new PatternManagerPopup();
	}

	private void applyCustomPanelsToSection(String action, int replacedPartNum, Integer secOrder) {
		int lastSecOrder = secOrder + 1;
		if (action.endsWith("+")) {
			lastSecOrder = actualArrangement.getSections().size() + 1;
		} else if (action.contains(",")) {
			String lastSecIndexString = action.split(",")[1];
			lastSecOrder = Integer.valueOf(lastSecIndexString) + 1;
		}

		for (int i = secOrder; i < lastSecOrder; i++) {
			Section sec = actualArrangement.getSections().get(i - 1);
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
				arrSection.getButtons().get(i).setText(i + ": " + sec.getType() + suffix);
			}
		}
	}

	public static void openVariationPopup(int secOrder) {
		if (varPopup != null) {
			varPopup.getFrame().dispose();
		}
		recalculateActualArrangementSection(secOrder - 1);
		varPopup = new VariationPopup(secOrder, actualArrangement.getSections().get(secOrder - 1),
				new Point(SwingUtils.getMouseLocation().x,
						vibeComposerGUI.getLocation().y),
				vibeComposerGUI.getSize());
	}

	public static void recalculateActualArrangementSection(int secOrder) {
		if (actualArrangement == null || actualArrangement.getSections() == null
				|| actualArrangement.getSections().size() <= secOrder) {
			return;
		}

		Section sec = actualArrangement.getSections().get(secOrder);
		if (sec != null) {
			sec.recalculatePartVariationMapBoundsIfNeeded();
		}
	}

	private void initArrangementSettings(int startY, int anchorSide) {

		arrangementSettings = new JPanel();
		arrangementSettings.setOpaque(false);
		arrangementSettings.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));

		JPanel arrangementSettingsLeft = new JPanel();
		arrangementSettingsLeft.setOpaque(false);
		arrangementSettingsLeft.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		JPanel arrangementSettingsRight = new JPanel();
		arrangementSettingsRight.setOpaque(false);
		arrangementSettingsRight.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		useArrangement = new CheckButton("ARRANGE", false);
		arrangementSettingsLeft.add(useArrangement);
		pieceLength = new JTextField("12", 2);
		//arrangementSettings.add(new JLabel("Max Length:"));
		JButton resetArrangementBtn = makeButton("Reset", "ArrangementReset", 60, 30);
		JButton randomizeArrangementBtn = makeButton("Randomize", e -> {
			Random arrGen = new Random();
			handleArrangementAction("ArrangementRandomize", arrGen.nextInt(),
					Integer.valueOf(pieceLength.getText()));
			recalculateTabPaneCounts();
			if (canRegenerateOnChange()) {
				regenerate();
			}
		}, 90);
		JButton arrangementPartInclusionBtn = makeButton("Parts", e -> openPartInclusionPopup(),
				60);
		JButton arrangementGlobalVariationBtn = makeButton("Vars", e -> openGlobalVariationPopup(),
				50);
		JButton patternManagerBtn = makeButton("Patterns", e -> openPatternManagerPopup(), 70);

		randomizeArrangementOnCompose = makeCheckBox("on Compose", true, true);

		List<CheckButton> defaultButtons = new ArrayList<>();
		defaultButtons
				.add(new SectionDropDownCheckButton(GLOBAL, true, OMNI.alphen(Color.pink, 70)));
		arrSection = new ArrangementSectionSelectorPanel(new ArrayList<>(), defaultButtons);
		arrSection.addPropertyChangeListener("selectedIndex", new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				String selItem = arrSection.getVal();
				if (selItem == null || (arrSection.getItemCount() - 1 != actualArrangement
						.getSections().size())) {
					return;
				}
				switchPanelsForSectionSelection(selItem);
			}
		});

		JButton commitPanelBtn = makeButton("Apply", "ArrangementApply", 50, 30);
		JButton commitAllPanelBtn = makeButton("Apply..", e -> openApplyCustomSectionPopup(), 60);
		JButton undoPanelBtn = makeButton("<-*",
				e -> arrSection.setSelectedIndexWithProperty(arrSection.getSelectedIndex(), true),
				30);

		JButton clearPanelBtn = makeButton("X*", e -> {
			if (!GLOBAL.equals(arrSection.getVal())) {
				Section sec = actualArrangement.getSections()
						.get(arrSection.getSelectedIndex() - 1);
				if (sec.hasCustomizedParts()) {
					sec.resetCustomizedParts(VibeComposerGUI.instrumentTabPane.getSelectedIndex());
					setActualModel(actualArrangement.convertToActualTableModel(), false);
					if (!sec.hasCustomizedParts()) {
						CheckButton cb = arrSection.getCurrentButton();
						cb.setText(cb.getText().substring(0, cb.getText().length() - 1));
						cb.repaint();
					}

					arrSection.setSelectedIndexWithProperty(arrSection.getSelectedIndex(), true);
				}
			}
		}, 30);

		JButton clearAllPanelsBtn = makeButton("CLR*", e -> {
			actualArrangement.getSections().forEach(s -> s.resetCustomizedParts());
			setActualModel(actualArrangement.convertToActualTableModel(), false);
			arrSection.getButtons().forEach(cb -> {
				if (!GLOBAL.equals(cb.getText()) && cb.getText().contains("*")) {
					cb.setText(cb.getText().substring(0, cb.getText().length() - 1));
					repaint();
				}
			});
			scrollableArrangementActualTable.repaint();
		}, 40);

		JButton copySelectedBtn = makeButton("Cc", "ArrangementAddLast", 30, 30);
		JButton removeSelectedBtn = makeButton("X", "ArrangementRemoveLast", 30, 30);
		newSectionBox = new ScrollComboBox<>(false);
		newSectionBox.addItem(OMNI.EMPTYCOMBO);
		for (SectionType type : Section.SectionType.values()) {
			newSectionBox.addItem(type.toString());
		}

		JButton addNewSectionBtn = makeButton("Add", "ArrangementAddNewSection", 35, 30);

		arrangementSettingsLeft.add(randomizeArrangementBtn);
		arrangementSettingsLeft.add(randomizeArrangementOnCompose);
		arrangementSettingsLeft.add(resetArrangementBtn);
		//arrangementSettings.add(pieceLength);

		arrangementVariationChance = new DetachedKnobPanel("Section<br>Variations", 30);
		arrangementSettingsLeft.add(arrangementVariationChance);
		arrangementPartVariationChance = new DetachedKnobPanel("Part<br>Variations", 25);
		arrangementSettingsLeft.add(arrangementPartVariationChance);
		arrangementSettingsLeft.add(arrangementPartInclusionBtn);
		arrangementSettingsLeft.add(arrangementGlobalVariationBtn);
		arrangementSettingsLeft.add(patternManagerBtn);

		arrangementMiddleColoredPanel = new JPanel();
		arrangementMiddleColoredPanel.add(new JLabel("                                      "));
		arrangementSettings.add(arrangementSettingsLeft);
		arrangementSettings.add(arrangementMiddleColoredPanel);


		manualArrangement = new CheckButton("MANUAL", false);
		arrangementSettingsRight.add(manualArrangement);
		arrangementSettingsRight.add(commitPanelBtn);
		arrangementSettingsRight.add(commitAllPanelBtn);
		arrangementSettingsRight.add(undoPanelBtn);
		arrangementSettingsRight.add(clearPanelBtn);
		arrangementSettingsRight.add(clearAllPanelsBtn);

		arrangementSettingsRight.add(newSectionBox);
		arrangementSettingsRight.add(addNewSectionBtn);
		arrangementSettingsRight.add(copySelectedBtn);
		arrangementSettingsRight.add(removeSelectedBtn);

		arrangementSettingsRight.add(new JLabel("Seed"));
		arrangementSeed = new RandomValueButton(0);
		arrangementSettingsRight.add(arrangementSeed);
		arrangementSettings.add(arrangementSettingsRight);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;

		arrSectionPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(scrollPaneDimension.width, 45);
			}
		};
		arrSectionPane.setViewportView(arrSection);
		arrSectionPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		arrSectionPane.getHorizontalScrollBar().setUnitIncrement(32);
		arrSectionPane.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		arrSectionPane.setOpaque(true);
		arrSection.setOpaque(true);
		everythingPanel.add(arrSectionPane, constraints);
		constraints.gridy = startY + 1;
		everythingPanel.add(arrangementSettings, constraints);

		scrollableArrangementTable = new JTable(5, 5) {

			private static final long serialVersionUID = 3846279087936376003L;

			@Override
			public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
				Component comp = super.prepareRenderer(renderer, row, col);
				comp.setForeground(isDarkMode ? arrangementDarkModeText : arrangementLightModeText);
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
						scrollableArrangementTable.convertColumnIndexToModel(col));
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

		arrangement = new Arrangement();
		actualArrangement = new Arrangement();
		arrangement.generateDefaultArrangement();

		scrollableArrangementTable.setModel(arrangement.convertToTableModel());
		arrangementScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};
		scrollableArrangementTable.setRowHeight(35);
		scrollableArrangementTable.setFont(new Font("Calibri", Font.PLAIN, 15));

		arrangementScrollPane.setViewportView(scrollableArrangementTable);
		JList<String> list = new JList<>();
		list.setListData(
				new String[] { "Section", "Bars", "Melody%", "Bass%", "Chord%", "Arp%", "Drum%" });
		list.setFixedCellHeight(scrollableArrangementTable.getRowHeight()
				+ scrollableArrangementTable.getRowMargin());
		arrangementScrollPane.setRowHeaderView(list);
		arrangementScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		arrangementScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		//actualArrangement.generateDefaultArrangement();
		if (useArrangement.isSelected()) {
			arrangement.setPreviewChorus(false);
			actualArrangement.setPreviewChorus(false);
		} else {
			arrangement.setPreviewChorus(true);
			actualArrangement.setPreviewChorus(true);
			actualArrangement.resetArrangement();
		}
		scrollableArrangementTable.setRowSelectionAllowed(false);
		scrollableArrangementTable.setColumnSelectionAllowed(true);
		scrollableArrangementTable.getTableHeader().setPreferredSize(
				new Dimension(scrollPaneDimension.width - arrangementRowHeaderWidth, 30));
		scrollableArrangementTable.getColumnModel()
				.addColumnModelListener(new TableColumnModelListener() {
					@Override
					public void columnMoved(TableColumnModelEvent e) {
						arrangementTableColumnDragging = true;
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
		scrollableArrangementTable.getTableHeader().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				LG.d(("MOVED HEADER"));
				arrangement.resortByIndexes(scrollableArrangementTable, false);
				arrangementTableColumnDragging = false;
			}
		});
		scrollableArrangementTable.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent evt) {
				int row = scrollableArrangementTable.rowAtPoint(evt.getPoint());
				int secOrder = scrollableArrangementTable.columnAtPoint(evt.getPoint());

				//LG.i(("Clicked! " + row + ", " + secOrder));
				if (row == 0 && secOrder >= 0) {
					boolean rClick = SwingUtilities.isRightMouseButton(evt);
					boolean mClick = !rClick && SwingUtilities.isMiddleMouseButton(evt);
					if (rClick) {
						handleArrangementAction("ArrangementRemove," + secOrder, 0, 0);
					} else if (mClick) {
						//LG.i(("mClick"));
						handleArrangementAction("ArrangementAdd," + secOrder, 0, 0);
					}

				}
			}
		});


		scrollableArrangementActualTable = new JTable(5, 5) {
			private static final long serialVersionUID = 1L;

			@Override
			public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
				Component comp = super.prepareRenderer(renderer, row, col);
				Object value = getModel().getValueAt(row,
						scrollableArrangementActualTable.convertColumnIndexToModel(col));
				comp.setForeground(isDarkMode ? arrangementDarkModeText : arrangementLightModeText);
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
						new int[] { 0, 0, melodyPanels.size(), 1, chordPanels.size(),
								arpPanels.size(), drumPanels.size() },
						true);*/
				return new CollectionCellRenderer(stringables, width, height, row - 2, col);
			}
		};
		scrollableArrangementActualTable.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent evt) {
				processActualArrangementMouseEvent(evt);
			}

			@Override
			public void mouseReleased(MouseEvent evt) {
				if (copyDragging) {
					processActualArrangementCopyDragging(evt);
					resetCopyDrag();
				}
			};
		});

		scrollableArrangementActualTable.addMouseMotionListener(new MouseMotionListener() {

			@Override
			public void mouseMoved(MouseEvent e) {
				boolean repaintAnyway = highlightedTableCell != null;
				highlightedTableCell = calculateCurrentTableSubcell(e);
				arrangementActualTableMousePoint = new Point(e.getPoint());
				if (highlightedTableCell != null || repaintAnyway) {
					scrollableArrangementActualTable.repaint();
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				boolean repaintAnyway = highlightedTableCell != null;
				highlightedTableCell = calculateCurrentTableSubcell(e);
				arrangementActualTableMousePoint = new Point(e.getPoint());
				if (highlightedTableCell != null || repaintAnyway) {
					scrollableArrangementActualTable.repaint();
				}
			}
		});

		//scrollableArrangementActualTable.setDefaultRenderer(Iterable.class, new ListCellRenderer());

		scrollableArrangementActualTable.setRowHeight(35);
		scrollableArrangementActualTable.setFont(new Font("Calibri", Font.PLAIN, 15));
		scrollableArrangementActualTable.setModel(actualArrangement.convertToActualTableModel());
		arrangementActualScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};

		JList<String> actualList = new JList<>();
		actualList.setListData(
				new String[] { "", "Section", "Info", "Melody", "Bass", "Chord", "Arp", "Drum" });
		actualList.setFixedCellHeight(scrollableArrangementActualTable.getRowHeight()
				+ scrollableArrangementActualTable.getRowMargin());
		arrangementActualScrollPane.setRowHeaderView(actualList);
		arrangementActualScrollPane
				.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		arrangementActualScrollPane.getVerticalScrollBar().setUnitIncrement(16);


		scrollableArrangementActualTable.setColumnSelectionAllowed(true);
		scrollableArrangementActualTable.setRowSelectionAllowed(false);
		scrollableArrangementActualTable.getColumnModel()
				.addColumnModelListener(new TableColumnModelListener() {
					@Override
					public void columnMoved(TableColumnModelEvent e) {
						actualArrangementTableColumnDragging = true;

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
		scrollableArrangementActualTable.getTableHeader().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				LG.i(("MOVED"));
				actualArrangement.resortByIndexes(scrollableArrangementActualTable, true);
				actualArrangementTableColumnDragging = false;
				manualArrangement.setSelected(true);
				manualArrangement.repaint();
			}
		});
		//scrollableArrangementActualTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);


		actualArrangementCombinedPanel = new JPanel();
		actualArrangementCombinedPanel
				.setLayout(new BoxLayout(actualArrangementCombinedPanel, BoxLayout.Y_AXIS));
		scrollableArrangementActualTable.getTableHeader().setPreferredSize(
				new Dimension(scrollPaneDimension.width - arrangementRowHeaderWidth, 30));
		actualArrangementCombinedPanel.add(scrollableArrangementActualTable.getTableHeader());
		actualArrangementCombinedPanel.add(scrollableArrangementActualTable);


		variationButtonsPanel = new JPanel();
		refreshVariationPopupButtons(1);
		actualArrangementCombinedPanel.add(variationButtonsPanel);

		arrangementActualScrollPane.setViewportView(actualArrangementCombinedPanel);

		instrumentTabPane.addTab("Arrangement", arrangementScrollPane);
		instrumentTabPane.addTab("Generated Arrangement", arrangementActualScrollPane);


		//toggleableComponents.add(arrSection);
		//toggleableComponents.add(commitPanelBtn);
		toggleableComponents.add(commitAllPanelBtn);
		toggleableComponents.add(undoPanelBtn);
		toggleableComponents.add(clearPanelBtn);
		toggleableComponents.add(clearAllPanelsBtn);

		resetArrSection();
	}

	protected Triple<Integer, Integer, Integer> calculateCurrentTableSubcell(MouseEvent evt) {
		int row = scrollableArrangementActualTable.rowAtPoint(evt.getPoint());
		int secOrder = scrollableArrangementActualTable.columnAtPoint(evt.getPoint());

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

	private void switchPanelsForSectionSelection(String selItem) {
		List<InstPanel> addedPanels = new ArrayList<>();

		if (GLOBAL.equals(selItem)) {
			LG.i(("Resetting to normal panels!"));
			arrangementMiddleColoredPanel.setBackground(panelColorHigh.brighter());
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
			arrangementMiddleColoredPanel.setBackground(uiColor().darker().darker());
			int sectionOrder = Integer.valueOf(selItem.split(":")[0]) - 1;
			Section sec = actualArrangement.getSections().get(sectionOrder);
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
		arrangementMiddleColoredPanel.repaint();
		addedPanels.forEach(p -> p.setVisible(true));
		toggleButtonEnabledForPanels();
		for (int i = 0; i < 5; i++) {
			JScrollPane pane = getInstPane(i);
			pane.repaint();
		}
		if (instrumentTabPane.getSelectedIndex() == 6) {
			actualArrangement.getSections().forEach(s -> s.initPartMapFromOldData());
			scrollableArrangementActualTable.repaint();
		}
	}

	private void processActualArrangementMouseEvent(java.awt.event.MouseEvent evt) {
		int row = scrollableArrangementActualTable.rowAtPoint(evt.getPoint());
		int secOrder = scrollableArrangementActualTable.columnAtPoint(evt.getPoint());


		LG.d(("Clicked! " + row + ", " + secOrder));
		boolean rClick = SwingUtilities.isRightMouseButton(evt);
		boolean mClick = !rClick && SwingUtilities.isMiddleMouseButton(evt);
		if (row == 0 && secOrder >= 0) {
			if (rClick) {
				handleArrangementAction("ArrangementRemove," + secOrder, 0, 0);
			} else if (mClick) {
				handleArrangementAction("ArrangementAdd," + secOrder, 0, 0);
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
				Section sec = actualArrangement.getSections().get(secOrder);
				boolean hasPresence = !sec.getPresence(part).isEmpty();
				boolean hasVariation = hasPresence && sec.hasVariation(part);

				if (evt.isControlDown()) {
					if (hasPresence) {
						int secOrder2 = secOrder;
						if (mClick) {
							secOrder2++;
							if (secOrder2 >= actualArrangement.getSections().size()) {
								return;
							}
						} else if (rClick) {
							secOrder2--;
							if (secOrder2 < 0) {
								return;
							}
						}
						Section sec2 = actualArrangement.getSections().get(secOrder2);
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
							arrangement.initPartInclusionMapIfNull();
							sec.generatePresences(new Random(), part, arrangement.getInclMap(),
									true);
						}
					}
				} else if (evt.isShiftDown()) {
					boolean hasAnyPresence = actualArrangement.getSections().stream()
							.anyMatch(e -> e.getPresence(part).contains(
									getInstList(part).get(partAbsoluteOrder).getPanelOrder()));
					if (mClick) {
						boolean hasAnyVariation = hasAnyPresence
								&& actualArrangement.getSections().stream().anyMatch(
										e -> !e.getVariation(part, partAbsoluteOrder).isEmpty());
						for (Section asec : actualArrangement.getSections()) {
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
							for (Section asec : actualArrangement.getSections()) {
								asec.initPartMapFromOldData();
								for (int i = 0; i < getInstList(part).size(); i++) {
									asec.resetPresence(part, partAbsoluteOrder);
								}
							}
						} else {
							arrangement.initPartInclusionMapIfNull();
							for (Section asec : actualArrangement.getSections()) {
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


				setActualModel(actualArrangement.convertToActualTableModel(), false);
				refreshVariationPopupButtons(actualArrangement.getSections().size());
				manualArrangement.setSelected(true);
				manualArrangement.repaint();
				scrollableArrangementActualTable.repaint();
			} else {
				int panelOrder = getInstList(part).get(partAbsoluteOrder).getPanelOrder();
				if (evt.isAltDown()) {
					if (secOrder + 1 < arrSection.getItemCount()) {
						arrSection.setSelectedIndexWithProperty(secOrder + 1, true);
						arrSection.repaint();
						instrumentTabPane.setSelectedIndex(part);
						switchTabPaneAfterApply = true;
					}
				} else if (evt.isControlDown()) {
					// begin copy-dragging
					Section sec = actualArrangement.getSections().get(secOrder);
					boolean hasSinglePresence = sec.getPresence(part).contains(panelOrder);
					if (hasSinglePresence && sec.containsPattern(part, panelOrder)) {
						copyDraggingOrigin = Triple.of(part, partAbsoluteOrder, secOrder);
						prepareCustomMidiSubcellCopy(part, panelOrder, sec);

					}
				} else if (currentMidi != null && partAbsoluteOrder < getInstList(part).size()) {
					Section sec = actualArrangement.getSections().get(secOrder);
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
		Point tablePoint = scrollableArrangementActualTable.getLocation();
		SwingUtilities.convertPointToScreen(tablePoint, scrollableArrangementActualTable);
		Rectangle r = scrollableArrangementActualTable.getCellRect(row, secOrder, false);

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
			PhraseNotes pn = guiConfig.getPatternRaw(copyDraggedPattern);
			if (pn == null) {
				new TemporaryInfoPopup("Invalid pattern for copying!", 1500);
				return;
			}
			Section sec = actualArrangement.getSections().get(partOrderSection.getRight());
			UsedPattern newPattern = copyDraggedPattern;
			int part = partOrderSection.getLeft();
			int panelOrder = getInstList(part).get(partOrderSection.getMiddle()).getPanelOrder();
			sec.putPattern(part, panelOrder, newPattern);
			if (!sec.getPresence(part).contains(panelOrder)) {
				sec.setPresence(part, partOrderSection.getMiddle());
			}
			pn.setApplied(true);
			setActualModel(actualArrangement.convertToActualTableModel(), false);
			refreshVariationPopupButtons(actualArrangement.getSections().size());
			manualArrangement.setSelected(true);
			manualArrangement.repaint();
			scrollableArrangementActualTable.repaint();
		} else {
			LG.i("Can't copy custom midi - invalid part!");
		}
	}

	private void prepareCustomMidiSubcellCopy(int part, int panelOrder, Section sec) {
		copyDragging = true;
		copyDraggedPattern = sec.getPattern(part, panelOrder);
		scrollableArrangementActualTable.repaint();
	}

	public void resetCopyDrag() {
		copyDragging = false;
		copyDraggedPattern = null;
		copyDraggingOrigin = null;
		scrollableArrangementActualTable.repaint();
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
					color = arrangementDarkModeLowestColor + (70 * count) / maxCounts[row];
					color = Math.min(color, 170);
				} else {
					color = arrangementLightModeHighestColor - (70 * count) / maxCounts[row];
					color = Math.max(color, 130);
				}

				int extraRed = 0;
				if (actual && actualArrangement.getSections().size() > col) {
					double remaining = 255 - color - 1;
					extraRed += actualArrangement.getSections().get(col)
							.countVariationsForPartType(row - 2) * remaining;
					extraRed = Math.min(255 - color - 1, extraRed);
				}

				comp.setBackground(new Color(color + extraRed, color, color));
			}
		} else {
			comp.setBackground(new Color(100, 150, 150));
		}
	}

	private void initScoreSettings(int startY, int anchorSide) {
		JPanel scrollableScorePanel = new JPanel();
		scrollableScorePanel.setLayout(new BoxLayout(scrollableScorePanel, BoxLayout.Y_AXIS));
		scrollableScorePanel.setAutoscrolls(true);
		scoreScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};
		scoreScrollPane.setViewportView(scrollableScorePanel);

		scoreScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
		scoreScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scoreScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		/*scoreScrollPane.getHorizontalScrollBar().addAdjustmentListener(new AdjustmentListener() {
			@Override
			public void adjustmentValueChanged(AdjustmentEvent e) {
				if (scorePanel != null) {
					LG.i("Updating pos!");
					SwingUtilities.invokeLater(() -> {
							scorePanel.update();
		
					});
				}
			}
		});*/

		instrumentTabPane.addTab("Score", scoreScrollPane);
	}


	private void refreshVariationPopupButtons(int count) {
		/*if (count == variationButtonsPanel.getComponents().length) {
			return;
		}*/
		variationButtonsPanel.removeAll();
		for (int i = 0; i < count; i++) {
			int fI = i;
			JButton butt = new JButton("Edit " + (i + 1)) {
				private static final long serialVersionUID = -374920351085418730L;

				@Override
				public void paintComponent(Graphics guh) {
					super.paintComponent(guh);
					if (actualArrangement != null) {
						if (actualArrangement.getSections() != null
								&& fI < actualArrangement.getSections().size()) {
							if (guh instanceof Graphics2D) {
								Graphics2D g = (Graphics2D) guh;
								Section sec = actualArrangement.getSections().get(fI);
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
					openVariationPopup(fI + 1);

				}
			});

			int width = Math.max(TABLE_COLUMN_MIN_WIDTH,
					(scrollPaneDimension.width - arrangementRowHeaderWidth) / count);
			butt.setPreferredSize(new Dimension(width, 50));
			butt.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent evt) {
					if (SwingUtilities.isMiddleMouseButton(evt)) {
						actualArrangement.getSections().get(fI)
								.setSectionVariations(new ArrayList<>());
						recolorVariationPopupButton(butt, actualArrangement.getSections().get(fI));
					}
				}
			});
			recolorVariationPopupButton(butt, actualArrangement.getSections().get(i));
			variationButtonsPanel.add(butt);
		}
	}

	public static void recolorAllVariationButtons() {
		for (Component c : variationButtonsPanel.getComponents()) {
			if (c instanceof JButton) {
				JButton butt = (JButton) c;
				int secOrder = Integer.valueOf(butt.getText().split(" ")[1]);
				Section sec = actualArrangement.getSections().get(secOrder - 1);
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
			color = arrangementDarkModeLowestColor + (35 * count) / total;
			color = Math.min(color, 135);
		} else {
			color = arrangementLightModeHighestColor - (70 * count) / total;
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
		chordProgressionLength = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new String[] { "4", "8", "RANDOM" }, chordProgressionLength);
		setChordProgressionLength(4);
		JLabel chordDurationFixedLabel = new JLabel("# of Chords");
		JPanel chordProgPanel = new JPanel();
		chordProgPanel.add(chordDurationFixedLabel);
		chordProgPanel.add(chordProgressionLength);
		chordProgPanel.setOpaque(false);
		macroParams.add(chordProgPanel);

		allowChordRepeats = new CustomCheckBox("Allow Chord Repeats", true);
		JPanel allowRepPanel = new JPanel();
		allowRepPanel.add(allowChordRepeats);
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
		useDoubledPanel.add(new JLabel("Beat Dur. Multiplier"));
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

	private void initChordProgressionSettings(int startY, int anchorSide) {
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
		firstChordSelection.addItemListener(this);

		lastChordSelection = new ScrollComboBox<String>(false);
		lastChordSelection.addItem("?");
		ScrollComboBox.addAll(MidiUtils.MAJOR_CHORDS.toArray(new String[] {}), lastChordSelection);
		lastChordSelection.addItemListener(this);

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

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		controlPanel.add(chordProgressionSettingsPanel);
	}

	private void initCustomChords(int startY, int anchorSide) {
		JPanel customChordsPanel = new JPanel();
		customChordsPanel.setOpaque(false);
		customChordsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		/*tipLabel = new JLabel(
				"Chord meaning: 1 = I(major), 10 = i(minor), 100 = I(aug), 1000 = I(dim), 10000 = I7(major), "
						+ "100000 = i7(minor), 1000000 = 9th, 10000000 = 13th, 100000000 = Sus4, 1000000000 = Sus2, 10000000000 = Sus7");*/

		tipLabel = new JLabel();
		//chordToolTip.add(tipLabel);

		JButton randomizeCustomChords = makeButton("    Randomize Chords    ", e -> {
			userChordsEnabled.setSelected(true);
			randomizeUserChords();
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
						userChords.getChordListString(), ScaleMode.valueOf(scaleMode.getVal()));
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
				copyGUItoConfig(guiConfig);
				List<String> normalizedChords = MidiUtils
						.respiceChords(userChords.getChordListString(), guiConfig);
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
				if (userChords.chordCount() > getMaxChordProgressionLength()) {
					userChords.cullChordsAbove(getMaxChordProgressionLength());
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
				if (melodyPanels.isEmpty()) {
					return;
				}
				userChords.alignWithMelodyTargetNotes(melodyPanels.get(0).getChordNoteChoices());
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


		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(customChordsPanel, constraints);

		toggleableComponents.add(twoExChordsButton);
		toggleableComponents.add(userDurationsEnabled);
		toggleableComponents.add(userChordsDurations);
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
		slider.setSnapToTicks(snapStartToBeat.isSelected());
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
							if (actualArrangement != null && slider.getMaximum() > 0) {
								String newTime = millisecondsToTimeString(slider.getUpperValue());
								if (!newTime.equals(currentTime.getText())) {
									currentTime.setText(newTime);
								}
								int val = slider.getUpperValue();
								int sectIndex = -1;
								if (sliderMeasureStartTimes != null
										&& actualArrangement.getSections().size() > 0) {
									int sectIndexCounter = 0;
									List<Integer> secMeasures = actualArrangement.getSections()
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
										&& sectIndex < actualArrangement.getSections().size()) {
									sec = actualArrangement.getSections().get(sectIndex);
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
									if (highlightPatterns.isSelected()) {
										SwingUtilities.invokeLater(() -> notifyVisualPatterns(val,
												finalSectIndex, actualSec));
									}
								}

								if (sequencer != null) {
									if (mainBpm.getInt() != (int) guiConfig.getBpm()) {
										sequencer.setTempoFactor(
												(float) (mainBpm.getInt() / guiConfig.getBpm()));
									}
									if (rememberLastPos.isSelected()) {
										savePauseInfo();
									}
								}

							}
						}

						if (loopBeat.isSelected() && !heavyBackgroundTasksInProgress && !isDragging
								&& (sequencer != null)) {
							/*if (showScore.isSelected() && !loopBeatCompose.isSelected()) {
								showScore.setSelected(false);
							
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
									|| (scorePopup != null || tabIndex == 7)) {
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
						e.printStackTrace();
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
		int finalVal = (int) (((1000 - speedAdjustment) * 60 * stretchMidi.getInt() / 100.0)
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

		transposeScore = new KnobPanel("Global Transpose<br>(Key)", 0, -24, 24);
		controlSettingsPanel.add(transposeScore);

		mainBpm = new DetachedKnobPanel("BPM", 80, bpmLow.getInt(), bpmHigh.getInt());
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
		controlSettingsPanel.add(currentChords);
		controlSettingsPanel.add(copyChords);
		controlSettingsPanel.add(clearSeed);


		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(controlSettingsPanel, constraints);
	}

	private void copyChords() {
		userChords.setupChords(currentChordsInternal);
		LG.i(("Copied chords: " + userChords.getChordListString()));
	}

	public void regenerateInPlace() {
		boolean wasSelected = startFromBar.isSelected();
		pauseInfoResettable = false;
		startFromBar.setSelected(false);
		pauseMidi();
		actionPerformed(
				new ActionEvent(regeneratePausePlay, ActionEvent.ACTION_PERFORMED, "Regenerate"));
		startFromBar.setSelected(wasSelected);
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
				openFolder(MIDIS_FOLDER + SAVED_MIDIS_FOLDER_BASE + "3star/");
			} else {
				saveGuiConfigFile(3);
			}
		});
		save3Star.setForeground(savedIndicatorForegroundColors[0]);
		JButton save4Star = makeButtonMoused("Save 4*", e -> {
			if (!SwingUtilities.isLeftMouseButton(e)) {
				openFolder(MIDIS_FOLDER + SAVED_MIDIS_FOLDER_BASE + "4star/");
			} else {
				saveGuiConfigFile(4);
			}
		});
		save4Star.setForeground(savedIndicatorForegroundColors[1]);
		JButton save5Star = makeButtonMoused("Save 5*", e -> {
			if (!SwingUtilities.isLeftMouseButton(e)) {
				openFolder(MIDIS_FOLDER + SAVED_MIDIS_FOLDER_BASE + "5star/");
			} else {
				saveGuiConfigFile(5);
			}
		});
		save5Star.setForeground(savedIndicatorForegroundColors[2]);
		JButton saveCustom = makeButtonMoused("Save ->", e -> {
			if (!SwingUtilities.isLeftMouseButton(e)) {
				openFolder(MIDIS_FOLDER + SAVED_MIDIS_FOLDER_BASE + "custom/");
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
				openFolder(EXPORT_FOLDER);
			} else {
				saveWavFile();
			}
		});


		showScore = new JButton("Show Score Tab");
		showScore.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				toggleShowScorePopup();
			}
		});

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
				e.printStackTrace();
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
		playSettingsPanel.add(showScore);
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
				File exportFolderDir = new File(EXPORT_FOLDER);
				exportFolderDir.mkdir();

				saveWavFile(EXPORT_FOLDER + "/" + filename + "-export.wav", defSynth);
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
					e.printStackTrace();
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
			e.printStackTrace();
		}

	}

	private void initHelperPopups() {
		JPanel helperPopupsPanel = new JPanel();
		helperPopupsPanel.add(makeButton("User Manual (opens browser)", e -> openHelpPopup()));
		helperPopupsPanel.add(makeButton("Debug Console", e -> openDebugConsole()));
		helperPopupsPanel.add(makeButton("About VibeComposer", e -> openAboutPopup()));
		extraSettingsPanel.add(helperPopupsPanel, BorderLayout.SOUTH);
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
						e.printStackTrace();
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
		if (useMidiCC.isSelected()) {
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
			double drumVol = drumVolumeSlider.getValue() / 100.0;
			sendVolumeMessage(drumVol, 9);
			sendReverbMessage(0.5, 9);
			sendChorusMessage(0.1, 9);
			sendLowPassFilterMessage(1.0, 9, 4);
			//drumPanels.forEach(e -> sendPanMessage(e.getPanSlider().getValue(), 9));
		}
	}

	protected void sendPanMessage(int pan100, int channel) {
		int value127 = useMidiCC.isSelected() ? OMNI.clampMidi(pan100 * 127 / 100) : 64;
		sendMidiCcMessage(value127, channel, 10);
	}

	protected void sendVolumeMessage(double volMultiplier, int channel) {
		int value127 = useMidiCC.isSelected()
				? OMNI.clampVel(volMultiplier * globalVolSlider.getValue() * 127 / 100.0)
				: 100;
		sendMidiCcMessage(value127, channel, 7);
	}

	protected void sendReverbMessage(double reverbMultiplier, int channel) {
		int value127 = useMidiCC.isSelected()
				? OMNI.clampVel(reverbMultiplier * globalReverbSlider.getValue())
				: 0;
		sendMidiCcMessage(value127, channel, 91);
	}

	protected void sendChorusMessage(double chorusMultiplier, int channel) {
		int value127 = useMidiCC.isSelected()
				? OMNI.clampVel(chorusMultiplier * globalChorusSlider.getValue())
				: 0;
		sendMidiCcMessage(value127, channel, 93);
	}

	protected void sendLowPassFilterMessage(double filterMultiplier, int channel, int part) {
		int value127 = useMidiCC.isSelected()
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
			e.printStackTrace();
		}
	}

	private void switchAllOnComposeCheckboxes(boolean state) {
		generateMelodiesOnCompose.setSelected(state);
		randomChordsGenerateOnCompose.setSelected(state);
		randomArpsGenerateOnCompose.setSelected(state);
		randomDrumsGenerateOnCompose.setSelected(state);
		randomizeBpmOnCompose.setSelected(state);
		randomizeTransposeOnCompose.setSelected(state);
		//randomizeChordStrumsOnCompose.setSelected(state);
		randomizeInstOnComposeOrGen.setSelected(state);
		randomArpHitsPerPattern.setSelected(state);
		randomizeArrangementOnCompose.setSelected(state);
		arrangementResetCustomPanelsOnCompose.setSelected(state);
		randomizeScaleModeOnCompose.setSelected(state);
		melodyTargetNotesRandomizeOnCompose.setSelected(state);
		melodyPatternRandomizeOnCompose.setSelected(state);
		randomizeTimingsOnCompose.setSelected(state);
		sidechainPatternsOnCompose.setSelected(state);
		copyChordsAfterGenerate.setSelected(state);
	}

	private void switchAllOnComposeCheckboxesForegrounds(Color fg) {
		generateMelodiesOnCompose.setForeground(fg);
		randomChordsGenerateOnCompose.setForeground(fg);
		randomArpsGenerateOnCompose.setForeground(fg);
		randomDrumsGenerateOnCompose.setForeground(fg);
		randomizeBpmOnCompose.setForeground(fg);
		randomizeTransposeOnCompose.setForeground(fg);
		//randomizeChordStrumsOnCompose.setForeground(fg);
		randomizeInstOnComposeOrGen.setForeground(fg);
		randomizeArrangementOnCompose.setForeground(fg);
		arrangementResetCustomPanelsOnCompose.setForeground(fg);
		randomizeScaleModeOnCompose.setForeground(fg);
		melodyTargetNotesRandomizeOnCompose.setForeground(fg);
		melodyPatternRandomizeOnCompose.setForeground(fg);
		switchOnComposeRandom.setForeground(fg);
		randomizeTimingsOnCompose.setForeground(fg);
		sidechainPatternsOnCompose.setForeground(fg);
		copyChordsAfterGenerate.setForeground(fg);
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
		if (scorePanel != null) {
			scorePanel.updatePanelHeight(newPrefSize.height);
			scorePanel.update();
		}
		scrollPaneDimension = newPrefSize;
		instrumentTabPane.setPreferredSize(newPrefSize);
		instrumentTabPane.setSize(newPrefSize);

		for (DrumPanel dp : drumPanels) {
			dp.getComboPanel().reapplyHits();
		}
		refreshVariationPopupButtons(scrollableArrangementActualTable.getColumnCount());
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
		SwingUtilities.updateComponentTreeUI(extraSettingsPanel);
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
		arrSection.setSelectedIndex(0);

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
		tipLabel.setForeground(toggledUIColor);
		currentTime.setForeground(toggledUIColor);
		totalTime.setForeground(toggledUIColor);
		compose.setForeground(toggledUIColor);
		compose.setBackground(COMPOSE_COLOR);
		regenerate.setForeground(toggledRegenerateColor);
		playMidi.setForeground(toggledUIColor);
		pauseMidi.setForeground(toggledUIColor);
		stopMidi.setForeground(toggledUIColor);
		loopBeatCompose.setForeground(toggledComposeColor);
		randomArpHitsPerPattern.setForeground(toggledUIColor);
		randomMelodyOnRegenerate.setForeground(toggledRegenerateColor);
		switchAllOnComposeCheckboxesForegrounds(toggledComposeColor);

		for (JSeparator x : separators) {
			x.setForeground(toggledUIColor);
		}

		panelColorHigh = UIManager.getColor("Panel.background");
		panelColorLow = UIManager.getColor("Panel.background");
		if (isDarkMode) {
			panelColorHigh = panelColorHigh.darker();
			panelColorLow = panelColorLow.brighter();
		} else {
			panelColorHigh = panelColorHigh.darker();
			//panelColorLow = panelColorLow.darker();
		}
		if (GLOBAL.equals(arrSection.getVal())) {
			arrangementMiddleColoredPanel.setBackground(panelColorHigh.brighter());
		} else {
			arrangementMiddleColoredPanel.setBackground(toggledUIColor.darker().darker());
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
			getAffectedPanels(i).forEach(e -> e.setBackground(OMNI.alphen(instColors[fI], 60)));
		}
		refreshVariationPopupButtons(actualArrangement.getSections().size());

		//switchFullMode(isDarkMode);

		if (scorePanel != null) {
			scorePanel.update();
		}


		removeComboBoxArrows(everythingPanel);
		//sizeRespectingPack();
		//setVisible(true);
		repaint();
		arrSectionPane.repaint();
		if (scorePanel != null) {

			scorePanel.setupMouseWheelListener();
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
		toggleButtonEnabledForPanels(GLOBAL.equals(arrSection.getVal()));
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
			}

			if (manualArrangement.isSelected() && (actualArrangement.getSections().isEmpty()
					|| !actualArrangement.getSections().stream().anyMatch(e -> e.hasPresence()))) {
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

			File makeDir = new File(MIDIS_FOLDER);
			makeDir.mkdir();
			makeDir = new File(MIDI_HISTORY_FOLDER);
			makeDir.mkdir();

			String seedData = "" + masterpieceSeed;
			if (!melodyPanels.isEmpty() && melodyPanels.get(0).getPatternSeed() != 0
					&& !melodyPanels.get(0).getMuteInst()) {
				seedData += "_" + melodyPanels.get(0).getPatternSeed();
			}
			String keyTrans = MidiUtils.SEMITONE_LETTERS.get((transposeScore.getInt() + 120) % 12)
					.replaceAll("#", "s");

			String fileName = "bpm" + mainBpm.getInt() + "_" + keyTrans + "_" + scaleMode.getVal()
					+ "_seed" + seedData;
			String relPath = MIDI_HISTORY_FOLDER + "/" + fileName + ".mid";

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

			if (configHistoryStoreRegeneratedTracks.isSelected() || !regenerate
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
			new TemporaryInfoPopup(BUG_HUNT_MESSAGE, null);
			reapplySolosMutes();
			return;
		}
		LG.i("================== VibeComposerGUI::composeMidi time: "
				+ (System.currentTimeMillis() - systemTime) + " ms ==========================");
	}

	private void fixCombinedTracks() {
		if (combineDrumTracks.isSelected()) {
			drumPanels.forEach(e -> {
				if (e.getSequenceTrack() < 0) {
					e.getSoloMuter().unsolo();
					e.getSoloMuter().unmute();
				}
			});
		}
		if (combineMelodyTracks.isSelected()) {
			melodyPanels.forEach(e -> {
				if (e.getSequenceTrack() < 0) {
					e.getSoloMuter().unsolo();
					e.getSoloMuter().unmute();
				}
			});
		}
	}

	public void fillUserParameters(boolean regenerate, boolean manual) {
		try {
			MidiGenerator.COLLAPSE_DRUM_TRACKS = combineDrumTracks.isSelected();
			MidiGenerator.recalculateDurations(stretchMidi.getInt());
			MidiGenerator.GLOBAL_DURATION_MULTIPLIER = globalNoteLengthMultiplier.getInt() / 1000.0;
			MidiGenerator.RANDOMIZE_TARGET_NOTES = !regenerate
					&& melodyTargetNotesRandomizeOnCompose.isSelected();
			MidiGenerator.TARGET_NOTES = (melody1ForcePatterns.isSelected()
					&& !melodyPanels.isEmpty()
					&& !melodyPanels.get(0).getNoteTargetsButton().isEnabled())
							? melodyPanels.stream()
									.collect(Collectors.toMap(MelodyPanel::getPanelOrder,
											MelodyPanel::getChordNoteChoices))
							: null;

			/*boolean addStartDelay = useArrangement.isSelected() || arrangementCustom.isSelected()
					|| drumPanels.stream().anyMatch(e -> e.getOffset() < 0)
					|| (randomChordDelay.isSelected()
							&& (chordPanels.stream().anyMatch(e -> e.getOffset() < 0)));*/
			MidiGenerator.START_TIME_DELAY = MidiGenerator.Durations.QUARTER_NOTE;

			/*if (loopBeat.isSelected()) {
				//MidiGenerator.START_TIME_DELAY = MidiGenerator.DBL_ERR;
				MidiGenerator.START_TIME_DELAY = MidiGenerator.Durations.EIGHTH_NOTE;
			} else {
				MidiGenerator.START_TIME_DELAY = MidiGenerator.Durations.EIGHTH_NOTE;
			}*/

			MidiGenerator.FIRST_CHORD = chordSelect(firstChordSelection.getVal());
			MidiGenerator.LAST_CHORD = chordSelect(lastChordSelection.getVal());

			// solve user chords
			boolean customChords = userChordsEnabled.isSelected()
					&& userChords.getChordletsRaw().size() > 0;
			if (customChords || userDurationsEnabled.isSelected()) {
				List<String> chords = userChords.getChordList();
				List<Double> durations = new ArrayList<>();
				String[] durationSplit = userChordsDurations.getText().split(",");
				if ((customChords && durationSplit.length < chords.size())
						|| !userDurationsEnabled.isSelected()) {
					durationSplit = null;
				}

				try {
					for (int i = 0; i < (customChords ? chords.size()
							: durationSplit.length); i++) {
						durations.add(durationSplit != null
								? (stretchMidi.getInt() * Double.valueOf(durationSplit[i]) / 100.0)
								: MidiGenerator.Durations.WHOLE_NOTE);
					}
				} catch (Exception e) {
					new TemporaryInfoPopup("Invalid durations!", 3000);
				}

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

			if (MelodyMidiDropPane.userMelody != null && useUserMelody.isSelected()) {
				MidiGenerator.userMelody = MelodyMidiDropPane.userMelody;
			} else {
				MidiGenerator.userMelody = null;
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
			e.printStackTrace();
		}

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
		if (!regenerate && generateMelodiesOnCompose.isSelected()) {
			int seed = getCurrentSeed();
			createRandomMelodyPanels(seed != 0 ? seed : new Random().nextInt(), melodyPanels.size(),
					false, null);
		}

		if (!regenerate && randomizeTimingsOnCompose.isSelected()) {
			if (globalSwingOverride.isSelected()) {
				globalSwingOverrideValue
						.setInt(50 + new Random().nextInt(randomDrumMaxSwingAdjust.getInt() * 2 + 1)
								- randomDrumMaxSwingAdjust.getInt());
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

		if (!regenerate && sidechainPatternsOnCompose.isSelected()) {
			sidechainPatterns(false, false);
		}


		if (regenerate && manual && randomMelodyOnRegenerate.isSelected()) {
			randomizeMelodySeeds();
		}

		if (regenerate && randomMelodyOnRegenerate.isSelected() && !melodyPanels.isEmpty()) {
			if (melodyPatternRandomizeOnCompose.isSelected()) {
				melodyPanels.forEach(e -> {
					e.setMelodyPatternOffsets(
							MelodyUtils.getRandomMelodyPattern(e.getAlternatingRhythmChance(),
									e.getPanelOrder() + (e.getPatternSeed() == 0 ? lastRandomSeed
											: e.getPatternSeed())));
				});
			}
			if (melodyTargetNotesRandomizeOnCompose.isSelected()) {
				melodyPanels.forEach(e -> {
					e.setChordNoteChoices(e.getNoteTargetsButton().getRandGenerator().apply(e
							.getPanelOrder()
							+ (e.getPatternSeed() == 0 ? lastRandomSeed : e.getPatternSeed())));
				});
			}
		}

		if (!regenerate && melodyPatternRandomizeOnCompose.isSelected()
				&& !melodyPanels.isEmpty()) {
			if (melody1ForcePatterns.isSelected()) {
				MelodyPanel firstMp = melodyPanels.get(0);
				List<Integer> pat = MelodyUtils.getRandomMelodyPattern(
						firstMp.getAlternatingRhythmChance(),
						firstMp.getPanelOrder() + (firstMp.getPatternSeed() == 0 ? lastRandomSeed
								: firstMp.getPatternSeed()));
				firstMp.setMelodyPatternOffsets(pat);
			} else {
				melodyPanels.forEach(e -> e.setMelodyPatternOffsets(
						MelodyUtils.getRandomMelodyPattern(e.getAlternatingRhythmChance(),
								e.getPanelOrder() + (e.getPatternSeed() == 0 ? lastRandomSeed
										: e.getPatternSeed()))));
			}
		}


		if (melody1ForcePatterns.isSelected() && !melodyPanels.isEmpty()) {
			MelodyPanel mp1 = melodyPanels.get(0);
			for (int i = 1; i < melodyPanels.size(); i++) {
				melodyPanels.get(i).overridePatterns(mp1);
			}
		}


		// BASS

		// ARPS
		if (instrumentTabPane.getSelectedIndex() != 3 && arpCopyMelodyInst.isSelected()
				&& !melodyPanels.isEmpty() && !melodyPanels.get(0).getMuteInst()) {
			if (arpPanels.size() > 0 && !arpPanels.get(0).getLockInst()) {
				arpPanels.get(0).getInstrumentBox().initInstPool(POOL.MELODY);
				arpPanels.get(0).setInstPool(POOL.MELODY);
				arpPanels.get(0).setInstrument(melodyPanels.get(0).getInstrument());
			}
		}

		if (!regenerate && arrangementResetCustomPanelsOnCompose.isSelected()) {
			actualArrangement.getSections().forEach(e -> e.resetCustomizedParts());
		} else {
			// check each section number of customized panels to see if it matches current counts
			for (int i = 0; i < actualArrangement.getSections().size(); i++) {
				Section sec = actualArrangement.getSections().get(i);
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

		if (!regenerate && randomizeArrangementOnCompose.isSelected()) {
			handleArrangementAction("ArrangementRandomize", lastRandomSeed,
					Integer.valueOf(pieceLength.getText()));
		}

		if ((regenerate || !randomizeArrangementOnCompose.isSelected()) && (currentMidi != null)
				&& manualArrangement.isSelected()) {
			arrangement.setOverridden(true);
		} else {
			arrangement.setOverridden(false);
		}

		if (currentMidiEditorPopup != null && currentMidiEditorPopup.isVisible()) {
			LG.i("MidiEditPopup is open - saving!");
			currentMidiEditorPopup.saveNotes(false);
		}

	}

	private void cleanUpUIAfterCompose(boolean regenerate) {


		List<String> prettyChords = MidiGenerator.chordInts;
		currentChords.setText(
				StringUtils.abbreviate("Chords:[" + StringUtils.join(prettyChords, ",") + "]", 60));
		currentChordsInternal.clear();
		currentChordsInternal.addAll(prettyChords);

		if (MelodyMidiDropPane.userMelody != null) {
			String chords = StringUtils.join(MidiGenerator.chordInts, ",");
			userChords.setupChords(MidiGenerator.chordInts);
			setChordProgressionLength(MidiGenerator.chordInts.size());
			guiConfig.setCustomChords(chords);
		} else if (!userChordsEnabled.isSelected() && copyChordsAfterGenerate.isSelected()) {
			userChords.setupChords(MidiGenerator.chordInts);
		}

		if (!regenerate && melodyTargetNotesRandomizeOnCompose.isSelected()
				&& MidiGenerator.TARGET_NOTES != null) {
			for (int i = 0; i < melodyPanels.size(); i++) {
				int mpOrder = melodyPanels.get(i).getPanelOrder();
				List<Integer> notes = MidiGenerator.TARGET_NOTES.get(mpOrder);
				if (notes != null) {
					melodyPanels.get(i).setChordNoteChoices(notes);
					guiConfig.getMelodyParts().get(i).setChordNoteChoices(notes);
				}
			}
		}

		for (int i = 0; i < arpPanels.size(); i++) {
			ArpPart ap = MidiGenerator.gc.getArpParts().get(i);
			if (ap.getArpPattern() == ArpPattern.RANDOM) {
				arpPanels.get(i).setArpPatternCustom(ap.getArpPatternCustom());
			}
		}

		//fixCombinedMelodyTracks();

		actualArrangement = new Arrangement();
		actualArrangement.setPreviewChorus(false);
		actualArrangement.getSections().clear();
		for (Section sec : MidiGenerator.gc.getActualArrangement().getSections()) {
			actualArrangement.getSections().add(sec.deepCopy());
		}
		guiConfig.setActualArrangement(actualArrangement);
		VibeComposerGUI.pianoRoll();
		/*if (showScore.isSelected()) {
			instrumentTabPane.setSelectedIndex(7);
		}*/


		if (currentMidiEditorPopup != null && currentMidiEditorPopup.isVisible()) {
			if (actualArrangement.getSections().size() <= currentMidiEditorSectionIndex) {
				currentMidiEditorPopup.close();
				currentMidiEditorPopup = null;
			} else {
				currentMidiEditorPopup
						.setup(actualArrangement.getSections().get(currentMidiEditorSectionIndex));
			}
		} else {
			LG.d("No midi editor is open!");
		}
	}

	private void resetArrSectionInBackground() {
		SwingUtilities.invokeLater(() -> {
			int arrSectionIndex = arrSection.getSelectedIndex();
			setActualModel(actualArrangement.convertToActualTableModel());
			if (arrSectionIndex != 0 && arrSectionIndex < arrSection.getItemCount()) {
				arrSection.setSelectedIndex(arrSectionIndex);
			} else {
				arrSection.setSelectedIndex(0);
			}
			refreshVariationPopupButtons(scrollableArrangementActualTable.getColumnCount());
			arrSection.getButtons().forEach(e -> e.repaint());
			arrSection.repaint();
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
			currentSequenceMidi = new File(TEMPORARY_SEQUENCE_MIDI_NAME);
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
			int sectionMaxText = Math.max(20 - actualArrangement.getSections().size(), 3);
			int explored = 0;
			int exploredSize = 0;
			boolean endDisplayed = false;
			while (current < slider.getMaximum()) {
				Section sec = null;

				int sizeCounter = exploredSize;

				for (int i = explored; i < actualArrangement.getSections().size(); i++) {
					Section arrSec = actualArrangement.getSections().get(i);
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

			slider.setSnapToTicks(snapStartToBeat.isSelected());
			// fix misalignment from different BPMs
			adjustSavedPositions();

			if (startFromBar.isSelected()) {
				int snapAdjustment = 50;
				if (startBeatCounter >= sliderBeatStartTimes.size()) {
					startBeatCounter = 0;
					pausedSliderPosition = 0;
					pausedMeasureCounter = 0;
					arrSection.setSelectedIndex(0);
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
				long startPos = (startFromBar.isSelected()) ? delayed : pausedSliderPosition;
				if (startPos < slider.getValue()) {
					startPos = slider.getValue();
					midiNavigate(startPos, 0);
				} else {
					midiNavigate(startPos);
				}
				resetPauseInfo();

			} else {
				String pauseBehavior = pauseBehaviorCombobox.getVal();
				if (!"NEVER".equalsIgnoreCase(pauseBehavior)) {
					boolean unpause = regenerate || pauseBehavior.contains("compose");
					unpause &= (pausedSliderPosition > 0
							&& pausedSliderPosition < slider.getMaximum() - 100);

					if (unpause) {
						long startPos = (startFromBar.isSelected())
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

			sequencer.start();  // start the playback
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
			ex.printStackTrace();
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
			chordProgressionLength.setVal("4");
			break;
		case 8:
			chordProgressionLength.setVal("8");
			break;
		default:
			chordProgressionLength.setVal("RANDOM");
			break;
		}

	}

	private int getMaxChordProgressionLength() {
		switch (chordProgressionLength.getSelectedIndex()) {
		case 0:
			return 4;
		case 1:
			return 8;
		default:
			return 16;
		}
	}

	private void randomizeMelodySeeds() {
		List<InstPanel> affectedPanels = getAffectedPanels(0);
		Random rand = new Random();
		int melodySeed = rand.nextInt();
		affectedPanels.forEach(e -> e.setVisible(false));
		if (!randomMelodySameSeed.isSelected()) {
			affectedPanels.forEach(e -> {
				int seed = rand.nextInt();
				e.setPatternSeed(seed);
			});
		} else {
			affectedPanels.forEach(e -> e.setPatternSeed(melodySeed));
		}
		affectedPanels.forEach(e -> e.setVisible(true));
	}

	private void unapplySolosMutes(boolean onlyIncluded) {
		if (!sequenceReady()) {
			return;
		}
		/*
				int countReducer = 0;
				if (combineDrumTracks.isSelected()) {
					countReducer = (int) ((onlyIncluded)
							? drumPanels.stream().filter(e -> !e.getMuteInst()).count()
							: drumPanels.size());
					countReducer = Math.max(countReducer - 1, 0);
				}
				if (combineMelodyTracks.isSelected()) {
					countReducer += 2;
				}
				int baseCount = (onlyIncluded) ? countAllIncludedPanels() : countAllPanels();
				if (padGeneratedMidi.isSelected()) {
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

		Optional<DrumPanel> notExcludedDrum = drumPanels.stream()
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
		List<Integer> paddedValues = padGeneratedMidiValues.getValues();
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
			File soundbankFile = new File((String) soundbankFilename.getEditor().getItem());
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
						+ (String) soundbankFilename.getEditor().getItem()));
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
			ex.printStackTrace();
			LG.i(("NO SOUNDBANK WITH THAT NAME FOUND!"));
		}
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
		JButton butt = new JButton(name);
		butt.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				a.accept(new Object());
			}

		});
		return butt;
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
		MidiGenerator.FIRST_CHORD = chordSelect(firstChordSelection.getVal());
		MidiGenerator.LAST_CHORD = chordSelect(lastChordSelection.getVal());
		MidiGenerator.userChords.clear();
		mg.generatePrettyUserChords(new Random().nextInt(),
				userChords.chordCount() > 0 ? userChords.chordCount()
						: MidiGenerator.gc.getFixedDuration(),
				4 * MidiGenerator.Durations.WHOLE_NOTE);
		List<String> prettyChords = MidiGenerator.chordInts;
		userChords.setupChords(prettyChords);
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
		if (arrSection.getSelectedIndex() > 0) {
			new ApplyCustomSectionPopup();
		}
	}

	private void openDebugConsole() {
		try {
			dconsole = new DebugConsole();
		} catch (Exception e) {
			// Auto-generated catch block
			e.printStackTrace();
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
		InstComboBox.BANNED_INSTS.addAll(Arrays.asList(bannedInsts.getText().split(",")));

		/*{
			int inst = melodyPanels.get(0).getInstrument();
			melodyPanels.get(0).getInstrumentBox().initInstPool(melodyPanels.get(0).getInstPool());
			melodyPanels.get(0).getInstrumentBox().setInstrument(inst);
			inst = bassPanel.getInstrument();
			bassPanel.getInstrumentBox().initInstPool(bassPanel.getInstPool());
			bassPanel.getInstrumentBox().setInstrument(inst);
		}*/


		if (ae.getActionCommand() == "InitAllInsts") {
			if (useAllInsts.isSelected()) {
				InstUtils.initAllInsts();
			} else {
				InstUtils.initNormalInsts();
			}
			for (int i = 0; i < melodyPanels.size(); i++) {
				int inst = melodyPanels.get(i).getInstrumentBox().getInstrument();
				melodyPanels.get(i).getInstrumentBox().initInstPool(InstUtils.POOL.MELODY);
				melodyPanels.get(i).getInstrumentBox().setInstrument(inst);
			}
			for (int i = 0; i < bassPanels.size(); i++) {
				int inst = bassPanels.get(i).getInstrumentBox().getInstrument();
				bassPanels.get(i).getInstrumentBox().initInstPool(InstUtils.POOL.BASS);
				bassPanels.get(i).getInstrumentBox().setInstrument(inst);
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

		if (isCompose && addInst[2].isSelected() && randomChordsGenerateOnCompose.isSelected()) {
			generatePanels(2);
		}
		if (isCompose && addInst[3].isSelected() && randomArpsGenerateOnCompose.isSelected()) {
			generatePanels(3);
		}

		if (isCompose && addInst[4].isSelected() && randomDrumsGenerateOnCompose.isSelected()) {
			generatePanels(4);
		}

		if (ae.getActionCommand() == "RandomizeTranspose") {
			Random instGen = new Random();
			transposeScore.setInt(instGen.nextInt(12) - 6);
			triggerRegenerate = true;
		}

		if (isCompose && randomizeTransposeOnCompose.isSelected()) {
			Random instGen = new Random();
			transposeScore.setInt(instGen.nextInt(12) - 6);
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
						ex.printStackTrace();
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

		if (ae.getActionCommand() == "DrumSave") {

			String drumsDirectory = DRUMS_FOLDER + "/";
			File makeSavedDir = new File(drumsDirectory);
			makeSavedDir.mkdir();

			JFileChooser chooser = new JFileChooser(makeSavedDir);
			int retrival = chooser.showSaveDialog(null);
			if (retrival == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
				try {
					String filepath = chooser.getSelectedFile().toString();
					String filename = chooser.getSelectedFile().getName().replaceAll(".xml", "");
					marshalDrums(((filepath.length() > 4)
							&& (filepath.lastIndexOf(".xml") == filepath.length() - 4)) ? filepath
									: filepath + ".xml");
					drumPartPresetBox.addItem(filename);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}

		if (ae.getActionCommand() == "DrumLoad") {
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
					unmarshallDrums(files[0]);
				} catch (JAXBException |

						IOException e) {
					// Auto-generated catch block
					e.printStackTrace();
				}
			}
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
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

		if (ae.getActionCommand() == "ClearPatterns") {
			for (DrumPanel dp : drumPanels) {
				dp.setPatternSeed(0);
				if (dp.getPattern() != RhythmPattern.FULL) {
					dp.setPattern(RhythmPattern.FULL);
					dp.setPauseChance(3 * dp.getPauseChance());
				}

			}
		}

		if (ae.getActionCommand() == "ClearChordPatterns")

		{
			for (InstPanel cp : getAffectedPanels(3)) {
				cp.setPatternSeed(0);
				cp.setPattern(RhythmPattern.FULL);

			}
		}

		if (ae.getActionCommand() == "ClearArpPatterns") {
			for (InstPanel ap : getAffectedPanels(3)) {
				ap.setPatternSeed(0);
				ap.setPattern(RhythmPattern.FULL);

			}
		}

		if (ae.getActionCommand().startsWith("Arrangement")) {
			Random arrGen = new Random();
			handleArrangementAction(ae.getActionCommand(), arrGen.nextInt(),
					Integer.valueOf(pieceLength.getText()));
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

		int bpm = instGen.nextInt(1 + bpmHigh.getInt() - bpmLow.getInt()) + bpmLow.getInt();
		if (arpAffectsBpm.isSelected() && !arpPanels.isEmpty()) {
			double highestArpPattern = arpPanels.stream().map(
					e -> (e.getPatternRepeat() * e.getHitsPerPattern()) / (e.getChordSpan() * 8.0))
					.max((e1, e2) -> Double.compare(e1, e2)).get();
			LG.i(("Repeater value: " + highestArpPattern));
			if (highestArpPattern > 1) {
				bpm *= 1 / (0.5 + highestArpPattern * 0.5);
			}
		}
		mainBpm.setInt(bpm);
		mainBpm.getKnob().setMin(bpmLow.getInt());
		mainBpm.getKnob().setMax(bpmHigh.getInt());
	}

	private void enthickenText(Component comp) {
		if (comp != null) {
			comp.setFont(comp.getFont().deriveFont(Font.BOLD));
		}
	}

	private void recalculateSoloMuters() {
		for (int i = 0; i < 5; i++) {
			recalcGroupSolo(i);
			recalcGroupMute(i);
		}
		recalcGlobals();
		needToRecalculateSoloMutersAfterSequenceGenerated = true;
	}

	private void randomizeInsts() {
		Random instGen = new Random();


		for (ChordPanel cp : chordPanels) {
			if (!cp.getLockInst()) {

				InstUtils.POOL pool = (instGen.nextInt(100) < Integer
						.valueOf(randomChordSustainChance.getInt())) ? InstUtils.POOL.CHORD
								: InstUtils.POOL.PLUCK;

				cp.setInstPool(pool);
				pool = cp.getInstPool();
				cp.getInstrumentBox().initInstPool(pool);

				cp.setInstrument(cp.getInstrumentBox().getRandomInstrument());
			}
		}
		for (ArpPanel ap : arpPanels) {
			if (!ap.getLockInst()) {
				ap.getInstrumentBox().setInstrument(ap.getInstrumentBox().getRandomInstrument());
			}
		}
		if (!melodyPanels.isEmpty()) {

			if (!combineMelodyTracks.isSelected()) {
				for (MelodyPanel mp : melodyPanels) {
					if (!mp.getLockInst()) {
						mp.getInstrumentBox()
								.setInstrument(mp.getInstrumentBox().getRandomInstrument());
					}
				}
			} else {
				int inst = melodyPanels.get(0).getInstrumentBox().getRandomInstrument();
				for (MelodyPanel mp : melodyPanels) {
					if (!mp.getLockInst()) {
						mp.getInstrumentBox().setInstrument(inst);
					}
				}
			}

		}

		for (BassPanel bp : bassPanels) {
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
		arrangementSeed.setValue(0);
	}

	private void saveGuiConfigFile(int rating) {
		if (currentMidi != null) {
			String newFileName = getFilenameForSaving(currentMidi.getName());
			LG.i(("Saving file: " + (rating >= 0 ? newFileName : saveCustomFilename.getText())));

			Date date = new Date();
			String saveDirectory = SAVED_MIDIS_FOLDER_BASE;
			String name = "";

			SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();
			f.applyPattern("yyMMdd-HH-mm-ss");
			String additionalInfo = "";

			if (rating >= 0) {
				saveDirectory += rating + "star/";

				File makeSavedDir = new File(MIDIS_FOLDER + saveDirectory);
				makeSavedDir.mkdir();
				name = newFileName;
				name = name.substring(0, name.length() - 4);

				additionalInfo = f.format(date);
			} else {
				saveDirectory += "custom/";
				name = saveCustomFilename.getText();
				if (customFilenameAddTimestamp.isSelected()) {
					additionalInfo = f.format(date);
				}
			}

			String finalFilePath = MIDIS_FOLDER + saveDirectory + additionalInfo
					+ (additionalInfo.isEmpty() ? "" : "_") + name + MID_EXTENSION;
			LG.i("Saving to final path: " + finalFilePath);
			File savedMidi = new File(finalFilePath);
			try {
				FileUtils.copyFile(currentMidi, savedMidi);
				copyGUItoConfig(guiConfig);
				marshalConfig(guiConfig, finalFilePath, MID_EXTENSION.length());
				if (rating >= 3) {
					savedIndicatorLabel.setForeground(savedIndicatorForegroundColors[rating - 3]);
				} else {
					savedIndicatorLabel.setForeground(savedIndicatorForegroundColors[3]);
				}

				savedIndicatorLabel.setVisible(true);
			} catch (IOException | JAXBException e) {
				// Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			LG.i(("currentMidi is NULL!"));
		}
	}

	private void saveGuiPresetFileByFilePath(String filePath) {
		try {
			GUIPreset preset = copyCurrentViewToPreset();
			marshalPreset(preset, filePath);
		} catch (IOException | JAXBException e) {
			// Auto-generated catch block
			e.printStackTrace();
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

				long startPos = (startFromBar.isSelected())
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
					e.printStackTrace();
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
			/*if (scorePopup != null) {
				LG.i(ShowPanelBig.rulerScrollPane.getPreferredSize());
				LG.i(ShowPanelBig.rulerScrollPane.getWidth());
				LG.i(ShowPanelBig.areaScrollPane.getWidth());
				LG.i(ShowPanelBig.horizontalPane.getWidth());
				LG.i(scoreScrollPane.getWidth());
				LG.i(scorePopup.getFrame().getWidth());
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
		instrumentTabPane.setTitleAt(0, "Melody (" + melodyPanels.size() + ")");
		instrumentTabPane.setTitleAt(1, " Bass  (" + bassPanels.size() + ")");
		instrumentTabPane.setTitleAt(2, "Chords (" + chordPanels.size() + ")");
		instrumentTabPane.setTitleAt(3, " Arps  (" + arpPanels.size() + ")");
		instrumentTabPane.setTitleAt(4, " Drums (" + drumPanels.size() + ")");
		instrumentTabPane.setTitleAt(5, "Arrangement (" + arrangement.getSections().size() + ")");
		instrumentTabPane.setTitleAt(6,
				"Generated Arrangement (" + actualArrangement.getSections().size() + ")");
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
							* stretchMidi.getInt() / 100.0);
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
			e.printStackTrace();
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

		chordSettings.setIncludePresets(randomChordPattern.isSelected());
		chordSettings.setUseDelay(randomChordDelay.isSelected());
		chordSettings.setUseStrum(randomChordStrum.isSelected());
		chordSettings.setUseSplit(randomChordSplit.isSelected());
		chordSettings.setUseTranspose(randomChordTranspose.isSelected());
		chordSettings.setShiftChance(randomChordShiftChance.getInt());
		chordSettings.setSustainChance(randomChordSustainChance.getInt());
		chordSettings.setFlattenVoicingChance(randomChordVoicingChance.getInt());
		return chordSettings;
	}

	private void setChordSettingsInUI(ChordGenSettings settings) {
		randomChordPattern.setSelected(settings.isIncludePresets());
		randomChordDelay.setSelected(settings.isUseDelay());
		randomChordStrum.setSelected(settings.isUseStrum());
		randomChordSplit.setSelected(settings.isUseSplit());
		randomChordTranspose.setSelected(settings.isUseTranspose());
		randomChordShiftChance.setInt(settings.getShiftChance());
		randomChordSustainChance.setInt(settings.getSustainChance());
		randomChordVoicingChance.setInt(settings.getFlattenVoicingChance());
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

	public void marshalDrums(String path) throws JAXBException, IOException {
		SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();
		f.applyPattern("yyMMdd-hh-mm-ss");
		JAXBContext context = JAXBContext.newInstance(DrumPartsWrapper.class);
		Marshaller mar = context.createMarshaller();
		mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		DrumPartsWrapper wrapper = new DrumPartsWrapper();
		List<DrumPart> parts = (List<DrumPart>) (List<?>) getInstPartsFromInstPanels(4, false);
		wrapper.setDrumParts(parts);
		mar.marshal(wrapper, new File(path));
		LG.i("File saved: " + path);
	}

	public void unmarshallDrums(File f) throws JAXBException, IOException {
		JAXBContext context = JAXBContext.newInstance(DrumPartsWrapper.class);
		DrumPartsWrapper wrapper = (DrumPartsWrapper) context.createUnmarshaller()
				.unmarshal(new FileReader(f));
		recreateInstPanelsFromInstParts(4, wrapper.getDrumParts(),
				!drumPartPresetAddCheckbox.isSelected());
	}

	public void unmarshallDrumsFromResource(InputStream f) throws JAXBException, IOException {
		JAXBContext context = JAXBContext.newInstance(DrumPartsWrapper.class);
		DrumPartsWrapper wrapper = (DrumPartsWrapper) context.createUnmarshaller().unmarshal(f);
		recreateInstPanelsFromInstParts(4, wrapper.getDrumParts());
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
		cs.add(generateMelodiesOnCompose);
		cs.add(null);
		cs.add(combineMelodyTracks);
		cs.add(randomMelodySameSeed);
		cs.add(randomMelodyOnRegenerate);
		cs.add(useUserMelody);
		cs.add(melodyPatternRandomizeOnCompose);
		cs.add(melodyTargetNotesRandomizeOnCompose);

		// bass panel

		// chord panel
		cs.add(randomChordsGenerateOnCompose);
		//cs.add(randomChordsToGenerate);
		cs.add(randomChordStruminess);
		cs.add(randomChordUseChordFill);
		cs.add(randomChordStretchType);
		cs.add(randomChordStretchPicker);
		cs.add(randomChordStretchGenerationChance);
		cs.add(randomChordMaxStrumPauseChance);
		cs.add(randomChordVaryLength);
		cs.add(randomChordExpandChance);
		cs.add(randomChordSustainChance);
		cs.add(randomChordMaxSplitChance);
		cs.add(chordSlashChance);
		cs.add(randomChordMinVel);
		cs.add(randomChordMaxVel);
		cs.add(randomChordPattern);
		cs.add(randomChordShiftChance);


		// arp panel
		cs.add(randomArpsGenerateOnCompose);
		//cs.add(randomArpsToGenerate);
		cs.add(randomArpHitsPicker);
		cs.add(randomArpHitsPerPattern);
		cs.add(randomArpAllSameHits);
		cs.add(randomArpUseChordFill);
		cs.add(randomArpTranspose);
		cs.add(randomArpStretchType);
		cs.add(randomArpStretchPicker);
		cs.add(randomArpStretchGenerationChance);
		cs.add(randomArpMaxExceptionChance);
		cs.add(arpCopyMelodyInst);
		cs.add(randomArpAllSameInst);
		cs.add(randomArpLimitPowerOfTwo);
		cs.add(null); // randomArpUseOctaveAdjustments
		cs.add(randomArpMaxRepeat);
		cs.add(randomArpMinVel);
		cs.add(randomArpMaxVel);
		cs.add(randomArpPattern);
		cs.add(randomArpShiftChance);
		cs.add(randomArpMinLength);
		cs.add(randomArpMaxLength);

		// drum panel
		cs.add(randomDrumsGenerateOnCompose);
		//cs.add(randomDrumsToGenerate);
		//cs.add(randomDrumMaxSwingAdjust);
		cs.add(randomDrumUseChordFill);
		cs.add(randomDrumSlide);
		cs.add(combineDrumTracks);
		cs.add(randomDrumPattern);
		cs.add(randomDrumVelocityPatternChance);
		cs.add(randomDrumShiftChance);

		// arrangement panel 
		cs.add(randomizeArrangementOnCompose);

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
		cs.add(useMidiCC);
		cs.add(arrangementResetCustomPanelsOnCompose);
		cs.add(null);
		cs.add(null);
		cs.add(loopBeatCompose);
		cs.add(useAllInsts);
		//cs.add(bannedInsts);
		cs.add(pauseBehaviorCombobox);
		cs.add(startFromBar);
		cs.add(rememberLastPos);
		cs.add(snapStartToBeat);
		cs.add(bpmLow);
		cs.add(bpmHigh);
		cs.add(stretchMidi);
		cs.add(displayVeloRectValues);
		cs.add(knobControlByDragging);
		cs.add(bottomUpReverseDrumPanels);
		cs.add(orderedTransposeGeneration);
		cs.add(patternApplyPausesWhenGenerating);
		cs.add(highlightPatterns);
		cs.add(highlightScoreNotes);
		cs.add(randomizeTimingsOnCompose);
		cs.add(customFilenameAddTimestamp);
		cs.add(configHistoryStoreRegeneratedTracks);
		cs.add(sidechainPatternsOnCompose);

		// ---------------- VIBECOMPOSER 2 ------------------------------------

		// drum panel
		cs.add(randomDrumHitsMultiplierOnGenerate);
		cs.add(drumPartPresetAddCheckbox);
		cs.add(randomDrumsOverrandomize);

		// extra settings
		cs.add(globalNoteLengthMultiplier);
		cs.add(copyChordsAfterGenerate);
		cs.add(miniScorePopup);

		// arps panel
		cs.add(randomArpCorrectMelodyNotes);

		// extra settings 2.5
		cs.add(reuseMidiChannelAfterCopy);
		cs.add(transposeNotePreview);
		cs.add(moveStartToCustomizedSection);

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
		if (!useArrangement.isSelected()) {
			arrangement.setPreviewChorus(true);
		} else {
			arrangement.setPreviewChorus(false);
		}
		arrangement.setFromTable(scrollableArrangementTable);
		boolean overrideSuccessful = manualArrangement.isSelected()
				&& actualArrangement.setFromActualTable(scrollableArrangementActualTable, false);
		arrangement.setOverridden(overrideSuccessful);

		PatternMap.checkMapBounds(guiConfig.getPatternMaps(), !overrideSuccessful);
		if (isNew) {
			gc.setPatternMaps(PatternMap.multiMapCopy(guiConfig.getPatternMaps()));
		}

		arrangement.setSeed(
				arrangementSeed.getValue() != 0 ? arrangementSeed.getValue() : lastRandomSeed);
		actualArrangement.setSeed(
				arrangementSeed.getValue() != 0 ? arrangementSeed.getValue() : lastRandomSeed);

		gc.setArrangement(arrangement);
		gc.setActualArrangement(actualArrangement);
		gc.setArrangementVariationChance(arrangementVariationChance.getInt());
		gc.setArrangementPartVariationChance(arrangementPartVariationChance.getInt());
		gc.setScaleMidiVelocityInArrangement(arrangementScaleMidiVelocity.isSelected());
		gc.setArrangementEnabled(useArrangement.isSelected());

		// macro
		gc.setScaleMode(ScaleMode.valueOf(scaleMode.getVal()));
		gc.setSoundbankName((String) soundbankFilename.getEditor().getItem());
		gc.setPieceLength(Integer.valueOf(pieceLength.getText()));
		if (chordProgressionLength.getSelectedIndex() < 2) {
			gc.setFixedDuration(Integer.valueOf(chordProgressionLength.getVal()));
		} else {
			gc.setFixedDuration(0);
		}

		gc.setTranspose(transposeScore.getInt());
		gc.setBpm(Double.valueOf(mainBpm.getInt()));
		gc.setArpAffectsBpm(arpAffectsBpm.isSelected());
		gc.setBeatDurationMultiplierIndex(beatDurationMultiplier.getSelectedIndex());
		gc.setSwingUnitMultiplierIndex(swingUnitMultiplier.getSelectedIndex());
		gc.setCustomMidiForceScale(customMidiForceScale.isSelected());
		gc.setTransposedNotesForceScale(transposedNotesForceScale.isSelected());
		gc.setAllowChordRepeats(allowChordRepeats.isSelected());
		gc.setGlobalSwingOverride(
				globalSwingOverride.isSelected() ? globalSwingOverrideValue.getInt() : null);
		gc.setHumanizeDrums(humanizeDrums.getInt());
		gc.setHumanizeNotes(humanizeNotes.getInt());

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
		gc.setMelodyUseOldAlgoChance(melodyUseOldAlgoChance.getInt());
		gc.setFirstNoteFromChord(melodyFirstNoteFromChord.isSelected());
		gc.setFirstNoteRandomized(randomChordNote.isSelected());
		gc.setMelodyBasicChordsOnly(melodyBasicChordsOnly.isSelected());
		gc.setMelodyTonicNoteTarget(melodyTonicNoteTarget.getInt());
		gc.setMelodyChordNoteTarget(melodyChordNoteTarget.getInt());
		gc.setMelodyModeNoteTarget(melodyModeNoteTarget.getInt());
		gc.setMelodyEmphasizeKey(melodyEmphasizeKey.isSelected());

		gc.setMelody1ForcePatterns(melody1ForcePatterns.isSelected());
		gc.setMelodyArpySurprises(melodyArpySurprises.isSelected());
		gc.setMelodySingleNoteExceptions(melodySingleNoteExceptions.isSelected());
		gc.setMelodyFillPausesPerChord(melodyFillPausesPerChord.isSelected());
		gc.setMelodyLegacyMode(melodyLegacyMode.isSelected());
		gc.setMelodyNewBlocksChance(melodyNewBlocksChance.getInt());
		gc.setMelodyUseDirectionsFromProgression(melodyUseDirectionsFromProgression.isSelected());
		gc.setMelodyAvoidChordJumps(melodyAvoidChordJumpsLegacy.isSelected());
		gc.setMelodyBlockTargetMode(melodyBlockTargetMode.getSelectedIndex());
		gc.setMelodyPatternEffect(melodyPatternEffect.getSelectedIndex());
		gc.setMelodyRhythmAccents(melodyRhythmAccents.getSelectedIndex());
		gc.setMelodyRhythmAccentsMode(melodyRhythmAccentsMode.getSelectedIndex());
		gc.setMelodyRhythmAccentsPocket(melodyRhythmAccentsPocket.isSelected());
		gc.setMelodyReplaceAvoidNotes(melodyReplaceAvoidNotes.getInt());
		gc.setMelodyMaxDirChanges(melodyMaxDirChanges.getInt());
		gc.setMelodyTargetNoteVariation(melodyTargetNoteVariation.getInt());

		gc.setMelodyBlockChoicePreference(melodyBlockChoicePreference.getValues());


		// chords
		gc.setUseChordFormula(useChordFormula.isSelected());
		gc.setLongProgressionSimilarity(longProgressionSimilarity.getInt());
		gc.setFirstChord(firstChordSelection.getVal());
		gc.setLastChord(lastChordSelection.getVal());
		gc.setKeyChangeType(KeyChangeType.valueOf(keyChangeTypeSelection.getVal()));
		gc.setCustomChordsEnabled(userChordsEnabled.isSelected());
		gc.setCustomChords(StringUtils.join(MidiGenerator.chordInts, ","));
		gc.setCustomChordDurations(userChordsDurations.getText());
		gc.setCustomDurationsEnabled(userDurationsEnabled.isSelected());
		gc.setSpiceChance(spiceChance.getInt());
		gc.setSpiceParallelChance(spiceParallelChance.getInt());
		gc.setDimAug6thEnabled(spiceAllowDimAug.isSelected());
		gc.setEnable9th13th(spiceAllow9th13th.isSelected());
		gc.setSpiceFlattenBigChords(spiceFlattenBigChords.isSelected());
		gc.setSquishProgressively(squishChordsProgressively.isSelected());
		gc.setChordSlashChance(chordSlashChance.getInt());
		gc.setSpiceForceScale(spiceForceScale.isSelected());

		// arps
		gc.setUseOctaveAdjustments(randomArpUseOctaveAdjustments.isSelected());
		gc.setRandomArpCorrectMelodyNotes(randomArpCorrectMelodyNotes.isSelected());

		// drums
		boolean isCustomMidiDevice = midiMode.isSelected()
				&& !(midiModeDevices.getVal()).contains("ervill");
		gc.setDrumCustomMapping(drumCustomMapping.isSelected() && isCustomMidiDevice);
		gc.setDrumCustomMappingNumbers(drumCustomMappingNumbers.getText());
		gc.setMelodyPatternFlip(melodyPatternFlip.isSelected());

		gc.setCombineMelodyTracks(combineMelodyTracks.isSelected());
	}

	public void copyConfigToGUI(GUIConfig gc) {
		arrSection.setVisible(false);
		randomMelodyOnRegenerate.setSelected(false);
		arrSection.setSelectedIndex(0);

		if (!CURRENT_VERSION.equals(gc.getVersion())) {
			LG.w("Loaded file is for an older version of VibeComposer! Curremt: " + CURRENT_VERSION + ", File version: " + gc.getVersion());
		}

		if (gc.getMelodyNotes() != null) {
			MelodyMidiDropPane.userMelody = gc.getMelodyNotes().makePhrase();
			dropPane.getMessage().setText("~MELODY LOADED FROM FILE~");
		}

		// seed
		randomSeed.setValue((int) gc.getRandomSeed());
		lastRandomSeed = randomSeed.getValue();
		midiMode.setSelected(gc.isMidiMode());

		// arrangement
		arrangement = gc.getArrangement();
		actualArrangement = gc.getActualArrangement();
		scrollableArrangementTable.setModel(arrangement.convertToTableModel());
		setActualModel(actualArrangement.convertToActualTableModel());
		arrSection.setSelectedIndex(0);
		refreshVariationPopupButtons(actualArrangement.getSections().size());

		arrangementVariationChance.setInt(gc.getArrangementVariationChance());
		arrangementPartVariationChance.setInt(gc.getArrangementPartVariationChance());
		arrangementScaleMidiVelocity.setSelected(gc.isScaleMidiVelocityInArrangement());
		arrangementSeed.setValue(arrangement.getSeed());
		useArrangement.setSelected(gc.isArrangementEnabled());
		manualArrangement.setSelected(true);

		// macro
		scaleMode.setVal(gc.getScaleMode().toString());
		soundbankFilename.getEditor().setItem(gc.getSoundbankName());
		pieceLength.setText(String.valueOf(gc.getPieceLength()));
		setChordProgressionLength(gc.getFixedDuration());

		transposeScore.setInt(gc.getTranspose());
		int bpm = (int) Math.round(gc.getBpm());
		mainBpm.getKnob().setMin(Math.min(VibeComposerGUI.mainBpm.getKnob().getMin(), bpm));
		mainBpm.getKnob().setMax(Math.max(VibeComposerGUI.mainBpm.getKnob().getMax(), bpm));

		mainBpm.setInt(bpm);

		arpAffectsBpm.setSelected(gc.isArpAffectsBpm());
		beatDurationMultiplier.setSelectedIndex(gc.getBeatDurationMultiplierIndex());
		swingUnitMultiplier.setSelectedIndex(gc.getSwingUnitMultiplierIndex());
		customMidiForceScale.setSelected(gc.isCustomMidiForceScale());
		transposedNotesForceScale.setSelected(gc.isTransposedNotesForceScale());
		allowChordRepeats.setSelected(gc.isAllowChordRepeats());
		globalSwingOverride.setSelected(gc.getGlobalSwingOverride() != null);
		if (gc.getGlobalSwingOverride() != null) {
			globalSwingOverrideValue.setInt(gc.getGlobalSwingOverride());
		}
		humanizeDrums.setInt(gc.getHumanizeDrums());
		humanizeNotes.setInt(gc.getHumanizeNotes());

		// parts
		setAddInst(0, gc.isMelodyEnable());
		setAddInst(1, gc.isBassEnable());
		setAddInst(2, gc.isChordsEnable());
		setAddInst(3, gc.isArpsEnable());
		setAddInst(4, gc.isDrumsEnable());

		//drumCustomMapping.setSelected(guiConfig.isDrumCustomMapping());
		drumCustomMappingNumbers.setText(gc.getDrumCustomMappingNumbers());
		if (StringUtils.countMatches(drumCustomMappingNumbers.getText(),
				",") != InstUtils.DRUM_INST_NUMBERS_SEMI.length - 1) {
			drumCustomMappingNumbers
					.setText(StringUtils.join(InstUtils.DRUM_INST_NUMBERS_SEMI, ","));
		}
		melodyPatternFlip.setSelected(gc.isMelodyPatternFlip());

		recreateInstPanelsFromInstParts(0, gc.getMelodyParts());
		recreateInstPanelsFromInstParts(1, gc.getBassParts());

		recreateInstPanelsFromInstParts(2, gc.getChordParts());
		recreateInstPanelsFromInstParts(3, gc.getArpParts());
		recreateInstPanelsFromInstParts(4, gc.getDrumParts());

		setChordSettingsInUI(gc.getChordGenSettings());

		// melody
		melodyFirstNoteFromChord.setSelected(gc.isFirstNoteFromChord());
		randomChordNote.setSelected(gc.isFirstNoteRandomized());
		melodyUseOldAlgoChance.setInt(gc.getMelodyUseOldAlgoChance());
		melodyBasicChordsOnly.setSelected(gc.isMelodyBasicChordsOnly());
		melodyTonicNoteTarget.setInt(gc.getMelodyTonicNoteTarget());
		melodyChordNoteTarget.setInt(gc.getMelodyChordNoteTarget());
		melodyModeNoteTarget.setInt(gc.getMelodyModeNoteTarget());
		melodyEmphasizeKey.setSelected(gc.isMelodyEmphasizeKey());

		melodyArpySurprises.setSelected(gc.isMelodyArpySurprises());
		melody1ForcePatterns.setSelected(gc.isMelody1ForcePatterns());
		melodySingleNoteExceptions.setSelected(gc.isMelodySingleNoteExceptions());
		melodyFillPausesPerChord.setSelected(gc.isMelodyFillPausesPerChord());
		melodyLegacyMode.setSelected(gc.isMelodyLegacyMode());
		melodyNewBlocksChance.setInt(gc.getMelodyNewBlocksChance());
		melodyAvoidChordJumpsLegacy.setSelected(gc.isMelodyAvoidChordJumps());
		melodyUseDirectionsFromProgression.setSelected(gc.isMelodyUseDirectionsFromProgression());
		melodyBlockTargetMode.setSelectedIndex(gc.getMelodyBlockTargetMode());
		melodyPatternEffect.setSelectedIndex(gc.getMelodyPatternEffect());
		melodyRhythmAccents.setSelectedIndex(gc.getMelodyRhythmAccents());
		melodyRhythmAccentsMode.setSelectedIndex(gc.getMelodyRhythmAccentsMode());
		melodyRhythmAccentsPocket.setSelected(gc.isMelodyRhythmAccentsPocket());
		melodyReplaceAvoidNotes.setInt(gc.getMelodyReplaceAvoidNotes());
		melodyMaxDirChanges.setInt(gc.getMelodyMaxDirChanges());
		melodyTargetNoteVariation.setInt(gc.getMelodyTargetNoteVariation());

		melodyBlockChoicePreference.setValues(gc.getMelodyBlockChoicePreference());

		// chords
		spiceChance.setInt(gc.getSpiceChance());
		spiceParallelChance.setInt(gc.getSpiceParallelChance());
		spiceAllowDimAug.setSelected(gc.isDimAug6thEnabled());
		spiceAllow9th13th.setSelected(gc.isEnable9th13th());
		spiceFlattenBigChords.setSelected(gc.isSpiceFlattenBigChords());
		squishChordsProgressively.setSelected(gc.isSquishProgressively());
		chordSlashChance.setInt(gc.getChordSlashChance());
		spiceForceScale.setSelected(gc.isSpiceForceScale());

		useChordFormula.setSelected(gc.isUseChordFormula());
		longProgressionSimilarity.setInt(gc.getLongProgressionSimilarity());
		firstChordSelection.setVal(gc.getFirstChord());
		lastChordSelection.setVal(gc.getLastChord());
		keyChangeTypeSelection.setVal(gc.getKeyChangeType().toString());
		userChordsEnabled.setSelected(gc.isCustomChordsEnabled());
		userChords.setupChords(gc.getCustomChords());
		userChordsDurations.setText(gc.getCustomChordDurations());
		userDurationsEnabled.setSelected(gc.isCustomDurationsEnabled());

		// arps
		randomArpUseOctaveAdjustments.setSelected(gc.isUseOctaveAdjustments());
		randomArpCorrectMelodyNotes.setSelected(gc.isRandomArpCorrectMelodyNotes());

		arrSection.setVisible(true);

		combineMelodyTracks.setSelected(gc.isCombineMelodyTracks());
		//fixCombinedMelodyTracks();


		if (MidiGenerator.chordInts.isEmpty()) {
			MidiGenerator.chordInts = userChords.getChordList();
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

	private void createHorizontalSeparator(int y, JFrame f) {
		int anchorTemp = constraints.anchor;
		JPanel sepPanel = new JPanel();
		sepPanel.setLayout(new BoxLayout(sepPanel, BoxLayout.X_AXIS));

		JSeparator x = new JSeparator(SwingConstants.HORIZONTAL);
		//x.setPreferredSize(new Dimension(2000, 2));
		constraints.fill = GridBagConstraints.BOTH;
		sepPanel.add(x);
		constraints.gridy = y;
		//constraints.anchor = GridBagConstraints.CENTER;
		everythingPanel.add(sepPanel, constraints);
		constraints.anchor = anchorTemp;
		separators.add(x);
		constraints.fill = GridBagConstraints.NONE;
	}

	private void addHorizontalSeparatorToPanel(JPanel panel) {
		JPanel sepPanel = new JPanel();
		sepPanel.setLayout(new BoxLayout(sepPanel, BoxLayout.X_AXIS));
		sepPanel.setMaximumSize(new Dimension(5000, 5));
		JSeparator x = new JSeparator(SwingConstants.HORIZONTAL);
		sepPanel.add(x);
		panel.add(sepPanel);
		separators.add(x);
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
			ip.setBackground(OMNI.alphen(instColors[part], 60));
		}

		if (initializingPart != null) {
			ip.setFromInstPart(initializingPart);
		}
		ip.setPanelOrder(panelOrder);

		affectedPanels.add(panelOrder - 1, ip);
		removeComboBoxArrows(ip);
		if (recalcArrangement) {
			if (actualArrangement != null && actualArrangement.getSections() != null) {
				actualArrangement.getSections().forEach(e -> e.initPartMapFromOldData());
			}
		}


		if (part < 4 || !bottomUpReverseDrumPanels.isSelected()) {
			((JPanel) getInstPane(part).getViewport().getView()).add(ip, panelOrder - 1);
		} else {
			((JPanel) getInstPane(part).getViewport().getView()).add(ip,
					affectedPanels.size() - panelOrder);
		}
		return ip;
	}

	public boolean isCustomSection() {
		return arrSection != null && !GLOBAL.equals(arrSection.getVal());
	}

	public static void removeInstPanel(int inst, int order, boolean singleRemove) {

		List<? extends InstPanel> panels = getInstList(inst);
		InstPanel panel = getPanelByOrder(order, panels);
		((JPanel) getInstPane(inst).getViewport().getView()).remove(panel);

		panels.remove(panel);

		actualArrangement.getSections().forEach(e -> e.initPartMapFromOldData());

		vibeComposerGUI.repaint();
	}

	private List<InstPart> getInstPartsFromInstPanels(int inst, boolean removeMuted) {
		List<? extends InstPanel> panels = getInstList(inst);
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
				newPanels.get(i).setPanelOrder(newPanelOrder);
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
			createRandomMelodyPanels(new Random().nextInt(), melodyPanels.size() + 1, true,
					(MelodyPanel) panel);
		} else if (partNum == 1) {

		} else if (partNum == 2) {
			createRandomChordPanels(chordPanels.size() + 1, true, (ChordPanel) panel);
		} else if (partNum == 3) {
			createRandomArpPanels(arpPanels.size() + 1, true, (ArpPanel) panel);
		} else if (partNum == 4) {
			createRandomDrumPanels(drumPanels.size() + 1, true, (DrumPanel) panel);
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
			createRandomMelodyPanels(panelCount, onlyAdd, null);
		} else if (part == 1) {
			createRandomBassPanels(panelCount, onlyAdd, null);
		} else if (part == 2) {
			createRandomChordPanels(panelCount, onlyAdd, null);
		} else if (part == 3) {
			createRandomArpPanels(panelCount, onlyAdd, null);
		} else if (part == 4) {
			createRandomDrumPanels(panelCount, onlyAdd, null);
		} else {
			throw new IllegalArgumentException("Unsupported panel part!");
		}
	}

	protected void createRandomMelodyPanels(int panelCount, boolean onlyAdd,
			MelodyPanel randomizedPanel) {
		createRandomMelodyPanels(new Random().nextInt(), panelCount, onlyAdd, null);
	}

	protected void createRandomMelodyPanels(int seed, int panelCount, boolean onlyAdd,
			MelodyPanel randomizedPanel) {
		ScrollComboBox.discardInteractions();
		List<MelodyPanel> affectedMelodies = (List<MelodyPanel>) (List<?>) getAffectedPanels(0);

		Random panelGenerator = new Random(seed);
		List<MelodyPanel> removedPanels = new ArrayList<>();
		List<MelodyPanel> remainingPanels = new ArrayList<>();
		for (Iterator<MelodyPanel> panelI = affectedMelodies.iterator(); panelI.hasNext();) {
			MelodyPanel panel = panelI.next();
			if (!onlyAdd && !panel.getLockInst()) {
				if (removedPanels.size() >= panelCount) {
					((JPanel) melodyScrollPane.getViewport().getView()).remove(panel);
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
		ChordSpanFill[] melodyFills = { ChordSpanFill.ALL, ChordSpanFill.ALL, ChordSpanFill.EVEN,
				ChordSpanFill.ODD, ChordSpanFill.HALF1, ChordSpanFill.HALF2 };
		for (int panelIndex = 0; panelIndex < panelCount; panelIndex++) {
			boolean needNewChannel = false;
			MelodyPanel ip = null;
			if (randomizedPanel != null) {
				ip = randomizedPanel;
			} else {
				if (panelIndex < removedPanels.size()) {
					ip = removedPanels.get(panelIndex);
				} else {
					ip = (MelodyPanel) addInstPanelToLayout(0);
					needNewChannel = true;
				}
			}
			if (randomizeInstOnComposeOrGen.isSelected()) {
				ip.setInstrument(ip.getInstrumentBox().getRandomInstrument());
			}

			ip.setSpeed(panelGenerator.nextInt(25));
			ip.setMaxBlockChange(3 + panelGenerator.nextInt(5));
			//melodyPanel.setSplitChance(melodyRand.nextInt(15));
			ip.setNoteExceptionChance(10 + panelGenerator.nextInt(15));
			ip.setMaxNoteExceptions(panelGenerator.nextInt(2));
			ip.setLeadChordsChance(panelGenerator.nextInt(50));

			ip.setChordSpanFill(melodyFills[panelGenerator.nextInt(melodyFills.length)]);
			ip.setFillFlip(false);

			int panelOrder = ip.getPanelOrder();
			if (panelOrder > 1) {
				ip.setFillPauses(true);
				ip.setPauseChance(50 + panelGenerator.nextInt(40));
				/*melodyPanel.toggleCombinedMelodyDisabledUI(
						combineMelodyTracks != null && !combineMelodyTracks.isSelected());*/
				ip.setVelocityMax(65 + panelGenerator.nextInt(20));
				ip.setVelocityMin(40 + panelGenerator.nextInt(20));
				if (panelOrder % 2 == 0) {
					ip.setTranspose(0);
				} else {
					ip.setTranspose(-12);
				}
				ip.setPanByOrder(3);
				ip.setNoteLengthMultiplier(70 + panelGenerator.nextInt(40));
			} else {
				ip.setFillPauses(panelGenerator.nextBoolean());
				ip.setPauseChance(panelGenerator.nextInt(35));
				ip.setTranspose(12);
				ip.setVelocityMax(80 + panelGenerator.nextInt(30));
				ip.setVelocityMin(50 + panelGenerator.nextInt(25));
				ip.setNoteLengthMultiplier(100 + panelGenerator.nextInt(25));
			}

			if (needNewChannel) {
				ip.setMidiChannel(TYPICAL_MIDI_CH.get(0).get((panelOrder - 1) % 3));
			}
		}
		repaint();
	}

	private void createRandomBassPanels(int panelCount, boolean onlyAdd,
			BassPanel randomizedPanel) {
		createRandomBassPanels(new Random().nextInt(), panelCount, onlyAdd, null);
	}

	private void createRandomBassPanels(int seed, int panelCount, boolean onlyAdd,
			BassPanel randomizedPanel) {
		ScrollComboBox.discardInteractions();
		List<BassPanel> affectedBasses = (List<BassPanel>) (List<?>) getAffectedPanels(1);

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
		Collections.sort(removedPanels, Comparator.comparing(e1 -> e1.getPanelOrder()));
		panelCount -= remainingPanels.size();
		ChordSpanFill[] bassFills = { ChordSpanFill.ALL, ChordSpanFill.ALL, ChordSpanFill.EVEN,
				ChordSpanFill.ODD, ChordSpanFill.HALF1, ChordSpanFill.HALF2 };
		List<RhythmPattern> viablePatterns = RhythmPattern.VIABLE_PATTERNS;

		for (int panelIndex = 0; panelIndex < panelCount; panelIndex++) {
			boolean needNewChannel = false;
			BassPanel ip = null;
			if (randomizedPanel != null) {
				ip = randomizedPanel;
			} else {
				if (panelIndex < removedPanels.size()) {
					ip = removedPanels.get(panelIndex);
				} else {
					ip = (BassPanel) addInstPanelToLayout(1);
					needNewChannel = true;
				}
			}
			if (randomizeInstOnComposeOrGen.isSelected()) {
				ip.setInstrument(ip.getInstrumentBox().getRandomInstrument());
			}
			int panelOrder = ip.getPanelOrder();

			ip.setFillFlip(false);

			if (panelOrder > 1) {
				ip.setChordSpanFill(bassFills[panelGenerator.nextInt(bassFills.length)]);
				ip.setPatternSeed(seed);
				ip.setPauseChance(30 + panelGenerator.nextInt(40));
				/*melodyPanel.toggleCombinedMelodyDisabledUI(
						combineMelodyTracks != null && !combineMelodyTracks.isSelected());*/
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
				// use pattern in 50% of the cases if checkbox selected
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
		repaint();
	}

	protected void createRandomChordPanels(int panelCount, boolean onlyAdd,
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
		if (randomChordStretchType.getVal().equals("FIXED")) {
			fixedChordStretch = randomChordStretchPicker.getVal();
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
				pool = (panelGenerator.nextInt(100) < randomChordSustainChance.getInt())
						? InstUtils.POOL.CHORD
						: InstUtils.POOL.PLUCK;
				ip.setInstPool(pool);
				pool = ip.getInstPool();
				ip.getInstrumentBox().initInstPool(pool);
				ip.setInstrument(ip.getInstrumentBox().getRandomInstrument());

			}

			ip.setTransitionChance(panelGenerator.nextInt(randomChordMaxSplitChance.getInt() + 1));
			ip.setTransitionSplit((getRandomFromArray(panelGenerator, MILISECOND_ARRAY_SPLIT, 0)));
			if (orderedTransposeGeneration.isSelected()) {
				ip.setTranspose((((ip.getPanelOrder()) % 3) - 1) * 12);
			} else {
				ip.setTranspose((panelGenerator.nextInt(3) - 1) * 12);
			}

			boolean pad = ip.getInstPool() == POOL.LONG_PAD;

			Pair<StrumType, Integer> strumPair = getRandomStrumPair();
			ip.setStrum(strumPair.getRight());
			ip.setStrumType(strumPair.getLeft());
			if (randomChordDelay.isSelected()) {
				ip.setOffset((getRandomFromArray(panelGenerator, MILISECOND_ARRAY_DELAY, 0)));
			} else {
				ip.setOffset(0);
			}


			if (randomChordUseChordFill.isSelected() && !pad) {
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
				if (randomChordPattern.isSelected()) {
					pattern = viablePatterns.get(panelGenerator.nextInt(viablePatterns.size()));
					if (pattern == RhythmPattern.MELODY1) {
						pattern = RhythmPattern.FULL;
					}
					if (ip.getStrum() > 501) {
						ip.setStrum(ip.getStrum() / 2);
					}
				}
			}

			if (!randomChordStretchType.getVal().equals("NONE")
					&& panelGenerator.nextInt(100) < randomChordStretchGenerationChance.getInt()) {
				ip.setStretchEnabled(true);
				if (fixedChordStretch < 0) {
					int atMost = randomChordStretchPicker.getVal();
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
					panelGenerator.nextInt(randomChordMaxStrumPauseChance.getInt() + 1));

			ip.setPattern(pattern);
			if ((pattern == RhythmPattern.FULL || pattern == RhythmPattern.MELODY1)
					&& ip.getStrum() > 499) {
				ip.setStrum(ip.getStrum() / 4);
			}

			if (pad || panelGenerator.nextInt(100) < randomChordExpandChance.getInt()) {
				ip.setPatternJoinMode(PatternJoinMode.EXPAND);
			} else {
				ip.setPatternJoinMode(PatternJoinMode.NOJOIN);
			}


			ip.setVelocityMax(randomChordMaxVel.getInt());
			ip.setVelocityMin(randomChordMinVel.getInt());

			if (randomChordVaryLength.isSelected()) {
				if (pool == InstUtils.POOL.PLUCK) {
					ip.setNoteLengthMultiplier(panelGenerator.nextInt(26) + 50);
				} else {
					ip.setNoteLengthMultiplier(panelGenerator.nextInt(26) + 85);
				}

			}

			if (panelGenerator.nextInt(100) < randomChordShiftChance.getInt()) {
				int maxShift = Math.min(ip.getPattern().maxShift, ip.getHitsPerPattern() - 1);
				// test opposite check for shift distance
				if (panelGenerator.nextInt(100) >= randomChordShiftChance.getInt()) {
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
				ip.setMidiChannel(VibeComposerGUI.getNextFreeMidiChannel(2, ip.getPanelOrder()));
				ip.setPanByOrder(5);
			}
		}

		repaint();
	}

	protected void createRandomArpPanels(int panelCount, boolean onlyAdd,
			ArpPanel randomizedPanel) {
		ScrollComboBox.discardInteractions();
		List<ArpPanel> affectedArps = (List<ArpPanel>) (List<?>) getAffectedPanels(3);

		Random panelGenerator = new Random();
		List<ArpPanel> removedPanels = new ArrayList<>();
		List<ArpPanel> remainingPanels = new ArrayList<>();
		for (Iterator<ArpPanel> panelI = affectedArps.iterator(); panelI.hasNext();) {
			ArpPanel panel = panelI.next();
			if (!onlyAdd && !panel.getLockInst()) {
				if (removedPanels.size() >= panelCount) {
					((JPanel) arpScrollPane.getViewport().getView()).remove(panel);
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

		int fixedHitsGenerated = -1;
		if (randomArpHitsPerPattern.isSelected() && randomArpAllSameHits.isSelected()) {
			Random instGen = new Random();
			if (randomArpLimitPowerOfTwo.isSelected()) {
				fixedHitsGenerated = getRandomFromArray(instGen, new int[] { 2, 4, 4, 8, 8, 8, 8 },
						0);
			} else {
				fixedHitsGenerated = instGen.nextInt(MidiGenerator.MAXIMUM_PATTERN_LENGTH - 1) + 2;

				if (fixedHitsGenerated == 5) {
					// reduced chance of 5
					fixedHitsGenerated = instGen.nextInt(MidiGenerator.MAXIMUM_PATTERN_LENGTH - 1)
							+ 2;
				}
				if (fixedHitsGenerated == 7) {
					// eliminate 7
					fixedHitsGenerated++;
				}
			}

			randomArpHitsPicker.setVal(fixedHitsGenerated);
		}

		int fixedInstrument = -1;
		int fixedHits = -1;

		if (arpCopyMelodyInst.isSelected() && !melodyPanels.isEmpty()
				&& !melodyPanels.get(0).getMuteInst()) {
			fixedInstrument = melodyPanels.get(0).getInstrument();
			if (affectedArps.size() > 0) {
				affectedArps.get(0).setInstrument(fixedInstrument);
			}
		}

		int fixedArpStretch = -1;
		if (randomArpStretchType.getVal().equals("FIXED")) {
			fixedArpStretch = randomArpStretchPicker.getVal();
		}


		int start = 0;
		if (randomizedPanel != null) {
			start = randomizedPanel.getPanelOrder() - 1;
			panelCount = start + 1;
		}

		ArpPanel first = (affectedArps.isEmpty() || !affectedArps.get(0).getLockInst()
				|| (randomizedPanel != null && start == 0)) ? null : affectedArps.get(0);
		List<RhythmPattern> viablePatterns = new ArrayList<>(RhythmPattern.VIABLE_PATTERNS);

		for (int panelIndex = start; panelIndex < panelCount; panelIndex++) {
			if (randomArpAllSameInst.isSelected() && first != null && fixedInstrument < 0) {
				fixedInstrument = first.getInstrument();
			}
			if (randomArpAllSameHits.isSelected() && first != null && fixedHits < 0) {
				fixedHits = first.getHitsPerPattern() / first.getChordSpan();
			}
			ArpPanel ip = null;
			boolean needNewChannel = false;
			if (randomizedPanel != null) {
				ip = randomizedPanel;
			} else {
				if (panelIndex < removedPanels.size()) {
					ip = removedPanels.get(panelIndex);
				} else {
					needNewChannel = true;
					ip = (ArpPanel) addInstPanelToLayout(3);
				}
			}


			if (randomArpHitsPerPattern.isSelected()) {
				if (fixedHits > 0) {
					ip.setHitsPerPattern(fixedHits);
				} else {
					if (fixedHitsGenerated > 0) {
						ip.setHitsPerPattern(fixedHitsGenerated);
					} else {
						Random instGen = new Random();
						int value = -1;
						if (randomArpLimitPowerOfTwo.isSelected()) {
							value = getRandomFromArray(instGen, new int[] { 2, 4, 4, 8, 8, 8, 8 },
									0);
						} else {
							value = instGen.nextInt(MidiGenerator.MAXIMUM_PATTERN_LENGTH - 1) + 2;

							if (value == 5) {
								// reduced chance of 5
								value = instGen.nextInt(MidiGenerator.MAXIMUM_PATTERN_LENGTH - 1)
										+ 2;
							}
							if (value == 7) {
								// eliminate 7
								value++;
							}
						}
						ip.setHitsPerPattern(value);
					}
				}
			} else {
				ip.setHitsPerPattern(randomArpHitsPicker.getSelectedIndex() + 1);
			}

			if (randomizeInstOnComposeOrGen.isSelected() || onlyAdd) {
				int instrument = ip.getInstrumentBox().getRandomInstrument();

				if (randomArpAllSameInst.isSelected()) {
					if (fixedInstrument >= 0) {
						instrument = fixedInstrument;
					} else {
						fixedInstrument = instrument;
					}
				}
				if (ip.getInstrumentBox().isEnabled()) {
					ip.setInstrument(instrument);
				}
			}

			ip.setChordSpan(panelGenerator.nextInt(2) + 1);

			if (orderedTransposeGeneration.isSelected()) {
				ip.setTranspose((((ip.getPanelOrder() + 1) % 3) - 1) * 12);
			} else {
				if (first == null && panelIndex == 0 && !onlyAdd) {
					ip.setTranspose(12);
				} else {
					ip.setTranspose((panelGenerator.nextInt(3) - 1) * 12);
				}
			}


			if (first == null && panelIndex == 0 && !onlyAdd && arpCopyMelodyInst.isSelected()
					&& !melodyPanels.isEmpty() && !melodyPanels.get(0).getMuteInst()) {
				ip.setInstrument(fixedInstrument);
			}


			if (ip.getChordSpan() == 1) {
				ip.setPatternRepeat(panelGenerator.nextInt(randomArpMaxRepeat.getInt()) + 1);
			} else {
				ip.setPatternRepeat(1);
				if (panelGenerator.nextBoolean() == true) {
					if ((first == null && panelIndex > 1) || (first != null)) {
						ip.setHitsPerPattern(ip.getHitsPerPattern() * ip.getChordSpan());
					}

				}
			}

			boolean fastArp = (ip.getPatternRepeat() * ip.getHitsPerPattern()
					/ (double) ip.getChordSpan()) >= 16;
			if (fastArp) {
				ip.setExceptionChance(
						panelGenerator.nextInt(1 + (randomArpMaxExceptionChance.getInt() / 3)));
			} else {
				ip.setExceptionChance(
						panelGenerator.nextInt(1 + randomArpMaxExceptionChance.getInt()));
			}

			if (!randomArpStretchType.getVal().equals("NONE")
					&& panelGenerator.nextInt(100) < randomArpStretchGenerationChance.getInt()) {
				ip.setStretchEnabled(true);
				if (fixedArpStretch < 0) {
					int atMost = randomArpStretchPicker.getVal();
					ip.setChordNotesStretch(panelGenerator.nextInt(atMost - 3 + 1) + 3);
				} else {
					ip.setChordNotesStretch(fixedArpStretch);
				}
			} else {
				ip.setStretchEnabled(false);
			}

			RhythmPattern pattern = RhythmPattern.FULL;
			// use pattern if checkbox selected and %chance 
			int patternChanceIncrease = (ip.getPanelOrder() < 4 || arpPanels.size() < 3) ? 0
					: arpPanels.size() * 5;
			int fillChanceIncrease = (ip.getPanelOrder() < 4 || arpPanels.size() < 3) ? 0
					: (arpPanels.size() - 3) * 5;
			if (panelGenerator.nextInt(100) < (30 + patternChanceIncrease)) {
				if (randomArpPattern.isSelected()) {
					pattern = viablePatterns.get(panelGenerator.nextInt(viablePatterns.size()));
				}
			}
			ip.setPattern(pattern);
			if (randomArpUseChordFill.isSelected()) {
				int fillWeight = OMNI.clampChance(
						panelGenerator.nextInt(100 - fillChanceIncrease) + fillChanceIncrease);
				ip.setChordSpanFill(ChordSpanFill.getWeighted(fillWeight));
			} else {
				ip.setChordSpanFill(ChordSpanFill.ALL);
			}
			ip.setFillFlip(false);
			ip.setPatternFlip(false);

			ip.setVelocityMax(randomArpMaxVel.getInt());
			ip.setVelocityMin(randomArpMinVel.getInt());

			int pauseMax = (int) (50 * ip.getPattern().getNoteFrequency());
			ip.setPauseChance(panelGenerator.nextInt(pauseMax + 1));
			ip.applyPauseChance(panelGenerator);

			if (panelGenerator.nextInt(100) < randomArpShiftChance.getInt()) {
				//LG.d("Arp getPattern: " + ip.getPattern().name());
				int maxShift = Math.min(ip.getPattern().maxShift, ip.getHitsPerPattern() - 1);

				if (beatDurationMultiplier != null && beatDurationMultiplier.getVal() < 0.75) {
					maxShift /= 2;
				}
				ip.setPatternShift(maxShift > 0 ? (panelGenerator.nextInt(maxShift) + 1) : 0);
			} else {
				ip.setPatternShift(0);
			}

			ip.growPattern(panelGenerator, 1, 5);

			int lengthRange = Math.max(1,
					1 + randomArpMaxLength.getInt() - randomArpMinLength.getInt());
			ip.setNoteLengthMultiplier(
					panelGenerator.nextInt(lengthRange) + randomArpMinLength.getInt());

			if (panelGenerator.nextBoolean()) {
				int arpPatternOrder = 0;
				// excludes CUSTOM pattern
				int[] patternWeights = { 60, 68, 75, 83, 91, 97, 100 };
				int randomWeight = panelGenerator.nextInt(100);
				for (int j = 0; j < patternWeights.length; j++) {
					if (randomWeight < patternWeights[j]) {
						arpPatternOrder = j;
						break;
					}
				}
				ip.setArpPattern(ArpPattern.values()[arpPatternOrder]);
				if (arpPatternOrder > 0 && panelGenerator.nextBoolean()) {
					ip.setArpPatternRotate(
							panelGenerator.nextInt(Math.min(4, ip.getChordNotesStretch())));
				}
			} else {
				ip.setArpPattern(ArpPattern.RANDOM);
			}

			if (needNewChannel) {
				ip.setMidiChannel(VibeComposerGUI.getNextFreeMidiChannel(3, ip.getPanelOrder()));
				ip.setPanByOrder(7);
			}

			ip.getComboPanel().reapplyShift();
			ip.getComboPanel().reapplyHits();
		}

		/*if (!affectedArps.isEmpty()) {
			ArpPanel lowest = affectedArps.get(0);
			if (!lowest.getLockInst()) {
				lowest.setPatternRepeat(1);
				lowest.setChordSpan(1);
		
			}
		}*/

		//sizeRespectingPack();
		repaint();
	}

	protected void createRandomDrumPanels(int panelCount, boolean onlyAdd,
			DrumPanel randomizedPanel) {
		ScrollComboBox.discardInteractions();
		List<DrumPanel> affectedDrums = (List<DrumPanel>) (List<?>) getAffectedPanels(4);

		Random panelGenerator = new Random();
		List<DrumPanel> removedPanels = new ArrayList<>();
		List<DrumPanel> remainingPanels = new ArrayList<>();
		for (Iterator<DrumPanel> panelI = affectedDrums.iterator(); panelI.hasNext();) {
			DrumPanel panel = panelI.next();
			if (!onlyAdd && !panel.getLockInst()) {
				if (removedPanels.size() >= panelCount) {
					((JPanel) drumScrollPane.getViewport().getView()).remove(panel);
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

		int slide = 0;

		if (randomDrumSlide.isSelected()) {
			slide = panelGenerator.nextInt(100) - 50;
		}

		int swingPercent = 50;
		if (onlyAdd && remainingPanels.size() > 0) {
			Optional<Integer> existingSwing = remainingPanels.stream()
					.filter(e -> (e.getSwingPercent() != 50)).map(e -> e.getSwingPercent())
					.findFirst();
			if (existingSwing.isPresent()) {
				swingPercent = existingSwing.get();
			}
		}
		// nothing's changed.. still the same..
		if (swingPercent == 50) {
			swingPercent = 50 + panelGenerator.nextInt(randomDrumMaxSwingAdjust.getInt() * 2 + 1)
					- randomDrumMaxSwingAdjust.getInt();
		}


		List<Integer> pitches = new ArrayList<>();
		for (int i = 0; i < panelCount; i++) {
			pitches.add(InstUtils.getInstByIndex(panelGenerator.nextInt(127), InstUtils.POOL.DRUM));
		}
		Collections.sort(pitches);
		int index = 0;
		long kickCount = remainingPanels.stream()
				.filter(e -> KICK_DRUMS.contains(e.getInstrument())).count();
		long snareCount = remainingPanels.stream()
				.filter(e -> SNARE_DRUMS.contains(e.getInstrument())).count();
		if (!onlyAdd && pitches.size() >= 3) {
			//LG.i(("Kick,snare: " + kickCount + ", " + snareCount));
			if (kickCount == 0) {
				pitches.set(index++, 35);
				pitches.set(index++, 36);
			} else if (kickCount == 1) {
				pitches.set(index++, 36);
			}


			if (snareCount == 0) {
				pitches.set(index++, panelGenerator.nextBoolean() ? 38 : 40);
			}
		} else if (onlyAdd) {
			List<Integer> allowedInsts = new ArrayList<>(
					Arrays.asList(InstUtils.DRUM_INST_NUMBERS));
			if (snareCount >= 1) {
				allowedInsts.remove(3);
				allowedInsts.remove(4);
			} else if (kickCount >= 1) {
				allowedInsts.clear();
				allowedInsts.add(38);
				allowedInsts.add(40);
			}
			if (kickCount >= 2 && snareCount >= 1) {
				allowedInsts.remove(0);
				allowedInsts.remove(0);
			} else if (kickCount == 0) {
				allowedInsts.clear();
				allowedInsts.add(35);
				allowedInsts.add(36);
			}
			pitches.set(0, allowedInsts.get(panelGenerator.nextInt(allowedInsts.size())));
		}
		if (!onlyAdd) {
			if (pitches.stream().filter(e -> KICK_DRUMS.contains(e)).count() > 1) {
				for (int i = index; i < pitches.size(); i++) {
					int e = pitches.get(i);
					if (KICK_DRUMS.contains(e)) {
						pitches.set(i, i % 2 == 0 ? 46 : 60);
					}
				}
			}

			if (pitches.stream().filter(e -> SNARE_DRUMS.contains(e)).count() > 1) {
				for (int i = index; i < pitches.size(); i++) {
					int e = pitches.get(i);
					if (SNARE_DRUMS.contains(e)) {
						pitches.set(i, i % 2 == 0 ? 42 : 44);
					}
				}
			}
			Collections.sort(pitches);
		}


		int chords = 2;
		int maxPatternPerChord = VisualPatternPanel.MAX_HITS;

		/*int[] drumHitGrid = IntStream.iterate(0, e -> e).limit(chords * maxPatternPerChord)
				.toArray();*/
		for (int panelIndex = 0; panelIndex < panelCount; panelIndex++) {
			DrumPanel ip = null;
			if (randomizedPanel != null) {
				ip = randomizedPanel;
			} else {
				if (panelIndex < removedPanels.size()) {
					ip = removedPanels.get(panelIndex);
				} else {
					ip = (DrumPanel) addInstPanelToLayout(4);
				}
			}

			if (new Random().nextInt(100) < randomDrumsOverrandomize.getInt()) {
				setupOverrandomizedDrum(panelGenerator, slide, swingPercent, pitches, panelIndex,
						ip);
			} else {
				setupBlueprintedDrum(panelGenerator, slide, swingPercent, pitches, panelIndex, ip);
			}


			/*DrumPart panelPart = dp.toDrumPart(lastRandomSeed);
			int[] drumPartArray = displayDrumPart(panelPart, chords, maxPatternPerChord);
			for (int j = 0; j < drumPartArray.length; j++) {
				drumHitGrid[j] += drumPartArray[j];
			}*/
			/*if (needPanApplied) {
				dp.getPanSlider().setValue(drumPanelGenerator.nextInt(100));
			}*/

		}
		/*for (int i = 0; i < chords * maxPatternPerChord; i++) {
			System.out.print(drumHitGrid[i] + ", ");
		}*/

		repaint();
	}

	private void setupBlueprintedDrum(Random panelGenerator, int slide, int swingPercent,
			List<Integer> pitches, int panelIndex, DrumPanel ip) {
		DrumPart dpart = DrumDefaults.getDrumFromInstrument(
				!ip.getInstrumentBox().isEnabled() ? ip.getInstrument() : pitches.get(panelIndex));
		int order = DrumDefaults.getOrder(dpart.getInstrument());
		DrumSettings settings = DrumDefaults.drumSettings[order];
		settings.applyToDrumPart(dpart, lastRandomSeed);


		dpart.setOrder(ip.getPanelOrder());
		dpart.setMuted(ip.getMuteInst());
		switch (randomDrumHitsMultiplierOnGenerate.getSelectedIndex()) {
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
			double ghostChanceReducer = (drumPanels.size() > 10) ? 0.8 : 1.0;
			ip.setIsVelocityPattern(panelGenerator
					.nextInt(100) < randomDrumVelocityPatternChance.getInt() * ghostChanceReducer);
		} else {
			ip.setIsVelocityPattern(false);
		}

		if (drumPanels.size() > 10 && ip.getPattern() == RhythmPattern.FULL
				&& panelGenerator.nextInt(100) < 30) {
			ip.setPattern(RhythmPattern.ALT);
		}

		if (settings.isVariableShift()
				&& panelGenerator.nextInt(100) < randomDrumShiftChance.getInt()) {
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

	private void setupOverrandomizedDrum(Random drumPanelGenerator, int slide, int swingPercent,
			List<Integer> pitches, int panelIndex, DrumPanel ip) {
		ip.setInstrument(pitches.get(panelIndex));
		//dp.setPitch(32 + drumPanelGenerator.nextInt(33));


		ip.setChordSpan(drumPanelGenerator.nextInt(2) + 1);
		RhythmPattern pattern = RhythmPattern.FULL;
		// use pattern in half the cases if checkbox selected

		if (randomDrumPattern.isSelected()) {
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

		switch (randomDrumHitsMultiplierOnGenerate.getSelectedIndex()) {
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
		if (PUNCHY_DRUMS.contains(ip.getInstrument())) {
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

		if (randomDrumUseChordFill.isSelected()) {
			ip.setChordSpanFill(ChordSpanFill.getWeighted(drumPanelGenerator.nextInt(100)));
		}
		ip.setFillFlip(false);
		ip.setPatternFlip(false);

		ip.setIsVelocityPattern(drumPanelGenerator.nextInt(100) < Integer
				.valueOf(randomDrumVelocityPatternChance.getInt()));

		if (drumPanelGenerator.nextInt(100) < randomDrumShiftChance.getInt()
				&& pattern != RhythmPattern.FULL) {
			ip.setPatternShift(drumPanelGenerator.nextInt(ip.getPattern().pattern.length - 1) + 1);
			ip.getComboPanel().reapplyShift();
		}

		ip.getComboPanel().reapplyHits();
	}

	private int[] displayDrumPart(DrumPart dp, int chords, int maxPatternPerChord) {
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
		return singleWeightedSelectFromArray(MILISECOND_ARRAY_STRUM, randomChordStruminess.getInt(),
				1);
	}

	public Pair<StrumType, Integer> getRandomStrumPair() {
		StrumType sType = selectTypeByStrumminess(randomChordStruminess.getInt());
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
		/*List<? extends InstPanel> panels = getInstList(partNum);
		for (int i = 0; i < panels.size(); i++) {
			if (panels.get(i).getPanelOrder() == partOrder) {
				return i;
			}
		}*/
		List<Integer> allPanelOrders = getInstList(partNum).stream().map(e -> e.getPanelOrder())
				.collect(Collectors.toList());
		allPanelOrders.sort(Comparator.comparing(e -> e));
		if (allPanelOrders.contains(partOrder)) {
			return allPanelOrders.indexOf(partOrder);
		}
		throw new IllegalArgumentException("Absolute order not found!");
	}

	public static void pianoRoll() {
		if (MidiGenerator.LAST_SCORES.isEmpty()) {
			return;
		}
		if (scorePanel == null) {
			scorePanel = new ShowPanelBig();
			((JPanel) scoreScrollPane.getViewport().getView()).add(scorePanel);
		}
		ShowPanelBig.scoreBox.setSelectedIndex(0);

		scorePanel.setScore();
		scoreScrollPane.repaint();
	}


	public static boolean canRegenerateOnChange() {
		return sequencer != null && regenerateWhenValuesChange.isSelected()
				&& !heavyBackgroundTasksInProgress && arrSection.getSelectedIndex() == 0;
	}

	public static int calculateSectionMeasureStart(int sectIndex) {
		if (actualArrangement == null || actualArrangement.getSections() == null
				|| sliderMeasureStartTimes == null || sliderMeasureStartTimes.isEmpty()
				|| sectIndex < 0 || sectIndex > actualArrangement.getSections().size()) {
			return 0;
		}
		List<Section> secs = actualArrangement.getSections();
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
		if (moveStartToCustomizedSection == null || !moveStartToCustomizedSection.isSelected()
				|| sliderMeasureStartTimes == null)
			return;

		int measure = calculateSectionMeasureStart(sectIndex);
		int startSliderVal = sliderMeasureStartTimes.get(measure);
		setSliderStart(startSliderVal);
	}

	public static void recolorVariationPopupButton(int sectionOrder) {
		if (actualArrangement == null || sectionOrder - 1 >= actualArrangement.getSections().size())
			return;

		for (Component c : VibeComposerGUI.variationButtonsPanel.getComponents()) {
			if (c instanceof JButton) {
				JButton cbutt = (JButton) c;
				if (cbutt.getText().equals("Edit " + sectionOrder)) {
					VibeComposerGUI.recolorVariationPopupButton(cbutt,
							actualArrangement.getSections().get(sectionOrder - 1));
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

	public void toggleShowScorePopup() {
		if (scorePanel != null) {
			if (instrumentTabPane.getComponentCount() == 8) {
				instrumentTabPane.remove(scoreScrollPane);
				if (VibeComposerGUI.miniScorePopup.isSelected()) {
					ShowPanelBig.beatWidthBases = ShowPanelBig.beatWidthBasesSmall;
					ShowPanelBig.beatWidthBase = ShowPanelBig.beatWidthBases
							.get(ShowPanelBig.beatWidthBaseIndex);
					scorePanel.updatePanelHeight(300);
					//scoreScrollPane.setMaximumSize(new Dimension(600, 300));
					scorePanel.getShowArea().setNoteHeight(4);
					scorePanel.setScore();
					scorePanel.setAlignmentX(LEFT_ALIGNMENT);
					scoreScrollPane.repaint();
					SwingUtilities.invokeLater(() -> {
						ShowPanelBig.zoomIn(ShowPanelBig.areaScrollPane, new Point(0, 0), 0.0, 0.0);
					});
				}
				scorePopup = new ShowScorePopup(scoreScrollPane);
			} else {
				if (scorePopup != null) {
					scorePopup.close();
					scorePopup = null;
				}
				if (instrumentTabPane.getComponentCount() < 8) {
					instrumentTabPane.add(scoreScrollPane, 7);
					instrumentTabPane.setTitleAt(7, " Score ");
				}

			}
		}
	}

	public static long lastPlayedMs = 0;

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
			if (part < 4 && transposeNotePreview.isSelected()) {
				Pair<ScaleMode, Integer> scaleKey = keyChangeAt(
						actualArrangement.getSections().indexOf(sec));
				int extraTranspose = (part > 0) ? ip.getTranspose() : 0;
				List<Note> notes = Collections.singletonList(new Note(
						(part > 0) ? pitch : (pitch + ip.getTranspose()), durationMs / 1000.0));
				if (scaleKey != null) {
					boolean snapToScale = (scaleKey.getLeft() != ScaleMode.IONIAN)
							|| transposedNotesForceScale.isSelected();
					MidiUtils.transposeNotes(notes, ScaleMode.IONIAN.noteAdjustScale,
							scaleKey.getLeft().noteAdjustScale, snapToScale);
					extraTranspose += scaleKey.getRight();
				}

				pitch = notes.get(0).getPitch() + transposeScore.getInt() + extraTranspose
						+ sec.getTransposeVariation(part, partOrder);

				if (pitch < 0 || pitch > 127) {
					LG.d("Pitch too high to play: " + pitch);
					return;
				}
			}

			Track trk = sequencer.getSequence().getTracks()[trackNum];
			ShortMessage noteOnMsg = new ShortMessage();
			noteOnMsg.setMessage(ShortMessage.NOTE_ON, ip.getMidiChannel() - 1, pitch, velocity);
			ShortMessage noteOffMsg = new ShortMessage();
			noteOffMsg.setMessage(ShortMessage.NOTE_OFF, ip.getMidiChannel() - 1, pitch, 0);

			long startPos = (sequencer.isRunning()) ? sequencer.getTickPosition() : 0;

			int startDelayMicroseconds = 50000;
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
			queueMidiEventForRemoval(trackNum, noteOn);
		} catch (InvalidMidiDataException e) {
			e.printStackTrace();
		}
	}

	public static void queueMidiEventForRemoval(int trackNum, MidiEvent mve) {
		if (midiEventsToRemove.containsKey(trackNum)) {
			midiEventsToRemove.get(trackNum).add(mve);
		} else {
			List<MidiEvent> mves = new ArrayList<>();
			mves.add(mve);
			midiEventsToRemove.put(trackNum, mves);
		}
	}

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
		if (actualArrangement == null || actualArrangement.getSections() == null || sectionIndex < 0
				|| sectionIndex >= actualArrangement.getSections().size()) {
			return null;
		}

		ScaleMode lastMode = ScaleMode.valueOf(scaleMode.getVal());
		int lastKeyChange = 0;
		for (int i = 0; i < sectionIndex; i++) {
			Section sec = actualArrangement.getSections().get(i);
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
