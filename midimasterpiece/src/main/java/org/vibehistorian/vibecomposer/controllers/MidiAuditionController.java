package org.vibehistorian.vibecomposer.controllers;

import jm.music.data.Note;
import jm.music.data.Part;
import jm.music.data.Phrase;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.GUIConfig;
import org.vibehistorian.vibecomposer.INST;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import org.vibehistorian.vibecomposer.PlaybackState;
import org.vibehistorian.vibecomposer.Section;
import org.vibehistorian.vibecomposer.gui.ArrangementGUI;
import org.vibehistorian.vibecomposer.gui.ExtraSettingsGUI;
import org.vibehistorian.vibecomposer.gui.MelodyGUI;
import org.vibehistorian.vibecomposer.gui.ScoreGUI;

import javax.sound.midi.InvalidMidiDataException;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.vibehistorian.vibecomposer.Constants.instNames;
import static org.vibehistorian.vibecomposer.PlaybackState.lastPlayedMs;
import static org.vibehistorian.vibecomposer.PlaybackState.sequencer;

/** Resolves and plays notes requested by the keyboard and MIDI editors. */
public final class MidiAuditionController {
	private final InstrumentPanelController panelController;
	private final MidiDeviceController midiDeviceController;
	private final ScoreGUI scoreGUI;
	private final MelodyGUI melodyGUI;
	private final ArrangementGUI arrangementGUI;
	private final Supplier<MidiUtils.ScaleMode> scaleMode;
	private final Supplier<GUIConfig> guiConfig;

	public MidiAuditionController(InstrumentPanelController panelController,
			MidiDeviceController midiDeviceController, ScoreGUI scoreGUI, MelodyGUI melodyGUI,
			ArrangementGUI arrangementGUI,
			Supplier<MidiUtils.ScaleMode> scaleMode, Supplier<GUIConfig> guiConfig) {
		this.panelController = panelController;
		this.midiDeviceController = midiDeviceController;
		this.scoreGUI = scoreGUI;
		this.melodyGUI = melodyGUI;
		this.arrangementGUI = arrangementGUI;
		this.scaleMode = scaleMode;
		this.guiConfig = guiConfig;
	}

	public void playNextNote(int keyboardTranspose, int velocity, INST instrument, int panelOrder) {
		INST activeInstrument = instrument == null ? INST.MELODY : instrument;
		int partOrder = panelOrder < 1 ? 1 : panelOrder;
		int part = activeInstrument.getIndex();
		LG.i(keyboardTranspose + ", " + velocity + ", " + part + ", " + partOrder);
		GUIConfig config = guiConfig.get();

		MelodyPart configuredMelody = activeInstrument == INST.MELODY
				? config.getMelodyParts().get(partOrder - 1) : null;
		Phrase nextNoteMelody = configuredMelody != null && configuredMelody.getCustomMidi() != null
				? configuredMelody.getCustomMidi().makePhrase() : null;
		int transpose = keyboardTranspose;
		if (nextNoteMelody == null) {
			LG.d("No custom melody to play!");
			nextNoteMelody = melodyGUI.getUserMelody();
			if (nextNoteMelody == null) {
				LG.d("No user melody/midi to play!");
				Part scorePart = scoreGUI.getScorePanel() == null || scoreGUI.getScorePanel().score == null
						? null : scoreGUI.getScorePanel().score.getPart(instNames[part] + "" + (partOrder - 1));
				nextNoteMelody = scorePart == null ? null : scorePart.getPhrase(0);
				if (nextNoteMelody == null) {
					LG.i("No actual melody to play!");
					return;
				}
				transpose += -1 * (panelController.getPanelByOrder(activeInstrument, partOrder).getTranspose()
						+ scoreGUI.getTranspose());
			}
		}

		int nextNoteIndex = PlaybackState.getNextNoteIndex(part, partOrder) % nextNoteMelody.size();
		Note note;
		while ((note = nextNoteMelody.getNote(nextNoteIndex++)) != null) {
			if (note.getPitch() >= 1) {
				playNote(note.getPitch() + transpose,
					(int) (note.getDuration() * 1000 * 60 / config.getBpm()), velocity,
						activeInstrument, partOrder, arrangementGUI.getActualArrangement().getSections().get(0), true);
				break;
			}
		}
	}

	public void playNote(int pitch, int durationMs, int velocity, INST instrument, int panelOrder,
						 Section section, boolean overrideLastPlayed) {
		if (sequencer == null || !sequencer.isOpen() || pitch < 0
				|| (!overrideLastPlayed && System.currentTimeMillis() - lastPlayedMs < 100)) {
			return;
		}

		InstPanel panel = panelController.getPanelByOrder(instrument, panelOrder);
		Integer trackNum = panel.getSequenceTrack();
		if (trackNum == null || trackNum < 0) {
			return;
		}
		try {
			if (instrument.getIndex() < 4 && ExtraSettingsGUI.transposeNotePreview.isSelected()) {
				Pair<MidiUtils.ScaleMode, Integer> scaleKey = arrangementGUI.keyChangeAt(
						arrangementGUI.getActualArrangement().getSections().indexOf(section), scaleMode.get());
				int extraTranspose = instrument != INST.MELODY ? panel.getTranspose() : 0;
				List<Note> notes = Collections.singletonList(new Note(
						instrument != INST.MELODY ? pitch : pitch + panel.getTranspose(),
						durationMs / 1000.0));
				if (scaleKey != null) {
					boolean snapToScale = scaleKey.getLeft() != MidiUtils.ScaleMode.IONIAN
							|| ExtraSettingsGUI.transposedNotesForceScale.isSelected();
					MidiUtils.transposeNotes(notes, MidiUtils.ScaleMode.IONIAN.noteAdjustScale,
							scaleKey.getLeft().noteAdjustScale, snapToScale);
					extraTranspose += scaleKey.getRight();
				}

				pitch = notes.get(0).getPitch() + scoreGUI.getTranspose() + extraTranspose
						+ section.getTransposeVariation(instrument.getIndex(), panelOrder);

				if (pitch < 0 || pitch > 127) {
					LG.d("Pitch too high to play: " + pitch);
					return;
				}
			}

			midiDeviceController.playNote(panel.getMidiChannel() - 1, pitch, velocity, durationMs);
		} catch (InvalidMidiDataException e) {
			LG.e(e);
		}
	}
}
