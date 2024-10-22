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
		//LG.d("Weights: " + StringUtils.join(durationWeights, ','));
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

					/*LG.d("Remaining: " + remainingNotes + ", Added from chance: "
							+ dur + ", chance: " + chance);*/
					break;
				}
			}
			if (lastNote) {
				durationSum += dur;
				durations.add(dur);
				//LG.d("Remaining: " + remainingNotes + ", Added from last: " + dur);
				remainingNotes--;
				break;
			}
			durationSum += dur;
			//LG.d("Added dur: " + dur + ", was remaining: " + (remainingDuration));
			durations.add(dur);
			remainingNotes--;

		}
		if (!MidiUtils.roughlyEqual(durationSum, durationLimit)) {
			/*LG.d("Last note needs duration fix, sum: " + durationSum + ", needed: "
					+ durationLimit);*/
			durations.set(durations.size() - 1,
					(durations.get(durations.size() - 1))
							+ ((durationLimit > durationSum) ? (durationLimit - durationSum)
									: (durationSum - durationLimit)));
		}
		/*LG.d("Duration lim: " + durationLimit + ", sum: "
				+ durations.stream().mapToDouble(e -> e).sum());
		LG.d("Duration sum sum: " + durationSum);*/
		return durations;
	}

	public List<Double> regenerateDurations(int maxSameDurAllowed, double shortestNote) {
		Random generator = new Random(randomSeed);
		durations = new ArrayList<>();
		double durationSum = 0;
		int sameDurCounter = 0;
		int lastDurIndex = Integer.MIN_VALUE;
		int retryCounter = 0;
		int maxRetry = 2;
		//LG.d("Max same: " + maxSameDurAllowed);
		//LG.d("Weights: " + Arrays.toString(durationWeights));
		//LG.d("Dur pool: " + Arrays.toString(durationPool));
		int longestDurIndex = 0;
		double longestDur = 0.0;
		while (durationSum < durationLimit - MidiGenerator.DBL_ERR) {
			double dur = shortestNote;
			int chance = generator.nextInt(100);
			int chosenIndex = 0;
			boolean lastNote = false;
			for (int i = 0; i < durationPool.length; i++) {
				if (i < (durationPool.length - 1) && (durationPool[i + 1] > (durationLimit
						- durationSum + MidiGenerator.DBL_ERR))) {
					dur = durationLimit - durationSum;
					if (dur < shortestNote - MidiGenerator.DBL_ERR && durations.size() > 0) {
						longestDur = longestDur - shortestNote + dur;
						//LG.d(longestDurIndex + ", " + longestDur);
						durations.set(longestDurIndex, longestDur);
						dur = shortestNote;
					}
					lastNote = true;
					break;
				}
				if (chance < durationWeights[i]) {
					dur = durationPool[i];

					//LG.d("Adding by chance: " + dur);
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
				if (dur > longestDur) {
					longestDur = dur;
					longestDurIndex = durations.size() - 1;
				}
				if (retryCounter == maxRetry) {
					LG.d("P A N I K");
					retryCounter = 0;
				}
			} else {
				retryCounter++;
			}

		}
		/*LG.d("Duration lim: " + durationLimit + ", sum: "
				+ durations.stream().mapToDouble(e -> e).sum());*/
		return durations;
	}
}
