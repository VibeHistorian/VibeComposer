package org.vibehistorian.vibecomposer.Enums;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

import org.vibehistorian.vibecomposer.OMNI;

@XmlType(name = "chordStretchType")
@XmlEnum
public enum ChordStretchType {
	NONE, FIXED, AT_MOST;

	private static final int[] weights = new int[] { 50, 75, 100 };

	public static ChordStretchType getWeighted(int value) {
		return OMNI.getWeightedValue(ChordStretchType.values(), value, weights);
	}
}
