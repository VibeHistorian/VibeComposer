package org.vibehistorian.vibecomposer.controllers;

import org.vibehistorian.vibecomposer.INST;
import org.vibehistorian.vibecomposer.Panels.InstPanel;
import org.vibehistorian.vibecomposer.Panels.SoloMuter;
import org.vibehistorian.vibecomposer.Panels.SoloMuter.State;
import org.vibehistorian.vibecomposer.PlaybackState;

import javax.sound.midi.Sequencer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Applies panel solo and mute state to the generated sequencer tracks. */
public final class SoloMuteController {
	public interface Context {
		List<? extends InstPanel> getPanels(INST instrument);
		boolean isInstrumentEnabled(INST instrument);
		SoloMuter getGlobalSoloMuter();
		List<SoloMuter> getGroupSoloMuters();
		void refreshScoreForSoloChange();
		void refreshScoreForMuteChange();
	}

	private final Context context;
	private boolean recalculationRequested;
	private boolean recalculationRequestedAfterSequenceGenerated;

	public SoloMuteController(Context context) {
		this.context = context;
	}

	public SoloMuter getGlobalSoloMuter() {
		return context.getGlobalSoloMuter();
	}

	public List<SoloMuter> getGroupSoloMuters() {
		return context.getGroupSoloMuters();
	}

	public void requestRecalculation() {
		recalculationRequested = true;
	}

	public void processRecalculationRequest(Runnable afterSoloMuterChange) {
		if (!recalculationRequested) {
			return;
		}
		recalculationRequested = false;
		unapplyTracks();
		reapplyTracks();
		afterSoloMuterChange.run();
	}

	public void requestRecalculationAfterSequenceGenerated() {
		recalculationRequestedAfterSequenceGenerated = true;
	}

	public boolean consumeRecalculationAfterSequenceGenerated() {
		boolean requested = recalculationRequestedAfterSequenceGenerated;
		recalculationRequestedAfterSequenceGenerated = false;
		return requested;
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

		for (INST instrument : INST.values()) {
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
		for (INST instrument : INST.values()) {
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
		boolean hasSoloSelection = context.getGlobalSoloMuter().soloState != State.OFF;
		for (INST instrument : INST.values()) {
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

	public void recalculatePanels() {
		for (INST instrumentType : INST.values()) {
			int instrument = instrumentType.getIndex();
			recalculateGroupSolo(instrument);
			recalculateGroupMute(instrument);
		}
		recalculateGlobals();
		requestRecalculationAfterSequenceGenerated();
	}

	public void recalculateGlobals() {
		boolean shouldSolo = false;
		boolean shouldMute = false;
		for (SoloMuter muter : context.getGroupSoloMuters()) {
			shouldSolo |= muter.soloState != State.OFF;
			shouldMute |= muter.muteState != State.OFF;
		}
		if (!shouldSolo) {
			context.getGlobalSoloMuter().unsolo();
		}
		if (!shouldMute) {
			context.getGlobalSoloMuter().unmute();
		}
	}

	public void recalculateGroupSolo(int instrument) {
		List<? extends InstPanel> panels = context.getPanels(INST.fromIndex(instrument));
		long soloCount = panels.stream()
				.filter(panel -> panel.getSoloMuter().soloState == State.FULL).count();
		SoloMuter groupMuter = context.getGroupSoloMuters().get(instrument);
		if (soloCount == 0) {
			groupMuter.unsolo();
		} else if (soloCount < panels.size()) {
			groupMuter.halfSolo();
		} else {
			groupMuter.solo();
		}
	}

	public void recalculateGroupMute(int instrument) {
		List<? extends InstPanel> panels = context.getPanels(INST.fromIndex(instrument));
		long muteCount = panels.stream()
				.filter(panel -> panel.getSoloMuter().muteState == State.FULL).count();
		SoloMuter groupMuter = context.getGroupSoloMuters().get(instrument);
		if (muteCount == 0) {
			groupMuter.unmute();
		} else if (muteCount < panels.size()) {
			groupMuter.halfMute();
		} else {
			groupMuter.mute();
		}
	}

	public void unsoloAllTracks() {
		for (SoloMuter groupMuter : context.getGroupSoloMuters()) {
			unsoloGroup(groupMuter);
		}
	}

	public void unsoloGroup(SoloMuter groupMuter) {
		groupMuter.unsolo();
		List<? extends InstPanel> panels = context.getPanels(INST.fromIndex(groupMuter.inst));
		for (InstPanel panel : panels) {
			panel.getSoloMuter().unsolo();
		}
		if (!sequenceReady()) {
			return;
		}
		for (InstPanel panel : panels) {
			PlaybackState.sequencer.setTrackSolo(panel.getSequenceTrack(), false);
		}
	}

	public void soloGroup(SoloMuter groupMuter) {
		groupMuter.solo();
		List<? extends InstPanel> panels = context.getPanels(INST.fromIndex(groupMuter.inst));
		for (InstPanel panel : panels) {
			panel.getSoloMuter().solo();
		}
		if (context.getGroupSoloMuters().stream()
				.filter(muter -> muter.soloState == State.FULL).count() == 5) {
			groupMuter.smParent.solo();
		} else {
			groupMuter.smParent.halfSolo();
		}
		if (!sequenceReady()) {
			return;
		}
		for (InstPanel panel : panels) {
			PlaybackState.sequencer.setTrackSolo(panel.getSequenceTrack(), true);
		}
	}

	public void unmuteAllTracks() {
		for (SoloMuter groupMuter : context.getGroupSoloMuters()) {
			unmuteGroup(groupMuter);
		}
	}

	public void unmuteGroup(SoloMuter groupMuter) {
		groupMuter.unmute();
		List<? extends InstPanel> panels = context.getPanels(INST.fromIndex(groupMuter.inst));
		for (InstPanel panel : panels) {
			panel.getSoloMuter().unmute();
		}
		if (!sequenceReady()) {
			return;
		}
		for (InstPanel panel : panels) {
			PlaybackState.sequencer.setTrackMute(panel.getSequenceTrack(), false);
		}
	}

	public void muteGroup(SoloMuter groupMuter) {
		groupMuter.mute();
		List<? extends InstPanel> panels = context.getPanels(INST.fromIndex(groupMuter.inst));
		for (InstPanel panel : panels) {
			panel.getSoloMuter().mute();
		}
		if (context.getGroupSoloMuters().stream()
				.filter(muter -> muter.muteState == State.FULL).count() == 5) {
			groupMuter.smParent.mute();
		} else {
			groupMuter.smParent.halfMute();
		}
		if (!sequenceReady()) {
			return;
		}
		for (InstPanel panel : panels) {
			PlaybackState.sequencer.setTrackMute(panel.getSequenceTrack(), true);
		}
	}

	public void onSoloToggled(SoloMuter muter) {
		if (muter.soloState != State.OFF) {
			muter.unsolo();
			if (muter.type == SoloMuter.Type.SINGLE) {
				recalculateGroupSolo(muter.inst);
				recalculateGlobals();
			} else if (muter.type == SoloMuter.Type.GROUP) {
				unsoloGroup(muter);
				recalculateGlobals();
			} else {
				unsoloAllTracks();
			}
		} else if (muter.type == SoloMuter.Type.SINGLE) {
			muter.solo();
			muter.smParent.solo();
			muter.smParent.smParent.solo();
		} else if (muter.type == SoloMuter.Type.GROUP) {
			soloGroup(muter);
		}

		if (sequenceReady()) {
			requestRecalculation();
		} else {
			requestRecalculationAfterSequenceGenerated();
		}
		context.refreshScoreForSoloChange();
	}

	public void onMuteToggled(SoloMuter muter) {
		if (muter.muteState != State.OFF) {
			muter.unmute();
			if (muter.type == SoloMuter.Type.SINGLE) {
				recalculateGroupMute(muter.inst);
				recalculateGlobals();
			} else if (muter.type == SoloMuter.Type.GROUP) {
				unmuteGroup(muter);
				recalculateGlobals();
			} else {
				unmuteAllTracks();
			}
		} else if (muter.type == SoloMuter.Type.SINGLE) {
			muter.mute();
			muter.smParent.mute();
			muter.smParent.smParent.mute();
		} else if (muter.type == SoloMuter.Type.GROUP) {
			muteGroup(muter);
		}

		if (sequenceReady()) {
			requestRecalculation();
		} else {
			requestRecalculationAfterSequenceGenerated();
		}
		context.refreshScoreForMuteChange();
	}

	public boolean isSingleSolo() {
		int groupIndex = -1;
		for (int i = 0; i < context.getGroupSoloMuters().size(); i++) {
			if (context.getGroupSoloMuters().get(i).soloState != State.OFF) {
				if (groupIndex >= 0) {
					return false;
				}
				groupIndex = i;
			}
		}
		if (groupIndex < 0) {
			return false;
		}
		boolean foundSolo = false;
		for (InstPanel panel : context.getPanels(INST.fromIndex(groupIndex))) {
			if (panel.getSoloMuter().soloState != State.OFF) {
				if (foundSolo) {
					return false;
				}
				foundSolo = true;
			}
		}
		return foundSolo;
	}

	private static boolean sequenceReady() {
		Sequencer sequencer = PlaybackState.sequencer;
		return sequencer != null && sequencer.isOpen() && sequencer.getSequence() != null;
	}
}
