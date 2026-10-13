package org.vibehistorian.vibecomposer.Panels;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.Components.Chordlet;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.SwingUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ChordletPanel extends JPanel {

	private static final long serialVersionUID = -4372897856098663297L;

	private List<Chordlet> chordlets = new ArrayList<>();

	private JScrollPane chordletsPane = new JScrollPane();
	private JPanel chordletsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
	private JPanel rawChordsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
	private JPanel extrasPanel = new JPanel();

	private JTextField rawChords = null;
	private boolean chordletsDisplayed = true;
	private java.util.function.Supplier<org.vibehistorian.vibecomposer.GUIConfig> generationConfig;
	private Runnable refreshGenerationConfig;
	private long rerollSequence;
	private static final class Snapshot {
		final List<String> chords;
        final List<org.vibehistorian.vibecomposer.generation.ChordLock> locks;
        final List<String> ids;
        Snapshot(List<String> chords,List<org.vibehistorian.vibecomposer.generation.ChordLock> locks,List<String> ids) { this.chords = chords; this.locks = locks; this.ids = ids; }
	}
	private final java.util.Deque<Snapshot> undo = new java.util.ArrayDeque<>();
	private final java.util.Deque<Snapshot> redo = new java.util.ArrayDeque<>();
	private boolean harmonyStale;
	public boolean isHarmonyStale() { return harmonyStale; }
    public void markHarmonyCurrent() { harmonyStale = false; setToolTipText("Harmony matches the generated score."); }
    public List<String> getEventIds() { return chordlets.stream().map(Chordlet::getEventId).collect(Collectors.toList()); }
    public void setEventIds(List<String> ids) {
        if (ids.size() != chordlets.size() || ids.stream().anyMatch(id -> id == null || id.trim().isEmpty())
                || new java.util.HashSet<>(ids).size() != ids.size()) { throw new IllegalArgumentException("Chord event identities must be unique and match the progression"); }
        for (int i = 0; i < ids.size(); i++) { chordlets.get(i).setEventId(ids.get(i)); }
    }
	public List<org.vibehistorian.vibecomposer.generation.ChordLock> getPropertyLocks() {
		List<org.vibehistorian.vibecomposer.generation.ChordLock> result = new ArrayList<>();
		for (int i = 0; i < chordlets.size(); i++) {
			if (chordlets.get(i).getGenerationLocks() != 0) { result.add(new org.vibehistorian.vibecomposer.generation.ChordLock(i,chordlets.get(i).getGenerationLocks())); }
		}
		return result;
	}
	public void setPropertyLocks(List<org.vibehistorian.vibecomposer.generation.ChordLock> locks) {
		for (Chordlet chord : chordlets) { chord.setGenerationLocks(0); }
		for (org.vibehistorian.vibecomposer.generation.ChordLock lock : locks) {
			if (lock.getIndex() < chordlets.size()) { chordlets.get(lock.getIndex()).setGenerationLocks(lock.getMask()); }
		}
	}

	public void setGenerationContext(java.util.function.Supplier<org.vibehistorian.vibecomposer.GUIConfig> config, Runnable refresh) {
		generationConfig = config;
		refreshGenerationConfig = refresh;
	}

	public List<Integer> getLockedIndices() {
		List<Integer> result = new ArrayList<>();
		for (int i = 0; i < chordlets.size(); i++) { if (chordlets.get(i).isGenerationLocked()) { result.add(i); } }
		return result;
	}

	public void setLockedIndices(List<Integer> indices) {
		for (int i = 0; i < chordlets.size(); i++) { chordlets.get(i).setGenerationLocked(indices.contains(i)); }
	}

	public void rememberGenerationEdit() {
        undo.push(new Snapshot(getChordList(),getPropertyLocks(),getEventIds()));
		if (undo.size() > 100) { undo.removeLast(); }
        redo.clear(); harmonyStale = true;
        setToolTipText("Harmony was edited; regenerate to update melody and accompaniment.");
	}

	public void undoGenerationEdit() {
		if (!undo.isEmpty()) {
            redo.push(new Snapshot(getChordList(),getPropertyLocks(),getEventIds())); Snapshot previous = undo.pop();
            setupChords(previous.chords); setPropertyLocks(previous.locks); setEventIds(previous.ids); harmonyStale = true;
		}
	}
	public void redoGenerationEdit() {
		if (!redo.isEmpty()) {
            undo.push(new Snapshot(getChordList(),getPropertyLocks(),getEventIds())); Snapshot next = redo.pop();
            setupChords(next.chords); setPropertyLocks(next.locks); setEventIds(next.ids); harmonyStale = true;
		}
	}

	public void rerollChord(Chordlet chord) {
		if (generationConfig == null || chord.isGenerationLocked()) { return; }
		refreshGenerationConfig.run();
		org.vibehistorian.vibecomposer.GUIConfig config = generationConfig.get();
		int index = chordlets.indexOf(chord);
		try {
			List<String> next = org.vibehistorian.vibecomposer.generation.HarmonicProgressionPlanner.reroll(
					getChordList(), index, config, config.getRandomSeed() + ++rerollSequence);
			rememberGenerationEdit();
			chord.setChordText(next.get(index));
		} catch (IllegalArgumentException invalid) {
			JOptionPane.showMessageDialog(this, invalid.getMessage(), "Chord reroll", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	private static final int extrasSize = 70;
	private int fullSize = 600;
	private static final int fullHeight = 35;
	private static final int heightOffset = 2;

	private void setupPanel() {
		setPreferredSize(new Dimension(fullSize, fullHeight));
		chordletsPane
				.setPreferredSize(new Dimension(fullSize - extrasSize, fullHeight - heightOffset));
		chordletsPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		chordletsPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		//SwingUtils.setupScrollpanePriorityScrolling(chordletsPane);
		chordletsPanel.setPreferredSize(
				new Dimension(fullSize - extrasSize, fullHeight * 10 / 4 - heightOffset * 2));
		rawChordsPanel
				.setPreferredSize(new Dimension(fullSize - extrasSize, fullHeight - heightOffset));
		extrasPanel.setPreferredSize(new Dimension(extrasSize - 10, fullHeight - heightOffset));
		extrasPanel.setLayout(new BoxLayout(extrasPanel, BoxLayout.X_AXIS));

		chordletsPane.setOpaque(true);
		chordletsPanel.setOpaque(true);
		rawChordsPanel.setOpaque(false);
		extrasPanel.setOpaque(false);

		//chordletsPanel.setBackground(OMNI.alphen(Color.cyan, 20));
		rawChords = new JTextField("", fullSize / 20);
		rawChordsPanel.setVisible(false);
		rawChords.setText(getChordListString());
		String tooltip = "Allowed chords: C/D/E/F/G/A/B + "
				+ StringUtils.join(MidiUtils.SPICE_NAMES_LIST, " / ");
		rawChords.setToolTipText(tooltip);
		JButton okButton = SwingUtils.makeButton("OK", e -> {
			toggleChordDisplay();
		});
		okButton.setPreferredSize(new Dimension(30, 25));
		okButton.setMargin(new Insets(0, 0, 0, 0));
		rawChordsPanel.add(okButton);
		rawChordsPanel.add(rawChords);
		rawChords.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				toggleChordDisplay();
			}
		});


		JButton addButton = SwingUtils.makeButton("+", e -> {
			addChord("C", true);
		});

		setupChordPickers(addButton);

		JButton switchButton = SwingUtils.makeButton("<>", e -> {
			toggleChordDisplay();
		});
		addButton.setMargin(new Insets(0, 0, 4, 0));
		switchButton.setMargin(new Insets(0, 0, 4, 0));
		addButton.setPreferredSize(new Dimension(30, 30));
		switchButton.setPreferredSize(new Dimension(30, 30));
		extrasPanel.add(addButton);
		extrasPanel.add(switchButton);

		chordletsPane.setViewportView(chordletsPanel);
		add(chordletsPane);
		add(rawChordsPanel);
		add(extrasPanel);

		chordletsPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				toggleChordDisplay();
			}
		});
		rawChordsPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				toggleChordDisplay();
			}
		});
	}

	public void setupChordPickers(JComponent addButton) {
		SwingUtils.addPopupMenu(addButton, (evt, e) -> {
			addChord(e, true);
		}, e -> {
			if (SwingUtilities.isRightMouseButton(e)) {
				return true;
			}
			return false;
		}, MidiUtils.SEMITONE_LETTERS, MidiUtils.SEMITONE_LETTERS.stream()
				.map(e -> Chordlet.getColorForChord(e)).collect(Collectors.toList()));

		List<String> allChords = new ArrayList<>();
		MidiUtils.SPICE_NAMES_LIST.forEach(s -> {
			MidiUtils.SEMITONE_LETTERS_ORDERED.forEach(e -> {
				allChords.add(e + s);
			});
		});


		SwingUtils.addPopupMenu(addButton, (evt, e) -> {
			addChord(e, true);
		}, e -> {
			if (SwingUtilities.isMiddleMouseButton(e)) {
				return true;
			}
			return false;
		}, allChords, allChords.stream().map(e -> Chordlet.getColorForChord(e))
				.collect(Collectors.toList()), 12);
	}

	public ChordletPanel(int width, List<String> chords) {
		fullSize = width;
		setupChords(chords);
		setupPanel();
	}

	public ChordletPanel(int width, String... chords) {
		this(width, Arrays.asList(chords));
	}

	public ChordletPanel(int width, String chords) {
		this(width, Arrays.asList(chords.split(",")));
	}

	public void toggleChordDisplay() {
		if (chordletsDisplayed) {
			rawChords.setText(getChordListString());
		} else {
			boolean setupSuccess = setupChords(rawChords.getText());
			if (!setupSuccess) {
				LG.e("Invalid chords entered!");
				SwingUtils.flashComponent(rawChords, OMNI.alphen(Color.red, 100), 100, 200);
				return;
			}
		}

		chordletsDisplayed = !chordletsDisplayed;
		chordletsPane.setVisible(chordletsDisplayed);
		rawChordsPanel.setVisible(!chordletsDisplayed);

		if (!chordletsDisplayed) {
			rawChords.requestFocus();
			SwingUtilities.invokeLater(() -> {
				rawChords.setSelectionStart(rawChords.getText().length());
				rawChords.setSelectionEnd(rawChords.getText().length());
			});

		}
	}

	public boolean setupChords(List<String> chords) {
		try {
			for (String symbol : chords) { if (MidiUtils.mappedChord(symbol) == null) { return false; } }
		} catch (RuntimeException invalid) { return false; }
        List<String> previousIds = getEventIds();
        chordletsPanel.setVisible(false);
		try {
			chordlets.clear();
			Component[] comps = chordletsPanel.getComponents();
			for (Component c : comps) {
				if (c instanceof Chordlet) {
					chordletsPanel.remove(c);
				}
			}
			//setOpaque(false);
			chords.forEach(e -> {
				if (MidiUtils.mappedChord(e) != null) {
					addChord(e, false);
				}
			});
			LG.d("Set up chords: " + chordlets.size());
		} catch (Exception e) {
			LG.e(e);
		}
        if (previousIds.size() == chordlets.size()) { setEventIds(previousIds); }
        update();
        chordletsPanel.setVisible(true);
		return chordlets.size() == chords.size();
	}

	public boolean setupChords(String chords) {
		String[] split = chords.split(",");
		List<String> chords2 = new ArrayList<>();
		for (String s : split) {
			chords2.add(s.trim());
		}
		return setupChords(chords2);
	}

	public void addChord(String chord, boolean singleAdd) {
		addChord(chord, singleAdd, null);
	}

    public Chordlet addChord(String chord, boolean singleAdd, Integer index) {
        if (singleAdd) { rememberGenerationEdit(); }
		Chordlet chordlet = new Chordlet(chord.trim(), this);
		if (index != null) {
			chordlets.add(index, chordlet);
			chordletsPanel.add(chordlet, (int) index);
		} else {
			chordlets.add(chordlet);
			chordletsPanel.add(chordlet);
		}

		if (singleAdd) {
			update();
		}
		return chordlet;
	}

    public void removeChordlet(Chordlet chordlet, boolean singleRemove) {
        if (singleRemove) { rememberGenerationEdit(); }
		chordlets.remove(chordlet);
		chordletsPanel.remove(chordlet);
		if (singleRemove) {
			update();
		}
	}

	public int chordCount() {
		return chordlets.size();
	}

	public List<Chordlet> getChordletsRaw() {
		return chordlets;
	}

	public List<Chordlet> getChordlets() {
		return new ArrayList<>(chordlets);
	}

	public List<String> getChordList() {
		return chordlets.stream().map(Chordlet::getChordText).collect(Collectors.toList());
	}

	public String getChordListString() {
		return StringUtils.join(getChordList(), ",");
	}

	public void update() {
		if (!chordletsDisplayed) {
			rawChords.setText(getChordListString());
		}
		revalidate();
		repaint();
	}

	public void resetChordlets() {
		chordlets.forEach(e -> e.resetToBaseChord());
		update();
	}

	public void cullChordsAbove(int maxChordProgressionLength) {
		int size = chordCount();
		for (int i = size - 1; i >= maxChordProgressionLength; i--) {
			removeChordlet(chordlets.get(i), false);
		}
		update();
	}

	public void handleMouseDrag(Chordlet source, MouseEvent evt) {
		int overlapIndex = -1;
		int sourceIndex = -1;
		//LG.i("Checking matches for Chordlet: " + source.getChordText());
		for (int i = 0; i < chordlets.size(); i++) {
			if (chordlets.get(i) == source) {
				sourceIndex = i;
				continue;
			}
			/*LG.i(evt.getLocationOnScreen() + ", comp: " + chordlets.get(i).getLocationOnScreen()
					+ ", comp size: " + chordlets.get(i).getSize());*/
			if (overlapIndex < 0 && OMNI.mouseInComp(chordlets.get(i), evt.getLocationOnScreen())
					&& !chordlets.get(i).mouseOverCloseButton(evt.getLocationOnScreen().x
							- chordlets.get(i).getLocationOnScreen().x)) {
				overlapIndex = i;
				if (sourceIndex >= 0) {
					break;
				}
			}
		}
		if (overlapIndex < 0) {
			return;
		}
        boolean copy = evt.isControlDown();
        rememberGenerationEdit();
		int insertionIndex = overlapIndex;
		LG.i("Overlap: " + overlapIndex + ", CTRL: " + evt.isControlDown());
		if (copy) {
			if (overlapIndex < sourceIndex) {
				insertionIndex++;
			}
			addChord(source.getChordText(), false, insertionIndex);
			source.reset();
		} else {
			if (overlapIndex < sourceIndex) {
				chordletsPanel.add(source, insertionIndex);

				chordlets.add(insertionIndex, source);
				chordlets.remove(sourceIndex + 1);
			} else {
				if (overlapIndex - sourceIndex == 1) {
					chordletsPanel.add(chordlets.get(overlapIndex), sourceIndex);
				} else {
					chordletsPanel.add(source, overlapIndex);
				}


				chordlets.add(insertionIndex + 1, source);
				chordlets.remove(sourceIndex);
			}
			//removeChordlet(source, false);
		}
		if (Math.abs(sourceIndex - overlapIndex) > 1) {
			source.reset();
		}
		update();
	}

	public void alignWithMelodyTargetNotes(List<Integer> melodyTargetNotes) {
		// goal1: align top notes of chords with melody
		// goal2: also copy up/down flow of melody

		Integer prevNote = null;
		for (int i = 0; i < chordCount(); i++) {
			int[] mapped = MidiUtils.mappedChord(chordlets.get(i).getSpicedText());
			List<Integer> pitches = MidiUtils.chordToPitches(mapped);
			int melodyChoice = melodyTargetNotes.get(i % melodyTargetNotes.size());
			int melodyNote = MidiUtils.MAJ_SCALE.get((melodyChoice + 70) % 7) % 12;
			// round note to closest from chord
			int noteInChordIndex = pitches
					.indexOf(MidiUtils.getClosestFromList(pitches, melodyNote));

			int inversion = 0;
			if (noteInChordIndex < mapped.length - 1) {
				inversion = (melodyChoice >= 0) ? (noteInChordIndex + 1)
						: (-1 * (mapped.length - noteInChordIndex - 1));
			}

			int newNote = MidiUtils.getXthChordNote(mapped.length - 1 + inversion, mapped);
			if (i > 0) {
				if (newNote > prevNote && melodyTargetNotes
						.get((i - 1) % melodyTargetNotes.size()) > melodyChoice) {
					inversion -= mapped.length;
				} else if (newNote < prevNote && melodyTargetNotes
						.get((i - 1) % melodyTargetNotes.size()) < melodyChoice) {
					inversion += mapped.length;
				} else if (newNote - 12 >= prevNote) {
					inversion -= mapped.length;
				} else if (newNote + 12 <= prevNote) {
					inversion += mapped.length;
				}
			} else if (inversion != 0) {
				if (inversion > 0 && Math.abs(inversion - mapped.length) < inversion) {
					inversion -= mapped.length;
				} else if (inversion < 0
						&& Math.abs(inversion + mapped.length) < Math.abs(inversion)) {
					inversion += mapped.length;
				}
			}
			prevNote = MidiUtils.getXthChordNote(mapped.length - 1 + inversion, mapped);
			chordlets.get(i).updateInversion(inversion);
		}
	}
}
