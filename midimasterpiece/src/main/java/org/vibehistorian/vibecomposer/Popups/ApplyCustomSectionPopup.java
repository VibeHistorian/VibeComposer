package org.vibehistorian.vibecomposer.Popups;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;

public class ApplyCustomSectionPopup extends CloseablePopup {

	JLabel description = new JLabel("Apply current change up to..");
	ScrollComboBox<String> sectionOptions = new ScrollComboBox<>(false);
	JButton applier = new JButton("APPLY");

	public ApplyCustomSectionPopup() {
		super("Apply Custom Section..", 11);
		JPanel panel = new JPanel();
		panel.add(description);

		int startIndex = VibeComposerGUI.arrSection.getSelectedIndex();
		for (int i = startIndex; i < VibeComposerGUI.arrSection.getItemCount(); i++) {
			sectionOptions.addItem(VibeComposerGUI.arrSection.getVal(i));
		}

		panel.add(sectionOptions);

		applier.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (sectionOptions.getItemCount() > 0) {
					VibeComposerGUI.vibeComposerGUI.handleArrangementAction(
							"ArrangementApply," + (sectionOptions.getSelectedIndex() + startIndex),
							0, 0);
				} else {
					VibeComposerGUI.vibeComposerGUI.handleArrangementAction("ArrangementApply", 0,
							0);
				}

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
