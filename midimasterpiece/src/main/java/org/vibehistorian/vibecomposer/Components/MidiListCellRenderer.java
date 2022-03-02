package org.vibehistorian.vibecomposer.Components;

import java.awt.Component;
import java.io.File;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

public class MidiListCellRenderer extends DefaultListCellRenderer {

	private static final long serialVersionUID = -6608596536988370745L;

	@Override
	public Component getListCellRendererComponent(JList list, Object value, int index,
			boolean isSelected, boolean cellHasFocus) {
		Component cell = null;

		if (value instanceof File) {
			cell = super.getListCellRendererComponent(list, "Draggable.mid", index, isSelected,
					cellHasFocus);
		}

		return cell;
	}
}
