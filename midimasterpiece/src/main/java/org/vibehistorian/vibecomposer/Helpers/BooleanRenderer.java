package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Component;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;

public class BooleanRenderer extends JPanel implements TableCellRenderer {

	private static final long serialVersionUID = 3986897083726263464L;
	private static final Border noFocusBorder = new EmptyBorder(1, 1, 1, 1);

	private JCheckBox checkbox;

	public BooleanRenderer() {
		super();
		checkbox = new JCheckBox();
		add(checkbox);
	}

	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {
		checkbox.setSelected((value != null && ((Boolean) value).booleanValue()));

		return this;
	}
}