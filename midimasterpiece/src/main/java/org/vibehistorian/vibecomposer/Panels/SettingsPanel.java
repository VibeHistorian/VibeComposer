package org.vibehistorian.vibecomposer.Panels;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.BevelBorder;

public class SettingsPanel extends JPanel {

	private static final long serialVersionUID = -2303731820368636826L;

	private int alwaysVisible = 8;
	private boolean showAll = true;
	private JCheckBox enabled = new JCheckBox("Enable", true);

	private JButton expandCollapseButton = new JButton("-");
	private Class instClass = null;

	public SettingsPanel(String title, Class clazz) {
		super();
		setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
		instClass = clazz;
		add(new JLabel(title));
		add(enabled);
	}

	public int getAlwaysVisible() {
		return alwaysVisible;
	}

	public void setAlwaysVisible(int alwaysVisible) {
		this.alwaysVisible = alwaysVisible;
	}

	public void showMinimalComponents() {
		Component[] cmps = getComponents();
		for (int i = alwaysVisible; i < cmps.length; i++) {
			cmps[i].setVisible(false);
		}
	}

	public void showAllComponents() {
		Component[] cmps = getComponents();
		for (int i = alwaysVisible; i < cmps.length; i++) {
			cmps[i].setVisible(true);
		}
	}

	public void finishMinimalInit() {
		alwaysVisible = getComponents().length + 1;
		expandCollapseButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (showAll) {
					showAll = false;
					showMinimalComponents();
					expandCollapseButton.setText("+");
				} else {
					showAll = true;
					showAllComponents();
					expandCollapseButton.setText("-");
				}
			}
		});
		expandCollapseButton.setPreferredSize(new Dimension(30, 30));
		add(expandCollapseButton);
	}

	public boolean getEnabled() {
		return enabled.isSelected();
	}

	public void setEnabled(boolean enabled) {
		this.enabled.setSelected(enabled);
	}
}
