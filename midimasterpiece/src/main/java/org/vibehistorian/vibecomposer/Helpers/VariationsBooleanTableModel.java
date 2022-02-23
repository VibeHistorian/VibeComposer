package org.vibehistorian.vibecomposer.Helpers;

import java.util.List;
import java.util.Set;

import javax.swing.table.AbstractTableModel;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class VariationsBooleanTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 8472479776056588708L;

	int part = 0;

	Object tableData[][];

	String columnNames[];

	List<String> partNames;

	int sectionOrder;

	@Override
	public int getColumnCount() {
		return columnNames.length;
	}

	@Override
	public String getColumnName(int column) {
		return columnNames[column];
	}

	@Override
	public int getRowCount() {
		return tableData.length;
	}

	@Override
	public Object getValueAt(int row, int column) {
		if (column >= 2
				&& !VibeComposerGUI.arrangement.getGlobalVariationMap().get(part)[column - 1]) {
			return "X";
		}
		if (column > 1 && tableData[row][1] == Boolean.FALSE) {
			return Boolean.FALSE;
		} else if (column == 0 && partNames != null && row < partNames.size()) {
			String data = String.valueOf(tableData[row][column]);
			data += (". " + partNames.get(row));
			return data;
		} else {
			return tableData[row][column];
		}
	}

	@Override
	public Class<?> getColumnClass(int column) {
		return (getValueAt(0, column).getClass());
	}

	@Override
	public void setValueAt(Object value, int row, int column) {
		if (!VibeComposerGUI.arrangement.getGlobalVariationMap().get(part)[column - 1]) {
			tableData[row][column] = Boolean.FALSE;
			fireTableDataChanged();
			return;
		}
		Boolean oldPresence = (Boolean) tableData[row][1];
		if (column > 1 && tableData[row][1] == Boolean.FALSE) {
			if (value == Boolean.TRUE) {
				tableData[row][1] = Boolean.TRUE;
				tableData[row][column] = Boolean.TRUE;
				fireTableDataChanged();
			}
		} else {
			tableData[row][column] = value;
			if (column == 1 && tableData[row][column] == Boolean.FALSE) {
				for (int i = 2; i < tableData[row].length; i++) {
					tableData[row][i] = Boolean.FALSE;
				}
				fireTableDataChanged();
			}

		}
		int realTableRow = part + 2;
		if (oldPresence != null && !oldPresence.equals(tableData[row][1])) {
			Object vcVal = VibeComposerGUI.scrollableArrangementActualTable.getModel()
					.getValueAt(realTableRow, sectionOrder);
			if (vcVal instanceof Set) {
				Set<Integer> presence = (Set<Integer>) vcVal;
				Integer realOrder = (Integer) tableData[row][0];
				if (oldPresence) {
					presence.remove(realOrder);
				} else {
					presence.add(realOrder);
				}
				VibeComposerGUI.scrollableArrangementActualTable.getModel().setValueAt(presence,
						realTableRow, sectionOrder);
			}
		}

		VibeComposerGUI.scrollableArrangementActualTable.repaint();
	}

	@Override
	public boolean isCellEditable(int row, int column) {
		return column > 0;
	}

	public VariationsBooleanTableModel(int part, int sectionOrder, Object[][] data,
			String[] colNames, List<String> partNames) {
		this.part = part;
		this.sectionOrder = sectionOrder;
		tableData = data;
		columnNames = colNames;
		this.partNames = partNames;
	}
}
