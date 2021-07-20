package org.vibehistorian.vibecomposer.Panels;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Helpers.CheckBoxIcon;
import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;

public class DrumHitsPatternPanel extends JPanel {

	private static final long serialVersionUID = 6963518339035392918L;

	private KnobPanel hitsPanel = null;
	private ScrollComboBox<String> patternType = null;
	private KnobPanel shiftPanel = null;
	private KnobPanel chordSpanPanel = null;
	private JButton doublerButton = null;

	private int lastHits = 0;

	private List<Integer> truePattern = new ArrayList<>();
	private JCheckBox[] hitChecks = new JCheckBox[32];
	private JLabel[] separators = new JLabel[3];

	private JPanel parentPanel = null;

	public static int width = 8 * CheckBoxIcon.width;
	public static int height = 2 * CheckBoxIcon.width;

	public static int mouseButton = 0;

	public static List<Integer> sextuplets = Arrays.asList(new Integer[] { 6, 12, 24 });
	public static List<Integer> quintuplets = Arrays.asList(new Integer[] { 5 });
	public static List<Integer> triplets = Arrays.asList(new Integer[] { 3 });


	public static Map<Integer, Insets> smallModeInsetMap = new HashMap<>();
	static {
		smallModeInsetMap.put(4, new Insets(0, 0, 0, CheckBoxIcon.width * 4 / 4));
		smallModeInsetMap.put(6, new Insets(0, 0, 0, CheckBoxIcon.width * 2 / 6));
		smallModeInsetMap.put(10, new Insets(0, 0, 0, CheckBoxIcon.width * 3 / 5));
		smallModeInsetMap.put(12, new Insets(0, 0, 0, CheckBoxIcon.width * 2 / 6));
		//smallModeInsetMap.put(18, new Insets(0, 0, 0, CheckBoxIcon.width * 2 / 6));
		smallModeInsetMap.put(24, new Insets(0, 0, 0, CheckBoxIcon.width * 2 / 6));
	}

	public static Map<Integer, Insets> bigModeInsetMap = new HashMap<>();
	static {

		for (int i = 4; i < 32; i++) {
			bigModeInsetMap.put(i, new Insets(0, 0, 0, CheckBoxIcon.width * (32 - i) / i));
		}
	}

	public static Map<Integer, Insets> bigModeDoubleChordGeneralInsetMap = new HashMap<>();
	static {

		for (int i = 4; i <= 32; i++) {
			bigModeDoubleChordGeneralInsetMap.put(i,
					new Insets(0, 0, 0, 2 * CheckBoxIcon.width * (32 - i / 2) / i));
		}
	}

	public static Map<Integer, Insets> bigModeDoubleChordTransitionInsetMap = new HashMap<>();
	static {

		for (int i = 4; i <= 32; i++) {
			bigModeDoubleChordTransitionInsetMap.put(i,
					new Insets(0, CheckBoxIcon.width * (32 - i / 2) / i, 0,
							CheckBoxIcon.width * (32 - i / 2) / i));
		}
	}


	public DrumHitsPatternPanel(KnobPanel hitsPanel, ScrollComboBox<String> patternType,
			KnobPanel shiftPanel, KnobPanel chordSpanPanel, JButton doubler, JPanel parentPanel) {
		super();
		//setBackground(new Color(50, 50, 50));
		FlowLayout layout = new FlowLayout(FlowLayout.CENTER, 0, 0);
		//layout.setVgap(0);
		//layout.setHgap(0);
		setLayout(layout);
		setPreferredSize(new Dimension(width, height));
		//setBorder(new BevelBorder(BevelBorder.LOWERED));
		this.hitsPanel = hitsPanel;
		this.patternType = patternType;
		this.shiftPanel = shiftPanel;
		this.parentPanel = parentPanel;
		this.chordSpanPanel = chordSpanPanel;
		doublerButton = doubler;
		lastHits = hitsPanel.getInt();
		int sepCounter = 0;
		for (int i = 0; i < 32; i++) {
			final int fI = i;
			truePattern.add(0);
			hitChecks[i] = new JCheckBox("", new CheckBoxIcon());
			//hitChecks[i].setBackground(new Color(128, 128, 128));
			hitChecks[i].addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					//System.out.println("True pattern size: " + truePattern.size());
					if (mouseButton == 2) {
						hitChecks[fI].setSelected(true);
					} else if (mouseButton == 3) {
						hitChecks[fI].setSelected(false);
					}
					int shI = (fI - shiftPanel.getInt() + 32) % 32;
					truePattern.set(shI, hitChecks[fI].isSelected() ? 1 : 0);
				}

			});
			hitChecks[i].addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					int mouseButt = e.getButton();
					if (mouseButt == 1) {
						mouseButton = -1;
					} else if (mouseButt > 1) {
						mouseButton = mouseButt;
						if (RhythmPattern.valueOf(
								(String) patternType.getSelectedItem()) != RhythmPattern.CUSTOM) {
							patternType.setSelectedItem(RhythmPattern.CUSTOM.toString());
						}
					}
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					mouseButton = -1;
				}
			});
			hitChecks[i].setMargin(new Insets(0, 0, 0, 0));
			if (i >= hitsPanel.getInt()) {
				hitChecks[i].setVisible(false);
			}
			hitChecks[i].setHorizontalAlignment(SwingConstants.LEFT);

			hitChecks[i].addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					int shI = (fI - shiftPanel.getInt() + 32) % 32;
					truePattern.set(shI, hitChecks[fI].isSelected() ? 1 : 0);
					if (RhythmPattern.valueOf(
							(String) patternType.getSelectedItem()) != RhythmPattern.CUSTOM) {
						patternType.setSelectedItem(RhythmPattern.CUSTOM.toString());
					}
				}

			});
			add(hitChecks[i]);
			if (i > 0 && i < 31 && ((i + 1) % 8) == 0) {
				JLabel sep = new JLabel("|");
				sep.setVisible(false);
				separators[sepCounter++] = sep;
				add(sep);
			}
		}

		patternType.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					DrumHitsPatternPanel.this.setVisible(false);
					RhythmPattern d = RhythmPattern.valueOf((String) patternType.getSelectedItem());
					if (d != RhythmPattern.CUSTOM) {
						truePattern = d.getPatternByLength(32);
					}

					for (int i = 0; i < 32; i++) {
						int shI = (i + shiftPanel.getInt()) % 32;
						hitChecks[shI].setSelected(truePattern.get(i) != 0);
					}
					DrumHitsPatternPanel.this.setVisible(true);
				}

			}

		});

		hitsPanel.getKnob().addMouseListener(new MouseAdapter() {

			@Override
			public void mouseReleased(MouseEvent e) {
				reapplyHits();

			}
		});

		doubler.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseReleased(MouseEvent e) {
				List<Integer> halfPattern = truePattern.subList(0, 16);
				Collections.rotate(halfPattern, shiftPanel.getInt());
				truePattern = MidiUtils.intersperse(0, 1, halfPattern);
				//Collections.rotate(halfPattern, -1 * shiftPanel.getInt());

				if (shiftPanel.getInt() > 0) {
					shiftPanel.setInt(0);
				}
				reapplyShift();
				if (lastHits != 24 && lastHits != 10) {
					hitsPanel.getKnob().setValue(2 * lastHits);
				}


			}
		});

		hitsPanel.getKnob().getTextValue().getDocument()
				.addDocumentListener(new DocumentListener() {

					@Override
					public void insertUpdate(DocumentEvent e) {
						reapplyHits();

					}

					@Override
					public void removeUpdate(DocumentEvent e) {
						reapplyHits();

					}

					@Override
					public void changedUpdate(DocumentEvent e) {
						reapplyHits();

					}

				});

		shiftPanel.getKnob().addMouseListener(new MouseAdapter() {

			@Override
			public void mouseReleased(MouseEvent e) {
				reapplyShift();

			}
		});

		shiftPanel.getKnob().getTextValue().getDocument()
				.addDocumentListener(new DocumentListener() {

					@Override
					public void insertUpdate(DocumentEvent e) {
						reapplyShift();

					}

					@Override
					public void removeUpdate(DocumentEvent e) {
						reapplyShift();

					}

					@Override
					public void changedUpdate(DocumentEvent e) {
						reapplyShift();

					}

				});

		chordSpanPanel.getKnob().addMouseListener(new MouseAdapter() {

			@Override
			public void mouseReleased(MouseEvent e) {
				reapplyHits();

			}
		});

		chordSpanPanel.getKnob().getTextValue().getDocument()
				.addDocumentListener(new DocumentListener() {

					@Override
					public void insertUpdate(DocumentEvent e) {
						reapplyHits();

					}

					@Override
					public void removeUpdate(DocumentEvent e) {
						reapplyHits();

					}

					@Override
					public void changedUpdate(DocumentEvent e) {
						reapplyHits();

					}

				});
	}

	public List<Integer> getTruePattern() {
		return truePattern;
	}

	public void setTruePattern(List<Integer> truePattern) {
		this.truePattern = truePattern;
		reapplyShift();
	}

	public void reapplyShift() {
		if (truePattern == null || truePattern.isEmpty()) {
			return;
		}
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				DrumHitsPatternPanel.this.setVisible(false);
				for (int i = 0; i < 32; i++) {
					int shI = (i + shiftPanel.getInt()) % 32;
					hitChecks[shI].setSelected(truePattern.get(i) != 0);
				}
				DrumHitsPatternPanel.this.setVisible(true);
			}
		});
	}

	public void reapplyHits() {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				DrumHitsPatternPanel.this.setVisible(false);
				int nowHits = hitsPanel.getInt();
				if (nowHits > 32)
					nowHits = 32;
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

				int chords = chordSpanPanel.getInt();

				if (VibeComposerGUI.isBigMonitorMode) {
					width = 32 * CheckBoxIcon.width;
					height = 1 * CheckBoxIcon.width;
					if (chords == 1) {
						if (bigModeInsetMap.containsKey(lastHits)) {
							for (int i = 0; i < lastHits; i++) {
								hitChecks[i].setMargin(bigModeInsetMap.get(lastHits));
							}
						} else {
							for (int i = 0; i < lastHits; i++) {
								hitChecks[i].setMargin(new Insets(0, 0, 0, 0));
							}
						}
					} else {
						for (int i = 0; i < lastHits; i++) {
							if (lastHits % 2 == 0) {
								hitChecks[i]
										.setMargin(bigModeDoubleChordGeneralInsetMap.get(lastHits));
							} else {
								if (i == lastHits / 2) {
									hitChecks[i].setMargin(
											bigModeDoubleChordTransitionInsetMap.get(lastHits));
								} else {
									hitChecks[i].setMargin(
											bigModeDoubleChordGeneralInsetMap.get(lastHits));
								}
							}

						}
					}

					if (lastHits == 32 && chords == 1) {
						for (JLabel lab : separators) {
							lab.setVisible(true);
						}
					} else if (lastHits == 32 && chords == 2) {
						separators[0].setVisible(true);
						separators[1].setVisible(false);
						separators[2].setVisible(true);
					} else if (lastHits == 16 && chords == 1) {
						separators[0].setVisible(true);
						separators[1].setVisible(false);
						separators[2].setVisible(false);
					} else {
						for (JLabel lab : separators) {
							lab.setVisible(false);
						}
					}

				} else {
					width = 8 * CheckBoxIcon.width;
					height = 2 * CheckBoxIcon.width;
					if (smallModeInsetMap.containsKey(lastHits)) {
						for (int i = 0; i < lastHits; i++) {
							hitChecks[i].setMargin(smallModeInsetMap.get(lastHits));
						}
					} else {
						for (int i = 0; i < lastHits; i++) {
							hitChecks[i].setMargin(new Insets(0, 0, 0, 0));
						}
					}
					for (JLabel lab : separators) {
						lab.setVisible(false);
					}
				}
				int bigModeWidthOffset = (VibeComposerGUI.isBigMonitorMode) ? 10 : 0;
				if (lastHits > 16 || (chords == 2 && VibeComposerGUI.isBigMonitorMode)) {
					DrumHitsPatternPanel.this.setPreferredSize(
							new Dimension(width + bigModeWidthOffset, height * 2));
					if (!VibeComposerGUI.isBigMonitorMode) {
						parentPanel.setMaximumSize(new Dimension(3000, 90));
					}
				} else {
					DrumHitsPatternPanel.this
							.setPreferredSize(new Dimension(width + bigModeWidthOffset, height));
					parentPanel.setMaximumSize(new Dimension(3000, 50));
				}
				DrumHitsPatternPanel.this.setVisible(true);
			}

		});

	}

}
