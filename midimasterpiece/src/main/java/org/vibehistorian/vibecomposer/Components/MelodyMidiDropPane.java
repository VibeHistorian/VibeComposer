package org.vibehistorian.vibecomposer.Components;

import java.awt.Dimension;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.VibeComposerGUI;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;

import jm.music.data.Phrase;

public class MelodyMidiDropPane extends MidiDropPane {

	private static final long serialVersionUID = 6132531225113455208L;

	public static Phrase userMelody = null;
	public static Phrase userMelodyCandidate = null;

	public final static Function<Phrase, PhraseNotes> melodyMidiConverter = e -> {

		List<Pair<ScaleMode, Integer>> detectionResults = MidiUtils.detectKeyAndMode(e, null,
				false);

		if (detectionResults == null) {
			LG.d("Melody uses unknown key, skipped!");
			return null;
		}

		MelodyMidiDropPane.userMelodyCandidate = e;
		VibeComposerGUI.userMelodyScaleModeSelect.removeAllItems();
		VibeComposerGUI.userMelodyScaleModeSelect.addItem(OMNI.EMPTYCOMBO);
		for (Pair<ScaleMode, Integer> p : detectionResults) {
			VibeComposerGUI.userMelodyScaleModeSelect
					.addItem(p.getLeft().toString() + "," + p.getRight());
		}
		return new PhraseNotes(userMelodyCandidate);
	};

	public MelodyMidiDropPane() {
		super(melodyMidiConverter);
		getMessage().setText(" * * Drag'n'Drop MIDI Here * * ");
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(200, 35);
	}

}
