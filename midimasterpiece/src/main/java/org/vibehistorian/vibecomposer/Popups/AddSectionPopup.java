package org.vibehistorian.vibecomposer.Popups;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.vibehistorian.vibecomposer.Arrangement;
import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;

public class AddSectionPopup extends CloseablePopup {

	JLabel description = new JLabel("Apply Until Section:");
	ScrollComboBox<String> sectionOptions = new ScrollComboBox<>(false);
	JButton applier = new JButton("APPLY");

	public AddSectionPopup(String windowTitle, Integer popupType) {
		super("Add/Insert Section", 12);
		JPanel panel = new JPanel();
		panel.add(description);

		Arrangement.defaultSections.keySet().forEach(e -> sectionOptions.addItem(e));

		panel.add(sectionOptions);
		applier.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {

			}
		});
		panel.add(applier);
		frame.add(panel);
		frame.pack();
		frame.setVisible(true);
	}

	@Override
	protected void addFrameWindowOperation() {
		// TODO Auto-generated method stub

	}

}
