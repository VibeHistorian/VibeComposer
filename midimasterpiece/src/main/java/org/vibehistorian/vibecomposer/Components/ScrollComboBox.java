package org.vibehistorian.vibecomposer.Components;

import java.awt.Component;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.BoundsPopupMenuListener;

public class ScrollComboBox<T> extends JComboBox<T> {

	private static final long serialVersionUID = -1471401267249157092L;
	private boolean scrollEnabled = true;
	protected boolean mousePressed = false;
	private boolean regenerating = true;
	private boolean hasPrototypeSet = false;
	private boolean requiresSettingPrototype = false;

	public ScrollComboBox() {
		this(true);
	}

	public ScrollComboBox(boolean isReg) {
		regenerating = isReg;

		BoundsPopupMenuListener listener = new BoundsPopupMenuListener(true, false);
		addPopupMenuListener(listener);

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

	@Override
	public void addItem(T val) {
		super.addItem(val);
		if (!hasPrototypeSet && requiresSettingPrototype) {
			setPrototypeDisplayValue(val);
			hasPrototypeSet = true;
		}
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

	public void removeArrowButton() {
		for (Component c : getComponents()) {
			if (c instanceof JButton) {
				//System.out.println("Rem button");
				remove(c);
				break;
			}
		}
	}

	public void setPrototype(T val) {
		setPrototypeDisplayValue(val);
		hasPrototypeSet = true;
	}

	public boolean isRequiresSettingPrototype() {
		return requiresSettingPrototype;
	}

	public void setRequiresSettingPrototype(boolean requiresSettingPrototype) {
		this.requiresSettingPrototype = requiresSettingPrototype;
	}
}
