package org.vibehistorian.vibecomposer.Helpers;

import java.util.List;

import javax.swing.table.AbstractTableModel;

public class PartInclusionBooleanTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 8472479776056588708L;

	int part = 0;

	Object tableData[][];

	String columnNames[];

	List<String> partNames;

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
		if (column == 0 && partNames != null && row < partNames.size()) {
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
		tableData[row][column] = value;
		if (column > 1 && value == Boolean.FALSE) {
			tableData[row][1] = Boolean.FALSE;
			fireTableDataChanged();
		} else if (column > 1 && value == Boolean.TRUE) {
			boolean allFilled = true;
			for (int i = 2; i < tableData[row].length; i++) {
				allFilled &= (Boolean) tableData[row][i];
			}
			if (allFilled) {
				tableData[row][1] = Boolean.TRUE;
			}
			fireTableDataChanged();
		} else if (column == 1 && value == Boolean.TRUE) {
			for (int i = 2; i < tableData[row].length; i++) {
				tableData[row][i] = Boolean.TRUE;
			}
			fireTableDataChanged();
		}
	}

	@Override
	public boolean isCellEditable(int row, int column) {
		return column > 0;
	}

	public PartInclusionBooleanTableModel(int part, Object[][] data, String[] colNames,
			List<String> partNames) {
		this.part = part;
		tableData = data;
		columnNames = colNames;
		this.partNames = partNames;
	}
}
