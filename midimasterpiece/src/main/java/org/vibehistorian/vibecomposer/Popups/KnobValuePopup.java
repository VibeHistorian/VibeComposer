package org.vibehistorian.vibecomposer.Popups;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.JKnob;
import org.vibehistorian.vibecomposer.Helpers.OMNI;
import org.vibehistorian.vibecomposer.Panels.NumPanel;

public class KnobValuePopup extends CloseablePopup {
	private JKnob knob = null;
	private NumPanel numPanel = null;
	private boolean stretchAfterCustomInput = false;
	private Integer customInput = null;

	public KnobValuePopup(JKnob knob, boolean stretch, boolean allowValuesOutsideRange) {
		super("Knob Value Setting", 0);
		this.knob = knob;
		stretchAfterCustomInput = stretch;

		numPanel = new NumPanel("Knob", knob.getValue(), knob.getMin(), knob.getMax());
		numPanel.getSlider().setVisible(false);
		numPanel.setAllowValuesOutsideRange(allowValuesOutsideRange);
		numPanel.getTextfield().addKeyListener(new KeyListener() {

			@Override
			public void keyTyped(KeyEvent e) {


			}

			@Override
			public void keyPressed(KeyEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					close();
				}

			}

		});

		frame.add(numPanel);
		frame.pack();
		frame.setVisible(true);

	}

	protected void addFrameWindowOperation() {
		frame.addWindowListener(new WindowListener() {

			@Override
			public void windowOpened(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowClosing(WindowEvent e) {
				try {
					customInput = Integer.valueOf(numPanel.getTextfield().getText());
				} catch (NumberFormatException ex) {
					System.out.println("Invalid value: " + numPanel.getTextfield().getText());
				}
				if (customInput != null) {
					int val = customInput;
					if (stretchAfterCustomInput) {
						if (val > knob.getMax()) {
							knob.setMax(val);
						} else if (val < knob.getMin()) {
							knob.setMin(val);
						}
						knob.setValue(val);
					} else {
						if (knob.getMin() <= val && knob.getMax() >= val) {
							knob.setValue(val);
						} else {
							knob.setValue(OMNI.clamp(val, knob.getMin(), knob.getMax()));
						}
					}
					if (VibeComposerGUI.regenerateWhenValuesChange.isSelected()) {
						VibeComposerGUI.vibeComposerGUI.composeMidi(true);
					}

				}

			}

			@Override
			public void windowClosed(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowIconified(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowDeiconified(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowActivated(WindowEvent e) {
				// Auto-generated method stub

			}

			@Override
			public void windowDeactivated(WindowEvent e) {
				// Auto-generated method stub

			}

		});

	}
}
