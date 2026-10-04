package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.Popups.MidiEditPopup;
import org.vibehistorian.vibecomposer.generation.MidiGenerator;

import java.util.List;

/** Owns the active MIDI editor and its selected arrangement section for one window. */
public final class MidiEditorSession {
	private final MidiEditPopup.Context context;
	private MidiEditPopup currentPopup;
	private MidiGenerator midiGenerator;
	private int sectionIndex = -1;

	public MidiEditorSession(MidiEditPopup.Context context) {
		this.context = context;
	}

	public void setMidiGenerator(MidiGenerator midiGenerator) {
		this.midiGenerator = midiGenerator;
		if (midiGenerator != null) {
			ApplicationSessionState.setActiveMidiTiming(midiGenerator.getTiming());
		}
	}

	public MidiGenerator getMidiGenerator() {
		return midiGenerator;
	}

	public void open(Section section, int instrument, int panelOrder, int sectionIndex) {
		currentPopup = new MidiEditPopup(context, section, instrument, panelOrder);
		currentPopup.setSec(section);
		this.sectionIndex = sectionIndex;
	}

	public boolean isVisible() {
		return currentPopup != null && currentPopup.isVisible();
	}

	public void saveNotesBeforeCompose() {
		if (isVisible()) {
			currentPopup.saveNotes(false);
		}
	}

	public void refreshAfterCompose(List<Section> sections) {
		if (!isVisible()) {
			return;
		}
		if (sectionIndex < 0 || sectionIndex >= sections.size()) {
			currentPopup.close();
			currentPopup = null;
			sectionIndex = -1;
			return;
		}
		currentPopup.setup(sections.get(sectionIndex));
	}
}
