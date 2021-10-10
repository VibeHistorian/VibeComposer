package org.vibehistorian.vibecomposer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Rhythm {

	private double[] durationPool = null;
	private int[] durationWeights = null;
	private int randomSeed = 0;
	private double durationLimit = MidiGenerator.Durations.WHOLE_NOTE;

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
		if (durationPool.length != durationWeights.length) {
			throw new IllegalArgumentException("Mismatching duration setting lengths!");
		}
		this.durationPool = durationPool;
		this.durationWeights = durationWeights;

	}

	public Rhythm(int randomSeed, double durationLimit) {
		this.randomSeed = randomSeed;
		this.durationLimit = durationLimit;
	}

	public List<Double> makeDurations(int durCount, double minDuration) {

		Random generator = new Random(randomSeed);
		durations = new ArrayList<>();
		double durationSum = 0;
		int maximum = durationPool.length;
		int remainingNotes = durCount;
		//System.out.println("Weights: " + StringUtils.join(durationWeights, ','));
		int weightAdjust = 0;
		while (remainingNotes > 0) {
			double dur = durationPool[0];
			int chance = generator.nextInt(100);
			double remainingDuration = durationLimit - durationSum;
			double minimumRemainingDuration = remainingNotes * dur;
			double maximumAllowedNoteDuration = remainingDuration - minimumRemainingDuration + dur
					+ MidiGenerator.DBL_ERR;
			for (int i = maximum - 1; i >= 0; i--) {
				if (durationPool[i] > maximumAllowedNoteDuration) {
					maximum = i;
					if (i > 1) {
						weightAdjust += (durationWeights[i] - durationWeights[i - 1]) / (i - 1);
					}
					//System.out.print(" Max: " + i);
				} else {
					break;
				}
			}
			boolean lastNote = false;
			for (int i = 0; i < maximum; i++) {
				if ((i < maximum - 1) && (durationPool[i + 1] > durationLimit - durationSum)) {
					dur = durationLimit - durationSum;
					lastNote = true;
					break;
				}
				if (chance < durationWeights[i] + weightAdjust) {
					dur = durationPool[i];

					/*System.out.println("Remaining: " + remainingNotes + ", Added from chance: "
							+ dur + ", chance: " + chance);*/
					break;
				}
			}
			if (lastNote) {
				durationSum += dur;
				durations.add(dur);
				//System.out.println("Remaining: " + remainingNotes + ", Added from last: " + dur);
				remainingNotes--;
				break;
			}
			durationSum += dur;
			//System.out.println("Added dur: " + dur + ", was remaining: " + (remainingDuration));
			durations.add(dur);
			remainingNotes--;

		}
		if (!MidiGenerator.roughlyEqual(durationSum, durationLimit)) {
			/*System.out.println("Last note needs duration fix, sum: " + durationSum + ", needed: "
					+ durationLimit);*/
			durations.set(durations.size() - 1,
					(durations.get(durations.size() - 1))
							+ ((durationLimit > durationSum) ? (durationLimit - durationSum)
									: (durationSum - durationLimit)));
		}
		/*System.out.println("Duration lim: " + durationLimit + ", sum: "
				+ durations.stream().mapToDouble(e -> e).sum());
		System.out.println("Duration sum sum: " + durationSum);*/
		return durations;
	}

	public List<Double> regenerateDurations(int maxSameDurAllowed) {
		Random generator = new Random(randomSeed);
		durations = new ArrayList<>();
		double durationSum = 0;
		int sameDurCounter = 0;
		int lastDurIndex = Integer.MIN_VALUE;
		int retryCounter = 0;
		int maxRetry = 2;
		//System.out.println("Max same: " + maxSameDurAllowed);
		//System.out.println("Weights: " + Arrays.toString(durationWeights));
		//System.out.println("Dur pool: " + Arrays.toString(durationPool));
		while (durationSum < durationLimit - MidiGenerator.DBL_ERR) {
			double dur = MidiGenerator.Durations.EIGHTH_NOTE / 4.0;
			int chance = generator.nextInt(100);
			int chosenIndex = 0;
			boolean lastNote = false;
			for (int i = 0; i < durationPool.length; i++) {
				if (i < (durationPool.length - 1) && (durationPool[i + 1] > (durationLimit
						- durationSum + MidiGenerator.DBL_ERR))) {
					dur = durationLimit - durationSum;
					lastNote = true;
					break;
				}
				if (chance < durationWeights[i]) {
					dur = durationPool[i];

					//System.out.println("Adding by chance: " + dur);
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
