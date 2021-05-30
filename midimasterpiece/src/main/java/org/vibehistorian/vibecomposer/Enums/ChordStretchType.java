package org.vibehistorian.vibecomposer.Enums;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "chordStretchType")
@XmlEnum
public enum ChordStretchType {
	NONE, FIXED, AT_MOST;
	
	private static final int[] weights = new int[] { 50, 75, 100 };
	
	public static ChordStretchType getWeighted(int value) {
		for (int i = 0; i < weights.length; i++) {
			if (value < weights[i]) {
				return ChordStretchType.values()[i];
			}
		}
		throw new IllegalArgumentException("ChordSpanFill error: Value higher than 99!");
	}
}
