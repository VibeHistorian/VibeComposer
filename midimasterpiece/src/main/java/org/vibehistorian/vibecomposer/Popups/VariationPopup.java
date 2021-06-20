package org.vibehistorian.vibecomposer.Popups;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import org.vibehistorian.vibecomposer.Helpers.BooleanTableModel;

public class VariationPopup {
	final JFrame frame = new JFrame();
	JTable table;
	Boolean[][] tableData;

	JScrollPane scroll;

	public VariationPopup(String[] variationDescriptions, int partsCount) {
		table = new JTable(partsCount, variationDescriptions.length);
		table.setModel(new BooleanTableModel(variationDescriptions, 3));
		//table.setDefaultRenderer(Boolean.class, new BooleanRenderer());
		scroll = new JScrollPane(table, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		frame.add(scroll);
		frame.pack();
		frame.setVisible(true);

		System.out.println("Opened arrangement variation page!");
	}

	public JFrame getFrame() {
		return frame;
	}
}
