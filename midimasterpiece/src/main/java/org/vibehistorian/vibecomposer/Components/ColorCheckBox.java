package org.vibehistorian.vibecomposer.Components;

import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.UITheme;

import javax.swing.*;
import java.awt.*;

public class ColorCheckBox extends JCheckBox {

	private static final long serialVersionUID = 4002734391597854630L;

	public static int width = 15;
	public static int height = 15;
	private int marginLeft = 0, marginRight = 0;
	private Dimension defaultSize = new Dimension(width, height);
	public static boolean HIDE_MARGINS = false;
	private boolean highlighted = false;

	public ColorCheckBox(Dimension defSize) {
		defSize = defaultSize;
	}

	public ColorCheckBox() {
		defaultSize = new Dimension(width, height);
		setOpaque(true);
	}

	@Override
	public void paintComponent(Graphics guh) {
		if (guh == null) {
			return;
		}
		Graphics2D g = (Graphics2D) guh;
		//super.paintComponent(g);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		Color color = null;

		if (highlighted && isSelected()) {
			color = UITheme.isDarkMode ? VeloRect.highlightColorDark
					: VeloRect.highlightColorLight;
		} else {
			color = isSelected()
					? ((UITheme.isDarkMode) ? UITheme.darkModeUIColor.darker()
							: UITheme.lightModeUIColor)
					: UITheme.panelColorLow;
		}

		//g.setColor(color);
		int minX = (HIDE_MARGINS) ? marginLeft : 0;
		int maxX = (HIDE_MARGINS) ? this.getSize().width - minX - marginRight
				: this.getSize().width;

		int height = this.getSize().height;
		Color color1 = color;
		Color color2 = OMNI.mixColor(color1, Color.black, 0.1);
		GradientPaint gp = new GradientPaint(minX, 0, color1, maxX, 0, color2);
		g.setPaint(gp);

		g.fillRect(minX, 0, maxX, height);
		g.setColor(Color.black);
		g.drawRect(minX, 0, maxX, height);

		g.dispose();
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

	public boolean isHighlighted() {
		return highlighted;
	}

	public void setHighlighted(boolean highlighted) {
		this.highlighted = highlighted;
	}

}
