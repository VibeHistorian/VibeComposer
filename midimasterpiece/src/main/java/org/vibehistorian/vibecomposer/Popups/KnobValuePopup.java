package org.vibehistorian.vibecomposer.Popups;

import java.awt.MouseInfo;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JFrame;

import org.vibehistorian.vibecomposer.Helpers.JKnob;
import org.vibehistorian.vibecomposer.Panels.NumPanel;

public class KnobValuePopup {
	final JFrame frame = new JFrame();
	private JKnob knob = null;
	private NumPanel numPanel = null;
	private boolean stretchAfterCustomInput = false;
	private Integer customInput = null;

	public KnobValuePopup(JKnob knob, boolean stretch) {
		this.knob = knob;
		stretchAfterCustomInput = stretch;
		numPanel = new NumPanel("Knob", knob.getValue(), knob.getMin(), knob.getMax());
		numPanel.getSlider().setVisible(false);
		numPanel.setAllowValuesOutsideRange(stretchAfterCustomInput);
		frame.add(numPanel);
		frame.setLocation(MouseInfo.getPointerInfo().getLocation());
		addFrameWindowOperation();
		numPanel.getTextfield().addKeyListener(new KeyListener() {

			@Override
			public void keyTyped(KeyEvent e) {


			}

			@Override
			public void keyPressed(KeyEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					applyAndClose();
				}

			}

		});
		frame.setTitle("Knob Value Setting");
		frame.pack();
		frame.setVisible(true);

	}

	public void applyAndClose() {
		try {
			customInput = Integer.valueOf(numPanel.getTextfield().getText());
		} catch (NumberFormatException ex) {
			System.out.println("Invalid value: " + numPanel.getTextfield().getText());
		}

		Toolkit.getDefaultToolkit().getSystemEventQueue()
				.postEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));

	}

	private void addFrameWindowOperation() {
		frame.addWindowListener(new WindowListener() {

			@Override
			public void windowOpened(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowClosing(WindowEvent e) {
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
						}
					}

				}

				JKnob.singlePopup = null;

			}

			@Override
			public void windowClosed(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowIconified(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowDeiconified(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowActivated(WindowEvent e) {
				// TODO Auto-generated method stub

			}

			@Override
			public void windowDeactivated(WindowEvent e) {
				// TODO Auto-generated method stub

			}

		});

	}
}
