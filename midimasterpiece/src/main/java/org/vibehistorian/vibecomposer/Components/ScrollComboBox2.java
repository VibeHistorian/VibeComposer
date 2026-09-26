package org.vibehistorian.vibecomposer.Components;

import org.vibehistorian.vibecomposer.ApplicationSessionState;

import org.vibehistorian.vibecomposer.Helpers.BoundsPopupMenuListener;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.SwingUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ScrollComboBox2<T> extends JComboBox<T> implements GloballyLockable {

	private static final long serialVersionUID = -1471401267249157092L;
	private boolean scrollEnabled = true;
	protected boolean userInteracting = false;
	protected boolean globalInteraction = false;
	private boolean regenerating = true;
	private boolean hasPrototypeSet = false;
	private boolean requiresSettingPrototype = false;
	private Consumer<? super Object> func = null;
	private InstrumentControlContext instrumentControlContext;
	public static ScrollComboBox2<?> lastTouchedBox = null;

	public ScrollComboBox2() {
		this(true);
	}

	public ScrollComboBox2(boolean isReg) {
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
				ItemEvent evnt = new ItemEvent(ScrollComboBox2.this, ItemEvent.ITEM_STATE_CHANGED,
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
					ItemEvent evnt = new ItemEvent(ScrollComboBox2.this,
							ItemEvent.ITEM_STATE_CHANGED, getSelectedItem(), ItemEvent.SELECTED);
					fireItemStateChanged(evnt);
				} else if (SwingUtilities.isMiddleMouseButton(evt)
						&& (!evt.isShiftDown() || evt.isControlDown())) {
					if (evt.isControlDown()) {
						if (evt.isShiftDown()) {
							setEnabledGlobal(!isEnabled());
						} else {
							setEnabled(!isEnabled());
						}
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
		userInteracting = true;
		globalInteraction = ctrlClick;
		lastTouchedBox = this;
	}

	public void discardInteraction() {
		userInteracting = false;
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
		boolean interacting = userInteracting;
		boolean isDifferent = getVal() != item;

		if (globalInteraction) {
			globalInteraction = false;
			userInteracting = false;
			InstPanel parentIp = SwingUtils.getInstParent(this);
			if (parentIp != null) {
				getAffectedPanels(parentIp).forEach(ip -> ip
						.findScrollComboBoxesByFirstVal(getItemAt(0)).forEach(e -> e.setVal(item)));
			}
		}

		if (interacting) {
			ApplicationSessionState.actionUndoManager.saveToHistory(this);
		}

		if (isEnabled()) {
			setSelectedItem(item);
		}

		if (func != null) {
			func.accept(new Object());
		}

		if (isEnabled() && regenerating && interacting && instrumentControlContext != null
				&& instrumentControlContext.canRegenerateOnChange()
				&& isDifferent) {
			instrumentControlContext.regenerate();
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

	public static <T> void addAll(T[] choices, ScrollComboBox2<T> choice) {
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

	public void setInstrumentControlContext(InstrumentControlContext instrumentControlContext) {
		this.instrumentControlContext = instrumentControlContext;
	}

	public void removeFunc() {
		func = null;
	}

	@Override
	public void setEnabledGlobal(boolean enabled) {
		InstPanel instParent = SwingUtils.getInstParent(this);
		if (instParent == null) {
			setEnabled(enabled);
			repaint();
			return;
		}
		for (InstPanel ip : getAffectedPanels(instParent)) {
			ip.findScrollComboBoxesByFirstVal(getItemAt(0)).forEach(e -> {
				e.setEnabled(enabled);
				e.repaint();
			});
		}
	}

	private List<InstPanel> getAffectedPanels(InstPanel instParent) {
		return instrumentControlContext == null ? Collections.<InstPanel>emptyList()
				: instrumentControlContext.getAffectedPanels(instParent.getPartNum());
	}
}
