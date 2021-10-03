package org.vibehistorian.vibecomposer;

import java.util.List;

public class MelodyBlock {
	public MelodyBlock(List<Integer> notes, List<Double> durations, boolean inverse) {
		super();
		this.notes = notes;
		this.durations = durations;
		this.inverse = inverse;
	}

	public List<Integer> notes;
	public List<Double> durations;
	public boolean inverse;

}
