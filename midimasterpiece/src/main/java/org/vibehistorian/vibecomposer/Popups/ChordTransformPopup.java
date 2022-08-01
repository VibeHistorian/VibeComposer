package org.vibehistorian.vibecomposer.Popups;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Components.Chordlet;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Panels.ChordletPanel;
import org.vibehistorian.vibecomposer.Panels.DetachedKnobPanel;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;

public class ChordTransformPopup extends CloseablePopup {
	private ScrollComboBox<String> scaleMode = new ScrollComboBox<>(false);
	private KnobPanel transpose = new DetachedKnobPanel("Transpose By", 0, -12, 12);
	private ChordletPanel userChords = null;

	public ChordTransformPopup(String chords) {
		super("Transform Chords", 0, new Point(-600, -200));

		JPanel allPanel = new JPanel();
		allPanel.setLayout(new GridLayout(0, 2, 0, 0));
		allPanel.setPreferredSize(new Dimension(700, 100));
		String[] scaleModes = new String[MidiUtils.ScaleMode.values().length];
		for (int i = 0; i < MidiUtils.ScaleMode.values().length; i++) {
			scaleModes[i] = MidiUtils.ScaleMode.values()[i].toString();
		}
		ScrollComboBox.addAll(scaleModes, scaleMode);

		userChords = new ChordletPanel(350, chords);
		allPanel.add(userChords);
		JPanel modePanel = new JPanel();
		modePanel.add(new JLabel("Mode"));
		modePanel.add(scaleMode);
		modePanel.add(transpose);
		modePanel.add(VibeComposerGUI.makeButton("R", e -> {
			transpose.setInt(-1 * transpose.getInt());
		}));
		allPanel.add(modePanel);

		allPanel.add(VibeComposerGUI.makeButton("From Ionian To Mode", e -> {
			convertFromIonian();
		}));
		allPanel.add(VibeComposerGUI.makeButton("From Mode To Ionian", e -> {
			convertToIonian();
		}));
		frame.add(allPanel);
		frame.pack();
		frame.setVisible(true);
	}

	private void convertFromIonian() {
		List<Chordlet> chordlets = userChords.getChordlets();
		List<String> newModeChords = new ArrayList<>();
		chordlets.forEach(ch -> {
			int[] pitches = MidiUtils.transposeChord(MidiUtils.mappedChord(ch.getChordText(), true),
					ScaleMode.IONIAN.noteAdjustScale,
					ScaleMode.valueOf(scaleMode.getVal()).noteAdjustScale, true);
			if (transpose.getInt() != 0) {
				pitches = MidiUtils.transposeChord(pitches, transpose.getInt());
			}
			newModeChords.add(MidiUtils.chordStringFromPitches(pitches) + ch.getInversionText());
		});
		userChords.setupChords(newModeChords);
	}

	private void convertToIonian() {
		List<Chordlet> chordlets = userChords.getChordlets();
		List<String> newModeChords = new ArrayList<>();
		chordlets.forEach(ch -> {
			int[] pitches = MidiUtils.mappedChord(ch.getChordText(), true);
			if (transpose.getInt() != 0) {
				pitches = MidiUtils.transposeChord(pitches, transpose.getInt());
			}
			pitches = MidiUtils.transposeChord(pitches,
					ScaleMode.valueOf(scaleMode.getVal()).noteAdjustScale,
					ScaleMode.IONIAN.noteAdjustScale, true);
			newModeChords.add(MidiUtils.chordStringFromPitches(pitches) + ch.getInversionText());
		});
		userChords.setupChords(newModeChords);
	}

	@Override
	protected void addFrameWindowOperation() {
		frame.addWindowListener(EMPTY_WINDOW_LISTENER);
	}

}
