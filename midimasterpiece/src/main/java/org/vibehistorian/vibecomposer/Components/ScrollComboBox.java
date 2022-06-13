package org.vibehistorian.vibecomposer.Components;

import java.util.Collection;

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

	public static <T> void addAll(T[] choices, ScrollComboBox<T> choice) {
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

	public static <T> void addAll(Collection<T> choices, ScrollComboBox<T> choice) {
		for (T c : choices) {
			choice.addItem(c);
		}
	}
}
