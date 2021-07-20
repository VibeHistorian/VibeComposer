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
along with this program; if not, write to the Free Software
Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/

package org.vibehistorian.vibecomposer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.MouseInfo;
import java.awt.Point;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
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
import javax.swing.JSlider;
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
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.vibehistorian.vibecomposer.MidiUtils.POOL;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Helpers.CheckBoxIcon;
import org.vibehistorian.vibecomposer.Helpers.FileTransferable;
import org.vibehistorian.vibecomposer.Helpers.MelodyMidiDropPane;
import org.vibehistorian.vibecomposer.Helpers.RandomValueButton;
import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;
import org.vibehistorian.vibecomposer.Panels.ArpPanel;
import org.vibehistorian.vibecomposer.Panels.BassPanel;
import org.vibehistorian.vibecomposer.Panels.ChordGenSettings;
import org.vibehistorian.vibecomposer.Panels.ChordPanel;
import org.vibehistorian.vibecomposer.Panels.DrumPanel;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;
import org.vibehistorian.vibecomposer.Panels.MelodyPanel;
import org.vibehistorian.vibecomposer.Panels.SettingsPanel;
import org.vibehistorian.vibecomposer.Panels.SoloMuter;
import org.vibehistorian.vibecomposer.Panels.SoloMuter.State;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.DrumPartsWrapper;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.Parts.Defaults.DrumDefaults;
import org.vibehistorian.vibecomposer.Parts.Defaults.DrumSettings;
import org.vibehistorian.vibecomposer.Popups.AboutPopup;
import org.vibehistorian.vibecomposer.Popups.DebugConsole;
import org.vibehistorian.vibecomposer.Popups.ExtraSettingsPopup;
import org.vibehistorian.vibecomposer.Popups.HelpPopup;
import org.vibehistorian.vibecomposer.Popups.VariationPopup;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.sun.media.sound.AudioSynthesizer;

// main class

public class VibeComposerGUI extends JFrame
		implements ActionListener, ItemListener, WindowListener {

	private static final long serialVersionUID = -677536546851756969L;

	private static final String SOUNDBANK_DEFAULT = "MuseScore_General.sf2";
	private static final String MIDIS_FOLDER = "midis";

	public static final int[] MILISECOND_ARRAY_STRUM = { 0, 16, 31, 62, 62, 125, 166, 250, 333, 500,
			750, 1000 };
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
	int arrangementDarkModeLowestColor = 100;
	Color arrangementDarkModeText = new Color(50, 50, 50);
	int arrangementLightModeHighestColor = 180;

	private static Set<Component> toggleableComponents = new HashSet<>();

	private static List<JSeparator> separators = new ArrayList<>();

	private static Soundbank soundfont = null;

	private Synthesizer synth = null;
	private boolean isSoundbankSynth = false;

	private GUIConfig guiConfig = new GUIConfig();

	// instrument individual panels
	public static BassPanel bassPanel;

	// instrument panels added into scrollpanes
	public static List<MelodyPanel> melodyPanels = new ArrayList<>();
	public static List<BassPanel> bassPanels = new ArrayList<>();
	public static List<ChordPanel> chordPanels = new ArrayList<>();
	public static List<ArpPanel> arpPanels = new ArrayList<>();
	public static List<DrumPanel> drumPanels = new ArrayList<>();

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

	// arrangement
	public static Arrangement arrangement;
	public static Arrangement actualArrangement;
	KnobPanel arrangementVariationChance;
	KnobPanel arrangementPartVariationChance;
	JCheckBox arrangementManualOverride;
	JTextField pieceLength;
	RandomValueButton arrangementSeed;

	// instrument scrollers
	JTabbedPane instrumentTabPane = new JTabbedPane(JTabbedPane.TOP);
	Dimension scrollPaneDimension = new Dimension(1600, 400);
	int arrangementRowHeaderWidth = 120;
	Dimension scrollPaneDimensionToggled = new Dimension(1000, 400);

	public static JScrollPane melodyScrollPane;
	public static JScrollPane bassScrollPane;
	public static JScrollPane chordScrollPane;
	public static JScrollPane arpScrollPane;
	public static JScrollPane drumScrollPane;
	JScrollPane arrangementScrollPane;
	JScrollPane arrangementActualScrollPane;
	public static JTable scrollableArrangementTable;
	public static JTable scrollableArrangementActualTable;

	JPanel actualArrangementCombinedPanel;
	JPanel arrangementCombinedPanel;
	JPanel variationButtonsPanel;

	// instrument global settings
	JTextField bannedInsts;
	JCheckBox useAllInsts;
	JButton reinitInstPools;

	// main title settings
	JLabel mainTitle;
	JLabel subTitle;
	JButton switchDarkMode;


	// macro params
	JTextField soundbankFilename;

	public static ScrollComboBox<String> scaleMode;
	ScrollComboBox<String> fixedLengthChords;
	JCheckBox useDoubledDurations;
	JCheckBox allowChordRepeats;
	JCheckBox useArrangement;
	JCheckBox randomizeArrangementOnCompose;
	JCheckBox globalSwingOverride;
	KnobPanel globalSwingOverrideValue;
	KnobPanel loopBeatCount;


	// chord variety settings
	KnobPanel spiceChance;
	KnobPanel chordSlashChance;
	JCheckBox spiceAllowDimAug;
	JCheckBox spiceAllow9th13th;
	JCheckBox spiceFlattenBigChords;


	// add/skip instruments
	SettingsPanel chordSettingsPanel;
	SettingsPanel arpSettingsPanel;
	SettingsPanel drumSettingsPanel;
	JCheckBox addChords;
	JCheckBox addArps;
	JCheckBox addDrums;

	JButton soloAllDrums;

	// melody gen settings
	JCheckBox arpCopyMelodyInst;
	KnobPanel maxJump;
	KnobPanel maxExceptions;
	KnobPanel melodyAlternateRhythmChance;
	KnobPanel melodySameRhythmChance;
	KnobPanel melodyUseOldAlgoChance;
	JCheckBox randomMelodyOnRegenerate;
	JCheckBox randomMelodySameSeed;
	JCheckBox melodyFirstNoteFromChord;
	JCheckBox randomChordNote;
	KnobPanel melodySplitChance;
	KnobPanel melodyExceptionChance;
	KnobPanel melodyQuickness;
	JCheckBox melodyBasicChordsOnly;
	JCheckBox useUserMelody;

	// bass gen settings
	// - there's nothing here - 

	// chord gen settings
	JTextField randomChordsToGenerate;
	JCheckBox randomChordsGenerateOnCompose;
	JCheckBox randomChordDelay;
	JCheckBox randomChordStrum;
	KnobPanel randomChordStruminess;
	JCheckBox randomChordSplit;
	JCheckBox randomChordTranspose;
	JCheckBox randomChordPattern;
	JCheckBox randomChordSustainUseShortening;
	KnobPanel randomChordSustainChance;
	KnobPanel randomChordShiftChance;
	KnobPanel randomChordVoicingChance;
	KnobPanel randomChordMaxSplitChance;
	JCheckBox randomChordUseChordFill;
	ScrollComboBox<String> randomChordStretchType;
	ScrollComboBox<String> randomChordStretchPicker;
	KnobPanel randomChordMinVel;
	KnobPanel randomChordMaxVel;

	// arp gen settings
	JTextField randomArpsToGenerate;
	JCheckBox randomArpsGenerateOnCompose;
	JCheckBox randomArpTranspose;
	JCheckBox randomArpPattern;
	JCheckBox randomArpHitsPerPattern;
	JCheckBox randomArpAllSameInst;
	JCheckBox randomArpAllSameHits;
	JCheckBox randomArpLimitPowerOfTwo;
	KnobPanel arpShiftChance;
	ScrollComboBox<String> randomArpHitsPicker;
	JCheckBox randomArpUseChordFill;
	ScrollComboBox<String> randomArpStretchType;
	ScrollComboBox<String> randomArpStretchPicker;
	JCheckBox randomArpUseOctaveAdjustments;
	KnobPanel randomArpMaxSwing;
	KnobPanel randomArpMaxRepeat;
	KnobPanel randomArpMinVel;
	KnobPanel randomArpMaxVel;

	// drum gen settings
	public static List<Integer> PUNCHY_DRUMS = Arrays
			.asList(new Integer[] { 35, 36, 37, 38, 39, 40 });
	JTextField randomDrumsToGenerate;
	JCheckBox randomDrumsGenerateOnCompose;
	JCheckBox randomDrumsOverrandomize;
	JTextField randomDrumMaxSwingAdjust;
	JCheckBox randomDrumSlide;
	JCheckBox randomDrumPattern;
	KnobPanel randomDrumVelocityPatternChance;
	KnobPanel randomDrumShiftChance;
	JCheckBox randomDrumUseChordFill;
	JCheckBox arrangementAffectsDrumVelocity;
	ScrollComboBox<String> randomDrumHitsMultiplier;
	int randomDrumHitsMultiplierLastState = 1;
	public static JCheckBox drumCustomMapping;
	public static JTextField drumCustomMappingNumbers;

	// chord settings - progression
	ScrollComboBox<String> firstChordSelection;
	ScrollComboBox<String> lastChordSelection;
	int firstChord = 0;
	int lastChord = 0;
	JCheckBox userChordsEnabled;
	JTextField userChords;
	JTextField userChordsDurations;

	// randomization button settings
	JCheckBox randomizeInstOnComposeOrGen;
	JCheckBox randomizeBpmOnCompose;
	JCheckBox randomizeTransposeOnCompose;
	JCheckBox randomizeChordStrumsOnCompose;
	JCheckBox arpAffectsBpm;
	public static KnobPanel mainBpm;
	public static KnobPanel bpmLow;
	public static KnobPanel bpmHigh;
	public static KnobPanel elongateMidi;
	public static KnobPanel transposeScore;
	JButton switchOnComposeRandom;

	// seed / midi
	RandomValueButton randomSeed;
	int lastRandomSeed = 0;
	double realBpm = 60;

	JList<File> generatedMidi;
	public static Sequencer sequencer = null;
	File currentMidi = null;
	MidiDevice device = null;

	JCheckBox showScore;
	ScrollComboBox<String> showScorePicker;
	JCheckBox midiMode;
	ScrollComboBox<String> midiModeDevices;
	JCheckBox collapseDrumTracks;

	JButton compose;
	JButton regenerate;
	JButton startMidi;
	JButton stopMidi;

	Thread cycle;
	JCheckBox useVolumeSliders;
	JCheckBox loopBeat;
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

	public static JFrame currentPopup = null;
	public static DebugConsole dconsole = null;
	public static VibeComposerGUI vibeComposerGUI = null;

	private static GridBagConstraints constraints = new GridBagConstraints();

	public static JPanel extraSettingsPanel;


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

		//createHorizontalSeparator(15, this);

		initSoloMuters(20, GridBagConstraints.WEST);

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


			constraints.gridy = 320;
			everythingPanel.add(instrumentTabPane, constraints);

			// arrangement
			initArrangementSettings(325, GridBagConstraints.CENTER);
		}

		//createHorizontalSeparator(327, this);

		// ---- OTHER SETTINGS ----
		{

			// randomization buttons
			initRandomButtons(330, GridBagConstraints.CENTER);

			initMacroParams(340, GridBagConstraints.CENTER);

			// chord settings - variety/spice
			// chord settings - progressions
			initChordProgressionSettings(350, GridBagConstraints.CENTER);

			// chord tool tip

			everythingPanel.add(controlPanel, constraints);

			initCustomChords(360, GridBagConstraints.CENTER);

		}


		//createHorizontalSeparator(400, this);

		// ---- CONTROL PANEL -----
		initControlPanel(410, GridBagConstraints.CENTER);


		// ---- PLAY PANEL ----
		initPlayPanel(420, GridBagConstraints.CENTER);
		initSliderPanel(440, GridBagConstraints.CENTER);

		// --- GENERATED MIDI DRAG n DROP ---

		constraints.anchor = GridBagConstraints.CENTER;

		// ---- MESSAGE PANEL ----

		JPanel messagePanel = new JPanel();
		messagePanel.setOpaque(false);
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

		initHelperPopups();

		isDarkMode = !isDarkMode;


		everythingPane.setViewportView(everythingPanel);
		add(everythingPane, constraints);

		//switchFullMode();
		instrumentTabPane.setSelectedIndex(0);
		//instrumentTabPane.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		recalculateTabPaneCounts();
		switchDarkMode();
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
		everythingPanel.add(mainTitle, constraints);
		constraints.gridy = 1;
		//everythingPanel.add(subTitle, constraints);

		JPanel mainButtonsPanel = new JPanel();
		mainButtonsPanel.setOpaque(false);
		constraints.gridy = startY + 3;

		//unsoloAll = makeButton("S", "UnsoloAllTracks");

		globalSoloMuter = new SoloMuter(-1, SoloMuter.Type.GLOBAL);

		mainButtonsPanel.add(globalSoloMuter);
		globalSoloMuter.setBackground(null);

		mainButtonsPanel.add(makeButton("Toggle Dark Mode", "SwitchDarkMode"));

		mainButtonsPanel.add(makeButton("Toggle Adv. Features", "ToggleAdv"));

		mainButtonsPanel.add(makeButton("B I G/small", "SwitchBigMode"));

		extraSettingsPanel = new JPanel();
		extraSettingsPanel.setLayout(new BoxLayout(extraSettingsPanel, BoxLayout.Y_AXIS));

		mainButtonsPanel.add(makeButton("Extra", "ShowExtraPopup"));

		everythingPanel.add(mainButtonsPanel, constraints);
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


		JPanel melodySettingsPanel = new JPanel();
		JLabel melodyLabel = new JLabel("MELODY  ");
		melodySettingsPanel.add(melodyLabel);
		melodySettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		melodyQuickness = new KnobPanel("Quickness", 100);
		melodySettingsPanel.add(melodyQuickness);


		maxJump = new KnobPanel("Max Note<br>Jump", 1, 1, 4);
		maxExceptions = new KnobPanel("Max<br>Exceptions", 2, 0, 4);
		melodyAlternateRhythmChance = new KnobPanel("Alternating<br>Rhythm", 50);
		melodySameRhythmChance = new KnobPanel("Doubled<br>Rhythm", 50);
		melodyUseOldAlgoChance = new KnobPanel("Legacy<br>Algo", 0);
		melodySplitChance = new KnobPanel("Split%", 50);
		melodyExceptionChance = new KnobPanel("Exception%", 50);

		melodySettingsPanel.add(maxJump);
		melodySettingsPanel.add(maxExceptions);
		melodySettingsPanel.add(melodyAlternateRhythmChance);
		melodySettingsPanel.add(melodySameRhythmChance);
		melodySettingsPanel.add(melodySplitChance);
		melodySettingsPanel.add(melodyExceptionChance);


		//melodySettingsPanel.add(melodyUseOldAlgoChance);

		randomChordNote = new JCheckBox();
		randomChordNote.setSelected(true);
		melodyFirstNoteFromChord = new JCheckBox();
		melodyFirstNoteFromChord.setSelected(true);


		//melodySettingsPanel.add(new JLabel("Note#1 From Chord:"));
		//melodySettingsPanel.add(melodyFirstNoteFromChord);
		//melodySettingsPanel.add(new JLabel("But Randomized:"));
		//melodySettingsPanel.add(randomChordNote);

		// ---- EXTRA -----

		JPanel melodySettingsExtraPanel = new JPanel();
		melodySettingsExtraPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		melodySettingsExtraPanel.setMaximumSize(new Dimension(1800, 50));

		JLabel melodyExtraLabel = new JLabel("MELODY SETTINGS+");
		melodyExtraLabel.setPreferredSize(new Dimension(120, 30));
		melodyExtraLabel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		melodySettingsExtraPanel.add(melodyExtraLabel);

		JButton generateUserMelodySeed = makeButton("Randomize Seed", "GenMelody");
		JButton clearUserMelodySeed = makeButton("Clear Seed", "ClearMelody");
		randomMelodySameSeed = new JCheckBox("Same#", false);
		randomMelodyOnRegenerate = new JCheckBox("On regen", false);
		arpCopyMelodyInst = new JCheckBox("Force copy Arp#1 inst.", true);
		melodyBasicChordsOnly = new JCheckBox("Force Scale", true);
		MelodyMidiDropPane dropPane = new MelodyMidiDropPane();
		dropPane.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		useUserMelody = new JCheckBox("Use MIDI Melody File", true);

		melodySettingsExtraPanel.add(arpCopyMelodyInst);
		melodySettingsExtraPanel.add(generateUserMelodySeed);
		melodySettingsExtraPanel.add(randomMelodySameSeed);
		melodySettingsExtraPanel.add(randomMelodyOnRegenerate);
		melodySettingsExtraPanel.add(clearUserMelodySeed);
		melodySettingsExtraPanel.add(melodyBasicChordsOnly);
		melodySettingsExtraPanel.add(useUserMelody);
		melodySettingsExtraPanel.add(dropPane);


		toggleableComponents.add(melodyAlternateRhythmChance);
		toggleableComponents.add(melodySameRhythmChance);
		toggleableComponents.add(melodySplitChance);
		toggleableComponents.add(melodyExceptionChance);
		toggleableComponents.add(melodySettingsExtraPanel);

		melodySettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		melodySettingsPanel.setMaximumSize(new Dimension(1800, 50));
		scrollableMelodyPanels.add(melodySettingsPanel);
		scrollableMelodyPanels.add(melodySettingsExtraPanel);
		//addHorizontalSeparatorToPanel(scrollableMelodyPanels);
	}

	private void initMelody(int startY, int anchorSide) {

		for (int i = 0; i < 3; i++) {
			MelodyPanel melodyPanel = new MelodyPanel(this);
			((JPanel) melodyScrollPane.getViewport().getView()).add(melodyPanel);
			melodyPanels.add(melodyPanel);
			melodyPanel.setPanelOrder(i + 1);
			//melodyPanel.setMidiChannel(i + 1);
			if (i > 0) {
				melodyPanel.setPauseChance(60);
				melodyPanel.getVolSlider().setVisible(false);
				melodyPanel.getSoloMuter().setVisible(false);
				melodyPanel.getInstrumentBox().setVisible(false);
				melodyPanel.setVelocityMax(80);
				melodyPanel.setVelocityMin(63);

				if (i % 2 == 1) {
					melodyPanel.setTranspose(12);
				} else {
					melodyPanel.setTranspose(-12);
				}
			}
		}


		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Melody", melodyScrollPane);


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
		chordSettingsPanel.add(new JLabel("CHORDS"));
		chordSettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

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


		randomChordDelay = new JCheckBox("Delay", false);
		randomChordStrum = new JCheckBox("", true);
		randomChordStruminess = new KnobPanel("Struminess", 50);
		randomChordSplit = new JCheckBox("Random Split Pos.", false);
		randomChordTranspose = new JCheckBox("Transpose", true);
		randomChordSustainChance = new KnobPanel("Chord%", 25);
		randomChordSustainUseShortening = new JCheckBox("Shorten Plucks", true);
		randomChordUseChordFill = new JCheckBox("Fills", true);
		randomChordMaxSplitChance = new KnobPanel("Max<br>Split%", 25);
		chordSlashChance = new KnobPanel("Chord1<br>Slash%", 0);
		randomChordPattern = new JCheckBox("Patterns", true);
		randomChordShiftChance = new KnobPanel("Shift%", 25);
		randomChordVoicingChance = new KnobPanel("Flatten<br>Voicing%", 100);
		randomChordMinVel = new KnobPanel("Min<br>Vel", 65, 0, 126);
		randomChordMaxVel = new KnobPanel("Max<br>Vel", 90, 1, 127);

		chordSettingsPanel.add(randomChordTranspose);
		chordSettingsPanel.add(randomChordStrum);
		chordSettingsPanel.add(randomChordStruminess);
		chordSettingsPanel.add(randomChordUseChordFill);

		chordSettingsPanel.add(randomChordDelay);
		chordSettingsPanel.add(randomChordSplit);
		//chordSettingsPanel.finishMinimalInit();

		randomChordStretchType = new ScrollComboBox<>();
		MidiUtils.addAllToJComboBox(new String[] { "NONE", "FIXED", "AT_MOST" },
				randomChordStretchType);
		randomChordStretchType.setSelectedItem("NONE");
		JLabel stretchLabel = new JLabel("StretCh.");
		chordSettingsPanel.add(stretchLabel);
		chordSettingsPanel.add(randomChordStretchType);
		randomChordStretchPicker = new ScrollComboBox<>();
		MidiUtils.addAllToJComboBox(new String[] { "3", "4", "5", "6" }, randomChordStretchPicker);
		randomChordStretchPicker.setSelectedItem("4");
		chordSettingsPanel.add(randomChordStretchPicker);

		JButton clearChordPatternSeeds = makeButton("Clear presets", "ClearChordPatterns");

		JPanel chordSettingsExtraPanel = new JPanel();
		JLabel csExtra = new JLabel("CHORD SETTINGS+");
		csExtra.setPreferredSize(new Dimension(120, 30));
		csExtra.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		chordSettingsExtraPanel.add(csExtra);

		chordSettingsExtraPanel.add(randomChordSustainChance);
		chordSettingsExtraPanel.add(randomChordSustainUseShortening);
		chordSettingsExtraPanel.add(randomChordMaxSplitChance);
		chordSettingsExtraPanel.add(chordSlashChance);
		chordSettingsExtraPanel.add(randomChordVoicingChance);
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


		createRandomChordPanels(Integer.valueOf(randomChordsToGenerate.getText()), false);

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
				//System.out.println("Size: " + scrollPaneDimension.toString());
				return scrollPaneDimension;
			}
		};
		arpScrollPane.setViewportView(scrollableArpPanels);
		arpScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		arpScrollPane.getVerticalScrollBar().setUnitIncrement(16);

		JPanel arpsSettingsPanel = new JPanel();
		arpsSettingsPanel.add(new JLabel("ARPS      "));
		arpsSettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
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


		randomArpTranspose = new JCheckBox("Transpose", true);
		randomArpPattern = new JCheckBox("Patterns", false);
		randomArpHitsPicker = new ScrollComboBox<String>();
		MidiUtils.addAllToJComboBox(new String[] { "1", "2", "3", "4", "5", "6", "7", "8" },
				randomArpHitsPicker);
		randomArpHitsPicker.setSelectedItem("4");
		randomArpHitsPerPattern = new JCheckBox("Random#", true);
		randomArpAllSameInst = new JCheckBox("One Inst.", false);
		randomArpAllSameHits = new JCheckBox("One #", true);
		randomArpLimitPowerOfTwo = new JCheckBox("<html>Limit 2<sup>n</sup>", true);
		randomArpUseChordFill = new JCheckBox("Fills", true);
		arpShiftChance = new KnobPanel("Shift%", 25);
		randomArpUseOctaveAdjustments = new JCheckBox("Rand. Oct.", false);
		randomArpMaxSwing = new KnobPanel("Swing%", 50);
		randomArpMaxRepeat = new KnobPanel("Max<br>Repeat", 2, 1, 4);
		randomArpMinVel = new KnobPanel("Min<br>Vel", 65, 0, 126);
		randomArpMaxVel = new KnobPanel("Max<br>Vel", 90, 1, 127);

		arpsSettingsPanel.add(new JLabel("Arp#"));
		arpsSettingsPanel.add(randomArpHitsPicker);
		arpsSettingsPanel.add(randomArpHitsPerPattern);
		arpsSettingsPanel.add(randomArpAllSameHits);
		arpsSettingsPanel.add(randomArpUseChordFill);

		arpsSettingsPanel.add(randomArpTranspose);

		randomArpStretchType = new ScrollComboBox<>();
		MidiUtils.addAllToJComboBox(new String[] { "NONE", "FIXED", "AT_MOST" },
				randomArpStretchType);
		randomArpStretchType.setSelectedItem("AT_MOST");
		JLabel stretchLabel = new JLabel("StretCh.");
		arpsSettingsPanel.add(stretchLabel);
		arpsSettingsPanel.add(randomArpStretchType);
		randomArpStretchPicker = new ScrollComboBox<>();
		MidiUtils.addAllToJComboBox(new String[] { "3", "4", "5", "6" }, randomArpStretchPicker);
		randomArpStretchPicker.setSelectedItem("4");
		arpsSettingsPanel.add(randomArpStretchPicker);


		toggleableComponents.add(stretchLabel);
		toggleableComponents.add(randomArpStretchType);
		toggleableComponents.add(randomArpStretchPicker);

		JButton clearArpPatternSeeds = makeButton("Clear Presets", "ClearArpPatterns");
		JPanel arpSettingsExtraPanel = new JPanel();
		JLabel csExtra = new JLabel("ARP SETTINGS+");
		csExtra.setPreferredSize(new Dimension(120, 30));
		csExtra.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		arpSettingsExtraPanel.add(csExtra);

		arpSettingsExtraPanel.add(randomArpAllSameInst);
		arpSettingsExtraPanel.add(randomArpUseOctaveAdjustments);
		arpSettingsExtraPanel.add(randomArpMaxSwing);
		arpSettingsExtraPanel.add(randomArpMaxRepeat);
		arpSettingsExtraPanel.add(randomArpMinVel);
		arpSettingsExtraPanel.add(randomArpMaxVel);
		arpSettingsExtraPanel.add(randomArpPattern);
		arpSettingsExtraPanel.add(arpShiftChance);
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


		createRandomArpPanels(Integer.valueOf(randomArpsToGenerate.getText()), false);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Arps", arpScrollPane);
	}

	private void initBass(int startY, int anchorSide) {
		bassPanel = new BassPanel(this);

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
		bassSettingsPanel.add(new JLabel("BASS"));
		bassSettingsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		bassSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		bassSettingsPanel.setMaximumSize(new Dimension(1800, 50));

		scrollableBassPanels.add(bassSettingsPanel);
		scrollableBassPanels.add(bassPanel);

		bassPanels.add(bassPanel);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Bass", bassScrollPane);
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
		drumsPanel.add(new JLabel("DRUMS "));
		drumsPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
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

		JButton clearPatternSeeds = makeButton("Clear Presets", "ClearPatterns");

		randomDrumMaxSwingAdjust = new JTextField("20", 2);
		randomDrumSlide = new JCheckBox("Random Delay", false);
		randomDrumUseChordFill = new JCheckBox("Fills", true);
		randomDrumPattern = new JCheckBox("Patterns", true);
		randomDrumVelocityPatternChance = new KnobPanel("Dynamic%", 50);
		randomDrumShiftChance = new KnobPanel("Shift%", 50);

		drumsPanel.add(new JLabel("Max swing%+-"));
		drumsPanel.add(randomDrumMaxSwingAdjust);
		drumsPanel.add(randomDrumUseChordFill);

		randomDrumHitsMultiplier = new ScrollComboBox<>();
		MidiUtils.addAllToJComboBox(new String[] { "1", "2", "3", "4", "---" },
				randomDrumHitsMultiplier);
		randomDrumHitsMultiplier.setSelectedItem("1");
		randomDrumHitsMultiplier.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent event) {
				if (event.getStateChange() == ItemEvent.SELECTED) {
					String item = (String) event.getItem();
					if ("---".equals(item)) {
						return;
					}
					int newState = Integer.valueOf(item);
					if (newState != randomDrumHitsMultiplierLastState) {
						for (int i = 0; i < drumPanels.size(); i++) {
							int newHits = drumPanels.get(i).getHitsPerPattern() * newState
									/ randomDrumHitsMultiplierLastState;
							drumPanels.get(i).setHitsPerPattern(newHits);
						}
						randomDrumHitsMultiplierLastState = newState;
					}

				}
			}
		});

		drumsPanel.add(new JLabel("Hits Multiplier:"));
		drumsPanel.add(randomDrumHitsMultiplier);
		drumsPanel.add(randomDrumSlide);
		ScrollComboBox<String> drumPartPresetBox = new ScrollComboBox<>();
		MidiUtils.addAllToJComboBox(new String[] { "---", "POP", "DNB" }, drumPartPresetBox);
		drumPartPresetBox.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent event) {
				if (event.getStateChange() == ItemEvent.SELECTED) {
					String item = (String) event.getItem();
					if ("---".equals(item)) {
						return;
					}
					InputStream is = VibeComposerGUI.class
							.getResourceAsStream("/drums/" + item + ".xml");
					try {
						unmarshallDrumsFromResource(is);
					} catch (JAXBException | IOException e) {
						// TODO Auto-generated catch block
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


		collapseDrumTracks = new JCheckBox("Combine Drum MIDI Tracks", true);
		collapseDrumTracks.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent e) {
				drumPanels.forEach(
						f -> f.getSoloMuter().setVisible(!collapseDrumTracks.isSelected()));
			}

		});
		drumExtraSettings.add(collapseDrumTracks);

		drumExtraSettings.add(makeButton("Save Drums As", "DrumSave"));
		drumExtraSettings.add(makeButton("Load Drums", "DrumLoad"));

		drumExtraSettings.add(randomDrumPattern);
		drumExtraSettings.add(randomDrumVelocityPatternChance);
		arrangementAffectsDrumVelocity = new JCheckBox("Adj. Vel. in Arr.", true);
		drumExtraSettings.add(arrangementAffectsDrumVelocity);
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


		createBlueprintedDrumPanels((Integer.valueOf(randomDrumsToGenerate.getText())), false);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		instrumentTabPane.addTab("Drums", drumScrollPane);

	}

	public static void setActualModel(TableModel model) {
		scrollableArrangementActualTable.setModel(model);
		scrollableArrangementActualTable.setRowSelectionAllowed(false);
		scrollableArrangementActualTable.setColumnSelectionAllowed(true);
	}

	private void handleArrangementAction(String action, int seed, int maxLength) {
		boolean refreshActual = false;
		if (action.equalsIgnoreCase("ArrangementReset")) {
			arrangement.generateDefaultArrangement();
			pieceLength.setText("12");
		} else if (action.equalsIgnoreCase("ArrangementAddLast")) {
			if (instrumentTabPane.getSelectedIndex() == 5) {
				arrangement.duplicateSection(scrollableArrangementTable, false);
			} else {
				actualArrangement.duplicateSection(scrollableArrangementActualTable, true);
				refreshActual = true;
			}
			if (arrangement.getSections().size() > maxLength) {
				pieceLength.setText("" + ++maxLength);
			}
		} else if (action.equalsIgnoreCase("ArrangementRemoveLast")) {
			if (instrumentTabPane.getSelectedIndex() == 5) {
				arrangement.removeSection(scrollableArrangementTable, false);
			} else {
				actualArrangement.removeSection(scrollableArrangementActualTable, true);
				refreshActual = true;
			}
			//pieceLength.setText("" + --maxLength);
		} else if (action.equalsIgnoreCase("ArrangementRandomize")) {
			// on compose -> this must happen before compose part
			arrangement.randomizeFully(maxLength, seed, 30, 30, 2, 4, 15);
		} else if (action.startsWith("ArrangementOpenVariation,")) {
			Integer secOrder = Integer.valueOf(action.split(",")[1]);
			if (varPopup != null) {
				varPopup.getFrame().dispose();
			}
			recalculateActualArrangementSection(secOrder - 1);
			varPopup = new VariationPopup(secOrder,
					actualArrangement.getSections().get(secOrder - 1),
					new Point(MouseInfo.getPointerInfo().getLocation().x,
							vibeComposerGUI.getLocation().y),
					vibeComposerGUI.getSize());
			refreshActual = true;
			//variationJD.getFrame().setTitle(action);
		}

		if (!refreshActual) {
			scrollableArrangementTable.setModel(arrangement.convertToTableModel());
		} else {
			setActualModel(actualArrangement.convertToActualTableModel());
			refreshVariationPopupButtons(actualArrangement.getSections().size());
		}
	}

	private void recalculateActualArrangementSection(Integer secOrder) {
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
		JPanel arrangementSettings = new JPanel();
		arrangementSettings.setOpaque(false);
		arrangementSettings.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		arrangementSettings.add(new JLabel("ARRANGEMENT"));

		useArrangement = new JCheckBox("Enable", false);
		arrangementSettings.add(useArrangement);

		pieceLength = new JTextField("12", 2);
		//arrangementSettings.add(new JLabel("Max Length:"));


		JButton resetArrangementBtn = makeButton("Reset Arr.", "ArrangementReset");

		JButton randomizeArrangementBtn = makeButton("Randomize Sections (Max):",
				"ArrangementRandomize");

		randomizeArrangementOnCompose = new JCheckBox("on Compose", true);


		JButton copySelectedBtn = makeButton("Copy Selected Section", "ArrangementAddLast");

		JButton removeSelectedBtn = makeButton("Remove All Selected", "ArrangementRemoveLast");

		arrangementSettings.add(randomizeArrangementBtn);
		arrangementSettings.add(pieceLength);
		arrangementSettings.add(randomizeArrangementOnCompose);

		arrangementVariationChance = new KnobPanel("Section<br>Variations", 30);
		arrangementSettings.add(arrangementVariationChance);
		arrangementPartVariationChance = new KnobPanel("Part<br>Variations", 30);
		arrangementSettings.add(arrangementPartVariationChance);

		arrangementSettings
				.add(new JLabel("                                                          "));

		arrangementSettings.add(copySelectedBtn);
		arrangementSettings.add(removeSelectedBtn);
		arrangementSettings.add(resetArrangementBtn);

		arrangementSettings.add(new JLabel("Seed"));
		arrangementSeed = new RandomValueButton(0);
		arrangementSettings.add(arrangementSeed);

		arrangementManualOverride = new JCheckBox("Allow Manual Change", false);
		arrangementSettings.add(arrangementManualOverride);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
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
				if (row >= 2) {
					Object objValue = getModel().getValueAt(row,
							scrollableArrangementTable.convertColumnIndexToModel(col));
					Integer value = (objValue instanceof String)
							? Integer.valueOf((String) objValue)
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
				} else {
					comp.setBackground(new Color(100, 150, 150));
				}

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
		/*scrollableArrangementTable.getColumnModel()
				.addColumnModelListener(new TableColumnModelListener() {
					@Override
					public void columnMoved(TableColumnModelEvent e) {
						MidiGenerator.ARRANGEMENT.resortByIndexes(scrollableArrangementTable);
		
					}
		
		*/

		scrollableArrangementActualTable = new JTable(5, 5) {
			private static final long serialVersionUID = 1L;

			@Override
			public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
				Component comp = super.prepareRenderer(renderer, row, col);
				String value = (String) getModel().getValueAt(row,
						scrollableArrangementActualTable.convertColumnIndexToModel(col));
				comp.setForeground(isDarkMode ? arrangementDarkModeText : arrangementLightModeText);
				if (value == null)
					return comp;
				if (getModel().getColumnCount() <= col) {
					return comp;
				}

				arrangementTableProcessComponent(comp, row, col, value,
						new int[] { 0, 0, melodyPanels.size(), 1, chordPanels.size(),
								arpPanels.size(), drumPanels.size() },
						true);
				return comp;
			}
		};

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
	}

	private void arrangementTableProcessComponent(Component comp, int row, int col, String value,
			int[] maxCounts, boolean actual) {
		if (row >= 2) {

			// 2,3,4,5,6 -> melody, bass, chord, arp, drum counts

			if (value.equalsIgnoreCase("")) {
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
				if (actual) {
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

	private void refreshVariationPopupButtons(int count) {
		/*if (count == variationButtonsPanel.getComponents().length) {
			return;
		}*/
		variationButtonsPanel.removeAll();
		for (int i = 0; i < count; i++) {
			JButton butt = makeButton("Edit " + (i + 1), "ArrangementOpenVariation," + (i + 1));
			butt.setPreferredSize(new Dimension(
					(scrollPaneDimension.width - arrangementRowHeaderWidth) / count, 50));
			recolorVariationPopupButton(butt, actualArrangement.getSections().get(i));
			variationButtonsPanel.add(butt);
		}
	}

	private void recolorVariationPopupButton(JButton butt, Section sec) {
		int count = (sec.getRiskyVariations() != null)
				? (int) sec.getRiskyVariations().stream().filter(e -> e.booleanValue()).count()
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


		randomizeInstOnComposeOrGen = new JCheckBox("on Compose/Gen");
		randomizeBpmOnCompose = new JCheckBox("on Compose");
		randomizeTransposeOnCompose = new JCheckBox("on Compose");
		randomizeInstOnComposeOrGen.setSelected(true);
		randomizeBpmOnCompose.setSelected(true);
		randomizeTransposeOnCompose.setSelected(true);
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
		macroParams.setLayout(new GridLayout(0, 2, 0, 0));
		macroParams.setOpaque(false);
		macroParams.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		fixedLengthChords = new ScrollComboBox<>();
		MidiUtils.addAllToJComboBox(new String[] { "4", "8", "RANDOM" }, fixedLengthChords);
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
		globalSwingOverride = new JCheckBox("", false);
		globalSwingOverrideValue = new KnobPanel("Global Swing<br>Override", 50);
		globalSwingPanel.add(globalSwingOverride);
		globalSwingPanel.add(globalSwingOverrideValue);
		globalSwingPanel.setOpaque(false);
		macroParams.add(globalSwingPanel);

		useDoubledDurations = new JCheckBox("Doubled Beat Duration", false);
		JPanel useDoubledPanel = new JPanel();
		useDoubledPanel.add(useDoubledDurations);
		useDoubledPanel.setOpaque(false);
		macroParams.add(useDoubledPanel);

		chordProgPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		allowRepPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		globalSwingPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		useDoubledPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));

		JPanel allInstsPanel = new JPanel();
		useAllInsts = new JCheckBox("Use All Inst., Except:", false);
		allInstsPanel.add(useAllInsts);
		bannedInsts = new JTextField("", 8);
		allInstsPanel.add(bannedInsts);
		reinitInstPools = makeButton("Initialize All Inst.", "InitAllInsts");
		allInstsPanel.add(reinitInstPools);
		extraSettingsPanel.add(allInstsPanel);

		loopBeatCount = new KnobPanel("Loop # Beats", 1, 1, 4);

		JPanel customDrumMappingPanel = new JPanel();

		drumCustomMapping = new JCheckBox("Custom Drum Mapping", true);
		drumCustomMappingNumbers = new JTextField(
				StringUtils.join(MidiUtils.DRUM_INST_NUMBERS_SEMI, ","));

		customDrumMappingPanel.add(drumCustomMapping);
		customDrumMappingPanel.add(drumCustomMappingNumbers);
		drumCustomMapping.setToolTipText(
				"<html>" + StringUtils.join(MidiUtils.DRUM_INST_NAMES_SEMI, "|") + "</html>");

		extraSettingsPanel.add(loopBeatCount);
		extraSettingsPanel.add(customDrumMappingPanel);

		toggleableComponents.add(globalSwingPanel);
		toggleableComponents.add(useDoubledPanel);

		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		controlPanel.add(macroParams);
	}

	private void initChordProgressionSettings(int startY, int anchorSide) {
		// CHORD SETTINGS 1 - chord variety 
		JPanel chordProgressionSettingsPanel = new JPanel();
		chordProgressionSettingsPanel.setLayout(new GridLayout(0, 2, 0, 0));
		chordProgressionSettingsPanel.setOpaque(false);
		chordProgressionSettingsPanel
				.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		//toggleableComponents.add(chordProgressionSettingsPanel);


		spiceChance = new KnobPanel("Spice", 8);
		spiceAllowDimAug = new JCheckBox("Dim/Aug", false);
		spiceAllow9th13th = new JCheckBox("9th/13th", false);
		spiceFlattenBigChords = new JCheckBox("Spicy Voicing", false);


		JPanel spiceChancePanel = new JPanel();
		spiceChancePanel.add(spiceChance);
		spiceChancePanel.setOpaque(false);

		JPanel spiceAllowDimAugPanel = new JPanel();
		spiceAllowDimAugPanel.add(spiceAllowDimAug);
		spiceAllowDimAugPanel.setOpaque(false);

		JPanel spiceAllow9th13thPanel = new JPanel();
		spiceAllow9th13thPanel.add(spiceAllow9th13th);
		spiceAllow9th13thPanel.setOpaque(false);

		JPanel spiceFlattenBigChordsPanel = new JPanel();
		spiceFlattenBigChordsPanel.add(spiceFlattenBigChords);
		spiceFlattenBigChordsPanel.setOpaque(false);

		chordProgressionSettingsPanel.add(spiceChancePanel);
		chordProgressionSettingsPanel.add(spiceAllowDimAugPanel);
		chordProgressionSettingsPanel.add(spiceAllow9th13thPanel);
		chordProgressionSettingsPanel.add(spiceFlattenBigChordsPanel);

		// CHORD SETTINGS 2 - chord progression
		firstChordSelection = new ScrollComboBox<String>();
		firstChordSelection.addItem("R");
		firstChordSelection.addItem("I");
		firstChordSelection.addItem("V");
		firstChordSelection.addItem("vi");
		firstChordSelection.addItemListener(this);

		lastChordSelection = new ScrollComboBox<String>();
		lastChordSelection.addItem("R");
		lastChordSelection.addItem("I");
		lastChordSelection.addItem("IV");
		lastChordSelection.addItem("V");
		lastChordSelection.addItem("vi");
		lastChordSelection.addItemListener(this);
		lastChordSelection.setSelectedIndex(0);
		JPanel lastChordPanel = new JPanel();

		lastChordPanel.add(new JLabel("First Chord:"));
		lastChordPanel.add(firstChordSelection);
		lastChordPanel.add(new JLabel("Last Chord:"));
		lastChordPanel.add(lastChordSelection);
		extraSettingsPanel.add(lastChordPanel);


		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		controlPanel.add(chordProgressionSettingsPanel);
	}

	private void initCustomChords(int startY, int anchorSide) {
		JPanel chordToolTip = new JPanel();
		chordToolTip.setOpaque(false);
		chordToolTip.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		/*tipLabel = new JLabel(
				"Chord meaning: 1 = I(major), 10 = i(minor), 100 = I(aug), 1000 = I(dim), 10000 = I7(major), "
						+ "100000 = i7(minor), 1000000 = 9th, 10000000 = 13th, 100000000 = Sus4, 1000000000 = Sus2, 10000000000 = Sus7");*/
		String tooltip = "[Allowed chords: C/D/E/F/G/A/B + m / aug / dim / maj7 / m7 / maj9 / m9 / maj13 / m13 / sus4 / sus2 / sus7]";
		tipLabel = new JLabel();
		//chordToolTip.add(tipLabel);

		JButton randomizeCustomChords = makeButton("    Randomize Chords    ",
				"RandomizeUserChords");
		chordToolTip.add(randomizeCustomChords);

		userChordsEnabled = new JCheckBox("Custom Chords", false);
		chordToolTip.add(userChordsEnabled);

		userChords = new JTextField("R", 35);
		userChords.setToolTipText(tooltip);
		chordToolTip.add(userChords);
		userChordsDurations = new JTextField("2,2,2,2", 9);
		chordToolTip.add(new JLabel("Chord durations:"));
		chordToolTip.add(userChordsDurations);


		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(chordToolTip, constraints);

	}

	private void initSliderPanel(int startY, int anchorSide) {
		JPanel sliderPanel = new JPanel();
		sliderPanel.setOpaque(false);
		sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.X_AXIS));
		sliderPanel.setPreferredSize(new Dimension(1200, 20));


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
						if (needToRecalculateSoloMuters) {
							needToRecalculateSoloMuters = false;
							unapplySolosMutes();

							reapplySolosMutes();
							recolorButtons();
						}
						try {
							sleep(100);
						} catch (InterruptedException e) {

						}
					} catch (Exception e) {
						System.out.println("Exception in SOLO buttons thread:" + e);
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

	private static int countAllPanels() {
		int count = 0;
		for (int i = 0; i < 5; i++) {
			count += getInstList(i).size();
		}
		return count;
	}

	private void startOmnipresentThread() {
		// init thread

		Thread cycle = new Thread() {

			public void run() {


				while (true) {
					try {
						if (sequencer != null && sequencer.isRunning()) {
							if (!isDragging && !isKeySeeking)
								slider.setValue((int) (sequencer.getMicrosecondPosition() / 1000));
							if (!isDragging && !isKeySeeking)
								currentTime.setText(microsecondsToTimeString(
										sequencer.getMicrosecondPosition()));
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
						if (actualArrangement != null && slider.getMaximum() > 0) {
							int val = slider.getValue();
							int arrangementSize = actualArrangement.getSections().stream()
									.mapToInt(e -> e.getMeasures()).sum();
							if (arrangementSize > 0) {
								int divisor = slider.getMaximum() / arrangementSize;
								int sectIndex = (val - 1) / divisor;
								if (sectIndex >= arrangementSize) {
									sectionText.setText("End");
								} else {
									if (useArrangement.isSelected()) {
										Section sec = null;
										int sizeCounter = 0;
										for (Section arrSec : actualArrangement.getSections()) {
											if (sizeCounter == sectIndex || (sectIndex < sizeCounter
													+ arrSec.getMeasures())) {
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
							if (loopBeat.isSelected()) {
								if (showScore.isSelected()) {
									showScore.setSelected(false);

								}
								/*int del = delayed();
								if (slider.getValue() < del) {
									slider.setValue(del);
								}*/


								if (slider.getValue() > loopBeatCount.getInt() * beatFromBpm()
										+ delayed()) {
									stopMidi();
									if (sequencer != null)
										composeMidi(true);
								}
							}


						}

						try {
							sleep(25);
						} catch (InterruptedException e) {

						}
					} catch (Exception e) {
						System.out.println("Exception in SEQUENCE SLIDER:");
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

	private static int delayed() {
		return (int) (MidiGenerator.START_TIME_DELAY * 2000 * 60 / mainBpm.getInt());
	}

	private static int beatFromBpm() {
		int finalVal = (1985 * 60 * elongateMidi.getInt() / mainBpm.getInt());
		//System.out.println(finalVal + "");
		return finalVal;
	}

	private void initControlPanel(int startY, int anchorSide) {
		JPanel controlSettingsPanel = new JPanel();
		//controlSettingsPanel.setLayout(new BoxLayout(controlSettingsPanel, BoxLayout.Y_AXIS));
		controlSettingsPanel.setOpaque(false);

		transposeScore = new KnobPanel("Global Transpose<br>(Key)", 0, -24, 24);
		controlSettingsPanel.add(transposeScore);

		mainBpm = new KnobPanel("BPM", 80, 60, 110);
		mainBpm.getKnob().setStretchAfterCustomInput(true);

		JPanel bpmLowHighPanel = new JPanel();

		arpAffectsBpm = new JCheckBox("BPM slowed by ARP", false);
		bpmLow = new KnobPanel("Min<br>BPM.", 60, 20, 249);
		bpmHigh = new KnobPanel("Max<br>BPM.", 110, 21, 250);
		elongateMidi = new KnobPanel("Elongate MIDI by:", 2, 1, 4);
		bpmLowHighPanel.add(bpmLow);
		bpmLowHighPanel.add(bpmHigh);
		bpmLowHighPanel.add(arpAffectsBpm);
		bpmLowHighPanel.add(elongateMidi);

		extraSettingsPanel.add(bpmLowHighPanel);

		controlSettingsPanel.add(mainBpm);
		scaleMode = new ScrollComboBox<String>();
		String[] scaleModes = new String[MidiUtils.ScaleMode.values().length];
		for (int i = 0; i < MidiUtils.ScaleMode.values().length; i++) {
			scaleModes[i] = MidiUtils.ScaleMode.values()[i].toString();
		}
		MidiUtils.addAllToJComboBox(scaleModes, scaleMode);

		controlSettingsPanel.add(new JLabel("Scale"));
		controlSettingsPanel.add(scaleMode);


		randomSeed = new RandomValueButton(0);
		compose = makeButton("COMPOSE", "Compose");
		compose.setBackground(new Color(180, 150, 90));
		compose.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		//compose.setBorderPainted(true);
		compose.setPreferredSize(new Dimension(80, 40));
		compose.setFont(compose.getFont().deriveFont(Font.BOLD));
		regenerate = makeButton("Regenerate", "Regenerate");
		regenerate.setFont(regenerate.getFont().deriveFont(Font.BOLD));
		JButton copySeed = makeButton("Copy seed", "CopySeed");
		JButton copyChords = makeButton("Copy chords", "CopyChords");
		JButton clearSeed = makeButton("Clear All Seeds", "ClearSeed");

		JButton loadConfig = makeButton("Load Config", "LoadGUIConfig");

		controlSettingsPanel.add(new JLabel("Random Seed:"));
		controlSettingsPanel.add(randomSeed);
		controlSettingsPanel.add(compose);
		controlSettingsPanel.add(regenerate);
		controlSettingsPanel.add(copySeed);
		controlSettingsPanel.add(currentChords);
		controlSettingsPanel.add(copyChords);
		controlSettingsPanel.add(clearSeed);
		controlSettingsPanel.add(loadConfig);


		constraints.gridy = startY;
		constraints.anchor = anchorSide;
		everythingPanel.add(controlSettingsPanel, constraints);
	}

	private void initPlayPanel(int startY, int anchorSide) {

		JPanel playSavePanel = new JPanel();
		playSavePanel.setOpaque(false);
		stopMidi = makeButton("STOP", "StopMidi");
		startMidi = makeButton("PLAY", "StartMidi");

		JButton save3Star = makeButton("Save 3*", "Save 3*");
		JButton save4Star = makeButton("Save 4*", "Save 4*");
		JButton save5Star = makeButton("Save 5*", "Save 5*");

		JButton saveWavFile = makeButton("Export As .wav", "SaveWavFile");

		showScore = new JCheckBox("Show Score", true);
		showScorePicker = new ScrollComboBox<String>();
		MidiUtils.addAllToJComboBox(new String[] { "w/o DRUMS", "DRUMS ONLY", "ALL" },
				showScorePicker);

		useVolumeSliders = new JCheckBox("Use Vol. Sliders", true);
		loopBeat = new JCheckBox("Loop Beat", false);

		midiMode = new JCheckBox("MIDI Transmitter Mode", true);
		midiMode.setToolTipText("Select a MIDI port on the right and click Regenerate.");

		midiMode.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				closeMidiDevice();
			}

		});

		midiModeDevices = new ScrollComboBox<String>();
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
				closeMidiDevice();
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
		playSettingsPanel.setOpaque(false);

		soundbankFilename = new JTextField(SOUNDBANK_DEFAULT, 18);
		JPanel soundbankPanel = new JPanel();
		JLabel soundbankLabel = new JLabel("Soundbank name:");
		soundbankPanel.add(soundbankLabel);
		soundbankPanel.add(soundbankFilename);
		extraSettingsPanel.add(soundbankPanel);

		playSettingsPanel.add(showScore);
		playSettingsPanel.add(showScorePicker);
		playSettingsPanel.add(useVolumeSliders);
		playSettingsPanel.add(loopBeat);
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
		helperPopupsPanel.add(makeButton("User Manual (opens browser)", "ShowHelpPopup"));
		helperPopupsPanel.add(makeButton("Debug Console", "ShowDebugPopup"));
		helperPopupsPanel.add(makeButton("About VibeComposer", "ShowAboutPopup"));
		extraSettingsPanel.add(helperPopupsPanel);
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
							for (int j = 0; j < 4; j++) {
								List<? extends InstPanel> panels = getInstList(j);
								for (int i = 0; i < panels.size(); i++) {
									if (j == 0 && i > 0) {
										// melody panels under first
										continue;
									}
									double vol = panels.get(i).getVolSlider().getValue() / 100.0;
									ShortMessage volumeMessage = new ShortMessage();
									volumeMessage.setMessage(ShortMessage.CONTROL_CHANGE,
											panels.get(i).getMidiChannel() - 1, 7,
											(int) (vol * 127));
									if (synth != null && synth.isOpen()) {
										synth.getReceiver().send(volumeMessage, -1);

									}
									if (midiMode.isSelected() && device != null) {
										device.getReceiver().send(volumeMessage, -1);
									}
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
		randomizeBpmOnCompose.setSelected(state);
		randomizeTransposeOnCompose.setSelected(state);
		//randomizeChordStrumsOnCompose.setSelected(state);
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

	private void switchBigMonitorMode() {
		Dimension newPrefSize = null;
		if (!isBigMonitorMode) {
			newPrefSize = new Dimension(1900, 600);
		} else {
			newPrefSize = new Dimension(1600, 400);
		}
		scrollPaneDimension = newPrefSize;
		instrumentTabPane.setPreferredSize(newPrefSize);
		instrumentTabPane.setSize(newPrefSize);
		isBigMonitorMode = !isBigMonitorMode;

		for (DrumPanel dp : drumPanels) {
			dp.getComboPanel().reapplyHits();
		}
		refreshVariationPopupButtons(scrollableArrangementActualTable.getColumnCount());
		pack();
	}

	private void switchDarkMode() {
		System.out.println("Switching dark mode!");
		if (isDarkMode) {
			FlatIntelliJLaf.install();
		} else {
			FlatDarculaLaf.install();
		}
		//UIManager.put("TabbedPane.contentOpaque", false);

		isDarkMode = !isDarkMode;
		ColorUIResource r = null;
		if (!isDarkMode) {
			r = new ColorUIResource(new Color(215, 215, 222));
		} else {
			r = new ColorUIResource(new Color(72, 70, 70));
		}
		UIManager.put("Button.background", r);
		UIManager.put("Panel.background", r);
		UIManager.put("ComboBox.background", r);
		UIManager.put("TextField.background", r);
		SwingUtilities.updateComponentTreeUI(this);

		toggledUIColor = (isDarkMode) ? darkModeUIColor : lightModeUIColor;

		mainTitle.setForeground((isDarkMode) ? new Color(0, 220, 220) : lightModeUIColor);
		subTitle.setForeground(toggledUIColor);
		messageLabel.setForeground(toggledUIColor);
		tipLabel.setForeground(toggledUIColor);
		currentTime.setForeground(toggledUIColor);
		totalTime.setForeground(toggledUIColor);
		randomChordsGenerateOnCompose.setForeground(toggledUIColor);
		randomArpsGenerateOnCompose.setForeground(toggledUIColor);
		randomDrumsGenerateOnCompose.setForeground(toggledUIColor);
		switchOnComposeRandom.setForeground(toggledUIColor);
		compose.setForeground(toggledUIColor);
		regenerate.setForeground(toggledUIColor);
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
		if (!isDarkMode) {
			panelColorHigh.brighter();
			panelColorLow.brighter();
		}


		globalSoloMuter.reapplyTextColor();
		for (SoloMuter sm : groupSoloMuters) {
			sm.reapplyTextColor();
		}

		for (int i = 0; i < 5; i++) {
			getInstList(i).forEach(e -> e.getSoloMuter().reapplyTextColor());
		}
		refreshVariationPopupButtons(actualArrangement.getSections().size());

		//switchFullMode(isDarkMode);


		//sizeRespectingPack();
		setVisible(true);
		repaint();
	}

	private void switchFullMode() {
		isFullMode = !isFullMode;

		toggleableComponents.forEach(e -> e.setVisible(isFullMode));
		melodyPanels
				.forEach(e -> e.getToggleableComponents().forEach(f -> f.setVisible(isFullMode)));
		bassPanel.getToggleableComponents().forEach(e -> e.setVisible(isFullMode));
		chordPanels
				.forEach(e -> e.getToggleableComponents().forEach(f -> f.setVisible(isFullMode)));
		arpPanels.forEach(e -> e.getToggleableComponents().forEach(f -> f.setVisible(isFullMode)));
		drumPanels.forEach(e -> e.getToggleableComponents().forEach(f -> f.setVisible(isFullMode)));


		/*instrumentTabPane
				.setPreferredSize(isFullMode ? scrollPaneDimension : scrollPaneDimensionToggled);*/
		/*if (isFullMode) {
			pack();
		}*/

	}

	private void closeMidiDevice() {
		stopMidi();
		if (sequencer != null) {
			sequencer.close();
			sequencer = null;
		}

		System.out.println("Closed sequencer!");

		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {

				MidiDevice oldDevice = device;
				device = null;


				if (oldDevice != null) {
					oldDevice.close();
				}

				System.out.println("Closed oldDevice!");
				oldDevice = null;
			}

		});

		needToRecalculateSoloMutersAfterSequenceGenerated = true;
	}

	private void composeMidi(boolean regenerate) {
		if (sequencer != null) {
			sequencer.stop();
		}
		if (midiMode.isSelected()) {
			synth = null;
		} else {
			if (device != null) {
				if (synth != null) {
					synth.close();
					synth = null;
				}
				if (sequencer != null) {
					sequencer.close();
					sequencer = null;
					System.out.println("CLOSED SEQUENCER!");
				}
				device.close();
				device = null;
				System.out.println("CLOSED DEVICE!");
			}
		}

		needToRecalculateSoloMuters = true;

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
				randomizeMelodySeeds();
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

		if (arpCopyMelodyInst.isSelected() && !melodyPanels.get(0).getMuteInst()) {
			if (arpPanels.size() > 0) {
				arpPanels.get(0).setInstrument(melodyPanels.get(0).getInstrument());
			}
		}

		if (!regenerate && randomizeArrangementOnCompose.isSelected()) {
			handleArrangementAction("ArrangementRandomize", lastRandomSeed,
					Integer.valueOf(pieceLength.getText()));
		} else {
			arrangement.resortByIndexes(scrollableArrangementTable);
			if (actualArrangement.getSections().size() > 1) {
				actualArrangement.resortByIndexes(scrollableArrangementActualTable);
			}
		}

		if ((regenerate || !randomizeArrangementOnCompose.isSelected()) && (currentMidi != null)
				&& arrangementManualOverride.isSelected()) {
			arrangement.setOverridden(true);
		} else {
			arrangement.setOverridden(false);
		}

		if (globalSwingOverride.isSelected()) {
			int swing = globalSwingOverrideValue.getInt();
			melodyPanels.forEach(e -> e.setSwingPercent(swing));
			randomArpMaxSwing.setInt(swing);
			drumPanels.forEach(e -> {
				if (!PUNCHY_DRUMS.contains(e.getInstrument())) {
					e.setSwingPercent(swing);
				}
			});
		}

		MidiGenerator melodyGen = new MidiGenerator(copyGUItoConfig());
		fillUserParameters();

		File makeDir = new File(MIDIS_FOLDER);
		makeDir.mkdir();

		String seedData = "" + masterpieceSeed;
		if (melodyPanels.get(0).getPatternSeed() != 0 && !melodyPanels.get(0).getMuteInst()) {
			seedData += "_" + melodyPanels.get(0).getPatternSeed();
		}

		String fileName = "seed" + seedData;
		String relPath = MIDIS_FOLDER + "/" + fileName + ".mid";

		// unapply all tracks first
		unapplySolosMutes();

		melodyGen.generateMasterpiece(masterpieceSeed, relPath);

		if (MelodyMidiDropPane.userMelody != null) {
			userChords.setText(StringUtils.join(MidiGenerator.chordInts, ","));
			setFixedLengthChords(MidiGenerator.chordInts.size());
		}

		// reapply
		reapplySolosMutes();

		currentMidi = null;

		setActualModel(MidiGenerator.gc.getActualArrangement().convertToActualTableModel());

		actualArrangement = new Arrangement();
		actualArrangement.setPreviewChorus(false);
		actualArrangement.getSections().clear();
		for (Section sec : MidiGenerator.gc.getActualArrangement().getSections()) {
			actualArrangement.getSections().add(sec.deepCopy());
		}

		refreshVariationPopupButtons(scrollableArrangementActualTable.getColumnCount());

		try (FileWriter fw = new FileWriter("randomSeedHistory.txt", true);
				BufferedWriter bw = new BufferedWriter(fw);
				PrintWriter out = new PrintWriter(bw)) {
			out.println(new Date().toString() + ", Seed: " + seedData);
		} catch (IOException e) {
			System.out.println("Failed to write into Random Seed History..");
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
			slider.setMajorTickSpacing(slider.getMaximum() / actualArrangement.getSections()
					.stream().mapToInt(e -> e.getMeasures()).sum());

			startVolumeSliderThread();
			recalculateTabPaneCounts();
			if (needToRecalculateSoloMutersAfterSequenceGenerated) {
				needToRecalculateSoloMuters = true;
				needToRecalculateSoloMutersAfterSequenceGenerated = false;
			}

		} catch (MidiUnavailableException | InvalidMidiDataException | IOException ex) {
			ex.printStackTrace();
		}
	}

	private void setFixedLengthChords(int size) {
		switch (size) {
		case 4:
			fixedLengthChords.setSelectedItem("4");
			break;
		case 8:
			fixedLengthChords.setSelectedItem("8");
			break;
		default:
			fixedLengthChords.setSelectedItem("RANDOM");
			break;
		}

	}

	private void randomizeMelodySeeds() {
		Random rand = new Random();
		int melodySeed = rand.nextInt();
		melodyPanels.forEach(e -> e.setVisible(false));
		if (!randomMelodySameSeed.isSelected()) {
			melodyPanels.forEach(e -> {
				int seed = rand.nextInt();
				e.setPatternSeed(seed);
			});
		} else {
			melodyPanels.forEach(e -> e.setPatternSeed(melodySeed));
		}
		melodyPanels.forEach(e -> e.setVisible(true));
	}

	private void unapplySolosMutes() {
		if (!sequenceReady()) {
			return;
		}
		for (int i = 0; i < sequencer.getSequence().getTracks().length; i++) {
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

		// drum specific
		if (collapseDrumTracks.isSelected() && drumPanels.size() > 0) {
			sequencer.setTrackSolo(drumPanels.get(0).getSequenceTrack(),
					groupSoloMuters.get(4).soloState != State.OFF);
		}

		if (collapseDrumTracks.isSelected() && drumPanels.size() > 0) {
			sequencer.setTrackMute(drumPanels.get(0).getSequenceTrack(),
					groupSoloMuters.get(4).muteState != State.OFF);
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

	private void randomizeUserChords() {
		MidiGenerator mg = new MidiGenerator(copyGUItoConfig());
		MidiGenerator.FIRST_CHORD = null;
		MidiGenerator.LAST_CHORD = null;
		MidiGenerator.userChords.clear();
		MidiGenerator.userChordsDurations.clear();
		mg.generatePrettyUserChords(new Random().nextInt(), MidiGenerator.gc.getFixedDuration(),
				4 * MidiGenerator.Durations.HALF_NOTE);
		List<String> prettyChords = MidiGenerator.chordInts;
		userChords.setText(StringUtils.join(prettyChords, ","));
	}

	private void openHelpPopup() {
		if (currentPopup != null) {
			currentPopup.setVisible(false);
		}
		HelpPopup popup = new HelpPopup();
		currentPopup = popup.getFrame();
	}

	private void openAboutPopup() {
		if (currentPopup != null) {
			currentPopup.setVisible(false);
		}
		AboutPopup popup = new AboutPopup();
		currentPopup = popup.getFrame();
	}

	private void openExtraSettingsPopup() {
		if (currentPopup != null) {
			currentPopup.setVisible(false);
		}
		ExtraSettingsPopup popup = new ExtraSettingsPopup();
		currentPopup = popup.getFrame();
	}

	private void openDebugConsole() {
		try {
			dconsole = new DebugConsole();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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

	// Deal with item events (generated by the ScrollComboBox<String>boxs)
	public void itemStateChanged(ItemEvent ie) {
	}

	// Deal with Action events (button pushes)
	public void actionPerformed(ActionEvent ae) {
		boolean tabPanePossibleChange = false;
		boolean soloMuterPossibleChange = false;

		System.out.println("Processing.. ::" + ae.getActionCommand() + "::");

		InstComboBox.BANNED_INSTS.clear();
		InstComboBox.BANNED_INSTS.addAll(Arrays.asList(bannedInsts.getText().split(",")));

		{
			int inst = melodyPanels.get(0).getInstrument();
			melodyPanels.get(0).getInstrumentBox().initInstPool(melodyPanels.get(0).getInstPool());
			melodyPanels.get(0).getInstrumentBox().setInstrument(inst);
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
			melodyPanels.forEach(e -> e.getInstrumentBox().initInstPool(POOL.PLUCK));
			bassPanel.getInstrumentBox().initInstPool(POOL.BASS);
		}

		if (ae.getActionCommand() == "RandStrums" || (ae.getActionCommand() == "Compose"
				& randomizeChordStrumsOnCompose.isSelected())) {
			for (ChordPanel p : chordPanels) {
				p.setStrum(selectRandomStrumByStruminess());
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
							.valueOf(randomChordSustainChance.getInt())) ? POOL.CHORD : POOL.PLUCK;

					cp.getInstrumentBox().initInstPool(pool);
					cp.setInstPool(pool);

					cp.setInstrument(cp.getInstrumentBox().getRandomInstrument());
				}
			}
			for (ArpPanel ap : arpPanels) {
				if (!ap.getLockInst()) {
					ap.getInstrumentBox()
							.setInstrument(ap.getInstrumentBox().getRandomInstrument());
				}
			}
			if (!melodyPanels.isEmpty()) {
				int inst = melodyPanels.get(0).getInstrumentBox().getRandomInstrument();
				for (MelodyPanel mp : melodyPanels) {
					if (!mp.getLockInst()) {
						mp.getInstrumentBox().setInstrument(inst);
					}
				}
			}


			if (!bassPanel.getLockInst()) {

				bassPanel.getInstrumentBox()
						.setInstrument(bassPanel.getInstrumentBox().getRandomInstrument());
			}
		}

		cpSm = new HashMap<>();
		apSm = new HashMap<>();
		dpSm = new HashMap<>();
		if (ae.getActionCommand() == "Compose" || ae.getActionCommand() == "Regenerate"
				|| (ae.getActionCommand().startsWith("Rand")
						&& ae.getActionCommand().charAt(4) != 'o')) {
			for (InstPanel ip : chordPanels) {
				cpSm.put(ip.getPanelOrder(), ip.getSoloMuter());
			}
			for (InstPanel ip : arpPanels) {
				apSm.put(ip.getPanelOrder(), ip.getSoloMuter());
			}
			for (InstPanel ip : drumPanels) {
				dpSm.put(ip.getPanelOrder(), ip.getSoloMuter());
			}
			soloMuterPossibleChange = true;
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
			tabPanePossibleChange = true;
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
			tabPanePossibleChange = true;
		}

		if (ae.getActionCommand() == "RandDrums" || (ae.getActionCommand() == "Compose"
				&& addDrums.isSelected() && randomDrumsGenerateOnCompose.isSelected())) {
			/*List<Integer> drumInsts = drumPanels.stream().map(e -> e.getPitch())
					.collect(Collectors.toList());*/

			if (randomDrumsOverrandomize.isSelected()) {
				createRandomDrumPanels(Integer.valueOf(randomDrumsToGenerate.getText()), false);
			} else {
				createBlueprintedDrumPanels(Integer.valueOf(randomDrumsToGenerate.getText()),
						false);
			}

			/*for (int i = 0; i < drumInsts.size() && i < drumPanels.size(); i++) {
				drumPanels.get(i).setPitch(drumInsts.get(i));
			}*/

			tabPanePossibleChange = true;
		}

		if (ae.getActionCommand() == "Compose" || ae.getActionCommand() == "Regenerate"
				|| (ae.getActionCommand().startsWith("Rand")
						&& ae.getActionCommand().charAt(4) != 'o')) {
			for (InstPanel ip : chordPanels) {
				if (cpSm.containsKey(ip.getPanelOrder())) {
					ip.setSoloMuter(cpSm.get(ip.getPanelOrder()));
				}
			}
			for (InstPanel ip : arpPanels) {
				if (apSm.containsKey(ip.getPanelOrder())) {
					ip.setSoloMuter(apSm.get(ip.getPanelOrder()));
				}
			}
			for (InstPanel ip : drumPanels) {
				if (dpSm.containsKey(ip.getPanelOrder())) {
					ip.setSoloMuter(dpSm.get(ip.getPanelOrder()));
				}
			}
		}

		realBpm = Double.valueOf(mainBpm.getInt());
		if (ae.getActionCommand() == "RandomizeBpm"
				|| (ae.getActionCommand() == "Compose" && randomizeBpmOnCompose.isSelected())) {
			Random instGen = new Random();

			int bpm = instGen.nextInt(1 + bpmHigh.getInt() - bpmLow.getInt()) + bpmLow.getInt();
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
			mainBpm.setInt(bpm);
			mainBpm.getKnob().setMin(bpmLow.getInt());
			mainBpm.getKnob().setMax(bpmHigh.getInt());
			realBpm = bpm;
		}

		if (ae.getActionCommand() == "RandomizeTranspose" || (ae.getActionCommand() == "Compose"
				&& randomizeTransposeOnCompose.isSelected())) {
			Random instGen = new Random();
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
					List<String> prettyChords = MidiGenerator.chordInts;
					currentChords.setText("Chords:[" + StringUtils.join(prettyChords, ",") + "]");
					//sizeRespectingPack();
					repaint();
				}
			};
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
			worker.execute();

		}

		if (ae.getActionCommand() == "StopMidi") {
			stopMidi();
		}

		if (ae.getActionCommand() == "StartMidi") {
			startMidi();
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
				System.out.println("You cancelled the choice");
			else {
				System.out.println("You chose " + filename);
				try {
					unmarshallDrums(files[0]);
				} catch (JAXBException |

						IOException e) {
					// TODO Auto-generated catch block
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
			randomizeMelodySeeds();
		}

		if (ae.getActionCommand() == "ClearMelody") {
			melodyPanels.forEach(e -> e.setPatternSeed(0));
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
			String str = StringUtils.join(MidiGenerator.chordInts, ",");
			userChords.setText(str);
			System.out.println("Copied chords: " + str);
		}

		if (ae.getActionCommand() == "ClearSeed") {
			randomSeed.setText("0");
			chordPanels.forEach(e -> e.setPatternSeed(0));
			arpPanels.forEach(e -> e.setPatternSeed(0));
			drumPanels.forEach(e -> e.setPatternSeed(0));
			melodyPanels.forEach(e -> e.setPatternSeed(0));
			bassPanel.setPatternSeed(0);
			arrangementSeed.setText("0");
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
			createBlueprintedDrumPanels(drumPanels.size() + 1, true);
			randomDrumsToGenerate.setText("" + drumPanels.size());
			//sizeRespectingPack();
			repaint();
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
		}

		if (ae.getActionCommand().startsWith("RemoveDrum,")) {
			String drumNumber = ae.getActionCommand().split(",")[1];

			removeInstPanel(4, Integer.valueOf(drumNumber), true);
			randomDrumsToGenerate.setText("" + drumPanels.size());
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
		}


		if (ae.getActionCommand() == "ClearChordPatterns")

		{
			for (ChordPanel cp : chordPanels) {
				cp.setPatternSeed(0);
				cp.setPattern(RhythmPattern.FULL);

			}
		}

		if (ae.getActionCommand() == "AddChord") {
			//addChordPanelToLayout();
			createRandomChordPanels(chordPanels.size() + 1, true);
			randomChordsToGenerate.setText("" + chordPanels.size());

			//sizeRespectingPack();
			repaint();
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
		}

		if (ae.getActionCommand().startsWith("RemoveChord,")) {
			String chordNumber = ae.getActionCommand().split(",")[1];

			removeInstPanel(2, Integer.valueOf(chordNumber), true);
			randomChordsToGenerate.setText("" + chordPanels.size());
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
		}

		if (ae.getActionCommand() == "AddArp")

		{
			//addArpPanelToLayout();
			createRandomArpPanels(arpPanels.size() + 1, true);
			randomArpsToGenerate.setText("" + arpPanels.size());
			//sizeRespectingPack();
			repaint();
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
		}

		if (ae.getActionCommand().startsWith("RemoveArp,")) {
			String arpNumber = ae.getActionCommand().split(",")[1];
			removeInstPanel(3, Integer.valueOf(arpNumber), true);
			randomArpsToGenerate.setText("" + arpPanels.size());
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
		}

		if (ae.getActionCommand() == "ClearArpPatterns") {
			for (ArpPanel ap : arpPanels) {
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

		if (ae.getActionCommand() == "SwitchBigMode") {
			switchBigMonitorMode();
		}

		if (ae.getActionCommand() == "SwitchDarkMode") {
			switchDarkMode();
		}

		if (ae.getActionCommand() == "ToggleAdv") {
			switchFullMode();
		}

		if (ae.getActionCommand() == "ShowAboutPopup") {
			openAboutPopup();
		}

		if (ae.getActionCommand() == "ShowHelpPopup") {
			openHelpPopup();
		}

		if (ae.getActionCommand() == "ShowDebugPopup") {
			openDebugConsole();
		}

		if (ae.getActionCommand() == "ShowExtraPopup") {
			openExtraSettingsPopup();
		}

		if (ae.getActionCommand() == "CopyPart") {

			JButton source = (JButton) ae.getSource();
			InstPanel sourcePanel = (InstPanel) source.getParent();
			InstPart part = null;

			part = sourcePanel.toInstPart(lastRandomSeed);
			InstPanel newPanel = addInstPanelToLayout(instrumentTabPane.getSelectedIndex());
			int order = newPanel.getPanelOrder();
			newPanel.setFromInstPart(part);
			newPanel.setPanelOrder(order);

			switch (instrumentTabPane.getSelectedIndex()) {
			case 2:
				newPanel.setMidiChannel(2 + (newPanel.getPanelOrder() - 1) % 7);
				break;
			case 3:
				newPanel.setMidiChannel(11 + (newPanel.getPanelOrder() - 1) % 5);
				break;
			default:
				break;
			}
			soloMuterPossibleChange = true;
			tabPanePossibleChange = true;
			System.out.println("Set sequencer solo: " + sourcePanel.getMidiChannel());
		}
		// recalcs 
		if (tabPanePossibleChange) {
			recalculateTabPaneCounts();
		}
		if (soloMuterPossibleChange) {
			for (int i = 0; i < 5; i++) {
				recalcGroupSolo(i);
				recalcGroupMute(i);
			}
			recalcGlobals();
		}

		System.out.println("Finished.. ::" + ae.getActionCommand() + "::");
		messageLabel.setText("::" + ae.getActionCommand() + "::");
	}

	private void startMidi() {
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

	private void stopMidi() {
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

	public static void unsoloAllTracks(boolean resetButtons) {
		if (!sequenceReady())
			return;

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
		if (!sequenceReady())
			return;
		groupSm.unsolo();
		List<? extends InstPanel> groupList = getInstList(groupSm.inst);
		for (InstPanel ip : groupList) {
			ip.getSoloMuter().unsolo();
			sequencer.setTrackSolo(ip.getSequenceTrack(), false);
		}
	}

	public static void soloGroup(SoloMuter groupSm) {
		if (!sequenceReady())
			return;
		groupSm.solo();
		List<? extends InstPanel> groupList = getInstList(groupSm.inst);
		for (InstPanel ip : groupList) {
			ip.getSoloMuter().solo();
			sequencer.setTrackSolo(ip.getSequenceTrack(), true);
		}
	}

	public static void unmuteAllTracks(boolean resetButtons) {
		if (!sequenceReady())
			return;

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
		if (!sequenceReady())
			return;
		groupSm.unmute();
		List<? extends InstPanel> groupList = getInstList(groupSm.inst);
		for (InstPanel ip : groupList) {
			ip.getSoloMuter().unmute();
			sequencer.setTrackMute(ip.getSequenceTrack(), false);
		}
	}

	public static void muteGroup(SoloMuter groupSm) {
		if (!sequenceReady())
			return;
		groupSm.mute();
		List<? extends InstPanel> groupList = getInstList(groupSm.inst);
		for (InstPanel ip : groupList) {
			ip.getSoloMuter().mute();
			sequencer.setTrackMute(ip.getSequenceTrack(), true);
		}
	}

	public static boolean sequenceReady() {
		return VibeComposerGUI.sequencer != null && VibeComposerGUI.sequencer.isOpen();
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
	}

	public void fillUserParameters() {
		try {
			MidiGenerator.DISPLAY_SCORE = showScore.isSelected();
			MidiGenerator.showScoreMode = showScorePicker.getSelectedIndex();
			MidiGenerator.COLLAPSE_DRUM_TRACKS = collapseDrumTracks.isSelected();
			MidiGenerator.noteMultiplier = elongateMidi.getInt();
			MidiGenerator.recalculateDurations();

			if (loopBeat.isSelected()) {
				MidiGenerator.START_TIME_DELAY = 0.001;
			} else {
				MidiGenerator.START_TIME_DELAY = MidiGenerator.Durations.EIGHTH_NOTE;
			}

			MidiGenerator.FIRST_CHORD = chordSelect((String) firstChordSelection.getSelectedItem());
			MidiGenerator.LAST_CHORD = chordSelect((String) lastChordSelection.getSelectedItem());

			if (userChordsEnabled.isSelected() && !userChords.getText().contains("R")) {
				System.out.println("Trying to solve user chords!");
				String text = userChords.getText().replaceAll(" ", "");
				userChords.setText(text);
				String[] userChordsSplit = text.split(",");
				System.out.println(StringUtils.join(userChordsSplit, ";"));

				String[] userChordsDurationsSplit = userChordsDurations.getText().split(",");
				if (userChordsSplit.length != userChordsDurationsSplit.length) {
					List<Integer> durations = IntStream.iterate(2, n -> n)
							.limit(userChordsSplit.length).boxed().collect(Collectors.toList());
					userChordsDurations.setText(StringUtils.join(durations, ","));
					userChordsDurationsSplit = userChordsDurations.getText().split(",");
				}
				try {

					if (userChordsSplit.length == userChordsDurationsSplit.length) {

						List<String> userChordsParsed = new ArrayList<>();
						List<Double> userChordsDurationsParsed = new ArrayList<>();
						for (int i = 0; i < userChordsDurationsSplit.length; i++) {
							if (MidiUtils.chordsMap.containsKey(userChordsSplit[i])) {
								userChordsParsed.add(userChordsSplit[i]);
							} else {
								int[] interval = MidiUtils.getSpelledChord(userChordsSplit[i]);
								if (interval != null) {
									userChordsParsed.add(userChordsSplit[i]);
								}
							}

							userChordsDurationsParsed
									.add(Double.valueOf(userChordsDurationsSplit[i])
											* elongateMidi.getInt());
						}
						if (userChordsParsed.size() == userChordsDurationsParsed.size()) {
							MidiGenerator.userChords = userChordsParsed;
							MidiGenerator.userChordsDurations = userChordsDurationsParsed;
						} else {
							MidiGenerator.userChords.clear();
							MidiGenerator.userChordsDurations.clear();
						}

						System.out.println(userChordsDurationsParsed.toString());
					} else {
						MidiGenerator.userChords.clear();
						MidiGenerator.userChordsDurations.clear();
					}
				} catch (Exception e) {
					System.out.println("Bad user input in custom chords/durations!\n");
					e.printStackTrace();
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
		chordSettings.setShiftChance(Integer.valueOf(randomChordShiftChance.getInt()));
		chordSettings.setSustainChance(Integer.valueOf(randomChordSustainChance.getInt()));
		chordSettings.setFlattenVoicingChance(Integer.valueOf(randomChordVoicingChance.getInt()));
		chordSettings.setUseShortening(randomChordSustainUseShortening.isSelected());
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
		randomChordSustainUseShortening.setSelected(settings.isUseShortening());
	}

	public String chordSelect(String s) {
		String chord = null;
		if (s == "R") {
			chord = null;
		} else if (s == "I") {
			chord = "C";
		} else if (s == "IV") {
			chord = "F";
		} else if (s == "V") {
			chord = "G";
		} else if (s == "vi") {
			chord = "Am";
		} else {
			chord = null;
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

	public void marshalDrums(String path) throws JAXBException, IOException {
		SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();
		f.applyPattern("yyMMdd-hh-mm-ss");
		JAXBContext context = JAXBContext.newInstance(DrumPartsWrapper.class);
		Marshaller mar = context.createMarshaller();
		mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		DrumPartsWrapper wrapper = new DrumPartsWrapper();
		wrapper.setDrumParts((List<DrumPart>) (List<?>) getInstPartsFromInstPanels(4, false));
		mar.marshal(wrapper, new File(path.substring(0, path.length() - 4) + "-drumParts.xml"));
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

	public void marshal(String path) throws JAXBException, IOException {
		SimpleDateFormat f = (SimpleDateFormat) SimpleDateFormat.getInstance();
		f.applyPattern("yyMMdd-hh-mm-ss");
		JAXBContext context = JAXBContext.newInstance(GUIConfig.class);
		Marshaller mar = context.createMarshaller();
		mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		mar.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "");
		mar.marshal(guiConfig, new File(path.substring(0, path.length() - 4) + "-guiConfig.xml"));
	}

	public GUIConfig unmarshall(File f) throws JAXBException, IOException {
		JAXBContext context = JAXBContext.newInstance(GUIConfig.class);
		return (GUIConfig) context.createUnmarshaller().unmarshal(new FileReader(f));
	}

	public GUIConfig copyGUItoConfig() {
		// seed
		//GUIConfig gc = new GUIConfig();

		guiConfig.setRandomSeed(lastRandomSeed);

		// arrangement
		if (!useArrangement.isSelected()) {
			arrangement.setPreviewChorus(true);
		} else {
			arrangement.setPreviewChorus(false);
		}
		arrangement.setFromModel(scrollableArrangementTable);
		boolean overrideSuccessful = actualArrangement.setFromActualTable(
				scrollableArrangementActualTable, false) && arrangementManualOverride.isSelected();
		System.out.println("OVERRIDE OK?: " + overrideSuccessful);
		if (overrideSuccessful) {
			arrangement.setOverridden(true);
		} else {
			arrangement.setOverridden(false);
		}

		arrangement.setSeed((Integer.valueOf(arrangementSeed.getText()) != 0)
				? Integer.valueOf(arrangementSeed.getText())
				: lastRandomSeed);
		actualArrangement.setSeed((Integer.valueOf(arrangementSeed.getText()) != 0)
				? Integer.valueOf(arrangementSeed.getText())
				: lastRandomSeed);

		guiConfig.setArrangement(arrangement);
		guiConfig.setActualArrangement(actualArrangement);
		guiConfig.setArrangementVariationChance(
				Integer.valueOf(arrangementVariationChance.getInt()));
		guiConfig.setArrangementPartVariationChance(
				Integer.valueOf(arrangementPartVariationChance.getInt()));
		guiConfig.setArrangementReduceDrumVelocityFromSectionChance(
				arrangementAffectsDrumVelocity.isSelected());
		guiConfig.setArrangementEnabled(useArrangement.isSelected());

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
		guiConfig.setBpm(Double.valueOf(mainBpm.getInt()));
		guiConfig.setArpAffectsBpm(arpAffectsBpm.isSelected());
		guiConfig.setDoubledDurations(useDoubledDurations.isSelected());
		guiConfig.setAllowChordRepeats(allowChordRepeats.isSelected());

		// parts
		guiConfig.setMelodyParts((List<MelodyPart>) (List<?>) getInstPartsFromInstPanels(0, false));
		guiConfig.setBassPart(bassPanel.toBassPart(lastRandomSeed));

		guiConfig.setChordsEnable(addChords.isSelected());
		guiConfig.setArpsEnable(addArps.isSelected());
		guiConfig.setDrumsEnable(addDrums.isSelected());

		guiConfig.setChordParts((List<ChordPart>) (List<?>) getInstPartsFromInstPanels(2, false));
		guiConfig.setArpParts((List<ArpPart>) (List<?>) getInstPartsFromInstPanels(3, false));
		guiConfig.setDrumParts((List<DrumPart>) (List<?>) getInstPartsFromInstPanels(4, false));

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
		guiConfig.setMelodyBasicChordsOnly(melodyBasicChordsOnly.isSelected());


		// chords
		guiConfig.setFirstChord((String) firstChordSelection.getSelectedItem());
		guiConfig.setLastChord((String) lastChordSelection.getSelectedItem());
		guiConfig.setCustomChordsEnabled(userChordsEnabled.isSelected());
		guiConfig.setCustomChords(StringUtils.join(MidiGenerator.chordInts, ","));
		guiConfig.setCustomChordDurations(userChordsDurations.getText());
		guiConfig.setSpiceChance(Integer.valueOf(spiceChance.getInt()));
		guiConfig.setDimAugEnabled(spiceAllowDimAug.isSelected());
		guiConfig.setEnable9th13th(spiceAllow9th13th.isSelected());
		guiConfig.setSpiceFlattenBigChords(spiceFlattenBigChords.isSelected());
		guiConfig.setChordSlashChance(Integer.valueOf(chordSlashChance.getInt()));

		// arps
		guiConfig.setUseOctaveAdjustments(randomArpUseOctaveAdjustments.isSelected());
		guiConfig.setMaxArpSwing(Integer.valueOf(randomArpMaxSwing.getInt()));

		// drums
		boolean isCustomMidiDevice = midiMode.isSelected()
				&& !((String) midiModeDevices.getSelectedItem()).contains("ervill");
		guiConfig.setDrumCustomMapping(drumCustomMapping.isSelected() && isCustomMidiDevice);
		guiConfig.setDrumCustomMappingNumbers(drumCustomMappingNumbers.getText());

		return guiConfig;
	}

	public void copyConfigToGUI() {
		// seed
		randomSeed.setText(String.valueOf(guiConfig.getRandomSeed()));
		lastRandomSeed = (int) guiConfig.getRandomSeed();

		// arrangement
		arrangement = guiConfig.getArrangement();
		actualArrangement = guiConfig.getActualArrangement();
		scrollableArrangementTable.setModel(arrangement.convertToTableModel());
		setActualModel(actualArrangement.convertToActualTableModel());
		refreshVariationPopupButtons(actualArrangement.getSections().size());

		arrangementVariationChance.setInt(guiConfig.getArrangementVariationChance());
		arrangementPartVariationChance.setInt(guiConfig.getArrangementPartVariationChance());
		arrangementAffectsDrumVelocity
				.setSelected(guiConfig.isArrangementReduceDrumVelocityFromSectionChance());
		arrangementSeed.setText("" + arrangement.getSeed());
		useArrangement.setSelected(guiConfig.isArrangementEnabled());

		// macro
		scaleMode.setSelectedItem(guiConfig.getScaleMode().toString());
		soundbankFilename.setText(guiConfig.getSoundbankName());
		pieceLength.setText(String.valueOf(guiConfig.getPieceLength()));
		setFixedLengthChords(guiConfig.getFixedDuration());

		transposeScore.setInt(guiConfig.getTranspose());
		mainBpm.setInt((int) Math.round(guiConfig.getBpm()));

		arpAffectsBpm.setSelected(guiConfig.isArpAffectsBpm());
		useDoubledDurations.setSelected(guiConfig.isDoubledDurations());
		allowChordRepeats.setSelected(guiConfig.isAllowChordRepeats());

		// parts

		bassPanel.setFromInstPart(guiConfig.getBassPart());

		addChords.setSelected(guiConfig.isChordsEnable());
		addArps.setSelected(guiConfig.isArpsEnable());
		addDrums.setSelected(guiConfig.isDrumsEnable());

		drumCustomMapping.setSelected(guiConfig.isDrumCustomMapping());
		drumCustomMappingNumbers.setText(guiConfig.getDrumCustomMappingNumbers());

		recreateInstPanelsFromInstParts(0, guiConfig.getMelodyParts());
		recreateInstPanelsFromInstParts(2, guiConfig.getChordParts());
		recreateInstPanelsFromInstParts(3, guiConfig.getArpParts());
		recreateInstPanelsFromInstParts(4, guiConfig.getDrumParts());
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
		melodyBasicChordsOnly.setSelected(guiConfig.isMelodyBasicChordsOnly());

		// chords
		spiceChance.setInt(guiConfig.getSpiceChance());
		spiceAllowDimAug.setSelected(guiConfig.isDimAugEnabled());
		spiceAllow9th13th.setSelected(guiConfig.isEnable9th13th());
		spiceFlattenBigChords.setSelected(guiConfig.isSpiceFlattenBigChords());
		chordSlashChance.setInt(guiConfig.getChordSlashChance());
		firstChordSelection.setSelectedItem(guiConfig.getFirstChord());
		lastChordSelection.setSelectedItem(guiConfig.getLastChord());
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
		List<InstPanel> panels = (List<InstPanel>) getInstList(inst);
		int panelOrder = (panels.size() > 0) ? getValidPanelNumber(panels) : 1;

		InstPanel ip = null;
		switch (inst) {
		case 0:
			ip = new MelodyPanel(this);
			break;
		case 1:
			ip = new BassPanel(this);
			break;
		case 2:
			ip = new ChordPanel(this);
			break;
		case 3:
			ip = new ArpPanel(this);
			break;
		case 4:
			ip = new DrumPanel(this);
			break;
		}
		ip.getToggleableComponents().forEach(e -> e.setVisible(isFullMode));
		ip.setPanelOrder(panelOrder);

		if (inst == 4) {
			ip.getSoloMuter().setVisible(!collapseDrumTracks.isSelected());
		}
		panels.add(ip);
		((JPanel) getInstPane(inst).getViewport().getView()).add(ip, panelOrder + 1);
		return ip;
	}

	private void removeInstPanel(int inst, int order, boolean singleRemove) {
		List<? extends InstPanel> panels = getInstList(inst);
		InstPanel panel = getPanelByOrder(order, panels);
		((JPanel) getInstPane(inst).getViewport().getView()).remove(panel);

		panels.remove(panel);

		if (singleRemove) {
			repaint();
		}
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

	private void recreateInstPanelsFromInstParts(int inst, List<? extends InstPart> parts) {
		List<? extends InstPanel> panels = getInstList(inst);
		JScrollPane pane = getInstPane(inst);
		for (InstPanel panel : panels) {
			((JPanel) pane.getViewport().getView()).remove(panel);
		}
		panels.clear();
		for (InstPart part : parts) {
			InstPanel panel = addInstPanelToLayout(inst);
			panel.setFromInstPart(part);
		}

		repaint();
	}

	private void createBlueprintedDrumPanels(int panelCount, boolean onlyAdd) {
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
		if (onlyAdd && drumPanels.size() > 0) {
			Optional<Integer> existingSwing = drumPanels.stream()
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
			pitches.add(MidiUtils.getInstByIndex(drumPanelGenerator.nextInt(127), POOL.DRUM));
		}
		Collections.sort(pitches);
		if (!onlyAdd && pitches.size() > 2) {
			pitches.set(0, 35);
			pitches.set(1, 36);
			pitches.set(2, 38);
		}
		if (pitches.stream().filter(e -> e == 38 || e == 39 || e == 40).count() > 1) {
			for (int i = 3; i < pitches.size(); i++) {
				int e = pitches.get(i);
				if (e == 38 || e == 39 || e == 40) {
					pitches.set(i, i % 2 == 0 ? 42 : 44);
				}
			}
		}
		Collections.sort(pitches);
		for (int i = 0; i < panelCount; i++) {
			DrumPart dpart = DrumDefaults.getDrumFromInstrument(pitches.get(i));
			int order = DrumDefaults.getOrder(dpart.getInstrument());
			DrumSettings settings = DrumDefaults.drumSettings[order];
			settings.applyToDrumPart(dpart, lastRandomSeed);
			dpart.setCustomPattern(null);
			DrumPanel dp = (DrumPanel) addInstPanelToLayout(4);

			dpart.setOrder(dp.getPanelOrder());
			dp.setFromInstPart(dpart);

			dp.setHitsPerPattern(dp.getHitsPerPattern() * randomDrumHitsMultiplierLastState);

			if (settings.isSwingable()) {
				dp.setDelay(slide);
				dp.setSwingPercent(swingPercent);
			}

			if (randomChordUseChordFill.isSelected() && settings.isFillable()) {
				dp.setChordSpanFill(ChordSpanFill.getWeighted(drumPanelGenerator.nextInt(100)));
			}

			if (settings.isDynamicable()) {
				dp.setIsVelocityPattern(
						drumPanelGenerator.nextInt(100) < randomDrumVelocityPatternChance.getInt());
			}

			if (settings.isVariableShift()
					&& drumPanelGenerator.nextInt(100) < randomDrumShiftChance.getInt()) {
				// settings set the maximum shift, this sets 0 - max randomly
				dp.setPatternShift(drumPanelGenerator.nextInt(dp.getPatternShift() + 1));
			}

			if (dp.getPatternShift() > 0) {
				dp.getComboPanel().reapplyShift();
			}

			dp.getComboPanel().reapplyHits();

		}

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
		if (onlyAdd && drumPanels.size() > 0) {
			Optional<Integer> existingSwing = drumPanels.stream()
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
			pitches.add(MidiUtils.getInstByIndex(drumPanelGenerator.nextInt(127), POOL.DRUM));
		}
		Collections.sort(pitches);
		if (!onlyAdd && pitches.size() > 3) {
			pitches.set(0, 35);
			pitches.set(1, 36);
			pitches.set(2, 38);

		}
		Collections.sort(pitches);
		for (int i = 0; i < panelCount; i++) {
			DrumPanel dp = (DrumPanel) addInstPanelToLayout(4);
			dp.setInstrument(pitches.get(i));
			//dp.setPitch(32 + drumPanelGenerator.nextInt(33));


			dp.setChordSpan(drumPanelGenerator.nextInt(2) + 1);
			int patternOrder = 0;
			// use pattern in half the cases if checkbox selected

			if (randomDrumPattern.isSelected()) {
				int[] patternWeights = { 35, 60, 80, 90, 90, 100 };
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

			dp.setHitsPerPattern(hits * randomDrumHitsMultiplierLastState);

			int adjustVelocity = -1 * dp.getHitsPerPattern() / dp.getChordSpan();


			dp.setPattern(RhythmPattern.values()[patternOrder]);
			int velocityMin = drumPanelGenerator.nextInt(30) + 50 + adjustVelocity;

			dp.setVelocityMax(1 + velocityMin + drumPanelGenerator.nextInt(25));
			dp.setVelocityMin(velocityMin);

			if (patternOrder > 0) {
				dp.setPauseChance(drumPanelGenerator.nextInt(5) + 0);
			} else {
				dp.setPauseChance(drumPanelGenerator.nextInt(40) + 40);
			}

			// punchy drums - kicks, snares
			if (PUNCHY_DRUMS.contains(dp.getInstrument())) {
				adjustVelocity += 15;
				dp.setExceptionChance(drumPanelGenerator.nextInt(3));
				dp.setUseMelodyNotePattern(drumPanelGenerator.nextInt(100) < 75);
			} else {
				dp.setDelay(slide);
				dp.setSwingPercent(swingPercent);
				dp.setExceptionChance(drumPanelGenerator.nextInt(10));
			}

			if (randomChordUseChordFill.isSelected()) {
				dp.setChordSpanFill(ChordSpanFill.getWeighted(drumPanelGenerator.nextInt(100)));
			}

			dp.setIsVelocityPattern(drumPanelGenerator.nextInt(100) < Integer
					.valueOf(randomDrumVelocityPatternChance.getInt()));

			if (drumPanelGenerator.nextInt(100) < Integer.valueOf(randomDrumShiftChance.getInt())
					&& patternOrder > 0) {
				dp.setPatternShift(
						drumPanelGenerator.nextInt(dp.getPattern().pattern.length - 1) + 1);
				dp.getComboPanel().reapplyShift();
			}

			dp.getComboPanel().reapplyHits();

		}

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
			ChordPanel cp = (ChordPanel) addInstPanelToLayout(2);
			MidiUtils.POOL pool = (chordPanelGenerator.nextInt(100) < Integer
					.valueOf(randomChordSustainChance.getInt())) ? POOL.CHORD : POOL.PLUCK;

			cp.getInstrumentBox().initInstPool(pool);
			cp.setInstPool(pool);

			cp.setInstrument(cp.getInstrumentBox().getRandomInstrument());
			cp.setTransitionChance(chordPanelGenerator
					.nextInt(Integer.valueOf(randomChordMaxSplitChance.getInt() + 1)));
			cp.setTransitionSplit(
					(getRandomFromArray(chordPanelGenerator, MILISECOND_ARRAY_SPLIT, 0)));
			cp.setTranspose((chordPanelGenerator.nextInt(3) - 1) * 12);

			cp.setStrum(selectRandomStrumByStruminess());
			if (cp.getStretchEnabled() && cp.getChordNotesStretch() > 4 && cp.getStrum() > 499) {
				cp.setStrum(cp.getStrum() / 2);
			}
			cp.setDelay((getRandomFromArray(chordPanelGenerator, MILISECOND_ARRAY_DELAY, 0)));

			if (randomChordUseChordFill.isSelected()) {
				cp.setChordSpanFill(ChordSpanFill.getWeighted(chordPanelGenerator.nextInt(100)));
			}
			int patternOrder = 0;
			// use pattern in 20% of the cases if checkbox selected
			if (chordPanelGenerator.nextInt(100) < 20) {
				if (randomChordPattern.isSelected()) {
					patternOrder = chordPanelGenerator.nextInt(RhythmPattern.values().length);
					if (cp.getStrum() > 251) {
						cp.setStrum(cp.getStrum() / 2);
					}
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


			cp.setVelocityMax(randomChordMaxVel.getInt());
			cp.setVelocityMin(randomChordMinVel.getInt());


			if (chordPanelGenerator.nextInt(100) < Integer.valueOf(randomChordShiftChance.getInt())
					&& patternOrder > 0) {
				cp.setPatternShift(
						chordPanelGenerator.nextInt(cp.getPattern().pattern.length - 1) + 1);
			}

			cp.setMidiChannel(11 + (cp.getPanelOrder() - 1) % 5);

		}

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
			if (randomArpLimitPowerOfTwo.isSelected()) {
				fixedHitsGenerated = getRandomFromArray(instGen, new int[] { 2, 4, 4, 8, 8 }, 0);
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

			randomArpHitsPicker.setSelectedItem("" + fixedHitsGenerated);
		}

		int fixedInstrument = -1;
		int fixedHits = -1;

		if (arpCopyMelodyInst.isSelected() && !melodyPanels.get(0).getMuteInst()) {
			fixedInstrument = melodyPanels.get(0).getInstrument();
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

			ArpPanel ap = (ArpPanel) addInstPanelToLayout(3);


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
							value = getRandomFromArray(instGen, new int[] { 2, 4, 4, 8, 8 }, 0);
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
				if (arpCopyMelodyInst.isSelected() && !melodyPanels.get(0).getMuteInst()) {
					ap.setInstrument(fixedInstrument);
				}
			} else {
				ap.setTranspose((arpPanelGenerator.nextInt(3) - 1) * 12);
			}
			ap.setPauseChance(arpPanelGenerator.nextInt(50));
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


			ap.setVelocityMax(randomArpMaxVel.getInt());
			ap.setVelocityMin(randomArpMinVel.getInt());

			if (arpPanelGenerator.nextInt(100) < Integer.valueOf(arpShiftChance.getInt())
					&& patternOrder > 0) {
				ap.setPatternShift(
						arpPanelGenerator.nextInt(ap.getPattern().pattern.length - 1) + 1);
			}

			if (arpPanelGenerator.nextBoolean()) {
				int arpPatternOrder = 0;
				int[] patternWeights = { 60, 70, 80, 90, 100 };
				int randomWeight = arpPanelGenerator.nextInt(100);
				for (int j = 0; j < patternWeights.length; j++) {
					if (randomWeight < patternWeights[j]) {
						arpPatternOrder = j;
						break;
					}
				}
				ap.setArpPattern(ArpPattern.values()[arpPatternOrder]);
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

	public int selectRandomStrumByStruminess() {
		return singleWeightedSelectFromArray(MILISECOND_ARRAY_STRUM, randomChordStruminess.getInt(),
				1);
	}

	public int singleWeightedSelectFromArray(int[] oldArray, int weight, int from) {
		int[] array = Arrays.copyOfRange(oldArray, from, oldArray.length);
		//System.out.println("New array: " + Arrays.toString(array));
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
		//System.out.println("Total: " + totalWeight + ", Target: " + targetWeight);
		// -> strength of reduction depends on how far from ends
		totalWeight = 0;

		//System.out.println("New array: " + Arrays.toString(realWeights));
		for (int i = 0; i < array.length; i++) {
			totalWeight += realWeights[i];
			if (totalWeight >= targetWeight) {
				return array[i];
			}
		}
		return array[array.length - 1];

	}
}
