package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.SoloMuter.State;

import javax.sound.midi.Sequencer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Applies panel solo and mute state to the generated sequencer tracks. */
public final class SoloMuteController {
	public interface Context {
		List<? extends InstPanel> getPanels(int instrument);
		boolean isInstrumentEnabled(int instrument);
	}

	private final Context context;

	public SoloMuteController(Context context) {
		this.context = context;
	}

	public void unapplyTracks() {
		if (!sequenceReady()) {
			return;
		}

		Sequencer sequencer = PlaybackState.sequencer;
		sequencer.setTrackSolo(0, false);
		sequencer.setTrackMute(0, false);

		Set<Integer> tracksToUnsolo = new HashSet<>();
		Set<Integer> tracksToUnmute = new HashSet<>();
		for (int track = 1; track < sequencer.getSequence().getTracks().length; track++) {
			tracksToUnsolo.add(track);
			tracksToUnmute.add(track);
		}

		for (int instrument = 0; instrument < 5; instrument++) {
			if (!context.isInstrumentEnabled(instrument)) {
				continue;
			}
			for (InstPanel panel : context.getPanels(instrument)) {
				Integer track = panel.getSequenceTrack();
				if (track < 0 || panel.getMuteInst()) {
					continue;
				}
				if (panel.getSoloMuter().soloState == State.FULL) {
					tracksToUnsolo.remove(track);
				} else if (panel.getSoloMuter().muteState == State.FULL) {
					tracksToUnmute.remove(track);
				}
			}
		}

		tracksToUnsolo.forEach(track -> sequencer.setTrackSolo(track, false));
		tracksToUnmute.forEach(track -> sequencer.setTrackMute(track, false));
	}

	public void reapplyTracks() {
		if (!sequenceReady()) {
			return;
		}

		Sequencer sequencer = PlaybackState.sequencer;
		for (int instrument = 0; instrument < 5; instrument++) {
			for (InstPanel panel : context.getPanels(instrument)) {
				if (panel.getSequenceTrack() < 0) {
					panel.getSoloMuter().unsolo();
					panel.getSoloMuter().unmute();
				} else {
					sequencer.setTrackSolo(panel.getSequenceTrack(),
							panel.getSoloMuter().soloState == State.FULL);
					sequencer.setTrackMute(panel.getSequenceTrack(),
							panel.getSoloMuter().muteState == State.FULL);
				}
			}
		}
	}

	public void toggleExclude() {
		boolean hasSoloSelection = SoloMuteState.globalSoloMuter.soloState != State.OFF;
		for (int instrument = 0; instrument < 5; instrument++) {
			for (InstPanel panel : context.getPanels(instrument)) {
				if (hasSoloSelection && panel.getSoloMuter().soloState == State.OFF) {
					panel.setMuteInst(true);
				} else {
					if (hasSoloSelection) {
						panel.getSoloMuter().unsolo();
					}
					panel.setMuteInst(false);
				}
			}
		}
	}

	private static boolean sequenceReady() {
		Sequencer sequencer = PlaybackState.sequencer;
		return sequencer != null && sequencer.isOpen() && sequencer.getSequence() != null;
	}
}
