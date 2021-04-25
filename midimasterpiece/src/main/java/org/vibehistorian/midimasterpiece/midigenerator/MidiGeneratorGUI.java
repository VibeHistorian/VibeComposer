/* --------------------
* @author VibeHistorian
* ---------------------
*/
package org.vibehistorian.midimasterpiece.midigenerator;

import java.awt.Color;
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
import java.util.Iterator;
import java.util.List;
import java.util.Random;

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
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.border.BevelBorder;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils.PARTS;
import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils.POOL;
import org.vibehistorian.midimasterpiece.midigenerator.Enums.ChordSpanFill;
import org.vibehistorian.midimasterpiece.midigenerator.Enums.RhythmPattern;
import org.vibehistorian.midimasterpiece.midigenerator.Helpers.FileTransferable;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.ArpPanel;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.ChordGenSettings;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.ChordPanel;
import org.vibehistorian.midimasterpiece.midigenerator.Panels.DrumPanel;
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
			62 };
	private static final double[] MILISECOND_ARRAY_DELAY = { 0, 63, 125, 250, 333 };
	private static final double[] MILISECOND_ARRAY_SPLIT = { 625, 750, 875 };
	private static final double[] MILISECOND_MULTIPLIER_ARRAY = { 1, 1.5, 2, 3, 4 };

	private static boolean isDarkMode = false;

	private static List<JSeparator> separators = new ArrayList<>();

	//private static int chordGenPanelStart = 50;
	//private static int drumGenPanelStart = 200;
	//private static int arpGenPanelStart = 100;

	private static Soundbank soundfont = null;


	private Synthesizer synth = null;

	private GUIConfig guiConfig = new GUIConfig();

	// instrument individual panels
	private List<DrumPanel> drumPanels = new ArrayList<>();
	private List<ChordPanel> chordPanels = new ArrayList<>();
	private List<ArpPanel> arpPanels = new ArrayList<>();

	// instrument scrollers
	JTabbedPane instrumentTabPane = new JTabbedPane(JTabbedPane.TOP);

	Dimension scrollPaneDimension = new Dimension(1400, 200);

	JScrollPane drumScrollPane;
	JScrollPane chordScrollPane;
	JScrollPane arpScrollPane;

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

	// macro params
	JTextField soundbankFilename;
	JTextField pieceLength;
	JCheckBox minorScale;
	JCheckBox fixedLengthChords;
	JCheckBox useArrangement;


	// chord variety settings
	JTextField spiceChance;
	JTextField chordSlashChance;
	JCheckBox spiceAllowDimAug;
	JCheckBox spiceAllow9th13th;


	// add/skip instruments
	JCheckBox addMelody;
	JCheckBox addChords;
	JCheckBox addArps;
	JCheckBox addBassRoots;
	JCheckBox addDrums;


	// default instrument pickers (TODO)
	InstComboBox melodyInst;
	InstComboBox bassRootsInst;

	// melody gen settings
	JCheckBox melodyLock;
	JCheckBox arpCopyMelodyInst;
	JTextField maxJump;
	JTextField maxExceptions;
	JTextField melodyPauseChance;
	JTextField userMelodySeed;
	JCheckBox randomMelodyOnRegenerate;
	JCheckBox melodyFirstNoteFromChord;
	JCheckBox randomChordNote;


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

	// bass roots settings
	JCheckBox bassRootsLock;

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
	JCheckBox randomizeInstOnCompose;
	JCheckBox randomizeBmpTransOnCompose;
	JCheckBox randomizeChordStrumsOnCompose;
	JCheckBox arpAffectsBpm;
	JTextField mainBpm;
	JTextField transposeScore;
	JButton switchOnComposeRandom;

	// seed / midi
	JTextField randomSeed;
	int lastRandomSeed = 0;
	double realBpm = 60;

	JList<File> generatedMidi;
	Sequencer sequencer = null;
	File currentMidi = null;
	MidiDevice device = null;

	JCheckBox midiMode;
	JComboBox<String> midiModeDevices;

	JButton compose;
	JButton regenerate;
	JButton startMidi;
	JButton stopMidi;

	JLabel tipLabel;
	JLabel currentChords = new JLabel("Chords:[]");
	JLabel messageLabel;

	private static GridBagConstraints constraints = new GridBagConstraints();

	public static void main(String args[]) {
		FlatDarculaLaf.install();
		isDarkMode = true;
		MidiGeneratorGUI midiGeneratorGUI = new MidiGeneratorGUI("General MIDI Generator (BETA)");
	}

	public MidiGeneratorGUI(String title) {
		super(title);

		// register the closebox event
		this.addWindowListener(this);

		setLayout(new GridBagLayout());
		//setPreferredSize(new Dimension(1400, 1000));

		//constraints.fill = GridBagConstraints.BOTH;
		constraints.gridwidth = GridBagConstraints.REMAINDER;
		initTitles(0, GridBagConstraints.CENTER);


		// chord tool tip

		JPanel chordToolTip = new JPanel();
		tipLabel = new JLabel(
				"Chord meaning: 1 = I(major), 10 = i(minor), 100 = I(aug), 1000 = I(dim), 10000 = I7(major), 100000 = i7(minor)");
		tipLabel.setForeground(isDarkMode ? Color.CYAN : Color.BLUE);
		chordToolTip.add(tipLabel);
		constraints.gridy = 298;
		constraints.anchor = GridBagConstraints.CENTER;
		add(chordToolTip, constraints);

		// ---- OTHER SETTINGS ----
		{
			initMacroParams(300, GridBagConstraints.CENTER);

			// chord settings - variety/spice
			// chord settings - progressions
			initChordSettings(310, GridBagConstraints.CENTER);

			// randomization buttons
			initRandomButtons(320, GridBagConstraints.CENTER);

		}
		createHorizontalSeparator(390, this);


		// ---- INSTRUMENTS ----
		{
			// melody
			initMelody(20, GridBagConstraints.WEST);
			initMelodyGenSettings(25, GridBagConstraints.CENTER);

			createHorizontalSeparator(30, this);

			// bass
			initBassRoots(33, GridBagConstraints.WEST);
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

			constraints.gridy = 295;
			add(instrumentTabPane, constraints);
		}

		// ---- CONTROL PANEL -----
		initControlPanel(400, GridBagConstraints.CENTER);


		// ---- PLAY PANEL ----
		initPlayPanel(420, GridBagConstraints.CENTER);


		// --- GENERATED MIDI DRAG n DROP ---

		constraints.anchor = GridBagConstraints.CENTER;

		JPanel midiDragAndDropPanel = new JPanel();

		generatedMidi = new JList<File>();
		generatedMidi.setTransferHandler(new FileTransferHandler());
		generatedMidi.setDragEnabled(true);
		midiDragAndDropPanel.add(new JLabel("Midi Drag'N'Drop:"));
		midiDragAndDropPanel.add(generatedMidi);
		constraints.gridy = 960;
		add(midiDragAndDropPanel, constraints);

		// ---- MESSAGE PANEL ----

		JPanel messagePanel = new JPanel();
		messageLabel = new JLabel("Click something!");
		messageLabel.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		messageLabel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		messagePanel.add(messageLabel);
		constraints.gridy = 999;
		add(messagePanel, constraints);

		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		int screenHeight = d.height;
		int screenWidth = d.width;
		setSize(screenWidth / 2, screenHeight / 2);
		setLocation(screenWidth / 4, screenHeight / 4);

		setFocusable(true);
		requestFocus();
		requestFocusInWindow();


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
		mainTitle.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		mainTitle.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		subTitle = new JLabel("by Vibe Historian");
		subTitle.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		subTitle.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		constraints.weightx = 100;
		constraints.weighty = 100;
		constraints.gridx = 0;
		constraints.gridy = startY;
		constraints.gridwidth = 3;
		constraints.gridheight = 1;
		constraints.anchor = anchorSide;
		add(mainTitle, constraints);
		constraints.gridy = 1;
		add(subTitle, constraints);

		JPanel darkModeSwitchPanel = new JPanel();

		constraints.gridy = startY + 3;
		JButton switchDarkModeButton = new JButton("Switch Dark Mode");
		switchDarkModeButton.addActionListener(this);
		switchDarkModeButton.setActionCommand("SwitchDarkMode");
		darkModeSwitchPanel.add(switchDarkModeButton);
		add(darkModeSwitchPanel, constraints);
	}

	private void initMacroParams(int startY, int anchorSide) {
		JPanel macroParams = new JPanel();
		useArrangement = new JCheckBox("Arrange", true);
		macroParams.add(useArrangement);

		soundbankFilename = new JTextField(SOUNDBANK_DEFAULT, 18);
		macroParams.add(new JLabel("Soundbank name:"));
		macroParams.add(soundbankFilename);

		pieceLength = new JTextField("1", 2);
		macroParams.add(new JLabel("Piece Length:"));
		macroParams.add(pieceLength);

		transposeScore = new JTextField("0", 3);
		macroParams.add(new JLabel("Transpose:"));
		macroParams.add(transposeScore);

		Random bpmRand = new Random();
		mainBpm = new JTextField(String.valueOf(bpmRand.nextInt(30) + 70), 3);


		macroParams.add(new JLabel("BPM:"));
		macroParams.add(mainBpm);

		fixedLengthChords = new JCheckBox();
		fixedLengthChords.setSelected(true);
		macroParams.add(new JLabel("Chord duration fixed: "));
		macroParams.add(fixedLengthChords);

		minorScale = new JCheckBox();
		minorScale.setSelected(false);

		macroParams.add(new JLabel("Minor Key:"));
		macroParams.add(minorScale);


		useAllInsts = new JCheckBox("Use all inst., except:", false);
		macroParams.add(useAllInsts);
		bannedInsts = new JTextField("", 8);
		macroParams.add(bannedInsts);
		reinitInstPools = new JButton("Initialize all inst.");
		reinitInstPools.addActionListener(this);
		reinitInstPools.setActionCommand("InitAllInsts");
		macroParams.add(reinitInstPools);

		constraints.gridy = startY;
		add(macroParams, constraints);
	}

	private void initMelodyGenSettings(int startY, int anchorSide) {
		JPanel melodySettingsPanel = new JPanel();

		maxJump = new JTextField("4", 2);
		maxExceptions = new JTextField("1", 2);
		melodySettingsPanel.add(new JLabel("Max Note Jump:"));
		melodySettingsPanel.add(maxJump);
		melodySettingsPanel.add(new JLabel("Max Exceptions:"));
		melodySettingsPanel.add(maxExceptions);
		randomChordNote = new JCheckBox();
		randomChordNote.setSelected(true);
		melodyFirstNoteFromChord = new JCheckBox();
		melodyFirstNoteFromChord.setSelected(true);


		melodySettingsPanel.add(new JLabel("Note#1 From Chord:"));
		melodySettingsPanel.add(melodyFirstNoteFromChord);
		melodySettingsPanel.add(new JLabel("But Randomized:"));
		melodySettingsPanel.add(randomChordNote);


		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		add(melodySettingsPanel, constraints);
	}

	private void initMelody(int startY, int anchorSide) {
		JPanel melodyPanel = new JPanel();
		addMelody = new JCheckBox("Enable Melody", true);
		melodyPanel.add(addMelody);
		melodyInst = new InstComboBox();
		melodyInst.initInstPool(POOL.PLUCK);
		melodyInst.setInstrument(8);
		arpCopyMelodyInst = new JCheckBox("Inst. copy ARP1", true);


		/*melodyInst.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent evt) {
				arpMelodyLockInst.setSelected(false);
			}
			
			public void mouseEntered(java.awt.event.MouseEvent evt) {
				// do nothing
			}
		});
		
		for (Component c : melodyInst.getComponents()) {
			c.addMouseListener(new java.awt.event.MouseAdapter() {
				public void mouseClicked(java.awt.event.MouseEvent evt) {
					arpMelodyLockInst.setSelected(false);
				}
				
				public void mouseEntered(java.awt.event.MouseEvent evt) {
					// do nothing
				}
			});
		}*/

		melodyLock = new JCheckBox("Lock Inst.", false);

		userMelodySeed = new JTextField("0", 10);
		JButton generateUserMelodySeed = new JButton("Random");
		generateUserMelodySeed.addActionListener(this);
		generateUserMelodySeed.setActionCommand("GenMelody");
		JButton clearUserMelodySeed = new JButton("Clear");
		clearUserMelodySeed.addActionListener(this);
		clearUserMelodySeed.setActionCommand("ClearMelody");
		melodyPauseChance = new JTextField("20", 3);
		randomMelodyOnRegenerate = new JCheckBox("On regen", false);


		melodyPanel.add(melodyLock);
		melodyPanel.add(melodyInst);
		melodyPanel.add(arpCopyMelodyInst);
		melodyPanel.add(new JLabel("Pause%"));
		melodyPanel.add(melodyPauseChance);
		melodyPanel.add(generateUserMelodySeed);
		melodyPanel.add(userMelodySeed);
		melodyPanel.add(randomMelodyOnRegenerate);
		melodyPanel.add(clearUserMelodySeed);


		melodyPanel.add(new JLabel("Midi ch.: 1"));

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		add(melodyPanel, constraints);


	}


	private void initChordGenSettings(int startY, int anchorSide) {
		JPanel chordSettingsPanel = new JPanel();

		addChords = new JCheckBox("Enable Chords", true);
		chordSettingsPanel.add(addChords);

		JButton chordAddJButton = new JButton("+Chord");
		chordAddJButton.addActionListener(this);
		chordAddJButton.setActionCommand("AddChord");
		chordSettingsPanel.add(chordAddJButton);

		randomChordsToGenerate = new JTextField("5", 2);
		JButton randomizeChords = new JButton("Generate Chords:");
		randomizeChords.addActionListener(this);
		randomizeChords.setActionCommand("RandChords");
		randomChordsGenerateOnCompose = new JCheckBox("on Compose", true);
		chordSettingsPanel.add(randomizeChords);
		chordSettingsPanel.add(randomChordsToGenerate);
		chordSettingsPanel.add(randomChordsGenerateOnCompose);

		randomChordStretchType = new JComboBox<>();
		MidiUtils.addAllToJComboBox(new String[] { "NONE", "FIXED", "AT_MOST" },
				randomChordStretchType);
		randomChordStretchType.setSelectedItem("NONE");
		chordSettingsPanel.add(new JLabel("StretCh."));
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
		chordSettingsPanel.add(new JLabel("Chord%"));
		chordSettingsPanel.add(randomChordSustainChance);
		chordSettingsPanel.add(new JLabel("Max split%"));
		chordSettingsPanel.add(randomChordMaxSplitChance);
		chordSettingsPanel.add(randomChordPattern);
		chordSettingsPanel.add(new JLabel("Shift%"));
		chordSettingsPanel.add(randomChordShiftChance);

		JButton clearChordPatternSeeds = new JButton("Clear presets");
		clearChordPatternSeeds.addActionListener(this);
		clearChordPatternSeeds.setActionCommand("ClearChordPatterns");

		chordSettingsPanel.add(clearChordPatternSeeds);
		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		add(chordSettingsPanel, constraints);
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
		addArps = new JCheckBox("Enable Arps   ", true);
		arpsSettingsPanel.add(addArps);

		JButton arpAddJButton = new JButton("  +Arp ");
		arpAddJButton.addActionListener(this);
		arpAddJButton.setActionCommand("AddArp");
		arpsSettingsPanel.add(arpAddJButton);

		randomArpsToGenerate = new JTextField("5", 2);
		JButton randomizeArps = new JButton("Generate Arps:    ");
		randomizeArps.addActionListener(this);
		randomizeArps.setActionCommand("RandArps");
		randomArpsGenerateOnCompose = new JCheckBox("on Compose", true);
		arpsSettingsPanel.add(randomizeArps);
		arpsSettingsPanel.add(randomArpsToGenerate);
		arpsSettingsPanel.add(randomArpsGenerateOnCompose);

		randomArpStretchType = new JComboBox<>();
		MidiUtils.addAllToJComboBox(new String[] { "NONE", "FIXED", "AT_MOST" },
				randomArpStretchType);
		randomArpStretchType.setSelectedItem("AT_MOST");
		arpsSettingsPanel.add(new JLabel("StretCh."));
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


		arpsSettingsPanel.add(new JLabel("Arp#"));
		arpsSettingsPanel.add(randomArpHitsPicker);
		arpsSettingsPanel.add(randomArpHitsPerPattern);
		arpsSettingsPanel.add(randomArpAllSameHits);
		arpsSettingsPanel.add(randomArpAllSameInst);
		arpsSettingsPanel.add(randomArpUseChordFill);
		arpsSettingsPanel.add(randomArpTranspose);
		arpsSettingsPanel.add(randomArpPattern);
		arpsSettingsPanel.add(new JLabel("Pattern shift%"));
		arpsSettingsPanel.add(arpShiftChance);

		JButton clearArpPatternSeeds = new JButton("Clear presets");
		clearArpPatternSeeds.addActionListener(this);
		clearArpPatternSeeds.setActionCommand("ClearArpPatterns");

		arpsSettingsPanel.add(clearArpPatternSeeds);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		add(arpsSettingsPanel, constraints);
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

	private void initBassRoots(int startY, int anchorSide) {
		JPanel bassRootsPanel = new JPanel();
		addBassRoots = new JCheckBox("Enable Basses", true);
		bassRootsInst = new InstComboBox();
		bassRootsInst.initInstPool(POOL.BASS);
		bassRootsInst.setInstrument(74);


		bassRootsLock = new JCheckBox("Lock Inst.", false);


		bassRootsPanel.add(addBassRoots);
		bassRootsPanel.add(bassRootsLock);
		bassRootsPanel.add(bassRootsInst);

		bassRootsPanel.add(new JLabel("Midi ch.: 9"));

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		add(bassRootsPanel, constraints);
	}

	private void initDrumGenSettings(int startY, int anchorSide) {
		JPanel drumsPanel = new JPanel();
		addDrums = new JCheckBox("Enable Drums ", true);
		drumsPanel.add(addDrums);
		//drumsPanel.add(drumInst);


		JButton drumAddJButton = new JButton(" +Drum ");
		drumAddJButton.addActionListener(this);
		drumAddJButton.setActionCommand("AddDrum");
		drumsPanel.add(drumAddJButton);

		randomDrumsToGenerate = new JTextField("6", 2);
		JButton randomizeDrums = new JButton("Generate Drums: ");
		randomizeDrums.addActionListener(this);
		randomizeDrums.setActionCommand("RandDrums");
		randomDrumsGenerateOnCompose = new JCheckBox("on Compose", true);
		drumsPanel.add(randomizeDrums);
		drumsPanel.add(randomDrumsToGenerate);
		drumsPanel.add(randomDrumsGenerateOnCompose);

		JButton clearPatternSeeds = new JButton("Clear patterns");
		clearPatternSeeds.addActionListener(this);
		clearPatternSeeds.setActionCommand("ClearPatterns");

		randomDrumMaxSwingAdjust = new JTextField("20", 2);
		randomDrumSlide = new JCheckBox("Random delay", false);
		randomDrumPattern = new JCheckBox("Pattern presets", true);
		randomDrumVelocityPatternChance = new JTextField("50", 3);
		randomDrumShiftChance = new JTextField("25", 3);

		drumsPanel.add(new JLabel("Max swing%+-"));
		drumsPanel.add(randomDrumMaxSwingAdjust);

		drumsPanel.add(randomDrumSlide);
		drumsPanel.add(randomDrumPattern);
		drumsPanel.add(new JLabel("Velocity pattern%"));
		drumsPanel.add(randomDrumVelocityPatternChance);
		drumsPanel.add(new JLabel("Pattern shift%"));
		drumsPanel.add(randomDrumShiftChance);
		drumsPanel.add(clearPatternSeeds);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		add(drumsPanel, constraints);
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

	private void initRandomButtons(int startY, int anchorSide) {
		JPanel randomButtonsPanel = new JPanel();

		JButton randomizeInstruments = new JButton("Randomize Inst.");
		randomizeInstruments.addActionListener(this);
		randomizeInstruments.setActionCommand("RandomizeInst");

		JButton randomizeBpmTransp = new JButton("Randomize BPM+Transpose");
		randomizeBpmTransp.addActionListener(this);
		randomizeBpmTransp.setActionCommand("RandomizeBpmTrans");

		randomizeInstOnCompose = new JCheckBox("on Compose");
		randomizeBmpTransOnCompose = new JCheckBox("on Compose");
		randomizeInstOnCompose.setSelected(true);
		randomizeBmpTransOnCompose.setSelected(true);


		constraints.anchor = GridBagConstraints.CENTER;


		randomButtonsPanel.add(randomizeInstruments);
		randomButtonsPanel.add(randomizeInstOnCompose);
		randomButtonsPanel.add(randomizeBpmTransp);
		randomButtonsPanel.add(randomizeBmpTransOnCompose);
		arpAffectsBpm = new JCheckBox("Slowed by ARP", true);
		randomButtonsPanel.add(arpAffectsBpm);

		JButton randomizeStrums = new JButton("Randomize strums");
		randomizeStrums.addActionListener(this);
		randomizeStrums.setActionCommand("RandStrums");
		randomButtonsPanel.add(randomizeStrums);

		randomizeChordStrumsOnCompose = new JCheckBox("On compose");
		randomizeChordStrumsOnCompose.setSelected(true);
		randomButtonsPanel.add(randomizeChordStrumsOnCompose);

		switchOnComposeRandom = new JButton("Uncheck all 'on Compose'");
		switchOnComposeRandom.addActionListener(this);
		switchOnComposeRandom.setActionCommand("UncheckComposeRandom");
		randomButtonsPanel.add(switchOnComposeRandom);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		add(randomButtonsPanel, constraints);
	}

	private void initChordSettings(int startY, int anchorSide) {
		// CHORD SETTINGS 1 - chord variety 
		JPanel chordSettingsProgressionPanel = new JPanel();


		chordSlashChance = new JTextField("25", 3);
		chordSettingsProgressionPanel.add(new JLabel("Ch1 slash chord%"));
		chordSettingsProgressionPanel.add(chordSlashChance);

		spiceChance = new JTextField("8", 3);
		chordSettingsProgressionPanel.add(new JLabel("Spice%"));
		chordSettingsProgressionPanel.add(spiceChance);

		spiceAllowDimAug = new JCheckBox("Dim/Aug");
		spiceAllowDimAug.setSelected(false);
		chordSettingsProgressionPanel.add(spiceAllowDimAug);

		spiceAllow9th13th = new JCheckBox("9th/13th");
		spiceAllow9th13th.setSelected(true);
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

		userChordsEnabled = new JCheckBox();
		userChordsEnabled.setSelected(false);


		chordSettingsProgressionPanel.add(new JLabel("Custom chords:"));
		chordSettingsProgressionPanel.add(userChordsEnabled);

		userChords = new JTextField("R", 8);
		chordSettingsProgressionPanel.add(new JLabel("Chords:"));
		chordSettingsProgressionPanel.add(userChords);
		userChordsDurations = new JTextField("2,2,2,2", 6);
		chordSettingsProgressionPanel.add(new JLabel("Chord durations (max. 8):"));
		chordSettingsProgressionPanel.add(userChordsDurations);
		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		add(chordSettingsProgressionPanel, constraints);
	}

	private void initControlPanel(int startY, int anchorSide) {
		JPanel controlPanel = new JPanel();
		randomSeed = new JTextField("0", 8);
		compose = new JButton("Compose");
		compose.addActionListener(this);
		compose.setActionCommand("Compose");
		regenerate = new JButton("Regenerate");
		regenerate.addActionListener(this);
		regenerate.setActionCommand("Regenerate");
		JButton copySeed = new JButton("Copy seed");
		copySeed.addActionListener(this);
		copySeed.setActionCommand("CopySeed");
		JButton copyChords = new JButton("Copy chords");
		copyChords.addActionListener(this);
		copyChords.setActionCommand("CopyChords");
		JButton clearSeed = new JButton("Clear");
		clearSeed.addActionListener(this);
		clearSeed.setActionCommand("ClearSeed");

		JButton loadConfig = new JButton("Load Config");
		loadConfig.addActionListener(this);
		loadConfig.setActionCommand("LoadGUIConfig");

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
		add(controlPanel, constraints);
	}

	private void initPlayPanel(int startY, int anchorSide) {

		JPanel playSavePanel = new JPanel();

		stopMidi = new JButton("STOP");
		stopMidi.addActionListener(this);
		stopMidi.setActionCommand("StopMidi");
		startMidi = new JButton("PLAY");
		startMidi.addActionListener(this);
		startMidi.setActionCommand("StartMidi");

		JButton save3Star = new JButton("Save 3*");
		save3Star.addActionListener(this);
		save3Star.setActionCommand("Save 3*");
		JButton save4Star = new JButton("Save 4*");
		save4Star.addActionListener(this);
		save4Star.setActionCommand("Save 4*");
		JButton save5Star = new JButton("Save 5*");
		save5Star.addActionListener(this);
		save5Star.setActionCommand("Save 5*");

		JButton saveWavFile = new JButton("Export as .wav");
		saveWavFile.addActionListener(this);
		saveWavFile.setActionCommand("SaveWavFile");

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

		playSavePanel.add(startMidi);
		playSavePanel.add(stopMidi);
		playSavePanel.add(save3Star);
		playSavePanel.add(save4Star);
		playSavePanel.add(save5Star);
		playSavePanel.add(saveWavFile);
		playSavePanel.add(midiMode);
		playSavePanel.add(midiModeDevices);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		add(playSavePanel, constraints);
	}

	private void switchAllOnComposeCheckboxes(boolean state) {
		randomChordsGenerateOnCompose.setSelected(state);
		randomDrumsGenerateOnCompose.setSelected(state);
		randomizeBmpTransOnCompose.setSelected(state);
		randomizeChordStrumsOnCompose.setSelected(state);
		randomizeInstOnCompose.setSelected(state);
		//TODO:secondArpMultiplierRandom.setSelected(state);
		randomArpHitsPerPattern.setSelected(state);
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
		for (JSeparator x : separators) {
			x.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		}
		pack();
		setVisible(true);
		repaint();
	}

	private void composeMidi(boolean regenerate) {
		if (midiMode.isSelected()) {
			synth = null;
		} else {
			if (device != null) {
				device.close();
			}
			device = null;
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
				userMelodySeed.setText(String.valueOf(melodySeed));
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

		MelodyGenerator melodyGen = new MelodyGenerator();
		fillUserParameters();

		File makeDir = new File(MIDIS_FOLDER);
		makeDir.mkdir();

		String seedData = "" + masterpieceSeed;
		if (MelodyGenerator.USER_MELODY_SEED != 0 && addMelody.isSelected()) {
			seedData += "_" + userMelodySeed.getText();
		}

		String fileName = "seed" + seedData;
		String relPath = MIDIS_FOLDER + "/" + fileName + ".mid";
		int melodyInstrument = jm.constants.ProgramChanges.KALIMBA;
		melodyGen.generateMasterpiece(masterpieceSeed, relPath, melodyInstrument);
		currentMidi = null;


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
			pack();
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
				if (synth != null) {
					// soundbank synth already opened correctly, do nothing
				} else if (synthesizer != null) {
					// open soundbank synth
					for (Transmitter tm : sequencer.getTransmitters()) {
						tm.close();
					}

					sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
					synth = synthesizer;

				} else {
					// use default system synth
					for (Transmitter tm : sequencer.getTransmitters()) {
						tm.close();
					}
					Synthesizer defSynth = MidiSystem.getSynthesizer();
					defSynth.open();
					sequencer.getTransmitter().setReceiver(defSynth.getReceiver());


				}
			}


			/*if (synth != null) {
				double vol = 0.9D;
				ShortMessage volumeMessage = new ShortMessage();
				for (int i = 0; i < 16; i++) {
					volumeMessage.setMessage(ShortMessage.CONTROL_CHANGE, i, 7,
							(int) (vol * 127));
					synth.getReceiver().send(volumeMessage, -1);
				}
			}*/

			sequencer.setTickPosition(0);
			sequencer.start();  // start the playback

		} catch (MidiUnavailableException | InvalidMidiDataException | IOException ex) {
			ex.printStackTrace();
		}
	}

	private Synthesizer loadSynth() {
		Synthesizer synthesizer = null;
		try {
			File soundbankFile = new File(soundbankFilename.getText());
			if (soundbankFile.isFile()) {
				if (synth == null) {

					soundfont = MidiSystem.getSoundbank(
							new BufferedInputStream(new FileInputStream(soundbankFile)));
					synthesizer = MidiSystem.getSynthesizer();

					synthesizer.isSoundbankSupported(soundfont);
					synthesizer.open();
					synthesizer.loadAllInstruments(soundfont);
				}
				System.out.println("Playing using soundbank: " + soundbankFilename.getText());
			} else {
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
			int inst = melodyInst.getInstrument();
			melodyInst.initInstPool(melodyInst.getInstPool());
			melodyInst.setInstrument(inst);
			inst = bassRootsInst.getInstrument();
			bassRootsInst.initInstPool(bassRootsInst.getInstPool());
			bassRootsInst.setInstrument(inst);
		}


		if (ae.getActionCommand() == "InitAllInsts") {
			if (useAllInsts.isSelected()) {
				MidiUtils.initAllInsts();
			} else {
				MidiUtils.initNormalInsts();
			}
		}

		if (ae.getActionCommand() == "RandStrums" || (ae.getActionCommand() == "Compose"
				& randomizeChordStrumsOnCompose.isSelected())) {
			Random strumsGen = new Random();
			for (ChordPanel p : chordPanels) {
				p.setStrum((int) getRandomFromArray(strumsGen, MILISECOND_ARRAY_STRUM));
			}

		}

		if (ae.getActionCommand() == "RandomizeInst"
				|| (ae.getActionCommand() == "Compose" && randomizeInstOnCompose.isSelected())) {
			Random instGen = new Random();


			for (ChordPanel cp : chordPanels) {
				if (!cp.getLockInst()) {
					cp.getInstrumentBox().setSelectedIndex(
							instGen.nextInt(cp.getInstrumentBox().getItemCount()));
				}
			}
			for (ArpPanel ap : arpPanels) {
				if (!ap.getLockInst()) {
					ap.getInstrumentBox().setSelectedIndex(
							instGen.nextInt(ap.getInstrumentBox().getItemCount()));
				}
			}
			if (!melodyLock.isSelected()) {

				melodyInst.setSelectedIndex(instGen.nextInt(melodyInst.getItemCount()));
			}

			if (!bassRootsLock.isSelected()) {

				bassRootsInst.setSelectedIndex(instGen.nextInt(bassRootsInst.getItemCount()));
			}
		}

		if (ae.getActionCommand() == "RandArps" || (ae.getActionCommand() == "Compose"
				&& addArps.isSelected() && randomArpsGenerateOnCompose.isSelected())) {
			createRandomArpPanels(Integer.valueOf(randomArpsToGenerate.getText()), false);
		}

		if (ae.getActionCommand() == "RandDrums" || (ae.getActionCommand() == "Compose"
				&& addDrums.isSelected() && randomDrumsGenerateOnCompose.isSelected())) {
			createRandomDrumPanels(Integer.valueOf(randomDrumsToGenerate.getText()), false);
		}

		if (ae.getActionCommand() == "RandChords" || (ae.getActionCommand() == "Compose"
				&& addChords.isSelected() && randomChordsGenerateOnCompose.isSelected())) {
			createRandomChordPanels(Integer.valueOf(randomChordsToGenerate.getText()), false);
		}


		realBpm = Double.valueOf(mainBpm.getText());
		if (ae.getActionCommand() == "RandomizeBpmTrans" || (ae.getActionCommand() == "Compose"
				&& randomizeBmpTransOnCompose.isSelected())) {
			Random instGen = new Random();

			int bpm = instGen.nextInt(30) + 50;
			if (arpAffectsBpm.isSelected() && !arpPanels.isEmpty()) {
				int highestArpPattern = arpPanels.stream()
						.map(e -> e.getPatternRepeat() / e.getChordSpan())
						.max((e1, e2) -> Integer.compare(e1, e2)).get();
				if (highestArpPattern > 2) {
					bpm *= (2 / ((double) highestArpPattern));
				}
			}
			mainBpm.setText("" + bpm);
			realBpm = bpm;
			transposeScore.setText(String.valueOf(instGen.nextInt(12) - 6));
		}


		// midi generation
		if (ae.getActionCommand() == "Compose" || ae.getActionCommand() == "Regenerate") {
			boolean isRegenerateOnly = ae.getActionCommand() == "Regenerate";
			switchMidiButtons(false);
			SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
				@Override
				protected Void doInBackground()
						throws InterruptedException, MidiUnavailableException, IOException {
					composeMidi(isRegenerateOnly);
					return null;
				}

				@Override
				protected void done() {
					switchMidiButtons(true);
					currentChords.setText(
							"Chords:[" + StringUtils.join(MelodyGenerator.chordInts, ",") + "]");
					pack();
					repaint();
				}
			};
			worker.execute();

		}

		if (ae.getActionCommand() == "StopMidi") {
			if (sequencer != null) {
				System.out.println("Stopping Midi..");
				sequencer.stop();
				System.out.println("Stopped Midi!");
			} else {
				System.out.println("Sequencer is NULL!");
			}
		}

		if (ae.getActionCommand() == "StartMidi") {
			if (sequencer != null) {
				System.out.println("Starting Midi..");
				sequencer.setTickPosition(0);
				sequencer.start();
				System.out.println("Started Midi!");
			} else {
				System.out.println("Sequencer is NULL!");
			}
		}

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

				String soundbankLoadedString = (synth != null) ? "SB_" : "";

				String finalFilePath = currentMidi.getParent() + ratingDirectory + f.format(date)
						+ "_" + soundbankLoadedString + currentMidi.getName();

				File savedMidi = new File(finalFilePath);
				try {
					FileUtils.copyFile(currentMidi, savedMidi);
					copyGUItoConfig();
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
			pack();
			repaint();
			SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
				@Override
				protected Void doInBackground()
						throws InterruptedException, MidiUnavailableException, IOException {
					SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();
					Synthesizer defSynth;
					f.applyPattern("yyMMdd-HH-mm-ss");
					Date date = new Date();
					defSynth = (synth != null) ? synth : MidiSystem.getSynthesizer();
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
			userMelodySeed.setText(String.valueOf(melodySeed));
		}

		if (ae.getActionCommand() == "ClearMelody") {
			userMelodySeed.setText(String.valueOf(0));
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
			String str = StringUtils.join(MelodyGenerator.chordInts, ",");
			userChords.setText(str);
			System.out.println("Copied chords: " + str);
		}

		if (ae.getActionCommand() == "ClearSeed") {
			randomSeed.setText("0");
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
					guiConfig = unmarshall(files[0]);
					copyConfigToGUI();
				} catch (JAXBException | IOException e) {
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
			pack();
			repaint();
		}

		if (ae.getActionCommand().startsWith("RemoveDrum,")) {
			String drumNumber = ae.getActionCommand().split(",")[1];
			removeDrumPanel(Integer.valueOf(drumNumber), true);
			randomDrumsToGenerate.setText("" + drumPanels.size());
		}

		if (ae.getActionCommand() == "ClearChordPatterns") {
			for (ChordPanel cp : chordPanels) {
				cp.setPatternSeed(0);
				cp.setPattern(RhythmPattern.RANDOM);

			}
		}

		if (ae.getActionCommand() == "AddChord") {
			//addChordPanelToLayout();
			createRandomChordPanels(chordPanels.size() + 1, true);
			randomChordsToGenerate.setText("" + chordPanels.size());

			pack();
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
			pack();
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


		if (ae.getActionCommand() == "SwitchDarkMode") {
			switchDarkMode();
		}

		{
			// recalculate stuff
			//randomChordsCount.setText("" + chordPanels.size());
			//randomArpsToGenerate.setText("" + arpPanels.size());
			//randomDrumsCount.setText("" + drumPanels.size());

			instrumentTabPane.setTitleAt(0, "Chords (" + chordPanels.size() + ")");
			instrumentTabPane.setTitleAt(1, "Arps (" + arpPanels.size() + ")");
			instrumentTabPane.setTitleAt(2, "Drums (" + drumPanels.size() + ")");
		}

		System.out.println("Finished.. ::" + ae.getActionCommand() + "::");
		messageLabel.setText("::" + ae.getActionCommand() + "::");
	}

	public void fillUserParameters() {
		try {
			MelodyGenerator.DISPLAY_SCORE = !midiMode.isSelected();

			MelodyGenerator.PIECE_LENGTH = Integer.valueOf(pieceLength.getText());
			MelodyGenerator.FIXED_LENGTH = fixedLengthChords.isSelected();
			MelodyGenerator.TRANSPOSE_SCORE = Integer.valueOf(transposeScore.getText());
			MelodyGenerator.MINOR_SONG = minorScale.isSelected();
			MelodyGenerator.MAIN_BPM = Double.valueOf(mainBpm.getText());


			MelodyGenerator.MAX_JUMP = Integer.valueOf(maxJump.getText());
			MelodyGenerator.MAX_EXCEPTIONS = Integer.valueOf(maxExceptions.getText());
			MelodyGenerator.FIRST_NOTE_FROM_CHORD = melodyFirstNoteFromChord.isSelected();
			MelodyGenerator.RANDOM_CHORD_NOTE = randomChordNote.isSelected();
			MelodyGenerator.MELODY_PAUSE_CHANCE = Integer.valueOf(melodyPauseChance.getText());

			MelodyGenerator.SPICE_CHANCE = Integer.valueOf(spiceChance.getText());
			MelodyGenerator.SPICE_ALLOW_DIM_AUG = spiceAllowDimAug.isSelected();
			MelodyGenerator.SPICE_ALLOW_9th_13th = spiceAllow9th13th.isSelected();
			MelodyGenerator.CHORD_SLASH_CHANCE = Integer.valueOf(chordSlashChance.getText());

			MelodyGenerator.FIRST_CHORD = chordSelect(
					(String) firstChordSelection.getSelectedItem());
			MelodyGenerator.LAST_CHORD = chordSelect((String) lastChordSelection.getSelectedItem());

			MelodyGenerator.USER_MELODY_SEED = Integer
					.valueOf((StringUtils.isEmpty(userMelodySeed.getText())) ? "0"
							: userMelodySeed.getText());
			if (userChordsEnabled.isSelected()) {
				String[] userChordsSplit = userChords.getText().split(",");
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
						List<Integer> userChordsParsed = new ArrayList<>();
						List<Double> userChordsDurationsParsed = new ArrayList<>();
						for (int i = 0; i < userChordsDurationsSplit.length; i++) {
							if (!userChordsRandom) {
								userChordsParsed.add(Integer.valueOf(userChordsSplit[i]));
							}
							userChordsDurationsParsed
									.add(Double.valueOf(userChordsDurationsSplit[i]));
						}
						MelodyGenerator.userChords = userChordsParsed;
						MelodyGenerator.userChordsDurations = userChordsDurationsParsed;
						System.out.println(userChordsDurationsParsed.toString());
						//MelodyGenerator.FIXED_LENGTH = false;
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


			MelodyGenerator.PARTS_INSTRUMENT_MAP.clear();

			if (addMelody.isSelected())
				MelodyGenerator.PARTS_INSTRUMENT_MAP.put(PARTS.MELODY, melodyInst.getInstrument());
			if (addChords.isSelected()) {
				MelodyGenerator.PARTS_INSTRUMENT_MAP.put(PARTS.CHORDS, 0);
				MelodyGenerator.CHORD_PARTS = getChordPartsFromChordPanels(true);


				MelodyGenerator.CHORD_SETTINGS = getChordSettingsFromUI();
			}
			if (addArps.isSelected()) {
				MelodyGenerator.PARTS_INSTRUMENT_MAP.put(PARTS.ARPS, 0);
				MelodyGenerator.ARP_PARTS = getArpPartsFromArpPanels(true);
				//MelodyGenerator.ARP_SETTINGS = getAr
			}

			if (addBassRoots.isSelected())
				MelodyGenerator.PARTS_INSTRUMENT_MAP.put(PARTS.BASSROOTS,
						bassRootsInst.getInstrument());
			if (addDrums.isSelected()) {
				MelodyGenerator.PARTS_INSTRUMENT_MAP.put(PARTS.DRUMS, 0);
				MelodyGenerator.DRUM_PARTS = getDrumPartsFromDrumPanels(true);
			}

			if (useArrangement.isSelected()) {
				Arrangement arr = new Arrangement();
				arr.generateDefaultArrangement(lastRandomSeed);
				MelodyGenerator.ARRANGEMENT = arr;
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

	private void setChordSettingsFromUI(ChordGenSettings settings) {
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

		int microsecondsPerQtrNote = (int) (500000 * 120 / MelodyGenerator.MAIN_BPM);
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

	@SuppressWarnings("restriction")
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
	}

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

	public void copyGUItoConfig() {
		guiConfig.setRandomSeed(lastRandomSeed);

		guiConfig.setSoundbankName(soundbankFilename.getText());
		guiConfig.setMinor(minorScale.isSelected());
		guiConfig.setPieceLength(Integer.valueOf(pieceLength.getText()));
		guiConfig.setFixedDuration(fixedLengthChords.isSelected());

		guiConfig.setTranspose(Integer.valueOf(transposeScore.getText()));
		guiConfig.setBpm(Double.valueOf(mainBpm.getText()));
		guiConfig.setArpAffectsBpm(arpAffectsBpm.isSelected());

		guiConfig.setMelodyEnable(addMelody.isSelected());
		guiConfig.setChordsEnable(addChords.isSelected());
		guiConfig.setArpsEnable(addArps.isSelected());
		guiConfig.setBassRootsEnable(addBassRoots.isSelected());
		guiConfig.setDrumsEnable(addDrums.isSelected());

		guiConfig.setDrumParts(getDrumPartsFromDrumPanels(false));
		guiConfig.setChordParts(getChordPartsFromChordPanels(false));
		guiConfig.setArpParts(getArpPartsFromArpPanels(false));

		guiConfig.setChordGenSettings(getChordSettingsFromUI());
		//guiConfig.setArpGenSettings(getArpSettingsFromUI());

		guiConfig.setMelodyInst(melodyInst.getInstrument());
		guiConfig.setBassRootsInst(bassRootsInst.getInstrument());

		guiConfig.setUserMelodySeed(!StringUtils.isEmpty(userMelodySeed.getText())
				? Long.valueOf(userMelodySeed.getText())
				: 0);

		guiConfig.setMaxNoteJump(Integer.valueOf(maxJump.getText()));
		guiConfig.setMaxExceptions(Integer.valueOf(maxExceptions.getText()));
		guiConfig.setMelodyPauseChance(Integer.valueOf(melodyPauseChance.getText()));
		guiConfig.setSpiceChance(Integer.valueOf(spiceChance.getText()));
		guiConfig.setDimAugEnabled(spiceAllowDimAug.isSelected());
		guiConfig.setChordSlashChance(Integer.valueOf(chordSlashChance.getText()));

		guiConfig.setFirstChord((String) firstChordSelection.getSelectedItem());
		guiConfig.setLastChord((String) lastChordSelection.getSelectedItem());
		guiConfig.setCustomChordsEnabled(userChordsEnabled.isSelected());
		guiConfig.setCustomChords(StringUtils.join(MelodyGenerator.chordInts, ","));
		guiConfig.setCustomChordDurations(userChordsDurations.getText());

		guiConfig.setFirstNoteFromChord(melodyFirstNoteFromChord.isSelected());
		guiConfig.setFirstNoteRandomized(randomChordNote.isSelected());
	}

	public void copyConfigToGUI() {
		randomSeed.setText(String.valueOf(guiConfig.getRandomSeed()));
		lastRandomSeed = (int) guiConfig.getRandomSeed();

		soundbankFilename.setText(guiConfig.getSoundbankName());
		minorScale.setSelected(guiConfig.isMinor());
		pieceLength.setText(String.valueOf(guiConfig.getPieceLength()));
		fixedLengthChords.setSelected(guiConfig.isFixedDuration());

		transposeScore.setText(String.valueOf(guiConfig.getTranspose()));
		mainBpm.setText(String.valueOf(guiConfig.getBpm()));
		arpAffectsBpm.setSelected(guiConfig.isArpAffectsBpm());

		addMelody.setSelected(guiConfig.isMelodyEnable());
		addChords.setSelected(guiConfig.isChordsEnable());
		addArps.setSelected(guiConfig.isArp1ArpEnable());
		addBassRoots.setSelected(guiConfig.isBassRootsEnable());

		addDrums.setSelected(guiConfig.isDrumsEnable());
		recreateDrumPanelsFromDrumParts(guiConfig.getDrumParts());
		recreateChordPanelsFromChordParts(guiConfig.getChordParts());
		recreateArpPanelsFromArpParts(guiConfig.getArpParts());

		setChordSettingsFromUI(guiConfig.getChordGenSettings());

		melodyInst.setInstrument(guiConfig.getMelodyInst());

		bassRootsInst.setInstrument(guiConfig.getBassRootsInst());

		userMelodySeed.setText(String.valueOf(guiConfig.getUserMelodySeed()));

		maxJump.setText(String.valueOf(guiConfig.getMaxNoteJump()));
		maxExceptions.setText(String.valueOf(guiConfig.getMaxExceptions()));
		melodyPauseChance.setText(String.valueOf(guiConfig.getMelodyPauseChance()));
		spiceChance.setText(String.valueOf(guiConfig.getSpiceChance()));
		spiceAllowDimAug.setSelected(guiConfig.isDimAugEnabled());
		chordSlashChance.setText(String.valueOf(guiConfig.getChordSlashChance()));

		firstChordSelection.setSelectedItem(guiConfig.getFirstChord());
		lastChordSelection.setSelectedItem(guiConfig.getLastChord());
		userChordsEnabled.setSelected(guiConfig.isCustomChordsEnabled());
		userChords.setText(guiConfig.getCustomChords());
		userChordsDurations.setText(guiConfig.getCustomChordDurations());

		melodyFirstNoteFromChord.setSelected(guiConfig.isFirstNoteFromChord());
		randomChordNote.setSelected(guiConfig.isFirstNoteRandomized());
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


	private static void createHorizontalSeparator(int y, JFrame f) {
		int anchorTemp = constraints.anchor;
		JSeparator x = new JSeparator(SwingConstants.HORIZONTAL);
		x.setPreferredSize(new Dimension(1420, 2));
		x.setForeground(isDarkMode ? Color.CYAN : Color.BLUE);
		JPanel sepPanel = new JPanel();
		sepPanel.add(x);
		constraints.gridy = y;
		constraints.anchor = GridBagConstraints.CENTER;
		f.add(sepPanel, constraints);
		constraints.anchor = anchorTemp;
		separators.add(x);
	}

	public DrumPanel addDrumPanelToLayout() {
		int panelOrder = (drumPanels.size() > 0) ? getHighestDrumPanelNumber(drumPanels) + 1 : 1;

		DrumPanel drumJPanel = new DrumPanel(this);
		drumJPanel.setPanelOrder(panelOrder);
		drumJPanel.initComponents();
		drumPanels.add(drumJPanel);
		((JPanel) drumScrollPane.getViewport().getView()).add(drumJPanel);
		return drumJPanel;
	}

	private void removeDrumPanel(int order, boolean singleRemove) {
		DrumPanel panel = getDrumPanelByOrder(order, drumPanels);
		((JPanel) drumScrollPane.getViewport().getView()).remove(panel);
		drumPanels.remove(panel);

		if (singleRemove) {
			//reorderDrumPanels();
			pack();
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

		pack();
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

		int swingPercent = 50;

		if (true) {
			swingPercent = 50
					+ drumPanelGenerator
							.nextInt(Integer.valueOf(randomDrumMaxSwingAdjust.getText()) * 2 + 1)
					- Integer.valueOf(randomDrumMaxSwingAdjust.getText());
		}

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
			dp.setPitch(pitches.get(i));
			//dp.setPitch(32 + drumPanelGenerator.nextInt(33));


			dp.setChordSpan(drumPanelGenerator.nextInt(2) + 1);
			int patternOrder = 0;
			// use pattern in half the cases if checkbox selected
			if (drumPanelGenerator.nextBoolean() == true) {
				if (randomDrumPattern.isSelected()) {
					patternOrder = drumPanelGenerator.nextInt(RhythmPattern.values().length);
				}
			}
			int hits = 4;
			while (drumPanelGenerator.nextInt(10) < 5 && hits < 32) {
				hits *= 2;
			}
			if ((hits / dp.getChordSpan() >= 16)) {
				hits /= 2;
			}

			dp.setHitsPerPattern(hits);

			int adjustVelocity = -1 * dp.getHitsPerPattern() / dp.getChordSpan();

			if (dp.getPitch() == 35 || dp.getPitch() == 36 || dp.getPitch() == 38
					|| dp.getPitch() == 40) {
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


			if (dp.getPitch() > 41) {
				dp.setDelay(slide);
			}
			if (dp.getPitch() > 39) {
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

		pack();
		repaint();
	}


	private static int getHighestDrumPanelNumber(List<DrumPanel> panels) {
		int highest = 1;
		for (DrumPanel p : panels) {
			highest = (p.getPanelOrder() > highest) ? p.getPanelOrder() : highest;
		}
		return highest;
	}

	private static DrumPanel getDrumPanelByOrder(int order, List<DrumPanel> panels) {
		return panels.stream().filter(e -> e.getPanelOrder() == order).findFirst().get();
	}

	public ChordPanel addChordPanelToLayout() {
		int panelOrder = (chordPanels.size() > 0) ? getHighestChordPanelNumber(chordPanels) + 1 : 1;

		ChordPanel cp = new ChordPanel(this);
		cp.setPanelOrder(panelOrder);
		cp.initComponents();
		chordPanels.add(cp);
		((JPanel) chordScrollPane.getViewport().getView()).add(cp);
		return cp;
	}

	private void removeChordPanel(int order, boolean singleRemove) {
		ChordPanel panel = getChordPanelByOrder(order, chordPanels);
		((JPanel) chordScrollPane.getViewport().getView()).remove(panel);
		chordPanels.remove(panel);

		if (singleRemove) {
			//reorderChordPanels();
			pack();
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

		pack();
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
			} else {
				cp.setStretchEnabled(false);
			}

			cp.setPattern(RhythmPattern.values()[patternOrder]);

			if (chordPanelGenerator.nextInt(100) < Integer.valueOf(randomChordShiftChance.getText())
					&& patternOrder > 0) {
				cp.setPatternShift(
						chordPanelGenerator.nextInt(cp.getPattern().pattern.length - 1) + 1);
			}

			cp.setMidiChannel(11 + (cp.getPanelOrder() - 1) % 5);

		}

		pack();
		repaint();
	}


	private static int getHighestChordPanelNumber(List<ChordPanel> panels) {
		int highest = 1;
		for (ChordPanel p : panels) {
			highest = (p.getPanelOrder() > highest) ? p.getPanelOrder() : highest;
		}
		return highest;
	}

	private static ChordPanel getChordPanelByOrder(int order, List<ChordPanel> panels) {
		return panels.stream().filter(e -> e.getPanelOrder() == order).findFirst().get();
	}

	public ArpPanel addArpPanelToLayout() {
		int panelOrder = (arpPanels.size() > 0) ? getHighestArpPanelNumber(arpPanels) + 1 : 1;

		ArpPanel ap = new ArpPanel(this);
		ap.setPanelOrder(panelOrder);
		ap.initComponents();
		arpPanels.add(ap);
		((JPanel) arpScrollPane.getViewport().getView()).add(ap);
		return ap;
	}

	private void removeArpPanel(int order, boolean singleRemove) {
		ArpPanel panel = getArpPanelByOrder(order, arpPanels);
		((JPanel) arpScrollPane.getViewport().getView()).remove(panel);
		arpPanels.remove(panel);

		if (singleRemove) {
			//reorderArpPanels();
			pack();
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

		pack();
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

		if (arpCopyMelodyInst.isSelected() && addMelody.isSelected()) {
			fixedInstrument = melodyInst.getInstrument();
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
				if (arpCopyMelodyInst.isSelected() && addMelody.isSelected()) {
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


		pack();
		repaint();
	}


	private static int getHighestArpPanelNumber(List<ArpPanel> panels) {
		int highest = 1;
		for (ArpPanel p : panels) {
			highest = (p.getPanelOrder() > highest) ? p.getPanelOrder() : highest;
		}
		return highest;
	}

	private static ArpPanel getArpPanelByOrder(int order, List<ArpPanel> panels) {
		return panels.stream().filter(e -> e.getPanelOrder() == order).findFirst().get();
	}

	private static double getRandomFromArray(Random generator, double[] array) {
		return array[generator.nextInt(array.length)];
	}
}
