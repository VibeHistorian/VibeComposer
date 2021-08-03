package org.vibehistorian.vibecomposer.Popups;

import java.awt.Dimension;
import java.awt.MouseInfo;
import java.util.HashMap;
import java.util.Map;

import javax.sound.midi.Sequencer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;

import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.CheckBoxIcon;
import org.vibehistorian.vibecomposer.Panels.VisualPatternPanel;
import org.vibehistorian.vibecomposer.Panels.DrumPanel;

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

	public DrumLoopPopup() {
		dhpps.clear();
		hitsPanel.setLayout(new BoxLayout(hitsPanel, BoxLayout.Y_AXIS));
		initSliderPanel();

		for (int i = VibeComposerGUI.drumPanels.size() - 1; i >= 0; i--) {
			DrumPanel dp = VibeComposerGUI.drumPanels.get(i);
			JPanel textHitsPanel = new JPanel();
			JTextField drumNum = new JTextField(dp.getInstrument() + "", 8);
			drumNum.setFocusable(false);
			drumNum.setEditable(false);
			textHitsPanel.add(drumNum);
			VisualPatternPanel dhpp = dp.makeVisualPatternPanel(new JButton("Dd"));
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
		frame.setLocation(MouseInfo.getPointerInfo().getLocation());
		frame.add(scroll);
		frame.pack();
		frame.setVisible(true);

		System.out.println("Opened Drum Loop popup!");
	}

	public void initPanels() {
		dhpps.clear();
		hitsPanel.setLayout(new BoxLayout(hitsPanel, BoxLayout.Y_AXIS));


		for (int i = 0; i < VibeComposerGUI.drumPanels.size(); i++) {
			DrumPanel dp = VibeComposerGUI.drumPanels.get(i);
			JPanel textHitsPanel = new JPanel();
			JTextField drumNum = new JTextField(dp.getInstrument() + "", 8);
			drumNum.setFocusable(false);
			drumNum.setEditable(false);
			textHitsPanel.add(drumNum);
			VisualPatternPanel dhpp = dp.makeVisualPatternPanel(new JButton("Dd"));
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
		Sequencer sequencer = VibeComposerGUI.sequencer;
		Thread cycle = new Thread() {

			public void run() {


				while (true) {
					try {
						if (sequencer != null && sequencer.isRunning()) {
							slider.setValue(
									VibeComposerGUI.slider.getValue() % slider.getMaximum());
						}

						try {
							sleep(25);
						} catch (InterruptedException e) {

						}
					} catch (Exception e) {
						System.out.println("Exception in SEQUENCE SLIDER:");
						e.printStackTrace();
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

		sliderPanel.setPreferredSize(new Dimension(32 * CheckBoxIcon.width + 130, 20));


		slider = new JSlider();
		slider.setMaximum(VibeComposerGUI.slider.getMaximum() / 4);
		slider.setToolTipText("Test");
		sliderPanel.add(slider);
		hitsPanel.add(sliderPanel);

		startOmnipresentThread();

	}
}