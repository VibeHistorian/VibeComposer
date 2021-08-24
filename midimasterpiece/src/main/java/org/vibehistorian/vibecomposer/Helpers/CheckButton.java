package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class CheckButton extends JButton {

	private static final long serialVersionUID = -3057766009648466934L;

	private boolean selected = false;
	boolean transparentBackground = false;

	public CheckButton(String name, boolean sel) {
		setText(name);
		selected = sel;
		setPreferredSize(new Dimension(25 + ((name.length() > 3) ? 6 * name.length() - 3 : 0), 25));
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
		addBackground(OMNI.alphen(VibeComposerGUI.isDarkMode ? VibeComposerGUI.darkModeUIColor
				: VibeComposerGUI.lightModeUIColor, selected ? 50 : 0));
	}

	public void addBackground(Color c) {
		transparentBackground = true;
		setOpaque(false);
		setBackground(c);
	}

	@Override
	protected void paintComponent(Graphics g) {
		if (transparentBackground) {
			g.setColor(getBackground());
			g.fillRect(0, 0, getWidth(), getHeight());
		}
		super.paintComponent(g);
	}

}
