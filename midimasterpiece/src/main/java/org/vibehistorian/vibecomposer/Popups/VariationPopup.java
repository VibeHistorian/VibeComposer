package org.vibehistorian.vibecomposer.Popups;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.BooleanTableModel;

public class VariationPopup {

	public static final String[] tableNames = { "Melody", "Bass", "Chords", "Arps", "Drums" };
	public static final String[][] variationDescriptions = { { "#", "Transpose", "MaxJump" },
			{ "#", "OffsetSeed" }, { "#", "Transpose", "IgnoreFill", "UpStretch" },
			{ "#", "Transpose", "IgnoreFill" }, { "#", "IgnoreFill", "MoreExceptions" } };

	final JFrame frame = new JFrame();
	JPanel tablesPanel = new JPanel();
	JTable[] tables = new JTable[5];

	JScrollPane scroll;

	public VariationPopup(int section) {
		tablesPanel.setLayout(new BoxLayout(tablesPanel, BoxLayout.Y_AXIS));
		for (int i = 0; i < 5; i++) {
			List<Integer> rowOrders = VibeComposerGUI.getInstList(i).stream()
					.map(e -> e.getPanelOrder()).collect(Collectors.toList());
			Collections.sort(rowOrders);
			JTable table = new JTable();
			table.setModel(new BooleanTableModel(variationDescriptions[i], rowOrders));
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

			tables[i] = table;
			JButton namedTableToggle = new JButton(tableNames[i] + "(+)");
			namedTableToggle.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					table.setVisible(!table.isVisible());

				}

			});
			tablesPanel.add(namedTableToggle);
			tablesPanel.add(tables[i].getTableHeader());
			tablesPanel.add(tables[i]);
		}


		scroll = new JScrollPane(tablesPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		frame.setPreferredSize(new Dimension(400, 600));
		frame.add(scroll);
		frame.setTitle("Variations - Section " + section);
		frame.pack();
		frame.setVisible(true);

		System.out.println("Opened arrangement variation page!");
	}

	public JFrame getFrame() {
		return frame;
	}
}
