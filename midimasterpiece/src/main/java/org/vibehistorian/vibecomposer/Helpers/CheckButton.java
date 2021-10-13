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
	private Runnable runnable = null;

	public CheckButton(String name, boolean sel) {
		this(name, sel, null);
	}

	public CheckButton(String name, boolean sel, Color opaqueColor) {

		setText(name);
		if (opaqueColor != null) {
			bgColor = opaqueColor;
			setBackground(bgColor.darker().darker());
		} else {
			setBackground(OMNI.alphen(VibeComposerGUI.uiColor(), selected ? 60 : 0));
		}
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
		setSelected(sel);
	}

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
		addBackground();
		if (runnable != null) {
			runnable.run();
		}
	}

	public void setSelectedRaw(boolean selected) {
		this.selected = selected;
	}

	public void addRunnable(Runnable rn) {
		runnable = rn;
	}

	public void removeRunnable() {
		runnable = null;
	}

	public void addBackground() {
		transparentBackground = true;
		setOpaque(false);

	}

	@Override
	protected void paintComponent(Graphics g) {
		if (transparentBackground) {
			Color c = null;
			if (bgColor != null) {
				if (bgColor.getAlpha() < 255) {
					c = bgColor;
				} else {
					c = OMNI.alphen(bgColor, 80);
				}
			} else {
				c = OMNI.alphen(VibeComposerGUI.uiColor(), 80);
			}
			g.setColor(c);
			g.drawRect(0, 0, getWidth(), getHeight());
			if (selected) {
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		}
		super.paintComponent(g);
	}

}
