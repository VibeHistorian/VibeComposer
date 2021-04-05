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
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
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
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.border.BevelBorder;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.vibehistorian.midimasterpiece.midigenerator.MidiUtils.PARTS;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.sun.media.sound.AudioSynthesizer;

// main class

public class MidiGeneratorGUI extends JFrame
		implements ActionListener, ItemListener, WindowListener {
	
	private static final long serialVersionUID = -677536546851756969L;
	
	private static final String SOUNDBANK_DEFAULT = "MuseScore_General.sf2";
	private static final String MIDIS_FOLDER = "midis";
	
	private static final double[] CHORD_STRUM_DIVISION_ARRAY = { 10000, 2, 3, 4, 5, 6, 8, 12, 16,
			32 };
	private static final double[] SECOND_CHORD_STRUM_MULTIPLIER = { 1, 1.25, 1.5, 2, 3, 4 };
	
	private static boolean isDarkMode = false;
	JLabel mainTitle;
	JLabel subTitle;
	JButton switchDarkMode;
	Color messageColorDarkMode = new Color(200, 200, 200);
	Color messageColorLightMode = new Color(120, 120, 200);
	
	private Synthesizer synth = null;
	
	private GUIConfig guiConfig = new GUIConfig();
	
	private List<DrumPanel> drumPanels = new ArrayList<>();
	
	JTextField soundbankFilename;
	
	JTextField pieceLength;
	JTextField userChords;
	JTextField userChordsDurations;
	
	JLabel messageLabel;
	
	JList<File> generatedMidi;
	
	JTextField maxJump;
	JTextField maxExceptions;
	JTextField pauseChance;
	JTextField melodyPauseChance;
	JTextField secondArpPauseChance;
	
	JTextField randomDrumsCount;
	
	JTextField spiceChance;
	JTextField chordTransitionChance;
	JTextField chordSlashChance;
	JTextField chordStrum;
	JTextField secondChordStrum;
	JCheckBox randomizeChordStrumsOnCompose;
	JTextField transposeScore;
	
	JComboBox<String> arpCount;
	JComboBox<String> secondArpMultiplier;
	JComboBox<String> secondArpOctaveAdjust;
	JCheckBox secondArpMultiplierRandom;
	
	JCheckBox spiceAllowDimAug;
	JCheckBox melodyFirstNoteFromChord;
	JCheckBox randomArpPattern;
	JCheckBox randomArpCount;
	JCheckBox addMelody;
	JCheckBox addChords1;
	JCheckBox addChords2;
	JCheckBox addArp1;
	JCheckBox addArp2;
	JCheckBox addBassRoots;
	JCheckBox addDrums;
	
	JCheckBox melodyLock;
	JCheckBox chords1Lock;
	JCheckBox chords2Lock;
	JCheckBox arp1Lock;
	JCheckBox arp2Lock;
	JCheckBox bassRootsLock;
	
	JComboBox<String> melodyInst;
	JComboBox<String> chords1Inst;
	JComboBox<String> chords2Inst;
	JComboBox<String> arp1Inst;
	JComboBox<String> arp2Inst;
	JComboBox<String> bassRootsInst;
	JComboBox<String> drumInst;
	
	JTextField userMelodySeed;
	JCheckBox randomMelodyOnRegenerate;
	
	JCheckBox randomDrumsOnCompose;
	JCheckBox randomDrumSlide;
	JCheckBox randomDrumPattern;
	
	JTextField velocityPatternChance;
	JTextField rotationChance;
	
	JCheckBox randomChordNote;
	JCheckBox minorScale;
	JCheckBox fixedLengthChords;
	JCheckBox userChordsEnabled;
	
	JCheckBox randomizeInstOnCompose;
	JCheckBox randomizeBmpTransOnCompose;
	
	JCheckBox arpMelodyLockInst;
	JCheckBox arp2LockInst;
	JCheckBox arpPatternRepeat;
	JCheckBox arpAllowPauses;
	
	JCheckBox arpAffectsBpm;
	
	JComboBox<String> firstChordSelection;
	JComboBox<String> lastChordSelection;
	
	JTextField mainBpm;
	JTextField randomSeed;
	int firstChord = 0;
	int lastChord = 0;
	Sequencer sequencer = null;
	int lastRandomSeed = 0;
	File currentMidi = null;
	
	public static void main(String args[]) {
		FlatDarculaLaf.install();
		isDarkMode = true;
		MidiGeneratorGUI midiGeneratorGUI = new MidiGeneratorGUI("General MIDI Generator (BETA)");
	}
	
	public MidiGeneratorGUI(String title) {
		super(title);
		
		//register the closebox event
		this.addWindowListener(this);
		
		setLayout(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		
		mainTitle = new JLabel("General MIDI Generator (Beta)");
		mainTitle.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		mainTitle.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		subTitle = new JLabel("by Vibe Historian");
		subTitle.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		subTitle.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		constraints.weightx = 100;
		constraints.weighty = 100;
		constraints.gridx = 1;
		constraints.gridy = 0;
		constraints.gridwidth = 2;
		constraints.gridheight = 1;
		constraints.anchor = GridBagConstraints.CENTER;
		add(mainTitle, constraints);
		constraints.gridy = 1;
		add(subTitle, constraints);
		
		JPanel p5 = new JPanel();
		
		constraints.gridy = 5;
		JButton switchDarkModeButton = new JButton("Switch Dark Mode");
		switchDarkModeButton.addActionListener(this);
		switchDarkModeButton.setActionCommand("SwitchDarkMode");
		p5.add(switchDarkModeButton);
		add(p5, constraints);
		
		
		JPanel p10 = new JPanel();
		soundbankFilename = new JTextField(SOUNDBANK_DEFAULT, 18);
		p10.add(new JLabel("Soundbank name:"));
		p10.add(soundbankFilename);
		
		pieceLength = new JTextField("4", 2);
		p10.add(new JLabel("Piece Length:"));
		p10.add(pieceLength);
		
		fixedLengthChords = new JCheckBox();
		fixedLengthChords.setSelected(true);
		p10.add(new JLabel("Chord duration fixed: "));
		p10.add(fixedLengthChords);
		
		minorScale = new JCheckBox();
		minorScale.setSelected(false);
		
		p10.add(new JLabel("Minor Key:"));
		p10.add(minorScale);
		
		constraints.gridx = 1;
		constraints.gridy = 10;
		constraints.gridwidth = 3;
		constraints.gridheight = 1;
		add(p10, constraints);
		
		constraints.anchor = GridBagConstraints.WEST;
		
		// -------- INSTRUMENTS -------------------
		
		JPanel p50 = new JPanel();
		JPanel p60 = new JPanel();
		JPanel p70 = new JPanel();
		JPanel p80 = new JPanel();
		JPanel p90 = new JPanel();
		JPanel p100 = new JPanel();
		JPanel p110 = new JPanel();
		
		
		addMelody = new JCheckBox("Add Melody", false);
		melodyInst = new JComboBox<String>();
		addAllToJComboBox(MelodyGenerator.PLUCKY_INST_NAMES, melodyInst);
		arpMelodyLockInst = new JCheckBox("Inst. copy ARP1", true);
		
		addChords1 = new JCheckBox("Add Chords1", true);
		chords1Inst = new JComboBox<String>();
		addAllToJComboBox(MelodyGenerator.CHORD_INST_NAMES, chords1Inst);
		
		addChords2 = new JCheckBox("Add Chords2", true);
		chords2Inst = new JComboBox<String>();
		addAllToJComboBox(MelodyGenerator.PLUCKY_INST_NAMES, chords2Inst);
		
		addArp1 = new JCheckBox("Add ARP1", true);
		arp1Inst = new JComboBox<String>();
		addAllToJComboBox(MelodyGenerator.PLUCKY_INST_NAMES, arp1Inst);
		
		addArp2 = new JCheckBox("Add ARP2", true);
		arp2Inst = new JComboBox<String>();
		addAllToJComboBox(MelodyGenerator.PLUCKY_INST_NAMES, arp2Inst);
		arp2LockInst = new JCheckBox("Inst. copy ARP1", false);
		
		addBassRoots = new JCheckBox("Add BassRoots", true);
		bassRootsInst = new JComboBox<String>();
		addAllToJComboBox(MelodyGenerator.BASS_INST_NAMES, bassRootsInst);
		
		addDrums = new JCheckBox("Drums", false);
		
		selectJComboBoxByInst(melodyInst, MelodyGenerator.PLUCKY_INST_NAMES, 12);
		
		selectJComboBoxByInst(chords1Inst, MelodyGenerator.CHORD_INST_NAMES, 4);
		
		selectJComboBoxByInst(chords2Inst, MelodyGenerator.PLUCKY_INST_NAMES, 46);
		
		selectJComboBoxByInst(arp1Inst, MelodyGenerator.PLUCKY_INST_NAMES, 11);
		
		selectJComboBoxByInst(arp2Inst, MelodyGenerator.PLUCKY_INST_NAMES, 4);
		
		selectJComboBoxByInst(bassRootsInst, MelodyGenerator.BASS_INST_NAMES, 74);
		
		melodyInst.addMouseListener(new java.awt.event.MouseAdapter() {
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
		}
		
		melodyLock = new JCheckBox("Lock Inst.", false);
		chords1Lock = new JCheckBox("Lock Inst.", true);
		chords2Lock = new JCheckBox("Lock Inst.", true);
		arp1Lock = new JCheckBox("Lock Inst.", false);
		arp2Lock = new JCheckBox("Lock Inst.", false);
		bassRootsLock = new JCheckBox("Lock Inst.", false);
		
		userMelodySeed = new JTextField("0", 10);
		JButton generateUserMelodySeed = new JButton("Random");
		generateUserMelodySeed.addActionListener(this);
		generateUserMelodySeed.setActionCommand("GenMelody");
		JButton clearUserMelodySeed = new JButton("Clear");
		clearUserMelodySeed.addActionListener(this);
		clearUserMelodySeed.setActionCommand("ClearMelody");
		melodyPauseChance = new JTextField("20", 3);
		randomMelodyOnRegenerate = new JCheckBox("On regen", false);
		
		
		p50.add(addMelody);
		p50.add(melodyLock);
		p50.add(melodyInst);
		p50.add(arpMelodyLockInst);
		p50.add(new JLabel("Pause%:"));
		p50.add(melodyPauseChance);
		p50.add(generateUserMelodySeed);
		p50.add(userMelodySeed);
		p50.add(randomMelodyOnRegenerate);
		p50.add(clearUserMelodySeed);
		
		chordStrum = new JTextField("250", 4);
		secondChordStrum = new JTextField("500", 4);
		p60.add(addChords1);
		p60.add(chords1Lock);
		p60.add(chords1Inst);
		p60.add(new JLabel("Ch1 strum:"));
		p60.add(chordStrum);
		
		p70.add(addChords2);
		p70.add(chords2Lock);
		p70.add(chords2Inst);
		p70.add(new JLabel("Ch2 strum:"));
		p70.add(secondChordStrum);
		
		
		arpCount = new JComboBox<String>();
		addAllToJComboBox(new String[] { "1", "2", "3", "4", "5", "6", "7", "8" }, arpCount);
		arpCount.setSelectedIndex(2);
		randomArpPattern = new JCheckBox("Random pattern", true);
		randomArpCount = new JCheckBox("Random#", true);
		arpPatternRepeat = new JCheckBox("Repeatable", true);
		arpAllowPauses = new JCheckBox("Pauses", true);
		pauseChance = new JTextField("25", 3);
		
		
		p80.add(addArp1);
		p80.add(arp1Lock);
		p80.add(arp1Inst);
		p80.add(new JLabel("Arps#"));
		p80.add(arpCount);
		p80.add(randomArpCount);
		p80.add(randomArpPattern);
		p80.add(arpPatternRepeat);
		p80.add(arpAllowPauses);
		p80.add(new JLabel("%:"));
		p80.add(pauseChance);
		
		secondArpMultiplier = new JComboBox<String>();
		addAllToJComboBox(new String[] { "1", "2", "3", "4" }, secondArpMultiplier);
		secondArpMultiplier.setSelectedItem("2");
		
		secondArpOctaveAdjust = new JComboBox<String>();
		addAllToJComboBox(new String[] { "-24", "-12", "0", "12", "24" }, secondArpOctaveAdjust);
		secondArpOctaveAdjust.setSelectedItem("0");
		
		secondArpMultiplierRandom = new JCheckBox("random", true);
		
		secondArpPauseChance = new JTextField("50", 3);
		
		p90.add(addArp2);
		p90.add(arp2Lock);
		p90.add(arp2Inst);
		p90.add(arp2LockInst);
		p90.add(new JLabel("Repeats#"));
		p90.add(secondArpMultiplier);
		p90.add(secondArpMultiplierRandom);
		
		p90.add(new JLabel("Transpose+-"));
		p90.add(secondArpOctaveAdjust);
		p90.add(new JLabel("Pause%:"));
		p90.add(secondArpPauseChance);
		
		p100.add(addBassRoots);
		p100.add(bassRootsLock);
		p100.add(bassRootsInst);
		
		JButton drumAddJButton = new JButton("+Drum");
		drumAddJButton.addActionListener(this);
		drumAddJButton.setActionCommand("AddDrum");
		JButton clearPatternSeeds = new JButton("Clear patterns");
		clearPatternSeeds.addActionListener(this);
		clearPatternSeeds.setActionCommand("ClearPatterns");
		
		drumInst = new JComboBox<String>();
		addAllToJComboBox(MelodyGenerator.DRUM_NAMES, drumInst);
		
		selectDrumJComboBoxByInst(drumInst, 42);
		
		randomDrumsCount = new JTextField("4", 2);
		
		JButton randomizeDrums = new JButton("Randomize New Drums:");
		randomizeDrums.addActionListener(this);
		randomizeDrums.setActionCommand("RandDrums");
		
		randomDrumsOnCompose = new JCheckBox("on compose", true);
		randomDrumSlide = new JCheckBox("Random slide", true);
		randomDrumPattern = new JCheckBox("Include presets", true);
		velocityPatternChance = new JTextField("100", 3);
		rotationChance = new JTextField("25", 3);
		
		p110.add(addDrums);
		p110.add(drumInst);
		p110.add(drumAddJButton);
		p110.add(clearPatternSeeds);
		p110.add(randomizeDrums);
		p110.add(randomDrumsCount);
		p110.add(randomDrumsOnCompose);
		p110.add(randomDrumSlide);
		p110.add(randomDrumPattern);
		p110.add(new JLabel("Velocity pattern%:"));
		p110.add(velocityPatternChance);
		p110.add(new JLabel("Rotation chance%:"));
		p110.add(rotationChance);
		
		
		constraints.gridx = 0;
		constraints.gridy = 50;
		constraints.gridwidth = 3;
		constraints.gridheight = 1;
		//add(p6, constraints);
		constraints.gridy = 50;
		add(p50, constraints);
		constraints.gridy = 60;
		add(p60, constraints);
		constraints.gridy = 70;
		add(p70, constraints);
		constraints.gridy = 80;
		add(p80, constraints);
		constraints.gridy = 90;
		add(p90, constraints);
		constraints.gridy = 100;
		add(p100, constraints);
		constraints.gridy = 110;
		add(p110, constraints);
		
		// ---- DRUMS ----
		// gridy 200 - 300 range
		
		if (addDrums.isSelected()) {
			createRandomDrumPanels(4);
		}
		
		
		// ---- BPM/TRANS ----
		
		JPanel p650 = new JPanel();
		
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
		
		p650.add(randomizeInstruments);
		p650.add(randomizeInstOnCompose);
		p650.add(randomizeBpmTransp);
		p650.add(randomizeBmpTransOnCompose);
		
		constraints.gridy = 650;
		add(p650, constraints);
		
		
		// ------------------- SETTINGS --------------------------------------
		
		JPanel p750 = new JPanel();
		maxJump = new JTextField("4", 2);
		maxExceptions = new JTextField("1", 2);
		p750.add(new JLabel("Max Note Jump:"));
		p750.add(maxJump);
		p750.add(new JLabel("Max Exceptions:"));
		p750.add(maxExceptions);
		randomChordNote = new JCheckBox();
		randomChordNote.setSelected(true);
		melodyFirstNoteFromChord = new JCheckBox();
		melodyFirstNoteFromChord.setSelected(true);
		
		
		p750.add(new JLabel("Note#1 From Chord:"));
		p750.add(melodyFirstNoteFromChord);
		p750.add(new JLabel("But Randomized:"));
		p750.add(randomChordNote);
		
		
		constraints.gridx = 1;
		constraints.gridy = 750;
		constraints.gridwidth = 2;
		constraints.gridheight = 1;
		add(p750, constraints);
		
		JPanel p700 = new JPanel();
		
		transposeScore = new JTextField("0", 3);
		p700.add(new JLabel("Transpose:"));
		p700.add(transposeScore);
		
		Random bpmRand = new Random();
		mainBpm = new JTextField(String.valueOf(bpmRand.nextInt(30) + 70), 3);
		
		arpAffectsBpm = new JCheckBox("Slowed by ARP2", true);
		
		p700.add(new JLabel("BPM:"));
		p700.add(mainBpm);
		p700.add(arpAffectsBpm);
		
		constraints.gridx = 1;
		constraints.gridy = 700;
		constraints.gridwidth = 2;
		constraints.gridheight = 1;
		add(p700, constraints);
		
		// CHORD SETTINGS 1
		
		JPanel chordPanel850 = new JPanel();
		
		
		firstChordSelection = new JComboBox<String>();
		firstChordSelection.addItem("R");
		firstChordSelection.addItem("I");
		firstChordSelection.addItem("V");
		firstChordSelection.addItem("vi");
		firstChordSelection.addItemListener(this);
		chordPanel850.add(new JLabel("First Chord:"));
		chordPanel850.add(firstChordSelection);
		lastChordSelection = new JComboBox<String>();
		lastChordSelection.addItem("R");
		lastChordSelection.addItem("I");
		lastChordSelection.addItem("V");
		lastChordSelection.addItem("vi");
		lastChordSelection.addItemListener(this);
		lastChordSelection.setSelectedIndex(0);
		chordPanel850.add(new JLabel("Last Chord:"));
		chordPanel850.add(lastChordSelection);
		
		userChordsEnabled = new JCheckBox();
		userChordsEnabled.setSelected(false);
		
		
		chordPanel850.add(new JLabel("Custom chords:"));
		chordPanel850.add(userChordsEnabled);
		
		userChords = new JTextField("R", 8);
		chordPanel850.add(new JLabel("Chords:"));
		chordPanel850.add(userChords);
		userChordsDurations = new JTextField("2,2,2,2", 6);
		chordPanel850.add(new JLabel("Chord durations (max. 8):"));
		chordPanel850.add(userChordsDurations);
		constraints.gridx = 1;
		constraints.gridy = 850;
		constraints.gridwidth = 3;
		constraints.gridheight = 1;
		add(chordPanel850, constraints);
		
		// ---- CHORD SETTINGS 2 ----
		
		JPanel p870 = new JPanel();
		chordTransitionChance = new JTextField("25", 3);
		p870.add(new JLabel("Transition chord%"));
		p870.add(chordTransitionChance);
		
		chordSlashChance = new JTextField("25", 3);
		p870.add(new JLabel("Slash chord%"));
		p870.add(chordSlashChance);
		
		spiceChance = new JTextField("8", 3);
		p870.add(new JLabel("Spice%:"));
		p870.add(spiceChance);
		
		spiceAllowDimAug = new JCheckBox("Dim/Aug");
		spiceAllowDimAug.setSelected(false);
		p870.add(spiceAllowDimAug);
		
		JButton randomizeStrums = new JButton("Randomize strums");
		randomizeStrums.addActionListener(this);
		randomizeStrums.setActionCommand("RandStrums");
		p870.add(randomizeStrums);
		
		randomizeChordStrumsOnCompose = new JCheckBox("On compose");
		randomizeChordStrumsOnCompose.setSelected(true);
		p870.add(randomizeChordStrumsOnCompose);
		
		constraints.gridy = 870;
		add(p870, constraints);
		
		// CONTROL PANEL
		
		JPanel controlPanel900 = new JPanel();
		randomSeed = new JTextField("0", 16);
		JButton compose = new JButton("Compose");
		compose.addActionListener(this);
		compose.setActionCommand("Compose");
		JButton stopMidi = new JButton("STOP");
		stopMidi.addActionListener(this);
		stopMidi.setActionCommand("StopMidi");
		JButton startMidi = new JButton("PLAY");
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
		
		JButton regenerate = new JButton("Regenerate");
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
		
		controlPanel900.add(new JLabel("Random Seed:"));
		controlPanel900.add(randomSeed);
		controlPanel900.add(compose);
		controlPanel900.add(regenerate);
		controlPanel900.add(copySeed);
		controlPanel900.add(copyChords);
		controlPanel900.add(clearSeed);
		controlPanel900.add(loadConfig);
		
		constraints.gridx = 0;
		constraints.gridy = 900;
		constraints.gridwidth = 3;
		constraints.gridheight = 1;
		add(controlPanel900, constraints);
		
		JPanel playSavePanel950 = new JPanel();
		playSavePanel950.add(startMidi);
		playSavePanel950.add(stopMidi);
		playSavePanel950.add(save3Star);
		playSavePanel950.add(save4Star);
		playSavePanel950.add(save5Star);
		
		constraints.gridx = 0;
		constraints.gridy = 950;
		constraints.gridwidth = 3;
		constraints.gridheight = 1;
		add(playSavePanel950, constraints);
		
		// --- GENERATED MIDI ---
		
		JPanel p960 = new JPanel();
		
		generatedMidi = new JList<File>();
		generatedMidi.setTransferHandler(new FileTransferHandler());
		generatedMidi.setDragEnabled(true);
		p960.add(new JLabel("Midi Drag'N'Drop:"));
		p960.add(generatedMidi);
		constraints.gridy = 960;
		add(p960, constraints);
		
		JPanel messagePanel999 = new JPanel();
		messageLabel = new JLabel("Click something!");
		messageLabel.setForeground((isDarkMode) ? Color.CYAN : Color.BLUE);
		messageLabel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		messagePanel999.add(messageLabel);
		constraints.gridy = 999;
		add(messagePanel999, constraints);
		
		Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
		int screenHeight = d.height;
		int screenWidth = d.width;
		setSize(screenWidth / 2, screenHeight / 2);
		setLocation(screenWidth / 4, screenHeight / 4);
		pack();
		setVisible(true);
		repaint();
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
		if (melodyLock.isSelected()) {
			arpMelodyLockInst.setSelected(false);
		}
		if (arp2Lock.isSelected()) {
			arp2LockInst.setSelected(false);
		}
		
		
		if (ae.getActionCommand() == "Compose" && secondArpMultiplierRandom.isSelected()) {
			Random instGen = new Random();
			secondArpMultiplier.setSelectedIndex(instGen.nextInt(4));
		}
		
		if (ae.getActionCommand() == "RandStrums" || (ae.getActionCommand() == "Compose"
				& randomizeChordStrumsOnCompose.isSelected())) {
			Random strumsGen = new Random();
			double strumStart = 1000.0;
			double strum1 = strumStart / CHORD_STRUM_DIVISION_ARRAY[strumsGen
					.nextInt(CHORD_STRUM_DIVISION_ARRAY.length)];
			double strum2 = strum1 * SECOND_CHORD_STRUM_MULTIPLIER[strumsGen
					.nextInt(SECOND_CHORD_STRUM_MULTIPLIER.length)];
			chordStrum.setText("" + (int) Math.floor(strum1));
			secondChordStrum.setText("" + (int) Math.floor(strum2));
		}
		
		
		if (ae.getActionCommand() == "RandDrums" || (ae.getActionCommand() == "Compose"
				&& addDrums.isSelected() && randomDrumsOnCompose.isSelected())) {
			createRandomDrumPanels(Integer.valueOf(randomDrumsCount.getText()));
		}
		
		if (ae.getActionCommand() == "RandomizeInst"
				|| (ae.getActionCommand() == "Compose" && randomizeInstOnCompose.isSelected())) {
			Random instGen = new Random();
			if (!melodyLock.isSelected()) {
				melodyInst.setSelectedIndex(
						instGen.nextInt(MelodyGenerator.PLUCKY_INST_NAMES.length));
			}
			
			if (!chords1Lock.isSelected()) {
				
				chords1Inst
						.setSelectedIndex(instGen.nextInt(MelodyGenerator.CHORD_INST_NAMES.length));
			}
			if (!chords2Lock.isSelected()) {
				
				chords2Inst.setSelectedIndex(
						instGen.nextInt(MelodyGenerator.PLUCKY_INST_NAMES.length));
			}
			if (!arp1Lock.isSelected()) {
				
				arp1Inst.setSelectedIndex(
						instGen.nextInt(MelodyGenerator.PLUCKY_INST_NAMES.length));
			}
			if (!arp2Lock.isSelected()) {
				
				arp2Inst.setSelectedIndex(
						instGen.nextInt(MelodyGenerator.PLUCKY_INST_NAMES.length));
			}
			if (!bassRootsLock.isSelected()) {
				
				bassRootsInst
						.setSelectedIndex(instGen.nextInt(MelodyGenerator.BASS_INST_NAMES.length));
			}
			System.out.println("RANDOMIZED INSTS!");
		}
		
		if (ae.getActionCommand() == "RandomizeBpmTrans" || (ae.getActionCommand() == "Compose"
				&& randomizeBmpTransOnCompose.isSelected())) {
			Random instGen = new Random();
			
			mainBpm.setText(String.valueOf(instGen.nextInt(30) + 70));
			transposeScore.setText(String.valueOf(instGen.nextInt(12) - 6));
			
			System.out.println("RANDOMIZED BPM/Transpose!");
		}
		
		
		// midi generation
		if (ae.getActionCommand() == "Compose" || ae.getActionCommand() == "Regenerate") {
			Integer masterpieceSeed = 0;
			
			Integer parsedSeed = (NumberUtils.isCreatable(randomSeed.getText()))
					? Integer.valueOf(randomSeed.getText())
					: 0;
			
			if (ae.getActionCommand() == "Regenerate") {
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
			} else if ((!StringUtils.isEmpty(randomSeed.getText())
					&& !"0".equals(randomSeed.getText())
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
			
			arpCount.setSelectedIndex(MelodyGenerator.ARPS_PER_CHORD - 1);
			
			
			try (FileWriter fw = new FileWriter("randomSeedHistory.txt", true);
					BufferedWriter bw = new BufferedWriter(fw);
					PrintWriter out = new PrintWriter(bw)) {
				out.println(new Date().toString() + ", Seed: " + seedData);
			} catch (IOException e) {
				//exception handling left as an exercise for the reader
				System.out.println(
						"Yikers! An exception while writing a single line at the end of a .txt file!");
			}
			
			try {
				if (sequencer != null) {
					sequencer.stop();
				}
				boolean fileFound = false;
				Synthesizer synthesizer = null;
				try {
					
					
					File soundbankFile = new File(soundbankFilename.getText());
					if (soundbankFile.isFile()) {
						if (synth == null) {
							
							Soundbank soundfont = MidiSystem.getSoundbank(
									new BufferedInputStream(new FileInputStream(soundbankFile)));
							synthesizer = MidiSystem.getSynthesizer();
							
							synthesizer.isSoundbankSupported(soundfont);
							synthesizer.open();
							synthesizer.loadAllInstruments(soundfont);
							fileFound = true;
						}
						System.out
								.println("Playing using soundbank: " + soundbankFilename.getText());
					} else {
						fileFound = false;
						synth = null;
						System.out.println("NO SOUNDBANK WITH THAT NAME FOUND!");
					}
					
					
				} catch (FileNotFoundException ex) {
					fileFound = false;
					synth = null;
					System.out.println("NO SOUNDBANK WITH THAT NAME FOUND!");
				}
				
				if (sequencer != null) {
					// do nothing
				} else {
					sequencer = MidiSystem.getSequencer(!fileFound); // Get the default Sequencer
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
				
				if (synth != null) {
					// already opened correctly, do nothing
					//saveWavFile("wavtest2.wav", synth);
				} else if (fileFound && (synthesizer != null)) {
					for (Transmitter tm : sequencer.getTransmitters()) {
						tm.close();
					}
					sequencer.getTransmitter().setReceiver(synthesizer.getReceiver());
					//saveWavFile("wavtest2.wav", synthesizer);
					//synthesizer.open();
					synth = synthesizer;
				} else {
					for (Transmitter tm : sequencer.getTransmitters()) {
						tm.close();
					}
					Synthesizer defSynth = MidiSystem.getSynthesizer();
					defSynth.open();
					sequencer.getTransmitter().setReceiver(defSynth.getReceiver());
					//saveWavFile("wavtest2.wav", defSynth);
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
		
		if (ae.getActionCommand() == "ClearPatterns") {
			for (DrumPanel dp : drumPanels) {
				dp.setPatternSeed(0);
				if (dp.getPattern() != DrumPattern.RANDOM) {
					dp.setPattern(DrumPattern.RANDOM);
					dp.setPauseChance(3 * dp.getPauseChance());
				}
				
			}
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
		
		if (ae.getActionCommand() == "AddDrum") {
			addDrumPanelToLayout();
			pack();
			repaint();
		}
		
		if (ae.getActionCommand().startsWith("RemoveDrum,")) {
			String drumNumber = ae.getActionCommand().split(",")[1];
			removeDrumPanel(Integer.valueOf(drumNumber), true);
		}
		
		if (ae.getActionCommand() == "SwitchDarkMode") {
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
			pack();
			setVisible(true);
			repaint();
		}
		
		System.out.println("::" + ae.getActionCommand() + "::");
		messageLabel.setText("::" + ae.getActionCommand() + "::");
	}
	
	public void fillUserParameters() {
		try {
			MelodyGenerator.MAX_JUMP = Integer.valueOf(maxJump.getText());
			MelodyGenerator.MAX_EXCEPTIONS = Integer.valueOf(maxExceptions.getText());
			MelodyGenerator.MELODY_PAUSE_CHANCE = Integer.valueOf(melodyPauseChance.getText());
			MelodyGenerator.SPICE_CHANCE = Integer.valueOf(spiceChance.getText());
			MelodyGenerator.SPICE_ALLOW_DIM_AUG = spiceAllowDimAug.isSelected();
			MelodyGenerator.PIECE_LENGTH = Integer.valueOf(pieceLength.getText());
			MelodyGenerator.RANDOM_CHORD_NOTE = randomChordNote.isSelected();
			MelodyGenerator.FIXED_LENGTH = fixedLengthChords.isSelected();
			MelodyGenerator.TRANSPOSE_SCORE = Integer.valueOf(transposeScore.getText());
			MelodyGenerator.MINOR_SONG = minorScale.isSelected();
			MelodyGenerator.MAIN_BPM = Double.valueOf(mainBpm.getText());
			MelodyGenerator.FIRST_CHORD = chordSelect(
					(String) firstChordSelection.getSelectedItem());
			MelodyGenerator.LAST_CHORD = chordSelect((String) lastChordSelection.getSelectedItem());
			MelodyGenerator.USER_MELODY_SEED = Integer
					.valueOf((StringUtils.isEmpty(userMelodySeed.getText())) ? "0"
							: userMelodySeed.getText());
			
			MelodyGenerator.CHORD_STRUM = Integer.valueOf(chordStrum.getText());
			MelodyGenerator.SECOND_CHORD_STRUM = Integer.valueOf(secondChordStrum.getText());
			MelodyGenerator.CHORD_TRANSITION_CHANCE = Integer
					.valueOf(chordTransitionChance.getText());
			MelodyGenerator.CHORD_SLASH_CHANCE = Integer.valueOf(chordSlashChance.getText());
			
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
			
			MelodyGenerator.SECOND_ARP_COUNT_MULTIPLIER = secondArpMultiplier.getSelectedIndex()
					+ 1;
			MelodyGenerator.SECOND_ARP_OCTAVE_ADJUST = Integer
					.valueOf((String) secondArpOctaveAdjust.getSelectedItem());
			if (arpAffectsBpm.isSelected() && MelodyGenerator.SECOND_ARP_COUNT_MULTIPLIER > 1) {
				MelodyGenerator.MAIN_BPM *= (2.0
						/ (MelodyGenerator.SECOND_ARP_COUNT_MULTIPLIER + 1));
				// mainBpm.setText(String.valueOf(MelodyGenerator.MAIN_BPM));
			}
			
			
			MelodyGenerator.ARPS_PER_CHORD = arpCount.getSelectedIndex() + 1;
			MelodyGenerator.ARP_RANDOM_SHUFFLE = randomArpPattern.isSelected();
			MelodyGenerator.RANDOM_ARPS_PER_CHORD = randomArpCount.isSelected();
			MelodyGenerator.ARP_PATTERN_REPEAT = arpPatternRepeat.isSelected();
			MelodyGenerator.ARP_ALLOW_PAUSES = arpAllowPauses.isSelected();
			MelodyGenerator.FIRST_NOTE_FROM_CHORD = melodyFirstNoteFromChord.isSelected();
			MelodyGenerator.ARP_PAUSE_CHANCE = Integer.valueOf(pauseChance.getText());
			MelodyGenerator.SECOND_ARP_PAUSE_CHANCE = Integer
					.valueOf(secondArpPauseChance.getText());
			
			MelodyGenerator.PARTS_INSTRUMENT_MAP.clear();
			
			if (arpMelodyLockInst.isSelected()) {
				melodyInst.setSelectedIndex(arp1Inst.getSelectedIndex());
			}
			
			if (arp2LockInst.isSelected()) {
				arp2Inst.setSelectedIndex(arp1Inst.getSelectedIndex());
			}
			
			if (addMelody.isSelected())
				MelodyGenerator.PARTS_INSTRUMENT_MAP.put(PARTS.MELODY, getInstByIndex(
						melodyInst.getSelectedIndex(), MelodyGenerator.PLUCKY_INST_NAMES));
			if (addChords1.isSelected())
				MelodyGenerator.PARTS_INSTRUMENT_MAP.put(PARTS.CHORDS1, getInstByIndex(
						chords1Inst.getSelectedIndex(), MelodyGenerator.CHORD_INST_NAMES));
			if (addChords2.isSelected())
				MelodyGenerator.PARTS_INSTRUMENT_MAP.put(PARTS.CHORDS2, getInstByIndex(
						chords2Inst.getSelectedIndex(), MelodyGenerator.PLUCKY_INST_NAMES));
			if (addArp1.isSelected())
				MelodyGenerator.PARTS_INSTRUMENT_MAP.put(PARTS.ARP1, getInstByIndex(
						arp1Inst.getSelectedIndex(), MelodyGenerator.PLUCKY_INST_NAMES));
			if (addArp2.isSelected())
				MelodyGenerator.PARTS_INSTRUMENT_MAP.put(PARTS.ARP2, getInstByIndex(
						arp2Inst.getSelectedIndex(), MelodyGenerator.PLUCKY_INST_NAMES));
			if (addBassRoots.isSelected())
				MelodyGenerator.PARTS_INSTRUMENT_MAP.put(PARTS.BASSROOTS, getInstByIndex(
						bassRootsInst.getSelectedIndex(), MelodyGenerator.BASS_INST_NAMES));
			if (addDrums.isSelected()) {
				MelodyGenerator.PARTS_INSTRUMENT_MAP.put(PARTS.DRUMS, 0);
				MelodyGenerator.DRUM_PARTS = getDrumPartsFromDrumPanels();
			}
			
		} catch (Exception e) {
			System.out.println("User screwed up his inputs!" + e);
		}
		
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
	
	private static void addAllToJComboBox(String[] choices, JComboBox<String> choice) {
		for (String c : choices) {
			choice.addItem(c);
		}
	}
	
	
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
			
			// Play Sequence into AudioSynthesizer Receiver.
			double totalLength = this.sendOutputSequenceMidiEvents(synth.getReceiver());
			
			// give it an extra 2 seconds, to the reverb to fade out--otherwise it sounds unnatural
			totalLength += 2;
			// Calculate how long the WAVE file needs to be.
			long len = (long) (stream1.getFormat().getFrameRate() * totalLength);
			stream2 = new AudioInputStream(stream1, stream1.getFormat(), len);
			
			
			// Write the wave file to disk
			AudioSystem.write(stream2, AudioFileFormat.Type.WAVE, new File("wavtest1.wav"));
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
		
		guiConfig.setSecondArpMultiplier(secondArpMultiplier.getSelectedIndex() + 1);
		guiConfig.setSecondArpOctaveAdjust(
				Integer.valueOf((String) secondArpOctaveAdjust.getSelectedItem()));
		guiConfig.setSecondArpPauseChance(Integer.valueOf(secondArpPauseChance.getText()));
		
		guiConfig.setArpCustomCount(arpCount.getSelectedIndex() + 1);
		guiConfig.setArpRandomPattern(randomArpPattern.isSelected());
		guiConfig.setArpRandomCount(randomArpCount.isSelected());
		guiConfig.setArpRandomRepeats(arpPatternRepeat.isSelected());
		guiConfig.setArpRandomPauses(arpAllowPauses.isSelected());
		
		guiConfig.setMelodyEnable(addMelody.isSelected());
		guiConfig.setChords1Enable(addChords1.isSelected());
		guiConfig.setChords2Enable(addChords2.isSelected());
		guiConfig.setArp1ArpEnable(addArp1.isSelected());
		guiConfig.setArp2ArpEnable(addArp2.isSelected());
		guiConfig.setBassRootsEnable(addBassRoots.isSelected());
		
		guiConfig.setDrumsEnable(addDrums.isSelected());
		guiConfig.setDrumParts(getDrumPartsFromDrumPanels());
		
		guiConfig.setMelodyInst(
				getInstByIndex(melodyInst.getSelectedIndex(), MelodyGenerator.PLUCKY_INST_NAMES));
		guiConfig.setChords1Inst(
				getInstByIndex(chords1Inst.getSelectedIndex(), MelodyGenerator.CHORD_INST_NAMES));
		guiConfig.setChords2Inst(
				getInstByIndex(chords2Inst.getSelectedIndex(), MelodyGenerator.PLUCKY_INST_NAMES));
		guiConfig.setArp1ArpInst(
				getInstByIndex(arp1Inst.getSelectedIndex(), MelodyGenerator.PLUCKY_INST_NAMES));
		guiConfig.setArp2ArpInst(
				getInstByIndex(arp2Inst.getSelectedIndex(), MelodyGenerator.PLUCKY_INST_NAMES));
		guiConfig.setBassRootsInst(
				getInstByIndex(bassRootsInst.getSelectedIndex(), MelodyGenerator.BASS_INST_NAMES));
		
		guiConfig.setUserMelodySeed(!StringUtils.isEmpty(userMelodySeed.getText())
				? Long.valueOf(userMelodySeed.getText())
				: 0);
		
		guiConfig.setMaxNoteJump(Integer.valueOf(maxJump.getText()));
		guiConfig.setMaxExceptions(Integer.valueOf(maxExceptions.getText()));
		guiConfig.setPauseChance(Integer.valueOf(pauseChance.getText()));
		guiConfig.setSecondArpPauseChance(Integer.valueOf(secondArpPauseChance.getText()));
		guiConfig.setMelodyPauseChance(Integer.valueOf(melodyPauseChance.getText()));
		guiConfig.setSpiceChance(Integer.valueOf(spiceChance.getText()));
		guiConfig.setDimAugEnabled(spiceAllowDimAug.isSelected());
		guiConfig.setChordTransitionChance(Integer.valueOf(chordTransitionChance.getText()));
		guiConfig.setChordSlashChance(Integer.valueOf(chordSlashChance.getText()));
		guiConfig.setChordStrum(Integer.valueOf(chordStrum.getText()));
		guiConfig.setSecondChordStrum(Integer.valueOf(secondChordStrum.getText()));
		
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
		
		secondArpMultiplier.setSelectedIndex(guiConfig.getSecondArpMultiplier() - 1);
		secondArpOctaveAdjust.setSelectedItem(String.valueOf(guiConfig.getSecondArpOctaveAdjust()));
		secondArpPauseChance.setText(String.valueOf(guiConfig.getSecondArpPauseChance()));
		
		arpCount.setSelectedIndex(guiConfig.getArpCustomCount() - 1);
		randomArpPattern.setSelected(guiConfig.isArpRandomPattern());
		randomArpCount.setSelected(guiConfig.isArpRandomCount());
		arpPatternRepeat.setSelected(guiConfig.isArpRandomRepeats());
		arpAllowPauses.setSelected(guiConfig.isArpRandomPauses());
		
		addMelody.setSelected(guiConfig.isMelodyEnable());
		addChords1.setSelected(guiConfig.isChords1Enable());
		addChords2.setSelected(guiConfig.isChords2Enable());
		addArp1.setSelected(guiConfig.isArp1ArpEnable());
		addArp2.setSelected(guiConfig.isArp2ArpEnable());
		addBassRoots.setSelected(guiConfig.isBassRootsEnable());
		
		addDrums.setSelected(guiConfig.isDrumsEnable());
		recreateDrumPanelsFromDrumParts(guiConfig.getDrumParts());
		
		selectJComboBoxByInst(melodyInst, MelodyGenerator.PLUCKY_INST_NAMES,
				guiConfig.getMelodyInst());
		
		selectJComboBoxByInst(chords1Inst, MelodyGenerator.CHORD_INST_NAMES,
				guiConfig.getChords1Inst());
		
		selectJComboBoxByInst(chords2Inst, MelodyGenerator.PLUCKY_INST_NAMES,
				guiConfig.getChords2Inst());
		
		selectJComboBoxByInst(arp1Inst, MelodyGenerator.PLUCKY_INST_NAMES,
				guiConfig.getArp1ArpInst());
		
		selectJComboBoxByInst(arp2Inst, MelodyGenerator.PLUCKY_INST_NAMES,
				guiConfig.getArp2ArpInst());
		
		selectJComboBoxByInst(bassRootsInst, MelodyGenerator.BASS_INST_NAMES,
				guiConfig.getBassRootsInst());
		
		userMelodySeed.setText(String.valueOf(guiConfig.getUserMelodySeed()));
		
		maxJump.setText(String.valueOf(guiConfig.getMaxNoteJump()));
		maxExceptions.setText(String.valueOf(guiConfig.getMaxExceptions()));
		pauseChance.setText(String.valueOf(guiConfig.getPauseChance()));
		secondArpPauseChance.setText(String.valueOf(guiConfig.getSecondArpPauseChance()));
		melodyPauseChance.setText(String.valueOf(guiConfig.getMelodyPauseChance()));
		spiceChance.setText(String.valueOf(guiConfig.getSpiceChance()));
		spiceAllowDimAug.setSelected(guiConfig.isDimAugEnabled());
		chordTransitionChance.setText(String.valueOf(guiConfig.getChordTransitionChance()));
		chordSlashChance.setText(String.valueOf(guiConfig.getChordSlashChance()));
		chordStrum.setText(String.valueOf(guiConfig.getChordStrum()));
		secondChordStrum.setText(String.valueOf(guiConfig.getSecondChordStrum()));
		
		firstChordSelection.setSelectedItem(guiConfig.getFirstChord());
		lastChordSelection.setSelectedItem(guiConfig.getLastChord());
		userChordsEnabled.setSelected(guiConfig.isCustomChordsEnabled());
		userChords.setText(guiConfig.getCustomChords());
		userChordsDurations.setText(guiConfig.getCustomChordDurations());
		
		melodyFirstNoteFromChord.setSelected(guiConfig.isFirstNoteFromChord());
		randomChordNote.setSelected(guiConfig.isFirstNoteRandomized());
	}
	
	public static void selectJComboBoxByInst(JComboBox<String> choice, String[] instPool,
			Integer number) {
		List<Integer> instPoolNumbers = MelodyGenerator.getInstNumbers(instPool);
		int index = instPoolNumbers.indexOf(number);
		choice.setSelectedIndex(index);
	}
	
	public static Integer getInstByIndex(int index, String[] instPool) {
		List<Integer> instPoolNumbers = MelodyGenerator.getInstNumbers(instPool);
		return instPoolNumbers.get(index);
	}
	
	public static void selectDrumJComboBoxByInst(JComboBox<String> choice, Integer number) {
		int index = MelodyGenerator.DRUM_NAMES_NUMBERS.indexOf(number);
		choice.setSelectedIndex(index);
	}
	
	public static Integer getDrumInstByIndex(int index) {
		return MelodyGenerator.DRUM_NAMES_NUMBERS.get(index);
	}
	
	public DrumPanel addDrumPanelToLayout() {
		GridBagConstraints constraints = new GridBagConstraints();
		
		constraints.weightx = 100;
		constraints.weighty = 100;
		constraints.gridx = 1;
		constraints.gridy = 200 + drumPanels.size();
		constraints.gridwidth = 2;
		constraints.gridheight = 1;
		constraints.anchor = GridBagConstraints.WEST;
		
		DrumPanel drumJPanel = new DrumPanel(this);
		drumJPanel.setDrumPanelOrder(drumPanels.size());
		drumJPanel.initComponents();
		drumJPanel.setPitch(getDrumInstByIndex(drumInst.getSelectedIndex()));
		drumPanels.add(drumJPanel);
		add(drumJPanel, constraints);
		return drumJPanel;
	}
	
	private void removeDrumPanel(int index, boolean singleRemove) {
		DrumPanel panel = drumPanels.get(index);
		remove(panel);
		drumPanels.remove(panel);
		
		if (singleRemove) {
			reorderDrumPanels();
			pack();
			repaint();
		}
	}
	
	private List<DrumPart> getDrumPartsFromDrumPanels() {
		List<DrumPart> parts = new ArrayList<>();
		for (DrumPanel p : drumPanels) {
			parts.add(p.toDrumPart(lastRandomSeed));
		}
		return parts;
	}
	
	private void recreateDrumPanelsFromDrumParts(List<DrumPart> parts) {
		for (DrumPanel panel : drumPanels) {
			remove(panel);
		}
		drumPanels.clear();
		int counter = 0;
		for (DrumPart part : parts) {
			DrumPanel panel = addDrumPanelToLayout();
			panel.setFromDrumPart(part);
			panel.setDrumPanelOrder(counter++);
		}
		
		pack();
		repaint();
	}
	
	private void reorderDrumPanels() {
		for (int i = 0; i < drumPanels.size(); i++) {
			DrumPanel p = drumPanels.get(i);
			p.setDrumPanelOrder(i);
		}
	}
	
	private void createRandomDrumPanels(int panelCount) {
		Random drumPanelGenerator = new Random();
		for (DrumPanel panel : drumPanels) {
			remove(panel);
		}
		drumPanels.clear();
		
		int slide = 0;
		
		if (randomDrumSlide.isSelected()) {
			slide = drumPanelGenerator.nextInt(300) - 150;
		}
		
		for (int i = 0; i < panelCount; i++) {
			DrumPanel dp = addDrumPanelToLayout();
			dp.setPitch(getDrumInstByIndex(
					drumPanelGenerator.nextInt(MelodyGenerator.DRUM_NAMES.length)));
			//dp.setPitch(32 + drumPanelGenerator.nextInt(33));
			
			
			dp.setChordSpan(drumPanelGenerator.nextInt(2) + 1);
			int patternOrder = 0;
			if (randomDrumPattern.isSelected()) {
				patternOrder = drumPanelGenerator.nextInt(DrumPattern.values().length);
			}
			int hits = 4;
			while (drumPanelGenerator.nextInt(10) < 5 && hits < 32) {
				hits *= 2;
			}
			if ((hits / dp.getChordSpan() >= 16)) {
				hits /= 2;
			}
			
			dp.setHitsPerPattern(hits);
			
			int adjustVelocity = (dp.getHitsPerPattern() / 2) / dp.getChordSpan();
			
			dp.setPattern(DrumPattern.values()[patternOrder]);
			int velocityMin = drumPanelGenerator.nextInt(50 - adjustVelocity) + 20;
			dp.setVelocityMin(velocityMin);
			dp.setVelocityMax(1 + velocityMin + drumPanelGenerator.nextInt(40 - adjustVelocity));
			
			if (patternOrder > 0) {
				dp.setPauseChance(drumPanelGenerator.nextInt(5) + 0);
			} else {
				dp.setPauseChance(drumPanelGenerator.nextInt(40) + 40);
			}
			dp.setExceptionChance(drumPanelGenerator.nextInt(15));
			
			if (dp.getPitch() > 40) {
				dp.setSlideMiliseconds(slide);
			}
			
			dp.setIsVelocityPattern(drumPanelGenerator.nextInt(100) < Integer
					.valueOf(velocityPatternChance.getText()));
			
			if (drumPanelGenerator.nextInt(100) < Integer.valueOf(rotationChance.getText())
					&& patternOrder > 0) {
				dp.setPatternRotation(
						drumPanelGenerator.nextInt(dp.getPattern().pattern.length - 1) + 1);
			}
			
		}
		
		pack();
		repaint();
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
}
