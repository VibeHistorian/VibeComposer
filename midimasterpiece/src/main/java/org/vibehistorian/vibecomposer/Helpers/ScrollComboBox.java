package org.vibehistorian.vibecomposer.Helpers;

import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.VibeComposerGUI;

public class ScrollComboBox<T> extends JComboBox<T> {

	private static final long serialVersionUID = -1471401267249157092L;
	private boolean scrollEnabled = true;
	private boolean mousePressed = false;
	private boolean regenerating = true;

	public ScrollComboBox() {
		addMouseWheelListener(new MouseWheelListener() {

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (!scrollEnabled || !isEnabled())
					return;
				mousePressed = true;
				setSelectedIndex((getSelectedIndex() + e.getWheelRotation() + getItemCount())
						% getItemCount());
				ItemEvent evnt = new ItemEvent(ScrollComboBox.this, ItemEvent.ITEM_STATE_CHANGED,
						getSelectedItem(), ItemEvent.SELECTED);
				fireItemStateChanged(evnt);
			}

		});
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				mousePressed = true;
				if (SwingUtilities.isRightMouseButton(evt)) {
					if (!isEnabled()) {
						return;
					}
					setSelectedIndex(0);
					ItemEvent evnt = new ItemEvent(ScrollComboBox.this,
							ItemEvent.ITEM_STATE_CHANGED, getSelectedItem(), ItemEvent.SELECTED);
					fireItemStateChanged(evnt);
				} else if (SwingUtilities.isMiddleMouseButton(evt)) {
					if (evt.isControlDown()) {
						setEnabled(!isEnabled());
					}
				}
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

	@Override
	public void setSelectedIndex(int index) {
		setVal(getItemAt(index));
	}

	public void setVal(T item) {
		if (isEnabled()) {
			setSelectedItem(item);
		}
		if (isEnabled() && regenerating && mousePressed
				&& VibeComposerGUI.canRegenerateOnChange()) {
			VibeComposerGUI.vibeComposerGUI.composeMidi(true);
		}
		mousePressed = false;
	}

	public T getLastVal() {
		return getItemAt(getItemCount() - 1);
	}

	public static <T> void addAll(T[] choices, ScrollComboBox<T> choice) {
		for (T c : choices) {
			choice.addItem(c);
		}
	}

	public boolean isRegenerating() {
		return regenerating;
	}

	public void setRegenerating(boolean regenerating) {
		this.regenerating = regenerating;
	}
}
