package org.vibehistorian.vibecomposer.Popups;

import javax.swing.JPanel;

public class ChordletPickerPopup extends CloseablePopup {

	JPanel firstLetterPicker = new JPanel();
	JPanel spicyPicker = new JPanel();


	public ChordletPickerPopup(String windowTitle, Integer popupType) {
		super(windowTitle, 15);

		frame.pack();
		frame.setVisible(true);
	}

	@Override
	protected void addFrameWindowOperation() {
	}

}
