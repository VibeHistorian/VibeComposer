package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class ListCellRenderer extends JComponent implements TableCellRenderer {

	private static final long serialVersionUID = 7080615849668597111L;

	private int height = 10;
	private int width = 10;
	private Iterable<? extends Object> stringables = new ArrayList<>();

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		TableModel mdl = table.getModel();
		height = (int) ((VibeComposerGUI.scrollPaneDimension.getHeight() - 50) / mdl.getRowCount());
		width = (int) ((VibeComposerGUI.scrollPaneDimension.getWidth() - 100)
				/ mdl.getColumnCount());
		stringables = value instanceof String ? Collections.singleton((String) value)
				: (Iterable<? extends Object>) value;
		System.out.println("ListCellRenderer smth.");
		return this;
	}

	@Override
	public void paintComponent(Graphics guh) {

		if (guh instanceof Graphics2D) {
			Graphics2D g = (Graphics2D) guh;
			Color c = Color.white;

			g.setColor(c);
			g.fillRect(0, 0, width, height);
			g.setColor(Color.black);
			g.drawRect(0, 0, width, height);

			//g.setColor(OMNI.alphen(Color.black, 127));
			String value = StringUtils.join(stringables, ";");
			g.drawString(value, width / 2, height / 2);
			System.out.println("ListCellRenderer RENDERED");

		}
	}

}
