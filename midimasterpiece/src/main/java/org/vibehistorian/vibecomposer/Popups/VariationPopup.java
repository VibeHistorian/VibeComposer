package org.vibehistorian.vibecomposer.Popups;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.table.JTableHeader;

import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.BooleanTableModel;
import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;

public class VariationPopup {

	public static final String[] tableNames = { "Melody", "Bass", "Chords", "Arps", "Drums" };


	final JFrame frame = new JFrame();
	JPanel tablesPanel = new JPanel();
	JTable[] tables = new JTable[5];

	JScrollPane scroll;

	public VariationPopup(int section, Section sec, Point parentLoc, Dimension parentDim) {
		addFrameWindowOperation();
		JPanel measuresPanel = new JPanel();
		measuresPanel.add(new JLabel("Measures "));
		ScrollComboBox<String> measureCombo = new ScrollComboBox<>();
		MidiUtils.addAllToJComboBox(new String[] { "1", "2", "3", "4", "---" }, measureCombo);
		measureCombo.setSelectedItem(String.valueOf(sec.getMeasures()));

		measureCombo.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent event) {
				if (event.getStateChange() == ItemEvent.SELECTED) {
					String item = (String) event.getItem();
					if ("---".equals(item)) {
						return;
					}
					sec.setMeasures(Integer.valueOf(item));

				}
			}
		});


		tablesPanel.setLayout(new BoxLayout(tablesPanel, BoxLayout.Y_AXIS));
		measuresPanel.add(measureCombo);
		measuresPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		tablesPanel.add(measuresPanel);
		tablesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (int i = 0; i < 5; i++) {

			JTable table = new JTable();
			table.setAlignmentX(Component.LEFT_ALIGNMENT);
			if (sec.getPartPresenceVariationMap().get(i) == null) {
				sec.initPartMap();
			}
			table.setModel(new BooleanTableModel(sec.getPartPresenceVariationMap().get(i),
					Section.variationDescriptions[i]));
			table.setRowSelectionAllowed(false);
			table.setColumnSelectionAllowed(false);
			table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
			table.getColumnModel().getColumn(0).setMaxWidth(27);
			//table.setDefaultRenderer(Boolean.class, new BooleanRenderer());

			/*JList<String> list = new JList<>();
			String[] listData = new String[rowCount];
			for (int j = 0; j < rowCount; j++) {
				listData[j] = String.valueOf(j);
			}
			list.setListData(listData);
			list.setFixedCellHeight(table.getRowHeight() + table.getRowMargin());*/
			int fI = i;
			tables[i] = table;
			JPanel categoryPanel = new JPanel();
			categoryPanel.setLayout(new BoxLayout(categoryPanel, BoxLayout.Y_AXIS));
			categoryPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

			JPanel categoryButtons = new JPanel();
			JLabel categoryName = new JLabel(tableNames[i].toUpperCase());
			categoryName.setAlignmentX(Component.LEFT_ALIGNMENT);
			categoryName.setFont(new Font("Arial", Font.BOLD, 13));
			categoryName.setBorder(new BevelBorder(BevelBorder.RAISED));
			categoryPanel.add(categoryName);

			for (int j = 1; j < Section.variationDescriptions[i].length; j++) {
				int fJ = j;
				JButton butt = new JButton(Section.variationDescriptions[i][j]);
				//butt.setPreferredSize(new Dimension(80, 25));
				butt.setAlignmentX(Component.LEFT_ALIGNMENT);
				butt.addMouseListener(new MouseAdapter() {

					@Override
					public void mouseClicked(MouseEvent e) {
						if (SwingUtilities.isLeftMouseButton(e)) {
							for (int k = 0; k < VibeComposerGUI.getInstList(fI).size(); k++) {
								if (fJ > 1) {
									table.getModel().setValueAt(Boolean.TRUE, k, 1);
								}
								table.getModel().setValueAt(Boolean.TRUE, k, fJ);
								//sec.resetPresence(fI, j);
								table.repaint();
							}
						} else {
							for (int k = 0; k < VibeComposerGUI.getInstList(fI).size(); k++) {
								table.getModel().setValueAt(Boolean.FALSE, k, fJ);
								//sec.resetPresence(fI, j);
								table.repaint();
							}
						}


					}

				});
				categoryButtons.add(butt);
			}

			categoryButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
			categoryPanel.add(categoryButtons);
			tablesPanel.add(categoryPanel);
			JTableHeader header = tables[i].getTableHeader();
			header.setAlignmentX(Component.LEFT_ALIGNMENT);
			tablesPanel.add(header);
			tablesPanel.add(tables[i]);
		}

		addRiskyVariations(sec);

		scroll = new JScrollPane(tablesPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		int heightLimit = 950;
		frame.setPreferredSize(new Dimension(650,
				(parentDim.height < heightLimit) ? parentDim.height : heightLimit));
		int newLocX = parentLoc.x - 190;
		frame.setLocation((newLocX < 0) ? 0 : newLocX, parentLoc.y);
		frame.add(scroll);
		frame.setTitle("Variations - Section " + section);
		frame.pack();
		frame.setVisible(true);

		System.out.println("Opened arrangement variation page!");
	}

	private void addRiskyVariations(Section sec) {
		for (int i = 0; i < Section.riskyVariationNames.length; i++) {
			JCheckBox riskyVar = new JCheckBox(Section.riskyVariationNames[i], false);
			if (sec.getRiskyVariations() != null) {
				riskyVar.setSelected(sec.getRiskyVariations().get(i));
			}
			final int index = i;
			riskyVar.addItemListener(new ItemListener() {

				@Override
				public void itemStateChanged(ItemEvent e) {
					sec.setRiskyVariation(index, riskyVar.isSelected());
				}

			});
			tablesPanel.add(riskyVar);
		}
	}

	private void addFrameWindowOperation() {
		frame.addWindowListener(new WindowListener() {

			@Override
			public void windowOpened(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowClosing(WindowEvent e) {
				VibeComposerGUI.varPopup = null;
				VibeComposerGUI.setActualModel(
						VibeComposerGUI.actualArrangement.convertToActualTableModel());

			}

			@Override
			public void windowClosed(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowIconified(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowDeiconified(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowActivated(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowDeactivated(WindowEvent e) {
				// TODO Auto-generated method stub

			}

		});

	}

	public JFrame getFrame() {
		return frame;
	}
}
