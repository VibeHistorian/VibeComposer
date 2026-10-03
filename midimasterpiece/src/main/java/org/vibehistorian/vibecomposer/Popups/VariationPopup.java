package org.vibehistorian.vibecomposer.Popups;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Constants;
import org.vibehistorian.vibecomposer.Helpers.VariationsBooleanTableModel;
import org.vibehistorian.vibecomposer.INST;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Panels.ChordletPanel;
import org.vibehistorian.vibecomposer.Panels.DetachedKnobPanel;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;
import org.vibehistorian.vibecomposer.Panels.TransparentablePanel;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.SectionConfig;
import org.vibehistorian.vibecomposer.SwingUtils;
import org.vibehistorian.vibecomposer.generators.MidiGenerator;
import org.vibehistorian.vibecomposer.gui.ChordGUI;
import org.vibehistorian.vibecomposer.gui.ExtraSettingsGUI;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

public class VariationPopup {

	final JDialog frame = new JDialog();
	JPanel tablesPanel = new JPanel();
	JTable[] tables = new JTable[5];

	ChordletPanel userChords;
	JTextField userChordsDurations;

	int sectionOrder = 0;
	Section sectionObject = null;
	JScrollPane scroll;
	List<KnobPanel> knobs = new ArrayList<>();
	KnobPanel keyChangeKnob = new DetachedKnobPanel("Key Change", 0, -12, 12);
	ScrollComboBox<String> scaleMode = new ScrollComboBox<>(false);

	private final ArrangementActions arrangementActions;
	private final ChordGUI chordGUI;

	public interface ArrangementActions {
		void recolorVariationPopupButton(int sectionOrder);
		void refreshActualModel(boolean reset);
		void clearVariationPopup();
		void markManualArrangement();
		void removeVariationForAllSections(INST instrument, int row, int column);
		void toggleGlobalVariation(INST instrument, int column);
		Boolean[] getGlobalVariationMap(INST instrument);
	}

	public VariationPopup(int section, Section sec, Point parentLoc, Dimension parentDim,
			IntFunction<List<? extends InstPanel>> getInstList,
			IntFunction<List<? extends InstPart>> partsForInstrument,
			ChordGUI chordGUI, JTable actualArrangementTable,
			ArrangementActions arrangementActions) {
		this.arrangementActions = arrangementActions;
		this.chordGUI = chordGUI;
		addFrameWindowOperation();
		sectionOrder = section;
		sectionObject = sec;
		tablesPanel.setLayout(new BoxLayout(tablesPanel, BoxLayout.Y_AXIS));

		tablesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		PopupUtils.addEmptySpaceCloser(tablesPanel, frame, arrangementActions::markManualArrangement);

		addTypesMeasures(sec);
		addCustomChordsDurations(sec);
		addSectionVariations(sec);
		addVariationSettings(sec);
		//addSectionConfigSettings(sec);
		addInstVolumeKnobs(sec);
		for (INST instrument : INST.values()) {
			int i = instrument.getIndex();

            JTable table = new JTable();
			table.setAlignmentX(Component.LEFT_ALIGNMENT);
			if (sec.getPartMap().get(i) == null) {
				sec.initPartMap(partsForInstrument);
			}

			List<String> partNames = getInstList.apply(i).stream()
					.map(e -> (e.getInstrumentBox().getVal()).split(": ")[1])
					.collect(Collectors.toList());

			table.setModel(new VariationsBooleanTableModel(i, sectionOrder - 1,
					sec.getPartMap().get(i), Section.variationDescriptions[i], partNames,
					() -> arrangementActions.getGlobalVariationMap(instrument), actualArrangementTable));
			table.setRowSelectionAllowed(false);
			table.setColumnSelectionAllowed(false);
			table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

			table.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mousePressed(java.awt.event.MouseEvent evt) {
					int row = table.rowAtPoint(evt.getPoint());
					int col = table.columnAtPoint(evt.getPoint());

					LG.d("Clicked VariationPopup table cell! " + row + ", " + col);
					if (col >= 1) {
						if (SwingUtilities.isMiddleMouseButton(evt)) {
							arrangementActions.removeVariationForAllSections(instrument, row, col);
							table.repaint();

							tables[i].getModel().setValueAt(Boolean.FALSE, row, col);
						}

					}
				}
			});

			tables[i] = table;
			TransparentablePanel categoryPanel = new TransparentablePanel();
			categoryPanel.setLayout(new BoxLayout(categoryPanel, BoxLayout.X_AXIS));
			categoryPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			categoryPanel.setMaximumSize(new Dimension(2000, 40));
			categoryPanel.setBorder(new BevelBorder(BevelBorder.RAISED));
			boolean commited = sec.getInstPartList(i) != null;
			JPanel categoryButtons = new JPanel();
			JLabel categoryName = commited
					? new JLabel("*" + Constants.instNames[i] + " - Committed")
					: new JLabel(Constants.instNames[i].toUpperCase());
			categoryName.setAlignmentX(Component.LEFT_ALIGNMENT);
			categoryName.setFont(new Font("Arial", Font.BOLD, 13));
			categoryName.setBorder(new BevelBorder(BevelBorder.RAISED));
			categoryPanel.add(categoryName);
			categoryPanel.addBackground(OMNI.alphen(Constants.instColors[i], 50));

			categoryButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
			categoryPanel.add(categoryButtons);
			tablesPanel.add(categoryPanel);
			JTableHeader header = tables[i].getTableHeader();
			header.setReorderingAllowed(false);
			header.setResizingAllowed(false);
			header.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					int col = table.columnAtPoint(e.getPoint());
					if (col == 0)
						return;

					if (SwingUtilities.isLeftMouseButton(e)) {
						for (int k = 0; k < getInstList.apply(i).size(); k++) {
							if (col > 1) {
								if (table.getModel().getValueAt(k, 1) == Boolean.TRUE) {
									table.getModel().setValueAt(Boolean.TRUE, k, col);
								}
							} else {
								table.getModel().setValueAt(Boolean.TRUE, k, col);
							}

							//sec.resetPresence(fI, j);
						}
					} else if (SwingUtilities.isRightMouseButton(e)) {
						for (int k = 0; k < getInstList.apply(i).size(); k++) {
							table.getModel().setValueAt(Boolean.FALSE, k, col);
							//sec.resetPresence(fI, j);
						}
					} else if (SwingUtilities.isMiddleMouseButton(e) && col >= 2) {
						arrangementActions.toggleGlobalVariation(instrument, col);
					}
					table.repaint();
				}
			});
			header.setAlignmentX(Component.LEFT_ALIGNMENT);
			tablesPanel.add(header);
			tablesPanel.add(tables[i]);
		}

		scroll = new JScrollPane(tablesPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		int heightLimit = 850;
		frame.setPreferredSize(new Dimension(680, Math.min(parentDim.height, heightLimit)));
		int newLocX = parentLoc.x - 190;
		SwingUtils.setFrameLocation(frame, new Point(newLocX, parentLoc.y));
		frame.add(scroll);
		frame.setTitle("Variations - Section " + section);
		frame.pack();
		frame.setVisible(true);

		LG.d("Opened arrangement variation page!");
	}

	private void addTypesMeasures(Section sec) {
		JPanel typeAndMeasuresPanel = new JPanel();
		typeAndMeasuresPanel.add(new JLabel("Section type"));

		ScrollComboBox<String> typeCombo = new ScrollComboBox<>(false);
		for (Section.SectionType type : Section.SectionType.values()) {
			typeCombo.addItem(type.toString());
		}

		typeCombo.addItem(OMNI.EMPTYCOMBO);
		typeCombo.setVal(OMNI.EMPTYCOMBO);
		typeCombo.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent event) {
				if (event.getStateChange() == ItemEvent.SELECTED) {
					String item = (String) event.getItem();
					if (OMNI.EMPTYCOMBO.equals(item)) {
						return;
					}
					sec.setType(String.valueOf(item));

				}
			}
		});
		typeAndMeasuresPanel.add(typeCombo);

		typeAndMeasuresPanel.add(new JLabel("Measures"));
		ScrollComboBox<String> measureCombo = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new String[] { "1", "2", "3", "4", OMNI.EMPTYCOMBO }, measureCombo);
		measureCombo.setVal(String.valueOf(sec.getMeasures()));
		measureCombo.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent event) {
				if (event.getStateChange() == ItemEvent.SELECTED) {
					String item = (String) event.getItem();
					if (OMNI.EMPTYCOMBO.equals(item)) {
						return;
					}
					sec.setMeasures(Integer.parseInt(item));

				}
			}
		});

		typeAndMeasuresPanel.add(measureCombo);
		typeAndMeasuresPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		tablesPanel.add(typeAndMeasuresPanel);
	}

	private void addCustomChordsDurations(Section sec) {
		JPanel customChordsDurationsPanel = new JPanel();
		JCheckBox userChordsEnabled = new CustomCheckBox("Custom Chords",
				sec.isCustomChordsEnabled());
		customChordsDurationsPanel.add(userChordsEnabled);
		userChordsEnabled.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				sec.setCustomChordsEnabled(userChordsEnabled.isSelected());
				arrangementActions.recolorVariationPopupButton(sectionOrder);
			}

		});

		String tooltip = "Allowed chords: C/D/E/F/G/A/B + "
				+ StringUtils.join(MidiUtils.SPICE_NAMES_LIST, " / ");

		String guiUserChords = (chordGUI.userChordsEnabled.isSelected()
				? chordGUI.userChords.getChordListString()
				: StringUtils.join(MidiGenerator.chordInts, ","));
		userChords = new ChordletPanel(300,
				(sec.isCustomChordsEnabled() || sec.isDisplayAlternateChords())
						? sec.getCustomChords()
						: guiUserChords);
		userChords.setToolTipText(tooltip);
		customChordsDurationsPanel.add(userChords);


		JCheckBox userDurationsEnabled = new CustomCheckBox("Custom Durations",
				sec.isCustomDurationsEnabled());
		customChordsDurationsPanel.add(userDurationsEnabled);
		userDurationsEnabled.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				sec.setCustomDurationsEnabled(userDurationsEnabled.isSelected());
				arrangementActions.recolorVariationPopupButton(sectionOrder);
			}

		});
		userChordsDurations = new JTextField(
				sec.isCustomDurationsEnabled() ? sec.getCustomDurations()
						: chordGUI.userChordsDurations.getText(),
				7);
		customChordsDurationsPanel.add(userChordsDurations);
		customChordsDurationsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);


		tablesPanel.add(customChordsDurationsPanel);
	}

	private void addInstVolumeKnobs(Section sec) {
		JPanel instVolumesPanel = new JPanel();
		instVolumesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (INST instrument : INST.values()) {
			int i = instrument.getIndex();
			int val = sec.getVol(i);
			KnobPanel panel = new DetachedKnobPanel(Constants.instNames[i], 100, 20, 150);
			panel.setInt(val);
			panel.setShowTextInKnob(ExtraSettingsGUI.isShowingTextInKnobs);
			panel.addBackgroundWithBorder(OMNI.alphen(Constants.instColors[i], 50));
			knobs.add(panel);
			instVolumesPanel.add(panel);
		}
		tablesPanel.add(instVolumesPanel);
	}

	private void addVariationSettings(Section sec) {
		JPanel variationSettingsPanel = new JPanel();
		variationSettingsPanel.setLayout(new BoxLayout(variationSettingsPanel, BoxLayout.X_AXIS));
		variationSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel transitionPanel = new JPanel();
		transitionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		transitionPanel.setLayout(new BoxLayout(transitionPanel, BoxLayout.X_AXIS));
		ScrollComboBox<String> transitionBox = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(Section.transitionNames, transitionBox);
		transitionBox.setSelectedIndex(sec.getTransitionType());
		transitionBox.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				sec.setTransitionType(transitionBox.getSelectedIndex());
				arrangementActions.recolorVariationPopupButton(sectionOrder);
			}
		});
		transitionPanel.add(new JLabel("Transition Type "));
		transitionPanel.add(transitionBox);


		SectionConfig secC = sectionObject.getSecConfig();


		JPanel keyChangePanel = new JPanel();
		keyChangePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		keyChangePanel.setLayout(new BoxLayout(keyChangePanel, BoxLayout.X_AXIS));
		keyChangeKnob.setInt(secC.getCustomKeyChange() == null ? 0 : secC.getCustomKeyChange());
		keyChangeKnob.getKnob().setFunc(e -> {
			secC.setCustomKeyChange(keyChangeKnob.getValueRaw());
		});
		keyChangePanel.add(keyChangeKnob);

		String[] scaleModes = new String[MidiUtils.ScaleMode.values().length];
		for (int i = 0; i < MidiUtils.ScaleMode.values().length; i++) {
			scaleModes[i] = MidiUtils.ScaleMode.values()[i].toString();
		}
		scaleMode.addItem(OMNI.EMPTYCOMBO);
		ScrollComboBox.addAll(scaleModes, scaleMode);
		if (secC.getCustomScale() != null) {
			scaleMode.setValRaw(secC.getCustomScale().toString());
		}
		scaleMode.setFunc(e -> {
			secC.setCustomScale(
					scaleMode.getSelectedIndex() > 0 ? ScaleMode.valueOf(scaleMode.getVal())
							: null);
		});

		keyChangePanel.add(new JLabel("Scale "));
		keyChangePanel.add(scaleMode);

		ScrollComboBox<String> customKeyChangeType = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new String[] { OMNI.EMPTYCOMBO, "2-5-1", "Direct" },
				customKeyChangeType);
		customKeyChangeType.setFunc(e -> {
			secC.setCustomKeyChangeType(customKeyChangeType.getSelectedIndex());
		});
		if (secC.getCustomKeyChangeType() > 0) {
			customKeyChangeType.setSelectedIndex(secC.getCustomKeyChangeType());
		}
		keyChangePanel.add(new JLabel("Type "));
		keyChangePanel.add(customKeyChangeType);

		variationSettingsPanel.add(transitionPanel);
		variationSettingsPanel.add(keyChangePanel);

		tablesPanel.add(variationSettingsPanel);
	}

	private void addSectionConfigSettings(Section sec) {
		SectionConfig secConfig = sec.getSecConfig();

		JPanel sectionConfigSettingsPanel = new JPanel();
		sectionConfigSettingsPanel.setLayout(new GridLayout(0, 4, 0, 0));
		sectionConfigSettingsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		ScrollComboBox<String> beatDurMultiBox = new ScrollComboBox<>(false);
		ScrollComboBox.addAll(new String[] { OMNI.EMPTYCOMBO, "0.5", "1.0", "2.0" },
				beatDurMultiBox);
		beatDurMultiBox
				.setSelectedIndex(OMNI.d(secConfig.getBeatDurationMultiplierIndex(), -1) + 1);
		beatDurMultiBox.setFunc(e -> {
			secConfig.setBeatDurationMultiplierIndex(
					beatDurMultiBox.getSelectedIndex() > 0 ? beatDurMultiBox.getSelectedIndex() - 1
							: null);
		});
		KnobPanel swing = new DetachedKnobPanel("Swing Override",
				OMNI.d(secConfig.getSectionSwingOverride(), 0));
		swing.getKnob().setFunc(e -> {
			secConfig.setSectionSwingOverride(swing.getInt() > 0 ? swing.getInt() : null);
		});

		KnobPanel bpm = new DetachedKnobPanel("BPM Override", OMNI.d(secConfig.getSectionBpm(), 0),
				0, 200);
		bpm.getKnob().setFunc(e -> {
			secConfig.setSectionBpm(bpm.getInt() > 0 ? bpm.getInt() : null);
		});

		sectionConfigSettingsPanel.add(new JLabel("Beat Dur. Multi.:"));
		sectionConfigSettingsPanel.add(beatDurMultiBox);
		sectionConfigSettingsPanel.add(swing);
		sectionConfigSettingsPanel.add(bpm);

		tablesPanel.add(sectionConfigSettingsPanel);
	}

	private void addSectionVariations(Section sec) {
		JPanel sectionVarPanel = new JPanel();
		sectionVarPanel.setLayout(new GridLayout(0, 2, 0, 0));
		sectionVarPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (int i = 0; i < Section.sectionVariationNames.length; i++) {
			JCheckBox sectionVar = new CustomCheckBox(Section.sectionVariationNames[i], false);
			if (sec.getSectionVariations() != null) {
				sectionVar.setSelected(sec.isSectionVar(i));
			}
			final int index = i;
			sectionVar.addItemListener(new ItemListener() {

				@Override
				public void itemStateChanged(ItemEvent e) {
					sec.setSectionVariation(index, sectionVar.isSelected() ? 1 : 0);
					if (index == 1) {
						sec.setDisplayAlternateChords(sectionVar.isSelected());
					}
					arrangementActions.recolorVariationPopupButton(sectionOrder);
				}

			});
			sectionVarPanel.add(sectionVar);
		}


		tablesPanel.add(sectionVarPanel);

	}

	private void addFrameWindowOperation() {
		frame.addWindowListener(new WindowListener() {

			@Override
			public void windowOpened(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowClosing(WindowEvent e) {
				arrangementActions.clearVariationPopup();
				sectionObject.setCustomChords(userChords.getChordListString());
				sectionObject.setCustomDurations(userChordsDurations.getText());
				List<Integer> instVolumes = new ArrayList<>();
				for (KnobPanel kp : knobs) {
					instVolumes.add(kp.getInt());
				}
				sectionObject.setInstVelocityMultiplier(instVolumes);
				arrangementActions.refreshActualModel(false);
				arrangementActions.recolorVariationPopupButton(sectionOrder);
			}

			@Override
			public void windowClosed(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowIconified(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowDeiconified(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowActivated(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowDeactivated(WindowEvent e) {
				// Auto-generated method stub

			}

		});

	}

	public JDialog getFrame() {
		return frame;
	}
}
