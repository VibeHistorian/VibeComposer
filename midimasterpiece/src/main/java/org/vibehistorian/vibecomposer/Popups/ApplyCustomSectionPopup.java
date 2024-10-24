package org.vibehistorian.vibecomposer.Popups;

import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ApplyCustomSectionPopup extends CloseablePopup {

	JLabel description = new JLabel("Apply Until Section:");
	ScrollComboBox<String> sectionOptions = new ScrollComboBox<>(false);
	JButton applier = new JButton("APPLY");

	public ApplyCustomSectionPopup() {
		super("Apply Custom Section..", 11);
		JPanel framePanel = new JPanel();
		framePanel.setLayout(new GridLayout(0, 1, 0, 0));
		JPanel panel = new JPanel();
		panel.add(description);

		int startIndex = VibeComposerGUI.arrSection.getSelectedIndex();
		for (int i = startIndex; i < VibeComposerGUI.arrSection.getItemCount(); i++) {
			sectionOptions.addItem(VibeComposerGUI.arrSection.getVal(i));
		}

		panel.add(sectionOptions);

		applier.addActionListener(e -> {
            if (sectionOptions.getItemCount() > 0) {
                VibeComposerGUI.vibeComposerGUI.handleArrangementAction(
                        "ArrangementApply," + (sectionOptions.getSelectedIndex() + startIndex),
                        0, 0);
            } else {
                VibeComposerGUI.vibeComposerGUI.handleArrangementAction("ArrangementApply", 0,
                        0);
            }
            close();

        });
		panel.add(applier);
		panel.setPreferredSize(new Dimension(300, 100));

		JPanel panelGlobal = new JPanel();
		panelGlobal.add(VibeComposerGUI.makeButton("Apply to Global", e -> {
			Section sec = VibeComposerGUI.actualArrangement.getSections()
					.get(VibeComposerGUI.arrSection.getSelectedIndex() - 1);
			for (int i = 0; i < 5; i++) {
				List<? extends InstPart> customizedParts = sec.getInstPartList(i);
				if (customizedParts != null) {
					List<? extends InstPanel> globalIps = VibeComposerGUI.getInstList(i);
					for (int j = 0; j < customizedParts.size(); j++) {
						InstPart ip = customizedParts.get(j);
						globalIps.get(j).setFromInstPart(ip);

					}
				}
			}
		}));
		panelGlobal.setPreferredSize(new Dimension(300, 100));


		framePanel.setPreferredSize(new Dimension(300, 200));
		framePanel.add(panel);
		framePanel.add(panelGlobal);
		frame.add(framePanel);
		frame.pack();
		frame.setVisible(true);
	}

	@Override
	protected void addFrameWindowOperation() {

	}

}
