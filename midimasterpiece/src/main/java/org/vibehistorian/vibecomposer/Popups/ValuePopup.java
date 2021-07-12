package org.vibehistorian.vibecomposer.Popups;

import java.awt.MouseInfo;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JFrame;

import org.vibehistorian.vibecomposer.Helpers.RandomValueButton;
import org.vibehistorian.vibecomposer.Panels.NumPanel;

public class ValuePopup {
	final JFrame frame = new JFrame();
	private RandomValueButton butt = null;
	private NumPanel numPanel = null;
	private Integer customInput = null;

	public ValuePopup(RandomValueButton butt) {
		this.butt = butt;
		numPanel = new NumPanel("Button", butt.getValue(), Integer.MIN_VALUE, Integer.MAX_VALUE);
		numPanel.getSlider().setVisible(false);
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
		frame.setTitle("Button Value Setting");
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
					butt.setValue(customInput);
				}

				RandomValueButton.singlePopup = null;

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
