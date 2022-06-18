package org.vibehistorian.vibecomposer.Popups;

import java.awt.GridLayout;

import javax.swing.JPanel;

import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Popups.MidiEditPopup.PatternNameMarker;

public class PatternManagerPopup extends CloseablePopup {

	public ScrollComboBox<String> patternPartBox = new ScrollComboBox<>(false);
	public ScrollComboBox<Integer> patternPartOrderBox = new ScrollComboBox<>(false);
	public ScrollComboBox<PatternNameMarker> patternNameBox = new ScrollComboBox<>(false);

	public PatternManagerPopup() {
		super("Pattern Manager", 16);

		JPanel patternManagerPanel = new JPanel();
		patternManagerPanel.setLayout(new GridLayout(0, 5, 0, 0));
		//patternManagerPanel.setPreferredSize(new Dimension(size));

		ScrollComboBox.addAll(VibeComposerGUI.instNames, patternPartBox);
		patternPartBox.setFunc(e -> loadParts());
		patternPartOrderBox.setFunc(e -> loadNames());
		//patternNameBox.setFunc(e -> loadNotes());

		patternManagerPanel.add(patternPartBox);
		patternManagerPanel.add(patternPartOrderBox);
		patternManagerPanel.add(patternNameBox);

		loadParts();
		loadNames();

		patternManagerPanel.add(VibeComposerGUI.makeButton("Unapply", e -> {
			unapply();
		}));
		patternManagerPanel.add(VibeComposerGUI.makeButton("Remove", e -> {
			remove();
		}));

		frame.add(patternManagerPanel);

		frame.pack();
		frame.setVisible(true);
		LG.d("Opened Pattern Manager popup!");
	}

	@Override
	protected void addFrameWindowOperation() {
		frame.addWindowListener(EMPTY_WINDOW_LISTENER);
	}

	private void loadParts() {
		MidiEditPopup.loadParts(patternPartBox, patternPartOrderBox, patternNameBox);
	}

	private void loadNames() {
		MidiEditPopup.loadNames(patternPartBox, patternPartOrderBox, patternNameBox);
	}

	private void unapply() {

	}

	private void remove() {

	}
}
