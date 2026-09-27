package org.vibehistorian.vibecomposer;

/** Instrument group order used by panel and generation workflows. */
public enum INST {
	MELODY(0),
	BASS(1),
	CHORD(2),
	ARP(3),
	DRUM(4);

	private final int index;

	INST(int index) {
		this.index = index;
	}

	public int getIndex() {
		return index;
	}

	public static INST fromIndex(int index) {
		for (INST instrument : values()) {
			if (instrument.index == index) {
				return instrument;
			}
		}
		throw new IllegalArgumentException("Unsupported instrument index: " + index);
	}
}
