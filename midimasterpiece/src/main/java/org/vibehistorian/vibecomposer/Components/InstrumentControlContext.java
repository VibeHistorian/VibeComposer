package org.vibehistorian.vibecomposer.Components;

import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.INST;

import java.util.List;

/** Window operations used by controls embedded in instrument panels. */
public interface InstrumentControlContext {
	List<InstPanel> getAffectedPanels(INST instrument);

	boolean canRegenerateOnChange();

	void regenerate();
}
