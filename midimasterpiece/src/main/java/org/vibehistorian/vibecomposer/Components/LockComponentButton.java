package org.vibehistorian.vibecomposer.Components;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;

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
					if (e.isControlDown() && linkedComponent instanceof GloballyLockable) {
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
		setOpaque(false);
		setSize(new Dimension(w, h));
	}


	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (linkedComponent == null) {
			return;
		}
		boolean isLocked = linkedComponent.isEnabled();
		int iconIndex = isLocked ? 0 : 1;
		g.drawImage(VibeComposerGUI.LOCK_COMPONENT_ICONS.get(iconIndex), 0, 0, this);
		//g.dispose();
	}


	public Component getLinkedComponent() {
		return linkedComponent;
	}


	public void setLinkedComponent(Component linkedComponent) {
		this.linkedComponent = linkedComponent;
	}

}
