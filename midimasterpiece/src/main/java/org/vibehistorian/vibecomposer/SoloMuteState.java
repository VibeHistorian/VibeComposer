package org.vibehistorian.vibecomposer;

import org.vibehistorian.vibecomposer.Panels.SoloMuter;

import java.util.List;

/** Owns solo and mute state shared across instrument panels and playback. */
public final class SoloMuteState {
	private SoloMuteState() {
	}

	public static SoloMuter globalSoloMuter;
	public static List<SoloMuter> groupSoloMuters;
}
