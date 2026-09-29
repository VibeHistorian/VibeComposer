package org.vibehistorian.vibecomposer.Components;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.LG;
import org.vibehistorian.vibecomposer.MidiUtils;
import org.vibehistorian.vibecomposer.MidiUtils.ScaleMode;
import org.vibehistorian.vibecomposer.OMNI;
import org.vibehistorian.vibecomposer.Helpers.PhraseNotes;

import jm.music.data.Phrase;

public class MelodyMidiDropPane extends MidiDropPane {

	private static final long serialVersionUID = 6132531225113455208L;

	public static Phrase userMelody = null;
	public static Phrase userMelodyCandidate = null;

	private static Function<Phrase, PhraseNotes> createMelodyMidiConverter(
			Consumer<List<String>> scaleModeOptions) {
		return e -> {

		List<Pair<ScaleMode, Integer>> detectionResults = MidiUtils.detectKeyAndMode(e, null,
				false, 0);

		if (detectionResults == null) {
			LG.d("Melody uses unknown key, skipped!");
			return null;
		}

		MelodyMidiDropPane.userMelodyCandidate = e;
		List<String> detectedModes = new ArrayList<>();
		detectedModes.add(OMNI.EMPTYCOMBO);
		for (Pair<ScaleMode, Integer> p : detectionResults) {
			detectedModes.add(p.getLeft().toString() + "," + p.getRight());
		}
		scaleModeOptions.accept(detectedModes);
		return new PhraseNotes(userMelodyCandidate);
		};
	}

	public MelodyMidiDropPane(Consumer<List<String>> scaleModeOptions) {
		super(createMelodyMidiConverter(scaleModeOptions));
		getMessage().setText(" * * Drag'n'Drop MIDI Here * * ");
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(200, 35);
	}

}
