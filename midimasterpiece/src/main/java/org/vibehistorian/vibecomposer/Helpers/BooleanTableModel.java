package org.vibehistorian.vibecomposer.Helpers;

import java.util.List;

import javax.swing.table.AbstractTableModel;

public class BooleanTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 8472479776056588708L;

	Object tableData[][];

	String columnNames[];

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
		if (column > 1 && tableData[row][1] == Boolean.FALSE) {
			return Boolean.FALSE;
		} else {
			return tableData[row][column];
		}
	}

	@Override
	public Class getColumnClass(int column) {
		return (getValueAt(0, column).getClass());
	}

	@Override
	public void setValueAt(Object value, int row, int column) {
		if (column > 1 && tableData[row][1] == Boolean.FALSE) {
			tableData[row][column] = Boolean.FALSE;
		} else {
			tableData[row][column] = value;
			if (column == 1 && tableData[row][column] == Boolean.FALSE) {
				for (int i = 2; i < tableData[row].length; i++) {
					tableData[row][i] = Boolean.FALSE;
				}
				fireTableDataChanged();
			}

		}

	}

	@Override
	public boolean isCellEditable(int row, int column) {
		return column > 0;
	}

	public BooleanTableModel(String[] colNames, List<Integer> rowNames) {
		tableData = new Object[rowNames.size()][colNames.length + 1];
		for (int i = 0; i < rowNames.size(); i++) {
			tableData[i][0] = rowNames.get(i);
			for (int j = 1; j < colNames.length + 1; j++) {
				tableData[i][j] = Boolean.FALSE;
			}
		}
		columnNames = colNames;
	}
}
