package org.vibehistorian.vibecomposer.Panels;

import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import org.vibehistorian.vibecomposer.Enums.RhythmPattern;

public class DrumHitsPatternPanel extends JPanel {

	private static final long serialVersionUID = 6963518339035392918L;

	private NumPanel hitsPanel = null;
	private JComboBox<String> patternType = null;
	private NumPanel shiftPanel = null;
	private int lastHits = 0;

	private List<Integer> truePattern = new ArrayList<>();
	private JCheckBox[] hitChecks = new JCheckBox[32];

	public DrumHitsPatternPanel(NumPanel hitsPanel, JComboBox<String> patternType,
			NumPanel shiftPanel) {
		super();
		this.hitsPanel = hitsPanel;
		this.patternType = patternType;
		this.shiftPanel = shiftPanel;
		lastHits = hitsPanel.getInt();

		for (int i = 0; i < 32; i++) {
			truePattern.add(0);
			hitChecks[i] = new JCheckBox("", false);
			hitChecks[i].setMargin(new Insets(0, 0, 0, 0));
			if (i >= hitsPanel.getInt()) {
				hitChecks[i].setVisible(false);
			}
			add(hitChecks[i]);
		}

		patternType.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					RhythmPattern d = RhythmPattern.valueOf((String) patternType.getSelectedItem());
					if (d != RhythmPattern.CUSTOM) {
						for (int i = 0; i < 32; i++) {
							hitChecks[i].setEnabled(false);
						}
					} else {
						for (int i = 0; i < 32; i++) {
							hitChecks[i].setEnabled(true);
						}
					}
					truePattern = d.getPatternByLength(32);

					for (int i = 0; i < 32; i++) {
						int shI = (i + shiftPanel.getInt()) % 32;
						hitChecks[shI].setSelected(truePattern.get(i) != 0);
					}
				}


			}

		});

	}


}
