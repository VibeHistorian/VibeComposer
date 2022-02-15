package org.vibehistorian.vibecomposer.Panels;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JPanel;
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

		rawChords.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				toggleChordDisplay();
			}
		});

		add(chordletsPanel);
		add(rawChordsPanel);

		JButton addButton = VibeComposerGUI.makeButton("+", e -> {
			addChord("C");
		});
		chordletsPanel.add(addButton);

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
		chordletsPanel.setVisible(chordletsDisplayed);
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
		chordletsPanel.add(chordlet, chordlets.size() - 1);
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
