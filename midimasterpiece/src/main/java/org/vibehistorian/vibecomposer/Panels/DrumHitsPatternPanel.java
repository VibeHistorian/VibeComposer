package org.vibehistorian.vibecomposer.Panels;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.vibehistorian.vibecomposer.Enums.RhythmPattern;

public class DrumHitsPatternPanel extends JPanel {

	private static final long serialVersionUID = 6963518339035392918L;

	private NumPanel hitsPanel = null;
	private JComboBox<String> patternType = null;
	private NumPanel shiftPanel = null;
	private int lastHits = 0;

	private List<Integer> truePattern = new ArrayList<>();
	private JCheckBox[] hitChecks = new JCheckBox[32];

	private JPanel parentPanel = null;

	public DrumHitsPatternPanel(NumPanel hitsPanel, JComboBox<String> patternType,
			NumPanel shiftPanel, JPanel parentPanel) {
		super();
		FlowLayout layout = new FlowLayout(FlowLayout.CENTER, 0, 0);
		layout.setVgap(0);
		layout.setHgap(0);
		setLayout(layout);
		setPreferredSize(new Dimension(170, 40));
		//setBorder(new BevelBorder(BevelBorder.LOWERED));
		this.hitsPanel = hitsPanel;
		this.patternType = patternType;
		this.shiftPanel = shiftPanel;
		this.parentPanel = parentPanel;
		lastHits = hitsPanel.getInt();

		for (int i = 0; i < 32; i++) {
			truePattern.add(0);
			hitChecks[i] = new JCheckBox("", false);
			hitChecks[i].setMargin(new Insets(0, 0, 0, 0));
			if (i >= hitsPanel.getInt()) {
				hitChecks[i].setVisible(false);
			}
			final int fI = i;
			hitChecks[i].addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					int shI = (fI - shiftPanel.getInt() + 32) % 32;
					truePattern.set(shI, hitChecks[fI].isSelected() ? 1 : 0);

				}

			});
			;
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

		hitsPanel.getSlider().addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent e) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						int nowHits = hitsPanel.getInt();
						if (nowHits > lastHits) {
							for (int i = lastHits; i < nowHits; i++) {
								hitChecks[i].setVisible(true);
							}

						} else if (nowHits < lastHits) {
							for (int i = nowHits; i < lastHits; i++) {
								hitChecks[i].setVisible(false);
							}
						}
						lastHits = nowHits;
						if (lastHits > 16) {
							DrumHitsPatternPanel.this.setPreferredSize(new Dimension(170, 80));
							parentPanel.setMaximumSize(new Dimension(3000, 90));
						} else {
							DrumHitsPatternPanel.this.setPreferredSize(new Dimension(170, 40));
							parentPanel.setMaximumSize(new Dimension(3000, 50));
						}
					}

				});

			}
		});
		shiftPanel.getSlider().addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent e) {
				for (int i = 0; i < 32; i++) {
					int shI = (i + shiftPanel.getInt()) % 32;
					hitChecks[shI].setSelected(truePattern.get(i) != 0);
				}
			}
		});
	}

	public List<Integer> getTruePattern() {
		return truePattern;
	}

	public void setTruePattern(List<Integer> truePattern) {
		this.truePattern = truePattern;
		for (int i = 0; i < 32; i++) {
			int shI = (i + shiftPanel.getInt()) % 32;
			hitChecks[shI].setSelected(truePattern.get(i) != 0);
		}
	}


}
