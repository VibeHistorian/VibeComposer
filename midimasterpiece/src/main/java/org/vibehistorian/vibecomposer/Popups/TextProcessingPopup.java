package org.vibehistorian.vibecomposer.Popups;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.function.Consumer;

import javax.swing.JTextField;

public class TextProcessingPopup extends CloseablePopup {
	private Consumer<String> callback = null;
	private JTextField textField = new JTextField("", 8);

	public TextProcessingPopup(String title, Consumer<String> callback) {
		super(title, 0);
		this.callback = callback;

		textField.addKeyListener(new KeyListener() {

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

		frame.add(textField);
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
				if (frame.isVisible()) {
					callback.accept(textField.getText());
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
