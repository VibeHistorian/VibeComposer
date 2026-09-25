package org.vibehistorian.vibecomposer;

import java.awt.Image;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;

/** Caches image assets shared by the application UI. */
public final class GUIAssets {
	private static final String[] SECTION_VAR_ICON_NAMES = { "v0_skipChord.png",
			"v1_swapChords.png", "v2_swapMelody.png", "v3_melodySpeed.png", "v4_keyChange.png" };
	private static final String[] SECTION_TRANSITION_ICON_NAMES = { "v5_transUp.png",
			"v6_transDown.png", "v7_transCut.png", "v8_halvedTempo.png" };
	private static final String[] LOCK_COMPONENT_ICON_NAMES = { "lock.png", "toggle_lock.png",
			"lock_white.png", "toggle_lock_white.png" };

	public static final List<Image> SECTION_VARIATIONS_ICONS = new ArrayList<>();
	public static final List<Image> SECTION_TRANSITION_ICONS = new ArrayList<>();
	public static final List<Image> LOCK_COMPONENT_ICONS = new ArrayList<>();

	private GUIAssets() {
	}

	public static void load() {
		loadIcons(SECTION_VAR_ICON_NAMES, SECTION_VARIATIONS_ICONS, "/icons/sectionvars/", 15);
		loadIcons(SECTION_TRANSITION_ICON_NAMES, SECTION_TRANSITION_ICONS, "/icons/transitions/", 15);
		loadIcons(LOCK_COMPONENT_ICON_NAMES, LOCK_COMPONENT_ICONS, "/icons/", 8);
	}

	private static void loadIcons(String[] names, List<Image> destination, String resourcePath,
			int size) {
		for (String name : names) {
			Image source = new ImageIcon(GUIAssets.class.getResource(resourcePath + name)).getImage();
			Image scaled = source.getScaledInstance(size, size, Image.SCALE_SMOOTH);
			destination.add(new ImageIcon(scaled).getImage());
		}
	}
}
