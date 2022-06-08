package org.vibehistorian.vibecomposer.Components;

import java.awt.event.ItemEvent;
import java.util.function.Function;

import javax.swing.JComboBox;

public class FireableComboBox<T> extends JComboBox<T> {

	private static final long serialVersionUID = 3383179107333241378L;
	ScrollComboPanel<T> parent = null;
	private Function<Object, String> tooltipFunc = null;

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

	public Function<Object, String> getTooltipFunc() {
		return tooltipFunc;
	}

	public void setTooltipFunc(Function<Object, String> tooltipFunc) {
		this.tooltipFunc = tooltipFunc;
	}

	@Override
	public String getToolTipText() {
		if (tooltipFunc != null) {
			tooltipFunc.apply(new Object());
		}
		return super.getToolTipText();
	}

	public String superToolTipText() {
		return super.getToolTipText();
	}
}