package org.vibehistorian.vibecomposer;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;

public class UndoManager {
	public static List<Pair<Component, Integer>> undoList = new ArrayList<>();
	public static ScrollComboBox<String> historyBox = new ScrollComboBox<>(false);
	public static int historyIndex = 0;
	public static boolean recordingEvents = false;

	static {
		historyBox.setFunc(e -> {
			if (historyIndex != historyBox.getSelectedIndex()) {
				loadFromHistory(historyBox.getSelectedIndex());
			}
		});
	}

	public static void updateHistoryBox() {
		historyBox.removeAllItems();
		for (int i = 0; i < undoList.size(); i++) {
			historyBox.addItem(i + " (" + undoList.get(i).getLeft().getName() + ")");
		}
		historyBox.setSelectedIndex(historyBox.getItemCount() - 1);
	}

	public static void saveToHistory(Component c) {
		saveToHistory(c, VibeComposerGUI.getComponentValue(c));
	}

	public static void saveToHistory(Component c, Integer val) {
		if (!recordingEvents) {
			return;
		}

		if (historyIndex + 1 < undoList.size() && undoList.size() > 0) {
			undoList = undoList.subList(0, historyIndex + 1);
		}

		undoList.add(MutablePair.of(c, val));
		historyIndex = undoList.size() - 1;
		updateHistoryBox();
	}

	public static void undo() {
		loadFromHistory(historyIndex - 1);
	}

	public static void redo() {
		loadFromHistory(historyIndex + 1);
	}

	public static void loadFromHistory(int index) {
		if (historyIndex == index) {
			return;
		}
		LG.i("Loading undoHistory with index: " + index);
		if (undoList.size() > 0 && index >= 0 && index < undoList.size()) {
			Integer currValue = VibeComposerGUI.getComponentValue(undoList.get(index).getLeft());
			VibeComposerGUI.setComponent(undoList.get(index).getLeft(),
					undoList.get(index).getRight(), true);
			undoList.get(index).setValue(currValue);
			historyIndex = index;
			historyBox.setSelectedIndex(index);
		}
	}
}
