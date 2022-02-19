package org.vibehistorian.vibecomposer.Components;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Icon;
import javax.swing.JCheckBox;

import org.vibehistorian.vibecomposer.UndoManager;

public class CustomCheckBox extends JCheckBox {

	private static final long serialVersionUID = -1661897355378496721L;

	public CustomCheckBox(String string, boolean b) {
		this(string, null, b);
	}

	public CustomCheckBox() {
		this("", false);
	}

	public CustomCheckBox(String text, Icon icon) {
		this(text, icon, false);
	}

	public CustomCheckBox(String string) {
		this(string, false);
	}

	public CustomCheckBox(String text, Icon icon, boolean selected) {
		super(text, icon, selected);
		addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				UndoManager.saveToHistory(CustomCheckBox.this,
						CustomCheckBox.this.isSelected() ? 0 : 1);
			}
		});
	}
}
