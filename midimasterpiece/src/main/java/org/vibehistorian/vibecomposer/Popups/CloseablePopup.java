package org.vibehistorian.vibecomposer.Popups;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JFrame;

public abstract class CloseablePopup {
	final JFrame frame = new JFrame();
	public static Map<Integer, CloseablePopup> currentPopupMap = new HashMap<>();
	private Integer popupType = 0;
	public static WindowListener EMPTY_WINDOW_LISTENER = new WindowListener() {
		@Override
		public void windowOpened(WindowEvent e) {
		}

		@Override
		public void windowClosing(WindowEvent e) {
		}

		@Override
		public void windowClosed(WindowEvent e) {
		}

		@Override
		public void windowIconified(WindowEvent e) {
		}

		@Override
		public void windowDeiconified(WindowEvent e) {
		}

		@Override
		public void windowActivated(WindowEvent e) {
		}

		@Override
		public void windowDeactivated(WindowEvent e) {
		}
	};
	// 16 current max

	public CloseablePopup(String windowTitle, Integer popupType) {
		this(windowTitle, popupType, new Point(0, 0));
	}

	public CloseablePopup(String windowTitle, Integer popupType, Point locOffset) {
		this.setPopupType(popupType);
		Point loc = MouseInfo.getPointerInfo().getLocation();
		loc.translate(12, 12);
		loc.translate(locOffset.x, locOffset.y);
		frame.setLocation(loc);
		addFrameWindowOperation();
		frame.setTitle(windowTitle);
		handleOpen();
	}

	public void handleOpen() {
		if (currentPopupMap.get(popupType) != null) {
			currentPopupMap.get(popupType).close();
			currentPopupMap.put(popupType, this);
		} else {
			currentPopupMap.put(popupType, this);
		}
	}

	public void handleClose() {
		currentPopupMap.remove(popupType);
	}

	public void close() {
		Toolkit.getDefaultToolkit().getSystemEventQueue()
				.postEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
		handleClose();

	}

	protected abstract void addFrameWindowOperation();

	public JFrame getFrame() {
		return frame;
	}

	public Integer getPopupType() {
		return popupType;
	}

	public void setPopupType(Integer popupType) {
		this.popupType = popupType;
	}
}

