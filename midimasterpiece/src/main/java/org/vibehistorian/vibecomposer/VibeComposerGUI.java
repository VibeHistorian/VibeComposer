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

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Track;
import javax.sound.midi.Transmitter;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vibehistorian.vibecomposer.InstUtils.POOL;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.Section.SectionType;
import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.KeyChangeType;
import org.vibehistorian.vibecomposer.Enums.PatternJoinMode;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Enums.StrumType;
import org.vibehistorian.vibecomposer.Helpers.CheckBoxIcon;
import org.vibehistorian.vibecomposer.Helpers.CheckButton;
import org.vibehistorian.vibecomposer.Helpers.CollectionCellRenderer;
import org.vibehistorian.vibecomposer.Helpers.FileTransferable;
import org.vibehistorian.vibecomposer.Helpers.MelodyMidiDropPane;
import org.vibehistorian.vibecomposer.Helpers.OMNI;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Helpers.PlayheadRangeSlider;
import org.vibehistorian.vibecomposer.Helpers.RandomValueButton;
import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;
import org.vibehistorian.vibecomposer.Helpers.SectionDropDownCheckButton;
import org.vibehistorian.vibecomposer.Helpers.ShowPanelBig;
import org.vibehistorian.vibecomposer.Helpers.VeloRect;
import org.vibehistorian.vibecomposer.Panels.ArpPanel;
import org.vibehistorian.vibecomposer.Panels.BassPanel;
import org.vibehistorian.vibecomposer.Panels.ButtonSelectorPanel;
import org.vibehistorian.vibecomposer.Panels.ChordGenSettings;
import org.vibehistorian.vibecomposer.Panels.ChordPanel;
import org.vibehistorian.vibecomposer.Panels.DetachedKnobPanel;
import org.vibehistorian.vibecomposer.Panels.DrumPanel;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;
import org.vibehistorian.vibecomposer.Panels.MelodyPanel;
import org.vibehistorian.vibecomposer.Panels.SettingsPanel;
import org.vibehistorian.vibecomposer.Panels.SoloMuter;
import org.vibehistorian.vibecomposer.Panels.SoloMuter.State;
import org.vibehistorian.vibecomposer.Panels.VisualPatternPanel;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.BassPart;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.DrumPartsWrapper;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Parts.Defaults.DrumDefaults;
import org.vibehistorian.vibecomposer.Parts.Defaults.DrumSettings;
import org.vibehistorian.vibecomposer.Popups.AboutPopup;
import org.vibehistorian.vibecomposer.Popups.ApplyCustomSectionPopup;
import org.vibehistorian.vibecomposer.Popups.ArrangementPartInclusionPopup;
import org.vibehistorian.vibecomposer.Popups.DebugConsole;
import org.vibehistorian.vibecomposer.Popups.DrumLoopPopup;
import org.vibehistorian.vibecomposer.Popups.ExtraSettingsPopup;
import org.vibehistorian.vibecomposer.Popups.HelpPopup;
import org.vibehistorian.vibecomposer.Popups.ShowScorePopup;
import org.vibehistorian.vibecomposer.Popups.VariationPopup;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.sun.media.sound.AudioSynthesizer;

import jm.music.data.Phrase;
import jm.music.tools.Mod;

// main class

public class VibeComposerGUI extends JFrame
		implements ActionListener, ItemListener, WindowListener {

	private static final long serialVersionUID = -677536546851756969L;

	private static final Logger LOGGER = LoggerFactory.getLogger(VibeComposerGUI.class);

	private static final String MIDIS_FOLDER = "midis";
	private static final String MIDI_HISTORY_FOLDER = MIDIS_FOLDER + "/midi_history";
	private static final String PRESET_FOLDER = "presets";
	private static final String SOUNDBANK_FOLDER = ".";
	private static final String EXPORT_FOLDER = "exports";
	private static final String MID_EXTENSION = ".mid";

	public static final int[] MILISECOND_ARRAY_STRUM = { 0, 31, 62, 125, 125, 250, 333, 500, 666,
			750, 1000, 1500, 2000 };
	public static final int[] MILISECOND_ARRAY_DELAY = { 0, 62, 125, 250, 333 };
	public static final int[] MILISECOND_ARRAY_SPLIT = { 625, 750, 875 };

	public static VariationPopup varPopup = null;

	// COLORS
	public static Color panelColorHigh, panelColorLow;
	public static boolean isBigMonitorMode = false;
	public static boolean isDarkMode = true;
	private static boolean isFullMode = true;
	public static Color darkModeUIColor = Color.CYAN;
	public static Color lightModeUIColor = new Color(0, 90, 255);
	public static Color toggledUIColor = Color.cyan;

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

	private static GUIConfig guiConfig = new GUIConfig();

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

	public static List<? extends InstPanel> getInstList(int order) {
		if (order < 0 || order > 4) {
			throw new IllegalArgumentException("Inst list order wrong.");
		}
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
		return null;
	}

	public static JScrollPane getInstPane(int order) {
		if (order < 0 || order > 4) {
			throw new IllegalArgumentException("Inst list order wrong.");
		}
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
	public static ButtonSelectorPanel arrSection;
	JPanel arrangementMiddleColoredPanel;
	ScrollComboBox<String> newSectionBox;

	// instrument scrollers
	public static JTabbedPane instrumentTabPane = new JTabbedPane(JTabbedPane.TOP);
	public static JScrollPane scoreScrollPane;
	public static ShowPanelBig scorePanel;
	public static Dimension scrollPaneDimension = new Dimension(1600, 400);
	int arrangementRowHeaderWidth = 120;

	public static JScrollPane melodyScrollPane;
	public static JScrollPane bassScrollPane;
	public static JScrollPane chordScrollPane;
	public static JScrollPane arpScrollPane;
	public static JScrollPane drumScrollPane;
	JScrollPane arrangementScrollPane;
	JScrollPane arrangementActualScrollPane;
	public static JTable scrollableArrangementTable;
	public static JTable scrollableArrangementActualTable;
	public static boolean arrangementTableColumnDragging = false;
	public static boolean actualArrangementTableColumnDragging = false;

	JPanel actualArrangementCombinedPanel;
	JPanel arrangementCombinedPanel;
	public static JPanel variationButtonsPanel;

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
	ScrollComboBox<String> fixedLengthChords;
	ScrollComboBox<String> beatDurationMultiplier;
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
	JCheckBox extraSettingsReverseDrumPanels;
	JCheckBox extraSettingsOrderedTransposeGeneration;


	// add/skip instruments
	SettingsPanel chordSettingsPanel;
	SettingsPanel arpSettingsPanel;
	SettingsPanel drumSettingsPanel;
	JCheckBox addMelody;
	JCheckBox addBass;
	JCheckBox addChords;
	JCheckBox addArps;
	JCheckBox addDrums;
	VeloRect drumVolumeSlider;

	JButton soloAllDrums;

	// melody gen settings
	JButton randomizeMelodies;
	JCheckBox randomizeMelodiesOnCompose;
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
	public static ScrollComboBox<String> userMelodyScaleModeSelect;

	JCheckBox melody1ForcePatterns;
	JCheckBox melodyArpySurprises;
	JCheckBox melodySingleNoteExceptions;
	JCheckBox melodyFillPausesPerChord;
	JCheckBox melodyAvoidChordJumps;
	JCheckBox melodyUseDirectionsFromProgression;
	JCheckBox melodyPatternFlip;
	public static JCheckBox patternApplyPausesWhenGenerating;
	public static ScrollComboBox<String> melodyBlockTargetMode;
	JCheckBox melodyTargetNotesRandomizeOnCompose;
	ScrollComboBox<String> melodyPatternEffect;
	JCheckBox melodyPatternRandomizeOnCompose;
	KnobPanel melodyReplaceAvoidNotes;
	KnobPanel melodyMaxDirChanges;

	// bass gen settings
	// - there's nothing here - 

	// chord gen settings
	JButton chordAddJButton;
	JButton randomizeChords;
	JTextField randomChordsToGenerate;
	JCheckBox randomChordsGenerateOnCompose;
	JCheckBox randomChordDelay;
	JCheckBox randomChordStrum;
	KnobPanel randomChordStruminess;
	JCheckBox randomChordSplit;
	JCheckBox randomChordTranspose;
	JCheckBox randomChordPattern;
	JCheckBox randomChordSustainUseShortening;
	KnobPanel randomChordExpandChance;
	KnobPanel randomChordSustainChance;
	KnobPanel randomChordShiftChance;
	KnobPanel randomChordVoicingChance;
	KnobPanel randomChordMaxSplitChance;
	JCheckBox randomChordUseChordFill;
	ScrollComboBox<String> randomChordStretchType;
	ScrollComboBox<Integer> randomChordStretchPicker;
	KnobPanel randomChordMinVel;
	KnobPanel randomChordMaxVel;

	// arp gen settings
	JButton arpAddJButton;
	JButton randomizeArps;
	JTextField randomArpsToGenerate;
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
	JCheckBox randomArpUseOctaveAdjustments;
	KnobPanel randomArpMaxSwing;
	KnobPanel randomArpMaxRepeat;
	KnobPanel randomArpMinVel;
	KnobPanel randomArpMaxVel;
	KnobPanel randomArpMinLength;
	KnobPanel randomArpMaxLength;
	JCheckBox arpCopyMelodyInst;

	// drum gen settings
	public static List<Integer> PUNCHY_DRUMS = Arrays.asList(new Integer[] { 35, 36, 38, 39, 40 });
	public static List<Integer> KICK_DRUMS = Arrays.asList(new Integer[] { 35, 36 });
	public static List<Integer> SNARE_DRUMS = Arrays.asList(new Integer[] { 38, 40 });
	JButton drumAddJButton;
	JButton randomizeDrums;
	JTextField randomDrumsToGenerate;
	JCheckBox randomDrumsGenerateOnCompose;
	JCheckBox randomDrumsOverrandomize;
	JTextField randomDrumMaxSwingAdjust;
	JCheckBox randomDrumSlide;
	JCheckBox randomDrumPattern;
	KnobPanel randomDrumVelocityPatternChance;
	KnobPanel randomDrumShiftChance;
	JCheckBox randomDrumUseChordFill;
	JCheckBox arrangementScaleMidiVelocity;
	public static KnobPanel humanizeNotes;
	public static KnobPanel humanizeDrums;
	JCheckBox arrangementResetCustomPanelsOnCompose;
	ScrollComboBox<String> randomDrumHitsMultiplier;
	int randomDrumHitsMultiplierLastState = 1;
	public static JCheckBox drumCustomMapping;
	public static JTextField drumCustomMappingNumbers;


	// chord variety settings
	KnobPanel spiceChance;
	KnobPanel chordSlashChance;
	JCheckBox spiceAllowDimAug;
	JCheckBox spiceAllow9th13th;
	JCheckBox spiceFlattenBigChords;
	JCheckBox extraSquishChordsProgressively;
	KnobPanel spiceParallelChance;

	JCheckBox spiceForceScale;
	ScrollComboBox<String> firstChordSelection;
	ScrollComboBox<String> lastChordSelection;

	// chord settings - progression
	JCheckBox extraUseChordFormula;
	ScrollComboBox<String> keyChangeTypeSelection;
	public static CheckButton userChordsEnabled;
	public static JTextField userChords;
	public static JTextField userChordsDurations;

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

	// seed / midi
	RandomValueButton randomSeed;
	public static int lastRandomSeed = 0;
	double realBpm = 60;

	JList<File> generatedMidi;
	public static Sequencer sequencer = null;
	File currentMidi = null;
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
	public static boolean composingInProgress = false;

	Thread cycle;
	JCheckBox useMidiCC;
	CheckButton loopBeat;
	JCheckBox loopBeatCompose;
	public static PlayheadRangeSlider slider;
	public static List<Integer> sliderMeasureStartTimes = null;
	private List<Integer> sliderBeatStartTimes = null;

	JLabel currentTime;
	JLabel totalTime;
	JLabel sectionText;
	boolean isKeySeeking = false;
	public static boolean isDragging = false;
	private static long pausedSliderPosition = 0;
	private static int pausedMeasureCounter = 0;
	private static long startSliderPosition = 0;
	private static int startBeatCounter = 0;

	JLabel tipLabel;
	public static JLabel currentChords = new JLabel("Chords:[]");
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

	public static Map<Integer, SoloMuter> cpSm = null;
	public static Map<Integer, SoloMuter> apSm = null;
	public static Map<Integer, SoloMuter> dpSm = null;

	public static DebugConsole dconsole = null;
	public static VibeComposerGUI vibeComposerGUI = null;

	private static GridBagConstraints constraints = new GridBagConstraints();

	public static JPanel extraSettingsPanel;

	public static boolean isShowingTextInKnobs = true;
	public static JCheckBox displayVeloRectValues;
	public static JCheckBox highlightPatterns;
	public static JCheckBox highlightScoreNotes;
	public static JCheckBox customFilenameAddTimestamp;


	public static void main(String args[]) {
		FlatDarculaLaf.install();
		UIManager.put("CheckBox.icon", new CheckBoxIcon());

		isDarkMode = true;
		vibeComposerGUI = new VibeComposerGUI("VibeComposer (BETA)");
		vibeComposerGUI.init();
	}

	public VibeComposerGUI(String title) {
		super(title);
	}

	private void init() {
		long sysTime = System.currentTimeMillis();
		everythingPanel = new JPanel() {

			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
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

		initSoloMuters(20, GridBagConstraints.WEST);
		LOGGER.info("Titles, Extra, S/M " + (System.currentTimeMillis() - sysTime) + " ms!");
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
		LOGGER.info("Gen settings: " + (System.currentTimeMillis() - sysTime) + " ms!");
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
			LOGGER.info("Insts: " + (System.currentTimeMillis() - sysTime) + " ms!");

			constraints.gridy = 320;

			instrumentTabPane.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					int indx = instrumentTabPane.indexAtLocation(e.getX(), e.getY());
					if (SwingUtilities.isRightMouseButton(e) && indx < 5) {
						LOGGER.info(("RMB pressed in instrument tab pane: " + indx));
						switch (indx) {
						case 0:
							addMelody.setSelected(!addMelody.isSelected());
							break;
						case 1:
							addBass.setSelected(!addBass.isSelected());
							break;
						case 2:
							addChords.setSelected(!addChords.isSelected());
							/*instrumentTabPane.setEnabledAt(indx,
									!instrumentTabPane.isEnabledAt(indx));*/
							break;
						case 3:
							addArps.setSelected(!addArps.isSelected());
							break;
						case 4:
							addDrums.setSelected(!addDrums.isSelected());
							break;
						default:
							break;
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
		LOGGER.info("Arr: " + (System.currentTimeMillis() - sysTime) + " ms!");
		initScoreSettings(330, GridBagConstraints.CENTER);
		LOGGER.info("Scr: " + (System.currentTimeMillis() - sysTime) + " ms!");
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
		LOGGER.info(
				"Butts, params, cp, chords: " + (System.currentTimeMillis() - sysTime) + " ms!");

		//createHorizontalSeparator(400, this);

		// ---- CONTROL PANEL -----
		initControlPanel(410, GridBagConstraints.CENTER);


		// ---- PLAY PANEL ----
		initPlayPanel(420, GridBagConstraints.CENTER);
		initSliderPanel(440, GridBagConstraints.CENTER);
		LOGGER.info("Control, play, slider: " + (System.currentTimeMillis() - sysTime) + " ms!");
		// --- GENERATED MIDI DRAG n DROP ---

		constraints.anchor = GridBagConstraints.CENTER;

		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		int screenHeight = d.height;
		int screenWidth = d.width;
		setSize(screenWidth / 2, screenHeight / 2);
		setLocation(screenWidth / 4, screenHeight / 4);

		setFocusable(true);
		requestFocus();
		requestFocusInWindow();

		isDarkMode = !isDarkMode;


		everythingPane.setViewportView(everythingPanel);
		add(everythingPane, constraints);
		LOGGER.info("Add everything: " + (System.currentTimeMillis() - sysTime) + " ms!");
		setFullMode(isFullMode);
		LOGGER.info("Full: " + (System.currentTimeMillis() - sysTime) + " ms!");
		instrumentTabPane.setSelectedIndex(7);
		//instrumentTabPane.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		recalculateTabPaneCounts();
		switchDarkMode();
		pack();
		LOGGER.info("Dark, pack: " + (System.currentTimeMillis() - sysTime) + " ms!");

		if (presetLoadBox.getVal().equalsIgnoreCase("default")) {
			loadPreset();
		}


		setVisible(true);
		repaint();

		/*
		// switch pane using C/A/D (chords/arps/drums)
		
		KeyboardFocusManager.getCurrentKeyboardFocusManager()
				.addKeyEventDispatcher(new KeyEventDispatcher() {
					public boolean dispatchKeyEvent(KeyEvent e) {
						if (!soundbankFilename.isFocusOwner()) {
							if (e.getID() == KeyEvent.KEY_RELEASED) {
								if (e.getKeyCode() == KeyEvent.VK_C)
									instrumentTabPane.setSelectedIndex(0);
								else if (e.getKeyCode() == KeyEvent.VK_A)
									instrumentTabPane.setSelectedIndex(1);
								else if (e.getKeyCode() == KeyEvent.VK_D)
									instrumentTabPane.setSelectedIndex(2);
							}
						}
						return false;
					}
				});*/

		LOGGER.info("VibeComposer started in: " + (System.currentTimeMillis() - sysTime) + " ms!");
	}


	private void initTitles(int startY, int anchorSide) {
		mainTitle = new JLabel("Vibe Composer");
		mainTitle.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		subTitle = new JLabel("by Vibe Historian");
		subTitle.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

		mainTitle.setFont(new Font("Courier", Font.BOLD, 25));
		//subTitle.setFont(subTitle.getFont().deriveFont(Font.BOLD));
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
		globalReverbSlider = new VeloRect(0, 127, 96);
		globalChorusSlider = new VeloRect(0, 127, 48);

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
		extraSettingsPanel.setLayout(new BoxLayout(extraSettingsPanel, BoxLayout.Y_AXIS));

		mainButtonsPanel.add(makeButton("Extra", e -> openExtraSettingsPopup()));


		// ---- MESSAGE PANEL ----

		messageLabel = new JLabel("Click something!");
		messageLabel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		//mainButtonsPanel.add(messageLabel);

		presetLoadBox = new ScrollComboBox<String>(false);
		presetLoadBox.setEditable(true);
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


		mainButtonsPanel.add(presetLoadBox);
		mainButtonsPanel.add(makeButton("Load Preset", e -> loadPreset()));
		mainButtonsPanel.add(makeButton("Save Preset", e -> savePreset()));

		everythingPanel.add(mainButtonsPanel, constraints);
	}

	private void loadPreset() {
		String presetName = (String) presetLoadBox.getEditor().getItem();
		LOGGER.info("Trying to load preset: " + presetName);

		if (OMNI.EMPTYCOMBO.equalsIgnoreCase(presetName)) {
			return;
		} else {
			// check if file exists | special case: --- should load new GUIConfig()
			File loadedFile = new File(PRESET_FOLDER + "/" + presetName + ".xml");
			if (loadedFile.exists()) {
				try {
					GUIPreset preset = unmarshallPreset(loadedFile);
					guiConfig = preset;
					copyConfigToGUI();
					List<Component> presetComps = makeSettableComponentList();
					for (int i = 0; i < preset.getOrderedValuesUI().size(); i++) {
						setComponent(presetComps.get(i), preset.getOrderedValuesUI().get(i));
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
					manualArrangement.setSelected(false);
				} catch (JAXBException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return;
				}
			}
		}

		LOGGER.info("Loaded preset: " + presetName);
	}

	private void savePreset() {
		String presetName = (String) presetLoadBox.getEditor().getItem();
		LOGGER.info("Trying to save preset: " + presetName);
		if (!StringUtils.isAlphanumeric(presetName)) {
			return;
		}
		File makeSavedDir = new File(PRESET_FOLDER);
		makeSavedDir.mkdir();

		String filePath = PRESET_FOLDER + "/" + presetName + ".xml";
		saveGuiPresetFileByFilePath(filePath);
		presetLoadBox.addItem(presetName);
	}

	private void initExtraSettings() {
		JPanel arrangementExtraSettingsPanel = new JPanel();

		arrangementScaleMidiVelocity = new JCheckBox("Scale Midi Velocity in Arrangement", true);
		arrangementResetCustomPanelsOnCompose = new JCheckBox("Reset customized panels On Compose",
				true);

		useMidiCC = new JCheckBox("Use Volume/Pan/Reverb/Chorus/Filter/.. MIDI CC", true);
		useMidiCC.setToolTipText("Volume - 7, Reverb - 91, Chorus - 93, Filter - 74");
		//RandomIntegerListButton bt = new RandomIntegerListButton("0,1,2,3");
		arrangementExtraSettingsPanel.add(arrangementScaleMidiVelocity);
		arrangementExtraSettingsPanel.add(useMidiCC);
		arrangementExtraSettingsPanel.add(arrangementResetCustomPanelsOnCompose);
		//arrangementExtraSettingsPanel.add(bt);

		JPanel humanizationPanel = new JPanel();

		// / 10000
		humanizeNotes = new KnobPanel("Humanize Notes<br>/10000", 150, 0, 1000);
		humanizeNotes.setRegenerating(false);
		// / 10000
		humanizeDrums = new KnobPanel("Humanize Drums<br>/10000", 20, 0, 100);
		humanizeDrums.setRegenerating(false);
		humanizationPanel.add(humanizeNotes);
		humanizationPanel.add(humanizeDrums);

		extraSettingsPanel.add(arrangementExtraSettingsPanel);
		extraSettingsPanel.add(humanizationPanel);

		JPanel loopBeatExtraSettingsPanel = new JPanel();
		loopBeatCompose = new JCheckBox("Compose On Loop Repeat", false);
		loopBeatExtraSettingsPanel.add(loopBeatCompose);
		extraSettingsPanel.add(loopBeatExtraSettingsPanel);

		JPanel allInstsPanel = new JPanel();
		useAllInsts = new JCheckBox("Use All Inst., Except:", false);
		allInstsPanel.add(useAllInsts);
		bannedInsts = new JTextField("", 8);
		allInstsPanel.add(bannedInsts);
		reinitInstPools = makeButton("Initialize All Inst.", "InitAllInsts");
		allInstsPanel.add(reinitInstPools);
		extraSettingsPanel.add(allInstsPanel);


		JPanel pauseBehaviorPanel = new JPanel();
		pauseBehaviorLabel = new JLabel("Start From Pause:");
		pauseBehaviorCombobox = new ScrollComboBox<>(false);
		startFromBar = new JCheckBox("Start From Bar", true);
		rememberLastPos = new JCheckBox("Remember Last Pos.", true);
		snapStartToBeat = new JCheckBox("Snap Start To Beat", true);
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
		pauseBehaviorPanel.add(pauseBehaviorLabel);
		pauseBehaviorPanel.add(pauseBehaviorCombobox);
		pauseBehaviorPanel.add(startFromBar);
		pauseBehaviorPanel.add(rememberLastPos);
		pauseBehaviorPanel.add(snapStartToBeat);

		JPanel customDrumMappingPanel = new JPanel();
		drumCustomMapping = new JCheckBox("Custom Drum Mapping", true);
		drumCustomMappingNumbers = new JTextField(
				StringUtils.join(InstUtils.DRUM_INST_NUMBERS_SEMI, ","));
		melodyPatternFlip = new JCheckBox("Inverse Melody1 Pattern", false);
		patternApplyPausesWhenGenerating = new JCheckBox("Apply Pause% on Generate", true);

		customDrumMappingPanel.add(drumCustomMapping);
		customDrumMappingPanel.add(drumCustomMappingNumbers);
		customDrumMappingPanel.add(melodyPatternFlip);
		customDrumMappingPanel.add(patternApplyPausesWhenGenerating);
		drumCustomMapping.setToolTipText(
				"<html>" + StringUtils.join(InstUtils.DRUM_INST_NAMES_SEMI, "|") + "</html>");

		extraSettingsPanel.add(pauseBehaviorPanel);
		extraSettingsPanel.add(customDrumMappingPanel);


		// CHORD SETTINGS 2
		keyChangeTypeSelection = new ScrollComboBox<String>(false);
		ScrollComboBox.addAll(new String[] { "PIVOT", "TWOFIVEONE", "DIRECT" },
				keyChangeTypeSelection);
		keyChangeTypeSelection.setVal("TWOFIVEONE");
		keyChangeTypeSelection.addItemListener(this);

		JPanel chordChoicePanel = new JPanel();
		spiceFlattenBigChords = new JCheckBox("Spicy Voicing", false);
		extraUseChordFormula = new JCheckBox("Chord Formula", true);
		randomChordVoicingChance = new KnobPanel("Flatten<br>Voicing%", 100);
		extraSquishChordsProgressively = new JCheckBox("Flatten<br>Progressively", false);


		chordChoicePanel.add(extraUseChordFormula);
		chordChoicePanel.add(randomChordVoicingChance);
		chordChoicePanel.add(spiceFlattenBigChords);
		chordChoicePanel.add(extraSquishChordsProgressively);
		chordChoicePanel.add(new JLabel("Key change type:"));
		chordChoicePanel.add(keyChangeTypeSelection);
		extraSettingsPanel.add(chordChoicePanel);

		JPanel bpmLowHighPanel = new JPanel();

		arpAffectsBpm = new JCheckBox("BPM slowed by ARP", false);
		bpmLow = new KnobPanel("Min<br>BPM.", 50, 20, 249);
		bpmLow.setRegenerating(false);
		bpmHigh = new KnobPanel("Max<br>BPM.", 95, 21, 250);
		bpmHigh.setRegenerating(false);
		stretchMidi = new KnobPanel("Stretch MIDI%:", 100, 25, 400);
		stretchMidi.getKnob().setTickSpacing(25);
		stretchMidi.getKnob().setTickThresholds(
				Arrays.asList(new Integer[] { 25, 50, 100, 150, 200, 300, 400 }));
		bpmLowHighPanel.add(bpmLow);
		bpmLowHighPanel.add(bpmHigh);
		bpmLowHighPanel.add(arpAffectsBpm);
		bpmLowHighPanel.add(stretchMidi);

		extraSettingsPanel.add(bpmLowHighPanel);

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
		JLabel soundbankLabel = new JLabel("Soundbank name:");
		soundbankPanel.add(soundbankLabel);
		soundbankPanel.add(soundbankFilename);
		extraSettingsPanel.add(soundbankPanel);

		JPanel displayStylePanel = new JPanel();
		displayVeloRectValues = new JCheckBox("Display Bar Values", true);
		highlightPatterns = new JCheckBox("Highlight Sequencer Pattern", true);
		highlightScoreNotes = new JCheckBox("Highlight Score Notes", true);
		customFilenameAddTimestamp = new JCheckBox("Add Timestamp To Custom Filenames", false);
		displayVeloRectValues.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				VibeComposerGUI.this.repaint();
			}
		});

		JCheckBox checkbutt = new JCheckBox("Show Knob Texts", isShowingTextInKnobs);
		checkbutt.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				isShowingTextInKnobs = !isShowingTextInKnobs;
				for (int i = 0; i < 5; i++) {
					List<? extends InstPanel> panels = getInstList(i);
					panels.forEach(ipanel -> ipanel.toggleComponentTexts(isShowingTextInKnobs));
				}
			}

		});
		displayStylePanel.add(checkbutt);
		displayStylePanel.add(displayVeloRectValues);
		displayStylePanel.add(highlightPatterns);
		displayStylePanel.add(highlightScoreNotes);
		displayStylePanel.add(customFilenameAddTimestamp);
		extraSettingsPanel.add(displayStylePanel);


		JPanel panelGenerationSettingsPanel = new JPanel();
		extraSettingsReverseDrumPanels = new JCheckBox("Bottom-Top Drum Display", false);
		extraSettingsReverseDrumPanels.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent e) {
				for (DrumPanel dp : drumPanels) {
					dp.setVisible(false);
					((JPanel) getInstPane(4).getViewport().getView()).remove(dp);

				}
				List<DrumPanel> sortedDps = new ArrayList<>(drumPanels);
				Collections.sort(sortedDps,
						(e1, e2) -> Integer.compare(e1.getPanelOrder(), e2.getPanelOrder()));
				for (DrumPanel dp : sortedDps) {
					if (!extraSettingsReverseDrumPanels.isSelected()) {
						((JPanel) getInstPane(4).getViewport().getView()).add(dp);
					} else {
						((JPanel) getInstPane(4).getViewport().getView()).add(dp, 2);
					}
					dp.setVisible(true);
				}
			}
		});

		extraSettingsOrderedTransposeGeneration = new JCheckBox("Ordered Transpose Generation",
				true);


		panelGenerationSettingsPanel.add(extraSettingsReverseDrumPanels);
		panelGenerationSettingsPanel.add(extraSettingsOrderedTransposeGeneration);
		extraSettingsPanel.add(panelGenerationSettingsPanel);

		initHelperPopups();
	}

	private void initSoloMuters(int startY, int anchorSide) {
		JPanel soloMuterPanel = new JPanel();
		soloMuterPanel.setOpaque(false);
		JLabel emptySmLabel = new JLabel("");
		emptySmLabel.setPreferredSize(new Dimension(1, 3));
		soloMuterPanel.add(emptySmLabel);

		groupSoloMuters = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			SoloMuter sm = new SoloMuter(i, SoloMuter.Type.GROUP);
			groupSoloMuters.add(sm);
			soloMuterPanel.add(sm);
		}

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(soloMuterPanel, constraints);
	}


	private void initMelodyGenSettings(int startY, int anchorSide) {

		JPanel scrollableMelodyPanels = new JPanel();
		scrollableMelodyPanels.setLayout(new BoxLayout(scrollableMelodyPanels, BoxLayout.Y_AXIS));
		scrollableMelodyPanels.setAutoscrolls(true);

		melodyScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};
		melodyScrollPane.setViewportView(scrollableMelodyPanels);
		melodyScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		melodyScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		//melodySettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		melodyUseOldAlgoChance = new KnobPanel("Legacy<br>Algo", 0);

		randomChordNote = new JCheckBox();
		randomChordNote.setSelected(true);
		melodyFirstNoteFromChord = new JCheckBox();
		melodyFirstNoteFromChord.setSelected(true);


		//melodySettingsPanel.add(new JLabel("Note#1 From Chord:"));
		//melodySettingsPanel.add(melodyFirstNoteFromChord);
		//melodySettingsPanel.add(new JLabel("But Randomized:"));
		//melodySettingsPanel.add(randomChordNote);

		// ---- EXTRA -----
		JPanel melodySettingsExtraPanelsHolder = new JPanel();
		melodySettingsExtraPanelsHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
		melodySettingsExtraPanelsHolder.setLayout(new GridLayout(0, 1, 0, 0));
		melodySettingsExtraPanelsHolder.setMaximumSize(new Dimension(1800, 100));

		JPanel melodySettingsExtraPanelShape = new JPanel();
		melodySettingsExtraPanelShape.setAlignmentX(Component.LEFT_ALIGNMENT);
		melodySettingsExtraPanelShape.setMaximumSize(new Dimension(1800, 50));
		JLabel melodyExtraLabel2 = new JLabel("MELODY SETTINGS+");
		melodyExtraLabel2.setPreferredSize(new Dimension(120, 30));
		melodyExtraLabel2.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		melodySettingsExtraPanelShape.add(melodyExtraLabel2);

		melodyBasicChordsOnly = new JCheckBox("<html>Base<br> Chords</html>", false);
		melodyChordNoteTarget = new KnobPanel("Chord Note<br> Target%", 40);
		melodyTonicNoteTarget = new KnobPanel("Tonic Note<br> Target%", 20);
		melodyEmphasizeKey = new JCheckBox("<html>Emphasize<br> Key</html>", true);
		melodyModeNoteTarget = new KnobPanel("Mode Note<br> Target%", 15);
		melodyArpySurprises = new JCheckBox("<html>Insert<br> Arps</html>", false);
		melodySingleNoteExceptions = new JCheckBox("<html>Single Note<br>Exceptions</html>", true);
		melodyFillPausesPerChord = new JCheckBox("<html>Fill Pauses<br>Per Chord</html>", true);
		melodyAvoidChordJumps = new JCheckBox("<html>Avoid<br>Chord Jumps</html>", true);
		melodyUseDirectionsFromProgression = new JCheckBox("<html>Use Chord<br>Directions</html>",
				false);
		melodyBlockTargetMode = new ScrollComboBox<>();
		ScrollComboBox.addAll(
				new String[] { "#. Chord Note", "Chord Root + #", "MIDI 60 (C4) + #" },
				melodyBlockTargetMode);
		melodyBlockTargetMode.setSelectedIndex(2);
		melodyTargetNotesRandomizeOnCompose = new JCheckBox(
				"<html>Randomize Targets<br> on Compose</html>", true);
		melodyPatternEffect = new ScrollComboBox<>();
		ScrollComboBox.addAll(new String[] { "Rhythm", "Notes", "Rhythm+Notes" },
				melodyPatternEffect);
		melodyPatternEffect.setSelectedIndex(2);
		melodyPatternRandomizeOnCompose = new JCheckBox(
				"<html>Randomize Pattern<br> on Compose</html>", true);

		melodyReplaceAvoidNotes = new KnobPanel("Replace<br>Avoid Notes", 2, 0, 2);
		melodyMaxDirChanges = new KnobPanel("Max. Dir.<br>Changes", 2, 1, 6);

		melodySettingsExtraPanelShape.add(melodyBasicChordsOnly);
		melodySettingsExtraPanelShape.add(melodyChordNoteTarget);
		melodySettingsExtraPanelShape.add(melodyTonicNoteTarget);
		melodySettingsExtraPanelShape.add(melodyEmphasizeKey);
		melodySettingsExtraPanelShape.add(melodyModeNoteTarget);
		melodySettingsExtraPanelShape.add(melodyReplaceAvoidNotes);
		melodySettingsExtraPanelShape.add(melodyMaxDirChanges);
		melodySettingsExtraPanelShape.add(melodyArpySurprises);
		melodySettingsExtraPanelShape.add(melodySingleNoteExceptions);
		melodySettingsExtraPanelShape.add(melodyFillPausesPerChord);
		//melodySettingsExtraPanelShape.add(melodyAvoidChordJumps);

		JPanel melodySettingsExtraPanelBlocksPatternsCompose = new JPanel();
		melodySettingsExtraPanelBlocksPatternsCompose.setAlignmentX(Component.LEFT_ALIGNMENT);
		melodySettingsExtraPanelBlocksPatternsCompose.setMaximumSize(new Dimension(1800, 50));
		JLabel melodyExtraLabel3 = new JLabel("MELODY SETTINGS++");
		melodyExtraLabel3.setPreferredSize(new Dimension(120, 30));
		melodyExtraLabel3.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		melodySettingsExtraPanelBlocksPatternsCompose.add(melodyExtraLabel3);


		melodySettingsExtraPanelBlocksPatternsCompose.add(melodyUseDirectionsFromProgression);
		melodySettingsExtraPanelBlocksPatternsCompose.add(new JLabel("Target Mode"));
		melodySettingsExtraPanelBlocksPatternsCompose.add(melodyBlockTargetMode);
		melodySettingsExtraPanelBlocksPatternsCompose.add(melodyTargetNotesRandomizeOnCompose);
		melodySettingsExtraPanelBlocksPatternsCompose.add(new JLabel("Pattern Effect"));
		melodySettingsExtraPanelBlocksPatternsCompose.add(melodyPatternEffect);
		melodySettingsExtraPanelBlocksPatternsCompose.add(melodyPatternRandomizeOnCompose);

		JPanel melodySettingsExtraPanelOrg = new JPanel();
		melodySettingsExtraPanelOrg.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		melodySettingsExtraPanelOrg.setAlignmentX(Component.LEFT_ALIGNMENT);
		melodySettingsExtraPanelOrg.setMaximumSize(new Dimension(1800, 50));

		addMelody = new JCheckBox("MELODY", true);
		melodySettingsExtraPanelOrg.add(addMelody);
		groupFilterSliders[0] = new VeloRect(0, 127, 127);
		JLabel filterLabel = new JLabel("LP");
		melodySettingsExtraPanelOrg.add(filterLabel);
		melodySettingsExtraPanelOrg.add(groupFilterSliders[0]);

		randomizeMelodies = makeButton("Randomize Melodies",
				e -> createRandomMelodyPanels(new Random().nextInt()));
		melodySettingsExtraPanelOrg.add(randomizeMelodies);
		randomizeMelodiesOnCompose = new JCheckBox("On Compose", false);
		melodySettingsExtraPanelOrg.add(randomizeMelodiesOnCompose);

		JButton generateUserMelodySeed = makeButton("Randomize Seed", e -> randomizeMelodySeeds());
		JButton clearUserMelodySeed = makeButton("Clear Seed",
				e -> getAffectedPanels(0).forEach(m -> m.setPatternSeed(0)));
		randomMelodySameSeed = new JCheckBox("Same#", true);
		randomMelodyOnRegenerate = new JCheckBox("On regen", false);
		melody1ForcePatterns = new JCheckBox("<html>Force Melody#1<br> Outline</html>", true);

		MelodyMidiDropPane dropPane = new MelodyMidiDropPane();
		dropPane.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		useUserMelody = new JCheckBox("<html>Use MIDI<br>Melody File</html>", true);
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
									ScaleMode.IONIAN.noteAdjustScale);
							VibeComposerGUI.transposeScore.setInt(transposeUpBy * -1);
							VibeComposerGUI.scaleMode.setVal(toMode.toString());
							MelodyMidiDropPane.userMelody = melody;
						}
						userMelodyScaleModeSelect.setSelectedIndex(0);
					}
				}
			}

		});

		combineMelodyTracks = new JCheckBox("<html>Combine<br>MIDI Tracks</html>", false);
		combineMelodyTracks.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent e) {
				for (int i = 1; i < melodyPanels.size(); i++) {
					melodyPanels.get(i)
							.toggleCombinedMelodyDisabledUI(!combineMelodyTracks.isSelected());
				}
			}
		});

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

		melodySettingsExtraPanelsHolder.add(melodySettingsExtraPanelShape);
		melodySettingsExtraPanelsHolder.add(melodySettingsExtraPanelBlocksPatternsCompose);


		//scrollableMelodyPanels.add(melodySettingsPanel);
		scrollableMelodyPanels.add(melodySettingsExtraPanelOrg);
		scrollableMelodyPanels.add(melodySettingsExtraPanelsHolder);
		//addHorizontalSeparatorToPanel(scrollableMelodyPanels);

		toggleableComponents.add(melodySettingsExtraPanelsHolder);
	}

	private void initMelody(int startY, int anchorSide) {

		for (int i = 0; i < 3; i++) {
			MelodyPanel melodyPanel = new MelodyPanel(this);
			((JPanel) melodyScrollPane.getViewport().getView()).add(melodyPanel);
			melodyPanel.setInstrument(8);
			melodyPanels.add(melodyPanel);
			melodyPanel.setPanelOrder(i + 1);
			if (i > 0) {

				melodyPanel.setFillPauses(true);
				melodyPanel.setSpeed(0);
				melodyPanel.setPauseChance(80);

				if (i > 1) {
					melodyPanel.setMuteInst(true);
				}
				melodyPanel.toggleCombinedMelodyDisabledUI(
						combineMelodyTracks != null && !combineMelodyTracks.isSelected());
				melodyPanel.setVelocityMax(75);
				melodyPanel.setVelocityMin(50);
				melodyPanel.setMidiChannel(i + 6);
				if (i % 2 == 1) {
					melodyPanel.setTranspose(0);
					melodyPanel.getPanSlider().setValue(75);
				} else {
					melodyPanel.setTranspose(-12);
					melodyPanel.getPanSlider().setValue(25);
				}
				melodyPanel.getVolSlider().setValue(55);
			} else {
				melodyPanel.setFillPauses(false);
				melodyPanel.setSpeed(50);
				melodyPanel.setPauseChance(20);
				melodyPanel.setTranspose(12);
				melodyPanel.setVelocityMax(105);
				melodyPanel.setVelocityMin(65);
				melodyPanel.setNoteLengthMultiplier(115);
			}
		}


		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Melody", melodyScrollPane);


	}


	private void initBass(int startY, int anchorSide) {
		BassPanel bassPanel = new BassPanel(this);

		JPanel scrollableBassPanels = new JPanel();
		scrollableBassPanels.setLayout(new BoxLayout(scrollableBassPanels, BoxLayout.Y_AXIS));
		scrollableBassPanels.setAutoscrolls(true);

		bassScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};
		bassScrollPane.setViewportView(scrollableBassPanels);
		bassScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		bassScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		JPanel bassSettingsPanel = new JPanel();
		addBass = new JCheckBox("BASS", true);
		bassSettingsPanel.add(addBass);
		bassSettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		bassSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		bassSettingsPanel.setMaximumSize(new Dimension(1800, 50));

		groupFilterSliders[1] = new VeloRect(0, 127, 127);
		JLabel filterLabel = new JLabel("LP");
		bassSettingsPanel.add(filterLabel);
		bassSettingsPanel.add(groupFilterSliders[1]);

		JPanel bassSettingsAdvancedPanel = new JPanel();
		bassSettingsAdvancedPanel.add(new JLabel("BASS SETTINGS+"));
		//bassSettingsAdvancedPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		bassSettingsAdvancedPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		bassSettingsAdvancedPanel.setMaximumSize(new Dimension(1800, 50));

		scrollableBassPanels.add(bassSettingsPanel);
		scrollableBassPanels.add(bassSettingsAdvancedPanel);
		scrollableBassPanels.add(bassPanel);
		bassSettingsAdvancedPanel.setVisible(false);


		bassPanels.add(bassPanel);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Bass", bassScrollPane);
	}

	private void initChordGenSettings(int startY, int anchorSide) {
		JPanel scrollableChordPanels = new JPanel();
		scrollableChordPanels.setLayout(new BoxLayout(scrollableChordPanels, BoxLayout.Y_AXIS));
		scrollableChordPanels.setAutoscrolls(true);

		chordScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};
		chordScrollPane.setViewportView(scrollableChordPanels);
		chordScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		chordScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		JPanel chordSettingsPanel = new JPanel();
		chordSettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

		addChords = new JCheckBox("CHORDS", true);
		chordSettingsPanel.add(addChords);
		groupFilterSliders[2] = new VeloRect(0, 127, 127);
		JLabel filterLabel = new JLabel("LP");
		chordSettingsPanel.add(filterLabel);
		chordSettingsPanel.add(groupFilterSliders[2]);

		chordAddJButton = makeButton("+Chord", "AddChord");
		chordSettingsPanel.add(chordAddJButton);

		randomChordsToGenerate = new JTextField("3", 2);
		randomizeChords = makeButton("Generate Chords:", e -> {
			createPanels(2, Integer.valueOf(randomChordsToGenerate.getText()), false);
			recalculateTabPaneCounts();
			recalculateSoloMuters();
		});
		randomChordsGenerateOnCompose = new JCheckBox("on Compose", true);
		chordSettingsPanel.add(randomizeChords);
		chordSettingsPanel.add(randomChordsToGenerate);
		chordSettingsPanel.add(randomChordsGenerateOnCompose);


		randomChordDelay = new JCheckBox("Delay", false);
		randomChordStrum = new JCheckBox("", true);
		randomChordStruminess = new DetachedKnobPanel("Struminess", 50);
		randomChordSplit = new JCheckBox("Use Split (ms)", false);
		randomChordTranspose = new JCheckBox("Transpose", true);
		randomChordSustainChance = new DetachedKnobPanel("Chord%", 50);
		randomChordSustainUseShortening = new JCheckBox("Vary Length", true);
		randomChordExpandChance = new DetachedKnobPanel("Expand%", 70);
		randomChordUseChordFill = new JCheckBox("Fills", true);
		randomChordMaxSplitChance = new DetachedKnobPanel("Max Tran-<br>sition%", 25);
		chordSlashChance = new KnobPanel("Chord1<br>Slash%", 30);
		randomChordPattern = new JCheckBox("Patterns", true);
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

		randomChordStretchType = new ScrollComboBox<>();
		ScrollComboBox.addAll(new String[] { "NONE", "FIXED", "AT_MOST" }, randomChordStretchType);
		randomChordStretchType.setVal("AT_MOST");
		JLabel stretchLabel = new JLabel("VOICES");
		chordSettingsPanel.add(stretchLabel);
		chordSettingsPanel.add(randomChordStretchType);
		randomChordStretchPicker = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new Integer[] { 3, 4, 5, 6 }, randomChordStretchPicker);
		randomChordStretchPicker.setVal(5);
		chordSettingsPanel.add(randomChordStretchPicker);

		JButton clearChordPatternSeeds = makeButton("Clear presets", "ClearChordPatterns");

		JPanel chordSettingsExtraPanel = new JPanel();
		JLabel csExtra = new JLabel("CHORD SETTINGS+");
		csExtra.setPreferredSize(new Dimension(120, 30));
		csExtra.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		chordSettingsExtraPanel.add(csExtra);

		chordSettingsExtraPanel.add(randomChordSustainChance);
		chordSettingsExtraPanel.add(randomChordSustainUseShortening);
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
		scrollableChordPanels.add(chordSettingsPanel);
		chordSettingsExtraPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		chordSettingsExtraPanel.setMaximumSize(new Dimension(1800, 50));
		//constraints.gridy = startY + 1;
		scrollableChordPanels.add(chordSettingsExtraPanel);
		//addHorizontalSeparatorToPanel(scrollableChordPanels);
	}

	private void initChords(int startY, int anchorSide) {
		// ---- CHORDS ----
		// gridy 50 - 99 range


		createRandomChordPanels(Integer.valueOf(randomChordsToGenerate.getText()), false, null);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Chords", chordScrollPane);
	}

	private void initArpGenSettings(int startY, int anchorSide) {
		JPanel scrollableArpPanels = new JPanel();
		scrollableArpPanels.setLayout(new BoxLayout(scrollableArpPanels, BoxLayout.Y_AXIS));
		scrollableArpPanels.setAutoscrolls(true);

		arpScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				//LOGGER.info(("Size: " + scrollPaneDimension.toString()));
				return scrollPaneDimension;
			}
		};
		arpScrollPane.setViewportView(scrollableArpPanels);
		arpScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		arpScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		JPanel arpsSettingsPanel = new JPanel();
		arpsSettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		addArps = new JCheckBox("ARPS", true);
		arpsSettingsPanel.add(addArps);
		groupFilterSliders[3] = new VeloRect(0, 127, 127);
		JLabel filterLabel = new JLabel("LP");
		arpsSettingsPanel.add(filterLabel);
		arpsSettingsPanel.add(groupFilterSliders[3]);

		arpAddJButton = makeButton("  +Arp ", "AddArp");
		arpsSettingsPanel.add(arpAddJButton);

		randomArpsToGenerate = new JTextField("4", 2);
		randomizeArps = makeButton("Generate Arps:    ", e -> {
			createPanels(3, Integer.valueOf(randomArpsToGenerate.getText()), false);
			recalculateTabPaneCounts();
			recalculateSoloMuters();
		});
		randomArpsGenerateOnCompose = new JCheckBox("on Compose", true);
		arpsSettingsPanel.add(randomizeArps);
		arpsSettingsPanel.add(randomArpsToGenerate);
		arpsSettingsPanel.add(randomArpsGenerateOnCompose);


		randomArpTranspose = new JCheckBox("Transpose", true);
		randomArpPattern = new JCheckBox("Patterns", true);
		randomArpHitsPicker = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new Integer[] { 1, 2, 3, 4, 5, 6, 7, 8 }, randomArpHitsPicker);
		randomArpHitsPicker.setVal(4);
		randomArpHitsPerPattern = new JCheckBox("Random#", true);
		randomArpAllSameInst = new JCheckBox("One Inst.", false);
		randomArpAllSameHits = new JCheckBox("One #", true);
		randomArpLimitPowerOfTwo = new JCheckBox("<html>Limit 2<sup>n</sup>", true);
		randomArpUseChordFill = new JCheckBox("Fills", true);
		randomArpShiftChance = new DetachedKnobPanel("Shift%", 50);
		randomArpUseOctaveAdjustments = new JCheckBox("Rand. Oct.", false);
		randomArpMaxSwing = new KnobPanel("Swing%", 50);
		randomArpMaxRepeat = new DetachedKnobPanel("Max<br>Repeat", 2, 1, 4);
		randomArpMinVel = new DetachedKnobPanel("Min<br>Vel", 65, 0, 126);
		randomArpMaxVel = new DetachedKnobPanel("Max<br>Vel", 90, 1, 127);
		randomArpMinLength = new DetachedKnobPanel("Min<br>Length", 75, 25, 200);
		randomArpMaxLength = new DetachedKnobPanel("Max<br>Length", 100, 25, 200);

		arpsSettingsPanel.add(new JLabel("Arp#"));
		arpsSettingsPanel.add(randomArpHitsPicker);
		arpsSettingsPanel.add(randomArpHitsPerPattern);
		arpsSettingsPanel.add(randomArpAllSameHits);
		arpsSettingsPanel.add(randomArpUseChordFill);

		arpsSettingsPanel.add(randomArpTranspose);

		randomArpStretchType = new ScrollComboBox<>();
		ScrollComboBox.addAll(new String[] { "NONE", "FIXED", "AT_MOST" }, randomArpStretchType);
		randomArpStretchType.setVal("AT_MOST");
		JLabel stretchLabel = new JLabel("VOICES");
		arpsSettingsPanel.add(stretchLabel);
		arpsSettingsPanel.add(randomArpStretchType);
		randomArpStretchPicker = new ScrollComboBox<>();
		ScrollComboBox.addAll(new Integer[] { 3, 4, 5, 6 }, randomArpStretchPicker);
		randomArpStretchPicker.setVal(4);
		arpsSettingsPanel.add(randomArpStretchPicker);


		toggleableComponents.add(stretchLabel);
		toggleableComponents.add(randomArpStretchType);
		toggleableComponents.add(randomArpStretchPicker);

		JButton clearArpPatternSeeds = makeButton("Clear Patterns", "ClearArpPatterns");
		JPanel arpSettingsExtraPanel = new JPanel();
		JLabel csExtra = new JLabel("ARP SETTINGS+");
		csExtra.setPreferredSize(new Dimension(120, 30));
		csExtra.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		arpSettingsExtraPanel.add(csExtra);

		arpCopyMelodyInst = new JCheckBox("Arp#1 Copy Melody Inst.", true);

		arpSettingsExtraPanel.add(arpCopyMelodyInst);
		arpSettingsExtraPanel.add(randomArpAllSameInst);
		arpSettingsExtraPanel.add(randomArpLimitPowerOfTwo);
		arpSettingsExtraPanel.add(randomArpUseOctaveAdjustments);
		arpSettingsExtraPanel.add(randomArpMaxSwing);
		arpSettingsExtraPanel.add(randomArpMaxRepeat);
		arpSettingsExtraPanel.add(randomArpMinVel);
		arpSettingsExtraPanel.add(randomArpMaxVel);
		arpSettingsExtraPanel.add(randomArpPattern);
		arpSettingsExtraPanel.add(randomArpShiftChance);
		arpSettingsExtraPanel.add(randomArpMinLength);
		arpSettingsExtraPanel.add(randomArpMaxLength);
		arpSettingsExtraPanel.add(clearArpPatternSeeds);
		toggleableComponents.add(arpSettingsExtraPanel);


		arpsSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		arpsSettingsPanel.setMaximumSize(new Dimension(1800, 50));
		scrollableArpPanels.add(arpsSettingsPanel);
		arpSettingsExtraPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		arpSettingsExtraPanel.setMaximumSize(new Dimension(1800, 50));
		//constraints.gridy = startY + 1;
		scrollableArpPanels.add(arpSettingsExtraPanel);
		//addHorizontalSeparatorToPanel(scrollableArpPanels);
	}

	private void initArps(int startY, int anchorSide) {
		// --- ARPS -----------


		createRandomArpPanels(Integer.valueOf(randomArpsToGenerate.getText()), false, null);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Arps", arpScrollPane);
	}

	private void initDrumGenSettings(int startY, int anchorSide) {
		JPanel scrollableDrumPanels = new JPanel();
		scrollableDrumPanels.setLayout(new BoxLayout(scrollableDrumPanels, BoxLayout.Y_AXIS));
		scrollableDrumPanels.setAutoscrolls(true);

		drumScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};
		drumScrollPane.setViewportView(scrollableDrumPanels);

		drumScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		drumScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		JPanel drumsPanel = new JPanel();
		drumsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		addDrums = new JCheckBox("DRUMS", true);
		drumsPanel.add(addDrums);

		drumVolumeSlider = new VeloRect(0, 100, 80);
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

		drumAddJButton = makeButton(" +Drum ", "AddDrum");
		drumsPanel.add(drumAddJButton);

		randomDrumsToGenerate = new JTextField("6", 2);
		randomizeDrums = makeButton("Generate Drums: ", e -> {
			createPanels(4, Integer.valueOf(randomDrumsToGenerate.getText()), false);
			recalculateTabPaneCounts();
			recalculateSoloMuters();
		});
		randomDrumsGenerateOnCompose = new JCheckBox("on Compose", true);
		drumsPanel.add(randomizeDrums);
		drumsPanel.add(randomDrumsToGenerate);
		drumsPanel.add(randomDrumsGenerateOnCompose);

		JButton clearPatternSeeds = makeButton("Clear Presets", "ClearPatterns");

		randomDrumMaxSwingAdjust = new JTextField("20", 2);
		randomDrumSlide = new JCheckBox("Random Delay", false);
		randomDrumUseChordFill = new JCheckBox("Fills", true);
		randomDrumPattern = new JCheckBox("Patterns", true);
		randomDrumVelocityPatternChance = new DetachedKnobPanel("Dynamic%", 50);
		randomDrumShiftChance = new DetachedKnobPanel("Shift%", 50);

		drumsPanel.add(new JLabel("Max swing%+-"));
		drumsPanel.add(randomDrumMaxSwingAdjust);
		drumsPanel.add(randomDrumUseChordFill);

		randomDrumHitsMultiplier = new ScrollComboBox<>();
		ScrollComboBox.addAll(new String[] { OMNI.EMPTYCOMBO, "0.5x", "1.5x", "2x" },
				randomDrumHitsMultiplier);
		randomDrumHitsMultiplier.setVal(OMNI.EMPTYCOMBO);
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
							affectedDrums.get(i).setHitsPerPattern(newHits * 3 / 2);
							break;
						case 3:
							affectedDrums.get(i).setHitsPerPattern(newHits * 2);
							break;
						default:
							throw new IllegalArgumentException(
									"Only 3 hits multiplier states allowed!");
						}
						if (randomDrumHitsMultiplier.getSelectedIndex() > 1
								&& affectedDrums.get(i).getPattern() == RhythmPattern.CUSTOM) {
							List<Integer> trueSub = affectedDrums.get(i).getComboPanel()
									.getTruePattern().subList(0, newHits);
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

		drumsPanel.add(new JLabel("Hits Multiplier:"));
		drumsPanel.add(randomDrumHitsMultiplier);
		drumsPanel.add(randomDrumSlide);
		ScrollComboBox<String> drumPartPresetBox = new ScrollComboBox<>();
		ScrollComboBox.addAll(new String[] { OMNI.EMPTYCOMBO, "POP", "DNB" }, drumPartPresetBox);
		drumPartPresetBox.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent event) {
				if (event.getStateChange() == ItemEvent.SELECTED) {
					String item = (String) event.getItem();
					if (OMNI.EMPTYCOMBO.equals(item)) {
						return;
					}
					InputStream is = VibeComposerGUI.class
							.getResourceAsStream("/drums/" + item + ".xml");
					try {
						unmarshallDrumsFromResource(is);
					} catch (JAXBException | IOException e) {
						// Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		});
		drumsPanel.add(new JLabel("Factory Presets:"));
		drumsPanel.add(drumPartPresetBox);


		JPanel drumExtraSettings = new JPanel();
		JLabel csExtra = new JLabel("DRUM SETTINGS+");
		csExtra.setPreferredSize(new Dimension(120, 30));
		csExtra.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		drumExtraSettings.add(csExtra);


		combineDrumTracks = new JCheckBox("Combine MIDI Tracks", true);
		combineDrumTracks.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent e) {
				drumPanels
						.forEach(f -> f.getSoloMuter().setVisible(!combineDrumTracks.isSelected()));
			}

		});
		drumExtraSettings.add(combineDrumTracks);

		drumExtraSettings.add(makeButton("Save Drums As", "DrumSave"));
		drumExtraSettings.add(makeButton("Load Drums", "DrumLoad"));

		drumExtraSettings.add(randomDrumPattern);
		drumExtraSettings.add(randomDrumVelocityPatternChance);

		drumExtraSettings.add(randomDrumShiftChance);
		drumExtraSettings.add(clearPatternSeeds);

		randomDrumsOverrandomize = new JCheckBox("Overrandomize", false);
		drumExtraSettings.add(randomDrumsOverrandomize);

		toggleableComponents.add(drumExtraSettings);

		drumsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		drumsPanel.setMaximumSize(new Dimension(1800, 50));
		scrollableDrumPanels.add(drumsPanel);
		drumExtraSettings.setAlignmentX(Component.LEFT_ALIGNMENT);
		drumExtraSettings.setMaximumSize(new Dimension(1800, 50));
		//constraints.gridy = startY + 1;
		scrollableDrumPanels.add(drumExtraSettings);
		//addHorizontalSeparatorToPanel(scrollableDrumPanels);
	}

	private void initDrums(int startY, int anchorSide) {


		createBlueprintedDrumPanels((Integer.valueOf(randomDrumsToGenerate.getText())), false,
				null);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Drums", drumScrollPane);

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
			refreshActual = true;
			resetArrSectionSelection = false;
			resetArrSectionPanel = false;
			//variationJD.getFrame().setTitle(action);
		} else if (action.startsWith("ArrangementApply")) {
			String selItem = arrSection.getVal();
			if (GLOBAL.equals(selItem)) {
				return;
			}

			Integer secOrder = Integer.valueOf(selItem.split(":")[0]);

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
				switch (instrumentTabPane.getSelectedIndex()) {
				case 0:
					sec.setMelodyParts(
							(List<MelodyPart>) (List<?>) getInstPartsFromCustomSectionInstPanels(
									0));
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
				if (instrumentTabPane.getSelectedIndex() < 5) {
					String suffix = "";
					if (sec.hasCustomizedParts()) {
						suffix = "*";
					}
					arrSection.getButtons().get(i).setText(i + ": " + sec.getType() + suffix);
					//resetArrSection();
					//arrSection.setSelectedIndex(secOrder);
					resetArrSectionSelection = false;
					resetArrSectionPanel = false;
					refreshActual = true;
					checkManual = true;
				}
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
			LOGGER.info(("add exact"));
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
		} else if (action.equalsIgnoreCase("ArrangementOpenPartInclusion")) {
			arrangement.recalculatePartInclusionMapBoundsIfNeeded();
			new ArrangementPartInclusionPopup(arrangement,
					new Point(MouseInfo.getPointerInfo().getLocation().x,
							vibeComposerGUI.getLocation().y),
					vibeComposerGUI.getSize());
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

	public static void openVariationPopup(int secOrder) {
		if (varPopup != null) {
			varPopup.getFrame().dispose();
		}
		recalculateActualArrangementSection(secOrder - 1);
		varPopup = new VariationPopup(secOrder, actualArrangement.getSections().get(secOrder - 1),
				new Point(MouseInfo.getPointerInfo().getLocation().x,
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
		useArrangement = new CheckButton("ARRANGE", false);
		arrangementSettings.add(useArrangement);
		pieceLength = new JTextField("12", 2);
		//arrangementSettings.add(new JLabel("Max Length:"));
		JButton resetArrangementBtn = makeButton("Reset Arr.", "ArrangementReset");
		JButton randomizeArrangementBtn = makeButton("Randomize", "ArrangementRandomize");
		JButton arrangementPartInclusionBtn = makeButton("Parts", "ArrangementOpenPartInclusion");

		randomizeArrangementOnCompose = new JCheckBox("on Compose", true);

		List<CheckButton> defaultButtons = new ArrayList<>();
		defaultButtons
				.add(new SectionDropDownCheckButton(GLOBAL, true, OMNI.alphen(Color.pink, 70)));
		arrSection = new ButtonSelectorPanel(new ArrayList<>(), defaultButtons);
		arrSection.addPropertyChangeListener("selectedIndex", new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				String selItem = arrSection.getVal();
				if (selItem == null || (arrSection.getItemCount() - 1 != actualArrangement
						.getSections().size())) {
					return;
				}
				List<InstPanel> addedPanels = new ArrayList<>();

				if (GLOBAL.equals(selItem)) {
					LOGGER.info(("Resetting to normal panels!"));
					arrangementMiddleColoredPanel.setBackground(panelColorHigh.brighter());
					for (int i = 0; i < 5; i++) {
						JScrollPane pane = getInstPane(i);
						List<? extends InstPanel> panels = getInstList(i);
						for (Component c : ((JPanel) pane.getViewport().getView())
								.getComponents()) {
							if (c instanceof InstPanel) {
								InstPanel ip = (InstPanel) c;
								//LOGGER.info(("Switching panel!"));
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
					LOGGER.info(("Switching panels!"));
					arrangementMiddleColoredPanel.setBackground(uiColor().darker().darker());
					int sectionOrder = Integer.valueOf(selItem.split(":")[0]) - 1;
					Section sec = actualArrangement.getSections().get(sectionOrder);
					for (int i = 0; i < 5; i++) {
						JScrollPane pane = getInstPane(i);
						List<InstPanel> sectionPanels = new ArrayList<>();
						if (sec.getInstPartList(i) != null) {
							//LOGGER.info(("Creating panels from section parts! " + i));
							List<? extends InstPart> ip = sec.getInstPartList(i);
							for (Component c : ((JPanel) pane.getViewport().getView())
									.getComponents()) {
								if (c instanceof InstPanel) {
									int order = ((InstPanel) c).getPanelOrder();
									((JPanel) pane.getViewport().getView()).remove(c);
									InstPanel pCopy = InstPanel.makeInstPanel(i,
											VibeComposerGUI.this);
									pCopy.setFromInstPart(
											ip.get(VibeComposerGUI.getAbsoluteOrder(i, order)));
									sectionPanels.add(pCopy);
								}
							}
						} else {
							//LOGGER.info(("Making copies of normal panels! " + i));
							List<? extends InstPanel> panels = getInstList(i);
							//Set<Integer> presence = sec.getPresence(i);
							for (Component c : ((JPanel) pane.getViewport().getView())
									.getComponents()) {
								if (c instanceof InstPanel) {
									InstPanel ip = (InstPanel) c;

									//LOGGER.info(("Switching panel!"));
									int order = ip.getPanelOrder();
									((JPanel) pane.getViewport().getView()).remove(ip);
									/*if (!presence.contains(ip.getPanelOrder())) {
										continue;
									}*/
									InstPanel p = panels.get(order - 1);
									InstPanel pCopy = InstPanel.makeInstPanel(i,
											VibeComposerGUI.this);
									pCopy.setFromInstPart(p.toInstPart(0));
									sectionPanels.add(pCopy);
								}

							}
						}

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
			}
		});

		JButton commitPanelBtn = makeButton("Apply", "ArrangementApply");
		commitPanelBtn.setMargin(new Insets(0, 0, 0, 0));
		JButton commitAllPanelBtn = makeButton("Apply..", e -> openApplyCustomSectionPopup());
		commitAllPanelBtn.setMargin(new Insets(0, 0, 0, 0));
		JButton undoPanelBtn = new JButton("<-*");
		undoPanelBtn.setPreferredSize(new Dimension(25, 25));
		undoPanelBtn.setMargin(new Insets(0, 0, 0, 0));
		undoPanelBtn.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				arrSection.setSelectedIndexWithProperty(arrSection.getSelectedIndex(), true);
				//resetArrSectionInBackground();
			}

		});

		JButton clearPanelBtn = new JButton("X*");
		clearPanelBtn.setPreferredSize(new Dimension(25, 25));
		clearPanelBtn.setMargin(new Insets(0, 0, 0, 0));
		clearPanelBtn.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (!GLOBAL.equals(arrSection.getVal())) {
					Section sec = actualArrangement.getSections()
							.get(arrSection.getSelectedIndex() - 1);
					if (sec.hasCustomizedParts()) {
						sec.resetCustomizedParts();
						setActualModel(actualArrangement.convertToActualTableModel(), false);
						CheckButton cb = arrSection.getButtons().get(arrSection.getSelectedIndex());
						cb.setText(cb.getText().substring(0, cb.getText().length() - 1));
						cb.repaint();
						arrSection.setSelectedIndexWithProperty(arrSection.getSelectedIndex(),
								true);
					}
				}
			}

		});
		JButton clearAllPanelsBtn = new JButton("CLR*");
		clearAllPanelsBtn.setPreferredSize(new Dimension(35, 25));
		clearAllPanelsBtn.setMargin(new Insets(0, 0, 0, 0));
		clearAllPanelsBtn.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				actualArrangement.getSections().forEach(s -> s.resetCustomizedParts());
				resetArrSectionInBackground();
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						arrSection.repaint();
					}
				});

			}

		});
		JButton copySelectedBtn = makeButton("Cc", "ArrangementAddLast");
		copySelectedBtn.setPreferredSize(new Dimension(25, 30));
		copySelectedBtn.setMargin(new Insets(0, 0, 0, 0));
		JButton removeSelectedBtn = makeButton("X", "ArrangementRemoveLast");
		newSectionBox = new ScrollComboBox<>(false);
		newSectionBox.addItem(OMNI.EMPTYCOMBO);
		for (SectionType type : Section.SectionType.values()) {
			newSectionBox.addItem(type.toString());
		}

		JButton addNewSectionBtn = makeButton("Add", "ArrangementAddNewSection");

		arrangementSettings.add(randomizeArrangementBtn);
		//arrangementSettings.add(pieceLength);
		arrangementSettings.add(randomizeArrangementOnCompose);

		arrangementVariationChance = new DetachedKnobPanel("Section<br>Variations", 30);
		arrangementSettings.add(arrangementVariationChance);
		arrangementPartVariationChance = new DetachedKnobPanel("Part<br>Variations", 25);
		arrangementSettings.add(arrangementPartVariationChance);
		arrangementSettings.add(resetArrangementBtn);
		arrangementSettings.add(arrangementPartInclusionBtn);

		arrangementMiddleColoredPanel = new JPanel();
		arrangementMiddleColoredPanel.add(new JLabel("                                      "));
		arrangementSettings.add(arrangementMiddleColoredPanel);


		manualArrangement = new CheckButton("MANUAL", false);
		arrangementSettings.add(manualArrangement);
		arrangementSettings.add(arrSection);
		arrangementSettings.add(commitPanelBtn);
		arrangementSettings.add(commitAllPanelBtn);
		arrangementSettings.add(undoPanelBtn);
		arrangementSettings.add(clearPanelBtn);
		arrangementSettings.add(clearAllPanelsBtn);

		arrangementSettings.add(newSectionBox);
		arrangementSettings.add(addNewSectionBtn);
		arrangementSettings.add(copySelectedBtn);
		arrangementSettings.add(removeSelectedBtn);

		arrangementSettings.add(new JLabel("Seed"));
		arrangementSeed = new RandomValueButton(0);
		arrangementSettings.add(arrangementSeed);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(arrSection, constraints);
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
		TableModel model = new DefaultTableModel(7, 11);

		scrollableArrangementTable.setModel(model);
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

		arrangement = new Arrangement();
		actualArrangement = new Arrangement();
		arrangement.generateDefaultArrangement();
		//actualArrangement.generateDefaultArrangement();
		if (useArrangement.isSelected()) {
			arrangement.setPreviewChorus(false);
			actualArrangement.setPreviewChorus(false);
		} else {
			arrangement.setPreviewChorus(true);
			actualArrangement.setPreviewChorus(true);
			actualArrangement.resetArrangement();
		}
		scrollableArrangementTable.setModel(arrangement.convertToTableModel());
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
				LOGGER.debug(("MOVED HEADER"));
				arrangement.resortByIndexes(scrollableArrangementTable, false);
				arrangementTableColumnDragging = false;
			}
		});
		scrollableArrangementTable.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mousePressed(java.awt.event.MouseEvent evt) {
				int row = scrollableArrangementTable.rowAtPoint(evt.getPoint());
				int secOrder = scrollableArrangementTable.columnAtPoint(evt.getPoint());

				//LOGGER.info(("Clicked! " + row + ", " + secOrder));
				if (row == 0 && secOrder >= 0) {
					boolean rClick = SwingUtilities.isRightMouseButton(evt);
					boolean mClick = !rClick && SwingUtilities.isMiddleMouseButton(evt);
					if (rClick) {
						handleArrangementAction("ArrangementRemove," + secOrder, 0, 0);
					} else if (mClick) {
						//LOGGER.info(("mClick"));
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

				if (row == 1) {
					comp.setBackground(new Color(100, 150, 150));
					return comp;
				}

				int height = (int) (350 / getModel().getRowCount());
				int width = (int) ((VibeComposerGUI.scrollPaneDimension.getWidth() - 60)
						/ getModel().getColumnCount()) - 2;
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
				int row = scrollableArrangementActualTable.rowAtPoint(evt.getPoint());
				int secOrder = scrollableArrangementActualTable.columnAtPoint(evt.getPoint());


				LOGGER.debug(("Clicked! " + row + ", " + secOrder));
				boolean rClick = SwingUtilities.isRightMouseButton(evt);
				boolean mClick = !rClick && SwingUtilities.isMiddleMouseButton(evt);
				if (row == 0 && secOrder >= 0) {
					if (rClick) {
						handleArrangementAction("ArrangementRemove," + secOrder, 0, 0);
					} else if (mClick) {
						handleArrangementAction("ArrangementAdd," + secOrder, 0, 0);
					}
				} else if (row >= 2 && secOrder >= 0) {
					int part = row - 2;
					if (rClick || mClick) {
						//LOGGER.debug(("Clickable! rClick: " + rClick));
						Section sec = actualArrangement.getSections().get(secOrder);
						boolean hasPresence = !sec.getPresence(part).isEmpty();
						boolean hasVariation = hasPresence && sec.hasVariation(part);

						Point mousePoint = MouseInfo.getPointerInfo().getLocation();
						Point tablePoint = scrollableArrangementActualTable.getLocation();
						SwingUtilities.convertPointToScreen(tablePoint,
								scrollableArrangementActualTable);
						Rectangle r = scrollableArrangementActualTable.getCellRect(row, secOrder,
								false);
						/*LOGGER.debug("Mouse point: " + mousePoint.toString());
						LOGGER.debug("Table point: " + tablePoint.toString());
						LOGGER.debug(r.toString());*/

						mousePoint.x -= tablePoint.x;
						mousePoint.y -= tablePoint.y;

						mousePoint.x -= r.x;
						mousePoint.y -= r.y;


						double orderPercentage = OMNI.clamp((mousePoint.x / (double) r.width), 0.01,
								0.99);

						int actualSize = getInstList(part).size();
						int visualSize = Math.max(CollectionCellRenderer.MIN_CELLS + 1,
								actualSize + 1);
						int partAbsoluteOrder = (int) Math.floor(orderPercentage * visualSize);

						LOGGER.debug("Percentage: " + orderPercentage);
						LOGGER.debug("Selected subcell: " + (partAbsoluteOrder + 1));
						boolean shiftOverrideButton = false;
						if ((actualSize > CollectionCellRenderer.MIN_CELLS
								&& partAbsoluteOrder == actualSize)
								|| (actualSize <= CollectionCellRenderer.MIN_CELLS
										&& partAbsoluteOrder == CollectionCellRenderer.MIN_CELLS)) {
							shiftOverrideButton = true;
						} else if (partAbsoluteOrder >= actualSize) {
							LOGGER.debug("Can't interact: subcell not present in part - "
									+ (partAbsoluteOrder + 1));
							return;
						}

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
									sec2.setVariation(part, absOrder,
											sec.getVariation(part, absOrder));
								}

							}
						} else if (evt.isShiftDown() || shiftOverrideButton) {
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
									sec.generatePresences(new Random(), part,
											arrangement.getPartInclusionMap(), true);
								}
							}
						} else {
							boolean hasSinglePresence = sec.getPresence(part).contains(
									getInstList(part).get(partAbsoluteOrder).getPanelOrder());
							boolean hasSingleVariation = hasSinglePresence
									&& !sec.getVariation(part, partAbsoluteOrder).isEmpty();

							if (mClick) {
								if (hasSingleVariation) {
									for (int i = 2; i < Section.variationDescriptions[part].length; i++) {
										sec.removeVariationForPart(part, partAbsoluteOrder, i);
									}
								} else if (hasSinglePresence) {
									sec.generateVariationForPartAndOrder(new Random(), part,
											partAbsoluteOrder);
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
					}

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
				new String[] { "", "Section", "Bars", "Melody", "Bass", "Chord", "Arp", "Drum" });
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
				LOGGER.info(("MOVED"));
				actualArrangement.resortByIndexes(scrollableArrangementActualTable, true);
				actualArrangementTableColumnDragging = false;
				manualArrangement.setSelected(true);
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
		toggleableComponents.add(commitPanelBtn);
		toggleableComponents.add(commitAllPanelBtn);
		toggleableComponents.add(undoPanelBtn);
		toggleableComponents.add(clearPanelBtn);
		toggleableComponents.add(clearAllPanelsBtn);
	}

	protected void arrangementTableProcessSectionType(Component comp, String valueAt) {
		int typeOffset = Section.getTypeMelodyOffset(valueAt);
		comp.setBackground(new Color(100 + 15 * typeOffset, 150, 150));
	}

	private void arrangementTableProcessComponent(Component comp, int row, int col, String value,
			int[] maxCounts, boolean actual) {
		if (row >= 2) {

			// 2,3,4,5,6 -> melody, bass, chord, arp, drum counts
			//LOGGER.debug("Comp class: " + comp.getClass());
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

		scoreScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scoreScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		/*scoreScrollPane.getHorizontalScrollBar().addAdjustmentListener(new AdjustmentListener() {
			@Override
			public void adjustmentValueChanged(AdjustmentEvent e) {
				if (scorePanel != null) {
					LOGGER.info("Updating pos!");
					SwingUtilities.invokeLater(new Runnable() {
		
						@Override
						public void run() {
							scorePanel.update();
						}
		
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
			JButton butt = makeButton("Edit " + (i + 1), "ArrangementOpenVariation," + (i + 1));
			butt.setPreferredSize(new Dimension(
					(scrollPaneDimension.width - arrangementRowHeaderWidth) / count, 50));
			int fI = i;
			butt.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent evt) {
					if (SwingUtilities.isMiddleMouseButton(evt)) {
						actualArrangement.getSections().get(fI)
								.setRiskyVariations(new ArrayList<>());
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
		int count = (sec.getRiskyVariations() != null)
				? (int) sec.getRiskyVariations().stream().filter(e -> e > 0).count()
				: 0;
		int color = 0;
		if (isDarkMode) {
			color = arrangementDarkModeLowestColor
					+ (70 * count) / Section.riskyVariationNames.length;
			color = Math.min(color, 170);
		} else {
			color = arrangementLightModeHighestColor
					- (70 * count) / Section.riskyVariationNames.length;
			color = Math.max(color, 130);
		}

		int extraRed = 0;
		double remaining = 255 - color - 1;
		extraRed += (count * remaining) / (double) Section.riskyVariationNames.length;
		extraRed = Math.min(255 - color - 1, extraRed);

		butt.setBackground(new Color(color + extraRed, color, color));
	}

	private void initRandomButtons(int startY, int anchorSide) {
		JPanel randomButtonsPanel = new JPanel();
		//randomButtonsPanel.setBackground(new Color(60, 20, 60));
		randomButtonsPanel.setLayout(new GridLayout(0, 2));
		randomButtonsPanel.setOpaque(false);
		randomButtonsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		randomButtonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		JButton randomizeInstruments = makeButton("Randomize Inst.", "RandomizeInst");

		JButton randomizeBpm = makeButton("Randomize BPM", "RandomizeBpm");
		JButton randomizeTranspose = makeButton("Randomize Transpose", "RandomizeTranspose");

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


		randomizeInstOnComposeOrGen = new JCheckBox("on Compose/Gen", true);
		randomizeBpmOnCompose = new JCheckBox("on Compose", true);
		randomizeTransposeOnCompose = new JCheckBox("on Compose", true);
		randomizeInstOnComposeOrGen.setAlignmentX(Component.LEFT_ALIGNMENT);
		randomizeBpmOnCompose.setAlignmentX(Component.LEFT_ALIGNMENT);
		randomizeTransposeOnCompose.setAlignmentX(Component.LEFT_ALIGNMENT);
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

		randomizeChordStrumsOnCompose = new JCheckBox("on Compose");
		randomizeChordStrumsOnCompose.setSelected(false);
		//randomButtonsPanel.add(randomizeChordStrumsOnCompose);

		switchOnComposeRandom = makeButton("Untick all 'on Compose'", "UncheckComposeRandom");
		switchOnComposeRandom.setPreferredSize(new Dimension(170, 20));
		switchOnComposeRandom.setAlignmentX(Component.LEFT_ALIGNMENT);
		switchOnComposeRandom.setFont(switchOnComposeRandom.getFont().deriveFont(6));
		randomButtonsPanel.add(switchOnComposeRandom);
		//randomButtonsPanel.add(randomBottomPanel);

		//toggleableComponents.add(randomizeStrums);
		//toggleableComponents.add(randomizeChordStrumsOnCompose);
		controlPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		controlPanel.add(randomButtonsPanel);

	}


	private void initMacroParams(int startY, int anchorSide) {
		JPanel macroParams = new JPanel();
		macroParams.setLayout(new GridLayout(2, 0, 0, 0));
		macroParams.setOpaque(false);
		macroParams.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		fixedLengthChords = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new String[] { "4", "8", "RANDOM" }, fixedLengthChords);
		setFixedLengthChords(4);
		JLabel chordDurationFixedLabel = new JLabel("# of Chords");
		JPanel chordProgPanel = new JPanel();
		chordProgPanel.add(chordDurationFixedLabel);
		chordProgPanel.add(fixedLengthChords);
		chordProgPanel.setOpaque(false);
		macroParams.add(chordProgPanel);

		allowChordRepeats = new JCheckBox("Allow Chord Repeats", true);
		JPanel allowRepPanel = new JPanel();
		allowRepPanel.add(allowChordRepeats);
		allowRepPanel.setOpaque(false);
		macroParams.add(allowRepPanel);

		JPanel globalSwingPanel = new JPanel();
		globalSwingOverride = new JCheckBox("<html>Global Swing<br>Override</html>", false);
		globalSwingOverrideValue = new KnobPanel("", 50);
		globalSwingOverrideValue.setRegenerating(false);
		globalSwingOverrideApplyButton = new JButton("A");
		globalSwingOverrideApplyButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent evt) {
				int swing = globalSwingOverrideValue.getInt();
				melodyPanels.forEach(e -> e.setSwingPercent(swing));
				randomArpMaxSwing.setInt(swing);
				drumPanels.forEach(e -> {
					e.setSwingPercent(swing);
				});
			}
		});
		globalSwingPanel.add(globalSwingOverride);
		globalSwingPanel.add(globalSwingOverrideValue);
		globalSwingPanel.add(globalSwingOverrideApplyButton);
		globalSwingPanel.setOpaque(false);
		macroParams.add(globalSwingPanel);


		beatDurationMultiplier = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new String[] { "1/2", "1", "2" }, beatDurationMultiplier);
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

	private void initChordProgressionSettings(int startY, int anchorSide) {
		// CHORD SETTINGS 1 - chord variety 
		JPanel chordProgressionSettingsPanel = new JPanel();
		chordProgressionSettingsPanel.setLayout(new GridLayout(2, 0, 0, 0));
		chordProgressionSettingsPanel.setOpaque(false);
		chordProgressionSettingsPanel
				.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		//toggleableComponents.add(chordProgressionSettingsPanel);


		spiceChance = new KnobPanel("Spice", 15);
		spiceChance.setRegenerating(false);
		spiceAllowDimAug = new JCheckBox("Dim/Aug/6th", false);
		spiceAllow9th13th = new JCheckBox("9th/13th", false);
		spiceForceScale = new JCheckBox("Force Scale", true);
		spiceParallelChance = new KnobPanel("Aeolian", 5);
		spiceParallelChance.setRegenerating(false);

		firstChordSelection = new ScrollComboBox<String>(false);
		firstChordSelection.addItem("?");
		ScrollComboBox.addAll(MidiUtils.MAJOR_CHORDS.toArray(new String[] {}), firstChordSelection);
		firstChordSelection.setVal("C");
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
		String tooltip = "Allowed chords: C/D/E/F/G/A/B + "
				+ StringUtils.join(MidiUtils.SPICE_NAMES_LIST, " / ");
		tipLabel = new JLabel();
		//chordToolTip.add(tipLabel);

		JButton randomizeCustomChords = makeButton("    Randomize Chords    ",
				"RandomizeUserChords");
		customChordsPanel.add(randomizeCustomChords);

		userChordsEnabled = new CheckButton("Custom Chords", false);
		customChordsPanel.add(userChordsEnabled);

		userChords = new JTextField("?", 35);
		userChords.setToolTipText(tooltip);
		customChordsPanel.add(userChords);

		JButton normalizeChordsButton = new JButton("N") {
			private static final long serialVersionUID = 4142323272860314396L;
			String checkedChords = userChords.getText();

			@Override
			public String getToolTipText() {
				if (super.getToolTipText() == null) {
					return null;
				}
				if (!userChords.getText().equalsIgnoreCase(checkedChords)) {
					putClientProperty(TOOL_TIP_TEXT_KEY,
							(StringUtils.join(MidiUtils.getKeyModesForChordsAndTarget(
									userChords.getText(), ScaleMode.valueOf(scaleMode.getVal())))));
					checkedChords = userChords.getText();
				}

				return super.getToolTipText();
			}
		};
		normalizeChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				List<String> normalizedChords = MidiUtils.processRawChords(userChords.getText(),
						ScaleMode.valueOf(scaleMode.getVal()));
				if (normalizedChords != null) {
					userChords.setText(StringUtils.join(normalizedChords, ","));
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
				List<String> normalizedChords = MidiUtils.respiceChords(userChords.getText(),
						guiConfig);
				if (normalizedChords != null) {
					userChords.setText(StringUtils.join(normalizedChords, ","));
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
				Pair<List<String>, List<Double>> normalizedChords = solveUserChords(userChords,
						userChordsDurations);
				if (normalizedChords != null) {
					List<String> chords = normalizedChords.getLeft();
					List<String> chords2x = new ArrayList<>(chords);
					chords.forEach(ch -> {
						chords2x.add(ch);
					});
					userChords.setText(StringUtils.join(chords2x, ","));
				}
			}
		});
		customChordsPanel.add(twoExChordsButton);

		JButton ddChordsButton = new JButton("Dd");
		ddChordsButton.setPreferredSize(new Dimension(25, 25));
		ddChordsButton.setMargin(new Insets(0, 0, 0, 0));
		ddChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				Pair<List<String>, List<Double>> normalizedChords = solveUserChords(userChords,
						userChordsDurations);
				if (normalizedChords != null) {
					List<String> chords = normalizedChords.getLeft();
					List<String> chordsDd = new ArrayList<>();
					chords.forEach(ch -> {
						chordsDd.add(ch);
						chordsDd.add(ch);
					});
					userChords.setText(StringUtils.join(chordsDd, ","));
				}
			}
		});
		customChordsPanel.add(ddChordsButton);

		JButton dotdotChordsButton = new JButton("..");
		dotdotChordsButton.setPreferredSize(new Dimension(25, 25));
		dotdotChordsButton.setMargin(new Insets(0, 0, 0, 0));
		dotdotChordsButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				Pair<List<String>, List<Double>> normalizedChords = solveUserChords(userChords,
						userChordsDurations);
				if (normalizedChords != null) {
					List<String> chords = normalizedChords.getLeft();
					List<String> chordsDotDot = new ArrayList<>();
					chords.forEach(ch -> {
						chordsDotDot.add(MidiUtils.makeSpelledChord(MidiUtils.mappedChord(ch)));
					});
					userChords.setText(StringUtils.join(chordsDotDot, ","));
				}
			}
		});
		customChordsPanel.add(dotdotChordsButton);

		userChordsDurations = new JTextField("4,4,4,4", 9);
		JLabel userChordsDurationsLabel = new JLabel("Chord durations:");
		customChordsPanel.add(userChordsDurationsLabel);
		customChordsPanel.add(userChordsDurations);


		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(customChordsPanel, constraints);

		toggleableComponents.add(twoExChordsButton);
		toggleableComponents.add(userChordsDurations);
		toggleableComponents.add(dotdotChordsButton);
		toggleableComponents.add(ddChordsButton);
		toggleableComponents.add(normalizeChordsButton);
		toggleableComponents.add(userChordsDurationsLabel);

	}

	private void initSliderPanel(int startY, int anchorSide) {
		JPanel sliderPanel = new JPanel();
		sliderPanel.setOpaque(false);
		sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.X_AXIS));
		sliderPanel.setPreferredSize(new Dimension(1200, 40));


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
		everythingPanel.add(sliderPanel, constraints);

		JPanel sliderInfoPanel = new JPanel();
		sliderInfoPanel.setOpaque(false);
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
		constraints.gridy = startY + 1;
		constraints.anchor = anchorSide;
		everythingPanel.add(sliderInfoPanel, constraints);

		startOmnipresentThread();
		startSoloButtonControlThread();

	}

	private void startSoloButtonControlThread() {
		Thread cycle = new Thread() {

			public void run() {
				while (true) {
					try {
						// recalc sequencer tracks from button colorings
						if (needToRecalculateSoloMuters && !composingInProgress) {
							needToRecalculateSoloMuters = false;
							unapplySolosMutes(true);

							reapplySolosMutes();
							recolorButtons();
						}
						try {
							sleep(100);
						} catch (InterruptedException e) {

						}
					} catch (Exception e) {
						LOGGER.info(("Exception in SOLO buttons thread:" + e));
					}
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
			List<? extends InstPanel> panels = getInstList(i);
			count += panels.stream().filter(e -> !e.getMuteInst()).count();
		}
		return count;
	}

	private void startOmnipresentThread() {
		// init thread

		Thread cycle = new Thread() {

			public void run() {
				int allowedActionsOnZero = 0;
				while (true) {
					try {
						if (sequencer != null && sequencer.isRunning()) {
							if (!isDragging && !isKeySeeking) {
								if (allowedActionsOnZero == 0) {
									slider.setUpperValue(
											(int) (sequencer.getMicrosecondPosition() / 1000));
								} else {
									slider.setUpperValueRaw(
											(int) (sequencer.getMicrosecondPosition() / 1000));
								}

								currentTime.setText(microsecondsToTimeString(
										sequencer.getMicrosecondPosition()));
								//slider.repaint();
							} else {
								currentTime
										.setText(millisecondsToTimeString(slider.getUpperValue()));
							}
						}


						if (allowedActionsOnZero == 0) {
							if (actualArrangement != null && slider.getMaximum() > 0) {
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
										&& actualArrangement.getSections().size() > sectIndex) {
									sec = actualArrangement.getSections().get(sectIndex);
								}
								int finalSectIndex = sectIndex;
								if (sec == null) {
									sectionText.setText("End");
								} else {
									if (useArrangement.isSelected()) {
										String sectionName = (sec != null)
												? sec.getType().toString()
												: "END";
										sectionText.setText(sectionName);
									} else {
										sectionText.setText("ALL INST");
									}
								}

								Section actualSec = sec;
								if (highlightPatterns.isSelected()) {
									SwingUtilities.invokeLater(new Runnable() {
										public void run() {
											notifyVisualPatterns(val, finalSectIndex, actualSec);
										}
									});
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

						if (loopBeat.isSelected() && !composingInProgress && !isDragging
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
							if (newSliderVal >= ((mult * loopBeatCount.getInt() * beatFromBpm(0)
									/ 4) - 50) || sequencerEnded) {
								stopMidi();
								if (!loopBeatCompose.isSelected()) {
									composeMidi(true);
								} else {
									ActionEvent action = new ActionEvent(VibeComposerGUI.this,
											ActionEvent.ACTION_PERFORMED, "Compose");
									actionPerformed(action);
								}
							}
						}

						try {
							int tabIndex = instrumentTabPane.getSelectedIndex();
							if (loopBeat.isSelected() || (tabIndex >= 2 && tabIndex <= 4)
									|| (scorePopup != null || tabIndex == 7)) {
								sleep(5);
								allowedActionsOnZero = (allowedActionsOnZero + 1) % 5;
							} else {
								allowedActionsOnZero = 0;
								sleep(25);
							}

						} catch (InterruptedException e) {
							LOGGER.error("THREAD INTERRUPTED!");
						}
					} catch (Exception e) {
						LOGGER.error("Exception in SEQUENCE SLIDER:");
						LOGGER.error(e.getMessage());
						e.printStackTrace();
						try {
							sleep(200);
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
					sectIndex >= 0 && (sectIndex < sliderMeasureStartTimes.size() - 1) ? sectIndex
							: 0);
			int beatFindingStartIndex = sliderBeatStartTimes.indexOf(measureStart);
			int beatChordNumInMeasure = 0;
			int bfsiEnd = 0;
			int lastMeasureStartTimeIndex = 0;
			double quarterNote = beatFromBpm(0) / 4.0;
			List<Double> beatQuarterNotesInMeasure = new ArrayList<>();
			for (int bfsi = beatFindingStartIndex; bfsi < sliderBeatStartTimes.size(); bfsi++) {
				if (sliderBeatStartTimes.get(bfsi) > val) {
					bfsiEnd = bfsi;
					break;
				} else {

					int measureIndex = sliderMeasureStartTimes
							.indexOf(sliderBeatStartTimes.get(bfsi));
					if (measureIndex != -1) {
						// reset when exactly on measure
						beatChordNumInMeasure = 0;
						lastMeasureStartTimeIndex = measureIndex;
						beatQuarterNotesInMeasure.clear();
					} else {
						beatChordNumInMeasure++;
					}

					if (beatChordNumInMeasure > 0) {
						beatQuarterNotesInMeasure.add((sliderBeatStartTimes.get(bfsi)
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
			//LOGGER.debug(quarterNotesInMeasure + " qtn");
			if (quarterNotesInMeasure < MidiGenerator.DBL_ERR) {
				return;
			}


			// needed: wholeNotesInMeasure, beatNumInMeasure, beatDurationsInMeasure

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
					if (part == 4 && combineDrumTracks.isSelected()) {
						turnOff |= ((soloCondition ? groupSoloMuters.get(4).soloState == State.OFF
								: groupSoloMuters.get(4).muteState != State.OFF));
					} else {
						turnOff |= ((soloCondition ? ip.getSoloMuter().soloState == State.OFF
								: ip.getSoloMuter().muteState != State.OFF));
					}
				}

				boolean isIgnoreFill = false;
				if (!turnOff && sec != null) {
					int ignoreFillIndex = part == 4 ? 1 : 2;
					isIgnoreFill = sec
							.getVariation(part,
									VibeComposerGUI.getAbsoluteOrder(part, ip.getPanelOrder()))
							.contains(ignoreFillIndex);
				}
				ip.getComboPanel().notifyPatternHighlight(quarterNotesInMeasure,
						beatChordNumInMeasure, beatQuarterNotesInMeasure, turnOff, isIgnoreFill,
						totalChords);
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
		int finalVal = (int) (((4000 - speedAdjustment) * 60 * stretchMidi.getInt() / 100.0)
				/ guiConfig.getBpm());
		/*if (useDoubledDurations.isSelected()) {
			finalVal *= 2;
		}*/
		return finalVal;
	}

	public int sliderMeasureWidth() {
		return (int) (beatFromBpm(0) * MidiGenerator.GENERATED_MEASURE_LENGTH / 4);
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

		randomizeScaleModeOnCompose = new JCheckBox("Rand. on Compose", true);
		controlSettingsPanel.add(randomizeScaleModeOnCompose);


		randomSeed = new RandomValueButton(0);
		compose = makeButton("COMPOSE", "Compose");
		compose.setBackground(new Color(180, 150, 90));
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
			boolean wasSelected = startFromBar.isSelected();
			startFromBar.setSelected(false);
			pauseMidi();
			actionPerformed(new ActionEvent(regenerateStopPlay, ActionEvent.ACTION_PERFORMED,
					"Regenerate"));
			startFromBar.setSelected(wasSelected);
		});
		regenerateStopPlay.setMargin(new Insets(0, 0, 0, 0));
		regeneratePausePlay.setMargin(new Insets(0, 0, 0, 0));
		regenerateStopPlay.setPreferredSize(new Dimension(25, 30));
		regeneratePausePlay.setPreferredSize(new Dimension(25, 30));
		regenerate.setFont(regenerate.getFont().deriveFont(Font.BOLD));
		JButton copySeed = makeButton("Copy Main Seed", "CopySeed");
		JButton copyChords = makeButton("Copy chords", "CopyChords");
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

	private void initPlayPanel(int startY, int anchorSide) {

		JPanel playSavePanel = new JPanel();
		playSavePanel.setOpaque(false);
		stopMidi = makeButton("STOP", e -> stopMidi());
		playMidi = makeButton("PLAY", e -> playMidi());
		pauseMidi = makeButton("PAUSE", e -> pauseMidi());
		stopMidi.setFont(stopMidi.getFont().deriveFont(Font.BOLD));
		playMidi.setFont(playMidi.getFont().deriveFont(Font.BOLD));
		pauseMidi.setFont(pauseMidi.getFont().deriveFont(Font.BOLD));

		JButton save3Star = makeButton("Save 3*", "Save 3*");
		JButton save4Star = makeButton("Save 4*", "Save 4*");
		JButton save5Star = makeButton("Save 5*", "Save 5*");
		JButton saveCustom = makeButton("Save ->", "Save Custom");
		saveCustomFilename = new JTextField("savefilename", 12);


		JButton loadConfig = makeButton("LOAD..", "LoadGUIConfig");

		JButton saveWavFile = makeButton("Export .WAV", "SaveWavFile");

		showScore = new JButton("Show Score Tab");
		showScore.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (scorePanel != null) {
					if (instrumentTabPane.getComponentCount() == 8) {
						instrumentTabPane.remove(scoreScrollPane);
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
		});

		regenerateWhenValuesChange = new CheckButton("Regenerate on Change", true);
		/*showScorePicker = new ScrollComboBox<String>();
		ScrollComboBox.addAll(
				new String[] { "NO Drums/Chords", "Drums Only", "Chords Only", "ALL" },
				showScorePicker);*/

		loopBeat = new CheckButton("Loop Quarter Notes", false);
		loopBeatCount = new DetachedKnobPanel("", 16, 1, 16);

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
					LOGGER.info(("Added device: " + infos[i].toString()));
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
		generatedMidi.setTransferHandler(new FileTransferHandler());
		generatedMidi.setDragEnabled(true);

		playSavePanel.add(playMidi);
		playSavePanel.add(pauseMidi);
		playSavePanel.add(stopMidi);
		playSavePanel.add(save3Star);
		playSavePanel.add(save4Star);
		playSavePanel.add(save5Star);
		playSavePanel.add(saveCustom);
		playSavePanel.add(saveCustomFilename);

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
		playSettingsPanel.add(midiMode);
		playSettingsPanel.add(midiModeDevices);


		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(playSettingsPanel, constraints);

		constraints.gridy = startY + 5;
		constraints.anchor = anchorSide;
		everythingPanel.add(playSavePanel, constraints);
	}

	private void initHelperPopups() {
		JPanel helperPopupsPanel = new JPanel();
		helperPopupsPanel.add(makeButton("User Manual (opens browser)", e -> openHelpPopup()));
		helperPopupsPanel.add(makeButton("Debug Console", e -> openDebugConsole()));
		helperPopupsPanel.add(makeButton("About VibeComposer", e -> openAboutPopup()));
		extraSettingsPanel.add(helperPopupsPanel);
	}

	private void startMidiCcThread() {
		if (cycle != null && cycle.isAlive()) {
			LOGGER.info(("MidiCcThread already exists!"));
			return;
		}
		LOGGER.info(("Starting new MidiCcThread..!"));
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
				LOGGER.info(("ENDED MidiCcThread!"));
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
					if (combineMelodyTracks.isSelected() && j == 0 && i > 0) {
						// melody panels under first
						continue;
					}
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
		int value127 = useMidiCC.isSelected() ? OMNI.clampVel(pan100 * 127 / 100) : 64;
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
		randomizeMelodiesOnCompose.setSelected(state);
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

	}

	private void switchMidiButtons(boolean state) {
		playMidi.setEnabled(state);
		pauseMidi.setEnabled(state);
		stopMidi.setEnabled(state);
		compose.setEnabled(state);
		regenerate.setEnabled(state);
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
			newPrefSize = new Dimension(1600, 435);
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
			r = new ColorUIResource(new Color(193, 203, 208));
		} else {
			r = new ColorUIResource(new Color(68, 66, 67));
		}
		UIManager.put("Button.background", r);
		UIManager.put("Panel.background", r);
		UIManager.put("ComboBox.background", r);
		UIManager.put("TextField.background", r);
		SwingUtilities.updateComponentTreeUI(this);
		SwingUtilities.updateComponentTreeUI(extraSettingsPanel);
	}

	public static Color uiColor() {
		return (isDarkMode) ? darkModeUIColor : lightModeUIColor;
	}

	public void removeComboBoxArrows(Container parent) {
		for (Component c : parent.getComponents()) {
			if (c instanceof ScrollComboBox) {
				ScrollComboBox scb = (ScrollComboBox) c;
				scb.removeArrowButton();
				//LOGGER.debug("Unconfigured");
			}

			if (c instanceof Container) {
				//LOGGER.debug("Going deep");
				removeComboBoxArrows((Container) c);
			}

		}
	}

	private void switchDarkMode() {
		//setVisible(false);
		arrSection.setSelectedIndex(0);

		LOGGER.info(("Switching dark mode!"));
		if (isDarkMode) {
			FlatIntelliJLaf.install();
		} else {
			FlatDarculaLaf.install();
		}
		//UIManager.put("TabbedPane.contentOpaque", false);

		isDarkMode = !isDarkMode;
		updateGlobalUI();

		toggledUIColor = uiColor();

		mainTitle.setForeground((isDarkMode) ? new Color(0, 220, 220) : lightModeUIColor);
		subTitle.setForeground(toggledUIColor);
		messageLabel.setForeground(toggledUIColor);
		tipLabel.setForeground(toggledUIColor);
		currentTime.setForeground(toggledUIColor);
		totalTime.setForeground(toggledUIColor);
		randomizeMelodiesOnCompose.setForeground(toggledUIColor);
		randomChordsGenerateOnCompose.setForeground(toggledUIColor);
		randomArpsGenerateOnCompose.setForeground(toggledUIColor);
		randomDrumsGenerateOnCompose.setForeground(toggledUIColor);
		switchOnComposeRandom.setForeground(toggledUIColor);
		compose.setForeground(toggledUIColor);
		regenerate.setForeground(toggledUIColor);
		playMidi.setForeground(toggledUIColor);
		pauseMidi.setForeground(toggledUIColor);
		stopMidi.setForeground(toggledUIColor);
		randomArpHitsPerPattern.setForeground(toggledUIColor);
		randomizeInstOnComposeOrGen.setForeground(toggledUIColor);
		randomizeBpmOnCompose.setForeground(toggledUIColor);
		randomizeTransposeOnCompose.setForeground(toggledUIColor);
		//randomizeChordStrumsOnCompose.setForeground(toggledUIColor);
		randomizeArrangementOnCompose.setForeground(toggledUIColor);
		for (JSeparator x : separators) {
			x.setForeground(toggledUIColor);
		}

		panelColorHigh = UIManager.getColor("Panel.background").darker();
		panelColorLow = UIManager.getColor("Panel.background").brighter();
		/*if (!isDarkMode) {
			panelColorHigh = panelColorHigh.brighter();
			panelColorLow = panelColorLow.brighter();
		}*/
		if (GLOBAL.equals(arrSection.getVal())) {
			arrangementMiddleColoredPanel.setBackground(panelColorHigh.brighter());
		} else {
			arrangementMiddleColoredPanel.setBackground(toggledUIColor.darker().darker());
		}


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
		chordAddJButton.setEnabled(isOriginal);
		arpAddJButton.setEnabled(isOriginal);
		drumAddJButton.setEnabled(isOriginal);
		randomChordsToGenerate.setEnabled(isOriginal);
		randomArpsToGenerate.setEnabled(isOriginal);
		randomDrumsToGenerate.setEnabled(isOriginal);
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

		LOGGER.info(("Closed sequencer!"));
		MidiDevice oldDevice = device;
		device = null;

		if (oldDevice != null) {
			oldDevice.close();
		}

		LOGGER.info(("Closed oldDevice!"));
		oldDevice = null;
		needToRecalculateSoloMutersAfterSequenceGenerated = true;
	}

	public void composeMidi(boolean regenerate) {
		composingInProgress = true;
		long systemTime = System.currentTimeMillis();
		if (sequencer != null) {
			sequencer.stop();
		}

		if (manualArrangement.isSelected() && (actualArrangement.getSections().isEmpty()
				|| !actualArrangement.getSections().stream().anyMatch(e -> e.hasPresence()))) {
			LOGGER.info(("Nothing to compose! Uncheck MANUAL arrangement!"));
			composingInProgress = false;
			return;
		}

		saveStartInfo();
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
					LOGGER.info(("CLOSED SEQUENCER!"));
				}
				device.close();
				device = null;
				LOGGER.info(("CLOSED DEVICE!"));
			}
		}

		needToRecalculateSoloMuters = true;

		Integer masterpieceSeed = prepareMainSeed(regenerate);

		prepareUI(regenerate);
		copyGUItoConfig(guiConfig);
		MidiGenerator melodyGen = new MidiGenerator(guiConfig);
		fillUserParameters(regenerate);

		File makeDir = new File(MIDIS_FOLDER);
		makeDir.mkdir();
		makeDir = new File(MIDI_HISTORY_FOLDER);
		makeDir.mkdir();

		String seedData = "" + masterpieceSeed;
		if (melodyPanels.get(0).getPatternSeed() != 0 && !melodyPanels.get(0).getMuteInst()) {
			seedData += "_" + melodyPanels.get(0).getPatternSeed();
		}

		String fileName = "seed" + seedData;
		String relPath = MIDI_HISTORY_FOLDER + "/" + fileName + ".mid";

		// unapply S/M, generate, reapply S/M with new track numbering
		unapplySolosMutes(true);
		melodyGen.generateMasterpiece(masterpieceSeed, relPath);
		fixCombinedDrumTracks();
		reapplySolosMutes();

		cleanUpUIAfterCompose(regenerate);


		try (FileWriter fw = new FileWriter("randomSeedHistory.txt", true);
				BufferedWriter bw = new BufferedWriter(fw);
				PrintWriter out = new PrintWriter(bw)) {
			out.println(new Date().toString() + ", Seed: " + seedData);
		} catch (IOException e) {
			LOGGER.info(("Failed to write into Random Seed History.."));
		}

		handleGeneratedMidi(regenerate, relPath);
		resetArrSectionInBackground();
		composingInProgress = false;
		LOGGER.info("================== VibeComposerGUI::composeMidi time: "
				+ (System.currentTimeMillis() - systemTime) + " ms ==========================");
	}

	private void fixCombinedDrumTracks() {
		if (combineDrumTracks.isSelected()) {
			drumPanels.forEach(e -> {
				if (e.getSequenceTrack() < 0) {
					e.getSoloMuter().unsolo();
					e.getSoloMuter().unmute();
				}
			});
		}
	}

	public void fillUserParameters(boolean regenerate) {
		try {
			MidiGenerator.COLLAPSE_DRUM_TRACKS = combineDrumTracks.isSelected();
			MidiGenerator.COLLAPSE_MELODY_TRACKS = combineMelodyTracks.isSelected();
			MidiGenerator.recalculateDurations(stretchMidi.getInt());
			MidiGenerator.RANDOMIZE_TARGET_NOTES = !regenerate
					&& melodyTargetNotesRandomizeOnCompose.isSelected();
			MidiGenerator.TARGET_NOTES = (melody1ForcePatterns.isSelected()
					&& !melodyPanels.get(0).getNoteTargetsButton().isEnabled())
							? melodyPanels.get(0).getChordNoteChoices()
							: null;

			/*boolean addStartDelay = useArrangement.isSelected() || arrangementCustom.isSelected()
					|| drumPanels.stream().anyMatch(e -> e.getDelay() < 0)
					|| (randomChordDelay.isSelected()
							&& (chordPanels.stream().anyMatch(e -> e.getDelay() < 0)));*/
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
			if (userChordsEnabled.isSelected() && !userChords.getText().contains("?")) {
				Pair<List<String>, List<Double>> solvedChordsDurations = solveUserChords(userChords,
						userChordsDurations);
				if (solvedChordsDurations != null) {
					MidiGenerator.userChords = solvedChordsDurations.getLeft();
					MidiGenerator.userChordsDurations = solvedChordsDurations.getRight();
				} else {
					MidiGenerator.userChords.clear();
					MidiGenerator.userChordsDurations.clear();
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
			if (!addMelody.isSelected()) {
				MidiGenerator.gc.setMelodyParts(new ArrayList<>());
			}
			if (!addBass.isSelected()) {
				MidiGenerator.gc.setBassParts(new ArrayList<>());
			}
			if (!addChords.isSelected()) {
				MidiGenerator.gc.setChordParts(new ArrayList<>());
			}
			if (!addArps.isSelected()) {
				MidiGenerator.gc.setArpParts(new ArrayList<>());
			}
			if (!addDrums.isSelected()) {
				MidiGenerator.gc.setDrumParts(new ArrayList<>());
			}

		} catch (Exception e) {
			LOGGER.info(("User screwed up his inputs!"));
			e.printStackTrace();
		}

	}

	private void cleanUpUIAfterCompose(boolean regenerate) {
		if (MelodyMidiDropPane.userMelody != null) {
			userChords.setText(StringUtils.join(MidiGenerator.chordInts, ","));
			setFixedLengthChords(MidiGenerator.chordInts.size());
		}

		if (!regenerate && melodyTargetNotesRandomizeOnCompose.isSelected()
				&& MidiGenerator.TARGET_NOTES != null) {
			melodyPanels.forEach(e -> e.setChordNoteChoices(MidiGenerator.TARGET_NOTES));
		}

		actualArrangement = new Arrangement();
		actualArrangement.setPreviewChorus(false);
		actualArrangement.getSections().clear();
		for (Section sec : MidiGenerator.gc.getActualArrangement().getSections()) {
			actualArrangement.getSections().add(sec.deepCopy());
		}
		VibeComposerGUI.pianoRoll();
		/*if (showScore.isSelected()) {
			instrumentTabPane.setSelectedIndex(7);
		}*/
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
			LOGGER.info(("Skipping, regenerated seed: " + masterpieceSeed));
		} else if (parsedSeed != 0) {
			masterpieceSeed = parsedSeed;
		} else {
			Random seedGenerator = new Random();
			int randomVal = seedGenerator.nextInt();
			masterpieceSeed = randomVal;
		}

		LOGGER.info(("Melody seed: " + masterpieceSeed));
		lastRandomSeed = masterpieceSeed;
		return masterpieceSeed;
	}

	private void prepareUI(boolean regenerate) {

		// MELODY
		if (!regenerate && randomizeMelodiesOnCompose.isSelected()) {
			int seed = randomSeed.getValue() != 0 ? randomSeed.getValue() : lastRandomSeed;
			createRandomMelodyPanels(seed != 0 ? seed : new Random().nextInt());
		}


		if (randomMelodyOnRegenerate.isSelected()) {
			randomizeMelodySeeds();
		}

		if (!regenerate && melodyPatternRandomizeOnCompose.isSelected()) {
			if (melody1ForcePatterns.isSelected()) {
				List<Integer> pat = MelodyUtils.getRandomMelodyPattern(
						melodyPanels.get(0).getAlternatingRhythmChance(),
						melodyPanels.get(0).getPatternSeed() == 0 ? lastRandomSeed
								: melodyPanels.get(0).getPatternSeed());
				melodyPanels.get(0).setMelodyPatternOffsets(pat);
			} else {
				melodyPanels.forEach(e -> e.setMelodyPatternOffsets(
						MelodyUtils.getRandomMelodyPattern(e.getAlternatingRhythmChance(),
								e.getPatternSeed() == 0 ? lastRandomSeed : e.getPatternSeed())));
			}
		}


		if (melody1ForcePatterns.isSelected()) {
			MelodyPanel mp1 = melodyPanels.get(0);
			for (int i = 1; i < melodyPanels.size(); i++) {
				melodyPanels.get(i).overridePatterns(mp1);
			}
		}


		// BASS

		// ARPS
		if (instrumentTabPane.getSelectedIndex() != 3 && arpCopyMelodyInst.isSelected()
				&& !melodyPanels.get(0).getMuteInst()) {
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
						sec.resetCustomizedParts();
						break;
					}
				}
			}
		}

		if (!regenerate && randomizeScaleModeOnCompose.isSelected()) {
			Integer[] allowedScales = new Integer[] { 0, 1, 3, 4, 5, 8, 9 };
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

	}

	private void resetArrSectionInBackground() {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				int arrSectionIndex = arrSection.getSelectedIndex();
				setActualModel(actualArrangement.convertToActualTableModel());
				if (arrSectionIndex != 0 && arrSectionIndex < arrSection.getItemCount()) {
					arrSection.setSelectedIndex(arrSectionIndex);
				} else {
					arrSection.setSelectedIndex(0);
				}
				refreshVariationPopupButtons(scrollableArrangementActualTable.getColumnCount());
				arrSection.repaint();
			}

		});

	}

	private void handleGeneratedMidi(boolean regenerate, String relPath) {

		currentMidi = null;
		try {
			if (sequencer != null) {
				sequencer.stop();
			}
			Synthesizer synthesizer = null;
			if (!midiMode.isSelected()) {
				synthesizer = loadSynth();
			}


			if (sequencer == null) {
				sequencer = MidiSystem.getSequencer(synthesizer == null); // Get the default Sequencer
				if (sequencer == null) {
					LOGGER.error("Sequencer device not supported");
					return;
				}
				sequencer.open(); // Open device
			}


			// Create sequence, the File must contain MIDI file data.
			currentMidi = new File(relPath);
			generatedMidi.setListData(new File[] { currentMidi });
			//sizeRespectingPack();
			repaint();
			Sequence sequence = MidiSystem.getSequence(currentMidi);
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
							LOGGER.debug(
									infos[i].toString() + "| max recv: " + device.getMaxReceivers()
											+ ", max trm: " + device.getMaxTransmitters());
							if (device.getMaxReceivers() != 0) {
								LOGGER.debug(
										"Found max receivers != 0, opening midi receiver device: "
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

			resetSequencerTickPosition();

			totalTime.setText(microsecondsToTimeString(sequencer.getMicrosecondLength()));
			sequencer.start();  // start the playback
			slider.setMaximum((int) (sequencer.getMicrosecondLength() / 1000));
			slider.setPaintTicks(true);
			int measureWidth = sliderMeasureWidth();
			slider.setMajorTickSpacing(measureWidth);
			slider.setMinorTickSpacing(beatFromBpm(0));
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
			if (!endDisplayed) {
				table.put(slider.getMaximum(), new JLabel("END"));
				sliderMeasureStartTimes.add(slider.getMaximum());
				sliderBeatStartTimes.add(slider.getMaximum());
			}

			//sliderBeatStartTimes.add(slider.getMaximum());

			slider.setCustomMajorTicks(sliderMeasureStartTimes);
			slider.setCustomMinorTicks(sliderBeatStartTimes);
			/*LOGGER.info(("Size measures: " + sliderMeasureStartTimes.size()));
			LOGGER.info(("Size beats: " + sliderBeatStartTimes.size()));
			LOGGER.info(("What beats: " + sliderBeatStartTimes.toString()));*/

			if (startFromBar.isSelected()) {
				int snapAdjustment = 50;
				slider.setValue(sliderBeatStartTimes.get(startBeatCounter) + snapAdjustment);
			}
			// Force the slider to use the new labels
			slider.setLabelTable(table);
			slider.setPaintLabels(true);

			List<String> prettyChords = MidiGenerator.chordInts;
			currentChords.setText("Chords:[" + StringUtils.join(prettyChords, ",") + "]");

			if (loopBeat.isSelected()) {
				resetPauseInfo();
				int startPos = delayed;
				if (startPos < slider.getValue()) {
					startPos = slider.getValue();
				}
				midiNavigate(startPos);
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


			loopBeatCount.getKnob()
					.setMax(userChordsEnabled.isSelected()
							? (int) Math.ceil(OMNI.sumListDouble(
									OMNI.parseDoublesString(userChordsDurations.getText())))
							: MidiGenerator.chordInts.size() * 4);
			startMidiCcThread();
			recalculateTabPaneCounts();
			sequencer.setTempoFactor(1);
			if (needToRecalculateSoloMutersAfterSequenceGenerated) {
				needToRecalculateSoloMuters = true;
				needToRecalculateSoloMutersAfterSequenceGenerated = false;
			}
		} catch (MidiUnavailableException | InvalidMidiDataException | IOException ex) {
			ex.printStackTrace();
		}
	}

	private void resetSequencerTickPosition() {

		if (slider.getValue() < slider.getMaximum()) {
			midiNavigate(slider.getValue());
		} else {
			slider.setValue(0);
			midiNavigate(0);
		}


	}

	private void setFixedLengthChords(int size) {
		switch (size) {
		case 4:
			fixedLengthChords.setVal("4");
			break;
		case 8:
			fixedLengthChords.setVal("8");
			break;
		default:
			fixedLengthChords.setVal("RANDOM");
			break;
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

		int countReducer = 0;
		if (combineDrumTracks.isSelected()) {
			countReducer = (int) ((onlyIncluded)
					? drumPanels.stream().filter(e -> !e.getMuteInst()).count()
					: drumPanels.size());
			countReducer = Math.max(countReducer - 1, 0);
		}
		int baseCount = (onlyIncluded) ? countAllIncludedPanels() : countAllPanels();

		sequencer.setTrackSolo(0, false);
		sequencer.setTrackMute(0, false);
		for (int i = 1 + baseCount - countReducer; i < sequencer.getSequence()
				.getTracks().length; i++) {
			LOGGER.debug("Unsoloing: " + i);
			sequencer.setTrackSolo(i, false);
			sequencer.setTrackMute(i, false);
		}
	}

	private void reapplySolosMutes() {
		if (!sequenceReady()) {
			return;
		}
		// set by soloState/muteState
		for (int i = 0; i < 5; i++) {
			List<? extends InstPanel> panels = getInstList(i);
			panels.forEach(e -> sequencer.setTrackSolo(e.getSequenceTrack(),
					e.getSoloMuter().soloState == State.FULL));
			panels.forEach(e -> sequencer.setTrackMute(e.getSequenceTrack(),
					e.getSoloMuter().muteState == State.FULL));
		}

		Optional<DrumPanel> notExcludedDrum = drumPanels.stream()
				.filter(e -> e.getSequenceTrack() >= 0).findFirst();

		// drum specific
		if (combineDrumTracks.isSelected() && notExcludedDrum.isPresent()) {
			sequencer.setTrackSolo(notExcludedDrum.get().getSequenceTrack(),
					groupSoloMuters.get(4).soloState != State.OFF);
		}

		if (combineDrumTracks.isSelected() && notExcludedDrum.isPresent()) {
			sequencer.setTrackMute(notExcludedDrum.get().getSequenceTrack(),
					groupSoloMuters.get(4).muteState != State.OFF);
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
				LOGGER.info(("Playing using soundbank: "
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
				LOGGER.info(("NO SOUNDBANK WITH THAT NAME FOUND!"));
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
			LOGGER.info(("NO SOUNDBANK WITH THAT NAME FOUND!"));
		}
		return synthesizer;
	}

	private JButton makeButton(String name, String actionCommand) {
		JButton butt = new JButton(name);
		butt.addActionListener(this);
		butt.setActionCommand(actionCommand);
		return butt;
	}

	private JButton makeButton(String name, Consumer<? super Object> a) {
		JButton butt = new JButton(name);
		butt.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				a.accept(new Object());
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
		MidiGenerator.userChordsDurations.clear();
		mg.generatePrettyUserChords(new Random().nextInt(), MidiGenerator.gc.getFixedDuration(),
				4 * MidiGenerator.Durations.WHOLE_NOTE);
		List<String> prettyChords = MidiGenerator.chordInts;
		userChords.setText(StringUtils.join(prettyChords, ","));
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
		if (sequencer != null) {
			stopMidi();
			sequencer.close();
		}
		System.exit(0);
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
		boolean tabPanePossibleChange = false;
		boolean soloMuterPossibleChange = false;
		boolean triggerRegenerate = false;

		LOGGER.info(("Processing '" + ae.getActionCommand() + "'.."));
		long actionSystemTime = System.currentTimeMillis();

		boolean isCompose = "Compose".equals(ae.getActionCommand());
		boolean isRegenerate = "Regenerate".equals(ae.getActionCommand());

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

		if (isCompose && addChords.isSelected() && randomChordsGenerateOnCompose.isSelected()) {
			createPanels(2, Integer.valueOf(randomChordsToGenerate.getText()), false);
			tabPanePossibleChange = true;
		}
		if (isCompose && addArps.isSelected() && randomArpsGenerateOnCompose.isSelected()) {
			createPanels(3, Integer.valueOf(randomArpsToGenerate.getText()), false);
			tabPanePossibleChange = true;
		}

		if (isCompose && addDrums.isSelected() && randomDrumsGenerateOnCompose.isSelected()) {
			createPanels(4, Integer.valueOf(randomDrumsToGenerate.getText()), false);
			tabPanePossibleChange = true;
		}

		realBpm = Double.valueOf(mainBpm.getInt());
		if (ae.getActionCommand() == "RandomizeBpm"
				|| (isCompose && randomizeBpmOnCompose.isSelected())) {
			Random instGen = new Random();

			int bpm = instGen.nextInt(1 + bpmHigh.getInt() - bpmLow.getInt()) + bpmLow.getInt();
			if (arpAffectsBpm.isSelected() && !arpPanels.isEmpty()) {
				double highestArpPattern = arpPanels.stream()
						.map(e -> (e.getPatternRepeat() * e.getHitsPerPattern())
								/ (e.getChordSpan() * 8.0))
						.max((e1, e2) -> Double.compare(e1, e2)).get();
				LOGGER.info(("Repeater value: " + highestArpPattern));
				if (highestArpPattern > 1) {
					bpm *= 1 / (0.5 + highestArpPattern * 0.5);
				}
			}
			mainBpm.setInt(bpm);
			mainBpm.getKnob().setMin(bpmLow.getInt());
			mainBpm.getKnob().setMax(bpmHigh.getInt());
			realBpm = bpm;
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
			composeMidi(isRegenerate);
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
			tabPanePossibleChange = true;
			//worker.execute();

		}

		if (ae.getActionCommand().startsWith("Save ")) {
			saveGuiConfigFile(ae.getActionCommand());
		}

		if (ae.getActionCommand() == "DrumSave") {

			String drumsDirectory = "drums/";
			File makeSavedDir = new File(drumsDirectory);
			makeSavedDir.mkdir();

			JFileChooser chooser = new JFileChooser(makeSavedDir);
			int retrival = chooser.showSaveDialog(null);
			if (retrival == JFileChooser.APPROVE_OPTION) {
				try {
					marshalDrums(chooser.getSelectedFile() + ".xml");
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
				LOGGER.info(("You cancelled the choice"));
			else {
				LOGGER.info(("You chose " + filename));
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

		if (ae.getActionCommand() == "SaveWavFile") {

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
							+ currentMidi.getName();
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
					switchMidiButtons(true);
					messageLabel.setText("PROCESSED WAV!");
					repaint();
				}
			};
			worker.execute(); //here the process thread initiates


		}

		if (ae.getActionCommand() == "CopySeed") {
			/*Toolkit toolkit = Toolkit.getDefaultToolkit();
			Clipboard clipboard = toolkit.getSystemClipboard();
			StringSelection strSel = new StringSelection(str);
			clipboard.setContents(strSel, null);*/
			randomSeed.setValue(lastRandomSeed);
			LOGGER.info(("Copied to random seed: " + lastRandomSeed));
		}

		if (ae.getActionCommand() == "CopyChords") {
			userChords.setText(
					currentChords.getText().substring(8, currentChords.getText().length() - 1));
			LOGGER.info(("Copied chords: " + userChords.getText()));
		}


		if (ae.getActionCommand() == "LoadGUIConfig") {
			FileDialog fd = new FileDialog(this, "Choose a file", FileDialog.LOAD);
			fd.setDirectory(null);
			fd.setFile("*.xml");
			fd.setVisible(true);
			String filename = fd.getFile();
			File[] files = fd.getFiles();
			if (filename == null)
				LOGGER.info(("You cancelled the choice"));
			else {
				LOGGER.info(("You chose " + filename));
				try {
					guiConfig =

							unmarshallConfig(files[0]);
					copyConfigToGUI();
				} catch (JAXBException |

						IOException e) {
					// Auto-generated catch block
					e.printStackTrace();
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

		if (ae.getActionCommand() == "AddDrum") {
			//addDrumPanelToLayout();
			createBlueprintedDrumPanels(drumPanels.size() + 1, true, null);
			//sizeRespectingPack();
			repaint();
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
		}

		if (ae.getActionCommand().startsWith("RemoveDrum,")) {
			String drumNumber = ae.getActionCommand().split(",")[1];

			removeInstPanel(4, Integer.valueOf(drumNumber), true);
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
		}


		if (ae.getActionCommand() == "ClearChordPatterns")

		{
			for (InstPanel cp : getAffectedPanels(3)) {
				cp.setPatternSeed(0);
				cp.setPattern(RhythmPattern.FULL);

			}
		}

		if (ae.getActionCommand() == "AddChord") {
			//addChordPanelToLayout();
			createRandomChordPanels(chordPanels.size() + 1, true, null);

			//sizeRespectingPack();
			repaint();
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
		}

		if (ae.getActionCommand().startsWith("RemoveChord,")) {
			String chordNumber = ae.getActionCommand().split(",")[1];

			removeInstPanel(2, Integer.valueOf(chordNumber), true);
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
		}

		if (ae.getActionCommand() == "AddArp")

		{
			//addArpPanelToLayout();
			createRandomArpPanels(arpPanels.size() + 1, true, null);
			//sizeRespectingPack();
			repaint();
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
		}

		if (ae.getActionCommand().startsWith("RemoveArp,")) {
			String arpNumber = ae.getActionCommand().split(",")[1];
			removeInstPanel(3, Integer.valueOf(arpNumber), true);
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
		}

		if (ae.getActionCommand() == "ClearArpPatterns") {
			for (InstPanel ap : getAffectedPanels(3)) {
				ap.setPatternSeed(0);
				ap.setPattern(RhythmPattern.FULL);

			}
		}

		if (ae.getActionCommand() == "RandomizeUserChords") {
			userChordsEnabled.setSelected(true);
			randomizeUserChords();
		}

		if (ae.getActionCommand().startsWith("Arrangement")) {
			Random arrGen = new Random();
			handleArrangementAction(ae.getActionCommand(), arrGen.nextInt(),
					Integer.valueOf(pieceLength.getText()));
			tabPanePossibleChange = true;
		}

		if (ae.getActionCommand() == "CopyPart") {

			JButton source = (JButton) ae.getSource();
			InstPanel sourcePanel = (InstPanel) source.getParent();
			InstPart part = null;

			part = sourcePanel.toInstPart(lastRandomSeed);
			InstPanel newPanel = addInstPanelToLayout(instrumentTabPane.getSelectedIndex(), part,
					true);
			newPanel.setPatternSeed(sourcePanel.getPatternSeed());

			switch (instrumentTabPane.getSelectedIndex()) {
			case 2:
				newPanel.setMidiChannel(11 + (newPanel.getPanelOrder() - 1) % 5);
				newPanel.setPanByOrder(5);
				break;
			case 3:
				newPanel.setMidiChannel(2 + (newPanel.getPanelOrder() - 1) % 7);
				newPanel.setPanByOrder(7);
				break;
			case 4:
				newPanel.getComboPanel().reapplyHits();
				break;
			default:
				break;
			}
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
			//LOGGER.info(("Set sequencer solo: " + sourcePanel.getMidiChannel()));
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
			composeMidi(true);
		}

		LOGGER.info("Finished '" + ae.getActionCommand() + "' in: "
				+ (System.currentTimeMillis() - actionSystemTime) + " ms");
		messageLabel.setText("::" + ae.getActionCommand() + "::");
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

				cp.getInstrumentBox().initInstPool(pool);
				cp.setInstPool(pool);

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
		chordPanels.forEach(e -> e.setPatternSeed(0));
		arpPanels.forEach(e -> e.setPatternSeed(0));
		drumPanels.forEach(e -> e.setPatternSeed(0));
		melodyPanels.forEach(e -> e.setPatternSeed(0));
		bassPanels.forEach(e -> e.setPatternSeed(0));
		arrangementSeed.setValue(0);
	}

	private void saveGuiConfigFile(String actionCommand) {
		if (currentMidi != null) {
			LOGGER.info(("Saving file: " + currentMidi.getName()));

			Date date = new Date();
			String[] starSplit = actionCommand.split(" ");
			if (starSplit.length == 1) {
				LOGGER.info(("WRONG SAVE COMMAND: " + actionCommand));
				return;
			}
			String rating = starSplit[1].substring(0, 1);
			String saveDirectory = "/saved_";
			String name = "";

			SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();
			f.applyPattern("yyMMdd-HH-mm-ss");
			String fdate = "";

			if (!"C".equals(rating)) {
				saveDirectory += rating + "star/";

				File makeSavedDir = new File(MIDIS_FOLDER + saveDirectory);
				makeSavedDir.mkdir();
				name = currentMidi.getName();
				name = name.substring(0, name.length() - 4);
				fdate = f.format(date);
			} else {
				saveDirectory += "custom/";
				name = saveCustomFilename.getText();
				if (customFilenameAddTimestamp.isSelected()) {
					fdate = f.format(date);
				}
			}

			String finalFilePath = MIDIS_FOLDER + saveDirectory + fdate
					+ (fdate.isEmpty() ? "" : "_") + name + MID_EXTENSION;

			File savedMidi = new File(finalFilePath);
			try {
				FileUtils.copyFile(currentMidi, savedMidi);
				copyGUItoConfig(guiConfig);
				marshalConfig(guiConfig, finalFilePath, MID_EXTENSION.length());
			} catch (IOException | JAXBException e) {
				// Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			LOGGER.info(("currentMidi is NULL!"));
		}
	}

	private void saveGuiPresetFileByFilePath(String filePath) {
		try {
			GUIPreset preset = new GUIPreset();
			copyGUItoConfig(preset);
			List<Component> presetComps = makeSettableComponentList();
			List<Integer> presetCompValues = new ArrayList<>();
			for (int i = 0; i < presetComps.size(); i++) {
				presetCompValues.add(getComponentValue(presetComps.get(i)));
			}
			preset.setOrderedValuesUI(presetCompValues);
			preset.setDarkMode(isDarkMode);
			preset.setFullMode(isFullMode);
			preset.setBigMode(isBigMonitorMode);
			marshalPreset(preset, filePath);
		} catch (IOException | JAXBException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void playMidi() {
		if (sequencer != null) {
			LOGGER.info(("Starting Midi.."));
			if (sequencer.isRunning()) {
				sequencer.stop();
				long startPos = (startFromBar.isSelected())
						? sliderMeasureStartTimes.get(pausedMeasureCounter)
						: pausedSliderPosition;
				if (startPos < slider.getValue()) {
					startPos = slider.getValue();
				}
				midiNavigate(startPos);
			} else {
				sequencer.stop();
				savePauseInfo();
				if (pausedSliderPosition > 0 && pausedSliderPosition < slider.getMaximum() - 100) {
					LOGGER.debug(("Unpausing.."));
					midiNavigate(pausedSliderPosition);
				} else {
					LOGGER.debug(("Resetting.."));
					resetSequencerTickPosition();
				}
			}

			LOGGER.debug(("Position set.."));
			try {
				Thread.sleep(25);
			} catch (InterruptedException e) {
				// Auto-generated catch block
				e.printStackTrace();
			}
			sequencer.start();
			startMidiCcThread();
			LOGGER.info("Started Midi: " + pausedSliderPosition + "/" + slider.getMaximum()
					+ ", measure: " + pausedMeasureCounter);
		} else {
			LOGGER.info(("Sequencer is NULL!"));
		}
	}

	private void stopMidi() {
		if (sequencer != null) {
			LOGGER.info(("Stopping Midi.."));
			sequencer.stop();
			//resetSequencerTickPosition();
			slider.setUpperValue(slider.getValue());
			resetPauseInfo();
			LOGGER.info(("Stopped Midi!"));
		} else {
			LOGGER.info(("Sequencer is NULL!"));
		}
	}

	private void pauseMidi() {
		if (sequencer != null) {
			LOGGER.info(("Pausing Midi.."));
			sequencer.stop();
			savePauseInfo();
			LOGGER.info(
					"Paused Midi: " + pausedSliderPosition + ", measure: " + pausedMeasureCounter);
		} else {
			LOGGER.info(("Sequencer is NULL!"));
		}
	}

	public void savePauseInfo() {
		pausedSliderPosition = slider.getUpperValue();
		if (MidiGenerator.chordInts.size() > 0) {
			pausedMeasureCounter = (int) (pausedSliderPosition - delayed()) / sliderMeasureWidth();
			if (pausedMeasureCounter > 0 && sectionText.getText().equalsIgnoreCase("end")) {
				pausedMeasureCounter--;
			}
		} else {
			pausedMeasureCounter = 0;
		}
	}

	private void saveStartInfo() {
		startSliderPosition = slider.getValue();
		if (MidiGenerator.chordInts.size() > 0) {
			startBeatCounter = (int) ((startSliderPosition - delayed() + 20) / beatFromBpm(0));
		} else {
			startBeatCounter = 0;
		}
	}

	private void resetPauseInfo() {
		pausedSliderPosition = 0;
		pausedMeasureCounter = 0;
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
		if (!sequenceReady())
			return;
		for (InstPanel ip : groupList) {
			sequencer.setTrackMute(ip.getSequenceTrack(), true);
		}
	}

	public static boolean sequenceReady() {
		return (sequencer != null) && (sequencer.isOpen()) && (sequencer.getSequence() != null);
	}

	private void recalculateGenerationCounts() {
		randomChordsToGenerate.setText("" + Math.max(1, chordPanels.size()));
		randomArpsToGenerate.setText("" + Math.max(1, arpPanels.size()));
		randomDrumsToGenerate.setText("" + Math.max(1, drumPanels.size()));
	}

	private void recalculateTabPaneCounts() {
		instrumentTabPane.setTitleAt(0, "Melody (" + melodyPanels.size() + ")");
		instrumentTabPane.setTitleAt(1, " Bass  (1)");
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
		LOGGER.info(("Solving custom chords.."));
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
					LOGGER.info("Lengths don't match, solved only these: "
							+ userChordsParsed.toString() + " !");
				}
			}
		} catch (Exception e) {
			LOGGER.info(("Bad user input in custom chords/durations!\n"));
			e.printStackTrace();
		}
		if (!solvedChords.isEmpty() && !solvedDurations.isEmpty()) {
			LOGGER.info((solvedChords.toString()));
			LOGGER.info((solvedDurations.toString()));
			return Pair.of(solvedChords, solvedDurations);
		} else {
			return null;
		}
	}

	public static Pair<List<String>, List<Double>> solveUserChords(JTextField customChords,
			JTextField customChordsDurations) {

		String text = customChords.getText().replaceAll(" ", "");
		customChords.setText(text);
		String[] userChordsSplit = text.split(",");
		//LOGGER.info((StringUtils.join(userChordsSplit, ";")));

		String[] userChordsDurationsSplit = customChordsDurations.getText().split(",");
		if (userChordsSplit.length != userChordsDurationsSplit.length) {
			List<Integer> durations = IntStream.iterate(4, n -> n).limit(userChordsSplit.length)
					.boxed().collect(Collectors.toList());
			customChordsDurations.setText(StringUtils.join(durations, ","));
			userChordsDurationsSplit = customChordsDurations.getText().split(",");
		}
		return solveUserChords(userChordsSplit, userChordsDurationsSplit);
	}

	public static Pair<List<String>, List<Double>> solveUserChords(String customChords,
			String customChordsDurations) {

		String text = customChords.replaceAll(" ", "");
		String[] userChordsSplit = text.split(",");
		//LOGGER.info((StringUtils.join(userChordsSplit, ";")));

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
		wrapper.setDrumParts((List<DrumPart>) (List<?>) getInstPartsFromInstPanels(4, false));
		mar.marshal(wrapper, new File(path.substring(0, path.length() - 4) + "-drumParts.xml"));
		LOGGER.info("File saved: " + path);
	}

	public void unmarshallDrums(File f) throws JAXBException, IOException {
		JAXBContext context = JAXBContext.newInstance(DrumPartsWrapper.class);
		DrumPartsWrapper wrapper = (DrumPartsWrapper) context.createUnmarshaller()
				.unmarshal(new FileReader(f));
		recreateInstPanelsFromInstParts(4, wrapper.getDrumParts());
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
		LOGGER.info("File saved: " + path);
	}

	public void marshalPreset(GUIPreset preset, String path) throws JAXBException, IOException {
		SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();
		f.applyPattern("yyMMdd-hh-mm-ss");
		JAXBContext context = JAXBContext.newInstance(GUIPreset.class);
		Marshaller mar = context.createMarshaller();
		mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		mar.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "");
		mar.marshal(preset, new File(path));
		LOGGER.info("File saved: " + path);
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
		cs.add(randomizeMelodiesOnCompose);
		cs.add(melody1ForcePatterns);
		cs.add(combineMelodyTracks);
		cs.add(randomMelodySameSeed);
		cs.add(randomMelodyOnRegenerate);
		cs.add(useUserMelody);
		cs.add(melodyPatternRandomizeOnCompose);
		cs.add(melodyTargetNotesRandomizeOnCompose);

		// bass panel - nothing

		// chord panel
		cs.add(randomChordsGenerateOnCompose);
		//cs.add(randomChordsToGenerate);
		cs.add(randomChordStruminess);
		cs.add(randomChordUseChordFill);
		cs.add(randomChordStretchType);
		cs.add(randomChordStretchPicker);
		cs.add(randomChordSustainUseShortening);
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
		cs.add(arpCopyMelodyInst);
		cs.add(randomArpAllSameInst);
		cs.add(randomArpLimitPowerOfTwo);
		cs.add(randomArpUseOctaveAdjustments);
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
		cs.add(humanizeNotes);
		cs.add(humanizeDrums);
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
		cs.add(extraSettingsReverseDrumPanels);
		cs.add(extraSettingsOrderedTransposeGeneration);
		cs.add(patternApplyPausesWhenGenerating);
		cs.add(highlightPatterns);
		cs.add(highlightScoreNotes);

		return cs;
	}

	public void setComponent(Component c, Integer num) {
		if (c instanceof ScrollComboBox) {
			ScrollComboBox csc = ((ScrollComboBox) c);
			if (csc.getItemCount() > 0) {
				csc.setSelectedIndex(Math.min(num, csc.getItemCount()));
			}
		} else if (c instanceof KnobPanel) {
			((KnobPanel) c).setInt(num);
		} else if (c instanceof JCheckBox) {
			((JCheckBox) c).setSelected(num != null && num > 0);
		} else if (c instanceof CheckButton) {
			((CheckButton) c).setSelected(num != null && num > 0);
		} else {
			throw new IllegalArgumentException("UNSUPPORTED COMPONENT!" + c.getClass());
		}
	}

	public Integer getComponentValue(Component c) {
		if (c instanceof ScrollComboBox) {
			return ((ScrollComboBox) c).getSelectedIndex();
		} else if (c instanceof KnobPanel) {
			return ((KnobPanel) c).getInt();
		} else if (c instanceof JCheckBox) {
			return ((JCheckBox) c).isSelected() ? 1 : 0;
		} else if (c instanceof CheckButton) {
			return ((CheckButton) c).isSelected() ? 1 : 0;
		} else {
			throw new IllegalArgumentException("UNSUPPORTED COMPONENT!" + c.getClass());
		}
	}

	public void copyGUItoConfig(GUIConfig gc) {
		// seed
		//GUIConfig gc = new GUIConfig();

		if (MelodyMidiDropPane.userMelody != null) {
			gc.setMelodyNotes(new PhraseNotes(MelodyMidiDropPane.userMelody));
		}

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
		LOGGER.debug(("OVERRIDE OK?: " + overrideSuccessful));
		if (overrideSuccessful) {
			arrangement.setOverridden(true);
		} else {
			arrangement.setOverridden(false);
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
		if (fixedLengthChords.getSelectedIndex() < 2) {
			gc.setFixedDuration(Integer.valueOf(fixedLengthChords.getVal()));
		} else {
			gc.setFixedDuration(0);
		}

		gc.setTranspose(transposeScore.getInt());
		gc.setBpm(Double.valueOf(mainBpm.getInt()));
		gc.setArpAffectsBpm(arpAffectsBpm.isSelected());
		gc.setBeatDurationMultiplier(beatDurationMultiplier.getSelectedIndex());
		gc.setAllowChordRepeats(allowChordRepeats.isSelected());
		gc.setGlobalSwingOverride(
				globalSwingOverride.isSelected() ? globalSwingOverrideValue.getInt() : null);

		// parts
		gc.setMelodyEnable(addMelody.isSelected());
		gc.setBassEnable(addBass.isSelected());
		gc.setChordsEnable(addChords.isSelected());
		gc.setArpsEnable(addArps.isSelected());
		gc.setDrumsEnable(addDrums.isSelected());

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

		gc.setMelodyArpySurprises(melodyArpySurprises.isSelected());
		gc.setMelodySingleNoteExceptions(melodySingleNoteExceptions.isSelected());
		gc.setMelodyFillPausesPerChord(melodyFillPausesPerChord.isSelected());
		gc.setMelodyUseDirectionsFromProgression(melodyUseDirectionsFromProgression.isSelected());
		gc.setMelodyAvoidChordJumps(melodyAvoidChordJumps.isSelected());
		gc.setMelodyBlockTargetMode(melodyBlockTargetMode.getSelectedIndex());
		gc.setMelodyPatternEffect(melodyPatternEffect.getSelectedIndex());
		gc.setMelodyReplaceAvoidNotes(melodyReplaceAvoidNotes.getInt());
		gc.setMelodyMaxDirChanges(melodyMaxDirChanges.getInt());


		// chords
		gc.setUseChordFormula(extraUseChordFormula.isSelected());
		gc.setFirstChord(firstChordSelection.getVal());
		gc.setLastChord(lastChordSelection.getVal());
		gc.setKeyChangeType(KeyChangeType.valueOf(keyChangeTypeSelection.getVal()));
		gc.setCustomChordsEnabled(userChordsEnabled.isSelected());
		gc.setCustomChords(StringUtils.join(MidiGenerator.chordInts, ","));
		gc.setCustomChordDurations(userChordsDurations.getText());
		gc.setSpiceChance(spiceChance.getInt());
		gc.setSpiceParallelChance(spiceParallelChance.getInt());
		gc.setDimAugDom7thEnabled(spiceAllowDimAug.isSelected());
		gc.setEnable9th13th(spiceAllow9th13th.isSelected());
		gc.setSpiceFlattenBigChords(spiceFlattenBigChords.isSelected());
		gc.setSquishProgressively(extraSquishChordsProgressively.isSelected());
		gc.setChordSlashChance(chordSlashChance.getInt());
		gc.setSpiceForceScale(spiceForceScale.isSelected());

		// arps
		gc.setUseOctaveAdjustments(randomArpUseOctaveAdjustments.isSelected());
		gc.setMaxArpSwing(randomArpMaxSwing.getInt());

		// drums
		boolean isCustomMidiDevice = midiMode.isSelected()
				&& !(midiModeDevices.getVal()).contains("ervill");
		gc.setDrumCustomMapping(drumCustomMapping.isSelected() && isCustomMidiDevice);
		gc.setDrumCustomMappingNumbers(drumCustomMappingNumbers.getText());
		gc.setMelodyPatternFlip(melodyPatternFlip.isSelected());
	}

	public void copyConfigToGUI() {


		if (guiConfig.getMelodyNotes() != null) {
			MelodyMidiDropPane.userMelody = guiConfig.getMelodyNotes().makePhrase();
			MelodyMidiDropPane.message.setText("~MELODY LOADED FROM FILE~");
		}

		// seed
		randomSeed.setValue((int) guiConfig.getRandomSeed());
		lastRandomSeed = randomSeed.getValue();
		midiMode.setSelected(guiConfig.isMidiMode());

		// arrangement
		arrangement = guiConfig.getArrangement();
		actualArrangement = guiConfig.getActualArrangement();
		scrollableArrangementTable.setModel(arrangement.convertToTableModel());
		setActualModel(actualArrangement.convertToActualTableModel());
		arrSection.setSelectedIndex(0);
		refreshVariationPopupButtons(actualArrangement.getSections().size());

		arrangementVariationChance.setInt(guiConfig.getArrangementVariationChance());
		arrangementPartVariationChance.setInt(guiConfig.getArrangementPartVariationChance());
		arrangementScaleMidiVelocity.setSelected(guiConfig.isScaleMidiVelocityInArrangement());
		arrangementSeed.setValue(arrangement.getSeed());
		useArrangement.setSelected(guiConfig.isArrangementEnabled());
		manualArrangement.setSelected(true);

		// macro
		scaleMode.setVal(guiConfig.getScaleMode().toString());
		soundbankFilename.getEditor().setItem(guiConfig.getSoundbankName());
		pieceLength.setText(String.valueOf(guiConfig.getPieceLength()));
		setFixedLengthChords(guiConfig.getFixedDuration());

		transposeScore.setInt(guiConfig.getTranspose());
		mainBpm.setInt((int) Math.round(guiConfig.getBpm()));

		arpAffectsBpm.setSelected(guiConfig.isArpAffectsBpm());
		beatDurationMultiplier.setSelectedIndex(guiConfig.getBeatDurationMultiplier());
		allowChordRepeats.setSelected(guiConfig.isAllowChordRepeats());
		globalSwingOverride.setSelected(guiConfig.getGlobalSwingOverride() != null);
		if (guiConfig.getGlobalSwingOverride() != null) {
			globalSwingOverrideValue.setInt(guiConfig.getGlobalSwingOverride());
		}

		// parts

		addMelody.setSelected(guiConfig.isMelodyEnable());
		addBass.setSelected(guiConfig.isBassEnable());
		addChords.setSelected(guiConfig.isChordsEnable());
		addArps.setSelected(guiConfig.isArpsEnable());
		addDrums.setSelected(guiConfig.isDrumsEnable());

		//drumCustomMapping.setSelected(guiConfig.isDrumCustomMapping());
		drumCustomMappingNumbers.setText(guiConfig.getDrumCustomMappingNumbers());
		if (StringUtils.countMatches(drumCustomMappingNumbers.getText(),
				",") != InstUtils.DRUM_INST_NUMBERS_SEMI.length - 1) {
			drumCustomMappingNumbers
					.setText(StringUtils.join(InstUtils.DRUM_INST_NUMBERS_SEMI, ","));
		}
		melodyPatternFlip.setSelected(guiConfig.isMelodyPatternFlip());

		recreateInstPanelsFromInstParts(0, guiConfig.getMelodyParts());
		if (guiConfig.getBassParts() != null && !guiConfig.getBassParts().isEmpty()) {
			recreateInstPanelsFromInstParts(1, guiConfig.getBassParts());
		}

		recreateInstPanelsFromInstParts(2, guiConfig.getChordParts());
		recreateInstPanelsFromInstParts(3, guiConfig.getArpParts());
		recreateInstPanelsFromInstParts(4, guiConfig.getDrumParts());

		setChordSettingsInUI(guiConfig.getChordGenSettings());

		// melody
		melodyFirstNoteFromChord.setSelected(guiConfig.isFirstNoteFromChord());
		randomChordNote.setSelected(guiConfig.isFirstNoteRandomized());
		melodyUseOldAlgoChance.setInt(guiConfig.getMelodyUseOldAlgoChance());
		melodyBasicChordsOnly.setSelected(guiConfig.isMelodyBasicChordsOnly());
		melodyTonicNoteTarget.setInt(guiConfig.getMelodyTonicNoteTarget());
		melodyChordNoteTarget.setInt(guiConfig.getMelodyChordNoteTarget());
		melodyModeNoteTarget.setInt(guiConfig.getMelodyModeNoteTarget());
		melodyEmphasizeKey.setSelected(guiConfig.isMelodyEmphasizeKey());

		melodyArpySurprises.setSelected(guiConfig.isMelodyArpySurprises());
		melodySingleNoteExceptions.setSelected(guiConfig.isMelodySingleNoteExceptions());
		melodyFillPausesPerChord.setSelected(guiConfig.isMelodyFillPausesPerChord());
		melodyAvoidChordJumps.setSelected(guiConfig.isMelodyAvoidChordJumps());
		melodyUseDirectionsFromProgression
				.setSelected(guiConfig.isMelodyUseDirectionsFromProgression());
		melodyBlockTargetMode.setSelectedIndex(guiConfig.getMelodyBlockTargetMode());
		melodyPatternEffect.setSelectedIndex(guiConfig.getMelodyPatternEffect());
		melodyReplaceAvoidNotes.setInt(guiConfig.getMelodyReplaceAvoidNotes());
		melodyMaxDirChanges.setInt(guiConfig.getMelodyMaxDirChanges());

		// chords
		spiceChance.setInt(guiConfig.getSpiceChance());
		spiceParallelChance.setInt(guiConfig.getSpiceParallelChance());
		spiceAllowDimAug.setSelected(guiConfig.isDimAugDom7thEnabled());
		spiceAllow9th13th.setSelected(guiConfig.isEnable9th13th());
		spiceFlattenBigChords.setSelected(guiConfig.isSpiceFlattenBigChords());
		extraSquishChordsProgressively.setSelected(guiConfig.isSquishProgressively());
		chordSlashChance.setInt(guiConfig.getChordSlashChance());
		spiceForceScale.setSelected(guiConfig.isSpiceForceScale());

		extraUseChordFormula.setSelected(guiConfig.isUseChordFormula());
		firstChordSelection.setVal(guiConfig.getFirstChord());
		lastChordSelection.setVal(guiConfig.getLastChord());
		keyChangeTypeSelection.setVal(guiConfig.getKeyChangeType().toString());
		userChordsEnabled.setSelected(guiConfig.isCustomChordsEnabled());
		userChords.setText(guiConfig.getCustomChords());
		userChordsDurations.setText(guiConfig.getCustomChordDurations());

		// arps
		randomArpUseOctaveAdjustments.setSelected(guiConfig.isUseOctaveAdjustments());
		randomArpMaxSwing.setInt(guiConfig.getMaxArpSwing());

	}

	private class FileTransferHandler extends TransferHandler {
		@Override
		protected Transferable createTransferable(JComponent c) {
			List<File> files = new ArrayList<>();
			files.add(currentMidi);
			return new FileTransferable(files);
		}

		@Override
		public int getSourceActions(JComponent c) {
			return COPY_OR_MOVE;
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

	public InstPanel addInstPanelToLayout(int inst) {
		return addInstPanelToLayout(inst, null, true);
	}

	public InstPanel addInstPanelToLayout(int inst, boolean recalc) {
		return addInstPanelToLayout(inst, null, recalc);
	}

	public InstPanel addInstPanelToLayout(int inst, InstPart initializingPart,
			boolean recalcArrangement) {
		InstPanel ip = InstPanel.makeInstPanel(inst, this);
		List<InstPanel> affectedPanels = getAffectedPanels(inst);
		int panelOrder = (affectedPanels.size() > 0) ? getValidPanelNumber(affectedPanels) : 1;

		ip.getToggleableComponents().forEach(e -> e.setVisible(isFullMode));
		if (arrSection != null && !GLOBAL.equals(arrSection.getVal())) {
			ip.toggleGlobalElements(false);
			ip.toggleEnabledCopyRemove(false);
			if (inst == 4) {
				ip.getInstrumentBox().setEnabled(true);
			}
		} else {
			ip.setBackground(OMNI.alphen(instColors[inst], 60));

			if (inst == 4) {
				ip.getSoloMuter().setVisible(!combineDrumTracks.isSelected());
			}
		}

		if (initializingPart != null) {
			ip.setFromInstPart(initializingPart);
		}
		ip.setPanelOrder(panelOrder);

		affectedPanels.add(ip);
		removeComboBoxArrows(ip);
		if (recalcArrangement) {
			if (actualArrangement != null && actualArrangement.getSections() != null) {
				actualArrangement.getSections().forEach(e -> e.initPartMapFromOldData());
			}
		}


		if (inst < 4 || !extraSettingsReverseDrumPanels.isSelected()) {
			((JPanel) getInstPane(inst).getViewport().getView()).add(ip, panelOrder + 1);
		} else {
			((JPanel) getInstPane(inst).getViewport().getView()).add(ip,
					affectedPanels.size() - panelOrder + 2);
		}
		return ip;
	}

	private void removeInstPanel(int inst, int order, boolean singleRemove) {

		List<? extends InstPanel> panels = getInstList(inst);
		InstPanel panel = getPanelByOrder(order, panels);
		((JPanel) getInstPane(inst).getViewport().getView()).remove(panel);

		panels.remove(panel);

		actualArrangement.getSections().forEach(e -> e.initPartMapFromOldData());

		repaint();
	}

	private List<InstPart> getInstPartsFromInstPanels(int inst, boolean removeMuted) {
		List<? extends InstPanel> panels = getInstList(inst);
		List<InstPart> parts = new ArrayList<>();
		for (InstPanel p : panels) {
			if (!removeMuted || !p.getMuteInst()) {
				parts.add(p.toInstPart(lastRandomSeed));
			}
		}
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
		List<InstPanel> panels = getAffectedPanels(inst);
		JScrollPane pane = getInstPane(inst);
		for (InstPanel panel : panels) {
			((JPanel) pane.getViewport().getView()).remove(panel);
		}
		panels.clear();
		for (InstPart part : parts) {
			InstPanel panel = addInstPanelToLayout(inst, false);
			panel.setFromInstPart(part);
		}
		repaint();
	}

	private void randomizePanel(InstPanel panel) {
		if (panel instanceof DrumPanel) {
			createBlueprintedDrumPanels(drumPanels.size() + 1, true, (DrumPanel) panel);
		} else if (panel instanceof ArpPanel) {
			createRandomArpPanels(arpPanels.size() + 1, true, (ArpPanel) panel);
		} else if (panel instanceof ChordPanel) {
			createRandomChordPanels(chordPanels.size() + 1, true, (ChordPanel) panel);
		}
	}

	protected void createRandomMelodyPanels(int seed) {
		Random melodyRand = new Random(seed);
		for (int i = 0; i < 3; i++) {
			MelodyPanel melodyPanel = melodyPanels.get(i);
			if (melodyPanel.getLockInst()) {
				continue;
			}
			if (randomizeInstOnComposeOrGen.isSelected()) {
				melodyPanel.setInstrument(melodyPanel.getInstrumentBox().getRandomInstrument());
			}

			melodyPanel.setSpeed(melodyRand.nextInt(25));
			melodyPanel.setMaxBlockChange(3 + melodyRand.nextInt(5));
			//melodyPanel.setSplitChance(melodyRand.nextInt(15));
			melodyPanel.setNoteExceptionChance(10 + melodyRand.nextInt(15));
			melodyPanel.setMaxNoteExceptions(melodyRand.nextInt(2));
			melodyPanel.setLeadChordsChance(melodyRand.nextInt(50));
			if (i > 0) {
				melodyPanel.setFillPauses(true);
				melodyPanel.setPauseChance(50 + melodyRand.nextInt(40));
				/*melodyPanel.toggleCombinedMelodyDisabledUI(
						combineMelodyTracks != null && !combineMelodyTracks.isSelected());*/
				melodyPanel.setVelocityMax(65 + melodyRand.nextInt(20));
				melodyPanel.setVelocityMin(40 + melodyRand.nextInt(20));
				if (i % 2 == 1) {
					melodyPanel.setTranspose(0);
					melodyPanel.getPanSlider().setValue(75);
				} else {
					melodyPanel.setTranspose(-12);
					melodyPanel.getPanSlider().setValue(25);
				}
				melodyPanel.setNoteLengthMultiplier(70 + melodyRand.nextInt(40));
			} else {
				melodyPanel.setFillPauses(melodyRand.nextBoolean());
				melodyPanel.setPauseChance(melodyRand.nextInt(35));
				melodyPanel.setTranspose(12);
				melodyPanel.setVelocityMax(80 + melodyRand.nextInt(30));
				melodyPanel.setVelocityMin(50 + melodyRand.nextInt(25));
				melodyPanel.setNoteLengthMultiplier(100 + melodyRand.nextInt(25));
			}
		}
		repaint();
	}

	private void createPanels(int part, int panelCount, boolean onlyAdd) {
		if (part == 0) {
			createRandomMelodyPanels(new Random().nextInt());
		} else if (part == 2) {
			createRandomChordPanels(panelCount, onlyAdd, null);
		} else if (part == 3) {
			createRandomArpPanels(panelCount, onlyAdd, null);
		} else if (part == 4) {
			if (randomDrumsOverrandomize.isSelected()) {
				createRandomDrumPanels(panelCount, onlyAdd, null);
			} else {
				createBlueprintedDrumPanels(panelCount, onlyAdd, null);
			}
		} else {
			throw new IllegalArgumentException("Unsupported panel part!");
		}
		if (canRegenerateOnChange()) {
			composeMidi(true);
		}
	}


	protected void createBlueprintedDrumPanels(int panelCount, boolean onlyAdd,
			DrumPanel randomizedPanel) {
		List<DrumPanel> affectedDrums = (List<DrumPanel>) (List<?>) getAffectedPanels(4);

		Random drumPanelGenerator = new Random();
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
		Collections.sort(removedPanels,
				(e1, e2) -> Integer.compare(e1.getPanelOrder(), e2.getPanelOrder()));

		panelCount -= remainingPanels.size();

		int slide = 0;

		if (randomDrumSlide.isSelected()) {
			slide = drumPanelGenerator.nextInt(100) - 50;
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
			swingPercent = 50
					+ drumPanelGenerator
							.nextInt(Integer.valueOf(randomDrumMaxSwingAdjust.getText()) * 2 + 1)
					- Integer.valueOf(randomDrumMaxSwingAdjust.getText());
		}


		List<Integer> pitches = new ArrayList<>();
		for (int i = 0; i < panelCount; i++) {
			pitches.add(
					InstUtils.getInstByIndex(drumPanelGenerator.nextInt(127), InstUtils.POOL.DRUM));
		}
		Collections.sort(pitches);
		int index = 0;
		long kickCount = remainingPanels.stream()
				.filter(e -> KICK_DRUMS.contains(e.getInstrument())).count();
		long snareCount = remainingPanels.stream()
				.filter(e -> SNARE_DRUMS.contains(e.getInstrument())).count();
		if (!onlyAdd && pitches.size() >= 3) {
			//LOGGER.info(("Kick,snare: " + kickCount + ", " + snareCount));
			if (kickCount == 0) {
				pitches.set(index++, 35);
				pitches.set(index++, 36);
			} else if (kickCount == 1) {
				pitches.set(index++, 36);
			}


			if (snareCount == 0) {
				pitches.set(index++, drumPanelGenerator.nextBoolean() ? 38 : 40);
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
			pitches.set(0, allowedInsts.get(drumPanelGenerator.nextInt(allowedInsts.size())));
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
		for (int i = 0; i < panelCount; i++) {
			DrumPanel dp = null;
			if (randomizedPanel != null) {
				dp = randomizedPanel;
			} else {
				if (i < removedPanels.size()) {
					dp = removedPanels.get(i);
				} else {
					dp = (DrumPanel) addInstPanelToLayout(4);
				}
			}

			DrumPart dpart = DrumDefaults.getDrumFromInstrument(
					!dp.getInstrumentBox().isEnabled() ? dp.getInstrument() : pitches.get(i));
			int order = DrumDefaults.getOrder(dpart.getInstrument());
			DrumSettings settings = DrumDefaults.drumSettings[order];
			settings.applyToDrumPart(dpart, lastRandomSeed);


			dpart.setOrder(dp.getPanelOrder());
			dpart.setMuted(dp.getMuteInst());
			dp.setFromInstPart(dpart);

			//dp.setHitsPerPattern(dp.getHitsPerPattern() * randomDrumHitsMultiplierLastState);

			if (settings.isSwingable()) {
				dp.setDelay(slide);
				dp.setSwingPercent(swingPercent);
			} else {
				dp.setSwingPercent(50);
			}

			if (settings.isDynamicable() && (dp.getPattern() != RhythmPattern.MELODY1)) {
				double ghostChanceReducer = (drumPanels.size() > 10) ? 0.8 : 1.0;
				dp.setIsVelocityPattern(drumPanelGenerator.nextInt(
						100) < randomDrumVelocityPatternChance.getInt() * ghostChanceReducer);
			} else {
				dp.setIsVelocityPattern(false);
			}

			if (drumPanels.size() > 10 && dp.getPattern() == RhythmPattern.FULL
					&& drumPanelGenerator.nextInt() < 30) {
				dp.setPattern(RhythmPattern.ALT);
			}

			if (settings.isVariableShift()
					&& drumPanelGenerator.nextInt(100) < randomDrumShiftChance.getInt()) {
				// settings set the maximum shift, this sets 0 - max randomly
				dp.setPatternShift(drumPanelGenerator.nextInt(dp.getPatternShift() + 1));
			}

			dp.applyPauseChance(drumPanelGenerator);

			//if (dp.getPatternShift() > 0) {
			dp.getComboPanel().reapplyShift();
			//}

			dp.getComboPanel().reapplyHits();

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

	private int[] displayDrumPart(DrumPart dp, int chords, int maxPatternPerChord) {
		int[] displayArray = new int[chords * maxPatternPerChord];
		List<Integer> patternGenerated = MidiGenerator.generateDrumPatternFromPart(dp);
		patternGenerated = MidiUtils.intersperse(0, dp.getChordSpan() - 1, patternGenerated);
		patternGenerated = MidiUtils.intersperse(0,
				(maxPatternPerChord / dp.getHitsPerPattern()) - 1, patternGenerated);
		//LOGGER.info((StringUtils.join(patternGenerated, ",")));
		int size = patternGenerated.size();
		//LOGGER.info(("Size: " + size));
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

	private void createRandomDrumPanels(int panelCount, boolean onlyAdd,
			DrumPanel randomizedPanel) {
		List<DrumPanel> affectedDrums = (List<DrumPanel>) (List<?>) getAffectedPanels(4);

		Random drumPanelGenerator = new Random();
		for (Iterator<DrumPanel> panelI = affectedDrums.iterator(); panelI.hasNext();) {
			DrumPanel panel = panelI.next();
			if (!onlyAdd) {
				((JPanel) drumScrollPane.getViewport().getView()).remove(panel);
				panelI.remove();
			}

		}

		panelCount -= affectedDrums.size();

		int slide = 0;

		if (randomDrumSlide.isSelected()) {
			slide = drumPanelGenerator.nextInt(100) - 50;
		}

		int swingPercent = 50;
		if (onlyAdd && affectedDrums.size() > 0) {
			Optional<Integer> existingSwing = affectedDrums.stream()
					.filter(e -> (e.getSwingPercent() != 50)).map(e -> e.getSwingPercent())
					.findFirst();
			if (existingSwing.isPresent()) {
				swingPercent = existingSwing.get();
			}
		}
		// nothing's changed.. still the same..
		if (swingPercent == 50) {
			swingPercent = 50
					+ drumPanelGenerator
							.nextInt(Integer.valueOf(randomDrumMaxSwingAdjust.getText()) * 2 + 1)
					- Integer.valueOf(randomDrumMaxSwingAdjust.getText());
		}


		List<Integer> pitches = new ArrayList<>();
		for (int i = 0; i < panelCount; i++) {
			pitches.add(
					InstUtils.getInstByIndex(drumPanelGenerator.nextInt(127), InstUtils.POOL.DRUM));
		}
		Collections.sort(pitches);
		if (!onlyAdd && pitches.size() > 3) {
			pitches.set(0, 35);
			pitches.set(1, 36);
			pitches.set(2, 38);

		}
		Collections.sort(pitches);
		List<RhythmPattern> viablePatterns = new ArrayList<>(Arrays.asList(RhythmPattern.values()));
		viablePatterns.remove(RhythmPattern.CUSTOM);
		for (int i = 0; i < panelCount; i++) {
			DrumPanel dp = (DrumPanel) addInstPanelToLayout(4);
			dp.setInstrument(pitches.get(i));
			//dp.setPitch(32 + drumPanelGenerator.nextInt(33));


			dp.setChordSpan(drumPanelGenerator.nextInt(2) + 1);
			RhythmPattern pattern = RhythmPattern.FULL;
			// use pattern in half the cases if checkbox selected

			if (randomDrumPattern.isSelected()) {
				int[] patternWeights = { 35, 60, 80, 90, 90, 100 };
				int randomWeight = drumPanelGenerator.nextInt(100);
				for (int j = 0; j < patternWeights.length; j++) {
					if (randomWeight < patternWeights[j]) {
						pattern = viablePatterns.get(j);
						break;
					}
				}
			}

			int hits = 4;
			while (drumPanelGenerator.nextBoolean() && hits < 16) {
				hits *= 2;
			}
			if ((hits / dp.getChordSpan() >= 8)) {
				hits /= 2;
			}

			dp.setHitsPerPattern(hits * 2);

			int adjustVelocity = -1 * dp.getHitsPerPattern() / dp.getChordSpan();


			dp.setPattern(pattern);
			int velocityMin = drumPanelGenerator.nextInt(30) + 50 + adjustVelocity;

			dp.setVelocityMax(1 + velocityMin + drumPanelGenerator.nextInt(25));
			dp.setVelocityMin(velocityMin);

			if (pattern != RhythmPattern.FULL) {
				dp.setPauseChance(drumPanelGenerator.nextInt(5) + 0);
			} else {
				dp.setPauseChance(drumPanelGenerator.nextInt(40) + 40);
			}

			// punchy drums - kicks, snares
			if (PUNCHY_DRUMS.contains(dp.getInstrument())) {
				adjustVelocity += 15;
				dp.setExceptionChance(drumPanelGenerator.nextInt(3));
			} else {
				dp.setDelay(slide);
				dp.setSwingPercent(swingPercent);
				dp.setExceptionChance(drumPanelGenerator.nextInt(10));
				if (drumPanelGenerator.nextInt(100) < 75) {
					dp.setPattern(RhythmPattern.MELODY1);
				}
			}

			if (randomDrumUseChordFill.isSelected()) {
				dp.setChordSpanFill(ChordSpanFill.getWeighted(drumPanelGenerator.nextInt(100)));
			}

			dp.setIsVelocityPattern(drumPanelGenerator.nextInt(100) < Integer
					.valueOf(randomDrumVelocityPatternChance.getInt()));

			if (drumPanelGenerator.nextInt(100) < randomDrumShiftChance.getInt()
					&& pattern != RhythmPattern.FULL) {
				dp.setPatternShift(
						drumPanelGenerator.nextInt(dp.getPattern().pattern.length - 1) + 1);
				dp.getComboPanel().reapplyShift();
			}

			dp.getComboPanel().reapplyHits();

		}

		repaint();
	}

	protected void createRandomChordPanels(int panelCount, boolean onlyAdd,
			ChordPanel randomizedPanel) {
		List<ChordPanel> affectedChords = (List<ChordPanel>) (List<?>) getAffectedPanels(2);

		Random chordPanelGenerator = new Random();
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
		Collections.sort(removedPanels,
				(e1, e2) -> Integer.compare(e1.getPanelOrder(), e2.getPanelOrder()));

		panelCount -= remainingPanels.size();

		int fixedChordStretch = -1;
		if (randomChordStretchType.getVal().equals("FIXED")) {
			fixedChordStretch = Integer.valueOf(randomChordStretchPicker.getVal());
		}

		List<RhythmPattern> viablePatterns = new ArrayList<>(Arrays.asList(RhythmPattern.values()));
		viablePatterns.remove(RhythmPattern.CUSTOM);

		for (int i = 0; i < panelCount; i++) {
			boolean needNewChannel = false;
			ChordPanel cp = null;
			if (randomizedPanel != null) {
				cp = randomizedPanel;
			} else {
				if (i < removedPanels.size()) {
					cp = removedPanels.get(i);
				} else {
					cp = (ChordPanel) addInstPanelToLayout(2);
					needNewChannel = true;
				}
			}
			InstUtils.POOL pool = cp.getInstPool();

			if ((randomizeInstOnComposeOrGen.isSelected() || onlyAdd)
					&& cp.getInstrumentBox().isEnabled()) {
				pool = (chordPanelGenerator.nextInt(100) < Integer
						.valueOf(randomChordSustainChance.getInt())) ? InstUtils.POOL.CHORD
								: InstUtils.POOL.PLUCK;
				cp.getInstrumentBox().initInstPool(pool);
				cp.setInstPool(pool);
				cp.setInstrument(cp.getInstrumentBox().getRandomInstrument());

			}
			cp.setTransitionChance(
					chordPanelGenerator.nextInt(randomChordMaxSplitChance.getInt() + 1));
			cp.setTransitionSplit(
					(getRandomFromArray(chordPanelGenerator, MILISECOND_ARRAY_SPLIT, 0)));
			if (extraSettingsOrderedTransposeGeneration.isSelected()) {
				cp.setTranspose((((cp.getPanelOrder()) % 3) - 1) * 12);
			} else {
				cp.setTranspose((chordPanelGenerator.nextInt(3) - 1) * 12);
			}


			Pair<StrumType, Integer> strumPair = getRandomStrumPair();
			cp.setStrum(strumPair.getRight());
			cp.setStrumType(strumPair.getLeft());
			cp.setDelay((getRandomFromArray(chordPanelGenerator, MILISECOND_ARRAY_DELAY, 0)));

			if (randomChordUseChordFill.isSelected()) {
				cp.setChordSpanFill(ChordSpanFill.getWeighted(chordPanelGenerator.nextInt(100)));
			} else {
				cp.setChordSpanFill(ChordSpanFill.ALL);
			}
			// default SINGLE = 4
			RhythmPattern pattern = RhythmPattern.SINGLE;
			// use pattern in 20% of the cases if checkbox selected
			int patternChance = pool == InstUtils.POOL.PLUCK ? 50 : 20;
			if (chordPanelGenerator.nextInt(100) < patternChance) {
				if (randomChordPattern.isSelected()) {
					pattern = viablePatterns
							.get(chordPanelGenerator.nextInt(viablePatterns.size()));
					if (cp.getStrum() > 501) {
						cp.setStrum(cp.getStrum() / 2);
					}
				}
			}

			if (!randomChordStretchType.getVal().equals("NONE")) {
				cp.setStretchEnabled(true);
				if (fixedChordStretch < 0) {
					int atMost = Integer.valueOf(randomChordStretchPicker.getVal());
					cp.setChordNotesStretch(chordPanelGenerator.nextInt(atMost - 3 + 1) + 3);
				} else {
					cp.setChordNotesStretch(fixedChordStretch);
				}
				if (cp.getChordNotesStretch() > 3 && cp.getStrum() > 999) {
					cp.setStrum(cp.getStrum() / 2);
				}
			} else {
				cp.setStretchEnabled(false);
			}

			cp.setPattern(pattern);
			if ((pattern == RhythmPattern.FULL || pattern == RhythmPattern.MELODY1)
					&& cp.getStrum() > 499) {
				cp.setStrum(cp.getStrum() / 4);
			}

			if (chordPanelGenerator.nextInt(100) < randomChordExpandChance.getInt()) {
				cp.setPatternJoinMode(PatternJoinMode.EXPAND);
			} else {
				cp.setPatternJoinMode(PatternJoinMode.NOJOIN);
			}


			cp.setVelocityMax(randomChordMaxVel.getInt());
			cp.setVelocityMin(randomChordMinVel.getInt());

			if (randomChordSustainUseShortening.isSelected()) {
				if (pool == InstUtils.POOL.PLUCK) {
					cp.setNoteLengthMultiplier(chordPanelGenerator.nextInt(26) + 50);
				} else {
					cp.setNoteLengthMultiplier(chordPanelGenerator.nextInt(26) + 85);
				}

			}

			if (chordPanelGenerator.nextInt(100) < randomChordShiftChance.getInt()) {
				int maxShift = Math.min(cp.getPattern().maxShift, cp.getHitsPerPattern());
				cp.setPatternShift(maxShift > 0 ? (chordPanelGenerator.nextInt(maxShift) + 1) : 0);
			} else {
				cp.setPatternShift(0);
			}

			if (needNewChannel) {
				cp.setMidiChannel(11 + (cp.getPanelOrder() - 1) % 5);
				cp.setPanByOrder(5);
			}
		}

		repaint();
	}

	protected void createRandomArpPanels(int panelCount, boolean onlyAdd,
			ArpPanel randomizedPanel) {
		List<ArpPanel> affectedArps = (List<ArpPanel>) (List<?>) getAffectedPanels(3);

		Random arpPanelGenerator = new Random();
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
		Collections.sort(removedPanels,
				(e1, e2) -> Integer.compare(e1.getPanelOrder(), e2.getPanelOrder()));

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

		if (arpCopyMelodyInst.isSelected() && !melodyPanels.get(0).getMuteInst()) {
			fixedInstrument = melodyPanels.get(0).getInstrument();
			if (affectedArps.size() > 0) {
				affectedArps.get(0).setInstrument(fixedInstrument);
			}
		}

		int fixedArpStretch = -1;
		if (randomArpStretchType.getVal().equals("FIXED")) {
			fixedArpStretch = Integer.valueOf(randomArpStretchPicker.getVal());
		}


		int start = 0;
		if (randomizedPanel != null) {
			start = randomizedPanel.getPanelOrder() - 1;
			panelCount = start + 1;
		}

		ArpPanel first = (affectedArps.isEmpty() || !affectedArps.get(0).getLockInst()
				|| (randomizedPanel != null && start == 0)) ? null : affectedArps.get(0);
		List<RhythmPattern> viablePatterns = new ArrayList<>(Arrays.asList(RhythmPattern.values()));
		viablePatterns.remove(RhythmPattern.CUSTOM);

		for (int i = start; i < panelCount; i++) {
			if (randomArpAllSameInst.isSelected() && first != null && fixedInstrument < 0) {
				fixedInstrument = first.getInstrument();
			}
			if (randomArpAllSameHits.isSelected() && first != null && fixedHits < 0) {
				fixedHits = first.getHitsPerPattern() / first.getChordSpan();
			}
			ArpPanel ap = null;
			boolean needNewChannel = false;
			if (randomizedPanel != null) {
				ap = randomizedPanel;
			} else {
				if (i < removedPanels.size()) {
					ap = removedPanels.get(i);
				} else {
					needNewChannel = true;
					ap = (ArpPanel) addInstPanelToLayout(3);
				}
			}


			if (randomArpHitsPerPattern.isSelected()) {
				if (fixedHits > 0) {
					ap.setHitsPerPattern(fixedHits);
				} else {
					if (fixedHitsGenerated > 0) {
						ap.setHitsPerPattern(fixedHitsGenerated);
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
						ap.setHitsPerPattern(value);
					}
				}
			} else {
				ap.setHitsPerPattern(randomArpHitsPicker.getSelectedIndex() + 1);
			}

			if (randomizeInstOnComposeOrGen.isSelected() || onlyAdd) {
				int instrument = ap.getInstrumentBox().getRandomInstrument();

				if (randomArpAllSameInst.isSelected()) {
					if (fixedInstrument >= 0) {
						instrument = fixedInstrument;
					} else {
						fixedInstrument = instrument;
					}
				}
				if (ap.getInstrumentBox().isEnabled()) {
					ap.setInstrument(instrument);
				}
			}

			ap.setChordSpan(arpPanelGenerator.nextInt(2) + 1);

			if (extraSettingsOrderedTransposeGeneration.isSelected()) {
				ap.setTranspose((((ap.getPanelOrder() + 1) % 3) - 1) * 12);
			} else {
				if (first == null && i == 0 && !onlyAdd) {
					ap.setTranspose(12);
				} else {
					ap.setTranspose((arpPanelGenerator.nextInt(3) - 1) * 12);
				}
			}


			if (first == null && i == 0 && !onlyAdd && arpCopyMelodyInst.isSelected()
					&& !melodyPanels.get(0).getMuteInst()) {
				ap.setInstrument(fixedInstrument);
			}


			if (ap.getChordSpan() == 1) {
				ap.setPatternRepeat(arpPanelGenerator.nextInt(randomArpMaxRepeat.getInt()) + 1);
			} else {
				ap.setPatternRepeat(1);
				if (arpPanelGenerator.nextBoolean() == true) {
					if ((first == null && i > 1) || (first != null)) {
						ap.setHitsPerPattern(ap.getHitsPerPattern() * ap.getChordSpan());
					}

				}
			}

			if (!randomArpStretchType.getVal().equals("NONE")) {
				ap.setStretchEnabled(true);
				if (fixedArpStretch < 0) {
					int atMost = Integer.valueOf(randomArpStretchPicker.getVal());
					ap.setChordNotesStretch(arpPanelGenerator.nextInt(atMost - 3 + 1) + 3);
				} else {
					ap.setChordNotesStretch(fixedArpStretch);
				}
			} else {
				ap.setStretchEnabled(false);
			}

			RhythmPattern pattern = RhythmPattern.FULL;
			// use pattern if checkbox selected and %chance 
			if (arpPanelGenerator.nextInt(100) < 30 + arpPanels.size() * 5) {
				if (randomArpPattern.isSelected()) {
					pattern = viablePatterns.get(arpPanelGenerator.nextInt(viablePatterns.size()));
				}
			}
			ap.setPattern(pattern);
			if (randomArpUseChordFill.isSelected()) {
				int exoticFillChanceIncrease = (arpPanels.size() > 3) ? (arpPanels.size() - 3) * 5
						: 0;
				ap.setChordSpanFill(ChordSpanFill
						.getWeighted(arpPanelGenerator.nextInt(100) + exoticFillChanceIncrease));
			} else {
				ap.setChordSpanFill(ChordSpanFill.ALL);
			}


			ap.setVelocityMax(randomArpMaxVel.getInt());
			ap.setVelocityMin(randomArpMinVel.getInt());

			int pauseMax = (int) (50 * ap.getPattern().getNoteFrequency());
			ap.setPauseChance(arpPanelGenerator.nextInt(pauseMax + 1));
			ap.applyPauseChance(arpPanelGenerator);

			if (arpPanelGenerator.nextInt(100) < randomArpShiftChance.getInt()) {
				LOGGER.debug("Arp getPattern: " + ap.getPattern().name());
				int maxShift = Math.min(ap.getPattern().maxShift, ap.getHitsPerPattern());
				ap.setPatternShift(maxShift > 0 ? (arpPanelGenerator.nextInt(maxShift) + 1) : 0);
			} else {
				ap.setPatternShift(0);
			}

			int lengthRange = Math.max(1,
					1 + randomArpMaxLength.getInt() - randomArpMinLength.getInt());
			ap.setNoteLengthMultiplier(
					arpPanelGenerator.nextInt(lengthRange) + randomArpMinLength.getInt());

			if (arpPanelGenerator.nextBoolean()) {
				int arpPatternOrder = 0;
				int[] patternWeights = { 60, 68, 75, 83, 91, 97, 100 };
				int randomWeight = arpPanelGenerator.nextInt(100);
				for (int j = 0; j < patternWeights.length; j++) {
					if (randomWeight < patternWeights[j]) {
						arpPatternOrder = j;
						break;
					}
				}
				ap.setArpPattern(ArpPattern.values()[arpPatternOrder]);
				if (arpPatternOrder > 0 && arpPanelGenerator.nextBoolean()) {
					ap.setArpPatternRotate(
							arpPanelGenerator.nextInt(Math.min(4, ap.getChordNotesStretch())));
				}
			} else {
				ap.setArpPattern(ArpPattern.RANDOM);
			}

			if (needNewChannel) {
				ap.setMidiChannel(2 + (ap.getPanelOrder() - 1) % 7);
				ap.setPanByOrder(7);
			}
		}

		if (!affectedArps.isEmpty()) {
			ArpPanel lowest = affectedArps.get(0);
			if (!lowest.getLockInst()) {
				lowest.setPatternRepeat(1);
				lowest.setChordSpan(1);

			}
		}

		//sizeRespectingPack();
		repaint();
	}

	private static int getValidPanelNumber(List<? extends InstPanel> panels) {
		panels.sort((e1, e2) -> Integer.compare(e1.getPanelOrder(), e2.getPanelOrder()));
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

	private static String millisecondsToTimeString(int l) {
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

	public long msToTicks(long ms) {
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

	public void midiNavigate(long sliderValue) {
		long time = (sliderValue - 10) * 1000;
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
		//LOGGER.info(("New array: " + Arrays.toString(array)));
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
		//LOGGER.info(("Total: " + totalWeight + ", Target: " + targetWeight));
		// -> strength of reduction depends on how far from ends
		totalWeight = 0;

		//LOGGER.info(("New array: " + Arrays.toString(realWeights)));
		for (int i = 0; i < array.length; i++) {
			totalWeight += realWeights[i];
			if (totalWeight >= targetWeight) {
				return array[i];
			}
		}
		return array[array.length - 1];

	}

	public static int getAbsoluteOrder(int partNum, int partOrder) {
		List<? extends InstPanel> panels = getInstList(partNum);
		for (int i = 0; i < panels.size(); i++) {
			if (panels.get(i).getPanelOrder() == partOrder) {
				return i;
			}
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
				&& arrSection.getSelectedIndex() == 0;
	}
}
