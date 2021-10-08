package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class VeloRect extends JComponent {

	private static final long serialVersionUID = -4362816375260955975L;

	public static final Dimension VELO_DIM = new Dimension(CheckBoxIcon.width,
			CheckBoxIcon.width * 2);
	//public static boolean veloDragging = false;

	private int min = 0;
	private int max = 100;
	private int val = 50;
	private int defaultVal = 50;
	public static boolean fine = false;

	public VeloRect(int min, int max, int currentVal) {
		super();
		this.min = min;
		this.max = max;
		this.val = currentVal;
		this.defaultVal = 50;
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				if (SwingUtilities.isLeftMouseButton(evt)) {
					updateValueFromScreen();
				} else if (SwingUtilities.isRightMouseButton(evt)) {
					setValue(defaultVal);
					repaint();
				} else if (SwingUtilities.isMiddleMouseButton(evt)) {
					updateValueFromScreen();
					fine = true;
				}
			}

			@Override
			public void mouseReleased(MouseEvent evt) {
				fine = false;
			}
		});

		addMouseMotionListener(new MouseMotionListener() {

			@Override
			public void mouseDragged(MouseEvent e) {
				updateValueFromScreen();
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				if (e.isControlDown()) {
					updateValueFromScreen();
				}
			}

		});
	}

	public void updateValueFromScreen() {
		Point xy = new Point(MouseInfo.getPointerInfo().getLocation());
		SwingUtilities.convertPointFromScreen(xy, VeloRect.this);
		int newVal = max - (max * xy.y / getHeight());
		if (fine) {
			int newValFine = (val * 999 + newVal) / 1000;
			newVal = (newVal > val) ? OMNI.clamp(newValFine, val + 1, max)
					: OMNI.clamp(newValFine, min, val - 1);
		}
		setValue(OMNI.clamp(newVal, min, max));
		repaint();
	}

	public int getMin() {
		return min;
	}

	public void setMin(int min) {
		this.min = min;
	}

	public int getMax() {
		return max;
	}

	public void setMax(int max) {
		this.max = max;
	}

	public int getValue() {
		return val;
	}

	public void setValue(int value) {
		this.val = value;
	}

	public void setDefaultValue(int defValue) {
		this.defaultVal = defValue;
		this.val = defValue;
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(VELO_DIM);
	}

	/**
	 * Return the minimum size that the knob would like to be.
	 * This is the same size as the preferred size so the
	 * knob will be of a fixed size.
	 *
	 * @return the minimum size of the JKnob.
	 */
	@Override
	public Dimension getMinimumSize() {
		return new Dimension(VELO_DIM);
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(VELO_DIM);
	}

	@Override
	public void paintComponent(Graphics guh) {

		if (guh instanceof Graphics2D) {
			Graphics2D g = (Graphics2D) guh;
			g.setColor(VibeComposerGUI.isDarkMode ? new Color(100, 100, 100)
					: new Color(180, 180, 180));
			g.fillRect(0, 0, this.getSize().width, this.getSize().height);
			g.setColor(OMNI.alphen(VibeComposerGUI.uiColor(), 150));
			g.fillRect(0, (int) (this.getSize().height * (1 - val / (double) max)),
					this.getSize().width, this.getSize().height * val / max);
		}
	}
}
