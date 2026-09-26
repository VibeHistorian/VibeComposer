package org.vibehistorian.vibecomposer.Components;

import org.vibehistorian.vibecomposer.Panels.InstPanel;

import java.util.List;

/** Window operations used by controls embedded in instrument panels. */
public interface InstrumentControlContext {
	List<InstPanel> getAffectedPanels(int instrument);

	boolean canRegenerateOnChange();

	void regenerate();
}
