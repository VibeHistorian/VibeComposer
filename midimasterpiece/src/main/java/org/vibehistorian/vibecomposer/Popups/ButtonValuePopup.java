package org.vibehistorian.vibecomposer.Popups;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Random;

import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.Components.RandomValueButton;
import org.vibehistorian.vibecomposer.Panels.NumPanel;

public class ButtonValuePopup extends CloseablePopup {
	private RandomValueButton butt = null;
	private NumPanel numPanel = null;
	private Integer customInput = null;
	public int randomNum = Integer.MIN_VALUE;

	public ButtonValuePopup(RandomValueButton butt) {
		super("Button Value Setting", 0);
		this.butt = butt;
		Random rand = new Random();
		randomNum = rand.nextInt();

		numPanel = new NumPanel("Button", butt.getValue(), Integer.MIN_VALUE, Integer.MAX_VALUE);
		numPanel.getSlider().setVisible(false);
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
					LG.d("Invalid value: " + numPanel.getTextfield().getText());
				}
				if (customInput != null) {
					butt.setValue(customInput);
				}

				/*if (RandomValueButton.singlePopup != null) {
					if (RandomValueButton.singlePopup.randomNum == randomNum) {
						RandomValueButton.singlePopup = null;
					}
				}*/

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
