package org.vibehistorian.vibecomposer.Popups;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class PopupUtils {
	public static void addEmptySpaceCloser(JPanel panel, Window parentFrame) {
		addEmptySpaceCloser(panel, parentFrame, null);
	}

	public static void addEmptySpaceCloser(JPanel panel, Window parentFrame, Runnable rn) {
		panel.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				if (SwingUtilities.isRightMouseButton(evt)) {
					Toolkit.getDefaultToolkit().getSystemEventQueue()
							.postEvent(new WindowEvent(parentFrame, WindowEvent.WINDOW_CLOSING));
					if (rn != null) {
						rn.run();
					}
				}
			}
		});
	}
}
