package org.vibehistorian.vibecomposer.Panels;

import org.vibehistorian.vibecomposer.Components.ColorCheckBox;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;
import org.vibehistorian.vibecomposer.Components.VeloRect;
import org.vibehistorian.vibecomposer.Enums.RhythmPattern;
import org.vibehistorian.vibecomposer.Helpers.CheckBoxIcon;
import org.vibehistorian.vibecomposer.MidiGenerator;
import org.vibehistorian.vibecomposer.MidiGenerator.Durations;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
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
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VisualPatternPanel extends JPanel {

	private static final long serialVersionUID = 6963518339035392918L;

	private KnobPanel hitsPanel = null;
	private ScrollComboBox<RhythmPattern> patternType = null;
	private KnobPanel shiftPanel = null;
	private KnobPanel chordSpanPanel = null;

	private JButton velocityToggler = null;

	private int lastHits = 0;

	private List<Integer> truePattern = new ArrayList<>();
	private List<Integer> trueVelocities = new ArrayList<>();
	public static final int MAX_HITS = 32;
	private ColorCheckBox[] hitChecks = new ColorCheckBox[MAX_HITS];
	private VeloRect[] hitVelocities = new VeloRect[MAX_HITS];
	private boolean showingVelocities = false;
	private int lastHighlightedHit = -1;


	private JLabel[] separators = new JLabel[3];

	private InstPanel parentPanel = null;

	public static int width = 8 * CheckBoxIcon.width;
	public static int height = 2 * CheckBoxIcon.width;

	public static final List<Integer> FULL_PATTERN = IntStream.iterate(1, e -> e).limit(MAX_HITS)
			.boxed().collect(Collectors.toList());

	public static int mouseButton = 0;

	/*public static List<Integer> sextuplets = Arrays.asList(new Integer[] { 6, 12, 24 });
	public static List<Integer> quintuplets = Arrays.asList(new Integer[] { 5 });
	public static List<Integer> triplets = Arrays.asList(new Integer[] { 3 });*/

	private boolean viewOnly = false;
	private boolean needShift = false;
	private boolean bigModeAllowed = true;

	private boolean isGhostNotes = false;

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


	public VisualPatternPanel(KnobPanel hitsKnob, ScrollComboBox<RhythmPattern> patternBox,
			KnobPanel shiftKnob, KnobPanel chordSpanKnob, InstPanel parentPanel) {
		super();
		//setBackground(new Color(50, 50, 50));
		FlowLayout layout = new FlowLayout(FlowLayout.CENTER, 0, 0);
		//layout.setVgap(0);
		//layout.setHgap(0);
		setLayout(layout);
		setPreferredSize(new Dimension(width, height));
		//setBorder(new BevelBorder(BevelBorder.LOWERED));
		this.hitsPanel = hitsKnob;
		this.patternType = patternBox;
		this.shiftPanel = shiftKnob;
		this.parentPanel = parentPanel;
		this.chordSpanPanel = chordSpanKnob;
		lastHits = hitsPanel.getInt();
		int sepCounter = 0;
		int defaultVel = (parentPanel.getVelocityMax() + parentPanel.getVelocityMin()) / 2;
		for (int i = 0; i < MAX_HITS; i++) {
			final int fI = i;
			truePattern.add(1);
			trueVelocities.add(defaultVel);
			hitChecks[i] = new ColorCheckBox();
			hitVelocities[i] = VeloRect.midi(defaultVel);
			hitVelocities[i].linkVisualParent(VisualPatternPanel.this, i);
			//hitVelocities[i].setDefaultSize(new Dimension(CheckBoxIcon.width, CheckBoxIcon.width));
			hitVelocities[i].setVisible(false);
			//hitChecks[i].setBackground(new Color(128, 128, 128));
			hitChecks[i].addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					//LG.d("True pattern size: " + truePattern.size());
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
						/*if (patternType.getVal() != RhythmPattern.CUSTOM) {
							patternType.setVal(RhythmPattern.CUSTOM);
						}*/
					}
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					mouseButton = -1;

					if (e.getButton() >= 1 && isEnabled()
							&& VibeComposerGUI.canRegenerateOnChange()) {
						Timer tmr = new Timer(100, new ActionListener() {

							@Override
							public void actionPerformed(ActionEvent e) {
								VibeComposerGUI.vibeComposerGUI.regenerate();
							}
						});
						tmr.setRepeats(false);
						tmr.start();
					}

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
		patternType.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				if (SwingUtilities.isMiddleMouseButton(evt) && evt.isShiftDown()
						&& patternType.isEnabled()) {
					if (evt.isControlDown()) {
						randomizePatternGlobal();
					} else {
						randomizePattern();
					}
					if (VibeComposerGUI.canRegenerateOnChange()) {
						VibeComposerGUI.vibeComposerGUI.regenerate();
					}
				}
			}
		});
		patternType.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent evt) {
				if (evt.getStateChange() == ItemEvent.SELECTED) {
					VisualPatternPanel.this.setVisible(false);
					RhythmPattern d = patternType.getVal();
					if (d != RhythmPattern.CUSTOM) {
						int hits = hitsKnob.getInt();
						truePattern = (d == RhythmPattern.EUCLID)
								? RhythmPattern.makeEuclideanPattern(hits,
										(int) truePattern.subList(0, hits).stream()
												.filter(e -> e > 0).count(),
										0, MAX_HITS)
								: d.getPatternByLength(MAX_HITS, 0);
						if (trueVelocities.isEmpty()) {
							int updatedVel = (parentPanel.getVelocityMax()
									+ parentPanel.getVelocityMin()) / 2;
							trueVelocities = IntStream.iterate(updatedVel, e -> e).boxed()
									.collect(Collectors.toList());
						}
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

	protected void randomizePatternGlobal() {
		InstPanel instParent = parentPanel;
		if (instParent == null) {
			randomizePattern();
			repaint();
			return;
		}
		List<InstPanel> allPanels = VibeComposerGUI.getAffectedPanels(instParent.getPartNum());
		allPanels.forEach(e -> {
			if (e.getComboPanel().patternType.isEnabled()) {
				e.getComboPanel().randomizePattern();
				e.getComboPanel().repaint();
			}
		});
	}

	protected void randomizePattern() {
		//LG.i("Randomize pattern.");
		if (patternType.isEnabled()) {
			Random rand = new Random();
			if (RhythmPattern.EUCLID.equals(patternType.getVal())) {
				long oldNum = truePattern.subList(0, lastHits).stream().filter(e -> e > 0).count();
				int newNum = rand.nextInt((lastHits / 2) + 1) + (lastHits / 4) + 1;
				if (newNum == oldNum) {
					if (newNum == lastHits) {
						newNum--;
					} else {
						newNum++;
					}
				}
				newNum = OMNI.clamp(newNum, 1, lastHits);
				truePattern = RhythmPattern.makeEuclideanPattern(lastHits, newNum, 0, MAX_HITS);
			} else {
				patternType.setValRaw(RhythmPattern.CUSTOM);
				for (int i = 0; i < MAX_HITS; i++) {
					truePattern.set(i, rand.nextInt(2));
				}
			}
			shiftPanel.setInt(0);
			reapplyShift();
		}
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
						hitsPanel.setInt(2 * lastHits);
					}


				}
			});
		}
	}

	public void linkExpander(JButton expander) {
		if (expander != null) {
			expander.addMouseListener(new MouseAdapter() {

				@Override
				public void mouseReleased(MouseEvent e) {
					if (e.isControlDown()) {
						expand2xGlobal();
					} else {
						expand2x();
					}
				}
			});
		}
	}

	public void expand2xGlobal() {
		InstPanel instParent = parentPanel;
		if (instParent == null) {
			expand2x();
			repaint();
			return;
		}
		List<InstPanel> allPanels = VibeComposerGUI.getAffectedPanels(instParent.getPartNum());
		allPanels.forEach(e -> {
			if (e.getComboPanel().hitsPanel.isEnabled()) {
				e.getComboPanel().expand2x();
				e.getComboPanel().repaint();
			}
		});
	}

	public void expand2x() {
		List<Integer> tickThresholds = hitsPanel.getKnob().getTickThresholds();
		if (!hitsPanel.isEnabled() || !chordSpanPanel.isEnabled() || chordSpanPanel.getInt() > 2
				|| !(tickThresholds.contains(lastHits * 2))) {
			return;
		}
		List<Integer> firstPattern = truePattern.subList(0, lastHits);
		Collections.rotate(firstPattern, shiftPanel.getInt());
		for (int i = 0; i < lastHits; i++) {
			firstPattern.add(0);
		}
		truePattern = firstPattern;
		while (truePattern.size() < MAX_HITS) {
			truePattern.addAll(firstPattern);
		}
		truePattern = truePattern.subList(0, MAX_HITS);
		if (shiftPanel.getInt() > 0) {
			shiftPanel.setInt(0);
		}
		patternType.setVal(RhythmPattern.CUSTOM);
		hitsPanel.setInt(lastHits * 2);
		chordSpanPanel.setInt(chordSpanPanel.getInt() * 2);
		reapplyShift();
	}

	public void linkVelocityToggle(JButton veloToggler) {
		if (veloToggler != null) {
			veloToggler.addMouseListener(new MouseAdapter() {

				@Override
				public void mouseReleased(MouseEvent evt) {
					if (SwingUtilities.isRightMouseButton(evt)) {
						if (showingVelocities) {
							int updatedVel = (parentPanel.getVelocityMax()
									+ parentPanel.getVelocityMin()) / 2;
							List<Integer> defaultVelos = IntStream.iterate(updatedVel, e -> e)
									.limit(MAX_HITS).boxed().collect(Collectors.toList());
							setVelocities(defaultVelos);
							repaint();
						} else if (parentPanel != null) {
							parentPanel.applyPauseChance(new Random(
									parentPanel.getPatternSeed() != 0 ? parentPanel.getPatternSeed()
											: VibeComposerGUI.lastRandomSeed));
						}

					} else if (SwingUtilities.isMiddleMouseButton(evt) && parentPanel != null) {
						Random veloRand = new Random();
						int minVel = parentPanel.getVelocityMin();
						int maxVel = parentPanel.getVelocityMax();
						List<Integer> randomVelos = IntStream
								.iterate(minVel + veloRand.nextInt(maxVel - minVel + 1),
										e -> minVel + veloRand.nextInt(maxVel - minVel + 1))
								.limit(MAX_HITS).boxed().collect(Collectors.toList());
						setVelocities(randomVelos);
						repaint();
					} else {
						toggleVelocityShow();
					}
				}
			});
			velocityToggler = veloToggler;
		}
	}

	public void toggleVelocityShow() {
		toggleVelocityShow(null);
	}

	public void toggleVelocityShow(Integer mouseButton) {
		showingVelocities = !showingVelocities;
		velocityToggler.setText(showingVelocities ? "H" : "V");
		VisualPatternPanel.this.setVisible(false);
		setGhostNotes(false);
		if (showingVelocities) {
			for (int i = 0; i < MAX_HITS; i++) {
				hitVelocities[i].setVisible(hitChecks[i].isVisible());
				hitVelocities[i].setEnabled(hitChecks[i].isSelected());
				hitChecks[i].setVisible(false);
			}
		} else {
			for (int i = 0; i < MAX_HITS; i++) {
				hitChecks[i].setVisible(hitVelocities[i].isVisible());
				if (parentPanel.getPattern() == RhythmPattern.CUSTOM) {
					hitChecks[i].setSelected(hitVelocities[i].isEnabled());
				}
				hitVelocities[i].setVisible(false);
			}
		}
		reapplyHitsRaw();
		reapplyShiftRaw(false);

		VisualPatternPanel.this.setVisible(true);
	}

	public void checkPattern(int fI) {
		checkPattern(fI, null);
	}

	public int getPattern(int fI) {
		int shI = (fI - shiftPanel.getInt() + MAX_HITS) % MAX_HITS;
		return truePattern.get(shI);
	}

	public int getShifted(int fI) {
		return (fI + shiftPanel.getInt() + MAX_HITS) % MAX_HITS;
	}

	public void checkPattern(int fI, Integer directSetting) {
		int shift = shiftPanel.getInt();
		int initialVal = fI - shift;
		int shI = (initialVal + MAX_HITS) % MAX_HITS;
		if (directSetting != null) {
			hitChecks[fI].setSelected(directSetting > 0);
			hitVelocities[fI].setEnabled(directSetting > 0);
		}
		int applied = (directSetting != null) ? directSetting
				: (hitChecks[fI].isSelected() ? 1 : 0);
		truePattern.set(shI, applied);
		boolean reapplyNeeded = false;

		while ((initialVal += lastHits) < MAX_HITS) {
			if (initialVal >= 0 && (shift + initialVal < MAX_HITS)) {
				truePattern.set(initialVal, applied);
				reapplyNeeded = true;
			}
		}

		if (patternType.getVal() != RhythmPattern.CUSTOM) {
			patternType.setVal(RhythmPattern.CUSTOM);
		}
		if (reapplyNeeded) {
			reapplyShift();
		}
	}

	public List<Integer> getTruePattern() {
		if (isGhostNotes && showingVelocities) {
			return new ArrayList<>(FULL_PATTERN);
		}
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
				int shI = (i + shiftPanel.getInt()) % MAX_HITS;
				hitVelocities[i].setValue(velocities.get(i));
				trueVelocities.set(i, velocities.get(shI));
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

		SwingUtilities.invokeLater(() -> {
			VisualPatternPanel.this.setVisible(false);
			reapplyShiftRaw(false);
			VisualPatternPanel.this.setVisible(true);
		});
	}

	public void reapplyShiftRaw(boolean velocityGhost) {
		if (truePattern == null || truePattern.isEmpty()) {
			return;
		}

		int shift = shiftPanel.getInt();
		for (int i = 0; i < MAX_HITS; i++) {
			int shI = (i + shift) % MAX_HITS;
			hitChecks[shI].setSelected(truePattern.get(i) != 0);
		}
		for (int i = 0; i < MAX_HITS; i++) {
			int shI = (i + shift) % MAX_HITS;
			hitVelocities[shI].setValueRaw(trueVelocities.get(i));
			if (!velocityGhost) {
				hitVelocities[shI].setEnabled(truePattern.get(i) != 0);
			}
		}
	}

	public void reapplyHits() {
		SwingUtilities.invokeLater(() -> {
			VisualPatternPanel.this.setVisible(false);
			reapplyHitsRaw();
			VisualPatternPanel.this.setVisible(true);

		});

	}

	public void reapplyHitsRaw() {
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
						hitChecks[i].setMargin(bigModeDoubleChordGeneralInsetMap.get(lastHits));
						hitVelocities[i].setMargin(bigModeDoubleChordGeneralInsetMap.get(lastHits));
					} else {
						if (i == lastHits / 2) {
							hitChecks[i]
									.setMargin(bigModeDoubleChordTransitionInsetMap.get(lastHits));
							hitVelocities[i]
									.setMargin(bigModeDoubleChordTransitionInsetMap.get(lastHits));
						} else {
							hitChecks[i].setMargin(bigModeDoubleChordGeneralInsetMap.get(lastHits));
							hitVelocities[i]
									.setMargin(bigModeDoubleChordGeneralInsetMap.get(lastHits));
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

		VisualPatternPanel.this.setPreferredSize(new Dimension(width + bigModeWidthOffset, height));

		parentPanel.setMaximumSize(new Dimension(3000, height > 50 ? height + 6 : 50));
		repaint();
	}

	public boolean isViewOnly() {
		return viewOnly;
	}

	public void setViewOnly(boolean viewOnly) {
		this.viewOnly = viewOnly;
	}

	public static final double[] SPAN_4_ZONES = { 0.0, 0.25, 0.5, 0.75 };
	public static final double[] SPAN_2_ZONES = { 0.0, 0.5 };

	public void notifyPatternHighlight(double currentPatternTime, int chordNumInMeasure,
			List<Double> prevChordDurations, boolean turnOff, boolean ignoreFill, int totalChords,
			double currentChordDuration) {

		if (parentPanel == null) {
			return;
		}
		if (turnOff) {
			if (lastHighlightedHit >= 0) {
				hitVelocities[lastHighlightedHit].setHighlighted(false);
				hitChecks[lastHighlightedHit].setHighlighted(false);
				lastHighlightedHit = -1;
			}
			return;
		}


		/*LG.i(parentPanel.getPanelOrder() + "#");
		LG.i("Quarter notes: " + currentPatternTime);
		LG.i(StringUtils.join(prevChordDurations, ", "));
		LG.i("Chord num: " + chordNumInMeasure);*/
		List<Integer> fillPattern = parentPanel.getChordSpanFill().getPatternByLength(totalChords,
				parentPanel.getFillFlip());

		if (patternType.getVal() == RhythmPattern.MELODY1
				|| (!ignoreFill && fillPattern.get(chordNumInMeasure) < 1)) {
			if (lastHighlightedHit >= 0) {
				hitVelocities[lastHighlightedHit].setHighlighted(false);
				hitChecks[lastHighlightedHit].setHighlighted(false);
				lastHighlightedHit = -1;
			}
			return;
		}

		// which chord part is current chord?
		// what does actual pattern look like after pattern repeats?
		// what position in the full pattern? (modulo full pattern).

		// chordspan = 1 --> subtract sum of all beatDurationsInMeasure
		//        --> remaining duration divided by whole note == percentage

		// chordSpan = 2 --> subtract pairs/triples/quadruples
		int chordSpan = chordSpanPanel.getInt();
		int patternRepeat = parentPanel.getPatternRepeat();

		int indexOfSubtractableDurations = chordSpan * ((chordNumInMeasure) / chordSpan);
		for (int i = 0; i < indexOfSubtractableDurations; i++) {
			currentPatternTime -= prevChordDurations.get(i);
		}

		double patternTotalDuration = Durations.WHOLE_NOTE * chordSpan;
		double percentage = (currentPatternTime / patternTotalDuration);
		/*LG.i("Quarter notes: " + currentPatternTime);
		LG.i("Percentage raw: " + percentage);
		LG.i("Last chord duration: " + currentChordDuration);*/

		double patternCoverage = currentChordDuration / Durations.WHOLE_NOTE;
		/*if (chordSpan > 1 && patternRepeat != 3 && patternCoverage > 1 + MidiGenerator.DBL_ERR) {
			while (chordSpan % 2 == 0 && patternRepeat % 2 == 0) {
				chordSpan /= 2;
				patternRepeat /= 2;
			}
		}*/
		if (chordSpan > 1 && patternRepeat > 1 && patternCoverage > 1 + MidiGenerator.DBL_ERR) {
			int chordSpanPart = chordNumInMeasure % chordSpan;
			double normalizedPercentage = percentage / patternCoverage;
			// percentage within a chord part
			normalizedPercentage -= (chordSpan == 4) ? SPAN_4_ZONES[chordSpanPart]
					: SPAN_2_ZONES[chordSpanPart];
			//LG.i("Percentage norm (current chord): " + normalizedPercentage);

			double repeatThreshold = (chordSpan == 4) ? 0.25 : 0.5;
			double newPercentage = normalizedPercentage % patternCoverage;
			//newPercentage %= 1.0;
			newPercentage *= patternCoverage;
			newPercentage %= repeatThreshold;
			newPercentage += (chordSpan == 4) ? SPAN_4_ZONES[chordSpanPart]
					: SPAN_2_ZONES[chordSpanPart];
			percentage = newPercentage;
			//double zoneCalc = wholeNotesInMeasure / chordSpan;
			//int leftover = (int) Math.floor((percentage % wholeNotesInMeasure) / zoneCalc);
			//percentage = (percentage - (leftover * zoneCalc))
			//		+ ((chordSpan == 4) ? SPAN_4_ZONES[leftover] : SPAN_2_ZONES[leftover]);
		}

		percentage *= patternRepeat;
		// 10 for modulo calc
		percentage = (10.0 + percentage) % 1.0;
		//LG.i("Percentage: " + percentage);
		int highlightedHit = (int) Math.floor(percentage * lastHits);
		if (highlightedHit < 0) {
			highlightedHit = 0;
		}
		if (highlightedHit == lastHighlightedHit && lastHits > 1) {
			return;
		}
		if (showingVelocities) {
			hitVelocities[highlightedHit].setHighlighted(true);
		} else {
			hitChecks[highlightedHit].setHighlighted(true);
		}

		if (lastHighlightedHit >= 0 && lastHits > 1 && lastHighlightedHit != highlightedHit) {
			hitVelocities[lastHighlightedHit].setHighlighted(false);
			hitChecks[lastHighlightedHit].setHighlighted(false);
		}
		lastHighlightedHit = highlightedHit;
	}

	public List<Integer> getTrueVelocities() {
		return trueVelocities;
	}

	public void linkGhostNoteSwitch(JCheckBox isVelocityPattern) {
		isVelocityPattern.addMouseListener(new MouseAdapter() {

			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isMiddleMouseButton(e)) {
					if (!showingVelocities) {
						toggleVelocityShow();
					}
					if (isVelocityPattern.isSelected()) {
						setGhostNotes(true);
						Random randr = new Random();
						patternType.setVal(RhythmPattern.CUSTOM);
						for (int i = 0; i < MAX_HITS; i++) {
							int shI = (i + shiftPanel.getInt()) % MAX_HITS;
							if (!hitVelocities[shI].isEnabled()) {
								hitVelocities[shI].setEnabled(true);
								int max = parentPanel.getVelocityMax();
								int min = parentPanel.getVelocityMin();
								int val = (randr.nextInt(max - min + 1) + min) / 2;
								hitVelocities[shI].setValueRaw(val);
								trueVelocities.set(i, val);
							}
						}
					} else {
						setGhostNotes(false);
						for (int i = 0; i < MAX_HITS; i++) {
							if (!hitChecks[i].isSelected()) {
								//hitVelocities[shI].setValue(20);
								hitVelocities[i].setEnabled(false);
							}
						}
					}
					reapplyHitsRaw();
					reapplyShiftRaw(true);

				}
			}
		});
	}

	public boolean isGhostNotes() {
		return isGhostNotes;
	}

	public void setGhostNotes(boolean isGhostNotes) {
		this.isGhostNotes = isGhostNotes;
	}

}
