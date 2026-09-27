package org.vibehistorian.vibecomposer;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.Components.ScrollComboBox;

import javax.swing.JTabbedPane;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class UndoManager {
	public List<List<Pair<Component, Integer>>> undoList = new ArrayList<>();
	public ScrollComboBox<String> historyBox = new ScrollComboBox<>(false);
	public int historyIndex = 0;
	private boolean recordingEvents = false;
	private static final int HISTORY_LIMIT = 100;

	public UndoManager() {
		historyBox.setFunc(e -> {
			if (historyIndex != historyBox.getSelectedIndex()) {
				loadFromHistory(historyBox.getSelectedIndex());
			}
		});
	}

	public void updateHistoryBox() {
		historyBox.removeAllItems();
		for (int i = 0; i < undoList.size(); i++) {
			historyBox.addItem(i + " (" + undoList.get(i).stream().count() + " changes)");
		}
		historyBox.setSelectedIndex(historyBox.getItemCount() - 1);
	}

	public void saveToHistory(List<Component> cs) {
		if (!recordingEvents) {
			return;
		}
		if (historyIndex >= 0 && historyIndex + 1 < undoList.size() && undoList.size() > 0) {
			undoList = undoList.subList(0, historyIndex + 1);
		}

		save(cs.stream().map(e -> MutablePair.of(e, UIComponentState.getValue(e)))
				.collect(Collectors.toList()));
	}

	public void saveToHistory(Component c) {
		if (!recordingEvents) {
			return;
		}
		saveToHistory(c, UIComponentState.getValue(c));
	}

	public void saveToHistory(Component c, Integer val) {
		if (!recordingEvents) {
			return;
		}

		if (historyIndex >= 0 && historyIndex + 1 < undoList.size() && undoList.size() > 0) {
			undoList = undoList.subList(0, historyIndex + 1);
		}

		save(Collections.singletonList(MutablePair.of(c, val)));
	}

	private void save(List<Pair<Component, Integer>> historyItems) {
		undoList.add(historyItems);
		if (undoList.size() >= HISTORY_LIMIT) {
			undoList = undoList.subList(HISTORY_LIMIT / 5, HISTORY_LIMIT);
			LG.i("Cleaned items from Undo history: " + HISTORY_LIMIT / 5);
		}
		historyIndex = undoList.size() - 1;
		updateHistoryBox();
	}

	public void undo() {
		loadFromHistory(historyIndex - 1);
		//historyIndex = Math.max(0, historyIndex - 1);
	}

	public void redo() {
		loadFromHistory(historyIndex + 1);
		//historyIndex = Math.min(undoList.size(), historyIndex + 1);
	}

	public void setRecordingEvents(boolean state) {
		recordingEvents = state;
	}

	public void loadFromHistory(int index) {
		/*if (historyIndex == index) {
			return;
		}*/
		LG.i("Loading undoHistory with index: " + index);
		if (undoList.size() > 0 && index >= 0 && index < undoList.size()) {
			undoList.get(index).forEach(e -> {
				if (e.getLeft() instanceof JTabbedPane) {
					setRecordingEvents(false);
					UIComponentState.setValue(e.getLeft(), e.getRight(), true);
					setRecordingEvents(true);
				} else {
					UIComponentState.setValue(e.getLeft(), e.getRight(), true);
				}
				//e.setValue(currValue);
			});

			historyIndex = index;
			historyBox.setSelectedIndex(index);
		}
	}
}
