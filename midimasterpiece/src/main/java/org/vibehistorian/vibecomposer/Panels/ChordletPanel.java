package org.vibehistorian.vibecomposer.Panels;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JPanel;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.Components.Chordlet;

public class ChordletPanel extends JPanel {

	private static final long serialVersionUID = -4372897856098663297L;

	private List<Chordlet> chordlets = new ArrayList<>();

	private void setupSize() {
		setPreferredSize(new Dimension(200, 40));
	}

	public ChordletPanel(String... chords) {
		setupChords(Arrays.asList(chords));
		setupSize();
	}

	public ChordletPanel(List<String> chords) {
		setupChords(chords);
	}

	public ChordletPanel(String chords) {
		setupChords(chords);
	}

	public void setupChords(List<String> chords) {
		chordlets.clear();
		setOpaque(false);
		chords.forEach(e -> addChord(e));
		LG.i("Set up chords: " + chordlets.size());
	}

	public void setupChords(String chords) {
		String[] split = chords.split(",");
		List<String> chords2 = new ArrayList<>();
		for (String s : split) {
			chords2.add(s.trim());
		}
		setupChords(chords2);
	}

	public void addChord(String chord) {
		Chordlet chordlet = new Chordlet(chord);
		chordlets.add(chordlet);
		add(chordlet);
		repaint();
	}

	public List<String> getChordList() {
		return chordlets.stream().map(e -> e.getChordText()).collect(Collectors.toList());
	}

	public String getChordListString() {
		return StringUtils.join(getChordList(), ",");
	}
}
