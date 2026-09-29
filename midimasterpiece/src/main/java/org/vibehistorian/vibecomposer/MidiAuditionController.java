package org.vibehistorian.vibecomposer;

import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.Components.MelodyMidiDropPane;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Parts.InstPart;
import org.vibehistorian.vibecomposer.Parts.MelodyPart;
import jm.music.data.Note;
import jm.music.data.Part;
import jm.music.data.Phrase;

import javax.sound.midi.InvalidMidiDataException;
import java.util.Collections;
import java.util.List;

import static org.vibehistorian.vibecomposer.ApplicationSessionState.guiConfig;
import static org.vibehistorian.vibecomposer.Constants.instNames;
import static org.vibehistorian.vibecomposer.PlaybackState.*;

/** Resolves and plays notes requested by the keyboard and MIDI editors. */
public final class MidiAuditionController {
	private final InstrumentPanelController panelController;
	private final MidiDeviceController midiDeviceController;

	public MidiAuditionController(InstrumentPanelController panelController,
			MidiDeviceController midiDeviceController) {
		this.panelController = panelController;
		this.midiDeviceController = midiDeviceController;
	}

	public void playNextNote(int keyboardTranspose, int velocity, INST instrument, int panelOrder) {
		INST activeInstrument = instrument == null ? INST.MELODY : instrument;
		int partOrder = panelOrder < 1 ? 1 : panelOrder;
		int part = activeInstrument.getIndex();
		LG.i(keyboardTranspose + ", " + velocity + ", " + part + ", " + partOrder);

		MelodyPart configuredMelody = activeInstrument == INST.MELODY
				? guiConfig.getMelodyParts().get(partOrder - 1) : null;
		Phrase nextNoteMelody = configuredMelody != null && configuredMelody.getCustomMidi() != null
				? configuredMelody.getCustomMidi().makePhrase() : null;
		int transpose = keyboardTranspose;
		if (nextNoteMelody == null) {
			LG.d("No custom melody to play!");
			nextNoteMelody = MelodyMidiDropPane.userMelody;
			if (nextNoteMelody == null) {
				LG.d("No user melody/midi to play!");
				Part scorePart = ScoreGUI.scorePanel == null || ScoreGUI.scorePanel.score == null
						? null : ScoreGUI.scorePanel.score.getPart(instNames[part] + "" + (partOrder - 1));
				nextNoteMelody = scorePart == null ? null : scorePart.getPhrase(0);
				if (nextNoteMelody == null) {
					LG.i("No actual melody to play!");
					return;
				}
				transpose += -1 * (panelController.getPanelByOrder(activeInstrument, partOrder).getTranspose()
						+ ScoreGUI.transposeScore.getInt());
			}
		}

		int nextNoteIndex = PlaybackState.getNextNoteIndex(part, partOrder) % nextNoteMelody.size();
		Note note;
		while ((note = nextNoteMelody.getNote(nextNoteIndex++)) != null) {
			if (note.getPitch() >= 1) {
				playNote(note.getPitch() + transpose,
						(int) (note.getDuration() * 1000 * 60 / guiConfig.getBpm()), velocity,
						activeInstrument, partOrder, ArrangementGUI.actualArrangement.getSections().get(0), true);
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
				Pair<MidiUtils.ScaleMode, Integer> scaleKey = ArrangementGUI.keyChangeAt(
						ArrangementGUI.actualArrangement.getSections().indexOf(section));
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

				pitch = notes.get(0).getPitch() + ScoreGUI.transposeScore.getInt() + extraTranspose
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
