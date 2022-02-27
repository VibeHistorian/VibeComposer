package org.vibehistorian.vibecomposer.Components;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.BoxLayout;
import javax.swing.ComboBoxEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;

import org.vibehistorian.vibecomposer.SwingUtils;
import org.vibehistorian.vibecomposer.UndoManager;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.BoundsPopupMenuListener;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.TransparentablePanel;

public class ScrollComboPanel<T> extends TransparentablePanel implements GloballyLockable {

	private static final long serialVersionUID = -1471401267249157092L;
	protected boolean scrollEnabled = true;
	protected boolean userInteracting = false;
	protected boolean globalInteraction = false;
	protected boolean regenerating = true;
	protected boolean hasPrototypeSet = false;
	protected boolean requiresSettingPrototype = true;
	private Consumer<? super Object> func = null;
	public static ScrollComboPanel<?> lastTouchedBox = null;
	protected LockComponentButton lockButt = null;
	protected FireableComboBox<T> scb = null;
	public int w = 80;
	public int h = 28;

	protected JLayeredPane pane = null;

	public ScrollComboPanel() {
		this(true, true);
	}

	public ScrollComboPanel(boolean isReg) {
		this(isReg, true);
	}

	public ScrollComboPanel(boolean isReg, boolean setup) {
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		//this.setBorder(new BevelBorder(BevelBorder.RAISED));
		setAlignmentY(0.5f);
		setOpaque(false);
		regenerating = isReg;
		if (setup) {
			setupBox(new FireableComboBox<T>(this));
		}

	}

	public void setupBox(FireableComboBox<T> box) {
		scb = box;
		BoundsPopupMenuListener listener = new BoundsPopupMenuListener(true, false);
		scb.addPopupMenuListener(listener);

		scb.addMouseWheelListener(new MouseWheelListener() {

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (!scrollEnabled || !isEnabled())
					return;
				prepareInteraction(e.isControlDown());
				setSelectedIndex((getSelectedIndex() + e.getWheelRotation() + getItemCount())
						% getItemCount());
				ItemEvent evnt = new ItemEvent(scb, ItemEvent.ITEM_STATE_CHANGED, getSelectedItem(),
						ItemEvent.SELECTED);
				scb._fireItemStateChanged(evnt);
			}

		});
		scb.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent evt) {
				prepareInteraction(evt.isControlDown());
				if (SwingUtilities.isRightMouseButton(evt)) {
					if (!isEnabled()) {
						return;
					}
					setSelectedIndex(0);
					ItemEvent evnt = new ItemEvent(scb, ItemEvent.ITEM_STATE_CHANGED,
							getSelectedItem(), ItemEvent.SELECTED);
					fireItemStateChanged(evnt);
				} else if (SwingUtilities.isMiddleMouseButton(evt) && !evt.isAltDown()
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

		if (regenerating) {
			pane = new JLayeredPane();
			pane.setPreferredSize(new Dimension(w, h));
			pane.setOpaque(false);
			pane.add(scb);
			lockButt = new LockComponentButton(this);
			pane.add(lockButt);
			pane.setComponentZOrder(scb, Integer.valueOf(1));
			pane.setComponentZOrder(lockButt, Integer.valueOf(0));
			lockButt.setBounds(0, h - 8, 8, 8);

			scb.setBounds(0, 0, 80, h);
			add(pane);
		} else {
			add(scb);
		}
	}

	public FireableComboBox<T> box() {
		return scb;
	}

	@Override
	public void setEnabled(boolean enabled) {
		scb.setEnabled(enabled);
	}

	@Override
	public boolean isEnabled() {
		return scb.isEnabled();
	}


	public void fireItemStateChanged(ItemEvent evnt) {
		scb._fireItemStateChanged(evnt);

	}

	public Object getSelectedItem() {
		return scb.getSelectedItem();
	}

	public int getItemCount() {
		return scb.getItemCount();
	}

	public int getSelectedIndex() {
		return scb.getSelectedIndex();
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

	public T getItemAt(int selectedIndex) {
		return scb.getItemAt(selectedIndex);
	}

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
				VibeComposerGUI.getAffectedPanels(parentIp.getPartNum()).forEach(ip -> ip
						.findScrollComboBoxesByFirstVal(getItemAt(0)).forEach(e -> e.setVal(item)));
			}
		}

		if (interacting) {
			UndoManager.saveToHistory(this);
		}

		if (isEnabled() && isDifferent) {
			setSelectedItem(item);
		}

		if (func != null) {
			func.accept(new Object());
		}
		if (isEnabled() && regenerating && interacting && VibeComposerGUI.canRegenerateOnChange()
				&& isDifferent) {
			VibeComposerGUI.vibeComposerGUI.composeMidi(true);
		}
		discardInteraction();
	}

	public void setSelectedItem(T item) {
		scb.setSelectedItem(item);
	}

	public T getLastVal() {
		return getItemAt(getItemCount() - 1);
	}

	public void addItem(T val) {
		scb.addItem(val);
		if (requiresSettingPrototype) {
			setPrototype(val);
		}
	}

	public static <T> void addAll(T[] choices, ScrollComboPanel<T> choice) {
		for (T c : choices) {
			choice.addItem(c);
		}

		/*if (choice.getItemCount() > 0) {
			SwingUtilities.invokeLater(() -> {
				choice.lockButt.setBounds(choice.getWidth() - 7, choice.getHeight() - 8, 8, 8);
				LG.i(choice.lockButt.getBounds().toString());
				LG.i(choice.getSize().toString());
			});
		}*/
	}

	public boolean isRegenerating() {
		return regenerating;
	}

	public void setRegenerating(boolean regenerating) {
		this.regenerating = regenerating;
	}

	public void removeArrowButton() {
		for (Component c : scb.getComponents()) {
			if (c instanceof JButton) {
				//LG.d("Rem button");
				scb.remove(c);
				break;
			}
		}
	}

	@Override
	public void setMaximumSize(Dimension maximumSize) {
		super.setMaximumSize(maximumSize);
		w = maximumSize.width;
		h = maximumSize.height;
		if (pane != null) {
			pane.setPreferredSize(maximumSize);
			scb.setBounds(0, 0, w, h);
		} else {
			scb.setMaximumSize(maximumSize);
		}

		if (lockButt != null) {
			lockButt.setBounds(0, h - 8, 8, 8);
		}
	}

	public void setPrototype(T val) {
		if (w < 120 && pane != null) {
			int textW = SwingUtils.getDrawStringWidth(val.toString());
			int newW = Math.min(120, (h * 4 / 3) + (textW * 9 / 8));
			if (w > newW) {
				return;
			}
			w = newW;
			pane.setPreferredSize(new Dimension(w, h));
			scb.setBounds(0, 0, w, h);
			scb.setPrototypeDisplayValue(val);
			hasPrototypeSet = true;
		}
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

	@Override
	public void setEnabledGlobal(boolean enabled) {
		InstPanel instParent = SwingUtils.getInstParent(this);
		if (instParent == null) {
			setEnabled(enabled);
			repaint();
			return;
		}
		instParent.getAllComponentsLike(scb, FireableComboBox.class).forEach(e -> {
			e.setEnabled(enabled);
			e.repaint();
		});
	}

	public void removeAllItems() {
		scb.removeAllItems();
	}

	public void addItemListener(ItemListener itemListener) {
		scb.addItemListener(itemListener);
	}

	public void addActionListener(ActionListener actionListener) {
		scb.addActionListener(actionListener);
	}

	public void removeItemAt(int i) {
		scb.removeItemAt(i);
	}

	public ComboBoxEditor getEditor() {
		return scb.getEditor();
	}

	public void setEditable(boolean b) {
		scb.setEditable(b);
	}

	@Override
	public synchronized void addMouseListener(MouseListener l) {
		scb.addMouseListener(l);
	}
}

class FireableComboBox<T> extends JComboBox<T> {

	private static final long serialVersionUID = 3383179107333241378L;
	ScrollComboPanel<T> parent = null;

	public FireableComboBox(ScrollComboPanel<T> scrollComboBox) {
		parent = scrollComboBox;
	}

	public void _fireItemStateChanged(ItemEvent e) {
		fireItemStateChanged(e);
	}

	@Override
	public void setSelectedIndex(int index) {
		parent.setVal(getItemAt(index));
	}
}
