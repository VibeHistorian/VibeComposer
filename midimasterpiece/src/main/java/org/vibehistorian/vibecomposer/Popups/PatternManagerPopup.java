package org.vibehistorian.vibecomposer.Popups;

import java.awt.GridLayout;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JPanel;

import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Helpers.UsedPattern;
import org.vibehistorian.vibecomposer.Popups.MidiEditPopup.PatternNameMarker;

public class PatternManagerPopup extends CloseablePopup {

	public ScrollComboBox<String> patternPartBox = new ScrollComboBox<>(false);
	public ScrollComboBox<Integer> patternPartOrderBox = new ScrollComboBox<>(false);
	public ScrollComboBox<PatternNameMarker> patternNameBox = new ScrollComboBox<>(false);

	CustomCheckBox removeCB = new CustomCheckBox("Remove", false);

	public PatternManagerPopup() {
		super("Pattern Manager", 16);

		JPanel patternManagerPanel = new JPanel();
		patternManagerPanel.setLayout(new GridLayout(0, 3, 0, 0));
		//patternManagerPanel.setPreferredSize(new Dimension(size));

		patternPartBox.setFunc(e -> loadPartOrders());
		patternPartOrderBox.setFunc(e -> loadNames());
		//patternNameBox.setFunc(e -> loadNotes());

		loadParts();
		loadPartOrders();
		loadNames();

		patternManagerPanel.add(patternPartBox);
		patternManagerPanel.add(patternPartOrderBox);
		patternManagerPanel.add(patternNameBox);

		patternManagerPanel.add(VibeComposerGUI.makeButton("Unapply Selected", e -> {
			unapply(0);
		}));
		patternManagerPanel.add(VibeComposerGUI.makeButton("Unapply Generated (Multi)", e -> {
			unapply(1);
		}));
		patternManagerPanel.add(VibeComposerGUI.makeButton("Unapply Custom (Multi)", e -> {
			unapply(2);
		}));

		patternManagerPanel.add(removeCB);

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
		patternPartBox.removeAllItems();
		ScrollComboBox.addAll(VibeComposerGUI.instNames, patternPartBox);
		patternPartBox.addItem("");
	}

	private void loadPartOrders() {
		MidiEditPopup.loadPartOrders(patternPartBox, patternPartOrderBox, patternNameBox);
		patternPartOrderBox.addItem(null);
	}

	private void loadNames() {
		MidiEditPopup.loadNames(patternPartBox, patternPartOrderBox, patternNameBox);
		patternNameBox.addItem(new PatternNameMarker("", true));
	}

	private void unapply(int mode) {
		// 0 exact match -> empty means match on super category
		// 1 starts with **GEN**
		// 2 doesn't match starting with **GEN** or BASE_PATTERNS

		int part = patternPartBox.getSelectedIndex();
		Integer partOrder = patternPartOrderBox.getSelectedItem();
		String name = (patternNameBox.getSelectedItem() != null)
				? patternNameBox.getSelectedItem().name
				: "";

		int depth = 3;
		if ("".equals(name)) {
			depth = 2;
			if (null == partOrder) {
				depth = 1;
				if (part >= 5) {
					depth = 0;
				}
			}
		}
		if (depth == 3 && mode >= 1) {
			new TemporaryInfoPopup(
					"Multi - Only works when at least one field has an empty value selected", 2500);
			return;
		}

		boolean remove = removeCB.isSelected();

		switch (depth) {
		case 0:
			for (int i = 0; i < 5; i++) {
				List<Integer> partOrders = VibeComposerGUI.guiConfig.getPatternMaps().get(i)
						.getKeys();
				for (Integer pO : partOrders) {
					unapply2(mode, i, pO, remove);
				}
			}
			break;
		case 1:
			List<Integer> partOrders = VibeComposerGUI.guiConfig.getPatternMaps().get(part)
					.getKeys();
			for (Integer pO : partOrders) {
				unapply2(mode, part, pO, remove);
			}
			break;
		case 2:
			unapply2(mode, part, partOrder, remove);
			break;
		case 3:
			unapply3(part, partOrder, name, remove);
			break;
		default:
			throw new UnsupportedOperationException("Mode too big.");
		}

		if (remove) {
			loadNames();
		}

	}

	private void unapply2(int mode, int part, Integer partOrder, boolean remove) {
		Set<String> patNames = VibeComposerGUI.guiConfig.getPatternMaps().get(part)
				.getPatternNames(partOrder);
		patNames = patNames.stream().filter(e -> filter(mode, e)).collect(Collectors.toSet());
		for (String pat : patNames) {
			unapply3(part, partOrder, pat, remove);
		}
	}

	private void unapply3(int part, Integer partOrder, String name, boolean remove) {
		if (remove) {
			VibeComposerGUI.guiConfig.getPatternMaps().get(part).removePattern(partOrder, name);
		} else {
			unapply(part, partOrder, name);
		}
	}

	private void unapply(int part, Integer partOrder, String pat) {
		PhraseNotes pn = VibeComposerGUI.guiConfig.getPatternRaw(part, partOrder, pat);
		if (pn != null) {
			pn.setApplied(false);
		}
	}

	public boolean filter(int mode, String patternName) {
		switch (mode) {
		case 0:
			return true;
		case 1:
			return genMatch(patternName);
		case 2:
			return customMatch(patternName);
		default:
			return true;
		}

	}

	public boolean genMatch(String patternName) {
		return (patternName != null) && patternName.startsWith(UsedPattern.GENERATED);
	}

	public boolean customMatch(String patternName) {
		for (String base : UsedPattern.BASE_PATTERNS) {
			if (base.equals(patternName)) {
				return false;
			}
		}
		if (genMatch(patternName)) {
			return false;
		}
		return true;
	}
}
