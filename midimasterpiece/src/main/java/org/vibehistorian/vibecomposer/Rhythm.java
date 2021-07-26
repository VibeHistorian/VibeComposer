package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Rhythm {

	private double[] durationPool = null;
	private int[] durationWeights = null;
	private int randomSeed = 0;
	private double durationLimit = MidiGenerator.Durations.HALF_NOTE;

	private List<Double> durations;


	public List<Double> getDurations() {
		return durations;
	}

	public void setDurations(List<Double> durations) {
		this.durations = durations;
	}

	public Rhythm(int randomSeed, double durationLimit, double[] durationPool,
			int[] durationWeights) {
		this(randomSeed, durationLimit);
		this.durationPool = durationPool;
		this.durationWeights = durationWeights;

	}

	public Rhythm(int randomSeed, double durationLimit) {
		this.randomSeed = randomSeed;
		this.durationLimit = durationLimit;
	}

	public List<Double> regenerateDurations() {
		Random generator = new Random(randomSeed);
		durations = new ArrayList<>();
		double durationSum = 0;
		while (durationSum < durationLimit - 0.01) {
			double dur = MidiGenerator.Durations.SIXTEENTH_NOTE / 2.0;
			int chance = generator.nextInt(100);
			for (int i = 0; i < durationPool.length; i++) {
				if (i < (durationPool.length - 1)
						&& (durationPool[i + 1] > durationLimit - durationSum)) {
					dur = durationLimit - durationSum;
					break;
				}
				if (chance < durationWeights[i]) {
					dur = durationPool[i];
					break;
				}
			}
			durationSum += dur;
			durations.add(dur);
		}
		/*System.out.println("Duration lim: " + durationLimit + ", sum: "
				+ durations.stream().mapToDouble(e -> e).sum());*/
		return durations;
	}

}
