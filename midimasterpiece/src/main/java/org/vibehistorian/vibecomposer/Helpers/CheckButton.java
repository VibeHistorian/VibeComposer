package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;

import org.apache.commons.lang3.StringUtils;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class CheckButton extends JButton {

	private static final long serialVersionUID = -3057766009648466934L;

	private boolean selected = false;
	private boolean transparentBackground = false;
	private Color bgColor = null;

	public CheckButton(String name, boolean sel, Color opaqueColor) {
		this(name, sel);
		bgColor = opaqueColor;
	}

	public CheckButton(String name, boolean sel) {
		setText(name);
		setSelected(sel);
		setBackground(OMNI.alphen(VibeComposerGUI.isDarkMode ? VibeComposerGUI.darkModeUIColor
				: VibeComposerGUI.lightModeUIColor, selected ? 60 : 0));
		if (StringUtils.isEmpty(name)) {
			setPreferredSize(new Dimension(20, 20));
		} else {
			setPreferredSize(
					new Dimension(25 + ((name.length() > 3) ? 6 * name.length() - 3 : 0), 25));
		}
		setMargin(new Insets(0, 0, 0, 0));
		addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				setSelected(!selected);
			}

		});
	}

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
		addBackground();
	}

	public void addBackground() {
		transparentBackground = true;
		setOpaque(false);

	}

	@Override
	protected void paintComponent(Graphics g) {
		if (transparentBackground) {
			if (bgColor != null) {
				g.setColor(OMNI.alphen(bgColor, 50));
			} else {
				g.setColor(OMNI.alphen(VibeComposerGUI.isDarkMode ? VibeComposerGUI.darkModeUIColor
						: VibeComposerGUI.lightModeUIColor, selected ? 60 : 0));
			}

			g.fillRect(0, 0, getWidth(), getHeight());
		}
		super.paintComponent(g);
	}

}
