package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.Popups.ValuePopup;

public class RandomValueButton extends JButton {

	private static final long serialVersionUID = -2737936353529731016L;
	public static ValuePopup singlePopup = null;


	public RandomValueButton(int value) {
		this.setPreferredSize(new Dimension(100, 30));
		this.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					Random rand = new Random();
					setText(rand.nextInt() + "");
				} else if (SwingUtilities.isRightMouseButton(e)) {
					setText("0");
				} else if (SwingUtilities.isMiddleMouseButton(e)) {
					if (singlePopup != null) {
						singlePopup.close();
						singlePopup = null;
					}
					singlePopup = new ValuePopup(RandomValueButton.this);
				}
			}
		});
		setValue(value);
	}

	public int getValue() {
		return Integer.valueOf(getText());
	}

	public void setValue(int value) {
		setText("" + value);
	}

}
