package org.vibehistorian.vibecomposer.Popups;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.Helpers.BooleanTableModel;

public class VariationPopup {

	public static final String[] tableNames = { "Melody", "Bass", "Chords", "Arps", "Drums" };


	final JFrame frame = new JFrame();
	JPanel tablesPanel = new JPanel();
	JTable[] tables = new JTable[5];

	JScrollPane scroll;

	public VariationPopup(int section, Section sec) {
		tablesPanel.setLayout(new BoxLayout(tablesPanel, BoxLayout.Y_AXIS));
		for (int i = 0; i < 5; i++) {

			JTable table = new JTable();
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
