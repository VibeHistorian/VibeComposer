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

	public List<Double> regenerateDurations(int maxSameDurAllowed) {
		Random generator = new Random(randomSeed);
		durations = new ArrayList<>();
		double durationSum = 0;
		int sameDurCounter = 0;
		int lastDurIndex = Integer.MIN_VALUE;
		int retryCounter = 0;
		int maxRetry = 2;
		while (durationSum < durationLimit - 0.01) {
			double dur = MidiGenerator.Durations.SIXTEENTH_NOTE / 2.0;
			int chance = generator.nextInt(100);
			int chosenIndex = 0;
			boolean lastNote = false;
			for (int i = 0; i < durationPool.length; i++) {
				if (i < (durationPool.length - 1)
						&& (durationPool[i + 1] > durationLimit - durationSum)) {
					dur = durationLimit - durationSum;
					lastNote = true;
					break;
				}
				if (chance < durationWeights[i]) {
					dur = durationPool[i];
					chosenIndex = i;
					break;
				}
			}
			if (lastNote) {
				durationSum += dur;
				durations.add(dur);
				break;
			}

			if (lastDurIndex == chosenIndex) {
				sameDurCounter++;
			} else {
				lastDurIndex = chosenIndex;
				sameDurCounter = 0;
			}

			if (sameDurCounter < maxSameDurAllowed || chosenIndex == 0
					|| retryCounter == maxRetry) {
				durationSum += dur;
				durations.add(dur);
				if (retryCounter == maxRetry) {
					System.out.println("P A N I K");
					retryCounter = 0;
				}
			} else {
				retryCounter++;
			}

		}
		/*System.out.println("Duration lim: " + durationLimit + ", sum: "
				+ durations.stream().mapToDouble(e -> e).sum());*/
		return durations;
	}

	public static int[] normalizedCumulativeWeights(int[] weights) {
		int[] finalWeights = new int[weights.length];
		double total = 0;
		for (int w : weights) {
			total += w;
		}
		for (int i = 0; i < weights.length; i++) {
			double normalizedWeight = weights[i] * 100.0 / total;
			finalWeights[i] = (int) Math.round(normalizedWeight);
			if (i > 0) {
				finalWeights[i] += finalWeights[i - 1];
			}
		}
		finalWeights[weights.length - 1] = 100;
		return finalWeights;
	}
}
