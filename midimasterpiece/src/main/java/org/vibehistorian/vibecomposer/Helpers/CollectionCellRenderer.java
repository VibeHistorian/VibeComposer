package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class CollectionCellRenderer extends JComponent implements TableCellRenderer {

	private static final long serialVersionUID = 7080615849668597111L;

	private int height = 10;
	private int width = 10;
	private Collection<? extends Object> stringables = new ArrayList<>();

	public CollectionCellRenderer(Collection<? extends Object> itrs, int w, int h) {
		stringables = itrs;
		this.height = h;
		this.width = w;
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				System.out.println("YEP");

			}
		});
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		TableModel mdl = table.getModel();
		height = (int) ((VibeComposerGUI.scrollPaneDimension.getHeight() - 50) / mdl.getRowCount());
		width = (int) ((VibeComposerGUI.scrollPaneDimension.getWidth() - 100)
				/ mdl.getColumnCount());
		stringables = value instanceof String ? Collections.singleton((String) value)
				: (Collection<? extends Object>) value;
		System.out.println("ListCellRenderer smth.");

		return this;
	}

	@Override
	public void paintComponent(Graphics guh) {

		if (guh instanceof Graphics2D) {
			Graphics2D g = (Graphics2D) guh;
			Color c = VibeComposerGUI.panelColorLow;

			g.setColor(c);
			g.fillRect(0, 0, width, height);
			g.setColor(Color.black);
			g.drawRect(0, 0, width, height);

			//g.setColor(OMNI.alphen(Color.black, 127));
			if (stringables.size() == 0) {
				return;
			}
			double x = (width / stringables.size()) / 2;
			int counter = 0;
			for (Object o : stringables) {
				g.drawString(o.toString(), (int) x - 5, height / 2);
				x += (width / (double) stringables.size()) / 2;
				g.drawLine((int) x, 0, (int) x, height);
				x += (width / (double) stringables.size()) / 2;
			}
			//String value = StringUtils.join(stringables, ";");
			//g.drawString(value, 2, height / 2);
			System.out.println("ListCellRenderer RENDERED");

		}
	}

}
