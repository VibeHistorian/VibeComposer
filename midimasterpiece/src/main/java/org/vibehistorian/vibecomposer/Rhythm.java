package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Rhythm {

	private double[] durationPool = null;
	private int[] durationWeights = null;
	private int randomSeed = 0;
	private double durationLimit = 2.0;

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
		while (durationSum < durationLimit) {
			double dur = 0.125 * MidiGenerator.noteMultiplier;
			int chance = generator.nextInt(100);
			for (int i = 0; i < durationPool.length; i++) {
				if (i < (durationPool.length - 1)
						&& (durationPool[i + 1] > durationLimit - durationSum)) {
					dur = durationPool[i];
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
		return durations;
	}

}
