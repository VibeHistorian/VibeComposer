package org.vibehistorian.vibecomposer.Components;

import org.vibehistorian.vibecomposer.ApplicationSessionState;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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
		this.setHorizontalTextPosition(SwingConstants.LEFT);
		addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				ApplicationSessionState.actionUndoManager.saveToHistory(CustomCheckBox.this,
						CustomCheckBox.this.isSelected() ? 0 : 1);
			}
		});
	}
}
