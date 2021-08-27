package org.vibehistorian.vibecomposer.Panels;

import java.awt.Color;
import java.awt.Graphics;

import javax.swing.JPanel;
import javax.swing.border.BevelBorder;

public class TransparentablePanel extends JPanel {

	private static final long serialVersionUID = 2375576843247236822L;

	boolean transparentBackground = false;

	@Override
	protected void paintComponent(Graphics g) {
		if (transparentBackground) {
			g.setColor(getBackground());
			g.fillRect(0, 0, getWidth(), getHeight());
		}
		super.paintComponent(g);
	}

	public void addBackgroundWithBorder(Color c) {
		addBackgroundWithBorder(c, BevelBorder.RAISED);
	}

	public void addBackgroundWithBorder(Color c, int borderType) {
		addBackground(c);
		this.setBorder(new BevelBorder(borderType));
	}

	public void addBackground(Color c) {
		transparentBackground = true;
		setOpaque(false);
		setBackground(c);
	}
}
