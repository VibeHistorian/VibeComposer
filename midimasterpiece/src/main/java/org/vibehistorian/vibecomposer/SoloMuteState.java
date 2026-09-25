package org.vibehistorian.vibecomposer;

import java.util.List;
import java.util.Map;

import org.vibehistorian.vibecomposer.Panels.SoloMuter;

/** Owns solo and mute state shared across instrument panels and playback. */
public final class SoloMuteState {
	private SoloMuteState() {
	}

	public static SoloMuter globalSoloMuter;
	public static List<SoloMuter> groupSoloMuters;
	public static boolean needToRecalculateSoloMuters;
	public static boolean needToRecalculateSoloMutersAfterSequenceGenerated;
	public static Map<Integer, SoloMuter> cpSm;
	public static Map<Integer, SoloMuter> apSm;
	public static Map<Integer, SoloMuter> dpSm;
}
