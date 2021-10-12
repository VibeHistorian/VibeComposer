package org.vibehistorian.vibecomposer.Helpers;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
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
	private int marginLeft = 0, marginRight = 0;
	private Dimension defaultSize = VELO_DIM;
	private boolean highlighted = false;
	public static Color highlightColorDark = OMNI.alphen(new Color(255, 100, 100), 200);
	public static Color highlightColorLight = OMNI.alphen(new Color(255, 100, 100), 200);

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
					fine = true;
					fineStart = val;
				} else if (SwingUtilities.isRightMouseButton(evt)) {
					setValue(defaultVal);
					repaint();
				} else if (SwingUtilities.isMiddleMouseButton(evt)) {
					updateValueFromScreen();
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
				if (!SwingUtilities.isRightMouseButton(e)) {
					updateValueFromScreen();
				}
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				if (e.isControlDown()) {
					updateValueFromScreen();
				}
			}

		});
		updateSizes(defaultSize);
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

	public void setMargin(Insets in) {
		marginLeft = in.left;
		marginRight = in.right;
		updateSizes(
				new Dimension(defaultSize.width + marginLeft + marginRight, defaultSize.height));
	}

	public void setMarginLeftRight(int left, int right) {
		marginLeft = left;
		marginRight = right;
		updateSizes(
				new Dimension(defaultSize.width + marginLeft + marginRight, defaultSize.height));
	}

	public void updateSizes(Dimension size) {
		setPreferredSize(size);
		setMinimumSize(size);
		setMaximumSize(size);
		setSize(size);
	}

	public void setDefaultSize(Dimension size) {
		defaultSize = size;
	}

	/*@Override
	public Dimension getPreferredSize() {
		return new Dimension(VELO_DIM);
	}
	@Override
	public Dimension getMinimumSize() {
		return new Dimension(VELO_DIM);
	}
	
	@Override
	public Dimension getMaximumSize() {
		return new Dimension(VELO_DIM);
	}*/

	@Override
	public void paintComponent(Graphics guh) {

		if (guh instanceof Graphics2D) {
			Graphics2D g = (Graphics2D) guh;
			int displayedValue = isEnabled() ? val : max / 5;
			g.setColor(VibeComposerGUI.isDarkMode ? new Color(100, 100, 100)
					: new Color(180, 180, 180));

			int minX = (ColorCheckBox.HIDE_MARGINS) ? marginLeft : 0;
			int maxX = (ColorCheckBox.HIDE_MARGINS) ? this.getSize().width - minX - marginRight
					: this.getSize().width;
			int height = this.getSize().height;

			g.fillRect(minX, 0, maxX, height);
			Color c = null;

			if (highlighted && isEnabled()) {
				c = VibeComposerGUI.isDarkMode ? highlightColorDark : highlightColorLight;
			} else {
				c = OMNI.alphen(VibeComposerGUI.uiColor(), isEnabled() ? 150 : 70);
			}

			g.setColor(c);
			g.fillRect(minX, (int) (height * (1 - displayedValue / (double) max)), maxX,
					height * displayedValue / max);
			g.setColor(Color.black);
			g.drawRect(minX, 0, maxX, height);
		}
	}

	public boolean isHighlighted() {
		return highlighted;
	}

	public void setHighlighted(boolean highlighted) {
		if (this.highlighted == highlighted) {
			return;
		}
		this.highlighted = highlighted;
		repaint();
		/*if (highlighted) {
			Timer timer = new Timer(120, new ActionListener() {
		
				@Override
				public void actionPerformed(ActionEvent e) {
					setHighlighted(false);
					repaint();
				}
			});
			timer.setRepeats(false);
			timer.start();
		}*/
	}
}
