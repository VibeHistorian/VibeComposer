package org.vibehistorian.vibecomposer.Popups;

import org.vibehistorian.vibecomposer.PlaybackState;

import org.vibehistorian.vibecomposer.Helpers.CheckBoxIcon;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.Panels.DrumPanel;
import org.vibehistorian.vibecomposer.Panels.VisualPatternPanel;
import org.vibehistorian.vibecomposer.SwingUtils;
import org.vibehistorian.vibecomposer.VibeComposerGUI;

import javax.sound.midi.Sequencer;
import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DrumLoopPopup {
	final JFrame frame = new JFrame();
	JPanel hitsPanel = new JPanel();
	JScrollPane scroll;

	Thread cycle;
	JSlider slider;
	JLabel currentTime;
	JLabel totalTime;
	JLabel sectionText;
	boolean isKeySeeking = false;
	boolean isDragging = false;
	long pauseMs;
	public static Map<DrumPanel, VisualPatternPanel> dhpps = new HashMap<>();
	private final List<DrumPanel> drumPanels;

	@SuppressWarnings("unchecked")
	public DrumLoopPopup() {
		this((List<DrumPanel>) (List<?>) VibeComposerGUI.getInstList(4));
	}

	public DrumLoopPopup(List<DrumPanel> drumPanels) {
		this.drumPanels = drumPanels;
		dhpps.clear();
		hitsPanel.setLayout(new BoxLayout(hitsPanel, BoxLayout.Y_AXIS));
		initSliderPanel();

		for (int i = drumPanels.size() - 1; i >= 0; i--) {
			DrumPanel dp = drumPanels.get(i);
			JPanel textHitsPanel = new JPanel();
			JTextField drumNum = new JTextField(dp.getInstrument() + "", 8);
			drumNum.setFocusable(false);
			drumNum.setEditable(false);
			textHitsPanel.add(drumNum);
			VisualPatternPanel dhpp = dp.makeVisualPatternPanel();
			dhpp.setTruePattern(dp.getComboPanel().getTruePattern());
			dhpp.setViewOnly(true);

			dhpp.reapplyShift();
			dhpp.reapplyHits();
			dhpps.put(dp, dhpp);
			textHitsPanel.add(dhpp);
			hitsPanel.add(textHitsPanel);

		}

		scroll = new JScrollPane(hitsPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

		scroll.getVerticalScrollBar().setUnitIncrement(16);
		frame.setLocation(SwingUtils.getMouseLocation());
		frame.add(scroll);
		frame.pack();
		frame.setVisible(true);

		LG.d("Opened Drum Loop popup!");
	}

	public void initPanels() {
		dhpps.clear();
		hitsPanel.setLayout(new BoxLayout(hitsPanel, BoxLayout.Y_AXIS));


		for (int i = 0; i < drumPanels.size(); i++) {
			DrumPanel dp = drumPanels.get(i);
			JPanel textHitsPanel = new JPanel();
			JTextField drumNum = new JTextField(dp.getInstrument() + "", 8);
			drumNum.setFocusable(false);
			drumNum.setEditable(false);
			textHitsPanel.add(drumNum);
			VisualPatternPanel dhpp = dp.makeVisualPatternPanel();
			dhpp.setTruePattern(dp.getComboPanel().getTruePattern());
			dhpp.setViewOnly(true);

			dhpp.reapplyShift();
			dhpp.reapplyHits();
			dhpps.put(dp, dhpp);
			textHitsPanel.add(dhpp);
			hitsPanel.add(textHitsPanel);

		}
	}

	public JFrame getFrame() {
		return frame;
	}

	private void startOmnipresentThread() {
		// init thread
		Sequencer sequencer = PlaybackState.sequencer;
		Thread cycle = new Thread() {

			public void run() {


				while (true) {
					try {
						if (sequencer != null && sequencer.isRunning()) {
							slider.setValue(
									PlaybackState.slider.getUpperValue() % slider.getMaximum());
						}

						try {
							sleep(25);
						} catch (InterruptedException e) {

						}
					} catch (Exception e) {
						LG.d("Exception in SEQUENCE SLIDER:");
						LG.e(e);
						try {
							sleep(200);
						} catch (InterruptedException e2) {

						}
					}
				}

			}
		};
		cycle.start();
	}

	private void initSliderPanel() {

		JPanel sliderPanel = new JPanel();
		JLabel emptyLabel = new JLabel("");
		emptyLabel.setPreferredSize(new Dimension(115, 20));
		sliderPanel.add(emptyLabel);
		sliderPanel.setOpaque(false);
		sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.X_AXIS));

		sliderPanel.setPreferredSize(
				new Dimension(VisualPatternPanel.MAX_HITS * CheckBoxIcon.width + 130, 20));


		slider = new JSlider();
		slider.setMaximum(PlaybackState.slider.getMaximum() / 4);
		slider.setToolTipText("Test");
		sliderPanel.add(slider);
		hitsPanel.add(sliderPanel);

		startOmnipresentThread();

	}
}
