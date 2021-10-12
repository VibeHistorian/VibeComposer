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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
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
import org.vibehistorian.vibecomposer.Enums.ChordSpanFill;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Helpers.CheckBoxIcon;
import org.vibehistorian.vibecomposer.Helpers.ColorCheckBox;
import org.vibehistorian.vibecomposer.Helpers.ScrollComboBox;
import org.vibehistorian.vibecomposer.Helpers.VeloRect;

public class VisualPatternPanel extends JPanel {

	private static final long serialVersionUID = 6963518339035392918L;

	private KnobPanel hitsPanel = null;
	private ScrollComboBox<RhythmPattern> patternType = null;
	private KnobPanel shiftPanel = null;
	private KnobPanel chordSpanPanel = null;

	private JButton velocityToggler = null;

	private int lastHits = 0;

	private List<Integer> truePattern = new ArrayList<>();
	public static final int MAX_HITS = 32;
	private ColorCheckBox[] hitChecks = new ColorCheckBox[MAX_HITS];
	private VeloRect[] hitVelocities = new VeloRect[MAX_HITS];
	private boolean showingVelocities = false;
	private int lastHighlightedHit = -1;


	private JLabel[] separators = new JLabel[3];

	private InstPanel parentPanel = null;

	public static int width = 8 * CheckBoxIcon.width;
	public static int height = 2 * CheckBoxIcon.width;

	public static int mouseButton = 0;

	/*public static List<Integer> sextuplets = Arrays.asList(new Integer[] { 6, 12, 24 });
	public static List<Integer> quintuplets = Arrays.asList(new Integer[] { 5 });
	public static List<Integer> triplets = Arrays.asList(new Integer[] { 3 });*/

	private boolean viewOnly = false;
	private boolean needShift = false;
	private boolean bigModeAllowed = true;

	public void setBigModeAllowed(boolean bigModeAllowed) {
		this.bigModeAllowed = bigModeAllowed;
	}

	public static Map<Integer, Insets> smallModeInsetMap = new HashMap<>();
	static {
		smallModeInsetMap.put(1, new Insets(0, 0, 0, CheckBoxIcon.width * 7));
		smallModeInsetMap.put(2, new Insets(0, 0, 0, CheckBoxIcon.width * 6 / 2));
		smallModeInsetMap.put(3, new Insets(0, 0, 0, CheckBoxIcon.width * 5 / 3));
		smallModeInsetMap.put(4, new Insets(0, 0, 0, CheckBoxIcon.width * 4 / 4));
		smallModeInsetMap.put(5, new Insets(0, 0, 0, CheckBoxIcon.width * 3 / 5));
		smallModeInsetMap.put(6, new Insets(0, 0, 0, CheckBoxIcon.width * 2 / 6));
		smallModeInsetMap.put(10, new Insets(0, 0, 0, CheckBoxIcon.width * 3 / 5));
		smallModeInsetMap.put(12, new Insets(0, 0, 0, CheckBoxIcon.width * 2 / 6));
		//smallModeInsetMap.put(18, new Insets(0, 0, 0, CheckBoxIcon.width * 2 / 6));
		smallModeInsetMap.put(24, new Insets(0, 0, 0, CheckBoxIcon.width * 2 / 6));
	}

	public static Map<Integer, Insets> bigModeInsetMap = new HashMap<>();
	static {

		for (int i = 2; i < MAX_HITS; i++) {
			bigModeInsetMap.put(i, new Insets(0, 0, 0, CheckBoxIcon.width * (MAX_HITS - i) / i));
		}
	}

	public static Map<Integer, Insets> bigModeDoubleChordGeneralInsetMap = new HashMap<>();
	static {

		for (int i = 2; i <= MAX_HITS; i++) {
			bigModeDoubleChordGeneralInsetMap.put(i,
					new Insets(0, 0, 0, 2 * CheckBoxIcon.width * (MAX_HITS - i / 2) / i));
		}
	}

	public static Map<Integer, Insets> bigModeDoubleChordTransitionInsetMap = new HashMap<>();
	static {

		for (int i = 2; i <= MAX_HITS; i++) {
			bigModeDoubleChordTransitionInsetMap.put(i,
					new Insets(0, CheckBoxIcon.width * (MAX_HITS - i / 2) / i, 0,
							CheckBoxIcon.width * (MAX_HITS - i / 2) / i));
		}
	}


	public VisualPatternPanel(KnobPanel hitsPanel, ScrollComboBox<RhythmPattern> patternType,
			KnobPanel shiftPanel, KnobPanel chordSpanPanel,
			ScrollComboBox<ChordSpanFill> chordSpanFill, InstPanel parentPanel) {
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
		lastHits = hitsPanel.getInt();
		int sepCounter = 0;
		for (int i = 0; i < MAX_HITS; i++) {
			final int fI = i;
			truePattern.add(0);
			hitChecks[i] = new ColorCheckBox();
			hitVelocities[i] = new VeloRect(0, 127, 63);
			//hitVelocities[i].setDefaultSize(new Dimension(CheckBoxIcon.width, CheckBoxIcon.width));
			hitVelocities[i].setVisible(false);
			//hitChecks[i].setBackground(new Color(128, 128, 128));
			hitChecks[i].addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					//System.out.println("True pattern size: " + truePattern.size());
					boolean change = false;
					if (mouseButton == 2) {
						hitChecks[fI].setSelected(true);
						change = true;
					} else if (mouseButton == 3) {
						hitChecks[fI].setSelected(false);
						change = true;
					}
					if (change) {
						needShift = true;
						checkPattern(fI);
					}

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
						if (patternType.getVal() != RhythmPattern.CUSTOM) {
							patternType.setVal(RhythmPattern.CUSTOM);
						}
					}
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					mouseButton = -1;
					/*for (DrumPanel dp : VibeComposerGUI.drumPanels) {
						if (dp.getComboPanel().needShift) {
							dp.getComboPanel().reapplyShift();
							dp.getComboPanel().needShift = false;
							DrumLoopPopup.dhpps.get(dp).reapplyShift();
						}
					}*/
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
					checkPattern(fI);
				}

			});
			add(hitChecks[i]);
			add(hitVelocities[i]);
			/*if (i > 0 && i < 31 && ((i + 1) % 8) == 0) {
				JLabel sep = new JLabel("|");
				sep.setVisible(false);
				separators[sepCounter++] = sep;
				add(sep);
			}*/
		}

		patternType.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					VisualPatternPanel.this.setVisible(false);
					RhythmPattern d = patternType.getVal();
					if (d != RhythmPattern.CUSTOM) {
						truePattern = d.getPatternByLength(MAX_HITS, 0);
					}

					for (int i = 0; i < MAX_HITS; i++) {
						int shI = (i + shiftPanel.getInt()) % MAX_HITS;
						if (showingVelocities) {
							hitVelocities[shI].setEnabled(truePattern.get(i) != 0);
						} else {
							hitChecks[shI].setSelected(truePattern.get(i) != 0);
						}

					}
					VisualPatternPanel.this.setVisible(true);
				}

			}

		});

		hitsPanel.getKnob().addMouseListener(new MouseAdapter() {

			@Override
			public void mouseReleased(MouseEvent e) {
				reapplyHits();

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

	public void linkDoubler(JButton doubler) {
		if (doubler != null) {
			doubler.addMouseListener(new MouseAdapter() {

				@Override
				public void mouseReleased(MouseEvent e) {
					List<Integer> halfPattern = truePattern.subList(0, 16);
					Collections.rotate(halfPattern, shiftPanel.getInt());
					truePattern = MidiUtils.intersperse(0, 1, halfPattern);
					//Collections.rotate(halfPattern, -1 * shiftPanel.getInt());
					patternType.setVal(RhythmPattern.CUSTOM);
					if (shiftPanel.getInt() > 0) {
						shiftPanel.setInt(0);
					}
					reapplyShift();
					if (lastHits != 24 && lastHits != 10) {
						hitsPanel.getKnob().setValue(2 * lastHits);
					}


				}
			});
		}
	}

	public void linkVelocityToggle(JButton veloToggler) {
		if (veloToggler != null) {
			veloToggler.addMouseListener(new MouseAdapter() {

				@Override
				public void mouseReleased(MouseEvent e) {
					toggleVelocityShow();
				}
			});
			velocityToggler = veloToggler;
		}
	}

	public void toggleVelocityShow() {
		showingVelocities = !showingVelocities;
		velocityToggler.setText(showingVelocities ? "H" : "V");
		VisualPatternPanel.this.setVisible(false);
		if (showingVelocities) {
			for (int i = 0; i < MAX_HITS; i++) {
				hitVelocities[i].setVisible(hitChecks[i].isVisible());
				hitVelocities[i].setEnabled(hitChecks[i].isSelected());
				hitChecks[i].setVisible(false);
			}
		} else {
			for (int i = 0; i < MAX_HITS; i++) {
				hitChecks[i].setVisible(hitVelocities[i].isVisible());
				hitChecks[i].setSelected(hitVelocities[i].isEnabled());
				hitVelocities[i].setVisible(false);
			}
		}
		reapplyHits();
		VisualPatternPanel.this.setVisible(true);
	}

	public void checkPattern(int fI) {
		int shI = (fI - shiftPanel.getInt() + MAX_HITS) % MAX_HITS;
		int applied = hitChecks[fI].isSelected() ? 1 : 0;
		truePattern.set(shI, applied);
		boolean reapplyNeeded = false;
		while ((shI += lastHits) < MAX_HITS) {
			truePattern.set(shI, applied);
			reapplyNeeded = true;
		}
		if (patternType.getVal() != RhythmPattern.CUSTOM) {
			patternType.setVal(RhythmPattern.CUSTOM);
		}
		if (reapplyNeeded) {
			reapplyShift();
		}
	}

	public List<Integer> getTruePattern() {
		return truePattern;
	}

	public void setTruePattern(List<Integer> truePattern) {
		this.truePattern = truePattern;
		reapplyShift();
		reapplyHits();
	}

	public List<Integer> getVelocities() {
		if (!showingVelocities) {
			return null;
		}
		List<Integer> velocities = new ArrayList<>();
		for (int i = 0; i < MAX_HITS; i++) {
			velocities.add(hitVelocities[i].getValue());
		}
		return velocities;
	}

	public void setVelocities(List<Integer> velocities) {
		if (velocities != null) {
			for (int i = 0; i < velocities.size() && i < MAX_HITS; i++) {
				hitVelocities[i].setValue(velocities.get(i));
			}
			if (!showingVelocities) {
				toggleVelocityShow();
			}
		} else {
			if (showingVelocities) {
				toggleVelocityShow();
			}
		}
	}

	public void reapplyShift() {
		if (truePattern == null || truePattern.isEmpty()) {
			return;
		}
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				VisualPatternPanel.this.setVisible(false);
				for (int i = 0; i < MAX_HITS; i++) {
					int shI = (i + shiftPanel.getInt()) % MAX_HITS;
					if (showingVelocities) {
						hitVelocities[shI].setEnabled(truePattern.get(i) != 0);
					} else {
						hitChecks[shI].setSelected(truePattern.get(i) != 0);
					}

				}
				VisualPatternPanel.this.setVisible(true);
			}
		});
	}

	public void reapplyHits() {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				VisualPatternPanel.this.setVisible(false);
				boolean showBIG = (VibeComposerGUI.isBigMonitorMode || viewOnly) && bigModeAllowed;
				int nowHits = hitsPanel.getInt();
				if (nowHits > MAX_HITS)
					nowHits = MAX_HITS;
				if (nowHits > lastHits) {
					for (int i = 0; i < nowHits; i++) {
						hitChecks[i].setVisible(!showingVelocities);
						hitVelocities[i].setVisible(showingVelocities);
					}

				} else if (nowHits < lastHits) {
					for (int i = nowHits; i < lastHits; i++) {
						hitChecks[i].setVisible(false);
						hitVelocities[i].setVisible(false);
					}
				}
				lastHits = nowHits;

				int chords = chordSpanPanel.getInt();

				if (showBIG) {
					width = MAX_HITS * CheckBoxIcon.width;
					height = 1 * CheckBoxIcon.width;
					if (chords == 1) {
						if (bigModeInsetMap.containsKey(lastHits)) {
							for (int i = 0; i < lastHits; i++) {
								hitChecks[i].setMargin(bigModeInsetMap.get(lastHits));
								hitVelocities[i].setMargin(bigModeInsetMap.get(lastHits));
							}
						} else {
							for (int i = 0; i < lastHits; i++) {
								hitChecks[i].setMargin(new Insets(0, 0, 0, 0));
								hitVelocities[i].setMargin(new Insets(0, 0, 0, 0));
							}
						}
					} else {
						for (int i = 0; i < lastHits; i++) {
							if (lastHits % 2 == 0) {
								hitChecks[i]
										.setMargin(bigModeDoubleChordGeneralInsetMap.get(lastHits));
								hitVelocities[i]
										.setMargin(bigModeDoubleChordGeneralInsetMap.get(lastHits));
							} else {
								if (i == lastHits / 2) {
									hitChecks[i].setMargin(
											bigModeDoubleChordTransitionInsetMap.get(lastHits));
									hitVelocities[i].setMargin(
											bigModeDoubleChordTransitionInsetMap.get(lastHits));
								} else {
									hitChecks[i].setMargin(
											bigModeDoubleChordGeneralInsetMap.get(lastHits));
									hitVelocities[i].setMargin(
											bigModeDoubleChordGeneralInsetMap.get(lastHits));
								}
							}

						}
					}
					/*if (!viewOnly) {
						if (lastHits == MAX_HITS && chords == 1) {
							for (JLabel lab : separators) {
								lab.setVisible(true);
							}
						} else if (lastHits == MAX_HITS && chords > 1) {
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
						for (JLabel lab : separators) {
							lab.setVisible(false);
						}
					}*/

				} else {
					width = 8 * CheckBoxIcon.width;
					height = 2 * CheckBoxIcon.width;
					if (smallModeInsetMap.containsKey(lastHits)) {
						for (int i = 0; i < lastHits; i++) {
							hitChecks[i].setMargin(smallModeInsetMap.get(lastHits));
							hitVelocities[i].setMargin(smallModeInsetMap.get(lastHits));
						}
					} else {
						for (int i = 0; i < lastHits; i++) {
							hitChecks[i].setMargin(new Insets(0, 0, 0, 0));
							hitVelocities[i].setMargin(new Insets(0, 0, 0, 0));
						}
					}
					/*for (JLabel lab : separators) {
						lab.setVisible(false);
					}*/
				}
				int bigModeWidthOffset = (showBIG) ? 10 : 0;
				boolean bigModeTwoRows = (chords > 1 && showBIG);
				if (lastHits > 16 && !showBIG && showingVelocities) {
					height *= 4;
				} else if (bigModeTwoRows || (lastHits > 16 && !showingVelocities)
						|| ((lastHits > 8 || showBIG) && showingVelocities)) {
					height *= 2;
					if (showingVelocities && bigModeTwoRows) {
						height *= 2;
					}
				}

				VisualPatternPanel.this
						.setPreferredSize(new Dimension(width + bigModeWidthOffset, height));

				parentPanel.setMaximumSize(new Dimension(3000, height > 50 ? height + 20 : 50));
				VisualPatternPanel.this.setVisible(true);
				repaint();
			}

		});

	}

	public boolean isViewOnly() {
		return viewOnly;
	}

	public void setViewOnly(boolean viewOnly) {
		this.viewOnly = viewOnly;
	}

	public void notifyPatternHighlight(double percentage, int chordNum) {
		if (parentPanel == null) {
			return;
		}
		List<Integer> fillPattern = parentPanel.getChordSpanFill().getPatternByLength(chordNum + 1,
				parentPanel.getFillFlip());

		if (patternType.getVal() == RhythmPattern.MELODY1 || fillPattern.get(chordNum) < 1) {
			if (lastHighlightedHit >= 0) {
				hitVelocities[lastHighlightedHit].setHighlighted(false);
				hitChecks[lastHighlightedHit].setHighlighted(false);
			}
			return;
		}

		if (parentPanel.getPatternRepeat() > 1) {
			percentage = (percentage > 0.499) ? (percentage * 2 - 1) : percentage * 2;
		}

		int realChordNum = chordNum % chordSpanPanel.getInt();
		int highlightedHit = (int) ((realChordNum + percentage) * lastHits
				/ chordSpanPanel.getInt());
		if (highlightedHit == lastHighlightedHit && lastHits > 1) {
			return;
		}
		if (showingVelocities) {
			hitVelocities[highlightedHit].setHighlighted(true);
			if (lastHighlightedHit >= 0 && lastHits > 1 && lastHighlightedHit != highlightedHit) {
				hitVelocities[lastHighlightedHit].setHighlighted(false);
			}
		} else {
			hitChecks[highlightedHit].setHighlighted(true);
			if (lastHighlightedHit >= 0 && lastHits > 1 && lastHighlightedHit != highlightedHit) {
				hitChecks[lastHighlightedHit].setHighlighted(false);
			}
		}
		lastHighlightedHit = highlightedHit;
	}

}
