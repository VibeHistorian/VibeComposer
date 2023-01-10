package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.SwingConstants;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class CheckBoxIcon implements Icon {

	public static int width = 15;
	public static int height = 15;

	@Override
	public void paintIcon(Component component, Graphics g, int x, int y) {
		AbstractButton abstractButton = (AbstractButton) component;
		ButtonModel buttonModel = abstractButton.getModel();

		Color color = buttonModel.isSelected()
				? ((VibeComposerGUI.isDarkMode) ? VibeComposerGUI.darkModeUIColor.darker()
						: VibeComposerGUI.lightModeUIColor)
				: ((VibeComposerGUI.isDarkMode) ? VibeComposerGUI.panelColorLow
						: VibeComposerGUI.panelColorHigh);
		g.setColor(color);

		int newHeight = (abstractButton.getHeight() - height) / 2;
		if (abstractButton.getHorizontalTextPosition() == SwingConstants.LEFT) {
			int newWidth = abstractButton.getWidth() - width;
			g.drawRect(newWidth - 3, newHeight, width, height);
			g.fillRect(newWidth - 1, newHeight + 2, width - 2, height - 2);
		} else {
			g.drawRect(1, newHeight, width, height);
			g.fillRect(3, newHeight + 2, width - 2, height - 2);
		}
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

