package org.vibehistorian.vibecomposer.Components;

import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class LockComponentButton extends JComponent {

	private static final long serialVersionUID = -6448690349479083935L;
	private Component linkedComponent = null;
	public int h = 8;
	public int w = 8;

	public LockComponentButton(Component comp) {
		linkedComponent = comp;
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (linkedComponent != null) {
					if ((e.isControlDown() || SwingUtilities.isMiddleMouseButton(e))
							&& linkedComponent instanceof GloballyLockable) {
						((GloballyLockable) linkedComponent)
								.setEnabledGlobal(!linkedComponent.isEnabled());
					} else {
						linkedComponent.setEnabled(!linkedComponent.isEnabled());
					}
					repaint();
				}
			}
		});
		setPreferredSize(new Dimension(w, h));
		setOpaque(true);
		setSize(new Dimension(w, h));
	}


	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (linkedComponent == null) {
			return;
		}
		boolean isLocked = linkedComponent.isEnabled();
		int iconIndex = isLocked ? 1 : 0;
		if (VibeComposerGUI.isDarkMode) {
			iconIndex += 2;
		}
		if (iconIndex >= 2 && g instanceof Graphics2D) {
			Graphics2D g2d = (Graphics2D) g;
			Composite c = AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, .5f);
			g2d.setComposite(c);
			g2d.drawImage(VibeComposerGUI.LOCK_COMPONENT_ICONS.get(iconIndex), 0, 0, this);
		} else {
			g.drawImage(VibeComposerGUI.LOCK_COMPONENT_ICONS.get(iconIndex), 0, 0, this);
		}
		//g.dispose();
	}


	public Component getLinkedComponent() {
		return linkedComponent;
	}


	public void setLinkedComponent(Component linkedComponent) {
		this.linkedComponent = linkedComponent;
	}

}
