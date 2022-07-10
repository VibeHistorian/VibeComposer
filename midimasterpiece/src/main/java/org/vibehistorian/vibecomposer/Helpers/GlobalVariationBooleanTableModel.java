package org.vibehistorian.vibecomposer.Helpers;

import javax.swing.table.AbstractTableModel;

public class GlobalVariationBooleanTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 8472479776056588708L;

	Object tableData[];

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
		return 1;
	}

	@Override
	public Object getValueAt(int row, int column) {
		return tableData[column];
	}

	@Override
	public Class<?> getColumnClass(int column) {
		return Boolean.class;
	}

	@Override
	public void setValueAt(Object value, int row, int column) {
		tableData[column] = value;
		if (column > 0 && value == Boolean.FALSE) {
			tableData[0] = Boolean.FALSE;
			fireTableDataChanged();
		} else if (column > 0 && value == Boolean.TRUE) {
			boolean allFilled = true;
			for (int i = 1; i < tableData.length; i++) {
				allFilled &= (Boolean) tableData[i];
			}
			if (allFilled) {
				tableData[0] = Boolean.TRUE;
			}
			fireTableDataChanged();
		} else if (column == 0) {
			for (int i = 1; i < tableData.length; i++) {
				tableData[i] = value;
			}
			fireTableDataChanged();
		}
	}

	@Override
	public boolean isCellEditable(int row, int column) {
		return true;
	}

	public GlobalVariationBooleanTableModel(Object[] data, String[] colNames) {
		tableData = data;
		columnNames = colNames;
	}
}
