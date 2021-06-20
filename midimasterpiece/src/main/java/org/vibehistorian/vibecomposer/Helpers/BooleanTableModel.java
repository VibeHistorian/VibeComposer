package org.vibehistorian.vibecomposer.Helpers;

import javax.swing.table.AbstractTableModel;

public class BooleanTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 8472479776056588708L;

	Object tableData[][];

	String columnNames[];

	public int getColumnCount() {
		return columnNames.length;
	}

	public String getColumnName(int column) {
		return columnNames[column];
	}

	public int getRowCount() {
		return tableData.length;
	}

	public Object getValueAt(int row, int column) {
		return tableData[row][column];
	}

	public Class getColumnClass(int column) {
		return (getValueAt(0, column).getClass());
	}

	public void setValueAt(Object value, int row, int column) {
		tableData[row][column] = value;
	}

	public boolean isCellEditable(int row, int column) {
		return (column != 0);
	}

	public BooleanTableModel(String[] colNames, int rowCount) {
		tableData = new Boolean[colNames.length][rowCount];
		for (int i = 0; i < colNames.length; i++) {
			for (int j = 0; j < rowCount; j++) {
				tableData[i][j] = Boolean.FALSE;
			}
		}
		columnNames = colNames;
	}
}
