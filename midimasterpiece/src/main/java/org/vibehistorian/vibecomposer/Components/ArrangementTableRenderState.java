package org.vibehistorian.vibecomposer.Components;

import org.apache.commons.lang3.tuple.Triple;
import org.vibehistorian.vibecomposer.Arrangement;

/** Snapshot of arrangement data and interaction state needed to paint an arrangement table cell. */
public final class ArrangementTableRenderState {
	private final Arrangement arrangement;
	private final boolean copyDragging;
	private final Triple<Integer, Integer, Integer> copyDraggingOrigin;
	private final Triple<Integer, Integer, Integer> highlightedCell;
	private final boolean mousePointPresent;

	public ArrangementTableRenderState(Arrangement arrangement, boolean copyDragging,
			Triple<Integer, Integer, Integer> copyDraggingOrigin,
			Triple<Integer, Integer, Integer> highlightedCell, boolean mousePointPresent) {
		this.arrangement = arrangement;
		this.copyDragging = copyDragging;
		this.copyDraggingOrigin = copyDraggingOrigin;
		this.highlightedCell = highlightedCell;
		this.mousePointPresent = mousePointPresent;
	}

	public Arrangement getArrangement() {
		return arrangement;
	}

	public boolean isCopyDragging() {
		return copyDragging;
	}

	public Triple<Integer, Integer, Integer> getCopyDraggingOrigin() {
		return copyDraggingOrigin;
	}

	public Triple<Integer, Integer, Integer> getHighlightedCell() {
		return highlightedCell;
	}

	public boolean hasMousePoint() {
		return mousePointPresent;
	}
}
