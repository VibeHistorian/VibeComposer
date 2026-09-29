package org.vibehistorian.vibecomposer.Popups;

import org.vibehistorian.vibecomposer.ArrangementGUI;
import org.vibehistorian.vibecomposer.INST;

import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.SwingUtils;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.IntFunction;

public class ApplyCustomSectionPopup extends CloseablePopup {

	JLabel description = new JLabel("Apply Until Section:");
	ScrollComboBox<String> sectionOptions = new ScrollComboBox<>(false);
	JButton applier = new JButton("APPLY");

	public ApplyCustomSectionPopup(IntFunction<List<? extends InstPanel>> getInstList,
			ArrangementGUI.ActionHandler arrangementAction) {
		super("Apply Custom Section..", 11);
		JPanel framePanel = new JPanel();
		framePanel.setLayout(new GridLayout(0, 1, 0, 0));
		JPanel panel = new JPanel();
		panel.add(description);

		int startIndex = ArrangementGUI.arrSection.getSelectedIndex();
		for (int i = startIndex; i < ArrangementGUI.arrSection.getItemCount(); i++) {
			sectionOptions.addItem(ArrangementGUI.arrSection.getVal(i));
		}

		panel.add(sectionOptions);

		applier.addActionListener(e -> {
            if (sectionOptions.getItemCount() > 0) {
				arrangementAction.handleArrangementAction(
                        "ArrangementApply," + (sectionOptions.getSelectedIndex() + startIndex),
                        0, 0);
            } else {
				arrangementAction.handleArrangementAction("ArrangementApply", 0,
                        0);
            }
            close();

        });
		panel.add(applier);
		panel.setPreferredSize(new Dimension(300, 100));

		JPanel panelGlobal = new JPanel();
		panelGlobal.add(SwingUtils.makeButton("Apply to Global", e -> {
			Section sec = ArrangementGUI.actualArrangement.getSections()
					.get(ArrangementGUI.arrSection.getSelectedIndex() - 1);
			for (INST instrument : INST.values()) {
				int i = instrument.getIndex();
				List<? extends InstPart> customizedParts = sec.getInstPartList(i);
				if (customizedParts != null) {
					List<? extends InstPanel> globalIps = getInstList.apply(i);
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
