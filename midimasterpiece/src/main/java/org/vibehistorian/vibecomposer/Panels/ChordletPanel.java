package org.vibehistorian.vibecomposer.Panels;

import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Components.Chordlet;

public class ChordletPanel extends JPanel {

	private static final long serialVersionUID = -4372897856098663297L;

	private List<Chordlet> chordlets = new ArrayList<>();
	private JPanel chordletsPanel = new JPanel();
	private JPanel rawChordsPanel = new JPanel();
	private JTextField rawChords = new JTextField("", 12);
	private boolean chordletsDisplayed = true;

	private void setupPanel() {
		setPreferredSize(new Dimension(400, 40));
		chordletsPanel.setPreferredSize(new Dimension(400, 40));
		rawChordsPanel.setPreferredSize(new Dimension(400, 40));
		rawChordsPanel.setVisible(false);
		rawChords.setText(getChordListString());
		rawChordsPanel.add(rawChords);

		add(chordletsPanel);
		add(rawChordsPanel);

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
				return;
			}
		}

		chordletsDisplayed = !chordletsDisplayed;
		chordletsPanel.setVisible(chordletsDisplayed);
		rawChordsPanel.setVisible(!chordletsDisplayed);
	}

	public boolean setupChords(List<String> chords) {
		chordlets.clear();
		chordletsPanel.removeAll();
		//setOpaque(false);
		chords.forEach(e -> {
			if (MidiUtils.mappedChord(e) != null) {
				addChord(e);
			}
		});
		LG.i("Set up chords: " + chordlets.size());
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

	public void addChord(String chord) {
		Chordlet chordlet = new Chordlet(chord.trim(), this);
		chordlets.add(chordlet);
		chordletsPanel.add(chordlet);
		update();
	}

	public void removeChordlet(Chordlet chordlet) {
		chordlets.remove(chordlet);
		chordletsPanel.remove(chordlet);
		update();
	}

	public List<String> getChordList() {
		return chordlets.stream().map(e -> e.getChordText()).collect(Collectors.toList());
	}

	public String getChordListString() {
		return StringUtils.join(getChordList(), ",");
	}

	public void update() {
		revalidate();
		repaint();
	}
}
