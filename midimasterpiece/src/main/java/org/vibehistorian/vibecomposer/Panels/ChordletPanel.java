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
	private JPanel rawChordsPanel = new JPanel();
	private JPanel extrasPanel = new JPanel();

	private JTextField rawChords = new JTextField("", 46);
	private boolean chordletsDisplayed = true;

	private static final int extrasSize = 70;
	private static final int fullSize = 600;
	private static final int fullHeight = 35;
	private static final int heightOffset = 2;

	private void setupPanel() {
		setPreferredSize(new Dimension(fullSize, fullHeight));
		chordletsPane
				.setPreferredSize(new Dimension(fullSize - extrasSize, fullHeight - heightOffset));
		chordletsPanel
				.setPreferredSize(new Dimension(fullSize - extrasSize, fullHeight - heightOffset));
		rawChordsPanel
				.setPreferredSize(new Dimension(fullSize - extrasSize, fullHeight - heightOffset));
		extrasPanel.setPreferredSize(new Dimension(extrasSize - 10, fullHeight - heightOffset));
		extrasPanel.setLayout(new BoxLayout(extrasPanel, BoxLayout.X_AXIS));

		chordletsPanel.setBackground(OMNI.alphen(Color.cyan, 20));

		rawChordsPanel.setVisible(false);
		rawChords.setText(getChordListString());
		String tooltip = "Allowed chords: C/D/E/F/G/A/B + "
				+ StringUtils.join(MidiUtils.SPICE_NAMES_LIST, " / ");
		rawChords.setToolTipText(tooltip);
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

		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				toggleChordDisplay();
			}
		});
	}

	public ChordletPanel(List<String> chords) {
		setupChords(chords);
		setupPanel();
	}

	public ChordletPanel(String... chords) {
		this(Arrays.asList(chords));
	}

	public ChordletPanel(String chords) {
		this(Arrays.asList(chords.split(",")));
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
		Chordlet chordlet = new Chordlet(chord.trim(), this);
		chordlets.add(chordlet);
		chordletsPanel.add(chordlet);
		if (singleAdd) {
			update();
		}
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
}
