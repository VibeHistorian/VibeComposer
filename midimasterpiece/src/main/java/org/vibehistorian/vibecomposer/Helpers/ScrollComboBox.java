package org.vibehistorian.vibecomposer.Helpers;

import java.awt.event.ItemEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.JComboBox;

public class ScrollComboBox<T> extends JComboBox<T> {

	private static final long serialVersionUID = -1471401267249157092L;
	private boolean scrollEnabled = true;

	public ScrollComboBox() {
		addMouseWheelListener(new MouseWheelListener() {

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (!scrollEnabled)
					return;

				setSelectedIndex((getSelectedIndex() + e.getWheelRotation() + getItemCount())
						% getItemCount());
				ItemEvent evnt = new ItemEvent(ScrollComboBox.this, ItemEvent.ITEM_STATE_CHANGED,
						getSelectedItem(), ItemEvent.SELECTED);
				fireItemStateChanged(evnt);
			}

		});
	}

	public boolean isScrollEnabled() {
		return scrollEnabled;
	}

	public void setScrollEnabled(boolean scrollEnabled) {
		this.scrollEnabled = scrollEnabled;
	}

	public T getVal() {
		return getItemAt(getSelectedIndex());
	}
}
