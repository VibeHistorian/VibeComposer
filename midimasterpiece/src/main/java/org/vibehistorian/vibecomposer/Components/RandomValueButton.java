package org.vibehistorian.vibecomposer.Components;

import org.vibehistorian.vibecomposer.Popups.ButtonValuePopup;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Random;
import java.util.function.IntSupplier;

public class RandomValueButton extends JButton {

	private static final long serialVersionUID = -2737936353529731016L;
	private int value = 0;
	private IntSupplier currentSeed = () -> 0;

	public RandomValueButton(int value) {
		this(value, () -> 0);
	}

	public RandomValueButton(int value, IntSupplier currentSeed) {
		this.currentSeed = currentSeed == null ? () -> 0 : currentSeed;
		this.setPreferredSize(new Dimension(100, 30));
		this.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					if (isEnabled()) {
						new ButtonValuePopup(RandomValueButton.this);
					}
				} else if (SwingUtilities.isRightMouseButton(e)) {
					if (isEnabled()) {
						setValue(0);
					}
				} else if (SwingUtilities.isMiddleMouseButton(e)) {
					if (e.isControlDown()) {
						setEnabled(!isEnabled());
					} else if (isEnabled()) {
						if (e.isShiftDown()) {
							setValue(RandomValueButton.this.currentSeed.getAsInt());
						} else {
							Random rand = new Random();
							setValue(rand.nextInt());
						}
					}
				}
			}
		});
		setValue(value);
	}

	public int getValue() {
		return value;
	}

	public void setCurrentSeedSupplier(IntSupplier currentSeed) {
		this.currentSeed = currentSeed == null ? () -> 0 : currentSeed;
	}

	public void setValue(int value) {
		if (!isEnabled()) {
			return;
		}
		setText("" + value);
		this.value = value;
	}

}
