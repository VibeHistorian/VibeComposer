package org.vibehistorian.vibecomposer.Components;

import java.awt.Component;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.SwingUtils;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.BoundsPopupMenuListener;
import org.vibehistorian.vibecomposer.Panels.InstPanel;

public class ScrollComboBox<T> extends JComboBox<T> {

	private static final long serialVersionUID = -1471401267249157092L;
	private boolean scrollEnabled = true;
	protected boolean interactive = false;
	protected boolean globalInteraction = false;
	private boolean regenerating = true;
	private boolean hasPrototypeSet = false;
	private boolean requiresSettingPrototype = false;
	private Consumer<? super Object> func = null;
	public static ScrollComboBox<?> lastTouchedBox = null;

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
				prepareInteraction(e.isControlDown());
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
				prepareInteraction(evt.isControlDown());
				if (SwingUtilities.isRightMouseButton(evt)) {
					if (!isEnabled()) {
						return;
					}
					setSelectedIndex(0);
					ItemEvent evnt = new ItemEvent(ScrollComboBox.this,
							ItemEvent.ITEM_STATE_CHANGED, getSelectedItem(), ItemEvent.SELECTED);
					fireItemStateChanged(evnt);
				} else if (SwingUtilities.isMiddleMouseButton(evt) && !evt.isShiftDown()) {
					if (evt.isControlDown()) {
						setEnabled(!isEnabled());
					} else {
						List<Integer> viableIndices = IntStream.iterate(0, e -> e + 1)
								.limit(getItemCount()).boxed().collect(Collectors.toList());
						if (viableIndices.size() > 1) {
							viableIndices.remove(Integer.valueOf(getSelectedIndex()));
						}
						setSelectedIndex(
								viableIndices.get(new Random().nextInt(viableIndices.size())));
					}
				}
			}
		});

	}

	public static void discardInteractions() {
		if (lastTouchedBox != null) {
			lastTouchedBox.discardInteraction();
		}
	}

	public void prepareInteraction(boolean ctrlClick) {
		interactive = true;
		globalInteraction = ctrlClick;
		lastTouchedBox = this;
	}

	public void discardInteraction() {
		interactive = false;
		globalInteraction = false;
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
		boolean shouldRegenerate = interactive;
		boolean isDifferent = getVal() != item;

		if (globalInteraction) {
			globalInteraction = false;
			interactive = false;
			InstPanel parentIp = SwingUtils.getInstParent(this);
			if (parentIp != null) {
				VibeComposerGUI.getAffectedPanels(parentIp.getPartNum()).forEach(ip -> ip
						.findScrollComboBoxesByFirstVal(getItemAt(0)).forEach(e -> e.setVal(item)));
			}
		}
		if (isEnabled()) {
			setSelectedItem(item);
		}

		if (func != null) {
			func.accept(new Object());
		}

		if (isEnabled() && regenerating && shouldRegenerate
				&& VibeComposerGUI.canRegenerateOnChange() && isDifferent) {
			VibeComposerGUI.vibeComposerGUI.composeMidi(true);
		}
		discardInteraction();
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
				//LG.d("Rem button");
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

	public void setFunc(Consumer<? super Object> func) {
		this.func = func;
	}

	public void removeFunc() {
		func = null;
	}
}
