package org.vibehistorian.vibecomposer.Popups;

import java.awt.Dimension;

import javax.sound.midi.Sequencer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;

import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.CheckBoxIcon;
import org.vibehistorian.vibecomposer.Panels.DrumHitsPatternPanel;
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

	public DrumLoopPopup() {
		hitsPanel.setLayout(new BoxLayout(hitsPanel, BoxLayout.Y_AXIS));


		for (DrumPanel dp : VibeComposerGUI.drumPanels) {
			DrumHitsPatternPanel dhpp = dp.makeDrumHitsPanel(new JButton("Dd"));
			dhpp.setTruePattern(dp.getComboPanel().getTruePattern());
			dhpp.setViewOnly(true);

			dhpp.reapplyShift();
			dhpp.reapplyHits();
			hitsPanel.add(dhpp);

		}
		initSliderPanel();
		scroll = new JScrollPane(hitsPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

		scroll.getVerticalScrollBar().setUnitIncrement(16);
		frame.add(scroll);
		frame.pack();
		frame.setVisible(true);

		System.out.println("Opened Drum Loop popup!");
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
		sliderPanel.setOpaque(false);
		sliderPanel.setLayout(new BoxLayout(sliderPanel, BoxLayout.X_AXIS));

		sliderPanel.setPreferredSize(new Dimension(32 * CheckBoxIcon.width + 10, 20));


		slider = new JSlider();
		slider.setMaximum(VibeComposerGUI.slider.getMaximum() / 4);
		slider.setToolTipText("Test");

		sliderPanel.add(slider);
		hitsPanel.add(sliderPanel);

		startOmnipresentThread();

	}
}
