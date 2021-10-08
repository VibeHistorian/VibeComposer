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
	public static int fineStart = 50;

	public VeloRect(int min, int max, int currentVal) {
		super();
		this.min = min;
		this.max = max;
		this.val = currentVal;
		this.defaultVal = 50;
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				if (!isEnabled()) {
					return;
				}
				if (SwingUtilities.isLeftMouseButton(evt)) {
					updateValueFromScreen();
				} else if (SwingUtilities.isRightMouseButton(evt)) {
					setValue(defaultVal);
					repaint();
				} else if (SwingUtilities.isMiddleMouseButton(evt)) {
					updateValueFromScreen();
					fine = true;
					fineStart = val;
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
		if (!isEnabled()) {
			return;
		}
		Point xy = new Point(MouseInfo.getPointerInfo().getLocation());
		SwingUtilities.convertPointFromScreen(xy, VeloRect.this);
		int newVal = max - (max * xy.y / getHeight());
		if (fine) {
			newVal = (fineStart * 9 + newVal) / 10;
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
			Color c = OMNI.alphen(VibeComposerGUI.uiColor(), isEnabled() ? 150 : 70);
			g.setColor(c);
			g.fillRect(0, (int) (this.getSize().height * (1 - val / (double) max)),
					this.getSize().width, this.getSize().height * val / max);
		}
	}
}
