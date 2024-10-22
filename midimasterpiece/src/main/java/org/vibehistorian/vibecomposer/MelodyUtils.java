package org.vibehistorian.vibecomposer;

import jm.music.data.Note;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.Enums.BlockType;
import org.vibehistorian.vibecomposer.MidiGenerator.Durations;
import org.vibehistorian.vibecomposer.Popups.TemporaryInfoPopup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.vibehistorian.vibecomposer.Enums.BlockType.BLOCK_CHANGE_MAP;
import static org.vibehistorian.vibecomposer.Enums.BlockType.blockChange;
import static org.vibehistorian.vibecomposer.Enums.BlockType.blockOfList;
import static org.vibehistorian.vibecomposer.Enums.BlockType.getBlocksForType;

public class MelodyUtils {

	//public static final List<Integer> chordyNotes = Arrays.asList(new Integer[] { 0, 2, 4, 7 });
	public static final List<Integer> cMajorSubstituteNotes = Arrays
			.asList(0, 2, 3, 5);
	public static List<List<Integer>> MELODY_PATTERNS = new ArrayList<>();
	public static List<List<Integer>> SOLO_MELODY_PATTERNS = new ArrayList<>();
	public static List<Integer> ALT_PATTERN_INDEXES = Arrays.asList(0, 1, 8, 9, 10, 12, 15, 16);
	public static List<Integer> BLOCK_CHANGE_JUMP_PREFERENCE = Arrays.asList(0, 7, 4, 3, 1, 2, 5, 6);

	public static List<List<Integer>> CHORD_DIRECTIONS = new ArrayList<>();

	public static final int NUM_LISTS = 4;

	static {

		CHORD_DIRECTIONS.add(Arrays.asList(-1, -1, 1, 2));
		CHORD_DIRECTIONS.add(Arrays.asList(-1, 0, 0, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, -1, -1, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, -1, 1, 0));
		CHORD_DIRECTIONS.add(Arrays.asList(0, -1, 0, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, -1, 1, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, 1, -1, 0));
		CHORD_DIRECTIONS.add(Arrays.asList(0, 1, -1, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, 1, 1, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(0, 1, 1, 2));
		//CHORD_DIRECTIONS.add(Arrays.asList(new Integer[] { 0, 0, 1, -1 }));
		CHORD_DIRECTIONS.add(Arrays.asList(0, 0, -1, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(1, -1, 1, 2));
		CHORD_DIRECTIONS.add(Arrays.asList(1, -1, 0, 1));
		CHORD_DIRECTIONS.add(Arrays.asList(2, 0, 1, 2));
		CHORD_DIRECTIONS.add(Arrays.asList(2, 0, -1, 1));

		// ALT PATTERNS: 0, 1, 8, 9, 10, 12, 15, 16
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 1, 3));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 1, 3, 1, 2, 1, 4));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 1, 3, 2, 2, 3, 4));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 3, 4, 1, 2, 1, 3));
		MELODY_PATTERNS.add(Arrays.asList(1, 1, 2, 3));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 3, 1));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 3, 2));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 3, 3));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 2, 1));
		MELODY_PATTERNS.add(Arrays.asList(1, 2, 2, 3));
		MELODY_PATTERNS.add(Arrays.asList(1, 1, 2, 1)); // 7
		//MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 1, 1, 2, 2 }));
		MELODY_PATTERNS.add(Arrays.asList(1, 1, 1, 2));
		//MELODY_PATTERNS.add(Arrays.asList(new Integer[] { 1, 1, 1, 1 }));
		// inverse patterns
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, 1, -1, 2));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, 2, -2, -1));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, 2, -1, 2));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, -1, 2, 3));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, -1, 2, 2));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, -1, -1, 1));
		SOLO_MELODY_PATTERNS.add(Arrays.asList(1, -1, 1, 2));
		MELODY_PATTERNS.addAll(SOLO_MELODY_PATTERNS);


	}

	public static Integer[] getRandomForType(Integer type, Random melodyBlockGenerator) {
		List<Integer[]> usedList = getBlocksForType(type);
		int rand2 = melodyBlockGenerator.nextInt(usedList.size());
		return usedList.get(rand2);
	}

	public static Integer[] getRandomForTypeAndLength(Integer type, Random melodyBlockGenerator,
			int length) {
		List<Integer[]> usedList = getBlocksForType(type);
		List<Integer[]> filteredList = usedList.stream().filter(e -> e.length == length)
				.collect(Collectors.toList());
		if (filteredList.size() == 0) {
			return null;
		}
		int rand2 = melodyBlockGenerator.nextInt(filteredList.size());
		return filteredList.get(rand2);
	}

	public static Integer[] getRandomForTypeAndBlockChangeAndLength(Integer type, int blockChange,
			Integer length, Random melodyBlockGenerator, int approx) {
		final int clampedBlockChange = OMNI.clamp(blockChange, -7, 7);
		List<Integer[]> usedList = getBlocksForType(type);
		// length fits, note distance and distance roughly equal (diff < approx)
		List<Integer[]> filteredList = usedList.stream()
				.filter(e -> (length == null || e.length == length)
						&& (Math.abs(blockChange(e) - Math.abs(clampedBlockChange)) <= approx))
				.collect(Collectors.toList());
		if (filteredList.size() == 0) {
			return null;
		}
		int rand2 = melodyBlockGenerator.nextInt(filteredList.size());
		Integer[] block = filteredList.get(rand2);
		if (blockChange(block) == -1 * clampedBlockChange) {
			return BlockType.inverse(block);
		} else {
			return block;
		}
	}

	public static Pair<Integer, Integer[]> getRandomByApproxBlockChangeAndLength(int blockChange,
																				 int approx, Random melodyBlockGenerator, Integer length, int remainingVariance,
																				 int remainingDirChanges, List<Integer> usedMelodyBlockJumpPreference) {
		//LG.d("Chosen change: " + chosenChange);
		List<Pair<Integer, Integer[]>> viableBlocks = new ArrayList<>();
		int start = Math.max(blockChange - approx, -7);
		int end = Math.min(blockChange + approx, 7);
		for (int i = start; i <= end; i++) {
			if (BLOCK_CHANGE_MAP.containsKey(i)) {
				for (Pair<Integer, Integer[]> typeBlock : BLOCK_CHANGE_MAP.get(i)) {
					Integer[] block = typeBlock.getRight();
					if (length == null || block.length == length) {
						viableBlocks.add(typeBlock);
					}
				}

			}
			if (BLOCK_CHANGE_MAP.containsKey(i * -1)) {
				List<Pair<Integer, Integer[]>> invertedBlocks = new ArrayList<>();
				for (Pair<Integer, Integer[]> typeBlock : BLOCK_CHANGE_MAP.get(i * -1)) {
					Integer[] block = typeBlock.getRight();
					if (length == null || block.length == length) {
						invertedBlocks.add(Pair.of(typeBlock.getLeft(), BlockType.inverse(block)));
					}
				}
				viableBlocks.addAll(invertedBlocks);
			}
		}

		//int sizeBefore = viableBlocks.size();
		viableBlocks.removeIf(e -> MelodyUtils.variance(e.getRight()) > remainingVariance);
		/*LG.d("Size difference: " + (sizeBefore - viableBlocks.size())
				+ ", for variance remaining: " + remainingVariance);*/
		viableBlocks.removeIf(
				e -> MelodyUtils.interblockDirectionChange(e.getRight()) > remainingDirChanges);
		/*LG.d("Size difference: " + (sizeBefore - viableBlocks.size())
				+ ", for dir change remaining: " + remainingDirChanges);*/
		//viableBlocks.forEach(e -> LG.d(StringUtils.join(e, ',')));
		if (viableBlocks.size() == 0) {
			LG.d("Viable blocks size is 0, getting random block!");
			Integer[] block = getRandomForTypeAndBlockChangeAndLength(null, blockChange, length,
					melodyBlockGenerator, 4);
			if (block == null) {
				LG.e("**************************************Fatal error generating melody block!");
				new TemporaryInfoPopup(
						"Error generating melody block: " + blockChange + ", " + length, 2000);
			}
			return Pair.of(blockOfList(block), block);
		}
		// TODO: RIP complexity
		// TODO: allow choosing the weighting function - n^2 too weighty, maybe n^1.5 is just right..
		if (!usedMelodyBlockJumpPreference.isEmpty()) {
			viableBlocks.sort(Comparator.comparingInt(e -> usedMelodyBlockJumpPreference.indexOf(Math.abs(blockChange(e.getRight())))));
			double floatingIndex = melodyBlockGenerator.nextDouble();
			double weighted = Math.pow(floatingIndex, 2);
			int blockToGetIndex = Math.min(viableBlocks.size() - 1, (int) (weighted * viableBlocks.size()));
			return viableBlocks.get(blockToGetIndex);
		}

		return viableBlocks.get(melodyBlockGenerator.nextInt(viableBlocks.size()));

	}

	public static List<Integer> inverse(List<Integer> block) {
		List<Integer> newBlock = new ArrayList<>();
		for (int i = 0; i < block.size(); i++) {
			newBlock.add(block.get(i) * -1);
		}
		return newBlock;
	}

	public static int interblockDirectionChange(Integer[] block) {
		int lastDirection = blockChange(block);
		int counter = 0;
		// 0 4 2 7 -> 2 interblock changes
		// 0 7 4 -> 1 
		// 0 2 4 -> 0
		for (int i = 1; i < block.length; i++) {
			int currentDir = block[i] - block[i - 1];
			if (lastDirection > 0 && currentDir < 0) {
				lastDirection = -1;
				counter++;
			} else if (lastDirection < 0 && currentDir > 0) {
				lastDirection = 1;
				counter++;
			}
		}
		//LG.d("Interblock change: " + counter);
		return counter;
	}

	public static Integer variance(Integer[] block) {
		// 0 4 7 2 -> variance 7 - 2 = 5
		// 0 2 7 4 -> variance 7 - 4 = 3

		int min = block[0];
		int max = block[block.length - 1];
		if (min > max) {
			min = max;
			max = block[0];
		}
		int biggestOutsider = 0;
		for (int i = 1; i < block.length - 1; i++) {
			int diff = 0;
			if (block[i] < min) {
				diff = min - block[i];
			} else if (block[i] > max) {
				diff = block[i] - max;
			}

			if (diff > biggestOutsider) {
				biggestOutsider = diff;
			}
		}
		//LG.d("Variance: " + biggestOutsider);
		return biggestOutsider;
	}

	public static Pair<List<Integer>, Integer> blockChangeSequence(int chord1, int chord2,
			int randSeed, int numBlocks, int maxBlockChange, int maxDirChanges) {
		Random rand = new Random(randSeed);
		List<Integer> changeList = new ArrayList<>();

		// how many notes need to be corrected | change = 5 -> sum of block change sequence must be -5
		int change = chord1 - chord2;
		//LG.d("Change: " + change);
		List<Integer> reducableIndices = new ArrayList<>();

		for (int i = 0; i < numBlocks; i++) {
			int chg = rand.nextInt(maxBlockChange * 2 + 1) - maxBlockChange;
			changeList.add(chg);
			change += chg;
			reducableIndices.add(i);

		}
		//LG.d("Initial: " + StringUtils.join(changeList, ","));
		//LG.d("reducableIndices i's: " + StringUtils.join(reducableIndices, ", "));
		if (change > 0) {
			reducableIndices.removeIf(e -> changeList.get(e) == -1 * maxBlockChange);
		} else if (change < 0) {
			reducableIndices.removeIf(e -> changeList.get(e) == maxBlockChange);
		}

		rand.setSeed(randSeed);
		int increment = (change > 0) ? -1 : 1;
		for (int i = 0; i < Math.abs(change); i++) {
			if (reducableIndices.size() == 0)
				break;
			int redI = rand.nextInt(reducableIndices.size());
			int redIndex = reducableIndices.get(redI);
			int newValue = changeList.get(redIndex) + increment;
			changeList.set(redIndex, newValue);
			if (Math.abs(newValue) == maxBlockChange) {
				Integer removed = reducableIndices.remove(redI);
			}
		}
		rand.setSeed(randSeed);

		//LG.d("Decr: " + StringUtils.join(changeList, ","));
		//Collections.shuffle(changeList, rand);

		smartShuffleMaxDirChange(changeList, rand, maxDirChanges);
		int remainingDirChange = calculateDirectionChanges(changeList);

		//LG.d("Shuffled: " + StringUtils.join(changeList, ","));
		return Pair.of(changeList, remainingDirChange);
	}

	private static int calculateDirectionChanges(List<Integer> changeList) {
		if (changeList == null || changeList.size() < 2) {
			return 0;
		}
		return interblockDirectionChange(changeList.toArray(new Integer[] {}));
	}

	private static void smartShuffleMaxDirChange(List<Integer> changeList, Random rand,
			int maxDirChange) {
		if (changeList == null || changeList.size() <= 1) {
			return;
		}

		// maxDirChange: 1,2,3, UNLIMITED

		// only one direction present
		if (!changeList.stream().anyMatch(e -> e < 0) || !changeList.stream().anyMatch(e -> e > 0)
				|| changeList.size() < 3) {
			Collections.shuffle(changeList, rand);
			return;
		}
		// negative and positive values present:
		// sorting creates 1 dir change: -2, -1, 0, 0, 3, 4
		Collections.sort(changeList);

		int maxNegIndex = 0;
		int firstPosIndex = 0;
		for (int i = 1; i < changeList.size(); i++) {
			if (changeList.get(i) >= 0 && maxNegIndex == 0) {
				maxNegIndex = i - 1;
			}
			if (changeList.get(i) > 0 && firstPosIndex == 0) {
				firstPosIndex = i;
				break;
			}
		}
		int zeroCount = firstPosIndex - maxNegIndex - 1;

		List<Integer> negSub = new ArrayList<>(changeList.subList(0, maxNegIndex + 1));
		List<Integer> posSub = new ArrayList<>(
				changeList.subList(firstPosIndex, changeList.size()));
		Collections.shuffle(negSub, rand);
		Collections.shuffle(posSub, rand);

		if (rand.nextBoolean() && maxDirChange > 1) {

			int posIndexForSwap = rand.nextInt(posSub.size());
			if (maxDirChange == 2) {
				negSub.add(0, posSub.get(posIndexForSwap));
				posSub.remove(posIndexForSwap);
			} else {
				int negIndexForSwap = rand.nextInt(negSub.size());
				int temp = posSub.get(posIndexForSwap);
				posSub.set(posIndexForSwap, negSub.get(negIndexForSwap));
				negSub.set(negIndexForSwap, temp);
			}
		}

		List<Integer> finalList = new ArrayList<>();
		if (rand.nextBoolean()) {
			finalList.addAll(negSub);
			finalList.addAll(posSub);
		} else {
			finalList.addAll(posSub);
			finalList.addAll(negSub);
		}

		if (zeroCount > 0) {
			for (int i = 0; i < zeroCount; i++) {
				int randIndex = rand.nextInt(finalList.size());
				finalList.add(randIndex, 0);
			}
		}
		changeList.clear();
		changeList.addAll(finalList);

	}

	public boolean blockContainsJump(Integer[] block, int jump) {
		for (int i = 1; i < block.length; i++) {
			if (Math.abs(block[i] - block[i - 1]) == jump) {
				return true;
			}
		}
		return false;
	}

	public static List<Integer> getRandomMelodyPattern(int altPatternChance, Integer randomSeed) {
		Random rand = new Random();
		if (randomSeed != null) {
			rand.setSeed(randomSeed);
		}
		if (rand.nextInt(100) < altPatternChance) {
			return new ArrayList<>(MELODY_PATTERNS
					.get(ALT_PATTERN_INDEXES.get(rand.nextInt(ALT_PATTERN_INDEXES.size()))));
		}
		return new ArrayList<>(MELODY_PATTERNS.get(rand.nextInt(MELODY_PATTERNS.size())));
	}

	public static List<Note> sortNotesByRhythmicImportance(List<Note> notes) {
		List<Note> sorted = new ArrayList<>();
		List<Note> main8th = new ArrayList<>();
		List<Note> main16th = new ArrayList<>();
		List<Note> others = new ArrayList<>();

		double currTime = 0;
		for (Note n : notes) {
			if (MidiUtils.isMultiple(currTime + n.getOffset(), Durations.EIGHTH_NOTE)) {
				main8th.add(n);
			} else if (MidiUtils.isMultiple(currTime + n.getOffset(), Durations.SIXTEENTH_NOTE)) {
				main16th.add(n);
			} else {
				others.add(n);
			}
			currTime += n.getRhythmValue();
		}
		Collections.sort(main8th, Comparator.comparing(e -> e.getRhythmValue()));
		Collections.sort(main16th, Comparator.comparing(e -> e.getRhythmValue()));
		Collections.sort(others, Comparator.comparing(e -> e.getRhythmValue()));
		sorted.addAll(others);
		sorted.addAll(main16th);
		sorted.addAll(main8th);
		LG.n("Others: " + others.size() + ", 16th: " + main16th.size() + ", 8th: "
				+ main8th.size());
		return sorted;
	}

	public static Pair<Integer, Integer[]> generateBlockByBlockChangeAndLength(Integer blockChange,
			int approx, Random blockNotesGenerator, Integer forcedLength, int remainingVariance,
			int remainingDirChanges) {
		remainingVariance = Math.max(0, remainingVariance);
		Random rnd = new Random(blockNotesGenerator.nextInt());
		int newLen = (forcedLength != null) ? forcedLength : (rnd.nextInt(2) + 3);
		int last = newLen - 1;
		Integer[] newBlock = new Integer[newLen];
		newBlock[0] = 0;
		newBlock[last] = Math.abs(blockChange) + rnd.nextInt(approx * 2 + 1) - approx;
		if (blockChange < 0 && newBlock[last] > 0) {
			newBlock[last] *= -1;
		}
		int currentMin = Math.min(0, newBlock[last]);
		int currentMax = Math.max(0, newBlock[last]);

		int defaultDirection = Integer.signum(currentMax - currentMin);
		int dirChangesLeft = remainingDirChanges;
		boolean restrictDirectionChanges = (defaultDirection != 0);


		for (int i = 1; i < last; i++) {
			boolean cantChangeDir = restrictDirectionChanges && dirChangesLeft < 1;
			int usableRemainingVariance = cantChangeDir ? 0 : (remainingVariance + 1) / 2; // don't use all the jumpiness at once
			boolean ascending = newBlock[last] - newBlock[i-1] > 0;
			int high = cantChangeDir ? (ascending ? newBlock[last] : newBlock[i-1]) : currentMax;
			int low = cantChangeDir ? (ascending ? newBlock[i-1] : newBlock[last]) : currentMin;
			newBlock[i] = rnd.nextInt(usableRemainingVariance * 2 + (high - low) + 1)
					+ low - usableRemainingVariance;



			// between lowest note and lowest possible note
			int varianceOverlapLow = currentMin - newBlock[i];
			if (varianceOverlapLow > 0) {
				remainingVariance -= varianceOverlapLow;
				currentMin = newBlock[i];
			}
			// between highest note and highest possible note
			int varianceOverlapHigh = newBlock[i] - currentMax;
			if (varianceOverlapHigh > 0) {
				remainingVariance -= varianceOverlapHigh;
				currentMax = newBlock[i];
			}

			if (!restrictDirectionChanges && !cantChangeDir) {
				Integer[] testedBlock = Arrays.copyOf(newBlock, i + 2);
				testedBlock[i + 1] = newBlock[last];
				dirChangesLeft = remainingDirChanges - interblockDirectionChange(testedBlock);
			}

			remainingVariance = Math.max(0, remainingVariance);
		}


		/*viableBlocks.removeIf(e -> MelodyUtils.variance(e.getRight()) > remainingVariance);
		viableBlocks.removeIf(
				e -> MelodyUtils.interblockDirectionChange(e.getRight()) > remainingDirChanges);*/
		LG.d("generateBlockByBlockChangeAndLength: " + StringUtils.join(newBlock, ","));
		return Pair.of(Integer.MAX_VALUE, newBlock);
	}
}
