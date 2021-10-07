package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
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
	private int curr = 50;

	public VeloRect(int min, int max, int curr) {
		super();
		this.min = min;
		this.max = max;
		this.curr = curr;
		addMouseMotionListener(new MouseMotionListener() {

			@Override
			public void mouseDragged(MouseEvent e) {
				updateCurr();
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				if (e.isControlDown()) {
					updateCurr();
				}
			}

		});
	}

	public void updateCurr() {
		Point xy = new Point(MouseInfo.getPointerInfo().getLocation());
		SwingUtilities.convertPointFromScreen(xy, VeloRect.this);
		setCurr(max - (max * xy.y / getHeight()));
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

	public int getCurr() {
		return curr;
	}

	public void setCurr(int curr) {
		this.curr = curr;
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
			g.fillRect(0, (int) (this.getSize().height * (1 - curr / (double) max)),
					this.getSize().width, this.getSize().height * curr / max);
		}
	}
}
