package org.vibehistorian.vibecomposer.Panels;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.SwingUtils;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Components.Chordlet;

public class ChordletPanel extends JPanel {

	private static final long serialVersionUID = -4372897856098663297L;

	private List<Chordlet> chordlets = new ArrayList<>();

	private JScrollPane chordletsPane = new JScrollPane();
	private JPanel chordletsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
	private JPanel rawChordsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
	private JPanel extrasPanel = new JPanel();

	private JTextField rawChords = null;
	private boolean chordletsDisplayed = true;

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
		JButton okButton = VibeComposerGUI.makeButton("OK", e -> {
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


		JButton addButton = VibeComposerGUI.makeButton("+", e -> {
			addChord("C", true);
		});

		setupChordPickers(addButton);

		JButton switchButton = VibeComposerGUI.makeButton("<>", e -> {
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
			LG.i("Set up chords: " + chordlets.size());
		} catch (Exception e) {
			e.printStackTrace();
		}
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
		return chordlets.stream().map(e -> e.getChordText()).collect(Collectors.toList());
	}

	public String getChordListString() {
		return StringUtils.join(getChordList(), ",");
	}

	public void update() {
		/*if (!VibeComposerGUI.userChordsEnabled.isSelected()) {
			VibeComposerGUI.userChordsEnabled.setSelected(true);
			VibeComposerGUI.userChordsEnabled.repaint();
		}*/
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

		for (int i = 0; i < chordCount(); i++) {
			int[] mapped = MidiUtils.mappedChord(chordlets.get(i).getSpicedText());
			int melodyChoice = melodyTargetNotes.get(i % melodyTargetNotes.size());
			int melodyNote = MidiUtils.MAJ_SCALE.get((melodyChoice + 70) % 7) % 12;

			if (mapped[mapped.length - 1] % 12 == melodyNote) {
				chordlets.get(i).updateInversion(0);
				continue;
			}

			for (int j = 0; j < mapped.length - 1; j++) {
				if ((mapped[j] % 12) == melodyNote) {
					int inversion = (melodyChoice >= 0) ? (j + 1) : (-1 * (mapped.length - j + 1));
					chordlets.get(i).updateInversion(inversion);
				}

			}
		}

	}
}
