package org.vibehistorian.vibecomposer.Popups;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;

public class ApplyCustomSectionPopup extends CloseablePopup {

	JLabel description = new JLabel("Apply current change up to..");
	ScrollComboBox<String> sectionOptions = new ScrollComboBox<>(false);

	public ApplyCustomSectionPopup() {
		super("Apply Custom Section..", 11);
		JPanel panel = new JPanel();
		panel.add(description);
		panel.add(sectionOptions);
		frame.add(panel);
		frame.pack();
		frame.setVisible(true);
	}

	@Override
	protected void addFrameWindowOperation() {
		// TODO Auto-generated method stub

	}

}
