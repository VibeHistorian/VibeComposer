package org.vibehistorian.vibecomposer.Panels;

import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.CheckButton;
import org.vibehistorian.vibecomposer.Helpers.SectionDropDownCheckButton;

public class ButtonSelectorPanel extends JPanel {

	private static final long serialVersionUID = -2425443567987119524L;
	private static int unremovableButtons = 1;
	private List<CheckButton> buttons = new ArrayList<>();
	private int selectedIndex = -1;

	public ButtonSelectorPanel(List<CheckButton> buttons, List<CheckButton> defaultButtons) {
		setOpaque(false);
		setMaximumSize(new Dimension(VibeComposerGUI.scrollPaneDimension.width, 30));
		setPreferredSize(new Dimension(VibeComposerGUI.scrollPaneDimension.width, 30));
		setMinimumSize(new Dimension(VibeComposerGUI.scrollPaneDimension.width, 30));
		unremovableButtons = defaultButtons.size();
		addAllButtons(defaultButtons);
		addAllButtons(buttons);
		setSelectedIndex(0);
	}

	public void addAllButtons(List<CheckButton> cbs) {
		int startSize = buttons.size();
		for (int i = 0; i < cbs.size(); i++) {
			addButton(cbs.get(i), i + startSize);
		}
	}

	public void addButton(CheckButton cb, int buttonIndex) {
		cb.addRunnable(new Runnable() {

			@Override
			public void run() {
				if (cb.isSelected()) {
					buttons.get(selectedIndex).setSelectedRaw(false);
					setSelectedIndex(buttonIndex);
				} else {
					if (selectedIndex == buttonIndex) {
						cb.setSelectedRaw(true);
					} else {
						setSelectedIndex(0);
					}
				}
				ButtonSelectorPanel.this.repaint();
			}
		});
		cb.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				if (buttonIndex > 0 && SwingUtilities.isMiddleMouseButton(evt)) {
					VibeComposerGUI.openVariationPopup(buttonIndex);
				}
			}
		});
		//cb.setMargin(new Insets(0, 0, 0, 0));
		cb.setPreferredSize(new Dimension(
				25 + ((cb.getText().length() > 3) ? 6 * cb.getText().length() - 4 : 0), 20));
		this.buttons.add(cb);
		add(cb);
	}

	public List<CheckButton> getButtons() {
		return buttons;
	}

	public void setButtons(List<CheckButton> buttons) {
		removeAll();
		this.buttons = this.buttons.subList(0, Math.min(this.buttons.size(), unremovableButtons));
		this.buttons.forEach(e -> add(e));
		addAllButtons(buttons);
		setSelectedIndex(0);
	}

	public String getVal() {
		return buttons.get(selectedIndex).getText();
	}

	public String getVal(int index) {
		return buttons.get(index).getText();
	}

	public void addAll(String[] items) {
		for (String i : items) {
			addItem(i);
		}
	}

	public void addItem(String item) {
		if (selectedIndex < 0 || buttons.isEmpty()) {
			selectedIndex = 0;
		}
		CheckButton cb = new SectionDropDownCheckButton(item, false);
		addButton(cb, buttons.size());
	}

	public int getSelectedIndex() {
		return selectedIndex;
	}

	public void setSelectedIndexWithProperty(int selectedIndex, boolean forcedPropertyChange) {
		boolean needFirePropertyChange = forcedPropertyChange;
		if (this.selectedIndex != selectedIndex) {
			needFirePropertyChange = true;
			if (this.selectedIndex >= 0 && this.selectedIndex < buttons.size()) {
				buttons.get(this.selectedIndex).setSelectedRaw(false);
			}
			this.selectedIndex = selectedIndex;
			buttons.get(selectedIndex).setSelectedRaw(true);
		}
		if (needFirePropertyChange) {
			firePropertyChange("selectedIndex", -1, selectedIndex);
		}
	}

	public void setSelectedIndex(int selectedIndex) {
		setSelectedIndexWithProperty(selectedIndex, false);
	}

	public int getItemCount() {
		return buttons.size();
	}
}
