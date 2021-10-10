package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

import javax.swing.JCheckBox;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class ColorCheckBox extends JCheckBox {

	private static final long serialVersionUID = 4002734391597854630L;

	public static int width = 15;
	public static int height = 15;
	private int marginLeft = 0, marginRight = 0;
	private Dimension defaultSize = new Dimension(width, height);
	public static boolean HIDE_MARGINS = false;

	public ColorCheckBox(Dimension defSize) {
		defSize = defaultSize;
	}

	public ColorCheckBox() {
		defaultSize = new Dimension(width, height);
	}

	@Override
	public void paintComponent(Graphics guh) {
		Graphics2D g = (Graphics2D) guh;


		Color color = isSelected()
				? ((VibeComposerGUI.isDarkMode) ? VibeComposerGUI.darkModeUIColor.darker()
						: VibeComposerGUI.lightModeUIColor)
				: ((VibeComposerGUI.isDarkMode) ? VibeComposerGUI.panelColorLow
						: VibeComposerGUI.panelColorHigh);
		g.setColor(color);
		int minX = (HIDE_MARGINS) ? marginLeft : 0;
		int maxX = (HIDE_MARGINS) ? this.getSize().width - minX - marginRight
				: this.getSize().width;
		g.fillRect(minX, 0, maxX, this.getSize().height);
		g.setColor(Color.black);
		g.drawRect(minX, 0, maxX, this.getSize().height);


	}

	@Override
	public void setMargin(Insets in) {
		marginLeft = in.left;
		marginRight = in.right;
		if (defaultSize != null) {
			updateSizes(new Dimension(defaultSize.width + marginLeft + marginRight,
					defaultSize.height));
		}
	}

	public void setMarginLeftRight(int left, int right) {
		marginLeft = left;
		marginRight = right;
		if (defaultSize != null) {
			updateSizes(new Dimension(defaultSize.width + marginLeft + marginRight,
					defaultSize.height));
		}

	}

	public void updateSizes(Dimension size) {
		setPreferredSize(size);
		setMinimumSize(size);
		setMaximumSize(size);
		setSize(size);
	}

	public void setDefaultSize(Dimension size) {
		defaultSize = size;
	}

	@Override
	public int getHeight() {
		return height;
	}

}
