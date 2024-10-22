package org.vibehistorian.vibecomposer.Popups;

import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.MidiEditArea;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;
import org.vibehistorian.vibecomposer.Helpers.UsedPattern;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Popups.MidiEditPopup.PatternNameMarker;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

import javax.swing.*;
import java.awt.*;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PatternManagerPopup extends CloseablePopup {

	public ScrollComboBox<String> patternPartBox = new ScrollComboBox<>(false);
	public ScrollComboBox<Integer> patternPartOrderBox = new ScrollComboBox<>(false);
	public ScrollComboBox<PatternNameMarker> patternNameBox = new ScrollComboBox<>(false);

	MidiEditArea mvea = null;

	int panelWidth = 1000;

	CustomCheckBox removeCB = new CustomCheckBox("Remove", false);

	public PatternManagerPopup() {
		super("Pattern Manager", 14, new Point(-200, -200));

		JPanel allPanels = new JPanel();
		allPanels.setLayout(new BoxLayout(allPanels, BoxLayout.Y_AXIS));
		allPanels.setMaximumSize(new Dimension(panelWidth, 500));

		JPanel mveaPanel = new JPanel();
		mveaPanel.setPreferredSize(new Dimension(panelWidth, 350));
		mveaPanel.setMinimumSize(new Dimension(panelWidth, 350));
		mvea = new MidiEditArea(126, 1, new PhraseNotes());
		mvea.setPop(null);
		mvea.setPreferredSize(new Dimension(panelWidth, 350));
		mvea.setEnabled(false);
		mveaPanel.add(mvea);

		JPanel patternComboBoxPanel = new JPanel();
		patternComboBoxPanel.setLayout(new GridLayout(0, 3, 0, 0));
		patternComboBoxPanel.setPreferredSize(new Dimension(panelWidth, 50));
		patternComboBoxPanel.setMinimumSize(new Dimension(panelWidth, 50));

		JPanel patternButtonsPanel = new JPanel();
		patternButtonsPanel.setLayout(new GridLayout(0, 4, 0, 0));
		patternButtonsPanel.setPreferredSize(new Dimension(panelWidth, 50));
		patternButtonsPanel.setMinimumSize(new Dimension(panelWidth, 50));

		patternPartBox.setFunc(e -> loadPartOrders());
		patternPartOrderBox.setFunc(e -> loadNames());
		patternNameBox.setFunc(e -> {
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
			if (depth == 3) {
				PhraseNotes pn = VibeComposerGUI.guiConfig.getPattern(part, partOrder, name);
				if (pn != null) {
					setCustomValues(pn);
				}
			}

		});

		loadParts();
		loadPartOrders();
		loadNames();

		patternComboBoxPanel.add(patternPartBox);
		patternComboBoxPanel.add(patternPartOrderBox);
		patternComboBoxPanel.add(patternNameBox);

		patternButtonsPanel.add(VibeComposerGUI.makeButton("Unapply Selected", e -> {
			unapply(0);
		}));
		patternButtonsPanel.add(VibeComposerGUI.makeButton("Unapply Generated (Multi)", e -> {
			unapply(1);
		}));
		patternButtonsPanel.add(VibeComposerGUI.makeButton("Unapply Custom (Multi)", e -> {
			unapply(2);
		}));
		patternButtonsPanel.add(VibeComposerGUI.makeButton("Unapply M/V/I (Multi)", e -> {
			unapply(3);
		}));

		patternButtonsPanel.add(removeCB);

		allPanels.add(patternComboBoxPanel);
		allPanels.add(patternButtonsPanel);
		allPanels.add(mveaPanel);
		frame.add(allPanels);

		frame.pack();
		frame.setVisible(true);
		LG.d("Opened Pattern Manager popup!");
	}

	@Override
	protected void addFrameWindowOperation() {
		frame.addWindowListener(EMPTY_WINDOW_LISTENER);
	}

	public void setCustomValues(PhraseNotes values) {
		int vmin = -1 * MidiEditPopup.baseMargin * MidiEditPopup.trackScope;
		int vmax = MidiEditPopup.baseMargin * MidiEditPopup.trackScope;
		if (!values.isEmpty()) {
			IntSummaryStatistics notes = values.stream().map(e -> e.getPitch()).filter(e -> e >= 0)
					.mapToInt(e -> e).boxed().collect(Collectors.summarizingInt(Integer::intValue));
			if (notes.getCount() > 0) {
				vmin += notes.getMin();
				vmax += notes.getMax();
			}
		}
		mvea.setMin(Math.min(mvea.min, vmin));
		mvea.setMax(Math.max(mvea.max, vmax));


		mvea.part = OMNI.clamp(patternPartBox.getSelectedIndex(), 0, 4);
		mvea.marginX = (mvea.part == 4) ? 160 : 80;

		mvea.setValues(values);

		repaintMvea();
	}

	public void repaintMvea() {
		mvea.setAndRepaint();
		MidiEditArea.sectionLength = mvea.getValues().stream().map(e -> e.getRv())
				.mapToDouble(e -> e).sum();
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

	public static void unapply2(int mode, int part, Integer partOrder, boolean remove) {
		Set<String> patNames = VibeComposerGUI.guiConfig.getPatternMaps().get(part)
				.getPatternNames(partOrder);
		patNames = patNames.stream().filter(e -> filter(mode, e)).collect(Collectors.toSet());
		for (String pat : patNames) {
			unapply3(part, partOrder, pat, remove);
		}
	}

	public static void unapply3(int part, Integer partOrder, String name, boolean remove) {
		if (remove) {
			VibeComposerGUI.guiConfig.getPatternMaps().get(part).removePattern(partOrder, name);
		} else {
			unapply(part, partOrder, name);
		}
	}

	public static void unapply(int part, Integer partOrder, String pat) {
		toggle(part, partOrder, pat, false);
	}

	public static Boolean toggle(int part, Integer partOrder, String pat, Boolean forcedState) {
		PhraseNotes pn = VibeComposerGUI.guiConfig.getPatternRaw(part, partOrder, pat);
		if (pn == null) {
			return null;
		}
		pn.setApplied((forcedState != null) ? forcedState : !pn.isApplied());
		return pn.isApplied();
	}

	public static boolean filter(int mode, String patternName) {
		switch (mode) {
		case 0:
			return true;
		case 1:
			return genMatch(patternName);
		case 2:
			return !customMatch(patternName) && !genMatch(patternName);
		case 3:
			return customMatch(patternName);
		default:
			return true;
		}

	}

	public static boolean genMatch(String patternName) {
		return (patternName != null) && patternName.startsWith(UsedPattern.GENERATED);
	}

	public static boolean customMatch(String patternName) {
		return UsedPattern.BASE_PATTERNS_SET.contains(patternName);
	}
}
