package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.Components.CheckButton;
import org.vibehistorian.vibecomposer.Components.CustomCheckBox;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox2;
import org.vibehistorian.vibecomposer.Components.ScrollComboPanel;
import org.vibehistorian.vibecomposer.Panels.KnobPanel;

import javax.swing.*;
import java.awt.Component;

/** Reads and restores the value-bearing state of shared GUI controls. */
public final class UIComponentState {
	private UIComponentState() {
	}

	public static void setValue(Component component, Integer value, boolean repaint) {
		if (component == null) {
			return;
		} else if (component instanceof ScrollComboPanel) {
			ScrollComboPanel<?> comboPanel = (ScrollComboPanel<?>) component;
			if (comboPanel.getItemCount() > 0) {
				comboPanel.setSelectedIndex(Math.min(value, comboPanel.getItemCount()));
			}
		} else if (component instanceof KnobPanel) {
			((KnobPanel) component).setInt(value);
		} else if (component instanceof CustomCheckBox) {
			((JCheckBox) component).setSelected(value != null && value > 0);
		} else if (component instanceof CheckButton) {
			((CheckButton) component).setSelected(value != null && value > 0);
		} else if (component instanceof ScrollComboBox2) {
			ScrollComboBox2<?> comboBox = (ScrollComboBox2<?>) component;
			if (comboBox.getItemCount() > 0) {
				comboBox.setSelectedIndex(Math.min(value, comboBox.getItemCount()));
			}
		} else if (component instanceof JTabbedPane) {
			JTabbedPane tabbedPane = (JTabbedPane) component;
			tabbedPane.setSelectedIndex(value < tabbedPane.getComponents().length ? value : value - 1);
		} else {
			throw new IllegalArgumentException("UNSUPPORTED COMPONENT!" + component.getClass());
		}
		if (repaint) {
			component.repaint();
		}
	}

	public static Integer getValue(Component component) {
		if (component == null) {
			return 0;
		}

		if (component instanceof ScrollComboPanel) {
			return ((ScrollComboPanel<?>) component).getSelectedIndex();
		} else if (component instanceof KnobPanel) {
			return ((KnobPanel) component).getInt();
		} else if (component instanceof CustomCheckBox) {
			return ((CustomCheckBox) component).isSelected() ? 1 : 0;
		} else if (component instanceof CheckButton) {
			return ((CheckButton) component).isSelected() ? 1 : 0;
		} else if (component instanceof ScrollComboBox2) {
			return ((ScrollComboBox2<?>) component).getSelectedIndex();
		} else if (component instanceof JTabbedPane) {
			return ((JTabbedPane) component).getSelectedIndex();
		} else {
			throw new IllegalArgumentException("UNSUPPORTED COMPONENT!" + component.getClass());
		}
	}
}
