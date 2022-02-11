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
import java.util.List;
import java.util.Random;

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
import org.vibehistorian.vibecomposer.Helpers.PhraseNote;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
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

	MidiEditArea mvea = null;
	InstPanel parent = null;
	JTextField text = null;
	Section sec = null;
	public ScrollComboBox<String> highlightMode = new ScrollComboBox<>(false);
	public ScrollComboBox<String> snapToTimeGrid = new ScrollComboBox<>(false);
	public CheckButton regenerateInPlaceOnChange = new CheckButton("R~ on Change",
			regenerateInPlaceChoice);
	public CheckButton snapToScaleGrid = new CheckButton("Snap to Scale", snapToGridChoice);
	public static final double[] TIME_GRID = new double[] { 0.125, 1 / 6.0, 0.25, 1 / 3.0, 0.5,
			2 / 3.0, 1.0, 4 / 3.0, 2.0, 4.0 };

	public int notesHistoryIndex = 0;

	public List<PhraseNotes> notesHistory = new ArrayList<>();


	public static double getTimeGrid() {
		return TIME_GRID[snapToTimeGridChoice];
	}

	public static final int baseMargin = 5;
	public static int trackScope = 1;
	public static int trackScopeUpDown = 0;


	public int part = 0;
	public int partOrder = 0;
	public static int highlightModeChoice = 3;
	public static int snapToTimeGridChoice = 2;
	public static boolean snapToGridChoice = true;
	public static boolean regenerateInPlaceChoice = false;
	public CheckButton applyToMainBtn;
	public JLabel historyLabel = new JLabel("Edit History:");
	public ScrollComboBox<String> editHistoryBox = new ScrollComboBox<>(false);
	public boolean applyOnClose = true;
	public JList<File> generatedMidi;

	public MidiEditPopup(Section section, int secPartNum, int secPartOrder) {
		super("Edit MIDI Phrase (Graphical)", 14);
		sec = section;
		part = secPartNum;
		partOrder = secPartOrder;
		applyOnClose = true;
		LG.i("Midi Edit Popup, Part: " + secPartNum + ", Order: " + secPartOrder);
		PhraseNotes values = sec.getPartPhraseNotes().get(part).get(partOrder);
		if (values == null) {
			values = VibeComposerGUI.getAffectedPanels(part).get(partOrder).getCustomMidi();
		}
		if (values == null) {
			values = new PhraseNotes();
		} else {
			values = values.copy();
		}
		values.setCustom(true);

		ScrollComboBox.addAll(
				new String[] { "No Highlight", "Scale/Key", "Chords", "Scale/Key and Chords" },
				highlightMode);
		highlightMode.setSelectedIndex(highlightModeChoice);
		highlightMode.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				highlightModeChoice = highlightMode.getSelectedIndex();
				mvea.repaint();
			}
		});

		ScrollComboBox.addAll(new String[] { "1/32", "1/24", "1/16", "1/12", "1/8", "1/6", "1/4",
				"1/3", "1/2", "1" }, snapToTimeGrid);
		snapToTimeGrid.setSelectedIndex(snapToTimeGridChoice);
		snapToTimeGrid.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				snapToTimeGridChoice = snapToTimeGrid.getSelectedIndex();
				mvea.repaint();
			}
		});
		trackScopeUpDown = 0;
		int vmin = -1 * baseMargin * trackScope;
		int vmax = baseMargin * trackScope;
		if (values != null && !values.isEmpty()) {
			vmin += values.stream().map(e -> e.getPitch()).filter(e -> e >= 0).mapToInt(e -> e)
					.min().getAsInt();
			vmax += values.stream().map(e -> e.getPitch()).filter(e -> e >= 0).mapToInt(e -> e)
					.max().getAsInt();
		}
		int min = vmin;
		int max = vmax;
		mvea = new MidiEditArea(min, max, values);
		mvea.setPop(this);
		mvea.setPreferredSize(new Dimension(1500, 600));

		MidiEditArea.sectionLength = values.stream().map(e -> e.getRv()).mapToDouble(e -> e).sum();

		JPanel allPanels = new JPanel();
		allPanels.setLayout(new BoxLayout(allPanels, BoxLayout.Y_AXIS));
		allPanels.setMaximumSize(new Dimension(1500, 750));

		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(0, 4, 0, 0));
		buttonPanel.setPreferredSize(new Dimension(1500, 50));

		buttonPanel.add(VibeComposerGUI.makeButton("???", e -> {
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

							mvea.getValues().get(i).setPitch(12 * (pitch / 12) + closestNormalized);
						} else {
							mvea.getValues().get(i).setPitch(pitch);
						}

					}
				}
			}
			saveToHistory();
			repaintMvea();
		}));

		buttonPanel.add(VibeComposerGUI.makeButton("Undo", e -> undo()));
		buttonPanel.add(VibeComposerGUI.makeButton("Redo", e -> redo()));

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
		buttonPanel.add(midiDragDropPanel);

		JPanel buttonPanel2 = new JPanel();
		buttonPanel2.setLayout(new GridLayout(0, 4, 0, 0));
		buttonPanel2.setPreferredSize(new Dimension(1500, 50));

		applyToMainBtn = new CheckButton("Apply to Global",
				VibeComposerGUI.getInstList(part).get(partOrder).getCustomMidiToggle());
		applyToMainBtn.setFunc(e -> {
			if (applyToMainBtn.isSelected()) {
				mvea.getValues().setCustom(false);
			}
			apply();
		});
		buttonPanel2.add(applyToMainBtn);
		buttonPanel2.add(VibeComposerGUI.makeButton("Discard / Save Global", e -> {
			mvea.getValues().setCustom(false);
			close();
		}));

		buttonPanel2.add(VibeComposerGUI.makeButton("Exit / Save Section", e -> close()));
		buttonPanel2.add(VibeComposerGUI.makeButton("Close without Saving", e -> {
			applyOnClose = false;
			close();
		}));

		JPanel mveaPanel = new JPanel();
		mveaPanel.setPreferredSize(new Dimension(1500, 600));
		mveaPanel.setMinimumSize(new Dimension(1500, 600));
		mveaPanel.add(mvea);

		JPanel textPanel = new JPanel();
		textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.X_AXIS));
		text = new JTextField(values.toStringPitches());
		textPanel.add(text);
		textPanel.add(VibeComposerGUI.makeButton("Apply", e -> {
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

		snapToScaleGrid.setFunc(e -> {
			snapToGridChoice = snapToScaleGrid.isSelected();
		});
		regenerateInPlaceOnChange.setFunc(e -> {
			regenerateInPlaceChoice = regenerateInPlaceOnChange.isSelected();
		});
		textPanel.add(regenerateInPlaceOnChange);
		textPanel.add(new JLabel("  Highlight Mode:"));
		textPanel.add(highlightMode);
		textPanel.add(new JLabel("  Snap To Time:"));
		textPanel.add(snapToTimeGrid);
		textPanel.add(snapToScaleGrid);


		JButton recompButt = new JButton("Recompose Part");
		recompButt.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				recomposePart(SwingUtilities.isMiddleMouseButton(evt));
			}
		});
		textPanel.add(recompButt);

		editHistoryBox.setFunc(e -> {
			if (notesHistoryIndex != editHistoryBox.getSelectedIndex()) {
				loadFromHistory(editHistoryBox.getSelectedIndex());
			}
		});
		textPanel.add(historyLabel);
		textPanel.add(editHistoryBox);

		allPanels.add(buttonPanel);
		allPanels.add(buttonPanel2);
		allPanels.add(textPanel);

		allPanels.add(mveaPanel);

		if (values.isEmpty()) {
			recomposePart(false);
		} else {
			saveToHistory();
		}
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

		frame.setLocation(VibeComposerGUI.vibeComposerGUI.getLocation());
		frame.add(allPanels);
		frame.pack();
		frame.setVisible(true);
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
					scaleKey.getLeft().noteAdjustScale);
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
			if (mvea.getValues().isCustom()) {
				sec.addPhraseNotes(part, partOrder, mvea.getValues().copy());
			} else if (sec.containsPhrase(part, partOrder)) {
				sec.getPhraseNotes(part, partOrder).setCustom(false);
			}
			if (applyToMainBtn.isSelected()) {
				if (VibeComposerGUI.getInstList(part).get(partOrder).getCustomMidi() == null
						|| !mvea.getValues().isCustom()) {
					PhraseNotes pnCopy = mvea.getValues().copy();
					pnCopy.setCustom(true);
					VibeComposerGUI.getInstList(part).get(partOrder).setCustomMidi(pnCopy);
				}
			} else {
				VibeComposerGUI.getInstList(part).get(partOrder).setCustomMidi(null);
			}
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

		if (!sec.containsPhrase(part, partOrder)) {
			close();
			LG.i("MidiEditPopup cannot be setup - section doesn't contain the part/partOrder!");
			return;
		}

		setSec(sec);
		PhraseNotes values = sec.getPhraseNotes(part, partOrder);
		if (applyToMainBtn.isSelected() && (values == null || !values.isCustom())) {
			LG.i("Setup - not applicable, or not custom midi!");
			apply();
			repaintMvea();
			return;
		}

		int vmin = -1 * baseMargin;
		int vmax = baseMargin;

		values = values.copy();

		if (!values.isEmpty()) {
			vmin += values.stream().map(e -> e.getPitch()).filter(e -> e >= 0).mapToInt(e -> e)
					.min().getAsInt();
			vmax += values.stream().map(e -> e.getPitch()).filter(e -> e >= 0).mapToInt(e -> e)
					.max().getAsInt();
		}
		mvea.setMin(Math.min(mvea.min, vmin));
		mvea.setMax(Math.max(mvea.max, vmax));
		values.setCustom(true);


		mvea.setValues(values);
		saveToHistory();

		apply();
		LG.i("Custom MIDI setup successful: " + part + ", " + partOrder);
		repaintMvea();
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

	public void recomposePart(boolean isRandom) {
		MidiGenerator mg = VibeComposerGUI.melodyGen;

		mg.storeGlobalParts();
		mg.replaceWithSectionCustomChordDurations(sec);

		mg.progressionDurations = new ArrayList<>(sec.getGeneratedDurations());

		sec.getPhraseNotes(part, partOrder).setCustom(false);

		LG.i("Chord prog: " + mg.chordProgression.size());
		InstPart ip = MidiGenerator.gc.getInstPartList(part).get(partOrder);

		int seed = ip.getPatternSeed();
		if (isRandom) {
			ip.setPatternSeed(new Random().nextInt());
		}
		if (VibeComposerGUI.getInstList(part).get(partOrder).getCustomMidi() != null) {
			VibeComposerGUI.getInstList(part).get(partOrder).getCustomMidi().setCustom(false);
		}
		List<Integer> variations = sec.getVariation(part, partOrder);
		switch (part) {
		case 0:
			mg.fillMelodyFromPart((MelodyPart) ip, mg.chordProgression, mg.rootProgression,
					sec.getTypeMelodyOffset(), sec, variations);
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
		sec.getPhraseNotes(part, partOrder).setCustom(true);

		mg.replaceChordsDurationsFromBackup();
		mg.restoreGlobalPartsToGuiConfig();
		mvea.min = 110;
		mvea.max = 10;
		setup(sec);
		//mg.fill
	}

	public void repaintMvea() {
		mvea.repaint();
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
		this.parent = parent;
	}

	public InstPanel getParent() {
		return parent;
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
				if (applyOnClose) {
					apply();
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
		PhraseNotes pn = sec.getPartPhraseNotes().get(part).get(partOrder);
		if (pn != null && pn.isCustom()) {
			return true;
		} else {
			return false;
		}
	}

	public PhraseNotes getValues() {
		return mvea.getValues();
	}

}
