package org.vibehistorian.vibecomposer.Popups;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.JTableHeader;

import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.BooleanRenderer;
import org.vibehistorian.vibecomposer.Helpers.BooleanTableModel;

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
		JComboBox<String> measureCombo = new JComboBox<>();
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
			table.setDefaultRenderer(Boolean.class, new BooleanRenderer());

			/*JList<String> list = new JList<>();
			String[] listData = new String[rowCount];
			for (int j = 0; j < rowCount; j++) {
				listData[j] = String.valueOf(j);
			}
			list.setListData(listData);
			list.setFixedCellHeight(table.getRowHeight() + table.getRowMargin());*/

			tables[i] = table;
			JButton namedTableToggle = new JButton(tableNames[i] + " (+)");
			namedTableToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
			namedTableToggle.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					table.setVisible(!table.isVisible());

				}

			});
			tablesPanel.add(namedTableToggle);
			JTableHeader header = tables[i].getTableHeader();
			header.setAlignmentX(Component.LEFT_ALIGNMENT);
			tablesPanel.add(header);
			tablesPanel.add(tables[i]);
		}

		addRiskyVariations(sec);

		scroll = new JScrollPane(tablesPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scroll.getVerticalScrollBar().setUnitIncrement(16);

		int heightLimit = 800;
		frame.setPreferredSize(new Dimension(500,
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
