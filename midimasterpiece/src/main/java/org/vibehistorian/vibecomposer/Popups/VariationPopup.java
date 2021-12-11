package org.vibehistorian.vibecomposer.Popups;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.table.JTableHeader;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.OMNI;
import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;
import org.vibehistorian.vibecomposer.Helpers.VariationsBooleanTableModel;
import org.vibehistorian.vibecomposer.Panels.DetachedKnobPanel;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;
import org.vibehistorian.vibecomposer.Panels.TransparentablePanel;

public class VariationPopup {

	public static Map<Integer, Set<Integer>> bannedInstVariations = new HashMap<>();
	static {
		for (int i = 0; i < 5; i++) {
			bannedInstVariations.put(i, new HashSet<>());
		}
	}

	final JFrame frame = new JFrame();
	JPanel tablesPanel = new JPanel();
	JTable[] tables = new JTable[5];

	JTextField userChords;
	JTextField userChordsDurations;

	int sectionOrder = 0;
	Section sectionObject = null;
	JScrollPane scroll;
	List<KnobPanel> knobs = new ArrayList<>();
	KnobPanel keyChangeKnob = new DetachedKnobPanel("Key Change", 0, -12, 12);
	ScrollComboBox<String> scaleMode = new ScrollComboBox<>(false);

	public VariationPopup(int section, Section sec, Point parentLoc, Dimension parentDim) {
		addFrameWindowOperation();
		sectionOrder = section;
		sectionObject = sec;
		tablesPanel.setLayout(new BoxLayout(tablesPanel, BoxLayout.Y_AXIS));

		tablesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		PopupUtils.addEmptySpaceCloser(tablesPanel, frame, new Runnable() {

			@Override
			public void run() {
				VibeComposerGUI.manualArrangement.setSelected(true);
				VibeComposerGUI.manualArrangement.repaint();
			}
		});

		addTypesMeasures(sec);
		addCustomChordsDurations(sec);
		addRiskyVariations(sec);
		addVariationSettings(sec);
		addInstVolumeKnobs(sec);
		for (int i = 0; i < 5; i++) {
			int fI = i;

			JTable table = new JTable();
			table.setAlignmentX(Component.LEFT_ALIGNMENT);
			if (sec.getPartPresenceVariationMap().get(i) == null) {
				sec.initPartMap();
			}

			List<String> partNames = VibeComposerGUI.getInstList(i).stream()
					.map(e -> (e.getInstrumentBox().getVal()).split(": ")[1])
					.collect(Collectors.toList());

			table.setModel(new VariationsBooleanTableModel(fI, sectionOrder - 1,
					sec.getPartPresenceVariationMap().get(i), Section.variationDescriptions[i],
					partNames));
			table.setRowSelectionAllowed(false);
			table.setColumnSelectionAllowed(false);
			table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

			table.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mousePressed(java.awt.event.MouseEvent evt) {
					int row = table.rowAtPoint(evt.getPoint());
					int col = table.columnAtPoint(evt.getPoint());

					System.out.println("Clicked VariationPopup table cell! " + row + ", " + col);
					if (col >= 1) {
						if (SwingUtilities.isMiddleMouseButton(evt)) {
							Boolean val = (Boolean) tables[fI].getModel().getValueAt(row, col);
							if (val) {
								for (Section sec : VibeComposerGUI.actualArrangement
										.getSections()) {
									sec.removeVariationForPart(fI, row, col);
								}
								table.repaint();
							}
						}

					}
				}
			});

			if (i < 4) {
				//table.getColumnModel().getColumn(0).setMaxWidth(27);
			}
			//table.setDefaultRenderer(Boolean.class, new BooleanRenderer());

			/*JList<String> list = new JList<>();
			String[] listData = new String[rowCount];
			for (int j = 0; j < rowCount; j++) {
				listData[j] = String.valueOf(j);
			}
			list.setListData(listData);
			list.setFixedCellHeight(table.getRowHeight() + table.getRowMargin());*/

			tables[i] = table;
			TransparentablePanel categoryPanel = new TransparentablePanel();
			categoryPanel.setLayout(new BoxLayout(categoryPanel, BoxLayout.X_AXIS));
			categoryPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			categoryPanel.setMaximumSize(new Dimension(2000, 40));
			categoryPanel.setBorder(new BevelBorder(BevelBorder.RAISED));
			boolean commited = sec.getInstPartList(fI) != null;
			JPanel categoryButtons = new JPanel();
			JLabel categoryName = commited
					? new JLabel("*" + VibeComposerGUI.instNames[i] + " - Committed")
					: new JLabel(VibeComposerGUI.instNames[i].toUpperCase());
			categoryName.setAlignmentX(Component.LEFT_ALIGNMENT);
			categoryName.setFont(new Font("Arial", Font.BOLD, 13));
			categoryName.setBorder(new BevelBorder(BevelBorder.RAISED));
			categoryPanel.add(categoryName);
			categoryPanel.addBackground(OMNI.alphen(VibeComposerGUI.instColors[i], 50));

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
						for (int k = 0; k < VibeComposerGUI.getInstList(fI).size(); k++) {
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
						for (int k = 0; k < VibeComposerGUI.getInstList(fI).size(); k++) {
							table.getModel().setValueAt(Boolean.FALSE, k, col);
							//sec.resetPresence(fI, j);
						}
					} else if (SwingUtilities.isMiddleMouseButton(e) && col > 1) {
						if (bannedInstVariations.get(fI).contains(col)) {
							bannedInstVariations.get(fI).remove(col);
						} else {
							bannedInstVariations.get(fI).add(col);
							for (Section sec : VibeComposerGUI.actualArrangement.getSections()) {
								sec.removeVariationForAllParts(fI, col);
							}
						}
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
		frame.setPreferredSize(new Dimension(650, Math.min(parentDim.height, heightLimit)));
		int newLocX = parentLoc.x - 190;
		frame.setLocation((newLocX < 0) ? 0 : newLocX, parentLoc.y);
		frame.add(scroll);
		frame.setTitle("Variations - Section " + section);
		frame.pack();
		frame.setVisible(true);

		System.out.println("Opened arrangement variation page!");
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
					sec.setMeasures(Integer.valueOf(item));

				}
			}
		});

		typeAndMeasuresPanel.add(measureCombo);
		typeAndMeasuresPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		tablesPanel.add(typeAndMeasuresPanel);
	}

	private void addCustomChordsDurations(Section sec) {
		JPanel customChordsDurationsPanel = new JPanel();
		JCheckBox userChordsEnabled = new JCheckBox("Custom Chords",
				sec.isCustomChordsDurationsEnabled());
		customChordsDurationsPanel.add(userChordsEnabled);
		userChordsEnabled.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				sec.setCustomChordsDurationsEnabled(userChordsEnabled.isSelected());
				VibeComposerGUI.recolorVariationPopupButton(sectionOrder);
			}

		});

		String tooltip = "Allowed chords: C/D/E/F/G/A/B + "
				+ StringUtils.join(MidiUtils.SPICE_NAMES_LIST, " / ");

		String guiUserChords = (VibeComposerGUI.userChordsEnabled.isSelected()
				? VibeComposerGUI.userChords.getText()
				: StringUtils.join(MidiGenerator.chordInts, ","));
		userChords = new JTextField(
				(sec.isCustomChordsDurationsEnabled() || sec.isDisplayAlternateChords())
						? sec.getCustomChords()
						: guiUserChords,
				23);
		userChords.setToolTipText(tooltip);
		customChordsDurationsPanel.add(userChords);
		userChordsDurations = new JTextField(
				sec.isCustomChordsDurationsEnabled() ? sec.getCustomDurations()
						: VibeComposerGUI.userChordsDurations.getText(),
				9);
		customChordsDurationsPanel.add(new JLabel("Chord durations:"));
		customChordsDurationsPanel.add(userChordsDurations);
		customChordsDurationsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);


		tablesPanel.add(customChordsDurationsPanel);
	}

	private void addInstVolumeKnobs(Section sec) {
		JPanel instVolumesPanel = new JPanel();
		instVolumesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (int i = 0; i < 5; i++) {
			int val = sec.getVol(i);
			KnobPanel panel = new DetachedKnobPanel(VibeComposerGUI.instNames[i], val, 20, 150);
			panel.setShowTextInKnob(VibeComposerGUI.isShowingTextInKnobs);
			panel.addBackgroundWithBorder(OMNI.alphen(VibeComposerGUI.instColors[i], 50));
			knobs.add(panel);
			instVolumesPanel.add(panel);
		}
		tablesPanel.add(instVolumesPanel);
	}

	private void addVariationSettings(Section sec) {
		JPanel variationSettingsPanel = new JPanel();
		variationSettingsPanel.setLayout(new GridLayout(0, 2, 0, 0));
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
				VibeComposerGUI.recolorVariationPopupButton(sectionOrder);
			}
		});
		transitionPanel.add(new JLabel("Transition Type"));
		transitionPanel.add(transitionBox);

		JPanel keyChangePanel = new JPanel();
		keyChangePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		keyChangePanel.setLayout(new BoxLayout(keyChangePanel, BoxLayout.X_AXIS));
		keyChangeKnob.setInt(sec.getCustomKeyChange() == null ? 0 : sec.getCustomKeyChange());
		keyChangePanel.add(keyChangeKnob);

		String[] scaleModes = new String[MidiUtils.ScaleMode.values().length];
		for (int i = 0; i < MidiUtils.ScaleMode.values().length; i++) {
			scaleModes[i] = MidiUtils.ScaleMode.values()[i].toString();
		}
		scaleMode.addItem(OMNI.EMPTYCOMBO);
		ScrollComboBox.addAll(scaleModes, scaleMode);
		if (sec.getCustomScale() != null) {
			scaleMode.setSelectedItem(sec.getCustomScale().toString());
		}

		keyChangePanel.add(new JLabel("Scale"));
		keyChangePanel.add(scaleMode);


		variationSettingsPanel.add(transitionPanel);
		variationSettingsPanel.add(keyChangePanel);

		tablesPanel.add(variationSettingsPanel);
	}

	private void addRiskyVariations(Section sec) {
		JPanel riskyVarPanel = new JPanel();
		riskyVarPanel.setLayout(new GridLayout(0, 2, 0, 0));
		riskyVarPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (int i = 0; i < Section.riskyVariationNames.length; i++) {
			JCheckBox riskyVar = new JCheckBox(Section.riskyVariationNames[i], false);
			if (sec.getRiskyVariations() != null) {
				riskyVar.setSelected(sec.isRiskyVar(i));
			}
			final int index = i;
			riskyVar.addItemListener(new ItemListener() {

				@Override
				public void itemStateChanged(ItemEvent e) {
					sec.setRiskyVariation(index, riskyVar.isSelected() ? 1 : 0);
					VibeComposerGUI.recolorVariationPopupButton(sectionOrder);
				}

			});
			riskyVarPanel.add(riskyVar);
		}


		tablesPanel.add(riskyVarPanel);

	}

	private void addFrameWindowOperation() {
		frame.addWindowListener(new WindowListener() {

			@Override
			public void windowOpened(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowClosing(WindowEvent e) {
				VibeComposerGUI.varPopup = null;
				sectionObject.setCustomChords(userChords.getText());
				sectionObject.setCustomDurations(userChordsDurations.getText());
				List<Integer> instVolumes = new ArrayList<>();
				for (KnobPanel kp : knobs) {
					instVolumes.add(kp.getInt());
				}
				sectionObject.setInstVelocityMultiplier(instVolumes);
				sectionObject.setCustomKeyChange(keyChangeKnob.getInt());
				sectionObject.setCustomScale(
						scaleMode.getSelectedIndex() > 0 ? ScaleMode.valueOf(scaleMode.getVal())
								: null);
				VibeComposerGUI.setActualModel(
						VibeComposerGUI.actualArrangement.convertToActualTableModel(), false);
				VibeComposerGUI.recolorVariationPopupButton(sectionOrder);
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

	public JFrame getFrame() {
		return frame;
	}
}
