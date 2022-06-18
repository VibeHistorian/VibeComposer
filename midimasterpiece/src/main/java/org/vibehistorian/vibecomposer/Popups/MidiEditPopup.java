package org.vibehistorian.vibecomposer.Popups;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.JMusicUtilsCustom;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Components.CheckButton;
import org.vibehistorian.vibecomposer.Components.MidiDropPane;
import org.vibehistorian.vibecomposer.Components.MidiEditArea;
import org.vibehistorian.vibecomposer.Components.MidiListCellRenderer;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Helpers.FileTransferHandler;
import org.vibehistorian.vibecomposer.Helpers.PatternMap;
import org.vibehistorian.vibecomposer.Helpers.PhraseNote;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Helpers.UsedPattern;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Parts.ArpPart;
import org.vibehistorian.vibecomposer.Parts.BassPart;
import org.vibehistorian.vibecomposer.Parts.ChordPart;
import org.vibehistorian.vibecomposer.Parts.DrumPart;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;

import jm.music.data.Note;
import jm.music.data.Part;
import jm.music.data.Phrase;
import jm.music.data.Score;

public class MidiEditPopup extends CloseablePopup {

	public static class PatternNameMarker {
		public String name = "";
		public boolean loadable = false;

		public PatternNameMarker(String name, boolean loadable) {
			super();
			this.name = name;
			this.loadable = loadable;
		}

		@Override
		public String toString() {
			return name + (!loadable ? " (!)" : "");
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, loadable);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;

			if (obj == null || !(obj instanceof PatternNameMarker)) {
				return false;
			}
			PatternNameMarker other = (PatternNameMarker) obj;
			return (loadable == other.loadable) && name.equals(other.name);
		}
	}

	public static int highlightModeChoice = 3;
	public static int snapToTimeGridChoice = 2;
	public static boolean snapToGridChoice = true;
	public static boolean regenerateInPlaceChoice = false;
	public static boolean applyOnLoadChoice = false;

	public static final int baseMargin = 5;
	public static int trackScope = 1;
	public static int trackScopeUpDown = 0;
	public static final double[] TIME_GRID = new double[] { 0.125, 1 / 6.0, 0.25, 1 / 3.0, 0.5,
			2 / 3.0, 1.0, 4 / 3.0, 2.0, 4.0 };

	public static double getTimeGrid() {
		return TIME_GRID[snapToTimeGridChoice];
	}

	public ScrollComboBox<String> highlightMode = new ScrollComboBox<>(false);
	public ScrollComboBox<String> snapToTimeGrid = new ScrollComboBox<>(false);
	public CheckButton regenerateInPlaceOnChange = new CheckButton("R~ on Change",
			regenerateInPlaceChoice);
	public CheckButton applyOnLoad = new CheckButton("Apply on Load/Import", applyOnLoadChoice);
	public CheckButton snapToScaleGrid = new CheckButton("Snap to Scale", snapToGridChoice);

	public ScrollComboBox<String> patternPartBox = new ScrollComboBox<>(false);
	public ScrollComboBox<Integer> patternPartOrderBox = new ScrollComboBox<>(false);
	public ScrollComboBox<PatternNameMarker> patternNameBox = new ScrollComboBox<>(false);

	public JLabel historyLabel = new JLabel("Edit History:");
	public ScrollComboBox<String> editHistoryBox = new ScrollComboBox<>(false);
	public CheckButton displayDrumHelper = new CheckButton("Drum Ghosts", false);

	public JList<File> generatedMidi;
	public boolean saveOnClose = true;
	public int notesHistoryIndex = 0;
	public List<PhraseNotes> notesHistory = new ArrayList<>();

	public int part = 0;
	public int partOrder = 0;

	MidiEditArea mvea = null;
	InstPanel ip = null;
	JTextField text = null;
	Section sec = null;

	public MidiEditPopup(Section section, int secPartNum, int secPartOrder) {
		super("Edit MIDI Phrase (Graphical)", 14);
		sec = section;
		saveOnClose = true;
		trackScopeUpDown = 0;
		LG.i("Midi Edit Popup, Part: " + secPartNum + ", Order: " + secPartOrder);

		JPanel allPanels = new JPanel();
		allPanels.setLayout(new BoxLayout(allPanels, BoxLayout.Y_AXIS));
		allPanels.setMaximumSize(new Dimension(1500, 750));

		JPanel buttonPanel = makeTopButtonPanel();

		JPanel buttonPanel2 = makePatternSavingPanel();

		setupIdentifiers(secPartNum, secPartOrder);

		JPanel mveaPanel = new JPanel();
		mveaPanel.setPreferredSize(new Dimension(1500, 600));
		mveaPanel.setMinimumSize(new Dimension(1500, 600));

		PhraseNotes values = loadSecValues(secPartNum, secPartOrder);
		if (values == null || values.isEmpty()) {
			values = recomposePart(false);
			if (values == null) {
				new TemporaryInfoPopup("Recomposing produced no notes, quitting!", 1500);
				return;
			}
		}

		JPanel bottomSettingsPanel = bottomActionsPreferencesPanel(values);


		mvea = new MidiEditArea(126, 1, values);
		mvea.setPop(this);
		mvea.setPreferredSize(new Dimension(1500, 600));
		mveaPanel.add(mvea);

		setCustomValues(values);

		MidiEditArea.sectionLength = values.stream().map(e -> e.getRv()).mapToDouble(e -> e).sum();


		allPanels.add(buttonPanel);
		allPanels.add(buttonPanel2);
		allPanels.add(bottomSettingsPanel);

		allPanels.add(mveaPanel);

		addKeyboardControls(allPanels);

		frame.setLocation(VibeComposerGUI.vibeComposerGUI.getLocation());
		frame.add(allPanels);
		frame.pack();
		frame.setVisible(true);
	}

	private PhraseNotes loadSecValues(int secPartNum, int secPartOrder) {
		UsedPattern pat = sec.getPattern(secPartNum, secPartOrder);
		if (pat == null) {
			LG.i("Replacing pattern with section type pattern!");
			pat = new UsedPattern(secPartNum, secPartOrder, sec.getPatternType());
			sec.putPattern(secPartNum, secPartOrder, pat);
		}
		LG.i("Loading pattern: " + pat.toString());
		PhraseNotes values = VibeComposerGUI.guiConfig.getPattern(pat);
		if (values == null) {
			LG.e("-----------------------LoadSecValues returns null!--------------");
		} else {
			setSelectedPattern(pat);
		}
		return values;
	}

	private void addKeyboardControls(JPanel allPanels) {
		Action undoAction = new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				undo();
			}
		};
		Action redoAction = new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				redo();
			}
		};

		Action deleteAction = new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				deleteSelected();
			}
		};
		allPanels.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
		allPanels.getActionMap().put("undo", undoAction);
		allPanels.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
		allPanels.getActionMap().put("redo", redoAction);
		allPanels.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
		allPanels.getActionMap().put("delete", deleteAction);
	}

	private JPanel makeTopButtonPanel() {
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(0, 5, 0, 0));
		buttonPanel.setPreferredSize(new Dimension(1500, 50));

		buttonPanel.add(VibeComposerGUI.makeButton("Rand. Pitch", e -> {
			int size = mvea.getValues().size();
			boolean successRandGenerator = false;
			/*if (butt != null && butt.getRandGenerator() != null) {
				List<Integer> randValues = null;
				try {
					randValues = butt.getRandGenerator().apply(new Object());
				} catch (Exception exc) {
					LG.d("Random generator is not ready!");
				}
				if (randValues != null && !randValues.isEmpty()) {
					mvea.getValues().clear();
					mvea.getValues().addAll(randValues);
					successRandGenerator = true;
				}
			
			}*/
			if (!successRandGenerator) {
				Random rnd = new Random();
				for (int i = 0; i < size; i++) {
					if (mvea.getValues().get(i).getPitch() >= 0) {
						int pitch = rnd
								.nextInt(mvea.max - mvea.min + 1 - trackScope * baseMargin * 2)
								+ mvea.min + baseMargin * trackScope;
						if (snapToScaleGrid.isSelected()) {
							int closestNormalized = MidiUtils
									.getClosestFromList(MidiUtils.MAJ_SCALE, pitch % 12);

							mvea.getValues().get(i)
									.setPitch(MidiUtils.octavePitch(pitch) + closestNormalized);
						} else {
							mvea.getValues().get(i).setPitch(pitch);
						}

					}
				}
			}
			saveToHistory();
			repaintMvea();
		}));

		buttonPanel.add(VibeComposerGUI.makeButton("Rand. Velocity", e -> {
			Random rand = new Random();
			InstPanel ip = VibeComposerGUI.getAffectedPanels(part).get(partOrder);
			int velmin = ip.getVelocityMin();
			int velmax = ip.getVelocityMax();
			mvea.getValues().forEach(n -> n.setDynamic(rand.nextInt(velmax - velmin + 1) + velmin));


			saveToHistory();
			repaintMvea();
		}));

		buttonPanel.add(VibeComposerGUI.makeButton("Undo", e -> undo()));
		buttonPanel.add(VibeComposerGUI.makeButton("Redo", e -> redo()));

		JPanel midiDragDropPanel = makeMidiDragDropPanel();
		buttonPanel.add(midiDragDropPanel);

		return buttonPanel;
	}

	private JPanel makeMidiDragDropPanel() {
		JPanel midiDragDropPanel = new JPanel();
		midiDragDropPanel.setLayout(new GridLayout(0, 1));

		generatedMidi = new JList<File>();
		MidiListCellRenderer dndRenderer = new MidiListCellRenderer();
		dndRenderer.setHorizontalAlignment(SwingConstants.CENTER);
		generatedMidi.setCellRenderer(dndRenderer);
		generatedMidi.setBorder(new BevelBorder(BevelBorder.RAISED));
		generatedMidi.setTransferHandler(new FileTransferHandler(e -> {
			return buildMidiFileFromNotes();
		}));
		generatedMidi.setDragEnabled(true);
		generatedMidi.setListData(new File[] { new File("tempMidi.mid") });

		midiDragDropPanel.add(generatedMidi);
		midiDragDropPanel.add(new MidiDropPane(e -> {
			PhraseNotes pn = new PhraseNotes(e);
			double length = pn.stream().map(f -> f.getRv()).mapToDouble(f -> f).sum();
			LG.i("Dropped MIDI Length: " + length);
			if (length > MidiEditArea.sectionLength + MidiGenerator.DBL_ERR) {
				return null;
			} else if (length < MidiEditArea.sectionLength - MidiGenerator.DBL_ERR) {
				PhraseNote lastNote = pn.get(pn.size() - 1);
				lastNote.setRv(lastNote.getRv() + MidiEditArea.sectionLength - length);
			}
			pn.forEach(f -> f.setPitch(f.getPitch() - VibeComposerGUI.transposeScore.getInt()));
			mvea.setValues(pn.copy());
			saveToHistory();
			repaintMvea();

			return pn;
		}));
		return midiDragDropPanel;
	}

	private JPanel bottomActionsPreferencesPanel(PhraseNotes values) {
		JPanel bottomSettingsPanel = new JPanel();
		bottomSettingsPanel.setLayout(new BoxLayout(bottomSettingsPanel, BoxLayout.X_AXIS));
		text = new JTextField(values.toStringPitches(), 40);
		bottomSettingsPanel.add(text);
		bottomSettingsPanel.add(VibeComposerGUI.makeButton("Apply", e -> {
			if (StringUtils.isNotEmpty(text.getText())) {
				try {
					String[] textSplit = text.getText().split(",");
					List<Integer> nums = new ArrayList<>();
					for (String s : textSplit) {
						nums.add(Integer.valueOf(s));
					}
					for (int i = 0; i < nums.size() && i < mvea.getValues().size(); i++) {
						mvea.getValues().get(i).setPitch(nums.get(i));
					}
					repaintMvea();
				} catch (Exception exc) {
					LG.d("Incorrect text format, cannot convert to list of numbers.");
				}
			}
		}));


		ScrollComboBox.addAll(
				new String[] { "No Highlight", "Scale/Key", "Chords", "Scale/Key and Chords" },
				highlightMode);
		highlightMode.setSelectedIndex(highlightModeChoice);
		highlightMode.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				highlightModeChoice = highlightMode.getSelectedIndex();
				mvea.setAndRepaint();
			}
		});

		ScrollComboBox.addAll(new String[] { "1/32", "1/24", "1/16", "1/12", "1/8", "1/6", "1/4",
				"1/3", "1/2", "1" }, snapToTimeGrid);
		snapToTimeGrid.setSelectedIndex(snapToTimeGridChoice);
		snapToTimeGrid.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				snapToTimeGridChoice = snapToTimeGrid.getSelectedIndex();
				mvea.setAndRepaint();
			}
		});

		snapToScaleGrid.setFunc(e -> {
			snapToGridChoice = snapToScaleGrid.isSelected();
		});
		regenerateInPlaceOnChange.setFunc(e -> {
			regenerateInPlaceChoice = regenerateInPlaceOnChange.isSelected();
		});
		applyOnLoad.setFunc(e -> {
			applyOnLoadChoice = applyOnLoad.isSelected();
		});
		displayDrumHelper.setFunc(e -> {
			repaintMvea();
		});

		bottomSettingsPanel.add(applyOnLoad);
		bottomSettingsPanel.add(regenerateInPlaceOnChange);
		bottomSettingsPanel.add(displayDrumHelper);
		bottomSettingsPanel.add(new JLabel("  Highlight Mode:"));
		bottomSettingsPanel.add(highlightMode);
		bottomSettingsPanel.add(new JLabel("  Snap To Time:"));
		bottomSettingsPanel.add(snapToTimeGrid);
		bottomSettingsPanel.add(snapToScaleGrid);

		JButton recompButt = new JButton("Recompose Part");
		recompButt.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				recomposePart(SwingUtilities.isMiddleMouseButton(evt));
			}
		});
		bottomSettingsPanel.add(recompButt);

		editHistoryBox.setFunc(e -> {
			if (notesHistoryIndex != editHistoryBox.getSelectedIndex()) {
				loadFromHistory(editHistoryBox.getSelectedIndex());
			}
		});
		bottomSettingsPanel.add(historyLabel);
		bottomSettingsPanel.add(editHistoryBox);

		return bottomSettingsPanel;
	}

	private JPanel makePatternSavingPanel() {
		JPanel buttonPanel2 = new JPanel();
		buttonPanel2.setLayout(new GridLayout(0, 10, 0, 0));
		buttonPanel2.setPreferredSize(new Dimension(1500, 50));

		ScrollComboBox.addAll(VibeComposerGUI.instNames, patternPartBox);
		patternPartBox.setFunc(e -> loadParts());
		patternPartOrderBox.setFunc(e -> loadNames());
		//patternNameBox.setFunc(e -> loadNotes());

		buttonPanel2.add(patternPartBox);
		buttonPanel2.add(patternPartOrderBox);
		buttonPanel2.add(patternNameBox);

		loadParts();
		loadNames();

		buttonPanel2.add(VibeComposerGUI.makeButton("Load Pattern", e -> {
			loadNotes(true);
		}));
		buttonPanel2.add(VibeComposerGUI.makeButton("Import Pattern", e -> {
			loadNotes(false);
		}));
		buttonPanel2.add(VibeComposerGUI.makeButton("Save Pattern", e -> {
			saveNotes(false, false);
		}));
		buttonPanel2.add(VibeComposerGUI.makeButton("<html>Save Pattern<br>+ Apply</html>", e -> {
			saveNotes(false);
		}));
		buttonPanel2.add(VibeComposerGUI
				.makeButtonMoused("<html>Save Pattern as New<br>+ Apply</html>", e -> {
					if (SwingUtilities.isLeftMouseButton(e)) {
						saveNotes(true);
					} else {
						new PatternNamePopup(patternName -> {
							PatternNameMarker pnm = new PatternNameMarker(patternName, true);
							patternNameBox.addItem(pnm);
							patternNameBox.setVal(pnm);
							// store in current part as new
							VibeComposerGUI.guiConfig.getPatternMaps().get(part).put(partOrder,
									patternName, getValues());
							apply();
							setSelectedPattern(sec.getPattern(part, partOrder));
						});
					}

				}));

		/*buttonPanel2.add(VibeComposerGUI.makeButton("Apply", e -> {
			apply();
		}));*/

		buttonPanel2.add(VibeComposerGUI.makeButton("Unapply", e -> {
			unapply();
		}));

		buttonPanel2.add(VibeComposerGUI.makeButton("Close without Applying", e -> {
			saveOnClose = false;
			close();
		}));
		return buttonPanel2;
	}

	private void loadParts() {
		loadParts(patternPartBox, patternPartOrderBox, patternNameBox);
	}

	public static void loadParts(ScrollComboBox<String> parts, ScrollComboBox<Integer> partOrders,
			ScrollComboBox<PatternNameMarker> names) {
		if (VibeComposerGUI.guiConfig.getPatternMaps().isEmpty()) {
			return;
		}
		names.removeAllItems();
		partOrders.removeAllItems();
		ScrollComboBox.addAll(
				VibeComposerGUI.guiConfig.getPatternMaps().get(parts.getSelectedIndex()).getKeys(),
				partOrders);
		if (partOrders.getItemCount() > 0) {
			partOrders.setSelectedIndex(0);
		}
	}

	private void loadNames() {
		loadNames(patternPartBox, patternPartOrderBox, patternNameBox);
	}

	public static void loadNames(ScrollComboBox<String> parts, ScrollComboBox<Integer> partOrders,
			ScrollComboBox<PatternNameMarker> names) {
		if (VibeComposerGUI.guiConfig.getPatternMaps().isEmpty()) {
			return;
		}
		names.removeAllItems();
		int part = parts.getSelectedIndex();
		int partOrder = partOrders.getSelectedItem();
		Set<String> patternNames = VibeComposerGUI.guiConfig.getPatternMaps().get(part)
				.getPatternNames(partOrder);
		List<PatternNameMarker> namesWithMarkers = patternNames.stream()
				.map(e -> new PatternNameMarker(e,
						VibeComposerGUI.guiConfig.getPatternRaw(part, partOrder, e) != null))
				.collect(Collectors.toList());
		ScrollComboBox.addAll(namesWithMarkers, names);
		if (names.getItemCount() > 0) {
			names.setSelectedIndex(0);
		}
	}

	private void loadNotes(boolean overwrite) {
		PhraseNotes pn = getPatternMap().get(patternPartOrderBox.getSelectedItem(),
				patternNameBox.getSelectedItem().name);
		if (pn == null) {
			return;
		}
		// TODO: overwrite notes setting
		if (overwrite) {
			setCustomValues(pn);
		} else {
			// import instead
		}
		if (applyOnLoadChoice) {
			apply();
		}
	}

	public void saveNotes(boolean newName) {
		saveNotes(newName, true);
	}

	public void saveNotes(boolean newName, boolean apply) {
		String patternName = (newName) ? UsedPattern.generateName(part, partOrder)
				: patternNameBox.getSelectedItem().name;

		if (newName) {
			PatternNameMarker pnm = new PatternNameMarker(patternName, true);
			patternNameBox.addItem(pnm);
			patternNameBox.setValRaw(pnm);
			// store in current part as new
			VibeComposerGUI.guiConfig.getPatternMaps().get(part).put(partOrder, patternName,
					getValues());
		} else {
			// store in selected part
			getPatternMap().put(patternPartOrderBox.getSelectedItem(), patternName, getValues());
		}
		if (apply) {
			apply();
			if (newName) {
				setSelectedPattern(sec.getPattern(part, partOrder));
			}
		}
	}

	private void setSelectedPattern(UsedPattern pat) {
		LG.i("Setting pattern selection: " + pat.toString());
		patternPartBox.setSelectedIndex(pat.getPart());
		patternPartOrderBox.setVal(pat.getPartOrder());
		patternNameBox.setValRaw(new PatternNameMarker(pat.getName(), true));
	}

	private UsedPattern getSelectedPattern() {
		return new UsedPattern(patternPartBox.getSelectedIndex(),
				patternPartOrderBox.getSelectedItem(), patternNameBox.getSelectedItem().name);
	}

	private UsedPattern getSelectedPatternNone() {
		return new UsedPattern(patternPartBox.getSelectedIndex(),
				patternPartOrderBox.getSelectedItem(), UsedPattern.NONE);
	}

	public PatternMap getPatternMap() {
		return VibeComposerGUI.guiConfig.getPatternMaps().get(patternPartBox.getSelectedIndex());
	}

	public void setCustomValues(PhraseNotes values) {

		int vmin = -1 * baseMargin * trackScope;
		int vmax = baseMargin * trackScope;
		if (!values.isEmpty()) {
			vmin += values.stream().map(e -> e.getPitch()).filter(e -> e >= 0).mapToInt(e -> e)
					.min().getAsInt();
			vmax += values.stream().map(e -> e.getPitch()).filter(e -> e >= 0).mapToInt(e -> e)
					.max().getAsInt();
		}
		mvea.setMin(Math.min(mvea.min, vmin));
		mvea.setMax(Math.max(mvea.max, vmax));


		mvea.setValues(values);
		saveToHistory();

		repaintMvea();
	}

	public void setupIdentifiers(int secPartNum, int secPartOrder) {
		part = secPartNum;
		partOrder = secPartOrder;
		UsedPattern pat = sec.getPattern(part, partOrder);

		if (pat != null && pat.isCustom(part, partOrder)) {
			setSelectedPattern(pat);
		} else {
			patternPartBox.setSelectedIndex(part);
			patternPartOrderBox.setVal(partOrder);
			String patName = sec.getPatternName(part, partOrder);
			patternNameBox.setValRaw(new PatternNameMarker(patName,
					VibeComposerGUI.guiConfig.getPatternRaw(part, partOrder, patName) != null));
		}
		frame.setTitle("Edit MIDI Phrase (Graphical) | Part: " + VibeComposerGUI.instNames[part]
				+ ", Order: " + secPartOrder);
	}

	private File buildMidiFileFromNotes() {
		Pair<ScaleMode, Integer> scaleKey = VibeComposerGUI
				.keyChangeAt(VibeComposerGUI.actualArrangement.getSections().indexOf(sec));

		Phrase phr = mvea.getValues().makePhrase();

		List<Note> notes = phr.getNoteList();
		int extraTranspose = 0;
		InstPanel ip = VibeComposerGUI.getInstList(part).get(partOrder);
		if (scaleKey != null) {
			MidiUtils.transposeNotes(notes, ScaleMode.IONIAN.noteAdjustScale,
					scaleKey.getLeft().noteAdjustScale,
					VibeComposerGUI.transposedNotesForceScale.isSelected());
			extraTranspose = scaleKey.getRight();
		}
		final int finalExtraTranspose = extraTranspose;
		notes.forEach(e -> {
			int pitch = e.getPitch() + VibeComposerGUI.transposeScore.getInt() + finalExtraTranspose
					+ ip.getTranspose();
			e.setPitch(pitch);
		});
		Score scr = new Score();
		Part prt = new Part();
		prt.add(phr);
		scr.add(prt);

		JMusicUtilsCustom.midi(scr, "tempMidi.mid");
		File f = new File("tempMidi.mid");
		return f;
	}

	public void deleteSelected() {
		if (mvea.selectedNotes.size() > 0) {
			mvea.selectedNotes.forEach(e -> {
				if (e.getRv() < MidiGenerator.DBL_ERR) {
					mvea.getValues().remove(e);
				} else {
					e.setPitch(Note.REST);
				}
			});
			mvea.selectedNotes.clear();
			mvea.selectedNotesCopy.clear();
			mvea.reset();
			saveToHistory();
		}
	}

	public void apply() {
		if (mvea != null && mvea.getValues() != null) {
			// TODO
			UsedPattern pat = getSelectedPattern();
			VibeComposerGUI.guiConfig.getPatternRaw(pat).setApplied(true);
			sec.putPattern(part, partOrder, pat);
			LG.i("Applied: " + pat.toString());
			VibeComposerGUI.scrollableArrangementActualTable.repaint();
		}
	}

	public void unapply() {
		if (mvea != null && mvea.getValues() != null) {
			UsedPattern pat = getSelectedPattern();
			VibeComposerGUI.guiConfig.getPatternRaw(pat).setApplied(false);
			sec.putPattern(part, partOrder, new UsedPattern(part, partOrder, UsedPattern.NONE));
			VibeComposerGUI.scrollableArrangementActualTable.repaint();
		}
	}

	public void undo() {
		loadFromHistory(notesHistoryIndex - 1);
	}

	public void redo() {
		loadFromHistory(notesHistoryIndex + 1);
	}

	public void setup(Section sec) {
		LG.i("Midi Edit Popup Setup, Part: " + part + ", Order: " + partOrder);

		if (!sec.containsPattern(part, partOrder)) {
			close();
			LG.i("MidiEditPopup cannot be setup - section doesn't contain the part/partOrder!");
			return;
		}

		setSec(sec);
		PhraseNotes values = loadSecValues(part, partOrder);
		/*if (values == null) {
			LG.i("Setup - not applicable, or not custom midi!");
			apply();
			repaintMvea();
			return;
		}*/
		if (values == null || values.isEmpty()) {
			LG.e("-----------------------Part needed to be recomposed on setup!--------------");
			values = recomposePart(false);
		}
		setCustomValues(values);

		LG.i("Custom MIDI setup successful: " + part + ", " + partOrder);
	}

	public void saveToHistory() {
		if (notesHistoryIndex + 1 < notesHistory.size() && notesHistory.size() > 0) {
			notesHistory = notesHistory.subList(0, notesHistoryIndex + 1);
		}

		notesHistory.add(mvea.getValues().copy());
		notesHistoryIndex = notesHistory.size() - 1;
		updateHistoryBox();
	}

	public void loadFromHistory(int index) {
		if (notesHistoryIndex == index) {
			return;
		}
		LG.i("Loading notes with index: " + index);
		if (notesHistory.size() > 0 && index >= 0 && index < notesHistory.size()) {
			mvea.setValues(notesHistory.get(index).copy());
			notesHistoryIndex = index;
			editHistoryBox.setSelectedIndex(index);
			repaintMvea();
		}
	}

	public PhraseNotes recomposePart(boolean isRandom) {
		MidiGenerator mg = VibeComposerGUI.melodyGen;

		mg.storeGlobalParts();
		mg.replaceWithSectionCustomChordDurations(sec);

		mg.progressionDurations = new ArrayList<>(sec.getGeneratedDurations());

		sec.putPattern(part, partOrder, getSelectedPatternNone());

		LG.i("Chord prog: " + mg.chordProgression.size());
		InstPart ip = MidiGenerator.gc.getInstPartList(part).stream()
				.filter(e -> e.getOrder() == partOrder).findFirst().get();

		int seed = ip.getPatternSeed();
		if (isRandom) {
			ip.setPatternSeed(new Random().nextInt());
		}
		List<Integer> variations = sec.getVariation(part, partOrder);
		switch (part) {
		case 0:
			mg.fillMelodyFromPart((MelodyPart) ip, mg.chordProgression, mg.rootProgression,
					sec.getTypeMelodyOffset(), sec, variations, false);
			break;
		case 1:
			mg.fillBassFromPart((BassPart) ip, mg.rootProgression, sec, variations);
			break;
		case 2:
			mg.fillChordsFromPart((ChordPart) ip, mg.chordProgression, sec, variations);
			break;
		case 3:
			mg.fillArpFromPart((ArpPart) ip, mg.chordProgression, sec, variations);
			break;
		case 4:
			mg.fillDrumsFromPart((DrumPart) ip, mg.chordProgression, sec.isClimax(), sec,
					variations);
			break;
		default:
			throw new IllegalArgumentException("Invalid part: " + part);
		}
		ip.setPatternSeed(seed);

		mg.replaceChordsDurationsFromBackup();
		mg.restoreGlobalPartsToGuiConfig();

		UsedPattern generatedPat = sec.getPattern(part, partOrder);
		LG.i("Recompose, new pattern: " + generatedPat.toString());
		PhraseNotes pn = MidiGenerator.gc.getPattern(generatedPat);
		VibeComposerGUI.guiConfig.putPattern(generatedPat, pn);

		mvea.min = 110;
		mvea.max = 10;


		if (pn == null) {
			new TemporaryInfoPopup("Recomposing produced no notes, quitting!", 1500);
			saveOnClose = false;
			close();
			return new PhraseNotes(Collections.singletonList(new Note("C4")));
		} else {
			setup(sec);
			//mg.fill
			return getValues();
		}
	}

	public void repaintMvea() {
		mvea.setAndRepaint();
		MidiEditArea.sectionLength = mvea.getValues().stream().map(e -> e.getRv())
				.mapToDouble(e -> e).sum();
		text.setText(mvea.getValues().toStringPitches());
	}

	public void updateHistoryBox() {
		editHistoryBox.removeAllItems();
		for (int i = 0; i < notesHistory.size(); i++) {
			editHistoryBox.addItem(i + " ("
					+ notesHistory.get(i).stream().filter(e -> e.getPitch() >= 0).count() + ")");
		}
		editHistoryBox.setSelectedIndex(editHistoryBox.getItemCount() - 1);
	}

	public void setParent(InstPanel parent) {
		this.ip = parent;
	}

	public InstPanel getParent() {
		return ip;
	}

	@Override
	protected void addFrameWindowOperation() {
		frame.addWindowListener(new WindowListener() {

			@Override
			public void windowOpened(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowClosing(WindowEvent e) {
				if (saveOnClose) {
					saveNotes(false);
				}
			}

			@Override
			public void windowClosed(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowIconified(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowDeiconified(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowActivated(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowDeactivated(WindowEvent e) {
				// Auto-generated method stub

			}

		});
	}

	public JTextField getText() {
		return text;
	}

	public void setText(JTextField text) {
		this.text = text;
	}

	public Section getSec() {
		return sec;
	}

	public void setSec(Section sec) {
		this.sec = sec;
	}

	public boolean isSectionCustom() {
		return sec.containsPattern(part, partOrder)
				&& sec.getPattern(part, partOrder).isCustom(part, partOrder);
	}

	public PhraseNotes getValues() {
		return mvea.getValues();
	}

}
