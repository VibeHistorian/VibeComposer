/* --------------------
* @author VibeHistorian
* ---------------------
*/
package org.vibehistorian.midimasterpiece.midigenerator;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
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
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

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
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.border.BevelBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils.POOL;
import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils.ScaleMode;
import org.vibehistorian.midimasterpiece.midigenerator.Enums.ChordSpanFill;
import org.vibehistorian.midimasterpiece.midigenerator.Enums.RhythmPattern;
import org.vibehistorian.midimasterpiece.midigenerator.Helpers.FileTransferable;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.ArpPanel;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.BassPanel;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.ChordGenSettings;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.ChordPanel;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.DrumPanel;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.InstPanel;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.MelodyPanel;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.NumPanel;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.ArpPart;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.ChordPart;
import org.vibehistorian.midimasterpiece.midigenerator.Parts.DrumPart;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.sun.media.sound.AudioSynthesizer;

// main class

public class MidiGeneratorGUI extends JFrame
		implements ActionListener, ItemListener, WindowListener {

	private static final long serialVersionUID = -677536546851756969L;

	private static final String SOUNDBANK_DEFAULT = "MuseScore_General.sf2";
	private static final String MIDIS_FOLDER = "midis";

	private static final double[] MILISECOND_ARRAY_STRUM = { 0, 1000, 750, 500, 333, 250, 166, 125,
			62, 62, 31 };
	private static final double[] MILISECOND_ARRAY_DELAY = { 0, 62, 125, 250, 333 };
	private static final double[] MILISECOND_ARRAY_SPLIT = { 625, 750, 875 };
	private static final double[] MILISECOND_MULTIPLIER_ARRAY = { 1, 1.5, 2, 3, 4 };

	private static boolean isDarkMode = true;
	private static boolean isFullMode = true;

	private static Set<Component> toggleableComponents = new HashSet<>();

	private static List<JSeparator> separators = new ArrayList<>();

	private static Soundbank soundfont = null;


	private Synthesizer synth = null;
	private boolean isSoundbankSynth = false;

	private GUIConfig guiConfig = new GUIConfig();

	// instrument individual panels
	private MelodyPanel melodyPanel;
	private BassPanel bassPanel;

	// instrument panels added into scrollpanes
	private List<ChordPanel> chordPanels = new ArrayList<>();
	private List<ArpPanel> arpPanels = new ArrayList<>();
	private List<DrumPanel> drumPanels = new ArrayList<>();

	// arrangement
	private Arrangement arrangement;
	NumPanel arrangementVariationChance;
	JCheckBox arrangementManualOverride;

	// instrument scrollers
	JTabbedPane instrumentTabPane = new JTabbedPane(JTabbedPane.TOP);
	Dimension scrollPaneDimension = new Dimension(1600, 200);
	Dimension scrollPaneDimensionToggled = new Dimension(1000, 200);

	JScrollPane drumScrollPane;
	JScrollPane chordScrollPane;
	JScrollPane arpScrollPane;
	JScrollPane arrangementScrollPane;
	JScrollPane arrangementActualScrollPane;
	JTable scrollableArrangementTable;
	JTable scrollableArrangementActualTable;

	// instrument global settings
	JTextField bannedInsts;
	JCheckBox useAllInsts;
	JButton reinitInstPools;

	// main title settings
	JLabel mainTitle;
	JLabel subTitle;
	JButton switchDarkMode;
	Color messageColorDarkMode = new Color(200, 200, 200);
	Color messageColorLightMode = new Color(120, 120, 200);
	Color actualArrangementDarkMode = new Color(50, 50, 50);
	Color actualArrangementLightMode = new Color(120, 120, 200);

	// macro params
	JTextField soundbankFilename;
	JTextField pieceLength;
	JComboBox<String> scaleMode;
	JComboBox<String> fixedLengthChords;
	JCheckBox useArrangement;
	JCheckBox randomizeArrangementOnCompose;
	JCheckBox globalSwingOverride;
	NumPanel globalSwingOverrideValue;


	// chord variety settings
	NumPanel spiceChance;
	NumPanel chordSlashChance;
	JCheckBox spiceAllowDimAug;
	JCheckBox spiceAllow9th13th;


	// add/skip instruments
	JCheckBox addChords;
	JCheckBox addArps;
	JCheckBox addDrums;

	// melody gen settings
	JCheckBox arpCopyMelodyInst;
	NumPanel maxJump;
	NumPanel maxExceptions;
	NumPanel melodyAlternateRhythmChance;
	NumPanel melodySameRhythmChance;
	NumPanel melodyUseOldAlgoChance;
	JCheckBox randomMelodyOnRegenerate;
	JCheckBox melodyFirstNoteFromChord;
	JCheckBox randomChordNote;
	NumPanel melodySplitChance;
	NumPanel melodyExceptionChance;
	NumPanel melodyQuickness;

	// bass gen settings
	// - there's nothing here - 

	// chord gen settings
	JTextField randomChordsToGenerate;
	JCheckBox randomChordsGenerateOnCompose;
	JCheckBox randomChordDelay;
	JCheckBox randomChordStrum;
	JCheckBox randomChordSplit;
	JCheckBox randomChordTranspose;
	JCheckBox randomChordPattern;
	JTextField randomChordSustainChance;
	JTextField randomChordShiftChance;
	JTextField randomChordMaxSplitChance;
	JCheckBox randomChordUseChordFill;
	JComboBox<String> randomChordStretchType;
	JComboBox<String> randomChordStretchPicker;

	// arp gen settings
	JTextField randomArpsToGenerate;
	JCheckBox randomArpsGenerateOnCompose;
	JCheckBox randomArpTranspose;
	JCheckBox randomArpPattern;
	JCheckBox randomArpHitsPerPattern;
	JCheckBox randomArpAllSameInst;
	JCheckBox randomArpAllSameHits;
	JTextField arpShiftChance;
	JComboBox<String> randomArpHitsPicker;
	JCheckBox randomArpUseChordFill;
	JComboBox<String> randomArpStretchType;
	JComboBox<String> randomArpStretchPicker;
	JCheckBox randomArpUseOctaveAdjustments;
	JTextField randomArpMaxSwing;


	// drum gen settings
	JTextField randomDrumsToGenerate;
	JCheckBox randomDrumsGenerateOnCompose;
	JTextField randomDrumMaxSwingAdjust;
	JCheckBox randomDrumSlide;
	JCheckBox randomDrumPattern;
	JTextField randomDrumVelocityPatternChance;
	JTextField randomDrumShiftChance;
	JCheckBox randomDrumUseChordFill;

	// chord settings - progression
	JComboBox<String> firstChordSelection;
	JComboBox<String> lastChordSelection;
	int firstChord = 0;
	int lastChord = 0;
	JCheckBox userChordsEnabled;
	JTextField userChords;
	JTextField userChordsDurations;

	// randomization button settings
	JCheckBox randomizeInstOnComposeOrGen;
	JCheckBox randomizeBmpTransOnCompose;
	JCheckBox randomizeChordStrumsOnCompose;
	JCheckBox arpAffectsBpm;
	JTextField mainBpm;
	NumPanel transposeScore;
	JButton switchOnComposeRandom;

	// seed / midi
	JTextField randomSeed;
	int lastRandomSeed = 0;
	double realBpm = 60;

	JList<File> generatedMidi;
	Sequencer sequencer = null;
	File currentMidi = null;
	MidiDevice device = null;

	JCheckBox showScore;
	JComboBox<String> showScorePicker;
	JCheckBox midiMode;
	JComboBox<String> midiModeDevices;
	JCheckBox collapseDrumTracks;

	JButton compose;
	JButton regenerate;
	JButton startMidi;
	JButton stopMidi;

	Thread cycle;
	JCheckBox useVolumeSliders;
	JSlider slider;
	JLabel currentTime;
	JLabel totalTime;
	JLabel sectionText;
	boolean isKeySeeking = false;
	boolean isDragging = false;
	long pauseMs;

	JLabel tipLabel;
	JLabel currentChords = new JLabel("Chords:[]");
	JLabel messageLabel;

	JPanel everythingPanel;
	JScrollPane everythingPane;

	private static MidiGeneratorGUI midiGeneratorGUI = null;

	private static GridBagConstraints constraints = new GridBagConstraints();

	public static void main(String args[]) {
		FlatDarculaLaf.install();
		isDarkMode = true;
		midiGeneratorGUI = new MidiGeneratorGUI("General MIDI Generator (BETA)");
	}

	public MidiGeneratorGUI(String title) {
		super(title);

		everythingPanel = new JPanel();
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


		// chord tool tip

		JPanel chordToolTip = new JPanel();
		/*tipLabel = new JLabel(
				"Chord meaning: 1 = I(major), 10 = i(minor), 100 = I(aug), 1000 = I(dim), 10000 = I7(major), "
						+ "100000 = i7(minor), 1000000 = 9th, 10000000 = 13th, 100000000 = Sus4, 1000000000 = Sus2, 10000000000 = Sus7");*/
		tipLabel = new JLabel(
				"[Allowed chords: C/D/E/F/G/A/B + m / aug / dim / maj7 / m7 / 9 / 13 / sus4 / sus2 / sus7]");
		chordToolTip.add(tipLabel);

		userChordsEnabled = new JCheckBox();
		userChordsEnabled.setSelected(false);


		chordToolTip.add(new JLabel("Custom chords:"));
		chordToolTip.add(userChordsEnabled);

		userChords = new JTextField("R", 15);
		chordToolTip.add(new JLabel("Chords:"));
		chordToolTip.add(userChords);
		userChordsDurations = new JTextField("2,2,2,2", 6);
		chordToolTip.add(new JLabel("Chord durations (max. 8):"));
		chordToolTip.add(userChordsDurations);

		constraints.gridy = 355;
		constraints.anchor = GridBagConstraints.CENTER;
		everythingPanel.add(chordToolTip, constraints);


		// ---- INSTRUMENTS ----
		{
			// melody
			initMelodyGenSettings(20, GridBagConstraints.WEST);
			initMelody(25, GridBagConstraints.WEST);

			createHorizontalSeparator(30, this);

			// bass
			initBass(33, GridBagConstraints.WEST);
			createHorizontalSeparator(35, this);

			// chords
			initChordGenSettings(40, GridBagConstraints.WEST);
			initChords(50, GridBagConstraints.CENTER);
			//createHorizontalSeparator(100, this);

			// arps
			initArpGenSettings(105, GridBagConstraints.WEST);
			initArps(110, GridBagConstraints.CENTER);
			//createHorizontalSeparator(150, this);


			// drums
			initDrumGenSettings(190, GridBagConstraints.WEST);
			initDrums(200, GridBagConstraints.CENTER);
			//createHorizontalSeparator(290, this);

			// arrangement
			initArrangementSettings(280, GridBagConstraints.WEST);
		}

		constraints.gridy = 320;
		everythingPanel.add(instrumentTabPane, constraints);


		// ---- OTHER SETTINGS ----
		{

			// randomization buttons
			initRandomButtons(330, GridBagConstraints.CENTER);

			initMacroParams(340, GridBagConstraints.CENTER);

			// chord settings - variety/spice
			// chord settings - progressions
			initChordSettings(350, GridBagConstraints.CENTER);


		}


		createHorizontalSeparator(400, this);

		// ---- CONTROL PANEL -----
		initControlPanel(410, GridBagConstraints.CENTER);

		// ---- PLAY PANEL ----
		initPlayPanel(420, GridBagConstraints.CENTER);
		initSliderPanel(440, GridBagConstraints.CENTER);

		// --- GENERATED MIDI DRAG n DROP ---

		constraints.anchor = GridBagConstraints.CENTER;

		// ---- MESSAGE PANEL ----

		JPanel messagePanel = new JPanel();
		messageLabel = new JLabel("Click something!");
		messageLabel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		messagePanel.add(messageLabel);
		constraints.gridy = 999;
		everythingPanel.add(messagePanel, constraints);

		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		int screenHeight = d.height;
		int screenWidth = d.width;
		setSize(screenWidth / 2, screenHeight / 2);
		setLocation(screenWidth / 4, screenHeight / 4);

		setFocusable(true);
		requestFocus();
		requestFocusInWindow();

		isDarkMode = !isDarkMode;
		switchDarkMode();

		everythingPane.setViewportView(everythingPanel);
		add(everythingPane, constraints);

		recalculateTabPaneCounts();

		pack();
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
	}

	private void initTitles(int startY, int anchorSide) {
		mainTitle = new JLabel("General MIDI Generator (Beta)");
		mainTitle.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		subTitle = new JLabel("by Vibe Historian");
		subTitle.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		constraints.weightx = 100;
		constraints.weighty = 100;
		constraints.gridx = 0;
		constraints.gridy = startY;
		constraints.gridwidth = 3;
		constraints.gridheight = 1;
		constraints.anchor = anchorSide;
		everythingPanel.add(mainTitle, constraints);
		constraints.gridy = 1;
		everythingPanel.add(subTitle, constraints);

		JPanel darkModeSwitchPanel = new JPanel();

		constraints.gridy = startY + 3;
		darkModeSwitchPanel.add(makeButton("Toggle Dark Mode", this, "SwitchDarkMode"));

		darkModeSwitchPanel.add(makeButton("Toggle Adv. Features", this, "ToggleAdv"));

		everythingPanel.add(darkModeSwitchPanel, constraints);
	}

	private void initMacroParams(int startY, int anchorSide) {
		JPanel macroParams = new JPanel();

		fixedLengthChords = new JComboBox<>();
		MidiUtils.addAllToJComboBox(new String[] { "4", "8", "NOT" }, fixedLengthChords);
		fixedLengthChords.setSelectedItem(0);
		JLabel chordDurationFixedLabel = new JLabel("Chord duration fixed: ");
		macroParams.add(chordDurationFixedLabel);
		macroParams.add(fixedLengthChords);

		globalSwingOverride = new JCheckBox("Global Swing override:", false);
		globalSwingOverrideValue = new NumPanel("", 50);
		macroParams.add(globalSwingOverride);
		macroParams.add(globalSwingOverrideValue);


		useAllInsts = new JCheckBox("Use all inst., except:", false);
		macroParams.add(useAllInsts);
		bannedInsts = new JTextField("", 8);
		macroParams.add(bannedInsts);
		reinitInstPools = makeButton("Initialize all inst.", this, "InitAllInsts");
		macroParams.add(reinitInstPools);

		/*toggleableComponents.add(soundbankFilename);
		toggleableComponents.add(fixedLengthChords);
		toggleableComponents.add(globalSwingOverride);
		toggleableComponents.add(globalSwingOverrideValue);
		toggleableComponents.add(useAllInsts);
		toggleableComponents.add(bannedInsts);
		toggleableComponents.add(reinitInstPools);
		toggleableComponents.add(soundbankLabel);
		toggleableComponents.add(chordDurationFixedLabel);*/
		toggleableComponents.add(macroParams);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(macroParams, constraints);
	}

	private void initMelodyGenSettings(int startY, int anchorSide) {
		JPanel melodySettingsPanel = new JPanel();
		melodySettingsPanel.add(new JLabel("Melody settings:"));

		arpCopyMelodyInst = new JCheckBox("Force copy Arp#1 inst.", true);
		melodySettingsPanel.add(arpCopyMelodyInst);
		toggleableComponents.add(arpCopyMelodyInst);

		maxJump = new NumPanel("Max Note Jump", 1, 0, 4);
		maxExceptions = new NumPanel("Max Exceptions", 2, 0, 4);
		melodyAlternateRhythmChance = new NumPanel("Alternating Rhythm", 50);
		melodySameRhythmChance = new NumPanel("Doubled rhythm", 50);
		melodyUseOldAlgoChance = new NumPanel("Legacy algo", 0);
		melodySplitChance = new NumPanel("Split%", 50);
		melodyExceptionChance = new NumPanel("Exception%", 50);
		melodyQuickness = new NumPanel("Quickness", 100);
		melodySettingsPanel.add(maxJump);
		melodySettingsPanel.add(maxExceptions);
		melodySettingsPanel.add(melodyAlternateRhythmChance);
		melodySettingsPanel.add(melodySameRhythmChance);
		melodySettingsPanel.add(melodySplitChance);
		melodySettingsPanel.add(melodyExceptionChance);
		melodySettingsPanel.add(melodyQuickness);
		//melodySettingsPanel.add(new JLabel("Legacy algorithm%:"));
		//melodySettingsPanel.add(melodyUseOldAlgoChance);

		randomChordNote = new JCheckBox();
		randomChordNote.setSelected(true);
		melodyFirstNoteFromChord = new JCheckBox();
		melodyFirstNoteFromChord.setSelected(true);


		//melodySettingsPanel.add(new JLabel("Note#1 From Chord:"));
		//melodySettingsPanel.add(melodyFirstNoteFromChord);
		//melodySettingsPanel.add(new JLabel("But Randomized:"));
		//melodySettingsPanel.add(randomChordNote);

		JButton generateUserMelodySeed = makeButton("Randomize seed", this, "GenMelody");
		JButton clearUserMelodySeed = makeButton("Clear(0)", this, "ClearMelody");
		randomMelodyOnRegenerate = new JCheckBox("On regen", false);
		melodySettingsPanel.add(generateUserMelodySeed);
		melodySettingsPanel.add(randomMelodyOnRegenerate);
		melodySettingsPanel.add(clearUserMelodySeed);

		toggleableComponents.add(maxJump);
		toggleableComponents.add(maxExceptions);
		toggleableComponents.add(melodyAlternateRhythmChance);
		toggleableComponents.add(melodySameRhythmChance);
		toggleableComponents.add(melodySplitChance);
		toggleableComponents.add(melodyExceptionChance);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(melodySettingsPanel, constraints);
	}

	private void initMelody(int startY, int anchorSide) {
		melodyPanel = new MelodyPanel(this);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(melodyPanel, constraints);


	}


	private void initChordGenSettings(int startY, int anchorSide) {
		JPanel chordSettingsPanel = new JPanel();
		chordSettingsPanel.add(new JLabel("CHORDS"));

		addChords = new JCheckBox("Enable", true);
		chordSettingsPanel.add(addChords);

		JButton chordAddJButton = makeButton("+Chord", "AddChord");
		chordSettingsPanel.add(chordAddJButton);

		randomChordsToGenerate = new JTextField("2", 2);
		JButton randomizeChords = makeButton("Generate Chords:", "RandChords");
		randomChordsGenerateOnCompose = new JCheckBox("on Compose", true);
		chordSettingsPanel.add(randomizeChords);
		chordSettingsPanel.add(randomChordsToGenerate);
		chordSettingsPanel.add(randomChordsGenerateOnCompose);

		randomChordStretchType = new JComboBox<>();
		MidiUtils.addAllToJComboBox(new String[] { "NONE", "FIXED", "AT_MOST" },
				randomChordStretchType);
		randomChordStretchType.setSelectedItem("NONE");
		JLabel stretchLabel = new JLabel("StretCh.");
		chordSettingsPanel.add(stretchLabel);
		chordSettingsPanel.add(randomChordStretchType);
		randomChordStretchPicker = new JComboBox<>();
		MidiUtils.addAllToJComboBox(new String[] { "3", "4", "5", "6" }, randomChordStretchPicker);
		randomChordStretchPicker.setSelectedItem("4");
		chordSettingsPanel.add(randomChordStretchPicker);

		randomChordDelay = new JCheckBox("Delay", false);
		randomChordStrum = new JCheckBox("Strum", true);
		randomChordSplit = new JCheckBox("RandSplit", false);
		randomChordTranspose = new JCheckBox("Transpose", true);
		randomChordSustainChance = new JTextField("25", 2);
		randomChordPattern = new JCheckBox("Presets", false);
		randomChordShiftChance = new JTextField("25", 3);
		randomChordUseChordFill = new JCheckBox("Fills", true);
		randomChordMaxSplitChance = new JTextField("25", 3);

		chordSettingsPanel.add(randomChordDelay);
		chordSettingsPanel.add(randomChordStrum);
		chordSettingsPanel.add(randomChordSplit);
		chordSettingsPanel.add(randomChordTranspose);
		chordSettingsPanel.add(randomChordUseChordFill);
		JLabel chordChance = new JLabel("Chord%");
		chordSettingsPanel.add(chordChance);
		chordSettingsPanel.add(randomChordSustainChance);
		JLabel maxSplitChance = new JLabel("Max split%");
		chordSettingsPanel.add(maxSplitChance);
		chordSettingsPanel.add(randomChordMaxSplitChance);
		chordSettingsPanel.add(randomChordPattern);
		JLabel chordShiftChance = new JLabel("Shift%");
		chordSettingsPanel.add(chordShiftChance);
		chordSettingsPanel.add(randomChordShiftChance);

		toggleableComponents.add(randomChordDelay);
		toggleableComponents.add(stretchLabel);
		toggleableComponents.add(randomChordStretchType);
		toggleableComponents.add(randomChordStretchPicker);
		toggleableComponents.add(randomChordStrum);
		toggleableComponents.add(randomChordSplit);
		toggleableComponents.add(randomChordTranspose);
		toggleableComponents.add(randomChordUseChordFill);
		toggleableComponents.add(chordChance);
		toggleableComponents.add(randomChordSustainChance);
		toggleableComponents.add(maxSplitChance);
		toggleableComponents.add(randomChordMaxSplitChance);
		toggleableComponents.add(randomChordPattern);
		toggleableComponents.add(chordShiftChance);
		toggleableComponents.add(randomChordShiftChance);

		JButton clearChordPatternSeeds = makeButton("Clear presets", "ClearChordPatterns");

		chordSettingsPanel.add(clearChordPatternSeeds);
		toggleableComponents.add(clearChordPatternSeeds);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(chordSettingsPanel, constraints);
	}

	private void initChords(int startY, int anchorSide) {
		// ---- CHORDS ----
		// gridy 50 - 99 range


		JPanel scrollableChordPanels = new JPanel();
		scrollableChordPanels.setLayout(new BoxLayout(scrollableChordPanels, BoxLayout.Y_AXIS));
		scrollableChordPanels.setAutoscrolls(true);

		chordScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(1300, 150);
			}
		};
		chordScrollPane.setViewportView(scrollableChordPanels);
		chordScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		chordScrollPane.getVerticalScrollBar().setUnitIncrement(16);


		createRandomChordPanels(Integer.valueOf(randomChordsToGenerate.getText()), false);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Chords", chordScrollPane);
	}

	private void initArpGenSettings(int startY, int anchorSide) {
		JPanel arpsSettingsPanel = new JPanel();
		arpsSettingsPanel.add(new JLabel("ARPS      "));

		addArps = new JCheckBox("Enable", true);
		arpsSettingsPanel.add(addArps);

		JButton arpAddJButton = makeButton("  +Arp ", "AddArp");
		arpsSettingsPanel.add(arpAddJButton);

		randomArpsToGenerate = new JTextField("3", 2);
		JButton randomizeArps = makeButton("Generate Arps:    ", "RandArps");
		randomArpsGenerateOnCompose = new JCheckBox("on Compose", true);
		arpsSettingsPanel.add(randomizeArps);
		arpsSettingsPanel.add(randomArpsToGenerate);
		arpsSettingsPanel.add(randomArpsGenerateOnCompose);

		randomArpStretchType = new JComboBox<>();
		MidiUtils.addAllToJComboBox(new String[] { "NONE", "FIXED", "AT_MOST" },
				randomArpStretchType);
		randomArpStretchType.setSelectedItem("AT_MOST");
		JLabel stretchLabel = new JLabel("StretCh.");
		arpsSettingsPanel.add(stretchLabel);
		arpsSettingsPanel.add(randomArpStretchType);
		randomArpStretchPicker = new JComboBox<>();
		MidiUtils.addAllToJComboBox(new String[] { "3", "4", "5", "6" }, randomArpStretchPicker);
		randomArpStretchPicker.setSelectedItem("4");
		arpsSettingsPanel.add(randomArpStretchPicker);

		randomArpTranspose = new JCheckBox("Transpose", true);
		randomArpPattern = new JCheckBox("Presets", false);
		randomArpHitsPicker = new JComboBox<String>();
		MidiUtils.addAllToJComboBox(new String[] { "1", "2", "3", "4", "5", "6", "7", "8" },
				randomArpHitsPicker);
		randomArpHitsPicker.setSelectedItem("4");
		randomArpHitsPerPattern = new JCheckBox("Random#", true);
		randomArpAllSameInst = new JCheckBox("One inst.", false);
		randomArpAllSameHits = new JCheckBox("One #", true);
		randomArpUseChordFill = new JCheckBox("Fills", true);
		arpShiftChance = new JTextField("25", 3);
		randomArpUseOctaveAdjustments = new JCheckBox("Rand. oct.", false);
		randomArpMaxSwing = new JTextField("50", 3);

		arpsSettingsPanel.add(new JLabel("Arp#"));
		arpsSettingsPanel.add(randomArpHitsPicker);
		arpsSettingsPanel.add(randomArpHitsPerPattern);
		arpsSettingsPanel.add(randomArpAllSameHits);
		arpsSettingsPanel.add(randomArpAllSameInst);
		arpsSettingsPanel.add(randomArpUseChordFill);
		arpsSettingsPanel.add(randomArpTranspose);
		arpsSettingsPanel.add(randomArpUseOctaveAdjustments);
		JLabel arpSwingLabel = new JLabel("Swing%");
		arpsSettingsPanel.add(arpSwingLabel);
		arpsSettingsPanel.add(randomArpMaxSwing);
		arpsSettingsPanel.add(randomArpPattern);
		JLabel patternShiftLabel = new JLabel("Pattern shift%");
		arpsSettingsPanel.add(patternShiftLabel);
		arpsSettingsPanel.add(arpShiftChance);

		toggleableComponents.add(stretchLabel);
		toggleableComponents.add(randomArpStretchType);
		toggleableComponents.add(randomArpStretchPicker);
		toggleableComponents.add(randomArpAllSameHits);
		toggleableComponents.add(randomArpAllSameInst);
		toggleableComponents.add(randomArpUseChordFill);
		toggleableComponents.add(randomArpTranspose);
		toggleableComponents.add(randomArpUseOctaveAdjustments);
		toggleableComponents.add(arpSwingLabel);
		toggleableComponents.add(randomArpMaxSwing);
		toggleableComponents.add(randomArpPattern);
		toggleableComponents.add(patternShiftLabel);
		toggleableComponents.add(arpShiftChance);

		JButton clearArpPatternSeeds = makeButton("Clear presets", "ClearArpPatterns");

		arpsSettingsPanel.add(clearArpPatternSeeds);
		toggleableComponents.add(clearArpPatternSeeds);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(arpsSettingsPanel, constraints);
	}

	private void initArps(int startY, int anchorSide) {
		// --- ARPS -----------


		JPanel scrollableArpPanels = new JPanel();
		scrollableArpPanels.setLayout(new BoxLayout(scrollableArpPanels, BoxLayout.Y_AXIS));
		scrollableArpPanels.setAutoscrolls(true);

		arpScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(1300, 150);
			}
		};
		arpScrollPane.setViewportView(scrollableArpPanels);
		arpScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		arpScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		createRandomArpPanels(Integer.valueOf(randomArpsToGenerate.getText()), false);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Arps", arpScrollPane);
	}

	private void initBass(int startY, int anchorSide) {
		bassPanel = new BassPanel(this);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(bassPanel, constraints);
	}

	private void initDrumGenSettings(int startY, int anchorSide) {
		JPanel drumsPanel = new JPanel();
		drumsPanel.add(new JLabel("DRUMS "));

		addDrums = new JCheckBox("Enable", true);
		drumsPanel.add(addDrums);
		//drumsPanel.add(drumInst);


		JButton drumAddJButton = makeButton(" +Drum ", "AddDrum");
		drumsPanel.add(drumAddJButton);

		randomDrumsToGenerate = new JTextField("8", 2);
		JButton randomizeDrums = makeButton("Generate Drums: ", "RandDrums");
		randomDrumsGenerateOnCompose = new JCheckBox("on Compose", true);
		drumsPanel.add(randomizeDrums);
		drumsPanel.add(randomDrumsToGenerate);
		drumsPanel.add(randomDrumsGenerateOnCompose);

		JButton clearPatternSeeds = makeButton("Clear patterns", "ClearPatterns");

		randomDrumMaxSwingAdjust = new JTextField("20", 2);
		randomDrumSlide = new JCheckBox("Random delay", false);
		randomDrumPattern = new JCheckBox("Pattern presets", true);
		randomDrumVelocityPatternChance = new JTextField("50", 3);
		randomDrumShiftChance = new JTextField("25", 3);

		drumsPanel.add(new JLabel("Max swing%+-"));
		drumsPanel.add(randomDrumMaxSwingAdjust);

		drumsPanel.add(randomDrumSlide);
		drumsPanel.add(randomDrumPattern);
		JLabel velocityPatternLabel = new JLabel("Velocity pattern%");
		drumsPanel.add(velocityPatternLabel);
		drumsPanel.add(randomDrumVelocityPatternChance);
		JLabel patternShiftLabel = new JLabel("Pattern shift%");
		drumsPanel.add(patternShiftLabel);
		drumsPanel.add(randomDrumShiftChance);
		drumsPanel.add(clearPatternSeeds);

		toggleableComponents.add(randomDrumSlide);
		toggleableComponents.add(randomDrumPattern);
		toggleableComponents.add(velocityPatternLabel);
		toggleableComponents.add(randomDrumVelocityPatternChance);
		toggleableComponents.add(patternShiftLabel);
		toggleableComponents.add(randomDrumShiftChance);
		toggleableComponents.add(clearPatternSeeds);

		collapseDrumTracks = new JCheckBox("Collapse Drum Tracks", true);
		drumsPanel.add(collapseDrumTracks);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(drumsPanel, constraints);
	}

	private void initDrums(int startY, int anchorSide) {
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


		createRandomDrumPanels((Integer.valueOf(randomDrumsToGenerate.getText())), false);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Drums", drumScrollPane);

	}

	private void handleArrangementAction(String action, int seed, int maxLength) {
		if (action.equalsIgnoreCase("ArrangementReset")) {
			arrangement.generateDefaultArrangement();
			pieceLength.setText("12");
		}

		if (action.equalsIgnoreCase("ArrangementAddLast")) {
			arrangement.addSectionLast(scrollableArrangementTable);
			if (arrangement.getSections().size() > maxLength) {
				pieceLength.setText("" + ++maxLength);
			}
		}

		if (action.equalsIgnoreCase("ArrangementRemoveLast")) {
			arrangement.removeSectionLast(scrollableArrangementTable);
			//pieceLength.setText("" + --maxLength);
		}

		if (action.equalsIgnoreCase("ArrangementRandomize")) {
			// on compose -> this must happen before compose part
			arrangement.randomizeFully(maxLength, seed, 30, 30, 2, 4, 15);
		}
		scrollableArrangementTable.setModel(arrangement.convertToTableModel());
	}

	private void initArrangementSettings(int startY, int anchorSide) {
		JPanel arrangementSettings = new JPanel();

		arrangementSettings.add(new JLabel("ARRANGEMENT"));

		useArrangement = new JCheckBox("Enable", false);
		arrangementSettings.add(useArrangement);

		pieceLength = new JTextField("12", 2);
		//arrangementSettings.add(new JLabel("Max Length:"));


		JButton resetArrangementBtn = makeButton("Reset arr.", "ArrangementReset");

		JButton randomizeArrangementBtn = makeButton("Randomize sections:", "ArrangementRandomize");

		randomizeArrangementOnCompose = new JCheckBox("on Compose", true);


		JButton addLastSectionBtn = makeButton("Add section", "ArrangementAddLast");

		JButton removeLastSectionBtn = makeButton("Remove last section", "ArrangementRemoveLast");

		arrangementSettings.add(randomizeArrangementBtn);
		arrangementSettings.add(pieceLength);
		arrangementSettings.add(randomizeArrangementOnCompose);

		arrangementVariationChance = new NumPanel("Variation", 30);
		arrangementSettings.add(arrangementVariationChance);

		arrangementSettings.add(addLastSectionBtn);
		arrangementSettings.add(removeLastSectionBtn);
		arrangementSettings.add(resetArrangementBtn);

		arrangementManualOverride = new JCheckBox("Allow manual change", true);
		arrangementSettings.add(arrangementManualOverride);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(arrangementSettings, constraints);

		scrollableArrangementTable = new JTable(5, 5);
		TableModel model = new DefaultTableModel(7, 11);


		scrollableArrangementTable.setModel(model);
		arrangementScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};
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
		arrangement.generateDefaultArrangement();
		if (useArrangement.isSelected()) {
			arrangement.setPreviewChorus(false);
		} else {
			arrangement.setPreviewChorus(true);
		}
		scrollableArrangementTable.setModel(arrangement.convertToTableModel());

		/*scrollableArrangementTable.getColumnModel()
				.addColumnModelListener(new TableColumnModelListener() {
		
					@Override
					public void columnAdded(TableColumnModelEvent e) {
						// 
		
					}
		
					@Override
					public void columnRemoved(TableColumnModelEvent e) {
						//
		
					}
		
					@Override
					public void columnMoved(TableColumnModelEvent e) {
						MelodyGenerator.ARRANGEMENT.resortByIndexes(scrollableArrangementTable);
		
					}
		
					@Override
					public void columnMarginChanged(ChangeEvent e) {
						// 
		
					}
		
					@Override
					public void columnSelectionChanged(ListSelectionEvent e) {
						// 
		
					}
					//etc.
				});
		*/
		//constraints.gridy = startY;
		//constraints.anchor = anchorSide;

		scrollableArrangementActualTable = new JTable(5, 5) {
			private static final long serialVersionUID = 1L;

			@Override
			public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
				Component comp = super.prepareRenderer(renderer, row, col);
				String value = (String) getModel().getValueAt(row, col);
				if (value == null)
					return comp;
				comp.setForeground(actualArrangementDarkMode);
				if (row >= 2) {

					// 2,3,4,5,6 -> melody, bass, chord, arp, drum counts
					int[] maxCounts = new int[] { 0, 0, 1, 1, chordPanels.size(), arpPanels.size(),
							drumPanels.size() };
					if (value.equalsIgnoreCase("")) {
						comp.setBackground(new Color(70, 70, 70));
					} else {
						int count = StringUtils.countMatches(value, ",") + 1;
						int color = 120 + (70 * count) / maxCounts[row];
						comp.setBackground(new Color(color, color, color));
					}
				} else {
					comp.setBackground(new Color(100, 100, 150));
				}

				return comp;
			}
		};
		TableModel actualModel = new DefaultTableModel(7, 11);


		scrollableArrangementActualTable.setModel(actualModel);
		arrangementActualScrollPane = new JScrollPane() {
			@Override
			public Dimension getPreferredSize() {
				return scrollPaneDimension;
			}
		};
		arrangementActualScrollPane.setViewportView(scrollableArrangementActualTable);
		JList<String> actualList = new JList<>();
		actualList.setListData(
				new String[] { "Section", "Bars", "Melody", "Bass", "Chord", "Arp", "Drum" });
		actualList.setFixedCellHeight(scrollableArrangementActualTable.getRowHeight()
				+ scrollableArrangementActualTable.getRowMargin());
		arrangementActualScrollPane.setRowHeaderView(actualList);
		arrangementActualScrollPane
				.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		arrangementActualScrollPane.getVerticalScrollBar().setUnitIncrement(16);


		instrumentTabPane.addTab("Arrangement", arrangementScrollPane);
		instrumentTabPane.addTab("Generated", arrangementActualScrollPane);
	}

	private void initRandomButtons(int startY, int anchorSide) {
		JPanel randomButtonsPanel = new JPanel();

		JButton randomizeInstruments = makeButton("Randomize Inst.", "RandomizeInst");

		JButton randomizeBpmTransp = makeButton("Randomize BPM+Transpose", "RandomizeBpmTrans");

		randomizeInstOnComposeOrGen = new JCheckBox("on Compose/Gen");
		randomizeBmpTransOnCompose = new JCheckBox("on Compose");
		randomizeInstOnComposeOrGen.setSelected(true);
		randomizeBmpTransOnCompose.setSelected(true);


		constraints.anchor = GridBagConstraints.CENTER;


		randomButtonsPanel.add(randomizeInstruments);
		randomButtonsPanel.add(randomizeInstOnComposeOrGen);
		randomButtonsPanel.add(randomizeBpmTransp);
		randomButtonsPanel.add(randomizeBmpTransOnCompose);
		arpAffectsBpm = new JCheckBox("Slowed by ARP", true);
		randomButtonsPanel.add(arpAffectsBpm);

		JButton randomizeStrums = makeButton("Randomize strums", "RandStrums");
		randomButtonsPanel.add(randomizeStrums);

		randomizeChordStrumsOnCompose = new JCheckBox("On compose");
		randomizeChordStrumsOnCompose.setSelected(true);
		randomButtonsPanel.add(randomizeChordStrumsOnCompose);

		switchOnComposeRandom = makeButton("Uncheck all 'on Compose'", "UncheckComposeRandom");
		randomButtonsPanel.add(switchOnComposeRandom);

		toggleableComponents.add(randomizeStrums);
		toggleableComponents.add(randomizeChordStrumsOnCompose);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(randomButtonsPanel, constraints);
	}

	private void initChordSettings(int startY, int anchorSide) {
		// CHORD SETTINGS 1 - chord variety 
		JPanel chordSettingsProgressionPanel = new JPanel();
		toggleableComponents.add(chordSettingsProgressionPanel);

		chordSlashChance = new NumPanel("Chord1 slash%", 25);
		chordSettingsProgressionPanel.add(chordSlashChance);

		spiceChance = new NumPanel("Spice", 8);
		chordSettingsProgressionPanel.add(spiceChance);

		spiceAllowDimAug = new JCheckBox("Dim/Aug");
		spiceAllowDimAug.setSelected(false);
		chordSettingsProgressionPanel.add(spiceAllowDimAug);

		spiceAllow9th13th = new JCheckBox("9th/13th");
		spiceAllow9th13th.setSelected(false);
		chordSettingsProgressionPanel.add(spiceAllow9th13th);

		// CHORD SETTINGS 2 - chord progression
		firstChordSelection = new JComboBox<String>();
		firstChordSelection.addItem("R");
		/*firstChordSelection.addItem("I");
		firstChordSelection.addItem("V");
		firstChordSelection.addItem("vi");
		firstChordSelection.addItemListener(this);
		chordSettingsProgressionPanel.add(new JLabel("First Chord:"));
		chordSettingsProgressionPanel.add(firstChordSelection);*/
		lastChordSelection = new JComboBox<String>();
		lastChordSelection.addItem("R");
		lastChordSelection.addItem("I");
		lastChordSelection.addItem("V");
		lastChordSelection.addItem("vi");
		lastChordSelection.addItemListener(this);
		lastChordSelection.setSelectedIndex(0);
		chordSettingsProgressionPanel.add(new JLabel("Last Chord:"));
		chordSettingsProgressionPanel.add(lastChordSelection);


		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(chordSettingsProgressionPanel, constraints);
	}

	private static String microsecondsToTimeString(long l) {
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
		if (ms == 0)
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

	public void midiNavigate(long time) {
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

	private void initSliderPanel(int startY, int anchorSide) {
		JPanel sliderPanel = new JPanel();
		sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.X_AXIS));
		sliderPanel.setPreferredSize(new Dimension(800, 20));


		slider = new JSlider();
		slider.setMaximum(0);
		slider.setToolTipText("Test");

		//slider.setMinimumSize(new Dimension(1000, 3));
		slider.addMouseListener(new MouseAdapter() {

			@Override
			public void mousePressed(MouseEvent e) {

				isDragging = true;
			}

			@Override
			public void mouseReleased(MouseEvent e) {

				if (isDragging) {
					if (sequencer != null)
						midiNavigate(((long) slider.getValue()) * 1000);
					isDragging = false;
				}
			}
		});
		sliderPanel.add(slider);
		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(sliderPanel, constraints);

		JPanel sliderInfoPanel = new JPanel();
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


		// init thread

		Thread cycle = new Thread() {

			public void run() {

				while (true) {
					if (sequencer != null && sequencer.isRunning()) {
						if (!isDragging && !isKeySeeking)
							slider.setValue((int) (sequencer.getMicrosecondPosition() / 1000));
						if (!isDragging && !isKeySeeking)
							currentTime.setText(
									microsecondsToTimeString(sequencer.getMicrosecondPosition()));
						else
							currentTime.setText(millisecondsToTimeString(slider.getValue()));
					} else {
						//if (!isDragging && !isKeySeeking) {
						//slider.setValue((int) (pauseMs / 1000));
						//}
						//if (!isDragging && !isKeySeeking)
						//currentTime.setText(microsecondsToTimeString(core.midiPauseProgMs));
						//else
						//currentTime.setText(millisecondsToTimeString(slider.getValue()));
					}
					if (arrangement != null && slider.getMaximum() > 0) {
						int val = slider.getValue();
						int arrangementSize = arrangement.getSections().stream()
								.mapToInt(e -> e.getMeasures()).sum();
						if (arrangementSize == 0) {
							arrangementSize = 1;
							System.out.println("ARRANGEMENT WAS 0!");
						}
						int divisor = slider.getMaximum() / arrangementSize;
						int sectIndex = (val - 1) / divisor;
						if (sectIndex >= arrangementSize) {
							sectionText.setText("End");
						} else {
							if (useArrangement.isSelected()) {
								Section sec = null;
								int sizeCounter = 0;
								for (Section arrSec : arrangement.getSections()) {
									if (sizeCounter == sectIndex
											|| (sectIndex < sizeCounter + arrSec.getMeasures())) {
										sec = arrSec;
										break;
									}
									sizeCounter += arrSec.getMeasures();
								}
								String sectionName = sec.getType().toString();
								sectionText.setText(sectionName);
							} else {
								sectionText.setText("ALL INST");
							}

						}


					}

					try {
						sleep(25);
					} catch (InterruptedException e) {

					}
				}
			}
		};
		cycle.start();
	}

	private void initControlPanel(int startY, int anchorSide) {
		JPanel controlPanel = new JPanel();

		transposeScore = new NumPanel("Global Transpose", 0, -24, 24);
		controlPanel.add(transposeScore);

		Random bpmRand = new Random();
		mainBpm = new JTextField(String.valueOf(bpmRand.nextInt(30) + 50), 3);


		controlPanel.add(new JLabel("BPM:"));
		controlPanel.add(mainBpm);
		scaleMode = new JComboBox<String>();
		String[] scaleModes = new String[MidiUtils.ScaleMode.values().length];
		for (int i = 0; i < MidiUtils.ScaleMode.values().length; i++) {
			scaleModes[i] = MidiUtils.ScaleMode.values()[i].toString();
		}
		MidiUtils.addAllToJComboBox(scaleModes, scaleMode);

		controlPanel.add(new JLabel("Mode:"));
		controlPanel.add(scaleMode);


		randomSeed = new JTextField("0", 8);
		compose = makeButton("COMPOSE", "Compose");
		compose.setBackground(new Color(150, 150, 60));
		regenerate = makeButton("Regenerate", "Regenerate");
		JButton copySeed = makeButton("Copy seed", "CopySeed");
		JButton copyChords = makeButton("Copy chords", "CopyChords");
		JButton clearSeed = makeButton("Clear All Seeds", "ClearSeed");

		JButton loadConfig = makeButton("Load Config", "LoadGUIConfig");

		controlPanel.add(new JLabel("Random Seed:"));
		controlPanel.add(randomSeed);
		controlPanel.add(compose);
		controlPanel.add(regenerate);
		controlPanel.add(copySeed);
		controlPanel.add(currentChords);
		controlPanel.add(copyChords);
		controlPanel.add(clearSeed);
		controlPanel.add(loadConfig);


		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(controlPanel, constraints);
	}

	private void initPlayPanel(int startY, int anchorSide) {

		JPanel playSavePanel = new JPanel();

		stopMidi = makeButton("STOP", "StopMidi");
		startMidi = makeButton("PLAY", "StartMidi");

		JButton save3Star = makeButton("Save 3*", "Save 3*");
		JButton save4Star = makeButton("Save 4*", "Save 4*");
		JButton save5Star = makeButton("Save 5*", "Save 5*");

		JButton saveWavFile = makeButton("Export as .wav", "SaveWavFile");

		showScore = new JCheckBox("Show score", true);
		showScorePicker = new JComboBox<String>();
		MidiUtils.addAllToJComboBox(new String[] { "w/o DRUMS", "DRUMS ONLY", "ALL" },
				showScorePicker);

		useVolumeSliders = new JCheckBox("Use vol. sliders", true);

		midiMode = new JCheckBox("MIDI transmitter mode (select device and regenerate)", true);
		midiModeDevices = new JComboBox<String>();
		MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
		MidiDevice dev = null;
		for (int i = 0; i < infos.length; i++) {
			try {
				dev = MidiSystem.getMidiDevice(infos[i]);
				if (dev.getMaxReceivers() != 0 && dev.getMaxTransmitters() == 0) {
					midiModeDevices.addItem(infos[i].toString());
					if (infos[i].toString().startsWith("loopMIDI")) {
						midiModeDevices.setSelectedItem(infos[i].toString());
					}
					System.out.println("Added device: " + infos[i].toString());
				}
			} catch (MidiUnavailableException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		midiModeDevices.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (device != null) {
					device.close();
				}
				device = null;

			}

		});

		generatedMidi = new JList<File>();
		generatedMidi.setTransferHandler(new FileTransferHandler());
		generatedMidi.setDragEnabled(true);

		playSavePanel.add(startMidi);
		playSavePanel.add(stopMidi);
		playSavePanel.add(save3Star);
		playSavePanel.add(save4Star);
		playSavePanel.add(save5Star);
		playSavePanel.add(saveWavFile);
		playSavePanel.add(new JLabel("Midi Drag'N'Drop:"));
		playSavePanel.add(generatedMidi);

		JPanel playSettingsPanel = new JPanel();

		soundbankFilename = new JTextField(SOUNDBANK_DEFAULT, 18);
		JLabel soundbankLabel = new JLabel("Soundbank name:");
		playSettingsPanel.add(soundbankLabel);
		playSettingsPanel.add(soundbankFilename);

		playSettingsPanel.add(showScore);
		playSettingsPanel.add(showScorePicker);
		playSettingsPanel.add(useVolumeSliders);
		playSettingsPanel.add(midiMode);
		playSettingsPanel.add(midiModeDevices);


		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(playSettingsPanel, constraints);

		constraints.gridy = startY + 5;
		constraints.anchor = anchorSide;
		everythingPanel.add(playSavePanel, constraints);
	}

	private void startVolumeSliderThread() {
		if (cycle != null && cycle.isAlive()) {
			System.out.println("Volume slider thread already exists!");
			return;
		}
		System.out.println("Starting new slider thread..!");
		cycle = new Thread() {

			public void run() {

				while (sequencer != null && sequencer.isRunning()) {

					try {
						if (useVolumeSliders.isSelected()) {

							double melodyVol = melodyPanel.getVolSlider().getValue() / 100.0;
							ShortMessage melodyVolumeMessage = new ShortMessage();
							melodyVolumeMessage.setMessage(ShortMessage.CONTROL_CHANGE, 0, 7,
									(int) (melodyVol * 127));
							if (synth != null && synth.isOpen()) {
								synth.getReceiver().send(melodyVolumeMessage, -1);

							}
							if (midiMode.isSelected() && device != null) {
								device.getReceiver().send(melodyVolumeMessage, -1);
							}

							double bassVol = bassPanel.getVolSlider().getValue() / 100.0;
							ShortMessage bassVolumeMessage = new ShortMessage();
							bassVolumeMessage.setMessage(ShortMessage.CONTROL_CHANGE, 8, 7,
									(int) (bassVol * 127));
							if (synth != null && synth.isOpen()) {
								synth.getReceiver().send(bassVolumeMessage, -1);

							}
							if (midiMode.isSelected() && device != null) {
								device.getReceiver().send(bassVolumeMessage, -1);
							}

							for (int i = 0; i < chordPanels.size(); i++) {
								double vol = chordPanels.get(i).getVolSlider().getValue() / 100.0;
								ShortMessage volumeMessage = new ShortMessage();
								volumeMessage.setMessage(ShortMessage.CONTROL_CHANGE,
										chordPanels.get(i).getMidiChannel() - 1, 7,
										(int) (vol * 127));
								if (synth != null && synth.isOpen()) {
									synth.getReceiver().send(volumeMessage, -1);

								}
								if (midiMode.isSelected() && device != null) {
									device.getReceiver().send(volumeMessage, -1);
								}
							}

							for (int i = 0; i < arpPanels.size(); i++) {
								double vol = arpPanels.get(i).getVolSlider().getValue() / 100.0;
								ShortMessage volumeMessage = new ShortMessage();
								volumeMessage.setMessage(ShortMessage.CONTROL_CHANGE,
										arpPanels.get(i).getMidiChannel() - 1, 7,
										(int) (vol * 127));
								if (synth != null && synth.isOpen()) {
									synth.getReceiver().send(volumeMessage, -1);

								}
								if (midiMode.isSelected() && device != null) {
									device.getReceiver().send(volumeMessage, -1);
								}
							}


						} else {
							for (int i = 0; i < 16; i++) {
								double vol = 1.0;
								ShortMessage volumeMessage = new ShortMessage();
								volumeMessage.setMessage(ShortMessage.CONTROL_CHANGE, i, 7,
										(int) (vol * 127));
								if (synth != null && synth.isOpen()) {
									synth.getReceiver().send(volumeMessage, -1);

								}
								if (midiMode.isSelected() && device != null) {
									device.getReceiver().send(volumeMessage, -1);
								}
							}
						}
					} catch (InvalidMidiDataException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						return;
					} catch (MidiUnavailableException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						return;
					}
					try {
						sleep(25);
					} catch (InterruptedException e) {
						e.printStackTrace();
						return;
					}
				}
				System.out.println("ENDED VOLUME SLIDER THREAD!");
			}
		};
		cycle.start();
	}

	private void switchAllOnComposeCheckboxes(boolean state) {
		randomChordsGenerateOnCompose.setSelected(state);
		randomArpsGenerateOnCompose.setSelected(state);
		randomDrumsGenerateOnCompose.setSelected(state);
		randomizeBmpTransOnCompose.setSelected(state);
		randomizeChordStrumsOnCompose.setSelected(state);
		randomizeInstOnComposeOrGen.setSelected(state);
		randomArpHitsPerPattern.setSelected(state);
		randomizeArrangementOnCompose.setSelected(state);

	}

	private void switchMidiButtons(boolean state) {
		startMidi.setEnabled(state);
		stopMidi.setEnabled(state);
		compose.setEnabled(state);
		regenerate.setEnabled(state);

	}

	private void switchDarkMode() {
		System.out.println("Switching dark mode!");
		if (isDarkMode) {
			FlatIntelliJLaf.install();
		} else {
			FlatDarculaLaf.install();
		}
		isDarkMode = !isDarkMode;
		SwingUtilities.updateComponentTreeUI(this);
		mainTitle.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		subTitle.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		messageLabel.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		tipLabel.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		currentTime.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		totalTime.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		randomChordsGenerateOnCompose.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		randomArpsGenerateOnCompose.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		randomDrumsGenerateOnCompose.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		switchOnComposeRandom.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		compose.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		regenerate.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		randomArpHitsPerPattern.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		randomizeInstOnComposeOrGen.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		randomizeBmpTransOnCompose.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		randomizeChordStrumsOnCompose.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		randomizeArrangementOnCompose.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		for (JSeparator x : separators) {
			x.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		}

		//switchFullMode(isDarkMode);

		sizeRespectingPack();
		setVisible(true);
		repaint();
	}

	private void switchFullMode() {
		isFullMode = !isFullMode;

		toggleableComponents.forEach(e -> e.setVisible(isFullMode));
		melodyPanel.getToggleableComponents().forEach(e -> e.setVisible(isFullMode));
		bassPanel.getToggleableComponents().forEach(e -> e.setVisible(isFullMode));
		chordPanels
				.forEach(e -> e.getToggleableComponents().forEach(f -> f.setVisible(isFullMode)));
		arpPanels.forEach(e -> e.getToggleableComponents().forEach(f -> f.setVisible(isFullMode)));
		drumPanels.forEach(e -> e.getToggleableComponents().forEach(f -> f.setVisible(isFullMode)));
		/*melodySameRhythmChance.setVisible(aFlag);
		melodyUseOldAlgoChance.setVisible(aFlag);
		melodyFirstNoteFromChord.setVisible(aFlag);
		randomChordNote.setVisible(aFlag);
		
		randomArpStretchPicker.setVisible(aFlag);
		randomArpStretchType.setVisible(aFlag);
		randomChordStretchPicker.setVisible(aFlag);
		randomChordStretchType.setVisible(aFlag);
		
		randomChordStrum.setVisible(aFlag);
		randomChordSplit.setVisible(aFlag);
		randomChordMaxSplitChance.setVisible(aFlag);
		randomChordStretchType.setVisible(aFlag);*/


		/*instrumentTabPane
				.setPreferredSize(isFullMode ? scrollPaneDimension : scrollPaneDimensionToggled);*/
		if (isFullMode)
			pack();
	}

	private void composeMidi(boolean regenerate) {

		if (midiMode.isSelected()) {
			synth = null;
		} else {
			if (device != null) {
				if (synth != null) {
					synth.close();
					synth = null;
				}
				if (sequencer != null) {
					sequencer.stop();
					sequencer.close();
					sequencer = null;
					System.out.println("CLOSED SEQUENCER!");
				}
				device.close();
				device = null;
				System.out.println("CLOSED DEVICE!");
			}
		}

		Integer masterpieceSeed = 0;

		Integer parsedSeed = (NumberUtils.isCreatable(randomSeed.getText()))
				? Integer.valueOf(randomSeed.getText())
				: 0;

		if (regenerate) {
			masterpieceSeed = lastRandomSeed;
			if (parsedSeed != 0) {
				masterpieceSeed = parsedSeed;
			}
			if (randomMelodyOnRegenerate.isSelected()) {
				Random rand = new Random();
				int melodySeed = rand.nextInt();
				melodyPanel.setPatternSeed(melodySeed);
			}
		}

		Random seedGenerator = new Random();
		int randomVal = seedGenerator.nextInt();
		if (masterpieceSeed != 0) {
			System.out.println("Skipping, regenerated seed: " + masterpieceSeed);
		} else if ((!StringUtils.isEmpty(randomSeed.getText()) && !"0".equals(randomSeed.getText())
				&& (StringUtils.isNumeric(randomSeed.getText())
						|| StringUtils.isNumeric(randomSeed.getText().substring(1))))) {
			masterpieceSeed = Integer.valueOf(randomSeed.getText());
		} else {
			masterpieceSeed = randomVal;
		}

		System.out.println("Melody seed: " + masterpieceSeed);
		lastRandomSeed = masterpieceSeed;

		// TODO: refactor into "pre config phase" method?

		if (!regenerate && randomizeArrangementOnCompose.isSelected()) {
			handleArrangementAction("ArrangementRandomize", lastRandomSeed,
					Integer.valueOf(pieceLength.getText()));
		}
		if (regenerate && (currentMidi != null) && arrangementManualOverride.isSelected()) {
			arrangement.setOverridden(true);
		} else {
			arrangement.setOverridden(false);
		}

		if (globalSwingOverride.isSelected()) {
			int swing = globalSwingOverrideValue.getInt();
			melodyPanel.setSwingPercent(swing);
			randomArpMaxSwing.setText("" + swing);
			drumPanels.forEach(e -> {
				if (e.getInstrument() > 40) {
					e.setSwingPercent(swing);
				}
			});
		}

		MelodyGenerator melodyGen = new MelodyGenerator(copyGUItoConfig(false));
		fillUserParameters();

		File makeDir = new File(MIDIS_FOLDER);
		makeDir.mkdir();

		String seedData = "" + masterpieceSeed;
		if (melodyPanel.getPatternSeed() != 0 && !melodyPanel.getMuteInst()) {
			seedData += "_" + melodyPanel.getPatternSeed();
		}

		String fileName = "seed" + seedData;
		String relPath = MIDIS_FOLDER + "/" + fileName + ".mid";
		int melodyInstrument = jm.constants.ProgramChanges.KALIMBA;
		melodyGen.generateMasterpiece(masterpieceSeed, relPath, melodyInstrument);
		currentMidi = null;


		// TODO: from real parts - set after generation!
		scrollableArrangementActualTable
				.setModel(MelodyGenerator.gc.getArrangement().convertToActualTableModel());


		try (FileWriter fw = new FileWriter("randomSeedHistory.txt", true);
				BufferedWriter bw = new BufferedWriter(fw);
				PrintWriter out = new PrintWriter(bw)) {
			out.println(new Date().toString() + ", Seed: " + seedData);
		} catch (IOException e) {
			System.out.println(
					"Yikers! An exception while writing a single line at the end of a .txt file!");
		}

		try {
			if (sequencer != null) {
				sequencer.stop();
			}
			Synthesizer synthesizer = null;
			if (!midiMode.isSelected()) {
				synthesizer = loadSynth();
			}


			if (sequencer != null) {
				// do nothing
			} else {
				sequencer = MidiSystem.getSequencer(synthesizer == null); // Get the default Sequencer
				if (sequencer == null) {
					System.err.println("Sequencer device not supported");
					return;
				}
				sequencer.open(); // Open device
			}


			// Create sequence, the File must contain MIDI file data.
			currentMidi = new File(relPath);
			generatedMidi.setListData(new File[] { currentMidi });
			sizeRespectingPack();
			Sequence sequence = MidiSystem.getSequence(currentMidi);
			sequencer.setSequence(sequence); // load it into sequencer

			if (midiMode.isSelected()) {
				if (device == null) {
					for (Transmitter tm : sequencer.getTransmitters()) {
						tm.close();
					}
					device = null;
					MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
					for (int i = 0; i < infos.length; i++) {
						if (infos[i].toString()
								.equalsIgnoreCase((String) midiModeDevices.getSelectedItem())) {
							device = MidiSystem.getMidiDevice(infos[i]);
							System.out.println(
									infos[i].toString() + "| max recv: " + device.getMaxReceivers()
											+ ", max trm: " + device.getMaxTransmitters());
							if (device.getMaxReceivers() != 0) {
								System.out.println(
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


			sequencer.setTickPosition(0);
			totalTime.setText(microsecondsToTimeString(sequencer.getMicrosecondLength()));
			sequencer.start();  // start the playback
			slider.setMaximum((int) (sequencer.getMicrosecondLength() / 1000));
			slider.setPaintTicks(true);
			slider.setMajorTickSpacing(slider.getMaximum()
					/ arrangement.getSections().stream().mapToInt(e -> e.getMeasures()).sum());
			//TODO: track soloing?
			//sequencer.setTrackSolo(1, true);
			startVolumeSliderThread();
			recalculateTabPaneCounts();

		} catch (MidiUnavailableException | InvalidMidiDataException | IOException ex) {
			ex.printStackTrace();
		}
	}

	private Synthesizer loadSynth() {
		Synthesizer synthesizer = null;
		try {
			File soundbankFile = new File(soundbankFilename.getText());
			if (soundbankFile.isFile()) {
				if (synth == null || !isSoundbankSynth) {
					synth = null;
					soundfont = MidiSystem.getSoundbank(
							new BufferedInputStream(new FileInputStream(soundbankFile)));
					synthesizer = MidiSystem.getSynthesizer();

					synthesizer.isSoundbankSupported(soundfont);
					synthesizer.open();
					synthesizer.loadAllInstruments(soundfont);
				}
				System.out.println("Playing using soundbank: " + soundbankFilename.getText());
			} else {
				if (synth != null && isSoundbankSynth) {
					synth.unloadAllInstruments(soundfont);
					synth.close();
				}
				synthesizer = null;
				synth = null;
				soundfont = null;
				System.out.println("NO SOUNDBANK WITH THAT NAME FOUND!");
			}


		} catch (InvalidMidiDataException | IOException | MidiUnavailableException ex) {
			synthesizer = null;
			synth = null;
			soundfont = null;
			ex.printStackTrace();
			System.out.println("NO SOUNDBANK WITH THAT NAME FOUND!");
		}
		return synthesizer;
	}

	private JButton makeButton(String name, ActionListener listener, String actionCommand) {
		JButton butt = new JButton(name);
		butt.addActionListener(listener);
		butt.setActionCommand(actionCommand);
		return butt;
	}

	private JButton makeButton(String name, String actionCommand) {
		JButton butt = new JButton(name);
		butt.addActionListener(this);
		butt.setActionCommand(actionCommand);
		return butt;
	}

	// Deal with the window closebox
	public void windowClosing(WindowEvent we) {
		if (sequencer != null) {
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

	// Deal with item events (generated by the JComboBox<String>boxs)
	public void itemStateChanged(ItemEvent ie) {
	}

	// Deal with Action events (button pushes)
	public void actionPerformed(ActionEvent ae) {
		System.out.println("Processing.. ::" + ae.getActionCommand() + "::");

		InstComboBox.BANNED_INSTS.clear();
		InstComboBox.BANNED_INSTS.addAll(Arrays.asList(bannedInsts.getText().split(",")));

		{
			int inst = melodyPanel.getInstrument();
			melodyPanel.getInstrumentBox().initInstPool(melodyPanel.getInstPool());
			melodyPanel.getInstrumentBox().setInstrument(inst);
			inst = bassPanel.getInstrument();
			bassPanel.getInstrumentBox().initInstPool(bassPanel.getInstPool());
			bassPanel.getInstrumentBox().setInstrument(inst);
		}


		if (ae.getActionCommand() == "InitAllInsts") {
			if (useAllInsts.isSelected()) {
				MidiUtils.initAllInsts();
			} else {
				MidiUtils.initNormalInsts();
			}
			melodyPanel.getInstrumentBox().initInstPool(POOL.PLUCK);
			bassPanel.getInstrumentBox().initInstPool(POOL.BASS);
		}

		if (ae.getActionCommand() == "RandStrums" || (ae.getActionCommand() == "Compose"
				& randomizeChordStrumsOnCompose.isSelected())) {
			Random strumsGen = new Random();
			for (ChordPanel p : chordPanels) {
				p.setStrum((int) getRandomFromArray(strumsGen, MILISECOND_ARRAY_STRUM));
				if (p.getStretchEnabled() && p.getChordNotesStretch() > 4 && p.getStrum() > 499) {
					p.setStrum(p.getStrum() / 2);
				}
			}

		}

		if (ae.getActionCommand() == "RandomizeInst" || (ae.getActionCommand() == "Compose"
				&& randomizeInstOnComposeOrGen.isSelected())) {
			Random instGen = new Random();


			for (ChordPanel cp : chordPanels) {
				if (!cp.getLockInst()) {

					MidiUtils.POOL pool = (instGen.nextInt(100) < Integer
							.valueOf(randomChordSustainChance.getText())) ? POOL.CHORD : POOL.PLUCK;

					cp.getInstrumentBox().initInstPool(pool);
					cp.setInstPool(pool);

					cp.setInstrument(cp.getInstrumentBox().getRandomInstrument());
				}
			}
			for (ArpPanel ap : arpPanels) {
				if (!ap.getLockInst()) {
					ap.getInstrumentBox().setSelectedIndex(
							instGen.nextInt(ap.getInstrumentBox().getItemCount()));
				}
			}
			if (!melodyPanel.getLockInst()) {

				melodyPanel.getInstrumentBox()
						.setInstrument(melodyPanel.getInstrumentBox().getRandomInstrument());
			}

			if (!bassPanel.getLockInst()) {

				bassPanel.getInstrumentBox()
						.setInstrument(bassPanel.getInstrumentBox().getRandomInstrument());
			}
		}


		if (ae.getActionCommand() == "RandChords" || (ae.getActionCommand() == "Compose"
				&& addChords.isSelected() && randomChordsGenerateOnCompose.isSelected())) {
			List<InstComboBox> chordInsts = chordPanels.stream().map(e -> e.getInstrumentBox())
					.collect(Collectors.toList());
			createRandomChordPanels(Integer.valueOf(randomChordsToGenerate.getText()), false);
			if (!randomizeInstOnComposeOrGen.isSelected()) {
				for (int i = 0; i < chordInsts.size() && i < chordPanels.size(); i++) {
					chordPanels.get(i).getInstrumentBox()
							.initInstPool(chordInsts.get(i).getInstPool());
					chordPanels.get(i).setInstPool(chordInsts.get(i).getInstPool());
					chordPanels.get(i).setInstrument(chordInsts.get(i).getInstrument());
				}
			}

		}
		if (ae.getActionCommand() == "RandArps" || (ae.getActionCommand() == "Compose"
				&& addArps.isSelected() && randomArpsGenerateOnCompose.isSelected())) {
			List<Integer> arpInsts = arpPanels.stream().map(e -> e.getInstrument())
					.collect(Collectors.toList());
			createRandomArpPanels(Integer.valueOf(randomArpsToGenerate.getText()), false);
			if (!randomizeInstOnComposeOrGen.isSelected()) {
				for (int i = 0; i < arpInsts.size() && i < arpPanels.size(); i++) {
					arpPanels.get(i).setInstrument(arpInsts.get(i));
				}
			}
		}

		if (ae.getActionCommand() == "RandDrums" || (ae.getActionCommand() == "Compose"
				&& addDrums.isSelected() && randomDrumsGenerateOnCompose.isSelected())) {
			/*List<Integer> drumInsts = drumPanels.stream().map(e -> e.getPitch())
					.collect(Collectors.toList());*/
			createRandomDrumPanels(Integer.valueOf(randomDrumsToGenerate.getText()), false);
			/*for (int i = 0; i < drumInsts.size() && i < drumPanels.size(); i++) {
				drumPanels.get(i).setPitch(drumInsts.get(i));
			}*/
		}


		realBpm = Double.valueOf(mainBpm.getText());
		if (ae.getActionCommand() == "RandomizeBpmTrans" || (ae.getActionCommand() == "Compose"
				&& randomizeBmpTransOnCompose.isSelected())) {
			Random instGen = new Random();

			int bpm = instGen.nextInt(30) + 50;
			if (arpAffectsBpm.isSelected() && !arpPanels.isEmpty()) {
				double highestArpPattern = arpPanels.stream()
						.map(e -> (e.getPatternRepeat() * e.getHitsPerPattern())
								/ (e.getChordSpan() * 8.0))
						.max((e1, e2) -> Double.compare(e1, e2)).get();
				System.out.println("Repeater value: " + highestArpPattern);
				if (highestArpPattern > 1) {
					bpm *= 1 / (0.5 + highestArpPattern * 0.5);
				}
			}
			mainBpm.setText("" + bpm);
			realBpm = bpm;
			transposeScore.setInt(instGen.nextInt(12) - 6);
		}


		// midi generation
		if (ae.getActionCommand() == "Compose" || ae.getActionCommand() == "Regenerate") {
			boolean isRegenerateOnly = ae.getActionCommand() == "Regenerate";
			switchMidiButtons(false);


			SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
				@Override
				protected Void doInBackground()
						throws InterruptedException, MidiUnavailableException, IOException {
					try {
						composeMidi(isRegenerateOnly);
					} catch (Throwable ex) {
						ex.printStackTrace();
						return null;
					}

					return null;
				}

				@Override
				protected void done() {
					switchMidiButtons(true);
					List<String> prettyChords = MelodyGenerator.chordInts.stream()
							.map(e -> MidiUtils.prettyChord(e)).collect(Collectors.toList());
					currentChords.setText("Chords:[" + StringUtils.join(prettyChords, ",") + "]");
					sizeRespectingPack();
					repaint();
				}
			};

			worker.execute();

		}

		if (ae.getActionCommand() == "StopMidi") {
			if (sequencer != null) {
				System.out.println("Stopping Midi..");
				sequencer.stop();
				slider.setValue(0);
				sequencer.setTickPosition(0);
				System.out.println("Stopped Midi!");
			} else {
				System.out.println("Sequencer is NULL!");
			}
		}

		if (ae.getActionCommand() == "StartMidi") {
			if (sequencer != null) {
				System.out.println("Starting Midi..");
				sequencer.stop();
				sequencer.setTickPosition(0);
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				sequencer.start();
				startVolumeSliderThread();
				System.out.println("Started Midi!");
			} else {
				System.out.println("Sequencer is NULL!");
			}
		}

		/*if (ae.getActionCommand() == "PauseMidi") {
			if (sequencer != null) {
				System.out.println("Pausing Midi..");
				sequencer.stop();
				startMidi.setText("Start");
				startMidi.setActionCommand("StartMidi");
				System.out.println("Paused Midi!");
			} else {
				System.out.println("Sequencer is NULL!");
			}
		}*/

		if (ae.getActionCommand().startsWith("Save ")) {
			if (currentMidi != null) {
				System.out.println("Saving file: " + currentMidi.getName());

				Date date = new Date();
				String[] starSplit = ae.getActionCommand().split(" ");
				if (starSplit.length == 1) {
					System.out.println("WRONG SAVE COMMAND: " + ae.getActionCommand());
					return;
				}
				String rating = starSplit[1].substring(0, 1);
				SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();
				f.applyPattern("yyMMdd-HH-mm-ss");

				String ratingDirectory = "/saved_" + rating + "star/";

				File makeSavedDir = new File(MIDIS_FOLDER + ratingDirectory);
				makeSavedDir.mkdir();

				String soundbankLoadedString = (isSoundbankSynth) ? "SB_" : "";

				String finalFilePath = currentMidi.getParent() + ratingDirectory + f.format(date)
						+ "_" + soundbankLoadedString + currentMidi.getName();

				File savedMidi = new File(finalFilePath);
				try {
					FileUtils.copyFile(currentMidi, savedMidi);
					copyGUItoConfig(true);
					marshal(finalFilePath);
				} catch (IOException | JAXBException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				System.out.println("currentMidi is NULL!");
			}
		}

		if (ae.getActionCommand() == "UncheckComposeRandom") {
			switchAllOnComposeCheckboxes(false);
			switchOnComposeRandom.setText("Check all 'on Compose'");
			switchOnComposeRandom.setActionCommand("CheckComposeRandom");
		}

		if (ae.getActionCommand() == "CheckComposeRandom") {
			switchAllOnComposeCheckboxes(true);
			switchOnComposeRandom.setText("Uncheck all 'on Compose'");
			switchOnComposeRandom.setActionCommand("UncheckComposeRandom");
		}

		if (ae.getActionCommand() == "SaveWavFile") {

			if (currentMidi == null) {
				messageLabel.setText("Need to compose first!");
				messageLabel.repaint(0);
				return;
			}
			switchMidiButtons(false);
			sizeRespectingPack();
			repaint();
			SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {

				@Override
				protected Void doInBackground()
						throws InterruptedException, MidiUnavailableException, IOException {
					SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();
					Synthesizer defSynth;
					f.applyPattern("yyMMdd-HH-mm-ss");
					Date date = new Date();
					defSynth = (isSoundbankSynth && synth != null) ? synth
							: MidiSystem.getSynthesizer();
					String soundbankOptional = (soundfont != null) ? "SB_" : "";
					String filename = f.format(date) + "_" + soundbankOptional
							+ currentMidi.getName();
					saveWavFile(filename + "-export.wav", defSynth);
					defSynth.open();
					if (soundfont != null) {
						defSynth.unloadAllInstruments(soundfont);
						defSynth.loadAllInstruments(soundfont);
					}
					for (Transmitter tm : sequencer.getTransmitters()) {
						tm.close();
					}
					sequencer.getTransmitter().setReceiver(defSynth.getReceiver());
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

		if (ae.getActionCommand() == "GenMelody") {

			Random rand = new Random();
			int melodySeed = rand.nextInt();
			melodyPanel.setPatternSeed(melodySeed);
		}

		if (ae.getActionCommand() == "ClearMelody") {
			melodyPanel.setPatternSeed(0);
		}

		if (ae.getActionCommand() == "CopySeed") {
			String str = String.valueOf(lastRandomSeed);
			/*Toolkit toolkit = Toolkit.getDefaultToolkit();
			Clipboard clipboard = toolkit.getSystemClipboard();
			StringSelection strSel = new StringSelection(str);
			clipboard.setContents(strSel, null);*/
			randomSeed.setText(str);
			System.out.println("Copied to random seed: " + str);
		}

		if (ae.getActionCommand() == "CopyChords") {
			String str = StringUtils.join(MelodyGenerator.chordInts.stream()
					.map(e -> MidiUtils.prettyChord(e)).collect(Collectors.toList()), ",");
			userChords.setText(str);
			System.out.println("Copied chords: " + str);
		}

		if (ae.getActionCommand() == "ClearSeed") {
			randomSeed.setText("0");
			chordPanels.forEach(e -> e.setPatternSeed(0));
			arpPanels.forEach(e -> e.setPatternSeed(0));
			drumPanels.forEach(e -> e.setPatternSeed(0));
			melodyPanel.setPatternSeed(0);
			bassPanel.setPatternSeed(0);
		}


		if (ae.getActionCommand() == "LoadGUIConfig") {
			FileDialog fd = new FileDialog(this, "Choose a file", FileDialog.LOAD);
			fd.setDirectory(null);
			fd.setFile("*.xml");
			fd.setVisible(true);
			String filename = fd.getFile();
			File[] files = fd.getFiles();
			if (filename == null)
				System.out.println("You cancelled the choice");
			else {
				System.out.println("You chose " + filename);
				try {
					guiConfig =

							unmarshall(files[0]);
					copyConfigToGUI();
				} catch (JAXBException |

						IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}

		if (ae.getActionCommand() == "ClearPatterns") {
			for (DrumPanel dp : drumPanels) {
				dp.setPatternSeed(0);
				if (dp.getPattern() != RhythmPattern.RANDOM) {
					dp.setPattern(RhythmPattern.RANDOM);
					dp.setPauseChance(3 * dp.getPauseChance());
				}

			}
		}

		if (ae.getActionCommand() == "AddDrum") {
			//addDrumPanelToLayout();
			createRandomDrumPanels(drumPanels.size() + 1, true);
			randomDrumsToGenerate.setText("" + drumPanels.size());
			sizeRespectingPack();
			repaint();
		}

		if (ae.getActionCommand().startsWith("RemoveDrum,")) {
			String drumNumber = ae.getActionCommand().split(",")[1];

			removeDrumPanel(Integer.valueOf(drumNumber), true);
			randomDrumsToGenerate.setText("" + drumPanels.size());
		}

		if (ae.getActionCommand() == "ClearChordPatterns")

		{
			for (ChordPanel cp : chordPanels) {
				cp.setPatternSeed(0);
				cp.setPattern(RhythmPattern.RANDOM);

			}
		}

		if (ae.getActionCommand() == "AddChord") {
			//addChordPanelToLayout();
			createRandomChordPanels(chordPanels.size() + 1, true);
			randomChordsToGenerate.setText("" + chordPanels.size());

			sizeRespectingPack();
			repaint();
		}

		if (ae.getActionCommand().startsWith("RemoveChord,")) {
			String chordNumber = ae.getActionCommand().split(",")[1];
			removeChordPanel(Integer.valueOf(chordNumber), true);
			randomChordsToGenerate.setText("" + chordPanels.size());
		}

		if (ae.getActionCommand() == "AddArp") {
			//addArpPanelToLayout();
			createRandomArpPanels(arpPanels.size() + 1, true);
			randomArpsToGenerate.setText("" + arpPanels.size());
			sizeRespectingPack();
			repaint();
		}

		if (ae.getActionCommand().startsWith("RemoveArp,")) {
			String arpNumber = ae.getActionCommand().split(",")[1];
			removeArpPanel(Integer.valueOf(arpNumber), true);
			randomArpsToGenerate.setText("" + arpPanels.size());
		}

		if (ae.getActionCommand() == "ClearArpPatterns") {
			for (ArpPanel ap : arpPanels) {
				ap.setPatternSeed(0);
				ap.setPattern(RhythmPattern.RANDOM);

			}
		}

		if (ae.getActionCommand().startsWith("Arrangement")) {
			Random arrGen = new Random();
			handleArrangementAction(ae.getActionCommand(), arrGen.nextInt(),
					Integer.valueOf(pieceLength.getText()));
		}


		if (ae.getActionCommand() == "SwitchDarkMode") {
			switchDarkMode();
		}

		if (ae.getActionCommand() == "ToggleAdv") {
			switchFullMode();
		}

		{
			// recalculate stuff
			//randomChordsCount.setText("" + chordPanels.size());
			//randomArpsToGenerate.setText("" + arpPanels.size());
			//randomDrumsCount.setText("" + drumPanels.size());

			recalculateTabPaneCounts();
		}

		System.out.println("Finished.. ::" + ae.getActionCommand() + "::");
		messageLabel.setText("::" + ae.getActionCommand() + "::");
	}

	private void recalculateTabPaneCounts() {
		instrumentTabPane.setTitleAt(0, "Chords (" + chordPanels.size() + ")");
		instrumentTabPane.setTitleAt(1, "Arps (" + arpPanels.size() + ")");
		instrumentTabPane.setTitleAt(2, "Drums (" + drumPanels.size() + ")");
		instrumentTabPane.setTitleAt(3, "Arrangement (" + arrangement.getSections().size() + ")");
	}

	public void fillUserParameters() {
		try {
			MelodyGenerator.DISPLAY_SCORE = showScore.isSelected();
			MelodyGenerator.showScoreMode = showScorePicker.getSelectedIndex();
			MelodyGenerator.COLLAPSE_DRUM_TRACKS = collapseDrumTracks.isSelected();

			MelodyGenerator.FIRST_CHORD = chordSelect(
					(String) firstChordSelection.getSelectedItem());
			MelodyGenerator.LAST_CHORD = chordSelect((String) lastChordSelection.getSelectedItem());

			if (userChordsEnabled.isSelected()) {
				String text = userChords.getText().replaceAll(" ", "");
				userChords.setText(text);
				String[] userChordsSplit = text.split(",");
				System.out.println(StringUtils.join(userChordsSplit, ";"));
				String[] userChordsDurationsSplit = userChordsDurations.getText().split(",");
				try {
					boolean userChordsRandom = false;
					if (userChords.getText().contains("R")) {
						userChordsRandom = true;
					}
					if (userChordsRandom
							|| (userChordsSplit.length == userChordsDurationsSplit.length)) {
						System.out.println("Trying to solve user chords!");
						List<Long> userChordsParsed = new ArrayList<>();
						List<Double> userChordsDurationsParsed = new ArrayList<>();
						for (int i = 0; i < userChordsDurationsSplit.length; i++) {
							if (!userChordsRandom) {
								if (userChordsSplit[i].matches("[0-9]+")) {
									userChordsParsed.add(Long.valueOf(userChordsSplit[i]));
								} else {
									userChordsParsed
											.add(MidiUtils.unprettyChord(userChordsSplit[i]));
								}
							}
							userChordsDurationsParsed
									.add(Double.valueOf(userChordsDurationsSplit[i]));
						}
						MelodyGenerator.userChords = userChordsParsed;
						MelodyGenerator.userChordsDurations = userChordsDurationsParsed;
						System.out.println(userChordsDurationsParsed.toString());
					} else {
						MelodyGenerator.userChords.clear();
						MelodyGenerator.userChordsDurations.clear();
					}
				} catch (Exception e) {
					System.out.println("Bad user input in custom chords/durations!\n");
					e.printStackTrace();
				}
			} else {
				MelodyGenerator.userChords.clear();
				MelodyGenerator.userChordsDurations.clear();
			}

			// to include it in the XML when saving, but not when generating
			if (!addChords.isSelected()) {
				MelodyGenerator.gc.setChordParts(new ArrayList<>());
			}
			if (!addArps.isSelected()) {
				MelodyGenerator.gc.setArpParts(new ArrayList<>());
			}
			if (!addDrums.isSelected()) {
				MelodyGenerator.gc.setDrumParts(new ArrayList<>());
			}

		} catch (Exception e) {
			System.out.println("User screwed up his inputs!");
			e.printStackTrace();
		}

	}

	private ChordGenSettings getChordSettingsFromUI() {
		ChordGenSettings chordSettings = new ChordGenSettings();

		chordSettings.setIncludePresets(randomChordPattern.isSelected());
		chordSettings.setUseDelay(randomChordDelay.isSelected());
		chordSettings.setUseStrum(randomChordStrum.isSelected());
		chordSettings.setUseSplit(randomChordSplit.isSelected());
		chordSettings.setUseTranspose(randomChordTranspose.isSelected());
		chordSettings.setShiftChance(Integer.valueOf(randomChordShiftChance.getText()));
		chordSettings.setSustainChance(Integer.valueOf(randomChordSustainChance.getText()));
		return chordSettings;
	}

	private void setChordSettingsInUI(ChordGenSettings settings) {
		randomChordPattern.setSelected(settings.isIncludePresets());
		randomChordDelay.setSelected(settings.isUseDelay());
		randomChordStrum.setSelected(settings.isUseStrum());
		randomChordSplit.setSelected(settings.isUseSplit());
		randomChordTranspose.setSelected(settings.isUseTranspose());
		randomChordShiftChance.setText("" + settings.getShiftChance());
		randomChordSustainChance.setText("" + settings.getSustainChance());
	}

	//returns a string[] given a upper or lower case roman numeral
	//this method is used in the selection of a first and last bar 
	public int chordSelect(String s) {
		int chord = 0;
		if (s == "R") {
			chord = 0;
		} else if (s == "I") {
			chord = 1;
		} else if (s == "V") {
			chord = 5;
		} else if (s == "vi") {
			chord = 60;
		} else { //if (fChord = "i")
			chord = 0;
		}
		return chord;
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
			if (soundfont != null) {
				synth.unloadAllInstruments(soundfont);
				synth.loadAllInstruments(soundfont);
			}
			// Play Sequence into AudioSynthesizer Receiver.
			double totalLength = this.sendOutputSequenceMidiEvents(synth.getReceiver());

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

	public void marshal(String path) throws JAXBException, IOException {
		SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();
		f.applyPattern("yyMMdd-hh-mm-ss");
		JAXBContext context = JAXBContext.newInstance(GUIConfig.class);
		Marshaller mar = context.createMarshaller();
		mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		mar.marshal(guiConfig, new File(path.substring(0, path.length() - 4) + "-guiConfig.xml"));
	}

	public GUIConfig unmarshall(File f) throws JAXBException, IOException {
		JAXBContext context = JAXBContext.newInstance(GUIConfig.class);
		return (GUIConfig) context.createUnmarshaller().unmarshal(new FileReader(f));
	}

	public GUIConfig copyGUItoConfig(boolean storage) {
		// seed
		guiConfig.setRandomSeed(lastRandomSeed);

		// arrangement
		if (!useArrangement.isSelected()) {
			arrangement.setPreviewChorus(true);
		} else {
			arrangement.setPreviewChorus(false);
			boolean overrideSuccessful = !storage
					&& arrangement.setFromActualTable(scrollableArrangementActualTable);
			System.out.println("OVERRIDE OK?: " + overrideSuccessful);
			if (!overrideSuccessful) {
				arrangement.setFromModel(scrollableArrangementTable);
			}

		}
		guiConfig.setArrangement(arrangement);
		guiConfig.setArrangementVariationChance(
				Integer.valueOf(arrangementVariationChance.getInt()));

		// macro
		guiConfig.setScaleMode(ScaleMode.valueOf((String) scaleMode.getSelectedItem()));
		guiConfig.setSoundbankName(soundbankFilename.getText());
		guiConfig.setPieceLength(Integer.valueOf(pieceLength.getText()));
		if (fixedLengthChords.getSelectedIndex() < 2) {
			guiConfig.setFixedDuration(
					Integer.valueOf((String) fixedLengthChords.getSelectedItem()));
		} else {
			guiConfig.setFixedDuration(0);
		}

		guiConfig.setTranspose(transposeScore.getInt());
		guiConfig.setBpm(Double.valueOf(mainBpm.getText()));
		guiConfig.setArpAffectsBpm(arpAffectsBpm.isSelected());

		// parts
		guiConfig.setMelodyPart(melodyPanel.toMelodyPart(lastRandomSeed));
		guiConfig.setBassPart(bassPanel.toBassPart(lastRandomSeed));

		guiConfig.setChordsEnable(addChords.isSelected());
		guiConfig.setArpsEnable(addArps.isSelected());
		guiConfig.setDrumsEnable(addDrums.isSelected());
		guiConfig.setDrumParts(getDrumPartsFromDrumPanels(false));
		guiConfig.setChordParts(getChordPartsFromChordPanels(false));
		guiConfig.setArpParts(getArpPartsFromArpPanels(false));

		guiConfig.setChordGenSettings(getChordSettingsFromUI());

		// melody
		guiConfig.setMaxNoteJump(Integer.valueOf(maxJump.getInt()));
		guiConfig.setMaxExceptions(Integer.valueOf(maxExceptions.getInt()));
		guiConfig.setMelodyAlternateRhythmChance(
				Integer.valueOf(melodyAlternateRhythmChance.getInt()));
		guiConfig.setMelodySameRhythmChance(Integer.valueOf(melodySameRhythmChance.getInt()));
		guiConfig.setMelodyUseOldAlgoChance(Integer.valueOf(melodyUseOldAlgoChance.getInt()));
		guiConfig.setMelodySplitChance(Integer.valueOf(melodySplitChance.getInt()));
		guiConfig.setMelodyExceptionChance(Integer.valueOf(melodyExceptionChance.getInt()));
		guiConfig.setFirstNoteFromChord(melodyFirstNoteFromChord.isSelected());
		guiConfig.setFirstNoteRandomized(randomChordNote.isSelected());
		guiConfig.setMelodyQuickness(melodyQuickness.getInt());


		// chords
		guiConfig.setFirstChord((String) firstChordSelection.getSelectedItem());
		guiConfig.setLastChord((String) lastChordSelection.getSelectedItem());
		guiConfig.setCustomChordsEnabled(userChordsEnabled.isSelected());
		guiConfig.setCustomChords(StringUtils.join(MelodyGenerator.chordInts.stream()
				.map(e -> MidiUtils.prettyChord(e)).collect(Collectors.toList()), ","));
		guiConfig.setCustomChordDurations(userChordsDurations.getText());
		guiConfig.setSpiceChance(Integer.valueOf(spiceChance.getInt()));
		guiConfig.setDimAugEnabled(spiceAllowDimAug.isSelected());
		guiConfig.setEnable9th13th(spiceAllow9th13th.isSelected());
		guiConfig.setChordSlashChance(Integer.valueOf(chordSlashChance.getInt()));

		// arps
		guiConfig.setUseOctaveAdjustments(randomArpUseOctaveAdjustments.isSelected());
		guiConfig.setMaxArpSwing(Integer.valueOf(randomArpMaxSwing.getText()));

		return guiConfig;
	}

	public void copyConfigToGUI() {
		// seed
		randomSeed.setText(String.valueOf(guiConfig.getRandomSeed()));
		lastRandomSeed = (int) guiConfig.getRandomSeed();

		// arrangement
		arrangement = guiConfig.getArrangement();
		arrangementVariationChance.setInt(guiConfig.getArrangementVariationChance());

		// macro
		scaleMode.setSelectedItem(guiConfig.getScaleMode().toString());
		soundbankFilename.setText(guiConfig.getSoundbankName());
		pieceLength.setText(String.valueOf(guiConfig.getPieceLength()));
		if (guiConfig.getFixedDuration() == 0) {
			fixedLengthChords.setSelectedItem("NOT");
		} else {
			fixedLengthChords.setSelectedItem("" + guiConfig.getFixedDuration());
		}
		transposeScore.setInt(guiConfig.getTranspose());
		mainBpm.setText(String.valueOf(guiConfig.getBpm()));
		arpAffectsBpm.setSelected(guiConfig.isArpAffectsBpm());

		// parts
		melodyPanel.setFromMelodyPart(guiConfig.getMelodyPart());
		bassPanel.setFromBassPart(guiConfig.getBassPart());

		addChords.setSelected(guiConfig.isChordsEnable());
		addArps.setSelected(guiConfig.isArpsEnable());
		addDrums.setSelected(guiConfig.isDrumsEnable());
		recreateChordPanelsFromChordParts(guiConfig.getChordParts());
		recreateArpPanelsFromArpParts(guiConfig.getArpParts());
		recreateDrumPanelsFromDrumParts(guiConfig.getDrumParts());
		randomChordsToGenerate.setText(chordPanels.size() + "");
		randomArpsToGenerate.setText(arpPanels.size() + "");
		randomDrumsToGenerate.setText(drumPanels.size() + "");

		setChordSettingsInUI(guiConfig.getChordGenSettings());

		// melody
		melodyFirstNoteFromChord.setSelected(guiConfig.isFirstNoteFromChord());
		randomChordNote.setSelected(guiConfig.isFirstNoteRandomized());
		maxJump.setInt(guiConfig.getMaxNoteJump());
		maxExceptions.setInt(guiConfig.getMaxExceptions());
		melodyAlternateRhythmChance.setInt(guiConfig.getMelodyAlternateRhythmChance());
		melodySameRhythmChance.setInt(guiConfig.getMelodySameRhythmChance());
		melodyUseOldAlgoChance.setInt(guiConfig.getMelodyUseOldAlgoChance());
		melodySplitChance.setInt(guiConfig.getMelodySplitChance());
		melodyExceptionChance.setInt(guiConfig.getMelodyExceptionChance());
		melodyQuickness.setInt(guiConfig.getMelodyQuickness());

		// chords
		spiceChance.setInt(guiConfig.getSpiceChance());
		spiceAllowDimAug.setSelected(guiConfig.isDimAugEnabled());
		spiceAllow9th13th.setSelected(guiConfig.isEnable9th13th());
		chordSlashChance.setInt(guiConfig.getChordSlashChance());
		firstChordSelection.setSelectedItem(guiConfig.getFirstChord());
		lastChordSelection.setSelectedItem(guiConfig.getLastChord());
		userChordsEnabled.setSelected(guiConfig.isCustomChordsEnabled());
		userChords.setText(guiConfig.getCustomChords());
		userChordsDurations.setText(guiConfig.getCustomChordDurations());

		// arps
		randomArpUseOctaveAdjustments.setSelected(guiConfig.isUseOctaveAdjustments());
		randomArpMaxSwing.setText(String.valueOf(guiConfig.getMaxArpSwing()));

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
		JSeparator x = new JSeparator(SwingConstants.HORIZONTAL);
		x.setPreferredSize(new Dimension(1420, 2));
		JPanel sepPanel = new JPanel();
		sepPanel.add(x);
		constraints.gridy = y;
		constraints.anchor = GridBagConstraints.CENTER;
		everythingPanel.add(sepPanel, constraints);
		constraints.anchor = anchorTemp;
		separators.add(x);
	}

	public DrumPanel addDrumPanelToLayout() {
		int panelOrder = (drumPanels.size() > 0) ? getHighestPanelNumber(drumPanels) : 1;

		DrumPanel dp = new DrumPanel(this);
		dp.getToggleableComponents().forEach(e -> e.setVisible(isFullMode));
		dp.setPanelOrder(panelOrder);
		drumPanels.add(dp);
		((JPanel) drumScrollPane.getViewport().getView()).add(dp);
		return dp;
	}

	private void removeDrumPanel(int order, boolean singleRemove) {
		InstPanel panel = getPanelByOrder(order, drumPanels);
		((JPanel) drumScrollPane.getViewport().getView()).remove(panel);
		drumPanels.remove(panel);

		if (singleRemove) {
			//reorderDrumPanels();
			sizeRespectingPack();
			repaint();
		}
	}

	private List<DrumPart> getDrumPartsFromDrumPanels(boolean removeMuted) {
		List<DrumPart> parts = new ArrayList<>();
		for (DrumPanel p : drumPanels) {
			if (!removeMuted || !p.getMuteInst()) {
				parts.add(p.toDrumPart(lastRandomSeed));
			}
		}
		return parts;
	}

	private void recreateDrumPanelsFromDrumParts(List<DrumPart> parts) {
		for (DrumPanel panel : drumPanels) {
			((JPanel) drumScrollPane.getViewport().getView()).remove(panel);
		}
		drumPanels.clear();
		for (DrumPart part : parts) {
			DrumPanel panel = addDrumPanelToLayout();
			panel.setFromDrumPart(part);
		}

		sizeRespectingPack();
		repaint();
	}

	private void createRandomDrumPanels(int panelCount, boolean onlyAdd) {
		Random drumPanelGenerator = new Random();
		for (Iterator<DrumPanel> panelI = drumPanels.iterator(); panelI.hasNext();) {
			DrumPanel panel = panelI.next();
			if (!onlyAdd) {
				((JPanel) drumScrollPane.getViewport().getView()).remove(panel);
				panelI.remove();
			}

		}

		panelCount -= drumPanels.size();

		int slide = 0;

		if (randomDrumSlide.isSelected()) {
			slide = drumPanelGenerator.nextInt(100) - 50;
		}

		int swingPercent = 50
				+ drumPanelGenerator
						.nextInt(Integer.valueOf(randomDrumMaxSwingAdjust.getText()) * 2 + 1)
				- Integer.valueOf(randomDrumMaxSwingAdjust.getText());


		List<Integer> pitches = new ArrayList<>();
		for (int i = 0; i < panelCount; i++) {
			pitches.add(MidiUtils.getInstByIndex(drumPanelGenerator.nextInt(127), POOL.DRUM));
		}
		Collections.sort(pitches);
		if (!onlyAdd && pitches.size() > 2) {
			pitches.set(0, 35);
			pitches.set(1, 36);
		}

		for (int i = 0; i < panelCount; i++) {
			DrumPanel dp = addDrumPanelToLayout();
			dp.setInstrument(pitches.get(i));
			//dp.setPitch(32 + drumPanelGenerator.nextInt(33));


			dp.setChordSpan(drumPanelGenerator.nextInt(2) + 1);
			int patternOrder = 0;
			// use pattern in half the cases if checkbox selected

			if (randomDrumPattern.isSelected()) {
				int[] patternWeights = { 15, 65, 85, 100, 100 };
				int randomWeight = drumPanelGenerator.nextInt(100);
				for (int j = 0; j < patternWeights.length; j++) {
					if (randomWeight < patternWeights[j]) {
						patternOrder = j;
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

			dp.setHitsPerPattern(hits);

			int adjustVelocity = -1 * dp.getHitsPerPattern() / dp.getChordSpan();

			if (dp.getInstrument() == 35 || dp.getInstrument() == 36 || dp.getInstrument() == 38
					|| dp.getInstrument() == 40) {
				adjustVelocity += 15;
			}

			dp.setPattern(RhythmPattern.values()[patternOrder]);
			int velocityMin = drumPanelGenerator.nextInt(30) + 50 + adjustVelocity;
			dp.setVelocityMin(velocityMin);
			dp.setVelocityMax(1 + velocityMin + drumPanelGenerator.nextInt(25));

			if (patternOrder > 0) {
				dp.setPauseChance(drumPanelGenerator.nextInt(5) + 0);
			} else {
				dp.setPauseChance(drumPanelGenerator.nextInt(40) + 40);
			}


			if (dp.getInstrument() > 40) {
				dp.setDelay(slide);
			}
			if (dp.getInstrument() > 40) {
				dp.setSwingPercent(swingPercent);
				dp.setExceptionChance(drumPanelGenerator.nextInt(10));
			} else {
				dp.setExceptionChance(drumPanelGenerator.nextInt(3));
			}


			dp.setIsVelocityPattern(drumPanelGenerator.nextInt(100) < Integer
					.valueOf(randomDrumVelocityPatternChance.getText()));

			if (drumPanelGenerator.nextInt(100) < Integer.valueOf(randomDrumShiftChance.getText())
					&& patternOrder > 0) {
				dp.setPatternShift(
						drumPanelGenerator.nextInt(dp.getPattern().pattern.length - 1) + 1);
			}

		}

		sizeRespectingPack();
		repaint();
	}

	public ChordPanel addChordPanelToLayout() {
		int panelOrder = (chordPanels.size() > 0) ? getHighestPanelNumber(chordPanels) : 1;

		ChordPanel cp = new ChordPanel(this);
		cp.getToggleableComponents().forEach(e -> e.setVisible(isFullMode));
		cp.setPanelOrder(panelOrder);
		chordPanels.add(cp);
		((JPanel) chordScrollPane.getViewport().getView()).add(cp);
		return cp;
	}

	private void removeChordPanel(int order, boolean singleRemove) {
		InstPanel panel = getPanelByOrder(order, chordPanels);
		((JPanel) chordScrollPane.getViewport().getView()).remove(panel);
		chordPanels.remove(panel);

		if (singleRemove) {
			//reorderChordPanels();
			sizeRespectingPack();
			repaint();
		}
	}

	private List<ChordPart> getChordPartsFromChordPanels(boolean removeMuted) {
		List<ChordPart> parts = new ArrayList<>();
		for (ChordPanel p : chordPanels) {
			if (!removeMuted || !p.getMuteInst()) {
				parts.add(p.toChordPart(lastRandomSeed));
			}
		}
		return parts;
	}

	private void recreateChordPanelsFromChordParts(List<ChordPart> parts) {
		for (ChordPanel panel : chordPanels) {
			((JPanel) chordScrollPane.getViewport().getView()).remove(panel);
		}
		chordPanels.clear();
		for (ChordPart part : parts) {
			ChordPanel panel = addChordPanelToLayout();
			panel.setFromChordPart(part);
		}

		sizeRespectingPack();
		repaint();
	}

	private void createRandomChordPanels(int panelCount, boolean onlyAdd) {
		Random chordPanelGenerator = new Random();
		for (Iterator<ChordPanel> panelI = chordPanels.iterator(); panelI.hasNext();) {
			ChordPanel panel = panelI.next();
			if (!panel.getLockInst() && !onlyAdd) {
				((JPanel) chordScrollPane.getViewport().getView()).remove(panel);
				panelI.remove();
			}
		}

		// create only remaining
		panelCount -= chordPanels.size();

		int fixedChordStretch = -1;
		if (randomChordStretchType.getSelectedItem().equals("FIXED")) {
			fixedChordStretch = Integer
					.valueOf((String) randomChordStretchPicker.getSelectedItem());
		}

		for (int i = 0; i < panelCount; i++) {
			ChordPanel cp = addChordPanelToLayout();
			MidiUtils.POOL pool = (chordPanelGenerator.nextInt(100) < Integer
					.valueOf(randomChordSustainChance.getText())) ? POOL.CHORD : POOL.PLUCK;

			cp.getInstrumentBox().initInstPool(pool);
			cp.setInstPool(pool);

			cp.setInstrument(cp.getInstrumentBox().getRandomInstrument());
			cp.setTransitionChance(chordPanelGenerator
					.nextInt(Integer.valueOf(randomChordMaxSplitChance.getText())));
			cp.setTransitionSplit(
					(int) (getRandomFromArray(chordPanelGenerator, MILISECOND_ARRAY_SPLIT)));
			cp.setTranspose((chordPanelGenerator.nextInt(3) - 1) * 12);

			cp.setStrum(((int) (getRandomFromArray(chordPanelGenerator, MILISECOND_ARRAY_STRUM))));
			cp.setDelay(((int) (getRandomFromArray(chordPanelGenerator, MILISECOND_ARRAY_DELAY))));

			if (randomChordUseChordFill.isSelected()) {
				cp.setChordSpanFill(ChordSpanFill.getWeighted(chordPanelGenerator.nextInt(100)));
			}
			int patternOrder = 0;
			// use pattern in half the cases if checkbox selected
			if (chordPanelGenerator.nextBoolean() == true) {
				if (randomChordPattern.isSelected()) {
					patternOrder = chordPanelGenerator.nextInt(RhythmPattern.values().length);
				}
			}
			if (!randomChordStretchType.getSelectedItem().equals("NONE")) {
				cp.setStretchEnabled(true);
				if (fixedChordStretch < 0) {
					int atMost = Integer
							.valueOf((String) randomChordStretchPicker.getSelectedItem());
					cp.setChordNotesStretch(chordPanelGenerator.nextInt(atMost - 3 + 1) + 3);
				} else {
					cp.setChordNotesStretch(fixedChordStretch);
				}
				if (cp.getChordNotesStretch() > 4 && cp.getStrum() > 499) {
					cp.setStrum(cp.getStrum() / 2);
				}
			} else {
				cp.setStretchEnabled(false);
			}

			cp.setPattern(RhythmPattern.values()[patternOrder]);

			int velocityMin = chordPanelGenerator.nextInt(15) + 63;
			cp.setVelocityMin(velocityMin);
			cp.setVelocityMax(1 + velocityMin + chordPanelGenerator.nextInt(20));

			if (chordPanelGenerator.nextInt(100) < Integer.valueOf(randomChordShiftChance.getText())
					&& patternOrder > 0) {
				cp.setPatternShift(
						chordPanelGenerator.nextInt(cp.getPattern().pattern.length - 1) + 1);
			}

			cp.setMidiChannel(11 + (cp.getPanelOrder() - 1) % 5);

		}

		sizeRespectingPack();
		repaint();
	}

	public ArpPanel addArpPanelToLayout() {
		int panelOrder = (arpPanels.size() > 0) ? getHighestPanelNumber(arpPanels) : 1;

		ArpPanel ap = new ArpPanel(this);
		ap.getToggleableComponents().forEach(e -> e.setVisible(isFullMode));
		ap.setPanelOrder(panelOrder);
		arpPanels.add(ap);
		((JPanel) arpScrollPane.getViewport().getView()).add(ap);
		return ap;
	}

	private void removeArpPanel(int order, boolean singleRemove) {
		InstPanel panel = getPanelByOrder(order, arpPanels);
		((JPanel) arpScrollPane.getViewport().getView()).remove(panel);
		arpPanels.remove(panel);

		if (singleRemove) {
			//reorderArpPanels();
			sizeRespectingPack();
			repaint();
		}
	}

	private List<ArpPart> getArpPartsFromArpPanels(boolean removeMuted) {
		List<ArpPart> parts = new ArrayList<>();
		for (ArpPanel p : arpPanels) {
			if (!removeMuted || !p.getMuteInst()) {
				parts.add(p.toArpPart(lastRandomSeed));
			}
		}
		return parts;
	}

	private void recreateArpPanelsFromArpParts(List<ArpPart> parts) {
		for (ArpPanel panel : arpPanels) {
			((JPanel) arpScrollPane.getViewport().getView()).remove(panel);
		}
		arpPanels.clear();
		for (ArpPart part : parts) {
			ArpPanel panel = addArpPanelToLayout();
			panel.setFromArpPart(part);
		}

		sizeRespectingPack();
		repaint();
	}

	private void createRandomArpPanels(int panelCount, boolean onlyAdd) {
		Random arpPanelGenerator = new Random();
		for (Iterator<ArpPanel> panelI = arpPanels.iterator(); panelI.hasNext();) {
			ArpPanel panel = panelI.next();
			if (!panel.getLockInst() && !onlyAdd) {
				((JPanel) arpScrollPane.getViewport().getView()).remove(panel);
				panelI.remove();
			}
		}
		int fixedHitsGenerated = -1;
		if (randomArpHitsPerPattern.isSelected() && randomArpAllSameHits.isSelected()) {
			Random instGen = new Random();
			fixedHitsGenerated = instGen.nextInt(MelodyGenerator.MAXIMUM_PATTERN_LENGTH - 1) + 2;

			if (fixedHitsGenerated == 5) {
				// reduced chance of 5
				fixedHitsGenerated = instGen.nextInt(MelodyGenerator.MAXIMUM_PATTERN_LENGTH - 1)
						+ 2;
			}
			if (fixedHitsGenerated == 7) {
				// eliminate 7
				fixedHitsGenerated++;
			}
			randomArpHitsPicker.setSelectedItem("" + fixedHitsGenerated);
		}

		int fixedInstrument = -1;
		int fixedHits = -1;

		if (arpCopyMelodyInst.isSelected() && !melodyPanel.getMuteInst()) {
			fixedInstrument = melodyPanel.getInstrument();
			if (arpPanels.size() > 0) {
				arpPanels.get(0).setInstrument(fixedInstrument);
			}
		}

		int fixedArpStretch = -1;
		if (randomArpStretchType.getSelectedItem().equals("FIXED")) {
			fixedArpStretch = Integer.valueOf((String) randomArpStretchPicker.getSelectedItem());
		}

		// create only remaining
		panelCount -= arpPanels.size();

		ArpPanel first = (arpPanels.isEmpty()) ? null : arpPanels.get(0);

		for (int i = 0; i < panelCount; i++) {
			if (randomArpAllSameInst.isSelected() && first != null && fixedInstrument < 0) {
				fixedInstrument = arpPanels.get(0).getInstrument();
			}
			if (randomArpAllSameHits.isSelected() && first != null && fixedHits < 0) {
				fixedHits = first.getHitsPerPattern() / first.getChordSpan();
			}

			ArpPanel ap = addArpPanelToLayout();


			if (randomArpHitsPerPattern.isSelected()) {
				if (fixedHits > 0) {
					ap.setHitsPerPattern(fixedHits);
				} else {
					if (fixedHitsGenerated > 0) {
						ap.setHitsPerPattern(fixedHitsGenerated);
					} else {
						Random instGen = new Random();
						int value = instGen.nextInt(MelodyGenerator.MAXIMUM_PATTERN_LENGTH - 1) + 2;

						if (value == 5) {
							// reduced chance of 5
							value = instGen.nextInt(MelodyGenerator.MAXIMUM_PATTERN_LENGTH - 1) + 2;
						}
						if (value == 7) {
							// eliminate 7
							value++;
						}
						ap.setHitsPerPattern(value);
					}
				}
			} else {
				ap.setHitsPerPattern(randomArpHitsPicker.getSelectedIndex() + 1);
			}
			int instrument = ap.getInstrumentBox().getRandomInstrument();

			if (randomArpAllSameInst.isSelected()) {
				if (fixedInstrument >= 0) {
					instrument = fixedInstrument;
				} else {
					fixedInstrument = instrument;
				}
			}
			ap.setChordSpan(arpPanelGenerator.nextInt(2) + 1);
			ap.setInstrument(instrument);
			if (first == null && i == 0) {
				ap.setTranspose(12);
				if (arpCopyMelodyInst.isSelected() && !melodyPanel.getMuteInst()) {
					ap.setInstrument(fixedInstrument);
				}
			} else {
				ap.setTranspose((arpPanelGenerator.nextInt(3) - 1) * 12);
			}
			ap.setPauseChance(arpPanelGenerator.nextInt(50));
			if (ap.getChordSpan() == 1) {
				ap.setPatternRepeat(arpPanelGenerator.nextInt(4) + 1);
			} else {
				ap.setPatternRepeat(1);
				if (arpPanelGenerator.nextBoolean() == true) {
					if ((first == null && i > 1) || (first != null)) {
						ap.setHitsPerPattern(ap.getHitsPerPattern() * ap.getChordSpan());
					}

				}
			}

			if (!randomArpStretchType.getSelectedItem().equals("NONE")) {
				ap.setStretchEnabled(true);
				if (fixedArpStretch < 0) {
					int atMost = Integer.valueOf((String) randomArpStretchPicker.getSelectedItem());
					ap.setChordNotesStretch(arpPanelGenerator.nextInt(atMost - 3 + 1) + 3);
				} else {
					ap.setChordNotesStretch(fixedArpStretch);
				}
			} else {
				ap.setStretchEnabled(false);
			}

			int patternOrder = 0;
			// use pattern in half the cases if checkbox selected
			if (arpPanelGenerator.nextBoolean() == true) {
				if (randomArpPattern.isSelected()) {
					patternOrder = arpPanelGenerator.nextInt(RhythmPattern.values().length);
				}
			}
			ap.setPattern(RhythmPattern.values()[patternOrder]);
			if (randomArpUseChordFill.isSelected()) {
				ap.setChordSpanFill(ChordSpanFill.getWeighted(arpPanelGenerator.nextInt(100)));
			}

			int velocityMin = arpPanelGenerator.nextInt(15) + 63;
			ap.setVelocityMin(velocityMin);
			ap.setVelocityMax(1 + velocityMin + arpPanelGenerator.nextInt(20));


			if (arpPanelGenerator.nextInt(100) < Integer.valueOf(arpShiftChance.getText())
					&& patternOrder > 0) {
				ap.setPatternShift(
						arpPanelGenerator.nextInt(ap.getPattern().pattern.length - 1) + 1);
			}

			ap.setMidiChannel(2 + (ap.getPanelOrder() - 1) % 7);
		}

		if (!arpPanels.isEmpty()) {
			ArpPanel lowest = arpPanels.get(0);
			if (!lowest.getLockInst()) {
				lowest.setPatternRepeat(1);
				lowest.setChordSpan(1);

			}
		}

		sizeRespectingPack();
		repaint();
	}

	private static int getValidPanelNumber(List<? extends InstPanel> panels) {
		panels.sort((e1, e2) -> Integer.compare(e1.getPanelOrder(), e2.getPanelOrder()));
		if (panels.stream().anyMatch(e -> e.getLockInst())) {
			return getLowestAvailablePanelNumber(panels);
		} else {
			return getHighestPanelNumber(panels);
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

	private static InstPanel getPanelByOrder(int order, List<? extends InstPanel> panels) {
		return panels.stream().filter(e -> e.getPanelOrder() == order).findFirst().get();
	}

	private static double getRandomFromArray(Random generator, double[] array) {
		return array[generator.nextInt(array.length)];
	}
}
