package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.Icon;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class CheckBoxIcon implements Icon {

	public static int width = 15;
	public static int height = 15;

	@Override
	public void paintIcon(Component component, Graphics g, int x, int y) {
		AbstractButton abstractButton = (AbstractButton) component;
		ButtonModel buttonModel = abstractButton.getModel();

		Color color = buttonModel.isSelected()
				? ((VibeComposerGUI.isDarkMode) ? Color.CYAN.darker()
						: VibeComposerGUI.myBlueDarkMode)
				: ((VibeComposerGUI.isDarkMode) ? VibeComposerGUI.panelColorLow.brighter()
						: VibeComposerGUI.panelColorHigh);
		g.setColor(color);

		g.drawRect(1, 1, width, height);
		g.fillRect(3, 3, width - 2, height - 2);

	}

	@Override
	public int getIconWidth() {
		return width;
	}

	@Override
	public int getIconHeight() {
		return height;
	}
}

