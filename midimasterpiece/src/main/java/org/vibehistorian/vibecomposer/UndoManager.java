package org.vibehistorian.vibecomposer;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;

public class UndoManager {
	public static List<List<Pair<Component, Integer>>> undoList = new ArrayList<>();
	public static ScrollComboBox<String> historyBox = new ScrollComboBox<>(false);
	public static int historyIndex = 0;
	public static boolean recordingEvents = false;
	public static final int HISTORY_LIMIT = 100;

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
			historyBox.addItem(i + " (" + undoList.get(i).stream().count() + " changes)");
		}
		historyBox.setSelectedIndex(historyBox.getItemCount() - 1);
	}

	public static void saveToHistory(List<Component> cs) {
		if (!recordingEvents) {
			return;
		}
		if (historyIndex >= 0 && historyIndex + 1 < undoList.size() && undoList.size() > 0) {
			undoList = undoList.subList(0, historyIndex + 1);
		}

		save(cs.stream().map(e -> MutablePair.of(e, VibeComposerGUI.getComponentValue(e)))
				.collect(Collectors.toList()));
	}

	public static void saveToHistory(Component c) {
		if (!recordingEvents) {
			return;
		}
		saveToHistory(c, VibeComposerGUI.getComponentValue(c));
	}

	public static void saveToHistory(Component c, Integer val) {
		if (!recordingEvents) {
			return;
		}

		if (historyIndex >= 0 && historyIndex + 1 < undoList.size() && undoList.size() > 0) {
			undoList = undoList.subList(0, historyIndex + 1);
		}

		save(Collections.singletonList(MutablePair.of(c, val)));
	}

	private static void save(List<Pair<Component, Integer>> historyItems) {
		undoList.add(historyItems);
		if (undoList.size() >= HISTORY_LIMIT) {
			undoList = undoList.subList(HISTORY_LIMIT / 5, HISTORY_LIMIT);
			LG.i("Cleaned items from Undo history: " + HISTORY_LIMIT / 5);
		}
		historyIndex = undoList.size() - 1;
		updateHistoryBox();
	}

	public static void undo() {
		loadFromHistory(historyIndex);
		historyIndex = Math.max(0, historyIndex - 1);
	}

	public static void redo() {
		loadFromHistory(historyIndex);
		historyIndex = Math.min(undoList.size(), historyIndex + 1);
	}

	public static void loadFromHistory(int index) {
		/*if (historyIndex == index) {
			return;
		}*/
		LG.i("Loading undoHistory with index: " + index);
		if (undoList.size() > 0 && index >= 0 && index < undoList.size()) {
			undoList.get(index).forEach(e -> {
				Integer currValue = VibeComposerGUI.getComponentValue(e.getLeft());
				VibeComposerGUI.setComponent(e.getLeft(), e.getRight(), true);
				e.setValue(currValue);
			});

			historyIndex = index;
			historyBox.setSelectedIndex(index);
		}
	}
}
