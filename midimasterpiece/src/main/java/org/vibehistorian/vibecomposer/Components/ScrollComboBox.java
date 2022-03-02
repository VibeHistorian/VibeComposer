package org.vibehistorian.vibecomposer.Components;

public class ScrollComboBox<T> extends ScrollComboPanel<T> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4602921599519276174L;

	public ScrollComboBox() {
		super(true);
	}

	public ScrollComboBox(boolean isRegenerating) {
		super(isRegenerating);
	}


	public static <T> void addAll(T[] choices, ScrollComboBox2<T> choice) {
		for (T c : choices) {
			choice.addItem(c);
		}
	}
}
