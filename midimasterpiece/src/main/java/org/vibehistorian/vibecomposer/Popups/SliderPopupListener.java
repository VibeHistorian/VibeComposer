package org.vibehistorian.vibecomposer.Popups;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.vibehistorian.vibecomposer.LG;

public class SliderPopupListener extends MouseAdapter {
	private JDialog toolTip = null;
	private final JLabel label = new JLabel("", SwingConstants.CENTER);
	private final Dimension size = new Dimension(30, 20);
	private int prevValue = -1;
	private boolean isDragging = false;
	private Thread cycle = null;
	private JFrame owner = null;

	protected SliderPopupListener(JDialog toolDialog, JFrame owner) {
		super();
		label.setOpaque(true);
		label.setBackground(UIManager.getColor("ToolTip.background"));
		//label.setBorder(UIManager.getBorder("ToolTip.border"));
		toolTip = toolDialog;
		toolTip.add(label);
		toolTip.setSize(size);
		this.owner = owner;
	}

	protected void updateToolTip(MouseEvent me) {
		//ownerFrame.repaint();
		JSlider slider = (JSlider) me.getComponent();
		int intValue = (int) slider.getValue();
		if (prevValue != intValue) {
			label.setText(String.format("%03d", slider.getValue()));
			Point pt = me.getPoint();
			pt.y = -size.height;
			SwingUtilities.convertPointToScreen(pt, me.getComponent());
			pt.translate(-size.width / 2, 0);
			toolTip.setLocation(pt);
		}
		prevValue = intValue;
	}

	@Override
	public void mousePressed(MouseEvent me) {
		//if (UIManager.getBoolean("Slider.onlyLeftMouseButtonDrag")
		//		&& SwingUtilities.isLeftMouseButton(me)) {
		toolTip.setVisible(true);
		isDragging = true;
		startVolumeSliderThread(me);
		//}
	}

	@Override
	public void mouseReleased(MouseEvent me) {
		toolTip.setVisible(false);
		isDragging = false;
		//toolTip.dispose();
	}

	private void startVolumeSliderThread(MouseEvent me) {
		if (cycle != null && cycle.isAlive()) {
			LG.d("Label slider thread already exists!");
			return;
		}
		LG.d("Starting new label slider thread..!");
		cycle = new Thread() {

			public void run() {
				while (isDragging) {

					updateToolTip(me);
					owner.pack();
					owner.setVisible(true);
					owner.repaint();
					try {
						sleep(25);
					} catch (InterruptedException e) {
						// Auto-generated catch block
						e.printStackTrace();
					}
				}

			}
		};
		cycle.start();
	}
}
