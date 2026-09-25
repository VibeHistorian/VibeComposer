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
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.Components.*;
import org.vibehistorian.vibecomposer.Enums.ArpPattern;
import org.vibehistorian.vibecomposer.Enums.StrumType;
import org.vibehistorian.vibecomposer.Helpers.CheckBoxIcon;
import org.vibehistorian.vibecomposer.Helpers.FileTransferHandler;
import org.vibehistorian.vibecomposer.Helpers.MidiHandler;
import org.vibehistorian.vibecomposer.InstUtils.POOL;
import org.vibehistorian.vibecomposer.MidiGenerator.Durations;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.Panels.*;
import org.vibehistorian.vibecomposer.Panels.SoloMuter.State;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.Wrappers.ArpPartsWrapper;
import org.vibehistorian.vibecomposer.Parts.Wrappers.InstPartsWrapper;
import org.vibehistorian.vibecomposer.Popups.AboutPopup;
import org.vibehistorian.vibecomposer.Popups.ApplyCustomSectionPopup;
import org.vibehistorian.vibecomposer.Popups.DebugConsole;
import org.vibehistorian.vibecomposer.Popups.DrumLoopPopup;
import org.vibehistorian.vibecomposer.Popups.ExtraSettingsPopup;
import org.vibehistorian.vibecomposer.Popups.HelpPopup;
import org.vibehistorian.vibecomposer.Popups.TemporaryInfoPopup;

import javax.sound.midi.*;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.BevelBorder;
import javax.swing.plaf.ColorUIResource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.vibehistorian.vibecomposer.ApplicationSessionState.*;
import static org.vibehistorian.vibecomposer.Constants.instNames;
import static org.vibehistorian.vibecomposer.GUIConstants.COMPOSE_COLOR;
import static org.vibehistorian.vibecomposer.GUIConstants.DEFAULT_HEIGHT;
import static org.vibehistorian.vibecomposer.GUIConstants.DEFAULT_WIDTH;
import static org.vibehistorian.vibecomposer.GenerationGUI.*;
import static org.vibehistorian.vibecomposer.PlaybackState.*;
import static org.vibehistorian.vibecomposer.SoloMuteState.globalSoloMuter;
import static org.vibehistorian.vibecomposer.SoloMuteState.groupSoloMuters;
import static org.vibehistorian.vibecomposer.SoloMuteState.needToRecalculateSoloMuters;
import static org.vibehistorian.vibecomposer.SoloMuteState.needToRecalculateSoloMutersAfterSequenceGenerated;
import static org.vibehistorian.vibecomposer.UITheme.*;

// main class
public class VibeComposerGUI extends JFrame
		implements ActionListener, ItemListener, WindowListener {

	private static final long serialVersionUID = -677536546851756969L;

	// COLORS

	Color messageColorDarkMode = new Color(200, 200, 200);
	Color messageColorLightMode = new Color(120, 120, 200);

	private Synthesizer synth = null;
	private boolean isSoundbankSynth = false;
	private boolean needSoundbankRefresh = false;

	// instrument panels added into scrollpanes

	public static List<InstPanel> getAffectedPanels(int inst) {
		List<InstPanel> affectedPanels = isCustomSection()
				? getSectionPanelList(inst)
				: (List<InstPanel>) getInstList(inst);
		return affectedPanels;
	}

	public static List<? extends InstPanel> getInstList(int order) {
		if (vibeComposerGUI == null) {
			throw new IllegalStateException("The main window has not been initialized.");
		}
		return getInstrumentControls(order).getPanels();
	}

	private static InstrumentGUIControls getInstrumentControls(int order) {
		if (vibeComposerGUI == null) {
			throw new IllegalStateException("The main window has not been initialized.");
		}
		return vibeComposerGUI.getOwnedInstrumentControls(order);
	}

	private InstrumentGUIControls getOwnedInstrumentControls(int order) {
		switch (order) {
		case 0: return melodyGUI;
		case 1: return bassGUI;
		case 2: return chordGUI;
		case 3: return arpGUI;
		case 4: return drumGUI;
		default: throw new IllegalArgumentException("Inst list order wrong.");
		}
	}

	public static JScrollPane getInstPane(int order) {
		return getInstrumentControls(order).getPanelScrollPane();
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

// arrangement subcells - copy dragging

// instrument global settings

// main title settings
	JLabel mainTitle;
	JLabel subTitle;

	// macro params

	JCheckBox randomizeScaleModeOnCompose;

// seed / midi

	JList<File> generatedMidi;
	MidiDevice device = null;

CheckButton midiMode;
	ScrollComboBox<String> midiModeDevices;
	MidiHandler mh = new MidiHandler();

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
	Thread cycle;

	ScrollComboBox<String> loopBeatCompose;
	JLabel totalTime;
	boolean isKeySeeking = false;

JLabel messageLabel;
	ScrollComboBox<String> presetLoadBox;
	VeloRect globalVolSlider;
	VeloRect globalReverbSlider;
	VeloRect globalChorusSlider;
	JPanel everythingPanel;
	JPanel controlPanel;
	JScrollPane everythingPane;

	public static VibeComposerGUI vibeComposerGUI = null;

	static GridBagConstraints constraints = new GridBagConstraints();
	private MelodyGUI melodyGUI;
	private BassGUI bassGUI;
	private ChordGUI chordGUI;
	private ArpGUI arpGUI;
	private ScoreGUI scoreGUI;
	private ExtraSettingsGUI extraSettingsGUI;
	private GenerationGUI generationGUI;
	private ArrangementGUI arrangementGUI;

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
		System.setErr(ApplicationSessionState.dummyOut);
	}

	public VibeComposerGUI(String title) {
		super(title);
		vibeComposerGUI = this;
	}

	private void initExtraSettingsGUI() {
		extraSettingsGUI = new ExtraSettingsGUI(new ExtraSettingsGUI.Context() {
			@Override public JButton makeButton(String name, Consumer<? super Object> action) { return VibeComposerGUI.makeButton(name, action); }
			@Override public JButton makeButton(String name, String actionCommand) { return VibeComposerGUI.this.makeButton(name, actionCommand); }
			@Override public JCheckBox makeCheckBox(String label, boolean selected, boolean thick) { return VibeComposerGUI.makeCheckBox(label, selected, thick); }
			@Override public void initHelperPopups(JPanel settingsPanel) { VibeComposerGUI.this.initHelperPopups(settingsPanel); }
			@Override public void markSoundbankRefreshNeeded() { needSoundbankRefresh = true; }
			@Override public List<InstPanel> getAffectedPanels(int instrument) { return VibeComposerGUI.getAffectedPanels(instrument); }
			@Override public List<? extends InstPanel> getInstList(int instrument) { return VibeComposerGUI.getInstList(instrument); }
		}, drumGUI);
		extraSettingsGUI.initExtraSettings();
	}

	private void initGenerationGUI() {
		generationGUI = new GenerationGUI(new GenerationGUI.Context() {
			@Override public JButton makeButton(String name, String actionCommand) {
				return VibeComposerGUI.this.makeButton(name, actionCommand);
			}
			@Override public JButton makeButton(String name, Consumer<? super Object> action) {
				return VibeComposerGUI.makeButton(name, action);
			}
			@Override public JCheckBox makeCheckBox(String label, boolean selected, boolean thick) {
				return VibeComposerGUI.makeCheckBox(label, selected, thick);
			}
			@Override public void addControlPanel(JPanel panel, int startY, int anchorSide) {
				constraints.gridy = startY;
				constraints.anchor = anchorSide;
				controlPanel.add(panel);
			}
			@Override public void alignControlPanel() {
				controlPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			}
			@Override public void enthickenText(Component component) {
				VibeComposerGUI.this.enthickenText(component);
			}
			@Override public void randomizeBpm() { VibeComposerGUI.this.randomizeBPM(); }
			@Override public void randomizeTranspose(boolean currentTabOnly) {
				VibeComposerGUI.this.randomizeTranspose(currentTabOnly);
			}
			@Override public void sidechainPatterns(boolean showPopup, boolean currentTabOnly) {
				VibeComposerGUI.this.sidechainPatterns(showPopup, currentTabOnly);
			}
			@Override public void applyGlobalSwing(int swing, boolean customPanels) {
				VibeComposerGUI.this.applyGlobalSwing(swing, customPanels);
			}
			@Override public void setChordProgressionLength(int size) {
				VibeComposerGUI.this.setChordProgressionLength(size);
			}
		});
	}

	private void initArrangementGUI() {
		arrangementGUI = new ArrangementGUI(new ArrangementGUI.Context() {
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
			@Override public void toggleButtonEnabledForPanels() {
				VibeComposerGUI.this.toggleButtonEnabledForPanels();
			}
			@Override public List<? extends InstPanel> getInstList(int instrument) {
				return VibeComposerGUI.getInstList(instrument);
			}
		});
	}

	private void initScoreGUI() {
		scoreGUI = new ScoreGUI();
	}

	private void initMelodyGUI() {
		melodyGUI = new MelodyGUI(new MelodyGUI.Context() {
			@Override
			public JButton makeButton(String name, Consumer<? super Object> action) {
				return SwingUtils.makeButton(name, action);
			}

			@Override
			public void addPanel() {
				VibeComposerGUI.this.addPanel(0);
			}

			@Override
			public void generatePanels(boolean triggerRegenerate) {
				VibeComposerGUI.this.generatePanels(0, triggerRegenerate);
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
			public MelodyPanel addMelodyPanel() {
				return (MelodyPanel) VibeComposerGUI.this.addInstPanelToLayout(0);
			}
			@Override
			public List<InstPanel> getAffectedPanels(int instrument) {
				return VibeComposerGUI.getAffectedPanels(instrument);
			}

		});
	}

	private void initBassGUI() {
		bassGUI = new BassGUI(new BassGUI.Context() {
			@Override
			public JButton makeButton(String name, Consumer<? super Object> action) {
				return SwingUtils.makeButton(name, action);
			}

			@Override
			public void addPanel() {
				VibeComposerGUI.this.addPanel(1);
			}

			@Override
			public void generatePanels(boolean triggerRegenerate) {
				VibeComposerGUI.this.generatePanels(1, triggerRegenerate);
			}

			@Override
			public BassPanel addBassPanel() {
				return (BassPanel) VibeComposerGUI.this.addInstPanelToLayout(1);
			}
			@Override
			public List<InstPanel> getAffectedPanels(int instrument) {
				return VibeComposerGUI.getAffectedPanels(instrument);
			}

		});
	}

	private DrumGUI drumGUI;

	private void initDrumGUI() {
		drumGUI = new DrumGUI(new DrumGUI.Context() {
			@Override public JButton makeButton(String name, Consumer<? super Object> action) {
				return SwingUtils.makeButton(name, action);
			}
			@Override public JButton makeButton(String name, String actionCommand) {
				return VibeComposerGUI.this.makeButton(name, actionCommand);
			}
			@Override public void addPanel() { VibeComposerGUI.this.addPanel(4); }
			@Override public void generatePanels(boolean triggerRegenerate) {
				VibeComposerGUI.this.generatePanels(4, triggerRegenerate);
			}
			@Override public DrumPanel addDrumPanel() {
				return (DrumPanel) VibeComposerGUI.this.addInstPanelToLayout(4);
			}
			@Override public List<InstPanel> getAffectedPanels(int instrument) {
				return VibeComposerGUI.getAffectedPanels(instrument);
			}
		});
	}


	private void initArpGUI() {
		arpGUI = new ArpGUI(new ArpGUI.Context() {
			@Override
			public JButton makeButton(String name, Consumer<? super Object> action) {
				return SwingUtils.makeButton(name, action);
			}

			@Override
			public JButton makeButton(String name, String actionCommand) {
				return VibeComposerGUI.this.makeButton(name, actionCommand);
			}

			@Override
			public void addPanel() {
				VibeComposerGUI.this.addPanel(3);
			}

			@Override
			public void generatePanels(boolean triggerRegenerate) {
				VibeComposerGUI.this.generatePanels(3, triggerRegenerate);
			}

			@Override
			public ArpPanel addArpPanel() {
				return (ArpPanel) VibeComposerGUI.this.addInstPanelToLayout(3);
			}
			@Override
			public List<InstPanel> getAffectedPanels(int instrument) {
				return VibeComposerGUI.getAffectedPanels(instrument);
			}
			@Override
			public List<? extends InstPanel> getInstList(int instrument) {
				return VibeComposerGUI.getInstList(instrument);
			}

		});
	}


	private void initChordGUI() {
		chordGUI = new ChordGUI(new ChordGUI.Context() {
			@Override
			public JButton makeButton(String name, Consumer<? super Object> action) {
				return SwingUtils.makeButton(name, action);
			}

			@Override
			public JButton makeButton(String name, String actionCommand) {
				return VibeComposerGUI.this.makeButton(name, actionCommand);
			}

			@Override
			public void addPanel() {
				VibeComposerGUI.this.addPanel(2);
			}

			@Override
			public void generatePanels(boolean triggerRegenerate) {
				VibeComposerGUI.this.generatePanels(2, triggerRegenerate);
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
			public void alignChordsWithMelody(ChordletPanel chordlets) {
				if (!melodyGUI.getPanels().isEmpty()) {
					chordlets.alignWithMelodyTargetNotes(
							melodyGUI.getPanels().get(0).getChordNoteChoices());
				}
			}

			@Override
			public ChordPanel addChordPanel() {
				return (ChordPanel) VibeComposerGUI.this.addInstPanelToLayout(2);
			}
			@Override
			public List<InstPanel> getAffectedPanels(int instrument) {
				return VibeComposerGUI.getAffectedPanels(instrument);
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
		initTitles(0, GridBagConstraints.CENTER);

		initExtraSettingsGUI();
		initGenerationGUI();

		// randomization buttons
		generationGUI.initRandomButtons(350, GridBagConstraints.CENTER);

		initSoloMutersAndTrackControl(20, GridBagConstraints.WEST);
		LG.i("Titles, Extra, S/M " + (System.currentTimeMillis() - sysTime) + " ms!");
		// ---- INSTRUMENT SETTINGS ----
		{
			chordGUI.initChordGenSettings(40, GridBagConstraints.WEST);
			arpGUI.initArpGenSettings(105, GridBagConstraints.WEST);
			drumGUI.initDrumGenSettings(190, GridBagConstraints.WEST);
			melodyGUI.initMelodyGenSettings(220, GridBagConstraints.WEST);

		}
		LG.i("Gen settings: " + (System.currentTimeMillis() - sysTime) + " ms!");
		boolean randomizeInstsTemp = GenerationGUI.randomizeInstOnComposeOrGen.isSelected();
		GenerationGUI.randomizeInstOnComposeOrGen.setSelected(true);
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
					if (indx >= 0 && indx < 5) {
						if (SwingUtilities.isRightMouseButton(e)) {
							LG.i(("RMB pressed in instrument tab pane: " + indx));
							setAddInst(indx, !getInstrumentControls(indx).getEnabledCheckBox().isSelected());
						} else if (SwingUtilities.isMiddleMouseButton(e)) {
							LG.i(("MMB pressed in instrument tab pane: " + indx));
							boolean hasAny = false;
							for (int i = 0; i < 5; i++) {
								if (i != indx && getInstrumentControls(i).getEnabledCheckBox().isSelected()) {
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
				getInstrumentControls(i).getEnabledCheckBox().addChangeListener((evt) -> {
					instrumentTabPane.setBackgroundAt(finalI,
							OMNI.alphen(getInstrumentControls(finalI).getEnabledCheckBox().isSelected()
									? Constants.instColors[finalI] : Color.white, 40));
				});
			}

			// arrangement
			ArrangementGUI.arrangementGUI.initArrangementSettings(325, GridBagConstraints.CENTER);


		}
		GenerationGUI.randomizeInstOnComposeOrGen.setSelected(randomizeInstsTemp);
		LG.i("Arr: " + (System.currentTimeMillis() - sysTime) + " ms!");
		scoreGUI.initScoreSettings(330, GridBagConstraints.CENTER);
		LG.i("Scr: " + (System.currentTimeMillis() - sysTime) + " ms!");
		//createHorizontalSeparator(327, this);

		// ---- OTHER SETTINGS ----
		{


			generationGUI.initMacroParams(360, GridBagConstraints.CENTER);

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
		getInstrumentControls(partNum).getEnabledCheckBox().setSelected(b);
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
			getInstrumentControls(0).getRandomPanelsToGenerate().setText("3");
			getInstrumentControls(1).getRandomPanelsToGenerate().setText("1");
			getInstrumentControls(2).getRandomPanelsToGenerate().setText("2");
			getInstrumentControls(3).getRandomPanelsToGenerate().setText("3");
			getInstrumentControls(4).getRandomPanelsToGenerate().setText("6");
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
			ArrangementGUI.arrangementGUI.applyCustomPanelsToSection("", i,
					ArrangementGUI.arrSection.getSelectedIndex());
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
			if (sectionGuiConfig.getBeatDurationMultiplierIndex() != GenerationGUI.beatDurationMultiplier
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
			ArrangementGUI.arrangementGUI
					.switchPanelsForSectionSelection(ArrangementGUI.arrSection.getVal());

			/*if (sectionGuiConfig.getGlobalSwingOverride() != null) {
				applyGlobalSwing(sectionGuiConfig.getGlobalSwingOverride(), true);
			}*/

			//copyConfigToGUI(guiConfig);
			//clearAllSeeds();
		}
	}

	private void generatePanels(int part, boolean triggerRegenerate) {
		int panelCount = isCustomSection() ? getInstList(part).size()
				: Integer.valueOf(getInstrumentControls(part).getRandomPanelsToGenerate().getText());
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





	public static JCheckBox makeCheckBox(String string, boolean b, boolean thick) {
		return SwingUtils.makeCheckBox(string, b, thick);
	}

	/*public void fixCombinedMelodyTracks() {
		if (MelodyGUI.combineMelodyTracks == null) {
			return;
		}
		boolean foundValid = false;
		int start = currentMidi == null ? 1 : 0;
		for (int i = start; i < melodyGUI.getPanels().size(); i++) {
			if (MelodyGUI.combineMelodyTracks.isSelected()) {
				boolean isValid = melodyGUI.getPanels().get(i).getSequenceTrack() >= 0;
				if (!foundValid && isValid) {
					foundValid = true;
					melodyGUI.getPanels().get(i).toggleCombinedMelodyDisabledUI(true);
				} else {
					melodyGUI.getPanels().get(i)
							.toggleCombinedMelodyDisabledUI(!MelodyGUI.combineMelodyTracks.isSelected());
				}
			} else {
				melodyGUI.getPanels().get(i)
						.toggleCombinedMelodyDisabledUI(!MelodyGUI.combineMelodyTracks.isSelected());
			}
		}
		if (!foundValid) {
			melodyGUI.getPanels().get(0).toggleCombinedMelodyDisabledUI(true);
		}
	}*/

















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
		return getInstrumentControls(partNum).getEnabledCheckBox().isSelected();
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
								String newTime = OMNI.millisecondsToTimeString(slider.getUpperValue());
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
							if (GenerationGUI.beatDurationMultiplier.getSelectedIndex() == 0) {
								mult = 0.5;
							} else if (GenerationGUI.beatDurationMultiplier.getSelectedIndex() == 2) {
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
			//drumGUI.getPanels().forEach(e -> sendPanMessage(e.getPanSlider().getValue(), 9));
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
				? OMNI.clampVel(filterMultiplier * getInstrumentControls(part).getGroupFilterSlider().getValue())
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
		GenerationGUI.randomizeBpmOnCompose.setSelected(state);
		GenerationGUI.randomizeTransposeOnCompose.setSelected(state);
		//GenerationGUI.randomizeChordStrumsOnCompose.setSelected(state);
		GenerationGUI.randomizeInstOnComposeOrGen.setSelected(state);
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
		GenerationGUI.randomizeBpmOnCompose.setForeground(fg);
		GenerationGUI.randomizeTransposeOnCompose.setForeground(fg);
		//GenerationGUI.randomizeChordStrumsOnCompose.setForeground(fg);
		GenerationGUI.randomizeInstOnComposeOrGen.setForeground(fg);
		ArrangementGUI.randomizeArrangementOnCompose.setForeground(fg);
		ArrangementGUI.arrangementResetCustomPanelsOnCompose.setForeground(fg);
		randomizeScaleModeOnCompose.setForeground(fg);
		MelodyGUI.melodyTargetNotesRandomizeOnCompose.setForeground(fg);
		MelodyGUI.melodyPatternRandomizeOnCompose.setForeground(fg);
		GenerationGUI.switchOnComposeRandom.setForeground(fg);
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

		for (DrumPanel dp : drumGUI.getPanels()) {
			dp.getComboPanel().reapplyHits();
		}
		ArrangementGUI.arrangementGUI.refreshVariationPopupButtons(
				ArrangementGUI.scrollableArrangementActualTable.getColumnCount());
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
		ArrangementGUI.arrangementGUI.refreshVariationPopupButtons(
				ArrangementGUI.actualArrangement.getSections().size());

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
			getInstrumentControls(i).getAddPanelButton().setEnabled(isOriginal);
			getInstrumentControls(i).getRandomPanelsToGenerate().setEnabled(isOriginal);
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
			if (!melodyGUI.getPanels().isEmpty() && melodyGUI.getPanels().get(0).getPatternSeed() != 0
					&& !melodyGUI.getPanels().get(0).getMuteInst()) {
				seedData += "_" + melodyGUI.getPanels().get(0).getPatternSeed();
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

			if (ExtraSettingsGUI.configHistoryStoreRegeneratedTracks.isSelected() || !regenerate
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
			currentBeatMultiplier = GenerationGUI.beatDurationMultiplier.getSelectedItem();
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
			drumGUI.getPanels().forEach(e -> {
				if (e.getSequenceTrack() < 0) {
					e.getSoloMuter().unsolo();
					e.getSoloMuter().unmute();
				}
			});
		}
		if (MelodyGUI.combineMelodyTracks.isSelected()) {
			melodyGUI.getPanels().forEach(e -> {
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
					&& !melodyGUI.getPanels().isEmpty()
					&& !melodyGUI.getPanels().get(0).getNoteTargetsButton().isEnabled())
							? melodyGUI.getPanels().stream()
									.collect(Collectors.toMap(MelodyPanel::getPanelOrder,
											MelodyPanel::getChordNoteChoices))
							: null;

			/*boolean addStartDelay = useArrangement.isSelected() || arrangementCustom.isSelected()
					|| drumGUI.getPanels().stream().anyMatch(e -> e.getOffset() < 0)
					|| (ChordGUI.randomChordDelay.isSelected()
							&& (chordGUI.getPanels().stream().anyMatch(e -> e.getOffset() < 0)));*/
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

		if (!regenerate && GenerationGUI.randomizeBpmOnCompose.isSelected()) {
			randomizeBPM();
		}

		// MELODY
		if (!regenerate && MelodyGUI.generateMelodiesOnCompose.isSelected()) {
			int seed = getCurrentSeed();
			createPanels(0, melodyGUI.getPanels().size(), false,
					seed != 0 ? seed : new Random().nextInt(), null);
		}

		if (!regenerate && ExtraSettingsGUI.randomizeTimingsOnCompose.isSelected()) {
			if (GenerationGUI.globalSwingOverride.isSelected()) {
				GenerationGUI.globalSwingOverrideValue
						.setInt(50 + new Random().nextInt(DrumGUI.randomDrumMaxSwingAdjust.getInt() * 2 + 1)
								- DrumGUI.randomDrumMaxSwingAdjust.getInt());
			}
			double randomBeatMultiplier = new Random().nextDouble();
			if (randomBeatMultiplier < 0.85) {
				GenerationGUI.beatDurationMultiplier.setSelectedIndex(1);
			} else if (randomBeatMultiplier < 0.95) {
				GenerationGUI.beatDurationMultiplier.setSelectedIndex(0);
			} else {
				GenerationGUI.beatDurationMultiplier.setSelectedIndex(2);
			}
		}

		if (!regenerate && ExtraSettingsGUI.sidechainPatternsOnCompose.isSelected()) {
			sidechainPatterns(false, false);
		}


		if (regenerate && manual && MelodyGUI.randomMelodyOnRegenerate.isSelected()) {
			melodyGUI.randomizeMelodySeeds();
		}

		if (regenerate && MelodyGUI.randomMelodyOnRegenerate.isSelected() && !melodyGUI.getPanels().isEmpty()) {
			if (MelodyGUI.melodyPatternRandomizeOnCompose.isSelected()) {
				melodyGUI.getPanels().forEach(e -> {
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
				melodyGUI.getPanels().forEach(e -> {
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
				&& !melodyGUI.getPanels().isEmpty()) {
			if (MelodyGUI.melody1ForcePatterns.isSelected()) {
				MelodyPanel firstMp = melodyGUI.getPanels().get(0);
				List<Integer> pat = MelodyUtils.getRandomMelodyPattern(
						firstMp.getAlternatingRhythmChance(),
						firstMp.getPanelOrder() + (firstMp.getPatternSeed() == 0 ? lastRandomSeed
								: firstMp.getPatternSeed()));
				firstMp.setMelodyPatternOffsets(pat);
			} else {
				melodyGUI.getPanels().forEach(e -> {
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


		if (MelodyGUI.melody1ForcePatterns.isSelected() && !melodyGUI.getPanels().isEmpty()) {
			MelodyPanel mp1 = melodyGUI.getPanels().get(0);
			for (int i = 1; i < melodyGUI.getPanels().size(); i++) {
				melodyGUI.getPanels().get(i).overridePatterns(mp1);
			}
		}


		// BASS

		// ARPS
		if (instrumentTabPane.getSelectedIndex() != 3 && ArpGUI.arpCopyMelodyInst.isSelected()
				&& !melodyGUI.getPanels().isEmpty() && !melodyGUI.getPanels().get(0).getMuteInst()) {
			if (arpGUI.getPanels().size() > 0 && !arpGUI.getPanels().get(0).getLockInst()) {
				arpGUI.getPanels().get(0).getInstrumentBox().initInstPool(POOL.MELODY);
				arpGUI.getPanels().get(0).setInstPool(POOL.MELODY);
				arpGUI.getPanels().get(0).setInstrument(melodyGUI.getPanels().get(0).getInstrument());
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
			for (int i = 0; i < melodyGUI.getPanels().size(); i++) {
				int mpOrder = melodyGUI.getPanels().get(i).getPanelOrder();
				List<Integer> notes = MelodyGenerator.TARGET_NOTES.get(mpOrder);
				if (notes != null) {
					melodyGUI.getPanels().get(i).setChordNoteChoices(notes);
					guiConfig.getMelodyParts().get(i).setChordNoteChoices(notes);
				}
			}
		}

		for (int i = 0; i < arpGUI.getPanels().size(); i++) {
			ArpPart ap = MidiGenerator.gc.getArpParts().get(i);
			if (ap.getArpPattern() == ArpPattern.RANDOM) {
				arpGUI.getPanels().get(i).setArpPatternCustom(ap.getArpPatternCustom());
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
			ArrangementGUI.arrangementGUI.refreshVariationPopupButtons(
					ArrangementGUI.scrollableArrangementActualTable.getColumnCount());
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

			totalTime.setText(OMNI.microsecondsToTimeString(sequencer.getMicrosecondLength()));
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
			if (GenerationGUI.beatDurationMultiplier.getSelectedIndex() == 0) {
				divisor = 0.5;
			} else if (GenerationGUI.beatDurationMultiplier.getSelectedIndex() == 2) {
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

	private void unapplySolosMutes(boolean onlyIncluded) {
		if (!sequenceReady()) {
			return;
		}
		/*
				int countReducer = 0;
				if (DrumGUI.combineDrumTracks.isSelected()) {
					countReducer = (int) ((onlyIncluded)
							? drumGUI.getPanels().stream().filter(e -> !e.getMuteInst()).count()
							: drumGUI.getPanels().size());
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

		Optional<DrumPanel> notExcludedDrum = drumGUI.getPanels().stream()
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
		new DrumLoopPopup(drumGUI.getPanels());
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
			int inst = melodyGUI.getPanels().get(0).getInstrument();
			melodyGUI.getPanels().get(0).getInstrumentBox().initInstPool(melodyGUI.getPanels().get(0).getInstPool());
			melodyGUI.getPanels().get(0).getInstrumentBox().setInstrument(inst);
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

		if (ae.getActionCommand() == "RandStrums"
				|| (isCompose & GenerationGUI.randomizeChordStrumsOnCompose.isSelected())) {
			for (InstPanel p : getAffectedPanels(2)) {
				ChordPanel cp = (ChordPanel) p;
				Pair<StrumType, Integer> strumPair = ChordGUI.getRandomStrumPair();
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
		if (isCompose && GenerationGUI.randomizeInstOnComposeOrGen.isSelected()) {
			randomizeInsts();
		}

		if (isCompose || isRegenerate) {
			soloMuterPossibleChange = true;
		}

		if (isCompose && getInstrumentControls(2).getEnabledCheckBox().isSelected()
				&& ChordGUI.randomChordsGenerateOnCompose.isSelected()) {
			generatePanels(2);
		}
		if (isCompose && getInstrumentControls(3).getEnabledCheckBox().isSelected()
				&& ArpGUI.randomArpsGenerateOnCompose.isSelected()) {
			generatePanels(3);
		}

		if (isCompose && getInstrumentControls(4).getEnabledCheckBox().isSelected()
				&& DrumGUI.randomDrumsGenerateOnCompose.isSelected()) {
			generatePanels(4);
		}

		if (ae.getActionCommand() == "RandomizeTranspose") {
			Random instGen = new Random();
			ScoreGUI.transposeScore.setInt(instGen.nextInt(12) - 6);
			triggerRegenerate = true;
		}

		if (isCompose && GenerationGUI.randomizeTransposeOnCompose.isSelected()) {
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
			GenerationGUI.switchOnComposeRandom.setText("  Tick all 'on Compose'   ");
			GenerationGUI.switchOnComposeRandom.setActionCommand("CheckComposeRandom");
		}

		if (ae.getActionCommand() == "CheckComposeRandom") {
			switchAllOnComposeCheckboxes(true);
			GenerationGUI.switchOnComposeRandom.setText("Untick all 'on Compose'");
			GenerationGUI.switchOnComposeRandom.setActionCommand("UncheckComposeRandom");
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
		if (ArpGUI.arpAffectsBpm.isSelected() && !arpGUI.getPanels().isEmpty()) {
			double highestArpPattern = arpGUI.getPanels().stream().map(
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


		for (ChordPanel cp : chordGUI.getPanels()) {
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
		for (ArpPanel ap : arpGUI.getPanels()) {
			if (!ap.getLockInst()) {
				ap.getInstrumentBox().setInstrument(ap.getInstrumentBox().getRandomInstrument());
			}
		}
		if (!melodyGUI.getPanels().isEmpty()) {

			if (!MelodyGUI.combineMelodyTracks.isSelected()) {
				for (MelodyPanel mp : melodyGUI.getPanels()) {
					if (!mp.getLockInst()) {
						mp.getInstrumentBox()
								.setInstrument(mp.getInstrumentBox().getRandomInstrument());
					}
				}
			} else {
				int inst = melodyGUI.getPanels().get(0).getInstrumentBox().getRandomInstrument();
				for (MelodyPanel mp : melodyGUI.getPanels()) {
					if (!mp.getLockInst()) {
						mp.getInstrumentBox().setInstrument(inst);
					}
				}
			}

		}

		for (BassPanel bp : bassGUI.getPanels()) {
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
			getInstrumentControls(i).getRandomPanelsToGenerate()
					.setText("" + Math.max(1, getInstList(i).size()));
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
		cs.add(GenerationGUI.randomizeInstOnComposeOrGen);
		cs.add(GenerationGUI.randomizeBpmOnCompose);
		cs.add(GenerationGUI.randomizeTransposeOnCompose);

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
		cs.add(ExtraSettingsGUI.orderedTransposeGeneration);
		cs.add(ExtraSettingsGUI.patternApplyPausesWhenGenerating);
		cs.add(ExtraSettingsGUI.highlightPatterns);
		cs.add(ScoreGUI.highlightScoreNotes);
		cs.add(ExtraSettingsGUI.randomizeTimingsOnCompose);
		cs.add(ExtraSettingsGUI.customFilenameAddTimestamp);
		cs.add(ExtraSettingsGUI.configHistoryStoreRegeneratedTracks);
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
		cs.add(ExtraSettingsGUI.reuseMidiChannelAfterCopy);
		cs.add(ExtraSettingsGUI.transposeNotePreview);
		cs.add(ExtraSettingsGUI.moveStartToCustomizedSection);
		cs.add(ExtraSettingsGUI.allowValuesOutOfRange);

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
		gc.setVersion(CURRENT_VERSION);
		gc.setRandomSeed(lastRandomSeed);
		gc.setMidiMode(midiMode.isSelected());
		gc.setBpm(Double.valueOf(mainBpm.getInt()));
		gc.setScaleMode(ScaleMode.valueOf(scaleMode.getVal()));

		arrangementGUI.saveToConfig(gc, isNew, lastRandomSeed, guiConfig.getPatternMaps());
		melodyGUI.saveToConfig(gc, lastRandomSeed);
		bassGUI.saveToConfig(gc, lastRandomSeed);
		chordGUI.saveToConfig(gc, lastRandomSeed);
		arpGUI.saveToConfig(gc, lastRandomSeed);
		drumGUI.saveToConfig(gc, lastRandomSeed,
				midiMode.isSelected() && !midiModeDevices.getVal().contains("ervill"));
		ScoreGUI.saveToConfig(gc);
		GenerationGUI.saveToConfig(gc);
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
		MelodyGUI.randomMelodyOnRegenerate.setSelected(false);
		randomSeed.setValue((int) gc.getRandomSeed());
		lastRandomSeed = randomSeed.getValue();
		midiMode.setSelected(gc.isMidiMode());
		scaleMode.setVal(gc.getScaleMode().toString());

		// Restore each module's controls and models before recreating its panels.
		arrangementGUI.loadFromConfig(gc);
		melodyGUI.loadFromConfig(gc);
		bassGUI.loadFromConfig(gc);
		chordGUI.loadFromConfig(gc, this::setChordProgressionLength);
		arpGUI.loadFromConfig(gc);
		drumGUI.loadFromConfig(gc);
		ScoreGUI.loadFromConfig(gc);
		GenerationGUI.loadFromConfig(gc);
		ExtraSettingsGUI.loadFromConfig(gc);

		int bpm = (int) Math.round(gc.getBpm());
		mainBpm.getKnob().setMin(Math.min(GenerationGUI.mainBpm.getKnob().getMin(), bpm));
		mainBpm.getKnob().setMax(Math.max(GenerationGUI.mainBpm.getKnob().getMax(), bpm));
		mainBpm.setInt(bpm);

		melodyGUI.loadPartsFromConfig(gc, parts -> recreateInstPanelsFromInstParts(0, parts));
		bassGUI.loadPartsFromConfig(gc, parts -> recreateInstPanelsFromInstParts(1, parts));
		chordGUI.loadPartsFromConfig(gc, parts -> recreateInstPanelsFromInstParts(2, parts));
		arpGUI.loadPartsFromConfig(gc, parts -> recreateInstPanelsFromInstParts(3, parts));
		drumGUI.loadPartsFromConfig(gc, parts -> recreateInstPanelsFromInstParts(4, parts));

		ArrangementGUI.arrSection.setVisible(true);
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

	static List<InstPart> getInstPartsFromCustomSectionInstPanels(int inst) {
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
			createPanels(partNum, melodyGUI.getPanels().size() + 1, true, new Random().nextInt(), panel);
		} else if (partNum == 1) {

		} else if (partNum == 2) {
			createPanels(partNum, chordGUI.getPanels().size() + 1, true, null, panel);
		} else if (partNum == 3) {
			createPanels(partNum, arpGUI.getPanels().size() + 1, true, null, panel);
		} else if (partNum == 4) {
			createPanels(partNum, drumGUI.getPanels().size() + 1, true, null, panel);
		}
	}

	private void addPanel(int part) {
		createPanels(part, getAffectedPanels(part).size() + 1, true);
		recalculateGeneratorAndTabCounts();
		recalculateSoloMuters();
		repaint();
	}

	private void createPanels(int part, int panelCount, boolean onlyAdd) {
		createPanels(part, panelCount, onlyAdd, null, null);
	}

	private void createPanels(int part, int panelCount, boolean onlyAdd, Integer seed,
			InstPanel randomizedPanel) {
		if (part == 0) {
			if (seed == null) {
				melodyGUI.createRandomMelodyPanels(panelCount, onlyAdd);
			} else {
				melodyGUI.createRandomMelodyPanels(seed, panelCount, onlyAdd,
						(MelodyPanel) randomizedPanel);
			}
		} else if (part == 1) {
			if (seed == null) {
				bassGUI.createRandomBassPanels(panelCount, onlyAdd);
			} else {
				bassGUI.createRandomBassPanels(seed, panelCount, onlyAdd,
						(BassPanel) randomizedPanel);
			}
		} else if (part == 2) {
			chordGUI.createRandomChordPanels(panelCount, onlyAdd, (ChordPanel) randomizedPanel);
		} else if (part == 3) {
			arpGUI.createRandomArpPanels(panelCount, onlyAdd, (ArpPanel) randomizedPanel);
		} else if (part == 4) {
			drumGUI.createRandomDrumPanels(panelCount, onlyAdd, (DrumPanel) randomizedPanel);
		} else {
			throw new IllegalArgumentException("Unsupported panel part!");
		}
		repaint();
	}



	private static int getValidPanelNumber(List<? extends InstPanel> panels) {
		panels.sort(Comparator.comparing(e1 -> e1.getPanelOrder()));
		if (panels.stream().anyMatch(e -> e.getLockInst())) {
			return getLowestAvailablePanelNumber(panels);
		} else {
			return getLowestAvailablePanelNumber(panels);
		}
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

	public static void midiNavigate(long sliderValue) {
		midiNavigate(sliderValue, 25);
	}

	public static void midiNavigate(long sliderValue, int offset) {
		long time = (sliderValue - offset) * 1000;
		long timeTicks = PlaybackState.msToSequencerTicks(time);
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

	public static int getAbsoluteOrder(int partNum, int partOrder) {
		List<Integer> allPanelOrders = getInstList(partNum).stream().map(e -> e.getPanelOrder())
				.collect(Collectors.toList());
		allPanelOrders.sort(Comparator.comparing(e -> e));
		if (allPanelOrders.contains(partOrder)) {
			return allPanelOrders.indexOf(partOrder);
		}
		throw new IllegalArgumentException("Absolute order not found!");
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
							|| ExtraSettingsGUI.transposedNotesForceScale.isSelected();
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
				startPos + (PlaybackState.msToSequencerTicks(startDelayMicroseconds)));
			trk.add(noteOn);
			MidiEvent noteOff = new MidiEvent(noteOffMsg,
				startPos + (PlaybackState.msToSequencerTicks(startDelayMicroseconds + durationMs * 1000)));
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
